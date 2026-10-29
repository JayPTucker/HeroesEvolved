package com.jayptucker.heroesevolved.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Shared carry seat geometry, derived from the carrier's locally known position on both sides. */
public final class CarryGeometry {
    private static final double FOOT_HEIGHT = .10D;
    private static final double FORWARD_OFFSET = -.20D;
    private static final double CLEARANCE = .35D;

    private CarryGeometry() {}

    public static Vec3 position(Entity carrier, Entity held) {
        return position(carrier, held.getBbWidth(), held.getBbHeight());
    }

    /** Dimension hints let the seat settle correctly before the passenger's spawn packet arrives. */
    public static Vec3 position(Entity carrier, double heldWidth, double heldHeight) {
        double width = dimension(heldWidth, .6D);
        double height = dimension(heldHeight, 1.8D);
        boolean rightHanded = !(carrier instanceof Player player) || player.getMainArm() == HumanoidArm.RIGHT;
        Vec3 preferred = carrier.position().add(offset(carrier.getYRot(), carrier.getBbWidth(), width, rightHanded));
        Vec3 opposite = carrier.position().add(offset(carrier.getYRot(), carrier.getBbWidth(), width, !rightHanded));
        double clearance = clearance(carrier.getBbWidth(), width);
        Vec3 forward = Vec3.directionFromRotation(0, carrier.getYRot());
        Vec3 behind = carrier.position().add(forward.scale(-clearance)).add(0, FOOT_HEIGHT, 0);
        for (Vec3 candidate : new Vec3[]{preferred, opposite, behind})
            if (clear(carrier, width, height, candidate)) return candidate;
        // Do not fall back inside the carrier's body or interaction ray. The service
        // releases cargo at its last safe point when every exterior seat is blocked.
        return preferred;
    }

    /** Pure, right-handed preferred offset; yaw zero faces positive Z. */
    public static Vec3 offset(float yaw, double carrierWidth, double heldWidth) {
        return offset(yaw, carrierWidth, heldWidth, true);
    }

    public static Vec3 offset(float yaw, double carrierWidth, double heldWidth, boolean rightHanded) {
        Vec3 forward = Vec3.directionFromRotation(0, Float.isFinite(yaw) ? yaw : 0);
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        double clearance = clearance(carrierWidth, heldWidth);
        return right.scale(rightHanded ? clearance : -clearance)
                .add(forward.scale(FORWARD_OFFSET)).add(0, FOOT_HEIGHT, 0);
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

    private static double clearance(double carrierWidth, double heldWidth) {
        // Entity hitboxes stay axis-aligned when they turn. Their diagonal radius keeps
        // even wide cargo outside the forward ray at diagonal headings, without seat wobble.
        return (dimension(carrierWidth, .6D) + dimension(heldWidth, .6D)) * Math.sqrt(.5D) + CLEARANCE;
    }
}
