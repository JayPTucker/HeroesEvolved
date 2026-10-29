package com.jayptucker.heroesevolved.mixin;

import com.jayptucker.heroesevolved.client.BodyVisuals;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Armour does not float where a limb is missing: the sleeve or trouser leg of a severed limb is hidden too. */
@Mixin(HumanoidArmorLayer.class)
public abstract class SeveredArmorMixin {
    @Unique private static LivingEntity heroesevolved$wearer;

    @Inject(method = "renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;ILnet/minecraft/client/model/HumanoidModel;FFFFFF)V",
            at = @At("HEAD"))
    private void heroesevolved$rememberWearer(PoseStack pose, MultiBufferSource buffers, LivingEntity entity, EquipmentSlot slot, int light,
                                              HumanoidModel<?> model, float a, float b, float c, float d, float e, float f, CallbackInfo callback) {
        heroesevolved$wearer = entity;
    }

    @Inject(method = "setPartVisibility", at = @At("TAIL"))
    private void heroesevolved$hideSeveredLimbs(HumanoidModel<?> model, EquipmentSlot slot, CallbackInfo callback) {
        LivingEntity wearer = heroesevolved$wearer;
        heroesevolved$wearer = null;
        if (wearer == null || BodyVisuals.get(wearer) == null) return;
        if (BodyVisuals.missing(wearer, BodyVisuals.RIGHT_ARM)) model.rightArm.visible = false;
        if (BodyVisuals.missing(wearer, BodyVisuals.LEFT_ARM)) model.leftArm.visible = false;
        if (BodyVisuals.missing(wearer, BodyVisuals.RIGHT_LEG)) model.rightLeg.visible = false;
        if (BodyVisuals.missing(wearer, BodyVisuals.LEFT_LEG)) model.leftLeg.visible = false;
    }
}
