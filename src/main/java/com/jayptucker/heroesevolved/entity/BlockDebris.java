package com.jayptucker.heroesevolved.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A real block flying about the world: the vanilla block display (its renderer, its smooth movement), centred on its
 * position and never saved, so no stray rock outlives the move that threw it. Telekinesis's debris and Density's
 * earthbending (rocks, discs, boulders, the lifted mountain) both use it.
 */
public final class BlockDebris extends Display.BlockDisplay {
    private BlockState block;
    private float size, squash = 1;

    public BlockDebris(ServerLevel level, BlockState block, float size) { this(level, block, size, 1); }

    /** {@code squash} flattens it to that share of its height: a disc of stone. */
    public BlockDebris(ServerLevel level, BlockState block, float size, float squash) {
        super(EntityType.BLOCK_DISPLAY, level);
        this.block = block;
        this.size = size;
        this.squash = squash;
        CompoundTag tag = transform(size, squash, 0);
        tag.put("block_state", NbtUtils.writeBlockState(block));
        tag.putInt("teleport_duration", 2);
        tag.putFloat("view_range", Math.max(.6F, size));
        readAdditionalSaveData(tag);
    }

    /** Changes the block it shows. */
    public void block(BlockState state) {
        if (state.equals(block)) return;
        block = state;
        CompoundTag tag = new CompoundTag();
        tag.put("block_state", NbtUtils.writeBlockState(state));
        readAdditionalSaveData(tag);
    }

    public BlockState block() { return block; }

    public float size() { return size; }

    /**
     * Grows or shrinks it about its centre, eased over {@code ticks} on every client. The block goes in the tag too:
     * a block display re-reads its block on every load of saved data, and a tag without one turns it to air.
     */
    public void resize(float to, int ticks) {
        if (Math.abs(to - size) < 1.0E-3) return;
        size = to;
        CompoundTag tag = transform(to, squash, ticks);
        tag.put("block_state", NbtUtils.writeBlockState(block));
        readAdditionalSaveData(tag);
    }

    private static CompoundTag transform(float size, float squash, int ticks) {
        CompoundTag tag = new CompoundTag(), transform = new CompoundTag();
        ListTag scale = new ListTag(), translation = new ListTag();
        for (int i = 0; i < 3; i++) {
            float s = i == 1 ? size * squash : size;
            scale.add(FloatTag.valueOf(s));
            translation.add(FloatTag.valueOf(-s / 2));
        }
        transform.put("scale", scale);
        transform.put("translation", translation);
        ListTag rotation = new ListTag();
        for (float value : new float[] {0, 0, 0, 1}) rotation.add(FloatTag.valueOf(value));
        transform.put("left_rotation", rotation);
        transform.put("right_rotation", rotation.copy());
        tag.put("transformation", transform);
        if (ticks > 0) { tag.putInt("interpolation_duration", ticks); tag.putInt("start_interpolation", 0); }
        return tag;
    }

    @Override public boolean shouldBeSaved() { return false; }
}
