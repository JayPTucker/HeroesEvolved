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
            GLFW.GLFW_KEY_P,
            "key.categories.heroesevolved"
    );

    public static final KeyMapping ABILITY_1 = new KeyMapping(
            "key.heroesevolved.ability_1",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            "key.categories.heroesevolved"
    );

    public static final KeyMapping ABILITY_2 = new KeyMapping(
            "key.heroesevolved.ability_2",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            "key.categories.heroesevolved"
    );

    public static final KeyMapping ABILITY_3 = new KeyMapping(
            "key.heroesevolved.ability_3",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Z,
            "key.categories.heroesevolved"
    );

    public static final KeyMapping ABILITY_4 = new KeyMapping(
            "key.heroesevolved.ability_4",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_X,
            "key.categories.heroesevolved"
    );

    // V is unbound in vanilla; C and X are its hotbar save/load keys.
    public static final KeyMapping ABILITY_5 = new KeyMapping(
            "key.heroesevolved.ability_5",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            "key.categories.heroesevolved"
    );

    // Keys 6 to 8 start unbound: players give them a key in Controls and an
    // ability in the Powers menu.
    public static final KeyMapping ABILITY_6 = unbound("key.heroesevolved.ability_6");
    public static final KeyMapping ABILITY_7 = unbound("key.heroesevolved.ability_7");
    public static final KeyMapping ABILITY_8 = unbound("key.heroesevolved.ability_8");

    /** Hold to open the ability wheel; tap to recast the last ability. R is unbound in vanilla. */
    public static final KeyMapping ABILITY_WHEEL = new KeyMapping(
            "key.heroesevolved.ability_wheel",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            "key.categories.heroesevolved"
    );

    /** The eight ability keys, in order. */
    public static final KeyMapping[] ABILITY_KEYS = {
            ABILITY_1, ABILITY_2, ABILITY_3, ABILITY_4,
            ABILITY_5, ABILITY_6, ABILITY_7, ABILITY_8
    };

    // J is unbound in vanilla.
    public static final KeyMapping OPEN_JOURNAL = new KeyMapping(
            "key.heroesevolved.open_journal",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            "key.categories.heroesevolved"
    );

    /** Operators only; the server rejects anyone else's request. */
    public static final KeyMapping OPEN_ADMIN = new KeyMapping(
            "key.heroesevolved.open_admin",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            "key.categories.heroesevolved"
    );

    private ModKeyMappings() {
    }

    private static KeyMapping unbound(String name) {
        return new KeyMapping(name, InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(), "key.categories.heroesevolved");
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_POWERS);
        event.register(OPEN_ADMIN);
        event.register(OPEN_JOURNAL);
        event.register(ABILITY_1);
        event.register(ABILITY_2);
        event.register(ABILITY_3);
        event.register(ABILITY_4);
        event.register(ABILITY_5);
        event.register(ABILITY_6);
        event.register(ABILITY_7);
        event.register(ABILITY_8);
        event.register(ABILITY_WHEEL);
    }
}
