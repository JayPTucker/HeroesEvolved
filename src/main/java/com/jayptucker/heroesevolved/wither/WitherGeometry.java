package com.jayptucker.heroesevolved.wither;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/**
 * The Ossuary, the creature's body and every attack's shape, in one place: the server's hits, the renderer's rest pose
 * and the telegraphs all read these numbers, so a warning is exactly what lands.
 *
 * <p>Body points are written in model pixels (as {@code RiftWitherModel} builds them): x to the model's side, up from the
 * ground, and forward (the model's -z). {@link #point} turns them into the world for any yaw and scale.
 */
public final class WitherGeometry {
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "wither_cage"));
    public static final BlockPos ORIGIN = new BlockPos(0, 64, 0);
    public static final int INTRO_TICKS = 120, DEATH_TICKS = 70, MINIMUM_LEVEL = 16;

    // ---- The Ossuary (tools/generate-wither-arena.py draws the same numbers) ----
    /** Walkable floor radius, the wall ring's outer radius, the dome's height and the leash for fighters who fly out. */
    public static final double RADIUS = 29, WALL = 31, FLIGHT_HEIGHT = 44, LEASH = 37;
    public static final List<BlockPos> ANCHORS = List.of(ORIGIN.offset(-20, 6, -12), ORIGIN.offset(20, 6, -12), ORIGIN.offset(0, 6, 23));
    /** Broken pillars: real cover from Event Horizon and what Ribbreaker crashes into. x, z, half width, height above floor. */
    public static final int[][] PILLARS = {{-15, 6, 2, 12}, {15, 6, 2, 11}, {-9, -15, 1, 9}, {9, -15, 1, 10}, {-10, 17, 1, 8}, {10, 17, 1, 9}};
    public static Vec3 floor() { return Vec3.atBottomCenterOf(ORIGIN.above(3)); }
    public static Vec3 center() { return floor().add(0, 7, 0); }
    public static Vec3 entrance() { return floor().add(0, 0, -25); }
    public static Vec3 exit() { return floor().add(0, 1.6, -37); }

    // ---- The body ----
    /** Model scale in the arena and at the summoning site. The arena body stands a little under ten blocks tall. */
    public static final float ARENA_SCALE = 1.8F, INTRO_SCALE = .9F;
    public static final float WIDTH = 5.2F, HEIGHT = 8.8F;
    /** Rest-pose anchors in model pixels {x, up, forward}. The model's bones are built to end exactly here. */
    public static final float[] HIPS = {0, 16, -2}, CORE = {0, 43, 4}, YOKE = {0, 54, -2};
    public static final float[][] NECK_BASE = {{0, 57, -1}, {-16, 58, -2}, {16, 58, -2}};
    public static final float[][] HEAD = {{0, 68, 4}, {-25, 64, 1}, {25, 64, 1}};
    public static final float[] MOUTH_REACH = {10, 8, 8};
    public static final float[][] SHOULDER = {{-20, 55, -2}, {20, 55, -2}};
    public static final float[][] HAND = {{-21, 17, 6}, {21, 17, 6}};

    public static Vec3 point(Vec3 feet, float yaw, float scale, float x, float up, float forward) {
        double r = Math.toRadians(yaw), s = scale / 16D;
        Vec3 right = new Vec3(-Math.cos(r), 0, -Math.sin(r)), ahead = new Vec3(-Math.sin(r), 0, Math.cos(r));
        return feet.add(right.scale(-x * s)).add(ahead.scale(forward * s)).add(0, up * s, 0);
    }
    private static Vec3 point(Vec3 feet, float yaw, float[] p) { return point(feet, yaw, ARENA_SCALE, p[0], p[1], p[2]); }
    public static Vec3 core(Vec3 feet) { return feet.add(0, CORE[1] * ARENA_SCALE / 16, 0); }
    public static Vec3 core(Vec3 feet, float yaw) { return point(feet, yaw, CORE); }
    /** Where head {@code index} (0 center, 1 and 2 the sides) sits at rest. */
    public static Vec3 head(Vec3 feet, float yaw, int index) { return point(feet, yaw, HEAD[index]); }
    /** Its open mouth once it has turned to {@code aim}: beams, skulls and tethers leave from here. */
    public static Vec3 mouth(Vec3 feet, float yaw, int index, Vec3 aim) {
        Vec3 head = head(feet, yaw, index);
        Vec3 to = aim.subtract(head);
        Vec3 dir = to.lengthSqr() < 1e-4 ? Vec3.directionFromRotation(0, yaw) : to.normalize();
        return head.add(dir.scale(MOUTH_REACH[index] * ARENA_SCALE / 16)).add(0, -.2, 0);
    }
    public static Vec3 hand(Vec3 feet, float yaw, int side) { return point(feet, yaw, HAND[side]); }

    // ---- Moves ----
    /** Windups are each telegraph's length; rest is the pause a move adds before the next. */
    public enum Move {
        VERDICT(26, 14), RAIN(22, 10), PULSE(24, 10), TETHER(20, 12), LUNGE(24, 12), SLAM(28, 18), HORIZON(64, 22),
        BITE(14, 8), RAKE(18, 12), SPIRES(20, 12);
        public final int windup, rest;
        Move(int windup, int rest) { this.windup = windup; this.rest = rest; }
        /** Moves that draw on an anchor while they wind up, so the room's counter is lit. */
        public boolean feeds() { return this == RAIN || this == SLAM || this == HORIZON || this == SPIRES || this == LUNGE; }
    }
    public static final int HEAD_WINDUP = 16, FEED_TICKS = 140, STANCE_HITS = 3;
    /** Maw Snap: the center head strikes down a short lane. */
    public static final float BITE_REACH = 9, BITE_RADIUS = 1.7F;
    /** Rending Claw: one arm sweeps a wide arc. */
    public static final float RAKE_REACH = 10.5F, RAKE_HALF_ANGLE = 1.25F;
    /** Threefold Verdict: three beams held for {@code VERDICT_FIRE} ticks. */
    public static final float VERDICT_RADIUS = 1.15F, VERDICT_SPREAD = 3.6F;
    public static final int VERDICT_FIRE = 10;
    /** Hollow Rain: burning skulls dropped onto marked circles. */
    public static final float SKULL_RADIUS = 2.3F, RAIN_HEIGHT = 12, RAIN_SPREAD = 4;
    public static final float TETHER_RADIUS = 1.1F;
    /** Starved Pulse and every slam's aftershock: a ring of the real floor thrown up. A normal jump clears it. */
    public static final float PULSE_SPEED = .7F, PULSE_REACH = 26, WAVE_HEIGHT = .8F, WAVE_FRONT = 1.1F;
    public static final int PULSE_ECHO = 14;
    /** Ribbreaker: a charge along a fixed lane; a wall or pillar in the way stops it dead and leaves it stunned. */
    public static final float LUNGE_RADIUS = 3, LUNGE_REACH = 26, CRASH_MARGIN = 3;
    public static final int LUNGE_TICKS = 14, CRASH_TICKS = 70;
    /** Ossuary Slam: it rises, then both fists come down; the shock rolls out from where they land. */
    public static final float SLAM_RADIUS = 5.5F, SLAM_RISE = 7, SLAM_WAVE = 18;
    public static final int SLAM_TICKS = 10;
    /** Bone Spires: a crack runs to each fighter's mark, and spires burst from it in order. */
    public static final float SPIRE_RADIUS = 1.25F, SPIRE_HEIGHT = 3.6F, SPIRE_STEP = 1.6F, SPIRE_REACH = 24;
    public static final int SPIRE_RATE = 2, SPIRE_LINGER = 26;
    /** Event Horizon: the star drinks for three seconds, then shines on everyone it can see. */
    public static final float HORIZON_PULL = .045F;

    /**
     * The chain it opens on a fighter: what reaches them from where they are, then follow-ups that punish the usual
     * answer. Never one move twice in a row; Event Horizon every third chain of the last phase.
     */
    public static Move[] chain(int phase, int turn, double distance, boolean high) {
        boolean odd = Math.floorMod(turn, 2) == 1;
        if (phase >= 3 && Math.floorMod(turn, 3) == 2) return new Move[]{Move.HORIZON, Move.PULSE};
        if (high) return phase == 1 ? new Move[]{Move.TETHER, Move.VERDICT}
                : odd ? new Move[]{Move.TETHER, Move.RAIN, Move.VERDICT} : new Move[]{Move.VERDICT, Move.TETHER, Move.LUNGE};
        if (distance < 9) return switch (phase) {
            case 1 -> odd ? new Move[]{Move.RAKE, Move.PULSE} : new Move[]{Move.BITE, Move.RAKE};
            case 2 -> odd ? new Move[]{Move.BITE, Move.SPIRES} : new Move[]{Move.RAKE, Move.BITE, Move.SLAM};
            default -> odd ? new Move[]{Move.SLAM, Move.RAKE, Move.BITE} : new Move[]{Move.RAKE, Move.BITE, Move.PULSE};
        };
        if (distance < 18) return switch (phase) {
            case 1 -> odd ? new Move[]{Move.PULSE, Move.BITE} : new Move[]{Move.VERDICT, Move.RAIN};
            case 2 -> odd ? new Move[]{Move.SLAM, Move.RAKE} : new Move[]{Move.SPIRES, Move.VERDICT};
            default -> odd ? new Move[]{Move.SPIRES, Move.SLAM, Move.RAKE} : new Move[]{Move.VERDICT, Move.SPIRES, Move.PULSE};
        };
        return switch (phase) {
            case 1 -> odd ? new Move[]{Move.VERDICT, Move.TETHER} : new Move[]{Move.RAIN, Move.TETHER};
            case 2 -> odd ? new Move[]{Move.RAIN, Move.SPIRES} : new Move[]{Move.LUNGE, Move.RAKE};
            default -> odd ? new Move[]{Move.RAIN, Move.VERDICT, Move.SPIRES} : new Move[]{Move.LUNGE, Move.SLAM, Move.RAKE};
        };
    }

    public static double angle(Vec3 from, Vec3 to, float yaw) {
        Vec3 d = to.subtract(from);
        double a = Math.atan2(-d.x, d.z) - Math.toRadians(yaw);
        return Math.abs(Math.atan2(Math.sin(a), Math.cos(a)));
    }
    public static boolean inArc(Vec3 from, Vec3 to, float yaw, double reach, double halfAngle) {
        return to.multiply(1, 0, 1).distanceToSqr(from.multiply(1, 0, 1)) <= reach * reach && angle(from, to, yaw) <= halfAngle;
    }
    public static double lineDistance(Vec3 a, Vec3 b, Vec3 point) {
        Vec3 d = b.subtract(a);
        double t = Math.clamp(point.subtract(a).dot(d) / Math.max(1e-7, d.lengthSqr()), 0, 1);
        return point.distanceTo(a.add(d.scale(t)));
    }
    /** The spires' centers along one crack, nearest the creature first. */
    public static List<Vec3> spires(Vec3 from, Vec3 mark) {
        Vec3 flat = new Vec3(mark.x - from.x, 0, mark.z - from.z);
        double length = Math.min(SPIRE_REACH, flat.length() + 4);
        Vec3 dir = flat.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : flat.normalize();
        java.util.ArrayList<Vec3> out = new java.util.ArrayList<>();
        for (double d = 3; d <= length; d += SPIRE_STEP) out.add(new Vec3(from.x, floor().y, from.z).add(dir.scale(d)));
        return out;
    }
    /** The ripple's ring at {@code age} ticks: a pressure front {@link #WAVE_HEIGHT} high. */
    public static double ripple(int age, float speed) { return age * speed; }
    private WitherGeometry() {}
}
