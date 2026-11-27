package com.jayptucker.heroesevolved.telekinesis;

import com.jayptucker.heroesevolved.ability.AbilitySlot;
import com.jayptucker.heroesevolved.ability.AbilityUseResult;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.AbilityUseService;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.energy.StaminaDrain;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.skills.SkillCatalog;
import com.jayptucker.heroesevolved.skills.SkillTreeData;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.jayptucker.heroesevolved.telekinesis.KineticGeometry.*;
import static com.jayptucker.heroesevolved.telekinesis.TelekinesisSkillService.*;

/** The far hand's tree and its new core moves: Pin, Juggle and Spike, Skyfall, Carom, swinging and throwing, Levitate. */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class TelekinesisFarHandGameTests {
    private static final Set<String> ACTIVES = Set.of(PIN, ROUND_UP, STEP, BECKON, JUGGLE, LEVITATE);

    private static ServerPlayer player(GameTestHelper h) {
        var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "far-hand"));
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
        for (String id : upgrades) ids.add(id.startsWith("telekinesis_") ? id : "telekinesis_" + id);
        p.setData(ModDataAttachments.SKILL_TREES, SkillTreeData.empty().with(ModAbilities.TELEKINESIS_ID.toString(),
                new SkillTreeData.Build(5, ids, Map.of(), 9)));
    }

    /** A still, floating target: no AI, no gravity, a hundred health. */
    private static Mob target(GameTestHelper h, Vec3 at) {
        var mob = EntityType.PILLAGER.create(h.getLevel());
        mob.setNoAi(true); mob.setNoGravity(true); mob.setPersistenceRequired(); mob.setPos(at);
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100); mob.setHealth(100);
        h.getLevel().addFreshEntity(mob);
        return mob;
    }

    /** A target that obeys physics (a NoAI mob never moves under its own velocity), with no goals so it stands still. */
    private static Mob physical(GameTestHelper h, Vec3 at) {
        var mob = EntityType.PILLAGER.create(h.getLevel());
        mob.setPersistenceRequired(); mob.setPos(at);
        mob.goalSelector.removeAllGoals(goal -> true);
        mob.targetSelector.removeAllGoals(goal -> true);
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100); mob.setHealth(100);
        h.getLevel().addFreshEntity(mob);
        return mob;
    }

    private static List<BlockPos> floor(GameTestHelper h, Vec3 centre, int radius) {
        List<BlockPos> placed = new ArrayList<>();
        BlockPos base = BlockPos.containing(centre).below();
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
            BlockPos at = base.offset(x, 0, z);
            h.getLevel().setBlock(at, Blocks.STONE.defaultBlockState(), 3);
            placed.add(at);
        }
        return placed;
    }

    private static List<BlockPos> wall(GameTestHelper h, BlockPos at, int half, int height) {
        List<BlockPos> placed = new ArrayList<>();
        for (int x = -half; x <= half; x++) for (int y = -1; y < height; y++) {
            BlockPos block = at.offset(x, y, 0);
            h.getLevel().setBlock(block, Blocks.STONE.defaultBlockState(), 3);
            placed.add(block);
        }
        return placed;
    }

    private static void clearBlocks(GameTestHelper h, List<BlockPos> blocks) {
        for (BlockPos at : blocks) h.getLevel().setBlock(at, Blocks.AIR.defaultBlockState(), 3);
    }

    private static void aim(ServerPlayer p, Vec3 point) {
        Vec3 d = point.subtract(p.getEyePosition());
        p.setYRot((float) Math.toDegrees(Math.atan2(-d.x, d.z)));
        p.setXRot((float) -Math.toDegrees(Math.atan2(d.y, d.horizontalDistance())));
    }

    private static void cleanup(ServerPlayer p, Entity... targets) {
        PowerRules.rewinding(p, false);
        TelekinesisService.stopAll(p);
        for (Entity target : targets) target.discard();
    }

    private static void cast(GameTestHelper h, ServerPlayer p, String id, boolean modifier) {
        h.assertTrue(TelekinesisSkillService.activate(p, id, modifier), "The learned technique casts: " + id);
    }

    @GameTest(template = "rewind_empty", batch = "kinetic.far_catalog")
    public static void everyNodeIsPurchasableAndOldLoadoutsResolve(GameTestHelper h) {
        var nodes = SkillCatalog.nodes("telekinesis");
        var tree = nodes.stream().filter(n -> !n.signature()).toList();
        h.assertTrue(tree.size() == 18 && tree.stream().filter(SkillCatalog.Node::active).map(SkillCatalog.Node::id)
                .collect(Collectors.toSet()).equals(ACTIVES), "Eighteen nodes keep their six saved action IDs");
        var skyfall = nodes.stream().filter(SkillCatalog.Node::signature).toList();
        h.assertTrue(skyfall.size() == 1 && skyfall.getFirst().id().equals(SKYFALL) && skyfall.getFirst().active(),
                "One signature, Skyfall, sits on the Collision branch");
        for (int branch = 0; branch < 3; branch++) {
            var p = player(h);
            try {
                int slot = 0;
                for (var node : tree) if (node.branch() == branch) {
                    h.assertTrue(SkillTreeService.purchase(p, node.id()), "A complete branch fits the point budget: " + node.id());
                    if (node.active()) h.assertTrue(SkillTreeService.equip(p, AbilitySlot.values()[slot++], node.id()), "A saved active equips");
                }
                var data = p.getData(ModDataAttachments.SKILL_TREES);
                var loaded = SkillTreeData.CODEC.parse(JsonOps.INSTANCE, SkillTreeData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow()).getOrThrow();
                h.assertTrue(data.equals(loaded), "Purchases and slots round-trip unchanged");
                for (int lane = 0; lane < 3; lane++) h.assertTrue(SkillTreeService.bonus(p, ModAbilities.TELEKINESIS_ID, lane) == 0,
                        "Nodes unlock interactions, not blanket stat boosts");
            } finally { cleanup(p); }
        }
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "kinetic.far_pin", timeoutTicks = 120)
    public static void pinDrivesAFoeIntoTheWallAndHoldsIt(GameTestHelper h) {
        var p = player(h); learn(p);
        Vec3 start = p.position().add(0, 0, 3);
        var blocks = floor(h, start, 3);
        blocks.addAll(wall(h, p.blockPosition().offset(0, 0, 7), 2, 4));
        var foe = physical(h, start);
        aim(p, foe.getBoundingBox().getCenter());
        cast(h, p, PIN, false);
        h.runAfterDelay(15, () -> {
            try {
                h.assertTrue(foe.getBoundingBox().maxZ > start.z + 2 && foe.getBoundingBox().maxZ <= p.blockPosition().getZ() + 7.01,
                        "The palm drives the foe back to the wall and no further: " + foe.position());
                h.assertTrue(Math.abs(foe.getHealth() - (100 - (PIN_DAMAGE + 1.5F))) < .01, "One wall blow of 6 + half a point a tier over 2: " + foe.getHealth());
                h.assertTrue(TelekinesisService.isHeld(foe), "It is held against the wall");
            } catch (RuntimeException e) { clearBlocks(h, blocks); cleanup(p, foe); throw e; }
        });
        h.runAfterDelay(15 + PIN_HOLD + 6, () -> {
            try {
                h.assertTrue(!TelekinesisService.isHeld(foe), "The pin lets go after three seconds");
                h.assertTrue(Math.abs(foe.getHealth() - (100 - (PIN_DAMAGE + 1.5F))) < .01, "Holding never hurts it again");
                h.succeed();
            } finally { clearBlocks(h, blocks); cleanup(p, foe); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "kinetic.far_pin_together", timeoutTicks = 60)
    public static void pinnedTogetherCarriesTheFoeBehindAlong(GameTestHelper h) {
        var p = player(h); learn(p, "0_2");
        Vec3 first = p.position().add(0, 0, 3), second = p.position().add(0, 0, 5);
        var blocks = floor(h, first.add(0, 0, 1), 3);
        blocks.addAll(wall(h, p.blockPosition().offset(0, 0, 8), 2, 4));
        var a = physical(h, first);
        var b = physical(h, second);
        aim(p, a.getBoundingBox().getCenter());
        cast(h, p, PIN, false);
        h.runAfterDelay(15, () -> {
            try {
                h.assertTrue(TelekinesisService.isHeld(a) && TelekinesisService.isHeld(b), "Both are pinned");
                h.assertTrue(a.getHealth() < 100 && b.getHealth() < 100, "Both take the wall blow");
                h.succeed();
            } finally { clearBlocks(h, blocks); cleanup(p, a, b); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "kinetic.far_juggle", timeoutTicks = 120)
    public static void juggleKeepsFoesUpAndSpikeDrivesThemDown(GameTestHelper h) {
        var p = player(h); learn(p, "iron_rain");
        Vec3 spot = p.position().add(0, 0, 6);
        var blocks = floor(h, spot, 3);
        Mob a = physical(h, spot), b = physical(h, spot.add(1.5, 0, 0));
        aim(p, a.getBoundingBox().getCenter());
        h.assertTrue(SkillTreeService.equip(p, AbilitySlot.PRIMARY, JUGGLE), "Juggle equips");
        h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.PRIMARY, false) == AbilityUseResult.SUCCESS, "Juggle casts");
        h.runAfterDelay(JUGGLE_STAGGER + 3, () -> {
            try {
                h.assertTrue(Math.abs(a.getHealth() - (100 - JUGGLE_DAMAGE)) < .01 && Math.abs(b.getHealth() - (100 - JUGGLE_DAMAGE)) < .01,
                        "Each foe is popped once, two damage: " + a.getHealth() + ", " + b.getHealth());
                h.assertTrue(a.getY() > spot.y + .5, "And goes up");
                int energy = PlayerEnergyService.getEnergy(p);
                h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.PRIMARY, false) == AbilityUseResult.SUCCESS,
                        "A second press spikes them while they are still in the air");
                h.assertTrue(PlayerEnergyService.getEnergy(p) == energy, "Spike is free");
                h.assertTrue(a.getDeltaMovement().y < -1 && TelekinesisService.isSlammed(a) && TelekinesisService.isSlammed(b),
                        "Both are driven straight down");
            } catch (RuntimeException e) { clearBlocks(h, blocks); cleanup(p, a, b); throw e; }
        });
        h.runAfterDelay(JUGGLE_STAGGER + 25, () -> {
            try {
                float spike = SPIKE_DAMAGE + 2.5F;
                h.assertTrue(Math.abs(a.getHealth() - (100 - JUGGLE_DAMAGE - spike)) < .01, "The spike lands once, without fall damage: " + a.getHealth());
                h.succeed();
            } finally { clearBlocks(h, blocks); cleanup(p, a, b); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "kinetic.far_skyfall", timeoutTicks = 140)
    public static void skyfallHaulsAFoeUpAndDrivesItDownWhereItStood(GameTestHelper h) {
        var p = player(h); learn(p, "1_2", "skyfall");
        Vec3 spot = p.position().add(0, 0, 6);
        var blocks = floor(h, spot, 4);
        Mob foe = physical(h, spot), bystander = physical(h, spot.add(2.5, 0, 0));
        aim(p, foe.getBoundingBox().getCenter());
        cast(h, p, SKYFALL, false);
        h.runAfterDelay(SKYFALL_HAUL + 2, () -> {
            try {
                h.assertTrue(foe.getY() > spot.y + 10, "The far hand hauls it high into the sky: " + foe.getY());
                h.assertTrue(TelekinesisService.isHeld(foe) && foe.getHealth() == 100, "Held, not yet hurt");
            } catch (RuntimeException e) { clearBlocks(h, blocks); cleanup(p, foe, bystander); throw e; }
        });
        h.runAfterDelay(SKYFALL_IMPACT + 8, () -> {
            try {
                h.assertTrue(foe.getY() < spot.y + 1.5, "It comes down where it stood: " + foe.getY());
                h.assertTrue(Math.abs(foe.getHealth() - (100 - 17)) < .01, "12 + tier to the hauled foe, and no fall damage: " + foe.getHealth());
                h.assertTrue(Math.abs(bystander.getHealth() - (100 - 8.5F)) < .01, "6 + half a tier to a foe in the ring: " + bystander.getHealth());
                h.assertTrue(!TelekinesisService.isHeld(foe), "Released");
                h.succeed();
            } finally { clearBlocks(h, blocks); cleanup(p, foe, bystander); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "kinetic.far_carom", timeoutTicks = 60)
    public static void caromSendsAStruckBodyAtAnotherFoe(GameTestHelper h) {
        var p = player(h); learn(p, "1_0", "1_2");
        Vec3 spot = p.position().add(0, 0, 5);
        var blocks = floor(h, spot, 4);
        Mob struck = physical(h, spot), other = physical(h, spot.add(4, 0, 0));
        TelekinesisSkillService.onImpact(p, struck, struck.position(), 6, false, false);
        h.assertTrue(struck.getDeltaMovement().x > 1, "The struck body rebounds toward the other foe");
        h.runAfterDelay(10, () -> {
            try {
                h.assertTrue(other.getHealth() < 100, "It hits the other foe");
                h.succeed();
            } finally { clearBlocks(h, blocks); cleanup(p, struck, other); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "kinetic.far_swing")
    public static void aHeldBodySwungThroughAFoeBatsItAsideAndThrowsHarder(GameTestHelper h) {
        var p = player(h);
        var held = target(h, p.position().add(0, 0, 3));
        aim(p, held.getBoundingBox().getCenter());
        Mob victim = null;
        try {
            h.assertTrue(TelekinesisService.grip(p, 5), "The far hand takes hold");
            p.setXRot(0); p.setYRot(0);
            Vec3 before = held.position();
            p.setYRot(-80);
            Vec3 after = holdPoint(p, held);
            victim = target(h, before.lerp(after, .35));
            TelekinesisService.tick(p.getServer());
            h.assertTrue(victim.getHealth() < 100, "Swinging the held body through a foe bats it");
            h.assertTrue(victim.getDeltaMovement().lengthSqr() > .1, "And knocks it aside");
            h.assertTrue(TelekinesisService.throwGripped(p), "Then it is thrown");
            h.assertTrue(held.getDeltaMovement().length() > THROW_SPEED + .3, "Harder for the whip: " + held.getDeltaMovement().length());
            h.succeed();
        } finally { cleanup(p, held); if (victim != null) victim.discard(); }
    }

    @GameTest(template = "rewind_empty", batch = "kinetic.far_loose", timeoutTicks = 60)
    public static void gripTakesALooseItemAndThrowsItIntoAFoe(GameTestHelper h) {
        var p = player(h);
        // Everything stays inside the test area's loaded ground (it is twelve blocks across).
        var item = new ItemEntity(h.getLevel(), p.getX(), p.getEyeY(), p.getZ() + 3, new ItemStack(Items.IRON_AXE));
        item.setNoGravity(true); item.setDeltaMovement(Vec3.ZERO);
        h.getLevel().addFreshEntity(item);
        var foe = target(h, p.position().add(3, 0, 7));
        aim(p, item.position());
        h.assertTrue(TelekinesisService.findGripTarget(p) == item && TelekinesisService.grip(p, 5), "A loose item can be gripped");
        aim(p, foe.getBoundingBox().getCenter());
        h.assertTrue(TelekinesisService.throwGripped(p), "And thrown");
        h.runAfterDelay(12, () -> {
            try {
                h.assertTrue(foe.getHealth() < 100, "A thrown axe hits for its weight: item " + item.position() + " moving "
                        + item.getDeltaMovement() + " alive " + item.isAlive() + ", foe " + foe.position());
                h.succeed();
            } finally { cleanup(p, foe, item); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "kinetic.far_disarm")
    public static void disarmPlucksTheWeaponAndASecondPressFlingsItFree(GameTestHelper h) {
        var p = player(h);
        var foe = target(h, p.position().add(0, 0, 4));
        foe.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        aim(p, foe.getBoundingBox().getCenter());
        try {
            h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.QUATERNARY, false) == AbilityUseResult.SUCCESS, "Disarm casts");
            h.assertTrue(foe.getMainHandItem().isEmpty() && TelekinesisService.hasPlucked(p), "The weapon is plucked away");
            int energy = PlayerEnergyService.getEnergy(p);
            h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.QUATERNARY, false) == AbilityUseResult.SUCCESS,
                    "A second press flings it");
            h.assertTrue(PlayerEnergyService.getEnergy(p) == energy && !TelekinesisService.hasPlucked(p), "For free, once");
            h.assertTrue(TelekinesisService.weaponDamage(new ItemStack(Items.IRON_SWORD)) >= 6, "A sword hits for its own damage");
            h.succeed();
        } finally {
            h.getLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(16)).forEach(Entity::discard);
            cleanup(p, foe);
        }
    }

    @GameTest(template = "rewind_empty", batch = "kinetic.far_levitate")
    public static void levitateHoversOnADrainAndLandsGently(GameTestHelper h) {
        var p = player(h); learn(p, "bastion");
        try {
            cast(h, p, LEVITATE, false);
            h.assertTrue(levitating(p) && p.getAbilities().flying && StaminaDrain.active(p, LEVITATE),
                    "The caster hovers, paid for every second");
            TelekinesisSkillService.tick(p.getServer());
            TelekinesisSkillService.tick(p.getServer());
            h.assertTrue(TelekinesisService.hasBulletStop(p), "Still Air holds shots round a hovering telekinetic");
            h.assertTrue(TelekinesisSkillService.cancel(p, LEVITATE, true), "A second press comes down");
            h.assertTrue(!levitating(p) && !p.getAbilities().flying && !StaminaDrain.active(p, LEVITATE) && !TelekinesisService.hasBulletStop(p),
                    "Flight, the drain and the catch all end together");
            h.assertTrue(p.hasEffect(net.minecraft.world.effect.MobEffects.SLOW_FALLING), "And the landing is gentle");
            h.succeed();
        } finally { cleanup(p); }
    }

    @GameTest(template = "rewind_empty", batch = "kinetic.far_second_step")
    public static void secondStepVaultsAgainInTheAirForFree(GameTestHelper h) {
        var p = player(h); learn(p, "2_0", "2_2");
        try {
            h.assertTrue(SkillTreeService.equip(p, AbilitySlot.PRIMARY, STEP), "Vector Step equips");
            h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.PRIMARY, false) == AbilityUseResult.SUCCESS, "The first throw");
            p.setOnGround(false);
            p.setDeltaMovement(Vec3.ZERO);
            int energy = PlayerEnergyService.getEnergy(p);
            h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.PRIMARY, false) == AbilityUseResult.SUCCESS, "A second throw in the air");
            h.assertTrue(p.getDeltaMovement().y > .5 && PlayerEnergyService.getEnergy(p) == energy, "Free, and real");
            h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.PRIMARY, false) == AbilityUseResult.ON_COOLDOWN,
                    "Only one second throw");
            h.assertTrue(TelekinesisSkillService.cushion(p) && !TelekinesisSkillService.cushion(p), "Grounding still saves exactly one landing");
            h.succeed();
        } finally { cleanup(p); }
    }

    @GameTest(template = "rewind_empty", batch = "kinetic.far_catch")
    public static void softLandingCatchesAFriendsLongFallOnceInAWhile(GameTestHelper h) {
        var p = player(h); learn(p, "0_0");
        var friend = target(h, p.position().add(0, 0, 3));
        var scoreboard = p.getScoreboard();
        PlayerTeam team = scoreboard.addPlayerTeam("fh" + p.getUUID().toString().substring(0, 8));
        scoreboard.addPlayerToTeam(p.getScoreboardName(), team);
        scoreboard.addPlayerToTeam(friend.getScoreboardName(), team);
        var foe = target(h, p.position().add(2, 0, 3));
        try {
            h.assertTrue(!TelekinesisSkillService.catchFall(friend, 3, List.of(p)), "A short fall needs no catching");
            h.assertTrue(!TelekinesisSkillService.catchFall(foe, 8, List.of(p)), "A foe is never caught");
            h.assertTrue(TelekinesisSkillService.catchFall(friend, 8, List.of(p)), "A friend's long fall is caught");
            h.assertTrue(!TelekinesisSkillService.catchFall(friend, 8, List.of(p)), "Once every twenty seconds");
            h.succeed();
        } finally { scoreboard.removePlayerTeam(team); cleanup(p, friend, foe); }
    }

    @GameTest(template = "rewind_empty", batch = "kinetic.far_round_up")
    public static void roundUpPullsToItsSpotAndARecastTossesForFree(GameTestHelper h) {
        var p = player(h); learn(p);
        Mob anchor = target(h, p.position().add(0, 0, 8)), victim = target(h, p.position().add(2, 0, 8));
        aim(p, anchor.getBoundingBox().getCenter());
        try {
            h.assertTrue(SkillTreeService.equip(p, AbilitySlot.PRIMARY, ROUND_UP), "Round Up equips");
            h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.PRIMARY, false) == AbilityUseResult.SUCCESS, "Round Up casts");
            for (int i = 0; i < 4; i++) TelekinesisSkillService.tick(p.getServer());
            h.assertTrue(victim.getDeltaMovement().x < 0, "The hands pull toward the aimed spot, not the caster");
            int energy = PlayerEnergyService.getEnergy(p);
            h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.PRIMARY, false) == AbilityUseResult.SUCCESS,
                    "A recast tosses them before the cooldown");
            h.assertTrue(victim.getDeltaMovement().y > .8 && PlayerEnergyService.getEnergy(p) == energy, "Up, and for free");
        } finally { cleanup(p, anchor, victim); }
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "kinetic.far_retrieve")
    public static void retrieveBringsOneRealStackAcrossTheWorld(GameTestHelper h) {
        var p = player(h); learn(p, "retrieve");
        BlockPos pos = p.blockPosition().offset(0, 1, 6);
        h.getLevel().setBlock(pos, Blocks.CHEST.defaultBlockState(), 3);
        var chest = (ChestBlockEntity) h.getLevel().getBlockEntity(pos);
        chest.setItem(0, new ItemStack(Items.DIAMOND, 3)); chest.setItem(1, new ItemStack(Items.GOLD_INGOT, 4));
        aim(p, Vec3.atCenterOf(pos));
        try {
            cast(h, p, BECKON, false);
            var stacks = h.getLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(12), item -> item.getItem().is(Items.DIAMOND));
            h.assertTrue(chest.getItem(0).isEmpty() && chest.getItem(1).getCount() == 4 && stacks.size() == 1
                            && stacks.getFirst().getItem().getCount() == 3 && !p.getInventory().contains(new ItemStack(Items.DIAMOND)),
                    "Retrieve takes one stack and leaves it travelling in the world");
            stacks.forEach(Entity::discard);
        } finally { h.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3); cleanup(p); }
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "kinetic.far_reject")
    public static void beckonAndPinWithNothingToTakeCostNothing(GameTestHelper h) {
        var p = player(h); learn(p);
        try {
            h.assertTrue(SkillTreeService.equip(p, AbilitySlot.PRIMARY, BECKON) && SkillTreeService.equip(p, AbilitySlot.SECONDARY, PIN), "Both equip");
            int energy = PlayerEnergyService.getEnergy(p);
            h.assertTrue(AbilityUseService.activateAssignedAction(p, AbilitySlot.PRIMARY, false) == AbilityUseResult.CANNOT_USE
                    && AbilityUseService.activateAssignedAction(p, AbilitySlot.SECONDARY, false) == AbilityUseResult.CANNOT_USE
                    && PlayerEnergyService.getEnergy(p) == energy, "No target, no charge");
        } finally { cleanup(p); }
        h.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "kinetic.far_cleanup")
    public static void losingThePowerLetsEverythingGo(GameTestHelper h) {
        var p = player(h); learn(p, "skyfall");
        var foe = physical(h, p.position().add(0, 0, 5));
        var held = target(h, p.position().add(1.5, 0, 3));
        try {
            aim(p, foe.getBoundingBox().getCenter());
            cast(h, p, SKYFALL, false);
            cast(h, p, LEVITATE, false);
            h.assertTrue(TelekinesisService.isHeld(foe) && levitating(p), "A haul and a hover are running");
            TelekinesisService.stopAll(p);
            h.assertTrue(!TelekinesisService.isHeld(foe) && !levitating(p) && !p.getAbilities().flying, "Everything is let go at once");
            h.assertTrue(KineticCommands.describe(p).contains("techniques: idle"), "The admin status reads idle: " + KineticCommands.describe(p));
            h.succeed();
        } finally { cleanup(p, foe, held); }
    }
}
