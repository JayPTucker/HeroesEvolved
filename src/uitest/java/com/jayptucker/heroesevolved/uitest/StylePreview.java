package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Plays every class ability's set piece on a stage in a flat world, one after another, and photographs each
 * at its telling moments from a camera a few blocks off. A villager stands on the stage for the pieces that
 * follow someone. Screenshots land in build/style-preview/screenshots/styles/.
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class StylePreview {
    private enum Place { STAGE, AIMED, BOUND, LINE, FACE }

    /** One piece to play: how it is placed, its scale, the ages to photograph, and the camera's distance, height and pitch. */
    private record Shot(Piece piece, Place place, float scale, int[] ages, double distance, double height, float pitch) {}

    private static final List<Shot> SHOTS = new ArrayList<>();

    private static void shot(Piece piece, Place place, float scale, int... ages) { SHOTS.add(new Shot(piece, place, scale, ages, 11, 2.5, 10)); }

    private static void far(Piece piece, Place place, float scale, double distance, double height, float pitch, int... ages) {
        SHOTS.add(new Shot(piece, place, scale, ages, distance, height, pitch));
    }

    static {
        far(Piece.GROUND_ZERO, Place.AIMED, 9, 14, 3, 5, 22, 34);
        far(Piece.GROUND_ZERO, Place.AIMED, 9, 34, 6, -14, 70, 140);
        shot(Piece.CONTAINMENT_DOME, Place.STAGE, 5, 40);
        shot(Piece.REACTOR_HEARTH, Place.STAGE, 7, 50);
        shot(Piece.HOUR_HAND, Place.FACE, 7, 12);
        shot(Piece.TIME_LOOP, Place.BOUND, 1, 50, 114);
        shot(Piece.BORROWED_TIME, Place.BOUND, 1, 40);
        far(Piece.SUNFALL, Place.AIMED, 5, 14, 3, -8, 12, 26);
        shot(Piece.EMBER_SHROUD, Place.BOUND, 1, 40);
        shot(Piece.HEARTHFIRE, Place.STAGE, 6, 60);
        far(Piece.HAILSTORM, Place.AIMED, 5, 15, 3, -8, 40);
        shot(Piece.CRYSTAL_BASTION, Place.STAGE, 3, 40, 152);
        far(Piece.AURORA_VEIL, Place.STAGE, 8, 16, 2, -18, 80);
        shot(Piece.TESLA_SPIRE, Place.AIMED, 9, 40);
        shot(Piece.FARADAY_CAGE, Place.BOUND, 2.2F, 40);
        shot(Piece.JUMPSTART, Place.LINE, 1, 4);
        shot(Piece.FISSURE_MAW, Place.LINE, 14, 20);
        shot(Piece.STONE_RAMPART, Place.FACE, 3.5F, 30);
        far(Piece.BEDROCK_STANCE, Place.STAGE, 10, 14, 5, 22, 30);
        shot(Piece.TEMPEST_DRILL, Place.LINE, 1, 8);
        shot(Piece.GALE_WALL, Place.BOUND, 3, 40);
        shot(Piece.FORMATION_FLIGHT, Place.BOUND, 1, 40);
        shot(Piece.IMPLOSION, Place.AIMED, 6, 14, 26);
        shot(Piece.DEBRIS_HALO, Place.BOUND, 2.6F, 40);
        shot(Piece.GATHERING_HAND, Place.BOUND, 1, 6, 20);
        far(Piece.PSYCHIC_STORM, Place.AIMED, 6, 14, 3, 0, 50);
        shot(Piece.BLIND_SPOT, Place.BOUND, 1, 12);
        shot(Piece.HIVE_MIND, Place.BOUND, 1, 40);
        shot(Piece.VITAL_BURST, Place.STAGE, 6, 8);
        shot(Piece.UNDYING, Place.BOUND, 1, 40);
        far(Piece.LIFEBLOOM, Place.STAGE, 7, 12, 2.5, 2, 60);
        shot(Piece.RESONANCE_CASCADE, Place.AIMED, 4.5F, 12, 20);
        shot(Piece.SILENCE_DOME, Place.STAGE, 5, 40);
        shot(Piece.BATTLE_HYMN, Place.BOUND, 1, 40);
        shot(Piece.LIGHTNING_FLURRY, Place.LINE, 1, 4);
        shot(Piece.PHASE_BLUR, Place.BOUND, 1, 40);
        shot(Piece.RELAY_RUN, Place.LINE, 1, 4);
        shot(Piece.SHADOW_STRIKE, Place.FACE, 1, 4);
        shot(Piece.REFRACTION_CLOAK, Place.BOUND, 1, 40);
        shot(Piece.PRISM_HAZE, Place.STAGE, 6, 60);
        shot(Piece.PHASE_ERUPTION, Place.AIMED, 3.5F, 16);
        shot(Piece.DENSE_BULWARK, Place.BOUND, 1, 40);
        shot(Piece.PHASE_FIELD, Place.BOUND, 1, 40);
        shot(Piece.PRISMATIC_OVERFLOW, Place.STAGE, 7, 8);
        shot(Piece.EMPATHIC_WARD, Place.BOUND, 1, 40);
        shot(Piece.KINDRED_LIGHT, Place.BOUND, 1, 12);
        shot(Piece.DRONE_SWARM, Place.BOUND, 1, 40);
        shot(Piece.FIREWALL, Place.FACE, 1, 30);
        shot(Piece.OVERCLOCK, Place.BOUND, 1, 40);
        shot(Piece.FORGE_CANNON, Place.BOUND, 1, 14);
        shot(Piece.BULWARK_LATTICE, Place.BOUND, 1, 40);
        shot(Piece.MENDING_LATTICE, Place.BOUND, 1, 20);
        far(Piece.EXPLODING_MAN, Place.STAGE, 24, 80, 12, -12, 4, 40, 140);
    }

    private static boolean started;
    private static int ticks, step = -1, wait, shotIndex;
    private static long began;
    private static String pending;
    private static Villager stand;
    /** Where everything plays: well away from spawn, where the stasis bunker and its buildings stand. Set once the world exists. */
    private static volatile Vec3 STAGE = Vec3.ZERO;

    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        if (!Boolean.getBoolean("heroes.preview.styles")) return;
        var mc = Minecraft.getInstance();
        ticks++;
        if (!started && ticks > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.options.pauseOnLostFocus = false;
            mc.options.hideGui = true;
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("Styles-" + System.currentTimeMillis(),
                    new LevelSettings("Style preview", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(7L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        var server = mc.getSingleplayerServer();
        if (step == -1) {
            if (++wait < 60) return;
            // Go to the stage first, so its chunks are loaded before anyone is put there.
            server.execute(() -> {
                var level = server.overworld();
                level.setDayTime(6000);
                var spawn = level.getSharedSpawnPos();
                STAGE = new Vec3(spawn.getX() + 64.5, -60, spawn.getZ() + 64.5);
                var p = server.getPlayerList().getPlayer(mc.player.getUUID());
                p.getAbilities().flying = true; p.onUpdateAbilities();
                p.teleportTo(level, STAGE.x, STAGE.y + 2.5, STAGE.z - 11, 0, 10);
            });
            step = -2; wait = 0;
            return;
        }
        if (step == -2) {
            if (++wait < 50) return;
            server.execute(() -> {
                var level = server.overworld();
                Villager v = EntityType.VILLAGER.create(level);
                v.setNoAi(true); v.setInvulnerable(true); v.setPersistenceRequired();
                v.moveTo(STAGE.x, STAGE.y, STAGE.z, 180, 0);
                v.setYHeadRot(180);
                level.addFreshEntity(v);
                stand = v;
            });
            step = 0; wait = 0;
            return;
        }
        if (step >= SHOTS.size()) { if (++wait > 20) mc.stop(); return; }
        Shot s = SHOTS.get(step);
        if (wait == 0) {
            SetPieceClient.clear();
            Vec3 eye = STAGE.add(0, s.height, -s.distance);
            float pitch = s.pitch;
            server.execute(() -> {
                var p = server.getPlayerList().getPlayer(mc.player.getUUID());
                p.getAbilities().flying = true; p.onUpdateAbilities();
                p.teleportTo(server.overworld(), eye.x, eye.y, eye.z, 0, pitch);
            });
        }
        wait++;
        if (wait == 6) {
            int bound = stand == null ? -1 : stand.getId();
            Vec3 toCamera = new Vec3(0, 0, -1);
            Vec3 origin, target;
            switch (s.place) {
                case AIMED -> { origin = STAGE.add(-5, 0, -5); target = STAGE; }
                case LINE -> { origin = STAGE.add(-Math.max(5, s.scale / 2), 0, 0); target = STAGE.add(Math.max(5, s.scale / 2), 0, 0); }
                case FACE -> {
                    origin = s.piece == Piece.FIREWALL ? STAGE.add(0, 0, 2.5) : s.piece == Piece.SHADOW_STRIKE ? STAGE.add(0, 0, 4) : STAGE;
                    target = origin.add(toCamera);
                }
                case BOUND -> {
                    origin = s.piece == Piece.HIVE_MIND ? STAGE.add(3, 0, 3) : STAGE;
                    target = s.piece == Piece.GATHERING_HAND ? STAGE.add(5, 0, -3) : s.piece == Piece.FORGE_CANNON ? STAGE.add(6, 1, -6) : STAGE;
                }
                default -> { origin = STAGE; target = STAGE.add(toCamera); }
            }
            boolean follows = s.place == Place.BOUND;
            SetPieceClient.play(s.piece, origin, target, s.scale, follows ? bound : -1, 1234 + step);
            if (s.piece == Piece.TESLA_SPIRE) SetPieceClient.play(Piece.TESLA_ZAP, STAGE.add(0, 3.2, 0), STAGE.add(3, 1, -3), 1, -1, 7);
            began = mc.level.getGameTime();
            shotIndex = 0;
        }
        if (wait > 6 && shotIndex < s.ages.length && mc.level.getGameTime() - began >= s.ages[shotIndex]) {
            pending = String.format("styles/%02d-%s-%d.png", step, s.piece.name().toLowerCase(), s.ages[shotIndex]);
            shotIndex++;
        }
        if (wait > 6 && shotIndex >= s.ages.length && pending == null) { step++; wait = 0; }
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post e) {
        if (pending == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(out.getParent()); image.writeToFile(out);
            System.out.println("STYLE_CAPTURE " + pending + " fps=" + mc.getFps());
        } catch (Exception ex) { throw new RuntimeException(ex); }
        pending = null;
    }
}
