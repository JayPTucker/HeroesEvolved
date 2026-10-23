package com.jayptucker.heroesevolved.mixin;

import com.jayptucker.heroesevolved.time.TimeRewindBlocks;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

/** Captures mining, explosions, fire, commands, fluids, and ability block changes. */
@Mixin(Level.class)
public abstract class LevelBlockRewindMixin {
    @WrapMethod(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z")
    private boolean heroesevolved$recordBlockChange(BlockPos pos, BlockState state, int flags,
                                                  int recursion, Operation<Boolean> original) {
        if (!((Object) this instanceof ServerLevel level)) {
            return original.call(pos, state, flags, recursion);
        }
        TimeRewindBlocks.PendingChange change = TimeRewindBlocks.begin(level, pos, state);
        boolean changed = false;
        try {
            changed = original.call(pos, state, flags, recursion);
            return changed;
        } finally {
            TimeRewindBlocks.finish(change, changed);
        }
    }
}
