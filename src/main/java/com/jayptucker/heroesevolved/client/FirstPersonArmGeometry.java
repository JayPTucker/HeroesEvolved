package com.jayptucker.heroesevolved.client;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.world.entity.HumanoidArm;

/** Camera-space shoulder anchors. Each arm remains on its own side of the crosshair. */
public final class FirstPersonArmGeometry {
    private FirstPersonArmGeometry() {}

    public static void pose(PoseStack stack, HumanoidArm arm, ArmPose pose, float time) {
        pose(stack, arm, pose, time, time);
    }

    public static void pose(PoseStack stack, HumanoidArm arm, ArmPose pose, float time, float poseAge) {
        float side = arm == HumanoidArm.RIGHT ? 1 : -1;
        if (pose == ArmPose.KINETIC_GATHER || pose == ArmPose.KINETIC_HOLD || pose == ArmPose.KINETIC_RELEASE) {
            kinetic(stack, side, pose, time, poseAge);
            return;
        }
        float shake = pose == ArmPose.TWO_HANDS_STRAIN ? (float) Math.sin(time * 2.7) * .008F : 0;
        float spread = pose == ArmPose.ARMS_WIDE ? .65F : .42F;
        float height = switch (pose) {
            case ARMS_RAISED, TEMPLE -> -.1F;
            case GROUND -> -.55F;
            default -> -.38F;
        };
        stack.translate(side * spread + shake, height + shake, -.22F);
        stack.mulPose(Axis.YP.rotationDegrees(side * (pose == ArmPose.ARMS_WIDE ? -30 : 4)));
        // Model arms extend along +Y; -90 degrees points them along camera -Z.
        // Smaller angles raise the hands, larger negative angles point downward.
        float pitch = switch (pose) {
            case GROUND -> -130;
            case ARMS_RAISED -> -35;
            case TEMPLE -> -20;
            default -> -90;
        };
        stack.mulPose(Axis.XP.rotationDegrees(pitch));
    }

    private static void kinetic(PoseStack stack, float side, ArmPose pose, float time, float age) {
        float pull = .5F + .5F * (float) Math.sin(age * .38F + (side > 0 ? 0 : Math.PI));
        float thrust = (float) Math.sin(Math.clamp(age / 9F, 0, 1) * Math.PI);
        float tension = (float) Math.sin(time * 2.3F) * .004F;
        float spread = pose == ArmPose.KINETIC_GATHER ? .48F + pull * .035F : .48F;
        float depth = switch (pose) {
            case KINETIC_GATHER -> -.25F - pull * .08F;
            case KINETIC_RELEASE -> -.24F - thrust * .18F;
            default -> -.25F;
        };
        float pitch = switch (pose) {
            case KINETIC_GATHER -> -70 - pull * 18;
            case KINETIC_RELEASE -> -72 - thrust * 18;
            default -> -82;
        };
        stack.translate(side * spread, -.42F + tension, depth);
        stack.mulPose(Axis.YP.rotationDegrees(side * (pose == ArmPose.KINETIC_GATHER ? 6 + pull * 8 : 8)));
        stack.mulPose(Axis.XP.rotationDegrees(pitch));
        stack.mulPose(Axis.ZP.rotationDegrees(side * (pose == ArmPose.KINETIC_GATHER ? 8 : 4)));
    }
}
