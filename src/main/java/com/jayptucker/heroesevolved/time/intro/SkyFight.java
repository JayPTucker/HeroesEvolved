package com.jayptucker.heroesevolved.time.intro;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Peter Petrelli and Sylar fighting in the sky near Kirby Plaza while Ted Sprague burns below: passes that meet in a
 * clash and fly apart, then Peter catches Sylar and hurls him into a tower, and dives to Ted.
 * <p>
 * One path for every viewer. The Time vision moves the real actors along it from the vantage (TimeIntroService); the
 * Last Day draws it on each client from far down the avenue ({@code client/effects/intro/SkyFightArt}), timed from the
 * light appearing, so it works however far the server sends entities. Ticks are counted from the scene's own start.
 */
public final class SkyFight {
    /** How far the loser of a pass is thrown back (then kept inside the script's open sky). */
    static final double RECOIL = 6;
    /** Where Peter comes down, beside Ted. */
    public static final Vec3 LANDING = IntroBlast.CENTER.add(0, 0, 1.1);

    /** How long Peter takes to reach Sylar for the throw, the wind-up, Sylar's flight into the wall, the pause, the dive. */
    public static final int GRAB = 20, WIND = 6, FLING = 10, PAUSE = 24, DIVE = 26;

    /**
     * When the fight starts, how long one pass lasts, how many passes before the throw, and where: the point the passes
     * turn about, how far they range across (x) and along (z) the avenue, the open sky they must keep to ({@code lo} to
     * {@code hi}), and the wall Sylar is thrown into (as drawn far off; the vision finds the real face by a raycast). Each
     * pass: they fly to meet (to 38%), clash and hold (to 50%), the loser is thrown back (to 80%), and both wheel round.
     */
    public record Script(int start, int pass, int passes, Vec3 centre, double rx, double rz, Vec3 lo, Vec3 hi, Vec3 impactGuess) {
        public int throwAt() { return start + pass * passes; }
        public int impactAt() { return throwAt() + GRAB + WIND + FLING; }
        public int diveAt() { return impactAt() + PAUSE; }
        public int landAt() { return diveAt() + DIVE; }
        /** The tick pass {@code k}'s clash lands. */
        public int clashAt(int k) { return start + k * pass + Math.round(pass * 0.38F); }

        Vec3 inside(Vec3 v) {
            return new Vec3(Mth.clamp(v.x, lo.x, hi.x), Mth.clamp(v.y, lo.y, hi.y), Mth.clamp(v.z, lo.z, hi.z));
        }
    }

    /**
     * The Time vision: four quick passes from just after arriving, close to the vantage. They range down the avenue
     * between the two towers just north of it (open sky from x -2 to 10 below y 110) and over the cross street beyond, and
     * Sylar goes into the face of the west tower of that pair, about thirty blocks from where the visitors stand.
     */
    public static final Script VISION = new Script(10, 44, 4, new Vec3(4, 90, -58), 3.5, 11,
            new Vec3(-1, 82, -74), new Vec3(9, 100, -44), new Vec3(-5.5, 92, -45));
    /**
     * The Last Day, seen from far off: a long fight from just after the light appears, while everyone runs. It turns over
     * the plaza's west side (open sky above y 72 from x -1 to 30, z -92 to -60), within the sky that can be seen between
     * the two towers south of the plaza from far down the avenue (x -6 to 14), and Sylar goes into the face of the tower
     * west of the plaza.
     */
    public static final Script LAST_DAY = new Script(120, 80, 12, new Vec3(8, 97, -78), 6.5, 4.55,
            new Vec3(-1, 85, -90), new Vec3(18, 108, -62), new Vec3(-3, 98, -84.5));

    private SkyFight() {}

    /** Who comes off better in pass {@code k}: Peter in the even passes, Sylar in the odd ones. */
    public static boolean peterWins(int k) { return k % 2 == 0; }

    private static double angle(int k) { return k * 2.4 + 0.6; }

    /** Where Peter starts pass {@code k} (and ends pass {@code k - 1}). */
    static Vec3 peterStart(Script s, int k) {
        double a = angle(k);
        return s.inside(s.centre().add(Math.cos(a) * s.rx(), Math.sin(k * 1.7) * 3, Math.sin(a) * s.rz()));
    }

    /** Where Sylar starts pass {@code k}: across from Peter, a little higher. */
    static Vec3 sylarStart(Script s, int k) {
        double a = angle(k);
        return s.inside(s.centre().add(-Math.cos(a) * s.rx(), 2 - Math.sin(k * 1.7) * 1.8, -Math.sin(a) * s.rz()));
    }

    static Vec3 meet(Script s, int k) {
        double a = angle(k) + 1.2;
        return s.inside(s.centre().add(Math.cos(a) * Math.min(2.5, s.rx()), Math.sin(k) * 2, Math.sin(a) * 2));
    }

    private static float ease(float x) { x = Mth.clamp(x, 0, 1); return x * x * (3 - 2 * x); }
    private static float easeOut(float x) { x = Mth.clamp(x, 0, 1); return 1 - (1 - x) * (1 - x) * (1 - x); }
    private static float easeIn(float x) { x = Mth.clamp(x, 0, 1); return x * x * x; }
    private static Vec3 lerp(Vec3 a, Vec3 b, float t) { return a.add(b.subtract(a).scale(t)); }

    /** The two of them at {@code t}: [0] Peter, [1] Sylar (null once he is in the wall). */
    public static Vec3[] at(Script s, float t, Vec3 impact) {
        float rel = t - s.start();
        if (rel < 0) return new Vec3[] {peterStart(s, 0), sylarStart(s, 0)};
        int k = (int) (rel / s.pass());
        if (k < s.passes()) return pass(s, k, (rel - k * s.pass()) / s.pass(), t);
        // The throw: Peter reaches Sylar, winds up, and hurls him into the tower; then he dives to Ted.
        Vec3 p0 = peterStart(s, s.passes()), q = sylarStart(s, s.passes());
        Vec3 grab = q.add(p0.subtract(q).normalize().scale(0.9));
        float r = t - s.throwAt();
        Vec3 peter, sylar;
        if (r < GRAB) { peter = lerp(p0, grab, ease(r / GRAB)); sylar = q; }
        else if (r < GRAB + WIND) { peter = grab; sylar = q.add(0, Math.sin((r - GRAB) * 1.6) * 0.15, 0); }
        else if (r < GRAB + WIND + FLING) { peter = grab; sylar = lerp(q, impact, easeIn((r - GRAB - WIND) / FLING) * 0.85F + (r - GRAB - WIND) / FLING * 0.15F); }
        else { sylar = null; peter = t < s.diveAt() ? grab.add(0, Math.sin(t * 0.15) * 0.15, 0) : lerp(grab, LANDING, easeIn((t - s.diveAt()) / DIVE)); }
        return new Vec3[] {peter, sylar};
    }

    private static Vec3[] pass(Script s, int k, float u, float t) {
        Vec3 p = peterStart(s, k), q = sylarStart(s, k), m = meet(s, k);
        Vec3 axis = q.subtract(p).normalize(), up = new Vec3(0, 1, 0);
        Vec3 pm = m.subtract(axis.scale(0.7)), qm = m.add(axis.scale(0.7));
        boolean peterWins = peterWins(k);
        Vec3 pc = s.inside(peterWins ? pm.subtract(axis.scale(1.5)) : pm.subtract(axis.scale(RECOIL)).subtract(up));
        Vec3 qc = s.inside(peterWins ? qm.add(axis.scale(RECOIL)).add(up.scale(2)) : qm.add(axis.scale(1.5)));
        if (u < 0.38F) { float e = ease(u / 0.38F); return new Vec3[] {lerp(p, pm, e), lerp(q, qm, e)}; }
        if (u < 0.5F) {
            double j = Math.sin(t * 2.3) * 0.12;
            return new Vec3[] {pm.add(0, j, 0), qm.add(0, -j, 0)};
        }
        if (u < 0.8F) { float e = easeOut((u - 0.5F) / 0.3F); return new Vec3[] {lerp(pm, pc, e), lerp(qm, qc, e)}; }
        float e = ease((u - 0.8F) / 0.2F);
        return new Vec3[] {lerp(pc, peterStart(s, k + 1), e), lerp(qc, sylarStart(s, k + 1), e)};
    }

    /** What each of them is doing at {@code t}, for their pose: 0 hovering, 1 flying, 2 thrown, 3 striking, 4 holding. */
    public static int[] moves(Script s, float t) {
        float rel = t - s.start();
        if (rel < 0) return new int[] {0, 0};
        int k = (int) (rel / s.pass());
        if (k < s.passes()) {
            float u = (rel - k * s.pass()) / s.pass();
            boolean pw = peterWins(k);
            if (u < 0.38F) return new int[] {1, 1};
            if (u < 0.5F) return pw ? new int[] {3, 0} : new int[] {0, 3};
            if (u < 0.65F) return pw ? new int[] {0, 2} : new int[] {2, 0};
            return new int[] {1, 1};
        }
        float r = t - s.throwAt();
        if (r < GRAB) return new int[] {1, 0};
        if (r < GRAB + WIND) return new int[] {4, 2};
        if (r < GRAB + WIND + FLING) return new int[] {3, 2};
        return new int[] {t < s.diveAt() ? 0 : 1, 2};
    }
}
