package com.jayptucker.heroesevolved.mixin;

import com.jayptucker.heroesevolved.time.TemporalMobTime;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class TemporalMobSensesMixin {
    @Inject(method = "hasLineOfSight", at = @At("HEAD"), cancellable = true)
    private void heroes$unseen(Entity other, CallbackInfoReturnable<Boolean> result) {
        if ((Object) this instanceof Mob mob && !mob.level().isClientSide && other instanceof LivingEntity living
                && TemporalMobTime.target(mob, living) != living) result.setReturnValue(false);
    }
    @Inject(method = "canAttack(Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
    private void heroes$cannotTrack(LivingEntity other, CallbackInfoReturnable<Boolean> result) {
        if ((Object) this instanceof Mob mob && !mob.level().isClientSide
                && TemporalMobTime.target(mob, other) != other) result.setReturnValue(false);
    }
}
