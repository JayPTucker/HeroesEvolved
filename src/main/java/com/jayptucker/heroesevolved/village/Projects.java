package com.jayptucker.heroesevolved.village;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.radio.BandChatter;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Village projects. Every remembered village puts up a notice board by its bell (six signs over a chest) and posts what it
 * wants to build next: one of its own kind's vanilla buildings (a house, a farm, a workshop), asking for what that building
 * is made of. Players fill the chest; the village takes what it still needs, the signs count it, and once it has it all its
 * people build it over two days ({@link Growth#grow(ServerLevel, VillageMemory.Village, ResourceLocation)}, {@link Rebuild}).
 * Only villages that are loaded and ticking do any of this. What the player sees: {@code docs/village-growth.md}.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class Projects {
    /** The board stands this far from the bell. */
    public static final int BOARD_NEAREST = 5, BOARD_FURTHEST = 18;
    /** A village builds (or rebuilds) at most this many things. */
    public static final int MOST = 16;
    /** Where a village's tier starts, counting a bed as 1 and each thing it has built as 2. */
    public static final int VILLAGE_FROM = 5, TOWN_FROM = 20;
    /** The most of any one material a project asks for. */
    public static final int MOST_OF_ONE = 160;
    /** Anyone this close to the board hears what it's for, once a project. */
    static final int TELL = 10;
    /** How often a village with no board looks for room for one, and a ready project for room to build (ticks). */
    static final int RETRY = 1200;
    /** How wide a line on a sign may be (the game's own limit, in font pixels). */
    static final int SIGN_WIDTH = 90;

    /** What a village asks for. The label is the sign's; {@code one} and {@code many} are for chat. */
    public enum Material {
        PLANKS("Planks", "plank", "planks"),
        STONE("Cobblestone", "cobblestone", "cobblestone"),
        SANDSTONE("Sandstone", "sandstone", "sandstone"),
        TERRACOTTA("Terracotta", "terracotta", "terracotta"),
        GLASS("Glass panes", "glass pane", "glass panes"),
        WOOL("Wool", "wool", "wool"),
        BEDS("Beds", "bed", "beds"),
        LIGHT("Torches", "torch", "torches"),
        IRON("Iron ingots", "iron ingot", "iron ingots"),
        HAY("Hay bales", "hay bale", "hay bales");

        public final String label;
        final String one, many;
        Material(String label, String one, String many) { this.label = label; this.one = one; this.many = many; }
        String count(int n) { return n + " " + (n == 1 ? one : many); }
    }

    public enum Stage { GATHERING, READY, BUILDING, DONE }

    /**
     * How big a place is, from its beds and what it has built: a hamlet builds houses and farms; a village adds bigger
     * houses, workshops and rebuilding its small homes bigger; a town adds big houses, libraries and temples, its own
     * two-storey town houses, and rebuilding homes with a second storey.
     */
    public enum Tier {
        HAMLET("Hamlet", "a hamlet"), VILLAGE("Village", "a village"), TOWN("Town", "a town");
        public final String label;
        final String a;
        Tier(String label, String a) { this.label = label; this.a = a; }
    }

    /** {@code v}'s tier now: its beds (loaded ones, within the village) and twice what it has built. */
    public static Tier tier(ServerLevel level, VillageMemory.Village v) {
        long beds = level.getPoiManager().getCountInRange(t -> t.is(net.minecraft.world.entity.ai.village.poi.PoiTypes.HOME),
                v.bell, Villages.RADIUS, net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY);
        long size = beds + 2L * v.houses;
        return size >= TOWN_FROM ? Tier.TOWN : size >= VILLAGE_FROM ? Tier.VILLAGE : Tier.HAMLET;
    }

    /**
     * A home in a village, as its template placed it: the village's own houses (from its structure's pieces) and the ones
     * it built itself. What an upgrade tears down and builds bigger.
     */
    public record Home(ResourceLocation template, BlockPos origin, Rotation rotation) {
        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("template", template.toString());
            t.putLong("origin", origin.asLong());
            t.putString("rotation", rotation.name());
            return t;
        }

        static Home load(CompoundTag t) {
            ResourceLocation id = ResourceLocation.tryParse(t.getString("template"));
            Rotation r;
            try { r = Rotation.valueOf(t.getString("rotation")); } catch (IllegalArgumentException e) { r = Rotation.NONE; }
            return id == null ? null : new Home(id, BlockPos.of(t.getLong("origin")), r);
        }

        /** A small house (a village can rebuild it bigger), a medium one (a town can), or neither. */
        public boolean small() { return template.getPath().contains("small_house"); }
        public boolean medium() { return template.getPath().contains("medium_house"); }
    }

    /** {@code v}'s homes that are still standing (at least {@link #STANDING} of their blocks), nearest the bell first. */
    public static List<Home> homes(ServerLevel level, VillageMemory.Village v) {
        List<Home> found = new ArrayList<>();
        try {
            var start = level.structureManager().getStructureWithPieceAt(v.bell, net.minecraft.tags.StructureTags.VILLAGE);
            if (start.isValid())
                for (var piece : start.getPieces())
                    if (piece instanceof net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece pe) {
                        ResourceLocation id = templateOf(pe.getElement());
                        if (id != null && id.getPath().contains("/houses/")) found.add(new Home(id, pe.getPosition(), pe.getRotation()));
                    }
        } catch (RuntimeException e) {
            HeroesEvolved.LOGGER.debug("Couldn't read the village structure at {}", v.bell, e);
        }
        found.addAll(v.homes);
        found.removeIf(h -> v.replaced.contains(h.origin().asLong()) || h.origin().distSqr(v.bell) > (long) Villages.RADIUS * Villages.RADIUS);
        found.removeIf(h -> standing(level, v, h) < STANDING || touched(level, h));
        found.sort(java.util.Comparator.comparingDouble(h -> h.origin().distSqr(v.bell)));
        return found;
    }

    /** Whether a player has put anything down in or on home {@code h} (it is then theirs too: never torn down). */
    static boolean touched(ServerLevel level, Home h) {
        StructureTemplate t = level.getStructureManager().get(h.template()).orElse(null);
        if (t == null) return true;
        var box = t.getBoundingBox(new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings().setRotation(h.rotation()), h.origin());
        for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()))
            if (PlayerBuilt.is(level, p)) return true;
        return false;
    }

    /**
     * Every building {@code v} has as it was built, to mend (village/Repairs): its structure's own rigid pieces (houses,
     * its meeting point, wells, lamps; not its streets, which follow the ground) and what it built itself, less what it
     * tore down to rebuild and the homes players own.
     */
    public static List<Home> pieces(ServerLevel level, VillageMemory.Village v) {
        List<Home> found = new ArrayList<>();
        try {
            var start = level.structureManager().getStructureWithPieceAt(v.bell, net.minecraft.tags.StructureTags.VILLAGE);
            if (start.isValid())
                for (var piece : start.getPieces())
                    if (piece instanceof net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece pe
                            && pe.getElement().getProjection() == net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool.Projection.RIGID) {
                        ResourceLocation id = templateOf(pe.getElement());
                        if (id != null && !id.getPath().contains("/streets/")) found.add(new Home(id, pe.getPosition(), pe.getRotation()));
                    }
        } catch (RuntimeException e) {
            HeroesEvolved.LOGGER.debug("Couldn't read the village structure at {}", v.bell, e);
        }
        found.addAll(v.homes);
        java.util.Set<Long> owned = new java.util.HashSet<>();
        for (var o : v.owned.values()) owned.add(o.home().origin().asLong());
        found.removeIf(h -> v.replaced.contains(h.origin().asLong()) || owned.contains(h.origin().asLong())
                || h.origin().distSqr(v.bell) > (long) (Villages.RADIUS + 32) * (Villages.RADIUS + 32));
        return found;
    }

    /** How much of a home must still stand for the village to rebuild it rather than leave it be. */
    static final float STANDING = 0.6F;

    /** How much of {@code h} still stands as it was built (0 if it can't be read or isn't loaded). */
    public static float standing(ServerLevel level, VillageMemory.Village v, Home h) {
        StructureTemplate t = level.getStructureManager().get(h.template()).orElse(null);
        if (t == null) return 0;
        var blocks = Growth.blocks(level, t, h.origin(), h.rotation());
        if (blocks == null) return 0;
        String kind = Growth.kind(level, v.bell);
        int all = 0, there = 0;
        for (var b : blocks) {
            if (b.state().isAir() || b.state().is(Blocks.STRUCTURE_VOID) || b.state().is(Blocks.JIGSAW)) continue;
            if (!level.hasChunkAt(b.pos())) return 0;
            all++;
            BlockState want = Styles.ours(h.template()) ? Styles.apply(b.state(), kind) : b.state();
            if (level.getBlockState(b.pos()).is(want.getBlock())) there++;
        }
        return all == 0 ? 0 : there / (float) all;
    }

    private static java.lang.reflect.Field singleTemplate;

    /** The template a village piece was placed from, or null. */
    @SuppressWarnings("unchecked")
    static ResourceLocation templateOf(net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement element) {
        if (!(element instanceof net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement single)) return null;
        try {
            if (singleTemplate == null) {
                singleTemplate = net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement.class.getDeclaredField("template");
                singleTemplate.setAccessible(true);
            }
            var either = (com.mojang.datafixers.util.Either<ResourceLocation, StructureTemplate>) singleTemplate.get(single);
            return either.left().orElse(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            // Its name is in its description: Single[Left[minecraft:village/plains/houses/plains_small_house_1]].
            var m = java.util.regex.Pattern.compile("([a-z0-9_.-]+:[a-z0-9_./-]+)").matcher(single.toString());
            return m.find() ? ResourceLocation.tryParse(m.group(1)) : null;
        }
    }

    // ------------------------------------------------------------------
    // The board
    // ------------------------------------------------------------------

    /**
     * Where a village's board stands: {@code origin} is the ground cell under its middle, {@code facing} the way its signs
     * face (toward the bell). Cells are counted from the reader's left ({@code x}), up ({@code y}) and toward the reader
     * ({@code z}).
     */
    public static final class Board {
        final BlockPos origin;
        final Direction facing;
        Board(BlockPos origin, Direction facing) { this.origin = origin.immutable(); this.facing = facing; }
        public BlockPos origin() { return origin; }
        public Direction facing() { return facing; }
        /** The reader's right, looking at the signs. */
        Direction right() { return facing.getCounterClockWise(); }
        public BlockPos at(int x, int y, int z) { return origin.relative(right(), x).above(y).relative(facing, z); }
        public BlockPos chest() { return at(0, 0, 1); }
        /** Sign {@code i}: 0 to 2 along the top row from the left, 3 to 5 along the bottom. */
        public BlockPos sign(int i) { return at(i % 3 - 1, i < 3 ? 2 : 1, 1); }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putLong("origin", origin.asLong());
            t.putInt("facing", facing.get2DDataValue());
            return t;
        }

        static Board load(CompoundTag t) { return new Board(BlockPos.of(t.getLong("origin")), Direction.from2DDataValue(t.getInt("facing"))); }
    }

    /** The board's wood, after the village's kind. */
    record Wood(Block log, Block planks, Block slab, Block sign) {}

    static Wood wood(String kind) {
        return switch (kind) {
            case "savanna" -> new Wood(Blocks.ACACIA_LOG, Blocks.ACACIA_PLANKS, Blocks.ACACIA_SLAB, Blocks.ACACIA_WALL_SIGN);
            case "taiga", "snowy" -> new Wood(Blocks.SPRUCE_LOG, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_WALL_SIGN);
            case "desert" -> new Wood(Blocks.BIRCH_LOG, Blocks.BIRCH_PLANKS, Blocks.BIRCH_SLAB, Blocks.BIRCH_WALL_SIGN);
            default -> new Wood(Blocks.OAK_LOG, Blocks.OAK_PLANKS, Blocks.OAK_SLAB, Blocks.OAK_WALL_SIGN);
        };
    }

    record Cell(BlockPos pos, BlockState state) {}

    /**
     * What the board is made of, in the order it goes up (so each thing is put down after what holds it): two posts, a
     * backboard two high, a slab roof with a lantern on each post, six signs on its face, and the chest at its foot.
     */
    static List<Cell> layout(Board b, Wood w) {
        List<Cell> cells = new ArrayList<>();
        BlockState log = w.log().defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
        for (int y = 0; y <= 2; y++) { cells.add(new Cell(b.at(-2, y, 0), log)); cells.add(new Cell(b.at(2, y, 0), log)); }
        for (int y = 1; y <= 2; y++) for (int x = -1; x <= 1; x++) cells.add(new Cell(b.at(x, y, 0), w.planks().defaultBlockState()));
        for (int x = -1; x <= 1; x++) cells.add(new Cell(b.at(x, 3, 0), w.slab().defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM)));
        BlockState lantern = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false);
        cells.add(new Cell(b.at(-2, 3, 0), lantern));
        cells.add(new Cell(b.at(2, 3, 0), lantern));
        for (int i = 0; i < 6; i++) cells.add(new Cell(b.sign(i), w.sign().defaultBlockState().setValue(WallSignBlock.FACING, b.facing)));
        cells.add(new Cell(b.chest(), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, b.facing)));
        return cells;
    }

    /** Somewhere by {@code bell} with room for a board, or null: natural ground first, then paths and paving. */
    static Board site(ServerLevel level, BlockPos bell) {
        int turn = (int) Math.floorMod(bell.asLong() * 31, 16);
        for (int pass = 0; pass < 2; pass++)
            for (int r = BOARD_NEAREST; r <= BOARD_FURTHEST; r++)
                for (int i = 0; i < 24; i++) {
                    double a = (i + turn) * Math.PI * 2 / 24;
                    int x0 = bell.getX() + (int) Math.round(Math.cos(a) * r), z0 = bell.getZ() + (int) Math.round(Math.sin(a) * r);
                    Direction facing = Direction.getNearest((float) (bell.getX() - x0), 0F, (float) (bell.getZ() - z0));
                    Board b = fit(level, bell, x0, z0, facing, pass == 1);
                    if (b != null) return b;
                }
        return null;
    }

    /** A board at {@code x0, z0} facing {@code facing}, if the ground there is level, open to the sky and clear. */
    static Board fit(ServerLevel level, BlockPos bell, int x0, int z0, Direction facing, boolean paved) {
        BlockPos column = new BlockPos(x0, 0, z0);
        if (!level.hasChunkAt(column)) return null;
        int y = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column).getY();
        if (Math.abs(y - bell.getY()) > 3) return null;
        Board b = new Board(new BlockPos(x0, y, z0), facing);
        for (int x = -2; x <= 2; x++)
            for (int z = 0; z <= 2; z++) {
                BlockPos ground = b.at(x, -1, z);
                if (!level.hasChunkAt(ground)) return null;
                if (z <= 1) {
                    BlockState g = level.getBlockState(ground);
                    boolean ok = Growth.natural(g) || paved && (g.is(Blocks.DIRT_PATH) || g.isFaceSturdy(level, ground, Direction.UP));
                    if (!ok || !level.getFluidState(ground).isEmpty() || g.is(Blocks.FARMLAND)) return null;
                }
                // The board itself, and room to stand in front of it; never on or over anything a player put down.
                if (z <= 1 && PlayerBuilt.is(level, ground)) return null;
                for (int dy = 0; dy <= (z == 2 ? 1 : 3); dy++) {
                    BlockState s = level.getBlockState(b.at(x, dy, z));
                    if (!s.isAir() && !(s.canBeReplaced() && s.getFluidState().isEmpty()) || PlayerBuilt.is(level, b.at(x, dy, z))) return null;
                }
            }
        return b;
    }

    /** Puts up (or mends) {@code v}'s board: every piece that's missing, where nothing else stands in its way. */
    static void build(ServerLevel level, VillageMemory.Village v) {
        Board b = v.board;
        if (b == null) return;
        for (Cell c : layout(b, wood(Growth.kind(level, v.bell)))) {
            if (!level.hasChunkAt(c.pos())) continue;
            BlockState now = level.getBlockState(c.pos());
            if (now.is(c.state().getBlock())) continue;
            if (!now.isAir() && !(now.canBeReplaced() && now.getFluidState().isEmpty()) || PlayerBuilt.is(level, c.pos())) continue;
            if (c.state().is(Blocks.CHEST) || c.state().getBlock() instanceof WallSignBlock) {
                if (!c.state().canSurvive(level, c.pos())) continue;
            }
            level.setBlock(c.pos(), c.state(), Block.UPDATE_ALL);
            if (level.getBlockEntity(c.pos()) instanceof ChestBlockEntity chest) {
                chest.applyComponents(DataComponentMap.builder().set(DataComponents.CUSTOM_NAME,
                        Component.literal("For " + v.name + "'s builders")).build(), DataComponentPatch.EMPTY);
                chest.setChanged();
            }
            if (level.getBlockEntity(c.pos()) instanceof SignBlockEntity sign) sign.setWaxed(true);
        }
        signs(level, v);
    }

    /** Whether every piece of the board is standing. */
    public static boolean whole(ServerLevel level, VillageMemory.Village v) {
        if (v.board == null) return false;
        for (Cell c : layout(v.board, wood(Growth.kind(level, v.bell))))
            if (level.hasChunkAt(c.pos()) && !level.getBlockState(c.pos()).is(c.state().getBlock())) return false;
        return true;
    }

    // ------------------------------------------------------------------
    // The project
    // ------------------------------------------------------------------

    /** What a village is gathering for, and how far along it is. Saved with the village ({@link VillageMemory}). */
    public static final class Project {
        ResourceLocation template;
        String title;
        Stage stage = Stage.GATHERING;
        final EnumMap<Material, Integer> need = new EnumMap<>(Material.class);
        final EnumMap<Material, Float> have = new EnumMap<>(Material.class);
        /** Beds in the building: a newcomer for each (up to two). */
        int beds;
        long posted = -1, finished = -1;
        long lastTry = -1000;
        /** It has everything but nowhere to build yet. */
        boolean noRoom;
        /** The home it rebuilds bigger, or null for a new building. */
        Home upgrade;
        /** Who it is a home for (village/Homes), or null: a home for a player is built with nothing to gather. */
        UUID owner;
        /** The last calendar day the village gathered for it itself (village/Workers); -1 until it starts. */
        long gathered = -1;
        final Set<UUID> givers = new LinkedHashSet<>(), told = new HashSet<>();

        public ResourceLocation template() { return template; }
        public Home upgrade() { return upgrade; }
        public UUID owner() { return owner; }
        public String title() { return title; }
        public Stage stage() { return stage; }
        public int beds() { return beds; }
        public Map<Material, Integer> need() { return Map.copyOf(need); }
        public int need(Material m) { return need.getOrDefault(m, 0); }
        public float have(Material m) { return have.getOrDefault(m, 0F); }
        public float left(Material m) { return Math.max(0, need(m) - have(m)); }
        public boolean has(Material m) { return have(m) + 1e-3F >= need(m); }
        public Set<UUID> givers() { return Set.copyOf(givers); }

        public boolean complete() {
            for (Material m : need.keySet()) if (!has(m)) return false;
            return true;
        }

        /** How much of it is in, by count. */
        public float fraction() {
            float in = 0, all = 0;
            for (var e : need.entrySet()) { all += e.getValue(); in += Math.min(e.getValue(), have(e.getKey())); }
            return all <= 0 ? 1 : in / all;
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("template", template.toString());
            t.putString("title", title);
            t.putString("stage", stage.name());
            CompoundTag n = new CompoundTag(), h = new CompoundTag();
            need.forEach((m, c) -> n.putInt(m.name(), c));
            have.forEach((m, c) -> h.putFloat(m.name(), c));
            t.put("need", n);
            t.put("have", h);
            t.putInt("beds", beds);
            t.putLong("posted", posted);
            t.putLong("finished", finished);
            t.putBoolean("no_room", noRoom);
            if (upgrade != null) t.put("upgrade", upgrade.save());
            if (owner != null) t.putUUID("owner", owner);
            t.putLong("gathered", gathered);
            ListTag g = new ListTag(), k = new ListTag();
            for (UUID u : givers) g.add(NbtUtils.createUUID(u));
            for (UUID u : told) k.add(NbtUtils.createUUID(u));
            t.put("givers", g);
            t.put("told", k);
            return t;
        }

        static Project load(CompoundTag t) {
            Project p = new Project();
            p.template = ResourceLocation.tryParse(t.getString("template"));
            p.title = t.getString("title");
            try { p.stage = Stage.valueOf(t.getString("stage")); } catch (IllegalArgumentException e) { p.stage = Stage.GATHERING; }
            CompoundTag n = t.getCompound("need"), h = t.getCompound("have");
            for (Material m : Material.values()) {
                if (n.contains(m.name())) p.need.put(m, n.getInt(m.name()));
                if (h.contains(m.name())) p.have.put(m, h.getFloat(m.name()));
            }
            p.beds = t.getInt("beds");
            p.posted = t.getLong("posted");
            p.finished = t.getLong("finished");
            p.noRoom = t.getBoolean("no_room");
            if (t.contains("upgrade")) p.upgrade = Home.load(t.getCompound("upgrade"));
            if (t.hasUUID("owner")) p.owner = t.getUUID("owner");
            p.gathered = t.contains("gathered") ? t.getLong("gathered") : -1;
            for (Tag u : t.getList("givers", Tag.TAG_INT_ARRAY)) p.givers.add(NbtUtils.loadUUID(u));
            for (Tag u : t.getList("told", Tag.TAG_INT_ARRAY)) p.told.add(NbtUtils.loadUUID(u));
            return p.template == null ? null : p;
        }
    }

    /** A building a village of some kind can put up: its template, what the board calls it, and how often it's chosen. */
    public record Plan(ResourceLocation id, String title, int weight, Tier tier) {}

    /** The town houses: our own two-storey designs, recoloured for each kind ({@link Styles}). */
    public static final List<ResourceLocation> TOWN_HOUSES = List.of(
            ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "village/town_house_1"),
            ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "village/town_house_2"));

    public static final String UPGRADE = "A bigger home", STOREY = "A two-storey home";

    private static final Map<String, List<Plan>> PLANS = new HashMap<>();

    /** Every building a village of {@code kind} can put up: the vanilla templates under {@code village/<kind>/houses/}. */
    public static List<Plan> plans(ServerLevel level, String kind) {
        return PLANS.computeIfAbsent(kind, k -> {
            List<Plan> out = new ArrayList<>();
            String prefix = "village/" + k + "/houses/";
            try {
                level.getStructureManager().listTemplates()
                        .filter(id -> id.getNamespace().equals("minecraft") && id.getPath().startsWith(prefix))
                        .sorted()
                        .forEach(id -> { Plan p = plan(id); if (p != null) out.add(p); });
            } catch (RuntimeException e) {
                HeroesEvolved.LOGGER.warn("Couldn't list village templates for {}", k, e);
            }
            // Should listing ever fail, a village can still build its small houses.
            if (out.isEmpty())
                for (int i = 1; i <= (k.equals("taiga") ? 5 : 8); i++)
                    out.add(new Plan(ResourceLocation.withDefaultNamespace(prefix + k + "_small_house_" + i), "A new house", 4, Tier.HAMLET));
            for (ResourceLocation id : TOWN_HOUSES) out.add(new Plan(id, "A town house", 2, Tier.TOWN));
            return List.copyOf(out);
        });
    }

    /** What the board calls the building in template {@code id}, or null if a village doesn't build it on its own. */
    static Plan plan(ResourceLocation id) {
        String n = id.getPath().substring(id.getPath().lastIndexOf('/') + 1);
        // name, title, weight, and the tier it needs: 0 a hamlet, 1 a village, 2 a town.
        String[][] names = {
                {"small_house", "A new house", "4", "0"}, {"medium_house", "A bigger house", "2", "1"}, {"big_house", "A big house", "1", "2"},
                {"large_farm", "A large farm", "1", "2"}, {"farm", "A farm", "2", "0"}, {"animal_pen", "An animal pen", "1", "0"},
                {"stable", "A stable", "1", "1"}, {"library", "A library", "1", "2"}, {"temple", "A temple", "1", "2"},
                {"butcher", "A butcher's shop", "1", "1"}, {"tool_smith", "A toolsmith's", "1", "1"}, {"weaponsmith", "A weaponsmith's", "1", "1"},
                {"armorer", "An armourer's", "1", "1"}, {"fletcher", "A fletcher's", "1", "1"}, {"shepherd", "A shepherd's", "1", "1"},
                {"fisher", "A fisher's cottage", "1", "1"}, {"tannery", "A tannery", "1", "1"}, {"cartographer", "A mapmaker's", "1", "1"},
                {"mason", "A mason's yard", "1", "1"}};
        for (String[] e : names)
            if (n.contains(e[0])) return new Plan(id, e[1], Integer.parseInt(e[2]), Tier.values()[Integer.parseInt(e[3])]);
        return null;
    }

    /**
     * What {@code blocks} are made of, as a village asks for it: wood in planks (a log is four), stone, sandstone,
     * terracotta, glass in panes (a block is three), wool, beds, torches, iron and hay. Soil, plants, crops, snow and ice
     * the village finds for itself.
     */
    public static EnumMap<Material, Float> materials(List<StructureTemplate.StructureBlockInfo> blocks) {
        EnumMap<Material, Float> m = new EnumMap<>(Material.class);
        for (StructureTemplate.StructureBlockInfo b : blocks) add(m, b.state());
        return m;
    }

    static void add(EnumMap<Material, Float> m, BlockState s) {
        if (s.isAir() || s.is(Blocks.JIGSAW) || s.is(Blocks.STRUCTURE_VOID)) return;
        Block block = s.getBlock();
        String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
        if (block instanceof BedBlock) { if (s.getValue(BedBlock.PART) == BedPart.FOOT) put(m, Material.BEDS, 1); return; }
        if (s.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && s.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) return;
        if (block instanceof DoorBlock) { put(m, path.startsWith("iron") ? Material.IRON : Material.PLANKS, 2); return; }
        // Workshops and furniture, by what they're made of.
        if (block == Blocks.FURNACE || block == Blocks.SMOKER) { put(m, Material.STONE, 8); return; }
        if (block == Blocks.BLAST_FURNACE) { put(m, Material.IRON, 5); put(m, Material.STONE, 3); return; }
        if (block == Blocks.STONECUTTER) { put(m, Material.IRON, 1); put(m, Material.STONE, 3); return; }
        if (block == Blocks.GRINDSTONE) { put(m, Material.STONE, 2); put(m, Material.PLANKS, 2); return; }
        if (block == Blocks.BREWING_STAND) { put(m, Material.STONE, 3); return; }
        if (block == Blocks.CAULDRON || block == Blocks.WATER_CAULDRON) { put(m, Material.IRON, 7); return; }
        if (block == Blocks.ANVIL || block == Blocks.CHIPPED_ANVIL || block == Blocks.DAMAGED_ANVIL) { put(m, Material.IRON, 10); return; }
        if (block == Blocks.SMITHING_TABLE) { put(m, Material.IRON, 2); put(m, Material.PLANKS, 4); return; }
        if (block == Blocks.IRON_BARS) { put(m, Material.IRON, 0.5F); return; }
        if (block == Blocks.CHAIN) { put(m, Material.IRON, 1); return; }
        if (block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST || block == Blocks.BARREL || block == Blocks.NOTE_BLOCK || block == Blocks.JUKEBOX) {
            put(m, Material.PLANKS, 8); return;
        }
        if (block == Blocks.BOOKSHELF) { put(m, Material.PLANKS, 6); return; }
        if (block == Blocks.CRAFTING_TABLE || block == Blocks.LECTERN || block == Blocks.CARTOGRAPHY_TABLE
                || block == Blocks.FLETCHING_TABLE || block == Blocks.LOOM) { put(m, Material.PLANKS, 4); return; }
        if (block == Blocks.COMPOSTER || block == Blocks.CAMPFIRE) { put(m, Material.PLANKS, 3); return; }
        if (block == Blocks.LADDER) { put(m, Material.PLANKS, 0.5F); return; }
        if (block == Blocks.TORCH || block == Blocks.WALL_TORCH || block == Blocks.SOUL_TORCH || block == Blocks.SOUL_WALL_TORCH
                || block == Blocks.LANTERN || block == Blocks.SOUL_LANTERN) { put(m, Material.LIGHT, 1); return; }
        if (block == Blocks.HAY_BLOCK) { put(m, Material.HAY, 1); return; }
        float shape = path.endsWith("_slab") ? 0.5F : 1F;
        if (path.contains("sandstone")) { put(m, Material.SANDSTONE, shape); return; }
        if (path.contains("terracotta")) { put(m, Material.TERRACOTTA, 1); return; }
        if (path.contains("glass_pane")) { put(m, Material.GLASS, 1); return; }
        if (path.endsWith("glass")) { put(m, Material.GLASS, 3); return; }
        if (path.endsWith("_wool")) { put(m, Material.WOOL, 1); return; }
        if (path.endsWith("_carpet")) { put(m, Material.WOOL, 0.67F); return; }
        if (s.is(BlockTags.LOGS)) { put(m, Material.PLANKS, 4); return; }
        if (s.is(BlockTags.PLANKS) || s.is(BlockTags.WOODEN_STAIRS) || s.is(BlockTags.WOODEN_FENCES) || s.is(BlockTags.WOODEN_PRESSURE_PLATES)
                || s.is(BlockTags.ALL_SIGNS)) { put(m, Material.PLANKS, 1); return; }
        if (s.is(BlockTags.WOODEN_SLABS) || s.is(BlockTags.WOODEN_BUTTONS)) { put(m, Material.PLANKS, 0.5F); return; }
        if (s.is(BlockTags.FENCE_GATES)) { put(m, Material.PLANKS, 2); return; }
        if (s.is(BlockTags.WOODEN_TRAPDOORS)) { put(m, Material.PLANKS, 1.5F); return; }
        if (stone(path)) { put(m, Material.STONE, shape); }
    }

    /** Cobblestone, stone, stone bricks, the polished stones, cobbled deepslate and bricks, in any shape. */
    static boolean stone(String path) {
        if (path.contains("blackstone") || path.contains("redstone") || path.contains("end_stone") || path.contains("glowstone")
                || path.contains("dripstone") || path.contains("lodestone") || path.contains("button") || path.contains("pressure_plate")) return false;
        return path.contains("cobblestone") || path.contains("stone_brick") || path.startsWith("stone") || path.startsWith("smooth_stone")
                || path.contains("andesite") || path.contains("diorite") || path.contains("granite") || path.contains("cobbled_deepslate")
                || path.contains("deepslate_brick") || path.contains("deepslate_tile") || path.equals("bricks") || path.startsWith("brick_")
                || path.contains("mud_brick");
    }

    private static void put(EnumMap<Material, Float> m, Material k, float n) { m.merge(k, n, Float::sum); }

    /** How much of {@code m} one of {@code stack} counts for (0 if it doesn't). */
    public static float worth(ItemStack stack, Material m) {
        return switch (m) {
            case PLANKS -> stack.is(ItemTags.PLANKS) ? 1 : stack.is(ItemTags.LOGS) ? 4 : 0;
            case STONE -> stack.is(Tags.Items.COBBLESTONES) || stack.is(Tags.Items.STONES) || stack.is(Items.STONE_BRICKS)
                    || stack.is(Items.MOSSY_STONE_BRICKS) || stack.is(Items.SMOOTH_STONE) || stack.is(Items.BRICKS) ? 1 : 0;
            case SANDSTONE -> stack.is(Tags.Items.SANDSTONE_BLOCKS) ? 1 : 0;
            case TERRACOTTA -> stack.is(ItemTags.TERRACOTTA) ? 1 : 0;
            case GLASS -> stack.is(Tags.Items.GLASS_PANES) ? 1 : stack.is(Tags.Items.GLASS_BLOCKS) ? 3 : 0;
            case WOOL -> stack.is(ItemTags.WOOL) ? 1 : 0;
            case BEDS -> stack.is(ItemTags.BEDS) ? 1 : 0;
            case LIGHT -> stack.is(Items.TORCH) || stack.is(Items.SOUL_TORCH) || stack.is(Items.LANTERN) || stack.is(Items.SOUL_LANTERN) ? 1 : 0;
            case IRON -> stack.is(Tags.Items.INGOTS_IRON) ? 1 : stack.is(Tags.Items.STORAGE_BLOCKS_IRON) ? 9 : 0;
            case HAY -> stack.is(Items.HAY_BLOCK) ? 1 : stack.is(Items.WHEAT) ? 1F / 9 : 0;
        };
    }

    /** How much of what it asks for a village of this mood gathers for itself: a thriving one, half. */
    static float share(Growth.Mood mood) {
        return switch (mood) { case THRIVING -> 0.5F; case STEADY -> 0.75F; case STRUGGLING -> 1F; };
    }

    /** One thing a village might post: a building, and the home it would replace (null for a new one). */
    record Option(Plan plan, Home old) {}

    /**
     * What {@code v} could post at its tier: its kind's buildings up to the tier, and, from a village up, rebuilding one of
     * its homes bigger (a small one into a bigger house; in a town, a small or medium one into a two-storey town house).
     */
    static List<Option> options(ServerLevel level, VillageMemory.Village v, Tier tier) {
        List<Plan> plans = plans(level, Growth.kind(level, v.bell));
        List<Option> out = new ArrayList<>();
        for (Plan p : plans) if (p.tier.ordinal() <= tier.ordinal()) out.add(new Option(p, null));
        if (tier == Tier.HAMLET) return out;
        // Only a home whose bigger design fits where it stands now: never a project the village couldn't finish.
        List<ResourceLocation> medium = new ArrayList<>(plans.stream().filter(p -> p.id.getPath().contains("medium_house")).map(Plan::id).toList());
        List<ResourceLocation> town = new ArrayList<>(TOWN_HOUSES);
        java.util.Collections.shuffle(medium, new java.util.Random(level.random.nextLong()));
        java.util.Collections.shuffle(town, new java.util.Random(level.random.nextLong()));
        for (Home h : homes(level, v)) {
            if (tier == Tier.TOWN && (h.small() || h.medium()))
                for (ResourceLocation to : town)
                    if (Growth.upgradeFits(level, v, h, to)) { out.add(new Option(new Plan(to, STOREY, 3, Tier.TOWN), h)); return out; }
            if (h.small())
                for (ResourceLocation to : medium)
                    if (Growth.upgradeFits(level, v, h, to)) { out.add(new Option(new Plan(to, UPGRADE, 3, Tier.VILLAGE), h)); return out; }
        }
        return out;
    }

    /** Posts {@code v}'s next project (a home first, never the same kind twice running), or null if it can't. */
    public static Project post(ServerLevel level, VillageMemory.Village v) {
        Tier tier = tier(level, v);
        List<Option> all = options(level, v, tier);
        String last = v.project == null ? null : v.project.title;
        List<Option> pick = new ArrayList<>();
        for (Option o : all)
            if ((v.houses > 0 || o.plan.title.equals("A new house") || o.old != null) && !o.plan.title.equals(last)) pick.add(o);
        if (pick.isEmpty()) pick.addAll(all);
        while (!pick.isEmpty()) {
            int total = 0;
            for (Option o : pick) total += o.plan.weight;
            int roll = level.random.nextInt(Math.max(1, total));
            Option chosen = pick.getLast();
            for (Option o : pick) { roll -= o.plan.weight; if (roll < 0) { chosen = o; break; } }
            pick.remove(chosen);
            Project p = project(level, v, chosen.plan, chosen.old);
            if (p == null) continue;
            v.project = p;
            VillageMemory.get(level).setDirty();
            BandChatter.news(level, v.bell, v.name + " is gathering for " + lower(p.title) + ". The list's up on the board by the bell.", (int) Days.DAY);
            signs(level, v);
            return p;
        }
        return null;
    }

    /**
     * A project for {@code plan} at {@code v}: what its template is made of (in the village's colours), less the village's
     * own share; rebuilding {@code old}, less half of what the old house gives back too.
     */
    static Project project(ServerLevel level, VillageMemory.Village v, Plan plan, Home old) {
        EnumMap<Material, Float> raw = madeOf(level, v, plan.id());
        if (raw == null || raw.isEmpty()) return null;
        float share = share(Growth.mood(level, v));
        int oldBeds = 0;
        if (old != null) {
            EnumMap<Material, Float> salvage = madeOf(level, v, old.template());
            if (salvage != null) {
                oldBeds = Math.round(salvage.getOrDefault(Material.BEDS, 0F));
                salvage.forEach((m, n) -> { if (m != Material.BEDS) raw.computeIfPresent(m, (k, need) -> need - n * 0.5F); });
            }
        }
        Project p = new Project();
        p.template = plan.id();
        p.title = plan.title();
        p.upgrade = old;
        p.posted = Days.today(level);
        p.beds = Math.max(0, Math.round(raw.getOrDefault(Material.BEDS, 0F)) - oldBeds);
        // What the old house gives back in full isn't asked for at all.
        raw.forEach((m, n) -> { if (n > 0.5F) p.need.put(m, Math.max(1, Math.min(MOST_OF_ONE, Math.round(n * share)))); });
        if (p.need.isEmpty()) p.need.put(Material.PLANKS, 1);
        return p;
    }

    /** What building {@code id} is made of, as {@code v}'s kind builds it; null if its template can't be read. */
    static EnumMap<Material, Float> madeOf(ServerLevel level, VillageMemory.Village v, ResourceLocation id) {
        StructureTemplate t = level.getStructureManager().get(id).orElse(null);
        if (t == null) return null;
        var blocks = Growth.blocks(level, t, BlockPos.ZERO, Rotation.NONE);
        if (blocks == null) return null;
        Growth.restyle(level, v, id, blocks);
        return materials(blocks);
    }

    // ------------------------------------------------------------------
    // Filling it
    // ------------------------------------------------------------------

    /**
     * The village takes what it still needs from its board's chest and leaves the rest. {@code giver} is who just closed
     * it (thanked, and told what was taken), or null (a hopper, or a check). How many items it took.
     */
    public static int collect(ServerLevel level, VillageMemory.Village v, ServerPlayer giver) {
        Project p = v.project;
        if (p == null || p.stage != Stage.GATHERING || v.board == null) return 0;
        BlockPos at = v.board.chest();
        Container chest = donationChest(level, at);
        if (chest == null) return 0;
        EnumMap<Material, Float> took = new EnumMap<>(Material.class);
        int items = 0;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ItemStack stack = chest.getItem(slot);
            if (stack.isEmpty()) continue;
            for (Material m : p.need.keySet()) {
                float per = worth(stack, m), left = p.left(m);
                if (per <= 0 || left <= 1e-3F) continue;
                int n = Math.min(stack.getCount(), (int) Math.ceil(left / per - 1e-4F));
                if (n <= 0) continue;
                chest.removeItem(slot, n);
                float got = Math.min(left, n * per);
                p.have.merge(m, got, Float::sum);
                took.merge(m, got, Float::sum);
                items += n;
                break;
            }
        }
        if (items == 0) return 0;
        chest.setChanged();
        VillageMemory.get(level).setDirty();
        level.playSound(null, at, SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1, 1);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, at.getX() + .5, at.getY() + 1.1, at.getZ() + .5, 8, .4, .3, .4, 0);
        if (giver != null) {
            if (p.givers.add(giver.getUUID())) VillageMemory.thanked(level, v.bell, giver, 1);
            giver.sendSystemMessage(Component.literal(v.name + "'s builders take " + list(took) + "."
                    + (p.complete() ? "" : " Still needed: " + list(stillNeeded(p)) + ".")).withStyle(ChatFormatting.GRAY));
        }
        if (p.complete()) ready(level, v);
        signs(level, v);
        return items;
    }

    /** The board's full inventory, including a connected chest, without loading either half's chunk. */
    private static Container donationChest(ServerLevel level, BlockPos at) {
        if (!level.hasChunkAt(at)) return null;
        BlockState state = level.getBlockState(at);
        if (state.getBlock() instanceof ChestBlock block) {
            if (state.getValue(ChestBlock.TYPE) != ChestType.SINGLE
                    && !level.hasChunkAt(at.relative(ChestBlock.getConnectedDirection(state)))) return null;
            return ChestBlock.getContainer(block, state, level, at, true);
        }
        return level.getBlockEntity(at) instanceof Container container ? container : null;
    }

    private static EnumMap<Material, Float> stillNeeded(Project p) {
        EnumMap<Material, Float> m = new EnumMap<>(Material.class);
        for (Material k : p.need.keySet()) if (!p.has(k)) m.put(k, p.left(k));
        return m;
    }

    private static String list(EnumMap<Material, Float> m) {
        List<String> parts = new ArrayList<>();
        m.forEach((k, n) -> parts.add(k.count(Math.max(1, (int) Math.ceil(n - 1e-3F)))));
        if (parts.size() <= 1) return String.join("", parts);
        return String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.getLast();
    }

    /** It has everything: work starts the next morning, once there's room. */
    static void ready(ServerLevel level, VillageMemory.Village v) {
        Project p = v.project;
        p.stage = Stage.READY;
        VillageMemory.get(level).setDirty();
        level.playSound(null, v.board == null ? v.bell : v.board.chest(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.2F, 1);
        Component line = Component.literal("That's everything " + v.name + " needs. Its builders start on " + lower(p.title) + " in the morning.")
                .withStyle(ChatFormatting.GREEN);
        for (ServerPlayer pl : level.players())
            if (p.givers.contains(pl.getUUID()) || pl.blockPosition().distSqr(v.bell) <= 48 * 48) pl.sendSystemMessage(line);
        BandChatter.news(level, v.bell, v.name + " has all it needs for " + lower(p.title) + ". Work starts in the morning.", (int) Days.DAY);
        start(level, v);
    }

    /** Plans the building's site ({@link Growth#grow}); false if there's no room yet (it tries again later). */
    static boolean start(ServerLevel level, VillageMemory.Village v) {
        Project p = v.project;
        p.lastTry = level.getGameTime();
        Rebuild.Site site = null;
        if (p.upgrade != null) {
            // Rebuilt where it stands if it still stands and there's still room; if not (it burnt, or something was built
            // in the way), the same house goes up new on open ground, so nothing players gave is wasted.
            if (standing(level, v, p.upgrade) >= STANDING) site = Growth.upgrade(level, v, p.upgrade, p.template);
            if (site == null) p.upgrade = null;
        }
        if (site == null && p.upgrade == null) site = Growth.grow(level, v, p.template);
        // A home for a player has no house to fall back on; if its design has no room, any small house of the kind will do.
        if (site == null && p.owner != null) site = Growth.grow(level, v);
        if (site != null && p.owner != null && site.home() != null) p.template = site.home().template();
        if (site == null) {
            p.noRoom = true;
            VillageMemory.get(level).setDirty();
            return false;
        }
        p.noRoom = false;
        p.stage = Stage.BUILDING;
        VillageMemory.get(level).setDirty();
        signs(level, v);
        return true;
    }

    /** The building is up ({@link Growth#built}): the project is done, and those who gave are thanked. */
    static void built(ServerLevel level, VillageMemory.Village v, Rebuild.Site site) {
        Project p = v.project;
        if (p == null || p.stage != Stage.BUILDING) return;
        if (p.owner != null) Homes.built(level, v, site, p.owner);
        p.stage = Stage.DONE;
        p.finished = Days.today(level);
        VillageMemory.get(level).setDirty();
        Growth.kindness(level, v.bell);
        for (UUID u : p.givers)
            if (level.getPlayerByUUID(u) instanceof ServerPlayer giver) {
                VillageMemory.thanked(level, v.bell, giver, 2);
                giver.sendSystemMessage(Component.literal(v.name + " thanks you for your part in " + lower(p.title) + ".").withStyle(ChatFormatting.GREEN));
            }
        signs(level, v);
    }

    // ------------------------------------------------------------------
    // Each pass
    // ------------------------------------------------------------------

    private static final Map<Long, Long> LOOKED = new HashMap<>(), MENDED = new HashMap<>();

    /**
     * {@code v}'s board and project, once a pass ({@link Growth#consider}): put up or mend the board, post the next project
     * a day after the last is done, take what's in the chest, start building once it's all in, and keep the signs true.
     */
    public static void consider(ServerLevel level, VillageMemory.Village v) {
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) return;
        long now = level.getGameTime(), day = Days.today(level);
        if (v.board == null) {
            Long looked = LOOKED.get(v.bell.asLong());
            if (looked != null && now - looked < RETRY && now >= looked) return;
            LOOKED.put(v.bell.asLong(), now);
            Board b = site(level, v.bell);
            if (b == null) return;
            v.board = b;
            VillageMemory.get(level).setDirty();
            build(level, v);
        } else if (!whole(level, v) && !Long.valueOf(day).equals(MENDED.get(v.bell.asLong()))) {
            MENDED.put(v.bell.asLong(), day);
            build(level, v);
        }
        grown(level, v);
        Project p = v.project;
        // Homes players asked for come first; then the village's own projects.
        if (p == null || p.stage == Stage.DONE && day > p.finished) {
            if (!v.orders.isEmpty()) p = Homes.post(level, v);
            else if (v.houses < MOST) p = post(level, v);
        }
        Homes.tick(level, v);
        if (p != null) {
            if (p.stage == Stage.GATHERING) collect(level, v, null);
            if (p.stage == Stage.GATHERING) Workers.gather(level, v);
            if (p.stage == Stage.READY && (now - p.lastTry >= RETRY || now < p.lastTry)) start(level, v);
            if (p.stage == Stage.BUILDING && !Growth.building(level, v)) { p.stage = Stage.READY; p.lastTry = -1000; }
            if (p.stage == Stage.GATHERING) tell(level, v, p);
        }
        signs(level, v);
    }

    /** {@code v} grew into a bigger tier: word gets round. A place that shrinks says nothing. */
    static void grown(ServerLevel level, VillageMemory.Village v) {
        Tier now = tier(level, v);
        if (v.tier == now.ordinal()) return;
        boolean grew = v.tier >= 0 && now.ordinal() > v.tier;
        v.tier = now.ordinal();
        VillageMemory.get(level).setDirty();
        if (!grew) return;
        BandChatter.news(level, v.bell, now == Tier.TOWN ? v.name + "'s a proper town now. Who'd have thought it."
                : v.name + "'s grown. More a village than a hamlet these days.", (int) Days.DAY);
        Component line = Component.literal(v.name + " has grown into " + now.a + ".").withStyle(ChatFormatting.GREEN);
        for (ServerPlayer pl : level.players()) if (pl.blockPosition().distSqr(v.bell) <= 96 * 96) pl.sendSystemMessage(line);
        signs(level, v);
    }

    /** Anyone new by the board hears what it's for. */
    private static void tell(ServerLevel level, VillageMemory.Village v, Project p) {
        if (v.board == null) return;
        for (ServerPlayer pl : level.players()) {
            if (pl.isSpectator() || pl.blockPosition().distSqr(v.board.origin) > TELL * TELL || !p.told.add(pl.getUUID())) continue;
            VillageMemory.get(level).setDirty();
            pl.sendSystemMessage(Component.literal(v.name + "'s notice board: the village is gathering for " + lower(p.title)
                    + ". Leave what it needs in the chest below the signs.").withStyle(ChatFormatting.GRAY));
        }
    }

    @SubscribeEvent
    public static void closed(PlayerContainerEvent.Close event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getContainer() instanceof ChestMenu menu)) return;
        ServerLevel level = player.serverLevel();
        Container c = menu.getContainer();
        // The opened chest owns the contribution, even if another village's bell is closer to the player.
        for (VillageMemory.Village v : VillageMemory.all(level)) {
            if (v.board == null || !level.hasChunkAt(v.board.chest())
                    || !(level.getBlockEntity(v.board.chest()) instanceof ChestBlockEntity chest)) continue;
            if (c == chest || c instanceof CompoundContainer both && both.contains(chest)) {
                collect(level, v, player);
                return;
            }
        }
    }

    /** A village was forgotten: when it last looked for room or mended its board goes with it. */
    static void forgotten(BlockPos bell) {
        LOOKED.remove(bell.asLong());
        MENDED.remove(bell.asLong());
    }

    @SubscribeEvent
    public static void stop(ServerStoppingEvent event) {
        PLANS.clear();
        LOOKED.clear();
        MENDED.clear();
    }

    // ------------------------------------------------------------------
    // The signs
    // ------------------------------------------------------------------

    /** Writes the board's six signs, where they've changed. */
    public static void signs(ServerLevel level, VillageMemory.Village v) {
        if (v.board == null) return;
        Component[][] text = text(level, v);
        for (int i = 0; i < 6; i++) {
            BlockPos at = v.board.sign(i);
            if (!level.hasChunkAt(at) || !(level.getBlockEntity(at) instanceof SignBlockEntity sign)) continue;
            SignText now = sign.getFrontText();
            boolean same = true;
            for (int j = 0; j < 4; j++) if (!now.getMessage(j, false).equals(text[i][j])) same = false;
            if (same) continue;
            SignText t = new SignText().setColor(DyeColor.BLACK);
            for (int j = 0; j < 4; j++) t = t.setMessage(j, text[i][j]);
            sign.setText(t, true);
            sign.setWaxed(true);
        }
    }

    /** What the six signs say: the village, the project and how far along it is; then two lines for each material. */
    public static Component[][] text(ServerLevel level, VillageMemory.Village v) {
        Component[][] lines = new Component[6][4];
        for (Component[] sign : lines) java.util.Arrays.fill(sign, Component.empty());
        lines[0][0] = Component.literal(v.name).withStyle(ChatFormatting.BOLD);
        Project p = v.project;
        if (p == null) {
            if (v.houses >= MOST) { lines[0][1] = Component.literal("Thank you for"); lines[0][2] = Component.literal("everything!"); }
            else lines[0][2] = Component.literal("Notices soon");
            return lines;
        }
        List<String> title = wrap(p.title, 2);
        for (int j = 0; j < title.size(); j++) lines[0][1 + j] = Component.literal(title.get(j));
        if (title.size() == 1 && v.tier >= 0) lines[0][2] = Component.literal("- " + Tier.values()[v.tier].label + " -");
        // Black throughout: coloured text (dark green was tried) can't be read on the board's planks.
        lines[0][3] = Component.literal(status(level, v, p));
        int k = 0;
        for (Material m : Material.values()) {
            if (!p.need.containsKey(m) || k >= 10) continue;
            int sign = 1 + k / 2, line = (k % 2) * 2;
            int need = p.need(m), have = (int) Math.floor(p.have(m) + 1e-3F);
            lines[sign][line] = Component.literal(m.label);
            lines[sign][line + 1] = Component.literal(p.has(m) ? "Done (" + need + ")" : have + " / " + need);
            k++;
        }
        return lines;
    }

    static String status(ServerLevel level, VillageMemory.Village v, Project p) {
        return switch (p.stage) {
            case GATHERING -> Math.min(99, (int) Math.floor(p.fraction() * 100)) + "% gathered";
            case READY -> p.noRoom ? "Finding room" : "Starts at dawn";
            case BUILDING -> {
                for (Rebuild.Site s : Rebuild.all(level))
                    if (s.growth() && s.bell().equals(v.bell) && s.size() > 0)
                        yield s.started() && s.done() > 0 ? "Building: " + Math.min(99, s.done() * 100 / s.size()) + "%" : "Starts at dawn";
                yield "Building";
            }
            case DONE -> "Finished!";
        };
    }

    /** {@code text} in at most {@code lines} lines that each fit on a sign. */
    static List<String> wrap(String text, int lines) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (width(next) <= SIGN_WIDTH || line.isEmpty()) { line = new StringBuilder(next); continue; }
            out.add(line.toString());
            line = new StringBuilder(word);
        }
        if (!line.isEmpty()) out.add(line.toString());
        return out.size() <= lines ? out : out.subList(0, lines);
    }

    /** About how wide {@code s} is in the game's font (pixels, with the gap after each letter). */
    static int width(String s) {
        int w = 0;
        for (char c : s.toCharArray()) {
            w += switch (c) {
                case 'i', '!', '.', ',', '\'', ':', ';', '|' -> 2;
                case 'l', '`' -> 3;
                case 't', 'I', ' ', '[', ']' -> 4;
                case 'f', 'k', '(', ')', '<', '>', '{', '}', '*', '"' -> 5;
                default -> 6;
            };
        }
        return w;
    }

    static String lower(String title) { return title.isEmpty() ? title : Character.toLowerCase(title.charAt(0)) + title.substring(1); }

    /** One line for the village memory command. */
    public static String describe(ServerLevel level, VillageMemory.Village v) {
        Project p = v.project;
        String board = v.board == null ? "no board yet" : "board at " + v.board.origin.toShortString() + (whole(level, v) ? "" : " (damaged)");
        if (p == null) return "Project: none (" + board + ")";
        StringBuilder s = new StringBuilder(tier(level, v).label + ". Project: " + p.title + " (" + p.template + (p.upgrade == null ? "" : ", rebuilding "
                + p.upgrade.template().getPath() + " at " + p.upgrade.origin().toShortString()) + "), " + status(level, v, p) + ", " + board + ":");
        for (Material m : p.need.keySet()) s.append(' ').append(m.label).append(' ').append((int) Math.floor(p.have(m) + 1e-3F)).append('/').append(p.need(m));
        return s.toString();
    }

    /** Posts rebuilding one of {@code v}'s homes bigger, as a place of {@code tier} would (admin, previews). True if it had one. */
    public static boolean rebuild(ServerLevel level, VillageMemory.Village v, Tier tier) {
        for (Option o : options(level, v, tier)) {
            if (o.old == null) continue;
            Project p = project(level, v, o.plan, o.old);
            if (p == null) continue;
            v.project = p;
            VillageMemory.get(level).setDirty();
            signs(level, v);
            return true;
        }
        return false;
    }

    /** Fills the project as if players had brought it all (admin, previews, tests). */
    public static void fill(ServerLevel level, VillageMemory.Village v) {
        Project p = v.project;
        if (p == null || p.stage != Stage.GATHERING) return;
        p.need.forEach((m, n) -> p.have.put(m, (float) n));
        VillageMemory.get(level).setDirty();
        ready(level, v);
        signs(level, v);
    }

    private Projects() {}
}
