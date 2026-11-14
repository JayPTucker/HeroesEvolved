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
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The Odessa Arms: a brick walk-up a block west of Primatech Paper, where the Last Day's prologue begins. A lobby on the
 * street and three floors of four apartments, two each side of a corridor, with a ladder well at its west end. Every
 * apartment has a bed under the window, a kitchen, a table, a radio and a name plate by its door. Drawn from this
 * class alone, so the prologue knows where everyone wakes and which door is theirs.
 */
public final class Apartments {
    /** The city block it stands on (one of the 'A' blocks in {@link CityPlan}). */
    public static final int BX = 2, BZ = 7;
    /** The building's north-west corner (outer wall), the street floor, and the height of a storey. */
    public static final int X0 = CityGrid.originX(BX) + 12, Z0 = CityGrid.originZ(BZ) + 12, Y = CityGrid.STREET_Y, STOREY = 5;
    public static final int SIZE = 16, FLOORS = 3, PER_FLOOR = 4, UNITS = FLOORS * PER_FLOOR;
    private static final int TOP = Y + STOREY * (FLOORS + 1);

    private Apartments() {}

    /** Floor block of storey {@code k} (0 is the lobby). */
    static int base(int k) { return Y + STOREY * k; }

    /** Apartment {@code i}'s interior, as local corners {x0, z0, x1, z1}: north-west, north-east, south-west, south-east. */
    private static int[] room(int i) {
        return switch (i % PER_FLOOR) {
            case 0 -> new int[] {1, 1, 7, 5};
            case 1 -> new int[] {9, 1, 14, 5};
            case 2 -> new int[] {1, 10, 7, 14};
            default -> new int[] {9, 10, 14, 14};
        };
    }

    private static boolean north(int i) { return i % PER_FLOOR < 2; }
    private static int floor(int i) { return 1 + Math.floorMod(i, UNITS) / PER_FLOOR; }

    /** "1A" to "3D". */
    public static String number(int i) { return floor(i) + String.valueOf((char) ('A' + i % PER_FLOOR)); }

    /** The lower half of apartment {@code i}'s door, in the corridor wall. */
    public static BlockPos door(int i) {
        int[] r = room(i);
        return new BlockPos(X0 + r[0] + 3, base(floor(i)) + 1, Z0 + (north(i) ? 6 : 9));
    }

    /** The name plate beside the door, on the corridor side. */
    public static BlockPos plate(int i) {
        BlockPos door = door(i);
        return new BlockPos(door.getX() + 1, door.getY() + 1, door.getZ() + (north(i) ? 1 : -1));
    }

    /** Which way the plate faces: out into the corridor. */
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

    /** Whether {@code pos} is inside apartment {@code i} (its room, not the corridor). */
    public static boolean inside(int i, Vec3 pos) {
        int[] r = room(i);
        int y = base(floor(i));
        return pos.x >= X0 + r[0] && pos.x < X0 + r[2] + 1 && pos.z >= Z0 + r[1] && pos.z < Z0 + r[3] + 1 && pos.y >= y && pos.y < y + STOREY;
    }

    /** All twelve doors, for opening and closing them together. */
    public static List<BlockPos> doors() {
        return java.util.stream.IntStream.range(0, UNITS).mapToObj(Apartments::door).toList();
    }

    // ------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------

    static Feature feature() {
        return new Feature(X0 - 2, Z0 - 2, X0 + SIZE + 1, Z0 + SIZE + 1, Apartments::draw);
    }

    private static int wx(int lx) { return X0 + lx; }
    private static int wz(int lz) { return Z0 + lz; }

    private static void draw(CityCanvas c) {
        int x1 = X0 + SIZE - 1, z1 = Z0 + SIZE - 1;
        // Shell: brick walls, stone bands at each floor, a cornice and a parapet.
        c.fill(X0, Y, Z0, x1, Y, z1, Blocks.SPRUCE_PLANKS);
        c.fill(X0, Y + 1, Z0, x1, TOP, z1, Blocks.BRICKS);
        c.fill(X0 + 1, Y + 1, Z0 + 1, x1 - 1, TOP - 1, z1 - 1, Blocks.AIR);
        for (int k = 1; k <= FLOORS; k++) {
            c.fill(X0, base(k), Z0, x1, base(k), z1, Blocks.STONE_BRICKS);
            c.fill(X0 + 1, base(k), Z0 + 1, x1 - 1, base(k), z1 - 1, Blocks.SPRUCE_PLANKS);
        }
        c.fill(X0 + 1, TOP, Z0 + 1, x1 - 1, TOP, z1 - 1, Blocks.SMOOTH_STONE);
        for (int x = X0; x <= x1; x++) {
            c.set(x, TOP + 1, Z0, Blocks.BRICK_WALL); c.set(x, TOP + 1, z1, Blocks.BRICK_WALL);
            c.set(x, TOP, Z0 - 1, CityCanvas.stairs(Blocks.DARK_OAK_STAIRS, Direction.SOUTH, true));
            c.set(x, TOP, z1 + 1, CityCanvas.stairs(Blocks.DARK_OAK_STAIRS, Direction.NORTH, true));
        }
        for (int z = Z0; z <= z1; z++) {
            c.set(X0, TOP + 1, z, Blocks.BRICK_WALL); c.set(x1, TOP + 1, z, Blocks.BRICK_WALL);
            c.set(X0 - 1, TOP, z, CityCanvas.stairs(Blocks.DARK_OAK_STAIRS, Direction.EAST, true));
            c.set(x1 + 1, TOP, z, CityCanvas.stairs(Blocks.DARK_OAK_STAIRS, Direction.WEST, true));
        }
        // Windows, two panes tall on every storey, kept clear of the partitions and the corridor.
        for (int k = 0; k <= FLOORS; k++) {
            int y = base(k) + 2;
            for (int lx : new int[] {2, 3, 5, 6, 10, 11, 13}) {
                if (k == 0 && (lx == 6 || lx == 10)) continue;
                for (int z : new int[] {Z0, z1}) c.fill(wx(lx), y, z, wx(lx), y + 1, z, Blocks.GLASS_PANE);
            }
            for (int lz : new int[] {2, 3, 4, 11, 12, 13})
                for (int x : new int[] {X0, x1}) c.fill(x, y, wz(lz), x, y + 1, wz(lz), Blocks.GLASS_PANE);
        }
        lobby(c);
        for (int k = 1; k <= FLOORS; k++) storey(c, k);
        // The ladder well at the corridor's west end, from the lobby to the top floor.
        for (int y = Y + 1; y < base(FLOORS) + 2; y++)
            c.set(wx(1), y, wz(7), Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.EAST));
        for (int k = 1; k <= FLOORS; k++) c.set(wx(1), base(k), wz(7),
                Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.EAST));
        for (int i = 0; i < UNITS; i++) apartment(c, i);
    }

    /** The street floor: open doors, mailboxes, a bench, a sign over the entrance. */
    private static void lobby(CityCanvas c) {
        int y = Y + 1;
        c.fill(wx(1), Y, wz(1), wx(14), Y, wz(14), Blocks.POLISHED_ANDESITE);
        for (int lx = 2; lx <= 13; lx += 2) for (int lz = 2; lz <= 13; lz += 2) c.set(wx(lx), Y, wz(lz), Blocks.POLISHED_DIORITE);
        BlockState open = Blocks.DARK_OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH).setValue(DoorBlock.OPEN, true);
        for (int lx : new int[] {7, 8}) {
            BlockState lower = open.setValue(DoorBlock.HINGE, lx == 7 ? net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT
                    : net.minecraft.world.level.block.state.properties.DoorHingeSide.RIGHT);
            c.set(wx(lx), y, Z0, lower.setValue(DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER));
            c.set(wx(lx), y + 1, Z0, lower.setValue(DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
        }
        // An awning over the steps, and the building's name.
        for (int lx = 6; lx <= 9; lx++) c.set(wx(lx), y + 2, Z0 - 1, CityCanvas.slab(Blocks.DARK_OAK_SLAB, true));
        c.sign(wx(8), y + 3, Z0 - 1, Direction.NORTH, Blocks.DARK_OAK_WALL_SIGN, DyeColor.WHITE, true, "ODESSA ARMS");
        c.sign(wx(7), y + 3, Z0 - 1, Direction.NORTH, Blocks.DARK_OAK_WALL_SIGN, DyeColor.WHITE, true, "APARTMENTS");
        // Mailboxes along the east wall, a bench and a potted fern.
        for (int lz = 3; lz <= 12; lz++) c.fill(wx(14), y, wz(lz), wx(14), y + 1, wz(lz), Blocks.BARREL.defaultBlockState()
                .setValue(net.minecraft.world.level.block.BarrelBlock.FACING, Direction.WEST));
        c.sign(wx(13), y + 1, wz(7), Direction.WEST, Blocks.SPRUCE_WALL_SIGN, DyeColor.BLACK, false, "MAIL", "1A-3D");
        for (int lz = 10; lz <= 12; lz++) c.set(wx(3), y, wz(lz), CityCanvas.stairs(Blocks.SPRUCE_STAIRS, Direction.WEST, false));
        c.set(wx(3), y, wz(13), Blocks.POTTED_FERN);
        for (int[] at : new int[][] {{5, 5}, {10, 5}, {5, 11}, {10, 11}})
            c.set(wx(at[0]), base(1) - 1, wz(at[1]), CityCanvas.hangingLantern(Blocks.LANTERN));
    }

    /** One upper storey: a corridor down the middle, partitions, lamps. */
    private static void storey(CityCanvas c, int k) {
        int b = base(k);
        Block wall = Blocks.WHITE_TERRACOTTA;
        c.fill(wx(1), b + 1, wz(6), wx(14), b + STOREY - 1, wz(6), wall);
        c.fill(wx(1), b + 1, wz(9), wx(14), b + STOREY - 1, wz(9), wall);
        c.fill(wx(8), b + 1, wz(1), wx(8), b + STOREY - 1, wz(5), wall);
        c.fill(wx(8), b + 1, wz(10), wx(8), b + STOREY - 1, wz(14), wall);
        c.fill(wx(2), b, wz(7), wx(14), b, wz(8), Blocks.DARK_OAK_PLANKS);
        c.fill(wx(2), b + 1, wz(7), wx(14), b + 1, wz(8), Blocks.RED_CARPET);
        for (int lx : new int[] {4, 11}) c.set(wx(lx), b + STOREY - 1, wz(7), CityCanvas.hangingLantern(Blocks.LANTERN));
        c.sign(wx(2), b + 2, wz(8), Direction.NORTH, Blocks.SPRUCE_WALL_SIGN, DyeColor.BLACK, false, "FLOOR " + k);
    }

    /** One apartment: its door and plate, a bed under the window, a kitchen, a table, a radio, a rug, a lamp. */
    private static void apartment(CityCanvas c, int i) {
        int[] r = room(i);
        int b = base(floor(i)), y = b + 1;
        boolean north = north(i);
        Direction out = north ? Direction.NORTH : Direction.SOUTH;
        int outer = north ? r[1] : r[3], inner = north ? r[3] : r[1], step = north ? 1 : -1;
        // Walls of plaster, a wood floor.
        c.fill(wx(r[0]), b, wz(r[1]), wx(r[2]), b, wz(r[3]), Blocks.STRIPPED_SPRUCE_WOOD);
        BlockPos door = door(i);
        c.door(door.getX(), door.getY(), door.getZ(), Blocks.SPRUCE_DOOR, out.getOpposite());
        c.sign(plate(i).getX(), plate(i).getY(), plate(i).getZ(), plateFacing(i), Blocks.SPRUCE_WALL_SIGN, DyeColor.BLACK, false, number(i));
        // The bed, head to the outside wall.
        int bedX = wx(r[0] + 1);
        BlockState bed = Blocks.BLUE_BED.defaultBlockState().setValue(BedBlock.FACING, out);
        c.set(bedX, y, wz(outer), bed.setValue(BedBlock.PART, BedPart.HEAD));
        c.set(bedX, y, wz(outer + step), bed.setValue(BedBlock.PART, BedPart.FOOT));
        c.set(bedX + 1, y, wz(outer), Blocks.SPRUCE_TRAPDOOR.defaultBlockState()); // nightstand
        c.set(bedX + 1, y + 1, wz(outer), Blocks.CANDLE.defaultBlockState()
                .setValue(net.minecraft.world.level.block.CandleBlock.LIT, true));
        // The kitchen on the far side: stove, sink, counter.
        int kx = wx(r[2]);
        c.set(kx, y, wz(outer), Blocks.SMOKER.defaultBlockState().setValue(net.minecraft.world.level.block.SmokerBlock.FACING, Direction.WEST));
        c.set(kx, y, wz(outer + step), Blocks.WATER_CAULDRON.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL, 2));
        c.set(kx, y, wz(outer + 2 * step), Blocks.BARREL.defaultBlockState()
                .setValue(net.minecraft.world.level.block.BarrelBlock.FACING, Direction.UP));
        c.set(kx, y + 1, wz(outer + 2 * step), Blocks.FLOWER_POT);
        // A table with two chairs, and the radio on a shelf by the door.
        int tx = wx((r[0] + r[2]) / 2 + 1), tz = wz(outer + 2 * step);
        c.set(tx, y, tz, Blocks.SPRUCE_FENCE);
        c.set(tx, y + 1, tz, Blocks.SPRUCE_PRESSURE_PLATE);
        c.set(tx - 1, y, tz, CityCanvas.stairs(Blocks.SPRUCE_STAIRS, Direction.WEST, false));
        c.set(tx + 1, y, tz, CityCanvas.stairs(Blocks.SPRUCE_STAIRS, Direction.EAST, false));
        c.set(wx(r[0]), y, wz(inner), Blocks.JUKEBOX);
        c.sign(wx(r[0]) + 1, y, wz(inner), Direction.EAST, Blocks.SPRUCE_WALL_SIGN, DyeColor.BLACK, false, "RADIO");
        // A rug and a lamp.
        c.fill(wx(r[0] + 2), b + 1, wz(inner), wx(r[0] + 3), b + 1, wz(inner - step), Blocks.BROWN_CARPET);
        c.set(wx((r[0] + r[2]) / 2), b + STOREY - 1, wz((r[1] + r[3]) / 2), CityCanvas.hangingLantern(Blocks.LANTERN));
    }
}
