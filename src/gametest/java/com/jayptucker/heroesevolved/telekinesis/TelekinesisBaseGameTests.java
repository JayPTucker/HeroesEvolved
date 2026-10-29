package com.jayptucker.heroesevolved.telekinesis;

import com.jayptucker.heroesevolved.ability.AbilitySlot;
import com.jayptucker.heroesevolved.ability.AbilityUseResult;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.AbilityUseService;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.skills.SkillTreeData;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Base controls, physical cover, safe releases and recovery semantics of the rebuilt force kit. */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class TelekinesisBaseGameTests {
    private static ServerPlayer player(GameTestHelper h) {
        ServerPlayer p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "tk-base"));
        try {
            Connection connection = p.connection.getConnection();
            Field channel = Connection.class.getDeclaredField("channel"); channel.setAccessible(true);
            if (connection.channel() == null) channel.set(connection, new EmbeddedChannel());
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 40, 3))));
        p.setYRot(0); p.setXRot(0); p.setOnGround(false);
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty()
                .assignDormant(ModAbilities.TELEKINESIS_ID).unlock(ModAbilities.TELEKINESIS_ID));
        PlayerProgressionService.setLevel(p, 5);
        PlayerEnergyService.restore(p, PlayerEnergyService.getMaximumEnergy(p));
        return p;
    }
    private static Mob mob(GameTestHelper h, Vec3 position) {
        Mob mob = EntityType.PILLAGER.create(h.getLevel());
        mob.setNoAi(true); mob.setPersistenceRequired(); mob.setPos(position);
        h.getLevel().addFreshEntity(mob);
        return mob;
    }
    /**
     * A mob that obeys physics. Vanilla never moves a NoAI mob under its own velocity (it only decays),
     * so tests that throw or slam a body need its AI on; with no goals it still stands still.
     */
    private static Mob physicalMob(GameTestHelper h, Vec3 position) {
        Mob mob = EntityType.PILLAGER.create(h.getLevel());
        mob.setPersistenceRequired(); mob.setPos(position);
        mob.goalSelector.removeAllGoals(goal -> true);
        mob.targetSelector.removeAllGoals(goal -> true);
        h.getLevel().addFreshEntity(mob);
        return mob;
    }
    private static void aim(ServerPlayer p, Entity target) {
        Vec3 delta = target.getBoundingBox().getCenter().subtract(p.getEyePosition());
        p.setYRot((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
        p.setXRot((float) -Math.toDegrees(Math.atan2(delta.y, delta.horizontalDistance())));
    }
    private static void tick(ServerPlayer p, int ticks) {
        for (int i = 0; i < ticks; i++) TelekinesisService.tick(p.getServer());
    }
    private static void cleanup(ServerPlayer p, Entity... targets) {
        PowerRules.rewinding(p, false);
        TelekinesisService.stopAll(p);
        for (Entity target : targets) target.discard();
    }
    private static PlayerTeam team(ServerPlayer p, Entity friend) {
        var scoreboard = p.getScoreboard();
        PlayerTeam team = scoreboard.addPlayerTeam("tk" + p.getUUID().toString().substring(0, 8));
        scoreboard.addPlayerToTeam(p.getScoreboardName(), team);
        scoreboard.addPlayerToTeam(friend.getScoreboardName(), team);
        return team;
    }

    @GameTest(template = "rewind_empty", batch = "tk.base_push")
    public static void palmStrikeRespectsCoverAndFriends(GameTestHelper h) {
        ServerPlayer p = player(h);
        Mob enemy = mob(h, p.position().add(0, 0, 4));
        Mob friend = mob(h, p.position().add(1, 0, 4));
        Mob covered = mob(h, p.position().add(0, 0, 9));
        PlayerTeam team = team(p, friend);
        BlockPos wall = p.blockPosition().offset(0, 0, 6);
        try {
            for (int y = 0; y < 4; y++) h.getLevel().setBlock(wall.above(y), Blocks.STONE.defaultBlockState(), 3);
            aim(p, enemy);
            TelekinesisService.push(p, 5);
            h.assertTrue(enemy.getDeltaMovement().length() > .5 && enemy.getHealth() < enemy.getMaxHealth(),
                    "The palm strike moves and hits a visible enemy");
            h.assertTrue(friend.getDeltaMovement().lengthSqr() < .001 && friend.getHealth() == friend.getMaxHealth(),
                    "A teammate is protected from both force and damage");
            h.assertTrue(covered.getDeltaMovement().lengthSqr() < .001 && covered.getHealth() == covered.getMaxHealth(),
                    "Solid cover stops the cone");
            h.assertTrue(h.getLevel().getBlockState(wall).is(Blocks.STONE), "The wave does not destroy terrain");
            h.succeed();
        } finally {
            for (int y = 0; y < 4; y++) h.getLevel().setBlock(wall.above(y), Blocks.AIR.defaultBlockState(), 3);
            p.getScoreboard().removePlayerTeam(team); cleanup(p, enemy, friend, covered);
        }
    }

    @GameTest(template = "rewind_empty", batch = "tk.base_grip")
    public static void gripStaysOffTheCrosshairAndDoesNotMutatePersistentFlags(GameTestHelper h) {
        ServerPlayer p = player(h);
        Mob target = mob(h, p.position().add(0, 0, 4));
        target.setNoGravity(true);
        try {
            aim(p, target);
            h.assertTrue(TelekinesisService.grip(p, 5), "A visible mind can be seized");
            p.setXRot(0); target.setDeltaMovement(Vec3.ZERO); tick(p, 1);
            Vec3 level = target.getDeltaMovement();
            p.setXRot(80); target.setDeltaMovement(Vec3.ZERO); tick(p, 1);
            h.assertTrue(target.getDeltaMovement().distanceToSqr(level) < .0001,
                    "Looking sharply down does not drag the held target into the crosshair or floor");
            h.assertTrue(Math.abs(level.x) > .1, "The hold moves the target into a peripheral side slot");
            TelekinesisService.cancelGrip(p);
            h.assertTrue(target.isNoGravity() && target.isNoAi() && !target.noPhysics && !target.isInvulnerable(),
                    "Grip never overwrites original gravity, AI, collision or protection flags");
            h.assertTrue(!TelekinesisService.isHeld(target), "Cancellation releases the target reservation");
            h.succeed();
        } finally { cleanup(p, target); }
    }

    @GameTest(template = "rewind_empty", batch = "tk.base_friendly_release")
    public static void softLandingOnlyProtectsGentlyReleasedFriends(GameTestHelper h) {
        ServerPlayer p = player(h);
        Mob friend = mob(h, p.position().add(0, 0, 4));
        PlayerTeam team = team(p, friend);
        p.setData(ModDataAttachments.SKILL_TREES, SkillTreeData.empty().with(ModAbilities.TELEKINESIS_ID.toString(),
                new SkillTreeData.Build(5, Set.of("telekinesis_0_0"), Map.of(), 0)));
        try {
            aim(p, friend);
            h.assertTrue(TelekinesisService.grip(p, 5), "A friendly grip supports rescues");
            friend.fallDistance = 20;
            float health = friend.getHealth();
            p.setXRot(0);
            h.assertTrue(TelekinesisService.placeGripped(p), "The modifier always releases a grip");
            h.assertTrue(friend.fallDistance == 0 && friend.hasEffect(MobEffects.SLOW_FALLING) && friend.getHealth() == health,
                    "Soft Landing catches the friend without applying attack damage");
            h.assertTrue(!TelekinesisService.placeGripped(p), "A duplicate modifier cannot repeat a release");
            h.succeed();
        } finally { p.getScoreboard().removePlayerTeam(team); cleanup(p, friend); }
    }

    @GameTest(template = "rewind_empty", batch = "tk.base_projectiles")
    public static void bulletCatchIsBoundedPeripheralAndLeavesFriendlyShotsAlone(GameTestHelper h) {
        ServerPlayer p = player(h);
        Mob shooter = mob(h, p.position().add(0, 0, 6));
        List<Arrow> arrows = new ArrayList<>();
        try {
            for (int i = 0; i < 16; i++) {
                Arrow arrow = new Arrow(EntityType.ARROW, h.getLevel());
                arrow.setOwner(shooter); arrow.setPos(p.position().add(i * .01, 1.2, 3));
                arrow.setDeltaMovement(0, 0, -1); h.getLevel().addFreshEntity(arrow); arrows.add(arrow);
            }
            Arrow friendly = new Arrow(EntityType.ARROW, h.getLevel());
            friendly.setOwner(p); friendly.setPos(p.position().add(.5, 1.2, 3));
            friendly.setDeltaMovement(0, 0, -1); h.getLevel().addFreshEntity(friendly); arrows.add(friendly);
            TelekinesisService.bulletStop(p, 5); tick(p, 1);
            h.assertTrue(TelekinesisService.caughtCount(p) == TelekinesisService.MAX_CAUGHT_PROJECTILES,
                    "Capture has a fixed projectile capacity");
            Arrow captured = arrows.stream().filter(Entity::isNoGravity).findFirst().orElseThrow();
            h.assertTrue(Math.abs(captured.getX() - p.getX()) > 1,
                    "Captured projectiles form beside the player rather than blocking the camera");
            h.assertTrue(!friendly.isNoGravity() && friendly.getDeltaMovement().z == -1,
                    "Friendly projectiles continue unmodified");
            TelekinesisService.cancelBulletStop(p);
            h.assertTrue(arrows.stream().noneMatch(Entity::isNoGravity), "Safe cancellation restores projectile gravity");
            h.assertTrue(captured.getOwner() == shooter && captured.getDeltaMovement().lengthSqr() == 0,
                    "Cancellation drops the captured shot without firing it at the owner's aim");
            h.succeed();
        } finally { for (Arrow arrow : arrows) arrow.discard(); cleanup(p, shooter); }
    }

    @GameTest(template = "rewind_empty", batch = "tk.base_release_cost")
    public static void secondBulletPressReleasesBeforeRecoveryAndCostsNoSecondActivation(GameTestHelper h) {
        ServerPlayer p = player(h);
        try {
            h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.TERTIARY, false) == AbilityUseResult.SUCCESS,
                    "The guard starts through the shared ability router");
            int energy = PlayerEnergyService.getEnergy(p);
            h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.TERTIARY, false) == AbilityUseResult.SUCCESS,
                    "An ordinary second press can release before the long recovery");
            h.assertTrue(PlayerEnergyService.getEnergy(p) == energy && !TelekinesisService.hasBulletStop(p),
                    "Releasing cannot consume another activation cost");
            h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.TERTIARY, false) == AbilityUseResult.ON_COOLDOWN,
                    "Recovery begins after the guard ends");
            h.succeed();
        } finally { cleanup(p); }
    }

    @GameTest(template = "rewind_empty", batch = "tk.base_storm")
    public static void stormWarnsBeforeLiftingAndCanEndWithoutAFinale(GameTestHelper h) {
        ServerPlayer p = player(h);
        Mob target = mob(h, p.position().add(0, 0, 5));
        try {
            float health = target.getHealth();
            TelekinesisService.telekineticStorm(p, 5);
            tick(p, TelekinesisService.STORM_GATHER_TICKS - 1);
            h.assertTrue(target.getDeltaMovement().lengthSqr() == 0 && target.getHealth() == health,
                    "The storm gives a readable gathering interval before affecting targets");
            h.assertTrue(!TelekinesisService.slamStorm(p), "The early-release key cannot skip the gathering interval");
            tick(p, 1);
            h.assertTrue(target.getDeltaMovement().y > .1, "The second phase lifts the marked target");
            TelekinesisService.cancelStorm(p);
            h.assertTrue(!TelekinesisService.hasActiveLift(p) && target.getDeltaMovement().lengthSqr() == 0
                    && target.getHealth() == health && !target.isNoGravity(),
                    "Safe cancellation releases the lift without impact damage or persistent flags");
            h.succeed();
        } finally { cleanup(p, target); }
    }

    @GameTest(template = "rewind_empty", batch = "tk.base_long_reach", timeoutTicks = 60)
    public static void longReachExtendsGripAcquisitionWithoutBreakingItsMaintenance(GameTestHelper h) {
        ServerPlayer p = player(h);
        Vec3 position = p.position().add(0, 0, 38);
        var chunk = new net.minecraft.world.level.ChunkPos(BlockPos.containing(position));
        h.getLevel().setChunkForced(chunk.x, chunk.z, true);
        Mob target = mob(h, position);
        aim(p, target);
        h.startSequence().thenWaitUntil(() -> h.assertTrue(h.getLevel().getEntities(p,
                new net.minecraft.world.phys.AABB(p.position(), position).inflate(2), Entity::isAlive).contains(target),
                "The far target's entity section must be available")).thenExecute(() -> {
            try {
                h.assertTrue(TelekinesisService.findGripTarget(p) == null, "Ordinary Grip stops at its 32-block reach");
                p.setData(ModDataAttachments.SKILL_TREES, SkillTreeData.empty().with(ModAbilities.TELEKINESIS_ID.toString(),
                        new SkillTreeData.Build(5, Set.of("telekinesis_long_reach"), Map.of(), 0)));
                h.assertTrue(TelekinesisService.findGripTarget(p) == target && TelekinesisService.grip(p, 5),
                        "Long Reach adds eight real blocks to acquisition");
                tick(p, 3);
                h.assertTrue(TelekinesisService.isGripping(p), "The maintenance range supports the upgraded acquisition range");
            } finally {
                cleanup(p, target);
                h.getLevel().setChunkForced(chunk.x, chunk.z, false);
            }
        }).thenSucceed();
    }

    @GameTest(template = "rewind_empty", batch = "tk.base_wall_impact", timeoutTicks = 60)
    public static void thrownBodyActuallyHitsTheNearbyWallExactlyOnce(GameTestHelper h) {
        ServerPlayer p = player(h);
        Mob target = physicalMob(h, p.position().add(0, 0, 4));
        target.setNoGravity(true);
        BlockPos wall = p.blockPosition().offset(0, 0, 6);
        for (int x = -1; x <= 1; x++) for (int y = -2; y < 5; y++)
            h.getLevel().setBlock(wall.offset(x, y, 0), Blocks.STONE.defaultBlockState(), 3);
        aim(p, target);
        h.assertTrue(TelekinesisService.grip(p, 5) && TelekinesisService.throwGripped(p), "The body is launched toward the nearby wall");
        h.runAfterDelay(25, () -> {
            try {
                h.assertTrue(target.getBoundingBox().maxZ <= wall.getZ() + .01,
                        "Vanilla collision clips the body before the wall, even at launch speed");
                h.assertTrue(Math.abs(target.getHealth() - (target.getMaxHealth() - 9)) < .01,
                        "The actual world collision causes one throw impact, without repeated damage: " + target.getHealth());
                h.succeed();
            } finally {
                for (int x = -1; x <= 1; x++) for (int y = -2; y < 5; y++)
                    h.getLevel().setBlock(wall.offset(x, y, 0), Blocks.AIR.defaultBlockState(), 3);
                cleanup(p, target);
            }
        });
    }

    @GameTest(template = "rewind_empty", batch = "tk.base_ground_impact", timeoutTicks = 60)
    public static void slammedBodyLandsOnRealGroundWithoutExtraVanillaFallDamage(GameTestHelper h) {
        ServerPlayer p = player(h);
        Vec3 start = p.position().add(0, 4, 4);
        BlockPos floor = BlockPos.containing(start).below(5);
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
            h.getLevel().setBlock(floor.offset(x, 0, z), Blocks.STONE.defaultBlockState(), 3);
        Mob target = physicalMob(h, start);
        // Hold it in the air until the slam, so it starts from the height the test means.
        target.setNoGravity(true);
        aim(p, target);
        h.assertTrue(TelekinesisService.grip(p, 5) && TelekinesisService.slamGripped(p), "The body is slammed down toward real ground");
        target.setNoGravity(false);
        h.runAfterDelay(25, () -> {
            try {
                h.assertTrue(target.getY() >= floor.getY() + .99 && target.onGround(),
                        "A high-speed slam lands on the near side of the floor and stays there: " + target.position());
                h.assertTrue(Math.abs(target.getHealth() - (target.getMaxHealth() - 10)) < .01,
                        "Only the single designed slam damage is applied, without a fall-damage race: " + target.getHealth());
                h.assertTrue(!TelekinesisService.isSlammed(target), "Landing consumes the pending impact and its fall protection");
                h.succeed();
            } finally {
                for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
                    h.getLevel().setBlock(floor.offset(x, 0, z), Blocks.AIR.defaultBlockState(), 3);
                cleanup(p, target);
            }
        });
    }

    @GameTest(template = "rewind_empty", batch = "tk.base_cleanup")
    public static void incapacitationEndsPendingControlInsteadOfHoldingItUntilExpiry(GameTestHelper h) {
        ServerPlayer p = player(h);
        Mob target = mob(h, p.position().add(0, 0, 4));
        try {
            aim(p, target);
            h.assertTrue(TelekinesisService.grip(p, 5), "The grip begins");
            TelekinesisService.bulletStop(p, 5);
            PowerRules.rewinding(p, true); tick(p, 1);
            h.assertTrue(!TelekinesisService.isGripping(p) && !TelekinesisService.hasBulletStop(p)
                    && !TelekinesisService.isHeld(target), "Incapacitation clears both control states immediately");
            h.assertTrue(!target.noPhysics && !target.isInvulnerable() && target.isNoAi(),
                    "Cleanup leaves the recipient's original state intact");
            h.succeed();
        } finally { cleanup(p, target); }
    }
}
