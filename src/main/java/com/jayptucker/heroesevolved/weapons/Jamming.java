package com.jayptucker.heroesevolved.weapons;

import com.jayptucker.heroesevolved.registry.ModItems;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.EffectCure;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * A gun frying in someone's hand (Electrokinesis's Dead Trigger, {@code electro/ElectroMachines}). The one question "is
 * this gun jammed": while a body has the {@link ModItems#JAMMED} effect its TaCZ gun won't fire or reload
 * ({@link TaczLink} cancels the shot, fire and reload events on both sides), and a Company guard puts it down and swings
 * it instead. A real effect, so it syncs to the jammed player's own client, shows an icon and refreshes rather than
 * stacking.
 */
public final class Jamming {
    private Jamming() {}

    public static boolean jammed(@Nullable Entity entity) { return entity instanceof LivingEntity living && living.hasEffect(ModItems.JAMMED); }

    /** Whether {@code body} holds a TaCZ gun in either hand. */
    public static boolean holdsGun(LivingEntity body) {
        return PowerWeapons.gunId(body.getMainHandItem()) != null || PowerWeapons.gunId(body.getOffhandItem()) != null;
    }

    /** Jams the gun {@code target} holds for {@code ticks}. False (and nothing happens) when they hold none. */
    public static boolean jam(LivingEntity target, int ticks, @Nullable Entity source) {
        if (!holdsGun(target)) return false;
        target.addEffect(new MobEffectInstance(ModItems.JAMMED, ticks, 0, false, true, true), source);
        return true;
    }

    public static final class Effect extends MobEffect {
        public Effect() { super(MobEffectCategory.HARMFUL, 0x5588FF); }

        // Milk doesn't unfry a trigger; it wears off in seconds.
        @Override public void fillEffectCures(Set<EffectCure> cures, MobEffectInstance instance) {}
    }
}
