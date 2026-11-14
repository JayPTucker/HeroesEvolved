package com.jayptucker.heroesevolved.time;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.config.HeroesEvolvedConfig;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Game tests run side by side, so a server-wide Still World in one test would freeze every other test's mobs. The test
 * server keeps time fields to their configured area; {@code TimeVeilGameTests} switches server-wide on for its own batch.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class TimeFieldTestSupport {
    private TimeFieldTestSupport() {}

    @SubscribeEvent(priority = EventPriority.LOWEST) public static void start(ServerStartedEvent event) { serverWide(false); }

    static void serverWide(boolean on) { HeroesEvolvedConfig.COMMON.timeFieldsServerWide.set(on); }
}
