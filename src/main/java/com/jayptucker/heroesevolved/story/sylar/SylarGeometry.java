package com.jayptucker.heroesevolved.story.sylar;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Every position, reach and timing of Sylar's fight in the Collection, read by the server (where things land) and by
 * {@code SylarArt} (where they are drawn), so a telegraph assembles exactly as its move lands and a case glows exactly
 * where its glass stands.
 *
 * <p>The Collection is a round hall in a pocket dimension, rebuilt from the {@code the_collection} blueprint before every
 * fight. Its floor is a dial plate {@link #FLOOR_R} blocks in radius round {@link #ORIGIN}; twelve glass cases stand at
 * the hours on a ring {@link #CASE_R} out; in the last phase four pillars rise on the diagonals. Angles are a clock's:
 * twelve toward -z (north), the hours clockwise seen from above, so three o'clock is +x.</p>
 *
 * <p>The blueprint's {@code @case:N}, {@code @cover:N}, {@code @spawn:*} markers are drafted to these numbers
 * ({@code tools/draft-the-collection.py}), and a game test compares them.</p>
 */
public final class SylarGeometry {
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "the_collection"));
    /** The floor block under the hall's middle. Well away from x 0, and on a chunk corner's far side. */
    public static final BlockPos ORIGIN = new BlockPos(256, 64, 256);
    /** The blueprint's anchor cell (the floor block at the middle) and its size. */
    public static final int ANCHOR_X = 24, ANCHOR_Y = 1, ANCHOR_Z = 24, SIZE = 49, HEIGHT = 26;

    /** The walkable dial plate's radius; the dais in the middle; the case ring; the pillars on the diagonals. */
    public static final double FLOOR_R = 21, DAIS_R = 4, CASE_R = 16, COVER_R = 10;
    /** Pillars are three blocks square and this tall. */
    public static final int COVER_HEIGHT = 7;
    /** Anyone within this of the ticket's holder comes into the Collection with them. */
    public static final double PARTY = 16;

    // ------------------------------------------------------------------
    // The collection: twelve cases, one per hour
    // ------------------------------------------------------------------

    /** What each hour's case holds (null: kept empty, for what he takes from you). Twelve o'clock is index 0. */
    public static final List<ResourceLocation> CASES = java.util.Arrays.asList(
            ModAbilities.REGENERATION_ID,          // 12: Claire Bennet's, if he reached Pod 14 on Night Three
            ModAbilities.ELECTROKINESIS_ID,        // 1
            ModAbilities.CRYOKINESIS_ID,           // 2
            null,                                  // 3: kept for you
            ModAbilities.PYROKINESIS_ID,           // 4
            ModAbilities.QUAKE_ID,                 // 5
            ModAbilities.RADIATION_ID,             // 6: Ted Sprague's, if he saw the light at 6:14
            null,                                  // 7: kept for you
            ModAbilities.SONIC_RESONANCE_ID,       // 8
            ModAbilities.SUPERSPEED_ID,            // 9
            ModAbilities.TIME_MANIPULATION_ID,     // 10
            null);                                 // 11: kept for you
    public static final int REGENERATION_CASE = 0, RADIATION_CASE = 6;
    /** The empty cases, in the order he fills them. */
    public static final int[] EMPTY_CASES = {3, 7, 11};

    /** The case at {@code hour} (0 to 11): which case holds {@code power}, or -1. */
    public static int caseOf(ResourceLocation power) { return CASES.indexOf(power); }

    /** The clock angle of hour {@code h}: 0 at twelve, clockwise. */
    public static double angle(int hour) { return hour * Math.PI / 6; }

    /** The x and z offset from the middle of a point at clock angle {@code g} and radius {@code r}. */
    public static double dx(double g, double r) { return Math.sin(g) * r; }
    public static double dz(double g, double r) { return -Math.cos(g) * r; }

    /** The clock angle of an offset from the middle (0 to 2 pi). */
    public static double angleOf(double dx, double dz) {
        double g = Math.atan2(dx, -dz);
        return g < 0 ? g + 2 * Math.PI : g;
    }

    /** Which hour's wedge an offset from the middle stands in (0 to 11; each wedge is centred on its hour). */
    public static int hourOf(double dx, double dz) {
        return (int) Math.floor((angleOf(dx, dz) + Math.PI / 12) / (Math.PI / 6)) % 12;
    }

    /** The lower glass block of hour {@code h}'s case. Its plinth is under it, a second glass block and a cap over it. */
    public static BlockPos caseGlass(int hour) {
        double g = angle(hour);
        return ORIGIN.offset((int) Math.round(dx(g, CASE_R)), 2, (int) Math.round(dz(g, CASE_R)));
    }

    /** Where the specimen floats in hour {@code h}'s case. */
    public static Vec3 specimen(int hour) { return Vec3.atCenterOf(caseGlass(hour)).add(0, 0.5, 0); }

    // ------------------------------------------------------------------
    // The room
    // ------------------------------------------------------------------

    /** The middle of the dais, at standing height. */
    public static Vec3 center() { return Vec3.atBottomCenterOf(ORIGIN.above()); }

    /** Standing height on the dial plate. */
    public static double floorY() { return ORIGIN.getY() + 1; }

    /** A blueprint cell's world position. */
    public static BlockPos cell(int x, int y, int z) { return ORIGIN.offset(x - ANCHOR_X, y - ANCHOR_Y, z - ANCHOR_Z); }

    /** The middle column of pillar {@code i} (0 to 3: half past one, half past four, half past seven, half past ten). */
    public static BlockPos cover(int i) {
        double g = (1.5 + 3 * i) * Math.PI / 6;
        return ORIGIN.offset((int) Math.round(dx(g, COVER_R)), 1, (int) Math.round(dz(g, COVER_R)));
    }

    /** Inside the dial plate (the hall, not its walls). */
    public static boolean onFloor(Vec3 at) {
        Vec3 c = center();
        return Math.hypot(at.x - c.x, at.z - c.z) <= FLOOR_R + 0.5;
    }

    /** Where the ticket's party arrives: south of the dais, facing him. */
    public static Vec3 arrival(int i) {
        double side = (i % 2 == 0 ? 1 : -1) * ((i + 1) / 2) * 1.6;
        return center().add(side, 0, 11);
    }

    // ------------------------------------------------------------------
    // Moves: reaches, damage and timings (each windup is its telegraph piece's length)
    // ------------------------------------------------------------------

    /** Hurl: a slab of floor torn out beside him lands where you stood. */
    public static final double HURL_RADIUS = 2.5;
    public static final int HURL_WINDUP = Piece.SYLAR_HURL_MARK.ticks, HURL_LIFT = 14;
    /** Reel In: whoever is this far above the floor, this far away, or out of his sight this long, is pulled to him. */
    public static final double REEL_HEIGHT = 3, REEL_FAR = 20;
    public static final int REEL_UNSEEN = 80, REEL_WINDUP = Piece.SYLAR_REEL_MARK.ticks, REEL_PULL = 6;
    /** Arc Lattice: lightning strung between two cases; Icefall: a ring under each of you; Fault Line: a crack through you. */
    public static final double LATTICE_REACH = 1.5, ICE_RADIUS = 2.2, FAULT_REACH = 1.5;
    public static final int LATTICE_WINDUP = Piece.SYLAR_LATTICE_MARK.ticks, ICE_WINDUP = Piece.SYLAR_ICE_MARK.ticks,
            FAULT_WINDUP = Piece.SYLAR_FAULT_MARK.ticks;
    /** The Second Hand: a hand of fire from the dais's edge to the wall, sweeping a quarter turn. Jump it or outrun it. */
    public static final double HAND_INNER = DAIS_R, HAND_REACH = 0.9, HAND_SWEEP = Math.PI / 2;
    public static final int HAND_WINDUP = Piece.SYLAR_HAND_MARK.ticks, HAND_TICKS = Piece.SYLAR_HAND.ticks;
    /** Feet this far over the floor clear a low wave (a jump does). */
    public static final double JUMP_CLEAR = 0.6;
    /** Resonance: a ring of sound rolling out from his feet. */
    public static final double RING_START = 2, RING_SPEED = 0.45;
    public static final int RING_WINDUP = Piece.SYLAR_RING_MARK.ticks;
    /** Blur: three dashes, each this long in ticks, hitting everyone this close to its line. */
    public static final double BLUR_REACH = 1.3;
    public static final int BLUR_WINDUP = Piece.SYLAR_BLUR_MARK.ticks, BLUR_LEG = 4, BLUR_LEGS = 3;
    /** Every Hour: stopped time over every hour but two opposite ones. */
    public static final int HOURS_WINDUP = Piece.SYLAR_HOURS_MARK.ticks, STOPPED_TICKS = 40;
    /** The Light at 6:14: he rises over the dais and burns everyone he can see. */
    public static final double LIGHT_RISE = 5;
    public static final int LIGHT_WINDUP = Piece.SYLAR_LIGHT_MARK.ticks;
    /** Appraisal: a ring under you, then held in the air while he learns how your power works. */
    public static final double APPRAISE_RADIUS = 1.6, HELD_HEIGHT = 3;
    public static final int APPRAISE_WINDUP = Piece.SYLAR_APPRAISE_MARK.ticks, HOLD_TICKS = 60;
    public static final float STRUGGLE_NEEDED = 8;
    /** Your own power, turned on you: a bolt down a line from his hand. */
    public static final int YOURS_WINDUP = Piece.SYLAR_YOURS_MARK.ticks;
    /** A shove for anyone who stands this close. */
    public static final double SHOVE_REACH = 2.8;
    public static final int SHOVE_EVERY = 30;

    /** The Second Hand's angle {@code age} ticks into its sweep, from {@code start}. */
    public static double handAngle(double start, double age) {
        return start + HAND_SWEEP * Math.min(1, Math.max(0, age / HAND_TICKS));
    }

    /** Resonance's radius {@code age} ticks after it leaves his feet. */
    public static double ringRadius(double age) { return RING_START + RING_SPEED * age; }

    /** How long Resonance rolls before it reaches the wall. */
    public static int ringTicks() { return (int) Math.ceil((FLOOR_R + 1 - RING_START) / RING_SPEED); }

    /** Every Hour spares hour {@code safe} and the one opposite it. */
    public static boolean spared(int hour, int safe) { return hour == safe || hour == (safe + 6) % 12; }

    /** The distance from {@code p} to the segment {@code a}–{@code b}, across the floor. */
    public static double flatDistance(Vec3 p, Vec3 a, Vec3 b) {
        double abx = b.x - a.x, abz = b.z - a.z, apx = p.x - a.x, apz = p.z - a.z;
        double length = abx * abx + abz * abz;
        double k = length < 1e-6 ? 0 : Math.max(0, Math.min(1, (apx * abx + apz * abz) / length));
        return Math.hypot(apx - abx * k, apz - abz * k);
    }

    /** Where a line from the middle through clock angle {@code g} meets the wall, at standing height. */
    public static Vec3 rim(double g) { return center().add(dx(g, FLOOR_R), 0, dz(g, FLOOR_R)); }

    private SylarGeometry() {}
}
