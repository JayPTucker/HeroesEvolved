package com.jayptucker.heroesevolved.story.elle;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * The shape of Primatech's test floor, shared by the fight (server) and its drawing (client), so a conduit that lights
 * up is exactly the strip that hurts. Everything is measured from the floor block in the middle of the room; the
 * {@code primatech_substation} blueprint is drawn to the same numbers, and a game test checks its markers against them.
 *
 * <p>Six capacitor banks stand against the walls, numbered clockwise from the north-west. Each has a breaker lever at
 * chest height on its face and a copper conduit in the floor running from under the breaker to the pylon's pad.</p>
 */
public final class PrimatechGeometry {
    public static final int BANK_COUNT = 6;
    /** Each bank's breaker, relative to the middle floor block (x, z); the lever is one block above standing height. */
    private static final int[][] BREAKERS = {{-6, -11}, {6, -11}, {11, 0}, {6, 11}, {-6, 11}, {-11, 0}};
    /** Which way each breaker faces (into the room). */
    private static final Direction[] FACING = {Direction.SOUTH, Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.NORTH, Direction.EAST};
    /** The four rubber mats (3 by 3 of black wool), by their middles. */
    public static final int[][] MATS = {{0, -8}, {0, 8}, {-8, -5}, {8, 5}};
    /** Half the room's floor, from the middle to the inside of a wall. */
    public static final double HALF = 12.5;
    /** The pylon's copper pad reaches this far from the middle; touching it while banks are live hurts. */
    public static final double PYLON = 1.6;
    /** How close to a conduit's strip a surge reaches. */
    public static final double CONDUIT_REACH = 1.35;
    /** How high the ceiling is over the floor block (the lights are in it). */
    public static final double CEILING = 10;

    private PrimatechGeometry() {}

    /** Breaker {@code i}'s cell, relative to the middle floor block: (x, 2, z). */
    public static int[] breaker(int i) { return new int[] {BREAKERS[i][0], 2, BREAKERS[i][1]}; }
    public static Direction facing(int i) { return FACING[i]; }

    /** The middle of bank {@code i}'s face (where its arcs leave), from {@code floor}, the middle floor block's bottom centre. */
    public static Vec3 bank(Vec3 floor, int i) {
        Direction f = FACING[i];
        return floor.add(BREAKERS[i][0] - f.getStepX() * 0.5, 2.5, BREAKERS[i][1] - f.getStepZ() * 0.5);
    }

    /** The top of bank {@code i} (its lightning rod). */
    public static Vec3 rod(Vec3 floor, int i) {
        Direction f = FACING[i];
        return floor.add(BREAKERS[i][0] - f.getStepX(), 4.6, BREAKERS[i][1] - f.getStepZ());
    }

    /** Where bank {@code i}'s conduit starts on the floor (under its breaker), at the floor's surface. */
    public static Vec3 conduitStart(Vec3 floor, int i) { return floor.add(BREAKERS[i][0], 1.02, BREAKERS[i][1]); }

    /** Where bank {@code i}'s conduit reaches the pylon's pad. */
    public static Vec3 conduitEnd(Vec3 floor, int i) {
        double x = BREAKERS[i][0], z = BREAKERS[i][1], m = Math.max(Math.abs(x), Math.abs(z));
        return floor.add(x * 1.6 / m, 1.02, z * 1.6 / m);
    }

    /** The top of the pylon's coil, under the ceiling. */
    public static Vec3 coil(Vec3 floor) { return floor.add(0, 8.5, 0); }

    /** Distance across the floor from {@code at} to bank {@code i}'s conduit strip. */
    public static double fromConduit(Vec3 floor, int i, Vec3 at) {
        Vec3 a = conduitStart(floor, i), b = conduitEnd(floor, i);
        double ax = a.x, az = a.z, bx = b.x - ax, bz = b.z - az, px = at.x - ax, pz = at.z - az;
        double k = Math.clamp((px * bx + pz * bz) / (bx * bx + bz * bz), 0, 1);
        return Math.hypot(px - bx * k, pz - bz * k);
    }

    /** Bank {@code i}'s bit in a mask of live banks. */
    public static int bit(int i) { return 1 << i; }
    public static boolean live(int mask, int i) { return (mask & bit(i)) != 0; }
}
