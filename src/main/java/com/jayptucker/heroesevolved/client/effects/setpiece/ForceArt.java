package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient.Active;
import com.jayptucker.heroesevolved.invisibility.ForceGeometry;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.UP;
import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.hash;
import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.rgb;
import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.smooth;
import static net.minecraft.util.Mth.PI;
import static net.minecraft.util.Mth.TWO_PI;
import static net.minecraft.util.Mth.cos;
import static net.minecraft.util.Mth.sin;

/**
 * Invisibility's force fields, drawn as one idea: <b>a lens of bent light made solid</b>. Every field is a clear,
 * faintly blue sheet or shell with a bright rim where you see it edge-on and a fine triangular facet lattice that
 * flashes where it is struck. The shield is a bowed lens, the bridge and barrier are flat panes, the bubble is a
 * geodesic cage of great circles, the push and the nova are the same lens travelling outward. Making something
 * invisible is the lens closing round it until even its rim is gone.
 *
 * <p>Palette: {@code 9CC8FF} field (the body of every sheet), {@code E8F4FF} edge (rims and facet seams),
 * {@code 3F6FD8} deep (halos), {@code B6A2FF} sheen (the violet iridescence on bubbles and pressure), and
 * {@code 1E2A44} stress (cracks as a bubble crushes, drawn with normal blending).</p>
 */
final class ForceArt {
    static final float[] FIELD = rgb(0x9CC8FF), EDGE = rgb(0xE8F4FF), DEEP = rgb(0x3F6FD8), SHEEN = rgb(0xB6A2FF),
            STRESS = rgb(0x1E2A44);

    private ForceArt() {}

    static void draw(Canvas c, Active a, float t) {
        switch (a.piece()) {
            case FORCE_SHIELD -> shield(c, a, t);
            case FORCE_PUSH -> push(c, a, t);
            case FORCE_FORM -> form(c, a, t);
            case FORCE_BRIDGE -> bridge(c, a, t, 1);
            case FORCE_WALL -> wall(c, a, t, 1);
            case FORCE_BUBBLE -> bubble(c, a, t);
            case FORCE_CRUSH -> crush(c, a, t);
            case FORCE_BEAD -> bead(c, a, t);
            case FORCE_NOVA_CHARGE -> novaCharge(c, a, t);
            case FORCE_NOVA -> nova(c, a, t);
            case FORCE_DISC -> disc(c, a, t);
            case FORCE_VANISH -> vanish(c, a, t);
            case FORCE_NET -> net(c, a, t);
            case FORCE_SHATTER -> shatter(c, a, t);
            case FORCE_RIPPLE -> ripple(c, a, t);
            default -> { }
        }
    }

    // ==================================================================
    // Shared primitives
    // ==================================================================

    private static boolean minimal() { return Minecraft.getInstance().options.particles().get() == ParticleStatus.MINIMAL; }
    private static boolean detail() { return Minecraft.getInstance().options.particles().get() == ParticleStatus.ALL; }
    private static float clock(Canvas c, float t) { return c.level == null ? t : (c.level.getGameTime() % 24000L) + c.partial; }
    private static float out(float x) { x = Mth.clamp(x, 0, 1); return 1 - (1 - x) * (1 - x) * (1 - x); }

    /** A held field stops drawing the moment a later piece replaces it (crushed, popped, thrown, or a fresh copy). */
    private static boolean gone(Canvas c, Active a, Piece... by) {
        return c.level != null && SetPieceClient.superseded(a, java.util.EnumSet.copyOf(java.util.List.of(by)), c.level.getGameTime());
    }

    /** Two directions across the plane whose normal is {@code n}. */
    static Vec3[] across(Vec3 n) {
        Vec3 u = Math.abs(n.y) > 0.9 ? new Vec3(1, 0, 0).cross(n) : UP.cross(n);
        u = u.normalize();
        return new Vec3[] {u, n.cross(u).normalize()};
    }

    /** A point on a round lens: {@code (x, y)} across it in blocks, bowed {@code sag} toward {@code n} at its middle. */
    private static Vec3 onLens(Vec3 centre, Vec3 n, Vec3[] b, float radius, float sag, float x, float y) {
        float r2 = Math.min(1, (x * x + y * y) / (radius * radius));
        return centre.add(b[0].scale(x)).add(b[1].scale(y)).add(n.scale(sag * (1 - r2)));
    }

    /** A glowing line in three layers: a wide faint halo, the coloured body, a thin bright core. */
    static void line3(Canvas c, Vec3[] points, float width, float alpha) {
        if (alpha <= 0.003F) return;
        c.ribbon(points, width * 2.6F, DEEP, alpha * 0.35F, alpha * 0.35F);
        c.ribbon(points, width, FIELD, alpha * 0.8F, alpha * 0.8F);
        c.ribbon(points, width * 0.32F, EDGE, alpha, alpha);
    }

    private static Vec3[] loop(Vec3 centre, Vec3[] b, float radius, Vec3 n, float sag, int steps) {
        Vec3[] p = new Vec3[steps + 1];
        for (int i = 0; i <= steps; i++) {
            float a = i * TWO_PI / steps;
            p[i] = onLens(centre, n, b, radius, sag, cos(a) * radius, sin(a) * radius);
        }
        return p;
    }

    /**
     * The lens: a round, bowed sheet of field colour, clear in the middle and brighter toward the rim, with a
     * triangular facet lattice and a three-layer rim. {@code flash} brightens the facets (a hit, a release).
     */
    static void lens(Canvas c, Vec3 centre, Vec3 n, float radius, float sag, float spin, float alpha, float flash) {
        if (alpha <= 0.003F || radius <= 0.02F) return;
        Vec3[] b = across(n);
        if (!c.glow()) return;
        int rings = 6, segs = 28;
        for (int i = 0; i < rings; i++)
            for (int j = 0; j < segs; j++) {
                float r0 = radius * i / rings, r1 = radius * (i + 1) / rings;
                float a0 = j * TWO_PI / segs, a1 = (j + 1) * TWO_PI / segs;
                Vec3 p00 = onLens(centre, n, b, radius, sag, cos(a0) * r0, sin(a0) * r0), p01 = onLens(centre, n, b, radius, sag, cos(a1) * r0, sin(a1) * r0);
                Vec3 p10 = onLens(centre, n, b, radius, sag, cos(a0) * r1, sin(a0) * r1), p11 = onLens(centre, n, b, radius, sag, cos(a1) * r1, sin(a1) * r1);
                float k0 = i / (float) rings, k1 = (i + 1) / (float) rings;
                float al0 = alpha * (0.05F + 0.22F * k0 * k0 * k0), al1 = alpha * (0.05F + 0.22F * k1 * k1 * k1);
                c.tri(p00, p10, p11, FIELD, al0, al1, al1);
                if (i > 0) c.tri(p00, p11, p01, FIELD, al0, al1, al0);
            }
        float spacing = Math.max(0.32F, radius / 3.2F);
        lattice(c, centre, n, b, radius, sag, spacing, spin, alpha * (0.3F + 0.6F * flash));
        line3(c, loop(centre, b, radius, n, sag, 40), 0.055F, alpha);
    }

    /** Three families of parallel lines clipped to a disc: the triangular facets of a lens. */
    private static void lattice(Canvas c, Vec3 centre, Vec3 n, Vec3[] b, float radius, float sag, float spacing, float spin, float alpha) {
        if (alpha <= 0.003F) return;
        for (int k = 0; k < 3; k++) {
            float ang = spin + k * PI / 3;
            float dx = cos(ang), dy = sin(ang), mx = -dy, my = dx;
            for (float o = -radius + spacing * 0.5F; o < radius; o += spacing) {
                float half = Mth.sqrt(Math.max(0, radius * radius - o * o));
                if (half < 0.08F) continue;
                int steps = 6;
                Vec3[] p = new Vec3[steps + 1];
                float[] al = new float[steps + 1], w = new float[steps + 1];
                for (int s = 0; s <= steps; s++) {
                    float tau = -half + 2 * half * s / steps;
                    p[s] = onLens(centre, n, b, radius, sag, mx * o + dx * tau, my * o + dy * tau);
                    al[s] = alpha * (0.35F + 0.65F * sin(PI * s / steps));
                    w[s] = 0.012F;
                }
                c.ribbon(p, w, EDGE, al);
            }
        }
    }

    /** A flat pane between {@code corner}, {@code corner + u} and {@code corner + v}: fill, facets and lit edges. */
    static void pane(Canvas c, Vec3 corner, Vec3 u, Vec3 v, float alpha, float spacing, boolean edges) {
        if (alpha <= 0.003F || !c.glow()) return;
        Vec3 p1 = corner.add(u), p2 = corner.add(u).add(v), p3 = corner.add(v);
        c.tri(corner, p1, p2, FIELD, alpha * 0.16F, alpha * 0.16F, alpha * 0.16F);
        c.tri(corner, p2, p3, FIELD, alpha * 0.16F, alpha * 0.16F, alpha * 0.16F);
        float lu = (float) u.length(), lv = (float) v.length();
        if (lu < 1e-3 || lv < 1e-3) return;
        Vec3 eu = u.scale(1 / lu), ev = v.scale(1 / lv);
        // Facet lattice clipped to the pane (Liang–Barsky in the pane's own coordinates).
        for (int k = 0; k < 3; k++) {
            float ang = k * PI / 3 + 0.2F;
            float dx = cos(ang), dy = sin(ang), mx = -dy, my = dx;
            float reach = lu + lv;
            for (float o = -reach; o < reach; o += spacing) {
                float ox = mx * o + lu * 0.5F, oy = my * o + lv * 0.5F;
                float t0 = -reach, t1 = reach;
                float[] pp = {-dx, dx, -dy, dy}, qq = {ox, lu - ox, oy, lv - oy};
                boolean inside = true;
                for (int e = 0; e < 4 && inside; e++) {
                    if (Math.abs(pp[e]) < 1e-6) { if (qq[e] < 0) inside = false; continue; }
                    float r = qq[e] / pp[e];
                    if (pp[e] < 0) t0 = Math.max(t0, r); else t1 = Math.min(t1, r);
                    if (t0 > t1) inside = false;
                }
                if (!inside || t1 - t0 < 0.05F) continue;
                Vec3 a = corner.add(eu.scale(ox + dx * t0)).add(ev.scale(oy + dy * t0));
                Vec3 z = corner.add(eu.scale(ox + dx * t1)).add(ev.scale(oy + dy * t1));
                c.line(a, z, 0.014F, EDGE, alpha * 0.45F, alpha * 0.45F);
            }
        }
        if (edges) line3(c, new Vec3[] {corner, p1, p2, p3, corner}, 0.045F, alpha);
    }

    /** A geodesic cage: a see-through shell with six great circles, turned by {@code spin} about {@code axis}. */
    static void cage(Canvas c, Vec3 at, float radius, float spin, Vec3 axis, float alpha, float sheen, float[] rim) {
        if (alpha <= 0.003F || radius <= 0.02F) return;
        if (c.glow()) {
            c.shell(at, radius, radius, radius, PI, FIELD, alpha * 0.42F, 0.04F, Canvas.PLAIN, spin);
            if (sheen > 0) c.shell(at, radius * 1.03F, radius * 1.03F, radius * 1.03F, PI, SHEEN, alpha * sheen * 0.4F, 0F, Canvas.PLAIN, -spin);
            Vec3[] b = across(axis);
            float cs = cos(spin), sn = sin(spin);
            // Six great circles round the icosahedron's axes give a lattice of triangles that turns as one.
            for (int i = 0; i < 6; i++) {
                float lat = i == 0 ? 0 : 1.1071F, lon = i == 0 ? 0 : (i - 1) * TWO_PI / 5;
                Vec3 local = new Vec3(sin(lat) * cos(lon), cos(lat), sin(lat) * sin(lon));
                double x = local.x * cs - local.z * sn, z = local.x * sn + local.z * cs;
                Vec3 world = b[0].scale(x).add(axis.normalize().scale(local.y)).add(b[1].scale(z));
                c.ring(at, world, radius, 0.022F, 0, TWO_PI, rim, alpha * 0.55F);
            }
            c.glow(at, radius * 1.25F, DEEP, alpha * 0.08F);
        }
    }

    private static Vec3 eye(Entity e, float partial) { return e.getEyePosition(partial); }

    // ==================================================================
    // H: Lens Shield and Force Push
    // ==================================================================

    private static void shield(Canvas c, Active a, float t) {
        Entity e = a.bound(c);
        if (e == null || gone(c, a, Piece.FORCE_PUSH, Piece.FORCE_SHIELD)) return;
        boolean first = a.target().y > 0.5;
        int pressure = (int) a.target().x;
        float radius = a.scale() * (first ? out(t / 4) : 1);
        float alpha = Mth.clamp(t / 2, 0, 1) * Mth.clamp((a.piece().ticks - t) / 2, 0, 1);
        // Through your own eyes the lens sits right in front of you: keep it a faint rim, not a window frame.
        var mc = Minecraft.getInstance();
        boolean own = e == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
        if (own) alpha *= 0.35F;
        Vec3 eye = eye(e, c.partial), look = e.getViewVector(c.partial);
        Vec3 centre = ForceGeometry.shieldCentre(eye, look);
        float spin = clock(c, t) * 0.02F;
        float flash = first ? 1 - smooth(0, 6, t) : 0.15F * pressure;
        lens(c, centre, look, radius, (float) ForceGeometry.SHIELD_SAG, spin, alpha * 0.95F, own ? flash * 0.3F : flash);
        if (!c.glow()) return;
        // Stored Pressure shows as violet rings tightening inside the rim.
        for (int i = 0; i < pressure; i++) {
            float r = radius * (0.88F - i * 0.1F);
            float bow = (float) ForceGeometry.SHIELD_SAG * (1 - (r * r) / (radius * radius));
            c.ring(centre.add(look.scale(bow)), look, r, 0.025F, 0, TWO_PI, SHEEN, alpha * (0.45F + 0.25F * sin(clock(c, t) * 0.5F + i)));
        }
        // Light from the palms holds the lens up.
        Vec3 right = look.cross(UP);
        right = right.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : right.normalize();
        for (int side = -1; side <= 1; side += 2) {
            Vec3 palm = eye.add(look.scale(0.55)).add(right.scale(side * 0.3)).add(0, -0.32, 0);
            c.glow(palm, 0.22F, FIELD, alpha * 0.45F);
            c.line(palm, centre.add(right.scale(side * radius * 0.6)), 0.018F, EDGE, alpha * 0.4F, 0);
        }
    }

    private static void push(Canvas c, Active a, float t) {
        Vec3 start = a.origin(), span = a.target().subtract(start);
        float range = (float) span.length();
        if (range < 0.1F) return;
        Vec3 dir = span.scale(1 / range);
        float radius = a.scale(), arrive = range / (float) ForceGeometry.PUSH_SPEED;
        float life = a.piece().ticks, fade = Mth.clamp((life - t) / 4, 0, 1);
        if (c.glow()) {
            c.glow(start, 0.9F * (1 - smooth(0, 4, t)) + 0.1F, EDGE, 0.8F * fade);
            int ghosts = minimal() ? 1 : 3;
            for (int i = 0; i < ghosts; i++) {
                float ti = t - i * 0.9F;
                if (ti < 0) continue;
                float d = (float) Math.min(range, ti * ForceGeometry.PUSH_SPEED);
                float grow = 1 + 0.18F * d / range;
                float spent = Mth.clamp((ti - arrive) / 4, 0, 1);
                float alpha = (i == 0 ? 1 : 0.4F / i) * (1 - spent) * fade;
                lens(c, start.add(dir.scale(d)), dir, radius * grow * (1 + 0.25F * spent), 0.55F, ti * 0.05F, alpha, i == 0 ? 1 - spent : 0);
            }
            // A wake of pressure rings the wave leaves behind it.
            for (int i = 1; i <= (minimal() ? 2 : 5); i++) {
                float d = range * i / 6F, passed = t - d / (float) ForceGeometry.PUSH_SPEED;
                if (passed < 0) continue;
                float al = (1 - smooth(0, 7, passed)) * 0.4F * fade;
                c.ring(start.add(dir.scale(d)), dir, radius * (0.6F + 0.4F * smooth(0, 6, passed)), 0.03F, 0, TWO_PI, FIELD, al);
            }
        }
    }

    // ==================================================================
    // Z: Force Bridge and the Force Barrier
    // ==================================================================

    private record Span(Vec3 origin, Direction dir, double slope, int length) {}

    private static Span span(Active a) {
        Vec3 d = a.target().subtract(a.origin());
        double flat = Math.sqrt(d.x * d.x + d.z * d.z);
        Direction dir = flat < 0.5 ? Direction.NORTH : Direction.getNearest(d.x, 0, d.z);
        int steps = (int) Math.round(flat);
        double slope = steps == 0 ? 0 : d.y / steps;
        return new Span(a.origin(), dir, slope, steps + 1);
    }

    private static void bridge(Canvas c, Active a, float t, float strength) {
        if (gone(c, a, Piece.FORCE_SHATTER, Piece.FORCE_BRIDGE)) return;
        Span s = span(a);
        float alpha = strength * Mth.clamp(t / 2, 0, 1) * Mth.clamp((a.piece().ticks - t) / 2, 0, 1);
        bridgeSteps(c, s, alpha, s.length);
    }

    /** Draws the first {@code shown} steps of a bridge: one pane over all three lanes per step, risers between. */
    private static void bridgeSteps(Canvas c, Span s, float alpha, float shown) {
        Direction side = s.dir.getClockWise();
        Vec3 along = new Vec3(s.dir.getStepX(), 0, s.dir.getStepZ()), across = new Vec3(side.getStepX(), 0, side.getStepZ());
        Vec3 lastLeft = null, lastRight = null;
        for (int i = 0; i < s.length && i < shown; i++) {
            float k = Mth.clamp(shown - i, 0, 1);
            BlockPos cell = ForceGeometry.bridgeCell(s.origin, s.dir, s.slope, i, 0);
            Vec3 middle = new Vec3(cell.getX() + 0.5, cell.getY() + 1.01, cell.getZ() + 0.5);
            Vec3 corner = middle.subtract(along.scale(0.5)).subtract(across.scale(1.5));
            pane(c, corner, along, across.scale(3), alpha * k, 0.55F, false);
            Vec3 left = corner, right = corner.add(across.scale(3));
            if (lastLeft != null && Math.abs(lastLeft.y - left.y) > 0.01) {
                Vec3 low = left.y < lastLeft.y ? left : lastLeft;
                pane(c, low, across.scale(3), new Vec3(0, Math.abs(left.y - lastLeft.y), 0), alpha * k * 0.6F, 0.55F, false);
            }
            if (c.glow()) {
                // Rails along both outer edges.
                line3(c, new Vec3[] {left, left.add(along)}, 0.04F, alpha * k);
                line3(c, new Vec3[] {right, right.add(along)}, 0.04F, alpha * k);
            }
            lastLeft = left.add(along); lastRight = right.add(along);
        }
        if (lastRight != null && c.glow()) line3(c, new Vec3[] {lastLeft, lastRight}, 0.04F, alpha);
    }

    private static Vec3[] wallFrame(Active a, float halfWidth) {
        Vec3 n = a.target().subtract(a.origin()).multiply(1, 0, 1);
        n = n.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : n.normalize();
        Vec3 side = new Vec3(-n.z, 0, n.x);
        Vec3 corner = a.origin().subtract(side.scale(halfWidth + 0.5));
        return new Vec3[] {corner, side.scale(2 * halfWidth + 1), new Vec3(0, ForceGeometry.WALL_HEIGHT, 0), n};
    }

    private static void wall(Canvas c, Active a, float t, float strength) {
        if (gone(c, a, Piece.FORCE_SHATTER, Piece.FORCE_WALL)) return;
        float alpha = strength * Mth.clamp(t / 2, 0, 1) * Mth.clamp((a.piece().ticks - t) / 2, 0, 1);
        Vec3[] f = wallFrame(a, a.scale());
        float shimmer = 0.85F + 0.15F * sin(clock(c, t) * 0.3F);
        pane(c, f[0], f[1], f[2], alpha * shimmer, 0.6F, true);
        if (c.glow()) {
            // The faint back face gives the wall its block of thickness.
            Vec3 back = f[3].scale(0.5);
            pane(c, f[0].add(back), f[1], f[2], alpha * 0.35F, 2.4F, false);
            pane(c, f[0].subtract(back), f[1], f[2], alpha * 0.35F, 2.4F, false);
        }
    }

    /** The span or wall forming: a bright seam runs along the bridge, or rises up the wall, and leaves the field behind. */
    private static void form(Canvas c, Active a, float t) {
        float life = a.piece().ticks, k = out(t / (life - 2));
        float fade = Mth.clamp((life - t) / 3, 0, 1);
        if (a.scale() >= ForceGeometry.SHELTER_FLAG) {
            Vec3[] f = wallFrame(a, a.scale() - ForceGeometry.SHELTER_FLAG);
            Vec3 top = f[0].add(f[2].scale(k));
            pane(c, f[0], f[1], f[2].scale(k), fade, 0.6F, false);
            if (c.glow()) {
                line3(c, new Vec3[] {top, top.add(f[1])}, 0.09F, fade);
                c.glow(top.add(f[1].scale(0.5)), 1.4F, EDGE, 0.35F * fade);
            }
            return;
        }
        Span s = span(a);
        float shown = k * s.length;
        bridgeSteps(c, s, fade, shown);
        if (c.glow()) {
            int i = Math.min(s.length - 1, (int) shown);
            BlockPos cell = ForceGeometry.bridgeCell(s.origin, s.dir, s.slope, i, 0);
            Vec3 front = new Vec3(cell.getX() + 0.5, cell.getY() + 1.05, cell.getZ() + 0.5);
            Direction side = s.dir.getClockWise();
            Vec3 across = new Vec3(side.getStepX(), 0, side.getStepZ()).scale(1.6);
            line3(c, new Vec3[] {front.subtract(across), front.add(across)}, 0.08F, fade);
            c.glow(front, 1.1F, EDGE, 0.4F * fade);
        }
    }

    // ==================================================================
    // X: bubbles, the crush, the roll
    // ==================================================================

    private static Vec3 bubbleAt(Canvas c, Active a) {
        Entity e = a.bound(c);
        return e == null ? a.origin() : e.getPosition(c.partial).add(0, e.getBbHeight() * 0.5, 0);
    }

    private static void bubble(Canvas c, Active a, float t) {
        if (gone(c, a, Piece.FORCE_CRUSH, Piece.FORCE_SHATTER, Piece.FORCE_RIPPLE, Piece.FORCE_BUBBLE)) return;
        boolean shelter = a.scale() >= ForceGeometry.SHELTER_FLAG;
        float radius = shelter ? a.scale() - ForceGeometry.SHELTER_FLAG : a.scale();
        Vec3 at = bubbleAt(c, a);
        float alpha = Mth.clamp(t / 2, 0, 1) * Mth.clamp((a.piece().ticks - t) / 2, 0, 1);
        Vec3 rolling = a.target().subtract(a.origin());
        float time = clock(c, t);
        Vec3 axis = rolling.lengthSqr() > 0.01 ? rolling.normalize().cross(UP) : UP;
        if (axis.lengthSqr() < 1e-4) axis = UP;
        float spin = rolling.lengthSqr() > 0.01 ? time * 0.45F : time * (shelter ? 0.015F : 0.04F);
        if (shelter) cage(c, at, radius * 1.08F, spin, axis, alpha * 0.7F, 0, EDGE);
        else {
            cage(c, at, radius, spin, axis, alpha, 0.8F, EDGE);
            if (c.glow()) c.ring(at.add(0, -radius * 0.98F, 0), UP, radius * 0.25F, 0.05F, 0, TWO_PI, SHEEN, alpha * 0.3F);
        }
    }

    private static void crush(Canvas c, Active a, float t) {
        if (gone(c, a, Piece.FORCE_SHATTER)) return;
        Vec3 at = bubbleAt(c, a);
        float base = a.scale(), k = (float) ForceGeometry.crushScale(t, 8);
        float radius = base * k, strain = smooth(0, 8, t);
        float alpha = Mth.clamp((a.piece().ticks - t) / 2, 0, 1);
        cage(c, at, radius, clock(c, t) * 0.04F + strain * 1.4F, UP, alpha, 0.8F + strain, EDGE);
        if (c.glow()) c.glow(at, radius * 0.9F, SHEEN, 0.25F * strain * alpha);
        if (c.soft()) {
            // Dark stress cracks spread over the shell as it closes.
            int cracks = minimal() ? 3 : 7;
            for (int i = 0; i < cracks; i++) {
                float th = hash(a.seed(), i) * TWO_PI, ph = 0.4F + hash(a.seed(), i + 20) * 2.3F;
                Vec3 n = new Vec3(sin(ph) * cos(th), cos(ph), sin(ph) * sin(th));
                Vec3 n2 = new Vec3(sin(ph + 0.6F) * cos(th + 0.5F), cos(ph + 0.6F), sin(ph + 0.6F) * sin(th + 0.5F));
                c.bolt(at.add(n.scale(radius * 1.01)), at.add(n2.scale(radius * 1.01)), a.seed() + i, 0.12F, 0.025F, STRESS,
                        alpha * strain * 0.85F);
            }
        }
    }

    // ==================================================================
    // Pinpoint, the Nova, the disc, the net
    // ==================================================================

    private static void bead(Canvas c, Active a, float t) {
        Entity e = a.bound(c);
        Vec3 to = e == null ? a.target() : e.getPosition(c.partial).add(0, e.getBbHeight() * 0.5, 0);
        Vec3 from = a.origin();
        float travel = 3, k = out(t / travel);
        Vec3 bead = from.add(to.subtract(from).scale(k));
        float fade = Mth.clamp((a.piece().ticks - t) / 3, 0, 1);
        if (!c.glow()) return;
        if (t < travel + 0.5F) {
            line3(c, new Vec3[] {from.add(to.subtract(from).scale(Math.max(0, k - 0.35F))), bead}, 0.05F, 1);
            c.shell(bead, 0.16F, 0.16F, 0.16F, PI, FIELD, 0.9F, 0.2F, Canvas.PLAIN, 0);
            c.glow(bead, 0.35F, EDGE, 0.8F);
        } else {
            float pop = t - travel;
            Vec3 dir = to.subtract(from);
            dir = dir.lengthSqr() < 1e-4 ? UP : dir.normalize();
            lens(c, to, dir.reverse(), 0.3F + pop * 0.25F, 0.15F, pop * 0.2F, fade * (1 - smooth(0, 6, pop)), 1);
            c.glow(to, 0.8F * (1 - smooth(0, 3, pop)), EDGE, 0.8F * fade);
        }
    }

    private static void novaCharge(Canvas c, Active a, float t) {
        Entity e = a.bound(c);
        Vec3 feet = e == null ? a.origin() : e.getPosition(c.partial);
        Vec3 core = feet.add(0, 1, 0);
        float life = a.piece().ticks, gather = smooth(0, 20, t);
        float fade = Mth.clamp((life - t) / 2, 0, 1);
        if (c.glow()) {
            // Shells close in and stack round the body, brighter as they gather.
            for (int i = 0; i < (minimal() ? 2 : 3); i++) {
                float ph = (t / 20F + i / 3F) % 1;
                float r = Mth.lerp(ph, 3.6F, 1.1F);
                cage(c, core, r, clock(c, t) * 0.05F * (i % 2 == 0 ? 1 : -1), UP, (0.15F + 0.35F * gather) * sin(PI * ph) * fade, 0.4F, EDGE);
            }
            c.glow(core, 0.6F + 1.1F * gather, EDGE, (0.25F + 0.5F * gather) * fade);
            // The reach is marked on the ground before it lands: a ring at the full radius, tightening its glow.
            float reach = a.scale();
            c.ring(feet.add(0, 0.08, 0), UP, reach, 0.06F + 0.1F * (1 - gather), 0, TWO_PI, FIELD, 0.5F * fade * Mth.clamp(t / 4, 0, 1));
            c.ring(feet.add(0, 0.08, 0), UP, reach, 0.02F, 0, TWO_PI, EDGE, 0.8F * fade * Mth.clamp(t / 4, 0, 1));
            if (reach > ForceGeometry.NOVA_RADIUS + 1 && !minimal()) {
                // Implosion: spokes of pull run inward along the ground.
                for (int i = 0; i < 12; i++) {
                    float ang = i * TWO_PI / 12 + hash(a.seed(), i) * 0.2F, run = (t * 0.08F + hash(a.seed(), i + 40)) % 1;
                    c.spoke(feet.add(0, 0.1, 0), UP, ang, reach * (1 - run), reach * (1 - run) - 1.6F, 0.05F, SHEEN, 0.6F * fade * sin(PI * run));
                }
            }
        }
    }

    private static void nova(Canvas c, Active a, float t) {
        Vec3 at = a.origin();
        float reach = a.scale(), life = a.piece().ticks;
        float k = out(t / 8), r = reach * k;
        float fade = 1 - smooth(6, life, t);
        if (c.glow()) {
            c.glow(at, (1.5F + reach * 0.18F) * (1 - smooth(0, 5, t)), EDGE, 0.55F * (1 - smooth(0, 6, t)));
            cage(c, at, r, t * 0.03F, UP, fade, 0.6F, EDGE);
            cage(c, at, reach * out(Math.max(0, t - 2) / 9), -t * 0.03F, UP, fade * 0.45F, 1, FIELD);
            // A shock ring along the ground, three layers.
            Vec3 ground = at.add(0, -0.92, 0);
            c.ring(ground, UP, r, 0.5F, 0, TWO_PI, DEEP, 0.3F * fade);
            c.ring(ground, UP, r, 0.16F, 0, TWO_PI, FIELD, 0.7F * fade);
            c.ring(ground, UP, r, 0.05F, 0, TWO_PI, EDGE, fade);
        }
        if (c.glow() && !minimal()) {
            // Facets torn off the shell drift outward and fade.
            int n = detail() ? 24 : 12;
            for (int i = 0; i < n; i++) facet(c, at, a.seed(), i, r * 0.9F, t, reach * 0.06F, fade * 0.8F);
        }
    }

    /** A small triangle of force flying outward from {@code at}, tumbling. */
    private static void facet(Canvas c, Vec3 at, int seed, int i, float distance, float t, float drift, float alpha) {
        float th = hash(seed, i) * TWO_PI, ph = 0.3F + hash(seed, i + 50) * 2.2F, size = 0.18F + hash(seed, i + 90) * 0.28F;
        Vec3 n = new Vec3(sin(ph) * cos(th), cos(ph), sin(ph) * sin(th));
        Vec3 p = at.add(n.scale(distance + drift * t)).add(0, -0.004 * t * t, 0);
        Vec3[] b = across(n);
        float spin = t * (0.2F + hash(seed, i + 7) * 0.3F);
        Vec3 u = b[0].scale(cos(spin)).add(b[1].scale(sin(spin))).scale(size);
        Vec3 v = b[0].scale(cos(spin + 2.1F)).add(b[1].scale(sin(spin + 2.1F))).scale(size);
        Vec3 w = b[0].scale(cos(spin + 4.2F)).add(b[1].scale(sin(spin + 4.2F))).scale(size);
        c.tri(p.add(u), p.add(v), p.add(w), FIELD, alpha * 0.35F, alpha * 0.35F, alpha * 0.35F);
        c.line(p.add(u), p.add(v), 0.015F, EDGE, alpha, alpha * 0.5F);
    }

    private static void disc(Canvas c, Active a, float t) {
        Entity e = a.bound(c);
        if (gone(c, a, Piece.FORCE_NET, Piece.FORCE_DISC)) return;
        Vec3 feet = e == null ? a.origin() : e.getPosition(c.partial);
        float alpha = Mth.clamp(t / 2, 0, 1) * Mth.clamp((a.piece().ticks - t) / 2, 0, 1);
        float radius = a.scale(), spin = clock(c, t) * 0.12F;
        Vec3 at = feet.add(0, -0.06, 0);
        lens(c, at, UP, radius, -0.12F, spin, alpha, 0.25F);
        if (c.glow() && !minimal()) {
            // Rings shed below the disc show it holding you up.
            for (int i = 0; i < 3; i++) {
                float ph = (clock(c, t) * 0.08F + i / 3F) % 1;
                c.ring(at.add(0, -0.15 - ph * 0.9, 0), UP, radius * (0.9F - 0.4F * ph), 0.03F, 0, TWO_PI, FIELD, alpha * 0.4F * (1 - ph));
            }
        }
    }

    private static void net(Canvas c, Active a, float t) {
        Vec3 at = a.origin().add(0, 0.05, 0);
        float life = a.piece().ticks, fade = 1 - smooth(6, life, t);
        float dip = sin(Math.min(1, t / 6F) * PI) * 0.35F;
        lens(c, at.add(0, -dip, 0), UP, a.scale() * (0.6F + 0.4F * out(t / 3)), -dip, t * 0.04F, fade, 1 - smooth(0, 8, t));
        if (c.glow())
            for (int i = 0; i < 3; i++) {
                float ti = t - i * 1.5F;
                if (ti <= 0) continue;
                c.ring(at, UP, a.scale() + ti * 0.35F, 0.04F, 0, TWO_PI, FIELD, 0.6F * (1 - smooth(0, 9, ti)));
            }
    }

    // ==================================================================
    // Unseen: Vanishing Touch and Vanishing Squad
    // ==================================================================

    private static void vanish(Canvas c, Active a, float t) {
        Entity e = a.bound(c);
        float life = a.piece().ticks;
        if (e == null) {
            // Vanishing Squad: a lens-dome sweeps out to its reach and everyone inside goes clear.
            Vec3 feet = a.origin();
            float reach = a.scale(), k = out(t / 10), fade = 1 - smooth(8, life, t);
            if (c.glow()) {
                c.shell(feet, reach * k, reach * k * 0.6F, reach * k, PI / 2, FIELD, 0.35F * fade, 0.02F, Canvas.PLAIN, t * 0.02F);
                c.ring(feet.add(0, 0.08, 0), UP, reach * k, 0.12F, 0, TWO_PI, SHEEN, 0.5F * fade);
                c.ring(feet.add(0, 0.08, 0), UP, reach * k, 0.03F, 0, TWO_PI, EDGE, 0.8F * fade);
            }
            return;
        }
        Vec3 at = e.getPosition(c.partial).add(0, e.getBbHeight() * 0.5, 0);
        float base = a.scale(), k = smooth(2, life - 2, t);
        // The lens closes round the body; its rim is the last thing to go.
        float radius = base * (1.15F - 0.45F * k), alpha = (1 - smooth(life - 6, life, t)) * Mth.clamp(t / 2, 0, 1);
        cage(c, at, radius, t * 0.05F, UP, alpha * (1 - 0.6F * k), 0.9F, EDGE);
        if (c.glow()) {
            c.ring(at, UP, radius * 1.02F, 0.05F, 0, TWO_PI, SHEEN, alpha * 0.8F);
            c.glow(at, radius * (1 - k) + 0.1F, EDGE, 0.35F * alpha * (1 - k));
        }
    }

    // ==================================================================
    // Breaking and striking
    // ==================================================================

    private static void shatter(Canvas c, Active a, float t) {
        float life = a.piece().ticks, fade = 1 - smooth(4, life, t);
        if (!c.glow()) return;
        int n = minimal() ? 8 : detail() ? 28 : 16;
        Vec3 o = a.origin(), d = a.target().subtract(o);
        if (d.lengthSqr() < 1e-4) {
            // A bubble pops: its facets fly off the sphere.
            float r = a.scale();
            c.glow(o, r * 1.4F * (1 - smooth(0, 4, t)), EDGE, 0.7F);
            c.ring(o, UP, r * (1 + 0.15F * t), 0.04F, 0, TWO_PI, FIELD, 0.6F * (1 - smooth(0, 8, t)));
            for (int i = 0; i < n; i++) facet(c, o, a.seed(), i, r, t, 0.18F, fade);
            return;
        }
        // A bridge or wall breaks outward: facets scattered over its area fly away and fall.
        boolean isWall = a.scale() >= ForceGeometry.SHELTER_FLAG;
        for (int i = 0; i < n; i++) {
            Vec3 from;
            if (isWall) {
                Vec3[] f = wallFrame(a, a.scale() - ForceGeometry.SHELTER_FLAG);
                from = f[0].add(f[1].scale(hash(a.seed(), i + 300))).add(f[2].scale(hash(a.seed(), i + 400)));
            } else {
                Span s = span(a);
                int step = (int) (hash(a.seed(), i + 300) * s.length);
                BlockPos cell = ForceGeometry.bridgeCell(s.origin, s.dir, s.slope, step, (int) (hash(a.seed(), i + 400) * 3) - 1);
                from = new Vec3(cell.getX() + 0.5, cell.getY() + 1, cell.getZ() + 0.5);
            }
            facet(c, from, a.seed(), i, 0.1F, t, 0.22F, fade);
        }
    }

    private static void ripple(Canvas c, Active a, float t) {
        Vec3 at = a.origin(), n = a.target().subtract(at);
        n = n.lengthSqr() < 1e-4 ? UP : n.normalize();
        float life = a.piece().ticks, size = Math.max(0.6F, a.scale() * 0.7F);
        if (!c.glow()) return;
        for (int i = 0; i < 2; i++) {
            float ti = t - i * 1.6F;
            if (ti <= 0) continue;
            float al = 1 - smooth(0, life - 1, ti);
            c.ring(at, n, size * 0.2F + ti * 0.18F * size, 0.035F, 0, TWO_PI, i == 0 ? EDGE : SHEEN, 0.8F * al);
        }
        lens(c, at, n, size * 0.5F * out(t / 3), 0.05F, 0, 0.6F * (1 - smooth(0, life, t)), 1);
        c.glow(at, 0.4F, EDGE, 0.6F * (1 - smooth(0, 4, t)));
    }
}
