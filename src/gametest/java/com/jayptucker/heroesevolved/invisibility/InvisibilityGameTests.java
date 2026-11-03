package com.jayptucker.heroesevolved.invisibility;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Concealment, counterplay and cleanup remain server-owned even when every silhouette is cosmetic. */
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
    private static void clean(ServerPlayer... players) {
        for (ServerPlayer p : players) {
            PowerRules.rewinding(p, false);
            InvisibilityService.forget(p); OpticalDecoyService.clear(p); p.discard();
        }
    }
    private static Mob enemy(GameTestHelper h, ServerPlayer p, double distance) {
        var mob = EntityType.HUSK.create(h.getLevel());
        mob.setNoAi(true); mob.setNoGravity(true); mob.setPersistenceRequired();
        mob.moveTo(p.getX(), p.getY(), p.getZ() + distance, 180, 0);
        h.getLevel().addFreshEntity(mob);
        return mob;
    }
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
        novice.setSprinting(true); veteran.setSprinting(true);
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
                h.assertTrue(!InvisibilityService.isUnseen(p), "A sensing power still pierces Ghost-style concealment");
                h.succeed();
            } finally { clean(p); }
        });
    }
    @GameTest(template = "rewind_empty", batch = "optic.shared")
    public static void sharedVeilConcealsFriendsButAnAttackRevealsOnlyItsAttacker(GameTestHelper h) {
        ServerPlayer owner = player(h, 5, 3), friend = player(h, 5, 5);
        friend.setShiftKeyDown(true);
        var victim = enemy(h, friend, 3);
        InvisibilityService.shroud(owner);
        h.runAfterDelay(12, () -> {
            try {
                h.assertTrue(InvisibilityService.veilActive(owner) && InvisibilityService.isUnseen(owner)
                                && InvisibilityService.isUnseen(friend), "A moving veil must cover consenting companions");
                victim.hurt(friend.damageSources().playerAttack(friend), 1);
                h.assertTrue(!InvisibilityService.isUnseen(friend) && InvisibilityService.isUnseen(owner),
                        "Attacking must expose the attacker without exposing the whole team");
                h.succeed();
            } finally { victim.discard(); clean(owner, friend); }
        });
    }
    @GameTest(template = "rewind_empty", batch = "optic.dash")
    public static void refractionStepMovesPhysicallyAndStopsAtSolidWalls(GameTestHelper h) {
        ServerPlayer p = player(h, 5, 3);
        BlockPos wall = h.absolutePos(new BlockPos(3, 1, 7));
        for (int x = -1; x <= 1; x++) for (int y = 0; y < 3; y++)
            h.getLevel().setBlock(wall.offset(x, y, 0), Blocks.STONE.defaultBlockState(), 3);
        Vec3 before = p.position();
        try {
            h.assertTrue(InvisibilityService.silentStep(p, 5), "Refraction Step must begin: position=" + p.position()
                    + ", canAct=" + PowerRules.canAct(p) + ", passenger=" + p.isPassenger()
                    + ", safeAhead=" + com.jayptucker.heroesevolved.speed.SpeedService.safe(p, p.position().add(0, 0, .25))
                    + ", collision=" + h.getLevel().getBlockCollisions(p, p.getBoundingBox()).iterator().hasNext()
                    + ", box=" + p.getBoundingBox());
        } catch (Throwable failure) { clean(p); throw failure; }
        h.onEachTick(() -> { if (InvisibilityService.activeDash(p)) p.move(net.minecraft.world.entity.MoverType.SELF, p.getDeltaMovement()); });
        h.runAfterDelay(12, () -> {
            try {
                h.assertTrue(p.getZ() > before.z && p.getZ() < wall.getZ(),
                        "The dash travels through free space and cannot pass through a solid wall");
                h.assertTrue(!InvisibilityService.activeDash(p), "The short motion ends without retaining control");
                h.succeed();
            } finally { clean(p); }
        });
    }
    @GameTest(template = "rewind_empty", batch = "optic.decoy_interaction")
    public static void opticalDoublesRequireReachAndShatterOnlyOnce(GameTestHelper h) {
        ServerPlayer p = player(h, 5, 3), distant = player(h, 5, 10);
        try {
            h.assertTrue(OpticalDecoyService.spawnMirage(p, 5), "Mirage creates a public optical double");
            var decoy = OpticalDecoyService.snapshots(p).getFirst();
            h.assertTrue(!OpticalDecoyService.dissolve(distant, decoy.id()),
                    "An arbitrary ID cannot dissolve an out-of-reach or unobserved double");
            h.assertTrue(OpticalDecoyService.dissolve(p, decoy.id()) && OpticalDecoyService.count(p) == 0,
                    "An actual nearby test must shatter the optical construction");
            h.assertTrue(!OpticalDecoyService.dissolve(p, decoy.id()), "A dissolved double cannot be consumed twice");
            h.succeed();
        } finally { clean(p, distant); }
    }
    @GameTest(template = "rewind_empty", batch = "optic.decoy_search")
    public static void hostileMobsInvestigateAnOpticalDoubleInsteadOfItsHiddenOwner(GameTestHelper h) {
        ServerPlayer p = player(h, 5, 3);
        var hunter = enemy(h, p, 6);
        hunter.setTarget(p); InvisibilityService.toggleFade(p);
        OpticalDecoyService.spawnMirage(p, 5);
        h.runAfterDelay(7, () -> {
            try {
                h.assertTrue(hunter.getTarget() == null && OpticalDecoyService.isInvestigating(hunter),
                        "The hunter must investigate the false route rather than retain a live target lock");
                h.succeed();
            } finally { hunter.discard(); clean(p); }
        });
    }
    @GameTest(template = "rewind_empty", batch = "optic.cleanup")
    public static void suppressionClearsConcealmentAndCappedDoubles(GameTestHelper h) {
        ServerPlayer p = player(h, 5, 3);
        InvisibilityService.toggleFade(p); OpticalDecoyService.burst(p, 12);
        h.assertTrue(OpticalDecoyService.count(p) == 3, "A burst cannot exceed three doubles per caster");
        PowerRules.rewinding(p, true);
        h.runAfterDelay(2, () -> {
            try {
                h.assertTrue(!InvisibilityService.isFaded(p) && OpticalDecoyService.count(p) == 0,
                        "Immobilization must end persistent concealment and optical constructions");
                h.succeed();
            } finally { clean(p); }
        });
    }
    @GameTest(template = "rewind_empty", batch = "optic.potion_restore")
    public static void revealsSuspendAndUnfadeRestoresAnExistingInvisibilityPotion(GameTestHelper h) {
        ServerPlayer p = player(h, 5, 3);
        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.INVISIBILITY,
                200, 0, false, false));
        InvisibilityService.toggleFade(p);
        InvisibilityService.reveal(p, 20);
        h.assertTrue(!InvisibilityService.isUnseen(p)
                        && !p.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY),
                "A reveal must remove concealment, rather than leave the power potion hiding the body");
        h.runAfterDelay(6, () -> {
            try {
                InvisibilityService.unfade(p);
                var restored = p.getEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY);
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
        var context = new com.jayptucker.heroesevolved.ability.AbilityUseContext(mimic, ModAbilities.MIMICRY_ID, 5, false);
        h.assertTrue(echo.canUse(context) && echo.activate(context)
                        == com.jayptucker.heroesevolved.ability.AbilityActivationResult.SUCCESS,
                "Echo I can borrow the redesigned Fade");
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

    @GameTest(template = "rewind_empty", batch = "optic.skills")
    public static void redesignedSkillNodesKeepTheirSavedIdentitiesAndDoNotGrantOldStatBonuses(GameTestHelper h) {
        ServerPlayer p = player(h, 5, 3);
        try {
            var nodes = SkillCatalog.nodes("invisibility");
            h.assertTrue(nodes.size() == 18 && nodes.stream().filter(SkillCatalog.Node::active).count() == 6,
                    "The three redesigned branches retain eighteen nodes and six active techniques");
            h.assertTrue(nodes.stream().anyMatch(n -> n.id().equals("invisibility_cloak_field") && n.name().equals("Hidden Passage")),
                    "The new corridor retains the saved Cloak Field ID");
            p.setData(ModDataAttachments.SKILL_TREES, SkillTreeData.empty().with(ModAbilities.INVISIBILITY_ID.toString(),
                    new SkillTreeData.Build(15, Set.of("invisibility_0_0", "invisibility_0_2", "invisibility_1_0",
                            "invisibility_1_2", "invisibility_2_0", "invisibility_2_2"), Map.of())));
            for (int branch = 0; branch < 3; branch++) h.assertTrue(SkillTreeService.bonus(p, ModAbilities.INVISIBILITY_ID, branch) == 0,
                    "Named interaction passives must not silently retain the original blanket stat bonus");
            h.succeed();
        } finally { clean(p); }
    }
    @GameTest(template = "rewind_empty", batch = "optic.quiet_crouch")
    public static void learningQuietFootfallDoesNotWeakenCrouchedRecovery(GameTestHelper h) {
        ServerPlayer ordinary = player(h, 3, 3), learned = player(h, 3, 7);
        try {
            learned.setData(ModDataAttachments.SKILL_TREES, SkillTreeData.empty().with(ModAbilities.INVISIBILITY_ID.toString(),
                    new SkillTreeData.Build(15, Set.of("invisibility_0_0"), Map.of())));
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
