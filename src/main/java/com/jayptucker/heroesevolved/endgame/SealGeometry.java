package com.jayptucker.heroesevolved.endgame;

import com.jayptucker.heroesevolved.combat.BossLocks;
import com.jayptucker.heroesevolved.combat.BossScaling;
import net.minecraft.world.phys.Vec3;

/**
 * The Living Seal (the native End Dragon) in numbers: its moves, the five beats of each, its locks, how hard it hits and
 * what it takes. The server's hits, the poses on its body and every mark on the ground read these, so a warning fills
 * exactly as its hit lands (the bosses skill's fight-design.md).
 */
public final class SealGeometry {
    private SealGeometry() {}

    // ---- Who it fights and how much it can take ----
    /** Tuned for an awakened power at this Power Level (the End's entry level). */
    public static final int BASELINE_LEVEL = EndGeometry.MIN_LEVEL;
    /** Health for one fighter of strength 1; the party's strength scales it (combat/BossScaling). */
    public static final float BASE_HEALTH = 800;
    /** No blow takes more than this share of its health, and no second more than this: a flawless fight lasts 40 s. */
    public static final float HIT_CAP = .05F, SECOND_CAP = .025F;
    /**
     * In the air it takes {@code AIR_TAKES} of what a blow would do on the ground, after the out-of-reach lock's factor
     * (BossLocks.hitFactor: plain weapons half, a power in full, a reach or hit-there key more). On the ground plain
     * weapons do {@code PLAIN_BODY}; its head takes more, the rest of its body a little less.
     */
    public static final float AIR_TAKES = .5F, PLAIN_BODY = .6F, HEAD_TAKES = 1.2F, BODY_TAKES = .85F;
    /** Crashed or slumped after a chain it takes this much more; staggered, a little more. */
    public static final float OPEN_TAKES = 1.35F, STAGGER_TAKES = 1.2F;

    // ---- The Seal (phase one's shield): four knots at the altars ----
    /** What it takes with this many knots standing (index): all four, a quarter. */
    public static final float[] SEAL_TAKES = {1, .75F, .55F, .4F, .25F};
    /** While any knot stands it can't be brought below this share of its health. */
    public static final float SEAL_GATE = .7F;
    /** A knot's health (scaled by the party, BossScaling.lockScale), and how long a broken one takes to re-form. */
    public static final float KNOT_HEALTH = 40;
    public static final int KNOT_MEND = 600;
    /** A knot floats this far over its altar's focus (the focus the counter-resonators use in Sylar's stage). */
    public static final double KNOT_LIFT = .6;
    /** A shard sent back hits it for this share of its health, past the Seal; a feeding knot's backfire for this. */
    public static final float RETURNED_SHARE = .04F, BACKFIRE_SHARE = .1F;
    /** A feeding knot drinks this long, from the windup of the move it feeds. */
    public static final int FEED_TICKS = 160;

    // ---- Out of reach ----
    /**
     * An aimed bring-down adds this much drag (strong, good); at 1 it is dragged out of the sky. It fades slowly, so one
     * player alone gets there in two strong uses or three good ones, each a full aim cooldown apart (BossLocks.AIM_COOLDOWN).
     */
    public static final float DRAG_STRONG = .55F, DRAG_GOOD = .38F, DRAG_FADE = .001F;

    // ---- Riftbrand (phase two's curse) ----
    /** Its breath brands for {@code BRAND_TICKS}; while seen the Seal drinks {@code BRAND_DRAIN} a second and mends {@code BRAND_FEED}. */
    public static final int BRAND_TICKS = 240, BRAND_UNSEEN = 60;
    public static final float BRAND_DRAIN = 1, BRAND_FEED = .004F;

    // ---- The open star (phase three's weak point) ----
    public static final float STAR_RADIUS = 3, STAR_TAKES = 1.5F;
    public static final int STAR_CRACKS = 3, STAR_WINDOW = 100, STAR_REEL = 40;

    // ---- Windows ----
    /** Three charged hits during windups, spaced {@code STANCE_GAP}, break its stance; it reels {@code STAGGER}. */
    public static final int STANCE_HITS = 3, STANCE_GAP = 8, STAGGER = 36, INTERRUPT_GAP = 100;
    /** A crash (the Seal shattered, a feeding knot's backfire) leaves it stunned on the ground. */
    public static final int CRASH_TICKS = 50, DRAGGED_TICKS = 30;
    /** How long it slumps after a chain, by phase. */
    public static int slump(int phase) { return phase <= 1 ? 30 : phase == 2 ? 22 : 16; }
    /** How much of a move's rest it keeps in each phase. */
    public static float restScale(int phase) { return phase <= 1 ? .7F : phase == 2 ? .55F : .42F; }

    // ---- The hunt and the landings ----
    /** Air chains before it lands, by phase (phase three no longer hunts: its wings are torn). */
    public static final int[] AIR_CHAINS = {2, 2, 0};
    /** How long it stays down, by phase; its ground timer pauses while a move plays. Phase three stays down. */
    public static final int[] LANDED_TICKS = {240, 220, Integer.MAX_VALUE / 4};
    /** It takes off early when nobody stays within {@code IDLE_REACH} for {@code IDLE_TAKEOFF} ticks. */
    public static final int IDLE_TAKEOFF = 80;
    public static final double IDLE_REACH = 24;
    public static final double ORBIT_RADIUS = 30, ORBIT_SPEED = .018, ORBIT_LIFT = 12, ISLAND_RADIUS = 64;
    /** Phase changes: it holds for this long and can't be hurt. */
    public static final int TRANSITION_TICKS = 40;

    // ---- Shapes ----
    /** Riftdive: it climbs behind the marked spot, dives through it along the strip and pulls out beyond. */
    public static final double DIVE_RUN = 34, DIVE_HEIGHT = 24, DIVE_EXIT = 16, DIVE_STRIP = 11;
    public static final float DIVE_RADIUS = 4.5F, DIVE_IMPACT = 6;
    public static final int DIVE_APPROACH = 16, DIVE_PULLOUT = 18;
    /** Severing Breath: a low run along the strip, dropping pools. */
    public static final double RUN_HALF = 22, RUN_ALT = 8, RUN_LEAD = 12;
    public static final float RUN_RADIUS = 3, POOL_RADIUS = 2.8F;
    public static final int RUN_TICKS = 34, RUN_POOLS = 9, POOL_TICKS = 110, POOL_TICKS_LAST = 170;
    /** Starfall: shards thrown from its rings to a ring round each fighter, landing {@code SHARD_FLIGHT} after the windup. */
    public static final float SHARD_RADIUS = 3.2F;
    public static final int SHARD_FLIGHT = 20;
    /** Sky Harpoon: a thread from its head; whoever it catches is hauled down. */
    public static final float HARPOON_RADIUS = 1.4F;
    public static final double HARPOON_REACH = 48;
    /** Descent: it glides over the marked ring and slams into it. */
    public static final float LAND_RADIUS = 7;
    public static final int LAND_DROP = 10;
    /** Its ground moves, measured from the body. */
    public static final double BITE_FROM = 3, BITE_REACH = 13;
    public static final float BITE_RADIUS = 2.6F, TAIL_RADIUS = 12, TAIL_HALF = 1.22F, SCORCH_RADIUS = 16, SCORCH_HALF = .56F,
            ROAR_RADIUS = 16, TAKEOFF_RADIUS = 9, BUFFET_REACH = 18;
    /** Seal Rush: a charge down a lane at someone 12 to 30 blocks away, ending past them. */
    public static final double RUSH_MIN = 12, RUSH_REACH = 30, RUSH_OVER = 5;
    public static final float RUSH_RADIUS = 3.2F;
    public static final int RUSH_TICKS = 14;
    /** Rift Leap (phase three): an arc onto a ring by the farthest fighter, its shock rolling out from there. */
    public static final float LEAP_RADIUS = 6;
    public static final double LEAP_HEIGHT = 10, LEAP_SHORT = 3, LEAP_MIN = 10;
    public static final int LEAP_TICKS = 12;
    /** The Unmaking: it climbs over the island's centre; a power used in the last {@code UNMAKING_DODGE} ticks slips it. */
    public static final double UNMAKING_HEIGHT = 38;
    public static final int UNMAKING_DODGE = 14;
    public static final float OUTLAST_LEFT = 6;
    /** A low pressure ring (Wing Buffet and Rift Leap's shock): a jump clears it. */
    public static final double WAVE_SPEED = 1.35;
    public static final float WAVE_HEIGHT = .8F;

    // ---- Gear never trivialises it ----
    /** 30% of every blow is the Seal's fracture, which no armour stops; heavy blows also take 5% of your maximum health. */
    public static final float PIERCE = .3F, HEAVY_SHARE = .05F;

    // ---- Moves ----
    /**
     * Every move: its windup at the first phase (the opening pose and signal; the mark fills over it, plus
     * {@link #travel}), its rest before the next, and whether it is cast in the air.
     */
    public enum Move {
        RIFTDIVE(26, 10, true), BREATH(30, 10, true), STARFALL(24, 8, true), HARPOON(14, 6, true), DESCENT(28, 6, true),
        BITE(10, 4, false), TAIL(16, 6, false), BUFFET(18, 6, false), SCORCH(22, 8, false), RUSH(20, 8, false),
        ROAR(26, 8, false), LEAP(20, 8, false), TAKEOFF(14, 10, false), UNMAKING(70, 20, false);
        public final int windup, rest;
        public final boolean air;
        Move(int windup, int rest, boolean air) { this.windup = windup; this.rest = rest; this.air = air; }
        public static Move byId(int id) { return id < 0 || id >= values().length ? null : values()[id]; }
    }
    /** How long after its windup a move's hit lands: the mark fills over windup plus this. */
    public static int travel(Move move) {
        return switch (move) { case RIFTDIVE -> DIVE_APPROACH; case DESCENT -> LAND_DROP; case STARFALL -> SHARD_FLIGHT; case LEAP -> LEAP_TICKS; default -> 0; };
    }
    /** How long the strike runs (the live beat), for the poses. */
    public static int strike(Move move) {
        return switch (move) {
            case BITE -> 3; case TAIL -> 4; case BUFFET -> 3; case SCORCH -> 8; case RUSH -> RUSH_TICKS; case ROAR -> 6;
            case LEAP -> LEAP_TICKS; case TAKEOFF -> 8; case RIFTDIVE -> DIVE_APPROACH + DIVE_PULLOUT; case BREATH -> RUN_TICKS;
            case STARFALL -> SHARD_FLIGHT; case HARPOON -> 4; case DESCENT -> LAND_DROP; case UNMAKING -> 6;
        };
    }
    /** The end pose: the strike held before the window opens (four ticks at least; five on the bite, a bait). */
    public static int hold(Move move) {
        return switch (move) { case BITE -> 5; case SCORCH, ROAR, DESCENT -> 8; case TAKEOFF -> 0; case BREATH -> 4; case UNMAKING -> 20; default -> 6; };
    }
    /** The shortest a move's windup may run however fast it gets: 8 ticks for the bite, 10 for the harpoon, 12 with an area mark. */
    public static int floor(Move move) { return move == Move.BITE ? 8 : move == Move.HARPOON ? 10 : 12; }
    public static boolean feintable(Move move) {
        return move == Move.BITE || move == Move.TAIL || move == Move.RUSH || move == Move.RIFTDIVE || move == Move.LEAP;
    }
    /**
     * One cast's windup: shorter each phase, longer against weaker parties, and from the second phase every third cast
     * of a feintable move holds its opening pose half again as long. Never under {@link #floor}.
     */
    public static int castWindup(Move move, int phase, int cast, float partyAverage) {
        float scale = phase <= 1 ? 1 : phase == 2 ? .86F : .74F;
        if (phase >= 2 && feintable(move) && cast % 3 == 2) scale *= 1.45F;
        scale *= BossScaling.windupScale(partyAverage);
        return Math.max(floor(move), Math.round(move.windup * scale));
    }
    public static boolean feint(Move move, int phase, int cast) { return phase >= 2 && feintable(move) && cast % 3 == 2; }
    public static boolean heavy(Move move) {
        return move == Move.RIFTDIVE || move == Move.DESCENT || move == Move.RUSH || move == Move.LEAP || move == Move.UNMAKING || move == Move.TAIL;
    }
    /** What each move does before armour, at the first phase; 15% more each phase. */
    public static float damage(Move move, int phase) {
        float base = switch (move) {
            case BITE -> 12; case TAIL -> 13; case BUFFET -> 10; case SCORCH -> 11; case RUSH -> 15; case ROAR -> 6; case LEAP -> 16;
            case TAKEOFF -> 9; case RIFTDIVE -> 16; case BREATH -> 12; case STARFALL -> 9; case HARPOON -> 8; case DESCENT -> 17; case UNMAKING -> 22;
        };
        return base * (1 + .15F * (Math.max(1, phase) - 1));
    }
    /** A burning pool's bite every half second. */
    public static float poolDamage(int phase) { return phase >= 3 ? 4 : 3; }
    /** Moves that drink from a knot while they wind up, from the second phase (the room weapon). */
    public static boolean feeds(Move move, int phase) {
        return phase >= 2 && (move == Move.BREATH || move == Move.UNMAKING || move == Move.LEAP || phase == 2 && move == Move.RIFTDIVE);
    }
    /** Moves a charged hit can't break. */
    public static boolean committed(Move move) { return move == Move.DESCENT || move == Move.TAKEOFF || move == Move.UNMAKING; }

    // ---- Locks ----
    /** What each phase asks: the Seal, Starfall and reach; reach, the feeding knot and Riftbrand; The Unmaking, the star and the knot. */
    public static BossLocks.Lock[] locks(int phase) {
        return switch (Math.max(1, Math.min(3, phase))) {
            case 1 -> new BossLocks.Lock[]{BossLocks.Lock.SHIELD, BossLocks.Lock.PROJECTILES, BossLocks.Lock.OUT_OF_REACH};
            case 2 -> new BossLocks.Lock[]{BossLocks.Lock.OUT_OF_REACH, BossLocks.Lock.ROOM_WEAPON, BossLocks.Lock.CURSE};
            default -> new BossLocks.Lock[]{BossLocks.Lock.UNBLOCKABLE, BossLocks.Lock.WEAK_POINT, BossLocks.Lock.ROOM_WEAPON};
        };
    }
    /** The top of each phase's health: nothing it mends lifts it back into a beaten phase. */
    public static float phaseTop(int phase) { return phase <= 1 ? 1 : phase == 2 ? 2 / 3F : 1 / 3F; }

    // ---- Chains: what it does next, by where its focus stands (never dice) ----
    /** In the air: a chain of air moves, the harpoon first for anyone up high. */
    public static Move[] airChain(int phase, int turn, boolean high) {
        boolean odd = Math.floorMod(turn, 2) == 1;
        if (high) return odd ? new Move[]{Move.HARPOON, Move.STARFALL} : new Move[]{Move.HARPOON, Move.RIFTDIVE};
        if (phase <= 1) return odd ? new Move[]{Move.STARFALL, Move.RIFTDIVE} : new Move[]{Move.BREATH, Move.STARFALL};
        return odd ? new Move[]{Move.RIFTDIVE, Move.RIFTDIVE, Move.BREATH} : new Move[]{Move.STARFALL, Move.BREATH, Move.RIFTDIVE};
    }
    /** On the ground: close (under 9), mid (to 18), far, or up high. */
    public static Move[] groundChain(int phase, int turn, double distance, boolean high) {
        boolean odd = Math.floorMod(turn, 2) == 1;
        if (high) return phase >= 3 ? new Move[]{Move.HARPOON, Move.LEAP} : new Move[]{Move.HARPOON, Move.RUSH};
        if (distance < 9) return switch (phase) {
            case 1 -> odd ? new Move[]{Move.BITE, Move.TAIL, Move.BUFFET} : new Move[]{Move.BUFFET, Move.BITE};
            case 2 -> odd ? new Move[]{Move.BITE, Move.SCORCH, Move.TAIL} : new Move[]{Move.TAIL, Move.BITE, Move.BUFFET};
            default -> odd ? new Move[]{Move.BITE, Move.TAIL, Move.ROAR} : new Move[]{Move.TAIL, Move.BITE, Move.SCORCH, Move.BUFFET};
        };
        if (distance < 18) return switch (phase) {
            case 1 -> odd ? new Move[]{Move.BUFFET, Move.RUSH} : new Move[]{Move.RUSH, Move.BITE};
            case 2 -> odd ? new Move[]{Move.SCORCH, Move.RUSH} : new Move[]{Move.RUSH, Move.SCORCH, Move.BITE};
            default -> odd ? new Move[]{Move.SCORCH, Move.LEAP, Move.BITE} : new Move[]{Move.RUSH, Move.BITE, Move.TAIL};
        };
        return switch (phase) {
            case 1 -> odd ? new Move[]{Move.RUSH, Move.BITE} : new Move[]{Move.RUSH, Move.TAIL};
            case 2 -> odd ? new Move[]{Move.RUSH, Move.SCORCH} : new Move[]{Move.RUSH, Move.BITE, Move.TAIL};
            default -> odd ? new Move[]{Move.LEAP, Move.BITE, Move.TAIL} : new Move[]{Move.LEAP, Move.SCORCH, Move.ROAR};
        };
    }

    // ---- Shapes on the ground ----
    /** The Dragon's forward direction for a yaw, as vanilla flies it. */
    public static Vec3 forward(float yaw) { return EndGeometry.forward(yaw); }
    public static float yaw(Vec3 direction) { return EndGeometry.yaw(direction); }
    /** Its chest, where the Seal's rings orbit and the star opens: the body part's centre. */
    public static Vec3 chest(net.minecraft.world.entity.boss.enderdragon.EnderDragon dragon) { return body(dragon).getBoundingBox().getCenter(); }
    /** The native body's chest part (vanilla keeps the field private; the parts list holds it by name). */
    public static net.minecraft.world.entity.boss.EnderDragonPart body(net.minecraft.world.entity.boss.enderdragon.EnderDragon dragon) {
        for (var part : dragon.getSubEntities()) if ("body".equals(part.name)) return part;
        return dragon.getSubEntities()[2];
    }
}
