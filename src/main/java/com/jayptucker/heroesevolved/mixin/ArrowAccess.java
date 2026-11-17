package com.jayptucker.heroesevolved.mixin;

import net.minecraft.world.entity.projectile.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractArrow.class)
public interface ArrowAccess {
    @Accessor("inGround") boolean heroes$inGround();
}
