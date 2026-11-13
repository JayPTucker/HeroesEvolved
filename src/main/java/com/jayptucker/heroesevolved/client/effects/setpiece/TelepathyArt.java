package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient.Active;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.telepathy.TelepathyGeometry;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.*;
import static net.minecraft.util.Mth.*;

/**
 * Thought as Matt Parkman feels it, for every Telepathy skill and style. One shape language from four primitives:
 * <ul>
 *   <li>{@link #thread}: a fine filament between two minds, bowed, shimmering along its length, with beads of thought
 *   running down it and dimming where it passes the camera;</li>
 *   <li>{@link #iris}: an eye's iris of rings and striations, opening round a mind that is being read;</li>
 *   <li>{@link #ripple}: a thought-wave, a ring whose rim swells and dips in lobes;</li>
 *   <li>{@link #murmur}: three arcs over a dot, a voice.</li>
 * </ul>
 * The palette is the inside of a closed eye, not neon: {@code SHADOW 241638}, {@code VIOLET 7A4BD6}, {@code LILAC
 * B794FF}, {@code PALE E6D8FF} and a {@code CORE FFF7FF}, with {@code ROSE FF8FC0} for a mind won over and {@code FEAR
 * D23A5C} for one that is hurt, frightened or in danger. Memories are drawn on the ground in fading footsteps.
 */
final class TelepathyArt {
    private static final float[] SHADOW = rgb(0x241638), VIOLET = rgb(0x7A4BD6), LILAC = rgb(0xB794FF), PALE = rgb(0xE6D8FF),
            CORE = rgb(0xFFF7FF), ROSE = rgb(0xFF8FC0), FEAR = rgb(0xD23A5C);

    private TelepathyArt() {}

    static void draw(Canvas c, Active a, float t) {
        if (c.solid()) return;
        Entity bound = a.bound(c);
        float clock = (c.level.getGameTime() % 2400 + c.partial) / 20F;
        switch (a.piece()) {
            case TELE_COMMAND -> command(c, a, bound instanceof Player p ? p : null, t, clock);
            case TELE_LISTEN -> listen(c, a, bound, t, clock);
            case TELE_VOICE -> voice(c, a, bound, t);
            case TELE_WEAVE -> weave(c, a, bound, t, clock);
            case TELE_PERSUADE -> {
                if (SetPieceClient.superseded(a, java.util.Set.of(Piece.TELE_WON, Piece.TELE_FRIGHT), c.level.getGameTime())) return;
                persuade(c, a, bound, t, clock);
            }
            case TELE_WON -> won(c, a, bound, t, clock);
            case TELE_RECALL -> recall(c, a, bound, t, clock);
            case TELE_MEMORY -> memory(c, a, t);
            case TELE_ECHO -> echo(c, a, t, clock);
            case TELE_LOST -> lost(c, a, bound, t, clock);
            case TELE_FRIGHT -> fright(c, a, bound, t);
            case TELE_HIVE_LINK -> hiveLink(c, a, bound, t, clock);
            case TELE_HALO -> halo(c, a, bound, t, clock);
            case TELE_PROJECT -> project(c, a, bound, t, clock);
            case PSYCHIC_STORM -> storm(c, a, t, clock);
            case BLIND_SPOT -> blindSpot(c, a, bound, t, clock);
            case HIVE_MIND -> hiveMind(c, a, bound, t, clock);
            default -> { }
        }
    }

    // ------------------------------------------------------------------
    // The four primitives
    // ------------------------------------------------------------------

    /** Two directions across {@code d}. */
    private static Vec3[] across(Vec3 d) {
        Vec3 n = d.normalize();
        Vec3 u = (Math.abs(n.y) > .9 ? new Vec3(1, 0, 0) : UP).cross(n).normalize();
        return new Vec3[] {u, n.cross(u).normalize()};
    }

    private static Vec3 bezier(Vec3 a, Vec3 mid, Vec3 b, float k) {
        float j = 1 - k;
        return a.scale(j * j).add(mid.scale(2 * j * k)).add(b.scale(k * k));
    }

    /**
     * A thread of thought from {@code a} toward {@code b}, bowed by {@code bow} at its middle and drawn as far as
     * {@code reach} (0 to 1) of the way. {@code beads} glowing points run down it toward {@code b}.
     */
    static void thread(Canvas c, Vec3 a, Vec3 b, Vec3 bow, float reach, float width, float[] tint, float alpha, float clock, int beads) {
        if (alpha < .01F || reach <= 0 || a.distanceToSqr(b) < 1e-4) return;
        int n = 20;
        Vec3 mid = a.lerp(b, .5).add(bow);
        Vec3 side = across(b.subtract(a))[0];
        Vec3[] p = new Vec3[n + 1];
        float[] fade = new float[n + 1], w = new float[n + 1];
        Vec3 eye = c.camera();
        for (int i = 0; i <= n; i++) {
            float k = reach * i / n, wobble = sin(k * 11 + clock * 7) * .05F * sin(PI * k);
            p[i] = bezier(a, mid, b, k).add(side.scale(wobble));
            fade[i] = alpha * (float) Math.clamp((p[i].distanceTo(eye) - .7) / 1.6, 0, 1);
            w[i] = width;
        }
        if (c.soft()) c.ribbon(p, scaled(w, 3.2F), VIOLET, scaled(fade, .28F));
        if (c.glow()) {
            c.ribbon(p, w, tint, scaled(fade, .75F));
            c.ribbon(p, scaled(w, .35F), CORE, scaled(fade, .9F));
            for (int j = 0; j < beads; j++) {
                float k = frac(clock * .9F + j / (float) beads) * reach;
                Vec3 at = bezier(a, mid, b, k);
                float near = (float) Math.clamp((at.distanceTo(eye) - .7) / 1.6, 0, 1);
                c.glow(at, width * 4.5F, PALE, alpha * near * sin(PI * k / Math.max(reach, .01F)));
            }
        }
    }

    /** An iris round {@code at}, across {@code axis}: rings, turning striations and a dark pupil {@code pupil} of the way out. */
    static void iris(Canvas c, Vec3 at, Vec3 axis, float r, float[] tint, float alpha, float spin, float pupil) {
        if (alpha < .01F || r < .02F) return;
        if (c.soft() && pupil > 0) c.disc(at, axis, r * pupil, SHADOW, .45F * alpha, .25F * alpha);
        if (!c.glow()) return;
        c.ring(at, axis, r, .04F, 0, TWO_PI, tint, .8F * alpha);
        c.ring(at, axis, r * .58F, .022F, 0, TWO_PI, PALE, .6F * alpha);
        int spokes = minimal() ? 8 : 14;
        for (int i = 0; i < spokes; i++)
            c.spoke(at, axis, spin + i * TWO_PI / spokes, r * .62F, r * .94F, .016F, i % 2 == 0 ? tint : PALE, .55F * alpha);
    }

    /** A thought-wave: a ring across {@code axis} whose rim swells in {@code lobes} lobes as it turns. */
    static void ripple(Canvas c, Vec3 at, Vec3 axis, float r, float width, float[] tint, float alpha, float phase, int lobes) {
        if (alpha < .01F || r < .02F) return;
        Vec3[] b = across(axis);
        int n = minimal() ? 28 : 48;
        Vec3[] p = new Vec3[n + 1];
        for (int i = 0; i <= n; i++) {
            float ang = i * TWO_PI / n, rr = r * (1 + .08F * sin(lobes * ang + phase * 3));
            p[i] = at.add(b[0].scale(cos(ang) * rr)).add(b[1].scale(sin(ang) * rr));
        }
        if (c.soft()) c.ribbon(p, width * 3, VIOLET, alpha * .22F, alpha * .22F);
        if (c.glow()) {
            c.ribbon(p, width, tint, alpha * .75F, alpha * .75F);
            c.ribbon(p, width * .35F, CORE, alpha * .7F, alpha * .7F);
        }
    }

    /** A voice: a dot and three arcs over it, facing {@code toward} (the viewer), {@code size} across. */
    static void murmur(Canvas c, Vec3 at, Vec3 toward, float size, float[] tint, float alpha) {
        if (alpha < .01F || !c.glow()) return;
        Vec3 n = toward.normalize();
        Vec3 up = UP.subtract(n.scale(UP.dot(n)));
        if (up.lengthSqr() < 1e-4) up = new Vec3(1, 0, 0);
        up = up.normalize();
        Vec3 right = n.cross(up).normalize();
        Vec3 base = at.subtract(up.scale(size * .45F));
        c.glow(base, size * .22F, PALE, alpha);
        for (int k = 1; k <= 3; k++) {
            float r = size * (.28F + .26F * k);
            Vec3[] arc = new Vec3[9];
            for (int i = 0; i <= 8; i++) {
                float ang = (i / 8F - .5F) * 1.75F;
                arc[i] = base.add(up.scale(cos(ang) * r)).add(right.scale(sin(ang) * r));
            }
            float aa = alpha * (1 - k * .18F);
            c.ribbon(arc, size * .07F, tint, aa, aa);
            c.ribbon(arc, size * .025F, CORE, aa * .8F, aa * .8F);
        }
    }

    // ------------------------------------------------------------------
    // The skills
    // ------------------------------------------------------------------

    /** Drop It: a dart of a word leaves the fingers, and a ring snaps tight round the mind it hits. */
    private static void command(Canvas c, Active a, Player owner, float t, float clock) {
        Vec3 from = owner != null ? hand(c, owner, .55) : a.origin(), to = a.target();
        float fly = smooth(0, 3, t);
        if (t < 6 && c.glow()) {
            Vec3 head = from.lerp(to, fly), tail = from.lerp(to, Math.max(0, fly - .35F));
            float f = 1 - smooth(3, 6, t);
            c.line(tail, head, .045F, LILAC, 0, .9F * f);
            c.line(tail, head, .015F, CORE, 0, f);
            c.glow(head, .18F, PALE, f);
        }
        if (t < 3) return;
        float hold = 1 - smooth(9, 14, t), squeeze = smooth(3, 9, t);
        ripple(c, to, UP, lerp(squeeze, 1.15F, .38F), .04F, LILAC, hold, clock, 5);
        ripple(c, to.add(0, -.55, 0), UP, lerp(squeeze, .9F, .3F), .03F, PALE, hold * .7F, clock + 1, 4);
        Vec3 view = c.camera().subtract(to);
        iris(c, to, view, .28F * (1 - squeeze * .3F), LILAC, hold * (1 - smooth(3, 12, t)), clock * 2, .35F);
    }

    /** Far Hearing: thought-waves drawn in round the listener's head, and a light at each temple. */
    private static void listen(Canvas c, Active a, Entity bound, float t, float clock) {
        if (bound == null) return;
        float f = fade(t, a.piece().ticks, 2, 2);
        boolean me = firstPerson(bound);
        Vec3 eye = bound.getEyePosition(c.partial);
        for (int i = 0; i < 3; i++) {
            float k = frac(clock * .7F + i / 3F), r = lerp(k, 2.3F, .5F);
            // Seen by anyone else, thought-waves close in on the head from every side, facing them. The listener's own
            // first-person view stays clear: the voices' marks and the action bar say enough.
            if (!me) ripple(c, eye, c.camera().subtract(eye), r * .8F, .028F, LILAC, f * .5F * sin(PI * k), clock + i, 6);
        }
        if (!me && c.glow() && bound instanceof Player p) {
            Vec3 temple = TelepathyGeometry.temple(p, c.partial), other = eye.add(eye.subtract(temple).multiply(1, 0, 1));
            float pulse = .7F + .3F * sin(clock * 6);
            c.glow(temple, .14F, PALE, f * pulse);
            c.glow(new Vec3(other.x, temple.y, other.z), .14F, PALE, f * pulse);
        }
    }

    /** A voice heard by Far Hearing: a murmur floating a few blocks off, in the speaker's direction, smaller the farther. */
    private static void voice(Canvas c, Active a, Entity bound, float t) {
        if (bound == null) return;
        Vec3 eye = bound.getEyePosition(c.partial), mark = TelepathyGeometry.mark(eye, a.target());
        float far = clamp((a.scale() - 1) / 10, 0, 1), size = lerp(far, .34F, .17F);
        float alpha = fade(t, a.piece().ticks, 1, 4) * lerp(far, .85F, .45F);
        Vec3 look = bound.getViewVector(c.partial), toward = mark.subtract(eye).normalize();
        if (firstPerson(bound) && look.dot(toward) > .97) alpha *= .45F;
        alpha *= near(c, mark);
        murmur(c, mark, c.camera().subtract(mark), size, LILAC, alpha);
        Vec3 tail = mark.add(toward.scale(1.4));
        if (c.glow()) c.line(mark.add(toward.scale(.15)), tail, .012F, PALE, alpha * .6F * near(c, tail), 0);
    }

    /** Dream Double: three threads spiral from the weaving hands into the victim's head, then close round it. */
    private static void weave(Canvas c, Active a, Entity bound, float t, float clock) {
        Player me = Minecraft.getInstance().player;
        Vec3 from = me != null ? hand(c, me, .45) : a.origin();
        Vec3 to = bound != null ? bound.getEyePosition(c.partial) : a.target();
        float life = a.piece().ticks, f = fade(t, life, 1, 6), reach = smooth(0, 8, t);
        Vec3 d = to.subtract(from);
        if (d.lengthSqr() < 1e-4) return;
        Vec3[] b = across(d);
        int n = 22;
        for (int j = 0; j < 3; j++) {
            Vec3[] p = new Vec3[n + 1];
            float[] al = new float[n + 1], w = new float[n + 1];
            for (int i = 0; i <= n; i++) {
                float k = reach * i / n, ang = k * 8 + clock * 4 + j * TWO_PI / 3, r = .26F * sin(PI * k);
                p[i] = from.add(d.scale(k)).add(b[0].scale(cos(ang) * r)).add(b[1].scale(sin(ang) * r));
                al[i] = f * (float) Math.clamp((p[i].distanceTo(c.camera()) - .6) / 1.5, 0, 1);
                w[i] = .03F;
            }
            if (c.glow()) {
                c.ribbon(p, w, j == 1 ? PALE : LILAC, scaled(al, .8F));
                c.ribbon(p, scaled(w, .35F), CORE, scaled(al, .8F));
            }
        }
        if (t > 6) iris(c, to, c.camera().subtract(to), lerp(smooth(6, 16, t), .9F, .42F), LILAC, f * smooth(6, 9, t), -clock * 1.5F, .45F);
    }

    /**
     * Persuasion: rose and lilac threads from the speaking hand, and petals of thought closing round the mind's head over
     * the talk-down's length (the scale). Everyone sees it: the mind has that long to break the line of sight.
     */
    private static void persuade(Canvas c, Active a, Entity bound, float t, float clock) {
        float windup = Math.max(4, a.scale());
        if (t > windup + 2) return;
        Vec3 to = bound != null ? bound.getEyePosition(c.partial) : a.target(), from = a.origin();
        float k = clamp(t / windup, 0, 1), f = smooth(0, 3, t) * (1 - smooth(windup, windup + 2, t));
        thread(c, from, to, new Vec3(0, .35, 0), 1, .03F, mix(LILAC, ROSE, k), f * .8F, clock, 3);
        float r = lerp(smooth(0, 1, k), 1.3F, .36F);
        float[] tint = mix(LILAC, ROSE, k);
        if (c.glow()) for (int i = 0; i < 6; i++) {
            float base = i * TWO_PI / 6 + clock * 1.6F;
            Vec3[] petal = new Vec3[7];
            for (int s = 0; s <= 6; s++) {
                float u = s / 6F, ang = base + u * .9F, rr = r * (1 - .25F * u);
                petal[s] = to.add(cos(ang) * rr, .1 + .18 * sin(PI * u), sin(ang) * rr);
            }
            c.ribbon(petal, .035F, tint, .2F * f, .8F * f);
        }
        ripple(c, to.add(0, .05, 0), UP, r, .03F, tint, f * .6F, clock, 6);
        if (k > .92F && c.glow()) c.glow(to, .9F, ROSE, (k - .92F) / .08F * f * .6F);
    }

    /** A mind won over: a rose halo over its head, three motes turning in it. */
    private static void won(Canvas c, Active a, Entity bound, float t, float clock) {
        if (bound == null) return;
        float f = fade(t, a.piece().ticks, 2, 2);
        Vec3 head = bound.getPosition(c.partial).add(0, bound.getBbHeight() + .28, 0);
        if (c.soft()) c.disc(head, UP, .42F, ROSE, .12F * f, 0);
        if (!c.glow()) return;
        c.ring(head, UP, .34F, .035F, 0, TWO_PI, ROSE, .75F * f);
        c.ring(head, UP, .34F, .012F, 0, TWO_PI, CORE, .8F * f);
        for (int i = 0; i < 3; i++) {
            float ang = clock * 2.2F + i * TWO_PI / 3;
            c.glow(head.add(cos(ang) * .34, .03 * sin(clock * 4 + i), sin(ang) * .34), .09F, PALE, f);
        }
    }

    /** Memory Walk's cast: an iris opens on the mind (or the place) and thought-waves turn back from it, rewinding. */
    private static void recall(Canvas c, Active a, Entity bound, float t, float clock) {
        Vec3 to = bound != null ? bound.getEyePosition(c.partial) : a.target();
        float life = a.piece().ticks, f = fade(t, life, 2, 8);
        thread(c, a.origin(), to, new Vec3(0, .2, 0), smooth(0, 4, t), .025F, PALE, f * .7F, clock, 2);
        iris(c, to, c.camera().subtract(to), .55F * smooth(0, 5, t), LILAC, f, -clock * 3 - t * .4F, .4F);
        for (int i = 0; i < 3; i++) {
            float k = clamp((t - i * 3) / 12, 0, 1);
            if (k > 0 && k < 1) ripple(c, to, c.camera().subtract(to), .5F + k * 1.4F, .025F, PALE, f * (1 - k), -clock * 2 - i, 5);
        }
    }

    /**
     * A segment of a walked memory: footsteps on the ground from where the mind was to where it went next, dimmer the
     * longer ago, red where it was hurt, and a ring where it stood still.
     */
    private static void memory(Canvas c, Active a, float t) {
        float s = a.scale();
        boolean hurt = s >= TelepathyGeometry.FLAG_HURT;
        float age = clamp((s - (hurt ? TelepathyGeometry.FLAG_HURT : 0) - 1) / 10, 0, 1);
        float alpha = fade(t, a.piece().ticks, 4, 60) * (1 - .45F * age);
        float[] tint = hurt ? FEAR : mix(LILAC, VIOLET, age);
        Vec3 o = a.origin().add(0, .04, 0), e = a.target().add(0, .04, 0);
        Vec3 d = e.subtract(o).multiply(1, 0, 1);
        double length = d.length();
        if (length < .3) {
            if (c.glow()) c.ring(o, UP, .35F, .03F, 0, TWO_PI, tint, alpha * .45F);
            return;
        }
        if (c.glow()) c.line(o, e, .026F, tint, alpha * .45F, alpha * .55F);
        Vec3 dir = d.normalize(), side = new Vec3(-dir.z, 0, dir.x);
        int steps = (int) Math.min(40, length / .55);
        for (int j = 0; j <= steps; j++) {
            double k = steps == 0 ? 0 : j / (double) steps;
            Vec3 step = o.lerp(e, k).add(side.scale(j % 2 == 0 ? .13 : -.13));
            if (c.soft()) c.disc(step, UP, .12F, tint, .6F * alpha, .15F * alpha);
            if (c.glow()) c.glow(step.add(0, .02, 0), .17F, tint, .5F * alpha);
        }
        if (hurt && c.glow()) c.ring(e, UP, .45F, .035F, 0, TWO_PI, FEAR, alpha * .7F);
    }

    /** Where a walked memory began: a pale echo of a body standing there, a light running slowly up and down it. */
    private static void echo(Canvas c, Active a, float t, float clock) {
        float f = fade(t, a.piece().ticks, 6, 60);
        Vec3 at = a.origin();
        if (c.soft()) {
            c.shell(at.add(0, .9, 0), .3F, .85F, .3F, PI, PALE, .2F * f, .3F, PLAIN, 0);
            c.shell(at.add(0, 1.72, 0), .21F, .21F, .21F, PI, PALE, .22F * f, .3F, PLAIN, 0);
        }
        if (!c.glow()) return;
        c.ring(at.add(0, .05, 0), UP, .5F, .03F, 0, TWO_PI, LILAC, .6F * f);
        float y = .1F + 1.8F * frac(clock * .3F);
        c.ring(at.add(0, y, 0), UP, y > 1.5F ? .22F : .34F, .02F, 0, TWO_PI, PALE, .5F * f);
    }

    /** Lost in Thought: a slow fog of thought-waves wanders round the head, motes drifting in it. */
    private static void lost(Canvas c, Active a, Entity bound, float t, float clock) {
        if (bound == null) return;
        float f = fade(t, a.piece().ticks, 2, 2);
        Vec3 head = bound.getEyePosition(c.partial).add(0, firstPerson(bound) ? -.6 : .05, 0);
        for (int i = 0; i < 2; i++) {
            float tilt = .5F + .3F * sin(clock * .7F + i * 2), spin = clock * (.6F + .3F * i) + i * PI;
            Vec3 axis = new Vec3(sin(tilt) * cos(spin), cos(tilt), sin(tilt) * sin(spin));
            ripple(c, head, axis, .55F + .05F * sin(clock * 2 + i), .025F, i == 0 ? PALE : LILAC, f * .5F, clock + i, 3);
        }
        if (c.glow()) for (int i = 0; i < (minimal() ? 3 : 6); i++) {
            float ang = clock * .8F + hash(a.seed(), i) * TWO_PI, rr = .45F + hash(a.seed(), i + 9) * .3F;
            c.glow(head.add(cos(ang) * rr, sin(clock * 1.3F + i) * .25, sin(ang) * rr), .07F, PALE, f * .8F);
        }
        if (c.soft()) c.sphere(head, .62F, VIOLET, .05F * f);
    }

    /** Waking Fright (and a talk-down shaken off, at half size): the false thing bursts into shards, a red flash round the head. */
    private static void fright(Canvas c, Active a, Entity bound, float t) {
        float life = a.piece().ticks, f = 1 - smooth(4, life, t), size = Math.min(1, a.scale());
        Vec3 head = bound != null ? bound.getEyePosition(c.partial) : a.origin();
        Vec3 burst = size < 1 ? head : a.target().add(0, .9, 0);
        int shards = (int) ((minimal() ? 5 : 10) * size);
        float fly = smooth(0, 10, t) * 1.5F * size;
        if (c.soft()) for (int i = 0; i < shards; i++) {
            float yaw = hash(a.seed(), i) * TWO_PI, pitch = (hash(a.seed(), i + 20) - .4F) * 1.6F;
            Vec3 dir = new Vec3(cos(yaw) * cos(pitch), sin(pitch), sin(yaw) * cos(pitch));
            Vec3 base = burst.add(dir.scale(fly));
            c.shard(base, base.add(dir.scale(.18 * size)), .05F * size, 3, t * .3F + i, i % 3 == 0 ? FEAR : VIOLET, .7F * f);
        }
        if (c.glow()) {
            float k = smooth(0, 8, t);
            c.ring(head, c.camera().subtract(head), (.3F + 1.1F * k) * size, .05F, 0, TWO_PI, FEAR, .8F * (1 - k));
            if (t < 4) c.glow(burst, .9F * size, PALE, 1 - t / 4);
        }
    }

    /** Hive Link: a thread arcs from the linking mind to each friend's head, and a halo snaps into place there. */
    private static void hiveLink(Canvas c, Active a, Entity bound, float t, float clock) {
        Vec3 to = bound != null ? bound.getEyePosition(c.partial) : a.target();
        float life = a.piece().ticks, f = fade(t, life, 1, 10);
        thread(c, a.origin(), to, new Vec3(0, 1.2, 0), smooth(0, 8, t), .03F, LILAC, f, clock, 2);
        if (t > 7 && bound != null) {
            Vec3 head = bound.getPosition(c.partial).add(0, bound.getBbHeight() + .32, 0);
            float snap = smooth(7, 11, t);
            if (c.glow()) c.ring(head, UP, lerp(snap, .9F, .3F), .035F, 0, TWO_PI, LILAC, f * snap);
        }
    }

    /** A linked friend's halo, which only the link sees: three beads that swell while they speak, red while they're in danger. */
    private static void halo(Canvas c, Active a, Entity bound, float t, float clock) {
        if (bound == null || !c.glow()) return;
        float v = a.scale() - 1;
        boolean danger = v >= TelepathyGeometry.FLAG_DANGER;
        if (danger) v -= TelepathyGeometry.FLAG_DANGER;
        boolean speaking = v >= TelepathyGeometry.FLAG_SPEAKING;
        float f = fade(t, a.piece().ticks, 2, 2);
        Vec3 head = bound.getPosition(c.partial).add(0, bound.getBbHeight() + .32, 0);
        float[] tint = danger ? FEAR : LILAC;
        float pulse = danger ? .75F + .25F * sin(clock * 9) : 1;
        c.ring(head, UP, .3F, .025F, 0, TWO_PI, tint, .6F * f * pulse);
        for (int i = 0; i < 3; i++) {
            float ang = clock * 1.4F + i * TWO_PI / 3, swell = speaking ? .12F + .04F * sin(clock * 14 + i * 2) : .07F;
            c.glow(head.add(cos(ang) * .3, 0, sin(ang) * .3), swell, speaking ? CORE : PALE, f);
        }
        if (speaking) murmur(c, head.add(0, .35, 0), c.camera().subtract(head), .22F, PALE, f * .7F);
    }

    /** Mind Network: a telepath's voice inside your head, a thread curling round it (low at the edge of your own view). */
    private static void project(Canvas c, Active a, Entity bound, float t, float clock) {
        if (bound == null) return;
        float f = fade(t, a.piece().ticks, 2, 3);
        boolean me = firstPerson(bound);
        Vec3 head = bound.getEyePosition(c.partial).add(0, me ? -.55 : 0, 0);
        ripple(c, head, new Vec3(.25, 1, .1), me ? .9F : .5F, .02F, PALE, f * (me ? .4F : .6F), clock * 2, 4);
        if (c.glow()) for (int i = 0; i < 2; i++) {
            float ang = clock * 3 + i * PI;
            c.glow(head.add(cos(ang) * (me ? .9 : .5), .1 * sin(clock * 5 + i), sin(ang) * (me ? .9 : .5)), .06F, CORE, f * .8F);
        }
    }

    // ------------------------------------------------------------------
    // The styles
    // ------------------------------------------------------------------

    /** Psychic Storm: threads of thought wheel round the point under a great iris looking down, synapses firing through it. */
    private static void storm(Canvas c, Active a, float t, float clock) {
        float f = fade(t, a.piece().ticks, 12, 20), r = a.scale();
        Vec3 o = a.target(), top = o.add(0, 3.8, 0);
        if (c.glow()) for (int k = 0; k < (minimal() ? 4 : 7); k++) {
            int n = 30;
            Vec3[] p = new Vec3[n + 1];
            float[] al = new float[n + 1], w = new float[n + 1];
            for (int i = 0; i <= n; i++) {
                float u = i / (float) n, ang = k * TWO_PI / 7 - t * .12F + u * 1.4F * TWO_PI, rr = lerp(u, r, .5F);
                p[i] = o.add(cos(ang) * rr, u * 3.8, sin(ang) * rr);
                al[i] = .65F * f * sin(PI * u) * (float) Math.clamp((p[i].distanceTo(c.camera()) - 1.5) / 3, 0, 1);
                w[i] = .07F;
            }
            c.ribbon(p, w, k % 2 == 0 ? LILAC : PALE, al);
        }
        iris(c, top, UP, 1.5F + .15F * sin(t * .4F), LILAC, f, clock * .8F, .35F);
        if (c.glow()) {
            int flick = (int) (t / 3);
            for (int k = 0; k < 4; k++) {
                float ang = hash(a.seed() + flick, k) * TWO_PI, rr = r * hash(a.seed() + flick, k + 4);
                Vec3 p = o.add(cos(ang) * rr, .5 + hash(a.seed() + flick, k + 8) * 3, sin(ang) * rr);
                c.bolt(top, p, a.seed() + flick * 17 + k, .4F, .035F, LILAC, .65F * f);
            }
        }
        for (int i = 0; i < 2; i++) {
            float k = frac(t * .03F + i * .5F);
            ripple(c, o.add(0, .06, 0), UP, r * k, .05F, LILAC, f * .6F * (1 - k), clock + i, 7);
        }
        if (c.soft()) c.disc(o.add(0, .05, 0), UP, r, SHADOW, .25F * f, 0);
    }

    /** Blind Spot: thought-waves spread off them and a fading echo of them stays behind where they were. */
    private static void blindSpot(Canvas c, Active a, Entity bound, float t, float clock) {
        float life = a.piece().ticks, f = 1 - t / life;
        Vec3 o = bound != null ? bound.getPosition(c.partial) : a.origin();
        if (c.soft()) c.shell(a.origin().add(0, .95, 0), .34F, .95F, .34F, PI, PALE, .32F * f, .3F, PLAIN, 0);
        for (int k = 0; k < 3; k++) {
            float p = clamp((t - k * 5) / 18, 0, 1);
            if (p > 0) ripple(c, o.add(0, .08 + k * .3, 0), UP, .5F + p * 9, .06F, LILAC, .75F * (1 - p), clock + k, 5);
        }
    }

    /** Hive Mind: a thread joins each friend's head to the hive above where it was made, with a halo on both ends. */
    private static void hiveMind(Canvas c, Active a, Entity bound, float t, float clock) {
        float f = fade(t, a.piece().ticks, 10, 20);
        Vec3 head = (bound != null ? bound.getPosition(c.partial) : a.origin()).add(0, 2.0, 0), hub = a.origin().add(0, 3.2, 0);
        thread(c, head, hub, new Vec3(0, .8, 0), 1, .04F, LILAC, f * .8F, clock, 2);
        if (!c.glow()) return;
        c.ring(head.add(0, -.1, 0), UP, .32F, .03F, clock * 2, clock * 2 + PI * 1.6F, PALE, .8F * f);
        c.glow(hub, .6F, LILAC, .55F * f);
        iris(c, hub, UP, .55F, LILAC, f * .8F, -clock, .3F);
    }

    // ------------------------------------------------------------------

    /** 0 within a block and a half of the camera, 1 from three and a half out: things that pass the lens don't fill it. */
    private static float near(Canvas c, Vec3 at) { return (float) Math.clamp((at.distanceTo(c.camera()) - 1.5) / 2, 0, 1); }

    private static boolean firstPerson(Entity e) {
        Minecraft mc = Minecraft.getInstance();
        return e == mc.player && mc.options.getCameraType() == CameraType.FIRST_PERSON;
    }

    /** The hand the player sees: the body anchor in third person, a camera-space point in first. */
    private static Vec3 hand(Canvas c, Player owner, double reach) {
        if (!firstPerson(owner)) return TelepathyGeometry.hand(owner, c.partial, reach);
        Vec3 look = owner.getViewVector(c.partial);
        Vec3 right = Vec3.directionFromRotation(0, owner.getViewYRot(c.partial) + 90);
        Vec3 cameraUp = right.cross(look).normalize();
        return c.camera().add(look.scale(reach + .13))
                .add(right.scale(owner.getMainArm() == HumanoidArm.RIGHT ? .39 : -.39)).subtract(cameraUp.scale(.43));
    }

    private static float[] scaled(float[] v, float k) {
        float[] out = new float[v.length];
        for (int i = 0; i < v.length; i++) out[i] = v[i] * k;
        return out;
    }

    private static boolean minimal() { return Minecraft.getInstance().options.particles().get() == ParticleStatus.MINIMAL; }
}
