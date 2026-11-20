package com.jayptucker.heroesevolved.story.prologue;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.time.intro.FractureCity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Everything the Last Day does to Odessa's street (the rubble on the sidewalks, the holes Sylar tears in the facades, every
 * window the wave bursts), logged with what stood there before, and put back when the day ends so the next one (or the
 * Time vision) finds the city whole. Changes are written without neighbour or shape updates, so a burst window leaves its
 * frame as it was and nothing round it reacts.
 * <p>
 * The log lives in memory; what's saved is only the box it covers. A server that stops mid-day without ending it (a
 * crash) has the box rebuilt from the city's own plan the next time a day starts ({@link FractureCity#rebuild}).
 */
final class LastDayDamage {
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private final Map<BlockPos, BlockState> was = new LinkedHashMap<>();
    private int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;

    /** Sets {@code pos} to {@code state}, remembering what it was the first time it's touched. */
    void set(ServerLevel city, BlockPos pos, BlockState state) {
        BlockPos at = pos.immutable();
        BlockState before = city.getBlockState(at);
        if (before == state) return;
        was.putIfAbsent(at, before);
        city.setBlock(at, state, FLAGS);
        if (at.getX() < x0 || at.getX() > x1 || at.getZ() < z0 || at.getZ() > z1) {
            x0 = Math.min(x0, at.getX()); x1 = Math.max(x1, at.getX());
            z0 = Math.min(z0, at.getZ()); z1 = Math.max(z1, at.getZ());
            Saved.get(city).cover(x0, z0, x1, z1);
        }
    }

    /** Whether the day changed this block. */
    boolean changed(BlockPos pos) { return was.containsKey(pos); }

    int size() { return was.size(); }

    /** For previews: how many changed blocks lie within {@code r} of {@code at}, and a few of them as they are now. */
    String near(ServerLevel city, net.minecraft.world.phys.Vec3 at, double r) {
        int n = 0;
        StringBuilder sample = new StringBuilder();
        for (BlockPos pos : was.keySet()) {
            if (pos.distToCenterSqr(at) > r * r) continue;
            if (n++ < 8) sample.append(' ').append(pos.toShortString()).append('=').append(city.getBlockState(pos).getBlock().getName().getString());
        }
        return n + " changed within " + (int) r + ":" + sample;
    }

    /** Every changed block back as it was. */
    void restore(ServerLevel city) {
        for (Map.Entry<BlockPos, BlockState> e : was.entrySet()) city.setBlock(e.getKey(), e.getValue(), FLAGS);
        was.clear();
        x0 = z0 = Integer.MAX_VALUE;
        x1 = z1 = Integer.MIN_VALUE;
        Saved.get(city).clear();
    }

    /** A day that never ended (the server stopped mid-way): rebuild what it covered from the plan. */
    static void recover(ServerLevel city) {
        Saved saved = Saved.get(city);
        if (!saved.dirty) return;
        HeroesEvolved.LOGGER.info("The last Last Day didn't end cleanly; rebuilding Odessa from {},{} to {},{}", saved.x0, saved.z0, saved.x1, saved.z1);
        FractureCity.rebuild(city, saved.x0, saved.z0, saved.x1, saved.z1);
        saved.clear();
    }

    /** The box a day's changes cover, saved with the city in case it never gets to put them back. */
    static final class Saved extends SavedData {
        boolean dirty;
        int x0, z0, x1, z1;

        static Saved get(ServerLevel city) {
            return city.getDataStorage().computeIfAbsent(new Factory<>(Saved::new, (tag, registries) -> {
                Saved s = new Saved();
                s.dirty = tag.getBoolean("dirty");
                s.x0 = tag.getInt("x0"); s.z0 = tag.getInt("z0"); s.x1 = tag.getInt("x1"); s.z1 = tag.getInt("z1");
                return s;
            }), "heroesevolved_last_day_damage");
        }

        void cover(int x0, int z0, int x1, int z1) {
            if (dirty && this.x0 <= x0 && this.z0 <= z0 && this.x1 >= x1 && this.z1 >= z1) return;
            this.x0 = dirty ? Math.min(this.x0, x0) : x0; this.z0 = dirty ? Math.min(this.z0, z0) : z0;
            this.x1 = dirty ? Math.max(this.x1, x1) : x1; this.z1 = dirty ? Math.max(this.z1, z1) : z1;
            dirty = true;
            setDirty();
        }

        void clear() {
            if (!dirty) return;
            dirty = false;
            setDirty();
        }

        @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putBoolean("dirty", dirty);
            tag.putInt("x0", x0); tag.putInt("z0", z0); tag.putInt("x1", x1); tag.putInt("z1", z1);
            return tag;
        }
    }
}
