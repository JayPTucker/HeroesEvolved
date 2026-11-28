package com.jayptucker.heroesevolved.trailer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Finds a place to film without generating the world first: it asks the world generator for the ground's height (and
 * the biome) across a wide area, and picks a dry, flat hilltop in open country that drops away to the west, so the
 * sunset sits on an open horizon behind the fight. {@link #clearTrees} then takes out any tree on the stage itself.
 */
public final class Scout {
    /** A place to film: the ground under the stage's middle, how good it is, and what it is. */
    public record Spot(BlockPos ground, double score, String biome, double prominence, double westDrop) {}

    private static final Set<ResourceKey<Biome>> OPEN = Set.of(Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS, Biomes.MEADOW,
            Biomes.SAVANNA, Biomes.SAVANNA_PLATEAU, Biomes.CHERRY_GROVE);

    private final ServerLevel level;
    private final ChunkGenerator generator;
    private final RandomState random;

    private Scout(ServerLevel level) {
        this.level = level;
        this.generator = level.getChunkSource().getGenerator();
        this.random = level.getChunkSource().randomState();
    }

    private int height(int x, int z) { return generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, random); }

    private boolean dry(int x, int z, int h) {
        return generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, random) == h;
    }

    private Holder<Biome> biome(int x, int y, int z) {
        return generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z), random.sampler());
    }

    /** The lowest and highest ground on a ring of {@code points} at {@code radius} round x, z. */
    private int[] ring(int x, int z, double radius, int points) {
        int low = Integer.MAX_VALUE, high = Integer.MIN_VALUE;
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            int h = height(x + (int) Math.round(Math.cos(a) * radius), z + (int) Math.round(Math.sin(a) * radius));
            low = Math.min(low, h);
            high = Math.max(high, h);
        }
        return new int[] {low, high};
    }

    private double mean(int x, int z, double radius, int points) {
        double sum = 0;
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            sum += height(x + (int) Math.round(Math.cos(a) * radius), z + (int) Math.round(Math.sin(a) * radius));
        }
        return sum / points;
    }

    /**
     * The best hilltop between {@code near} and {@code far} blocks of the world's middle (well clear of the bunker under
     * spawn), or the best dry, flat open ground if no hilltop will do, or null if the whole area is sea.
     */
    public static Spot find(ServerLevel level, int near, int far) {
        Scout scout = new Scout(level);
        int sea = level.getSeaLevel();
        long began = System.currentTimeMillis();
        record Candidate(int x, int z, int h, double rough) {}
        // Each height is a whole noise column (a millisecond or more), so the places are asked about in parallel;
        // the world generator itself works on many threads at once, so this is safe.
        int step = 48, across = 2 * far / step + 1;
        List<Candidate> first = IntStream.range(0, across * across).parallel().mapToObj(i -> {
            int x = -far + i / across * step, z = -far + i % across * step;
            if (Math.abs(x) < near && Math.abs(z) < near) return null;
            int h = scout.height(x, z);
            if (h < sea + 4 || !scout.dry(x, z, h)) return null;
            int[] close = scout.ring(x, z, 5, 6);
            if (close[1] - close[0] > 2 || Math.abs(close[0] - h) > 2) return null;
            if (!OPEN.contains(scout.biome(x, h, z).unwrapKey().orElse(null))) return null;
            return new Candidate(x, z, h, h - scout.mean(x, z, 32, 8));
        }).filter(java.util.Objects::nonNull).sorted(Comparator.comparingDouble(Candidate::rough).reversed()).toList();
        List<Spot> spots = first.subList(0, Math.min(60, first.size())).parallelStream().map(c -> {
            int[] stage = scout.ring(c.x, c.z, 9, 16);
            if (stage[1] - stage[0] > 3 || stage[1] > c.h + 2) return null;
            double prominence = c.h - scout.mean(c.x, c.z, 28, 12);
            // How far the land falls away to the west, where the sun goes down.
            double west = 0;
            for (int d = 40; d <= 100; d += 20) west += c.h - scout.height(c.x - d, c.z);
            west /= 4;
            String name = scout.biome(c.x, c.h, c.z).unwrapKey().map(k -> k.location().getPath()).orElse("?");
            return new Spot(new BlockPos(c.x, c.h, c.z), prominence + .5 * Math.min(west, 30), name, prominence, west);
        }).filter(java.util.Objects::nonNull).toList();
        Spot best = spots.stream().filter(s -> s.prominence >= 2).max(Comparator.comparingDouble(Spot::score)).orElse(null);
        Spot chosen = best != null ? best : spots.stream().max(Comparator.comparingDouble(Spot::score)).orElse(null);
        System.out.println("TRAILER_SCOUT " + across * across + " places in " + (System.currentTimeMillis() - began) + " ms, "
                + first.size() + " open and flat; " + (best != null ? "hilltop " : "no hilltop, flat ground ") + chosen);
        return chosen;
    }

    /** The highest ground (leaves and plants ignored) on a ring of {@code radius} round {@code middle}. */
    public static int highest(ServerLevel level, BlockPos middle, double radius) {
        int top = middle.getY();
        for (int i = 0; i < 32; i++) {
            double a = Math.PI * 2 * i / 32;
            int x = middle.getX() + (int) Math.round(Math.cos(a) * radius), z = middle.getZ() + (int) Math.round(Math.sin(a) * radius);
            top = Math.max(top, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
        }
        return top;
    }

    /**
     * Thins the grass on the fighting floor so it hides no feet or kicks: all of it within {@code radius - 2}, then less
     * and less out to {@code radius + 3}, so the edge is ragged rather than a mown circle.
     */
    public static int thinGrass(ServerLevel level, BlockPos middle, int radius) {
        var random = new java.util.Random(middle.asLong());
        int thinned = 0;
        var cursor = new BlockPos.MutableBlockPos();
        for (int dx = -radius - 3; dx <= radius + 3; dx++)
            for (int dz = -radius - 3; dz <= radius + 3; dz++) {
                double r = Math.sqrt(dx * dx + dz * dz), keep = Math.clamp((r - (radius - 2)) / 5, 0, 1);
                if (random.nextDouble() < keep) continue;
                for (int dy = -3; dy <= 3; dy++) {
                    cursor.set(middle.getX() + dx, middle.getY() + dy, middle.getZ() + dz);
                    if (!level.hasChunkAt(cursor)) continue;
                    var state = level.getBlockState(cursor);
                    if (state.is(Blocks.SHORT_GRASS) || state.is(Blocks.FERN)) {
                        level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 2);
                        thinned++;
                    }
                }
            }
        return thinned;
    }

    /** Takes every log and leaf off the stage (a circle of {@code radius} round {@code middle}), so nothing blocks the camera. */
    public static int clearTrees(ServerLevel level, BlockPos middle, int radius) {
        int cleared = 0;
        var cursor = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++)
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) continue;
                for (int dy = -2; dy <= 28; dy++) {
                    cursor.set(middle.getX() + dx, middle.getY() + dy, middle.getZ() + dz);
                    if (!level.hasChunkAt(cursor)) continue;
                    var state = level.getBlockState(cursor);
                    if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || state.is(Blocks.TALL_GRASS) || state.is(Blocks.LARGE_FERN)) {
                        level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 2);
                        cleared++;
                    }
                }
            }
        return cleared;
    }
}
