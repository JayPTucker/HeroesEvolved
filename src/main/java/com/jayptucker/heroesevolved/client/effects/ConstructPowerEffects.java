package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

import static com.jayptucker.heroesevolved.client.effects.PowerParticle.Shape.*;

/**
 * Technopathy draws routed circuits, not lightning; assembly draws discrete courses of material,
 * not spell circles. Every endpoint is recorded in world space and every piece expires in at most
 * 32 ticks. All pieces share the ordinary ability particle budget, including delayed pieces.
 */
final class ConstructPowerEffects {
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final Vec3 X = new Vec3(1, 0, 0);
    private static final Vec3 Z = new Vec3(0, 0, 1);
    private static final int CYAN = 0x67E8EB, GREEN = 0xB5F799, CIRCUIT = 0x23777F;
    private static final int SLATE = 0x89908D, STONE = 0xB6A68D, AMBER = 0xEDB76C, SEAM = 0xFFF0C5;

    private ConstructPowerEffects() {}

    static boolean play(ClientLevel level, Kind kind, Vec3 origin, Vec3 target,
                        float scale, boolean detail, int density) {
        switch (kind) {
            case TECH_LINK, TECH_PULSE, TECH_SCAN, TECH_CAGE,
                    MATTER_WEAVE, MATTER_SHARD, MATTER_COLLAPSE, MATTER_SHIELD -> { }
            default -> { return false; }
        }
        if (!valid(origin) || !valid(target)
                || !Float.isFinite(scale)) return true;
        float size = Math.clamp(scale, .1F, 16);
        boolean rich = detail && density >= 16;
        Brush brush = new Brush(level, rich ? 76 : detail ? 48 : 28);
        switch (kind) {
            case TECH_LINK -> link(brush, origin, limit(origin, target, 128), size, detail, rich);
            case TECH_PULSE -> pulse(brush, origin, Math.clamp(size, 1, 10), detail, rich);
            case TECH_SCAN -> scan(brush, target, Math.clamp(size, 1, 16), detail, rich);
            case TECH_CAGE -> cage(brush, target, Math.clamp(size, .6F, 5), detail, rich);
            case MATTER_WEAVE -> weave(brush, origin, target, Math.clamp(size, .4F, 1.8F), detail, rich);
            case MATTER_SHARD -> shard(brush, origin, limit(origin, target, 12), Math.clamp(size, .4F, 1.8F), detail);
            case MATTER_COLLAPSE -> collapse(brush, origin, Math.clamp(size, .7F, 8), detail, rich);
            case MATTER_SHIELD -> shield(brush, origin, target, Math.clamp(size, 1, 6), detail, rich);
            default -> { }
        }
        return true;
    }

    /** Three orthogonal legs carry a visible data packet into a square terminal. */
    private static void link(Brush b, Vec3 from, Vec3 to, float scale, boolean detail, boolean rich) {
        Vec3 direction = direction(from, to), right = right(direction), vertical = right.cross(direction).normalize();
        Vec3 first = new Vec3(to.x, from.y, from.z);
        Vec3 second = new Vec3(to.x, to.y, from.z);
        Vec3[] points = {from, first, second, to};
        for (int i = 0; i < 3; i++) {
            if (points[i].distanceToSqr(points[i + 1]) < .002) continue;
            b.trace(points[i], points[i + 1], i * 2, detail);
            if (rich) {
                Vec3 offset = right.scale(.09);
                b.line(points[i].add(offset), points[i + 1].add(offset), .009F, 15, i * 2, GREEN, .32F);
            }
        }
        terminal(b, from, right, vertical, .12 + scale * .045, 0, CYAN, detail);
        terminal(b, to, right, vertical, .19 + scale * .07, 5, GREEN, detail);
    }

    /** The surge steps along eight etched, right-angle tracks instead of expanding as a ring. */
    private static void pulse(Brush b, Vec3 center, float radius, boolean detail, boolean rich) {
        center = center.add(0, .065, 0);
        int branches = detail ? 8 : 4;
        terminal(b, center, X, Z, .22, 0, GREEN, false);
        for (int i = 0; i < branches; i++) {
            Vec3 outward = cardinal(i), sideways = UP.cross(outward);
            double handed = i < 4 ? 1 : -1;
            Vec3 start = center.add(sideways.scale(handed * .19));
            Vec3 elbow = start.add(outward.scale(radius * .38));
            Vec3 corner = elbow.add(sideways.scale(handed * radius * .19));
            Vec3 end = corner.add(outward.scale(radius * .52));
            b.line(start, elbow, .025F, 16, 0, CYAN, .67F);
            b.line(elbow, corner, .025F, 16, 2, CYAN, .67F);
            b.line(corner, end, .021F, 16, 4, GREEN, .73F);
            b.packet(corner, end, .038F, 9, 4, GREEN);
            if (rich) {
                Vec3 branch = elbow.add(outward.scale(-.1)).add(sideways.scale(-handed * radius * .18));
                b.line(elbow, branch, .012F, 14, 3, CIRCUIT, .65F);
                terminal(b, end, X, Z, .11, 6, GREEN, false);
            }
        }
    }

    /** Parallel scan bars resolve through a cuboid with separated corner brackets. */
    private static void scan(Brush b, Vec3 center, float radius, boolean detail, boolean rich) {
        double span = Math.clamp(.65 + radius * .075, .8, 1.8);
        double height = span * .72;
        brackets(b, center, X, Z, span, span, .26, 18, CYAN, .63F);
        int rows = detail ? 7 : 3;
        for (int row = 0; row < rows; row++) {
            double t = row / (double) Math.max(1, rows - 1);
            double y = -height + 2 * height * t;
            Vec3 left = center.add(-span, y, -span), right = center.add(span, y, -span);
            int delay = row * 2;
            b.line(left, right, .014F, 12, delay, row % 2 == 0 ? CYAN : GREEN, .49F);
            b.line(right, center.add(span, y, span), .011F, 12, delay, CIRCUIT, .56F);
            if (rich) b.line(center.add(-span, y, span), center.add(span, y, span), .011F, 12, delay, GREEN, .24F);
        }
        for (int i = 0; i < 4; i++) {
            double x = (i & 1) == 0 ? -span : span, z = (i & 2) == 0 ? -span : span;
            b.line(center.add(x, -height, z), center.add(x, height, z), .017F, 22, i, CYAN, .43F);
        }
        if (rich) {
            // Sparse reach indicators show the real search extent without a radius-sized solid box.
            for (int i = 0; i < 4; i++) {
                Vec3 end = center.add(cardinal(i).scale(radius));
                Vec3 side = UP.cross(cardinal(i)).scale(.24);
                b.line(end.subtract(side), end.add(side), .02F, 16, 5, GREEN, .6F);
                b.line(end, end.subtract(cardinal(i).scale(.42)), .02F, 16, 5, GREEN, .6F);
            }
        }
    }

    /** A relay trap is a grounded node cage; it never follows the player or the camera. */
    private static void cage(Brush b, Vec3 center, float radius, boolean detail, boolean rich) {
        double half = radius * .72, low = -.45, high = Math.clamp(radius, .9, 2.3);
        Vec3 bottom = center.add(0, low, 0), top = center.add(0, high, 0);
        rectangle(b, bottom, X, Z, half, half, .018F, 18, 0, CIRCUIT, .7F);
        rectangle(b, top, X, Z, half, half, .023F, 18, 3, CYAN, .6F);
        for (int i = 0; i < 4; i++) {
            double x = (i & 1) == 0 ? -half : half, z = (i & 2) == 0 ? -half : half;
            Vec3 base = bottom.add(x, 0, z), tip = top.add(x, 0, z);
            b.line(base, tip, .025F, 18, i, CYAN, .58F);
            b.packet(base, tip, .045F, 13, i, GREEN);
            if (detail) terminal(b, base, X, Z, .13, i, GREEN, false);
        }
        if (rich) {
            // Small horizontal offsets make the face traces look like connected boards.
            for (int i = 0; i < 4; i++) {
                Vec3 out = cardinal(i), side = UP.cross(out);
                Vec3 mid = center.add(out.scale(half)).add(0, high * .4, 0);
                Vec3 corner = mid.add(side.scale(half * .35));
                b.line(mid.subtract(side.scale(half * .4)), corner, .013F, 17, 2, GREEN, .42F);
                b.line(corner, corner.add(0, high * .25, 0), .013F, 17, 3, GREEN, .42F);
            }
        }
    }

    /** Courses click into place; the actual server-owned blocks remain visible between the seams. */
    private static void weave(Brush b, Vec3 from, Vec3 center, float scale, boolean detail, boolean rich) {
        // Outside the real cube faces: an inset lattice is completely hidden by block depth tests.
        double half = .515 * scale;
        int courses = detail ? 3 : 2;
        for (int row = 0; row < courses; row++) {
            double y = -half + (row + .5) * 2 * half / courses;
            int delay = row * 3;
            rectangle(b, center.add(0, y, 0), X, Z, half, half, .024F, 16, delay, AMBER, .68F);
        }
        // Vertical seams are staggered like masonry instead of drawn as one luminous cube.
        for (int i = 0; i < 4; i++) {
            double x = (i & 1) == 0 ? -half : half, z = (i & 2) == 0 ? -half : half;
            b.line(center.add(x, -half, z), center.add(x, half, z), .016F, 18, i, SEAM, .56F);
        }
        int pieces = rich ? 8 : detail ? 5 : 3;
        for (int i = 0; i < pieces; i++) {
            Vec3 radial = cardinal(i).add(0, i % 2 == 0 ? .35 : -.25, 0).normalize();
            Vec3 start = center.add(radial.scale(.9 + scale * .28));
            Vec3 destination = center.add(radial.scale(half * .8));
            Vec3 travel = destination.subtract(start).scale(1 / 10.0);
            b.crystal(start, radial, .08F * scale, .045F * scale, 10, i % 3 * 2, SLATE, .75F, travel);
            if (rich) b.line(start, destination, .01F, 9, i % 3 * 2, STONE, .27F);
        }
        if (detail && from.distanceToSqr(center) > 1 && from.distanceToSqr(center) < 24 * 24) {
            Vec3 end = center.add(0, half, 0), midpoint = from.lerp(end, .5).add(0, -.15, 0);
            b.line(midpoint, end, .011F, 12, 0, AMBER, .32F);
        }
    }

    /** A short, layered stone dart; six simultaneous projectiles must not each spawn a full burst. */
    private static void shard(Brush b, Vec3 from, Vec3 to, float scale, boolean detail) {
        Vec3 axis = direction(from, to), side = right(axis), up = side.cross(axis).normalize();
        Vec3 point = to.subtract(axis.scale(.15));
        b.crystal(point, axis, .1F * scale, .04F * scale, 5, 0, STONE, .9F, Vec3.ZERO);
        b.line(from, to, .025F * scale, 6, 0, AMBER, .64F);
        if (!detail) return;
        Vec3 tail = point.subtract(axis.scale(.48 * scale));
        for (int i = 0; i < 3; i++) {
            double angle = i * Math.PI * 2 / 3;
            Vec3 edge = tail.add(side.scale(Math.cos(angle) * .16 * scale)).add(up.scale(Math.sin(angle) * .16 * scale));
            b.line(edge, point.add(axis.scale(.2 * scale)), .018F, 5, 0, i == 0 ? SEAM : SLATE, .8F);
        }
        if (from.distanceToSqr(to) > .06) {
            b.crystal(from.lerp(to, .35), axis, .04F * scale, .005F, 7, 0, SLATE, .5F, axis.scale(-.025));
        }
    }

    /** Broken courses contract into their anchor while individual facets tumble outward. */
    private static void collapse(Brush b, Vec3 center, float radius, boolean detail, boolean rich) {
        int sides = detail ? 8 : 4;
        for (int i = 0; i < sides; i++) {
            double angle = i * Math.PI * 2 / sides;
            Vec3 radial = new Vec3(Math.cos(angle), .12 + (i % 3) * .18, Math.sin(angle)).normalize();
            Vec3 side = UP.cross(radial).normalize();
            Vec3 base = center.add(radial.scale(radius * .72));
            Vec3 inward = center.add(radial.scale(radius * .15));
            b.line(base.subtract(side.scale(.28)), base.add(side.scale(.28)), .045F, 14, i % 3, AMBER, .62F);
            b.line(base, inward, .012F, 11, i % 3, SEAM, .43F);
            b.crystal(base, side.add(0, .4, 0), .09F + radius * .009F, .016F, 18, i % 3,
                    i % 2 == 0 ? SLATE : STONE, .78F, radial.scale(-radius * .025).add(0, -.018, 0));
            if (rich) {
                Vec3 high = base.add(0, .55, 0);
                b.crystal(high, radial, .065F, .012F, 22, 0, SLATE, .65F,
                        radial.scale(.04).add(0, -.025, 0));
            }
        }
        terminal(b, center.add(0, .06, 0), X, Z, Math.min(.5, radius * .2), 2, SEAM, false);
    }

    /** Three segmented armor panels with visible joints, oriented to the supplied facing vector. */
    private static void shield(Brush b, Vec3 center, Vec3 toward, float radius, boolean detail, boolean rich) {
        Vec3 direction = direction(center, toward);
        // The masonry stays upright even when the owner looks straight up or down.
        Vec3 forward = new Vec3(direction.x, 0, direction.z);
        if (forward.lengthSqr() < .001) forward = Z;
        forward = forward.normalize();
        Vec3 side = UP.cross(forward).normalize();
        double width = Math.clamp(radius * .26, .45, 1.25), height = Math.clamp(radius * .4, .8, 1.65);
        int panels = detail ? 3 : 1;
        for (int panel = 0; panel < panels; panel++) {
            int offset = panels == 1 ? 0 : panel - 1;
            Vec3 normal = forward.add(side.scale(offset * -.23)).normalize();
            Vec3 across = UP.cross(normal).normalize();
            Vec3 at = center.add(forward.scale(.8 + radius * .08)).add(side.scale(offset * width * 1.7));
            int delay = Math.abs(offset) * 2;
            rectangle(b, at, across, UP, width * .78, height, .025F, 13, delay, AMBER, .68F);
            int rows = rich ? 4 : 2;
            for (int row = 1; row < rows; row++) {
                Vec3 course = at.add(0, -height + row * 2 * height / rows, 0);
                b.line(course.subtract(across.scale(width * .75)), course.add(across.scale(width * .75)),
                        .017F, 12, delay + row, SEAM, .51F);
            }
            if (rich) {
                // A broad gray backing strip supplies weight without an opaque screen-filling flash.
                Vec3 back = at.subtract(normal.scale(.025));
                b.line(back.add(0, -height * .78, 0), back.add(0, height * .78, 0),
                        (float) (width * .53), 11, delay, SLATE, .17F);
                b.line(at.add(0, -.4 * height, 0), at.add(0, .4 * height, 0), .012F, 12, delay, AMBER, .49F);
            }
        }
    }

    private static void terminal(Brush b, Vec3 at, Vec3 right, Vec3 up, double half,
                                 int delay, int color, boolean detail) {
        // The source can be the local player's eyes; a terminal through the near plane obscures aim.
        var minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.gameRenderer != null
                && minecraft.gameRenderer.getMainCamera().getPosition().distanceToSqr(at) < .35 * .35) return;
        rectangle(b, at, right, up, half, half, .016F, 17, delay, color, .7F);
        if (detail) b.crystal(at, up, .026F, .009F, 13, delay, color, .8F, Vec3.ZERO);
    }

    private static void rectangle(Brush b, Vec3 at, Vec3 horizontal, Vec3 vertical,
                                  double width, double height, float thickness,
                                  int life, int delay, int color, float alpha) {
        Vec3 h = horizontal.scale(width), v = vertical.scale(height);
        Vec3 a = at.subtract(h).subtract(v), c = at.add(h).add(v);
        Vec3 d = at.subtract(h).add(v), e = at.add(h).subtract(v);
        b.line(a, e, thickness, life, delay, color, alpha);
        b.line(e, c, thickness, life, delay, color, alpha);
        b.line(c, d, thickness, life, delay, color, alpha);
        b.line(d, a, thickness, life, delay, color, alpha);
    }

    private static void brackets(Brush b, Vec3 at, Vec3 horizontal, Vec3 vertical,
                                 double width, double height, double fraction,
                                 int life, int color, float alpha) {
        for (int i = 0; i < 4; i++) {
            double x = (i & 1) == 0 ? -1 : 1, y = (i & 2) == 0 ? -1 : 1;
            Vec3 corner = at.add(horizontal.scale(x * width)).add(vertical.scale(y * height));
            b.line(corner, corner.subtract(horizontal.scale(x * width * fraction)), .022F, life, 0, color, alpha);
            b.line(corner, corner.subtract(vertical.scale(y * height * fraction)), .022F, life, 0, color, alpha);
        }
    }

    private static Vec3 cardinal(int index) {
        return switch (index & 3) { case 0 -> X; case 1 -> Z; case 2 -> X.scale(-1); default -> Z.scale(-1); };
    }

    private static Vec3 direction(Vec3 from, Vec3 to) {
        Vec3 delta = to.subtract(from);
        return delta.lengthSqr() < .0001 ? Z : delta.normalize();
    }

    private static Vec3 right(Vec3 normal) {
        return Math.abs(normal.y) > .98 ? X : normal.cross(UP).normalize();
    }

    private static Vec3 limit(Vec3 from, Vec3 to, double range) {
        return from.distanceToSqr(to) <= range * range ? to : from.add(direction(from, to).scale(range));
    }

    private static boolean valid(Vec3 point) {
        return Double.isFinite(point.lengthSqr()) && Math.abs(point.x) <= 32_000_000
                && Math.abs(point.y) <= 32_000_000 && Math.abs(point.z) <= 32_000_000;
    }

    /** Per-event caps complement the renderer's per-tick and particle-engine group caps. */
    private static final class Brush {
        private final ClientLevel level;
        private int remaining;

        private Brush(ClientLevel level, int limit) { this.level = level; remaining = limit; }

        private void line(Vec3 from, Vec3 to, float width, int life, int delay, int color, float alpha) {
            if (remaining <= 0 || from.distanceToSqr(to) < .00001) return;
            remaining--;
            // The shared ribbon sprite feathers across most of its width; leave a readable core
            // at ordinary play distances without adding another glow layer or extra particles.
            float visibleWidth = width < .1F ? width * 2.8F : width;
            AbilityEffectRenderer.add(new PowerParticle(level, STREAK, from, to, visibleWidth, visibleWidth * .48F,
                    life, delay, color, Math.min(.95F, alpha * 1.15F)));
        }

        private void crystal(Vec3 at, Vec3 axis, float start, float end, int life, int delay,
                             int color, float alpha, Vec3 velocity) {
            if (remaining <= 0) return;
            remaining--;
            AbilityEffectRenderer.add(new PowerParticle(level, CRYSTAL, at, axis, start * 1.25F, end * 1.25F,
                    life, delay, color, alpha).moving(velocity));
        }

        private void packet(Vec3 from, Vec3 to, float size, int life, int delay, int color) {
            if (from.distanceToSqr(to) < .002) return;
            crystal(from, to.subtract(from).normalize(), size, size * .6F, life, delay,
                    color, .95F, to.subtract(from).scale(1.0 / life));
        }

        private void trace(Vec3 from, Vec3 to, int delay, boolean detail) {
            line(from, to, .022F, 17, delay, CYAN, .73F);
            if (detail) line(from, to, .063F, 17, delay, CIRCUIT, .23F);
            packet(from, to, .038F, 12, delay, GREEN);
        }
    }
}
