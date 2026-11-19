package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.story.bunker.BunkerData;
import com.jayptucker.heroesevolved.story.bunker.BunkerPower;
import com.jayptucker.heroesevolved.story.prologue.PrologueService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import java.nio.file.*;
import java.util.List;

/**
 * Creates a fresh normal world and wakes its player as the Helix Ward's sleeper (the Last Day skipped, the ward claimed):
 * the dark ward and its pod, the power coming back, the way out through the vent, then a tour of every room of
 * Sublevel 4, the hall on emergency power, the vault, the stair and the lobby, and the block audit.
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class BunkerPreview {
    private static boolean started;
    private static int ticks, worldTicks;
    private static String pending;

    /** A camera: bunker-relative feet position (or above the surface), facing. */
    private record Shot(String name, int x, int y, int z, float yaw, float pitch, boolean surface) {
        Shot(String name, int x, int y, int z, float yaw, float pitch) { this(name, x, y, z, yaw, pitch, false); }
    }

    /** The tour after the power is back: each shot is framed, then taken 25 ticks later. */
    private static final List<Shot> TOUR = List.of(
            // From where each of the user's screenshots (2026-11-18) was taken, to check what they flagged.
            new Shot("fix-01-records-console", 16, 1, 11, 156, 0),
            new Shot("fix-02-commons-columns", 5, 4, 25, 31, 5),
            new Shot("fix-03-infirmary", -29, 1, 23, -34, 13),
            new Shot("fix-04-maintenance", 6, -7, 8, 89, 10),
            new Shot("fix-05-booth", 4, 1, 9, -143, 25),
            new Shot("fix-06-decon-east", 4, 1, 2, -21, 3),
            new Shot("fix-07-decon-west", -3, 1, 2, 25, 1),
            new Shot("fix-08-stairwell", 10, 23, -3, -54, 8, false),
            new Shot("fix-09-vent", 17, 7, 46, 93, 8),
            new Shot("fix-10-office-door", 23, 1, 44, -79, 12),
            new Shot("bunker-11-hall-door", 0, 1, 34, 0, -6),
            new Shot("bunker-17-commons", 0, 1, 31, 180, -14),
            new Shot("bunker-26-infirmary", -24, 1, 21, 90, 2));
    private static final int TOUR_START = 300, STEP = 30;

    private static void visit(Minecraft mc, Shot shot) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            var player = server.getPlayerList().getPlayer(mc.player.getUUID());
            BlockPos origin = BunkerData.get(server).origin;
            int top = shot.surface() ? server.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, origin.getX(), origin.getZ()) + shot.y()
                    : origin.getY() + shot.y();
            player.setNoGravity(true);
            player.teleportTo(server.overworld(), origin.getX() + shot.x() + .5, top, origin.getZ() + shot.z() + .5, shot.yaw(), shot.pitch());
        });
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        if (!Boolean.getBoolean("heroes.preview.bunker")) return;
        var mc = Minecraft.getInstance();
        ticks++;
        if (!started && ticks > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.options.gamma().set(1.0);
            mc.options.pauseOnLostFocus = false;
            mc.createWorldOpenFlows().createFreshLevel("Bunker-" + System.currentTimeMillis(),
                    new LevelSettings("Bunker preview", GameType.SURVIVAL, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                    new WorldOptions(2026L, true, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.NORMAL).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        int t = ++worldTicks;
        var server = mc.getSingleplayerServer();
        switch (t) {
            // The ward is this player's: skip the Last Day and wake there, dosed.
            case 20 -> server.execute(() -> {
                var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                BunkerData.get(server).claimLab(player.getUUID());
                PrologueService.skip(player);
            });
            case 95 -> pending = "bunker-01-ward-pod.png";
            case 125 -> pending = "bunker-02-ward-pod-alarm.png";
            case 165 -> pending = "bunker-03-ward-open.png";
            // Out of the pod: the alarm stops and the power comes back, in a wave from the generator.
            case 180 -> visit(mc, new Shot("ward", 22, 2, 38, 180, 4));
            case 200 -> pending = "bunker-04-ward-power-coming.png";
            case 285 -> mc.options.hideGui = true;
            default -> { }
        }
        if (t >= TOUR_START && t < TOUR_START + STEP * TOUR.size()) {
            Shot shot = TOUR.get((t - TOUR_START) / STEP);
            if ((t - TOUR_START) % STEP == 0) visit(mc, shot);
            if ((t - TOUR_START) % STEP == 25) pending = shot.name() + ".png";
        }
        int end = TOUR_START + STEP * TOUR.size();
        // Emergency power, as a new sleeper's waking leaves it: the hall dark, its beacons turning.
        if (t == end) server.execute(() -> BunkerPower.cut(server.overworld(), BunkerData.get(server)));
        if (t == end + 2) visit(mc, new Shot("dark", 0, 1, 34, 0, -14));
        if (t == end + 30) pending = "bunker-32-hall-emergency.png";
        if (t == end + 32) visit(mc, new Shot("dark", 0, 1, 31, 180, -18));
        if (t == end + 60) pending = "bunker-33-commons-emergency.png";
        if (t == end + 62) server.execute(() -> BunkerPower.start(server.overworld(), BunkerData.get(server)));
        // Audit at the end, once every light has come back on and the light has settled.
        if (t == end + 200) server.execute(() -> {
            BlockPos o = BunkerData.get(server).origin;
            int surface = server.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, o.getX(), o.getZ());
            var rooms = com.jayptucker.heroesevolved.story.bunker.PastBunker.bounds(o);
            System.out.println("BUNKER_AUDIT\n" + com.jayptucker.heroesevolved.time.intro.city.CityAudit.run(server.overworld(),
                    rooms.minX(), rooms.minY(), o.getZ() - 11, rooms.maxX(), surface + 16, rooms.maxZ()));
            // Dark floor only inside what the bunker built (its rooms, then the stairwell), not the caves round it.
            System.out.println("BUNKER_DARK rooms\n" + com.jayptucker.heroesevolved.time.intro.city.CityAudit.run(server.overworld(),
                    rooms.minX() + 2, rooms.minY() + 2, rooms.minZ() + 2, rooms.maxX() - 2, rooms.maxY() - 2, rooms.maxZ() - 2, true));
            System.out.println("BUNKER_DARK stair\n" + com.jayptucker.heroesevolved.time.intro.city.CityAudit.run(server.overworld(),
                    o.getX() - 3, o.getY() - 1, o.getZ() - 9, o.getX() + 15, surface, o.getZ(), true));
            var player = server.getPlayerList().getPlayer(mc.player.getUUID());
            System.out.println("BUNKER_WARD power " + com.jayptucker.heroesevolved.ability.service.PlayerAbilityService.getData(player).assignedPower()
                    .map(p -> p.getKey() + (p.getValue().isUnlocked() ? " awake" : " dormant")).orElse("none"));
        });
        if (t == end + 205) PreviewQuiet.quit();
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post e) {
        if (pending == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(out.getParent()); image.writeToFile(out);
            System.out.println("BUNKER_CAPTURE " + pending);
        } catch (Exception ex) { throw new RuntimeException(ex); }
        pending = null;
    }
}
