package com.jayptucker.heroesevolved.travel;

import com.jayptucker.heroesevolved.company.CompanyAttention;
import com.jayptucker.heroesevolved.company.CompanyOps;
import com.jayptucker.heroesevolved.company.Pursuit;
import com.jayptucker.heroesevolved.dampener.Dampening;
import com.jayptucker.heroesevolved.gifted.GiftedMobs;
import com.jayptucker.heroesevolved.registry.ModItems;
import com.jayptucker.heroesevolved.village.VillageMemory;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class TravellerLifecycleGameTests {
    private static BlockPos bell(GameTestHelper h) { return h.absolutePos(new BlockPos(10, 2, 10)); }
    private static Vec3 start(GameTestHelper h) { return Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(1, 2, 1))); }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "travel.unloadedcargo")
    public static void anUnloadedCaravanDoesNotDropItsCargo(GameTestHelper h) {
        Caravan c = (Caravan) Travellers.start(h.getLevel(), Party.Kind.CARAVAN, start(h), bell(h));
        try {
            for (var v : c.drovers()) v.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            c.tick();
            h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(bell(h)).inflate(20),
                    i -> i.getItem().is(Items.BREAD)).isEmpty(), "Unloading is not a lost caravan and gives no cargo");
            h.assertTrue(c.stage() == Party.Stage.OVER && !c.delivered(), "The unloaded journey ends without a delivery");
            h.assertTrue(c.members().stream().noneMatch(m -> Travellers.LIVE.contains(m.getUUID())), "No travellers remain registered");
            h.succeed();
        } finally {
            Travellers.clearAll();
            for (var m : c.members()) Travellers.release(m, true);
            VillageMemory.forget(h.getLevel(), bell(h));
        }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "travel.partialunload")
    public static void losingOneTravellerEndsTheJourneyWithoutRewards(GameTestHelper h) {
        Caravan c = (Caravan) Travellers.start(h.getLevel(), Party.Kind.CARAVAN, start(h), bell(h));
        try {
            c.drovers().getFirst().remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            c.tick();
            h.assertTrue(c.stage() == Party.Stage.OVER && !c.delivered(), "A party cannot keep walking with an unloaded member");
            h.assertTrue(c.members().stream().allMatch(Entity::isRemoved), "Its remaining members leave");
            h.assertTrue(c.members().stream().noneMatch(m -> Travellers.LIVE.contains(m.getUUID())), "Its entire roster is released");
            h.succeed();
        } finally {
            Travellers.clearAll();
            for (var m : c.members()) Travellers.release(m, true);
            VillageMemory.forget(h.getLevel(), bell(h));
        }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "travel.deadcleanup")
    public static void aDeadTravellerIsNoLongerRegisteredAsLive(GameTestHelper h) {
        Party party = Travellers.start(h.getLevel(), Party.Kind.PEDLAR, start(h), bell(h));
        try {
            party.members().getFirst().kill();
            party.tick();
            h.assertTrue(party.stage() == Party.Stage.OVER, "The journey is over");
            h.assertTrue(party.members().stream().noneMatch(m -> Travellers.LIVE.contains(m.getUUID())), "Dead travellers leave the live roster");
            h.succeed();
        } finally {
            Travellers.clearAll();
            for (var m : party.members()) Travellers.release(m, true);
            VillageMemory.forget(h.getLevel(), bell(h));
        }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "travel.cancelpursuit")
    public static void clearingRefugeesAlsoCallsOffTheirPursuit(GameTestHelper h) {
        FakePlayer p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "RoadWitness"));
        Refugees r = (Refugees) Travellers.start(h.getLevel(), Party.Kind.REFUGEES, start(h), bell(h));
        try {
            r.pursuit = Pursuit.start(h.getLevel(), p, r.family(), start(h));
            h.assertTrue(r.pursuit != null, "The pursuit starts");
            Travellers.clearAll();
            h.assertTrue(r.pursuit.over() && CompanyOps.current(p) == null, "Clearing the party immediately ends its pursuit");
            h.assertTrue(r.pursuit.agents().stream().allMatch(Entity::isRemoved), "Its agents leave too");
            h.succeed();
        } finally {
            Travellers.clearAll();
            CompanyOps.callOffAll();
            CompanyAttention.forget(p);
            VillageMemory.forget(h.getLevel(), bell(h));
        }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "travel.dampenedhealer")
    public static void aDampenedHealerCannotMendUntilTheirGiftReturns(GameTestHelper h) {
        Healer party = (Healer) Travellers.start(h.getLevel(), Party.Kind.HEALER, start(h), bell(h));
        Villager patient = EntityType.VILLAGER.create(h.getLevel());
        patient.moveTo(start(h).add(1, 0, 0));
        patient.getPersistentData().putBoolean(GiftedMobs.ROLLED, true);
        patient.setNoAi(true);
        h.getLevel().addFreshEntity(patient);
        patient.setHealth(4);
        try {
            Dampening.strike(null, party.healer(), party.healer().position());
            h.assertTrue(Dampening.dampened(party.healer()), "The healer's gift is dampened");
            party.age = Healer.EVERY;
            party.business();
            h.assertTrue(patient.getHealth() == 4, "A dampened healer cannot heal: " + patient.getHealth());
            party.healer().removeEffect(ModItems.DAMPENED);
            party.business();
            h.assertTrue(patient.getHealth() == 4 + Healer.MEND, "Mending resumes when their gift returns");
            h.succeed();
        } finally {
            Travellers.clearAll();
            patient.discard();
            VillageMemory.forget(h.getLevel(), bell(h));
        }
    }
}
