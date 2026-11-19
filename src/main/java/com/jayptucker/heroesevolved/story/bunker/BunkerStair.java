package com.jayptucker.heroesevolved.story.bunker;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.GrindstoneBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;

/**
 * The way out of the stasis bunker in the present: the same way the Last Day's survivors came down, a hundred and three
 * years on. Behind the records room's north wall the vault door stands stuck open, cracked and overgrown, and a switchback
 * stair climbs from it to a ruined lobby on the surface, where it comes up through a railed slot in the floor.
 * <p>
 * Everything is relative to the bunker's origin (the records room's floor, under where the old ladder shaft was). The
 * stairwell takes the rock north of the bunker: x -3..15, z -9..0. Two lanes run east and west, the south lane (z -3..-1)
 * beside the vault and the north lane (z -7..-5) behind a solid divider; flights climb along x 3..12 and turn on landings
 * at the west end (x -2..2) and the east end (x 13..14). The vault's frame is the stairwell's south wall (z 0).
 */
final class BunkerStair {
    private BunkerStair() {}

    /** The stair needs this much depth; a shallow bunker (a superflat world) keeps the short ladder shaft. */
    static final int MIN_TOP = 12;
    private static final int X0 = -3, X1 = 15, Z0 = -9, Z1 = 0;
    private static final int FLIGHT_X0 = 3, FLIGHT_X1 = 12, LONGEST = FLIGHT_X1 - FLIGHT_X0 + 1;
    /** The two lanes: the south one beside the vault, the north one behind the divider. */
    private static final int[] SOUTH = {-3, -1}, NORTH = {-7, -5};
    private static final int FLAGS = Block.UPDATE_CLIENTS;

    private static final BlockState AIR = Blocks.AIR.defaultBlockState(), WALL = Blocks.DEEPSLATE_TILES.defaultBlockState(),
            CRACKED = Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState(), FLOOR = Blocks.POLISHED_DEEPSLATE.defaultBlockState(),
            STEEL = Blocks.IRON_BLOCK.defaultBlockState(), FRAME = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();

    static boolean fits(int top) { return top >= MIN_TOP; }

    /** The lobby's footprint round the stair, relative to the bunker origin (directly under spawn). */
    private static final int LX0 = X0 - 1, LX1 = X1 + 1, LZ0 = Z0 - 1, LZ1 = Z1 + 2;
    /** How far round the lobby the ground is graded to meet its floor. */
    private static final int GRADE = 5;

    /** The middle (median) ground height over the lobby's footprint: the surface the lobby's floor is laid at. */
    static int groundLevel(ServerLevel level, BlockPos spawn) {
        java.util.List<Integer> heights = new java.util.ArrayList<>();
        for (int x = LX0; x <= LX1; x++)
            for (int z = LZ0; z <= LZ1; z++)
                heights.add(level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        spawn.getX() + x, spawn.getZ() + z));
        java.util.Collections.sort(heights);
        return heights.get(heights.size() / 2);
    }

    /**
     * Grades the ground round a lobby built before the grading existed (it was cut straight down into the hillside), found
     * by the railed slot at its floor. Leaves the ruin itself alone. Does nothing if there is no such lobby.
     */
    static void regrade(ServerLevel level, BlockPos origin) {
        Builder b = new Builder(level, origin);
        for (int y = MIN_TOP - 1; y < level.getMaxBuildHeight() - origin.getY() - 2; y++)
            for (int[] lane : new int[][] {SOUTH, NORTH})
                if (b.get(FLIGHT_X0 - 1, y, lane[0] - 1).is(Blocks.POLISHED_BLACKSTONE) && b.get(FLIGHT_X0 - 1, y + 1, lane[0] - 1).is(Blocks.IRON_BARS)) {
                    b.grade(y, LX0, LX1, LZ0, LZ1);
                    return;
                }
    }

    /** Builds the stairwell, the vault and the ruined lobby for a bunker whose surface is {@code top} above its floor. */
    static void build(ServerLevel level, BlockPos origin, int top) {
        Builder b = new Builder(level, origin);
        b.stairwell(top);
        b.vault();
        b.lobby(top);
    }

    private static final class Builder {
        final ServerLevel level;
        final BlockPos origin;
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        Builder(ServerLevel level, BlockPos origin) { this.level = level; this.origin = origin; }

        void set(int x, int y, int z, BlockState state) {
            cursor.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
            if (level.isOutsideBuildHeight(cursor) || level.getBlockState(cursor).is(Blocks.BEDROCK)) return;
            level.setBlock(cursor, state, FLAGS);
        }
        void set(int x, int y, int z, Block block) { set(x, y, z, block.defaultBlockState()); }
        BlockState get(int x, int y, int z) {
            return level.getBlockState(cursor.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z));
        }
        int noise(int... v) {
            int h = 0x2545F491;
            for (int i : v) { h ^= i * 0x9E3779B9 + (h << 6) + (h >>> 2); h *= 0x85EBCA6B; h ^= h >>> 13; }
            return h & 0x7FFFFFFF;
        }

        // ------------------------------------------------------------------
        // The stairwell
        // ------------------------------------------------------------------

        void stairwell(int top) {
            int rise = top - 1, flights = (rise + LONGEST - 1) / LONGEST;
            // Solid rock round the whole well first, up to just below the surface, then carve what is walked.
            for (int x = X0; x <= X1; x++)
                for (int z = Z0; z <= Z1; z++)
                    for (int y = -1; y <= top - 2; y++)
                        set(x, y, z, noise(x, y, z) % 11 == 0 ? CRACKED : WALL);
            // The vault landing at the foot, beside the vault only.
            landing(-2, 2, SOUTH[0], SOUTH[1], 0, top);
            int floor = 0;
            for (int i = 1; i <= flights; i++) {
                int length = rise / flights + (i <= rise % flights ? 1 : 0);
                boolean east = i % 2 == 1;
                int[] lane = east ? SOUTH : NORTH;
                flight(lane, east, floor, length, top, i == flights);
                floor += length;
                // The landing it arrives on, across both lanes (the top one is the lobby's floor).
                if (i < flights) {
                    if (east) landing(13, 14, NORTH[0], SOUTH[1], floor, top);
                    else landing(-2, 2, NORTH[0], SOUTH[1], floor, top);
                }
            }
        }

        /** One flight along its lane: level floor first, then the steps up to the landing at the far end. */
        void flight(int[] lane, boolean east, int from, int length, int top, boolean last) {
            for (int x = FLIGHT_X0; x <= FLIGHT_X1; x++) {
                int p = east ? x - FLIGHT_X0 : FLIGHT_X1 - x, k = p - (LONGEST - 1 - length);
                int walk = from + Math.max(k, 0);
                // Wear grows with height, toward the surface the blast and the weather came from: treads chipped to rubble
                // stone, moss on the top flights, stone fallen along one side. The middle of the lane always stays clear, and
                // nothing lies on a tread itself (a carpet on a step stops you climbing it).
                double worn = Math.min(1, Math.max(0, walk / (double) Math.max(1, top - 1)));
                Direction rise = east ? Direction.EAST : Direction.WEST;
                for (int z = lane[0]; z <= lane[1]; z++) {
                    int n = noise(x, walk, z, 3) % 100;
                    if (k > 0) {
                        net.minecraft.world.level.block.Block tread = n < 45 * worn * worn ? (n % 2 == 0 && worn > 0.6
                                ? Blocks.MOSSY_COBBLESTONE_STAIRS : Blocks.COBBLED_DEEPSLATE_STAIRS) : Blocks.POLISHED_DEEPSLATE_STAIRS;
                        set(x, walk, z, tread.defaultBlockState().setValue(StairBlock.FACING, rise));
                    } else set(x, walk, z, n < 40 * worn ? Blocks.COBBLED_DEEPSLATE.defaultBlockState() : FLOOR);
                    // Four blocks of headroom; over the last flight the well stays open to the sky, the slot in the lobby floor.
                    for (int y = walk + 1; y <= (last ? top + 3 : walk + 4); y++) set(x, y, z, AIR);
                    // Fallen stone heaped against the wall side of the lane only.
                    boolean edge = z == (east ? lane[0] : lane[1]);
                    if (edge && noise(x, walk, z, 4) % 100 < 55 * worn) {
                        int pile = noise(x, walk, z, 5) % 3;
                        set(x, walk + 1, z, pile == 0 ? Blocks.COBBLED_DEEPSLATE.defaultBlockState()
                                : pile == 1 ? Blocks.TUFF.defaultBlockState() : Blocks.COBBLED_DEEPSLATE_SLAB.defaultBlockState());
                    }
                }
                // The light the well has now: glow lichen on both walls every other step, and glow berries hanging on vines
                // that came down through the cracks in the ceiling every third.
                if (Math.floorMod(x, 2) == 1)
                    for (int side = 0; side < 2; side++) {
                        int z = side == 0 ? lane[0] : lane[1];
                        Direction toWall = side == 0 ? Direction.NORTH : Direction.SOUTH;
                        int h = walk + 2 + (x + side) % 2;
                        if (get(x, h, z + (side == 0 ? -1 : 1)).isSolid() && get(x, h, z).isAir()) set(x, h, z, lichen(toWall));
                    }
                int middle = (lane[0] + lane[1]) / 2;
                if (!last && Math.floorMod(x, 3) == 0 && get(x, walk + 5, middle).isSolid()) {
                    set(x, walk + 4, middle, Blocks.CAVE_VINES.defaultBlockState()
                            .setValue(net.minecraft.world.level.block.CaveVinesBlock.BERRIES, true));
                    set(x, walk + 5, middle, Blocks.ROOTED_DIRT);
                }
            }
        }

        /** A landing: floor at {@code floor}, four blocks of room above, a dead lamp and lichen on its end wall. */
        void landing(int x0, int x1, int z0, int z1, int floor, int top) {
            for (int x = x0; x <= x1; x++)
                for (int z = z0; z <= z1; z++) {
                    set(x, floor, z, noise(x, floor, z, 5) % 7 == 0 ? Blocks.MOSS_BLOCK.defaultBlockState() : FLOOR);
                    for (int y = floor + 1; y <= floor + 4; y++) set(x, y, z, AIR);
                }
            boolean westEnd = x0 < 0;
            int wallX = westEnd ? X0 : X1;
            Direction toWall = westEnd ? Direction.WEST : Direction.EAST;
            int edgeX = westEnd ? x0 : x1, middle = (z0 + z1) / 2;
            if (floor + 4 < top - 1) {
                set(wallX, floor + 3, middle, Blocks.WAXED_OXIDIZED_COPPER_BULB);
                set(edgeX, floor + 3, middle - 1, lichen(toWall));
                set(edgeX, floor + 3, middle + 1, lichen(toWall));
            }
        }

        BlockState lichen(Direction wall) {
            return Blocks.GLOW_LICHEN.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(wall), true);
        }

        // ------------------------------------------------------------------
        // The vault: stuck open, cracked, grown over
        // ------------------------------------------------------------------

        void vault() {
            // The frame in the stairwell's south wall, round a three by three opening, a crack through its east side.
            for (int x = -2; x <= 2; x++)
                for (int y = 0; y <= 4; y++) {
                    boolean opening = Math.abs(x) <= 1 && y >= 1 && y <= 3;
                    boolean corner = Math.abs(x) == 2 && (y == 0 || y == 4);
                    set(x, y, Z1, opening ? AIR : corner ? Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState()
                            : x == 2 && y == 2 ? Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState() : FRAME);
                }
            set(0, 4, Z1, Blocks.CHISELED_POLISHED_BLACKSTONE);
            // Through to the records room: its north wall opens three wide to meet the vault.
            for (int x = -1; x <= 1; x++) for (int y = 1; y <= 3; y++) set(x, y, Z1 + 1, AIR);
            // The plug stands open off its west hinge, as the last one through left it, and never closed again: a corner
            // sheared off, a bar bent out of the gap, the wheel still on it.
            for (int z = -3; z <= -1; z++)
                for (int y = 1; y <= 3; y++) set(-2, y, z, STEEL);
            set(-2, 3, -3, AIR);
            set(-2, 3, -3, Blocks.IRON_BARS);
            set(-2, 2, -3, Blocks.CHISELED_TUFF);
            set(-1, 2, -2, Blocks.GRINDSTONE.defaultBlockState().setValue(GrindstoneBlock.FACE, AttachFace.WALL)
                    .setValue(GrindstoneBlock.FACING, Direction.EAST));
            // A hundred years of growing in: vines over the frame and hanging across the top of the doorway, on the plug,
            // roots through the ceiling, moss over the floor, lichen glowing on the stone.
            for (int x = -1; x <= 2; x++) {
                set(x, 4, -1, vine(Direction.SOUTH));
                if (x != 0 && noise(x, 9) % 3 != 0) set(x, 3, -1, vine(Direction.SOUTH));
            }
            for (int y = 1; y <= 3; y++) set(2, y, -1, y == 1 ? Blocks.MOSS_CARPET.defaultBlockState() : vine(Direction.SOUTH));
            for (int y = 2; y <= 3; y++) set(-1, y, -1, vine(Direction.WEST));
            set(-1, 1, -3, vine(Direction.WEST));
            for (int x = -1; x <= 2; x++) set(x, 5, -2, Blocks.ROOTED_DIRT);
            set(0, 4, -2, Blocks.HANGING_ROOTS); set(2, 4, -3, Blocks.HANGING_ROOTS);
            set(1, 4, -3, Blocks.CAVE_VINES.defaultBlockState().setValue(net.minecraft.world.level.block.CaveVinesBlock.BERRIES, true));
            for (int[] m : new int[][] {{-1, -2}, {0, -3}, {1, -1}, {2, -2}}) set(m[0], 1, m[1], Blocks.MOSS_CARPET);
            set(0, 0, -1, Blocks.MOSS_BLOCK); set(1, 0, -2, Blocks.MOSS_BLOCK);
            set(1, 1, -2, Blocks.AZALEA);
            // Lichen on the frame's face, either side, where the light from the records room reaches.
            set(-2, 4, -1, lichen(Direction.SOUTH));
            // The sign that greeted the Last Day's survivors, still on the wall beside the frame.
            sign(1, 2, -3, Direction.SOUTH, DyeColor.YELLOW, true, "SUBLEVEL 4", "STASIS WING", "PRIMATECH", "SHELTER");
        }

        BlockState vine(Direction wall) {
            return Blocks.VINE.defaultBlockState().setValue(VineBlock.getPropertyForFace(wall), true);
        }

        void sign(int x, int y, int z, Direction facing, DyeColor color, boolean glow, String... lines) {
            set(x, y, z, Blocks.SPRUCE_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, facing));
            cursor.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
            if (level.getBlockEntity(cursor) instanceof SignBlockEntity sign) {
                SignText text = new SignText().setColor(color).setHasGlowingText(glow);
                for (int i = 0; i < Math.min(4, lines.length); i++) text = text.setMessage(i, Component.literal(lines[i]));
                sign.setText(text, true);
                sign.setWaxed(true);
                sign.setChanged();
            }
        }

        // ------------------------------------------------------------------
        // The ruined lobby on the surface
        // ------------------------------------------------------------------

        void lobby(int top) {
            int y = top - 1;
            int lx0 = LX0, lx1 = LX1, lz0 = LZ0, lz1 = LZ1;
            grade(y, lx0, lx1, lz0, lz1);
            boolean east = ((top - 2 + LONGEST) / LONGEST) % 2 == 1;   // which way the last flight climbs
            int[] lane = east ? SOUTH : NORTH;
            // Clear the ruin's footprint up into the air (trees too; their leaves decay on their own), then stand its floor
            // on a footing that reaches down to the ground wherever the ground dips.
            for (int x = lx0; x <= lx1; x++)
                for (int z = lz0; z <= lz1; z++) {
                    for (int h = y + 1; h <= Math.max(y + 24, standing(x, z)); h++) set(x, h, z, AIR);
                    if (slot(x, z, lane, top)) continue;
                    for (int h = y - 1; h > y - 12; h--) {
                        BlockState below = get(x, h, z);
                        if (!below.isAir() && !below.canBeReplaced() && !below.is(BlockTags.LEAVES) && !below.is(BlockTags.LOGS)) break;
                        set(x, h, z, Blocks.STONE_BRICKS);
                    }
                    int n = noise(x, z, 11) % 23;
                    boolean edge = x == lx0 || x == lx1 || z == lz0 || z == lz1;
                    BlockState tile = n < 2 ? Blocks.MOSS_BLOCK.defaultBlockState() : n < 4 ? Blocks.COARSE_DIRT.defaultBlockState()
                            : n < 6 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState()
                            : (x + z) % 2 == 0 ? Blocks.POLISHED_ANDESITE.defaultBlockState() : Blocks.SMOOTH_STONE.defaultBlockState();
                    set(x, y, z, edge ? Blocks.STONE_BRICKS.defaultBlockState() : tile);
                    if (n < 2 && !edge) set(x, y + 1, z, noise(x, z, 12) % 2 == 0 ? Blocks.SHORT_GRASS : Blocks.MOSS_CARPET);
                    else if (n < 4 && !edge) set(x, y + 1, z, Blocks.DEAD_BUSH);
                }
            // Leaves cut off from their trunk round the edge would hang in the air: clear the loose ones close by.
            for (int x = lx0 - GRADE - 4; x <= lx1 + GRADE + 4; x++)
                for (int z = lz0 - GRADE - 4; z <= lz1 + GRADE + 4; z++) {
                    if (x >= lx0 && x <= lx1 && z >= lz0 && z <= lz1) continue;
                    for (int h = y - 2; h <= y + 16; h++) {
                        BlockState leaf = get(x, h, z);
                        if (leaf.is(BlockTags.LEAVES) && !leaf.getValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT)) set(x, h, z, AIR);
                    }
                }
            walls(lx0, lx1, lz0, lz1, y);
            slotRails(lane, y, east, top);
            // A young tree come up through the floor in the south-east corner.
            tree(lx1 - 3, y, lz1 - 2);
        }

        /**
         * The ground round the ruin, graded to meet its floor: in a ring {@link #GRADE} blocks wide the surface eases from the
         * floor's height at the ruin's edge to the land's own height at the ring's edge, cut back where a hill rose and filled
         * where the ground fell away, topped with whatever the land was topped with (grass, sand, snow). Everything above the
         * new surface goes, overhangs and trees included, so the ruin stands in a clearing instead of at the bottom of a pit.
         */
        void grade(int y, int x0, int x1, int z0, int z1) {
            for (int x = x0 - GRADE; x <= x1 + GRADE; x++)
                for (int z = z0 - GRADE; z <= z1 + GRADE; z++) {
                    if (x >= x0 && x <= x1 && z >= z0 && z <= z1) continue;
                    int d = Math.max(Math.max(x0 - x, x - x1), Math.max(z0 - z, z - z1));
                    int natural = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            origin.getX() + x, origin.getZ() + z) - 1 - origin.getY();
                    BlockState skin = get(x, natural, z);
                    if (!skin.isSolid() || skin.is(BlockTags.LOGS) || skin.is(BlockTags.LEAVES) || !skin.getFluidState().isEmpty())
                        skin = Blocks.GRASS_BLOCK.defaultBlockState();
                    int target = y + Math.round((natural - y) * d / (float) (GRADE + 1));
                    for (int h = target + 1; h <= Math.max(Math.max(natural, target) + 24, standing(x, z)); h++) if (!get(x, h, z).isAir()) set(x, h, z, AIR);
                    for (int h = natural + 1; h < target; h++) set(x, h, z, Blocks.DIRT);
                    set(x, target, z, skin);
                }
        }

        /** The top of whatever stands in a column (trees included), relative to the origin: clear up to here, or a tall
         *  tree's crown is left hanging over the clearing. */
        int standing(int x, int z) {
            return level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, origin.getX() + x, origin.getZ() + z)
                    - origin.getY();
        }

        /** The open slot over the last flight's upper steps, where the stair comes up through the floor. */
        boolean slot(int x, int z, int[] lane, int top) {
            return x >= FLIGHT_X0 && x <= FLIGHT_X1 && z >= lane[0] && z <= lane[1];
        }

        /** What is left of the lobby's walls: pale concrete in a dark frame, broken windows, mostly fallen on the east. */
        void walls(int x0, int x1, int z0, int z1, int y) {
            for (int x = x0; x <= x1; x++)
                for (int z = z0; z <= z1; z++) {
                    boolean north = z == z0, west = x == x0, east = x == x1;
                    if (!north && !west && !east) continue;
                    int along = north ? x - x0 : z - z0;
                    // How much of the wall still stands here: highest at the north-west corner, crumbling away eastward.
                    int standing = west ? 5 - (along > 8 ? 2 : 0) : north ? Math.max(0, 5 - (x - x0) / 4) : 1;
                    standing -= noise(x, z, 21) % 2;
                    boolean pier = along % 4 == 0;
                    boolean[] solid = new boolean[8];
                    for (int h = 1; h <= standing; h++) {
                        boolean window = !pier && h >= 2 && h <= 3 && along % 4 == 2;
                        BlockState block = pier ? Blocks.POLISHED_DEEPSLATE.defaultBlockState()
                                : window ? (noise(x, h, z) % 3 == 0 ? Blocks.GLASS_PANE.defaultBlockState() : AIR)
                                : h == 1 ? Blocks.STONE_BRICKS.defaultBlockState()
                                : noise(x, h, z) % 6 == 0 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState()
                                : Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
                        set(x, y + h, z, block);
                        solid[h] = block.isSolid() && !block.is(Blocks.GLASS_PANE);
                    }
                    // Vines down the inside of what stands, rubble at the foot of what fell.
                    if (standing >= 3 && noise(x, z, 22) % 3 == 0) {
                        Direction wall = north ? Direction.NORTH : west ? Direction.WEST : Direction.EAST;
                        int ix = west ? x + 1 : east ? x - 1 : x, iz = north ? z + 1 : z;
                        for (int h = 2; h <= standing; h++)
                            if (solid[h] && get(ix, y + h, iz).isAir()) set(ix, y + h, iz, vine(wall));
                    }
                    if (standing <= 1 && noise(x, z, 23) % 2 == 0) {
                        int ix = west ? x + 1 : east ? x - 1 : x, iz = north ? z + 1 : z;
                        if (get(ix, y + 1, iz).isAir()) set(ix, y + 1, iz, Blocks.COBBLESTONE_SLAB);
                    }
                }
        }

        /**
         * The slot's edges, as the Last Day's lobby had them: a dark lip and a rail along both sides and the far end, and a
         * hazard stripe and the shelter sign at the head of the stair.
         */
        void slotRails(int[] lane, int y, boolean east, int top) {
            for (int x = FLIGHT_X0 - 1; x <= FLIGHT_X1 + 1; x++)
                for (int z : new int[] {lane[0] - 1, lane[1] + 1}) {
                    set(x, y, z, Blocks.POLISHED_BLACKSTONE);
                    set(x, y + 1, z, Blocks.IRON_BARS);
                }
            // The head of the stair is at the landing end the flight climbs toward; the other end is railed off.
            int head = east ? FLIGHT_X1 + 1 : FLIGHT_X0 - 1, foot = east ? FLIGHT_X0 - 1 : FLIGHT_X1 + 1;
            for (int z = lane[0]; z <= lane[1]; z++) {
                set(head, y, z, (z - lane[0]) % 2 == 0 ? Blocks.YELLOW_CONCRETE : Blocks.BLACK_CONCRETE);
                set(head, y + 1, z, AIR);
                set(foot, y, z, Blocks.POLISHED_BLACKSTONE);
                set(foot, y + 1, z, Blocks.IRON_BARS);
            }
            int postZ = lane[0] - 1, postX = head;
            set(postX, y + 1, postZ, Blocks.POLISHED_BLACKSTONE);
            set(postX, y + 2, postZ, Blocks.POLISHED_BLACKSTONE);
            sign(postX + (east ? 1 : -1), y + 2, postZ, east ? Direction.EAST : Direction.WEST, DyeColor.YELLOW, true,
                    "SUBLEVEL 4", "SHELTER", "STAFF ONLY", "ODESSA SITE");
            // Vines and lichen spilling over the lip and down the well.
            for (int x = FLIGHT_X0; x <= FLIGHT_X1; x += 3)
                for (int d = 1; d <= 3; d++) {
                    int z = lane[0];
                    if (get(x, y - d, z - 1).isSolid() && get(x, y - d, z).isAir()) set(x, y - d, z, vine(Direction.NORTH));
                }
        }

        /** A young oak grown up through the floor. */
        void tree(int x, int y, int z) {
            set(x, y, z, Blocks.ROOTED_DIRT);
            for (int h = 1; h <= 4; h++) set(x, y + h, z, Blocks.OAK_LOG);
            BlockState leaf = Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
            for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++)
                    for (int dy = 3; dy <= 6; dy++) {
                        if (dx == 0 && dz == 0 && dy <= 4) continue;
                        if (dx * dx + dz * dz + (dy - 4.5) * (dy - 4.5) > 6.2) continue;
                        if (get(x + dx, y + dy, z + dz).isAir()) set(x + dx, y + dy, z + dz, leaf);
                    }
        }
    }
}
