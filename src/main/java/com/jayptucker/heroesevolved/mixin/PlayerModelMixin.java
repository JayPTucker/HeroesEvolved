package com.jayptucker.heroesevolved.mixin;

import com.jayptucker.heroesevolved.client.PowerArmPoses;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lets powers override arm angles after vanilla has finished posing them. */
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {
    @Inject(
            method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",
            at = @At("TAIL")
    )
    private void heroesevolved$applyPowerArmPose(
            LivingEntity entity,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch,
            CallbackInfo callback
    ) {
        PowerArmPoses.apply((PlayerModel<?>) (Object) this, entity, ageInTicks);
    }
}
