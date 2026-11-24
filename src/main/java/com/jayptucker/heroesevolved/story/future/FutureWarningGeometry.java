package com.jayptucker.heroesevolved.story.future;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** One beat sheet for the actor, the breach and the light that drags them away. */
public final class FutureWarningGeometry {
    public static final int OMEN = 90, TURN = 30, PULL = 180, SHUT = 240, END = 260;
    public static final float BOOM_VOLUME = 10;
    private FutureWarningGeometry() {}
    public static float smooth(float x) { x = Mth.clamp(x, 0, 1); return x * x * (3 - 2 * x); }
    public static float pulled(float age) { return smooth((age - PULL) / (SHUT - PULL - 8)); }
    public static Vec3 actor(Vec3 center, Vec3 normal, float age) {
        float out = smooth(age / 45), pull = pulled(age);
        return center.add(0, -1.15 + .45 * pull, 0).add(normal.scale(-.65 + 1.3 * out - 2.4 * pull));
    }
}
