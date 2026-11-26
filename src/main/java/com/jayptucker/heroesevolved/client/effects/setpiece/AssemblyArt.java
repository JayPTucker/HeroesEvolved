package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.assembly.AssemblyGeometry;
import com.jayptucker.heroesevolved.client.ClientMatterState;
import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient.Active;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.hash;
import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.rgb;
import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.smooth;

/**
 * Matter Assembly drawn as one idea: <b>crystal folded out of thin air</b>. Matter is a ring of amber fragments
 * orbiting the body; building throws fragments from the hands into a blueprint outline, and each cell snaps solid
 * with a bright seam; Shards are spinning crystals that flash where they ricochet; whatever is taken apart flies home
 * into the orbit. Palette: {@code 7A4513} deep amber (dark seams, in the soft pass), {@code F2A848} amber,
 * {@code FFC766} honey, {@code FFE3A0} seam light, {@code FFF1C8} core, {@code BFE8FF} ice glint. Shapes come from
 * {@link AssemblyGeometry}, the same ones the server builds.
 */
final class AssemblyArt {
    private static final float[] AMBER = rgb(0xF2A848), SEAM = rgb(0xFFE3A0), PLATE = rgb(0xC79A62), DUST = {0.7F, 0.62F, 0.5F},
            CRYSTAL = rgb(0xBFE8FF), CORE = rgb(0xFFF1C8), HEAD = rgb(0x6E655C), HONEY = rgb(0xFFC766), DEEP = rgb(0x7A4513);
    private static final Vec3 UP = new Vec3(0, 1, 0);

    private AssemblyArt() {}

    private static boolean minimal() { return Minecraft.getInstance().options.particles().get() == ParticleStatus.MINIMAL; }

    /** The hand a player builds with: from the shoulder along the look, or just below the view in first person. */
    private static Vec3 hand(Canvas c, Player p, double reach) {
        Minecraft mc = Minecraft.getInstance();
        if (p == mc.player && mc.options.getCameraType() == CameraType.FIRST_PERSON) {
            Vec3 look = p.getViewVector(c.partial), right = Vec3.directionFromRotation(0, p.getViewYRot(c.partial) + 90);
            Vec3 up = right.cross(look).normalize();
            return c.camera().add(look.scale(reach + .15)).add(right.scale(p.getMainArm() == HumanoidArm.RIGHT ? .36 : -.36)).subtract(up.scale(.4));
        }
        double side = p.getMainArm() == HumanoidArm.RIGHT ? -1 : 1;
        double yaw = Math.toRadians(Mth.rotLerp(c.partial, p.yBodyRotO, p.yBodyRot));
        return p.getPosition(c.partial).add(Math.cos(yaw) * side * .3125, p.isCrouching() ? 1.17 : 1.375, Math.sin(yaw) * side * .3125)
                .add(p.getViewVector(c.partial).scale(reach));
    }

    /** Where a construct's Matter comes from: the bound builder's hand, or above {@code fallback}. */
    private static Vec3 source(Canvas c, Active a, Vec3 fallback) {
        return a.bound(c) instanceof Player p ? hand(c, p, .62) : fallback.add(0, 1.3, 0);
    }

    /** A cell's twelve edges, lit: a thin seam of light (and a dark seam behind it so it reads at noon). */
    private static void edges(Canvas c, BlockPos cell, float width, float[] col, float alpha) {
        if (alpha < .01F) return;
        double inset = -.01;
        double x0 = cell.getX() + inset, y0 = cell.getY() + inset, z0 = cell.getZ() + inset, s = 1 - 2 * inset;
        int[][] e = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int[] pair : e) {
            Vec3 a = new Vec3(x0 + (pair[0] & 1) * s, y0 + (pair[0] >> 1 & 1) * s, z0 + (pair[0] >> 2 & 1) * s);
            Vec3 b = new Vec3(x0 + (pair[1] & 1) * s, y0 + (pair[1] >> 1 & 1) * s, z0 + (pair[1] >> 2 & 1) * s);
            if (c.soft()) c.line(a, b, width * 2.2F, DEEP, alpha * .35F, alpha * .35F);
            if (c.glow()) c.line(a, b, width, col, alpha, alpha);
        }
    }

    /** One fragment of Matter: a small amber diamond pointing along {@code along}, with a warm glint. */
    private static void fragment(Canvas c, Vec3 at, Vec3 along, float size, float spin, float alpha) {
        if (alpha < .02F) return;
        Vec3 d = along.lengthSqr() < 1e-6 ? UP : along.normalize();
        if (c.solid()) c.shard(at.subtract(d.scale(size * .8)), at.add(d.scale(size)), size * .45F, 4, spin, AMBER, 1);
        if (c.glow()) c.glow(at, size * 1.3F, HONEY, .38F * alpha);
    }

    /** A fragment's flight from {@code from} to {@code to} over its own window: an arc, and a short bright tail. */
    private static void flight(Canvas c, Vec3 from, Vec3 to, float k, float lift, float size, int seed, float alpha) {
        if (k <= 0 || k >= 1) return;
        float e = k * k * (3 - 2 * k), back = Math.max(0, e - .18F);
        Vec3 at = from.lerp(to, e).add(0, Mth.sin(e * Mth.PI) * lift, 0);
        Vec3 tail = from.lerp(to, back).add(0, Mth.sin(back * Mth.PI) * lift, 0);
        fragment(c, at, at.subtract(tail), size, seed + k * 9, alpha);
        if (c.glow()) {
            c.line(tail, at, size * .55F, AMBER, 0, .55F * alpha);
            c.line(tail.lerp(at, .5), at, size * .2F, CORE, 0, .8F * alpha);
        }
    }

    // ------------------------------------------------------------------ Matter: the orbit

    /**
     * Every assembler's Matter, drawn every frame: fragments in two tilted rings round the chest, turning opposite
     * ways. The ring grows and thins one fragment at a time as Matter is spent and comes home ({@link ClientMatterState}
     * eases the count); in first person the fragments that would pass through the view are left out.
     */
    static void drawLive(Canvas c) {
        if (c.soft() || ClientMatterState.assemblers().isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        float clock = (c.level.getGameTime() % 24000 + c.partial);
        for (ClientMatterState.Assembler a : ClientMatterState.assemblers()) {
            if (!(c.level.getEntity(a.entity) instanceof Player p) || !p.isAlive() || p.isInvisible()
                    || p.position().distanceToSqr(c.camera()) > 64 * 64) continue;
            boolean self = p == mc.player && mc.options.getCameraType() == CameraType.FIRST_PERSON;
            int whole = (int) Math.floor(a.shown);
            float partial = a.shown - whole;
            int n = Math.min(32, whole + (partial > .02F ? 1 : 0));
            if (n <= 0) continue;
            Vec3 feet = p.getPosition(c.partial);
            double height = p.isCrouching() ? .9 : 1.12;
            int inner = (n + 1) / 2, outer = n / 2;
            for (int i = 0; i < n; i++) {
                boolean ringA = i % 2 == 0;
                int k = i / 2, count = ringA ? inner : outer;
                float speed = ringA ? .045F : -.034F, tilt = ringA ? .26F : -.33F;
                float angle = clock * speed + k * Mth.TWO_PI / Math.max(1, count) + (ringA ? 0 : .4F);
                float r = ringA ? .66F : .82F;
                Vec3 at = feet.add(Mth.cos(angle) * r, height + Mth.sin(angle) * r * tilt + Mth.sin(clock * .17F + i) * .035, Mth.sin(angle) * r);
                if (self && at.distanceToSqr(c.camera()) < .5) continue;
                Vec3 along = new Vec3(-Mth.sin(angle) * Math.signum(speed), Mth.cos(angle) * tilt * Math.signum(speed), Mth.cos(angle) * Math.signum(speed));
                // The last fragment fades in or out as the count moves.
                float alpha = i == n - 1 && partial > .02F ? partial : 1;
                fragment(c, at, along, .085F * (.5F + .5F * alpha), i * 1.7F + clock * .1F, alpha);
            }
        }
    }

    // ------------------------------------------------------------------ building

    /**
     * A construct folding together: fragments leave the builder's hand in arcs and land in its shape, its cells
     * flashing in as they arrive, and a lattice of light runs over it and fades. Origin the shape's centre, target the
     * builder's feet, scale its size; bound to the builder.
     */
    static void rise(Canvas c, Active a, float t) {
        int fragments = minimal() ? 8 : 18;
        Vec3 from = source(c, a, a.target());
        for (int i = 0; i < fragments; i++) {
            float start = hash(a.seed(), i + 200) * 8, k = Mth.clamp((t - start) / 9, 0, 1);
            if (t < start) continue;
            float s = a.scale() * 0.8F;
            Vec3 to = a.origin().add((hash(a.seed(), i + 80) - 0.5) * 2 * s, (hash(a.seed(), i + 120) - 0.5) * 1.6 * s, (hash(a.seed(), i + 160) - 0.5) * 2 * s);
            flight(c, from, to, k, .8F + a.scale() * .25F, .1F, i, 1);
            if (k >= 1 && t - start - 9 < 5 && c.glow()) c.glow(to, .4F * (1 - (t - start - 9) / 5), SEAM, .7F);
        }
        float lattice = smooth(10, 16, t) * Mth.clamp((a.piece().ticks - t) / 10, 0, 1);
        if (lattice > 0.01F) {
            float s = a.scale();
            if (c.glow()) c.shell(a.origin(), s, s * 0.8F, s, Mth.PI, SEAM, 0.45F * lattice, 0.1F, Canvas.hexes(5, 0.07F, 0.04F), t * 0.02F);
            if (c.glow()) c.glow(a.origin(), s * 1.4F, AMBER, 0.18F * lattice);
        }
    }

    /**
     * Panel: a blueprint of the shape is drawn in thin light first, then fragments fly from the hand into it one cell
     * after another, each cell snapping solid with a bright seam, and a sheen sweeps across the finished pane.
     */
    static void panel(Canvas c, Active a, float t) {
        int flags = Math.round(a.scale());
        boolean flat = (flags & AssemblyGeometry.FLAT) != 0, wings = (flags & AssemblyGeometry.WINGS) != 0;
        Vec3 n = a.target().subtract(a.origin());
        Direction facing = flat ? Direction.NORTH : Direction.getNearest(n.x, 0, n.z);
        BlockPos base = BlockPos.containing(a.origin());
        List<BlockPos> cells = AssemblyGeometry.panel(base, facing, flat, wings);
        float f = Canvas.fade(t, a.piece().ticks, 0, 5);
        Vec3 from = source(c, a, a.origin());
        // The blueprint: every cell outlined faintly before any Matter arrives.
        float blueprint = smooth(0, 2, t) * (1 - smooth(6, 12, t));
        for (BlockPos cell : cells) edges(c, cell, .014F, SEAM, .45F * blueprint * f);
        for (int i = 0; i < cells.size(); i++) {
            BlockPos cell = cells.get(i);
            Vec3 center = Vec3.atCenterOf(cell);
            float arrive = AssemblyGeometry.panelArrival(i) + .5F;
            int per = minimal() ? 1 : 2;
            for (int j = 0; j < per; j++) {
                float start = arrive - 4 - j * .7F;
                Vec3 jitter = new Vec3(hash(a.seed(), i * 7 + j) - .5, hash(a.seed(), i * 7 + j + 3) - .5, hash(a.seed(), i * 7 + j + 5) - .5).scale(.5);
                flight(c, from, center.add(jitter), Mth.clamp((t - start) / 4, 0, 1), .5F, .09F, i * 3 + j, f);
            }
            // The snap: the cell's seams flare as it locks, then settle.
            float snap = smooth(arrive - .5F, arrive, t) * (1 - smooth(arrive, arrive + 7, t));
            if (snap > .01F) {
                edges(c, cell, .03F, CORE, snap * f);
                if (c.glow()) c.glow(center, .62F, AMBER, .35F * snap * f);
            }
        }
        // The sheen: a band of light wiping across the finished pane.
        float done = AssemblyGeometry.panelArrival(cells.size()) + .5F, wipe = smooth(done, done + 6, t);
        if (wipe > 0 && wipe < 1 && c.glow()) {
            Direction side = facing.getClockWise();
            Vec3 across = flat ? new Vec3(1, 0, 0) : Vec3.atLowerCornerOf(side.getNormal());
            Vec3 normal = flat ? UP : Vec3.atLowerCornerOf(facing.getNormal());
            Vec3 middle = Vec3.atCenterOf(base).add(flat ? Vec3.ZERO : UP.scale(.5)).add(normal.scale(-.52));
            Vec3 band = middle.add(across.scale(-1.5 + 3 * wipe));
            Vec3 spanDir = flat ? new Vec3(0, 0, 1) : UP;
            double span = flat ? 1.5 : 1;
            c.line(band.add(spanDir.scale(-span)), band.add(spanDir.scale(span)), .09F, SEAM, .7F * f, .7F * f);
            c.line(band.add(spanDir.scale(-span)), band.add(spanDir.scale(span)), .03F, CORE, f, f);
        }
    }

    /**
     * A Skywalk plate (scale 1): fragments converge under the foot and the three cells flash as they hold. At the start
     * of a Skywalk (scale 2, bound to the walker): the air under the feet firms into a ring that spreads and fades.
     */
    static void plate(Canvas c, Active a, float t) {
        float f = Canvas.fade(t, a.piece().ticks, 0, 6);
        if (a.scale() >= 1.5F) {
            Vec3 feet = a.bound(c) != null ? a.bound(c).getPosition(c.partial) : a.origin();
            float grow = smooth(0, 10, t);
            if (c.glow()) {
                c.ring(feet.add(0, .04, 0), UP, .4F + 1.6F * grow, .05F, t * .1F, Mth.TWO_PI, SEAM, f * (1 - grow));
                c.ring(feet.add(0, .04, 0), UP, .3F + .9F * grow, .03F, -t * .15F, Mth.TWO_PI - 1, AMBER, f * (1 - grow));
            }
            return;
        }
        Vec3 n = a.target().subtract(a.origin());
        Direction forward = Direction.getNearest(n.x, 0, n.z), side = forward.getClockWise();
        BlockPos middle = BlockPos.containing(a.origin());
        for (int o = -1; o <= 1; o++) {
            BlockPos cell = middle.relative(side, o);
            edges(c, cell, .022F, SEAM, f * (1 - smooth(2, 12, t)));
            if (t < 4) for (int j = 0; j < (minimal() ? 1 : 2); j++) {
                float ang = hash(a.seed(), o * 5 + j) * Mth.TWO_PI;
                Vec3 from = Vec3.atCenterOf(cell).add(Mth.cos(ang) * 1.3, -.9, Mth.sin(ang) * 1.3);
                flight(c, from, Vec3.atCenterOf(cell), smooth(0, 3.5F, t + j * .5F), .2F, .07F, o * 5 + j, f);
            }
        }
    }

    // ------------------------------------------------------------------ Launch Pad

    /**
     * A Launch Pad held: a crystal plate lying flat, two rings of light (the inner one turning), and three chevrons
     * running out along the way it throws. A pad hanging in the air (scale 2) also sheds slow glints downward, so it
     * reads as floating. Placed: fragments rain down into the plate, the rings lock with a flash, and the chevrons fire once.
     */
    static void pad(Canvas c, Active a, float t, boolean placed) {
        Vec3 center = a.origin().add(0, .05, 0);
        Vec3 along = a.target().subtract(a.origin());
        along = along.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : new Vec3(along.x, 0, along.z).normalize();
        Vec3 side = along.cross(UP).normalize();
        boolean floating = a.scale() >= 1.5F;
        float clock = c.level.getGameTime() % 2400 + c.partial;
        float f = placed ? Canvas.fade(t, a.piece().ticks, 0, 2) : Canvas.fade(t, a.piece().ticks, 2, 2);
        float half = (float) AssemblyGeometry.PAD_HALF;
        float lock = placed ? smooth(6, 9, t) : 1;
        if (placed) for (int i = 0; i < (minimal() ? 6 : 12); i++) {
            float ang = hash(a.seed(), i) * Mth.TWO_PI, r = half * (.3F + .7F * hash(a.seed(), i + 9));
            Vec3 to = center.add(Mth.cos(ang) * r, 0, Mth.sin(ang) * r);
            flight(c, to.add((hash(a.seed(), i + 20) - .5) * 2, 2.6, (hash(a.seed(), i + 30) - .5) * 2), to, smooth(i * .4F, i * .4F + 6, t), -.3F, .08F, i, f);
        }
        if (c.soft()) c.disc(center, UP, half * lock, DEEP, .0F, .18F * f * lock);
        if (c.glow()) {
            c.disc(center.add(0, .01, 0), UP, half * lock, AMBER, .14F * f, .05F * f);
            c.ring(center, UP, half * lock, .06F, 0, Mth.TWO_PI, SEAM, .85F * f);
            c.ring(center, UP, half * .62F * lock, .035F, clock * .06F, clock * .06F + Mth.TWO_PI * .8F, AMBER, .75F * f);
            if (placed && t > 6 && t < 12) c.ring(center, UP, half * (1 + .5F * smooth(6, 12, t)), .1F, 0, Mth.TWO_PI, CORE, f * (1 - smooth(6, 12, t)));
            for (int k = 0; k < 3; k++) {
                float u = placed ? Mth.clamp((t - 9 - k * 2) / 6, 0, 1) : (clock * .05F + k / 3F) % 1;
                if (u <= 0 || u >= 1) continue;
                float glow = Mth.sin(u * Mth.PI) * f * lock;
                Vec3 tip = center.add(along.scale(-half * .8 + u * half * 1.9)).add(0, .02, 0);
                Vec3 wingA = tip.subtract(along.scale(.38)).add(side.scale(.42)), wingB = tip.subtract(along.scale(.38)).add(side.scale(-.42));
                c.line(wingA, tip, .07F, AMBER, glow * .7F, glow * .7F);
                c.line(wingB, tip, .07F, AMBER, glow * .7F, glow * .7F);
                c.line(wingA, tip, .025F, CORE, glow, glow);
                c.line(wingB, tip, .025F, CORE, glow, glow);
            }
            if (floating) for (int i = 0; i < (minimal() ? 1 : 3); i++) {
                float drop = ((clock * .04F + hash(a.seed(), i)) % 1);
                float ang = hash(a.seed(), i + 40) * Mth.TWO_PI;
                Vec3 g = center.add(Mth.cos(ang) * half * .8, -drop * 1.4, Mth.sin(ang) * half * .8);
                c.glow(g, .07F, HONEY, .5F * f * (1 - drop));
            }
        }
    }

    /**
     * A throw off a pad: the plate flashes and a ring bursts out, a column of light leaps up along the throw, and a
     * rope of light trails the body for a moment. A thrown foe (scale 2) also wears a turning reticle: shoot it now.
     */
    static void launch(Canvas c, Active a, float t) {
        float f = Canvas.fade(t, a.piece().ticks, 0, 5);
        Vec3 pad = a.origin().add(0, .06, 0), dir = a.target().subtract(a.origin());
        Vec3 up = dir.lengthSqr() < 1e-6 ? UP : dir.normalize();
        float burst = smooth(0, 8, t);
        if (c.glow()) {
            c.ring(pad, UP, 1 + 1.8F * burst, .09F, 0, Mth.TWO_PI, CORE, f * (1 - burst));
            c.ring(pad, UP, .8F + 1.2F * burst, .05F, 0, Mth.TWO_PI, AMBER, f * (1 - burst));
            c.glow(pad, 1.2F * (1 - smooth(0, 5, t)), HONEY, .6F * f);
            float reach = 1.5F + 5 * smooth(0, 4, t);
            c.line(pad, pad.add(up.scale(reach)), .22F * (1 - burst), AMBER, .5F * f, 0);
            c.line(pad, pad.add(up.scale(reach)), .07F * (1 - burst), CORE, .9F * f, 0);
        }
        Entity body = a.bound(c);
        if (body == null || !body.isAlive()) return;
        Vec3 at = body.getPosition(c.partial).add(0, body.getBbHeight() * .4, 0);
        if (c.glow() && t < 12) {
            Vec3 tail = at.subtract(up.scale(Math.min(3, at.distanceTo(pad))));
            c.line(tail, at, .12F, AMBER, 0, .55F * f * (1 - smooth(6, 12, t)));
            c.line(tail, at, .035F, CORE, 0, .9F * f * (1 - smooth(6, 12, t)));
        }
        if (a.scale() >= 1.5F && c.glow()) {
            float spin = t * .25F, r = Math.max(.6F, body.getBbWidth() * .9F);
            c.ring(at, UP, r, .04F, spin, spin + Mth.TWO_PI * .8F, SEAM, .8F * f);
            for (int k = 0; k < 4; k++) {
                float ang = spin + k * Mth.HALF_PI;
                Vec3 out = new Vec3(Mth.cos(ang), 0, Mth.sin(ang));
                c.line(at.add(out.scale(r + .08)), at.add(out.scale(r + .32)), .035F, HONEY, f, f);
            }
        }
    }

    // ------------------------------------------------------------------ Kaleidoscope

    /**
     * Kaleidoscope: fragments fly from the builder into a ring of mirrors round the foe; then the inner faces shine with
     * sheens sliding up them, and a star of reflected light turns slowly between the walls.
     */
    static void kaleido(Canvas c, Active a, float t) {
        float life = a.piece().ticks, f = Canvas.fade(t, life, 2, 10);
        Vec3 feet = a.origin();
        BlockPos center = BlockPos.containing(feet.add(0, .1, 0));
        List<BlockPos> cells = AssemblyGeometry.kaleidoscope(center);
        if (t < AssemblyGeometry.kaleidoArrival(cells.size()) + 2) {
            Vec3 from = source(c, a, a.target());
            for (int i = 0; i < cells.size(); i++) {
                float arrive = AssemblyGeometry.kaleidoArrival(i) + .5F;
                flight(c, from, Vec3.atCenterOf(cells.get(i)), Mth.clamp((t - arrive + 4) / 4, 0, 1), .7F, .08F, i, f);
            }
        }
        for (BlockPos cell : cells) {
            float arrive = AssemblyGeometry.kaleidoArrival(cells.indexOf(cell)) + .5F;
            edges(c, cell, .022F, CORE, f * (1 - smooth(arrive, arrive + 8, t)) * smooth(arrive - .5F, arrive, t));
        }
        if (!c.glow() || t < 6) return;
        float clock = c.level.getGameTime() % 2400 + c.partial;
        // Sheens sliding up the four inner faces.
        for (int w = 0; w < 4; w++) {
            Direction face = Direction.from2DDataValue(w);
            Vec3 n = Vec3.atLowerCornerOf(face.getNormal()), s = n.cross(UP);
            Vec3 plane = Vec3.atBottomCenterOf(center).add(n.scale(.52));
            float slide = (clock * .03F + w * .25F) % 1;
            Vec3 lo = plane.add(0, slide * 2, 0);
            c.line(lo.add(s.scale(-.5)), lo.add(s.scale(.5)).add(0, .35, 0), .05F, SEAM, .45F * f, .1F * f);
        }
        // The star: reflected rays bouncing round between the mirrors, turning.
        for (int level = 0; level < 2; level++) {
            Vec3 mid = Vec3.atBottomCenterOf(center).add(0, .55 + level * .9, 0);
            float turn = clock * (level == 0 ? .03F : -.025F);
            for (int k = 0; k < 4; k++) {
                float a0 = turn + k * Mth.HALF_PI, a1 = a0 + Mth.PI * .75F;
                Vec3 p0 = mid.add(Mth.cos(a0) * .5, 0, Mth.sin(a0) * .5), p1 = mid.add(Mth.cos(a1) * .5, 0, Mth.sin(a1) * .5);
                c.line(p0, p1, .022F, CRYSTAL, .55F * f, .55F * f);
                c.glow(p0, .08F, CORE, .6F * f);
            }
        }
    }

    // ------------------------------------------------------------------ Shards

    /**
     * One step of a Shard: the crystal crosses the step in a tick, turning end over end, with an amber tail that
     * thins behind it. Brighter (scale) for every ricochet it has taken.
     */
    static void shard(Canvas c, Active a, float t) {
        Vec3 from = a.origin(), to = a.target(), d = to.subtract(from);
        if (d.lengthSqr() < 1e-6) return;
        Vec3 dir = d.normalize();
        float bright = Math.max(1, a.scale()), u = Mth.clamp(t, 0, 1), fadeOut = 1 - smooth(0, 4, t);
        Vec3 head = from.lerp(to, u);
        if (t < 1.05F) {
            float spin = t * 3 + a.seed();
            if (c.solid()) c.shard(head.subtract(dir.scale(.32)), head.add(dir.scale(.28)), .075F * (.8F + .2F * bright), 4, spin,
                    bright > 1.4F ? CORE : CRYSTAL, 1);
            if (c.glow()) {
                c.glow(head, .28F * bright, AMBER, .5F);
                c.glow(head, .1F * bright, CORE, .9F);
            }
        }
        if (c.glow() && fadeOut > 0) {
            c.line(from, head, .055F * bright, AMBER, .05F * fadeOut, .55F * fadeOut);
            c.line(from, head, .016F * bright, CORE, .1F * fadeOut, .9F * fadeOut);
        }
    }

    /**
     * A ricochet off your own matter: a flash where it struck, a ring spreading flat on the face it bounced from, and
     * a spray of chips off the face. More ricochets, a bigger flash.
     */
    static void bounce(Canvas c, Active a, float t) {
        float f = Canvas.fade(t, a.piece().ticks, 0, 6), k = smooth(0, 7, t), power = Math.max(1, a.scale());
        Vec3 at = a.origin(), n = a.target().subtract(a.origin());
        n = n.lengthSqr() < 1e-6 ? UP : n.normalize();
        if (c.glow()) {
            c.glow(at, (.5F + .2F * power) * (1 - smooth(0, 4, t)), CORE, f);
            c.ring(at, n, .12F + (.6F + .15F * power) * k, .05F, 0, Mth.TWO_PI, SEAM, f * (1 - k));
        }
        int chips = minimal() ? 2 : 5;
        for (int i = 0; i < chips; i++) {
            Vec3 spray = n.add((hash(a.seed(), i) - .5) * 1.4, (hash(a.seed(), i + 5) - .5) * 1.4, (hash(a.seed(), i + 9) - .5) * 1.4).normalize();
            Vec3 p = at.add(spray.scale(.8 * k)).add(0, -.25 * k * k, 0);
            fragment(c, p, spray, .05F, i + t, f * (1 - k));
        }
    }

    /**
     * Matter flying home: as many fragments as came back (up to sixteen drawn) lift off where the construct stood and
     * arc into the owner's orbit one after another, each with a small glint as it rejoins.
     */
    static void returning(Canvas c, Active a, float t) {
        Entity owner = a.bound(c);
        if (owner == null) return;
        int n = Math.min(minimal() ? 6 : 16, Math.max(1, Math.round(a.scale())));
        Vec3 chest = owner.getPosition(c.partial).add(0, 1.12, 0);
        float spread = .5F + Math.min(2, a.scale() * .05F);
        for (int i = 0; i < n; i++) {
            float start = i * (12F / n), k = Mth.clamp((t - start) / 10, 0, 1);
            Vec3 from = a.origin().add((hash(a.seed(), i) - .5) * 2 * spread, (hash(a.seed(), i + 4) - .5) * spread, (hash(a.seed(), i + 8) - .5) * 2 * spread);
            float ang = hash(a.seed(), i + 12) * Mth.TWO_PI;
            Vec3 to = chest.add(Mth.cos(ang) * .7, 0, Mth.sin(ang) * .7);
            flight(c, from, to, k, 1.1F, .08F, i, 1);
            if (k >= 1 && t - start - 10 < 4 && c.glow()) c.glow(to, .22F * (1 - (t - start - 10) / 4), CORE, .8F);
        }
    }

    /**
     * A sentinel's head: a crystal turning over its pillar, a ring of light round it tilting as it turns, and a
     * pale beam sweeping slowly round as it searches. Gone with the pillar.
     */
    static void sentinel(Canvas c, Active a, float t) {
        Vec3 o = a.origin();
        BlockPos pillar = BlockPos.containing(o.x, o.y - 1.4, o.z);
        if (c.level != null && c.level.getBlockState(pillar).isAir()) return;
        float f = Canvas.fade(t, a.piece().ticks, 10, 20), spin = t * 0.08F, bob = Mth.sin(t * 0.15F) * 0.08F;
        Vec3 core = o.add(0, bob, 0);
        if (c.solid() && f > 0.5F) {
            for (int i = 0; i < 4; i++) {
                float ang = spin + i * Mth.HALF_PI;
                Vec3 out = new Vec3(Mth.cos(ang) * 0.42, 0, Mth.sin(ang) * 0.42);
                c.shard(core, core.add(out), 0.16F, 4, ang, CRYSTAL, 1);
            }
            c.shard(core, core.add(0, 0.55, 0), 0.2F, 4, spin, CRYSTAL, 1);
            c.shard(core, core.add(0, -0.45, 0), 0.18F, 4, -spin, CRYSTAL, 1);
        }
        if (c.glow()) {
            c.glow(core, 0.7F, CORE, 0.8F * f);
            c.ring(core, new Vec3(Mth.sin(spin) * 0.3, 1, Mth.cos(spin) * 0.3), 0.75F, 0.05F, 0, Mth.TWO_PI, AMBER, 0.8F * f);
            float sweep = t * 0.05F;
            Vec3 beam = core.add(Mth.cos(sweep) * 6, -0.8, Mth.sin(sweep) * 6);
            c.line(core, beam, 0.05F, CRYSTAL, 0.5F * f, 0);
        }
    }

    /**
     * Monument (the finisher, {@code assembly/Monument}): plates tear up from the ground all round and fly in arcs onto the
     * foe, seams trailing; a plinth rises under the statue (the foe itself drawn in stone, by {@code BodyCopies}); it holds,
     * then crumbles from the top into falling chips and dust, and the plinth sinks. Bound to the foe; origin its feet,
     * target the builder; scale its height.
     */
    /**
     * The alternate finishers ({@code assembly/AssemblyFinishers}). Hammer: plates fly up from the ground into a giant
     * hammer over the caster's head (a handle and a block of a head with a bright seam), it swings over in an arc onto
     * the foe, and breaks into tumbling plates and dust. Shard Orbit: crystal shards rise round the foe and orbit it,
     * faster and tighter, then all drive in at once in a flash and fall as chips. Origin the foe's feet, target the
     * caster's, scale the foe's height.
     */
    static void alternates(Canvas c, Active a, float t) {
        float h = Math.max(.6F, a.scale()), life = a.piece().ticks, f = Canvas.fade(t, life, 1, 6);
        Vec3 feet = a.origin(), home = a.target();
        int seed = a.seed();
        if (a.piece() == com.jayptucker.heroesevolved.styles.Piece.FINISH_HAMMER) {
            int built = com.jayptucker.heroesevolved.assembly.AssemblyFinishers.HAMMER_BUILT, land = com.jayptucker.heroesevolved.assembly.AssemblyFinishers.HAMMER_LAND;
            Vec3 head = com.jayptucker.heroesevolved.assembly.AssemblyFinishers.hammer(home, feet, h, Math.min(t, land));
            Vec3 grip = home.add(0, 1.9, 0);
            // Plates flying up from the ground into the head.
            if (t < built + 2) for (int i = 0; i < 10; i++) {
                float start = hash(seed, i) * 4, k = smooth(start, built, t);
                if (k <= 0 || k >= 1) continue;
                float ang = hash(seed, i + 20) * Mth.TWO_PI, r = 1.8F + hash(seed, i + 40) * 1.4F;
                Vec3 from = home.add(Mth.cos(ang) * r, -.2, Mth.sin(ang) * r);
                Vec3 at = from.lerp(head, k).add(0, Mth.sin(k * Mth.PI) * .8, 0);
                if (c.solid()) c.box(at, .2F, .05F, .15F, ang, k * 6, 0, PLATE, 1);
                if (c.glow()) c.line(from.lerp(head, Math.max(0, k - .2F)), at, .03F, SEAM, f * .2F, f * .8F);
            }
            if (t < land + 2) {
                float size = smooth(2, built, t);
                Vec3 d = head.subtract(grip);
                double len = d.length();
                if (len > .1 && size > .02F) {
                    d = d.scale(1 / len);
                    float yaw = (float) Math.atan2(-d.x, d.z), pitch = (float) Math.acos(Mth.clamp(d.y, -1, 1));
                    // A sledge: the head long in the swing (the box's own z), dark stone banded with plate at each face.
                    Vec3 along = new Vec3(-Math.cos(pitch) * Math.sin(yaw), -Math.sin(pitch), Math.cos(pitch) * Math.cos(yaw));
                    if (c.solid()) {
                        c.box(grip.lerp(head, .5), .07F * size, (float) len * .5F * size, .07F * size, yaw, pitch, 0, PLATE, 1);
                        c.box(head, .4F * size, .4F * size, .62F * size, yaw, pitch, 0, HEAD, 1);
                        for (int sign : new int[]{-1, 1})
                            c.box(head.add(along.scale(sign * .6 * size)), .45F * size, .45F * size, .06F * size, yaw, pitch, 0, PLATE, 1);
                    }
                    if (c.glow()) {
                        for (int sign : new int[]{-1, 1})
                            c.ring(head.add(along.scale(sign * .52 * size)), along, .46F * size, .03F, 0, Mth.TWO_PI, SEAM, f * .7F);
                        if (t > land - 3) c.glow(head, .7F, AMBER, f * .4F);
                    }
                }
            }
            if (t >= land) {
                float age = t - land;
                if (c.solid()) for (int i = 0; i < 14; i++) {
                    float ang = hash(seed, i + 80) * Mth.TWO_PI, out = .15F + .12F * age * hash(seed, i + 90);
                    double y = h + .3 + 1.2 * hash(seed, i + 100) * Math.min(1, age / 3) - .045 * age * age;
                    if (y < 0) continue;
                    c.box(feet.add(Mth.cos(ang) * out, y, Mth.sin(ang) * out), .14F, .05F, .11F, ang + age * .4F, age * .5F, 0, PLATE, 1);
                }
                if (c.soft()) c.ring(feet.add(0, .1, 0), Canvas.UP, .6F + age * .15F, .45F, 0, Mth.TWO_PI, DUST, f * .4F * (1 - smooth(0, 9, age)));
                if (c.glow()) c.ring(feet.add(0, .06, 0), Canvas.UP, .4F + 2.4F * smooth(land, land + 7, t), .05F, 0, Mth.TWO_PI, SEAM,
                        f * (1 - smooth(land, land + 7, t)));
            }
            return;
        }
        // Shard Orbit.
        int rise = com.jayptucker.heroesevolved.assembly.AssemblyFinishers.ORBIT_RISE, land = com.jayptucker.heroesevolved.assembly.AssemblyFinishers.ORBIT_LAND;
        Vec3 core = feet.add(0, h * .55, 0);
        int n = 7;
        if (t < land + 1) {
            float up = smooth(0, rise, t), close = smooth(rise, land, t);
            float spin = t * (.12F + .3F * close) + t * t * .01F;
            for (int i = 0; i < n; i++) {
                float ang = spin + i * Mth.TWO_PI / n;
                float r = Mth.lerp(close * close, 2.4F, .55F);
                double y = h * (.2 + .6 * hash(seed, i)) * up;
                Vec3 at = feet.add(Mth.cos(ang) * r, y + .1 * Mth.sin(t * .4F + i), Mth.sin(ang) * r);
                if (t >= land - 1) at = at.lerp(core, smooth(land - 1, land, t));
                Vec3 point = core.subtract(at);
                Vec3 tip = at.add(point.lengthSqr() < 1e-4 ? Canvas.UP.scale(.4) : point.normalize().scale(.45));
                c.shard(at, tip, .09F, 4, ang, CRYSTAL, c.solid() ? 1 : f * .5F);
                if (c.glow()) c.glow(at, .12F, CRYSTAL, f * .4F);
            }
            if (c.glow()) c.ring(feet.add(0, .05, 0), Canvas.UP, Mth.lerp(close, 2.4F, .6F), .03F, 0, Mth.TWO_PI, SEAM, f * .4F * up);
        } else {
            float age = t - land;
            if (c.glow()) {
                c.glow(core, 1.3F * (1 - smooth(0, 3, age)), CORE, f * (1 - smooth(0, 3, age)));
                c.ring(core, Canvas.UP, .3F + 1.8F * smooth(0, 6, age), .05F, 0, Mth.TWO_PI, CRYSTAL, f * (1 - smooth(0, 6, age)));
            }
            for (int i = 0; i < 12; i++) {
                float ang = hash(seed, i + 30) * Mth.TWO_PI;
                double y = h * .55 + .4 * hash(seed, i + 40) * Math.min(1, age / 2) - .04 * age * age;
                if (y < 0) continue;
                Vec3 at = feet.add(Mth.cos(ang) * (.2 + age * .1), y, Mth.sin(ang) * (.2 + age * .1));
                c.shard(at, at.add(Mth.cos(ang) * .15, .1, Mth.sin(ang) * .15), .04F, 4, ang + age, CRYSTAL, c.solid() ? 1 : f * .6F);
            }
        }
    }

    static void finisher(Canvas c, Active a, float t) {
        float h = Math.max(.6F, a.scale()), life = a.piece().ticks, f = Canvas.fade(t, life, 1, 6);
        Vec3 feet = a.origin();
        int shut = com.jayptucker.heroesevolved.combat.FinisherBeats.MONUMENT_SHUT;
        int crumble = com.jayptucker.heroesevolved.combat.FinisherBeats.MONUMENT_CRUMBLE;
        // Plates flying in from the ground to the body.
        if (t < shut + 2) for (int i = 0; i < 12; i++) {
            float start = hash(a.seed(), i) * 3, k = smooth(start, shut, t);
            if (k <= 0) continue;
            float ang = hash(a.seed(), i + 20) * Mth.TWO_PI, r = 2.2F + hash(a.seed(), i + 40) * 1.5F;
            Vec3 from = feet.add(Mth.cos(ang) * r, -.2, Mth.sin(ang) * r);
            Vec3 to = feet.add(Mth.cos(ang) * .35, h * (.15 + .8 * hash(a.seed(), i + 60)), Mth.sin(ang) * .35);
            Vec3 at = from.lerp(to, k).add(0, Mth.sin(k * Mth.PI) * 1.2, 0);
            if (c.solid() && k < 1) c.box(at, .22F, .05F, .16F, ang * Mth.RAD_TO_DEG, k * 360, 0, PLATE, 1);
            if (c.glow() && k < 1) c.line(from.lerp(to, Math.max(0, k - .25F)).add(0, Mth.sin(Math.max(0, k - .25F) * Mth.PI) * 1.2, 0), at, .03F, SEAM, f * .2F, f * .8F);
        }
        // The plinth rising under the statue, sinking once it crumbles.
        float lift = .5F * smooth(shut, shut + 8, t) * (1 - smooth(crumble + 2, crumble + 8, t));
        if (lift > .02F && c.solid()) c.box(feet.add(0, lift / 2 - .02, 0), 1.1F, lift, 1.1F, 0, 0, 0, PLATE, 1);
        if (t >= shut && t < crumble && c.glow()) {
            float seam = 1 - smooth(shut, shut + 6, t);
            c.ring(feet.add(0, lift + .02, 0), Canvas.UP, .6F, .03F, 0, Mth.TWO_PI, SEAM, f * seam);
        }
        // The crumble: chips falling from the top down, and dust.
        if (t >= crumble) {
            float age = t - crumble;
            if (c.solid()) for (int i = 0; i < 14; i++) {
                float delay = (1 - hash(a.seed(), i + 80)) * 4, fall = Math.max(0, age - delay);
                double y = .5 + h * hash(a.seed(), i + 80) - .04 * fall * fall;
                if (y < 0) continue;
                float ang = hash(a.seed(), i + 100) * Mth.TWO_PI;
                c.box(feet.add(Mth.cos(ang) * (.2 + fall * .04), y, Mth.sin(ang) * (.2 + fall * .04)), .12F, .1F, .12F, fall * 30, fall * 20, 0, PLATE, 1);
            }
            if (c.soft()) c.ring(feet.add(0, .1, 0), Canvas.UP, .5F + age * .12F, .4F, 0, Mth.TWO_PI, DUST, f * .35F * (1 - smooth(0, 8, age)));
        }
    }
}
