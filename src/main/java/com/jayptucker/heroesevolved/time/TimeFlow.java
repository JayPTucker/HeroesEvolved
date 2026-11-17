package com.jayptucker.heroesevolved.time;

import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.expansion.AdvancedTechniques;
import com.jayptucker.heroesevolved.mimicry.MimicryService;
import com.jayptucker.heroesevolved.network.TimeFlowPayload;
import com.jayptucker.heroesevolved.speed.SpeedService;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * How fast time runs where, and who moves through it faster than the world. Every slowed- or stopped-time field
 * (Between Seconds, Still World, a speedster's Perception) is gathered here into one picture, which the clients get as
 * one small {@link TimeFlowPayload}: rain, particles, lit fuses, the dust lifted off the ground, the rings fast things
 * leave in thick air and the blur of anyone moving through it are all drawn from it.
 * <p>
 * The rules that belong to slowed time as a whole live here too: a speedster at speed is dragged (as if running in
 * water) rather than frozen or left free, and a fuse burns slowly enough to be answered.
 */
public final class TimeFlow {
    /** A speedster running in slowed time keeps this share of their speed: still fast, but the air is thick as water. */
    public static final double DRAG_SLOW = 0.55;
    /** And in stopped time, where it is thicker still. */
    public static final double DRAG_STOP = 0.4;
    /** In slowed time a fuse (a swelling creeper, lit TNT) burns at most once every this many ticks: six times slower. */
    public static final int FUSE_INTERVAL = 6;
    /** How someone moving faster than the world looks: not at all, a speedster's gold, or a time traveller's amber. */
    public static final int NONE = 0, SPEED = 1, TIME = 2;
    /** A refresh, so a player who logs in, changes world or lost a packet catches up. */
    private static final int REFRESH = 20;

    /** One field: whose, where, how far it reaches and how slow (an interval of 0 is stopped). */
    public record Field(UUID owner, int ownerEntity, ResourceKey<Level> dimension, Vec3 center, double radius, int interval,
                        boolean follows) {
        public boolean stopped() { return interval == 0; }
    }

    private static TimeFlowPayload last;
    private static long lastSent;

    private TimeFlow() {}

    /** Every live field on the server. */
    public static List<Field> fields(MinecraftServer server) {
        List<Field> fields = new ArrayList<>();
        TimeSlowService.describe(server, fields);
        TimeStopService.describe(server, fields);
        AdvancedTechniques.describe(fields);
        return fields;
    }

    /** A speedster, or a Mimic channeling one. */
    public static boolean speedster(ServerPlayer player) {
        if (SpeedService.awakened(player)) return true;
        return MimicryService.isMimic(player) && ModAbilities.SUPERSPEED_ID.equals(MimicryService.memory(player).current());
    }

    /** At speed: the run's stride is on, not a speedster standing about. */
    public static boolean running(ServerPlayer player) {
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        return speed != null && speed.hasModifier(SpeedService.STRIDE);
    }

    public static boolean slowedAround(ServerPlayer player) {
        return TimeSlowService.reaches(player) || AdvancedTechniques.perceptionReaches(player);
    }

    public static boolean stoppedAround(ServerPlayer player) { return TimeStopService.reaches(player); }

    /**
     * The share of their speed a running speedster keeps in slowed or stopped time (their own Perception included).
     * 1 for everyone else, who is slowed by the field's own rule instead.
     */
    public static double drag(ServerPlayer player) {
        if (!speedster(player) || !running(player)) return 1;
        if (stoppedAround(player)) return DRAG_STOP;
        return slowedAround(player) ? DRAG_SLOW : 1;
    }

    /**
     * Who moves faster than the world round them, and how they look doing it: whoever holds a field, a guest sharing
     * one, and any speedster at speed. Everyone else in a field is slowed (or stopped) with it.
     */
    public static int style(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator() || !(slowedAround(player) || stoppedAround(player))) return NONE;
        boolean speedster = speedster(player);
        boolean free = TimeSlowService.hasActiveField(player) || TimeStopService.hasActiveField(player)
                || AdvancedTechniques.perceiving(player) || TemporalSharing.shared(player) || speedster && running(player);
        if (!free) return NONE;
        return speedster ? SPEED : TIME;
    }

    /** A fuse's interval in a field of {@code interval}: never quicker than {@link #FUSE_INTERVAL} once time is slowed. */
    public static int fuse(int interval) {
        return interval > 1 ? Math.max(interval, FUSE_INTERVAL) : interval;
    }

    /** Sends the picture when it changes (a field starts or ends, someone starts or stops moving through it), and once a second. */
    public static void tick(MinecraftServer server) {
        List<Field> fields = fields(server);
        if (fields.isEmpty() && last == null) return;
        List<TimeFlowPayload.Mover> movers = new ArrayList<>();
        if (!fields.isEmpty())
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                int style = style(player);
                if (style != NONE) movers.add(new TimeFlowPayload.Mover(player.getId(), style));
            }
        TimeFlowPayload payload = TimeFlowPayload.of(TimeVeil.serverWide(), fields, movers);
        long now = server.overworld().getGameTime();
        if (payload.sameAs(last) && now - lastSent < REFRESH) return;
        // Nothing left: one empty picture clears every client, then nothing more is sent until time bends again.
        last = fields.isEmpty() ? null : payload;
        lastSent = now;
        PacketDistributor.sendToAllPlayers(payload);
    }

    /** A player who joins (or arrives from another world) is told at once. */
    public static void catchUp(ServerPlayer player) {
        if (last != null) PacketDistributor.sendToPlayer(player, last);
    }

    public static void clear() { last = null; lastSent = 0; }
}
