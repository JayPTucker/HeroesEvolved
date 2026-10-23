package com.jayptucker.heroesevolved.client;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.entity.CarryAnchorEntity;
import com.jayptucker.heroesevolved.network.ClientArmPoseState;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;

/**
 * Applies power arm poses to the player model. Called from a mixin at the
 * end of {@code PlayerModel#setupAnim}, because vanilla recalculates every
 * limb angle there; poses set any earlier (e.g. in a render event) are lost.
 */
public final class PowerArmPoses {
    private PowerArmPoses() {
    }

    public static void apply(PlayerModel<?> model, LivingEntity entity, float ageInTicks) {
        ArmPose pose = entity.getPassengers().stream()
                .anyMatch(CarryAnchorEntity.class::isInstance)
                ? ArmPose.TWO_HANDS
                : ClientArmPoseState.get(entity.getId());
        if (pose == null) {
            return;
        }

        float aimX = -Mth.HALF_PI + model.head.xRot;
        float aimY = model.head.yRot;
        boolean rightHanded = entity.getMainArm() == HumanoidArm.RIGHT;
        ModelPart main = rightHanded ? model.rightArm : model.leftArm;
        // Inward rotation is negative for the right arm and positive for the left.
        float inward = rightHanded ? -1.0F : 1.0F;

        switch (pose) {
            case ONE_HAND -> set(main, aimX, aimY + inward * 0.1F, 0.0F);
            case TWO_HANDS, TWO_HANDS_STRAIN -> {
                float tremble = pose == ArmPose.TWO_HANDS_STRAIN
                        ? Mth.sin(ageInTicks * 2.7F) * 0.05F : 0.0F;
                set(model.rightArm, aimX + tremble, aimY - 0.15F, tremble);
                set(model.leftArm, aimX - tremble, aimY + 0.15F, -tremble);
            }
            case ARMS_RAISED -> {
                set(model.rightArm, -2.9F, 0.0F, 0.25F);
                set(model.leftArm, -2.9F, 0.0F, -0.25F);
            }
            case ARMS_WIDE -> {
                set(model.rightArm, -0.2F, 0.0F, 1.35F);
                set(model.leftArm, -0.2F, 0.0F, -1.35F);
            }
            case TEMPLE -> set(main, -2.35F, inward * 0.1F, inward * 0.55F);
            case GROUND -> {
                set(model.rightArm, -0.55F, -0.15F, 0.1F);
                set(model.leftArm, -0.55F, 0.15F, -0.1F);
            }
        }

        // setupAnim copies arms onto sleeves before we run; copy again.
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftSleeve.copyFrom(model.leftArm);
    }

    private static void set(ModelPart arm, float x, float y, float z) {
        arm.xRot = x;
        arm.yRot = y;
        arm.zRot = z;
    }
}
