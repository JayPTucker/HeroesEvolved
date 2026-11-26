package com.jayptucker.heroesevolved.assembly;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The shapes and numbers Matter Assembly's server and its drawing share ({@code AssemblyArt}), so a panel's picture is
 * always exactly its blocks and a pad's glow is exactly where it launches you.
 */
public final class AssemblyGeometry {
    /** Panel: how far it reaches, how far out a mid-air panel stands, how long it lasts, how many you keep. */
    public static final double PANEL_RANGE = 16, PANEL_AIR = 5;
    public static final int PANEL_TICKS = 400, PANELS = 3;
    /** A panel's cells land one a tick after a short wind-up (the drawing's blueprint), so its picture is honest. */
    public static final int PANEL_DELAY = 2, PANEL_RATE = 1;
    /** Looking down past this, a flat panel goes straight under your feet (to catch you) instead of ahead. */
    public static final float UNDER_PITCH = 75;
    /** Looking down more steeply than this lays a panel flat. */
    public static final float FLAT_PITCH = 40;
    /** Panel piece flags, packed into its scale (always 1 or more, inside the client's clamp). */
    public static final int FLAG = 1, FLAT = 2, WINGS = 4;

    /** Launch Pad: reach, the mid-air distance, its life, how many you keep, its Matter, and the throws it gives. */
    public static final double PAD_RANGE = 20, PAD_AIR = 7, PAD_HALF = 1.5;
    public static final int PAD_TICKS = 400, PADS = 2, PAD_MATTER = 4, PAD_REARM = 12;
    public static final double FRIEND_FORWARD = 1.05, FRIEND_UP = 1.2, FOE_UP = 1.35;
    /** How long a thrown foe counts as airborne (Shards hit it harder; turrets want it). */
    public static final int AIRBORNE_TICKS = 40;
    public static final float AIRBORNE_BONUS = 1.5F;

    /** Shards: speed per tick, reach, bounces off your own matter (Bank Shot adds two), the bonus each bounce adds. */
    public static final double SHARD_SPEED = 2.8, SHARD_RANGE = 64;
    public static final int SHARD_BOUNCES = 2, BANK_SHOT_BOUNCES = 2, KALEIDO_BOUNCES = 8;
    public static final float BOUNCE_BONUS = .25F, BANK_SHOT_BONUS = .35F;

    /** Kaleidoscope: the mirror box round a foe, and how long it stands. */
    public static final int KALEIDO_TICKS = 140, KALEIDO_DELAY = 2, KALEIDO_RATE = 2;

    /** When cell {@code index} of a panel lands, in ticks after the cast (the server's schedule and the drawing's). */
    public static float panelArrival(int index) { return PANEL_DELAY + index / (float) PANEL_RATE; }

    /** When cell {@code index} of a Kaleidoscope lands. */
    public static float kaleidoArrival(int index) { return KALEIDO_DELAY + index / (float) KALEIDO_RATE; }

    /**
     * Where a flat panel's middle cell goes: a floor at the level you stand on, two blocks ahead (over a drop, it is
     * the step you take next); looking nearly straight down, right under your feet.
     */
    public static BlockPos floor(Vec3 feet, Direction facing, float pitch) {
        BlockPos under = BlockPos.containing(feet.x, feet.y - .5, feet.z);
        return pitch >= UNDER_PITCH ? under : under.relative(facing, 2);
    }

    private AssemblyGeometry() {}

    /**
     * A panel's cells. Standing, it is three wide and two high across {@code facing}, its lowest middle cell at
     * {@code base}; Buttressed Corners adds a wing folded back at each end. Flat, it is a three-by-three floor
     * centred on {@code base}.
     */
    public static List<BlockPos> panel(BlockPos base, Direction facing, boolean flat, boolean wings) {
        List<BlockPos> cells = new ArrayList<>();
        if (flat) {
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) cells.add(base.offset(x, 0, z));
            return cells;
        }
        Direction side = facing.getClockWise();
        for (int y = 0; y < 2; y++) for (int x = -1; x <= 1; x++) cells.add(base.relative(side, x).above(y));
        if (wings) for (int sign : new int[]{-1, 1}) for (int y = 0; y < 2; y++)
            cells.add(base.relative(side, sign * 2).relative(facing.getOpposite()).above(y));
        return cells;
    }

    /** The scale a panel's piece carries: its shape packed as flags. */
    public static float panelFlags(boolean flat, boolean wings) { return FLAG | (flat ? FLAT : 0) | (wings ? WINGS : 0); }

    /** The space a pad throws from: a flat box over its plate. */
    public static AABB padZone(Vec3 center) {
        return new AABB(center.x - PAD_HALF, center.y - .35, center.z - PAD_HALF, center.x + PAD_HALF, center.y + 1.1, center.z + PAD_HALF);
    }

    /** The cells of a Kaleidoscope: a ring of mirrors two high round {@code center}, open to the sky. */
    public static List<BlockPos> kaleidoscope(BlockPos center) {
        List<BlockPos> cells = new ArrayList<>();
        for (int y = 0; y <= 1; y++) for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
            if (x != 0 || z != 0) cells.add(center.offset(x, y, z));
        return cells;
    }

    /** The damage multiplier a Shard has earned after {@code bounces} ricochets. */
    public static float bounced(int bounces, boolean bankShot) { return 1 + bounces * (bankShot ? BANK_SHOT_BONUS : BOUNCE_BONUS); }
}
