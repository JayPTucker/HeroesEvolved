package com.jayptucker.heroesevolved.input;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(
        modid = HeroesEvolved.MOD_ID,
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD
)
public final class ModKeyMappings {
    /**
     * Where our keys count: in the world, and in our own hold-to-open wheels, whose screens watch the key that opened
     * them. Never in any other screen, so NeoForge never hands R (the wheel) or O (admin) to us while JEI's R (show
     * recipe) or Ctrl+O (hide the list), or another mod's menu key, is in use. The Controls screen still marks two
     * bindings on one key red whatever their contexts; that is only a warning.
     */
    public static final IKeyConflictContext PLAY = new IKeyConflictContext() {
        @Override public boolean isActive() {
            var screen = net.minecraft.client.Minecraft.getInstance().screen;
            return screen == null || screen instanceof com.jayptucker.heroesevolved.gui.screen.AbilityWheelScreen
                    || screen instanceof com.jayptucker.heroesevolved.gui.screen.MemoryWheelScreen;
        }

        @Override public boolean conflicts(IKeyConflictContext other) {
            return other == this || other == KeyConflictContext.IN_GAME || other == KeyConflictContext.UNIVERSAL;
        }
    };

    public static final KeyMapping OPEN_POWERS = new KeyMapping(
            "key.heroesevolved.open_powers",
            PLAY,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_P,
            "key.categories.heroesevolved"
    );

    public static final KeyMapping ABILITY_1 = new KeyMapping(
            "key.heroesevolved.ability_1",
            PLAY,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            "key.categories.heroesevolved"
    );

    public static final KeyMapping ABILITY_2 = new KeyMapping(
            "key.heroesevolved.ability_2",
            PLAY,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            "key.categories.heroesevolved"
    );

    public static final KeyMapping ABILITY_3 = new KeyMapping(
            "key.heroesevolved.ability_3",
            PLAY,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Z,
            "key.categories.heroesevolved"
    );

    public static final KeyMapping ABILITY_4 = new KeyMapping(
            "key.heroesevolved.ability_4",
            PLAY,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_X,
            "key.categories.heroesevolved"
    );

    // V is unbound in vanilla; C and X are its hotbar save/load keys.
    public static final KeyMapping ABILITY_5 = new KeyMapping(
            "key.heroesevolved.ability_5",
            PLAY,
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
            PLAY,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            "key.categories.heroesevolved"
    );

    /**
     * Empathic Mimicry's memory wheel: hold to choose which remembered power answers; tap to step to the next.
     * B is unbound in vanilla.
     */
    public static final KeyMapping MEMORY_WHEEL = new KeyMapping(
            "key.heroesevolved.memory_wheel",
            PLAY,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
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
            PLAY,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            "key.categories.heroesevolved"
    );

    /** Operators only; the server rejects anyone else's request. */
    public static final KeyMapping OPEN_ADMIN = new KeyMapping(
            "key.heroesevolved.open_admin",
            PLAY,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            "key.categories.heroesevolved"
    );

    /**
     * Finish a glistening foe; Shift + the key switches finishers on or off. C is vanilla's "save hotbar" key, which
     * only does anything in creative with a number key held, so the two rarely meet.
     */
    public static final KeyMapping FINISHER = new KeyMapping(
            "key.heroesevolved.finisher",
            PLAY,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_C,
            "key.categories.heroesevolved"
    );

    private ModKeyMappings() {
    }

    private static KeyMapping unbound(String name) {
        return new KeyMapping(name, PLAY, InputConstants.Type.KEYSYM,
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
        event.register(MEMORY_WHEEL);
        event.register(FINISHER);
    }
}
