package com.jayptucker.heroesevolved.client;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.entity.CarryAnchorEntity;
import com.jayptucker.heroesevolved.network.ClientArmPoseState;
import com.jayptucker.heroesevolved.network.ClientFlightVisualState;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Applies power arm poses to the player model. Called from a mixin at the
 * end of {@code PlayerModel#setupAnim}, because vanilla recalculates every
 * limb angle there; poses set any earlier (e.g. in a render event) are lost.
 */
public final class PowerArmPoses {
    private PowerArmPoses() {
    }

    public static void apply(PlayerModel<?> model, LivingEntity entity, float ageInTicks) {
        boolean flying = entity instanceof Player player && !player.onGround()
                && !(player.getVehicle() instanceof CarryAnchorEntity)
                && ClientFlightVisualState.isFlightActive(player.getId());
        if (flying) {
            float flutter = Mth.sin(ageInTicks * .22F) * .025F;
            float spread = ClientFlightVisualState.isDiving(entity.getId()) ? .045F : .08F;
            set(model.rightLeg, .035F + flutter, 0, spread);
            set(model.leftLeg, -.035F - flutter, 0, -spread);
            model.rightPants.copyFrom(model.rightLeg);
            model.leftPants.copyFrom(model.leftLeg);
        }
        ArmPose pose = ClientArmPoseState.get(entity.getId());
        CarryAnchorEntity carryAnchor = CarryVisuals.anchor(entity);
        if (carryAnchor != null && (pose == null || pose == ArmPose.TWO_HANDS)) {
            // Vanilla's empty-hand renderer also calls setupAnim; leave that arm below the view.
            if (CarryVisuals.isLocalFirstPersonCarrier(entity)) return;
            // Follow the actual seat if a wall moves cargo to the carrier's other side.
            int side = CarryVisuals.supportSide(entity, carryAnchor);
            ModelPart support = side > 0 ? model.rightArm : model.leftArm;
            ModelPart free = side > 0 ? model.leftArm : model.rightArm;
            set(support, -.72F, -.08F * side, .72F * side);
            set(free, flying ? -2.98F : -.08F, 0, (flying ? -.08F : -.04F) * side);
            model.rightSleeve.copyFrom(model.rightArm);
            model.leftSleeve.copyFrom(model.leftArm);
            return;
        }
        if (pose == null) {
            if (flying) {
                // A leading fist and a trailing stabilizer give cruising a readable silhouette.
                float speed = (float) Math.clamp(entity.getDeltaMovement().length() / 2.8, 0, 1);
                float turn = Mth.clamp(Mth.wrapDegrees(entity.getYRot() - entity.yRotO) * .015F, -.25F, .25F);
                float breath = Mth.sin(ageInTicks * .16F) * .018F;
                boolean rightHanded = entity.getMainArm() == HumanoidArm.RIGHT;
                ModelPart leading = rightHanded ? model.rightArm : model.leftArm;
                ModelPart trailing = rightHanded ? model.leftArm : model.rightArm;
                float side = rightHanded ? 1 : -1;
                set(leading, -2.98F + breath, side * -.07F, side * (.08F + turn));
                if (ClientFlightVisualState.isDiving(entity.getId()))
                    set(trailing, -2.98F - breath, side * .07F, side * (-.08F + turn));
                else
                    set(trailing, .12F - speed * .15F - breath, 0, side * (-.16F + turn));
                model.rightSleeve.copyFrom(model.rightArm);
                model.leftSleeve.copyFrom(model.leftArm);
            }
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
            case KINETIC_GATHER -> {
                float pull = .5F + .5F * Mth.sin(ClientArmPoseState.ageTicks(entity.getId()) * .38F);
                set(model.rightArm, -1.32F + pull * .42F, aimY * .35F - .22F, .32F);
                set(model.leftArm, -1.32F + (1 - pull) * .42F, aimY * .35F + .22F, -.32F);
            }
            case KINETIC_HOLD -> {
                float tension = Mth.sin(ageInTicks * 2.3F) * .014F;
                ModelPart brace = rightHanded ? model.leftArm : model.rightArm;
                set(main, aimX + .16F + tension, aimY + inward * .12F, -inward * .12F);
                set(brace, -.92F - tension, aimY * .35F - inward * .25F, inward * .4F);
            }
            case KINETIC_RELEASE -> {
                float phase = Mth.clamp(ClientArmPoseState.ageTicks(entity.getId()) / 9F, 0, 1);
                float thrust = Mth.sin(phase * Mth.PI);
                ModelPart brace = rightHanded ? model.leftArm : model.rightArm;
                set(main, aimX + .18F - thrust * .3F, aimY + inward * .06F, -inward * .08F);
                set(brace, aimX + .75F - thrust * .32F, aimY - inward * .3F, inward * .28F);
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
