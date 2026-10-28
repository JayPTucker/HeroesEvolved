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

    public static final ResourceLocation SONIC_RESONANCE_ID = ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "sonic_resonance");
    public static final DeferredHolder<Ability, com.jayptucker.heroesevolved.ability.types.SonicResonanceAbility> SONIC_RESONANCE =
            ABILITIES.register(SONIC_RESONANCE_ID.getPath(), com.jayptucker.heroesevolved.ability.types.SonicResonanceAbility::new);

    public static final ResourceLocation SUPERSPEED_ID = ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "superspeed");
    public static final DeferredHolder<Ability, com.jayptucker.heroesevolved.ability.types.SuperspeedAbility> SUPERSPEED =
            ABILITIES.register(SUPERSPEED_ID.getPath(), com.jayptucker.heroesevolved.ability.types.SuperspeedAbility::new);

    public static final ResourceLocation PRECOGNITION_ID = ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "precognition");
    public static final DeferredHolder<Ability, com.jayptucker.heroesevolved.ability.types.PrecognitionAbility> PRECOGNITION =
            ABILITIES.register("precognition", com.jayptucker.heroesevolved.ability.types.PrecognitionAbility::new);

    public static final ResourceLocation INVISIBILITY_ID = ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "invisibility");
    public static final DeferredHolder<Ability, com.jayptucker.heroesevolved.ability.types.InvisibilityAbility> INVISIBILITY =
            ABILITIES.register(INVISIBILITY_ID.getPath(), com.jayptucker.heroesevolved.ability.types.InvisibilityAbility::new);
    public static final ResourceLocation PHASING_ID = ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "phasing");
    public static final DeferredHolder<Ability, com.jayptucker.heroesevolved.ability.types.PhasingAbility> PHASING =
            ABILITIES.register(PHASING_ID.getPath(), com.jayptucker.heroesevolved.ability.types.PhasingAbility::new);
    public static final ResourceLocation MIMICRY_ID = ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "empathic_mimicry");
    public static final DeferredHolder<Ability, com.jayptucker.heroesevolved.ability.types.MimicryAbility> MIMICRY =
            ABILITIES.register(MIMICRY_ID.getPath(), com.jayptucker.heroesevolved.ability.types.MimicryAbility::new);

    public static final ResourceLocation PYROKINESIS_ID = ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "pyrokinesis");
    public static final DeferredHolder<Ability, com.jayptucker.heroesevolved.ability.types.PyrokinesisAbility> PYROKINESIS =
            ABILITIES.register(PYROKINESIS_ID.getPath(), com.jayptucker.heroesevolved.ability.types.PyrokinesisAbility::new);

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

    // TELEPATHY
    public static final ResourceLocation TELEPATHY_ID =
        ResourceLocation.fromNamespaceAndPath(
                HeroesEvolved.MOD_ID,
                "telepathy"
        );

    public static final DeferredHolder<
            Ability,
            com.jayptucker.heroesevolved.ability.types.TelepathyAbility
    > TELEPATHY = ABILITIES.register(
            TELEPATHY_ID.getPath(),
            com.jayptucker.heroesevolved.ability.types.TelepathyAbility::new
    );

    // CRYOKINESIS
    public static final ResourceLocation CRYOKINESIS_ID =
        ResourceLocation.fromNamespaceAndPath(
                HeroesEvolved.MOD_ID,
                "cryokinesis"
        );

    public static final DeferredHolder<
            Ability,
            com.jayptucker.heroesevolved.ability.types.CryokinesisAbility
    > CRYOKINESIS = ABILITIES.register(
            CRYOKINESIS_ID.getPath(),
            com.jayptucker.heroesevolved.ability.types.CryokinesisAbility::new
    );

    // RADIATION (the Glowing Man)
    public static final ResourceLocation RADIATION_ID =
        ResourceLocation.fromNamespaceAndPath(
                HeroesEvolved.MOD_ID,
                "radiation"
        );

    public static final DeferredHolder<
            Ability,
            com.jayptucker.heroesevolved.ability.types.RadiationAbility
    > RADIATION = ABILITIES.register(
            RADIATION_ID.getPath(),
            com.jayptucker.heroesevolved.ability.types.RadiationAbility::new
    );
}