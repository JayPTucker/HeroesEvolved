package com.jayptucker.heroesevolved.mixin;

import com.jayptucker.heroesevolved.grid.GridDisruption;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Machines inside an electromagnetic pulse (and ones an electrokinetic shut off with Kill Switch) skip their ticks. */
@Mixin(Level.class)
public abstract class BlockEntityStallMixin {
    @WrapWithCondition(method = "tickBlockEntities",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/TickingBlockEntity;tick()V"))
    private boolean heroesevolved$stallMachine(TickingBlockEntity ticker) {
        if ((Object) this instanceof ServerLevel level
                && com.jayptucker.heroesevolved.time.TemporalRevenge.get(level.getServer()).hasRedeemed()) {
            if (level.getBlockEntity(ticker.getPos()) instanceof net.minecraft.world.Container container)
                com.jayptucker.heroesevolved.time.TemporalRevenge.clean(level.getServer(), container);
            // A hopper may pull from a chest that never ticks. Clear invalidated history before any transfer.
            for (var direction : net.minecraft.core.Direction.values()) {
                var pos = ticker.getPos().relative(direction);
                if (level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof net.minecraft.world.Container container)
                    com.jayptucker.heroesevolved.time.TemporalRevenge.clean(level.getServer(), container);
            }
        }
        return !((Object) this instanceof ServerLevel level && GridDisruption.stalled(level, ticker.getPos()));
    }
}
