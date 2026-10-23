package com.jayptucker.heroesevolved.cryo;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** A hemisphere above the caster, with six blocks of frost penetration below its base. */
public final class ColdDomeShape {
    private ColdDomeShape() {}
    public static boolean contains(Vec3 center, Vec3 point, double radius) {
        Vec3 delta = point.subtract(center);
        double above = Math.max(0, delta.y);
        return delta.y >= -6 && delta.x * delta.x + delta.z * delta.z + above * above <= radius * radius;
    }
    public static AABB bounds(Vec3 center, double radius) {
        return new AABB(center.x - radius, center.y - 6, center.z - radius,
                center.x + radius, center.y + radius, center.z + radius);
    }
}
