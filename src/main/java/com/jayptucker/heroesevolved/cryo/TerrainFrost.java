package com.jayptucker.heroesevolved.cryo;

import com.jayptucker.heroesevolved.effects.WorldMutationPolicy;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

import java.util.HashMap;
import java.util.Map;

/**
 * Sustained cold turns terrain into ice. Each hit (an Ice Blast pulse, a Crystal Lance, a Frost Armor shard, a Cryo
 * Gun pulse) cools the exposed 3 x 3 patch round the impact: the aimed block fastest, its edges at two thirds and its
 * corners at half, and no neighbour may finish before the aimed block has. Harder blocks need more cold
 * ({@code 20 + 8 * sqrt(hardness)}: stone 30, obsidian 77). Solids become brittle frost that thaws back after
 * {@link #THAW_TICKS} unless broken; water becomes ice and lava obsidian, for good.
 *
 * <p>Cold that hasn't finished shows as rime growing on the struck face and, on solids, cracks spreading through the
 * block (the vanilla break overlay, under an id of its own per block). It fades {@link #CONTACT_TIMEOUT} ticks after the
 * last hit. Permission is checked without side effects while cold builds and with the real break event only at the
 * moment a block changes, so a long contact never posts break events (which record echoes and mirror snapshots).</p>
 */
public final class TerrainFrost {
    static final int CONTACT_TIMEOUT = 80, FLUID_SETTLE_TICKS = 30;
    static final int THAW_TICKS = 20 * 12;
    private static final int MAX_TRACKED = 4_000;
    private static final Map<Target, Chill> CHILLED = new HashMap<>();

    private record Target(ServerLevel level, BlockPos pos) {}
    /** Cold built up in one block. {@code cold} is negative for a block whose change was refused (protected land). */
    private record Chill(BlockState state, double cold, long expires, Direction settledFace) {
        boolean solidified(BlockState current, long now) {
            return settledFace != null && state == current && now < expires;
        }

        boolean refused(BlockState current, long now) {
            return cold < 0 && state == current && now < expires;
        }
    }

    private TerrainFrost() {}

    static void blast(ServerLevel level, BlockHitResult hit, ServerPlayer caster) {
        spread(level, hit, caster, 4);
    }

    static void projectile(ServerLevel level, BlockHitResult hit, ServerPlayer caster, boolean lance) {
        spread(level, hit, caster, lance ? 12 : 4);
    }

    /** Ten pulses a second: the held gun cools terrain at the same rate as the base Ice Blast. */
    public static void freezeGun(ServerLevel level, BlockHitResult hit, Entity shooter) {
        spread(level, hit, shooter, 2);
    }

    private static void spread(ServerLevel level, BlockHitResult hit, Entity actor, int amount) {
        BlockPos center = hit.getBlockPos();
        if (actor == null || actor.level() != level || !level.hasChunkAt(center)) return;
        ServerPlayer player = actor instanceof ServerPlayer p ? p : null;
        if (player != null ? !player.mayBuild() : !EventHooks.canEntityGrief(level, actor)) return;

        BlockState impact = level.getBlockState(center);
        Chill settled = CHILLED.get(new Target(level, center));
        Direction face = hit.getDirection();
        if (settled != null && settled.solidified(impact, level.getGameTime())) face = settled.settledFace;
        else if ((impact.is(Blocks.WATER) || impact.is(Blocks.LAVA)) && level.getBlockState(center.above()).isAir())
            face = Direction.UP;
        Direction across = face.getAxis() == Direction.Axis.X ? Direction.SOUTH : Direction.EAST;
        Direction along = face.getAxis() == Direction.Axis.Y ? Direction.SOUTH : Direction.UP;
        Vec3 normal = Vec3.atLowerCornerOf(face.getNormal());
        Vec3 start = Vec3.atCenterOf(center).add(normal.scale(.53));
        // Give the directly hit block the first chance to freeze, even over a softer neighbor.
        boolean centerCooling = chill(level, center, face, hit.getLocation(), player, amount, true);
        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                int distance = Math.abs(a) + Math.abs(b);
                if (distance == 0) continue;
                BlockPos pos = center.relative(across, a).relative(along, b);
                if (!level.hasChunkAt(pos) || !level.hasChunkAt(pos.relative(face)) || level.isEmptyBlock(pos)) continue;
                Vec3 surface = Vec3.atCenterOf(pos).add(normal.scale(.53));
                if (level.clip(new ClipContext(start, surface, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, actor))
                        .getType() == HitResult.Type.MISS)
                    chill(level, pos, face, surface, player, amount * (distance == 1 ? 2.0 / 3 : .5), !centerCooling);
            }
        }
    }

    /** Whether this contact is still working through unfrozen material. */
    private static boolean chill(ServerLevel level, BlockPos pos, Direction face, Vec3 impact, ServerPlayer player,
                                 double amount, boolean canFinish) {
        if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)
                || player != null && !level.mayInteract(player, pos)) return false;
        BlockState state = level.getBlockState(pos);
        Target target = new Target(level, pos.immutable());
        Chill previous = CHILLED.get(target);
        long now = level.getGameTime();
        if (previous != null && previous.solidified(state, now)) {
            CHILLED.put(target, new Chill(state, 0, now + FLUID_SETTLE_TICKS, previous.settledFace));
            return false;
        }
        // The land refused this change: keep quiet instead of asking again every pulse.
        if (previous != null && previous.refused(state, now)) return false;
        boolean water = state.is(Blocks.WATER);
        boolean lava = state.is(Blocks.LAVA);
        float hardness = state.getDestroySpeed(level, pos);
        if (!water && !lava && (hardness < 0 || state.hasBlockEntity() || !state.getFluidState().isEmpty()
                || !state.isCollisionShapeFullBlock(level, pos) || FrostConstructBlock.isFrost(state)
                || state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE)
                || state.is(Blocks.FROSTED_ICE) || TemporaryBlocks.isTemporary(level, pos))) return false;

        // A flowing liquid can change depth as it cools. A replacement solid starts at zero.
        boolean sameTarget = previous != null && now < previous.expires
                && previous.settledFace == null
                && (previous.state == state || (water || lava) && previous.state.is(state.getBlock()));
        double before = sameTarget ? previous.cold : 0;
        int required = required(state, hardness);
        double cold = canFinish ? before + amount : Math.max(before, Math.min(before + amount, required * .92));
        if (cold + 1e-6 < required) {
            if (previous != null || CHILLED.size() < MAX_TRACKED) {
                CHILLED.put(target, new Chill(state, cold, now + CONTACT_TIMEOUT, null));
                if (before == 0 || (int) (before * 8 / required) != (int) (cold * 8 / required))
                    glaze(level, pos, face, (float) (cold / required));
                if (!water && !lava && crack(before, required) != crack(cold, required))
                    level.destroyBlockProgress(crackId(pos), pos, crack(cold, required));
            }
            return true;
        }

        CHILLED.remove(target);
        if (!water && !lava) level.destroyBlockProgress(crackId(pos), pos, -1);
        glaze(level, pos, face, 1);
        if (water || lava) {
            if (!level.getEntities(null, new AABB(pos)).isEmpty()) return false;
            BlockState replacement = (water ? Blocks.ICE : Blocks.OBSIDIAN).defaultBlockState();
            boolean placed = player == null ? level.setBlockAndUpdate(pos, replacement)
                    : WorldMutationPolicy.place(player, level, pos, replacement);
            if (!placed) {
                CHILLED.put(target, new Chill(state, -1, now + CONTACT_TIMEOUT, null));
                return false;
            }
            // Keep the footprint aligned on the pool after the liquid becomes a full block.
            CHILLED.put(target, new Chill(replacement, 0, now + FLUID_SETTLE_TICKS, face));
        } else if (!TemporaryBlocks.freezeSolid(level, pos, THAW_TICKS, player)) {
            CHILLED.put(target, new Chill(state, -1, now + CONTACT_TIMEOUT, null));
            return false;
        }

        SetPiecePayload.send(level, Piece.CRYO_QUENCH, impact, impact, .7F, null);
        level.playSound(null, pos, lava ? SoundEvents.FIRE_EXTINGUISH : SoundEvents.GLASS_PLACE,
                SoundSource.BLOCKS, .8F, lava ? .8F : 1.25F);
        return true;
    }

    private static void glaze(ServerLevel level, BlockPos pos, Direction face, float progress) {
        Vec3 center = Vec3.atCenterOf(pos);
        SetPiecePayload.send(level, Piece.CRYO_TERRAIN_FROST, center, center.add(Vec3.atLowerCornerOf(face.getNormal())),
                progress, null);
    }

    /** Cold a block needs before it turns: water 12, lava 24, a solid 20 + 8 * sqrt(hardness) (stone 30, obsidian 77). */
    static int required(BlockState state, float hardness) {
        return state.is(Blocks.WATER) ? 12 : state.is(Blocks.LAVA) ? 24
                : 20 + (int) Math.round(8 * Math.sqrt(Math.min(Math.max(hardness, 0), 64)));
    }

    /** Cracks start once a tenth of the cold is in and reach the last stage just before the block turns. */
    private static int crack(double cold, int required) {
        return cold <= 0 ? -1 : Math.min(9, (int) (cold * 10 / required) - 1);
    }

    /**
     * The break overlay is keyed by a breaker's entity id. A negative id per block never matches an entity, so the
     * patch's cracks never fight a player's own mining or each other.
     */
    static int crackId(BlockPos pos) {
        return -2 - (Long.hashCode(pos.asLong()) & 0x3FFFFFFF);
    }

    public static void tick() {
        var entries = CHILLED.entrySet().iterator();
        while (entries.hasNext()) {
            var entry = entries.next();
            Target target = entry.getKey();
            if (target.level.getGameTime() < entry.getValue().expires) continue;
            entries.remove();
            // Unfinished cold fades: take its cracks with it.
            if (entry.getValue().cold > 0 && target.level.hasChunkAt(target.pos))
                target.level.destroyBlockProgress(crackId(target.pos), target.pos, -1);
        }
    }

    /** How far a block is toward turning, 0 to 1 (0 when no cold is building in it). For tests and previews. */
    public static double progress(ServerLevel level, BlockPos pos) {
        Chill chill = CHILLED.get(new Target(level, pos));
        if (chill == null || chill.cold <= 0 || chill.settledFace != null) return 0;
        BlockState state = level.getBlockState(pos);
        if (!chill.state.is(state.getBlock())) return 0;
        return Math.min(1, chill.cold / required(state, state.getDestroySpeed(level, pos)));
    }

    public static void clear() {
        CHILLED.clear();
    }
}
