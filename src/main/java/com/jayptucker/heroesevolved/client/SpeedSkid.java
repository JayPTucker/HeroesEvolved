package com.jayptucker.heroesevolved.client;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.network.ClientArmPoseState;
import com.jayptucker.heroesevolved.network.SpeedSkidPayload;
import com.jayptucker.heroesevolved.speed.SpeedMovement;
import com.jayptucker.heroesevolved.speed.SpeedService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A speedster who lets go of Forward at speed skids to a stop instead of halting dead: vanilla friction would end a
 * 60-blocks-a-second run in two ticks. The slide is kept here, on the runner's own client (the client owns its
 * movement, as {@link SpeedLedge} does), easing off as a cube so most of it goes at once and the rest is a short slide:
 * about half a block from a sprint's double, four flat out. The server is told so everyone sees the
 * hockey stop ({@code SPEED_SKID_STOP}), the dust and the skid marks.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class SpeedSkid {
    /** How much of the run's speed the slide starts with, and the most it starts with (blocks a tick). */
    public static final double KEEP = .45, MAX_PUSH = 1.5;

    /** A runner at full pace barely touches the ground (every bump is a little hop), so "running" is remembered for a
     *  few ticks, and a release in mid-stride waits a few ticks to land before the skid starts. */
    private static final int LANDING = 8;

    private static int sinceRun = 1000, waiting = -1;
    private static double runSpeed;
    private static Vec3 heading = Vec3.ZERO, push = Vec3.ZERO;
    private static int age = -1, ticks;

    private SpeedSkid() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) { sinceRun = 1000; waiting = age = -1; return; }
        Vec3 step = new Vec3(player.getX() - player.xo, 0, player.getZ() - player.zo);
        boolean free = !player.isInWater() && !player.getAbilities().flying && !player.isPassenger() && !player.isFallFlying();
        if (age >= 0) slide(player, free);
        if (player.isSprinting() && !player.isShiftKeyDown() && free && SpeedMovement.accelerating(player)) {
            sinceRun = 0;
            runSpeed = step.horizontalDistance();
            if (step.lengthSqr() > 1e-6) heading = step.normalize();
            waiting = -1;
            return;
        }
        sinceRun++;
        if (age >= 0 || player.input.hasForwardImpulse() || !free) { waiting = -1; return; }
        if (waiting < 0 && sinceRun <= 3 && runSpeed >= SpeedService.SKID_SPEED) waiting = 0;
        if (waiting < 0) return;
        if (!player.onGround()) { if (++waiting > LANDING) waiting = -1; return; }
        waiting = -1;
        push = heading.scale(Math.min(MAX_PUSH, runSpeed * KEEP));
        ticks = 6 + (int) Math.round(Math.min(4, runSpeed * 1.5));
        age = 0;
        PacketDistributor.sendToServer(SpeedSkidPayload.INSTANCE);
        slide(player, true);
    }

    /** Sets next tick's velocity along the slide, or ends it if the runner steers out of it. */
    private static void slide(LocalPlayer player, boolean free) {
        ArmPose pose = ClientArmPoseState.get(player.getId());
        boolean dodging = pose == ArmPose.SPEED_SKID_BACK || pose == ArmPose.SPEED_SKID_LEFT || pose == ArmPose.SPEED_SKID_RIGHT;
        if (age >= ticks || !free || player.input.hasForwardImpulse() || player.horizontalCollision || dodging) {
            age = -1;
            return;
        }
        double ease = Math.pow(1 - age / (double) ticks, 3);
        player.setDeltaMovement(push.x * ease, player.getDeltaMovement().y, push.z * ease);
        age++;
    }
}
