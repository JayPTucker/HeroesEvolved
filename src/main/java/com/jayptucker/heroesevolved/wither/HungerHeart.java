package com.jayptucker.heroesevolved.wither;

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
 * A Hunger Heart: a crystal of the star's light floating in one of the three bone pylons. In the first phase the hearts
 * feed the Star Ward over the Wither's ribs; from the second they sleep until a heavy move draws on one, and breaking a
 * feeding heart turns the hunger back on the Wither. A heart is a lock target ({@link BossLocks}): plain weapons do half
 * to it, a power's blows do it all, a key's hits more, an aimed key a share at once, and a skull sent back shatters it.
 */
public final class HungerHeart extends Monster implements BossLocks.Lockable {
    public static final int NONE = 0, WARDING = 1, DORMANT = 2, FEEDING = 3;
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(HungerHeart.class, EntityDataSerializers.INT),
            INDEX = SynchedEntityData.defineId(HungerHeart.class, EntityDataSerializers.INT);
    private RiftWitherEntity boss;
    private int lastStruck = -100;

    public HungerHeart(EntityType<? extends HungerHeart> type, Level level) {
        super(type, level);
        setNoGravity(true);
        setPersistenceRequired();
        xpReward = 0;
    }
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, WitherGeometry.HEART_HEALTH)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1).add(Attributes.MOVEMENT_SPEED, 0).add(Attributes.ARMOR, 0);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder b) { super.defineSynchedData(b); b.define(STATE, NONE); b.define(INDEX, 0); }
    @Override protected void registerGoals() {}

    /** A new heart in pylon {@code index}, for {@code boss}, with health scaled for the party. */
    static HungerHeart form(RiftWitherEntity boss, int index, int state, float health) {
        HungerHeart heart = com.jayptucker.heroesevolved.registry.ModEntities.HUNGER_HEART.get().create(boss.level());
        if (heart == null) return null;
        heart.boss = boss;
        heart.entityData.set(INDEX, index);
        heart.entityData.set(STATE, state);
        heart.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
        heart.setHealth(health);
        Vec3 at = WitherGeometry.heart(index);
        heart.moveTo(at.x, at.y, at.z, 0, 0);
        boss.level().addFreshEntity(heart);
        return heart;
    }
    public int index() { return entityData.get(INDEX); }
    public int state() { return entityData.get(STATE); }
    void state(int state) { entityData.set(STATE, state); }
    RiftWitherEntity boss() { return boss; }

    @Override public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        if (level().isClientSide) return;
        if (boss == null || boss.isRemoved() || !boss.fighting() || boss.hearts[index()] != this) { discard(); return; }
        Vec3 at = WitherGeometry.heart(index());
        if (position().distanceToSqr(at) > 1e-4) setPos(at);
    }
    @Override public boolean isPushable() { return false; }
    @Override protected void pushEntities() {}
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override public boolean isInvulnerableTo(DamageSource source) {
        return source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_DROWNING) || source.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL)
                || source.is(net.minecraft.world.damagesource.DamageTypes.CRAMMING) || super.isInvulnerableTo(source);
    }
    @Override protected SoundEvent getAmbientSound() { return null; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.AMETHYST_BLOCK_HIT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.GLASS_BREAK; }
    @Override public net.minecraft.network.chat.Component getName() {
        return net.minecraft.network.chat.Component.translatable("entity.heroesevolved.hunger_heart");
    }

    // ---------------------------------------------------------------- the lock

    @Override public BossLocks.Lock lock() {
        if (boss == null || !boss.fighting() || boss.arriving()) return null;
        return state() == WARDING ? BossLocks.Lock.SHIELD : state() == FEEDING ? BossLocks.Lock.ROOM_WEAPON : null;
    }
    @Override public Vec3 aimPoint() { return getBoundingBox().getCenter(); }
    @Override public void answered(ServerPlayer player, BossLocks.Key key) {
        if (lock() == null) return;
        float share = key.strong() ? BossLocks.STRONG_AIMED : BossLocks.GOOD_AIMED;
        if (com.jayptucker.heroesevolved.amplify.Amplification.surged(player)) share *= BossLocks.SURGED;
        Vec3 from = player.getEyePosition().add(player.getLookAngle().scale(.6)).add(0, -.25, 0);
        SetPiecePayload.sendSeeded((ServerLevel) level(), Piece.WITHER_KEY, from, aimPoint(), 1, this, 0,
                key.role().ordinal() | (key.strong() ? 1 << 5 : 0));
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 1.6F, .7F);
        take(getMaxHealth() * share, player);
    }

    @Override public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || !Float.isFinite(amount) || amount <= 0) return false;
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        BossLocks.Lock lock = lock();
        if (lock == null) {
            // Asleep (or the fight has not begun): the blow rings off it.
            if (tickCount - lastStruck > 10 && source.getEntity() instanceof ServerPlayer) {
                lastStruck = tickCount;
                level().playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.HOSTILE, 1, .5F);
            }
            return false;
        }
        if (isInvulnerableTo(source)) return false;
        ServerPlayer by = source.getEntity() instanceof ServerPlayer p ? p : null;
        if (by != null && WitherArena.sheltered(by)) return false;
        if (source.getDirectEntity() instanceof RiftSkull skull && skull.returned()) {
            take(getMaxHealth() * 2, by);
            return true;
        }
        float factor = BossLocks.hitFactor(source, lock);
        if (factor <= 0) return false;
        take(Math.min(amount * factor, getMaxHealth() * .6F), by);
        return true;
    }
    /** Takes {@code amount}; at nothing left it shatters and tells the Wither who broke it. */
    void take(float amount, ServerPlayer by) {
        if (isRemoved() || boss == null) return;
        float left = getHealth() - amount;
        lastStruck = tickCount;
        if (left > 0) {
            setHealth(left);
            SetPiecePayload.sendSeeded((ServerLevel) level(), Piece.WITHER_CRACK, aimPoint(), aimPoint(), 1, this, 0, 8 + Math.round(7 * (1 - left / getMaxHealth())));
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.HOSTILE, 1.2F, .6F + .6F * left / getMaxHealth());
            return;
        }
        setHealth(0);
        boss.heartBroken(index(), this, by);
        discard();
    }
}
