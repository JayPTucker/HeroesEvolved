package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.client.CarryVisuals;
import com.jayptucker.heroesevolved.client.effects.TempestCamera;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.entity.EntityCarryService;
import com.jayptucker.heroesevolved.flight.FlightDynamicsService;
import com.jayptucker.heroesevolved.flight.FlightService;
import com.jayptucker.heroesevolved.network.PlayerPowerSyncService;
import com.jayptucker.heroesevolved.network.ClientFlightVisualState;
import com.jayptucker.heroesevolved.render.FlightPoseRenderer;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.story.prologue.PrologueService;
import com.jayptucker.heroesevolved.story.bunker.CryoChamberService;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

import java.nio.file.Files;
import java.util.function.Consumer;

/** Reproduces acceleration with a villager and a pig in the real client. */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class FlightCarryPreview {
    private static boolean started;
    private static int startup, age, photos;
    private static String pending;
    private static Mob cargo;
    private static int angle;

    private static void server(Consumer<ServerPlayer> action) {
        var mc = Minecraft.getInstance();
        var id = mc.player.getUUID();
        mc.getSingleplayerServer().execute(() -> {
            try {
                action.accept(mc.getSingleplayerServer().getPlayerList().getPlayer(id));
            } catch (Throwable failure) {
                PreviewQuiet.report("FLIGHT_CARRY FAIL: " + failure);
            }
        });
    }

    private static void stage(ServerPlayer p, EntityType<? extends Mob> type) {
        EntityCarryService.release(p);
        FlightService.stopForPowerRemoval(p);
        if (cargo != null) cargo.discard();
        var world = p.getServer().overworld();
        for (int x = 17; x <= 21; x++) for (int z = 17; z <= 27; z++) world.setChunkForced(x, z, true);
        p.teleportTo(world, 300.5, -48, 300.5, 0, 0);
        p.setDeltaMovement(Vec3.ZERO);
        p.setOnGround(false);
        p.removeAllEffects();
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty()
                .assignDormant(ModAbilities.FLIGHT_ID).unlock(ModAbilities.FLIGHT_ID));
        PlayerProgressionService.setLevel(p, 5);
        PlayerEnergyService.restore(p, 999);
        PlayerPowerSyncService.sync(p);
        world.setDayTime(6000);
        FlightService.toggleFlight(p);
        cargo = type.create(world);
        cargo.setNoAi(true);
        cargo.setPersistenceRequired();
        cargo.moveTo(300.5, -48, 302, 0, 0);
        world.addFreshEntity(cargo);
        if (!EntityCarryService.pickUp(p, cargo)) throw new IllegalStateException("Pickup " + type);
        PreviewQuiet.report("FLIGHT_CARRY staged " + type + " in " + world.dimension().location()
                + " on " + world.getBlockState(p.blockPosition().below()));
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("heroes.preview.flightCarry")) return;
        var mc = Minecraft.getInstance();
        if (!started && ++startup > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.options.hideGui = true;
            mc.options.pauseOnLostFocus = false;
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("FlightCarry-" + System.currentTimeMillis(),
                    new LevelSettings("Flight carry preview", GameType.CREATIVE, false, Difficulty.EASY,
                            true, rules, WorldDataConfiguration.DEFAULT), new WorldOptions(77L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT)
                            .createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        age++;
        if (age == 80) server(p -> { PrologueService.skip(p); CryoChamberService.release(p); });
        int scene = (age - 300) / 150, step = (age - 300) % 150;
        if (age >= 300 && scene < 2) {
            String name = scene == 0 ? "villager" : "pig";
            switch (step) {
                case 0 -> {
                    mc.options.keyUp.setDown(false);
                    mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                    angle = 0;
                    server(p -> stage(p, scene == 0 ? EntityType.VILLAGER : EntityType.PIG));
                }
                case 15 -> pending = name + "-hover.png";
                case 20 -> {
                    mc.options.keyUp.setDown(true);
                    server(p -> FlightService.setForwardInput(p, true));
                }
                case 25, 35, 45, 55 -> server(p -> FlightService.changeSpeed(p, 1));
                case 30 -> pending = name + "-accelerating.png";
                case 60 -> {
                    var pose = FlightPoseRenderer.frame(mc.player, 0);
                    boolean horizontal = ClientFlightVisualState.speedStep(mc.player.getId()) == 4
                            && pose != null && pose.pitch() > 80 && pose.amount() > .95;
                    PreviewQuiet.report("FLIGHT_CARRY " + (horizontal ? "PASS" : "FAIL")
                            + ": " + name + " horizontal at maximum cruise speed");
                    angle = 1;
                    pending = name + "-fast-front.png";
                }
                case 68 -> { angle = 2; pending = name + "-fast-back.png"; }
                case 75 -> {
                    mc.player.setYRot(35);
                    angle = 0;
                }
                case 80 -> pending = name + "-turn.png";
                case 85 -> {
                    mc.options.setCameraType(CameraType.FIRST_PERSON);
                    TempestCamera.fix(null, null);
                }
                case 90 -> pending = name + "-first-person.png";
                case 95 -> {
                    mc.options.keyUp.setDown(false);
                    mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                    server(p -> {
                        FlightDynamicsService.pressSkyburst(p);
                        FlightDynamicsService.releaseSkyburst(p);
                    });
                }
                case 100 -> pending = name + "-skyburst.png";
                case 120 -> pending = name + "-braking.png";
                case 130 -> {
                    var anchor = CarryVisuals.anchor(mc.player);
                    PreviewQuiet.report("FLIGHT_CARRY " + (anchor != null && anchor.getFirstPassenger() != null
                            ? "PASS" : "FAIL") + ": " + name + " attached after acceleration, turn and Skyburst");
                }
                default -> { }
            }
        }
        if (age == 605) {
            PreviewQuiet.report("FLIGHT_CARRY " + (photos == 16 ? "PASS" : "FAIL") + ": " + photos + "/16 photos");
            PreviewQuiet.quit();
        }
    }

    @SubscribeEvent public static void frame(RenderFrameEvent.Pre event) {
        if (!Boolean.getBoolean("heroes.preview.flightCarry")) return;
        var mc = Minecraft.getInstance();
        if (age < 300 || mc.player == null || mc.options.getCameraType().isFirstPerson()) return;
        float partial = mc.getTimer().getGameTimeDeltaPartialTick(false);
        Vec3 at = mc.player.getPosition(partial);
        Vec3 offset = switch (angle) {
            case 1 -> new Vec3(1, 1.8, 5);
            case 2 -> new Vec3(1, 1.8, -5);
            default -> new Vec3(4.5, 1.8, 2);
        };
        TempestCamera.fix(at.add(offset), at.add(0, 1, 0));
    }

    @SubscribeEvent public static void capture(RenderFrameEvent.Post event) {
        if (pending == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            var out = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(out.getParent());
            image.writeToFile(out);
            photos++;
        } catch (Exception failure) {
            PreviewQuiet.report("FLIGHT_CARRY FAIL: screenshot " + pending + ": " + failure);
        }
        pending = null;
    }
}
