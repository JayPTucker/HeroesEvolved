package com.jayptucker.heroesevolved.mixin;

import com.jayptucker.heroesevolved.time.TemporalMobTime;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public abstract class TemporalMobTargetMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void heroes$brainOwner(org.spongepowered.asm.mixin.injection.callback.CallbackInfo callback) {
        Mob mob = (Mob) (Object) this;
        if (!mob.level().isClientSide) TemporalMobTime.track(mob);
    }
    @Inject(method = "getTarget", at = @At("RETURN"), cancellable = true)
    private void heroes$lastSeen(CallbackInfoReturnable<LivingEntity> result) {
        Mob mob = (Mob) (Object) this;
        if (!mob.level().isClientSide) result.setReturnValue(TemporalMobTime.target(mob, result.getReturnValue()));
    }
}
