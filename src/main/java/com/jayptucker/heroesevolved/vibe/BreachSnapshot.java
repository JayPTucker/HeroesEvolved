package com.jayptucker.heroesevolved.vibe;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

/**
 * The terrain round one breach mouth, as the other mouth's viewers see it through the opening: every block state and
 * its sky and block light in a sphere about the mouth, plus the colours the client can't look up for a world it isn't in
 * (the sky, grass, leaves and water there). Sent once when a breach opens, run-length packed.
 */
public record BreachSnapshot(BlockPos origin, int sizeX, int sizeY, int sizeZ, int[] states, byte[] light,
                             int sky, int grass, int foliage, int water) {
    /** Equal reach in every direction, including above and below tilted openings. */
    public static final int REACH = 30;
    private static final int LIMIT = REACH * 2 + 1;

    /** Check the actual full chunks before capturing so their edges never become holes in the view. */
    static boolean ready(ServerLevel level, Vec3 center) {
        BlockPos base = BlockPos.containing(center);
        for (int x = (base.getX() - REACH) >> 4; x <= (base.getX() + REACH) >> 4; x++) {
            for (int z = (base.getZ() - REACH) >> 4; z <= (base.getZ() + REACH) >> 4; z++) {
                if (level.getChunkSource().getChunkNow(x, z) == null) return false;
            }
        }
        return true;
    }

    public static BreachSnapshot capture(ServerLevel level, Vec3 center) {
        BlockPos base = BlockPos.containing(center);
        BlockPos origin = base.offset(-REACH, -REACH, -REACH);
        int sx = LIMIT, sy = LIMIT, sz = LIMIT;
        int[] states = new int[sx * sy * sz];
        byte[] light = new byte[states.length];
        int air = Block.getId(Blocks.AIR.defaultBlockState());
        java.util.Arrays.fill(states, air);
        java.util.Arrays.fill(light, (byte) 0xF0);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int cx = origin.getX() >> 4; cx <= (base.getX() + REACH) >> 4; cx++) {
            for (int cz = origin.getZ() >> 4; cz <= (base.getZ() + REACH) >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                int firstX = (cx << 4) - origin.getX(), firstZ = (cz << 4) - origin.getZ();
                int minX = Math.max(0, firstX), maxX = Math.min(sx, firstX + 16);
                int minZ = Math.max(0, firstZ), maxZ = Math.min(sz, firstZ + 16);
                for (int x = minX; x < maxX; x++) {
                    for (int z = minZ; z < maxZ; z++) {
                        int dx = x - REACH, dz = z - REACH;
                        int heightSqr = REACH * REACH - dx * dx - dz * dz;
                        if (heightSqr < 0) continue;
                        int height = (int) Math.sqrt(heightSqr);
                        int bottom = Math.max(base.getY() - height, level.getMinBuildHeight());
                        int top = Math.min(base.getY() + height, level.getMaxBuildHeight() - 1);
                        for (int y = bottom; y <= top; y++) {
                            int i = index(x, y - origin.getY(), z, sy, sz);
                            pos.set(origin.getX() + x, y, origin.getZ() + z);
                            states[i] = Block.getId(chunk.getBlockState(pos));
                            int skyLight = level.getBrightness(LightLayer.SKY, pos), blockLight = level.getBrightness(LightLayer.BLOCK, pos);
                            light[i] = (byte) (skyLight << 4 | blockLight);
                        }
                    }
                }
            }
        }
        Biome biome = level.getBiome(base).value();
        int sky = level.dimensionType().hasSkyLight() && !level.dimensionType().hasCeiling()
                ? dim(biome.getSkyColor(), daylight(level)) : biome.getFogColor();
        return new BreachSnapshot(origin, sx, sy, sz, states, light, sky,
                biome.getGrassColor(base.getX(), base.getZ()), biome.getFoliageColor(), biome.getWaterColor());
    }

    /** How lit the sky is there now, worked out as the client works out its own sky. */
    private static float daylight(ServerLevel level) {
        float angle = level.getTimeOfDay(1);
        return Math.clamp((float) Math.cos(angle * Math.PI * 2) * 2 + .5F, 0.06F, 1);
    }

    private static int dim(int rgb, float by) {
        int r = (int) ((rgb >> 16 & 255) * by), g = (int) ((rgb >> 8 & 255) * by), b = (int) ((rgb & 255) * by);
        return r << 16 | g << 8 | b;
    }

    private static int index(int x, int y, int z, int sy, int sz) { return (x * sz + z) * sy + y; }

    public boolean contains(int x, int y, int z) {
        int dx = x - origin.getX(), dy = y - origin.getY(), dz = z - origin.getZ();
        return dx >= 0 && dy >= 0 && dz >= 0 && dx < sizeX && dy < sizeY && dz < sizeZ;
    }

    public BlockState state(BlockPos pos) {
        if (!contains(pos.getX(), pos.getY(), pos.getZ())) return Blocks.AIR.defaultBlockState();
        return Block.stateById(states[index(pos.getX() - origin.getX(), pos.getY() - origin.getY(), pos.getZ() - origin.getZ(), sizeY, sizeZ)]);
    }

    /** Packed light, sky in the high four bits and block in the low; open sky outside the box. */
    public int light(BlockPos pos) {
        if (!contains(pos.getX(), pos.getY(), pos.getZ())) return 0xF0;
        return light[index(pos.getX() - origin.getX(), pos.getY() - origin.getY(), pos.getZ() - origin.getZ(), sizeY, sizeZ)] & 255;
    }

    /** The world position of the block at {@code i} in the arrays. */
    public BlockPos at(int i) {
        int y = i % sizeY, rest = i / sizeY, z = rest % sizeZ, x = rest / sizeZ;
        return origin.offset(x, y, z);
    }

    public int solidCount() {
        int air = Block.getId(Blocks.AIR.defaultBlockState()), n = 0;
        for (int state : states) if (state != air) n++;
        return n;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(origin);
        buf.writeVarInt(sizeX);
        buf.writeVarInt(sizeY);
        buf.writeVarInt(sizeZ);
        for (int i = 0; i < states.length; ) {
            int run = 1;
            while (i + run < states.length && states[i + run] == states[i]) run++;
            buf.writeVarInt(run);
            buf.writeVarInt(states[i]);
            i += run;
        }
        for (int i = 0; i < light.length; ) {
            int run = 1;
            while (i + run < light.length && light[i + run] == light[i]) run++;
            buf.writeVarInt(run);
            buf.writeByte(light[i]);
            i += run;
        }
        buf.writeInt(sky);
        buf.writeInt(grass);
        buf.writeInt(foliage);
        buf.writeInt(water);
    }

    public static BreachSnapshot read(FriendlyByteBuf buf) {
        BlockPos origin = buf.readBlockPos();
        int sx = Math.clamp(buf.readVarInt(), 1, LIMIT), sy = Math.clamp(buf.readVarInt(), 1, LIMIT), sz = Math.clamp(buf.readVarInt(), 1, LIMIT);
        int[] states = new int[sx * sy * sz];
        byte[] light = new byte[states.length];
        for (int i = 0; i < states.length; ) {
            int run = buf.readVarInt(), state = buf.readVarInt();
            if (run <= 0 || i + run > states.length) throw new IllegalArgumentException("Bad breach snapshot");
            java.util.Arrays.fill(states, i, i + run, state);
            i += run;
        }
        for (int i = 0; i < light.length; ) {
            int run = buf.readVarInt();
            byte value = buf.readByte();
            if (run <= 0 || i + run > light.length) throw new IllegalArgumentException("Bad breach snapshot");
            java.util.Arrays.fill(light, i, i + run, value);
            i += run;
        }
        return new BreachSnapshot(origin, sx, sy, sz, states, light, buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt());
    }
}
