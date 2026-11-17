package com.jayptucker.heroesevolved.mixin;

import com.jayptucker.heroesevolved.client.ClientTimeFlow;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Rain and snow fall on their own clock ({@link ClientTimeFlow#weatherTicks}), which runs at the camera's rate: in slowed
 * time the drops crawl down, and when time stops they hang in the air. Splashes and the patter follow the same clock.
 */
@Mixin(LevelRenderer.class)
public abstract class TemporalWeatherMixin {
    @Redirect(method = "renderSnowAndRain", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/LevelRenderer;ticks:I", opcode = Opcodes.GETFIELD))
    private int heroes$weatherTicks(LevelRenderer renderer) {
        return ClientTimeFlow.weatherTicks();
    }

    @ModifyVariable(method = "renderSnowAndRain", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float heroes$weatherPartial(float partial) {
        return ClientTimeFlow.weatherPartial(partial);
    }

    @Inject(method = "tickRain", at = @At("HEAD"), cancellable = true)
    private void heroes$slowSplashes(Camera camera, CallbackInfo ci) {
        if (!ClientTimeFlow.rainTick()) ci.cancel();
    }
}
