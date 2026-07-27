package com.jayptucker.heroesevolved.input;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(
    modid = HeroesEvolved.MOD_ID,
    value = Dist.CLIENT,
    bus = EventBusSubscriber.Bus.MOD
)

public final class ModKeyMappings {
    public static final KeyMapping OPEN_POWERS = new KeyMapping(
        "key.heroesevolved.open_powers",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_H,
        "key.categories.heroesevolved"
    );

    private ModKeyMappings() {

    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_POWERS);
    }
}
