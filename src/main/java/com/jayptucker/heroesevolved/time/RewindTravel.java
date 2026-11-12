package com.jayptucker.heroesevolved.time;

import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.network.RewindTravelPayload;
import com.jayptucker.heroesevolved.network.ScreenTintPayload;
import com.jayptucker.heroesevolved.network.TimeGhostPayload;
import com.jayptucker.heroesevolved.sounds.ModSounds;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Going back through your own past. When a rewind reaches a player (the one rewinding, anyone it puts back,
 * anyone whose death it undoes) they are not simply moved: for a few seconds they are carried backward along
 * the path they actually took, through every world it crossed, with the rewind's violet trail behind them and
 * echoes of themselves along the way, and then they land as they were. The world around them is already back.
 * While travelling they cannot act and cannot be hurt.
 *
 * <p>A long path (a speedster's minute) is played back at most {@link #MAX_STEP} blocks a tick; what is left
 * is crossed in one cut at the end. Jumps in the recorded path (teleports, respawns, portals) are crossed in no
 * time. Players without a client (machines' fake players, tests) land at once.</p>
 */
public final class RewindTravel {
    /** Longest trip back: for Overrun's minute, and for Rewrite the Moment's fifteen seconds. */
    public static final int MINUTE_TICKS = 60, MOMENT_TICKS = 30;
    /** Shortest trip, so standing still still feels like going back. */
    private static final int SHORTEST = 16;
    /** Fastest a trip moves, in blocks a tick. */
    public static final double MAX_STEP = 6;
    /** A step between two recorded points longer than this was a teleport. */
    private static final double JUMP = 24;

    /** Where a trip is at one tick. */
    public record Stop(ResourceKey<Level> dimension, Vec3 at, float yaw, float pitch) {}

    private static final class Trip {
        final UUID player;
        final List<Stop> stops;
        final Consumer<ServerPlayer> land;
        int age;
        ResourceKey<Level> shown;
        Trip(UUID player, List<Stop> stops, Consumer<ServerPlayer> land) { this.player = player; this.stops = stops; this.land = land; }
    }

    private static final Map<UUID, Trip> TRIPS = new HashMap<>();

    private RewindTravel() {}

    public static boolean travelling(Entity entity) { return TRIPS.containsKey(entity.getUUID()); }

    /**
     * The stops of a trip back along {@code path} (newest first), one a tick, in at most {@code ticks} ticks.
     * The first stop is where the path starts (now); the last is where it ends (the moment gone back to).
     */
    public static List<Stop> plan(List<TimeRewindPlayers.Point> path, int ticks) {
        List<Stop> stops = new ArrayList<>();
        int n = path.size();
        if (n == 0) return stops;
        double[] cumulative = new double[n];
        for (int i = 1; i < n; i++) {
            TimeRewindPlayers.Point a = path.get(i - 1), b = path.get(i);
            double d = a.dimension().equals(b.dimension()) ? a.at().distanceTo(b.at()) : Double.MAX_VALUE;
            cumulative[i] = cumulative[i - 1] + (d <= JUMP ? d : 0);
        }
        double total = cumulative[n - 1], shown = Math.min(total, MAX_STEP * ticks);
        int count = Mth.clamp((int) Math.ceil(shown / 1.2), SHORTEST, Math.max(SHORTEST, ticks));
        int j = 0;
        for (int k = 0; k <= count; k++) {
            double s = shown * k / count;
            // Jumps (no length) are crossed as soon as the trip reaches them; the first stop is always now.
            while (k > 0 && j < n - 1 && cumulative[j + 1] <= s) j++;
            TimeRewindPlayers.Point a = path.get(j);
            if (j >= n - 1) { stops.add(new Stop(a.dimension(), a.at(), a.yaw(), a.pitch())); continue; }
            TimeRewindPlayers.Point b = path.get(j + 1);
            double length = cumulative[j + 1] - cumulative[j];
            float f = length <= 1e-6 ? 0 : (float) ((s - cumulative[j]) / length);
            stops.add(new Stop(a.dimension(), a.at().lerp(b.at(), f), Mth.rotLerp(f, a.yaw(), b.yaw()), Mth.lerp(f, a.pitch(), b.pitch())));
        }
        // Whatever the trip did not have time to show is crossed in one cut: it always ends where the path does.
        TimeRewindPlayers.Point last = path.get(n - 1);
        stops.set(stops.size() - 1, new Stop(last.dimension(), last.at(), last.yaw(), last.pitch()));
        return stops;
    }

    /**
     * A landing that sends each player back through their own past since {@code cutoff} (at most {@code ticks}
     * ticks), then puts them back as {@code state} has them, shows them arriving, and runs {@code then}.
     */
    public static TimeRewindEntities.Landing landing(long cutoff, int ticks, Consumer<ServerPlayer> then) {
        return (player, level, state) -> send(player, cutoff, ticks, current -> {
            if (current == null) {
                PendingPlayerRewinds.get(level.getServer()).put(player.getUUID(), level.dimension().location(), state);
                return;
            }
            TimeRewindEntities.restorePlayer(current, level, state);
            arrive(current);
            if (then != null) then.accept(current);
        });
    }

    /** Carries this player back along their path since {@code cutoff}, then runs {@code land} (with them, or null if they left). */
    public static void send(ServerPlayer player, long cutoff, int ticks, Consumer<ServerPlayer> land) {
        send(player, TimeRewindPlayers.path(player.getUUID(), cutoff), ticks, land);
    }

    /** Carries this player back along {@code past} (newest first, as {@link TimeRewindPlayers#path} gives it), then runs {@code land}. */
    public static void send(ServerPlayer player, List<TimeRewindPlayers.Point> past, int ticks, Consumer<ServerPlayer> land) {
        finish(player.getServer(), player.getUUID());
        if (player instanceof FakePlayer) { land.accept(player); return; }
        long now = player.getServer().overworld().getGameTime();
        List<TimeRewindPlayers.Point> path = new ArrayList<>(past);
        path.add(0, new TimeRewindPlayers.Point(now, player.level().dimension(), player.position(), player.getYRot(), player.getXRot()));
        List<Stop> stops = plan(path, ticks);
        if (stops.size() < 2) { land.accept(player); return; }
        Trip trip = new Trip(player.getUUID(), stops, land);
        TRIPS.put(player.getUUID(), trip);
        PowerRules.rewinding(player, true);
        ServerLevel level = player.serverLevel();
        Vec3 from = player.position();
        AbilityEffects.send(level, Kind.TEMPORAL_BURST, from, from.add(0, 1, 0), 2);
        level.playSound(null, from.x, from.y, from.z, ModSounds.TIME_SLOW.get(), SoundSource.PLAYERS, 0.9F, 0.7F);
        ScreenTintPayload.send(player, 0x806A48E0, stops.size() + 4, ScreenTintPayload.Style.EDGE);
        show(trip, player);
        // Echoes of them along the way, newest first, as Rewrite the Moment has always shown: you watch yourself go back.
        int echoes = Math.min(6, stops.size() / 4);
        for (int i = 1; i <= echoes; i++) {
            Stop stop = stops.get(i * (stops.size() - 1) / (echoes + 1));
            if (stop.dimension.equals(level.dimension()) && stop.at.distanceToSqr(from) > 1)
                TimeGhostPayload.echo(player, stop.at, stop.yaw, 18, i * 3);
        }
    }

    /** Tells the traveller and everyone watching them where this stretch of the trip (in their world) goes. */
    private static void show(Trip trip, ServerPlayer player) {
        int from = Math.min(trip.age, trip.stops.size() - 1);
        ResourceKey<Level> world = trip.stops.get(from).dimension;
        List<Stop> run = new ArrayList<>();
        for (int i = from; i < trip.stops.size() && trip.stops.get(i).dimension.equals(world); i++) run.add(trip.stops.get(i));
        trip.shown = world;
        RewindTravelPayload.send(player, from, run);
    }

    public static void tick(MinecraftServer server) {
        for (Trip trip : List.copyOf(TRIPS.values())) {
            ServerPlayer player = server.getPlayerList().getPlayer(trip.player);
            if (player == null || !player.isAlive()) { land(server, trip); continue; }
            trip.age++;
            if (trip.age >= trip.stops.size() - 1) { land(server, trip); continue; }
            Stop previous = trip.stops.get(trip.age - 1), stop = trip.stops.get(trip.age);
            ServerLevel level = server.getLevel(stop.dimension);
            if (level == null) { land(server, trip); continue; }
            boolean crossing = level != player.serverLevel();
            player.teleportTo(level, stop.at.x, stop.at.y, stop.at.z, stop.yaw, stop.pitch);
            player.setDeltaMovement(Vec3.ZERO);
            player.resetFallDistance();
            if (crossing || !stop.dimension.equals(trip.shown)) show(trip, player);
            else if (trip.age % 2 == 0 && previous.dimension.equals(stop.dimension) && previous.at.distanceToSqr(stop.at) > 0.01)
                AbilityEffects.send(level, Kind.TEMPORAL_REWIND, previous.at, stop.at, 1 - trip.age / (double) trip.stops.size());
        }
    }

    private static void land(MinecraftServer server, Trip trip) {
        TRIPS.remove(trip.player);
        ServerPlayer player = server.getPlayerList().getPlayer(trip.player);
        if (player != null) PowerRules.rewinding(player, false);
        trip.land.accept(player);
    }

    /** Ends this player's trip now, landing them (when they log out, or when another rewind reaches them). */
    public static void finish(MinecraftServer server, UUID player) {
        Trip trip = TRIPS.get(player);
        if (trip != null) land(server, trip);
    }

    /** Lands everyone still travelling (the server is stopping). */
    public static void finishAll(MinecraftServer server) {
        for (Trip trip : List.copyOf(TRIPS.values())) land(server, trip);
    }

    /** Arriving: the flash and the sound Rewrite the Moment has always ended with. */
    public static void arrive(ServerPlayer player) {
        Vec3 to = player.position();
        ServerLevel level = player.serverLevel();
        player.resetFallDistance();
        AbilityEffects.send(level, Kind.TIME_REWIND_ARRIVE, to, to.add(0, 1, 0), 2.5);
        level.playSound(null, to.x, to.y, to.z, ModSounds.TIME_UNSLOW.get(), SoundSource.PLAYERS, 0.9F, 0.8F);
        ScreenTintPayload.send(player, 0x60C0A060, 10, ScreenTintPayload.Style.FLASH);
    }

    public static void clear() { TRIPS.clear(); }

}
