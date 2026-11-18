package com.jayptucker.heroesevolved.client;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.intro.PowerIntroService;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Blackout, then uncertain blinks from the crater floor. Only the owner's view is directed. */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class RadiationIntroClient {
    private static ClientLevel world;
    private static LocalPlayer owner;
    private static CameraType before;
    private static Pose beforePose;
    private static long began;
    private static float yaw;

    private RadiationIntroClient() {}

    public static void begin() {
        clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        world = mc.level;
        owner = mc.player;
        began = world.getGameTime();
        yaw = owner.getYRot();
        before = mc.options.getCameraType();
        beforePose = owner.getForcedPose();
        owner.setForcedPose(Pose.SLEEPING);
        mc.options.setCameraType(CameraType.FIRST_PERSON);
    }

    public static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        return owner != null && mc.player == owner && mc.level == world && owner.isAlive();
    }

    public static void clear() {
        if (owner != null && owner.getForcedPose() == Pose.SLEEPING) owner.setForcedPose(beforePose);
        if (before != null) Minecraft.getInstance().options.setCameraType(before);
        before = null;
        beforePose = null;
        owner = null;
        world = null;
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (owner == null) return;
        if (!active() || world.getGameTime() - began > PowerIntroService.RADIATION_RECOVERY_TICKS + 40) {
            clear();
            return;
        }
        owner.setForcedPose(Pose.SLEEPING);
        Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON);
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }

    @SubscribeEvent public static void movement(MovementInputUpdateEvent event) {
        if (!active()) return;
        event.getInput().forwardImpulse = 0;
        event.getInput().leftImpulse = 0;
        event.getInput().jumping = false;
        event.getInput().shiftKeyDown = false;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void hands(RenderHandEvent event) { if (active()) event.setCanceled(true); }

    @SubscribeEvent public static void hud(RenderGuiLayerEvent.Pre event) { if (active()) event.setCanceled(true); }

    @SubscribeEvent public static void interact(InputEvent.InteractionKeyMappingTriggered event) {
        if (!active()) return;
        event.setCanceled(true);
        event.setSwingHand(false);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void camera(ViewportEvent.ComputeCameraAngles event) {
        if (!active()) return;
        float age = world.getGameTime() - began + (float) event.getPartialTick();
        float rise = smooth(110, PowerIntroService.RADIATION_RECOVERY_TICKS, age);
        float sway = (1 - rise) * VisualComfort.shakeScale();
        // On their back, looking up past the rim; the head wanders before they can focus.
        event.setPitch(Mth.lerp(rise, -68 + 7 * smooth(24, 100, age), owner.getXRot()) + Mth.sin(age * .07F) * 2 * sway);
        event.setYaw(Mth.rotLerp(rise, yaw, owner.getYRot()) + Mth.sin(age * .045F) * 4 * sway);
        event.setRoll((10 + Mth.sin(age * .055F) * 3) * sway);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void eyes(RenderGuiEvent.Post event) {
        if (!active()) return;
        float age = world.getGameTime() - began + event.getPartialTick().getGameTimeDeltaPartialTick(true);
        float open = .82F * smooth(12, 34, age) + .18F * smooth(82, 114, age);
        open *= 1 - blink(age, 38, 45, 58);
        open *= 1 - .9F * blink(age, 72, 78, 88);
        var g = event.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight();
        // Curved upper and lower eyelids, with a soft edge; fully shut during the teleport.
        for (int x = 0; x < w; x += 4) {
            float side = (x + 2F - w * .5F) / (w * .5F);
            int lid = Math.round(h * .5F * (1 - open) + h * .13F * side * side * open * (1 - smooth(100, 130, age)));
            int right = Math.min(w, x + 4);
            g.fill(x, 0, right, lid, 0xFF000000);
            g.fill(x, h - lid, right, h, 0xFF000000);
            int feather = Math.round(5 * (1 - smooth(115, 138, age)));
            if (feather > 0) {
                g.fillGradient(x, lid, right, lid + feather, 0xD0000000, 0x00000000);
                g.fillGradient(x, h - lid - feather, right, h - lid, 0x00000000, 0xD0000000);
            }
        }
    }

    private static float blink(float age, float close, float shut, float reopen) {
        return smooth(close, shut, age) * (1 - smooth(shut, reopen, age));
    }

    private static float smooth(float from, float to, float value) {
        float t = Mth.clamp((value - from) / (to - from), 0, 1);
        return t * t * (3 - 2 * t);
    }
}
