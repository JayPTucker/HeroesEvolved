package com.jayptucker.heroesevolved.client.effects.intro;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.client.DazeView;
import com.jayptucker.heroesevolved.client.sound.SynthSound;
import com.jayptucker.heroesevolved.client.sound.WaveRumble;
import com.jayptucker.heroesevolved.network.ClientCameraShakeState;
import com.jayptucker.heroesevolved.story.prologue.PrologueTimeline;
import com.jayptucker.heroesevolved.time.intro.IntroBlast;
import com.jayptucker.heroesevolved.time.intro.city.CityGrid;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/**
 * The Last Day's big wave from where this player stands, before it reaches them. They hear it coming: a rumble that
 * builds for four seconds ({@link WaveRumble}) while the ground shakes harder. For the last second and a half their head
 * is turned to the light (the mouse can't turn it away) and held there until the wave hits; then the server's daze takes
 * over ({@link DazeView}), from facing it. When it reaches them is the shared script's ({@link PrologueTimeline#hits}),
 * from where they stand, as the server's own knock is. Nothing of this happens below ground.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class WaveApproach {
    /** How long before it hits the rumble starts, the head is turned, and the turn takes. */
    public static final int RUMBLE = 80, LOOK = 30, TURN = 14;
    private static long lightFor = Long.MIN_VALUE;
    private static boolean rumbled;
    private static SynthSound rumble;
    /** When the turn began (ticks since the light, -1 before), the view it began from, and when the wave hits. */
    private static float turnFrom = -1, fromYaw, fromPitch;
    private static int hitAt;

    private WaveApproach() {}

    static void reset() {
        if (rumble != null) rumble.fadeOut(4);
        rumble = null;
        rumbled = false;
        turnFrom = -1;
        lightFor = Long.MIN_VALUE;
    }

    /** Every client tick of the Last Day while the light is up. */
    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (!LastDayArt.live() || p == null) return;
        if (lightFor != IntroScene.light) { reset(); lightFor = IntroScene.light; }
        float t = IntroScene.sinceLight(0);
        boolean open = !IntroScene.underground && p.getY() > CityGrid.STREET_Y - 4;
        int hit = PrologueTimeline.hits(p.getX(), p.getZ());
        // It has hit (the daze began), or it's long past, or they went below: the rumble gives way.
        if (rumble != null && (DazeView.active() || t > hit + 12 || !open)) { rumble.fadeOut(6); rumble = null; }
        if (!open || t > hit + 12) return;
        hitAt = hit;
        if (!rumbled && t >= hit - RUMBLE && t < hit - 4) {
            rumbled = true;
            rumble = WaveRumble.play((hit - t) / 20F);
        }
        // The ground shakes harder as it comes.
        if (rumbled && t < hit && ((int) t & 7) == 0) {
            float k = Mth.clamp(1 - (hit - t) / RUMBLE, 0, 1);
            ClientCameraShakeState.start(0.03F + 0.3F * k * k, 12);
        }
        if (turnFrom < 0 && t >= hit - LOOK && t < hit) {
            turnFrom = t;
            fromYaw = p.getYRot();
            fromPitch = p.getXRot();
        }
    }

    /** Whether this player's head is being held toward the wave now. */
    private static boolean turning(float t) {
        return turnFrom >= 0 && t >= turnFrom && t < hitAt + 3 && !DazeView.active() && LastDayArt.live();
    }

    /** Turns the head to the light every frame (after the mouse has had its turn), easing in, then holding. */
    @SubscribeEvent
    public static void frame(RenderFrameEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || turnFrom < 0) return;
        float t = IntroScene.sinceLight(event.getPartialTick().getGameTimeDeltaPartialTick(true));
        if (!turning(t)) return;
        Vec3 to = IntroBlast.CENTER.add(0, 5, 0).subtract(p.getEyePosition());
        float yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(to.y, to.horizontalDistance()));
        float k = Mth.clamp((t - turnFrom) / TURN, 0, 1);
        k = k * k * (3 - 2 * k);
        float y = Mth.rotLerp(k, fromYaw, yaw), x = Mth.lerp(k, fromPitch, pitch);
        p.setYRot(y);
        p.setXRot(x);
        p.yRotO = y;
        p.xRotO = x;
        p.setYHeadRot(y);
    }

    /** The mouse can't turn the head away while it's held. */
    @SubscribeEvent
    public static void mouse(CalculatePlayerTurnEvent event) {
        if (turnFrom >= 0 && turning(IntroScene.sinceLight(0))) event.setMouseSensitivity(-1 / 3.0);
    }
}
