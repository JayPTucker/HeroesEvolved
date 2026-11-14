package com.jayptucker.heroesevolved.pyro;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.UUID;

/** Firebending: strikes from the fists and feet, the combo, jet dodges and Counterflame. */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class PyroCombatGameTests {
    private static ServerPlayer fighter(GameTestHelper h, int level) {
        ServerPlayer p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "firebender-test")) {
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
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(5, 2, 5))));
        p.setYRot(0);
        p.setXRot(0);
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty()
                .assignDormant(ModAbilities.PYROKINESIS_ID).unlock(ModAbilities.PYROKINESIS_ID));
        PlayerProgressionService.setLevel(p, level);
        PlayerEnergyService.restore(p, PlayerEnergyService.getMaximumEnergy(p));
        return p;
    }

    /** A pillager standing on the floor {@code ahead} blocks in front (+Z) and {@code across} to the side. */
    private static LivingEntity foe(GameTestHelper h, ServerPlayer p, double ahead, double across) {
        var foe = EntityType.PILLAGER.create(h.getLevel());
        foe.setNoAi(true);
        foe.setNoGravity(true);
        foe.setPersistenceRequired();
        foe.moveTo(p.getX() + across, p.getY(), p.getZ() + ahead, 180, 0);
        h.getLevel().addFreshEntity(foe);
        return foe;
    }

    private static void aim(ServerPlayer p, LivingEntity at) {
        p.lookAt(EntityAnchorArgument.Anchor.EYES, at.getBoundingBox().getCenter());
    }

    /** Runs a step later; a failure lets the fighter go first, so they never outlive the test holding their power. */
    private static void later(GameTestHelper h, ServerPlayer p, int ticks, Runnable step) {
        h.runAfterDelay(ticks, () -> {
            try { step.run(); }
            catch (RuntimeException | AssertionError e) { PyroService.forget(p); throw e; }
        });
    }

    @GameTest(template = "rewind_empty", batch = "pyro.firebending_combo")
    public static void fistsAlternateThenTheRoundhouseKicks(GameTestHelper h) {
        ServerPlayer p = fighter(h, 2);
        LivingEntity foe = foe(h, p, 4, 0);
        aim(p, foe);
        try {
            h.assertTrue(PyroCombat.next(p) == PyroCombat.Strike.JAB, "A fresh combo opens with the jab");
            float full = foe.getHealth();
            h.assertTrue(PyroCombat.strike(p) == 1, "An empty-handed click throws fire");
            h.assertTrue(foe.getHealth() < full && foe.isOnFire(), "The jab's fist of fire burns the foe");
            h.assertTrue(PyroCombat.strike(p) == 0, "A second click straight away is too soon");
            h.assertTrue(PyroCombat.next(p) == PyroCombat.Strike.CROSS, "The rear fist follows the jab");
        } catch (RuntimeException | AssertionError e) { PyroService.forget(p); throw e; }
        later(h, p, PyroCombat.JAB_GAP + 1, () -> {
            float before = foe.getHealth();
            h.assertTrue(PyroCombat.strike(p) == 1, "The cross is thrown inside the combo window");
            h.assertTrue(foe.getHealth() < before, "The cross lands");
            h.assertTrue(PyroCombat.next(p) == PyroCombat.Strike.ROUNDHOUSE, "From level 2 the combo ends in a kick");
        });
        later(h, p, 2 * PyroCombat.JAB_GAP + 2, () -> {
            float before = foe.getHealth();
            foe.setDeltaMovement(Vec3.ZERO);
            h.assertTrue(PyroCombat.strike(p) == 1, "The roundhouse is thrown");
            h.assertTrue(foe.getHealth() < before, "The kicked crescent burns the foe");
            h.assertTrue(foe.getDeltaMovement().z > .3, "The crescent throws the foe away");
            h.assertTrue(PyroCombat.next(p) == PyroCombat.Strike.JAB, "After the kick the combo starts again");
            PyroService.forget(p);
            h.succeed();
        });
    }

    @GameTest(template = "rewind_empty", batch = "pyro.firebending_novice")
    public static void levelOneLoopsTheFistsAndNeedsAnEmptyHand(GameTestHelper h) {
        ServerPlayer p = fighter(h, 1);
        try {
            h.assertTrue(PyroCombat.strike(p) == 1, "A jab");
            p.setPose(Pose.CROUCHING);
            h.assertTrue(PyroCombat.next(p) == PyroCombat.Strike.CROSS, "Crouching doesn't sweep before level 2");
            p.setPose(Pose.STANDING);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE_SWORD));
            h.assertTrue(PyroCombat.strike(p) == -1, "A held weapon fights the ordinary way");
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        } catch (RuntimeException | AssertionError e) { PyroService.forget(p); throw e; }
        later(h, p, PyroCombat.JAB_GAP + 1, () -> {
            h.assertTrue(PyroCombat.strike(p) == 1, "A cross");
            h.assertTrue(PyroCombat.next(p) == PyroCombat.Strike.JAB, "Without kicks the fists simply alternate");
            PyroService.forget(p);
            h.succeed();
        });
    }

    @GameTest(template = "rewind_empty", batch = "pyro.firebending_sweep")
    public static void crouchingSweepTripsEveryoneRoundAndFansTheFirestorm(GameTestHelper h) {
        ServerPlayer p = fighter(h, 2);
        LivingEntity front = foe(h, p, 2.5, 0), behind = foe(h, p, -2.5, 0), far = foe(h, p, 0, 6);
        try {
            h.assertTrue(PyroService.ringOfFire(p, 2), "A Firestorm to fan");
        } catch (RuntimeException | AssertionError e) { PyroService.forget(p); throw e; }
        later(h, p, PyroGeometry.STORM_WARNING + 2, () -> {
            float frontHealth = front.getHealth(), behindHealth = behind.getHealth(), farHealth = far.getHealth();
            p.setPose(Pose.CROUCHING);
            h.assertTrue(PyroCombat.next(p) == PyroCombat.Strike.SWEEP, "Crouching turns the click into a sweep kick");
            front.setDeltaMovement(Vec3.ZERO);
            behind.setDeltaMovement(Vec3.ZERO);
            h.assertTrue(PyroCombat.strike(p) == 1, "The sweep is thrown");
            h.assertTrue(front.getHealth() < frontHealth && behind.getHealth() < behindHealth,
                    "The ring of fire reaches in front and behind");
            h.assertTrue(front.getDeltaMovement().y > .3 && behind.getDeltaMovement().y > .3, "Both are tripped up");
            h.assertTrue(far.getHealth() == farHealth, "Nothing beyond the ring is touched");
            h.assertTrue(PyroService.isFed(p), "A kick through your own Firestorm fans it");
            PyroService.forget(p);
            h.succeed();
        });
    }

    @GameTest(template = "rewind_empty", batch = "pyro.firebending_dodge")
    public static void aDodgedBlowLoadsACounterflame(GameTestHelper h) {
        ServerPlayer p = fighter(h, 2);
        LivingEntity foe = foe(h, p, 4, 0);
        aim(p, foe);
        var source = p.damageSources().mobAttack(foe);
        try {
            float health = p.getHealth();
            h.assertTrue(PyroCombat.dodge(p, PyroCombat.Dodge.LEFT), "A jet step");
            h.assertTrue(PyroCombat.isDodging(p) && PyroCombat.isEvading(p), "The step has its evasion window");
            h.assertTrue(!PyroCombat.dodge(p, PyroCombat.Dodge.RIGHT), "Dodges have a cooldown");
            p.hurt(source, 6);
            h.assertTrue(p.getHealth() == health, "A blow during the step misses");
            h.assertTrue(PyroCombat.isLoaded(p) && PyroService.heat(p) >= PyroCombat.COUNTER_HEAT,
                    "The dodged blow loads a Counterflame and Heat");
            h.assertTrue(PyroCombat.next(p) == PyroCombat.Strike.CROSS, "The loaded rear fist is next");
            float full = foe.getHealth();
            h.assertTrue(PyroCombat.strike(p) == 1, "The counter is thrown");
            float dealt = full - foe.getHealth();
            h.assertTrue(dealt >= (2 + .4F * 2) * 2, "A Counterflame cross is a fireball, " + dealt);
            h.assertTrue(!PyroCombat.isLoaded(p), "Throwing it spends the Counterflame");
        } catch (RuntimeException | AssertionError e) { PyroService.forget(p); throw e; }
        later(h, p, PyroCombat.EVADE_TICKS + 2, () -> {
            float health = p.getHealth();
            p.invulnerableTime = 0;
            p.hurt(source, 4);
            h.assertTrue(p.getHealth() < health, "Once the window closes, blows land again");
            PyroService.forget(p);
            h.succeed();
        });
    }

    @GameTest(template = "rewind_empty", batch = "pyro.firebending_flip")
    public static void theBackflipKicksAnArcUpThroughTheFoe(GameTestHelper h) {
        ServerPlayer p = fighter(h, 2);
        LivingEntity foe = foe(h, p, 2.5, 0);
        try {
            float before = foe.getHealth();
            foe.setDeltaMovement(Vec3.ZERO);
            h.assertTrue(PyroCombat.dodge(p, PyroCombat.Dodge.BACK), "A backflip");
            h.assertTrue(foe.getHealth() < before, "The flip kick's arc burns the foe in front");
            h.assertTrue(foe.getDeltaMovement().y > .5, "The arc launches the foe upward");
            h.assertTrue(p.getDeltaMovement().z < 0 && p.getDeltaMovement().y > .3, "The fighter flips up and back");
            h.assertTrue(PyroCombat.cushion(p), "The landing is cushioned");
        } catch (RuntimeException | AssertionError e) { PyroService.forget(p); throw e; }
        later(h, p, PyroGeometry.FLIP_SPIN + 1, () -> {
            h.assertTrue(!PyroCombat.isDodging(p), "The flip ends");
            PyroService.forget(p);
            h.succeed();
        });
    }

    @GameTest(template = "rewind_empty", batch = "pyro.firebending_tired")
    public static void noStaminaNoFire(GameTestHelper h) {
        ServerPlayer p = fighter(h, 2);
        try {
            PlayerEnergyService.consumeUpTo(p, PlayerEnergyService.getMaximumEnergy(p));
            h.assertTrue(PyroCombat.strike(p) == -1, "No stamina, no fist of fire (the click punches instead)");
            h.assertTrue(!PyroCombat.dodge(p, PyroCombat.Dodge.LEFT), "No stamina, no jet step");
            h.succeed();
        } finally { PyroService.forget(p); }
    }
}
