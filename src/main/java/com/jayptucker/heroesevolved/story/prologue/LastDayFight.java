package com.jayptucker.heroesevolved.story.prologue;

import com.jayptucker.heroesevolved.story.actor.StoryActorEntity.Pose;
import com.jayptucker.heroesevolved.time.intro.IntroBlast;
import com.jayptucker.heroesevolved.time.intro.SkyFight;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Peter Petrelli and Sylar on the Last Day, as one script the server and every client read, timed in ticks from the
 * light appearing. Peter streaks in over the city from the south and Sylar meets him over Kirby Plaza; four passes there;
 * Sylar grips Peter from across the sky and hurls him down the avenue, and the fight comes to the City Market, right over
 * the people at the beacon. Sylar tears two chunks out of the facades and throws them at Peter, who dodges both: each
 * smashes into the building across the street and brings stone and glass down on the sidewalk (on the people standing
 * there, {@link LastDayRescue}). Peter's lightning, a tackle, and he hurls Sylar back up the avenue into the dark tower by
 * the plaza; then he flies back and dives into the light beside Ted. A breath later the wave comes ({@link PrologueTimeline#WAVE}).
 * <p>
 * The client draws all of it from this class ({@code client/effects/intro/LastDayArt}), so it reads from the far end of
 * the avenue as well as from under it; the server does what it does to the street on the same ticks.
 */
public final class LastDayFight {
    /** Peter streaks in from the south; Sylar shows himself over the plaza; they meet: the fight and the music begin. */
    public static final int PETER_IN = 90, SYLAR_IN = 118, ACTION = 150;
    /** Four passes over the plaza, from ACTION. */
    public static final SkyFight.Script PLAZA = new SkyFight.Script(ACTION, 80, 4, new Vec3(8, 97, -78), 6.5, 4.55,
            new Vec3(-1, 85, -90), new Vec3(18, 108, -62), new Vec3(-3, 98, -84.5));
    /** Sylar's grip on Peter across the sky, and the hurl down the avenue to the market. */
    public static final int GRIP = 470, HURL = 480, HURLED = 528;
    /** Sylar arrives over the cross street; they fly at each other; the clash (Peter's lightning). */
    public static final int SYLAR_HERE = 548, CHARGE = 556, CLASH = 574, RECOILED = 594;
    /** The first chunk: torn from the west facade, flung at Peter, into the market's front. Then the second, the other way. */
    public static final int TEAR_1 = 600, FLING_1 = 614, RUBBLE_EAST = 620;
    public static final int TEAR_2 = 634, FLING_2 = 648, RUBBLE_WEST = 654;
    /** Peter sees each throw coming: his dodge starts a moment before it leaves Sylar's grip. */
    public static final int DODGE_LEAD = 3, DODGE_TICKS = 10;
    /** Peter's tackle, the throw, and Sylar into the tower by the plaza. */
    public static final int TACKLE = 672, GRAB = 690, THROW = 700, TOWER = 745;
    /** Peter turns back for Ted: up the avenue, the dive, and down beside him in the light. */
    public static final int RETURN = 760, DIVE = 830, LAND = 856;
    /** How long the stone and glass take to come down from a facade onto the sidewalk. */
    public static final int FALL = 9;

    /** Where Peter comes in from, high over the outskirts south of Primatech. */
    static final Vec3 ENTRY = new Vec3(4.5, 118, 150);
    /**
     * Over the avenue at the south edge of the plaza's open sky: every long flight between the plaza and the market goes
     * by way of the avenue's own open air (the road is open to the sky its whole length), never across the towers.
     */
    static final Vec3 PLAZA_GATE = new Vec3(4.5, 104, -62), SYLAR_GATE = new Vec3(3.5, 101, -62);
    /** Where the hurl leaves Peter (in front of the market's north end), and where Sylar comes to (over the cross street). */
    static final Vec3 THROWN = new Vec3(4.5, 79, 40), SYLAR_CLOSE = new Vec3(3.5, 88, 22);
    /** Where they meet, a step apart, and where each is knocked back to. */
    static final Vec3 MEET_PETER = new Vec3(4.3, 83, 31.6), MEET_SYLAR = new Vec3(3.8, 83.6, 30.2);
    static final Vec3 PETER_BACK = new Vec3(4.5, 77, 46), SYLAR_BACK = new Vec3(3.5, 90, 16);
    /** Peter's two dodges: up over the first chunk, then down and back from the second. */
    static final Vec3 DODGE_1 = new Vec3(4.5, 83, 48), DODGE_2 = new Vec3(4.5, 78, 41);
    /** High over the avenue on the way back, before the dive. */
    static final Vec3 OVER_TED = new Vec3(4.5, 96, -64);

    private LastDayFight() {}

    // ------------------------------------------------------------------
    // The facades: where the chunks come from and where they land
    // ------------------------------------------------------------------

    /**
     * The four facade points of the fight, found once when the light appears (before anything is broken) and sent to
     * every client with the light: where Sylar tears the first chunk from the west side, where it hits the market's front,
     * where he tears the second from above the market, and where that one hits the west side.
     */
    public record Facades(Vec3 grabWest, Vec3 hitEast, Vec3 grabEast, Vec3 hitWest) {
        /** The fallback when the city isn't there to look at (and the guess the game test checks against). */
        public static final Facades GUESS = new Facades(new Vec3(-3.3, 78.5, 47.5), new Vec3(10.7, 74.5, 47.5),
                new Vec3(10.7, 80.5, 55.5), new Vec3(-3.3, 72.5, 55.5));

        public Vec3 get(int i) { return switch (i) { case 0 -> grabWest; case 1 -> hitEast; case 2 -> grabEast; default -> hitWest; }; }

        /** As whole tenths of a block, for the light's cue. */
        public List<Integer> encode() {
            List<Integer> out = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                Vec3 v = get(i);
                out.add((int) Math.round(v.x * 10)); out.add((int) Math.round(v.y * 10)); out.add((int) Math.round(v.z * 10));
            }
            return out;
        }

        public static Facades decode(List<Integer> ints, int from) {
            if (ints.size() < from + 12) return GUESS;
            Vec3[] v = new Vec3[4];
            for (int i = 0; i < 4; i++)
                v[i] = new Vec3(ints.get(from + i * 3) / 10.0, ints.get(from + i * 3 + 1) / 10.0, ints.get(from + i * 3 + 2) / 10.0);
            return new Facades(v[0], v[1], v[2], v[3]);
        }
    }

    /** Looks across the avenue for each facade's real face, a little lower each try if a building is short there. */
    public static Facades find(Level level) {
        return new Facades(face(level, new Vec3(2, 78.5, 47.5), -1, Facades.GUESS.grabWest),
                face(level, new Vec3(6, 74.5, 47.5), 1, Facades.GUESS.hitEast),
                face(level, new Vec3(6, 80.5, 55.5), 1, Facades.GUESS.grabEast),
                face(level, new Vec3(2, 72.5, 55.5), -1, Facades.GUESS.hitWest));
    }

    private static Vec3 face(Level level, Vec3 from, int dx, Vec3 guess) {
        for (int drop = 0; drop <= 8; drop += 2) {
            Vec3 a = from.add(0, -drop, 0), b = a.add(dx * 14, 0, 0);
            BlockHitResult hit = level.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
            if (hit.getType() == HitResult.Type.BLOCK) return hit.getLocation().add(-dx * 0.3, 0, 0);
        }
        return guess;
    }

    // ------------------------------------------------------------------
    // Where they are
    // ------------------------------------------------------------------

    private static float ease(float x) { x = Mth.clamp(x, 0, 1); return x * x * (3 - 2 * x); }
    private static float easeOut(float x) { x = Mth.clamp(x, 0, 1); return 1 - (1 - x) * (1 - x) * (1 - x); }
    private static float easeIn(float x) { x = Mth.clamp(x, 0, 1); return x * x * x; }
    private static float span(float t, int from, int to) { return (t - from) / (float) (to - from); }
    private static Vec3 lerp(Vec3 a, Vec3 b, float k) { return a.add(b.subtract(a).scale(k)); }

    /** The point {@code k} of the way along a path through {@code points}, by distance, so the pace is even across its legs. */
    private static Vec3 along(float k, Vec3... points) {
        double total = 0;
        for (int i = 1; i < points.length; i++) total += points[i].distanceTo(points[i - 1]);
        double want = Mth.clamp(k, 0, 1) * total;
        for (int i = 1; i < points.length; i++) {
            double leg = points[i].distanceTo(points[i - 1]);
            if (want <= leg || i == points.length - 1) return lerp(points[i - 1], points[i], (float) Math.min(1, want / Math.max(leg, 1e-6)));
            want -= leg;
        }
        return points[points.length - 1];
    }

    static Vec3 peterAfterPlaza() { return SkyFight.peterStart(PLAZA, PLAZA.passes()); }
    static Vec3 sylarAfterPlaza() { return SkyFight.sylarStart(PLAZA, PLAZA.passes()); }

    /** Peter at {@code t}, or null before he arrives. */
    public static Vec3 peter(float t, Facades f) {
        if (t < PETER_IN) return null;
        if (t < ACTION) return along(ease(span(t, PETER_IN, ACTION)), ENTRY, PLAZA_GATE, SkyFight.peterStart(PLAZA, 0));
        if (t < GRIP) return SkyFight.at(PLAZA, t, PLAZA.impactGuess())[0];
        Vec3 p4 = peterAfterPlaza();
        if (t < HURL) return p4.add(0, Math.sin(t * 2.4) * 0.12, 0);
        if (t < HURLED) return along(easeOut(span(t, HURL, HURLED)), p4, PLAZA_GATE, THROWN);
        if (t < CHARGE) return THROWN.add(0, ease(span(t, HURLED, CHARGE)) * 1.2 + Math.sin(t * 0.2) * 0.1, 0);
        if (t < CLASH) return lerp(THROWN.add(0, 1.2, 0), MEET_PETER, ease(span(t, CHARGE, CLASH)));
        if (t < RECOILED) return lerp(MEET_PETER, PETER_BACK, easeOut(span(t, CLASH, RECOILED)));
        int d1 = FLING_1 - DODGE_LEAD, d2 = FLING_2 - DODGE_LEAD;
        if (t < d1) return PETER_BACK.add(0, Math.sin(t * 0.25) * 0.15, 0);
        if (t < d1 + DODGE_TICKS) return lerp(PETER_BACK, DODGE_1, easeOut(span(t, d1, d1 + DODGE_TICKS)));
        if (t < d2) return DODGE_1.add(0, Math.sin(t * 0.25) * 0.15, 0);
        if (t < d2 + DODGE_TICKS) return lerp(DODGE_1, DODGE_2, easeOut(span(t, d2, d2 + DODGE_TICKS)));
        if (t < TACKLE) return DODGE_2;
        if (t < GRAB) return lerp(DODGE_2, grabSpot(), ease(span(t, TACKLE, GRAB)));
        if (t < RETURN) return grabSpot().add(0, Math.sin(t * 0.15) * 0.15, 0);
        if (t < DIVE) return lerp(grabSpot(), OVER_TED, ease(span(t, RETURN, DIVE)));
        if (t < LAND) return lerp(OVER_TED, SkyFight.LANDING, easeIn(span(t, DIVE, LAND)));
        return SkyFight.LANDING;
    }

    /** Where Peter takes hold of Sylar for the throw: just south of him, a little below. */
    private static Vec3 grabSpot() { return SYLAR_BACK.add(0, -0.4, 1.0); }

    /** Sylar at {@code t}, or null before he shows himself and once he's in the tower. */
    public static Vec3 sylar(float t, Facades f) {
        if (t < SYLAR_IN || t >= TOWER) return null;
        if (t < GRIP) return t < ACTION ? SkyFight.sylarStart(PLAZA, 0) : SkyFight.at(PLAZA, t, PLAZA.impactGuess())[1];
        Vec3 s4 = sylarAfterPlaza();
        if (t < HURL + 10) return s4;
        if (t < SYLAR_HERE) return along(ease(span(t, HURL + 10, SYLAR_HERE)), s4, SYLAR_GATE, SYLAR_CLOSE);
        if (t < CHARGE) return SYLAR_CLOSE;
        if (t < CLASH) return lerp(SYLAR_CLOSE, MEET_SYLAR, ease(span(t, CHARGE, CLASH)));
        if (t < RECOILED) return lerp(MEET_SYLAR, SYLAR_BACK, easeOut(span(t, CLASH, RECOILED)));
        if (t < THROW) return SYLAR_BACK.add(0, Math.sin(t * 0.21) * 0.15, 0);
        // Thrown: slow off the hand, then faster and faster into the wall.
        return lerp(SYLAR_BACK, PLAZA.impactGuess(), easeIn(span(t, THROW, TOWER)) * 0.85F + span(t, THROW, TOWER) * 0.15F);
    }

    /** Ted, down on his knees in the middle of the avenue at ground zero. */
    public static final Vec3 TED = IntroBlast.CENTER;

    /** How Peter holds himself at {@code t}. */
    public static Pose peterPose(float t) {
        if (t < ACTION) return Pose.FLY;
        if (t < GRIP) return plaza(SkyFight.moves(PLAZA, t)[0]);
        if (t < HURL) return Pose.HOVER;
        if (t < HURLED) return Pose.TUMBLE;
        if (t < CHARGE) return Pose.HOVER;
        if (t < CLASH) return Pose.FLY;
        if (t < CLASH + 8) return Pose.STRIKE;
        if (t < FLING_1 - DODGE_LEAD) return Pose.HOVER;
        if (t < FLING_1 - DODGE_LEAD + 8) return Pose.FLY;
        if (t < FLING_2 - DODGE_LEAD) return Pose.HOVER;
        if (t < FLING_2 - DODGE_LEAD + 8) return Pose.FLY;
        if (t < TACKLE) return t > FLING_2 + 14 && t < FLING_2 + 22 ? Pose.STRIKE : Pose.HOVER;
        if (t < GRAB) return Pose.FLY;
        if (t < THROW + 6) return Pose.REACH;
        if (t < RETURN) return Pose.HOVER;
        if (t < LAND) return Pose.FLY;
        return Pose.REACH;
    }

    /** How Sylar holds himself at {@code t}. */
    public static Pose sylarPose(float t) {
        if (t < GRIP) return t < ACTION ? Pose.HOVER : plaza(SkyFight.moves(PLAZA, t)[1]);
        if (t < HURL + 6) return Pose.STRIKE;
        if (t < SYLAR_HERE) return Pose.FLY;
        if (t < CHARGE) return Pose.HOVER;
        if (t < CLASH) return Pose.FLY;
        if (t < CLASH + 12) return Pose.TUMBLE;
        if (t >= TEAR_1 - 4 && t < FLING_1 + 4 || t >= TEAR_2 - 4 && t < FLING_2 + 4) return Pose.STRIKE;
        if (t < GRAB) return Pose.HOVER;
        if (t < THROW) return Pose.HOVER;
        return Pose.TUMBLE;
    }

    private static Pose plaza(int move) {
        return switch (move) { case 1 -> Pose.FLY; case 2 -> Pose.TUMBLE; case 3 -> Pose.STRIKE; case 4 -> Pose.REACH; default -> Pose.HOVER; };
    }

    /** What each is looking at, when hovering: the other, or Ted once Peter has come down. */
    public static Vec3 peterLook(float t, Facades f) {
        if (t >= LAND) return TED.add(0, 1.2, 0);
        Vec3 s = sylar(t, f);
        return s != null ? s : TED.add(0, 1, 0);
    }

    // ------------------------------------------------------------------
    // The two chunks
    // ------------------------------------------------------------------

    /** Chunk {@code which} (0 the first, 1 the second) at {@code t}, or null while it's still in the wall or once it has hit. */
    public static Vec3 chunk(int which, float t, Facades f) {
        int tear = which == 0 ? TEAR_1 : TEAR_2, fling = which == 0 ? FLING_1 : FLING_2, hit = which == 0 ? RUBBLE_EAST : RUBBLE_WEST;
        if (t < tear || t >= hit) return null;
        Vec3 from = which == 0 ? f.grabWest() : f.grabEast(), to = which == 0 ? f.hitEast() : f.hitWest();
        Vec3 out = pulled(which, f);
        if (t < fling) return lerp(from, out, easeOut(span(t, tear, fling)));
        // Flung straight through where Peter was hovering, and on into the building across the street.
        Vec3 aim = which == 0 ? PETER_BACK.add(0, 0.9, 0) : DODGE_1.add(0, 0.9, 0);
        double first = out.distanceTo(aim), second = aim.distanceTo(to), d = span(t, fling, hit) * (first + second);
        return d < first ? lerp(out, aim, (float) (d / first)) : lerp(aim, to, (float) ((d - first) / Math.max(second, 1e-3)));
    }

    /** Where a chunk is held, torn out and turning in Sylar's grip, before he throws it. */
    static Vec3 pulled(int which, Facades f) {
        Vec3 from = which == 0 ? f.grabWest() : f.grabEast();
        return from.add(which == 0 ? 2.6 : -2.6, 1.4, 0);
    }

    /** Where a chunk hits, the sidewalk below it (x on the sidewalk, z at the hit) and the side it's on. */
    public static Vec3 impact(int which, Facades f) { return which == 0 ? f.hitEast() : f.hitWest(); }

    // ------------------------------------------------------------------
    // The people on the sidewalks
    // ------------------------------------------------------------------

    /**
     * Where the people the rubble catches stand, {@code count} of them, alternating sides of the avenue: under the first
     * chunk's hit on the market's sidewalk, then under the second's across the street, then a few steps either side of each.
     */
    public static List<Vec3> victims(Facades f, int count) {
        List<Vec3> out = new ArrayList<>();
        double[] along = {0, -4, 4};
        for (int i = 0; i < count; i++) {
            boolean east = i % 2 == 0;
            Vec3 hit = east ? f.hitEast() : f.hitWest();
            double z = Math.floor(hit.z) + 0.5 + along[Math.min(i / 2, along.length - 1)];
            out.add(new Vec3(east ? 9.5 : -1.5, com.jayptucker.heroesevolved.time.intro.city.CityGrid.STREET_Y + 1, z));
        }
        return out;
    }

    /** Which chunk's rubble comes down on victim {@code i}: the first (east) or the second (west). */
    public static int chunkFor(int i) { return i % 2; }

    /** The tick victim {@code i}'s rubble lands on them. */
    public static int pinnedAt(int i) { return (chunkFor(i) == 0 ? RUBBLE_EAST : RUBBLE_WEST) + FALL; }

    // ------------------------------------------------------------------
    // The beats, for the drawing and the sound
    // ------------------------------------------------------------------

    /** Every moment something happens that the client draws or plays once. */
    public enum Beat {
        ENTRY_BOOM(PETER_IN + 34), SYLAR_SHOWS(SYLAR_IN), MEET(ACTION),
        PASS_0(-1), PASS_1(-1), PASS_2(-1), PASS_3(-1),
        GRIPPED(GRIP), HURLED_DOWN(HURL), ARRIVES(SYLAR_HERE), BOLT(CLASH),
        TORN_1(TEAR_1), FLUNG_1(FLING_1), HIT_1(RUBBLE_EAST), TORN_2(TEAR_2), FLUNG_2(FLING_2), HIT_2(RUBBLE_WEST),
        TACKLED(GRAB), THROWN_NORTH(THROW), INTO_TOWER(TOWER), LANDS(LAND);

        private final int tick;
        Beat(int tick) { this.tick = tick; }

        public int tick() {
            return switch (this) {
                case PASS_0 -> PLAZA.clashAt(0);
                case PASS_1 -> PLAZA.clashAt(1);
                case PASS_2 -> PLAZA.clashAt(2);
                case PASS_3 -> PLAZA.clashAt(3);
                default -> tick;
            };
        }

        /** Where it happens. */
        public Vec3 where(Facades f) {
            return switch (this) {
                case ENTRY_BOOM -> peter(tick(), f);
                case SYLAR_SHOWS -> SkyFight.sylarStart(PLAZA, 0);
                case MEET, PASS_0, PASS_1, PASS_2, PASS_3 -> {
                    Vec3 p = peter(tick(), f), s = sylar(tick(), f);
                    yield s == null ? p : p.add(s).scale(0.5);
                }
                case GRIPPED -> peterAfterPlaza();
                case HURLED_DOWN -> peterAfterPlaza();
                case ARRIVES -> SYLAR_CLOSE;
                case BOLT -> MEET_PETER.add(MEET_SYLAR).scale(0.5);
                case TORN_1 -> f.grabWest();
                case FLUNG_1 -> pulled(0, f);
                case HIT_1 -> f.hitEast();
                case TORN_2 -> f.grabEast();
                case FLUNG_2 -> pulled(1, f);
                case HIT_2 -> f.hitWest();
                case TACKLED, THROWN_NORTH -> SYLAR_BACK;
                case INTO_TOWER -> PLAZA.impactGuess();
                case LANDS -> SkyFight.LANDING;
            };
        }

        /** Whether Peter came off better in this plaza pass. */
        public boolean peterWins() {
            return switch (this) { case PASS_0 -> SkyFight.peterWins(0); case PASS_1 -> SkyFight.peterWins(1);
                case PASS_2 -> SkyFight.peterWins(2); case PASS_3 -> SkyFight.peterWins(3); default -> true; };
        }
    }
}
