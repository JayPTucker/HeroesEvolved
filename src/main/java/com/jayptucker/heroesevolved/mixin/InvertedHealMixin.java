package com.jayptucker.heroesevolved.mixin;

import com.jayptucker.heroesevolved.shapeshift.ShapeshiftTraitService;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A shapeshifter in an undead form is healed by harm and harmed by healing, as the undead are. */
@Mixin(LivingEntity.class)
public abstract class InvertedHealMixin {
    @Inject(method = "isInvertedHealAndHarm", at = @At("HEAD"), cancellable = true)
    private void heroesevolved$undeadForm(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Player player && ShapeshiftTraitService.inverted(player)) cir.setReturnValue(true);
    }
}
