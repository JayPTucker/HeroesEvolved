package com.jayptucker.heroesevolved.endgame;

import com.jayptucker.heroesevolved.combat.BossLocks;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A Seal Knot: a knot of the Living Seal floating at a grounding altar's focus, fed by a beam from a tower crystal. In the
 * first phase it wards the Dragon (the shield); from the second it sleeps until a big move drinks from it (the room
 * weapon). It is a creature so every power that hits creatures hits it, and a lock target ({@link BossLocks}): plain
 * weapons do half, a power's blows in full, a key's blows more, an aimed key a share at once, a returned shard shatters it.
 */
public final class SealKnot extends Monster implements BossLocks.Lockable {
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(SealKnot.class, EntityDataSerializers.INT),
            INDEX = SynchedEntityData.defineId(SealKnot.class, EntityDataSerializers.INT);
    private SealFight fight;
    private int lastRing = -100;

    public SealKnot(EntityType<? extends SealKnot> type, Level level) {
        super(type, level);
        setNoGravity(true);
        setPersistenceRequired();
        xpReward = 0;
    }
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, SealGeometry.KNOT_HEALTH)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1).add(Attributes.MOVEMENT_SPEED, 0).add(Attributes.ARMOR, 0);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder b) { super.defineSynchedData(b); b.define(STATE, SealView.NONE); b.define(INDEX, 0); }
    @Override protected void registerGoals() {}

    /** A new knot at altar {@code index} for {@code fight}, its health scaled to the party. */
    static SealKnot form(SealFight fight, int index, int state, float health, Vec3 at) {
        SealKnot knot = com.jayptucker.heroesevolved.registry.ModEntities.SEAL_KNOT.get().create(fight.e.level);
        if (knot == null) return null;
        knot.fight = fight;
        knot.entityData.set(INDEX, index);
        knot.entityData.set(STATE, state);
        knot.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
        knot.setHealth(health);
        knot.moveTo(at.x, at.y, at.z, 0, 0);
        fight.e.level.addFreshEntity(knot);
        return knot;
    }
    public int index() { return entityData.get(INDEX); }
    public int state() { return entityData.get(STATE); }
    void state(int state) { entityData.set(STATE, state); }

    @Override public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        if (level().isClientSide) return;
        if (fight == null || !fight.owns(this)) { discard(); return; }
        Vec3 at = fight.knotAt(index());
        if (position().distanceToSqr(at) > 1e-4) setPos(at);
    }
    @Override public boolean isPushable() { return false; }
    @Override protected void pushEntities() {}
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override public boolean isInvulnerableTo(DamageSource source) {
        return source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_DROWNING) || source.is(DamageTypeTags.IS_EXPLOSION) && !(source.getEntity() instanceof ServerPlayer)
                || source.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL) || source.is(net.minecraft.world.damagesource.DamageTypes.CRAMMING) || super.isInvulnerableTo(source);
    }
    @Override protected SoundEvent getAmbientSound() { return null; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.AMETHYST_BLOCK_HIT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.GLASS_BREAK; }
    @Override public net.minecraft.network.chat.Component getName() { return net.minecraft.network.chat.Component.translatable("entity.heroesevolved.seal_knot"); }

    // ---------------------------------------------------------------- the lock

    @Override public BossLocks.Lock lock() {
        if (fight == null || !fight.fighting()) return null;
        return state() == SealView.WARDING ? BossLocks.Lock.SHIELD : state() == SealView.FEEDING ? BossLocks.Lock.ROOM_WEAPON : null;
    }
    @Override public Vec3 aimPoint() { return getBoundingBox().getCenter(); }
    @Override public void answered(ServerPlayer player, BossLocks.Key key) {
        if (lock() == null) return;
        float share = key.strong() ? BossLocks.STRONG_AIMED : BossLocks.GOOD_AIMED;
        if (com.jayptucker.heroesevolved.amplify.Amplification.surged(player)) share *= BossLocks.SURGED;
        Vec3 from = player.getEyePosition().add(player.getLookAngle().scale(.6)).add(0, -.25, 0);
        SetPiecePayload.sendSeeded((ServerLevel) level(), Piece.END_SEAL_KEY, from, aimPoint(), 1, this, 0,
                key.role().ordinal() | (key.strong() ? 1 << 5 : 0));
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 1.6F, .7F);
        take(getMaxHealth() * share, player);
    }

    @Override public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || !Float.isFinite(amount) || amount <= 0) return false;
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        BossLocks.Lock lock = lock();
        if (lock == null) {
            // Asleep: the blow rings off it.
            if (tickCount - lastRing > 10 && source.getEntity() instanceof ServerPlayer) {
                lastRing = tickCount;
                level().playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.HOSTILE, 1, .5F);
            }
            return false;
        }
        if (isInvulnerableTo(source)) return false;
        ServerPlayer by = source.getEntity() instanceof ServerPlayer p ? p : null;
        if (by != null && (EndBattle.sheltered(by) || !EndBattle.eligible(by))) return false;
        if (source.getDirectEntity() instanceof EndBolt bolt && bolt.returned()) { take(getMaxHealth() * 2, by); return true; }
        float factor = BossLocks.hitFactor(source, lock);
        if (factor <= 0) return false;
        take(Math.min(amount * factor, getMaxHealth() * .6F), by);
        return true;
    }
    /** Takes {@code amount}; at nothing left it shatters and tells the fight who broke it. */
    void take(float amount, ServerPlayer by) {
        if (isRemoved() || fight == null) return;
        float left = getHealth() - amount;
        if (left > 0) {
            setHealth(left);
            SetPiecePayload.sendSeeded((ServerLevel) level(), Piece.END_SEAL_CRACK, aimPoint(), aimPoint(), 1, this, 0, Math.round(15 * (1 - left / getMaxHealth())));
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.HOSTILE, 1.2F, .6F + .6F * left / getMaxHealth());
            return;
        }
        setHealth(0);
        fight.knotBroken(index(), this, by);
        discard();
    }
}
