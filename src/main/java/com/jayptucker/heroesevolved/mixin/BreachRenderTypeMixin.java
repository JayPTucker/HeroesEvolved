package com.jayptucker.heroesevolved.mixin;

import com.jayptucker.heroesevolved.client.effects.BreachViewActors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderType.class)
abstract class BreachRenderTypeMixin {
    @Inject(method = "draw", at = @At(value = "INVOKE", target =
            "Lcom/mojang/blaze3d/vertex/BufferUploader;drawWithShader(Lcom/mojang/blaze3d/vertex/MeshData;)V"))
    private void heroes$drawBreachActorsInStencil(CallbackInfo ci) {
        if (BreachViewActors.drawing()) Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
    }
}
