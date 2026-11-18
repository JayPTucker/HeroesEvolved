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
    /** The file in the game directory that says how a run ended (see {@code tools/preview-result.gradle}). */
    public static final String RESULT = "preview-result.txt";
    private static final java.util.List<String> REPORT = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

    /**
     * Prints a line of a driver's own report and keeps it for the result file. A line containing {@code FAIL} fails the
     * Gradle run once the preview closes.
     */
    public static void report(String line) {
        System.out.println(line);
        REPORT.add(line);
    }

    /**
     * Ends a preview run once its photographs are written: closes the game at once, without saving its world. A preview's
     * world is thrown away, and saving it is slow: a normal {@code Minecraft.stop()} first saves every chunk Odessa and the
     * bunker touched, which left the window black for minutes before it closed.
     */
    public static void quit() {
        System.out.println("PREVIEW_DONE");
        System.out.flush();
        System.err.flush();
        // The halt below skips the log's last flush, so the run's result goes in a file of its own, which
        // tools/preview-result.gradle reads to pass or fail the Gradle run.
        try {
            var lines = new java.util.ArrayList<>(REPORT);
            lines.add("PREVIEW_DONE");
            java.nio.file.Files.write(Minecraft.getInstance().gameDirectory.toPath().resolve(RESULT), lines);
        } catch (java.io.IOException e) {
            System.out.println("PREVIEW could not write its result: " + e);
        }
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
