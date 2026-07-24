package com.jayptucker.heroesevolved;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(HeroesEvolved.MOD_ID)
public final class HeroesEvolved {
    public static final String MOD_ID = "heroesevolved";
    public static final Logger LOGGER = LogUtils.getLogger();

    public HeroesEvolved(IEventBus modEventBus) {
        LOGGER.info("Heroes Evolved initialized.");
    }
}
