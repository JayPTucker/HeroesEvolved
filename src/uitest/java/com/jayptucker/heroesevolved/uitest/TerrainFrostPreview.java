package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.client.effects.TempestCamera;
import com.jayptucker.heroesevolved.cryo.CryoService;
import com.jayptucker.heroesevolved.cryo.TerrainFrost;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.input.ModKeyMappings;
import com.jayptucker.heroesevolved.network.PlayerPowerSyncService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
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

/**
 * Photographs terrain freezing under a held Ice Blast, pressed with the real key in survival: a stone wall's 3 x 3 patch
 * cracking and frosting from the aimed block out, the brittle ice broken by hand, an obsidian wall taking longer, and
 * pools of water and lava turning to ice and obsidian. Run with {@code tools/terrain-frost-preview.init.gradle}; shots go
 * to {@code build/terrain-frost-preview/screenshots/}. Each shot logs the patch's blocks and their progress.
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class TerrainFrostPreview {
    private static final BlockPos STONE = new BlockPos(202, -59, 206), OBSIDIAN = new BlockPos(212, -59, 206);
    private static final BlockPos WATER = new BlockPos(221, -61, 206), LAVA = new BlockPos(227, -61, 206);
    private static boolean started;
    private static int ticks, world;
    private static String pending;
    private static BlockPos watching;

    private TerrainFrostPreview() {}

    private static void server(Minecraft mc, Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            var p = server.getPlayerList().getPlayer(mc.player.getUUID());
            if (p != null) action.accept(p);
        });
    }

    /** Stands the player at {@code x, z} on the floor, looking at {@code at}, rested and off cooldown. */
    private static void stand(ServerPlayer p, double x, double z, Vec3 at) {
        CryoService.stopAll(p);
        Vec3 eye = new Vec3(x, -60 + p.getEyeHeight(), z), delta = at.subtract(eye);
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, delta.horizontalDistance()));
        p.connection.teleport(x, -60, z, yaw, pitch);
        p.setDeltaMovement(Vec3.ZERO);
        p.resetFallDistance();
        PlayerEnergyService.restore(p, 999);
        com.jayptucker.heroesevolved.cooldown.CooldownService.clearAll(p);
    }

    private static void build(ServerLevel level) {
        for (int x = 196; x <= 232; x++)
            for (int z = 199; z <= 210; z++) {
                level.setBlockAndUpdate(new BlockPos(x, -61, z), Blocks.STONE.defaultBlockState());
                for (int y = -60; y <= -55; y++) level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        for (int x = 198; x <= 206; x++)
            for (int y = -60; y <= -57; y++) level.setBlockAndUpdate(new BlockPos(x, y, 206), Blocks.STONE_BRICKS.defaultBlockState());
        for (int x = 209; x <= 215; x++)
            for (int y = -60; y <= -57; y++) level.setBlockAndUpdate(new BlockPos(x, y, 206), Blocks.OBSIDIAN.defaultBlockState());
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                level.setBlockAndUpdate(WATER.offset(dx, 0, dz), Blocks.WATER.defaultBlockState());
                level.setBlockAndUpdate(LAVA.offset(dx, 0, dz), Blocks.LAVA.defaultBlockState());
            }
    }

    private static void power(ServerPlayer player) {
        var power = ModAbilities.CRYOKINESIS_ID;
        player.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(power).unlock(power));
        player.setData(ModDataAttachments.POWER_INTRO_HISTORY, player.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(power));
        PlayerProgressionService.setLevel(player, 5);
        PlayerEnergyService.restore(player, 999);
        PlayerPowerSyncService.sync(player);
    }

    /** Logs the aimed block, an edge and a corner of the patch round {@code center} (a wall, or a pool seen from above). */
    private static void report(Minecraft mc, String shot, BlockPos center, boolean pool) {
        server(mc, p -> {
            var level = p.serverLevel();
            BlockPos edge = pool ? center.east() : center.above(), corner = pool ? center.east().south() : center.above().east();
            StringBuilder line = new StringBuilder("TERRAIN_SHOT " + shot);
            for (BlockPos pos : new BlockPos[]{center, edge, corner})
                line.append(String.format(" | %s %.2f", level.getBlockState(pos).getBlock().getDescriptionId()
                        .replace("block.minecraft.", "").replace("block.heroesevolved.", ""), TerrainFrost.progress(level, pos)));
            System.out.println(line);
        });
    }

    private static void shoot(Minecraft mc, String name, BlockPos center, boolean pool) {
        report(mc, name, center, pool);
        pending = name + ".png";
    }

    private static void side(Vec3 at, BlockPos look) {
        TempestCamera.fix(at, Vec3.atCenterOf(look));
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("heroes.preview.terrainfrost")) return;
        var mc = Minecraft.getInstance();
        if (!started && ++ticks > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.options.pauseOnLostFocus = false;
            mc.options.guiScale().set(2);
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
            rules.getRule(GameRules.RULE_DOFIRETICK).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("TerrainFrost-" + System.currentTimeMillis(),
                    new LevelSettings("Terrain frost preview", GameType.SURVIVAL, false, Difficulty.PEACEFUL, true, rules,
                            WorldDataConfiguration.DEFAULT),
                    new WorldOptions(77L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(),
                    mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        switch (++world) {
            case 104 -> server(mc, com.jayptucker.heroesevolved.story.prologue.PrologueService::skip);
            case 115 -> server(mc, p -> {
                com.jayptucker.heroesevolved.story.bunker.CryoChamberService.release(p);
                var level = p.server.overworld();
                for (int x = 12; x <= 14; x++)
                    for (int z = 12; z <= 13; z++) level.setChunkForced(x, z, true);
                p.teleportTo(level, 202.5, -60, 202.5, 0, 0);
                p.setGameMode(GameType.SURVIVAL);
                level.setDayTime(6000);
                build(level);
                power(p);
                System.out.println("TERRAIN_STAGE " + p.level().dimension().location() + " "
                        + level.getBlockState(BlockPos.containing(202.5, -61, 202.5)));
            });
            case 190 -> server(mc, p -> stand(p, 202.5, 202.5, Vec3.atCenterOf(STONE)));
            case 195 -> {
                mc.gui.getChat().clearMessages(true);
                mc.getToasts().clear();
                mc.options.hideGui = true;
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                side(new Vec3(197.5, -56.8, 201), STONE);
            }
            // Stone: the aimed block cracks first, then the edges, then the corners.
            case 200 -> ModKeyMappings.ABILITY_1.setDown(true);
            case 220 -> shoot(mc, "01-stone-cracking", STONE, false);
            case 238 -> shoot(mc, "02-stone-centre-turns", STONE, false);
            case 252 -> shoot(mc, "03-stone-edges-turn", STONE, false);
            case 272 -> shoot(mc, "04-stone-patch-frozen", STONE, false);
            case 276 -> {
                ModKeyMappings.ABILITY_1.setDown(false);
                TempestCamera.fix(null, null);
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                server(mc, p -> stand(p, 202.5, 204.2, Vec3.atCenterOf(STONE)));
            }
            case 284 -> shoot(mc, "05-brittle-ice-close", STONE, false);
            case 288 -> mc.options.keyAttack.setDown(true);
            case 330 -> mc.options.keyAttack.setDown(false);
            case 334 -> shoot(mc, "06-ice-broken-by-hand", STONE, false);
            // Obsidian: the same patch, much slower.
            case 340 -> {
                server(mc, p -> stand(p, 212.5, 202.5, Vec3.atCenterOf(OBSIDIAN)));
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                side(new Vec3(207.5, -56.8, 201), OBSIDIAN);
            }
            case 346 -> ModKeyMappings.ABILITY_1.setDown(true);
            case 386 -> shoot(mc, "07-obsidian-cracking", OBSIDIAN, false);
            case 432 -> shoot(mc, "08-obsidian-centre-turns", OBSIDIAN, false);
            case 506 -> shoot(mc, "09-obsidian-patch-frozen", OBSIDIAN, false);
            // Water to ice.
            case 510 -> {
                ModKeyMappings.ABILITY_1.setDown(false);
                server(mc, p -> stand(p, 221.5, 202.2, Vec3.atCenterOf(WATER).add(0, .38, 0)));
                side(new Vec3(217, -56.5, 203), WATER);
            }
            case 518 -> ModKeyMappings.ABILITY_1.setDown(true);
            case 527 -> shoot(mc, "10-water-skinning", WATER, true);
            case 548 -> shoot(mc, "11-water-ice", WATER, true);
            // Lava to obsidian.
            case 552 -> {
                ModKeyMappings.ABILITY_1.setDown(false);
                server(mc, p -> stand(p, 227.5, 202.2, Vec3.atCenterOf(LAVA).add(0, .38, 0)));
                side(new Vec3(223, -56.5, 203), LAVA);
            }
            case 560 -> ModKeyMappings.ABILITY_1.setDown(true);
            case 578 -> shoot(mc, "12-lava-cooling", LAVA, true);
            case 616 -> shoot(mc, "13-lava-obsidian", LAVA, true);
            case 620 -> {
                ModKeyMappings.ABILITY_1.setDown(false);
                TempestCamera.fix(null, null);
            }
            case 630 -> PreviewQuiet.quit();
            default -> {}
        }
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post event) {
        if (pending == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(out.getParent());
            image.writeToFile(out);
            System.out.println("TERRAIN_CAPTURE " + out);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        pending = null;
    }
}
