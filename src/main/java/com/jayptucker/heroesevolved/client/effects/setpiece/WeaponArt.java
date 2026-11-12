package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient.Active;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.*;
import static net.minecraft.util.Mth.*;

/**
 * The power-themed TaCZ guns, drawn in one shape language: a tapered ribbon from the muzzle. The heat gun's is a
 * short rolling tongue of flame; the cold gun's a pale streak led by an ice shard, which breaks into a star of shards
 * where it lands; the taser's two thin wires, sagging slightly, with current crawling along them and over whoever the
 * barbs bit. The palettes echo the powers they imitate (Pyrokinesis's embers, Cryokinesis's ice, Electrokinesis's
 * ions) but run thinner and shorter: these are machines, not powers.
 */
final class WeaponArt {
    private static final float[] EMBER_EDGE = rgb(0xA32813), EMBER = rgb(0xF76A13), EMBER_GOLD = rgb(0xFFBE42);
    private static final float[] ICE = rgb(0xA6D3E5), MIST = rgb(0xDCEBF1), ICE_WHITE = rgb(0xF4FAFC);
    private static final float[] WIRE = rgb(0x2A2A2E), ION = rgb(0x84D5FF), ION_CORE = rgb(0xEAF5FF);
    private static final Vec3 UP = new Vec3(0, 1, 0);

    private WeaponArt() {}

    static void draw(Canvas c, Active a, float t) {
        if (c.solid()) return;
        switch (a.piece()) {
            case GUN_HEAT -> heat(c, a, t);
            case GUN_FROST -> { if (a.scale() >= 1) burst(c, a, t); else frost(c, a, t); }
            case GUN_TASER -> taser(c, a, t);
            default -> {}
        }
    }

    // ------------------------------------------------------------------

    private static void heat(Canvas c, Active a, float t) {
        Vec3 path = a.target().subtract(a.origin());
        Vec3 at = muzzle(c, a.bound(c), a.origin());
        Vec3 end = at.add(path);
        float alpha = fade(t, a.piece().ticks, 0.6F, 4);
        float front = smooth(0, 2.5F, t), tail = smooth(3, a.piece().ticks, t) * 0.7F;
        Vec3 head = at.lerp(end, front), back = at.lerp(end, tail);
        float phase = (c.level.getGameTime() % 2400 + c.partial) * 0.5F;
        if (c.glow()) {
            c.glow(at, 0.22F, EMBER_GOLD, alpha * 0.55F);
            c.glow(head, 0.5F, EMBER, alpha * 0.18F);
            return;
        }
        int tongues = minimal() ? 2 : 4;
        for (int i = 0; i < tongues; i++) {
            // Tongues of different lengths that curl apart, so the stream breaks up instead of reading as a beam.
            float spread = 0.14F + 0.07F * i;
            Vec3 tip = back.lerp(head, 0.62F + 0.38F * frac(hash(a.seed(), i) + phase * 0.11F));
            tongue(c, back, tip, 0.05F, 0.3F + 0.08F * i, spread, phase * (1 + 0.2F * i) + i * 2.1F, alpha * (i == 0 ? 0.9F : 0.6F), a.seed() + i);
        }
    }

    /** One tongue of flame: three nested ribbons (edge, body, hot core) that roll as they widen. */
    private static void tongue(Canvas c, Vec3 from, Vec3 to, float w0, float w1, float spread, float phase, float alpha, int seed) {
        Vec3 axis = to.subtract(from);
        if (axis.lengthSqr() < 0.01 || alpha < 0.01F) return;
        Vec3 right = side(axis.normalize()), up = axis.normalize().cross(right);
        int n = 9;
        Vec3[] spine = new Vec3[n];
        float[] edge = new float[n], body = new float[n], core = new float[n], fade = new float[n], hot = new float[n];
        for (int i = 0; i < n; i++) {
            float p = i / (n - 1F);
            float curl = sin(phase - p * 6.0F + hash(seed, 1) * 6), fold = cos(phase * 0.8F - p * 4.4F);
            spine[i] = from.lerp(to, p).add(right.scale(curl * spread * p * 2)).add(up.scale(fold * spread * p * 1.5 + p * p * 0.25));
            float w = lerp(p, w0, w1);
            edge[i] = w * 1.6F;
            body[i] = w * 1.1F;
            core[i] = w * 0.55F;
            fade[i] = alpha * (1 - smooth(0.55F, 1, p)) * smooth(0, 0.08F, p + 0.02F);
            hot[i] = alpha * (1 - smooth(0.15F, 0.6F, p));
        }
        c.ribbon(spine, edge, EMBER_EDGE, fade);
        c.ribbon(spine, body, EMBER, fade);
        c.ribbon(spine, core, EMBER_GOLD, hot);
    }

    // ------------------------------------------------------------------

    private static void frost(Canvas c, Active a, float t) {
        Vec3 path = a.target().subtract(a.origin());
        Vec3 at = muzzle(c, a.bound(c), a.origin());
        Vec3 end = at.add(path);
        float flight = (float) clamp(path.length() / 9, 1, 5);
        float front = smooth(0, flight, t), tail = smooth(flight * 0.4F, flight + 5, t);
        float alpha = fade(t, a.piece().ticks, 0.5F, 5);
        Vec3 head = at.lerp(end, front), back = at.lerp(end, tail);
        if (c.glow()) {
            c.glow(at, 0.18F, ICE_WHITE, alpha * (1 - smooth(0, 3, t)) * 0.7F);
            if (t < flight + 1) c.glow(head, 0.28F, ICE, alpha * 0.5F);
            return;
        }
        if (head.distanceToSqr(back) > 0.01) {
            c.line(back, head, 0.07F, MIST, 0, alpha * 0.55F);
            c.line(back, head, 0.025F, ICE_WHITE, 0, alpha * 0.8F);
        }
        if (t < flight + 0.5F) {
            Vec3 dir = path.normalize();
            c.shard(head.subtract(dir.scale(0.35)), head.add(dir.scale(0.15)), 0.06F, 5, t * 0.4F, ICE, alpha * 0.8F);
        }
    }

    private static void burst(Canvas c, Active a, float t) {
        Vec3 at = a.origin();
        float grow = smooth(0, 3, t), alpha = fade(t, a.piece().ticks, 0.5F, 7);
        if (c.glow()) {
            c.glow(at, 0.45F * grow, ICE_WHITE, alpha * 0.45F);
            return;
        }
        int count = minimal() ? 4 : 7;
        for (int i = 0; i < count; i++) {
            float yaw = TWO_PI * hash(a.seed(), i), pitch = (hash(a.seed(), i + 20) - 0.3F) * 1.6F;
            Vec3 dir = new Vec3(cos(yaw) * cos(pitch), sin(pitch), sin(yaw) * cos(pitch));
            float length = (0.25F + 0.3F * hash(a.seed(), i + 40)) * grow;
            c.shard(at, at.add(dir.scale(length)), 0.045F + 0.03F * hash(a.seed(), i + 60), 4, i, ICE, alpha * 0.85F);
        }
        c.ring(at, 0.2F + 0.5F * grow, 0.05F, MIST, alpha * 0.5F * (1 - grow * 0.5F));
    }

    // ------------------------------------------------------------------

    private static void taser(Canvas c, Active a, float t) {
        Entity target = a.bound(c);
        Vec3 hit = target != null ? target.getPosition(c.partial).add(0, target.getBbHeight() * 0.55, 0) : a.target();
        Vec3 from = a.origin();
        float alpha = fade(t, a.piece().ticks, 0.5F, 5);
        float reach = smooth(0, 1.5F, t);
        long tick = c.level.getGameTime();
        for (int side = -1; side <= 1; side += 2) {
            Vec3 barb = hit.add(side * 0.12, side * 0.18, 0);
            Vec3 tip = from.lerp(barb, reach);
            Vec3[] wire = new Vec3[8];
            for (int i = 0; i < wire.length; i++) {
                float p = i / 7F;
                wire[i] = from.lerp(tip, p).add(0, -sin(PI * p) * 0.12 * reach - side * 0.02, 0);
            }
            if (!c.glow()) c.ribbon(wire, 0.012F, WIRE, alpha, alpha);
            else if (reach >= 1 && (tick + side) % 3 != 0)
                c.bolt(from, barb, (int) (tick * 31 + side), 0.08F, 0.012F, ION, alpha * 0.8F);
        }
        if (c.glow() && reach >= 1 && target instanceof LivingEntity body && !minimal()) {
            // Current crawling over the body the barbs bit.
            float h = body.getBbHeight(), w = body.getBbWidth();
            Vec3 feet = body.getPosition(c.partial);
            for (int i = 0; i < 3; i++) {
                int seed = (int) (tick * 7 + i * 13);
                Vec3 p0 = feet.add((hash(seed, 1) - 0.5) * w, h * hash(seed, 2), (hash(seed, 3) - 0.5) * w);
                Vec3 p1 = feet.add((hash(seed, 4) - 0.5) * w, h * hash(seed, 5), (hash(seed, 6) - 0.5) * w);
                c.bolt(p0, p1, seed, 0.1F, 0.01F, ION, alpha * 0.7F);
            }
            c.glow(hit, 0.25F, ION_CORE, alpha * 0.4F);
        }
    }

    // ------------------------------------------------------------------

    /**
     * The muzzle of whoever fired, this frame: in first person, low and right of the camera where TaCZ draws the gun;
     * otherwise forward and right of the eyes. Falls back to where the server said.
     */
    private static Vec3 muzzle(Canvas c, Entity shooter, Vec3 fallback) {
        if (!(shooter instanceof LivingEntity living)) return fallback;
        Minecraft mc = Minecraft.getInstance();
        Vec3 look = living.getViewVector(c.partial);
        Vec3 right = Vec3.directionFromRotation(0, living.getViewYRot(c.partial) + 90);
        if (living == mc.player && mc.options.getCameraType().isFirstPerson()) {
            Vec3 cameraUp = right.cross(look).normalize();
            return c.camera().add(look.scale(0.95)).add(right.scale(0.24)).subtract(cameraUp.scale(0.2));
        }
        return living.getEyePosition(c.partial).add(look.scale(0.7)).add(right.scale(0.28)).add(0, -0.22, 0);
    }

    private static Vec3 side(Vec3 axis) { return (Math.abs(axis.y) > 0.93 ? new Vec3(1, 0, 0) : UP).cross(axis).normalize(); }

    private static boolean minimal() { return Minecraft.getInstance().options.particles().get() == ParticleStatus.MINIMAL; }
}
