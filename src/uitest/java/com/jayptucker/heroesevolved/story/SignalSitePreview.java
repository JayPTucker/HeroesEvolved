package com.jayptucker.heroesevolved.story;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

/** Finds a naturally generated Last Signal site and photographs its route from player height. */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class SignalSitePreview {
    private static final int SHOT_TICKS = 36;
    private static final int EXTRACTION_TICKS = 36;

    private record Shot(String name, Vec3 eye, Vec3 look) {}

    private static final List<Shot> SHOTS = List.of(
            new Shot("01-arrival", new Vec3(16.5, 9.6, 4.5), new Vec3(16.5, 12.2, 12.5)),
            new Shot("02-relay", new Vec3(15.2, 9.65, 13.8), new Vec3(11.5, 9.4, 14.8)),
            new Shot("03-stair", new Vec3(16.5, 9.65, 16.6), new Vec3(16.5, 3.5, 26.5)),
            new Shot("04-controls", new Vec3(17.5, 3.65, 24.7), new Vec3(15.5, 4.15, 30.5)),
            new Shot("05-isolator", new Vec3(14.3, 3.65, 25.5), new Vec3(10.3, 3.85, 25.5)),
            new Shot("06-coolant", new Vec3(21.5, 3.65, 27.5), new Vec3(25.2, 3.85, 27.5)),
            new Shot("07-stasis", new Vec3(16.6, 3.65, 34.0), new Vec3(17.9, 4.1, 42.5)),
            new Shot("08-pod", new Vec3(21.2, 3.65, 39.4), new Vec3(24.5, 3.5, 43.5)),
            new Shot("09-egress", new Vec3(37.4, 3.65, 37.2), new Vec3(37.4, 8.2, 46.1)),
            new Shot("10-pickup", new Vec3(37.2, 9.65, 46.2), new Vec3(38.4, 9.7, 49.4)),
            new Shot("11-overview", new Vec3(41.0, 27.0, 0.8), new Vec3(21.0, 6.5, 23.0)),
            new Shot("12-field-access", new Vec3(36.2, 9.65, 51.8), new Vec3(27.8, 2.7, 51.8)),
            new Shot("13-hillside-access", new Vec3(28.5, 17.6, 1.6), new Vec3(19.1, 9.7, 1.6)));

    private static boolean started;
    private static boolean placed;
    private static int ticks;
    private static int siteTicks;
    private static String pending;
    private static volatile SignalSite site;
    private static volatile RuntimeException failure;
    private static volatile boolean extracted;

    private SignalSitePreview() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("heroes.preview.signalSite")) return;
        if (failure != null) throw new IllegalStateException("Last Signal preview failed", failure);
        Minecraft mc = Minecraft.getInstance();
        if (!started && ++ticks > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.options.gamma().set(1.0);
            mc.options.hideGui = true;
            mc.options.pauseOnLostFocus = false;
            GameRules rules = new GameRules();
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("SignalSite-" + System.currentTimeMillis(),
                    new LevelSettings("Last Signal build preview", GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                            rules, WorldDataConfiguration.DEFAULT), new WorldOptions(2026L, true, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.NORMAL).createWorldDimensions(),
                    mc.screen);
        }
        var server = mc.getSingleplayerServer();
        if (server == null || mc.player == null || mc.level == null) return;
        if (!placed) {
            placed = true;
            server.execute(() -> {
                try {
                    var level = server.overworld();
                    BlockPos arrival = SignalSite.locate(level, level.getSharedSpawnPos());
                    if (arrival == null) throw new IllegalStateException("No Last Signal site within the preview search radius");
                    level.getChunkAt(arrival);
                    SignalSite located = SignalSite.at(level, arrival);
                    if (located == null) throw new IllegalStateException("Located structure did not contain its Last Signal arrival");
                    var bounds = located.piece().getBoundingBox();
                    for (int x = bounds.minX() >> 4; x <= bounds.maxX() >> 4; x++) {
                        for (int z = bounds.minZ() >> 4; z <= bounds.maxZ() >> 4; z++) level.getChunk(x, z);
                    }
                    checkTerrain(located, new BlockPos(16, 8, 0), new BlockPos(16, 8, -1));
                    checkTerrain(located, new BlockPos(28, 16, 0), new BlockPos(28, 16, -1));
                    checkTerrain(located, new BlockPos(36, 8, 53), new BlockPos(36, 8, 54));
                    checkTerrain(located, new BlockPos(27, 1, 53), new BlockPos(27, 1, 54));
                    for (String marker : List.of("relay", "isolator", "coolant", "exit")) {
                        if (!level.getBlockState(located.at(marker)).is(Blocks.LEVER))
                            throw new IllegalStateException("Generated structure is missing its " + marker + " lever");
                    }
                    System.out.println("SIGNAL_SITE_GENERATED " + located.origin() + " " + located.piece().getRotation());
                    visit(mc, located, SHOTS.getFirst());
                    site = located;
                } catch (RuntimeException exception) {
                    failure = exception;
                }
            });
        }
        if (site == null) return;

        int elapsed = siteTicks++;
        if (elapsed < 48) return;
        int index = (elapsed - 48) / SHOT_TICKS;
        if (index >= SHOTS.size()) {
            int extractionTick = elapsed - 48 - SHOTS.size() * SHOT_TICKS;
            if (extractionTick == 0 || extractionTick == EXTRACTION_TICKS) server.execute(() -> {
                try {
                    var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                    if (extractionTick == 0) stageExtraction(mc, site);
                    else checkExtraction(player, site);
                } catch (RuntimeException exception) {
                    failure = exception;
                }
            });
            if (extracted && pending == null) mc.stop();
            return;
        }
        Shot shot = SHOTS.get(index);
        int shotTick = (elapsed - 48) % SHOT_TICKS;
        if (shotTick == 0) server.execute(() -> {
            try {
                var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                if (index == 2) operate(player, site, "relay", LastSignalService.Phase.ISOLATE);
                if (index == 5) operate(player, site, "isolator", LastSignalService.Phase.COOL);
                if (index == 6) {
                    operate(player, site, "coolant", LastSignalService.Phase.RESCUE);
                    checkEscortPaths(site);
                }
                visit(mc, site, shot);
            } catch (RuntimeException exception) {
                failure = exception;
            }
        });
        if (shotTick == SHOT_TICKS - 1) pending = shot.name();
    }

    private static void checkTerrain(SignalSite site, BlockPos approach, BlockPos adjoining) {
        BlockPos entrance = at(site, approach);
        BlockPos outside = at(site, adjoining);
        site.level().getChunkAt(outside);
        int surface = site.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, outside.getX(), outside.getZ());
        System.out.println("SIGNAL_SITE_APPROACH " + entrance + " outside feet " + surface);
    }

    private static void operate(ServerPlayer player, SignalSite site, String marker, LastSignalService.Phase expected) {
        BlockPos pos = site.at(marker);
        var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        var use = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, pos, hit);
        LastSignalService.console(use);
        if (!use.isCanceled() || LastSignalService.phase(player) != expected)
            throw new IllegalStateException("Using " + marker + " did not advance the mission to " + expected);
        System.out.println("SIGNAL_SITE_CONTROL " + marker + " " + expected);
    }

    private static void checkEscortPaths(SignalSite site) {
        List<BlockPos> waypoints = List.of(at(site, new BlockPos(20, 2, 36)),
                at(site, new BlockPos(34, 2, 36)), site.at("extract"));
        for (BlockPos pos : site.all("pod")) {
            Villager captive = sleeperAt(site, pos);
            captive.setOnGround(true);
            for (BlockPos waypoint : waypoints) {
                var route = captive.getNavigation().createPath(waypoint, 0);
                if (route == null || !route.canReach()) {
                    throw new IllegalStateException("No escort path from " + captive.blockPosition() + " to " + waypoint);
                }
                stand(captive, waypoint);
            }
            System.out.println("SIGNAL_SITE_WALK " + pos + " true");
            stand(captive, pos);
        }
    }

    private static Villager sleeperAt(SignalSite site, BlockPos pos) {
        var sleepers = site.level().getEntitiesOfClass(Villager.class, new AABB(pos).inflate(1));
        if (sleepers.size() != 1) throw new IllegalStateException("Expected one released sleeper at " + pos);
        return sleepers.getFirst();
    }

    private static void stageExtraction(Minecraft mc, SignalSite site) {
        visit(mc, site, SHOTS.get(9));
        var pods = site.all("pod");
        stand(sleeperAt(site, pods.get(0)), at(site, new BlockPos(35, 8, 49)));
        stand(sleeperAt(site, pods.get(1)), at(site, new BlockPos(37, 8, 49)));
    }

    private static void checkExtraction(ServerPlayer player, SignalSite site) {
        if (LastSignalService.rescued(player) != 2) throw new IllegalStateException("Pickup did not count both sleepers");
        Vec3 before = player.position();
        var level = player.level();
        operate(player, site, "exit", LastSignalService.Phase.COMPLETE);
        if (player.level() != level || !player.position().equals(before))
            throw new IllegalStateException("Extraction moved the player from the site");
        System.out.println("SIGNAL_SITE_EXTRACTION " + player.blockPosition() + " " + player.level().dimension().location());
        extracted = true;
    }

    private static void stand(Villager captive, BlockPos pos) {
        captive.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    }

    private static BlockPos at(SignalSite site, BlockPos offset) {
        return site.origin().offset(offset.rotate(site.piece().getRotation()));
    }

    private static Vec3 at(SignalSite site, Vec3 offset) {
        Vec3 center = Vec3.atBottomCenterOf(site.origin());
        double x = offset.x - 0.5;
        double z = offset.z - 0.5;
        return switch (site.piece().getRotation()) {
            case NONE -> center.add(x, offset.y, z);
            case CLOCKWISE_90 -> center.add(-z, offset.y, x);
            case CLOCKWISE_180 -> center.add(-x, offset.y, -z);
            case COUNTERCLOCKWISE_90 -> center.add(z, offset.y, -x);
        };
    }

    private static void visit(Minecraft mc, SignalSite site, Shot shot) {
        var player = site.level().getServer().getPlayerList().getPlayer(mc.player.getUUID());
        Vec3 eye = at(site, shot.eye());
        Vec3 delta = at(site, shot.look()).subtract(eye);
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z)));
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        player.teleportTo(site.level(), eye.x, eye.y - player.getEyeHeight(), eye.z, yaw, pitch);
    }

    @SubscribeEvent
    public static void render(RenderFrameEvent.Post event) {
        if (pending == null) return;
        Minecraft mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            var out = mc.gameDirectory.toPath().resolve("screenshots").resolve("signal-" + pending + ".png");
            Files.createDirectories(out.getParent());
            image.writeToFile(out);
            System.out.println("SIGNAL_SITE_CAPTURE " + out);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save Last Signal preview " + pending, exception);
        }
        pending = null;
    }
}
