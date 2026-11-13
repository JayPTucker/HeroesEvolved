package com.jayptucker.heroesevolved.time;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.Targeting;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.effects.WorldMutationPolicy;
import com.jayptucker.heroesevolved.entity.TemporalEchoEntity;
import com.jayptucker.heroesevolved.expansion.ExpansionSkills;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.ScreenTintPayload;
import com.jayptucker.heroesevolved.network.TimeGhostPayload;
import com.jayptucker.heroesevolved.progression.MasteryService;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import com.jayptucker.heroesevolved.sounds.ModSounds;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;

/**
 * Space-Time Manipulation's skill tree, built around the kit rather than stat bonuses. Three branches:
 * <ul>
 *   <li><b>Stillness</b> (stopping things): Still Point, Selective Stasis, Compound Interest, Stasis Seal → Held Volley →
 *   Return to Sender.</li>
 *   <li><b>Wayfinder</b> (folding space): Behind You, Spatial Anchor, Snap Shut (and the Singularity under it), Moment's
 *   Edge → Long Edge → Thousand Cuts.</li>
 *   <li><b>Historian</b> (undoing and hurrying time): Foreknowledge, Second Chance, Shared Hour, Fast Forward → Wider Era
 *   → Era's End.</li>
 * </ul>
 * Stopped time is one idea throughout: a creature held by Still World, Selective Stasis, a Stasis Seal or Moment's Edge
 * stores the blows its holder lands ({@link TimeStopService#storeImpact}) and feels them all when it is let go. Node ids
 * are kept from the older tree, so purchases and prerequisites carry over.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class SpaceTimeSkills {
    public static final String STILL_POINT = "time_manipulation_0_0", COMPOUND = "time_manipulation_0_2",
            BEHIND_YOU = "time_manipulation_1_0", SNAP_SHUT = "time_manipulation_1_2",
            FOREKNOWLEDGE = "time_manipulation_2_0", SHARED_HOUR = "time_manipulation_2_2",
            HELD_VOLLEY = "time_manipulation_wider_seal", RETURN_TO_SENDER = "time_manipulation_return_to_sender",
            LONG_EDGE = "time_manipulation_far_fold", THOUSAND_CUTS = "time_manipulation_wayfinder",
            WIDER_ERA = "time_manipulation_era_radius", ERAS_END = "time_manipulation_eras_end";
    /** Free folds Still Point gives each Still World. */
    public static final int FREE_FOLDS = 3;

    private record Hold(UUID owner, long until) {}
    private record Seal(ServerPlayer owner, ServerLevel level, Vec3 center, double radius, long until,
                        Map<Projectile, Vec3> shots, Set<UUID> touched) {}
    private record Homing(ServerPlayer owner, Projectile shot, LivingEntity target, long until) {}
    private static final class Edge {
        final ServerPlayer owner; final List<LivingEntity> targets; final float damage; final int cuts; final boolean linger;
        int index; long next;
        Edge(ServerPlayer owner, List<LivingEntity> targets, float damage, int cuts, boolean linger, long next) {
            this.owner = owner; this.targets = targets; this.damage = damage; this.cuts = cuts; this.linger = linger; this.next = next;
        }
    }
    private record Blow(UUID who, long at, float amount) {}
    private record Marks(Set<UUID> targets, long until) {}
    private record Behind(UUID target, long until) {}

    /** Creatures stopped by a seal or Moment's Edge (Selective Stasis keeps its own single target). */
    private static final Map<UUID, Hold> HELD = new HashMap<>();
    private static final Map<UUID, Integer> FOLDS = new HashMap<>();
    private static final Map<UUID, Seal> SEALS = new HashMap<>();
    private static final List<Homing> HOMING = new ArrayList<>();
    private static final Map<UUID, Edge> EDGES = new HashMap<>();
    private static final Map<UUID, Behind> BEHIND = new HashMap<>();
    /** Who hurt each traveller recently (Foreknowledge), and the blows each traveller landed (Era's End). */
    private static final Map<UUID, Deque<Blow>> HURT_BY = new HashMap<>(), DEALT = new HashMap<>();
    private static final Map<UUID, Marks> MARKS = new HashMap<>();
    private static boolean replaying;

    private SpaceTimeSkills() {}

    public static boolean has(ServerPlayer p, String node) { return SkillTreeService.has(p, node); }
    private static long now(ServerPlayer p) { return p.getServer().overworld().getGameTime(); }
    private static long now(MinecraftServer server) { return server.overworld().getGameTime(); }
    private static boolean hostile(ServerPlayer p, LivingEntity t) {
        return ExpansionSkills.hostile(p, t) && !TemporalEntityEchoService.isReplaying(t);
    }

    // ------------------------------------------------------------------
    // Held creatures: the one rule every stop shares
    // ------------------------------------------------------------------

    /** Who holds this creature in a seal or Moment's Edge, or null. */
    public static UUID holder(Entity entity) {
        if (HELD.isEmpty()) return null;
        Hold hold = HELD.get(entity.getUUID());
        return hold == null ? null : hold.owner;
    }

    static void hold(ServerPlayer owner, LivingEntity target, int ticks) {
        long until = now(owner) + ticks;
        HELD.merge(target.getUUID(), new Hold(owner.getUUID(), until),
                (old, fresh) -> old.owner.equals(fresh.owner) && old.until > fresh.until ? old : fresh);
        target.setDeltaMovement(Vec3.ZERO); target.hurtMarked = true;
        TimeGeometry.show(owner.serverLevel(), Piece.TIME_STASIS, target.position(), target.getEyePosition(), target.getBbHeight(), target);
    }

    /** Lets a held creature go: the blows its holder stored land at once. */
    static void letGo(MinecraftServer server, LivingEntity target) {
        Hold hold = HELD.remove(target.getUUID());
        if (hold != null) TimeStopService.release(server, hold.owner, target);
    }

    // ------------------------------------------------------------------
    // Stillness
    // ------------------------------------------------------------------

    /** Still Point: a Still World gives three folds that cost nothing. */
    public static void stillWorldStarted(ServerPlayer p) {
        if (has(p, STILL_POINT)) FOLDS.put(p.getUUID(), FREE_FOLDS);
    }

    public static boolean freeFold(ServerPlayer p) {
        return FOLDS.getOrDefault(p.getUUID(), 0) > 0 && TimeStopService.hasActiveField(p);
    }

    /** Compound Interest: each stored blow adds a tenth to them all, up to 60%. */
    public static float compound(boolean learned, int hits) {
        return learned ? 1 + Math.min(.6F, .1F * hits) : 1;
    }

    public static float storeCap(ServerPlayer owner, LivingEntity target) {
        float cap = target instanceof ServerPlayer ? 12 : 40;
        return has(owner, COMPOUND) ? cap * 1.5F : cap;
    }

    /**
     * Stasis Seal: a sphere of stopped time where you aim. Shots that fly in hang frozen, and creatures that walk in stop
     * for two seconds, once each.
     */
    static boolean seal(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        var hit = level.clip(new ClipContext(eye, eye.add(look.scale(TimeGeometry.SEAL_REACH)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        double radius = has(p, HELD_VOLLEY) ? TimeGeometry.SEAL_WIDE : TimeGeometry.SEAL_RADIUS;
        Vec3 center = hit.getType() == HitResult.Type.BLOCK
                ? hit.getLocation().subtract(look.scale(Math.min(radius * .6, eye.distanceTo(hit.getLocation()) * .5)))
                : eye.add(look.scale(12));
        Seal old = SEALS.put(p.getUUID(), new Seal(p, level, center, radius, now(p) + TimeGeometry.SEAL_TICKS, new LinkedHashMap<>(), new HashSet<>()));
        if (old != null) resume(old);
        TimeGeometry.show(level, Piece.TIME_SEAL, center, center, (float) radius, null);
        ArmPosePayload.show(p, ArmPose.TIME_FRAME, 14);
        level.playSound(null, center.x, center.y, center.z, ModSounds.TIME_SLOW_STOP.get(), SoundSource.PLAYERS, .7F, 1.35F);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1, .6F);
        return true;
    }

    public static boolean sealing(ServerPlayer p) { return SEALS.containsKey(p.getUUID()); }

    /** The seal thrown: everything inside flies where you look. Free (a follow-up). */
    static boolean throwSeal(ServerPlayer p) {
        Seal seal = SEALS.remove(p.getUUID());
        if (seal == null) return false;
        boolean sender = has(p, RETURN_TO_SENDER);
        Vec3 aim = p.getLookAngle().normalize();
        LivingEntity mark = sender ? Targeting.livingEntityInSight(p, 48, t -> hostile(p, t)) : null;
        for (var entry : seal.shots.entrySet()) {
            Projectile shot = entry.getKey();
            if (shot.isRemoved()) continue;
            shot.setNoGravity(false);
            Vec3 from = shot.position(), heading = aim;
            if (mark != null) heading = mark.getBoundingBox().getCenter().subtract(from).normalize();
            if (sender) shot.setOwner(p);
            shot.setDeltaMovement(heading.scale(entry.getValue().length() * (sender ? 1.5 : 1) + .5));
            shot.hurtMarked = true;
            if (mark != null) HOMING.add(new Homing(p, shot, mark, now(p) + 40));
        }
        MinecraftServer server = p.getServer();
        for (UUID id : seal.touched) {
            Hold hold = HELD.get(id);
            if (hold == null || !hold.owner.equals(p.getUUID())) continue;
            if (seal.level.getEntity(id) instanceof LivingEntity creature && creature.isAlive()) {
                letGo(server, creature);
                push(creature, aim.scale(1.4).add(0, .35, 0));
            } else HELD.remove(id);
        }
        TimeGeometry.show(seal.level, Piece.TIME_SEAL_BREAK, seal.center, seal.center.add(aim.scale(6)), (float) seal.radius, null);
        ArmPosePayload.show(p, ArmPose.TIME_THROW, 12);
        seal.level.playSound(null, seal.center.x, seal.center.y, seal.center.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1, .7F);
        seal.level.playSound(null, seal.center.x, seal.center.y, seal.center.z, ModSounds.TIME_BLINK.get(), SoundSource.PLAYERS, .6F, 1.4F);
        return true;
    }

    /** A seal that ran out lets its shots go on as they were. */
    private static void resume(Seal seal) {
        for (var entry : seal.shots.entrySet()) {
            Projectile shot = entry.getKey();
            if (shot.isRemoved()) continue;
            shot.setNoGravity(false); shot.setDeltaMovement(entry.getValue()); shot.hurtMarked = true;
        }
    }

    private static void tickSeal(Seal seal, long now) {
        ServerPlayer p = seal.owner;
        boolean own = has(p, HELD_VOLLEY);
        AABB box = new AABB(seal.center, seal.center).inflate(seal.radius);
        double r2 = seal.radius * seal.radius;
        for (Projectile shot : seal.level.getEntitiesOfClass(Projectile.class, box, s -> s.position().distanceToSqr(seal.center) <= r2)) {
            if (seal.shots.size() >= TimeGeometry.SEAL_SHOTS) break;
            if (!own && shot.getOwner() == p) continue;
            seal.shots.computeIfAbsent(shot, Projectile::getDeltaMovement);
            shot.setDeltaMovement(Vec3.ZERO); shot.setNoGravity(true); shot.hurtMarked = true;
        }
        for (LivingEntity creature : seal.level.getEntitiesOfClass(LivingEntity.class, box,
                t -> hostile(p, t) && t.getBoundingBox().getCenter().distanceToSqr(seal.center) <= r2)) {
            if (seal.touched.add(creature.getUUID())) {
                hold(p, creature, TimeGeometry.SEAL_HOLD);
                seal.level.playSound(null, creature.blockPosition(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, .8F, .5F);
            }
        }
        if (now % 10 == 0) TimeGeometry.show(seal.level, Piece.TIME_SEAL, seal.center, seal.center, (float) seal.radius, null);
    }

    // ------------------------------------------------------------------
    // Wayfinder
    // ------------------------------------------------------------------

    /**
     * Behind You: where Fold Space lands when it is aimed at a creature, just behind it, or null when that spot isn't safe.
     */
    public static Vec3 behind(ServerPlayer p, double reach) {
        if (!has(p, BEHIND_YOU)) return null;
        LivingEntity target = Targeting.livingEntityInSight(p, reach, t -> hostile(p, t));
        if (target == null) return null;
        Vec3 spot = TimeGeometry.behind(target);
        if (!SpaceTimeService.safe(p, spot)) {
            // Its back is against a wall: come round the side away from you instead.
            Vec3 side = target.position().subtract(p.position()).multiply(1, 0, 1).normalize().scale(TimeGeometry.BEHIND);
            spot = target.position().add(side);
            if (!SpaceTimeService.safe(p, spot)) return null;
        }
        BEHIND.put(p.getUUID(), new Behind(target.getUUID(), now(p) + TimeGeometry.BEHIND_TICKS));
        return spot;
    }

    /** The creature Behind You just put you behind, for facing it. */
    public static LivingEntity behindTarget(ServerPlayer p) {
        Behind b = BEHIND.get(p.getUUID());
        return b != null && p.serverLevel().getEntity(b.target) instanceof LivingEntity t ? t : null;
    }

    /** Everything Fold Space sets off once you've moved: Still Point's count and Snap Shut. */
    public static void folded(ServerPlayer p, Vec3 from, Vec3 to, boolean free, Entity carried) {
        if (free) {
            FOLDS.computeIfPresent(p.getUUID(), (k, n) -> n > 1 ? n - 1 : null);
            p.displayClientMessage(Component.translatable("time.heroesevolved.still_point", FOLDS.getOrDefault(p.getUUID(), 0)), true);
        }
        if (!has(p, SNAP_SHUT)) return;
        ServerLevel level = p.serverLevel();
        Vec3 hole = from.add(0, .9, 0);
        double r = TimeGeometry.SNAP_RADIUS;
        for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, new AABB(hole, hole).inflate(r),
                t -> t != carried && hostile(p, t) && t.getBoundingBox().getCenter().distanceToSqr(hole) <= r * r)) {
            // Hurt first, then pull: the blow's own knockback would otherwise throw them away from the hole.
            t.hurt(p.damageSources().playerAttack(p), 3);
            Vec3 in = hole.subtract(t.getBoundingBox().getCenter());
            push(t, in.scale(.45).add(0, .2, 0));
            t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
        }
        TimeGeometry.show(level, Piece.TIME_SNAP, from, to, (float) r, null);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, .8F, .55F);
    }

    private static List<LivingEntity> edgeTargets(ServerPlayer p) {
        boolean longEdge = has(p, LONG_EDGE);
        double range = longEdge ? TimeGeometry.EDGE_LONG_RANGE : TimeGeometry.EDGE_RANGE;
        int count = longEdge ? TimeGeometry.EDGE_LONG_TARGETS : TimeGeometry.EDGE_TARGETS;
        List<LivingEntity> pool = new ArrayList<>(p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(range),
                t -> hostile(p, t) && t.distanceToSqr(p) <= range * range && p.hasLineOfSight(t)));
        // A chain: the nearest to you, then the nearest to that one, and so on.
        List<LivingEntity> chain = new ArrayList<>();
        Vec3 at = p.position();
        while (chain.size() < count && !pool.isEmpty()) {
            Vec3 from = at;
            LivingEntity next = Collections.min(pool, Comparator.comparingDouble(t -> t.position().distanceToSqr(from)));
            pool.remove(next); chain.add(next); at = next.position();
        }
        return chain;
    }

    public static boolean canEdge(ServerPlayer p) { return !EDGES.containsKey(p.getUUID()) && !edgeTargets(p).isEmpty(); }

    /**
     * Moment's Edge: time stops for a heartbeat. You fold between up to three enemies, cutting each, and land behind the
     * last; they feel every cut at once when time resumes.
     */
    static boolean edge(ServerPlayer p, int tier) {
        List<LivingEntity> targets = edgeTargets(p);
        if (targets.isEmpty() || EDGES.containsKey(p.getUUID())) return false;
        boolean linger = has(p, THOUSAND_CUTS);
        int span = targets.size() * TimeGeometry.EDGE_STEP + 4 + (linger ? TimeGeometry.EDGE_LINGER : 0);
        for (LivingEntity t : targets) hold(p, t, span);
        EDGES.put(p.getUUID(), new Edge(p, targets, 6 + tier, linger ? 2 : 1, linger, now(p) + 1));
        ServerLevel level = p.serverLevel();
        TimeGhostPayload.echo(p, p.position(), p.yBodyRot, 20, 0);
        ScreenTintPayload.send(p, 0x40506A90, 10, ScreenTintPayload.Style.FLASH);
        level.playSound(null, p.blockPosition(), ModSounds.TIME_SLOW_STOP.get(), SoundSource.PLAYERS, .8F, 1.25F);
        ArmPosePayload.show(p, ArmPose.TIME_DRAW, 8);
        return true;
    }

    public static boolean cutting(ServerPlayer p) { return EDGES.containsKey(p.getUUID()); }

    private static boolean tickEdge(Edge edge, long now) {
        ServerPlayer p = edge.owner;
        if (!p.isAlive() || p.isRemoved()) return true;
        if (now < edge.next) return false;
        ServerLevel level = p.serverLevel();
        if (edge.index >= edge.targets.size()) {
            // Time resumes: unless Thousand Cuts holds them a little longer, every cut lands now.
            if (!edge.linger) for (LivingEntity t : List.copyOf(edge.targets)) if (t.isAlive()) letGo(p.getServer(), t);
            level.playSound(null, p.blockPosition(), ModSounds.TIME_SLOW_STOP.get(), SoundSource.PLAYERS, .7F, .8F);
            return true;
        }
        LivingEntity t = edge.targets.get(edge.index++);
        edge.next = now + TimeGeometry.EDGE_STEP;
        if (!t.isAlive() || t.level() != level) return false;
        Vec3 from = p.position(), spot = TimeGeometry.behind(t);
        if (!SpaceTimeService.safe(p, spot)) {
            spot = t.position().add(from.subtract(t.position()).multiply(1, 0, 1).normalize().scale(TimeGeometry.BEHIND));
            if (!SpaceTimeService.safe(p, spot)) spot = from;
        }
        float[] face = TimeGeometry.facing(spot, t);
        TimeGhostPayload.echo(p, from, p.yBodyRot, 14, 0);
        p.teleportTo(level, spot.x, spot.y, spot.z, face[0], face[1]);
        p.setDeltaMovement(Vec3.ZERO); p.resetFallDistance();
        p.connection.send(new ClientboundSetEntityMotionPacket(p));
        AbilityEffects.send(level, Kind.TIME_FOLD, from, spot, .7);
        for (int i = 0; i < edge.cuts; i++) {
            t.invulnerableTime = 0;
            t.hurt(p.damageSources().playerAttack(p), edge.damage);
        }
        TimeGeometry.show(level, Piece.TIME_CUT, spot.add(0, 1.1, 0), t.getBoundingBox().getCenter(), edge.cuts, null);
        ArmPosePayload.show(p, ArmPose.TIME_DRAW, 8);
        level.playSound(null, t.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, .9F, 1.6F);
        return false;
    }

    // ------------------------------------------------------------------
    // Historian
    // ------------------------------------------------------------------

    /** Foreknowledge: after undoing {@code ticks} of time, you remember who hurt you in them. */
    public static void remember(ServerPlayer p, int ticks) {
        Deque<Blow> blows = HURT_BY.remove(p.getUUID());
        if (!has(p, FOREKNOWLEDGE) || blows == null) return;
        long cutoff = now(p) - ticks - 5;
        Set<UUID> who = new HashSet<>();
        for (Blow b : blows) if (b.at >= cutoff) who.add(b.who);
        if (who.isEmpty()) return;
        MARKS.put(p.getUUID(), new Marks(who, now(p) + TimeGeometry.MARK_TICKS));
        p.displayClientMessage(Component.translatable("time.heroesevolved.foreknowledge", who.size()), true);
        showMarks(p, MARKS.get(p.getUUID()));
    }

    public static boolean marked(ServerPlayer p, Entity target) {
        Marks m = MARKS.get(p.getUUID());
        return m != null && m.targets.contains(target.getUUID());
    }

    private static void showMarks(ServerPlayer p, Marks marks) {
        for (UUID id : marks.targets)
            if (p.serverLevel().getEntity(id) instanceof LivingEntity t && t.isAlive() && t.distanceToSqr(p) < 96 * 96)
                TimeGeometry.showTo(p, Piece.TIME_MARK, t.position(), t.getEyePosition(), t.getBbHeight(), t);
    }

    /** Shared Hour: Second Chance takes friends near you back their own five seconds too. */
    public static int sharedHour(ServerPlayer p) {
        if (!has(p, SHARED_HOUR)) return 0;
        int carried = 0;
        double r = TimeGeometry.SHARED_RADIUS;
        for (ServerPlayer friend : p.serverLevel().getEntitiesOfClass(ServerPlayer.class, p.getBoundingBox().inflate(r),
                f -> f != p && f.isAlive() && !f.isSpectator() && f.distanceToSqr(p) <= r * r && ExpansionSkills.friendly(p, f))) {
            if (SpaceTimeService.rewindPersonal(friend)) carried++;
        }
        if (carried > 0) TemporalStoryService.reach(p, TemporalStoryService.RESCUE);
        return carried;
    }

    /**
     * Fast Forward: wind a creature's clock five seconds ahead. Aimed at nothing, the years pass for the plants and young
     * animals around you instead.
     */
    static boolean fastForward(ServerPlayer p, int tier) {
        ServerLevel level = p.serverLevel();
        LivingEntity aimed = Targeting.livingEntityInSight(p, TimeGeometry.FORWARD_RANGE,
                t -> t != p && t.isAlive() && !(t instanceof TemporalEchoEntity) && !TemporalEntityEchoService.isReplaying(t));
        ArmPosePayload.show(p, ArmPose.TIME_TURN, 14);
        level.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1, 1.6F);
        level.playSound(null, p.blockPosition(), ModSounds.TIME_BLINK.get(), SoundSource.PLAYERS, .5F, 1.6F);
        if (aimed == null) return grow(p);
        List<LivingEntity> reached = new ArrayList<>(List.of(aimed));
        if (has(p, WIDER_ERA)) reached.addAll(level.getEntitiesOfClass(LivingEntity.class, aimed.getBoundingBox().inflate(TimeGeometry.ERA_SPREAD),
                t -> t != aimed && t != p && t.isAlive() && t.distanceToSqr(aimed) <= TimeGeometry.ERA_SPREAD * TimeGeometry.ERA_SPREAD
                        && !(t instanceof TemporalEchoEntity)).stream().limit(12).toList());
        int done = 0;
        for (LivingEntity t : reached) {
            if (hostile(p, t)) { forwardEnemy(p, t); done++; }
            else if (ExpansionSkills.friendly(p, t)) { forwardFriend(p, t); done++; }
        }
        return done > 0;
    }

    static void forwardEnemy(ServerPlayer p, LivingEntity t) {
        ServerLevel level = p.serverLevel();
        int span = TimeGeometry.FORWARD_TICKS;
        // The next five seconds of burning, poison and wither, all at once.
        float burn = Math.min(t.getRemainingFireTicks(), span) / 20F;
        float rot = 0;
        MobEffectInstance poison = t.getEffect(MobEffects.POISON), wither = t.getEffect(MobEffects.WITHER);
        if (poison != null) rot += Math.min(poison.getDuration(), span) / (float) Math.max(1, 25 >> poison.getAmplifier());
        if (wither != null) rot += Math.min(wither.getDuration(), span) / (float) Math.max(1, 40 >> wither.getAmplifier());
        if (t.getRemainingFireTicks() > 0) t.setRemainingFireTicks(Math.max(0, t.getRemainingFireTicks() - span));
        advance(t, span, null);
        if (burn + rot > 0) {
            // Not one of your blows: Era's End shouldn't replay it.
            replaying = true;
            try {
                t.invulnerableTime = 0;
                t.hurt(p.damageSources().indirectMagic(p, p), burn + rot);
            } finally { replaying = false; }
        }
        // A creature held in stopped time takes its stored blows now.
        if (p.getUUID().equals(SpaceTimeService.holder(t))) {
            if (!SpaceTimeService.releaseTarget(p, t)) letGo(p.getServer(), t);
        }
        t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, span, 1));
        t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, span, 0));
        if (has(p, ERAS_END)) replay(p, t);
        TimeGeometry.show(level, Piece.TIME_FORWARD, t.position(), t.getEyePosition(), 1, t);
    }

    static void forwardFriend(ServerPlayer p, LivingEntity t) {
        int span = TimeGeometry.FORWARD_TICKS;
        MobEffectInstance regen = t.getEffect(MobEffects.REGENERATION);
        if (regen != null) t.heal(Math.min(regen.getDuration(), span) / (float) Math.max(1, 50 >> regen.getAmplifier()));
        // Their harmful effects (and fire) run five seconds closer to ending, without hurting them.
        if (t.getRemainingFireTicks() > 0) t.setRemainingFireTicks(Math.max(0, t.getRemainingFireTicks() - span));
        advance(t, span, MobEffectCategory.HARMFUL);
        if (regen != null) advance(t, span, MobEffectCategory.BENEFICIAL, MobEffects.REGENERATION);
        TimeGeometry.show(p.serverLevel(), Piece.TIME_FORWARD, t.position(), t.getEyePosition(), 1 + TimeGeometry.FRIEND, t);
    }

    /** Runs effects {@code ticks} closer to ending: all of them, or one category, or only the listed effects. */
    @SafeVarargs
    private static void advance(LivingEntity t, int ticks, MobEffectCategory only, Holder<MobEffect>... effects) {
        for (MobEffectInstance e : List.copyOf(t.getActiveEffects())) {
            if (only != null && e.getEffect().value().getCategory() != only) continue;
            if (effects.length > 0 && !List.of(effects).contains(e.getEffect())) continue;
            if (e.isInfiniteDuration()) continue;
            t.removeEffect(e.getEffect());
            if (e.getDuration() > ticks)
                t.addEffect(new MobEffectInstance(e.getEffect(), e.getDuration() - ticks, e.getAmplifier(), e.isAmbient(), e.isVisible(), e.showIcon()));
        }
    }

    /** Era's End: every blow you landed on it in the last five seconds comes back at half strength. */
    private static void replay(ServerPlayer p, LivingEntity t) {
        Deque<Blow> dealt = DEALT.get(p.getUUID());
        if (dealt == null) return;
        long cutoff = now(p) - TimeGeometry.FORWARD_TICKS;
        float total = 0; int n = 0;
        for (Iterator<Blow> it = dealt.iterator(); it.hasNext(); ) {
            Blow b = it.next();
            if (b.who.equals(t.getUUID()) && b.at >= cutoff) {
                total += b.amount * .5F;
                Vec3 at = t.getBoundingBox().getCenter();
                double ang = n * 2.1;
                TimeGeometry.show(p.serverLevel(), Piece.TIME_CUT, at.add(Math.cos(ang) * 1.2, .2, Math.sin(ang) * 1.2), at,
                        1 + TimeGeometry.REPLAY, null, 2 + n * 2);
                n++;
                it.remove();
            }
        }
        if (total <= 0) return;
        replaying = true;
        try {
            t.invulnerableTime = 0;
            t.hurt(p.damageSources().playerAttack(p), total);
        } finally { replaying = false; }
    }

    /** Fast Forward with nothing aimed: crops, saplings and young animals round you grow up (Wider Era: five blocks). */
    private static boolean grow(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        int radius = has(p, WIDER_ERA) ? TimeGeometry.GROWTH_WIDE : TimeGeometry.GROWTH_RADIUS, calls = 0;
        BlockPos origin = p.blockPosition();
        for (int pass = 0; pass < 20 && calls < 600; pass++)
            for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-radius, -1, -radius), origin.offset(radius, 2, radius))) {
                if (level.random.nextInt(4) != 0) continue;
                var state = level.getBlockState(pos);
                if (!state.isRandomlyTicking() || !WorldMutationPolicy.canEdit(p, level, pos)) continue;
                state.randomTick(level, pos.immutable(), level.random);
                calls++;
            }
        for (AgeableMob young : level.getEntitiesOfClass(AgeableMob.class, p.getBoundingBox().inflate(radius), AgeableMob::isBaby)) young.setAge(0);
        TimeGeometry.show(level, Piece.TIME_FORWARD, p.position(), p.getEyePosition(), TimeGeometry.GROWTH + radius, null);
        return true;
    }

    // ------------------------------------------------------------------
    // Routing
    // ------------------------------------------------------------------

    /** The three named techniques. */
    public static boolean technique(AbilityUseContext c, String mechanic) {
        ServerPlayer p = c.player();
        if (TemporalSnapshotService.isTemporalDimension(p.serverLevel())) return false;
        boolean done = switch (mechanic) {
            case "stasis_seal" -> seal(p);
            case "shared_blink" -> edge(p, c.abilityLevel());
            case "accelerate" -> fastForward(p, c.abilityLevel());
            default -> false;
        };
        if (done) MasteryService.markPowerUse(p, ModAbilities.TIME_MANIPULATION_ID);
        return done;
    }

    /** Second presses, checked before cooldown and stamina: Selective Stasis lets go early; a Stasis Seal is thrown. */
    public static boolean followUp(AbilityUseContext c, int branch, String mechanic) {
        if (mechanic.isEmpty()) return branch == 0 && SpaceTimeService.releaseStasis(c.player());
        return mechanic.equals("stasis_seal") && throwSeal(c.player());
    }

    // ------------------------------------------------------------------
    // Events
    // ------------------------------------------------------------------

    /**
     * Runs before stopped time stores a blow, so what is stored already includes these bonuses: Behind You, Foreknowledge's
     * marks; and it records the blows Foreknowledge and Era's End remember.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void damage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) return;
        Entity source = event.getSource().getEntity();
        if (target instanceof ServerPlayer victim && source instanceof LivingEntity attacker && attacker != victim
                && SpaceTimeService.awakened(victim) && has(victim, FOREKNOWLEDGE))
            record(HURT_BY, victim, attacker.getUUID(), 0, 300);
        if (!(source instanceof ServerPlayer p) || source == target || replaying) return;
        Behind b = BEHIND.get(p.getUUID());
        if (b != null && b.target.equals(target.getUUID()) && event.getSource().getDirectEntity() == p) {
            BEHIND.remove(p.getUUID());
            event.setAmount(event.getAmount() * (TimeStopService.isStopped(target) ? 2 : 1.5F));
            TimeGeometry.show(p.serverLevel(), Piece.TIME_CUT, p.getEyePosition(), target.getBoundingBox().getCenter(), 1, null);
            p.serverLevel().playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1, 1.3F);
        }
        if (marked(p, target)) event.setAmount(event.getAmount() * 1.3F);
        if (has(p, ERAS_END)) record(DEALT, p, target.getUUID(), event.getAmount(), TimeGeometry.FORWARD_TICKS);
    }

    private static void record(Map<UUID, Deque<Blow>> into, ServerPlayer p, UUID who, float amount, int keep) {
        long now = now(p);
        Deque<Blow> blows = into.computeIfAbsent(p.getUUID(), k -> new ArrayDeque<>());
        blows.addLast(new Blow(who, now, amount));
        while (blows.size() > 32 || !blows.isEmpty() && blows.getFirst().at < now - keep) blows.removeFirst();
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) { tick(event.getServer()); }

    public static void tick(MinecraftServer server) {
        long now = now(server);
        List<Map.Entry<UUID, Hold>> expired = new ArrayList<>();
        for (var it = HELD.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            if (now < entry.getValue().until) continue;
            it.remove();
            expired.add(entry);
        }
        // Released after the loop: a released blow can kill, and a death lets go of what the dead held.
        for (var entry : expired) freeOne(server, entry.getValue().owner, entry.getKey());
        if (now % 10 == 0 && !HELD.isEmpty())
            for (ServerLevel level : server.getAllLevels())
                for (UUID id : HELD.keySet())
                    if (level.getEntity(id) instanceof LivingEntity t)
                        TimeGeometry.show(level, Piece.TIME_STASIS, t.position(), t.getEyePosition(), t.getBbHeight(), t);
        FOLDS.keySet().removeIf(id -> {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            return p == null || !TimeStopService.hasActiveField(p);
        });
        for (Seal seal : List.copyOf(SEALS.values())) {
            ServerPlayer p = seal.owner;
            if (now >= seal.until || !p.isAlive() || p.isRemoved() || p.level() != seal.level) {
                SEALS.remove(p.getUUID());
                resume(seal);
                continue;
            }
            tickSeal(seal, now);
        }
        HOMING.removeIf(h -> {
            if (now >= h.until || h.shot.isRemoved() || !h.target.isAlive() || h.shot.level() != h.target.level()) return true;
            Vec3 v = h.shot.getDeltaMovement();
            Vec3 want = h.target.getBoundingBox().getCenter().subtract(h.shot.position()).normalize().scale(Math.max(.6, v.length()));
            h.shot.setDeltaMovement(v.lerp(want, .35)); h.shot.hurtMarked = true;
            return false;
        });
        // A copy: a cut that kills can end someone else's edge (drop) while this one runs.
        for (Edge edge : List.copyOf(EDGES.values()))
            if (tickEdge(edge, now)) EDGES.remove(edge.owner.getUUID(), edge);
        BEHIND.values().removeIf(b -> now >= b.until);
        MARKS.entrySet().removeIf(e -> now >= e.getValue().until);
        if (now % 10 == 0) for (var e : MARKS.entrySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p != null) showMarks(p, e.getValue());
        }
        if (now % 100 == 0) {
            HURT_BY.values().removeIf(d -> d.isEmpty() || d.getLast().at < now - 300);
            DEALT.values().removeIf(d -> d.isEmpty() || d.getLast().at < now - TimeGeometry.FORWARD_TICKS);
        }
    }

    private static void push(Entity target, Vec3 velocity) {
        target.setDeltaMovement(velocity); target.hurtMarked = true;
        if (target instanceof ServerPlayer player) player.connection.send(new ClientboundSetEntityMotionPacket(player));
    }

    /** Ends what a traveller is doing (death, logout, a new world, losing the power): what they held goes free, and its blows land. */
    public static void drop(ServerPlayer p) {
        UUID id = p.getUUID();
        Seal seal = SEALS.remove(id);
        if (seal != null) resume(seal);
        EDGES.remove(id); FOLDS.remove(id); BEHIND.remove(id);
        MinecraftServer server = p.getServer();
        List<UUID> freed = new ArrayList<>();
        for (var it = HELD.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            if (!entry.getValue().owner.equals(id)) continue;
            it.remove();
            freed.add(entry.getKey());
        }
        HELD.remove(id);
        HOMING.removeIf(h -> h.owner == p);
        if (server != null) for (UUID held : freed) freeOne(server, id, held);
    }

    private static void freeOne(MinecraftServer server, UUID owner, UUID held) {
        for (ServerLevel level : server.getAllLevels())
            if (level.getEntity(held) instanceof LivingEntity t) { TimeStopService.release(server, owner, t); return; }
    }

    public static void clear() {
        SEALS.values().forEach(SpaceTimeSkills::resume);
        SEALS.clear(); HELD.clear(); FOLDS.clear(); HOMING.clear(); EDGES.clear(); BEHIND.clear();
        HURT_BY.clear(); DEALT.clear(); MARKS.clear();
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) { clear(); }
}
