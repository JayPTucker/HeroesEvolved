package com.jayptucker.heroesevolved.client;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.sounds.ModSounds;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Knocked flat by a blast, seen from inside: blackout, then uncertain blinks from the ground, the head wandering before it
 * can focus, and back up on your feet as the view rises to where you're looking. The Last Day's wave starts with a longer
 * blackout ({@code blackout} ticks, about three seconds): out cold, with two failed tries at opening the eyes, the screams
 * all around heard slowed and smeared ({@code PanicSounds.dazed}). Only this player's view is directed;
 * the server holds the body down. Used by the radiation awakening (after its own blast) and the Last Day's wave, which
 * adds the ringing: a high tone in the ears that fades over several seconds, with everything else muffled under it
 * ({@code mixin/SoundEngineDuckMixin}), after one great crash of glass.
 * <p>
 * The timings are the radiation awakening's (its blinks were tuned for a 140-tick recovery) and stretch with
 * {@code length}.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class DazeView {
    private static ClientLevel world;
    private static LocalPlayer owner;
    private static CameraType before;
    private static Pose beforePose;
    private static long began;
    private static float yaw;
    /** The recovery's length, the extra time held after it, how long of it is out cold, and whether the ears ring. */
    private static int length = 140, grace, blackout;
    /** The screaming heard while out cold. */
    private static com.jayptucker.heroesevolved.client.sound.SynthSound dazed;
    private static boolean ringing;
    /** How muffled the world was last tick, so volumes are re-applied only while it changes. */
    private static float lastDuck = 1;

    private DazeView() {}

    /** On your back for {@code length} ticks (and held {@code grace} more), with ringing ears if {@code ringing}. */
    public static void begin(int length, int grace, boolean ringing) { begin(length, grace, ringing, 0); }

    /** As {@link #begin(int, int, boolean)}, out cold for the first {@code blackout} ticks. */
    public static void begin(int length, int grace, boolean ringing, int blackout) {
        clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        world = mc.level;
        owner = mc.player;
        began = world.getGameTime();
        yaw = owner.getYRot();
        DazeView.length = Math.max(40, length);
        DazeView.grace = Math.max(0, grace);
        DazeView.ringing = ringing;
        DazeView.blackout = Math.clamp(blackout, 0, Math.max(0, DazeView.length - 40));
        before = mc.options.getCameraType();
        beforePose = owner.getForcedPose();
        owner.setForcedPose(Pose.SLEEPING);
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        if (ringing) {
            // The crash first, over everything; then the ringing it leaves behind.
            mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.LASTDAY_SHATTER.get(), 1, 1));
            mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.LASTDAY_RINGING.get(), 1, 0.85F));
        }
        // Out cold: the screaming goes on, slowed and smeared, until they come to.
        if (DazeView.blackout > 0) {
            dazed = com.jayptucker.heroesevolved.client.sound.PanicSounds.dazed(DazeView.blackout);
            if (dazed != null) mc.getSoundManager().play(dazed);
        }
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
        if (dazed != null) dazed.fadeOut(10);
        dazed = null;
        blackout = 0;
        owner = null;
        world = null;
        ringing = false;
    }

    /**
     * Ticks since the blast, scaled to the radiation awakening's 140-tick timings: held at 0 while out cold, then the
     * awakening's blinks over what is left.
     */
    private static float age(float partial) {
        float since = world.getGameTime() - began + partial;
        if (blackout <= 0) return since * 140F / length;
        return Math.max(0, since - blackout) * 140F / Math.max(40, length - blackout);
    }

    /** Ticks since the blast, unscaled. */
    private static float since(float partial) { return world.getGameTime() - began + partial; }

    /**
     * How loud the world is through the ringing, 1 when it isn't: muffled almost to nothing while you're down, coming
     * back as you get up, clear a few seconds after.
     */
    public static float duck() {
        if (!ringing || world == null || Minecraft.getInstance().level != world) return 1;
        float since = world.getGameTime() - began;
        float back = smooth(length * 0.55F, length + 90, since);
        return Mth.lerp(back, 0.12F, 1);
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (owner == null) {
            restoreVolumes();
            return;
        }
        if (!active() || world.getGameTime() - began > length + grace) {
            // Up again; the ringing goes on fading after.
            boolean ears = ringing && active() && world.getGameTime() - began < length + 90;
            ClientLevel stillHere = world;
            long when = began;
            clear();
            if (ears) { ringing = true; world = stillHere; began = when; }
            return;
        }
        owner.setForcedPose(Pose.SLEEPING);
        Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON);
        restoreVolumes();
    }

    /** Re-applies every playing sound's volume while the muffling changes (and once more when it's gone). */
    private static void restoreVolumes() {
        float duck = duck();
        if (Math.abs(duck - lastDuck) < 0.01F) return;
        lastDuck = duck;
        Minecraft mc = Minecraft.getInstance();
        // Re-applies the volume of every playing sound (the category passed only matters for the master channel).
        mc.getSoundManager().updateSourceVolume(SoundSource.AMBIENT, mc.options.getSoundSourceVolume(SoundSource.AMBIENT));
        if (duck >= 1) { ringing = ringing && owner != null; }
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); lastDuck = 1; }

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
        float age = age((float) event.getPartialTick());
        float rise = smooth(110, 140, age);
        float sway = (1 - rise) * VisualComfort.shakeScale();
        // On their back, looking up; the head wanders before they can focus.
        event.setPitch(Mth.lerp(rise, -68 + 7 * smooth(24, 100, age), owner.getXRot()) + Mth.sin(age * .07F) * 2 * sway);
        event.setYaw(Mth.rotLerp(rise, yaw, owner.getYRot()) + Mth.sin(age * .045F) * 4 * sway);
        event.setRoll((10 + Mth.sin(age * .055F) * 3) * sway);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void eyes(RenderGuiEvent.Post event) {
        if (!active()) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        float age = age(partial);
        float open = .82F * smooth(12, 34, age) + .18F * smooth(82, 114, age);
        open *= 1 - blink(age, 38, 45, 58);
        open *= 1 - .9F * blink(age, 72, 78, 88);
        float since = since(partial);
        if (since < blackout) {
            // Out cold, trying to come to: twice the eyes almost open, a slit of blurred light, and fall shut again.
            float b = blackout;
            open = .06F * blink(since, b * .45F, b * .52F, b * .62F) + .13F * blink(since, b * .74F, b * .82F, b * .94F);
        }
        var g = event.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight();
        // Curved upper and lower eyelids, with a soft edge; fully shut at first.
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
