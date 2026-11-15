package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.story.bunker.BunkerData;
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

/** Creates a fresh normal world, lets the stasis awakening play, then tours the bunker taking screenshots. */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class BunkerPreview {
    private static boolean started;
    private static int ticks, worldTicks;
    private static String pending;

    private static void visit(Minecraft mc, int x, int y, int z, float yaw, float pitch, boolean surface) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            var player = server.getPlayerList().getPlayer(mc.player.getUUID());
            BlockPos origin = BunkerData.get(server).origin;
            int top = surface ? server.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, origin.getX(), origin.getZ()) + y : origin.getY() + y;
            player.setNoGravity(surface);
            player.teleportTo(server.overworld(), origin.getX() + x + .5, top, origin.getZ() + z + .5, yaw, pitch);
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
        switch (++worldTicks) {
            case 60 -> pending = "bunker-1-in-pod.png";
            // Emergency power: from the pod across the dark hall, down it, and up at the beacon over the door.
            case 90 -> mc.player.setXRot(-12);
            case 98 -> pending = "bunker-1b-dark-hall.png";
            case 100 -> visit(mc, 0, 1, 20, 0, -16, false);
            case 106 -> pending = "bunker-1c-dark-hall-down.png";
            case 109 -> pending = "bunker-1d-dark-hall-down-later.png";
            case 111 -> visit(mc, 0, 1, 23, 180, -34, false);
            case 116 -> pending = "bunker-1e-door-beacon.png";
            // Out of the pod once it opens: the power comes back.
            case 150 -> pending = "bunker-2-power-back.png";
            case 175 -> visit(mc, 0, 1, 20, 0, -12, false);
            case 200 -> pending = "bunker-3-chamber.png";
            case 210 -> visit(mc, 0, 1, 38, 180, -14, false);
            case 235 -> pending = "bunker-4-chamber-back.png";
            case 245 -> {
                mc.options.hideGui = true;
                visit(mc, 1, 1, 7, 194, -2, false);
            }
            case 270 -> pending = "bunker-5-receiver.png";
            case 280 -> visit(mc, 3, 1, 14, -80, 2, false);
            case 305 -> pending = "bunker-6-lab.png";
            case 315 -> visit(mc, -2, 1, 15, 118, 6, false);
            case 340 -> pending = "bunker-7-infirmary.png";
            case 350 -> visit(mc, 8, 8, 10, 141, 38, true);
            case 380 -> pending = "bunker-8-hatch.png";
            case 390 -> visit(mc, -4, 7, 29, 6, 8, false);
            case 415 -> pending = "bunker-9-gallery.png";
            case 425 -> visit(mc, 6, 1, 3, -60, 12, false);
            case 450 -> pending = "bunker-10-generator.png";
            case 460 -> visit(mc, -6, 1, 3, 60, 12, false);
            case 485 -> pending = "bunker-11-security.png";
            case 495 -> visit(mc, 1, 1, 30, -34, -28, false);
            case 520 -> pending = "bunker-12-breach.png";
            case 530 -> visit(mc, -10, -5, 8, -110, 12, false);
            case 555 -> pending = "bunker-13-maintenance.png";
            case 565 -> visit(mc, -4, 1, 25, 120, 8, false);
            case 590 -> pending = "bunker-14-pods.png";
            case 600 -> visit(mc, 0, 1, 28, 0, -12, false); // down the hall to the far wall: Exhibit 1 over the analysts' desk
            case 625 -> pending = "bunker-15-exhibit-one.png";
            case 635 -> visit(mc, -1, 1, 35, 0, -26, false); // up close, where its label can be read
            case 660 -> pending = "bunker-16-exhibit-one-close.png";
            case 670 -> visit(mc, -2, 1, 20, 90, -18, false); // the sleeper's own pod and nameplate
            case 695 -> pending = "bunker-17-nameplate.png";
            case 705 -> visit(mc, 2, 1, -3, 34, -6, false); // the vault from its landing: stuck open, cracked, grown over
            case 730 -> pending = "bunker-18-vault.png";
            case 740 -> visit(mc, 16, 5, 7, 140, 24, true); // the ruined lobby on the surface, from the south-east
            case 765 -> pending = "bunker-19-lobby.png";
            case 775 -> visit(mc, -1, 2, -6, -90, 30, true); // the head of the stair, looking down the slot
            case 800 -> pending = "bunker-20-stair-slot.png";
            case 810 -> visit(mc, 4, 1, -2, -90, -18, false); // in the well, up the first flight
            case 835 -> pending = "bunker-21-stairwell.png";
            // Audit at the end, once every light has come back on and the light has settled.
            case 845 -> {
                var server = mc.getSingleplayerServer();
                server.execute(() -> {
                    BlockPos o = BunkerData.get(server).origin;
                    int surface = server.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, o.getX(), o.getZ());
                    System.out.println("BUNKER_AUDIT\n" + com.jayptucker.heroesevolved.time.intro.city.CityAudit.run(server.overworld(),
                            o.getX() - 18, o.getY() - 8, o.getZ() - 11, o.getX() + 18, surface + 16, o.getZ() + 44));
                    // Dark floor only inside what the bunker built (its rooms, then the stairwell), not the caves round it.
                    var rooms = com.jayptucker.heroesevolved.story.bunker.PastBunker.bounds(o);
                    System.out.println("BUNKER_DARK rooms\n" + com.jayptucker.heroesevolved.time.intro.city.CityAudit.run(server.overworld(),
                            rooms.minX() + 2, rooms.minY() + 2, rooms.minZ() + 2, rooms.maxX() - 2, rooms.maxY() - 2, rooms.maxZ() - 2, true));
                    System.out.println("BUNKER_DARK stair\n" + com.jayptucker.heroesevolved.time.intro.city.CityAudit.run(server.overworld(),
                            o.getX() - 3, o.getY() - 1, o.getZ() - 9, o.getX() + 15, surface, o.getZ(), true));
                });
            }
            case 850 -> PreviewQuiet.quit();
            default -> { }
        }
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post e) {
        if (pending == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(out.getParent()); image.writeToFile(out);
            System.out.println("BUNKER_CAPTURE " + pending + " " + net.minecraft.client.Minecraft.getInstance().getSoundManager().getDebugString());
        } catch (Exception ex) { throw new RuntimeException(ex); }
        pending = null;
    }
}
