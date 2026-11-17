package com.jayptucker.heroesevolved.mixin;

import com.jayptucker.heroesevolved.time.TemporalMobTime;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Optional;

@Mixin(Brain.class)
public abstract class TemporalMobBrainMixin {
    @Inject(method = "getMemory", at = @At("RETURN"), cancellable = true)
    private void heroes$remembered(MemoryModuleType<?> memory, CallbackInfoReturnable<Optional<?>> result) {
        if (memory != MemoryModuleType.ATTACK_TARGET) return;
        var owner = TemporalMobTime.owner((Brain<?>) (Object) this);
        if (owner != null && result.getReturnValue().orElse(null) instanceof LivingEntity target)
            result.setReturnValue(Optional.ofNullable(TemporalMobTime.target(owner, target)));
    }
}
