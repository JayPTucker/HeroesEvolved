package com.jayptucker.heroesevolved.mixin;

import com.jayptucker.heroesevolved.client.effects.TimeVeilClient;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A caster moving faster than time leaves no shadow where they really are; only their stand-in is seen. */
@Mixin(EntityRenderDispatcher.class)
public abstract class VeiledShadowMixin {
    @Inject(method = "renderShadow", at = @At("HEAD"), cancellable = true)
    private static void heroesevolved$noShadow(PoseStack pose, MultiBufferSource buffers, Entity entity, float weight, float partialTick,
                                              LevelReader level, float size, CallbackInfo ci) {
        if (TimeVeilClient.hidden(entity)) ci.cancel();
    }
}
