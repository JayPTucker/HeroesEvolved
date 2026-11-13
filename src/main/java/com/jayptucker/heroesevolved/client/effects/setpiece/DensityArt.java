package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient.Active;
import com.jayptucker.heroesevolved.phasing.DensityGeometry;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.*;

/**
 * Density's skill tree, in the power's grammar: surfaces that ripple like water where something passes into them, and
 * stone. Every piece is built from four primitives:
 * <ul>
 *   <li>{@link #ripple}: rings opening across a surface, each wider and fainter, wobbling so they never look machined;</li>
 *   <li>{@link #oval}: a standing, body-sized ripple (a wall left open, a body passed through, a shell left behind);</li>
 *   <li>{@link #slab}: a block of stone, drawn solid and lit by the sun;</li>
 *   <li>{@link #crack}: a jagged dark line across the ground.</li>
 * </ul>
 * Ghost is teal and pale; Dense is slate, stone and dark cracks with pale light in them. The palette matches the core
 * kit's {@code DensityEffects}.
 */
final class DensityArt {
    private static final float[] TEAL = rgb(0x7FE8D8), PALE = rgb(0xD8F4EE), DEEP = rgb(0x2E8F86), SLATE = rgb(0x6E7F86),
            STONE = rgb(0x9A9284), DARK = rgb(0x3E4A4E);
    /** Drawn with normal blending behind the light, so ripples hold against a bright sky. */
    private static final float[] INK = rgb(0x163331);
    private static final Set<Piece> AMBUSHED = Set.of(Piece.DENSITY_AMBUSH);

    private DensityArt() {}

    static void draw(Canvas c, Active a, float t) {
        switch (a.piece()) {
            case DENSITY_PASS -> pass(c, a, t);
            case DENSITY_PRIMED -> primed(c, a, t);
            case DENSITY_AMBUSH -> ambush(c, a, t);
            case DENSITY_DOOR -> door(c, a, t);
            case DENSITY_HARDEN -> harden(c, a, t);
            case DENSITY_REBOUND -> rebound(c, a, t);
            case DENSITY_PILE -> pile(c, a, t);
            case DENSITY_SINK_MARK -> mark(c, a, t);
            case DENSITY_SINKHOLE -> sinkhole(c, a, t);
            case DENSITY_ENTOMB -> entomb(c, a, t);
            case DENSITY_UPROOT -> uproot(c, a, t);
            case DENSITY_FADE -> fade(c, a, t);
            case DENSITY_GRASP -> grasp(c, a, t);
            case DENSITY_DROP -> drop(c, a, t);
            case DENSITY_SLIP -> slip(c, a, t);
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

    /**
     * A standing, body-sized ripple across {@code normal}: {@code rings} wavering ovals {@code rx} wide and {@code ry}
     * tall, drifting outward by {@code phase} (0 to 1, wrapping).
     */
    private static void oval(Canvas c, Vec3 at, Vec3 normal, float rx, float ry, int rings, float phase, int seed, float alpha) {
        if (alpha <= .003F) return;
        Vec3 n = normal.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : normal.normalize();
        Vec3 side = Math.abs(n.y) > .9 ? new Vec3(1, 0, 0) : n.cross(UP).normalize();
        Vec3 up = side.cross(n).normalize();
        if (up.y < 0) up = up.reverse();
        int steps = minimal() ? 20 : 36;
        for (int i = 0; i < rings; i++) {
            float k = ((i + phase) / rings) % 1F, s = .25F + .75F * k, ai = alpha * Mth.sin(k * Mth.PI);
            Vec3[] pts = new Vec3[steps + 1];
            for (int j = 0; j <= steps; j++) {
                float ang = j * Mth.TWO_PI / steps, wob = 1 + Mth.sin(ang * 5 + i * 1.7F + seed) * .05F;
                pts[j] = at.add(side.scale(Mth.cos(ang) * rx * s * wob)).add(up.scale(Mth.sin(ang) * ry * s * wob));
            }
            if (c.soft()) c.ribbon(pts, .1F, INK, .22F * ai, .22F * ai);
            if (!c.glow()) continue;
            c.ribbon(pts, .12F, DEEP, .3F * ai, .3F * ai);
            c.ribbon(pts, .045F, TEAL, .8F * ai, .8F * ai);
            c.ribbon(pts, .016F, PALE, .8F * ai, .8F * ai);
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
    // Infiltrator
    // ------------------------------------------------------------------

    /**
     * Pass Through (and an enemy Fade Through drifts through): the body's surface ripples like water where you went
     * through it, a pale wake runs along your line, and a ghost of you is drawn out of it.
     */
    private static void pass(Canvas c, Active a, float t) {
        Vec3 o = a.origin(), dir = a.target().subtract(o).multiply(1, 0, 1);
        dir = dir.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : dir.normalize();
        float h = Math.max(.8F, a.scale()), life = a.piece().ticks, f = fade(t, life, 0, 6);
        float open = 1 - (float) Math.pow(1 - Mth.clamp(t / 5F, 0, 1), 3);
        Vec3 chest = o.add(0, h * .55, 0);
        oval(c, chest, dir, .5F, h * .5F, 3, open * .9F, a.seed(), f);
        ripple(c, chest.add(dir.scale(.3)), dir, .7F, open, TEAL, .8F * f);
        // The wake: from two blocks before the body to where you came out.
        Vec3 from = chest.subtract(dir.scale(2)), to = a.target().add(0, h * .55, 0);
        if (c.soft()) c.line(from, to, .2F, INK, 0, .18F * f);
        if (c.glow()) {
            c.line(from, to, .35F, DEEP, 0, .3F * f);
            c.line(from, to, .1F, TEAL, 0, .7F * f);
            c.line(from, to, .03F, PALE, 0, .9F * f);
            c.glow(chest, .5F + .4F * (1 - open), PALE, .7F * f * (1 - open * .6F));
        }
    }

    /** Where a hand is drawn from: the visible hand in first person, the body's hand otherwise. */
    private static Vec3 hand(Canvas c, Entity owner) {
        Minecraft mc = Minecraft.getInstance();
        if (!(owner instanceof LivingEntity body)) return owner.position().add(0, 1.2, 0);
        Vec3 look = owner.getViewVector(c.partial);
        if (owner != mc.player || mc.options.getCameraType() != CameraType.FIRST_PERSON)
            return DensityGeometry.hand(body, look, c.partial);
        Vec3 right = Vec3.directionFromRotation(0, owner.getViewYRot(c.partial) + 90);
        Vec3 cameraUp = right.cross(look).normalize();
        boolean rightHand = !(owner instanceof Player p) || p.getMainArm() == HumanoidArm.RIGHT;
        return c.camera().add(look.scale(.85)).add(right.scale(rightHand ? .39 : -.39)).subtract(cameraUp.scale(.43));
    }

    /** Ambush: a hand primed by a wall. Teal water clings to it in turning rings until it strikes. */
    private static void primed(Canvas c, Active a, float t) {
        if (SetPieceClient.superseded(a, AMBUSHED, a.start() + (long) t)) return;
        Entity owner = a.bound(c);
        if (owner == null || !c.glow()) return;
        float f = fade(t, a.piece().ticks, 3, 10), clock = t + c.partial;
        Vec3 at = hand(c, owner), look = owner.getViewVector(c.partial);
        float pulse = .75F + .25F * Mth.sin(clock * .5F);
        c.glow(at, .22F * pulse, TEAL, .6F * f);
        c.glow(at, .08F, PALE, .8F * f);
        for (int i = 0; i < 2; i++) {
            float spin = clock * (.25F + i * .1F) + i * Mth.PI;
            Vec3 axis = look.add(Mth.cos(spin) * .6, Mth.sin(spin) * .6, 0).normalize();
            c.ring(at, axis, .14F + i * .04F, .018F, 0, Mth.TWO_PI * .7F, i == 0 ? TEAL : PALE, .65F * f);
        }
    }

    /** Ambush's strike: the arm drives into the body in two ticks and the body's surface ripples round it. */
    private static void ambush(Canvas c, Active a, float t) {
        Vec3 from = a.origin(), to = a.target(), dir = to.subtract(from);
        if (dir.lengthSqr() < 1.0E-4) dir = new Vec3(0, 0, 1);
        float life = a.piece().ticks, f = fade(t, life, 0, 5), reach = Mth.clamp(t / 2F, 0, 1);
        Vec3 tip = from.add(dir.scale(reach));
        if (c.soft()) c.line(from, tip, .16F, INK, .1F * f, .25F * f);
        if (c.glow()) {
            c.line(from, tip, .3F, DEEP, .15F * f, .35F * f);
            c.line(from, tip, .1F, TEAL, .4F * f, .85F * f);
            c.line(from, tip, .03F, PALE, .5F * f, f);
        }
        if (reach >= 1) {
            float k = Mth.clamp((t - 2) / 6F, 0, 1);
            ripple(c, to.subtract(dir.normalize().scale(.3)), dir, .9F, k, TEAL, f);
            if (c.glow()) c.glow(to, .6F * (1 - k) + .15F, PALE, .8F * f);
        }
    }

    /** Ghost Network: a wall you passed stays open, its face slowly rippling outward like a pond. Brighter for Phase Carry. */
    private static void door(Canvas c, Active a, float t) {
        boolean carried = a.scale() >= DensityGeometry.CARRIED;
        Vec3 at = a.origin(), normal = a.target().subtract(at);
        float f = fade(t, a.piece().ticks, 2, 2) * (carried ? 1.3F : .85F);
        float phase = ((c.level.getGameTime() % 40) + c.partial) / 40F;
        oval(c, at, normal, carried ? .75F : .6F, carried ? 1.2F : 1.05F, carried ? 4 : 3, phase, a.seed(), Math.min(1, f));
        if (c.glow()) c.glow(at, .4F, TEAL, .25F * f);
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
    // Escape Artist
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

    /**
     * Fade Through (and friends Phase Carry has loosened): the body thins to a few pale strands, and fainter shells of it
     * trail behind it, each a step further back along its travel.
     */
    private static void fade(Canvas c, Active a, float t) {
        Entity e = a.bound(c);
        if (e == null) return;
        boolean friend = a.scale() < 1;
        Vec3 o = a.at(c);
        float h = e.getBbHeight(), f = fade(t, a.piece().ticks, 3, 8) * (friend ? .6F : 1), clock = t + c.partial;
        if (firstPerson(e)) f *= .35F;
        Vec3 travel = new Vec3(e.getX() - e.xo, 0, e.getZ() - e.zo);
        Vec3 back = travel.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0, e.getYRot()).reverse() : travel.normalize().reverse();
        for (int s = 0; s < 3; s++) {
            Vec3 at = o.add(back.scale(s * .45)).add(0, h * .5, 0);
            float as = f * (1 - s * .3F) * (.7F + .3F * Mth.sin(clock * .7F + s));
            oval(c, at, back, .38F, h * .52F, 1, .75F, a.seed() + s, as * .7F);
        }
        if (!c.glow()) return;
        int strands = minimal() ? 3 : 6;
        for (int i = 0; i < strands; i++) {
            float ang = i * Mth.TWO_PI / strands + clock * .05F;
            Vec3[] pts = new Vec3[9];
            for (int j = 0; j < pts.length; j++) {
                float y = j / 8F * h, sway = Mth.sin(clock * .3F + j * .9F + i) * .05F, rr = .36F + sway - (y > h * .76F ? .08F : 0);
                pts[j] = o.add(Mth.cos(ang) * rr, y, Mth.sin(ang) * rr);
            }
            c.ribbon(pts, .03F, i % 2 == 0 ? TEAL : PALE, .15F * f, .55F * f);
        }
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

    /** Phase Fall: the floor ripples where you sink into it, and again where you come out, with a pale streak between. */
    private static void drop(Canvas c, Active a, float t) {
        Vec3 from = a.origin(), to = a.target();
        boolean down = to.y < from.y;
        float life = a.piece().ticks, f = fade(t, life, 0, 6), k = Mth.clamp(t / 7F, 0, 1);
        ripple(c, from.add(0, .06, 0), UP, 1.4F, k, TEAL, f);
        // Coming out of a ceiling (down) or up through a floor (rise).
        Vec3 out = down ? to.add(0, 2.05, 0) : to.add(0, .06, 0);
        ripple(c, out, UP, 1.2F, Mth.clamp((t - 1) / 7F, 0, 1), TEAL, f);
        if (c.glow()) {
            Vec3 a0 = from.add(0, 1, 0), b0 = to.add(0, 1, 0);
            float s = Mth.clamp(t / 2F, 0, 1);
            c.line(a0, a0.lerp(b0, s), .35F, DEEP, .25F * f, .1F * f);
            c.line(a0, a0.lerp(b0, s), .06F, PALE, .6F * f, .2F * f);
        }
    }

    /** Untouchable: the blow goes through a pale shell of you left where you stood, which shivers and breaks into rings. */
    private static void slip(Canvas c, Active a, float t) {
        Vec3 o = a.origin();
        float life = a.piece().ticks, f = fade(t, life, 0, 6), k = Mth.clamp(t / 8F, 0, 1);
        Entity e = a.bound(c);
        Vec3 face = e == null ? new Vec3(0, 0, 1) : Vec3.directionFromRotation(0, e.getYRot());
        float shiver = Mth.sin((t + c.partial) * 3.1F) * .05F * (1 - k);
        oval(c, o.add(face.scale(-.15 - k * .3)).add(shiver, .95, 0), face, .4F + k * .3F, .95F + k * .2F, 2, .6F + k * .3F, a.seed(),
                f * (firstPerson(e) ? .3F : 1));
        ripple(c, o.add(0, .06, 0), UP, 1.4F, k, PALE, .8F * f);
        if (c.glow()) {
            for (int i = 0; i < 8; i++) {
                float ang = i * Mth.TWO_PI / 8 + hash(a.seed(), i);
                Vec3 p = o.add(0, .4 + 1.1 * hash(a.seed(), i + 8), 0);
                Vec3 out = new Vec3(Mth.cos(ang), (hash(a.seed(), i + 16) - .5F) * .6F, Mth.sin(ang));
                c.line(p.add(out.scale(.3 + k * .8)), p.add(out.scale(.5 + k * 1.2)), .025F, TEAL, .7F * f * (1 - k), 0);
            }
        }
    }

    private static boolean minimal() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.options.particles().get() == ParticleStatus.MINIMAL;
    }

    private static float fade(float age, float life, float in, float out) {
        if (age < 0 || age >= life) return 0;
        return (in > 0 ? smooth(0, in, age) : 1) * (out > 0 ? 1 - smooth(life - out, life, age) : 1);
    }
}
