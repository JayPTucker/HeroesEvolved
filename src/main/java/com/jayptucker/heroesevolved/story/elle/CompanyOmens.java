package com.jayptucker.heroesevolved.story.elle;

import com.jayptucker.heroesevolved.effects.WorldMutationPolicy;
import com.jayptucker.heroesevolved.grid.GridDisruption;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The day before Elle comes, the grid around you goes wrong. Three kinds of omen, more often as the day goes on:
 * <ul>
 *   <li><b>Lights flicker</b>: lamps and copper bulbs stutter off and on, then settle back as they were.</li>
 *   <li><b>Redstone misfires</b>: a short {@link GridDisruption#jam} kills a circuit for a second (doors and pistons
 *   drop, lamps blink out), a note block plays by itself, a door swings open and shut.</li>
 *   <li><b>Machines switch themselves on and off</b>: Forge Energy and Create machines stall
 *   ({@link GridDisruption#stall}) and furnaces light up cold or go dark mid-smelt, for a few seconds.</li>
 * </ul>
 * <p>Only blocks the haunted player could use themselves are touched (spawn protection and the world border hold), and
 * every change is put back exactly: nothing is placed, broken, drained or used up. If the block was changed in between,
 * it is left alone. The check deliberately does not post a {@code BlockEvent.BreakEvent} the way
 * {@link WorldMutationPolicy} does: that event records temporal echoes and mirrors breaks into snapshots, and an omen
 * breaks nothing.</p>
 */
public final class CompanyOmens {
    public static final int RADIUS = 20, HEIGHT = 8;

    public enum Kind { LIGHTS, REDSTONE, MACHINES }

    /** The haunted player could touch this block themselves (no break-event probe: see the class notes). */
    private static boolean allowed(ServerPlayer player, ServerLevel level, BlockPos pos) {
        return player.mayBuild() && level.hasChunkAt(pos) && level.getWorldBorder().isWithinBounds(pos) && level.mayInteract(player, pos);
    }

    /** A block to set (if it is still the same kind of block) at a game time. */
    private record Step(ServerLevel level, BlockPos pos, Block block, BlockState state, long at, Runnable action) {}
    private static final List<Step> STEPS = new ArrayList<>();

    private CompanyOmens() {}

    /** What was found round a player: the lights, the redstone and the machines. */
    record Survey(List<BlockPos> lights, List<BlockPos> redstone, List<BlockPos> furnaces, List<BlockPos> machines) {}

    static Survey survey(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos at = player.blockPosition();
        List<BlockPos> lights = new ArrayList<>(), redstone = new ArrayList<>(), furnaces = new ArrayList<>(), machines = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -RADIUS; dx <= RADIUS; dx++)
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                if (dx * dx + dz * dz > RADIUS * RADIUS) continue;
                for (int dy = -HEIGHT; dy <= HEIGHT; dy++) {
                    pos.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz);
                    if (!level.hasChunkAt(pos)) continue;
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) continue;
                    Block block = state.getBlock();
                    if (block instanceof RedstoneLampBlock || block instanceof CopperBulbBlock) lights.add(pos.immutable());
                    if (block instanceof AbstractFurnaceBlock) furnaces.add(pos.immutable());
                    if (block instanceof RedStoneWireBlock || block instanceof DiodeBlock || block instanceof net.minecraft.world.level.block.piston.PistonBaseBlock
                            || block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof FenceGateBlock
                            || block instanceof NoteBlock || block instanceof ObserverBlock || block instanceof RedstoneTorchBlock
                            || block instanceof RedstoneLampBlock || block instanceof LeverBlock)
                        redstone.add(pos.immutable());
                }
            }
        for (int cx = (at.getX() - RADIUS) >> 4; cx <= (at.getX() + RADIUS) >> 4; cx++)
            for (int cz = (at.getZ() - RADIUS) >> 4; cz <= (at.getZ() + RADIUS) >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (BlockEntity entity : chunk.getBlockEntities().values()) {
                    BlockPos p = entity.getBlockPos();
                    if (!entity.isRemoved() && p.distSqr(at) <= RADIUS * RADIUS && Math.abs(p.getY() - at.getY()) <= HEIGHT
                            && GridDisruption.isMachine(level, p)) machines.add(p.immutable());
                }
            }
        return new Survey(lights, redstone, furnaces, machines);
    }

    // ------------------------------------------------------------------
    // The three omens
    // ------------------------------------------------------------------

    /** Up to three lamps near the player stutter off and on, then settle back as they were. Returns them. */
    public static List<BlockPos> flicker(ServerPlayer player) { return flicker(player, survey(player)); }

    static List<BlockPos> flicker(ServerPlayer player, Survey survey) {
        ServerLevel level = player.serverLevel();
        List<BlockPos> done = new ArrayList<>();
        List<BlockPos> lights = new ArrayList<>(survey.lights());
        java.util.Collections.shuffle(lights, new java.util.Random(level.getGameTime()));
        long now = level.getGameTime();
        for (BlockPos pos : lights) {
            if (done.size() >= 3) break;
            if (!allowed(player, level, pos)) continue;
            BlockState original = level.getBlockState(pos);
            if (!original.hasProperty(BlockStateProperties.LIT)) continue;
            BlockState flipped = original.setValue(BlockStateProperties.LIT, !original.getValue(BlockStateProperties.LIT));
            int start = done.size() * 3;
            // A stutter: off, on, off for longer, then back as it was.
            int[] at = {0, 2, 4, 9};
            for (int k = 0; k < at.length; k++) step(level, pos, original.getBlock(), k % 2 == 0 ? flipped : original, now + start + at[k], null);
            step(level, pos, original.getBlock(), original, now + start + 14 + level.getRandom().nextInt(8), null);
            SetPiecePayload.send(level, Piece.ELLE_OMEN, Vec3.atCenterOf(pos), Vec3.atCenterOf(pos), 1, null);
            level.playSound(null, pos, original.getValue(BlockStateProperties.LIT) ? SoundEvents.COPPER_BULB_TURN_OFF : SoundEvents.COPPER_BULB_TURN_ON,
                    SoundSource.BLOCKS, 0.6F, 0.8F);
            done.add(pos);
        }
        return done;
    }

    /** A circuit near the player dies for a second, and something wired does what nobody told it to. Returns what was hit. */
    public static List<BlockPos> misfire(ServerPlayer player) { return misfire(player, survey(player)); }

    static List<BlockPos> misfire(ServerPlayer player, Survey survey) {
        ServerLevel level = player.serverLevel();
        List<BlockPos> done = new ArrayList<>();
        List<BlockPos> redstone = new ArrayList<>(survey.redstone());
        if (redstone.isEmpty()) return done;
        java.util.Collections.shuffle(redstone, new java.util.Random(level.getGameTime() * 31));
        long now = level.getGameTime();
        BlockPos center = redstone.getFirst();
        if (allowed(player, level, center)) {
            GridDisruption.jam(level, center, 4, 12 + level.getRandom().nextInt(16));
            SetPiecePayload.send(level, Piece.ELLE_OMEN, Vec3.atCenterOf(center), Vec3.atCenterOf(center), 2, null);
            level.playSound(null, center, SoundEvents.REDSTONE_TORCH_BURNOUT, SoundSource.BLOCKS, 0.5F, 1.2F);
            done.add(center);
        }
        // A stray pulse: one wired thing acts on its own, then goes back.
        for (BlockPos pos : redstone) {
            if (!allowed(player, level, pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof NoteBlock) {
                level.blockEvent(pos, state.getBlock(), 0, 0);
            } else if (state.getBlock() instanceof DoorBlock door && state.getValue(DoorBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER) {
                boolean open = state.getValue(DoorBlock.OPEN);
                door.setOpen(null, level, state, pos, !open);
                step(level, pos, door, null, now + 16, () -> {
                    BlockState now2 = level.getBlockState(pos);
                    if (now2.getBlock() == door) door.setOpen(null, level, now2, pos, open);
                });
            } else if (state.hasProperty(BlockStateProperties.OPEN) && (state.getBlock() instanceof TrapDoorBlock || state.getBlock() instanceof FenceGateBlock)) {
                level.setBlock(pos, state.cycle(BlockStateProperties.OPEN), Block.UPDATE_CLIENTS);
                level.playSound(null, pos, SoundEvents.WOODEN_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.7F, 0.9F);
                step(level, pos, state.getBlock(), state, now + 14, null);
            } else continue;
            if (!done.contains(pos)) done.add(pos);
            SetPiecePayload.send(level, Piece.ELLE_OMEN, Vec3.atCenterOf(pos), Vec3.atCenterOf(pos), 2, null);
            break;
        }
        return done;
    }

    /** Machines near the player switch off, and cold furnaces flare on, for a few seconds. Returns them. */
    public static List<BlockPos> machines(ServerPlayer player) { return machines(player, survey(player)); }

    static List<BlockPos> machines(ServerPlayer player, Survey survey) {
        ServerLevel level = player.serverLevel();
        List<BlockPos> done = new ArrayList<>();
        long now = level.getGameTime();
        for (BlockPos pos : survey.machines()) {
            if (done.size() >= 2) break;
            if (GridDisruption.stall(null, level, pos, 40 + level.getRandom().nextInt(60))) done.add(pos);
        }
        for (BlockPos pos : survey.furnaces()) {
            if (done.size() >= 3) break;
            if (!allowed(player, level, pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (!state.hasProperty(BlockStateProperties.LIT)) continue;
            level.setBlock(pos, state.cycle(BlockStateProperties.LIT), Block.UPDATE_CLIENTS);
            step(level, pos, state.getBlock(), state, now + 30 + level.getRandom().nextInt(30), null);
            if (!done.contains(pos)) done.add(pos);
        }
        for (BlockPos pos : done) SetPiecePayload.send(level, Piece.ELLE_OMEN, Vec3.atCenterOf(pos), Vec3.atCenterOf(pos), 3, null);
        if (!done.isEmpty()) level.playSound(null, done.getFirst(), SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.5F, 1.8F);
        return done;
    }

    /**
     * One omen near a player, of a kind picked at random among the ones that have something to act on. Returns the kind,
     * or null when nothing nearby is wired (then the air only hums).
     */
    static Kind haunt(ServerPlayer player) {
        Survey survey = survey(player);
        List<Kind> kinds = new ArrayList<>(List.of(Kind.LIGHTS, Kind.LIGHTS, Kind.REDSTONE, Kind.MACHINES));
        java.util.Collections.shuffle(kinds, new java.util.Random(player.level().getGameTime() ^ player.getUUID().getLeastSignificantBits()));
        for (Kind kind : kinds) {
            List<BlockPos> hit = switch (kind) {
                case LIGHTS -> flicker(player, survey);
                case REDSTONE -> misfire(player, survey);
                case MACHINES -> machines(player, survey);
            };
            if (!hit.isEmpty()) return kind;
        }
        Vec3 at = player.getEyePosition().add(player.getLookAngle().scale(2));
        SetPiecePayload.send(player.serverLevel(), Piece.ELLE_OMEN, at, at, 4, null);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.BEACON_AMBIENT, SoundSource.AMBIENT, 0.5F, 1.9F);
        return null;
    }

    private static void step(ServerLevel level, BlockPos pos, Block block, BlockState state, long at, Runnable action) {
        if (STEPS.size() > 512) return;
        STEPS.add(new Step(level, pos.immutable(), block, state, at, action));
    }

    /** Pending stutters and restorations still to run. */
    public static int pending() { return STEPS.size(); }

    public static void tick(MinecraftServer server) {
        if (STEPS.isEmpty()) return;
        for (Iterator<Step> it = STEPS.iterator(); it.hasNext(); ) {
            Step step = it.next();
            if (step.level.getServer() != server) continue;
            if (step.level.getGameTime() < step.at) continue;
            it.remove();
            if (!step.level.hasChunkAt(step.pos)) continue;
            if (step.action != null) { step.action.run(); continue; }
            // Only put back what is still the block we changed.
            if (step.level.getBlockState(step.pos).getBlock() == step.block) step.level.setBlock(step.pos, step.state, Block.UPDATE_CLIENTS);
        }
    }

    /** Puts everything back now (server stopping, tests). */
    public static void settle() {
        for (Step step : List.copyOf(STEPS)) {
            if (step.action != null || !step.level.hasChunkAt(step.pos)) continue;
            if (step.level.getBlockState(step.pos).getBlock() == step.block) step.level.setBlock(step.pos, step.state, Block.UPDATE_CLIENTS);
        }
        // The last step for each block is its restoration; earlier stutters were applied in order above.
        for (Step step : STEPS) if (step.action != null) step.action.run();
        STEPS.clear();
    }
}
