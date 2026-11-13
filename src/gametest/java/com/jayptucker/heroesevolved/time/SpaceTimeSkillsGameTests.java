package com.jayptucker.heroesevolved.time;

import com.jayptucker.heroesevolved.ability.AbilitySlot;
import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.skills.SkillTreeData;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Space-Time's skill tree: every node does what its description says. */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class SpaceTimeSkillsGameTests {
    private static ServerPlayer player(GameTestHelper h, String... nodes) {
        var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "time-skills")) {
            @Override public void teleportTo(net.minecraft.server.level.ServerLevel level, double x, double y, double z, float yaw, float pitch) {
                super.teleportTo(level, x, y, z, yaw, pitch); absMoveTo(x, y, z, yaw, pitch);
            }
            @Override public void teleportTo(double x, double y, double z) {
                super.teleportTo(x, y, z); absMoveTo(x, y, z, getYRot(), getXRot());
            }
            @Override public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source) { return false; }
        };
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 2, 2))));
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(ModAbilities.TIME_MANIPULATION_ID).unlock(ModAbilities.TIME_MANIPULATION_ID));
        PlayerProgressionService.setLevel(p, 5); p.setOnGround(true);
        p.setData(ModDataAttachments.SKILL_TREES, SkillTreeData.empty().with(ModAbilities.TIME_MANIPULATION_ID.toString(),
                new SkillTreeData.Build(15, Set.of(nodes), Map.of())));
        return p;
    }

    private static <T extends Mob> T mob(GameTestHelper h, EntityType<T> type, Vec3 at) {
        T mob = type.create(h.getLevel());
        mob.setPos(at); mob.setNoAi(true);
        h.getLevel().addFreshEntity(mob);
        return mob;
    }

    private static void hit(ServerPlayer p, LivingEntity t, float amount) {
        t.invulnerableTime = 0;
        t.hurt(p.damageSources().playerAttack(p), amount);
    }

    private static void done(ServerPlayer p, Entity... others) {
        SpaceTimeService.forget(p); TimeStopService.stop(p); TimeSlowService.stop(p);
        for (Entity e : others) if (e != null) e.discard();
    }

    private static boolean near(float a, float b) { return Math.abs(a - b) < .05F; }

    @GameTest(template = "rewind_empty", batch = "time.skills.still_point")
    public static void stillPointMakesThreeFoldsFreeWhileStillWorldHolds(GameTestHelper h) {
        var p = player(h, SpaceTimeSkills.STILL_POINT);
        var fold = ModAbilities.TIME_MANIPULATION.get().actions().get(AbilitySlot.PRIMARY);
        var context = new AbilityUseContext(p, ModAbilities.TIME_MANIPULATION_ID, 5, false);
        try {
            int cost = fold.energyCost(context);
            h.assertTrue(cost > 0, "Fold Space costs stamina outside a Still World");
            TimeStopService.start(p, 5);
            h.assertTrue(fold.energyCost(context) == 0 && fold.cooldownTicks(context) == 0, "Inside your Still World, folds are free");
            h.assertTrue(!fold.cooldownId(context).equals(fold.definition().id()), "Free folds keep a cooldown of their own");
            for (int i = 0; i < SpaceTimeSkills.FREE_FOLDS; i++) SpaceTimeSkills.folded(p, p.position(), p.position(), true, null);
            h.assertTrue(fold.energyCost(context) == cost, "Only three folds are free");
            h.succeed();
        } finally { done(p); }
    }

    @GameTest(template = "rewind_empty", batch = "time.skills.stasis")
    public static void selectiveStasisStoresBlowsAndASnapLandsThemCompounded(GameTestHelper h) {
        var p = player(h, SpaceTimeSkills.COMPOUND);
        var target = mob(h, EntityType.PILLAGER, p.position().add(0, 0, 3));
        try {
            float before = target.getHealth();
            h.assertTrue(SpaceTimeService.suspend(p, target, 100), "Selective Stasis holds its target");
            hit(p, target, 4); hit(p, target, 4);
            h.assertTrue(target.getHealth() == before, "Blows on a creature in stasis wait");
            h.assertTrue(SpaceTimeService.releaseStasis(p), "A second press lets it go");
            // Two stored blows of 4, grown by Compound Interest (+10% each).
            h.assertTrue(near(before - target.getHealth(), 8 * 1.2F), "Stored blows land together, compounded: " + (before - target.getHealth()));
            h.assertTrue(!TimeStopService.isStopped(target), "Released creatures move again");
            h.succeed();
        } finally { done(p, target); }
    }

    @GameTest(template = "rewind_empty", batch = "time.skills.seal")
    public static void stasisSealHoldsShotsAndCreaturesThenThrowsThem(GameTestHelper h) {
        var p = player(h);
        var wall = BlockPos.containing(p.getEyePosition().add(0, 0, 6));
        h.getLevel().setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
        Arrow shot = null; Mob raider = null;
        try {
            h.assertTrue(SpaceTimeSkills.seal(p), "A seal is set where you aim");
            Vec3 center = Vec3.atCenterOf(wall).add(0, 0, -2.3);
            shot = new Arrow(EntityType.ARROW, h.getLevel());
            shot.setPos(center); shot.setDeltaMovement(0, 0, -1.2);
            h.getLevel().addFreshEntity(shot);
            raider = mob(h, EntityType.PILLAGER, center.add(.5, -1.2, 0));
            SpaceTimeSkills.tick(h.getLevel().getServer());
            h.assertTrue(shot.getDeltaMovement().lengthSqr() < 1e-6, "A shot in the seal hangs still");
            h.assertTrue(TimeStopService.isStopped(raider), "An enemy that walks in stops");
            h.assertTrue(SpaceTimeSkills.throwSeal(p), "A second press throws it");
            h.assertTrue(shot.getDeltaMovement().z > .5, "The shot flies where you look: " + shot.getDeltaMovement());
            h.assertTrue(!TimeStopService.isStopped(raider), "Thrown creatures move again");
            h.succeed();
        } finally { done(p, shot, raider); h.getLevel().setBlockAndUpdate(wall, Blocks.AIR.defaultBlockState()); }
    }

    @GameTest(template = "rewind_empty", batch = "time.skills.behind")
    public static void behindYouFoldsBehindAnEnemyAndTheNextHitLandsHarder(GameTestHelper h) {
        var p = player(h, SpaceTimeSkills.BEHIND_YOU);
        var target = mob(h, EntityType.PILLAGER, p.position().add(0, 0, 5));
        target.setYRot(180); target.setYHeadRot(180); target.yBodyRot = 180;
        try {
            Vec3 spot = SpaceTimeSkills.behind(p, 10);
            h.assertTrue(spot != null && spot.z > target.getZ() + 1, "Fold Space lands behind the creature it is aimed at: " + spot);
            float before = target.getHealth();
            hit(p, target, 4);
            h.assertTrue(near(before - target.getHealth(), 6), "The next hit lands 50% harder: " + (before - target.getHealth()));
            before = target.getHealth();
            hit(p, target, 4);
            h.assertTrue(near(before - target.getHealth(), 4), "Only the first hit");
            h.succeed();
        } finally { done(p, target); }
    }

    @GameTest(template = "rewind_empty", batch = "time.skills.snap")
    public static void snapShutDragsEnemiesIntoTheHoleYouLeft(GameTestHelper h) {
        var p = player(h, SpaceTimeSkills.SNAP_SHUT);
        Vec3 from = p.position();
        var near = mob(h, EntityType.PILLAGER, from.add(2, 0, 0));
        var far = mob(h, EntityType.PILLAGER, from.add(-5, 0, 3));
        try {
            float health = near.getHealth();
            SpaceTimeSkills.folded(p, from, from.add(0, 0, 6), false, null);
            h.assertTrue(near.getHealth() < health && near.getDeltaMovement().x < 0, "An enemy beside the hole is dragged in and hurt");
            h.assertTrue(near.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "and slowed");
            h.assertTrue(far.getHealth() == far.getMaxHealth(), "Enemies out of reach are untouched");
            h.succeed();
        } finally { done(p, near, far); }
    }

    @GameTest(template = "rewind_empty", batch = "time.skills.edge", timeoutTicks = 60)
    public static void momentsEdgeCutsEachEnemyAndTheyFeelEveryCutAtOnce(GameTestHelper h) {
        var p = player(h);
        var first = mob(h, EntityType.PILLAGER, p.position().add(1, 0, 3));
        var second = mob(h, EntityType.PILLAGER, p.position().add(-1, 0, 6));
        float full = first.getHealth();
        try {
            h.assertTrue(SpaceTimeSkills.canEdge(p) && SpaceTimeSkills.edge(p, 5), "Moment's Edge starts with enemies in reach");
            h.assertTrue(TimeStopService.isStopped(first) && TimeStopService.isStopped(second), "Time stops for everyone it will cut");
        } catch (RuntimeException e) { done(p, first, second); throw e; }
        h.runAfterDelay(3, () -> {
            try {
                h.assertTrue(first.getHealth() == full, "Cuts wait while time is stopped");
            } catch (RuntimeException e) { done(p, first, second); throw e; }
        });
        h.runAfterDelay(14, () -> {
            try {
                h.assertTrue(!SpaceTimeSkills.cutting(p), "The edge has finished");
                h.assertTrue(near(full - first.getHealth(), 11) && near(full - second.getHealth(), 11),
                        "Each enemy feels its cut (6 + level) when time resumes: " + (full - first.getHealth()) + ", " + (full - second.getHealth()));
                h.assertTrue(p.position().distanceTo(second.position()) < 2.5, "You land by the last one");
                h.succeed();
            } finally { done(p, first, second); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "time.skills.forward")
    public static void fastForwardRunsAnEnemysClockAndHurriesAFriendsHealing(GameTestHelper h) {
        var p = player(h, SpaceTimeSkills.ERAS_END);
        var enemy = mob(h, EntityType.PILLAGER, p.position().add(0, 0, 3));
        var friend = mob(h, EntityType.VILLAGER, p.position().add(2, 0, 2));
        try {
            enemy.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 1));
            hit(p, enemy, 4); hit(p, enemy, 4);
            float before = enemy.getHealth();
            SpaceTimeSkills.forwardEnemy(p, enemy);
            // Poison II hurts every 12 ticks: 100 / 12 for the five seconds, plus half the 8 dealt (Era's End).
            float expected = 100 / 12F + 4;
            h.assertTrue(Math.abs(before - enemy.getHealth() - expected) < .1F, "Five seconds of poison and the replayed blows land at once: " + (before - enemy.getHealth()));
            h.assertTrue(enemy.getEffect(MobEffects.POISON).getDuration() <= 100, "Its poison has run five seconds closer to ending");
            h.assertTrue(enemy.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && enemy.hasEffect(MobEffects.WEAKNESS), "It is left weary");
            friend.setHealth(10);
            friend.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 300, 0));
            friend.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0));
            SpaceTimeSkills.forwardFriend(p, friend);
            h.assertTrue(near(friend.getHealth(), 12), "A friend's next five seconds of Regeneration arrive at once: " + friend.getHealth());
            h.assertTrue(!friend.hasEffect(MobEffects.POISON), "and their poison runs out");
            h.succeed();
        } finally { done(p, enemy, friend); }
    }

    @GameTest(template = "rewind_empty", batch = "time.skills.foreknowledge")
    public static void foreknowledgeRemembersWhoHurtYouInTheUndoneSeconds(GameTestHelper h) {
        var p = player(h, SpaceTimeSkills.FOREKNOWLEDGE);
        var attacker = mob(h, EntityType.PILLAGER, p.position().add(0, 0, 2));
        var bystander = mob(h, EntityType.PILLAGER, p.position().add(2, 0, 0));
        try {
            // A new player ignores damage for three seconds; a test has to take that away.
            var grace = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            grace.setAccessible(true);
            grace.setInt(p, 0);
            p.hurt(p.damageSources().mobAttack(attacker), 2);
            SpaceTimeSkills.remember(p, TimeGeometry.SECOND_TICKS);
            h.assertTrue(SpaceTimeSkills.marked(p, attacker) && !SpaceTimeSkills.marked(p, bystander), "Only whoever hurt you is remembered");
            float before = attacker.getHealth();
            hit(p, attacker, 4);
            h.assertTrue(near(before - attacker.getHealth(), 5.2F), "Hits on them land 30% harder: " + (before - attacker.getHealth()));
            h.succeed();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        } finally { done(p, attacker, bystander); }
    }

    @GameTest(template = "rewind_empty", batch = "time.skills.shared_hour")
    public static void sharedHourTakesAFriendBackThroughTheirOwnFiveSeconds(GameTestHelper h) {
        var friend = player(h);
        Vec3 then = friend.position();
        long now = friend.getServer().overworld().getGameTime();
        try {
            friend.setHealth(16);
            SpaceTimeService.record(friend, now - 100);
            friend.teleportTo(then.x, then.y, then.z + 4);
            friend.setHealth(8);
            SpaceTimeService.record(friend, now);
            h.assertTrue(SpaceTimeService.rewindPersonal(friend), "A friend with five seconds of history can be taken back");
            h.assertTrue(friend.position().distanceTo(then) < .1 && friend.getHealth() == 16, "They stand where they were, with the health they had");
            h.succeed();
        } finally { done(friend); }
    }

    @GameTest(template = "rewind_empty", batch = "time.skills.anchor")
    public static void spatialAnchorBringsYourPetHome(GameTestHelper h) {
        var p = player(h);
        var wolf = EntityType.WOLF.create(h.getLevel());
        wolf.setPos(p.position().add(1, 0, 0));
        wolf.setTame(true, false); wolf.setOwnerUUID(p.getUUID());
        h.getLevel().addFreshEntity(wolf);
        Vec3 home = p.position().add(0, 0, 5);
        try {
            p.setData(ModDataAttachments.SPACE_TIME_ANCHOR, new SpaceTimeService.Anchor(true,
                    h.getLevel().dimension().location().toString(), home.x, home.y, home.z, 0, 0));
            h.assertTrue(SpaceTimeService.recall(p), "The anchor takes you home");
            h.assertTrue(p.position().distanceTo(home) < .1, "You arrive at the anchor");
            h.assertTrue(wolf.position().distanceTo(home.add(1, 0, 0)) < .1, "Your pet comes with you, still beside you: " + wolf.position());
            h.succeed();
        } finally { done(p, wolf); }
    }
}
