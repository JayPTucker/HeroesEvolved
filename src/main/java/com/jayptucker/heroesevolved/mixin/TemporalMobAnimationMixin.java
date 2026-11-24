package com.jayptucker.heroesevolved.mixin;

import com.jayptucker.heroesevolved.client.ClientMobTime;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntityRenderer.class)
public abstract class TemporalMobAnimationMixin {
    @ModifyVariable(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private float heroes$slowPose(float value, LivingEntity entity, float yaw, float partial, PoseStack poses, MultiBufferSource buffers, int light) {
        // Its tick is paused, so vanilla's partial tick would replay the last step every frame.
        if (com.jayptucker.heroesevolved.client.effects.FrozenVisuals.isFrozen(entity)) return 1;
        return entity instanceof Mob mob ? ClientMobTime.partial(mob, value) : value;
    }
}
