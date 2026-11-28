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
 *
 * <p>It also gives every run an honest result ({@code tools/preview-result.gradle} reads it): the driver's own
 * {@link #report}/{@link #check} lines, any step {@link #guard} caught, and an {@link #audit} of the run itself (no crash
 * report, photographs written, not too many blank, the player never below the world).</p>
 *
 * <p>And it stages every preview's player in the overworld: a new player is never sent into the Last Day's prologue
 * ({@link #joined}), unless the preview is of the Last Day.</p>
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class PreviewQuiet {
    private static Boolean previewing;
    /** The file in the game directory that says how a run ended (see {@code tools/preview-result.gradle}). */
    public static final String RESULT = "preview-result.txt";
    private static final java.util.List<String> REPORT = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

    /** When this run began: photographs and crash reports older than this belong to an earlier run. */
    private static final long STARTED = System.currentTimeMillis();
    /** Previews that photograph the Last Day itself, and so keep a new player's prologue. */
    private static final java.util.Set<String> KEEP_PROLOGUE = java.util.Set.of("heroes.preview.prologue");
    /** Preview players already reported as fallen below the world (once each). */
    private static final java.util.Set<java.util.UUID> FELL = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Whether any preview driver is running (whatever its sound setting). */
    static boolean previewRun() {
        return System.getProperties().stringPropertyNames().stream()
                .anyMatch(key -> key.startsWith("heroes.preview.") && Boolean.getBoolean(key) && !key.equals("heroes.preview.sound"));
    }

    /**
     * A preview's player is never sent into the Last Day (unless the preview is of the Last Day). A new player normally
     * lives the prologue first, in its own dimension behind a black hold; a driver that then teleports "within the
     * player's level" put them at y=-60 under the prologue city. Marking them as having slept through it at login, before
     * the bunker decides (it waits a moment after login), wakes them in the bunker in the overworld instead.
     */
    @SubscribeEvent public static void joined(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) || !previewRun()) return;
        if (KEEP_PROLOGUE.stream().anyMatch(Boolean::getBoolean)) return;
        player.getPersistentData().putBoolean(com.jayptucker.heroesevolved.story.prologue.PrologueService.SLEPT, true);
        System.out.println("PREVIEW_STAGE " + player.getName().getString() + " skips the Last Day: wakes in the bunker, in the overworld");
    }

    /** A preview player below the world is photographing the void: that fails the run, whatever the driver checks. */
    @SubscribeEvent public static void fell(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) || player.tickCount % 20 != 0 || !previewRun()) return;
        if (player.getY() < player.level().getMinBuildHeight() && FELL.add(player.getUUID()))
            report("PREVIEW_STAGE FAIL " + player.getName().getString() + " is below the world in "
                    + player.level().dimension().location() + " at " + player.blockPosition());
    }
    /** More than this share of blank photographs fails the run (one deliberate blackout shot is fine). */
    private static final double BLANK_SHARE = 1 / 3.0;

    /**
     * Prints a line of a driver's own report and keeps it for the result file. A line containing {@code FAIL} fails the
     * Gradle run once the preview closes.
     */
    public static void report(String line) {
        System.out.println(line);
        REPORT.add(line);
    }

    /** A driver's check: reports {@code <prefix> PASS what} or {@code <prefix> FAIL what}, so a miss fails the run. */
    public static void check(String prefix, boolean ok, String what) {
        report(prefix + (ok ? " PASS " : " FAIL ") + what);
    }

    /**
     * Runs one step of a scene and reports a throw as a failure instead of letting it crash the integrated server, so one
     * broken scene is named and the rest still run.
     */
    public static void guard(String scene, Runnable step) {
        try { step.run(); }
        catch (RuntimeException | AssertionError e) {
            report("PREVIEW_STEP FAIL " + scene + ": " + e);
            e.printStackTrace();
        }
    }

    /**
     * What every run is checked for, whatever its driver checks itself: no crash report written during the run, at least
     * one photograph, and not too many blank ones (a black or one-colour frame is the dark, the void or an unloaded sky).
     */
    static java.util.List<String> audit(java.nio.file.Path game) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        try (var crashes = java.nio.file.Files.exists(game.resolve("crash-reports"))
                ? java.nio.file.Files.list(game.resolve("crash-reports")) : java.util.stream.Stream.<java.nio.file.Path>empty()) {
            crashes.filter(p -> newer(p)).forEach(p -> lines.add("PREVIEW_AUDIT FAIL crash report written: " + p.getFileName()));
        } catch (java.io.IOException e) {
            lines.add("PREVIEW_AUDIT could not read crash-reports: " + e);
        }
        java.nio.file.Path shots = game.resolve("screenshots");
        java.util.List<java.nio.file.Path> taken = new java.util.ArrayList<>();
        if (java.nio.file.Files.isDirectory(shots)) {
            try (var walk = java.nio.file.Files.walk(shots)) {
                walk.filter(p -> p.toString().endsWith(".png") && newer(p)).forEach(taken::add);
            } catch (java.io.IOException e) {
                lines.add("PREVIEW_AUDIT could not read screenshots: " + e);
            }
        }
        if (taken.isEmpty()) {
            lines.add("PREVIEW_AUDIT FAIL no photographs were written to " + shots);
            return lines;
        }
        int blank = 0;
        for (java.nio.file.Path shot : taken) {
            Boolean empty = blank(shot);
            if (empty == null) lines.add("PREVIEW_AUDIT note: could not read " + shot.getFileName() + " (still being written?)");
            else if (empty) {
                blank++;
                lines.add("PREVIEW_AUDIT blank photograph: " + shots.relativize(shot));
            }
        }
        boolean tooMany = blank > taken.size() * BLANK_SHARE;
        lines.add("PREVIEW_AUDIT " + (tooMany ? "FAIL " : "PASS ") + taken.size() + " photographs, " + blank + " blank");
        return lines;
    }

    private static boolean newer(java.nio.file.Path p) {
        try { return java.nio.file.Files.getLastModifiedTime(p).toMillis() >= STARTED - 2000; }
        catch (java.io.IOException e) { return false; }
    }

    /**
     * Whether a photograph is one flat colour: sampled on a grid, every pixel's brightness within a few steps of the
     * rest. Null if it could not be read.
     */
    static Boolean blank(java.nio.file.Path shot) {
        try (var in = java.nio.file.Files.newInputStream(shot);
             var image = com.mojang.blaze3d.platform.NativeImage.read(in)) {
            int w = image.getWidth(), h = image.getHeight(), low = 255, high = 0;
            for (int y = 0; y < 24; y++)
                for (int x = 0; x < 32; x++) {
                    int abgr = image.getPixelRGBA(x * (w - 1) / 31, y * (h - 1) / 23);
                    int r = abgr & 0xFF, g = abgr >> 8 & 0xFF, b = abgr >> 16 & 0xFF;
                    int lum = (r * 3 + g * 6 + b) / 10;
                    low = Math.min(low, lum);
                    high = Math.max(high, lum);
                }
            return high - low < 8;
        } catch (Exception e) {
            return null;
        }
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
            var game = Minecraft.getInstance().gameDirectory.toPath();
            var lines = new java.util.ArrayList<>(REPORT);
            for (String line : audit(game)) {
                System.out.println(line);
                lines.add(line);
            }
            lines.add("PREVIEW_DONE");
            java.nio.file.Files.write(game.resolve(RESULT), lines);
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
