package com.jayptucker.heroesevolved.mixin;

import com.jayptucker.heroesevolved.grid.GridDisruption;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Create compatibility without a dependency: when Create is installed, its kinetic blocks inside an
 * electromagnetic pulse report zero speed, so machines stop working and shafts stop turning. Without
 * Create the target class never loads and this does nothing; {@code require = 0} keeps a future Create
 * rename from stopping the game.
 */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.kinetics.base.KineticBlockEntity", remap = false)
public abstract class CreateKineticSeizeMixin {
    @Inject(method = "getSpeed", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void heroesevolved$seize(CallbackInfoReturnable<Float> result) {
        if ((Object) this instanceof BlockEntity entity && GridDisruption.seized(entity.getLevel(), entity.getBlockPos()))
            result.setReturnValue(0.0F);
    }
}
