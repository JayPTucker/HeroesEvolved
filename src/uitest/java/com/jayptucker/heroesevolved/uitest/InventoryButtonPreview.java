package com.jayptucker.heroesevolved.uitest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
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

/** Screenshots the inventory Skills button (recipe book closed and open) and the creative tab icon, then exits. */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class InventoryButtonPreview {
    private static boolean started;
    private static int ticks, worldTicks;
    private static String pending;

    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        if (!Boolean.getBoolean("heroes.preview.inventory")) return;
        var mc = Minecraft.getInstance();
        ticks++;
        if (!started && ticks > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.createWorldOpenFlows().createFreshLevel("Inventory-" + System.currentTimeMillis(),
                    new LevelSettings("Inventory preview", GameType.SURVIVAL, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                    new WorldOptions(1L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        var server = mc.getSingleplayerServer();
        switch (++worldTicks) {
            case 60 -> mc.setScreen(new InventoryScreen(mc.player));
            case 70 -> pending = "inventory-skills-button.png";
            case 80 -> mc.screen.children().stream().filter(c -> c instanceof ImageButton).findFirst()
                    .ifPresent(c -> ((ImageButton) c).onPress());
            case 90 -> pending = "inventory-skills-button-recipe-open.png";
            case 100 -> server.execute(() -> server.getPlayerList().getPlayer(mc.player.getUUID()).setGameMode(GameType.CREATIVE));
            case 120 -> mc.setScreen(new CreativeModeInventoryScreen(mc.player, mc.player.connection.enabledFeatures(), false));
            // Mod tabs sit on a later creative page; open the page holding ours.
            case 130 -> mc.screen.children().stream().filter(c -> c instanceof net.minecraft.client.gui.components.Button b
                    && b.getMessage().getString().equals(">")).findFirst()
                    .ifPresent(c -> ((net.minecraft.client.gui.components.Button) c).onPress());
            case 140 -> pending = "creative-tab-icon.png";
            case 155 -> mc.stop();
            default -> { }
        }
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post e) {
        if (pending == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(out.getParent()); image.writeToFile(out);
            System.out.println("INVENTORY_CAPTURE " + pending);
        } catch (Exception ex) { throw new RuntimeException(ex); }
        pending = null;
    }
}
