package com.jayptucker.heroesevolved.ability;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.energy.StaminaDrain;
import com.jayptucker.heroesevolved.time.TimeSlowService;
import com.jayptucker.heroesevolved.time.TimeStopService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One question, one place: is this player off the ground because of the mod?
 *
 * <p>A dedicated server (with {@code allow-flight=false}, the default) kicks a player who floats for about four seconds
 * without creative flight, Levitation, an elytra or a riptide: "Flying is not enabled on this server". Nothing tells it
 * that a power is holding the player up, and single-player never checks, so hovers, rides, lifts, launches and being
 * frozen mid-air in stopped time all kicked players on servers. {@code mixin.ServerFloatingMixin} asks this before the
 * kick counts a tick, so a client flying on its own is still caught.</p>
 *
 * <p>A player is excused while the server is moving them, a held power is draining their stamina, time around them is
 * stopped or slowed, and for {@link #GRACE_TICKS} after any of those or after they used a power. Powers that grant
 * flight outright (Flight, Flame Flight, Telekinesis, a winged morph) set creative flight instead, which vanilla
 * already accepts.</p>
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class Airborne {
    /** How long floating stays the power's doing after the last sign of one (a move, a push, a drain): five seconds. */
    public static final int GRACE_TICKS = 100;
    private static final Map<UUID, Long> LAST = new ConcurrentHashMap<>();

    private Airborne() {
    }

    /** Whether the floating-too-long kick should let this player be: they are in the air because of a power. */
    public static boolean excused(ServerPlayer player) {
        long now = player.level().getGameTime();
        if (held(player)) {
            LAST.put(player.getUUID(), now);
            return true;
        }
        Long last = LAST.get(player.getUUID());
        return last != null && now - last >= 0 && now - last <= GRACE_TICKS;
    }

    /** Marks that the player just used a power: an action, a release, a fighting-style move, anything paid for. */
    public static void moved(ServerPlayer player) {
        LAST.put(player.getUUID(), player.level().getGameTime());
    }

    private static boolean held(ServerPlayer player) {
        // hurtMarked: the server set their motion this tick (a launch, a lift, a carry, a grab), not their own client.
        return player.hurtMarked
                || StaminaDrain.active(player)
                || TimeStopService.isStopped(player)
                || TimeStopService.hasActiveField(player)
                || TimeSlowService.reaches(player);
    }

    /** Server pushes are sent and cleared between ticks; look for them on both sides of the players' own ticks. */
    @SubscribeEvent
    public static void before(ServerTickEvent.Pre event) {
        notePushes(event);
    }

    @SubscribeEvent
    public static void after(ServerTickEvent.Post event) {
        notePushes(event);
    }

    private static void notePushes(ServerTickEvent event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers())
            if (player.hurtMarked) LAST.put(player.getUUID(), player.level().getGameTime());
    }

    @SubscribeEvent
    public static void leave(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST.remove(event.getEntity().getUUID());
    }
}
