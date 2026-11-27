package com.jayptucker.heroesevolved.telekinesis;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Telekinesis's shared numbers and places: the far hand's reaches, the schedules it keeps, and where held things hang.
 * The server ({@code TelekinesisService}, {@code TelekinesisSkillService}) and the drawing ({@code KineticArt}) both
 * read these, so a palm is drawn reaching a body at the tick the server shoves it, a held body hangs where the hand is
 * drawn, and a Skyfall's ring fills exactly as the body comes down.
 */
public final class KineticGeometry {
    private KineticGeometry() {}

    // ---- Shove: a palm of bent air driven along the aim
    /** The Shove's reach in base power range (doubled by {@code AbilityRanges.ranged}: sixteen blocks). */
    public static final double SHOVE_BASE_RANGE = 8;
    /** Half the Shove's cone, in degrees. */
    public static final double SHOVE_HALF_ANGLE = 35;
    /** Ticks the palm takes to reach the end of the cone; a body is shoved as the palm reaches it. */
    public static final int SHOVE_TRAVEL = 4;

    /** The tick (after the cast) the palm reaches something {@code distance} blocks out of a {@code reach}-block cone. */
    public static int shoveArrival(double distance, double reach) {
        return Mth.clamp((int) Math.ceil(SHOVE_TRAVEL * distance / Math.max(1, reach)), 0, SHOVE_TRAVEL);
    }

    /** Where the palm is {@code t} ticks into a shove of {@code reach} blocks, as a share of the reach (0 at the hand). */
    public static float shoveTravel(float t) { return Mth.clamp(t / SHOVE_TRAVEL, 0, 1); }

    // ---- Grip: the far hand closes on something and holds it beside the view
    /** Grip's reach in base power range (thirty-two blocks; Long Reach adds four base, eight real). */
    public static final double GRIP_BASE_RANGE = 16;
    /** How fast a held body may follow the hand, in blocks a tick: fast enough to swing it like a club. */
    public static final double GRIP_MAX_SPEED = 2.2;
    /** A held body moving at least this fast (blocks a tick) bats aside whatever it sweeps through. */
    public static final double SWING_HIT_SPEED = .55;
    /** A thrown body's speed, and how much a whip of the view (swing speed, blocks a tick) adds to it. */
    public static final double THROW_SPEED = 2.7, WHIP_BONUS = .8, MAX_WHIP = 2;

    /** Where a held body hangs: ahead and to the casting side, by yaw only, so looking up never swings it into the eye. */
    public static Vec3 holdPoint(Player player, Entity held) {
        Vec3 forward = Vec3.directionFromRotation(0, player.getYRot());
        Vec3 side = new Vec3(-forward.z, 0, forward.x).scale(player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1);
        double width = Mth.clamp(held.getBbWidth(), .3, 4);
        // The look's pitch lifts or lowers the hold a little, so a swing up or down moves the body.
        double pitch = Mth.clamp(-player.getXRot() / 90.0, -1, 1);
        return player.position().add(forward.scale(2.8 + width * .25)).add(side.scale(1.5 + width * .5))
                .add(0, Math.max(.7, 1.6 - held.getBbHeight() * .35) + pitch * 1.4, 0);
    }

    /** The far hand's size round something of this width and height: a hand a little bigger than what it holds. */
    public static float handScale(double width, double height) {
        return (float) Mth.clamp(Math.max(width * 1.35, height * .62), .5, 3.5);
    }

    // ---- Bullet Catch: a cupped hand ahead; shots hang in a fan before it
    public static final int MAX_CAUGHT = 12, STILL_AIR_CAUGHT = 6;
    /** How close a shot must come (blocks) to be caught; Still Air catches anything that reaches this round a hover. */
    public static final double CATCH_RADIUS = 6, STILL_AIR_RADIUS = 3.5;

    /**
     * Where the {@code index}th caught shot hangs: two arcs ahead, one each side of the view, held in the cupped hands
     * and climbing outward, so the middle of the view (and first person's sight) stays clear.
     */
    public static Vec3 shotSlot(Player player, int index) {
        Vec3 forward = Vec3.directionFromRotation(0, player.getYRot());
        Vec3 side = new Vec3(-forward.z, 0, forward.x);
        double sign = index % 2 == 0 ? 1 : -1;
        int step = index / 2;
        double angle = sign * (.62 + .16 * step), radius = 1.8 + .06 * step;
        Vec3 out = forward.scale(Math.cos(angle) * radius).add(side.scale(Math.sin(angle) * radius));
        return player.position().add(out).add(0, 1.0 + .14 * step, 0);
    }

    /** The middle of the cupped hand, ahead of the chest. */
    public static Vec3 catchHand(Player player) {
        Vec3 forward = Vec3.directionFromRotation(0, player.getYRot());
        return player.position().add(forward.scale(1.4)).add(0, 1.25, 0);
    }

    // ---- Disarm: pluck the weapon, then fling it back
    /** How long a plucked weapon can still be flung with a second press. */
    public static final int FLING_WINDOW = 60;
    public static final double FLING_SPEED = 2.2;

    // ---- Telekinetic Storm: gather, lift and turn, slam
    public static final int STORM_GATHER = 20, STORM_LIFT = 45, STORM_RADIUS = 12;
    /** How high the storm lifts, and the whole piece's length (the slam is a piece of its own). */
    public static final double STORM_HEIGHT = 3.5;
    public static final int STORM_TICKS = STORM_GATHER + STORM_LIFT;

    // ---- Pin (Sylar's): the palm drives a body back along the aim and holds it against what it meets
    public static final double PIN_BASE_RANGE = 12, PIN_DRIVE = 10, PIN_SPEED = 1.6;
    public static final int PIN_HOLD = 60, PIN_HOLD_AIR = 30, PIN_TOGETHER = 2;
    public static final float PIN_DAMAGE = 6;

    // ---- Round Up and Juggle
    public static final double ROUND_UP_RADIUS = 4.5;
    public static final int ROUND_UP_TICKS = 100;
    public static final double JUGGLE_RADIUS = 5, JUGGLE_POP = .95;
    public static final int JUGGLE_STAGGER = 4, JUGGLE_GAP = 14, JUGGLE_TARGETS = 3, JUGGLE_POPS = 3;
    public static final float JUGGLE_DAMAGE = 2, SPIKE_DAMAGE = 5;

    // ---- Levitate
    public static final double LEVITATE_HEIGHT = 6, RAISE_RADIUS = 3;
    public static final float LEVITATE_SPEED = .018F;

    // ---- Skyfall (the signature): haul a foe into the sky, hold it, drive it down where it stood
    public static final int SKYFALL_HAUL = 24, SKYFALL_HANG = 10, SKYFALL_DROP = 6;
    public static final int SKYFALL_IMPACT = SKYFALL_HAUL + SKYFALL_HANG + SKYFALL_DROP;
    public static final double SKYFALL_HEIGHT = 16, SKYFALL_RADIUS = 5, SKYFALL_BASE_RANGE = 12;

    /** How high the hauled body is {@code t} ticks into a Skyfall, from 0 (where it stood) to 1 (the top). */
    public static double skyfallLift(float t) {
        if (t < SKYFALL_HAUL) { float u = t / SKYFALL_HAUL; return u * u * (3 - 2 * u); }
        if (t < SKYFALL_HAUL + SKYFALL_HANG) return 1;
        float u = Mth.clamp((t - SKYFALL_HAUL - SKYFALL_HANG) / SKYFALL_DROP, 0, 1);
        return 1 - u * u;
    }

    /** How full the warning ring on the ground is: it fills over the haul and the hang and is full as the drop starts. */
    public static float skyfallWarning(float t) { return Mth.clamp(t / (SKYFALL_HAUL + SKYFALL_HANG), 0, 1); }

    // ---- Soft Landing's catch
    public static final double CATCH_FALL = 5, CATCH_REACH = 16;
    public static final int CATCH_COOLDOWN = 400;
}
