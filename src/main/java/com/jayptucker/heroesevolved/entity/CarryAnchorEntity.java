package com.jayptucker.heroesevolved.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** Invisible vehicle that keeps a carried entity at the carrier's hands. */
public final class CarryAnchorEntity extends Entity {
    public CarryAnchorEntity(
            EntityType<? extends CarryAnchorEntity> entityType,
            Level level
    ) {
        super(entityType, level);
        noPhysics = true;
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        // The anchor has no independent synchronized state.
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        // Carry anchors are temporary and are never loaded from disk.
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        // Carry anchors are temporary and are never saved to disk.
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction moveFunction) {
        if (hasPassenger(passenger)) {
            moveFunction.accept(passenger, getX(), getY(), getZ());
        }
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
