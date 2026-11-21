package com.jayptucker.heroesevolved.village;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.config.HeroesEvolvedConfig;
import com.jayptucker.heroesevolved.data.EclipseTimeline;
import com.jayptucker.heroesevolved.events.EclipseService;
import com.jayptucker.heroesevolved.gifted.GiftedMobs;
import com.jayptucker.heroesevolved.gifted.Renegades;
import com.jayptucker.heroesevolved.mimicry.GiftedVillagers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * World events in villages, wherever they are: not only beside a player, but in a village up to
 * {@link HeroesEvolvedConfig.Common#villageIncidentRange} blocks away (640 by default), which calls for help on the
 * emergency band and stays loaded until it is over.
 * <ul>
 *   <li>Every {@link HeroesEvolvedConfig.Common#villageIncidentMinutes} minutes, a roll: a gifted villager somewhere wakes
 *   ({@link Renegades#awaken}), and half the time turns on their village.</li>
 *   <li>As each Eclipse leaves, the gifts it stirred wake: every dormant gift in a village near a player (up to three), and
 *   one more somewhere farther off.</li>
 *   <li>The Eclipse's meltdown ({@link VillagerMeltdown}) happens in a village found the same way.</li>
 * </ul>
 * Finding the village uses the world's own structure search (the one {@code /locate} uses), from a point a few hundred
 * blocks off in a random direction so it isn't always the nearest. Then a ticket loads it, and once its villagers are
 * there the event starts.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class VillageIncidents {
    /** What happens when the village is ready. */
    public enum Kind { AWAKENING, RENEGADE, GUARDIAN, MELTDOWN, OVERLOAD, METEOR }

    /** A village being loaded for an incident. */
    private static final class Pending {
        final ServerLevel level;
        BlockPos center;
        final Kind kind;
        final long since;
        long readyAt = -1;
        final String key;
        Pending(ServerLevel level, BlockPos center, Kind kind, long since, String key) {
            this.level = level; this.center = center; this.kind = kind; this.since = since; this.key = key;
        }
    }

    private static final List<Pending> PENDING = new ArrayList<>();
    /** How long a village may take to load before the incident is dropped. */
    static final int LOAD_TIMEOUT = 20 * 30;
    /** Once loaded, a moment for its villagers to arrive. */
    static final int SETTLE = 40;
    private static long nextRoll = -1;
    /** Players this close to one another count as together: one roll between them. */
    public static final int GROUP_RADIUS = 256;
    private static EclipseTimeline.Phase lastPhase;
    private static int sequence;

    private VillageIncidents() {}

    /**
     * Starts an incident of {@code kind} in a village near a random player (within the configured range). False if
     * there is no player or no village to be found; the incident itself begins once the village has loaded.
     */
    public static boolean request(MinecraftServer server, Kind kind) { return request(server, server.overworld().players(), kind); }

    /** As {@link #request(MinecraftServer, Kind)}, near one of {@code among} (a group of players). */
    public static boolean request(MinecraftServer server, List<ServerPlayer> among, Kind kind) {
        ServerLevel level = server.overworld();
        List<ServerPlayer> players = new ArrayList<>(among.stream().filter(p -> !p.isSpectator() && p.level() == level).toList());
        if (players.isEmpty()) return false;
        Collections.shuffle(players, new java.util.Random(level.random.nextLong()));
        for (ServerPlayer p : players) {
            BlockPos village = find(level, p.blockPosition(), HeroesEvolvedConfig.COMMON.villageIncidentRange.get(), level.random);
            if (village != null) { requestAt(level, village, kind); return true; }
        }
        return false;
    }

    /** Starts an incident of {@code kind} at the village round {@code village} once it has loaded. */
    public static void requestAt(ServerLevel level, BlockPos village, Kind kind) {
        String key = "incident:" + (sequence++);
        Pending p = new Pending(level, village, kind, level.getGameTime(), key);
        VillageTickets.hold(level, village, key);
        PENDING.add(p);
        // A viber nearby may see it before it happens.
        com.jayptucker.heroesevolved.vibe.VibeVisions.foresee(level, village, kind);
    }

    /**
     * A village within {@code range} blocks of {@code near}: searched from a point about half that far away in a random
     * direction, then from {@code near} itself. Null if the world has no villages there (or doesn't generate structures).
     */
    public static BlockPos find(ServerLevel level, BlockPos near, int range, RandomSource random) {
        if (!level.getServer().getWorldData().worldGenOptions().generateStructures()) return null;
        for (int attempt = 0; attempt < 3; attempt++) {
            double a = random.nextDouble() * Math.PI * 2, d = attempt < 2 ? range * (.3 + .3 * random.nextDouble()) : 0;
            BlockPos from = near.offset((int) (Math.cos(a) * d), 0, (int) (Math.sin(a) * d));
            BlockPos found;
            try {
                found = level.findNearestMapStructure(StructureTags.VILLAGE, from, Math.max(4, range / 16), false);
            } catch (RuntimeException e) {
                HeroesEvolved.LOGGER.warn("Village search failed", e);
                return null;
            }
            if (found != null && found.atY(near.getY()).distSqr(near) <= (long) range * range) return found;
        }
        return null;
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel overworld = server.overworld();
        long now = overworld.getGameTime();
        for (Pending p : List.copyOf(PENDING)) if (p.level.getServer() == server) tick(p);
        if (now % 20 != 0) return;
        eclipse(server);
        int minutes = HeroesEvolvedConfig.COMMON.villageIncidentMinutes.get();
        if (minutes <= 0) { nextRoll = -1; return; }
        if (nextRoll < 0 || nextRoll - now > minutes * 1200L) nextRoll = now + minutes * 1200L;
        if (now < nextRoll) return;
        nextRoll = now + minutes * 1200L;
        // One roll for each group of players: friends together share theirs, players far apart each get their own, as
        // often as anyone playing alone would (events/PlayerGroups).
        for (List<ServerPlayer> group : com.jayptucker.heroesevolved.events.PlayerGroups.of(overworld, GROUP_RADIUS))
            if (overworld.random.nextDouble() < HeroesEvolvedConfig.COMMON.villageIncidentChance.get()) request(server, group, Kind.AWAKENING);
    }

    private static void tick(Pending p) {
        long now = p.level.getGameTime();
        if (now - p.since > LOAD_TIMEOUT) { drop(p); return; }
        if (!p.level.isPositionEntityTicking(p.center)) return;
        if (p.readyAt < 0) {
            // The structure search gives the village's start at y 0: stand it on the ground once it exists.
            p.center = p.level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.center);
            p.readyAt = now + SETTLE;
            return;
        }
        if (now < p.readyAt) return;
        start(p);
        drop(p);
    }

    private static void drop(Pending p) {
        PENDING.remove(p);
        VillageTickets.release(p.key);
    }

    /** The village is here: pick who it happens to. */
    private static void start(Pending p) {
        BlockPos bell = Villages.bell(p.level, p.center);
        // One trouble at a time: a village already calling for help gets nothing more piled on it.
        if (com.jayptucker.heroesevolved.radio.EmergencyBand.troubled(p.level, bell)) {
            HeroesEvolved.LOGGER.info("Village incident {} at {} skipped: the village is already in trouble", p.kind, bell);
            return;
        }
        if (p.kind == Kind.METEOR) {
            // Nobody in the village causes this one: a great meteor comes down on its bell.
            com.jayptucker.heroesevolved.meteor.Meteors.great(p.level, bell, HeroesEvolvedConfig.COMMON.meteorGreatTicks.get());
            HeroesEvolved.LOGGER.info("Village incident METEOR at {}", bell);
            return;
        }
        List<Villager> adults = new ArrayList<>(Villages.members(p.level, bell).stream()
                .filter(v -> !v.isBaby() && !GiftedMobs.isGifted(v) && !VillagerMeltdown.critical(v)
                        && !com.jayptucker.heroesevolved.village.overload.Overloads.critical(v)
                        && !v.getTags().contains(com.jayptucker.heroesevolved.company.CompanyOps.PRISONER_TAG)).toList());
        if (adults.isEmpty()) {
            HeroesEvolved.LOGGER.info("Village incident at {} found nobody to happen to", bell);
            return;
        }
        Collections.shuffle(adults, new java.util.Random(p.level.random.nextLong()));
        // A villager already carrying a dormant gift goes first.
        Villager who = adults.stream().filter(v -> GiftedVillagers.gift(v) != null).findFirst().orElse(adults.getFirst());
        switch (p.kind) {
            case AWAKENING -> Renegades.awaken(who);
            case RENEGADE -> Renegades.awaken(who, true);
            case GUARDIAN -> Renegades.awaken(who, false);
            case MELTDOWN -> VillagerMeltdown.start(adults.getFirst(), HeroesEvolvedConfig.COMMON.villagerMeltdownFuseTicks.get());
            case METEOR -> { }
            case OVERLOAD -> {
                // Someone whose dormant gift has a storm goes first; otherwise the first of them, with a storm's gift.
                Villager eye = adults.stream().filter(v -> com.jayptucker.heroesevolved.village.overload.Storm.of(GiftedVillagers.gift(v)) != null)
                        .findFirst().orElse(adults.getFirst());
                var storm = com.jayptucker.heroesevolved.village.overload.Storm.of(GiftedVillagers.gift(eye));
                if (storm == null) storm = com.jayptucker.heroesevolved.village.overload.Storm.byId(p.level.random.nextInt(com.jayptucker.heroesevolved.village.overload.Storm.values().length));
                com.jayptucker.heroesevolved.village.overload.Overloads.start(eye, storm, HeroesEvolvedConfig.COMMON.overloadTicks.get());
            }
        }
        HeroesEvolved.LOGGER.info("Village incident {} at {}", p.kind, bell);
    }

    /** Villages waiting to load (tests). */
    public static int pending() { return PENDING.size(); }

    // ------------------------------------------------------------------
    // The Eclipse stirs gifts
    // ------------------------------------------------------------------

    /** As the Eclipse leaves, dormant gifts near players wake (up to three), and one more somewhere farther off. */
    private static void eclipse(MinecraftServer server) {
        EclipseTimeline.Phase phase = EclipseService.phase(server);
        if (phase == lastPhase) return;
        lastPhase = phase;
        if (phase != EclipseTimeline.Phase.DEPARTURE) return;
        wakeNear(server, 3);
        for (List<ServerPlayer> group : com.jayptucker.heroesevolved.events.PlayerGroups.of(server.overworld(), GROUP_RADIUS))
            request(server, group, Kind.AWAKENING);
    }

    /**
     * Wakes up to {@code most} dormant gifts in villages within 128 blocks of each group of players (so each group sees
     * as many as a player alone would). Never in a village already in trouble. How many woke.
     */
    public static int wakeNear(MinecraftServer server, int most) {
        ServerLevel level = server.overworld();
        int woke = 0;
        List<Villager> done = new ArrayList<>();
        for (List<ServerPlayer> group : com.jayptucker.heroesevolved.events.PlayerGroups.of(level, GROUP_RADIUS)) {
            List<Villager> gifted = new ArrayList<>();
            for (ServerPlayer p : group)
                for (Villager v : level.getEntitiesOfClass(Villager.class, p.getBoundingBox().inflate(128),
                        v -> v.isAlive() && !v.isBaby() && GiftedVillagers.gift(v) != null && !GiftedMobs.isGifted(v) && !VillagerMeltdown.critical(v)
                                && !com.jayptucker.heroesevolved.village.overload.Overloads.critical(v)
                                && !v.getTags().contains(com.jayptucker.heroesevolved.company.CompanyOps.PRISONER_TAG)
                                && !com.jayptucker.heroesevolved.radio.EmergencyBand.troubled(level, v.blockPosition())))
                    if (!gifted.contains(v) && !done.contains(v)) gifted.add(v);
            Collections.shuffle(gifted, new java.util.Random(level.random.nextLong()));
            int here = 0;
            for (Villager v : gifted) {
                if (here >= most) break;
                // One awakening per village: the first may already be calling for help.
                if (com.jayptucker.heroesevolved.radio.EmergencyBand.troubled(level, v.blockPosition())) continue;
                if (Renegades.awaken(v) != null) { here++; done.add(v); }
            }
            woke += here;
        }
        return woke;
    }

    @SubscribeEvent
    public static void stop(ServerStoppingEvent event) {
        for (Pending p : List.copyOf(PENDING)) drop(p);
        nextRoll = -1;
        lastPhase = null;
    }
}
