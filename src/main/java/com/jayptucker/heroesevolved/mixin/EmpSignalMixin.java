package com.jayptucker.heroesevolved.mixin;

import com.jayptucker.heroesevolved.radiation.RadiationEmp;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class EmpSignalMixin {
    @Inject(method = {"getSignal", "getDirectSignal"}, at = @At("HEAD"), cancellable = true)
    private void heroesevolved$emp(BlockGetter world, BlockPos pos, Direction direction, CallbackInfoReturnable<Integer> result) {
        if (world instanceof ServerLevel level && RadiationEmp.jammed(level, pos)) result.setReturnValue(0);
    }
}
