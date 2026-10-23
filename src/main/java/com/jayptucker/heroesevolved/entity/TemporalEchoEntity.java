package com.jayptucker.heroesevolved.entity;

import com.jayptucker.heroesevolved.time.TemporalEchoService;
import com.jayptucker.heroesevolved.time.TemporalParadoxService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
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
    private boolean hostile;

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
                .add(Attributes.MAX_HEALTH, 24.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.32D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.15D, true));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    /** Turns a disturbed memory into a hostile, killable temporal NPC. */
    public void becomeHostile(net.minecraft.server.level.ServerPlayer attacker) {
        if (hostile) {
            setTarget(attacker);
            return;
        }

        hostile = true;
        setNoAi(false);
        setNoGravity(false);
        // Replaying echoes are invulnerable so they cannot be farmed. A
        // disturbed echo must become mortal, or survival players could never
        // defeat it.
        setInvulnerable(false);
        setTarget(attacker);
        setCustomName(net.minecraft.network.chat.Component.literal(
                getProfileName() + " (Temporal Echo)"
        ));
    }

    public boolean isHostile() {
        return hostile;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PROFILE_ID, Optional.empty());
        builder.define(PROFILE_NAME, "");
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // The first attack is a warning: the memory stops replaying and
        // fights back. Later hits damage the hostile echo normally.
        if (!hostile && source.getEntity()
                instanceof net.minecraft.server.level.ServerPlayer attacker) {
            TemporalEchoService.provoke(this, attacker);
            return false;
        }

        if (!hostile || !super.hurt(source, amount)) {
            return false;
        }

        // Wounding your own past self wounds you as well.
        if (source.getEntity() instanceof net.minecraft.server.level.ServerPlayer
                attacker && attacker.getUUID().equals(getProfileId())) {
            TemporalParadoxService.mirrorSelfDamage(attacker, amount);
        }
        return true;
    }

    /** True when this echo is a recording of the given player. */
    public boolean isEchoOf(net.minecraft.world.entity.player.Player player) {
        return player.getUUID().equals(getProfileId());
    }

    /** Stops replaying history and turns to look at the player who broke it. */
    public void faceIntruder(net.minecraft.world.entity.player.Player player) {
        // Replaying echoes have no AI, so turn the body and head directly.
        lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
                player.getEyePosition());
        setYHeadRot(getYRot());
        yBodyRot = getYRot();
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        TemporalEchoService.onEchoKilled(this, source.getEntity());
    }

    @Override
    public boolean isPickable() {
        // Players must be able to attempt an attack to disturb an echo.
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return hostile;
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
