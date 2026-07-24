package com.jayptucker.heroesevolved.ability.registry;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.Ability;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModAbilities {
    public static final DeferredRegister<Ability> ABILITIES =
            DeferredRegister.create(AbilityRegistry.ABILITY_REGISTRY_KEY, HeroesEvolved.MOD_ID);

    private ModAbilities() {
    }

    public static void register(IEventBus modEventBus) {
        ABILITIES.register(modEventBus);
    }
}