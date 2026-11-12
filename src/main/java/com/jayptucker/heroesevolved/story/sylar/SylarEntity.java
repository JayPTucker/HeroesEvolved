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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.ScreenTintPayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.styles.Piece;
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

    // The Watchmaker's shop (WatchmakerArena) and his moves there (WatchmakerMoves).
    /** How long his grab holds you before he takes your power; how long a cracked watch, and a ring of stopped time, last. */
    public static final int HOLD_TICKS = 60, STAGGER_TICKS = 60, STOPPED_TICKS = 40;
    /** How hard you must struggle to tear free: a sneak press counts one, a hit on him half its damage. */
    public static final float STRUGGLE_NEEDED = 6;
    private static final ResourceLocation HUNGER_SPEED = ResourceLocation.fromNamespaceAndPath("heroesevolved", "sylar_hunger_speed");
    private boolean arena, fleeing, heldShift;
    private int stagger, cracks, chaseLeg, chaseTicks, holdTicks, ringAge = -1;
    private Vec3 mark2 = Vec3.ZERO, chaseCheck = Vec3.ZERO, ringCenter = Vec3.ZERO;
    private float gap, struggle;
    private UUID held;
    private final Set<ResourceLocation> taken = new HashSet<>();
    private final Set<UUID> ringHit = new HashSet<>();
    private record Stopped(Vec3 at, int until) {}
    private final Map<UUID, Stopped> stopped = new HashMap<>();
    private List<net.minecraft.core.BlockPos> wall = List.of();
    private int wallIn;

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

    /** Continue the same fight when the original quarry leaves an expedition. */
    public void retarget(ServerPlayer player) {
        interrupt();
        quarry = player.getUUID();
        setTarget(player);
    }

    public boolean hunting() { return hunting; }
    public boolean extracting() { return entityData.get(EXTRACTING); }
    public int extractionTicks() { return extractionTicks; }
    public int phase() { return phase; }
    public SylarPowers.Pose castPose() { return SylarPowers.Pose.values()[Math.clamp(entityData.get(POSE), 0, SylarPowers.Pose.values().length - 1)]; }
    public String casting() { return casting == null ? "" : casting.id(); }
    /** For scenes in the past that move him by hand (Night Three): how he holds himself, so a windup reads. */
    public void scenePose(SylarPowers.Pose pose) { entityData.set(POSE, pose.ordinal()); }
    /** For previews: he picks no move of his own for this long. */
    public void holdOff(int ticks) { globalCooldown = Math.max(globalCooldown, ticks); }
    public boolean downed() { return downedTicks > 0; }
    public boolean revived() { return revived; }
    /** Ticks until he runs (0 or less when he is not about to); and how long this fight has gone on. */
    public int escapeIn() { return escapeIn; }
    public int combatTicks() { return combatTicks; }
    public ResourceLocation borrowed() { return borrowed; }
    ServerBossEvent bar() { return bar; }
    void arena(boolean arena) { this.arena = arena; }
    /** He is fighting in his shop. */
    public boolean arena() { return arena; }
    /** He is running for the alley (the last tenth of his health, in his shop). */
    public boolean fleeing() { return fleeing; }
    /** Reeling from a cracked watch. */
    public boolean staggered() { return stagger > 0; }
    public int cracks() { return cracks; }
    /** Who he has in his grip, if anyone. */
    public UUID holding() { return held; }
    public float struggle() { return struggle; }
    public int ringAge() { return ringAge; }
    /** Caught in a ring of stopped time. */
    public boolean timeStopped(Entity entity) { Stopped s = stopped.get(entity.getUUID()); return s != null && tickCount < s.until(); }
    /** Powers he has taken from people in this fight (he has them until it ends). */
    public Set<ResourceLocation> taken() { return Set.copyOf(taken); }
    /** His watch shows while he winds up a move or holds someone: the moment a charged hit can crack it. */
    public boolean exposed() { return casting != null || held != null; }
    void mark2(Vec3 at) { mark2 = at; }
    Vec3 mark2() { return mark2; }
    void gap(float gap) { this.gap = gap; }
    public float gap() { return gap; }
    float castProgress() { return casting == null ? 0 : Math.min(1, castTicks / (float) Math.max(1, casting.windup())); }

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
        known.addAll(taken);
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
        if (victim == null || victim.level() != level() || !(SylarStoryService.eligible(victim)
                || com.jayptucker.heroesevolved.story.LastSignalService.targetable(victim))) {
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
        if (arena && combatTicks % 20 == 1) SetPiecePayload.send(serverLevel, Piece.WATCH_ARENA, WatchmakerArena.center(),
                Vec3.atCenterOf(WatchmakerArena.cell(14, 7, 41)), phase + (fleeing ? 10 : 0), null);
        tickStopped(serverLevel);
        tickRings(serverLevel);
        tickWall(serverLevel);
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
        if (stagger > 0) { tickStagger(serverLevel); return; }
        if (fleeing) { tickChase(serverLevel, victim, story); return; }
        if (held != null) { tickHold(serverLevel); return; }
        if (recovery > 0) { recovery--; setTarget(null); return; }
        boolean inReach = distanceToSqr(victim) <= 7 * 7 && hasLineOfSight(victim);
        if (extractionTicks > 0 && !arena) {
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
        // In his shop the grab takes the place of the slow channel.
        if (!arena && inReach && combatTicks >= 80) {
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
        List<SylarPowers.Move> pool = new ArrayList<>(SylarPowers.available(known(level)));
        if (arena) pool.addAll(WatchmakerMoves.available(phase));
        for (SylarPowers.Move move : pool) {
            if (cooldowns.getOrDefault(move.id(), 0) > tickCount || distance < move.minRange() || distance > move.maxRange()
                    || move.needsSight() && !sight) continue;
            int weight = WatchmakerMoves.ALL.contains(move) ? WatchmakerMoves.weight(move, phase)
                    : move.power().equals(theirs) ? 3 : move.power().equals(ModAbilities.TELEKINESIS_ID) ? 1 : 2;
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
        glint(level);
        if (move.power().equals(theirs) && !move.power().equals(ModAbilities.TELEKINESIS_ID) && random.nextFloat() < 0.35F)
            SylarVoice.say(victim, "story.heroesevolved.sylar.say.yours");
    }

    private void tickCast(ServerLevel level) {
        ServerPlayer victim = level.getServer().getPlayerList().getPlayer(quarry);
        if (victim == null) { cancelCast(); return; }
        // Winding up, he does nothing else: no fists while the telegraph runs.
        getNavigation().stop(); setTarget(null);
        castTicks++;
        if (castTicks % 4 == 0) casting.pulse().run(this, level, victim, power());
        if (castTicks % 10 == 0) glint(level);
        if (castTicks < casting.windup()) return;
        SylarPowers.Move move = casting;
        cancelCast();
        cooldowns.put(move.id(), tickCount + move.cooldown());
        if (!move.needsSight() || hasLineOfSight(victim)) move.execute().run(this, level, victim, power());
        // In his shop the clocks run faster once the doors lock.
        globalCooldown = phase == 1 ? 50 : phase == 2 ? (arena ? 26 : 32) : (arena ? 20 : 24);
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
        Vec3 from = position();
        teleportTo(near.x, near.y, near.z);
        // The wall ripples like water where he went in, and again where he steps out.
        AbilityEffects.send(level, Kind.DENSITY_RIPPLE, from.add(0, 1, 0), getEyePosition(), 1.1);
        AbilityEffects.send(level, Kind.DENSITY_RIPPLE, near.add(0, 1, 0), near.add(0, 1, 0).add(victim.position().subtract(near).normalize()), 1.1);
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
        // At a tenth, in his shop, he runs for the back door.
        if (arena && !fleeing && fraction <= CHASE_AT) startChase(level);
        int next = fraction > PHASE_TWO ? 1 : fraction > PHASE_THREE ? 2 : 3;
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
        if (arena && phase >= 2) {
            // The Hunger: the doors lock, and every clock in the shop runs faster. So does he.
            WatchmakerArena.lock(level);
            var speed = getAttribute(Attributes.MOVEMENT_SPEED);
            if (!speed.hasModifier(HUNGER_SPEED) && !fleeing)
                speed.addTransientModifier(new AttributeModifier(HUNGER_SPEED, 0.2, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            for (ServerPlayer witness : bar.getPlayers())
                SylarVoice.warn(witness, phase == 2 ? "story.heroesevolved.sylar.warn.doors" : "story.heroesevolved.sylar.warn.clockwork");
        }
        if (phase == 3 && !arena && !escapeTried && canEscape(story)) {
            escapeTried = true;
            escapeIn = 80;
            for (ServerPlayer witness : bar.getPlayers()) SylarVoice.warn(witness, "story.heroesevolved.sylar.warn.escape");
        }
    }

    /** Where the phases turn: The Hunger below three-fifths, Clockwork below three-tenths; the chase at a tenth. */
    public static final float PHASE_TWO = 0.6F, PHASE_THREE = 0.3F, CHASE_AT = 0.1F;

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
    // The Watchmaker: his watch, his grab, his rings, his run
    // ------------------------------------------------------------------

    /** The glint of his watch on his wrist while it shows. */
    private void glint(ServerLevel level) {
        SetPiecePayload.send(level, Piece.WATCH_EXPOSED, getEyePosition(), getEyePosition(), cracks, this);
    }

    /** A hit counts as charged: a full-strength swing, a critical arrow, or a blow of eight or more. */
    static boolean charged(DamageSource source, float amount) {
        if (source.getDirectEntity() instanceof AbstractArrow arrow) return arrow.isCritArrow();
        if (source.getEntity() instanceof Player player && source.getDirectEntity() == player && WatchmakerEvents.fullSwing(player)) return true;
        return amount >= 8 && source.getEntity() instanceof Player;
    }

    /** His watch cracks: whatever he was doing stops, and he reels for three seconds, taking more from every hit. */
    private void crack() {
        if (!(level() instanceof ServerLevel level)) return;
        cracks++;
        stagger = STAGGER_TICKS;
        cancelCast();
        pinned = null;
        if (held != null && level.getServer().getPlayerList().getPlayer(held) instanceof ServerPlayer victim) release(victim, true);
        held = null;
        getNavigation().stop();
        entityData.set(POSE, SylarPowers.Pose.STAGGER.ordinal());
        SetPiecePayload.send(level, Piece.WATCH_CRACK, getEyePosition(), getEyePosition().add(getLookAngle()), cracks, this);
        SylarPowers.sound(level, position(), SoundEvents.GLASS_BREAK, 1.2F, 1.4F);
        SylarPowers.sound(level, position(), SoundEvents.BELL_RESONATE, 0.8F, 1.8F);
        CameraShakePayload.sendAround(level, position(), 24, 0.35F, 6);
        for (ServerPlayer witness : bar.getPlayers()) SylarVoice.warn(witness, "story.heroesevolved.sylar.warn.crack");
        if (cracks == 1) for (ServerPlayer witness : bar.getPlayers()) SylarVoice.say(witness, "story.heroesevolved.sylar.say.cracked");
    }

    private void tickStagger(ServerLevel level) {
        getNavigation().stop(); setTarget(null);
        setDeltaMovement(getDeltaMovement().multiply(0.3, 1, 0.3));
        if (--stagger == 0) { entityData.set(POSE, 0); globalCooldown = Math.min(globalCooldown, 10); }
    }

    /** His lunge missed: he stumbles for a second. */
    void overreach() {
        stagger = Math.max(stagger, 20);
        entityData.set(POSE, SylarPowers.Pose.STAGGER.ordinal());
    }

    /** His lunge caught someone. He holds them in front of him; they have three seconds to tear free. */
    void grab(ServerPlayer player) {
        held = player.getUUID(); holdTicks = 0; struggle = 0; heldShift = player.isShiftKeyDown();
        pinned = null;
        entityData.set(POSE, SylarPowers.Pose.ONE_HAND.ordinal());
        SylarVoice.warn(player, "story.heroesevolved.sylar.warn.grab");
        if (level() instanceof ServerLevel level) {
            SylarPowers.sound(level, player.position(), SoundEvents.WARDEN_ATTACK_IMPACT, 1.0F, 0.7F);
            glint(level);
        }
    }

    /** Struggling against his grip: each sneak press counts one, and hits on him count too (anyone's). */
    public void struggle(float amount) {
        if (held == null) return;
        struggle += amount;
    }

    private void tickHold(ServerLevel level) {
        getNavigation().stop(); setTarget(null);
        ServerPlayer victim = level.getServer().getPlayerList().getPlayer(held);
        if (victim == null || !victim.isAlive() || victim.level() != level) { held = null; entityData.set(POSE, 0); return; }
        holdTicks++;
        Vec3 front = position().add(SylarPowers.flat(getLookAngle()).scale(1.15)).add(0, 0.35, 0);
        if (victim.position().distanceToSqr(front) > 0.04) victim.teleportTo(level, front.x, front.y, front.z, victim.getYRot(), victim.getXRot());
        victim.setDeltaMovement(Vec3.ZERO); victim.fallDistance = 0;
        boolean shift = victim.isShiftKeyDown();
        if (shift && !heldShift) struggle(1);
        heldShift = shift;
        if (holdTicks % 10 == 1) SetPiecePayload.send(level, Piece.WATCH_HOLD, front, getEyePosition(), struggle / STRUGGLE_NEEDED, victim);
        if (holdTicks % 10 == 0) glint(level);
        if (struggle >= STRUGGLE_NEEDED) { breakFree(level, victim); return; }
        if (holdTicks >= HOLD_TICKS) take(level, victim);
    }

    private void release(ServerPlayer victim, boolean shove) {
        held = null; holdTicks = 0; struggle = 0;
        entityData.set(POSE, 0);
        if (shove) SylarPowers.push(victim, SylarPowers.flat(victim.position().subtract(position())).scale(0.9).add(0, 0.3, 0));
    }

    private void breakFree(ServerLevel level, ServerPlayer victim) {
        release(victim, true);
        stagger = Math.max(stagger, 30);
        entityData.set(POSE, SylarPowers.Pose.STAGGER.ordinal());
        AbilityEffects.send(level, Kind.TK_WAVE, victim.getBoundingBox().getCenter(), getEyePosition(), 1.2);
        SylarPowers.sound(level, victim.position(), SoundEvents.CHAIN_BREAK, 1.2F, 0.7F);
        SylarVoice.warn(victim, "story.heroesevolved.sylar.warn.free");
    }

    /** Too slow: he takes your power for the rest of the fight (and learns it, as his channel would). */
    private void take(ServerLevel level, ServerPlayer victim) {
        release(victim, false);
        SylarPowers.hurt(this, victim, 6 * power());
        PlayerAbilityService.getData(victim).assignedPower().filter(e -> e.getValue().isUnlocked()).map(Map.Entry::getKey).ifPresent(power -> {
            if (SylarStoryService.outOfReach(level.getServer(), power) == null) taken.add(power);
        });
        WatchmakerArena.steal(victim);
        SetPiecePayload.send(level, Piece.WATCH_TAKE, victim.getBoundingBox().getCenter(), getEyePosition(), 1, this);
        SylarPowers.sound(level, victim.position(), SoundEvents.WARDEN_HEARTBEAT, 1.5F, 0.6F);
        ScreenTintPayload.send(victim, 0x50402018, 40, ScreenTintPayload.Style.VIGNETTE);
        SylarVoice.warn(victim, "story.heroesevolved.sylar.warn.taken");
        SylarVoice.say(victim, "story.heroesevolved.sylar.say.taken");
        SylarStoryService.acquire(this, victim);
        SylarPowers.push(victim, SylarPowers.flat(victim.position().subtract(position())).scale(0.6).add(0, 0.2, 0));
        globalCooldown = 40;
    }

    /** His ice wall goes up {@code delay} ticks from now. */
    void raiseWall(List<net.minecraft.core.BlockPos> cells, int delay) { wall = cells; wallIn = Math.max(1, delay); }

    private void tickWall(ServerLevel level) {
        if (wallIn <= 0 || --wallIn > 0) return;
        for (var cell : wall)
            com.jayptucker.heroesevolved.cryo.TemporaryBlocks.place(level, cell, net.minecraft.world.level.block.Blocks.PACKED_ICE.defaultBlockState(),
                    WatchmakerMoves.WALL_TICKS, null);
        wall = List.of();
    }

    /** The rings of stopped time start closing from beyond the walls. */
    void startRings() { ringAge = 0; ringHit.clear(); ringCenter = mark(); }

    private void tickRings(ServerLevel level) {
        if (ringAge < 0) return;
        double before = WatchmakerMoves.ringRadius(ringAge), after = WatchmakerMoves.ringRadius(++ringAge);
        // Its own middle: his next move's mark mustn't drag the rings across the shop.
        Vec3 c = ringCenter;
        for (LivingEntity foe : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(WatchmakerMoves.RING_START + 1, 4, WatchmakerMoves.RING_START + 1),
                e -> SylarPowers.foe(this, e))) {
            double dx = foe.getX() - c.x, dz = foe.getZ() - c.z, d = Math.hypot(dx, dz);
            if (ringHit.contains(foe.getUUID()) || d > before + 0.4 || d < after - 0.4) continue;
            ringHit.add(foe.getUUID());
            if (WatchmakerMoves.inGap(dx, dz, gap)) continue;
            SylarPowers.hurt(this, foe, 5 * power());
            stopped.put(foe.getUUID(), new Stopped(foe.position(), tickCount + STOPPED_TICKS));
            AbilityEffects.send(level, Kind.TIME_STOP_PULSE, foe.position(), foe.getEyePosition(), 1.0);
            if (foe instanceof ServerPlayer player) ScreenTintPayload.send(player, 0x605B3FD1, STOPPED_TICKS, ScreenTintPayload.Style.EDGE);
        }
        if (ringAge >= Piece.WATCH_RINGS.ticks) ringAge = -1;
    }

    /** Held still where the ring caught them, until the time runs out. */
    private void tickStopped(ServerLevel level) {
        if (stopped.isEmpty()) return;
        for (var it = stopped.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            Entity entity = level.getEntity(entry.getKey());
            if (entity == null || tickCount >= entry.getValue().until() || !entity.isAlive()) { it.remove(); continue; }
            Vec3 at = entry.getValue().at();
            entity.setDeltaMovement(Vec3.ZERO); entity.fallDistance = 0;
            if (entity.position().distanceToSqr(at) > 0.01) {
                if (entity instanceof ServerPlayer player) player.teleportTo(level, at.x, at.y, at.z, player.getYRot(), player.getXRot());
                else entity.teleportTo(at.x, at.y, at.z);
            }
        }
    }

    /** At a tenth of his health in his shop, he runs: the back door bursts and he makes for the end of the alley. */
    private void startChase(ServerLevel level) {
        fleeing = true; chaseLeg = 0; chaseTicks = 0; chaseCheck = position();
        cancelCast(); pinned = null; escapeIn = -1;
        if (held != null && level.getServer().getPlayerList().getPlayer(held) instanceof ServerPlayer victim) release(victim, true);
        held = null;
        WatchmakerArena.burstBack(level);
        var speed = getAttribute(Attributes.MOVEMENT_SPEED);
        speed.removeModifier(HUNGER_SPEED);
        bar.setName(Component.translatable("story.heroesevolved.sylar.bar.chase"));
        for (ServerPlayer witness : bar.getPlayers()) SylarVoice.warn(witness, "story.heroesevolved.sylar.warn.chase");
    }

    private void tickChase(ServerLevel level, ServerPlayer victim, SylarStoryData story) {
        setTarget(null);
        List<Vec3> route = WatchmakerArena.chaseRoute();
        chaseTicks++;
        if (chaseLeg >= route.size() || chaseTicks > 20 * 30) { escape(level, victim, story); return; }
        Vec3 next = route.get(chaseLeg);
        if (Math.hypot(next.x - getX(), next.z - getZ()) < 1.3) { chaseLeg++; return; }
        getNavigation().moveTo(next.x, next.y, next.z, 1.05);
        getLookControl().setLookAt(next.x, next.y + 1.5, next.z);
        // Stuck on something: he steps through it, the way a man who knows where he's going does.
        if (chaseTicks % 20 == 0) {
            if (position().distanceToSqr(chaseCheck) < 0.25) {
                Vec3 step = com.jayptucker.heroesevolved.speed.SpeedService.swept(this, SylarPowers.flat(next.subtract(position())).scale(1.5));
                teleportTo(step.x, step.y, step.z);
            }
            chaseCheck = position();
        }
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
        // Reeling from a cracked watch, he can't guard himself.
        if (stagger > 0) amount *= 1.25F;
        var story = SylarStoryData.get(level().getServer());
        boolean precog = story.knows(ModAbilities.PRECOGNITION_ID) || ModAbilities.PRECOGNITION_ID.equals(borrowed);
        if (tickCount >= nextPredictedDodge && source.getDirectEntity() instanceof Projectile && precog && stagger == 0 && !fleeing) {
            Vec3 facing = getLookAngle().multiply(1, 0, 1).normalize(), side = new Vec3(-facing.z, 0, facing.x);
            Vec3 end = com.jayptucker.heroesevolved.speed.SpeedService.swept(this, side.scale(2));
            if (end.distanceToSqr(position()) >= 2.25) {
                nextPredictedDodge = tickCount + 80;
                AbilityEffects.send((ServerLevel) level(), Kind.PROPHECY_BREAK, position(), end, 1.5);
                teleportTo(end.x, end.y, end.z); return false;
            }
        }
        // He saw that blow coming, too (but not twice in four seconds).
        if (hunting && precog && stagger == 0 && !fleeing && held == null && tickCount >= nextSidestep && source.getEntity() instanceof LivingEntity attacker
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
            // The counterplay: a charged hit while his watch shows cracks it, and he staggers.
            boolean charged = charged(source, amount);   // read every hit, so a swing never carries over to the next
            if (hunting && exposed() && stagger == 0 && charged) crack();
            else if (held != null) { if (source.getEntity() instanceof Player) struggle(Math.max(1, amount / 2)); }
            else if (extractionTicks > 0 || pinning() || casting != null && staggerDamageTaken >= 5) interrupt();
            SylarStoryService.provoked(this);
        }
        return hit;
    }

    @Override public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide && !isAlive()) SylarStoryService.defeated(this, source.getEntity());
    }
    @Override public void remove(RemovalReason reason) { bar.removeAllPlayers(); held = null; stopped.clear(); super.remove(reason); }
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
        List<SylarPowers.Move> moves = new ArrayList<>(SylarPowers.MOVES);
        moves.addAll(WatchmakerMoves.ALL);
        for (SylarPowers.Move move : moves) if (move.id().equals(id)) {
            casting = move; castTicks = 0; staggerDamageTaken = 0;
            entityData.set(POSE, move.pose().ordinal());
            move.telegraph().run(this, level, target, power());
            glint(level);
            return true;
        }
        return false;
    }

}
