package com.jayptucker.heroesevolved.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** A short-lived, non-interactive block memory rendered with transparency. */
public final class TemporalGhostBlockEntity extends Entity {
    private static final EntityDataAccessor<BlockState> BLOCK_STATE =
            SynchedEntityData.defineId(
                    TemporalGhostBlockEntity.class,
                    EntityDataSerializers.BLOCK_STATE
            );

    public TemporalGhostBlockEntity(
            EntityType<? extends TemporalGhostBlockEntity> entityType,
            Level level
    ) {
        super(entityType, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public void setBlockState(BlockState state) {
        entityData.set(BLOCK_STATE, state);
    }

    public BlockState getBlockState() {
        return entityData.get(BLOCK_STATE);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(BLOCK_STATE, Blocks.AIR.defaultBlockState());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        // Ghost blocks are rebuilt from replay actions and never persisted.
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        // Ghost blocks are rebuilt from replay actions and never persisted.
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
