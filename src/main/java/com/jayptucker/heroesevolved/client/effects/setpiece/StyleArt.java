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

    private static final float[] WARM = rgb(0xFF9A3C), WARM_CORE = rgb(0xFFE0A0);

    /**
     * Last Warmth (Cryokinesis's finisher, {@code cryo/LastWarmth}): an ember of the foe's warmth is drawn out of its
     * chest along a thread to the open palm (the only warm colour in the kit), frost rings creep over the body from the
     * feet and the crown toward the middle as it leaves, the fist closes on it in a puff of steam, and the rimed body
     * breaks into falling snow. Bound to the foe, the caster's entity id in the seed; scale its height (+ HOT: a flame).
     */
    static void warmth(Canvas c, Active a, float t) {
        boolean hot = a.scale() >= com.jayptucker.heroesevolved.cryo.LastWarmth.HOT;
        float h = Math.max(.6F, a.scale() - (hot ? com.jayptucker.heroesevolved.cryo.LastWarmth.HOT : 0));
        float life = a.piece().ticks, f = fade(t, life, 1, 6);
        net.minecraft.world.entity.Entity foe = a.bound(c), owner = c.level.getEntity(a.seed());
        Vec3 feet = foe != null && foe.isAlive() ? foe.getPosition(c.partial) : a.origin();
        Vec3 chest = feet.add(0, h * .6, 0);
        Vec3 palm = owner instanceof net.minecraft.world.entity.player.Player p
                ? com.jayptucker.heroesevolved.combat.StyleHits.hand(p, c.partial, true) : chest.add(0, 0, 1);
        int draw = com.jayptucker.heroesevolved.cryo.LastWarmth.DRAW, close = com.jayptucker.heroesevolved.cryo.LastWarmth.CLOSE;
        int land = com.jayptucker.heroesevolved.cryo.LastWarmth.LAND;
        float r = Math.max(.45F, h * .22F);
        // Frost creeping in from the feet and the crown.
        float creep = smooth(draw, land, t);
        if (c.glow() && t < land + 4) for (int k = 0; k < 2; k++) {
            double y = k == 0 ? h * (.05 + .45 * creep) : h * (.98 - .4 * creep);
            float a1 = f * (.3F + .6F * creep) * (1 - smooth(land, land + 4, t));
            c.ring(feet.add(0, y, 0), UP, r, .07F, 0, TWO_PI, GLACIER, a1 * .4F);
            c.ring(feet.add(0, y, 0), UP, r, .022F, 0, TWO_PI, RIME, a1);
        }
        // The ember drawn out to the palm, and the thread it rides.
        float pull = smooth(draw, close, t);
        if (t >= draw && t < close + 1) {
            Vec3 ember = chest.lerp(palm, pull);
            float size = hot ? .26F : .12F;
            if (c.glow()) {
                c.line(chest, ember, .02F, FROST, f * .5F, f * .8F);
                c.glow(ember, size * 2.4F, WARM, f * .5F);
                c.glow(ember, size, WARM_CORE, f);
            }
        }
        // The fist closes: steam.
        float steam = smooth(close, close + 1, t) * (1 - smooth(close + 2, close + 8, t));
        if (steam > 0 && c.glow()) for (int i = 0; i < 5; i++) {
            Vec3 puff = palm.add((hash(a.seed(), i) - .5) * .3, (t - close) * .05 + hash(a.seed(), i + 5) * .2, (hash(a.seed(), i + 9) - .5) * .3);
            c.glow(puff, .14F + (t - close) * .02F, RIME, steam * f * .35F);
        }
        // Snow and chips of ice as the rimed body breaks.
        if (t >= land && c.glow()) {
            float fall = t - land;
            for (int i = 0; i < 16; i++) {
                double ang = hash(a.seed(), i + 20) * TWO_PI, rr = r * (.4 + 1.2 * hash(a.seed(), i + 40));
                double y = h * hash(a.seed(), i + 60) - fall * (.05 + .05 * hash(a.seed(), i + 80));
                if (y < 0) continue;
                c.glow(feet.add(Math.cos(ang) * rr * (1 + fall * .03), y, Math.sin(ang) * rr * (1 + fall * .03)), .05F, RIME, f * .9F);
            }
        }
    }

    /**
     * Telekinesis's alternate finishers ({@code telekinesis/KineticFinishers}), with the Puppeteer's hand. Crush: the
     * fingers close over the foe from above and squeeze in, trembling, bands of pressure tightening round it, then an
     * inward ripple as it buckles. Toss: the hand carries it up, holds it, and drives it down into a ripple and dust
     * ring. Bound to the foe; origin its feet where it stood, target yours, scale its height.
     */
    static void kineticFinishers(Canvas c, Active a, float t) {
        if (c.solid()) return;
        float h = Math.max(.6F, a.scale()), f = fade(t, a.piece().ticks, 1, 7);
        net.minecraft.world.entity.Entity foe = a.bound(c);
        boolean alive = foe != null && foe.isAlive();
        Vec3 feet = alive ? foe.getPosition(c.partial) : a.origin();
        float clock = c.level.getGameTime() % 2400 + c.partial;
        if (a.piece() == com.jayptucker.heroesevolved.styles.Piece.FINISH_CRUSH) {
            int land = com.jayptucker.heroesevolved.telekinesis.KineticFinishers.CRUSH_LAND;
            if (t < land + 1) {
                float squeeze = smooth(2, land, t);
                fingers(c, feet, h, foe != null ? foe.getBbWidth() : .6F, smooth(0, 3, t), squeeze, f, clock, a.seed());
                if (c.glow()) for (int i = 0; i < 3; i++) {
                    float r = (Math.max(.35F, h * .25F) + .3F) * (1.1F - .45F * squeeze) * (1 + .04F * sin(clock * 2 + i));
                    c.ring(feet.add(0, h * (.25 + .28 * i), 0), UP, r, .04F, 0, TWO_PI, TK_EDGE, f * (.35F + .45F * squeeze));
                }
            } else if (c.glow()) {
                float k = smooth(land, land + 6, t);
                c.ring(feet.add(0, h * .5, 0), UP, 1.4F * (1 - k) + .1F, .06F, 0, TWO_PI, TK_EDGE, f * (1 - k));
                c.glow(feet.add(0, h * .5, 0), .9F * (1 - smooth(land, land + 3, t)), TK_CORE, f * (1 - smooth(land, land + 3, t)));
            }
            return;
        }
        // Toss.
        int high = com.jayptucker.heroesevolved.telekinesis.KineticFinishers.TOSS_HIGH, pull = com.jayptucker.heroesevolved.telekinesis.KineticFinishers.TOSS_PULL;
        int land = com.jayptucker.heroesevolved.telekinesis.KineticFinishers.TOSS_LAND;
        if (t < land) {
            fingers(c, feet, h, foe != null ? foe.getBbWidth() : .6F, smooth(0, 3, t), .25F, f, clock, a.seed());
            // A trail of bent air under the body on the way down.
            if (t > pull && c.glow()) c.line(feet, feet.add(0, 1.4, 0), .2F, TK_EDGE, 0, f * .5F);
            if (c.glow() && t < high) c.ring(a.origin().add(0, .05, 0), UP, .6F + .4F * smooth(0, high, t), .04F, 0, TWO_PI, TK_EDGE, f * .5F);
        } else {
            Vec3 ground = a.origin().add(0, .05, 0);
            float k = smooth(land, land + 8, t);
            if (c.glow()) {
                c.ring(ground, UP, .4F + 2.6F * k, .06F, 0, TWO_PI, TK_EDGE, f * (1 - k));
                c.ring(ground, UP, .3F + 1.6F * k, .025F, 0, TWO_PI, TK_CORE, f * (1 - k));
                c.glow(ground.add(0, .4, 0), 1.1F * (1 - smooth(land, land + 3, t)), TK_CORE, f * .6F * (1 - smooth(land, land + 3, t)));
            }
            if (c.soft()) c.ring(ground, UP, .5F + 2.2F * k, .35F, 0, TWO_PI, TK_SHADE, f * .3F * (1 - k));
        }
    }

    /** Five fingers of bent air over a body, from open ({@code close} 0) to shut, squeezed in by {@code squeeze}. */
    private static void fingers(Canvas c, Vec3 feet, float height, float width, float close, float squeeze, float alpha, float clock, int seed) {
        float k = close * alpha;
        if (k <= .003F) return;
        float r = (width * .5F + .3F) * (1 + .8F * (1 - close)) * (1 - .4F * squeeze);
        Vec3 crown = feet.add(0, height + .45, 0);
        int n = 7;
        for (int f = 0; f < 5; f++) {
            float angle = f * TWO_PI / 5 + hash(seed, 0) * TWO_PI;
            float tremble = (.04F + .05F * squeeze) * sin(clock * (1.7F + squeeze * 2) + f * 2.1F);
            Vec3 out = new Vec3(cos(angle), 0, sin(angle));
            float reach = f == 0 ? .5F : .78F;
            Vec3[] finger = new Vec3[n];
            for (int i = 0; i < n; i++) {
                float u = i / (n - 1F), bend = sin(u * PI * .5F);
                finger[i] = crown.add(out.scale(r * bend + tremble)).add(0, -(height + .45) * reach * u * u - .1 * u, 0);
            }
            if (c.soft()) c.ribbon(finger, .16F, TK_SHADE, k * .3F, k * .05F);
            else if (c.glow()) {
                c.ribbon(finger, .07F, TK_EDGE, k * .8F, k * .2F);
                c.ribbon(finger, .025F, TK_CORE, k, k * .3F);
            }
        }
        if (c.glow()) c.glow(crown, .5F * k, TK_CORE, k * .45F);
    }

    /**
     * Cryokinesis's alternate finishers ({@code cryo/CryoFinishers}), in Last Warmth's frost: Coffin (leaning shards grow
     * up over the body and seal, then burst outward and skid away) and Icicle Ring (a ring of icicles rises, leans in until
     * their points meet over the foe, a frost burst, and they crumble). Origin the foe's feet, target yours, scale its height.
     */
    static void cryoFinishers(Canvas c, Active a, float t) {
        if (c.solid()) return;
        float h = Math.max(.6F, a.scale()), f = fade(t, a.piece().ticks, 1, 7);
        Vec3 feet = a.origin();
        int seed = a.seed();
        if (a.piece() == com.jayptucker.heroesevolved.styles.Piece.FINISH_COFFIN) {
            int sealed = com.jayptucker.heroesevolved.cryo.CryoFinishers.COFFIN_SEALED, land = com.jayptucker.heroesevolved.cryo.CryoFinishers.COFFIN_LAND;
            float r = Math.max(.4F, h * .26F);
            int n = minimal() ? 7 : 11;
            if (t < land) {
                for (int i = 0; i < n; i++) {
                    float ang = (i + hash(seed, i) * .4F) * TWO_PI / n;
                    // Each shard starts a little later than the last, so the ice climbs round the body.
                    float grow = smooth(i * .6F, sealed - 2 + i * .2F, t);
                    if (grow <= 0) continue;
                    Vec3 out = new Vec3(cos(ang), 0, sin(ang));
                    Vec3 base = feet.add(out.scale(r * 1.05));
                    Vec3 tip = feet.add(out.scale(r * .35)).add(0, (h + .25) * (.75F + .25F * hash(seed, i + 7)) * grow, 0);
                    float shake = t > sealed ? (hash(seed + (int) t, i) - .5F) * .02F : 0;
                    c.shard(base.add(shake, 0, shake), tip, .16F + .06F * hash(seed, i + 3), 5, ang, FROST, f * .55F);
                }
                float seal = smooth(sealed - 3, sealed, t);
                if (c.soft() && seal > 0) c.shell(feet.add(0, h * .5, 0), r * .95F, h * .58F, r * .95F, PI, GLACIER, f * .25F * seal, .6F,
                        Canvas.hexes(5, .12F, .4F), 0);
                if (c.glow()) for (int k = 0; k < 3; k++) {
                    float y = (float) (h * (.15 + .35 * k)) * smooth(0, sealed, t);
                    c.ring(feet.add(0, y, 0), UP, r, .02F, 0, TWO_PI, RIME, f * .7F);
                }
            } else {
                // Shattered: shards thrown out and skidding over the ground, slowing, then snow.
                float k = t - land;
                for (int i = 0; i < n + 4; i++) {
                    float ang = hash(seed, i + 30) * TWO_PI;
                    Vec3 out = new Vec3(cos(ang), 0, sin(ang));
                    float travel = (1 - (float) Math.exp(-k * .25F)) * (1.2F + 1.6F * hash(seed, i + 40));
                    double hop = Math.max(0, .9 * hash(seed, i + 50) * h * (1 - k / 6F) * (k < 6 ? 1 : 0) + .04);
                    Vec3 at = feet.add(out.scale(r + travel)).add(0, hop, 0);
                    Vec3 tip = at.add(out.scale(.3)).add(0, .08, 0);
                    c.shard(at, tip, .07F + .05F * hash(seed, i + 60), 4, ang + k * .4F, FROST, f * .7F);
                }
                if (c.glow()) {
                    c.glow(feet.add(0, h * .5, 0), 1.2F * (1 - smooth(land, land + 3, t)), RIME, f * (1 - smooth(land, land + 3, t)));
                    c.ring(feet.add(0, .04, 0), UP, r + 2.2F * smooth(land, land + 8, t), .03F, 0, TWO_PI, RIME, f * (1 - smooth(land + 2, land + 9, t)));
                }
                snow(c, feet, r, h, k, f, seed);
            }
            return;
        }
        // Icicle Ring.
        int risen = com.jayptucker.heroesevolved.cryo.CryoFinishers.ICICLES_RISEN, land = com.jayptucker.heroesevolved.cryo.CryoFinishers.ICICLES_LAND;
        float ringR = Math.max(1.3F, h * .6F + .7F);
        int n = minimal() ? 6 : 9;
        Vec3 meet = feet.add(0, h + .35, 0);
        float rise = smooth(0, risen, t), lean = smooth(risen, land, t), crumble = smooth(land + 3, land + 10, t);
        for (int i = 0; i < n; i++) {
            float ang = i * TWO_PI / n + .2F;
            Vec3 base = feet.add(cos(ang) * ringR, 0, sin(ang) * ringR);
            Vec3 upright = base.add(0, (h + .8) * rise, 0);
            Vec3 tip = upright.lerp(meet, lean);
            if (crumble > 0) tip = base.lerp(tip, 1 - crumble);
            if (base.distanceToSqr(tip) < .01) continue;
            c.shard(base, tip, .14F * (1 - crumble * .5F), 5, ang, FROST, f * .6F);
            if (c.glow()) c.line(base, tip, .02F, RIME, f * .3F, f * .7F);
        }
        if (c.glow() && t < land + 4) c.ring(feet.add(0, .04, 0), UP, ringR, .03F, 0, TWO_PI, GLACIER, f * .6F * rise);
        if (t >= land) {
            float burst = smooth(land, land + 6, t), gone = f * (1 - smooth(land + 2, land + 9, t));
            if (c.glow()) {
                c.glow(meet, .3F + 1.3F * (1 - smooth(land, land + 3, t)), RIME, f * (1 - smooth(land, land + 3, t)));
                c.ring(feet.add(0, h * .5, 0), UP, .4F + 1.6F * burst, .04F, 0, TWO_PI, FROST, gone);
            }
            if (c.soft()) c.shell(feet.add(0, h * .5, 0), .45F + .2F * h, h * .55F, .45F + .2F * h, PI, GLACIER, gone * .3F, .6F,
                    Canvas.hexes(5, .12F, .4F), 0);
            snow(c, feet, ringR * .6F, h, t - land, f, seed);
        }
    }

    /** Falling snow and glinting chips round a broken frozen body. */
    private static void snow(Canvas c, Vec3 feet, float r, float h, float fall, float f, int seed) {
        if (!c.glow()) return;
        for (int i = 0; i < 16; i++) {
            double ang = hash(seed, i + 20) * TWO_PI, rr = r * (.4 + 1.2 * hash(seed, i + 40));
            double y = h * hash(seed, i + 60) - fall * (.05 + .05 * hash(seed, i + 80));
            if (y < 0) continue;
            c.glow(feet.add(Math.cos(ang) * rr * (1 + fall * .03), y, Math.sin(ang) * rr * (1 + fall * .03)), .05F, RIME, f * .9F);
        }
    }

    /**
     * Cut Strings (Telekinesis's finisher, {@code telekinesis/CutStrings}): thin strings run from the raised fingers to
     * the foe's head, hands and feet, swaying as it dances, and snap one by one at the cut, their halves whipping away.
     * Bound to the foe, the puppeteer's entity id in the seed; scale its height.
     */
    static void strings(Canvas c, Active a, float t) {
        float h = Math.max(.6F, a.scale()), life = a.piece().ticks, f = fade(t, life, 2, 6);
        net.minecraft.world.entity.Entity foe = a.bound(c), owner = c.level.getEntity(a.seed());
        if (foe == null || !(owner instanceof net.minecraft.world.entity.player.Player p)) return;
        Vec3 feet = foe.getPosition(c.partial);
        float yaw = net.minecraft.util.Mth.rotLerp(c.partial, foe instanceof net.minecraft.world.entity.LivingEntity l ? l.yBodyRotO : 0,
                foe instanceof net.minecraft.world.entity.LivingEntity l2 ? l2.yBodyRot : 0) * DEG_TO_RAD;
        Vec3 side = new Vec3(cos(yaw), 0, sin(yaw));
        Vec3 hand = com.jayptucker.heroesevolved.combat.StyleHits.hand(p, c.partial, true).add(0, .25, 0);
        Vec3[] ends = {feet.add(0, h * .97, 0), feet.add(side.scale(.38)).add(0, h * .6, 0), feet.add(side.scale(-.38)).add(0, h * .6, 0),
                feet.add(side.scale(.14)).add(0, .1, 0), feet.add(side.scale(-.14)).add(0, .1, 0)};
        int cut = com.jayptucker.heroesevolved.telekinesis.CutStrings.CUT;
        float reach = smooth(0, 6, t);
        for (int i = 0; i < ends.length; i++) {
            Vec3 finger = hand.add(side.scale((i - 2) * .05));
            float snapAt = cut + i * .6F, sway = sin((t + i * 3) * .5F) * .12F;
            Vec3 end = finger.lerp(ends[i], reach);
            if (t < snapAt) {
                Vec3 mid = finger.lerp(end, .5).add(0, -.1 + sway, 0);
                if (c.glow()) {
                    c.line(finger, mid, .018F, TK_EDGE, f * .5F, f * .5F);
                    c.line(mid, end, .018F, TK_EDGE, f * .5F, f * .5F);
                    c.line(finger, mid, .006F, TK_CORE, f, f);
                    c.line(mid, end, .006F, TK_CORE, f, f);
                    c.glow(end, .06F, TK_CORE, f * reach);
                }
            } else if (c.glow()) {
                // Snapped: the two halves whip away and fade.
                float k = smooth(snapAt, snapAt + 5, t), gone = f * (1 - k);
                Vec3 mid = finger.lerp(ends[i], .5);
                c.line(finger, finger.lerp(mid, 1 - k * .7).add(0, -k * .4, 0), .012F, TK_EDGE, gone, gone * .3F);
                c.line(ends[i], ends[i].lerp(mid, 1 - k * .7).add(0, k * .5, 0), .012F, TK_EDGE, gone, gone * .3F);
                if (k < .3F) c.glow(mid, .15F, TK_CORE, f * (1 - k / .3F));
            }
        }
    }
}
