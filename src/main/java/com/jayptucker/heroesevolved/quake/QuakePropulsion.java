package com.jayptucker.heroesevolved.quake;

import net.minecraft.world.phys.Vec3;

/** Recoil follows the full aim vector while preserving jump and sideways momentum. */
public final class QuakePropulsion {
    private QuakePropulsion() {}

    public static Vec3 recoil(Vec3 velocity, Vec3 aim, double impulse, double maximumAlong) {
        Vec3 away = aim.normalize().reverse();
        double available = Math.max(0, maximumAlong - velocity.dot(away));
        return velocity.add(away.scale(Math.min(impulse, available)));
    }
}
