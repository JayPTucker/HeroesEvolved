package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient.Active;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.*;

/** Life, foresight, empathy and assembled matter (minds are drawn by TelepathyArt). */
final class MindArt {
    private MindArt() {}

    private static final float[] WHITE = {1, 1, 1};
    private static final float[] BLOOD = rgb(0xE0303C), LIFE_GOLD = rgb(0xFFD98A), LEAF = rgb(0x8CFF8A), BLOOM = rgb(0xFFB8E0);
    private static final float[] HOLO = rgb(0x5CFFD0), HOLO_DEEP = rgb(0x1E9C8A);
    private static final float[] CRYSTAL = rgb(0xDCE8F8), CRYSTAL_EDGE = rgb(0x9FD0FF), LATTICE = rgb(0xFFE6A8);

    // ------------------------------------------------------------------
    // Regeneration
    // ------------------------------------------------------------------

    /** A heartbeat of light, twice: crimson shells spreading off them, veins of it running out through the ground. */
    static void vitalBurst(Canvas c, Active a, float t) {
        float r = a.scale();
        Vec3 o = a.origin();
        if (c.soft()) for (int beat = 0; beat < 2; beat++) {
            float p = Mth.clamp((t - beat * 6) / 14, 0, 1);
            if (p > 0) c.shell(o.add(0, 1, 0), r * p, r * p * 0.8F, r * p, Mth.PI, BLOOD, 0.35F * (1 - p), 0, PLAIN, 0);
        }
        if (!c.glow()) return;
        float f = 1 - t / a.piece().ticks;
        for (int i = 0; i < 10; i++) {
            float ang = i * Mth.TWO_PI / 10 + hash(a.seed(), i), len = r * Mth.clamp(t / 8, 0, 1);
            Vec3[] p = new Vec3[7];
            for (int k = 0; k <= 6; k++) {
                float kk = k / 6F, wob = (hash(a.seed(), i * 7 + k) - 0.5F) * 0.6F;
                p[k] = o.add(Mth.cos(ang + wob * kk) * len * kk, 0.05, Mth.sin(ang + wob * kk) * len * kk);
            }
            c.ribbon(p, 0.1F, BLOOD, 0.9F * f, 0.1F * f);
        }
        if (t > 8) for (int i = 0; i < 12; i++) {
            float k = Mth.clamp((t - 8 - i) / 10, 0, 1), ang = i * Mth.TWO_PI / 12;
            if (k > 0 && k < 1) c.glow(o.add(Mth.cos(ang) * r * (1 - k), 1 + k * 0.2, Mth.sin(ang) * r * (1 - k)), 0.2F, LIFE_GOLD, 1 - k);
        }
        if (t < 4) c.glow(o.add(0, 1, 0), 3, BLOOD, 1 - t / 4);
    }

    /** White-gold strands wind tight round them like a cocoon, under a halo. */
    static void undying(Canvas c, Active a, float t) {
        if (!c.glow()) return;
        float f = fade(t, a.piece().ticks, 8, 16);
        Vec3 o = a.at(c);
        float wrap = smooth(0, 14, t);
        for (int k = 0; k < 6; k++) c.helix(o, 2.1F, 1.4F - wrap * 0.75F, 0.45F, 1.6F, k * Mth.TWO_PI / 6 + t * 0.07F * (k % 2 == 0 ? 1 : -1), 0.07F,
                k % 2 == 0 ? LIFE_GOLD : WHITE, 0.7F * f, 26);
        c.ring(o.add(0, 2.35, 0), UP, 0.38F, 0.05F, 0, Mth.TWO_PI, LIFE_GOLD, 0.9F * f);
        c.glow(o.add(0, 2.35, 0), 0.7F, LIFE_GOLD, 0.4F * f);
        c.glow(o.add(0, 1, 0), 1.8F, LIFE_GOLD, 0.2F * f);
    }

    /** A tree of light grows where they stand, branch by branch, petals drifting off it. */
    static void lifebloom(Canvas c, Active a, float t) {
        if (!c.glow()) return;
        float life = a.piece().ticks, f = fade(t, life, 4, 30), grow = smooth(0, 34, t), r = a.scale();
        Vec3 o = a.origin();
        List<Vec3> tips = new ArrayList<>();
        branch(c, a.seed(), o, new Vec3(0, 1, 0), 1.8F, 0, grow, f, tips);
        for (int i = 0; i < tips.size(); i++) c.glow(tips.get(i), 0.22F, i % 3 == 0 ? BLOOM : LEAF, 0.8F * f * grow);
        for (int i = 0; i < 18; i++) {
            float k = (t * 0.008F + hash(a.seed(), i)) % 1, ang = hash(a.seed(), i + 30) * Mth.TWO_PI, rr = 0.5F + hash(a.seed(), i + 60) * 2.5F;
            c.glow(o.add(Mth.cos(ang + k * 2) * rr, 4.2 * (1 - k), Mth.sin(ang + k * 2) * rr), 0.08F, BLOOM, f * grow);
        }
        c.ring(o.add(0, 0.06, 0), r, 0.1F, LEAF, 0.45F * f);
        c.disc(o.add(0, 0.05, 0), UP, r, LEAF, 0.12F * f, 0);
    }

    private static void branch(Canvas c, int seed, Vec3 from, Vec3 dir, float length, int depth, float grow, float f, List<Vec3> tips) {
        float start = depth * 0.2F, k = Mth.clamp((grow - start) / 0.25F, 0, 1);
        if (k <= 0) return;
        Vec3 to = from.add(dir.scale(length * k));
        c.line(from, to, 0.14F * (1 - depth * 0.2F), depth < 2 ? LIFE_GOLD : LEAF, 0.85F * f, 0.85F * f);
        if (depth >= 3 || k < 1) { if (depth >= 3) tips.add(to); return; }
        for (int i = 0; i < 3; i++) {
            float yaw = hash(seed, depth * 10 + i) * Mth.TWO_PI, tilt = 0.45F + hash(seed, depth * 10 + i + 5) * 0.4F;
            Vec3 d = dir.scale(Mth.cos(tilt)).add(Mth.cos(yaw) * Mth.sin(tilt), 0, Mth.sin(yaw) * Mth.sin(tilt)).normalize();
            branch(c, seed * 31 + i + depth, to, d, length * 0.68F, depth + 1, grow, f, tips);
        }
    }

    // ------------------------------------------------------------------
    // Empathic Mimicry
    // ------------------------------------------------------------------

    /** Every power they remember at once: arms of rainbow light spiral out of them, rings of colour after. */
    static void overflow(Canvas c, Active a, float t) {
        if (!c.glow()) return;
        float life = a.piece().ticks, f = 1 - t / life, r = a.scale(), out = Mth.clamp(t / 14, 0, 1);
        Vec3 o = a.origin().add(0, 1, 0);
        for (int arm = 0; arm < 8; arm++) {
            Vec3[] p = new Vec3[17];
            for (int i = 0; i <= 16; i++) {
                float k = i / 16F * out, ang = arm * Mth.TWO_PI / 8 + k * 3;
                p[i] = o.add(Mth.cos(ang) * r * k, Mth.sin(k * Mth.PI) * 0.6, Mth.sin(ang) * r * k);
            }
            c.ribbon(p, 0.18F, hue(arm / 8F), 0.1F * f, 0.9F * f);
        }
        for (int k = 0; k < 3; k++) {
            float p = Mth.clamp((t - k * 4) / 18, 0, 1);
            if (p > 0) c.ring(o.add(0, -0.9, 0), r * p, 0.25F, hue(k / 3F + t * 0.01F), 0.8F * (1 - p));
        }
        if (t < 5) c.glow(o, 3.5F, WHITE, 1 - t / 5);
    }

    /** Three sigils of borrowed colour circle their chest, each a ring round a star. */
    static void ward(Canvas c, Active a, float t) {
        if (!c.glow()) return;
        float f = fade(t, a.piece().ticks, 8, 16);
        Vec3 o = a.at(c).add(0, 1.2, 0);
        for (int k = 0; k < 3; k++) {
            float ang = k * Mth.TWO_PI / 3 + t * 0.07F;
            Vec3 p = o.add(Mth.cos(ang) * 1.3, Mth.sin(t * 0.08F + k) * 0.25, Mth.sin(ang) * 1.3);
            Vec3 axis = p.subtract(o).normalize();
            float[] col = hue(k / 3F + 0.05F);
            c.ring(p, axis, 0.35F, 0.04F, 0, Mth.TWO_PI, col, 0.9F * f);
            for (int s = 0; s < 5; s++) c.spoke(p, axis, s * Mth.TWO_PI / 5 + t * 0.1F, 0, 0.3F, 0.03F, col, 0.8F * f);
            c.glow(p, 0.5F, col, 0.4F * f);
        }
    }

    /** Halos in every colour rise up a friend, one after another. */
    static void kindred(Canvas c, Active a, float t) {
        if (!c.glow()) return;
        float life = a.piece().ticks;
        Vec3 o = a.at(c);
        for (int k = 0; k < 4; k++) {
            float p = Mth.clamp((t - k * 5) / 22, 0, 1);
            if (p <= 0 || p >= 1) continue;
            c.ring(o.add(0, p * 2.6, 0), 0.75F - p * 0.25F, 0.07F, hue(k / 4F), 0.9F * Mth.sin(p * Mth.PI));
        }
        c.glow(o.add(0, 1, 0), 1.5F, WHITE, 0.25F * (1 - t / life));
    }

    // ------------------------------------------------------------------
    // Matter Assembly
    // ------------------------------------------------------------------

    private static Vec3 muzzleDir(Active a) {
        Vec3 d = a.target().subtract(a.origin().add(0, 1.6, 0));
        return d.lengthSqr() < 1e-4 ? a.facing() : d.normalize();
    }

    /** Shards fly in and lock into a barrel over their shoulder; its muzzle flashes with every shot. */
    static void cannon(Canvas c, Active a, float t) {
        float life = a.piece().ticks, f = fade(t, life, 2, 8);
        Vec3 dir = muzzleDir(a), side = dir.cross(UP);
        if (side.lengthSqr() < 1e-4) side = new Vec3(1, 0, 0);
        side = side.normalize();
        Vec3 up = side.cross(dir).normalize();
        Vec3 base = a.at(c).add(0, 1.9, 0).add(side.scale(0.7)).add(up.scale(0.15));
        float lock = smooth(0, 7, t), apart = Mth.clamp((t - (life - 8)) / 8, 0, 1);
        for (int i = 0; i < 8; i++) {
            float ang = i * Mth.TWO_PI / 8;
            Vec3 offset = side.scale(Mth.cos(ang) * 0.22).add(up.scale(Mth.sin(ang) * 0.22));
            Vec3 scatter = offset.normalize().scale((1 - lock) * 2.5 + apart * 1.5);
            Vec3 p0 = base.add(offset).add(scatter), p1 = p0.add(dir.scale(1.2));
            if (c.solid() && f > 0.2F) c.shard(p0, p1, 0.09F, 4, ang, CRYSTAL, 1);
        }
        if (c.glow()) {
            Vec3 muzzle = base.add(dir.scale(1.3));
            for (int shot = 0; shot < 5; shot++) {
                float s = t - (8 + shot * 5);
                if (s >= 0 && s < 3) c.glow(muzzle, 0.9F, WHITE, 1 - s / 3);
            }
            c.ring(muzzle, dir, 0.3F, 0.04F, 0, Mth.TWO_PI, CRYSTAL_EDGE, 0.6F * f * lock);
        }
    }

    /** One crystal slug, out of the barrel and into the target. */
    static void slug(Canvas c, Active a, float t) {
        float k = Mth.clamp(t / 3, 0, 1);
        Vec3 from = a.origin(), to = a.target(), p = from.lerp(to, k), d = to.subtract(from).normalize();
        if (k < 1 && c.solid()) c.shard(p.subtract(d.scale(0.3)), p.add(d.scale(0.3)), 0.1F, 4, t, CRYSTAL, 1);
        if (c.glow()) {
            c.line(from.lerp(to, Math.max(0, k - 0.4F)), p, 0.05F, CRYSTAL_EDGE, 0, 0.9F);
            if (k >= 1) {
                float s = (t - 3) / 5;
                for (int i = 0; i < 6; i++) {
                    float ang = i * Mth.TWO_PI / 6 + a.seed();
                    c.line(to, to.add(Mth.cos(ang) * s, Mth.sin(ang * 2) * s * 0.6, Mth.sin(ang) * s), 0.04F, CRYSTAL_EDGE, 1 - s, 0);
                }
            }
        }
    }

    /** Crystal panels fly in and lock into a shell round them, turning slowly, their edges lit. */
    static void lattice(Canvas c, Active a, float t) {
        float life = a.piece().ticks, f = fade(t, life, 2, 12);
        Vec3 o = a.at(c).add(0, 1, 0);
        float g = (1 + Mth.sqrt(5)) / 2, r = 1.55F, spin = t * 0.015F, lock = smooth(0, 12, t);
        float[][] base = {{-1, g, 0}, {1, g, 0}, {-1, -g, 0}, {1, -g, 0}, {0, -1, g}, {0, 1, g}, {0, -1, -g}, {0, 1, -g},
                {g, 0, -1}, {g, 0, 1}, {-g, 0, -1}, {-g, 0, 1}};
        int[][] faces = {{0, 11, 5}, {0, 5, 1}, {0, 1, 7}, {0, 7, 10}, {0, 10, 11}, {1, 5, 9}, {5, 11, 4}, {11, 10, 2}, {10, 7, 6}, {7, 1, 8},
                {3, 9, 4}, {3, 4, 2}, {3, 2, 6}, {3, 6, 8}, {3, 8, 9}, {4, 9, 5}, {2, 4, 11}, {6, 2, 10}, {8, 6, 7}, {9, 8, 1}};
        Vec3[] v = new Vec3[12];
        float cs = Mth.cos(spin), sn = Mth.sin(spin);
        for (int i = 0; i < 12; i++) {
            float l = Mth.sqrt(base[i][0] * base[i][0] + base[i][1] * base[i][1] + base[i][2] * base[i][2]);
            float x = base[i][0] / l, z = base[i][2] / l;
            v[i] = new Vec3((x * cs - z * sn) * r, base[i][1] / l * r * 1.2, (x * sn + z * cs) * r);
        }
        for (int i = 0; i < faces.length; i++) {
            Vec3 mid = v[faces[i][0]].add(v[faces[i][1]]).add(v[faces[i][2]]).scale(1 / 3.0);
            float k = Mth.clamp(lock * 1.4F - hash(a.seed(), i) * 0.4F, 0, 1);
            Vec3 push = mid.normalize().scale((1 - k) * 3);
            Vec3 a0 = o.add(v[faces[i][0]].subtract(mid).scale(0.92).add(mid)).add(push), a1 = o.add(v[faces[i][1]].subtract(mid).scale(0.92).add(mid)).add(push),
                    a2 = o.add(v[faces[i][2]].subtract(mid).scale(0.92).add(mid)).add(push);
            if (c.soft()) c.tri(a0, a1, a2, CRYSTAL, 0.18F * f, 0.18F * f, 0.18F * f);
            if (c.glow()) c.ribbon(new Vec3[] {a0, a1, a2, a0}, 0.03F, CRYSTAL_EDGE, 0.7F * f * k, 0.7F * f * k);
        }
    }

    /** Lines of crystal lace wind up round a friend into a lattice, then fade. */
    static void mending(Canvas c, Active a, float t) {
        if (!c.glow()) return;
        float f = fade(t, a.piece().ticks, 6, 20), grow = smooth(0, 20, t);
        Vec3 o = a.at(c);
        for (int k = 0; k < 4; k++) {
            c.helix(o, 2.1F * grow, 0.7F, 0.6F, 1, k * Mth.HALF_PI, 0.04F, LATTICE, 0.8F * f, 20);
            c.helix(o, 2.1F * grow, 0.7F, 0.6F, -1, k * Mth.HALF_PI, 0.04F, CRYSTAL_EDGE, 0.8F * f, 20);
        }
        c.glow(o.add(0, 1, 0), 1.4F, LATTICE, 0.25F * f);
    }
}
