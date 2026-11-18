package com.jayptucker.heroesevolved.client;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.network.ClientArmPoseState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

/**
 * Power Amplification's gestures (Ando's red lightning), in third person ({@link #third}, from {@code PowerArmPoses}) and
 * first person ({@link #first}, from {@code FirstPersonArmGeometry}). The conventions are those files': {@code aimX}
 * points an arm along the look; {@code main} casts and {@code off} is the other arm; an arm swings outward with
 * {@code z = -inward * k} (main) or {@code z = inward * k} (off).
 */
public final class AmpPoses {
    private AmpPoses() {}

    public static boolean owns(ArmPose pose) {
        return switch (pose) {
            case AMP_ZAP, AMP_CUP, AMP_THROW, AMP_BEAM, AMP_TEMPLES, AMP_PALM, AMP_RAISE, AMP_SHOW -> true;
            default -> false;
        };
    }

    public static void third(PlayerModel<?> model, ArmPose pose, int id, float ageInTicks, float aimX, float aimY,
                             ModelPart main, ModelPart off, float inward) {
        float age = ClientArmPoseState.ageTicks(id);
        switch (pose) {
            case AMP_ZAP -> {
                // A hand thrust out at the ally, fingers first, sparks jumping from them; the other arm loose.
                float reach = smooth(age / 3F), tremor = Mth.sin(ageInTicks * 3.3F) * .02F;
                set(main, Mth.lerp(reach, -.45F, aimX + .05F) + tremor, aimY + inward * .05F, -.08F * inward);
                set(off, .2F, 0, inward * .12F);
            }
            case AMP_CUP -> {
                // Palms cupped together in front of the chest round the growing ball, trembling harder as it grows.
                float hold = smooth(age / 5F), shake = Mth.sin(ageInTicks * 2.6F) * (.012F + Math.min(.04F, age * .0012F));
                float x = Mth.lerp(hold, -.4F, aimX + .55F) + shake;
                set(main, x, aimY + inward * .48F, -.04F * inward);
                set(off, x - shake * 2, aimY - inward * .48F, .04F * inward);
            }
            case AMP_THROW -> {
                // The ball thrown from one palm: cocked back by the ear, then whipped out along the aim.
                float whip = smooth((age - 1F) / 3F), settle = smooth((age - 5F) / 4F);
                set(main, Mth.lerp(whip, -2.6F, aimX - .1F) + .45F * settle, aimY + inward * .12F * (1 - whip), -.15F * inward);
                set(off, .3F, 0, inward * .3F);
            }
            case AMP_BEAM -> {
                // Both hands driven forward together, heels of the palms touching, kicked by the beam.
                float kick = 1 - smooth(age / 7F), push = smooth(age / 2F);
                float x = Mth.lerp(push, -.9F, aimX) - .12F * kick;
                set(main, x, aimY + inward * .2F, -.05F * inward);
                set(off, x, aimY - inward * .2F, .05F * inward);
            }
            case AMP_TEMPLES -> {
                // Fingertips pressed to the temples, elbows out, a jolt running through.
                float lift = smooth(age / 4F), jolt = age > 4 && age < 7 ? Mth.sin(ageInTicks * 5F) * .05F : 0;
                set(main, Mth.lerp(lift, 0, -2.45F) + jolt, inward * .25F * lift, -inward * .65F * lift);
                set(off, Mth.lerp(lift, 0, -2.45F) - jolt, -inward * .25F * lift, inward * .65F * lift);
            }
            case AMP_PALM -> {
                // A palm laid flat on a block or a chest, trembling as the current runs into it; the other arm held off.
                float reach = smooth(age / 4F), tremor = Mth.sin(ageInTicks * 3.1F) * .03F;
                set(main, Mth.lerp(reach, -.4F, aimX + .3F) + tremor, aimY + inward * .06F, -.1F * inward);
                set(off, .35F, 0, inward * .42F);
            }
            case AMP_RAISE -> {
                // Both arms raised over the head, shaking with what is gathering there.
                float lift = smooth(age / 6F), shake = Mth.sin(ageInTicks * 2.9F) * .03F * lift;
                set(main, -2.95F * lift + shake, 0, -inward * .28F * lift);
                set(off, -2.95F * lift - shake, 0, inward * .28F * lift);
            }
            case AMP_SHOW -> {
                // Hands up and open in front of the chest, sparks dancing between the fingers.
                float open = smooth(age / 6F), sway = Mth.sin(ageInTicks * .3F) * .06F;
                set(main, -1.15F * open + sway, inward * .35F * open, -inward * .25F * open);
                set(off, -1.15F * open - sway, -inward * .35F * open, inward * .25F * open);
            }
            default -> {}
        }
    }

    public static void first(PoseStack stack, HumanoidArm arm, float side, ArmPose pose, float time, float age) {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        boolean main = player == null || arm == player.getMainArm();
        float spread = .4F, height = -.42F, depth = -.4F, pitch = -90, yaw = 0, roll = 0;
        switch (pose) {
            case AMP_ZAP -> {
                float reach = smooth(age / 3F);
                if (main) { spread = .3F; height = -.38F + .03F * reach; depth = -.4F - .18F * reach; pitch = -92 + Mth.sin(time * 3.3F) * 1.5F; yaw = 6; }
                else { spread = .62F; height = -.74F; pitch = -50; }
            }
            case AMP_CUP -> {
                float hold = smooth(age / 5F), shake = Mth.sin(time * 2.6F) * (.004F + Math.min(.012F, age * .0004F));
                spread = Mth.lerp(hold, .4F, .15F) + shake; height = -.44F + shake; depth = -.42F; pitch = -78; yaw = 26 * hold; roll = side * 10 * hold;
            }
            case AMP_THROW -> {
                float whip = smooth((age - 1F) / 3F);
                if (main) { spread = .34F; height = Mth.lerp(whip, -.18F, -.36F); depth = Mth.lerp(whip, -.25F, -.62F); pitch = Mth.lerp(whip, -150, -92); }
                else { spread = .62F; height = -.74F; pitch = -50; }
            }
            case AMP_BEAM -> {
                float kick = 1 - smooth(age / 7F);
                spread = .13F; height = -.37F + .03F * kick; depth = -.56F + .07F * kick; pitch = -94 + 10 * kick; yaw = 14;
            }
            case AMP_TEMPLES -> {
                float lift = smooth(age / 4F);
                spread = .52F; height = Mth.lerp(lift, -.6F, -.14F); depth = -.34F; pitch = Mth.lerp(lift, -70, -140); roll = -side * 18 * lift;
            }
            case AMP_PALM -> {
                float reach = smooth(age / 4F);
                if (main) { spread = .32F; height = -.42F - .14F * reach; depth = -.4F - .14F * reach; pitch = -90 - 28 * reach + Mth.sin(time * 3.1F) * 1.5F; }
                else { spread = .6F; height = -.7F; pitch = -55; }
            }
            case AMP_RAISE -> {
                float lift = smooth(age / 6F);
                spread = .58F; height = Mth.lerp(lift, -.55F, -.06F); depth = -.36F; pitch = Mth.lerp(lift, -80, -158) + Mth.sin(time * 2.9F) * 1.5F;
            }
            case AMP_SHOW -> {
                float open = smooth(age / 6F), sway = Mth.sin(time * .3F) * .01F;
                spread = .33F + sway; height = -.4F; depth = -.42F; pitch = -72; roll = side * 28 * open;
            }
            default -> {}
        }
        stack.translate(side * spread, height, depth);
        stack.mulPose(Axis.YP.rotationDegrees(side * yaw));
        stack.mulPose(Axis.XP.rotationDegrees(pitch));
        if (roll != 0) stack.mulPose(Axis.ZP.rotationDegrees(roll));
    }

    private static float smooth(float value) {
        float t = Mth.clamp(value, 0, 1);
        return t * t * (3 - 2 * t);
    }

    private static void set(ModelPart arm, float x, float y, float z) {
        arm.xRot = x;
        arm.yRot = y;
        arm.zRot = z;
    }
}
