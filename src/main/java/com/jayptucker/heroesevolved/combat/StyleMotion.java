package com.jayptucker.heroesevolved.combat;

import com.jayptucker.heroesevolved.ability.service.PowerRules;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The movement half of every fighting style (firebending, lightning, waterbending): dashes the server re-sends each
 * tick so ground friction can't eat them, the evasion window a dodge opens, and the fall cushion after it. A style
 * decides what a dodge looks like and what its counter does; this class only moves the body and keeps the windows.
 *
 * <p>First built for Pyrokinesis's jet steps and backflip, whose feel (a quick shove, a short hop that falls the
 * client's way, a stop at a wall, a few ticks in which blows miss) is the bar for every style since.</p>
 */
public final class StyleMotion {
    /** What a blow met: nothing, a dodge it missed, or a dodge it missed first (the style's counter). */
    public enum Evasion { NONE, MISSED, COUNTER }

    /**
     * A dash: {@code push} each tick for {@code pushTicks} (with {@code lift} falling the client's way), or once at the
     * start when {@code impulse} (a flip, which flies on its own momentum). It lasts {@code length} ticks either way.
     * With {@code ease} above 0 the push fades as {@code (1 - age / pushTicks)^ease} (a skid).
     */
    private record Dash(ServerPlayer player, Vec3 push, double lift, long start, int pushTicks, int length,
                        boolean impulse, boolean flying, int ease) {}
    private static final class Evade { long until; boolean countered; }

    private static final Map<UUID, Dash> DASHES = new HashMap<>();
    private static final Map<UUID, Evade> EVADES = new HashMap<>();
    private static final Map<UUID, Long> CUSHIONS = new HashMap<>();
    private static final Map<UUID, Long> BRACES = new HashMap<>();

    private StyleMotion() {}

    private static long now(ServerPlayer player) { return player.getServer().overworld().getGameTime(); }

    /**
     * Starts a dash and pushes its first tick now. A flying body ({@code flying}) is pushed level, with no hop.
     * Anything already running for this player is replaced.
     */
    public static void dash(ServerPlayer player, Vec3 push, double lift, int pushTicks, int length, boolean impulse, boolean flying) {
        start(new Dash(player, push, lift, now(player), pushTicks, length, impulse, flying, 0));
    }

    /**
     * A skid: the body is thrown along {@code push} at full speed and the push eases off over {@code ticks} as a cube
     * ({@code (1 - age / ticks)^3}), so most of the ground goes in the first few ticks and the rest is a short slide to
     * a stop. It stays on the ground (no hop) and stops at a wall. It covers {@code push * skidReach(ticks)} blocks.
     */
    public static void skid(ServerPlayer player, Vec3 push, int ticks) {
        start(new Dash(player, push, 0, now(player), ticks, ticks, false, false, 3));
    }

    /** How many blocks a {@link #skid} of {@code ticks} covers for each block a tick of its starting push. */
    public static double skidReach(int ticks) {
        double sum = 0;
        for (int age = 0; age < ticks; age++) sum += Math.pow(1 - age / (double) ticks, 3);
        return sum;
    }

    private static void start(Dash dash) {
        DASHES.put(dash.player.getUUID(), dash);
        dash.player.resetFallDistance();
        push(dash, 0);
    }

    public static boolean dashing(ServerPlayer player) { return DASHES.containsKey(player.getUUID()); }

    /** Blows from creatures and shots miss for the next {@code ticks}; the first one is the style's counter. */
    public static void evade(ServerPlayer player, int ticks) {
        Evade evade = new Evade();
        evade.until = now(player) + ticks;
        EVADES.put(player.getUUID(), evade);
    }

    public static boolean evading(ServerPlayer player) {
        Evade evade = EVADES.get(player.getUUID());
        return evade != null && now(player) <= evade.until;
    }

    /**
     * Called for every blow a fighter takes. Falls, fire and anything without a creature or shot behind it still land,
     * and so does anything that bypasses invulnerability.
     */
    public static Evasion evade(ServerPlayer player, DamageSource source) {
        Evade evade = EVADES.get(player.getUUID());
        if (evade == null || now(player) > evade.until || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || source.getEntity() == player || source.getEntity() == null && source.getDirectEntity() == null) return Evasion.NONE;
        if (evade.countered) return Evasion.MISSED;
        evade.countered = true;
        return Evasion.COUNTER;
    }

    /**
     * Braces for {@code ticks}: blows still land, but the style decides what is left of each
     * ({@link FightingStyles.Style#braced}). Regeneration's brawler braces instead of dodging.
     */
    public static void brace(ServerPlayer player, int ticks) { BRACES.put(player.getUUID(), now(player) + ticks); }

    public static boolean bracing(ServerPlayer player) {
        Long until = BRACES.get(player.getUUID());
        return until != null && now(player) <= until;
    }

    /** Falls are cushioned for {@code ticks}, so a flip off a ledge lands soft. */
    public static void cushion(ServerPlayer player, int ticks) { CUSHIONS.put(player.getUUID(), now(player) + ticks); }

    public static boolean cushioned(ServerPlayer player) {
        Long until = CUSHIONS.get(player.getUUID());
        return until != null && now(player) <= until;
    }

    /** Re-sends the dash so ground friction doesn't eat it; the hop's fall is worked out the client's way. */
    private static void push(Dash dash, int age) {
        ServerPlayer player = dash.player;
        if (dash.impulse) {
            if (age == 0) push(player, dash.push.add(0, dash.lift, 0));
            return;
        }
        if (age >= dash.pushTicks) return;
        double vertical = dash.lift;
        if (!dash.flying) for (int i = 0; i < age; i++) vertical = (vertical - .08) * .98;
        Vec3 along = dash.ease > 0 ? dash.push.scale(Math.pow(1 - age / (double) dash.pushTicks, dash.ease)) : dash.push;
        push(player, along.add(0, dash.flying ? 0 : vertical, 0));
    }

    /** Sets a body's velocity on the server and, for a player, on their own client too. */
    public static void push(Entity target, Vec3 velocity) {
        target.setDeltaMovement(velocity);
        target.hurtMarked = true;
        if (target instanceof ServerPlayer other) other.connection.send(new ClientboundSetEntityMotionPacket(other));
    }

    public static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        for (Dash dash : List.copyOf(DASHES.values())) {
            ServerPlayer player = dash.player;
            int age = (int) (now - dash.start);
            if (player.isRemoved() || !player.isAlive() || !PowerRules.canAct(player) || age >= dash.length
                    || age > 0 && !dash.impulse && player.horizontalCollision) {
                DASHES.remove(player.getUUID());
                continue;
            }
            push(dash, age);
            player.resetFallDistance();
        }
        EVADES.values().removeIf(evade -> now > evade.until);
        CUSHIONS.values().removeIf(until -> now > until);
        BRACES.values().removeIf(until -> now > until);
    }

    public static void forget(ServerPlayer player) {
        UUID id = player.getUUID();
        DASHES.remove(id);
        EVADES.remove(id);
        CUSHIONS.remove(id);
        BRACES.remove(id);
    }

    public static void clear() {
        DASHES.clear();
        EVADES.clear();
        CUSHIONS.clear();
        BRACES.clear();
    }
}
