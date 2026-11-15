package com.jayptucker.heroesevolved.time.intro.city;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The Odessa Arms: a brick walk-up a block west of Primatech Paper, where the Last Day's prologue begins. A lobby on the
 * street and three floors of four apartments, two each side of a hall, with a real stairwell at the hall's west end: two
 * short flights and a half landing a storey, wound round an open well with a rail, under a skylight. Every apartment has
 * a bed under the window, a kitchen, a table, a radio and a name plate by its door. A fire escape climbs the front, a
 * water tank stands on the roof. Drawn from this class alone, so the prologue knows where everyone wakes and which door is
 * theirs.
 * <p>
 * Local grid: x 0..17 west to east, z 0..18 north (the street) to south, outer walls on the edges. Apartments fill
 * z 1..5 and z 13..17 either side of a wall at x 8; the hall runs between them (z 7..11). The stairwell is the hall's west
 * end: a half landing at x 2..3, flights at x 4..5 (going up west on the north side, z 7..8, and back east on the south
 * side, z 10..11, onto a step at x 6 in the floor above), and a rail down the well between them (z 9).
 */
public final class Apartments {
    /** The city block it stands on (one of the 'A' blocks in {@link CityPlan}). */
    public static final int BX = 2, BZ = 7;
    /** The building's north-west corner (outer wall), the street floor, and the height of a storey. */
    public static final int X0 = CityGrid.originX(BX) + CityGrid.LOT_MIN, Z0 = CityGrid.originZ(BZ) + CityGrid.LOT_MIN, Y = CityGrid.STREET_Y,
            STOREY = 5;
    /** Width (x) and depth (z): the lot's width, and a block deeper, into the back sidewalk. */
    public static final int SIZE = 18, DEPTH = 19, FLOORS = 3, PER_FLOOR = 4, UNITS = FLOORS * PER_FLOOR;
    private static final int TOP = Y + STOREY * (FLOORS + 1);
    /** The stairwell: its half landing, its two flights, the step in the floor where each flight arrives, the well. */
    private static final int LANDING_X0 = 2, LANDING_X1 = 3, FLIGHT_X0 = 4, FLIGHT_X1 = 5, ARRIVE_X = 6, WELL_Z = 9;
    private static final int UP_Z0 = 7, UP_Z1 = 8, BACK_Z0 = 10, BACK_Z1 = 11, HALL_Z0 = 7, HALL_Z1 = 11;

    private Apartments() {}

    /** Floor block of storey {@code k} (0 is the lobby). */
    static int base(int k) { return Y + STOREY * k; }

    /** Apartment {@code i}'s interior, as local corners {x0, z0, x1, z1}: north-west, north-east, south-west, south-east. */
    private static int[] room(int i) {
        return switch (i % PER_FLOOR) {
            case 0 -> new int[] {1, 1, 7, 5};
            case 1 -> new int[] {9, 1, 16, 5};
            case 2 -> new int[] {1, 13, 7, 17};
            default -> new int[] {9, 13, 16, 17};
        };
    }

    private static boolean north(int i) { return i % PER_FLOOR < 2; }
    private static int floor(int i) { return 1 + Math.floorMod(i, UNITS) / PER_FLOOR; }

    /** "1A" to "3D". */
    public static String number(int i) { return floor(i) + String.valueOf((char) ('A' + i % PER_FLOOR)); }

    /** The lower half of apartment {@code i}'s door, in the hall wall: at the end of the room nearest the stairs' arrival. */
    public static BlockPos door(int i) {
        int x = i % 2 == 0 ? 7 : 12;
        return new BlockPos(X0 + x, base(floor(i)) + 1, Z0 + (north(i) ? HALL_Z0 - 1 : HALL_Z1 + 1));
    }

    /** The name plate beside the door, on the hall side. */
    public static BlockPos plate(int i) {
        BlockPos door = door(i);
        return new BlockPos(door.getX() + 1, door.getY() + 1, door.getZ() + (north(i) ? 1 : -1));
    }

    /** Which way the plate faces: out into the hall. */
    public static Direction plateFacing(int i) { return north(i) ? Direction.SOUTH : Direction.NORTH; }

    /** Where its resident wakes: on their feet at the foot of the bed. */
    public static Vec3 wake(int i) { return wake(i, 0); }

    /**
     * Where the {@code seat}-th person sharing apartment {@code i} wakes (more than twelve new players at once): the first at
     * the foot of the bed, the next one and two steps further in, clear of the table and chairs.
     */
    public static Vec3 wake(int i, int seat) {
        int[] r = room(i);
        int in = 2 + Math.min(seat, 2);
        int z = north(i) ? r[1] + in : r[3] - in;
        return new Vec3(X0 + r[0] + 1.5, base(floor(i)) + 1, Z0 + z + 0.5);
    }

    /** Which way they face on waking: toward their door. */
    public static float wakeYaw(int i) { return north(i) ? 0 : 180; }

    /** Whether {@code pos} is inside apartment {@code i} (its room, not the hall). */
    public static boolean inside(int i, Vec3 pos) {
        int[] r = room(i);
        int y = base(floor(i));
        return pos.x >= X0 + r[0] && pos.x < X0 + r[2] + 1 && pos.z >= Z0 + r[1] && pos.z < Z0 + r[3] + 1 && pos.y >= y && pos.y < y + STOREY;
    }

    /** The two street doors (their lower halves): shut until everyone has met in the lobby. */
    public static List<BlockPos> frontDoors() { return List.of(new BlockPos(X0 + 8, Y + 1, Z0), new BlockPos(X0 + 9, Y + 1, Z0)); }

    /** Whether {@code pos} is in the lobby, where everyone meets before going out (the hall inside the street doors). */
    public static boolean inLobby(Vec3 pos) {
        return pos.x >= X0 + 6 && pos.x < X0 + 17 && pos.z >= Z0 + 1 && pos.z < Z0 + 12 && pos.y >= Y + 0.5 && pos.y < Y + STOREY;
    }

    /** The middle of the lobby, by the mailboxes. */
    public static Vec3 lobby() { return new Vec3(X0 + 12.5, Y + 1, Z0 + 4.5); }

    /** All twelve doors, for opening and closing them together. */
    public static List<BlockPos> doors() {
        return java.util.stream.IntStream.range(0, UNITS).mapToObj(Apartments::door).toList();
    }

    // ------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------

    static Feature feature() {
        return new Feature(X0 - 2, Z0 - 2, X0 + SIZE + 1, Z0 + DEPTH + 1, Apartments::draw);
    }

    private static int wx(int lx) { return X0 + lx; }
    private static int wz(int lz) { return Z0 + lz; }
    private static void set(CityCanvas c, int lx, int y, int lz, BlockState state) { c.set(wx(lx), y, wz(lz), state); }
    private static void set(CityCanvas c, int lx, int y, int lz, Block block) { c.set(wx(lx), y, wz(lz), block); }
    private static void fill(CityCanvas c, int ax, int ay, int az, int bx, int by, int bz, Block block) {
        c.fill(wx(ax), ay, wz(az), wx(bx), by, wz(bz), block);
    }
    private static void fill(CityCanvas c, int ax, int ay, int az, int bx, int by, int bz, BlockState state) {
        c.fill(wx(ax), ay, wz(az), wx(bx), by, wz(bz), state);
    }

    private static void draw(CityCanvas c) {
        int x1 = SIZE - 1, z1 = DEPTH - 1;
        // Shell: brick, a stone base storey, a stone band at every floor, the floors themselves.
        fill(c, 0, Y, 0, x1, Y, z1, Blocks.SPRUCE_PLANKS);
        fill(c, 0, Y + 1, 0, x1, TOP, z1, Blocks.BRICKS);
        fill(c, 1, Y + 1, 1, x1 - 1, TOP - 1, z1 - 1, Blocks.AIR);
        fill(c, 0, Y + 1, 0, x1, Y + 1, 0, Blocks.STONE_BRICKS);
        for (int k = 1; k <= FLOORS; k++) {
            fill(c, 0, base(k), 0, x1, base(k), z1, Blocks.STONE_BRICKS);
            fill(c, 1, base(k), 1, x1 - 1, base(k), z1 - 1, Blocks.SPRUCE_PLANKS);
        }
        fill(c, 1, TOP, 1, x1 - 1, TOP, z1 - 1, Blocks.SMOOTH_STONE);
        windows(c);
        lobby(c);
        for (int k = 1; k <= FLOORS; k++) storey(c, k);
        stairwell(c);
        for (int i = 0; i < UNITS; i++) apartment(c, i);
        roof(c);
        fireEscape(c);
    }

    /**
     * Two-pane windows on every storey, each with a stone lintel over it and a stone sill under it (an upside-down stair
     * in the wall's face, so the sill's lip throws a shadow), kept clear of the walls inside.
     */
    private static void windows(CityCanvas c) {
        int x1 = SIZE - 1, z1 = DEPTH - 1;
        int[] northSouth = {2, 3, 5, 6, 10, 11, 14, 15};
        int[] eastWest = {2, 3, 4, 8, 9, 10, 14, 15, 16};
        int[] westWall = {2, 3, 4, 14, 15, 16};   // the stairwell's west side is a solid wall
        for (int k = 0; k <= FLOORS; k++) {
            int y = base(k) + 2;
            for (int lx : northSouth) {
                // The entrance and the lobby's own windows take the street storey's front.
                if (k == 0 && lx >= 5 && lx <= 11) continue;
                window(c, lx, y, 0, Direction.NORTH);
                window(c, lx, y, z1, Direction.SOUTH);
            }
            for (int lz : westWall) window(c, 0, y, lz, Direction.WEST);
            for (int lz : eastWest) window(c, x1, y, lz, Direction.EAST);
        }
    }

    private static void window(CityCanvas c, int lx, int y, int lz, Direction out) {
        fill(c, lx, y, lz, lx, y + 1, lz, Blocks.GLASS_PANE);
        set(c, lx, y + 2, lz, Blocks.SMOOTH_SANDSTONE);
        if (y - 1 > Y + 1) set(c, lx, y - 1, lz, CityCanvas.stairs(Blocks.SMOOTH_SANDSTONE_STAIRS, out.getOpposite(), true));
    }

    /** The street floor: a vestibule and hall, mailboxes, a bench, the super's office, a laundry at the back. */
    private static void lobby(CityCanvas c) {
        int y = Y + 1;
        fill(c, 1, Y, 1, 16, Y, 17, Blocks.POLISHED_ANDESITE);
        for (int lx = 7; lx <= 16; lx += 2) for (int lz = 2; lz <= 11; lz += 2) set(c, lx, Y, lz, Blocks.POLISHED_DIORITE);
        // The street doors: shut until everyone has met here (the Last Day opens them), hinged on the outer sides.
        BlockState open = Blocks.DARK_OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH).setValue(DoorBlock.OPEN, false);
        for (int lx : new int[] {8, 9}) {
            BlockState lower = open.setValue(DoorBlock.HINGE, lx == 8 ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT)
                    .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
            set(c, lx, y, 0, lower);
            set(c, lx, y + 1, 0, lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
            set(c, lx, y + 2, 0, Blocks.GLASS_PANE);
        }
        // Wide windows either side of the doors, and a step, a canopy, the name and two lamps on the street.
        for (int lx : new int[] {7, 10}) fill(c, lx, y + 1, 0, lx, y + 2, 0, Blocks.GLASS_PANE);
        for (int lx = 7; lx <= 10; lx++) set(c, lx, Y, -1, CityCanvas.stairs(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH, false));
        for (int lx = 6; lx <= 11; lx++) set(c, lx, y + 3, -1, CityCanvas.slab(Blocks.DARK_OAK_SLAB, false));
        c.sign(wx(9), y + 4, wz(-1), Direction.NORTH, Blocks.DARK_OAK_WALL_SIGN, DyeColor.WHITE, true, "ODESSA ARMS");
        c.sign(wx(8), y + 4, wz(-1), Direction.NORTH, Blocks.DARK_OAK_WALL_SIGN, DyeColor.WHITE, true, "APARTMENTS");
        for (int lx : new int[] {5, 12}) set(c, lx, y + 1, -1, Blocks.WALL_TORCH.defaultBlockState()
                .setValue(net.minecraft.world.level.block.WallTorchBlock.FACING, Direction.NORTH));
        // The super's office in the north-west corner, with its own door off the vestibule.
        fill(c, 6, y, 1, 6, y + STOREY - 2, 5, Blocks.WHITE_TERRACOTTA);
        fill(c, 1, y, 6, 5, y + STOREY - 2, 6, Blocks.WHITE_TERRACOTTA);
        c.door(wx(6), y, wz(3), Blocks.SPRUCE_DOOR, Direction.WEST);
        set(c, 3, y, 3, CityCanvas.stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH, false));
        set(c, 3, y, 2, Blocks.SPRUCE_PLANKS);
        set(c, 3, y + 1, 2, Blocks.LANTERN);
        set(c, 1, y, 5, Blocks.BARREL.defaultBlockState().setValue(net.minecraft.world.level.block.BarrelBlock.FACING, Direction.UP));
        set(c, 3, base(1) - 1, 3, CityCanvas.hangingLantern(Blocks.LANTERN));
        // Mailboxes along the east wall, a bench under the west window of the hall, a fern.
        for (int lz = 5; lz <= 6; lz++) fill(c, 16, y, lz, 16, y + 1, lz, Blocks.BARREL.defaultBlockState()
                .setValue(net.minecraft.world.level.block.BarrelBlock.FACING, Direction.WEST));
        c.sign(wx(15), y + 1, wz(5), Direction.WEST, Blocks.SPRUCE_WALL_SIGN, DyeColor.BLACK, false, "MAIL", "1A-3D");
        for (int lx = 12; lx <= 14; lx++) set(c, lx, y, 1, CityCanvas.stairs(Blocks.SPRUCE_STAIRS, Direction.NORTH, false));
        set(c, 16, y, 1, Blocks.POTTED_FERN);
        // The hall's south wall closes off the laundry at the back, through a door.
        fill(c, 1, y, HALL_Z1 + 1, 16, y + STOREY - 2, HALL_Z1 + 1, Blocks.WHITE_TERRACOTTA);
        c.door(wx(12), y, wz(HALL_Z1 + 1), Blocks.SPRUCE_DOOR, Direction.SOUTH);
        for (int lx = 2; lx <= 6; lx += 2) {
            set(c, lx, y, 17, Blocks.WATER_CAULDRON.defaultBlockState().setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL, 3));
            set(c, lx + 1, y, 17, Blocks.BARREL.defaultBlockState().setValue(net.minecraft.world.level.block.BarrelBlock.FACING, Direction.UP));
        }
        for (int[] at : new int[][] {{4, 15}, {12, 15}}) set(c, at[0], base(1) - 1, at[1], CityCanvas.hangingLantern(Blocks.LANTERN));
        for (int[] at : new int[][] {{9, 3}, {14, 3}, {10, 9}, {14, 9}}) set(c, at[0], base(1) - 1, at[1], CityCanvas.hangingLantern(Blocks.LANTERN));
    }

    /** One upper storey: the walls round the hall and between the apartments, a runner down the hall, lamps. */
    private static void storey(CityCanvas c, int k) {
        int b = base(k);
        Block wall = Blocks.WHITE_TERRACOTTA;
        fill(c, 1, b + 1, HALL_Z0 - 1, 16, b + STOREY - 1, HALL_Z0 - 1, wall);
        fill(c, 1, b + 1, HALL_Z1 + 1, 16, b + STOREY - 1, HALL_Z1 + 1, wall);
        fill(c, 8, b + 1, 1, 8, b + STOREY - 1, HALL_Z0 - 1, wall);
        fill(c, 8, b + 1, HALL_Z1 + 1, 8, b + STOREY - 1, 17, wall);
        // The hall: a dark floor with a runner from the stairs to the far end.
        fill(c, ARRIVE_X + 1, b, HALL_Z0, 16, b, HALL_Z1, Blocks.DARK_OAK_PLANKS);
        fill(c, ARRIVE_X + 2, b + 1, 8, 15, b + 1, 10, Blocks.RED_CARPET);
        for (int lx : new int[] {10, 14}) set(c, lx, b + STOREY - 1, 9, CityCanvas.hangingLantern(Blocks.LANTERN));
        c.sign(wx(15), b + 2, wz(HALL_Z0), Direction.SOUTH, Blocks.SPRUCE_WALL_SIGN, DyeColor.BLACK, false, "FLOOR " + k);
    }

    /**
     * The stairs, one storey at a time from the lobby up: from the hall floor a flight of two goes up west along the north
     * side to the half landing, and a flight of three comes back east along the south side, the last step set into the
     * floor above. A rail runs up the well between the flights, a lamp hangs under every half landing, and the well is open
     * to a skylight in the roof.
     */
    private static void stairwell(CityCanvas c) {
        BlockState up = CityCanvas.stairs(Blocks.SPRUCE_STAIRS, Direction.WEST, false);
        BlockState back = CityCanvas.stairs(Blocks.SPRUCE_STAIRS, Direction.EAST, false);
        // Clear the well from the lobby floor to the roof (the floors above don't cross it).
        for (int k = 1; k <= FLOORS; k++) fill(c, LANDING_X0, base(k), HALL_Z0, FLIGHT_X1, base(k), HALL_Z1, Blocks.AIR);
        // A solid wall behind the half landings, the stair's own floor in the lobby.
        fill(c, 1, Y + 1, HALL_Z0, 1, TOP - 1, HALL_Z1, Blocks.BRICKS);
        fill(c, LANDING_X0, Y, HALL_Z0, ARRIVE_X, Y, HALL_Z1, Blocks.SPRUCE_PLANKS);
        // A lamp over the foot of the stairs in the lobby.
        set(c, ARRIVE_X, base(1) - 1, UP_Z1, CityCanvas.hangingLantern(Blocks.LANTERN));
        for (int k = 0; k < FLOORS; k++) {
            int f = base(k);
            for (int z = UP_Z0; z <= UP_Z1; z++) {
                set(c, FLIGHT_X1, f + 1, z, up);
                set(c, FLIGHT_X0, f + 2, z, up);
            }
            fill(c, LANDING_X0, f + 2, HALL_Z0, LANDING_X1, f + 2, HALL_Z1, Blocks.SPRUCE_PLANKS);
            for (int z = BACK_Z0; z <= BACK_Z1; z++) {
                set(c, FLIGHT_X0, f + 3, z, back);
                set(c, FLIGHT_X1, f + 4, z, back);
                set(c, ARRIVE_X, f + 5, z, back);
            }
            // Under the half landing: the space below it, and a lamp hung from it (none in the lobby, where it is low).
            if (k > 0) set(c, LANDING_X0, f + 1, WELL_Z, CityCanvas.hangingLantern(Blocks.LANTERN));
        }
        // The rail: balusters up the well between the flights, from the lobby to the top floor.
        for (int x = FLIGHT_X0; x <= FLIGHT_X1; x++) fill(c, x, Y + 1, WELL_Z, x, base(FLOORS), WELL_Z, Blocks.DARK_OAK_FENCE);
        // On the top floor the well is open below: a rail along its edge.
        for (int z = HALL_Z0; z <= WELL_Z; z++) set(c, ARRIVE_X, base(FLOORS) + 1, z, Blocks.DARK_OAK_FENCE);
        // The skylight over the well.
        fill(c, LANDING_X0, TOP, HALL_Z0, FLIGHT_X1, TOP, HALL_Z1, Blocks.GLASS);
    }

    /** One apartment: its door and plate, a bed under the window, a kitchen, a table, a radio, a rug, a lamp. */
    private static void apartment(CityCanvas c, int i) {
        int[] r = room(i);
        int b = base(floor(i)), y = b + 1;
        boolean north = north(i);
        Direction out = north ? Direction.NORTH : Direction.SOUTH;
        int outer = north ? r[1] : r[3], inner = north ? r[3] : r[1], step = north ? 1 : -1;
        fill(c, r[0], b, r[1], r[2], b, r[3], Blocks.STRIPPED_SPRUCE_WOOD);
        BlockPos door = door(i);
        c.door(door.getX(), door.getY(), door.getZ(), Blocks.SPRUCE_DOOR, out.getOpposite());
        c.sign(plate(i).getX(), plate(i).getY(), plate(i).getZ(), plateFacing(i), Blocks.SPRUCE_WALL_SIGN, DyeColor.BLACK, false, number(i));
        // The bed, head to the outside wall, under the window.
        int bedX = r[0] + 1;
        BlockState bed = Blocks.BLUE_BED.defaultBlockState().setValue(BedBlock.FACING, out);
        set(c, bedX, y, outer, bed.setValue(BedBlock.PART, BedPart.HEAD));
        set(c, bedX, y, outer + step, bed.setValue(BedBlock.PART, BedPart.FOOT));
        // A nightstand: a raised trapdoor, whose flat top holds the candle.
        set(c, bedX + 1, y, outer, CityCanvas.trapdoor(Blocks.SPRUCE_TRAPDOOR, Direction.NORTH, true, false));
        set(c, bedX + 1, y + 1, outer, Blocks.CANDLE.defaultBlockState().setValue(net.minecraft.world.level.block.CandleBlock.LIT, true));
        // The kitchen along the far wall: stove, sink, counter with a pot on it.
        int kx = r[2];
        set(c, kx, y, outer, Blocks.SMOKER.defaultBlockState().setValue(net.minecraft.world.level.block.SmokerBlock.FACING, Direction.WEST));
        set(c, kx, y, outer + step, Blocks.WATER_CAULDRON.defaultBlockState().setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL, 2));
        set(c, kx, y, outer + 2 * step, Blocks.BARREL.defaultBlockState().setValue(net.minecraft.world.level.block.BarrelBlock.FACING, Direction.UP));
        set(c, kx, y + 1, outer + 2 * step, Blocks.FLOWER_POT);
        // A table with a chair each side, their backs away from it; the radio on a cabinet by the hall wall.
        int tx = (r[0] + r[2]) / 2 + 1, tz = outer + 2 * step;
        set(c, tx, y, tz, Blocks.SPRUCE_FENCE);
        set(c, tx, y + 1, tz, Blocks.SPRUCE_PRESSURE_PLATE);
        set(c, tx - 1, y, tz, CityCanvas.stairs(Blocks.SPRUCE_STAIRS, Direction.WEST, false));
        set(c, tx + 1, y, tz, CityCanvas.stairs(Blocks.SPRUCE_STAIRS, Direction.EAST, false));
        set(c, r[0], y, inner, Blocks.JUKEBOX);
        c.sign(wx(r[0]) + 1, y, wz(inner), Direction.EAST, Blocks.SPRUCE_WALL_SIGN, DyeColor.BLACK, false, "RADIO");
        // A rug and a lamp.
        fill(c, r[0] + 2, b + 1, inner, r[0] + 3, b + 1, inner - step, Blocks.BROWN_CARPET);
        set(c, (r[0] + r[2]) / 2, b + STOREY - 1, (r[1] + r[3]) / 2, CityCanvas.hangingLantern(Blocks.LANTERN));
    }

    /** A cornice of dark wood on brackets, a brick parapet, the water tank, chimneys, the skylight's curb. */
    private static void roof(CityCanvas c) {
        int x1 = SIZE - 1, z1 = DEPTH - 1;
        for (int x = 0; x <= x1; x++) {
            set(c, x, TOP + 1, 0, Blocks.BRICK_WALL);
            set(c, x, TOP + 1, z1, Blocks.BRICK_WALL);
            set(c, x, TOP, -1, CityCanvas.stairs(Blocks.DARK_OAK_STAIRS, Direction.SOUTH, true));
            set(c, x, TOP, z1 + 1, CityCanvas.stairs(Blocks.DARK_OAK_STAIRS, Direction.NORTH, true));
            // Brackets under the cornice on the street front, every other block.
            if (x % 2 == 1) set(c, x, TOP - 1, -1, CityCanvas.trapdoor(Blocks.DARK_OAK_TRAPDOOR, Direction.NORTH, false, true));
        }
        for (int z = 0; z <= z1; z++) {
            set(c, 0, TOP + 1, z, Blocks.BRICK_WALL);
            set(c, x1, TOP + 1, z, Blocks.BRICK_WALL);
            set(c, -1, TOP, z, CityCanvas.stairs(Blocks.DARK_OAK_STAIRS, Direction.EAST, true));
            set(c, x1 + 1, TOP, z, CityCanvas.stairs(Blocks.DARK_OAK_STAIRS, Direction.WEST, true));
        }
        // A curb round the skylight.
        for (int x = LANDING_X0 - 1; x <= FLIGHT_X1 + 1; x++) {
            set(c, x, TOP + 1, HALL_Z0 - 1, CityCanvas.slab(Blocks.STONE_BRICK_SLAB, false));
            set(c, x, TOP + 1, HALL_Z1 + 1, CityCanvas.slab(Blocks.STONE_BRICK_SLAB, false));
        }
        Building.waterTower(c, wx(11), TOP + 1, wz(12));
        for (int lx : new int[] {2, 15}) {
            fill(c, lx, TOP + 1, 16, lx, TOP + 3, 16, Blocks.BRICKS);
            set(c, lx, TOP + 4, 16, Blocks.BRICK_WALL);
        }
    }

    /**
     * Iron fire escapes up the street front, in front of the north-east apartments' windows: a landing at every floor, a
     * rail on its outer edge, posts at both ends up to the landing above.
     */
    private static void fireEscape(CityCanvas c) {
        int a = 12, b = 16;
        for (int k = 1; k <= FLOORS; k++) {
            int y = base(k);
            for (int x = a; x <= b; x++) {
                set(c, x, y, -1, CityCanvas.trapdoor(Blocks.IRON_TRAPDOOR, Direction.NORTH, true, false));
                set(c, x, y, -2, CityCanvas.trapdoor(Blocks.IRON_TRAPDOOR, Direction.NORTH, true, false));
                set(c, x, y + 1, -2, CityCanvas.trapdoor(Blocks.IRON_TRAPDOOR, Direction.SOUTH, false, true));
            }
            for (int x : new int[] {a, b}) {
                set(c, x, y + 1, -1, CityCanvas.trapdoor(Blocks.IRON_TRAPDOOR, x == a ? Direction.EAST : Direction.WEST, false, true));
                if (k < FLOORS) fill(c, x, y + 2, -2, x, y + STOREY - 1, -2, Blocks.IRON_BARS);
            }
        }
    }
}
