package com.jayptucker.heroesevolved.uitest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import java.nio.file.*;

/** Places the exported ruins with their real terrain processor, then photographs each in a normal world. */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class RuinsPreview {
    private static final String[] RUINS = {"burnt_house", "collapsed_house", "bennet_house", "isaac_studio"};
    private static boolean started;
    private static int ticks, worldTicks;
    private static String pending;
    private static final int[] floors = new int[4];
    private static final net.minecraft.core.BlockPos[] origins = new net.minecraft.core.BlockPos[4];
    private static final boolean OCEAN = Boolean.getBoolean("heroes.preview.ruins.ocean");
    private static int sceneIndex, sceneTicks;
    private static boolean requested;
    private static volatile boolean staged;

    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        if (!Boolean.getBoolean("heroes.preview.ruins")) return;
        var mc = Minecraft.getInstance();
        ticks++;
        if (!started && ticks > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            // This is a scenery fixture; login must not enrol it in a waking that can reclaim the camera later.
            com.jayptucker.heroesevolved.config.HeroesEvolvedConfig.COMMON.bunkerEnabled.set(false);
            mc.options.gamma().set(1.0);
            mc.options.hideGui = true;
            mc.options.pauseOnLostFocus = false;
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("Ruins-" + System.currentTimeMillis(),
                    new LevelSettings("Ruins preview", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(4242L, true, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.NORMAL).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        var server = mc.getSingleplayerServer();
        worldTicks++;
        if (worldTicks == 40) server.execute(() -> {
            var player = server.getPlayerList().getPlayer(mc.player.getUUID());
            com.jayptucker.heroesevolved.story.prologue.PrologueService.skip(player);
            com.jayptucker.heroesevolved.story.bunker.CryoChamberService.release(player);
            player.setGameMode(GameType.SPECTATOR);
            player.removeAllEffects();
        });
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        int index = sceneIndex, phase = sceneTicks;
        if (worldTicks < 200) return; // let the intro alarm overlay fade first
        if (index >= RUINS.length) { if (phase == 0) PreviewQuiet.quit(); return; }
        String name = RUINS[index];
        if (phase == 0 && !requested) { requested = true; server.execute(() -> {
            server.overworld().setDayTime(6000);
            var level = server.overworld();
            var location = OCEAN ? ocean(level, index) : new net.minecraft.core.BlockPos(200 + index * 80, 0, 0);
            int x = location.getX(), z = location.getZ();
            for (int cx = (x - 16) >> 4; cx <= (x + 40) >> 4; cx++) for (int cz = (z - 16) >> 4; cz <= (z + 40) >> 4; cz++) level.getChunk(cx, cz);
            int floor = com.jayptucker.heroesevolved.story.bunker.OdessaRuins.ground(level, x + 10, z + 10) - 1;
            floors[index] = floor;
            var at = new net.minecraft.core.BlockPos(x, floor, z);
            origins[index] = at;
            var settings = new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings();
            level.registryAccess().registryOrThrow(Registries.PROCESSOR_LIST)
                    .get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("heroesevolved", "ancient_ruins")).list().forEach(settings::addProcessor);
            server.getStructureManager().get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("heroesevolved", "ruins/" + name)).orElseThrow()
                    .placeInWorld(level, at, at, settings, level.random, 2 | 16);
            if (OCEAN) {
                // A one-cell original cupboard exercises the same barrel processor with an actual readable journal.
                var source = at.offset(9, 40, -3);
                var old = level.getBlockState(source);
                level.setBlock(source, net.minecraft.world.level.block.Blocks.BARREL.defaultBlockState(), 2 | 16);
                ((net.minecraft.world.level.block.entity.BarrelBlockEntity) level.getBlockEntity(source)).setItem(13,
                        com.jayptucker.heroesevolved.story.bunker.OdessaCaches.book(com.jayptucker.heroesevolved.story.bunker.OdessaCaches.JOURNALS.getFirst()));
                var barrel = new net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate();
                barrel.fillFromWorld(level, source, new net.minecraft.core.Vec3i(1, 1, 1), false, net.minecraft.world.level.block.Blocks.STRUCTURE_VOID);
                level.setBlock(source, old, 2 | 16);
                var cupboard = at.offset(9, 2, -3);
                barrel.placeInWorld(level, cupboard, cupboard, settings, level.random, 2 | 16);
                // The barrel now stops the surface scan, so use the sea-level cell for nearby floating boards.
                com.jayptucker.heroesevolved.structure.RuinWater.driftwood(level, new net.minecraft.core.BlockPos(cupboard.getX(), level.getSeaLevel(), cupboard.getZ()));
                System.out.println("OCEAN_RUIN " + name + " at " + at + " seabed=" + floor + " sea=" + level.getSeaLevel());
            }
            var player = server.getPlayerList().getPlayer(mc.player.getUUID());
            player.setNoGravity(true);
            player.getAbilities().flying = true; player.onUpdateAbilities();
            if (OCEAN) player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION, 10000, 0, false, false));
            if (OCEAN) player.teleportTo(server.overworld(), x + 30, floor + 12, z + 30, 135, 15);
            else player.teleportTo(server.overworld(), x - 10, floor + 16, z - 10, -45, 30);
            staged = true;
        }); }
        if (!staged) return;
        var at = origins[index];
        int x = at.getX(), z = at.getZ();
        String prefix = OCEAN ? "ocean-" : "ruins-";
        if (phase == 25) pending = prefix + name + "-outside.png";
        if (phase == 30) server.execute(() -> server.getPlayerList().getPlayer(mc.player.getUUID())
                .teleportTo(server.overworld(), x + 10, OCEAN ? server.overworld().getSeaLevel() + 16 : floors[index] + 27, z + 10, 0, 89));
        if (phase == 55) pending = prefix + name + "-above.png";
        if (phase == 57) server.execute(() -> {
            var player = server.getPlayerList().getPlayer(mc.player.getUUID());
            if (name.equals("isaac_studio")) player.teleportTo(server.overworld(), x + 11.5, floors[index] + 3, z + 10.5, 90, 0);
            else player.teleportTo(server.overworld(), x + 9.5, floors[index] + 1, z + .5, 0, 0);
        });
        if (phase == 75) pending = prefix + name + (name.equals("isaac_studio") ? "-painting.png" : "-entrance.png");
        if (++sceneTicks >= 80) { sceneTicks = 0; sceneIndex++; requested = false; staged = false; }
    }

    private static net.minecraft.core.BlockPos ocean(net.minecraft.server.level.ServerLevel level, int index) {
        var generator = level.getChunkSource().getGenerator();
        var random = level.getChunkSource().randomState();
        for (int i = 0; i < 4096; i++) {
            int x = 1200 + (i % 64) * 64, z = 1200 + (i / 64) * 64 + index * 64;
            if (!generator.getBiomeSource().getNoiseBiome((x + 10) >> 2, level.getSeaLevel() >> 2, (z + 10) >> 2, random.sampler())
                    .is(net.minecraft.tags.BiomeTags.IS_OCEAN)) continue;
            int bottom = generator.getBaseHeight(x + 10, z + 10, net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG, level, random);
            int top = generator.getBaseHeight(x + 10, z + 10, net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, level, random);
            boolean occupied = false;
            for (var previous : origins) if (previous != null && Math.abs(previous.getX() - x) < 64 && Math.abs(previous.getZ() - z) < 64) occupied = true;
            boolean deep = top - bottom >= 23;
            // Pick legible natural sites for the photographs; an isolated cave opening can be much lower than its rim.
            for (int dx : new int[]{0, 10, 20, 30}) for (int dz : new int[]{0, 10, 20, 30})
                if (Math.abs(generator.getBaseHeight(x + dx, z + dz, net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG, level, random) - bottom) > 4) deep = false;
            if (!occupied && deep) return new net.minecraft.core.BlockPos(x, bottom - 1, z);
        }
        throw new IllegalStateException("No deep water found for ocean ruin preview");
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post e) {
        if (pending == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(out.getParent()); image.writeToFile(out);
            System.out.println("RUINS_CAPTURE " + out);
        } catch (Exception ex) { throw new RuntimeException(ex); }
        pending = null;
    }
}
