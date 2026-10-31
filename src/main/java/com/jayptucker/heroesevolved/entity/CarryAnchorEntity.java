package com.jayptucker.heroesevolved.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** An invisible passenger of the carrier, and vehicle of the held entity. */
public final class CarryAnchorEntity extends Entity {
    private static final EntityDataAccessor<Float> CARGO_WIDTH = SynchedEntityData.defineId(
            CarryAnchorEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> CARGO_HEIGHT = SynchedEntityData.defineId(
            CarryAnchorEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> CARGO_EYE = SynchedEntityData.defineId(
            CarryAnchorEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Byte> HOLD = SynchedEntityData.defineId(
            CarryAnchorEntity.class, EntityDataSerializers.BYTE);

    public CarryAnchorEntity(EntityType<? extends CarryAnchorEntity> entityType, Level level) {
        super(entityType, level);
        noPhysics = true;
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(CARGO_WIDTH, .6F);
        builder.define(CARGO_HEIGHT, 1.8F);
        builder.define(CARGO_EYE, 1.62F);
        builder.define(HOLD, (byte) CarryGeometry.Hold.ARMS.ordinal());
    }

    public void setCargo(Entity held) {
        entityData.set(CARGO_WIDTH, held.getBbWidth());
        entityData.set(CARGO_HEIGHT, held.getBbHeight());
        entityData.set(CARGO_EYE, held.getEyeHeight());
        entityData.set(HOLD, (byte) CarryGeometry.hold(held).ordinal());
    }

    /** How the cargo is held: decided once on the server at pickup, so every side draws it the same way. */
    public CarryGeometry.Hold hold() {
        CarryGeometry.Hold[] holds = CarryGeometry.Hold.values();
        return holds[Math.floorMod(entityData.get(HOLD), holds.length)];
    }

    public Entity carrier() { return getVehicle(); }

    public Vec3 seatPosition(Entity carrier) {
        Entity held = getFirstPassenger();
        CarryGeometry.Hold hold = hold();
        double width = held == null ? entityData.get(CARGO_WIDTH) : held.getBbWidth();
        double height = held == null ? entityData.get(CARGO_HEIGHT) : held.getBbHeight();
        double eye = held == null ? entityData.get(CARGO_EYE) : held.getEyeHeight();
        Vec3 seat = CarryGeometry.position(carrier, hold, width, height, eye);
        return CarryGeometry.fits(carrier, hold, width, height, seat) ? seat : position();
    }

    @Override
    public Vec3 getVehicleAttachmentPoint(Entity vehicle) {
        // Vanilla places passengers at vehicleSeat - attachment. Cancel its default
        // overhead player seat and substitute the shared, locally derived side slot.
        return vehicle.getPassengerRidingPosition(this).subtract(seatPosition(vehicle));
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction moveFunction) {
        if (hasPassenger(passenger)) moveFunction.accept(passenger, getX(), getY(), getZ());
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) { return getPassengers().isEmpty(); }

    @Override
    public LivingEntity getControllingPassenger() { return null; }

    /** A person carried in the arms sits in them: vanilla folds their legs at the hips. */
    @Override
    public boolean shouldRiderSit() { return hold() == CarryGeometry.Hold.ARMS; }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) { return position(); }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        Entity owner = carrier();
        if (owner != null) setYRot(owner.getYRot());
        // Pickup constructs both links in one server tick. A short grace also
        // avoids discarding a just-spawned anchor before its passenger is linked.
        if (!level().isClientSide && tickCount > 2
                && (!(owner instanceof ServerPlayer) || !owner.isAlive() || owner.isRemoved()
                    || owner.level() != level() || getPassengers().isEmpty())) discard();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public boolean isPickable() { return false; }

    @Override
    public boolean isPushable() { return false; }

    @Override
    public boolean canBeCollidedWith() { return false; }

    @Override
    public boolean shouldBeSaved() { return false; }
}
