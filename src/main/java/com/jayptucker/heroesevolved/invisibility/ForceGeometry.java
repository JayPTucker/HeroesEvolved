package com.jayptucker.heroesevolved.invisibility;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The shapes of the force fields, shared by the server (what blocks, holds and hits) and {@code ForceArt} (what is
 * drawn), so the picture never lies about the field.
 */
public final class ForceGeometry {
    private ForceGeometry() {}

    /** How far in front of the eyes the Lens Shield stands, and its half-width (Wide Lens adds half again). */
    public static final double SHIELD_DISTANCE = 1.15, SHIELD_RADIUS = 1.45, WIDE_SHIELD_RADIUS = 2.15;
    /** How far the shield's centre bows out toward the threat: the lens's sag at its middle. */
    public static final double SHIELD_SAG = 0.32;
    /** Force Push: the wave's speed in blocks a tick, its reach (Hard Push reaches farther) and its half-width. */
    public static final double PUSH_SPEED = 2.6, PUSH_RANGE = 10, HARD_PUSH_RANGE = 14, PUSH_RADIUS = 1.9;
    /** The Force Barrier's height in blocks. */
    public static final int WALL_HEIGHT = 4;
    /** Force Nova's reach. */
    public static final double NOVA_RADIUS = 12;
    /** Shelter bubbles cover friends this close to the caster. */
    public static final double SHELTER_REACH = 3.5;
    /** A variant flag packed into a set piece's scale (all real sizes stay well below it). */
    public static final float SHELTER_FLAG = 32;

    /** The middle of the Lens Shield for a player looking along {@code look} from {@code eye}. */
    public static Vec3 shieldCentre(Vec3 eye, Vec3 look) { return eye.add(look.scale(SHIELD_DISTANCE)); }

    /** Whether a point lies on the shield's face: in front of it, near its plane and within its radius. */
    public static boolean onShield(Vec3 eye, Vec3 look, double radius, Vec3 point, double thickness) {
        Vec3 centre = shieldCentre(eye, look), offset = point.subtract(centre);
        double along = offset.dot(look);
        double across = offset.subtract(look.scale(along)).length();
        return across <= radius && along >= -thickness && along <= thickness + SHIELD_SAG;
    }

    /** Whether a threat coming from {@code from} meets the shield's face (it covers about 70° each side of the aim). */
    public static boolean shieldFaces(Vec3 eye, Vec3 look, Vec3 from) {
        Vec3 to = from.subtract(eye);
        return to.lengthSqr() < 1e-6 || to.normalize().dot(look) > 0.34;
    }

    /**
     * One cell of a Force Bridge. The bridge runs along a compass direction from {@code origin} (the walker's feet
     * over the first step), rising {@code slope} blocks per step; {@code lane} is −1, 0 or 1 across its width. The
     * client rebuilds the same cells from the piece's origin and target, so the drawn steps are the solid ones.
     */
    public static BlockPos bridgeCell(Vec3 origin, Direction dir, double slope, int i, int lane) {
        Direction side = dir.getClockWise();
        return BlockPos.containing(origin.x + dir.getStepX() * i + side.getStepX() * lane, 0,
                origin.z + dir.getStepZ() * i + side.getStepZ() * lane).atY(Mth.floor(origin.y + slope * i + 1e-4) - 1);
    }

    /** The radius of a bubble round this body: a sphere that clears its bounding box with a little room. */
    public static double bubbleRadius(Entity e) {
        double w = e.getBbWidth(), h = e.getBbHeight();
        return Mth.clamp(Math.sqrt(w * w + h * h) * 0.5 + 0.35, 0.8, 3.5);
    }

    /** The bubble's centre: the middle of the body. */
    public static Vec3 bubbleCentre(Entity e) { return e.position().add(0, e.getBbHeight() * 0.5, 0); }

    /** How a crushed bubble shrinks: from its full size to 35% over {@code ticks}, slowing as it closes. */
    public static double crushScale(float age, float ticks) {
        float k = Mth.clamp(age / ticks, 0, 1);
        return 1 - 0.65 * (1 - (1 - k) * (1 - k));
    }
}
