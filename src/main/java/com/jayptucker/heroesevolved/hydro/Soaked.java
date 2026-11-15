package com.jayptucker.heroesevolved.hydro;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Drenched by a hydrokinetic's water. A soaked creature can't stay on fire, takes half again as much from lightning
 * (Electrokinesis), shrugs off part of every burn as steam (Pyrokinesis), and freezes solid under Ice Daggers once Brine
 * Edge is learned. It wears off like getting dry; milk doesn't help.
 */
public final class Soaked extends MobEffect {
    public Soaked() { super(MobEffectCategory.NEUTRAL, 0x3CC2D6); }

    @Override public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) { return duration % 5 == 0; }

    @Override public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.isOnFire()) entity.clearFire();
        return true;
    }

    @Override public void fillEffectCures(java.util.Set<net.neoforged.neoforge.common.EffectCure> cures,
                                          net.minecraft.world.effect.MobEffectInstance instance) {}
}
