package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.network.SpeedCyclonePayload;
import com.jayptucker.heroesevolved.speed.TornadoShape;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * While you run a Cyclone of your own (its ring, a vacuum, a containment run or the tornado it rises into) or circle
 * your own Flight Tempest, the view is pulled back to watch it: you see your runner whipping round the ring, what it
 * has caught and, once it rises, the whole funnel. It stands behind the eye along the way you are looking (so a
 * Tempest still steers the way the screen faces) and turns only when you turn. For a ring it frames the ring, a few
 * blocks up; as a tornado rises it glides further out and up with the funnel. It glides out from your own view when
 * the run starts and back into it when the run is over, instead of cutting. The camera is moved by
 * {@code TornadoCameraMixin}; your player stays the camera's entity in third person, so it keeps moving and reporting
 * its position.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class TempestCamera {
    /** A speedster's tornado: how far back from the funnel the view stands, how high, and the height it looks at. */
    private static final double BACK = 26, UP = 12, LOOK_AT = 9;
    /** A Flight Tempest's, which the flyer circles high up its wall; and while the flyer's laps are still forming it. */
    private static final double SKY_BACK = 34, SKY_UP = 15, SKY_LOOK_AT = 11, SKY_FORM_BACK = 19, SKY_FORM_UP = 13, SKY_FORM_LOOK_AT = 6;
    /** A ring without a funnel: this many of its radii back and up, looking just over the runner's head. */
    private static final double RING_BACK = 2.4, RING_UP = 1.15, RING_LOOK_AT = 1.3;
    /** Ticks the view stays on the end of it before it comes home: a ring breaking, a tornado coming down, a Tempest's finale. */
    private static final int RING_LINGER = 8, TORNADO_LINGER = 18, TEMPEST_LINGER = 11;
    /** Longest the view takes to come home, and how close counts as home. */
    private static final int HOME_TICKS = 16;
    private static final double HOME_CLOSE = 0.6;
    /**
     * The glide, a critically damped follow (no overshoot): each tick the velocity keeps {@code KEEP} of itself and
     * gains {@code PULL} of the distance left, so it eases in and out, nine-tenths of the way in about twelve ticks.
     * The aim follows a little quicker. ({@code PULL} is just under {@code (1 - sqrt KEEP)^2}: any more overshoots.)
     */
    private static final double KEEP = 0.5, PULL = 0.0857, AIM_KEEP = 0.4, AIM_PULL = 0.1350;

    /** Where the view is and which way it looks. */
    public record Shot(Vec3 at, float yaw, float pitch) {}

    private static Vec3 spot, spotOld, spotVel = Vec3.ZERO;
    private static Vec3 focus, focusOld, focusVel = Vec3.ZERO;
    private static CameraType before;
    /** Ticks spent coming home, or -1 while watching. */
    private static int homing = -1;

    private TempestCamera() {}

    /** Your own Cyclone or Tempest, if one is running (or has only just ended) and you are with it. */
    private static SpeedCyclone.View mine(Minecraft mc) {
        if (mc.player == null) return null;
        long now = mc.level.getGameTime();
        for (SpeedCyclone.View v : SpeedCyclone.views()) {
            if (v.entity != mc.player.getId()) continue;
            float gone = v.gone(now, 0);
            switch (v.style) {
                case SpeedCyclonePayload.FLIGHT -> { if (gone < TEMPEST_LINGER) return v; }
                // A speedster's, for as long as they are running it: a tornado they have stepped out of is left behind.
                case SpeedCyclonePayload.SPEED, SpeedCyclonePayload.CONTAIN, SpeedCyclonePayload.CONTAIN_FAIL -> {
                    if (!v.free && gone < (v.rose() ? TORNADO_LINGER : RING_LINGER)) return v;
                }
                default -> { }
            }
        }
        return null;
    }

    /** A still view set by a preview driver (see {@link #fix}); it wins over everything else. */
    private static Shot fixed;

    /**
     * Holds the view still at {@code at}, looking at {@code lookAt}, until called with nulls. For preview drivers that
     * need a side view of something done along the look (a camera behind or in front of the player sees only their
     * body). Keep the game in a third-person mode so the player is still drawn.
     */
    public static void fix(Vec3 at, Vec3 lookAt) {
        if (at == null || lookAt == null) { fixed = null; return; }
        Vec3 look = lookAt.subtract(at);
        fixed = new Shot(at, (float) (Mth.atan2(-look.x, look.z) * Mth.RAD_TO_DEG),
                (float) (-Mth.atan2(look.y, look.horizontalDistance()) * Mth.RAD_TO_DEG));
    }

    /** True while the view is pulled back from you for a Cyclone or Tempest of your own (for previews and tests). */
    public static boolean watching() { return spot != null && homing < 0; }

    /** The view for this frame, or null when there is nothing to watch. */
    public static Shot shot(float partial) {
        if (fixed != null) return fixed;
        // A finisher of your own holds a still side view for its length.
        Shot finisher = com.jayptucker.heroesevolved.client.FinisherClient.shot();
        if (finisher != null) return finisher;
        // An Ultimate of your own flies its own shots.
        Shot ultimate = com.jayptucker.heroesevolved.client.UltimateCamera.shot(partial);
        if (ultimate != null) return ultimate;
        if (spot == null || spotOld == null) return null;
        Vec3 at = spotOld.lerp(spot, partial), look = focusOld.lerp(focus, partial).subtract(at);
        float yaw = (float) (Mth.atan2(-look.x, look.z) * Mth.RAD_TO_DEG);
        float pitch = (float) (-Mth.atan2(look.y, look.horizontalDistance()) * Mth.RAD_TO_DEG);
        return new Shot(at, yaw, pitch);
    }

    /**
     * Where the view wants to be for this run, and what it looks at. A ring is framed from a few of its radii back;
     * as its tornado rises the view moves out and up with the funnel's height, so it glides back as the funnel grows.
     */
    private static Vec3[] frame(Minecraft mc, SpeedCyclone.View v) {
        float yaw = mc.player.getYRot() * Mth.DEG_TO_RAD;
        Vec3 look = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        double back, up, lookAt;
        float built = v.built(mc.level.getGameTime(), 0);
        double rise = built < 0 ? 0 : Mth.clamp((TornadoShape.height(built) - 3) / (TornadoShape.HEIGHT - 3), 0, 1);
        if (v.style == SpeedCyclonePayload.FLIGHT) {
            // While it forms, close enough to see the flyer's laps; then out with the funnel.
            back = Mth.lerp(rise, SKY_FORM_BACK, SKY_BACK);
            up = Mth.lerp(rise, SKY_FORM_UP, SKY_UP);
            lookAt = Mth.lerp(rise, SKY_FORM_LOOK_AT, SKY_LOOK_AT);
        } else {
            double r = Math.max(4, v.radius);
            back = Mth.lerp(rise, r * RING_BACK, BACK);
            up = Mth.lerp(rise, r * RING_UP, UP);
            lookAt = Mth.lerp(rise, RING_LOOK_AT, LOOK_AT);
        }
        Vec3 aim = v.center.add(0, lookAt, 0);
        return new Vec3[] {clear(mc, aim, v.center.subtract(look.scale(back)).add(0, up, 0)), aim};
    }

    /** Where your own camera would be, and what it would look at: the view glides back there before handing over. */
    private static Vec3[] home(Minecraft mc) {
        Vec3 eye = mc.player.getEyePosition(), look = mc.player.getLookAngle();
        CameraType type = before == null ? CameraType.FIRST_PERSON : before;
        // Into first person it stops just behind the head and cuts the last step, so it never shows the inside of it.
        if (type == CameraType.FIRST_PERSON) return new Vec3[] {clear(mc, eye, eye.subtract(look.scale(2.5))), eye.add(look.scale(8))};
        if (type == CameraType.THIRD_PERSON_FRONT) return new Vec3[] {clear(mc, eye, eye.add(look.scale(4))), eye};
        return new Vec3[] {clear(mc, eye, eye.subtract(look.scale(4))), eye};
    }

    /** {@code goal}, or the last open point on the way to it from {@code from} if a wall is in the way. */
    private static Vec3 clear(Minecraft mc, Vec3 from, Vec3 goal) {
        var hit = mc.level.clip(new ClipContext(from, goal, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation().add(from.subtract(goal).normalize().scale(0.3)) : goal;
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) { release(mc); return; }
        if (mc.isPaused()) return;
        SpeedCyclone.View run = RewindTravelClient.travelling() ? null : mine(mc);
        Vec3[] frame;
        if (run != null) { frame = frame(mc, run); homing = -1; }
        else {
            // Nothing to watch: glide home from wherever the view is, if it is anywhere.
            if (spot == null || RewindTravelClient.travelling()) { release(mc); return; }
            frame = home(mc);
            if (++homing > HOME_TICKS || homing > 3 && spot.distanceTo(frame[0]) < HOME_CLOSE) { release(mc); return; }
        }
        if (spot == null) {
            // Set off from wherever your view is now, so it glides out instead of cutting.
            Camera camera = mc.gameRenderer.getMainCamera();
            Vec3 from = camera.isInitialized() ? camera.getPosition() : mc.player.getEyePosition();
            Vec3 ahead = camera.isInitialized() ? new Vec3(camera.getLookVector()) : mc.player.getLookAngle();
            spot = spotOld = from;
            focus = focusOld = from.add(ahead.scale(8));
            spotVel = focusVel = Vec3.ZERO;
        }
        spotOld = spot; focusOld = focus;
        spotVel = spotVel.scale(KEEP).add(frame[0].subtract(spot).scale(PULL));
        spot = spot.add(spotVel);
        focusVel = focusVel.scale(AIM_KEEP).add(frame[1].subtract(focus).scale(AIM_PULL));
        focus = focus.add(focusVel);
        // Third person, so your own body (or your runner) is drawn in the shot.
        if (before == null) before = mc.options.getCameraType();
        if (mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK) mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
    }

    private static void release(Minecraft mc) {
        spot = spotOld = focus = focusOld = null;
        spotVel = focusVel = Vec3.ZERO;
        homing = -1;
        if (before != null) { mc.options.setCameraType(before); before = null; }
    }
}
