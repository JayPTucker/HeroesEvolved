package com.jayptucker.heroesevolved.mixin;

import com.jayptucker.heroesevolved.client.CarryVisuals;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Excludes local first-person cargo before rendering, including its name and shadow. */
@Mixin(EntityRenderDispatcher.class)
public abstract class CarryVisibilityMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void heroesevolved$hideLocalCargo(
            Entity entity, Frustum frustum, double cameraX, double cameraY, double cameraZ,
            CallbackInfoReturnable<Boolean> result
    ) {
        if (CarryVisuals.hideInFirstPerson(entity)) result.setReturnValue(false);
    }
}
