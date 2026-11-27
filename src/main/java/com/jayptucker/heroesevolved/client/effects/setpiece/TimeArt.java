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
            case FINISH_LOOP -> loop(c, a, t);
            default -> { }
        }
    }

    /**
     * Closed Loop (the finisher, {@code time/ClosedLoop}): a clock face stands across the foe, its hands racing forward in
     * amber while two of you fold in either side (a gold seam joins the two places), then it snaps still at ten past ten,
     * violet and ice, the moment the later you goes back. With three of you a second, smaller face over the foe's head
     * runs backward. Origin the foe's feet, target where you fold in, scale the foe's height (+ 32 for three).
     */
    private static void loop(Canvas c, Active a, float t) {
        boolean three = a.scale() >= com.jayptucker.heroesevolved.time.ClosedLoop.THREE;
        float h = Math.max(.6F, a.scale() - (three ? com.jayptucker.heroesevolved.time.ClosedLoop.THREE : 0));
        float life = a.piece().ticks, f = fade(t, life, 3, 8);
        Vec3 feet = a.origin(), near = a.target();
        Vec3 toNear = near.subtract(feet).multiply(1, 0, 1);
        toNear = toNear.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : toNear.normalize();
        Vec3 far = feet.subtract(near.subtract(feet).multiply(1, 0, 1));
        Vec3 chest = feet.add(0, h * .6, 0);
        float r = Math.max(1.05F, h * .68F);
        float stopAt = com.jayptucker.heroesevolved.time.ClosedLoop.GONE;
        boolean stopped = t >= stopAt;
        // Racing forward, faster and faster, until the loop closes; then held at ten past ten.
        float run = Math.min(t, stopAt);
        float minute = stopped ? STOP_MINUTE : run * run * .004F + run * .05F, hour = stopped ? STOP_HOUR : minute / 12F;
        float snap = smooth(stopAt, stopAt + 2, t);
        face(c, chest.subtract(toNear.scale(.35)), toNear, r * (1 + .08F * (1 - snap) * (stopped ? 1 : 0)), hour, minute,
                stopped ? VIOLET : AMBER, f * (.75F + .25F * snap));
        if (stopped) {
            face(c, chest.subtract(toNear.scale(.37)), toNear, r * .72F, hour, minute, ICE, f * .5F * snap);
            motes(c, feet, r * .9F, h * 1.1F, a.seed(), 10, 0, t, ICE, f * .6F * snap);
            if (c.glow()) c.glow(chest, .9F * (1 - smooth(stopAt, stopAt + 4, t)), PALE, f * (1 - smooth(stopAt, stopAt + 4, t)));
        } else {
            motes(c, feet, r * .9F, h * 1.1F, a.seed(), 8, .05F, t, GOLD, f * .5F);
        }
        // The fold: a gold seam through the foe joins the two places you arrive, as you arrive.
        float seam = smooth(4, 6, t) * (1 - smooth(10, 14, t));
        if (seam > 0) seam(c, near.add(0, 1, 0), far.add(0, 1, 0), .05F, GOLD, f * seam);
        if (three) {
            Vec3 crown = feet.add(0, h + .5, 0);
            float back = -t * .12F;
            face(c, crown, UP, .55F, back / 12F, back, VIOLET, f * smooth(5, 8, t));
        }
    }

    /** The alternate finishers ({@code time/TimeFinishers}): Stopped Clock and Unwound. */
    static void alternates(Canvas c, Active a, float t) {
        if (c.solid()) return;
        if (a.piece() == com.jayptucker.heroesevolved.styles.Piece.FINISH_STOPPED) stopped(c, a, t);
        else unwound(c, a, t);
    }

    /**
     * Stopped Clock: a violet face across the foe whose hands race and stop at ten past ten; six cuts hang frozen across
     * the body, each one cracking the face; time lurches on (amber), and every cut flares at once and the face breaks.
     * Origin the foe's feet, target yours, scale its height.
     */
    private static void stopped(Canvas c, Active a, float t) {
        float h = Math.max(.6F, a.scale()), f = fade(t, a.piece().ticks, 2, 8);
        Vec3 feet = a.origin(), toMe = a.target().subtract(feet).multiply(1, 0, 1);
        toMe = toMe.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : toMe.normalize();
        Vec3 right = new Vec3(-toMe.z, 0, toMe.x);
        Vec3 chest = feet.add(0, h * .6, 0);
        int[] cuts = com.jayptucker.heroesevolved.time.TimeFinishers.STOPPED_CUTS;
        int resume = com.jayptucker.heroesevolved.time.TimeFinishers.STOPPED_RESUME, land = com.jayptucker.heroesevolved.time.TimeFinishers.STOPPED_LAND;
        float r = Math.max(1.05F, h * .7F);
        boolean going = t >= resume;
        // Hands race for a moment, stop dead, then lurch on when time starts again.
        float minute = t < 3 ? t * .12F : going ? STOP_MINUTE + (t - resume) * .03F : STOP_MINUTE;
        float hour = t < 3 ? minute / 12 : going ? STOP_HOUR + (t - resume) * .0025F : STOP_HOUR;
        float broken = smooth(land, land + 4, t);
        Vec3 faceAt = chest.subtract(toMe.scale(.4));
        face(c, faceAt, toMe, r, hour, minute, going ? AMBER : VIOLET, f * (1 - broken));
        if (!going) motes(c, feet, r * .9F, h * 1.1F, a.seed(), 10, 0, t, ICE, f * .6F);
        // Cracks across the face: one more for every cut, running out from the centre.
        int cracked = 0;
        for (int cut : cuts) if (t >= cut) cracked++;
        for (int i = 0; i < cracked; i++) {
            float ang = hash(a.seed(), i) * Mth.TWO_PI;
            Vec3 out = right.scale(Mth.cos(ang)).add(0, Mth.sin(ang), 0);
            Vec3 bend = out.add(right.scale(Mth.sin(ang) * .3)).normalize();
            Vec3 mid = faceAt.add(out.scale(r * .45)), end = mid.add(bend.scale(r * .5));
            seam(c, faceAt, mid, .022F, ICE, f * (1 - broken) * .9F);
            seam(c, mid, end, .016F, ICE, f * (1 - broken) * .7F);
        }
        // The cuts themselves, frozen where they were made, flaring together as they land.
        float flare = smooth(land - 1, land, t) * (1 - smooth(land + 1, land + 6, t));
        for (int i = 0; i < cuts.length; i++) {
            if (t < cuts[i]) continue;
            float ang = (hash(a.seed() + 11, i) - .5F) * 2.4F + (i % 2 == 0 ? .5F : -.5F);
            Vec3 across = right.scale(Mth.cos(ang)).add(0, Mth.sin(ang), 0);
            Vec3 at = chest.add(0, (hash(a.seed() + 5, i) - .5) * h * .5, 0).add(toMe.scale(.2));
            float len = h * (.55F + .2F * hash(a.seed() + 9, i)) * smooth(cuts[i], cuts[i] + 1, t);
            float hold = t < land ? .75F : 0;
            float alpha = f * (hold + flare * 1.2F);
            seam(c, at.subtract(across.scale(len)), at.add(across.scale(len)), .03F, t < land ? ICE : GOLD, alpha);
            if (c.glow() && t < cuts[i] + 3) c.glow(at, .35F * (1 - smooth(cuts[i], cuts[i] + 3, t)), PALE, f * .6F);
        }
        if (t >= land) {
            float burst = smooth(land, land + 6, t), gone = f * (1 - burst);
            if (c.glow()) {
                c.glow(chest, .4F + 1.2F * (1 - smooth(land, land + 3, t)), PALE, f * (1 - smooth(land, land + 3, t)));
                c.ring(faceAt, toMe, r * (1 + burst * .6F), .05F, 0, Mth.TWO_PI, GOLD, gone);
            }
            motes(c, feet, r * (1 + burst), h * 1.2F, a.seed() + 3, 14, .06F, t - land, GOLD, gone * .8F);
        }
    }

    /**
     * Unwound: a clock across the foe spins backward, faster and faster, violet into ice; its afterimages (drawn by
     * BodyCopies) step back out and are pulled in; then the face shrinks to a point where the body was, with a pale flash.
     * Origin the foe's feet, target yours, bound to the foe, scale its height.
     */
    private static void unwound(Canvas c, Active a, float t) {
        float h = Math.max(.6F, a.scale()), f = fade(t, a.piece().ticks, 2, 8);
        Vec3 feet = a.origin(), toMe = a.target().subtract(feet).multiply(1, 0, 1);
        toMe = toMe.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : toMe.normalize();
        Vec3 chest = feet.add(0, h * .6, 0);
        int gone = com.jayptucker.heroesevolved.combat.FinisherBeats.UNWOUND_GONE, land = com.jayptucker.heroesevolved.combat.FinisherBeats.UNWOUND_LAND;
        float spin = -(t * t * .006F + t * .03F);
        float shrink = smooth(gone - 2, land, t);
        float r = Math.max(1.05F, h * .7F) * (1 - shrink * .97F);
        float[] colour = t < gone - 4 ? VIOLET : ICE;
        face(c, chest.subtract(toMe.scale(.4)), toMe, r, spin / 12, spin, colour, f * (1 - smooth(land, land + 2, t)));
        // A second, smaller face turning faster, the other way round, over the head.
        face(c, feet.add(0, h + .45, 0), UP, .45F * (1 - shrink), -spin * .7F / 12, -spin * .7F, DEEP, f * .6F * smooth(2, 5, t) * (1 - shrink));
        motes(c, feet, Math.max(.3F, r * .9F), h * 1.1F, a.seed(), 10, -.04F, t, PALE, f * .5F * (1 - shrink));
        if (t >= land - 1 && c.glow()) {
            float flash = 1 - smooth(land, land + 4, t);
            c.glow(chest, .2F + 1.1F * flash, PALE, f * flash);
            c.ring(chest, toMe, .2F + 1.6F * smooth(land, land + 6, t), .04F, 0, Mth.TWO_PI, VIOLET, f * (1 - smooth(land, land + 6, t)));
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
        float spin = (c.time()) * .05F;
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

    // ------------------------------------------------------------------
    // Ultimates (time/TimeUltimates)
    // ------------------------------------------------------------------

    /**
     * Ten Past Ten ({@code TimeUltimates.TenPastTen}): stopped time round the foe (motes hanging perfectly still, a cold
     * clock under it with its hands at ten past ten, a cage of still rings), a cut of light left hanging across the foe
     * at every step, all of them flashing at once on the snap, and the clock shattering into its hour marks.
     */
    static void tenPastTen(Canvas c, Active a, float t) {
        if (UltArt.cut(c, a)) return;
        net.minecraft.world.entity.Entity foe = UltArt.foe(c, a);
        Vec3 base = a.target(), facing = a.facing();
        float h = Math.max(.8F, a.scale());
        int seed = a.seed() * 7 + 2;
        int stop = com.jayptucker.heroesevolved.time.TimeUltimates.TenPastTen.STOP,
                cut = com.jayptucker.heroesevolved.time.TimeUltimates.TenPastTen.CUT,
                cuts = com.jayptucker.heroesevolved.time.TimeUltimates.TenPastTen.CUTS,
                every = com.jayptucker.heroesevolved.time.TimeUltimates.TenPastTen.EVERY,
                snap = com.jayptucker.heroesevolved.time.TimeUltimates.TenPastTen.SNAP;
        Vec3 feet = foe != null ? foe.getPosition(c.partial) : base;
        Vec3 mid = feet.add(0, h * .55, 0);
        float still = UltArt.window(t, stop, snap + 1, 3, 1);
        if (still > 0) {
            face(c, base.add(0, .06, 0), UP, 2.4F, 10 / 12F, 10 / 60F, VIOLET, still);
            motes(c, base, 3.2F, 3, seed, 26, 0, 0, ICE, still * .9F);
            for (int i = 0; i < 3; i++) {
                Vec3 axis = new Vec3(Math.cos(i * 2.1), .5, Math.sin(i * 2.1)).normalize();
                if (c.glow()) c.ring(mid, axis, .9F + .1F * i, .025F, 0, Mth.TWO_PI, ICE, still * .6F);
            }
            if (c.soft()) c.shell(mid, 3, 2.4F, 3, Mth.PI, DUSK, still * .12F, .3F, Canvas.PLAIN, 0);
        }
        // The cuts: a seam of light across the foe from each step, hanging there until the snap.
        for (int i = 0; i < cuts; i++) {
            float at = cut + i * every + 1;
            if (t < at) break;
            float flash = t >= snap ? 1 - smooth(snap, snap + 8, t) : 1;
            if (flash <= 0) continue;
            Vec3 from = com.jayptucker.heroesevolved.time.TimeUltimates.TenPastTen.step(base, facing, i).add(0, 1.2, 0);
            Vec3 dir = mid.subtract(from).normalize();
            Vec3 a0 = mid.subtract(dir.scale(1.3)).add(0, (i % 2 == 0 ? .5 : -.4), 0), a1 = mid.add(dir.scale(1.3)).add(0, (i % 2 == 0 ? -.5 : .4), 0);
            float grow = smooth(at, at + 2, t);
            float boost = t >= snap ? 1 + 2 * (1 - smooth(snap, snap + 4, t)) : 1;
            seam(c, a0, a0.lerp(a1, grow), .05F * boost, t >= snap ? PALE : GOLD, flash * .9F);
        }
        // The snap: every cut lands, the clock breaks into its hour marks, a ring runs out.
        if (t >= snap) {
            float age = t - snap, f = 1 - smooth(6, 30, age);
            if (c.glow() && age < 6) c.glow(mid, 2.2F * (1 - age / 6), PALE, 1 - age / 6);
            for (int i = 0; i < 12; i++) {
                float ang = i * Mth.TWO_PI / 12;
                Vec3 dir = new Vec3(Mth.cos(ang), .3 + .2 * hash(seed, i), Mth.sin(ang));
                Vec3 p = base.add(Mth.cos(ang) * 2.2, .1, Mth.sin(ang) * 2.2).add(dir.scale(age * .25)).add(0, -.01 * age * age, 0);
                seam(c, p, p.add(dir.scale(.3)), .03F, i % 3 == 0 ? PALE : VIOLET, f);
            }
            if (c.glow()) c.ring(base.add(0, .08, 0), UP, .5F + 6 * smooth(0, 12, age), .06F, 0, Mth.TWO_PI, VIOLET, f * (1 - smooth(0, 14, age)));
        }
    }

    /** Second Chance's mark (re-sent each second; the second in the seed): a gold clock on the ground, its hands turning backward. */
    static void markUlt(Canvas c, Active a, float t) {
        int second = a.seed();
        boolean last = second >= com.jayptucker.heroesevolved.time.TimeUltimates.SecondChance.TICKS / 20 - 1;
        float f = Math.clamp(t / 2 + (second > 0 ? .5F : 0), 0, 1) * (last ? 1 - smooth(10, 22, t) : Math.clamp((a.piece().ticks - t) / 2 + .3F, 0, 1));
        float now = c.level.getGameTime() + c.partial;
        face(c, a.origin().add(0, .06, 0), UP, 1.3F, -(now / 2400F) % 1, -(now / 120F) % 1, GOLD, f * .8F);
        motes(c, a.origin(), 1.2F, 2.2F, 41, 10, -.01F, now, AMBER, f * .6F);
    }

    /**
     * A rewind (seed 0): a violet trail drawn from where they were back to the mark, ghosts along it newest first, and
     * the mark's clock spinning back; or (seed 1) the hours given back, a gold pulse where they stand.
     */
    static void rewindUlt(Canvas c, Active a, float t) {
        float life = a.piece().ticks, f = fade(t, life, 0, 12);
        if (a.seed() == 1) {
            face(c, a.origin().add(0, .06, 0), UP, .5F + 1.5F * smooth(0, 10, t), -t / 20F, -t / 6F, GOLD, f);
            if (c.glow()) c.glow(a.origin().add(0, 1, 0), 1.2F * (1 - smooth(0, 10, t)), PALE, f);
            return;
        }
        Vec3 from = a.origin().add(0, 1, 0), to = a.target().add(0, 1, 0);
        float k = smooth(0, 8, t);
        seam(c, from.lerp(to, k), from, .06F, VIOLET, f);
        face(c, a.target().add(0, .06, 0), UP, 1.4F, -t / 8F, -t / 2F, VIOLET, f);
        if (c.glow() && t < 8) c.glow(to, 1.2F * (1 - t / 8), PALE, 1 - t / 8);
    }

    /**
     * Borrowed Hours on a body (bound; re-sent every three seconds): seed 0, a friend, a gold clock under their feet with
     * its hands racing; seed 1, a foe, a violet clock over its head with its hands barely crawling.
     */
    static void hours(Canvas c, Active a, float t) {
        net.minecraft.world.entity.Entity body = a.bound(c);
        if (body == null) return;
        float h = Math.max(.6F, a.scale()), f = fade(t, a.piece().ticks, 3, 6), now = c.level.getGameTime() + c.partial;
        if (a.seed() == 0) {
            face(c, body.getPosition(c.partial).add(0, .06, 0), UP, .7F, (now / 40F) % 1, (now / 6F) % 1, GOLD, f);
            motes(c, body.getPosition(c.partial), .6F, h, body.getId(), 6, .08F, now, AMBER, f * .7F);
        } else {
            face(c, body.getPosition(c.partial).add(0, h + .5, 0), UP, .4F, (now / 9600F) % 1, (now / 1600F) % 1, VIOLET, f);
        }
    }
}
