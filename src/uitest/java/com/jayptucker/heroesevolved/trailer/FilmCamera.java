package com.jayptucker.heroesevolved.trailer;

import com.jayptucker.heroesevolved.client.effects.TempestCamera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The trailer's moving camera: a path of keys (where it is, what it looks at, its field of view and roll) flown on the
 * game's own clock, so slow motion slows the camera too, like a real high-speed camera. Each frame it places the view
 * through {@link TempestCamera#fix}, the one way the mod moves a camera without changing the camera's entity.
 *
 * <p>The clock counts client ticks plus the frame's partial tick ({@link #now}); the recorder reads the same clock, so a
 * camera move and the frames that film it can never drift apart.</p>
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class FilmCamera {
    /** One key: at fraction {@code t} of the move the camera is {@code at}, looking at {@code look}. */
    public record Key(double t, Vec3 at, Vec3 look, double fov, double roll) {}

    /** How a move is spread over its length. */
    public enum Ease { NONE, IN, OUT, BOTH }

    /** A camera move: keys spread over {@code ticks} of game time. */
    public static final class Move {
        final List<Key> keys;
        final double ticks;
        final Ease ease;

        private Move(List<Key> keys, double ticks, Ease ease) {
            this.keys = List.copyOf(keys);
            this.ticks = ticks;
            this.ease = ease;
        }

        public static Move of(double ticks, Ease ease, Key... keys) { return new Move(List.of(keys), ticks, ease); }

        /**
         * An arc round {@code centre}: the angle (degrees from +X towards +Z, so 90 is due south of the centre) runs from
         * {@code a0} to {@code a1}, the radius, the height over the centre and the field of view ease between their two
         * values, and the camera keeps looking at {@code look}.
         */
        public static Move orbit(double ticks, Ease ease, Vec3 centre, Vec3 look, double a0, double a1,
                                 double r0, double r1, double h0, double h1, double fov0, double fov1, double roll) {
            List<Key> keys = new ArrayList<>();
            int steps = Math.max(4, (int) Math.ceil(Math.abs(a1 - a0) / 6));
            for (int i = 0; i <= steps; i++) {
                double t = i / (double) steps;
                double a = Math.toRadians(Mth.lerp(t, a0, a1)), r = Mth.lerp(t, r0, r1);
                Vec3 at = centre.add(Math.cos(a) * r, Mth.lerp(t, h0, h1), Math.sin(a) * r);
                keys.add(new Key(t, at, look, Mth.lerp(t, fov0, fov1), roll));
            }
            return new Move(keys, ticks, ease);
        }

        /** Where the camera is at fraction {@code u} of the move (already eased). */
        Key sample(double u) {
            u = Mth.clamp(u, 0, 1);
            int n = keys.size();
            if (n == 1) return keys.getFirst();
            int i = 0;
            while (i < n - 2 && u > keys.get(i + 1).t) i++;
            Key k1 = keys.get(i), k2 = keys.get(i + 1);
            Key k0 = i > 0 ? keys.get(i - 1) : k1, k3 = i + 2 < n ? keys.get(i + 2) : k2;
            double s = k2.t <= k1.t ? 1 : (u - k1.t) / (k2.t - k1.t);
            return new Key(u, spline(k0.at, k1.at, k2.at, k3.at, s), spline(k0.look, k1.look, k2.look, k3.look, s),
                    Mth.lerp(s, k1.fov, k2.fov), Mth.lerp(s, k1.roll, k2.roll));
        }

        double ease(double u) {
            u = Mth.clamp(u, 0, 1);
            return switch (ease) {
                case NONE -> u;
                case IN -> u * u;
                case OUT -> 1 - (1 - u) * (1 - u);
                case BOTH -> u * u * u * (u * (u * 6 - 15) + 10);
            };
        }
    }

    /** Catmull-Rom through p1 and p2, so a path passes through every key without corners. */
    private static Vec3 spline(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double s) {
        double s2 = s * s, s3 = s2 * s;
        return p1.scale(2).add(p2.subtract(p0).scale(s)).add(p0.scale(2).subtract(p1.scale(5)).add(p2.scale(4)).subtract(p3).scale(s2))
                .add(p3.subtract(p0).add(p1.scale(3)).subtract(p2.scale(3)).scale(s3)).scale(.5);
    }

    private static long ticks;
    private static Move move;
    private static double start, shake, shakeFrom, shakeUntil;
    private static Key current;
    /**
     * What the move follows (a fighter who dashes and flips) and where it was when the move began. The aim keeps up
     * quickly ({@code aimRate}) so the subject stays framed; the camera itself trails ({@code followRate}), like an
     * operator panning before they walk, and never comes closer than {@code nearest}.
     */
    private static Supplier<Vec3> follow;
    private static double followRate, aimRate = .45, nearest;
    private static Vec3 anchor, anchorOld, aimAnchor, aimAnchorOld, anchorStart;

    private FilmCamera() {}

    /** The film clock: client ticks run so far plus this frame's partial tick. */
    public static double now(float partial) { return ticks + partial; }

    /** The film clock for the frame being drawn. */
    public static double now() {
        return now(Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true));
    }

    /** Flies {@code next} from now. The camera holds the move's last key once it ends, until {@link #stop}. */
    public static void play(Move next) {
        play(next, null, 0);
    }

    /**
     * Flies {@code next} carried along with {@code target}: the move shifts as the target moves from where it was now,
     * the camera eased by {@code rate} a tick (0.15 is an operator keeping up a step behind) and its aim three times
     * quicker, both half as fast up and down so a jump doesn't bounce the frame. It never comes within {@code near}
     * blocks of what it looks at.
     */
    public static void play(Move next, Supplier<Vec3> target, double rate, double near) {
        move = next;
        start = ticks;
        follow = target;
        followRate = rate;
        aimRate = Math.min(.6, rate * 3);
        nearest = near;
        anchor = anchorOld = aimAnchor = aimAnchorOld = anchorStart = target == null ? null : target.get();
    }

    public static void play(Move next, Supplier<Vec3> target, double rate) { play(next, target, rate, 0); }

    private static Vec3 ease(Vec3 from, Vec3 to, double rate, double upRate) {
        Vec3 d = to.subtract(from);
        return from.add(d.x * rate, d.y * upRate, d.z * rate);
    }

    /**
     * The camera stays this far over the ground under it, and rises until it can see the subject's feet: a follow once
     * drifted down a terrace and filmed the fighter's legs hidden behind its edge. Both ease in over a few ticks.
     */
    private static final double CLEARANCE = .9;
    private static double lift, liftOld;
    private static Vec3 raw;
    /** Extra field of view for a big move (a flip), easing out and back over its length. */
    private static double widen, widenFrom, widenUntil;

    /** The highest ground round {@code at} (a little ahead too, so the camera rises before it reaches a step). */
    private static double ground(Minecraft mc, Vec3 at) {
        int top = Integer.MIN_VALUE;
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++)
                top = Math.max(top, mc.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        Mth.floor(at.x) + dx, Mth.floor(at.z) + dz));
        return top;
    }

    /** A little hand-held shake for {@code length} ticks, {@code amount} blocks at its start, fading out. */
    public static void shake(double amount, double length) {
        shake = amount;
        shakeFrom = ticks;
        shakeUntil = ticks + length;
    }

    /** Gives the view back. */
    /** Widens the view by {@code degrees} and back over {@code length} ticks, for a move that leaves the frame. */
    public static void widen(double degrees, double length) {
        widen = degrees;
        widenFrom = ticks;
        widenUntil = ticks + length;
    }

    private static boolean blocked(Minecraft mc, Vec3 from, Vec3 to) {
        return mc.level.clip(new net.minecraft.world.level.ClipContext(from, to, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player)).getType() == net.minecraft.world.phys.HitResult.Type.BLOCK;
    }

    public static void stop() {
        move = null;
        current = null;
        follow = null;
        raw = null;
        lift = liftOld = 0;
        widenUntil = 0;
        TempestCamera.fix(null, null);
    }

    public static boolean flying() { return move != null; }

    @SubscribeEvent public static void tick(ClientTickEvent.Pre event) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        ticks++;
        if (follow != null && anchor != null) {
            anchorOld = anchor;
            aimAnchorOld = aimAnchor;
            Vec3 goal = follow.get();
            if (goal != null) {
                // The camera rises and falls with a jump at half speed (no bounce); the aim keeps up fully (a flip
                // left the frame when it lagged).
                anchor = ease(anchor, goal, followRate, followRate * .5);
                aimAnchor = ease(aimAnchor, goal, aimRate, aimRate);
            }
        }
        liftOld = lift;
        if (raw != null) {
            double y = Math.max(raw.y, ground(mc, raw) + CLEARANCE);
            Vec3 feet = follow == null ? null : follow.get();
            if (feet != null) {
                feet = feet.add(0, .3, 0);
                for (int i = 0; i < 16 && blocked(mc, new Vec3(raw.x, y, raw.z), feet); i++) y += .25;
            }
            lift += (y - raw.y - lift) * .35;
        }
    }

    @SubscribeEvent public static void frame(RenderFrameEvent.Pre event) {
        if (move == null) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        double clock = now(partial);
        Key key = move.sample(move.ease((clock - start) / move.ticks));
        if (anchor != null) {
            Vec3 at = key.at().add(anchorOld.lerp(anchor, partial).subtract(anchorStart));
            Vec3 look = key.look().add(aimAnchorOld.lerp(aimAnchor, partial).subtract(anchorStart));
            Vec3 flat = new Vec3(at.x - look.x, 0, at.z - look.z);
            if (nearest > 0 && flat.length() < nearest && flat.length() > 1e-3)
                at = new Vec3(look.x, at.y, look.z).add(flat.normalize().scale(nearest));
            key = new Key(key.t(), at, look, key.fov(), key.roll());
        }
        raw = key.at();
        Vec3 at = raw.add(0, Mth.lerp(partial, liftOld, lift), 0);
        if (clock < widenUntil) {
            double u = (clock - widenFrom) / (widenUntil - widenFrom);
            key = new Key(key.t(), key.at(), key.look(), key.fov() + widen * Math.sin(Math.PI * Mth.clamp(u, 0, 1)), key.roll());
        }
        if (clock < shakeUntil && shake > 0) {
            double left = (shakeUntil - clock) / Math.max(1, shakeUntil - shakeFrom), a = shake * left * left;
            at = at.add(a * Math.sin(clock * 7.3), a * Math.sin(clock * 9.1 + 1), a * Math.sin(clock * 6.7 + 2));
        }
        current = new Key(key.t(), at, key.look(), key.fov(), key.roll());
        TempestCamera.fix(at, key.look());
    }

    @SubscribeEvent public static void fov(ViewportEvent.ComputeFov event) {
        if (current != null && event.usedConfiguredFov()) event.setFOV(current.fov());
    }

    @SubscribeEvent public static void angles(ViewportEvent.ComputeCameraAngles event) {
        if (current != null) event.setRoll((float) current.roll());
    }
}
