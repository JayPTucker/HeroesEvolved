package com.jayptucker.heroesevolved;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

import com.jayptucker.heroesevolved.ability.registry.AbilityRegistry;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;

@Mod(HeroesEvolved.MOD_ID)
public final class HeroesEvolved {
    public static final String MOD_ID = "heroesevolved";
    public static final Logger LOGGER = LogUtils.getLogger();

    public HeroesEvolved(IEventBus modEventBus) {

        AbilityRegistry.register(modEventBus);
        ModAbilities.register(modEventBus);
        ModDataAttachments.register(modEventBus);
        
        LOGGER.info("Heroes Evolved initialized.");
    }
}
