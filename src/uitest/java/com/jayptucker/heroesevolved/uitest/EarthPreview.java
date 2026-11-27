package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.AbilitySlot;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.AbilityUseService;
import com.jayptucker.heroesevolved.client.effects.TempestCamera;
import com.jayptucker.heroesevolved.combat.FightingStyles;
import com.jayptucker.heroesevolved.combat.FightingStyles.Dodge;
import com.jayptucker.heroesevolved.combat.Takedowns;
import com.jayptucker.heroesevolved.cooldown.CooldownService;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.phasing.DensityFinishers;
import com.jayptucker.heroesevolved.phasing.DensityService;
import com.jayptucker.heroesevolved.phasing.EarthStyle;
import com.jayptucker.heroesevolved.phasing.Earthworks;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.skills.SkillCatalog;
import com.jayptucker.heroesevolved.skills.SkillTreeData;
import com.jayptucker.heroesevolved.story.bunker.CryoChamberService;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Photographs Density's earthbending (flag {@code heroes.preview.earth}): Horse Stance, Rock Strike (stone, boulder),
 * Earth Shackle and Crush, pillars and walls, the Rising Mountain, tunnelling, Tiger and Crane, the tree's actives, the
 * classes, Rock Clap, first person and the Root ring, into {@code build/earth-preview/screenshots/}. Every scene stands
 * on force-loaded overworld ground about 150 blocks from the bunker, on grass (earth).
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class EarthPreview {
    private static final List<Mob> ACTORS = new ArrayList<>();
    private static final int STAGE = 150, SPOT = 14;
    private static boolean started, gui;
    private static int startup, age, shots, failures;
    private static String capture;
    private static Vec3 anchor = Vec3.ZERO;

    private static boolean on() { return Boolean.getBoolean("heroes.preview.earth"); }

    private static void server(Minecraft mc, Consumer<ServerPlayer> action) {
        var id = mc.player.getUUID();
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            try { action.accept(server.getPlayerList().getPlayer(id)); }
            catch (RuntimeException e) { failures++; PreviewQuiet.report("EARTH_PREVIEW FAIL at " + age + ": " + e); e.printStackTrace(); }
        });
    }

    private static ServerLevel world(ServerPlayer player) { return player.getServer().overworld(); }

    private static void stage(ServerPlayer player) {
        var world = world(player);
        for (int cx = (STAGE - 16) >> 4; cx <= (STAGE + 3 * SPOT + 16) >> 4; cx++)
            for (int cz = (STAGE - 16) >> 4; cz <= (STAGE + 3 * SPOT + 16) >> 4; cz++)
                world.setChunkForced(cx, cz, true);
        player.teleportTo(world, STAGE + .5, -60, STAGE + .5, 0, 0);
        log("staged in " + player.level().dimension().location() + " at " + player.blockPosition()
                + " on " + world.getBlockState(player.blockPosition().below()));
    }

    /** Puts the player on spot {@code slot} (0 to 8), facing south, with everything from the last scene cleared away. */
    private static void reset(ServerPlayer player, int slot, float pitch, boolean tree) {
        double x = STAGE + (slot % 3) * SPOT + .5, z = STAGE + (slot / 3 % 3) * SPOT + .5;
        DensityService.forget(player);
        FightingStyles.forget(player);
        CooldownService.clearAll(player);
        PlayerEnergyService.restore(player, 999);
        ACTORS.forEach(a -> { DensityService.release(a); a.discard(); });
        ACTORS.clear();
        Set<String> learned = new HashSet<>();
        if (tree) for (var node : SkillCatalog.nodes("phasing")) learned.add(node.id());
        player.setData(ModDataAttachments.SKILL_TREES, SkillTreeData.empty().with(ModAbilities.PHASING_ID.toString(),
                new SkillTreeData.Build(20, learned, Map.of())));
        world(player).setDayTime(6000);
        player.teleportTo(world(player), x, -60, z, 0, pitch);
        player.setDeltaMovement(Vec3.ZERO);
        player.setHealth(player.getMaxHealth());
        player.removeAllEffects();
        anchor = new Vec3(x, -60, z);
        Minecraft.getInstance().execute(() -> TempestCamera.fix(null, null));
    }

    private static Mob actor(ServerPlayer player, double dx, double dz) {
        Mob actor = EntityType.PILLAGER.create(player.serverLevel());
        actor.setNoAi(true);
        actor.setPersistenceRequired();
        float yaw = (float) Math.toDegrees(Math.atan2(dx, -dz));
        actor.moveTo(player.getX() + dx, -60, player.getZ() + dz, yaw, 0);
        actor.setYHeadRot(yaw);
        actor.yBodyRot = yaw;
        actor.getAttribute(Attributes.MAX_HEALTH).setBaseValue(400);
        actor.setHealth(400);
        actor.setOnGround(true);
        player.serverLevel().addFreshEntity(actor);
        ACTORS.add(actor);
        return actor;
    }

    private static void look(ServerPlayer p) {
        if (!ACTORS.isEmpty()) p.lookAt(EntityAnchorArgument.Anchor.EYES, ACTORS.getFirst().getBoundingBox().getCenter());
    }

    private static void lookAt(ServerPlayer p, double dx, double dy, double dz) {
        p.lookAt(EntityAnchorArgument.Anchor.EYES, anchor.add(dx, dy, dz));
    }

    /** A still camera {@code dx, dy, dz} from the spot, looking at {@code lx, ly, lz} (queued behind the reset). */
    private static void side(double dx, double dy, double dz, double lx, double ly, double lz) {
        Minecraft mc = Minecraft.getInstance();
        mc.getSingleplayerServer().execute(() -> {
            Vec3 at = anchor.add(dx, dy, dz), target = anchor.add(lx, ly, lz);
            mc.execute(() -> TempestCamera.fix(at, target));
        });
    }

    private static void cast(ServerPlayer p, AbilitySlot slot) {
        log(slot + " " + AbilityUseService.activateAssignedAction(p, slot, false));
    }

    private static void cast(ServerPlayer p, String action) {
        log(action + " " + AbilityUseService.activateAction(p, action, false));
    }

    private static void log(String what) { System.out.println("EARTH_PREVIEW " + what); }

    private static void shoot(String name) { capture = name; shots++; }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!on()) return;
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
            mc.createWorldOpenFlows().createFreshLevel("Earth-" + System.currentTimeMillis(),
                    new LevelSettings("Earth Preview", GameType.CREATIVE, false, Difficulty.EASY, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(73L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.getSingleplayerServer() == null || mc.getOverlay() != null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        if (age >= 100) mc.getToasts().clear();
        ++age;
        if (age == 104) { server(mc, com.jayptucker.heroesevolved.story.prologue.PrologueService::skip); return; }
        if (age == 110) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            com.jayptucker.heroesevolved.network.ClientScreenTintState.clear();
            server(mc, p -> {
                CryoChamberService.release(p);
                p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(ModAbilities.PHASING_ID).unlock(ModAbilities.PHASING_ID));
                p.setData(ModDataAttachments.POWER_INTRO_HISTORY, p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(ModAbilities.PHASING_ID));
                PlayerProgressionService.setLevel(p, 5);
                PlayerProgressionService.setPowerLevel(p, 20);
                com.jayptucker.heroesevolved.network.PlayerPowerSyncService.sync(p);
                p.setGameMode(GameType.SURVIVAL);
                stage(p);
            });
            return;
        }
        if (age < 170) return;
        if (age % 20 == 0) com.jayptucker.heroesevolved.network.ClientScreenTintState.clear();
        script(mc, age - 170);
    }

    private static void script(Minecraft mc, int t) {
        switch (t) {
            // ---------------------------------------------------------- Horse Stance
            case 0 -> server(mc, p -> reset(p, 0, 0, false));
            case 10 -> server(mc, p -> cast(p, AbilitySlot.PRIMARY));
            case 16 -> shoot("01-stance-back.png");
            case 18 -> side(4, 1.5, 0, 0, .9, 0);
            case 22 -> shoot("02-stance-side.png");
            case 24 -> side(0, 1.6, 3.5, 0, .9, 0);
            case 28 -> shoot("03-stance-front.png");
            // ---------------------------------------------------------- Rock Strike
            case 40 -> server(mc, p -> { reset(p, 1, 0, false); actor(p, 0, 8); look(p); });
            case 44 -> side(5, 1.6, 2, 0, 1.2, 2);
            case 50 -> server(mc, p -> { cast(p, AbilitySlot.SECONDARY); com.jayptucker.heroesevolved.phasing.Stones.release(p); });
            case 52 -> shoot("04-lift-tear.png");
            case 58 -> shoot("05-lift-hover.png");
            case 62 -> server(mc, p -> { look(p); cast(p, AbilitySlot.SECONDARY); });
            case 63 -> shoot("06-throw.png");
            case 65 -> shoot("07-hit.png");
            case 70 -> side(-2.5, 1.8, 5, 0, 1.2, 0);
            case 74 -> server(mc, p -> { CooldownService.clearAll(p); look(p); cast(p, AbilitySlot.SECONDARY); });
            case 94 -> shoot("08-boulder-front.png");
            case 96 -> side(5, 1.8, 2, 0, 1.2, 2);
            case 100 -> shoot("09-boulder-side.png");
            case 102 -> server(mc, p -> { look(p); cast(p, AbilitySlot.SECONDARY); });
            case 104 -> shoot("10-boulder-thrown.png");
            // ---------------------------------------------------------- Earth Shackle and Crush
            case 120 -> server(mc, p -> { reset(p, 2, 0, false); actor(p, 0, 5); look(p); });
            case 124 -> side(4, 1.4, 4, 0, .5, 5);
            case 130 -> server(mc, p -> cast(p, AbilitySlot.TERTIARY));
            case 134 -> shoot("11-shackle.png");
            case 144 -> shoot("12-shackle-hold.png");
            case 146 -> server(mc, p -> { look(p); cast(p, AbilitySlot.TERTIARY); });
            case 148 -> shoot("13-crush.png");
            // ---------------------------------------------------------- Pillar, self-launch, wall and shove
            case 170 -> server(mc, p -> { reset(p, 3, 0, false); actor(p, 0, 7); look(p); });
            case 174 -> side(6, 2, 4, 0, 2, 7);
            case 180 -> server(mc, p -> cast(p, AbilitySlot.QUATERNARY));
            case 182 -> shoot("14-pillar-rising.png");
            case 186 -> shoot("15-pillar-launch.png");
            case 200 -> server(mc, p -> { reset(p, 4, 85, false); });
            case 204 -> side(5, 2, 0, 0, 2, 0);
            case 210 -> server(mc, p -> cast(p, AbilitySlot.QUATERNARY));
            case 214 -> shoot("16-pillar-self.png");
            case 230 -> server(mc, p -> { reset(p, 5, 0, false); actor(p, 0, 7); });
            case 234 -> side(6, 2.5, 1, 0, 1.2, 3);
            case 240 -> mc.options.keyShift.setDown(true);
            case 242 -> server(mc, p -> com.jayptucker.heroesevolved.phasing.Earthworks.raise(p, 5, true));
            case 243 -> mc.options.keyShift.setDown(false);
            case 244 -> shoot("17-wall-rising.png");
            case 250 -> shoot("18-wall.png");
            case 252 -> server(mc, p -> cast(p, AbilitySlot.QUATERNARY));
            case 256 -> shoot("19-shove.png");
            case 262 -> shoot("20-shove-hit.png");
            // ---------------------------------------------------------- Rising Mountain
            case 290 -> server(mc, p -> { reset(p, 6, 0, false); actor(p, 0, 9); actor(p, 1.5, 10); look(p); });
            case 294 -> side(13, 5, 2, 0, 3, 9);
            case 300 -> server(mc, p -> cast(p, AbilitySlot.QUINARY));
            case 310 -> shoot("21-mountain-warning.png");
            case 330 -> shoot("22-mountain-rising.png");
            case 350 -> shoot("23-mountain-top.png");
            case 352 -> side(0, 2, 2, 0, 5, 9);
            case 356 -> shoot("24-mountain-from-below.png");
            case 358 -> side(13, 5, 2, 0, 3, 9);
            case 360 -> server(mc, Earthworks::releaseMountain);
            case 364 -> shoot("25-mountain-falling.png");
            case 368 -> shoot("26-mountain-slam.png");
            case 376 -> shoot("27-mountain-dust.png");
            // ---------------------------------------------------------- Tunnel (the tree's), and the style's back dodge
            case 400 -> server(mc, p -> { reset(p, 7, 0, true); actor(p, 0, 3); });
            case 404 -> side(5, 2, 0, 0, .5, 0);
            case 410 -> server(mc, p -> cast(p, "phasing_2_1"));
            case 412 -> shoot("28-tunnel-in.png");
            case 425 -> server(mc, p -> { p.teleportTo(world(p), p.getX(), p.getY(), p.getZ() + 3, 0, 0); });
            case 428 -> server(mc, p -> DensityService.sinkOrSurface(p, 5));
            case 430 -> shoot("29-tunnel-out.png");
            // ---------------------------------------------------------- Tiger and Crane
            case 450 -> server(mc, p -> { reset(p, 8, 0, false); actor(p, 0, 2.6); look(p); });
            case 454 -> side(3.5, 1.6, 1.3, 0, 1.1, 1.3);
            case 460 -> server(mc, p -> log("claw " + EarthStyle.STYLE.strike(p)));
            case 462 -> shoot("30-tiger-claw.png");
            case 466 -> server(mc, p -> log("gauntlet " + EarthStyle.STYLE.strike(p)));
            case 468 -> shoot("31-gauntlet-claw.png");
            case 472 -> server(mc, p -> log("slab " + EarthStyle.STYLE.strike(p)));
            case 475 -> shoot("32-twin-slab.png");
            case 480 -> shoot("33-twin-slab-late.png");
            case 496 -> mc.options.keyShift.setDown(true);
            case 500 -> server(mc, p -> log("stomp " + EarthStyle.STYLE.strike(p)));
            case 503 -> shoot("34-horse-stomp.png");
            case 506 -> mc.options.keyShift.setDown(false);
            case 516 -> server(mc, p -> log("slide " + EarthStyle.STYLE.dodge(p, Dodge.LEFT)));
            case 519 -> shoot("35-earth-slide.png");
            // ---------------------------------------------------------- The tree
            case 540 -> server(mc, p -> { reset(p, 0, 0, true); actor(p, 0, 8); look(p); });
            case 544 -> side(5, 1.8, 2, 0, 1.2, 2);
            case 550 -> server(mc, p -> cast(p, AbilitySlot.SECONDARY));
            case 558 -> shoot("36-stacked-discs.png");
            case 560 -> server(mc, p -> { look(p); cast(p, AbilitySlot.SECONDARY); });
            case 561 -> shoot("37-disc-thrown.png");
            case 580 -> server(mc, p -> { reset(p, 1, 0, true); actor(p, 0, 8); look(p); });
            case 584 -> side(5, 2, 4, 0, 1.2, 6);
            case 590 -> server(mc, p -> cast(p, "phasing_0_1"));
            case 593 -> shoot("38-earth-gauntlet.png");
            case 598 -> shoot("39-earth-gauntlet-held.png");
            case 620 -> server(mc, p -> { reset(p, 2, 0, true); actor(p, 0, 9); });
            case 624 -> side(7, 3, 4, 0, .8, 7);
            case 630 -> server(mc, p -> cast(p, "phasing_phase_carry"));
            case 640 -> shoot("40-avalanche.png");
            case 648 -> shoot("41-avalanche-rolling.png");
            case 670 -> server(mc, p -> reset(p, 3, 0, true));
            case 674 -> side(3, 1.6, 2.5, 0, 1, 0);
            case 680 -> server(mc, p -> cast(p, "phasing_1_1"));
            case 686 -> shoot("42-stone-skin.png");
            case 700 -> server(mc, p -> { reset(p, 4, 0, true); actor(p, 0, 7); look(p); });
            case 704 -> side(5, 3, 3, 0, 0, 7);
            case 710 -> server(mc, p -> cast(p, "phasing_phase_lock"));
            case 715 -> shoot("43-quicksand-warning.png");
            case 728 -> shoot("44-quicksand.png");
            case 750 -> server(mc, p -> { reset(p, 5, 0, true); actor(p, 4, 9); actor(p, -6, 5); });
            case 754 -> side(0, 9, -3, 0, 0, 3);
            case 760 -> mc.options.keyShift.setDown(true);
            case 762 -> server(mc, p -> cast(p, AbilitySlot.PRIMARY));
            case 763 -> mc.options.keyShift.setDown(false);
            case 768 -> shoot("45-seismic-stomp.png");
            case 774 -> shoot("46-seismic-stomp-wide.png");
            case 800 -> server(mc, p -> reset(p, 6, 0, true));
            case 804 -> server(mc, p -> cast(p, "phasing_phase_fall"));
            case 808 -> mc.options.keyUp.setDown(true);
            case 814 -> shoot("47-earth-surf-back.png");
            case 818 -> side(4, 1.5, 6, 0, 1, 10);
            case 822 -> shoot("48-earth-surf-side.png");
            case 826 -> mc.options.keyUp.setDown(false);
            case 850 -> server(mc, p -> { reset(p, 7, 0, true); DensityService.setNotch(p, DensityService.FULL_DENSE, 400); });
            case 854 -> side(3, 1.6, 2.5, 0, .8, 0);
            case 892 -> shoot("49-wait-and-listen.png");
            // ---------------------------------------------------------- Rock Clap
            case 1010 -> server(mc, p -> {
                reset(p, 1, 0, false);
                Mob foe = actor(p, 0, 2.4);
                foe.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20);
                foe.setHealth(2);
                look(p);
            });
            case 1014 -> side(3.5, 1.8, 1.2, 0, 1, 2.4);
            case 1020 -> server(mc, p -> log("rock clap " + Takedowns.request(p, ACTORS.getFirst().getId(), DensityFinishers.ROCK_CLAP)));
            case 1026 -> shoot("54-rock-clap-rise.png");
            case 1032 -> shoot("55-rock-clap.png");
            case 1036 -> shoot("56-rock-clap-break.png");
            // ---------------------------------------------------------- First person and the HUD
            case 1060 -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                server(mc, p -> { reset(p, 2, 0, false); actor(p, 0, 7); look(p); });
            }
            case 1066 -> server(mc, p -> cast(p, AbilitySlot.PRIMARY));
            case 1072 -> shoot("57-first-person-stance.png");
            case 1074 -> server(mc, p -> { cast(p, AbilitySlot.PRIMARY); cast(p, AbilitySlot.SECONDARY); com.jayptucker.heroesevolved.phasing.Stones.release(p); });
            case 1080 -> shoot("58-first-person-lift.png");
            case 1082 -> server(mc, p -> { look(p); cast(p, AbilitySlot.SECONDARY); });
            case 1083 -> shoot("59-first-person-strike.png");
            case 1090 -> server(mc, p -> { CooldownService.clearAll(p); look(p); log("claw " + EarthStyle.STYLE.strike(p)); });
            case 1091 -> shoot("60-first-person-claw.png");
            case 1100 -> { mc.options.keyShift.setDown(true); }
            case 1102 -> server(mc, p -> com.jayptucker.heroesevolved.phasing.Earthworks.raise(p, 5, true));
            case 1103 -> mc.options.keyShift.setDown(false);
            case 1105 -> shoot("61-first-person-wall.png");
            case 1120 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                gui = true;
                server(mc, p -> { reset(p, 3, 0, false); cast(p, AbilitySlot.PRIMARY); });
            }
            case 1150 -> shoot("62-hud-root.png");
            case 1160 -> {
                gui = false;
                String result = "EARTH_PREVIEW_RESULT " + (failures == 0 ? "PASS" : "FAIL") + ": " + shots + " photos, " + failures + " failures";
                System.out.println(result);
                PreviewQuiet.report(result);
            }
            case 1170 -> PreviewQuiet.quit();
            default -> { }
        }
    }

    @SubscribeEvent public static void hud(RenderGuiEvent.Pre event) {
        if (on() && age >= 100 && !gui) event.setCanceled(true);
    }

    @SubscribeEvent public static void frame(RenderFrameEvent.Post event) {
        if (capture == null) return;
        Minecraft mc = Minecraft.getInstance();
        Path path = mc.gameDirectory.toPath().resolve("screenshots").resolve(capture);
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Files.createDirectories(path.getParent());
            image.writeToFile(path);
            System.out.println("EARTH_PREVIEW_CAPTURE " + capture);
        } catch (Exception e) {
            failures++;
            PreviewQuiet.report("EARTH_PREVIEW FAIL capture " + capture + ": " + e);
        } finally {
            capture = null;
        }
    }
}
