package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient.Active;
import com.jayptucker.heroesevolved.shapeshift.ShapeDecoy;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.*;
import static com.jayptucker.heroesevolved.client.effects.setpiece.ShapeArt.*;
import static com.jayptucker.heroesevolved.client.effects.setpiece.ShapeInstinctArt.*;
import static net.minecraft.util.Mth.*;

/**
 * Shapeshifting's tricks, in the same plates as everything else the power does:
 * <ul>
 *   <li>a decoy's seam: the shell split open down its back where you stepped out, plates peeled back either side;</li>
 *   <li>a decoy bursting: every plate of the shell thrown out at once, harder when it was struck;</li>
 *   <li>Reflection's mirror: a dish of plates that opens between you and your attacker, turns, and shatters;</li>
 *   <li>Nightmare's flare: a crown of thorn-like plates erupting round you, over a pool of shadow;</li>
 *   <li>a disguise bursting off: plates blown off the body from a dark crack that runs up it;</li>
 *   <li>a daze: three plates circling the head of a confused creature.</li>
 * </ul>
 */
final class ShapeTrickArt {
    private static final Vec3 UP = new Vec3(0, 1, 0);

    private ShapeTrickArt() {}

    private static Vec3 horizontal(float yaw) { return new Vec3(-sin(yaw), 0, cos(yaw)); }

    // ------------------------------------------------------------------
    // Decoy
    // ------------------------------------------------------------------

    /** The shell's back is split from the hips to the neck, edged with plates peeled open to their brass. */
    static void seam(Canvas c, Active a, float t, Entity body) {
        if (body == null) return;
        float life = body instanceof ShapeDecoy decoy ? Math.max(20, decoy.life() + t) : ShapeDecoy.LIFE;
        float alpha = fade(t, life, 4, 8);
        if (alpha <= .01F) return;
        float w = body.getBbWidth(), h = body.getBbHeight(), yaw = yawOf(body, c.partial);
        Vec3 feet = body.getPosition(c.partial), back = horizontal(yaw).scale(-1), side = new Vec3(cos(yaw), 0, sin(yaw));
        float open = easeOut(t / 5);
        int n = 9;
        Vec3[] crack = new Vec3[n];
        float[] al = new float[n];
        for (int i = 0; i < n; i++) {
            float fy = lerp(i / (n - 1F), .22F, .9F);
            float jag = (hash(a.seed(), i) - .5F) * .05F;
            crack[i] = feet.add(0, h * fy, 0).add(back.scale(w * .38 + .02)).add(side.scale(jag));
            al[i] = alpha * open * sin(PI * i / (n - 1F));
        }
        if (c.glow()) {
            layer(c, crack, .1F, SLATE, al, .35F);
            layer(c, crack, .035F, BRASS, al, .9F);
            layer(c, crack, .012F, HIGHLIGHT, al, 1);
        }
        if (!c.solid()) return;
        for (int i = 1; i < n - 1; i++) {
            for (int s = -1; s <= 1; s += 2) {
                Vec3 out = back.scale(.55).add(side.scale(s)).normalize();
                Vec3 hinge = crack[i].add(side.scale(s * .03));
                Spot spot = new Spot(i, s, i / (float) n, out, UP.cross(out).normalize(), hinge, .06F, .12F, 0,
                        hash(a.seed() + s, i), hash(a.seed() + 9, i));
                scale(c, spot, hinge, .4F + 1.5F * open * alpha, 0, 1, face(spot), 1);
            }
        }
    }

    /** Every plate of the shell flung out at once, with a flash if it was struck. */
    static void burst(Canvas c, Active a, float t) {
        Vec3 feet = a.origin();
        float h = (float) Math.max(.4, a.target().y - feet.y);
        boolean struck = a.scale() >= 1;
        int count = minimal() ? 22 : 54;
        for (int i = 0; i < count; i++) {
            float th = hash(a.seed(), i) * TWO_PI, fy = hash(a.seed() + 1, i);
            Vec3 out = new Vec3(cos(th), .35 + .5 * hash(a.seed() + 2, i), sin(th)).normalize();
            Vec3 start = feet.add(cos(th) * .3, h * fy, sin(th) * .3);
            float speed = (struck ? .3F : .14F) + .15F * hash(a.seed() + 3, i);
            Vec3 at = start.add(out.scale(speed * t * (1 - t / 44))).add(0, -.02 * t * t, 0);
            float size = 1 - smooth(10, 20, t);
            loose(c, at, out, t * (.4F + hash(a.seed() + 4, i)) * (i % 2 == 0 ? 1 : -1), size, .2F, a.seed(), i);
        }
        if (!c.glow()) return;
        float flash = (struck ? 1 : .45F) * (1 - smooth(0, 6, t));
        Vec3 middle = feet.add(0, h * .55, 0);
        c.glow(middle, (.9F + h * .5F) * flash, BRASS, .6F * flash);
        c.glow(middle, .3F * flash, HIGHLIGHT, flash);
        seamRing(c, feet.add(0, .05, 0), UP, .4F + 1.6F * easeOut(t / 10), 1 - smooth(6, 16, t));
    }

    // ------------------------------------------------------------------
    // Reflection
    // ------------------------------------------------------------------

    /**
     * A dish of plates opens halfway between you and your attacker, its brass side toward them, and turns once like a
     * mirror caught in the light; then it shatters, the pieces flying both ways.
     */
    static void mirror(Canvas c, Active a, float t, Entity body) {
        Vec3 from = a.origin(), to = a.target();
        Vec3 dir = to.subtract(from);
        if (dir.lengthSqr() < 1.0E-4) return;
        dir = dir.normalize();
        Vec3 centre = from.lerp(to, .5);
        float radius = clamp(a.scale() * .42F, .55F, 1.4F);
        float open = easeOut(t / 4), shatter = smooth(11, 13, t), gone = smooth(12, 20, t);
        if (shatter < 1) {
            for (int ring = 1; ring <= 3; ring++) {
                float r = radius * ring / 3F * open;
                int count = 6 + ring * 5;
                int rr = ring;
                around(centre, dir, r, count, t * .15F * (ring % 2 == 0 ? 1 : -1), radius / 3.2F, a.seed() + ring, s -> {
                    if (c.solid()) scale(c, s, s.hinge(), HALF_PI - .25F + .1F * rr, 0, (1 - shatter) * open, face(s), 1);
                });
            }
            if (c.glow()) {
                seamRing(c, centre, dir, radius * open * 1.05F, 1 - shatter);
                // A glint sweeping across the face, like light turning on glass.
                Vec3[] b = basis(dir);
                float sweep = (t / 10F) % 1 * 2 - 1;
                Vec3 glint = centre.add(b[0].scale(sweep * radius * .8)).add(b[1].scale(sweep * radius * .3));
                c.glow(glint, .35F * open, HIGHLIGHT, .7F * open * (1 - shatter));
            }
        }
        if (shatter > 0) {
            int n = minimal() ? 14 : 30;
            Vec3[] b = basis(dir);
            for (int i = 0; i < n; i++) {
                float th = hash(a.seed(), i) * TWO_PI, r = radius * sqrt(hash(a.seed() + 1, i));
                Vec3 at = centre.add(b[0].scale(cos(th) * r)).add(b[1].scale(sin(th) * r));
                float way = i % 2 == 0 ? 1 : -1, age = t - 12;
                Vec3 out = dir.scale(way).add(at.subtract(centre).scale(.6)).normalize();
                loose(c, at.add(out.scale(.18 * age)).add(0, -.015 * age * age, 0), out, age * .6F * way, 1 - gone, .16F, a.seed(), i);
            }
        }
        streak(c, to, from, .03F, 0, SHEEN, (1 - smooth(0, 8, t)) * .8F);
    }

    // ------------------------------------------------------------------
    // Nightmare
    // ------------------------------------------------------------------

    /**
     * A pool of shadow spreads under you and a crown of thorn-like plates erupts round you, then sinks. A foe also sees
     * a warden rear up in your place (drawn by {@code MorphClient}), so for them the crown stays low.
     */
    static void nightmare(Canvas c, Active a, float t, Entity body) {
        if (body == null) return;
        boolean foe = a.scale() >= 1.5F;
        Vec3 feet = body.getPosition(c.partial);
        float w = Math.max(.6F, body.getBbWidth()), rise = easeOut(t / 5) * (1 - smooth(24, 34, t));
        float alpha = fade(t, 36, 2, 10);
        if (c.soft()) {
            for (int k = 0; k < 3; k++)
                c.ring(feet.add(0, .04 + k * .01, 0), UP, (w + 1.4F) * easeOut(t / 7) * (1 - k * .25F), .7F, 0, TWO_PI, SHADOW, .35F * alpha);
        }
        float tall = foe ? .9F : 1.8F + body.getBbHeight() * .4F;
        int rows = foe ? 2 : 4;
        for (int r = 0; r < rows; r++) {
            float rowRise = rise * (1 - r * .18F);
            Vec3 centre = feet.add(0, tall * rowRise * (r + 1) / rows, 0);
            float radius = w * .7F + .25F + r * .12F;
            around(centre, UP.scale(-1), radius, 12 + r * 2, r * .26F + t * .02F, .34F - r * .04F, a.seed() + r, s -> {
                if (c.solid()) scale(c, s, s.hinge(), .25F + .3F * rowRise, 0, rowRise, face(s), 1);
            });
            seamRing(c, centre, UP, radius * 1.05F, rowRise * alpha * .7F);
        }
        if (c.glow() && t < 6) c.glow(feet.add(0, body.getBbHeight() * .5, 0), 1.5F * (1 - t / 6), SHEEN, .4F * (1 - t / 6));
    }

    // ------------------------------------------------------------------
    // Exposed
    // ------------------------------------------------------------------

    /** A dark crack runs up the body and every plate of the disguise is blown off it. */
    static void exposed(Canvas c, Active a, float t, Entity body) {
        Vec3 feet = a.origin();
        float h = (float) Math.max(.4, a.target().y - feet.y), w = Math.max(.3F, a.scale());
        float yaw = body == null ? 0 : yawOf(body, c.partial);
        boolean inside = body != null && insideOf(body);
        hull(feet, yaw, w, h, a.seed(), s -> {
            float delay = s.fy() * 3, age = t - delay;
            if (age < 0 || age > 18) return;
            float fly = easeOut(age / 10);
            Vec3 hinge = s.hinge().add(s.out().scale(fly * (1.2 + s.hash()))).add(0, fly * .4 - .02 * age * age, 0);
            if (inside && hinge.distanceTo(c.camera()) < 1.2) return;
            if (c.solid()) scale(c, s, hinge, s.lean() + 2.4F * fly, age * .5F * (s.hash() > .5F ? 1 : -1), 1 - smooth(8, 18, age), face(s), 1);
        });
        if (inside) return;
        int n = 10;
        Vec3[] crack = new Vec3[n];
        float[] al = new float[n];
        Vec3 front = horizontal(yaw);
        float up = easeOut(t / 4), fadeOut = 1 - smooth(8, 18, t);
        for (int i = 0; i < n; i++) {
            float fy = i / (n - 1F) * up;
            crack[i] = feet.add(0, h * fy, 0).add(front.scale(w * .4)).add(new Vec3(cos(yaw), 0, sin(yaw)).scale((hash(a.seed(), i) - .5) * .12));
            al[i] = fadeOut;
        }
        if (c.soft()) layer(c, crack, .09F, SHADOW, al, .9F);
        if (c.glow()) layer(c, crack, .025F, BRASS, al, 1 - smooth(0, 8, t));
    }

    // ------------------------------------------------------------------
    // Plate Mail
    // ------------------------------------------------------------------

    /**
     * The plates lie flat and overlapping over the torso, hips to shoulders, like scale armour, with a brass glint running
     * up them; the head and legs stay bare, so the wearer is still who they were. Each
     * copy fades in and out over two ticks so the re-sent pieces hand over without a seam. Hidden from your own
     * first-person view, where it would close round the camera.
     */
    static void plates(Canvas c, Active a, float t, Entity body) {
        if (insideOf(body)) return;
        float alpha = fade(t, 12, 2, 2);
        if (alpha <= .01F) return;
        float w = body.getBbWidth(), h = body.getBbHeight();
        Vec3 feet = body.getPosition(c.partial);
        float yaw = yawOf(body, c.partial);
        float glint = ((body.level().getGameTime() + c.partial) % 30) / 30F;
        hull(feet, yaw, w + .08F, h + .04F, body.getId(), s -> {
            if (s.fy() < .34F || s.fy() > .8F) return;
            float near = 1 - Math.min(1, Math.abs(s.fy() - (.34F + .46F * glint)) * 10);
            if (c.solid()) scale(c, s, s.hinge(), s.lean() * .4F - .05F + .25F * near, 0, alpha, face(s), 1);
            else if (c.glow() && near > .5F && (s.col() & 1) == 0) c.glow(s.hinge().add(s.out().scale(.04)), .07F, BRASS, .45F * near * alpha);
        });
    }

    // ------------------------------------------------------------------
    // Daze
    // ------------------------------------------------------------------

    /** Three plates circling a confused creature's head, bobbing out of step. */
    static void daze(Canvas c, Active a, float t, Entity body) {
        float life = Math.max(10, a.scale());
        float alpha = fade(t, life, 3, 6);
        if (alpha <= .01F) return;
        Vec3 top = body.getPosition(c.partial).add(0, body.getBbHeight() + .3, 0);
        float r = Math.max(.3F, body.getBbWidth() * .5F);
        for (int k = 0; k < 3; k++) {
            float th = t * .35F + k * TWO_PI / 3;
            Vec3 out = new Vec3(cos(th), 0, sin(th));
            Vec3 at = top.add(out.scale(r)).add(0, sin(t * .5F + k * 2) * .06, 0);
            loose(c, at, out, th, alpha, .15F, a.seed(), k);
            if (c.glow() && detail()) c.glow(at, .07F, BRASS, .4F * alpha);
        }
    }
}
