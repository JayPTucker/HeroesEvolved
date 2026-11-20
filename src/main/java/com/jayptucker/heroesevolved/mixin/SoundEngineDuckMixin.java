package com.jayptucker.heroesevolved.mixin;

import com.jayptucker.heroesevolved.client.DazeView;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ears ringing after a blast ({@link DazeView}): every sound but the ringing itself (played on the master channel) comes
 * through muffled for a while. The player's own volume settings are never changed.
 */
@Mixin(SoundEngine.class)
public abstract class SoundEngineDuckMixin {
    @Inject(method = "calculateVolume(FLnet/minecraft/sounds/SoundSource;)F", at = @At("RETURN"), cancellable = true)
    private void heroesEvolved$ringing(float volume, SoundSource source, CallbackInfoReturnable<Float> result) {
        if (source == SoundSource.MASTER) return;
        float duck = DazeView.duck();
        if (duck < 1) result.setReturnValue(result.getReturnValue() * duck);
    }
}
