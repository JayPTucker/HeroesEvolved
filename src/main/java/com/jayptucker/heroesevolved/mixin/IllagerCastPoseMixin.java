package com.jayptucker.heroesevolved.mixin;

import com.jayptucker.heroesevolved.client.ClientGifted;
import com.jayptucker.heroesevolved.gifted.MobMoves;
import net.minecraft.client.model.IllagerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.AbstractIllager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A gifted illager winding up a move ({@code gifted/GiftedMob}) holds its arms the way the move will leave: both raised
 * like an evoker's spell for a cast, one aimed at its target for a pointed move, both overhead for a slam. Arms that
 * were crossed uncross. Everything else about the model is vanilla's.
 */
@Mixin(IllagerModel.class)
public abstract class IllagerCastPoseMixin {
    @Shadow @Final private ModelPart head;
    @Shadow @Final private ModelPart arms;
    @Shadow @Final private ModelPart rightArm;
    @Shadow @Final private ModelPart leftArm;

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/monster/AbstractIllager;FFFFF)V", at = @At("TAIL"))
    private void heroesevolved$castPose(AbstractIllager illager, float limbSwing, float limbSwingAmount, float age, float headYaw,
                                         float headPitch, CallbackInfo ci) {
        ClientGifted.State state = ClientGifted.get(illager);
        if (state == null || !state.casting()) return;
        MobMoves.Pose pose = MobMoves.Pose.values()[Math.clamp(state.pose(), 0, MobMoves.Pose.values().length - 1)];
        if (pose == MobMoves.Pose.NONE) return;
        arms.visible = false;
        leftArm.visible = true;
        rightArm.visible = true;
        rightArm.z = 0; rightArm.x = -5; leftArm.z = 0; leftArm.x = 5;
        float shake = Mth.cos(age * .6662F) * .12F;
        switch (pose) {
            case CAST -> {
                rightArm.xRot = shake; leftArm.xRot = -shake;
                rightArm.yRot = 0; leftArm.yRot = 0;
                rightArm.zRot = (float) (Math.PI * 3 / 4); leftArm.zRot = (float) (-Math.PI * 3 / 4);
            }
            case POINT -> {
                rightArm.xRot = -Mth.HALF_PI + head.xRot + shake * .3F; rightArm.yRot = head.yRot; rightArm.zRot = 0;
                leftArm.xRot = -.5F; leftArm.yRot = .3F; leftArm.zRot = 0;
            }
            case SLAM -> {
                rightArm.xRot = -Mth.PI * .92F + shake; leftArm.xRot = -Mth.PI * .92F + shake;
                rightArm.yRot = -.15F; leftArm.yRot = .15F;
                rightArm.zRot = 0; leftArm.zRot = 0;
            }
            default -> { }
        }
    }
}
