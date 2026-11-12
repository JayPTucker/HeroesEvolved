package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.AbilitySlot;
import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.types.FlightAbility;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.flight.DiveBombService;
import com.jayptucker.heroesevolved.flight.FlightDynamicsService;
import com.jayptucker.heroesevolved.flight.FlightService;
import com.jayptucker.heroesevolved.flight.FlightSkillService;
import com.jayptucker.heroesevolved.skills.SkillCatalog;
import com.jayptucker.heroesevolved.skills.SkillTreeData;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.phys.Vec3;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Photographs the actual server abilities, with preparation and release frames for each maneuver. */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class FlightPreview {
    private static boolean started;
    private static int ticks, worldTicks;
    private static String pending;
    private static Vec3 burstOrigin;
    private static final List<Entity> SKILL_PROPS = new ArrayList<>();
    private static PlayerTeam skillTeam;

    private static void onServer(Minecraft mc, Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(mc.player.getUUID());
            if (player != null) action.accept(player);
        });
    }

    private static void place(ServerPlayer p, double x, double y, double z, float yaw, float pitch) {
        DiveBombService.forget(p);
        FlightSkillService.forget(p);
        FlightService.stopForPowerRemoval(p);
        FlightDynamicsService.forget(p);
        p.teleportTo(p.serverLevel(), x + .5, y, z + .5, yaw, pitch);
        p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        p.setOnGround(y <= -60);
        p.getAbilities().flying = false;
        p.onUpdateAbilities();
        p.resetFallDistance();
        PlayerEnergyService.restore(p, 999);
    }

    private static void skillStage(ServerPlayer p, String action, double y, float pitch) {
        for (Entity prop : SKILL_PROPS) prop.discard();
        SKILL_PROPS.clear();
        place(p, 400, y, 0, 0, pitch);
        // This preview exercises mutually exclusive upgrades in isolation; it is not a purchasable player build.
        var learned = SkillCatalog.nodes("flight").stream().map(SkillCatalog.Node::id).collect(Collectors.toSet());
        p.setData(ModDataAttachments.SKILL_TREES, SkillTreeData.empty().with(ModAbilities.FLIGHT_ID.toString(),
                new SkillTreeData.Build(5, learned, Map.of(), 6)));
        SkillTreeService.equip(p, AbilitySlot.PRIMARY, action);
        SkillTreeService.sync(p);
        if (skillTeam == null) skillTeam = p.getScoreboard().addPlayerTeam("flight-preview");
        p.getScoreboard().addPlayerToTeam(p.getScoreboardName(), skillTeam);
    }

    private static LivingEntity skillTarget(ServerPlayer p, double x, double y, double z, boolean friend) {
        var target = (friend ? EntityType.VILLAGER : EntityType.PILLAGER).create(p.serverLevel());
        target.setNoAi(true); target.setNoGravity(true); target.setPersistenceRequired();
        target.moveTo(x + .5, y, z + .5, 180, 0);
        p.serverLevel().addFreshEntity(target);
        if (friend) p.getScoreboard().addPlayerToTeam(target.getScoreboardName(), skillTeam);
        SKILL_PROPS.add(target);
        return target;
    }

    private static void skillArrow(ServerPlayer p, Entity owner, double x, double y, double z) {
        Arrow arrow = new Arrow(EntityType.ARROW, p.serverLevel());
        arrow.setOwner(owner); arrow.setPos(x + .5, y, z + .5); arrow.setDeltaMovement(0, 0, -.45);
        p.serverLevel().addFreshEntity(arrow); SKILL_PROPS.add(arrow);
    }

    private static void skillCast(ServerPlayer p, String id) {
        SkillCatalog.find("flight", id).orElseThrow().action().activate(new AbilityUseContext(p, ModAbilities.FLIGHT_ID, 5, false));
    }

    private static void releaseBurst(ServerPlayer player, String scene) {
        burstOrigin = player.position();
        FlightDynamicsService.releaseSkyburst(player);
        System.out.printf(java.util.Locale.ROOT, "SKYBURST %s release speed %.2f%n",
                scene, player.getDeltaMovement().length());
    }

    private static void reportBurst(Minecraft mc, String scene) {
        Vec3 client = mc.player.position();
        onServer(mc, p -> System.out.printf(java.util.Locale.ROOT,
                "SKYBURST %s server distance %.2f, client distance %.2f, active %b%n", scene,
                p.position().distanceTo(burstOrigin), client.distanceTo(burstOrigin), FlightDynamicsService.isBursting(p)));
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("heroes.preview.flight")) return;
        var mc = Minecraft.getInstance();
        ticks++;
        if (!started && ticks > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.options.hideGui = false;
            mc.options.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
            mc.options.guiScale().set(2);
            mc.resizeDisplay();
            mc.options.pauseOnLostFocus = false;
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("Flight-" + System.currentTimeMillis(),
                    new LevelSettings("Flight preview", GameType.CREATIVE, false, Difficulty.EASY, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(77L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        switch (++worldTicks) {
            case 200 -> {
                mc.options.hideGui = true;
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                onServer(mc, p -> {
                    p.serverLevel().setDayTime(1000);
                    p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty()
                            .assignDormant(ModAbilities.FLIGHT_ID).unlock(ModAbilities.FLIGHT_ID));
                    PlayerProgressionService.setLevel(p, 5);
                    place(p, 300, -60, 0, 0, 5);
                    for (double[] at : new double[][]{{300, 8}, {303, 10}, {297, 11}, {300, 14}, {300, -12}}) {
                        var target = EntityType.PILLAGER.create(p.serverLevel());
                        target.setNoAi(true); target.setPersistenceRequired();
                        target.moveTo(at[0] + .5, -60, at[1] + .5, 180, 0);
                        p.serverLevel().addFreshEntity(target);
                    }
                });
            }
            // Ground tap: the player's front camera sees the wave open behind the actual moving flier.
            case 230 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                onServer(mc, p -> { FlightDynamicsService.pressSkyburst(p); releaseBurst(p, "ground tap"); });
            }
            case 232 -> pending = "flight-1-skyburst-takeoff.png";
            case 235 -> pending = "flight-1-skyburst-wake.png";
            case 255 -> reportBurst(mc, "ground tap");
            // Hold Skyburst in the air: brake and coil, then change direction on release.
            case 265 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                onServer(mc, p -> { place(p, 300, -48, 0, 0, 0); FlightService.toggleFlight(p); FlightDynamicsService.pressSkyburst(p); });
            }
            case 285 -> pending = "flight-2-skyburst-charge.png";
            case 291 -> {
                mc.player.setYRot(-40);
                onServer(mc, p -> { p.setYRot(-40); releaseBurst(p, "aerial hold"); });
            }
            case 293 -> pending = "flight-2-skyburst-release.png";
            // Build airspeed in sustained flight. Two frames show the bank and the trail.
            case 315 -> {
                mc.options.keyUp.setDown(true);
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                onServer(mc, p -> {
                    place(p, 300, -40, -70, 0, 0);
                    FlightService.toggleFlight(p);
                    FlightService.setForwardInput(p, true);
                    for (int i = 0; i < 4; i++) FlightService.changeSpeed(p, 1);
                });
            }
            case 355 -> pending = "flight-3-cruise-vapor.png";
            case 360 -> {
                mc.player.setYRot(55);
                onServer(mc, p -> p.setYRot(55));
            }
            case 364 -> pending = "flight-3-banked-turn.png";
            // Dive: visible compression, then the plunge and ground-level pressure front.
            case 390 -> {
                mc.options.keyUp.setDown(false);
                onServer(mc, p -> { place(p, 300, -45, 0, 0, 65); DiveBombService.start(p, 5); });
            }
            case 396 -> pending = "flight-4-dive-charge.png";
            case 402 -> pending = "flight-4-dive-plunge.png";
            case 410 -> pending = "flight-4-dive-impact.png";
            case 417 -> pending = "flight-4-dive-pressure-front.png";
            // Grab and throw at close range.
            case 440 -> onServer(mc, p -> {
                place(p, 300, -60, -16, 0, 0);
                new FlightAbility().actions().get(AbilitySlot.QUATERNARY)
                        .activate(new AbilityUseContext(p, ModAbilities.FLIGHT_ID, 5, false));
            });
            case 450 -> pending = "flight-5-skyward-carry.png";
            case 455 -> onServer(mc, p -> new FlightAbility().actions().get(AbilitySlot.QUATERNARY)
                    .activate(new AbilityUseContext(p, ModAbilities.FLIGHT_ID, 5, false)));
            case 457 -> pending = "flight-5-skyward-throw.png";
            // Tempest: preparation is distinct from the active funnel and finishing release.
            case 485 -> onServer(mc, p -> { place(p, 300, -60, 4, 0, 0); FlightService.startCyclone(p); });
            case 497 -> pending = "flight-6-tempest-charge.png";
            case 525 -> pending = "flight-6-tempest-funnel.png";
            case 565 -> pending = "flight-6-tempest-steered.png";
            case 612 -> pending = "flight-6-tempest-finale.png";
            case 620 -> {
                mc.options.hideGui = false;
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                onServer(mc, p -> { place(p, 300, -40, 0, 0, 0); FlightService.toggleFlight(p); FlightDynamicsService.pressSkyburst(p); });
            }
            case 638 -> pending = "flight-7-first-person-skyburst.png";
            case 650 -> onServer(mc, p -> { FlightDynamicsService.cancelSkyburst(p); place(p, 300, -40, 0, 0, 65); DiveBombService.start(p, 5); });
            case 663 -> pending = "flight-7-first-person-dive.png";
            // Purchasable skills: use actual learned Node actions, with visible friends and hostile shots.
            case 680 -> {
                mc.options.hideGui = true;
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                onServer(mc, p -> {
                    skillStage(p, "flight_0_1", -60, -10);
                    skillTarget(p, 400, -55, 10, true);
                    skillTarget(p, 403, -60, 5, false);
                });
            }
            case 700 -> onServer(mc, p -> skillCast(p, "flight_0_1"));
            case 701 -> pending = "flight-skill-1-rescue-rush.png";
            case 706 -> pending = "flight-skill-1-tandem-lift.png";
            case 740 -> onServer(mc, p -> {
                skillStage(p, "flight_sky_catch", -60, 15);
                skillTarget(p, 401, -60, 6, true).setHealth(10);
                LivingEntity shooter = skillTarget(p, 398, -60, 8, false);
                skillArrow(p, shooter, 400, -59, 10);
            });
            case 745 -> onServer(mc, p -> skillCast(p, "flight_sky_catch"));
            case 747 -> pending = "flight-skill-2-wind-sanctuary.png";
            case 758 -> pending = "flight-skill-2-safe-harbor.png";
            case 785 -> onServer(mc, p -> {
                skillStage(p, "flight_1_1", -52, 0);
                FlightService.toggleFlight(p);
                LivingEntity shooter = skillTarget(p, 400, -52, 7, false);
                skillArrow(p, shooter, 400, -51, 2);
                skillCast(p, "flight_1_1");
            });
            case 789 -> pending = "flight-skill-3-barrel-roll.png";
            case 793 -> pending = "flight-skill-3-roll-finish.png";
            case 825 -> onServer(mc, p -> {
                skillStage(p, "flight_tailwind", -60, 4);
                FlightService.toggleFlight(p);
                skillTarget(p, 400, -60, 5, true);
                skillTarget(p, 401, -60, 8, false);
                skillCast(p, "flight_tailwind");
            });
            case 832 -> pending = "flight-skill-4-jetstream.png";
            case 847 -> pending = "flight-skill-4-draft-rider.png";
            case 875 -> onServer(mc, p -> {
                skillStage(p, "flight_2_1", -60, 15);
                skillTarget(p, 400, -60, 6, false);
                skillTarget(p, 402, -60, 6, false);
                skillCast(p, "flight_2_1");
            });
            case 879 -> pending = "flight-skill-5-thermal-warning.png";
            case 885 -> pending = "flight-skill-5-thermal-lance.png";
            case 893 -> pending = "flight-skill-5-rising-pressure.png";
            case 925 -> onServer(mc, p -> {
                skillStage(p, "flight_thunderclap", -60, 7);
                FlightService.toggleFlight(p); FlightDynamicsService.addMomentum(p, 100);
                for (double[] at : new double[][]{{400, 5}, {403, 6}, {397, 6}})
                    skillTarget(p, at[0], -60, at[1], false);
                skillCast(p, "flight_thunderclap");
            });
            case 929 -> pending = "flight-skill-6-vacuum-gather.png";
            case 935 -> pending = "flight-skill-6-vacuum-clap.png";
            case 947 -> pending = "flight-skill-6-sonic-bloom.png";
            case 952 -> onServer(mc, p -> p.serverLevel().setDayTime(16000));
            case 960 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                onServer(mc, p -> {
                    place(p, 400, -52, 0, 0, 0);
                    FlightService.toggleFlight(p);
                    FlightDynamicsService.pressSkyburst(p);
                });
            }
            case 977 -> pending = "flight-8-skyburst-night.png";
            // Capture the resulting loadout HUD, then close the isolated preview.
            case 980 -> {
                mc.options.hideGui = false;
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                onServer(mc, p -> { FlightSkillService.forget(p); FlightService.stopForPowerRemoval(p); });
            }
            case 1000 -> pending = "flight-skill-7-loadout-hud.png";
            case 1020 -> {
                mc.options.hideGui = true;
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                onServer(mc, p -> {
                    p.serverLevel().setDayTime(1000);
                    place(p, 400, -45, 0, 0, 0);
                    FlightService.toggleFlight(p);
                    FlightDynamicsService.addMomentum(p, 100);
                    FlightDynamicsService.pressSkyburst(p);
                });
            }
            case 1053 -> onServer(mc, p -> releaseBurst(p, "full charge + Momentum"));
            case 1056 -> pending = "flight-skyburst-sonic-boom.png";
            case 1060 -> pending = "flight-skyburst-full-wake.png";
            case 1085 -> reportBurst(mc, "full charge + Momentum");
            case 1090 -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                onServer(mc, p -> { p.serverLevel().setDayTime(16000); place(p, 400, -44, 0, 0, 0); });
            }
            case 1100 -> onServer(mc, p -> { FlightDynamicsService.pressSkyburst(p); releaseBurst(p, "night tap"); });
            case 1104 -> pending = "flight-skyburst-first-person-release.png";
            case 1120 -> mc.stop();
            default -> { }
        }
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post event) {
        if (pending == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(out.getParent());
            image.writeToFile(out);
            System.out.println("FLIGHT_CAPTURE " + pending);
        } catch (Exception ex) { throw new RuntimeException(ex); }
        pending = null;
    }
}
