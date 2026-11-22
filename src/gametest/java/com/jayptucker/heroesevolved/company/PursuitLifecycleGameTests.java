package com.jayptucker.heroesevolved.company;

import com.jayptucker.heroesevolved.gifted.GiftedMobs;
import com.jayptucker.heroesevolved.village.VillageTickets;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class PursuitLifecycleGameTests {
    private static Villager refugee(GameTestHelper h, int x) {
        Villager v = EntityType.VILLAGER.create(h.getLevel());
        v.moveTo(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(x, 2, 3))));
        v.setNoAi(true);
        v.getPersistentData().putBoolean(GiftedMobs.ROLLED, true);
        h.getLevel().addFreshEntity(v);
        return v;
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "company.pursuitcapture")
    public static void twoAgentsCannotTakeTheSameRefugee(GameTestHelper h) {
        ServerPlayer p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "RoadWitness"));
        List<Villager> family = new ArrayList<>(List.of(refugee(h, 3), refugee(h, 9)));
        Pursuit hunt = null;
        try {
            hunt = Pursuit.start(h.getLevel(), p, family, family.getFirst().position());
            h.assertTrue(hunt != null && hunt.agents().size() == 2, "Both agents set out");
            for (CompanyAgentEntity a : hunt.agents()) a.setPos(family.getFirst().position());
            hunt.tick();
            h.assertTrue(hunt.taken() == 1, "Only one refugee was taken, counted " + hunt.taken());
            h.assertTrue(family.getFirst().isRemoved() && !family.get(1).isRemoved(), "The distant refugee is still free");
            h.succeed();
        } finally {
            CompanyOps.callOffAll();
            if (hunt != null) for (CompanyAgentEntity a : hunt.agents()) a.discard();
            for (Villager v : family) v.discard();
            CompanyAttention.forget(p);
        }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "company.pursuitending")
    public static void agentsLeaveWhenTheLastRefugeeIsTaken(GameTestHelper h) {
        ServerPlayer p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "RoadWitness"));
        Villager v = refugee(h, 3);
        Pursuit hunt = null;
        try {
            hunt = Pursuit.start(h.getLevel(), p, List.of(v), v.position());
            h.assertTrue(hunt != null, "The pursuit starts");
            for (CompanyAgentEntity a : hunt.agents()) a.setPos(v.position());
            hunt.tick();
            hunt.tick();
            h.assertTrue(hunt.over(), "The pursuit is over");
            h.assertTrue(hunt.agents().stream().allMatch(Entity::isRemoved), "Its agents leave with their prisoner");
            h.assertTrue(!VillageTickets.held(hunt.key() + "road"), "The road ticket is released");
            h.succeed();
        } finally {
            CompanyOps.callOffAll();
            if (hunt != null) for (CompanyAgentEntity a : hunt.agents()) a.discard();
            v.discard();
            CompanyAttention.forget(p);
        }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "company.pursuitunload")
    public static void anUnloadedRefugeeCallsOffTheHuntWithoutAnOutcome(GameTestHelper h) {
        ServerPlayer p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "RoadWitness"));
        List<Villager> family = List.of(refugee(h, 3), refugee(h, 9));
        Pursuit hunt = null;
        try {
            hunt = Pursuit.start(h.getLevel(), p, family, family.getFirst().position());
            h.assertTrue(hunt != null, "The pursuit starts");
            family.getFirst().remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            CompanyOps.tick(h.getLevel().getServer());
            h.assertTrue(hunt.over() && CompanyOps.current(p) == null, "Losing a refugee calls off the hunt");
            h.assertTrue(hunt.taken() == 0 && !family.get(1).isRemoved(), "No capture or death is inferred from unloading");
            h.assertTrue(hunt.agents().stream().allMatch(Entity::isRemoved), "The agents leave");
            h.assertTrue(!VillageTickets.held(hunt.key() + "road"), "Its road ticket is released");
            h.succeed();
        } finally {
            CompanyOps.callOffAll();
            if (hunt != null) for (CompanyAgentEntity a : hunt.agents()) a.discard();
            for (Villager v : family) v.discard();
            CompanyAttention.forget(p);
        }
    }
}
