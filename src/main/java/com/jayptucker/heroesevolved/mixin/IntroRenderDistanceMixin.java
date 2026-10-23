package com.jayptucker.heroesevolved.mixin;

import com.jayptucker.heroesevolved.time.intro.TimeIntroService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A dimension-local cap; the player's saved graphics settings are never changed. */
@Mixin(Options.class)
public abstract class IntroRenderDistanceMixin {
    @Inject(method = "getEffectiveRenderDistance", at = @At("RETURN"), cancellable = true)
    private void heroesEvolved$introDistance(CallbackInfoReturnable<Integer> result) {
        var level = Minecraft.getInstance().level;
        if (level != null && TimeIntroService.isCity(level)) result.setReturnValue(Math.min(12, result.getReturnValue()));
    }
}
