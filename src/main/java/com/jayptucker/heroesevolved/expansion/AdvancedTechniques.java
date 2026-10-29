package com.jayptucker.heroesevolved.expansion;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.Targeting;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.effects.PrivateGlow;
import com.jayptucker.heroesevolved.effects.WorldMutationPolicy;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.invisibility.InvisibilityService;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.radiation.RadiationService;
import com.jayptucker.heroesevolved.registry.ModBlocks;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import com.jayptucker.heroesevolved.speed.SpeedService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
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
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
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
    private record Seal(ServerPlayer owner, ServerLevel level, Vec3 center, double radius, long until, Map<Projectile, Vec3> held) {}
    private record Persuaded(Mob mob, ServerPlayer owner, long until) {}
    private static final List<Zone> ZONES = new ArrayList<>();
    private static final Map<UUID, Seal> SEALS = new HashMap<>();
    private static final Map<UUID, Persuaded> PERSUADED = new HashMap<>();
    private static final Map<UUID, Long> COMPELLED = new HashMap<>();   // player -> until (cannot attack persuader)
    private static final Map<UUID, UUID> COMPELLER = new HashMap<>();
    private static final Map<UUID, Long> ADAPTING = new HashMap<>();
    private static final Map<UUID, String> ADAPTED_TO = new HashMap<>();
    private static final Map<UUID, Long> HIVE = new HashMap<>();         // linked player -> until
    private static final Map<UUID, UUID> HIVE_OWNER = new HashMap<>();
    private static final Map<UUID, Long> MIRAGES = new HashMap<>();
    private static final Map<UUID, Long> PERCEPTION = new HashMap<>();
    private static final Map<UUID, Integer> DODGES = new HashMap<>();
    private static final Map<UUID, Long> DODGE_UNTIL = new HashMap<>();
    private static final Map<UUID, Long> OMENS = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWNS = new HashMap<>();
    private static final Set<Projectile> SLOWED = Collections.newSetFromMap(new WeakHashMap<>());
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
            // ---------------- Telepathy ----------------
            case "persuasion" -> {
                boolean silver = has(p, "telepathy_silver_tongue");
                int ticks = silver ? 800 : 400;
                LivingEntity target = Targeting.livingEntityInSight(p, silver ? 24 : 16, t -> t != p);
                List<LivingEntity> targets = new ArrayList<>();
                if (target != null) targets.add(target);
                if (has(p, "telepathy_edens_voice"))
                    targets.addAll(level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(8), m -> m instanceof Enemy && !targets.contains(m)));
                if (targets.isEmpty()) return false;
                for (LivingEntity t : targets) {
                    if (t instanceof Villager) {
                        p.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 1200, 0));
                    } else if (t instanceof Mob mob) {
                        mob.setTarget(null);
                        PERSUADED.put(mob.getUUID(), new Persuaded(mob, p, now + ticks));
                    } else if (t instanceof ServerPlayer other && hostile(p, other)) {
                        COMPELLED.put(other.getUUID(), now + 120);
                        COMPELLER.put(other.getUUID(), p.getUUID());
                        other.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 1));
                        other.displayClientMessage(Component.translatable("technique.heroesevolved.persuasion.compelled", p.getName()), true);
                    }
                    AbilityEffects.send(level, Kind.PSYCHIC_TETHER, p.getEyePosition(), t.getEyePosition(), 1);
                }
            }
            case "memory_walk" -> {
                boolean deep = has(p, "telepathy_deep_read");
                LivingEntity target = Targeting.livingEntityInSight(p, 24, t -> t != p);
                if (target instanceof ServerPlayer other) {
                    var power = PlayerAbilityService.getData(other).assignedPower();
                    p.sendSystemMessage(power.map(e -> Component.translatable("technique.heroesevolved.memory_walk.power", other.getName(),
                                    Component.translatable("ability." + e.getKey().getNamespace() + "." + e.getKey().getPath()),
                                    e.getValue().isUnlocked() ? "awake" : "dormant"))
                            .orElse(Component.translatable("technique.heroesevolved.memory_walk.none", other.getName())).copy().withStyle(ChatFormatting.LIGHT_PURPLE));
                }
                InvisibilityService.revealAround(level, p.position(), deep ? 48 : 32, 200);
                if (deep) for (Mob mob : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(32), m -> m instanceof Enemy)) PrivateGlow.glow(p, mob, 200);
                if (target != null && has(p, "telepathy_dreamweaver") && hostile(p, target)) {
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 9));
                    target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 120));
                    target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 2));
                    if (target instanceof Mob mob) mob.setTarget(null);
                }
                AbilityEffects.send(level, Kind.MIND_PULSE, p.position(), p.getEyePosition(), 3);
            }
            case "hive_link" -> {
                List<ServerPlayer> linked = allies(p, 24);
                for (ServerPlayer member : linked) {
                    HIVE.put(member.getUUID(), now + 600);
                    HIVE_OWNER.put(member.getUUID(), p.getUUID());
                    member.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 600, 0, false, false));
                    for (ServerPlayer other : linked) if (other != member) PrivateGlow.glow(member, other, 600);
                }
                AbilityEffects.send(level, Kind.MIND_LINK, p.position(), p.getEyePosition(), 3);
            }
            // ---------------- Regeneration ----------------
            case "take_the_wound" -> {
                LivingEntity friend = Targeting.livingEntityInSight(p, 16, t -> t != p && friendly(p, t) && t.getHealth() < t.getMaxHealth());
                if (friend == null) return false;
                float healed = Math.min(8, friend.getMaxHealth() - friend.getHealth());
                friend.heal(healed);
                p.hurt(p.damageSources().magic(), healed / (has(p, "regeneration_martyrs_grace") ? 4 : 2));
                AbilityEffects.send(level, Kind.BLOOD_THREAD, friend.getEyePosition(), p.getEyePosition(), 1);
            }
            case "adaptation" -> {
                ADAPTING.put(p.getUUID(), now + 600);
                ADAPTED_TO.remove(p.getUUID());
                AbilityEffects.at(p, Kind.REGENERATION, 1.4);
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
            }
            // ---------------- Space-Time ----------------
            case "stasis_seal" -> {
                Seal existing = SEALS.remove(p.getUUID());
                if (existing != null) { release(existing, true); return true; }
                BlockHitResult hit = aimBlock(p, 24);
                Vec3 center = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : p.getEyePosition().add(p.getLookAngle().scale(12));
                SEALS.put(p.getUUID(), new Seal(p, level, center, has(p, "time_manipulation_wider_seal") ? 5 : 3, now + 200, new HashMap<>()));
                AbilityEffects.send(level, Kind.TEMPORAL_FIELD, center, center.add(0, 1, 0), 3);
            }
            case "shared_blink" -> {
                BlockHitResult hit = aimBlock(p, has(p, "time_manipulation_far_fold") ? 48 : 24);
                if (hit.getType() != HitResult.Type.BLOCK) return false;
                Vec3 to = Vec3.atBottomCenterOf(hit.getBlockPos().relative(hit.getDirection()));
                if (!SpeedService.safe(p, to)) return false;
                Vec3 offset = to.subtract(p.position());
                int carried = 0;
                for (LivingEntity friend : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(4), t -> t != p && friendly(p, t))) {
                    if (carried++ >= 3) break;
                    Vec3 dest = friend.position().add(offset);
                    if (SpeedService.safe(friend, dest)) { friend.teleportTo(dest.x, dest.y, dest.z); friend.resetFallDistance(); }
                }
                AbilityEffects.send(level, Kind.TEMPORAL_BURST, p.position(), to, 1.5);
                p.teleportTo(to.x, to.y, to.z);
                p.resetFallDistance();
                if (has(p, "time_manipulation_wayfinder"))
                    for (ServerPlayer ally : allies(p, 6)) { ally.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 1)); ally.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200)); }
            }
            case "accelerate" -> {
                int radius = has(p, "time_manipulation_era_radius") ? 5 : 3, calls = 0;
                BlockPos origin = p.blockPosition();
                for (int pass = 0; pass < 20 && calls < 600; pass++)
                    for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-radius, -1, -radius), origin.offset(radius, 2, radius))) {
                        var state = level.getBlockState(pos);
                        if (!state.isRandomlyTicking() || !WorldMutationPolicy.canEdit(p, level, pos)) continue;
                        if (level.random.nextInt(4) == 0) { state.randomTick(level, pos.immutable(), level.random); calls++; }
                    }
                if (has(p, "time_manipulation_eras_end"))
                    for (LivingEntity t : enemies(p, p.position(), radius)) { t.hurt(p.damageSources().magic(), 4); t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 160, 1)); }
                for (AgeableMob baby : level.getEntitiesOfClass(AgeableMob.class, p.getBoundingBox().inflate(radius), AgeableMob::isBaby)) baby.setAge(0);
                AbilityEffects.send(level, Kind.TEMPORAL_FIELD, p.position(), p.getEyePosition(), radius);
            }
            // ---------------- Radiation ----------------
            case "heat_vent" -> {
                boolean clean = has(p, "radiation_clean_vent");
                int vented = RadiationService.vent(p, clean ? 50 : 30);
                Vec3 aim = p.getLookAngle().normalize();
                for (LivingEntity t : enemies(p, p.position(), 7)) {
                    Vec3 delta = t.position().subtract(p.position());
                    if (delta.normalize().dot(aim) < 0.6) continue;
                    t.hurt(p.damageSources().inFire(), 3 + vented / 6F);
                    if (!clean) t.igniteForSeconds(4);
                }
                if (has(p, "radiation_containment")) { p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200)); p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200)); }
                AbilityEffects.send(level, Kind.NUCLEAR_RAY, p.getEyePosition(), p.getEyePosition().add(aim.scale(7)), 1.5);
            }
            case "reactor_core" -> {
                int vented = RadiationService.vent(p, has(p, "radiation_power_transfer") ? 40 : 20);
                PlayerEnergyService.restore(p, vented);
                for (ServerPlayer ally : allies(p, 6)) {
                    if (ally != p) PlayerEnergyService.restore(ally, 10);
                    if (has(p, "radiation_reactor")) { ally.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 200, 1)); ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200)); }
                }
                AbilityEffects.send(level, Kind.NUCLEAR_EMP, p.position(), p.getEyePosition(), 3);
            }
            case "glowing_path" -> {
                boolean catastrophe = has(p, "radiation_catastrophe");
                ZONES.add(new Zone("contamination", p, level, p.position(), p.position(), has(p, "radiation_wider_path") ? 9 : 6,
                        now + (catastrophe ? 400 : 200)));
                AbilityEffects.send(level, Kind.NUCLEAR_FALLOUT, p.position(), p.getEyePosition(), 6);
            }
            // ---------------- Sonic Resonance ----------------
            case "shatter_note" -> {
                BlockHitResult hit = aimBlock(p, 24);
                if (hit.getType() != HitResult.Type.BLOCK) return false;
                int half = has(p, "sonic_resonance_wider_shatter") ? 2 : 1;
                float hardness = has(p, "sonic_resonance_resonant_breaker") ? 3F : 0.6F;
                int broken = 0;
                for (BlockPos pos : BlockPos.betweenClosed(hit.getBlockPos().offset(-half, -half, -half), hit.getBlockPos().offset(half, half, half))) {
                    var state = level.getBlockState(pos);
                    float speed = state.getDestroySpeed(level, pos);
                    if (state.isAir() || speed < 0 || speed > hardness || state.hasBlockEntity() || !WorldMutationPolicy.canEdit(p, level, pos)) continue;
                    level.destroyBlock(pos, true, p);
                    if (++broken >= 64) break;
                }
                for (LivingEntity t : enemies(p, hit.getLocation(), half + 1.5)) t.hurt(p.damageSources().playerAttack(p), 4);
                com.jayptucker.heroesevolved.sonic.SonicService.voice(p, false);
                AbilityEffects.send(level, Kind.SONIC_WAVE, p.getEyePosition(), hit.getLocation(), 1);
            }
            case "echo_map" -> {
                int radius = has(p, "sonic_resonance_deep_echo") ? 20 : 12, shown = 0;
                BlockPos origin = p.blockPosition();
                for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-radius, -radius, -radius), origin.offset(radius, radius, radius))) {
                    var state = level.getBlockState(pos);
                    if (!state.is(Tags.Blocks.ORES) && !state.is(ModBlocks.HELIXITE_ORE.get()) && !state.is(ModBlocks.DEEPSLATE_HELIXITE_ORE.get())) continue;
                    level.sendParticles(p, ParticleTypes.GLOW, true, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, 3, .2, .2, .2, 0);
                    if (++shown >= 96) break;
                }
                if (has(p, "sonic_resonance_cartographer")) {
                    InvisibilityService.revealAround(level, p.position(), 32, 200);
                    for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(32), t -> t != p)) PrivateGlow.glow(p, t, 160);
                }
                p.displayClientMessage(Component.translatable("technique.heroesevolved.echo_map", shown), true);
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, new com.jayptucker.heroesevolved.network.AbilityEffectPayload(
                        Kind.SONIC_SENSE, p.position(), p.getEyePosition(), radius));
            }
            case "quiet_zone" -> {
                ZONES.add(new Zone("quiet", p, level, p.position(), p.position(), 6, now + (has(p, "sonic_resonance_long_silence") ? 300 : 200)));
                AbilityEffects.send(level, Kind.SONIC_SENSE, p.position(), p.getEyePosition(), 6);
            }
            // ---------------- Superspeed ----------------
            case "mirage" -> {
                MIRAGES.put(p.getUUID(), now + (has(p, "superspeed_lingering_mirage") ? 200 : 120));
                for (Mob mob : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(16), m -> m.getTarget() == p)) {
                    mob.setTarget(null);
                    mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
                }
                AbilityEffects.send(level, Kind.SPEED_VORTEX, p.position(), p.getEyePosition(), 3);
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
                Vec3 from = p.getEyePosition();
                for (LivingEntity t : struck) {
                    if (!PowerReactions.electric(p, t)) t.hurt(p.damageSources().lightningBolt(), 6 + momentum / 10F);
                    AbilityEffects.send(level, Kind.ELECTRIC_ARC, from, t.getEyePosition(), 1.5);
                    from = t.getEyePosition();
                }
                level.playSound(null, p.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.8F, 1.4F);
            }
            case "perception" -> {
                PERCEPTION.put(p.getUUID(), now + (has(p, "superspeed_long_perception") ? 200 : 120));
                AbilityEffects.send(level, Kind.TEMPORAL_FIELD, p.position(), p.getEyePosition(), 4);
            }
            // ---------------- Precognition ----------------
            case "foresight" -> {
                DODGES.put(p.getUUID(), has(p, "precognition_clear_sight") ? 5 : 3);
                DODGE_UNTIL.put(p.getUUID(), now + 200);
                if (has(p, "precognition_fatekeeper")) for (ServerPlayer ally : allies(p, 8)) if (ally != p) {
                    DODGES.put(ally.getUUID(), Math.max(1, DODGES.getOrDefault(ally.getUUID(), 0)));
                    DODGE_UNTIL.put(ally.getUUID(), now + 200);
                }
                AbilityEffects.send(level, Kind.PROPHECY_THREAD, p.position(), p.getEyePosition(), 2);
            }
            case "find_the_path" -> {
                int radius = has(p, "precognition_far_sight") ? 64 : 32;
                BlockPos origin = p.blockPosition(), found = null;
                double best = Double.MAX_VALUE;
                int checked = 0;
                for (BlockPos pos : BlockPos.withinManhattan(origin, radius, Math.min(radius, 48), radius)) {
                    if (++checked > 400_000) break;
                    var state = level.getBlockState(pos);
                    if (state.is(ModBlocks.HELIXITE_ORE.get()) || state.is(ModBlocks.DEEPSLATE_HELIXITE_ORE.get())) {
                        double d = pos.distSqr(origin);
                        if (d < best) { best = d; found = pos.immutable(); }
                        break;
                    }
                }
                if (found != null) {
                    Vec3 to = Vec3.atCenterOf(found), from = p.getEyePosition(), step = to.subtract(from).normalize();
                    for (int i = 1; i <= 12; i++) {
                        Vec3 point = from.add(step.scale(i * 1.5));
                        level.sendParticles(p, ParticleTypes.END_ROD, true, point.x, point.y, point.z, 1, 0, 0, 0, 0);
                    }
                    p.displayClientMessage(Component.translatable("technique.heroesevolved.find_the_path.helixite", (int) Math.sqrt(best)), true);
                } else p.displayClientMessage(Component.translatable("technique.heroesevolved.find_the_path.none"), true);
                if (has(p, "precognition_the_painter")) {
                    BlockPos village = level.findNearestMapStructure(StructureTags.VILLAGE, origin, 64, false);
                    if (village != null) p.sendSystemMessage(Component.translatable("technique.heroesevolved.find_the_path.village",
                            village.getX(), village.getZ()).withStyle(ChatFormatting.GOLD));
                }
            }
            case "shared_omen" -> {
                long until = now + (has(p, "precognition_long_omen") ? 240 : 160);
                for (ServerPlayer ally : allies(p, 24)) OMENS.put(ally.getUUID(), until);
                AbilityEffects.send(level, Kind.PROPHECY_THREAD, p.position(), p.getEyePosition(), 4);
            }
            default -> { return false; }
        }
        return true;
    }

    private static void release(Seal seal, boolean thrown) {
        boolean sender = has(seal.owner, "time_manipulation_return_to_sender");
        Vec3 aim = seal.owner.getLookAngle().normalize();
        for (var entry : seal.held.entrySet()) {
            Projectile shot = entry.getKey();
            if (shot.isRemoved()) continue;
            shot.setNoGravity(false);
            if (thrown) {
                if (sender) shot.setOwner(seal.owner);
                shot.setDeltaMovement(aim.scale(entry.getValue().length() * (sender ? 1.5 : 1.0) + 0.5));
            } else shot.setDeltaMovement(entry.getValue());
            shot.hurtMarked = true;
        }
    }

    // ------------------------------------------------------------------
    // Tick: fields, persuaded mobs, perception, seals
    // ------------------------------------------------------------------

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
                case "quiet" -> {
                    if (now % 5 != 0) break;
                    boolean harmonist = has(p, "sonic_resonance_harmonist");
                    for (Mob mob : zone.level.getEntitiesOfClass(Mob.class, new AABB(zone.center, zone.center).inflate(zone.radius))) mob.setTarget(null);
                    if (harmonist && now % 40 == 0)
                        for (ServerPlayer ally : zone.level.getEntitiesOfClass(ServerPlayer.class, new AABB(zone.center, zone.center).inflate(zone.radius),
                                a -> a == p || p.isAlliedTo(a))) ally.heal(1);
                }
                default -> { }
            }
        }

        // Persuaded mobs turn on the persuader's enemies.
        for (Persuaded persuaded : List.copyOf(PERSUADED.values())) {
            Mob mob = persuaded.mob;
            if (!mob.isAlive() || now >= persuaded.until) { PERSUADED.remove(mob.getUUID()); continue; }
            if (now % 20 == 0 && (mob.getTarget() == null || !mob.getTarget().isAlive())) {
                mob.level().getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(12),
                                t -> t != mob && t != persuaded.owner && !(t instanceof Mob m && PERSUADED.containsKey(m.getUUID())) && hostile(persuaded.owner, t))
                        .stream().findFirst().ifPresent(mob::setTarget);
            }
        }
        COMPELLED.values().removeIf(until -> until <= now);
        COMPELLER.keySet().removeIf(id -> !COMPELLED.containsKey(id));
        HIVE.values().removeIf(until -> until <= now);
        HIVE_OWNER.keySet().removeIf(id -> !HIVE.containsKey(id));
        // Shared Senses: whatever hunts one linked mind glows for all of them.
        if (now % 20 == 0) for (var entry : HIVE_OWNER.entrySet()) {
            ServerPlayer owner = server.getPlayerList().getPlayer(entry.getValue()), member = server.getPlayerList().getPlayer(entry.getKey());
            if (owner == null || member == null || !has(owner, "telepathy_shared_senses")) continue;
            for (Mob hunter : member.serverLevel().getEntitiesOfClass(Mob.class, member.getBoundingBox().inflate(32), m -> m.getTarget() == member))
                for (var linked : HIVE_OWNER.entrySet()) {
                    ServerPlayer viewer = linked.getValue().equals(entry.getValue()) ? server.getPlayerList().getPlayer(linked.getKey()) : null;
                    if (viewer != null) PrivateGlow.glow(viewer, hunter, 40);
                }
        }
        ADAPTING.values().removeIf(until -> until <= now);
        ADAPTED_TO.keySet().removeIf(id -> !ADAPTING.containsKey(id));
        DODGE_UNTIL.values().removeIf(until -> until <= now);
        DODGES.keySet().removeIf(id -> !DODGE_UNTIL.containsKey(id));
        OMENS.values().removeIf(until -> until <= now);
        COOLDOWNS.values().removeIf(until -> until <= now);

        // Superspeed: mirages hold attention, perception slows (or stops) what flies at you.
        for (var entry : List.copyOf(MIRAGES.entrySet())) {
            ServerPlayer p = server.getPlayerList().getPlayer(entry.getKey());
            if (p == null || now >= entry.getValue()) { MIRAGES.remove(entry.getKey()); continue; }
            if (has(p, "superspeed_time_remnant") && now % 20 == 0)
                for (LivingEntity t : enemies(p, p.position(), 4)) t.hurt(p.damageSources().playerAttack(p), 2);
        }
        for (var entry : List.copyOf(PERCEPTION.entrySet())) {
            ServerPlayer p = server.getPlayerList().getPlayer(entry.getKey());
            if (p == null || now >= entry.getValue()) { PERCEPTION.remove(entry.getKey()); continue; }
            boolean stop = has(p, "superspeed_flash_time");
            for (Projectile shot : p.serverLevel().getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(8), s -> s.getOwner() != p)) {
                if (stop) { shot.setDeltaMovement(Vec3.ZERO); shot.setNoGravity(true); }
                else if (SLOWED.add(shot)) shot.setDeltaMovement(shot.getDeltaMovement().scale(0.3));
                shot.hurtMarked = true;
            }
        }

        // Stasis seals hold projectiles until thrown or expired.
        for (Seal seal : List.copyOf(SEALS.values())) {
            if (now >= seal.until || !seal.owner.isAlive()) { SEALS.remove(seal.owner.getUUID()); release(seal, false); continue; }
            for (Projectile shot : seal.level.getEntitiesOfClass(Projectile.class, new AABB(seal.center, seal.center).inflate(seal.radius))) {
                if (seal.held.size() >= 24) break;
                seal.held.computeIfAbsent(shot, s -> s.getDeltaMovement());
                shot.setDeltaMovement(Vec3.ZERO);
                shot.setNoGravity(true);
                shot.hurtMarked = true;
            }
        }
    }

    // ------------------------------------------------------------------
    // Passives and capstones that react to events
    // ------------------------------------------------------------------

    @SubscribeEvent public static void target(LivingChangeTargetEvent event) {
        LivingEntity target = event.getNewAboutToBeSetTarget();
        if (target == null) return;
        Persuaded persuaded = PERSUADED.get(event.getEntity().getUUID());
        if (persuaded != null && (target == persuaded.owner || target instanceof ServerPlayer ally && persuaded.owner.isAlliedTo(ally))) {
            event.setCanceled(true);
            return;
        }
        if (target instanceof ServerPlayer player && MIRAGES.containsKey(player.getUUID()) && event.getEntity().distanceToSqr(player) > 4) {
            event.setCanceled(true);
            return;
        }
        // Shared Omen: the moment something chooses you, you brace.
        if (target instanceof ServerPlayer player && OMENS.containsKey(player.getUUID()) && cooldown(player.getUUID(), "omen", now(player), 40)) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 0));
            var owner = player.getServer().getPlayerList().getPlayers().stream().filter(p -> has(p, "precognition_isaacs_gift")).findAny();
            if (owner.isPresent()) player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 0));
        }
    }

    @SubscribeEvent public static void attack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer attacker && COMPELLED.containsKey(attacker.getUUID())
                && event.getTarget().getUUID().equals(COMPELLER.get(attacker.getUUID()))) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void damage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        long now = victim.getServer() == null ? 0 : now(victim.getServer());

        // Foresight: dodge a blow you saw coming.
        Integer dodges = DODGES.get(victim.getUUID());
        if (dodges != null && dodges > 0 && event.getSource().getEntity() != null) {
            if (dodges == 1) DODGES.remove(victim.getUUID()); else DODGES.put(victim.getUUID(), dodges - 1);
            event.setCanceled(true);
            if (victim.level() instanceof ServerLevel level)
                level.sendParticles(ParticleTypes.END_ROD, victim.getX(), victim.getY() + 1, victim.getZ(), 6, .3, .5, .3, .02);
            return;
        }

        if (victim instanceof ServerPlayer player) {
            // Adaptation: the same harm twice is less harm.
            if (ADAPTING.containsKey(player.getUUID())) {
                String type = event.getSource().typeHolder().getRegisteredName();
                if (type.equals(ADAPTED_TO.get(player.getUUID())))
                    event.setAmount(event.getAmount() * (has(player, "regeneration_thick_skin") ? 0.4F : 0.6F));
                ADAPTED_TO.put(player.getUUID(), type);
            }
            if (has(player, "regeneration_perfect_adaptation") && (event.getSource().is(DamageTypeTags.IS_FIRE)
                    || event.getSource().is(net.minecraft.world.damagesource.DamageTypes.WITHER))) {
                event.setCanceled(true);
                return;
            }

            // Mind Network: linked friends share a quarter of each wound.
            UUID hiveOwner = HIVE_OWNER.get(player.getUUID());
            if (sharing == 0 && hiveOwner != null && player.getServer() != null) {
                ServerPlayer owner = player.getServer().getPlayerList().getPlayer(hiveOwner);
                if (owner != null && has(owner, "telepathy_mind_network")) {
                    List<ServerPlayer> others = player.getServer().getPlayerList().getPlayers().stream()
                            .filter(o -> o != player && hiveOwner.equals(HIVE_OWNER.get(o.getUUID()))).toList();
                    if (!others.isEmpty()) {
                        float shared = event.getAmount() * 0.25F;
                        event.setAmount(event.getAmount() - shared);
                        sharing++;
                        try { for (ServerPlayer other : others) other.hurt(other.damageSources().magic(), shared / others.size()); }
                        finally { sharing--; }
                    }
                }
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
        ZONES.clear(); SEALS.clear(); PERSUADED.clear(); COMPELLED.clear(); COMPELLER.clear(); ADAPTING.clear(); ADAPTED_TO.clear();
        HIVE.clear(); HIVE_OWNER.clear(); MIRAGES.clear(); PERCEPTION.clear(); DODGES.clear(); DODGE_UNTIL.clear(); OMENS.clear();
        COOLDOWNS.clear(); SLOWED.clear(); sharing = 0;
    }
}
