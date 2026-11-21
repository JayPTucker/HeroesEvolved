package com.jayptucker.heroesevolved.mixin;

import com.jayptucker.heroesevolved.client.ClientOverloads;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Under a supercell's deck of cloud ({@code OverloadArt}) the sky's own clouds are hidden: they are drawn after the set
 * pieces, and the deck writes no depth, so they showed through it as blocks of blue sky.
 */
@Mixin(LevelRenderer.class)
public abstract class StormCloudsMixin {
    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void heroes$underTheDeck(CallbackInfo ci) {
        if (ClientOverloads.hidesClouds()) ci.cancel();
    }
}
