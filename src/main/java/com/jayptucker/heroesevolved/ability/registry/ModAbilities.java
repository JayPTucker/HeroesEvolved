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

public final class ModAbilities {
    public static final DeferredRegister<Ability> ABILITIES =
            DeferredRegister.create(AbilityRegistry.ABILITY_REGISTRY_KEY, HeroesEvolved.MOD_ID);

    public static final ResourceLocation REGENERATION_ID =
        ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "regeneration");

    public static final DeferredHolder<Ability, RegenerationAbility> REGENERATION =
        ABILITIES.register(REGENERATION_ID.getPath(), RegenerationAbility::new);
        
    private ModAbilities() {
    }

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
}