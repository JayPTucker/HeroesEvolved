package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient.Active;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.wither.RiftWitherEntity;
import com.jayptucker.heroesevolved.wither.WitherGeometry;
import com.jayptucker.heroesevolved.wither.WitherGeometry.Move;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import java.util.List;
import java.util.Set;
import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.*;
import static net.minecraft.util.Mth.*;

/**
 * The Wither: a broken cage around a hungry star. Every drawing speaks in four things: the breach (a torn, jagged rim
 * of mint and white round violet-black depth that recedes behind it), bone (pale spires and plates, lit and solid), the
 * star's light (mint, then violet, then ember as the phases turn) and soot (cracks and scorch, drawn dark). Every
 * warning fills over exactly its move's windup and has the shape the server will hit; none of it is particles.
 */
final class WitherArt {
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final float[] MINT = rgb(0x6EE9CF), WHITE = rgb(0xE8FFF4), VIOLET = rgb(0xA18AE4), DEEP = rgb(0x2C1E5C),
            DARK = rgb(0x05060D), EMBER = rgb(0xFF947F), BONE = rgb(0xD9D0B4), CHAR = rgb(0x1E1A19), ASH = rgb(0x6F706A),
            PLATE = rgb(0x8C877C);
    private static final Set<Piece> CANCEL = Set.of(Piece.WITHER_CANCEL, Piece.WITHER_BACKFIRE, Piece.WITHER_PHASE, Piece.WITHER_DEATH);
    private WitherArt() {}

    private static float[] tint(int phase) { return phase >= 3 ? EMBER : phase == 2 ? VIOLET : MINT; }
    private static int phaseOf(Active a, Canvas c) { return a.bound(c) instanceof RiftWitherEntity b ? b.phase() : 1; }

    static void draw(Canvas c, Active a, float t) {
        float alpha = Math.min(smooth(0, 2, t), 1 - smooth(a.piece().ticks - 4, a.piece().ticks, t));
        switch (a.piece()) {
            case WITHER_BREACH -> gate(c, a.origin(), normal(a), WitherGeometry.GATE_W, WitherGeometry.GATE_H, 1, alpha, c.time(), 7);
            case WITHER_FORM -> form(c, a, t);
            case WITHER_TEAR -> {
                float[] g = WitherGeometry.arrivalGateSize(t);
                gate(c, a.origin(), normal(a), g[0], g[1], g[2], 1 - smooth(WitherGeometry.ARRIVAL_SHUT, WitherGeometry.ARRIVAL_SHUT + 4, t), c.time(), 3);
                if (t > WitherGeometry.ARRIVAL_SHUT - 8 && t < WitherGeometry.ARRIVAL_SHUT + 12) slamFlash(c, a.origin(), normal(a), t - (WitherGeometry.ARRIVAL_SHUT - 4), 3);
            }
            case WITHER_OPEN -> open(c, a, t);
            case WITHER_AURA -> aura(c, a, alpha);
            case WITHER_WARNING -> warning(c, a, t);
            case WITHER_HEAD_WARN -> {
                if (SetPieceClient.superseded(a, CANCEL, c.level.getGameTime()) || t > WitherGeometry.HEAD_WINDUP) return;
                float p = clamp(t / WitherGeometry.HEAD_WINDUP, 0, 1), in = smooth(0, 2, t);
                if (c.glow()) {
                    glowStar(c, a.origin(), .18F + .4F * p, MINT, in);
                    c.line(a.origin(), a.target(), .02F, MINT, in * .5F, in * .2F);
                    target(c, a.target(), WitherGeometry.SKULL_RADIUS, p, MINT, in);
                }
            }
            case WITHER_MUZZLE -> {
                if (!c.glow()) return;
                float fade = 1 - smooth(0, 12, t);
                Vec3 dir = a.target().subtract(a.origin()).normalize();
                glowStar(c, a.origin(), (.35F + t * .07F) * fade, WHITE, fade);
                for (int i = 0; i < 3; i++) c.ring(a.origin().add(dir.scale(.3 + i * .35 + t * .08)), dir, .25F + i * .18F + t * .05F, .05F, 0, TWO_PI, MINT, fade * (1 - i * .25F));
            }
            case WITHER_FIRE -> fire(c, a, t);
            case WITHER_WAKE -> {
                Vec3 dir = a.target().subtract(a.origin());
                if (dir.lengthSqr() < 1e-4) return;
                Vec3 side = dir.normalize().cross(UP);
                float[] tint = tint(phaseOf(a, c));
                float fade = 1 - smooth(4, 18, t);
                if (c.glow()) for (int s : new int[]{-1, 1}) {
                    Vec3 o = side.scale(s * 2.6);
                    c.line(a.origin().add(o), a.target().add(o), .45F, tint, 0, fade * .25F);
                    c.line(a.origin().add(o), a.target().add(o), .08F, WHITE, 0, fade * .7F);
                }
                if (c.soft()) for (int i = 0; i < 3; i++) {
                    Vec3 at = a.origin().lerp(a.target(), i / 3D);
                    Vec3 ground = new Vec3(at.x, WitherGeometry.floor().y + .4 + t * .04, at.z);
                    c.cloud(ground.add(side.scale((i - 1) * 2.2)), .9F + t * .07F, .45F + t * .03F, .9F + t * .07F, i + a.seed(), t * .03F, fade * .45F, Shades.smoke(c, ASH, 0));
                }
            }
            case WITHER_ARC -> arc(c, a, t);
            case WITHER_PULSE -> pulse(c, a, t);
            case WITHER_IMPACT -> impact(c, a, t);
            case WITHER_NOVA -> nova(c, a, t);
            case WITHER_BACKFIRE -> {
                float fade = 1 - smooth(14, 36, t);
                Vec3 core = a.bound(c) instanceof RiftWitherEntity b ? b.core() : a.target();
                if (c.glow()) {
                    for (int i = 0; i < 3; i++) c.bolt(a.origin(), core, (int) (t / 2) * 7 + i * 31, 1.1F, .18F * fade, i == 0 ? WHITE : MINT, fade);
                    glowStar(c, core, 1.6F * fade + .4F, WHITE, fade);
                    for (int i = 0; i < 3; i++) c.ring(a.origin(), UP, .8F + t * (.35F + i * .12F), .1F, 0, TWO_PI, i == 0 ? WHITE : MINT, fade * (1 - i * .3F));
                }
                if (c.solid()) for (int i = 0; i < 8; i++) {
                    float h1 = hash(i, 31), h2 = hash(i, 47), fly = Math.min(t, 24);
                    Vec3 at = a.origin().add((h1 - .5) * fly * .5, fly * .25 - fly * fly * .012, (h2 - .5) * fly * .5);
                    c.shard(at, at.add((h2 - .5) * .5, .55, (h1 - .5) * .5), .14F, 4, t * .2F, rgb(0x3B3346), 1);
                }
            }
            case WITHER_PHASE -> phase(c, a, t);
            case WITHER_CRACK -> {
                if (!c.glow() || !(a.bound(c) instanceof RiftWitherEntity b)) return;
                float fade = 1 - smooth(4, 16, t);
                Vec3 core = b.core(), ahead = WitherGeometry.forward(b.getYRot()), side = ahead.cross(UP);
                int cracks = 3 + 2 * a.seed();
                for (int i = 0; i < cracks; i++) {
                    double angle = hash(a.seed() * 13, i) * TWO_PI;
                    Vec3 end = core.add(side.scale(Math.cos(angle) * 2.6)).add(0, Math.sin(angle) * 2.4, 0).add(ahead.scale(.8));
                    c.bolt(core.add(ahead.scale(.9)), end, a.seed() * 17 + i, .35F, .05F, WHITE, fade);
                }
                glowStar(c, core.add(ahead.scale(.9)), 1.1F * fade, WHITE, fade);
            }
            case WITHER_SPIRES -> spires(c, a, t);
            case WITHER_SPEW -> spew(c, a, t);
            case WITHER_DEATH -> death(c, a, t);
            case WITHER_DETONATE -> detonate(c, a, t);
            default -> {}
        }
    }

    // ================================================================ the breach

    private static Vec3 normal(Active a) {
        Vec3 n = a.target().subtract(a.origin());
        return n.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : n.normalize();
    }

    /**
     * A tear in the world: a near-black body ringed by a jagged rim (violet haze, mint body, white core), a tunnel of
     * shrinking rims receding behind it away from the viewer, star specks drifting down that tunnel, cracks leaking into
     * the air round it and dark shards of broken space orbiting it. {@code open} 0 is a hairline seam, 1 fully open;
     * {@code w} and {@code h} are its half width and half height.
     */
    static void gate(Canvas c, Vec3 at, Vec3 normal, float w, float h, float open, float alpha, float time, int seed) {
        if (alpha <= .003F || h <= .01F) return;
        Vec3 side = normal.cross(UP).normalize();
        Vec3 toCamera = c.camera().subtract(at);
        Vec3 recede = normal.scale(toCamera.dot(normal) >= 0 ? -1 : 1);
        float width = w * Math.max(.02F, open);
        if (open < .05F) {
            // A hairline seam of light, crackling at its edges.
            if (c.glow()) {
                Vec3 top = at.add(0, h, 0), bottom = at.add(0, -h, 0);
                c.bolt(bottom, top, seed * 31 + (int) (time / 2), .14F, .05F, WHITE, alpha);
                c.bolt(bottom, top, seed * 37 + (int) (time / 3), .25F, .12F, MINT, alpha * .6F);
                for (int i = 0; i < 6; i++) {
                    float k = hash(seed, i) * 2 - 1;
                    Vec3 from = at.add(0, k * h * .85, 0);
                    Vec3 to = from.add(side.scale((hash(seed + 1, i) - .5) * 1.6)).add(0, (hash(seed + 2, i) - .5) * .8, 0);
                    c.bolt(from, to, seed * 13 + i + (int) (time / 2), .12F, .02F, MINT, alpha * .7F * (.5F + .5F * sin(time * .7F + i)));
                }
                c.glow(at, h * .9F, MINT, alpha * .12F);
            }
            return;
        }
        int n = 56;
        Vec3[] rim = new Vec3[n + 1];
        for (int i = 0; i <= n; i++) {
            float angle = i * TWO_PI / n;
            float jag = 1 + .07F * sin(i * 2.7F + time * .11F) + .045F * cos(i * 4.1F - time * .08F) + .06F * (hash(seed, i % n) - .5F);
            rim[i] = at.add(side.scale(cos(angle) * width * jag)).add(0, sin(angle) * h * jag, 0);
        }
        if (c.soft()) {
            // The body of the tear: a hole, black through three quarters of it, deep violet only at the torn lip.
            for (int i = 0; i < n; i++) {
                Vec3 i0 = at.add(rim[i].subtract(at).scale(.74)), i1 = at.add(rim[i + 1].subtract(at).scale(.74));
                c.v(at.x, at.y, at.z, DARK, alpha * .98F);
                c.v(i0.x, i0.y, i0.z, DARK, alpha * .97F);
                c.v(i1.x, i1.y, i1.z, DARK, alpha * .97F);
                c.tri(i0, rim[i], rim[i + 1], DEEP, alpha * .97F, alpha * .9F, alpha * .9F);
                c.tri(i0, rim[i + 1], i1, DARK, alpha * .97F, alpha * .9F, alpha * .97F);
            }
        }
        if (c.glow()) {
            // The tunnel: rims receding behind the tear, each smaller, turning, fading toward violet.
            for (int k = 1; k <= 5; k++) {
                float s = 1 - k * .15F, fade = alpha * .5F * (1 - k / 6F);
                Vec3[] ring = new Vec3[n + 1];
                for (int i = 0; i <= n; i++) {
                    Vec3 off = rim[i].subtract(at);
                    double turn = k * .25 + time * .01 * k;
                    Vec3 r = side.scale(off.dot(side) * Math.cos(turn) * s).add(0, off.y * s, 0).add(side.scale(-off.y * Math.sin(turn) * s * .15));
                    ring[i] = at.add(r).add(recede.scale(k * .65 * Math.max(1, h * .5)));
                }
                c.ribbon(ring, .05F + .02F * k, mix(MINT, VIOLET, k / 5F), fade, fade);
            }
            // Star specks falling away down the tunnel.
            for (int i = 0; i < 14; i++) {
                float u = (time * .012F + hash(seed + 5, i)) % 1;
                double angle = hash(seed + 6, i) * TWO_PI;
                Vec3 off = side.scale(Math.cos(angle) * width * .75 * (1 - u)).add(0, Math.sin(angle) * h * .75 * (1 - u), 0);
                c.glow(at.add(off).add(recede.scale(u * 4 * Math.max(1, h * .5))), .07F + .05F * (1 - u), i % 3 == 0 ? WHITE : MINT, alpha * (1 - u) * .9F);
            }
            // The rim, three layers.
            c.ribbon(rim, .2F, VIOLET, alpha * .4F, alpha * .4F);
            c.ribbon(rim, .065F, MINT, alpha * .9F, alpha * .9F);
            c.ribbon(rim, .018F, WHITE, alpha, alpha);
            // Cracks leaking out into the air from the rim, flickering.
            for (int i = 0; i < 9; i++) {
                int at0 = (int) (hash(seed + 9, i) * n);
                float flicker = .5F + .5F * sin(time * (.3F + hash(seed, i) * .4F) + i * 2);
                Vec3 from = rim[at0], out = from.subtract(at).normalize();
                Vec3 to = from.add(out.scale(.5 + hash(seed + 3, i) * (.6 + .3 * h))).add(normal.scale((hash(seed + 4, i) - .5) * .5));
                c.bolt(from, to, seed * 7 + i + (int) (time / 3), .1F, .025F, MINT, alpha * flicker * .75F);
            }
            c.glow(at, Math.max(width, h) * 1.6F, VIOLET, alpha * .07F);
        }
        if (c.solid() && alpha > .3F) {
            // Shards of broken space circling the tear.
            for (int i = 0; i < 9; i++) {
                float angle = i * TWO_PI / 9 + time * (.008F + hash(seed + 8, i) * .006F);
                float r = 1.25F + hash(seed + 10, i) * .5F;
                Vec3 p = at.add(side.scale(cos(angle) * (width + .5) * r)).add(0, sin(angle) * (h + .4) * r * .9, 0).add(normal.scale((hash(seed + 11, i) - .5) * .8));
                Vec3 tip = p.add(side.scale(cos(angle + 1.2F) * .35)).add(0, .45 + hash(seed, i) * .2, 0).add(normal.scale(.15));
                c.shard(p, tip, .09F + .06F * hash(seed + 12, i), 4, time * .05F + i, rgb(0x241B3D), 1);
            }
        }
    }
    /** A breach snapping shut: a flash across its plane and a ring thrown out. */
    private static void slamFlash(Canvas c, Vec3 at, Vec3 normal, float t, float size) {
        if (!c.glow() || t < 0) return;
        float fade = 1 - smooth(0, 14, t);
        c.glow(at, size * (1.2F + t * .1F), WHITE, fade * .5F);
        c.ring(at, normal, size * .5F + t * .45F, .14F, 0, TWO_PI, MINT, fade);
        c.ring(at, normal, size * .3F + t * .3F, .05F, 0, TWO_PI, WHITE, fade);
    }

    /** The summoning site: the body knits, a seam opens behind it and tears wide, it is dragged through, the tear seals. */
    private static void form(Canvas c, Active a, float t) {
        Vec3 body = a.origin(), gate = a.target();
        Vec3 flat = body.subtract(gate).multiply(1, 0, 1);
        Vec3 normal = flat.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : flat.normalize();
        float scale = WitherGeometry.INTRO_SCALE / WitherGeometry.ARENA_SCALE;
        Vec3 core = WitherGeometry.core(body).subtract(body).scale(scale).add(body);
        if (t < WitherGeometry.FORM_END + 6) {
            // Soul smoke spirals in and knits the body; threads of light run to the star.
            float in = 1 - smooth(WitherGeometry.FORM_END - 6, WitherGeometry.FORM_END + 6, t);
            if (c.soft()) for (int i = 0; i < 10; i++) {
                float k = i / 10F, angle = k * TWO_PI * 2 + t * .2F, r = 3.4F * (1 - smooth(0, WitherGeometry.FORM_END, t) * .7F) * (1 - k * .3F);
                Vec3 at = body.add(cos(angle) * r, k * 5.5, sin(angle) * r);
                c.cloud(at, .7F, .55F, .7F, i, t * .05F, in * .55F, Shades.smoke(c, CHAR, 0));
            }
            if (c.glow()) {
                for (int i = 0; i < 8; i++) {
                    float angle = i * TWO_PI / 8 + t * .05F;
                    Vec3 from = body.add(cos(angle) * 3.2, .3 + hash(4, i) * 4, sin(angle) * 3.2);
                    c.line(from.lerp(core, smooth(0, WitherGeometry.FORM_END, t) * .6), core, .04F, MINT, 0, in * .7F);
                }
                glowStar(c, core, .3F + .5F * smooth(10, WitherGeometry.FORM_END, t), MINT, in);
            }
        }
        float[] g = WitherGeometry.introGate(t);
        float alpha = 1 - smooth(WitherGeometry.INTRO_TICKS - 2, WitherGeometry.INTRO_TICKS + 6, t);
        if (t >= WitherGeometry.SEAM) {
            gate(c, gate, normal, g[0], g[1], g[2], alpha, c.time(), 5);
            if (t < WitherGeometry.OPENED + 4 && c.glow()) {
                // The seam splitting: cracks race out from it across the air.
                float spread = smooth(WitherGeometry.SEAM, WitherGeometry.SPLIT + 6, t);
                Vec3 side = normal.cross(UP).normalize();
                for (int i = 0; i < 12; i++) {
                    float k = hash(51, i) * 2 - 1;
                    Vec3 from = gate.add(0, k * g[1] * .9, 0);
                    Vec3 to = from.add(side.scale((hash(52, i) - .5) * 5 * spread)).add(0, (hash(53, i) - .5) * 2.5 * spread, 0);
                    c.bolt(from, to, 300 + i * 11 + (int) (t / 2), .2F, .03F, i % 3 == 0 ? WHITE : MINT, (1 - smooth(WitherGeometry.OPENED - 4, WitherGeometry.OPENED + 4, t)) * .8F);
                }
            }
            if (t >= WitherGeometry.SPLIT && t < WitherGeometry.SPLIT + 16) slamFlash(c, gate, normal, t - WitherGeometry.SPLIT, 2.5F);
        }
        if (t > WitherGeometry.PULL - 6 && t < WitherGeometry.THROUGH + 10) {
            // Air and dust streaming into the breach.
            float pull = smooth(WitherGeometry.PULL - 6, WitherGeometry.PULL + 8, t) * (1 - smooth(WitherGeometry.THROUGH, WitherGeometry.THROUGH + 10, t));
            Vec3 side = normal.cross(UP).normalize();
            for (int i = 0; i < 16; i++) {
                float u = (t * (.035F + hash(61, i) * .02F) + hash(62, i)) % 1;
                Vec3 start = gate.add(normal.scale(2 + hash(63, i) * 6)).add(side.scale((hash(64, i) - .5) * 10)).add(0, (hash(65, i) - .55) * 5, 0);
                Vec3 at = start.lerp(gate, u * u), next = start.lerp(gate, Math.min(1, u * u + .12));
                if (c.glow()) c.line(at, next, .03F, MINT, 0, pull * .45F * (1 - u));
                if (c.soft() && i % 2 == 0) {
                    Vec3 ground = new Vec3(at.x, body.y + .3 + u * .8, at.z);
                    c.cloud(ground, .45F * (1 - u * .5F), .25F, .45F * (1 - u * .5F), i, t * .04F, pull * .4F * (1 - u), Shades.smoke(c, ASH, 0));
                }
            }
        }
        if (t >= WitherGeometry.THROUGH - 2) slamFlash(c, gate, normal, t - (WitherGeometry.THROUGH - 2), 3);
    }

    /** The way out tearing open where the Wither died (seed 0), or the summoning site's breach slamming shut (seed 1). */
    private static void open(Canvas c, Active a, float t) {
        Vec3 normal = normal(a);
        if (a.seed() == 1) {
            float swell = smooth(0, 8, t), shut = smooth(8, 13, t);
            float h = WitherGeometry.GATE_H * (1 + .25F * swell) * (1 - shut * .97F), w = WitherGeometry.GATE_W * (1 + .3F * swell);
            gate(c, a.origin(), normal, w, h, 1 - shut, 1 - smooth(13, 16, t), c.time(), 7);
            slamFlash(c, a.origin(), normal, t - 12, 2.5F);
            return;
        }
        float seam = smooth(0, 10, t), open = smooth(10, 24, t);
        float alpha = 1 - smooth(26, 34, t);
        gate(c, a.origin(), normal, WitherGeometry.GATE_W, WitherGeometry.GATE_H * (.4F + .6F * seam), Math.max(.02F * seam, open), alpha, c.time(), 9);
        slamFlash(c, a.origin(), normal, t - 10, 2.2F);
        if (c.glow()) c.glow(a.origin().add(0, -WitherGeometry.GATE_LIFT + .1, 0), 2.5F, MINT, (1 - smooth(10, 40, t)) * .4F);
    }

    // ================================================================ the star and its room

    private static void aura(Canvas c, Active a, float alpha) {
        if (!(a.bound(c) instanceof RiftWitherEntity b) || b.dying()) return;
        int phase = a.seed() & 3;
        float[] tint = tint(phase);
        Vec3 core = WitherGeometry.core(b.getPosition(c.partial));
        float time = c.level.getGameTime() + c.partial;
        if (c.glow()) {
            float exposed = b.exposed() ? 1 : 0;
            glowStar(c, core, .55F + phase * .15F + exposed * .25F + .05F * sin(time * .3F), tint, alpha);
            c.glow(core, 3 + phase, tint, alpha * (.08F + exposed * .06F));
            // Stance: three arcs round the star; a cracked one burns white.
            for (int i = 0; i < 3; i++) {
                boolean cracked = i < b.stance();
                c.ring(core, WitherGeometry.forward(b.getYRot()), 1.05F + i * .16F, cracked ? .05F : .025F, -HALF_PI + i * .3F, HALF_PI + i * .3F,
                        cracked ? WHITE : tint, alpha * (cracked ? .95F : .22F));
            }
        }
        int index = ((a.seed() >>> 5) & 3) - 1;
        if (index >= 0 && index < WitherGeometry.ANCHORS.size()) {
            // A lit anchor: its filament drinks toward the star in pulses.
            Vec3 at = Vec3.atCenterOf(WitherGeometry.ANCHORS.get(index));
            if (c.glow()) {
                thread(c, at, core, .07F, tint, alpha * .85F, time);
                for (int i = 0; i < 4; i++) {
                    float u = (time * .05F + i * .25F) % 1;
                    c.glow(at.lerp(core, u), .25F, WHITE, alpha * sin(u * PI) * .8F);
                }
                c.ring(at, UP, 1.3F + .15F * sin(time * .4F), .06F, 0, TWO_PI, tint, alpha);
                c.ring(at.add(0, -2.4, 0), UP, 2.2F, .08F, 0, TWO_PI, tint, alpha * .6F);
                c.glow(at, 1.6F, tint, alpha * .35F);
            }
        }
    }

    // ================================================================ warnings

    private static void warning(Canvas c, Active a, float t) {
        if (SetPieceClient.superseded(a, CANCEL, c.level.getGameTime())) return;
        Move move = Move.values()[Math.floorMod(a.seed() & 31, Move.values().length)];
        float windup = (a.seed() >> 8) > 0 ? a.seed() >> 8 : move.windup;
        if (t > windup) return;
        float p = clamp(t / windup, 0, 1), alpha = smooth(0, 3, t), urgent = .7F + .3F * sin(t * (.4F + p * 1.2F));
        Vec3 from = a.origin(), to = a.target();
        int phase = phaseOf(a, c);
        float[] tint = tint(phase);
        double floorY = WitherGeometry.floor().y + .06;
        switch (move) {
            case BITE -> lane(c, flat(from, floorY), flat(to, floorY), WitherGeometry.BITE_RADIUS, p, tint, alpha * urgent, true);
            case RAKE -> {
                if (!(a.bound(c) instanceof RiftWitherEntity)) return;
                Vec3 at = flat(from, floorY);
                float yaw = (float) Math.atan2(to.z - from.z, to.x - from.x), half = WitherGeometry.RAKE_HALF_ANGLE;
                boolean left = ((a.seed() >> 5) & 1) == 0;
                float start = left ? yaw + half : yaw - half, sweep = (left ? -2 : 2) * half * p;
                sector(c, at, 2.5F, WitherGeometry.RAKE_REACH, yaw - half, yaw + half, tint, alpha * .5F);
                if (c.glow()) {
                    c.ring(at, UP, WitherGeometry.RAKE_REACH, .05F, Math.min(start, start + sweep), Math.max(start, start + sweep), WHITE, alpha);
                    Vec3 edge = at.add(cos(start + sweep) * WitherGeometry.RAKE_REACH, 0, sin(start + sweep) * WitherGeometry.RAKE_REACH);
                    c.line(at.add(cos(start + sweep) * 2.5, 0, sin(start + sweep) * 2.5), edge, .07F, WHITE, alpha * .4F, alpha);
                }
            }
            case VERDICT, TETHER -> {
                float radius = move == Move.TETHER ? WitherGeometry.TETHER_RADIUS : WitherGeometry.VERDICT_RADIUS;
                tubeLane(c, from, to, radius, p, tint, alpha * urgent);
                if (c.glow()) {
                    glowStar(c, from, .2F + .55F * p, move == Move.TETHER ? WHITE : tint, alpha);
                    if (move == Move.TETHER) hook(c, to, to.subtract(from).normalize(), .6F + .3F * p, WHITE, alpha);
                }
            }
            case LUNGE -> {
                boolean crash = ((a.seed() >> 6) & 1) == 1;
                boolean grounded = Math.abs(to.y - from.y) < .5 && from.y - WitherGeometry.LUNGE_LIFT < floorY + 1.5;
                if (grounded) {
                    lane(c, flat(from, floorY), flat(to, floorY), WitherGeometry.LUNGE_RADIUS, p, tint, alpha * urgent, false);
                    chevrons(c, flat(from, floorY), flat(to, floorY), WitherGeometry.LUNGE_RADIUS * .7F, t, tint, alpha);
                } else tubeLane(c, from, to, WitherGeometry.LUNGE_RADIUS, p, tint, alpha * urgent);
                if (crash && c.glow()) {
                    // Where the charge will meet stone: a burst glyph, so baiting it into a pillar is readable.
                    Vec3 dir = to.subtract(from).normalize(), at = to.add(dir.scale(WitherGeometry.WIDTH / 2));
                    for (int i = 0; i < 8; i++) {
                        float angle = i * TWO_PI / 8 + .2F;
                        Vec3 side = dir.cross(UP).normalize();
                        Vec3 tip = at.add(side.scale(cos(angle) * (1 + p))).add(0, sin(angle) * (1 + p), 0);
                        c.line(at, tip, .06F, EMBER, alpha, 0);
                    }
                    c.glow(at, 1.2F, EMBER, alpha * .4F * urgent);
                }
            }
            case RAIN -> {
                boolean grounded = to.y - .8 < floorY + .4;
                Vec3 at = grounded ? flat(to, floorY) : to.add(0, -.75, 0);
                target(c, at, WitherGeometry.SKULL_RADIUS, p, tint, alpha * urgent);
                if (c.glow()) {
                    Vec3 top = to.add(0, WitherGeometry.RAIN_HEIGHT, 0);
                    c.line(top, at, .025F, tint, alpha * .1F, alpha * .5F);
                    c.glow(top.lerp(at, p * .85), .3F, WHITE, alpha * .8F);
                }
                if (c.soft()) c.disc(at.add(0, .01, 0), UP, WitherGeometry.SKULL_RADIUS, CHAR, alpha * .28F * p, alpha * .12F * p);
            }
            case SLAM -> {
                Vec3 at = flat(to, floorY);
                target(c, at, WitherGeometry.SLAM_RADIUS, p, tint, alpha * urgent);
                if (c.glow()) for (int i = 0; i < 8; i++) c.spoke(at, UP, i * TWO_PI / 8 + p * .6F, WitherGeometry.SLAM_RADIUS * .35F, WitherGeometry.SLAM_RADIUS * (.6F + .4F * p), .05F, tint, alpha * .6F);
                if (c.soft()) c.disc(at.add(0, .01, 0), UP, WitherGeometry.SLAM_RADIUS, CHAR, alpha * .35F * p, alpha * .1F);
            }
            case PULSE -> {
                Vec3 at = flat(from, floorY);
                if (c.glow()) {
                    // The hunger draws in before it rolls out: rings converge on its feet, then the charge ring closes.
                    for (int i = 0; i < 3; i++) {
                        float k = ((p * 2 + i / 3F) % 1), r = 9 - k * 6;
                        c.ring(at, UP, r, .07F, 0, TWO_PI, tint, alpha * k * .7F);
                    }
                    c.ring(at, UP, 3, .14F, -HALF_PI, -HALF_PI + TWO_PI * p, WHITE, alpha);
                    c.ring(at, UP, 3, .5F, 0, TWO_PI, tint, alpha * .25F);
                }
            }
            case HORIZON -> {
                if (!c.glow()) return;
                glowStar(c, from, .5F + p * 1.4F, WHITE, alpha);
                c.glow(from, 3 + p * 5, tint, alpha * .2F * p);
                horizonRays(c, from, tint, alpha * (.25F + .5F * p), .02F + .02F * p, false);
                for (int i = 0; i < 3; i++) {
                    float k = ((t * .03F + i / 3F) % 1);
                    c.ring(from, UP, 14 * (1 - k), .08F, 0, TWO_PI, tint, alpha * k * .6F);
                }
            }
            case SPIRES -> {}
            case BROOD -> {
                // Where each one will land: a bone-white circle with a skull's two eyes, and the arc it will fly.
                Vec3 at = flat(to, floorY);
                target(c, at, WitherGeometry.BROOD_LAND, p, BONE, alpha * urgent);
                if (c.glow()) {
                    Vec3[] arc = new Vec3[17];
                    for (int i = 0; i <= 16; i++) arc[i] = WitherGeometry.spewArc(from, at, i / 16F);
                    c.ribbon(arc, .04F, BONE, alpha * .05F, alpha * .5F * p);
                    Vec3 side = at.subtract(from).multiply(1, 0, 1).normalize().cross(UP);
                    for (int s : new int[]{-1, 1}) c.glow(at.add(side.scale(s * .45)).add(0, .1, 0), .3F, tint, alpha * p);
                    glowStar(c, from, .2F + .5F * p, tint, alpha);
                }
                if (c.soft()) c.disc(at.add(0, .01, 0), UP, WitherGeometry.BROOD_LAND, CHAR, alpha * .3F * p, alpha * .1F);
            }
        }
    }
    /** A skeleton of its brood in flight: a smoking trail along the same arc the server flies it on. */
    private static void spew(Canvas c, Active a, float t) {
        if (t > WitherGeometry.BROOD_FLIGHT + 10) return;
        Vec3 from = a.origin(), to = a.target();
        float k = Math.min(1, t / WitherGeometry.BROOD_FLIGHT), fade = 1 - smooth(WitherGeometry.BROOD_FLIGHT, WitherGeometry.BROOD_FLIGHT + 10, t);
        float tail = Math.max(0, k - .4F);
        float[] tint = tint(a.seed());
        Vec3[] trail = new Vec3[13];
        for (int i = 0; i <= 12; i++) trail[i] = WitherGeometry.spewArc(from, to, tail + (k - tail) * i / 12F).add(0, .8, 0);
        if (c.glow()) {
            c.ribbon(trail, .35F, tint, 0, fade * .35F);
            c.ribbon(trail, .08F, WHITE, 0, fade * .7F);
            if (k < 1) c.glow(trail[12], .9F, tint, fade * .5F);
        }
        if (c.soft()) for (int i = 0; i < 6; i++) {
            Vec3 at = WitherGeometry.spewArc(from, to, Math.max(0, k - i * .07F)).add(0, .8, 0);
            c.cloud(at, .35F + i * .06F, .3F, .35F + i * .06F, i + a.entity(), t * .05F, fade * .4F * (1 - i / 6F), Shades.smoke(c, CHAR, 0));
        }
        if (t < 6 && c.glow()) glowStar(c, from, .8F * (1 - t / 6), WHITE, 1 - t / 6);
    }
    private static Vec3 flat(Vec3 at, double y) { return new Vec3(at.x, y, at.z); }

    /** A band on the floor from {@code a} to {@code b}: a dark scorch, mint edges, and a white center filling as it nears. */
    private static void lane(Canvas c, Vec3 a, Vec3 b, float radius, float p, float[] tint, float alpha, boolean jaws) {
        Vec3 dir = b.subtract(a);
        if (dir.lengthSqr() < 1e-4) return;
        Vec3 side = dir.normalize().cross(UP).scale(radius);
        if (c.soft()) {
            Vec3 a0 = a.add(side), a1 = a.subtract(side), b0 = b.add(side), b1 = b.subtract(side);
            c.tri(a0, a1, b1, CHAR, alpha * .3F, alpha * .3F, alpha * .3F);
            c.tri(a0, b1, b0, CHAR, alpha * .3F, alpha * .3F, alpha * .3F);
        }
        if (c.glow()) {
            c.line(a.add(side), b.add(side), .06F, tint, alpha * .5F, alpha);
            c.line(a.subtract(side), b.subtract(side), .06F, tint, alpha * .5F, alpha);
            c.line(a.add(side), a.subtract(side), .05F, tint, alpha * .6F, alpha * .6F);
            c.line(b.add(side), b.subtract(side), .05F, tint, alpha, alpha);
            c.line(a, a.lerp(b, p), .09F, WHITE, alpha * .5F, alpha);
            c.line(a.add(side.scale(.5)), a.add(side.scale(.5)).lerp(b.add(side.scale(.5)), p), .03F, WHITE, 0, alpha * .6F);
            c.line(a.subtract(side.scale(.5)), a.subtract(side.scale(.5)).lerp(b.subtract(side.scale(.5)), p), .03F, WHITE, 0, alpha * .6F);
            if (jaws) {
                // Two fangs at the end of the lane, closing as the snap nears.
                Vec3 f = dir.normalize();
                for (int s : new int[]{-1, 1}) {
                    Vec3 root = b.add(side.scale(s * (1.2 - .9 * p)));
                    c.line(root.subtract(f.scale(1.4)), root.add(f.scale(.6)).subtract(side.scale(s * .35)), .07F, WHITE, alpha * .3F, alpha);
                }
            }
        }
    }
    /** Chevrons along a lane, running toward its end. */
    private static void chevrons(Canvas c, Vec3 a, Vec3 b, float size, float t, float[] tint, float alpha) {
        if (!c.glow()) return;
        Vec3 dir = b.subtract(a);
        double length = dir.length();
        if (length < 1) return;
        dir = dir.scale(1 / length);
        Vec3 side = dir.cross(UP);
        for (int i = 0; i < 6; i++) {
            double u = ((i / 6D + t * .03) % 1) * length;
            Vec3 tip = a.add(dir.scale(u + size * .6));
            for (int s : new int[]{-1, 1}) c.line(tip.subtract(dir.scale(size * .7)).add(side.scale(s * size)), tip, .07F, tint, 0, alpha * .8F);
        }
    }
    /** A lane through the air: rings along it, edges, and a white thread filling it. */
    private static void tubeLane(Canvas c, Vec3 a, Vec3 b, float radius, float p, float[] tint, float alpha) {
        if (!c.glow()) return;
        Vec3 dir = b.subtract(a);
        double length = dir.length();
        if (length < .1) return;
        Vec3 n = dir.scale(1 / length);
        Vec3 side = n.cross(UP);
        side = side.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 up = side.cross(n).normalize();
        for (Vec3 o : new Vec3[]{side, side.scale(-1), up, up.scale(-1)}) c.line(a.add(o.scale(radius)), b.add(o.scale(radius)), .025F, tint, alpha * .2F, alpha * .55F);
        int rings = Math.max(3, (int) (length / 2.5));
        for (int i = 0; i <= rings; i++) {
            float k = i / (float) rings, lit = k <= p ? 1 : .3F;
            c.ring(a.add(dir.scale(k)), n, radius, .035F, 0, TWO_PI, k <= p ? WHITE : tint, alpha * lit * (.4F + .6F * k));
        }
        c.line(a, a.add(dir.scale(p)), .06F, WHITE, alpha * .4F, alpha);
        c.ring(b, n, radius * (1.3F - .3F * p), .07F, -HALF_PI, -HALF_PI + TWO_PI * p, WHITE, alpha);
    }
    /** A circle the hit will fill: a faint rim, a white arc closing round it, and an inner ring tightening to the center. */
    private static void target(Canvas c, Vec3 at, float radius, float p, float[] tint, float alpha) {
        if (!c.glow()) return;
        c.ring(at, UP, radius, .04F, 0, TWO_PI, tint, alpha * .6F);
        c.ring(at, UP, radius, .1F, -HALF_PI, -HALF_PI + TWO_PI * p, WHITE, alpha * .9F);
        c.ring(at, UP, radius * (1 - p) + .1F, .05F, 0, TWO_PI, VIOLET, alpha * .7F);
        c.ring(at, UP, radius, .5F, 0, TWO_PI, tint, alpha * .12F);
    }
    /** A flat sector of an annulus on the floor, dark beneath and outlined in light. */
    private static void sector(Canvas c, Vec3 at, float r0, float r1, float from, float to, float[] tint, float alpha) {
        int n = 24;
        if (c.soft()) for (int i = 0; i < n; i++) {
            float a0 = from + (to - from) * i / n, a1 = from + (to - from) * (i + 1) / n;
            Vec3 i0 = at.add(cos(a0) * r0, 0, sin(a0) * r0), o0 = at.add(cos(a0) * r1, 0, sin(a0) * r1);
            Vec3 i1 = at.add(cos(a1) * r0, 0, sin(a1) * r0), o1 = at.add(cos(a1) * r1, 0, sin(a1) * r1);
            c.tri(i0, o0, o1, CHAR, alpha * .5F, alpha * .5F, alpha * .5F);
            c.tri(i0, o1, i1, CHAR, alpha * .5F, alpha * .5F, alpha * .5F);
        }
        if (c.glow()) {
            c.ring(at, UP, r1, .05F, from, to, tint, alpha * 1.6F);
            c.ring(at, UP, r0, .04F, from, to, tint, alpha * 1.2F);
            for (float angle : new float[]{from, to}) c.line(at.add(cos(angle) * r0, 0, sin(angle) * r0), at.add(cos(angle) * r1, 0, sin(angle) * r1), .05F, tint, alpha * 1.2F, alpha * 1.6F);
        }
    }
    private static void hook(Canvas c, Vec3 at, Vec3 dir, float size, float[] col, float alpha) {
        Vec3 side = dir.cross(UP);
        side = side.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3[] barb = new Vec3[7];
        for (int i = 0; i < 7; i++) {
            float k = i / 6F, angle = k * PI * 1.2F;
            barb[i] = at.add(dir.scale(-size * sin(angle))).add(side.scale(size * (1 - cos(angle)) * .6F)).add(0, -size * .2F * k, 0);
        }
        c.ribbon(barb, .06F, col, alpha, alpha * .4F);
    }

    // ================================================================ landings

    private static void fire(Canvas c, Active a, float t) {
        if (!c.glow()) return;
        Move move = Move.values()[Math.floorMod(a.seed(), Move.values().length)];
        float[] tint = tint(phaseOf(a, c));
        Vec3 from = a.origin(), to = a.target(), dir = to.subtract(from);
        if (dir.lengthSqr() < 1e-4) return;
        Vec3 n = dir.normalize();
        if (move == Move.TETHER) {
            // The hook snaps out, bites, and reels back.
            float out = smooth(0, 3, t), back = smooth(4, 12, t), fade = 1 - smooth(10, 16, t);
            Vec3 tip = from.lerp(to, out * (1 - back));
            c.bolt(from, tip, a.seed() * 7 + (int) t, .25F, .06F, WHITE, fade);
            c.line(from, tip, .2F, tint, fade * .3F, fade * .3F);
            hook(c, tip, n, .7F, WHITE, fade);
            if (t < 5) c.glow(to, 1, WHITE, (1 - t / 5) * .6F);
            return;
        }
        float hold = WitherGeometry.VERDICT_FIRE;
        float on = smooth(0, 1.5F, t) * (1 - smooth(hold, hold + 5, t));
        float r = a.scale();
        float wob = 1 + .08F * sin(t * 2.3F);
        c.line(from, to, r * 1.5F * wob, tint, on * .28F, on * .2F);
        c.line(from, to, r * .55F * wob, tint, on * .85F, on * .7F);
        c.line(from, to, r * .18F, WHITE, on, on);
        double length = dir.length();
        for (int i = 0; i < 7; i++) {
            float u = (i / 7F + t * .12F) % 1;
            c.ring(from.add(dir.scale(u)), n, r * (.8F + .5F * u), .05F, 0, TWO_PI, WHITE, on * (1 - u) * .8F);
        }
        glowStar(c, from, r * 1.1F * on, WHITE, on);
        c.glow(to, r * 2.2F, tint, on * .6F);
        c.glow(to, r * .9F, WHITE, on * .8F);
        if (length > 2) c.ring(to.subtract(n.scale(.05)), n, r * 1.6F * (1 + t * .05F), .08F, 0, TWO_PI, tint, on * .7F);
    }
    private static void arc(Canvas c, Active a, float t) {
        Move move = Move.values()[Math.floorMod(a.seed() & 31, Move.values().length)];
        float[] tint = tint(phaseOf(a, c));
        float fade = 1 - smooth(6, 20, t);
        if (move == Move.BITE) {
            Vec3 from = a.origin(), to = a.target(), dir = to.subtract(from).normalize(), side = dir.cross(UP);
            float snap = smooth(0, 3, t);
            if (c.glow()) {
                c.line(from, to, .3F, tint, fade * .3F, fade * .5F);
                c.line(from, to, .07F, WHITE, fade * .5F, fade);
                for (int s : new int[]{-1, 1}) {
                    Vec3[] jaw = new Vec3[9];
                    for (int i = 0; i < 9; i++) {
                        float k = i / 8F, angle = (k - .5F) * PI * .9F;
                        jaw[i] = to.add(dir.scale(cos(angle) * 1.6 - .6)).add(side.scale(s * (sin(abs(angle)) * .2 + (1.4 - snap * 1.1)))).add(0, .3, 0);
                    }
                    c.ribbon(jaw, .12F, WHITE, fade, fade);
                }
                c.glow(to.add(0, .4, 0), 1.6F, WHITE, fade * .5F);
            }
            if (c.soft()) for (int i = 0; i < 4; i++) {
                Vec3 at = to.add(side.scale((i - 1.5) * .9)).add(0, .3 + t * .03, 0);
                c.cloud(at, .5F + t * .05F, .3F + t * .02F, .5F + t * .05F, i, t * .03F, fade * .45F, Shades.smoke(c, ASH, 0));
            }
            return;
        }
        // Rending Claw: three claw arcs sweep across the sector the warning showed, with trails.
        Vec3 at = a.origin();
        float yaw = (float) Math.atan2(a.target().z - at.z, a.target().x - at.x), half = WitherGeometry.RAKE_HALF_ANGLE;
        boolean left = ((a.seed() >> 5) & 1) == 0;
        float sweep = smooth(0, 5, t), start = left ? yaw + half : yaw - half, end = start + (left ? -2 : 2) * half * sweep;
        if (c.glow()) for (int k = 0; k < 3; k++) {
            float r = a.scale() * (.72F + k * .14F), y = .5F + k * .45F;
            Vec3 ring = at.add(0, y, 0);
            float trail = (left ? 1 : -1) * 1.1F;
            c.ring(ring, UP, r, .28F, Math.min(end, end + trail), Math.max(end, end + trail), tint, fade * .35F);
            c.ring(ring, UP, r, .06F, Math.min(start, end), Math.max(start, end), WHITE, fade * (sweep < 1 ? 1 : .7F));
        }
        if (c.soft()) for (int i = 0; i < 5; i++) {
            float angle = start + (end - start) * i / 4F, r = a.scale() * .85F;
            c.cloud(at.add(cos(angle) * r, .4 + t * .02, sin(angle) * r), .6F, .3F, .6F, i, t * .02F, fade * .35F * sweep, Shades.smoke(c, ASH, 0));
        }
    }
    private static void pulse(Canvas c, Active a, float t) {
        if (SetPieceClient.superseded(a, Set.of(Piece.WITHER_CANCEL, Piece.WITHER_DEATH), c.level.getGameTime())) return;
        float speed = (a.seed() & 1023) / 100F;
        if (speed <= 0) speed = WitherGeometry.PULSE_SPEED;
        float[] tint = tint(a.seed() >> 10);
        float r = (float) WitherGeometry.ripple(t, speed), reach = a.scale();
        if (r > reach + 1) return;
        float fade = 1 - smooth(reach * .8F, reach + 1, r);
        Vec3 at = a.origin().add(0, .1, 0);
        if (c.glow()) {
            c.ring(at, UP, r, .45F, 0, TWO_PI, tint, fade * .35F);
            c.ring(at.add(0, WitherGeometry.WAVE_HEIGHT * .6, 0), UP, r, .06F, 0, TWO_PI, WHITE, fade * .8F);
            c.ring(at, UP, Math.max(.1F, r - 1.4F), .12F, 0, TWO_PI, tint, fade * .2F);
        }
        if (c.soft()) {
            // Dust kicked up just behind the rolling front.
            int n = Math.min(28, 8 + (int) (r * 1.1F));
            for (int i = 0; i < n; i++) {
                float angle = i * TWO_PI / n + hash(a.seed(), i) * .3F;
                Vec3 puff = at.add(cos(angle) * (r - .8), .35, sin(angle) * (r - .8));
                c.cloud(puff, .55F, .35F, .55F, i, t * .02F, fade * .32F, Shades.smoke(c, ASH, 0));
            }
        }
    }
    private static void impact(Canvas c, Active a, float t) {
        float r = a.scale(), fade = 1 - smooth(r > 3 ? 8 : 4, a.piece().ticks, t);
        Vec3 at = a.origin();
        float[] tint = tint(phaseOf(a, c));
        if (c.glow()) {
            c.glow(at.add(0, .5, 0), r * (1.2F - .5F * smooth(0, 6, t)), WHITE, (1 - smooth(0, 8, t)) * .6F);
            c.ring(at, UP, r * (.4F + 1.4F * smooth(0, 14, t)), .3F, 0, TWO_PI, tint, fade * .6F);
            c.ring(at, UP, r * (.3F + 1.1F * smooth(0, 10, t)), .06F, 0, TWO_PI, WHITE, fade);
        }
        if (c.soft()) {
            // Scorched cracks radiating from the point of impact, and dust thrown out low.
            if (r > 2) for (int i = 0; i < 9; i++) {
                float angle = i * TWO_PI / 9 + hash(7, i);
                Vec3 end = at.add(cos(angle) * r * (.8 + hash(8, i) * .5), .02, sin(angle) * r * (.8 + hash(8, i) * .5));
                c.line(at.add(0, .02, 0), end, .12F, CHAR, fade * .8F, 0);
            }
            int n = r > 3 ? 12 : 6;
            for (int i = 0; i < n; i++) {
                float angle = i * TWO_PI / n + hash(9, i) * .4F, d = r * (.4F + .8F * smooth(0, 16, t));
                c.cloud(at.add(cos(angle) * d, .5 + t * .03, sin(angle) * d), .8F + t * .05F, .5F + t * .03F, .8F + t * .05F, i, t * .02F,
                        fade * .55F, Shades.smoke(c, ASH, 0));
            }
        }
    }
    private static void nova(Canvas c, Active a, float t) {
        if (!c.glow()) return;
        float[] tint = tint(phaseOf(a, c));
        float flash = 1 - smooth(0, 10, t), fade = 1 - smooth(4, 24, t);
        glowStar(c, a.origin(), 2.5F * fade + .3F, WHITE, fade);
        c.glow(a.origin(), 12 * flash + 2, WHITE, flash * .45F);
        horizonRays(c, a.origin(), WHITE, fade, .1F * fade + .02F, true);
        horizonRays(c, a.origin(), tint, fade * .4F, .35F * fade, true);
        c.ring(new Vec3(a.origin().x, WitherGeometry.floor().y + .1, a.origin().z), UP, t * 2.4F, .4F, 0, TWO_PI, tint, fade * .4F);
    }
    /** Rays from the star across the floor, each stopping at the first stone: the shadows behind cover are the safe places. */
    private static void horizonRays(Canvas c, Vec3 from, float[] col, float alpha, float width, boolean blaze) {
        int n = 72;
        Vec3[] edge = new Vec3[n + 1];
        double floorY = WitherGeometry.floor().y + .08;
        for (int i = 0; i <= n; i++) {
            double angle = i * Math.PI * 2 / n;
            Vec3 end = new Vec3(from.x + Math.cos(angle) * WitherGeometry.HORIZON_REACH, floorY, from.z + Math.sin(angle) * WitherGeometry.HORIZON_REACH);
            var hit = c.level.clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
            Vec3 stop = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
            edge[i] = new Vec3(stop.x, Math.max(floorY, stop.y), stop.z);
            if (i < n) {
                c.line(from, stop, width, col, alpha * (blaze ? 1 : .2F), alpha * (blaze ? .5F : .6F));
                c.glow(stop, blaze ? .5F : .18F, col, alpha * .6F);
            }
        }
        c.ribbon(edge, .08F, col, alpha * .7F, alpha * .7F);
    }
    private static void phase(Canvas c, Active a, float t) {
        int phase = a.seed();
        float[] tint = tint(phase);
        Vec3 ground = a.origin();
        float fade = 1 - smooth(10, 50, t);
        if (c.glow()) {
            for (int i = 0; i < 3; i++) {
                float k = Math.max(0, t - i * 4);
                c.ring(ground, UP, 2 + k * .9F, .35F, 0, TWO_PI, tint, fade * (1 - i * .25F) * .6F);
                c.ring(ground, UP, 2 + k * .9F, .07F, 0, TWO_PI, WHITE, fade * (1 - i * .25F));
            }
            Vec3 core = a.bound(c) instanceof RiftWitherEntity b ? b.core() : a.target();
            c.line(ground, core.add(0, 30 * smooth(0, 8, t), 0), 1.2F * fade, tint, fade * .25F, 0);
            glowStar(c, core, 2 * fade + .5F, WHITE, fade);
        }
        // The second phase: its armour plates are thrown off and crash to the floor.
        if (phase == 2 && c.solid() && a.bound(c) instanceof RiftWitherEntity b && t > 8) {
            float yaw = b.getYRot();
            for (int s = 0; s < 2; s++) {
                Vec3 shoulder = WitherGeometry.point(ground, yaw, WitherGeometry.ARENA_SCALE, (s == 0 ? -1 : 1) * 21, 52, -2);
                Vec3 out = shoulder.subtract(ground).multiply(1, 0, 1).normalize();
                float k = Math.min(t - 8, 22);
                double y = Math.max(WitherGeometry.floor().y + .3, shoulder.y + k * .35 - k * k * .045);
                Vec3 at = new Vec3(shoulder.x, y, shoulder.z).add(out.scale(k * .22));
                float spin = Math.min(t - 8, 20) * .18F * (s == 0 ? 1 : -1);
                float sink = smooth(42, 50, t);
                c.box(at.add(0, -sink * .5, 0), 1.1F, .3F, .9F, yaw * DEG_TO_RAD + s, spin, spin * .6F, PLATE, 1);
                c.box(at.add(0, .35 - sink * .5, 0), .8F, .25F, .7F, yaw * DEG_TO_RAD + s, spin, spin * .6F, rgb(0x6E6A62), 1);
            }
        }
    }
    private static void spires(Canvas c, Active a, float t) {
        if (SetPieceClient.superseded(a, CANCEL, c.level.getGameTime())) return;
        Vec3 from = a.origin(), mark = a.target();
        List<Vec3> line = WitherGeometry.spires(from, mark);
        if (line.isEmpty()) return;
        float windup = (a.seed() >> 4) > 0 ? a.seed() >> 4 : Move.SPIRES.windup, p = clamp(t / windup, 0, 1);
        float[] tint = tint(a.seed() & 15);
        Vec3 end = line.getLast();
        double floorY = WitherGeometry.floor().y + .05;
        // The crack runs out ahead of the spires and glows hotter as they come.
        Vec3 start = new Vec3(from.x, floorY, from.z).add(line.getFirst().subtract(from).multiply(1, 0, 1).normalize().scale(2));
        Vec3 tip = start.lerp(new Vec3(end.x, floorY, end.z), Math.min(1, p * 1.2));
        Vec3[] crack = new Vec3[16];
        Vec3 side = tip.subtract(start).normalize().cross(UP);
        for (int i = 0; i < 16; i++) crack[i] = start.lerp(tip, i / 15D).add(side.scale((hash(a.seed() + 3, i) - .5) * .5 * (i == 0 || i == 15 ? 0 : 1)));
        float crackFade = 1 - smooth(windup + line.size() * WitherGeometry.SPIRE_RATE, windup + line.size() * WitherGeometry.SPIRE_RATE + 16, t);
        if (c.soft()) c.ribbon(crack, .22F, CHAR, crackFade * .9F, crackFade * .9F);
        if (c.glow()) {
            c.ribbon(crack, .07F, tint, crackFade * (.4F + .6F * p), crackFade * (.4F + .6F * p));
            c.ribbon(crack, .02F, WHITE, crackFade * p, crackFade * p);
            if (t < windup) target(c, new Vec3(end.x, floorY, end.z), WitherGeometry.SPIRE_RADIUS + .3F, p, tint, (1 - smooth(windup - 2, windup, t)));
        }
        for (int i = 0; i < line.size(); i++) {
            float since = t - (windup + i * WitherGeometry.SPIRE_RATE);
            if (since < -3) continue;
            Vec3 base = line.get(i);
            Vec3 at = new Vec3(base.x, floorY, base.z);
            if (since < 0) {
                if (c.glow()) c.glow(at.add(0, .1, 0), .8F, tint, (since + 3) / 3 * .6F);
                continue;
            }
            float rise = smooth(0, 3, since), sink = smooth(WitherGeometry.SPIRE_LINGER - 8, WitherGeometry.SPIRE_LINGER, since);
            float h = WitherGeometry.SPIRE_HEIGHT * rise * (1 - sink) * (.85F + .3F * hash(a.seed() + 7, i));
            if (h <= .05F) continue;
            if (c.solid()) {
                float lean = (hash(a.seed() + 11, i) - .5F) * .5F;
                Vec3 tipAt = at.add(side.scale(lean)).add(0, h, 0);
                c.shard(at.add(0, -.3, 0), tipAt, WitherGeometry.SPIRE_RADIUS * .55F * (1 - sink * .5F), 5, i * 1.3F, BONE, 1);
                for (int s : new int[]{-1, 1}) {
                    Vec3 root = at.add(side.scale(s * .55)), small = root.add(side.scale(s * .5)).add(0, h * .5, 0);
                    c.shard(root.add(0, -.2, 0), small, .22F * (1 - sink), 4, i + s, mix(BONE, rgb(0x8A8270), .4F), 1);
                }
            }
            if (c.glow() && since < 8) {
                float flash = 1 - smooth(0, 8, since);
                c.ring(at.add(0, .1, 0), UP, WitherGeometry.SPIRE_RADIUS * (1 + since * .15F), .12F, 0, TWO_PI, tint, flash);
                c.glow(at.add(0, h * .4, 0), 1.2F, WHITE, flash * .35F);
            }
            if (c.soft() && since < 14) c.cloud(at.add(0, .4 + since * .03, 0), .7F + since * .05F, .4F, .7F + since * .05F, i, since * .03F,
                    (1 - smooth(0, 14, since)) * .5F, Shades.smoke(c, ASH, 0));
        }
    }

    // ================================================================ death

    private static void death(Canvas c, Active a, float t) {
        if (t > WitherGeometry.DETONATE + 2) return;
        Vec3 core = a.bound(c) instanceof RiftWitherEntity b ? WitherGeometry.core(b.getPosition(c.partial)) : a.target();
        float time = c.level.getGameTime() + c.partial;
        if (!c.glow()) {
            if (c.soft() && t > WitherGeometry.IMPLODE - 10) {
                // Everything drawn in: dark smoke spiralling into the star.
                float k = smooth(WitherGeometry.IMPLODE - 10, WitherGeometry.DETONATE, t);
                for (int i = 0; i < 12; i++) {
                    float angle = i * TWO_PI / 12 + t * .15F, r = 7 * (1 - k) + .5F;
                    c.cloud(core.add(cos(angle) * r, sin(angle * 2) * r * .3, sin(angle) * r), .9F * (1 - k * .5F), .6F, .9F * (1 - k * .5F), i, t * .05F, .5F * k, Shades.smoke(c, CHAR, 0));
                }
            }
            return;
        }
        float thrash = smooth(0, 4, t) * (1 - smooth(26, 32, t));
        // Light cracks flicker across the dying body.
        if (thrash > 0) for (int i = 0; i < 4; i++) {
            int seed = (int) (t / 2) * 5 + i;
            Vec3 end = core.add((hash(seed, 1) - .5) * 6, (hash(seed, 2) - .4) * 6, (hash(seed, 3) - .5) * 6);
            c.bolt(core, end, seed, .5F, .05F, WHITE, thrash * .8F);
        }
        if (t >= WitherGeometry.RIBS_BURST && t < WitherGeometry.RIBS_BURST + 14) slamFlash(c, core, UP, t - WitherGeometry.RIBS_BURST, 3);
        // Beams breaking out through every fracture, more and longer as the star swells, drawn back in before it goes.
        float swell = smooth(WitherGeometry.RIBS_BURST, WitherGeometry.IMPLODE, t), drink = smooth(WitherGeometry.IMPLODE, WitherGeometry.DETONATE - 2, t);
        int beams = (int) (3 + swell * 13);
        for (int i = 0; i < beams; i++) {
            double yaw = hash(41, i) * Math.PI * 2, pitch = (hash(42, i) - .35) * 1.6;
            Vec3 dir = new Vec3(Math.cos(yaw) * Math.cos(pitch), Math.sin(pitch), Math.sin(yaw) * Math.cos(pitch));
            float born = WitherGeometry.RIBS_BURST + i * 3;
            float length = (8 + hash(43, i) * 20) * smooth(born, born + 10, t) * (1 - drink);
            if (length <= .05F) continue;
            float flicker = .75F + .25F * sin(time * .9F + i);
            Vec3 end = core.add(dir.scale(length));
            c.line(core, end, .6F, MINT, flicker * .3F, 0);
            c.line(core, end, .18F, WHITE, flicker * .9F, 0);
        }
        float starSize = .6F + 2.6F * swell;
        starSize = lerp(drink, starSize, .25F);
        glowStar(c, core, starSize, mix(MINT, WHITE, swell), 1);
        c.glow(core, starSize * 4, MINT, .2F + .2F * swell);
        if (drink > 0) {
            // Rings closing on the star, faster and faster.
            for (int i = 0; i < 4; i++) {
                float k = (t * .08F + i / 4F) % 1;
                c.ring(core, UP, 16 * (1 - k), .15F, 0, TWO_PI, WHITE, drink * k);
                c.ring(core, new Vec3(1, 0, 0), 12 * (1 - k), .1F, 0, TWO_PI, MINT, drink * k * .6F);
            }
        }
    }
    private static void detonate(Canvas c, Active a, float t) {
        Vec3 core = a.origin(), ground = a.target();
        float radius = a.scale();
        float flash = 1 - smooth(0, 12, t), shock = smooth(0, 30, t), fade = 1 - smooth(20, 60, t);
        if (c.glow()) {
            c.glow(core, 40 * flash + 4, WHITE, flash);
            c.glow(core, 10 + 20 * shock, MINT, fade * .25F);
            // The shock dome, and its foot racing across the floor.
            c.shell(core, radius * shock, radius * shock * .6F, radius * shock, PI, mix(WHITE, MINT, shock), fade * .45F, .05F, PLAIN, t * .01F);
            c.ring(ground.add(0, .15, 0), UP, radius * shock, 1.4F, 0, TWO_PI, MINT, fade * .7F);
            c.ring(ground.add(0, .25, 0), UP, radius * shock, .25F, 0, TWO_PI, WHITE, fade);
            c.ring(ground.add(0, .15, 0), UP, radius * smooth(4, 50, t) * .7F, .6F, 0, TWO_PI, VIOLET, (1 - smooth(30, 70, t)) * .5F);
            // A pillar of light to the vault, then embers drifting down.
            float pillar = 1 - smooth(4, 30, t);
            c.line(ground, ground.add(0, 60, 0), 3.5F * pillar, MINT, pillar * .5F, 0);
            c.line(ground, ground.add(0, 60, 0), 1F * pillar, WHITE, pillar, 0);
            if (t > 10) for (int i = 0; i < 60; i++) {
                float h1 = hash(71, i), h2 = hash(72, i), h3 = hash(73, i);
                float life = (t - 10 - h3 * 60);
                if (life < 0 || life > 80) continue;
                Vec3 at = ground.add((h1 - .5) * 50, 26 - life * .3 + sin(life * .1F + i) * .5, (h2 - .5) * 50);
                c.glow(at, .14F, i % 3 == 0 ? WHITE : MINT, sin(life / 80 * PI) * .8F);
            }
        }
        if (c.soft()) {
            // Smoke rolling out along the floor, and the scorch left where it stood.
            for (int i = 0; i < 28; i++) {
                float angle = i * TWO_PI / 28 + hash(81, i) * .2F, d = radius * .55F * smooth(0, 40, t) * (.7F + .3F * hash(82, i));
                c.cloud(ground.add(cos(angle) * d, 1 + hash(83, i), sin(angle) * d), 2.2F + t * .03F, 1.2F, 2.2F + t * .03F, i, t * .01F,
                        (1 - smooth(30, 110, t)) * .55F * smooth(0, 4, t), Shades.smoke(c, ASH, 0));
            }
            float scorch = smooth(2, 8, t) * (1 - smooth(120, 150, t));
            c.disc(ground.add(0, .03, 0), UP, 7, CHAR, scorch * .85F, 0);
            for (int i = 0; i < 12; i++) {
                float angle = i * TWO_PI / 12 + hash(84, i) * .3F;
                c.line(ground.add(0, .04, 0), ground.add(cos(angle) * (6 + hash(85, i) * 7), .04, sin(angle) * (6 + hash(85, i) * 7)), .18F, CHAR, scorch * .9F, 0);
            }
        }
    }

    // ================================================================ primitives

    /** The star: layered light with three turning rings and spikes through it. */
    private static void glowStar(Canvas c, Vec3 at, float radius, float[] tint, float alpha) {
        if (radius <= .01F || alpha <= .003F) return;
        float time = c.level.getGameTime() + c.partial;
        c.glow(at, radius * 2.4F, tint, alpha * .22F);
        c.glow(at, radius * .75F, WHITE, alpha * .7F);
        for (int i = 0; i < 3; i++) {
            Vec3 axis = new Vec3(cos(time * .035F + i * 2), sin(time * .025F + i), sin(time * .035F + i * 2)).normalize();
            c.ring(at, axis, radius, .02F + radius * .015F, 0, TWO_PI, tint, alpha * .8F);
            c.line(at.subtract(axis.scale(radius * 1.6)), at.add(axis.scale(radius * 1.6)), .015F + radius * .01F, WHITE, 0, alpha * .8F);
        }
    }
    private static void thread(Canvas c, Vec3 from, Vec3 to, float width, float[] tint, float alpha, float time) {
        Vec3[] points = new Vec3[13];
        Vec3 side = to.subtract(from).normalize().cross(UP);
        for (int i = 0; i < 13; i++) {
            float p = i / 12F;
            points[i] = from.lerp(to, p).add(side.scale(sin(p * PI) * sin(p * PI * 3 + time * .08F) * .14));
        }
        c.ribbon(points, width * 3, tint, alpha * .15F, alpha * .15F);
        c.ribbon(points, width, tint, alpha * .75F, alpha * .75F);
        c.ribbon(points, width * .2F, WHITE, alpha * .85F, alpha * .85F);
    }
}
