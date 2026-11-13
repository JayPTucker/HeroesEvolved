package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.AbilitySlot;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.AbilityUseService;
import com.jayptucker.heroesevolved.cooldown.CooldownService;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.skills.SkillTreeData;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.story.bunker.CryoChamberService;
import com.jayptucker.heroesevolved.vibe.VibeService;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
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
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Runs the actual ability keys, every optional active, the class abilities and the awakening in a flat world well outside the spawn bunker. */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class VibePreview {
    private static boolean started;
    private static int startup, age;
    private static String capture;

    private static void server(Minecraft mc, Consumer<ServerPlayer> action) {
        var id = mc.player.getUUID();
        var server = mc.getSingleplayerServer();
        server.execute(() -> action.accept(server.getPlayerList().getPlayer(id)));
    }

    private static void reset(ServerPlayer player, int x, float yaw, float pitch, long time) {
        VibeService.forget(player);
        CooldownService.clearAll(player);
        PlayerEnergyService.restore(player, 999);
        player.serverLevel().setDayTime(time);
        player.teleportTo(player.serverLevel(), x + .5, -60, .5, yaw, pitch);
        player.setDeltaMovement(Vec3.ZERO);
    }

    private static Pillager actor(ServerPlayer player, double dx, double dz) {
        Pillager actor = EntityType.PILLAGER.create(player.serverLevel());
        actor.setNoAi(true);
        actor.setPersistenceRequired();
        actor.moveTo(player.position().add(dx, 0, dz));
        actor.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        actor.setHealth(100);
        player.serverLevel().addFreshEntity(actor);
        return actor;
    }

    private static void cast(ServerPlayer player, AbilitySlot slot) {
        System.out.println("VIBE_PREVIEW " + slot + " " + AbilityUseService.activateAssignedAction(player, slot, false));
    }

    private static void cast(ServerPlayer player, String action) {
        System.out.println("VIBE_PREVIEW " + action + " " + AbilityUseService.activateAction(player, action, false));
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("heroes.preview.vibe")) return;
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
            mc.createWorldOpenFlows().createFreshLevel("Vibe-" + System.currentTimeMillis(),
                    new LevelSettings("Vibe Preview", GameType.CREATIVE, false, Difficulty.EASY, true, rules, WorldDataConfiguration.DEFAULT),
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
                    p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(ModAbilities.VIBE_ID).unlock(ModAbilities.VIBE_ID));
                    p.setData(ModDataAttachments.POWER_INTRO_HISTORY, p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(ModAbilities.VIBE_ID));
                    PlayerProgressionService.setLevel(p, 5);
                    // Every optional active and class ability, so the second half can cast each one.
                    p.setData(ModDataAttachments.SKILL_TREES, SkillTreeData.empty().with(ModAbilities.VIBE_ID.toString(),
                            new SkillTreeData.Build(15, Set.of("vibe_0_0", "vibe_reservoir", "vibe_0_1", "vibe_reverb", "vibe_1_1", "vibe_flashstep",
                                    "vibe_2_1", "vibe_prism_lock", "vibe_crosswave", "vibe_mirror", "vibe_sanctuary"), Map.of(), 0)));
                    p.setGameMode(GameType.SURVIVAL);
                    reset(p, 210, 0, 0, 12800);
                });
            }
            case 151 -> server(mc, p -> { actor(p, .4, 7); cast(p, AbilitySlot.PRIMARY); });
            case 156 -> capture = "01-vibe-blast-dusk.png";
            case 161 -> capture = "02-vibe-blast-wavefront.png";
            case 179 -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                server(mc, p -> reset(p, 222, 0, 0, 6000));
            }
            case 199 -> server(mc, p -> { actor(p, .1, 7); cast(p, AbilitySlot.PRIMARY); });
            case 205 -> capture = "03-vibe-blast-first-person.png";
            case 229 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                server(mc, p -> reset(p, 235, 0, 11, 12800));
            }
            case 254 -> server(mc, p -> cast(p, AbilitySlot.SECONDARY));
            case 259 -> capture = "04-breach-forming.png";
            case 275 -> capture = "05-linked-breaches.png";
            case 279 -> mc.options.setCameraType(CameraType.FIRST_PERSON);
            case 281 -> capture = "06-breach-first-person.png";
            case 285 -> server(mc, p -> p.teleportTo(p.getX(), p.getY(), p.getZ() + 2.3));
            case 289 -> capture = "07-breach-arrival.png";
            case 297 -> server(mc, p -> cast(p, AbilitySlot.SECONDARY));
            case 301 -> capture = "08-breach-closing.png";
            case 320 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                server(mc, p -> reset(p, 248, 0, 0, 12800));
            }
            case 344 -> server(mc, p -> {
                var witness = actor(p, 0, 4);
                var attacker = actor(p, 3.2, 7);
                witness.hurt(p.damageSources().mobAttack(attacker), 3);
                cast(p, AbilitySlot.TERTIARY);
            });
            case 360 -> capture = "09-read-the-vibe.png";
            case 384 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                server(mc, p -> reset(p, 261, 0, 0, 12800));
            }
            case 410 -> server(mc, p -> cast(p, AbilitySlot.QUATERNARY));
            case 423 -> server(mc, p -> {
                var arrow = EntityType.ARROW.create(p.serverLevel());
                arrow.setPos(p.position().add(.45, 1.2, 5.5));
                arrow.setNoGravity(true);
                arrow.setDeltaMovement(0, 0, -1.8);
                p.serverLevel().addFreshEntity(arrow);
            });
            case 427 -> capture = "10-breach-guard.png";
            case 431 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                server(mc, p -> cast(p, AbilitySlot.QUATERNARY));
            }
            case 436 -> capture = "11-return-fire.png";
            case 462 -> server(mc, p -> reset(p, 276, 0, 16, 12800));
            case 486 -> server(mc, p -> { actor(p, 2, 6); cast(p, AbilitySlot.QUINARY); });
            case 497 -> capture = "12-frequency-lock-warning.png";
            case 510 -> capture = "13-frequency-lock-collapse.png";
            // ---------------------------------------------------------- skills and class abilities
            case 540 -> server(mc, p -> reset(p, 290, 0, 0, 12800));
            case 560 -> server(mc, p -> { actor(p, .3, 9); cast(p, "vibe_0_1"); });
            case 565 -> capture = "14-vibe-lance-windup.png";
            case 570 -> capture = "15-vibe-lance.png";
            case 590 -> server(mc, p -> reset(p, 304, 0, 0, 12800));
            case 610 -> server(mc, p -> { actor(p, 0, 6); cast(p, "vibe_reverb"); });
            case 619 -> capture = "16-reverb.png";
            case 640 -> server(mc, p -> reset(p, 318, 0, 0, 12800));
            case 660 -> server(mc, p -> { actor(p, 0, 7); actor(p, 3.2, 8); cast(p, "vibe_crosswave"); });
            case 669 -> capture = "17-crosswave.png";
            case 690 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                server(mc, p -> reset(p, 332, 0, 0, 12800));
            }
            case 710 -> server(mc, p -> cast(p, "vibe_mirror"));
            case 720 -> server(mc, p -> {
                for (double x : new double[] {-1.2, .4, 1.5}) {
                    var arrow = EntityType.ARROW.create(p.serverLevel());
                    arrow.setPos(p.position().add(x, 1.2, 5.5));
                    arrow.setNoGravity(true);
                    arrow.setDeltaMovement(0, 0, -1.8);
                    p.serverLevel().addFreshEntity(arrow);
                }
            });
            case 724 -> capture = "18-mirror-breach.png";
            case 740 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                server(mc, p -> reset(p, 346, 0, 12, 12800));
            }
            case 760 -> server(mc, p -> cast(p, "vibe_flashstep"));
            case 762 -> capture = "19-flashstep.png";
            case 785 -> server(mc, p -> reset(p, 360, 0, 11, 12800));
            case 805 -> server(mc, p -> cast(p, "vibe_sanctuary"));
            case 823 -> capture = "20-sanctuary-breach.png";
            case 845 -> server(mc, p -> reset(p, 374, 0, 0, 12800));
            case 865 -> server(mc, p -> {
                var witness = actor(p, 0, 5);
                var attacker = actor(p, 3, 7.5);
                witness.hurt(p.damageSources().mobAttack(attacker), 3);
                cast(p, "vibe_2_1");
            });
            case 881 -> capture = "21-co-vibe.png";
            case 905 -> server(mc, p -> reset(p, 388, 0, 16, 12800));
            case 925 -> server(mc, p -> { actor(p, 2, 6); actor(p, -3, 9); cast(p, "vibe_prism_lock"); });
            case 936 -> capture = "22-prism-lock-warning.png";
            case 949 -> capture = "23-prism-lock-collapse.png";
            case 970 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                server(mc, p -> reset(p, 402, 0, 0, 12800));
            }
            case 990 -> server(mc, p -> cast(p, "vibe_1_1"));
            case 996 -> capture = "24-attune-frequency.png";
            // ---------------------------------------------------------- the awakening
            case 1015 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                server(mc, p -> reset(p, 416, 0, 0, 12800));
            }
            case 1035 -> server(mc, p -> {
                Villager ward = EntityType.VILLAGER.create(p.serverLevel());
                ward.setNoAi(true);
                ward.moveTo(p.position().add(1, 0, 5));
                p.serverLevel().addFreshEntity(ward);
                var threat = actor(p, 1, 7);
                ArmPosePayload.show(p, ArmPose.VIBE_READ, 28);
                SetPiecePayload.send(p.serverLevel(), Piece.VIBE_AWAKEN, p.position(), ward.position(), 1, p);
                VibeService.awaken(p, ward, List.of(threat));
            });
            case 1039 -> capture = "25-awakening-tears.png";
            case 1048 -> capture = "26-awakening-rescue.png";
            // ---------------------------------------------------------- passives that link two moves
            case 1070 -> server(mc, p -> reset(p, 430, 0, 11, 12800));
            case 1090 -> server(mc, p -> cast(p, AbilitySlot.SECONDARY));
            case 1104 -> server(mc, p -> {
                p.teleportTo(p.serverLevel(), p.getX(), p.getY(), p.getZ(), 0, 0);
                actor(p, .3, 11);
            });
            case 1107 -> server(mc, p -> cast(p, AbilitySlot.PRIMARY));
            case 1112 -> capture = "27-through-the-seam.png";
            case 1116 -> capture = "28-through-the-seam-exit.png";
            case 1135 -> server(mc, p -> {
                reset(p, 444, 0, 0, 12800);
                for (int x = -3; x <= 3; x++) for (int y = 0; y < 4; y++)
                    p.serverLevel().setBlockAndUpdate(p.blockPosition().offset(x, y, 9), Blocks.STONE.defaultBlockState());
            });
            case 1150 -> server(mc, p -> cast(p, AbilitySlot.QUATERNARY));
            case 1158 -> server(mc, p -> {
                for (double x : new double[] {-.3, .1, .4}) {
                    var arrow = EntityType.ARROW.create(p.serverLevel());
                    arrow.setPos(p.position().add(x, 1.2, 5.5));
                    arrow.setNoGravity(true);
                    arrow.setDeltaMovement(0, 0, -1.8);
                    p.serverLevel().addFreshEntity(arrow);
                }
                actor(p, 2.2, 7.6);
            });
            case 1166 -> server(mc, p -> cast(p, AbilitySlot.QUATERNARY));
            case 1170 -> capture = "29-overflow-return-fire.png";
            case 1176 -> capture = "30-overflow-collapse.png";
            case 1200 -> mc.stop();
            default -> { }
        }
    }

    @SubscribeEvent public static void hud(RenderGuiEvent.Pre event) {
        if (age >= 100 && Boolean.getBoolean("heroes.preview.vibe")) event.setCanceled(true);
    }

    @SubscribeEvent public static void frame(RenderFrameEvent.Post event) {
        if (capture == null) return;
        Minecraft mc = Minecraft.getInstance();
        Path path = mc.gameDirectory.toPath().resolve("screenshots").resolve(capture);
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Files.createDirectories(path.getParent());
            image.writeToFile(path);
            System.out.println("VIBE_PREVIEW_CAPTURE " + capture);
        } catch (Exception e) {
            throw new IllegalStateException("Could not capture Vibe preview", e);
        } finally {
            capture = null;
        }
    }
}
