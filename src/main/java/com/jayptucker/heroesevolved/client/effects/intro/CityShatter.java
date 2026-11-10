package com.jayptucker.heroesevolved.client.effects.intro;

import com.jayptucker.heroesevolved.time.intro.IntroBlast;
import com.jayptucker.heroesevolved.time.intro.city.CityGrid;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The city coming apart, on this client only. As the front passes, windows burst, leaves strip, street
 * furniture and awnings tear away, cars and loose stone are flung ahead of the dust; the heat of the
 * flash sets roofs and trees alight first. The server's city is never touched: every change is logged
 * with the moment it happened, so the rewind puts each block back as the clock runs past it, and the
 * debris, whose flight is a closed formula of time, flies home on its own.
 */
final class CityShatter {
    private record Change(BlockPos pos, BlockState was, float t) {}
    /** A piece of the city in flight: where and when it broke off, how it was thrown, when it landed. */
    private record Piece(BlockState state, float x, float y, float z, float vx, float vy, float vz,
                         float ax, float ay, float az, float spin, float scale, float t0, float landed) {}

    private static final List<Change> CHANGES = new ArrayList<>();
    private static final List<Piece> PIECES = new ArrayList<>();
    private static final int REACH = 104, PER_TICK = 420, PIECES_PER_TICK = 90, MAX_PIECES = 1700, MAX_FIRES = 230;
    private static final float GRAVITY = 0.045F, DRAG = 0.06F;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static float swept;
    private static int fires, sounds;

    private CityShatter() {}

    static void reset() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && com.jayptucker.heroesevolved.time.intro.TimeIntroService.isCity(mc.level)) restore(Float.NEGATIVE_INFINITY);
        CHANGES.clear();
        PIECES.clear();
        swept = 0;
        fires = 0;
    }

    private static void restore(float t) {
        ClientLevel level = Minecraft.getInstance().level;
        while (!CHANGES.isEmpty() && CHANGES.getLast().t > t) {
            Change change = CHANGES.removeLast();
            if (level != null) level.setBlock(change.pos, change.was, FLAGS);
        }
    }

    static void tick(float t) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || IntroScene.detonation < 0) return;
        if (IntroScene.rewinding(0)) {
            restore(t);
            return;
        }
        if (IntroScene.stopped() || t <= 0) return;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        sounds = 0;
        if (t >= 2 && t <= 30) ignite(level, cam, t);
        float front = IntroBlast.front(t) + 3;
        if (front <= swept) return;
        sweep(level, cam, t, swept, front);
        swept = front;
    }

    // ------------------------------------------------------------------
    // The shock front
    // ------------------------------------------------------------------

    private static void sweep(ClientLevel level, Vec3 cam, float t, float from, float to) {
        int budget = PER_TICK, thrown = 0;
        int cx = Mth.floor(cam.x), cz = Mth.floor(cam.z);
        double gx = IntroBlast.CENTER.x, gz = IntroBlast.CENTER.z;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = cx - REACH; x <= cx + REACH && budget > 0; x++)
            for (int z = cz - REACH; z <= cz + REACH && budget > 0; z++) {
                double d = Math.hypot(x + 0.5 - gx, z + 0.5 - gz);
                if (d < from || d >= to || !CityGrid.inCity(x, z)) continue;
                double ux = (x + 0.5 - gx) / d, uz = (z + 0.5 - gz) / d;
                // The face that took the blast: the neighbour toward ground zero.
                int sx = Math.abs(ux) > Math.abs(uz) ? -(int) Math.signum(ux) : 0, sz = sx == 0 ? -(int) Math.signum(uz) : 0;
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                boolean road = CityGrid.road(x, z);
                for (int y = top - 1; y >= CityGrid.STREET_Y && budget > 0; y--) {
                    pos.set(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || state.hasBlockEntity() || state.getDestroySpeed(level, pos) < 0 || !state.getFluidState().isEmpty()) continue;
                    float roll = CityGrid.hash(x, y, z, 6) / (float) Integer.MAX_VALUE;
                    float chance, piece, scale;
                    if (state.getSoundType() == net.minecraft.world.level.block.SoundType.GLASS) { chance = 1; piece = 0.3F; scale = 0.22F; }
                    else if (state.is(BlockTags.LEAVES)) { chance = 0.8F; piece = 0.22F; scale = 0.32F; }
                    else if (state.is(BlockTags.WOOL) || state.getBlock() instanceof CarpetBlock) { chance = 0.85F; piece = 0.6F; scale = 0.45F; }
                    else if (thin(state)) { chance = 0.65F; piece = 0.55F; scale = 0.4F; }
                    else if (road && y <= CityGrid.STREET_Y + 2) { chance = 1; piece = 0.85F; scale = 0.7F; }
                    else if (y == top - 1) { chance = 0.38F; piece = 0.7F; scale = 0.55F; }
                    else if (level.getBlockState(pos.offset(sx, 0, sz)).isAir()) {
                        chance = 0.06F + 0.2F * Mth.clamp((y - CityGrid.STREET_Y) / 70F, 0, 1);
                        piece = 0.75F;
                        scale = 0.45F + 0.25F * roll;
                    } else continue;
                    if (roll >= chance) continue;
                    BlockPos at = pos.immutable();
                    CHANGES.add(new Change(at, state, t));
                    level.setBlock(at, Blocks.AIR.defaultBlockState(), FLAGS);
                    budget--;
                    if (state.getSoundType() == net.minecraft.world.level.block.SoundType.GLASS && sounds < 3 && at.distToCenterSqr(cam) < 36 * 36) {
                        sounds++;
                        level.playLocalSound(at, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 0.7F, 0.75F + roll * 0.4F, false);
                    }
                    if (thrown < PIECES_PER_TICK && PIECES.size() < MAX_PIECES && (roll / chance) < piece) {
                        thrown++;
                        throwPiece(state, at, ux, uz, t, scale, roll, y - CityGrid.STREET_Y);
                    }
                }
            }
    }

    private static boolean thin(BlockState state) {
        Block b = state.getBlock();
        return b instanceof LanternBlock || b instanceof IronBarsBlock || b instanceof WallBlock || b instanceof FenceBlock
                || b instanceof ButtonBlock || b instanceof TrapDoorBlock || b instanceof SlabBlock || b instanceof StairBlock
                || state.is(BlockTags.FLOWERS) || state.is(Blocks.SHORT_GRASS) || state.is(Blocks.AZALEA) || state.is(Blocks.FLOWERING_AZALEA)
                || state.is(BlockTags.FLOWER_POTS) || state.is(Blocks.CAULDRON) || state.is(Blocks.CHAIN) || state.is(Blocks.LIGHTNING_ROD);
    }

    private static void throwPiece(BlockState state, BlockPos at, double ux, double uz, float t, float scale, float roll, int height) {
        float speed = 0.7F + 1.5F * fraction(roll, 3) + Math.min(height, 60) * 0.008F;
        float up = 0.12F + 0.55F * fraction(roll, 7);
        float x = at.getX() + 0.5F, y = at.getY() + 0.5F, z = at.getZ() + 0.5F;
        float vx = (float) ux * speed, vz = (float) uz * speed;
        float ax = fraction(roll, 11) - 0.5F, ay = fraction(roll, 13) - 0.5F, az = fraction(roll, 17) - 0.5F;
        float l = Mth.sqrt(ax * ax + ay * ay + az * az) + 1e-4F;
        // When it reaches the street, so it stops tumbling there.
        float landed = Float.MAX_VALUE;
        for (float tau = 1; tau < 400; tau += 1) {
            if (height(y, up, tau) <= CityGrid.STREET_Y + scale * 0.5F) { landed = tau; break; }
        }
        PIECES.add(new Piece(state, x, y, z, vx, up, vz, ax / l, ay / l, az / l, (fraction(roll, 19) - 0.5F) * 0.7F,
                scale * (0.8F + 0.4F * fraction(roll, 23)), t, landed));
    }

    private static float fraction(float roll, int salt) {
        int h = Float.floatToIntBits(roll) * 31 + salt * 977;
        h ^= h >>> 15; h *= 0x2C1B3C6D; h ^= h >>> 12;
        return (h & 0xFFFF) / 65535F;
    }

    private static float travel(float tau) { return (1 - (float) Math.exp(-DRAG * tau)) / DRAG; }
    private static float height(float y0, float vy, float tau) {
        return y0 + (vy + GRAVITY / DRAG) * travel(tau) - GRAVITY * tau / DRAG;
    }

    // ------------------------------------------------------------------
    // The heat of the flash
    // ------------------------------------------------------------------

    private static void ignite(ClientLevel level, Vec3 cam, float t) {
        float reach = 60 + t * 16;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int tries = 50;
        for (int i = 0; i < tries && fires < MAX_FIRES; i++) {
            int seed = CityGrid.hash((int) t, i, 77);
            int x = Mth.floor(cam.x) + seed % (2 * 96) - 96, z = Mth.floor(cam.z) + (seed / 191) % (2 * 96) - 96;
            if (!CityGrid.inCity(x, z) || IntroBlast.distance(x, z) > reach) continue;
            int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
            pos.set(x, top - 1, z);
            BlockState below = level.getBlockState(pos);
            boolean burns = below.isFlammable(level, pos, Direction.UP);
            boolean roof = top > CityGrid.STREET_Y + 6 && below.isFaceSturdy(level, pos, Direction.UP) && (seed % 7 == 0);
            if (!burns && !roof) continue;
            pos.set(x, top, z);
            if (!level.getBlockState(pos).isAir()) continue;
            BlockPos at = pos.immutable();
            CHANGES.add(new Change(at, Blocks.AIR.defaultBlockState(), t));
            level.setBlock(at, Blocks.FIRE.defaultBlockState(), FLAGS);
            fires++;
        }
    }

    // ------------------------------------------------------------------
    // Drawing the debris
    // ------------------------------------------------------------------

    static void render(Vec3 cam, float t, MultiBufferSource.BufferSource buffers) {
        if (PIECES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        PoseStack pose = new PoseStack();
        var blocks = mc.getBlockRenderer();
        BlockPos.MutableBlockPos light = new BlockPos.MutableBlockPos();
        for (Piece p : PIECES) {
            float tau = t - p.t0;
            if (tau < 0) continue;
            float along = travel(tau);
            float x = p.x + p.vx * along, z = p.z + p.vz * along;
            float y = Math.max(height(p.y, p.vy, tau), CityGrid.STREET_Y + p.scale * 0.5F);
            double dx = x - cam.x, dy = y - cam.y, dz = z - cam.z;
            if (dx * dx + dy * dy + dz * dz > 150 * 150) continue;
            float angle = p.spin * Math.min(tau, p.landed);
            pose.pushPose();
            pose.translate(dx, dy, dz);
            pose.mulPose(Axis.of(new org.joml.Vector3f(p.ax, p.ay, p.az)).rotation(angle));
            pose.scale(p.scale, p.scale, p.scale);
            pose.translate(-0.5, -0.5, -0.5);
            light.set(Mth.floor(x), Mth.floor(y), Mth.floor(z));
            blocks.renderSingleBlock(p.state, pose, buffers, LevelRenderer.getLightColor(level, light), OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        buffers.endBatch();
    }

    static boolean hasDebris() { return !PIECES.isEmpty(); }
    static int changes() { return CHANGES.size(); }
}
