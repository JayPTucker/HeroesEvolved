package com.jayptucker.heroesevolved.company;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.registry.ModEntities;
import com.jayptucker.heroesevolved.registry.ModItems;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** The Company's agents: darts that deepen and put you under, guards who go down instead of dying in the past, and the Suppressor's quiet. */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class CompanyAgentGameTests {
    private static CompanyAgentEntity agent(GameTestHelper h, CompanyAgentEntity.Role role, boolean nonLethal, BlockPos relative) {
        CompanyAgentEntity agent = CompanyAgentEntity.create(ModEntities.COMPANY_AGENT.get(), h.getLevel(), role,
                Vec3.atBottomCenterOf(h.absolutePos(relative)), 0, nonLethal);
        h.assertTrue(agent != null && h.getLevel().addFreshEntity(agent), "An agent must spawn");
        return agent;
    }

    /** A powered stranger the agents' own targeting doesn't see (not in the level's player list). */
    private static ServerPlayer stranger(GameTestHelper h, Vec3 at) {
        ServerPlayer p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "company-test"));
        p.setPos(at);
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(ModAbilities.QUAKE_ID).unlock(ModAbilities.QUAKE_ID));
        return p;
    }

    @GameTest(template = "rewind_empty", batch = "company.darts")
    public static void dartsDeepenAndTheThirdPutsYouUnder(GameTestHelper h) {
        CompanyAgentEntity guard = agent(h, CompanyAgentEntity.Role.GUARD, false, new BlockPos(1, 2, 1));
        ServerPlayer p = stranger(h, Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(4, 2, 4))));
        try {
            h.assertTrue(guard.getMainHandItem().is(Items.CROSSBOW), "A guard carries a tranquilizer rifle");
            double speed = p.getAttributeValue(Attributes.MOVEMENT_SPEED);
            h.assertTrue(Tranquilizer.dart(guard, p) == 1 && Tranquilizer.doses(p) == 1, "One dart, one dose");
            h.assertTrue(p.getAttributeValue(Attributes.MOVEMENT_SPEED) < speed, "A dose makes your legs heavy");
            double slower = p.getAttributeValue(Attributes.MOVEMENT_SPEED);
            h.assertTrue(Tranquilizer.dart(guard, p) == 2 && p.getAttributeValue(Attributes.MOVEMENT_SPEED) < slower,
                    "A second dart deepens the dose");
            Tranquilizer.dart(guard, p);
            h.assertTrue(!p.hasEffect(ModItems.TRANQUILIZED) && p.hasEffect(MobEffects.BLINDNESS) && Tranquilizer.spared(p),
                    "The third dart puts you under, and the agents leave you be for a while");
            h.succeed();
        } finally { guard.discard(); }
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(net.minecraft.server.players.PlayerList list, String name) {
        try {
            java.lang.reflect.Field field = net.minecraft.server.players.PlayerList.class.getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(list);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    @GameTest(template = "rewind_empty", batch = "company.aim", timeoutTicks = 200)
    public static void aGuardSeesAStrangerAimsAndFires(GameTestHelper h) {
        CompanyAgentEntity guard = agent(h, CompanyAgentEntity.Role.GUARD, false, new BlockPos(1, 2, 1));
        // A listed player in survival, standing still in the open six blocks away.
        ServerPlayer p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "company-target"));
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(7, 2, 1))));
        p.xo = p.getX(); p.zo = p.getZ();
        h.getLevel().addNewPlayer(p);
        var list = h.getLevel().getServer().getPlayerList();
        CompanyAgentGameTests.<java.util.List<ServerPlayer>>field(list, "players").add(p);
        boolean[] aimed = new boolean[1];
        h.onEachTick(() -> { if (guard.aiming()) aimed[0] = true; });
        h.succeedWhen(() -> {
            h.assertTrue(guard.getTarget() == p, "The guard turns on the stranger");
            h.assertTrue(Tranquilizer.doses(p) >= 1, "...and darts them");
            h.assertTrue(aimed[0], "He raised the rifle before he fired");
            CompanyAgentGameTests.<java.util.List<ServerPlayer>>field(list, "players").remove(p);
            h.getLevel().removePlayerImmediately(p, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
            guard.discard();
        });
    }

    @GameTest(template = "rewind_empty", batch = "company.darts")
    public static void movingFastMakesDartsMiss(GameTestHelper h) {
        ServerPlayer p = stranger(h, Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2, 2, 2))));
        p.xo = p.getX(); p.zo = p.getZ();
        h.assertTrue(!CompanyAgentEntity.dodges(p, 0.0), "Standing still, every dart lands");
        p.xo = p.getX() - 0.3;
        h.assertTrue(CompanyAgentEntity.dodges(p, 0.3) && !CompanyAgentEntity.dodges(p, 0.6), "A sprint makes some darts miss");
        p.xo = p.getX() - 0.9;
        h.assertTrue(CompanyAgentEntity.dodges(p, 0.8) && !CompanyAgentEntity.dodges(p, 0.9), "A blur makes most of them miss");
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "company.down")
    public static void inThePastAgentsGoDownInsteadOfDying(GameTestHelper h) {
        CompanyAgentEntity past = agent(h, CompanyAgentEntity.Role.GUARD, true, new BlockPos(1, 2, 1));
        CompanyAgentEntity present = agent(h, CompanyAgentEntity.Role.GUARD, false, new BlockPos(4, 2, 4));
        try {
            h.assertTrue(past.hurt(h.getLevel().damageSources().generic(), 100) && past.isAlive() && past.outCold() && past.getHealth() >= 1,
                    "A blow that would kill a guard in the past puts him on the floor instead");
            h.assertTrue(past.getTarget() == null && !past.hurt(h.getLevel().damageSources().generic(), 5),
                    "Out cold, he gives no more orders and takes no more blows");
            present.hurt(h.getLevel().damageSources().generic(), 100);
            h.assertTrue(!present.isAlive(), "An agent in the present is as mortal as anyone");
            h.succeed();
        } finally { past.discard(); present.discard(); }
    }

    @GameTest(template = "rewind_empty", batch = "company.quiet", timeoutTicks = 100)
    public static void theSuppressorSilencesPowersNearHim(GameTestHelper h) {
        CompanyAgentEntity suppressor = agent(h, CompanyAgentEntity.Role.SUPPRESSOR, true, new BlockPos(1, 2, 1));
        ServerPlayer near = stranger(h, suppressor.position().add(4, 0, 0));
        ServerPlayer far = stranger(h, suppressor.position().add(SuppressorField.RADIUS + 3, 0, 0));
        h.assertTrue(suppressor.getMainHandItem().isEmpty(), "The Suppressor carries no rifle");
        h.runAfterDelay(3, () -> {
            try {
                h.assertTrue(SuppressorField.suppresses(near) && !PowerRules.canAct(near), "Four blocks from him, no power answers");
                h.assertTrue(!SuppressorField.suppresses(far) && PowerRules.canAct(far), "Past the edge of his quiet, powers work");
                suppressor.knockOut();
                h.assertTrue(!SuppressorField.suppresses(near) && PowerRules.canAct(near), "Put him down and the quiet lifts");
                h.succeed();
            } finally { suppressor.discard(); }
        });
    }
}
