package com.jayptucker.heroesevolved.village;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.radio.BandChatter;
import com.jayptucker.heroesevolved.radio.EmergencyBand;
import com.jayptucker.heroesevolved.registry.ModItems;
import com.jayptucker.heroesevolved.weapons.PowerWeapons;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Containers;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Market days. One day in {@link #EVERY}, from morning to evening, a village (two stalls) or a town (four) holds a market
 * by its bell while a player is about: stalls of fence posts, a barrel counter and a striped canopy, each with a trader
 * selling what villagers don't: a pedlar, a grocer, a builder's merchant (what notice boards ask for), a curio dealer.
 * Prices follow how the place is doing, and a friend of it gets a little off. Stalls go only on open ground, never on or
 * over anything a player put down, and come down when the market packs up. What the player sees:
 * {@code docs/village-growth.md}.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class Markets {
    /** A market every this many days, from this time of day to that. */
    public static final int EVERY = 7, FROM = 1_000, TO = 11_000;
    /** A market runs while a player is this close; unseen this long, it packs up early. */
    public static final int NEAR = 96, UNSEEN = 2_400;
    /** Standing at which a trader knocks some off. */
    public static final int FRIEND = 3;
    public static final String TAG = "heroesevolved_market";
    private static final Set<UUID> LIVE = new HashSet<>();

    private Markets() {}

    public enum Kind {
        PEDLAR("the Pedlar", Blocks.RED_WOOL), GROCER("the Grocer", Blocks.LIME_WOOL),
        BUILDER("the Builder's Merchant", Blocks.ORANGE_WOOL), CURIO("the Curio Dealer", Blocks.PURPLE_WOOL);
        final String title;
        final Block colour;
        Kind(String title, Block colour) { this.title = title; this.colour = colour; }
    }

    private static final String[] NAMES = {"Odile", "Bram", "Hester", "Lucan", "Mirela", "Tobin", "Agathe", "Rufus", "Inga", "Corin", "Dagny", "Silas"};

    /** A market on: its day, its stalls (where they stand, facing the bell), the blocks they put down, their traders. */
    public static final class Market {
        long day, seen;
        final List<BlockPos> stalls = new ArrayList<>();
        final List<Direction> facings = new ArrayList<>();
        final List<Kind> kinds = new ArrayList<>();
        final List<BlockPos> placed = new ArrayList<>();
        final List<UUID> traders = new ArrayList<>();
        final Set<UUID> told = new HashSet<>(), friends = new HashSet<>();

        public long day() { return day; }
        public List<Kind> kinds() { return List.copyOf(kinds); }
        public List<BlockPos> stalls() { return List.copyOf(stalls); }
        public List<UUID> traders() { return List.copyOf(traders); }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putLong("day", day);
            t.putLongArray("placed", placed.stream().mapToLong(BlockPos::asLong).toArray());
            ListTag tr = new ListTag();
            for (UUID u : traders) tr.add(NbtUtils.createUUID(u));
            t.put("traders", tr);
            return t;
        }

        /** A saved market is only ever one to take down (its traders were turned away on load). */
        static Market load(CompoundTag t) {
            Market m = new Market();
            m.day = t.getLong("day");
            for (long p : t.getLongArray("placed")) m.placed.add(BlockPos.of(p));
            for (Tag u : t.getList("traders", Tag.TAG_INT_ARRAY)) m.traders.add(NbtUtils.loadUUID(u));
            return m;
        }
    }

    /** Whether {@code day} is market day at {@code v} (each place keeps its own day of the week). */
    public static boolean marketDay(VillageMemory.Village v, long day) {
        return Math.floorMod(day + (v.bell.asLong() * 31 >>> 7), EVERY) == 0;
    }

    /** Each pass for a loaded, untroubled village (from {@link Growth}): the news the evening before, opening, closing. */
    public static void consider(ServerLevel level, VillageMemory.Village v) {
        long day = Days.today(level), time = level.getDayTime() % 24_000, now = level.getGameTime();
        Projects.Tier tier = Projects.tier(level, v);
        Market m = v.market;
        if (m != null && (m.day != day || time < FROM || time > TO || tier == Projects.Tier.HAMLET)) { close(level, v, false); m = null; }
        // A market saved over a restart lost its traders (turned away on load): take it down and set it up again.
        if (m != null && !m.traders.isEmpty() && m.traders.stream().noneMatch(LIVE::contains)) { close(level, v, false); v.marketHeld = -1; m = null; }
        if (tier == Projects.Tier.HAMLET) return;
        if (time >= 12_000 && marketDay(v, day + 1) && v.marketNews != day) {
            v.marketNews = day;
            BandChatter.news(level, v.bell, "Market day in " + v.name + " tomorrow. Bring your emeralds.", (int) Days.DAY);
        }
        boolean someone = near(level, v.bell);
        if (m == null && marketDay(v, day) && time >= FROM && time <= TO && v.marketHeld != day && someone) open(level, v, tier);
        m = v.market;
        if (m == null) return;
        if (someone) m.seen = now;
        else if (now - m.seen > UNSEEN) { close(level, v, false); return; }
        for (ServerPlayer p : level.players())
            if (!p.isSpectator() && p.blockPosition().distSqr(v.bell) <= 48 * 48 && m.told.add(p.getUUID()))
                p.sendSystemMessage(Component.literal("It's market day in " + v.name + ": " + words(m.stalls.size()) + " stalls are up by the bell.")
                        .withStyle(ChatFormatting.GRAY));
    }

    private static boolean near(ServerLevel level, BlockPos bell) {
        for (ServerPlayer p : level.players()) if (!p.isSpectator() && p.blockPosition().distSqr(bell) <= (long) NEAR * NEAR) return true;
        return false;
    }

    private static String words(int n) { return n == 2 ? "two" : n == 3 ? "three" : n == 4 ? "four" : String.valueOf(n); }

    /** Opens {@code v}'s market now: as many stalls as its tier has room for. False if there was room for none. */
    public static boolean open(ServerLevel level, VillageMemory.Village v, Projects.Tier tier) {
        if (v.market != null || !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) return false;
        List<Kind> kinds = new ArrayList<>(List.of(Kind.BUILDER, Kind.GROCER, Kind.CURIO, Kind.PEDLAR));
        java.util.Collections.shuffle(kinds.subList(1, kinds.size()), new java.util.Random(level.random.nextLong()));
        int want = tier == Projects.Tier.TOWN ? 4 : 2;
        Market m = new Market();
        m.day = Days.today(level);
        m.seen = level.getGameTime();
        String kind = Growth.kind(level, v.bell);
        Growth.Mood mood = Growth.mood(level, v);
        for (Kind k : kinds.subList(0, want)) {
            BlockPos at = null;
            Direction facing = null;
            search:
            for (int r = 4; r <= 20; r++)
                for (int i = 0; i < 24; i++) {
                    double a = (i + r * 0.6) * Math.PI * 2 / 24;
                    int x = v.bell.getX() + (int) Math.round(Math.cos(a) * r), z = v.bell.getZ() + (int) Math.round(Math.sin(a) * r);
                    Direction f = Direction.getNearest((float) (v.bell.getX() - x), 0F, (float) (v.bell.getZ() - z));
                    BlockPos c = fit(level, v.bell, x, z, f);
                    if (c == null) continue;
                    // Stalls stand apart, on the ground, never on (or against) another stall.
                    boolean apart = true;
                    for (BlockPos o : m.stalls) if (Math.abs(o.getX() - c.getX()) + Math.abs(o.getZ() - c.getZ()) < 5) apart = false;
                    if (apart) { at = c; facing = f; break search; }
                }
            if (at == null) break;
            stall(level, m, at, facing, k, kind);
            WanderingTrader t = EntityType.WANDERING_TRADER.create(level);
            if (t == null) continue;
            String name = NAMES[level.random.nextInt(NAMES.length)] + " " + k.title;
            t.moveTo(at.getX() + .5, at.getY(), at.getZ() + .5, facing.toYRot(), 0);
            t.setYHeadRot(facing.toYRot());
            t.setYBodyRot(facing.toYRot());
            t.setCustomName(Component.literal(name));
            t.setNoAi(true);
            t.setPersistenceRequired();
            t.setDespawnDelay(24_000);
            t.addTag(TAG);
            stock(level, t, k, kind, mood);
            LIVE.add(t.getUUID());
            level.addFreshEntity(t);
            m.stalls.add(at);
            m.facings.add(facing);
            m.kinds.add(k);
            m.traders.add(t.getUUID());
        }
        if (m.stalls.isEmpty()) {
            for (BlockPos p : m.placed) remove(level, p);
            return false;
        }
        v.market = m;
        v.marketHeld = m.day;
        VillageMemory.get(level).setDirty();
        BandChatter.news(level, v.bell, "It's market day in " + v.name + ". " + (tier == Projects.Tier.TOWN ? "The whole square's full of stalls." : "A couple of stalls by the bell."), (int) Days.DAY);
        return true;
    }

    /**
     * Where a stall fits at {@code x, z}, facing {@code facing} (toward the bell): level ground three wide and two deep,
     * open to the sky, room to stand in front, and nothing a player put down. Its middle back cell, or null.
     */
    static BlockPos fit(ServerLevel level, BlockPos bell, int x, int z, Direction facing) {
        BlockPos column = new BlockPos(x, 0, z);
        if (!level.hasChunkAt(column)) return null;
        int y = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column).getY();
        if (Math.abs(y - bell.getY()) > 3) return null;
        BlockPos o = new BlockPos(x, y, z);
        Direction right = facing.getCounterClockWise();
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = 0; dz <= 2; dz++) {
                BlockPos c = o.relative(right, dx).relative(facing, dz);
                if (!level.hasChunkAt(c)) return null;
                if (dz <= 1) {
                    BlockPos g = c.below();
                    BlockState gs = level.getBlockState(g);
                    if (!(gs.isFaceSturdy(level, g, Direction.UP) || gs.is(Blocks.DIRT_PATH)) || !level.getFluidState(g).isEmpty()
                            || gs.is(Blocks.FARMLAND) || stallBlock(gs) || PlayerBuilt.is(level, g)) return null;
                    if (level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c).getY() != y) return null;
                }
                for (int dy = 0; dy <= (dz == 2 ? 1 : 2); dy++) {
                    BlockPos q = c.above(dy);
                    BlockState s = level.getBlockState(q);
                    if (!s.isAir() && !(s.canBeReplaced() && s.getFluidState().isEmpty()) || PlayerBuilt.is(level, q)) return null;
                }
            }
        return o;
    }

    /** Puts up a stall at {@code o}: posts at its corners, a barrel counter with a lantern, a striped canopy. */
    static void stall(ServerLevel level, Market m, BlockPos o, Direction facing, Kind k, String kind) {
        Direction right = facing.getCounterClockWise();
        Block fence = switch (kind) {
            case "taiga", "snowy" -> Blocks.SPRUCE_FENCE;
            case "savanna" -> Blocks.ACACIA_FENCE;
            case "desert" -> Blocks.BIRCH_FENCE;
            default -> Blocks.OAK_FENCE;
        };
        for (int dx : new int[]{-1, 1})
            for (int dz = 0; dz <= 1; dz++)
                for (int dy = 0; dy <= 1; dy++) put(level, m, o.relative(right, dx).relative(facing, dz).above(dy), fence.defaultBlockState());
        BlockPos counter = o.relative(facing);
        put(level, m, counter, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = 0; dz <= 1; dz++)
                put(level, m, o.relative(right, dx).relative(facing, dz).above(2), (dx == 0 ? Blocks.WHITE_WOOL : k.colour).defaultBlockState());
        put(level, m, counter.above(), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
    }

    private static void put(ServerLevel level, Market m, BlockPos p, BlockState state) {
        BlockState now = level.getBlockState(p);
        if (!now.isAir() && !now.canBeReplaced() || PlayerBuilt.is(level, p)) return;
        level.setBlock(p, state, Block.UPDATE_ALL);
        m.placed.add(p.immutable());
    }

    /** Whether {@code s} is a block a stall puts down (so taking a stall down takes nothing else). */
    static boolean stallBlock(BlockState s) {
        return s.is(BlockTags.WOODEN_FENCES) || s.is(Blocks.BARREL) || s.is(BlockTags.WOOL) || s.is(Blocks.LANTERN);
    }

    private static void remove(ServerLevel level, BlockPos p) {
        if (!level.hasChunkAt(p) || PlayerBuilt.is(level, p) || !stallBlock(level.getBlockState(p))) return;
        if (level.getBlockEntity(p) instanceof Container box) Containers.dropContents(level, p, box);
        level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    /** Packs {@code v}'s market up: the stalls come down (top first) and the traders go. {@code hurry}: trouble came. */
    public static void close(ServerLevel level, VillageMemory.Village v, boolean hurry) {
        Market m = v.market;
        if (m == null) return;
        v.market = null;
        VillageMemory.get(level).setDirty();
        List<BlockPos> placed = new ArrayList<>(m.placed);
        placed.sort(java.util.Comparator.<BlockPos>comparingInt(BlockPos::getY).reversed());
        for (BlockPos p : placed) remove(level, p);
        for (UUID id : m.traders) {
            LIVE.remove(id);
            Entity e = level.getEntity(id);
            if (e != null) e.discard();
        }
        Component line = Component.literal(hurry ? "The traders in " + v.name + " pack up in a hurry." : "The market in " + v.name + " packs up for the day.")
                .withStyle(ChatFormatting.GRAY);
        for (ServerPlayer p : level.players()) if (m.told.contains(p.getUUID()) && p.blockPosition().distSqr(v.bell) <= 64 * 64) p.sendSystemMessage(line);
    }

    // ------------------------------------------------------------------
    // What they sell
    // ------------------------------------------------------------------

    /** What things cost at a place doing this well: dear where it struggles, cheaper where it thrives. */
    static int cost(int base, Growth.Mood mood) {
        float f = switch (mood) { case THRIVING -> 0.85F; case STEADY -> 1F; case STRUGGLING -> 1.3F; };
        return Math.max(1, Math.round(base * f));
    }

    /** What a trader pays: more where the place thrives, less where it struggles. */
    static int pays(int base, Growth.Mood mood) {
        float f = switch (mood) { case THRIVING -> 1.2F; case STEADY -> 1F; case STRUGGLING -> 0.75F; };
        return Math.max(1, Math.round(base * f));
    }

    private static MerchantOffer sell(int emeralds, ItemStack what, Growth.Mood mood) {
        return new MerchantOffer(new ItemCost(Items.EMERALD, cost(emeralds, mood)), what, 8, 1, 0.05F);
    }

    private static MerchantOffer buy(net.minecraft.world.item.Item item, int count, int emeralds, Growth.Mood mood) {
        return new MerchantOffer(new ItemCost(item, count), new ItemStack(Items.EMERALD, pays(emeralds, mood)), 12, 1, 0.05F);
    }

    static void stock(ServerLevel level, WanderingTrader t, Kind k, String kind, Growth.Mood mood) {
        MerchantOffers offers = t.getOffers();
        offers.clear();
        switch (k) {
            case PEDLAR -> {
                offers.add(sell(8, new ItemStack(ModItems.HELIXITE_SHARD.get()), mood));
                ItemStack radio = EmergencyBand.radio();
                if (!radio.isEmpty()) offers.add(sell(6, radio, mood));
                ItemStack tasers = PowerWeapons.ammo(PowerWeapons.TASER_CARTRIDGE, 6);
                if (!tasers.isEmpty()) offers.add(sell(10, tasers, mood));
                ItemStack rounds = PowerWeapons.ammo(PowerWeapons.DAMPENER_ROUND, 4);
                if (!rounds.isEmpty()) offers.add(sell(12, rounds, mood));
                offers.add(new MerchantOffer(new ItemCost(ModItems.HELIXITE_METEORITE.get(), 1), new ItemStack(Items.EMERALD, pays(10, mood)), 4, 3, 0.05F));
            }
            case GROCER -> {
                offers.add(sell(2, new ItemStack(Items.BREAD, 6), mood));
                offers.add(sell(3, new ItemStack(Items.COOKED_BEEF, 4), mood));
                offers.add(sell(4, new ItemStack(Items.GOLDEN_CARROT, 3), mood));
                offers.add(sell(2, new ItemStack(Items.PUMPKIN_PIE, 2), mood));
                offers.add(sell(3, new ItemStack(Items.CAKE), mood));
                offers.add(sell(3, new ItemStack(Items.HONEY_BOTTLE, 2), mood));
                offers.add(buy(Items.WHEAT, 20, 1, mood));
                offers.add(buy(Items.CARROT, 22, 1, mood));
                offers.add(buy(Items.POTATO, 26, 1, mood));
            }
            case BUILDER -> {
                net.minecraft.world.item.Item log = switch (kind) {
                    case "taiga", "snowy" -> Items.SPRUCE_LOG;
                    case "savanna" -> Items.ACACIA_LOG;
                    case "desert" -> Items.BIRCH_LOG;
                    default -> Items.OAK_LOG;
                };
                offers.add(sell(3, new ItemStack(log, 8), mood));
                offers.add(kind.equals("desert") ? sell(2, new ItemStack(Items.SANDSTONE, 16), mood) : sell(2, new ItemStack(Items.COBBLESTONE, 32), mood));
                offers.add(sell(3, new ItemStack(Items.GLASS_PANE, 16), mood));
                offers.add(sell(3, new ItemStack(Items.TERRACOTTA, 16), mood));
                offers.add(sell(3, new ItemStack(Items.WHITE_BED), mood));
                offers.add(sell(1, new ItemStack(Items.TORCH, 16), mood));
                offers.add(sell(4, new ItemStack(Items.IRON_INGOT, 3), mood));
            }
            case CURIO -> {
                for (int i = 0; i < 2; i++)
                    offers.add(sell(14, EnchantmentHelper.enchantItem(level.random, new ItemStack(Items.BOOK), 20, level.registryAccess(), Optional.<net.minecraft.core.HolderSet<net.minecraft.world.item.enchantment.Enchantment>>empty()), mood));
                offers.add(sell(10, new ItemStack(Items.NAME_TAG), mood));
                offers.add(sell(6, new ItemStack(Items.SADDLE), mood));
                offers.add(sell(8, new ItemStack(Items.ENDER_PEARL, 2), mood));
                offers.add(sell(9, new ItemStack(Items.GOLDEN_APPLE), mood));
                offers.add(sell(5, new ItemStack(Items.SPYGLASS), mood));
            }
        }
    }

    /** A trader's prices for {@code p}: a friend of the place (standing {@link #FRIEND}) gets 15% off. */
    @SubscribeEvent
    public static void trading(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !(event.getTarget() instanceof WanderingTrader t) || !t.getTags().contains(TAG)) return;
        VillageMemory.Village v = null;
        for (VillageMemory.Village w : VillageMemory.all(p.serverLevel())) if (w.market != null && w.market.traders.contains(t.getUUID())) v = w;
        if (v == null) return;
        boolean friend = v.standing(p.getUUID()) >= FRIEND;
        for (MerchantOffer o : t.getOffers())
            o.setSpecialPriceDiff(friend && o.getCostA().is(Items.EMERALD) ? -Math.max(1, Math.round(o.getBaseCostA().getCount() * 0.15F)) : 0);
        if (friend && v.market.friends.add(p.getUUID()))
            p.sendSystemMessage(Component.literal(t.getName().getString() + " knocks a bit off for a friend of " + v.name + ".").withStyle(ChatFormatting.GRAY));
    }

    /** A market trader saved with its chunk belongs to a market that's over: it isn't let back in. */
    @SubscribeEvent
    public static void joined(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !event.getEntity().getTags().contains(TAG) || LIVE.contains(event.getEntity().getUUID())) return;
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void stop(ServerStoppingEvent event) { LIVE.clear(); }

    /** Everyone selling at {@code v}'s market (loaded ones). */
    public static List<WanderingTrader> traders(ServerLevel level, VillageMemory.Village v) {
        List<WanderingTrader> out = new ArrayList<>();
        if (v.market == null) return out;
        for (UUID id : v.market.traders) if (level.getEntity(id) instanceof WanderingTrader t) out.add(t);
        return out;
    }
}
