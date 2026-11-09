package com.jayptucker.heroesevolved.mixin;

import com.jayptucker.heroesevolved.grid.GridDisruption;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Machines inside an electromagnetic pulse (and ones a technopath shut off) skip their ticks. */
@Mixin(Level.class)
public abstract class BlockEntityStallMixin {
    @WrapWithCondition(method = "tickBlockEntities",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/TickingBlockEntity;tick()V"))
    private boolean heroesevolved$stallMachine(TickingBlockEntity ticker) {
        return !((Object) this instanceof ServerLevel level && GridDisruption.stalled(level, ticker.getPos()));
    }
}
