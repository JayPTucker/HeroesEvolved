package com.jayptucker.heroesevolved.mixin;

import com.jayptucker.heroesevolved.client.UnseenClient;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanishing Touch can hide things that aren't creatures (a boat, a cart, an item, an armour stand's load). Creatures
 * are hidden by {@code UnseenClient}'s render hook; everything else simply isn't drawn while it is hidden.
 */
@Mixin(EntityRenderDispatcher.class)
abstract class UnseenObjectMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void heroesevolved$skipUnseenObjects(Entity entity, Frustum frustum, double cameraX, double cameraY, double cameraZ,
            CallbackInfoReturnable<Boolean> result) {
        if (!(entity instanceof LivingEntity) && UnseenClient.hidden(entity.getId())) result.setReturnValue(false);
    }
}
