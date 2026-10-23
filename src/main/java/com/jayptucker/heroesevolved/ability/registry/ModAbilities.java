package com.jayptucker.heroesevolved.ability.registry;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.Ability;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import com.jayptucker.heroesevolved.ability.types.RegenerationAbility;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredHolder;

import com.jayptucker.heroesevolved.ability.types.FlightAbility;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.minecraft.resources.ResourceLocation;
import com.jayptucker.heroesevolved.ability.types.TimeManipulationAbility;
import com.jayptucker.heroesevolved.ability.types.QuakeAbility;

public final class ModAbilities {
    public static final DeferredRegister<Ability> ABILITIES =
            DeferredRegister.create(AbilityRegistry.ABILITY_REGISTRY_KEY, HeroesEvolved.MOD_ID);

    public static final ResourceLocation REGENERATION_ID =
        ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "regeneration");

    public static final DeferredHolder<Ability, RegenerationAbility> REGENERATION =
        ABILITIES.register(REGENERATION_ID.getPath(), RegenerationAbility::new);
        
    private ModAbilities() {
    }

    // FLIGHT
    public static final ResourceLocation FLIGHT_ID =
        ResourceLocation.fromNamespaceAndPath(
                HeroesEvolved.MOD_ID,
                "flight"
        );

    public static final DeferredHolder<Ability, FlightAbility> FLIGHT =
            ABILITIES.register(FLIGHT_ID.getPath(), FlightAbility::new);

    public static void register(IEventBus modEventBus) {
        ABILITIES.register(modEventBus);
    }

    // TIME MANIPULATION
    public static final ResourceLocation TIME_MANIPULATION_ID =
        ResourceLocation.fromNamespaceAndPath(
                HeroesEvolved.MOD_ID,
                "time_manipulation"
        );

    public static final DeferredHolder<
            Ability,
            TimeManipulationAbility
    > TIME_MANIPULATION = ABILITIES.register(
            TIME_MANIPULATION_ID.getPath(),
            TimeManipulationAbility::new
    );

    // QUAKE
    public static final ResourceLocation QUAKE_ID =
        ResourceLocation.fromNamespaceAndPath(
                HeroesEvolved.MOD_ID,
                "quake"
        );

    public static final DeferredHolder<Ability, QuakeAbility> QUAKE =
            ABILITIES.register(QUAKE_ID.getPath(), QuakeAbility::new);

    // ELECTROKINESIS
    public static final ResourceLocation ELECTROKINESIS_ID =
        ResourceLocation.fromNamespaceAndPath(
                HeroesEvolved.MOD_ID,
                "electrokinesis"
        );

    public static final DeferredHolder<
            Ability,
            com.jayptucker.heroesevolved.ability.types.ElectrokinesisAbility
    > ELECTROKINESIS = ABILITIES.register(
            ELECTROKINESIS_ID.getPath(),
            com.jayptucker.heroesevolved.ability.types.ElectrokinesisAbility::new
    );

    // TELEKINESIS
    public static final ResourceLocation TELEKINESIS_ID =
        ResourceLocation.fromNamespaceAndPath(
                HeroesEvolved.MOD_ID,
                "telekinesis"
        );

    public static final DeferredHolder<
            Ability,
            com.jayptucker.heroesevolved.ability.types.TelekinesisAbility
    > TELEKINESIS = ABILITIES.register(
            TELEKINESIS_ID.getPath(),
            com.jayptucker.heroesevolved.ability.types.TelekinesisAbility::new
    );
}