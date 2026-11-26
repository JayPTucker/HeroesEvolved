package com.jayptucker.heroesevolved.wither;

import com.jayptucker.heroesevolved.combat.BossLocks;
import com.jayptucker.heroesevolved.combat.Takedowns;
import com.jayptucker.heroesevolved.registry.ModItems;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.wither.WitherGeometry.Move;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/**
 * The cage is an actor at the summoning site and a three-phase boss inside its breach. It hovers low over the Ossuary's
 * floor, turns with weight, and fights in chains: a few linked moves fitted to where its focus stands, then a slump with
 * its chest open. Heavy hits during windups break its stance; a charge into stone stuns it.
 *
 * <p>Each phase is built from locks that powers open ({@link BossLocks}, {@link WitherGeometry#locks}): the Star Ward fed
 * by three Hunger Hearts, and its skulls to send back; the brood that carries bone back to mend it, the hearts that feed
 * its heavy moves and backfire when broken, and the Hunger Mark; the discharge, the exposed star, and the hearts again.
 */
public final class RiftWitherEntity extends Monster implements Takedowns.PhaseFinishable, BossLocks.Party {
    public static final double BASE_HEALTH = 720, PARTY_STEP = .5, PARTY_MAX = 3;
    public static final float HIT_CAP = .05F, SECOND_CAP = .025F, BACKFIRE = .1F;
    public static final int STATE_NONE = 0, STATE_SPENT = 1, STATE_STAGGER = 2, STATE_CRASH = 3, STATE_ROAR = 4, STATE_ARRIVE = 5;
    public static final int TRANSITION_TICKS = WitherGeometry.TRANSITION_TICKS;
    private static final EntityDataAccessor<Integer> MODE = data(EntityDataSerializers.INT), AGE = data(EntityDataSerializers.INT),
            PHASE = data(EntityDataSerializers.INT), ANCHOR = data(EntityDataSerializers.INT), BURNT = data(EntityDataSerializers.INT),
            ACTION = data(EntityDataSerializers.INT), STANCE = data(EntityDataSerializers.INT), STATE = data(EntityDataSerializers.INT),
            SIDE_HEAD = data(EntityDataSerializers.INT), ACTION_WINDUP = data(EntityDataSerializers.INT),
            /** Per heart five bits (state, then health or mend progress in sevenths), the ward at bit 15, mending at 16 to 18. */
            HEARTS = data(EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> FINISH = data(EntityDataSerializers.BOOLEAN);
    /** The breach it is passing through (the summoning site's, or the arrival tear), and that breach's facing; yaw 999 when none. */
    private static final EntityDataAccessor<org.joml.Vector3f> GATE = data(EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Float> GATE_YAW = data(EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> AIM_X = data(EntityDataSerializers.FLOAT), AIM_Y = data(EntityDataSerializers.FLOAT), AIM_Z = data(EntityDataSerializers.FLOAT),
            SIDE_X = data(EntityDataSerializers.FLOAT), SIDE_Y = data(EntityDataSerializers.FLOAT), SIDE_Z = data(EntityDataSerializers.FLOAT);
    private static <T> EntityDataAccessor<T> data(net.minecraft.network.syncher.EntityDataSerializer<T> type) { return SynchedEntityData.defineId(RiftWitherEntity.class, type); }

    private final ServerBossEvent bar = new ServerBossEvent(Component.translatable("entity.heroesevolved.rift_wither"), BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.NOTCHED_10);
    private final Map<UUID, ServerPlayer> party = new LinkedHashMap<>();
    private final Deque<float[]> recent = new ArrayDeque<>();
    // Server fight state. Package-private so WitherMoves, tests and previews can stage it.
    final List<Vec3> marks = new ArrayList<>(), origins = new ArrayList<>();
    final List<RiftSkull> skulls = new ArrayList<>();
    final Deque<Move> chain = new ArrayDeque<>();
    final Set<UUID> motionHit = new HashSet<>();
    final List<Mob> minions = new ArrayList<>();
    final List<WitherMoves.Ripple> ripples = new ArrayList<>();
    final List<WitherMoves.Spire> spires = new ArrayList<>();
    final List<WitherMoves.Spew> spews = new ArrayList<>();
    final Set<UUID> beamHit = new HashSet<>();
    final List<Vec3[]> beams = new ArrayList<>();
    /** The Bone Cage standing on the floor (null when none), and the Starving Wells drifting after fighters. */
    WitherMoves.Cage cage;
    final List<WitherMoves.Well> wells = new ArrayList<>();
    /** Whom the Starving Well being wound up will drift after. */
    UUID wellTarget;
    /** The Hunger Hearts in the three pylons (null where none floats), when each broken one re-forms, and the ward. */
    final HungerHeart[] hearts = new HungerHeart[3];
    final int[] mendAt = {-1, -1, -1};
    boolean ward = true, heartsFormed;
    /** How long each marked fighter has been out of its sight, and when its star was last struck. */
    final Map<UUID, Integer> unseen = new HashMap<>();
    final Deque<Integer> starHits = new ArrayDeque<>();
    int lastWardFlash = -100;
    Move casting, motion;
    int windup, rest = 50, transition, stagger, spent, crash, rotation, beamTicks, arrival;
    int clock, lastHurt = -1000, lastInterrupt = -1000, lastStance = -1000, recovery, feedTicks, motionTicks, motionStep, attacksLanded;
    int nextHeadShot, nextBrood = 200, actionTicks, serial, variant, casts, lungeStreak;
    /** A move's landing asks for its follow-up at once (a tethered fighter bitten; a missed charge turning back). */
    boolean quickFollow;
    float lockedYaw, effectiveHealth = (float) BASE_HEALTH;
    Vec3 motionFrom = Vec3.ZERO, motionTo = Vec3.ZERO, velocity = Vec3.ZERO, castFrom = Vec3.ZERO, sideOrigin = Vec3.ZERO, sideTarget = Vec3.ZERO;
    boolean bypass, still, lungeCrash;
    private Vec3 introFrom = Vec3.ZERO;
    private UUID focus;
    private DamageSource killingBlow;
    private UUID killer;
    // Client animation clocks: the tick each synced change arrived, so poses run on the client's own frame time.
    private int actionStamp, stateStamp, sideStamp, deathStamp;
    private float lean, leanO, bank, bankO;
    private Vec3 watch, watchO;

    public RiftWitherEntity(EntityType<? extends RiftWitherEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setNoGravity(true);
        xpReward = 120;
    }
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, BASE_HEALTH).add(Attributes.MOVEMENT_SPEED, .26)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1).add(Attributes.ARMOR, 6).add(Attributes.ARMOR_TOUGHNESS, 2).add(Attributes.FOLLOW_RANGE, 64);
    }
    @Override protected void registerGoals() {}
    @Override protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(MODE, 0); b.define(AGE, 0); b.define(PHASE, 1); b.define(ANCHOR, -1); b.define(BURNT, 0); b.define(FINISH, false);
        b.define(ACTION, -1); b.define(STANCE, 0); b.define(STATE, 0); b.define(SIDE_HEAD, -1); b.define(ACTION_WINDUP, 20);
        b.define(AIM_X, 0F); b.define(AIM_Y, 0F); b.define(AIM_Z, 0F); b.define(SIDE_X, 0F); b.define(SIDE_Y, 0F); b.define(SIDE_Z, 0F);
        b.define(GATE, new org.joml.Vector3f()); b.define(GATE_YAW, 999F); b.define(HEARTS, 1 << 15);
    }

    // ---- What everyone can read ----
    public int mode() { return entityData.get(MODE); }
    public int age() { return entityData.get(AGE); }
    public int phase() { return entityData.get(PHASE); }
    public int anchor() { return entityData.get(ANCHOR); }
    public int burnt() { return entityData.get(BURNT); }
    public boolean fighting() { return mode() == 2; }
    public boolean dying() { return mode() == 3; }
    public UUID killer() { return killer; }
    public Move action() { int id = entityData.get(ACTION); return id < 0 ? null : Move.values()[(id & 31) % Move.values().length]; }
    public int actionVariant() { int id = entityData.get(ACTION); return id < 0 ? 0 : (id >> 5) & 7; }
    /** This cast's windup (feints and later phases change it): the pose and the warning both run on it. */
    public int actionWindup() { return entityData.get(ACTION_WINDUP); }
    /** Its last fifteen percent: faster still. */
    public boolean starving() { return fighting() && getHealth() / getMaxHealth() < WitherGeometry.STARVING; }
    /** Ticks since the current action began: the server's count, or the client's own clock with the frame's fraction. */
    public float actionAge(float partial) { return level().isClientSide ? tickCount - actionStamp + partial : actionTicks; }
    public int actionAge() { return (int) actionAge(0); }
    public int state() { return entityData.get(STATE); }
    public float stateAge(float partial) { return tickCount - stateStamp + partial; }
    public int stance() { return entityData.get(STANCE); }
    public boolean exposed() {
        if (!level().isClientSide) return crash > 0 || stagger > 0 || spent > 0 && casting == null && motionTicks == 0 && recovery == 0;
        int s = state(); return s == STATE_SPENT || s == STATE_STAGGER || s == STATE_CRASH;
    }
    public Vec3 aim() { return new Vec3(entityData.get(AIM_X), entityData.get(AIM_Y), entityData.get(AIM_Z)); }
    public int sideHead() { int id = entityData.get(SIDE_HEAD); return id < 0 ? -1 : id & 3; }
    public float sideAge(float partial) { return level().isClientSide ? tickCount - sideStamp + partial : sideTicks; }
    public int sideAge() { return (int) sideAge(0); }
    public Vec3 sideAim() { return new Vec3(entityData.get(SIDE_X), entityData.get(SIDE_Y), entityData.get(SIDE_Z)); }
    public float deathAge(float partial) { return level().isClientSide ? tickCount - deathStamp + partial : age(); }
    /** The breach it is passing through, or null. */
    public Vec3 gate() { return entityData.get(GATE_YAW) > 900 ? null : new Vec3(entityData.get(GATE)); }
    /** The side of that breach it came from (or is going to): the breach's normal. */
    public Vec3 gateNormal() { return WitherGeometry.forward(entityData.get(GATE_YAW)); }
    /** How far behind its breach's plane the body is: above zero, it is being swallowed (or not yet out). */
    public double gateDepth(float partial) {
        Vec3 gate = gate();
        return gate == null ? -1 : -getPosition(partial).subtract(gate).dot(gateNormal());
    }
    public boolean arriving() { return arrival > 0 || state() == STATE_ARRIVE; }
    public Vec3 core() { return WitherGeometry.core(position()); }
    public float modelScale() { return mode() == 1 ? WitherGeometry.INTRO_SCALE : WitherGeometry.ARENA_SCALE; }
    public float leanForward(float partial) { return Mth.lerp(partial, leanO, lean); }
    public float leanSide(float partial) { return Mth.lerp(partial, bankO, bank); }
    /** Where the idle heads look: the nearest player, smoothed. */
    public Vec3 watchPoint(float partial) { return watch == null ? null : watchO == null ? watch : watchO.lerp(watch, partial); }
    public ServerPlayer focus() {
        var people = fighters();
        return people.stream().filter(p -> p.getUUID().equals(focus)).findFirst().orElse(people.isEmpty() ? null : people.getFirst());
    }
    public int attacksLanded() { return attacksLanded; }
    /** The Star Ward stands over its ribs. */
    public boolean warded() { return (entityData.get(HEARTS) & (1 << 15)) != 0; }
    /** Heart {@code i}'s state ({@link HungerHeart#WARDING} and so on; 0 where none floats). */
    public int heartState(int i) { return (entityData.get(HEARTS) >> (i * 5)) & 3; }
    /** Heart {@code i}'s health from 0 to 1, or how far a broken one has re-formed. */
    public float heartLevel(int i) { return ((entityData.get(HEARTS) >> (i * 5 + 2)) & 7) / 7F; }
    public boolean heartMending(int i) { return (entityData.get(HEARTS) >> (16 + i) & 1) != 0; }
    /** How many hearts still feed the ward (server). */
    public int wardHearts() {
        int n = 0;
        for (HungerHeart h : hearts) if (h != null && !h.isRemoved() && h.state() == HungerHeart.WARDING) n++;
        return n;
    }
    /** Whether it may spew its brood now: off cooldown, and room on the floor for more. */
    public boolean broodReady() {
        return clock >= nextBrood && minions.size() + spews.size() < WitherGeometry.broodCap(Math.max(1, fighters().size()));
    }
    public void still(boolean value) { still = value; }
    public void holdOff(int ticks) { rest = Math.max(rest, ticks); }
    /** At the summoning site: formed facing {@code yaw}, with its breach about to open at {@code gate} behind it. */
    public void beginIntro(Vec3 gate, float yaw) {
        entityData.set(MODE, 1); entityData.set(AGE, 0);
        entityData.set(GATE, gate.toVector3f()); entityData.set(GATE_YAW, yaw);
        introFrom = position();
    }
    /** In the Ossuary: it falls out of a tear above the dais, lands and roars before the first chain. */
    public void beginFight() {
        entityData.set(MODE, 2); refreshDimensions(); setHealth(getMaxHealth()); rest = 30; bar.setProgress(1);
        arrival = WitherGeometry.ARRIVAL_TICKS;
        entityData.set(STATE, STATE_ARRIVE); entityData.set(AGE, 0);
        entityData.set(GATE, WitherGeometry.arrivalGate().toVector3f()); entityData.set(GATE_YAW, 180F);
        setPos(WitherGeometry.arrivalPath(0));
        setYRot(180); setYHeadRot(180); yBodyRot = 180;
    }
    /** Tests and staged previews start on the dais at once. */
    public void skipArrival() {
        if (arrival <= 0) return;
        arrival = 0; entityData.set(GATE_YAW, 999F); entityData.set(STATE, STATE_NONE);
        setPos(WitherGeometry.floor().add(0, .35, 0));
    }

    private int sideTicks;

    @Override public EntityDimensions getDefaultDimensions(Pose pose) {
        return mode() == 1 ? EntityDimensions.scalable(2.6F, 4.6F) : EntityDimensions.scalable(WitherGeometry.WIDTH, WitherGeometry.HEIGHT);
    }
    @Override public AABB getBoundingBoxForCulling() { return getBoundingBox().inflate(5, 3, 5); }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (key.equals(MODE)) { refreshDimensions(); if (mode() == 3) deathStamp = tickCount; }
        if (key.equals(ACTION)) actionStamp = tickCount;
        if (key.equals(STATE)) stateStamp = tickCount;
        if (key.equals(SIDE_HEAD)) sideStamp = tickCount;
    }
    @Override public boolean isPushable() { return false; }
    @Override protected void pushEntities() {}
    @Override public boolean canBeCollidedWith() { return false; }
    @Override protected float tickHeadTurn(float yRot, float step) { yBodyRot = getYRot(); return step; }
    @Override protected SoundEvent getAmbientSound() { return null; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.WITHER_HURT; }
    @Override protected SoundEvent getDeathSound() { return null; }
    @Override public float getVoicePitch() { return .55F; }
    @Override protected void tickDeath() { if (!level().isClientSide && ++deathTime >= 2) remove(RemovalReason.KILLED); }

    public void join(ServerPlayer p) {
        if (party.put(p.getUUID(), p) == null) {
            float fraction = getHealth() / getMaxHealth();
            double maximum = BASE_HEALTH * Math.min(PARTY_MAX, 1 + PARTY_STEP * (party.size() - 1));
            effectiveHealth = (float) maximum;
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(Math.min(1000, maximum));
            setHealth(getMaxHealth() * fraction);
        }
        bar.addPlayer(p);
        bar.setProgress(getHealth() / getMaxHealth());
    }
    /**
     * Everyone in the Ossuary the blast and the death's pull reach: the level's players and the fight's own party, which
     * holds game-test and other mods' fake players the level's list does not.
     */
    @Override public List<ServerPlayer> everyone() {
        Set<ServerPlayer> all = new LinkedHashSet<>();
        for (ServerPlayer p : ((ServerLevel) level()).players()) if (!p.isSpectator() && p.isAlive()) all.add(p);
        for (ServerPlayer p : party.values()) if (p.isAlive() && !p.isRemoved() && p.level() == level() && !p.isSpectator()) all.add(p);
        return List.copyOf(all);
    }
    public List<ServerPlayer> fighters() {
        return party.values().stream().filter(p -> p.isAlive() && !p.isRemoved() && p.level() == level() && !p.isSpectator() && !WitherArena.sheltered(p)).toList();
    }

    @Override public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        fallDistance = 0;
        if (level().isClientSide) { clientTick(); return; }
        ServerLevel level = (ServerLevel) level();
        clock++;
        actionTicks++;
        if (mode() == 1) { introTick(level); return; }
        if (dying()) {
            entityData.set(AGE, age() + 1);
            WitherMoves.deathTick(this, age());
            if (age() >= WitherGeometry.DEATH_TICKS) { setHealth(0); die(killingBlow == null ? damageSources().generic() : killingBlow); }
            return;
        }
        if (!fighting()) return;
        tendHearts();
        if (arrival > 0) {
            int t = WitherGeometry.ARRIVAL_TICKS - arrival;
            entityData.set(AGE, t);
            WitherMoves.arrivalTick(this, t);
            if (t == WitherGeometry.ARRIVAL_SHUT) entityData.set(GATE_YAW, 999F);
            if (--arrival == 0) { entityData.set(STATE, STATE_NONE); entityData.set(GATE_YAW, 999F); }
            bar.setProgress(1);
            return;
        }
        if (sideHead() >= 0) {
            sideTicks++;
            if (sideTicks == WitherGeometry.HEAD_WINDUP) WitherMoves.sideShot(this, sideOrigin, sideTarget);
            if (sideTicks > 30) entityData.set(SIDE_HEAD, -1);
        }
        syncState();
        if (clock % 10 == 0) SetPiecePayload.sendSeeded(level, Piece.WITHER_AURA, position(), WitherGeometry.floor(), 1, this, 0,
                phase() | (burnt() << 2) | ((anchor() + 1) << 5));
        bar.setProgress(Math.clamp(getHealth() / getMaxHealth(), 0, 1));
        entityData.set(FINISH, window());
        if (feedTicks > 0 && --feedTicks == 0) {
            int lit = anchor();
            entityData.set(ANCHOR, -1);
            if (lit >= 0 && hearts[lit] != null) hearts[lit].state(HungerHeart.DORMANT);
        }
        WitherMoves.tickHazards(this);
        tendMarks(level);
        skulls.removeIf(Entity::isRemoved);
        minions.removeIf(m -> !m.isAlive());
        if (casting == null && motionTicks == 0 && recovery == 0 && action() != null && actionTicks > (actionWindup() + 30)) entityData.set(ACTION, -1);
        if (fighters().isEmpty()) { idle(level); return; }
        if (outside()) { setPos(WitherGeometry.floor().add(0, .3, 0)); velocity = Vec3.ZERO; cancelChain(); }
        if (transition > 0) { WitherMoves.roarTick(this, TRANSITION_TICKS - transition); transition--; return; }
        if (crash > 0) {
            // Thrown down: it falls to the floor and lies there with its chest open.
            crash--;
            double above = getY() - (WitherGeometry.floor().y + .35);
            if (above > .05) move(new Vec3(0, -Math.min(.9, above), 0));
            return;
        }
        if (ward) guardHearts();
        if (stagger > 0) { stagger--; return; }
        if (stance() > 0 && clock - lastStance > 45 && clock % 20 == 0) entityData.set(STANCE, stance() - 1);
        if (motionTicks > 0) { WitherMoves.tickMotion(this); return; }
        if (casting != null) {
            turn(lockedYaw, 16);
            WitherMoves.windup(this, casting);
            if (--windup <= 0) {
                Move move = casting;
                casting = null;
                WitherMoves.land(this, move);
                attacksLanded++;
                recovery = WitherGeometry.hold(move);
                rest = Math.max(rest, Math.round(move.rest * WitherGeometry.restScale(phase()) * (starving() ? .6F : 1)));
                if (quickFollow) { quickFollow = false; rest = 0; recovery = Math.min(recovery, 4); }
                else if (chain.isEmpty()) spent = Math.max(spent, Math.round(WitherGeometry.spentTicks(phase()) * (starving() ? .7F : 1)));
            }
            return;
        }
        if (recovery > 0) { recovery--; return; }
        if (spent > 0) { spent--; rest = Math.max(rest, 4); }
        if (!still) advance(level);
        if (still || spent > 0 || --rest > 0) return;
        if (chain.isEmpty()) {
            var people = fighters();
            ServerPlayer foe = people.get(Math.floorMod(rotation, people.size()));
            focus = foe.getUUID();
            boolean high = foe.getY() > WitherGeometry.floor().y + 6 && foe.getY() > getY() + 4;
            chain.addAll(List.of(WitherGeometry.chain(phase(), rotation++, horizontal(foe), high)));
            if (atItsBack() && chain.peekFirst() != Move.TAIL) chain.addFirst(Move.TAIL);
        }
        Move next = chain.removeFirst();
        // The brood waits its turn, and never floods the floor: skip it for the chain's next move.
        if (next == Move.BROOD && !broodReady()) {
            if (chain.isEmpty()) { rest = Math.max(rest, 6); return; }
            next = chain.removeFirst();
        }
        start(next);
    }
    /**
     * The summoning site: it knits together and roars at whoever built it; a seam splits the air behind it and tears
     * wide; the breach drags at it while it digs in; then it is yanked backwards through, and the breach convulses shut
     * to the size of a door.
     */
    private void introTick(ServerLevel level) {
        int age = age() + 1;
        entityData.set(AGE, age);
        Vec3 gate = gate(), back = gateNormal().scale(-1);
        if (gate == null) gate = position().add(back.scale(4)).add(0, WitherGeometry.GATE_LIFT, 0);
        Vec3 ground = new Vec3(gate.x, introFrom.y, gate.z);
        if (age == 1) {
            SetPiecePayload.send(level, Piece.WITHER_FORM, position(), gate, 1, this);
            level.playSound(null, blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1F, .55F);
        }
        if (age == WitherGeometry.FORM_END + 2) {
            level.playSound(null, getX(), getY() + 3, getZ(), SoundEvents.WITHER_AMBIENT, SoundSource.HOSTILE, 3F, .5F);
            CameraShakePayload.sendAround(level, position(), 32, .3F, 24);
        }
        if (age == WitherGeometry.SEAM) level.playSound(null, gate.x, gate.y, gate.z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.HOSTILE, 3F, .5F);
        if (age == WitherGeometry.SPLIT) {
            level.playSound(null, gate.x, gate.y, gate.z, SoundEvents.PORTAL_TRIGGER, SoundSource.HOSTILE, 2F, .45F);
            level.playSound(null, gate.x, gate.y, gate.z, SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 2F, .4F);
            CameraShakePayload.sendAround(level, gate, 40, .4F, 20);
        }
        if (age > WitherGeometry.PULL && age <= WitherGeometry.YANK) {
            // It digs in against the pull: dragged back in jerks, claws scraping.
            float k = WitherGeometry.smooth(WitherGeometry.PULL, WitherGeometry.YANK, age);
            setPos(introFrom.add(back.scale(.9 * k + .12 * Math.sin(age * 1.7) * k)));
            if (age % 6 == 0) level.playSound(null, getX(), getY(), getZ(), SoundEvents.BONE_BLOCK_STEP, SoundSource.HOSTILE, 2F, .5F);
        }
        if (age == WitherGeometry.YANK) {
            level.playSound(null, getX(), getY() + 3, getZ(), SoundEvents.WITHER_HURT, SoundSource.HOSTILE, 3F, .45F);
            introFrom = position();
        }
        if (age > WitherGeometry.YANK && age <= WitherGeometry.THROUGH) {
            float k = (age - WitherGeometry.YANK) / (float) (WitherGeometry.THROUGH - WitherGeometry.YANK);
            Vec3 end = ground.add(back.scale(WitherGeometry.SWALLOW_DEPTH));
            setPos(introFrom.lerp(end, k * k));
        }
        if (age == WitherGeometry.THROUGH) {
            level.playSound(null, gate.x, gate.y, gate.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 2.5F, .4F);
            level.playSound(null, gate.x, gate.y, gate.z, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.HOSTILE, 2.5F, .6F);
            CameraShakePayload.sendAround(level, gate, 40, .5F, 24);
        }
        if (age >= WitherGeometry.INTRO_TICKS) WitherArena.swallowed(this);
    }
    /** Nobody to fight: it drifts home over the dais and mends after ten quiet seconds. */
    private void idle(ServerLevel level) {
        Vec3 home = WitherGeometry.floor().add(0, .3, 0), to = home.subtract(position());
        if (to.length() > .5) move(to.normalize().scale(Math.min(.25, to.length())));
        if (clock - lastHurt > 200 && clock % 20 == 0) heal(getMaxHealth() * .05F);
        if (casting != null || motionTicks > 0) cancelChain();
    }
    private boolean outside() {
        Vec3 d = position().subtract(WitherGeometry.floor());
        return d.horizontalDistanceSqr() > (WitherGeometry.RADIUS + 2) * (WitherGeometry.RADIUS + 2) || d.y < -2 || d.y > WitherGeometry.FLIGHT_HEIGHT - 8;
    }
    private double horizontal(Entity e) { return Math.sqrt(e.position().subtract(position()).horizontalDistanceSqr()); }
    /** Breach Step: the body starts behind the far tear's plane (drawn shrinking into it) and comes out over its ticks. */
    void step(Vec3 from, Vec3 to, float face) {
        motion = Move.STEP; motionTicks = WitherGeometry.STEP_TICKS; motionStep = 0; motionFrom = from; motionTo = to; motionHit.clear();
        setPos(from); setYRot(face); setYHeadRot(face); yBodyRot = face; lockedYaw = face; velocity = Vec3.ZERO;
        entityData.set(GATE, to.add(0, WitherGeometry.HEIGHT / 2, 0).toVector3f()); entityData.set(GATE_YAW, face);
    }
    /** Out of the tear: it closes behind it. */
    void stepped() { entityData.set(GATE_YAW, 999F); }
    /** Someone is hugging its back, low and close: its tail answers that. */
    boolean atItsBack() {
        for (ServerPlayer p : fighters()) {
            double d = horizontal(p);
            if (d < WitherGeometry.TAIL_REACH - 1 && p.getY() < WitherGeometry.floor().y + 3
                    && WitherGeometry.angle(position(), p.position(), getYRot()) > Math.toRadians(110)) return true;
        }
        return false;
    }
    private void syncState() {
        int s = arrival > 0 ? STATE_ARRIVE : transition > 0 ? STATE_ROAR : crash > 0 ? STATE_CRASH : stagger > 0 ? STATE_STAGGER
                : spent > 0 && casting == null && motionTicks == 0 && recovery == 0 ? STATE_SPENT : STATE_NONE;
        if (s != state()) entityData.set(STATE, s);
    }

    /** Hovers toward a striking distance from its focus, circling as it comes; rises to meet fliers. */
    private void advance(ServerLevel level) {
        ServerPlayer foe = focus();
        if (foe == null) return;
        Vec3 to = foe.position().subtract(position()).multiply(1, 0, 1);
        double distance = to.length();
        Vec3 dir = distance < 1e-3 ? Vec3.directionFromRotation(0, getYRot()) : to.scale(1 / distance);
        double preferred = phase() == 1 ? 6 : phase() == 2 ? 5 : 4.5;
        Vec3 side = new Vec3(-dir.z, 0, dir.x).scale(rotation % 2 == 0 ? 1 : -1);
        Vec3 wanted = Vec3.ZERO;
        if (distance > preferred + 1) wanted = dir.scale(Math.min(distance - preferred, 1)).add(side.scale(.35));
        else if (distance < preferred - 2.5) wanted = dir.scale(-.5).add(side.scale(.5));
        else wanted = side.scale(.6);
        double speed = (distance > 14 ? .78 : .44) + phase() * .06 + (starving() ? .08 : 0);
        if (spent > 0) speed *= .25;
        speed *= Math.clamp(getAttributeValue(Attributes.MOVEMENT_SPEED) / .26, .2, 1.6);
        wanted = wanted.lengthSqr() > 1e-4 ? wanted.normalize().scale(speed) : Vec3.ZERO;
        double hover = WitherGeometry.floor().y + .3;
        if (foe.getY() > WitherGeometry.floor().y + 5) hover = Math.clamp(foe.getY() - 3.5, hover, WitherGeometry.floor().y + 30);
        wanted = wanted.add(0, Math.clamp(hover - getY(), -.35, .35), 0);
        velocity = velocity.lerp(wanted, .14);
        move(velocity);
        float rate = phase() == 1 ? 9 : phase() == 2 ? 11 : 14;
        turn(yawTo(foe.position()), spent > 0 ? rate * .4F : rate);
    }
    /** Moves by {@code delta}, sliding along walls rather than sticking to them. */
    boolean move(Vec3 delta) {
        if (level().noCollision(this, getBoundingBox().move(delta))) { setPos(position().add(delta)); return true; }
        Vec3 x = new Vec3(delta.x, delta.y, 0), z = new Vec3(0, delta.y, delta.z);
        if (level().noCollision(this, getBoundingBox().move(x))) { setPos(position().add(x)); return false; }
        if (level().noCollision(this, getBoundingBox().move(z))) { setPos(position().add(z)); return false; }
        velocity = Vec3.ZERO;
        return false;
    }
    float yawTo(Vec3 at) { Vec3 d = at.subtract(position()); return (float) Math.toDegrees(Math.atan2(-d.x, d.z)); }
    void turn(float yaw, float rate) {
        float next = Mth.approachDegrees(getYRot(), yaw, rate);
        setYRot(next); setYHeadRot(next); yBodyRot = next;
    }

    private void clientTick() {
        leanO = lean; bankO = bank; watchO = watch;
        Vec3 moved = position().subtract(xo, yo, zo);
        double r = Math.toRadians(yBodyRot);
        Vec3 ahead = new Vec3(-Math.sin(r), 0, Math.cos(r)), right = new Vec3(-Math.cos(r), 0, -Math.sin(r));
        lean = Mth.lerp(.15F, lean, (float) Mth.clamp(moved.dot(ahead) * 2.2, -1, 1));
        bank = Mth.lerp(.15F, bank, (float) Mth.clamp(moved.dot(right) * 2.2, -1, 1));
        Player near = level().getNearestPlayer(this, 48);
        Vec3 want = near == null ? null : near.getEyePosition();
        watch = want == null ? watch : watch == null ? want : watch.lerp(want, .2);
    }

    void cancelChain() {
        if (motion == Move.STEP) { setPos(motionTo); stepped(); }
        casting = null; chain.clear(); motionTicks = 0; motion = null; recovery = 0; velocity = Vec3.ZERO; beamTicks = 0; beams.clear();
        entityData.set(ACTION, -1); entityData.set(SIDE_HEAD, -1);
        clearHazards();
        SetPiecePayload.send((ServerLevel) level(), Piece.WITHER_CANCEL, position(), position(), 1, this);
    }
    /** From the second phase a heavy move draws on the sleeping heart nearest its focus: it lights and can be broken. */
    private void feed() {
        if (phase() < 2 || anchor() >= 0 || burnt() == 7) return;
        ServerPlayer p = focus();
        if (p == null) return;
        int closest = -1;
        double distance = Double.MAX_VALUE;
        for (int i = 0; i < 3; i++) if ((burnt() & (1 << i)) == 0 && hearts[i] != null && hearts[i].state() == HungerHeart.DORMANT) {
            double d = p.position().distanceToSqr(WitherGeometry.heartCenter(i));
            if (d < distance) { distance = d; closest = i; }
        }
        if (closest < 0) return;
        hearts[closest].state(HungerHeart.FEEDING);
        entityData.set(ANCHOR, closest);
        feedTicks = WitherGeometry.FEED_TICKS;
    }
    public void start(Move move) {
        if (!fighting() || dying()) return;
        casting = move; windup = WitherGeometry.castWindup(move, phase(), casts++, starving()); marks.clear();
        lungeStreak = move == Move.LUNGE ? lungeStreak + 1 : 0;
        entityData.set(ACTION_WINDUP, windup); origins.clear(); motionTicks = 0; motion = null; recovery = 0; velocity = Vec3.ZERO;
        for (ServerPlayer p : fighters()) marks.add(p.position().add(0, .8, 0));
        ServerPlayer foe = focus();
        Vec3 aim = foe == null ? position().add(Vec3.directionFromRotation(0, getYRot()).scale(10)) : foe.position().add(0, .8, 0);
        lockedYaw = yawTo(aim);
        if (move == Move.RAKE) variant ^= 1;
        if (move == Move.BROOD) nextBrood = clock + WitherGeometry.broodCooldown(phase());
        entityData.set(AIM_X, (float) aim.x); entityData.set(AIM_Y, (float) aim.y); entityData.set(AIM_Z, (float) aim.z);
        serial = (serial + 1) & 255;
        entityData.set(ACTION, move.ordinal() | (variant << 5) | (serial << 8));
        actionTicks = 0;
        if (move.feeds()) feed();
        WitherMoves.warn(this, move);
        if (!still && clock >= nextHeadShot && (move == Move.RAIN || move == Move.TETHER || move == Move.PULSE || move == Move.SPIRES)) {
            var people = fighters();
            if (!people.isEmpty()) {
                ServerPlayer other = people.get(Math.floorMod(rotation + 1, people.size()));
                startSideShot(rotation % 2 + 1, other.position().add(0, .8, 0));
                nextHeadShot = clock + WitherGeometry.headShotGap(phase());
            }
        }
    }
    public void startSideShot(int head, Vec3 target) {
        if (!fighting() || head < 1 || head > 2) return;
        sideOrigin = WitherGeometry.mouth(position(), getYRot(), head, target);
        sideTarget = target;
        sideTicks = 0;
        serial = (serial + 1) & 255;
        entityData.set(SIDE_HEAD, head | (serial << 4));
        entityData.set(SIDE_X, (float) target.x); entityData.set(SIDE_Y, (float) target.y); entityData.set(SIDE_Z, (float) target.z);
        SetPiecePayload.send((ServerLevel) level(), Piece.WITHER_HEAD_WARN, sideOrigin, target, 1, this);
    }
    // ---------------------------------------------------------------- the hearts and the ward

    /** Forms, re-forms and syncs the hearts: three warding ones when the fight begins; a broken one re-forms while the ward stands. */
    private void tendHearts() {
        if (!heartsFormed) {
            heartsFormed = true;
            if (ward || phase() >= 2)
                for (int i = 0; i < 3; i++) hearts[i] = HungerHeart.form(this, i, ward ? HungerHeart.WARDING : HungerHeart.DORMANT, heartHealth());
        }
        for (int i = 0; i < 3; i++) {
            if (hearts[i] != null && hearts[i].isRemoved()) hearts[i] = null;
            if (hearts[i] == null && ward && mendAt[i] >= 0 && clock >= mendAt[i]) {
                mendAt[i] = -1;
                hearts[i] = HungerHeart.form(this, i, HungerHeart.WARDING, heartHealth());
                SetPiecePayload.sendSeeded((ServerLevel) level(), Piece.WITHER_HEART_BREAK, WitherGeometry.heartCenter(i), core(), 1, this, 0, 1 << 3);
                level().playSound(null, WitherGeometry.heartCenter(i).x, WitherGeometry.heartCenter(i).y, WitherGeometry.heartCenter(i).z,
                        SoundEvents.AMETHYST_CLUSTER_PLACE, SoundSource.HOSTILE, 2F, .5F);
            }
        }
        int bits = ward ? 1 << 15 : 0;
        for (int i = 0; i < 3; i++) {
            HungerHeart h = hearts[i];
            int state = h == null ? 0 : h.state();
            int level = state != 0 ? Math.round(7 * h.getHealth() / h.getMaxHealth())
                    : mendAt[i] >= 0 ? Math.round(7 * Math.clamp(1 - (mendAt[i] - clock) / (float) WitherGeometry.HEART_MEND, 0, 1)) : 0;
            bits |= (state | level << 2) << (i * 5);
            if (state == 0 && mendAt[i] >= 0) bits |= 1 << (16 + i);
        }
        entityData.set(HEARTS, bits);
    }
    private float heartHealth() { return (float) (WitherGeometry.HEART_HEALTH * Math.min(2, 1 + .5 * (Math.max(1, party.size()) - 1))); }
    private void reformHearts(int state) {
        for (int i = 0; i < 3; i++) {
            if (hearts[i] != null) hearts[i].discard();
            mendAt[i] = -1;
            hearts[i] = HungerHeart.form(this, i, state, heartHealth());
        }
        heartsFormed = true;
    }
    private void dropHearts(boolean burst) {
        for (int i = 0; i < 3; i++) {
            mendAt[i] = -1;
            if (hearts[i] == null) continue;
            if (burst) SetPiecePayload.sendSeeded((ServerLevel) level(), Piece.WITHER_HEART_BREAK, WitherGeometry.heartCenter(i), core(), 1, this, 0, hearts[i].state());
            hearts[i].discard();
            hearts[i] = null;
        }
    }
    /** Heart {@code index} was broken (by {@code by}, or by something nobody owns). */
    void heartBroken(int index, HungerHeart heart, ServerPlayer by) {
        if (hearts[index] != heart) return;
        hearts[index] = null;
        Vec3 at = WitherGeometry.heartCenter(index);
        SetPiecePayload.sendSeeded((ServerLevel) level(), Piece.WITHER_HEART_BREAK, at, core(), 1, this, 0, heart.state());
        level().playSound(null, at.x, at.y, at.z, SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 2F, .45F);
        level().playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.HOSTILE, 2F, .4F);
        CameraShakePayload.sendAround((ServerLevel) level(), at, 24, .25F, 10);
        if (heart.state() == HungerHeart.WARDING) {
            if (ward && wardHearts() == 0) breakWard(by);
            else mendAt[index] = clock + WitherGeometry.HEART_MEND;
        } else if (heart.state() == HungerHeart.FEEDING) backfire(index, by);
    }
    /** All three hearts are broken: the ward shatters off its ribs and it is thrown down, chest open. */
    void breakWard(ServerPlayer by) {
        if (!ward) return;
        ward = false;
        dropHearts(false);
        cancelChain();
        crash = WitherGeometry.WARD_CRASH; spent = 0; recovery = 0; stagger = 0; entityData.set(STANCE, 0);
        rest = Math.max(rest, 24);
        SetPiecePayload.send((ServerLevel) level(), Piece.WITHER_WARD_BREAK, core(), WitherGeometry.floor(), 1, this);
        level().playSound(null, getX(), getY() + 4, getZ(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 4F, .35F);
        level().playSound(null, getX(), getY() + 4, getZ(), SoundEvents.WITHER_HURT, SoundSource.HOSTILE, 3F, .4F);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 2F, .4F);
        CameraShakePayload.sendAround((ServerLevel) level(), position(), 64, .55F, 24);
        tell("wither.heroesevolved.ward_broken");
        tendHearts();
    }
    /** One line of the fight's story to everyone in it, in chat. */
    private void tell(String key) {
        for (ServerPlayer p : everyone()) p.sendSystemMessage(Component.translatable(key).withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.ITALIC));
    }
    /** Tests and previews: the fight without its ward, as if broken long ago. */
    public void unward() { ward = false; heartsFormed = true; dropHearts(false); tendHearts(); }
    /** While the ward stands, a side head turns on whoever goes for a heart. */
    private void guardHearts() {
        if (clock < nextHeadShot || sideHead() >= 0 || still) return;
        for (ServerPlayer p : fighters())
            for (HungerHeart h : hearts)
                if (h != null && p.position().distanceTo(h.position()) < 6) {
                    startSideShot(rotation % 2 + 1, p.position().add(0, .8, 0));
                    nextHeadShot = clock + WitherGeometry.headShotGap(phase());
                    return;
                }
    }
    /** A brood skeleton climbed in with bone: one crack in its stance mends. */
    void mendStance() { entityData.set(STANCE, Math.max(0, stance() - 1)); }
    /** Heals {@code amount}, never above the top of the phase it is in. */
    void mend(float amount) {
        float top = getMaxHealth() * WitherGeometry.phaseTop(phase());
        if (getHealth() < top) setHealth(Math.min(top, getHealth() + amount));
    }

    // ---------------------------------------------------------------- the Hunger Mark

    /** Its star drinks from every marked fighter it can see; out of its sight long enough, the thread snaps. */
    private void tendMarks(ServerLevel level) {
        for (ServerPlayer p : fighters()) {
            if (!p.hasEffect(ModItems.HUNGERING)) { unseen.remove(p.getUUID()); continue; }
            boolean seen = WitherMoves.seen(level, core(), p.getEyePosition());
            int out = seen ? 0 : unseen.getOrDefault(p.getUUID(), 0) + 1;
            unseen.put(p.getUUID(), out);
            if (out >= WitherGeometry.MARK_UNSEEN) {
                p.removeEffect(ModItems.HUNGERING);
                unseen.remove(p.getUUID());
                SetPiecePayload.sendSeeded(level, Piece.WITHER_MARK, p.position().add(0, 1.1, 0), core(), 1, p, 0, 1);
                level.playSound(null, p.getX(), p.getY() + 1, p.getZ(), SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE, 1.2F, .7F);
                continue;
            }
            if (clock % 10 == 0) SetPiecePayload.sendSeeded(level, Piece.WITHER_MARK, p.position().add(0, 1.1, 0), core(), 1, p, 0, seen ? 0 : 2);
            if (seen && clock % 20 == 0) {
                int guard = p.invulnerableTime;
                p.invulnerableTime = 0;
                if (p.hurt(damageSources().wither(), WitherGeometry.MARK_DRAIN)) mend(getMaxHealth() * WitherGeometry.MARK_FEED);
                p.invulnerableTime = Math.max(p.invulnerableTime, guard);
            }
        }
    }

    // ---------------------------------------------------------------- the star

    /** A blow on its star: a shot that lands at it, a blow from up at its height, or one aimed straight at it. */
    private boolean onStar(DamageSource source) {
        Vec3 core = core();
        Entity direct = source.getDirectEntity();
        if (direct != null && !(direct instanceof Player) && direct.position().distanceTo(core) < WitherGeometry.STAR_RADIUS + .6) return true;
        if (!(source.getEntity() instanceof ServerPlayer p)) return false;
        Vec3 eye = p.getEyePosition(), to = core.subtract(eye);
        if (to.length() < 6 && eye.y > core.y - 1.5) return true;
        return to.length() < 40 && to.lengthSqr() > .01 && Math.acos(Math.clamp(to.normalize().dot(p.getLookAngle()), -1, 1)) < Math.toRadians(4.5)
                && WitherMoves.seen((ServerLevel) level(), eye, core);
    }
    private void starHit() {
        starHits.add(clock);
        while (!starHits.isEmpty() && clock - starHits.peekFirst() > WitherGeometry.STAR_WINDOW) starHits.removeFirst();
        SetPiecePayload.sendSeeded((ServerLevel) level(), Piece.WITHER_CRACK, core(), core(), 1, this, 0, Math.min(3, starHits.size()));
        if (starHits.size() < WitherGeometry.STAR_CRACKS || stagger > 0 || crash > 0) return;
        starHits.clear();
        cancelChain();
        stagger = WitherGeometry.STAR_REEL; spent = 0; rest = Math.max(rest, 24);
        level().playSound(null, getX(), getY() + 5, getZ(), SoundEvents.WITHER_HURT, SoundSource.HOSTILE, 3F, .35F);
        level().playSound(null, getX(), getY() + 5, getZ(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.HOSTILE, 3F, .5F);
        CameraShakePayload.sendAround((ServerLevel) level(), position(), 40, .35F, 16);
    }

    public boolean backfire(int index, ServerPlayer player) {
        if (index != anchor() || !fighting() || (burnt() & (1 << index)) != 0) return false;
        entityData.set(BURNT, burnt() | 1 << index); entityData.set(ANCHOR, -1);
        feedTicks = 0;
        if (hearts[index] != null) { HungerHeart lit = hearts[index]; hearts[index] = null; lit.discard(); }
        cancelChain();
        entityData.set(STANCE, 0);
        stagger = 48; spent = 0; rest = Math.max(rest, 56);
        var at = WitherGeometry.heartCenter(index);
        SetPiecePayload.send((ServerLevel) level(), Piece.WITHER_BACKFIRE, at, core(), 1, this);
        CameraShakePayload.sendAround((ServerLevel) level(), position(), 48, .4F, 20);
        level().playSound(null, at.x, at.y, at.z, SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.4F, .5F);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.WITHER_HURT, SoundSource.HOSTILE, 2F, .45F);
        bypass = true;
        ServerPlayer credit = player != null ? player : fighters().stream().min(Comparator.comparingDouble(p -> p.position().distanceToSqr(at))).orElse(null);
        invulnerableTime = 0;
        try { hurt(credit == null ? damageSources().magic() : damageSources().indirectMagic(this, credit), getMaxHealth() * BACKFIRE); }
        finally { bypass = false; }
        return true;
    }
    /** Ribbreaker hit stone: stopped dead and stunned, chest open. */
    void crashed(Vec3 at) {
        crash = WitherGeometry.CRASH_TICKS; spent = 0; recovery = 0; chain.clear(); entityData.set(STANCE, 0);
        rest = Math.max(rest, 20);
    }
    private void phaseChanged() {
        float fraction = getHealth() / getMaxHealth();
        int next = fraction > 2 / 3F ? 1 : fraction > 1 / 3F ? 2 : 3;
        if (next <= phase() || dying()) return;
        cancelChain();
        ward = false;
        entityData.set(PHASE, next);
        entityData.set(BURNT, 0);
        reformHearts(HungerHeart.DORMANT);
        transition = TRANSITION_TICKS; stagger = crash = spent = 0;
        entityData.set(ANCHOR, -1); feedTicks = 0; rest = Math.max(rest, 24); entityData.set(STANCE, 0);
        bar.setColor(next == 2 ? BossEvent.BossBarColor.PURPLE : BossEvent.BossBarColor.RED);
        bar.setName(Component.translatable("wither.heroesevolved.phase." + next));
        WitherMoves.roar(this, next);
        WitherArena.phase(next);
        tell("wither.heroesevolved.phase_tip." + next);
    }
    public void operatorPhase() { if (fighting() && phase() < 3) { setHealth(getMaxHealth() * (phase() == 1 ? .65F : .32F)); phaseChanged(); } }
    /** Operator control: its ward shatters as if all three hearts were broken. */
    public boolean operatorBreakWard() { if (!fighting() || !ward || arrival > 0) return false; breakWard(null); return true; }
    /** Operator control: straight to the death scene, its detonation and the exit, credited to the first fighter. */
    public void operatorDefeat() {
        if (!fighting()) return;
        skipArrival(); transition = 0;
        ServerPlayer p = fighters().stream().findFirst().orElse(null);
        if (p != null) killer = p.getUUID();
        beginDeath(p == null ? damageSources().generic() : damageSources().playerAttack(p));
    }

    @Override public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) return false;
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        if (!fighting() || transition > 0 || arrival > 0 || !Float.isFinite(amount) || amount <= 0) return false;
        if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.DROWN) || source.is(DamageTypes.CRAMMING)) return false;
        if (source.getEntity() != null && WitherArena.sheltered(source.getEntity())) return false;
        boolean swing = source.getEntity() instanceof Player p && WitherEvents.fullSwing(p);
        boolean returned = source.getDirectEntity() instanceof RiftSkull skull && skull.returned();
        boolean heavy = amount >= 8 || returned || source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.AbstractArrow a && a.isCritArrow() || swing;
        boolean star = false;
        if (!bypass) {
            if (returned) amount = getMaxHealth() * WitherGeometry.RETURNED_SHARE;
            else {
                amount /= Math.max(1, effectiveHealth / getMaxHealth());
                if (source.getEntity() instanceof Player p) amount *= p.distanceTo(this) <= 48 ? 1 : Math.clamp((80 - p.distanceTo(this)) / 32, 0, 1);
                else amount = Math.min(3, amount);
                // Plain weapons are the fallback, never the plan; while its ward stands, everything does far less.
                if (BossLocks.plain(source)) amount *= WitherGeometry.PLAIN_BODY;
                if (ward) amount *= WitherGeometry.WARD_TAKES[Math.min(3, wardHearts())];
            }
            if (crash > 0 || spent > 0 && casting == null) amount *= 1.35F;
            else if (stagger > 0) amount *= 1.2F;
            if (phase() == 3 && onStar(source)) {
                star = true;
                // A power whose key is its blows on the star counts as the table says; anything else, half again and more.
                BossLocks.Key key = source.getEntity() instanceof ServerPlayer p && !BossLocks.plain(source) ? BossLocks.key(p, BossLocks.Lock.WEAK_POINT) : null;
                amount *= key != null && key.channel() == BossLocks.Channel.HITS
                        ? Math.max(WitherGeometry.STAR_TAKES, key.strong() ? BossLocks.STRONG_HITS : BossLocks.GOOD_HITS) : WitherGeometry.STAR_TAKES;
            }
            amount = Math.min(amount, Math.max(10, getMaxHealth() * HIT_CAP));
            while (!recent.isEmpty() && clock - recent.peekFirst()[0] >= 20) recent.removeFirst();
            if (!recent.isEmpty() && !returned) {
                float total = 0;
                for (float[] hit : recent) total += hit[1];
                amount = Math.min(amount, Math.max(0, getMaxHealth() * SECOND_CAP - total));
            }
            if (ward) {
                // The ward holds the line: until its hearts are broken it cannot be brought below the gate.
                amount = Math.min(amount, getHealth() - getMaxHealth() * WitherGeometry.WARD_GATE);
                if (amount <= 0) {
                    if (clock - lastWardFlash > 6) {
                        lastWardFlash = clock;
                        SetPiecePayload.send((ServerLevel) level(), Piece.WITHER_WARD_HIT, core(), core(), 1, this);
                        level().playSound(null, getX(), getY() + 4, getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 1.5F, .5F);
                    }
                    return false;
                }
            }
            if (amount <= 0) return false;
        }
        boolean lethal = amount >= getHealth();
        if (lethal) amount = Math.max(0, getHealth() - 1);
        boolean landed = amount <= 0 || super.hurt(source, amount);
        if (!landed) return false;
        lastHurt = clock;
        if (!bypass) recent.add(new float[]{clock, amount});
        if (source.getEntity() instanceof Player p) killer = p.getUUID();
        if (heavy && casting != null && casting != Move.HORIZON && clock - lastInterrupt >= 100 && clock - lastStance >= 8) {
            lastStance = clock;
            entityData.set(STANCE, stance() + 1);
            SetPiecePayload.sendSeeded((ServerLevel) level(), Piece.WITHER_CRACK, core(), core(), 1, this, 0, stance());
            if (stance() >= WitherGeometry.STANCE_HITS) {
                lastInterrupt = clock;
                cancelChain();
                entityData.set(STANCE, 0);
                stagger = 36; spent = 0; rest = Math.max(rest, 32);
                level().playSound(null, getX(), getY(), getZ(), SoundEvents.SKELETON_HURT, SoundSource.HOSTILE, 2.5F, .45F);
                CameraShakePayload.sendAround((ServerLevel) level(), position(), 32, .3F, 14);
            }
        }
        if (star) starHit();
        if (lethal) beginDeath(source); else phaseChanged();
        return true;
    }
    private void beginDeath(DamageSource source) {
        killingBlow = source;
        cancelChain();
        entityData.set(MODE, 3); entityData.set(AGE, 0); entityData.set(FINISH, false); entityData.set(ANCHOR, -1);
        ward = false;
        dropHearts(true);
        tendHearts();
        clearMinions();
        clearHazards();
        SetPiecePayload.send((ServerLevel) level(), Piece.WITHER_DEATH, position(), core(), 1, this);
        level().playSound(null, blockPosition(), SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 1.4F, .6F);
        bar.setName(Component.translatable("wither.heroesevolved.collapsing"));
        bar.setColor(BossEvent.BossBarColor.WHITE);
    }
    @Override public void die(DamageSource source) {
        boolean reward = fighting() || dying();
        entityData.set(MODE, 4);
        super.die(source);
        bar.removeAllPlayers();
        if (reward && !level().isClientSide) WitherArena.victory(this);
    }
    @Override public void remove(RemovalReason reason) {
        clearMinions(); clearHazards(); bar.removeAllPlayers();
        for (int i = 0; i < 3; i++) if (hearts[i] != null) { hearts[i].discard(); hearts[i] = null; }
        super.remove(reason);
    }
    private void clearHazards() {
        if (cage != null) WitherMoves.releaseCage(this, false);
        wells.clear();
        for (var skull : List.copyOf(skulls)) skull.discard();
        for (var spew : List.copyOf(spews)) { spew.mob().discard(); minions.remove(spew.mob()); }
        spews.clear();
        skulls.clear(); ripples.clear(); spires.clear(); beams.clear(); beamTicks = 0;
    }
    private void clearMinions() { for (Mob minion : List.copyOf(minions)) minion.discard(); minions.clear(); }
    private boolean window() {
        if (!fighting() || transition > 0 || arrival > 0) return false;
        float fraction = getHealth() / getMaxHealth();
        return phase() == 1 ? fraction <= 2 / 3F + .06F && fraction > 2 / 3F : phase() == 2 ? fraction <= 1 / 3F + .06F && fraction > 1 / 3F : fraction <= .1F;
    }
    @Override public boolean finishWindow() { return entityData.get(FINISH); }
    @Override public void phaseFinished(ServerPlayer p) {
        if (!window()) return;
        if (phase() == 3) { killer = p.getUUID(); beginDeath(damageSources().playerAttack(p)); return; }
        setHealth(Math.max(1, getMaxHealth() * (phase() == 1 ? 2 / 3F : 1 / 3F) - getMaxHealth() * .06F));
        phaseChanged();
        stagger = 70;
    }
}
