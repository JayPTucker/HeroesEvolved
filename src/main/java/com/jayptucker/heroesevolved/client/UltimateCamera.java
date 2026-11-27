package com.jayptucker.heroesevolved.client;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.client.effects.TempestCamera;
import com.jayptucker.heroesevolved.config.ClientVisualSettings;
import com.jayptucker.heroesevolved.network.UltimateCameraPayload;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

import java.util.List;

/**
 * The camera of your own Ultimate: the server sends a few keys (an eye and a point to look at, at a tick), and the view
 * eases from one to the next while letterbox bars close in from the top and bottom, then hands back when the path ends.
 * Your feet stay put while it plays (the Ultimate moves your body itself). A key that follows a body looks at it
 * wherever it goes; one whose eye follows keeps its offset from it. The eye is kept in front of walls. Off in the
 * client settings ({@code ultimateCamera}), nothing here happens.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class UltimateCamera {
    private static List<UltimateCameraPayload.Key> keys = List.of();
    private static long start = Long.MIN_VALUE, endsAt = Long.MIN_VALUE;
    private static int ticks;
    private static CameraType before;
    /** The last eye shown, so a key that can't be placed (a body gone) holds still instead of jumping. */
    private static Vec3 lastEye, lastLook;

    private UltimateCamera() {}

    public static void play(UltimateCameraPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || payload.player() != mc.player.getId()) return;
        if (payload.ticks() <= 0 || payload.keys().isEmpty() || !ClientVisualSettings.ULTIMATE_CAMERA.get()) {
            release(mc);
            return;
        }
        keys = payload.keys();
        ticks = payload.ticks();
        start = mc.level.getGameTime();
        endsAt = start + ticks;
        lastEye = lastLook = null;
        if (before == null) before = mc.options.getCameraType();
        if (mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK) mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
    }

    /** Whether your Ultimate's camera is playing now. */
    public static boolean playing() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && !keys.isEmpty() && mc.level.getGameTime() <= endsAt + 1;
    }

    /** The view this frame, or null (asked by {@link TempestCamera#shot}). */
    public static TempestCamera.Shot shot(float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (!playing() || mc.level == null) return null;
        float t = (float) (mc.level.getGameTime() - start) + partial;
        UltimateCameraPayload.Key a = keys.getFirst(), b = a;
        for (UltimateCameraPayload.Key k : keys) {
            if (k.tick() <= t) a = k;
            if (k.tick() >= t) { b = k; break; }
            b = k;
        }
        float span = Math.max(1, b.tick() - a.tick());
        float u = b == a ? 0 : Mth.clamp((t - a.tick()) / span, 0, 1);
        u = u * u * (3 - 2 * u);
        Vec3[] ea = place(mc, a, partial), eb = place(mc, b, partial);
        if (ea == null || eb == null) {
            if (lastEye == null) return null;
            return toShot(lastEye, lastLook);
        }
        Vec3 eye = ea[0].lerp(eb[0], u), look = ea[1].lerp(eb[1], u);
        // In front of walls, seen from what it looks at.
        var hit = mc.level.clip(new ClipContext(look, eye, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        if (hit.getType() == HitResult.Type.BLOCK) eye = hit.getLocation().add(look.subtract(hit.getLocation()).normalize().scale(.3));
        lastEye = eye;
        lastLook = look;
        return toShot(eye, look);
    }

    private static Vec3[] place(Minecraft mc, UltimateCameraPayload.Key k, float partial) {
        if (k.follow() < 0) return new Vec3[]{k.eye(), k.look()};
        Entity body = mc.level.getEntity(k.follow());
        if (body == null) return null;
        Vec3 centre = body.getPosition(partial).add(0, body.getBbHeight() * .55, 0);
        Vec3 eye = k.eyeFollows() ? centre.add(k.eye()) : k.eye();
        return new Vec3[]{eye, centre};
    }

    private static TempestCamera.Shot toShot(Vec3 eye, Vec3 look) {
        Vec3 d = look.subtract(eye);
        return new TempestCamera.Shot(eye, (float) (Mth.atan2(-d.x, d.z) * Mth.RAD_TO_DEG),
                (float) (-Mth.atan2(d.y, d.horizontalDistance()) * Mth.RAD_TO_DEG));
    }

    /** How far the letterbox bars have closed: 0 open, 1 shut, easing in and out over six ticks. */
    public static float bars(float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (!playing() || mc.level == null) return 0;
        float t = (float) (mc.level.getGameTime() - start) + partial;
        float in = Mth.clamp(t / 6, 0, 1), out = Mth.clamp((ticks - t) / 6, 0, 1);
        float k = Math.min(in, out);
        return k * k * (3 - 2 * k);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) { release(mc); return; }
        if (!keys.isEmpty() && mc.level.getGameTime() > endsAt + 1) release(mc);
    }

    /** Your feet stay put while the camera plays: the Ultimate moves your body itself. */
    @SubscribeEvent
    public static void hold(MovementInputUpdateEvent event) {
        if (!playing()) return;
        var input = event.getInput();
        input.forwardImpulse = 0;
        input.leftImpulse = 0;
        input.jumping = false;
        input.shiftKeyDown = false;
    }

    private static void release(Minecraft mc) {
        keys = List.of();
        lastEye = lastLook = null;
        if (before != null) { mc.options.setCameraType(before); before = null; }
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { release(Minecraft.getInstance()); }

    /** The letterbox: two black bars, a tenth of the screen each when shut. */
    @EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Bars {
        @SubscribeEvent public static void register(RegisterGuiLayersEvent event) {
            event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "ultimate_letterbox"), (gui, delta) -> {
                float k = bars(delta.getGameTimeDeltaPartialTick(true));
                if (k <= 0) return;
                int h = Math.round(gui.guiHeight() * .1F * k);
                gui.fill(0, 0, gui.guiWidth(), h, 0xFF000000);
                gui.fill(0, gui.guiHeight() - h, gui.guiWidth(), gui.guiHeight(), 0xFF000000);
            });
        }
    }
}
