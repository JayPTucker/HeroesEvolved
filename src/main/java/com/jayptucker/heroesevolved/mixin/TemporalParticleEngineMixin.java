package com.jayptucker.heroesevolved.mixin;

import com.jayptucker.heroesevolved.client.effects.TimedParticle;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ticks and draws every particle on its own clock ({@link TimedParticle}), so the world's particles slow with time. */
@Mixin(ParticleEngine.class)
public abstract class TemporalParticleEngineMixin {
    @Inject(method = "tickParticle", at = @At("HEAD"), cancellable = true)
    private void heroes$slowTick(Particle particle, CallbackInfo ci) {
        if (!((TimedParticle) particle).heroes$advance()) ci.cancel();
    }

    @Redirect(method = "render(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;Ljava/util/function/Predicate;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/Particle;render(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/Camera;F)V"))
    private void heroes$slowDraw(Particle particle, VertexConsumer buffer, Camera camera, float partial) {
        particle.render(buffer, camera, ((TimedParticle) particle).heroes$partial(partial));
    }
}
