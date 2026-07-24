package com.jayptucker.heroesevolved.ability.registry;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.Ability;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.neoforged.neoforge.registries.RegistryBuilder;

public final class AbilityRegistry {
    public static final ResourceKey<Registry<Ability>> ABILITY_REGISTRY_KEY = 
        ResourceKey.createRegistryKey(
            ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "abilities")
        );

    public static final Registry<Ability> ABILITIES = new RegistryBuilder<>(ABILITY_REGISTRY_KEY)
        .sync(true)
        .create();

    private AbilityRegistry() {

    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(AbilityRegistry::registerRegistry);
    }

    private static void registerRegistry(NewRegistryEvent event) {
        event.register(ABILITIES);
    }
}
