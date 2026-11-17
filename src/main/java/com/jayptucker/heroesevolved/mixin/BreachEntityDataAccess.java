package com.jayptucker.heroesevolved.mixin;

import net.minecraft.network.syncher.SynchedEntityData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SynchedEntityData.class)
public interface BreachEntityDataAccess {
    @Accessor("itemsById") SynchedEntityData.DataItem<?>[] heroes$itemsById();
}
