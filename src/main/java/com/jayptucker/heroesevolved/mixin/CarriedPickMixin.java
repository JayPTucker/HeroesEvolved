package com.jayptucker.heroesevolved.mixin;

import com.jayptucker.heroesevolved.entity.CarryAnchorEntity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Something carried in your arms is in front of you, so it must not catch your crosshair: you can
 * still click the ground to set it down, mine and use items. Carried cargo is protected anyway, so
 * nothing is lost by letting clicks and shots pass it by.
 */
@Mixin(LivingEntity.class)
public abstract class CarriedPickMixin {
    @Inject(method = "isPickable", at = @At("HEAD"), cancellable = true)
    private void heroesevolved$carriedIsNotPickable(CallbackInfoReturnable<Boolean> result) {
        if (((LivingEntity) (Object) this).getVehicle() instanceof CarryAnchorEntity) result.setReturnValue(false);
    }
}
