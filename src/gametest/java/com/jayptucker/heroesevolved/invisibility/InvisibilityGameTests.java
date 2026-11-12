package com.jayptucker.heroesevolved.invisibility;

import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.cryo.TemporaryBlocks;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.skills.SkillCatalog;
import com.jayptucker.heroesevolved.skills.SkillTreeData;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Concealment and force fields are server-owned: what hides, holds, blocks, throws and breaks is checked here. */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class InvisibilityGameTests {
    private static ServerPlayer player(GameTestHelper h, int level, int x) {
        var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "optic-test")) {
            @Override public boolean isInvulnerableTo(DamageSource damage) { return false; }
        };
        try {
            Connection connection = p.connection.getConnection();
            Field channel = Connection.class.getDeclaredField("channel"); channel.setAccessible(true);
            if (connection.channel() == null) channel.set(connection, new EmbeddedChannel());
            p.connection = new ServerGamePacketListenerImpl(p.server, connection, p,
                    CommonListenerCookie.createInitial(p.getGameProfile(), false)) {
                @Override public void send(Packet<?> packet) { }
            };
            Field grace = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            grace.setAccessible(true); grace.setInt(p, 0);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        // Explicitly own the floor height: template placement can include a foundation at local Y=1.
        for (int floorX = 0; floorX < 12; floorX++) for (int floorZ = 0; floorZ < 12; floorZ++)
            h.getLevel().setBlock(h.absolutePos(new BlockPos(floorX, 1, floorZ)), Blocks.STONE.defaultBlockState(), 3);
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(x, 2, 3))));
        p.setNoGravity(true); p.setYRot(0); p.setXRot(0); p.setGameMode(GameType.SURVIVAL);
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty()
                .assignDormant(ModAbilities.INVISIBILITY_ID).unlock(ModAbilities.INVISIBILITY_ID));
        p.setData(ModDataAttachments.POWER_INTRO_HISTORY,
                p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(ModAbilities.INVISIBILITY_ID));
        PlayerProgressionService.setLevel(p, level);
        PlayerEnergyService.restore(p, PlayerEnergyService.getMaximumEnergy(p));
        h.getLevel().addNewPlayer(p);
        return p;
    }
    private static void learn(ServerPlayer p, String... nodes) {
        p.setData(ModDataAttachments.SKILL_TREES, SkillTreeData.empty().with(ModAbilities.INVISIBILITY_ID.toString(),
                new SkillTreeData.Build(15, Set.of(nodes), Map.of())));
    }
    private static AbilityUseContext context(ServerPlayer p) { return new AbilityUseContext(p, ModAbilities.INVISIBILITY_ID, 5, false); }
    private static void clean(ServerPlayer... players) {
        for (ServerPlayer p : players) {
            PowerRules.rewinding(p, false);
            InvisibilityService.forget(p); p.discard();
        }
    }
    private static Mob enemy(GameTestHelper h, ServerPlayer p, double dx, double dz) {
        var mob = EntityType.HUSK.create(h.getLevel());
        mob.setNoAi(true); mob.setNoGravity(true); mob.setPersistenceRequired();
        mob.moveTo(p.getX() + dx, p.getY(), p.getZ() + dz, 180, 0);
        h.getLevel().addFreshEntity(mob);
        return mob;
    }
    private static Mob enemy(GameTestHelper h, ServerPlayer p, double distance) { return enemy(h, p, 0, distance); }
    private static void discard(LivingEntity... mobs) { for (LivingEntity m : mobs) m.discard(); }
    /** An arrow fired by {@code shooter} from {@code from} straight at {@code at}. */
    private static Arrow arrow(GameTestHelper h, LivingEntity shooter, Vec3 from, Vec3 at) {
        var arrow = new Arrow(h.getLevel(), shooter, new ItemStack(Items.ARROW), null);
        arrow.setPos(from);
        arrow.setNoGravity(true);
        arrow.setDeltaMovement(at.subtract(from).normalize().scale(1.4));
        h.getLevel().addFreshEntity(arrow);
        return arrow;
    }

    // ================================================================ Fade

    @GameTest(template = "rewind_empty", batch = "optic.aggro")
    public static void fadeReleasesExistingAggroAndPreventsExactTracking(GameTestHelper h) {
        ServerPlayer p = player(h, 5, 3);
        var hunter = enemy(h, p, 6);
        hunter.setTarget(p);
        h.assertTrue(hunter.getTarget() == p, "The hunter must already be chasing before Fade");
        InvisibilityService.toggleFade(p);
        h.assertTrue(hunter.getTarget() == null, "Fade must clear existing aggro, including a legitimate null target change");
        hunter.setTarget(p);
        h.assertTrue(hunter.getTarget() == null, "A distant hunter cannot reacquire the exact concealed position");
        h.runAfterDelay(12, () -> {
            try {
                h.assertTrue(InvisibilityService.isUnseen(p) && hunter.getTarget() == null,
                        "Concealment must continue after initial activation");
                h.succeed();
            } finally { hunter.discard(); clean(p); }
        });
    }
    @GameTest(template = "rewind_empty", batch = "optic.exposure")
    public static void noviceMovementBuildsMoreExposureAndStillnessSettlesIt(GameTestHelper h) {
        ServerPlayer novice = player(h, 1, 3), veteran = player(h, 5, 7);
        InvisibilityService.toggleFade(novice); InvisibilityService.toggleFade(veteran);
        long start = h.getLevel().getGameTime();
        int[] peak = {0};
        h.onEachTick(() -> {
            boolean moving = h.getLevel().getGameTime() - start < 20;
            for (ServerPlayer p : new ServerPlayer[]{novice, veteran}) {
                p.setSprinting(moving);
                p.setDeltaMovement(moving ? new Vec3(0, 0, .14) : Vec3.ZERO);
                if (moving) p.setPos(p.position().add(0, 0, .14));
            }
        });
        h.runAfterDelay(20, () -> {
            h.assertTrue(InvisibilityService.exposure(novice) > InvisibilityService.exposure(veteran),
                    "Inexperienced refraction must shimmer more under the same movement");
            peak[0] = InvisibilityService.exposure(novice);
        });
        h.runAfterDelay(35, () -> {
            try {
                h.assertTrue(InvisibilityService.exposure(novice) < peak[0], "Standing still must reduce Exposure");
                h.succeed();
            } finally { clean(novice, veteran); }
        });
    }
    @GameTest(template = "rewind_empty", batch = "optic.senses")
    public static void damageAndSensesRevealWithoutDestroyingTheFade(GameTestHelper h) {
        ServerPlayer p = player(h, 5, 3);
        InvisibilityService.toggleFade(p);
        p.hurt(p.damageSources().generic(), 1);
        h.assertTrue(InvisibilityService.isFaded(p) && !InvisibilityService.isUnseen(p),
                "A hit must flicker the player visible while preserving the toggle");
        h.runAfterDelay(23, () -> {
            try {
                h.assertTrue(InvisibilityService.isUnseen(p), "A quiet player can settle back into concealment");
                InvisibilityService.revealAround(h.getLevel(), p.position(), 3, 40);
                h.assertTrue(!InvisibilityService.isUnseen(p), "A sensing power still pierces concealment");
                h.succeed();
            } finally { clean(p); }
        });
    }
    @GameTest(template = "rewind_empty", batch = "optic.shared")
    public static void aHeldFadeCoversNearbyFriendsAndAnAttackRevealsOnlyItsAttacker(GameTestHelper h) {
        ServerPlayer owner = player(h, 5, 3), friend = player(h, 5, 5);
        friend.setShiftKeyDown(true);
        var victim = enemy(h, friend, 3);
        InvisibilityService.toggleFade(owner);
        h.runAfterDelay(12, () -> {
            try {
                h.assertTrue(InvisibilityService.sharing(owner) == 1 && InvisibilityService.isUnseen(owner)
                        && InvisibilityService.isUnseen(friend), "Fade must cover a consenting friend within three blocks");
                victim.hurt(friend.damageSources().playerAttack(friend), 1);
                h.assertTrue(!InvisibilityService.isUnseen(friend) && InvisibilityService.isUnseen(owner),
                        "Attacking must expose the attacker without exposing the whole team");
                h.succeed();
            } finally { victim.discard(); clean(owner, friend); }
        });
    }
    @GameTest(template = "rewind_empty", batch = "optic.potion_restore")
    public static void revealsSuspendAndUnfadeRestoresAnExistingInvisibilityPotion(GameTestHelper h) {
        ServerPlayer p = player(h, 5, 3);
        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.INVISIBILITY, 200, 0, false, false));
        InvisibilityService.toggleFade(p);
        InvisibilityService.reveal(p, 20);
        h.assertTrue(!InvisibilityService.isUnseen(p) && !p.hasEffect(MobEffects.INVISIBILITY),
                "A reveal must remove concealment, rather than leave the power potion hiding the body");
        h.runAfterDelay(6, () -> {
            try {
                InvisibilityService.unfade(p);
                var restored = p.getEffect(MobEffects.INVISIBILITY);
                h.assertTrue(restored != null && restored.getDuration() > 150 && restored.getDuration() < 200,
                        "Ending Fade restores the still-remaining duration of a pre-existing potion");
                h.succeed();
            } finally { clean(p); }
        });
    }
    @GameTest(template = "rewind_empty", batch = "optic.borrowed")
    public static void borrowedFadeSustainsAndReturnsWithoutTakingTheOriginalPower(GameTestHelper h) {
        ServerPlayer mimic = player(h, 5, 3), original = player(h, 5, 7);
        mimic.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty()
                .assignDormant(ModAbilities.MIMICRY_ID).unlock(ModAbilities.MIMICRY_ID));
        mimic.setData(ModDataAttachments.POWER_INTRO_HISTORY,
                mimic.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(ModAbilities.MIMICRY_ID));
        com.jayptucker.heroesevolved.mimicry.MimicryService.remember(mimic, ModAbilities.INVISIBILITY_ID, 5);
        var echo = com.jayptucker.heroesevolved.ability.registry.AbilityRegistry.ABILITIES.get(ModAbilities.MIMICRY_ID)
                .actions().get(com.jayptucker.heroesevolved.ability.AbilitySlot.TERTIARY);
        var context = new AbilityUseContext(mimic, ModAbilities.MIMICRY_ID, 5, false);
        h.assertTrue(echo.canUse(context) && echo.activate(context)
                        == com.jayptucker.heroesevolved.ability.AbilityActivationResult.SUCCESS,
                "Echo I can borrow Fade");
        h.runAfterDelay(12, () -> {
            try {
                h.assertTrue(InvisibilityService.isFaded(mimic) && InvisibilityService.isUnseen(mimic),
                        "A borrowed Fade must remain authorized after the initial Echo call returns");
                com.jayptucker.heroesevolved.mimicry.MimicryService.forget(mimic);
                h.assertTrue(!InvisibilityService.isFaded(mimic) && InvisibilityService.isInvisibilityOwner(original),
                        "Ending the borrowed session cleans its effect while preserving the original owner's power");
                h.succeed();
            } finally { com.jayptucker.heroesevolved.mimicry.MimicryService.forget(mimic); clean(mimic, original); }
        });
    }

    // ================================================================ Lens Shield and Force Push

    @GameTest(template = "rewind_empty", batch = "force.shield")
    public static void lensShieldStopsArrowsStoresPressureAndPushThrowsFoes(GameTestHelper h) {
        ServerPlayer p = player(h, 5, 3);
        var archer = enemy(h, p, 2, 9);
        var foe = enemy(h, p, 4);
        try {
            h.assertTrue(ForceFieldService.raiseShield(p) && ForceFieldService.shielding(p), "The Lens Shield must rise");
        } catch (RuntimeException e) { discard(archer, foe); clean(p); throw e; }
        var shot = arrow(h, archer, p.getEyePosition().add(0, 0, 4.5), p.getEyePosition());
        float health = p.getHealth();
        h.runAfterDelay(6, () -> {
            try {
                h.assertTrue(shot.isRemoved() && p.getHealth() == health && ForceFieldService.pressure(p) == 1,
                        "The shield must stop an arrow from the front and store a Pressure: removed=" + shot.isRemoved()
                                + " pressure=" + ForceFieldService.pressure(p));
                h.assertTrue(ForceFieldService.push(p) && !ForceFieldService.shielding(p), "The second press throws the shield as a Force Push");
            } catch (RuntimeException e) { discard(archer, foe); clean(p); throw e; }
        });
        h.runAfterDelay(10, () -> {
            try {
                h.assertTrue(foe.getHealth() < foe.getMaxHealth(), "Force Push must hurt the foe it meets");
                h.assertTrue(foe.getZ() > p.getZ() + 4.2 || foe.getDeltaMovement().z > .3,
                        "Force Push must throw the foe away along the aim");
                h.succeed();
            } finally { discard(archer, foe); clean(p); }
        });
    }
    @GameTest(template = "rewind_empty", batch = "force.rebound")
    public static void reboundSendsAStoppedArrowBackAtItsArcher(GameTestHelper h) {
        ServerPlayer p = player(h, 5, 3);
        learn(p, InvisibilitySkills.REBOUND);
        var archer = enemy(h, p, 0, 8);
        ForceFieldService.raiseShield(p);
        var shot = arrow(h, archer, p.getEyePosition().add(0, 0, 4.5), p.getEyePosition());
        h.runAfterDelay(4, () -> {
            try {
                h.assertTrue(!shot.isRemoved() && shot.getOwner() == p && shot.getDeltaMovement().z > .3,
                        "With Rebound the arrow must turn round toward its archer and become the caster's");
                h.succeed();
            } finally { shot.discard(); discard(archer); clean(p); }
        });
    }

    // ================================================================ Force Bridge and Force Barrier

    @GameTest(template = "rewind_empty", batch = "force.bridge")
    public static void forceBridgeSpansAGapStopsAtAWallAndLeavesNoTrace(GameTestHelper h) {
        ServerPlayer p = player(h, 5, 3);
        for (int x = 1; x <= 5; x++) for (int z = 4; z <= 11; z++) h.getLevel().setBlock(h.absolutePos(new BlockPos(x, 1, z)), Blocks.AIR.defaultBlockState(), 3);
        for (int y = 2; y <= 3; y++) h.getLevel().setBlock(h.absolutePos(new BlockPos(3, y, 9)), Blocks.STONE.defaultBlockState(), 3);
        BlockPos near = h.absolutePos(new BlockPos(3, 1, 6)), side = h.absolutePos(new BlockPos(2, 1, 6)), beyond = h.absolutePos(new BlockPos(3, 1, 10));
        try {
            h.assertTrue(ForceFieldService.bridge(p) && ForceFieldService.bridges(p) == 1, "Force Bridge must lay a span over the gap");
            h.assertTrue(h.getLevel().getBlockState(near).is(Blocks.BARRIER) && h.getLevel().getBlockState(side).is(Blocks.BARRIER)
                            && TemporaryBlocks.isTemporary(h.getLevel(), near),
                    "The span must be solid, three lanes wide, and temporary");
            h.assertTrue(h.getLevel().getBlockState(beyond).isAir(), "A wall across its path must stop the span");
        } catch (RuntimeException e) { clean(p); throw e; }
        double baseStep = p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT);
        h.runAfterDelay(6, () -> {
            try {
                h.assertTrue(ForceFieldService.stepping(p)
                                && p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT) > baseStep + .4,
                        "A player at the bridge steps up its stairs without jumping");
                ForceFieldService.forget(p);
                h.assertTrue(h.getLevel().getBlockState(near).isAir() && h.getLevel().getBlockState(side).isAir(),
                        "When the bridge goes, the air comes back exactly");
                h.assertTrue(p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT) == baseStep,
                        "The step-up ends with the bridge");
                h.succeed();
            } finally { clean(p); }
        });
    }
    @GameTest(template = "rewind_empty", batch = "force.barrier")
    public static void forceBarrierRaisesASolidWallWhereYouAim(GameTestHelper h) {
        ServerPlayer p = player(h, 5, 3);
        p.setXRot(45);
        try {
            h.assertTrue(InvisibilitySkills.skill(context(p), 1) && ForceFieldService.walls(p) == 1, "Force Barrier must rise");
            BlockPos cell = h.absolutePos(new BlockPos(3, 2, 4));
            h.assertTrue(h.getLevel().getBlockState(cell).is(Blocks.BARRIER) && h.getLevel().getBlockState(cell.above(3)).is(Blocks.BARRIER)
                            && h.getLevel().getBlockState(cell.east(3)).is(Blocks.BARRIER),
                    "The barrier must stand four tall and seven wide across the aim");
            ForceFieldService.forget(p);
            h.assertTrue(h.getLevel().getBlockState(cell).isAir(), "The barrier must leave no trace");
            h.succeed();
        } finally { clean(p); }
    }

    // ================================================================ Bubble, Crush, roll and shelter

    @GameTest(template = "rewind_empty", batch = "force.bubble")
    public static void aBubbleHoldsAFoeStopsItsBlowsAndCrushHurtsIt(GameTestHelper h) {
        ServerPlayer p = player(h, 5, 3);
        var foe = enemy(h, p, 4);
        Vec3 anchor = foe.position();
        try {
            h.assertTrue(ForceFieldService.bubble(p) && ForceFieldService.contained(foe), "Aiming at a foe must seal it in a bubble");
        } catch (RuntimeException e) { discard(foe); clean(p); throw e; }
        foe.setPos(anchor.add(1.5, 0, 0));
        float health = p.getHealth();
        p.hurt(foe.damageSources().mobAttack(foe), 4);
        h.runAfterDelay(3, () -> {
            try {
                h.assertTrue(foe.position().distanceTo(anchor) < .1, "The bubble must hold its foe where it was sealed");
                h.assertTrue(p.getHealth() == health, "A held foe cannot strike out of its bubble");
                h.assertTrue(ForceFieldService.crush(p) && ForceFieldService.crushing(foe), "The second press crushes the bubble");
            } catch (RuntimeException e) { discard(foe); clean(p); throw e; }
        });
        h.runAfterDelay(14, () -> {
            try {
                h.assertTrue(!ForceFieldService.contained(foe) && foe.getHealth() < foe.getMaxHealth() - 10,
                        "Crushing must hurt the held foe badly and release it: health=" + foe.getHealth());
                h.succeed();
            } finally { discard(foe); clean(p); }
        });
    }
    @GameTest(template = "rewind_empty", batch = "force.roll")
    public static void forcePushSendsAHeldBubbleRollingIntoOtherFoes(GameTestHelper h) {
        ServerPlayer p = player(h, 1, 3);
        // Stand at the arena's edge so the foe behind the bubble is beyond the Push's own reach but still in the arena.
        p.setPos(p.getX(), p.getY(), h.absolutePos(new BlockPos(3, 2, 0)).getZ() + .5);
        var held = enemy(h, p, 2);
        var bystander = enemy(h, p, 11);
        ForceFieldService.contain(p, held, 100, false);
        ForceFieldService.raiseShield(p);
        ForceFieldService.push(p);
        h.runAfterDelay(3, () -> {
            try {
                h.assertTrue(ForceFieldService.rolling(held), "A Push that meets a bubble must set it rolling");
            } catch (RuntimeException e) { discard(held, bystander); clean(p); throw e; }
        });
        h.runAfterDelay(16, () -> {
            try {
                h.assertTrue(bystander.getHealth() < bystander.getMaxHealth(), "The rolling bubble must bowl over the foe in its path");
                h.assertTrue(held.getZ() > p.getZ() + 4, "The bubble must have carried its foe along");
                h.succeed();
            } finally { discard(held, bystander); clean(p); }
        });
    }
    @GameTest(template = "rewind_empty", batch = "force.shelter")
    public static void aShelterBubbleStopsArrowsAndHalvesOtherHarm(GameTestHelper h) {
        ServerPlayer p = player(h, 5, 3), bare = player(h, 5, 9);
        p.setXRot(-90);
        var archer = enemy(h, p, 2, 9);
        try {
            h.assertTrue(ForceFieldService.bubble(p) && ForceFieldService.sheltered(p) && !ForceFieldService.sheltered(bare),
                    "Aiming at nothing shelters the caster and those close by, not a player six blocks off");
            p.hurt(archer.damageSources().mobAttack(archer), 8);
            bare.hurt(archer.damageSources().mobAttack(archer), 8);
            float sheltered = p.getMaxHealth() - p.getHealth(), open = bare.getMaxHealth() - bare.getHealth();
            h.assertTrue(open > 0 && Math.abs(sheltered - open / 2) < .01, "Harm from outside the shelter is halved: " + sheltered + " vs " + open);
        } catch (RuntimeException e) { discard(archer); clean(p, bare); throw e; }
        float health = p.getHealth();
        var shot = arrow(h, archer, p.position().add(0, 1, 3), p.position().add(0, 1, 0));
        h.runAfterDelay(5, () -> {
            try {
                h.assertTrue(shot.isRemoved() && p.getHealth() == health, "The shelter must stop an arrow at its shell");
                h.succeed();
            } finally { discard(archer); clean(p, bare); }
        });
    }

    // ================================================================ Force Nova

    @GameTest(template = "rewind_empty", batch = "force.nova")
    public static void forceNovaTelegraphsThenHitsHeldFoesTwiceAsHardAndVanishesTheCaster(GameTestHelper h) {
        ServerPlayer p = player(h, 1, 5);
        var held = enemy(h, p, 4, 0);
        var free = enemy(h, p, -4, 0);
        held.setHealth(40); free.setHealth(40);
        held.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(60);
        free.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(60);
        held.setHealth(60); free.setHealth(60);
        ForceFieldService.contain(p, held, 100, false);
        try {
            h.assertTrue(ForceFieldService.nova(p) && ForceFieldService.charging(p), "Force Nova must begin its wind-up");
        } catch (RuntimeException e) { discard(held, free); clean(p); throw e; }
        h.runAfterDelay(10, () -> {
            try {
                h.assertTrue(free.getHealth() == 60, "Nothing is hurt during the wind-up");
            } catch (RuntimeException e) { discard(held, free); clean(p); throw e; }
        });
        h.runAfterDelay(23, () -> {
            try {
                float heldLoss = 60 - held.getHealth(), freeLoss = 60 - free.getHealth();
                h.assertTrue(freeLoss > 5, "The burst must hurt a free foe: " + freeLoss);
                h.assertTrue(heldLoss > freeLoss * 1.8F, "A foe held in a bubble takes double: held " + heldLoss + " free " + freeLoss);
                h.assertTrue(InvisibilityService.isFaded(p) && !ForceFieldService.contained(held), "The caster vanishes after the flash");
                h.succeed();
            } finally { discard(held, free); clean(p); }
        });
    }

    // ================================================================ The tree

    @GameTest(template = "rewind_empty", batch = "force.touch")
    public static void vanishingTouchHidesAVillagerAndItsHunterLosesIt(GameTestHelper h) {
        ServerPlayer p = player(h, 5, 3);
        var villager = EntityType.VILLAGER.create(h.getLevel());
        villager.setNoAi(true); villager.setNoGravity(true);
        villager.moveTo(p.getX(), p.getY(), p.getZ() + 4, 180, 0);
        h.getLevel().addFreshEntity(villager);
        var hunter = enemy(h, p, 3, 8);
        hunter.setTarget(villager);
        try {
            h.assertTrue(InvisibilitySkills.skill(context(p), 0), "Vanishing Touch must take the villager under the aim");
            h.assertTrue(InvisibilityService.isHidden(villager) && villager.hasEffect(MobEffects.INVISIBILITY),
                    "The villager must be hidden");
            h.assertTrue(hunter.getTarget() == null, "The hunter must lose the hidden villager");
            hunter.setTarget(villager);
            h.assertTrue(hunter.getTarget() == null, "A distant hunter cannot pick the hidden villager out again");
            h.succeed();
        } finally { discard(villager, hunter); clean(p); }
    }
    @GameTest(template = "rewind_empty", batch = "force.squad")
    public static void vanishingSquadFadesFriendsAndPetsAtOnce(GameTestHelper h) {
        ServerPlayer p = player(h, 5, 3), friend = player(h, 5, 7);
        friend.setShiftKeyDown(true);
        var wolf = EntityType.WOLF.create(h.getLevel());
        wolf.setNoAi(true); wolf.tame(p);
        wolf.moveTo(p.getX() + 2, p.getY(), p.getZ(), 0, 0);
        h.getLevel().addFreshEntity(wolf);
        try {
            h.assertTrue(InvisibilitySkills.technique(context(p), "smoke_and_mirrors"), "Vanishing Squad must cast");
            h.assertTrue(InvisibilityService.isUnseen(p) && InvisibilityService.isUnseen(friend) && InvisibilityService.isHidden(wolf)
                    && InvisibilityService.squadActive(p), "The caster, a consenting friend and a pet must all fade");
            h.succeed();
        } finally { wolf.discard(); clean(p, friend); }
    }
    @GameTest(template = "rewind_empty", batch = "force.disc")
    public static void forceDiscCarriesItsRiderAlongTheirAimUntilTheyCrouch(GameTestHelper h) {
        ServerPlayer p = player(h, 5, 3);
        try {
            h.assertTrue(InvisibilitySkills.technique(context(p), "pickpocket") && ForceFieldService.riding(p), "Force Disc must start");
        } catch (RuntimeException e) { clean(p); throw e; }
        h.runAfterDelay(3, () -> {
            try {
                h.assertTrue(p.getDeltaMovement().z > .6, "The disc must carry its rider along their aim: " + p.getDeltaMovement());
                p.setShiftKeyDown(true);
            } catch (RuntimeException e) { clean(p); throw e; }
        });
        h.runAfterDelay(5, () -> {
            try {
                h.assertTrue(!ForceFieldService.riding(p) && p.hasEffect(MobEffects.SLOW_FALLING), "Crouching steps off and drifts down");
                h.succeed();
            } finally { clean(p); }
        });
    }
    @GameTest(template = "rewind_empty", batch = "force.net")
    public static void safetyNetCatchesALongFallOnceEveryTenSeconds(GameTestHelper h) {
        ServerPlayer p = player(h, 5, 3);
        learn(p, InvisibilitySkills.SAFETY_NET);
        try {
            float full = p.getHealth();
            p.causeFallDamage(12, 1, p.damageSources().fall());
            h.assertTrue(p.getHealth() == full, "The net must catch the first long fall");
            p.causeFallDamage(12, 1, p.damageSources().fall());
            h.assertTrue(p.getHealth() < full, "The net needs time to form again");
            h.succeed();
        } finally { clean(p); }
    }
    @GameTest(template = "rewind_empty", batch = "force.pinpoint")
    public static void pinpointHurtsAndPopsAFoeIntoTheAir(GameTestHelper h) {
        ServerPlayer p = player(h, 5, 3);
        var foe = enemy(h, p, 6);
        try {
            h.assertTrue(InvisibilitySkills.skill(context(p), 2), "Pinpoint must strike the foe under the aim");
            h.assertTrue(foe.getHealth() < foe.getMaxHealth() && foe.getDeltaMovement().y > .5, "Pinpoint must hurt and pop the foe up");
            h.succeed();
        } finally { discard(foe); clean(p); }
    }
    @GameTest(template = "rewind_empty", batch = "force.shell")
    public static void kineticShellSealsSeveralFoesAndOneCrushClosesThemAll(GameTestHelper h) {
        ServerPlayer p = player(h, 1, 3);
        p.setXRot(30);
        List<Mob> foes = List.of(enemy(h, p, -1.5, 5), enemy(h, p, 1.5, 5), enemy(h, p, 0, 7));
        try {
            h.assertTrue(InvisibilitySkills.technique(context(p), "cloak_field"), "Kinetic Shell must cast at the aimed ground");
            h.assertTrue(foes.stream().allMatch(ForceFieldService::contained), "Every foe near the aim must be sealed");
            h.assertTrue(ForceFieldService.crush(p), "One press of Crush closes them all");
        } catch (RuntimeException e) { foes.forEach(Mob::discard); clean(p); throw e; }
        h.runAfterDelay(12, () -> {
            try {
                h.assertTrue(foes.stream().allMatch(f -> f.getHealth() < f.getMaxHealth()), "Each sealed foe must be crushed");
                h.succeed();
            } finally { foes.forEach(Mob::discard); clean(p); }
        });
    }
    @GameTest(template = "rewind_empty", batch = "force.chain")
    public static void pressureChainBurstsACrushedBubbleOntoItsNeighbours(GameTestHelper h) {
        ServerPlayer p = player(h, 1, 3);
        learn(p, InvisibilitySkills.PRESSURE_CHAIN);
        var held = enemy(h, p, 5);
        var neighbour = enemy(h, p, 2, 5);
        ForceFieldService.contain(p, held, 100, false);
        ForceFieldService.crush(p);
        h.runAfterDelay(12, () -> {
            try {
                h.assertTrue(neighbour.getHealth() < neighbour.getMaxHealth(), "The burst must reach the neighbouring foe");
                h.succeed();
            } finally { discard(held, neighbour); clean(p); }
        });
    }
    @GameTest(template = "rewind_empty", batch = "force.implosion")
    public static void implosionSealsFoesSoTheNovaStrikesThemDouble(GameTestHelper h) {
        ServerPlayer p = player(h, 1, 3);
        learn(p, InvisibilitySkills.IMPLOSION);
        // A creature with its own movement: a no-AI mob cannot be shifted by velocity at all.
        var far = EntityType.PIG.create(h.getLevel());
        far.moveTo(p.getX(), p.getY(), p.getZ() + 7, 180, 0);
        h.getLevel().addFreshEntity(far);
        far.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(60);
        far.setHealth(60);
        ForceFieldService.nova(p);
        h.runAfterDelay(10, () -> {
            try {
                h.assertTrue(far.getZ() < p.getZ() + 7 - 1, "Implosion must draw foes inward while it gathers: " + (far.getZ() - p.getZ()));
            } catch (RuntimeException e) { discard(far); clean(p); throw e; }
        });
        h.runAfterDelay(23, () -> {
            try {
                // A free foe this far out would lose well under 14; sealed, it takes double.
                h.assertTrue(60 - far.getHealth() > 14, "Implosion's seal must double the burst: lost " + (60 - far.getHealth()));
                h.succeed();
            } finally { discard(far); clean(p); }
        });
    }
    @GameTest(template = "rewind_empty", batch = "force.unseen")
    public static void unseenForceStrikesFromConcealmentWithoutRevealing(GameTestHelper h) {
        ServerPlayer p = player(h, 5, 3);
        learn(p, InvisibilitySkills.UNSEEN_FORCE);
        var foe = enemy(h, p, 6);
        InvisibilityService.toggleFade(p);
        try {
            h.assertTrue(InvisibilitySkills.skill(context(p), 2), "Pinpoint must strike");
            h.assertTrue(InvisibilityService.isUnseen(p), "With Unseen Force a force move must not give its caster away");
            h.assertTrue(foe.getMaxHealth() - foe.getHealth() > 11 * 1.3F, "Unseen Force hits harder: " + foe.getHealth());
            h.succeed();
        } finally { discard(foe); clean(p); }
    }
    @GameTest(template = "rewind_empty", batch = "force.reveal")
    public static void aForceMoveWithoutUnseenForceRevealsItsCaster(GameTestHelper h) {
        ServerPlayer p = player(h, 5, 3);
        var foe = enemy(h, p, 6);
        InvisibilityService.toggleFade(p);
        try {
            h.assertTrue(InvisibilitySkills.skill(context(p), 2) && InvisibilityService.isFaded(p) && !InvisibilityService.isUnseen(p),
                    "A force strike flickers its caster visible");
            h.succeed();
        } finally { discard(foe); clean(p); }
    }
    @GameTest(template = "rewind_empty", batch = "optic.cleanup")
    public static void suppressionEndsConcealmentAndHeldFields(GameTestHelper h) {
        ServerPlayer p = player(h, 5, 3);
        InvisibilityService.toggleFade(p);
        ForceFieldService.raiseShield(p);
        PowerRules.rewinding(p, true);
        h.runAfterDelay(2, () -> {
            try {
                h.assertTrue(!InvisibilityService.isFaded(p) && !ForceFieldService.shielding(p),
                        "Immobilization must end concealment and the held shield");
                h.succeed();
            } finally { clean(p); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "optic.skills")
    public static void redesignedSkillNodesKeepTheirSavedIdentitiesAndDoNotGrantOldStatBonuses(GameTestHelper h) {
        ServerPlayer p = player(h, 5, 3);
        try {
            var nodes = SkillCatalog.nodes("invisibility");
            h.assertTrue(nodes.size() == 18 && nodes.stream().filter(SkillCatalog.Node::active).count() == 6,
                    "The three branches retain eighteen nodes and six active techniques");
            h.assertTrue(nodes.stream().anyMatch(n -> n.id().equals("invisibility_cloak_field") && n.name().equals("Kinetic Shell")),
                    "Kinetic Shell keeps the saved Cloak Field ID");
            learn(p, "invisibility_0_0", "invisibility_0_2", "invisibility_1_0", "invisibility_1_2", "invisibility_2_0", "invisibility_2_2");
            for (int branch = 0; branch < 3; branch++) h.assertTrue(SkillTreeService.bonus(p, ModAbilities.INVISIBILITY_ID, branch) == 0,
                    "Named interaction passives must not silently retain the original blanket stat bonus");
            h.succeed();
        } finally { clean(p); }
    }
    @GameTest(template = "rewind_empty", batch = "optic.quiet_crouch")
    public static void learningQuietLightDoesNotWeakenCrouchedRecovery(GameTestHelper h) {
        ServerPlayer ordinary = player(h, 3, 3), learned = player(h, 3, 7);
        try {
            learn(learned, InvisibilitySkills.QUIET_LIGHT);
            for (ServerPlayer p : new ServerPlayer[]{ordinary, learned}) {
                InvisibilityService.toggleFade(p); InvisibilityService.addExposure(p, 30); p.setShiftKeyDown(true);
            }
            for (int tick = 0; tick < 12; tick++) {
                ordinary.setPos(ordinary.position().add(0, 0, .1));
                learned.setPos(learned.position().add(0, 0, .1));
                InvisibilityService.tick(h.getLevel().getServer());
            }
            h.assertTrue(InvisibilityService.exposure(learned) <= InvisibilityService.exposure(ordinary)
                            && InvisibilityService.exposure(learned) < 30,
                    "Learning quiet movement must preserve or improve concealment recovery while crouching");
            h.succeed();
        } finally { clean(ordinary, learned); }
    }
}
