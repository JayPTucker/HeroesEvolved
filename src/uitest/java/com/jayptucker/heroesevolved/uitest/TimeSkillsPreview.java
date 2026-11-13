package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.AbilitySlot;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.AbilityUseService;
import com.jayptucker.heroesevolved.cooldown.CooldownService;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.skills.SkillTreeData;
import com.jayptucker.heroesevolved.story.bunker.CryoChamberService;
import com.jayptucker.heroesevolved.time.SpaceTimeService;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.npc.Villager;
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
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Casts every Space-Time skill (and the passives that show) in a flat world well away from the spawn bunker, and saves
 * shots to {@code build/time-skills-preview/screenshots/}.
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class TimeSkillsPreview {
    private static final Set<String> TREE = Set.of("time_manipulation_0_0", "time_manipulation_0_1", "time_manipulation_0_2",
            "time_manipulation_stasis_seal", "time_manipulation_wider_seal", "time_manipulation_return_to_sender",
            "time_manipulation_1_0", "time_manipulation_1_1", "time_manipulation_1_2", "time_manipulation_shared_blink",
            "time_manipulation_far_fold", "time_manipulation_wayfinder", "time_manipulation_2_0", "time_manipulation_2_1",
            "time_manipulation_2_2", "time_manipulation_accelerate", "time_manipulation_era_radius", "time_manipulation_eras_end");
    private static final List<Pillager> ACTORS = new ArrayList<>();
    private static boolean started;
    private static int startup, age;
    private static String capture;

    private static void server(Minecraft mc, Consumer<ServerPlayer> action) {
        var id = mc.player.getUUID();
        var server = mc.getSingleplayerServer();
        server.execute(() -> action.accept(server.getPlayerList().getPlayer(id)));
    }

    private static void reset(ServerPlayer player, int x, float yaw, float pitch, long time) {
        SpaceTimeService.forget(player);
        CooldownService.clearAll(player);
        PlayerEnergyService.restore(player, 999);
        ACTORS.forEach(a -> a.discard());
        ACTORS.clear();
        player.serverLevel().setDayTime(time);
        player.teleportTo(player.serverLevel(), x + .5, -60, .5, yaw, pitch);
        player.setDeltaMovement(Vec3.ZERO);
        player.setHealth(player.getMaxHealth());
        player.removeAllEffects();
    }

    private static Pillager actor(ServerPlayer player, double dx, double dz, float yaw) {
        Pillager actor = EntityType.PILLAGER.create(player.serverLevel());
        actor.setNoAi(true);
        actor.setPersistenceRequired();
        actor.moveTo(player.getX() + dx, player.getY(), player.getZ() + dz, yaw, 0);
        actor.setYHeadRot(yaw); actor.yBodyRot = yaw;
        actor.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
        actor.setHealth(200);
        player.serverLevel().addFreshEntity(actor);
        ACTORS.add(actor);
        return actor;
    }

    private static void hit(ServerPlayer p, int actor, float amount) {
        if (actor >= ACTORS.size()) return;
        var t = ACTORS.get(actor);
        t.invulnerableTime = 0;
        t.hurt(p.damageSources().playerAttack(p), amount);
        p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
    }

    private static void cast(ServerPlayer player, AbilitySlot slot) {
        System.out.println("TIME_PREVIEW " + slot + " " + AbilityUseService.activateAssignedAction(player, slot, false));
    }

    private static void cast(ServerPlayer player, String action) {
        System.out.println("TIME_PREVIEW " + action + " " + AbilityUseService.activateAction(player, action, false));
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("heroes.preview.timeskills")) return;
        var mc = Minecraft.getInstance();
        if (!started && ++startup >= 35 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.options.pauseOnLostFocus = false;
            mc.options.fov().set(78);
            mc.options.renderDistance().set(6);
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("Time-" + System.currentTimeMillis(),
                    new LevelSettings("Time Skills Preview", GameType.CREATIVE, false, Difficulty.EASY, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(71L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.getSingleplayerServer() == null || mc.getOverlay() != null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        if (age >= 100) mc.getToasts().clear();
        switch (++age) {
            case 110 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                server(mc, p -> {
                    CryoChamberService.release(p);
                    p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(ModAbilities.TIME_MANIPULATION_ID).unlock(ModAbilities.TIME_MANIPULATION_ID));
                    p.setData(ModDataAttachments.POWER_INTRO_HISTORY, p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(ModAbilities.TIME_MANIPULATION_ID));
                    p.setData(ModDataAttachments.TIME_INTRO_SEEN, true);
                    PlayerProgressionService.setLevel(p, 5);
                    p.setData(ModDataAttachments.SKILL_TREES, SkillTreeData.empty().with(ModAbilities.TIME_MANIPULATION_ID.toString(),
                            new SkillTreeData.Build(15, TREE, Map.of(), 0)));
                    p.setGameMode(GameType.SURVIVAL);
                    reset(p, 210, 0, 0, 6000);
                });
            }
            // ---------------------------------------------------------- Selective Stasis, then the snap
            case 140 -> server(mc, p -> { actor(p, 2.2, 6, 200); p.teleportTo(p.serverLevel(), p.getX(), p.getY(), p.getZ(), -20, 0); cast(p, "time_manipulation_0_1"); });
            case 143 -> capture = "01-stasis-cast.png";
            case 150 -> capture = "02-stasis-held.png";
            case 152 -> server(mc, p -> { hit(p, 0, 4); hit(p, 0, 4); hit(p, 0, 4); });
            case 158 -> server(mc, p -> cast(p, "time_manipulation_0_1"));
            case 160 -> capture = "03-stasis-snap.png";
            case 163 -> capture = "04-stasis-release.png";
            case 180 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                server(mc, p -> { reset(p, 230, -35, 0, 6000); actor(p, 1.4, 2.0, 215); });
            }
            case 195 -> server(mc, p -> cast(p, "time_manipulation_0_1"));
            case 199 -> capture = "05-stasis-front.png";
            case 215 -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                server(mc, p -> { reset(p, 250, 0, 0, 6000); actor(p, .3, 6, 180); });
            }
            case 230 -> server(mc, p -> cast(p, "time_manipulation_0_1"));
            case 233 -> capture = "06-stasis-first-person.png";
            // ---------------------------------------------------------- Stasis Seal, filled, then thrown
            case 250 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                server(mc, p -> {
                    reset(p, 270, 0, 0, 13000);
                    for (int x = -3; x <= 3; x++) for (int y = 0; y < 5; y++)
                        p.serverLevel().setBlockAndUpdate(p.blockPosition().offset(x, y, 11), Blocks.STONE.defaultBlockState());
                });
            }
            case 270 -> server(mc, p -> cast(p, "time_manipulation_stasis_seal"));
            case 273 -> capture = "07-seal-frame.png";
            case 276 -> server(mc, p -> {
                for (double x : new double[] {-.6, 0, .6}) {
                    var arrow = EntityType.ARROW.create(p.serverLevel());
                    arrow.setPos(p.position().add(x, 1.6, 10));
                    arrow.setDeltaMovement(0, 0, -1.4);
                    p.serverLevel().addFreshEntity(arrow);
                }
                actor(p, -1.2, 9, 180);
            });
            case 286 -> capture = "08-seal-holding.png";
            case 290 -> server(mc, p -> { actor(p, 3, 16, 180); cast(p, "time_manipulation_stasis_seal"); });
            case 293 -> capture = "09-seal-thrown.png";
            case 297 -> capture = "10-seal-thrown-late.png";
            // ---------------------------------------------------------- Behind You
            case 320 -> server(mc, p -> { reset(p, 300, 0, 0, 6000); actor(p, .2, 7, 180); });
            case 335 -> server(mc, p -> cast(p, AbilitySlot.PRIMARY));
            case 338 -> capture = "11-behind-you-fold.png";
            case 342 -> server(mc, p -> hit(p, 0, 4));
            case 344 -> capture = "12-behind-you-strike.png";
            // ---------------------------------------------------------- Snap Shut (front view: the hole is behind you)
            case 365 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                server(mc, p -> { reset(p, 320, 0, 25, 6000); actor(p, 2.2, -.5, 90); actor(p, -2.2, .5, 270); });
            }
            case 380 -> server(mc, p -> cast(p, AbilitySlot.PRIMARY));
            case 383 -> capture = "13-snap-shut.png";
            case 387 -> capture = "14-snap-shut-flash.png";
            // ---------------------------------------------------------- Moment's Edge with Thousand Cuts
            case 410 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                server(mc, p -> { reset(p, 340, 0, 0, 6000); actor(p, 2, 4, 200); actor(p, -2, 7, 160); actor(p, 1, 10, 180); });
            }
            case 425 -> server(mc, p -> cast(p, "time_manipulation_shared_blink"));
            case 427 -> capture = "15-edge-first-cut.png";
            case 431 -> capture = "16-edge-second-cut.png";
            case 435 -> capture = "17-edge-last-cut.png";
            case 445 -> capture = "18-edge-held.png";
            case 475 -> capture = "19-edge-release.png";
            // ---------------------------------------------------------- Spatial Anchor, planted and recalled
            case 500 -> server(mc, p -> {
                reset(p, 360, 0, 30, 18000);
                var wolf = EntityType.WOLF.create(p.serverLevel());
                wolf.moveTo(p.position().add(1.2, 0, -.5));
                wolf.setTame(true, false); wolf.setOwnerUUID(p.getUUID()); wolf.setOrderedToSit(true);
                p.serverLevel().addFreshEntity(wolf);
            });
            case 515 -> server(mc, p -> SpaceTimeService.remember(p));
            case 519 -> capture = "20-anchor-planted.png";
            case 535 -> server(mc, p -> {
                p.teleportTo(p.serverLevel(), p.getX(), p.getY(), p.getZ() + 12, 180, 20);
                for (var wolf : p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.animal.Wolf.class, p.getBoundingBox().inflate(20), w -> true))
                    wolf.teleportTo(p.getX() + 1, p.getY(), p.getZ());
            });
            case 550 -> server(mc, p -> cast(p, "time_manipulation_1_1"));
            case 553 -> capture = "21-anchor-recall.png";
            // ---------------------------------------------------------- Second Chance, Shared Hour, Foreknowledge
            case 560 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                server(mc, p -> { reset(p, 380, 0, 10, 6000); actor(p, 3, 5, 200); });
            }
            case 640 -> server(mc, p -> {
                p.teleportTo(p.serverLevel(), p.getX(), p.getY(), p.getZ() + 5, 0, 10);
                if (!ACTORS.isEmpty()) p.hurt(p.damageSources().mobAttack(ACTORS.get(0)), 4);
            });
            case 700 -> server(mc, p -> cast(p, "time_manipulation_2_1"));
            case 702 -> capture = "22-second-chance-wind.png";
            case 707 -> capture = "23-second-chance-arrive.png";
            case 716 -> capture = "24-foreknowledge-mark.png";
            case 730 -> mc.options.setCameraType(CameraType.FIRST_PERSON);
            case 731 -> server(mc, p -> com.jayptucker.heroesevolved.network.ArmPosePayload.show(p, com.jayptucker.heroesevolved.ability.ArmPose.TIME_WIND, 20));
            case 736 -> capture = "25-second-chance-first-person.png";
            // ---------------------------------------------------------- Fast Forward: enemy, friend, growth
            case 760 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                server(mc, p -> {
                    reset(p, 400, 0, 0, 6000);
                    actor(p, 2.2, 6, 200).addEffect(new MobEffectInstance(MobEffects.POISON, 200, 1));
                    p.teleportTo(p.serverLevel(), p.getX(), p.getY(), p.getZ(), -20, 0);
                });
            }
            case 775 -> server(mc, p -> { hit(p, 0, 4); cast(p, "time_manipulation_accelerate"); });
            case 779 -> capture = "26-fast-forward-enemy.png";
            case 786 -> capture = "27-fast-forward-enemy-late.png";
            case 805 -> server(mc, p -> {
                reset(p, 420, 0, 0, 6000);
                Villager friend = EntityType.VILLAGER.create(p.serverLevel());
                friend.setNoAi(true);
                friend.moveTo(p.getX() + .2, p.getY(), p.getZ() + 5, 180, 0);
                p.serverLevel().addFreshEntity(friend);
                friend.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0));
            });
            case 820 -> server(mc, p -> cast(p, "time_manipulation_accelerate"));
            case 826 -> capture = "28-fast-forward-friend.png";
            case 850 -> server(mc, p -> reset(p, 440, 0, 35, 6000));
            case 865 -> server(mc, p -> cast(p, "time_manipulation_accelerate"));
            case 872 -> capture = "29-fast-forward-growth.png";
            case 875 -> mc.options.setCameraType(CameraType.FIRST_PERSON);
            case 876 -> server(mc, p -> { reset(p, 460, 0, 0, 6000); actor(p, .2, 6, 180); });
            case 890 -> server(mc, p -> cast(p, "time_manipulation_accelerate"));
            case 893 -> capture = "30-fast-forward-first-person.png";
            case 896 -> capture = "31-fast-forward-flick.png";
            // ---------------------------------------------------------- Moment's Edge in first person
            case 915 -> server(mc, p -> { reset(p, 480, 0, 0, 6000); actor(p, 2, 4, 200); actor(p, -2, 7, 160); });
            case 930 -> server(mc, p -> cast(p, "time_manipulation_shared_blink"));
            case 932 -> capture = "32-edge-first-person.png";
            case 960 -> mc.stop();
            default -> { }
        }
    }

    @SubscribeEvent public static void hud(RenderGuiEvent.Pre event) {
        if (age >= 100 && Boolean.getBoolean("heroes.preview.timeskills")) event.setCanceled(true);
    }

    @SubscribeEvent public static void frame(RenderFrameEvent.Post event) {
        if (capture == null) return;
        Minecraft mc = Minecraft.getInstance();
        Path path = mc.gameDirectory.toPath().resolve("screenshots").resolve(capture);
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Files.createDirectories(path.getParent());
            image.writeToFile(path);
            System.out.println("TIME_PREVIEW_CAPTURE " + capture);
        } catch (Exception e) {
            throw new IllegalStateException("Could not capture Space-Time preview", e);
        } finally {
            capture = null;
        }
    }
}
