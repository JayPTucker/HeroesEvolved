package com.jayptucker.heroesevolved.mixin;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(EnderDragon.class)
public interface EndDragonAccess {
    @Invoker("reallyHurt") boolean heroes$reallyHurt(DamageSource source,float amount);
}
