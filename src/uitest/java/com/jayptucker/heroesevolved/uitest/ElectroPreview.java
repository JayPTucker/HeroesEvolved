package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.electro.ElectroService;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

import java.nio.file.*;
import java.util.function.Consumer;

/** Casts each Electrokinesis power at dusk against still targets and photographs it mid-effect. */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class ElectroPreview {
    private static boolean started;
    private static int ticks, worldTicks;
    private static String pending;

    private static void onServer(Minecraft mc, Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> action.accept(server.getPlayerList().getPlayer(mc.player.getUUID())));
    }

    private static void place(ServerPlayer p, double x, double z, float yaw, float pitch) {
        p.teleportTo(p.serverLevel(), x + 0.5, -60, z + 0.5, yaw, pitch);
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        if (!Boolean.getBoolean("heroes.preview.electro")) return;
        var mc = Minecraft.getInstance();
        ticks++;
        if (!started && ticks > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.options.hideGui = true;
            mc.options.pauseOnLostFocus = false;
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("Electro-" + System.currentTimeMillis(),
                    new LevelSettings("Electro preview", GameType.CREATIVE, false, Difficulty.EASY, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(77L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        switch (++worldTicks) {
            case 200 -> onServer(mc, p -> {
                p.serverLevel().setDayTime(13400);
                p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty()
                        .assignDormant(ModAbilities.ELECTROKINESIS_ID).unlock(ModAbilities.ELECTROKINESIS_ID));
                PlayerProgressionService.setLevel(p, 5);
                p.getAbilities().flying = false; p.onUpdateAbilities();
                place(p, 300, 0, 0, 4);
                double[][] spots = {{300, 8}, {303, 11}, {297, 13}, {300, 16}};
                for (double[] at : spots) {
                    var pillager = EntityType.PILLAGER.create(p.serverLevel());
                    pillager.setNoAi(true); pillager.setInvulnerable(true); pillager.setPersistenceRequired();
                    pillager.moveTo(at[0] + 0.5, -60, at[1] + 0.5, 180, 0);
                    p.serverLevel().addFreshEntity(pillager);
                }
            });
            // Chain Lightning, tapped: a forked bolt leaping down the line.
            case 230 -> onServer(mc, p -> { PlayerEnergyService.restore(p, 999); ElectroService.chainLightning(p, 5); });
            case 231 -> pending = "electro-1-chain-a.png";
            case 233 -> pending = "electro-1-chain-b.png";
            // Live Current, held.
            case 250 -> onServer(mc, p -> ElectroService.press(p, 5));
            case 266 -> pending = "electro-2-stream-a.png";
            case 271 -> pending = "electro-2-stream-b.png";
            case 280 -> onServer(mc, ElectroService::release);
            // Ball Lightning in flight.
            case 290 -> onServer(mc, p -> { place(p, 300, 0, 18, 2); ElectroService.ballLightning(p, 5); });
            case 296 -> pending = "electro-3-ball-a.png";
            case 302 -> pending = "electro-3-ball-b.png";
            // Thunderstrike: the warning, then the strike.
            case 320 -> onServer(mc, p -> { place(p, 300, -6, 0, 12); PlayerEnergyService.restore(p, 999); ElectroService.thunderstrike(p, 5); });
            case 328 -> pending = "electro-4-strike-warning.png";
            case 337 -> pending = "electro-4-strike-a.png";
            case 339 -> pending = "electro-4-strike-b.png";
            // Charged: the body crackles (seen from the front).
            case 360 -> { mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); onServer(mc, p -> { place(p, 300, 0, 0, 10); ElectroService.fillCharge(p); }); }
            case 372 -> pending = "electro-5-overcharged.png";
            // Storm Surge: the wind-up and the release, from behind.
            case 380 -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); onServer(mc, p -> { place(p, 300, 4, 0, 18); ElectroService.startSurge(p, 5); }); }
            case 398 -> pending = "electro-6-surge-charge.png";
            case 411 -> pending = "electro-6-surge-release-a.png";
            case 413 -> pending = "electro-6-surge-release-b.png";
            // Ride the Lightning, from behind.
            case 440 -> onServer(mc, p -> { place(p, 290, -4, -60, 6); ElectroService.rideTheLightning(p, 5); });
            case 441 -> pending = "electro-7-ride-a.png";
            case 443 -> pending = "electro-7-ride-b.png";
            case 455 -> pending = "electro-7-scar.png";
            case 470 -> mc.stop();
            default -> { }
        }
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post e) {
        if (pending == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(out.getParent()); image.writeToFile(out);
            System.out.println("ELECTRO_CAPTURE " + pending);
        } catch (Exception ex) { throw new RuntimeException(ex); }
        pending = null;
    }
}
