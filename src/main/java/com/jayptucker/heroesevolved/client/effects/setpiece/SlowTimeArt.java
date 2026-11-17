package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.client.effects.SlowTimeEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The rings fast things leave in thick, slowed air ({@link SlowTimeEffects}): thin, pale rings like the wake a bullet
 * drags through slowed time on screen. A shot leaves a corkscrew of small ones; a wading speedster's feet send ripples
 * across the ground, their body pushes rings off behind it, and the air piles up ahead of their shins like a bow wave.
 * In stopped time the rings hold still and turn the cold, pale violet of stopped time.
 * <p>
 * Every ring is three bands (a wide faint halo, the body, a thin bright core) over a faint slate edge drawn with normal
 * blending, so it still reads against a bright sky.
 */
final class SlowTimeArt {
    private static final float[] AIR = Canvas.rgb(0xC9D8EE), CORE = Canvas.rgb(0xF4F8FF), HALO = Canvas.rgb(0x8FA6C8),
            SHADE = Canvas.rgb(0x5A6478), ICE = Canvas.rgb(0xA996FF), ICE_CORE = Canvas.rgb(0xE6DCFF), ICE_HALO = Canvas.rgb(0x6A4FD8);

    private SlowTimeArt() {}

    static boolean live() { return SlowTimeEffects.live(); }

    static void drawLive(Canvas c) {
        if (c.solid()) return;
        Vec3 eye = c.camera();
        for (SlowTimeEffects.Ring ring : SlowTimeEffects.rings()) {
            if (ring.at.distanceToSqr(eye) > 64 * 64) continue;
            float age = ring.age + c.partial * ring.step, k = age / ring.life;
            if (k >= 1) continue;
            float fade = Canvas.fade(age, ring.life, 1.5F, ring.life * 0.6F);
            // A ring right on the camera (a shot coming at you) would only fill the view.
            fade *= Mth.clamp(((float) ring.at.distanceTo(eye) - 0.3F) / 1.2F, 0, 1);
            if (fade <= 0.01F) continue;
            float radius = ring.radius * (1 + ring.grow * (1 - (1 - k) * (1 - k)));
            // Stopped, the air turns the cold violet of stopped time.
            boolean frozen = ring.step <= 0;
            float[] body = frozen ? ICE : AIR, core = frozen ? ICE_CORE : CORE, halo = frozen ? ICE_HALO : HALO;
            switch (ring.kind) {
                case SlowTimeEffects.Ring.SHOT -> ring(c, ring.at, ring.axis, radius, 0.012F + ring.radius * 0.08F, body, core, halo, fade);
                case SlowTimeEffects.Ring.STEP -> {
                    ring(c, ring.at, Canvas.UP, radius, 0.03F, body, core, halo, 0.55F * fade);
                    // A second, smaller ripple follows the first out.
                    if (k > 0.15F) ring(c, ring.at, Canvas.UP, radius * 0.62F, 0.022F, body, core, halo, 0.4F * fade);
                }
                default -> ring(c, ring.at, ring.axis, radius, 0.02F, body, core, halo, 0.2F * fade);
            }
        }
        Minecraft mc = Minecraft.getInstance();
        for (SlowTimeEffects.Runner runner : SlowTimeEffects.runners()) if (runner.wading) bow(c, runner, mc);
    }

    /** One ring of air: a faint slate edge (normal blending), then halo, body and core (additive). */
    private static void ring(Canvas c, Vec3 at, Vec3 axis, float radius, float width, float[] body, float[] core, float[] halo, float alpha) {
        if (c.soft()) c.ring(at, axis, radius, width * 2.2F, 0, Mth.TWO_PI, SHADE, alpha * 0.2F);
        if (!c.glow()) return;
        c.ring(at, axis, radius, width * 3.5F, 0, Mth.TWO_PI, halo, alpha * 0.22F);
        c.ring(at, axis, radius, width, 0, Mth.TWO_PI, body, alpha * 0.6F);
        c.ring(at, axis, radius, width * 0.35F, 0, Mth.TWO_PI, core, alpha);
    }

    /** The air piling up ahead of a wading speedster's legs, and the V of the wake spreading out behind. */
    private static void bow(Canvas c, SlowTimeEffects.Runner runner, Minecraft mc) {
        Vec3 velocity = new Vec3(runner.velocity.x, 0, runner.velocity.z);
        double speed = velocity.length();
        if (speed < 1e-3) return;
        Vec3 dir = velocity.scale(1 / speed), side = new Vec3(-dir.z, 0, dir.x);
        Vec3 feet = runner.player.getPosition(c.partial);
        boolean own = runner.player == mc.player && mc.options.getCameraType().isFirstPerson();
        float strength = Mth.clamp((float) (speed - SlowTimeEffects.WADE) / 1.2F + 0.35F, 0, 1) * (own ? 0.45F : 1);
        float facing = (float) Math.atan2(dir.x, dir.z);
        for (int band = 0; band < 3; band++) {
            Vec3 at = feet.add(dir.scale(0.12 + band * 0.04)).add(0, 0.14 + band * 0.27, 0);
            float radius = 0.42F + band * 0.05F + (float) Math.min(0.3, speed * 0.08);
            float alpha = strength * (0.5F - band * 0.12F);
            if (c.soft()) c.ring(at, Canvas.UP, radius, 0.06F, facing - 1.15F, facing + 1.15F, SHADE, alpha * 0.18F);
            if (c.glow()) {
                c.ring(at, Canvas.UP, radius, 0.12F, facing - 1.15F, facing + 1.15F, HALO, alpha * 0.25F);
                c.ring(at, Canvas.UP, radius, 0.035F, facing - 1.05F, facing + 1.05F, AIR, alpha * 0.6F);
                c.ring(at, Canvas.UP, radius, 0.012F, facing - 0.95F, facing + 0.95F, CORE, alpha);
            }
        }
        if (!c.glow()) return;
        // The wake: two lines along the ground, opening out behind the runner as water would.
        float length = (float) Math.min(3.2, 1.2 + speed * 1.2);
        Vec3 ground = feet.add(0, 0.05, 0);
        for (int s = -1; s <= 1; s += 2) {
            Vec3 from = ground.add(side.scale(0.3 * s)), to = ground.subtract(dir.scale(length)).add(side.scale((0.3 + length * 0.42) * s));
            c.line(from, to, 0.05F, HALO, 0.2F * strength, 0);
            c.line(from, to, 0.015F, CORE, 0.6F * strength, 0);
        }
    }
}
