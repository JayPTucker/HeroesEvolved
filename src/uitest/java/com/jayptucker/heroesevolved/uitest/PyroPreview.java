package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.network.PlayerPowerSyncService;
import com.jayptucker.heroesevolved.network.ClientFlightVisualState;
import com.jayptucker.heroesevolved.network.FlightSpeedInputPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import com.jayptucker.heroesevolved.input.ModKeyMappings;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.pyro.PyroFlightService;
import com.jayptucker.heroesevolved.pyro.PyroService;
import com.jayptucker.heroesevolved.pyro.PhoenixService;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/** Uses the real survival client for cruise/hover checks, then photographs actual fire casts. */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class PyroPreview {
    private static boolean started;
    private static int ticks, world;
    private static String pending;
    private static Vec3 cruiseStart, hoverStart, noviceHoverStart;
    private static Mob spearTarget, meteorTarget, skillTarget;

    private static void onServer(Minecraft mc, Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            var p = server.getPlayerList().getPlayer(mc.player.getUUID());
            if (p != null) action.accept(p);
        });
    }

    private static void place(ServerPlayer p, double y, float pitch) {
        PyroFlightService.stop(p, false);
        PyroService.stopPalm(p);
        PyroService.cancelInferno(p);
        PyroService.cancelSpear(p);
        p.connection.teleport(300.5, y, 0.5, 0, pitch);
        p.setDeltaMovement(Vec3.ZERO);
        p.resetFallDistance();
        PlayerEnergyService.restore(p, 999);
    }

    private static Mob target(ServerPlayer p, double x, double z) {
        var target = EntityType.PILLAGER.create(p.serverLevel());
        target.setNoAi(true);
        target.setPersistenceRequired();
        target.moveTo(x, -60, z, 180, 0);
        p.serverLevel().addFreshEntity(target);
        return target;
    }

    private static com.jayptucker.heroesevolved.ability.AbilityUseContext skillContext(ServerPlayer p) {
        return new com.jayptucker.heroesevolved.ability.AbilityUseContext(p, ModAbilities.PYROKINESIS_ID, 5, false);
    }

    private static void skillScene(ServerPlayer p, double x, float pitch) {
        PyroService.forget(p);
        place(p, -60, pitch);
        p.connection.teleport(x, -60, 0.5, 0, pitch);
        p.setHealth(p.getMaxHealth());
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("heroes.preview.pyro")) return;
        var mc = Minecraft.getInstance();
        if (!started && ++ticks > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.options.pauseOnLostFocus = false;
            mc.options.guiScale().set(2);
            mc.options.hideGui = false;
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("Pyro-" + System.currentTimeMillis(),
                    new LevelSettings("Pyrokinesis preview", GameType.SURVIVAL, false, Difficulty.EASY,
                            true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(77L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT)
                            .createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        ++world;
        if (world == 211 && Boolean.getBoolean("heroes.preview.pyro.skillsOnly")) world = 544;
        // A gradual real view turn exercises Flight's bank while the camera remains level.
        if (world >= 252 && world <= 262) mc.player.setYRot(mc.player.getYRot() + 5);
        switch (world) {
            case 180 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                onServer(mc, p -> {
                    com.jayptucker.heroesevolved.story.bunker.CryoChamberService.release(p);
                    p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty()
                            .assignDormant(ModAbilities.PYROKINESIS_ID).unlock(ModAbilities.PYROKINESIS_ID));
                    p.setData(ModDataAttachments.POWER_INTRO_HISTORY,
                            p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(ModAbilities.PYROKINESIS_ID));
                    PlayerProgressionService.setLevel(p, 5);
                    PlayerPowerSyncService.sync(p);
                    p.serverLevel().setDayTime(12000);
                    p.serverLevel().setWeatherParameters(6000, 0, false, false);
                    place(p, -45, 0);
                    PyroService.addHeat(p, 35);

                });
            }
            // Start flight through the same key transition as a real player.
            case 190 -> ModKeyMappings.ABILITY_4.setDown(true);
            case 191 -> ModKeyMappings.ABILITY_4.setDown(false);
            case 210 -> onServer(mc, p -> {
                if (!PyroFlightService.isFlying(p)) throw new AssertionError("X did not start survival flame flight");
                System.out.println("PYRO_CLIENT_TOGGLE PASS");
            });
            case 220 -> {
                mc.options.hideGui = true;
                mc.gui.getChat().clearMessages(true);
                mc.getToasts().clear();
                pending = "pyro-1-hover-jets.png";
            }
            case 225 -> {
                PacketDistributor.sendToServer(new FlightSpeedInputPayload(1));
                PacketDistributor.sendToServer(new FlightSpeedInputPayload(1));
                PacketDistributor.sendToServer(new FlightSpeedInputPayload(1));
            }
            case 230 -> {
                onServer(mc, p -> cruiseStart = p.position());
                mc.options.keyUp.setDown(true);
            }
            case 237 -> pending = "pyro-2-flame-cruise.png";
            case 238 -> mc.options.keySprint.setDown(true);
            case 240 -> {
                mc.player.setXRot(-12);
                mc.options.keyRight.setDown(true);
            }
            case 250 -> {
                if (!ClientFlightVisualState.isFlightActive(mc.player.getId()))
                    throw new AssertionError("Pyro cruise is not using Flight's shared horizontal pose");
                pending = "pyro-2a-afterburner.png";
            }
            case 253 -> onServer(mc, p -> {
                double speed = p.getDeltaMovement().length();
                if (!PyroFlightService.isBoosting(p) || speed <= 3.9)
                    throw new AssertionError("Afterburner did not reach full thrust: " + speed);
                System.out.println("PYRO_CLIENT_AFTERBURNER PASS speed=" + speed);
            });
            case 258 -> {
                pending = "pyro-2b-banked-turn.png";
                mc.options.keySprint.setDown(false);
            }
            case 270 -> {
                mc.options.keyUp.setDown(false);
                mc.options.keyRight.setDown(false);
                onServer(mc, p -> {
                    double moved = p.position().distanceTo(cruiseStart);
                    if (!PyroFlightService.isFlying(p) || moved < 12 || PyroFlightService.speedStep(p) != 4
                            || p.getY() - cruiseStart.y < 1 || PyroFlightService.momentum(p) <= 5)
                        throw new AssertionError("Real client flame cruise failed: moved=" + moved
                                + " speed=" + PyroFlightService.speedStep(p) + " momentum=" + PyroFlightService.momentum(p));
                    System.out.println("PYRO_CLIENT_CRUISE PASS moved=" + moved + " momentum=" + PyroFlightService.momentum(p));
                });
            }
            case 275 -> {
                for (int notch = 0; notch < 4; notch++)
                    PacketDistributor.sendToServer(new FlightSpeedInputPayload(-1));
            }
            case 285 -> onServer(mc, p -> {
                if (PyroFlightService.speedStep(p) != 0 || p.getDeltaMovement().length() > 0.1)
                    throw new AssertionError("Wheel-down did not brake Pyro cruise into a hover");
                System.out.println("PYRO_CLIENT_WHEEL_BRAKE PASS");
            });
            case 290 -> onServer(mc, p -> hoverStart = p.position());
            case 310 -> onServer(mc, p -> {
                double drift = p.position().distanceTo(hoverStart);
                if (!PyroFlightService.isFlying(p) || drift > 1)
                    throw new AssertionError("Real client flame hover failed: drift=" + drift);
                System.out.println("PYRO_CLIENT_HOVER PASS drift=" + drift);
                PyroService.addHeat(p, 100);
                PyroFlightService.sync(p);
            });
            case 318 -> pending = "pyro-3-blue-hover.png";
            case 320 -> onServer(mc, p -> {
                noviceHoverStart = p.position();
                PlayerProgressionService.setLevel(p, 3);
                PlayerPowerSyncService.sync(p);
            });
            case 330 -> pending = "pyro-3b-unsteady-hover.png";
            case 333 -> onServer(mc, p -> {
                double drift = p.position().distanceTo(noviceHoverStart);
                if (!PyroFlightService.isFlying(p) || drift <= 0.05 || drift >= 2)
                    throw new AssertionError("Novice hover must visibly drift within control: drift=" + drift);
                System.out.println("PYRO_CLIENT_NOVICE_HOVER PASS drift=" + drift);
            });
            case 335 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                onServer(mc, p -> {
                    PlayerProgressionService.setLevel(p, 5);
                    PlayerPowerSyncService.sync(p);
                    place(p, -60, 0);
                    spearTarget = target(p, 300.5, 8.5);
                    target(p, 300.5, 11.5);
                    PyroService.startSpear(p, 5);
                });
            }
            case 354 -> pending = "pyro-4-cinder-spear-charge.png";
            case 357 -> onServer(mc, p -> PyroService.releaseSpear(p));
            case 361 -> pending = "pyro-5-cinder-spear-release.png";
            case 375 -> onServer(mc, p -> {
                if (spearTarget.getHealth() >= spearTarget.getMaxHealth())
                    throw new AssertionError("Preview Cinder Spear did not hit target");
                System.out.println("PYRO_CLIENT_SPEAR PASS health=" + spearTarget.getHealth());
            });
            case 400 -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                onServer(mc, p -> {
                    place(p, -53, 27);
                    PyroService.addHeat(p, 100);
                    if (!PyroFlightService.toggle(p, 5)) throw new AssertionError("Cannot hover for Sunfall preview");
                    PyroService.startInferno(p, 5);
                });
            }
            case 440 -> pending = "pyro-6-sunfall-charge.png";
            case 465 -> pending = "pyro-7-sunfall-drop.png";
            case 475 -> pending = "pyro-8-sunfall-impact.png";
            case 480 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                onServer(mc, p -> {
                    place(p, -48, 80);
                    // Separate ground keeps Sunfall's lingering fire out of the dive check.
                    p.connection.teleport(600.5, -48, 0.5, 0, 80);
                    PyroService.addHeat(p, 80);
                    meteorTarget = target(p, 600.5, 3.5);
                });
            }
            case 485 -> mc.options.keyJump.setDown(true);
            case 486 -> mc.options.keyJump.setDown(false);
            case 488 -> mc.options.keyJump.setDown(true);
            case 489 -> mc.options.keyJump.setDown(false);
            case 492 -> onServer(mc, p -> {
                if (!PyroFlightService.isFlying(p)) throw new AssertionError("Double-tap Jump did not ignite Pyro jets");
                System.out.println("PYRO_CLIENT_DOUBLE_TAP PASS");
            });
            case 495 -> {
                mc.options.keyShift.setDown(true);
                ModKeyMappings.ABILITY_4.setDown(true);
            }
            case 496 -> {
                ModKeyMappings.ABILITY_4.setDown(false);
                mc.options.keyShift.setDown(false);
            }
            case 499 -> onServer(mc, p -> {
                if (!PyroFlightService.isDiving(p)) throw new AssertionError("Real Shift + X did not start Meteor Dive");
                System.out.println("PYRO_CLIENT_METEOR_INPUT PASS");
            });
            case 501 -> pending = "pyro-9-meteor-windup.png";
            case 508 -> pending = "pyro-9b-meteor-comet.png";
            case 516 -> pending = "pyro-10-meteor-impact.png";
            case 525 -> onServer(mc, p -> {
                if (PyroFlightService.isFlying(p) || meteorTarget.getHealth() >= meteorTarget.getMaxHealth())
                    throw new AssertionError("Real Meteor Dive did not end with damaging impact: flying="
                            + PyroFlightService.isFlying(p) + " health=" + meteorTarget.getHealth());
                System.out.println("PYRO_CLIENT_METEOR_IMPACT PASS health=" + meteorTarget.getHealth());
            });
            case 545 -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                onServer(mc, p -> {
                    skillScene(p, 900.5, 0);
                    skillTarget = target(p, 900.5, 7.5);
                });
            }
            case 560 -> onServer(mc, p -> {
                if (!PyroService.skill(skillContext(p), 0)) throw new AssertionError("Preview Ember Lash did not cast");
            });
            case 563 -> pending = "pyro-11-ember-lash.png";
            case 573 -> onServer(mc, p -> {
                if (skillTarget.getHealth() >= skillTarget.getMaxHealth() || PyroService.isLashing(p))
                    throw new AssertionError("Lash must damage its victim and clear its reel");
                System.out.println("PYRO_CLIENT_LASH PASS");
            });
            case 580 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                onServer(mc, p -> skillScene(p, 1050.5, 0));
            }
            case 590 -> onServer(mc, p -> {
                PyroService.addHeat(p, 75);
                PyroService.skill(skillContext(p), 1);
                for (int i = 0; i < 4; i++) {
                    var shot = new net.minecraft.world.entity.projectile.Arrow(EntityType.ARROW, p.serverLevel());
                    shot.setNoGravity(true); shot.setPos(p.position().add(1.5, 1 + i * 0.1, 0));
                    p.serverLevel().addFreshEntity(shot);
                }
            });
            case 600 -> pending = "pyro-12-heat-mantle.png";
            case 608 -> onServer(mc, p -> {
                if (!PyroService.hasMantle(p) || PyroService.heat(p) <= 81)
                    throw new AssertionError("Mantle must catch shots and reclaim their Heat");
                System.out.println("PYRO_CLIENT_MANTLE PASS heat=" + PyroService.heat(p));
            });
            case 625 -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                onServer(mc, p -> {
                    skillScene(p, 1200.5, 0);
                    var friend = EntityType.VILLAGER.create(p.serverLevel());
                    friend.setNoAi(true); friend.setPersistenceRequired();
                    friend.moveTo(1200.5, -60, 4.5, 180, 0); friend.setHealth(5);
                    friend.igniteForSeconds(6); p.serverLevel().addFreshEntity(friend);
                    skillTarget = friend;
                    PyroService.addHeat(p, 30);
                });
            }
            case 640 -> onServer(mc, p -> PyroService.skill(skillContext(p), 2));
            case 646 -> pending = "pyro-13-cauterizing-phoenix.png";
            case 652 -> onServer(mc, p -> {
                if (skillTarget.isOnFire() || skillTarget.getHealth() <= 5 || skillTarget.getAbsorptionAmount() < 4)
                    throw new AssertionError("Friendly cauterization must heal, extinguish and shield");
                System.out.println("PYRO_CLIENT_CAUTERIZE PASS");
            });
            case 670 -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                onServer(mc, p -> {
                    skillScene(p, 1350.5, 0);
                    skillTarget = target(p, 1350.5, 7.5);
                    target(p, 1350.5, 10.5);
                });
            }
            case 685 -> onServer(mc, p -> PyroService.technique(skillContext(p), "blue_lance"));
            case 689 -> pending = "pyro-14-blue-lance-drill.png";
            case 705 -> onServer(mc, p -> {
                if (skillTarget.getHealth() >= skillTarget.getMaxHealth() || PyroService.isLancing(p))
                    throw new AssertionError("Blue Lance must hit and finish its fixed drill seam");
                System.out.println("PYRO_CLIENT_LANCE PASS");
            });
            case 720 -> onServer(mc, p -> {
                skillScene(p, 1500.5, 55);
                skillTarget = target(p, 1500.5, 4.5);
            });
            case 735 -> onServer(mc, p -> PyroService.technique(skillContext(p), "ember_mine"));
            case 739 -> pending = "pyro-15-ember-mine-sigil.png";
            case 746 -> onServer(mc, p -> skillTarget.setPos(p.position().add(0, 0, 1.4)));
            case 750 -> pending = "pyro-16-ember-mine-warning.png";
            case 756 -> pending = "pyro-17-ember-mine-eruption.png";
            case 765 -> onServer(mc, p -> {
                if (skillTarget.getHealth() >= skillTarget.getMaxHealth() || PyroService.mineCount(p) != 0)
                    throw new AssertionError("Warned Ember Mine must erupt and consume itself");
                System.out.println("PYRO_CLIENT_MINE PASS");
            });
            case 780 -> onServer(mc, p -> {
                skillScene(p, 1650.5, 0);
                for (int i = -2; i <= 2; i++) {
                    var fire = net.minecraft.core.BlockPos.containing(p.position().add(i * 1.2, 0, 3));
                    p.serverLevel().setBlock(fire, net.minecraft.world.level.block.Blocks.FIRE.defaultBlockState(), 3);
                }
            });
            case 795 -> onServer(mc, p -> PyroService.technique(skillContext(p), "fire_shepherd"));
            case 798 -> pending = "pyro-18-fire-shepherd.png";
            case 810 -> onServer(mc, p -> {
                if (PyroService.heat(p) < 10) throw new AssertionError("Fire Shepherd must turn drawn fire into Heat");
                System.out.println("PYRO_CLIENT_SHEPHERD PASS");
            });
            case 825 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                onServer(mc, p -> {
                    p.serverLevel().setDayTime(1000);
                    skillScene(p, 1800.5, 18);
                    p.setShiftKeyDown(true);
                    PyroService.ringOfFire(p, 5);
                    p.setShiftKeyDown(false);
                });
            }
            case 831 -> pending = "pyro-19a-firestorm-warning.png";
            case 842 -> pending = "pyro-19b-firestorm-vortex.png";
            case 845 -> onServer(mc, p -> PyroService.startPalm(p, 5));
            case 854 -> pending = "pyro-19c-dragon-breath.png";
            case 855 -> mc.options.setCameraType(CameraType.FIRST_PERSON);
            case 858 -> pending = "pyro-19d-dragon-breath-first-person.png";
            case 862 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                onServer(mc, p -> { PyroService.stopPalm(p); PyroService.collapseRing(p, 5); });
            }
            case 875 -> pending = "pyro-20-firestorm-collapse.png";
            case 902 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                onServer(mc, p -> {
                    p.serverLevel().setDayTime(16000);
                    skillScene(p, 1950.5, -15);
                    target(p, 1951.5, 12.5).igniteForSeconds(6);
                    target(p, 1948.5, 14.5);
                    PhoenixService.activate(p, 5);
                });
            }
            case 921 -> pending = "pyro-21a-phoenix-rise.png";
            case 926 -> pending = "pyro-21-phoenix-wings.png";
            case 935 -> pending = "pyro-21b-phoenix-swoop.png";
            case 948 -> pending = "pyro-22-phoenix-hunt.png";
            case 970 -> {
                if (Boolean.getBoolean("heroes.preview.pyro.skillsOnly")) {
                    mc.stop();
                    break;
                }
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                onServer(mc, p -> {
                    p.serverLevel().setDayTime(1000);
                    place(p, -51, 38);
                    if (!PyroFlightService.toggle(p, 5)) throw new AssertionError("Cannot hover over Sunfall's aftermath");
                });
            }
            case 997 -> {
                long flames = BlockPos.betweenClosedStream(new BlockPos(290, -60, 5), new BlockPos(310, -59, 29))
                        .filter(pos -> mc.level.getBlockState(pos).is(Blocks.SOUL_FIRE)).count();
                if (flames == 0) throw new AssertionError("Sunfall must leave visible Soul Fire on the grass");
                System.out.println("PYRO_CLIENT_SOUL_FIRE PASS blocks=" + flames);
                pending = "pyro-23-soul-fire-aftermath.png";
            }
            case 1005 -> mc.stop();
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
            System.out.println("PYRO_CAPTURE " + out);
        } catch (Exception ex) { throw new RuntimeException(ex); }
        pending = null;
    }
}
