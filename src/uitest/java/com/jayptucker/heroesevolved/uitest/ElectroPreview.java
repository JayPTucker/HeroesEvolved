package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.electro.BlackoutService;
import com.jayptucker.heroesevolved.electro.ElectroService;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.skills.SkillActionService;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
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
    private static boolean cleanHud;

    private static void onServer(Minecraft mc, Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> action.accept(server.getPlayerList().getPlayer(mc.player.getUUID())));
    }

    private static void place(ServerPlayer p, double x, double z, float yaw, float pitch) {
        p.teleportTo(p.serverLevel(), x + 0.5, -60, z + 0.5, yaw, pitch);
    }

    private static void target(ServerPlayer p, double x, double z) {
        var pillager = EntityType.PILLAGER.create(p.serverLevel());
        pillager.setNoAi(true);
        pillager.setInvulnerable(true);
        pillager.setPersistenceRequired();
        pillager.moveTo(x + .5, -60, z + .5, 180, 0);
        p.serverLevel().addFreshEntity(pillager);
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        if (!Boolean.getBoolean("heroes.preview.electro")) return;
        var mc = Minecraft.getInstance();
        ticks++;
        if (!started && ticks > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.options.hideGui = false;
            mc.options.pauseOnLostFocus = false;
            mc.options.fov().set(84);
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
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
        if (cleanHud) mc.getToasts().clear();
        switch (++worldTicks) {
            case 200 -> {
                cleanHud = true;
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                onServer(mc, p -> {
                    com.jayptucker.heroesevolved.story.bunker.CryoChamberService.release(p);
                    p.serverLevel().setDayTime(11750);
                    p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty()
                            .assignDormant(ModAbilities.ELECTROKINESIS_ID).unlock(ModAbilities.ELECTROKINESIS_ID));
                    p.setData(ModDataAttachments.POWER_INTRO_HISTORY,
                            p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(ModAbilities.ELECTROKINESIS_ID));
                    PlayerProgressionService.setLevel(p, 5);
                    p.getAbilities().flying = false; p.onUpdateAbilities();
                    place(p, 300, 0, 0, 4);
                    double[][] spots = {{300, 6}, {304, 8}, {301, 11}, {297, 10}};
                    for (double[] at : spots) target(p, at[0], at[1]);
                });
            }
            // Chain Lightning, tapped: a forked bolt leaping down the line.
            case 226 -> onServer(mc, p -> { PlayerEnergyService.restore(p, 999); ElectroService.press(p, 5); });
            case 229 -> pending = "electro-1-chain-draw.png";
            case 230 -> onServer(mc, ElectroService::release);
            case 234 -> pending = "electro-1-chain-a.png";
            case 236 -> pending = "electro-1-chain-b.png";
            // Live Current, held.
            case 250 -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                onServer(mc, p -> { place(p, 300, 2, 0, 4); ElectroService.press(p, 5); });
            }
            case 266 -> pending = "electro-2-stream-a.png";
            case 271 -> pending = "electro-2-stream-b.png";
            case 280 -> onServer(mc, p -> {
                if (!ElectroService.isHolding(p)) throw new AssertionError("Live Current must hold until release");
                ElectroService.release(p);
                System.out.println("ELECTRO_CLIENT_CURRENT PASS");
            });
            // Ball Lightning in flight.
            case 290 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                onServer(mc, p -> {
                    place(p, 470, 0, 0, 4);
                    if (!ElectroService.ballLightning(p, 5)) throw new AssertionError("Ball Lightning did not cast");
                });
            }
            case 294 -> onServer(mc, p -> place(p, 473, 0, 27, 3));
            case 296 -> pending = "electro-3-ball-a.png";
            case 302 -> pending = "electro-3-ball-b.png";
            case 303 -> onServer(mc, p -> { place(p, 470, 0, 0, 4); ElectroService.chainLightning(p, 5); });
            case 305 -> onServer(mc, p -> place(p, 474, 0, 28, 5));
            case 307 -> pending = "electro-3-ball-prime.png";
            case 313 -> pending = "electro-3-ball-burst.png";
            case 315 -> onServer(mc, p -> {
                if (ElectroService.hasOrb(p)) throw new AssertionError("Chain Lightning must burst the orb it hits");
                System.out.println("ELECTRO_CLIENT_RELAY PASS");
            });
            // Thunderstrike: the warning, then the strike.
            case 320 -> onServer(mc, p -> {
                p.serverLevel().setDayTime(12520);
                place(p, 560, 0, 0, 7);
                PlayerEnergyService.restore(p, 999);
                ElectroService.fillCharge(p);
                if (!ElectroService.thunderstrike(p, 5) || ElectroService.pendingStrikes(p) != 3)
                    throw new AssertionError("Overcharged Thunderstrike must schedule three strikes");
                System.out.println("ELECTRO_CLIENT_STRIKE PASS");
            });
            case 328 -> pending = "electro-4-strike-warning.png";
            case 337 -> pending = "electro-4-strike-a.png";
            case 339 -> pending = "electro-4-strike-b.png";
            case 346 -> pending = "electro-4-strike-followup.png";
            // Charged: the body crackles (seen from the front).
            case 360 -> { mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); onServer(mc, p -> { place(p, 300, 0, 0, 10); ElectroService.fillCharge(p); }); }
            case 372 -> pending = "electro-5-overcharged.png";
            // Storm Surge: the wind-up and the release, from behind.
            case 380 -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); onServer(mc, p -> {
                place(p, 300, 5, 0, 14);
                if (!ElectroService.startSurge(p, 5)) throw new AssertionError("Storm Surge did not start");
            }); }
            case 392 -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            case 398 -> pending = "electro-6-surge-charge.png";
            case 408 -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            case 411 -> pending = "electro-6-surge-release-a.png";
            case 419 -> pending = "electro-6-surge-release-b.png";
            // Ride the Lightning, from behind.
            case 437 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                onServer(mc, p -> { place(p, 670, 0, -60, 4); ElectroService.fillCharge(p); });
            }
            case 440 -> onServer(mc, p -> {
                Vec3 start = p.position();
                if (!ElectroService.rideTheLightning(p, 5) || p.position().distanceTo(start) < 10)
                    throw new AssertionError("Ride the Lightning did not travel along the open route");
                System.out.println("ELECTRO_CLIENT_RIDE PASS distance=" + p.position().distanceTo(start));
            });
            case 441 -> pending = "electro-7-ride-a.png";
            case 443 -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.player.setYRot(120);
                mc.player.setXRot(15);
                pending = "electro-7-ride-b.png";
            }
            case 455 -> pending = "electro-7-scar.png";
            case 470 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                onServer(mc, p -> {
                    place(p, 775, 0, 0, 13);
                    for (int x : new int[]{-4, 0, 4}) for (int z : new int[]{4, 8})
                        p.serverLevel().setBlockAndUpdate(new BlockPos(775 + x, -60, z), Blocks.REDSTONE_BLOCK.defaultBlockState());
                    target(p, 777, 8);
                    if (!BlackoutService.activate(p, 5)) throw new AssertionError("Blackout did not start");
                });
            }
            case 484 -> pending = "electro-8-blackout-siphon.png";
            case 491 -> pending = "electro-8-blackout-release.png";
            case 499 -> pending = "electro-8-blackout-wave.png";
            case 506 -> onServer(mc, p -> {
                if (BlackoutService.winding(p) || ElectroService.charge(p) < 20) throw new AssertionError("Blackout must return Charge");
                System.out.println("ELECTRO_CLIENT_BLACKOUT PASS charge=" + ElectroService.charge(p));
            });
            case 530 -> onServer(mc, p -> {
                p.serverLevel().setDayTime(1000);
                place(p, 890, 0, 0, 13);
                SkillActionService.activate(new AbilityUseContext(p, ModAbilities.ELECTROKINESIS_ID, 5, false), 1);
            });
            case 543 -> pending = "electro-9-static-screen.png";
            case 560 -> mc.stop();
            default -> { }
        }
    }

    @SubscribeEvent public static void hud(RenderGuiEvent.Pre event) {
        if (cleanHud && Boolean.getBoolean("heroes.preview.electro")) event.setCanceled(true);
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
