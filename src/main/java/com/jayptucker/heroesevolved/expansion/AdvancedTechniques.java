package com.jayptucker.heroesevolved.expansion;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.ability.Targeting;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.effects.WorldMutationPolicy;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.network.ScreenTintPayload;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.radiation.RadiationService;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import com.jayptucker.heroesevolved.speed.SpeedService;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;

/**
 * The second half of every original skill tree: one technique per branch, an upgrade that
 * changes it, and a capstone. Fields and marks are short-lived and bounded; all world edits go
 * through {@link WorldMutationPolicy}. Node ids are {@code <power>_<name>}.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class AdvancedTechniques {
    private record Zone(String kind, ServerPlayer owner, ServerLevel level, Vec3 center, Vec3 end, double radius, long until) {}
    private static final List<Zone> ZONES = new ArrayList<>();
    private static final Map<UUID, Long> ADAPTING = new HashMap<>();
    /** The kinds of harm each adapting body has learned, newest last: one, or three with Callus. */
    private static final Map<UUID, List<String>> ADAPTED_TO = new HashMap<>();
    private static final Map<UUID, Long> MIRAGES = new HashMap<>();
    /** Perception: the world around a runner moves at a fraction of its speed. */
    private record Perception(ServerPlayer owner, long until, double radius, int interval) {}
    private static final Map<UUID, Perception> PERCEPTION = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWNS = new HashMap<>();
    private static int sharing;

    private AdvancedTechniques() {}

    private static long now(MinecraftServer server) { return server.overworld().getGameTime(); }
    private static long now(ServerPlayer p) { return now(p.getServer()); }
    private static boolean has(ServerPlayer p, String node) { return SkillTreeService.has(p, node); }
    private static boolean hostile(ServerPlayer p, LivingEntity t) { return ExpansionSkills.hostile(p, t); }
    private static boolean friendly(ServerPlayer p, LivingEntity t) { return ExpansionSkills.friendly(p, t); }

    private static List<ServerPlayer> allies(ServerPlayer p, double radius) {
        return p.serverLevel().getEntitiesOfClass(ServerPlayer.class, p.getBoundingBox().inflate(radius),
                other -> other == p || p.isAlliedTo(other));
    }

    private static List<LivingEntity> enemies(ServerPlayer p, Vec3 center, double radius) {
        return p.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius),
                t -> hostile(p, t) && t.position().distanceToSqr(center) <= radius * radius).stream().limit(32).toList();
    }

    private static void push(Entity target, Vec3 velocity) {
        target.setDeltaMovement(velocity); target.hurtMarked = true;
        if (target instanceof ServerPlayer player) player.connection.send(new ClientboundSetEntityMotionPacket(player));
    }

    private static boolean cooldown(UUID id, String key, long now, int ticks) {
        UUID keyed = UUID.nameUUIDFromBytes((id + key).getBytes());
        if (COOLDOWNS.getOrDefault(keyed, 0L) > now) return false;
        COOLDOWNS.put(keyed, now + ticks);
        return true;
    }

    private static BlockHitResult aimBlock(ServerPlayer p, double reach) {
        Vec3 eye = p.getEyePosition();
        return p.level().clip(new ClipContext(eye, eye.add(p.getLookAngle().scale(reach)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
    }

    public static boolean activate(AbilityUseContext c, String mechanic) {
        ServerPlayer p = c.player();
        ServerLevel level = p.serverLevel();
        long now = now(p);
        switch (mechanic) {
            // Legacy mechanic aliases resolve through the same authoritative Flight skills.
            case "sky_catch" -> { return com.jayptucker.heroesevolved.flight.FlightSkillService.activate(p, "flight_sky_catch"); }
            case "tailwind" -> { return com.jayptucker.heroesevolved.flight.FlightSkillService.activate(p, "flight_tailwind"); }
            case "thunderclap" -> { return com.jayptucker.heroesevolved.flight.FlightSkillService.activate(p, "flight_thunderclap"); }
            // ---------------- Telekinesis ----------------
            case "remote_hand" -> { return com.jayptucker.heroesevolved.telekinesis.TelekinesisSkillService.activate(p, "telekinesis_remote_hand", c.modifierHeld()); }
            case "kinetic_volley" -> { return com.jayptucker.heroesevolved.telekinesis.TelekinesisSkillService.activate(p, "telekinesis_kinetic_volley", c.modifierHeld()); }
            case "force_bubble" -> { return com.jayptucker.heroesevolved.telekinesis.TelekinesisSkillService.activate(p, "telekinesis_force_bubble", c.modifierHeld()); }
            // Telepathy's techniques (Persuasion, Memory Walk, Hive Link) live with the power: TelepathySkills.
            // ---------------- Regeneration ----------------
            case "take_the_wound" -> {
                LivingEntity friend = Targeting.livingEntityInSight(p, 16, t -> t != p && friendly(p, t) && t.getHealth() < t.getMaxHealth());
                if (friend == null) return false;
                float healed = Math.min(8, friend.getMaxHealth() - friend.getHealth());
                friend.heal(healed);
                float taken = healed / (has(p, "regeneration_martyrs_grace") ? 4 : 2);
                p.hurt(p.damageSources().magic(), taken);
                com.jayptucker.heroesevolved.regeneration.RegenerationService.scar(p, taken);
                AbilityEffects.send(level, Kind.BLOOD_THREAD, friend.getEyePosition(), p.getEyePosition(), 1);
                ArmPosePayload.show(p, ArmPose.REGEN_DRAW, 14);
            }
            case "adaptation" -> {
                ADAPTING.put(p.getUUID(), now + 600);
                ADAPTED_TO.remove(p.getUUID());
                AbilityEffects.at(p, Kind.REGENERATION, 1.4);
                ArmPosePayload.show(p, ArmPose.REGEN_HARDEN, 16);
            }
            case "revive" -> {
                LivingEntity friend = Targeting.livingEntityInSight(p, has(p, "regeneration_field_medic") ? 16 : 8,
                        t -> t != p && friendly(p, t) && t.getHealth() < t.getMaxHealth() * 0.3F);
                if (friend == null) {
                    p.displayClientMessage(Component.translatable("technique.heroesevolved.revive.none"), true);
                    return false;
                }
                friend.setHealth(Math.max(friend.getHealth(), friend.getMaxHealth() * 0.6F));
                friend.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
                if (has(p, "regeneration_second_wind")) {
                    for (MobEffectInstance effect : List.copyOf(friend.getActiveEffects()))
                        if (!effect.getEffect().value().isBeneficial()) friend.removeEffect(effect.getEffect());
                    friend.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 2));
                }
                AbilityEffects.send(level, Kind.HEALING_AURA, friend.position(), friend.getEyePosition(), 2);
                // Both hands on their chest, pumping: the heartbeat comes back under them.
                com.jayptucker.heroesevolved.regeneration.RegenSkills.show(level, com.jayptucker.heroesevolved.styles.Piece.REGEN_CPR, friend.position(), friend.getEyePosition(),
                        friend.getBbHeight(), friend);
                ArmPosePayload.show(p, ArmPose.REGEN_COMPRESS, 20);
            }
            // ---------------- Space-Time ----------------
            // Space-Time's techniques live with the power.
            case "stasis_seal", "shared_blink", "accelerate" -> {
                return com.jayptucker.heroesevolved.time.SpaceTimeSkills.technique(c, mechanic);
            }
            // ---------------- Radiation ----------------
            // Induced Radioactivity's techniques live with the power.
            case "heat_vent", "reactor_core", "glowing_path" -> {
                return com.jayptucker.heroesevolved.radiation.RadiationSkills.technique(p, mechanic);
            }
            // ---------------- Sonic Resonance ----------------
            // Sonic Resonance's techniques live with the power.
            case "shatter_note", "echo_map", "quiet_zone" -> {
                return com.jayptucker.heroesevolved.sonic.SonicSkills.technique(p, mechanic);
            }
            // ---------------- Superspeed ----------------
            case "mirage" -> {
                MIRAGES.put(p.getUUID(), now + (has(p, "superspeed_lingering_mirage") ? 200 : 120));
                for (Mob mob : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(16), m -> m.getTarget() == p)) {
                    mob.setTarget(null);
                    mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
                }
                // Afterimages tear loose all round you (re-sent while the mirage lasts, in the tick).
                AbilityEffects.send(level, Kind.SPEED_MIRAGE, p.position(), p.getEyePosition(), 1);
                ArmPosePayload.show(p, ArmPose.SPEED_SPLIT, 16);
                level.playSound(null, p.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 1, 1.6F);
            }
            case "lightning_throw" -> {
                LivingEntity target = Targeting.livingEntityInSight(p, 32, t -> hostile(p, t));
                if (target == null) return false;
                int momentum = SpeedService.spendMomentum(p, 30);
                if (momentum == 0) {
                    p.displayClientMessage(Component.translatable("technique.heroesevolved.lightning_throw.momentum"), true);
                    return false;
                }
                List<LivingEntity> struck = new ArrayList<>(List.of(target));
                if (has(p, "superspeed_charged_throw"))
                    struck.addAll(enemies(p, target.position(), 6).stream().filter(t -> t != target).limit(2).toList());
                // Lightning gathers in the cocked hand and leaves it as the arm whips through.
                ArmPosePayload.show(p, ArmPose.SPEED_THROW, 14);
                // From the hand at the end of the whip, in front of the face (not behind the first-person camera).
                Vec3 from = com.jayptucker.heroesevolved.electro.ElectroGeometry.hand(p, 1, .6).add(0, .2, 0);
                boolean hop = false;
                for (LivingEntity t : struck) {
                    if (!PowerReactions.electric(p, t)) t.hurt(p.damageSources().lightningBolt(), 6 + momentum / 10F);
                    AbilityEffects.send(level, Kind.SPEED_THROW, from, t.getEyePosition(), hop ? 11.5F : 1.5F);
                    from = t.getEyePosition();
                    hop = true;
                }
                level.playSound(null, p.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.8F, 1.4F);
            }
            case "perception" -> startPerception(p);
            default -> { return false; }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Tick: fields, persuaded mobs, perception
    // ------------------------------------------------------------------

    /**
     * Inside a Perception field, everything except players and shots (which are slowed by their own
     * rule) updates once every few ticks, so mobs, falling blocks and items move at a third of their
     * speed, a sixth with Flash Time.
     */
    public static boolean perceptionSkips(Entity entity) {
        if (PERCEPTION.isEmpty() || entity instanceof net.minecraft.world.entity.player.Player
                || entity instanceof Projectile || !(entity.level() instanceof ServerLevel level)) return false;
        for (Perception field : PERCEPTION.values()) {
            ServerPlayer p = field.owner;
            if (p.level() != level || entity.getVehicle() == p || entity.distanceToSqr(p) > field.radius * field.radius
                    || entity instanceof net.minecraft.world.entity.OwnableEntity pet && pet.getOwner() == p || !active(field)) continue;
            return Math.floorMod(entity.getId(), field.interval) != Math.floorMod(level.getGameTime(), field.interval);
        }
        return false;
    }

    /** Perception: twelve blocks (sixteen with Long Perception) of the world slowed to a third, a sixth with Flash Time. */
    public static void startPerception(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        boolean longer = has(p, "superspeed_long_perception"), flashTime = has(p, "superspeed_flash_time");
        Perception field = new Perception(p, now(p) + (longer ? 200 : 120), longer ? 16 : 12, flashTime ? 6 : 3);
        PERCEPTION.put(p.getUUID(), field);
        AbilityEffects.send(level, Kind.SPEED_PERCEPTION, p.position(), p.getEyePosition(), (float) field.radius);
        // Open hands, held perfectly still while everything else crawls.
        ArmPosePayload.show(p, ArmPose.SPEED_STILL, 40);
        level.playSound(null, p.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1F, 0.5F);
        level.playSound(null, p.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 0.4F, 2F);
        ScreenTintPayload.send(p, 0x40FFC23A, 10, ScreenTintPayload.Style.FLASH);
    }

    /** Ends Perception at once (tests, power loss). */
    public static void endPerception(ServerPlayer p) {
        if (PERCEPTION.remove(p.getUUID()) != null)
            com.jayptucker.heroesevolved.time.ProjectileTimeService.releaseInactive();
    }

    /** True while {@code p} has Perception up. */
    public static boolean perceiving(ServerPlayer p) { return PERCEPTION.containsKey(p.getUUID()); }

    public static int playerSlowInterval(ServerPlayer target) {
        int interval = 1;
        for (Perception field : PERCEPTION.values())
            if (!field.owner.getUUID().equals(target.getUUID()) && active(field))
                interval = Math.max(interval, field.interval);
        return interval;
    }

    public static int projectileInterval(Projectile shot, Vec3 end) {
        int interval = 0;
        for (Perception field : PERCEPTION.values()) {
            if (!affectsShot(field, shot, end)) continue;
            interval = Math.max(interval, field.interval);
        }
        return interval;
    }

    public static boolean controlsShot(ServerPlayer player, Projectile shot, Vec3 end) {
        Perception field = PERCEPTION.get(player.getUUID());
        return field != null && affectsShot(field, shot, end);
    }

    private static boolean affectsShot(Perception field, Projectile shot, Vec3 end) {
        ServerPlayer owner = field.owner;
        return owner.level() == shot.level() && shot.getOwner() != owner && active(field)
                && com.jayptucker.heroesevolved.time.ProjectileTimeService
                .reachesField(shot.position(), end, owner.position(), field.radius);
    }

    private static boolean active(Perception field) {
        return !field.owner.isRemoved() && now(field.owner) < field.until
                && SpeedService.awakened(field.owner) && PowerRules.canAct(field.owner);
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long now = now(server);
        ZONES.removeIf(zone -> now >= zone.until || !zone.owner.isAlive() || zone.owner.isRemoved());
        for (Zone zone : List.copyOf(ZONES)) {
            ServerPlayer p = zone.owner;
            switch (zone.kind) {
                case "bubble" -> {
                    for (Projectile shot : zone.level.getEntitiesOfClass(Projectile.class, new AABB(zone.center, zone.center).inflate(zone.radius),
                            s -> !(s.getOwner() instanceof LivingEntity owner && (owner == p || p.isAlliedTo(owner))))) {
                        Vec3 away = shot.position().subtract(zone.center).normalize();
                        shot.setDeltaMovement(away.scale(Math.max(0.6, shot.getDeltaMovement().length())));
                        shot.setOwner(p);
                        shot.hurtMarked = true;
                    }
                    if (now % 5 == 0) for (LivingEntity t : enemies(p, zone.center, zone.radius))
                        push(t, t.position().subtract(zone.center).normalize().scale(0.6).add(0, .1, 0));
                    if (now % 20 == 0) AbilityEffects.send(zone.level, Kind.PSYCHIC_FIELD, zone.center, zone.center.add(0, 1, 0), zone.radius);
                }
                case "contamination" -> {
                    if (now % 20 != 0) break;
                    boolean catastrophe = has(p, "radiation_catastrophe");
                    for (LivingEntity t : enemies(p, zone.center, zone.radius)) {
                        t.hurt(p.damageSources().magic(), 2);
                        if (catastrophe) t.addEffect(new MobEffectInstance(MobEffects.WITHER, 60));
                    }
                    AbilityEffects.send(zone.level, Kind.NUCLEAR_FALLOUT, zone.center, zone.center.add(0, 1, 0), zone.radius);
                }
                default -> { }
            }
        }

        ADAPTING.values().removeIf(until -> until <= now);
        ADAPTED_TO.keySet().removeIf(id -> !ADAPTING.containsKey(id));
        COOLDOWNS.values().removeIf(until -> until <= now);

        // Superspeed: mirages hold attention, perception slows (or stops) what flies at you.
        for (var entry : List.copyOf(MIRAGES.entrySet())) {
            ServerPlayer p = server.getPlayerList().getPlayer(entry.getKey());
            if (p == null || now >= entry.getValue()) { MIRAGES.remove(entry.getKey()); continue; }
            if (now % 5 == 0) AbilityEffects.send(p.serverLevel(), Kind.SPEED_MIRAGE, p.position(), p.getEyePosition(), 1);
            if (has(p, "superspeed_time_remnant") && now % 20 == 0)
                for (LivingEntity t : enemies(p, p.position(), 4)) {
                    t.hurt(p.damageSources().playerAttack(p), 2);
                    // A remnant standing beside them lashes out.
                    Vec3 remnant = t.position().add(t.position().subtract(p.position()).normalize().scale(-1.2)).add(0, 1.1, 0);
                    AbilityEffects.send(p.serverLevel(), Kind.SPEED_IMPACT, remnant, t.getBoundingBox().getCenter(), 1);
                }
        }
        for (Perception field : List.copyOf(PERCEPTION.values())) {
            ServerPlayer p = field.owner;
            if (!active(field)) {
                endPerception(p);
                if (p.isAlive() && !p.isRemoved()) p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.6F);
                continue;
            }
            ServerLevel level = p.serverLevel();
            if (now % 10 == 0) {
                AbilityEffects.send(level, Kind.SPEED_PERCEPTION, p.position(), p.getEyePosition(), (float) field.radius);
                ScreenTintPayload.send(p, 0x24FFB040, 14, ScreenTintPayload.Style.HOLD);
            }
            // A slow heartbeat only the runner hears.
            if (now % 24 == 0) p.playNotifySound(SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.9F, 0.6F);
        }

    }

    // ------------------------------------------------------------------
    // Passives and capstones that react to events
    // ------------------------------------------------------------------

    @SubscribeEvent public static void target(LivingChangeTargetEvent event) {
        LivingEntity target = event.getNewAboutToBeSetTarget();
        if (target == null) return;
        if (target instanceof ServerPlayer player && MIRAGES.containsKey(player.getUUID()) && event.getEntity().distanceToSqr(player) > 4) {
            event.setCanceled(true);
            return;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void damage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        long now = victim.getServer() == null ? 0 : now(victim.getServer());

        if (victim instanceof ServerPlayer player) {
            // Adaptation: the same harm twice is less harm.
            if (ADAPTING.containsKey(player.getUUID())) {
                String type = event.getSource().typeHolder().getRegisteredName();
                List<String> learned = ADAPTED_TO.computeIfAbsent(player.getUUID(), id -> new ArrayList<>());
                if (learned.contains(type))
                    event.setAmount(event.getAmount() * (has(player, "regeneration_thick_skin") ? 0.4F : 0.6F));
                // Callus: up to three kinds of harm remembered at once, instead of only the last.
                learned.remove(type);
                learned.add(type);
                while (learned.size() > (has(player, com.jayptucker.heroesevolved.regeneration.RegenSkills.CALLUS) ? 3 : 1)) learned.removeFirst();
            }
            if (has(player, "regeneration_perfect_adaptation") && (event.getSource().is(DamageTypeTags.IS_FIRE)
                    || event.getSource().is(net.minecraft.world.damagesource.DamageTypes.WITHER))) {
                event.setCanceled(true);
                return;
            }

            // Martyr: a regenerating friend takes the killing blow instead.
            if (sharing == 0 && event.getAmount() >= player.getHealth() && player.getServer() != null) {
                for (ServerPlayer martyr : player.serverLevel().getEntitiesOfClass(ServerPlayer.class, player.getBoundingBox().inflate(8),
                        m -> m != player && has(m, "regeneration_martyr") && (m.isAlliedTo(player) || player.isShiftKeyDown()))) {
                    if (!cooldown(martyr.getUUID(), "martyr", now, 20 * 120)) continue;
                    float amount = event.getAmount();
                    event.setCanceled(true);
                    sharing++;
                    try { martyr.hurt(martyr.damageSources().magic(), amount); } finally { sharing--; }
                    com.jayptucker.heroesevolved.regeneration.RegenerationService.scar(martyr, amount);
                    AbilityEffects.send(martyr.serverLevel(), Kind.BLOOD_THREAD, player.getEyePosition(), martyr.getEyePosition(), 1.5);
                    return;
                }
            }
        }

        // Infinite Mass Punch: a full-momentum blow.
        if (event.getSource().getEntity() instanceof ServerPlayer attacker && event.getSource().getDirectEntity() == attacker
                && event.getSource().is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK)
                && has(attacker, "superspeed_infinite_mass") && SpeedService.momentum(attacker) >= 90) {
            int momentum = SpeedService.spendMomentum(attacker, 90);
            event.setAmount(event.getAmount() + momentum / (victim instanceof ServerPlayer ? 12F : 4F));
            push(victim, victim.position().subtract(attacker.position()).normalize().scale(2.2).add(0, .5, 0));
            AbilityEffects.at(attacker, Kind.SPEED_IMPACT, 3);
        }
    }

    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        ZONES.clear(); ADAPTING.clear(); ADAPTED_TO.clear();
        MIRAGES.clear(); PERCEPTION.clear();
        COOLDOWNS.clear(); sharing = 0;
    }
}
