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
            int top = surface ? server.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, origin.getX() + x, origin.getZ() + z) + y : origin.getY() + y;
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
            case 150 -> pending = "bunker-2-released.png";
            case 175 -> visit(mc, 0, 1, 20, 0, -12, false);
            case 200 -> pending = "bunker-3-chamber.png";
            case 210 -> visit(mc, 0, 1, 38, 180, -14, false);
            case 235 -> pending = "bunker-4-chamber-back.png";
            case 245 -> visit(mc, 0, 1, 9, 180, 10, false);
            case 270 -> pending = "bunker-5-records.png";
            case 280 -> visit(mc, 3, 1, 15, -90, 15, false);
            case 305 -> pending = "bunker-6-lab.png";
            case 315 -> visit(mc, -3, 1, 15, 90, 10, false);
            case 340 -> pending = "bunker-7-cell.png";
            case 350 -> visit(mc, 7, 9, 11, 148, 32, true);
            case 380 -> pending = "bunker-8-hatch.png";
            case 390 -> visit(mc, -4, 7, 29, 90, 4, false);
            case 415 -> pending = "bunker-9-gallery-painting.png";
            case 425 -> visit(mc, 6, 1, 3, -60, 12, false);
            case 450 -> pending = "bunker-10-generator.png";
            case 460 -> visit(mc, -6, 1, 3, 60, 12, false);
            case 485 -> pending = "bunker-11-security.png";
            case 495 -> visit(mc, 1, 1, 30, -34, -28, false);
            case 520 -> pending = "bunker-12-breach.png";
            case 530 -> visit(mc, -9, -5, 8, 180, 8, false);
            case 555 -> pending = "bunker-13-bennet-office.png";
            case 565 -> visit(mc, -4, 1, 25, 120, 8, false);
            case 590 -> pending = "bunker-14-pods.png";
            case 610 -> mc.stop();
            default -> { }
        }
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
