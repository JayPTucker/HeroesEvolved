package com.jayptucker.heroesevolved.combat;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.cooldown.CooldownService;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.pyro.DragonCoil;
import com.jayptucker.heroesevolved.pyro.PyroService;
import com.jayptucker.heroesevolved.story.actor.StoryActorEntity;
import com.jayptucker.heroesevolved.telepathy.LightsOut;
import com.jayptucker.heroesevolved.time.ClosedLoop;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.UUID;

/** Finishers (combat/Takedowns): the threshold, the request's checks, breaking one, and the first three finishers. */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class TakedownGameTests {
    private static ServerPlayer finisher(GameTestHelper h, ResourceLocation power, int level) {
        ServerPlayer p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "finisher-test")) {
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
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(5, 2, 2))));
        p.setYRot(0);
        p.setXRot(0);
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(power).unlock(power));
        PlayerProgressionService.setPowerLevel(p, level);
        PlayerEnergyService.restore(p, PlayerEnergyService.getMaximumEnergy(p));
        Takedowns.resetCooldown(p);
        return p;
    }

    /** A pillager {@code ahead} blocks in front (and {@code across} to the side), brought down to {@code health}. */
    private static Mob foe(GameTestHelper h, ServerPlayer p, double ahead, double across, float health) {
        Mob foe = EntityType.PILLAGER.create(h.getLevel());
        foe.setNoAi(true);
        foe.setNoGravity(true);
        foe.setPersistenceRequired();
        foe.moveTo(p.getX() + across, p.getY(), p.getZ() + ahead, 180, 0);
        foe.setHealth(health);
        h.getLevel().addFreshEntity(foe);
        return foe;
    }

    private static void later(GameTestHelper h, ServerPlayer p, int ticks, Runnable step) {
        h.runAfterDelay(ticks, () -> {
            try { step.run(); }
            catch (RuntimeException | AssertionError e) { tidy(h, p); throw e; }
        });
    }

    private static void guarded(GameTestHelper h, ServerPlayer p, Runnable step) {
        try { step.run(); }
        catch (RuntimeException | AssertionError e) { tidy(h, p); throw e; }
    }

    private static void tidy(GameTestHelper h, ServerPlayer p) {
        Takedowns.Run run = Takedowns.run(p);
        if (run != null) Takedowns.end(run, true);
        FightingStyles.forget(p);
        for (StoryActorEntity actor : h.getLevel().getEntitiesOfClass(StoryActorEntity.class, new AABB(p.blockPosition()).inflate(16)))
            actor.discard();
    }

    @GameTest(template = "rewind_empty", batch = "finishers.threshold")
    public static void aFoeCanBeFinishedOnlyWhenLowAndNeverABoss(GameTestHelper h) {
        ServerPlayer p = finisher(h, ModAbilities.PYROKINESIS_ID, 3);
        Mob foe = foe(h, p, 2.5, 0, 20);
        guarded(h, p, () -> {
            float threshold = Takedowns.threshold(foe);
            h.assertTrue(Math.abs(threshold - 4.8F) < .01F, "A pillager (24 health) glistens at a fifth: " + threshold);
            h.assertFalse(Takedowns.finishable(p, foe), "Not while it has 20 health");
            h.assertFalse(Takedowns.request(p, foe.getId()), "The key does nothing on a healthy foe");
            foe.setHealth(4.5F);
            h.assertTrue(Takedowns.finishable(p, foe), "At 4.5 health it can be finished");
            var warden = EntityType.WARDEN.create(h.getLevel());
            warden.setHealth(2);
            h.assertFalse(Takedowns.looksFinishable(p, warden, false), "A boss never glistens");
            var villager = EntityType.VILLAGER.create(h.getLevel());
            villager.setHealth(1);
            h.assertFalse(Takedowns.looksFinishable(p, villager, false), "Nor does a villager");
            PlayerProgressionService.setPowerLevel(p, 2);
            h.assertTrue(Takedowns.of(p) == null, "Finishers open at Level " + Takedowns.UNLOCK_LEVEL);
            PlayerProgressionService.setPowerLevel(p, 3);
            h.assertTrue(Takedowns.of(p) == DragonCoil.FINISHER, "A pyrokinetic's finisher is Dragon Coil");
            Takedowns.toggle(p);
            h.assertFalse(Takedowns.request(p, foe.getId()), "Switched off, the key does nothing");
            Takedowns.toggle(p);
        });
        tidy(h, p);
        foe.discard();
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "finishers.dragon")
    public static void dragonCoilFinishesSpillsAndRewards(GameTestHelper h) {
        ServerPlayer p = finisher(h, ModAbilities.PYROKINESIS_ID, 3);
        Mob foe = foe(h, p, 2.5, 0, 4);
        Mob near = foe(h, p, 3.5, 1.5, 24);
        double stamina = PlayerEnergyService.getStamina(p);
        guarded(h, p, () -> {
            h.assertTrue(Takedowns.request(p, foe.getId(), DragonCoil.FINISHER), "C on a glistening foe starts Dragon Coil");
            h.assertTrue(PlayerEnergyService.getStamina(p) < stamina, "It costs stamina");
            h.assertTrue(Takedowns.running(p), "It is playing");
            Mob other = foe(h, p, 2.5, -1.5, 3);
            h.assertFalse(Takedowns.request(p, other.getId()), "One finisher at a time");
            other.discard();
        });
        later(h, p, DragonCoil.BITE - 1, () -> h.assertTrue(foe.isAlive(), "Nothing lands before the bite"));
        later(h, p, DragonCoil.TICKS + 2, () -> {
            h.assertFalse(foe.isAlive(), "The bite finishes it");
            h.assertTrue(near.getHealth() < 24, "The overkill spills onto the foe beside it: " + near.getHealth());
            h.assertTrue(PyroService.heat(p) >= DragonCoil.HEAT_REWARD, "It gives Heat");
            h.assertFalse(Takedowns.running(p), "And it is over");
            Mob next = foe(h, p, 2.5, 0, 3);
            h.assertFalse(Takedowns.request(p, next.getId()), "The shared cooldown holds");
            next.discard();
            tidy(h, p);
            near.discard();
            h.succeed();
        });
    }

    @GameTest(template = "rewind_empty", batch = "finishers.broken")
    public static void aHeavyBlowBreaksAFinisherAndLightOnesAreHalved(GameTestHelper h) {
        ServerPlayer p = finisher(h, ModAbilities.PYROKINESIS_ID, 3);
        Mob foe = foe(h, p, 2.5, 0, 4);
        Mob attacker = foe(h, p, -3, 0, 24);
        guarded(h, p, () -> h.assertTrue(Takedowns.request(p, foe.getId(), DragonCoil.FINISHER), "Dragon Coil starts"));
        later(h, p, 3, () -> {
            float before = p.getHealth();
            p.hurt(p.damageSources().mobAttack(foe), 5);
            h.assertTrue(p.getHealth() == before, "The held foe can't hurt you");
            // Generic blows: a mob's own would be scaled by the test server's difficulty.
            p.invulnerableTime = 0;
            p.hurt(p.damageSources().generic(), 4);
            h.assertTrue(Math.abs(before - p.getHealth() - 2) < .01F, "A light blow is halved: " + (before - p.getHealth()));
            h.assertTrue(Takedowns.running(p), "And doesn't break it");
            p.invulnerableTime = 0;
            p.hurt(p.damageSources().generic(), 8);
            h.assertFalse(Takedowns.running(p), "A heavy blow breaks it");
        });
        later(h, p, DragonCoil.TICKS + 2, () -> {
            h.assertTrue(foe.isAlive(), "A broken finisher never lands");
            tidy(h, p);
            foe.discard();
            attacker.discard();
            h.succeed();
        });
    }

    @GameTest(template = "rewind_empty", batch = "finishers.loop")
    public static void closedLoopStrikesWithALaterSelfAndGivesFoldSpaceBack(GameTestHelper h) {
        ServerPlayer p = finisher(h, ModAbilities.TIME_MANIPULATION_ID, 3);
        Mob foe = foe(h, p, 2.5, 0, 4);
        ResourceLocation fold = ResourceLocation.fromNamespaceAndPath("heroesevolved", "time_blink");
        CooldownService.startCooldown(p, fold, 400);
        guarded(h, p, () -> h.assertTrue(Takedowns.request(p, foe.getId(), ClosedLoop.FINISHER), "C starts Closed Loop"));
        later(h, p, ClosedLoop.ARRIVE + 1, () -> h.assertTrue(
                !h.getLevel().getEntitiesOfClass(StoryActorEntity.class, foe.getBoundingBox().inflate(4)).isEmpty(),
                "Your later self folds in beside the foe"));
        later(h, p, ClosedLoop.TICKS + 2, () -> {
            h.assertFalse(foe.isAlive(), "The two cuts finish it");
            h.assertTrue(h.getLevel().getEntitiesOfClass(StoryActorEntity.class, foe.getBoundingBox().inflate(6)).isEmpty(),
                    "The later self has gone back");
            h.assertFalse(CooldownService.isOnCooldown(p, fold), "Fold Space is ready again");
            tidy(h, p);
            h.succeed();
        });
    }

    /**
     * Every finisher, one after another, each by a fresh player of its power on a fresh low pillager: the key starts it
     * and it kills. Whatever a finisher moves or spawns (the player, a double, gravity) is tidied before the next.
     */
    @GameTest(template = "rewind_empty", batch = "finishers.all", timeoutTicks = 2700)
    public static void everyFinisherFinishesALowPillager(GameTestHelper h) {
        var all = Takedowns.all();
        int step = 40;
        for (int i = 0; i < all.size(); i++) {
            Takedowns.Finisher f = all.get(i);
            int at = 1 + i * step;
            h.runAfterDelay(at, () -> {
                ServerPlayer p = finisher(h, f.power(), 3);
                Mob foe = foe(h, p, f.reach() > Takedowns.REACH ? 4 : 2.5, 0, 3);
                try {
                    h.assertTrue(Takedowns.variants(Takedowns.powerOf(p)).contains(f), f.id() + " is one of " + f.power().getPath() + "'s finishers");
                    h.assertTrue(Takedowns.request(p, foe.getId(), f), f.id() + " starts");
                } catch (RuntimeException | AssertionError e) { tidy(h, p); throw e; }
                h.runAfterDelay(f.ticks() + 3, () -> {
                    try {
                        h.assertFalse(foe.isAlive(), f.id() + " kills");
                        h.assertFalse(Takedowns.running(p), f.id() + " has ended");
                    } finally {
                        tidy(h, p);
                        p.setNoGravity(false);
                        foe.discard();
                    }
                });
            });
        }
        h.runAfterDelay(1 + all.size() * step + 2, h::succeed);
    }

    /** Each power has three finishers (Shapeshifting and the rest alike), and the key never picks the same one twice running. */
    @GameTest(template = "rewind_empty", batch = "finishers.pick", timeoutTicks = 400)
    public static void theKeyPicksADifferentFinisherEachTime(GameTestHelper h) {
        for (Takedowns.Finisher f : Takedowns.all())
            if (Takedowns.forPower(f.power()) == f)
                h.assertTrue(Takedowns.variants(f.power()).size() == 3, f.power().getPath() + " has three finishers");
        ServerPlayer p = finisher(h, ModAbilities.PYROKINESIS_ID, 3);
        String[] picked = new String[3];
        for (int i = 0; i < 3; i++) {
            int round = i;
            later(h, p, 1 + i * 45, () -> {
                Takedowns.resetCooldown(p);
                PlayerEnergyService.restore(p, PlayerEnergyService.getMaximumEnergy(p));
                Mob foe = foe(h, p, 2.5, 0, 3);
                h.assertTrue(Takedowns.request(p, foe.getId()), "Round " + round + " starts");
                picked[round] = Takedowns.run(p).finisher.id();
                if (round > 0) h.assertFalse(picked[round].equals(picked[round - 1]),
                        "Never the same twice running: " + picked[round - 1] + " then " + picked[round]);
            });
        }
        later(h, p, 1 + 3 * 45, () -> {
            tidy(h, p);
            p.setNoGravity(false);
            h.succeed();
        });
    }

    /** Borrowed Strength only comes up once an empath has kept a power with a finisher; with none it is never picked. */
    @GameTest(template = "rewind_empty", batch = "finishers.borrowed")
    public static void borrowedStrengthNeedsAKeptPower(GameTestHelper h) {
        ServerPlayer p = finisher(h, ModAbilities.MIMICRY_ID, 3);
        Mob foe = foe(h, p, 2.5, 0, 3);
        guarded(h, p, () -> {
            h.assertFalse(com.jayptucker.heroesevolved.mimicry.MimicFinishers.BORROWED_STRENGTH.available(p),
                    "Nothing kept, nothing to borrow");
            for (int i = 0; i < 12; i++) {
                Takedowns.Finisher pick = Takedowns.choose(p, foe);
                h.assertTrue(pick != com.jayptucker.heroesevolved.mimicry.MimicFinishers.BORROWED_STRENGTH,
                        "Borrowed Strength isn't picked with nothing kept");
            }
        });
        tidy(h, p);
        foe.discard();
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "finishers.lights")
    public static void lightsOutFinishesFromAfarAndHushesTheRoom(GameTestHelper h) {
        ServerPlayer p = finisher(h, ModAbilities.TELEPATHY_ID, 3);
        Mob foe = foe(h, p, 5, 0, 4);
        Mob near = foe(h, p, 6.5, 1.5, 24);
        near.setTarget(p);
        guarded(h, p, () -> {
            h.assertTrue(Takedowns.inReach(p, foe, Takedowns.RANGED_REACH), "Five blocks is in a telepath's reach");
            h.assertFalse(Takedowns.inReach(p, foe, Takedowns.REACH), "and out of anyone else's");
            h.assertTrue(Takedowns.request(p, foe.getId(), LightsOut.FINISHER), "C starts Lights Out, no touch needed");
        });
        later(h, p, LightsOut.SHAKE + 1, () -> {
            h.assertTrue(foe.isAlive(), "Nothing lands before the lights go out");
            h.assertTrue(near.getTarget() == null, "The hush makes the foe beside it forget you");
        });
        later(h, p, LightsOut.TICKS + 2, () -> {
            h.assertFalse(foe.isAlive(), "Lights out");
            h.assertTrue(near.getHealth() < 24, "The overkill spills onto the foe beside it: " + near.getHealth());
            tidy(h, p);
            near.discard();
            h.succeed();
        });
    }
}
