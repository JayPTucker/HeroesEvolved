package com.jayptucker.heroesevolved.story.bunker;

import com.jayptucker.heroesevolved.registry.ModBlocks;
import com.jayptucker.heroesevolved.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Builds the Odessa stasis bunker once, under world spawn: a ladder shaft from the surface,
 * then room blueprints: records (with the generator room east and security west, and Noah Bennet's
 * hidden office under security), a corridor with a lab and a breached cell off it, the stasis hall
 * with fourteen Cryochambers, and a ruined hut
 * marking the hatch on the surface. Coordinates are relative to the bunker floor
 * directly below spawn, with +Z running away from the shaft.
 */
public final class BunkerBuilder {
    /** Fourteen pods: seven along each long wall of the chamber. */
    public static final int PODS = 14;
    private static final BlockState WALL = Blocks.DEEPSLATE_TILES.defaultBlockState(),
            AIR = Blocks.AIR.defaultBlockState();

    private final ServerLevel level;
    private final BlockPos origin;
    private final int surface;

    private BunkerBuilder(ServerLevel level, BlockPos origin, int surface) {
        this.level = level; this.origin = origin; this.surface = surface;
    }

    /** A room blueprint and where its anchor lands, relative to the bunker floor under the shaft. */
    public record Room(String blueprint, BlockPos at) {}

    /** The rooms on the bunker's main level. Night Three builds the same rooms as they were on the third night. */
    public static final java.util.List<Room> ROOMS = java.util.List.of(
            new Room("bunker_records", new BlockPos(0, 0, 1)),
            new Room("bunker_corridor", new BlockPos(0, 0, 11)),
            new Room("bunker_lab", new BlockPos(2, 0, 11)),
            new Room("bunker_cell", new BlockPos(-9, 0, 11)),
            new Room("bunker_chamber", new BlockPos(0, 0, 18)),
            new Room("bunker_generator", new BlockPos(5, 0, 1)),
            new Room("bunker_security", new BlockPos(-14, 0, 1)));
    /** Sublevel 4-B, Noah Bennet's office, right under the checkpoint's red rug. Not on any plan. */
    public static final Room OFFICE = new Room("bunker_office", new BlockPos(-14, -6, 3));

    /** Where the stasis hall lands in the bunker. */
    public static final Room CHAMBER = ROOMS.stream().filter(room -> room.blueprint().equals("bunker_chamber")).findFirst().orElseThrow();
    /** Exhibit 1: Isaac's explosion over the city, high on the hall's far wall above the dead screen. Added in layout 2. */
    public static final String EXHIBIT_ONE = "painting:explosion:north";
    /** The last block of the west ceiling beam, cut back in layout 2 so Exhibit 1 fits under the ceiling. */
    public static final BlockPos BEAM_END = new BlockPos(4, 11, 21);
    /** The red ceiling lights over Exhibit 1 (chamber grid cells), with glowstone behind them as elsewhere in the hall. */
    public static final java.util.List<BlockPos> EXHIBIT_LIGHTS = java.util.List.of(new BlockPos(5, 12, 21), new BlockPos(8, 12, 21));

    /**
     * Gives a bunker built under an older layout what later layouts added, and nothing else: no room is
     * rebuilt over a player's changes. Waits until the chamber's entities are loaded (someone is near),
     * since part of it is a painting. Returns true once the bunker is up to date.
     */
    public static boolean upgrade(ServerLevel level, BunkerData data) {
        if (!data.built || data.layout >= BunkerData.LAYOUT) return true;
        var chamber = com.jayptucker.heroesevolved.structure.Blueprint.load(CHAMBER.blueprint());
        BlockPos at = data.origin.offset(CHAMBER.at());
        BlockPos painting = at.offset(chamber.markerOffsets(EXHIBIT_ONE).getFirst());
        if (!level.isPositionEntityTicking(painting)) return false;
        if (data.layout < 2) {
            // The ceiling beam stops one block short of the wall, where the painting's corner hangs.
            BlockPos beam = at.offset(chamber.offset(BEAM_END.getX(), BEAM_END.getY(), BEAM_END.getZ(), net.minecraft.world.level.block.Rotation.NONE));
            if (level.getBlockState(beam).is(Blocks.WAXED_WEATHERED_CUT_COPPER)) level.setBlock(beam, AIR, Block.UPDATE_ALL);
            // Lights over it, only where the ceiling is still the bunker's own brick.
            for (BlockPos cell : EXHIBIT_LIGHTS) {
                BlockPos glass = at.offset(chamber.offset(cell.getX(), cell.getY(), cell.getZ(), net.minecraft.world.level.block.Rotation.NONE));
                if (!level.getBlockState(glass).is(Blocks.DEEPSLATE_BRICKS)) continue;
                level.setBlock(glass.above(), Blocks.GLOWSTONE.defaultBlockState(), Block.UPDATE_ALL);
                level.setBlock(glass, Blocks.RED_STAINED_GLASS.defaultBlockState(), Block.UPDATE_ALL);
            }
            chamber.addMissing(level, at, net.minecraft.world.level.block.Rotation.NONE,
                    marker -> marker.equals(EXHIBIT_ONE) || marker.equals("sign:exhibit1"));
        }
        data.layout = BunkerData.LAYOUT;
        data.setDirty();
        com.jayptucker.heroesevolved.HeroesEvolved.LOGGER.info("Brought the stasis bunker at {} up to layout {}", data.origin, data.layout);
        return true;
    }

    /** Pod {@code index}'s base block, relative to the bunker origin. */
    public static BlockPos podOffset(int index) {
        return new BlockPos(index < 7 ? -5 : 5, 1, 20 + 2 * (index % 7));
    }

    public static Direction podFacing(int index) { return index < 7 ? Direction.EAST : Direction.WEST; }

    /** Builds the bunker below the world's spawn and returns its origin (the floor below the shaft). */
    public static BlockPos build(ServerLevel level) {
        BlockPos spawn = level.getSharedSpawnPos();
        for (int cx = (spawn.getX() - 12) >> 4; cx <= (spawn.getX() + 12) >> 4; cx++)
            for (int cz = (spawn.getZ() - 4) >> 4; cz <= (spawn.getZ() + 40) >> 4; cz++) level.getChunk(cx, cz);
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ());
        // Deep enough to be a secret; on shallow worlds, as deep as the world allows.
        int floor = Math.max(level.getMinBuildHeight() + 2, surface - 32);
        BunkerBuilder builder = new BunkerBuilder(level, new BlockPos(spawn.getX(), floor, spawn.getZ()), surface);
        builder.build();
        return builder.origin;
    }

    private void set(int x, int y, int z, BlockState state) {
        BlockPos pos = origin.offset(x, y, z);
        if (level.isOutsideBuildHeight(pos)) return;
        BlockState current = level.getBlockState(pos);
        if (current.getDestroySpeed(level, pos) < 0 && !current.isAir()) return; // never bedrock
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }

    /** Places a room blueprint with its anchor at the given bunker-relative position, then finishes its markers. */
    private void room(String blueprint, int x, int y, int z) {
        var marked = com.jayptucker.heroesevolved.structure.Blueprint.load(blueprint)
                .place(level, origin.offset(x, y, z), net.minecraft.world.level.block.Rotation.NONE);
        marked.forEach((marker, positions) -> positions.forEach(pos -> finish(marker, pos)));
    }

    /** Marker hooks: books for the lecterns and contents for the chests. */
    private void finish(String marker, BlockPos pos) {
        if (marker.startsWith("log:") && level.getBlockEntity(pos) instanceof LecternBlockEntity lectern) {
            ItemStack book = switch (marker.substring(4)) {
                case "orientation" -> BunkerLogs.orientation();
                case "lockdown" -> BunkerLogs.lockdown();
                case "quiet_years" -> BunkerLogs.quietYears();
                case "helixite" -> BunkerLogs.helixite();
                case "gabriel" -> BunkerLogs.gabrielGray();
                case "bennet" -> BunkerLogs.bennet();
                default -> ItemStack.EMPTY;
            };
            level.setBlock(pos, level.getBlockState(pos).setValue(LecternBlock.HAS_BOOK, true), Block.UPDATE_CLIENTS);
            if (level.getBlockEntity(pos) instanceof LecternBlockEntity filled) filled.setBook(book);
        }
        if (marker.startsWith("loot:") && level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
            ItemStack[] items = switch (marker.substring(5)) {
                case "records" -> new ItemStack[] {BunkerLogs.unfamiliarHand(), new ItemStack(Items.BREAD, 8),
                        new ItemStack(Items.TORCH, 16), new ItemStack(Items.APPLE, 4)};
                case "lab" -> new ItemStack[] {new ItemStack(ModItems.HELIXITE_SHARD.get()),
                        new ItemStack(ModItems.EMPTY_SYRINGE.get(), 2), new ItemStack(Items.GLASS_BOTTLE, 3)};
                case "cell" -> new ItemStack[] {new ItemStack(Items.CLOCK)};
                case "security" -> new ItemStack[] {new ItemStack(Items.IRON_SWORD), new ItemStack(Items.LEATHER_CHESTPLATE),
                        new ItemStack(Items.SHIELD), new ItemStack(Items.COOKED_BEEF, 6), new ItemStack(Items.COMPASS), BunkerLogs.shiftNotes()};
                case "office" -> new ItemStack[] {claireSample(), new ItemStack(ModItems.HELIXITE_SHARD.get(), 2),
                        new ItemStack(ModItems.EMPTY_SYRINGE.get()), new ItemStack(Items.SPYGLASS)};
                default -> new ItemStack[0];
            };
            for (int i = 0; i < items.length; i++) chest.setItem(i * 2, items[i]);
        }
    }

    /** Claire Bennet's blood, donated and kept cold: a strong catalyst for the Formula. */
    public static ItemStack claireSample() {
        ItemStack sample = new ItemStack(ModItems.BLOOD_SAMPLE.get());
        sample.set(ModItems.BLOOD_PURITY.get(), new com.jayptucker.heroesevolved.formula.BloodPurity(true, 3, "Claire Bennet"));
        return sample;
    }

    /** Places pod {@code index}, sealed, with its lamp, cap and pipework. Also used to repair a pod. */
    public static void placePod(ServerLevel level, BlockPos origin, int index, boolean open) {
        BlockPos base = origin.offset(podOffset(index));
        BlockState pod = ModBlocks.CRYOCHAMBER.get().defaultBlockState().setValue(CryoChamberBlock.FACING, podFacing(index))
                .setValue(CryoChamberBlock.OPEN, open);
        level.setBlock(base, pod, Block.UPDATE_CLIENTS);
        level.setBlock(base.above(), pod.setValue(CryoChamberBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER),
                Block.UPDATE_CLIENTS);
    }

    private void build() {
        int top = surface - origin.getY();

        // The shaft: a one-block ladder well from the records room to a trapdoor at the surface.
        for (int y = 0; y < Math.max(top, 4); y++)
            for (int x = -1; x <= 1; x++)
                for (int z = -1; z <= 1; z++) set(x, y, z, x == 0 && z == 0 && y > 0 ? AIR : WALL);
        for (int y = 1; y < top; y++) set(0, y, 0, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
        if (top > 2) set(0, top, 0, Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.HALF, Half.TOP)
                .setValue(TrapDoorBlock.FACING, Direction.SOUTH));

        // The rooms are blueprints in data/heroesevolved/blueprints; see the minecraft-building skill.
        for (Room room : ROOMS) room(room.blueprint(), room.at().getX(), room.at().getY(), room.at().getZ());
        if (origin.getY() + OFFICE.at().getY() > level.getMinBuildHeight())
            room(OFFICE.blueprint(), OFFICE.at().getX(), OFFICE.at().getY(), OFFICE.at().getZ());
        // A ruined hut marks the hatch on the surface, so the way back down can be found.
        if (top > 2) room("bunker_entrance", 0, top, 0);
    }

    /** The cell's interior, for the ticking a visitor hears inside it. */
    public static boolean inCell(BlockPos origin, BlockPos pos) {
        int x = pos.getX() - origin.getX(), y = pos.getY() - origin.getY(), z = pos.getZ() - origin.getZ();
        return x >= -8 && x <= -3 && z >= 12 && z <= 17 && y >= 0 && y <= 4;
    }
}
