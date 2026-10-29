package com.jayptucker.heroesevolved.regeneration;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.intro.PowerIntroHistory;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The reworked Regeneration kit: Regrowth, the stronger Mending and Blood Gift, Reckless, Blood Bond, Second Life. */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class RegenerationGameTests {
    private static ServerPlayer claire(GameTestHelper h, int level) {
        return claire(h, level, 2);
    }

    /** {@code height} above the arena floor; long-range checks run high, clear of the arena's walls. */
    private static ServerPlayer claire(GameTestHelper h, int level, int height) {
        ServerPlayer p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "claire-test")) {
            @Override public boolean isInvulnerableTo(DamageSource damage) { return false; }
            // FakePlayers skip death entirely; a real player's death starts with this event.
            @Override public void die(DamageSource source) {
                net.neoforged.neoforge.common.CommonHooks.onLivingDeath(this, source);
            }
            // FakePlayers ignore connection teleports; move them directly.
            @Override public void teleportTo(net.minecraft.server.level.ServerLevel level, double x, double y, double z, float yaw, float pitch) {
                absMoveTo(x, y, z, yaw, pitch);
            }
        };
        try {
            Connection connection = p.connection.getConnection();
            Field channel = Connection.class.getDeclaredField("channel"); channel.setAccessible(true);
            if (connection.channel() == null) channel.set(connection, new EmbeddedChannel());
            Field grace = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            grace.setAccessible(true); grace.setInt(p, 0);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, height, 3)))); p.setYRot(0); p.setXRot(0);
        p.setData(ModDataAttachments.POWER_INTRO_HISTORY, PowerIntroHistory.empty().with(ModAbilities.REGENERATION_ID));
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty()
                .assignDormant(ModAbilities.REGENERATION_ID).unlock(ModAbilities.REGENERATION_ID));
        PlayerProgressionService.setLevel(p, level);
        PlayerEnergyService.restore(p, PlayerEnergyService.getMaximumEnergy(p));
        PlayerList list = p.getServer().getPlayerList();
        try {
            Field players = PlayerList.class.getDeclaredField("players"); players.setAccessible(true);
            Field byId = PlayerList.class.getDeclaredField("playersByUUID"); byId.setAccessible(true);
            @SuppressWarnings("unchecked") var all = (List<ServerPlayer>) players.get(list);
            @SuppressWarnings("unchecked") var index = (Map<UUID, ServerPlayer>) byId.get(list);
            all.add(p); index.put(p.getUUID(), p);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        return p;
    }

    private static void done(ServerPlayer p, Entity... others) {
        RegenerationService.forget(p);
        BodyService.forget(p);
        PlayerList list = p.getServer().getPlayerList();
        try {
            Field players = PlayerList.class.getDeclaredField("players"); players.setAccessible(true);
            Field byId = PlayerList.class.getDeclaredField("playersByUUID"); byId.setAccessible(true);
            ((List<?>) players.get(list)).remove(p);
            ((Map<?, ?>) byId.get(list)).remove(p.getUUID());
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        for (Entity other : others) other.discard();
    }

    private static LivingEntity cow(GameTestHelper h, Vec3 at) {
        var cow = EntityType.COW.create(h.getLevel());
        cow.setNoAi(true); cow.setNoGravity(true); cow.moveTo(at.x, at.y, at.z, 180, 0);
        h.getLevel().addFreshEntity(cow);
        return cow;
    }

    private static void aim(ServerPlayer player, LivingEntity target) {
        Vec3 delta = target.getBoundingBox().getCenter().subtract(player.getEyePosition());
        player.setYRot((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
        player.setXRot((float) -Math.toDegrees(Math.atan2(delta.y, delta.horizontalDistance())));
    }

    @GameTest(template = "rewind_empty", batch = "regen.regrowth")
    public static void woundsBecomeRegrowthAndMendingSpendsIt(GameTestHelper h) {
        ServerPlayer p = claire(h, 3);
        try {
            p.hurt(p.damageSources().generic(), 6);
            h.assertTrue(RegenerationService.regrowth(p) == 12, "Six damage becomes twelve Regrowth, got " + RegenerationService.regrowth(p));
            RegenerationService.addRegrowth(p, 60);
            p.setHealth(p.getMaxHealth() - 1);
            RegenerationService.rapidMending(p, 3);
            h.assertTrue(RegenerationService.regrowth(p) == 0, "Rapid Mending spends all Regrowth");
            h.assertTrue(p.getAbsorptionAmount() > 0, "Healing past full becomes Absorption");
            h.succeed();
        } finally { done(p); }
    }

    @GameTest(template = "rewind_empty", batch = "regen.gift")
    public static void bloodGiftReachesTwelveBlocksAndFeedsRegrowth(GameTestHelper h) {
        ServerPlayer p = claire(h, 2, 40);
        LivingEntity hurt = cow(h, p.position().add(0, 0, 11));
        try {
            hurt.setHealth(2);
            aim(p, hurt);
            float before = p.getHealth();
            h.assertTrue(RegenerationService.bloodGift(p, 2), "A friend eleven blocks away is in reach");
            h.assertTrue(hurt.getHealth() > 2, "Her blood heals them");
            h.assertTrue(p.getHealth() < before && RegenerationService.regrowth(p) >= 6, "The cut costs her health and feeds Regrowth");
            h.succeed();
        } finally { done(p, hurt); }
    }

    @GameTest(template = "rewind_empty", batch = "regen.reckless")
    public static void indestructiblePunchesHardAndPushesArrowsOut(GameTestHelper h) {
        ServerPlayer p = claire(h, 4);
        LivingEntity dummy = cow(h, p.position().add(0, 0, 2));
        try {
            RegenerationService.indestructible(p, 4);
            float health = p.getHealth();
            dummy.hurt(p.damageSources().playerAttack(p), 1);
            h.assertTrue(dummy.getMaxHealth() - dummy.getHealth() >= 7, "A reckless punch lands much harder");
            h.assertTrue(p.getHealth() < health, "And breaks her own hand a little");
            Arrow arrow = new Arrow(EntityType.ARROW, h.getLevel());
            arrow.setPos(p.position().add(0, 1, 2));
            h.getLevel().addFreshEntity(arrow);
            float beforeArrow = p.getHealth();
            p.invulnerableTime = 0;
            p.hurt(p.damageSources().arrow(arrow, null), 6);
            h.assertTrue(beforeArrow - p.getHealth() <= 3.01F, "Arrows do half damage while Indestructible");
            var dropped = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(p.blockPosition()).inflate(2),
                    item -> item.getItem().is(Items.ARROW));
            h.assertTrue(!dropped.isEmpty(), "The arrow is pushed back out and drops at her feet");
            dropped.forEach(Entity::discard);
            h.succeed();
        } finally { done(p, dummy); }
    }

    @GameTest(template = "rewind_empty", batch = "regen.bond")
    public static void bloodBondDrawsAShareOfAFriendsWound(GameTestHelper h) {
        ServerPlayer p = claire(h, 4);
        LivingEntity friend = cow(h, p.position().add(3, 0, 0));
        try {
            RegenerationService.healingAura(p, 4);
            float friendBefore = friend.getHealth(), claireBefore = p.getHealth();
            friend.hurt(h.getLevel().damageSources().generic(), 8);
            h.assertTrue(friendBefore - friend.getHealth() < 6, "The bonded friend takes less of the wound");
            h.assertTrue(p.getHealth() < claireBefore, "Claire takes a share of it");
            h.succeed();
        } finally { done(p, friend); }
    }

    @GameTest(template = "rewind_empty", batch = "regen.second_life")
    public static void secondLifeSavesAFriendFromDeathOnce(GameTestHelper h) {
        ServerPlayer p = claire(h, 5);
        LivingEntity friend = cow(h, p.position().add(0, 0, 4));
        try {
            aim(p, friend);
            h.assertTrue(!RegenerationService.secondLife(p), "It needs 50 Regrowth");
            RegenerationService.addRegrowth(p, 60);
            h.assertTrue(RegenerationService.secondLife(p), "With Regrowth, her blood goes into the friend");
            h.assertTrue(RegenerationService.regrowth(p) == 10, "It costs 50 Regrowth");
            friend.hurt(h.getLevel().damageSources().generic(), 100);
            h.assertTrue(friend.isAlive() && friend.getHealth() > 0, "The first killing blow leaves them standing");
            friend.invulnerableTime = 0;
            friend.hurt(h.getLevel().damageSources().generic(), 100);
            h.assertTrue(!friend.isAlive(), "Only once");
            h.succeed();
        } finally { done(p, friend); }
    }

    @GameTest(template = "rewind_empty", batch = "regen.fall")
    public static void fallsHurtHalfAsMuch(GameTestHelper h) {
        ServerPlayer p = claire(h, 1);
        try {
            float before = p.getHealth();
            p.causeFallDamage(13, 1, p.damageSources().fall());
            float taken = before - p.getHealth();
            h.assertTrue(taken > 0 && taken <= 5.01F, "A ten-damage fall costs her five at most, took " + taken);
            h.succeed();
        } finally { done(p); }
    }

    // ------------------------------------------------------------------
    // Severed limbs and the downed state
    // ------------------------------------------------------------------

    @GameTest(template = "rewind_empty", timeoutTicks = 140, batch = "regen.sever")
    public static void anAxeTakesALimbThatGrowsBack(GameTestHelper h) {
        ServerPlayer p = claire(h, 5);
        var brute = EntityType.VINDICATOR.create(h.getLevel());
        brute.setNoAi(true);
        brute.moveTo(p.getX() + 1.5, p.getY(), p.getZ(), 90, 0);
        brute.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(Items.DIAMOND_AXE));
        h.getLevel().addFreshEntity(brute);
        p.hurt(h.getLevel().damageSources().mobAttack(brute), 9);
        boolean anyMissing = false;
        for (BodyService.Limb limb : BodyService.Limb.values()) anyMissing |= BodyService.missing(p, limb);
        h.assertTrue(anyMissing, "A heavy axe blow takes a limb");
        h.assertTrue(p.isAlive() && p.getHealth() > 0, "It does not kill her");
        h.runAfterDelay(110, () -> {
            try {
                for (BodyService.Limb limb : BodyService.Limb.values())
                    h.assertTrue(!BodyService.missing(p, limb), "At Level 5 the limb grows back within a few seconds: " + limb);
                h.succeed();
            } finally { done(p, brute); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "regen.handicap")
    public static void aMissingArmCannotSwingAndAMissingLegSlows(GameTestHelper h) {
        ServerPlayer p = claire(h, 1);
        LivingEntity target = cow(h, p.position().add(0, 0, 1.5));
        try {
            double speed = p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
            BodyService.sever(p, BodyService.arm(p, net.minecraft.world.InteractionHand.MAIN_HAND), 1);
            p.attack(target);
            h.assertTrue(target.getHealth() == target.getMaxHealth(), "No arm, no punch");
            BodyService.sever(p, BodyService.Limb.LEFT_LEG, 1);
            h.assertTrue(p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) < speed * 0.7,
                    "A missing leg slows her down");
            h.succeed();
        } finally { done(p, target); }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 60, batch = "regen.mend_limbs")
    public static void rapidMendingRegrowsLimbsAtOnce(GameTestHelper h) {
        ServerPlayer p = claire(h, 1);
        BodyService.sever(p, BodyService.Limb.RIGHT_LEG, 1);
        RegenerationService.rapidMending(p, 1);
        h.runAfterDelay(25, () -> {
            try {
                h.assertTrue(!BodyService.missing(p, BodyService.Limb.RIGHT_LEG), "Rapid Mending regrows a limb in about a second");
                h.succeed();
            } finally { done(p); }
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 220, batch = "regen.downed")
    public static void aLethalBlowPutsHerDownAndSheGetsBackUp(GameTestHelper h) {
        ServerPlayer p = claire(h, 5);
        var zombie = EntityType.ZOMBIE.create(h.getLevel());
        zombie.setNoAi(true); zombie.moveTo(p.getX() + 3, p.getY(), p.getZ(), 0, 0);
        h.getLevel().addFreshEntity(zombie);
        p.hurt(h.getLevel().damageSources().generic(), 1000);
        h.assertTrue(p.isAlive() && BodyService.isDowned(p), "She does not die; she goes down");
        h.assertTrue(p.getForcedPose() == net.minecraft.world.entity.Pose.SLEEPING, "Her body lies on the ground");
        float health = p.getHealth();
        p.invulnerableTime = 0;
        p.hurt(h.getLevel().damageSources().generic(), 10);
        h.assertTrue(p.getHealth() == health, "A body on the ground cannot be hurt any further");
        zombie.setTarget(p);
        h.assertTrue(zombie.getTarget() == null, "Monsters think she's dead and look elsewhere");
        int recovery = BodyService.downedRemaining(p);
        h.assertTrue(recovery > 0 && recovery <= 20 * 12, "At Level 5 she's down for twelve seconds at most, got " + recovery);
        h.runAfterDelay(recovery + 10, () -> {
            try {
                h.assertTrue(!BodyService.isDowned(p) && p.getForcedPose() == null, "Her body repairs itself and she gets up");
                h.assertTrue(p.getHealth() >= 6, "She gets up with some health back");
                h.succeed();
            } finally { done(p, zombie); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "regen.downed_time")
    public static void lowerLevelsStayDownLonger(GameTestHelper h) {
        h.assertTrue(BodyService.recoveryTicks(1, 0) == 20 * 90 && BodyService.recoveryTicks(5, 0) == 20 * 12,
                "Ninety seconds at Level 1, twelve at Level 5");
        h.assertTrue(BodyService.recoveryTicks(1, 100) < BodyService.recoveryTicks(1, 0), "Stored Regrowth shortens it");
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "regen.true_death")
    public static void theVoidAndADrainedBodyStillKill(GameTestHelper h) {
        ServerPlayer lost = claire(h, 5), drained = claire(h, 5);
        try {
            lost.hurt(h.getLevel().damageSources().fellOutOfWorld(), 1000);
            h.assertTrue(lost.isDeadOrDying() && !BodyService.isDowned(lost), "The void still kills");
            drained.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.jayptucker.heroesevolved.registry.ModItems.DRAINED, 600));
            drained.hurt(h.getLevel().damageSources().generic(), 1000);
            h.assertTrue(drained.isDeadOrDying() && !BodyService.isDowned(drained), "A body drained of its blood can die");
            h.succeed();
        } finally { done(lost); done(drained); }
    }

    @GameTest(template = "rewind_empty", batch = "regen.hazard")
    public static void sheIsMovedOutOfLavaBeforeGettingUp(GameTestHelper h) {
        ServerPlayer p = claire(h, 1);
        BlockPos feet = p.blockPosition();
        try {
            h.getLevel().setBlock(feet, net.minecraft.world.level.block.Blocks.LAVA.defaultBlockState(), 3);
            p.baseTick();
            h.assertTrue(BodyService.hazardous(p), "Standing up in lava would only kill her again");
            BodyService.moveToSafety(p);
            h.assertTrue(!p.blockPosition().equals(feet), "She is moved to safe ground nearby");
            h.succeed();
        } finally {
            h.getLevel().setBlock(feet, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            done(p);
        }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 220, batch = "regen.give_up")
    public static void holdingSneakGivesUpAndWakesHerAtHome(GameTestHelper h) {
        ServerPlayer p = claire(h, 1);
        p.hurt(h.getLevel().damageSources().generic(), 1000);
        h.assertTrue(BodyService.isDowned(p), "Down for a minute and a half at Level 1");
        p.setShiftKeyDown(true);
        h.runAfterDelay(BodyService.GIVE_UP_AFTER + BodyService.GIVE_UP_HOLD + 10, () -> {
            try {
                h.assertTrue(!BodyService.isDowned(p), "Holding Sneak lets her give up instead of waiting");
                h.assertTrue(p.getHealth() == p.getMaxHealth(), "She wakes whole");
                h.succeed();
            } finally { p.setShiftKeyDown(false); done(p); }
        });
    }
}
