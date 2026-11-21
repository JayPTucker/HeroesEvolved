package com.jayptucker.heroesevolved.village;

import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.company.Convoy;
import com.jayptucker.heroesevolved.events.PlayerGroups;
import com.jayptucker.heroesevolved.gifted.Gift;
import com.jayptucker.heroesevolved.gifted.GiftedMob;
import com.jayptucker.heroesevolved.gifted.GiftedMobs;
import com.jayptucker.heroesevolved.gifted.Rarity;
import com.jayptucker.heroesevolved.meteor.Starfall;
import com.jayptucker.heroesevolved.radio.EmergencyBand;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * World events in multiplayer and singleplayer: players together share rolls, a village in trouble takes nothing more,
 * hostile gifts grow with the party, and nobody sleeps through a starfall.
 */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class WorldEventBalanceGameTests {
    private static BlockPos at(GameTestHelper h, int x, int z) { return h.absolutePos(new BlockPos(x, 2, z)); }

    private static BlockPos bell(GameTestHelper h) {
        BlockPos bell = at(h, 6, 6);
        h.getLevel().setBlockAndUpdate(bell, Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, BellAttachType.FLOOR));
        return bell;
    }

    private static ServerPlayer player(GameTestHelper h, Vec3 at, String name) {
        ServerPlayer p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), name));
        p.setPos(at);
        p.setGameMode(GameType.SURVIVAL);
        h.getLevel().addNewPlayer(p);
        return p;
    }

    private static void remove(Entity... entities) {
        for (Entity e : entities) {
            if (e instanceof ServerPlayer p) p.serverLevel().removePlayerImmediately(p, Entity.RemovalReason.DISCARDED);
            else if (e != null) e.discard();
        }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "balance.groups")
    public static void playersTogetherAreOneGroupAndStrangersTheirOwn(GameTestHelper h) {
        ServerPlayer a = player(h, Vec3.atBottomCenterOf(at(h, 2, 2)), "A"), b = player(h, Vec3.atBottomCenterOf(at(h, 2, 200)), "B"),
                c = player(h, Vec3.atBottomCenterOf(at(h, 2, 400)), "C"), d = player(h, Vec3.atBottomCenterOf(at(h, 2, 2000)), "D");
        try {
            var groups = PlayerGroups.of(List.of(a, b, c, d), 256);
            h.assertTrue(groups.size() == 2, "A, B and C are linked (each within reach of the next); D is alone: " + groups.size());
            h.assertTrue(groups.stream().anyMatch(g -> g.containsAll(List.of(a, b, c))) && groups.stream().anyMatch(g -> g.equals(List.of(d))),
                    "The right groups");
            h.succeed();
        } finally { remove(a, b, c, d); }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "balance.troubled")
    public static void aVillageAlreadyInTroubleGetsNothingMorePiledOnIt(GameTestHelper h) {
        BlockPos bell = bell(h);
        Villager v = EntityType.VILLAGER.create(h.getLevel());
        v.moveTo(Vec3.atBottomCenterOf(at(h, 4, 6)));
        v.getPersistentData().putBoolean(GiftedMobs.ROLLED, true);
        h.getLevel().addFreshEntity(v);
        ServerPlayer p = player(h, Vec3.atBottomCenterOf(at(h, 6, -40)), "Wren");
        var call = EmergencyBand.call(h.getLevel(), bell, Component.literal("test"), 200);
        try {
            h.assertTrue(EmergencyBand.troubled(h.getLevel(), bell), "The village is calling for help");
            h.assertTrue(!EmergencyBand.troubled(h.getLevel(), bell.offset(200, 0, 0)), "Another village isn't");
            h.assertTrue(Convoy.start(h.getLevel(), p, bell, new Vec3(1, 0, 0)) == null, "So the Company doesn't come for anyone there now");
            h.succeed();
        } finally {
            EmergencyBand.end(call);
            remove(v, p);
            VillageMemory.forget(h.getLevel(), bell);
        }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 200, batch = "balance.party")
    public static void aHostileGiftGrowsWithThePlayersFightingIt(GameTestHelper h) {
        Pillager mob = EntityType.PILLAGER.create(h.getLevel());
        mob.moveTo(Vec3.atBottomCenterOf(at(h, 6, 6)));
        mob.getPersistentData().putBoolean(GiftedMobs.ROLLED, true);
        mob.setNoAi(true);
        h.getLevel().addFreshEntity(mob);
        GiftedMob g = GiftedMobs.bestow(mob, ModAbilities.PYROKINESIS_ID, Rarity.UNCOMMON, true, Gift.Origin.WILD, null, "Test the Tall");
        float alone = mob.getMaxHealth();
        ServerPlayer a = player(h, Vec3.atBottomCenterOf(at(h, 2, 2)), "A"), b = player(h, Vec3.atBottomCenterOf(at(h, 10, 2)), "B"),
                c = player(h, Vec3.atBottomCenterOf(at(h, 2, 10)), "C");
        mob.setTarget(a);
        h.succeedWhen(() -> {
            mob.setTarget(a);
            h.assertTrue(g.party() >= GiftedMob.PARTY_EACH * 2, "Three fighting it: half again for each past the first (" + g.party() + ")");
            h.assertTrue(Math.abs(mob.getMaxHealth() - alone * (1 + GiftedMob.PARTY_EACH * 2)) < 0.01, "Its health: " + alone + " -> " + mob.getMaxHealth());
            h.assertTrue(mob.getHealth() > alone, "And it keeps its share of it: " + mob.getHealth());
            remove(mob, a, b, c);
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "balance.starsleep")
    public static void nobodySleepsThroughAStarfall(GameTestHelper h) {
        var server = h.getLevel().getServer();
        ServerPlayer p = player(h, Vec3.atBottomCenterOf(at(h, 6, 6)), "Wren");
        try {
            Starfall.force(server, true);
            CanPlayerSleepEvent event = NeoForge.EVENT_BUS.post(new CanPlayerSleepEvent(p, at(h, 6, 6), null));
            h.assertTrue(event.getProblem() == Player.BedSleepingProblem.OTHER_PROBLEM, "Stars falling: no sleep");
            Starfall.force(server, false);
            CanPlayerSleepEvent after = NeoForge.EVENT_BUS.post(new CanPlayerSleepEvent(p, at(h, 6, 6), null));
            h.assertTrue(after.getProblem() == null, "Once they stop, sleep as usual");
            h.succeed();
        } finally {
            Starfall.force(server, false);
            remove(p);
        }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "balance.days")
    public static void daysAreCountedOnTheCalendar(GameTestHelper h) {
        long now = Days.clock(h.getLevel());
        h.assertTrue(Math.abs(Days.since(h.getLevel(), now - 2 * Days.DAY) - 2) < 0.001, "Two calendar days");
        h.assertTrue(Days.since(h.getLevel(), now + Days.DAY) == 0, "Never negative (time set back)");
        var scar = Scars.crater(h.getLevel(), at(h, 6, 6), 3);
        try {
            h.assertTrue(Math.abs(scar.days(now + 3 * Days.DAY) - 3) < 0.01, "A scar ages by the calendar");
            h.succeed();
        } finally { Scars.forget(h.getLevel(), scar); }
    }
}
