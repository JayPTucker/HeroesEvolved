package com.jayptucker.heroesevolved.story.elle;

import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.ScreenTintPayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Elle Bishop, sent by the Company to test you. A boss in three phases on Primatech's test floor
 * ({@link PrimatechArena}):
 * <ol>
 *   <li><b>Field Test</b> (100–65%): arc bolts, ground current and siphons from the banks.</li>
 *   <li><b>Overcharged</b> (65–30%): the conduits surge one after another, she rolls ball lightning, and she forces
 *   cut banks back on.</li>
 *   <li><b>Blackout</b> (30–0%): the lights go out, the storm comes down from the ceiling, and current leaks from her
 *   into anyone close.</li>
 * </ol>
 * <p>Her counterplay is the room's: <b>every live bank shields her</b> (each takes {@link ElleMoves#SHIELD_PER_BANK} off
 * every hit), and <b>a bank whose breaker is pulled while she siphons from it backfires into her</b>: a heavy hit
 * through her shield, three seconds stunned, and that bank blown for good.</p>
 */
public final class ElleEntity extends PathfinderMob implements Enemy {
    private static final EntityDataAccessor<Integer> POSE = SynchedEntityData.defineId(ElleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(ElleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> BANKS = SynchedEntityData.defineId(ElleEntity.class, EntityDataSerializers.INT);
    private static final double BAR_RANGE = 48;
    /** Where the phases turn. */
    public static final float PHASE_TWO = 0.65F, PHASE_THREE = 0.30F;

    public enum Pose { NONE, POINT, PALMS_DOWN, DRAW, BOTH_HANDS, RAISED, STAGGER, SCENE }

    private final ServerBossEvent bar = new ServerBossEvent(Component.translatable("story.heroesevolved.elle.bar.1"),
            BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_10);
    private UUID subject;
    private boolean scene;
    private int phase = 1, combatTicks, globalCooldown = 40, castTicks, stagger, overcharge, touchAt, alone;
    private ElleMoves casting;
    private final Map<String, Integer> cooldowns = new HashMap<>();
    private Vec3 mark = Vec3.ZERO;
    private int bank = -1;
    private float concentration;
    private final List<Vec3> strikes = new ArrayList<>();
    // The ball lightning rolling down its lane.
    private Vec3 ballFrom, ballTo;
    private int ballAge = -1;
    // The conduits' surges.
    private int surgeBank = -1, surgeAge = -1, nextSurge, surgeTurn;
    private int backfires, siphons;

    public ElleEntity(EntityType<? extends ElleEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired(); setCanPickUpLoot(false);
        bar.setVisible(false);
        xpReward = 50;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 200).add(Attributes.MOVEMENT_SPEED, .3)
                .add(Attributes.ATTACK_DAMAGE, 4).add(Attributes.FOLLOW_RANGE, 64).add(Attributes.ARMOR, 4)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.4);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(POSE, 0); builder.define(PHASE, 1); builder.define(BANKS, (1 << PrimatechGeometry.BANK_COUNT) - 1);
    }

    @Override protected void registerGoals() { goalSelector.addGoal(0, new FloatGoal(this)); }

    // ------------------------------------------------------------------
    // State others read
    // ------------------------------------------------------------------

    void subject(ServerPlayer player) { subject = player.getUUID(); }
    public UUID subject() { return subject; }
    /** She is only standing in a scene (her arrival): no fight, no bar. */
    public void scene(boolean scene) { this.scene = scene; entityData.set(POSE, scene ? Pose.SCENE.ordinal() : 0); }
    public boolean isScene() { return scene; }
    public int phase() { return level().isClientSide ? entityData.get(PHASE) : phase; }
    public Pose pose() { return Pose.values()[Math.clamp(entityData.get(POSE), 0, Pose.values().length - 1)]; }
    /** The banks feeding her, as her clients see them. */
    public int banks() { return entityData.get(BANKS); }
    public String casting() { return casting == null ? "" : casting.id(); }
    public int siphonBank() { return casting == ElleMoves.SIPHON ? bank : -1; }
    public boolean staggered() { return stagger > 0; }
    public int backfires() { return backfires; }
    public int siphons() { return siphons; }
    public int surgeBank() { return surgeAge >= 0 ? surgeBank : -1; }
    public int overcharge() { return overcharge; }
    ServerBossEvent bar() { return bar; }
    /** For previews: she picks no move of her own for this long. */
    public void holdOff(int ticks) { globalCooldown = Math.max(globalCooldown, ticks); }
    /** For previews: no conduit surge of its own for this long. */
    public void holdSurges(int ticks) { nextSurge = Math.max(nextSurge, combatTicks + ticks); }
    /** What every hit on her is multiplied by now: her shield, or her stun. */
    public float shield() { return stagger > 0 ? 1.25F : 1 - ElleMoves.SHIELD_PER_BANK * PrimatechArena.liveCount(); }
    private float power() { return (phase == 1 ? 1.0F : phase == 2 ? 1.2F : 1.35F) * (overcharge > 0 ? 1.3F : 1); }

    private ServerPlayer subjectPlayer(ServerLevel level) {
        ServerPlayer p = subject == null ? null : level.getServer().getPlayerList().getPlayer(subject);
        if (p != null && p.level() == level && p.isAlive() && !p.isSpectator()) return p;
        // The subject is down: she turns to whoever is still standing.
        ServerPlayer best = null;
        for (ServerPlayer other : level.players())
            if (ElleMoves.fighter(other) && (best == null || other.distanceToSqr(this) < best.distanceToSqr(this))) best = other;
        if (best != null) subject = best.getUUID();
        return best;
    }

    // ------------------------------------------------------------------
    // The fight, tick by tick
    // ------------------------------------------------------------------

    @Override protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel level) || scene) return;
        combatTicks++;
        entityData.set(BANKS, PrimatechArena.liveMask());
        ServerPlayer target = subjectPlayer(level);
        tickBar(level);
        if (target == null) {
            if (++alone > 40) ElleStoryService.ended(this, ElleStoryService.Ending.LOST);
            return;
        }
        alone = 0;
        if (combatTicks >= PrimatechArena.TIME_LIMIT) { ElleStoryService.ended(this, ElleStoryService.Ending.TIME); return; }
        getLookControl().setLookAt(target, 30, 30);
        if (combatTicks % 20 == 1) SetPiecePayload.send(level, Piece.ELLE_ARENA, PrimatechArena.floor(), PrimatechGeometry.coil(PrimatechArena.floor()), phase, this);
        if (combatTicks % 10 == 1) SetPiecePayload.send(level, Piece.ELLE_AURA, getEyePosition(), getEyePosition(), Math.max(0.1F, PrimatechArena.liveCount()), this);
        tickPhase(level);
        tickHazards(level);
        tickBall(level);
        if (stagger > 0) {
            getNavigation().stop();
            if (--stagger == 0) entityData.set(POSE, 0);
            return;
        }
        // A dampener round (or field) cuts her windup short; until it wears off she can only keep her distance.
        if (com.jayptucker.heroesevolved.dampener.Dampening.dampened(this)) { if (casting != null) finish(casting); keepDistance(target); return; }
        if (casting != null) { tickCast(level, target); return; }
        if (globalCooldown > 0) globalCooldown--;
        else if (combatTicks >= 30) choose(level, target);
        keepDistance(target);
        touch(level, target);
    }

    /** She fights at range: closes when far, backs off when crowded, and sidesteps in between. */
    private void keepDistance(ServerPlayer target) {
        double d = distanceTo(target);
        if (d > 12) getNavigation().moveTo(target, 1.0);
        else if (d < 5) {
            Vec3 away = position().add(ElleMoves.flat(position().subtract(target.position())).scale(4));
            if (PrimatechArena.inside(away)) getNavigation().moveTo(away.x, away.y, away.z, 1.15);
        } else if (combatTicks % 40 == 0) {
            Vec3 toward = ElleMoves.flat(target.position().subtract(position())), side = new Vec3(-toward.z, 0, toward.x);
            Vec3 step = position().add(side.scale(random.nextBoolean() ? 3 : -3));
            if (PrimatechArena.inside(step)) getNavigation().moveTo(step.x, step.y, step.z, 0.9);
        }
    }

    /** Up close her touch carries a charge: a jolt that throws you off her. */
    private void touch(ServerLevel level, ServerPlayer target) {
        if (combatTicks < touchAt || distanceToSqr(target) > 1.8 * 1.8) return;
        touchAt = combatTicks + 30;
        swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        ElleMoves.hurt(this, target, 4 * power());
        ElleMoves.push(target, ElleMoves.flat(target.position().subtract(position())).scale(0.9).add(0, 0.35, 0));
        SetPiecePayload.send(level, Piece.ELLE_BOLT, ElleMoves.hand(this), target.getBoundingBox().getCenter(), 0.6F, null);
        sound(level, target.position(), SoundEvents.TRIDENT_THUNDER.value(), 0.4F, 2.0F);
    }

    private void choose(ServerLevel level, ServerPlayer target) {
        double distance = distanceTo(target);
        boolean sight = hasLineOfSight(target);
        List<ElleMoves> options = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        int total = 0;
        for (ElleMoves move : ElleMoves.ALL) {
            if (move.phase() > phase || cooldowns.getOrDefault(move.id(), 0) > combatTicks
                    || distance < move.minRange() || distance > move.maxRange()) continue;
            if ((move == ElleMoves.ARC_BOLT || move == ElleMoves.BALL) && !sight) continue;
            if (move == ElleMoves.SIPHON && PrimatechArena.liveCount() == 0) continue;
            if (move == ElleMoves.REBOOT && cutBank() < 0) continue;
            int weight = move.weight() + (move == ElleMoves.GROUND_CURRENT && distance < 5 ? 2 : 0)
                    + (move == ElleMoves.SIPHON && getHealth() < getMaxHealth() * 0.8F ? 1 : 0);
            options.add(move); weights.add(weight); total += weight;
        }
        if (options.isEmpty()) return;
        int roll = random.nextInt(total);
        ElleMoves move = options.getLast();
        for (int i = 0; i < options.size(); i++) { roll -= weights.get(i); if (roll < 0) { move = options.get(i); break; } }
        start(level, move, target);
    }

    /** A bank that is cut off but not blown: one she can force back on. */
    private int cutBank() {
        List<Integer> cut = new ArrayList<>();
        for (int i = 0; i < PrimatechGeometry.BANK_COUNT; i++) if (PrimatechArena.bank(i) == PrimatechArena.Bank.CUT) cut.add(i);
        return cut.isEmpty() ? -1 : cut.get(random.nextInt(cut.size()));
    }

    private void start(ServerLevel level, ElleMoves move, LivingEntity target) {
        casting = move; castTicks = 0; concentration = 0;
        entityData.set(POSE, move.pose().ordinal());
        getNavigation().stop();
        Vec3 floor = PrimatechArena.floor();
        switch (move.id()) {
            case "arc_bolt" -> {
                // Locked where you stand now: the filament shows exactly the line it will take.
                mark = target.getBoundingBox().getCenter();
                Vec3 hand = ElleMoves.hand(this), end = reach(level, hand, mark, 26);
                mark = end;
                SetPiecePayload.send(level, Piece.ELLE_AIM, hand, end, 1, this);
                sound(level, position(), SoundEvents.BEACON_POWER_SELECT, 0.6F, 1.8F);
            }
            case "ground_current" -> {
                mark = position();
                SetPiecePayload.send(level, Piece.ELLE_RING_MARK, position().add(0, 0.02, 0), position(), ElleMoves.RING, this);
                sound(level, position(), SoundEvents.BEACON_AMBIENT, 1.2F, 1.6F);
            }
            case "siphon" -> {
                List<Integer> live = new ArrayList<>();
                for (int i = 0; i < PrimatechGeometry.BANK_COUNT; i++) if (PrimatechArena.live(i)) live.add(i);
                bank = live.get(random.nextInt(live.size()));
                SetPiecePayload.send(level, Piece.ELLE_SIPHON, PrimatechGeometry.bank(floor, bank), getEyePosition(), bank, this);
                sound(level, PrimatechGeometry.bank(floor, bank), SoundEvents.BEACON_ACTIVATE, 1.4F, 1.3F);
                for (ServerPlayer witness : bar.getPlayers()) ElleVoice.warn(witness, "story.heroesevolved.elle.warn.siphon", bank + 1);
            }
            case "reboot" -> {
                bank = cutBank();
                SetPiecePayload.send(level, Piece.ELLE_REBOOT, ElleMoves.hand(this), PrimatechGeometry.bank(floor, bank), bank, this);
                sound(level, PrimatechGeometry.bank(floor, bank), SoundEvents.RESPAWN_ANCHOR_CHARGE, 1.2F, 1.4F);
                for (ServerPlayer witness : bar.getPlayers()) ElleVoice.warn(witness, "story.heroesevolved.elle.warn.reboot", bank + 1);
            }
            case "ball_lightning" -> {
                Vec3 hand = ElleMoves.hand(this);
                Vec3 dir = ElleMoves.flat(target.position().subtract(position()));
                ballFrom = new Vec3(hand.x, PrimatechArena.ORIGIN.getY() + 1.9, hand.z);
                ballTo = reach(level, ballFrom, ballFrom.add(dir.scale(ElleMoves.BALL_REACH)), ElleMoves.BALL_REACH);
                SetPiecePayload.send(level, Piece.ELLE_ORB, ballFrom, ballTo, 1, this);
                sound(level, hand, SoundEvents.BEACON_POWER_SELECT, 1.0F, 0.7F);
            }
            case "storm" -> {
                strikes.clear();
                for (ServerPlayer p : bar.getPlayers()) if (ElleMoves.fighter(p)) strikes.add(new Vec3(p.getX(), PrimatechArena.ORIGIN.getY() + 1, p.getZ()));
                for (int i = 0; i < 2; i++) {
                    double a = random.nextDouble() * Math.PI * 2, r = 3 + random.nextDouble() * 7;
                    strikes.add(PrimatechArena.center().add(Math.cos(a) * r, 0, Math.sin(a) * r));
                }
                for (Vec3 at : strikes) SetPiecePayload.send(level, Piece.ELLE_STRIKE_MARK, at.add(0, 0.03, 0), at.add(0, PrimatechGeometry.CEILING - 1, 0), ElleMoves.STRIKE, null);
                sound(level, position(), SoundEvents.LIGHTNING_BOLT_THUNDER, 0.6F, 1.6F);
            }
            default -> {}
        }
    }

    /** From {@code from} toward {@code at}, out to {@code max} blocks or the first wall. */
    private Vec3 reach(ServerLevel level, Vec3 from, Vec3 at, double max) {
        Vec3 dir = at.subtract(from);
        if (dir.lengthSqr() < 1e-6) return at;
        Vec3 end = from.add(dir.normalize().scale(max));
        var hit = level.clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        return hit.getLocation();
    }

    private void tickCast(ServerLevel level, ServerPlayer target) {
        getNavigation().stop();
        castTicks++;
        ElleMoves move = casting;
        if (move == ElleMoves.SIPHON) {
            if (!PrimatechArena.live(bank)) { backfire(level); return; }
            if (castTicks % 10 == 0) sound(level, PrimatechGeometry.bank(PrimatechArena.floor(), bank), SoundEvents.BEACON_AMBIENT, 1.2F, 2.0F);
        }
        if (move == ElleMoves.REBOOT && PrimatechArena.live(bank)) { finish(move); return; }
        if (castTicks < move.windup()) return;
        finish(move);
        Vec3 floor = PrimatechArena.floor();
        switch (move.id()) {
            case "arc_bolt" -> {
                Vec3 hand = ElleMoves.hand(this);
                SetPiecePayload.send(level, Piece.ELLE_BOLT, hand, mark, 1.2F, null);
                sound(level, hand, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.9F, 1.7F);
                AABB lane = new AABB(hand, mark).inflate(1.2);
                for (LivingEntity foe : level.getEntitiesOfClass(LivingEntity.class, lane, e -> ElleMoves.foe(this, e))) {
                    Vec3 c = foe.getBoundingBox().getCenter();
                    if (distanceToSegment(c, hand, mark) <= 0.95) {
                        ElleMoves.hurt(this, foe, 6 * power());
                        foe.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1));
                    }
                }
                spend();
            }
            case "ground_current" -> {
                SetPiecePayload.send(level, Piece.ELLE_RING, mark.add(0, 0.02, 0), mark, ElleMoves.RING, null);
                sound(level, mark, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.2F, 0.9F);
                CameraShakePayload.sendAround(level, mark, 24, 0.25F, 6);
                for (LivingEntity foe : ElleMoves.foes(this, mark, ElleMoves.RING)) {
                    if (!PrimatechArena.onFloor(foe) || PrimatechArena.onMat(foe)) continue;
                    ElleMoves.hurt(this, foe, 7 * power());
                    foe.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2));
                }
                spend();
            }
            case "siphon" -> {
                siphons++;
                heal(getMaxHealth() * ElleMoves.SIPHON_HEAL);
                overcharge = 2;
                Vec3 face = PrimatechGeometry.bank(floor, bank);
                SetPiecePayload.send(level, Piece.ELLE_AURA, getEyePosition(), face, 6, this);
                sound(level, position(), SoundEvents.BEACON_POWER_SELECT, 1.4F, 1.2F);
                for (ServerPlayer witness : bar.getPlayers()) ElleVoice.say(witness, "story.heroesevolved.elle.say.siphoned");
            }
            case "reboot" -> {
                Vec3 breaker = Vec3.atCenterOf(PrimatechArena.breaker(bank));
                boolean grounded = !ElleMoves.foes(this, breaker, ElleMoves.GROUNDED).isEmpty();
                if (grounded) {
                    SetPiecePayload.send(level, Piece.ELLE_SHORT, PrimatechGeometry.bank(floor, bank), breaker, 1, null);
                    sound(level, breaker, SoundEvents.REDSTONE_TORCH_BURNOUT, 1.2F, 0.7F);
                    for (ServerPlayer witness : bar.getPlayers()) ElleVoice.warn(witness, "story.heroesevolved.elle.warn.grounded");
                } else {
                    PrimatechArena.reboot(level, bank);
                    sound(level, breaker, SoundEvents.BEACON_ACTIVATE, 1.4F, 1.5F);
                    for (ServerPlayer witness : bar.getPlayers()) ElleVoice.warn(witness, "story.heroesevolved.elle.warn.rebooted", bank + 1);
                }
            }
            case "ball_lightning" -> {
                ballAge = 0;
                sound(level, ballFrom, SoundEvents.TRIDENT_RIPTIDE_3.value(), 1.0F, 1.6F);
                spend();
            }
            case "storm" -> {
                for (Vec3 at : strikes) {
                    SetPiecePayload.send(level, Piece.ELLE_STRIKE, at, at.add(0, PrimatechGeometry.CEILING - 1, 0), ElleMoves.STRIKE, null);
                    for (LivingEntity foe : ElleMoves.foes(this, at, ElleMoves.STRIKE)) ElleMoves.hurt(this, foe, 8 * power());
                }
                sound(level, position(), SoundEvents.LIGHTNING_BOLT_THUNDER, 1.2F, 1.1F);
                CameraShakePayload.sendAround(level, position(), 32, 0.4F, 8);
                spend();
            }
            default -> {}
        }
    }

    private void finish(ElleMoves move) {
        cooldowns.put(move.id(), combatTicks + move.cooldown());
        casting = null; castTicks = 0;
        entityData.set(POSE, 0);
        globalCooldown = Math.max(globalCooldown, phase == 1 ? 36 : phase == 2 ? 26 : 20);
    }

    /** An attack used up one of the overcharged ones a siphon gave her. */
    private void spend() { if (overcharge > 0) overcharge--; }

    private static double distanceToSegment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double k = Math.clamp(p.subtract(a).dot(ab) / Math.max(1e-6, ab.lengthSqr()), 0, 1);
        return p.distanceTo(a.add(ab.scale(k)));
    }

    // ------------------------------------------------------------------
    // The counterplay: a siphon whose bank is cut comes back up the arc
    // ------------------------------------------------------------------

    private void backfire(ServerLevel level) {
        int blown = bank;
        casting = null; castTicks = 0;
        cooldowns.put(ElleMoves.SIPHON.id(), combatTicks + ElleMoves.SIPHON.cooldown());
        backfires++;
        Vec3 face = PrimatechGeometry.bank(PrimatechArena.floor(), blown);
        SetPiecePayload.send(level, Piece.ELLE_BACKFIRE, face, getEyePosition(), blown, this);
        PrimatechArena.blow(level, blown);
        sound(level, position(), SoundEvents.LIGHTNING_BOLT_IMPACT, 1.6F, 0.7F);
        sound(level, position(), SoundEvents.GENERIC_EXPLODE.value(), 0.8F, 1.4F);
        CameraShakePayload.sendAround(level, position(), 40, 0.6F, 10);
        // The one who pulled it (whoever stands nearest the breaker) is credited with the hit.
        Player puller = level.getNearestPlayer(face.x, face.y, face.z, 5, false);
        float before = getHealth();
        stagger = ElleMoves.STAGGER_TICKS;
        entityData.set(POSE, Pose.STAGGER.ordinal());
        bypass = true;
        try {
            invulnerableTime = 0;
            DamageSource source = puller != null ? damageSources().playerAttack(puller) : damageSources().lightningBolt();
            hurt(source, getMaxHealth() * ElleMoves.BACKFIRE);
        } finally { bypass = false; }
        if (getHealth() >= before && isAlive()) setHealth(before - getMaxHealth() * ElleMoves.BACKFIRE);
        for (ServerPlayer witness : bar.getPlayers()) {
            ElleVoice.warn(witness, "story.heroesevolved.elle.warn.backfire");
            if (backfires == 1) ElleVoice.say(witness, "story.heroesevolved.elle.say.backfire");
        }
    }

    // ------------------------------------------------------------------
    // The room's hazards: the pylon, the conduits, the leak, the ball lightning
    // ------------------------------------------------------------------

    private void tickHazards(ServerLevel level) {
        Vec3 floor = PrimatechArena.floor();
        // The pylon bites anyone touching its pad while any bank feeds it.
        if (combatTicks % 20 == 0 && PrimatechArena.liveCount() > 0)
            for (LivingEntity foe : ElleMoves.foes(this, floor, PrimatechGeometry.PYLON + 0.5)) {
                ElleMoves.hurt(this, foe, 3);
                SetPiecePayload.send(level, Piece.ELLE_BOLT, PrimatechGeometry.coil(floor).add(0, -2, 0), foe.getBoundingBox().getCenter(), 0.7F, null);
                sound(level, foe.position(), SoundEvents.TRIDENT_THUNDER.value(), 0.3F, 2.0F);
            }
        // Blackout: her current leaks into anyone who stays close.
        if (phase >= 3 && combatTicks % 20 == 10)
            for (LivingEntity foe : ElleMoves.foes(this, position(), 2.6)) {
                ElleMoves.hurt(this, foe, 2);
                SetPiecePayload.send(level, Piece.ELLE_BOLT, getEyePosition().add(0, -0.4, 0), foe.getBoundingBox().getCenter(), 0.5F, null);
            }
        // Overcharged on: the conduits surge one after another, each warning first.
        if (phase >= 2) {
            if (surgeAge < 0 && combatTicks >= nextSurge) {
                for (int k = 0; k < PrimatechGeometry.BANK_COUNT; k++) {
                    int i = (surgeTurn + k) % PrimatechGeometry.BANK_COUNT;
                    if (!PrimatechArena.live(i)) continue;
                    surgeBank = i; surgeAge = 0; surgeTurn = i + 1;
                    SetPiecePayload.send(level, Piece.ELLE_CONDUIT_MARK, PrimatechGeometry.conduitStart(floor, i), PrimatechGeometry.conduitEnd(floor, i), i, null);
                    sound(level, PrimatechGeometry.conduitStart(floor, i), SoundEvents.BEACON_AMBIENT, 1.0F, 1.9F);
                    break;
                }
                nextSurge = combatTicks + (phase == 2 ? ElleMoves.SURGE_EVERY_TWO : ElleMoves.SURGE_EVERY_THREE);
            }
            if (surgeAge >= 0 && ++surgeAge >= ElleMoves.SURGE_WARNING) {
                // A bank cut while its conduit warned never surges.
                if (PrimatechArena.live(surgeBank)) surge(level, surgeBank);
                surgeAge = -1;
            }
        }
    }

    private void surge(ServerLevel level, int i) {
        Vec3 floor = PrimatechArena.floor(), a = PrimatechGeometry.conduitStart(floor, i), b = PrimatechGeometry.conduitEnd(floor, i);
        SetPiecePayload.send(level, Piece.ELLE_CONDUIT, a, b, i, null);
        sound(level, a.lerp(b, 0.5), SoundEvents.LIGHTNING_BOLT_IMPACT, 1.0F, 1.3F);
        for (LivingEntity foe : ElleMoves.foes(this, a.lerp(b, 0.5), a.distanceTo(b) / 2 + 2)) {
            if (PrimatechGeometry.fromConduit(floor, i, foe.position()) > PrimatechGeometry.CONDUIT_REACH) continue;
            if (!PrimatechArena.onFloor(foe) || PrimatechArena.onMat(foe)) continue;
            ElleMoves.hurt(this, foe, 5 * power());
        }
    }

    private void tickBall(ServerLevel level) {
        if (ballAge < 0) return;
        double length = ballFrom.distanceTo(ballTo);
        int travel = (int) Math.ceil(length / ElleMoves.BALL_SPEED);
        Vec3 at = ballFrom.lerp(ballTo, Math.min(1, ++ballAge / (double) Math.max(1, travel)));
        boolean burst = ballAge >= travel;
        if (!burst) for (LivingEntity foe : ElleMoves.foes(this, at, 1.1)) if (Math.abs(foe.getBoundingBox().getCenter().y - at.y) < 1.4) { burst = true; break; }
        if (!burst) return;
        ballAge = -1;
        SetPiecePayload.send(level, Piece.ELLE_RING, at.add(0, -0.85, 0), at, (float) ElleMoves.BALL_BURST, this);
        sound(level, at, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.0F, 1.5F);
        for (LivingEntity foe : ElleMoves.foes(this, at, ElleMoves.BALL_BURST)) ElleMoves.hurt(this, foe, 6 * power());
    }

    // ------------------------------------------------------------------
    // The boss bar and phases
    // ------------------------------------------------------------------

    private void tickBar(ServerLevel level) {
        bar.setProgress(Math.clamp(getHealth() / getMaxHealth(), 0, 1));
        if (combatTicks % 10 != 1) return;
        bar.setVisible(true);
        Set<ServerPlayer> near = new HashSet<>();
        for (ServerPlayer player : level.players()) if (player.distanceToSqr(this) < BAR_RANGE * BAR_RANGE && !player.isSpectator()) near.add(player);
        for (ServerPlayer player : List.copyOf(bar.getPlayers())) if (!near.contains(player)) bar.removePlayer(player);
        for (ServerPlayer player : near) bar.addPlayer(player);
    }

    private void tickPhase(ServerLevel level) {
        float fraction = getHealth() / getMaxHealth();
        int next = fraction > PHASE_TWO ? 1 : fraction > PHASE_THREE ? 2 : 3;
        if (next <= phase) return;
        phase = next;
        entityData.set(PHASE, phase);
        bar.setName(Component.translatable("story.heroesevolved.elle.bar." + phase));
        bar.setColor(phase == 2 ? BossEvent.BossBarColor.YELLOW : BossEvent.BossBarColor.WHITE);
        if (casting != null && casting != ElleMoves.SIPHON) finish(casting);
        // Current bursts off her and throws everyone near back.
        SetPiecePayload.send(level, Piece.ELLE_PHASE, PrimatechArena.floor(), getEyePosition(), phase, this);
        for (LivingEntity near : ElleMoves.foes(this, position(), 5))
            ElleMoves.push(near, ElleMoves.flat(near.position().subtract(position())).scale(1.2).add(0, 0.45, 0));
        sound(level, position(), SoundEvents.LIGHTNING_BOLT_THUNDER, 1.4F, phase == 2 ? 1.2F : 0.8F);
        CameraShakePayload.sendAround(level, position(), 40, 0.5F, 12);
        nextSurge = combatTicks + 40;
        if (phase == 3) {
            PrimatechArena.lightsOut(level);
            sound(level, PrimatechArena.center(), SoundEvents.BEACON_DEACTIVATE, 2.0F, 0.5F);
            ScreenTintPayload.sendAround(level, PrimatechArena.center(), 40, 0x70000000, 30, ScreenTintPayload.Style.VIGNETTE);
        }
        for (ServerPlayer witness : bar.getPlayers()) {
            ElleVoice.say(witness, "story.heroesevolved.elle.say.phase" + phase);
            ElleVoice.warn(witness, "story.heroesevolved.elle.warn.phase" + phase);
        }
    }

    // ------------------------------------------------------------------
    // Taking damage: the shield, concentration, her stun
    // ------------------------------------------------------------------

    private boolean bypass;

    @Override public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) return super.hurt(source, amount);
        if (scene && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        // Her own current doesn't hurt her (the pylon, a surge she stands in).
        if (source.getEntity() == this) return false;
        if (!bypass && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) amount *= shield();
        boolean hit = super.hurt(source, amount);
        if (hit && amount > 0 && casting != null && !bypass) {
            // A hard enough hit breaks her concentration on a reboot.
            concentration += amount;
            if (casting == ElleMoves.REBOOT && concentration >= 6) {
                finish(casting);
                if (level() instanceof ServerLevel level) {
                    sound(level, position(), SoundEvents.REDSTONE_TORCH_BURNOUT, 1.0F, 1.2F);
                    for (ServerPlayer witness : bar.getPlayers()) ElleVoice.warn(witness, "story.heroesevolved.elle.warn.interrupted");
                }
            }
        }
        return hit;
    }

    @Override public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide && !isAlive() && !scene) ElleStoryService.defeated(this, source.getEntity());
    }

    private void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.HOSTILE, volume, pitch);
    }

    @Override public void remove(RemovalReason reason) { bar.removeAllPlayers(); super.remove(reason); }
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double distance) { return distance < 128 * 128; }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override protected boolean shouldDespawnInPeaceful() { return false; }
    @Override public boolean causeFallDamage(float distance, float multiplier, DamageSource source) { return false; }
    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); }
    @Override public Component getName() { return Component.translatable("entity.heroesevolved.elle_bishop"); }

    /** For tests and previews: start a move by id now, against the subject. */
    public boolean castNow(String id) {
        if (!(level() instanceof ServerLevel level)) return false;
        ElleMoves move = ElleMoves.byId(id);
        ServerPlayer target = subjectPlayer(level);
        if (move == null || target == null) return false;
        if (move == ElleMoves.SIPHON && PrimatechArena.liveCount() == 0 || move == ElleMoves.REBOOT && cutBank() < 0) return false;
        start(level, move, target);
        return true;
    }

    /** For tests and previews: siphon from one bank in particular. */
    public boolean siphonFrom(int i) {
        if (!(level() instanceof ServerLevel level) || !PrimatechArena.live(i)) return false;
        casting = ElleMoves.SIPHON; castTicks = 0; bank = i;
        entityData.set(POSE, Pose.DRAW.ordinal());
        getNavigation().stop();
        SetPiecePayload.send(level, Piece.ELLE_SIPHON, PrimatechGeometry.bank(PrimatechArena.floor(), i), getEyePosition(), i, this);
        return true;
    }

    /** For tests: where a conduit surge is about to land, and land it now. */
    public void surgeNow(int i) { if (level() instanceof ServerLevel level) surge(level, i); }

    /** The ball lightning, where it is rolling (null when there is none). */
    public Vec3 ball() { return ballAge < 0 ? null : ballFrom.lerp(ballTo, Math.min(1, ballAge * ElleMoves.BALL_SPEED / Math.max(1e-3, ballFrom.distanceTo(ballTo)))); }

    @Override public boolean isPushable() { return !scene; }
    BlockPos home() { return blockPosition(); }
}
