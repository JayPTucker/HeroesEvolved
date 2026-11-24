package com.jayptucker.heroesevolved.client.effects.intro;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.story.future.FutureWarningGeometry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** Uses WaveApproach's frame-based turn without replacing the camera entity or interrupting movement. */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class FutureLook {
    private static ClientLevel world;
    private static Vec3 at;
    private static long start;
    private static int ticks;
    private static float yaw, pitch;
    private FutureLook() {}
    public static void start(Vec3 target, int duration) {
        var mc = Minecraft.getInstance();
        ticks = Math.clamp(duration, 0, FutureWarningGeometry.TURN);
        if (mc.player == null || mc.level == null || !Double.isFinite(target.lengthSqr())) { ticks = 0; return; }
        world = mc.level; at = target; start = world.getGameTime();
        yaw = mc.player.getYRot(); pitch = mc.player.getXRot();
    }
    public static boolean active() {
        var mc = Minecraft.getInstance();
        return ticks > 0 && mc.level == world && mc.player != null && mc.player.isAlive()
                && world.getGameTime() - start < ticks;
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Pre event) {
        if (!active()) return;
        var p = Minecraft.getInstance().player;
        float age = world.getGameTime() - start + event.getPartialTick().getGameTimeDeltaPartialTick(true);
        Vec3 d = at.subtract(p.getEyePosition());
        float k = FutureWarningGeometry.smooth(age / 24);
        float y = Mth.rotLerp(k, yaw, (float) Math.toDegrees(Math.atan2(-d.x, d.z)));
        float x = Mth.lerp(k, pitch, (float) -Math.toDegrees(Math.atan2(d.y, d.horizontalDistance())));
        p.setYRot(y); p.yRotO = y; p.setYHeadRot(y); p.setXRot(x); p.xRotO = x;
    }
    @SubscribeEvent public static void mouse(CalculatePlayerTurnEvent event) {
        if (active()) event.setMouseSensitivity(-1 / 3.0);
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { ticks = 0; world = null; }
}
