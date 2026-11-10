package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.time.intro.FractureCity;
import com.jayptucker.heroesevolved.time.intro.TimeIntroService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

import java.nio.file.*;

/** Waits for Odessa to be built, then photographs it from the street and from the air. */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class TimeCityPreview {
    private static boolean started, built;
    private static int ticks, worldTicks, shot = -1, wait;
    private static String pending;

    /** x, y, z, yaw, pitch, name. */
    private static final Object[][] VIEWS = {
            {4.5, 65.6, 70.5, 180f, -4f, "1-arrival"},
            {4.5, 65.6, 20.5, 180f, -8f, "2-avenue-midtown"},
            {9.5, 65.6, -12.5, 110f, -6f, "3-city-hall"},
            {-30.5, 65.6, 68.5, -90f, -4f, "4-residential-street"},
            {-44.5, 65.6, 60.5, 200f, -8f, "5-shopfronts"},
            {40.5, 66.6, 36.5, 135f, -10f, "6-park"},
            {-26.5, 66.6, 36.5, 225f, -18f, "7-church"},
            {60.5, 66.6, 66.5, 160f, -25f, "8-crane"},
            {4.5, 65.6, 88.5, 0f, -6f, "9-primatech"},
            {4.5, 65.6, -40.5, 180f, -20f, "10-downtown"},
            {90.5, 175.6, 130.5, 145f, 32f, "11-aerial-southeast"},
            {4.5, 150.6, 150.5, 180f, 22f, "12-aerial-south"},
            {-60.5, 120.6, -20.5, 225f, 25f, "13-aerial-downtown"},
    };

    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        if (!Boolean.getBoolean("heroes.preview.timecity")) return;
        var mc = Minecraft.getInstance();
        ticks++;
        if (!started && ticks > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.options.hideGui = true;
            mc.options.pauseOnLostFocus = false;
            mc.options.renderDistance().set(12);
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("City-" + System.currentTimeMillis(),
                    new LevelSettings("City preview", GameType.SPECTATOR, false, Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(7L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        var server = mc.getSingleplayerServer();
        worldTicks++;
        if (worldTicks == 40) go(mc, 4.5, 140, 70.5, 180, 30);
        if (!built) {
            var city = server.getLevel(TimeIntroService.DIMENSION);
            if (city != null && worldTicks % 100 == 0) System.out.println("CITY_PROGRESS " + FractureCity.get(city).progress());
            if (city != null && FractureCity.get(city).ready()) { built = true; shot = 0; wait = 0; System.out.println("CITY_READY at " + worldTicks); }
            return;
        }
        if (shot >= VIEWS.length) { if (++wait > 20) mc.stop(); return; }
        Object[] view = VIEWS[shot];
        if (wait == 0) go(mc, (double) view[0], (double) view[1], (double) view[2], (float) view[3], (float) view[4]);
        wait++;
        // Give chunks time to mesh after each jump.
        if (wait == 70) pending = "city-" + view[5] + ".png";
        if (wait > 72) { shot++; wait = 0; }
    }

    private static void go(Minecraft mc, double x, double y, double z, float yaw, float pitch) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            var player = server.getPlayerList().getPlayer(mc.player.getUUID());
            player.teleportTo(server.getLevel(TimeIntroService.DIMENSION), x, y, z, yaw, pitch);
        });
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post e) {
        if (pending == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(out.getParent()); image.writeToFile(out);
            System.out.println("CITY_CAPTURE " + pending);
        } catch (Exception ex) { throw new RuntimeException(ex); }
        pending = null;
    }
}
