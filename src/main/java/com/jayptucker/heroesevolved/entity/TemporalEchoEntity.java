package com.jayptucker.heroesevolved.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

/**
 * A server-authoritative, non-interactive player-shaped entity used for
 * Temporal Snapshot echoes. The client renderer resolves the captured
 * player's skin from this profile data.
 */
public final class TemporalEchoEntity extends PathfinderMob {
    private static final EntityDataAccessor<Optional<UUID>> PROFILE_ID =
            SynchedEntityData.defineId(
                    TemporalEchoEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID
            );
    private static final EntityDataAccessor<String> PROFILE_NAME =
            SynchedEntityData.defineId(
                    TemporalEchoEntity.class,
                    EntityDataSerializers.STRING
            );

    public TemporalEchoEntity(
            EntityType<TemporalEchoEntity> entityType,
            Level level
    ) {
        super(entityType, level);
        setNoAi(true);
        setNoGravity(true);
        setInvulnerable(true);
        setSilent(true);
    }

    public TemporalEchoEntity(
            EntityType<TemporalEchoEntity> entityType,
            ServerLevel level,
            UUID profileId,
            String profileName
    ) {
        this(entityType, level);
        entityData.set(PROFILE_ID, Optional.of(profileId));
        entityData.set(PROFILE_NAME, profileName);
    }

    public UUID getProfileId() {
        return entityData.get(PROFILE_ID).orElse(getUUID());
    }

    public String getProfileName() {
        return entityData.get(PROFILE_NAME);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 1.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PROFILE_ID, Optional.empty());
        builder.define(PROFILE_NAME, "");
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
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

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        // Echoes are rebuilt when their owner enters a snapshot.
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        // Echoes are never loaded from disk.
    }
}
