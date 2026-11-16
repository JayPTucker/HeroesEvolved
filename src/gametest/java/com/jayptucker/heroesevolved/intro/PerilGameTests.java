package com.jayptucker.heroesevolved.intro;

import com.jayptucker.heroesevolved.PowerOwnershipTestSupport;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.events.AwakeningService;
import com.jayptucker.heroesevolved.mixin.CreeperSwellAccess;
import com.jayptucker.heroesevolved.time.TimeStopService;
import com.jayptucker.heroesevolved.time.intro.TimeIntroService;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.server.players.PlayerList;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Each power wakes for the dangers it can answer, not only for a lost fight: Space-Time stops time for anyone under
 * two hearts or about to be caught by a creeper, a telekinetic catches a fall, a cryokinetic freezes the lava. A power
 * that can't help stays asleep.
 */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class PerilGameTests {
    private static ServerPlayer player(GameTestHelper helper, String name, int x, double y) {
        ServerPlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), name)) {
            @Override public boolean isInvulnerableTo(DamageSource source) { return false; }
            @Override public void teleportTo(ServerLevel level, double x, double y, double z, float yaw, float pitch) { absMoveTo(x, y, z, yaw, pitch); }
        };
        player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(x, 2, 6))).add(0, y - 2, 0));
        helper.getLevel().addNewPlayer(player);
        return player;
    }

    /**
     * A time stop looks its owner up in the player list every tick and ends without them, so a test traveller must be
     * listed (with a channel, or the server's time sync to every listed player crashes).
     */
    @SuppressWarnings("unchecked")
    private static ServerPlayer listed(ServerPlayer player) {
        try {
            Connection connection = player.connection.getConnection();
            Field channel = Connection.class.getDeclaredField("channel"); channel.setAccessible(true);
            if (connection.channel() == null) channel.set(connection, new EmbeddedChannel());
            PlayerList list = player.getServer().getPlayerList();
            Field players = PlayerList.class.getDeclaredField("players"); players.setAccessible(true);
            Field byId = PlayerList.class.getDeclaredField("playersByUUID"); byId.setAccessible(true);
            ((List<ServerPlayer>) players.get(list)).add(player);
            ((Map<UUID, ServerPlayer>) byId.get(list)).put(player.getUUID(), player);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        return player;
    }

    private static void unlist(ServerPlayer player) {
        try {
            PlayerList list = player.getServer().getPlayerList();
            Field players = PlayerList.class.getDeclaredField("players"); players.setAccessible(true);
            Field byId = PlayerList.class.getDeclaredField("playersByUUID"); byId.setAccessible(true);
            ((List<?>) players.get(list)).remove(player);
            ((Map<?, ?>) byId.get(list)).remove(player.getUUID());
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private static void remove(ServerPlayer... players) {
        for (ServerPlayer player : players) {
            unlist(player);
            PowerOwnershipTestSupport.release(player);
            PowerIntroService.forget(player);
            AwakeningService.forget(player);
            TimeStopService.stop(player);
            player.serverLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
        }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 60, batch = "PerilGameTests.creeper")
    public static void aCreeperAboutToBlowStopsTime(GameTestHelper helper) {
        ServerPlayer traveller = listed(player(helper, "traveller", 2, 2)), ally = player(helper, "ally", 9, 2);
        Creeper creeper = EntityType.CREEPER.create(helper.getLevel());
        creeper.setNoAi(true);
        creeper.moveTo(ally.getX() + 1.5, ally.getY(), ally.getZ());
        helper.getLevel().addFreshEntity(creeper);
        try {
            helper.assertTrue(PlayerAbilityService.assignDormantAbility(traveller, ModAbilities.TIME_MANIPULATION_ID), "Dormant Space-Time assigned");
            // The fuse is two-thirds burnt down and still burning.
            ((CreeperSwellAccess) creeper).heroesEvolved$setSwell(20);
            ((CreeperSwellAccess) creeper).heroesEvolved$setOldSwell(20);
            creeper.setSwellDir(1);
            helper.assertTrue(Danger.imminent(creeper), "The creeper is about to go off");
            helper.assertTrue(Danger.blastEndangers(creeper, ally, 4), "The blast would kill the ally");
            helper.assertTrue(AwakeningService.answerBlast(traveller), "The blast wakes the traveller");
            helper.assertTrue(!AwakeningService.hasDormantPower(traveller), "Space-Time is awake");
            helper.assertTrue(TimeAwakening.stopping(traveller) && TimeStopService.hasActiveField(traveller), "Time stops");
            helper.assertTrue(TimeStopService.isStopped(creeper), "The creeper is caught in the stop");
            helper.assertTrue(!TimeIntroService.isQueued(traveller), "The vision waits until time starts again");
            helper.assertTrue(AwakeningService.inGrace(ally), "The ally is shielded through the stop");
        } catch (RuntimeException failure) { creeper.discard(); remove(traveller, ally); throw failure; }
        helper.runAfterDelay(15, () -> {
            try {
                helper.assertTrue(TimeAwakening.stopping(traveller), "Time is still stopped");
                helper.assertTrue(creeper.isAlive(), "Stopped time holds the fuse: the creeper never goes off");
                int swell = ((CreeperSwellAccess) creeper).heroesEvolved$swell();
                helper.assertTrue(swell == 20, "The fuse is held where it was (swell " + swell + ")");
                helper.succeed();
            } finally { creeper.discard(); remove(traveller, ally); }
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "PerilGameTests.low")
    public static void aTravellerStopsTimeForAnyoneUnderTwoHearts(GameTestHelper helper) {
        ServerPlayer traveller = listed(player(helper, "traveller", 4, 2)), hurt = player(helper, "hurt", 9, 2);
        try {
            helper.assertTrue(PlayerAbilityService.assignDormantAbility(traveller, ModAbilities.TIME_MANIPULATION_ID), "Dormant Space-Time assigned");
            // Hurt by something that isn't a fight: no attacker at all.
            AwakeningService.recordCombat(hurt, hurt.damageSources().magic());
            hurt.setHealth(3);
            helper.assertTrue(AwakeningService.peril(hurt, "regeneration") == null, "Being low without a fight doesn't wake most powers");
            helper.assertTrue(AwakeningService.peril(hurt, "time_manipulation") == Danger.Peril.LOW, "Under two hearts is enough for Space-Time");
            helper.assertTrue(AwakeningService.answerAlly(traveller), "The traveller answers them");
            helper.assertTrue(TimeAwakening.stopping(traveller), "Time stops for them");
            helper.succeed();
        } finally { remove(traveller, hurt); }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 120, batch = "PerilGameTests.fall", skyAccess = true)
    public static void aTelekineticCatchesAFall(GameTestHelper helper) {
        ServerPlayer sparker = player(helper, "sparker", 2, 2), catcher = player(helper, "catcher", 4, 2),
                faller = player(helper, "faller", 9, 4);
        try {
            helper.assertTrue(PlayerAbilityService.assignDormantAbility(sparker, ModAbilities.ELECTROKINESIS_ID), "Dormant electrokinesis assigned");
            helper.assertTrue(PlayerAbilityService.assignDormantAbility(catcher, ModAbilities.TELEKINESIS_ID), "Dormant telekinesis assigned");
            // Thirty blocks of fall behind them and the floor two blocks away.
            faller.setOnGround(false);
            faller.fallDistance = 30;
            faller.setDeltaMovement(0, -1.5, 0);
            helper.assertTrue(Danger.fallDamage(faller) >= faller.getHealth(), "The landing would kill");
            helper.assertTrue(!AwakeningService.answerAlly(sparker), "Lightning can't catch a fall: electrokinesis sleeps on");
            helper.assertTrue(AwakeningService.answerAlly(catcher), "A deadly fall wakes the telekinetic");
            helper.assertTrue(PowerIntroService.isActive(catcher), "The awakening plays its intro");
        } catch (RuntimeException failure) { remove(sparker, catcher, faller); throw failure; }
        helper.runAfterDelay(55, () -> {
            try {
                helper.assertTrue(faller.hasEffect(MobEffects.SLOW_FALLING), "The faller is caught and let down gently");
                helper.assertTrue(faller.fallDistance == 0, "The fall is forgotten");
                helper.succeed();
            } finally { remove(sparker, catcher, faller); }
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 120, batch = "PerilGameTests.lava")
    public static void aCryokineticFreezesTheLava(GameTestHelper helper) {
        ServerPlayer sparker = player(helper, "sparker", 2, 2), freezer = player(helper, "freezer", 4, 2),
                burning = player(helper, "burning", 9, 2);
        BlockPos pool = burning.blockPosition();
        try {
            helper.assertTrue(PlayerAbilityService.assignDormantAbility(sparker, ModAbilities.ELECTROKINESIS_ID), "Dormant electrokinesis assigned");
            helper.assertTrue(PlayerAbilityService.assignDormantAbility(freezer, ModAbilities.CRYOKINESIS_ID), "Dormant cryokinesis assigned");
            helper.getLevel().setBlock(pool.east(), Blocks.LAVA.defaultBlockState(), 3);
            helper.getLevel().setBlock(pool.west(), Blocks.LAVA.defaultBlockState(), 3);
            burning.setHealth(5);
            DamageSource lava = burning.damageSources().lava();
            // An electrokinetic's lightning is no use against lava.
            helper.assertTrue(Danger.of(lava, burning) == Danger.Peril.FIRE, "Lava is a fire danger");
            helper.assertTrue(!Danger.answers("electrokinesis", Danger.Peril.FIRE) && Danger.answers("cryokinesis", Danger.Peril.FIRE),
                    "Only some powers answer fire");
            float landed = AwakeningService.interceptAllyDamage(burning, lava, 20);
            helper.assertTrue(burning.getHealth() - landed >= 1, "The burn cannot kill");
            helper.assertTrue(AwakeningService.hasDormantPower(sparker), "Electrokinesis stays asleep");
            helper.assertTrue(!AwakeningService.hasDormantPower(freezer), "The burning wakes the cryokinetic");
        } catch (RuntimeException failure) { remove(sparker, freezer, burning); throw failure; }
        helper.runAfterDelay(75, () -> {
            try {
                helper.assertTrue(helper.getLevel().getBlockState(pool.east()).is(Blocks.OBSIDIAN)
                        && helper.getLevel().getBlockState(pool.west()).is(Blocks.OBSIDIAN), "The lava round them sets into obsidian");
                helper.assertTrue(!burning.isOnFire(), "The flames on them go out");
                helper.succeed();
            } finally { remove(sparker, freezer, burning); }
        });
    }
}
