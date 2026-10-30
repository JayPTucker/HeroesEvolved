package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Precognition is drawn as Isaac Mendez's paint: glowing brush strokes laid into the world. Red is
 * what is coming for you (the path of a shot, the reach of a blast), gold is what you foresee (ghosts of
 * where enemies are about to be, a painted canvas), and a white-gold cross is a future that changed.
 * Every stroke has a few thin bristles beside it, so lines read as painted, not drawn.
 */
final class PrecogEffects {
    static final int RED = 0xFF3040, DEEP = 0x9A0A1C, GOLD = 0xF7D88D, AMBER = 0xE8A040, WHITE = 0xFFF4E0, BLUE = 0x6FA8FF;
    private static final Vec3 UP = new Vec3(0, 1, 0);

    private PrecogEffects() {}

    static boolean play(ClientLevel level, Kind kind, Vec3 origin, Vec3 target, float size, boolean detail) {
        RandomSource random = level.random;
        switch (kind) {
            case PRECOG_THREAT -> threat(origin, target, size, random, detail);
            case PRECOG_RING -> ring(origin, size, random);
            case PRECOG_CROSS -> cross(origin, size, random, detail);
            case PRECOG_GHOST -> ghost(origin, target, size, random);
            case PRECOG_CANVAS -> canvas(origin, size, random);
            case PRECOG_BRUSH -> brushwork(origin, target, size, random);
            case PRECOG_FUTURE -> future(origin, target, size, random);
            case PRECOG_EXPOSED -> exposed(origin, size, random);
            case PRECOG_THREAD -> thread(origin, target, size, random);
            case PRECOG_ENDING -> ending(origin, target, size, random, detail);
            case PROPHECY_THREAD -> brush(wobble(origin, target, random, 0.05, 10), 0.05F, 0.015F, GOLD, 0.55F, 16, 0, random);
            case PROPHECY_BREAK -> {
                brush(circle(origin.add(0, 0.05, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), size, 36), 0.04F, 0.04F, GOLD, 0.5F, 20, 0, random);
                LightningRenderer.star(origin.add(0, 0.9, 0), 0.4F, WHITE, 0.6F, 10, 0);
            }
            default -> { return false; }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // What is coming
    // ------------------------------------------------------------------

    /** A shot's foreseen path and where it lands; a tiny {@code size} is only an aim (a bow being drawn). */
    private static void threat(Vec3 from, Vec3 to, float size, RandomSource random, boolean detail) {
        // Harm aimed at you ends at your own eyes: stop the paint short of the camera so it stays a
        // line coming at you, not a smear across the screen, and ring where it stops instead.
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        boolean atViewer = to.distanceToSqr(camera) < 2.5 * 2.5;
        double length = from.distanceTo(to);
        if (atViewer) {
            if (length < 3.2) return;
            to = from.add(to.subtract(from).normalize().scale(length - 2.2));
            length -= 2.2;
        }
        if (size < 0.5F) {
            // Dashes along the aim.
            Vec3 step = to.subtract(from).normalize();
            for (double d = 0.4; d < length - 0.2; d += 0.9)
                LightningRenderer.stroke(List.of(from.add(step.scale(d)), from.add(step.scale(Math.min(length, d + 0.45)))), 0.025F, 0.02F, RED, 0.5F, 16, 0);
            return;
        }
        int life = Math.clamp((int) size + 8, 10, 40);
        brush(wobble(from, to, random, 0.03, 12), 0.05F, 0.02F, RED, 0.75F, life, 0, random);
        LightningRenderer.stroke(List.of(from, to), 0.12F, 0.06F, DEEP, 0.25F, life, 0);
        if (atViewer) {
            brush(facing(to, 0.08F, 14), 0.012F, 0.012F, RED, 0.6F, life, 0, random);
            return;
        }
        mark(to, 0.32F, RED, life + 6, random);
        if (detail) brush(facing(to, 0.42F, 24), 0.025F, 0.025F, RED, 0.5F, life + 6, 0, random);
    }

    /** The reach of a blast that is about to go off, painted on the ground. */
    private static void ring(Vec3 center, float radius, RandomSource random) {
        Vec3 ground = center.add(0, 0.08, 0);
        Vec3 x = new Vec3(1, 0, 0), z = new Vec3(0, 0, 1);
        brush(circle(ground, x, z, radius, 48), 0.07F, 0.07F, RED, 0.75F, 34, 0, random);
        LightningRenderer.stroke(circle(ground, x, z, radius * 0.92, 48), 0.18F, 0.18F, DEEP, 0.28F, 34, 0);
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4 + random.nextDouble() * 0.2;
            Vec3 edge = ground.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
            brush(List.of(edge, edge.lerp(ground, 0.22)), 0.04F, 0.01F, RED, 0.6F, 30, 2, random);
        }
    }

    /** A future that changed: a quick white-gold cross and a spatter of paint. */
    private static void cross(Vec3 at, float size, RandomSource random, boolean detail) {
        // A cross on your own body would fill the screen: show it just ahead of your view, small.
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        if (at.distanceToSqr(camera.getPosition()) < 2.5 * 2.5) {
            Vec3 look = new Vec3(camera.getLookVector()), up = new Vec3(camera.getUpVector());
            at = camera.getPosition().add(look.scale(2.4)).subtract(up.scale(0.35));
            size *= 0.45F;
            detail = false;
        }
        mark(at, 0.45F * size, WHITE, 22, random);
        mark(at, 0.5F * size, GOLD, 18, random);
        LightningRenderer.star(at, 0.35F * size, WHITE, 0.7F, 8, 0);
        if (detail) spatter(at, 0.6 * size, 7, GOLD, random, 18);
    }

    // ------------------------------------------------------------------
    // What you foresee
    // ------------------------------------------------------------------

    /**
     * A ghost of where something is about to be: a painted silhouette of its height, and a faint path to
     * it. With no path ({@code from == to}) it is the afterimage you leave when you sidestep.
     */
    private static void ghost(Vec3 from, Vec3 at, float height, RandomSource random) {
        boolean afterimage = from.distanceToSqr(at) < 1.0E-4;
        int color = afterimage ? WHITE : GOLD;
        int life = afterimage ? 20 : 13;
        float alpha = afterimage ? 0.75F : 0.65F;
        if (!afterimage && from.distanceToSqr(at) > 0.25) {
            Vec3 a = from.add(0, 0.1, 0), b = at.add(0, 0.1, 0);
            double length = a.distanceTo(b);
            Vec3 step = b.subtract(a).normalize();
            for (double d = 0.2; d < length; d += 0.6)
                LightningRenderer.stroke(List.of(a.add(step.scale(d)), a.add(step.scale(Math.min(length, d + 0.3)))), 0.02F, 0.02F, GOLD, 0.3F, life, 0);
        }
        Vec3[] basis = cameraBasis(at);
        Vec3 left = basis[0], up = UP;
        float h = Math.max(0.5F, height);
        Vec3 head = at.add(up.scale(h * 0.87));
        brush(facing(head, h * 0.11F, 14), 0.04F, 0.04F, color, alpha, life, 0, random);
        Vec3 neck = at.add(up.scale(h * 0.75)), hips = at.add(up.scale(h * 0.42));
        brush(List.of(neck, neck.lerp(hips, 0.5).add(left.scale(0.02)), hips), 0.035F, 0.03F, color, alpha, life, 0, random);
        Vec3 shoulders = at.add(up.scale(h * 0.7));
        brush(List.of(shoulders.add(left.scale(h * 0.2)).subtract(0, h * 0.28, 0), shoulders.add(left.scale(h * 0.14)), shoulders,
                shoulders.subtract(left.scale(h * 0.14)), shoulders.subtract(left.scale(h * 0.2)).subtract(0, h * 0.28, 0)), 0.025F, 0.02F, color, alpha, life, 0, random);
        brush(List.of(at.add(left.scale(h * 0.12)), hips, at.subtract(left.scale(h * 0.12))), 0.03F, 0.025F, color, alpha, life, 0, random);
    }

    /** A painted canvas hanging over its subject: a gold frame with a red figure falling inside. */
    private static void canvas(Vec3 center, float threshold, RandomSource random) {
        Vec3[] basis = cameraBasis(center);
        Vec3 left = basis[0], up = basis[1];
        double w = 0.7, h = 0.5;
        Vec3 tl = center.add(left.scale(w)).add(up.scale(h)), tr = center.subtract(left.scale(w)).add(up.scale(h));
        Vec3 bl = center.add(left.scale(w)).subtract(up.scale(h)), br = center.subtract(left.scale(w)).subtract(up.scale(h));
        brush(List.of(tl, tr, br, bl, tl), 0.045F, 0.045F, GOLD, 0.7F, 12, 0, random);
        LightningRenderer.stroke(List.of(tl, tr, br, bl, tl), 0.12F, 0.12F, AMBER, 0.2F, 12, 0);
        // A falling figure, the ending they are painted into.
        Vec3 c = center.subtract(up.scale(0.05));
        brush(List.of(c.add(left.scale(0.32)).add(up.scale(0.18)), c, c.subtract(left.scale(0.3)).subtract(up.scale(0.26))), 0.06F, 0.025F, RED, 0.8F, 12, 0, random);
        brush(List.of(c.add(left.scale(0.05)).add(up.scale(0.02)), c.add(left.scale(0.12)).subtract(up.scale(0.28))), 0.04F, 0.02F, RED, 0.7F, 12, 0, random);
        brush(facing(c.add(left.scale(0.4)).add(up.scale(0.24)), 0.08F, 10), 0.035F, 0.035F, RED, 0.8F, 12, 0, random);
        // How far along the frame's bottom edge the ending is: the health it comes true at.
        brush(List.of(bl.add(up.scale(0.05)), bl.lerp(br, Math.clamp(threshold, 0.1, 1.0)).add(up.scale(0.05))), 0.02F, 0.02F, RED, 0.5F, 12, 0, random);
    }

    /** The trance: quick strokes of every colour in the air in front of the painter's hand. */
    private static void brushwork(Vec3 hand, Vec3 toward, float progress, RandomSource random) {
        Vec3 look = toward.subtract(hand).normalize();
        Vec3 side = look.cross(UP).lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : look.cross(UP).normalize();
        Vec3 up = side.cross(look).normalize();
        int[] colors = {RED, GOLD, BLUE, AMBER};
        for (int i = 0; i < 3; i++) {
            Vec3 start = hand.add(side.scale((random.nextDouble() - 0.5) * 0.5)).add(up.scale((random.nextDouble() - 0.5) * 0.4));
            double angle = random.nextDouble() * Math.PI * 2, length = 0.15 + 0.25 * progress;
            Vec3 dir = side.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
            Vec3 mid = start.add(dir.scale(length * 0.5)).add(dir.cross(look).scale(0.06));
            brush(List.of(start, mid, start.add(dir.scale(length))), 0.035F, 0.01F, colors[random.nextInt(colors.length)], 0.7F, 10, i, random);
        }
    }

    /** Living ahead of the present: gold rings spread out, and a ghost stands where you are about to be. */
    private static void future(Vec3 at, Vec3 ahead, float size, RandomSource random) {
        Vec3 x = new Vec3(1, 0, 0), z = new Vec3(0, 0, 1);
        for (int i = 0; i < 3; i++)
            brush(circle(at.add(0, 0.06 + i * 0.3 * size, 0), x, z, 0.5 + i * 0.6 * size, 40), 0.04F, 0.04F, i == 1 ? WHITE : GOLD, 0.55F, 18, i * 3, random);
        if (size >= 1) ghost(ahead, ahead, 1.8F, random);
    }

    /** An Exposed enemy: a red reticle over its chest. */
    private static void exposed(Vec3 feet, float height, RandomSource random) {
        Vec3 chest = feet.add(0, Math.max(0.5, height) * 0.6, 0);
        float radius = Math.max(0.35F, height * 0.3F);
        brush(facing(chest, radius, 28), 0.03F, 0.03F, RED, 0.7F, 18, 0, random);
        Vec3[] basis = cameraBasis(chest);
        for (Vec3 arm : new Vec3[] {basis[0], basis[0].reverse(), basis[1], basis[1].reverse()})
            brush(List.of(chest.add(arm.scale(radius * 0.7)), chest.add(arm.scale(radius * 1.35))), 0.03F, 0.015F, RED, 0.7F, 18, 0, random);
    }

    /** Fate Thread: a red string that sags between you and the one you hold. */
    private static void thread(Vec3 from, Vec3 to, float size, RandomSource random) {
        double length = from.distanceTo(to), sag = Math.min(1.5, length * 0.07);
        List<Vec3> points = new ArrayList<>();
        for (int i = 0; i <= 18; i++) {
            double t = i / 18.0;
            points.add(from.lerp(to, t).subtract(0, sag * 4 * t * (1 - t), 0));
        }
        LightningRenderer.stroke(points, 0.025F, 0.025F, RED, 0.75F, size > 1 ? 24 : 12, 0);
        LightningRenderer.stroke(points, 0.08F, 0.08F, DEEP, 0.3F, size > 1 ? 24 : 12, 0);
    }

    /** The painted ending arrives: two red slashes across it and paint thrown outward. */
    private static void ending(Vec3 feet, Vec3 eye, float height, RandomSource random, boolean detail) {
        Vec3 center = feet.lerp(eye, 0.6);
        Vec3[] basis = cameraBasis(center);
        double s = Math.max(0.6, height * 0.6);
        Vec3 a = basis[0].add(basis[1]).normalize().scale(s), b = basis[0].subtract(basis[1]).normalize().scale(s);
        brush(wobble(center.add(a), center.subtract(a), random, 0.04, 8), 0.12F, 0.03F, RED, 0.9F, 24, 0, random);
        brush(wobble(center.add(b), center.subtract(b), random, 0.04, 8), 0.12F, 0.03F, RED, 0.9F, 24, 3, random);
        LightningRenderer.stroke(List.of(center.add(a), center.subtract(a)), 0.03F, 0.01F, WHITE, 0.8F, 14, 0);
        LightningRenderer.stroke(List.of(center.add(b), center.subtract(b)), 0.03F, 0.01F, WHITE, 0.8F, 14, 3);
        brush(circle(feet.add(0, 0.06, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), 1.2, 40), 0.06F, 0.06F, RED, 0.6F, 26, 2, random);
        if (detail) spatter(center, 1.4, 12, RED, random, 22);
    }

    // ------------------------------------------------------------------
    // Brushwork
    // ------------------------------------------------------------------

    /** A painted stroke: a glowing body and a couple of thin bristle lines just beside it. */
    static void brush(List<Vec3> points, float startWidth, float endWidth, int color, float alpha, int life, int delay, RandomSource random) {
        if (points.size() < 2) return;
        // Paint has body: a little wider and brighter than a bolt of the same width.
        startWidth *= 1.4F; endWidth *= 1.4F; alpha = Math.min(1F, alpha * 1.2F);
        LightningRenderer.stroke(points, startWidth, endWidth, color, alpha, life, delay);
        for (int b = 0; b < 2; b++) {
            Vec3 shift = new Vec3(random.nextDouble() - 0.5, random.nextDouble() - 0.5, random.nextDouble() - 0.5).scale(startWidth * 1.6);
            List<Vec3> bristle = new ArrayList<>(points.size());
            for (Vec3 p : points) bristle.add(p.add(shift));
            LightningRenderer.stroke(bristle, startWidth * 0.3F, endWidth * 0.2F, color, alpha * 0.6F, life, delay);
        }
    }

    /** A gently waving line from {@code a} to {@code b}, like a hand-drawn stroke. */
    static List<Vec3> wobble(Vec3 a, Vec3 b, RandomSource random, double amount, int segments) {
        Vec3 dir = b.subtract(a);
        Vec3 side = dir.cross(UP).lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : dir.cross(UP).normalize();
        double phase = random.nextDouble() * Math.PI * 2, amplitude = Math.min(0.4, dir.length() * amount);
        List<Vec3> points = new ArrayList<>(segments + 1);
        for (int i = 0; i <= segments; i++) {
            double t = i / (double) segments;
            points.add(a.add(dir.scale(t)).add(side.scale(Math.sin(t * Math.PI * 2 + phase) * amplitude * Math.sin(t * Math.PI))));
        }
        return points;
    }

    /** An X facing the camera. */
    private static void mark(Vec3 at, float size, int color, int life, RandomSource random) {
        Vec3[] basis = cameraBasis(at);
        Vec3 a = basis[0].add(basis[1]).normalize().scale(size), b = basis[0].subtract(basis[1]).normalize().scale(size);
        brush(List.of(at.add(a), at.subtract(a)), 0.06F, 0.02F, color, 0.8F, life, 0, random);
        brush(List.of(at.add(b), at.subtract(b)), 0.06F, 0.02F, color, 0.8F, life, 1, random);
    }

    private static void spatter(Vec3 at, double reach, int count, int color, RandomSource random, int life) {
        for (int i = 0; i < count; i++) {
            Vec3 dir = new Vec3(random.nextDouble() - 0.5, random.nextDouble() - 0.3, random.nextDouble() - 0.5).normalize();
            double from = reach * (0.2 + random.nextDouble() * 0.3), to = reach * (0.5 + random.nextDouble() * 0.5);
            LightningRenderer.stroke(List.of(at.add(dir.scale(from)), at.add(dir.scale(to))), 0.035F, 0.005F, color, 0.7F, life, random.nextInt(3));
        }
    }

    static List<Vec3> circle(Vec3 center, Vec3 a, Vec3 b, double radius, int points) {
        List<Vec3> out = new ArrayList<>(points + 1);
        for (int i = 0; i <= points; i++) {
            double angle = i * Math.PI * 2 / points;
            out.add(center.add(a.scale(Math.cos(angle) * radius)).add(b.scale(Math.sin(angle) * radius)));
        }
        return out;
    }

    /** A circle facing the camera. */
    private static List<Vec3> facing(Vec3 center, double radius, int points) {
        Vec3[] basis = cameraBasis(center);
        return circle(center, basis[0], basis[1], radius, points);
    }

    /** Left and up, as the camera sees {@code at}: anything drawn in this plane faces the viewer. */
    private static Vec3[] cameraBasis(Vec3 at) {
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 view = at.subtract(camera);
        if (view.lengthSqr() < 1.0E-4) view = new Vec3(0, 0, 1);
        Vec3 left = UP.cross(view);
        if (left.lengthSqr() < 1.0E-6) left = new Vec3(1, 0, 0);
        left = left.normalize();
        Vec3 up = view.cross(left).normalize();
        return new Vec3[] {left, up};
    }
}
