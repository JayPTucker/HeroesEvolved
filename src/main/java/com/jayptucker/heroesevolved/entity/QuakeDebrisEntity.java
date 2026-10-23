package com.jayptucker.heroesevolved.entity;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A chunk of ground thrown up by a Quake power. It is visual only: the
 * terrain it imitates is never removed, and it never places a block.
 */
public final class QuakeDebrisEntity extends Entity {
    private static final EntityDataAccessor<BlockState> BLOCK_STATE =
            SynchedEntityData.defineId(
                    QuakeDebrisEntity.class,
                    EntityDataSerializers.BLOCK_STATE
            );
    private static final int MAXIMUM_LIFETIME_TICKS = 60;
    private static final double GRAVITY = 0.06D;

    public QuakeDebrisEntity(
            EntityType<? extends QuakeDebrisEntity> entityType,
            Level level
    ) {
        super(entityType, level);
    }

    public static void launch(
            ServerLevel level,
            BlockState state,
            Vec3 position,
            Vec3 velocity
    ) {
        if (state.isAir() || state.getRenderShape()
                != net.minecraft.world.level.block.RenderShape.MODEL) {
            return;
        }

        QuakeDebrisEntity debris = new QuakeDebrisEntity(
                com.jayptucker.heroesevolved.registry.ModEntities
                        .QUAKE_DEBRIS.get(),
                level
        );
        debris.entityData.set(BLOCK_STATE, state);
        debris.setPos(position);
        debris.setDeltaMovement(velocity);
        level.addFreshEntity(debris);
    }

    public BlockState getBlockState() {
        return entityData.get(BLOCK_STATE);
    }

    @Override
    public void tick() {
        super.tick();
        // Runs on both sides, so clients animate the arc smoothly from the
        // spawn velocity instead of waiting on position updates.
        setDeltaMovement(getDeltaMovement().add(0.0D, -GRAVITY, 0.0D)
                .scale(0.98D));
        move(MoverType.SELF, getDeltaMovement());

        boolean landed = onGround() && tickCount > 4;
        if (!level().isClientSide() && (landed
                || tickCount >= MAXIMUM_LIFETIME_TICKS)) {
            crumble();
        }
    }

    private void crumble() {
        if (level() instanceof ServerLevel level) {
            level.sendParticles(
                    new BlockParticleOption(ParticleTypes.BLOCK, getBlockState()),
                    getX(), getY() + 0.2D, getZ(),
                    10, 0.2D, 0.1D, 0.2D, 0.05D
            );
            level.playSound(null, getX(), getY(), getZ(),
                    getBlockState().getSoundType().getBreakSound(),
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.35F, 0.8F);
        }
        discard();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(BLOCK_STATE, Blocks.DIRT.defaultBlockState());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        // Debris lives for a few seconds and is never persisted.
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        // Debris lives for a few seconds and is never persisted.
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
    public boolean shouldBeSaved() {
        return false;
    }
}
