package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.network.SpeedCyclonePayload;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * While your own tornado stands (a Flight Tempest you circle, or a Superspeed Cyclone you have raised and are
 * still running), the view steps well back from the whole thing and holds still: far enough out to see the
 * funnel, what it has caught and you whipping round it. It stands behind the tornado along the way you are
 * looking (so a Tempest still steers the way the screen faces), glides after the tornado as it is steered, and
 * turns only when you turn. The camera is moved by {@code TornadoCameraMixin}; your player stays the camera's
 * entity in third person, so it keeps moving and is drawn as usual.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class TempestCamera {
    /** How far back from the funnel the view stands, how high, and the height on the funnel it looks at. */
    private static final double BACK = 26, UP = 12, LOOK_AT = 9;

    /** Where the view is and which way it looks. */
    public record Shot(Vec3 at, float yaw, float pitch) {}

    private static Vec3 spot, spotOld;
    private static Vec3 focus, focusOld;
    private static CameraType before;

    private TempestCamera() {}

    /** Your own tornado, if one is standing and you are with it. */
    private static SpeedCyclone.View mine(Minecraft mc) {
        if (mc.player == null) return null;
        for (SpeedCyclone.View v : SpeedCyclone.views()) {
            if (v.entity != mc.player.getId() || v.ended()) continue;
            if (v.style == SpeedCyclonePayload.FLIGHT) return v;
            // A speedster's: once it has risen, for as long as they are running it.
            if (v.style == SpeedCyclonePayload.SPEED && v.rose() && !v.free) return v;
        }
        return null;
    }

    /** The view for this frame, or null when there is no tornado to watch. */
    public static Shot shot(float partial) {
        if (spot == null || spotOld == null) return null;
        Vec3 at = spotOld.lerp(spot, partial), look = focusOld.lerp(focus, partial).subtract(at);
        float yaw = (float) (Mth.atan2(-look.x, look.z) * Mth.RAD_TO_DEG);
        float pitch = (float) (-Mth.atan2(look.y, look.horizontalDistance()) * Mth.RAD_TO_DEG);
        return new Shot(at, yaw, pitch);
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        SpeedCyclone.View tornado = mc.level == null ? null : mine(mc);
        if (tornado == null || RewindTravelClient.travelling()) { release(mc); return; }
        float yaw = mc.player.getYRot() * Mth.DEG_TO_RAD;
        Vec3 look = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 goal = tornado.center.subtract(look.scale(BACK)).add(0, UP, 0), aim = tornado.center.add(0, LOOK_AT, 0);
        if (spot == null) { spot = goal; spotOld = goal; focus = aim; focusOld = aim; }
        spotOld = spot; focusOld = focus;
        spot = spot.lerp(goal, 0.2);
        focus = focus.lerp(aim, 0.3);
        // Third person, so your own body is drawn in the shot.
        if (before == null) before = mc.options.getCameraType();
        if (mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK) mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
    }

    private static void release(Minecraft mc) {
        spot = spotOld = focus = focusOld = null;
        if (before != null) { mc.options.setCameraType(before); before = null; }
    }
}
