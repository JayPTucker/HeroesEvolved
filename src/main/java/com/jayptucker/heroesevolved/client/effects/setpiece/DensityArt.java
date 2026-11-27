package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient.Active;
import com.jayptucker.heroesevolved.phasing.DensityGeometry;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.*;

/**
 * Density's skill tree (earthbending), in {@code EarthArt}'s grammar and colours: stone, dark cracks, and a thin jade seam
 * where the bender's will is in the ground. Every piece is built from three primitives:
 * <ul>
 *   <li>{@link #ripple}: seismic rings opening across the ground, each wider and fainter, wobbling so they never look machined;</li>
 *   <li>{@link #slab}: a block of stone, drawn solid and lit by the sun;</li>
 *   <li>{@link #crack}: a jagged dark line across the ground with jade light in it.</li>
 * </ul>
 * (Density was once D.L. Hawkins's phasing, drawn in ghostly teal; it gave that up to become earthbending.)
 */
final class DensityArt {
    // Earthbending's own colours (EarthArt's): the jade seam of the bender's will, stone, and dark cracks.
    private static final float[] TEAL = EarthArt.JADE, PALE = EarthArt.JADE_PALE, DEEP = EarthArt.JADE_DEEP, SLATE = EarthArt.GREY,
            STONE = rgb(0x9A9284), DARK = EarthArt.CRACK;
    /** Drawn with normal blending behind the light, so rings hold against a bright sky. */
    private static final float[] INK = EarthArt.MUD;

    private DensityArt() {}

    static void draw(Canvas c, Active a, float t) {
        switch (a.piece()) {
            case DENSITY_HARDEN -> harden(c, a, t);
            case DENSITY_REBOUND -> rebound(c, a, t);
            case DENSITY_PILE -> pile(c, a, t);
            case DENSITY_SINK_MARK -> mark(c, a, t);
            case DENSITY_SINKHOLE -> sinkhole(c, a, t);
            case DENSITY_ENTOMB -> entomb(c, a, t);
            case DENSITY_UPROOT -> uproot(c, a, t);
            case DENSITY_GRASP -> grasp(c, a, t);
            default -> { }
        }
    }

    // ------------------------------------------------------------------
    // Primitives
    // ------------------------------------------------------------------

    /** Two axes spanning the plane a surface faces out of. */
    private static Vec3[] plane(Vec3 normal) {
        Vec3 n = normal.lengthSqr() < 1.0E-6 ? UP : normal.normalize();
        Vec3 u = Math.abs(n.y) > .9 ? new Vec3(1, 0, 0) : n.cross(UP).normalize();
        return new Vec3[] {u, u.cross(n).normalize()};
    }

    /**
     * A surface rippling like water across {@code normal}: three rings opening from the point of entry as {@code k} goes
     * from 0 to 1, each wider and fainter. Deep halo, teal body and pale core in the glow pass; an ink outline in the soft.
     */
    private static void ripple(Canvas c, Vec3 at, Vec3 normal, float r, float k, float[] body, float alpha) {
        if (alpha <= .003F || r <= .02F) return;
        for (int i = 0; i < 3; i++) {
            float ri = r * (.3F + .35F * i) * (.35F + .65F * k), ai = alpha * (1 - i * .25F) * (1 - k * .5F);
            float w = .05F + r * .02F;
            if (c.soft()) c.ring(at, normal, ri, w * 2.2F, 0, Mth.TWO_PI, INK, .28F * ai);
            if (!c.glow()) continue;
            c.ring(at, normal, ri, w * 2.6F, 0, Mth.TWO_PI, DEEP, .3F * ai);
            c.ring(at, normal, ri, w, 0, Mth.TWO_PI, body, .8F * ai);
            c.ring(at, normal, ri, w * .3F, 0, Mth.TWO_PI, PALE, .75F * ai);
        }
    }

    /** A block of stone, solid and lit by the sun; it shrinks rather than fades. */
    private static void slab(Canvas c, Vec3 at, float sx, float sy, float sz, float yaw, float pitch, float roll, float[] col, float size) {
        if (!c.solid() || size <= .02F) return;
        c.box(at, sx * size, sy * size, sz * size, yaw, pitch, roll, col, 1);
    }

    /** A jagged dark crack across the ground from {@code at} along {@code angle}, with a pale seam of light in it. */
    private static void crack(Canvas c, Vec3 at, float angle, float length, int seed, float light, float alpha) {
        if (alpha <= .003F || length <= .02F) return;
        int n = 5;
        Vec3[] pts = new Vec3[n + 1];
        float ca = Mth.cos(angle), sa = Mth.sin(angle);
        for (int i = 0; i <= n; i++) {
            float d = length * i / n, j = i == 0 ? 0 : (hash(seed, i) - .5F) * .22F * length / n * 2;
            pts[i] = at.add(ca * d - sa * j, .03, sa * d + ca * j);
        }
        if (c.soft()) c.ribbon(pts, .07F, DARK, .75F * alpha, .1F * alpha);
        if (c.glow() && light > 0) c.ribbon(pts, .02F, PALE, .6F * light * alpha, 0);
    }
    // ------------------------------------------------------------------
    // Density
    // ------------------------------------------------------------------

    private static boolean firstPerson(Entity e) {
        Minecraft mc = Minecraft.getInstance();
        return e == mc.getCameraEntity() && mc.options.getCameraType() == CameraType.FIRST_PERSON;
    }

    /**
     * Harden: plates of stone close over shoulders, chest and forearms, and cracks spread from the feet. The weight it
     * has banked shows as pale light in the seams between the plates, brighter the more it holds.
     */
    private static void harden(Canvas c, Active a, float t) {
        Entity e = a.bound(c);
        Vec3 o = a.at(c);
        float f = fade(t, a.piece().ticks, 0, 2), weight = Mth.clamp((a.scale() - 1) / 5F, 0, 1);
        float yaw = e instanceof LivingEntity body ? Mth.rotLerp(c.partial, body.yBodyRotO, body.yBodyRot) * Mth.DEG_TO_RAD : 0;
        float grow = Mth.clamp((t + 1) / 3F, 0, 1);
        // Plates: front and back of the chest, both shoulders, both forearms. Hidden in your own first-person view.
        if (!firstPerson(e)) {
            float s = Mth.sin(yaw), co = Mth.cos(yaw);
            float[][] plates = {{0, 1.2F, .2F, .26F, .26F, .06F}, {0, 1.2F, -.2F, .26F, .26F, .06F},
                    {.38F, 1.46F, 0, .15F, .09F, .18F}, {-.38F, 1.46F, 0, .15F, .09F, .18F},
                    {.42F, .95F, .04F, .1F, .2F, .11F}, {-.42F, .95F, .04F, .1F, .2F, .11F},
                    {.12F, .62F, .16F, .12F, .14F, .05F}, {-.12F, .62F, .16F, .12F, .14F, .05F}};
            for (int i = 0; i < plates.length; i++) {
                float[] p = plates[i];
                Vec3 at = o.add(p[0] * co - p[2] * s, p[1], p[0] * s + p[2] * co);
                float wob = (hash(a.seed(), i) - .5F) * .2F;
                slab(c, at, p[3], p[4], p[5], yaw + wob, wob * .5F, wob, i < 2 ? SLATE : STONE, grow);
                if (c.glow() && weight > 0) c.glow(at, .18F + .14F * weight, PALE, .5F * weight * f);
            }
        }
        for (int i = 0; i < 4; i++)
            crack(c, o, hash(a.seed(), i + 10) * Mth.TWO_PI, .5F + .4F * hash(a.seed(), i + 20) + weight * .4F, a.seed() + i, weight, .7F * f);
        if (c.glow()) c.ring(o.add(0, .04, 0), UP, .5F, .03F, 0, Mth.TWO_PI, mix(STONE, PALE, weight), .5F * f);
    }

    /** Mountain Armor's plates, in the body's own frame: x across, y up, z forward, then half-sizes. */
    private static final float[][] MOUNTAIN = {
            {0, 1.22F, .2F, .3F, .3F, .08F}, {0, 1.22F, -.21F, .3F, .3F, .08F},          // chest and back
            {.42F, 1.5F, 0, .2F, .12F, .22F}, {-.42F, 1.5F, 0, .2F, .12F, .22F},          // pauldrons
            {.44F, 1.02F, .03F, .13F, .22F, .14F}, {-.44F, 1.02F, .03F, .13F, .22F, .14F}, // gauntlets
            {.13F, .55F, .03F, .14F, .22F, .16F}, {-.13F, .55F, .03F, .14F, .22F, .16F},   // greaves
            {0, .86F, .14F, .26F, .1F, .1F},                                                 // belt
            {0, 2.02F, -.06F, .3F, .07F, .3F}};                                              // crown
    /** When each plate lands, as a fraction of the assembly: the legs first, the crown last. */
    private static final float[] MOUNTAIN_ORDER = {.45F, .5F, .6F, .65F, .75F, .8F, .1F, .2F, .3F, .95F};

    /**
     * Mountain Armor ({@code phasing/MountainArmor}): plates of stone tear out of the ground round the feet and fly up
     * along arcs onto the body (legs first, crown last), each snapping into place with a pale seam flash as it lands, a
     * sheen running over the whole suit at the end; then held on the body, the seams breathing pale light. The first
     * sending (seed 0) assembles it over {@code ASSEMBLE} ticks. Bound to the wearer; re-sent each second.
     */
    static void mountain(Canvas c, Active a, float t) {
        if (SetPieceClient.superseded(a, java.util.Set.of(com.jayptucker.heroesevolved.styles.Piece.DENSITY_ARMOR, com.jayptucker.heroesevolved.styles.Piece.DENSITY_ARMOR_BURST), c.level.getGameTime())) return;
        Entity e = a.bound(c);
        if (e == null) return;
        Vec3 o = e.getPosition(c.partial);
        boolean first = a.seed() == 0;
        int assemble = com.jayptucker.heroesevolved.phasing.MountainArmor.ASSEMBLE;
        float life = a.piece().ticks, f = Mth.clamp((life - t) / 2 + .5F, 0, 1);
        float yaw = e instanceof LivingEntity body ? Mth.rotLerp(c.partial, body.yBodyRotO, body.yBodyRot) * Mth.DEG_TO_RAD : 0;
        float s = Mth.sin(yaw), co = Mth.cos(yaw);
        float pulse = .5F + .5F * Mth.sin((c.level.getGameTime() + c.partial) * .25F);
        boolean hidden = firstPerson(e);
        for (int i = 0; i < MOUNTAIN.length; i++) {
            float[] p = MOUNTAIN[i];
            Vec3 seat = o.add(p[0] * co - p[2] * s, p[1], p[0] * s + p[2] * co);
            float land = first ? MOUNTAIN_ORDER[i] * assemble : -1;
            float wob = (hash(17, i) - .5F) * .16F;
            if (first && t < land) {
                // In flight: torn out of the ground beside the feet, along an arc to its seat.
                float start = Math.max(0, land - 6), k = smooth(start, land, t);
                if (k <= 0) {
                    // Still in the ground: a crack opening where it will come from.
                    float ang = hash(17, i + 40) * Mth.TWO_PI;
                    crack(c, o.add(Mth.cos(ang) * 1.4, 0, Mth.sin(ang) * 1.4), ang, .5F, 17 + i, .6F, smooth(0, start + .01F, t));
                    continue;
                }
                float ang = hash(17, i + 40) * Mth.TWO_PI;
                Vec3 from = o.add(Mth.cos(ang) * 1.4, -.1, Mth.sin(ang) * 1.4);
                Vec3 at = from.lerp(seat, k).add(0, Mth.sin(k * Mth.PI) * .9, 0);
                if (!hidden) slab(c, at, p[3], p[4], p[5], yaw + wob + (1 - k) * 3, (1 - k) * 2, wob, i < 2 ? SLATE : STONE, .6F + .4F * k);
                if (c.glow()) c.line(from.lerp(at, .7), at, .05F, PALE, 0, .6F * f);
                continue;
            }
            if (!hidden) slab(c, seat, p[3], p[4], p[5], yaw + wob, wob * .4F, wob, i < 2 || i == 9 ? SLATE : STONE, 1);
            if (c.glow()) {
                float snap = first ? 1 - smooth(land, land + 4, t) : 0;
                if (snap > 0) c.glow(seat, .5F, PALE, .9F * snap);
                if (!hidden) c.glow(seat, .2F, TEAL, (.12F + .18F * pulse) * f);
            }
        }
        // The sheen over the finished suit, and the ground it came from.
        if (first && c.glow() && t >= assemble && t < assemble + 6) {
            float k = (t - assemble) / 6;
            c.ring(o.add(0, .2 + 1.9 * k, 0), UP, .55F, .06F, 0, Mth.TWO_PI, PALE, .7F * (1 - k));
        }
        if (first && t < assemble + 4) for (int i = 0; i < 6; i++)
            crack(c, o, hash(17, i + 10) * Mth.TWO_PI, .8F + .6F * hash(17, i + 20), 17 + i, .8F, .8F);
    }

    /** Mountain Armor blasting off: every plate flies out from its seat, tumbling, over a ring of dust and cracks. */
    static void mountainBurst(Canvas c, Active a, float t) {
        Vec3 o = a.origin();
        float life = a.piece().ticks, r = Math.max(1, a.scale()), f = fade(t, life, 0, 8);
        Vec3 look = a.target().subtract(o);
        float yaw = (float) Mth.atan2(look.z, look.x) - Mth.HALF_PI;
        float s = Mth.sin(yaw), co = Mth.cos(yaw);
        for (int i = 0; i < MOUNTAIN.length; i++) {
            float[] p = MOUNTAIN[i];
            Vec3 seat = o.add(p[0] * co - p[2] * s, p[1], p[0] * s + p[2] * co);
            Vec3 out = seat.subtract(o.add(0, 1.1, 0));
            out = out.lengthSqr() < 1e-4 ? new Vec3(0, 1, 0) : out.normalize();
            float d = r * .9F * (1 - (float) Math.exp(-t / 5)), drop = .02F * t * t;
            Vec3 at = seat.add(out.scale(d)).add(0, Math.max(-p[1] + .1, out.y * d * .3 - drop), 0);
            slab(c, at, p[3], p[4], p[5], yaw + t * .3F * (i % 2 == 0 ? 1 : -1), t * .2F, i, i < 2 ? SLATE : STONE, 1 - smooth(life - 8, life, t));
        }
        if (c.glow() && t < 4) c.glow(o.add(0, 1.1, 0), 2.2F, PALE, .7F * (1 - t / 4));
        float k = smooth(0, 10, t);
        ripple(c, o.add(0, .05, 0), UP, r * k, k, STONE, f);
        for (int i = 0; i < 8; i++) crack(c, o, i * Mth.TWO_PI / 8 + hash(a.seed(), i), r * k, a.seed() + i, .6F, f);
    }

    /**
     * Rebound: the weight comes back out of the ground. A shock ring snaps out (ease-out cubic), slabs of stone are thrown
     * up round it and fall back, and dark cracks run out to the ring's edge.
     */
    private static void rebound(Canvas c, Active a, float t) {
        Vec3 o = a.origin().add(0, .05, 0);
        float r = Math.max(1, a.scale()), life = a.piece().ticks, f = fade(t, life, 0, 6);
        float open = 1 - (float) Math.pow(1 - Mth.clamp(t / 6F, 0, 1), 3);
        for (int i = 0; i < 2; i++) {
            float ri = r * open * (1 - i * .25F);
            if (c.soft()) c.ring(o.add(0, i * .05, 0), UP, ri, .3F, 0, Mth.TWO_PI, INK, .25F * f);
            if (c.glow()) {
                c.ring(o.add(0, i * .05, 0), UP, ri, .4F, 0, Mth.TWO_PI, STONE, .35F * f);
                c.ring(o.add(0, i * .05, 0), UP, ri, .08F, 0, Mth.TWO_PI, PALE, .7F * f);
            }
        }
        int n = minimal() ? 6 : 12;
        for (int i = 0; i < n; i++) {
            float ang = i * Mth.TWO_PI / n + hash(a.seed(), i) * .4F, d = r * (.4F + .5F * hash(a.seed(), i + 30)) * open;
            float lift = Math.max(0, 2.4F * (t / life) - 3.2F * (t / life) * (t / life)) * (.6F + hash(a.seed(), i + 60));
            Vec3 at = o.add(Mth.cos(ang) * d, .15 + lift, Mth.sin(ang) * d);
            slab(c, at, .18F, .12F, .14F, ang, t * .25F * (hash(a.seed(), i + 90) - .5F), t * .2F, i % 2 == 0 ? STONE : SLATE,
                    1 - smooth(life - 4, life, t));
            crack(c, o, ang, r * open, a.seed() + i, .6F * (1 - open), .8F * f);
        }
        if (c.glow()) c.glow(o.add(0, .3, 0), r * .35F * (1 - open) + .2F, PALE, .8F * f);
    }

    /** Pile Driver and Crater Landing: a collar of broken stone snaps up round the feet, and a shock rings inward. */
    private static void pile(Canvas c, Active a, float t) {
        Vec3 o = a.origin();
        float w = Math.max(.6F, a.scale()), r = w * .6F + .25F, life = a.piece().ticks, f = fade(t, life, 0, 4);
        float up = Mth.clamp(t / 2F, 0, 1) * (1 - smooth(life - 3, life, t));
        for (int i = 0; i < 7; i++) {
            float ang = i * Mth.TWO_PI / 7 + hash(a.seed(), i) * .5F;
            Vec3 at = o.add(Mth.cos(ang) * r, .08 * up, Mth.sin(ang) * r);
            slab(c, at, .13F, .18F * up + .02F, .06F, -ang + Mth.HALF_PI, .45F, 0, i % 2 == 0 ? STONE : DARK, 1);
            crack(c, o, ang, r + .5F + .3F * hash(a.seed(), i + 10), a.seed() + i, 0, .8F * f);
        }
        float k = Mth.clamp(t / 6F, 0, 1);
        if (c.glow()) {
            c.ring(o.add(0, .06, 0), UP, r * (1.8F - k), .05F, 0, Mth.TWO_PI, PALE, .6F * f);
            c.glow(o.add(0, .2, 0), r * .7F, STONE, .35F * f);
        }
    }

    /** Sinkhole's warning: a ring on the ground whose face fills like a dial, full exactly as the ground goes soft. */
    private static void mark(Canvas c, Active a, float t) {
        Vec3 o = a.origin().add(0, .05, 0);
        float r = Math.max(1, a.scale()), k = Mth.clamp(t / a.piece().ticks, 0, 1);
        float pulse = .8F + .2F * Mth.sin((t + c.partial) * 1.3F);
        if (c.soft()) {
            c.ring(o, UP, r, .16F, 0, Mth.TWO_PI, INK, .3F);
            c.disc(o, UP, r * k, DARK, .25F * k, .05F);
        }
        if (c.glow()) {
            c.ring(o, UP, r, .18F, 0, Mth.TWO_PI, DEEP, .35F * pulse);
            c.ring(o, UP, r, .05F, 0, Mth.TWO_PI, TEAL, .85F * pulse);
            c.ring(o, UP, r, .05F, -Mth.HALF_PI, -Mth.HALF_PI + Mth.TWO_PI * k, PALE, .9F);
            c.ring(o, UP, r * k, .04F, 0, Mth.TWO_PI, TEAL, .5F);
        }
    }

    /** Sinkhole: soft ground. A dark pool with rings drifting inward and three spiral arms turning slowly into the middle. */
    private static void sinkhole(Canvas c, Active a, float t) {
        Vec3 o = a.origin().add(0, .05, 0);
        float r = Math.max(1, a.scale()), f = fade(t, a.piece().ticks, 2, 2);
        float clock = (c.level.getGameTime() % 600) + c.partial;
        if (c.soft()) {
            c.disc(o, UP, r, INK, .75F * f, .25F * f);
            c.ring(o, UP, r, .22F, 0, Mth.TWO_PI, DARK, .6F * f);
        }
        for (int i = 0; i < 4; i++) {
            float k = 1 - ((clock / 30F + i / 4F) % 1F), ri = r * (.12F + .88F * k);
            if (c.soft()) c.ring(o, UP, ri, .1F, 0, Mth.TWO_PI, DARK, .45F * f * k);
            if (c.glow()) {
                c.ring(o, UP, ri, .14F, 0, Mth.TWO_PI, DEEP, .4F * f * k);
                c.ring(o, UP, ri, .045F, 0, Mth.TWO_PI, TEAL, .85F * f * k);
            }
        }
        if (!c.glow()) return;
        for (int arm = 0; arm < 3; arm++) {
            Vec3[] pts = new Vec3[14];
            for (int j = 0; j < pts.length; j++) {
                float q = j / (float) (pts.length - 1), ang = arm * Mth.TWO_PI / 3 + q * 2.4F - clock * .08F;
                pts[j] = o.add(Mth.cos(ang) * r * (1 - q * .9F), .01, Mth.sin(ang) * r * (1 - q * .9F));
            }
            c.ribbon(pts, .09F, TEAL, .1F * f, .7F * f);
            c.ribbon(pts, .03F, PALE, .1F * f, .9F * f);
        }
        c.ring(o, UP, r, .06F, 0, Mth.TWO_PI, TEAL, .85F * f);
        c.ring(o, UP, r, .2F, 0, Mth.TWO_PI, DEEP, .35F * f);
    }

    /**
     * Entombed: slabs of the ground fold in over the body like a closing hand, meet in a cairn, and sink back into the
     * ground, leaving only cracks and a teal seam where it closed.
     */
    private static void entomb(Canvas c, Active a, float t) {
        Vec3 o = a.origin();
        float w = Math.max(.8F, a.scale()), r = w * .7F, life = a.piece().ticks, f = fade(t, life, 0, 6);
        float close = smooth(0, 8, t), sink = smooth(life - 7, life, t);
        int n = 6;
        for (int i = 0; i < n; i++) {
            float ang = i * Mth.TWO_PI / n + hash(a.seed(), i) * .3F;
            float tilt = Mth.lerp(close, 0, 1.15F);
            Vec3 base = o.add(Mth.cos(ang) * r, 0, Mth.sin(ang) * r);
            Vec3 at = base.add(-Mth.cos(ang) * Mth.sin(tilt) * .5, Mth.cos(tilt) * .5 - sink * .9, -Mth.sin(ang) * Mth.sin(tilt) * .5);
            slab(c, at, .07F, .5F, .26F, -ang, 0, tilt * (1 - .1F * hash(a.seed(), i + 5)), i % 2 == 0 ? STONE : SLATE, 1 - sink * .6F);
            crack(c, o, ang + .3F, r + .6F, a.seed() + i, close * (1 - sink), .8F * f);
        }
        if (c.glow()) {
            c.glow(o.add(0, .6 * (1 - close), 0), .5F, TEAL, .4F * (1 - close) * f + .1F * f);
            c.ring(o.add(0, .05, 0), UP, r * (1 - .5F * close), .04F, 0, Mth.TWO_PI, PALE, .6F * f);
        }
    }

    // ------------------------------------------------------------------
    // Listener (Uproot and Grasp from Below, kept from the older tree)
    // ------------------------------------------------------------------

    /** Uproot: a ring of stone shards bursts up round the body as it is torn out of the ground. */
    private static void uproot(Canvas c, Active a, float t) {
        Vec3 o = a.origin();
        boolean torn = a.scale() >= 2;
        float life = a.piece().ticks, f = fade(t, life, 0, 5), up = (1 - (float) Math.pow(1 - Mth.clamp(t / 4F, 0, 1), 3));
        int n = torn ? 9 : 6;
        for (int i = 0; i < n; i++) {
            float ang = i * Mth.TWO_PI / n + hash(a.seed(), i) * .4F, r = .45F + .3F * hash(a.seed(), i + 9);
            float hgt = (torn ? 1.4F : .9F) * up * (.6F + .5F * hash(a.seed(), i + 18)) * (1 - smooth(life - 4, life, t));
            if (c.solid() && hgt > .05F) {
                Vec3 base = o.add(Mth.cos(ang) * r, 0, Mth.sin(ang) * r);
                c.shard(base, base.add(Mth.cos(ang) * .25 * up, hgt, Mth.sin(ang) * .25 * up), .12F, 4, ang, i % 2 == 0 ? STONE : SLATE, 1);
            }
            crack(c, o, ang, r + .7F, a.seed() + i, .4F, .8F * f);
        }
        ripple(c, o.add(0, .06, 0), UP, torn ? 1.6F : 1.2F, Mth.clamp(t / 8F, 0, 1), TEAL, f);
    }

    /** Grasp from Below: fingers of stone rise round the ankles and close in, with a teal ring where they broke the ground. */
    private static void grasp(Canvas c, Active a, float t) {
        Vec3 o = a.origin();
        float w = Math.max(.6F, a.scale()), r = w * .55F + .25F, life = a.piece().ticks, f = fade(t, life, 0, 5);
        float rise = 1 - (float) Math.pow(1 - Mth.clamp(t / 3F, 0, 1), 3), grip = smooth(2, 6, t);
        float drop = smooth(life - 5, life, t);
        for (int i = 0; i < 5; i++) {
            float ang = i * Mth.TWO_PI / 5 + hash(a.seed(), i) * .3F;
            Vec3 base = o.add(Mth.cos(ang) * r, -.1, Mth.sin(ang) * r);
            Vec3 tip = base.add(-Mth.cos(ang) * r * .7 * grip, (.8 + .2 * hash(a.seed(), i + 4)) * rise * (1 - drop), -Mth.sin(ang) * r * .7 * grip);
            if (c.solid() && rise * (1 - drop) > .05F) c.shard(base, tip, .07F, 4, ang, i % 2 == 0 ? SLATE : STONE, 1);
            if (c.glow()) c.line(base.add(0, .1, 0), tip, .025F, TEAL, .5F * f, .1F * f);
        }
        ripple(c, o.add(0, .06, 0), UP, r + .5F, Mth.clamp(t / 6F, 0, 1), TEAL, f);
    }

    private static boolean minimal() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.options.particles().get() == ParticleStatus.MINIMAL;
    }

    private static float fade(float age, float life, float in, float out) {
        if (age < 0 || age >= life) return 0;
        return (in > 0 ? smooth(0, in, age) : 1) * (out > 0 ? 1 - smooth(life - out, life, age) : 1);
    }

    /**
     * Six Feet (the finisher, {@code phasing/SixFeet}): ripples run out from the phaser's feet as they go Ghost; stone
     * fingers rise round the foe's ankles and close; at the hammer the slabs fold over the spot like a closing hand and sink
     * flat, leaving a ring of crumbs; started Dense, the ground splits in a star. Bound to the phaser; origin the foe's
     * feet, target where the phaser stood; scale the foe's height (+ DENSE).
     */
    /** Where a hand is drawn from: the visible hand in first person, the body's hand otherwise. */
    private static Vec3 hand(Canvas c, Entity owner) {
        Minecraft mc = Minecraft.getInstance();
        if (owner == mc.player && mc.options.getCameraType() == CameraType.FIRST_PERSON) {
            Vec3 look = owner.getViewVector(c.partial);
            Vec3 right = look.cross(UP).normalize();
            return owner.getEyePosition(c.partial).add(look.scale(.6)).add(right.scale(.32)).add(0, -.3, 0);
        }
        if (owner instanceof LivingEntity body) {
            Vec3 look = body.getViewVector(c.partial);
            return DensityGeometry.hand(body, look, c.partial);
        }
        return owner.getPosition(c.partial).add(0, 1.2, 0);
    }

    /**
     * The alternate finishers ({@code phasing/DensityFinishers}). Rock Clap: two slabs of the ground stand up on either side
     * of the foe, then clap together and break into chunks that tumble out (the seed is the ground's block, so they are
     * its colour). Heavy Hand: plates of stone gathering on the forearm, then a crater (a star of cracks, a ring of tilted
     * slabs that fold in and seal it); its seed is the bender's entity id. Origin the foe's feet, target yours, scale its
     * height.
     */
    static void alternates(Canvas c, Active a, float t) {
        float h = Math.max(.6F, a.scale()), f = fade(t, a.piece().ticks, 1, 7);
        Vec3 foe = a.origin(), home = a.target();
        Vec3 toMe = home.subtract(foe).multiply(1, 0, 1);
        toMe = toMe.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : toMe.normalize();
        int seed = a.seed();
        if (a.piece() == Piece.FINISH_PHASE) {
            int rise = com.jayptucker.heroesevolved.phasing.DensityFinishers.CLAP_RISE, land = com.jayptucker.heroesevolved.phasing.DensityFinishers.CLAP_LAND;
            float[] rock = EarthArt.colour(c, seed);
            Vec3 side = new Vec3(-toMe.z, 0, toMe.x);
            float yaw = -(float) Math.atan2(toMe.x, toMe.z);
            float up = smooth(0, rise, t), clap = smooth(rise + 1, land, t), gone = smooth(land + 1, land + 9, t);
            float sh = h * 1.05F + .2F, gap = (1 - clap) * (.75F + h * .1F) + .24F;
            if (t < land + 2) for (int s2 = -1; s2 <= 1; s2 += 2) {
                // Each slab climbs out of the ground beside the foe, then swings in to meet the other (box sizes are halves).
                Vec3 at = foe.add(side.scale(s2 * gap)).add(0, sh * .5 * up - sh * .5 * (1 - up), 0);
                slab(c, at, .2F, sh * .5F, .55F, yaw, 0, s2 * .12F * (1 - clap), rock, 1);
            }
            for (int i = 0; i < 6; i++) crack(c, foe, i * Mth.TWO_PI / 6 + hash(seed, i), 1.4F * up, seed + i, 0, f * (1 - gone));
            if (t >= land) {
                float s = t - land;
                for (int i = 0; i < 12; i++) {
                    float ang = hash(seed, i + 3) * Mth.TWO_PI, lift = .15F + hash(seed, i + 40) * .25F;
                    Vec3 out = side.scale(Mth.cos(ang)).add(toMe.scale(Mth.sin(ang) * .5)).add(0, lift, 0);
                    Vec3 at = foe.add(0, h * (.25 + hash(seed, i + 70) * .7), 0).add(out.scale(s * .2)).add(0, -.02 * s * s, 0);
                    float size = .14F + hash(seed, i + 90) * .14F;
                    slab(c, at, size, size, size, ang + s * .4F, s * .5F, i, rock, 1 - gone);
                }
                if (c.glow()) c.glow(foe.add(0, h * .55, 0), .8F * (1 - smooth(land, land + 3, t)), PALE, f * .5F * (1 - smooth(land, land + 3, t)));
            }
            return;
        }
        Entity owner = c.level.getEntity(seed);
        // Heavy Hand.
        int brace = com.jayptucker.heroesevolved.phasing.DensityFinishers.HEAVY_BRACE, swing = com.jayptucker.heroesevolved.phasing.DensityFinishers.HEAVY_SWING;
        int land = com.jayptucker.heroesevolved.combat.FinisherBeats.HEAVY_LAND;
        if (t < land + 1 && owner != null) {
            // Stone gathering on the forearm.
            float grow = smooth(brace, swing - 2, t);
            Vec3 fist = hand(c, owner);
            boolean firstPerson = owner == Minecraft.getInstance().player && Minecraft.getInstance().options.getCameraType() == CameraType.FIRST_PERSON;
            if (!firstPerson) for (int i = 0; i < 4; i++) {
                float ang = i * Mth.HALF_PI + .3F;
                slab(c, fist.add(Mth.cos(ang) * .1, -.05 - i * .04, Mth.sin(ang) * .1), .07F, .1F, .05F, ang, .2F, 0, STONE, grow);
            }
            ripple(c, home.add(0, .04, 0), UP, 1F, grow, SLATE, f * .6F * grow);
        }
        if (t >= land) {
            float crack = smooth(land, land + 3, t), seal = smooth(land + 5, land + 11, t);
            for (int i = 0; i < 7; i++)
                crack(c, foe, i * Mth.TWO_PI / 7 + hash(seed, i + 9), 2.6F * crack, seed + i, .8F * (1 - seal), f);
            int slabs = 7;
            for (int i = 0; i < slabs; i++) {
                float ang = i * Mth.TWO_PI / slabs + hash(seed, i + 30) * .3F;
                float tilt = (1 - seal) * .9F * crack + seal * 1.4F;
                Vec3 at = foe.add(Mth.cos(ang) * .75 * (1 - .5 * seal), .08 - .2 * seal, Mth.sin(ang) * .75 * (1 - .5 * seal));
                slab(c, at, .34F, .08F, .26F, -ang, -tilt, 0, SLATE, 1 - smooth(land + 10, land + 14, t));
            }
            ripple(c, foe.add(0, .05, 0), UP, 2F, crack, PALE, f * (1 - smooth(land + 2, land + 8, t)));
            if (c.glow()) c.glow(foe.add(0, .4, 0), 1.2F * (1 - smooth(land, land + 3, t)), PALE, f * .6F * (1 - smooth(land, land + 3, t)));
        }
    }

    static void finisher(Canvas c, Active a, float t) {
        boolean dense = a.scale() >= com.jayptucker.heroesevolved.phasing.SixFeet.DENSE;
        float life = a.piece().ticks, f = fade(t, life, 1, 6);
        Vec3 foe = a.origin(), home = a.target();
        int grasp = com.jayptucker.heroesevolved.phasing.SixFeet.GRASP, land = com.jayptucker.heroesevolved.phasing.SixFeet.LAND;
        // Ghost: ripples at the phaser's feet.
        if (t < grasp + 6) for (int k = 0; k < 2; k++) {
            float q = smooth(k * 2, grasp + 4 + k * 2, t);
            if (q > 0 && q < 1) ripple(c, home.add(0, .04, 0), UP, .3F + 1.6F * q, q, TEAL, f * (1 - q));
        }
        // Stone fingers round the ankles, rising and closing in.
        float rise = smooth(grasp, grasp + 5, t), close = smooth(grasp + 3, land, t), sink = smooth(land, land + 6, t);
        if (rise > 0 && sink < 1) for (int i = 0; i < 6; i++) {
            float ang = i * net.minecraft.util.Mth.TWO_PI / 6 + hash(a.seed(), i) * .3F;
            double r = .55 - .2 * close;
            Vec3 base = foe.add(Math.cos(ang) * r, -.2 - .6 * sink, Math.sin(ang) * r);
            float yaw = -ang * net.minecraft.util.Mth.RAD_TO_DEG + 90, lean = 25 + 30 * close;
            slab(c, base.add(0, .35 * rise, 0), .16F, .7F, .12F, yaw, lean, 0, SLATE, rise * (1 - sink));
        }
        // The slabs fold over and sink flat once the hammer lands.
        if (t >= land - 1) {
            float fold = smooth(land - 1, land + 3, t);
            for (int i = 0; i < 4; i++) {
                float ang = i * net.minecraft.util.Mth.HALF_PI + .4F;
                Vec3 at = foe.add(Math.cos(ang) * .6 * (1 - fold * .7), .3 - .7 * sink, Math.sin(ang) * .6 * (1 - fold * .7));
                slab(c, at, .55F, .12F, .5F, -ang * net.minecraft.util.Mth.RAD_TO_DEG, 80 * (1 - fold), 0, SLATE, 1 - smooth(land + 6, land + 10, t));
            }
            if (dense) for (int i = 0; i < 6; i++) crack(c, foe.add(0, .03, 0), i * net.minecraft.util.Mth.TWO_PI / 6 + hash(a.seed(), i + 9), 2.4F * fold, a.seed() + i, .8F, f);
            ripple(c, foe.add(0, .04, 0), UP, .4F + 1.2F * fold, fold, PALE, f * (1 - smooth(land + 3, land + 9, t)));
        }
    }
}
