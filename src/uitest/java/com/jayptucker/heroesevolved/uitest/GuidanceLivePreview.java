package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.client.PlayerGuidance;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.guidance.GuidanceData;
import com.jayptucker.heroesevolved.guidance.GuidanceService;
import com.jayptucker.heroesevolved.gui.screen.GuideScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.core.registries.Registries;
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

/** Actual login sync, HUD receipt and Guide button round trips over an integrated server connection. */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class GuidanceLivePreview {
    private static boolean started;
    private static int startup, age, step, stepTicks;
    private static volatile boolean receipt, disabled, restored;
    private static String capture;

    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        if (!Boolean.getBoolean("heroes.preview.guidanceLive")) return;
        var mc = Minecraft.getInstance();
        if (!started && ++startup > 35 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.options.pauseOnLostFocus = false;
            mc.options.guiScale().set(2); mc.resizeDisplay();
            mc.options.renderDistance().set(4);
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("Guidance-" + System.currentTimeMillis(),
                    new LevelSettings("Guidance Preview", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(73L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.getSingleplayerServer() == null || mc.getOverlay() != null) return;
        if (mc.screen instanceof PauseScreen) mc.setScreen(null);
        var server = mc.getSingleplayerServer();
        var id = mc.player.getUUID();
        age++;
        if (age == 104) server.execute(() -> com.jayptucker.heroesevolved.story.prologue.PrologueService.skip(server.getPlayerList().getPlayer(id)));
        if (age == 120) {
            mc.options.hideGui = false;
            mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
            server.execute(() -> {
                var p = server.getPlayerList().getPlayer(id);
                com.jayptucker.heroesevolved.story.bunker.CryoChamberService.release(p);
                p.setData(ModDataAttachments.PLAYER_ABILITIES, com.jayptucker.heroesevolved.ability.data.PlayerAbilityData.empty());
                p.setData(ModDataAttachments.GUIDANCE, GuidanceData.empty());
                GuidanceService.sync(p);
                p.teleportTo(server.overworld(), 150.5, -60, 150.5, 0, 8);
                server.overworld().setDayTime(6000);
                com.jayptucker.heroesevolved.network.PlayerPowerSyncService.sync(p);
            });
        }
        if (age < 140) return;
        if (age % 20 == 0) server.execute(() -> {
            var data = server.getPlayerList().getPlayer(id).getData(ModDataAttachments.GUIDANCE);
            receipt = data.hasSeen(GuidanceData.Hint.WELCOME);
            disabled = !data.enabled();
            restored = data.enabled() && receipt;
        });
        stepTicks++;
        if (step == 0 && receipt) { capture = "live-field-note"; step = 1; }
        if (step == 2 && stepTicks > 5) {
            mc.setScreen(new GuideScreen(null)); step = 3; stepTicks = 0;
        }
        if (step == 3 && stepTicks == 5) clickTips(mc);
        if (step == 3 && stepTicks > 5 && disabled && !PlayerGuidance.enabled()) { capture = "live-tips-disabled"; step = 4; }
        if (step == 5 && stepTicks == 5) clickTips(mc);
        if (step == 5 && stepTicks > 5 && restored && PlayerGuidance.enabled()) {
            PreviewQuiet.report("GUIDANCE_LIVE login, rendered receipt, disable and re-enable preserve history PASS");
            PreviewQuiet.quit();
        }
        if (age > 1200) { PreviewQuiet.report("GUIDANCE_LIVE FAIL timed out at step " + step); PreviewQuiet.quit(); }
    }

    private static void clickTips(Minecraft mc) {
        int left = Math.max(6, (mc.screen.width - Math.min(mc.screen.width - 12, 560)) / 2);
        mc.screen.mouseClicked(left + 12, mc.screen.height - 37, 0);
    }

    @SubscribeEvent public static void rendered(RenderFrameEvent.Post e) {
        if (capture == null || !Boolean.getBoolean("heroes.preview.guidanceLive")) return;
        var mc = Minecraft.getInstance();
        try (var picture = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            var directory = mc.gameDirectory.toPath().resolve("screenshots");
            Files.createDirectories(directory);
            picture.writeToFile(directory.resolve(capture + ".png"));
            if (step == 1) step = 2;
            if (step == 4) step = 5;
            stepTicks = 0;
        } catch (Exception error) { PreviewQuiet.report("GUIDANCE_LIVE capture FAIL " + error); }
        capture = null;
    }
}
