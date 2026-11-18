package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.network.VibeVisionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * How a Vibe vision looks to the viber ({@code vibe/VibeVisions}), after the show: the view starts to flicker and blur,
 * then they are elsewhere, in a washed-out, cold blue world with soft bright edges, drifting streaks and the odd
 * stutter, and at the end it flickers back. The blur and the blue grade are a post chain
 * ({@code shaders/post/vibe_vision.json}, vanilla's box blur and colour convolve); the rest is drawn over the HUD.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class VibeVisionClient {
    private static final ResourceLocation CHAIN = ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "shaders/post/vibe_vision.json");
    private static final int WAKE = 12;
    private static long start = Long.MIN_VALUE, wake = Long.MIN_VALUE;
    private static int flicker, length, kind;
    private static PostChain chain;

    private VibeVisionClient() {}

    public static void receive(VibeVisionPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        long now = mc.level == null ? 0 : mc.level.getGameTime();
        if (payload.start()) {
            start = now;
            flicker = payload.flicker();
            length = payload.length();
            kind = payload.kind();
            wake = now + flicker + length;
        } else if (start != Long.MIN_VALUE) {
            wake = Math.min(wake, now);
        }
    }

    /** True while this client is in (or flickering into or out of) a vision. */
    public static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && start != Long.MIN_VALUE && mc.level.getGameTime() < wake + WAKE;
    }

    /** 0 outside a vision, rising through the flicker to 1 in it, falling through the wake. */
    private static float depth(float now) {
        if (start == Long.MIN_VALUE || now < start || now >= wake + WAKE) return 0;
        if (now < start + flicker) return Mth.clamp((now - start) / flicker, 0, 1);
        if (now < wake) return 1;
        return 1 - Mth.clamp((now - wake) / WAKE, 0, 1);
    }

    /** Whether this moment is a stutter frame: dense while flickering in and out, rare in the vision. */
    private static boolean stutter(float now) {
        int tick = (int) now;
        float noise = hash(tick);
        if (now < start + flicker) return noise < .18F + .3F * (now - start) / flicker;
        if (now >= wake) return noise < .4F;
        return noise < .035F;
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            reset();
            return;
        }
        boolean blur = active() && depth(mc.level.getGameTime()) > .35F;
        if (blur && (chain == null || mc.gameRenderer.currentEffect() != chain)) {
            try {
                mc.gameRenderer.loadEffect(CHAIN);
                chain = mc.gameRenderer.currentEffect();
            } catch (RuntimeException failure) {
                chain = null;
            }
        } else if (!blur && chain != null) {
            if (mc.gameRenderer.currentEffect() == chain) mc.gameRenderer.shutdownEffect();
            chain = null;
        }
        if (!active() && start != Long.MIN_VALUE && mc.level.getGameTime() > wake + WAKE) start = Long.MIN_VALUE;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void draw(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || start == Long.MIN_VALUE) return;
        float now = mc.level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(true);
        float depth = depth(now);
        if (depth <= 0) return;
        if (chain != null && mc.gameRenderer.currentEffect() == chain) {
            // The blur breathes, and swims harder in a stutter.
            float radius = 1.5F + 2.2F * depth + .8F * Mth.sin(now * .21F) + (stutter(now) ? 3 : 0);
            try {
                chain.setUniform("Radius", radius);
            } catch (RuntimeException ignored) {
            }
        }
        GuiGraphics g = event.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight();
        // The future reads a little violet; the present a cold sky blue.
        int tint = kind == 2 ? 0x8C7CFF : 0x7CC8FF;
        boolean stutter = stutter(now);
        float wash = depth * (stutter ? .42F : .16F);
        g.fill(0, 0, w, h, argb(wash, tint));
        // Soft bright edges, as if the picture bleeds out at the sides.
        int bands = 18;
        for (int i = 0; i < bands; i++) {
            float f = 1 - i / (float) bands;
            int edge = Math.max(1, w / 7 / bands), top = Math.max(1, h / 6 / bands);
            int color = argb(depth * .2F * f * f, 0xDDF3FF);
            g.fill(i * edge, 0, (i + 1) * edge, h, color);
            g.fill(w - (i + 1) * edge, 0, w - i * edge, h, color);
            g.fill(0, i * top, w, (i + 1) * top, color);
            g.fill(0, h - (i + 1) * top, w, h - i * top, color);
        }
        // Fine scan lines, and a few bright streaks drifting down the frame.
        int lines = argb(depth * .05F, 0x0A2C66);
        for (int y = (int) (now * 1.3F) % 3; y < h; y += 3) g.fill(0, y, w, y + 1, lines);
        for (int k = 0; k < 3; k++) {
            float y = ((now * (.9F + k * .37F) + k * 97) % (h + 40)) - 20;
            int alpha = (int) (depth * (60 + 40 * hash((int) (now / 3) + k * 17)));
            g.fill(0, (int) y, w, (int) y + 1 + k % 2, alpha << 24 | 0xE6F6FF);
        }
        // A stutter tears the frame: a couple of offset bright bars.
        if (stutter) {
            for (int k = 0; k < 2; k++) {
                int y = (int) (hash((int) now * 7 + k) * h), bar = 2 + (int) (hash((int) now * 13 + k) * 10);
                g.fill(0, y, w, y + bar, argb(depth * .35F, 0xF0FAFF));
            }
        }
    }

    private static int argb(float alpha, int rgb) {
        return Mth.clamp((int) (alpha * 255), 0, 255) << 24 | rgb & 0xFFFFFF;
    }

    private static float hash(int i) {
        int x = i * 0x27D4EB2D ^ 0x5BD1E995;
        x ^= x >>> 15;
        x *= 0x2C1B3C6D;
        x ^= x >>> 12;
        return (x & 0xFFFFFF) / (float) 0x1000000;
    }

    private static void reset() {
        start = Long.MIN_VALUE;
        wake = Long.MIN_VALUE;
        chain = null;
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        Minecraft mc = Minecraft.getInstance();
        if (chain != null && mc.gameRenderer.currentEffect() == chain) mc.gameRenderer.shutdownEffect();
        reset();
    }
}
