package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.AbilitySlot;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.AbilityUseService;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.story.bunker.CryoChamberService;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.registries.Registries;
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
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Photographs the stamina bar in each of its states (full, just spent, recovering, Still World held, low, refused) into
 * {@code build/stamina-preview/screenshots/}, then closes. Flag {@code heroes.preview.stamina}.
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class StaminaHudPreview {
    private static final int STAGE = 150;
    private static boolean started;
    private static int startup, age;
    private static String capture;

    private static boolean on() { return Boolean.getBoolean("heroes.preview.stamina"); }

    private static void server(Minecraft mc, Consumer<ServerPlayer> action) {
        var id = mc.player.getUUID();
        var server = mc.getSingleplayerServer();
        server.execute(() -> action.accept(server.getPlayerList().getPlayer(id)));
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!on()) return;
        var mc = Minecraft.getInstance();
        if (!started && ++startup >= 35 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.options.pauseOnLostFocus = false;
            mc.options.renderDistance().set(4);
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("Stamina-" + System.currentTimeMillis(),
                    new LevelSettings("Stamina Preview", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(73L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.getSingleplayerServer() == null || mc.getOverlay() != null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        if (age >= 100) mc.getToasts().clear();
        ++age;
        if (age == 104) { server(mc, com.jayptucker.heroesevolved.story.prologue.PrologueService::skip); return; }
        if (age == 110) {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
            com.jayptucker.heroesevolved.network.ClientScreenTintState.clear();
            var power = ModAbilities.TIME_MANIPULATION_ID;
            server(mc, p -> {
                CryoChamberService.release(p);
                p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(power).unlock(power));
                p.setData(ModDataAttachments.POWER_INTRO_HISTORY, p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(power));
                PlayerProgressionService.setPowerLevel(p, 15);
                p.setGameMode(GameType.SURVIVAL);
                var world = p.getServer().overworld();
                world.setChunkForced(STAGE >> 4, STAGE >> 4, true);
                world.setDayTime(6000);
                p.teleportTo(world, STAGE + .5, -60, STAGE + .5, 0, 10);
                PlayerEnergyService.restore(p, 999);
                com.jayptucker.heroesevolved.network.PlayerPowerSyncService.sync(p);
                PlayerEnergyService.sendNow(p);
                System.out.println("STAMINA_PREVIEW staged in " + p.level().dimension().location() + " on "
                        + world.getBlockState(p.blockPosition().below()));
            });
            return;
        }
        if (age % 20 == 0) com.jayptucker.heroesevolved.network.ClientScreenTintState.clear();
        switch (age) {
            case 200 -> capture = "stamina-1-full.png";
            case 210 -> server(mc, p -> PlayerEnergyService.tryConsume(p, 45));
            case 214 -> capture = "stamina-2-just-spent.png";
            case 270 -> capture = "stamina-3-recovering.png";
            case 290 -> server(mc, p -> System.out.println("STAMINA_PREVIEW still world "
                    + AbilityUseService.activateAssignedAction(p, AbilitySlot.TERTIARY, false)));
            case 330 -> capture = "stamina-4-still-world.png";
            case 340 -> server(mc, p -> AbilityUseService.activateAssignedAction(p, AbilitySlot.TERTIARY, false));
            case 345 -> server(mc, p -> PlayerEnergyService.tryConsume(p, Math.max(0, PlayerEnergyService.getEnergy(p) - 22)));
            case 352 -> capture = "stamina-5-low.png";
            case 380 -> server(mc, p -> {
                com.jayptucker.heroesevolved.cooldown.CooldownService.clearAll(p);
                PlayerEnergyService.tryConsume(p, Math.max(0, PlayerEnergyService.getEnergy(p) - 22));
                System.out.println("STAMINA_PREVIEW refused " + AbilityUseService.activateAssignedAction(p, AbilitySlot.TERTIARY, false));
            });
            case 382 -> capture = "stamina-6-refused.png";
            case 400 -> PreviewQuiet.quit();
            default -> { }
        }
    }

    @SubscribeEvent public static void frame(RenderFrameEvent.Post event) {
        if (capture == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(capture);
            Files.createDirectories(out.getParent());
            image.writeToFile(out);
            System.out.println("STAMINA_CAPTURE " + capture);
        } catch (Exception ex) { throw new RuntimeException(ex); }
        capture = null;
    }
}
