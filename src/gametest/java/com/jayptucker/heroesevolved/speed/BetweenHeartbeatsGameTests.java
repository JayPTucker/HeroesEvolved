package com.jayptucker.heroesevolved.speed;

import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.combat.Ultimates;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.shapeshift.ShapeDecoy;
import com.jayptucker.heroesevolved.styles.StyleOps;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

/**
 * Between Heartbeats (Superspeed's Defense Ultimate): the speedster splits into three illusions of themself, which fight
 * the enemies within 32 blocks, draw their anger, leave everyone else alone, take three blows each, and are gone when it
 * stops.
 */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class BetweenHeartbeatsGameTests {
    private static final String MECHANIC = "phase_blur";

    private static ServerPlayer speedster(GameTestHelper h) {
        ServerPlayer p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "heartbeat-test")) {
            @Override public boolean isInvulnerableTo(DamageSource damage) { return false; }
        };
        try {
            Connection connection = p.connection.getConnection();
            Field channel = Connection.class.getDeclaredField("channel");
            channel.setAccessible(true);
            if (connection.channel() == null) channel.set(connection, new EmbeddedChannel());
            Field grace = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            grace.setAccessible(true);
            grace.setInt(p, 0);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(5, 2, 3))));
        p.setYRot(0);
        p.setXRot(0);
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty()
                .assignDormant(ModAbilities.SUPERSPEED_ID).unlock(ModAbilities.SUPERSPEED_ID));
        PlayerProgressionService.setLevel(p, 5);
        PlayerEnergyService.restore(p, PlayerEnergyService.getMaximumEnergy(p));
        return p;
    }

    private static <T extends Mob> T mob(GameTestHelper h, EntityType<T> type, ServerPlayer p, double dx, double dz, boolean ai) {
        T mob = type.create(h.getLevel());
        mob.setNoAi(!ai);
        mob.setPersistenceRequired();
        var health = mob.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) health.setBaseValue(200);
        mob.setHealth(200);
        mob.moveTo(p.getX() + dx, p.getY(), p.getZ() + dz, 180, 0);
        h.getLevel().addFreshEntity(mob);
        return mob;
    }

    private static SpeedUltimates.BetweenHeartbeats cast(GameTestHelper h, ServerPlayer p) {
        AbilityUseContext c = new AbilityUseContext(p, ModAbilities.SUPERSPEED_ID, 5, false);
        h.assertTrue(Ultimates.canUse(c, MECHANIC) && Ultimates.cast(c, MECHANIC), "Between Heartbeats starts");
        if (!(Ultimates.run(p) instanceof SpeedUltimates.BetweenHeartbeats run)) throw new AssertionError("Between Heartbeats is running");
        return run;
    }

    private static void step(GameTestHelper h, ServerPlayer p, int ticks, Runnable check) {
        h.runAfterDelay(ticks, () -> {
            try { check.run(); }
            catch (RuntimeException | AssertionError e) { Ultimates.stop(p); throw e; }
        });
    }

    @GameTest(template = "rewind_empty", batch = "speed.heartbeats", timeoutTicks = 200)
    public static void threeIllusionsOfYouFightTheEnemiesAndLeaveTheRestAlone(GameTestHelper h) {
        ServerPlayer p = speedster(h);
        LivingEntity pillager = mob(h, EntityType.PILLAGER, p, 0, 10, false);
        LivingEntity cow = mob(h, EntityType.COW, p, 4, 0, false);
        var hunter = mob(h, EntityType.ZOMBIE, p, -3, 3, true);
        hunter.setTarget(p);
        SpeedUltimates.BetweenHeartbeats run = cast(h, p);
        step(h, p, SpeedUltimates.BetweenHeartbeats.SPLIT - 2, () ->
                h.assertTrue(run.illusions().isEmpty(), "The speedster vibrates in place before splitting"));
        step(h, p, SpeedUltimates.BetweenHeartbeats.SPLIT + 3, () -> {
            List<ShapeDecoy> illusions = run.illusions();
            h.assertTrue(illusions.size() == SpeedUltimates.BetweenHeartbeats.ILLUSIONS, "Three illusions tear loose, got " + illusions.size());
            for (ShapeDecoy illusion : illusions) {
                h.assertTrue(!illusion.isRemoved() && illusion.speedster() && illusion.driven(), "Each is a running copy the run drives");
                h.assertTrue(p.getUUID().equals(illusion.owner()) && illusion.getOwner() == p, "Each is the speedster's own");
                h.assertFalse(StyleOps.isFoe(p, illusion), "Your own illusions are never your foes");
                h.assertTrue(illusion.form() != null && illusion.form().person(), "Each wears the speedster's own body");
            }
        });
        step(h, p, 34, () -> h.assertTrue(hunter.getTarget() instanceof ShapeDecoy,
                "A mob hunting the speedster turns on an illusion, got " + hunter.getTarget()));
        step(h, p, 40, () -> {
            float before = p.getHealth();
            p.invulnerableTime = 0;
            p.hurt(p.damageSources().generic(), 6);
            h.assertTrue(Math.abs(before - p.getHealth() - 3) < .01F, "Still vibrating, a 6-point blow lands as 3, took " + (before - p.getHealth()));
        });
        step(h, p, 110, () -> {
            h.assertTrue(pillager.getHealth() < 200, "The illusions fight an enemy 10 blocks away, it has " + pillager.getHealth());
            h.assertTrue(pillager.getLastHurtByMob() instanceof ShapeDecoy, "Its anger is drawn to the illusion that struck it");
            h.assertTrue(cow.getHealth() == 200, "A cow nobody is fighting is left alone");
            Ultimates.stop(p);
            h.assertFalse(Ultimates.running(p), "It stops when told to");
            h.assertTrue(run.illusions().stream().allMatch(ShapeDecoy::isRemoved), "Stopped early, every illusion is gone");
            pillager.discard();
            cow.discard();
            hunter.discard();
            h.succeed();
        });
    }

    @GameTest(template = "rewind_empty", batch = "speed.heartbeats", timeoutTicks = 100)
    public static void anIllusionStepsAsideTwiceAndBreaksOnTheThirdBlow(GameTestHelper h) {
        ServerPlayer p = speedster(h);
        LivingEntity striker = mob(h, EntityType.PILLAGER, p, 0, 20, false);
        SpeedUltimates.BetweenHeartbeats run = cast(h, p);
        ShapeDecoy[] target = new ShapeDecoy[1];
        int start = SpeedUltimates.BetweenHeartbeats.SPLIT + 4;
        step(h, p, start, () -> {
            target[0] = run.illusions().getFirst();
            target[0].hurt(h.getLevel().damageSources().mobAttack(striker), 5);
            h.assertTrue(!target[0].isRemoved() && run.blows().getFirst() == 2, "The first blow finds only an afterimage, " + run.blows());
        });
        step(h, p, start + 10, () -> {
            target[0].hurt(h.getLevel().damageSources().mobAttack(striker), 5);
            h.assertTrue(!target[0].isRemoved() && run.blows().getFirst() == 1, "So does the second, " + run.blows());
        });
        step(h, p, start + 20, () -> {
            target[0].hurt(h.getLevel().damageSources().mobAttack(striker), 5);
            h.assertTrue(target[0].isRemoved() && run.blows().getFirst() == 0, "The third breaks it");
            h.assertTrue(run.illusions().stream().filter(i -> !i.isRemoved()).count() == 2, "The other two fight on");
            Ultimates.stop(p);
            striker.discard();
            h.succeed();
        });
    }
}
