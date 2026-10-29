package com.jayptucker.heroesevolved.mixin;

import com.jayptucker.heroesevolved.client.BodyVisuals;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A severed hand holds nothing. */
@Mixin(ItemInHandLayer.class)
public abstract class SeveredHandItemMixin {
    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void heroesevolved$noHandNoItem(LivingEntity entity, ItemStack stack, ItemDisplayContext context, HumanoidArm arm,
                                            PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo callback) {
        if (BodyVisuals.missing(entity, BodyVisuals.arm(entity, arm))) callback.cancel();
    }
}
