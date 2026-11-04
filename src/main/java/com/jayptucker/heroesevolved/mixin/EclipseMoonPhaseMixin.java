package com.jayptucker.heroesevolved.mixin;

import com.jayptucker.heroesevolved.data.EclipseCalendar;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A solar eclipse can only happen at a new moon, so dimensions with a day cycle follow the
 * Eclipse calendar's lunar month. Moon rendering, slime spawning and moon brightness all read
 * this one method, on both sides.
 */
@Mixin(DimensionType.class)
public abstract class EclipseMoonPhaseMixin {
    @Shadow
    public abstract boolean hasFixedTime();

    @Inject(method = "moonPhase", at = @At("HEAD"), cancellable = true)
    private void heroesEvolved$eclipseMoon(long dayTime, CallbackInfoReturnable<Integer> result) {
        if (!hasFixedTime()) result.setReturnValue(EclipseCalendar.moonPhase(dayTime));
    }
}
