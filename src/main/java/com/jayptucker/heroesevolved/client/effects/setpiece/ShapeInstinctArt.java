package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient.Active;
import com.jayptucker.heroesevolved.shapeshift.InstinctMove;
import com.jayptucker.heroesevolved.shapeshift.ShapeshiftGeometry;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.*;
import static com.jayptucker.heroesevolved.client.effects.setpiece.ShapeArt.*;
import static net.minecraft.util.Mth.*;

/**
 * Instinct, drawn in Shapeshifting's one language: scales that flip from slate to brass. Lunge is a sleeve of plates
 * that grows out of the shoulder to brass talons. Every other form's move starts with a collar of plates flaring open
 * round the body, then adds its own accent:
 * <ul>
 *   <li>claws raking across the target;</li>
 *   <li>a silk strand and a web;</li>
 *   <li>a burst of plates thrown out by a creeper's blast;</li>
 *   <li>a body shed at one end of a blink and gathered at the other;</li>
 *   <li>plates strewn behind a pounce or charge;</li>
 *   <li>a kicked arc of plates;</li>
 *   <li>a spiral thrown up under a flung creature;</li>
 *   <li>rings of plates riding a warden's boom down its beam, and a spiral running along a guardian's gaze;</li>
 *   <li>bands of plates clamped round a creature in a zombie's grab.</li>
 * </ul>
 * The vanilla effects of the moves (fireballs, fangs, sonic-boom particles) are left as they are, as seasoning.
 */
final class ShapeInstinctArt {
    private ShapeInstinctArt() {}

    // ------------------------------------------------------------------
    // Shared shapes
    // ------------------------------------------------------------------

    /** Two directions square to {@code axis} and to each other. */
    static Vec3[] basis(Vec3 axis) {
        Vec3 helper = Math.abs(axis.y) > .9 ? new Vec3(1, 0, 0) : UP;
        Vec3 a = axis.cross(helper).normalize();
        return new Vec3[] {a, axis.cross(a).normalize()};
    }

    /**
     * A ring of {@code count} plates round {@code centre}, facing out from {@code axis} and hanging toward {@code -axis}:
     * the sleeve of an arm, a collar, a band round a waist.
     */
    static void around(Vec3 centre, Vec3 axis, float radius, int count, float phase, float length, int seed, Visitor visitor) {
        Vec3[] b = basis(axis);
        float halfWidth = TWO_PI * radius / count * .62F;
        for (int i = 0; i < count; i++) {
            float th = phase + i * TWO_PI / count;
            Vec3 out = b[0].scale(cos(th)).add(b[1].scale(sin(th)));
            Vec3 along = axis.cross(out).normalize();
            visitor.plate(new Spot(0, i, 0, out, along, centre.add(out.scale(radius)), halfWidth, length, .25F,
                    hash(seed, i), hash(seed + 7, i * 3), axis));
        }
    }

    /** A three-layer line from {@code from} to {@code to}, sagging by {@code sag}, faded where it passes the camera. */
    static void streak(Canvas c, Vec3 from, Vec3 to, float width, float sag, float[] body, float alpha) {
        if (!c.glow() || alpha <= .01F) return;
        int n = 12;
        Vec3[] points = new Vec3[n];
        float[] a = new float[n];
        Vec3 camera = c.camera();
        for (int i = 0; i < n; i++) {
            float u = i / (n - 1F);
            points[i] = from.lerp(to, u).add(0, -sin(PI * u) * sag, 0);
            a[i] = alpha * clamp((float) points[i].distanceTo(camera) / 1.4F - .25F, 0, 1);
        }
        layer(c, points, width * 3, SLATE, a, .32F);
        layer(c, points, width, body, a, .85F);
        layer(c, points, width * .32F, HIGHLIGHT, a, 1);
    }

    static void layer(Canvas c, Vec3[] points, float width, float[] colour, float[] alpha, float k) {
        float[] w = new float[points.length], a = new float[points.length];
        for (int i = 0; i < points.length; i++) { w[i] = width; a[i] = alpha[i] * k; }
        c.ribbon(points, w, colour, a);
    }

    /** A three-layer brass ring: halo, body, core. */
    static void seamRing(Canvas c, Vec3 at, Vec3 axis, float radius, float alpha) {
        if (!c.glow() || alpha <= .01F) return;
        c.ring(at, axis, radius, .12F, 0, TWO_PI, SLATE, .3F * alpha);
        c.ring(at, axis, radius, .04F, 0, TWO_PI, BRASS, .85F * alpha);
        c.ring(at, axis, radius, .013F, 0, TWO_PI, HIGHLIGHT, alpha);
    }

    /**
     * A collar of plates flaring open round the body and closing again: the shapeshifter's body acting. Skipped for
     * your own body in first person, where it would sit round the camera.
     */
    private static void flare(Canvas c, Entity body, Vec3 at, float t) {
        if (body == null || insideOf(body)) return;
        float open = sin(PI * clamp(t / 9, 0, 1));
        if (open <= .01F) return;
        float radius = body.getBbWidth() * .55F + .18F;
        around(at, UP, radius, 12, t * .05F, .2F, body.getId(), s -> {
            float angle = .25F + 1.9F * open;
            if (c.solid()) scale(c, s, s.hinge(), angle, 0, .85F, face(s), 1);
            else if (c.glow() && detail() && open > .5F && (s.col() & 1) == 0)
                c.glow(s.hinge().add(s.out().scale(.15)), .08F, BRASS, .4F * open);
        });
    }

    /** A loose plate tumbling free: placed at {@code at}, turned by {@code spin}, shrunk to {@code size}. */
    static void loose(Canvas c, Vec3 at, Vec3 out, float spin, float size, float length, int seed, int i) {
        if (!c.solid() || size <= .02F) return;
        Vec3 axis = Math.abs(out.y) > .9 ? new Vec3(1, 0, 0) : UP;
        Vec3 along = axis.cross(out).normalize();
        Spot s = new Spot(0, i, 0, out, along, at, length * .55F, length, 0, hash(seed, i), hash(seed + 3, i), axis);
        scale(c, s, at, .4F, spin, size, face(s), 1);
    }

    // ------------------------------------------------------------------
    // Lunge
    // ------------------------------------------------------------------

    /**
     * Your arm lengthens: a sleeve of plates grows out from the shoulder in three ticks, each ring flaring brass at the
     * growing tip, to three brass talons that close on whatever they hit. It holds a moment and draws back in.
     */
    static void lunge(Canvas c, Active a, float t, Entity body) {
        // In your own first-person view the sleeve leaves the hand you see at the bottom corner, not the shoulder
        // behind the camera, so it reaches in toward the crosshair instead of walling it in.
        boolean first = body != null && insideOf(body);
        Vec3 from = first && body instanceof Player self ? viewHand(c, self)
                : body instanceof Player p ? ShapeshiftGeometry.shoulder(p, c.partial) : a.origin();
        Vec3 to = a.target();
        Vec3 dir = to.subtract(from);
        double full = dir.length();
        if (full < .2) return;
        dir = dir.normalize();
        float reach = easeOut(t / 3) * (1 - smooth(5, 10, t));
        double length = full * reach;
        if (length < .05) return;
        boolean hit = a.scale() >= 1;
        Vec3 camera = c.camera();
        Vec3 axis = dir.scale(-1);
        double clear = first ? .35 : .7;
        float thin = first ? .6F : 1;
        int rings = Math.max(2, (int) (length / .13));
        for (int i = 0; i <= rings; i++) {
            float u = i / (float) rings;
            Vec3 centre = from.add(dir.scale(length * u));
            if (centre.distanceTo(camera) < clear) continue;
            float tipDistance = (float) ((1 - u) * length);
            float flared = 1 - smooth(0, .4F, tipDistance);
            float radius = lerp(u, .17F, .1F) * thin;
            int ring = i;
            around(centre, axis, radius, 6, i * .52F, .22F * thin, a.seed() + i, s -> {
                if (c.solid()) scale(c, s, s.hinge(), .3F + 1.3F * flared, 0, 1, face(s), 1);
                else if (c.glow() && flared > .5F && detail()) c.glow(s.hinge(), .06F, BRASS, .35F * flared);
            });
        }
        Vec3 tip = from.add(dir.scale(length));
        if (tip.distanceTo(camera) >= clear) {
            if (!first) seamRing(c, tip, dir, .14F, 1 - smooth(4, 9, t));
            // Three talons, open as the arm reaches and clenched on a hit.
            if (c.solid()) {
                Vec3[] b = basis(dir);
                float spread = hit ? lerp(smooth(2, 4, t), .9F, .2F) : .9F;
                for (int k = 0; k < 3; k++) {
                    float th = k * TWO_PI / 3 + .4F;
                    Vec3 side = b[0].scale(cos(th)).add(b[1].scale(sin(th)));
                    c.shard(tip.add(side.scale(.05 * thin)), tip.add(dir.scale(.3 * thin)).add(side.scale(.12 * spread * thin)), .035F * thin, 4, th, BRASS, 1);
                }
            }
        }
        if (hit && c.glow() && !first) {
            float k = sin(PI * clamp((t - 2.5F) / 5, 0, 1));
            c.glow(to, .7F * k, BRASS, .55F * k);
            c.glow(to, .22F * k, HIGHLIGHT, k);
            seamRing(c, to, dir, .25F + .8F * clamp((t - 2.5F) / 5, 0, 1), k);
        }
    }

    /** Where your own empty hand sits in first person: low and to the side of the camera, a little ahead of it. */
    private static Vec3 viewHand(Canvas c, Player self) {
        Vec3 look = self.getViewVector(c.partial);
        Vec3 right = Vec3.directionFromRotation(0, self.getViewYRot(c.partial) + 90);
        Vec3 up = right.cross(look).normalize();
        return c.camera().add(look.scale(.5)).add(right.scale(self.getMainArm() == HumanoidArm.RIGHT ? .39 : -.39)).subtract(up.scale(.4));
    }

    // ------------------------------------------------------------------
    // Every other form's move
    // ------------------------------------------------------------------

    static void instinct(Canvas c, Active a, float t, Entity body) {
        InstinctMove move = InstinctMove.byId(Math.round(a.scale()));
        if (move == null) return;
        Vec3 chest = body == null ? a.origin() : body.getPosition(c.partial).add(0, body.getBbHeight() * .6, 0);
        switch (move) {
            case DETONATE -> detonate(c, a, t);
            case BLINK -> blink(c, a, t, body);
            case POUNCE, CHARGE -> trail(c, a, t, body, move == InstinctMove.CHARGE);
            case BUCK -> { flare(c, body, chest, t); buck(c, a, t); }
            case FLING -> { flare(c, body, chest, t); fling(c, a, t); }
            case WEB -> { flare(c, body, chest, t); web(c, a, t); }
            case STRIKE -> { flare(c, body, chest, t); claws(c, a, t); }
            case GRAB -> flare(c, body, chest, t);
            case FAMILIAR -> familiar(c, a, t);
            case SLAM -> slam(c, a, t, body);
            default -> {
                flare(c, body, chest, t);
                streak(c, a.origin(), a.target(), .05F, 0, BRASS, 1 - smooth(1, 6, t));
            }
        }
    }

    /** Three claw marks raked across the target, drawn in a stroke and fading. */
    private static void claws(Canvas c, Active a, float t) {
        if (!c.glow()) return;
        Vec3 dir = a.target().subtract(a.origin());
        if (dir.lengthSqr() < 1.0E-4) return;
        dir = dir.normalize();
        Vec3 side = dir.cross(UP).normalize(), up = side.cross(dir).normalize();
        float head = easeOut(t / 3), tail = smooth(2, 8, t), alpha = 1 - smooth(6, 12, t);
        for (int k = -1; k <= 1; k++) {
            int n = 9;
            Vec3[] points = new Vec3[n];
            float[] al = new float[n];
            for (int i = 0; i < n; i++) {
                float u = lerp(i / (n - 1F), tail, head);
                points[i] = a.target().add(side.scale(-.6 + 1.2 * u)).add(up.scale(.5 - u + k * .2)).add(dir.scale(-.15 * sin(PI * u)));
                al[i] = alpha * sin(PI * i / (n - 1F));
            }
            layer(c, points, .1F, SLATE, al, .3F);
            layer(c, points, .035F, BRASS, al, .9F);
            layer(c, points, .012F, HIGHLIGHT, al, 1);
        }
    }

    /** A silk strand spun from the mouth, and a web blooming where it lands. */
    private static void web(Canvas c, Active a, float t) {
        float spool = easeOut(t / 3), alpha = 1 - smooth(9, 16, t);
        Vec3 from = a.origin(), to = from.lerp(a.target(), spool);
        streak(c, from, to, .02F, .15F * spool, SHEEN, alpha * .9F);
        if (t < 2.5F || !c.glow()) return;
        float bloom = easeOut((t - 2.5F) / 4);
        Vec3 axis = a.target().subtract(from).normalize();
        Vec3[] b = basis(axis);
        for (int k = 0; k < 8; k++) {
            float th = k * TWO_PI / 8;
            Vec3 end = a.target().add(b[0].scale(cos(th) * .75 * bloom)).add(b[1].scale(sin(th) * .75 * bloom));
            c.line(a.target(), end, .015F, HIGHLIGHT, alpha, alpha * .4F);
        }
        c.ring(a.target(), axis, .35F * bloom, .012F, 0, TWO_PI, HIGHLIGHT, alpha * .8F);
        c.ring(a.target(), axis, .65F * bloom, .012F, 0, TWO_PI, SHEEN, alpha * .6F);
    }

    /** A creeper's blast throws every plate of the shell outward at once, with a flash and a ring along the ground. */
    private static void detonate(Canvas c, Active a, float t) {
        Vec3 centre = a.origin();
        int count = minimal() ? 30 : 70;
        for (int i = 0; i < count; i++) {
            float u = hash(a.seed(), i) * 2 - 1, th = hash(a.seed() + 1, i) * TWO_PI;
            float r = sqrt(1 - u * u);
            Vec3 dir = new Vec3(r * cos(th), Math.abs(u) * .8 + .2, r * sin(th)).normalize();
            float speed = .35F + .35F * hash(a.seed() + 2, i);
            float travel = speed * t * (1 - t / 40);
            Vec3 at = centre.add(dir.scale(travel)).add(0, -.018 * t * t, 0);
            float size = (1 - smooth(8, 16, t)) * (.8F + .4F * hash(a.seed() + 3, i));
            loose(c, at, dir, t * (.5F + hash(a.seed() + 4, i)) * (i % 2 == 0 ? 1 : -1), size, .22F, a.seed(), i);
        }
        if (c.glow()) {
            float flash = 1 - smooth(0, 6, t);
            c.glow(centre, 3.2F * flash + .2F, BRASS, .7F * flash);
            c.glow(centre, 1.1F * flash, HIGHLIGHT, flash);
            Vec3 ground = centre.add(0, -.9, 0);
            seamRing(c, ground, UP, .5F + 4.5F * easeOut(t / 10), 1 - smooth(6, 14, t));
        }
    }

    /** The body sheds its shell where it stood, and plates gather into it where it lands. */
    private static void blink(Canvas c, Active a, float t, Entity body) {
        float w = body == null ? .6F : body.getBbWidth(), h = body == null ? 1.8F : body.getBbHeight();
        float yaw = body == null ? 0 : yawOf(body, c.partial);
        float shed = clamp(t / 9, 0, 1);
        hull(a.origin(), yaw, w, h, a.seed(), s -> {
            if (shed >= .98F) return;
            Vec3 hinge = s.hinge().add(s.out().scale(shed * (.5 + .6 * s.hash()))).add(0, shed * .3 - shed * shed * .9, 0);
            if (c.solid()) scale(c, s, hinge, s.lean() + 1.4F * shed, shed * 4 * (s.hash() > .5F ? 1 : -1), 1 - shed * shed, face(s), 1);
        });
        if (!(body != null && insideOf(body))) {
            float gather = clamp((t - 1) / 6, 0, 1), gone = smooth(6, 11, t);
            hull(a.target(), yaw, w, h, a.seed() + 1, s -> {
                if (gather <= 0 || gone >= .98F) return;
                Vec3 hinge = s.hinge().add(s.out().scale((1 - easeOut(gather)) * (1 + s.hash())));
                if (c.solid()) scale(c, s, hinge, s.lean() + (1 - snap(gather)) * 2, 0, (1 - gone) * gather, face(s), 1);
            });
        }
        streak(c, a.origin().add(0, h * .5, 0), a.target().add(0, h * .5, 0), .04F, 0, SHEEN, 1 - smooth(0, 5, t));
    }

    /** Plates strewn behind a pounce or a charge, tumbling down where the body passed; a charge starts with a roar ring. */
    private static void trail(Canvas c, Active a, float t, Entity body, boolean charge) {
        float h = body == null ? 1 : body.getBbHeight();
        int n = charge ? 20 : 14;
        float lay = charge ? 11 : 7;
        for (int i = 0; i < n; i++) {
            float born = i / (float) n * lay, age = t - born;
            if (age < 0 || age > 9) continue;
            Vec3 path = a.origin().lerp(a.target(), i / (float) n);
            Vec3 at = path.add((hash(a.seed(), i) - .5) * .8, h * (.3 + .6 * hash(a.seed() + 1, i)) - .03 * age * age, (hash(a.seed() + 2, i) - .5) * .8);
            Vec3 out = new Vec3(hash(a.seed() + 3, i) - .5, .4, hash(a.seed() + 4, i) - .5).normalize();
            loose(c, at, out, age * .7F * (i % 2 == 0 ? 1 : -1), 1 - smooth(4, 9, age), .2F, a.seed(), i);
        }
        if (charge) seamRing(c, a.origin().add(0, .05, 0), UP, .6F + 4.4F * easeOut(t / 8), 1 - smooth(5, 12, t));
    }

    /** A kicked arc along the ground in front, throwing plates up as it goes. */
    private static void buck(Canvas c, Active a, float t) {
        Vec3 dir = a.target().subtract(a.origin());
        dir = new Vec3(dir.x, 0, dir.z).normalize();
        Vec3 side = dir.cross(UP);
        float r = .6F + 2.9F * easeOut(t / 8), alpha = 1 - smooth(6, 13, t);
        int n = 17;
        Vec3[] arc = new Vec3[n];
        for (int i = 0; i < n; i++) {
            float th = -1.2F + 2.4F * i / (n - 1);
            arc[i] = a.origin().add(dir.scale(cos(th) * r)).add(side.scale(sin(th) * r)).add(0, .08, 0);
        }
        if (c.glow()) {
            float[] al = new float[n];
            for (int i = 0; i < n; i++) al[i] = alpha * sin(PI * i / (n - 1F));
            layer(c, arc, .3F, SLATE, al, .3F);
            layer(c, arc, .1F, BRASS, al, .9F);
            layer(c, arc, .03F, HIGHLIGHT, al, 1);
        }
        for (int i = 0; i < n; i += 2) {
            Vec3 at = arc[i].add(0, .5 * sin(PI * clamp(t / 10, 0, 1)) * (.5 + hash(a.seed(), i)), 0);
            loose(c, at, arc[i].subtract(a.origin()).normalize(), t * .8F, 1 - smooth(6, 11, t), .18F, a.seed(), i);
        }
    }

    /** A spiral of plates thrown up from under the flung creature. */
    private static void fling(Canvas c, Active a, float t) {
        Vec3 feet = a.origin();
        for (int i = 0; i < 14; i++) {
            float phase = i / 14F, rise = easeOut(clamp((t - phase * 3) / 10, 0, 1)) * 3;
            if (rise <= 0) continue;
            float th = phase * TWO_PI * 1.5F + t * .4F, r = .75F - .1F * rise;
            Vec3 out = new Vec3(cos(th), 0, sin(th));
            loose(c, feet.add(out.scale(r)).add(0, rise, 0), out, t * .6F, 1 - smooth(10, 16, t), .2F, a.seed(), i);
        }
        seamRing(c, feet.add(0, .05, 0), UP, .4F + 1.2F * easeOut(t / 6), 1 - smooth(3, 9, t));
    }

    /**
     * Apex Form's slam: a ring of plates bursts up out of the ground and races out five blocks, throwing plates up as it
     * goes, behind a wide brass ring and a flash at the feet.
     */
    private static void slam(Canvas c, Active a, float t, Entity body) {
        Vec3 feet = a.origin();
        float reach = .8F + 4.4F * easeOut(t / 7), alpha = 1 - smooth(6, 15, t);
        int count = minimal() ? 16 : 32;
        for (int i = 0; i < count; i++) {
            float th = i * TWO_PI / count + hash(a.seed(), i) * .2F;
            Vec3 out = new Vec3(cos(th), 0, sin(th));
            float lift = sin(PI * clamp((t - i % 3) / 12, 0, 1)) * (1 + hash(a.seed() + 1, i));
            loose(c, feet.add(out.scale(reach * (.7 + .3 * hash(a.seed() + 2, i)))).add(0, .1 + lift, 0), out, t * .5F + i,
                    1 - smooth(9, 15, t), .3F, a.seed(), i);
        }
        seamRing(c, feet.add(0, .06, 0), UP, reach, alpha);
        seamRing(c, feet.add(0, .06, 0), UP, reach * .6F, alpha * .5F);
        if (c.glow()) {
            float flash = 1 - smooth(0, 5, t);
            c.glow(feet.add(0, .4, 0), 2.4F * flash, BRASS, .6F * flash);
            c.glow(feet.add(0, .3, 0), .7F * flash, HIGHLIGHT, flash);
        }
    }

    /** A brass thread from you to the pet, with beads running along it. */
    private static void familiar(Canvas c, Active a, float t) {
        float alpha = fade(t, 16, 2, 6);
        streak(c, a.origin(), a.target(), .03F, .25F, BRASS, alpha);
        if (!c.glow()) return;
        for (int k = 0; k < 3; k++) {
            float u = ((t / 10) + k / 3F) % 1;
            Vec3 bead = a.origin().lerp(a.target(), u).add(0, -sin(PI * u) * .25, 0);
            c.glow(bead, .14F, BRASS, alpha * .8F);
            c.glow(bead, .05F, HIGHLIGHT, alpha);
        }
    }

    // ------------------------------------------------------------------
    // Held and charged moves
    // ------------------------------------------------------------------

    /**
     * A zombie's grab: bands of plates swing in round the held creature's chest, waist and thighs and clamp shut,
     * breathing while it struggles, then spring open and fall when it is let go.
     */
    static void hold(Canvas c, Active a, float t, Entity held) {
        if (insideOf(held)) return;
        float w = held.getBbWidth(), h = held.getBbHeight();
        Vec3 feet = held.getPosition(c.partial);
        float close = snap(clamp(t / 4, 0, 1)), open = smooth(34, 40, t);
        float[] heights = {.3F, .52F, .74F};
        for (int band = 0; band < heights.length; band++) {
            float pulse = 1 + .04F * sin(t * .8F + band);
            float radius = lerp(close, w * 1.2F + .5F, w * .46F + .04F) * pulse + open * .6F;
            Vec3 centre = feet.add(0, h * heights[band] - open * open * .8F, 0);
            // A cuff: one row hangs from the seam and one stands up from it, and both tuck their tips in against the
            // body, so each band pinches shut like a pair of jaws rather than flaring like a brim.
            float tuck = -.45F * close + (1 - close) * 1.4F + open * 1.8F;
            float spin = open * 3;
            for (Vec3 axis : new Vec3[] {UP, UP.scale(-1)}) {
                around(centre, axis, radius, 12, band * .3F + t * .03F + (axis.y < 0 ? .26F : 0), .3F, a.seed() + band * 2 + (axis.y < 0 ? 1 : 0), s -> {
                    if (c.solid()) scale(c, s, s.hinge(), tuck, spin * (s.hash() > .5F ? 1 : -1), 1 - open, face(s), 1);
                });
            }
            seamRing(c, centre, UP, radius * 1.02F, (1 - open) * close * .8F);
        }
    }

    /**
     * A warden's boom. For half a second a cone of plates gathers in front of the mouth, flaring faster and faster;
     * then the beam goes, with rings of plates opening one after another down its length.
     */
    static void sonic(Canvas c, Active a, float t, Entity body) {
        Vec3 from = a.origin(), to = a.target();
        Vec3 dir = to.subtract(from).normalize();
        float windup = ShapeshiftGeometry.SONIC_WINDUP;
        if (t < windup + 2) {
            Vec3 mouth = from.add(dir.scale(1.2));
            float k = clamp(t / windup, 0, 1), beat = Math.abs(sin(t * (.6F + t * .09F)));
            float gone = smooth(windup, windup + 2, t);
            // Two rings, the outer one wider and further out in front, so the cone opens toward the target and reads
            // from the side as well as the front.
            float big = body == null ? 1 : Math.min(1.3F, Math.max(1, body.getBbHeight() / 2.2F));
            for (int r = 0; r < 2; r++) {
                float radius = (.42F + .3F * k + r * (.22F + .2F * k)) * big;
                Vec3 at = mouth.add(dir.scale(r * .4 * big));
                int ring = r;
                around(at, dir.scale(-1), radius, 16 + 6 * r, t * .1F * (r == 0 ? 1 : -1), .28F * big, a.seed() + r, s -> {
                    if (c.solid()) scale(c, s, s.hinge(), .35F + 1.1F * beat * k, 0, (1 - gone) * (ring == 0 ? 1 : k), face(s), 1);
                });
                seamRing(c, at, dir, radius * 1.05F * (1 + .12F * beat), k * (1 - gone) * (r == 0 ? 1 : .7F));
            }
            if (c.glow()) c.glow(mouth, (.9F * k + .1F) * big, SHEEN, .55F * k * (1 - gone));
        }
        if (t < windup) return;
        float age = t - windup;
        double length = from.distanceTo(to);
        Vec3 camera = c.camera();
        for (int i = 1; i * 1.25 <= length; i++) {
            float ringAge = age - i * .25F;
            if (ringAge < 0 || ringAge > 10) continue;
            Vec3 at = from.add(dir.scale(i * 1.25));
            if (at.distanceTo(camera) < 1.2) continue;
            float r = .3F + .9F * easeOut(ringAge / 6), size = 1 - smooth(4, 10, ringAge);
            around(at, dir.scale(-1), r, 8, i * .7F, .24F, a.seed() + i, s -> {
                if (c.solid()) scale(c, s, s.hinge(), .4F + 1.2F * easeOut(ringAge / 4), 0, size, face(s), 1);
            });
            seamRing(c, at, dir, r * 1.1F, size * .8F);
        }
        streak(c, from, to, .12F, 0, SHEEN, 1 - smooth(0, 10, age));
    }

    /** A guardian's gaze: a beam that thickens and warms from sheen to brass for a second, plates spiralling along it, then a flash. */
    static void gaze(Canvas c, Active a, float t, Entity target) {
        Vec3 from = a.origin(), to = target == null ? a.target() : target.getPosition(c.partial).add(0, target.getBbHeight() * .6, 0);
        float charge = smooth(0, ShapeshiftGeometry.GAZE_WINDUP, t), end = smooth(ShapeshiftGeometry.GAZE_WINDUP, ShapeshiftGeometry.GAZE_WINDUP + 2, t);
        streak(c, from, to, .03F + .06F * charge, 0, mix(SHEEN, BRASS, charge), (.4F + .6F * charge) * (1 - end));
        Vec3 dir = to.subtract(from);
        double length = dir.length();
        if (length < .1) return;
        dir = dir.normalize();
        Vec3[] b = basis(dir);
        Vec3 camera = c.camera();
        for (int i = 0; i < 6; i++) {
            float u = (t * .06F + i / 6F) % 1;
            float th = u * TWO_PI * 3 + i;
            Vec3 out = b[0].scale(cos(th)).add(b[1].scale(sin(th)));
            Vec3 at = from.add(dir.scale(length * u)).add(out.scale(.22));
            if (at.distanceTo(camera) < 1) continue;
            loose(c, at, out, th, (1 - end) * .8F, .18F, a.seed(), i);
        }
        if (c.glow() && t > ShapeshiftGeometry.GAZE_WINDUP - 1) {
            float flash = 1 - smooth(ShapeshiftGeometry.GAZE_WINDUP - 1, ShapeshiftGeometry.GAZE_WINDUP + 2, t);
            c.glow(to, .8F * flash, BRASS, .6F * flash);
            c.glow(to, .25F, HIGHLIGHT, flash);
        }
    }
}
