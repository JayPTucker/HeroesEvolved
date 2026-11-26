package com.jayptucker.heroesevolved.mixin;

import com.jayptucker.heroesevolved.endgame.DragonPose;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EnderDragonRenderer;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The Living Seal's whole body: rearing, diving, rolling into a tail sweep, crouching (endgame/DragonPose). */
@Mixin(EnderDragonRenderer.class)
public abstract class EnderDragonRendererPoseMixin {
    @Inject(method = "render(Lnet/minecraft/world/entity/boss/enderdragon/EnderDragon;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 0))
    private void heroes$pose(EnderDragon dragon, float yaw, float partial, PoseStack stack, MultiBufferSource buffers, int light, CallbackInfo ci) {
        DragonPose pose = DragonPose.of(dragon, partial);
        if (pose == null) return;
        stack.translate(0, pose.lift - pose.crouch * .9F, 0);
        stack.mulPose(Axis.XP.rotationDegrees(pose.pitch));
        stack.mulPose(Axis.ZP.rotationDegrees(pose.roll));
    }
}
