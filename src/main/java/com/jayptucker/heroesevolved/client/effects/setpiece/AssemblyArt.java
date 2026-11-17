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
            CRYSTAL = rgb(0xBFE8FF), CORE = rgb(0xFFF1C8), HEAD = rgb(0x6E655C);

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

    /**
     * Monument (the finisher, {@code assembly/Monument}): plates tear up from the ground all round and fly in arcs onto the
     * foe, seams trailing; a plinth rises under the statue (the foe itself drawn in stone, by {@code BodyCopies}); it holds,
     * then crumbles from the top into falling chips and dust, and the plinth sinks. Bound to the foe; origin its feet,
     * target the builder; scale its height.
     */
    /**
     * The alternate finishers ({@code assembly/AssemblyFinishers}). Hammer: plates fly up from the ground into a giant
     * hammer over the caster's head (a handle and a block of a head with a bright seam), it swings over in an arc onto
     * the foe, and breaks into tumbling plates and dust. Shard Orbit: crystal shards rise round the foe and orbit it,
     * faster and tighter, then all drive in at once in a flash and fall as chips. Origin the foe's feet, target the
     * caster's, scale the foe's height.
     */
    static void alternates(Canvas c, Active a, float t) {
        float h = Math.max(.6F, a.scale()), life = a.piece().ticks, f = Canvas.fade(t, life, 1, 6);
        Vec3 feet = a.origin(), home = a.target();
        int seed = a.seed();
        if (a.piece() == com.jayptucker.heroesevolved.styles.Piece.FINISH_HAMMER) {
            int built = com.jayptucker.heroesevolved.assembly.AssemblyFinishers.HAMMER_BUILT, land = com.jayptucker.heroesevolved.assembly.AssemblyFinishers.HAMMER_LAND;
            Vec3 head = com.jayptucker.heroesevolved.assembly.AssemblyFinishers.hammer(home, feet, h, Math.min(t, land));
            Vec3 grip = home.add(0, 1.9, 0);
            // Plates flying up from the ground into the head.
            if (t < built + 2) for (int i = 0; i < 10; i++) {
                float start = hash(seed, i) * 4, k = smooth(start, built, t);
                if (k <= 0 || k >= 1) continue;
                float ang = hash(seed, i + 20) * Mth.TWO_PI, r = 1.8F + hash(seed, i + 40) * 1.4F;
                Vec3 from = home.add(Mth.cos(ang) * r, -.2, Mth.sin(ang) * r);
                Vec3 at = from.lerp(head, k).add(0, Mth.sin(k * Mth.PI) * .8, 0);
                if (c.solid()) c.box(at, .2F, .05F, .15F, ang, k * 6, 0, PLATE, 1);
                if (c.glow()) c.line(from.lerp(head, Math.max(0, k - .2F)), at, .03F, SEAM, f * .2F, f * .8F);
            }
            if (t < land + 2) {
                float size = smooth(2, built, t);
                Vec3 d = head.subtract(grip);
                double len = d.length();
                if (len > .1 && size > .02F) {
                    d = d.scale(1 / len);
                    float yaw = (float) Math.atan2(-d.x, d.z), pitch = (float) Math.acos(Mth.clamp(d.y, -1, 1));
                    // A sledge: the head long in the swing (the box's own z), dark stone banded with plate at each face.
                    Vec3 along = new Vec3(-Math.cos(pitch) * Math.sin(yaw), -Math.sin(pitch), Math.cos(pitch) * Math.cos(yaw));
                    if (c.solid()) {
                        c.box(grip.lerp(head, .5), .07F * size, (float) len * .5F * size, .07F * size, yaw, pitch, 0, PLATE, 1);
                        c.box(head, .4F * size, .4F * size, .62F * size, yaw, pitch, 0, HEAD, 1);
                        for (int sign : new int[]{-1, 1})
                            c.box(head.add(along.scale(sign * .6 * size)), .45F * size, .45F * size, .06F * size, yaw, pitch, 0, PLATE, 1);
                    }
                    if (c.glow()) {
                        for (int sign : new int[]{-1, 1})
                            c.ring(head.add(along.scale(sign * .52 * size)), along, .46F * size, .03F, 0, Mth.TWO_PI, SEAM, f * .7F);
                        if (t > land - 3) c.glow(head, .7F, AMBER, f * .4F);
                    }
                }
            }
            if (t >= land) {
                float age = t - land;
                if (c.solid()) for (int i = 0; i < 14; i++) {
                    float ang = hash(seed, i + 80) * Mth.TWO_PI, out = .15F + .12F * age * hash(seed, i + 90);
                    double y = h + .3 + 1.2 * hash(seed, i + 100) * Math.min(1, age / 3) - .045 * age * age;
                    if (y < 0) continue;
                    c.box(feet.add(Mth.cos(ang) * out, y, Mth.sin(ang) * out), .14F, .05F, .11F, ang + age * .4F, age * .5F, 0, PLATE, 1);
                }
                if (c.soft()) c.ring(feet.add(0, .1, 0), Canvas.UP, .6F + age * .15F, .45F, 0, Mth.TWO_PI, DUST, f * .4F * (1 - smooth(0, 9, age)));
                if (c.glow()) c.ring(feet.add(0, .06, 0), Canvas.UP, .4F + 2.4F * smooth(land, land + 7, t), .05F, 0, Mth.TWO_PI, SEAM,
                        f * (1 - smooth(land, land + 7, t)));
            }
            return;
        }
        // Shard Orbit.
        int rise = com.jayptucker.heroesevolved.assembly.AssemblyFinishers.ORBIT_RISE, land = com.jayptucker.heroesevolved.assembly.AssemblyFinishers.ORBIT_LAND;
        Vec3 core = feet.add(0, h * .55, 0);
        int n = 7;
        if (t < land + 1) {
            float up = smooth(0, rise, t), close = smooth(rise, land, t);
            float spin = t * (.12F + .3F * close) + t * t * .01F;
            for (int i = 0; i < n; i++) {
                float ang = spin + i * Mth.TWO_PI / n;
                float r = Mth.lerp(close * close, 2.4F, .55F);
                double y = h * (.2 + .6 * hash(seed, i)) * up;
                Vec3 at = feet.add(Mth.cos(ang) * r, y + .1 * Mth.sin(t * .4F + i), Mth.sin(ang) * r);
                if (t >= land - 1) at = at.lerp(core, smooth(land - 1, land, t));
                Vec3 point = core.subtract(at);
                Vec3 tip = at.add(point.lengthSqr() < 1e-4 ? Canvas.UP.scale(.4) : point.normalize().scale(.45));
                c.shard(at, tip, .09F, 4, ang, CRYSTAL, c.solid() ? 1 : f * .5F);
                if (c.glow()) c.glow(at, .12F, CRYSTAL, f * .4F);
            }
            if (c.glow()) c.ring(feet.add(0, .05, 0), Canvas.UP, Mth.lerp(close, 2.4F, .6F), .03F, 0, Mth.TWO_PI, SEAM, f * .4F * up);
        } else {
            float age = t - land;
            if (c.glow()) {
                c.glow(core, 1.3F * (1 - smooth(0, 3, age)), CORE, f * (1 - smooth(0, 3, age)));
                c.ring(core, Canvas.UP, .3F + 1.8F * smooth(0, 6, age), .05F, 0, Mth.TWO_PI, CRYSTAL, f * (1 - smooth(0, 6, age)));
            }
            for (int i = 0; i < 12; i++) {
                float ang = hash(seed, i + 30) * Mth.TWO_PI;
                double y = h * .55 + .4 * hash(seed, i + 40) * Math.min(1, age / 2) - .04 * age * age;
                if (y < 0) continue;
                Vec3 at = feet.add(Mth.cos(ang) * (.2 + age * .1), y, Mth.sin(ang) * (.2 + age * .1));
                c.shard(at, at.add(Mth.cos(ang) * .15, .1, Mth.sin(ang) * .15), .04F, 4, ang + age, CRYSTAL, c.solid() ? 1 : f * .6F);
            }
        }
    }

    static void finisher(Canvas c, Active a, float t) {
        float h = Math.max(.6F, a.scale()), life = a.piece().ticks, f = Canvas.fade(t, life, 1, 6);
        Vec3 feet = a.origin();
        int shut = com.jayptucker.heroesevolved.combat.FinisherBeats.MONUMENT_SHUT;
        int crumble = com.jayptucker.heroesevolved.combat.FinisherBeats.MONUMENT_CRUMBLE;
        // Plates flying in from the ground to the body.
        if (t < shut + 2) for (int i = 0; i < 12; i++) {
            float start = hash(a.seed(), i) * 3, k = smooth(start, shut, t);
            if (k <= 0) continue;
            float ang = hash(a.seed(), i + 20) * Mth.TWO_PI, r = 2.2F + hash(a.seed(), i + 40) * 1.5F;
            Vec3 from = feet.add(Mth.cos(ang) * r, -.2, Mth.sin(ang) * r);
            Vec3 to = feet.add(Mth.cos(ang) * .35, h * (.15 + .8 * hash(a.seed(), i + 60)), Mth.sin(ang) * .35);
            Vec3 at = from.lerp(to, k).add(0, Mth.sin(k * Mth.PI) * 1.2, 0);
            if (c.solid() && k < 1) c.box(at, .22F, .05F, .16F, ang * Mth.RAD_TO_DEG, k * 360, 0, PLATE, 1);
            if (c.glow() && k < 1) c.line(from.lerp(to, Math.max(0, k - .25F)).add(0, Mth.sin(Math.max(0, k - .25F) * Mth.PI) * 1.2, 0), at, .03F, SEAM, f * .2F, f * .8F);
        }
        // The plinth rising under the statue, sinking once it crumbles.
        float lift = .5F * smooth(shut, shut + 8, t) * (1 - smooth(crumble + 2, crumble + 8, t));
        if (lift > .02F && c.solid()) c.box(feet.add(0, lift / 2 - .02, 0), 1.1F, lift, 1.1F, 0, 0, 0, PLATE, 1);
        if (t >= shut && t < crumble && c.glow()) {
            float seam = 1 - smooth(shut, shut + 6, t);
            c.ring(feet.add(0, lift + .02, 0), Canvas.UP, .6F, .03F, 0, Mth.TWO_PI, SEAM, f * seam);
        }
        // The crumble: chips falling from the top down, and dust.
        if (t >= crumble) {
            float age = t - crumble;
            if (c.solid()) for (int i = 0; i < 14; i++) {
                float delay = (1 - hash(a.seed(), i + 80)) * 4, fall = Math.max(0, age - delay);
                double y = .5 + h * hash(a.seed(), i + 80) - .04 * fall * fall;
                if (y < 0) continue;
                float ang = hash(a.seed(), i + 100) * Mth.TWO_PI;
                c.box(feet.add(Mth.cos(ang) * (.2 + fall * .04), y, Mth.sin(ang) * (.2 + fall * .04)), .12F, .1F, .12F, fall * 30, fall * 20, 0, PLATE, 1);
            }
            if (c.soft()) c.ring(feet.add(0, .1, 0), Canvas.UP, .5F + age * .12F, .4F, 0, Mth.TWO_PI, DUST, f * .35F * (1 - smooth(0, 8, age)));
        }
    }
}
