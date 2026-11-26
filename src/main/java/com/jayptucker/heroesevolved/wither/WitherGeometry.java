package com.jayptucker.heroesevolved.wither;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.combat.BossLocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * The Ossuary, the creature's body, its breaches and every attack's shape, in one place: the server's hits, the
 * renderer's rest pose and the telegraphs all read these numbers, so a warning is exactly what lands.
 *
 * <p>Body points are written in model pixels (as {@code RiftWitherModel} builds them): x to the model's side, up from the
 * ground, and forward (the model's -z). {@link #point} turns them into the world for any yaw and scale.
 */
public final class WitherGeometry {
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "wither_cage"));
    public static final BlockPos ORIGIN = new BlockPos(0, 64, 0);
    /** The Power Level its numbers are tuned for: a fighter with a power at this level counts 1 ({@link #strength}). Anyone may enter. */
    public static final int BASELINE_LEVEL = 16;

    // ---- The Ossuary (tools/generate-wither-arena.py draws the same numbers) ----
    /** Walkable floor radius, the wall ring's outer radius, the vault's usable height and the leash for fliers. */
    public static final double RADIUS = 29, WALL = 31, FLIGHT_HEIGHT = 44, LEASH = 37;
    /** The ossuary wall's height above the floor; the giant ribs spring from its top. */
    public static final int WALL_HEIGHT = 10;
    public static final List<BlockPos> ANCHORS = List.of(ORIGIN.offset(-20, 6, -12), ORIGIN.offset(20, 6, -12), ORIGIN.offset(0, 6, 23));
    /** Broken pillars: real cover from Event Horizon and what Ribbreaker crashes into. x, z, half width, height above floor. */
    public static final int[][] PILLARS = {{-15, 6, 2, 12}, {15, 6, 2, 11}, {-9, -15, 1, 9}, {9, -15, 1, 10}, {-10, 17, 1, 8}, {10, 17, 1, 9}};
    public static Vec3 floor() { return Vec3.atBottomCenterOf(ORIGIN.above(3)); }
    public static Vec3 center() { return floor().add(0, 7, 0); }
    public static Vec3 entrance() { return floor().add(0, 0, -25); }
    /** The return breach in the entrance alcove, behind the wall. */
    public static Vec3 exit() { return floor().add(0, GATE_LIFT, -37); }

    // ---- Breaches: the summoning site's tear, the return alcove and the exit left where it died ----
    /** A walk-through breach's half width and half height, and how high its center hangs above the ground. */
    public static final float GATE_W = 1.25F, GATE_H = 2.15F, GATE_LIFT = 2.25F;
    /** Torn wide while the Wither is dragged through it at the summoning site. */
    public static final float WIDE_W = 2.7F, WIDE_H = 3.9F;
    /** The tear it drops out of over the dais when a fight begins. */
    public static final float ARRIVAL_W = 3.3F, ARRIVAL_H = 5.4F;
    public static Vec3 arrivalGate() { return floor().add(0, 11.5, 4); }
    public static Vec3 arrivalNormal() { return new Vec3(0, 0, -1); }

    /**
     * The summoning site, in ticks: the body knits, rears and roars; a seam splits the air behind it and tears wide; the
     * breach drags at it while it digs in; it is yanked backwards through; the breach convulses down to walking size.
     */
    public static final int FORM_END = 30, SEAM = 44, SPLIT = 56, OPENED = 72, PULL = 64, YANK = 96, THROUGH = 124,
            SEALED = 142, INTRO_TICKS = 150;
    /** How far behind the plane it falls before it is gone. */
    public static final float SWALLOW_DEPTH = 4;
    /** Over the dais: the tear opens, it falls out, lands, roars; then the fight. */
    public static final int ARRIVAL_OPEN = 14, ARRIVAL_LAND = 30, ARRIVAL_SHUT = 44, ARRIVAL_TICKS = 64;

    /** The breach's size at the summoning site at {@code t}: {width, height, open 0..1}. */
    public static float[] introGate(float t) {
        if (t < SEAM) return new float[]{0, 0, 0};
        float seam = smooth(SEAM, SPLIT, t), open = smooth(SPLIT, OPENED, t), close = smooth(THROUGH + 2, SEALED, t);
        float w = WIDE_W + (GATE_W - WIDE_W) * close, h = (WIDE_H * (.35F + .65F * seam)) + (GATE_H - WIDE_H) * close;
        return new float[]{w, h, Math.max(.02F * seam, open)};
    }
    /** The arrival tear over the dais at {@code t} ticks into the fight's opening: {width, height, open 0..1}. */
    public static float[] arrivalGateSize(float t) {
        float open = smooth(0, ARRIVAL_OPEN, t) * (1 - smooth(ARRIVAL_SHUT - 8, ARRIVAL_SHUT, t));
        return new float[]{ARRIVAL_W, ARRIVAL_H, open};
    }
    /** Where the arriving body's feet are: it falls out of the tear onto the dais. */
    public static Vec3 arrivalPath(float t) {
        Vec3 gate = arrivalGate(), start = gate.add(0, -HEIGHT / 2, 3), end = floor().add(0, .3, 0);
        if (t <= ARRIVAL_OPEN) return start;
        float k = Math.clamp((t - ARRIVAL_OPEN) / (ARRIVAL_LAND - ARRIVAL_OPEN), 0, 1);
        return new Vec3(lerp(k, start.x, end.x), lerp(k * k, start.y, end.y), lerp((float) Math.sqrt(k), start.z, end.z));
    }

    /**
     * The death scene, in ticks: it thrashes; its ribs burst; it rises and the star swells, light breaking out through
     * every fracture; the star drinks everything back in; then it detonates. The exit opens where it died while
     * everyone is still on the ground.
     */
    public static final int RIBS_BURST = 30, IMPLODE = 80, DETONATE = 96, DEATH_TICKS = 110, EXIT_OPENS = 40, EXIT_OPEN_TICKS = 34;
    /** The blast: how far it knocks people down, how long they stay down, how much of it they're out cold. */
    public static final float BLAST_RADIUS = 64;
    public static final int DAZE = 150, BLACKOUT = 50;
    /** How long the exit stays open after a victory before the Ossuary closes on its own. */
    public static final int EXIT_LINGER = 2400;

    // ---- The body ----
    /** Model scale in the arena and at the summoning site. The arena body stands a little under ten blocks tall. */
    public static final float ARENA_SCALE = 1.8F, INTRO_SCALE = 1.1F;
    public static final float WIDTH = 5.2F, HEIGHT = 8.8F;
    /** Rest-pose anchors in model pixels {x, up, forward}. The model's bones are built to end exactly here. */
    public static final float[] HIPS = {0, 16, -2}, CORE = {0, 43, 4}, YOKE = {0, 54, -2};
    public static final float[][] NECK_BASE = {{0, 57, -1}, {-16, 58, -2}, {16, 58, -2}};
    public static final float[][] HEAD = {{0, 68, 4}, {-25, 64, 1}, {25, 64, 1}};
    public static final float[] MOUTH_REACH = {10, 8, 8};
    public static final float[][] SHOULDER = {{-20, 55, -2}, {20, 55, -2}};
    public static final float[][] HAND = {{-21, 17, 6}, {21, 17, 6}};

    public static Vec3 point(Vec3 feet, float yaw, float scale, float x, float up, float forward) {
        double r = Math.toRadians(yaw), s = scale / 16D;
        Vec3 right = new Vec3(-Math.cos(r), 0, -Math.sin(r)), ahead = new Vec3(-Math.sin(r), 0, Math.cos(r));
        return feet.add(right.scale(-x * s)).add(ahead.scale(forward * s)).add(0, up * s, 0);
    }
    private static Vec3 point(Vec3 feet, float yaw, float[] p) { return point(feet, yaw, ARENA_SCALE, p[0], p[1], p[2]); }
    public static Vec3 core(Vec3 feet) { return feet.add(0, CORE[1] * ARENA_SCALE / 16, 0); }
    public static Vec3 core(Vec3 feet, float yaw) { return point(feet, yaw, CORE); }
    /** Where head {@code index} (0 center, 1 and 2 the sides) sits at rest. */
    public static Vec3 head(Vec3 feet, float yaw, int index) { return point(feet, yaw, HEAD[index]); }
    /** Its open mouth once it has turned to {@code aim}: beams, skulls and tethers leave from here. */
    public static Vec3 mouth(Vec3 feet, float yaw, int index, Vec3 aim) {
        Vec3 head = head(feet, yaw, index);
        Vec3 to = aim.subtract(head);
        Vec3 dir = to.lengthSqr() < 1e-4 ? Vec3.directionFromRotation(0, yaw) : to.normalize();
        return head.add(dir.scale(MOUTH_REACH[index] * ARENA_SCALE / 16)).add(0, -.2, 0);
    }
    public static Vec3 hand(Vec3 feet, float yaw, int side) { return point(feet, yaw, HAND[side]); }
    public static Vec3 forward(float yaw) { double r = Math.toRadians(yaw); return new Vec3(-Math.sin(r), 0, Math.cos(r)); }

    // ---- Moves ----
    /**
     * Windups are each telegraph's length in the first phase (later phases and feints change it per cast:
     * {@link #castWindup}); rest is the pause a move adds before the next, shortened each phase ({@link #restScale}).
     */
    public enum Move {
        VERDICT(19, 8), RAIN(17, 6), PULSE(18, 6), TETHER(14, 6), LUNGE(18, 8), SLAM(21, 10), HORIZON(48, 14),
        BITE(10, 4), RAKE(14, 6), SPIRES(16, 7), BROOD(18, 8),
        /** It tears the air and steps through, out of a second tear beside its focus. */
        STEP(16, 0),
        /** Its tail sweeps a full circle low round it: a jump clears it. */
        TAIL(14, 6),
        /** Ribs torn from its chest and thrown into the floor round a fighter: a cage only a power breaks early. */
        CAGE(18, 6),
        /** A starving little star spat out to drift after the farthest fighter, pulling, until it collapses. */
        WELL(16, 6);
        public final int windup, rest;
        Move(int windup, int rest) { this.windup = windup; this.rest = rest; }
        /** Moves that draw on an anchor while they wind up, so the room's counter is lit. */
        public boolean feeds() { return this == RAIN || this == SLAM || this == HORIZON || this == SPIRES || this == LUNGE || this == CAGE || this == WELL; }
    }
    public static final int HEAD_WINDUP = 16, FEED_TICKS = 140, STANCE_HITS = 3, TRANSITION_TICKS = 40;
    /** Maw Snap: the center head strikes down a short lane in front of it. */
    public static final float BITE_REACH = 9, BITE_RADIUS = 1.7F, BITE_START = 2;
    /** Rending Claw: one arm sweeps a wide arc. */
    public static final float RAKE_REACH = 10.5F, RAKE_HALF_ANGLE = 1.25F;
    /** Threefold Verdict: three beams held for {@code VERDICT_FIRE} ticks, ending at the first solid block. */
    public static final float VERDICT_RADIUS = 1.15F, VERDICT_SPREAD = 3.6F, VERDICT_LENGTH = 34;
    public static final int VERDICT_FIRE = 10;
    /** Hollow Rain: burning skulls dropped onto marked circles. */
    public static final float SKULL_RADIUS = 2.3F, RAIN_HEIGHT = 12, RAIN_SPREAD = 4;
    /** Grave Tether: a hooked thread down a narrow fixed lane; whoever it catches is hauled in. */
    public static final float TETHER_RADIUS = 1.1F, TETHER_LENGTH = 30;
    /** Starved Pulse and every slam's aftershock: a ring of the real floor thrown up. A normal jump clears it. */
    public static final float PULSE_SPEED = .7F, PULSE_REACH = 26, WAVE_HEIGHT = .8F, WAVE_FRONT = 1.1F;
    public static final int PULSE_ECHO = 14;
    /** Ribbreaker: a charge along a fixed lane; a wall or pillar in the way stops it dead and leaves it stunned. */
    public static final float LUNGE_RADIUS = 3, LUNGE_REACH = 26, LUNGE_OVERSHOOT = 8, LUNGE_LIFT = 3;
    public static final int LUNGE_TICKS = 14, CRASH_TICKS = 50;
    /** Ossuary Slam: it rises, then both fists come down; the shock rolls out from where they land. */
    public static final float SLAM_RADIUS = 5.5F, SLAM_RISE = 9, SLAM_WAVE = 18;
    public static final int SLAM_TICKS = 10;
    /** Bone Spires: a crack runs to each fighter's mark, and spires burst from it in order. */
    public static final float SPIRE_RADIUS = 1.25F, SPIRE_HEIGHT = 3.6F, SPIRE_STEP = 1.6F, SPIRE_REACH = 24;
    public static final int SPIRE_RATE = 2, SPIRE_LINGER = 26;
    /**
     * Bone Brood: wither skeletons retched up out of its chest and thrown in arcs onto marked spots beside each fighter.
     * Whoever stands on a mark when one lands is hurt; they crumble after {@code BROOD_LIFE} ticks or when it dies.
     */
    public static final float BROOD_LAND = 1.6F, BROOD_BESIDE = 2.6F;
    public static final int BROOD_FLIGHT = 16, BROOD_LIFE = 600;
    /** How many it spews at once, and how many may stand at a time, for a party of {@code fighters}. */
    public static int broodCount(int phase, int fighters) { return Math.min(4, (phase == 1 ? 2 : 3) + (fighters - 1) / 2); }
    public static int broodCap(int fighters) { return Math.min(8, 3 + fighters); }
    /** Ticks between broods in each phase. */
    public static int broodCooldown(int phase) { return phase == 1 ? 400 : phase == 2 ? 300 : 220; }
    /** A spewed skeleton's flight from the mouth to its mark at {@code k} (0 to 1): a high arc. */
    public static Vec3 spewArc(Vec3 from, Vec3 to, float k) {
        double rise = 4 + from.distanceTo(to) * .25;
        return from.lerp(to, k).add(0, 4 * rise * k * (1 - k), 0);
    }
    /**
     * Breach Step: a tear opens where it stands and another {@code STEP_BEHIND} blocks past its focus (the far side from
     * it, so the focus must turn); it falls through and emerges over {@code STEP_TICKS}, and the arrival's shock hits
     * anyone on the marked ring. It strikes on at once.
     */
    public static final float STEP_BEHIND = 4.5F, STEP_RADIUS = 3.4F, STEP_DEPTH = 2.6F;
    public static final int STEP_TICKS = 6;
    /** Tail Reap: a low full circle from just outside its body to {@code TAIL_REACH}, swept over {@code TAIL_TICKS}. */
    public static final float TAIL_INNER = 2.4F, TAIL_REACH = 7.5F, TAIL_HEIGHT = .9F;
    public static final int TAIL_TICKS = 8;
    /** Bone Cage: eight ribs land on a ring round a fighter; whoever is inside is held until a power breaks the bars. */
    public static final float CAGE_RADIUS = 3.2F;
    public static final int CAGE_BARS = 8, CAGE_HOLD = 40;
    /** Starving Well: it drifts after its fighter at {@code WELL_SPEED}, pulling within {@code WELL_PULL}, and collapses after {@code WELL_LIFE}. */
    public static final float WELL_SPEED = .2F, WELL_PULL = 9, WELL_BLAST = 3.5F, WELL_HEIGHT = 1.6F;
    public static final int WELL_LIFE = 90;
    /** Event Horizon: the star drinks for three seconds, then shines on everyone it can see. */
    public static final float HORIZON_PULL = .045F, HORIZON_REACH = 70;
    /**
     * Its timed answers: a power used in the last {@code HORIZON_DODGE} ticks before the discharge carries a dodging or
     * time-stopping key out of it (the warning's last white ring shows the moment). A regenerator outlasts it with
     * {@code OUTLAST_LEFT} health.
     */
    public static final int HORIZON_DODGE = 14;
    public static final float OUTLAST_LEFT = 6;

    // ---- The locks (combat/BossLocks): what each phase asks, and what answering it pays ----
    /** The locks each phase is built from: the ward and its skulls; the brood, the feeding hearts, the mark; the discharge, the star, the hearts. */
    public static BossLocks.Lock[] locks(int phase) {
        return switch (Math.max(1, Math.min(3, phase))) {
            case 1 -> new BossLocks.Lock[]{BossLocks.Lock.SHIELD, BossLocks.Lock.PROJECTILES};
            case 2 -> new BossLocks.Lock[]{BossLocks.Lock.BODIES, BossLocks.Lock.ROOM_WEAPON, BossLocks.Lock.CURSE};
            default -> new BossLocks.Lock[]{BossLocks.Lock.UNBLOCKABLE, BossLocks.Lock.WEAK_POINT, BossLocks.Lock.ROOM_WEAPON};
        };
    }
    /**
     * The Star Ward: the three Hunger Hearts in the pylons feed a lattice over its ribs. While three live it takes a
     * quarter of every blow, two a half, one three quarters, and it cannot be brought below {@code WARD_GATE} of its
     * health. A broken heart re-forms after {@code HEART_MEND} ticks while the ward stands; break all three and the ward
     * shatters, throwing it down for {@code WARD_CRASH} ticks.
     */
    public static final float WARD_GATE = .7F;
    public static final float[] WARD_TAKES = {1, .75F, .5F, .25F};
    public static final int HEART_HEALTH = 50, HEART_MEND = 600, WARD_CRASH = 60;
    /** A heart's cell in its pylon, two blocks tall and open on all four sides; the heart floats in it. */
    public static Vec3 heart(int index) { return Vec3.atBottomCenterOf(ANCHORS.get(index)).add(0, .25, 0); }
    public static Vec3 heartCenter(int index) { return heart(index).add(0, HEART_SIZE[1] / 2, 0); }
    /** The heart's width and height. */
    public static final float[] HEART_SIZE = {1.1F, 1.5F};
    /** A skull a fighter sends back: it hits the Wither for this share of its health, past the ward, and shatters a heart outright. */
    public static final float RETURNED_SHARE = .04F;
    /** Plain weapons (blades, arrows, bullets) do this much to its body; its powers' blows do it all. */
    public static final float PLAIN_BODY = .6F;
    /**
     * The mending brood (from the second phase): a skeleton that has stood {@code BROOD_RETURN} ticks walks back to it,
     * carrying bone; one that reaches {@code MEND_REACH} of its body climbs into its cage and mends {@code MEND_SHARE} of
     * its health (never above the top of its phase) and a crack in its stance.
     */
    public static final int BROOD_RETURN = 140;
    public static final float MEND_SHARE = .03F, MEND_REACH = 3.6F;
    /**
     * The Hunger Mark (from the second phase): a bite or a hook leaves it for {@code MARK_TICKS}. While it can see you
     * its star drinks {@code MARK_DRAIN} health a second from you and heals {@code MARK_FEED} of its own; out of its
     * sight for {@code MARK_UNSEEN} ticks in a row, the thread snaps.
     */
    public static final int MARK_TICKS = 240, MARK_UNSEEN = 60;
    public static final float MARK_DRAIN = 1, MARK_FEED = .005F;
    /**
     * The exposed star (Critical Mass): a blow that lands on the star (a shot within {@code STAR_RADIUS} of it, a blow
     * from up at its height, or an aimed one) does {@code STAR_TAKES} as much, or what the lock table gives a power whose
     * key is its blows there; three within {@code STAR_WINDOW} ticks crack it and it reels.
     */
    public static final float STAR_RADIUS = 2.2F, STAR_TAKES = 1.6F;
    public static final int STAR_CRACKS = 3, STAR_WINDOW = 100, STAR_REEL = 40;
    /** The top of each phase's health: nothing it heals can lift it back into a phase already beaten. */
    public static float phaseTop(int phase) { return phase <= 1 ? 1 : phase == 2 ? 2 / 3F : 1 / 3F; }

    // ---- Who it is fighting: it scales to the party's size, powers and levels ----
    /**
     * How much one fighter counts. With an awakened power at Power Level 16 a fighter counts 1, and each level above
     * adds {@code LEVEL_STEP} (Evolved, at 20, counts 1.24); a lower level counts {@code LEVEL_STEP} less a level, never
     * under {@code WEAKEST_GIFT}. Without an awakened power a fighter counts {@code UNGIFTED}: their only answer to its
     * locks is the slow one.
     */
    public static final float LEVEL_STEP = .06F, WEAKEST_GIFT = .6F, UNGIFTED = .5F;
    public static float strength(boolean gifted, int powerLevel) {
        return gifted ? Math.clamp(1 + LEVEL_STEP * (powerLevel - BASELINE_LEVEL), WEAKEST_GIFT, 1.3F) : UNGIFTED;
    }
    /**
     * Its health for a party whose strengths add up to {@code weight}: half again for each extra fighter's worth, up to
     * three times; a lone weaker fighter brings it down, never under 70%. It never shrinks during an attempt.
     */
    public static float healthScale(float weight) { return Math.clamp(1 + .5F * (weight - 1), .7F, 3); }
    /** A heart's health the same way, up to twice. */
    public static float heartScale(float weight) { return Math.clamp(1 + .5F * (weight - 1), .7F, 2); }
    /**
     * Its blows against the fighters in the Ossuary now, by their average strength: as listed against level-16 powers,
     * up to a tenth harder against stronger ones, down to a fifth softer against weaker ones.
     */
    public static float blowScale(float average) { return Math.clamp(.7F + .3F * average, .8F, 1.1F); }
    /** Its warnings against weaker fighters run longer, by up to 15% (never shorter than listed for strong ones). */
    public static float windupScale(float average) { return 1 + .25F * Math.clamp(1 - average, 0, .6F); }
    /** Its side heads fire sooner at a bigger party: 8% sooner for each extra fighter, to 70% of the gap. */
    public static float headShotScale(int fighters) { return Math.max(.7F, 1 - .08F * (Math.max(1, fighters) - 1)); }

    // ---- How hard it is (Souls-like: it is won by reading it, never by out-healing or out-trading it) ----
    /** Below this share of its health it is Starving: faster still. */
    public static final float STARVING = .15F;
    /**
     * Each move's windup for one cast: shorter each phase and when Starving, and from the second phase every third bite,
     * rake, slam and charge is a feint that holds its coil half again as long, so a panicked dodge is punished. The
     * warning fills over exactly this, and the body holds its pose for it.
     */
    public static int castWindup(Move move, int phase, int cast, boolean starving) {
        float scale = phase <= 1 ? 1 : phase == 2 ? .86F : .74F;
        if (starving) scale *= .88F;
        boolean feintable = move == Move.BITE || move == Move.RAKE || move == Move.SLAM || move == Move.LUNGE || move == Move.TAIL;
        if (phase >= 2 && feintable && cast % 3 == 0) scale *= 1.45F;
        return Math.max(floor(move), Math.round(move.windup * scale));
    }
    /**
     * The shortest a move's opening pose and signal may run, however fast it gets (fight-design.md, section 3): a
     * reaction window of 8 ticks for a quick single-target strike, 10 for the hook, 12 for anything with an area mark.
     */
    public static int floor(Move move) { return move == Move.BITE ? 8 : move == Move.TETHER ? 10 : 12; }
    /**
     * The end pose: how long the strike is held before the window opens, so a player sees it has ended (at least four
     * ticks; five on the bite, a bait that draws a swing). Moves that travel hold after they land.
     */
    public static int hold(Move move) {
        return switch (move) {
            case HORIZON -> 20; case VERDICT -> VERDICT_FIRE + 2; case SPIRES -> 10; case BROOD, CAGE, WELL -> 8; case BITE -> 5;
            case STEP -> 4; default -> 6;
        };
    }
    /** How much of a move's rest it keeps in each phase, and how long it slumps after a whole chain. */
    public static float restScale(int phase) { return phase <= 1 ? .6F : phase == 2 ? .45F : .32F; }
    public static int spentTicks(int phase) { return phase <= 1 ? 30 : phase == 2 ? 22 : 16; }
    /** Ticks between side-head skulls in each phase. */
    public static int headShotGap(int phase) { return phase <= 1 ? 60 : phase == 2 ? 44 : 32; }
    /**
     * Gear never trivialises it. Part of every blow is its star's hunger, which no armour stops ({@code PIERCE}); its
     * heavy blows also take {@code HEAVY_SHARE} of the target's own maximum health, so extra hearts from other mods buy
     * time, not safety. On its side, no blow takes more than {@code RiftWitherEntity.HIT_CAP} of its health and no
     * second more than {@code SECOND_CAP}: however strong the weapon, a flawless fight lasts forty seconds.
     */
    public static final float PIERCE = .3F, HEAVY_SHARE = .05F;
    public static boolean heavy(Move move) {
        return move == Move.SLAM || move == Move.LUNGE || move == Move.HORIZON || move == Move.TAIL || move == Move.STEP || move == Move.WELL;
    }
    /** What each move does to whoever it catches, before armour: a quarter to most of an unarmoured player's health. */
    public static float damage(Move move, int phase) {
        float base = switch (move) {
            case BITE -> 11; case RAKE -> 13; case VERDICT -> 9; case RAIN -> 8; case PULSE -> 9; case TETHER -> 7;
            case LUNGE -> 14; case SLAM -> 16; case SPIRES -> 9; case HORIZON -> 22; case BROOD -> 6;
            case STEP -> 10; case TAIL -> 12; case CAGE -> 6; case WELL -> 14;
        };
        return base * (1 + .15F * (Math.max(1, phase) - 1));
    }

    /**
     * The chain it opens on a fighter: what reaches them from where they are, then follow-ups that punish the usual
     * answer. Three moves from the start, four later; a Breach Step closes any distance at once and strikes on; Event
     * Horizon every third chain of the last phase. (A fighter at its back meets the tail first: {@link RiftWitherEntity}.)
     */
    public static Move[] chain(int phase, int turn, double distance, boolean high) {
        boolean odd = Math.floorMod(turn, 2) == 1;
        if (phase >= 3 && Math.floorMod(turn, 3) == 2) return new Move[]{Move.HORIZON, Move.PULSE};
        if (high) return switch (phase) {
            case 1 -> odd ? new Move[]{Move.TETHER, Move.RAIN, Move.VERDICT} : new Move[]{Move.TETHER, Move.VERDICT, Move.RAIN};
            case 2 -> odd ? new Move[]{Move.TETHER, Move.RAIN, Move.VERDICT} : new Move[]{Move.VERDICT, Move.TETHER, Move.LUNGE};
            default -> odd ? new Move[]{Move.WELL, Move.TETHER, Move.VERDICT} : new Move[]{Move.VERDICT, Move.TETHER, Move.LUNGE, Move.RAIN};
        };
        if (distance < 9) return switch (phase) {
            case 1 -> odd ? new Move[]{Move.RAKE, Move.TAIL, Move.PULSE} : new Move[]{Move.BITE, Move.RAKE, Move.TAIL};
            case 2 -> odd ? new Move[]{Move.BITE, Move.CAGE, Move.SPIRES} : new Move[]{Move.RAKE, Move.BITE, Move.TAIL, Move.SLAM};
            default -> odd ? new Move[]{Move.SLAM, Move.TAIL, Move.RAKE, Move.BITE} : new Move[]{Move.CAGE, Move.RAKE, Move.BITE, Move.PULSE};
        };
        if (distance < 18) return switch (phase) {
            case 1 -> odd ? new Move[]{Move.PULSE, Move.BITE, Move.RAKE} : new Move[]{Move.VERDICT, Move.RAIN, Move.STEP};
            case 2 -> odd ? new Move[]{Move.STEP, Move.RAKE, Move.BROOD} : new Move[]{Move.SPIRES, Move.CAGE, Move.VERDICT};
            default -> odd ? new Move[]{Move.WELL, Move.SPIRES, Move.SLAM} : new Move[]{Move.STEP, Move.CAGE, Move.VERDICT, Move.PULSE};
        };
        return switch (phase) {
            case 1 -> odd ? new Move[]{Move.STEP, Move.BITE, Move.RAKE} : new Move[]{Move.RAIN, Move.BROOD, Move.STEP};
            case 2 -> odd ? new Move[]{Move.BROOD, Move.STEP, Move.RAKE} : new Move[]{Move.LUNGE, Move.STEP, Move.BITE};
            default -> odd ? new Move[]{Move.WELL, Move.BROOD, Move.STEP} : new Move[]{Move.LUNGE, Move.STEP, Move.TAIL, Move.RAKE};
        };
    }
    /** Where a Breach Step comes out: past its focus, on the far side from where it stands, inside the floor. */
    public static Vec3 stepTarget(Vec3 from, Vec3 focus) {
        Vec3 flat = new Vec3(focus.x - from.x, 0, focus.z - from.z);
        Vec3 dir = flat.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : flat.normalize();
        Vec3 at = new Vec3(focus.x, floor().y + .3, focus.z).add(dir.scale(STEP_BEHIND));
        Vec3 off = at.subtract(floor()).multiply(1, 0, 1);
        if (off.length() > RADIUS - 4) at = floor().add(off.normalize().scale(RADIUS - 4)).add(0, .3, 0);
        return at;
    }
    /** Where a Breach Step may come out, in order: past its focus, then to either side of them, then in front. */
    public static List<Vec3> stepTargets(Vec3 from, Vec3 focus) {
        Vec3 flat = new Vec3(focus.x - from.x, 0, focus.z - from.z);
        Vec3 dir = flat.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : flat.normalize(), side = new Vec3(-dir.z, 0, dir.x);
        List<Vec3> out = new ArrayList<>();
        out.add(stepTarget(from, focus));
        for (Vec3 off : List.of(side, side.scale(-1), dir.scale(-1))) {
            Vec3 at = new Vec3(focus.x, floor().y + .3, focus.z).add(off.scale(STEP_BEHIND + 1));
            Vec3 d = at.subtract(floor()).multiply(1, 0, 1);
            if (d.length() > RADIUS - 4) at = floor().add(d.normalize().scale(RADIUS - 4)).add(0, .3, 0);
            out.add(at);
        }
        return out;
    }
    /** The cage's eight bars round {@code center} on the floor. */
    public static List<Vec3> cageBars(Vec3 center) {
        List<Vec3> out = new ArrayList<>();
        for (int i = 0; i < CAGE_BARS; i++) {
            double a = i * Math.PI * 2 / CAGE_BARS;
            out.add(new Vec3(center.x + Math.cos(a) * CAGE_RADIUS, floor().y, center.z + Math.sin(a) * CAGE_RADIUS));
        }
        return out;
    }

    public static double angle(Vec3 from, Vec3 to, float yaw) {
        Vec3 d = to.subtract(from);
        double a = Math.atan2(-d.x, d.z) - Math.toRadians(yaw);
        return Math.abs(Math.atan2(Math.sin(a), Math.cos(a)));
    }
    public static boolean inArc(Vec3 from, Vec3 to, float yaw, double reach, double halfAngle) {
        return to.multiply(1, 0, 1).distanceToSqr(from.multiply(1, 0, 1)) <= reach * reach && angle(from, to, yaw) <= halfAngle;
    }
    public static double lineDistance(Vec3 a, Vec3 b, Vec3 point) {
        Vec3 d = b.subtract(a);
        double t = Math.clamp(point.subtract(a).dot(d) / Math.max(1e-7, d.lengthSqr()), 0, 1);
        return point.distanceTo(a.add(d.scale(t)));
    }
    /** Maw Snap's lane on the floor: from just in front of its chest to the full reach along its committed yaw. */
    public static Vec3[] biteLane(Vec3 feet, float yaw) {
        Vec3 ahead = forward(yaw), base = new Vec3(feet.x, floor().y, feet.z);
        return new Vec3[]{base.add(ahead.scale(BITE_START)), base.add(ahead.scale(BITE_REACH))};
    }
    /** The spires' centers along one crack, nearest the creature first. */
    public static List<Vec3> spires(Vec3 from, Vec3 mark) {
        Vec3 flat = new Vec3(mark.x - from.x, 0, mark.z - from.z);
        double length = Math.min(SPIRE_REACH, flat.length() + 4);
        Vec3 dir = flat.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : flat.normalize();
        List<Vec3> out = new ArrayList<>();
        for (double d = 3; d <= length; d += SPIRE_STEP) out.add(new Vec3(from.x, floor().y, from.z).add(dir.scale(d)));
        return out;
    }
    /** The ripple's ring at {@code age} ticks: a pressure front {@link #WAVE_HEIGHT} high. */
    public static double ripple(float age, float speed) { return age * speed; }
    /** How high the thrown-up floor stands {@code offset} blocks ahead of (+) or behind (-) a ripple's front. */
    public static float lift(double offset) {
        if (Math.abs(offset) >= WAVE_FRONT) return 0;
        float c = (float) Math.cos(offset / WAVE_FRONT * Math.PI / 2);
        return WAVE_HEIGHT * c * c;
    }
    public static float smooth(float a, float b, float x) { float t = Math.clamp((x - a) / (b - a), 0, 1); return t * t * (3 - 2 * t); }
    private static double lerp(float k, double a, double b) { return a + (b - a) * k; }
    private WitherGeometry() {}
}
