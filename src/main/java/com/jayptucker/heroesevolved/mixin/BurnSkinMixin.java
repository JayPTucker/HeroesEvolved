package com.jayptucker.heroesevolved.mixin;

import com.jayptucker.heroesevolved.client.BurnClient;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A burning player is drawn with a burnt copy of their skin, its pixels flaking away (BurnClient). */
@Mixin(PlayerRenderer.class)
public abstract class BurnSkinMixin {
    @Inject(method = "getTextureLocation(Lnet/minecraft/client/player/AbstractClientPlayer;)Lnet/minecraft/resources/ResourceLocation;",
            at = @At("RETURN"), cancellable = true)
    private void heroesevolved$burnt(AbstractClientPlayer player, CallbackInfoReturnable<ResourceLocation> result) {
        ResourceLocation burnt = BurnClient.skin(player, result.getReturnValue());
        if (burnt != result.getReturnValue()) result.setReturnValue(burnt);
    }
}
