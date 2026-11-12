package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.cryo.FrostConstructBlock;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Connected frost surfaces, discovered from the actual client collision blocks. */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class CryoConstructArt {
    private static final float[] DEEP = Canvas.rgb(0x416A85);
    private static final float[] ICE = Canvas.rgb(0x86B9CD);
    private static final float[] RIME = Canvas.rgb(0xE0F2F4);
    private static final Map<ChunkPos, Set<BlockPos>> CELLS = new HashMap<>();
    private static ClientLevel world;

    private CryoConstructArt() {}

    private static void useWorld(ClientLevel level) {
        if (world == level) return;
        world = level;
        CELLS.clear();
    }

    public static void changed(ClientLevel level, BlockPos pos, BlockState state) {
        useWorld(level);
        ChunkPos chunk = new ChunkPos(pos);
        if (FrostConstructBlock.isFrost(state)) CELLS.computeIfAbsent(chunk, ignored -> new HashSet<>()).add(pos.immutable());
        else {
            var cells = CELLS.get(chunk);
            if (cells != null && cells.remove(pos) && cells.isEmpty()) CELLS.remove(chunk);
        }
    }

    public static void loaded(ClientLevel level, LevelChunk chunk) {
        useWorld(level);
        Set<BlockPos> cells = new HashSet<>();
        int baseX = chunk.getPos().getMinBlockX(), baseZ = chunk.getPos().getMinBlockZ();
        var sections = chunk.getSections();
        for (int index = 0; index < sections.length; index++) {
            var section = sections[index];
            if (!section.maybeHas(FrostConstructBlock::isFrost)) continue;
            int baseY = level.getSectionYFromSectionIndex(index) << 4;
            for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                if (FrostConstructBlock.isFrost(section.getBlockState(x, y, z)))
                    cells.add(new BlockPos(baseX + x, baseY + y, baseZ + z));
            }
        }
        if (cells.isEmpty()) CELLS.remove(chunk.getPos());
        else CELLS.put(chunk.getPos(), cells);
    }

    @SubscribeEvent public static void unload(ChunkEvent.Unload event) {
        if (event.getLevel() == world) CELLS.remove(event.getChunk().getPos());
    }

    static boolean live(ClientLevel level) {
        useWorld(level);
        return !CELLS.isEmpty();
    }

    static void draw(Canvas c) {
        if (c.glow()) return;
        useWorld(c.level);
        for (var entry : CELLS.entrySet()) {
            var chunk = entry.getKey();
            if (Math.abs(chunk.getMiddleBlockX() - c.cx) > 96 || Math.abs(chunk.getMiddleBlockZ() - c.cz) > 96) continue;
            for (BlockPos pos : entry.getValue()) {
                var form = form(pos);
                if (form != null && pos.distToCenterSqr(c.cx, c.cy, c.cz) < 96 * 96) cell(c, pos, form);
            }
        }
    }

    private static FrostConstructBlock.Form form(BlockPos pos) {
        var state = world.getBlockState(pos);
        return FrostConstructBlock.isFrost(state) ? state.getValue(FrostConstructBlock.FORM) : null;
    }

    private static void cell(Canvas c, BlockPos pos, FrostConstructBlock.Form form) {
        double low = form.bottom, high = form.top;
        var below = form(pos.below());
        var above = form(pos.above());
        Vec3 root = Vec3.atLowerCornerOf(pos);
        if (high < 1 || above == null || above.bottom > 0) {
            face(c, root.add(0, high, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), Direction.UP, pos);
            if (c.solid() && (form == FrostConstructBlock.Form.WALL || form == FrostConstructBlock.Form.CROWN))
                crest(c, root.add(0, high, 0), Long.hashCode(pos.asLong()));
        }
        if (low > 0 || below == null || below.top < 1)
            face(c, root.add(0, low, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), Direction.DOWN, pos);
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            var neighbor = form(pos.relative(dir));
            if (neighbor == null || high <= neighbor.bottom || low >= neighbor.top) {
                side(c, pos, root, dir, low, high);
                if (c.solid() && neighbor == null && (form == FrostConstructBlock.Form.DECK || form == FrostConstructBlock.Form.LOW)
                        && (low > 0 || below == null && world.getBlockState(pos.below()).isAir()))
                    icicle(c, root.add(.5, low, .5), dir, Long.hashCode(pos.asLong()));
            }
            else if (neighbor.bottom > low) side(c, pos, root, dir, low, neighbor.bottom);
            else if (neighbor.top < high) side(c, pos, root, dir, neighbor.top, high);
        }
    }

    private static void crest(Canvas c, Vec3 root, int seed) {
        for (int i = 0; i < 3; i++) {
            double x = .26 + .51 * Canvas.hash(seed, 4*i);
            double z = .24 + .53 * Canvas.hash(seed, 4*i+1);
            Vec3 at = root.add(x, -.11, z);
            Vec3 tip = at.add((x-.5)*.68, .36 + .42 * Canvas.hash(seed, 4*i+2), (z-.5)*.61);
            c.shard(at, tip, .12F + .10F * Canvas.hash(seed, 4*i+3), 5, i*2.4F, i == 0 ? RIME : ICE, 1);
        }
    }

    private static void icicle(Canvas c, Vec3 center, Direction side, int seed) {
        int variant = seed ^ side.ordinal() * 997;
        if (Canvas.hash(variant, 0) < .28F) return;
        Vec3 normal = Vec3.atLowerCornerOf(side.getNormal());
        Vec3 along = new Vec3(-normal.z, 0, normal.x);
        Vec3 root = center.add(normal.scale(.32)).add(along.scale(Canvas.hash(variant, 1) * .49 - .245)).add(0, .16, 0);
        Vec3 tip = root.add(normal.scale(.05)).add(along.scale(.14)).add(0, -.33 - .38 * Canvas.hash(variant, 2), 0);
        c.shard(root, tip, .15F + .08F * Canvas.hash(variant, 3), 5, Canvas.hash(variant, 4) * 5, ICE, 1);
    }

    private static void side(Canvas c, BlockPos pos, Vec3 root, Direction dir, double low, double high) {
        var along = dir.getAxis() == Direction.Axis.X ? new Vec3(0, 0, 1) : new Vec3(1, 0, 0);
        face(c, root.add(Math.max(0, dir.getStepX()), low, Math.max(0, dir.getStepZ())), along,
                new Vec3(0, high - low, 0), dir, pos);
    }

    private static void face(Canvas c, Vec3 root, Vec3 u, Vec3 v, Direction dir, BlockPos pos) {
        int seed = Long.hashCode(pos.asLong()) ^ dir.ordinal() * 293;
        Vec3 inward = Vec3.atLowerCornerOf(dir.getNormal()).scale(-.065);
        Vec3 center = root.add(u.scale(.24 + Canvas.hash(seed, 0) * .52))
                .add(v.scale(.23 + Canvas.hash(seed, 1) * .54)).add(inward);
        Vec3[] rim = {root, root.add(u.scale(.5)), root.add(u), root.add(u).add(v.scale(.5)),
                root.add(u).add(v), root.add(v).add(u.scale(.5)), root.add(v), root.add(v.scale(.5))};
        float daylight = .53F + c.daylight * .47F;
        float light = switch (dir) {
            case UP -> .93F;
            case DOWN -> .18F;
            case EAST, SOUTH -> .58F;
            default -> .46F;
        };
        if (c.solid()) {
            for (int i = 0; i < rim.length; i++) {
                float tint = (light + Canvas.hash(seed, i + 2) * .24F) * daylight;
                float[] color = Canvas.mix(DEEP, dir == Direction.UP ? RIME : ICE, Math.min(1, tint));
                c.tri(rim[i], rim[(i + 1) % rim.length], center, color, 1, 1, 1);
            }
            return;
        }
        // Pale seams catch light inside the facets, with no outline between adjoining cells.
        Vec3 forward = inward.scale(-1.08);
        Vec3 vein = center.add(forward);
        int edge = (int) (Canvas.hash(seed, 24) * 7);
        c.line(vein, vein.lerp(rim[edge].add(forward), .84), .022F, RIME, .64F * daylight, .12F);
        c.line(vein, vein.lerp(rim[(edge + 3) % rim.length].add(forward), .59), .013F, RIME, .48F * daylight, .06F);
    }
}
