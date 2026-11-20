package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient.Active;
import com.jayptucker.heroesevolved.mimicry.MimicColors;
import com.jayptucker.heroesevolved.story.sylar.SylarEntity;
import com.jayptucker.heroesevolved.story.sylar.SylarGeometry;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.UP;
import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.fade;
import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.hash;
import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.rgb;
import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.smooth;
import static net.minecraft.util.Mth.PI;
import static net.minecraft.util.Mth.TWO_PI;

/**
 * Sylar in the Collection. One idea: <b>he sees how it works</b>. Every telegraph is a watchmaker's schematic in brass
 * whose parts float apart and slide together, seating as the move lands (a ring of eight arcs, a line of segments, a
 * hand, a wedge of the dial); the stolen power's colour fills what has assembled. His own telekinesis is violet. The
 * cases glow with what they hold, and while he draws on one, a thread runs from it to his hand and its glass is
 * outlined in gilt: that is the case to break.
 *
 * <p>Palette: {@code C9A24A} brass, {@code 6E5220} deep brass halo, {@code FFE7A8} gilt core (the schematics);
 * {@code 4B2F8F}, {@code B9A6FF}, {@code EDE6FF} (his telekinesis); {@code 5B3FD1}, {@code 9A7CFF}, {@code C9C2FF}
 * (stopped time); {@code A32813}, {@code F76A13}, {@code FFBE42} (the second hand's fire); {@code FFF6E0} the light;
 * {@code 2A2420} soot. Stolen powers fill in their {@link MimicColors} colour.</p>
 *
 * <p>Floor angles are the server's (twelve toward -z, clockwise from above); every number is {@link SylarGeometry}'s.</p>
 */
final class SylarArt {
    static final float[] BRASS = rgb(0xC9A24A), DEEP = rgb(0x6E5220), GILT = rgb(0xFFE7A8), TK_DEEP = rgb(0x4B2F8F),
            TK = rgb(0xB9A6FF), TK_CORE = rgb(0xEDE6FF), STOP_DEEP = rgb(0x5B3FD1), STOP = rgb(0x9A7CFF), STOP_PALE = rgb(0xC9C2FF),
            FIRE_EDGE = rgb(0xA32813), FIRE = rgb(0xF76A13), FIRE_GOLD = rgb(0xFFBE42), LIGHT = rgb(0xFFF6E0), SUN = rgb(0xFF9A3C),
            SOOT = rgb(0x2A2420), STONE = rgb(0x4A4A50), GLASS = rgb(0xDDEEF2), RED = rgb(0xB3262E), WHITE = rgb(0xFFFFFF);

    private SylarArt() {}

    private static boolean minimal() { return Minecraft.getInstance().options.particles().get() == ParticleStatus.MINIMAL; }
    private static float clock(Canvas c, float t) { return c.level != null ? (c.level.getGameTime() % 24000) + c.partial : t; }
    private static float[] colour(int index) { return rgb(MimicColors.color(index)); }

    static void draw(Canvas c, Active a, float t) {
        switch (a.piece()) {
            case SYLAR_ROOM -> room(c, a, t);
            case SYLAR_LOCK -> lock(c, a, t);
            case SYLAR_HURL_MARK, SYLAR_ICE_MARK, SYLAR_APPRAISE_MARK -> ringMark(c, a, t);
            case SYLAR_IMPACT -> impact(c, a, t);
            case SYLAR_REEL_MARK -> reelMark(c, a, t);
            case SYLAR_REEL, SYLAR_SHOVE -> tkLine(c, a, t);
            case SYLAR_LATTICE_MARK, SYLAR_YOURS_MARK -> lineMark(c, a, t, a.piece() == Piece.SYLAR_LATTICE_MARK ? SylarGeometry.LATTICE_REACH : 1.2);
            case SYLAR_LATTICE -> lattice(c, a, t);
            case SYLAR_ICE -> ice(c, a, t);
            case SYLAR_FAULT_MARK -> faultMark(c, a, t);
            case SYLAR_FAULT -> fault(c, a, t);
            case SYLAR_RING_MARK -> ringMark(c, a, t);
            case SYLAR_RING -> ring(c, a, t);
            case SYLAR_HAND_MARK -> handMark(c, a, t);
            case SYLAR_HAND -> hand(c, a, t);
            case SYLAR_BLUR_MARK -> blurMark(c, a, t);
            case SYLAR_BLUR -> blur(c, a, t);
            case SYLAR_HOURS_MARK -> hours(c, a, t, false);
            case SYLAR_HOURS -> hours(c, a, t, true);
            case SYLAR_LIGHT_MARK -> lightMark(c, a, t);
            case SYLAR_LIGHT -> light(c, a, t);
            case SYLAR_FIZZLE -> fizzle(c, a, t);
            case SYLAR_HOLD -> hold(c, a, t);
            case SYLAR_TAKE, SYLAR_RELEASE -> stream(c, a, t);
            case SYLAR_YOURS -> yours(c, a, t);
            case SYLAR_BACKFIRE -> backfire(c, a, t);
            case SYLAR_STAGGER -> stagger(c, a, t);
            case SYLAR_PHASE -> phase(c, a, t);
            case SYLAR_COVER -> cover(c, a, t);
            case SYLAR_ARRIVE -> arrive(c, a, t);
            case SYLAR_DEATH -> death(c, a, t);
            default -> {}
        }
    }

    // ------------------------------------------------------------------
    // Primitives: a three-layer line, the floor's clock angles, and the schematic's parts sliding together
    // ------------------------------------------------------------------

    static void beam(Canvas c, Vec3 a, Vec3 b, float w, float[] halo, float[] body, float[] core, float alpha) {
        if (alpha < 0.01F) return;
        c.line(a, b, w * 3.2F, halo, alpha * 0.32F, alpha * 0.32F);
        c.line(a, b, w, body, alpha * 0.85F, alpha * 0.85F);
        c.line(a, b, w * 0.35F, core, alpha, alpha);
    }

    /** A point on the floor round {@code at}, at clock angle {@code g} and radius {@code r}. */
    static Vec3 at(Vec3 at, double g, double r) { return at.add(Math.sin(g) * r, 0, -Math.cos(g) * r); }

    /** An arc of the floor's clock, as points, from angle {@code g0} to {@code g1} at radius {@code r}. */
    static Vec3[] arc(Vec3 centre, double r, double g0, double g1) {
        int n = Math.max(2, (int) (Math.abs(g1 - g0) * r / 0.6) + 1);
        Vec3[] pts = new Vec3[n];
        for (int i = 0; i < n; i++) pts[i] = at(centre, g0 + (g1 - g0) * i / (n - 1), r);
        return pts;
    }

    static void arc3(Canvas c, Vec3 centre, double r, double g0, double g1, float w, float alpha) {
        if (alpha < 0.01F) return;
        Vec3[] pts = arc(centre, r, g0, g1);
        c.ribbon(pts, w * 3.2F, DEEP, alpha * 0.3F, alpha * 0.3F);
        c.ribbon(pts, w, BRASS, alpha * 0.85F, alpha * 0.85F);
        c.ribbon(pts, w * 0.35F, GILT, alpha, alpha);
    }

    /** How far part {@code i} of {@code n} has seated when the whole schematic is {@code k} assembled: they arrive in turn. */
    static float seat(float k, int i, int n) {
        float start = 0.45F * i / Math.max(1, n);
        float u = Mth.clamp((k - start) / 0.55F, 0, 1);
        return u * u * (3 - 2 * u);
    }

    /**
     * A ring of eight brass arcs on the floor, flung outward and up and turned, sliding in to radius {@code r} as
     * {@code k} reaches 1; twelve hour ticks show once it is half built, the inside fills with {@code fill}, and the
     * last part seats with a click of light.
     */
    static void assembleRing(Canvas c, Vec3 centre, float r, float k, float[] fill, float alpha) {
        if (!c.glow()) return;
        Vec3 p = centre.add(0, 0.06, 0);
        float w = Math.max(0.05F, r * 0.03F);
        for (int i = 0; i < 8; i++) {
            float s = seat(k, i, 8), off = 1 - s;
            double g0 = i * PI / 4 + 0.07, g1 = (i + 1) * PI / 4 - 0.07, turn = off * 0.7 * (i % 2 == 0 ? 1 : -1);
            Vec3 lift = p.add(0, off * 1.3, 0);
            arc3(c, lift, r * (1 + off * 0.55F), g0 + turn, g1 + turn, w, alpha * (0.25F + 0.75F * s));
        }
        if (k > 0.35F) {
            float tk = smooth(0.35F, 0.8F, k);
            for (int h = 0; h < 12; h++) {
                double g = h * PI / 6;
                c.line(at(p, g, r * (h % 3 == 0 ? 0.78F : 0.86F)), at(p, g, r * 0.97F), w * (h % 3 == 0 ? 1.4F : 0.9F), GILT, alpha * tk * 0.8F, alpha * tk * 0.8F);
            }
        }
        // Cross hairs slide in from outside to the middle.
        for (int i = 0; i < 4; i++) {
            double g = i * PI / 2 + PI / 4;
            float s = seat(k, i, 4);
            beam(c, at(p, g, r * (1.6F - 0.9F * s)), at(p, g, r * (0.55F - 0.3F * s)), w * 0.7F, DEEP, BRASS, GILT, alpha * 0.6F * s);
        }
        c.disc(p, UP, r, fill, alpha * 0.32F * k * k, alpha * 0.14F * k * k);
        if (k > 0.94F) c.ring(p, UP, r, w * 4, 0, TWO_PI, GILT, alpha * (k - 0.94F) / 0.06F * 0.6F);
    }

    /**
     * A line of brass segments, flung aside and up and sliding into place from both ends toward the middle as {@code k}
     * reaches 1, with a ruler's ticks along it and the danger strip ({@code reach} either side) filling with {@code fill}.
     */
    static void assembleLine(Canvas c, Vec3 a, Vec3 b, double reach, float k, float[] fill, float alpha) {
        if (!c.glow()) return;
        Vec3 d = b.subtract(a);
        double length = d.horizontalDistance();
        if (length < 0.1) return;
        Vec3 dir = new Vec3(d.x, 0, d.z).normalize(), side = new Vec3(-dir.z, 0, dir.x);
        int n = Math.max(2, (int) (length / 1.6));
        float w = 0.07F;
        for (int i = 0; i < n; i++) {
            // From both ends toward the middle.
            int order = Math.min(i, n - 1 - i) * 2 + (i < n / 2 ? 0 : 1);
            float s = seat(k, order, n), off = 1 - s;
            double u0 = (i + 0.08) / n, u1 = (i + 0.92) / n;
            Vec3 shift = side.scale(off * 1.6 * (i % 2 == 0 ? 1 : -1)).add(0, off * 1.1 + 0.06, 0);
            beam(c, a.add(d.scale(u0)).add(shift), a.add(d.scale(u1)).add(shift), w, DEEP, BRASS, GILT, alpha * (0.25F + 0.75F * s));
        }
        float tk = smooth(0.3F, 0.85F, k);
        for (double u = 0; u <= length; u += 1) {
            Vec3 m = a.add(dir.scale(u)).add(0, 0.06, 0);
            float big = Math.round(u) % 4 == 0 ? 1 : 0.55F;
            c.line(m.add(side.scale(reach * big)), m.add(side.scale(-reach * big)), 0.035F, GILT, alpha * tk * 0.55F, alpha * tk * 0.55F);
        }
        Vec3 l0 = a.add(side.scale(reach)).add(0, 0.05, 0), r0 = a.add(side.scale(-reach)).add(0, 0.05, 0);
        Vec3 l1 = b.add(side.scale(reach)).add(0, 0.05, 0), r1 = b.add(side.scale(-reach)).add(0, 0.05, 0);
        float fa = alpha * 0.28F * k * k;
        c.tri(l0, r0, r1, fill, fa, fa, fa);
        c.tri(l0, r1, l1, fill, fa, fa, fa);
        if (k > 0.94F) beam(c, a.add(0, 0.08, 0), b.add(0, 0.08, 0), 0.12F, DEEP, BRASS, WHITE, alpha * (k - 0.94F) / 0.06F);
    }

    /** A gear: a ring with square teeth, turned by {@code spin}, lying across {@code axis}. */
    static void gear(Canvas c, Vec3 at, Vec3 axis, float r, int teeth, float spin, float alpha) {
        c.ring(at, axis, r, r * 0.1F, 0, TWO_PI, BRASS, alpha);
        c.ring(at, axis, r * 0.3F, r * 0.06F, 0, TWO_PI, GILT, alpha * 0.8F);
        for (int i = 0; i < teeth; i++) {
            float ang = spin + i * TWO_PI / teeth;
            c.spoke(at, axis, ang, r, r * 1.14F, r * 0.09F, BRASS, alpha);
            if (i % (teeth / 4) == 0) c.spoke(at, axis, ang, r * 0.3F, r, r * 0.05F, DEEP, alpha * 0.8F);
        }
    }

    private static Vec3 handOf(Entity e, float partial) {
        Vec3 p = e.getPosition(partial);
        float yaw = (e instanceof net.minecraft.world.entity.LivingEntity l ? l.yBodyRot : e.getYRot()) * Mth.DEG_TO_RAD;
        return p.add(0, 1.45, 0).add(-Mth.sin(yaw) * 0.45, 0, Mth.cos(yaw) * 0.45);
    }

    // ------------------------------------------------------------------
    // The room
    // ------------------------------------------------------------------

    /** The cases' specimens, the thread to the one he draws on, his loupe, the lights by phase, the great movement. */
    private static void room(Canvas c, Active a, float t) {
        if (!(a.bound(c) instanceof SylarEntity s) || !s.fighting()) return;
        float now = clock(c, t);
        int phase = s.phase(), drawing = s.drawing();
        Vec3 centre = SylarGeometry.center();
        for (int h = 0; h < 12; h++) {
            if (!s.caseWhole(h)) continue;
            int k = indexOf(SylarGeometry.EMPTY_CASES, h);
            int index = k >= 0 ? s.stolenColour(k) : MimicColors.index(SylarGeometry.CASES.get(h).getPath());
            if (index < 0) continue;
            specimen(c, SylarGeometry.specimen(h), colour(index), now + h * 11, h == drawing || k >= 0);
        }
        // Every case that can be broken now is edged in gilt (the one he draws on brightest, below).
        int open = s.openCases();
        if (c.glow()) for (int h = 0; h < 12; h++) if ((open >> h & 1) != 0 && h != drawing) caseOutline(c, h, 0.45F + 0.2F * Mth.sin(now * 0.3F + h));
        if (drawing >= 0 && c.glow()) {
            Vec3 from = SylarGeometry.specimen(drawing), to = handOf(s, c.partial);
            int k = indexOf(SylarGeometry.EMPTY_CASES, drawing);
            int index = k >= 0 ? s.stolenColour(k) : SylarGeometry.CASES.get(drawing) == null ? -1 : MimicColors.index(SylarGeometry.CASES.get(drawing).getPath());
            float[] col = index < 0 ? TK : colour(index);
            thread(c, from, to, col, now);
            caseOutline(c, drawing, 0.6F + 0.4F * Mth.sin(now * 0.5F));
        }
        // His loupe, at his right eye, glints while he winds a move up.
        if (s.is(SylarEntity.CASTING) && c.glow()) {
            Vec3 eye = s.getEyePosition(c.partial);
            float yaw = s.getYHeadRot() * Mth.DEG_TO_RAD;
            Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw)), right = new Vec3(-fwd.z, 0, fwd.x);
            Vec3 lens = eye.add(fwd.scale(0.28)).add(right.scale(-0.1)).add(0, 0.02, 0);
            c.ring(lens, fwd, 0.09F, 0.03F, 0, TWO_PI, BRASS, 0.9F);
            c.glow(lens, 0.22F + 0.06F * Mth.sin(now), GILT, 0.7F);
        }
        if (s.is(SylarEntity.HOVERING) && c.glow()) {
            Vec3 feet = s.getPosition(c.partial);
            for (int i = 0; i < 3; i++) c.ring(feet.add(0, 0.1 + i * 0.25, 0), UP, 0.6F + i * 0.25F, 0.05F, 0, TWO_PI, TK, 0.5F);
        }
        // The hour marks on the floor glow with the phase: deep red once the cases are unlocked, gilt for the masterwork.
        if (phase >= 2 && c.glow()) {
            float[] col = phase == 2 ? RED : GILT;
            float pulse = 0.45F + 0.25F * Mth.sin(now * 0.12F);
            for (int h = 0; h < 12; h++) {
                double g = h * PI / 6;
                c.line(at(centre, g, 11).add(0, 0.04, 0), at(centre, g, 14.5).add(0, 0.04, 0), 0.5F, col, 0, pulse * 0.6F);
            }
            c.ring(centre.add(0, 0.04, 0), UP, 14.1F, 0.18F, 0, TWO_PI, col, pulse * 0.35F);
        }
        // The masterwork: the great movement turns overhead.
        if (phase >= 3 && c.glow()) {
            Vec3 high = centre.add(0, 16.5, 0);
            float spin = now * 0.02F;
            gear(c, high, UP, 6.5F, 24, spin, 0.55F);
            gear(c, high.add(9.5, -1.2, 2), UP, 3.2F, 12, -spin * 2.05F, 0.5F);
            gear(c, high.add(-7, -0.6, -7.5), UP, 4.1F, 16, -spin * 1.6F, 0.5F);
            float swing = Mth.sin(now * 0.08F) * 1.1F;
            Vec3 wheel = high.add(0, -3, 0);
            c.ring(wheel, UP, 3.0F, 0.12F, swing, swing + TWO_PI, GILT, 0.55F);
            for (int i = 0; i < 4; i++) c.spoke(wheel, UP, swing + i * PI / 2, 0.3F, 3.0F, 0.07F, BRASS, 0.5F);
        }
    }

    private static int indexOf(int[] values, int v) {
        for (int i = 0; i < values.length; i++) if (values[i] == v) return i;
        return -1;
    }

    /** A power under glass: a slow-turning core of its colour, a brass gear round it, brighter when the case is open. */
    private static void specimen(Canvas c, Vec3 at, float[] col, float now, boolean open) {
        float bob = Mth.sin(now * 0.07F) * 0.06F;
        Vec3 p = at.add(0, bob, 0);
        if (c.soft()) c.shard(p.add(0, -0.34, 0), p.add(0, 0.36, 0), 0.16F, 5, now * 0.05F, col, 0.85F);
        if (!c.glow()) return;
        c.glow(p, open ? 1.3F : 0.95F, col, open ? 1.0F : 0.8F);
        c.glow(p, 0.32F, WHITE, 0.75F);
        Vec3 base = at.add(0, -0.95, 0);
        c.line(base, base.add(0, 1.9, 0), 0.5F, col, 0.35F, 0.05F);
        c.ring(base.add(0, 0.02, 0), UP, 0.42F, 0.08F, 0, TWO_PI, col, 0.7F);
        gear(c, p, new Vec3(Mth.sin(now * 0.01F), 1, Mth.cos(now * 0.01F)).normalize(), 0.36F, 8, now * 0.04F, 0.6F);
    }

    /** The thread from an open case to his hand: the power's colour, with brass beads running along it toward him. */
    private static void thread(Canvas c, Vec3 from, Vec3 to, float[] col, float now) {
        Vec3 d = to.subtract(from);
        int n = 16;
        Vec3[] pts = new Vec3[n + 1];
        for (int i = 0; i <= n; i++) {
            float u = i / (float) n;
            pts[i] = from.add(d.scale(u)).add(0, Mth.sin(u * PI) * 0.8, 0).add(0, Mth.sin(now * 0.3F + u * 9) * 0.05, 0);
        }
        c.ribbon(pts, 0.22F, col, 0.25F, 0.25F);
        c.ribbon(pts, 0.07F, col, 0.85F, 0.85F);
        c.ribbon(pts, 0.025F, WHITE, 0.9F, 0.9F);
        for (int b = 0; b < 4; b++) {
            float u = ((now * 0.06F) + b / 4F) % 1;
            int i = Math.min(n, (int) (u * n));
            c.glow(pts[i], 0.16F, GILT, 0.8F);
        }
    }

    /** The open case's glass, edged in gilt: the one to break. */
    private static void caseOutline(Canvas c, int hour, float alpha) {
        Vec3 lo = Vec3.atLowerCornerOf(SylarGeometry.caseGlass(hour));
        double[] xs = {lo.x - 0.02, lo.x + 1.02}, zs = {lo.z - 0.02, lo.z + 1.02};
        double y0 = lo.y - 0.02, y1 = lo.y + 2.02;
        for (double x : xs) for (double z : zs) beam(c, new Vec3(x, y0, z), new Vec3(x, y1, z), 0.05F, DEEP, BRASS, GILT, alpha);
        for (double y : new double[] {y0, y1}) {
            beam(c, new Vec3(xs[0], y, zs[0]), new Vec3(xs[1], y, zs[0]), 0.05F, DEEP, BRASS, GILT, alpha);
            beam(c, new Vec3(xs[0], y, zs[1]), new Vec3(xs[1], y, zs[1]), 0.05F, DEEP, BRASS, GILT, alpha);
            beam(c, new Vec3(xs[0], y, zs[0]), new Vec3(xs[0], y, zs[1]), 0.05F, DEEP, BRASS, GILT, alpha);
            beam(c, new Vec3(xs[1], y, zs[0]), new Vec3(xs[1], y, zs[1]), 0.05F, DEEP, BRASS, GILT, alpha);
        }
    }

    /** A locked case mends: a brass band tightens round the glass and it is whole again. */
    private static void lock(Canvas c, Active a, float t) {
        if (!c.glow()) return;
        float k = t / a.piece().ticks, f = fade(t, a.piece().ticks, 1, 6);
        for (int i = 0; i < 3; i++) c.ring(a.origin().add(0, (i - 1) * 0.6, 0), UP, 1.4F - 0.7F * k, 0.06F, 0, TWO_PI, BRASS, f * 0.8F);
        c.glow(a.origin(), 1.0F, GILT, f * 0.4F);
    }

    // ------------------------------------------------------------------
    // Telegraphs and what they do
    // ------------------------------------------------------------------

    /** Hurl's landing, Icefall's ring, Appraisal's ring, Resonance's start: a ring assembling where it will land. */
    private static void ringMark(Canvas c, Active a, float t) {
        float life = a.piece().ticks, k = Mth.clamp(t / life, 0, 1);
        float r = a.piece() == Piece.SYLAR_RING_MARK ? (float) SylarGeometry.RING_START : a.scale();
        assembleRing(c, a.origin(), r, k, colour(a.seed()), fade(t, life, 1, 2));
        if (a.piece() == Piece.SYLAR_APPRAISE_MARK && c.glow()) {
            // A lens over the ring: he is looking at how you work.
            Vec3 lens = a.origin().add(0, 2.6, 0);
            c.ring(lens, UP, 0.9F * (1.6F - 0.6F * k), 0.07F, 0, TWO_PI, BRASS, k);
            c.glow(lens, 0.6F, GILT, 0.4F * k);
        }
    }

    /** The slab lands: a ring of dust thrown out, chips of stone, a flash. */
    private static void impact(Canvas c, Active a, float t) {
        float life = a.piece().ticks, f = fade(t, life, 0.5F, 14), r = a.scale();
        Vec3 at = a.origin();
        if (c.soft()) {
            float k = smooth(0, 14, t);
            c.ring(at.add(0, 0.3 + k * 0.4, 0), UP, r * (0.6F + 1.2F * k), 0.6F + k, 0, TWO_PI, STONE, f * 0.35F);
            for (int i = 0; i < (minimal() ? 4 : 10); i++) {
                float ang = TWO_PI * hash(a.seed(), i), sp = 0.15F + 0.2F * hash(a.seed(), 20 + i);
                Vec3 chip = at.add(Mth.cos(ang) * sp * t, 0.2 + 0.32 * t - 0.03 * t * t, Mth.sin(ang) * sp * t);
                if (chip.y > at.y) c.box(chip, 0.2F, 0.2F, 0.2F, t * 10 + i * 40, t * 7, 0, STONE, f);
            }
        }
        if (c.glow() && t < 5) c.glow(at.add(0, 0.6, 0), r * 1.1F, GILT, 0.6F * (1 - t / 5));
    }

    /** Reel In's hook: a violet ring closing round whoever climbed out of reach, a dashed line back to his hand. */
    private static void reelMark(Canvas c, Active a, float t) {
        if (!c.glow()) return;
        float life = a.piece().ticks, k = Mth.clamp(t / life, 0, 1), f = fade(t, life, 1, 1);
        Entity e = a.bound(c);
        Vec3 body = e != null ? e.getPosition(c.partial).add(0, 1, 0) : a.origin().add(0, 1, 0);
        for (int i = 0; i < 6; i++) {
            float s = seat(k, i, 6), off = 1 - s;
            float g0 = i * TWO_PI / 6 + off, g1 = g0 + TWO_PI / 6 - 0.15F;
            c.ring(body.add(0, (i % 2 - 0.5) * off, 0), UP, 0.7F + off * 1.2F, 0.06F, g0, g1, TK, f * (0.3F + 0.7F * s));
        }
        Vec3 hand = a.target();
        for (int i = 0; i < 10; i++) {
            if (i % 2 == 1) continue;
            Vec3 p0 = hand.lerp(body, i / 10.0), p1 = hand.lerp(body, (i + 1) / 10.0);
            c.line(p0, p1, 0.05F, TK, f * k, f * k);
        }
    }

    /** Telekinesis in a line: a violet pull from his hand to someone (Reel In), or a shove that throws them back. */
    private static void tkLine(Canvas c, Active a, float t) {
        if (!c.glow()) return;
        float f = fade(t, a.piece().ticks, 0.5F, 6);
        Entity e = a.bound(c);
        Vec3 from = a.origin(), to = a.piece() == Piece.SYLAR_REEL && e != null
                ? e.getPosition(c.partial).add(0, 1, 0) : a.target();
        beam(c, from, to, 0.08F, TK_DEEP, TK, TK_CORE, f);
        Vec3 dir = to.subtract(from).normalize();
        for (int i = 0; i < 3; i++) {
            float u = Mth.clamp(t / 6F - i * 0.25F, 0, 1);
            c.ring(from.lerp(to, u), dir, 0.25F + 0.45F * u, 0.05F, 0, TWO_PI, TK, f * (1 - u * 0.6F));
        }
    }

    /** Arc Lattice's line and Your Own's line: segments sliding into place, the strip filling with the power's colour. */
    private static void lineMark(Canvas c, Active a, float t, double reach) {
        float life = a.piece().ticks, k = Mth.clamp(t / life, 0, 1);
        Vec3 from = a.origin(), to = a.target();
        if (a.piece() == Piece.SYLAR_YOURS_MARK) {
            // Your own power's line, from his hand toward you and on past you.
            Vec3 dir = to.subtract(from).normalize();
            to = from.add(dir.scale(Math.max(8, from.distanceTo(to) + 4)));
            from = new Vec3(from.x, SylarGeometry.floorY(), from.z);
            to = new Vec3(to.x, SylarGeometry.floorY(), to.z);
        }
        assembleLine(c, from, to, reach, k, colour(a.seed()), fade(t, life, 1, 1));
    }

    /** The lattice strikes: forked lightning between two cases, flashing at each end. */
    private static void lattice(Canvas c, Active a, float t) {
        float f = fade(t, a.piece().ticks, 0.5F, 8);
        StoryArt.forked(c, a.origin(), a.target(), a.seed() + (int) (t / 2), 0.08F, f, 4);
        if (c.glow()) {
            c.glow(a.origin(), 0.9F, WHITE, f * 0.6F);
            c.glow(a.target(), 0.9F, WHITE, f * 0.6F);
        }
    }

    /** Icefall: shards of ice burst up through the ring and stand a moment, then sink. */
    private static void ice(Canvas c, Active a, float t) {
        float life = a.piece().ticks, f = fade(t, life, 0.5F, 10), r = a.scale();
        float rise = smooth(0, 4, t), sink = smooth(life - 10, life, t);
        float[] frost = rgb(0xBFEFFF), pale = rgb(0xEAF8FF);
        if (c.soft()) {
            int n = minimal() ? 7 : 14;
            for (int i = 0; i < n; i++) {
                float ang = TWO_PI * hash(a.seed(), i), d = r * (float) Math.sqrt(hash(a.seed(), 30 + i));
                float h = (0.9F + 1.6F * hash(a.seed(), 60 + i)) * rise * (1 - sink * 0.9F);
                Vec3 base = a.origin().add(Mth.cos(ang) * d, -0.1, Mth.sin(ang) * d);
                Vec3 tip = base.add((hash(a.seed(), 90 + i) - 0.5) * 0.5, h, (hash(a.seed(), 120 + i) - 0.5) * 0.5);
                if (h > 0.05F) c.shard(base, tip, 0.18F + 0.12F * hash(a.seed(), 150 + i), 5, ang, i % 3 == 0 ? pale : frost, 0.8F * f);
            }
        }
        if (c.glow()) {
            c.ring(a.origin().add(0, 0.06, 0), UP, r, 0.1F, 0, TWO_PI, pale, f * 0.6F);
            if (t < 6) c.glow(a.origin().add(0, 1, 0), r * 1.2F, pale, 0.5F * (1 - t / 6));
        }
    }

    /** Fault Line's schematic, with the crack itself growing dark along it. */
    private static void faultMark(Canvas c, Active a, float t) {
        float life = a.piece().ticks, k = Mth.clamp(t / life, 0, 1), f = fade(t, life, 1, 1);
        assembleLine(c, a.origin(), a.target(), SylarGeometry.FAULT_REACH, k, colour(a.seed()), f);
        if (c.soft()) crack(c, a.origin(), a.target(), a.seed(), k, f * 0.8F);
    }

    private static void crack(Canvas c, Vec3 a, Vec3 b, int seed, float reach, float alpha) {
        Vec3 d = b.subtract(a), side = new Vec3(-d.z, 0, d.x).normalize();
        int n = 24;
        Vec3[] pts = new Vec3[(int) (n * reach) + 1];
        for (int i = 0; i < pts.length; i++) {
            float u = i / (float) n;
            pts[i] = a.add(d.scale(u)).add(side.scale((hash(seed, i) - 0.5) * 0.7)).add(0, 0.04, 0);
        }
        if (pts.length >= 2) c.ribbon(pts, 0.28F, SOOT, alpha, alpha);
    }

    /** The floor heaves along the crack: blocks of stone jump and fall back, dust behind them. */
    private static void fault(Canvas c, Active a, float t) {
        float life = a.piece().ticks, f = fade(t, life, 0.5F, 10);
        Vec3 from = a.origin(), d = a.target().subtract(from);
        double length = d.horizontalDistance();
        if (c.soft()) {
            crack(c, from, a.target(), a.seed(), 1, f);
            for (int i = 0; i < (minimal() ? 6 : 14); i++) {
                float u = (i + hash(a.seed(), i)) / 14F, delay = u * 6;
                float age = t - delay;
                if (age < 0 || age > 14) continue;
                float h = Math.max(0, 0.9F * age - 0.07F * age * age);
                Vec3 p = from.add(d.scale(u)).add(0, h, 0);
                c.box(p, 0.7F, 0.5F, 0.7F, i * 37, age * 6, 0, STONE, f);
            }
        }
        if (c.glow() && t < 4) c.glow(from.add(d.scale(0.5)).add(0, 0.5, 0), (float) length * 0.25F, GILT, 0.4F * (1 - t / 4));
    }

    /** Resonance rolls out: a low wall of sound, shimmering gold, moving at exactly the server's speed. */
    private static void ring(Canvas c, Active a, float t) {
        float life = a.piece().ticks, f = fade(t, life, 1, 6);
        float r = (float) SylarGeometry.ringRadius(t);
        if (r > SylarGeometry.FLOOR_R + 1.5) return;
        float[] gold = colour(MimicColors.index("sonic_resonance"));
        Vec3 base = a.origin().add(0, 0.02, 0);
        if (c.glow()) {
            c.tube(base, r, 0.9F, gold, f * 0.45F, f * 0.05F, Canvas.PLAIN, 0);
            for (int i = 0; i < 3; i++) c.ring(base.add(0, 0.3 * i, 0), UP, r, 0.06F, 0, TWO_PI, i == 0 ? WHITE : gold, f * (0.9F - i * 0.25F));
            c.ring(base.add(0, 3.2, 0), UP, r, 0.04F, 0, TWO_PI, gold, f * 0.3F);
        }
    }

    /** The Second Hand's schematic: the hand along its starting radius, and a dashed arc showing the way it will turn. */
    private static void handMark(Canvas c, Active a, float t) {
        float life = a.piece().ticks, k = Mth.clamp(t / life, 0, 1), f = fade(t, life, 1, 1);
        Vec3 centre = a.origin();
        double g0 = SylarGeometry.angleOf(a.target().x - centre.x, a.target().z - centre.z);
        Vec3 inner = at(centre, g0, SylarGeometry.HAND_INNER);
        assembleLine(c, inner, a.target(), SylarGeometry.HAND_REACH, k, FIRE, f);
        if (!c.glow()) return;
        double sweep = a.scale();
        int dashes = 12;
        for (int i = 0; i < dashes; i++) {
            if (i * 1F / dashes > k) break;
            double ga = g0 + sweep * i / dashes, gb = g0 + sweep * (i + 0.55) / dashes;
            arc3(c, centre.add(0, 0.07, 0), 12.5, ga, gb, 0.08F, f * 0.8F);
        }
        Vec3 tip = at(centre, g0 + sweep * k, 12.5).add(0, 0.07, 0);
        c.glow(tip, 0.5F, FIRE_GOLD, f * k);
    }

    /** The hand of fire sweeping its quarter turn at the server's rate, a fading wake of embers behind it. */
    private static void hand(Canvas c, Active a, float t) {
        float life = a.piece().ticks;
        Vec3 centre = a.origin();
        double g0 = SylarGeometry.angleOf(a.target().x - centre.x, a.target().z - centre.z);
        double g = SylarGeometry.handAngle(g0, t);
        float f = fade(t, life + 6, 0.5F, 6);
        if (c.glow()) {
            for (int trail = 0; trail < 6; trail++) {
                double gt = SylarGeometry.handAngle(g0, Math.max(0, t - trail * 1.5F));
                float fa = f * (1 - trail / 6F);
                sheet(c, centre, gt, trail == 0 ? 1.15F : 0.8F - trail * 0.1F, fa);
            }
        }
    }

    /** A low sheet of flame along the radius at angle {@code g}, from the dais's edge to the wall. */
    private static void sheet(Canvas c, Vec3 centre, double g, float height, float alpha) {
        double r0 = SylarGeometry.HAND_INNER, r1 = SylarGeometry.FLOOR_R;
        int n = 14;
        for (int i = 0; i < n; i++) {
            double ra = r0 + (r1 - r0) * i / n, rb = r0 + (r1 - r0) * (i + 1) / n;
            float wob = 0.2F * Mth.sin(i * 1.7F + (float) g * 20);
            Vec3 a0 = at(centre, g, ra).add(0, 0.02, 0), b0 = at(centre, g, rb).add(0, 0.02, 0);
            Vec3 a1 = a0.add(0, height + wob, 0), b1 = b0.add(0, height - wob, 0);
            c.tri(a0, b0, b1, FIRE, alpha * 0.8F, alpha * 0.8F, 0);
            c.tri(a0, b1, a1, FIRE, alpha * 0.8F, 0, 0);
            c.tri(a0, b0, b0.add(0, height * 0.45, 0), FIRE_GOLD, alpha, alpha, alpha * 0.3F);
        }
        beam(c, at(centre, g, r0).add(0, 0.05, 0), at(centre, g, r1).add(0, 0.05, 0), 0.1F, FIRE_EDGE, FIRE, FIRE_GOLD, alpha);
        // The band it burns along the floor, as wide as its reach.
        double half = SylarGeometry.HAND_REACH;
        Vec3 side = new Vec3(Math.cos(g), 0, Math.sin(g)).scale(half);
        Vec3 a0 = at(centre, g, r0).add(0, 0.04, 0), b0 = at(centre, g, r1).add(0, 0.04, 0);
        c.tri(a0.add(side), a0.subtract(side), b0.subtract(side), FIRE, alpha * 0.45F, alpha * 0.45F, alpha * 0.45F);
        c.tri(a0.add(side), b0.subtract(side), b0.add(side), FIRE, alpha * 0.45F, alpha * 0.45F, alpha * 0.45F);
    }

    /** Blur's three dashes assembling in turn. */
    private static void blurMark(Canvas c, Active a, float t) {
        float life = a.piece().ticks, k = Mth.clamp((t / life) * 1.2F - a.seed() * 0.1F, 0, 1);
        assembleLine(c, a.origin(), a.target(), SylarGeometry.BLUR_REACH, k, colour(MimicColors.index("superspeed")), fade(t, life, 1, 1));
    }

    /** A dash: a streak of gold where he ran. */
    private static void blur(Canvas c, Active a, float t) {
        if (!c.glow()) return;
        float f = fade(t, a.piece().ticks, 0.5F, 6);
        float[] gold = colour(MimicColors.index("superspeed"));
        Vec3 from = a.origin().add(0, 1, 0), to = a.target().add(0, 1, 0);
        beam(c, from, to, 0.18F, rgb(0xFF8C1E), gold, rgb(0xFFF4C8), f);
        StoryArt.filament(c, from, to, a.seed(), 0.3F, 0.03F, f * 0.7F);
    }

    /**
     * Every Hour: the whole dial plate. Ten of the twelve hours assemble in violet; the two spared (one, and the one
     * opposite) blaze gilt from the start, so there is time to reach them. When it lands, stopped time washes the ten.
     */
    private static void hours(Canvas c, Active a, float t, boolean landed) {
        if (!c.glow()) return;
        float life = a.piece().ticks, k = landed ? 1 : Mth.clamp(t / life, 0, 1), f = fade(t, life, 1, landed ? 18 : 1);
        int safe = Math.round(a.scale()) - 1;
        Vec3 centre = SylarGeometry.center().add(0, 0.05, 0);
        double r0 = 2, r1 = SylarGeometry.FLOOR_R;
        for (int h = 0; h < 12; h++) {
            double g0 = h * PI / 6 - PI / 12, g1 = g0 + PI / 6;
            boolean spared = SylarGeometry.spared(h, safe);
            if (spared) {
                float pulse = 0.7F + 0.3F * Mth.sin(t * 0.6F);
                arc3(c, centre, r1 - 0.3, g0 + 0.02, g1 - 0.02, 0.14F, f * pulse);
                arc3(c, centre, r0 + 0.3, g0 + 0.02, g1 - 0.02, 0.1F, f * pulse);
                beam(c, at(centre, g0 + 0.02, r0), at(centre, g0 + 0.02, r1), 0.1F, DEEP, BRASS, GILT, f * pulse);
                beam(c, at(centre, g1 - 0.02, r0), at(centre, g1 - 0.02, r1), 0.1F, DEEP, BRASS, GILT, f * pulse);
                c.glow(at(centre, h * PI / 6, 12).add(0, 0.5, 0), 2.4F, GILT, f * 0.6F * pulse);
                for (double r = r0; r < r1; r += 2.5) {
                    Vec3 p0 = at(centre, g0, r), p1 = at(centre, g1, r), p2 = at(centre, g1, Math.min(r1, r + 2.5)), p3 = at(centre, g0, Math.min(r1, r + 2.5));
                    float gold = f * 0.30F * pulse;
                    c.tri(p0, p1, p2, FIRE_GOLD, gold, gold, gold);
                    c.tri(p0, p2, p3, FIRE_GOLD, gold, gold, gold);
                }
                continue;
            }
            float s = landed ? 1 : seat(k, (h * 5) % 12, 12), off = 1 - s;
            Vec3 lifted = centre.add(0, off * 2.5, 0);
            Vec3[] outer = arc(lifted, r1 * (1 + off * 0.2), g0 + 0.04, g1 - 0.04);
            float al = f * (0.25F + 0.75F * s);
            c.ribbon(outer, 0.4F, STOP_DEEP, al * 0.4F, al * 0.4F);
            c.ribbon(outer, 0.12F, STOP, al, al);
            c.line(at(lifted, g0 + 0.04, r0), at(lifted, g0 + 0.04, r1), 0.08F, STOP_PALE, al * 0.7F, al * 0.7F);
            if (s > 0.6F || landed) {
                float fill = landed ? f * 0.45F : al * 0.12F * s * s;
                for (double r = r0; r < r1; r += 2.5) {
                    Vec3 p0 = at(centre, g0, r), p1 = at(centre, g1, r), p2 = at(centre, g1, Math.min(r1, r + 2.5)), p3 = at(centre, g0, Math.min(r1, r + 2.5));
                    c.tri(p0, p1, p2, landed ? STOP_PALE : STOP, fill, fill, fill);
                    c.tri(p0, p2, p3, landed ? STOP_PALE : STOP, fill, fill, fill);
                }
            }
        }
    }

    /**
     * The Light at 6:14: rays assemble outward from him to the walls, each stopping at the first block in its way, so the
     * shadows behind the pillars show where to stand. A light that will fail (scale 2) shakes apart at seven tenths.
     */
    private static void lightMark(Canvas c, Active a, float t) {
        if (!c.glow()) return;
        Entity e = a.bound(c);
        float life = a.piece().ticks, k = Mth.clamp(t / life, 0, 1), f = fade(t, life, 1, 1);
        boolean fails = a.scale() > 1.5F;
        float broken = fails ? smooth(0.68F, 0.8F, k) : 0;
        Vec3 eye = e != null ? e.getEyePosition(c.partial) : a.target();
        float sunR = 0.4F + 1.4F * k * (1 - broken);
        c.glow(eye, sunR * 2.2F, SUN, f * (0.4F + 0.4F * k) * (1 - broken));
        c.glow(eye, sunR, LIGHT, f * 0.9F * (1 - broken));
        int rays = minimal() ? 24 : 48;
        Vec3 centre = SylarGeometry.center();
        for (int i = 0; i < rays; i++) {
            double g = i * TWO_PI / rays;
            Vec3 end = at(centre, g, SylarGeometry.FLOOR_R).add(0, 0.8, 0);
            Vec3 stop = clipped(c, eye, end, e);
            float s = seat(k, (i * 7) % rays, rays);
            if (s <= 0.01F) continue;
            Vec3 drop = new Vec3(0, -broken * 3 * hash(i, 3), 0);
            Vec3 tip = eye.lerp(stop, s).add(drop);
            float al = f * (0.25F + 0.6F * s) * (1 - broken);
            c.line(eye, tip, 0.05F, BRASS, al, al * 0.6F);
            c.line(eye, tip, 0.016F, GILT, al, al);
            if (!fails && s > 0.95F && stop.distanceToSqr(end) > 1) c.glow(stop, 0.35F, GILT, al);
        }
    }

    private static Vec3 clipped(Canvas c, Vec3 from, Vec3 to, Entity e) {
        if (c.level == null) return to;
        var hit = c.level.clip(e != null ? new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e)
                : new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS ? to : hit.getLocation();
    }

    /** The light lands: a white sun where he hangs, and every ray that reaches the walls burning white. */
    private static void light(Canvas c, Active a, float t) {
        if (!c.glow()) return;
        Entity e = a.bound(c);
        float life = a.piece().ticks, f = fade(t, life, 0.5F, 24);
        Vec3 eye = a.origin();
        float grow = smooth(0, 6, t);
        c.glow(eye, 3 + 10 * grow, LIGHT, f * 0.9F);
        c.glow(eye, 6 + 18 * grow, SUN, f * 0.45F);
        c.sphere(eye, 1.2F + 2.5F * grow, LIGHT, f * 0.5F);
        Vec3 centre = SylarGeometry.center();
        int rays = minimal() ? 24 : 48;
        for (int i = 0; i < rays; i++) {
            Vec3 end = at(centre, i * TWO_PI / rays, SylarGeometry.FLOOR_R).add(0, 0.8, 0);
            Vec3 stop = clipped(c, eye, end, e);
            c.line(eye, stop, 0.35F, SUN, f * 0.4F, f * 0.2F);
            c.line(eye, stop, 0.08F, LIGHT, f, f * 0.7F);
        }
        c.ring(centre.add(0, 0.05, 0), UP, (float) SylarGeometry.FLOOR_R * grow, 0.6F, 0, TWO_PI, LIGHT, f * 0.6F);
    }

    /** He reaches for the light and it comes apart: brass parts falling out of the air, a breath of grey. */
    private static void fizzle(Canvas c, Active a, float t) {
        float life = a.piece().ticks, f = fade(t, life, 0.5F, 12);
        Vec3 at = a.origin();
        for (int i = 0; i < (minimal() ? 8 : 18); i++) {
            float ang = TWO_PI * hash(a.seed(), i), sp = 0.05F + 0.12F * hash(a.seed(), 40 + i);
            Vec3 p = at.add(Mth.cos(ang) * sp * t, -0.015 * t * t + 0.1 * t, Mth.sin(ang) * sp * t);
            if (c.glow()) c.line(p, p.add(Mth.cos(ang + t * 0.3F) * 0.4, 0.1, Mth.sin(ang + t * 0.3F) * 0.4), 0.05F, BRASS, f, f);
        }
        if (c.soft()) c.sphere(at.add(0, 0.02 * t, 0), 0.6F + 0.05F * t, rgb(0x8A8A90), f * 0.25F);
    }

    /**
     * Held by Appraisal: three gear rings of brass turn round the body at chest, waist and knees, filling with the power's
     * colour as he learns it; violet bands run back to his hand, and a gilt arc at the chest fills as they fight.
     */
    private static void hold(Canvas c, Active a, float t) {
        if (!c.glow()) return;
        Entity e = a.bound(c);
        float f = fade(t, a.piece().ticks, 2, 2), now = clock(c, t);
        Vec3 body = e != null ? e.getPosition(c.partial) : a.origin();
        float progress = Mth.clamp(a.scale(), 0, 1), fought = (a.seed() % 100) / 99F;
        float[] col = colour(a.seed() / 100);
        for (int i = 0; i < 3; i++) {
            Vec3 at = body.add(0, 0.4 + i * 0.6, 0);
            float r = 0.8F + 0.15F * i;
            gear(c, at, UP, r, 12, now * (i % 2 == 0 ? 0.08F : -0.08F), f * 0.8F);
            c.ring(at, UP, r * 0.7F, r * 0.5F, PI, PI - progress * TWO_PI, col, f * 0.35F);
        }
        beam(c, a.target(), body.add(0, 1, 0), 0.06F, TK_DEEP, TK, TK_CORE, f * 0.8F);
        if (fought > 0) c.ring(body.add(0, 1.2, 0), UP, 1.15F, 0.08F, PI, PI - fought * TWO_PI, GILT, f);
        c.glow(body.add(0, 1, 0), 1.0F, col, f * 0.3F * progress);
    }

    /** A power travelling: out of you into a case (taken), or out of a broken case back into you (released). */
    private static void stream(Canvas c, Active a, float t) {
        if (!c.glow()) return;
        float life = a.piece().ticks, f = fade(t, life, 2, 12), k = smooth(0, 20, t);
        boolean release = a.piece() == Piece.SYLAR_RELEASE;
        Entity e = a.bound(c);
        Vec3 from = a.origin(), to = release && e != null ? e.getPosition(c.partial).add(0, 1, 0) : a.target();
        float[] col = colour(a.seed());
        Vec3 d = to.subtract(from), side = d.cross(UP);
        if (side.lengthSqr() < 1e-6) side = new Vec3(1, 0, 0);
        side = side.normalize();
        Vec3 up = side.cross(d).normalize();
        int strands = minimal() ? 2 : 4;
        for (int s = 0; s < strands; s++) {
            int n = 18;
            Vec3[] pts = new Vec3[n + 1];
            for (int i = 0; i <= n; i++) {
                float u = Math.min(i / (float) n, k), ang = u * 9 + s * TWO_PI / strands - t * 0.4F, r = 0.35F * Mth.sin(u * PI);
                pts[i] = from.add(d.scale(u)).add(side.scale(Mth.cos(ang) * r)).add(up.scale(Mth.sin(ang) * r));
            }
            c.ribbon(pts, 0.08F, col, f * 0.8F, f * 0.8F);
        }
        c.glow(to, 0.9F, release ? GILT : col, f * 0.6F * k);
    }

    /** Your own power, turned on you: a bolt of its colour down the line. */
    private static void yours(Canvas c, Active a, float t) {
        if (!c.glow()) return;
        float f = fade(t, a.piece().ticks, 0.5F, 8);
        float[] col = colour(a.seed());
        beam(c, a.origin(), a.target(), 0.22F, col, col, WHITE, f);
        c.glow(a.target(), 1.0F, col, f * 0.7F);
        c.glow(a.origin(), 0.6F, WHITE, f * 0.6F);
    }

    /** A backfire: the case bursts, glass flies, and the power lashes into his chest. */
    private static void backfire(Canvas c, Active a, float t) {
        float life = a.piece().ticks, f = fade(t, life, 0.5F, 14);
        float[] col = colour(a.seed());
        Entity e = a.bound(c);
        Vec3 from = a.origin(), to = e != null ? e.getPosition(c.partial).add(0, 1.1, 0) : a.target();
        if (c.glow()) {
            c.glow(from, 2.4F * smooth(0, 4, t), col, f * 0.8F);
            for (int i = 0; i < 3; i++) StoryArt.filament(c, from, to, a.seed() + i * 13 + (int) (t / 2), 0.6F, 0.06F, f);
            c.glow(to, 1.4F, col, f * 0.7F);
        }
        if (c.soft()) for (int i = 0; i < (minimal() ? 5 : 12); i++) {
            float ang = TWO_PI * hash(a.seed(), i), pitch = hash(a.seed(), 30 + i) * 1.2F, sp = 0.18F + 0.15F * hash(a.seed(), 60 + i);
            Vec3 p = from.add(Mth.cos(ang) * Mth.cos(pitch) * sp * t, Mth.sin(pitch) * sp * t - 0.02 * t * t, Mth.sin(ang) * Mth.cos(pitch) * sp * t);
            c.box(p, 0.16F, 0.02F, 0.12F, t * 20 + i * 30, t * 13, i * 9, GLASS, f * 0.7F);
        }
    }

    /** His concentration breaks: the loupe at his eye cracks apart in brass shards. */
    private static void stagger(Canvas c, Active a, float t) {
        if (!c.glow()) return;
        float f = fade(t, a.piece().ticks, 0.5F, 12);
        Entity e = a.bound(c);
        Vec3 eye = e != null ? e.getEyePosition(c.partial) : a.origin();
        for (int i = 0; i < 8; i++) {
            float ang = i * TWO_PI / 8 + hash(a.seed(), i), d = 0.15F + 0.09F * t;
            Vec3 p = eye.add(Mth.cos(ang) * d, Mth.sin(ang) * d - 0.01 * t * t, 0);
            c.line(p, p.add(Mth.cos(ang) * 0.2, Mth.sin(ang) * 0.2, 0.05), 0.04F, BRASS, f, f);
        }
        c.glow(eye, 0.7F, GILT, f * 0.6F);
    }

    /** A phase changes: a violet shove rolls out from him, and a ring of the new phase's colour flashes on the floor. */
    private static void phase(Canvas c, Active a, float t) {
        if (!c.glow()) return;
        float life = a.piece().ticks, f = fade(t, life, 0.5F, 14), k = smooth(0, 16, t);
        Vec3 at = a.origin();
        for (int i = 0; i < 3; i++) {
            float r = 1 + 9 * Mth.clamp(k - i * 0.12F, 0, 1);
            c.ring(at.add(0, 0.6 + i * 0.4, 0), UP, r, 0.12F, 0, TWO_PI, TK, f * (1 - k * 0.5F));
        }
        float[] col = a.scale() >= 3 ? GILT : RED;
        c.ring(SylarGeometry.center().add(0, 0.05, 0), UP, (float) SylarGeometry.FLOOR_R * k, 0.4F, 0, TWO_PI, col, f * 0.5F);
    }

    /** The pillars rise: a brass outline on the floor first, then a frame climbing with the stone. */
    private static void cover(Canvas c, Active a, float t) {
        if (!c.glow()) return;
        float life = a.piece().ticks, f = fade(t, life, 1, 6);
        Vec3 base = a.origin().add(0, 0.05, 0);
        float warn = Mth.clamp(t / com.jayptucker.heroesevolved.story.sylar.CollectionArena.COVER_WARN, 0, 1);
        double h = Math.max(0, (t - com.jayptucker.heroesevolved.story.sylar.CollectionArena.COVER_WARN)
                / com.jayptucker.heroesevolved.story.sylar.CollectionArena.COVER_STEP);
        double half = 1.55;
        Vec3[] corners = {base.add(-half, 0, -half), base.add(half, 0, -half), base.add(half, 0, half), base.add(-half, 0, half)};
        for (int i = 0; i < 4; i++) {
            Vec3 p0 = corners[i], p1 = corners[(i + 1) % 4];
            beam(c, p0, p0.lerp(p1, warn), 0.08F, DEEP, BRASS, GILT, f);
            if (h > 0) beam(c, p0, p0.add(0, Math.min(a.scale(), h), 0), 0.06F, DEEP, BRASS, GILT, f * 0.7F);
        }
    }

    /** Folded into or out of the hall: a clock face opens round them (arriving) or closes on them (leaving, home). */
    private static void arrive(Canvas c, Active a, float t) {
        if (!c.glow()) return;
        float life = a.piece().ticks, f = fade(t, life, 1, 8);
        Entity e = a.bound(c);
        Vec3 at = e != null ? e.getPosition(c.partial).add(0, 1, 0) : a.origin().add(0, 1, 0);
        boolean leaving = a.scale() < 1.5F;
        float k = leaving ? 1 - smooth(0, life * 0.8F, t) : smooth(0, life * 0.6F, t);
        float r = 0.3F + 1.6F * k;
        c.ring(at, UP, r, 0.08F, 0, TWO_PI, BRASS, f);
        c.ring(at, UP, r * 0.82F, 0.04F, 0, TWO_PI, GILT, f * 0.8F);
        for (int h = 0; h < 12; h++) c.spoke(at, UP, h * TWO_PI / 12, r * 0.86F, r, 0.05F, GILT, f);
        float hand = t * 0.5F * (leaving ? -1 : 1);
        c.spoke(at, UP, hand, 0, r * 0.8F, 0.06F, GILT, f);
        c.glow(at, r, GILT, f * 0.25F);
    }

    /**
     * His death: everything he held leaves him (a stream of each standing case's colour climbing out of him into the
     * dark), the schematic that was always round him comes undone and falls, and it ends in one gilt flash.
     */
    private static void death(Canvas c, Active a, float t) {
        float life = a.piece().ticks, f = fade(t, life, 1, 6);
        Entity e = a.bound(c);
        Vec3 body = e != null ? e.getPosition(c.partial).add(0, 1, 0) : a.target();
        int mask = a.seed();
        if (c.glow()) {
            int n = 0;
            for (int h = 0; h < 12; h++) {
                if ((mask >> h & 1) == 0) continue;
                var power = SylarGeometry.CASES.get(h);
                float[] col = power == null ? TK : colour(MimicColors.index(power.getPath()));
                float ang = h * TWO_PI / 12, rise = smooth(5 + n * 2, 40, t);
                Vec3 top = body.add(Mth.sin(ang) * 3 * rise, 8 * rise, -Mth.cos(ang) * 3 * rise);
                c.line(body, top, 0.12F, col, f * 0.7F, 0);
                c.glow(top, 0.5F, col, f * (1 - rise));
                n++;
            }
            for (int i = 0; i < 10; i++) {
                float ang = i * TWO_PI / 10, fall = Math.max(0, t - 20) * 0.04F;
                Vec3 p = body.add(Mth.cos(ang) * 1.1, 0.4 - fall * fall * 6, Mth.sin(ang) * 1.1);
                if (p.y > body.y - 1.1) c.line(p, p.add(Mth.cos(ang + 1) * 0.5, 0.1, Mth.sin(ang + 1) * 0.5), 0.06F, BRASS, f, f);
            }
            if (t > life - 10) c.glow(body, 4 * smooth(life - 10, life - 4, t), GILT, (1 - smooth(life - 4, life, t)) * 0.8F);
        }
    }
}
