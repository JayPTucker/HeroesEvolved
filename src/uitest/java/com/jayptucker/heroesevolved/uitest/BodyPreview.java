package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.config.ClientVisualSettings;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.intro.PowerIntroHistory;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.regeneration.BodyService;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

import java.nio.file.*;
import java.util.function.Consumer;

/** Severs limbs on a Level 1 regenerator and photographs each stage of regrowth, then the downed body. */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class BodyPreview {
    private static boolean started;
    private static int ticks, worldTicks;
    private static String pending;

    private static void onServer(Minecraft mc, Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> action.accept(server.getPlayerList().getPlayer(mc.player.getUUID())));
    }

    private static void level(ServerPlayer p, int level) {
        p.setData(ModDataAttachments.POWER_INTRO_HISTORY, PowerIntroHistory.empty().with(ModAbilities.REGENERATION_ID));
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty()
                .assignDormant(ModAbilities.REGENERATION_ID).unlock(ModAbilities.REGENERATION_ID));
        PlayerProgressionService.setLevel(p, level);
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        if (!Boolean.getBoolean("heroes.preview.body")) return;
        var mc = Minecraft.getInstance();
        ticks++;
        if (!started && ticks > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.options.hideGui = true;
            mc.options.pauseOnLostFocus = false;
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("Body-" + System.currentTimeMillis(),
                    new LevelSettings("Body preview", GameType.SURVIVAL, false, Difficulty.EASY, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(12L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        switch (++worldTicks) {
            case 200 -> { mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); onServer(mc, p -> {
                p.serverLevel().setDayTime(5000);
                level(p, 1);
                p.teleportTo(p.serverLevel(), 300.5, -60, 0.5, 200, 8);
                // Level 1: twelve seconds of regrowth after a one-second stump.
                BodyService.sever(p, BodyService.Limb.RIGHT_ARM, 1);
                BodyService.sever(p, BodyService.Limb.LEFT_LEG, 1);
            }); }
            case 210 -> pending = "body-1-stump.png";
            case 250 -> pending = "body-2-bone.png";
            case 330 -> pending = "body-3-muscle.png";
            case 400 -> pending = "body-4-skin.png";
            case 470 -> pending = "body-5-whole.png";
            // Gore off: the limb only fades.
            case 480 -> { ClientVisualSettings.SEVERED_LIMBS.set(false); onServer(mc, p -> BodyService.sever(p, BodyService.Limb.LEFT_ARM, 1)); }
            case 600 -> pending = "body-6-fade-only.png";
            case 610 -> ClientVisualSettings.SEVERED_LIMBS.set(true);
            // Downed: a lethal blow at Level 5, from the side, then her view from the ground with the HUD.
            case 700 -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                onServer(mc, p -> { level(p, 5); BodyService.forget(p); p.teleportTo(p.serverLevel(), 300.5, -60, 0.5, 270, 65);
                    BodyService.sever(p, BodyService.Limb.RIGHT_ARM, 5); p.hurt(p.damageSources().generic(), 1000); }); }
            case 720 -> pending = "body-7-downed.png";
            case 790 -> pending = "body-7-downed-regrowing.png";
            case 795 -> { mc.options.setCameraType(CameraType.FIRST_PERSON); mc.options.hideGui = false; }
            case 805 -> pending = "body-8-downed-view.png";
            case 810 -> { mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); mc.options.hideGui = true; }
            case 860 -> pending = "body-9-up-again.png";
            case 880 -> mc.stop();
            default -> { }
        }
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post e) {
        if (pending == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(out.getParent()); image.writeToFile(out);
            System.out.println("BODY_CAPTURE " + pending);
        } catch (Exception ex) { throw new RuntimeException(ex); }
        pending = null;
    }
}
