package com.jayptucker.heroesevolved.phasing;

import com.jayptucker.heroesevolved.speed.SpeedService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Moving through solid matter, shared by Density (D.L. Hawkins) and a speedster's Vibrate. Every
 * passage ends in a checked, open landing: you never stop inside a block, never pass bedrock or
 * barriers, and never cross ground the player may not touch.
 */
public final class Passage {
    private Passage() {}

    /** A block a phaser may pass: not bedrock or barrier, and not on ground the player may not touch. */
    public static boolean passable(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        if (!level.hasChunkAt(pos)) return false;
        var state = level.getBlockState(pos);
        return state.isAir() || state.getDestroySpeed(level, pos) >= 0 && level.mayInteract(player, pos);
    }

    /** True if the whole column the entity would occupy at {@code feet} may be passed. */
    private static boolean columnPassable(ServerPlayer player, Entity body, Vec3 feet) {
        for (double y = 0; y < body.getBbHeight(); y += 1)
            if (!passable(player, BlockPos.containing(feet.add(0, y, 0)))) return false;
        return passable(player, BlockPos.containing(feet.add(0, body.getBbHeight() - 0.01, 0)));
    }

    /**
     * The first open landing past at least one solid block along {@code direction}, within
     * {@code maximum} blocks, or null. Thin walls only count if something solid was actually passed.
     */
    public static Vec3 through(ServerPlayer player, Vec3 direction, double maximum) {
        Vec3 start = player.position();
        boolean passedSolid = false;
        for (double d = 0.5; d <= maximum; d += 0.5) {
            Vec3 point = start.add(direction.scale(d));
            if (!columnPassable(player, player, point)) return null;
            if (!SpeedService.safe(player, point)) passedSolid = true;
            else if (passedSolid) return point;
        }
        return null;
    }

    /** How many blocks of solid matter lie between {@code from} and the landing {@code to}. */
    public static double solidBetween(ServerPlayer player, Vec3 from, Vec3 to) {
        double length = from.distanceTo(to), solid = 0;
        if (length < 1.0E-4) return 0;
        Vec3 step = to.subtract(from).scale(0.25 / length);
        Vec3 at = from;
        for (double d = 0; d < length; d += 0.25, at = at.add(step))
            if (!SpeedService.safe(player, at)) solid += 0.25;
        return solid;
    }

    /**
     * Toward {@code goal}, passing through walls no thicker than {@code maxSolid}: the farthest open
     * point reached, or the start if nothing could be passed.
     */
    public static Vec3 sweep(ServerPlayer player, Vec3 goal, double maxSolid) {
        Vec3 start = player.position(), delta = goal.subtract(start);
        double length = Math.min(48, delta.length());
        if (length < 1.0E-4) return start;
        Vec3 direction = delta.normalize(), last = start;
        double run = 0;
        for (double d = 0.25; d <= length; d += 0.25) {
            Vec3 point = start.add(direction.scale(d));
            if (!columnPassable(player, player, point)) break;
            if (SpeedService.safe(player, point)) { last = point; run = 0; }
            else if ((run += 0.25) > maxSolid) break;
        }
        return last;
    }
}
