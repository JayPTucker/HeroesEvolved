package com.jayptucker.heroesevolved.telekinesis;

import com.jayptucker.heroesevolved.ability.*;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.AbilityUseService;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.skills.*;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class TelekinesisSkillGameTests {
    private static final Set<String> ACTIVES = Set.of("telekinesis_0_1", "telekinesis_1_1", "telekinesis_2_1",
            "telekinesis_remote_hand", "telekinesis_kinetic_volley", "telekinesis_force_bubble");

    private static ServerPlayer player(GameTestHelper h) {
        var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "kinetic-test"));
        try {
            Connection connection = p.connection.getConnection();
            var channel = Connection.class.getDeclaredField("channel"); channel.setAccessible(true);
            if (connection.channel() == null) channel.set(connection, new EmbeddedChannel());
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 40, 3))));
        p.setYRot(0); p.setXRot(0);
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty()
                .assignDormant(ModAbilities.TELEKINESIS_ID).unlock(ModAbilities.TELEKINESIS_ID));
        PlayerProgressionService.setLevel(p, 5);
        PlayerEnergyService.restore(p, PlayerEnergyService.getMaximumEnergy(p));
        return p;
    }

    private static void learn(ServerPlayer p, String... upgrades) {
        var ids = new HashSet<>(ACTIVES);
        for (String suffix : upgrades) ids.add("telekinesis_" + suffix);
        p.setData(ModDataAttachments.SKILL_TREES, SkillTreeData.empty().with(ModAbilities.TELEKINESIS_ID.toString(),
                new SkillTreeData.Build(5, ids, Map.of(), 6)));
    }

    private static LivingEntity target(GameTestHelper h, Vec3 at) {
        var mob = EntityType.PILLAGER.create(h.getLevel());
        mob.setNoAi(true); mob.setNoGravity(true); mob.setPersistenceRequired(); mob.setPos(at);
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100); mob.setHealth(100);
        h.getLevel().addFreshEntity(mob); return mob;
    }
    private static void aim(ServerPlayer p, Vec3 point) {
        Vec3 d = point.subtract(p.getEyePosition());
        p.setYRot((float) Math.toDegrees(Math.atan2(-d.x, d.z)));
        p.setXRot((float) -Math.toDegrees(Math.atan2(d.y, d.horizontalDistance())));
    }
    private static void tick(ServerPlayer p, int ticks) {
        for (int i = 0; i < ticks; i++) {
            TelekineticArsenalService.tick(p.getServer());
            TelekinesisSkillService.tick(p.getServer());
        }
    }
    private static void cleanup(ServerPlayer p, Entity... targets) {
        PowerRules.rewinding(p, false); TelekinesisService.stopAll(p);
        for (Entity target : targets) target.discard();
    }
    private static void cast(GameTestHelper h, ServerPlayer p, String suffix, boolean modifier) {
        h.assertTrue(TelekinesisSkillService.activate(p, "telekinesis_" + suffix, modifier), "Learned technique should cast: " + suffix);
    }

    @GameTest(template = "rewind_empty", batch = "kinetic.skills_catalog")
    public static void allEighteenNodesRemainPurchasableAndSavedLoadoutsResolve(GameTestHelper h) {
        var nodes = SkillCatalog.nodes("telekinesis");
        h.assertTrue(nodes.size() == 18 && nodes.stream().filter(SkillCatalog.Node::active).map(SkillCatalog.Node::id)
                .collect(Collectors.toSet()).equals(ACTIVES), "All eighteen purchases and six old action IDs must survive");
        for (int branch = 0; branch < 3; branch++) {
            var p = player(h);
            try {
                int slot = 0;
                for (var node : nodes) if (node.branch() == branch) {
                    h.assertTrue(SkillTreeService.purchase(p, node.id()), "A single complete branch must fit the point budget: " + node.id());
                    if (node.active()) h.assertTrue(SkillTreeService.equip(p, AbilitySlot.values()[slot++], node.id()), "Saved active must equip");
                }
                var data = p.getData(ModDataAttachments.SKILL_TREES);
                var encoded = SkillTreeData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
                var loaded = SkillTreeData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
                h.assertTrue(data.equals(loaded), "Purchases and slots must roundtrip unchanged");
                p.setData(ModDataAttachments.SKILL_TREES, loaded);
                for (var entry : loaded.build(ModAbilities.TELEKINESIS_ID.toString()).slots().entrySet())
                    h.assertTrue(SkillTreeService.actions(p, ModAbilities.TELEKINESIS_ID).get(AbilitySlot.valueOf(entry.getKey()))
                            .definition().id().getPath().equals(entry.getValue()), "Old equipped IDs must resolve to new mechanics");
                for (int lane = 0; lane < 3; lane++) h.assertTrue(SkillTreeService.bonus(p, ModAbilities.TELEKINESIS_ID, lane) == 0,
                        "Interaction nodes must not secretly retain old blanket stat boosts");
            } finally { cleanup(p); }
        }
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "kinetic.lance")
    public static void lanceHasTravelTimeAndSweepsThroughARealTarget(GameTestHelper h) {
        var p = player(h); learn(p);
        var victim = target(h, p.position().add(0, 0, 10)); aim(p, victim.getBoundingBox().getCenter());
        try {
            cast(h, p, "0_1", false);
            h.assertTrue(victim.getHealth() == 100 && TelekineticArsenalService.flyingCount(p) == 1,
                    "A cast creates a real traveling fragment, without instant target damage");
            tick(p, 8);
            h.assertTrue(victim.getHealth() < 100 && TelekineticArsenalService.flyingCount(p) == 0,
                    "Swept travel must eventually collide with the creature");
        } finally { cleanup(p, victim); }
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "kinetic.cover")
    public static void lanceStopsAtCoverInsteadOfDamagingAnEnemyBehindIt(GameTestHelper h) {
        var p = player(h); learn(p);
        var victim = target(h, p.position().add(0, 0, 10)); aim(p, victim.getBoundingBox().getCenter());
        BlockPos wall = p.blockPosition().offset(0, 0, 4);
        try {
            for (int x = -2; x <= 2; x++) for (int y = 0; y < 4; y++) h.getLevel().setBlock(wall.offset(x, y, 0), Blocks.STONE.defaultBlockState(), 3);
            cast(h, p, "0_1", false); tick(p, 20);
            h.assertTrue(victim.getHealth() == 100 && TelekineticArsenalService.flyingCount(p) == 0,
                    "Solid cover must intercept the fragment with no damage behind it");
            h.assertTrue(h.getLevel().getBlockState(wall).is(Blocks.STONE), "Kinetic fragments must not destroy terrain");
        } finally {
            for (int x = -2; x <= 2; x++) for (int y = 0; y < 4; y++) h.getLevel().setBlock(wall.offset(x, y, 0), Blocks.AIR.defaultBlockState(), 3);
            cleanup(p, victim);
        }
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "kinetic.pierce")
    public static void threadTheNeedleChangesCollisionToPierceTwoCreatures(GameTestHelper h) {
        var p = player(h); learn(p, "0_2");
        var first = target(h, p.position().add(0, 0, 5)); var second = target(h, p.position().add(0, 0, 8));
        aim(p, second.getBoundingBox().getCenter());
        try {
            cast(h, p, "0_1", false); tick(p, 10);
            h.assertTrue(first.getHealth() < 100 && second.getHealth() < 100, "The upgraded physical lance must pierce the first creature");
        } finally { cleanup(p, first, second); }
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "kinetic.volley")
    public static void volleyGathersThenLaunchesStaggeredShotsAndFiveFragmentsChangesCapacity(GameTestHelper h) {
        var p = player(h); learn(p, "five_fragments");
        try {
            cast(h, p, "kinetic_volley", false);
            h.assertTrue(TelekineticArsenalService.orbitCount(p) == 5 && TelekineticArsenalService.flyingCount(p) == 0,
                    "The upgraded volley must visibly gather five fragments before firing");
            tick(p, 8);
            h.assertTrue(TelekineticArsenalService.flyingCount(p) == 1 && TelekineticArsenalService.orbitCount(p) == 4,
                    "Shots must leave the orbit in a sequence rather than an instant damage loop");
            tick(p, 120);
            h.assertTrue(TelekineticArsenalService.flyingCount(p) == 0 && TelekineticArsenalService.orbitCount(p) == 0,
                    "Every missed shot must eventually expire");
        } finally { cleanup(p); }
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "kinetic.orbit")
    public static void orbitStaysBesideTheCameraAtEveryPitchAndCleansOnSuppression(GameTestHelper h) {
        var p = player(h); learn(p, "1_0", "five_fragments");
        try {
            TelekineticArsenalService.gather(p, 20);
            h.assertTrue(TelekineticArsenalService.orbitCount(p) == 5, "Repeated gathering must respect the capacity");
            for (float yaw : new float[]{0, 90, 180, -90}) for (float pitch : new float[]{-90, 0, 90}) {
                p.setYRot(yaw); p.setXRot(pitch);
                for (int slot = 0; slot < 5; slot++) {
                    Vec3 delta = TelekineticArsenalService.orbitPoint(p, slot, 20).subtract(p.position());
                    Vec3 forward = Vec3.directionFromRotation(0, yaw);
                    h.assertTrue(delta.horizontalDistance() > 1.2 && delta.dot(forward) < 0,
                            "Orbit must stay outside and behind the forward camera line even while looking vertically");
                }
            }
            PowerRules.rewinding(p, true); tick(p, 1);
            h.assertTrue(TelekineticArsenalService.orbitCount(p) == 0 && TelekineticArsenalService.flyingCount(p) == 0,
                    "Suppression must remove all transient display entities and projectiles");
        } finally { cleanup(p); }
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "kinetic.well")
    public static void aimedWellPullsToItsAnchorAndRecastReleasesWithoutChargingAgain(GameTestHelper h) {
        var p = player(h); learn(p);
        var anchor = target(h, p.position().add(0, 0, 8)); var victim = target(h, p.position().add(2, 0, 8));
        aim(p, anchor.getBoundingBox().getCenter());
        try {
            h.assertTrue(SkillTreeService.equip(p, AbilitySlot.PRIMARY, "telekinesis_1_1"), "Well should equip");
            h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.PRIMARY, false) == AbilityUseResult.SUCCESS, "Well should cast normally");
            tick(p, 4);
            h.assertTrue(victim.getDeltaMovement().x < 0, "The well must pull toward the aimed anchor, not the caster");
            int energy = PlayerEnergyService.getEnergy(p);
            h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.PRIMARY, false) == AbilityUseResult.SUCCESS,
                    "A recast must release an existing field even while its cast cooldown is active");
            h.assertTrue(victim.getDeltaMovement().y > .8 && PlayerEnergyService.getEnergy(p) == energy,
                    "Release must loft gathered enemies without charging another activation");
            victim.setDeltaMovement(Vec3.ZERO); tick(p, 4);
            h.assertTrue(victim.getDeltaMovement().lengthSqr() == 0, "Released field must stop pulling");
        } finally { cleanup(p, anchor, victim); }
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "kinetic.crossfire")
    public static void wallImpactConsumesAFragmentAndFiresAtASecondEnemy(GameTestHelper h) {
        var p = player(h); learn(p, "1_2");
        var struck = target(h, p.position().add(0, 0, 5)); var other = target(h, p.position().add(3, 0, 5));
        try {
            TelekineticArsenalService.gather(p, 2); tick(p, 12);
            TelekinesisSkillService.onImpact(p, struck, struck.position(), 6, false);
            h.assertTrue(TelekineticArsenalService.flyingCount(p) == 1 && TelekineticArsenalService.orbitCount(p) == 1,
                    "Crossfire must spend an orbiting fragment on a distinct target");
            h.assertTrue(other.getHealth() == 100, "Crossfire must travel rather than damage instantly");
        } finally { cleanup(p, struck, other); }
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "kinetic.bubble")
    public static void counterbatteryRedirectsIncomingEnemyArrowsAndLeavesAlliedShotsAlone(GameTestHelper h) {
        var p = player(h); learn(p, "bastion");
        var enemy = target(h, p.position().add(0, 0, 8));
        var ally = target(h, p.position().add(1, 0, 8));
        var team = p.getScoreboard().addPlayerTeam("tk-" + UUID.randomUUID().toString().substring(0, 8));
        p.getScoreboard().addPlayerToTeam(p.getScoreboardName(), team); p.getScoreboard().addPlayerToTeam(ally.getScoreboardName(), team);
        Arrow hostile = new Arrow(EntityType.ARROW, h.getLevel()), friendly = new Arrow(EntityType.ARROW, h.getLevel());
        hostile.setOwner(enemy); friendly.setOwner(ally);
        hostile.setPos(p.position().add(0, 1, 3)); friendly.setPos(p.position().add(1, 1, 3));
        hostile.setDeltaMovement(0, 0, -.8); friendly.setDeltaMovement(0, 0, -.8);
        h.getLevel().addFreshEntity(hostile); h.getLevel().addFreshEntity(friendly);
        try {
            cast(h, p, "force_bubble", false); tick(p, 1);
            h.assertTrue(hostile.getOwner() == p && hostile.getDeltaMovement().z > 0, "Counterbattery must return an approaching enemy shot down the crosshair");
            h.assertTrue(friendly.getOwner() == ally && friendly.getDeltaMovement().z < 0, "Allied projectiles must keep their owner and trajectory");
        } finally { p.getScoreboard().removePlayerTeam(team); cleanup(p, enemy, ally, hostile, friendly); }
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "kinetic.retrieve")
    public static void retrieveExtractsOneActualStackWithoutTeleportingItIntoInventory(GameTestHelper h) {
        var p = player(h); learn(p, "retrieve");
        BlockPos pos = p.blockPosition().offset(0, 1, 6);
        h.getLevel().setBlock(pos, Blocks.CHEST.defaultBlockState(), 3);
        var chest = (ChestBlockEntity) h.getLevel().getBlockEntity(pos);
        chest.setItem(0, new ItemStack(Items.DIAMOND, 3)); chest.setItem(1, new ItemStack(Items.GOLD_INGOT, 4));
        aim(p, Vec3.atCenterOf(pos));
        try {
            cast(h, p, "remote_hand", false);
            var stacks = h.getLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(12), item -> item.getItem().is(Items.DIAMOND));
            h.assertTrue(chest.getItem(0).isEmpty() && chest.getItem(1).getCount() == 4 && stacks.size() == 1
                            && stacks.getFirst().getItem().getCount() == 3 && !p.getInventory().contains(new ItemStack(Items.DIAMOND)),
                    "Retrieve must conserve items, take one stack, and leave it traveling in the world");
            stacks.forEach(Entity::discard);
        } finally { h.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3); cleanup(p); }
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "kinetic.step")
    public static void vectorStepReversesAndGroundingCushionsExactlyOneLanding(GameTestHelper h) {
        var p = player(h); learn(p, "2_0");
        try {
            cast(h, p, "2_1", true);
            h.assertTrue(p.getDeltaMovement().z < 0 && p.getDeltaMovement().y > .5, "Modifier step must vault backward with actual velocity");
            h.assertTrue(TelekinesisSkillService.cushion(p) && !TelekinesisSkillService.cushion(p), "Grounding must protect exactly one landing");
            cast(h, p, "2_1", false); TelekinesisSkillService.forget(p);
            h.assertTrue(!TelekinesisSkillService.cushion(p), "Revocation must clear unspent landing protection");
        } finally { cleanup(p); }
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "kinetic.reject")
    public static void remoteHandWithoutAValidTargetDoesNotSpendEnergy(GameTestHelper h) {
        var p = player(h); learn(p);
        try {
            h.assertTrue(SkillTreeService.equip(p, AbilitySlot.PRIMARY, "telekinesis_remote_hand"), "Learned remote hand must equip");
            int energy = PlayerEnergyService.getEnergy(p);
            h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.PRIMARY, false) == AbilityUseResult.CANNOT_USE
                    && PlayerEnergyService.getEnergy(p) == energy, "No-target casts must not charge stamina");
        } finally { cleanup(p); }
        h.succeed();
    }
}
