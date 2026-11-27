package com.jayptucker.heroesevolved.expansion;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * The signature abilities that play out over time, in one place: each is a {@link Run} keyed by its mechanic and its
 * caster, ticked on the server tick with its {@code ServerPlayer} kept (so game-test players run too), given a free
 * second press, and cancelled quietly on death, logout, a change of dimension or a dampened power. {@code SkillCatalog}
 * sends any signature node whose mechanic is routed here, whatever its power's own skill class is.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class Signatures {
    /** One signature playing out. */
    public abstract static class Run {
        public final ServerPlayer player;
        public final ServerLevel level;
        public final int tier;
        /** Ticks since it began (1 on the first tick). */
        public int age;

        protected Run(ServerPlayer player, int tier) {
            this.player = player;
            this.level = player.serverLevel();
            this.tier = Math.clamp(tier, 1, 5);
        }

        /** One server tick; false once it is over. */
        protected abstract boolean tick();

        /** The free second press; false if it does nothing (then the key casts as usual). */
        protected boolean press() { return false; }

        /** Ended early (death, logout, a dampened power): tidy up without the payoff. */
        protected void cancel() {}
    }

    private record Route(Predicate<ServerPlayer> canUse, BiPredicate<ServerPlayer, Integer> activate) {}

    private static final Map<String, Route> ROUTES = Map.ofEntries(
            Map.entry("epicenter", new Route(com.jayptucker.heroesevolved.quake.Epicenter::canUse, com.jayptucker.heroesevolved.quake.Epicenter::activate)),
            Map.entry("microburst", new Route(com.jayptucker.heroesevolved.flight.Microburst::canUse, com.jayptucker.heroesevolved.flight.Microburst::activate)),
            Map.entry("puppeteer", new Route(com.jayptucker.heroesevolved.telepathy.Puppeteer::canUse, com.jayptucker.heroesevolved.telepathy.Puppeteer::activate)),
            Map.entry("fission_star", new Route(com.jayptucker.heroesevolved.radiation.FissionStar::canUse, com.jayptucker.heroesevolved.radiation.FissionStar::activate)),
            Map.entry("standing_wave", new Route(com.jayptucker.heroesevolved.sonic.StandingWave::canUse, com.jayptucker.heroesevolved.sonic.StandingWave::activate)),
            Map.entry("lightning_round", new Route(com.jayptucker.heroesevolved.speed.LightningRound::canUse, com.jayptucker.heroesevolved.speed.LightningRound::activate)),
            Map.entry("bastion", new Route(com.jayptucker.heroesevolved.invisibility.Bastion::canUse, com.jayptucker.heroesevolved.invisibility.Bastion::activate)),
            Map.entry("mountain_armor", new Route(com.jayptucker.heroesevolved.phasing.MountainArmor::canUse, com.jayptucker.heroesevolved.phasing.MountainArmor::activate)),
            Map.entry("chorus", new Route(com.jayptucker.heroesevolved.mimicry.Chorus::canUse, com.jayptucker.heroesevolved.mimicry.Chorus::activate)),
            Map.entry("ballista", new Route(com.jayptucker.heroesevolved.assembly.Ballista::canUse, com.jayptucker.heroesevolved.assembly.Ballista::activate)),
            Map.entry("leviathan", new Route(com.jayptucker.heroesevolved.hydro.Leviathan::canUse, com.jayptucker.heroesevolved.hydro.Leviathan::activate)),
            Map.entry("overdrive", new Route(com.jayptucker.heroesevolved.amplify.Overdrive::canUse, com.jayptucker.heroesevolved.amplify.Overdrive::activate)));

    private record Key(String mechanic, UUID player) {}
    private static final Map<Key, Run> RUNS = new LinkedHashMap<>();

    private Signatures() {}

    /** Whether this mechanic is one of the signatures routed here. */
    public static boolean handles(String mechanic) { return ROUTES.containsKey(mechanic); }

    public static boolean canUse(AbilityUseContext c, String mechanic) {
        ServerPlayer p = c.player();
        Route route = ROUTES.get(mechanic);
        return route != null && !p.isPassenger() && PowerRules.canAct(p) && !running(mechanic, p) && route.canUse().test(p);
    }

    public static boolean activate(AbilityUseContext c, String mechanic) {
        Route route = ROUTES.get(mechanic);
        return route != null && route.activate().test(c.player(), c.abilityLevel());
    }

    /** The free second press of a running signature. */
    public static boolean press(ServerPlayer player, String mechanic) {
        Run run = RUNS.get(new Key(mechanic, player.getUUID()));
        return run != null && run.press();
    }

    /** Starts a run; false if this caster already has one of this mechanic going. */
    public static boolean start(String mechanic, Run run) {
        Key key = new Key(mechanic, run.player.getUUID());
        if (RUNS.containsKey(key)) return false;
        RUNS.put(key, run);
        return true;
    }

    public static boolean running(String mechanic, ServerPlayer player) { return RUNS.containsKey(new Key(mechanic, player.getUUID())); }

    @SuppressWarnings("unchecked")
    public static <T extends Run> T get(String mechanic, ServerPlayer player) { return (T) RUNS.get(new Key(mechanic, player.getUUID())); }

    /** Every running signature of this mechanic, whoever cast it. */
    @SuppressWarnings("unchecked")
    public static <T extends Run> List<T> all(String mechanic) {
        if (RUNS.isEmpty()) return List.of();
        List<T> out = new ArrayList<>();
        for (Map.Entry<Key, Run> e : RUNS.entrySet()) if (e.getKey().mechanic().equals(mechanic)) out.add((T) e.getValue());
        return out;
    }

    /** Ends a run now, without cancelling it (the run has already paid out). */
    public static void end(String mechanic, ServerPlayer player) { RUNS.remove(new Key(mechanic, player.getUUID())); }

    /** Every run of this player's, cancelled. */
    public static void forget(ServerPlayer player) {
        for (Map.Entry<Key, Run> e : List.copyOf(RUNS.entrySet()))
            if (e.getKey().player().equals(player.getUUID())) {
                RUNS.remove(e.getKey());
                e.getValue().cancel();
            }
    }

    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event) { tick(event.getServer()); }

    public static void tick(MinecraftServer server) {
        if (RUNS.isEmpty()) return;
        for (Map.Entry<Key, Run> e : List.copyOf(RUNS.entrySet())) {
            Run run = e.getValue();
            ServerPlayer p = run.player;
            if (p.getServer() != server || RUNS.get(e.getKey()) != run) continue;
            if (!p.isAlive() || p.isRemoved() || p.serverLevel() != run.level || !PowerRules.canAct(p)) {
                RUNS.remove(e.getKey());
                run.cancel();
                continue;
            }
            run.age++;
            boolean going;
            try { going = run.tick(); }
            catch (RuntimeException ex) {
                HeroesEvolved.LOGGER.error("Signature {} failed; ending it", e.getKey().mechanic(), ex);
                going = false;
            }
            if (!going && RUNS.get(e.getKey()) == run) RUNS.remove(e.getKey());
        }
    }

    // ------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------

    /** Foes of this player within {@code radius} of {@code point}, nearest first; {@code sight} asks for a clear line from the point. */
    public static List<LivingEntity> foes(ServerPlayer player, Vec3 point, double radius, boolean sight) {
        List<LivingEntity> found = new ArrayList<>(player.serverLevel().getEntitiesOfClass(LivingEntity.class,
                new AABB(point, point).inflate(radius), t -> ExpansionSkills.hostile(player, t)
                        && t.getBoundingBox().getCenter().distanceToSqr(point) <= radius * radius
                        && (!sight || clear(player.serverLevel(), point, t.getBoundingBox().getCenter()))));
        found.sort(Comparator.comparingDouble(t -> t.position().distanceToSqr(point)));
        return found.size() > 24 ? found.subList(0, 24) : found;
    }

    /** A clear line between two points (blocks only). */
    public static boolean clear(ServerLevel level, Vec3 a, Vec3 b) {
        return level.clip(new net.minecraft.world.level.ClipContext(a, b, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()))
                .getType() == net.minecraft.world.phys.HitResult.Type.MISS;
    }

    /** One blow that ignores the ordinary hurt cooldown, so a signature's own beats all land. */
    public static boolean hit(LivingEntity target, DamageSource source, float amount) {
        target.invulnerableTime = 0;
        return target.hurt(source, amount);
    }

    /** Bosses keep their footing: they take the damage but are not thrown, held or turned. */
    public static boolean boss(LivingEntity target) { return com.jayptucker.heroesevolved.telepathy.TelepathySkills.boss(target); }

    /** Throws a body, unless it is a boss. */
    public static void fling(LivingEntity target, Vec3 velocity) {
        if (boss(target)) return;
        target.setDeltaMovement(velocity);
        target.hurtMarked = true;
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { if (event.getEntity() instanceof ServerPlayer p) forget(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { if (event.getEntity() instanceof ServerPlayer p) forget(p); }
    @SubscribeEvent public static void death(LivingDeathEvent event) { if (event.getEntity() instanceof ServerPlayer p) forget(p); }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        for (Run run : List.copyOf(RUNS.values())) run.cancel();
        RUNS.clear();
    }
}
