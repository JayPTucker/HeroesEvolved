package com.jayptucker.heroesevolved.story.sylar;

import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.speed.SpeedService;
import com.jayptucker.heroesevolved.story.sylar.SylarPowers.Move;
import com.jayptucker.heroesevolved.story.sylar.SylarPowers.Pose;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * The Watchmaker's own moves, which he only uses in his shop. Every one is a clock that runs down: its telegraph is a
 * dial whose hand sweeps round, and it lands when the hand reaches twelve. The windup is exactly the telegraph's length
 * (its {@link Piece}'s ticks), so what you see is what you get.
 *
 * <ul>
 *   <li><b>Taking Inventory</b> (100–60%): powers from people he has hunted. A telekinetic slam that rips up the floor,
 *   a wall of ice across the shop, and lightning down a gear rail.</li>
 *   <li><b>The Hunger</b> (60–30%): he lunges to grab you ({@link SylarEntity#grab}).</li>
 *   <li><b>Clockwork</b> (30–0%): rings of stopped time close in; only the gap in the clock face is safe.</li>
 * </ul>
 */
public final class WatchmakerMoves {
    public static final double SLAM_RADIUS = 3.5;
    /** How far either side of a rail, or of the ice wall's line, its hit reaches. */
    public static final double RAIL_REACH = 1.8, WALL_REACH = 1.2;
    /** The wall stands eight seconds, three high, and rises two ticks after the shove (once its line is clear). */
    public static final int WALL_TICKS = 160, WALL_HEIGHT = 3, WALL_DELAY = 2;
    /** How long the grab's lunge can reach. */
    public static final double LUNGE = 10;
    /** The rings start beyond the shop's corners and close to the middle over {@link Piece#WATCH_RINGS}'s length. */
    public static final double RING_START = WatchmakerArena.HALF * 1.45;
    /** Half the gap's width: one hour either side of it. */
    public static final double GAP_HALF = Math.PI / 6;

    private WatchmakerMoves() {}

    /** Where the floor is under {@code at}: the shop's floor in the shop, otherwise the target's feet. */
    private static Vec3 floor(SylarEntity s, Vec3 at) {
        return s.arena() ? new Vec3(at.x, WatchmakerArena.ORIGIN.getY() + 1, at.z) : at;
    }

    private static void tick(SylarEntity s, ServerLevel l, Vec3 at) {
        // A watch ticking faster as the hand nears twelve.
        SylarPowers.sound(l, at, SoundEvents.NOTE_BLOCK_HAT.value(), 0.7F, 0.9F + s.castProgress() * 0.9F);
    }

    // ------------------------------------------------------------------
    // Taking Inventory
    // ------------------------------------------------------------------

    public static final Move FLOOR_SLAM = new Move("floor_slam", ModAbilities.TELEKINESIS_ID, Piece.WATCH_SLAM_MARK.ticks, 170, 0, 22, false, Pose.RAISED,
            (s, l, t, p) -> {
                Vec3 at = floor(s, t.position());
                s.mark(at);
                SetPiecePayload.send(l, Piece.WATCH_SLAM_MARK, at, at.add(0, 1, 0), (float) SLAM_RADIUS, null);
                SylarPowers.sound(l, at, SoundEvents.ILLUSIONER_PREPARE_MIRROR, 1.0F, 0.5F);
            },
            (s, l, t, p) -> tick(s, l, s.mark()),
            (s, l, t, p) -> {
                Vec3 at = s.mark();
                SetPiecePayload.send(l, Piece.WATCH_SLAM, at, SylarPowers.hand(s), (float) SLAM_RADIUS, null);
                for (LivingEntity hit : l.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(SLAM_RADIUS, 3, SLAM_RADIUS),
                        e -> SylarPowers.foe(s, e) && Math.hypot(e.getX() - at.x, e.getZ() - at.z) <= SLAM_RADIUS)) {
                    SylarPowers.hurt(s, hit, 7 * p);
                    SylarPowers.push(hit, SylarPowers.flat(hit.position().subtract(at)).scale(0.35).add(0, 0.95, 0));
                }
                if (s.arena()) {
                    WatchmakerArena.ripFloor(l, at, SLAM_RADIUS - 0.5);
                    WatchmakerArena.shatter(l, at, SLAM_RADIUS + 3);
                }
                CameraShakePayload.sendAround(l, at, 40, 0.8F, 12);
                SylarPowers.sound(l, at, SoundEvents.GENERIC_EXPLODE.value(), 0.8F, 0.6F);
                SylarPowers.sound(l, at, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, 1.0F, 0.6F);
            });

    public static final Move ICE_WALL = new Move("ice_wall", ModAbilities.CRYOKINESIS_ID, Piece.WATCH_ICE_MARK.ticks, 260, 0, 30, false, Pose.TWO_HANDS,
            (s, l, t, p) -> {
                Vec3[] line = wallLine(s, t.position());
                s.mark(line[0]); s.mark2(line[1]);
                SetPiecePayload.send(l, Piece.WATCH_ICE_MARK, line[0], line[1], WALL_HEIGHT, null);
                SylarPowers.sound(l, s.position(), SoundEvents.POWDER_SNOW_PLACE, 1.0F, 0.5F);
            },
            (s, l, t, p) -> tick(s, l, s.mark().lerp(s.mark2(), 0.5)),
            (s, l, t, p) -> {
                Vec3 a = s.mark(), b = s.mark2(), along = b.subtract(a), dir = SylarPowers.flat(along);
                // The far side: away from him.
                Vec3 across = new Vec3(-dir.z, 0, dir.x);
                if (across.dot(a.subtract(s.position())) < 0) across = across.reverse();
                for (LivingEntity hit : l.getEntitiesOfClass(LivingEntity.class, new AABB(a, b).inflate(WALL_REACH + 1, 3, WALL_REACH + 1),
                        e -> SylarPowers.foe(s, e) && distanceToSegment(e.position(), a, b) <= WALL_REACH)) {
                    SylarPowers.hurt(s, hit, 6 * p);
                    hit.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 3));
                    hit.setTicksFrozen(Math.max(hit.getTicksFrozen(), 160));
                    SylarPowers.push(hit, across.scale(0.85).add(0, 0.3, 0));
                }
                // The wall rises just after the shove, so nobody is sealed inside it and nobody leaves a hole in it.
                Set<BlockPos> cells = new LinkedHashSet<>();
                double length = along.horizontalDistance();
                for (double d = 0; d <= length; d += 0.4) {
                    Vec3 at = a.add(dir.scale(d));
                    for (int y = 0; y < WALL_HEIGHT; y++) cells.add(BlockPos.containing(at.x, a.y + y + 0.1, at.z));
                }
                s.raiseWall(List.copyOf(cells), WALL_DELAY);
                SetPiecePayload.send(l, Piece.WATCH_ICE_WALL, a, b, WALL_HEIGHT, null, WALL_DELAY);
                SylarPowers.sound(l, a.lerp(b, 0.5), SoundEvents.GLASS_BREAK, 1.2F, 0.5F);
                SylarPowers.sound(l, a.lerp(b, 0.5), SoundEvents.POWDER_SNOW_BREAK, 1.2F, 0.6F);
            });

    public static final Move RAIL_ARC = new Move("rail_arc", ModAbilities.ELECTROKINESIS_ID, Piece.WATCH_RAIL_MARK.ticks, 200, 0, 30, false, Pose.ONE_HAND,
            (s, l, t, p) -> {
                Vec3[] rail = nearestRail(t.position());
                s.mark(rail[0]); s.mark2(rail[1]);
                SetPiecePayload.send(l, Piece.WATCH_RAIL_MARK, rail[0], rail[1], 1, null);
                AbilityEffects.send(l, Kind.ELECTRIC_TELEGRAPH, SylarPowers.hand(s), SylarPowers.hand(s).add(0, 1, 0), 1.0);
                SylarPowers.sound(l, s.position(), SoundEvents.BEACON_ACTIVATE, 0.7F, 2.0F);
            },
            (s, l, t, p) -> SylarPowers.sound(l, s.mark().lerp(s.mark2(), 0.5), SoundEvents.REDSTONE_TORCH_BURNOUT, 0.5F, 0.8F + s.castProgress()),
            (s, l, t, p) -> {
                Vec3 a = s.mark(), b = s.mark2();
                SetPiecePayload.send(l, Piece.WATCH_RAIL_ARC, a, b, 1, null);
                Vec3 post = SylarPowers.hand(s).distanceToSqr(a) < SylarPowers.hand(s).distanceToSqr(b) ? a : b;
                AbilityEffects.send(l, Kind.ELECTRIC_ARC, SylarPowers.hand(s), post.add(0, 2.5, 0), 1.0);
                for (LivingEntity hit : l.getEntitiesOfClass(LivingEntity.class, new AABB(a, b).inflate(RAIL_REACH + 1, 3, RAIL_REACH + 1),
                        e -> SylarPowers.foe(s, e) && e.getY() < a.y + 2.5 && distanceToSegment(e.position(), a, b) <= RAIL_REACH)) {
                    SylarPowers.hurt(s, hit, 8 * p);
                    hit.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 2));
                    AbilityEffects.send(l, Kind.ELECTRIC_ARC, closest(hit.position(), a, b).add(0, 0.2, 0), hit.getBoundingBox().getCenter(), 0.8);
                }
                CameraShakePayload.sendAround(l, a.lerp(b, 0.5), 32, 0.4F, 8);
                SylarPowers.sound(l, a.lerp(b, 0.5), SoundEvents.LIGHTNING_BOLT_IMPACT, 0.9F, 1.2F);
            });

    // ------------------------------------------------------------------
    // The Hunger
    // ------------------------------------------------------------------

    public static final Move GRAB = new Move("grab", ModAbilities.TELEKINESIS_ID, Piece.WATCH_GRAB_MARK.ticks, 220, 1.5, LUNGE - 1, true, Pose.ONE_HAND,
            (s, l, t, p) -> {
                s.mark(t.position());
                SetPiecePayload.send(l, Piece.WATCH_GRAB_MARK, s.position(), t.position(), (float) LUNGE, s);
                SylarPowers.sound(l, s.position(), SoundEvents.EVOKER_PREPARE_ATTACK, 1.0F, 0.6F);
            },
            (s, l, t, p) -> SylarPowers.sound(l, s.position(), SoundEvents.WARDEN_HEARTBEAT, 0.6F, 1.2F + s.castProgress()),
            (s, l, t, p) -> {
                // He lunges down the lane he showed you, through where you stood and a little past it.
                Vec3 from = s.position(), dir = SylarPowers.flat(s.mark().subtract(from));
                double reach = Math.min(LUNGE, Math.sqrt(s.mark().distanceToSqr(from)) + 1);
                Vec3 end = SpeedService.swept(s, dir.scale(reach));
                ServerPlayer caught = null;
                double best = Double.MAX_VALUE;
                for (ServerPlayer player : l.getEntitiesOfClass(ServerPlayer.class, new AABB(from, end).inflate(1.6, 2, 1.6),
                        e -> SylarPowers.foe(s, e) && distanceToSegment(e.position(), from, end) <= 1.4 && Math.abs(e.getY() - from.y) < 2)) {
                    double along = player.position().subtract(from).dot(dir);
                    if (along < best) { best = along; caught = player; }
                }
                Vec3 stop = caught == null ? end : from.add(dir.scale(Math.max(0, best - 1.1)));
                s.teleportTo(stop.x, stop.y, stop.z);
                SylarPowers.sound(l, stop, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 0.6F);
                if (caught != null) s.grab(caught);
                else s.overreach();
            });

    // ------------------------------------------------------------------
    // Clockwork
    // ------------------------------------------------------------------

    public static final Move TIME_RINGS = new Move("time_rings", ModAbilities.TIME_MANIPULATION_ID, Piece.WATCH_RINGS_MARK.ticks, 200, 0, 40, false, Pose.RAISED,
            (s, l, t, p) -> {
                float gap = (float) (s.getRandom().nextInt(12) * Math.PI / 6);
                s.gap(gap);
                Vec3 c = s.arena() ? WatchmakerArena.center() : s.position();
                s.mark(c);
                SetPiecePayload.send(l, Piece.WATCH_RINGS_MARK, c, c.add(Mth.sin(gap), 0, -Mth.cos(gap)), (float) RING_START, null);
                SylarPowers.sound(l, c, SoundEvents.BELL_BLOCK, 1.5F, 0.5F);
                for (ServerPlayer witness : l.players())
                    if (witness.distanceToSqr(c) < 40 * 40) SylarVoice.warn(witness, "story.heroesevolved.sylar.warn.rings");
            },
            (s, l, t, p) -> tick(s, l, s.mark()),
            (s, l, t, p) -> {
                Vec3 c = s.mark();
                SetPiecePayload.send(l, Piece.WATCH_RINGS, c, c.add(Mth.sin(s.gap()), 0, -Mth.cos(s.gap())), (float) RING_START, null);
                SylarPowers.sound(l, c, com.jayptucker.heroesevolved.sounds.ModSounds.TIME_SLOW.get(), 1.4F, 0.7F);
                s.startRings();
            });

    /** Every arena move, for tests and {@link SylarEntity#castNow}. */
    public static final List<Move> ALL = List.of(FLOOR_SLAM, ICE_WALL, RAIL_ARC, GRAB, TIME_RINGS);

    /** What he can use in his shop in this phase. */
    static List<Move> available(int phase) {
        List<Move> moves = new ArrayList<>(List.of(FLOOR_SLAM, ICE_WALL, RAIL_ARC));
        if (phase >= 2) moves.add(GRAB);
        if (phase >= 3) moves.add(TIME_RINGS);
        return moves;
    }

    /** How keen he is on a move this phase: the phase's new trick most of all. */
    static int weight(Move move, int phase) {
        if (move == TIME_RINGS) return 6;
        if (move == GRAB) return phase == 2 ? 5 : 3;
        return 3;
    }

    // ------------------------------------------------------------------
    // Shapes shared with the drawing
    // ------------------------------------------------------------------

    /** The radius of the closing rings {@code age} ticks after they start. */
    public static double ringRadius(float age) {
        return RING_START * Math.max(0, 1 - age / Piece.WATCH_RINGS.ticks);
    }

    /** Is a point at offset (dx, dz) from the clock face's middle inside the gap at angle {@code gap}? */
    public static boolean inGap(double dx, double dz, double gap) {
        double angle = Math.atan2(dx, -dz);
        return Math.abs(Mth.wrapDegrees(Math.toDegrees(angle - gap))) <= Math.toDegrees(GAP_HALF);
    }

    /** The ice wall's line: through the target, square to him, from wall to wall in the shop. */
    static Vec3[] wallLine(SylarEntity s, Vec3 through) {
        Vec3 toward = SylarPowers.flat(through.subtract(s.position()));
        Vec3 dir = new Vec3(-toward.z, 0, toward.x);
        Vec3 at = floor(s, through);
        if (!s.arena()) return new Vec3[] {at.subtract(dir.scale(7)), at.add(dir.scale(7))};
        Vec3 c = WatchmakerArena.center();
        double lim = WatchmakerArena.HALF - 0.5, lo = -40, hi = 40;
        for (int axis = 0; axis < 2; axis++) {
            double d = axis == 0 ? dir.x : dir.z, p = axis == 0 ? at.x - c.x : at.z - c.z;
            if (Math.abs(d) < 1e-6) continue;
            double t1 = (-lim - p) / d, t2 = (lim - p) / d;
            lo = Math.max(lo, Math.min(t1, t2)); hi = Math.min(hi, Math.max(t1, t2));
        }
        return new Vec3[] {at.add(dir.scale(lo)), at.add(dir.scale(hi))};
    }

    static Vec3[] nearestRail(Vec3 to) {
        Vec3[] best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Vec3[] rail : WatchmakerArena.rails()) {
            double d = distanceToSegment(to, rail[0], rail[1]);
            if (d < bestDistance) { bestDistance = d; best = rail; }
        }
        return best;
    }

    static Vec3 closest(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = new Vec3(b.x - a.x, 0, b.z - a.z);
        double len = ab.lengthSqr();
        double u = len < 1e-6 ? 0 : Math.clamp(((p.x - a.x) * ab.x + (p.z - a.z) * ab.z) / len, 0, 1);
        return a.add(b.subtract(a).scale(u));
    }

    /** The horizontal distance from {@code p} to the segment from {@code a} to {@code b}. */
    static double distanceToSegment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 c = closest(p, a, b);
        return Math.hypot(p.x - c.x, p.z - c.z);
    }
}
