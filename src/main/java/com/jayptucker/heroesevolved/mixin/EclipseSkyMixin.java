package com.jayptucker.heroesevolved.mixin;

import com.jayptucker.heroesevolved.network.ClientEclipseState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The Eclipse's shadow. Sky darken drives the lightmap, so everything lit by the sky (anyone
 * standing outside) falls into near-night while torch-lit interiors keep their light. The sky
 * dome and clouds dim with it and the stars come out at totality.
 */
@Mixin(ClientLevel.class)
public abstract class EclipseSkyMixin {
    /** Vanilla night is 0.2; totality stays a touch brighter, like deep twilight. */
    @Unique
    private static final float TOTALITY_SKY_DARKEN = 0.3F;
    @Unique
    private static final Vec3 TOTALITY_SKY = new Vec3(0.03, 0.04, 0.1);
    @Unique
    private static final Vec3 TOTALITY_CLOUDS = new Vec3(0.1, 0.1, 0.14);

    @Unique
    private float heroesEvolved$darkness(float partialTick) {
        return ClientEclipseState.darkness((ClientLevel) (Object) this, partialTick);
    }

    @Inject(method = "getSkyDarken", at = @At("RETURN"), cancellable = true)
    private void heroesEvolved$eclipseSkyDarken(float partialTick, CallbackInfoReturnable<Float> result) {
        float darkness = heroesEvolved$darkness(partialTick);
        if (darkness <= 0) return;
        float vanilla = result.getReturnValue();
        result.setReturnValue(Mth.lerp(darkness, vanilla, Math.min(vanilla, TOTALITY_SKY_DARKEN)));
    }

    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
    private void heroesEvolved$eclipseSkyColor(Vec3 pos, float partialTick, CallbackInfoReturnable<Vec3> result) {
        float darkness = heroesEvolved$darkness(partialTick);
        if (darkness > 0) result.setReturnValue(result.getReturnValue().lerp(TOTALITY_SKY, darkness));
    }

    @Inject(method = "getCloudColor", at = @At("RETURN"), cancellable = true)
    private void heroesEvolved$eclipseCloudColor(float partialTick, CallbackInfoReturnable<Vec3> result) {
        float darkness = heroesEvolved$darkness(partialTick);
        if (darkness > 0) result.setReturnValue(result.getReturnValue().lerp(TOTALITY_CLOUDS, darkness));
    }

    @Inject(method = "getStarBrightness", at = @At("RETURN"), cancellable = true)
    private void heroesEvolved$eclipseStars(float partialTick, CallbackInfoReturnable<Float> result) {
        float darkness = heroesEvolved$darkness(partialTick);
        if (darkness > 0) result.setReturnValue(Math.max(result.getReturnValue(), darkness * darkness * 0.5F));
    }
}
