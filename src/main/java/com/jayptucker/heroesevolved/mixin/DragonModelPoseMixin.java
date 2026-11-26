package com.jayptucker.heroesevolved.mixin;

import com.jayptucker.heroesevolved.endgame.DragonPose;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Living Seal's head, jaw, wings and legs (endgame/DragonPose). Vanilla sets each part's angles just before drawing
 * it on every pass, so these are applied at the same moment and never accumulate.
 */
@Mixin(targets = "net.minecraft.client.renderer.entity.EnderDragonRenderer$DragonModel")
public abstract class DragonModelPoseMixin {
    @Shadow @Final private ModelPart head;
    @Shadow @Final private ModelPart jaw;
    @Shadow @Final private ModelPart leftWing;
    @Shadow @Final private ModelPart leftWingTip;
    @Shadow @Final private ModelPart rightWingTip;
    @Shadow private EnderDragon entity;
    @Shadow private float a;

    @Inject(method = "renderToBuffer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/model/geom/ModelPart;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V", ordinal = 1))
    private void heroes$head(PoseStack stack, VertexConsumer buffer, int light, int overlay, int color, CallbackInfo ci) {
        DragonPose pose = entity == null ? null : DragonPose.of(entity, a);
        if (pose == null) return;
        head.xRot += pose.head;
        jaw.xRot = Mth.lerp(pose.jaw, jaw.xRot, .75F);
    }

    @Inject(method = "renderSide", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/model/geom/ModelPart;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V", ordinal = 0))
    private void heroes$side(PoseStack stack, VertexConsumer buffer, int light, int overlay, float rotation, ModelPart wing, ModelPart frontLeg,
                             ModelPart frontLegTip, ModelPart frontFoot, ModelPart rearLeg, ModelPart rearLegTip, ModelPart rearFoot, int alpha, CallbackInfo ci) {
        DragonPose pose = entity == null ? null : DragonPose.of(entity, a);
        if (pose == null) return;
        float side = wing == leftWing ? 1 : -1, w = Mth.clamp(pose.hold, 0, 1);
        ModelPart tip = wing == leftWing ? leftWingTip : rightWingTip;
        // Raised high (negative z on the left), slammed down, spread flat, or swept back along the body.
        wing.zRot = Mth.lerp(w, wing.zRot, side * (-.95F * pose.raise + .15F));
        wing.xRot = Mth.lerp(w, wing.xRot, .125F - .35F * pose.sweep);
        wing.yRot = Mth.lerp(w, wing.yRot, side * (-.25F - 1.05F * pose.sweep));
        tip.zRot = Mth.lerp(w, tip.zRot, side * Mth.lerp(pose.spread, .9F, .05F) * (1 - .6F * pose.sweep));
        frontLeg.xRot += -1.1F * pose.front + .35F * pose.crouch;
        frontLegTip.xRot += .4F * pose.front - .3F * pose.crouch;
        rearLeg.xRot += .5F * pose.crouch;
        rearLegTip.xRot += .45F * pose.crouch;
    }
}
