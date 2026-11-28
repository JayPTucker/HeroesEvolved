package com.jayptucker.heroesevolved.trailer;

import com.jayptucker.heroesevolved.uitest.PreviewQuiet;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.DoubleUnaryOperator;

/**
 * Films a shot as 24 frames a second of video with motion blur. The game runs slowed down while it films (the driver
 * lowers the tick rate), so every rendered frame is captured, stamped with the film clock ({@link FilmCamera#now}) and
 * added into the video frame whose shutter it falls in: half of each frame's time (a 180-degree shutter), as a cine
 * camera blurs. A frame is written once the clock passes its shutter.
 *
 * <p>The playback speed can change through a shot: {@code speed} (of shot ticks) is 1 for real time and 0.35 for slow
 * motion, and each video frame covers {@code 20 / 24 * speed} ticks of game time.</p>
 *
 * <p>Frames go to {@code film/<shot>/00001.rgb} (raw RGB, top row first); {@code film/<shot>/shot.txt} records the frame count, the size and any
 * markers ({@link #mark}) as video frame numbers, which the edit and the score line up to. One frame a second is also
 * copied to {@code screenshots/} so the run's audit and a reviewer have stills to look at.</p>
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class FilmRecorder {
    public static final double FPS = 24, SHUTTER = .5;
    private static final double TICKS_PER_FRAME = 20 / FPS;

    private static String shot;
    private static Path folder;
    private static DoubleUnaryOperator speed = t -> 1;
    private static double start = Double.NaN, frameAt, shutterEnd, lastT;
    private static int width, height, inShutter, written, repeated, captured;
    private static int[] red, green, blue, last;
    private static final List<String> MARKS = new ArrayList<>();
    private static ThreadPoolExecutor io;

    private FilmRecorder() {}

    /** Starts filming {@code name} at the next frame, at {@code speed} (a function of ticks since the shot started). */
    public static void roll(String name, DoubleUnaryOperator playback) {
        Path game = Minecraft.getInstance().gameDirectory.toPath();
        shot = name;
        folder = game.resolve("film").resolve(name);
        try {
            Files.createDirectories(folder);
            try (var old = Files.list(folder)) {
                old.filter(p -> p.toString().endsWith(".png") || p.toString().endsWith(".rgb")).forEach(p -> p.toFile().delete());
            }
        } catch (IOException e) {
            PreviewQuiet.report("TRAILER FAIL could not prepare " + folder + ": " + e);
        }
        speed = playback;
        start = Double.NaN;
        inShutter = written = repeated = captured = 0;
        red = green = blue = last = null;
        MARKS.clear();
        if (io == null || io.isShutdown())
            io = new ThreadPoolExecutor(4, 4, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(12), new ThreadPoolExecutor.CallerRunsPolicy());
        System.out.println("TRAILER_ROLL " + name);
    }

    public static boolean rolling() { return shot != null; }

    /** Ticks of game time since the shot started filming (0 before its first frame). */
    public static double shotTime() { return Double.isNaN(start) ? 0 : FilmCamera.now() - start; }

    /** Notes the video frame being filmed now under {@code name} (an impact, a cut point) for the edit and the score. */
    public static void mark(String name) {
        if (shot == null) return;
        MARKS.add(name + " " + (written + 1));
        System.out.printf(Locale.ROOT, "TRAILER_MARK %s frame %d at shot tick %.2f%n", name, written + 1, shotTime());
    }

    /** Stops filming, writes the last frame and the shot's notes, and waits for every frame to reach the disk. */
    public static int cut() {
        if (shot == null) return 0;
        if (inShutter > 0) emit();
        String name = shot;
        shot = null;
        finishWrites();
        List<String> notes = new ArrayList<>();
        notes.add("frames " + written);
        notes.add("fps " + (int) FPS);
        notes.add("size " + width + " " + height);
        notes.add("captured " + captured);
        notes.add("repeated " + repeated);
        MARKS.forEach(m -> notes.add("mark " + m));
        try { Files.write(folder.resolve("shot.txt"), notes); }
        catch (IOException e) { PreviewQuiet.report("TRAILER FAIL could not write shot notes: " + e); }
        // Most video frames should blend several captures; a run where many only repeat the last one is too choppy to use.
        PreviewQuiet.check("TRAILER", written > 0 && repeated <= written / 4,
                name + ": " + written + " frames from " + captured + " captures, " + repeated + " repeated, " + width + "x" + height);
        return written;
    }

    private static void finishWrites() {
        if (io == null) return;
        io.shutdown();
        try {
            if (!io.awaitTermination(120, TimeUnit.SECONDS)) PreviewQuiet.report("TRAILER FAIL frames still writing after two minutes");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        io = null;
    }

    @SubscribeEvent public static void frame(RenderFrameEvent.Post event) {
        if (shot == null) return;
        Minecraft mc = Minecraft.getInstance();
        double clock = FilmCamera.now(event.getPartialTick().getGameTimeDeltaPartialTick(true));
        if (Double.isNaN(start)) {
            start = clock;
            frameAt = 0;
            shutterEnd = SHUTTER * TICKS_PER_FRAME * speed.applyAsDouble(0);
        }
        double t = clock - start;
        int[] pixels;
        try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            if (red == null || image.getWidth() != width || image.getHeight() != height) {
                width = image.getWidth();
                height = image.getHeight();
                red = new int[width * height];
                green = new int[width * height];
                blue = new int[width * height];
                inShutter = 0;
            }
            pixels = image.getPixelsRGBA();
        }
        captured++;
        // Every video frame whose shutter has closed before this capture is finished now.
        if (t >= shutterEnd && inShutter == 0)
            System.out.printf(Locale.ROOT, "TRAILER_REPEAT frame %d: the clock jumped from %.3f to %.3f (frame %.3f to %.3f)%n",
                    written + 1, lastT, t, frameAt, shutterEnd);
        lastT = t;
        while (t >= shutterEnd) {
            emit();
            double step = TICKS_PER_FRAME * Math.max(.02, speed.applyAsDouble(frameAt));
            frameAt += step;
            shutterEnd = frameAt + SHUTTER * step;
        }
        if (t >= frameAt) add(pixels);
        last = pixels;
    }

    private static void add(int[] pixels) {
        for (int i = 0; i < pixels.length; i++) {
            int v = pixels[i];
            red[i] += v & 0xFF;
            green[i] += v >>> 8 & 0xFF;
            blue[i] += v >>> 16 & 0xFF;
        }
        inShutter++;
    }

    /** Writes the frame whose shutter just closed: the average of its captures, or the last capture if none fell in it. */
    private static void emit() {
        int[] out = new int[width * height];
        if (inShutter > 0) {
            int n = inShutter, half = n / 2;
            for (int i = 0; i < out.length; i++)
                out[i] = 0xFF000000 | (blue[i] + half) / n << 16 | (green[i] + half) / n << 8 | (red[i] + half) / n;
            java.util.Arrays.fill(red, 0);
            java.util.Arrays.fill(green, 0);
            java.util.Arrays.fill(blue, 0);
        } else if (last != null) {
            for (int i = 0; i < out.length; i++) out[i] = 0xFF000000 | last[i];
            repeated++;
        } else return;
        inShutter = 0;
        int number = ++written, w = width, h = height;
        Path file = folder.resolve(String.format(Locale.ROOT, "%05d.rgb", number));
        Path still = number % (int) FPS == 1
                ? Minecraft.getInstance().gameDirectory.toPath().resolve("screenshots").resolve(shot + "-" + String.format(Locale.ROOT, "%05d.png", number))
                : null;
        io.execute(() -> write(out, w, h, file, still));
    }

    /**
     * Writes a frame as raw 8-bit RGB, top row first: a plain file write, where encoding a PNG took long enough that the
     * render thread ended up doing it whenever the writers fell behind, stalling the frame and making the clock jump.
     */
    private static void write(int[] pixels, int w, int h, Path file, Path still) {
        byte[] rgb = new byte[w * h * 3];
        for (int i = 0, o = 0; i < pixels.length; i++) {
            int v = pixels[i];
            rgb[o++] = (byte) v;
            rgb[o++] = (byte) (v >>> 8);
            rgb[o++] = (byte) (v >>> 16);
        }
        try {
            Files.write(file, rgb);
            if (still != null) {
                Files.createDirectories(still.getParent());
                try (NativeImage image = new NativeImage(w, h, false)) {
                    for (int y = 0, i = 0; y < h; y++)
                        for (int x = 0; x < w; x++, i++) image.setPixelRGBA(x, y, pixels[i]);
                    image.writeToFile(still);
                }
            }
        } catch (IOException | RuntimeException e) {
            PreviewQuiet.report("TRAILER FAIL could not write " + file.getFileName() + ": " + e);
        }
    }
}
