package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient.Active;
import com.jayptucker.heroesevolved.phasing.EarthGeometry;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.*;

/**
 * Density's earthbending, in one idea: <b>the ground is mass, and it moves in chunks</b>. Rock tears up out of the
 * ground in slabs and lumps the colour of the block it came from (the block's id rides in a piece's seed), the ground
 * splits in dark jagged cracks where it gives, and dust rolls off everything that moves. A thin jade seam (Earth
 * Kingdom green, the bender's root) glows only where the bender's own will is in the ground: a stance settling, a
 * telegraph filling, a seismic pulse.
 * <p>
 * Primitives: {@link #chunk} (a lit lump of rock, SOLID), {@link #crack} (a jagged dark split, SOFT, with an optional
 * jade seam in GLOW), {@link #dust} (a rolling billow, SOFT) and {@link #seam} (a three-layer jade ring, GLOW). Palette:
 * {@code 2B241E} crack, {@code A8946E} dust, {@code 2E5E3A} jade halo, {@code 5FA36A} jade, {@code CFEBC9} jade core,
 * and the ground's own colour.
 */
final class EarthArt {
    static final float[] CRACK = rgb(0x2B241E), DUST = rgb(0xA8946E), SOIL = rgb(0x7A6450), MUD = rgb(0x4A3A2C),
            JADE_DEEP = rgb(0x2E5E3A), JADE = rgb(0x5FA36A), JADE_PALE = rgb(0xCFEBC9), GREY = rgb(0x8A8580), EMBER = rgb(0xFF7A2A);

    private EarthArt() {}

    static void draw(Canvas c, Active a, float t) {
        switch (a.piece()) {
            case EARTH_STANCE -> stance(c, a, t);
            case EARTH_LIFT -> lift(c, a, t);
            case EARTH_THROW -> throwRing(c, a, t);
            case EARTH_SHATTER -> shatter(c, a, t);
            case EARTH_SHACKLE -> shackle(c, a, t);
            case EARTH_CRUSH -> crush(c, a, t);
            case EARTH_TUNNEL -> tunnel(c, a, t);
            case EARTH_PILLAR -> pillar(c, a, t);
            case EARTH_WALL -> wall(c, a, t);
            case EARTH_SHOVE -> shove(c, a, t);
            case EARTH_MOUNTAIN_MARK -> mark(c, a, t);
            case EARTH_MOUNTAIN -> mountain(c, a, t);
            case EARTH_MOUNTAIN_SLAM -> slam(c, a, t);
            case EARTH_GAUNTLET -> gauntlet(c, a, t);
            case EARTH_AVALANCHE -> avalanche(c, a, t);
            case EARTH_TENT -> tent(c, a, t);
            case EARTH_SURF -> surf(c, a, t);
            case EARTH_STOMP -> stomp(c, a, t);
            case EARTH_LISTEN -> listen(c, a, t);
            case EARTH_CLAW -> claw(c, a, t);
            case EARTH_SLAB -> slab(c, a, t);
            case EARTH_SPIKES -> spikes(c, a, t);
            case EARTH_SLIDE -> slide(c, a, t);
            default -> { }
        }
    }

    // ------------------------------------------------------------------
    // Primitives
    // ------------------------------------------------------------------

    /** The colour of the rock a piece is made of: the block in its seed, as its construct, on the map. */
    static float[] colour(Canvas c, int seed) {
        BlockState state = EarthGeometry.construct(EarthGeometry.block(seed));
        int col = state.getMapColor(c.level, BlockPos.ZERO).col;
        return col == 0 ? GREY : mix(rgb(col), GREY, .25F);
    }

    /** A lump of rock, lit by the sun, a little uneven by its seed; it shrinks rather than fades. */
    static void chunk(Canvas c, Vec3 at, float size, float yaw, float pitch, float roll, float[] col, float scale, int seed) {
        if (!c.solid() || size * scale <= .015F) return;
        float s = size * scale;
        c.box(at, s * (.8F + .4F * hash(seed, 1)), s * (.7F + .4F * hash(seed, 2)), s * (.8F + .4F * hash(seed, 3)), yaw, pitch, roll, col, 1);
    }

    /** A jagged dark split in the ground from {@code at} along {@code angle}; with {@code seam}, jade light in it. */
    static void crack(Canvas c, Vec3 at, float angle, float length, int seed, float seam, float alpha) {
        if (alpha <= .003F || length <= .02F) return;
        int n = 6;
        Vec3[] pts = new Vec3[n + 1];
        float ca = Mth.cos(angle), sa = Mth.sin(angle);
        for (int i = 0; i <= n; i++) {
            float d = length * i / n, j = i == 0 ? 0 : (hash(seed, i) - .5F) * .45F * length / n;
            pts[i] = at.add(ca * d - sa * j, .025, sa * d + ca * j);
        }
        if (c.soft()) c.ribbon(pts, .09F, CRACK, .85F * alpha, .15F * alpha);
        if (c.glow() && seam > 0) {
            c.ribbon(pts, .08F, JADE_DEEP, .25F * seam * alpha, 0);
            c.ribbon(pts, .025F, JADE, .7F * seam * alpha, 0);
        }
    }

    /** A rolling billow of dust. */
    static void dust(Canvas c, Vec3 at, float r, float alpha, int variant) {
        if (!c.soft() || alpha <= .003F || r <= .05F) return;
        c.cloud(at, r, r * .7F, r, variant, variant * .7F, alpha, Shades.smoke(c, DUST, 0));
    }

    /** A three-layer jade ring on the plane facing {@code axis}: the bender's will, in the ground. */
    static void seam(Canvas c, Vec3 at, Vec3 axis, float r, float w, float from, float to, float alpha) {
        if (!c.glow() || alpha <= .003F || r <= .02F) return;
        c.ring(at, axis, r, w * 3, from, to, JADE_DEEP, .35F * alpha);
        c.ring(at, axis, r, w, from, to, JADE, .85F * alpha);
        c.ring(at, axis, r, w * .3F, from, to, JADE_PALE, .9F * alpha);
    }

    static float fade(float age, float life, float in, float out) {
        if (age < 0 || age >= life) return 0;
        return (in > 0 ? smooth(0, in, age) : 1) * (out > 0 ? 1 - smooth(life - out, life, age) : 1);
    }

    static boolean minimal() { return Minecraft.getInstance().options.particles().get() == ParticleStatus.MINIMAL; }

    private static Vec3 flat(Vec3 v) {
        Vec3 f = v.multiply(1, 0, 1);
        return f.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : f.normalize();
    }

    private static Vec3 side(Vec3 fwd) { return new Vec3(-fwd.z, 0, fwd.x); }

    /** Where a fist is drawn from: the visible hand in first person, the body's palm otherwise. */
    static Vec3 hand(Canvas c, Entity owner, boolean main) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 look = owner.getViewVector(c.partial);
        if (owner == mc.player && mc.options.getCameraType() == CameraType.FIRST_PERSON) {
            Vec3 right = look.cross(UP).normalize();
            return owner.getEyePosition(c.partial).add(look.scale(.6)).add(right.scale(main ? .32 : -.32)).add(0, -.3, 0);
        }
        return owner instanceof LivingEntity body ? EarthGeometry.palm(body, look, c.partial, main) : owner.getPosition(c.partial).add(0, 1.2, 0);
    }

    /** Chunks thrown out from {@code at} into the half-space round {@code normal}, falling and shrinking. */
    private static void burst(Canvas c, Vec3 at, Vec3 normal, int n, float size, float speed, float t, float life, float[] col, int seed) {
        Vec3 nn = normal.lengthSqr() < 1e-6 ? UP : normal.normalize();
        Vec3 u = Math.abs(nn.y) > .9 ? new Vec3(1, 0, 0) : nn.cross(UP).normalize(), v = u.cross(nn).normalize();
        for (int i = 0; i < n; i++) {
            float ang = hash(seed, i) * Mth.TWO_PI, out = .35F + .65F * hash(seed, i + 20), sp = speed * (.6F + .6F * hash(seed, i + 40));
            Vec3 dir = nn.scale(.6 + .5 * hash(seed, i + 60)).add(u.scale(Mth.cos(ang) * out)).add(v.scale(Mth.sin(ang) * out)).normalize();
            Vec3 p = at.add(dir.scale(sp * t)).add(0, -.03 * t * t, 0);
            chunk(c, p, size * (.5F + .6F * hash(seed, i + 80)), ang + t * .3F, t * .4F * (hash(seed, i + 7) - .5F) * 4, i, col,
                    1 - smooth(life * .55F, life, t), seed + i);
        }
    }

    // ------------------------------------------------------------------
    // Horse Stance, Seismic Stomp and Wait and Listen
    // ------------------------------------------------------------------

    /** Horse Stance: the feet set, short cracks snap out from them, dust puffs, and a jade ring settles inward. */
    private static void stance(Canvas c, Active a, float t) {
        Vec3 feet = a.at(c);
        float f = fade(t, a.piece().ticks, 0, 6), snap = smooth(0, 3, t);
        for (int i = 0; i < 6; i++) crack(c, feet, i * Mth.TWO_PI / 6 + hash(a.seed(), i), .35F + .55F * snap, a.seed() + i, .6F * (1 - snap * .5F), f);
        seam(c, feet.add(0, .04, 0), UP, 1.4F - .9F * smooth(0, 10, t), .05F, 0, Mth.TWO_PI, f * .8F);
        dust(c, feet.add(0, .15, 0), .35F + .25F * snap, .45F * f, a.seed() & 7);
    }

    /** Seismic Stomp: a jade pulse runs out along the ground to the edge of what the bender can feel, then another. */
    private static void stomp(Canvas c, Active a, float t) {
        Vec3 feet = a.origin();
        float r = a.scale(), f = fade(t, a.piece().ticks, 0, 6);
        for (int k = 0; k < 2; k++) {
            float age = t - k * 5;
            if (age < 0) continue;
            float reach = smooth(0, 16, age);
            seam(c, feet.add(0, .05, 0), UP, .6F + r * reach, .06F + .04F * (1 - reach), 0, Mth.TWO_PI, f * (1 - reach) * (k == 0 ? 1 : .55F));
        }
        for (int i = 0; i < 8; i++) crack(c, feet, i * Mth.TWO_PI / 8 + hash(a.seed(), i), 1.2F * smooth(0, 3, t), a.seed() + i, .9F, f);
        dust(c, feet.add(0, .1, 0), .5F + .4F * smooth(0, 5, t), .5F * f * (1 - smooth(4, 14, t)), a.seed() & 7);
    }

    /** Wait and Listen: thin jade threads run from the soles into the ground, and a slow ring breathes at the feet. */
    private static void listen(Canvas c, Active a, float t) {
        Vec3 feet = a.at(c);
        float f = fade(t, a.piece().ticks, 2, 2), breathe = .5F + .5F * Mth.sin((c.level.getGameTime() + c.partial) * .15F);
        seam(c, feet.add(0, .04, 0), UP, .9F + .35F * breathe, .03F, 0, Mth.TWO_PI, f * .6F);
        if (!c.glow()) return;
        for (int i = 0; i < 6; i++) {
            float ang = i * Mth.TWO_PI / 6 + hash(a.seed(), i);
            Vec3 top = feet.add(Mth.cos(ang) * .25, .02, Mth.sin(ang) * .25);
            c.line(top, top.add(Mth.cos(ang) * .5, -.9, Mth.sin(ang) * .5), .02F, JADE, .6F * f, 0);
        }
    }

    // ------------------------------------------------------------------
    // Rock Strike
    // ------------------------------------------------------------------

    /** The stomp tears a stone up: the ground caves in a divot, cracks run from it, lumps hop up and fall, dust lifts. */
    private static void lift(Canvas c, Active a, float t) {
        Vec3 at = a.origin().add(0, .3, 0);
        float[] rock = colour(c, a.seed());
        float f = fade(t, a.piece().ticks, 0, 6), k = smooth(0, 4, t);
        if (c.soft()) c.disc(at.add(0, .02, 0), UP, .45F * a.scale() * k, CRACK, .8F * f, .1F * f);
        for (int i = 0; i < 5; i++) crack(c, at, i * Mth.TWO_PI / 5 + hash(a.seed(), i), (.5F + .4F * hash(a.seed(), i + 9)) * k * a.scale(), a.seed() + i, 0, f);
        burst(c, at, UP, minimal() ? 3 : 6, .12F, .12F, t, a.piece().ticks, rock, a.seed());
        dust(c, at.add(0, .2 + .05 * t, 0), .3F + .04F * t, .5F * f, a.seed() & 7);
    }

    /** The punch that drives a rock on: a jade ring kicks out behind it, and dust tears off its back. */
    private static void throwRing(Canvas c, Active a, float t) {
        Vec3 at = a.origin(), dir = a.target().subtract(at);
        if (dir.lengthSqr() < 1e-6) return;
        dir = dir.normalize();
        float f = fade(t, a.piece().ticks, 0, 5), k = smooth(0, 5, t);
        seam(c, at.subtract(dir.scale(.3 + .5 * k)), dir, (a.scale() * .6F + .2F) * (1 + k), .04F, 0, Mth.TWO_PI, f * (1 - k));
        dust(c, at.subtract(dir.scale(.4 + .8 * k)), .25F + .25F * a.scale(), .4F * f * (1 - k), a.seed() & 7);
    }

    /** A rock breaks on a wall or a body: lumps of it burst out off the surface, and dust rolls. */
    private static void shatter(Canvas c, Active a, float t) {
        Vec3 at = a.origin(), n = a.target().subtract(at);
        float[] rock = colour(c, a.seed());
        float s = Math.max(.25F, a.scale()), life = a.piece().ticks, f = fade(t, life, 0, 6);
        burst(c, at, n, minimal() ? 4 : 6 + Math.round(s * 5), .16F * s + .05F, .14F + .06F * s, t, life, rock, a.seed());
        dust(c, at.add(n.normalize().scale(.2 + .03 * t)), .3F + .5F * s * smooth(0, 6, t), .55F * f, a.seed() & 7);
        if (n.y > .7) for (int i = 0; i < 5; i++) crack(c, at, i * Mth.TWO_PI / 5 + hash(a.seed(), i), .8F * s * smooth(0, 3, t), a.seed() + i, 0, f);
    }

    /** Tiger Claw: stone closes over the fist, then cracks off it at the hit with three dark rake marks. */
    private static void claw(Canvas c, Active a, float t) {
        Entity owner = a.bound(c);
        float[] rock = colour(c, a.seed());
        float g = a.scale(), f = fade(t, a.piece().ticks, 0, 4);
        boolean firstPerson = owner == Minecraft.getInstance().player && Minecraft.getInstance().options.getCameraType() == CameraType.FIRST_PERSON;
        if (owner != null && t < 4 && !firstPerson) {
            Vec3 fist = hand(c, owner, g < 1.2F);
            int n = g > 1.2F ? 6 : 4;
            for (int i = 0; i < n; i++) {
                float ang = i * Mth.TWO_PI / n + hash(a.seed(), i);
                chunk(c, fist.add(Mth.cos(ang) * .11, Mth.sin(ang) * .11, 0), (g > 1.2F ? .11F : .08F), ang, i, 0, rock, 1 - smooth(3, 5, t), a.seed() + i);
            }
        }
        Vec3 hit = a.target();
        Vec3 dir = owner == null ? new Vec3(0, 0, 1) : flat(hit.subtract(owner.position()));
        if (t >= 1) {
            burst(c, hit, dir.scale(-1).add(0, .3, 0), minimal() ? 2 : 4, .07F * g, .12F, t - 1, a.piece().ticks - 1, rock, a.seed() + 9);
            if (c.soft()) for (int i = -1; i <= 1; i++) {
                Vec3 o = hit.subtract(dir.scale(.3)).add(side(dir).scale(i * .12));
                c.line(o.add(0, .25, 0), o.add(0, -.25, 0), .025F, CRACK, .8F * f, .8F * f);
            }
            dust(c, hit, .2F * g, .35F * f, a.seed() & 7);
        }
    }

    // ------------------------------------------------------------------
    // Earth Shackle and Crush
    // ------------------------------------------------------------------

    /** Earth Shackle: plates of the ground climb the legs and close round them; mud (scale 10+) is darker and wet. */
    private static void shackle(Canvas c, Active a, float t) {
        boolean mud = a.scale() >= 10;
        float w = Math.max(.4F, a.scale() - (mud ? 15 : 0)), life = a.piece().ticks;
        Vec3 at = a.origin();
        float[] rock = mud ? MUD : colour(c, a.seed());
        float climb = smooth(0, 6, t), sink = smooth(life - 6, life, t);
        int n = minimal() ? 5 : 7;
        for (int i = 0; i < n; i++) {
            float ang = i * Mth.TWO_PI / n + hash(a.seed(), i) * .3F, r = w * .55F + .12F;
            float h = (.25F + .3F * hash(a.seed(), i + 9)) * climb * (1 - sink);
            Vec3 p = at.add(Mth.cos(ang) * r, h * .5 - .05, Mth.sin(ang) * r);
            if (c.solid() && h > .02F) c.box(p, .14F, h * .5F + .05F, .09F, -ang, 0, -(.35F + .2F * climb), rock, 1);
        }
        for (int i = 0; i < 6; i++) crack(c, at, i * Mth.TWO_PI / 6 + hash(a.seed(), i + 3), .9F * climb, a.seed() + i, 0, 1 - sink);
        dust(c, at.add(0, .1, 0), .4F + .2F * climb, .45F * (1 - smooth(4, 14, t)), a.seed() & 7);
    }

    /** Crush: the plates squeeze in all at once, cracks shoot up the band, and lumps pop off it. */
    private static void crush(Canvas c, Active a, float t) {
        float w = Math.max(.4F, a.scale()), life = a.piece().ticks, f = fade(t, life, 0, 5);
        Vec3 at = a.origin();
        float[] rock = colour(c, a.seed());
        float sq = smooth(0, 2, t);
        for (int i = 0; i < 8; i++) {
            float ang = i * Mth.TWO_PI / 8, r = (w * .55F + .12F) * (1 - .35F * sq);
            Vec3 p = at.add(Mth.cos(ang) * r, .3, Mth.sin(ang) * r);
            chunk(c, p, .12F, -ang, 0, -.5F, rock, 1 - smooth(life - 5, life, t), a.seed() + i);
        }
        burst(c, at.add(0, .5, 0), UP, minimal() ? 3 : 6, .08F, .15F, t, life, rock, a.seed() + 31);
        dust(c, at.add(0, .4, 0), .5F * sq, .5F * f, a.seed() & 7);
    }

    // ------------------------------------------------------------------
    // Tunnel
    // ------------------------------------------------------------------

    /** Into the ground (target below) the ground caves in round you; out of it (target above) it bursts up. */
    private static void tunnel(Canvas c, Active a, float t) {
        Vec3 at = a.origin();
        boolean out = a.target().y > at.y;
        float s = a.scale(), life = a.piece().ticks, f = fade(t, life, 0, 6);
        float[] rock = colour(c, a.seed());
        if (out) {
            burst(c, at.add(0, .1, 0), UP, minimal() ? 4 : 8, .14F, .22F, t, life, rock, a.seed());
            dust(c, at.add(0, .4 + .06 * t, 0), .5F * s + .05F * t, .6F * f, a.seed() & 7);
        } else {
            float k = smooth(0, 6, t);
            for (int i = 0; i < 6; i++) {
                float ang = i * Mth.TWO_PI / 6 + hash(a.seed(), i) * .4F;
                Vec3 p = at.add(Mth.cos(ang) * .7 * (1 - .5 * k), .05 - .5 * k, Mth.sin(ang) * .7 * (1 - .5 * k));
                chunk(c, p, .22F, -ang, -.6F * k, 0, rock, 1 - smooth(life - 6, life, t), a.seed() + i);
            }
            if (c.soft()) c.disc(at.add(0, .03, 0), UP, .7F * s, CRACK, .85F * f, .2F * f);
            dust(c, at.add(0, .2, 0), .5F * s, .5F * f * (1 - k * .5F), a.seed() & 7);
        }
        for (int i = 0; i < 6; i++) crack(c, at, i * Mth.TWO_PI / 6 + hash(a.seed(), i + 12), 1.1F * s * smooth(0, 3, t), a.seed() + i, 0, f);
    }

    // ------------------------------------------------------------------
    // Pillar, Wall and the shove
    // ------------------------------------------------------------------

    /** The ground round a pillar's foot splits and puffs; lumps spray off its head as it climbs. */
    private static void pillar(Canvas c, Active a, float t) {
        Vec3 base = a.origin(), top = a.target(), axis = top.subtract(base);
        if (axis.lengthSqr() < 1e-6) return;
        Vec3 n = axis.normalize();
        float h = a.scale(), life = a.piece().ticks, f = fade(t, life, 0, 6);
        float[] rock = colour(c, a.seed());
        float climb = Mth.clamp(t / h, 0, 1);
        Vec3 head = base.add(n.scale(Math.min(h, t + 1)));
        if (t < h + 2) burst(c, head, n, minimal() ? 2 : 4, .1F, .1F, (t % 3) + c.partial, 4, rock, a.seed() + (int) t);
        Vec3[] plane = plane(n);
        for (int i = 0; i < 6; i++) {
            float ang = i * Mth.TWO_PI / 6 + hash(a.seed(), i);
            Vec3 dir = plane[0].scale(Mth.cos(ang)).add(plane[1].scale(Mth.sin(ang)));
            Vec3[] pts = new Vec3[5];
            for (int j = 0; j < 5; j++) pts[j] = base.add(dir.scale(.5 + j * .3 * smooth(0, 3, t))).add(n.scale(.02))
                    .add(plane[0].scale((hash(a.seed(), i * 5 + j) - .5) * .12));
            if (c.soft()) c.ribbon(pts, .08F, CRACK, .8F * f, .1F * f);
        }
        seam(c, base.add(n.scale(.03)), n, .8F + .5F * climb, .04F, 0, Mth.TWO_PI, f * (1 - climb));
        dust(c, base.add(n.scale(.3)), .55F + .2F * climb, .55F * f, a.seed() & 7);
    }

    private static Vec3[] plane(Vec3 n) {
        Vec3 u = Math.abs(n.y) > .9 ? new Vec3(1, 0, 0) : n.cross(UP).normalize();
        return new Vec3[] {u, u.cross(n).normalize()};
    }

    /** A wall rises: a curtain of dust along its foot, a long crack in front, lumps spat forward. */
    private static void wall(Canvas c, Active a, float t) {
        Vec3 mid = a.origin(), fwd = flat(a.target().subtract(mid)), side = side(fwd);
        float width = a.scale(), life = a.piece().ticks, f = fade(t, life, 0, 6);
        float[] rock = colour(c, a.seed());
        int n = Math.max(3, Math.round(width));
        for (int i = 0; i < n; i++) {
            float k = (i + .5F) / n - .5F;
            Vec3 foot = mid.add(side.scale(k * width));
            dust(c, foot.add(fwd.scale(.6 + .03 * t)).add(0, .3 + .02 * t, 0), .45F + .03F * t, .5F * f, (a.seed() + i) & 7);
            burst(c, foot.add(fwd.scale(.5)).add(0, .2, 0), fwd.add(0, .6, 0), minimal() ? 1 : 2, .1F, .1F, t, life, rock, a.seed() + i * 9);
        }
        Vec3 from = mid.add(fwd.scale(.9)).add(side.scale(-width * .5)), to = mid.add(fwd.scale(.9)).add(side.scale(width * .5));
        Vec3[] pts = new Vec3[9];
        for (int j = 0; j < 9; j++) pts[j] = from.lerp(to, j / 8.0).add(fwd.scale((hash(a.seed(), j) - .5) * .25)).add(0, .03, 0);
        if (c.soft()) c.ribbon(pts, .08F * smooth(0, 3, t), CRACK, .8F * f, .8F * f);
    }

    /** A shoved wall: dust boils off its back, and it leaves a dark furrow in the ground behind it. */
    private static void shove(Canvas c, Active a, float t) {
        Vec3 mid = a.origin(), push = flat(a.target().subtract(mid)), side = side(push);
        float width = Math.max(3, a.scale() / 3F), life = a.piece().ticks, f = fade(t, life, 0, 5);
        for (int i = -1; i <= 1; i++)
            dust(c, mid.subtract(push.scale(.6 + .08 * t)).add(side.scale(i * width * .35)).add(0, .5, 0), .5F + .05F * t, .45F * f, (a.seed() + i + 1) & 7);
        if (c.soft()) c.line(mid.subtract(push.scale(1.6)).add(0, .03, 0), mid.add(0, .03, 0), width * .45F, CRACK, 0, .45F * f);
    }

    // ------------------------------------------------------------------
    // Rising Mountain
    // ------------------------------------------------------------------

    /** The warning: a jade arc sweeps round the ring like a dial, full exactly as the ground tears free, cracks along it. */
    private static void mark(Canvas c, Active a, float t) {
        Vec3 at = a.origin().add(0, .05, 0);
        float r = a.scale(), life = a.piece().ticks, fill = Mth.clamp(t / life, 0, 1), f = fade(t, life + 1, 2, 0);
        seam(c, at, UP, r, .06F, 0, Mth.TWO_PI, f * .35F);
        seam(c, at, UP, r, .09F, -Mth.HALF_PI, -Mth.HALF_PI + fill * Mth.TWO_PI, f);
        int n = minimal() ? 8 : 14;
        for (int i = 0; i < n; i++) {
            float ang = i * Mth.TWO_PI / n;
            if (ang > fill * Mth.TWO_PI) continue;
            crack(c, at.add(Mth.cos(ang) * r, 0, Mth.sin(ang) * r), ang + Mth.HALF_PI, r * Mth.TWO_PI / n * 1.1F, a.seed() + i, .5F, f);
        }
        if (c.soft()) c.disc(at, UP, r * fill, CRACK, .05F * f, .25F * f);
    }

    /** The plug of ground in the air: a dark pit under it, its ragged rim, and rubble streaming off its underside. */
    private static void mountain(Canvas c, Active a, float t) {
        Vec3 at = a.origin();
        float r = a.scale(), h = (float) (a.target().y - at.y), life = a.piece().ticks, f = fade(t, life, 2, 2);
        float[] rock = colour(c, a.seed());
        // Once the slam lands, the last re-sent copy stops at once (its height is a moment out of date).
        for (Vec3 slam : SetPieceClient.where(com.jayptucker.heroesevolved.styles.Piece.EARTH_MOUNTAIN_SLAM))
            if (slam.distanceToSqr(at) < 1) return;
        if (h < .3F) {
            // The moment it tears free: dust bursts out round the rim.
            for (int i = 0; i < 10; i++) {
                float ang = i * Mth.TWO_PI / 10;
                dust(c, at.add(Mth.cos(ang) * r, .3 + .05 * t, Mth.sin(ang) * r), .6F + .06F * t, .6F * (1 - smooth(4, life, t)), i & 7);
            }
            return;
        }
        if (c.soft()) c.disc(at.add(0, .03, 0), UP, r, CRACK, .9F * f, .6F * f);
        for (int i = 0; i < 10; i++) {
            float ang = i * Mth.TWO_PI / 10 + hash(a.seed(), i) * .3F;
            crack(c, at.add(Mth.cos(ang) * r, 0, Mth.sin(ang) * r), ang, .6F + .5F * hash(a.seed(), i + 4), a.seed() + i, 0, f);
        }
        float time = c.level.getGameTime() + c.partial;
        int n = minimal() ? 6 : 12;
        for (int i = 0; i < n; i++) {
            // Each lump drops from the plug's underside and falls to the pit, round and round.
            float phase = ((time * .09F + hash(a.seed(), i)) % 1F), ang = hash(a.seed(), i + 30) * Mth.TWO_PI, rr = r * .7F * hash(a.seed(), i + 60);
            Vec3 p = at.add(Mth.cos(ang) * rr, h - 2.4 - phase * phase * (h - 1.4), Mth.sin(ang) * rr);
            chunk(c, p, .12F, ang + phase * 3, phase * 4, 0, rock, 1, a.seed() + i);
        }
        dust(c, at.add(0, h - 2.4, 0), r * .45F, .15F * f, a.seed() & 7);
    }

    /** The mountain comes down: a star of cracks, slabs heaved up round the rim, a ring of dust rolling out. */
    private static void slam(Canvas c, Active a, float t) {
        Vec3 at = a.origin();
        float r = a.scale(), life = a.piece().ticks, f = fade(t, life, 0, 10);
        float[] rock = colour(c, a.seed());
        float k = smooth(0, 4, t), seal = smooth(10, 24, t);
        for (int i = 0; i < 12; i++)
            crack(c, at, i * Mth.TWO_PI / 12 + hash(a.seed(), i), (r + 2.5F) * k, a.seed() + i, .8F * (1 - seal), f);
        int slabs = minimal() ? 8 : 14;
        for (int i = 0; i < slabs; i++) {
            float ang = i * Mth.TWO_PI / slabs + hash(a.seed(), i + 30) * .2F;
            Vec3 p = at.add(Mth.cos(ang) * (r + .4), .2 - .5 * seal, Mth.sin(ang) * (r + .4));
            if (c.solid()) c.box(p, .5F, .12F, .38F, -ang, -(.9F * k) * (1 - seal), 0, rock, 1);
        }
        for (int i = 0; i < 12; i++) {
            float ang = i * Mth.TWO_PI / 12, out = r + .5F + 3F * smooth(0, 18, t);
            dust(c, at.add(Mth.cos(ang) * out, .5, Mth.sin(ang) * out), .9F + .05F * t, .65F * f, i & 7);
        }
        burst(c, at.add(0, .3, 0), UP, minimal() ? 6 : 14, .22F, .3F, t, life * .7F, rock, a.seed());
        seam(c, at.add(0, .05, 0), UP, r + 5 * k, .1F, 0, Mth.TWO_PI, f * (1 - k));
    }

    // ------------------------------------------------------------------
    // The Boulder lane
    // ------------------------------------------------------------------

    /** Earth Gauntlet: stone fingers close round the chest; dragged, a furrow runs back to the bender; pinned, it splays. */
    private static void gauntlet(Canvas c, Active a, float t) {
        boolean pin = a.scale() >= 10;
        Entity body = a.bound(c);
        Vec3 chest = body == null ? a.origin() : body.getPosition(c.partial).add(0, body.getBbHeight() * .55, 0);
        Vec3 toMe = flat(a.target().subtract(chest));
        float w = Math.max(.35F, a.scale() - (pin ? 10 : 0)) * .5F + .1F, life = a.piece().ticks, f = fade(t, life, 0, 4);
        float[] rock = colour(c, a.seed());
        float close = smooth(0, 3, t), gone = smooth(life - 4, life, t);
        float yaw = (float) Math.atan2(toMe.x, toMe.z);
        for (int i = 0; i < 5; i++) {
            float ang = yaw + Mth.PI + (i - 2) * .55F * (1 - .3F * close);
            Vec3 p = chest.add(Mth.sin(ang) * w, (i - 2) * .12, Mth.cos(ang) * w);
            chunk(c, p, .13F, -ang, .3F, 0, rock, 1 - gone, a.seed() + i);
        }
        chunk(c, chest.add(toMe.scale(w + .05)), .2F, yaw, 0, 0, rock, 1 - gone, a.seed() + 9);
        if (!pin && c.soft()) {
            Vec3 foot = chest.subtract(0, body == null ? .9 : body.getBbHeight() * .55, 0).add(0, .03, 0);
            c.line(foot, foot.add(toMe.scale(2.5 * f)), .25F, CRACK, .5F * f, 0);
        }
        if (pin) burst(c, chest.subtract(toMe.scale(w + .2)), toMe.scale(-1), 5, .1F, .1F, t, life, rock, a.seed() + 50);
        dust(c, chest, .3F, .3F * f * (1 - close * .5F), a.seed() & 7);
    }

    /** Avalanche: cracks race ahead along the line the boulders will roll, dust jumping up as each one tears free. */
    private static void avalanche(Canvas c, Active a, float t) {
        Vec3 from = a.origin(), to = a.target(), dir = flat(to.subtract(from));
        float len = (float) from.distanceTo(to), life = a.piece().ticks, f = fade(t, life, 0, 6), run = smooth(0, 10, t);
        Vec3[] pts = new Vec3[12];
        for (int j = 0; j < 12; j++)
            pts[j] = from.add(dir.scale(len * run * j / 11.0)).add(side(dir).scale((hash(a.seed(), j) - .5) * .5)).add(0, .03, 0);
        if (c.soft()) c.ribbon(pts, .12F, CRACK, .8F * f, .3F * f);
        if (c.glow()) c.ribbon(pts, .03F, JADE, .5F * f, 0);
        for (int i = 0; i < 5; i++) {
            float age = t - i * 3;
            if (age < 0) continue;
            dust(c, from.add(dir.scale(2 + i * 1.6)).add(0, .4 + .04 * age, 0), .5F + .05F * age, .5F * (1 - smooth(4, 14, age)), (a.seed() + i) & 7);
        }
    }

    // ------------------------------------------------------------------
    // Earth Tent and Earth Surf
    // ------------------------------------------------------------------

    /** Earth Tent: dust bursts round its foot as the dome closes, and a jade sheen climbs it from the ground up. */
    private static void tent(Canvas c, Active a, float t) {
        Vec3 at = a.origin();
        float r = a.scale(), life = a.piece().ticks, f = fade(t, life, 0, 8), close = smooth(0, 8, t);
        for (int i = 0; i < 10; i++) {
            float ang = i * Mth.TWO_PI / 10;
            dust(c, at.add(Mth.cos(ang) * (r + .3), .3, Mth.sin(ang) * (r + .3)), .5F + .04F * t, .5F * f, i & 7);
        }
        for (int k = 0; k < 4; k++) {
            float lat = close * Mth.HALF_PI * (k + 1) / 4F;
            seam(c, at.add(0, Mth.sin(lat) * r, 0), UP, Mth.cos(lat) * r + .05F, .03F, 0, Mth.TWO_PI, f * .5F * (1 - smooth(8, 16, t)));
        }
    }

    /** Earth Surf: a crest of ground heaves up under the feet and behind them, throwing lumps and dust off its back. */
    private static void surf(Canvas c, Active a, float t) {
        Entity body = a.bound(c);
        if (body == null) return;
        Vec3 feet = body.getPosition(c.partial);
        Vec3 travel = feet.subtract(body.xo, body.yo, body.zo);
        Vec3 dir = travel.lengthSqr() < 1e-4 ? flat(body.getViewVector(c.partial)) : flat(travel);
        float life = a.piece().ticks, f = fade(t, life, 2, 2);
        float[] rock = colour(c, a.seed());
        float time = c.level.getGameTime() + c.partial;
        int n = minimal() ? 5 : 9;
        for (int i = 0; i < n; i++) {
            float k = i / (float) (n - 1), back = .2F + k * 1.6F, lift = Mth.sin(k * Mth.PI) * .45F + .05F;
            Vec3 p = feet.subtract(dir.scale(back)).add(side(dir).scale((hash(a.seed(), i) - .5) * .7)).add(0, lift - .25, 0);
            chunk(c, p, .2F - .06F * k, time * .1F + i, .4F, 0, rock, f, a.seed() + i);
        }
        dust(c, feet.subtract(dir.scale(2.2)).add(0, .3, 0), .4F, .22F * f, (int) time & 7);
    }

    // ------------------------------------------------------------------
    // Tiger and Crane
    // ------------------------------------------------------------------

    /** Twin Slab: a slab heaves out of the ground and slides ahead into the foe, then breaks up, dust trailing it. */
    private static void slab(Canvas c, Active a, float t) {
        Vec3 from = a.origin(), to = a.target(), dir = flat(to.subtract(from));
        float life = a.piece().ticks, go = smooth(1, 6, t), up = smooth(0, 2, t), gone = smooth(life - 5, life, t);
        float[] rock = colour(c, a.seed());
        Vec3 p = from.lerp(to, go).add(0, -.5 + up * .95, 0);
        float yaw = (float) Math.atan2(dir.x, dir.z);
        if (c.solid() && gone < 1) c.box(p, .65F * (1 - gone), .5F * (1 - gone), .18F * (1 - gone), -yaw, 0, 0, rock, 1);
        if (t > 6) burst(c, to.add(0, .5, 0), dir.add(0, .4, 0), minimal() ? 3 : 6, .14F, .14F, t - 6, life - 6, rock, a.seed());
        dust(c, from.lerp(to, go * .8F).add(0, .25, 0), .4F + .2F * go, .45F * (1 - gone), a.seed() & 7);
        if (c.soft()) c.line(from.add(0, .03, 0), from.lerp(to, go).add(0, .03, 0), .3F, CRACK, .4F * (1 - gone), .2F * (1 - gone));
    }

    /** Horse Stomp: a ring of stone spikes stabs up round the feet, holds a moment, and sinks back. */
    private static void spikes(Canvas c, Active a, float t) {
        Vec3 at = a.origin();
        float r = a.scale(), life = a.piece().ticks, up = smooth(0, 3, t), down = smooth(life - 6, life, t);
        float[] rock = colour(c, a.seed());
        int n = minimal() ? 6 : 11;
        for (int i = 0; i < n; i++) {
            float ang = i * Mth.TWO_PI / n + hash(a.seed(), i) * .3F, rr = 1.1F + (r - 1.1F) * hash(a.seed(), i + 9);
            float h = (.5F + .4F * hash(a.seed(), i + 20)) * up * (1 - down);
            Vec3 base = at.add(Mth.cos(ang) * rr, -.1, Mth.sin(ang) * rr);
            if (c.solid() && h > .02F) c.shard(base, base.add(Mth.cos(ang) * .15, h, Mth.sin(ang) * .15), .13F, 5, ang, rock, 1);
        }
        for (int i = 0; i < 8; i++) crack(c, at, i * Mth.TWO_PI / 8 + hash(a.seed(), i), r * up, a.seed() + i, 0, 1 - down);
        dust(c, at.add(0, .2, 0), .6F * up, .45F * (1 - smooth(4, 12, t)), a.seed() & 7);
    }

    /** Earth Slide: the ground carries the feet, a curl of it rolling ahead of them and a furrow left behind. */
    private static void slide(Canvas c, Active a, float t) {
        Entity body = a.bound(c);
        Vec3 from = a.origin(), to = a.target(), dir = flat(to.subtract(from));
        Vec3 feet = body == null ? from.lerp(to, smooth(0, 8, t)) : body.getPosition(c.partial);
        float life = a.piece().ticks, f = fade(t, life, 0, 5), go = smooth(0, 8, t);
        float[] rock = colour(c, a.seed());
        if (c.soft()) c.line(from.add(0, .03, 0), feet.add(0, .03, 0), .3F, CRACK, .15F * f, .6F * f);
        for (int i = 0; i < 4; i++) {
            Vec3 p = feet.add(dir.scale(.45)).add(side(dir).scale((i - 1.5) * .22)).add(0, .08 + .1 * Mth.sin(i * 1.3F + t), 0);
            chunk(c, p, .13F, i + t * .3F, .6F, 0, rock, (1 - go * .6F) * f, a.seed() + i);
        }
        dust(c, feet.add(dir.scale(.3)).add(0, .2, 0), .3F + .2F * go, .45F * f, a.seed() & 7);
    }

    // ------------------------------------------------------------------
    // Ultimates (phasing/DensityUltimates)
    // ------------------------------------------------------------------

    /** The block id in an Ultimate piece's seed (its low 24 bits). */
    private static int blockOf(int seed) { return seed & 0xFFFFFF; }

    /**
     * Earth's Embrace ({@code DensityUltimates.EarthsEmbrace}): a crack running to the foe and the ground closing round
     * its legs, two walls of rock rising either side and clapped shut, a pillar throwing it up, a boulder heaved out of the
     * ground and punted into it, and the boulder bursting. The rock's block rides in the companion {@code EARTH_ULT_ROCK}.
     */
    static void embrace(Canvas c, Active a, float t) {
        if (UltArt.cut(c, a)) return;
        Entity foe = UltArt.foe(c, a);
        Active rockPiece = SetPieceClient.boundTo(com.jayptucker.heroesevolved.styles.Piece.EARTH_ULT_ROCK, a.entity());
        int block = rockPiece != null ? blockOf(rockPiece.seed()) : 1;
        float[] col = colour(c, block);
        Vec3 base = a.target(), facing = a.facing(), sideways = side(facing);
        float h = Math.max(.8F, a.scale());
        int seed = a.seed() * 7 + 3;
        int swallow = com.jayptucker.heroesevolved.phasing.DensityUltimates.EarthsEmbrace.SWALLOW,
                walls = com.jayptucker.heroesevolved.phasing.DensityUltimates.EarthsEmbrace.WALLS,
                clap = com.jayptucker.heroesevolved.phasing.DensityUltimates.EarthsEmbrace.CLAP,
                pillarAt = com.jayptucker.heroesevolved.phasing.DensityUltimates.EarthsEmbrace.PILLAR,
                heave = com.jayptucker.heroesevolved.phasing.DensityUltimates.EarthsEmbrace.HEAVE,
                impact = com.jayptucker.heroesevolved.phasing.DensityUltimates.EarthsEmbrace.IMPACT;
        Vec3 feet = foe != null ? foe.getPosition(c.partial) : base.add(0, com.jayptucker.heroesevolved.phasing.DensityUltimates.EarthsEmbrace.height(t), 0);
        // The crack to the foe, jade in it.
        float run = smooth(0, swallow, t), cracked = fade(t, a.piece().ticks, 0, 20);
        Vec3 from = a.origin();
        float ang = (float) Math.atan2(base.z - from.z, base.x - from.x);
        crack(c, from, ang, (float) Math.sqrt(base.subtract(from).horizontalDistanceSqr()) * run, seed, 1 - smooth(swallow, swallow + 10, t), cracked);
        // The ground closing round its legs until the pillar.
        float legs = fade(t - swallow, pillarAt - swallow + 4, 3, 4);
        if (legs > 0) {
            for (int i = 0; i < 7; i++) {
                float g = i * Mth.TWO_PI / 7 + hash(seed, i);
                Vec3 at = base.add(Mth.cos(g) * .45, .2 + .25 * hash(seed, i + 9), Mth.sin(g) * .45);
                chunk(c, at, .32F, g, .3F, .2F, col, legs, seed + i);
            }
            if (t < swallow + 8) dust(c, base.add(0, .3, 0), .8F, .5F * (1 - (t - swallow) / 8), seed);
        }
        // The walls: up either side, clapped shut, then crumbling away.
        float wallsUp = smooth(walls, walls + 8, t), shut = smooth(clap - 3, clap, t), gone = 1 - smooth(clap + 6, clap + 14, t);
        if (t >= walls && gone > 0) {
            for (int s = -1; s <= 1; s += 2) {
                double off = s * (1.7 - 1.15 * shut);
                Vec3 at = base.add(sideways.scale(off)).add(0, -1.2 + 2.4 * wallsUp, 0);
                if (c.solid()) c.box(at, .3F * gone, 1.2F * gone, .9F * gone, (float) Math.atan2(facing.z, facing.x), 0, 0, col, 1);
                if (t < walls + 8) dust(c, base.add(sideways.scale(off)).add(0, .3, 0), .7F, .5F, seed + s);
            }
            if (t >= clap && t < clap + 8) dust(c, base.add(0, 1.2, 0), 1.2F, .6F * (1 - (t - clap) / 8), seed + 5);
        }
        // The pillar under the foe.
        if (t >= pillarAt && t < impact + 6) {
            float f = UltArt.window(t, pillarAt, impact + 6, 1, 6);
            double top = feet.y - base.y;
            int n = Math.max(1, (int) (top / .9));
            for (int i = 0; i < n; i++) chunk(c, base.add(0, i * .9 + .45, 0), .55F, i * .7F, 0, 0, col, f, seed + 30 + i);
            if (t < pillarAt + 6) seam(c, base.add(0, .05, 0), UP, 1.2F + .8F * smooth(pillarAt, pillarAt + 6, t), .06F, 0, Mth.TWO_PI, 1 - smooth(pillarAt, pillarAt + 6, t));
        }
        // The boulder: heaved up, punted, bursting on the foe.
        if (t >= heave && t < impact) {
            Vec3 at = com.jayptucker.heroesevolved.phasing.DensityUltimates.EarthsEmbrace.boulder(a.origin(), facing, base, t);
            float grow = smooth(heave, heave + 10, t);
            chunk(c, at, .5F + .55F * grow, t * .2F, t * .15F, .1F, col, 1, seed + 50);
            if (t < heave + 8) dust(c, a.origin().add(facing.scale(1.6)).add(0, .2, 0), .7F, .5F, seed + 51);
        }
        if (t >= impact) {
            float age = t - impact, life = a.piece().ticks - impact;
            burst(c, feet.add(0, h * .5, 0), facing, 12, .35F, .2F, age, life, col, seed + 60);
            if (age < 12) dust(c, feet.add(0, h * .5, 0), 1 + age * .1F, .6F * (1 - age / 12), seed + 61);
        }
    }

    /**
     * Stone Fortress, one second (re-sent each second; the block in the seed's low bits, the second in its top byte, 255
     * the slabs flung out): four slabs standing round her, turning slowly, rising out of the ground on the first second;
     * a jade ring where her stance is set.
     */
    static void fortress(Canvas c, Active a, float t) {
        if (UltArt.cut(c, a)) return;
        Entity body = a.bound(c);
        Vec3 feet = body != null ? body.getPosition(c.partial) : a.origin();
        int second = a.seed() >>> 24 & 0xFF;
        float[] col = colour(c, blockOf(a.seed()));
        float r = a.scale();
        int slabs = com.jayptucker.heroesevolved.phasing.DensityUltimates.StoneFortress.SLABS;
        if (second == 0xFF) {
            float life = a.piece().ticks;
            for (int i = 0; i < slabs; i++) {
                double g = com.jayptucker.heroesevolved.phasing.DensityUltimates.StoneFortress.angle(i, com.jayptucker.heroesevolved.phasing.DensityUltimates.StoneFortress.TICKS);
                Vec3 at = feet.add(Math.cos(g) * (r + t * .5), 1.1 + .15 * t - .02 * t * t, Math.sin(g) * (r + t * .5));
                if (c.solid()) c.box(at, .2F * (1 - t / life), 1.1F * (1 - t / life), .7F * (1 - t / life), (float) g, t * .1F, 0, col, 1);
                dust(c, at, .5F, .4F * (1 - t / life), i);
            }
            return;
        }
        boolean last = second >= com.jayptucker.heroesevolved.phasing.DensityUltimates.StoneFortress.TICKS / 20 - 1;
        float f = last ? 1 - smooth(14, 22, t) : 1;
        float rise = second == 0 ? smooth(0, 10, t) : 1;
        float age = second * 20 + t;
        for (int i = 0; i < slabs; i++) {
            double g = com.jayptucker.heroesevolved.phasing.DensityUltimates.StoneFortress.angle(i, age);
            Vec3 at = feet.add(Math.cos(g) * r, -1.1 + 2.2 * rise, Math.sin(g) * r);
            if (c.solid() && f > .01F) c.box(at, .2F, 1.1F, .7F, (float) g, 0, 0, col, 1);
            if (second == 0 && t < 10) dust(c, feet.add(Math.cos(g) * r, .3, Math.sin(g) * r), .6F, .5F * (1 - t / 10), i);
        }
        seam(c, feet.add(0, .05, 0), UP, .9F, .05F, 0, Mth.TWO_PI, f * .7F);
    }

    /**
     * Mountain Hall, one second (re-sent each second; the block in the seed's low bits, the second in its top byte, 255
     * its crumbling): a dome of rock chunks over the shelter, rising row by row on the first second, falling outward at
     * the end.
     */
    static void hall(Canvas c, Active a, float t) {
        int second = a.seed() >>> 24 & 0xFF;
        float[] col = colour(c, blockOf(a.seed()));
        Vec3 centre = a.origin();
        float r = a.scale();
        int rows = 3, per = minimal() ? 9 : 16;
        boolean crumbling = second == 0xFF;
        for (int row = 0; row < rows; row++) {
            double lat = (row + .5) / rows * Math.PI / 2;
            double ring = r * Math.cos(lat), y = r * .75 * Math.sin(lat);
            float rise = second == 0 ? smooth(row * 5, row * 5 + 8, t) : 1;
            for (int i = 0; i < per; i++) {
                double g = (i + row * .5) * Math.PI * 2 / per;
                Vec3 at = centre.add(Math.cos(g) * ring, y * rise - (1 - rise) * .8, Math.sin(g) * ring);
                float scale = 1;
                if (crumbling) {
                    at = at.add(Math.cos(g) * t * .2, -.02 * t * t, Math.sin(g) * t * .2);
                    scale = 1 - smooth(4, a.piece().ticks, t);
                }
                chunk(c, at, .62F - row * .08F, (float) g, (float) lat, 0, col, scale, row * 31 + i);
            }
        }
        if (second == 0 && t < 16) for (int i = 0; i < 6; i++) {
            double g = i * Math.PI / 3;
            dust(c, centre.add(Math.cos(g) * r, .3, Math.sin(g) * r), .9F, .5F * (1 - t / 16), i);
        }
        if (!crumbling) seam(c, centre.add(0, .05, 0), UP, r, .05F, 0, Mth.TWO_PI, .5F);
    }

    /** A shot breaking on rock: chips thrown back off it (the block in the seed). */
    static void chip(Canvas c, Active a, float t) {
        burst(c, a.origin(), a.origin().subtract(a.target()), 5, .12F, .12F, t, a.piece().ticks, colour(c, blockOf(a.seed())), a.seed());
    }
}
