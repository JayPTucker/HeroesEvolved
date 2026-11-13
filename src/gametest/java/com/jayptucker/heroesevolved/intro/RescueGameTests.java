package com.jayptucker.heroesevolved.intro;

import com.jayptucker.heroesevolved.PowerOwnershipTestSupport;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.entity.EntityCarryService;
import com.jayptucker.heroesevolved.events.AwakeningService;
import com.jayptucker.heroesevolved.flight.FlightService;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.GameType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.UUID;

/** Awakenings answer danger to their owner or a nearby ally, and the release saves whoever was going down. */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class RescueGameTests {
    private static ServerPlayer player(GameTestHelper helper, String name, int x) {
        ServerPlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), name)) {
            @Override public boolean isInvulnerableTo(DamageSource source) { return false; }
            @Override public void teleportTo(ServerLevel level, double x, double y, double z, float yaw, float pitch) { absMoveTo(x, y, z, yaw, pitch); }
        };
        player.setPos(spot(helper, x));
        helper.getLevel().addNewPlayer(player);
        return player;
    }

    /** The one being carried must be a real ServerPlayer: a FakePlayer refuses to ride anything. */
    private static ServerPlayer carriable(GameTestHelper helper, String name, int x) { return carriable(helper, name, x, 6); }

    private static ServerPlayer carriable(GameTestHelper helper, String name, int x, int z) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), name);
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), profile, ClientInformation.createDefault());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        player.connection = new ServerGamePacketListenerImpl(player.getServer(), connection, player,
                CommonListenerCookie.createInitial(profile, false)) {
            @Override public void send(Packet<?> packet) {}
        };
        try {
            Field grace = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            grace.setAccessible(true);
            grace.setInt(player, 0);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(x, 2, z))));
        helper.getLevel().addNewPlayer(player);
        return player;
    }

    private static Vec3 spot(GameTestHelper helper, int x) {
        return Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(x, 2, 6)));
    }

    private static Zombie attacker(GameTestHelper helper, ServerPlayer victim) { return attacker(helper, victim, 1.5, 0); }

    private static Zombie attacker(GameTestHelper helper, ServerPlayer victim, double dx, double dz) {
        Zombie zombie = EntityType.HUSK.create(helper.getLevel());
        zombie.setNoAi(true);
        zombie.moveTo(victim.getX() + dx, victim.getY(), victim.getZ() + dz);
        helper.getLevel().addFreshEntity(zombie);
        return zombie;
    }

    private static void remove(ServerPlayer... players) {
        for (ServerPlayer player : players) {
            EntityCarryService.release(player);
            PowerOwnershipTestSupport.release(player);
            PowerIntroService.forget(player);
            AwakeningService.forget(player);
            FlightService.stopForPowerRemoval(player);
            player.serverLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
        }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 260, batch = "RescueGameTests.speedster")
    public static void aSpeedsterRunsAnAllyOutOfTheFight(GameTestHelper helper) {
        ServerPlayer runner = player(helper, "runner", 2), ally = carriable(helper, "ally", 8);
        Zombie zombie = attacker(helper, ally);
        Vec3 start = ally.position();
        try {
            helper.assertTrue(PlayerAbilityService.assignDormantAbility(runner, ModAbilities.SUPERSPEED_ID), "Dormant speed assigned");
            ally.setHealth(6);
            DamageSource blow = ally.damageSources().mobAttack(zombie);
            AwakeningService.recordCombat(ally, blow);
            float landed = AwakeningService.interceptAllyDamage(ally, blow, 20);
            helper.assertTrue(ally.getHealth() - landed >= 1, "The blow on the ally cannot kill");
            helper.assertTrue(!AwakeningService.hasDormantPower(runner), "The ally's danger wakes the speedster");
            helper.assertTrue(PowerIntroService.isActive(runner), "The awakening plays its intro");
            helper.assertTrue(AwakeningService.inGrace(ally), "The ally is shielded until the rescue");
        } catch (RuntimeException failure) { zombie.discard(); remove(runner, ally); throw failure; }
        helper.runAfterDelay(20, () -> {
            try {
                helper.assertTrue(PowerIntroService.shields(ally), "The ally stays shielded through the rescue");
            } catch (RuntimeException failure) { zombie.discard(); remove(runner, ally); throw failure; }
        });
        helper.runAfterDelay(220, () -> {
            try {
                helper.assertTrue(!PowerIntroService.isActive(runner), "The run ends and controls return");
                helper.assertTrue(!EntityCarryService.isBeingCarried(ally), "The ally is set down at the end");
                double carried = ally.position().subtract(start).horizontalDistance();
                helper.assertTrue(carried >= 6, "The ally is carried well away from the fight (moved " + carried + ")");
                helper.assertTrue(ally.distanceTo(zombie) > 6, "The ally ends up away from their attacker");
                helper.assertTrue(ally.getHealth() >= ally.getMaxHealth() * 0.4F - 0.01F, "The ally gets a second wind");
                helper.succeed();
            } finally { zombie.discard(); remove(runner, ally); }
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 80, batch = "RescueGameTests.sky", skyAccess = true)
    public static void flightOnlyWakesUnderOpenSky(GameTestHelper helper) {
        ServerPlayer flyer = player(helper, "flyer", 6);
        Zombie zombie = attacker(helper, flyer);
        BlockPos roof = flyer.blockPosition().above(3);
        try {
            helper.assertTrue(Danger.openSky(flyer), "The test arena is open to the sky");
            helper.assertTrue(PlayerAbilityService.assignDormantAbility(flyer, ModAbilities.FLIGHT_ID), "Dormant flight assigned");
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                helper.getLevel().setBlock(roof.offset(dx, 0, dz), Blocks.STONE.defaultBlockState(), 3);
            helper.assertTrue(!Danger.openSky(flyer), "Under a roof is not open sky");
            AwakeningService.recordCombat(flyer, flyer.damageSources().mobAttack(zombie));
            flyer.setHealth(3);
            AwakeningService.tick(flyer);
            helper.assertTrue(AwakeningService.hasDormantPower(flyer), "Flight cannot awaken underground");
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                helper.getLevel().setBlock(roof.offset(dx, 0, dz), Blocks.AIR.defaultBlockState(), 3);
            AwakeningService.tick(flyer);
            helper.assertTrue(!AwakeningService.hasDormantPower(flyer), "Out under the sky, flight wakes");
            helper.succeed();
        } finally { zombie.discard(); remove(flyer); }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 200, batch = "RescueGameTests.flight", skyAccess = true)
    public static void aFlyerCarriesAnAllyIntoTheSky(GameTestHelper helper) {
        // The arena's barrier walls stand about seven blocks high. The flyer climbs from where it stands, in the
        // middle of the arena, so whichever way it heads (away from every monster near, and monsters in neighbouring
        // test arenas count) it is well over the walls by the time it reaches them.
        ServerPlayer flyer = player(helper, "flyer", 6), ally = carriable(helper, "ally", 6, 9);
        Zombie zombie = attacker(helper, ally, 0, 1.5);
        double ground = ally.getY();
        boolean[] carried = {false};
        double[] highest = {ground};
        try {
            helper.assertTrue(PlayerAbilityService.assignDormantAbility(flyer, ModAbilities.FLIGHT_ID), "Dormant flight assigned");
            ally.setHealth(3);
            AwakeningService.recordCombat(ally, ally.damageSources().mobAttack(zombie));
            helper.assertTrue(AwakeningService.answerAlly(flyer), "An ally going down under open sky wakes the flyer");
            helper.assertTrue(PowerIntroService.isActive(flyer), "The awakening plays its intro");
        } catch (RuntimeException failure) { zombie.discard(); remove(flyer, ally); throw failure; }
        for (int tick = 20; tick < 100; tick++) {
            helper.runAfterDelay(tick, () -> {
                // While carried, the ally is held at the flyer: their own position only follows when the carrier's chunk
                // ticks, and a test flyer climbing out of the arena leaves the chunks with no player to keep them ticking.
                boolean held = EntityCarryService.isBeingCarried(ally);
                if (held) carried[0] = true;
                highest[0] = Math.max(highest[0], held ? flyer.getY() : ally.getY());
            });
        }
        helper.runAfterDelay(140, () -> {
            try {
                helper.assertTrue(carried[0], "The flyer swoops in and takes the ally");
                StringBuilder above = new StringBuilder();
                net.minecraft.core.BlockPos top = flyer.blockPosition();
                for (int dy = -2; dy <= 12; dy++) {
                    var state = helper.getLevel().getBlockState(top.above(dy));
                    if (!state.isAir()) above.append(" ").append(dy).append(":").append(state.getBlock().getName().getString());
                }
                helper.assertTrue(highest[0] > ground + 8, "The ally is carried up out of the fight (rose " + (highest[0] - ground)
                        + ", flyer at " + helper.relativePos(flyer.blockPosition()) + ", around it:" + above + ")");
                helper.assertTrue(FlightService.hasActiveSession(flyer), "The flyer ends in real flight");
                helper.assertTrue(!PowerIntroService.isActive(flyer), "Controls return after the airlift");
                helper.succeed();
            } finally { zombie.discard(); remove(flyer, ally); }
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 60, batch = "RescueGameTests.reach")
    public static void anAllyOutOfReachIsNotAnswered(GameTestHelper helper) {
        ServerPlayer owner = player(helper, "owner", 2), ally = player(helper, "ally", 9);
        Zombie zombie = attacker(helper, ally);
        try {
            helper.assertTrue(PlayerAbilityService.assignDormantAbility(owner, ModAbilities.REGENERATION_ID), "Dormant power assigned");
            ally.setHealth(10);
            AwakeningService.recordCombat(ally, ally.damageSources().mobAttack(zombie));
            helper.assertTrue(!AwakeningService.answerAlly(owner), "An ally who is still standing doesn't wake anything");
            ally.setHealth(3);
            owner.setPos(owner.getX(), owner.getY() + 40, owner.getZ());
            helper.assertTrue(!AwakeningService.answerAlly(owner), "An ally beyond reach isn't answered");
            owner.setPos(owner.getX(), owner.getY() - 40, owner.getZ());
            helper.assertTrue(AwakeningService.answerAlly(owner), "An ally in reach and going down is");
            helper.succeed();
        } finally { zombie.discard(); remove(owner, ally); }
    }
}
