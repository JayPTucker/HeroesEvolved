package com.jayptucker.heroesevolved.render;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.entity.CarryAnchorEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Renders anchor passengers horizontally across their carrier's arms. */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class EntityCarryPoseRenderer {
    private static final Set<Integer> TRANSFORMED_ENTITIES = new HashSet<>();
    private static final Map<Integer, ArmPose> CARRIER_ARM_POSES =
            new HashMap<>();

    private EntityCarryPoseRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        if (!(event.getEntity() instanceof Player)) {
            applyCarryPose(event.getEntity(), event.getPoseStack());
        }
    }

    @SubscribeEvent
    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> event) {
        if (!(event.getEntity() instanceof Player)) {
            clearCarryPose(event.getEntity(), event.getPoseStack());
        }
    }

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        applyCarryPose(event.getEntity(), event.getPoseStack());
        applyCarrierArmPose(event);
    }

    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        clearCarryPose(event.getEntity(), event.getPoseStack());
        clearCarrierArmPose(event);
    }

    private static void applyCarryPose(Entity entity, PoseStack poseStack) {
        if (!(entity.getVehicle() instanceof CarryAnchorEntity anchor)) {
            return;
        }

        // Rotate the normal upright model into a horizontal, arms-held pose.
        // The anchor itself is synchronized in front of the carrier's chest.
        float carrierYaw = anchor.getYRot();
        poseStack.pushPose();
        poseStack.translate(0.0D, 0.9D, 0.0D);
        poseStack.mulPose(Axis.YP.rotationDegrees(-carrierYaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(carrierYaw));
        poseStack.translate(0.0D, -0.9D, 0.0D);
        TRANSFORMED_ENTITIES.add(entity.getId());
    }

    private static void clearCarryPose(Entity entity, PoseStack poseStack) {
        if (TRANSFORMED_ENTITIES.remove(entity.getId())) {
            poseStack.popPose();
        }
    }

    private static void applyCarrierArmPose(RenderPlayerEvent.Pre event) {
        if (event.getEntity().getPassengers().stream().noneMatch(
                CarryAnchorEntity.class::isInstance
        )) {
            return;
        }

        PlayerModel<?> model = event.getRenderer().getModel();
        CARRIER_ARM_POSES.put(event.getEntity().getId(), new ArmPose(
                model.rightArm,
                model.leftArm,
                model.rightSleeve,
                model.leftSleeve
        ));

        // Both arms reach forward and slightly inward around the held model.
        setArmPose(model.rightArm, -1.35F, 0.25F, 0.15F);
        setArmPose(model.leftArm, -1.35F, -0.25F, -0.15F);
        setArmPose(model.rightSleeve, -1.35F, 0.25F, 0.15F);
        setArmPose(model.leftSleeve, -1.35F, -0.25F, -0.15F);
    }

    private static void clearCarrierArmPose(RenderPlayerEvent.Post event) {
        ArmPose pose = CARRIER_ARM_POSES.remove(event.getEntity().getId());
        if (pose != null) {
            pose.restore();
        }
    }

    private static void setArmPose(
            ModelPart arm,
            float xRotation,
            float yRotation,
            float zRotation
    ) {
        arm.xRot = xRotation;
        arm.yRot = yRotation;
        arm.zRot = zRotation;
    }

    private record ArmPose(
            ModelPart rightArm,
            ModelPart leftArm,
            ModelPart rightSleeve,
            ModelPart leftSleeve,
            float rightArmX,
            float rightArmY,
            float rightArmZ,
            float leftArmX,
            float leftArmY,
            float leftArmZ,
            float rightSleeveX,
            float rightSleeveY,
            float rightSleeveZ,
            float leftSleeveX,
            float leftSleeveY,
            float leftSleeveZ
    ) {
        private ArmPose(
                ModelPart rightArm,
                ModelPart leftArm,
                ModelPart rightSleeve,
                ModelPart leftSleeve
        ) {
            this(
                    rightArm, leftArm, rightSleeve, leftSleeve,
                    rightArm.xRot, rightArm.yRot, rightArm.zRot,
                    leftArm.xRot, leftArm.yRot, leftArm.zRot,
                    rightSleeve.xRot, rightSleeve.yRot, rightSleeve.zRot,
                    leftSleeve.xRot, leftSleeve.yRot, leftSleeve.zRot
            );
        }

        private void restore() {
            setArmPose(rightArm, rightArmX, rightArmY, rightArmZ);
            setArmPose(leftArm, leftArmX, leftArmY, leftArmZ);
            setArmPose(rightSleeve, rightSleeveX, rightSleeveY, rightSleeveZ);
            setArmPose(leftSleeve, leftSleeveX, leftSleeveY, leftSleeveZ);
        }
    }
}
