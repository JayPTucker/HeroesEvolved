package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.AbilitySlot;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.AbilityUseService;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.client.TitleMenu;
import com.jayptucker.heroesevolved.cooldown.CooldownService;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.gui.screen.PowersScreen;
import com.jayptucker.heroesevolved.network.PlayerPowerSyncService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.story.bunker.CryoChamberService;
import com.jayptucker.heroesevolved.story.prologue.PrologueService;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Photographs the mod next to other client mods ({@code tools/client-mods-check.init.gradle}, flag
 * {@code heroes.preview.clientmods}): our title screen with whatever the other mods add to it, a world with a power in
 * use (seen from the front, so anything drawn on the player shows), the pause menu and the Powers screen. Written for
 * Essential, which rebuilds the title and pause menus and draws cosmetics and emotes on the player; it reports which of
 * {@code heroes.check.mods} (a comma-separated list of mod ids) actually loaded, and fails if one didn't.
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class ClientModsCheck {
    private static int clock, titleAt = -1, age;
    private static boolean worldMade;
    private static String capture;

    private ClientModsCheck() {
    }

    private static boolean on() {
        return Boolean.getBoolean("heroes.preview.clientmods");
    }

    private static void server(Minecraft mc, Consumer<ServerPlayer> action) {
        var id = mc.player.getUUID();
        var server = mc.getSingleplayerServer();
        server.execute(() -> action.accept(server.getPlayerList().getPlayer(id)));
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!on()) return;
        Minecraft mc = Minecraft.getInstance();
        clock++;
        if (mc.getOverlay() != null) return;
        if (!worldMade) {
            if (!(mc.screen instanceof TitleScreen)) {
                // A fresh game folder opens the first-launch accessibility screen; go on to the title screen.
                if (clock > 60 && titleAt < 0 && mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen) {
                    mc.options.onboardAccessibility = false;
                    mc.setScreen(new TitleScreen(true));
                }
                return;
            }
            if (titleAt < 0) {
                titleAt = clock;
                mc.options.pauseOnLostFocus = false;
                for (String id : System.getProperty("heroes.check.mods", "").split(",")) {
                    if (id.isBlank()) continue;
                    boolean loaded = ModList.get().isLoaded(id.trim());
                    PreviewQuiet.report("CLIENT_MODS_CHECK " + id.trim() + (loaded ? " loaded" : " FAIL not loaded"));
                }
            }
            int reveal = (int) (TitleMenu.CONTROLS_REVEAL_MS / 50);
            if (clock == titleAt + reveal + 60) capture = "1-title.png";
            // Escape closes a notice another mod opened over the menu (Essential's, for an offline account), so the next
            // shot shows both menus side by side.
            if (clock == titleAt + reveal + 66 || clock == titleAt + reveal + 67) {
                int action = clock == titleAt + reveal + 66 ? org.lwjgl.glfw.GLFW.GLFW_PRESS : org.lwjgl.glfw.GLFW.GLFW_RELEASE;
                mc.keyboardHandler.keyPress(mc.getWindow().getWindow(), org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, 0, action, 0);
            }
            if (clock == titleAt + reveal + 75) capture = "1b-title-menus.png";
            if (clock == titleAt + reveal + 95) {
                worldMade = true;
                var rules = new GameRules();
                rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
                mc.createWorldOpenFlows().createFreshLevel("ClientMods-" + System.currentTimeMillis(),
                        new LevelSettings("Client Mods Check", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules,
                                WorldDataConfiguration.DEFAULT),
                        new WorldOptions(73L, false, false),
                        access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT)
                                .createWorldDimensions(), mc.screen);
            }
            return;
        }
        if (mc.player == null || mc.getSingleplayerServer() == null) return;
        age++;
        if (age < 300 && mc.screen instanceof PauseScreen) mc.setScreen(null);
        ResourceLocation power = ModAbilities.PYROKINESIS_ID;
        switch (age) {
            case 100 -> server(mc, p -> {
                PrologueService.skip(p);
                CryoChamberService.release(p);
                p.setData(ModDataAttachments.POWER_INTRO_HISTORY, p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(power));
                PlayerAbilityService.replaceWithAbility(p, power);
                PlayerProgressionService.setPowerLevel(p, 20);
                p.setGameMode(GameType.SURVIVAL);
                var world = p.serverLevel();
                world.setDayTime(6000);
                int ground = world.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, 0, 0);
                p.teleportTo(world, 0.5, ground, 0.5, 0, 0);
                PlayerEnergyService.restore(p, 999);
                PlayerPowerSyncService.sync(p);
            });
            case 140 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                mc.getToasts().clear();
            }
            case 160 -> capture = "2-in-world.png";
            case 170 -> server(mc, p -> {
                CooldownService.clearAll(p);
                var result = AbilityUseService.activateAssignedAction(p, AbilitySlot.PRIMARY, false);
                PreviewQuiet.report("CLIENT_MODS_CHECK pyrokinesis primary: " + result);
            });
            case 178 -> capture = "3-power-in-use.png";
            case 230 -> mc.options.setCameraType(CameraType.FIRST_PERSON);
            case 250 -> capture = "4-first-person.png";
            case 300 -> mc.pauseGame(false);
            case 330 -> capture = "5-pause-menu.png";
            case 340 -> mc.setScreen(new PowersScreen());
            case 370 -> capture = "6-powers-screen.png";
            case 380 -> mc.setScreen(null);
            case 400 -> {
                PreviewQuiet.report("CLIENT_MODS_CHECK finished");
                PreviewQuiet.quit();
            }
            default -> {
            }
        }
    }

    @SubscribeEvent
    public static void frame(RenderFrameEvent.Post event) {
        if (capture == null) return;
        Minecraft mc = Minecraft.getInstance();
        try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(capture);
            Files.createDirectories(out.getParent());
            image.writeToFile(out);
            System.out.println("CLIENT_MODS_CHECK photographed " + capture);
        } catch (Exception e) {
            System.out.println("CLIENT_MODS_CHECK could not photograph " + capture + ": " + e);
        }
        capture = null;
    }
}
