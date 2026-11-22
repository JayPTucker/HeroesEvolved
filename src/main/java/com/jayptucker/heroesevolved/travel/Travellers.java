package com.jayptucker.heroesevolved.travel;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.company.CompanyOps;
import com.jayptucker.heroesevolved.config.HeroesEvolvedConfig;
import com.jayptucker.heroesevolved.events.PlayerGroups;
import com.jayptucker.heroesevolved.village.Growth;
import com.jayptucker.heroesevolved.village.VillageIncidents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Travellers on the road: now and then, near each group of players, a party sets out for a village within
 * {@link #DESTINATION} blocks: a pedlar, a travelling healer, a caravan or refugees. Parties aren't saved; their people
 * carry {@link #TAG}, and one that loads again later (a chunk, a restart) and isn't part of a live party is turned away.
 * What the player sees: {@code docs/travellers.md}.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class Travellers {
    public static final String TAG = HeroesEvolved.MOD_ID + "_traveller";
    /** A party heads for a village this near a player, and appears this far from them. */
    public static final int DESTINATION = 256, NEAREST = 40, FURTHEST = 70;
    /** It never appears this close to anyone, nor this close to its village. */
    public static final double CLEAR = 24, FROM_VILLAGE = 30;
    private static final List<Party> PARTIES = new ArrayList<>();
    static final Set<UUID> LIVE = new HashSet<>();
    private static long nextRoll = -1;

    private Travellers() {}

    public static List<Party> all() { return List.copyOf(PARTIES); }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        for (Party p : List.copyOf(PARTIES)) {
            if (p.stage == Party.Stage.OVER) { PARTIES.remove(p); continue; }
            p.tick();
            if (p.stage == Party.Stage.OVER) PARTIES.remove(p);
        }
        ServerLevel level = server.overworld();
        long now = level.getGameTime();
        if (now % 20 != 0) return;
        int minutes = HeroesEvolvedConfig.COMMON.travellerMinutes.get();
        if (minutes <= 0) { nextRoll = -1; return; }
        if (nextRoll < 0 || nextRoll - now > minutes * 1200L) nextRoll = now + minutes * 1200L;
        if (now < nextRoll) return;
        nextRoll = now + minutes * 1200L;
        // One roll for each group of players, as for village incidents; never a second party on a group's roads.
        for (List<ServerPlayer> group : PlayerGroups.of(level, VillageIncidents.GROUP_RADIUS)) {
            if (busy(group) || level.random.nextDouble() >= HeroesEvolvedConfig.COMMON.travellerChance.get()) continue;
            ServerPlayer p = group.get(level.random.nextInt(group.size()));
            double roll = level.random.nextDouble();
            Party.Kind kind = roll < 0.35 ? Party.Kind.PEDLAR : roll < 0.60 ? Party.Kind.HEALER : roll < 0.85 ? Party.Kind.CARAVAN : Party.Kind.REFUGEES;
            start(p, kind);
        }
    }

    private static boolean busy(List<ServerPlayer> group) {
        for (Party party : PARTIES)
            for (ServerPlayer p : group)
                if (party.level == p.level() && p.position().distanceToSqr(party.center) <= Party.SEEN * Party.SEEN) return true;
        return false;
    }

    /** A party of {@code kind} on the road near {@code p}, heading for a village near them. Null if there is none to head for. */
    public static Party start(ServerPlayer p, Party.Kind kind) {
        ServerLevel level = p.serverLevel();
        if (level != level.getServer().overworld()) return null;
        // Refugees make for a haven if there is one; anyone else for the nearest village.
        BlockPos bell = kind == Party.Kind.REFUGEES ? Growth.haven(level, p.blockPosition(), DESTINATION) : null;
        if (bell == null) bell = level.getPoiManager().findClosest(poi -> poi.is(PoiTypes.MEETING), p.blockPosition(), DESTINATION,
                PoiManager.Occupancy.ANY).orElse(null);
        if (bell == null) return null;
        Vec3 at = spawnPoint(level, p, bell);
        if (at == null) return null;
        return start(level, kind, at, bell);
    }

    /** A party of {@code kind} setting out from {@code at} for the village round {@code bell} (tests, previews). */
    public static Party start(ServerLevel level, Party.Kind kind, Vec3 at, BlockPos bell) {
        Party party = switch (kind) {
            case PEDLAR -> Pedlar.start(level, at, bell);
            case HEALER -> Healer.start(level, at, bell);
            case CARAVAN -> Caravan.start(level, at, bell);
            case REFUGEES -> Refugees.start(level, at, bell);
        };
        if (party == null) return null;
        PARTIES.add(party);
        HeroesEvolved.LOGGER.info("Travellers: {} on the road to {}", kind, party.village);
        return party;
    }

    /** Where a party appears: {@link #NEAREST} to {@link #FURTHEST} blocks off, out of the way, on dry ground that ticks. */
    static Vec3 spawnPoint(ServerLevel level, ServerPlayer p, BlockPos bell) {
        for (int tries = 0; tries < 12; tries++) {
            double a = level.random.nextDouble() * Math.PI * 2, d = NEAREST + level.random.nextDouble() * (FURTHEST - NEAREST);
            Vec3 at = CompanyOps.ground(level, p.getX() + Math.cos(a) * d, p.getZ() + Math.sin(a) * d, p.getY());
            BlockPos pos = BlockPos.containing(at);
            if (!level.isPositionEntityTicking(pos) || !level.getFluidState(pos).isEmpty() || !level.getFluidState(pos.below()).isEmpty()) continue;
            if (at.distanceTo(Vec3.atBottomCenterOf(bell)) < FROM_VILLAGE) continue;
            boolean clear = true;
            for (ServerPlayer q : level.players()) if (q.position().distanceToSqr(at) < CLEAR * CLEAR) clear = false;
            if (clear) return at;
        }
        return null;
    }

    /** One of a party's people into the world: tagged, kept, and counted live. */
    static <T extends Mob> T add(ServerLevel level, T mob) {
        mob.addTag(TAG);
        mob.setPersistenceRequired();
        LIVE.add(mob.getUUID());
        if (!level.addFreshEntity(mob)) {
            LIVE.remove(mob.getUUID());
            return null;
        }
        return mob;
    }

    /** No longer travelling: let go (to stay where they are, settled), or gone from the world. */
    public static void release(Mob mob, boolean discard) {
        LIVE.remove(mob.getUUID());
        mob.removeTag(TAG);
        if (discard && !mob.isRemoved()) {
            if (mob.isLeashed()) mob.dropLeash(true, false);
            mob.discard();
        }
    }

    /** Every party sent on its way (admin). How many. */
    public static int clearAll() {
        int n = 0;
        for (Party p : List.copyOf(PARTIES)) {
            if (p.stage == Party.Stage.OVER) continue;
            p.gone();
            n++;
        }
        PARTIES.clear();
        return n;
    }

    @SubscribeEvent
    public static void joined(EntityJoinLevelEvent event) {
        Entity e = event.getEntity();
        if (!e.level().isClientSide() && e.getTags().contains(TAG) && !LIVE.contains(e.getUUID())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void stop(ServerStoppingEvent event) {
        PARTIES.clear();
        LIVE.clear();
        nextRoll = -1;
    }
}
