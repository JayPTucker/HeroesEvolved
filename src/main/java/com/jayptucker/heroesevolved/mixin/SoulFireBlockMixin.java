package com.jayptucker.heroesevolved.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoulFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoulFireBlock.class)
public abstract class SoulFireBlockMixin {
    @Inject(method = "canSurvive", at = @At("RETURN"), cancellable = true)
    private void heroes$keepPlacedSoulFire(BlockState state, LevelReader level, BlockPos pos,
                                          CallbackInfoReturnable<Boolean> result) {
        // Flint and steel still picks its fire from the substrate. Soul Fire already placed
        // by a power can also stay anywhere that would support regular fire.
        if (!result.getReturnValueZ() && level.getBlockState(pos).is(Blocks.SOUL_FIRE)
                && Blocks.FIRE.defaultBlockState().canSurvive(level, pos)) result.setReturnValue(true);
    }
}
