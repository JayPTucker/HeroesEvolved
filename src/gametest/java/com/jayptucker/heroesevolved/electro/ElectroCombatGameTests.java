package com.jayptucker.heroesevolved.electro;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.combat.FightingStyles;
import com.jayptucker.heroesevolved.combat.FightingStyles.Dodge;
import com.jayptucker.heroesevolved.combat.StyleMotion;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.hydro.HydroService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.UUID;

/** Electrokinesis's fighting style: sparks, the Thunderclap, Grounding Strike, Arc Step, Flashback and Ground Out. */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class ElectroCombatGameTests {
    private static ServerPlayer fighter(GameTestHelper h, int level) {
        ServerPlayer p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "lightning-test")) {
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
                .assignDormant(ModAbilities.ELECTROKINESIS_ID).unlock(ModAbilities.ELECTROKINESIS_ID));
        PlayerProgressionService.setLevel(p, level);
        PlayerEnergyService.restore(p, PlayerEnergyService.getMaximumEnergy(p));
        return p;
    }

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

    private static void forget(ServerPlayer p) {
        ElectroService.forget(p);
        FightingStyles.forget(p);
    }

    private static void later(GameTestHelper h, ServerPlayer p, int ticks, Runnable step) {
        h.runAfterDelay(ticks, () -> {
            try { step.run(); }
            catch (RuntimeException | AssertionError e) { forget(p); throw e; }
        });
    }

    @GameTest(template = "rewind_empty", batch = "electro.style_combo")
    public static void sparksAlternateThenTheThunderclap(GameTestHelper h) {
        ServerPlayer p = fighter(h, 2);
        LivingEntity foe = foe(h, p, 4, 0);
        aim(p, foe);
        try {
            h.assertTrue(FightingStyles.of(p) == ElectroCombat.STYLE, "An electrokinetic fights with the lightning style");
            h.assertTrue(ElectroCombat.next(p) == ElectroCombat.Strike.JAB, "A fresh combo opens with a spark jab");
            float before = foe.getHealth();
            h.assertTrue(ElectroCombat.strike(p) == 1, "An empty-handed click throws a spark");
            h.assertTrue(foe.getHealth() < before, "The spark lands");
            h.assertTrue(ElectroCombat.strike(p) == 0, "A second click straight away is too soon");
            h.assertTrue(ElectroCombat.next(p) == ElectroCombat.Strike.CROSS, "The rear fist follows");
        } catch (RuntimeException | AssertionError e) { forget(p); throw e; }
        later(h, p, ElectroCombat.JAB_GAP + 1, () -> {
            h.assertTrue(ElectroCombat.strike(p) == 1, "The cross is thrown inside the combo window");
            h.assertTrue(ElectroCombat.next(p) == ElectroCombat.Strike.CLAP, "From level 2 the combo ends in a Thunderclap");
        });
        later(h, p, 2 * ElectroCombat.JAB_GAP + 2, () -> {
            float before = foe.getHealth();
            foe.setDeltaMovement(Vec3.ZERO);
            h.assertTrue(ElectroCombat.strike(p) == 1, "The clap is thrown");
            h.assertTrue(foe.getHealth() < before, "The clap's fan hits the foe in front");
            h.assertTrue(foe.getDeltaMovement().z > .3, "The clap throws the foe back, " + foe.getDeltaMovement());
            h.assertTrue(ElectroCombat.next(p) == ElectroCombat.Strike.JAB, "After the clap the combo starts again");
            forget(p);
            foe.discard();
            h.succeed();
        });
    }

    @GameTest(template = "rewind_empty", batch = "electro.style_seek")
    public static void aSparkSeeksTheNearestBodyNearTheAim(GameTestHelper h) {
        ServerPlayer p = fighter(h, 1);
        // Straight ahead is empty; the foe stands a block off the line.
        LivingEntity foe = foe(h, p, 5, 1);
        try {
            float before = foe.getHealth();
            h.assertTrue(ElectroCombat.strike(p) == 1, "A spark");
            h.assertTrue(foe.getHealth() < before, "Lightning finds the foe a block off the aim");
            forget(p);
            foe.discard();
            h.succeed();
        } catch (RuntimeException | AssertionError e) { forget(p); throw e; }
    }

    @GameTest(template = "rewind_empty", batch = "electro.style_ground")
    public static void crouchingGroundsARingOfCurrent(GameTestHelper h) {
        ServerPlayer p = fighter(h, 2);
        LivingEntity behind = foe(h, p, -2.5, 0), beside = foe(h, p, 0, 2.5);
        p.setPose(Pose.CROUCHING);
        p.setShiftKeyDown(true);
        try {
            h.assertTrue(ElectroCombat.next(p) == ElectroCombat.Strike.GROUND, "Crouching turns the click into a Grounding Strike");
            float a = behind.getHealth(), b = beside.getHealth();
            h.assertTrue(ElectroCombat.strike(p) == 1, "The palm goes into the floor");
            h.assertTrue(behind.getHealth() < a && beside.getHealth() < b, "The ring reaches all round, behind as well");
            forget(p);
            behind.discard();
            beside.discard();
            h.succeed();
        } catch (RuntimeException | AssertionError e) { forget(p); throw e; }
    }

    @GameTest(template = "rewind_empty", batch = "electro.style_ground_out")
    public static void anArcStepGroundsOutABlowAndTheNextSparkChains(GameTestHelper h) {
        ServerPlayer p = fighter(h, 2);
        LivingEntity attacker = foe(h, p, 4, 0), friend = foe(h, p, 6, 2);
        aim(p, attacker);
        var source = p.damageSources().mobAttack(attacker);
        try {
            float health = p.getHealth();
            h.assertTrue(ElectroCombat.dodge(p, Dodge.LEFT), "An Arc Step");
            h.assertTrue(ElectroCombat.isDodging(p) && StyleMotion.evading(p), "The step has its evasion window");
            h.assertTrue(p.getDeltaMovement().x > 1, "The step flashes sideways, " + p.getDeltaMovement());
            h.assertTrue(!ElectroCombat.dodge(p, Dodge.RIGHT), "Dodges have a cooldown");
            p.hurt(source, 6);
            h.assertTrue(p.getHealth() == health, "A blow during the flash misses");
            h.assertTrue(ElectroCombat.isLoaded(p), "The missed blow grounds out and loads the next spark");
            h.assertTrue(ElectroCombat.next(p) == ElectroCombat.Strike.CROSS, "The loaded rear fist is next");
            float a = attacker.getHealth(), b = friend.getHealth();
            h.assertTrue(ElectroCombat.strike(p) == 1, "The chained spark is thrown");
            h.assertTrue(attacker.getHealth() < a, "It arcs through the attacker");
            h.assertTrue(friend.getHealth() < b, "And leaps on to the foe beside them");
            h.assertTrue(!ElectroCombat.isLoaded(p), "Throwing it spends the charge");
            forget(p);
            attacker.discard();
            friend.discard();
            h.succeed();
        } catch (RuntimeException | AssertionError e) { forget(p); throw e; }
    }

    @GameTest(template = "rewind_empty", batch = "electro.style_flashback")
    public static void aFlashbackLeavesStaticThatBursts(GameTestHelper h) {
        ServerPlayer p = fighter(h, 2);
        LivingEntity foe = foe(h, p, 1.2, 0);
        try {
            h.assertTrue(ElectroCombat.dodge(p, Dodge.BACK), "A Flashback");
            h.assertTrue(p.getDeltaMovement().z < -1, "It snaps back, " + p.getDeltaMovement());
            h.assertTrue(ElectroCombat.pendingDecoys(p) == 1, "It leaves static where it began");
        } catch (RuntimeException | AssertionError e) { forget(p); throw e; }
        float before = foe.getHealth();
        later(h, p, ElectroGeometry.FLASHBACK_FUSE + 2, () -> {
            h.assertTrue(ElectroCombat.pendingDecoys(p) == 0, "The static has burst");
            h.assertTrue(foe.getHealth() < before, "Whoever stood in it is jolted");
            forget(p);
            foe.discard();
            h.succeed();
        });
    }

    @GameTest(template = "rewind_empty", batch = "electro.style_soaked")
    public static void lightningHitsTheSoakedHarder(GameTestHelper h) {
        ServerPlayer p = fighter(h, 1);
        LivingEntity dry = foe(h, p, 4, -2), wet = foe(h, p, 4, 2);
        try {
            HydroService.soak(wet, 200);
            float a = dry.getHealth(), b = wet.getHealth();
            ElectroService.shock(p, dry, 4);
            ElectroService.shock(p, wet, 4);
            float onDry = a - dry.getHealth(), onWet = b - wet.getHealth();
            h.assertTrue(onWet >= onDry * 1.45F, "A soaked foe takes half again as much from lightning, " + onDry + " vs " + onWet);
            forget(p);
            dry.discard();
            wet.discard();
            h.succeed();
        } catch (RuntimeException | AssertionError e) { forget(p); throw e; }
    }

    @GameTest(template = "rewind_empty", batch = "electro.style_tired")
    public static void noStaminaNoSparks(GameTestHelper h) {
        ServerPlayer p = fighter(h, 2);
        try {
            PlayerEnergyService.consumeUpTo(p, PlayerEnergyService.getMaximumEnergy(p));
            h.assertTrue(ElectroCombat.strike(p) == -1, "No stamina, no spark (the click punches instead)");
            h.assertTrue(!ElectroCombat.dodge(p, Dodge.LEFT), "No stamina, no Arc Step");
            h.succeed();
        } finally { forget(p); }
    }
}
