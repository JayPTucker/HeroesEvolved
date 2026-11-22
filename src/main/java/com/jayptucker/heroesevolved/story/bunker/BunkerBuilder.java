package com.jayptucker.heroesevolved.story.bunker;

import com.jayptucker.heroesevolved.registry.ModBlocks;
import com.jayptucker.heroesevolved.registry.ModItems;
import com.jayptucker.heroesevolved.structure.Blueprint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;

/**
 * Builds the Odessa stasis bunker once, under world spawn: Sublevel 4 as {@link BunkerLayout#STASIS} lays it out (the
 * airlock behind the vault, the commons, the stasis hall with fourteen Cryochambers, records, the generator, the
 * infirmary and containment, maintenance under the airlock, and the sealed Helix Ward with its one private pod), then the
 * way out north of the airlock: the vault and the switchback stair up to a ruined lobby on the surface
 * ({@link BunkerStair}). Coordinates are relative to the bunker floor directly below spawn, in front of the vault, with +Z
 * running away from the stair.
 * <p>
 * Worlds built before the rebuild keep their bunker ({@link BunkerLayout#LEGACY}); the room constants below and
 * {@link #upgrade} are theirs.
 */
public final class BunkerBuilder {
    /** Fourteen pods in the hall: seven along each long wall. New sleepers are spread over these (the lab's is chosen). */
    public static final int PODS = BunkerLayout.HALL_PODS;
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

    // The original bunker's rooms (BunkerLayout.LEGACY), kept for the worlds that have them.

    static final Room RECORDS = new Room("bunker_records", new BlockPos(0, 0, 1));
    static final Room CORRIDOR = new Room("bunker_corridor", new BlockPos(0, 0, 11));
    static final Room LAB = new Room("bunker_lab", new BlockPos(2, 0, 11));
    static final Room INFIRMARY = new Room("bunker_cell", new BlockPos(-9, 0, 11));
    public static final Room CHAMBER = new Room("bunker_chamber", new BlockPos(0, 0, 18));
    static final Room SECURITY = new Room("bunker_security", new BlockPos(-14, 0, 1));

    /** The original bunker's rooms on its main level. */
    public static final List<Room> ROOMS = List.of(RECORDS, CORRIDOR, LAB, INFIRMARY, CHAMBER,
            new Room("bunker_generator", new BlockPos(5, 0, 1)),
            SECURITY);
    /** The original bunker's maintenance room, under its security checkpoint. */
    public static final Room OFFICE = new Room("bunker_office", new BlockPos(-14, -6, 3));

    /** The receiver: Simple Radio's, or the copper box an older bunker was built with. */
    public static boolean isReceiver(BlockState state) {
        return state.is(Blocks.WAXED_WEATHERED_CUT_COPPER)
                || "simpleradio:receiver".equals(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
    }

    /**
     * Swaps an older bunker's receiver (a copper box, tinted glass, a lightning rod and a bulb) for the radio console the
     * records blueprint has now, cell by cell, only where the old pieces are still there.
     */
    private static void radioConsole(ServerLevel level, BlockPos origin) {
        var records = Blueprint.load(RECORDS.blueprint());
        var states = records.statesMatching(state -> true);
        BlockPos at = origin.offset(RECORDS.at());
        for (String marker : List.of("signal:receiver", "signal:display", "signal:speaker", "signal:microphone"))
            for (BlockPos cell : records.markerOffsets(marker)) {
                BlockPos pos = at.offset(cell);
                BlockState now = level.getBlockState(pos);
                boolean old = now.isAir() || now.is(Blocks.WAXED_WEATHERED_CUT_COPPER) || now.is(Blocks.TINTED_GLASS)
                        || now.is(Blocks.LIGHTNING_ROD) || now.is(Blocks.WAXED_WEATHERED_COPPER_BULB);
                if (old && states.get(cell) != null) level.setBlock(pos, states.get(cell), Block.UPDATE_ALL);
            }
    }

    public static BlockPos receiverPos(BunkerData data) { return data.origin.offset(data.layout().receiver()); }

    public static List<BlockPos> survivorPositions(BunkerData data) {
        return data.layout().survivors().stream().map(data.origin::offset).toList();
    }
    /**
     * Exhibit 1: Isaac's explosion over the city, on the hall's far wall above the analysts' desk.
     * Added in layout 2, high on the wall over a dead screen; layout 3 hangs it in the screen's place.
     */
    public static final String EXHIBIT_ONE = "painting:explosion:north";
    /** Marks the alarm beacons in the room blueprints, so a bunker built before them gets them (layout 4). */
    public static final String BEACON = "beacon";
    /** Where layout 2 hung Exhibit 1 (the painting's anchor, as a chamber grid cell). */
    public static final BlockPos FIRST_EXHIBIT = new BlockPos(7, 9, 21);
    /** Layout 2's signs on the far wall: Exhibit 1's label and the dead screen's "SIGNAL LOST". */
    public static final java.util.List<BlockPos> FIRST_SIGNS = java.util.List.of(new BlockPos(10, 8, 21), new BlockPos(7, 7, 21));
    static final List<String> OLD_EXHIBIT_LABEL = List.of("EXHIBIT 1", "I. MENDEZ", "THE LAST DAY", "IT CAME TRUE");
    static final List<String> OLD_SCREEN_LABEL = List.of("SIGNAL LOST", "2,190 DAYS", "SINCE LAST", "CONTACT");
    /** The dead screen and its frame (chamber grid), one polished panel behind Exhibit 1 from layout 3. */
    public static final net.minecraft.world.level.levelgen.structure.BoundingBox SCREEN =
            new net.minecraft.world.level.levelgen.structure.BoundingBox(3, 1, 22, 10, 7, 22);
    private static final List<String> OLD_SCREEN = List.of(
            "DPPPPPPP", "DPYYYYYP", "DPBYBYBP", "DPBBOBBP", "DPBOOOBP", "DPYYYYYP", "DPPPPPPP");
    /** The last block of the west ceiling beam: cut back in layout 2 for Exhibit 1's corner, whole again in 3. */
    public static final BlockPos BEAM_END = new BlockPos(4, 11, 21);
    /** The red ceiling lights over the far wall (chamber grid cells), with glowstone behind them as elsewhere in the hall. */
    public static final java.util.List<BlockPos> EXHIBIT_LIGHTS = java.util.List.of(new BlockPos(5, 12, 21), new BlockPos(8, 12, 21));

    /**
     * Updates the original fixtures in a bunker built under an older layout, where they still match.
     * Waits until the rooms and their entities are loaded (someone is near),
     * since part of it is a painting. Returns true once the bunker is up to date.
     */
    public static boolean upgrade(ServerLevel level, BunkerData data) {
        if (!data.built || data.layout >= BunkerData.LAYOUT || data.layout().stasis()) return true;
        var chamber = Blueprint.load(CHAMBER.blueprint());
        BlockPos at = data.origin.offset(CHAMBER.at());
        BlockPos painting = at.offset(chamber.markerOffsets(EXHIBIT_ONE).getFirst());
        if (!level.isPositionEntityTicking(painting) || !level.isPositionEntityTicking(at.offset(cell(chamber, FIRST_EXHIBIT)))
                || !BunkerLayoutUpgrade.ready(level, data.origin)) return false;
        if (data.layout < 2) {
            // Lights over the far wall, only where the ceiling is still the bunker's own brick.
            for (BlockPos light : EXHIBIT_LIGHTS) {
                BlockPos glass = at.offset(cell(chamber, light));
                if (!level.getBlockState(glass).is(Blocks.DEEPSLATE_BRICKS)
                        || !level.getBlockState(glass.above()).is(Blocks.DEEPSLATE_BRICKS)) continue;
                BlockState glow = Blocks.GLOWSTONE.defaultBlockState();
                level.setBlock(glass.above(), data.powered ? glow : BunkerPower.unpowered(0, glow), Block.UPDATE_ALL);
                level.setBlock(glass, Blocks.RED_STAINED_GLASS.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        if (data.layout < 3) {
            // Exhibit 1 comes down from high on the wall, with its label and the screen's sign.
            retireFirstExhibit(level, at);
            BlockPos oldExhibit = at.offset(cell(chamber, FIRST_SIGNS.getFirst()));
            BlockPos oldScreen = at.offset(cell(chamber, FIRST_SIGNS.getLast()));
            if (BunkerLayoutUpgrade.originalSign(level, oldExhibit, OLD_EXHIBIT_LABEL) != null)
                level.setBlock(oldExhibit, AIR, Block.UPDATE_ALL);
            if (BunkerLayoutUpgrade.originalSign(level, oldScreen, OLD_SCREEN_LABEL) != null)
                level.setBlock(oldScreen, AIR, Block.UPDATE_ALL);
            BlockPos beam = at.offset(cell(chamber, BEAM_END));
            if (level.getBlockState(beam).isAir()) level.setBlock(beam, Blocks.WAXED_WEATHERED_CUT_COPPER.defaultBlockState(), Block.UPDATE_ALL);
            // The screen's warning blocks and the wall beside it become one panel, where they are still the bunker's own.
            BlockState panel = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
            for (BlockPos grid : BlockPos.betweenClosed(SCREEN.minX(), SCREEN.minY(), SCREEN.minZ(), SCREEN.maxX(), SCREEN.maxY(), SCREEN.maxZ())) {
                BlockPos pos = at.offset(cell(chamber, grid));
                if (level.getBlockState(pos).equals(oldScreenBlock(grid)))
                    level.setBlock(pos, panel, Block.UPDATE_ALL);
            }
        }
        // Exhibit 1 and its label where the blueprint has them now, unless something already stands there.
        if (data.layout < 4) {
            chamber.addMissing(level, at, Rotation.NONE, marker -> marker.equals(EXHIBIT_ONE) || marker.equals("sign:exhibit1"));
            // The alarm beacons, in every room that has them, wherever their spot is still empty.
            for (Room room : ROOMS)
                Blueprint.load(room.blueprint()).addMissing(level, data.origin.offset(room.at()), Rotation.NONE, BEACON::equals);
        }
        BunkerLayoutUpgrade.apply(level, data);
        // The receiver becomes a Simple Radio console.
        if (data.layout < 6) {
            radioConsole(level, data.origin);
            // The ground round the ruined lobby at spawn, graded to meet its floor instead of standing as a pit's walls.
            BunkerStair.regrade(level, data.origin);
        }
        data.layout = BunkerData.LAYOUT;
        data.setDirty();
        com.jayptucker.heroesevolved.HeroesEvolved.LOGGER.info("Brought the stasis bunker at {} up to layout {}", data.origin, data.layout);
        return true;
    }

    static BlockState oldScreenBlock(BlockPos grid) {
        return switch (OLD_SCREEN.get(grid.getY() - SCREEN.minY()).charAt(grid.getX() - SCREEN.minX())) {
            case 'D' -> Blocks.DEEPSLATE_TILES.defaultBlockState();
            case 'Y' -> Blocks.YELLOW_CONCRETE.defaultBlockState();
            case 'B' -> Blocks.BLACK_CONCRETE.defaultBlockState();
            case 'O' -> Blocks.ORANGE_CONCRETE.defaultBlockState();
            default -> Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        };
    }

    /**
     * Takes Exhibit 1 down from where layout 2 hung it, if it still hangs there. {@code at} is where the
     * chamber's anchor is in this level; the Night Three bunker uses this too, since it rebuilds its rooms.
     */
    public static void retireFirstExhibit(ServerLevel level, BlockPos at) {
        BlockPos old = at.offset(cell(com.jayptucker.heroesevolved.structure.Blueprint.load(CHAMBER.blueprint()), FIRST_EXHIBIT));
        var explosion = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.jayptucker.heroesevolved.HeroesEvolved.MOD_ID, "explosion");
        level.getEntitiesOfClass(net.minecraft.world.entity.decoration.Painting.class, new net.minecraft.world.phys.AABB(old).inflate(4),
                        painting -> painting.getPos().equals(old) && painting.getVariant().is(explosion))
                .forEach(net.minecraft.world.entity.Entity::discard);
    }

    /** A chamber grid cell, relative to the chamber's anchor. */
    private static BlockPos cell(com.jayptucker.heroesevolved.structure.Blueprint chamber, BlockPos grid) {
        return chamber.offset(grid.getX(), grid.getY(), grid.getZ(), net.minecraft.world.level.block.Rotation.NONE);
    }

    /**
     * Puts a sleeper's name on the nameplate over their pod: revival in progress while they wake, revived
     * once they are out. Only touches a loaded sign, and only when its words change.
     */
    public static void labelPod(ServerLevel level, BunkerData data, int index, String name, boolean revived) {
        BlockPos cell = data.layout().podSign(index);
        if (cell == null) return;
        BlockPos pos = data.origin.offset(cell);
        if (!level.isLoaded(pos) || !level.getBlockState(pos).is(Blocks.SPRUCE_WALL_SIGN)
                || !(level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign)
                || !sign.isWaxed()) return;
        String subject = String.format("SUBJECT %02d", index + 1);
        String[] lines = {subject, name, revived ? "REVIVED" : "REVIVAL", revived ? "" : "IN PROGRESS"};
        var text = sign.getFrontText();
        if (!text.getMessage(0, false).getString().equals(subject)) return;
        if (text.getMessage(2, false).getString().equals("STILL ASLEEP")
                && text.getMessage(3, false).getString().equals("DO NOT OPEN")) return;
        boolean same = true;
        for (int i = 0; i < lines.length; i++) same &= text.getMessage(i, false).getString().equals(lines[i]);
        if (same) return;
        for (int i = 0; i < lines.length; i++) text = text.setMessage(i, net.minecraft.network.chat.Component.literal(lines[i]));
        sign.setText(text, true);
        sign.setWaxed(true);
    }

    /**
     * Pod 14 in the present: Claire Bennet's, open and empty, its glass frosted and clawed through from the inside. She
     * woke decades early and left; her note waits on a lectern beside the pod ({@link #claireLectern}). Kept that way
     * every time the bunker is near, so nothing seals it again, and the note is put back if someone takes it.
     */
    public static void releaseClaire(ServerLevel level, BunkerData data) {
        int claire = com.jayptucker.heroesevolved.story.night.NightThreeBunker.CLAIRE;
        BlockPos origin = data.origin;
        BunkerLayout layout = data.layout();
        BlockPos base = origin.offset(layout.podOffset(claire));
        if (!level.isLoaded(base)) return;
        BlockState state = level.getBlockState(base);
        if (!(state.getBlock() instanceof CryoChamberBlock)) placePod(level, origin, layout, claire, true);
        else if (!state.getValue(CryoChamberBlock.OPEN)) CryoChamberBlock.setOpen(level, base, true);
        CryoChamberBlock.setCracked(level, base, true);
        // Her note, beside the pod: in a chest in the rebuilt bunker, on a lectern in an older one.
        BlockPos lectern = claireLectern(data);
        BlockState stand = level.getBlockState(lectern);
        if (stand.isAir() || stand.canBeReplaced())
            level.setBlock(lectern, layout.stasis()
                    ? Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, net.minecraft.core.Direction.NORTH)
                    : net.minecraft.world.level.block.Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, net.minecraft.core.Direction.NORTH),
                    Block.UPDATE_ALL);
        if (level.getBlockEntity(lectern) instanceof LecternBlockEntity desk && !desk.hasBook()) {
            level.setBlock(lectern, level.getBlockState(lectern).setValue(LecternBlock.HAS_BOOK, true), Block.UPDATE_ALL);
            if (level.getBlockEntity(lectern) instanceof LecternBlockEntity filled) filled.setBook(BunkerLogs.claireNote());
        }
        if (level.getBlockEntity(lectern) instanceof ChestBlockEntity box && box.isEmpty()) box.setItem(13, BunkerLogs.claireNote());
        BlockPos cell = layout.podSign(claire);
        if (cell == null) return;
        BlockPos pos = origin.offset(cell);
        if (level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign
                && !sign.getFrontText().getMessage(2, false).getString().equals("RELEASED"))
            signPod(level, origin, layout, claire, net.minecraft.world.item.DyeColor.LIGHT_BLUE, "SUBJECT 14", "C. BENNET", "RELEASED", "");
    }

    /** The book in a log's place: on a lectern (an older bunker) or in a chest (the rebuilt one), or empty. */
    public static ItemStack log(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof LecternBlockEntity lectern) return lectern.getBook();
        if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest)
            for (int i = 0; i < chest.getContainerSize(); i++)
                if (chest.getItem(i).is(Items.WRITTEN_BOOK)) return chest.getItem(i);
        return ItemStack.EMPTY;
    }

    /** Where Claire's note waits beside Pod 14, read from the step in front of the pod. */
    public static BlockPos claireLectern(BunkerData data) { return data.origin.offset(data.layout().claireLectern()); }

    /** Whether {@code base} is Claire's pod in the bunker under spawn. */
    public static boolean isClaires(ServerLevel level, BunkerData data, BlockPos base) {
        return level.dimension() == net.minecraft.world.level.Level.OVERWORLD
                && base.equals(data.origin.offset(data.layout().podOffset(com.jayptucker.heroesevolved.story.night.NightThreeBunker.CLAIRE)));
    }

    /** Writes up to four lines on pod {@code index}'s sign, whatever it said before. */
    public static void signPod(ServerLevel level, BlockPos origin, BunkerLayout layout, int index, net.minecraft.world.item.DyeColor color, String... lines) {
        BlockPos cell = layout.podSign(index);
        if (cell == null) return;
        BlockPos pos = origin.offset(cell);
        if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign)) return;
        var text = new net.minecraft.world.level.block.entity.SignText().setColor(color).setHasGlowingText(true);
        for (int i = 0; i < 4; i++) text = text.setMessage(i, net.minecraft.network.chat.Component.literal(i < lines.length ? lines[i] : ""));
        sign.setText(text, true);
        sign.setWaxed(true);
        sign.setChanged();
        level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), Block.UPDATE_CLIENTS);
    }

    /** Whether {@code pos} is inside the bunker under spawn. */
    public static boolean inside(BunkerData data, BlockPos pos) { return data.layout().inside(data.origin, pos); }

    /** Builds the bunker below the world's spawn ({@link BunkerLayout#STASIS}) and returns its origin (the floor below the shaft). */
    public static BlockPos build(ServerLevel level) {
        BlockPos spawn = level.getSharedSpawnPos();
        var box = BunkerLayout.STASIS.bounds();
        for (int cx = (spawn.getX() + Math.min(box.minX(), BunkerStair.REACH_X0) - 2) >> 4; cx <= (spawn.getX() + Math.max(box.maxX(), BunkerStair.REACH_X1) + 2) >> 4; cx++)
            for (int cz = (spawn.getZ() + BunkerStair.REACH_Z0 - 2) >> 4; cz <= (spawn.getZ() + Math.max(box.maxZ(), BunkerStair.REACH_Z1) + 2) >> 4; cz++) level.getChunk(cx, cz);
        // The ground the ruined lobby stands on: the middle height of its whole footprint, not one column's, so on a
        // hillside it sits on the slope instead of in a pit cut down to wherever spawn happened to be lowest.
        int surface = BunkerStair.groundLevel(level, spawn);
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

    /**
     * Places a room blueprint with its anchor at the given bunker-relative position, then finishes its markers.
     * A new bunker is on emergency power, so its lights go in dark ({@link BunkerPower}).
     */
    private void room(String blueprint, int x, int y, int z) {
        var marked = com.jayptucker.heroesevolved.structure.Blueprint.load(blueprint)
                .place(level, origin.offset(x, y, z), net.minecraft.world.level.block.Rotation.NONE, Integer.MAX_VALUE - 1, BunkerPower::unpowered);
        marked.forEach((marker, positions) -> positions.forEach(pos -> finish(marker, pos)));
    }

    /** Marker hooks: books for the lecterns and contents for the chests. */
    private void finish(String marker, BlockPos pos) { finish(level, marker, pos); }

    /** Fills a placed marker: a book on a lectern, supplies in a chest. Also used for the bunkers of the past. */
    public static void finish(ServerLevel level, String marker, BlockPos pos) {
        if (marker.startsWith("log:") && level.getBlockEntity(pos) instanceof ChestBlockEntity box) {
            // A chest: the book lies in the middle of it.
            box.setItem(13, log(marker.substring(4)));
        }
        if (marker.startsWith("log:") && level.getBlockEntity(pos) instanceof LecternBlockEntity lectern) {
            ItemStack book = log(marker.substring(4));
            level.setBlock(pos, level.getBlockState(pos).setValue(LecternBlock.HAS_BOOK, true), Block.UPDATE_CLIENTS);
            if (level.getBlockEntity(pos) instanceof LecternBlockEntity filled) filled.setBook(book);
        }
        if (marker.startsWith("loot:") && level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
            ItemStack[] items = switch (marker.substring(5)) {
                case "records" -> new ItemStack[] {ItemStack.EMPTY, new ItemStack(Items.BREAD, 8),
                        new ItemStack(Items.TORCH, 16), new ItemStack(Items.APPLE, 4)};
                case "lab" -> new ItemStack[] {new ItemStack(ModItems.HELIXITE_SHARD.get()),
                        new ItemStack(ModItems.EMPTY_SYRINGE.get(), 2), new ItemStack(Items.GLASS_BOTTLE, 3), donatedSample()};
                case "security" -> new ItemStack[] {new ItemStack(Items.IRON_SWORD), new ItemStack(Items.LEATHER_CHESTPLATE),
                        new ItemStack(Items.SHIELD), new ItemStack(Items.COOKED_BEEF, 6), new ItemStack(Items.COMPASS),
                        new ItemStack(ModItems.HORN_RIMMED_GLASSES.get())};   // Bennet's spare pair
                default -> new ItemStack[0];
            };
            for (int i = 0; i < items.length; i++) chest.setItem(i * 2, items[i]);
        }
    }

    /** The book a {@code log:<id>} marker holds. */
    private static ItemStack log(String id) {
        return switch (id) {
            case "orientation" -> BunkerLogs.orientation();
            case "lockdown" -> BunkerLogs.lockdown();
            case "helixite" -> BunkerLogs.helixite();
            case "weir" -> BunkerLogs.weir();
            case "bennet4r" -> BunkerLogs.bennetOnTheWard();
            default -> ItemStack.EMPTY;
        };
    }

    /** A recorded donation kept in the lab's reagent chest. */
    static ItemStack donatedSample() {
        ItemStack sample = new ItemStack(ModItems.BLOOD_SAMPLE.get());
        sample.set(ModItems.BLOOD_PURITY.get(), new com.jayptucker.heroesevolved.formula.BloodPurity(true, 3, "Stasis volunteer"));
        return sample;
    }

    /** Places pod {@code index}, open or sealed. Also used to repair a pod. */
    public static void placePod(ServerLevel level, BlockPos origin, BunkerLayout layout, int index, boolean open) {
        BlockPos base = origin.offset(layout.podOffset(index));
        BlockState pod = ModBlocks.CRYOCHAMBER.get().defaultBlockState().setValue(CryoChamberBlock.FACING, layout.podFacing(index))
                .setValue(CryoChamberBlock.OPEN, open);
        level.setBlock(base, pod, Block.UPDATE_CLIENTS);
        level.setBlock(base.above(), pod.setValue(CryoChamberBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER),
                Block.UPDATE_CLIENTS);
    }

    private void build() {
        int top = surface - origin.getY();

        boolean stair = BunkerStair.fits(top);
        if (!stair) {
            // Too shallow for the stair (a superflat world): a one-block ladder well from the records room to a hatch.
            for (int y = 0; y < Math.max(top, 4); y++)
                for (int x = -1; x <= 1; x++)
                    for (int z = -1; z <= 1; z++) set(x, y, z, x == 0 && z == 0 && y > 0 ? AIR : WALL);
            for (int y = 1; y < top; y++) set(0, y, 0, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
            if (top > 2) set(0, top, 0, Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.HALF, Half.TOP)
                    .setValue(TrapDoorBlock.FACING, Direction.SOUTH));
        }

        // The rooms are blueprints drawn by tools/generate-bunker.py; see the minecraft-building skill.
        BunkerLayout layout = BunkerLayout.STASIS;
        for (Room room : layout.rooms()) room(room.blueprint(), room.at().getX(), room.at().getY(), room.at().getZ());
        for (Room room : layout.deep())
            if (origin.getY() + room.at().getY() > level.getMinBuildHeight())
                room(room.blueprint(), room.at().getX(), room.at().getY(), room.at().getZ());
        // The way out, as the Last Day's survivors came in: the vault behind records, stuck open and grown over, and a
        // switchback stair up to a ruined lobby on the surface (BunkerStair).
        if (stair) BunkerStair.build(level, origin, top);
    }

}
