package com.jayptucker.heroesevolved.client;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.world.entity.HumanoidArm;

/** Camera-space shoulder anchors. Each arm remains on its own side of the crosshair. */
public final class FirstPersonArmGeometry {
    private FirstPersonArmGeometry() {}

    public static void pose(PoseStack stack, HumanoidArm arm, ArmPose pose, float time) {
        float side = arm == HumanoidArm.RIGHT ? 1 : -1;
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
}
