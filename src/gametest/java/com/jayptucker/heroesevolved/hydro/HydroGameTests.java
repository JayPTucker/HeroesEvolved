package com.jayptucker.heroesevolved.hydro;

import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.combat.FightingStyles;
import com.jayptucker.heroesevolved.combat.FightingStyles.Dodge;
import com.jayptucker.heroesevolved.combat.StyleMotion;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.skills.SkillTreeData;
import com.jayptucker.heroesevolved.styles.Piece;
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
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Hydrokinesis: the reservoir, the kit, waterbending, the tree and the classes. */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class HydroGameTests {
    private static ServerPlayer bender(GameTestHelper h, int level, String... learned) {
        ServerPlayer p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "waterbender-test")) {
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
                .assignDormant(ModAbilities.HYDROKINESIS_ID).unlock(ModAbilities.HYDROKINESIS_ID));
        if (learned.length > 0) p.setData(ModDataAttachments.SKILL_TREES, SkillTreeData.empty()
                .with(ModAbilities.HYDROKINESIS_ID.toString(), new SkillTreeData.Build(15, Set.of(learned), Map.of())));
        PlayerProgressionService.setLevel(p, level);
        PlayerEnergyService.restore(p, PlayerEnergyService.getMaximumEnergy(p));
        HydroService.fill(p, 200);
        return p;
    }

    private static AbilityUseContext context(ServerPlayer p, boolean shift) {
        return new AbilityUseContext(p, ModAbilities.HYDROKINESIS_ID, 5, shift);
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
        HydroService.leave(p);
        FightingStyles.forget(p);
    }

    private static void later(GameTestHelper h, ServerPlayer p, int ticks, Runnable step) {
        h.runAfterDelay(ticks, () -> {
            try { step.run(); }
            catch (RuntimeException | AssertionError e) { forget(p); throw e; }
        });
    }

    private static Runnable guard(ServerPlayer p, Runnable step) {
        return () -> {
            try { step.run(); }
            catch (RuntimeException | AssertionError e) { forget(p); throw e; }
        };
    }

    // ------------------------------------------------------------------
    // The reservoir
    // ------------------------------------------------------------------

    @GameTest(template = "rewind_empty", batch = "hydro.reservoir")
    public static void waterIsCarriedAndPulledThinFromTheAirWhenDry(GameTestHelper h) {
        ServerPlayer p = bender(h, 1);
        guard(p, () -> {
            h.assertTrue(HydroService.water(p) == HydroGeometry.WATER, "A bender fills to a hundred");
            HydroService.Draw full = HydroService.draw(p, 25);
            h.assertTrue(!full.thin() && HydroService.water(p) == 75, "A move spends what it needs");
            HydroService.fill(p, -200);
            HydroService.Draw thin = HydroService.draw(p, 25);
            h.assertTrue(thin.thin() && thin.power() < .75F, "With nothing carried, the water is pulled thin and hits softer");
            ServerPlayer skin = bender(h, 1, "hydro_2_2");
            h.assertTrue(HydroService.capacity(skin) == HydroGeometry.SKIN_WATER, "Water Skin carries half again as much");
            forget(skin);
        }).run();
        forget(p);
        h.succeed();
    }

    // ------------------------------------------------------------------
    // The kit
    // ------------------------------------------------------------------

    @GameTest(template = "rewind_empty", batch = "hydro.whip")
    public static void theWhipCracksAndSoaksAndTheSnareHaulsIn(GameTestHelper h) {
        ServerPlayer p = bender(h, 1);
        LivingEntity near = foe(h, p, 5, 0);
        aim(p, near);
        guard(p, () -> {
            float before = near.getHealth();
            near.setDeltaMovement(Vec3.ZERO);
            h.assertTrue(HydroService.whip(context(p, false)), "The whip cracks");
            h.assertTrue(near.getHealth() < before, "It lands");
            h.assertTrue(HydroService.soaked(near), "And drenches its target");
            h.assertTrue(near.getDeltaMovement().z > .2, "The crack drives them back, " + near.getDeltaMovement());
            near.setDeltaMovement(Vec3.ZERO);
            near.invulnerableTime = 0;
            h.assertTrue(HydroService.whip(context(p, true)), "With Shift it snares");
            h.assertTrue(near.getDeltaMovement().z < -.5, "The snare hauls them toward the bender, " + near.getDeltaMovement());
        }).run();
        forget(p);
        near.discard();
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "hydro.wave", timeoutTicks = 80)
    public static void aCrashingWaveRollsOverAndCarriesAFoe(GameTestHelper h) {
        ServerPlayer p = bender(h, 1);
        LivingEntity foe = foe(h, p, 5, 0);
        float before = foe.getHealth();
        guard(p, () -> {
            h.assertTrue(HydroService.crashingWave(context(p, false)), "The wave rises");
            h.assertTrue(HydroService.waves(p) == 1, "And starts to roll");
            h.assertTrue(HydroService.water(p) == HydroGeometry.WATER - 25, "It costs a quarter of the water");
        }).run();
        later(h, p, 16, () -> {
            h.assertTrue(foe.getHealth() < before, "The wave rolls over the foe five blocks ahead");
            h.assertTrue(HydroService.soaked(foe), "And soaks them");
        });
        later(h, p, Piece.HYDRO_WAVE.ticks + 4, () -> {
            h.assertTrue(HydroService.waves(p) == 0, "It breaks at the end of its run");
            forget(p);
            foe.discard();
            h.succeed();
        });
    }

    @GameTest(template = "rewind_empty", batch = "hydro.octopus", timeoutTicks = 80)
    public static void octopusFormLashesSwallowsShotsAndBursts(GameTestHelper h) {
        ServerPlayer p = bender(h, 2);
        LivingEntity foe = foe(h, p, 2.5, 0);
        float before = foe.getHealth();
        Arrow arrow = EntityType.ARROW.create(h.getLevel());
        guard(p, () -> {
            h.assertTrue(HydroService.startOctopus(context(p, false)), "The water wraps round the bender");
            h.assertTrue(HydroService.octopus(p), "Octopus Form is up");
            h.assertTrue(Math.abs(HydroService.shelter(p, 10) - 6.5F) < .01F, "Blows against the shell lose a third");
            arrow.moveTo(p.getX() + 1.5, p.getY() + 1.2, p.getZ());
            arrow.setDeltaMovement(-1, 0, 0);
            h.getLevel().addFreshEntity(arrow);
        }).run();
        later(h, p, HydroGeometry.LASH_EVERY + 4, () -> {
            h.assertTrue(foe.getHealth() < before, "An arm lashes the foe in reach");
            h.assertTrue(arrow.isRemoved(), "The shot that came near is swallowed");
            float mid = foe.getHealth();
            foe.invulnerableTime = 0;
            h.assertTrue(HydroService.octopusBurst(context(p, false)), "A second press throws every arm out");
            h.assertTrue(foe.getHealth() < mid, "The burst hits");
            h.assertTrue(!HydroService.octopus(p), "And the form ends");
            h.assertTrue(!HydroService.octopusBurst(context(p, false)), "With no form there is nothing to burst");
            forget(p);
            foe.discard();
            h.succeed();
        });
    }

    @GameTest(template = "rewind_empty", batch = "hydro.rider", timeoutTicks = 80)
    public static void waveRiderSurfsAndLeapsOffTheCrest(GameTestHelper h) {
        ServerPlayer p = bender(h, 3);
        guard(p, () -> h.assertTrue(HydroService.startRide(context(p, false)), "A wave rises under the bender")).run();
        later(h, p, 2, () -> {
            h.assertTrue(HydroService.riding(p), "Riding");
            h.assertTrue(p.getDeltaMovement().horizontalDistance() > .5, "The wave carries them along, " + p.getDeltaMovement());
            h.assertTrue(HydroService.leap(context(p, false)), "A second press leaps off the crest");
            h.assertTrue(p.getDeltaMovement().y > 1, "High into the air, " + p.getDeltaMovement());
            h.assertTrue(StyleMotion.cushioned(p), "The landing is cushioned");
        });
        later(h, p, 46, () -> {
            h.assertTrue(!HydroService.riding(p), "The ride ends with the landing");
            forget(p);
            h.succeed();
        });
    }

    @GameTest(template = "rewind_empty", batch = "hydro.maelstrom", timeoutTicks = 140)
    public static void aMaelstromRisesDragsAndComesDownAsADeluge(GameTestHelper h) {
        ServerPlayer p = bender(h, 5);
        p.setXRot(38);
        LivingEntity foe = foe(h, p, 4.5, 2);
        float before = foe.getHealth();
        guard(p, () -> {
            h.assertTrue(HydroService.startMaelstrom(context(p, false)), "A whirlpool opens where the bender looks");
            h.assertTrue(HydroService.storming(p), "The Maelstrom is running");
        }).run();
        later(h, p, HydroGeometry.SPOUT_WINDUP + 30, () -> {
            h.assertTrue(foe.getHealth() < before, "The waterspout drags in and drowns the foe nearby");
            float mid = foe.getHealth();
            foe.invulnerableTime = 0;
            h.assertTrue(HydroService.endMaelstrom(p), "Shift brings it down");
            h.assertTrue(!HydroService.storming(p), "It is over");
            h.assertTrue(foe.getHealth() < mid, "The Deluge crashes down on what it held");
            forget(p);
            foe.discard();
            h.succeed();
        });
    }

    // ------------------------------------------------------------------
    // Waterbending
    // ------------------------------------------------------------------

    @GameTest(template = "rewind_empty", batch = "hydro.style_combo")
    public static void lashesAlternateThenIceDaggersFly(GameTestHelper h) {
        ServerPlayer p = bender(h, 2);
        LivingEntity foe = foe(h, p, 4, 0);
        aim(p, foe);
        guard(p, () -> {
            h.assertTrue(FightingStyles.of(p) == HydroCombat.STYLE, "A hydrokinetic fights as a waterbender");
            h.assertTrue(HydroCombat.next(p) == HydroCombat.Strike.LASH, "A fresh combo opens with a lash");
            float before = foe.getHealth();
            h.assertTrue(HydroCombat.strike(p) == 1, "An empty-handed click lashes");
            h.assertTrue(foe.getHealth() < before && HydroService.soaked(foe), "The lash lands and drenches");
            h.assertTrue(HydroCombat.strike(p) == 0, "A second click straight away is too soon");
            h.assertTrue(HydroCombat.next(p) == HydroCombat.Strike.BACKHAND, "The backhand follows");
        }).run();
        later(h, p, HydroCombat.LASH_GAP + 1, () -> {
            h.assertTrue(HydroCombat.strike(p) == 1, "The backhand");
            h.assertTrue(HydroCombat.next(p) == HydroCombat.Strike.DAGGERS, "From level 2 the combo ends in Ice Daggers");
        });
        later(h, p, 2 * HydroCombat.LASH_GAP + 2, () -> {
            float before = foe.getHealth();
            foe.invulnerableTime = 0;
            h.assertTrue(HydroCombat.strike(p) == 1, "The daggers are thrown");
            h.assertTrue(foe.getHealth() < before, "They strike the foe");
            h.assertTrue(HydroCombat.next(p) == HydroCombat.Strike.LASH, "And the combo starts again");
            forget(p);
            foe.discard();
            h.succeed();
        });
    }

    @GameTest(template = "rewind_empty", batch = "hydro.style_wheel")
    public static void crouchingWhirlsAWaterWheel(GameTestHelper h) {
        ServerPlayer p = bender(h, 2);
        LivingEntity behind = foe(h, p, -2.5, 0), beside = foe(h, p, 0, 2.5);
        p.setPose(Pose.CROUCHING);
        p.setShiftKeyDown(true);
        guard(p, () -> {
            h.assertTrue(HydroCombat.next(p) == HydroCombat.Strike.WHEEL, "Crouching turns the click into a Water Wheel");
            float a = behind.getHealth(), b = beside.getHealth();
            h.assertTrue(HydroCombat.strike(p) == 1, "The wheel turns");
            h.assertTrue(behind.getHealth() < a && beside.getHealth() < b, "It reaches all round");
        }).run();
        forget(p);
        behind.discard();
        beside.discard();
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "hydro.style_tide")
    public static void aFlowStepTurnsTheTideAndTheNextLashSnares(GameTestHelper h) {
        ServerPlayer p = bender(h, 2);
        LivingEntity attacker = foe(h, p, 5, 0);
        aim(p, attacker);
        var source = p.damageSources().mobAttack(attacker);
        guard(p, () -> {
            float health = p.getHealth();
            h.assertTrue(HydroCombat.dodge(p, Dodge.RIGHT), "A Flow Step");
            h.assertTrue(HydroCombat.isDodging(p) && StyleMotion.evading(p), "It has its evasion window");
            p.hurt(source, 6);
            h.assertTrue(p.getHealth() == health, "A blow during the step misses");
            h.assertTrue(HydroCombat.isLoaded(p), "The missed blow turns the tide");
            h.assertTrue(HydroCombat.next(p) == HydroCombat.Strike.BACKHAND, "The loaded rear hand is next");
            attacker.setDeltaMovement(Vec3.ZERO);
            float before = attacker.getHealth();
            h.assertTrue(HydroCombat.strike(p) == 1, "The turned lash is thrown");
            h.assertTrue(attacker.getHealth() < before, "It catches the attacker");
            h.assertTrue(attacker.getDeltaMovement().z < -.5, "And hauls them in, " + attacker.getDeltaMovement());
            h.assertTrue(!HydroCombat.isLoaded(p), "Throwing it spends the turn");
        }).run();
        forget(p);
        attacker.discard();
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "hydro.style_vault")
    public static void aGeyserVaultThrowsTheBenderBackAndTheFoeUp(GameTestHelper h) {
        ServerPlayer p = bender(h, 2);
        LivingEntity foe = foe(h, p, 1, 0);
        guard(p, () -> {
            float before = foe.getHealth();
            foe.setDeltaMovement(Vec3.ZERO);
            h.assertTrue(HydroCombat.dodge(p, Dodge.BACK), "A Geyser Vault");
            h.assertTrue(p.getDeltaMovement().z < 0 && p.getDeltaMovement().y > .5, "The bender goes up and back, " + p.getDeltaMovement());
            h.assertTrue(foe.getHealth() < before, "The column hits the foe in front");
            h.assertTrue(foe.getDeltaMovement().y > .5, "And launches them, " + foe.getDeltaMovement());
        }).run();
        forget(p);
        foe.discard();
        h.succeed();
    }

    // ------------------------------------------------------------------
    // The tree and the classes
    // ------------------------------------------------------------------

    @GameTest(template = "rewind_empty", batch = "hydro.geyser", timeoutTicks = 60)
    public static void aGeyserMarksItsSpotThenBursts(GameTestHelper h) {
        ServerPlayer p = bender(h, 5, "hydro_0_1");
        p.setXRot(38);
        LivingEntity foe = foe(h, p, 2.2, 0);
        float before = foe.getHealth();
        guard(p, () -> {
            h.assertTrue(HydroSkills.geyser(context(p, false)), "A Geyser is marked");
            h.assertTrue(HydroSkills.geysers(p) == 1, "It waits");
            h.assertTrue(foe.getHealth() == before, "Nothing happens before it bursts");
        }).run();
        later(h, p, HydroGeometry.GEYSER_DELAY + 2, () -> {
            h.assertTrue(HydroSkills.geysers(p) == 0, "It has burst");
            h.assertTrue(foe.getHealth() < before, "The column throws the foe standing on it");
            forget(p);
            foe.discard();
            h.succeed();
        });
    }

    @GameTest(template = "rewind_empty", batch = "hydro.wall", timeoutTicks = 40)
    public static void aTideWallSwallowsShots(GameTestHelper h) {
        ServerPlayer p = bender(h, 5, "hydro_1_1");
        Arrow arrow = EntityType.ARROW.create(h.getLevel());
        guard(p, () -> {
            h.assertTrue(HydroSkills.wall(context(p, false)), "The wall rises");
            h.assertTrue(HydroSkills.walls(p) == 1, "It stands");
            arrow.moveTo(p.getX(), p.getY() + 1.5, p.getZ() + 4.5);
            arrow.setDeltaMovement(0, 0, -1.5);
            h.getLevel().addFreshEntity(arrow);
        }).run();
        later(h, p, 3, () -> {
            h.assertTrue(arrow.isRemoved(), "A shot flying through the wall is swallowed");
            forget(p);
            h.succeed();
        });
    }

    @GameTest(template = "rewind_empty", batch = "hydro.healing", timeoutTicks = 100)
    public static void healingWaterHealsFriends(GameTestHelper h) {
        ServerPlayer p = bender(h, 5, "hydro_2_1");
        var friend = EntityType.VILLAGER.create(h.getLevel());
        friend.setNoAi(true);
        friend.moveTo(p.getX(), p.getY(), p.getZ() + 3, 180, 0);
        h.getLevel().addFreshEntity(friend);
        friend.setHealth(6);
        aim(p, friend);
        guard(p, () -> h.assertTrue(HydroSkills.healingWater(context(p, false)), "Healing Water wraps the friend in sight")).run();
        later(h, p, 62, () -> {
            h.assertTrue(friend.getHealth() > 10, "The friend heals over three seconds, " + friend.getHealth());
            forget(p);
            friend.discard();
            h.succeed();
        });
    }

    @GameTest(template = "rewind_empty", batch = "hydro.skills", timeoutTicks = 60)
    public static void theTreesActivesAllDoSomething(GameTestHelper h) {
        ServerPlayer p = bender(h, 5, "hydro_pressure_jet", "hydro_water_spout", "hydro_rain_call");
        LivingEntity foe = foe(h, p, 4, 0);
        aim(p, foe);
        float before = foe.getHealth();
        guard(p, () -> {
            h.assertTrue(HydroSkills.jet(context(p, false)), "The Pressure Jet starts");
            h.assertTrue(HydroSkills.jetting(p), "And holds");
            h.assertTrue(HydroSkills.rainCall(context(p, false)), "Rain Call");
            h.assertTrue(HydroSkills.fields(p, Piece.HYDRO_RAIN) == 1, "The cloud is overhead");
        }).run();
        later(h, p, 6, () -> {
            h.assertTrue(foe.getHealth() < before, "The jet hurts what it touches");
            h.assertTrue(HydroSkills.endJet(p), "Shift stops the jet");
            h.assertTrue(HydroSkills.spout(context(p, false)), "A Water Spout rises");
        });
        later(h, p, 10, () -> {
            h.assertTrue(p.getDeltaMovement().y > .1, "The spout lifts the bender, " + p.getDeltaMovement());
            h.assertTrue(HydroSkills.drop(p), "A second press drops them");
            h.assertTrue(p.getDeltaMovement().y < -1, "Down in a crash");
            forget(p);
            foe.discard();
            h.succeed();
        });
    }

    @GameTest(template = "rewind_empty", batch = "hydro.spirit")
    public static void spiritWaterTurnsAsideAKillingBlowOnce(GameTestHelper h) {
        ServerPlayer p = bender(h, 5, "hydro_spirit_water");
        guard(p, () -> {
            float health = p.getHealth();
            float first = HydroService.spiritWater(p, 1000);
            h.assertTrue(Math.abs(first - (health - 8)) < .01F, "The killing blow leaves the bender at 8 health, " + first);
            h.assertTrue(HydroService.water(p) == 0, "The water is spent");
            HydroService.fill(p, 100);
            h.assertTrue(HydroService.spiritWater(p, 1000) == 1000, "It can't happen again so soon");
            h.assertTrue(HydroService.spiritWater(p, 1) == 1, "An ordinary blow is never touched");
        }).run();
        forget(p);
        h.succeed();
    }

    // ------------------------------------------------------------------
    // The tree's passives (Return Current, Rain Call, Soothing Tide)
    // ------------------------------------------------------------------

    @GameTest(template = "rewind_empty", batch = "hydro.return", timeoutTicks = 40)
    public static void returnCurrentFlingsASwallowedShotBackAtItsShooter(GameTestHelper h) {
        ServerPlayer p = bender(h, 5, "hydro_1_1", "hydro_return_current");
        LivingEntity shooter = foe(h, p, 12, 0);
        Arrow arrow = EntityType.ARROW.create(h.getLevel());
        guard(p, () -> {
            h.assertTrue(HydroSkills.wall(context(p, false)), "The wall rises");
            arrow.setOwner(shooter);
            arrow.setNoGravity(true);
            arrow.moveTo(p.getX(), p.getY() + 1.5, p.getZ() + 4.5);
            arrow.setDeltaMovement(0, 0, -1.5);
            h.getLevel().addFreshEntity(arrow);
        }).run();
        later(h, p, 3, () -> {
            h.assertFalse(arrow.isRemoved(), "With Return Current the shot is not swallowed");
            h.assertTrue(arrow.getOwner() == p, "It is the bender's now");
            h.assertTrue(arrow.getDeltaMovement().z > 0, "And flies back toward its shooter, " + arrow.getDeltaMovement());
            arrow.discard();
            shooter.discard();
            forget(p);
            h.succeed();
        });
    }

    @GameTest(template = "rewind_empty", batch = "hydro.rain", timeoutTicks = 60)
    public static void rainCallMendsAndPutsOutFriendsSoaksFoesAndFillsTheWater(GameTestHelper h) {
        ServerPlayer p = bender(h, 5, "hydro_rain_call");
        var friend = EntityType.VILLAGER.create(h.getLevel());
        friend.setNoAi(true);
        friend.moveTo(p.getX() + 3, p.getY(), p.getZ(), 180, 0);
        h.getLevel().addFreshEntity(friend);
        friend.setHealth(6);
        friend.setRemainingFireTicks(200);
        LivingEntity foe = foe(h, p, 0, -3);
        guard(p, () -> {
            HydroService.fill(p, -60);
            h.assertTrue(HydroSkills.rainCall(context(p, false)), "The cloud gathers");
        }).run();
        int water = HydroService.water(p);
        later(h, p, 45, () -> {
            h.assertTrue(friend.getHealth() > 6, "A friend under the rain mends, " + friend.getHealth());
            h.assertFalse(friend.isOnFire(), "And stops burning");
            h.assertTrue(HydroService.soaked(foe), "A foe under it is Soaked");
            h.assertTrue(HydroService.water(p) > water, "The bender's water fills, " + water + " to " + HydroService.water(p));
            friend.discard();
            foe.discard();
            forget(p);
            h.succeed();
        });
    }

    @GameTest(template = "rewind_empty", batch = "hydro.soothing", timeoutTicks = 100)
    public static void soothingTideSpreadsHealingWaterToFriendsNearby(GameTestHelper h) {
        ServerPlayer p = bender(h, 5, "hydro_2_1", "hydro_soothing_tide");
        var aimed = EntityType.VILLAGER.create(h.getLevel());
        var beside = EntityType.VILLAGER.create(h.getLevel());
        aimed.setNoAi(true);
        beside.setNoAi(true);
        aimed.moveTo(p.getX(), p.getY(), p.getZ() + 3, 180, 0);
        beside.moveTo(p.getX() + 2, p.getY(), p.getZ() + 4, 180, 0);
        h.getLevel().addFreshEntity(aimed);
        h.getLevel().addFreshEntity(beside);
        aimed.setHealth(4);
        beside.setHealth(4);
        aim(p, aimed);
        guard(p, () -> h.assertTrue(HydroSkills.healingWater(context(p, false)), "Healing Water wraps the friend in sight")).run();
        later(h, p, 62, () -> {
            float main = aimed.getHealth() - 4, spread = beside.getHealth() - 4;
            h.assertTrue(main > 6, "The friend in sight heals in full, " + main);
            h.assertTrue(spread > 2 && spread < main, "A friend within four blocks of them heals at half, " + spread + " of " + main);
            aimed.discard();
            beside.discard();
            forget(p);
            h.succeed();
        });
    }
}
