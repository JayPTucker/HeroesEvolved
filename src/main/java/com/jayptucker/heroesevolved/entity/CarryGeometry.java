package com.jayptucker.heroesevolved.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * How a carrier holds what it carries, derived from the carrier's locally known position on both
 * sides so the server, the carrier's client and every onlooker agree.
 *
 * <ul>
 *   <li><b>{@link Hold#ARMS}</b>: a person (a player, a villager, a zombie) lies across both arms in
 *   front of your chest, a bridal carry, head toward your left arm and knees over your right.</li>
 *   <li><b>{@link Hold#CRADLE}</b>: anything else about your size or smaller (a pig, a sheep, a cat) is
 *   held in both arms against your chest.</li>
 *   <li><b>{@link Hold#OVERHEAD}</b>: only Flight's strength lifts something bigger, and it goes up over
 *   your head.</li>
 * </ul>
 *
 * <p>Local coordinates here are x to the carrier's left, y up and z forward. Left-handed carriers are
 * mirrored. The held entity's real position (the seat) is chosen so a carried player's own camera sits
 * where their head is drawn; the model itself is posed by the renderer around the same points.</p>
 */
public final class CarryGeometry {
    public enum Hold { ARMS, CRADLE, OVERHEAD }

    /** The most a person can carry in their arms; larger cargo needs Flight's overhead lift. */
    public static final double ARMS_WIDTH = 1.0, ARMS_HEIGHT = 2.1, LIFT_WIDTH = 2.0, LIFT_HEIGHT = 3.0;
    /** Bridal carry: where the hips rest between the arms, and how far the back reclines from flat. */
    public static final Vec3 ARMS_HIP = new Vec3(0.05, 0.98, 0.5);
    public static final double RECLINE_DEGREES = 38, HIP_HEIGHT = 0.75;
    private static final double CRADLE_HEIGHT = .85;

    private CarryGeometry() {}

    /** A body shaped like a person: drawn lying across the arms instead of simply held. */
    public static boolean biped(Entity e) {
        return e instanceof Player || e instanceof AbstractVillager || e instanceof AbstractIllager || e instanceof Witch
                || e instanceof Zombie || e instanceof AbstractSkeleton || e instanceof AbstractPiglin;
    }

    public static Hold hold(Entity held) { return hold(held.getBbWidth(), held.getBbHeight(), biped(held)); }

    public static Hold hold(double width, double height, boolean biped) {
        if (width <= ARMS_WIDTH + 1.0E-4 && height <= ARMS_HEIGHT + 1.0E-4) return biped && height >= 1.3 ? Hold.ARMS : Hold.CRADLE;
        return Hold.OVERHEAD;
    }

    /** Whether {@code held} can be carried at all: in the arms, or overhead when {@code lift} allows it. */
    public static boolean liftable(Entity held, boolean lift) {
        double w = held.getBbWidth(), h = held.getBbHeight();
        return w <= ARMS_WIDTH + 1.0E-4 && h <= ARMS_HEIGHT + 1.0E-4 || lift && w <= LIFT_WIDTH && h <= LIFT_HEIGHT;
    }

    /** +1 for a right-handed carrier (head on the left arm), -1 mirrored. */
    public static int side(Entity carrier) {
        return carrier instanceof Player player && player.getMainArm() == HumanoidArm.LEFT ? -1 : 1;
    }

    /** Carrier-local (x left, y up, z forward) to a world offset, for a carrier facing {@code yaw}. */
    public static Vec3 rotate(Vec3 local, float yaw) {
        double radians = Math.toRadians(Float.isFinite(yaw) ? yaw : 0);
        double sin = Math.sin(radians), cos = Math.cos(radians);
        // Left is (cos, 0, sin) and forward is (-sin, 0, cos) for Minecraft yaw.
        return new Vec3(local.x * cos - local.z * sin, local.y, local.x * sin + local.z * cos);
    }

    /** The direction the reclined body runs from hips to head, carrier-local. */
    public static Vec3 spine(int side) {
        double r = Math.toRadians(RECLINE_DEGREES);
        return new Vec3(Math.cos(r) * side, Math.sin(r), 0);
    }

    /**
     * The point the renderer poses the model around, carrier-local: the hips for a bridal carry, the
     * middle of the body for a cradle, and the feet for an overhead lift.
     */
    public static Vec3 pivot(Hold hold, double carrierWidth, double carrierHeight, double width, double height, int side) {
        return switch (hold) {
            case ARMS -> new Vec3(ARMS_HIP.x * side, ARMS_HIP.y, ARMS_HIP.z);
            case CRADLE -> new Vec3(0, CRADLE_HEIGHT, dimension(carrierWidth, .6) * .5 + dimension(width, .6) * .5 + .05);
            case OVERHEAD -> new Vec3(0, dimension(carrierHeight, 1.8) + .15, 0);
        };
    }

    /** The held entity's real position, carrier-local. A carried player's eyes are where their head is drawn. */
    public static Vec3 seat(Hold hold, double carrierWidth, double carrierHeight, double width, double height, double eye, int side) {
        Vec3 pivot = pivot(hold, carrierWidth, carrierHeight, width, height, side);
        return switch (hold) {
            case ARMS -> {
                double eyes = Math.clamp(eye, HIP_HEIGHT + .1, 3);
                yield pivot.add(spine(side).scale(eyes - HIP_HEIGHT)).subtract(0, eyes, 0);
            }
            case CRADLE -> pivot.subtract(0, dimension(height, 1.8) * .5, 0);
            case OVERHEAD -> pivot;
        };
    }

    public static Vec3 position(Entity carrier, Entity held) {
        return position(carrier, hold(held), held.getBbWidth(), held.getBbHeight(), held.getEyeHeight());
    }

    /** Dimension hints let the seat settle correctly before the passenger's spawn packet arrives. */
    public static Vec3 position(Entity carrier, Hold hold, double width, double height, double eye) {
        Vec3 local = seat(hold, carrier.getBbWidth(), carrier.getBbHeight(), width, height, eye, side(carrier));
        return carrier.position().add(rotate(local, carrier.getYRot()));
    }

    /**
     * Whether the carry still fits where the carrier is. Arms and cradle cargo stays within the
     * carrier's own height, so only a crawling carrier (a ceiling at chest height) loses its grip;
     * an overhead lift needs the whole space above the carrier's head.
     */
    public static boolean fits(Entity carrier, Hold hold, double width, double height, Vec3 seat) {
        if (hold == Hold.OVERHEAD) return clear(carrier, width, height, seat);
        Vec3 at = carrier.position();
        AABB chest = new AABB(at.x - .25, at.y + .75, at.z - .25, at.x + .25, at.y + 1.4, at.z + .25);
        return Double.isFinite(seat.lengthSqr()) && !carrier.level().getBlockCollisions(carrier, chest).iterator().hasNext();
    }

    public static boolean fits(Entity carrier, Entity held, Vec3 seat) {
        return fits(carrier, hold(held), held.getBbWidth(), held.getBbHeight(), seat);
    }

    /** Where cargo is let go: on the ground just in front of the carrier. */
    public static Vec3 releasePoint(Entity carrier, Entity held) {
        double reach = dimension(carrier.getBbWidth(), .6) * .5 + dimension(held.getBbWidth(), .6) * .5 + .25;
        return carrier.position().add(rotate(new Vec3(0, 0, reach), carrier.getYRot()));
    }

    public static AABB box(Entity held, Vec3 feet) {
        return box(held.getBbWidth(), held.getBbHeight(), feet);
    }

    public static AABB box(double width, double height, Vec3 feet) {
        double half = dimension(width, .6D) * .5D;
        return new AABB(feet.x - half, feet.y, feet.z - half,
                feet.x + half, feet.y + dimension(height, 1.8D), feet.z + half);
    }

    /** Checks blocks only: a carry seat must not jitter between slots because another entity walks by. */
    public static boolean clear(Entity carrier, double heldWidth, double heldHeight, Vec3 feet) {
        if (!Double.isFinite(feet.lengthSqr())) return false;
        AABB bounds = box(heldWidth, heldHeight, feet).deflate(.001D);
        var level = carrier.level();
        if (bounds.minY < level.getMinBuildHeight() || bounds.maxY >= level.getMaxBuildHeight()
                || !level.getWorldBorder().isWithinBounds(bounds)) return false;
        int minChunkX = Mth.floor(bounds.minX) >> 4, maxChunkX = Mth.floor(bounds.maxX) >> 4;
        int minChunkZ = Mth.floor(bounds.minZ) >> 4, maxChunkZ = Mth.floor(bounds.maxZ) >> 4;
        int y = Mth.floor(feet.y);
        for (int x = minChunkX; x <= maxChunkX; x++)
            for (int z = minChunkZ; z <= maxChunkZ; z++)
                if (!level.hasChunkAt(new BlockPos(x << 4, y, z << 4))) return false;
        return !level.getBlockCollisions(carrier, bounds).iterator().hasNext();
    }

    private static double dimension(double value, double fallback) {
        return Double.isFinite(value) && value > 0 ? Math.clamp(value, .01D, 32D) : fallback;
    }
}
