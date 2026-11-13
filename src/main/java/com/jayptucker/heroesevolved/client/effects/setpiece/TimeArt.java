package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient.Active;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.time.TimeGeometry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.*;

/**
 * Space-Time's skill tree, in the power's grammar: clockwork made of light. Every piece is built from three primitives:
 * a clock {@link #face} (two rings, twelve marks with every third longer, and two hands), a {@link #seam} (a three-layer
 * line of folded space) and hanging {@link #motes}. Colour and the hands carry the meaning:
 * <ul>
 *   <li>stopped time is cold violet and ice, hands held at ten past ten, motes perfectly still;</li>
 *   <li>hurried time is amber and gold, hands racing forward, sand pouring;</li>
 *   <li>undone time is violet, hands spinning backward;</li>
 *   <li>space is a gold seam and a hole that pinches shut.</li>
 * </ul>
 */
final class TimeArt {
    private static final float[] GOLD = rgb(0xFFD27A), AMBER = rgb(0xE8A43C), VIOLET = rgb(0x9A7CFF), DEEP = rgb(0x5B3FD1),
            PALE = rgb(0xF4EBFF), ICE = rgb(0xC9C2FF);
    /** Dark outlines drawn with normal blending behind the light, so the colours hold against a bright sky. */
    private static final float[] DUSK = rgb(0x2E1F6B), BRONZE = rgb(0x6B4310);
    /** Ten past ten, the hands of a stopped clock: hour and minute as fractions of a turn. */
    private static final float STOP_HOUR = 10 / 12F + 1 / 72F, STOP_MINUTE = 10 / 60F;
    private static final Set<Piece> RELEASED = Set.of(Piece.TIME_RELEASE);

    private TimeArt() {}

    static void draw(Canvas c, Active a, float t) {
        if (c.solid()) return;
        switch (a.piece()) {
            case TIME_STASIS -> stasis(c, a, t);
            case TIME_RELEASE -> release(c, a, t);
            case TIME_ANCHOR -> anchor(c, a, t);
            case TIME_SECOND -> second(c, a, t);
            case TIME_SEAL -> seal(c, a, t);
            case TIME_SEAL_BREAK -> sealBreak(c, a, t);
            case TIME_CUT -> cut(c, a, t);
            case TIME_SNAP -> snap(c, a, t);
            case TIME_MARK -> mark(c, a, t);
            case TIME_FORWARD -> forward(c, a, t);
            default -> { }
        }
    }

    // ------------------------------------------------------------------
    // Primitives
    // ------------------------------------------------------------------

    /**
     * A clock face across {@code axis}: hands at {@code hour} and {@code minute} (fractions of a turn). The rim is a
     * halo, a body and a core; the marks and hands take the body's colour with pale cores.
     */
    private static void face(Canvas c, Vec3 at, Vec3 axis, float r, float hour, float minute, float[] body, float alpha) {
        if (alpha <= .003F || r <= .02F) return;
        float k = Math.min(1, r / 1.5F) * .6F + .4F;
        float h = -hour * Mth.TWO_PI, m = -minute * Mth.TWO_PI;
        if (c.soft()) {
            // A dark rim and hands behind the light: in daylight the glow alone washes out to white.
            float[] dark = shade(body);
            c.ring(at, axis, r, .11F * k, 0, Mth.TWO_PI, dark, .32F * alpha);
            c.ring(at, axis, r * .86F, .05F * k, 0, Mth.TWO_PI, dark, .2F * alpha);
            c.spoke(at, axis, h, 0, r * .5F, .1F * k, dark, .3F * alpha);
            c.spoke(at, axis, m, 0, r * .78F, .08F * k, dark, .3F * alpha);
            return;
        }
        if (!c.glow()) return;
        c.ring(at, axis, r, .2F * k, 0, Mth.TWO_PI, DEEP, .35F * alpha);
        c.ring(at, axis, r, .055F * k, 0, Mth.TWO_PI, body, .85F * alpha);
        c.ring(at, axis, r, .018F * k, 0, Mth.TWO_PI, PALE, .75F * alpha);
        c.ring(at, axis, r * .86F, .03F * k, 0, Mth.TWO_PI, body, .55F * alpha);
        boolean fine = !minimal();
        for (int i = 0; i < 12; i++) {
            if (!fine && i % 3 != 0) continue;
            boolean major = i % 3 == 0;
            float ang = i * Mth.TWO_PI / 12;
            c.spoke(at, axis, ang, r * (major ? .66F : .74F), r * .84F, (major ? .05F : .03F) * k, major ? PALE : body, .9F * alpha);
        }
        c.spoke(at, axis, h, 0, r * .5F, .07F * k, body, .9F * alpha);
        c.spoke(at, axis, h, 0, r * .5F, .022F * k, PALE, alpha);
        c.spoke(at, axis, m, 0, r * .78F, .05F * k, body, .9F * alpha);
        c.spoke(at, axis, m, 0, r * .78F, .018F * k, PALE, alpha);
        c.glow(at, .16F * k + .04F, PALE, .8F * alpha);
    }

    private static float[] shade(float[] body) { return body == GOLD || body == AMBER ? BRONZE : DUSK; }

    /** A line of folded space: deep halo, violet body, pale core. */
    private static void seam(Canvas c, Vec3 a, Vec3 b, float width, float[] body, float alpha) {
        if (alpha <= .003F) return;
        if (c.soft()) { c.line(a, b, width * 1.8F, shade(body), .3F * alpha, .3F * alpha); return; }
        if (!c.glow()) return;
        c.line(a, b, width * 3.2F, DEEP, .4F * alpha, .4F * alpha);
        c.line(a, b, width, body, .85F * alpha, .85F * alpha);
        c.line(a, b, width * .35F, PALE, alpha, alpha);
    }

    /**
     * Motes round a point: {@code fall} 0 hangs them still (stopped time), more lets them drift down at that many blocks a
     * tick (hurried sand). Deterministic from the seed.
     */
    private static void motes(Canvas c, Vec3 at, float r, float h, int seed, int n, float fall, float time, float[] col, float alpha) {
        if (!c.glow() || alpha <= .003F) return;
        if (minimal()) n = Math.max(2, n / 3);
        for (int i = 0; i < n; i++) {
            float ang = hash(seed, i) * Mth.TWO_PI, rr = r * Mth.sqrt(hash(seed, i + 40));
            float y = hash(seed, i + 80) * h;
            if (fall != 0) y = ((y - fall * time) % h + h) % h;
            c.glow(at.add(Mth.cos(ang) * rr, y, Mth.sin(ang) * rr), .07F + hash(seed, i + 120) * .05F, col, alpha * (.5F + hash(seed, i + 160) * .5F));
        }
    }

    // ------------------------------------------------------------------
    // Stillness
    // ------------------------------------------------------------------

    /**
     * Selective Stasis (and anything a seal or Moment's Edge holds): a cold clock under the creature with its hands
     * stopped at ten past ten, a crown face over its head, and a cage of three ice rings that do not turn. Motes hang
     * round it, perfectly still. Stops drawing the moment the release arrives.
     */
    private static void stasis(Canvas c, Active a, float t) {
        if (SetPieceClient.superseded(a, RELEASED, a.start() + (long) t)) return;
        float h = Math.max(.6F, a.scale()), r = Math.max(.6F, h * .45F);
        Vec3 o = a.at(c);
        // Dimmed when it is right by the camera: Moment's Edge lands you a step behind a caged creature.
        float f = fade(t, a.piece().ticks, 2, 2) * near(c, o.add(0, h * .5, 0), 1, 3);
        face(c, o.add(0, .04, 0), UP, r * 1.35F, STOP_HOUR, STOP_MINUTE, VIOLET, .8F * f);
        face(c, o.add(0, h + .25, 0), UP, r * .55F, STOP_HOUR, STOP_MINUTE, ICE, .7F * f);
        for (int k = 0; k < 3; k++) {
            float tilt = k * Mth.PI / 3;
            Vec3 axis = new Vec3(Mth.cos(tilt), .35, Mth.sin(tilt)).normalize();
            Vec3 mid = o.add(0, h * .5, 0);
            if (c.soft()) c.ring(mid, axis, h * .62F, .07F, 0, Mth.TWO_PI, DUSK, .3F * f);
            else if (c.glow()) {
                c.ring(mid, axis, h * .62F, .12F, 0, Mth.TWO_PI, DEEP, .22F * f);
                c.ring(mid, axis, h * .62F, .035F, 0, Mth.TWO_PI, VIOLET, .7F * f);
                c.ring(mid, axis, h * .62F, .012F, 0, Mth.TWO_PI, ICE, .7F * f);
            }
        }
        motes(c, o, r * 1.2F, h * 1.1F, a.entity() * 31 + 7, 10, 0, t, ICE, .6F * f);
        if (c.soft()) c.disc(o.add(0, .03, 0), UP, r * 1.35F, DEEP, .16F * f, 0);
    }

    /**
     * Stopped time lets go: the clock under them cracks outward, its hands race on, and a gold slash for each stored
     * blow bursts from them at once.
     */
    private static void release(Canvas c, Active a, float t) {
        float life = a.piece().ticks, f = fade(t, life, 0, 9);
        Vec3 o = a.at(c);
        int hits = Mth.clamp((int) a.scale(), 1, 8);
        float burst = 1 - (float) Math.pow(1 - smooth(0, 6, t), 3);
        face(c, o.add(0, .04, 0), UP, .8F + 1.6F * burst, STOP_HOUR + t * .06F, STOP_MINUTE + t * .7F, GOLD, f * (1 - burst * .5F));
        if (!c.glow()) return;
        Vec3 mid = o.add(0, 1, 0);
        for (int i = 0; i < hits; i++) {
            float ang = hash(a.seed(), i) * Mth.TWO_PI, rise = (hash(a.seed(), i + 9) - .5F) * 1.2F;
            Vec3 dir = new Vec3(Mth.cos(ang), rise, Mth.sin(ang)).normalize();
            Vec3 tip = mid.add(dir.scale(.4 + 1.5 * burst));
            c.line(mid.add(dir.scale(.25 * burst)), tip, .1F, AMBER, .5F * f, 0);
            c.line(mid.add(dir.scale(.25 * burst)), tip, .03F, PALE, f, 0);
        }
        c.glow(mid, 1.3F * (1 - smooth(0, 5, t)) + .2F, PALE, .9F * f);
        c.ring(mid, UP, .5F + 2.4F * burst, .08F, 0, Mth.TWO_PI, GOLD, .7F * f * (1 - burst));
    }

    /**
     * Stasis Seal: a sphere of stopped time, faint as glass, with a clock face round its equator whose hands are stopped
     * and motes hanging inside. Stops drawing as soon as it is thrown.
     */
    private static void seal(Canvas c, Active a, float t) {
        for (Vec3 at : SetPieceClient.where(Piece.TIME_SEAL_BREAK)) if (at.equals(a.origin())) return;
        float f = fade(t, a.piece().ticks, 2, 2), r = a.scale();
        Vec3 o = a.origin();
        face(c, o, UP, r, STOP_HOUR, STOP_MINUTE, VIOLET, .9F * f);
        if (c.glow()) {
            c.shell(o, r, r, r, Mth.PI, ICE, .1F * f, .35F, PLAIN, 0);
            c.ring(o, new Vec3(1, 0, 0), r, .03F, 0, Mth.TWO_PI, ICE, .4F * f);
            c.ring(o, new Vec3(0, 0, 1), r, .03F, 0, Mth.TWO_PI, ICE, .4F * f);
        }
        motes(c, o.add(0, -r * .8, 0), r * .75F, r * 1.6F, (int) (o.x * 13 + o.z * 7), 14, 0, t, ICE, .7F * f);
        if (c.soft()) c.disc(o, UP, r, DEEP, .12F * f, 0);
    }

    /** The seal thrown: the sphere bursts along your aim, and three rings fly out after what it held. */
    private static void sealBreak(Canvas c, Active a, float t) {
        float life = a.piece().ticks, f = fade(t, life, 0, 8), r = a.scale();
        Vec3 o = a.origin(), aim = a.target().subtract(o).normalize();
        float burst = 1 - (float) Math.pow(1 - smooth(0, 7, t), 3);
        face(c, o, UP, r * (1 + .3F * burst), STOP_HOUR + t * .05F, STOP_MINUTE + t * .6F, GOLD, f * (1 - burst * .6F));
        seam(c, o, o.add(aim.scale(1 + 7 * burst)), .05F, GOLD, .8F * f * (1 - burst * .7F));
        if (!c.glow()) return;
        c.shell(o, r * (1 + .5F * burst), r * (1 + .5F * burst), r * (1 + .5F * burst), Mth.PI, ICE, .14F * f * (1 - burst), .5F, PLAIN, 0);
        for (int k = 0; k < 3; k++) {
            float go = smooth(k * 2, k * 2 + 9, t);
            if (go <= 0) continue;
            Vec3 at = o.add(aim.scale(go * 7));
            float rr = r * (.8F - .2F * k) * (1 - go * .5F);
            c.ring(at, aim, rr, .14F, 0, Mth.TWO_PI, DEEP, .3F * f * (1 - go));
            c.ring(at, aim, rr, .05F, 0, Mth.TWO_PI, VIOLET, .8F * f * (1 - go));
            c.ring(at, aim, rr, .016F, 0, Mth.TWO_PI, PALE, f * (1 - go));
        }
        c.glow(o, r * .7F * (1 - smooth(0, 5, t)), PALE, .8F * f);
    }

    // ------------------------------------------------------------------
    // Wayfinder
    // ------------------------------------------------------------------

    /**
     * One cut of Moment's Edge, or a Behind You strike: a crescent of gold light opens across the target, ticked like a
     * clock's rim, with a pale core. A second cut crosses the first. A replayed blow (Era's End) is a violet ghost of one.
     */
    private static void cut(Canvas c, Active a, float t) {
        if (!c.glow() && !c.soft()) return;
        boolean ghost = a.scale() > TimeGeometry.REPLAY;
        int cuts = Mth.clamp((int) (ghost ? a.scale() - TimeGeometry.REPLAY : a.scale()), 1, 2);
        Vec3 o = a.target(), from = a.origin();
        // Dimmed near the camera, or the cut you just made fills your own view.
        float close = near(c, o, 1.2F, 3.5F);
        float life = a.piece().ticks, f = fade(t, life, 0, 7) * (ghost ? .7F : 1) * close * close;
        Vec3 dir = o.subtract(from);
        dir = dir.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : dir.normalize();
        Vec3 side = dir.cross(UP);
        side = side.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 up = side.cross(dir).normalize();
        float open = 1 - (float) Math.pow(1 - smooth(0, 3, t), 3);
        float[] body = ghost ? VIOLET : GOLD, halo = ghost ? DEEP : AMBER;
        for (int k = 0; k < cuts; k++) {
            float tilt = (k == 0 ? .55F : -.6F) + hash(a.seed(), k) * .2F;
            Vec3 u = up.scale(Mth.cos(tilt)).add(side.scale(Mth.sin(tilt)));
            Vec3 v = u.cross(dir).normalize();
            int n = 14;
            Vec3[] pts = new Vec3[n];
            float[] w = new float[n], al = new float[n], wc = new float[n];
            for (int i = 0; i < n; i++) {
                float q = i / (float) (n - 1), ang = (-1.2F + 2.4F * q) * open;
                pts[i] = o.add(v.scale(Mth.sin(ang) * 1.05)).add(u.scale(Mth.cos(ang) * .35 - .1)).add(dir.scale(-Mth.cos(ang) * .3));
                float taper = Mth.sin(q * Mth.PI);
                w[i] = .16F * taper; wc[i] = .04F * taper; al[i] = f * taper;
            }
            if (c.soft()) {
                c.ribbon(pts, scaled(w, 1.5F), ghost ? DUSK : BRONZE, scaled(al, .35F));
                continue;
            }
            c.ribbon(pts, scaled(w, 2.2F), halo, scaled(al, .35F));
            c.ribbon(pts, w, body, al);
            c.ribbon(pts, wc, PALE, al);
            if (!minimal()) for (int i = 2; i < n - 2; i += 3) {
                Vec3 p = pts[i], out = p.subtract(o).normalize();
                c.line(p, p.add(out.scale(.14)), .025F, PALE, .8F * f, 0);
            }
        }
        if (c.glow()) c.glow(o, .5F * (1 - smooth(0, 4, t)) + .1F, PALE, .7F * f);
    }

    /** 0.3 at {@code close} blocks from the camera or nearer, rising to 1 at {@code clear}. */
    private static float near(Canvas c, Vec3 at, float close, float clear) {
        double dx = at.x - c.cx, dy = at.y - c.cy, dz = at.z - c.cz;
        return .3F + .7F * Mth.clamp(((float) Math.sqrt(dx * dx + dy * dy + dz * dz) - close) / (clear - close), 0, 1);
    }

    private static float[] scaled(float[] v, float k) {
        float[] out = new float[v.length];
        for (int i = 0; i < v.length; i++) out[i] = v[i] * k;
        return out;
    }

    /**
     * Snap Shut: the hole you left pinches closed. Violet rings fall in on it, three arms of a spiral wind into the
     * middle, and it closes in a flash with a last ripple; a dark core shows where space is missing.
     */
    private static void snap(Canvas c, Active a, float t) {
        float life = a.piece().ticks, f = fade(t, life, 1, 4), r = a.scale();
        Vec3 o = a.origin().add(0, .9, 0);
        float close = smooth(0, 8, t);
        if (c.soft()) {
            c.glow(o, .55F * (1 - close) + .05F, DEEP, .55F * f * (1 - smooth(7, 9, t)));
            return;
        }
        if (!c.glow()) return;
        for (int k = 0; k < 3; k++) {
            float q = smooth(k * 1.5F, 7 + k * 1.5F, t);
            if (q <= 0 || q >= 1) continue;
            c.ring(o, UP, r * (1 - q), .05F, 0, Mth.TWO_PI, VIOLET, .8F * f * Mth.sin(q * Mth.PI));
            c.ring(o, UP, r * (1 - q), .14F, 0, Mth.TWO_PI, DEEP, .25F * f);
        }
        int steps = 24;
        for (int arm = 0; arm < (minimal() ? 1 : 3); arm++) {
            Vec3[] pts = new Vec3[steps];
            for (int i = 0; i < steps; i++) {
                float q = i / (float) (steps - 1), ang = arm * Mth.TWO_PI / 3 + q * 4 + t * .5F;
                float rr = r * (1 - q) * (1 - close * .9F);
                pts[i] = o.add(Mth.cos(ang) * rr, (q - .5F) * .3F * (1 - close), Mth.sin(ang) * rr);
            }
            c.ribbon(pts, .05F, VIOLET, .1F * f, .8F * f);
        }
        float flash = smooth(7, 8, t) * (1 - smooth(8, 12, t));
        if (flash > 0) {
            c.glow(o, 1.1F, PALE, flash * f);
            c.ring(o, UP, .3F + 2 * smooth(8, 13, t), .06F, 0, Mth.TWO_PI, GOLD, .7F * f * (1 - smooth(8, 13, t)));
        }
    }

    /**
     * Spatial Anchor: a gold clock face presses down into the ground and settles, its hands showing the hour of the day
     * (in the piece's scale), with a ring pulsing outward as it lands.
     */
    private static void anchor(Canvas c, Active a, float t) {
        float life = a.piece().ticks, f = fade(t, life, 2, 10);
        float hour = Math.max(0, a.scale() - 1) % 12, settle = smooth(0, 6, t);
        Vec3 o = a.origin().add(0, .04 + (1 - settle) * .8, 0);
        float r = 1.2F + (1 - settle) * .5F;
        face(c, o, UP, r, hour / 12F, hour % 1, GOLD, f);
        if (!c.glow()) return;
        float pulse = smooth(5, 16, t);
        if (pulse > 0 && pulse < 1) c.ring(a.origin().add(0, .05, 0), UP, 1.2F + 2.2F * pulse, .07F, 0, Mth.TWO_PI, AMBER, .7F * f * (1 - pulse));
        c.line(o, o.add(0, 1.6 * (1 - smooth(4, 14, t)), 0), .06F, GOLD, .7F * f, 0);
    }

    // ------------------------------------------------------------------
    // Historian
    // ------------------------------------------------------------------

    /** Second Chance: violet rings close in on you from wide to tight, over a clock whose hands spin backward. */
    private static void second(Canvas c, Active a, float t) {
        float life = a.piece().ticks, f = fade(t, life, 1, 7);
        Vec3 o = a.at(c);
        face(c, o.add(0, .04, 0), UP, 1.1F, .4F - t * .08F, .2F - t * .9F, VIOLET, f);
        if (!c.glow()) return;
        for (int k = 0; k < 3; k++) {
            float q = smooth(k * 2, k * 2 + 9, t);
            if (q <= 0 || q >= 1) continue;
            float rr = 2.6F * (1 - q) + .35F;
            Vec3 at = o.add(0, .3 + k * .55, 0);
            c.ring(at, UP, rr, .16F, 0, Mth.TWO_PI, DEEP, .3F * f);
            c.ring(at, UP, rr, .05F, 0, Mth.TWO_PI, VIOLET, .85F * f * Mth.sin(q * Mth.PI));
            c.ring(at, UP, rr, .016F, 0, Mth.TWO_PI, PALE, f * Mth.sin(q * Mth.PI));
        }
        float flash = smooth(8, 11, t) * (1 - smooth(11, 18, t));
        if (flash > 0) c.glow(o.add(0, 1, 0), .9F, PALE, flash * f);
    }

    /**
     * Foreknowledge's mark, seen only by you: a small violet clock over their head, turned toward you, its hands spinning
     * backward, and a thin ring at their feet.
     */
    private static void mark(Canvas c, Active a, float t) {
        if (!c.glow() && !c.soft()) return;
        float f = fade(t, a.piece().ticks, 2, 2);
        Vec3 o = a.at(c), top = o.add(0, a.scale() + .55, 0);
        Vec3 toward = new Vec3(c.cx - top.x, 0, c.cz - top.z);
        toward = toward.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : toward.normalize();
        float spin = (c.level.getGameTime() + c.partial) * .05F;
        face(c, top, toward, .3F, -spin * .1F, -spin, VIOLET, .9F * f);
        if (c.glow()) c.ring(o.add(0, .05, 0), UP, .6F, .03F, 0, Mth.TWO_PI, VIOLET, .5F * f);
    }

    /**
     * Fast Forward. On an enemy: an amber clock round them whose hands race forward, ring after ring of years climbing
     * up their body, and sand pouring. On a friend the same in gold, gentler. With nothing aimed: a wide gold face on the
     * ground with racing hands, and rings of growth spreading over it.
     */
    private static void forward(Canvas c, Active a, float t) {
        float life = a.piece().ticks, f = fade(t, life, 2, 8), s = a.scale();
        Vec3 o = a.at(c);
        if (s >= TimeGeometry.GROWTH && s < TimeGeometry.FRIEND) {
            float r = s - TimeGeometry.GROWTH;
            face(c, o.add(0, .05, 0), UP, r, t * .08F, t * .9F, GOLD, f);
            if (c.glow()) for (int k = 0; k < 3; k++) {
                float q = smooth(k * 4, k * 4 + 12, t);
                if (q > 0 && q < 1) c.ring(o.add(0, .06, 0), UP, r * q, .07F, 0, Mth.TWO_PI, AMBER, .7F * f * (1 - q));
            }
            motes(c, o.add(0, .2, 0), r, 1.6F, a.seed(), 18, -.08F, t, GOLD, .6F * f);
            return;
        }
        boolean friend = s >= TimeGeometry.FRIEND;
        float[] body = friend ? GOLD : AMBER;
        float race = friend ? .5F : 1.1F;
        face(c, o.add(0, .04, 0), UP, 1.1F, t * .09F * race, t * race, body, f);
        if (c.glow()) for (int k = 0; k < 5; k++) {
            float q = ((t * .09F * race + k / 5F) % 1);
            Vec3 at = o.add(0, .2 + q * 2, 0);
            float a0 = f * Mth.sin(q * Mth.PI);
            c.ring(at, UP, .75F - q * .35F, .045F, 0, Mth.TWO_PI, body, .8F * a0);
            c.ring(at, UP, .75F - q * .35F, .015F, 0, Mth.TWO_PI, PALE, a0);
        }
        motes(c, o, .7F, 2.2F, a.seed(), 14, friend ? .03F : .12F, t, friend ? PALE : GOLD, .7F * f);
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
