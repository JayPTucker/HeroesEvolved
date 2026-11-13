package com.jayptucker.heroesevolved.phasing;

import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.skills.SkillTreeData;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Density's skill tree: every node does what its description says. */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class DensitySkillsGameTests {
    private static ServerPlayer phaser(GameTestHelper h, BlockPos at, String... nodes) {
        var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "density-skills")) {
            @Override public boolean isInvulnerableTo(DamageSource damage) { return false; }
            @Override public void teleportTo(ServerLevel level, double x, double y, double z, float yaw, float pitch) {
                super.teleportTo(level, x, y, z, yaw, pitch); absMoveTo(x, y, z, yaw, pitch);
            }
        };
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(at))); p.setYRot(0); p.setXRot(0);
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(ModAbilities.PHASING_ID).unlock(ModAbilities.PHASING_ID));
        PlayerProgressionService.setLevel(p, 5);
        PlayerEnergyService.restore(p, PlayerEnergyService.getMaximumEnergy(p));
        p.setData(ModDataAttachments.SKILL_TREES, SkillTreeData.empty().with(ModAbilities.PHASING_ID.toString(),
                new SkillTreeData.Build(15, Set.of(nodes), Map.of())));
        try {
            var grace = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            grace.setAccessible(true);
            grace.setInt(p, 0);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        return p;
    }

    private static <T extends Mob> T mob(GameTestHelper h, EntityType<T> type, BlockPos at) {
        T mob = type.create(h.getLevel());
        mob.setNoAi(true); mob.setPersistenceRequired();
        Vec3 feet = Vec3.atBottomCenterOf(h.absolutePos(at));
        mob.moveTo(feet.x, feet.y, feet.z, 180, 0);
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100); mob.setHealth(100);
        h.getLevel().addFreshEntity(mob);
        return mob;
    }

    private static void fill(GameTestHelper h, int x0, int y0, int z0, int x1, int y1, int z1, Block block) {
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++)
            h.getLevel().setBlock(h.absolutePos(new BlockPos(x, y, z)), block.defaultBlockState(), 3);
    }

    private static void hit(ServerPlayer p, LivingEntity t, float amount) {
        t.invulnerableTime = 0;
        t.hurt(p.damageSources().playerAttack(p), amount);
    }

    private static AbilityUseContext context(ServerPlayer p) { return new AbilityUseContext(p, ModAbilities.PHASING_ID, 5, false); }

    private static void done(ServerPlayer p, Entity... others) {
        DensityService.forget(p);
        for (Entity e : others) if (e != null) { if (e instanceof LivingEntity l) DensityService.release(l); e.discard(); }
    }

    @GameTest(template = "rewind_empty", batch = "density.skills.pass")
    public static void passThroughComesOutBehindAndInsideKnowledgeKeepsTheWayIn(GameTestHelper h) {
        fill(h, 0, 1, 0, 11, 1, 11, Blocks.STONE);
        var p = phaser(h, new BlockPos(3, 2, 2), DensitySkills.INSIDE);
        var t = mob(h, EntityType.PILLAGER, new BlockPos(3, 2, 5));
        try {
            h.assertTrue(DensitySkills.passThrough(p, 5), "Pass Through finds the enemy ahead");
            h.assertTrue(p.getZ() > t.getZ() + .5, "You come out behind it: " + p.getZ() + " vs " + t.getZ());
            h.assertTrue(t.getHealth() <= 100 - 9 + .01F, "4 + level damage: " + t.getHealth());
            float hurt = t.getHealth();
            t.heal(5);
            h.assertTrue(t.getHealth() == hurt, "It can't heal for a while");
            h.assertTrue(DensitySkills.knows(p, t), "Inside Knowledge remembers the way in");
            DensityService.setNotch(p, DensityService.GHOST, 200);
            hit(p, t, 4);
            h.assertTrue(hurt - t.getHealth() >= 7.9F, "A Ghost's blow on a body it knows lands at full strength: " + (hurt - t.getHealth()));
            h.succeed();
        } finally { done(p, t); }
    }

    @GameTest(template = "rewind_empty", batch = "density.skills.wall")
    public static void throughTheWallReachesAnEnemyBehindStone(GameTestHelper h) {
        var p = phaser(h, new BlockPos(3, 2, 2), DensitySkills.THROUGH_WALL);
        fill(h, 2, 2, 4, 4, 4, 4, Blocks.STONE);
        var t = mob(h, EntityType.PILLAGER, new BlockPos(3, 2, 5));
        try {
            h.assertTrue(DensityService.reach(p, 2), "The hand finds the enemy behind the wall");
            h.assertTrue(t.getHealth() <= 100 - 10 + .01F, "5 + level, +3 for coming through the wall: " + t.getHealth());
            h.succeed();
        } finally { done(p, t); }
    }

    @GameTest(template = "rewind_empty", batch = "density.skills.ambush")
    public static void steppingOutOfAWallPrimesAFreeHarderReach(GameTestHelper h) {
        var p = phaser(h, new BlockPos(3, 2, 2), DensitySkills.AMBUSH);
        var t = mob(h, EntityType.PILLAGER, new BlockPos(3, 2, 4));
        try {
            h.assertTrue(!DensitySkills.ambush(context(p)), "No ambush before a wall");
            DensityService.pass(p, p.position(), p.position());
            h.assertTrue(DensitySkills.primed(p), "Out of a wall, the hand is primed");
            h.assertTrue(DensitySkills.ambush(context(p)), "The primed reach goes as a follow-up (free, no cooldown)");
            h.assertTrue(t.getHealth() <= 100 - 14 + .01F, "5 + level, +4 for the ambush: " + t.getHealth());
            h.assertTrue(!DensitySkills.primed(p) && !DensitySkills.ambush(context(p)), "One ambush per wall");
            h.succeed();
        } finally { done(p, t); }
    }

    @GameTest(template = "rewind_empty", batch = "density.skills.network")
    public static void ghostNetworkLeavesTheWallOpenBehindYou(GameTestHelper h) {
        fill(h, 1, 2, 4, 5, 4, 5, Blocks.STONE);
        var p = phaser(h, new BlockPos(3, 2, 2), DensitySkills.NETWORK);
        var villager = mob(h, EntityType.VILLAGER, new BlockPos(3, 2, 2));
        try {
            Vec3 from = p.position(), to = Passage.through(p, new Vec3(0, 0, 1), 6);
            h.assertTrue(to != null, "There is a way through");
            DensityService.pass(p, from, to);
            h.assertTrue(DensityService.fullGhost(p), "You come out at Full Ghost");
            villager.teleportTo(from.x, from.y, from.z);
            DensitySkills.tick(h.getLevel().getServer());
            h.assertTrue(villager.getZ() > h.absolutePos(new BlockPos(3, 2, 6)).getZ(), "The villager follows you through the open wall: " + villager.getZ());
            h.succeed();
        } finally { done(p, villager); }
    }

    @GameTest(template = "rewind_empty", batch = "density.skills.carry")
    public static void phaseCarryBringsFriendsAndLeavesThemLoose(GameTestHelper h) {
        fill(h, 1, 2, 4, 5, 4, 5, Blocks.STONE);
        var p = phaser(h, new BlockPos(3, 2, 2), DensitySkills.CARRY);
        var villager = mob(h, EntityType.VILLAGER, new BlockPos(2, 2, 2));
        try {
            h.assertTrue(DensitySkills.carry(p), "Through the wall");
            h.assertTrue(villager.getZ() > h.absolutePos(new BlockPos(2, 2, 6)).getZ(), "The friend came too: " + villager.getZ());
            h.assertTrue(DensitySkills.loosened(villager), "Shots pass through a carried friend for a while");
            h.succeed();
        } finally { done(p, villager); }
    }

    @GameTest(template = "rewind_empty", batch = "density.skills.harden")
    public static void hardenBanksBlowsAndReboundRootsWithCraterLanding(GameTestHelper h) {
        fill(h, 0, 1, 0, 11, 1, 11, Blocks.STONE);
        var p = phaser(h, new BlockPos(3, 2, 2), DensitySkills.CRATER);
        var t = mob(h, EntityType.PILLAGER, new BlockPos(3, 2, 4));
        var zombie = EntityType.ZOMBIE.create(h.getLevel());
        try {
            h.assertTrue(DensitySkills.harden(p) && DensityService.notch(p) == DensityService.FULL_DENSE, "Harden: Full Dense");
            p.hurt(p.damageSources().mobAttack(zombie), 6);
            h.assertTrue(DensitySkills.hardened(p) >= 5.9F, "The blow is banked: " + DensitySkills.hardened(p));
            h.assertTrue(DensitySkills.followUp(context(p), 1, ""), "The second press is Rebound");
            h.assertTrue(t.getHealth() < 100, "Rebound's shockwave hurts the enemy beside you");
            h.assertTrue(DensityService.embedded(t), "Crater Landing roots it instead of throwing it");
            h.assertTrue(DensitySkills.hardened(p) < 0 && !DensitySkills.followUp(context(p), 1, ""), "Rebound spends the weight");
            h.succeed();
        } finally { done(p, t); }
    }

    @GameTest(template = "rewind_empty", batch = "density.skills.entomb", timeoutTicks = 120)
    public static void pileDriverSetInStoneAndEntombed(GameTestHelper h) {
        fill(h, 0, -1, 0, 11, 1, 11, Blocks.STONE);
        var p = phaser(h, new BlockPos(3, 2, 2), DensitySkills.PILE_DRIVER, DensitySkills.SET_IN_STONE, DensitySkills.ENTOMBED);
        var t = mob(h, EntityType.PILLAGER, new BlockPos(3, 2, 4));
        double ground = t.getY();
        try {
            DensityService.setNotch(p, DensityService.FULL_DENSE, 400);
            t.setOnGround(true);
            hit(p, t, 4);
            h.assertTrue(DensityService.embedded(t) && DensityService.holder(t) == p, "A Full Dense punch drives it into the ground");
            hit(p, t, 4);
            p.setXRot(20);
            h.assertTrue(DensitySkills.entomb(context(p)), "Embed again at a body you hold: the ground closes over it");
            h.assertTrue(DensityService.buried(t) && t.getY() < ground - 1.8, "Right under: " + (ground - t.getY()));
        } catch (RuntimeException e) { done(p, t); throw e; }
        h.runAfterDelay(DensityGeometry.ENTOMB_TICKS + 10, () -> {
            try {
                h.assertTrue(t.getHealth() < 100 - 8, "It took damage while buried: " + t.getHealth());
                h.assertTrue(!DensityService.embedded(t), "Then the ground lets it go");
                h.assertTrue(t.getY() >= ground - .1, "And throws it back up: " + t.getY() + " vs " + ground);
                h.succeed();
            } finally { done(p, t); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "density.skills.set", timeoutTicks = 120)
    public static void setInStoneHoldsAnEmbedLongerWithEveryDenseBlow(GameTestHelper h) {
        fill(h, 0, 1, 0, 11, 1, 11, Blocks.STONE);
        var p = phaser(h, new BlockPos(3, 2, 2), DensitySkills.SET_IN_STONE);
        var t = mob(h, EntityType.PILLAGER, new BlockPos(3, 2, 4));
        try {
            h.assertTrue(DensityService.embed(t, p, 20, false, .45), "Embedded for a second");
            DensityService.setNotch(p, DensityService.DENSE, 400);
            hit(p, t, 1); hit(p, t, 1);
        } catch (RuntimeException e) { done(p, t); throw e; }
        h.runAfterDelay(40, () -> {
            try {
                h.assertTrue(DensityService.embedded(t), "Two blows at Dense held it two seconds longer");
                h.succeed();
            } finally { done(p, t); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "density.skills.sinkhole", timeoutTicks = 80)
    public static void sinkholeWarnsThenSwallowsWhoeverStandsInIt(GameTestHelper h) {
        fill(h, 0, 1, 0, 11, 1, 11, Blocks.STONE);
        var p = phaser(h, new BlockPos(3, 2, 2), DensitySkills.SINKHOLE);
        var t = mob(h, EntityType.PILLAGER, new BlockPos(3, 2, 7));
        try {
            DensitySkills.sinkholeAt(p, Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 2, 7))));
            h.assertTrue(!DensityService.embedded(t), "Nothing happens during the warning");
        } catch (RuntimeException e) { done(p, t); throw e; }
        h.runAfterDelay(DensityGeometry.SINKHOLE_WARN + 8, () -> {
            try {
                h.assertTrue(DensityService.embedded(t), "Then the ground goes soft and takes it");
                h.succeed();
            } finally { done(p, t); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "density.skills.uproot")
    public static void uprootTearsAnEmbeddedEnemyOutOfTheGround(GameTestHelper h) {
        fill(h, 1, 1, 1, 6, 3, 6, Blocks.STONE);
        var p = phaser(h, new BlockPos(3, 4, 3), DensitySkills.UPROOT);
        p.setOnGround(true);
        var t = mob(h, EntityType.PILLAGER, new BlockPos(3, 4, 4));
        try {
            h.assertTrue(DensityService.sinkOrSurface(p, 5), "Down into the stone");
            h.assertTrue(DensityService.embed(t, p, 400, false, .45), "The enemy above is held in the ground");
            h.assertTrue(DensityService.sinkOrSurface(p, 5), "Up again, under it");
            h.assertTrue(!DensityService.embedded(t), "It is torn out of the ground");
            h.assertTrue(t.getHealth() <= 100 - 22 + .01F, "The uppercut deals double: " + t.getHealth());
            h.assertTrue(t.getDeltaMovement().y > 1.5, "And throws it twice as high: " + t.getDeltaMovement().y);
            float hurt = t.getHealth();
            t.heal(5);
            h.assertTrue(t.getHealth() == hurt, "It can't heal for a moment");
            h.succeed();
        } finally { done(p, t); }
    }

    @GameTest(template = "rewind_empty", batch = "density.skills.fade")
    public static void fadeThroughHurtsEveryEnemyYouDriftThrough(GameTestHelper h) {
        fill(h, 0, 1, 0, 11, 1, 11, Blocks.STONE);
        var p = phaser(h, new BlockPos(3, 2, 3));
        var t = mob(h, EntityType.PILLAGER, new BlockPos(3, 2, 3));
        try {
            h.assertTrue(DensitySkills.fade(p, 5) && DensityService.fullGhost(p), "Fade Through: Full Ghost");
            DensitySkills.tick(h.getLevel().getServer());
            h.assertTrue(t.getHealth() <= 100 - 5.5F + .01F, "The enemy you drift through is hurt: " + t.getHealth());
            h.assertTrue(t.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && DensityService.sealed(t), "Slowed, and it can't heal");
            float hurt = t.getHealth();
            DensitySkills.tick(h.getLevel().getServer());
            h.assertTrue(t.getHealth() == hurt, "Once per enemy");
            h.succeed();
        } finally { done(p, t); }
    }

    @GameTest(template = "rewind_empty", batch = "density.skills.grasp")
    public static void graspFromBelowPullsAnEnemyDownByTheAnkles(GameTestHelper h) {
        fill(h, 1, 1, 1, 6, 3, 6, Blocks.STONE);
        var p = phaser(h, new BlockPos(3, 4, 3), DensitySkills.GRASP);
        p.setOnGround(true);
        var t = mob(h, EntityType.PILLAGER, new BlockPos(5, 4, 3));
        t.setOnGround(true);
        try {
            h.assertTrue(DensityService.sinkOrSurface(p, 5), "Down into the stone");
            h.assertTrue(DensityService.reach(p, 5), "Reach Through from below grabs the enemy above");
            h.assertTrue(DensityService.embedded(t) && DensityService.sealed(t), "Waist-deep, and it can't heal");
            h.succeed();
        } finally { done(p, t); }
    }

    @GameTest(template = "rewind_empty", batch = "density.skills.deep_fall", skyAccess = true)
    public static void deepFallAtDenseLandsAsAShockwave(GameTestHelper h) {
        fill(h, 0, 1, 0, 8, 1, 8, Blocks.STONE);
        fill(h, 0, 5, 0, 8, 5, 8, Blocks.STONE);
        var p = phaser(h, new BlockPos(3, 6, 3), DensitySkills.PHASE_FALL, DensitySkills.DEEP_FALL);
        var zombie = mob(h, EntityType.ZOMBIE, new BlockPos(4, 2, 3));
        try {
            DensityService.setNotch(p, DensityService.DENSE, 200);
            h.assertTrue(DensitySkills.phaseFall(p, false), "Through the floor");
            h.assertTrue(p.getY() < h.absolutePos(new BlockPos(3, 5, 3)).getY(), "Below it now: " + p.getY());
            h.assertTrue(zombie.getHealth() < 100, "Landing at Dense shakes the ground");
            h.succeed();
        } finally { done(p, zombie); }
    }

    @GameTest(template = "rewind_empty", batch = "density.skills.untouchable")
    public static void untouchableSlipsAFatalBlowOnceEveryThirtySeconds(GameTestHelper h) {
        var p = phaser(h, new BlockPos(3, 2, 3), DensitySkills.UNTOUCHABLE);
        var zombie = EntityType.ZOMBIE.create(h.getLevel());
        try {
            p.setHealth(10);
            p.hurt(p.damageSources().mobAttack(zombie), 8);
            h.assertTrue(p.getHealth() == 10, "The blow passes through: " + p.getHealth());
            h.assertTrue(DensityService.fullGhost(p), "You slip to Full Ghost");
            DensityService.setNotch(p, DensityService.NEUTRAL, 0);
            p.invulnerableTime = 0;
            p.hurt(p.damageSources().mobAttack(zombie), 8);
            h.assertTrue(p.getHealth() < 10, "Only once every thirty seconds");
            h.succeed();
        } finally { done(p); }
    }
}
