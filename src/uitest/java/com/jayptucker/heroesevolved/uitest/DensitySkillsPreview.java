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
import com.jayptucker.heroesevolved.phasing.DensityService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.skills.SkillTreeData;
import com.jayptucker.heroesevolved.story.bunker.CryoChamberService;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
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
 * Casts every Density skill (and the passives that show) in a flat world well away from the spawn bunker, and saves
 * shots to {@code build/density-skills-preview/screenshots/}.
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class DensitySkillsPreview {
    private static final Set<String> TREE = Set.of("phasing_0_0", "phasing_0_1", "phasing_0_2", "phasing_phase_carry",
            "phasing_thicker_walls", "phasing_ghost_network", "phasing_1_0", "phasing_1_1", "phasing_1_2", "phasing_phase_lock",
            "phasing_long_lock", "phasing_entombed", "phasing_2_0", "phasing_2_1", "phasing_2_2", "phasing_phase_fall",
            "phasing_deep_fall", "phasing_untouchable");
    private static final List<net.minecraft.world.entity.Mob> ACTORS = new ArrayList<>();
    private static final List<BlockPos> BUILT = new ArrayList<>();
    private static boolean started;
    private static int startup, age;
    private static String capture;

    private static void server(Minecraft mc, Consumer<ServerPlayer> action) {
        var id = mc.player.getUUID();
        var server = mc.getSingleplayerServer();
        server.execute(() -> action.accept(server.getPlayerList().getPlayer(id)));
    }

    private static void reset(ServerPlayer player, int x, float yaw, float pitch) {
        DensityService.forget(player);
        CooldownService.clearAll(player);
        PlayerEnergyService.restore(player, 999);
        ACTORS.forEach(a -> { DensityService.release(a); a.discard(); });
        ACTORS.clear();
        for (BlockPos pos : BUILT) player.serverLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        BUILT.clear();
        player.serverLevel().setDayTime(6000);
        player.teleportTo(player.serverLevel(), x + .5, -60, .5, yaw, pitch);
        player.setDeltaMovement(Vec3.ZERO);
        player.setHealth(player.getMaxHealth());
        player.removeAllEffects();
    }

    private static Pillager actor(ServerPlayer player, double dx, double dz, float yaw) {
        Pillager actor = EntityType.PILLAGER.create(player.serverLevel());
        actor.setNoAi(true);
        actor.setPersistenceRequired();
        actor.moveTo(player.getX() + dx, -60, player.getZ() + dz, yaw, 0);
        actor.setYHeadRot(yaw); actor.yBodyRot = yaw;
        actor.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
        actor.setHealth(200);
        actor.setOnGround(true);
        player.serverLevel().addFreshEntity(actor);
        ACTORS.add(actor);
        return actor;
    }

    private static Villager friend(ServerPlayer player, double dx, double dz, float yaw) {
        Villager v = EntityType.VILLAGER.create(player.serverLevel());
        v.setNoAi(true);
        v.moveTo(player.getX() + dx, -60, player.getZ() + dz, yaw, 0);
        player.serverLevel().addFreshEntity(v);
        ACTORS.add(v);
        return v;
    }

    /** A wall across the player's path: x from -w to w, z at {@code dz} to {@code dz + thick - 1}, {@code high} tall. */
    private static void wall(ServerPlayer p, int w, int dz, int thick, int high) {
        BlockPos base = p.blockPosition();
        for (int x = -w; x <= w; x++) for (int z = dz; z < dz + thick; z++) for (int y = 0; y < high; y++) {
            BlockPos pos = base.offset(x, y, z);
            p.serverLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
            BUILT.add(pos);
        }
    }

    /** Turns the player aside (a back camera otherwise hides what they aim at behind their body). */
    private static void turn(ServerPlayer p, float yaw) {
        p.teleportTo(p.serverLevel(), p.getX(), p.getY(), p.getZ(), p.getYRot() + yaw, p.getXRot());
        p.setYHeadRot(p.getYRot()); p.yBodyRot = p.getYRot();
    }

    private static void hit(ServerPlayer p, int actor, float amount) {
        if (actor >= ACTORS.size()) return;
        var t = ACTORS.get(actor);
        t.invulnerableTime = 0;
        t.hurt(p.damageSources().playerAttack(p), amount);
        p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
    }

    private static void cast(ServerPlayer player, AbilitySlot slot) {
        System.out.println("DENSITY_PREVIEW " + slot + " " + AbilityUseService.activateAssignedAction(player, slot, false));
    }

    private static void cast(ServerPlayer player, String action) {
        System.out.println("DENSITY_PREVIEW " + action + " " + AbilityUseService.activateAction(player, action, false));
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("heroes.preview.densityskills")) return;
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
            mc.createWorldOpenFlows().createFreshLevel("DensitySkills-" + System.currentTimeMillis(),
                    new LevelSettings("Density Skills Preview", GameType.CREATIVE, false, Difficulty.EASY, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(73L, false, false),
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
                    p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(ModAbilities.PHASING_ID).unlock(ModAbilities.PHASING_ID));
                    p.setData(ModDataAttachments.POWER_INTRO_HISTORY, p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(ModAbilities.PHASING_ID));
                    PlayerProgressionService.setLevel(p, 5);
                    p.setData(ModDataAttachments.SKILL_TREES, SkillTreeData.empty().with(ModAbilities.PHASING_ID.toString(),
                            new SkillTreeData.Build(15, TREE, Map.of(), 0)));
                    p.setGameMode(GameType.SURVIVAL);
                    reset(p, 210, 0, 0);
                });
            }
            // ---------------------------------------------------------- Pass Through
            case 140 -> server(mc, p -> actor(p, 0, 4, 180));
            case 150 -> server(mc, p -> cast(p, "phasing_0_1"));
            case 151 -> server(mc, p -> turn(p, 110));
            case 153 -> capture = "01-pass-through.png";
            case 157 -> capture = "02-pass-through-late.png";
            case 170 -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                server(mc, p -> { reset(p, 230, 0, 0); actor(p, 0, 4, 180); });
            }
            case 185 -> server(mc, p -> cast(p, "phasing_0_1"));
            case 187 -> capture = "03-pass-through-first-person.png";
            // ---------------------------------------------------------- Through the Wall
            case 205 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                server(mc, p -> { reset(p, 250, 0, 0); wall(p, 2, 2, 1, 3); actor(p, 0, 3.6, 180); });
            }
            case 220 -> server(mc, p -> cast(p, AbilitySlot.SECONDARY));
            case 221 -> server(mc, p -> turn(p, -60));
            case 223 -> capture = "04-through-the-wall.png";
            // ---------------------------------------------------------- Ambush: primed by a wall, then the strike
            case 240 -> server(mc, p -> { reset(p, 270, 0, 0); wall(p, 2, 1, 1, 3); });
            case 255 -> server(mc, p -> DensityService.pass(p, p.position(), p.position().add(0, 0, 2.2)));
            case 257 -> server(mc, p -> turn(p, 80));
            case 260 -> capture = "05-ambush-primed.png";
            case 261 -> server(mc, p -> turn(p, -80));
            case 262 -> mc.options.setCameraType(CameraType.FIRST_PERSON);
            case 266 -> capture = "06-ambush-primed-first-person.png";
            case 268 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                server(mc, p -> actor(p, 0, 2.2, 180));
            }
            case 274 -> server(mc, p -> cast(p, AbilitySlot.SECONDARY));
            case 275 -> server(mc, p -> turn(p, 80));
            case 277 -> capture = "07-ambush-strike.png";
            // ---------------------------------------------------------- Ghost Network (front: the open wall behind you)
            case 300 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                server(mc, p -> { reset(p, 300, 0, 0); wall(p, 2, 1, 2, 3); });
            }
            case 315 -> server(mc, p -> {
                Vec3 from = p.position();
                DensityService.pass(p, from, from.add(0, 0, 3.4));
                friend(p, .6, -3.6, 0);
            });
            case 320 -> capture = "08-ghost-network.png";
            case 333 -> capture = "09-network-friend.png";
            // ---------------------------------------------------------- Phase Carry
            case 355 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                server(mc, p -> { reset(p, 330, 0, 0); wall(p, 3, 2, 1, 3); friend(p, -1.4, .3, 0); friend(p, 1.4, -.3, 0); });
            }
            case 370 -> server(mc, p -> cast(p, "phasing_phase_carry"));
            case 371 -> server(mc, p -> turn(p, 90));
            case 373 -> capture = "10-phase-carry.png";
            case 380 -> capture = "11-phase-carry-loose.png";
            // ---------------------------------------------------------- Harden, banking blows, then Rebound
            case 400 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                server(mc, p -> { reset(p, 360, 0, 0); actor(p, 2.2, 1.5, 230); actor(p, -2.2, 1.5, 130); actor(p, 0, -2.4, 0); });
            }
            case 415 -> server(mc, p -> cast(p, "phasing_1_1"));
            case 418 -> capture = "12-harden.png";
            case 420 -> server(mc, p -> {
                for (int i = 0; i < 3; i++) { p.invulnerableTime = 0; p.hurt(p.damageSources().mobAttack(ACTORS.get(0)), 5); }
                p.setHealth(p.getMaxHealth());
            });
            case 432 -> capture = "13-harden-weight.png";
            case 440 -> server(mc, p -> cast(p, "phasing_1_1"));
            case 443 -> capture = "14-rebound.png";
            case 448 -> capture = "15-rebound-crater.png";
            // ---------------------------------------------------------- Pile Driver
            case 470 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                server(mc, p -> { reset(p, 390, 0, 15); actor(p, 0, 2, 180); DensityService.setNotch(p, DensityService.FULL_DENSE, 200); });
            }
            case 485 -> server(mc, p -> hit(p, 0, 4));
            case 486 -> server(mc, p -> turn(p, 70));
            case 489 -> capture = "16-pile-driver.png";
            // ---------------------------------------------------------- Sinkhole
            case 505 -> server(mc, p -> { reset(p, 410, 0, 11.5F); actor(p, .8, 8, 180); actor(p, -1, 7.6, 160); });
            case 520 -> server(mc, p -> cast(p, "phasing_phase_lock"));
            case 522 -> server(mc, p -> { turn(p, 30); p.teleportTo(p.serverLevel(), p.getX(), p.getY(), p.getZ(), p.getYRot(), 30); });
            case 525 -> capture = "17-sinkhole-warning.png";
            case 545 -> capture = "18-sinkhole.png";
            case 547 -> server(mc, p -> actor(p, 2, 8, 220));
            // ---------------------------------------------------------- Embed, then Entombed
            case 570 -> server(mc, p -> { reset(p, 440, 0, 20); actor(p, 0, 3, 180); });
            case 585 -> server(mc, p -> cast(p, AbilitySlot.TERTIARY));
            case 586 -> server(mc, p -> turn(p, 60));
            case 588 -> capture = "19-embedded.png";
            case 594 -> server(mc, p -> turn(p, -60));
            case 595 -> server(mc, p -> cast(p, AbilitySlot.TERTIARY));
            case 596 -> server(mc, p -> turn(p, 60));
            case 599 -> capture = "20-entomb-closing.png";
            case 606 -> capture = "21-entomb-closed.png";
            // ---------------------------------------------------------- Sink, then Uproot (Sink waits two seconds to surface)
            case 630 -> server(mc, p -> { reset(p, 470, 0, 25); actor(p, 0, 1.2, 180); });
            case 645 -> server(mc, p -> { p.setOnGround(true); cast(p, AbilitySlot.QUATERNARY); });
            case 650 -> server(mc, p -> { if (!ACTORS.isEmpty()) DensityService.embed(ACTORS.get(0), p, 200, false, .45); });
            case 690 -> server(mc, p -> cast(p, AbilitySlot.QUATERNARY));
            case 691 -> server(mc, p -> turn(p, 70));
            case 693 -> capture = "22-uproot.png";
            case 697 -> capture = "23-uproot-late.png";
            // ---------------------------------------------------------- Fade Through, drifting through two enemies
            case 710 -> server(mc, p -> { reset(p, 500, 0, 0); actor(p, 0, 2.5, 180); actor(p, .3, 4.2, 180); });
            case 725 -> server(mc, p -> cast(p, "phasing_2_1"));
            case 726, 727, 728, 729, 730, 731, 732, 733, 734, 735, 736, 737 -> server(mc, p ->
                    p.teleportTo(p.serverLevel(), p.getX(), p.getY(), p.getZ() + .45, p.getYRot(), p.getXRot()));
            case 738 -> server(mc, p -> turn(p, 90));
            case 740 -> capture = "24-fade-through.png";
            case 741 -> server(mc, p -> turn(p, -90));
            case 742 -> mc.options.setCameraType(CameraType.FIRST_PERSON);
            case 745 -> capture = "25-fade-first-person.png";
            // ---------------------------------------------------------- Grasp from Below
            case 760 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                server(mc, p -> { reset(p, 530, 0, 15); actor(p, 0, 3.6, 180); });
            }
            case 775 -> server(mc, p -> { p.setOnGround(true); cast(p, AbilitySlot.QUATERNARY); });
            case 782 -> server(mc, p -> cast(p, AbilitySlot.SECONDARY));
            case 818 -> server(mc, p -> cast(p, AbilitySlot.QUATERNARY));
            case 819 -> server(mc, p -> turn(p, 60));
            case 821 -> capture = "26-grasp-from-below.png";
            // ---------------------------------------------------------- Phase Fall and Deep Fall, from a platform
            case 845 -> server(mc, p -> {
                reset(p, 560, 0, 25);
                BlockPos base = p.blockPosition();
                for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
                    BlockPos pos = base.offset(x, 4, z);
                    p.serverLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
                    BUILT.add(pos);
                }
                p.teleportTo(p.serverLevel(), p.getX(), p.getY() + 5, p.getZ(), 0, 25);
                actor(p, 1.6, 1.2, 200).moveTo(p.getX() + 1.6, -60, p.getZ() + 1.2);
            });
            case 860 -> server(mc, p -> { DensityService.setNotch(p, DensityService.DENSE, 200); cast(p, "phasing_phase_fall"); });
            case 862 -> capture = "27-phase-fall.png";
            case 866 -> capture = "28-deep-fall-landing.png";
            // ---------------------------------------------------------- Untouchable
            case 890 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                server(mc, p -> { reset(p, 590, 0, 0); actor(p, 1.2, 1.4, 220); });
            }
            case 905 -> server(mc, p -> { p.setHealth(6); p.invulnerableTime = 0; p.hurt(p.damageSources().mobAttack(ACTORS.get(0)), 8); });
            case 907 -> capture = "29-untouchable.png";
            case 912 -> capture = "30-untouchable-late.png";
            // ---------------------------------------------------------- First-person poses
            case 930 -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                server(mc, p -> reset(p, 620, 0, 0));
            }
            case 940 -> server(mc, p -> cast(p, "phasing_1_1"));
            case 944 -> capture = "31-harden-first-person.png";
            case 955 -> server(mc, p -> { reset(p, 640, 0, 11.5F); actor(p, 0, 8, 180); });
            case 965 -> server(mc, p -> cast(p, "phasing_phase_lock"));
            case 968 -> capture = "32-sinkhole-first-person.png";
            case 985 -> server(mc, p -> { reset(p, 660, 0, 20); actor(p, 0, 3, 180); });
            case 995 -> server(mc, p -> cast(p, AbilitySlot.TERTIARY));
            case 1002 -> server(mc, p -> cast(p, AbilitySlot.TERTIARY));
            case 1006 -> capture = "33-entomb-first-person.png";
            case 1020 -> server(mc, p -> { reset(p, 680, 0, 0); ArmPosePayload.show(p, ArmPose.DENSITY_USHER, 20); });
            case 1024 -> capture = "34-usher-first-person.png";
            case 1045 -> mc.stop();
            default -> { }
        }
    }

    @SubscribeEvent public static void hud(RenderGuiEvent.Pre event) {
        if (age >= 100 && Boolean.getBoolean("heroes.preview.densityskills")) event.setCanceled(true);
    }

    @SubscribeEvent public static void frame(RenderFrameEvent.Post event) {
        if (capture == null) return;
        Minecraft mc = Minecraft.getInstance();
        Path path = mc.gameDirectory.toPath().resolve("screenshots").resolve(capture);
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Files.createDirectories(path.getParent());
            image.writeToFile(path);
            System.out.println("DENSITY_PREVIEW_CAPTURE " + capture);
        } catch (Exception e) {
            throw new IllegalStateException("Could not capture Density preview", e);
        } finally {
            capture = null;
        }
    }
}
