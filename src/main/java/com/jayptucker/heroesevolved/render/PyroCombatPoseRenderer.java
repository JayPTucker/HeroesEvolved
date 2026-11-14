package com.jayptucker.heroesevolved.render;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.flight.FlightVisualGeometry;
import com.jayptucker.heroesevolved.network.ClientArmPoseState;
import com.jayptucker.heroesevolved.pyro.PyroGeometry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.HashSet;
import java.util.Set;

/**
 * Firebending turns the whole body, not just the limbs: the roundhouse twists the hips round with the kick, the sweep
 * kick spins once on the planted foot, a jet step leans away from its jet, and the flip kick turns a full backflip.
 * Only the model turns; the camera never does.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class PyroCombatPoseRenderer {
    private static final Set<Integer> POSED = new HashSet<>();

    private PyroCombatPoseRenderer() {}

    @SubscribeEvent
    public static void pre(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        ArmPose pose = ClientArmPoseState.get(player.getId());
        if (pose == null || !player.isAlive()) return;
        float age = ClientArmPoseState.ageTicks(player.getId());
        float body = Mth.rotLerp(event.getPartialTick(), player.yBodyRotO, player.yBodyRot);
        float handed = player.getMainArm() == HumanoidArm.LEFT ? -1 : 1;
        float twist = 0, pitch = 0, roll = 0, drop = 0;
        switch (pose) {
            case FIRE_JAB, FIRE_CROSS -> {
                // The shoulders turn into the punch.
                float punch = smooth(age / 1.5F) * (1 - smooth((age - 4) / 4F));
                twist = (pose == ArmPose.FIRE_CROSS ? 14 : -10) * handed * punch;
            }
            case FIRE_KICK -> {
                float swing = Mth.sin(Mth.PI * Mth.clamp(age / 10F, 0, 1));
                twist = 48 * handed * swing;
                pitch = -10 * swing;
            }
            case FIRE_SWEEP_KICK -> {
                float spin = Mth.clamp(age / PyroGeometry.SWEEP_SPIN, 0, 1);
                twist = 360 * handed * (1 - (1 - spin) * (1 - spin));
                drop = .18F * smooth(age / 2F) * (1 - smooth((age - 10) / 4F));
            }
            case FIRE_STEP -> {
                Vec3 travel = FlightVisualGeometry.travel(player);
                double yaw = Math.toRadians(body);
                double sideways = travel.x * -Math.cos(yaw) + travel.z * -Math.sin(yaw);
                float lean = 1 - smooth((age - 3) / 6F);
                roll = (float) Math.clamp(sideways * 18, -16, 16) * lean;
            }
            case FIRE_FLIP -> pitch = -360 * smooth(age / PyroGeometry.FLIP_SPIN);
            default -> { return; }
        }
        if (twist == 0 && pitch == 0 && roll == 0 && drop == 0) return;
        PoseStack stack = event.getPoseStack();
        stack.pushPose();
        stack.translate(0, .9 - drop, 0);
        stack.mulPose(Axis.YP.rotationDegrees(-body));
        stack.mulPose(Axis.YP.rotationDegrees(twist));
        stack.mulPose(Axis.ZP.rotationDegrees(roll));
        stack.mulPose(Axis.XP.rotationDegrees(pitch));
        stack.mulPose(Axis.YP.rotationDegrees(body));
        stack.translate(0, -.9, 0);
        POSED.add(player.getId());
    }

    @SubscribeEvent
    public static void post(RenderPlayerEvent.Post event) {
        if (POSED.remove(event.getEntity().getId())) event.getPoseStack().popPose();
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) POSED.clear();
    }

    private static float smooth(float value) {
        float t = Mth.clamp(value, 0, 1);
        return t * t * (3 - 2 * t);
    }
}
