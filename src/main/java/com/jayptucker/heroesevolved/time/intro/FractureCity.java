package com.jayptucker.heroesevolved.time.intro;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;

/** A twelve-chunk city with mixed neighbourhoods and a clear viewing avenue. */
public final class FractureCity extends SavedData {
    public static final int MIN = -96, WIDTH = 192, STREET_Y = 64;
    private static final int VERSION = 2;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private int column, version;
    private record Lot(int left, int front, int right, int back, int height, int style, int hash) {}

    public static FractureCity get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(FractureCity::new,
                (tag, registries) -> {
                    FractureCity city = new FractureCity();
                    city.version = tag.getInt("version");
                    return city;
                }), "heroesevolved_fracture_city");
    }
    public boolean ready() { return version == VERSION; }
    public void tickBuild(ServerLevel level) {
        if (ready()) return;
        int end = Math.min(WIDTH * WIDTH, column + 256);
        for (; column < end; column++) buildColumn(level, MIN + column % WIDTH, MIN + column / WIDTH);
        if (column == WIDTH * WIDTH) { version = VERSION; setDirty(); }
    }
    private static Lot lot(int x, int z) {
        int bx = Math.floorDiv(x - MIN, 32), bz = Math.floorDiv(z - MIN, 32);
        int hash = bx * 19 + bz * 31, style = Math.floorMod(bx * 3 + bz * 7, 6);
        return switch (style) {
            case 0 -> new Lot(12, 12, 29, 29, 36 + Math.floorMod(hash, 4) * 4, style, hash);
            case 1 -> new Lot(12, 12, 28, 27, 19 + Math.floorMod(hash, 3) * 5, style, hash);
            case 2 -> new Lot(12, 12, 29, 28, 12, style, hash);
            case 3 -> new Lot(11, 12, 29, 26, 9 + Math.floorMod(hash, 2) * 5, style, hash);
            case 4 -> new Lot(12, 13, 28, 29, 24 + Math.floorMod(hash, 3) * 5, style, hash);
            default -> new Lot(12, 12, 29, 29, 0, style, hash);
        };
    }
    static void buildColumn(ServerLevel level, int x, int z) {
        // Replace old columns completely so saved cities upgrade and interrupted builds are safe to repeat.
        int oldTop = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        for (int y = STREET_Y; y < oldTop; y++) {
            if (!level.getBlockState(new BlockPos(x, y, z)).isAir()) put(level, x, y, z, Blocks.AIR);
        }
        int lx = Math.floorMod(x - MIN, 32), lz = Math.floorMod(z - MIN, 32);
        Lot lot = lot(x, z);
        boolean road = lx < 8 || lz < 8;
        Block surface = road ? Blocks.GRAY_CONCRETE : Blocks.SMOOTH_STONE;
        if (road && ((lx == 3 && lz >= 12 && lz % 8 < 5)
                || (lz == 3 && lx >= 12 && lx % 8 < 5))) surface = Blocks.YELLOW_CONCRETE;
        if (road && ((lz == 9 && lx % 2 == 0) || (lx == 9 && lz % 2 == 0)
                || (lz == 11 && lx < 7) || (lx == 11 && lz < 7))) surface = Blocks.WHITE_CONCRETE;
        if ((lx == 6 && lz == 22) || (lz == 6 && lx == 22)) surface = Blocks.POLISHED_DEEPSLATE;
        if (!road && (lx == 8 || lz == 8)) surface = Blocks.ANDESITE;
        put(level, x, 63, z, surface);
        if (lot.style == 5) park(level, x, z, lx, lz);
        else building(level, x, z, lx, lz, lot);
        streetFurniture(level, x, z, lx, lz, lot);
        car(level, x, z, lx, lz, lot.hash);
    }
    private static void building(ServerLevel level, int x, int z, int lx, int lz, Lot lot) {
        if (lx < lot.left || lx > lot.right || lz < lot.front || lz > lot.back) {
            if (lz == lot.front - 1 && lx >= lot.left + 2 && lx <= lot.right - 2
                    && (lot.style == 1 || lot.style == 3)) {
                put(level, x, 67, z, lot.hash % 2 == 0 ? Blocks.GREEN_CONCRETE : Blocks.RED_TERRACOTTA);
                if (lx == (lot.left + lot.right) / 2) {
                    put(level, x, 68, z, Blocks.DARK_OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, Direction.NORTH));
                    String shop = switch (Math.floorMod(lot.hash, 4)) {
                        case 0 -> "CORNER CAFE"; case 1 -> "PHARMACY";
                        case 2 -> "CITY MARKET"; default -> "BOOKS & NEWS";
                    };
                    sign(level, new BlockPos(x, 68, z), shop, lot.style == 1 ? "APARTMENTS ABOVE" : "EST. 1984");
                }
            }
            return;
        }
        int unit = (lx - lot.left) / 6;
        int height = lot.style == 2 ? 11 + Math.floorMod(unit + lot.hash, 2) * 4 : lot.height;
        int pitch = lot.style == 0 ? 4 : 5;
        for (int h = 0; h <= height; h++) {
            int inset = lot.style == 0 && h > 11 ? 2 : lot.style == 4 && h > 18 ? (h > 27 ? 2 : 1) : 0;
            int left = lot.left + inset, right = lot.right - inset;
            int front = lot.front + inset, back = lot.back - inset;
            if (lx < left || lx > right || lz < front || lz > back) continue;
            boolean wall = lx == left || lx == right || lz == front || lz == back
                    || lot.style == 2 && (lx - lot.left) % 6 == 0;
            boolean floor = h > 0 && (h % pitch == pitch - 1 || h == height
                    || lot.style == 4 && (h == 18 || h == 27));
            if (!wall && !floor) {
                if (h % pitch == 2 && lz == front + 2 && (lx - left) % 6 == 3)
                    put(level, x, 64 + h, z, Blocks.OCHRE_FROGLIGHT);
                continue;
            }
            boolean entrance = lz == front && h < 2 && (lot.style == 2
                    ? (lx - lot.left) % 6 == 2 : lx == (left + right) / 2);
            if (entrance) continue;
            int along = lx == left || lx == right ? lz - front : lx - left;
            boolean window = wall && h % pitch >= 1 && h % pitch < pitch - 1 && along % 4 != 0;
            Block material;
            if (window) material = lot.style == 0 ? Blocks.LIGHT_BLUE_STAINED_GLASS
                    : Math.floorMod(along + h / pitch + lot.hash, 7) == 0
                    ? Blocks.YELLOW_STAINED_GLASS : Blocks.GRAY_STAINED_GLASS;
            else if (floor) material = lot.style == 1 || lot.style == 2 ? Blocks.SMOOTH_SANDSTONE : Blocks.SMOOTH_STONE;
            else material = switch (lot.style) {
                case 0 -> along % 4 == 0 ? Blocks.GRAY_CONCRETE : Blocks.LIGHT_GRAY_CONCRETE;
                case 1 -> Blocks.BRICKS;
                case 2 -> unit % 2 == 0 ? Blocks.BRICKS : Blocks.BROWN_TERRACOTTA;
                case 3 -> Blocks.SMOOTH_QUARTZ;
                default -> along % 4 == 0 ? Blocks.SMOOTH_SANDSTONE : Blocks.TERRACOTTA;
            };
            put(level, x, 64 + h, z, material);
        }
        if (lot.style == 2) {
            int slope = Math.min(lz - lot.front, lot.back - lz) / 3;
            put(level, x, 64 + height + 1 + slope, z, Blocks.DEEPSLATE_BRICK_SLAB);
            if ((lx - lot.left) % 6 == 4 && lz == lot.back - 2)
                for (int h = 0; h < 3; h++) put(level, x, 64 + height + slope + 2 + h, z, Blocks.BRICKS);
            return;
        }
        int inset = lot.style == 0 ? 2 : lot.style == 4 && height > 27 ? 2 : lot.style == 4 ? 1 : 0;
        if (lx < lot.left + inset || lx > lot.right - inset || lz < lot.front + inset || lz > lot.back - inset) return;
        if (lx == lot.left + inset || lx == lot.right - inset || lz == lot.front + inset || lz == lot.back - inset)
            put(level, x, 65 + height, z, Blocks.SMOOTH_STONE_SLAB);
        int cx = (lot.left + lot.right) / 2;
        if (Math.abs(lx - cx) <= 1 && lz >= lot.back - inset - 4 && lz <= lot.back - inset - 2) {
            put(level, x, 65 + height, z, Blocks.POLISHED_ANDESITE);
            put(level, x, 66 + height, z, Blocks.IRON_TRAPDOOR);
        }
        if (lx >= lot.left + inset + 2 && lx <= lot.left + inset + 4 && lz >= lot.front + inset + 2 && lz <= lot.front + inset + 4) {
            put(level, x, 65 + height, z, Blocks.STONE_BRICKS);
            put(level, x, 66 + height, z, Blocks.SMOOTH_STONE_SLAB);
        }
        if (lot.style == 1 && lz == lot.back && lx >= lot.right - 3 && lx < lot.right)
            for (int h = 4; h < height; h += 5) put(level, x, 64 + h, z, Blocks.IRON_TRAPDOOR);
    }
    private static void park(ServerLevel level, int x, int z, int lx, int lz) {
        if (lx < 11 || lz < 11 || lx > 30 || lz > 30) return;
        put(level, x, 63, z, lx == 20 || lz == 20 ? Blocks.GRAVEL : Blocks.GRASS_BLOCK);
        if (lx >= 18 && lx <= 22 && lz >= 18 && lz <= 22) {
            boolean edge = lx == 18 || lx == 22 || lz == 18 || lz == 22;
            put(level, x, 63, z, Blocks.SMOOTH_STONE);
            put(level, x, 64, z, edge ? Blocks.STONE_BRICKS : Blocks.WATER);
            if (lx == 20 && lz == 20) {
                put(level, x, 64, z, Blocks.POLISHED_ANDESITE);
                put(level, x, 65, z, Blocks.ANDESITE_WALL);
                put(level, x, 66, z, Blocks.WATER_CAULDRON.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL, 3));
            }
        }
        for (int[] tree : new int[][] {{14, 14}, {27, 15}, {14, 27}, {27, 27}}) {
            int dx = lx - tree[0], dz = lz - tree[1];
            if (dx == 0 && dz == 0) for (int h = 64; h < 69; h++) put(level, x, h, z, Blocks.OAK_LOG);
            for (int h = 67; h <= 71; h++)
                if (dx * dx + dz * dz + (h - 69) * (h - 69) <= 11)
                    put(level, x, h, z, Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true));
        }
        if ((lz == 16 || lz == 24) && lx >= 18 && lx <= 21)
            put(level, x, 64, z, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING,
                    lz == 16 ? Direction.NORTH : Direction.SOUTH));
        if (lz == 12 && lx >= 18 && lx <= 22) put(level, x, 64, z, Blocks.AZALEA_LEAVES);
    }
    private static void streetFurniture(ServerLevel level, int x, int z, int lx, int lz, Lot lot) {
        if (lz == 10 && (lx == 9 || lx == 10)) {
            if (lx == 9) for (int h = 64; h < 70; h++) put(level, x, h, z, Blocks.IRON_BARS);
            put(level, x, 70, z, lx == 10 ? Blocks.SEA_LANTERN : Blocks.POLISHED_ANDESITE_SLAB);
        }
        if (lx == 30 && lz == 9) {
            for (int h = 64; h < 68; h++) put(level, x, h, z, Blocks.IRON_BARS);
            put(level, x, 68, z, Blocks.GRAY_CONCRETE);
            put(level, x, 69, z, Blocks.RED_STAINED_GLASS);
            put(level, x, 70, z, Blocks.GRAY_CONCRETE);
        }
        if (lx == 10 && lz == 25) {
            put(level, x, 64, z, Blocks.RED_CONCRETE);
            put(level, x, 65, z, Blocks.STONE_BUTTON);
        }
        if (lx == 10 && lz == 28) put(level, x, 64, z, Blocks.CAULDRON);
        if (lx == 29 && lz == 10) {
            put(level, x, 64, z, Blocks.IRON_BARS);
            put(level, x, 65, z, Blocks.IRON_BARS);
            put(level, x, 66, z, Blocks.SPRUCE_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, Direction.NORTH));
            sign(level, new BlockPos(x, 66, z), "EAST " + (Math.floorDiv(z - MIN, 32) + 1), "AVENUE");
        }
        if (lot.hash % 11 == 2 && lx >= 9 && lx <= 11 && lz >= 16 && lz <= 22) {
            if (lx == 11 && (lz == 16 || lz == 22)) for (int h = 64; h < 67; h++) put(level, x, h, z, Blocks.GRAY_STAINED_GLASS_PANE);
            put(level, x, 67, z, Blocks.SMOOTH_STONE_SLAB);
            if (lx == 11 && lz > 17 && lz < 21) put(level, x, 64, z,
                    Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
        }
    }
    private static void car(ServerLevel level, int x, int z, int lx, int lz, int hash) {
        if (x >= -8 || lx < 1 || lx > 2 || lz < 17 || lz > 22 || hash % 3 == 0) return;
        Block paint = switch (Math.floorMod(hash, 4)) {
            case 0 -> Blocks.WHITE_CONCRETE; case 1 -> Blocks.BLUE_TERRACOTTA;
            case 2 -> Blocks.RED_TERRACOTTA; default -> Blocks.GRAY_CONCRETE;
        };
        put(level, x, 64, z, lz == 18 || lz == 21 ? Blocks.BLACK_CONCRETE : paint);
        put(level, x, 65, z, lz == 17 ? Blocks.SEA_LANTERN : lz == 22 ? Blocks.RED_STAINED_GLASS : paint);
        if (lz >= 19 && lz <= 20) {
            put(level, x, 66, z, Blocks.LIGHT_GRAY_STAINED_GLASS);
            put(level, x, 67, z, Blocks.SMOOTH_STONE_SLAB);
        }
    }
    private static void sign(ServerLevel level, BlockPos pos, String first, String second) {
        if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
            sign.setText(new SignText().setColor(DyeColor.WHITE)
                    .setMessage(1, Component.literal(first)).setMessage(2, Component.literal(second)), true);
            sign.setChanged();
        }
    }
    private static void put(ServerLevel level, int x, int y, int z, Block block) { put(level, x, y, z, block.defaultBlockState()); }
    private static void put(ServerLevel level, int x, int y, int z, BlockState state) { level.setBlock(new BlockPos(x, y, z), state, FLAGS); }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("version", version);
        return tag;
    }
}
