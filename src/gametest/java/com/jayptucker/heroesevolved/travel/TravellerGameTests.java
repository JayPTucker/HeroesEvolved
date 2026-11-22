package com.jayptucker.heroesevolved.travel;

import com.jayptucker.heroesevolved.company.CompanyAttention;
import com.jayptucker.heroesevolved.company.CompanyOps;
import com.jayptucker.heroesevolved.company.Pursuit;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.gifted.GiftedMobs;
import com.jayptucker.heroesevolved.registry.ModItems;
import com.jayptucker.heroesevolved.village.VillageMemory;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Travellers on the road: each kind sets out, walks, arrives and does its business; refugees, pursuit, unseen parties. */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class TravellerGameTests {
    private static BlockPos at(GameTestHelper h, int x, int z) { return h.absolutePos(new BlockPos(x, 2, z)); }

    private static BlockPos bell(GameTestHelper h) {
        BlockPos bell = at(h, 10, 10);
        h.getLevel().setBlockAndUpdate(bell, Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, BellAttachType.FLOOR));
        return bell;
    }

    private static ServerPlayer player(GameTestHelper h, Vec3 at, String name) {
        ServerPlayer p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), name));
        p.setPos(at);
        p.setGameMode(GameType.SURVIVAL);
        p.setData(ModDataAttachments.PLAYER_ABILITIES, com.jayptucker.heroesevolved.ability.data.PlayerAbilityData.empty());
        h.getLevel().addNewPlayer(p);
        return p;
    }

    private static void cleanup(GameTestHelper h, BlockPos bell, Entity... entities) {
        Travellers.clearAll();
        CompanyOps.callOffAll();
        for (Entity e : entities) {
            if (e instanceof ServerPlayer p) { CompanyAttention.forget(p); p.serverLevel().removePlayerImmediately(p, Entity.RemovalReason.DISCARDED); }
            else if (e != null) e.discard();
        }
        if (bell != null) VillageMemory.forget(h.getLevel(), bell);
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "travel.kinds")
    public static void everyKindSetsOutTaggedForTheRoad(GameTestHelper h) {
        BlockPos bell = bell(h);
        try {
            for (Party.Kind kind : Party.Kind.values()) {
                Party p = Travellers.start(h.getLevel(), kind, Vec3.atBottomCenterOf(at(h, 1, 1)), bell);
                h.assertTrue(p != null && p.kind() == kind && !p.members().isEmpty(), kind + " sets out");
                h.assertTrue(p.members().stream().allMatch(m -> m.getTags().contains(Travellers.TAG) && m.isAlive()), kind + "'s people are travellers");
            }
            h.succeed();
        } finally { cleanup(h, bell); }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "travel.pedlar")
    public static void thePedlarTradesWhatVillagersDont(GameTestHelper h) {
        BlockPos bell = bell(h);
        try {
            Pedlar p = (Pedlar) Travellers.start(h.getLevel(), Party.Kind.PEDLAR, Vec3.atBottomCenterOf(at(h, 1, 1)), bell);
            var offers = p.trader().getOffers();
            h.assertTrue(offers.stream().anyMatch(o -> o.getResult().is(ModItems.HELIXITE_SHARD.get())), "Helixite for sale");
            h.assertTrue(offers.stream().anyMatch(o -> o.getCostA().is(ModItems.HELIXITE_METEORITE.get().asItem()) && o.getResult().is(Items.EMERALD)),
                    "And meteorite bought");
            h.assertTrue(p.trader().getCustomName() != null && p.trader().getCustomName().getString().endsWith("the Pedlar"), "Named for themselves");
            h.succeed();
        } finally { cleanup(h, bell); }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 200, batch = "travel.healer")
    public static void theHealerMendsWhoeverIsHurt(GameTestHelper h) {
        BlockPos bell = bell(h);
        Villager hurt = EntityType.VILLAGER.create(h.getLevel());
        hurt.moveTo(Vec3.atBottomCenterOf(at(h, 3, 2)));
        hurt.getPersistentData().putBoolean(GiftedMobs.ROLLED, true);
        hurt.setNoAi(true);
        h.getLevel().addFreshEntity(hurt);
        hurt.setHealth(4);
        Healer p = (Healer) Travellers.start(h.getLevel(), Party.Kind.HEALER, Vec3.atBottomCenterOf(at(h, 2, 2)), bell);
        h.assertTrue(GiftedMobs.isGood(p.healer()), "A healer with the gift");
        h.succeedWhen(() -> {
            h.assertTrue(hurt.getHealth() >= 4 + Healer.MEND, "Not mended yet: " + hurt.getHealth());
            cleanup(h, bell, hurt);
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 60, batch = "travel.caravan")
    public static void aDeliveredCaravanPaysWhoeverBroughtItIn(GameTestHelper h) {
        BlockPos bell = bell(h);
        ServerPlayer p = player(h, Vec3.atBottomCenterOf(at(h, 9, 9)), "Wren");
        Caravan c = (Caravan) Travellers.start(h.getLevel(), Party.Kind.CARAVAN, Vec3.atBottomCenterOf(at(h, 1, 1)), bell);
        h.assertTrue(c.llamas().stream().allMatch(l -> l.isLeashed()), "Its llamas on leads");
        c.hurry(1000);
        h.runAfterDelay(3, () -> {
            h.assertTrue(c.delivered(), "It arrived (" + c.stage() + ")");
            h.assertTrue(p.getInventory().countItem(Items.EMERALD) >= 6, "Paid: " + p.getInventory().countItem(Items.EMERALD));
            h.assertTrue(VillageMemory.of(h.getLevel(), bell).standing(p.getUUID()) >= 1, "And the village thinks well of them");
            h.succeed();
            cleanup(h, bell, p);
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 60, batch = "travel.ambush")
    public static void aCaravanThatLosesItsDroversLeavesItsGoodsOnTheRoad(GameTestHelper h) {
        BlockPos bell = bell(h);
        Caravan c = (Caravan) Travellers.start(h.getLevel(), Party.Kind.CARAVAN, Vec3.atBottomCenterOf(at(h, 1, 1)), bell);
        c.ambush();
        h.assertTrue(c.ambushers().size() == 3 && c.ambushers().stream().anyMatch(GiftedMobs::isEvil), "A gifted renegade and two more");
        for (Villager v : c.drovers()) v.kill();
        h.runAfterDelay(3, () -> {
            h.assertTrue(c.stage() == Party.Stage.OVER && !c.delivered(), "Lost");
            boolean goods = !h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at(h, 1, 1)).inflate(8), i -> i.getItem().is(Items.BREAD)).isEmpty();
            h.assertTrue(goods, "Its goods lie on the road");
            h.succeed();
            for (var m : c.ambushers()) m.discard();
            cleanup(h, bell);
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 60, batch = "travel.refugees")
    public static void refugeesWhoMakeItSettleAndTheirGiftedOneGuards(GameTestHelper h) {
        BlockPos bell = bell(h);
        ServerPlayer p = player(h, Vec3.atBottomCenterOf(at(h, 8, 8)), "Wren");
        Refugees r = (Refugees) Travellers.start(h.getLevel(), Party.Kind.REFUGEES, Vec3.atBottomCenterOf(at(h, 1, 1)), bell);
        h.assertTrue(r.family().size() >= 2, "A family");
        r.hurry(1000);
        h.runAfterDelay(3, () -> {
            h.assertTrue(r.settled(), "They made it");
            h.assertTrue(r.family().stream().noneMatch(v -> v.getTags().contains(Travellers.TAG)) && r.family().stream().allMatch(Entity::isAlive),
                    "They belong to the village now");
            h.assertTrue(GiftedMobs.isGood(r.family().getFirst()) && bell.equals(GiftedMobs.of(r.family().getFirst()).gift().home()),
                    "The one with a gift guards it");
            h.assertTrue(VillageMemory.of(h.getLevel(), bell).sheltered() == 1, "It took them in");
            h.succeed();
            for (Villager v : r.family()) v.discard();
            cleanup(h, bell, p);
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 60, batch = "travel.pursuit")
    public static void anAgentWhoReachesThemTakesOneAndRemembersWhoWasThere(GameTestHelper h) {
        BlockPos bell = bell(h);
        ServerPlayer p = player(h, Vec3.atBottomCenterOf(at(h, 3, 3)), "Wren");
        Refugees r = (Refugees) Travellers.start(h.getLevel(), Party.Kind.REFUGEES, Vec3.atBottomCenterOf(at(h, 2, 2)), bell);
        Pursuit hunt = Pursuit.start(h.getLevel(), p, r.family, Vec3.atBottomCenterOf(at(h, 8, 2)));
        h.assertTrue(hunt != null && hunt.agents().size() == Pursuit.AGENTS, "Two agents come up behind them");
        Villager first = r.family().getFirst();
        h.runAfterDelay(2, () -> hunt.agents().getFirst().teleportTo(first.getX() + 0.5, first.getY(), first.getZ()));
        h.runAfterDelay(4, () -> {
            h.assertTrue(hunt.taken() >= 1 && first.isRemoved(), "One taken");
            h.assertTrue(Math.abs(CompanyAttention.of(p) - Pursuit.CAUGHT) < 0.01, "Caught with them: " + CompanyAttention.of(p));
            h.assertTrue(CompanyOps.stops(hunt.agents().get(1), p), "And they'll go for whoever stands by the rest");
            h.succeed();
            cleanup(h, bell, p);
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "travel.unseen")
    public static void aPartyNobodyIsNearMovesOnAndLeftoversAreTurnedAway(GameTestHelper h) {
        BlockPos bell = bell(h);
        Party party = Travellers.start(h.getLevel(), Party.Kind.HEALER, Vec3.atBottomCenterOf(at(h, 1, 1)), bell);
        party.unseenFor = Party.UNSEEN;
        h.runAfterDelay(2, () -> {
            h.assertTrue(party.stage() == Party.Stage.OVER && party.members().stream().allMatch(Entity::isRemoved), "Gone on its way");
            Villager leftover = EntityType.VILLAGER.create(h.getLevel());
            leftover.moveTo(Vec3.atBottomCenterOf(at(h, 4, 4)));
            leftover.addTag(Travellers.TAG);
            h.assertTrue(!h.getLevel().addFreshEntity(leftover), "A traveller of no live party is turned away when it loads");
            h.succeed();
            cleanup(h, bell);
        });
    }
}
