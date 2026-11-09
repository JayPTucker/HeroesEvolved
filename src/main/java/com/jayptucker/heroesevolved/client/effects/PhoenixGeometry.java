package com.jayptucker.heroesevolved.client.effects;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The Phoenix's body plan, as polylines in world space. Pure math (no rendering) so it can be checked
 * without a game: every point is finite and the whole bird fits inside {@link #REACH} of its center.
 *
 * <p>{@code flap} is the wing angle in radians (positive raises the wings); {@code sweep} from 0 to 1
 * folds them back for a dive. The frame is built from the heading, with "up" kept as close to the
 * world's up as the heading allows, so a diving bird pitches down rather than rolling.
 */
public final class PhoenixGeometry {
    public static final double REACH = 4.5;
    /** Flight feathers per wing. */
    public static final int FEATHERS = 9;
    private static final Vec3 UP = new Vec3(0, 1, 0);

    /** The parts of one pose. Each feather and tail plume is its own short polyline. */
    public record Pose(List<Vec3> body, List<Vec3> leftWing, List<Vec3> rightWing, List<List<Vec3>> feathers,
                       List<List<Vec3>> tail, List<List<Vec3>> crest, Vec3 head) {
        public List<List<Vec3>> all() {
            List<List<Vec3>> lines = new ArrayList<>();
            lines.add(body); lines.add(leftWing); lines.add(rightWing);
            lines.addAll(feathers); lines.addAll(tail); lines.addAll(crest);
            return lines;
        }
    }

    private PhoenixGeometry() {}

    public static Pose pose(Vec3 center, Vec3 heading, double flap, double sweep, double tailPhase) {
        Vec3 f = heading.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : heading.normalize();
        Vec3 u = UP.subtract(f.scale(f.y));
        u = u.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0).subtract(f.scale(f.x)).normalize() : u.normalize();
        Vec3 r = f.cross(u).normalize();
        sweep = Math.clamp(sweep, 0, 1);

        Vec3 head = center.add(f.scale(0.85)).add(u.scale(0.12));
        Vec3 tailRoot = center.subtract(f.scale(0.75)).subtract(u.scale(0.05));
        List<Vec3> body = List.of(head, center.add(f.scale(0.45)).add(u.scale(0.05)), center, tailRoot);

        List<List<Vec3>> feathers = new ArrayList<>();
        List<Vec3> left = wing(center, f, u, r, -1, flap, sweep, feathers);
        List<Vec3> right = wing(center, f, u, r, 1, flap, sweep, feathers);

        List<List<Vec3>> tail = new ArrayList<>();
        for (int j = -1; j <= 1; j++) {
            List<Vec3> plume = new ArrayList<>();
            plume.add(tailRoot);
            for (int k = 1; k <= 4; k++) {
                double wave = Math.sin(tailPhase + k * 0.9 + j * 1.3) * 0.13 * k;
                plume.add(tailRoot.subtract(f.scale(0.62 * k)).add(r.scale(j * 0.2 * k)).add(u.scale(wave - 0.04 * k)));
            }
            tail.add(plume);
        }

        List<List<Vec3>> crest = new ArrayList<>();
        for (int side : new int[] {-1, 1})
            crest.add(List.of(head, head.subtract(f.scale(0.45)).add(u.scale(0.38)).add(r.scale(side * 0.09))));
        crest.add(List.of(head, head.add(f.scale(0.26)).subtract(u.scale(0.05))));
        return new Pose(body, left, right, feathers, tail, crest, head);
    }

    /** One wing's leading edge, shoulder to tip, plus the flight feathers trailing from it. */
    private static List<Vec3> wing(Vec3 center, Vec3 f, Vec3 u, Vec3 r, int side, double flap, double sweep, List<List<Vec3>> feathers) {
        Vec3 shoulder = center.add(u.scale(0.1)).add(r.scale(side * 0.18));
        Vec3 elbow = shoulder.add(r.scale(side * Math.cos(flap) * 1.05)).add(u.scale(Math.sin(flap) * 1.05))
                .subtract(f.scale(0.15 + 0.35 * sweep));
        double outer = flap * 1.35;
        Vec3 tip = elbow.add(r.scale(side * Math.cos(outer) * 1.25 * (1 - 0.35 * sweep))).add(u.scale(Math.sin(outer) * 1.25))
                .subtract(f.scale(0.55 + 0.7 * sweep));
        List<Vec3> edge = List.of(shoulder, shoulder.lerp(elbow, 0.5).add(u.scale(0.08)), elbow, elbow.lerp(tip, 0.5).add(u.scale(0.05)), tip);
        for (int k = 0; k < FEATHERS; k++) {
            double t = 0.12 + 0.88 * k / (FEATHERS - 1.0);
            Vec3 root = t < 0.5 ? shoulder.lerp(elbow, t * 2) : elbow.lerp(tip, (t - 0.5) * 2);
            Vec3 feather = root.subtract(f.scale((0.55 + 0.6 * Math.pow(t, 0.7)) * (1 + 0.3 * sweep)))
                    .subtract(u.scale(0.12 * t)).add(r.scale(side * 0.12 * t));
            feathers.add(List.of(root, feather));
        }
        return edge;
    }
}
