package com.jayptucker.heroesevolved.story.sylar;

import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.effect.*;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** One mortal, player-shaped antagonist. Combat advances with entity ticks, so time and ice work on him. */
public final class SylarEntity extends PathfinderMob implements Enemy {
    private static final EntityDataAccessor<Boolean> EXTRACTING = SynchedEntityData.defineId(SylarEntity.class, EntityDataSerializers.BOOLEAN);
    private UUID quarry;
    private boolean hunting;
    private int combatTicks, extractionTicks, recovery;
    public SylarEntity(EntityType<? extends SylarEntity> type, Level level) {
        super(type, level); setPersistenceRequired(); setSilent(true); setCanPickUpLoot(false);
    }
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 60).add(Attributes.MOVEMENT_SPEED, .29)
                .add(Attributes.ATTACK_DAMAGE, 4).add(Attributes.FOLLOW_RANGE, 64).add(Attributes.ARMOR, 4);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder); builder.define(EXTRACTING, false);
    }
    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this)); goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.12, true));
    }
    public void watch(ServerPlayer player) { quarry = player.getUUID(); hunting = false; setTarget(null); }
    public void hunt(ServerPlayer player) { quarry = player.getUUID(); hunting = true; setTarget(player); }
    public UUID quarry() { return quarry; }
    public boolean hunting() { return hunting; }
    public boolean extracting() { return entityData.get(EXTRACTING); }
    public int extractionTicks() { return extractionTicks; }
    public void interrupt() {
        extractionTicks = 0; recovery = 60; entityData.set(EXTRACTING, false);
        getNavigation().stop();
    }
    @Override protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel serverLevel) || quarry == null) return;
        ServerPlayer victim = serverLevel.getServer().getPlayerList().getPlayer(quarry);
        if (victim == null || !SylarStoryService.eligible(victim) || victim.level() != level()) {
            interrupt(); setTarget(null); return;
        }
        getLookControl().setLookAt(victim, 30, 30);
        if (!hunting || com.jayptucker.heroesevolved.events.EclipseService.arePowersSuppressed(victim)) {
            setTarget(null); getNavigation().stop(); return;
        }
        if (com.jayptucker.heroesevolved.telepathy.TelepathyService.isForbiddenTarget(this, victim)) {
            interrupt(); setTarget(null); return;
        }
        combatTicks++;
        var story = SylarStoryData.get(serverLevel.getServer());
        if (story.knows(ModAbilities.REGENERATION_ID) && combatTicks % 40 == 0) heal(1);
        if (recovery > 0) { recovery--; setTarget(null); return; }
        boolean inReach = distanceToSqr(victim) <= 7 * 7 && hasLineOfSight(victim);
        if (extractionTicks > 0) {
            if (!inReach) { interrupt(); return; }
            setTarget(null); getNavigation().stop();
            entityData.set(EXTRACTING, true); extractionTicks++;
            // Powers, movement and allies can all break the hold. No input lock or teleportation.
            victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 12, 1, false, false, true));
            if (extractionTicks % 10 == 0) AbilityEffects.send(serverLevel, Kind.PSYCHIC_TETHER, getEyePosition(), victim.getEyePosition(), .45);
            if (extractionTicks % 20 == 0) SylarStoryService.clock(victim, .23F);
            if (extractionTicks >= 120) {
                float before = victim.getHealth() + victim.getAbsorptionAmount();
                boolean hit = victim.hurt(damageSources().mobAttack(this), 8);
                if (hit && (!victim.isAlive() || victim.getHealth() + victim.getAbsorptionAmount() < before))
                    SylarStoryService.acquire(this, victim);
                interrupt(); recovery = 140;
            }
            return;
        }
        setTarget(victim);
        if (inReach && combatTicks >= 80) {
            extractionTicks = 1; entityData.set(EXTRACTING, true); setTarget(null); getNavigation().stop();
            SylarStoryService.message(victim, "story.heroesevolved.sylar.hold");
            return;
        }
        if (combatTicks % 80 == 0 && distanceToSqr(victim) < 20 * 20 && hasLineOfSight(victim)) {
            copiedPower(serverLevel, victim, story);
        }
        if (story.knows(ModAbilities.FLIGHT_ID) && victim.getY() > getY() + 3 && victim.getY() < getY() + 20)
            setDeltaMovement(getDeltaMovement().add(0, .12, 0));
    }
    private void copiedPower(ServerLevel level, ServerPlayer victim, SylarStoryData data) {
        var powers = data.learned().stream().filter(id -> !id.equals(ModAbilities.REGENERATION_ID) && !id.equals(ModAbilities.FLIGHT_ID)).toList();
        var power = powers.isEmpty() ? ModAbilities.TELEKINESIS_ID : powers.get(random.nextInt(powers.size()));
        Kind kind = Kind.PSYCHIC_PUSH;
        if (power.equals(ModAbilities.CRYOKINESIS_ID)) {
            kind = Kind.CRYO_BOLT; victim.setTicksFrozen(Math.min(victim.getTicksRequiredToFreeze(), victim.getTicksFrozen() + 60));
            victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
        } else if (power.equals(ModAbilities.SUPERSPEED_ID)) {
            Vec3 from = position(), difference = victim.position().subtract(from);
            Vec3 end = com.jayptucker.heroesevolved.speed.SpeedService.swept(this,
                    difference.normalize().scale(Math.max(0, difference.length() - 1.8)));
            if (end.distanceToSqr(victim.position()) > 9) return;
            teleportTo(end.x, end.y, end.z);
            AbilityEffects.send(level, Kind.SPEED_TRAIL, from, end, 1);
            kind = Kind.SPEED_IMPACT;
        } else if (power.equals(ModAbilities.ELECTROKINESIS_ID)) kind = Kind.ELECTRIC_ARC;
        else if (power.equals(ModAbilities.QUAKE_ID)) kind = Kind.QUAKE_BEAM;
        else if (power.equals(ModAbilities.RADIATION_ID)) { kind = Kind.NUCLEAR_CHARGE; victim.igniteForSeconds(2); }
        else if (power.equals(ModAbilities.TIME_MANIPULATION_ID)) {
            kind = Kind.TEMPORAL_BURST; victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 2));
        } else if (power.equals(ModAbilities.TELEPATHY_ID)) {
            kind = Kind.PSYCHIC_TETHER; victim.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0));
        }
        AbilityEffects.send(level, kind, getEyePosition(), victim.getEyePosition(), 1.1);
        if (victim.hurt(damageSources().mobAttack(this), 3)) {
            Vec3 away = victim.position().subtract(position()).multiply(1, 0, 1).normalize().scale(.55).add(0, .15, 0);
            victim.push(away); victim.hurtMarked = true;
        }
    }
    private int nextPredictedDodge;
    @Override public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && tickCount >= nextPredictedDodge && source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile
                && SylarStoryData.get(level().getServer()).knows(ModAbilities.PRECOGNITION_ID)) {
            Vec3 facing = getLookAngle().multiply(1, 0, 1).normalize(), side = new Vec3(-facing.z, 0, facing.x);
            Vec3 end = com.jayptucker.heroesevolved.speed.SpeedService.swept(this, side.scale(2));
            if (end.distanceToSqr(position()) >= 2.25) {
                nextPredictedDodge = tickCount + 80;
                AbilityEffects.send((ServerLevel) level(), Kind.PROPHECY_BREAK, position(), end, 1.5);
                teleportTo(end.x, end.y, end.z); return false;
            }
        }
        boolean hit = super.hurt(source, amount);
        if (hit && amount > 0 && !level().isClientSide) {
            interrupt(); SylarStoryService.provoked(this);
        }
        return hit;
    }
    @Override public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide && !isAlive()) SylarStoryService.defeated(this, source.getEntity());
    }
    @Override public boolean shouldBeSaved() { return false; }
    // A human-sized mob normally fades out near the distance used for our sightings.
    @Override public boolean shouldRenderAtSqrDistance(double distance) { return distance < 128 * 128; }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override protected boolean shouldDespawnInPeaceful() { return false; }
    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); }
    @Override public Component getName() { return Component.translatable("entity.heroesevolved.sylar"); }
}
