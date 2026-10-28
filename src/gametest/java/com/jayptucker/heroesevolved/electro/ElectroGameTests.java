package com.jayptucker.heroesevolved.electro;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The rebuilt Electrokinesis kit: tap and hold, Charge, the delayed strike, the scar, the wind-up. */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class ElectroGameTests {
    private static ServerPlayer player(GameTestHelper h, int level) {
        ServerPlayer p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "electro-test")) {
            @Override public boolean isInvulnerableTo(DamageSource damage) { return false; }
            // FakePlayers ignore connection teleports; move them directly.
            @Override public void teleportTo(double x, double y, double z) { absMoveTo(x, y, z); }
        };
        try {
            Connection connection = p.connection.getConnection();
            Field channel = Connection.class.getDeclaredField("channel"); channel.setAccessible(true);
            if (connection.channel() == null) channel.set(connection, new EmbeddedChannel());
            Field grace = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            grace.setAccessible(true); grace.setInt(p, 0);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 2, 3)))); p.setYRot(0); p.setXRot(0);
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty()
                .assignDormant(ModAbilities.ELECTROKINESIS_ID).unlock(ModAbilities.ELECTROKINESIS_ID));
        PlayerProgressionService.setLevel(p, level);
        PlayerEnergyService.restore(p, PlayerEnergyService.getMaximumEnergy(p));
        return p;
    }

    /** Orbs, strikes and scars find their owner through the player list, so tests that use them register the player. */
    @SuppressWarnings("unchecked")
    private static void register(ServerPlayer player) {
        PlayerList list = player.getServer().getPlayerList();
        try {
            Field players = PlayerList.class.getDeclaredField("players"); players.setAccessible(true);
            Field byId = PlayerList.class.getDeclaredField("playersByUUID"); byId.setAccessible(true);
            ((List<ServerPlayer>) players.get(list)).add(player);
            ((Map<UUID, ServerPlayer>) byId.get(list)).put(player.getUUID(), player);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    @SuppressWarnings("unchecked")
    private static void unregister(ServerPlayer player, Entity... targets) {
        ElectroService.forget(player);
        PlayerList list = player.getServer().getPlayerList();
        try {
            Field players = PlayerList.class.getDeclaredField("players"); players.setAccessible(true);
            Field byId = PlayerList.class.getDeclaredField("playersByUUID"); byId.setAccessible(true);
            ((List<ServerPlayer>) players.get(list)).remove(player);
            ((Map<UUID, ServerPlayer>) byId.get(list)).remove(player.getUUID());
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        for (Entity target : targets) target.discard();
    }

    private static LivingEntity target(GameTestHelper h, Vec3 at) {
        var target = EntityType.PILLAGER.create(h.getLevel());
        target.setNoAi(true); target.setPersistenceRequired();
        target.moveTo(at.x, at.y, at.z, 180, 0);
        h.getLevel().addFreshEntity(target);
        return target;
    }

    private static boolean hurt(LivingEntity target) {
        return target.getHealth() < target.getMaxHealth();
    }

    @GameTest(template = "rewind_empty", batch = "electro.hold", timeoutTicks = 80)
    public static void tapFiresABoltAndHoldingBecomesLiveCurrent(GameTestHelper h) {
        ServerPlayer p = player(h, 3);
        LivingEntity pillager = target(h, p.position().add(0, 0, 6));
        ElectroService.press(p, 3);
        ElectroService.release(p);
        h.assertTrue(hurt(pillager), "A tap fires Chain Lightning at once");
        pillager.setHealth(pillager.getMaxHealth());
        pillager.invulnerableTime = 0;
        ElectroService.press(p, 3);
        h.runAfterDelay(30, () -> {
            try {
                h.assertTrue(ElectroService.isHolding(p), "Holding the key keeps Live Current going");
                h.assertTrue(hurt(pillager), "Live Current hurts its target while it holds it");
                h.assertTrue(pillager.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "Live Current locks the target's muscles");
                ElectroService.release(p);
                h.assertTrue(!ElectroService.isHolding(p), "Letting go ends the stream");
                h.succeed();
            } finally { ElectroService.forget(p); pillager.discard(); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "electro.charge")
    public static void chargeBuildsAndOverchargeEmpowersTheNextPower(GameTestHelper h) {
        ServerPlayer p = player(h, 4);
        LivingEntity pillager = target(h, p.position().add(0, 0, 6));
        try {
            ElectroService.chainLightning(p, 4);
            h.assertTrue(ElectroService.charge(p) > 0, "Shocking a foe builds Charge");
            ElectroService.fillCharge(p);
            h.assertTrue(ElectroService.charge(p) == ElectroService.MAX_CHARGE, "Charge fills to its maximum");
            pillager.invulnerableTime = 0;
            ElectroService.chainLightning(p, 4);
            h.assertTrue(ElectroService.charge(p) < ElectroService.MAX_CHARGE, "An Overcharged bolt spends the full Charge");
            // An Overcharged Thunderstrike calls three strikes.
            ElectroService.fillCharge(p);
            p.setXRot(40);
            h.assertTrue(ElectroService.thunderstrike(p, 4), "There is ground to strike");
            h.assertTrue(ElectroService.pendingStrikes(p) == 3, "Overcharged Thunderstrike strikes three times, got " + ElectroService.pendingStrikes(p));
            h.succeed();
        } finally { ElectroService.forget(p); pillager.discard(); }
    }

    @GameTest(template = "rewind_empty", batch = "electro.strike", timeoutTicks = 60)
    public static void thunderstrikeWarnsBeforeItLands(GameTestHelper h) {
        ServerPlayer p = player(h, 3);
        register(p);
        LivingEntity pillager = target(h, p.position().add(0, 0, 5));
        p.setXRot(18);
        h.assertTrue(ElectroService.thunderstrike(p, 3), "There is ground to strike");
        h.assertTrue(!hurt(pillager), "The strike is a warning first");
        h.runAfterDelay(24, () -> {
            try {
                h.assertTrue(hurt(pillager), "Lightning lands on the marked spot a moment later");
                h.succeed();
            } finally { unregister(p, pillager); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "electro.ball", timeoutTicks = 40)
    public static void ballLightningBurstsEarlyOnShift(GameTestHelper h) {
        ServerPlayer p = player(h, 2);
        register(p);
        // Off to the side, so the orb neither touches it nor curves toward it before it bursts.
        LivingEntity pillager = target(h, p.position().add(2.3, 0, 1));
        ElectroService.ballLightning(p, 2);
        h.runAfterDelay(1, () -> {
            try {
                h.assertTrue(ElectroService.hasOrb(p), "The orb is in flight");
                h.assertTrue(ElectroService.detonate(p), "Shift + key bursts the orb");
                h.assertTrue(!ElectroService.hasOrb(p), "A burst orb is gone");
                h.assertTrue(hurt(pillager), "The burst shocks what is around it");
                h.succeed();
            } finally { unregister(p, pillager); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "electro.ride", timeoutTicks = 40)
    public static void rideTheLightningLeavesALiveScar(GameTestHelper h) {
        ServerPlayer p = player(h, 4);
        register(p);
        Vec3 start = p.position();
        ElectroService.rideTheLightning(p, 4);
        Vec3 end = p.position();
        h.assertTrue(end.distanceTo(start) > 5, "The ride carries you down the line");
        // A foe that walks onto the path afterwards is shocked by the scar.
        LivingEntity pillager = target(h, start.lerp(end, 0.5));
        h.runAfterDelay(6, () -> {
            try {
                h.assertTrue(hurt(pillager), "The ground the ride crossed stays live");
                h.succeed();
            } finally { unregister(p, pillager); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "electro.ride_down")
    public static void rideTheLightningWorksWhileLookingDown(GameTestHelper h) {
        ServerPlayer p = player(h, 4);
        // Most players look slightly down; the ride used to stop at its first step into the ground.
        p.setXRot(12);
        Vec3 start = p.position();
        try {
            ElectroService.rideTheLightning(p, 4);
            h.assertTrue(p.position().distanceTo(start) > 5, "The ride follows the ground instead of stopping, moved " + p.position().distanceTo(start));
            h.succeed();
        } finally { ElectroService.forget(p); }
    }

    @GameTest(template = "rewind_empty", batch = "electro.surge", timeoutTicks = 80)
    public static void stormSurgeWindsUpThenReleases(GameTestHelper h) {
        ServerPlayer p = player(h, 5);
        LivingEntity pillager = target(h, p.position().add(0, 0, 5));
        ElectroService.startSurge(p, 5);
        h.assertTrue(ElectroService.isSurging(p) && !hurt(pillager), "Storm Surge gathers before it strikes");
        h.runAfterDelay(36, () -> {
            try {
                h.assertTrue(!ElectroService.isSurging(p), "The wind-up ends in a release");
                h.assertTrue(hurt(pillager), "The surge strikes everything around you");
                h.succeed();
            } finally { ElectroService.forget(p); pillager.discard(); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "electro.surge_cancel", timeoutTicks = 80)
    public static void shiftCancelsStormSurge(GameTestHelper h) {
        ServerPlayer p = player(h, 5);
        LivingEntity pillager = target(h, p.position().add(0, 0, 5));
        ElectroService.startSurge(p, 5);
        h.assertTrue(ElectroService.cancelSurge(p) && !ElectroService.isSurging(p), "Shift + key cancels the wind-up");
        h.runAfterDelay(36, () -> {
            try {
                h.assertTrue(!hurt(pillager), "A cancelled surge never releases");
                h.succeed();
            } finally { ElectroService.forget(p); pillager.discard(); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "electro.water")
    public static void waterTurnsYourChargeOnYou(GameTestHelper h) {
        ServerPlayer p = player(h, 3);
        BlockPos feet = p.blockPosition();
        try {
            ElectroService.addCharge(p, 60);
            h.getLevel().setBlock(feet, Blocks.WATER.defaultBlockState(), 3);
            p.baseTick();
            float health = p.getHealth();
            ElectroService.playerTick(p);
            h.assertTrue(ElectroService.charge(p) == 0, "Water drains all the Charge at once, and lightning absorption must not refill it");
            h.assertTrue(p.getHealth() < health, "And it goes through you");
            h.succeed();
        } finally {
            h.getLevel().setBlock(feet, Blocks.AIR.defaultBlockState(), 3);
            ElectroService.forget(p);
        }
    }
}
