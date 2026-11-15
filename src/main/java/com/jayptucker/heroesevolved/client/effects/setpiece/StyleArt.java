package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient.Active;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.*;
import static net.minecraft.util.Mth.*;

/**
 * The fighting styles' own pieces, each in its power's language. Force Palm is drawn with {@link ForceArt}'s lens and
 * pane (the field colour, a lattice of facets, a bright rim) so a punch through a lens is plainly the same power as the
 * shield; the Ice Skater's strip is frost laid flat on the ground, glittering as it lingers. Most other styles need no
 * pieces of their own: they speak in their power's existing strokes (see {@code combat/ComboStyle}).
 */
final class StyleArt {
    private static final float[] FROST = rgb(0xCFEFFF), RIME = rgb(0xF4FBFF), GLACIER = rgb(0x6FB6E8);

    private StyleArt() {}

    static void draw(Canvas c, Active a, float t) {
        if (c.solid()) return;
        float alpha = fade(t, a.piece().ticks, 1, Math.min(8, a.piece().ticks * .45F));
        Vec3 at = a.origin(), end = a.target();
        Player owner = a.bound(c) instanceof Player p ? p : null;
        switch (a.piece()) {
            case STYLE_LENS_JAB -> lensJab(c, at, end, a.scale(), t, alpha);
            case STYLE_SHARDS -> shard(c, at, end, t, alpha, a.seed());
            case STYLE_FORCE_BURST -> burst(c, at, end, a.scale(), t, alpha, a.seed());
            case STYLE_FIELD_SLAM -> slam(c, at, a.scale(), t, alpha);
            case STYLE_LENS_SLIDE -> slide(c, owner, at, end, t, alpha);
            case STYLE_BOUNCE -> bounce(c, at, end, t, alpha);
            case STYLE_ICE_STRIP -> strip(c, at, end, t, a.piece().ticks, a.seed());
            case STYLE_TK_PALM -> palm(c, at, end, a.scale(), t, alpha);
            case STYLE_TK_GRASP -> grasp(c, a.bound(c), at, a.scale(), t, a.seed());
            default -> { }
        }
    }

    /**
     * A jab through a lens: a small lens forms on the knuckles, flies out with the blow and flattens against what it
     * hits, its facets flashing as it cracks. A miss leaves it fading where the blow ran out.
     */
    private static void lensJab(Canvas c, Vec3 fist, Vec3 end, float size, float t, float alpha) {
        Vec3 path = end.subtract(fist);
        if (path.lengthSqr() < .01) return;
        Vec3 aim = path.normalize();
        float travel = smooth(0, 2.5F, t), hit = smooth(2, 4, t);
        Vec3 centre = fist.lerp(end.subtract(aim.scale(.25)), travel);
        float radius = (.22F + .18F * hit) * (size > .8F ? 1 : .8F);
        ForceArt.lens(c, centre, aim, radius, .05F - .08F * hit, t * .2F, alpha, size > .8F ? hit : 0);
        if (c.glow()) {
            c.line(fist, centre, .03F, ForceArt.FIELD, 0, alpha * .4F * (1 - hit));
            if (size > .8F) c.glow(end, .55F * hit * (1 - smooth(5, 9, t)), ForceArt.EDGE, alpha * .6F);
        }
    }

    /** One shard of released pressure: a tumbling triangle of field flying from the fist to its end. */
    private static void shard(Canvas c, Vec3 fist, Vec3 end, float t, float alpha, int seed) {
        Vec3 path = end.subtract(fist);
        if (path.lengthSqr() < .01 || !c.glow()) return;
        Vec3 aim = path.normalize(), side = side(aim), up = aim.cross(side);
        float travel = smooth(0, 4, t);
        Vec3 at = fist.lerp(end, travel);
        float spin = t * 1.3F + hash(seed, 1) * TWO_PI;
        Vec3 a = at.add(aim.scale(.28)), b = at.add(side.scale(cos(spin) * .14)).add(up.scale(sin(spin) * .14)),
                d = at.add(side.scale(cos(spin + 2.1F) * .14)).add(up.scale(sin(spin + 2.1F) * .14));
        float k = alpha * (1 - smooth(4, 7, t));
        c.tri(a, b, d, ForceArt.FIELD, k * .6F, k * .6F, k * .6F);
        c.line(a, b, .02F, ForceArt.EDGE, k, k);
        c.line(b, d, .02F, ForceArt.EDGE, k, k);
        c.line(d, a, .02F, ForceArt.EDGE, k, k);
        c.line(at.subtract(aim.scale(.9)), at, .025F, ForceArt.SHEEN, 0, k * .5F);
        if (t > 3.5F) c.glow(end, .3F * (1 - smooth(4, 8, t)), ForceArt.EDGE, alpha * .7F);
    }

    /** Force Burst: five lenses fanned from the palms race out across the cone and widen as they go. */
    private static void burst(Canvas c, Vec3 chest, Vec3 end, float reach, float t, float alpha, int seed) {
        Vec3 forward = flat(end.subtract(chest)), right = new Vec3(-forward.z, 0, forward.x);
        float open = smooth(0, 6, t), k = alpha * (1 - smooth(7, 14, t));
        for (int i = 0; i < 5; i++) {
            float angle = (i - 2) * .32F;
            Vec3 dir = forward.scale(cos(angle)).add(right.scale(sin(angle)));
            Vec3 centre = chest.add(dir.scale(.5 + reach * .85 * open)).add(0, (hash(seed, i) - .5F) * .3F, 0);
            ForceArt.lens(c, centre, dir, .35F + .75F * open, -.1F * open, t * .15F + i, k * (1 - .3F * Math.abs(i - 2) / 2), 1 - open);
        }
        if (c.glow()) c.glow(chest.add(forward.scale(.4)), 1.1F * (1 - smooth(0, 5, t)), ForceArt.EDGE, alpha * .7F);
    }

    /** Field Slam: a dome of force snaps out from the palm along the ground, its rim racing to the edge. */
    private static void slam(Canvas c, Vec3 feet, float radius, float t, float alpha) {
        float open = 1 - (float) Math.pow(1 - clamp(t / 7, 0, 1), 3), k = alpha * (1 - smooth(8, 16, t));
        float r = .4F + (radius - .4F) * open;
        if (c.glow()) {
            c.shell(feet, r, r * .55F, r, HALF_PI, ForceArt.FIELD, k * .35F, .05F, Canvas.PLAIN, t * .05F);
            ForceArt.cage(c, feet, r * .98F, t * .08F, UP, k * .4F, 0, ForceArt.EDGE);
            c.ring(feet.add(0, .05, 0), UP, r, .07F, 0, TWO_PI, ForceArt.EDGE, k);
            c.ring(feet.add(0, .05, 0), UP, r, .2F, 0, TWO_PI, ForceArt.DEEP, k * .4F);
        }
    }

    /** Shield Slide: a pane rides along on the side the body slides from, its rim lit. */
    private static void slide(Canvas c, Player owner, Vec3 from, Vec3 to, float t, float alpha) {
        Vec3 dir = to.subtract(from);
        if (dir.lengthSqr() < .01) return;
        dir = flat(dir);
        Vec3 feet = owner != null ? owner.getPosition(c.partial) : from.lerp(to, smooth(0, 5, t));
        // The pane stands behind the slide (where the blow came from), facing back along it.
        Vec3 normal = dir.scale(-1), across = new Vec3(-normal.z, 0, normal.x);
        Vec3 corner = feet.add(normal.scale(.7)).subtract(across.scale(.8)).add(0, .1, 0);
        ForceArt.pane(c, corner, across.scale(1.6), new Vec3(0, 2, 0), alpha * (1 - smooth(6, 12, t)), .35F, true);
    }

    /** Bounce: a pane under the feet bows down and flings the body up and back, then fades with a rim of light. */
    private static void bounce(Canvas c, Vec3 feet, Vec3 back, float t, float alpha) {
        float bow = sin(PI * clamp(t / 4, 0, 1));
        Vec3 centre = feet.add(0, .08 - .25 * bow, 0);
        ForceArt.lens(c, centre, UP, 1.1F, -.35F * bow, t * .1F, alpha * (1 - smooth(6, 14, t)), bow);
        if (c.glow()) c.ring(feet.add(0, .06, 0), UP, 1.1F + .9F * smooth(2, 10, t), .05F, 0, TWO_PI, ForceArt.EDGE,
                alpha * (1 - smooth(4, 12, t)));
    }

    /**
     * A skate's ice strip: frost laid flat along the skate, drawn in from the start as the skater passes, glittering
     * while it lingers and breaking up into patches as it melts.
     */
    private static void strip(Canvas c, Vec3 from, Vec3 to, float t, int life, int seed) {
        Vec3 path = to.subtract(from);
        double length = path.length();
        if (length < .3) return;
        Vec3 dir = path.scale(1 / length), right = new Vec3(-dir.z, 0, dir.x);
        float laid = smooth(0, 5, t), melt = smooth(life - 20, life, t), k = smooth(0, 1.5F, t) * (1 - melt);
        int n = 10;
        if (c.soft()) {
            Vec3[] spine = new Vec3[n];
            float[] w = new float[n], a = new float[n];
            for (int i = 0; i < n; i++) {
                float u = laid * i / (n - 1F);
                spine[i] = from.add(dir.scale(length * u)).add(0, .035, 0);
                // As it melts, it breaks into patches.
                float patch = .6F + .4F * sin(i * 2.3F + seed);
                w[i] = .5F * (1 - melt * (1 - patch));
                a[i] = k * (.45F + .15F * patch) * (1 - smooth(.7F, 1, (float) i / (n - 1)) * .5F);
            }
            c.ribbon(spine, w, FROST, a);
            for (int s : new int[]{-1, 1}) {
                Vec3[] edge = new Vec3[n];
                for (int i = 0; i < n; i++) edge[i] = spine[i].add(right.scale(s * w[i] * .9));
                c.ribbon(edge, .03F, GLACIER, k * .5F, k * .5F);
            }
        }
        if (c.glow() && !minimal()) {
            float clock = (c.level.getGameTime() % 2400 + c.partial);
            for (int i = 0; i < 9; i++) {
                float u = hash(seed, i), twinkle = .5F + .5F * sin(clock * .5F + i * 2.7F);
                Vec3 at = from.add(dir.scale(length * u * laid)).add(right.scale((hash(seed, i + 9) - .5F) * .7F)).add(0, .05, 0);
                c.glow(at, .07F + .05F * twinkle, RIME, k * twinkle * .8F);
            }
        }
    }

    // ------------------------------------------------------------------
    // Puppeteer (Telekinesis): the unseen hand, seen
    // ------------------------------------------------------------------

    private static final float[] TK_CORE = rgb(0xECF5FF), TK_EDGE = rgb(0xBFC8EB), TK_SHADE = rgb(0x929FC5);
    /** Finger offsets across the palm and their lengths, little finger to index (in palm widths). */
    private static final float[] FINGER_AT = {-.36F, -.12F, .12F, .36F}, FINGER_LONG = {.62F, .82F, .9F, .78F};

    /**
     * A swat: a hand of bent air, a body and a half tall, sweeps in from the far side, flattens against the foe with a
     * ripple, and follows through as it fades. {@code at} is the foe's middle, {@code to} the way it was batted.
     */
    private static void palm(Canvas c, Vec3 at, Vec3 to, float size, float t, float alpha) {
        Vec3 dir = to.subtract(at);
        if (dir.lengthSqr() < 1e-4) return;
        dir = dir.normalize();
        Vec3 across = side(dir), up = across.cross(dir).normalize();
        // Fingers always point up, whichever way the swat goes.
        if (up.y < 0) { up = up.scale(-1); across = across.scale(-1); }
        float scale = .95F * Math.max(.8F, size);
        float arrive = smooth(0, 2.2F, t), follow = smooth(2, 10, t);
        Vec3 contact = at.subtract(dir.scale(.45));
        Vec3 wrist = contact.subtract(dir.scale(1.6 * (1 - arrive))).add(dir.scale(.7 * follow)).subtract(up.scale(.35 * scale));
        // The hand bends back a little on contact, as a palm does.
        float flatten = sin(PI * clamp((t - 1.6F) / 4, 0, 1));
        float k = alpha * (.55F + .45F * flatten);
        Vec3 palm = wrist.add(up.scale(.42 * scale));
        if (c.soft()) c.disc(palm, dir, .42F * scale, TK_SHADE, .22F * k, 0);
        if (!c.glow()) return;
        // The heel of the palm and its outline.
        c.ring(palm, dir, .4F * scale, .045F, 0, TWO_PI, TK_EDGE, k * .8F);
        c.ring(palm, dir, .4F * scale, .015F, 0, TWO_PI, TK_CORE, k);
        Vec3 knuckles = palm.add(up.scale(.34 * scale));
        for (int i = 0; i < 4; i++) {
            Vec3 base = knuckles.add(across.scale(FINGER_AT[i] * scale * .8));
            float curl = .12F * flatten;
            Vec3[] finger = new Vec3[4];
            for (int j = 0; j < 4; j++) {
                float u = j / 3F;
                finger[j] = base.add(up.scale(FINGER_LONG[i] * scale * .75 * u)).add(across.scale(FINGER_AT[i] * scale * .25 * u))
                        .subtract(dir.scale(curl * u * u));
            }
            c.ribbon(finger, .07F * scale, TK_EDGE, k * .75F, k * .35F);
            c.ribbon(finger, .025F * scale, TK_CORE, k, k * .5F);
        }
        // The thumb, out to the side.
        Vec3 thumbBase = palm.add(across.scale(-.38 * scale)).subtract(up.scale(.05 * scale));
        Vec3[] thumb = {thumbBase, thumbBase.add(across.scale(-.22 * scale)).add(up.scale(.22 * scale)),
                thumbBase.add(across.scale(-.3 * scale)).add(up.scale(.46 * scale))};
        c.ribbon(thumb, .07F * scale, TK_EDGE, k * .75F, k * .35F);
        c.ribbon(thumb, .025F * scale, TK_CORE, k, k * .5F);
        // The blow: a ripple runs out from the print, and a flash where it landed.
        float ripple = smooth(1.8F, 8, t);
        if (ripple > 0 && ripple < 1) {
            c.ring(contact, dir, .3F + 1.3F * ripple * scale, .06F, 0, TWO_PI, TK_EDGE, alpha * (1 - ripple) * .8F);
            c.glow(contact, .7F * scale * (1 - smooth(2, 6, t)) * smooth(1.5F, 2.2F, t), TK_CORE, alpha * .7F);
        }
    }

    /**
     * Lift and Grip: five fingers of bent air close over the body from above and hold it, trembling, until the hold
     * ends ({@code hold} ticks, carried in the piece's scale) and they spring open.
     */
    private static void grasp(Canvas c, net.minecraft.world.entity.Entity body, Vec3 at, float hold, float t, int seed) {
        if (!c.glow() && !c.soft()) return;
        Vec3 feet = body != null ? body.getPosition(c.partial) : at;
        float height = body != null ? body.getBbHeight() : 1.8F, width = body != null ? body.getBbWidth() : .6F;
        float close = smooth(0, 3, t), open = smooth(hold, hold + 4, t);
        float k = close * (1 - open);
        if (k <= 0.003F) return;
        float clock = c.level.getGameTime() % 2400 + c.partial;
        float r = (width * .5F + .3F) * (1 + .8F * (1 - close) + 1.2F * open);
        Vec3 crown = feet.add(0, height + .45 + .4 * open, 0);
        int n = 7;
        for (int f = 0; f < 5; f++) {
            float angle = f * TWO_PI / 5 + hash(seed, 0) * TWO_PI;
            float tremble = .04F * sin(clock * 1.7F + f * 2.1F);
            Vec3 out = new Vec3(cos(angle), 0, sin(angle));
            // The thumb is shorter and set lower.
            float reach = f == 0 ? .5F : .78F;
            Vec3[] finger = new Vec3[n];
            for (int i = 0; i < n; i++) {
                float u = i / (n - 1F), bend = sin(u * PI * .5F);
                double radius = r * bend + tremble;
                finger[i] = crown.add(out.scale(radius)).add(0, -(height + .45) * reach * u * u - .1 * u, 0);
            }
            if (c.soft()) c.ribbon(finger, .16F, TK_SHADE, k * .3F, k * .05F);
            else {
                c.ribbon(finger, .07F, TK_EDGE, k * .8F, k * .2F);
                c.ribbon(finger, .025F, TK_CORE, k, k * .3F);
            }
        }
        if (c.glow()) {
            // The palm over the head, and a band of pressure round the waist.
            c.glow(crown, .5F * k, TK_CORE, k * .45F);
            c.ring(feet.add(0, height * .55, 0), UP, r * .95F, .05F, 0, TWO_PI, TK_EDGE, k * (.4F + .2F * sin(clock * .9F)));
        }
    }

    private static Vec3 flat(Vec3 v) {
        Vec3 level = new Vec3(v.x, 0, v.z);
        return level.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : level.normalize();
    }

    private static Vec3 side(Vec3 axis) { return (Math.abs(axis.y) > .93 ? new Vec3(0, 0, 1) : UP).cross(axis).normalize(); }

    private static boolean minimal() { return Minecraft.getInstance().options.particles().get() == ParticleStatus.MINIMAL; }
}
