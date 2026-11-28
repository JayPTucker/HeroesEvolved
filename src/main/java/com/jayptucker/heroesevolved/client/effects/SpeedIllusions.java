package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient;
import com.jayptucker.heroesevolved.entity.client.ShapeDecoyRenderer;
import com.jayptucker.heroesevolved.shapeshift.ShapeDecoy;
import com.jayptucker.heroesevolved.speed.SpeedUltimates;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A speedster's copies drawn as the speedster: Between Heartbeats' illusions and a Mirage's running copies are
 * {@code ShapeDecoy}s wearing the speedster's own body ({@code ShapeDecoyRenderer}). Each tick this keeps that body's
 * pose current, leaves real afterimages of it along every fast stride, and makes an illusion (and the speedster while
 * Between Heartbeats opens) <b>vibrate</b>: two faint gold copies of the body a hand's width to either side, flickering
 * side to side every tick, so the shape is never quite still.
 *
 * <p>{@link SpeedAfterimages} asks {@link #bodyNear} too, so a dodge, a zip or a burst sent at a copy leaves an
 * afterimage of the copy, not of a figure of light.</p>
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class SpeedIllusions {
    /** How far to either side a vibrating body's copies sit, and how long each lasts. */
    public static final double JITTER = .13;
    public static final int JITTER_LIFE = 3;
    private static final List<AbstractClientPlayer> BODIES = new ArrayList<>();
    private static final Map<Integer, Vec3> LAST = new HashMap<>();
    private static ClientLevel world;

    private SpeedIllusions() {}

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (world != mc.level) { world = mc.level; BODIES.clear(); LAST.clear(); }
        if (world == null || mc.player == null || mc.isPaused()) return;
        BODIES.clear();
        long now = world.getGameTime();
        boolean minimal = mc.options.particles().get() == ParticleStatus.MINIMAL;
        Set<Integer> seen = new HashSet<>();
        for (Entity e : world.entitiesForRendering()) {
            if (!(e instanceof ShapeDecoy copy) || !copy.speedster() || copy.isRemoved() || copy.distanceToSqr(mc.player) > 64 * 64) continue;
            if (!(ShapeDecoyRenderer.standIn(copy) instanceof AbstractClientPlayer body)) continue;
            ShapeDecoyRenderer.sync(copy, body);
            BODIES.add(body);
            seen.add(copy.getId());
            Vec3 at = copy.position(), was = LAST.put(copy.getId(), at);
            double moved = was == null ? 0 : was.distanceTo(at);
            // A stride too fast to see leaves afterimages of the copy along it.
            if (moved > .8 && moved < 20) SpeedAfterimages.trail(body, was, at, (int) Math.clamp(moved / (minimal ? 2.4 : 1.1), 1, 6));
            if (copy.driven()) vibrate(body, at, now, minimal);
        }
        LAST.keySet().retainAll(seen);
        // The speedster themself, blurring in place before Between Heartbeats splits them apart.
        for (SetPieceClient.Active a : SetPieceClient.running(Piece.SPEED_ULT_HEART)) {
            if (a.seed() != 0 || now - a.start() >= SpeedUltimates.BetweenHeartbeats.SPLIT) continue;
            if (world.getEntity(a.entity()) instanceof AbstractClientPlayer runner) vibrate(runner, runner.position(), now, minimal);
        }
    }

    /** Two faint copies of {@code body} to either side, the offset flipping each tick: a body vibrating too fast to hold still. */
    private static void vibrate(AbstractClientPlayer body, Vec3 at, long now, boolean minimal) {
        if (minimal && now % 3 != 0) return;
        double yaw = Math.toRadians(body.yBodyRot);
        Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        double swing = JITTER * ((now & 1) == 0 ? 1 : -.7);
        SpeedAfterimages.blur(body, at.add(right.scale(swing)), body.yBodyRot, SpeedAfterimages.GOLD, JITTER_LIFE, .85F);
        if (!minimal) SpeedAfterimages.blur(body, at.add(right.scale(-swing * .6)), body.yBodyRot, SpeedAfterimages.GOLD, JITTER_LIFE, .6F);
    }

    /** The copy's body nearest {@code near}, if one is closer (squared) than {@code within}; else null. */
    static AbstractClientPlayer bodyNear(Vec3 near, double within) {
        AbstractClientPlayer best = null;
        for (AbstractClientPlayer body : BODIES) {
            double d = body.position().distanceToSqr(near);
            if (d < within) { best = body; within = d; }
        }
        return best;
    }
}
