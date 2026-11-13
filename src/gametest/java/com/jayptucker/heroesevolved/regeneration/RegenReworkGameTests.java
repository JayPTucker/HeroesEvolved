package com.jayptucker.heroesevolved.regeneration;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.intro.PowerIntroHistory;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.skills.SkillTreeData;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Regeneration rework: Mending pushing arrows out, Blood Stream (the held Blood Gift), Freefall, Pay It Back,
 * Bloodletting, Adrenal Rush, Play Dead, and the passive nodes (Shared Blood, Scar Tissue, Quick Recovery, Back on Your
 * Feet).
 */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class RegenReworkGameTests {
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

    private static void learn(ServerPlayer p, String... ids) {
        p.setData(ModDataAttachments.SKILL_TREES, SkillTreeData.empty().with(ModAbilities.REGENERATION_ID.toString(),
                new SkillTreeData.Build(15, Set.of(ids), Map.of())));
    }

    private static Zombie zombie(GameTestHelper h, ServerPlayer p, double dx, double dz, boolean ai) {
        Zombie z = EntityType.ZOMBIE.create(h.getLevel());
        z.setNoAi(!ai);
        z.moveTo(p.getX() + dx, p.getY(), p.getZ() + dz, 180, 0);
        h.getLevel().addFreshEntity(z);
        return z;
    }

    @GameTest(template = "rewind_empty", batch = "regenrework.arrows")
    public static void rapidMendingPushesArrowsOut(GameTestHelper h) {
        ServerPlayer p = claire(h, 3);
        try {
            p.setArrowCount(3);
            p.setHealth(2);
            RegenerationService.rapidMending(p, 3);
            h.assertTrue(p.getArrowCount() == 0, "The arrows are pushed out");
            var dropped = h.getLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(3), i -> i.getItem().is(Items.ARROW));
            h.assertTrue(!dropped.isEmpty() && dropped.getFirst().getItem().getCount() == 3, "They drop at her feet");
            h.assertTrue(p.getHealth() > 2 + 4 + 2 * 3, "Their Regrowth goes into the heal (more than the base 10)");
            dropped.forEach(Entity::discard);
            h.succeed();
        } finally { done(p); }
    }

    @GameTest(template = "rewind_empty", batch = "regenrework.stream", timeoutTicks = 60)
    public static void heldBloodGiftBecomesAStream(GameTestHelper h) {
        ServerPlayer p = claire(h, 3);
        LivingEntity cow = cow(h, p.position().add(0, 0, 4));
        cow.setHealth(2);
        aim(p, cow);
        h.assertTrue(RegenSkills.canGift(p), "A friend is in reach");
        RegenSkills.pressGift(p, 3);
        h.runAfterDelay(RegenSkills.STREAM_HOLD + 2, () -> {
            try { h.assertTrue(RegenSkills.streaming(p), "Held, the gift becomes a stream"); }
            catch (RuntimeException e) { done(p, cow); throw e; }
        });
        h.runAfterDelay(RegenSkills.STREAM_HOLD + 24, () -> {
            try {
                h.assertTrue(cow.getHealth() > 4, "The stream keeps healing every half second (now " + cow.getHealth() + ")");
                RegenSkills.releaseGift(p);
                h.assertTrue(!RegenSkills.streaming(p), "Letting go ends it");
                h.succeed();
            } finally { done(p, cow); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "regenrework.tap")
    public static void aTappedBloodGiftIsStillTheGift(GameTestHelper h) {
        ServerPlayer p = claire(h, 3);
        LivingEntity cow = cow(h, p.position().add(0, 0, 4));
        cow.setHealth(2);
        aim(p, cow);
        try {
            RegenSkills.pressGift(p, 3);
            RegenSkills.releaseGift(p);
            h.assertTrue(cow.getHealth() > 2 && !RegenSkills.streaming(p), "A tap gives the gift at once");
            h.succeed();
        } finally { done(p, cow); }
    }

    @GameTest(template = "rewind_empty", batch = "regenrework.freefall")
    public static void freefallCrashesDownOnWhatIsBelow(GameTestHelper h) {
        ServerPlayer p = claire(h, 4);
        // A pillager: no armour to blunt the number being checked.
        var z = EntityType.PILLAGER.create(h.getLevel());
        z.setNoAi(true);
        z.moveTo(p.getX() + 2, p.getY(), p.getZ(), 180, 0);
        h.getLevel().addFreshEntity(z);
        try {
            h.assertTrue(!RegenSkills.freefall(p), "No Freefall without Indestructible");
            RegenerationService.indestructible(p, 4);
            h.assertTrue(RegenSkills.freefall(p) && RegenSkills.falling(p), "Pressed again while Indestructible, she leaps");
            h.assertTrue(!RegenSkills.freefall(p), "Once per Indestructible");
            int before = RegenerationService.regrowth(p);
            float health = z.getHealth();
            RegenSkills.land(p, 10);
            h.assertTrue(health - z.getHealth() >= 13.9F, "Ten blocks down: 4 + 10 to what she lands near (took " + (health - z.getHealth()) + ")");
            h.assertTrue(RegenerationService.regrowth(p) == before + 10, "A point of Regrowth per block fallen");
            h.assertTrue(!RegenSkills.falling(p), "Landed");
            h.succeed();
        } finally { done(p, z); }
    }

    @GameTest(template = "rewind_empty", batch = "regenrework.payback")
    public static void payItBackReturnsTheWoundsAsHealing(GameTestHelper h) {
        ServerPlayer p = claire(h, 4);
        LivingEntity cow = cow(h, p.position().add(0, 0, 3));
        try {
            RegenerationService.healingAura(p, 4);
            RegenerationService.takeShare(p, cow, 4);
            cow.setHealth(2);
            int before = RegenerationService.regrowth(p);
            h.assertTrue(RegenerationService.payItBack(p), "Pressed again during the bond: Pay It Back");
            h.assertTrue(!RegenerationService.hasHealingAura(p), "The bond ends");
            h.assertTrue(cow.getHealth() == cow.getMaxHealth(), "The friend heals by what she took plus 2 per level (now " + cow.getHealth() + ")");
            h.assertTrue(RegenerationService.regrowth(p) >= before + 8, "She gains twice what she took as Regrowth");
            h.assertTrue(!RegenerationService.payItBack(p), "Nothing left to pay back");
            h.succeed();
        } finally { done(p, cow); }
    }

    @GameTest(template = "rewind_empty", batch = "regenrework.shared")
    public static void sharedBloodBondsWhoeverSheHeals(GameTestHelper h) {
        ServerPlayer p = claire(h, 3);
        LivingEntity cow = cow(h, p.position().add(0, 0, 4));
        cow.setHealth(4);
        aim(p, cow);
        try {
            RegenerationService.bloodGift(p, 3);
            h.assertTrue(RegenerationService.bondHolder(cow) == null, "Without Shared Blood a gift bonds no one");
            learn(p, RegenSkills.SHARED_BLOOD);
            RegenerationService.bloodGift(p, 3);
            h.assertTrue(RegenerationService.bondHolder(cow) == p, "With it, the friend she healed is bonded to her");
            h.succeed();
        } finally { done(p, cow); }
    }

    @GameTest(template = "rewind_empty", batch = "regenrework.scar")
    public static void scarTissueDoublesRegrowthFromSharedWounds(GameTestHelper h) {
        ServerPlayer p = claire(h, 3);
        LivingEntity cow = cow(h, p.position().add(0, 0, 3));
        try {
            int before = RegenerationService.regrowth(p);
            RegenerationService.takeShare(p, cow, 3);
            int plain = RegenerationService.regrowth(p) - before;
            learn(p, RegenSkills.SCAR_TISSUE);
            p.invulnerableTime = 0;
            int mid = RegenerationService.regrowth(p);
            RegenerationService.takeShare(p, cow, 3);
            int scarred = RegenerationService.regrowth(p) - mid;
            h.assertTrue(plain > 0 && scarred == plain * 2, "Twice the Regrowth (" + plain + " then " + scarred + ")");
            h.succeed();
        } finally { done(p, cow); }
    }

    @GameTest(template = "rewind_empty", batch = "regenrework.bloodletting")
    public static void bloodlettingHealsFriendsInTheFan(GameTestHelper h) {
        ServerPlayer p = claire(h, 3);
        LivingEntity cow = cow(h, p.position().add(0, 0, 3));
        cow.setHealth(2);
        aim(p, cow);
        try {
            float health = p.getHealth();
            int before = RegenerationService.regrowth(p);
            h.assertTrue(RegenSkills.bloodletting(p, 3), "She flings the blood");
            h.assertTrue(cow.getHealth() >= 2 + 7 - 0.01F, "A friend in the fan heals 4 + level");
            h.assertTrue(p.getHealth() == health - 2, "It costs her 2 health");
            h.assertTrue(RegenerationService.regrowth(p) >= before + 4, "Each friend healed gives 4 Regrowth");
            h.succeed();
        } finally { done(p, cow); }
    }

    @GameTest(template = "rewind_empty", batch = "regenrework.rush")
    public static void adrenalRushSpendsRegrowth(GameTestHelper h) {
        ServerPlayer p = claire(h, 3);
        try {
            p.setHealth(4);
            RegenerationService.addRegrowth(p, 60);
            h.assertTrue(RegenSkills.adrenalRush(p), "The rush hits");
            h.assertTrue(RegenerationService.regrowth(p) == 20, "It spends 40 Regrowth");
            h.assertTrue(p.getHealth() >= 4 + 4 + 8 - 0.01F, "Healing 4 plus one per 5 spent");
            h.assertTrue(p.hasEffect(MobEffects.MOVEMENT_SPEED) && p.hasEffect(MobEffects.JUMP), "Speed and Jump Boost");
            h.succeed();
        } finally { done(p); }
    }

    @GameTest(template = "rewind_empty", batch = "regenrework.playdead", timeoutTicks = 30)
    public static void playDeadShakesOffHuntersAndSetsUpAnAmbush(GameTestHelper h) {
        ServerPlayer p = claire(h, 3);
        Zombie z = zombie(h, p, 3, 0, true);
        z.setTarget(p);
        try {
            h.assertTrue(RegenSkills.playDead(p) && RegenSkills.feigning(p), "She drops");
            h.assertTrue(z.getTarget() == null, "The hunter loses her");
            z.setTarget(p);
            h.assertTrue(z.getTarget() == null, "And can't pick her again while she lies there");
            h.assertTrue(RegenSkills.getUp(p) && !RegenSkills.feigning(p) && RegenSkills.ambushing(p), "She gets up, ready to ambush");
            float health = z.getHealth();
            z.invulnerableTime = 0;
            z.hurt(p.damageSources().playerAttack(p), 2);
            // 2 + 6, less a zombie's two points of armour.
            h.assertTrue(health - z.getHealth() >= 7, "Her next hit lands 6 harder (took " + (health - z.getHealth()) + ")");
            h.assertTrue(!RegenSkills.ambushing(p), "Once");
            h.succeed();
        } finally { done(p, z); }
    }

    @GameTest(template = "rewind_empty", batch = "regenrework.recovery", timeoutTicks = 200)
    public static void quickRecoveryAndBackOnYourFeet(GameTestHelper h) {
        ServerPlayer p = claire(h, 5);
        learn(p, RegenSkills.QUICK_RECOVERY, RegenSkills.BACK_ON_YOUR_FEET);
        h.assertTrue(RegenSkills.recovery(p, 300) == 200, "A third less time on the ground");
        RegenerationService.addRegrowth(p, 100);
        h.assertTrue(BodyService.goDown(p, 5, p.damageSources().generic()) && BodyService.isDowned(p), "She goes down");
        // Level 5 with full Regrowth: 12 s less 40%, then a third off: 96 ticks.
        h.runAfterDelay(120, () -> {
            try {
                h.assertTrue(!BodyService.isDowned(p), "She is back up");
                h.assertTrue(p.hasEffect(MobEffects.DAMAGE_BOOST), "Back on Your Feet: she rises stronger");
                h.succeed();
            } finally { done(p); }
        });
    }
}
