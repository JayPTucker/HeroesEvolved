package com.jayptucker.heroesevolved.styles;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.skills.SkillCatalog;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import com.jayptucker.heroesevolved.weapons.PowerWeapons;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * What the class abilities leave running: casts that act tick by tick (a storm, a sweeping blade, a coil
 * choosing targets), wards that change what happens when someone is hurt (a loop, a cocoon, a blur, a hive
 * sharing pain), zones that shelter whoever stands in them, and the class perks. Everything ends on its own,
 * when its caster can no longer act, logs out or changes dimension.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class StyleRuntime {
    /** One tick of a running cast; {@code age} counts from 0, the tick it was cast. */
    public interface Step { void run(int age); }

    private record Cast(ServerPlayer owner, long start, int ticks, Step step, Runnable end) {}
    private static final List<Cast> CASTS = new ArrayList<>();

    /** Protections and tricks attached to one entity, for a while. */
    public enum Ward { TIME_LOOP, EMBER, FARADAY, UNDYING, BLUR, REFRACTION, PHASE_FIELD, EMPATHY, HIVE, BLIND, LATTICE }

    public static final class WardState {
        public long until;
        public int charges;
        public float value;
        public ServerPlayer source;
        public List<LivingEntity> group = List.of();
    }

    private static final Map<UUID, EnumMap<Ward, WardState>> WARDS = new HashMap<>();

    /** A sheltered place: friends of its owner inside take {@code factor} of their damage. */
    private record Zone(ServerPlayer owner, Vec3 center, double radius, long until, float factor) {}
    private static final List<Zone> ZONES = new ArrayList<>();
    private static boolean sharing;

    private StyleRuntime() {}

    /** Runs {@code step} now and on each of the next {@code ticks} ticks, then {@code end} (if any). */
    public static void run(ServerPlayer owner, int ticks, Step step, Runnable end) {
        Cast cast = new Cast(owner, owner.serverLevel().getGameTime(), ticks, step, end);
        step.run(0);
        CASTS.add(cast);
    }

    public static void run(ServerPlayer owner, int ticks, Step step) { run(owner, ticks, step, null); }

    public static WardState ward(LivingEntity entity, Ward ward, int ticks, ServerPlayer source) {
        WardState state = new WardState();
        state.until = entity.level().getGameTime() + ticks;
        state.source = source;
        WARDS.computeIfAbsent(entity.getUUID(), id -> new EnumMap<>(Ward.class)).put(ward, state);
        return state;
    }

    public static Optional<WardState> warded(LivingEntity entity, Ward ward) {
        var wards = WARDS.get(entity.getUUID());
        WardState state = wards == null ? null : wards.get(ward);
        if (state == null || entity.level().getGameTime() > state.until) return Optional.empty();
        return Optional.of(state);
    }

    public static void unward(LivingEntity entity, Ward ward) {
        var wards = WARDS.get(entity.getUUID());
        if (wards != null) wards.remove(ward);
    }

    public static void zone(ServerPlayer owner, Vec3 center, double radius, int ticks, float factor) {
        ZONES.add(new Zone(owner, center, radius, owner.serverLevel().getGameTime() + ticks, factor));
    }

    // ------------------------------------------------------------------
    // Ticking and cleanup
    // ------------------------------------------------------------------

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long overworld = server.overworld().getGameTime();
        for (Cast cast : List.copyOf(CASTS)) {
            long age = cast.owner.serverLevel().getGameTime() - cast.start;
            boolean over = age > cast.ticks || cast.owner.isRemoved() || !cast.owner.isAlive() || !PowerRules.canAct(cast.owner);
            if (!over && age > 0) {
                try { cast.step.run((int) age); }
                catch (RuntimeException broken) { HeroesEvolved.LOGGER.warn("A class ability failed", broken); over = true; }
            }
            if (over || age >= cast.ticks) {
                CASTS.remove(cast);
                if (cast.end != null && cast.owner.isAlive() && !cast.owner.isRemoved()) cast.end.run();
            }
        }
        ZONES.removeIf(zone -> zone.owner.isRemoved() || zone.owner.serverLevel().getGameTime() > zone.until);
        if (overworld % 100 == 0) {
            WARDS.values().forEach(map -> map.values().removeIf(state -> state.source != null && state.source.isRemoved()));
            WARDS.values().removeIf(Map::isEmpty);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) supportPerk(player);
        }
    }

    public static void forget(ServerPlayer player) {
        CASTS.removeIf(cast -> cast.owner == player);
        WARDS.remove(player.getUUID());
        ZONES.removeIf(zone -> zone.owner == player);
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) forget(player);
    }

    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) forget(player);
    }

    @SubscribeEvent public static void stop(ServerStoppedEvent event) { CASTS.clear(); WARDS.clear(); ZONES.clear(); }

    // ------------------------------------------------------------------
    // Class perks
    // ------------------------------------------------------------------

    /** The role of the class ability this player has chosen for their power: "offense", "defense", "support" or "". */
    public static String role(ServerPlayer player) {
        return PlayerAbilityService.getData(player).assignedPower().filter(e -> e.getValue().isUnlocked())
                .flatMap(e -> SkillCatalog.chosenStyle(e.getKey().getPath(), SkillTreeService.build(player, e.getKey())))
                .map(SkillCatalog.Node::role).orElse("");
    }

    /** Support: friends near you regenerate, and so do you while one is near. */
    private static void supportPerk(ServerPlayer player) {
        if (!"support".equals(role(player)) || !PowerRules.canAct(player)) return;
        List<LivingEntity> friends = StyleOps.friends(player, player.position(), 12);
        if (friends.size() < 2) return;
        for (LivingEntity friend : friends) friend.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 110, 0, true, false, true));
    }

    // ------------------------------------------------------------------
    // Damage
    // ------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.HIGH) public static void incoming(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;
        var source = event.getSource();
        boolean world = source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
        // Offense hits harder; Defense takes less.
        if (source.getEntity() instanceof ServerPlayer attacker && attacker != victim && "offense".equals(role(attacker)))
            event.setAmount(event.getAmount() * 1.10F);
        if (victim instanceof ServerPlayer player && "defense".equals(role(player))) event.setAmount(event.getAmount() * 0.88F);
        if (world) return;
        long now = victim.level().getGameTime();
        for (Zone zone : ZONES)
            if (now <= zone.until && zone.owner.level() == victim.level() && victim.position().distanceToSqr(zone.center) <= zone.radius * zone.radius
                    && StyleOps.isFriend(zone.owner, victim)) event.setAmount(event.getAmount() * zone.factor);
        var wards = WARDS.get(victim.getUUID());
        if (wards == null) return;
        LivingEntity attacker = source.getEntity() instanceof LivingEntity living ? living : null;
        boolean melee = attacker != null && source.getDirectEntity() == attacker && attacker.distanceToSqr(victim) < 25;
        boolean projectile = source.getDirectEntity() instanceof Projectile;
        // Refraction Cloak: light bends round the body, and so does every shot aimed at it (TaCZ rounds included).
        if (active(victim, Ward.REFRACTION) && (projectile || PowerWeapons.isBullet(source.getDirectEntity()))) {
            event.setCanceled(true);
            refract(victim, source.getDirectEntity());
            return;
        }
        if (active(victim, Ward.PHASE_FIELD)) {
            if (projectile) { event.setCanceled(true); return; }
            event.setAmount(event.getAmount() * 0.8F);
        }
        WardState blur = state(victim, Ward.BLUR);
        if (blur != null && victim.getRandom().nextFloat() < 0.6F) {
            event.setCanceled(true);
            victim.level().playSound(null, victim.blockPosition(), net.minecraft.sounds.SoundEvents.BREEZE_WIND_CHARGE_BURST.value(),
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.4F, 1.8F);
            return;
        }
        WardState empathy = state(victim, Ward.EMPATHY);
        if (empathy != null && empathy.charges > 0) {
            empathy.charges--;
            event.setCanceled(true);
            if (victim instanceof ServerPlayer p) com.jayptucker.heroesevolved.energy.PlayerEnergyService.restore(p, 5);
            victim.level().playSound(null, victim.blockPosition(), net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_RESONATE,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 1.4F);
            if (empathy.charges <= 0) unward(victim, Ward.EMPATHY);
            return;
        }
        if (melee) {
            if (active(victim, Ward.EMBER)) {
                attacker.igniteForSeconds(4);
                attacker.hurt(victim.damageSources().onFire(), 3);
            }
            WardState faraday = state(victim, Ward.FARADAY);
            if (faraday != null && faraday.source != null) {
                attacker.invulnerableTime = 0;
                attacker.hurt(victim.damageSources().indirectMagic(faraday.source, faraday.source), 4);
                attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 2));
            }
        }
        WardState hive = state(victim, Ward.HIVE);
        if (hive != null && !sharing) {
            List<LivingEntity> linked = hive.group.stream().filter(e -> e != victim && e.isAlive() && !e.isRemoved()
                    && e.level() == victim.level() && e.distanceToSqr(victim) < 32 * 32).toList();
            if (!linked.isEmpty()) {
                float share = event.getAmount() / (linked.size() + 1);
                event.setAmount(share);
                sharing = true;
                try {
                    for (LivingEntity other : linked) {
                        other.invulnerableTime = 0;
                        other.hurt(victim.damageSources().magic(), share);
                    }
                } finally { sharing = false; }
            }
        }
    }

    /** The last word on a killing blow: a loop that closes, a cocoon that holds, a future that was already painted. */
    @SubscribeEvent(priority = EventPriority.LOW) public static void lethal(LivingDamageEvent.Pre event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        float health = victim.getHealth() + victim.getAbsorptionAmount();
        if (event.getNewDamage() < health) return;
        WardState loop = state(victim, Ward.TIME_LOOP);
        if (loop != null && victim instanceof ServerPlayer p) {
            event.setNewDamage(0);
            unward(victim, Ward.TIME_LOOP);
            StyleCasts.closeLoop(p, loop.value);
            return;
        }
        if (active(victim, Ward.UNDYING)) { event.setNewDamage(Math.max(0, health - 1)); return; }
    }

    /** Blind Spot: no mind near them can find them again for a while. */
    @SubscribeEvent public static void target(LivingChangeTargetEvent event) {
        if (event.getNewAboutToBeSetTarget() instanceof ServerPlayer player && active(player, Ward.BLIND)) event.setCanceled(true);
    }

    private static boolean active(LivingEntity entity, Ward ward) { return state(entity, ward) != null; }

    /** True while a Refraction Cloak bends shots round this body. */
    public static boolean refracting(LivingEntity entity) { return active(entity, Ward.REFRACTION); }

    /** A shot slides off the cloak: it is turned aside where it struck, with a glassy ring. */
    public static void refract(LivingEntity victim, net.minecraft.world.entity.Entity shot) {
        if (!(victim.level() instanceof net.minecraft.server.level.ServerLevel level)) return;
        Vec3 at = shot == null ? victim.getEyePosition() : shot.position();
        if (shot != null) {
            Vec3 v = shot.getDeltaMovement();
            // Turned 60 degrees round the vertical, the way the cloak's own bending turns shots further out.
            StyleOps.push(shot, new Vec3(v.x * 0.5 - v.z * 0.87, v.y, v.x * 0.87 + v.z * 0.5));
            if (PowerWeapons.isBullet(shot)) shot.discard();
        }
        com.jayptucker.heroesevolved.effects.AbilityEffects.send(level,
                com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind.OPTIC_BURST, at, at, 0.6);
        level.playSound(null, victim.blockPosition(), net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_HIT,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 1.7F);
    }

    private static WardState state(LivingEntity entity, Ward ward) {
        var wards = WARDS.get(entity.getUUID());
        WardState state = wards == null ? null : wards.get(ward);
        return state == null || entity.level().getGameTime() > state.until ? null : state;
    }
}
