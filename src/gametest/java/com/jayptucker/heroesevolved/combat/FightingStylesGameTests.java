package com.jayptucker.heroesevolved.combat;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.combat.ComboStyle.Move;
import com.jayptucker.heroesevolved.combat.FightingStyles.Dodge;
import com.jayptucker.heroesevolved.cryo.CryoService;
import com.jayptucker.heroesevolved.cryo.CryoStyle;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.hydro.HydroService;
import com.jayptucker.heroesevolved.invisibility.ForceStyle;
import com.jayptucker.heroesevolved.mimicry.MimicryService;
import com.jayptucker.heroesevolved.phasing.DensityStyle;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.pyro.PyroCombat;
import com.jayptucker.heroesevolved.quake.QuakeKit;
import com.jayptucker.heroesevolved.quake.QuakeStyle;
import com.jayptucker.heroesevolved.regeneration.RegenStyle;
import com.jayptucker.heroesevolved.speed.SpeedStyle;
import com.jayptucker.heroesevolved.telekinesis.KineticStyle;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceLocation;
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

/** The shared-engine fighting styles: Superspeed, Quake, Cryokinesis, Density, Regeneration, Force and Telekinesis. */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class FightingStylesGameTests {
    private static ServerPlayer fighter(GameTestHelper h, ResourceLocation power, int level) {
        ServerPlayer p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "style-test")) {
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
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(power).unlock(power));
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

    private static void forget(ServerPlayer p) { FightingStyles.forget(p); }

    private static void later(GameTestHelper h, ServerPlayer p, int ticks, Runnable step) {
        h.runAfterDelay(ticks, () -> {
            try { step.run(); }
            catch (RuntimeException | AssertionError e) { forget(p); throw e; }
        });
    }

    private static void guarded(ServerPlayer p, Runnable step) {
        try { step.run(); }
        catch (RuntimeException | AssertionError e) { forget(p); throw e; }
    }

    /** Throws the whole combo (lead, rear, finisher) at a foe four blocks ahead, checking each lands. */
    private static void combo(GameTestHelper h, ResourceLocation power, ComboStyle style, Runnable after) {
        ServerPlayer p = fighter(h, power, 2);
        LivingEntity foe = foe(h, p, 4, 0);
        aim(p, foe);
        guarded(p, () -> {
            h.assertTrue(FightingStyles.of(p) == style, power.getPath() + " fights with its own style");
            h.assertTrue(style.next(p) == Move.LEAD, "A fresh combo opens with the lead hand");
            float before = foe.getHealth();
            h.assertTrue(style.strike(p) == 1, "An empty-handed click strikes");
            h.assertTrue(foe.getHealth() < before, power.getPath() + ": the lead strike lands");
            h.assertTrue(style.strike(p) == 0, "A second click at once is too soon");
            h.assertTrue(style.next(p) == Move.REAR, "The rear hand follows");
        });
        later(h, p, ComboStyle.GAP + 1, () -> {
            float before = foe.getHealth();
            foe.invulnerableTime = 0;
            h.assertTrue(style.strike(p) == 1, "The rear strike");
            h.assertTrue(foe.getHealth() < before, power.getPath() + ": the rear strike lands");
            h.assertTrue(style.next(p) == Move.FINISHER, "From level 2 the combo ends in a finisher");
        });
        later(h, p, 2 * ComboStyle.GAP + 2, () -> {
            aim(p, foe);
            float before = foe.getHealth();
            foe.invulnerableTime = 0;
            h.assertTrue(style.strike(p) == 1, "The finisher");
            h.assertTrue(foe.getHealth() < before, power.getPath() + ": the finisher lands");
            h.assertTrue(style.next(p) == Move.LEAD, "And the combo starts again");
            after.run();
            forget(p);
            foe.discard();
            h.succeed();
        });
    }

    @GameTest(template = "rewind_empty", batch = "styles.speed_combo")
    public static void blurBoxingCombo(GameTestHelper h) { combo(h, ModAbilities.SUPERSPEED_ID, SpeedStyle.STYLE, () -> {}); }

    @GameTest(template = "rewind_empty", batch = "styles.quake_combo")
    public static void pressurePalmCombo(GameTestHelper h) { combo(h, ModAbilities.QUAKE_ID, QuakeStyle.STYLE, () -> {}); }

    @GameTest(template = "rewind_empty", batch = "styles.cryo_combo")
    public static void iceSkaterCombo(GameTestHelper h) { combo(h, ModAbilities.CRYOKINESIS_ID, CryoStyle.STYLE, () -> {}); }

    @GameTest(template = "rewind_empty", batch = "styles.density_combo")
    public static void ghostFistCombo(GameTestHelper h) { combo(h, ModAbilities.PHASING_ID, DensityStyle.STYLE, () -> {}); }

    @GameTest(template = "rewind_empty", batch = "styles.regen_combo")
    public static void recklessBrawlerCombo(GameTestHelper h) { combo(h, ModAbilities.REGENERATION_ID, RegenStyle.STYLE, () -> {}); }

    @GameTest(template = "rewind_empty", batch = "styles.force_combo")
    public static void forcePalmCombo(GameTestHelper h) { combo(h, ModAbilities.INVISIBILITY_ID, ForceStyle.STYLE, () -> {}); }

    @GameTest(template = "rewind_empty", batch = "styles.kinetic_combo")
    public static void puppeteerCombo(GameTestHelper h) { combo(h, ModAbilities.TELEKINESIS_ID, KineticStyle.STYLE, () -> {}); }

    // ------------------------------------------------------------------
    // What makes each style its own
    // ------------------------------------------------------------------

    @GameTest(template = "rewind_empty", batch = "styles.speed_moves")
    public static void aBlitzEndsBehindTheTargetAndABlurStepCounters(GameTestHelper h) {
        ServerPlayer p = fighter(h, ModAbilities.SUPERSPEED_ID, 2);
        LivingEntity foe = foe(h, p, 4, 0);
        aim(p, foe);
        guarded(p, () -> {
            SpeedStyle.STYLE.fighter(p).step = 2;
            SpeedStyle.STYLE.fighter(p).lastStrike = p.getServer().overworld().getGameTime();
            h.assertTrue(SpeedStyle.STYLE.next(p) == Move.FINISHER, "The combo is due its Blitz");
            h.assertTrue(SpeedStyle.STYLE.strike(p) == 1, "The Blitz");
            h.assertTrue(p.getZ() > foe.getZ(), "The speedster ends up behind the target, at z " + p.getZ() + " past " + foe.getZ());
            Vec3 before = p.position();
            h.assertTrue(SpeedStyle.STYLE.dodge(p, Dodge.LEFT), "A Blur Step");
            h.assertTrue(p.position().distanceTo(before) > 3, "The speedster is simply somewhere else");
            p.hurt(p.damageSources().mobAttack(foe), 6);
            h.assertTrue(SpeedStyle.STYLE.loaded(p), "A blow during the step loads Too Slow");
        });
        forget(p);
        foe.discard();
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "styles.quake_moves")
    public static void palmsBuildStrainAndShatterTheFrozen(GameTestHelper h) {
        ServerPlayer p = fighter(h, ModAbilities.QUAKE_ID, 2);
        LivingEntity foe = foe(h, p, 3.5, 0);
        aim(p, foe);
        guarded(p, () -> {
            int strain = QuakeKit.strain(p);
            QuakeStyle.STYLE.strike(p);
            h.assertTrue(QuakeKit.strain(p) > strain, "Every palm adds Strain");
            CryoService.freezeSolid(foe, 100, p);
            h.assertTrue(CryoService.isFrozen(foe), "The foe is frozen solid");
            float before = foe.getHealth();
            QuakeStyle.STYLE.fighter(p).readyAt = -1000;
            foe.invulnerableTime = 0;
            QuakeStyle.STYLE.strike(p);
            h.assertTrue(foe.getHealth() < before, "A palm on the frozen foe shatters the ice");
        });
        forget(p);
        QuakeKit.forget(p);
        foe.discard();
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "styles.cryo_moves")
    public static void soakedFoesFreezeAndSkatesLeaveIce(GameTestHelper h) {
        ServerPlayer p = fighter(h, ModAbilities.CRYOKINESIS_ID, 2);
        LivingEntity foe = foe(h, p, 3.5, 0);
        aim(p, foe);
        guarded(p, () -> {
            HydroService.soak(foe, 200);
            CryoStyle.STYLE.strike(p);
            h.assertTrue(CryoService.isFrozen(foe), "A frost jab freezes a Soaked foe solid at once");
            h.assertTrue(CryoStyle.STYLE.dodge(p, Dodge.RIGHT), "A skate");
            h.assertTrue(CryoStyle.STYLE.strips(p) == 1, "It lays a strip of ice behind it");
        });
        forget(p);
        foe.discard();
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "styles.density_moves")
    public static void ghostFistsGrowHeavierAndPhaseSlipsLast(GameTestHelper h) {
        ServerPlayer p = fighter(h, ModAbilities.PHASING_ID, 1);
        LivingEntity a = foe(h, p, 3, 0);
        aim(p, a);
        guarded(p, () -> {
            float before = a.getHealth();
            DensityStyle.STYLE.strike(p);
            float first = before - a.getHealth();
            DensityStyle.STYLE.fighter(p).readyAt = -1000;
            a.invulnerableTime = 0;
            float mid = a.getHealth();
            DensityStyle.STYLE.strike(p);
            float second = mid - a.getHealth();
            h.assertTrue(second > first * 1.1F, "The second blow of a combo is denser than the first: " + first + " then " + second);
            h.assertTrue(DensityStyle.STYLE.dodge(p, Dodge.LEFT), "A Phase Slip");
            h.assertTrue(StyleMotion.evading(p), "Blows pass through");
        });
        forget(p);
        a.discard();
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "styles.regen_moves")
    public static void theBrawlerBracesAndPaysItBack(GameTestHelper h) {
        ServerPlayer p = fighter(h, ModAbilities.REGENERATION_ID, 2);
        LivingEntity foe = foe(h, p, 3, 0);
        aim(p, foe);
        guarded(p, () -> {
            float health = p.getHealth();
            int thrown = RegenStyle.STYLE.strike(p);
            h.assertTrue(p.getHealth() < health, "Every swing costs her a little: strike " + thrown + ", health " + health + " to " + p.getHealth());
            h.assertTrue(RegenStyle.STYLE.dodge(p, Dodge.BACK), "A double tap is a Brace");
            h.assertTrue(StyleMotion.bracing(p) && !StyleMotion.evading(p), "She braces; nothing misses her");
            float braced = p.getHealth();
            p.invulnerableTime = 0;
            p.hurt(p.damageSources().mobAttack(foe), 8);
            float taken = braced - p.getHealth();
            h.assertTrue(taken > 0 && taken <= 4.01F, "A braced blow lands at half strength, " + taken);
            h.assertTrue(RegenStyle.STYLE.loaded(p), "And loads Payback");
            float first = foe.getHealth();
            foe.invulnerableTime = 0;
            RegenStyle.STYLE.strike(p);
            h.assertTrue(first - foe.getHealth() > 3 + .5F * 2 + 4, "Payback returns what she took on top of the swing");
        });
        forget(p);
        foe.discard();
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "styles.force_moves")
    public static void pressureIsReleasedAsShards(GameTestHelper h) {
        ServerPlayer p = fighter(h, ModAbilities.INVISIBILITY_ID, 2);
        LivingEntity foe = foe(h, p, 6, 0);
        aim(p, foe);
        guarded(p, () -> {
            h.assertTrue(ForceStyle.STYLE.dodge(p, Dodge.LEFT), "A Shield Slide");
            p.hurt(p.damageSources().mobAttack(foe), 6);
            h.assertTrue(ForceStyle.STYLE.loaded(p), "The evaded blow is stored as pressure");
            float before = foe.getHealth();
            ForceStyle.STYLE.strike(p);
            h.assertTrue(foe.getHealth() < before, "The next jab lets it go as shards that strike the foe");
            h.assertTrue(!ForceStyle.STYLE.loaded(p), "And spends it");
        });
        forget(p);
        foe.discard();
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "styles.kinetic_moves", timeoutTicks = 60)
    public static void aSwatBatsOneFoeIntoAnotherAndALiftSlams(GameTestHelper h) {
        ServerPlayer p = fighter(h, ModAbilities.TELEKINESIS_ID, 2);
        // The lead swat bats toward the player's left (+x here): the second foe stands just that side, a little farther
        // off so the swat picks the first.
        LivingEntity swatted = foe(h, p, 5, 0), beside = foe(h, p, 5.4, 1.0);
        aim(p, swatted);
        float other = beside.getHealth();
        guarded(p, () -> {
            swatted.setDeltaMovement(Vec3.ZERO);
            KineticStyle.STYLE.strike(p);
            h.assertTrue(Math.abs(swatted.getDeltaMovement().x) > .5, "The swat bats the foe sideways, " + swatted.getDeltaMovement());
        });
        later(h, p, 2, () -> {
            h.assertTrue(beside.getHealth() < other, "Whoever it is batted into is hurt too");
            KineticStyle.STYLE.fighter(p).step = 2;
            KineticStyle.STYLE.fighter(p).readyAt = -1000;
            KineticStyle.STYLE.fighter(p).lastStrike = p.getServer().overworld().getGameTime();
            aim(p, beside);
            KineticStyle.STYLE.strike(p);
            h.assertTrue(KineticStyle.STYLE.lifting() == 1, "The finisher lifts the foe");
        });
        later(h, p, 2 + KineticStyle.LIFT_HANG + 2, () -> {
            h.assertTrue(KineticStyle.STYLE.lifting() == 0, "And slams them down");
            forget(p);
            swatted.discard();
            beside.discard();
            h.succeed();
        });
    }

    @GameTest(template = "rewind_empty", batch = "styles.low_moves")
    public static void everyLowMoveReachesAllRound(GameTestHelper h) {
        ResourceLocation[] powers = {ModAbilities.SUPERSPEED_ID, ModAbilities.QUAKE_ID, ModAbilities.CRYOKINESIS_ID, ModAbilities.PHASING_ID,
                ModAbilities.INVISIBILITY_ID, ModAbilities.TELEKINESIS_ID};
        for (ResourceLocation power : powers) {
            ServerPlayer p = fighter(h, power, 2);
            LivingEntity beside = foe(h, p, .3, 2.2);
            guarded(p, () -> {
                p.setPose(Pose.CROUCHING);
                p.setShiftKeyDown(true);
                ComboStyle style = (ComboStyle) FightingStyles.of(p);
                h.assertTrue(style.next(p) == Move.LOW, power.getPath() + ": crouching throws the low move");
                float before = beside.getHealth();
                h.assertTrue(style.strike(p) == 1, power.getPath() + ": the low move");
                h.assertTrue(beside.getHealth() < before, power.getPath() + ": it reaches the foe beside");
            });
            forget(p);
            beside.discard();
        }
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "styles.mimic")
    public static void anEmpathBorrowsTheStyleOfThePowerTheyChannel(GameTestHelper h) {
        ServerPlayer p = fighter(h, ModAbilities.MIMICRY_ID, 5);
        guarded(p, () -> {
            h.assertTrue(FightingStyles.of(p) == null, "An empath with nothing channeled has no style");
            MimicryService.remember(p, ModAbilities.PYROKINESIS_ID, 5);
            h.assertTrue(FightingStyles.of(p) == PyroCombat.STYLE, "Channeling Pyrokinesis, they fight like a firebender");
            MimicryService.remember(p, ModAbilities.QUAKE_ID, 5);
            h.assertTrue(FightingStyles.of(p) == QuakeStyle.STYLE, "Channeling Quake, they fight with pressure palms");
        });
        forget(p);
        MimicryService.forget(p);
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "styles.tired")
    public static void noStaminaNoStyle(GameTestHelper h) {
        ServerPlayer p = fighter(h, ModAbilities.QUAKE_ID, 2);
        guarded(p, () -> {
            PlayerEnergyService.consumeUpTo(p, PlayerEnergyService.getMaximumEnergy(p));
            h.assertTrue(QuakeStyle.STYLE.strike(p) == -1, "No stamina, no strike (the click punches instead)");
            h.assertTrue(!QuakeStyle.STYLE.dodge(p, Dodge.LEFT), "No stamina, no dodge");
        });
        forget(p);
        h.succeed();
    }
}
