package com.jayptucker.heroesevolved.village;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What villages remember, kept per level: each village (by its bell) has a name of its own, a history of what happened to
 * it and how it ended, how much it owes each player, and whether it has fallen. Events record themselves here when they
 * end ({@link #record}); a village saved thinks well of its heroes (vanilla gossip, so trades are cheaper), greets them by
 * name, and puts up a memorial by its bell (a bust for one who saved it twice); a village that falls stays a ruin, with a
 * stone for the lost. Players who come near a fallen village are told what it was.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class VillageMemory extends SavedData {
    static final String NAME = HeroesEvolved.MOD_ID + "_village_memory";
    /** How it ended. */
    public enum Outcome { SAVED, HIT, FALLEN }
    /** Standing given to whoever ended it, and to everyone else who was there, when a village is saved. */
    public static final int HERO_STANDING = 3, PRESENT_STANDING = 1;
    /** Standing at which a village's people greet a player by name. */
    public static final int GREET_STANDING = 3;
    /** How many memorials a village keeps by its bell. */
    public static final int MEMORIALS = 4;
    /** A village's people greet a player at most this often; a fallen village is noticed from this far. */
    static final int GREET_EVERY = 2400, GREET_REACH = 7, FALLEN_REACH = 64;

    /** One thing that happened to a village. */
    public record Entry(String what, long day, Outcome outcome, int alive, int total, String hero, UUID heroId) {}

    /** One village's memory. */
    public static final class Village {
        final BlockPos bell;
        String name;
        final List<Entry> history = new ArrayList<>();
        final Map<UUID, Integer> standing = new HashMap<>();
        final Map<UUID, String> names = new HashMap<>();
        boolean fallen;
        final List<BlockPos> memorials = new ArrayList<>();
        final List<UUID> busts = new ArrayList<>();
        final List<UUID> told = new ArrayList<>();
        /** How it is growing (village/Growth): houses it built, families it took in, kindnesses done it (their days),
         * and the days it last grew, wore or lost someone. */
        int houses, sheltered;
        final List<Long> kindness = new ArrayList<>();
        long firstDay = -1, lastGrowth = -100, lastWear = -100, lastLeave = -100;
        /** Its notice board and what it is gathering for (village/Projects). */
        Projects.Board board;
        Projects.Project project;
        /** The homes it built itself (so it can rebuild them bigger), the homes it has torn down to rebuild, and the tier
         * it last told anyone it was (-1: not yet). */
        final List<Projects.Home> homes = new ArrayList<>();
        final java.util.Set<Long> replaced = new java.util.HashSet<>();
        int tier = -1;
        /** Homes players asked it for (village/Homes), in order; the homes they own; owners still to be told it's ready. */
        final List<UUID> orders = new ArrayList<>();
        final Map<UUID, Homes.Owned> owned = new HashMap<>();
        final java.util.Set<UUID> waking = new java.util.HashSet<>();
        /** Its market, while one is on (village/Markets), the day it last held one and the day it last told the radio. */
        Markets.Market market;
        /** What was already missing from its buildings when it was first looked over (village/Repairs): how it was, not damage. */
        final java.util.Set<Long> unbuilt = new java.util.HashSet<>();
        boolean baselined;
        long marketHeld = -1, marketNews = -1;
        public Markets.Market market() { return market; }
        public Map<UUID, Homes.Owned> owned() { return Map.copyOf(owned); }
        public List<UUID> orders() { return List.copyOf(orders); }
        Village(BlockPos bell) { this.bell = bell; }
        public Projects.Board board() { return board; }
        public Projects.Project project() { return project; }
        /** Sets how many things it has built, which moves its tier (previews). */
        public void setBuiltForPreview(int built) { houses = built; }
        /** The homes it built itself (previews). */
        public List<Projects.Home> homesForPreview() { return List.copyOf(homes); }
        public int houses() { return houses; }
        public int sheltered() { return sheltered; }

        public BlockPos bell() { return bell; }
        public String name() { return name; }
        public List<Entry> history() { return List.copyOf(history); }
        public boolean fallen() { return fallen; }
        public int standing(UUID player) { return standing.getOrDefault(player, 0); }
        /** Whether {@code player} has ended something that saved it: it thanks them as its hero (and covers for them). */
        public boolean owes(UUID player) { return heroOf(this, player) > 0; }
        public List<BlockPos> memorials() { return List.copyOf(memorials); }
        public List<UUID> busts() { return List.copyOf(busts); }
        /** The last thing it was saved from, or null. */
        public Entry lastSaved() {
            for (int i = history.size() - 1; i >= 0; i--) if (history.get(i).outcome == Outcome.SAVED) return history.get(i);
            return null;
        }
    }

    private final Map<Long, Village> villages = new LinkedHashMap<>();

    public static VillageMemory get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(VillageMemory::new, VillageMemory::load), NAME);
    }

    // ------------------------------------------------------------------
    // Names
    // ------------------------------------------------------------------

    private static final String[] FIRST = {"Ash", "Bram", "Cold", "Elm", "Fern", "Glen", "Hart", "Holly", "Kings", "Lark", "Mill",
            "Oak", "Red", "Stone", "Thorn", "West", "Wil", "Yew", "Brae", "Cinder", "Dun", "Har", "Lin", "Marsh"};
    private static final String[] LAST = {"ford", "brook", "field", "ham", "ley", "stead", "wick", "dale", "moor", "bury", "ton",
            "well", "hollow", "mere", "croft", "gate"};

    /** A village's own name: always the same for the same bell. */
    static String nameFor(BlockPos bell) {
        long h = bell.asLong() * 0x9E3779B97F4A7C15L;
        h ^= h >>> 31;
        String first = FIRST[(int) Math.floorMod(h, FIRST.length)], last = LAST[(int) Math.floorMod(h >>> 17, LAST.length)];
        // "Ashford", "Holly-dale" would read oddly: join with no hyphen, and avoid doubled letters at the seam.
        if (first.charAt(first.length() - 1) == last.charAt(0)) last = last.substring(1);
        return first + last;
    }

    /** The village round {@code bell} as remembered, made (and named) if this is the first anyone heard of it. */
    public static Village of(ServerLevel level, BlockPos bell) {
        VillageMemory data = get(level);
        Village v = data.villages.get(bell.asLong());
        if (v == null) {
            v = new Village(bell.immutable());
            v.name = nameFor(bell);
            v.firstDay = level.getDayTime() / 24_000 + 1;
            data.villages.put(bell.asLong(), v);
            data.setDirty();
        }
        return v;
    }

    /** Every village remembered in {@code level}. */
    public static List<Village> all(ServerLevel level) { return List.copyOf(get(level).villages.values()); }

    /**
     * {@code p} did the village round {@code at} a kindness (a favour): it thinks {@code by} better of them. Not an event
     * in its history, and no memorial.
     */
    public static void thanked(ServerLevel level, BlockPos at, ServerPlayer p, int by) {
        if (!Villages.hasBell(level, at)) return;
        Village v = of(level, Villages.bell(level, at));
        thank(v, p, by);
        get(level).setDirty();
    }

    /** The remembered village whose bell is nearest {@code at} (within a village's reach), or null. */
    public static Village near(ServerLevel level, BlockPos at) {
        Village best = null;
        double bestD = (double) Villages.RADIUS * Villages.RADIUS;
        for (Village v : get(level).villages.values()) {
            double d = v.bell.distSqr(at);
            if (d <= bestD) { bestD = d; best = v; }
        }
        return best;
    }

    /** The name of the village at {@code at} (the bell there, if it has one), or null if there is no village. */
    public static String name(ServerLevel level, BlockPos at) {
        if (!Villages.hasBell(level, at)) return null;
        return of(level, Villages.bell(level, at)).name;
    }

    // ------------------------------------------------------------------
    // Remembering
    // ------------------------------------------------------------------

    /**
     * Something happened to the village round {@code bell}: {@code what} ("the wildfire", "a renegade"), how it ended,
     * how many of its people lived, who ended it ({@code hero}, may be null) and who else was there.
     */
    public static Village record(ServerLevel level, BlockPos bell, String what, Outcome outcome, ServerPlayer hero,
                                 Collection<ServerPlayer> present, int alive, int total) {
        Village v = of(level, Villages.bell(level, bell));
        VillageMemory data = get(level);
        long day = level.getDayTime() / 24000 + 1;
        v.history.add(new Entry(what, day, outcome, alive, total, hero == null ? null : hero.getName().getString(),
                hero == null ? null : hero.getUUID()));
        if (outcome == Outcome.SAVED) {
            for (ServerPlayer p : present) if (p != hero) thank(v, p, PRESENT_STANDING);
            if (hero != null) thank(v, hero, HERO_STANDING);
            gossip(level, v, hero, present);
            memorial(level, v, what, day, hero);
            if (hero != null && heroOf(v, hero.getUUID()) >= 2 && !v.busts.contains(hero.getUUID())) bust(level, v, hero);
        } else if (outcome == Outcome.HIT && hero != null) {
            thank(v, hero, PRESENT_STANDING);
        } else if (outcome == Outcome.FALLEN && !v.fallen) {
            v.fallen = true;
            ruin(level, v.bell);
            lost(level, v, what, day);
        }
        data.setDirty();
        // Ending it in front of everyone gets talked about too (company/CompanyAttention).
        if (outcome != Outcome.FALLEN) com.jayptucker.heroesevolved.company.CompanyAttention.deed(level, v.bell, hero, present, outcome == Outcome.SAVED);
        HeroesEvolved.LOGGER.info("Village {} remembers {}: {} ({} of {} lived{})", v.name, what, outcome, alive, total,
                hero == null ? "" : ", ended by " + hero.getName().getString());
        return v;
    }

    private static void thank(Village v, ServerPlayer p, int by) {
        v.standing.merge(p.getUUID(), by, Integer::sum);
        v.names.put(p.getUUID(), p.getName().getString());
    }

    /** How many times {@code player} has ended something that saved this village. */
    static int heroOf(Village v, UUID player) {
        int n = 0;
        for (Entry e : v.history) if (e.outcome == Outcome.SAVED && player.equals(e.heroId)) n++;
        return n;
    }

    /**
     * The village's people think well of those who saved it, as vanilla's do of someone who cured a zombie villager: a
     * lasting gossip about whoever ended it, and a smaller one that spreads for everyone else who was there.
     */
    static void gossip(ServerLevel level, Village v, ServerPlayer hero, Collection<ServerPlayer> present) {
        for (Villager villager : Villages.members(level, v.bell)) {
            if (hero != null) {
                villager.getGossips().add(hero.getUUID(), GossipType.MAJOR_POSITIVE, 20);
                villager.getGossips().add(hero.getUUID(), GossipType.MINOR_POSITIVE, 25);
            }
            for (ServerPlayer p : present) if (p != hero) villager.getGossips().add(p.getUUID(), GossipType.MINOR_POSITIVE, 25);
        }
    }

    // ------------------------------------------------------------------
    // Memorials
    // ------------------------------------------------------------------

    /** A stone by the bell with a sign: what came, the day, who ended it. Up to {@link #MEMORIALS}. */
    static void memorial(ServerLevel level, Village v, String what, long day, ServerPlayer hero) {
        if (v.memorials.size() >= MEMORIALS) return;
        BlockPos spot = spot(level, v, 2);
        if (spot == null) return;
        level.setBlockAndUpdate(spot, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
        List<String> lines = new ArrayList<>(List.of("IN THANKS"));
        lines.addAll(wrap(title(what), 2));
        lines.add("Day " + day);
        if (lines.size() < 4) lines.add(hero == null ? "to all who came" : hero.getName().getString());
        sign(level, spot.above(), v.bell, Blocks.OAK_SIGN.defaultBlockState(), DyeColor.BLACK, false, lines.toArray(String[]::new));
        if (hero != null && !lines.getLast().equals(hero.getName().getString())) back(level, spot.above(), hero.getName().getString());
        v.memorials.add(spot);
        level.playSound(null, spot, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1, 0.8F);
    }

    /** Their likeness: their head on a pedestal by the bell, a plaque on its front. */
    static void bust(ServerLevel level, Village v, ServerPlayer hero) {
        BlockPos spot = spot(level, v, 2);
        if (spot == null) return;
        Direction toBell = facing(spot, v.bell);
        level.setBlockAndUpdate(spot, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
        level.setBlockAndUpdate(spot.above(), Blocks.PLAYER_HEAD.defaultBlockState()
                .setValue(SkullBlock.ROTATION, RotationSegment.convertToSegment(yaw(spot, v.bell) + 180)));
        if (level.getBlockEntity(spot.above()) instanceof SkullBlockEntity skull)
            skull.setOwner(new net.minecraft.world.item.component.ResolvableProfile(hero.getGameProfile()));
        BlockPos plaque = spot.relative(toBell);
        if (level.getBlockState(plaque).canBeReplaced() && level.getBlockState(plaque).getFluidState().isEmpty())
            sign(level, plaque, v.bell, Blocks.DARK_OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, toBell),
                    DyeColor.WHITE, true, hero.getName().getString(), "who saved", v.name, "twice");
        v.busts.add(hero.getUUID());
        v.memorials.add(spot);
        for (ServerPlayer p : level.players())
            if (p.blockPosition().distSqr(v.bell) <= 96 * 96)
                p.sendSystemMessage(Component.literal(v.name + " has carved a likeness of " + hero.getName().getString() + " by its bell.")
                        .withStyle(ChatFormatting.GOLD));
    }

    /** A stone for the lost by the bell of a village that fell. */
    static void lost(ServerLevel level, Village v, String what, long day) {
        BlockPos spot = spot(level, v, 2);
        if (spot == null) return;
        level.setBlockAndUpdate(spot, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
        List<String> lines = new ArrayList<>(List.of(v.name.toUpperCase(), "fell on day " + day));
        lines.addAll(wrap("to " + what, 2));
        if (lines.size() < 4) lines.add("Remember them");
        sign(level, spot.above(), v.bell, Blocks.DARK_OAK_SIGN.defaultBlockState(), DyeColor.WHITE, true, lines.toArray(String[]::new));
        if (!lines.getLast().equals("Remember them")) back(level, spot.above(), "Remember them");
        v.memorials.add(spot);
    }

    /**
     * Where a memorial can stand: open, solid, dry ground near the bell (rings out from 3 to 8 blocks), with room for
     * {@code height} blocks, not touching another memorial. Null if there is nowhere.
     */
    static BlockPos spot(ServerLevel level, Village v, int height) {
        for (int ring = 3; ring <= 8; ring++)
            for (int i = 0; i < 16; i++) {
                double a = i * Math.PI / 8 + ring * 0.37;
                int x = v.bell.getX() + (int) Math.round(Math.cos(a) * ring), z = v.bell.getZ() + (int) Math.round(Math.sin(a) * ring);
                if (!level.hasChunkAt(new BlockPos(x, v.bell.getY(), z))) continue;
                BlockPos at = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
                if (Math.abs(at.getY() - v.bell.getY()) > 4) continue;
                BlockPos below = at.below();
                if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP) || !level.getFluidState(below).isEmpty()) continue;
                if (PlayerBuilt.is(level, below)) continue;   // never on a player's floor
                boolean room = true;
                for (int y = 0; y < height; y++) {
                    BlockState s = level.getBlockState(at.above(y));
                    if (!s.isAir() && !(s.canBeReplaced() && s.getFluidState().isEmpty()) || PlayerBuilt.is(level, at.above(y))) { room = false; break; }
                }
                if (!room) continue;
                boolean touching = false;
                for (BlockPos m : v.memorials) if (m.distManhattan(at) <= 2) { touching = true; break; }
                if (!touching) return at;
            }
        return null;
    }

    private static float yaw(BlockPos from, BlockPos to) {
        return (float) Math.toDegrees(Math.atan2(-(to.getX() - from.getX()), to.getZ() - from.getZ()));
    }

    private static Direction facing(BlockPos from, BlockPos to) {
        return Direction.getNearest(to.getX() - from.getX(), 0, to.getZ() - from.getZ());
    }

    /** A sign at {@code at} (standing, turned to the bell, or the wall sign given), with four lines. */
    private static void sign(ServerLevel level, BlockPos at, BlockPos bell, BlockState state, DyeColor color, boolean glowing, String... lines) {
        if (state.getBlock() instanceof StandingSignBlock)
            state = state.setValue(StandingSignBlock.ROTATION, RotationSegment.convertToSegment(yaw(at, bell)));
        level.setBlockAndUpdate(at, state);
        if (!(level.getBlockEntity(at) instanceof SignBlockEntity sign)) return;
        SignText text = new SignText().setColor(color).setHasGlowingText(glowing);
        for (int i = 0; i < Math.min(4, lines.length); i++) text = text.setMessage(i, Component.literal(lines[i]));
        sign.setText(text, true);
        sign.setWaxed(true);
        sign.setChanged();
        level.sendBlockUpdated(at, state, state, Block.UPDATE_ALL);
    }

    /** Words split into lines a sign can hold (fifteen characters), at most {@code most} of them. */
    static List<String> wrap(String words, int most) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : words.split(" ")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > 15) {
                out.add(line.toString());
                line.setLength(0);
                if (out.size() == most) break;
            }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (line.length() > 0 && out.size() < most) out.add(line.toString());
        return out;
    }

    /** A line on the back of the sign at {@code at}. */
    private static void back(ServerLevel level, BlockPos at, String line) {
        if (!(level.getBlockEntity(at) instanceof SignBlockEntity sign)) return;
        sign.setText(new SignText().setColor(DyeColor.BLACK).setMessage(1, Component.literal(line)), false);
        sign.setChanged();
        level.sendBlockUpdated(at, level.getBlockState(at), level.getBlockState(at), Block.UPDATE_ALL);
    }

    /** "the wildfire" as a sign's line: "The Wildfire". */
    static String title(String what) {
        StringBuilder out = new StringBuilder();
        for (String word : what.split(" ")) {
            if (word.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    // ------------------------------------------------------------------
    // A village that fell
    // ------------------------------------------------------------------

    /**
     * Left in ruins (only where {@code mobGriefing} allows): doors torn off, windows broken, beds and torches gone, fires
     * out, walls holed and mossed over, cobwebs in its corners. The bell is left.
     */
    public static int ruin(ServerLevel level, BlockPos bell) {
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) return 0;
        var random = level.random;
        int changed = 0;
        // Every block of the village's box, once (about a hundred thousand reads: a few milliseconds, once per fall).
        for (BlockPos p : BlockPos.betweenClosed(bell.offset(-36, -6, -36), bell.offset(36, 12, 36))) {
            if (!level.hasChunkAt(p)) continue;
            BlockState s = level.getBlockState(p);
            if (s.isAir()) {
                // A cobweb in a corner: under a roof, against a wall.
                if (random.nextFloat() < 0.04F && !level.getBlockState(p.above()).isAir() && walls(level, p) >= 1
                        && !level.canSeeSky(p) && !PlayerBuilt.is(level, p.above())) { level.setBlock(p.immutable(), Blocks.COBWEB.defaultBlockState(), 3); changed++; }
                continue;
            }
            if (s.is(Blocks.BELL) || level.getBlockEntity(p) != null && !(s.getBlock() instanceof BedBlock)) continue;
            if (PlayerBuilt.is(level, p)) continue;   // what players built near it doesn't fall with it
            BlockState to = null;
            if (s.getBlock() instanceof DoorBlock) {
                if (s.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) to = Blocks.AIR.defaultBlockState();
            } else if (s.getBlock() instanceof BedBlock) to = Blocks.AIR.defaultBlockState();
            else if (s.is(net.neoforged.neoforge.common.Tags.Blocks.GLASS_BLOCKS) || s.is(net.neoforged.neoforge.common.Tags.Blocks.GLASS_PANES)
                    || s.is(Blocks.TORCH) || s.is(Blocks.WALL_TORCH) || s.is(BlockTags.FLOWER_POTS)) to = Blocks.AIR.defaultBlockState();
            else if (s.getBlock() instanceof CampfireBlock && s.getValue(CampfireBlock.LIT)) to = s.setValue(CampfireBlock.LIT, false);
            else if (s.is(Blocks.COBBLESTONE)) to = random.nextBoolean() ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : null;
            else if (s.is(Blocks.STONE_BRICKS)) to = (random.nextBoolean() ? Blocks.CRACKED_STONE_BRICKS : Blocks.MOSSY_STONE_BRICKS).defaultBlockState();
            else if (s.is(BlockTags.PLANKS) && random.nextFloat() < 0.15F && walls(level, p) < 4) to = Blocks.AIR.defaultBlockState();
            else if (s.is(BlockTags.CROPS)) to = Blocks.AIR.defaultBlockState();
            else if (s.is(BlockTags.SLABS) && random.nextFloat() < 0.12F && level.canSeeSky(p.above())) to = Blocks.AIR.defaultBlockState();
            if (to == null) continue;
            // Two-block things (doors, beds) take their other half with them, with no drops.
            level.setBlock(p.immutable(), to, Block.UPDATE_ALL);
            changed++;
        }
        return changed;
    }

    private static int walls(ServerLevel level, BlockPos p) {
        int n = 0;
        for (Direction d : Direction.Plane.HORIZONTAL) if (level.getBlockState(p.relative(d)).isSolid()) n++;
        return n;
    }

    // ------------------------------------------------------------------
    // Greetings, and remembering the fallen
    // ------------------------------------------------------------------

    private static final Map<UUID, Long> GREETED = new HashMap<>();
    private static final Map<Villager, String> LAST_GREETING = new java.util.WeakHashMap<>();

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();
        if (now % 40 != 0) return;
        for (ServerLevel level : server.getAllLevels()) {
            VillageMemory data = level.getDataStorage().get(new Factory<>(VillageMemory::new, VillageMemory::load), NAME);
            if (data == null || data.villages.isEmpty()) continue;
            for (ServerPlayer p : level.players()) {
                if (p.isSpectator()) continue;
                Village v = near(level, p.blockPosition());
                if (v == null) continue;
                if (v.fallen) remember(level, data, v, p);
                else if (v.standing(p.getUUID()) >= GREET_STANDING) greet(level, v, p, now);
            }
        }
    }

    private static void remember(ServerLevel level, VillageMemory data, Village v, ServerPlayer p) {
        if (v.told.contains(p.getUUID()) || p.blockPosition().distSqr(v.bell) > (long) FALLEN_REACH * FALLEN_REACH) return;
        v.told.add(p.getUUID());
        data.setDirty();
        Entry fell = null;
        for (Entry e : v.history) if (e.outcome == Outcome.FALLEN) fell = e;
        p.sendSystemMessage(Component.literal("This was " + v.name + "." + (fell == null ? "" : " It fell to " + fell.what + " on day " + fell.day + "."))
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    /** Someone of a village that owes {@code p} says hello, by name, now and then. */
    private static void greet(ServerLevel level, Village v, ServerPlayer p, long now) {
        Long last = GREETED.get(p.getUUID());
        if (last != null && now - last < GREET_EVERY) return;
        Villager who = null;
        double best = GREET_REACH * GREET_REACH;
        for (Villager villager : level.getEntitiesOfClass(Villager.class, p.getBoundingBox().inflate(GREET_REACH),
                x -> x.isAlive() && !x.isBaby() && !x.isSleeping() && x.hasLineOfSight(p))) {
            double d = villager.distanceToSqr(p);
            if (d < best && Villages.belongs(level, v.bell, villager)) { best = d; who = villager; }
        }
        if (who == null) return;
        GREETED.put(p.getUUID(), now);
        String name = p.getName().getString();
        Entry saved = v.lastSaved();
        List<String> lines = new ArrayList<>(List.of("Good to see you, " + name + "!", "It's " + name + "!", "Thank you, " + name + ".",
                name + "! " + v.name + " won't forget."));
        if (saved != null) lines.add(v.name + " won't forget " + saved.what + ", " + name + ".");
        if (saved != null && p.getUUID().equals(saved.heroId)) lines.add("You stopped " + saved.what + ", " + name + ".");
        String line = lines.get(level.random.nextInt(lines.size()));
        // Through the village's own speech bubbles (village/Shouts), which keep track of what they put up.
        Shouts.talk(level, who, line, 60);
        LAST_GREETING.put(who, line);
        who.playAmbientSound();
        who.getLookControl().setLookAt(p);
    }

    /** What {@code villager} last said to greet someone, or null (tests). */
    public static String greeting(Villager villager) { return LAST_GREETING.get(villager); }

    /** Lets {@code p} be greeted again at once (tests, previews). */
    public static void greetSoon(ServerPlayer p) { GREETED.remove(p.getUUID()); }

    @SubscribeEvent
    public static void stop(ServerStoppingEvent event) { GREETED.clear(); }

    // ------------------------------------------------------------------
    // Admin
    // ------------------------------------------------------------------

    /** A few lines on the village nearest {@code at}. */
    public static List<String> describe(ServerLevel level, BlockPos at) {
        Village v = near(level, at);
        if (v == null) return List.of("No remembered village near here.");
        List<String> out = new ArrayList<>();
        out.add(v.name + (v.fallen ? " (fallen)" : "") + ", bell at " + v.bell.toShortString());
        for (Entry e : v.history)
            out.add("  Day " + e.day + ": " + e.what + ", " + e.outcome.name().toLowerCase() + ", " + e.alive + " of " + e.total + " lived"
                    + (e.hero == null ? "" : ", ended by " + e.hero));
        v.standing.forEach((id, s) -> out.add("  Owes " + v.names.getOrDefault(id, id.toString()) + ": " + s));
        if (!v.fallen) out.add("  Doing: " + Growth.mood(level, v).name().toLowerCase() + " (score " + Growth.score(level, v) + "), "
                + v.houses + " new building(s), " + v.sheltered + " family(ies) taken in" + (Growth.haven(level, v) ? ", a haven" : ""));
        if (!v.fallen) out.add("  " + Projects.describe(level, v));
        out.addAll(Homes.describe(v));
        return out;
    }

    /** Forgets the village nearest {@code at}. True if there was one. */
    public static boolean forget(ServerLevel level, BlockPos at) {
        Village v = near(level, at);
        if (v == null) return false;
        get(level).villages.remove(v.bell.asLong());
        get(level).setDirty();
        Projects.forgotten(v.bell);
        Repairs.forgotten(v.bell);
        return true;
    }

    // ------------------------------------------------------------------
    // Saving
    // ------------------------------------------------------------------

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Village v : villages.values()) {
            CompoundTag t = new CompoundTag();
            t.putLong("bell", v.bell.asLong());
            t.putString("name", v.name);
            t.putBoolean("fallen", v.fallen);
            ListTag history = new ListTag();
            for (Entry e : v.history) {
                CompoundTag h = new CompoundTag();
                h.putString("what", e.what);
                h.putLong("day", e.day);
                h.putString("outcome", e.outcome.name());
                h.putInt("alive", e.alive);
                h.putInt("total", e.total);
                if (e.hero != null) h.putString("hero", e.hero);
                if (e.heroId != null) h.putUUID("heroId", e.heroId);
                history.add(h);
            }
            t.put("history", history);
            ListTag standing = new ListTag();
            v.standing.forEach((id, s) -> {
                CompoundTag st = new CompoundTag();
                st.putUUID("id", id);
                st.putInt("standing", s);
                st.putString("name", v.names.getOrDefault(id, ""));
                standing.add(st);
            });
            t.put("standing", standing);
            ListTag memorials = new ListTag();
            for (BlockPos m : v.memorials) memorials.add(net.minecraft.nbt.LongTag.valueOf(m.asLong()));
            t.put("memorials", memorials);
            ListTag busts = new ListTag();
            for (UUID id : v.busts) busts.add(NbtUtils.createUUID(id));
            t.put("busts", busts);
            ListTag told = new ListTag();
            for (UUID id : v.told) told.add(NbtUtils.createUUID(id));
            t.put("told", told);
            t.putInt("houses", v.houses);
            t.putInt("sheltered", v.sheltered);
            t.putLongArray("kindness", v.kindness.stream().mapToLong(Long::longValue).toArray());
            t.putLong("first_day", v.firstDay);
            t.putLong("last_growth", v.lastGrowth);
            t.putLong("last_wear", v.lastWear);
            t.putLong("last_leave", v.lastLeave);
            if (v.board != null) t.put("board", v.board.save());
            if (v.project != null) t.put("project", v.project.save());
            ListTag homes = new ListTag();
            for (Projects.Home h : v.homes) homes.add(h.save());
            t.put("homes", homes);
            t.putLongArray("replaced", v.replaced.stream().mapToLong(Long::longValue).toArray());
            t.putInt("tier", v.tier);
            ListTag orders = new ListTag();
            for (UUID id : v.orders) orders.add(NbtUtils.createUUID(id));
            t.put("orders", orders);
            ListTag owned = new ListTag();
            v.owned.forEach((id, o) -> { CompoundTag ot = o.save(); ot.putUUID("owner", id); owned.add(ot); });
            t.put("owned", owned);
            ListTag waking = new ListTag();
            for (UUID id : v.waking) waking.add(NbtUtils.createUUID(id));
            t.put("waking", waking);
            if (v.market != null) t.put("market", v.market.save());
            t.putLong("market_held", v.marketHeld);
            t.putLong("market_news", v.marketNews);
            t.putLongArray("unbuilt", v.unbuilt.stream().mapToLong(Long::longValue).toArray());
            t.putBoolean("baselined", v.baselined);
            list.add(t);
        }
        tag.put("villages", list);
        return tag;
    }

    static VillageMemory load(CompoundTag tag, HolderLookup.Provider registries) {
        VillageMemory data = new VillageMemory();
        for (Tag raw : tag.getList("villages", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) raw;
            Village v = new Village(BlockPos.of(t.getLong("bell")));
            v.name = t.getString("name").isEmpty() ? nameFor(v.bell) : t.getString("name");
            v.fallen = t.getBoolean("fallen");
            for (Tag hr : t.getList("history", Tag.TAG_COMPOUND)) {
                CompoundTag h = (CompoundTag) hr;
                Outcome outcome;
                try { outcome = Outcome.valueOf(h.getString("outcome")); } catch (IllegalArgumentException e) { outcome = Outcome.HIT; }
                v.history.add(new Entry(h.getString("what"), h.getLong("day"), outcome, h.getInt("alive"), h.getInt("total"),
                        h.contains("hero") ? h.getString("hero") : null, h.hasUUID("heroId") ? h.getUUID("heroId") : null));
            }
            for (Tag sr : t.getList("standing", Tag.TAG_COMPOUND)) {
                CompoundTag st = (CompoundTag) sr;
                v.standing.put(st.getUUID("id"), st.getInt("standing"));
                v.names.put(st.getUUID("id"), st.getString("name"));
            }
            for (Tag m : t.getList("memorials", Tag.TAG_LONG)) v.memorials.add(BlockPos.of(((net.minecraft.nbt.LongTag) m).getAsLong()));
            for (Tag id : t.getList("busts", Tag.TAG_INT_ARRAY)) v.busts.add(NbtUtils.loadUUID(id));
            for (Tag id : t.getList("told", Tag.TAG_INT_ARRAY)) v.told.add(NbtUtils.loadUUID(id));
            v.houses = t.getInt("houses");
            v.sheltered = t.getInt("sheltered");
            for (long d : t.getLongArray("kindness")) v.kindness.add(d);
            v.firstDay = t.contains("first_day") ? t.getLong("first_day") : -1;
            v.lastGrowth = t.contains("last_growth") ? t.getLong("last_growth") : -100;
            v.lastWear = t.contains("last_wear") ? t.getLong("last_wear") : -100;
            v.lastLeave = t.contains("last_leave") ? t.getLong("last_leave") : -100;
            if (t.contains("board")) v.board = Projects.Board.load(t.getCompound("board"));
            if (t.contains("project")) v.project = Projects.Project.load(t.getCompound("project"));
            for (Tag h : t.getList("homes", Tag.TAG_COMPOUND)) { var home = Projects.Home.load((CompoundTag) h); if (home != null) v.homes.add(home); }
            for (long r : t.getLongArray("replaced")) v.replaced.add(r);
            v.tier = t.contains("tier") ? t.getInt("tier") : -1;
            for (Tag id : t.getList("orders", Tag.TAG_INT_ARRAY)) v.orders.add(NbtUtils.loadUUID(id));
            for (Tag o : t.getList("owned", Tag.TAG_COMPOUND)) {
                CompoundTag ot = (CompoundTag) o;
                Homes.Owned home = Homes.Owned.load(ot);
                if (home != null && ot.hasUUID("owner")) v.owned.put(ot.getUUID("owner"), home);
            }
            for (Tag id : t.getList("waking", Tag.TAG_INT_ARRAY)) v.waking.add(NbtUtils.loadUUID(id));
            if (t.contains("market")) v.market = Markets.Market.load(t.getCompound("market"));
            v.marketHeld = t.contains("market_held") ? t.getLong("market_held") : -1;
            v.marketNews = t.contains("market_news") ? t.getLong("market_news") : -1;
            for (long u : t.getLongArray("unbuilt")) v.unbuilt.add(u);
            v.baselined = t.getBoolean("baselined");
            data.villages.put(v.bell.asLong(), v);
        }
        return data;
    }

    /** Where a remembered village's bell is, whether anything is remembered there (tests). */
    public static boolean remembers(ServerLevel level, BlockPos bell) { return get(level).villages.containsKey(bell.asLong()); }
}
