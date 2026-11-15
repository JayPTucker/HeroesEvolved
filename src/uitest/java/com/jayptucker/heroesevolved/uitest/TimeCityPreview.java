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
            {24.5, 66.1, -76.5, 90f, -6f, "01-kirby-plaza"},
            {2.5, 65.6, -70.5, -110f, -4f, "02-kirby-from-ted"},
            {4.5, 65.6, 70.5, 180f, -4f, "03-arrival-up-the-avenue"},
            {4.5, 65.6, 36.5, 180f, -28f, "04-great-towers"},
            {-2.5, 67.6, 34.5, -120f, -22f, "05-odessa-square"},
            {14.5, 66.1, 13.5, 0f, -8f, "06-red-steps"},
            {-44.5, 66.6, 99.5, 0f, -14f, "07-odessa-arms"},
            {-42.5, 66.6, 116.5, 90f, -12f, "08-arms-stairwell"},
            {-46.5, 76.6, 116.5, 90f, 40f, "09-arms-stairwell-down"},
            {-48.5, 71.6, 111.5, 135f, 10f, "10-arms-apartment"},
            {-108.5, 69.6, -97.5, 180f, 8f, "11-commons-pond"},
            {-60.5, 110.6, -60.5, 135f, 35f, "12-commons-aerial"},
            {4.5, 65.6, 88.5, 0f, -6f, "13-primatech"},
            {-30.5, 65.6, 68.5, -90f, -4f, "14-residential-street"},
            {36.5, 65.6, 30.5, 180f, -18f, "15-midtown-canyon"},
            {4.5, 160.6, 175.5, 180f, 14f, "16-skyline-from-south"},
            {110.5, 175.6, 140.5, 145f, 30f, "17-aerial-southeast"},
            {-120.5, 150.6, 60.5, -115f, 18f, "18-skyline-from-west"},
            {-48.5, 236.6, 34.5, -140f, 12f, "19-crowns"},
            {131.5, 65.6, 70.5, 180f, -6f, "20-suburb-street"},
            {240.5, 150.6, 240.5, 135f, 22f, "21-aerial-outskirts"},
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
            if (city != null && FractureCity.get(city).complete()) {
                built = true; shot = 0; wait = 0;
                System.out.println("CITY_READY at " + worldTicks);
                int lo = com.jayptucker.heroesevolved.time.intro.city.CityGrid.OUTER_MIN, hi = com.jayptucker.heroesevolved.time.intro.city.CityGrid.OUTER_MAX;
                server.execute(() -> System.out.println("CITY_AUDIT\n" + com.jayptucker.heroesevolved.time.intro.city.CityAudit.run(city, lo, lo, hi, hi)));
            }
            return;
        }
        if (shot >= VIEWS.length) { if (++wait > 20) PreviewQuiet.quit(); return; }
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
            // A new player is enrolled in the Last Day, which keeps them inside its streets: the camera roams the whole city.
            com.jayptucker.heroesevolved.story.prologue.PrologueService.skip(player);
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
