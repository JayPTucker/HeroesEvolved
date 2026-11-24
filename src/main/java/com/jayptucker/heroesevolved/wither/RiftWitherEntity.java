package com.jayptucker.heroesevolved.wither;

import com.jayptucker.heroesevolved.combat.Takedowns;
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
 * its chest open. Heavy hits during windups break its stance; a charge into stone stuns it; a lit pylon broken under it
 * backfires its own hunger.
 */
public final class RiftWitherEntity extends Monster implements Takedowns.PhaseFinishable {
    public static final double BASE_HEALTH = 440, PARTY_STEP = .5, PARTY_MAX = 3;
    public static final float HIT_CAP = .06F, SECOND_CAP = .04F, BACKFIRE = .1F;
    public static final int STATE_NONE = 0, STATE_SPENT = 1, STATE_STAGGER = 2, STATE_CRASH = 3, STATE_ROAR = 4;
    public static final int TRANSITION_TICKS = 40;
    private static final EntityDataAccessor<Integer> MODE = data(EntityDataSerializers.INT), AGE = data(EntityDataSerializers.INT),
            PHASE = data(EntityDataSerializers.INT), ANCHOR = data(EntityDataSerializers.INT), BURNT = data(EntityDataSerializers.INT),
            ACTION = data(EntityDataSerializers.INT), STANCE = data(EntityDataSerializers.INT), STATE = data(EntityDataSerializers.INT),
            SIDE_HEAD = data(EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> FINISH = data(EntityDataSerializers.BOOLEAN);
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
    final Set<UUID> beamHit = new HashSet<>();
    Move casting, motion;
    int windup, rest = 50, transition, stagger, spent, crash, rotation, beamTicks;
    int clock, lastHurt = -1000, lastInterrupt = -1000, lastStance = -1000, recovery, feedTicks, motionTicks, motionStep, attacksLanded;
    int nextHeadShot, actionTicks, serial, variant;
    float lockedYaw, effectiveHealth = (float) BASE_HEALTH;
    Vec3 motionFrom = Vec3.ZERO, motionTo = Vec3.ZERO, velocity = Vec3.ZERO, castFrom = Vec3.ZERO, sideOrigin = Vec3.ZERO, sideTarget = Vec3.ZERO;
    boolean bypass, still;
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
                .add(Attributes.KNOCKBACK_RESISTANCE, 1).add(Attributes.ARMOR, 6).add(Attributes.FOLLOW_RANGE, 64);
    }
    @Override protected void registerGoals() {}
    @Override protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(MODE, 0); b.define(AGE, 0); b.define(PHASE, 1); b.define(ANCHOR, -1); b.define(BURNT, 0); b.define(FINISH, false);
        b.define(ACTION, -1); b.define(STANCE, 0); b.define(STATE, 0); b.define(SIDE_HEAD, -1);
        b.define(AIM_X, 0F); b.define(AIM_Y, 0F); b.define(AIM_Z, 0F); b.define(SIDE_X, 0F); b.define(SIDE_Y, 0F); b.define(SIDE_Z, 0F);
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
    /** Ticks since the current action began: the server's count, or the client's own clock with the frame's fraction. */
    public float actionAge(float partial) { return level().isClientSide ? tickCount - actionStamp + partial : actionTicks; }
    public int actionAge() { return (int) actionAge(0); }
    public int state() { return entityData.get(STATE); }
    public float stateAge(float partial) { return tickCount - stateStamp + partial; }
    public int stance() { return entityData.get(STANCE); }
    public boolean exposed() { int s = state(); return s == STATE_SPENT || s == STATE_STAGGER || s == STATE_CRASH; }
    public Vec3 aim() { return new Vec3(entityData.get(AIM_X), entityData.get(AIM_Y), entityData.get(AIM_Z)); }
    public int sideHead() { int id = entityData.get(SIDE_HEAD); return id < 0 ? -1 : id & 3; }
    public float sideAge(float partial) { return level().isClientSide ? tickCount - sideStamp + partial : sideTicks; }
    public int sideAge() { return (int) sideAge(0); }
    public Vec3 sideAim() { return new Vec3(entityData.get(SIDE_X), entityData.get(SIDE_Y), entityData.get(SIDE_Z)); }
    public float deathAge(float partial) { return level().isClientSide ? tickCount - deathStamp + partial : age(); }
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
    public void still(boolean value) { still = value; }
    public void holdOff(int ticks) { rest = Math.max(rest, ticks); }
    public void beginIntro() { entityData.set(MODE, 1); entityData.set(AGE, 0); }
    public void beginFight() { entityData.set(MODE, 2); refreshDimensions(); setHealth(getMaxHealth()); rest = 45; bar.setProgress(1); }

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
        pollAnchors(level);
        if (feedTicks > 0 && --feedTicks == 0) entityData.set(ANCHOR, -1);
        WitherMoves.tickHazards(this);
        skulls.removeIf(Entity::isRemoved);
        minions.removeIf(m -> !m.isAlive());
        if (casting == null && motionTicks == 0 && recovery == 0 && action() != null && actionTicks > (action().windup + 34)) entityData.set(ACTION, -1);
        if (fighters().isEmpty()) { idle(level); return; }
        if (outside()) { setPos(WitherGeometry.floor().add(0, .3, 0)); velocity = Vec3.ZERO; cancelChain(); }
        if (transition > 0) { WitherMoves.roarTick(this, TRANSITION_TICKS - transition); transition--; return; }
        if (crash > 0) { crash--; return; }
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
                recovery = move == Move.HORIZON ? 24 : move == Move.VERDICT ? WitherGeometry.VERDICT_FIRE + 4 : move == Move.SPIRES ? 14 : 8;
                rest = Math.max(rest, move.rest);
                if (chain.isEmpty()) spent = Math.max(spent, phase() == 1 ? 52 : phase() == 2 ? 44 : 36);
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
        }
        start(chain.removeFirst());
    }
    private void introTick(ServerLevel level) {
        int age = age() + 1;
        entityData.set(AGE, age);
        if (age == 1) {
            SetPiecePayload.send(level, Piece.WITHER_FORM, position(), position().add(0, 1.8, 0), 1, this);
            level.playSound(null, blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, .8F, .55F);
        }
        if (age == 45) CameraShakePayload.sendAround(level, position(), 32, .25F, 40);
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
    private void syncState() {
        int s = transition > 0 ? STATE_ROAR : crash > 0 ? STATE_CRASH : stagger > 0 ? STATE_STAGGER
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
        double preferred = phase() == 1 ? 7 : phase() == 2 ? 6 : 5;
        Vec3 side = new Vec3(-dir.z, 0, dir.x).scale(rotation % 2 == 0 ? 1 : -1);
        Vec3 wanted = Vec3.ZERO;
        if (distance > preferred + 1) wanted = dir.scale(Math.min(distance - preferred, 1)).add(side.scale(.35));
        else if (distance < preferred - 2.5) wanted = dir.scale(-.5).add(side.scale(.5));
        else wanted = side.scale(.6);
        double speed = (distance > 18 ? .5 : .26) + phase() * .04;
        if (spent > 0) speed *= .25;
        speed *= Math.clamp(getAttributeValue(Attributes.MOVEMENT_SPEED) / .26, .2, 1.6);
        wanted = wanted.lengthSqr() > 1e-4 ? wanted.normalize().scale(speed) : Vec3.ZERO;
        double hover = WitherGeometry.floor().y + .3;
        if (foe.getY() > WitherGeometry.floor().y + 5) hover = Math.clamp(foe.getY() - 3.5, hover, WitherGeometry.floor().y + 30);
        wanted = wanted.add(0, Math.clamp(hover - getY(), -.35, .35), 0);
        velocity = velocity.lerp(wanted, .14);
        move(velocity);
        float rate = phase() == 1 ? 5 : phase() == 2 ? 7 : 9;
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
        casting = null; chain.clear(); motionTicks = 0; motion = null; recovery = 0; velocity = Vec3.ZERO; beamTicks = 0;
        entityData.set(ACTION, -1); entityData.set(SIDE_HEAD, -1);
        clearHazards();
        SetPiecePayload.send((ServerLevel) level(), Piece.WITHER_CANCEL, position(), position(), 1, this);
    }
    private void feed() {
        if (anchor() >= 0 || burnt() == 7) return;
        ServerPlayer p = focus();
        if (p == null) return;
        int closest = -1;
        double distance = Double.MAX_VALUE;
        for (int i = 0; i < 3; i++) if ((burnt() & (1 << i)) == 0) {
            double d = p.position().distanceToSqr(Vec3.atCenterOf(WitherGeometry.ANCHORS.get(i)));
            if (d < distance) { distance = d; closest = i; }
        }
        entityData.set(ANCHOR, closest);
        feedTicks = WitherGeometry.FEED_TICKS;
    }
    public void start(Move move) {
        if (!fighting() || dying()) return;
        casting = move; windup = move.windup; marks.clear(); origins.clear(); motionTicks = 0; motion = null; recovery = 0; velocity = Vec3.ZERO;
        for (ServerPlayer p : fighters()) marks.add(p.position().add(0, .8, 0));
        ServerPlayer foe = focus();
        Vec3 aim = foe == null ? position().add(Vec3.directionFromRotation(0, getYRot()).scale(10)) : foe.position().add(0, .8, 0);
        lockedYaw = yawTo(aim);
        if (move == Move.RAKE) variant ^= 1;
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
                nextHeadShot = clock + (phase() == 1 ? 110 : phase() == 2 ? 95 : 80);
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
    private void pollAnchors(ServerLevel level) {
        for (int i = 0; i < 3; i++) {
            BlockPos at = WitherGeometry.ANCHORS.get(i);
            if (!level.hasChunkAt(at) || (burnt() & (1 << i)) != 0) continue;
            if (!level.getBlockState(at).is(Blocks.TINTED_GLASS)) {
                if (anchor() == i) backfire(i, null);
                else level.setBlock(at, Blocks.TINTED_GLASS.defaultBlockState(), 3);
            }
        }
    }
    public boolean backfire(int index, ServerPlayer player) {
        if (index != anchor() || !fighting() || (burnt() & (1 << index)) != 0) return false;
        entityData.set(BURNT, burnt() | 1 << index); entityData.set(ANCHOR, -1);
        feedTicks = 0;
        cancelChain();
        entityData.set(STANCE, 0);
        stagger = 60; spent = 0; rest = Math.max(rest, 70);
        var at = Vec3.atCenterOf(WitherGeometry.ANCHORS.get(index));
        level().setBlock(WitherGeometry.ANCHORS.get(index), Blocks.AIR.defaultBlockState(), 3);
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
        entityData.set(PHASE, next);
        transition = TRANSITION_TICKS; stagger = crash = spent = 0;
        entityData.set(ANCHOR, -1); feedTicks = 0; rest = Math.max(rest, 24); entityData.set(STANCE, 0);
        bar.setColor(next == 2 ? BossEvent.BossBarColor.PURPLE : BossEvent.BossBarColor.RED);
        bar.setName(Component.translatable("wither.heroesevolved.phase." + next));
        WitherMoves.roar(this, next);
        WitherArena.phase(next);
    }
    public void operatorPhase() { if (fighting() && phase() < 3) { setHealth(getMaxHealth() * (phase() == 1 ? .65F : .32F)); phaseChanged(); } }

    @Override public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) return false;
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        if (!fighting() || transition > 0 || !Float.isFinite(amount) || amount <= 0) return false;
        if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.DROWN) || source.is(DamageTypes.CRAMMING)) return false;
        if (source.getEntity() != null && WitherArena.sheltered(source.getEntity())) return false;
        boolean swing = source.getEntity() instanceof Player p && WitherEvents.fullSwing(p);
        boolean heavy = amount >= 8 || source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.AbstractArrow a && a.isCritArrow() || swing;
        if (!bypass) {
            amount /= Math.max(1, effectiveHealth / getMaxHealth());
            if (source.getEntity() instanceof Player p) amount *= p.distanceTo(this) <= 48 ? 1 : Math.clamp((80 - p.distanceTo(this)) / 32, 0, 1);
            else amount = Math.min(3, amount);
            if (crash > 0 || spent > 0 && casting == null) amount *= 1.5F;
            else if (stagger > 0) amount *= 1.25F;
            amount = Math.min(amount, Math.max(10, getMaxHealth() * HIT_CAP));
            while (!recent.isEmpty() && clock - recent.peekFirst()[0] >= 20) recent.removeFirst();
            if (!recent.isEmpty()) {
                float total = 0;
                for (float[] hit : recent) total += hit[1];
                amount = Math.min(amount, Math.max(0, getMaxHealth() * SECOND_CAP - total));
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
                stagger = 50; spent = 0; rest = Math.max(rest, 45);
                level().playSound(null, getX(), getY(), getZ(), SoundEvents.SKELETON_HURT, SoundSource.HOSTILE, 2.5F, .45F);
                CameraShakePayload.sendAround((ServerLevel) level(), position(), 32, .3F, 14);
            }
        }
        if (lethal) beginDeath(source); else phaseChanged();
        return true;
    }
    private void beginDeath(DamageSource source) {
        killingBlow = source;
        cancelChain();
        entityData.set(MODE, 3); entityData.set(AGE, 0); entityData.set(FINISH, false); entityData.set(ANCHOR, -1);
        clearMinions();
        clearHazards();
        SetPiecePayload.send((ServerLevel) level(), Piece.WITHER_DEATH, position(), core(), 1, this);
        level().playSound(null, blockPosition(), SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 1.4F, .6F);
        bar.setName(Component.translatable("wither.heroesevolved.contained"));
    }
    @Override public void die(DamageSource source) {
        boolean reward = fighting() || dying();
        entityData.set(MODE, 4);
        super.die(source);
        bar.removeAllPlayers();
        if (reward && !level().isClientSide) WitherArena.victory(this);
    }
    @Override public void remove(RemovalReason reason) { clearMinions(); clearHazards(); bar.removeAllPlayers(); super.remove(reason); }
    private void clearHazards() {
        for (var skull : List.copyOf(skulls)) skull.discard();
        skulls.clear(); ripples.clear(); spires.clear(); beamTicks = 0;
    }
    private void clearMinions() { for (Mob minion : List.copyOf(minions)) minion.discard(); minions.clear(); }
    private boolean window() {
        if (!fighting() || transition > 0) return false;
        float fraction = getHealth() / getMaxHealth();
        return phase() == 1 ? fraction <= 2 / 3F + .06F && fraction > 2 / 3F : phase() == 2 ? fraction <= 1 / 3F + .06F && fraction > 1 / 3F : fraction <= .1F;
    }
    @Override public boolean finishWindow() { return entityData.get(FINISH); }
    @Override public void phaseFinished(ServerPlayer p) {
        if (!window()) return;
        if (phase() == 3) { killer = p.getUUID(); beginDeath(damageSources().playerAttack(p)); return; }
        setHealth(Math.max(1, getMaxHealth() * (phase() == 1 ? 2 / 3F : 1 / 3F) - getMaxHealth() * .06F));
        phaseChanged();
        stagger = 100;
    }
}
