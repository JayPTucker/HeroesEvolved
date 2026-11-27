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
 * Induced Radioactivity's own gestures, in third person ({@link #third}, from {@code PowerArmPoses}) and first person
 * ({@link #first}, from {@code FirstPersonArmGeometry}), in {@link KineticPoses}' conventions. A man who is a reactor:
 * the hands cup a core at the chest, palms thrust heat along the aim, the arms fling wide when the core lets go, a fist
 * takes hold of a collar, the core is raised overhead, two fingers send a lance.
 */
public final class RadiationPoses {
    private RadiationPoses() {}

    public static boolean owns(ArmPose pose) {
        return switch (pose) {
            case RAD_CUP, RAD_PALMS, RAD_FLARE, RAD_GRIP, RAD_RAISE, RAD_POINT -> true;
            default -> false;
        };
    }

    public static void third(PlayerModel<?> model, ArmPose pose, int id, float ageInTicks, float aimX, float aimY,
                             ModelPart main, ModelPart off, float inward) {
        float age = ClientArmPoseState.ageTicks(id);
        float heat = Mth.sin(ageInTicks * 1.9F) * .02F;
        switch (pose) {
            case RAD_CUP -> {
                // Both palms cupped round the core in front of the chest, trembling as it swells.
                float close = smooth(age / 4F);
                set(model.rightArm, -1.15F + heat, Mth.lerp(close, -.2F, -.55F), .05F);
                set(model.leftArm, -1.15F - heat, Mth.lerp(close, .2F, .55F), -.05F);
            }
            case RAD_PALMS -> {
                // Both palms thrust out along the aim, heels together, holding the heat on the target.
                float drive = smooth(age / 2.5F);
                set(model.rightArm, Mth.lerp(drive, -1.0F, aimX) + heat, aimY - .2F, .04F);
                set(model.leftArm, Mth.lerp(drive, -1.0F, aimX) - heat, aimY + .2F, -.04F);
            }
            case RAD_FLARE -> {
                // The arms flung wide and up, chest thrown out: the core letting go.
                float fling = smooth(age / 3F);
                set(model.rightArm, Mth.lerp(fling, -.6F, -.45F), 0, Mth.lerp(fling, .3F, 1.55F) + heat);
                set(model.leftArm, Mth.lerp(fling, -.6F, -.45F), 0, -Mth.lerp(fling, .3F, 1.55F) - heat);
            }
            case RAD_GRIP -> {
                // One fist takes hold of the foe's collar at arm's length; the other drawn back at the ribs.
                float reach = smooth(age / 2.5F);
                set(main, Mth.lerp(reach, -.5F, aimX + .1F) + heat, aimY + inward * .05F, -.08F * inward);
                set(off, -.55F, -inward * .1F, inward * .32F);
            }
            case RAD_RAISE -> {
                // The core raised overhead in both hands.
                float lift = smooth(age / 6F);
                set(model.rightArm, Mth.lerp(lift, -1.1F, -2.8F) + heat, -.35F, .2F);
                set(model.leftArm, Mth.lerp(lift, -1.1F, -2.8F) - heat, .35F, -.2F);
            }
            case RAD_POINT -> {
                // Two fingers flicked straight at the target; the other hand low.
                float flick = Mth.sin(Math.min(1, age / 4F) * Mth.PI) * .18F;
                set(main, aimX - flick, aimY, -.05F * inward);
                set(off, .2F, -inward * .4F, inward * .5F);
            }
            default -> { }
        }
    }

    public static void first(PoseStack stack, HumanoidArm arm, float side, ArmPose pose, float time, float age) {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        boolean main = player == null || arm == player.getMainArm();
        float spread = .4F, height = -.42F, depth = -.4F, pitch = -90, yaw = 0, roll = 0;
        float heat = Mth.sin(time * 1.9F) * .005F;
        switch (pose) {
            case RAD_CUP -> {
                float close = smooth(age / 4F);
                spread = Mth.lerp(close, .34F, .2F); height = -.42F + heat; depth = -.42F; pitch = -95; yaw = -10 * close; roll = side * 30 * close;
            }
            case RAD_PALMS -> {
                float drive = smooth(age / 2.5F);
                spread = .22F; height = -.36F + heat; depth = -.3F - .24F * drive; pitch = -92; roll = -side * 70;
            }
            case RAD_FLARE -> {
                float fling = smooth(age / 3F);
                spread = .45F + .3F * fling; height = -.45F + .1F * fling; depth = -.38F; pitch = -80 - 30 * fling; yaw = -side * 25 * fling;
            }
            case RAD_GRIP -> {
                float reach = smooth(age / 2.5F);
                if (main) { spread = .28F; height = -.4F; depth = -.32F - .26F * reach; pitch = -92; yaw = 8; }
                else { spread = .6F; height = -.7F; pitch = -50; }
            }
            case RAD_RAISE -> {
                float lift = smooth(age / 6F);
                spread = .3F; height = Mth.lerp(lift, -.45F, .02F) + heat; depth = -.4F; pitch = Mth.lerp(lift, -95, -160);
            }
            case RAD_POINT -> {
                float flick = (float) Math.sin(Math.min(1, age / 4) * Math.PI);
                if (main) { spread = .28F; height = -.36F; depth = -.5F - .05F * flick; pitch = -92 + 8 * flick; yaw = 6; }
                else { spread = .56F; height = -.62F; pitch = -50; }
            }
            default -> { }
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

    private static void set(ModelPart part, float x, float y, float z) {
        part.xRot = x;
        part.yRot = y;
        part.zRot = z;
    }
}
