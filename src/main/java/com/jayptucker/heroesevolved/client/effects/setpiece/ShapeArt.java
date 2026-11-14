package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.client.MorphClient;
import com.jayptucker.heroesevolved.client.UnseenClient;
import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient.Active;
import com.jayptucker.heroesevolved.shapeshift.ShapeshiftGeometry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.*;
import static net.minecraft.util.Mth.*;

/**
 * Shapeshifting: a body made of overlapping scales, like a pangolin's or a roof's tiles. Every piece is drawn from three
 * primitives on one hull that follows a body's outline (legs, hips, shoulders, neck and head for an upright body; a low
 * mound for a beast):
 * <ul>
 *   <li>{@code scales}: rows of curved, pointed plates, each hinged at its top edge, slate on the face and brass on the
 *       back, so a plate that flips over really shows its underside. Each tucks under the row above (darker at the
 *       hinge) and catches a pale sheen where it turns from the eye.</li>
 *   <li>{@code ripple}: a front that rolls along the hull (feet to head, or out from a touching hand), with a
 *       three-layer brass seam where it is.</li>
 *   <li>{@code shed}: plates flipping open, then torn loose to tumble on their own axes and drop away as they shrink.</li>
 * </ul>
 * Palette (iridescent, not neon): {@code 1F2A3D} shadow, {@code 3E6F86} slate-teal, {@code 8FCFC6} sheen,
 * {@code C29A5E} brass underside, {@code F1ECE2} highlight.
 */
final class ShapeArt {
    static final float[] SHADOW = rgb(0x1F2A3D), SLATE = rgb(0x3E6F86), SHEEN = rgb(0x8FCFC6),
            BRASS = rgb(0xC29A5E), HIGHLIGHT = rgb(0xF1ECE2);
    private static final Vec3 UP = new Vec3(0, 1, 0);

    /** A plate's outline: across from -1 to 1, down from 0 (the hinge) to 1 (the tip), pointed and rounded below. */
    private static final float[] EDGE_U = {-1, 1, 1, .84F, .48F, 0, -.48F, -.84F, -1};
    private static final float[] EDGE_V = {0, 0, .42F, .7F, .9F, 1, .9F, .7F, .42F};

    private ShapeArt() {}

    // ------------------------------------------------------------------
    // The hull
    // ------------------------------------------------------------------

    /**
     * One plate's place on the hull: hinged at {@code hinge}, the surface facing {@code out} (horizontal), its hinge
     * running along {@code along}. {@code fy} is how far up the body it sits, 0 at the feet and 1 at the crown.
     */
    record Spot(int row, int col, float fy, Vec3 out, Vec3 along, Vec3 hinge, float halfWidth, float length,
                        float lean, float hash, float hash2, Vec3 up) {
        Spot(int row, int col, float fy, Vec3 out, Vec3 along, Vec3 hinge, float halfWidth, float length, float lean, float hash, float hash2) {
            this(row, col, fy, out, along, hinge, halfWidth, length, lean, hash, hash2, UP);
        }
    }

    interface Visitor { void plate(Spot s); }

    /** Half-widths of an upright body (side to side, then front to back) per unit of hitbox width, by height. */
    private static final float[] UPRIGHT_Y = {0, .4F, .5F, .6F, .7F, .76F, .8F, .85F, .95F, 1};
    private static final float[] UPRIGHT_X = {.46F, .48F, .52F, .62F, .78F, .74F, .54F, .54F, .5F, .3F};
    private static final float[] UPRIGHT_Z = {.32F, .33F, .36F, .37F, .38F, .37F, .38F, .5F, .5F, .3F};
    /** A beast's mound, the same way. */
    private static final float[] MOUND_Y = {0, .25F, .6F, .85F, 1};
    private static final float[] MOUND_X = {.55F, .62F, .66F, .55F, .26F};

    private static float table(float[] ys, float[] xs, float y) {
        if (y <= ys[0]) return xs[0];
        for (int i = 1; i < ys.length; i++)
            if (y <= ys[i]) return lerp(smooth(ys[i - 1], ys[i], y), xs[i - 1], xs[i]);
        return xs[xs.length - 1];
    }

    /** How upright a body is: 1 for a person or a zombie, 0 for a cow or a spider. */
    private static float upright(float w, float h) { return smooth(1.5F, 2.6F, h / Math.max(w, .1F)); }

    /** The hull's half-width across the body at height fraction {@code fy}. */
    private static float halfX(float w, float h, float fy) {
        return lerp(upright(w, h), table(MOUND_Y, MOUND_X, fy), table(UPRIGHT_Y, UPRIGHT_X, fy)) * w + .05F;
    }

    /** The hull's half-depth, front to back, at height fraction {@code fy}. */
    private static float halfZ(float w, float h, float fy) {
        return lerp(upright(w, h), table(MOUND_Y, MOUND_X, fy) * 1.15F, table(UPRIGHT_Y, UPRIGHT_Z, fy)) * w + .05F;
    }

    /**
     * Walks every plate of a hull round a body {@code w} wide and {@code h} tall standing at {@code feet}: rows of
     * plates on an ellipse that follows the body's outline, each row offset half a plate from the last so the tips
     * sit between the plates below. The hull turns with the body, so the plates don't swim as it turns.
     */
    static void hull(Vec3 feet, float bodyYaw, float w, float h, int seed, Visitor visitor) {
        float plate = Math.max(.12F, h / 24F) * (minimal() ? 1.6F : 1);
        int rows = Math.max(5, ceil(h / (plate * .7F)));
        float rowHeight = h / rows;
        Vec3 forward = new Vec3(-sin(bodyYaw), 0, cos(bodyYaw)), side = new Vec3(cos(bodyYaw), 0, sin(bodyYaw));
        for (int i = 0; i < rows; i++) {
            float fy = (i + .55F) / rows;
            float a = halfX(w, h, fy), b = halfZ(w, h, fy);
            // Lean the plates out where the outline narrows upward (shoulders into the neck, the crown).
            float below = (halfX(w, h, fy - .05F) + halfZ(w, h, fy - .05F)) / 2, above = (halfX(w, h, fy + .05F) + halfZ(w, h, fy + .05F)) / 2;
            float lean = .16F + clamp((float) Math.atan2(below - above, .1F * h), -.2F, .7F);
            float perimeter = PI * (3 * (a + b) - sqrt((3 * a + b) * (a + 3 * b)));
            int cols = Math.max(6, Math.round(perimeter / (plate * .82F)));
            float halfWidth = perimeter / cols * .62F, length = rowHeight * 1.5F;
            float hingeY = (i + 1F) / rows * h + rowHeight * .12F;
            for (int j = 0; j < cols; j++) {
                float theta = (j + (i % 2) * .5F) * TWO_PI / cols;
                float x = a * cos(theta), z = b * sin(theta);
                Vec3 out = side.scale(cos(theta) / a).add(forward.scale(sin(theta) / b)).normalize();
                Vec3 along = new Vec3(-out.z, 0, out.x);
                Vec3 hinge = feet.add(side.scale(x)).add(forward.scale(z)).add(0, hingeY, 0);
                visitor.plate(new Spot(i, j, fy, out, along, hinge, halfWidth, length, lean,
                        hash(seed, i * 131 + j), hash(seed + 17, i * 71 + j * 3)));
            }
        }
    }

    /**
     * Draws one scale. {@code angle} swings it about its hinge (0 hangs straight down, positive lifts the tip outward,
     * past {@code PI/2} it flips over and shows its brass back). {@code tumble} spins a plate that has torn loose about
     * its own tilted axis. Solid in the SOLID pass; a pane of {@code alpha} in the others.
     */
    static void scale(Canvas c, Spot s, Vec3 hinge, float angle, float tumble, float size, float[] face, float alpha) {
        float co = cos(angle), si = sin(angle);
        Vec3 along = s.along;
        Vec3 down = s.up.scale(-co).add(s.out.scale(si));
        Vec3 normal = s.out.scale(co).add(s.up.scale(si));
        if (tumble != 0) {
            // A loose plate turns about an axis between its hinge line and its face.
            Vec3 axis = along.scale(s.hash2 - .5F).add(normal.scale(.6F)).add(down.scale(s.hash - .5F)).normalize();
            along = spin(along, axis, tumble);
            down = spin(down, axis, tumble);
            normal = spin(normal, axis, tumble);
        }
        float w = s.halfWidth * size, l = s.length * size, bow = .035F * size, thick = .009F;
        Vec3 view = c.camera().subtract(hinge).normalize();
        float facing = (float) Math.abs(normal.dot(view));
        float fresnel = (1 - facing) * (1 - facing) * (1 - facing);
        float[] front = c.lit((float) normal.x, (float) normal.y, (float) normal.z, mix(face, SHEEN, fresnel * .55F), new float[3]);
        float[] back = c.lit((float) -normal.x, (float) -normal.y, (float) -normal.z, BRASS, new float[3]);
        int n = EDGE_U.length;
        double[][] fp = new double[n + 1][], bp = new double[n + 1][];
        float[] shade = new float[n + 1];
        for (int k = 0; k <= n; k++) {
            float u = k == n ? 0 : EDGE_U[k], v = k == n ? .45F : EDGE_V[k];
            float lift = k == n ? bow * 1.4F : bow * (1 - u * u) * sin(PI * v);
            Vec3 p = hinge.add(along.scale(u * w)).add(down.scale(v * l)).add(normal.scale(lift));
            fp[k] = new double[] {p.x + normal.x * thick, p.y + normal.y * thick, p.z + normal.z * thick};
            bp[k] = new double[] {p.x - normal.x * thick, p.y - normal.y * thick, p.z - normal.z * thick};
            // Darker where it tucks under the row above; lighter toward the tip.
            shade[k] = .45F + .5F * smooth(0, .5F, v) + .08F * v;
        }
        for (int k = 0; k < n; k++) {
            int k2 = (k + 1) % n;
            tint(c, fp[n], front, shade[n], 0, alpha);
            tint(c, fp[k], front, shade[k], EDGE_V[k] * .1F, alpha);
            tint(c, fp[k2], front, shade[k2], EDGE_V[k2] * .1F, alpha);
            tint(c, bp[n], back, 1, 0, alpha);
            tint(c, bp[k2], back, .85F + .15F * EDGE_V[k2], EDGE_V[k2] * .4F, alpha);
            tint(c, bp[k], back, .85F + .15F * EDGE_V[k], EDGE_V[k] * .4F, alpha);
        }
    }

    /** One vertex: {@code base} darkened by {@code shade} and warmed toward the highlight by {@code light}. */
    private static void tint(Canvas c, double[] p, float[] base, float shade, float light, float alpha) {
        c.v(p[0], p[1], p[2], clamp(lerp(light, base[0], HIGHLIGHT[0]) * shade, 0, 1), clamp(lerp(light, base[1], HIGHLIGHT[1]) * shade, 0, 1),
                clamp(lerp(light, base[2], HIGHLIGHT[2]) * shade, 0, 1), alpha);
    }

    static Vec3 spin(Vec3 v, Vec3 axis, float angle) {
        double co = Math.cos(angle), si = Math.sin(angle);
        return v.scale(co).add(axis.cross(v).scale(si)).add(axis.scale(axis.dot(v) * (1 - co)));
    }

    /** A plate's own face colour: slate, darker on some, the way a scaled hide is never one flat colour. */
    static float[] face(Spot s) { return mix(SLATE, SHADOW, .2F + s.hash2 * .45F); }

    /** Where a plate's tip is, hanging at rest. */
    static Vec3 tip(Spot s) { return s.hinge.add(0, -s.length * .9F, 0).add(s.out.scale(.04)); }

    /** A three-layer brass seam round the hull at height fraction {@code fy}: halo, body, core. */
    private static void seam(Canvas c, Vec3 feet, float bodyYaw, float w, float h, float fy, float alpha, float camLimit) {
        if (!c.glow() || alpha <= .01F) return;
        float a = halfX(w, h, fy) * 1.06F, b = halfZ(w, h, fy) * 1.06F;
        if (feet.y + fy * h > camLimit) return;
        Vec3 forward = new Vec3(-sin(bodyYaw), 0, cos(bodyYaw)), side = new Vec3(cos(bodyYaw), 0, sin(bodyYaw));
        int n = 33;
        Vec3[] loop = new Vec3[n];
        for (int i = 0; i < n; i++) {
            float th = i * TWO_PI / (n - 1);
            loop[i] = feet.add(side.scale(a * cos(th))).add(forward.scale(b * sin(th))).add(0, fy * h, 0);
        }
        band(c, loop, .13F, SLATE, .35F * alpha);
        band(c, loop, .045F, BRASS, .85F * alpha);
        band(c, loop, .015F, HIGHLIGHT, alpha);
    }

    static void band(Canvas c, Vec3[] points, float width, float[] colour, float alpha) {
        float[] w = new float[points.length], a = new float[points.length];
        java.util.Arrays.fill(w, width);
        java.util.Arrays.fill(a, alpha);
        c.ribbon(points, w, colour, a);
    }

    static float easeOut(float p) { float k = 1 - clamp(p, 0, 1); return 1 - k * k * k; }

    /** Overshoots a little past 1 and settles back: a plate snapping shut. */
    static float snap(float p) {
        float k = clamp(p, 0, 1) - 1;
        return 1 + 2.7F * k * k * k + 1.7F * k * k;
    }

    // ------------------------------------------------------------------
    // The morph (drawn live from MorphClient's transitions)
    // ------------------------------------------------------------------

    static boolean live() { return MorphClient.transitions().iterator().hasNext(); }

    static void drawLive(Canvas c) {
        long now = c.level.getGameTime();
        for (MorphClient.Transition t : MorphClient.transitions()) {
            Entity body = c.level.getEntity(t.entity());
            if (body == null || UnseenClient.hidden(body.getId()) || body.isInvisible()) continue;
            morph(c, body, t, t.progress(now, c.partial));
        }
    }

    /**
     * Plates unfurl at a front that rolls up the old body from the feet, each swinging down from brass to slate and
     * snapping shut. Once the shell is closed it bristles in a wave that runs up it, catching the light, and bulges
     * into the new body's size. Then the plates lift open from the feet up, showing brass, tear loose, tumble out and
     * drop away, leaving the new shape. Going back to your own body runs the same from the head down. Seen from
     * inside (your own change in first person), the shell is a wider curtain that rises to just under your eyes and
     * scatters outward, so you see it without the view being blocked.
     */
    private static void morph(Canvas c, Entity body, MorphClient.Transition t, float u) {
        Vec3 feet = body.getPosition(c.partial);
        float bodyYaw = yawOf(body, c.partial);
        float covered = ShapeshiftGeometry.COVERED, shedAt = ShapeshiftGeometry.SHED;
        // The shell grows into the new body with a little overshoot, so it bulges as the body changes inside.
        float grow = snap(smooth(covered, shedAt + .04F, u));
        float w = Math.max(.2F, lerp(grow, t.fromWidth(), t.toWidth())), h = Math.max(.3F, lerp(grow, t.fromHeight(), t.toHeight()));
        float p = clamp(u / covered, 0, 1);
        float closing = (1 - (1 - p) * (1 - p)) * 1.32F - .12F;
        float q = clamp((u - shedAt) / (1 - shedAt), 0, 1);
        float opening = u < shedAt ? -1 : (float) Math.pow(q, 1.15) * 1.38F - .14F;
        float sealed = smooth(covered - .05F, covered, u) * (1 - smooth(shedAt - .02F, shedAt + .06F, u));
        float wave = (u - covered + .03F) / (shedAt - covered) * 1.5F - .25F;
        boolean down = t.down(), inside = insideOf(body), detail = detail();
        float eyeLimit = Float.MAX_VALUE, seamLimit = Float.MAX_VALUE;
        if (inside) {
            // From inside, the shell is a wider curtain round you that rises to just under your eyes, so you see it
            // climb and close without it filling the screen. The eye already sits at the new body's height.
            float eye = (float) (body.getEyePosition(c.partial).y - feet.y);
            w = Math.max(w, 1.7F);
            h = Math.max(h, eye + .35F);
            eyeLimit = (float) feet.y + eye - .28F;
            seamLimit = (float) feet.y + eye - .4F;
        }
        int seed = t.entity() * 31 + (int) t.start();
        final float plateLimit = eyeLimit;

        hull(feet, bodyYaw, w, h, seed, s -> {
            float k = (down ? 1 - s.fy : s.fy) + (s.hash - .5F) * .1F;
            float closed = clamp((closing - k) / .2F, 0, 1);
            if (closed <= 0) return;
            float open = opening < -.5F ? 0 : clamp((opening - k) / .18F, 0, 1);
            float lift = easeOut(open / .45F) * 1.75F;
            float loose = clamp((open - .45F) / .55F, 0, 1);
            if (loose >= .98F) return;
            // Unfurl: from folded up (brass out) down to rest, snapping a little past it.
            float angle = s.lean + (1 - snap(closed)) * 2.75F + lift;
            float bristle = (float) Math.exp(-square((s.fy - wave) / .09F)) * sealed;
            angle += bristle * .5F;
            float size = (.35F + .65F * smooth(0, .45F, closed)) * (1 - loose * loose);
            Vec3 hinge = s.hinge;
            float tumble = 0;
            if (loose > 0) {
                // Torn loose: flung out (harder near the crown), lifted a little, then dropping as it spins.
                float fling = .55F + .5F * s.hash + (s.fy > .8F ? .35F : 0);
                hinge = hinge.add(s.out.scale(loose * fling)).add(s.along.scale((s.hash2 - .5F) * loose * .5F))
                        .add(0, loose * (.35F + (s.fy > .8F ? .3F : 0)) - loose * loose * 1.35F, 0);
                tumble = loose * (4 + 3 * s.hash2) * (s.hash > .5F ? 1 : -1);
            }
            if (inside && hinge.y > plateLimit && loose < .15F) return;
            float[] colour = mix(face(s), SHEEN, bristle * .55F);
            if (c.solid()) {
                scale(c, s, hinge, angle, tumble, size, colour, 1);
            } else if (c.glow() && detail && (s.col + s.row) % 2 == 0) {
                // A glint as each plate turns over, and along the bristling wave.
                float turning = closed < 1 ? sin(PI * closed) : 0;
                float flash = open > 0 && open < .6F ? sin(PI * open / .6F) : 0;
                float glint = Math.max(turning, flash);
                Vec3 at = loose > 0 ? hinge : tip(s);
                if (glint > .05F && at.y <= plateLimit) c.glow(at, .1F * size + .02F, BRASS, .4F * glint);
                if (bristle > .3F && at.y <= plateLimit) c.glow(at, .07F, HIGHLIGHT, .3F * bristle);
            }
        });

        // The two fronts, each a brass seam rolling along the hull.
        if (closing > -.12F && closing < 1.15F) {
            float k = clamp(closing, 0, 1);
            seam(c, feet, bodyYaw, w, h, down ? 1 - k : k, sin(PI * k) * (1 - sealed), seamLimit);
        }
        if (opening > -.14F && opening < 1.2F) {
            float k = clamp(opening, 0, 1);
            seam(c, feet, bodyYaw, w, h, down ? 1 - k : k, sin(PI * k), seamLimit);
        }
    }

    // ------------------------------------------------------------------
    // Set pieces: study and touch
    // ------------------------------------------------------------------

    static void draw(Canvas c, Active a, float t) {
        Entity bound = a.bound(c);
        switch (a.piece()) {
            case SHIFT_STUDY -> { if (!c.solid() && bound != null) study(c, a, t, bound); }
            case SHIFT_TOUCH -> { if (!c.solid() && bound != null) touch(c, a, t, bound); }
            case SHIFT_LUNGE -> ShapeInstinctArt.lunge(c, a, t, bound);
            case SHIFT_INSTINCT -> ShapeInstinctArt.instinct(c, a, t, bound);
            case SHIFT_HOLD -> { if (bound != null) ShapeInstinctArt.hold(c, a, t, bound); }
            case SHIFT_SONIC -> ShapeInstinctArt.sonic(c, a, t, bound);
            case SHIFT_GAZE -> ShapeInstinctArt.gaze(c, a, t, bound);
            case SHIFT_DECOY -> ShapeTrickArt.seam(c, a, t, bound);
            case SHIFT_DECOY_BURST -> ShapeTrickArt.burst(c, a, t);
            case SHIFT_REFLECT -> ShapeTrickArt.mirror(c, a, t, bound);
            case SHIFT_NIGHTMARE -> ShapeTrickArt.nightmare(c, a, t, bound);
            case SHIFT_EXPOSED -> ShapeTrickArt.exposed(c, a, t, bound);
            case SHIFT_DAZE -> { if (bound != null) ShapeTrickArt.daze(c, a, t, bound); }
            case SHIFT_PLATES -> { if (bound != null) ShapeTrickArt.plates(c, a, t, bound); }
            default -> { }
        }
    }

    static float yawOf(Entity e, float partial) {
        return (e instanceof LivingEntity l ? rotLerp(partial, l.yBodyRotO, l.yBodyRot) : e.getYRot()) * DEG_TO_RAD;
    }

    /**
     * Ghostly plates scan up the creature twice, lifting as the band passes and leaving a faint shell behind, while a
     * thread runs from the studier's eye to it. As the study completes, the whole shell flashes brass and settles.
     */
    private static void study(Canvas c, Active a, float t, Entity target) {
        float life = a.piece().ticks, alpha = fade(t, life, 3, 3);
        Vec3 feet = target.getPosition(c.partial);
        float w = target.getBbWidth(), h = target.getBbHeight(), bodyYaw = yawOf(target, c.partial);
        float scan = (t / life * 2) % 1, memory = Math.min(1, t / life * 1.4F), done = smooth(life - 6, life - 2, t);
        if (!insideOf(target)) {
            hull(feet, bodyYaw, w, h, a.seed(), s -> {
                float near = (float) Math.exp(-square((s.fy - scan) / .09F));
                float seen = s.fy < scan || t > life / 2 ? memory * .18F : 0;
                float k = Math.max(near * .6F, seen) + done * .45F;
                if (k * alpha < .02F) return;
                if (c.soft()) scale(c, s, s.hinge, s.lean + near * .55F, 0, .95F, mix(face(s), SHEEN, near * .7F), Math.min(1, k * alpha * .95F));
                else if (near > .55F && (s.col & 1) == 0) c.glow(tip(s), .07F, SHEEN, .35F * near * alpha);
            });
            seam(c, feet, bodyYaw, w, h, scan, alpha * .8F, Float.MAX_VALUE);
        }
        if (c.glow()) {
            Vec3 chest = feet.add(0, h * .6, 0);
            thread(c, a.origin().add(0, -.2, 0), chest, alpha * (.5F + .5F * done));
        }
    }

    /** Plates ripple out over the creature from the hand that touched it, lift and flash brass, then settle to nothing. */
    private static void touch(Canvas c, Active a, float t, Entity target) {
        float life = a.piece().ticks, alpha = fade(t, life, 1, 5);
        Vec3 feet = target.getPosition(c.partial), hand = a.origin().add(feet.subtract(a.target()));
        float w = target.getBbWidth(), h = target.getBbHeight(), bodyYaw = yawOf(target, c.partial);
        float span = Math.max(w, h) * 1.3F + .3F, wave = easeOut(t / (life * .75F)) * 1.25F;
        if (!insideOf(target)) {
            hull(feet, bodyYaw, w, h, a.seed(), s -> {
                float d = (float) tip(s).distanceTo(hand) / span;
                float k = (float) Math.exp(-square((wave - d) / .22F));
                float after = d < wave ? .22F * (1 - t / life) : 0;
                float shown = Math.max(k, after);
                if (shown * alpha < .02F) return;
                if (c.soft()) scale(c, s, s.hinge, s.lean + k * 1.6F, 0, .95F, mix(face(s), SHEEN, k * .7F), Math.min(1, shown * alpha * 1.1F));
                else if (k > .6F && (s.col & 1) == 0) c.glow(tip(s), .09F, BRASS, .45F * k * alpha);
            });
        }
        if (c.glow()) {
            float burst = 1 - smooth(0, 6, t);
            c.glow(hand, .4F * burst + .05F, BRASS, .6F * burst);
            c.glow(hand, .13F, HIGHLIGHT, .9F * burst);
        }
    }

    /** A three-layer thread, faded where it passes the camera so a studier's own view isn't striped by it. */
    private static void thread(Canvas c, Vec3 from, Vec3 to, float alpha) {
        int n = 12;
        Vec3[] points = new Vec3[n];
        float[] fadeOut = new float[n];
        Vec3 camera = c.camera();
        for (int i = 0; i < n; i++) {
            points[i] = from.lerp(to, i / (n - 1.0)).add(0, -sin(PI * i / (n - 1F)) * .08F, 0);
            fadeOut[i] = alpha * clamp((float) points[i].distanceTo(camera) / 1.6F - .2F, 0, 1);
        }
        ribbon(c, points, .06F, SLATE, fadeOut, .3F);
        ribbon(c, points, .02F, SHEEN, fadeOut, .7F);
        ribbon(c, points, .007F, HIGHLIGHT, fadeOut, 1);
    }

    private static void ribbon(Canvas c, Vec3[] points, float width, float[] colour, float[] alpha, float k) {
        float[] w = new float[points.length], a = new float[points.length];
        for (int i = 0; i < points.length; i++) { w[i] = width; a[i] = alpha[i] * k; }
        c.ribbon(points, w, colour, a);
    }

    // ------------------------------------------------------------------

    /** The camera is inside this body (its owner in first person). */
    static boolean insideOf(Entity body) {
        Minecraft mc = Minecraft.getInstance();
        return body == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
    }

    static boolean minimal() { return Minecraft.getInstance().options.particles().get() == ParticleStatus.MINIMAL; }

    static boolean detail() { return Minecraft.getInstance().options.particles().get() == ParticleStatus.ALL; }
}
