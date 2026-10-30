package com.jayptucker.heroesevolved.story.sylar;

import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.effect.*;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Sylar: one mortal, player-shaped antagonist. Combat advances with entity ticks, so time and ice work on him.
 *
 * <p>When he hunts, he fights like a boss. A boss bar shows his health, which grows with every power
 * he has taken. He fights in three phases: <i>The Watchmaker</i>, measured, telekinesis and one
 * stolen gift at a time; <i>Hunger</i> below half, faster and harder; <i>Unfinished</i> below a
 * fifth, when he may try to escape if he knows how. Every power he knows is a real move with a windup
 * you can read (see {@link SylarPowers}); his passive gifts work too: Precognition sidesteps a blow,
 * Regeneration gets him back up once, Mimicry borrows your power for the fight.</p>
 */
public final class SylarEntity extends PathfinderMob implements Enemy {
    private static final EntityDataAccessor<Boolean> EXTRACTING = SynchedEntityData.defineId(SylarEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> POSE = SynchedEntityData.defineId(SylarEntity.class, EntityDataSerializers.INT);
    private static final ResourceLocation BOSS_HEALTH = ResourceLocation.fromNamespaceAndPath("heroesevolved", "sylar_boss_health");
    private static final double BAR_RANGE = 48;

    private UUID quarry;
    private boolean hunting;
    private int combatTicks, extractionTicks, recovery;

    // The fight.
    private final ServerBossEvent bar = new ServerBossEvent(Component.translatable("story.heroesevolved.sylar.bar.1"),
            BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
    private int phase = 1;
    private SylarPowers.Move casting;
    private int castTicks, globalCooldown = 20, staggerDamageTaken;
    private final Map<String, Integer> cooldowns = new HashMap<>();
    private Vec3 mark = Vec3.ZERO;
    private ResourceLocation borrowed;
    private boolean revived, escapeTried;
    private int downedTicks, escapeIn = -1, unseenTicks, nextSidestep;
    private UUID pinned;
    private int pinnedUntil;
    private UUID flurryTarget;
    private int flurryLeft, flurryNext;
    private float flurryDamage;

    public SylarEntity(EntityType<? extends SylarEntity> type, Level level) {
        super(type, level); setPersistenceRequired(); setSilent(true); setCanPickUpLoot(false);
        bar.setVisible(false);
    }
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 60).add(Attributes.MOVEMENT_SPEED, .29)
                .add(Attributes.ATTACK_DAMAGE, 4).add(Attributes.FOLLOW_RANGE, 64).add(Attributes.ARMOR, 4);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder); builder.define(EXTRACTING, false); builder.define(POSE, 0);
    }
    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this)); goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.12, true));
    }

    public void watch(ServerPlayer player) { quarry = player.getUUID(); hunting = false; setTarget(null); }

    /** The hunt begins: he grows into a boss sized to what he has taken. */
    public void hunt(ServerPlayer player) {
        quarry = player.getUUID(); hunting = true; setTarget(player);
        if (!(level() instanceof ServerLevel serverLevel)) return;
        var story = SylarStoryData.get(serverLevel.getServer());
        double health = Math.min(240, 80 + 12 * story.learned().size());
        var attribute = getAttribute(Attributes.MAX_HEALTH);
        attribute.removeModifier(BOSS_HEALTH);
        attribute.addPermanentModifier(new AttributeModifier(BOSS_HEALTH, health - attribute.getBaseValue(), AttributeModifier.Operation.ADD_VALUE));
        setHealth(getMaxHealth());
        // Peter's gift: he borrows yours for the length of the fight, whether he has taken it or not.
        if (story.knows(ModAbilities.MIMICRY_ID)) {
            PlayerAbilityService.getData(player).assignedPower().filter(e -> e.getValue().isUnlocked())
                    .map(Map.Entry::getKey).filter(id -> !story.knows(id)).ifPresent(id -> borrowed = id);
        }
        bar.setName(Component.translatable("story.heroesevolved.sylar.bar.1"));
        bar.setVisible(true);
    }

    public UUID quarry() { return quarry; }
    public boolean hunting() { return hunting; }
    public boolean extracting() { return entityData.get(EXTRACTING); }
    public int extractionTicks() { return extractionTicks; }
    public int phase() { return phase; }
    public SylarPowers.Pose castPose() { return SylarPowers.Pose.values()[Math.clamp(entityData.get(POSE), 0, SylarPowers.Pose.values().length - 1)]; }
    public String casting() { return casting == null ? "" : casting.id(); }
    public boolean downed() { return downedTicks > 0; }
    public boolean revived() { return revived; }
    /** Ticks until he runs (0 or less when he is not about to); and how long this fight has gone on. */
    public int escapeIn() { return escapeIn; }
    public int combatTicks() { return combatTicks; }
    public ResourceLocation borrowed() { return borrowed; }
    ServerBossEvent bar() { return bar; }

    public void interrupt() {
        extractionTicks = 0; recovery = 60; entityData.set(EXTRACTING, false);
        cancelCast();
        pinned = null;
        getNavigation().stop();
    }

    private void cancelCast() {
        if (casting != null) cooldowns.put(casting.id(), tickCount + casting.cooldown() / 2);
        casting = null; castTicks = 0; entityData.set(POSE, 0);
    }

    // ------------------------------------------------------------------
    // Hooks his moves use
    // ------------------------------------------------------------------

    void mark(Vec3 at) { mark = at; }
    Vec3 mark() { return mark; }

    /** Holds the target in place, a little off the ground, while he closes in. */
    void pin(LivingEntity target, int ticks) { pinned = target.getUUID(); pinnedUntil = tickCount + ticks; }

    public boolean pinning() { return pinned != null && tickCount < pinnedUntil; }

    void flurry(LivingEntity target, int hits, float damage) {
        flurryTarget = target.getUUID(); flurryLeft = hits; flurryDamage = damage; flurryNext = tickCount;
    }

    /** What he can use right now: everything he has taken, and anything he is borrowing. */
    Set<ResourceLocation> known(ServerLevel level) {
        Set<ResourceLocation> known = new HashSet<>(SylarStoryData.get(level.getServer()).learned());
        if (borrowed != null) known.add(borrowed);
        return known;
    }

    private float power() { return phase == 1 ? 1.0F : phase == 2 ? 1.25F : 1.4F; }

    // ------------------------------------------------------------------
    // The fight, tick by tick
    // ------------------------------------------------------------------

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
        tickBar(serverLevel);
        if (downedTicks > 0) { tickDowned(serverLevel, victim); return; }
        if (story.knows(ModAbilities.REGENERATION_ID) && combatTicks % 30 == 0) {
            heal(1);
            if (combatTicks % 120 == 0) AbilityEffects.send(serverLevel, Kind.REGEN_MEND, position(), getEyePosition(), 0.8);
        }
        tickPhase(serverLevel, victim, story);
        tickPin(serverLevel);
        tickFlurry();
        if (combatTicks % 10 == 0 && casting == null) deflect(serverLevel);
        if (escapeIn > 0 && --escapeIn == 0) { escape(serverLevel, victim, story); return; }
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
        if (casting != null) { tickCast(serverLevel); return; }
        setTarget(victim);
        if (inReach && combatTicks >= 80) {
            extractionTicks = 1; entityData.set(EXTRACTING, true); setTarget(null); getNavigation().stop();
            SylarStoryService.message(victim, "story.heroesevolved.sylar.hold");
            return;
        }
        if (globalCooldown > 0) globalCooldown--;
        else if (combatTicks >= 20) chooseMove(serverLevel, victim);
        phaseWalk(serverLevel, victim, story);
        if (story.knows(ModAbilities.FLIGHT_ID) && victim.getY() > getY() + 3 && victim.getY() < getY() + 20)
            setDeltaMovement(getDeltaMovement().add(0, .12, 0));
    }

    /** Picks what to do next, favouring what he took from you, and starts winding it up. */
    private void chooseMove(ServerLevel level, ServerPlayer victim) {
        double distance = Math.sqrt(distanceToSqr(victim));
        boolean sight = hasLineOfSight(victim);
        ResourceLocation theirs = PlayerAbilityService.getData(victim).assignedPower().map(Map.Entry::getKey).orElse(null);
        List<SylarPowers.Move> options = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        int total = 0;
        for (SylarPowers.Move move : SylarPowers.available(known(level))) {
            if (cooldowns.getOrDefault(move.id(), 0) > tickCount || distance < move.minRange() || distance > move.maxRange()
                    || move.needsSight() && !sight) continue;
            int weight = move.power().equals(theirs) ? 3 : move.power().equals(ModAbilities.TELEKINESIS_ID) ? 1 : 2;
            options.add(move); weights.add(weight); total += weight;
        }
        if (options.isEmpty()) return;
        int roll = random.nextInt(total);
        SylarPowers.Move move = options.getLast();
        for (int i = 0; i < options.size(); i++) { roll -= weights.get(i); if (roll < 0) { move = options.get(i); break; } }
        casting = move; castTicks = 0; staggerDamageTaken = 0;
        entityData.set(POSE, move.pose().ordinal());
        getNavigation().stop();
        move.telegraph().run(this, level, victim, power());
        if (move.power().equals(theirs) && !move.power().equals(ModAbilities.TELEKINESIS_ID) && random.nextFloat() < 0.35F)
            SylarVoice.say(victim, "story.heroesevolved.sylar.say.yours");
    }

    private void tickCast(ServerLevel level) {
        ServerPlayer victim = level.getServer().getPlayerList().getPlayer(quarry);
        if (victim == null) { cancelCast(); return; }
        getNavigation().stop();
        castTicks++;
        if (castTicks % 4 == 0) casting.pulse().run(this, level, victim, power());
        if (castTicks < casting.windup()) return;
        SylarPowers.Move move = casting;
        cancelCast();
        cooldowns.put(move.id(), tickCount + move.cooldown());
        if (!move.needsSight() || hasLineOfSight(victim)) move.execute().run(this, level, victim, power());
        globalCooldown = phase == 1 ? 50 : phase == 2 ? 32 : 24;
    }

    private void tickPin(ServerLevel level) {
        if (pinned == null) return;
        if (tickCount >= pinnedUntil || !(level.getEntity(pinned) instanceof LivingEntity held) || !held.isAlive()) { pinned = null; return; }
        // Lifted just off the ground and held there.
        held.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 3, 6, false, false, false));
        SylarPowers.push(held, new Vec3(0, held.onGround() ? 0.12 : 0.0, 0));
        held.fallDistance = 0;
        if (tickCount % 5 == 0) AbilityEffects.send(level, Kind.TK_GRIP, SylarPowers.hand(this), held.getBoundingBox().getCenter(), 0.9);
        // He walks up to you while you hang there.
        if (distanceToSqr(held) > 9) getNavigation().moveTo(held, 1.2);
    }

    private void tickFlurry() {
        if (flurryLeft <= 0 || tickCount < flurryNext || !(level() instanceof ServerLevel level)) return;
        if (!(level.getEntity(flurryTarget) instanceof LivingEntity target) || !target.isAlive() || distanceToSqr(target) > 16) { flurryLeft = 0; return; }
        SylarPowers.hurt(this, target, flurryDamage);
        swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        AbilityEffects.send(level, Kind.SPEED_IMPACT, getEyePosition(), target.getBoundingBox().getCenter(), 1);
        flurryLeft--; flurryNext = tickCount + 3;
    }

    /** Shots that come at him while he is not busy turn aside. */
    private void deflect(ServerLevel level) {
        for (Projectile shot : level.getEntitiesOfClass(Projectile.class, getBoundingBox().inflate(4), s -> s.getOwner() != this)) {
            Vec3 toward = position().subtract(shot.position());
            if (shot.getDeltaMovement().dot(toward) <= 0) continue;
            SylarPowers.push(shot, shot.getDeltaMovement().scale(-0.8).add(0, 0.2, 0));
            AbilityEffects.send(level, Kind.TK_WAVE, getEyePosition(), shot.position(), 0.6);
        }
    }

    /** With Phasing, a wall is no reason to stop: he walks out of it near you. */
    private void phaseWalk(ServerLevel level, ServerPlayer victim, SylarStoryData story) {
        if (!story.knows(ModAbilities.PHASING_ID) && !ModAbilities.PHASING_ID.equals(borrowed)) return;
        unseenTicks = hasLineOfSight(victim) ? 0 : unseenTicks + 1;
        if (unseenTicks < 60 || distanceToSqr(victim) > 16 * 16) return;
        Vec3 near = victim.position().add(SylarPowers.flat(position().subtract(victim.position())).scale(2.5));
        if (!level.noCollision(this, getBoundingBox().move(near.subtract(position())))) return;
        AbilityEffects.send(level, Kind.MIND_PULSE, position(), getEyePosition(), 1);
        teleportTo(near.x, near.y, near.z);
        unseenTicks = 0;
        SylarVoice.narrate(victim, "story.heroesevolved.sylar.narrate.phase");
    }

    // ------------------------------------------------------------------
    // The boss bar and phases
    // ------------------------------------------------------------------

    private void tickBar(ServerLevel level) {
        bar.setProgress(Math.clamp(getHealth() / getMaxHealth(), 0, 1));
        if (combatTicks % 10 != 0) return;
        Set<ServerPlayer> near = new HashSet<>();
        for (ServerPlayer player : level.players()) if (player.distanceToSqr(this) < BAR_RANGE * BAR_RANGE && !player.isSpectator()) near.add(player);
        for (ServerPlayer player : List.copyOf(bar.getPlayers())) if (!near.contains(player)) bar.removePlayer(player);
        for (ServerPlayer player : near) bar.addPlayer(player);
    }

    private void tickPhase(ServerLevel level, ServerPlayer victim, SylarStoryData story) {
        float fraction = getHealth() / getMaxHealth();
        int next = fraction > 0.5F ? 1 : fraction > 0.2F ? 2 : 3;
        if (next <= phase) return;
        phase = next;
        bar.setName(Component.translatable("story.heroesevolved.sylar.bar." + phase));
        bar.setColor(phase == 2 ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.WHITE);
        cancelCast();
        // A telekinetic shove clears the space around him as the fight changes.
        AbilityEffects.send(level, Kind.TK_WAVE, position().add(0, 1, 0), position().add(0, 2, 0), 3.0);
        for (LivingEntity near : SylarPowers.foes(this, position(), 5))
            SylarPowers.push(near, SylarPowers.flat(near.position().subtract(position())).scale(1.1).add(0, 0.4, 0));
        SylarPowers.sound(level, position(), SoundEvents.WARDEN_ROAR, 0.6F, 1.6F);
        for (ServerPlayer witness : bar.getPlayers()) SylarVoice.say(witness, "story.heroesevolved.sylar.say.phase" + phase);
        if (phase == 3 && !escapeTried && canEscape(story)) {
            escapeTried = true;
            escapeIn = 80;
            for (ServerPlayer witness : bar.getPlayers()) SylarVoice.warn(witness, "story.heroesevolved.sylar.warn.escape");
        }
    }

    private boolean canEscape(SylarStoryData story) {
        for (ResourceLocation id : List.of(ModAbilities.INVISIBILITY_ID, ModAbilities.PHASING_ID, ModAbilities.SUPERSPEED_ID,
                ModAbilities.TIME_MANIPULATION_ID, ModAbilities.FLIGHT_ID))
            if (story.knows(id)) return true;
        return false;
    }

    private void escape(ServerLevel level, ServerPlayer victim, SylarStoryData story) {
        AbilityEffects.send(level, Kind.MIND_PULSE, position(), getEyePosition(), 2);
        AbilityEffects.send(level, Kind.SPEED_BOOM, position().add(0, 1, 0), position().add(0, 1, 0).add(getLookAngle().reverse()), 1.5);
        SylarPowers.sound(level, position(), SoundEvents.ENDERMAN_TELEPORT, 1.0F, 0.5F);
        SylarStoryService.fled(this, victim);
    }

    // ------------------------------------------------------------------
    // Regeneration's second life
    // ------------------------------------------------------------------

    private void tickDowned(ServerLevel level, ServerPlayer victim) {
        getNavigation().stop(); setTarget(null);
        if (--downedTicks > 0) {
            if (downedTicks % 10 == 0) AbilityEffects.send(level, Kind.REGEN_MEND, position(), getEyePosition(), 1.0);
            return;
        }
        setHealth(getMaxHealth() * 0.4F);
        entityData.set(POSE, 0);
        AbilityEffects.send(level, Kind.REGEN_REVIVE, position(), position().add(0, 1, 0), getBbHeight());
        SylarPowers.sound(level, position(), SoundEvents.WARDEN_HEARTBEAT, 1.5F, 0.8F);
        for (ServerPlayer witness : bar.getPlayers()) SylarVoice.say(witness, "story.heroesevolved.sylar.say.revive");
    }

    // ------------------------------------------------------------------
    // Taking damage
    // ------------------------------------------------------------------

    private int nextPredictedDodge;
    @Override public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) return super.hurt(source, amount);
        if (downedTicks > 0) return false;
        var story = SylarStoryData.get(level().getServer());
        boolean precog = story.knows(ModAbilities.PRECOGNITION_ID) || ModAbilities.PRECOGNITION_ID.equals(borrowed);
        if (tickCount >= nextPredictedDodge && source.getDirectEntity() instanceof Projectile && precog) {
            Vec3 facing = getLookAngle().multiply(1, 0, 1).normalize(), side = new Vec3(-facing.z, 0, facing.x);
            Vec3 end = com.jayptucker.heroesevolved.speed.SpeedService.swept(this, side.scale(2));
            if (end.distanceToSqr(position()) >= 2.25) {
                nextPredictedDodge = tickCount + 80;
                AbilityEffects.send((ServerLevel) level(), Kind.PROPHECY_BREAK, position(), end, 1.5);
                teleportTo(end.x, end.y, end.z); return false;
            }
        }
        // He saw that blow coming, too (but not twice in four seconds).
        if (hunting && precog && tickCount >= nextSidestep && source.getEntity() instanceof LivingEntity attacker
                && !(source.getDirectEntity() instanceof Projectile) && random.nextFloat() < 0.25F) {
            Vec3 away = SylarPowers.flat(position().subtract(attacker.position())), side = new Vec3(-away.z, 0, away.x);
            Vec3 end = com.jayptucker.heroesevolved.speed.SpeedService.swept(this, side.scale(random.nextBoolean() ? 1.8 : -1.8));
            if (end.distanceToSqr(position()) >= 1) {
                nextSidestep = tickCount + 80;
                AbilityEffects.send((ServerLevel) level(), Kind.PRECOG_GHOST, position(), position(), getBbHeight());
                teleportTo(end.x, end.y, end.z);
                return false;
            }
        }
        // Claire's gift: the first time he should die in a fight, he gets back up instead.
        boolean regenerates = story.knows(ModAbilities.REGENERATION_ID) || ModAbilities.REGENERATION_ID.equals(borrowed);
        if (hunting && regenerates && !revived && amount >= getHealth() + getAbsorptionAmount()
                && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            revived = true;
            setHealth(1);
            downedTicks = 100;
            cancelCast(); extractionTicks = 0; entityData.set(EXTRACTING, false); pinned = null;
            entityData.set(POSE, SylarPowers.Pose.CROUCH.ordinal());
            SylarPowers.sound((ServerLevel) level(), position(), SoundEvents.PLAYER_DEATH, 1.0F, 0.8F);
            for (ServerPlayer witness : bar.getPlayers()) SylarVoice.narrate(witness, "story.heroesevolved.sylar.narrate.down");
            return true;
        }
        boolean hit = super.hurt(source, amount);
        if (hit && amount > 0) {
            // A hard enough hit breaks his concentration on whatever he was winding up.
            staggerDamageTaken += (int) amount;
            if (extractionTicks > 0 || pinning() || casting != null && staggerDamageTaken >= 5) interrupt();
            SylarStoryService.provoked(this);
        }
        return hit;
    }

    @Override public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide && !isAlive()) SylarStoryService.defeated(this, source.getEntity());
    }
    @Override public void remove(RemovalReason reason) { bar.removeAllPlayers(); super.remove(reason); }
    @Override public boolean shouldBeSaved() { return false; }
    // A human-sized mob normally fades out near the distance used for our sightings.
    @Override public boolean shouldRenderAtSqrDistance(double distance) { return distance < 128 * 128; }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override protected boolean shouldDespawnInPeaceful() { return false; }
    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); }
    @Override public Component getName() { return Component.translatable("entity.heroesevolved.sylar"); }

    /** For tests: start a move by id right now. */
    public boolean castNow(String id) {
        if (!(level() instanceof ServerLevel level) || quarry == null) return false;
        ServerPlayer victim = level.getServer().getPlayerList().getPlayer(quarry);
        LivingEntity target = victim != null ? victim : getTarget();
        if (target == null) return false;
        for (SylarPowers.Move move : SylarPowers.MOVES) if (move.id().equals(id)) {
            casting = move; castTicks = 0; staggerDamageTaken = 0;
            entityData.set(POSE, move.pose().ordinal());
            move.telegraph().run(this, level, target, power());
            return true;
        }
        return false;
    }

}
