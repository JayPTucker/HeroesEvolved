package com.jayptucker.heroesevolved.mixin;

import com.jayptucker.heroesevolved.endgame.DragonPose;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The Living Seal's neck: arched high for a roar or a breath, drawn low for a bite (endgame/DragonPose). Vanilla only
 * reads this offset to draw the neck, and the pose is only ever set on the client.
 */
@Mixin(EnderDragon.class)
public abstract class EnderDragonPoseMixin {
    @Inject(method = "getHeadPartYOffset", at = @At("RETURN"), cancellable = true)
    private void heroes$neck(int part, double[] spine, double[] parts, CallbackInfoReturnable<Float> ci) {
        EnderDragon dragon = (EnderDragon) (Object) this;
        if (!dragon.level().isClientSide || part >= 6) return;
        DragonPose pose = DragonPose.of(dragon, 0);
        if (pose != null) ci.setReturnValue(ci.getReturnValue() + pose.neck);
    }
}
