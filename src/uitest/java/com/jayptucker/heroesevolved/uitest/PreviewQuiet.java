package com.jayptucker.heroesevolved.uitest;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Every preview runs silent: sirens, alarms and the rest of the game's sound are muted while a preview driver (any
 * {@code heroes.preview.*} flag) takes its photographs, so a preview never sounds off on the machine it runs on.
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class PreviewQuiet {
    private static Boolean previewing;

    /**
     * Ends a preview run once its photographs are written: closes the game at once, without saving its world. A preview's
     * world is thrown away, and saving it is slow: a normal {@code Minecraft.stop()} first saves every chunk Odessa and the
     * bunker touched, which left the window black for minutes before it closed.
     */
    public static void quit() {
        System.out.println("PREVIEW_DONE");
        System.out.flush();
        System.err.flush();
        // End the process without the shutdown that saves the world (vanilla turns saving back on as it stops, and a
        // System.exit runs the client's shutdown hook, which stops the server the same way). On Windows a halt still ends
        // with a fail-fast exit code from some native thread, so Gradle marks the run failed: the photos are all written
        // by then; look for PREVIEW_DONE in the log.
        var mc = Minecraft.getInstance();
        mc.getSoundManager().destroy();
        mc.getWindow().close();
        Runtime.getRuntime().halt(0);
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Pre e) {
        if (previewing == null)
            previewing = System.getProperties().stringPropertyNames().stream()
                    .anyMatch(key -> key.startsWith("heroes.preview.") && Boolean.getBoolean(key))
                    // A preview someone is sitting and watching can opt back in to sound (-Psound).
                    && !Boolean.getBoolean("heroes.preview.sound");
        if (!previewing) return;
        var volume = Minecraft.getInstance().options.getSoundSourceOptionInstance(SoundSource.MASTER);
        if (volume.get() != 0.0) volume.set(0.0);
    }
}
