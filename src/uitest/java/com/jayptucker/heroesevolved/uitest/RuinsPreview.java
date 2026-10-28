package com.jayptucker.heroesevolved.uitest;

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

/** Places each story ruin with /place structure in a normal world and photographs it from above and beside. */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class RuinsPreview {
    private static final String[] RUINS = {"burnt_house", "collapsed_house", "bennet_house", "isaac_studio"};
    private static boolean started;
    private static int ticks, worldTicks;
    private static String pending;

    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        if (!Boolean.getBoolean("heroes.preview.ruins")) return;
        var mc = Minecraft.getInstance();
        ticks++;
        if (!started && ticks > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.options.gamma().set(1.0);
            mc.options.hideGui = true;
            mc.options.pauseOnLostFocus = false;
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("Ruins-" + System.currentTimeMillis(),
                    new LevelSettings("Ruins preview", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(4242L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        var server = mc.getSingleplayerServer();
        worldTicks++;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        int index = (worldTicks - 200) / 60, phase = (worldTicks - 200) % 60;
        if (worldTicks < 200) return; // let the intro alarm overlay fade first
        if (index >= RUINS.length) { if (phase == 0) mc.stop(); return; }
        int x = 200 + index * 40; // well clear of the spawn bunker
        String name = RUINS[index];
        if (phase == 0) server.execute(() -> {
            server.overworld().setDayTime(6000);
            var source = server.createCommandSourceStack().withSuppressedOutput().withPermission(4);
            server.getCommands().performPrefixedCommand(source, "place template heroesevolved:ruins/" + name + " " + x + " -61 0");
            var player = server.getPlayerList().getPlayer(mc.player.getUUID());
            player.getAbilities().flying = true; player.onUpdateAbilities();
            player.teleportTo(server.overworld(), x + 21, -51, 23, 135, 25);
        });
        if (phase == 25) pending = "ruins-" + name + "-outside.png";
        if (phase == 30) server.execute(() -> server.getPlayerList().getPlayer(mc.player.getUUID())
                .teleportTo(server.overworld(), x + 8, -34, 6.5, 0, 89));
        if (phase == 55) pending = "ruins-" + name + "-above.png";
        if (phase == 57 && name.equals("isaac_studio")) server.execute(() -> server.getPlayerList().getPlayer(mc.player.getUUID())
                .teleportTo(server.overworld(), x + 8.5, -60, 5.5, 90, 0));
        if (phase == 59 && name.equals("isaac_studio")) pending = "ruins-" + name + "-painting.png";
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post e) {
        if (pending == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(out.getParent()); image.writeToFile(out);
            System.out.println("RUINS_CAPTURE " + out);
        } catch (Exception ex) { throw new RuntimeException(ex); }
        pending = null;
    }
}
