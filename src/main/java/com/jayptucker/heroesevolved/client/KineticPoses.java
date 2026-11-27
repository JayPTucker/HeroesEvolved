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
 * Telekinesis's gestures, the hand the far hand copies, in third person ({@link #third}, from {@code PowerArmPoses}) and
 * first person ({@link #first}, from {@code FirstPersonArmGeometry}). The conventions are {@link AmpPoses}': {@code aimX}
 * points an arm along the look; {@code main} casts and {@code off} is the other arm; an arm swings outward with
 * {@code z = -inward * k} (main) or {@code z = inward * k} (off). Whatever the hand does here, the far hand does where the
 * force lands, so every pose is a readable hand shape held where the camera and the onlookers can see it.
 */
public final class KineticPoses {
    private KineticPoses() {}

    public static boolean owns(ArmPose pose) {
        return switch (pose) {
            case KINETIC_PIN, KINETIC_CATCH, KINETIC_PLUCK, KINETIC_FLING, KINETIC_STORM, KINETIC_SLAM, KINETIC_LEVITATE,
                    KINETIC_SKYFALL, KINETIC_BECKON, KINETIC_JUGGLE -> true;
            default -> false;
        };
    }

    public static void third(PlayerModel<?> model, ArmPose pose, int id, float ageInTicks, float aimX, float aimY,
                             ModelPart main, ModelPart off, float inward) {
        float age = ClientArmPoseState.ageTicks(id);
        float strain = Mth.sin(ageInTicks * 2.4F) * .025F;
        switch (pose) {
            case KINETIC_PIN -> {
                // The palm driven straight out at the body and held there, the other hand bracing the wrist.
                float drive = smooth(age / 2F);
                set(main, Mth.lerp(drive, aimX + .8F, aimX - .05F) + strain, aimY + inward * .04F, -.05F * inward);
                set(off, Mth.lerp(drive, -.4F, aimX + .45F) - strain, aimY - inward * .5F, .1F * inward);
            }
            case KINETIC_CATCH -> {
                // Both hands raised in front, wide and cupped: the shots hang in the air before them.
                float raise = smooth(age / 3F);
                float x = Mth.lerp(raise, -.3F, aimX + .35F) + strain;
                set(main, x, aimY - inward * .38F, -.25F * inward * raise);
                set(off, x - strain * 2, aimY + inward * .38F, .25F * inward * raise);
            }
            case KINETIC_PLUCK -> {
                // Two fingers pinched at the target, then yanked back past the shoulder.
                float reach = smooth(age / 2F), yank = smooth((age - 3F) / 3F);
                set(main, Mth.lerp(yank, Mth.lerp(reach, -.6F, aimX), -1.5F), aimY + inward * .45F * yank, -.15F * inward - .3F * inward * yank);
                set(off, -.35F, 0, inward * .2F);
            }
            case KINETIC_FLING -> {
                // Cocked by the ear, then whipped out along the aim.
                float whip = smooth((age - 1F) / 2.5F), settle = smooth((age - 5F) / 4F);
                set(main, Mth.lerp(whip, -2.75F, aimX - .15F) + .4F * settle, aimY + inward * .15F * (1 - whip), -.15F * inward);
                set(off, .35F * whip, -inward * .2F, inward * .35F);
            }
            case KINETIC_STORM -> {
                // Both arms raised high, palms up, shaking under the weight of everything held.
                float lift = smooth(age / 8F), shake = Mth.sin(ageInTicks * 2.9F) * .045F * lift;
                set(model.rightArm, Mth.lerp(lift, -.8F, -2.65F) + shake, 0, Mth.lerp(lift, .5F, .42F));
                set(model.leftArm, Mth.lerp(lift, -.8F, -2.65F) - shake, 0, -Mth.lerp(lift, .5F, .42F));
            }
            case KINETIC_SLAM -> {
                // Both fists pulled down hard from overhead.
                float pull = smooth(age / 2.5F);
                set(model.rightArm, Mth.lerp(pull, -2.8F, -.35F), 0, Mth.lerp(pull, .3F, .12F));
                set(model.leftArm, Mth.lerp(pull, -2.8F, -.35F), 0, -Mth.lerp(pull, .3F, .12F));
            }
            case KINETIC_LEVITATE -> {
                // Arms out low to the sides, palms down, pressing on the air beneath.
                float sway = Mth.sin(ageInTicks * .12F) * .07F;
                set(model.rightArm, -.25F + sway, 0, .85F - sway);
                set(model.leftArm, -.25F - sway, 0, -.85F + sway);
            }
            case KINETIC_SKYFALL -> {
                // One arm thrust straight up, the hand open and straining; the other braced low.
                float raise = smooth(age / 4F);
                set(main, Mth.lerp(raise, aimX, -3.05F) + strain * 2, inward * .1F, -.12F * inward);
                set(off, -.55F, -inward * .25F, inward * .45F);
            }
            case KINETIC_BECKON -> {
                // A palm turned up and offered, fingers curling: come here.
                float reach = smooth(age / 3F), curl = Mth.sin(Math.min(1, age / 10F) * Mth.PI) * .25F;
                set(main, Mth.lerp(reach, -.5F, aimX + .25F) + curl, aimY, -.1F * inward);
                set(off, -.2F, 0, inward * .15F);
            }
            case KINETIC_JUGGLE -> {
                // A palm flicked up from the hip, again and again.
                float flick = Mth.abs(Mth.sin(age * .45F));
                set(main, Mth.lerp(flick, -.3F, aimX - .3F), aimY + inward * .2F, -.12F * inward);
                set(off, Mth.lerp(1 - flick, -.3F, aimX - .3F), aimY - inward * .2F, .12F * inward);
            }
            default -> { }
        }
    }

    public static void first(PoseStack stack, HumanoidArm arm, float side, ArmPose pose, float time, float age) {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        boolean main = player == null || arm == player.getMainArm();
        float spread = .4F, height = -.42F, depth = -.4F, pitch = -90, yaw = 0, roll = 0;
        float strain = Mth.sin(time * 2.4F) * .006F;
        switch (pose) {
            case KINETIC_PIN -> {
                float drive = smooth(age / 2F);
                if (main) { spread = .26F; height = -.36F + strain; depth = -.32F - .26F * drive; pitch = -92; yaw = 8; roll = -side * 70; }
                else { spread = .16F; height = -.5F - strain; depth = -.36F; pitch = -70; yaw = 35; }
            }
            case KINETIC_CATCH -> {
                float raise = smooth(age / 3F);
                spread = .46F; height = Mth.lerp(raise, -.6F, -.3F) + strain; depth = -.45F; pitch = -100; yaw = -side * 14; roll = side * 25 * raise;
            }
            case KINETIC_PLUCK -> {
                float reach = smooth(age / 2F), yank = smooth((age - 3F) / 3F);
                if (main) { spread = .3F + .12F * yank; height = -.36F + .08F * yank; depth = -.3F - .25F * reach + .3F * yank; pitch = -90 + 45 * yank; yaw = 10; }
                else { spread = .6F; height = -.7F; pitch = -50; }
            }
            case KINETIC_FLING -> {
                float whip = smooth((age - 1F) / 2.5F);
                if (main) { spread = .34F; height = Mth.lerp(whip, -.16F, -.36F); depth = Mth.lerp(whip, -.24F, -.62F); pitch = Mth.lerp(whip, -150, -92); }
                else { spread = .62F; height = -.74F; pitch = -50; }
            }
            case KINETIC_STORM -> {
                float lift = smooth(age / 8F), shake = Mth.sin(time * 2.9F) * .01F * lift;
                spread = .52F; height = Mth.lerp(lift, -.55F, -.04F) + shake; depth = -.38F; pitch = Mth.lerp(lift, -80, -158); roll = -side * 12 * lift;
            }
            case KINETIC_SLAM -> {
                float pull = smooth(age / 2.5F);
                spread = .4F; height = Mth.lerp(pull, -.04F, -.7F); depth = -.4F; pitch = Mth.lerp(pull, -158, -55);
            }
            case KINETIC_LEVITATE -> {
                float sway = Mth.sin(time * .12F) * .015F;
                spread = .64F; height = -.58F + sway; depth = -.36F; pitch = -68; roll = side * 40;
            }
            case KINETIC_SKYFALL -> {
                float raise = smooth(age / 4F);
                if (main) { spread = .3F; height = Mth.lerp(raise, -.4F, .05F) + strain; depth = -.4F; pitch = Mth.lerp(raise, -90, -165); }
                else { spread = .6F; height = -.72F; pitch = -55; }
            }
            case KINETIC_BECKON -> {
                float reach = smooth(age / 3F), curl = Mth.sin(Math.min(1, age / 10F) * Mth.PI);
                if (main) { spread = .3F; height = -.4F; depth = -.42F - .1F * reach + .08F * curl; pitch = -82 + 14 * curl; roll = side * 80; }
                else { spread = .6F; height = -.72F; pitch = -50; }
            }
            case KINETIC_JUGGLE -> {
                float flick = Mth.abs(Mth.sin(age * .45F + (side > 0 ? 0 : Mth.HALF_PI)));
                spread = .4F; height = -.6F + .2F * flick; depth = -.42F; pitch = -60 - 40 * flick; roll = side * 70;
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

    private static void set(ModelPart arm, float x, float y, float z) {
        arm.xRot = x;
        arm.yRot = y;
        arm.zRot = z;
    }
}
