package com.jayptucker.heroesevolved.mixin;

import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A creeper's fuse, so an awakening can hold it or (Space-Time) run it backwards. */
@Mixin(Creeper.class)
public interface CreeperSwellAccess {
    @Accessor("swell") int heroesEvolved$swell();
    @Accessor("swell") void heroesEvolved$setSwell(int swell);
    @Accessor("oldSwell") void heroesEvolved$setOldSwell(int swell);
    @Accessor("maxSwell") int heroesEvolved$maxSwell();
    @Accessor("explosionRadius") int heroesEvolved$explosionRadius();
}
