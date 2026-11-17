package com.jayptucker.heroesevolved.hazard;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * Two kinds of burning up. Both ignore ordinary armor (the suits work by their own rules, in {@link Suits}), and a death
 * by either breaks the body apart (RadiationDeaths). Neither names an attacker: a meltdown is nobody's blow, so it reaches
 * friends and players where PvP is off, like lava.
 */
public final class HazardDamage {
    /** The heat and radiation pouring off someone melting down: their near-field and their shock waves. */
    public static final ResourceKey<DamageType> MELTDOWN = key("meltdown");
    /** A speedster's own friction at speeds their clothes can't take. */
    public static final ResourceKey<DamageType> FRICTION = key("friction");

    private static ResourceKey<DamageType> key(String path) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, path));
    }

    public static DamageSource source(Level level, ResourceKey<DamageType> type) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type));
    }

    /** As above, crediting {@code by} for the death message (only where no PvP rule should apply, as with a villager). */
    public static DamageSource source(Level level, ResourceKey<DamageType> type, Entity by) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type), by);
    }

    private HazardDamage() {}
}
