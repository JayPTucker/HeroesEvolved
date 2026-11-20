package com.jayptucker.heroesevolved.time.intro.city;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The City Market: a grocery on the avenue, a block south of Odessa Square, where the Last Day sends everyone on an
 * errand before the light. Its street floor faces the avenue (west) through open doors, with produce out on the sidewalk
 * under the awning; inside are aisles of shelves and a checkout counter. Brick apartments rise over it.
 */
public final class Market {
    /** The block it stands on (the 'G' in {@link CityPlan}) and its corners: the north-west of the lot's west part. */
    public static final int BX = 4, BZ = 5;
    public static final int X0 = CityGrid.originX(BX) + CityGrid.LOT_MIN, X1 = X0 + 9;
    public static final int Z0 = CityGrid.originZ(BZ) + CityGrid.LOT_MIN, Z1 = CityGrid.originZ(BZ) + CityGrid.LOT_MAX;
    private static final int Y = CityGrid.STREET_Y;

    private Market() {}

    /** Whether {@code pos} is inside the shop floor. */
    public static boolean inside(Vec3 pos) {
        return pos.x >= X0 + 1 && pos.x < X1 && pos.z >= Z0 + 1 && pos.z < Z1 && pos.y >= Y + 0.5 && pos.y < Y + Building.GROUND;
    }

    /**
     * The middle of the avenue in front of the market, where everyone waits with their groceries and the day's fight comes
     * down: the rubble, the wave and the burst windows are timed and placed from here.
     */
    public static Vec3 beacon() { return new Vec3(4, Y, (Z0 + Z1) / 2.0 + .5); }

    /**
     * The Last Day's checkpoint: on the shop floor in front of the checkout counter, where the errand's groceries are
     * picked up. The light appears up the avenue twenty seconds after the last of them has been here.
     */
    public static Vec3 checkout() { return new Vec3(X0 + 3.5, Y + 1, Z0 + 3.5); }

    /** Whether someone has reached the checkout: inside the shop, within a few steps of the counter. */
    public static boolean arrived(Vec3 pos) {
        Vec3 c = checkout();
        double dx = pos.x - c.x, dz = pos.z - c.z;
        return inside(pos) && dx * dx + dz * dz <= 16;
    }

    /** Whether someone is out front: on the avenue in front of the market, where everyone waits for the others. */
    public static boolean outFront(Vec3 pos) {
        double dx = pos.x - beacon().x, dz = pos.z - beacon().z;
        return dx * dx + dz * dz <= 36 && pos.y < Y + 6;
    }

    /** The lower half of every door on the shop's street floor (for tests and previews). */
    public static java.util.List<BlockPos> doors(net.minecraft.world.level.BlockGetter level) {
        java.util.List<BlockPos> out = new java.util.ArrayList<>();
        for (int x = X0; x <= X1; x++)
            for (int z = Z0; z <= Z1; z++) {
                if (x != X0 && x != X1 && z != Z0 && z != Z1) continue;
                BlockPos pos = new BlockPos(x, Y + 1, z);
                BlockState state = level.getBlockState(pos);
                if (state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.HALF)
                        == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER) out.add(pos);
            }
        return out;
    }

    /** The middle of its avenue front, on the sidewalk. */
    public static Vec3 front() { return new Vec3(X0 - 1.5, Y + 1, (Z0 + Z1) / 2.0 + .5); }

    /** The building itself: four storeys of brick over the shop, its doors open onto the avenue and both cross streets. */
    static Building building(int seed) {
        Building b = new Building(X0, Z0, X1, Z1, 4, CityPlan.brick(seed).with(Building.Ground.SHOP), Building.tiers(4),
                Building.Top.WATER_TOWER, new boolean[] {true, true, true, false}, seed, "CITY MARKET");
        b.doorsOpen = true;
        return b;
    }

    /** Inside and out front, drawn after the building: aisles, the counter, and produce on the sidewalk. */
    static void furnish(CityCanvas c) {
        int y = Y + 1;
        // Aisles: shelves two high running east-west, with things on top.
        for (int z = Z0 + 4; z <= Z1 - 4; z += 4)
            for (int x = X0 + 3; x <= X1 - 2; x++) {
                boolean barrel = (x + z) % 3 == 0;
                c.set(x, y, z, barrel ? Blocks.BARREL.defaultBlockState() : shelf(Direction.NORTH));
                c.set(x, y + 1, z, barrel ? shelf(Direction.SOUTH) : Blocks.BARREL.defaultBlockState());
                if ((x + z) % 2 == 0) c.set(x, y + 2, z, Blocks.FLOWER_POT);
            }
        // The checkout: a counter by the north door, a register on it, a stool behind.
        for (int x = X0 + 2; x <= X0 + 4; x++) {
            c.set(x, y, Z0 + 2, Blocks.SPRUCE_PLANKS);
            c.set(x, y + 1, Z0 + 2, CityCanvas.slab(Blocks.SMOOTH_STONE_SLAB, false));
        }
        c.set(X0 + 3, y + 1, Z0 + 2, Blocks.LOOM);
        c.set(X0 + 3, y, Z0 + 1, CityCanvas.stairs(Blocks.SPRUCE_STAIRS, Direction.NORTH, false));
        // Produce on the sidewalk under the awning, against the display windows, never in front of a door.
        Block[] produce = {Blocks.MELON, Blocks.PUMPKIN, Blocks.HAY_BLOCK, Blocks.BARREL, Blocks.MELON, Blocks.COMPOSTER};
        for (int z = Z0 + 1; z < Z1; z++) {
            BlockPos wall = new BlockPos(X0, y, z);
            boolean door = c.in(X0, z) && c.level.getBlockState(wall).getBlock() instanceof DoorBlock;
            boolean nearDoor = c.in(X0, z) && (c.level.getBlockState(wall.north()).getBlock() instanceof DoorBlock
                    || c.level.getBlockState(wall.south()).getBlock() instanceof DoorBlock);
            if (door || nearDoor || !c.in(X0 - 1, z)) continue;
            Block pick = produce[Math.floorMod(z, produce.length)];
            BlockState state = pick == Blocks.COMPOSTER ? Blocks.COMPOSTER.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.ComposterBlock.LEVEL, 7) : pick.defaultBlockState();
            c.set(X0 - 1, Y, z, state);
        }
    }

    private static BlockState shelf(Direction facing) {
        return Blocks.CHISELED_BOOKSHELF.defaultBlockState().setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, facing);
    }
}
