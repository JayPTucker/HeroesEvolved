package com.jayptucker.heroesevolved.vibe;

import com.jayptucker.heroesevolved.ability.AbilitySlot;
import com.jayptucker.heroesevolved.ability.AbilityUseResult;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.AbilityUseService;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.grid.GridGameTests;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.skills.SkillTreeData;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Vibe's optional skills, class abilities and awakening, cast through their real keys where they have one.
 * {@link VibeGameTests} covers the five core moves.
 */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class VibeKitGameTests {
    /** The test viber stands at (5, 2, 2) facing +Z. */
    private static ServerPlayer viber(GameTestHelper h, String... skills) {
        ServerPlayer player = GridGameTests.player(h, ModAbilities.VIBE_ID, 5);
        place(h, player, 5, 2);
        learn(player, skills);
        return player;
    }

    /** A viber that can really be teleported and walk through a breach: FakePlayer's connection ignores teleports. */
    private static ServerPlayer traveler(GameTestHelper h, String... skills) {
        ServerPlayer player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "vibe-kit-traveler")) {
            @Override public boolean teleportTo(ServerLevel world, double x, double y, double z,
                                                Set<RelativeMovement> relative, float yaw, float pitch) {
                if (world != serverLevel()) return false;
                absMoveTo(x, y, z, yaw, pitch);
                return true;
            }
        };
        try {
            Connection connection = player.connection.getConnection();
            Field channel = Connection.class.getDeclaredField("channel");
            channel.setAccessible(true);
            if (connection.channel() == null) channel.set(connection, new EmbeddedChannel());
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        player.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty()
                .assignDormant(ModAbilities.VIBE_ID).unlock(ModAbilities.VIBE_ID));
        PlayerProgressionService.setLevel(player, 5);
        PlayerEnergyService.restore(player, 999);
        place(h, player, 5, 2);
        learn(player, skills);
        h.getLevel().addNewPlayer(player);
        return player;
    }

    private static void place(GameTestHelper h, ServerPlayer player, double x, double z) {
        player.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(0, 2, 0))).add(x, 0, z));
        player.setYRot(0);
        player.setXRot(0);
        player.setOnGround(true);
    }

    private static void learn(ServerPlayer player, String... ids) {
        player.setData(ModDataAttachments.SKILL_TREES, SkillTreeData.empty().with(ModAbilities.VIBE_ID.toString(),
                new SkillTreeData.Build(15, Set.of(ids), Map.of(), 0)));
    }

    private static Pillager target(GameTestHelper h, Vec3 feet) {
        Pillager mob = EntityType.PILLAGER.create(h.getLevel());
        mob.setNoAi(true);
        mob.setPersistenceRequired();
        mob.setPos(feet);
        h.getLevel().addFreshEntity(mob);
        return mob;
    }

    private static Pillager target(GameTestHelper h, ServerPlayer player, double x, double z) {
        return target(h, player.position().add(x, 0, z));
    }

    private static void cast(GameTestHelper h, ServerPlayer player, String action) {
        h.assertTrue(AbilityUseService.activateAction(player, action, false) == AbilityUseResult.SUCCESS,
                action + " must cast once learned");
    }

    /** Runs a check and, if it fails, tidies the test's players and mobs first so nothing leaks into later tests. */
    private static void step(List<Entity> cleanup, Runnable check) {
        try {
            check.run();
        } catch (RuntimeException | AssertionError failure) {
            clean(cleanup);
            throw failure;
        }
    }

    private static void finish(GameTestHelper h, List<Entity> cleanup, Runnable check) {
        try {
            check.run();
            h.succeed();
        } finally {
            clean(cleanup);
        }
    }

    private static void clean(List<Entity> cleanup) {
        for (Entity entity : cleanup) {
            if (entity instanceof ServerPlayer player) {
                VibeService.forget(player);
                VibeBreaches.close(player);
                if (player.serverLevel().getPlayerByUUID(player.getUUID()) != null)
                    player.serverLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
            } else {
                entity.discard();
            }
        }
    }

    private static List<Entity> track(Entity... entities) { return new ArrayList<>(List.of(entities)); }

    // ------------------------------------------------------------------ Concussion

    @GameTest(template = "rewind_empty", batch = "vibe.kit")
    public static void vibeLanceHitsHarderThanThePalm(GameTestHelper h) {
        ServerPlayer p = viber(h, "vibe_0_1");
        Pillager far = target(h, p, 0, 8);
        var cleanup = track(p, far);
        step(cleanup, () -> cast(h, p, "vibe_0_1"));
        h.runAfterDelay(18, () -> finish(h, cleanup, () -> h.assertTrue(far.getHealth() < 11,
                "Vibe Lance lands its 14.5-damage narrow wave (a palm deals 9): " + far.getHealth())));
    }

    @GameTest(template = "rewind_empty", batch = "vibe.kit")
    public static void reverbsTwoPalmsStrikeEachEnemyOnce(GameTestHelper h) {
        ServerPlayer p = viber(h, "vibe_reverb");
        Pillager ahead = target(h, p, 0, 5);
        var cleanup = track(p, ahead);
        step(cleanup, () -> cast(h, p, "vibe_reverb"));
        h.runAfterDelay(16, () -> finish(h, cleanup, () -> {
            h.assertTrue(ahead.getHealth() < 11, "Reverb's main front hits the enemy ahead: " + ahead.getHealth());
            h.assertTrue(ahead.isAlive(), "Both palms share one strike list, so the enemy isn't hit twice");
        }));
    }

    @GameTest(template = "rewind_empty", batch = "vibe.kit")
    public static void crosswavesSecondFrontCatchesAnEnemyOffTheFirstLine(GameTestHelper h) {
        ServerPlayer p = viber(h, "vibe_crosswave");
        // Work out where the off-hand front runs, the same way the release does, and stand an enemy on it.
        Vec3 look = p.getLookAngle(), eye = p.getEyePosition();
        Vec3 end = VibeGeometry.clip(p, eye, eye.add(look.scale(24)));
        Vec3 offHand = VibeGeometry.hand(p, look, 1, true);
        Vec3 offEnd = end.add(VibeGeometry.side(look).reverse().scale(3));
        Vec3 point = offHand.lerp(offEnd, .85);
        Pillager flank = target(h, new Vec3(point.x, p.getY(), point.z));
        var cleanup = track(p, flank);
        step(cleanup, () -> cast(h, p, "vibe_crosswave"));
        h.runAfterDelay(20, () -> finish(h, cleanup, () -> h.assertTrue(flank.getHealth() < 24,
                "The diverging second front reaches an enemy the main wave passes by")));
    }

    private static Arrow shot(GameTestHelper h, Entity owner) {
        Arrow arrow = EntityType.ARROW.create(h.getLevel());
        arrow.setOwner(owner);
        return arrow;
    }

    @GameTest(template = "rewind_empty", batch = "vibe.kit")
    public static void overflowBurstsAFullBankIntoInterferenceWhereReturnFireStops(GameTestHelper h) {
        ServerPlayer p = viber(h, "vibe_reservoir");
        Pillager shooter = target(h, p, -3, 5);
        // Off Return Fire's line, but within three blocks of where it stops against the far wall.
        Pillager bystander = target(h, p, 2.6, 8.2);
        var cleanup = track(p, shooter, bystander);
        step(cleanup, () -> {
            h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.QUATERNARY, false) == AbilityUseResult.SUCCESS,
                    "Breach Guard opens");
            Vec3 to = p.position().add(0, 1.23, 0), from = to.add(0, 0, 6);
            for (int i = 0; i < VibeService.OVERFLOW_MIN; i++)
                h.assertTrue(VibeService.intercept(h.getLevel(), shot(h, shooter), from, to), "Shot " + (i + 1) + " is caught");
            h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.QUATERNARY, false) == AbilityUseResult.SUCCESS,
                    "Return Fire releases the bank");
        });
        h.runAfterDelay(1, () -> step(cleanup, () -> h.assertTrue(bystander.getHealth() == 24,
                "The interference waits for the wave to arrive")));
        h.runAfterDelay(8, () -> finish(h, cleanup, () -> h.assertTrue(
                bystander.getHealth() < 24 && bystander.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),
                "Overflow collapses where Return Fire stops, hurting and slowing an enemy beside its line: " + bystander.getHealth())));
    }

    @GameTest(template = "rewind_empty", batch = "vibe.kit")
    public static void returnAddressMarksWhoeverFiredACaughtShot(GameTestHelper h) {
        ServerPlayer p = viber(h, "vibe_0_2");
        Pillager shooter = target(h, p, -3, 7);
        var cleanup = track(p, shooter);
        finish(h, cleanup, () -> {
            h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.QUATERNARY, false) == AbilityUseResult.SUCCESS,
                    "Breach Guard opens");
            h.assertFalse(VibeSight.marked(p, shooter), "Nobody is marked yet");
            Vec3 to = p.position().add(0, 1.23, 0);
            h.assertTrue(VibeService.intercept(h.getLevel(), shot(h, shooter), to.add(0, 0, 6), to), "The guard catches the shot");
            h.assertTrue(VibeSight.marked(p, shooter), "Return Address marks the shooter, for the blast and the lock to punish");
        });
    }

    @GameTest(template = "rewind_empty", batch = "vibe.kit")
    public static void residualSenseMarksWhoeverHurtsYou(GameTestHelper h) {
        ServerPlayer p = viber(h, "vibe_2_0");
        ServerPlayer untrained = viber(h);
        Pillager attacker = target(h, p, 0, 3);
        var cleanup = track(p, untrained, attacker);
        finish(h, cleanup, () -> {
            VibeSight.wounded(untrained, attacker);
            h.assertFalse(VibeSight.marked(untrained, attacker), "Without the skill a hit leaves no mark");
            VibeSight.wounded(p, attacker);
            h.assertTrue(VibeSight.marked(p, attacker), "Residual Sense marks whoever hurt you");
        });
    }

    @GameTest(template = "rewind_empty", batch = "vibe.kit")
    public static void slingshotMakesTheBlastAfterACrossingInstantAndHarder(GameTestHelper h) {
        ServerPlayer p = traveler(h, "vibe_1_0");
        Vec3 start = p.position();
        p.setXRot(13);
        var cleanup = track(p);
        Pillager[] behind = new Pillager[1];
        step(cleanup, () -> h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.SECONDARY, false)
                == AbilityUseResult.SUCCESS, "A linked breach opens"));
        h.runAfterDelay(12, () -> p.setPos(start.add(0, 0, 2.35)));
        h.runAfterDelay(14, () -> step(cleanup, () -> {
            h.assertTrue(p.position().distanceToSqr(start) > 25, "The viber crosses to the exit");
            h.assertTrue(VibeService.primed(p), "Crossing your own breach primes the next blast");
            VibeBreaches.close(p);
            // Turn round and fire back down the room, at an enemy away from both mouths' line of travel.
            p.setYRot(180);
            p.setXRot(0);
            behind[0] = target(h, p, 0, -3);
            cleanup.add(behind[0]);
            h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.PRIMARY, false) == AbilityUseResult.SUCCESS,
                    "Vibe Blast fires");
            h.assertFalse(VibeService.primed(p), "The blast spends the prime");
        }));
        // A normal palm winds up for three ticks; a slung one is away after one.
        h.runAfterDelay(17, () -> finish(h, cleanup, () -> h.assertTrue(behind[0].getHealth() < 13.5F,
                "Slingshot's blast lands early and 30% harder (11.7, a palm deals 9): " + behind[0].getHealth())));
    }

    @GameTest(template = "rewind_empty", batch = "vibe.kit")
    public static void throughTheSeamCarriesABlastOutOfTheOtherMouth(GameTestHelper h) {
        ServerPlayer p = traveler(h, "vibe_0_0");
        Vec3 start = p.position();
        p.setXRot(13);
        // Between the mouths (skipped by a wave that goes through the seam) and beyond the exit (reached only by it).
        Pillager between = target(h, start.add(0, 0, 4));
        Pillager beyond = target(h, start.add(0, 0, 8.5));
        var cleanup = track(p, between, beyond);
        step(cleanup, () -> h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.SECONDARY, false)
                == AbilityUseResult.SUCCESS, "A linked breach opens"));
        h.runAfterDelay(11, () -> step(cleanup, () -> {
            p.setXRot(0);
            h.assertTrue(VibeBreaches.seam(p, p.getEyePosition(), p.getEyePosition().add(0, 0, 10)) != null,
                    "The open entry lies across the blast's line");
            h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.PRIMARY, false) == AbilityUseResult.SUCCESS,
                    "Vibe Blast fires into the entry");
        }));
        h.runAfterDelay(26, () -> finish(h, cleanup, () -> {
            h.assertTrue(between.getHealth() == 24, "The wave goes into the entry, so the enemy between the mouths is untouched");
            h.assertTrue(beyond.getHealth() < 24, "and comes out of the exit to hit the enemy beyond it");
        }));
    }

    @GameTest(template = "rewind_empty", batch = "vibe.kit")
    public static void mirrorBreachCatchesShotsTheNormalGuardWouldMiss(GameTestHelper h) {
        ServerPlayer p = viber(h, "vibe_mirror");
        Pillager shooter = target(h, p, 0, 6);
        var cleanup = track(p, shooter);
        finish(h, cleanup, () -> {
            Vec3 to = p.position().add(1.6, 1.23, 0), from = to.add(0, 0, 6);
            Arrow first = EntityType.ARROW.create(h.getLevel());
            first.setOwner(shooter);
            h.assertTrue(VibeService.guard(p, false), "A normal guard opens");
            h.assertFalse(VibeService.intercept(h.getLevel(), first, from, to), "1.6 blocks off centre is outside a 1.28 guard");
            VibeService.closeGuard(p);
            cast(h, p, "vibe_mirror");
            h.assertTrue(VibeService.intercept(h.getLevel(), first, from, to) && first.isRemoved(),
                    "Mirror Breach's 2.05-block horizon catches the same shot");
            first.discard();
        });
    }

    // ------------------------------------------------------------------ Breaching

    @GameTest(template = "rewind_empty", batch = "vibe.kit")
    public static void walkingBackThroughTheExitComesOutBehindTheEntry(GameTestHelper h) {
        ServerPlayer p = traveler(h, "vibe_wayfarer", "vibe_horizon");
        Vec3 start = p.position();
        p.setXRot(13);
        var cleanup = track(p);
        step(cleanup, () -> h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.SECONDARY, false)
                == AbilityUseResult.SUCCESS, "A linked breach opens"));
        h.runAfterDelay(11, () -> p.igniteForSeconds(4));
        h.runAfterDelay(12, () -> p.setPos(start.add(0, 0, 2.35)));
        h.runAfterDelay(14, () -> step(cleanup, () -> {
            h.assertTrue(p.position().distanceToSqr(start) > 25, "The entry sends the traveler to the exit");
            h.assertTrue(p.hasEffect(MobEffects.MOVEMENT_SPEED), "Wayfarer gives Speed on crossing");
            h.assertTrue(p.hasEffect(MobEffects.DAMAGE_RESISTANCE) && p.getRemainingFireTicks() <= 0,
                    "Safe Horizon puts the fire out and gives Resistance");
            p.setPos(p.position().subtract(0, 0, 1.48));
        }));
        h.runAfterDelay(17, () -> step(cleanup, () -> h.assertTrue(p.getZ() < start.z + 2,
                "Walking back through the exit comes out behind the entry, on the side you were heading for: "
                        + (p.getZ() - start.z))));
        // Keep backing away from the entry: the traveler must not be thrown back through it.
        h.runAfterDelay(18, () -> p.setPos(start.add(0, 0, -.4)));
        h.runAfterDelay(21, () -> finish(h, cleanup, () -> h.assertTrue(p.position().distanceToSqr(start) < 1,
                "Backing away from the entry leaves the traveler where they are")));
    }

    @GameTest(template = "rewind_empty", batch = "vibe.kit")
    public static void flashstepFoldsToTheGroundYouAimAt(GameTestHelper h) {
        ServerPlayer p = traveler(h, "vibe_flashstep");
        Vec3 start = p.position();
        p.setXRot(12);
        var cleanup = track(p);
        finish(h, cleanup, () -> {
            cast(h, p, "vibe_flashstep");
            h.assertTrue(p.getZ() - start.z > 4 && Math.abs(p.getY() - start.y) < .6,
                    "Flashstep lands on the aimed ground ahead: " + p.position().subtract(start));
        });
    }

    @GameTest(template = "rewind_empty", batch = "vibe.kit")
    public static void sanctuaryBreachShieldsAndCoolsTravelers(GameTestHelper h) {
        ServerPlayer p = traveler(h, "vibe_sanctuary");
        Vec3 start = p.position();
        p.setXRot(13);
        var cleanup = track(p);
        step(cleanup, () -> cast(h, p, "vibe_sanctuary"));
        h.runAfterDelay(11, () -> p.igniteForSeconds(4));
        h.runAfterDelay(12, () -> p.setPos(start.add(0, 0, 2.35)));
        h.runAfterDelay(14, () -> finish(h, cleanup, () -> {
            h.assertTrue(p.position().distanceToSqr(start) > 25, "Sanctuary's route carries the traveler");
            h.assertTrue(p.hasEffect(MobEffects.DAMAGE_RESISTANCE) && p.getRemainingFireTicks() <= 0,
                    "Crossing a Sanctuary Breach puts out fire and gives Resistance");
        }));
    }

    // ------------------------------------------------------------------ Dimensional Sight

    @GameTest(template = "rewind_empty", batch = "vibe.kit")
    public static void coVibeReadsFartherAndMarksTheAggressor(GameTestHelper h) {
        ServerPlayer p = viber(h, "vibe_2_1");
        Pillager witness = target(h, p, 0, 7);
        Pillager attacker = target(h, p, 3, 8);
        var cleanup = track(p, witness, attacker);
        finish(h, cleanup, () -> {
            witness.hurt(h.getLevel().damageSources().mobAttack(attacker), 2);
            cast(h, p, "vibe_2_1");
            h.assertTrue(VibeSight.marked(p, witness), "Co-Vibe reaches a witness seven blocks away (a normal read: 5.5)");
            h.assertTrue(VibeSight.marked(p, attacker), "The witness's recorded attacker is revealed and marked");
        });
    }

    @GameTest(template = "rewind_empty", batch = "vibe.kit")
    public static void afterimageSlowsAMarkedEnemyTheBlastHits(GameTestHelper h) {
        ServerPlayer p = viber(h, "vibe_afterimage");
        Pillager enemy = target(h, p, 0, 3);
        var cleanup = track(p, enemy);
        step(cleanup, () -> {
            h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.TERTIARY, false) == AbilityUseResult.SUCCESS
                    && VibeSight.marked(p, enemy), "Reading a hostile witness marks it");
            h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.PRIMARY, false) == AbilityUseResult.SUCCESS,
                    "Vibe Blast queues");
        });
        h.runAfterDelay(9, () -> finish(h, cleanup, () -> {
            h.assertTrue(enemy.getHealth() < 24, "The blast hits");
            h.assertTrue(enemy.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "Afterimage slows the marked enemy it hits");
        }));
    }

    @GameTest(template = "rewind_empty", batch = "vibe.kit")
    public static void fixedPointHoldsAMarkedEnemyInTheLockLonger(GameTestHelper h) {
        ServerPlayer p = viber(h, "vibe_fixed_point");
        Pillager enemy = target(h, p, 0, 4);
        var cleanup = track(p, enemy);
        step(cleanup, () -> {
            h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.TERTIARY, false) == AbilityUseResult.SUCCESS
                    && VibeSight.marked(p, enemy), "Reading a hostile witness marks it");
            p.setXRot(18);
            h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.QUINARY, false) == AbilityUseResult.SUCCESS,
                    "Frequency Lock casts");
        });
        h.runAfterDelay(VibeGeometry.LOCK_WARNING_TICKS + 2, () -> finish(h, cleanup, () -> {
            var slow = enemy.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
            h.assertTrue(slow != null && slow.getDuration() > 70,
                    "Fixed Point slows a marked enemy for five seconds (an unmarked one: under three)");
            h.assertTrue(enemy.getHealth() <= 24 - 13, "A marked enemy takes the read bonus from the collapse: " + enemy.getHealth());
        }));
    }

    @GameTest(template = "rewind_empty", batch = "vibe.kit")
    public static void prismLockCollapsesAWiderZone(GameTestHelper h) {
        ServerPlayer p = viber(h, "vibe_prism_lock");
        place(h, p, 5.5, 5.5);
        p.setXRot(90); // Straight down: both locks centre on the caster's feet.
        Pillager edge = target(h, p, 4.24, 4.24); // six blocks out
        var cleanup = track(p, edge);
        step(cleanup, () -> cast(h, p, "vibe_prism_lock"));
        h.runAfterDelay(VibeGeometry.LOCK_WARNING_TICKS + 2, () -> finish(h, cleanup, () -> {
            h.assertTrue(edge.getHealth() < 24 && edge.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),
                    "Prism Lock's 6.8-block collapse reaches an enemy six blocks out (Frequency Lock: five)");
            h.assertFalse(p.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "The caster standing in the middle is untouched");
        }));
    }

    // ------------------------------------------------------------------ Interference and the awakening

    @GameTest(template = "rewind_empty", batch = "vibe.kit", timeoutTicks = 120)
    public static void interferenceStopsASpeedsterUntilItClears(GameTestHelper h) {
        ServerPlayer speedster = GridGameTests.player(h, ModAbilities.SUPERSPEED_ID, 5);
        var cleanup = track(speedster);
        step(cleanup, () -> {
            // PvP is always off on the test server, so call the rule a lock or Afterimage applies directly.
            VibeService.desync(speedster, 40);
            h.assertTrue(VibeService.desynced(speedster), "A caught speedster is out of phase");
            h.assertTrue(speedster.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "Interference also slows");
            h.assertTrue(AbilityUseService.activateAssignedAction(speedster, AbilitySlot.PRIMARY, false) == AbilityUseResult.CANNOT_USE,
                    "Superspeed can't be used while out of phase");
        });
        h.runAfterDelay(42, () -> finish(h, cleanup, () -> h.assertFalse(VibeService.desynced(speedster),
                "The interference clears when its time runs out")));
    }

    @GameTest(template = "rewind_empty", batch = "vibe.kit")
    public static void theAwakeningBreachesAWardAwayFromItsAttacker(GameTestHelper h) {
        ServerPlayer owner = viber(h);
        place(h, owner, 2, 2);
        Villager ward = EntityType.VILLAGER.create(h.getLevel());
        ward.setNoAi(true);
        ward.setPos(owner.position().add(6, 0, 5.5));
        h.getLevel().addFreshEntity(ward);
        Pillager threat = target(h, ward.position().add(0, 0, 1.5));
        Vec3 before = ward.position();
        var cleanup = track(owner, ward, threat);
        finish(h, cleanup, () -> {
            VibeService.awaken(owner, ward, List.of(threat));
            h.assertTrue(ward.position().distanceToSqr(before) > 9 && ward.getZ() < before.z,
                    "The ward is breached away from the attacker: " + ward.position().subtract(before));
        });
    }

    @GameTest(template = "rewind_empty", batch = "vibe.kit")
    public static void aBlockedAwakeningPushesTheAttackerBackInstead(GameTestHelper h) {
        ServerPlayer owner = viber(h);
        place(h, owner, 2, 2);
        Villager ward = EntityType.VILLAGER.create(h.getLevel());
        ward.setNoAi(true);
        ward.setPos(owner.position().add(6, 0, 5));
        h.getLevel().addFreshEntity(ward);
        // A wall just behind the ward leaves no room to breach it away from the attacker.
        for (int x = 7; x <= 10; x++) for (int y = 2; y <= 4; y++) h.setBlock(new BlockPos(x, y, 9), net.minecraft.world.level.block.Blocks.STONE);
        Pillager threat = target(h, ward.position().add(0, 0, -2));
        Vec3 before = ward.position();
        var cleanup = track(owner, ward, threat);
        finish(h, cleanup, () -> {
            VibeService.awaken(owner, ward, List.of(threat));
            h.assertTrue(ward.position().distanceToSqr(before) < .01, "With no room, the ward stays put");
            h.assertTrue(threat.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "The interference slows the attacker");
            h.assertTrue(threat.getDeltaMovement().z < 0, "and shoves it away from the ward");
        });
    }
}
