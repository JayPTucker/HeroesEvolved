package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient.Active;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.hash;
import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.rgb;
import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.smooth;

/** Matter Assembly drawn: the ground giving up its matter to a construct, and a sentinel turret's crystal head. */
final class AssemblyArt {
    private static final float[] AMBER = rgb(0xF2A848), SEAM = rgb(0xFFE3A0), PLATE = rgb(0xC79A62), DUST = {0.7F, 0.62F, 0.5F},
            CRYSTAL = rgb(0xBFE8FF), CORE = rgb(0xFFF1C8);

    private AssemblyArt() {}

    /** Where plate {@code i} tears free from the ground, round the construct. */
    private static Vec3 source(Active a, int i) {
        float ang = hash(a.seed(), i) * Mth.TWO_PI, r = a.scale() + 2.5F + hash(a.seed(), i + 40) * 3.5F;
        return a.origin().add(Mth.cos(ang) * r, -a.scale() * 0.6 - 0.8, Mth.sin(ang) * r);
    }

    /** Where plate {@code i} settles into the construct. */
    private static Vec3 slot(Active a, int i) {
        float s = a.scale() * 0.8F;
        return a.origin().add((hash(a.seed(), i + 80) - 0.5) * 2 * s, (hash(a.seed(), i + 120) - 0.5) * 1.6 * s, (hash(a.seed(), i + 160) - 0.5) * 2 * s);
    }

    /**
     * The ground gives up its matter: plates tear free all round, leaving puffs of dust, and fly up in arcs into
     * the construct's shape with bright seams trailing them; then a lattice of light runs over it and fades.
     */
    static void rise(Canvas c, Active a, float t) {
        int plates = 18;
        for (int i = 0; i < plates; i++) {
            float start = hash(a.seed(), i + 200) * 8, k = Mth.clamp((t - start) / 12, 0, 1);
            if (t < start) continue;
            Vec3 from = source(a, i), to = slot(a, i);
            float e = k * k * (3 - 2 * k);
            Vec3 at = from.lerp(to, e).add(0, Math.sin(e * Math.PI) * (2 + a.scale() * 0.4), 0);
            if (c.solid() && k < 1)
                c.box(at, 0.32F, 0.06F, 0.28F, i + t * 0.4F, t * 0.3F + i, i * 0.7F, PLATE, 1);
            if (c.glow() && k < 1) {
                Vec3 behind = from.lerp(to, Math.max(0, e - 0.25F)).add(0, Math.sin(Math.max(0, e - 0.25F) * Math.PI) * (2 + a.scale() * 0.4), 0);
                c.line(behind, at, 0.07F, SEAM, 0, 0.8F);
                c.glow(at, 0.35F, AMBER, 0.6F);
            }
            if (c.soft() && t - start < 10) {
                float puff = (t - start) / 10;
                c.cloud(from.add(0, 0.3 + puff * 0.6, 0), 0.4F + puff * 0.5F, 0.3F + puff * 0.3F, 0.4F + puff * 0.5F, i, puff, 0.4F * (1 - puff),
                        Shades.smoke(c, DUST, 0));
            }
        }
        float lattice = smooth(14, 20, t) * Mth.clamp((a.piece().ticks - t) / 10, 0, 1);
        if (lattice > 0.01F) {
            float s = a.scale();
            if (c.glow()) c.shell(a.origin(), s, s * 0.8F, s, Mth.PI, SEAM, 0.55F * lattice, 0.1F, Canvas.hexes(5, 0.07F, 0.04F), t * 0.02F);
            if (c.glow()) c.glow(a.origin(), s * 1.4F, AMBER, 0.2F * lattice);
        }
    }

    /**
     * A sentinel's head: a crystal turning over its pillar, a ring of light round it tilting as it turns, and a
     * pale beam sweeping slowly round as it searches. Gone with the pillar.
     */
    static void sentinel(Canvas c, Active a, float t) {
        Vec3 o = a.origin();
        BlockPos pillar = BlockPos.containing(o.x, o.y - 1.4, o.z);
        if (c.level != null && c.level.getBlockState(pillar).isAir()) return;
        float f = Canvas.fade(t, a.piece().ticks, 10, 20), spin = t * 0.08F, bob = Mth.sin(t * 0.15F) * 0.08F;
        Vec3 core = o.add(0, bob, 0);
        if (c.solid() && f > 0.5F) {
            for (int i = 0; i < 4; i++) {
                float ang = spin + i * Mth.HALF_PI;
                Vec3 out = new Vec3(Mth.cos(ang) * 0.42, 0, Mth.sin(ang) * 0.42);
                c.shard(core, core.add(out), 0.16F, 4, ang, CRYSTAL, 1);
            }
            c.shard(core, core.add(0, 0.55, 0), 0.2F, 4, spin, CRYSTAL, 1);
            c.shard(core, core.add(0, -0.45, 0), 0.18F, 4, -spin, CRYSTAL, 1);
        }
        if (c.glow()) {
            c.glow(core, 0.7F, CORE, 0.8F * f);
            c.ring(core, new Vec3(Mth.sin(spin) * 0.3, 1, Mth.cos(spin) * 0.3), 0.75F, 0.05F, 0, Mth.TWO_PI, AMBER, 0.8F * f);
            float sweep = t * 0.05F;
            Vec3 beam = core.add(Mth.cos(sweep) * 6, -0.8, Mth.sin(sweep) * 6);
            c.line(core, beam, 0.05F, CRYSTAL, 0.5F * f, 0);
        }
    }
}
