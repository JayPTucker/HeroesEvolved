package com.jayptucker.heroesevolved.story.sylar;

import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.mimicry.MimicColors;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Gabriel Gray, who calls himself Sylar. One entity plays him everywhere: a story actor that scenes move by hand (the
 * avenue at 6:14, Night Three, the prisoner in Level 5 and the man behind you, see {@link #actor(boolean)}), and, once
 * {@link #begin} is called in the Collection, the boss.
 *
 * <p><b>The fight.</b> He keeps the powers he has taken in glass cases round his hall, and every move but his own
 * telekinesis draws on one of them: while he winds a move up, a thread runs from its case to his hand, and that case's
 * glass can be broken. Break it and the power backfires into him and is gone for the fight. What he takes from you goes
 * into an empty case, and breaking that case gives it back. Every telegraph is a brass schematic that assembles as the
 * move arrives ({@link SylarMoves}); the numbers are {@link SylarGeometry}'s.</p>
 *
 * <p>Three phases (Viewing Hours, Unlocked at two thirds, the Masterwork at one third), the default boss rules (a
 * per-hit and per-second cap, falloff for distant attackers, healing when nobody can reach him, health scaled to the
 * party) and a death scene before the drop. Whether he reached Pod 14 on Night Three or saw the light at 6:14 decides
 * whether his Regeneration and Radiation cases are full ({@link SylarData#holds}).</p>
 */
public final class SylarEntity extends PathfinderMob implements Enemy, com.jayptucker.heroesevolved.combat.Takedowns.PhaseFinishable {
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(SylarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CASES = SynchedEntityData.defineId(SylarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STOLEN = SynchedEntityData.defineId(SylarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DRAWING = SynchedEntityData.defineId(SylarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> POSE = SynchedEntityData.defineId(SylarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FLAGS = SynchedEntityData.defineId(SylarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> OPEN = SynchedEntityData.defineId(SylarEntity.class, EntityDataSerializers.INT);
    /** Synced flags: fighting, winding a move up, reeling, spent, dying, downed, hovering. */
    public static final int FIGHTING = 1, CASTING = 2, STAGGERED = 4, SPENT = 8, DYING = 16, DOWNED = 32, HOVERING = 64, FINISHABLE = 128;
    private static final int CATCHING = 256;

    /** How he holds himself: drawn by {@code SylarRenderer} (arms) and readable by scenes. */
    public enum Pose { NONE, ONE_HAND, TWO_HANDS, RAISED, CROUCH, STAGGER, HOLD, KNEEL }

    // ------------------------------------------------------------------
    // The rules of his fight (the bosses skill's defaults, tuned to the Collection)
    // ------------------------------------------------------------------

    /** His health alone with one fighter; each more fighter adds half again, up to three times. */
    public static final double BASE_HEALTH = 320, PARTY_STEP = 0.5, PARTY_MAX = 3;
    /** No hit takes more than this fraction of his health (and at least {@link #HIT_FLOOR}); no twenty ticks more than the next. */
    public static final float HIT_CAP = 0.06F, HIT_FLOOR = 10, SECOND_CAP = 0.04F;
    /** Full damage from attackers within {@link #FALLOFF_FULL} blocks, none from past {@link #FALLOFF_NONE}. The hall is 43 across. */
    public static final double FALLOFF_FULL = 24, FALLOFF_NONE = 36;
    /** A blow from anything but a player (a golem, lava, a fall) does at most this. */
    public static final float NON_PLAYER_CAP = 3;
    /** He heals this fraction of his health a second once nothing has hurt him for {@link #IDLE_TICKS} and he sees nobody. */
    public static final float IDLE_HEAL = 0.05F;
    public static final int IDLE_TICKS = 200;
    /** Where the phases turn. */
    public static final float PHASE_TWO = 2 / 3F, PHASE_THREE = 1 / 3F;
    /** Transitions (he can't be hurt), a broken concentration, a backfire, being spent, the death scene, Claire's gift. */
    public static final int TRANSITION_TWO = 40, TRANSITION_THREE = 60, CONCENTRATION_TICKS = 50, BACKFIRE_TICKS = 60,
            SPENT_TICKS = 80, FAILED_LIGHT_TICKS = 120, DEATH_TICKS = 60, DOWNED_TICKS = 100;
    /** A backfire takes this fraction of his health, through the caps. Claire's gift brings him back at this fraction. */
    public static final float BACKFIRE = 0.10F, REVIVE = 0.25F;
    /** A cracked concentration and being spent make every hit on him count this much more. */
    public static final float STAGGER_BONUS = 1.25F, SPENT_BONUS = 1.5F;
    /**
     * Finishers: he glistens in the last {@link #FINISH_WINDOW} of his health above each phase threshold, and in his last
     * {@link #FINISH_END}. A finish there takes {@link #FINISH_BITE} of his health (through the caps), forces the next
     * phase at once and leaves him reeling for {@link #FINISH_STAGGER} after its transition; in his last tenth it is the
     * killing blow.
     */
    public static final float FINISH_WINDOW = 0.06F, FINISH_END = 0.10F, FINISH_BITE = 0.15F;
    public static final int FINISH_STAGGER = 100;
    /** Echoes: one at the second phase, and one more for every two fighters past the first; half that again at the third. */
    public static int echoes(int phase, int fighters) {
        int extra = Math.max(0, fighters - 1) / 2;
        return phase == 2 ? 1 + extra : phase == 3 ? extra : 0;
    }
    /** The rest between moves, by phase. */
    private static final int[] REST = {0, 44, 32, 24};
    private static final ResourceLocation PARTY_HEALTH = ResourceLocation.fromNamespaceAndPath("heroesevolved", "sylar_party_health");

    private final ServerBossEvent bar = new ServerBossEvent(Component.literal("Sylar"), BossEvent.BossBarColor.PURPLE,
            BossEvent.BossBarOverlay.NOTCHED_10);

    // The fight's state.
    private final List<ServerPlayer> party = new ArrayList<>();
    private boolean refight, revived;
    /** He has reached for the light once (a world where he never saw it lets him try only once); this time it fails. */
    boolean triedLight, lightFails;
    /** The stolen case Your Own draws on; the fighter he last aimed at (he spreads his attention). */
    int yours = -1;
    ServerPlayer lastAimed;
    int fightTicks, rest = 60, transition, stagger, spent, dying = -1, downed = -1, shoveIn;
    private int lastHurt = -1000, finishedAt = -1000, pendingStagger;
    /** His echoes, each bound to the case whose power it carries (break the case and it fades). */
    final Map<net.minecraft.world.entity.Mob, Integer> echoes = new LinkedHashMap<>();
    private UUID killer;
    private DamageSource killingBlow;
    private final ArrayDeque<float[]> recent = new ArrayDeque<>();
    private final Map<UUID, Integer> unseen = new HashMap<>();
    /** Who put each stolen power into a case, and which power: by hour. */
    final UUID[] stolenFrom = new UUID[12];
    final ResourceLocation[] stolenPower = new ResourceLocation[12];

    // What a move is doing.
    SylarMoves.Move casting, last;
    int castTicks;
    private final Map<String, Integer> cooldowns = new HashMap<>();
    LivingEntity focus;
    Vec3 mark = Vec3.ZERO;
    double angle;
    int safeHour;
    final List<Vec3> spots = new ArrayList<>();
    final List<Vec3[]> lines = new ArrayList<>();
    // Moves still landing after their windup (SylarMoves ticks them).
    int ringAge = -1, handAge = -1, blurLeg = -1, blurTick, reelAge = -1, chunkAge = -1, hoverTicks;
    Vec3 ringCenter = Vec3.ZERO, reelFrom = Vec3.ZERO;
    double handStart, hoverY = Double.NaN;
    final Set<UUID> hit = new HashSet<>();
    final List<Vec3> blurPoints = new ArrayList<>();
    LivingEntity reeled;
    final SylarMoves.Chunk chunk = new SylarMoves.Chunk();
    // Appraisal: who he holds, and how hard they have fought.
    ServerPlayer held;
    int holdTicks;
    float struggle;
    boolean heldShift;
    Vec3 holdAt = Vec3.ZERO;
    record Stopped(Vec3 at, int until) {}
    final Map<UUID, Stopped> stopped = new HashMap<>();

    public SylarEntity(EntityType<? extends SylarEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired(); setCanPickUpLoot(false); setSilent(true);
        bar.setVisible(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 60).add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 5).add(Attributes.FOLLOW_RANGE, 64).add(Attributes.ARMOR, 10)
                .add(Attributes.ARMOR_TOUGHNESS, 4).add(Attributes.KNOCKBACK_RESISTANCE, 0.75);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PHASE, 1); builder.define(CASES, 0); builder.define(STOLEN, 0); builder.define(DRAWING, -1);
        builder.define(POSE, 0); builder.define(FLAGS, 0); builder.define(OPEN, 0);
    }

    @Override protected void registerGoals() { goalSelector.addGoal(0, new FloatGoal(this)); }

    // ------------------------------------------------------------------
    // What the client and scenes read
    // ------------------------------------------------------------------

    public int phase() { return entityData.get(PHASE); }
    /** The cases that hold something and stand whole, a bit per hour. */
    public int cases() { return entityData.get(CASES); }
    public boolean caseWhole(int hour) { return (cases() >> hour & 1) != 0; }
    /** The colour index ({@link MimicColors}) of what he took into empty case {@code k} (0 to 2), or -1. */
    public int stolenColour(int k) { return (entityData.get(STOLEN) >> (7 * k) & 127) - 1; }
    /** The case whose thread runs to his hand now, or -1. */
    public int drawing() { return entityData.get(DRAWING); }
    /** The cases whose glass may be broken now, a bit per hour: drawn on, holding a stolen power, or with an echo out. */
    public int openCases() { return entityData.get(OPEN); }
    public int flags() { return entityData.get(FLAGS); }
    public boolean is(int flag) { return (flags() & flag) != 0; }
    public boolean fighting() { return is(FIGHTING); }
    public Pose castPose() {
        Pose pose = Pose.values()[Math.clamp(entityData.get(POSE), 0, Pose.values().length - 1)];
        return pose == Pose.NONE && is(CATCHING) ? Pose.ONE_HAND : pose;
    }
    void catching(boolean active) { flag(CATCHING, active); }
    /** For scenes that move him by hand: how he holds himself, so a windup reads. */
    public void scenePose(Pose pose) { entityData.set(POSE, pose.ordinal()); }
    public String casting() { return casting == null ? "" : casting.id(); }
    public boolean refight() { return refight; }
    public List<ServerPlayer> party() { return List.copyOf(party); }
    /** The player he holds up in the air, if anyone. */
    public ServerPlayer holding() { return held; }
    public float struggle() { return struggle; }
    public boolean timeStopped(Entity e) { Stopped s = stopped.get(e.getUUID()); return s != null && tickCount < s.until(); }
    /**
     * What a telepath hears in his head mid-fight: which case he is reaching for (the one to break), or that every move
     * he makes comes out of one.
     */
    public String mindLine() {
        int h = drawing();
        if (h >= 0) {
            ResourceLocation power = stolenPower[h] != null ? stolenPower[h] : SylarGeometry.CASES.get(h);
            String name = power == null ? "an empty case" : Component.translatable("ability." + power.getNamespace() + "." + power.getPath()).getString();
            return "He is reaching for the case at " + (h == 0 ? 12 : h) + " o'clock: " + name + ". Break the glass before he closes his hand.";
        }
        if (held != null) return "He is taking your friend apart in his head, piece by piece. Hit him hard enough and he loses his place.";
        return "Every move he makes comes out of a glass case. He keeps looking at them, the way a man checks his watch.";
    }

    /** What he holds: his own telekinesis, and every power standing whole in a case (a scene actor has only his own). */
    public List<ResourceLocation> collection() {
        List<ResourceLocation> out = new ArrayList<>();
        out.add(com.jayptucker.heroesevolved.ability.registry.ModAbilities.TELEKINESIS_ID);
        if (!fighting()) return out;
        for (int h = 0; h < 12; h++) {
            if (!caseWhole(h)) continue;
            ResourceLocation power = stolenPower[h] != null ? stolenPower[h] : SylarGeometry.CASES.get(h);
            if (power != null && !out.contains(power)) out.add(power);
        }
        return out;
    }

    private boolean still;

    /** For previews and tests: he keeps his place (no walking, no shove); his moves still run when started. */
    public void still(boolean still) { this.still = still; if (still) getNavigation().stop(); }

    /** For previews and tests: he picks no move of his own for this long (a move's end never shortens it). */
    public void holdOff(int ticks) { rest = Math.max(rest, ticks); }
    ServerBossEvent bar() { return bar; }
    float castProgress() { return casting == null ? 0 : Math.min(1, castTicks / (float) Math.max(1, casting.windup())); }

    private void flag(int flag, boolean on) { entityData.set(FLAGS, on ? flags() | flag : flags() & ~flag); }
    void pose(Pose pose) { entityData.set(POSE, pose.ordinal()); }
    void drawing(int hour) { entityData.set(DRAWING, hour); }

    // ------------------------------------------------------------------
    // Beginning the fight
    // ------------------------------------------------------------------

    /**
     * The fight begins, with these people. His health is sized to them, the cases are filled from what the story says
     * he has, and the boss bar shows.
     */
    public void begin(List<ServerPlayer> fighters, boolean refight) {
        this.refight = refight;
        party.clear(); party.addAll(fighters);
        int count = (int) Math.max(1, fighters.stream().filter(SylarEntity::counts).count());
        double health = BASE_HEALTH * Math.min(PARTY_MAX, 1 + PARTY_STEP * (count - 1));
        var attribute = getAttribute(Attributes.MAX_HEALTH);
        attribute.removeModifier(PARTY_HEALTH);
        attribute.addPermanentModifier(new AttributeModifier(PARTY_HEALTH, health - attribute.getBaseValue(), AttributeModifier.Operation.ADD_VALUE));
        setHealth(getMaxHealth());
        int mask = 0;
        if (level() instanceof ServerLevel serverLevel) {
            for (int h = 0; h < 12; h++) {
                ResourceLocation power = SylarGeometry.CASES.get(h);
                if (power != null && SylarData.holds(serverLevel.getServer(), power)) mask |= 1 << h;
            }
        }
        entityData.set(CASES, mask);
        entityData.set(PHASE, 1);
        actor = false;
        flag(FIGHTING, true);
        rest = 50;
        bar.setColor(BossEvent.BossBarColor.PURPLE);
        updateBarName();
        bar.setVisible(true);
        for (ServerPlayer p : fighters) bar.addPlayer(p);
    }

    /** A player who fights (not watching in creative or as a spectator). */
    static boolean counts(Player player) { return player.isAlive() && !player.isSpectator() && !player.isCreative(); }

    /** The fighters still in the hall with him. */
    List<ServerPlayer> fighters() {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : party) if (counts(p) && p.level() == level() && !p.isRemoved()) out.add(p);
        return out;
    }

    /** Whom his moves hurt: fighters, and anything on their side (golems, villagers, pets); never another monster. */
    boolean foe(LivingEntity e) {
        if (e == this || !e.isAlive() || e.isSpectator() || e.isInvulnerable()) return false;
        if (e instanceof Player p) return !p.isCreative();
        return e instanceof AbstractGolem || e instanceof AbstractVillager || e instanceof TamableAnimal pet && pet.isTame();
    }

    List<LivingEntity> foesNear(Vec3 at, double radius) {
        return level().getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(radius, 6, radius), this::foe);
    }

    void hurtFoe(LivingEntity target, float amount) {
        // Each of his moves is its own blow: one landing just after another still lands.
        target.invulnerableTime = 0;
        target.hurt(damageSources().mobAttack(this), amount);
    }

    static void push(Entity e, Vec3 velocity) { e.setDeltaMovement(velocity); e.hurtMarked = true; }

    static Vec3 flat(Vec3 v) { Vec3 f = new Vec3(v.x, 0, v.z); return f.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : f.normalize(); }

    Vec3 hand() { return position().add(0, 1.45, 0).add(flat(getLookAngle()).scale(0.45)); }

    void sound(Vec3 at, SoundEvent sound, float volume, float pitch) {
        level().playSound(null, at.x, at.y, at.z, sound, SoundSource.HOSTILE, volume, pitch);
    }

    // ------------------------------------------------------------------
    // The fight, tick by tick
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // An actor: the prisoner behind his glass, and the man behind you
    // ------------------------------------------------------------------

    private boolean actor;

    /**
     * Makes him a story actor ({@link SylarStory}): he stands where he is put, turns to watch whoever is nearest, and
     * cannot be hurt (a hand moves, and whoever struck at him is somewhere else). The fight's {@link #begin} ends it.
     */
    public void actor(boolean actor) { this.actor = actor; if (actor) getNavigation().stop(); }
    public boolean actor() { return actor && !fighting(); }

    private void tickActor(ServerLevel level) {
        getNavigation().stop();
        Player near = level.getNearestPlayer(this, 12);
        if (near != null && !near.isSpectator()) getLookControl().setLookAt(near, 30, 30);
    }

    /** He folds away like a watch case closing: gone. */
    public void foldAway() {
        if (level() instanceof ServerLevel level) {
            SetPiecePayload.send(level, Piece.SYLAR_ARRIVE, position(), getEyePosition(), 1, null);
            sound(position(), SoundEvents.BELL_RESONATE, 1.0F, 0.6F);
        }
        discard();
    }

    @Override protected void customServerAiStep() {
        super.customServerAiStep();
        if (level() instanceof ServerLevel actorLevel && actor()) { tickActor(actorLevel); return; }
        if (!(level() instanceof ServerLevel level) || !fighting()) return;
        fightTicks++;
        List<ServerPlayer> fighters = fighters();
        tickBar(level);
        if (fightTicks % 20 == 1) SetPiecePayload.send(level, Piece.SYLAR_ROOM, SylarGeometry.center(), SylarGeometry.center(), phase(), this);
        pollCases(level);
        int open = 0;
        for (int h = 0; h < 12; h++) if (caseWhole(h) && open(h)) open |= 1 << h;
        if (open != openCases()) entityData.set(OPEN, open);
        SylarMoves.tickLanding(this, level);
        tickStopped(level);
        if (dying >= 0) { tickDying(level); return; }
        if (fighters.isEmpty()) { CollectionArena.end(level.getServer(), CollectionArena.Outcome.EVERYONE_DOWN); return; }
        if (downed >= 0) { tickDowned(level); return; }
        flag(FINISHABLE, inFinishWindow());
        if (transition > 0) {
            if (--transition == 0) {
                pose(Pose.NONE);
                if (pendingStagger > 0) { staggerFor(pendingStagger); pendingStagger = 0; }
            }
            getNavigation().stop();
            return;
        }
        tickEchoes(level);
        if (tickPhase(level)) return;
        if (fightTicks >= CollectionArena.fightTicks()) { CollectionArena.end(level.getServer(), CollectionArena.Outcome.TIME_UP); return; }
        leash();
        idleHeal(level, fighters);
        trackUnseen(fighters);
        if (stagger > 0) { if (--stagger == 0) { flag(STAGGERED, false); pose(Pose.NONE); } getNavigation().stop(); return; }
        if (spent > 0) { if (--spent == 0) { flag(SPENT, false); pose(Pose.NONE); } getNavigation().stop(); return; }
        if (held != null) { SylarMoves.tickHold(this, level); return; }
        if (casting != null) { tickCast(level); return; }
        if (chunkAge >= 0 || blurLeg >= 0 || reelAge >= 0) { getNavigation().stop(); return; }
        if (shoveIn > 0) shoveIn--;
        LivingEntity near = nearest(fighters);
        if (near != null && !still && shoveIn == 0 && distanceTo(near) <= SylarGeometry.SHOVE_REACH) { SylarMoves.shove(this, level, near); shoveIn = SylarGeometry.SHOVE_EVERY; }
        // A dampener round or field: his cases go quiet, and only his hands are left.
        boolean dampened = com.jayptucker.heroesevolved.dampener.Dampening.dampened(this);
        if (rest > 0) rest--;
        else if (!dampened) choose(level, fighters);
        if (!still) walk(near);
    }

    private LivingEntity nearest(List<ServerPlayer> fighters) {
        LivingEntity best = null;
        for (ServerPlayer p : fighters) if (best == null || distanceToSqr(p) < distanceToSqr(best)) best = p;
        return best;
    }

    /** He keeps to the middle distance: close enough to reach, far enough to watch. */
    private void walk(LivingEntity near) {
        if (near == null) return;
        getLookControl().setLookAt(near, 30, 30);
        double d = distanceTo(near);
        if (d > 9) getNavigation().moveTo(near, 1.0);
        else if (d < 4) getNavigation().stop();
    }

    private void choose(ServerLevel level, List<ServerPlayer> fighters) {
        List<SylarMoves.Move> options = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        int total = 0;
        for (SylarMoves.Move move : SylarMoves.ALL) {
            if (move == last && SylarMoves.ALL.size() > 1) continue;
            if (cooldowns.getOrDefault(move.id(), 0) > tickCount) continue;
            int weight = move.weight(this, fighters);
            if (weight <= 0) continue;
            options.add(move); weights.add(weight); total += weight;
        }
        if (options.isEmpty()) { rest = 10; return; }
        int roll = random.nextInt(total);
        SylarMoves.Move move = options.getLast();
        for (int i = 0; i < options.size(); i++) { roll -= weights.get(i); if (roll < 0) { move = options.get(i); break; } }
        start(level, move);
    }

    /** Starts winding {@code move} up. Returns false if it found nothing to aim at. */
    boolean start(ServerLevel level, SylarMoves.Move move) {
        spots.clear(); lines.clear(); focus = null;
        if (!move.start().run(this, level)) { rest = 10; return false; }
        casting = move; castTicks = 0;
        flag(CASTING, true);
        pose(move.pose());
        getNavigation().stop();
        int hour = move.caseHour(this);
        drawing(hour);
        sound(position(), SoundEvents.NOTE_BLOCK_HAT.value(), 0.8F, 0.7F);
        return true;
    }

    private void tickCast(ServerLevel level) {
        // Winding up, he does nothing else: no fists while the schematic assembles.
        getNavigation().stop();
        if (focus != null) getLookControl().setLookAt(focus, 30, 30);
        castTicks++;
        casting.pulse().run(this, level);
        // A watchmaker's ticking, faster as the parts seat.
        if (castTicks % Math.max(2, 6 - (int) (castProgress() * 4)) == 0)
            sound(position(), SoundEvents.NOTE_BLOCK_HAT.value(), 0.45F, 0.9F + castProgress() * 0.9F);
        if (castTicks < casting.windup()) return;
        SylarMoves.Move move = casting;
        endCast();
        cooldowns.put(move.id(), tickCount + move.cooldown());
        last = move;
        move.land().run(this, level);
        rest = Math.max(rest, REST[phase()]);
    }

    /** Stops the windup (the cooldown still runs, half as long). */
    void cancelCast() {
        if (casting != null) cooldowns.put(casting.id(), tickCount + casting.cooldown() / 2);
        if (casting != null) casting.cancel().run(this, (ServerLevel) level());
        endCast();
    }

    private void endCast() {
        casting = null; castTicks = 0;
        flag(CASTING, false);
        drawing(-1);
        if (held == null) pose(Pose.NONE);
    }

    void spend(int ticks) { spent = Math.max(spent, ticks); flag(SPENT, true); pose(Pose.KNEEL); }

    void staggerFor(int ticks) {
        stagger = Math.max(stagger, ticks); flag(STAGGERED, true);
        pose(Pose.STAGGER);
    }

    /** Players high above the floor, far off, or hidden from him: Reel In's targets. */
    private void trackUnseen(List<ServerPlayer> fighters) {
        if (fightTicks % 5 != 0) return;
        for (ServerPlayer p : fighters) unseen.merge(p.getUUID(), hasLineOfSight(p) ? -1000 : 5, (a, b) -> Math.max(0, a + b));
    }

    int unseen(Player p) { return unseen.getOrDefault(p.getUUID(), 0); }
    void seen(Player p) { unseen.remove(p.getUUID()); }

    /** Kept inside his hall: if something throws him out of it, he steps back onto the dais. */
    private void leash() {
        if (!Double.isNaN(hoverY)) return;
        Vec3 c = SylarGeometry.center();
        if (SylarGeometry.onFloor(position()) && getY() > c.y - 3 && getY() < c.y + 12) return;
        teleportTo(c.x, c.y, c.z);
        setDeltaMovement(Vec3.ZERO); fallDistance = 0;
        AbilityEffects.send((ServerLevel) level(), Kind.TK_WAVE, position().add(0, 1, 0), position().add(0, 2, 0), 1.5);
    }

    /** Nobody has hurt him for a while and he sees no one: he mends. */
    private void idleHeal(ServerLevel level, List<ServerPlayer> fighters) {
        if (fightTicks - lastHurt < IDLE_TICKS || fightTicks % 20 != 0 || getHealth() >= getMaxHealth()) return;
        for (ServerPlayer p : fighters) if (hasLineOfSight(p)) return;
        heal(getMaxHealth() * IDLE_HEAL);
        AbilityEffects.send(level, Kind.TK_WAVE, position(), getEyePosition(), 0.6);
    }

    // ------------------------------------------------------------------
    // The cases
    // ------------------------------------------------------------------

    /** Whether hour {@code h}'s glass may be broken now: the case he draws from, a stolen power, Claire's while he is down. */
    boolean open(int h) {
        if (dying >= 0) return true;
        if (stolenPower[h] != null) return true;
        if (h == drawing()) return true;
        if (echoes.containsValue(h)) return true;
        return downed >= 0 && h == SylarGeometry.REGENERATION_CASE;
    }

    private void pollCases(ServerLevel level) {
        int mask = cases();
        for (int h = 0; h < 12; h++) {
            BlockPos glass = SylarGeometry.caseGlass(h);
            boolean whole = level.getBlockState(glass).is(Blocks.GLASS) && level.getBlockState(glass.above()).is(Blocks.GLASS);
            if (whole) continue;
            boolean holds = (mask >> h & 1) != 0;
            if (!holds || !open(h)) {
                // Locked: time doesn't touch his collection. The glass is whole again before it can fall.
                level.setBlock(glass, Blocks.GLASS.defaultBlockState(), 3);
                level.setBlock(glass.above(), Blocks.GLASS.defaultBlockState(), 3);
                if (holds) SetPiecePayload.send(level, Piece.SYLAR_LOCK, Vec3.atCenterOf(glass), Vec3.atCenterOf(glass).add(0, 1, 0), 1, null);
                continue;
            }
            shatter(level, h);
        }
    }

    /** Hour {@code h}'s case is broken while open: what it held is gone, and he pays for it. */
    void shatter(ServerLevel level, int h) {
        BlockPos glass = SylarGeometry.caseGlass(h);
        level.destroyBlock(glass, false);
        level.destroyBlock(glass.above(), false);
        entityData.set(CASES, cases() & ~(1 << h));
        Vec3 at = SylarGeometry.specimen(h);
        sound(at, SoundEvents.GLASS_BREAK, 1.6F, 0.7F);
        dissolveEchoes(level, h);
        if (stolenPower[h] != null) {
            // It was yours: it goes back where it came from.
            UUID owner = stolenFrom[h];
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
            if (player == null) for (ServerPlayer p : party) if (p.getUUID().equals(owner)) player = p;
            CollectionArena.unsteal(owner);
            if (player != null) {
                SetPiecePayload.sendSeeded(level, Piece.SYLAR_RELEASE, at, player.getBoundingBox().getCenter(), 1, player, 0, colour(stolenPower[h]));
                SylarVoice.warn(player, "story.heroesevolved.sylar.warn.returned");
            }
            stolenPower[h] = null; stolenFrom[h] = null;
            syncStolen();
            return;
        }
        if (dying >= 0) return;
        if (downed >= 0 && h == SylarGeometry.REGENERATION_CASE) {
            // Claire's gift, gone before it could bring him back.
            for (ServerPlayer p : bar.getPlayers()) SylarVoice.narrate(p, "story.heroesevolved.sylar.narrate.claire_broken");
            downed = -1; flag(DOWNED, false);
            startDying(level);
            return;
        }
        if (h == drawing()) backfire(level, h);
    }

    /** The case he was drawing from breaks under his hand: the power lashes back into him. */
    private void backfire(ServerLevel level, int h) {
        cancelCast();
        Vec3 at = SylarGeometry.specimen(h);
        SetPiecePayload.sendSeeded(level, Piece.SYLAR_BACKFIRE, at, getBoundingBox().getCenter(), 1, this, 0, colour(SylarGeometry.CASES.get(h)));
        CameraShakePayload.sendAround(level, position(), 32, 0.5F, 10);
        sound(position(), SoundEvents.GENERIC_EXPLODE.value(), 0.7F, 1.3F);
        bypass = true;
        try { hurt(damageSources().magic(), getMaxHealth() * BACKFIRE); } finally { bypass = false; }
        staggerFor(BACKFIRE_TICKS);
        for (ServerPlayer p : bar.getPlayers()) {
            SylarVoice.warn(p, "story.heroesevolved.sylar.warn.backfire");
            SylarVoice.say(p, "story.heroesevolved.sylar.say.backfire");
        }
        updateBarName();
    }

    /** What he took from {@code player} goes into the first empty case. Returns the case's hour, or -1 if none is free. */
    int keep(ServerLevel level, ServerPlayer player, ResourceLocation power) {
        for (int h : SylarGeometry.EMPTY_CASES) {
            if (stolenPower[h] != null) continue;
            stolenPower[h] = power; stolenFrom[h] = player.getUUID();
            BlockPos glass = SylarGeometry.caseGlass(h);
            level.setBlock(glass, Blocks.GLASS.defaultBlockState(), 3);
            level.setBlock(glass.above(), Blocks.GLASS.defaultBlockState(), 3);
            entityData.set(CASES, cases() | 1 << h);
            syncStolen();
            updateBarName();
            return h;
        }
        return -1;
    }

    boolean freeCase() {
        for (int h : SylarGeometry.EMPTY_CASES) if (stolenPower[h] == null) return true;
        return false;
    }

    private void syncStolen() {
        int packed = 0;
        for (int k = 0; k < SylarGeometry.EMPTY_CASES.length; k++) {
            ResourceLocation power = stolenPower[SylarGeometry.EMPTY_CASES[k]];
            if (power != null) packed |= (colour(power) + 1) << (7 * k);
        }
        entityData.set(STOLEN, packed);
    }

    static int colour(ResourceLocation power) { return power == null ? MimicColors.index("telekinesis") : MimicColors.index(power.getPath()); }

    /** How many cases still hold something, of how many were filled. */
    private int standing() { return Integer.bitCount(cases()); }

    // ------------------------------------------------------------------
    // The bar and phases
    // ------------------------------------------------------------------

    private void tickBar(ServerLevel level) {
        bar.setProgress(Math.clamp(getHealth() / getMaxHealth(), 0, 1));
        if (fightTicks % 10 != 0) return;
        Set<ServerPlayer> near = new HashSet<>();
        for (ServerPlayer p : level.players()) if (!p.isSpectator() && p.distanceToSqr(this) < 64 * 64) near.add(p);
        for (ServerPlayer p : party) if (p.level() == level && !p.isRemoved()) near.add(p);
        for (ServerPlayer p : List.copyOf(bar.getPlayers())) if (!near.contains(p)) bar.removePlayer(p);
        for (ServerPlayer p : near) bar.addPlayer(p);
        updateBarName();
    }

    void updateBarName() {
        String phase = dying >= 0 ? "end" : String.valueOf(phase());
        bar.setName(Component.translatable("story.heroesevolved.sylar.bar." + phase, standing()));
    }

    /** Returns true while a phase change holds the fight. */
    private boolean tickPhase(ServerLevel level) {
        float fraction = getHealth() / getMaxHealth();
        int next = fraction > PHASE_TWO ? 1 : fraction > PHASE_THREE ? 2 : 3;
        if (next <= phase()) return false;
        entityData.set(PHASE, next);
        cancelCast();
        if (held != null) SylarMoves.release(this, level, true);
        bar.setColor(next == 2 ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.YELLOW);
        updateBarName();
        transition = next == 2 ? TRANSITION_TWO : TRANSITION_THREE;
        pose(Pose.RAISED);
        getNavigation().stop();
        // A shove clears the room round him as the fight changes.
        SetPiecePayload.send(level, Piece.SYLAR_PHASE, position(), getEyePosition(), next, this);
        for (LivingEntity near : foesNear(position(), 8))
            push(near, flat(near.position().subtract(position())).scale(1.3).add(0, 0.45, 0));
        sound(position(), SoundEvents.WARDEN_SONIC_BOOM, 0.6F, 0.6F);
        sound(position(), SoundEvents.BELL_RESONATE, 1.4F, next == 2 ? 0.7F : 0.5F);
        CameraShakePayload.sendAround(level, position(), 48, 0.7F, 16);
        if (next == 2) CollectionArena.lights(level, false);
        if (next == 3) CollectionArena.raiseCover(level);
        summonEchoes(level, echoes(next, (int) Math.max(1, party.stream().filter(SylarEntity::counts).count())));
        for (ServerPlayer p : bar.getPlayers()) {
            SylarVoice.say(p, "story.heroesevolved.sylar.say.phase" + next);
            SylarVoice.warn(p, "story.heroesevolved.sylar.warn.phase" + next);
        }
        rest = Math.max(rest, 20);
        return true;
    }

    // ------------------------------------------------------------------
    // Taking damage
    // ------------------------------------------------------------------

    /** Set while a designed counter (a backfire) hurts him: it goes through the caps. */
    private boolean bypass;

    @Override public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && actor() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            // He is here to talk. A hand moves, and whoever struck at him is somewhere else.
            if (source.getEntity() instanceof LivingEntity attacker)
                push(attacker, flat(attacker.position().subtract(position())).scale(1.2).add(0, 0.4, 0));
            return false;
        }
        if (level().isClientSide || !fighting()) return super.hurt(source, amount);
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        if (dying >= 0 || downed >= 0 || transition > 0) return false;
        if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.DROWN)
                || source.is(DamageTypes.CRAMMING) || source.is(DamageTypes.FLY_INTO_WALL)) return false;
        boolean charged = charged(source, amount);   // read on every hit, so a swing never carries over to the next
        boolean byPlayer = source.getEntity() instanceof Player;
        if (!bypass) {
            if (stagger > 0) amount *= STAGGER_BONUS;
            if (spent > 0) amount *= SPENT_BONUS;
            if (!byPlayer) amount = Math.min(amount, NON_PLAYER_CAP);
            amount *= falloff(source);
            amount = Math.min(amount, Math.max(HIT_FLOOR, getMaxHealth() * HIT_CAP));
            // A first blow may take the full per-hit cap; after it, no twenty ticks take more than the per-second cap.
            float recentSum = recentDamage();
            float allowed = recent.isEmpty() ? amount : getMaxHealth() * SECOND_CAP - recentSum;
            amount = Math.max(0, Math.min(amount, allowed));
            if (amount <= 0) return false;
        }
        boolean lethal = amount >= getHealth();
        if (lethal) amount = Math.max(0, getHealth() - 1);
        boolean landed = amount > 0 ? super.hurt(source, amount) : true;
        if (!landed) return false;
        lastHurt = fightTicks;
        if (!bypass) recent.addLast(new float[] {fightTicks, amount});
        if (byPlayer && source.getEntity() instanceof Player p) seen(p);
        // The counterplay: a charged hit while he winds up breaks his concentration.
        if (charged && (casting != null || held != null)) breakConcentration();
        else if (held != null && byPlayer) struggle += Math.max(1, amount / 2);
        if (lethal) {
            killingBlow = source;
            if (source.getEntity() instanceof Player p && killer == null) killer = p.getUUID();
            ServerLevel level = (ServerLevel) level();
            if (!revived && caseWhole(SylarGeometry.REGENERATION_CASE)) goDown(level);
            else startDying(level);
        }
        return true;
    }

    private float recentDamage() {
        while (!recent.isEmpty() && fightTicks - recent.peekFirst()[0] >= 20) recent.removeFirst();
        float sum = 0;
        for (float[] r : recent) sum += r[1];
        return sum;
    }

    /** Damage from far off fades: full within {@link #FALLOFF_FULL} of the attacker, nothing past {@link #FALLOFF_NONE}. */
    private float falloff(DamageSource source) {
        Entity attacker = source.getEntity();
        if (attacker == null || attacker.level() != level()) return 1;
        double d = attacker.distanceTo(this);
        if (d <= FALLOFF_FULL) return 1;
        return (float) Math.max(0, 1 - (d - FALLOFF_FULL) / (FALLOFF_NONE - FALLOFF_FULL));
    }

    /** A hit counts as charged: a full-strength swing, a critical arrow, or a blow of eight or more. */
    static boolean charged(DamageSource source, float amount) {
        if (source.getDirectEntity() instanceof AbstractArrow arrow) return arrow.isCritArrow();
        if (source.getEntity() instanceof Player player && source.getDirectEntity() == player && SylarEvents.fullSwing(player)) return true;
        return amount >= 8 && source.getEntity() instanceof Player;
    }

    /** A charged hit mid-windup: the schematic falls apart, and he reels. */
    void breakConcentration() {
        if (!(level() instanceof ServerLevel level)) return;
        cancelCast();
        if (held != null) SylarMoves.release(this, level, true);
        staggerFor(CONCENTRATION_TICKS);
        SetPiecePayload.send(level, Piece.SYLAR_STAGGER, getEyePosition(), getEyePosition().add(getLookAngle()), 1, this);
        sound(position(), SoundEvents.GLASS_BREAK, 1.1F, 1.6F);
        sound(position(), SoundEvents.BELL_RESONATE, 0.8F, 1.8F);
        CameraShakePayload.sendAround(level, position(), 24, 0.35F, 6);
        for (ServerPlayer p : bar.getPlayers()) SylarVoice.warn(p, "story.heroesevolved.sylar.warn.concentration");
    }

    // ------------------------------------------------------------------
    // Claire's gift, and the end
    // ------------------------------------------------------------------

    /** Claire's case still stands: he goes down instead of dying, and her gift draws him back up unless the case breaks. */
    private void goDown(ServerLevel level) {
        cancelCast();
        if (held != null) SylarMoves.release(this, level, false);
        downed = DOWNED_TICKS; flag(DOWNED, true); flag(FINISHABLE, false);
        drawing(SylarGeometry.REGENERATION_CASE);
        pose(Pose.KNEEL);
        getNavigation().stop();
        sound(position(), SoundEvents.PLAYER_DEATH, 1.0F, 0.8F);
        for (ServerPlayer p : bar.getPlayers()) {
            SylarVoice.warn(p, "story.heroesevolved.sylar.warn.claire");
            SylarVoice.narrate(p, "story.heroesevolved.sylar.narrate.down");
        }
    }

    private void tickDowned(ServerLevel level) {
        getNavigation().stop();
        if (--downed > 0) {
            if (downed % 10 == 0) AbilityEffects.send(level, Kind.REGEN_MEND, position(), getEyePosition(), 1.0);
            return;
        }
        downed = -1; flag(DOWNED, false); revived = true;
        drawing(-1);
        entityData.set(CASES, cases() & ~(1 << SylarGeometry.REGENERATION_CASE));
        setHealth(getMaxHealth() * REVIVE);
        pose(Pose.NONE);
        AbilityEffects.send(level, Kind.REGEN_REVIVE, position(), position().add(0, 1, 0), getBbHeight());
        sound(position(), SoundEvents.WARDEN_HEARTBEAT, 1.5F, 0.8F);
        for (ServerPlayer p : bar.getPlayers()) SylarVoice.say(p, "story.heroesevolved.sylar.say.revive");
        rest = 30;
    }

    /** The killing blow lands: two to three seconds in which the collection lets go of him, then he falls. */
    private void startDying(ServerLevel level) {
        if (dying >= 0) return;
        cancelCast();
        if (held != null) SylarMoves.release(this, level, false);
        SylarMoves.clearLanding(this, level);
        dying = DEATH_TICKS; flag(DYING, true);
        flag(FINISHABLE, false);
        dissolveEchoes(level, -1);
        hoverY = Double.NaN; setNoGravity(false);
        pose(Pose.KNEEL);
        getNavigation().stop();
        updateBarName();
        SetPiecePayload.sendSeeded(level, Piece.SYLAR_DEATH, SylarGeometry.center(), getEyePosition(), 1, this, 0, cases());
        sound(position(), SoundEvents.BELL_RESONATE, 2.0F, 0.4F);
        CameraShakePayload.sendAround(level, position(), 48, 0.4F, 30);
        for (ServerPlayer p : bar.getPlayers()) SylarVoice.say(p, "story.heroesevolved.sylar.say.defeat");
    }

    private void tickDying(ServerLevel level) {
        getNavigation().stop();
        setDeltaMovement(getDeltaMovement().multiply(0, 1, 0));
        if (dying == DEATH_TICKS / 2) {
            // Every case still standing gives up what it held.
            for (int h = 0; h < 12; h++) if (caseWhole(h)) shatter(level, h);
            CollectionArena.lights(level, true);
        }
        if (--dying > 0) return;
        dying = -1;
        bar.setVisible(false);
        setHealth(0);
        die(killingBlow != null ? killingBlow : damageSources().generic());
    }

    /** Who struck the killing blow first, if a player did. */
    public UUID killer() { return killer; }
    public boolean isDying() { return dying >= 0; }

    @Override public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide && fighting() && level() instanceof ServerLevel level)
            CollectionArena.defeated(level.getServer(), this, killer != null ? killer
                    : source.getEntity() instanceof Player p ? p.getUUID() : null);
    }

    // ------------------------------------------------------------------
    // Holding people still (Every Hour)
    // ------------------------------------------------------------------

    private void tickStopped(ServerLevel level) {
        if (stopped.isEmpty()) return;
        for (var it = stopped.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            Entity entity = level.getEntity(entry.getKey());
            if (entity == null) for (ServerPlayer p : party) if (p.getUUID().equals(entry.getKey())) entity = p;
            if (entity == null || tickCount >= entry.getValue().until() || !entity.isAlive()) { it.remove(); continue; }
            Vec3 at = entry.getValue().at();
            entity.setDeltaMovement(Vec3.ZERO); entity.fallDistance = 0;
            if (entity.position().distanceToSqr(at) > 0.01) SylarMoves.place(entity, at);
        }
    }

    // ------------------------------------------------------------------
    // Finishers at his thresholds
    // ------------------------------------------------------------------

    /** Just above a phase threshold, or in his last tenth: the moments a finisher can take him apart. */
    private boolean inFinishWindow() {
        if (!fighting() || dying >= 0 || downed >= 0 || transition > 0 || held != null) return false;
        float fraction = getHealth() / getMaxHealth();
        return switch (phase()) {
            case 1 -> fraction > PHASE_TWO && fraction <= PHASE_TWO + FINISH_WINDOW;
            case 2 -> fraction > PHASE_THREE && fraction <= PHASE_THREE + FINISH_WINDOW;
            default -> fraction <= FINISH_END;
        };
    }

    @Override public boolean finishWindow() { return is(FINISHABLE); }

    /**
     * A finisher lands on him in a window: before his last phase it bites a slice out of him and forces the next phase,
     * leaving him reeling once the transition is over; in his last tenth it is the killing blow (Claire's case, if it
     * still stands, still gets him back up).
     */
    @Override public void phaseFinished(ServerPlayer player) {
        if (!(level() instanceof ServerLevel level) || !fighting() || dying >= 0 || finishedAt == tickCount) return;
        finishedAt = tickCount;
        lastHurt = fightTicks;
        cancelCast();
        SetPiecePayload.send(level, Piece.SYLAR_STAGGER, getEyePosition(), getEyePosition().add(getLookAngle()), 1, this);
        sound(position(), SoundEvents.BELL_RESONATE, 1.4F, 0.5F);
        CameraShakePayload.sendAround(level, position(), 32, 0.6F, 12);
        if (phase() < 3) {
            float threshold = (phase() == 1 ? PHASE_TWO : PHASE_THREE) * getMaxHealth();
            setHealth(Math.max(1, Math.min(getHealth() - getMaxHealth() * FINISH_BITE, threshold - 0.5F)));
            pendingStagger = FINISH_STAGGER;
            for (ServerPlayer p : bar.getPlayers()) SylarVoice.say(p, "story.heroesevolved.sylar.say.finished");
            return;
        }
        if (killer == null) killer = player.getUUID();
        killingBlow = damageSources().playerAttack(player);
        setHealth(1);
        if (!revived && caseWhole(SylarGeometry.REGENERATION_CASE)) goDown(level);
        else startDying(level);
    }

    // ------------------------------------------------------------------
    // Echoes
    // ------------------------------------------------------------------

    private static final String[] HOURS = {"Twelve", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten", "Eleven"};
    public static final String ECHO = "heroesevolved_sylar_echo";

    /**
     * What is left of whoever a case's power was taken from steps out of it and fights for him: a gifted raider carrying
     * that power (its moves, sigil and colour come from the gifted mobs' own system). Bound to its case: break the case
     * and the echo fades with it. Never Claire's, Sprague's, or a case holding your power.
     */
    void summonEchoes(ServerLevel level, int count) {
        List<Integer> hours = new ArrayList<>();
        for (int h = 0; h < 12; h++) {
            ResourceLocation power = SylarGeometry.CASES.get(h);
            if (power == null || h == SylarGeometry.REGENERATION_CASE || h == SylarGeometry.RADIATION_CASE || !caseWhole(h)) continue;
            if (echoes.containsValue(h)) continue;
            hours.add(h);
        }
        java.util.Collections.shuffle(hours, new java.util.Random(random.nextLong()));
        for (int i = 0; i < Math.min(count, hours.size()); i++) {
            int h = hours.get(i);
            var mob = net.minecraft.world.entity.EntityType.VINDICATOR.create(level);
            if (mob == null) continue;
            double g = SylarGeometry.angle(h);
            Vec3 at = SylarGeometry.center().add(SylarGeometry.dx(g, 13), 0, SylarGeometry.dz(g, 13));
            mob.moveTo(at.x, at.y, at.z, (float) Math.toDegrees(g) + 180, 0);
            mob.getPersistentData().putBoolean(com.jayptucker.heroesevolved.gifted.GiftedMobs.ROLLED, true);
            mob.getPersistentData().putBoolean(com.jayptucker.heroesevolved.gifted.GiftedMobs.FOLLOWER, true);
            mob.getPersistentData().putBoolean(ECHO, true);
            mob.addTag(ECHO);
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), net.minecraft.world.entity.MobSpawnType.MOB_SUMMONED, null);
            mob.setPersistenceRequired();
            if (!level.addFreshEntity(mob)) continue;
            com.jayptucker.heroesevolved.gifted.GiftedMobs.bestow(mob, SylarGeometry.CASES.get(h), com.jayptucker.heroesevolved.gifted.Rarity.UNCOMMON,
                    true, com.jayptucker.heroesevolved.gifted.Gift.Origin.WILD, SylarGeometry.caseGlass(h), "Echo of " + HOURS[h] + " O'Clock");
            mob.setCustomNameVisible(true);
            mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 600, 0, false, false));
            echoes.put(mob, h);
            SetPiecePayload.sendSeeded(level, Piece.SYLAR_RELEASE, SylarGeometry.specimen(h), mob.getBoundingBox().getCenter(), 1, mob, 0,
                    colour(SylarGeometry.CASES.get(h)));
            sound(at, SoundEvents.EVOKER_PREPARE_SUMMON, 1.2F, 0.8F);
            ServerPlayer near = null;
            for (ServerPlayer p : fighters()) if (near == null || p.distanceToSqr(mob) < near.distanceToSqr(mob)) near = p;
            if (near != null) mob.setTarget(near);
        }
        if (count > 0 && !hours.isEmpty())
            for (ServerPlayer p : bar.getPlayers()) SylarVoice.warn(p, "story.heroesevolved.sylar.warn.echoes");
    }

    /** Echoes keep to the hall and a fighter; one that wanders off is set back by its case. */
    private void tickEchoes(ServerLevel level) {
        if (echoes.isEmpty() || fightTicks % 10 != 0) return;
        for (var it = echoes.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            var mob = entry.getKey();
            if (mob.isRemoved() || !mob.isAlive()) { it.remove(); continue; }
            if (!SylarGeometry.onFloor(mob.position()) || mob.getY() < SylarGeometry.floorY() - 3) {
                double g = SylarGeometry.angle(entry.getValue());
                Vec3 at = SylarGeometry.center().add(SylarGeometry.dx(g, 13), 0, SylarGeometry.dz(g, 13));
                mob.teleportTo(at.x, at.y, at.z);
            }
            if (!(mob.getTarget() instanceof Player target) || !target.isAlive()) {
                ServerPlayer near = null;
                for (ServerPlayer p : fighters()) if (near == null || p.distanceToSqr(mob) < near.distanceToSqr(mob)) near = p;
                if (near != null) mob.setTarget(near);
            }
        }
    }

    /** The echoes bound to case {@code hour} (all of them for -1) fade back into the dark. */
    void dissolveEchoes(ServerLevel level, int hour) {
        for (var it = echoes.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            if (hour >= 0 && entry.getValue() != hour) continue;
            var mob = entry.getKey();
            if (!mob.isRemoved()) {
                AbilityEffects.send(level, Kind.TK_WAVE, mob.position().add(0, 1, 0), mob.getEyePosition(), 1.0);
                SetPiecePayload.send(level, Piece.SYLAR_ARRIVE, mob.position(), mob.getEyePosition(), 1, null);
                mob.discard();
            }
            it.remove();
        }
    }

    /** The echoes standing now. */
    public List<net.minecraft.world.entity.Mob> echoes() { return List.copyOf(echoes.keySet()); }

    /** The case an echo is bound to, or -1. */
    public int echoCase(net.minecraft.world.entity.Mob echo) { return echoes.getOrDefault(echo, -1); }

    // ------------------------------------------------------------------
    // Housekeeping
    // ------------------------------------------------------------------

    @Override public boolean isPushable() { return !fighting() && super.isPushable(); }
    @Override public boolean causeFallDamage(float distance, float multiplier, DamageSource source) { return !fighting() && super.causeFallDamage(distance, multiplier, source); }
    @Override public void remove(RemovalReason reason) {
        bar.removeAllPlayers();
        for (var echo : List.copyOf(echoes.keySet())) if (!echo.isRemoved()) echo.discard();
        echoes.clear();
        if (level() instanceof ServerLevel level) SylarMoves.clearLanding(this, level);
        held = null; stopped.clear();
        super.remove(reason);
    }
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double distance) { return distance < 128 * 128; }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override protected boolean shouldDespawnInPeaceful() { return false; }
    @Override public Component getName() { return hasCustomName() ? super.getName() : Component.translatable("entity.heroesevolved.sylar"); }

    /** For tests and previews: start a move by id now (it aims at whoever it would). */
    public boolean castNow(String id) {
        if (!(level() instanceof ServerLevel level)) return false;
        for (SylarMoves.Move move : SylarMoves.ALL) if (move.id().equals(id)) {
            cancelCast();
            return start(level, move);
        }
        return false;
    }

    /** For tests: break the glass of hour {@code h}'s case as a player's fist would. */
    public void breakCase(int h) {
        if (level() instanceof ServerLevel level) {
            level.destroyBlock(SylarGeometry.caseGlass(h), false);
            pollCases(level);
        }
    }

    /** For tests: make hour {@code h}'s case hold its power again, as if the story had given it to him. */
    public void fill(int h, boolean full) { entityData.set(CASES, full ? cases() | 1 << h : cases() & ~(1 << h)); }
}
