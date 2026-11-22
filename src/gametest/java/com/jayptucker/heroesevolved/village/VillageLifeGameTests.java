package com.jayptucker.heroesevolved.village;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Living in villages: what players put down is never built over, worn, torn down or tidied by a village; a home of your
 * own, given or bought; market days with stalls, traders and prices that follow the place.
 */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class VillageLifeGameTests {
    private static final int R = 44;

    private static final class Listener extends FakePlayer {
        final List<String> heard = new ArrayList<>();
        Listener(GameTestHelper h, String name) { super(h.getLevel(), new GameProfile(UUID.randomUUID(), name)); }
        @Override public void sendSystemMessage(Component c) { heard.add(c.getString()); }
    }

    private static BlockPos meadow(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos center = h.absolutePos(new BlockPos(6, 0, 6)).atY(300);
        // Ground reused from earlier tests may carry marks from blocks a test player put down there.
        PlayerBuilt.clear(level, center.offset(-R - 16, -2, -R - 16), center.offset(R + 16, 30, R + 16));
        for (int dx = -R; dx <= R; dx++) for (int dz = -R; dz <= R; dz++) level.setBlock(center.offset(dx, 0, dz), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
        BlockPos bell = center.above();
        level.setBlockAndUpdate(bell, Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, BellAttachType.FLOOR));
        return bell;
    }

    private static Listener player(GameTestHelper h, BlockPos at, String name) {
        Listener p = new Listener(h, name);
        p.setPos(Vec3.atBottomCenterOf(at));
        p.setGameMode(GameType.SURVIVAL);
        h.getLevel().addNewPlayer(p);
        return p;
    }

    private static void clear(GameTestHelper h, BlockPos bell, Entity... entities) {
        ServerLevel level = h.getLevel();
        var v = VillageMemory.remembers(level, bell) ? VillageMemory.of(level, bell) : null;
        if (v != null) Markets.close(level, v, false);
        for (Entity e : entities)
            if (e instanceof FakePlayer p) level.removePlayerImmediately(p, Entity.RemovalReason.DISCARDED);
            else if (e != null) e.discard();
        for (Villager x : level.getEntitiesOfClass(Villager.class, new AABB(bell).inflate(R + 4, 30, R + 4))) x.discard();
        for (WanderingTrader x : level.getEntitiesOfClass(WanderingTrader.class, new AABB(bell).inflate(R + 4, 30, R + 4))) x.discard();
        for (Rebuild.Site s : Rebuild.all(level)) if (s.bell().equals(bell)) Rebuild.drop(level, s);
        BlockPos center = bell.below();
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-R - 2, 0, -R - 2), center.offset(R + 2, 24, R + 2)))
            if (!level.getBlockState(p).isAir()) level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
        for (var item : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(bell).inflate(R + 4, 30, R + 4))) item.discard();
        VillageMemory.forget(level, bell);
    }

    /** Puts {@code state} down at {@code p} as {@code who} would: the placement event, then the block. */
    private static void place(ServerLevel level, FakePlayer who, BlockPos p, net.minecraft.world.level.block.state.BlockState state) {
        level.setBlock(p, state, 3);
        NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(BlockSnapshot.create(level.dimension(), level, p), level.getBlockState(p.below()), who));
    }

    // ------------------------------------------------------------------
    // What players put down
    // ------------------------------------------------------------------

    @GameTest(template = "rewind_empty", timeoutTicks = 60, batch = "life.marks")
    public static void whatAPlayerPutsDownIsTheirs(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos bell = meadow(h);
        Listener builder = player(h, bell.offset(2, 0, 2), "Builder");
        try {
            BlockPos p = bell.offset(5, 0, 5);
            place(level, builder, p, Blocks.OAK_PLANKS.defaultBlockState());
            h.assertTrue(PlayerBuilt.is(level, p), "Marked when placed");
            h.assertTrue(!PlayerBuilt.is(level, p.east()), "Only where they placed");
            // Broken, then something else put there, with nobody asking in between: the new block isn't theirs.
            level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(p, Blocks.STONE.defaultBlockState(), 2);
            h.assertTrue(!PlayerBuilt.is(level, p), "What comes after a broken block isn't theirs");
            h.succeed();
        } finally {
            clear(h, bell, builder);
        }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 100, batch = "life.build")
    public static void aVillageNeverBuildsOverOrWearsAPlayersBlocks(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos bell = meadow(h);
        Listener builder = player(h, bell.offset(2, 0, 2), "Builder");
        try {
            var v = VillageMemory.of(level, bell);
            // A footprint with a player's flower in it doesn't fit; without, it does.
            int x0 = bell.getX() + 22, z0 = bell.getZ() + 2;
            var size = new net.minecraft.core.Vec3i(5, 6, 5);
            h.assertTrue(Growth.fits(level, x0, z0, size) != null, "Open ground fits");
            place(level, builder, new BlockPos(x0 + 2, bell.getY(), z0 + 2), Blocks.POPPY.defaultBlockState());
            h.assertTrue(Growth.fits(level, x0, z0, size) == null, "Not over a flower a player planted");
            // A site planned, then a player puts a carpet where a wall will go: the wall goes round it.
            Rebuild.Site site = Growth.grow(level, v);
            h.assertTrue(site != null, "A house planned");
            BlockPos carpet = null;
            for (int i = 0; i < site.size() && carpet == null; i++) {
                BlockPos p = site.positions.get(i);
                if (!site.states.get(i).isAir() && level.getBlockState(p).isAir() && p.getY() == site.base + 1) carpet = p;
            }
            place(level, builder, carpet, Blocks.RED_CARPET.defaultBlockState());
            Rebuild.hurry(level, site, 100_000);
            h.assertTrue(level.getBlockState(carpet).is(Blocks.RED_CARPET), "The player's carpet is still there");
            // A home with a player's block in it is never rebuilt.
            Projects.Home home = site.home();
            h.assertTrue(!Projects.homes(level, v).contains(home), "A home a player has put something in isn't the village's to tear down");
            // Wear leaves a player's panes and cobblestone alone.
            List<BlockPos> theirs = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                BlockPos p = bell.offset(-6 + i, 0, -6);
                place(level, builder, p, (i % 2 == 0 ? Blocks.GLASS_PANE : Blocks.COBBLESTONE).defaultBlockState());
                theirs.add(p);
            }
            for (int i = 0; i < 30; i++) Growth.wear(level, bell);
            for (BlockPos p : theirs)
                h.assertTrue(level.getBlockState(p).is(Blocks.GLASS_PANE) || level.getBlockState(p).is(Blocks.COBBLESTONE), "Worn: " + p.toShortString());
            h.succeed();
        } finally {
            clear(h, bell, builder);
        }
    }

    // ------------------------------------------------------------------
    // A home of your own
    // ------------------------------------------------------------------

    @GameTest(template = "rewind_empty", timeoutTicks = 100, batch = "life.gift")
    public static void aVillageThatOwesYouBuildsYouAHome(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos bell = meadow(h);
        Listener hero = player(h, bell.offset(3, 0, 3), "Hero");
        try {
            var v = VillageMemory.of(level, bell);
            v.houses = 4;
            v.standing.put(hero.getUUID(), Homes.GIFT_STANDING);
            Homes.ask(level, v, hero);
            h.assertTrue(hero.heard.getLast().contains("as thanks") && v.orders().isEmpty(), "First, the offer: " + hero.heard);
            Homes.ask(level, v, hero);
            h.assertTrue(v.orders().contains(hero.getUUID()), "Then the order");
            h.assertTrue(hero.heard.getLast().contains("starting at dawn"), "An idle board starts on it at once: " + hero.heard.getLast());
            var p = v.project();
            h.assertTrue(p != null && hero.getUUID().equals(p.owner()) && p.stage() == Projects.Stage.BUILDING && p.need().isEmpty(),
                    "It goes up at once, with nothing to gather: " + (p == null ? "none" : p.title() + " " + p.stage()));
            Rebuild.Site site = Rebuild.all(level).stream().filter(s -> s.growth() && s.bell().equals(bell)).findFirst().orElseThrow();
            int before = level.getEntitiesOfClass(Villager.class, new AABB(bell).inflate(R, 20, R)).size();
            Rebuild.hurry(level, site, 100_000);
            Homes.Owned mine = v.owned().get(hero.getUUID());
            h.assertTrue(mine != null && v.orders().isEmpty(), "It's theirs");
            h.assertTrue(level.getBlockState(mine.bed()).getBlock() instanceof net.minecraft.world.level.block.BedBlock, "With a bed");
            h.assertTrue(mine.bed().equals(hero.getRespawnPosition()), "Where they wake: " + hero.getRespawnPosition());
            h.assertTrue(level.getPoiManager().getFreeTickets(mine.bed()) == 0, "No villager can claim its bed");
            h.assertTrue(PlayerBuilt.is(level, mine.bed()), "Every block of it counts as theirs");
            h.assertTrue(level.getEntitiesOfClass(Villager.class, new AABB(bell).inflate(R, 20, R)).size() == before, "Nobody else moves in");
            h.assertTrue(hero.heard.stream().anyMatch(s -> s.contains("Your home in " + v.name() + " is ready")), "And they're told: " + hero.heard);
            Homes.ask(level, v, hero);
            h.assertTrue(hero.heard.getLast().contains("Your home in"), "One home each");
            h.succeed();
        } finally {
            clear(h, bell, hero);
        }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 60, batch = "life.buy")
    public static void elsewhereAHomeIsBoughtForEmeraldsAndAHamletHasNone(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos bell = meadow(h);
        Listener buyer = player(h, bell.offset(3, 0, 3), "Buyer");
        try {
            var v = VillageMemory.of(level, bell);
            Homes.ask(level, v, buyer);
            h.assertTrue(buyer.heard.getLast().contains("only a hamlet"), "A hamlet has none to spare: " + buyer.heard);
            v.houses = 4;
            h.assertTrue(Homes.price(level, v, buyer) == Homes.VILLAGE_PRICE, "A village's costs " + Homes.VILLAGE_PRICE);
            buyer.getInventory().add(new ItemStack(Items.EMERALD, 10));
            Homes.ask(level, v, buyer);
            Homes.ask(level, v, buyer);
            h.assertTrue(buyer.heard.getLast().contains("You'll need") && v.orders().isEmpty(), "Not without the emeralds: " + buyer.heard);
            buyer.getInventory().add(new ItemStack(Items.EMERALD, 20));
            Homes.ask(level, v, buyer);
            Homes.ask(level, v, buyer);
            h.assertTrue(v.orders().contains(buyer.getUUID()) && buyer.getInventory().countItem(Items.EMERALD) == 30 - Homes.VILLAGE_PRICE,
                    "Bought: " + buyer.getInventory().countItem(Items.EMERALD) + " left");
            v.houses = 10;
            h.assertTrue(Homes.price(level, v, buyer) == Homes.TOWN_PRICE, "A town's costs " + Homes.TOWN_PRICE);
            h.succeed();
        } finally {
            clear(h, bell, buyer);
        }
    }

    // ------------------------------------------------------------------
    // Market days
    // ------------------------------------------------------------------

    @GameTest(template = "rewind_empty", timeoutTicks = 60, batch = "life.market")
    public static void aMarketSetsUpStallsAndTradersAndPacksUpCleanly(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos bell = meadow(h);
        Listener shopper = player(h, bell.offset(3, 0, 3), "Shopper");
        try {
            var v = VillageMemory.of(level, bell);
            int days = 0;
            for (long d = 1; d <= 28; d++) if (Markets.marketDay(v, d)) days++;
            h.assertTrue(days == 4, "One day in seven: " + days);
            v.houses = 4;
            h.assertTrue(Markets.open(level, v, Projects.tier(level, v)), "A village opens its market");
            var m = v.market();
            h.assertTrue(m.kinds().size() == 2 && m.kinds().contains(Markets.Kind.BUILDER), "Two stalls, one of them builder's: " + m.kinds());
            List<WanderingTrader> traders = Markets.traders(level, v);
            h.assertTrue(traders.size() == 2 && traders.stream().allMatch(t -> !t.getOffers().isEmpty() && t.getCustomName() != null), "Two named traders with goods");
            WanderingTrader builder = traders.get(m.kinds().indexOf(Markets.Kind.BUILDER));
            h.assertTrue(builder.getOffers().stream().anyMatch(o -> o.getResult().is(Items.OAK_LOG)), "The builder's merchant sells what boards ask for");
            BlockPos stall = m.stalls().getFirst();
            h.assertTrue(level.getBlockState(stall.above(2)).is(net.minecraft.tags.BlockTags.WOOL), "A canopy over the stall");
            // A friend of the village gets a little off.
            var offer = builder.getOffers().getFirst();
            v.standing.put(shopper.getUUID(), Markets.FRIEND);
            NeoForge.EVENT_BUS.post(new PlayerInteractEvent.EntityInteract(shopper, InteractionHand.MAIN_HAND, builder));
            h.assertTrue(offer.getCostA().getCount() < offer.getBaseCostA().getCount(), "Cheaper for a friend: " + offer.getCostA());
            Markets.close(level, v, false);
            h.assertTrue(v.market() == null && Markets.traders(level, v).isEmpty() && level.getEntitiesOfClass(WanderingTrader.class, new AABB(bell).inflate(20)).isEmpty(),
                    "The traders go");
            h.assertTrue(level.getBlockState(stall.above(2)).isAir() && level.getBlockState(stall.below()).is(Blocks.GRASS_BLOCK), "The stalls come down; the ground stays");
            h.succeed();
        } finally {
            clear(h, bell, shopper);
        }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 60, batch = "life.town")
    public static void aTownHoldsABiggerMarketAndPricesFollowItsMood(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos bell = meadow(h);
        try {
            var v = VillageMemory.of(level, bell);
            v.houses = 10;
            h.assertTrue(Markets.open(level, v, Projects.Tier.TOWN) && v.market().kinds().size() == 4, "Four stalls in a town");
            for (BlockPos stall : v.market().stalls())
                h.assertTrue(stall.getY() == bell.getY() && level.getBlockState(stall.below()).is(Blocks.GRASS_BLOCK), "Every stall on the ground: " + v.market().stalls());
            h.assertTrue(Markets.cost(10, Growth.Mood.THRIVING) < Markets.cost(10, Growth.Mood.STEADY)
                    && Markets.cost(10, Growth.Mood.STEADY) < Markets.cost(10, Growth.Mood.STRUGGLING), "Dear where it struggles, cheaper where it thrives");
            h.assertTrue(Markets.pays(10, Growth.Mood.THRIVING) > Markets.pays(10, Growth.Mood.STRUGGLING), "And it pays more for goods where it thrives");
            h.succeed();
        } finally {
            clear(h, bell);
        }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 60, batch = "life.stalls")
    public static void stallsNeverGoOnAPlayersBlocksAndOldTradersAreTurnedAway(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos bell = meadow(h);
        Listener builder = player(h, bell.offset(3, 0, 3), "Builder");
        try {
            int x = bell.getX() + 8, z = bell.getZ();
            var top = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
            h.assertTrue(Markets.fit(level, bell, x, z, Direction.WEST) != null, "Open ground takes a stall: bell " + bell.toShortString() + " top " + top.toShortString()
                    + " ground " + level.getBlockState(top.below()) + " sky " + level.canSeeSky(top.above(3)));
            place(level, builder, new BlockPos(x, bell.getY() - 1, z), Blocks.STONE_BRICKS.defaultBlockState());
            h.assertTrue(Markets.fit(level, bell, x, z, Direction.WEST) == null, "Not on a player's paving");
            WanderingTrader old = EntityType.WANDERING_TRADER.create(level);
            old.moveTo(Vec3.atBottomCenterOf(bell.offset(4, 0, 4)));
            old.addTag(Markets.TAG);
            boolean added = level.addFreshEntity(old);
            h.assertTrue(!added || old.isRemoved(), "A trader from a market that's over isn't let back in");
            h.succeed();
        } finally {
            clear(h, bell, builder);
        }
    }
}
