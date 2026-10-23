package com.jayptucker.heroesevolved.mixin;

import com.jayptucker.heroesevolved.network.ClientFlightVisualState;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The Flight wheel owns airborne speed; vanilla sprint cannot add a second accelerator. */
@Mixin(LocalPlayer.class)
abstract class FlightSprintMixin {
    @Inject(method = "canStartSprinting", at = @At("HEAD"), cancellable = true)
    private void heroesevolved$wheelControlsFlightSpeed(CallbackInfoReturnable<Boolean> callback) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        if (!player.onGround() && ClientFlightVisualState.isFlightEnabled(player.getId())) {
            player.setSprinting(false);
            callback.setReturnValue(false);
        }
    }
}
