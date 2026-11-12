package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient.Active;
import com.jayptucker.heroesevolved.story.elle.ElleEntity;
import com.jayptucker.heroesevolved.story.elle.ElleMoves;
import com.jayptucker.heroesevolved.story.elle.PrimatechGeometry;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.UP;
import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.fade;
import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.hash;
import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.rgb;
import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.smooth;
import static net.minecraft.util.Mth.PI;
import static net.minecraft.util.Mth.TWO_PI;
import static net.minecraft.util.Mth.cos;
import static net.minecraft.util.Mth.sin;

/**
 * Elle Bishop on Primatech's test floor. One idea: <b>current crawls before it bites</b>. Every telegraph is a thin,
 * jittering filament or a creeping glow that tightens and brightens until it lands, and what lands is the same shape,
 * thick and white. Two colours tell you whose current it is: <b>her own</b> is pale electric blue; <b>the room's</b>
 * (banks, conduits, the pylon) is copper. A backfire is copper running the wrong way, into her.
 *
 * <p>Palette: {@code 2E6BD8} deep halo, {@code 7FD4FF} arc, {@code F2FBFF} core, {@code E0894A} copper (the grid),
 * {@code FFD7A8} hot copper core, {@code 1A1E24} scorch (normal blending).</p>
 *
 * <p>Floor shapes come from {@link PrimatechGeometry} and {@link ElleMoves}, the same numbers the fight uses, so a
 * conduit that lights is the strip that hurts and a ring's edge is where it stops reaching.</p>
 */
final class ElleArt {
    static final float[] HALO = rgb(0x2E6BD8), ARC = rgb(0x7FD4FF), CORE = rgb(0xF2FBFF), COPPER = rgb(0xE0894A),
            HOT = rgb(0xFFD7A8), COPPER_DEEP = rgb(0x8A3F1A), SCORCH = rgb(0x1A1E24), SMOKE = rgb(0x4A4E55);

    private ElleArt() {}

    private static boolean minimal() { return Minecraft.getInstance().options.particles().get() == ParticleStatus.MINIMAL; }
    private static boolean detail() { return Minecraft.getInstance().options.particles().get() == ParticleStatus.ALL; }
    private static float clock(Canvas c) { return c.level != null ? (c.level.getGameTime() % 24000) + c.partial : 0; }

    static void draw(Canvas c, Active a, float t) {
        switch (a.piece()) {
            case ELLE_ARENA -> arena(c, a, t);
            case ELLE_AURA -> aura(c, a, t);
            case ELLE_AIM -> aim(c, a, t);
            case ELLE_BOLT -> bolt(c, a, t);
            case ELLE_RING_MARK -> ringMark(c, a, t);
            case ELLE_RING -> ring(c, a, t);
            case ELLE_SIPHON -> siphon(c, a, t);
            case ELLE_BACKFIRE -> backfire(c, a, t);
            case ELLE_CONDUIT_MARK -> conduitMark(c, a, t);
            case ELLE_CONDUIT -> conduit(c, a, t);
            case ELLE_REBOOT -> reboot(c, a, t);
            case ELLE_STRIKE_MARK -> strikeMark(c, a, t);
            case ELLE_STRIKE -> strike(c, a, t);
            case ELLE_ORB -> orb(c, a, t);
            case ELLE_SHORT -> shortOut(c, a, t);
            case ELLE_PHASE -> phase(c, a, t);
            case ELLE_ARRIVE -> arrive(c, a, t);
            case ELLE_OMEN -> omen(c, a, t);
            case ELLE_DROP -> drop(c, a, t);
            default -> {}
        }
    }

    // ------------------------------------------------------------------
    // Primitives: a filament, a floor ring, a strip of floor
    // ------------------------------------------------------------------

    /**
     * A jagged channel of current from {@code a} to {@code b} in three layers (halo, body, core), with {@code jitter}
     * blocks of wander at its middle. Her own current is blue; pass {@code grid} for the room's copper.
     */
    static void filament(Canvas c, Vec3 a, Vec3 b, int seed, float jitter, float width, float alpha, boolean grid) {
        if (!c.glow() || alpha < 0.01F) return;
        double length = a.distanceTo(b);
        if (length < 0.02) return;
        int n = Mth.clamp((int) (length * 2.4) + 3, 4, 40);
        Vec3 d = b.subtract(a), side = d.cross(UP);
        if (side.lengthSqr() < 1e-6) side = new Vec3(1, 0, 0);
        side = side.normalize();
        Vec3 up = side.cross(d).normalize();
        Vec3[] points = new Vec3[n + 1];
        for (int i = 0; i <= n; i++) {
            float k = i / (float) n, taper = sin(k * PI);
            float ox = (hash(seed, i * 2) - 0.5F) * 2 * jitter * taper, oy = (hash(seed, i * 2 + 1) - 0.5F) * 2 * jitter * taper;
            points[i] = a.add(d.scale(k)).add(side.scale(ox)).add(up.scale(oy));
        }
        c.ribbon(points, width * 3.4F, grid ? COPPER_DEEP : HALO, alpha * 0.32F, alpha * 0.32F);
        c.ribbon(points, width, grid ? COPPER : ARC, alpha * 0.85F, alpha * 0.85F);
        c.ribbon(points, width * 0.35F, grid ? HOT : CORE, alpha, alpha);
    }

    /** A forked bolt: one channel and a few short branches off it. */
    static void forked(Canvas c, Vec3 a, Vec3 b, int seed, float width, float alpha, boolean grid, int branches) {
        float jitter = (float) Math.min(1.4, a.distanceTo(b) * 0.09);
        filament(c, a, b, seed, jitter, width, alpha, grid);
        if (minimal()) return;
        Vec3 d = b.subtract(a);
        for (int i = 0; i < branches; i++) {
            float k = 0.2F + 0.6F * hash(seed, 300 + i);
            Vec3 from = a.add(d.scale(k));
            Vec3 off = new Vec3(hash(seed, 310 + i) - 0.5, hash(seed, 320 + i) - 0.5, hash(seed, 330 + i) - 0.5).scale(2.2 * jitter + 0.4);
            filament(c, from, from.add(d.normalize().scale(0.6 + jitter)).add(off), seed * 7 + i, jitter * 0.5F, width * 0.45F, alpha * 0.7F, grid);
        }
    }

    /** A ring lying on the floor in three layers, from one angle to another. */
    static void floorRing(Canvas c, Vec3 at, float r, float width, float from, float to, float alpha, boolean grid) {
        if (!c.glow() || alpha < 0.01F) return;
        c.ring(at, UP, r, width * 3.2F, from, to, grid ? COPPER_DEEP : HALO, alpha * 0.3F);
        c.ring(at, UP, r, width, from, to, grid ? COPPER : ARC, alpha * 0.85F);
        c.ring(at, UP, r, width * 0.35F, from, to, grid ? HOT : CORE, alpha);
    }

    /** A ring stood upright across {@code axis}, in three layers. */
    static void band(Canvas c, Vec3 at, Vec3 axis, float r, float width, float from, float to, float alpha, boolean grid) {
        if (!c.glow() || alpha < 0.01F) return;
        c.ring(at, axis, r, width * 3.2F, from, to, grid ? COPPER_DEEP : HALO, alpha * 0.3F);
        c.ring(at, axis, r, width, from, to, grid ? COPPER : ARC, alpha * 0.85F);
        c.ring(at, axis, r, width * 0.35F, from, to, grid ? HOT : CORE, alpha);
    }

    /** A short burst of sparks: little filaments thrown out from {@code at}. */
    static void sparks(Canvas c, Vec3 at, float size, int seed, int count, float alpha, boolean grid) {
        int n = minimal() ? Math.max(1, count / 2) : count;
        for (int i = 0; i < n; i++) {
            float yaw = TWO_PI * hash(seed, i), pitch = (hash(seed, 40 + i) - 0.3F) * 1.6F;
            Vec3 dir = new Vec3(cos(yaw) * cos(pitch), sin(pitch), sin(yaw) * cos(pitch));
            filament(c, at, at.add(dir.scale(size * (0.5 + hash(seed, 80 + i)))), seed * 13 + i, size * 0.18F, 0.025F + size * 0.01F, alpha, grid);
        }
        if (c.glow()) c.glow(at, size * 0.6F, grid ? COPPER : ARC, alpha * 0.45F);
    }

    /** Light crawling along a strip of floor from {@code a} toward {@code b}, lit up to {@code reach} (0 to 1). */
    static void strip(Canvas c, Vec3 a, Vec3 b, float reach, float half, float alpha, boolean grid, float crawl) {
        if (!c.glow() || alpha < 0.01F) return;
        Vec3 d = b.subtract(a), front = a.add(d.scale(Mth.clamp(reach, 0, 1)));
        Vec3 side = d.cross(UP).normalize().scale(half);
        // The lit length, and the danger zone's two edges.
        c.line(a, front, 0.22F, grid ? COPPER : ARC, alpha * 0.6F, alpha * 0.9F);
        c.line(a, front, 0.06F, grid ? HOT : CORE, alpha, alpha);
        c.line(a.add(side), front.add(side), 0.04F, grid ? COPPER : ARC, alpha * 0.55F, alpha * 0.7F);
        c.line(a.subtract(side), front.subtract(side), 0.04F, grid ? COPPER : ARC, alpha * 0.55F, alpha * 0.7F);
        // Chevrons crawl toward the pylon.
        int marks = Math.max(2, (int) (a.distanceTo(b) / 1.2));
        Vec3 along = d.normalize();
        for (int i = 0; i < marks; i++) {
            float k = (i + crawl) / marks % 1;
            if (k > reach) continue;
            Vec3 p = a.add(d.scale(k));
            c.line(p.add(side.scale(0.7)), p.add(along.scale(0.45)), 0.05F, grid ? HOT : CORE, alpha * 0.7F, alpha * 0.7F);
            c.line(p.subtract(side.scale(0.7)), p.add(along.scale(0.45)), 0.05F, grid ? HOT : CORE, alpha * 0.7F, alpha * 0.7F);
        }
        c.glow(front, 0.5F, grid ? HOT : CORE, alpha * 0.6F);
    }

    /** A hand of hers, from her body as the server places it. */
    static Vec3 hand(Entity e, float partial) {
        float yaw = (e instanceof LivingEntity l ? Mth.rotLerp(partial, l.yBodyRotO, l.yBodyRot) : e.getYRot()) * Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(-cos(yaw), 0, -sin(yaw));
        return e.getPosition(partial).add(0, 1.375, 0).add(right.scale(0.3125)).add(e.getViewVector(partial).scale(0.72));
    }

    static Vec3 otherHand(Entity e, float partial) {
        float yaw = (e instanceof LivingEntity l ? Mth.rotLerp(partial, l.yBodyRotO, l.yBodyRot) : e.getYRot()) * Mth.DEG_TO_RAD;
        Vec3 left = new Vec3(cos(yaw), 0, sin(yaw));
        return e.getPosition(partial).add(0, 1.375, 0).add(left.scale(0.3125)).add(e.getViewVector(partial).scale(0.6));
    }

    // ------------------------------------------------------------------
    // The room and her body
    // ------------------------------------------------------------------

    /**
     * The test floor's grid, re-sent every second and bound to her: each live bank hums (a copper crackle from its face
     * to its rod) and a slow mote runs down its conduit to the pylon, and the pylon's column flickers with current.
     */
    private static void arena(Canvas c, Active a, float t) {
        float f = fade(t, a.piece().ticks, 4, 4);
        if (f <= 0 || !c.glow()) return;
        int mask = a.bound(c) instanceof ElleEntity elle ? elle.banks() : (1 << PrimatechGeometry.BANK_COUNT) - 1;
        Vec3 floor = a.origin();
        float now = clock(c);
        int live = Integer.bitCount(mask);
        for (int i = 0; i < PrimatechGeometry.BANK_COUNT; i++) {
            if (!PrimatechGeometry.live(mask, i)) continue;
            Vec3 face = PrimatechGeometry.bank(floor, i), rod = PrimatechGeometry.rod(floor, i);
            int flick = (int) (now / 3) + i * 31;
            if (hash(flick, 1) < 0.6F) filament(c, face, rod, flick, 0.25F, 0.03F, f * 0.7F, true);
            Vec3 s = PrimatechGeometry.conduitStart(floor, i), e = PrimatechGeometry.conduitEnd(floor, i);
            c.line(s, e, 0.16F, COPPER, f * 0.12F, f * 0.2F);
            if (!minimal()) {
                float k = (now * 0.03F + i * 0.17F) % 1;
                c.glow(s.lerp(e, k).add(0, 0.05, 0), 0.35F, HOT, f * 0.5F);
            }
            c.glow(face, 0.6F, COPPER, f * 0.25F);
        }
        // The pylon: a column of current from its pad to the coil, stronger with every bank feeding it.
        if (live > 0) {
            Vec3 pad = floor.add(0, 1.1, 0), coil = PrimatechGeometry.coil(floor);
            int flick = (int) (now / 2);
            filament(c, pad, coil, flick, 0.35F, 0.03F + live * 0.008F, f * (0.35F + live * 0.08F), true);
            floorRing(c, floor.add(0, 1.03, 0), (float) PrimatechGeometry.PYLON, 0.05F, 0, TWO_PI, f * (0.2F + live * 0.05F), true);
            c.glow(coil, 0.8F + live * 0.1F, HOT, f * 0.4F);
        }
    }

    /**
     * Her charge, re-sent every ten ticks: crackles at both hands, and her shield, one upright arc per live bank,
     * turning round her. After a siphon lands (the target is the bank), the stolen current flares in her.
     */
    private static void aura(Canvas c, Active a, float t) {
        Entity e = a.bound(c);
        if (e == null || !c.glow()) return;
        float f = fade(t, a.piece().ticks, 2, 2);
        Vec3 feet = e.getPosition(c.partial), chest = feet.add(0, 1.15, 0);
        float now = clock(c);
        int flick = (int) (now / 2);
        if (!a.target().equals(a.origin())) {
            // A siphon's end: her whole body flares copper-white.
            float k = t / a.piece().ticks;
            c.glow(chest, 1.6F * (1 - k) + 0.4F, HOT, (1 - k) * 0.8F);
            sparks(c, chest, 1.2F, flick, 8, (1 - k), true);
            return;
        }
        if (e instanceof ElleEntity elle && elle.isScene()) return;
        sparks(c, hand(e, c.partial), 0.35F, flick, 3, f * 0.8F, false);
        sparks(c, otherHand(e, c.partial), 0.3F, flick + 77, 3, f * 0.7F, false);
        int banks = Math.round(a.scale());
        if (banks <= 0 || a.scale() < 0.5F) return;
        float spin = now * 0.04F;
        for (int i = 0; i < banks; i++) {
            float ang = spin + i * TWO_PI / Math.max(1, banks);
            Vec3 axis = new Vec3(cos(ang), 0, sin(ang));
            Vec3 at = chest.add(new Vec3(-sin(ang), 0, cos(ang)).scale(0.05));
            band(c, at, axis, 1.0F, 0.025F, -PI * 0.42F, PI * 0.42F, f * 0.45F, false);
        }
    }

    // ------------------------------------------------------------------
    // Her attacks
    // ------------------------------------------------------------------

    /** The arc bolt's telegraph: a thin filament from her hand to where you stood, wandering less as it tightens. */
    private static void aim(Canvas c, Active a, float t) {
        float k = Mth.clamp(t / a.piece().ticks, 0, 1);
        float f = fade(t, a.piece().ticks, 2, 1);
        Vec3 hand = a.bound(c) != null ? hand(a.bound(c), c.partial) : a.origin();
        int flick = (int) (clock(c) / 2);
        filament(c, hand, a.target(), flick, 0.5F * (1 - k) + 0.05F, 0.012F + 0.02F * k, f * (0.35F + 0.6F * k), false);
        if (c.glow()) {
            // Where it lands: a small ring standing across the line, closing in.
            Vec3 dir = a.target().subtract(hand).normalize();
            band(c, a.target(), dir, 0.9F - 0.6F * k, 0.03F, 0, TWO_PI, f * (0.4F + 0.6F * k), false);
            c.glow(hand, 0.25F + 0.3F * k, CORE, f * 0.8F);
        }
    }

    /** The bolt itself: thick, forked, white at its heart, with a flash where it lands. */
    private static void bolt(Canvas c, Active a, float t) {
        float f = fade(t, a.piece().ticks, 0.5F, 7);
        float w = 0.05F * a.scale();
        forked(c, a.origin(), a.target(), a.seed(), w, f, false, 3);
        if (c.glow()) {
            c.glow(a.target(), 0.6F * a.scale(), CORE, f * 0.8F);
            c.glow(a.origin(), 0.35F * a.scale(), ARC, f * 0.6F);
        }
    }

    /** The ground current's telegraph: rings race out across the floor from her feet, and the edge brightens as it nears. */
    private static void ringMark(Canvas c, Active a, float t) {
        float r = a.scale(), k = Mth.clamp(t / a.piece().ticks, 0, 1), f = fade(t, a.piece().ticks, 2, 1);
        Vec3 at = a.origin();
        floorRing(c, at, r, 0.05F + 0.05F * k, 0, TWO_PI, f * (0.35F + 0.65F * k), false);
        for (int i = 0; i < 3; i++) {
            float run = ((t / 8F) + i / 3F) % 1;
            floorRing(c, at, r * run, 0.035F, 0, TWO_PI, f * 0.6F * (1 - run) * (0.4F + 0.6F * k), false);
        }
        if (c.glow()) c.disc(at.add(0, 0.01, 0), UP, r, ARC, f * 0.06F * k, f * 0.16F * k);
        // Spokes of current crawl out with the rings.
        int flick = (int) (clock(c) / 2);
        if (!minimal()) for (int i = 0; i < 8; i++) {
            float ang = i * TWO_PI / 8 + hash(a.seed(), i) * 0.4F;
            Vec3 tip = at.add(cos(ang) * r * k, 0.05, sin(ang) * r * k);
            filament(c, at.add(0, 0.05, 0), tip, flick + i, 0.15F, 0.02F, f * 0.5F, false);
        }
    }

    /** The floor discharges: a flash across the ring, current thrown out to its edge, a scorch left behind. */
    private static void ring(Canvas c, Active a, float t) {
        float r = a.scale(), f = fade(t, a.piece().ticks, 0.5F, 9);
        Vec3 at = a.origin();
        float grow = smooth(0, 3, t);
        if (c.glow()) {
            c.disc(at.add(0, 0.02, 0), UP, r * grow, CORE, f * 0.35F, f * 0.1F);
            floorRing(c, at, r * (0.95F + 0.1F * grow), 0.12F, 0, TWO_PI, f, false);
        }
        int spokes = minimal() ? 6 : 12;
        for (int i = 0; i < spokes; i++) {
            float ang = i * TWO_PI / spokes + hash(a.seed(), i);
            Vec3 tip = at.add(cos(ang) * r * grow, 0.15 + hash(a.seed(), 50 + i) * 0.5, sin(ang) * r * grow);
            filament(c, at.add(0, 0.1, 0), tip, a.seed() + i, 0.3F, 0.05F, f, false);
        }
        if (c.soft()) c.ring(at.add(0, 0.015, 0), UP, r * 0.5F, r * 0.5F, 0, TWO_PI, SCORCH, fade(t, a.piece().ticks, 2, 6) * 0.35F);
    }

    /**
     * A siphon: current is drawn out of a bank up an arc into her chest, copper motes running toward her. Round the bank's
     * breaker a ring fills as the siphon nears its end: pull the breaker before it closes.
     */
    private static void siphon(Canvas c, Active a, float t) {
        if (SetPieceClient.superseded(a, Set.of(Piece.ELLE_BACKFIRE), c.level.getGameTime())) return;
        float k = Mth.clamp(t / a.piece().ticks, 0, 1), f = fade(t, a.piece().ticks, 3, 2);
        Entity e = a.bound(c);
        Vec3 face = a.origin(), chest = e != null ? e.getPosition(c.partial).add(0, 1.2, 0) : a.target();
        int flick = (int) (clock(c) / 2);
        filament(c, face, chest, flick, 0.7F, 0.03F + 0.04F * k, f * (0.5F + 0.5F * k), true);
        if (!minimal()) filament(c, face, chest, flick + 101, 0.9F, 0.018F, f * 0.4F, false);
        if (c.glow()) {
            for (int i = 0; i < 5; i++) {
                float m = ((t * 0.09F) + i / 5F) % 1;
                c.glow(face.lerp(chest, m), 0.22F, HOT, f * 0.8F);
            }
            // The breaker: the face of the bank, a ring that fills toward the end.
            int i = Math.round(a.scale());
            Vec3 axis = i >= 0 && i < PrimatechGeometry.BANK_COUNT
                    ? new Vec3(PrimatechGeometry.facing(i).getStepX(), 0, PrimatechGeometry.facing(i).getStepZ()) : chest.subtract(face).normalize();
            Vec3 breaker = face.add(axis.scale(0.55));
            band(c, breaker, axis, 0.75F, 0.04F, 0, TWO_PI, f * 0.45F, true);
            band(c, breaker, axis, 0.75F, 0.07F, PI / 2, PI / 2 + TWO_PI * k, f, false);
            float pulse = 0.5F + 0.5F * sin(t * 0.9F);
            c.glow(breaker, 0.45F + 0.25F * pulse, HOT, f * (0.4F + 0.4F * pulse));
            c.glow(chest, 0.5F + 0.6F * k, HOT, f * 0.4F);
        }
    }

    /** The backfire: copper-white current slams up the arc into her and bursts round her body; the bank smokes. */
    private static void backfire(Canvas c, Active a, float t) {
        Entity e = a.bound(c);
        Vec3 face = a.origin(), chest = e != null ? e.getPosition(c.partial).add(0, 1.15, 0) : a.target();
        float f = fade(t, a.piece().ticks, 0.5F, 14);
        float run = smooth(0, 4, t);
        Vec3 front = face.lerp(chest, run);
        forked(c, face, front, a.seed(), 0.09F, f, true, 4);
        filament(c, face, front, a.seed() + 9, 0.5F, 0.05F, f * 0.8F, false);
        if (c.glow()) {
            float burst = smooth(3, 12, t);
            c.glow(chest, 2.4F * burst + 0.4F, HOT, f * 0.9F * (1 - burst * 0.5F));
            if (t > 3) {
                c.shell(chest, 1.8F * burst, 1.8F * burst, 1.8F * burst, PI, COPPER, f * 0.25F * (1 - burst), 0.2F, Canvas.PLAIN, 0);
                band(c, chest, UP, 2.2F * burst, 0.08F, 0, TWO_PI, f * (1 - burst), true);
            }
        }
        if (t > 3) sparks(c, chest, 1.6F, a.seed() + (int) (t / 2), 10, f, true);
        sparks(c, face, 0.9F, a.seed() * 3 + (int) (t / 2), 6, f * 0.8F, true);
        if (c.soft() && !minimal()) {
            var shade = Shades.smoke(c, SMOKE, 0.25F);
            for (int i = 0; i < 3; i++)
                c.cloud(face.add((hash(a.seed(), i) - 0.5) * 1.2, t * 0.04 + i * 0.3, (hash(a.seed(), 9 + i) - 0.5) * 1.2), 0.5F + t * 0.02F,
                        0.4F + t * 0.015F, 0.5F + t * 0.02F, i, t * 0.03F, fade(t, a.piece().ticks, 4, 12) * 0.45F, shade);
        }
    }

    /** A conduit's warning: copper light creeps from the bank toward the pylon, its edges showing how far the surge reaches. */
    private static void conduitMark(Canvas c, Active a, float t) {
        float k = Mth.clamp(t / a.piece().ticks, 0, 1), f = fade(t, a.piece().ticks, 2, 1);
        strip(c, a.origin(), a.target(), smooth(0, 0.8F, k), (float) PrimatechGeometry.CONDUIT_REACH, f * (0.4F + 0.6F * k), true, t * 0.08F);
        int flick = (int) (clock(c) / 2);
        if (k > 0.5F) filament(c, a.origin(), a.origin().lerp(a.target(), smooth(0, 0.8F, k)), flick, 0.25F, 0.025F, f * (k - 0.5F) * 1.6F, true);
    }

    /** The conduit surges: current runs the whole strip and arcs up off it. */
    private static void conduit(Canvas c, Active a, float t) {
        float f = fade(t, a.piece().ticks, 0.5F, 8);
        forked(c, a.origin(), a.target(), a.seed(), 0.07F, f, true, 3);
        Vec3 d = a.target().subtract(a.origin());
        Vec3 side = d.cross(UP).normalize().scale(PrimatechGeometry.CONDUIT_REACH);
        int n = minimal() ? 3 : 6;
        for (int i = 0; i < n; i++) {
            Vec3 p = a.origin().add(d.scale((i + 0.5) / n));
            filament(c, p, p.add(side.scale(hash(a.seed(), i) - 0.5).add(0, 0.6 + hash(a.seed(), 20 + i), 0)), a.seed() + i, 0.2F, 0.035F, f * 0.8F, true);
        }
        if (c.glow()) c.line(a.origin(), a.target(), (float) PrimatechGeometry.CONDUIT_REACH, HOT, f * 0.25F, f * 0.25F);
        if (c.soft()) c.line(a.origin().add(0, -0.01, 0), a.target().add(0, -0.01, 0), 0.45F, SCORCH, f * 0.4F, f * 0.4F);
    }

    /**
     * Her reboot: a thin current runs from her hand to a dead bank and a ring fills round its breaker; on the floor a
     * dashed copper circle shows where standing grounds the current.
     */
    private static void reboot(Canvas c, Active a, float t) {
        float k = Mth.clamp(t / a.piece().ticks, 0, 1), f = fade(t, a.piece().ticks, 3, 2);
        Entity e = a.bound(c);
        Vec3 hand = e != null ? hand(e, c.partial) : a.origin(), face = a.target();
        int flick = (int) (clock(c) / 2);
        filament(c, hand, face, flick, 0.6F, 0.02F + 0.02F * k, f * (0.4F + 0.4F * k), false);
        int i = Math.round(a.scale());
        if (i < 0 || i >= PrimatechGeometry.BANK_COUNT || !c.glow()) return;
        Vec3 axis = new Vec3(PrimatechGeometry.facing(i).getStepX(), 0, PrimatechGeometry.facing(i).getStepZ());
        Vec3 breaker = face.add(axis.scale(0.55));
        band(c, breaker, axis, 0.75F, 0.07F, PI / 2, PI / 2 + TWO_PI * k, f, false);
        // Where to stand: the grounding circle round the breaker, on the floor.
        Vec3 ground = new Vec3(breaker.x, Math.floor(breaker.y) - 0.98, breaker.z);
        int dashes = 16;
        for (int d = 0; d < dashes; d++) {
            float from = d * TWO_PI / dashes + t * 0.03F;
            floorRing(c, ground, (float) ElleMoves.GROUNDED, 0.05F, from, from + TWO_PI / dashes * 0.55F, f * 0.75F, true);
        }
        for (int m = 0; m < 4; m++) c.glow(hand.lerp(face, ((t * 0.07F) + m / 4F) % 1), 0.2F, CORE, f * 0.7F);
    }

    /** A storm strike's warning: a ring on the floor that tightens and brightens, and a glow gathering on the ceiling above it. */
    private static void strikeMark(Canvas c, Active a, float t) {
        float k = Mth.clamp(t / a.piece().ticks, 0, 1), f = fade(t, a.piece().ticks, 3, 1), r = a.scale();
        floorRing(c, a.origin(), r * (1.25F - 0.25F * k), 0.05F + 0.05F * k, 0, TWO_PI, f * (0.4F + 0.6F * k), false);
        if (!c.glow()) return;
        c.disc(a.origin(), UP, r, ARC, f * 0.04F, f * 0.18F * k);
        c.glow(a.target(), 0.4F + 1.2F * k, CORE, f * 0.5F * k);
        c.line(a.target(), a.origin(), 0.03F, ARC, f * 0.25F * k, f * 0.05F);
        int flick = (int) (clock(c) / 2);
        if (k > 0.4F) sparks(c, a.target(), 0.6F, flick, 4, f * k, false);
    }

    /** Lightning from the ceiling: a thick forked channel and a flash on the floor. */
    private static void strike(Canvas c, Active a, float t) {
        float f = fade(t, a.piece().ticks, 0.5F, 9);
        forked(c, a.target(), a.origin(), a.seed(), 0.12F, f, false, 4);
        if (c.glow()) {
            c.glow(a.origin(), 1.8F, CORE, f * 0.8F);
            floorRing(c, a.origin(), a.scale() * (1 + 0.4F * smooth(0, 6, t)), 0.1F, 0, TWO_PI, f, false);
        }
        if (c.soft()) c.disc(a.origin().add(0, 0.015, 0), UP, a.scale() * 0.7F, SCORCH, fade(t, a.piece().ticks, 1, 8) * 0.5F, 0);
    }

    /**
     * Ball lightning: it forms in her hands, its lane lit on the floor, then rolls down the lane at the server's speed.
     * The burst is its own piece; once it arrives the ball is gone.
     */
    private static void orb(Canvas c, Active a, float t) {
        if (SetPieceClient.superseded(a, Set.of(Piece.ELLE_RING), c.level.getGameTime())) return;
        int form = ElleMoves.BALL.windup();
        Vec3 from = a.origin(), to = a.target();
        double length = from.distanceTo(to);
        float travel = (float) (length / ElleMoves.BALL_SPEED);
        if (t > form + travel + 1) return;
        float f = fade(t, form + travel + 1, 3, 1);
        // The lane on the floor.
        Vec3 floorFrom = new Vec3(from.x, Math.floor(from.y) + 0.03, from.z), floorTo = new Vec3(to.x, Math.floor(from.y) + 0.03, to.z);
        if (Math.abs(from.y - Math.floor(from.y)) > 0.85) { floorFrom = floorFrom.add(0, -1, 0); floorTo = floorTo.add(0, -1, 0); }
        strip(c, floorFrom, floorTo, 1, 1.1F, f * 0.55F, false, t * 0.1F);
        float k = t < form ? 0 : Math.min(1, (t - form) / travel);
        Vec3 at = from.lerp(to, k);
        float size = t < form ? 0.15F + 0.35F * smooth(0, form, t) : 0.5F;
        int flick = (int) (clock(c) / 2);
        if (c.glow()) {
            c.glow(at, size * 2.2F, ARC, f * 0.5F);
            c.glow(at, size, CORE, f);
            c.shell(at, size, size, size, PI, ARC, f * 0.35F, 0.3F, Canvas.PLAIN, clock(c) * 0.3F);
        }
        sparks(c, at, size * 1.6F, flick, 5, f, false);
    }

    /** A bank shorts out: sparks fountain from its face, its rod crackles and dies, smoke rolls off. Twice as big when blown. */
    private static void shortOut(Canvas c, Active a, float t) {
        float s = a.scale(), f = fade(t, a.piece().ticks, 0.5F, 10);
        Vec3 face = a.origin();
        sparks(c, face, 0.8F * s, a.seed() + (int) (t / 2), 6 + (int) (4 * s), f, true);
        if (t < 6) filament(c, face, a.target(), a.seed() + (int) t, 0.3F, 0.04F, f, true);
        if (c.glow() && s > 1.5F) c.glow(face, 2.2F * (1 - smooth(0, 10, t)), HOT, f);
        if (c.soft() && !minimal()) {
            var shade = Shades.smoke(c, SMOKE, 0);
            for (int i = 0; i < (int) (2 * s); i++)
                c.cloud(face.add((hash(a.seed(), i) - 0.5) * 0.8, 0.2 + t * 0.05, (hash(a.seed(), 7 + i) - 0.5) * 0.8), 0.35F * s + t * 0.02F,
                        0.3F * s + t * 0.015F, 0.35F * s + t * 0.02F, i, t * 0.04F, fade(t, a.piece().ticks, 3, 10) * 0.4F, shade);
        }
    }

    /**
     * A phase turns: current bursts off her across the whole floor and up to the ceiling. At Blackout the ceiling's lights
     * flare one after another and go out.
     */
    private static void phase(Canvas c, Active a, float t) {
        float f = fade(t, a.piece().ticks, 0.5F, 12), grow = smooth(0, 14, t);
        Vec3 floor = a.origin(), surface = floor.add(0, 1.03, 0);
        Entity e = a.bound(c);
        Vec3 chest = e != null ? e.getPosition(c.partial).add(0, 1.15, 0) : a.target();
        Vec3 feet = new Vec3(chest.x, surface.y, chest.z);
        floorRing(c, feet, 13 * grow, 0.15F, 0, TWO_PI, f * (1 - grow * 0.6F), false);
        int n = minimal() ? 4 : 8;
        for (int i = 0; i < n; i++) {
            float ang = i * TWO_PI / n + hash(a.seed(), i);
            Vec3 top = new Vec3(chest.x + cos(ang) * 3, floor.y + PrimatechGeometry.CEILING - 0.2, chest.z + sin(ang) * 3);
            if (t < 10) filament(c, chest, top, a.seed() + i + (int) t, 0.5F, 0.05F, f, false);
        }
        if (c.glow()) c.glow(chest, 2.5F * (1 - grow) + 0.5F, CORE, f * 0.8F);
        if (Math.round(a.scale()) >= 3 && c.glow()) {
            int k = 0;
            for (int dx : new int[] {-9, -3, 3, 9})
                for (int dz : new int[] {-9, -3, 3, 9}) {
                    float at = 2 + k++ * 1.4F, life = fade(t - at, 6, 0.5F, 5);
                    if (t < at) continue;
                    Vec3 light = floor.add(dx, PrimatechGeometry.CEILING - 0.1, dz);
                    c.glow(light, 1.4F, HOT, life);
                    sparks(c, light, 0.6F, a.seed() + k, 3, life, true);
                }
        }
    }

    /**
     * Her arrival, in the dark the lights left: current crawls over her body from the ground up, a ring of it at her
     * feet, then a dart's pale streak from her toward you.
     */
    private static void arrive(Canvas c, Active a, float t) {
        Entity e = a.bound(c);
        Vec3 feet = e != null ? e.getPosition(c.partial) : a.origin();
        float f = fade(t, a.piece().ticks, 6, 6);
        int flick = (int) (clock(c) / 2);
        float rise = smooth(0, 24, t);
        floorRing(c, feet.add(0, 0.03, 0), 0.9F + 0.3F * sin(t * 0.4F), 0.05F, 0, TWO_PI, f * 0.8F, false);
        int strands = minimal() ? 3 : 6;
        for (int i = 0; i < strands; i++) {
            float ang = i * TWO_PI / strands + t * 0.05F;
            Vec3 low = feet.add(cos(ang) * 0.45, 0.05, sin(ang) * 0.45);
            Vec3 high = feet.add(cos(ang + 1) * 0.35, 0.1 + 1.85 * rise, sin(ang + 1) * 0.35);
            filament(c, low, high, flick + i * 17, 0.25F, 0.025F, f * 0.75F, false);
        }
        if (c.glow()) {
            c.glow(feet.add(0, 1.1, 0), 1.6F * rise, ARC, f * 0.25F);
            if (e != null) { c.glow(hand(e, c.partial), 0.4F, CORE, f * 0.8F); c.glow(otherHand(e, c.partial), 0.4F, CORE, f * 0.8F); }
        }
        // The dart, from behind her, toward you.
        if (t >= 38 && t < 44) {
            float k = smooth(38, 42, t);
            Vec3 from = feet.add(0, 1.4, 0), to = a.target();
            Vec3 tip = from.lerp(to, k), tail = from.lerp(to, Math.max(0, k - 0.25F));
            if (c.glow()) { c.line(tail, tip, 0.03F, CORE, 0, 0.9F); c.glow(tip, 0.2F, CORE, 0.9F); }
        }
    }

    /** An omen: a small crackle of current where a lamp stutters, a circuit misfires or a machine switches by itself. */
    private static void omen(Canvas c, Active a, float t) {
        float f = fade(t, a.piece().ticks, 1, 8);
        int kind = Math.round(a.scale());
        Vec3 at = a.origin();
        int flick = a.seed() + (int) (t / 2);
        if (kind == 4) {
            // The air hums: a faint ring of current standing in the air.
            band(c, at, new Vec3(0, 0.3, 1), 0.5F + t * 0.03F, 0.02F, 0, TWO_PI, f * 0.4F, false);
            return;
        }
        // Current crawls over the block's faces, then throws a few sparks off it.
        if (c.glow()) {
            c.glow(at, 1.1F, kind == 3 ? COPPER : ARC, f * 0.45F);
            c.glow(at, 0.45F, CORE, f * 0.6F);
        }
        for (int i = 0; i < 4; i++) {
            float a0 = TWO_PI * hash(flick, i), a1 = a0 + 1.2F + hash(flick, 10 + i);
            Vec3 p = at.add(cos(a0) * 0.55, (hash(flick, 20 + i) - 0.5) * 1.1, sin(a0) * 0.55);
            Vec3 q = at.add(cos(a1) * 0.55, (hash(flick, 30 + i) - 0.5) * 1.1, sin(a1) * 0.55);
            filament(c, p, q, flick + i, 0.15F, 0.03F, f, kind == 3);
        }
        sparks(c, at, kind == 3 ? 1.0F : 0.85F, flick, 5, f * 0.9F, kind == 3);
        if (kind == 2 && t < 8) filament(c, at, at.add(0, -0.8, 0), flick, 0.2F, 0.03F, f, false);
    }

    /** What she leaves: her capacitor flies to whoever beat her, a filament of its charge trailing behind. */
    private static void drop(Canvas c, Active a, float t) {
        float f = fade(t, a.piece().ticks, 2, 6), k = smooth(4, 30, t);
        Vec3 from = a.origin(), to = a.bound(c) instanceof LivingEntity l ? l.getEyePosition(c.partial).add(0, -0.4, 0) : a.target();
        Vec3 at = from.lerp(to, k).add(0, sin(k * PI) * 1.2, 0);
        if (c.solid()) {
            c.box(at, 0.12F, 0.2F, 0.12F, t * 0.15F, 0, 0, rgb(0x3A3F47), f);
            c.box(at.add(0, 0.21, 0), 0.13F, 0.03F, 0.13F, t * 0.15F, 0, 0, COPPER, f);
            c.box(at.add(0, -0.21, 0), 0.13F, 0.03F, 0.13F, t * 0.15F, 0, 0, COPPER, f);
        }
        if (c.glow()) c.glow(at, 0.45F, ARC, f * 0.8F);
        filament(c, from, at, a.seed() + (int) (t / 2), 0.3F, 0.02F, f * 0.6F * (1 - k), false);
    }
}
