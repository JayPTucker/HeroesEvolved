package com.jayptucker.heroesevolved.story.bunker;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.structure.Blueprint;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CopperBulbBlock;
import net.minecraft.world.level.block.EndRodBlock;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The stasis bunker's own lights. A new bunker is built on emergency power: its bulbs, lamps and
 * lanterns dark, only the alarm beacons turning. Every sleeper's waking fails the main power again
 * ({@link #cut}). When the last sleeper is out of their pod the main breaker closes ({@link #start}) and
 * the lights come on as a wave from the generator outward, so the stasis hall lights up from its door
 * to its far wall. A light is switched only where it still stands as the bunker built it, so nothing a
 * player put there is touched. A bunker built before this was lit ({@link BunkerData#powered} is true).
 */
public final class BunkerPower {
    /** Where the power comes from, bunker-relative: the generator room's floor. */
    private static final BlockPos GENERATOR = new BlockPos(10, 1, 6);
    /** The breaker's clunk comes first, then the lights follow the wiring out at a block a tick. */
    private static final int FIRST = 6;
    private static final double TICKS_PER_BLOCK = 1.0;

    private record Light(BlockPos pos, BlockState on, BlockState off, long at) {}
    private static final List<Light> PENDING = new ArrayList<>();

    private BunkerPower() {}

    /** How {@code on} stands while the power is out, or null if it is not one of the bunker's lights. */
    public static @Nullable BlockState off(BlockState on) {
        Block block = on.getBlock();
        if (block == Blocks.GLOWSTONE) return Blocks.DEEPSLATE_BRICKS.defaultBlockState();
        if (block instanceof CopperBulbBlock && on.getValue(CopperBulbBlock.LIT)) return on.setValue(CopperBulbBlock.LIT, false);
        if (block == Blocks.LANTERN || block == Blocks.SOUL_LANTERN) return Blocks.AIR.defaultBlockState();
        if (block == Blocks.END_ROD) return Blocks.LIGHTNING_ROD.defaultBlockState().setValue(LightningRodBlock.FACING, on.getValue(EndRodBlock.FACING));
        if (block == Blocks.PEARLESCENT_FROGLIGHT || block == Blocks.OCHRE_FROGLIGHT || block == Blocks.VERDANT_FROGLIGHT || block == Blocks.SEA_LANTERN)
            return Blocks.SMOOTH_QUARTZ.defaultBlockState();
        return null;
    }

    /** For {@link Blueprint#place}: every light as it stands with the power out. */
    public static BlockState unpowered(int layer, BlockState state) {
        BlockState off = off(state);
        return off == null ? state : off;
    }

    /** True while the lights are coming on. */
    public static boolean switching() { return !PENDING.isEmpty(); }

    /**
     * Closes the main breaker: the lights start coming on. Returns false, and does nothing, if they are
     * already on or already coming on.
     */
    public static boolean start(ServerLevel level, BunkerData data) {
        if (!data.built || data.powered || switching()) return false;
        BlockPos generator = data.origin.offset(GENERATOR);
        long now = level.getGameTime();
        forEachFixture(data, (pos, on) -> {
            long delay = FIRST + Math.round(Math.sqrt(pos.distSqr(generator)) * TICKS_PER_BLOCK);
            PENDING.add(new Light(pos, on, off(on), now + delay));
        });
        PENDING.sort(Comparator.comparingLong(Light::at));
        if (PENDING.isEmpty()) finish(data);
        return true;
    }

    /**
     * The main power fails: every light still as the blueprint has it goes dark at once, and the bunker is
     * back on emergency power until a {@link #start}. A new sleeper waking does this.
     */
    public static void cut(ServerLevel level, BunkerData data) {
        PENDING.clear();
        if (!data.built) return;
        forEachFixture(data, (pos, on) -> {
            if (level.isLoaded(pos) && level.getBlockState(pos) == on) level.setBlock(pos, off(on), Block.UPDATE_ALL);
        });
        if (data.powered) {
            data.powered = false;
            data.setDirty();
        }
    }

    /** Every light in the bunker's rooms: where it is, and how it stands with the power on. */
    static void forEachFixture(BunkerData data, java.util.function.BiConsumer<BlockPos, BlockState> each) {
        List<BunkerBuilder.Room> rooms = new ArrayList<>(BunkerBuilder.ROOMS);
        rooms.add(BunkerBuilder.OFFICE);
        for (BunkerBuilder.Room room : rooms) {
            BlockPos at = data.origin.offset(room.at());
            Blueprint.load(room.blueprint()).statesMatching(state -> off(state) != null).forEach((cell, on) -> each.accept(at.offset(cell), on));
        }
    }

    /** Turns on the lights whose moment has come; called every server tick. */
    public static void tick(ServerLevel level, BunkerData data) {
        if (PENDING.isEmpty()) return;
        long now = level.getGameTime();
        while (!PENDING.isEmpty() && PENDING.getFirst().at() <= now) {
            Light light = PENDING.removeFirst();
            if (!level.getBlockState(light.pos()).equals(light.off())) continue;
            level.setBlock(light.pos(), light.on(), Block.UPDATE_ALL);
            level.playSound(null, light.pos(), SoundEvents.COPPER_BULB_TURN_ON, SoundSource.BLOCKS, 0.55F,
                    0.8F + level.random.nextFloat() * 0.3F);
        }
        if (PENDING.isEmpty()) finish(data);
    }

    private static void finish(BunkerData data) {
        data.powered = true;
        data.setDirty();
        HeroesEvolved.LOGGER.info("The stasis bunker at {} has its power back", data.origin);
    }

    /** Forgets a half-finished switch-on when the server stops; the next start finishes it. */
    public static void reset() { PENDING.clear(); }
}
