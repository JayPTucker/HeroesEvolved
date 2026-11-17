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

    private static boolean running;
    private static double runSpeed;
    private static Vec3 push = Vec3.ZERO;
    private static int age = -1, ticks;

    private SpeedSkid() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) { running = false; age = -1; return; }
        Vec3 step = new Vec3(player.getX() - player.xo, 0, player.getZ() - player.zo);
        boolean ground = player.onGround() && !player.isInWater() && !player.getAbilities().flying;
        if (age >= 0) slide(player, ground);
        boolean now = player.isSprinting() && !player.isShiftKeyDown() && ground && SpeedMovement.accelerating(player);
        if (now) runSpeed = step.horizontalDistance();
        else if (running && age < 0 && ground && !player.input.hasForwardImpulse()) {
            double speed = Math.max(runSpeed, step.horizontalDistance());
            Vec3 heading = step.lengthSqr() > 1e-6 ? step.normalize() : player.getLookAngle().multiply(1, 0, 1).normalize();
            if (speed >= SpeedService.SKID_SPEED && Double.isFinite(heading.x)) {
                push = heading.scale(Math.min(MAX_PUSH, speed * KEEP));
                ticks = 6 + (int) Math.round(Math.min(4, speed * 1.5));
                age = 0;
                PacketDistributor.sendToServer(SpeedSkidPayload.INSTANCE);
                slide(player, ground);
            }
        }
        running = now;
    }

    /** Sets next tick's velocity along the slide, or ends it if the runner steers out of it. */
    private static void slide(LocalPlayer player, boolean ground) {
        ArmPose pose = ClientArmPoseState.get(player.getId());
        boolean dodging = pose == ArmPose.SPEED_SKID_BACK || pose == ArmPose.SPEED_SKID_LEFT || pose == ArmPose.SPEED_SKID_RIGHT;
        if (age >= ticks || !ground || player.input.hasForwardImpulse() || player.horizontalCollision || dodging) {
            age = -1;
            return;
        }
        double ease = Math.pow(1 - age / (double) ticks, 3);
        player.setDeltaMovement(push.x * ease, player.getDeltaMovement().y, push.z * ease);
        age++;
    }
}
