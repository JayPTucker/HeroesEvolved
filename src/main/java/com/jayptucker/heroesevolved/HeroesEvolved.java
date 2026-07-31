package com.jayptucker.heroesevolved;

import com.mojang.logging.LogUtils;

import com.jayptucker.heroesevolved.sounds.ModSounds;
import com.jayptucker.heroesevolved.particles.ModParticles;
import com.jayptucker.heroesevolved.registry.ModEntities;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

import com.jayptucker.heroesevolved.ability.registry.AbilityRegistry;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;

import com.jayptucker.heroesevolved.config.HeroesEvolvedConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;

@Mod(HeroesEvolved.MOD_ID)
public final class HeroesEvolved {
    public static final String MOD_ID = "heroesevolved";
    public static final Logger LOGGER = LogUtils.getLogger();

    public HeroesEvolved(IEventBus modEventBus, ModContainer modContainer) {

        modContainer.registerConfig(ModConfig.Type.COMMON, HeroesEvolvedConfig.COMMON_SPEC);
        AbilityRegistry.register(modEventBus);
        ModAbilities.register(modEventBus);
        ModDataAttachments.register(modEventBus);
        ModSounds.register(modEventBus);
        ModParticles.register(modEventBus);
        ModEntities.register(modEventBus);

        LOGGER.info("Heroes Evolved initialized.");
    }
}
