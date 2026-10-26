package com.jayptucker.heroesevolved.cryo;

import com.jayptucker.heroesevolved.ability.Targeting;
import com.jayptucker.heroesevolved.ability.AbilitySlot;
import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.AbilityActivationResult;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.types.TimeManipulationAbility;
import com.jayptucker.heroesevolved.config.HeroesEvolvedConfig;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.electro.ElectroService;
import com.jayptucker.heroesevolved.quake.QuakeService;
import com.jayptucker.heroesevolved.radiation.RadiationService;
import com.jayptucker.heroesevolved.telekinesis.TelekinesisService;
import com.jayptucker.heroesevolved.telepathy.TelepathyService;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class CryoGameTests {
    private static ServerPlayer player(GameTestHelper helper, int height) {
        ServerPlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "cryo-test"));
        player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, height, 2))));
        helper.getLevel().addNewPlayer(player);
        return player;
    }

    private static <T extends LivingEntity> T target(GameTestHelper helper, EntityType<T> type, Vec3 pos) {
        T mob = type.create(helper.getLevel());
        mob.setPos(pos);
        mob.setNoGravity(true);
        helper.getLevel().addFreshEntity(mob);
        return mob;
    }

    private static void aim(ServerPlayer player, LivingEntity target) {
        Vec3 delta = target.getBoundingBox().getCenter().subtract(player.getEyePosition());
        player.setYRot((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
        player.setXRot((float) -Math.toDegrees(Math.atan2(delta.y, delta.horizontalDistance())));
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(PlayerList list, String name) {
        try {
            Field field = PlayerList.class.getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(list);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private static void register(ServerPlayer player) {
        try {
            Connection connection = player.connection.getConnection();
            if (connection.channel() == null) {
                Field channel = Connection.class.getDeclaredField("channel");
                channel.setAccessible(true);
                channel.set(connection, new EmbeddedChannel());
            }
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        PlayerList list = player.getServer().getPlayerList();
        CryoGameTests.<List<ServerPlayer>>field(list, "players").add(player);
        CryoGameTests.<Map<UUID, ServerPlayer>>field(list, "playersByUUID").put(player.getUUID(), player);
    }

    private static void remove(ServerPlayer player, Entity... targets) {
        com.jayptucker.heroesevolved.PowerOwnershipTestSupport.release(player);
        CryoService.stopAll(player);
        TelekinesisService.stopAll(player);
        PlayerList list = player.getServer().getPlayerList();
        CryoGameTests.<List<ServerPlayer>>field(list, "players").remove(player);
        CryoGameTests.<Map<UUID, ServerPlayer>>field(list, "playersByUUID").remove(player.getUUID());
        for (Entity target : targets) target.discard();
        player.serverLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 100, batch = "CryoGameTests.flashFreezeHoldsThroughContactDamageThenShatters")
    public static void flashFreezeHoldsThroughContactDamageThenShatters(GameTestHelper helper) {
        ServerPlayer player = player(helper, 15);
        Cow cow = target(helper, EntityType.COW, player.position().add(0, 0, 9));
        aim(player, cow);
        helper.assertTrue(CryoService.flashFreeze(player, 1), "Flash Freeze must reach nine blocks (old reach was six)");
        helper.assertTrue(CryoService.isFrozen(cow), "Its own initial damage must not shatter the freeze");
        Vec3 position = cow.position();
        helper.runAfterDelay(25, () -> {
            try {
                helper.assertTrue(CryoService.isFrozen(cow), "Freeze must survive its contact damage and subsequent ticks");
                helper.assertTrue(cow.position().distanceToSqr(position) < 0.01, "Mob must remain inside its ice");
                helper.assertTrue(!cow.isNoAi(), "Freeze must not persist a NoAI flag");
                float health = cow.getHealth();
                cow.hurt(player.damageSources().playerAttack(player), 2);
                helper.assertTrue(!CryoService.isFrozen(cow), "A physical hit must shatter the shell");
                helper.assertTrue(health - cow.getHealth() >= 6.9F, "Shattering must apply the bonus damage");
                helper.succeed();
            } finally { remove(player, cow); }
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 100, batch = "CryoGameTests.frozenCreeperPausesFuseAndResumesAfterThaw")
    public static void frozenCreeperPausesFuseAndResumesAfterThaw(GameTestHelper helper) {
        ServerPlayer player = player(helper, 2);
        Creeper creeper = target(helper, EntityType.CREEPER, player.position().add(3, 0, 0));
        creeper.ignite();
        CryoService.freezeSolid(creeper, 45, player);
        helper.runAfterDelay(35, () -> {
            helper.assertTrue(creeper.isAlive() && CryoService.isFrozen(creeper), "An ignited creeper must not explode while frozen");
            helper.assertTrue(creeper.getSwelling(1) == 0, "The actual creeper fuse must pause");
            helper.assertTrue(!creeper.isNoAi(), "AI flag must remain intact");
        });
        helper.runAfterDelay(50, () -> {
            try {
                helper.assertTrue(!CryoService.isFrozen(creeper), "Timed freeze must thaw");
                helper.assertTrue(creeper.getSwelling(1) > 0, "Mob simulation must resume after thaw");
                helper.succeed();
            } finally { remove(player, creeper); }
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 100, batch = "CryoGameTests.accumulatingColdFreezesAndCleanupRestoresSpeed")
    public static void accumulatingColdFreezesAndCleanupRestoresSpeed(GameTestHelper helper) {
        ServerPlayer player = player(helper, 25);
        Cow cow = target(helper, EntityType.COW, player.position().add(3, 0, 0));
        double speed = cow.getAttributeValue(Attributes.MOVEMENT_SPEED);
        CryoService.chill(cow, 80, player);
        helper.assertTrue(cow.getAttributeValue(Attributes.MOVEMENT_SPEED) < speed, "Chill must actually slow the mob");
        CryoService.chill(cow, 80, player);
        helper.assertTrue(CryoService.isFrozen(cow), "Sustained cold must build into a solid freeze");
        CryoService.stopAll(player);
        helper.assertTrue(!CryoService.isFrozen(cow) && cow.getTicksFrozen() == 0, "Power removal must thaw its targets");
        helper.assertTrue(Math.abs(cow.getAttributeValue(Attributes.MOVEMENT_SPEED) - speed) < 0.0001,
                "Cleanup must remove only the cryo movement penalty");
        remove(player, cow);
        helper.succeed();
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 100, batch = "CryoGameTests.frostBoltReachesThirtyBlocksButStopsAtWalls")
    public static void frostBoltReachesThirtyBlocksButStopsAtWalls(GameTestHelper helper) {
        ServerPlayer player = player(helper, 35);
        Cow cow = target(helper, EntityType.COW, player.position().add(0, 0, 30));
        cow.setNoAi(true);
        try {
            aim(player, cow);
            CryoService.frostBolt(player, 1);
            helper.assertTrue(cow.getHealth() < cow.getMaxHealth() && cow.getTicksFrozen() >= 95,
                    "Frost Bolt must damage and chill beyond its old twenty-block limit");
            BlockPos wall = player.blockPosition().offset(0, 0, 10);
            for (int y = 0; y < 3; y++) helper.getLevel().setBlock(wall.above(y), Blocks.STONE.defaultBlockState(), 3);
            helper.assertTrue(Targeting.livingEntityInSight(player, 20, entity -> entity == cow) == null,
                    "Longer reach must still stop at solid walls");
            helper.assertTrue(CryoService.onFrozenHit(cow, 2, true) == 2, "Cold damage must not gain shatter damage");
            for (int y = 0; y < 3; y++) helper.getLevel().removeBlock(wall.above(y), false);
            helper.succeed();
        } finally { remove(player, cow); }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 120, batch = "CryoGameTests.blizzardFreezesWithoutItsPulsesShatteringTargets")
    public static void blizzardFreezesWithoutItsPulsesShatteringTargets(GameTestHelper helper) {
        ServerPlayer player = player(helper, 45);
        register(player);
        Cow inside = target(helper, EntityType.COW, player.position().add(3, 0, 0));
        Cow outside = target(helper, EntityType.COW, player.position().add(21, 0, 0));
        inside.setNoAi(true);
        outside.setNoAi(true);
        CryoService.blizzard(player, 1);
        helper.runAfterDelay(65, () -> {
            try {
                helper.assertTrue(CryoService.isFrozen(inside), "Storm must hold a fully chilled mob through later damage pulses");
                helper.assertTrue(inside.getHealth() >= inside.getMaxHealth() - 4,
                        "Sixty-five storm ticks must deal at most four one-damage pulses, without shatter bonuses");
                helper.assertTrue(!CryoService.isFrozen(outside) && outside.getTicksFrozen() == 0,
                        "Storm must stop beyond its expanded twenty-block radius");
                CryoService.stopAll(player);
                helper.assertTrue(!CryoService.hasBlizzard(player) && !CryoService.isFrozen(inside),
                        "Stopping a storm must clear its fog state and owned freezes");
                helper.succeed();
            } finally { remove(player, inside, outside); }
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 100, batch = "CryoGameTests.frozenVillagersKeepTelepathyStoryProgress")
    public static void frozenVillagersKeepTelepathyStoryProgress(GameTestHelper helper) {
        ServerPlayer player = player(helper, 55);
        Villager first = target(helper, EntityType.VILLAGER, player.position().add(0, 0, 30));
        Villager second = target(helper, EntityType.VILLAGER, player.position().add(0, 0, 35));
        try {
            CryoService.freezeSolid(first, 80, player);
            aim(player, first);
            helper.assertTrue(TelepathyService.readMind(player, 1), "Telepathy must reach thirty blocks and read a frozen villager");
            helper.assertTrue(player.getData(ModDataAttachments.PLAYER_STORY_PROGRESS) == 1, "Reading must reveal the first story memory");
            TelepathyService.readMind(player, 1);
            helper.assertTrue(player.getData(ModDataAttachments.PLAYER_STORY_PROGRESS) == 1, "One mind must not duplicate story progress");
            first.discard();
            aim(player, second);
            TelepathyService.readMind(player, 1);
            helper.assertTrue(player.getData(ModDataAttachments.PLAYER_STORY_PROGRESS) == 2, "A new mind must advance the existing story");
            helper.succeed();
        } finally { remove(player, first, second); }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 100, batch = "CryoGameTests.rangedBlastsLightningAndPushReachBeyondOldLimits")
    public static void rangedBlastsLightningAndPushReachBeyondOldLimits(GameTestHelper helper) {
        ServerPlayer player = player(helper, 65);
        Cow cow = target(helper, EntityType.COW, player.position().add(0, 0, 40));
        cow.setNoAi(true);
        // The forty-block target can leave the template's normal chunk tickets.
        // Make entity accessibility deterministic regardless of chunk alignment.
        var forced = new java.util.HashSet<net.minecraft.world.level.ChunkPos>();
        for (int z = 0; z <= 48; z += 8) {
            var chunk = new net.minecraft.world.level.ChunkPos(player.blockPosition().offset(0, 0, z));
            if (!helper.getLevel().getForcedChunks().contains(chunk.toLong())) {
                helper.getLevel().setChunkForced(chunk.x, chunk.z, true); forced.add(chunk);
            }
            helper.getLevel().getChunk(chunk.x, chunk.z);
        }
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(helper.getLevel().getEntities(player,
                    new net.minecraft.world.phys.AABB(player.position(), cow.position()).inflate(2), Entity::isAlive).contains(cow),
                    "Wait for the distant target's chunk to become accessible before checking power range");
        }).thenExecute(() -> {
            try {
            cow.setPos(player.position().add(0, 0, 12));
            aim(player, cow);
            QuakeService.shockwave(player, 1);
            helper.assertTrue(cow.getHealth() < cow.getMaxHealth(), "Level one Quake must hit twelve blocks away (old range eight)");
            cow.setHealth(cow.getMaxHealth());
            cow.invulnerableTime = 0;
            cow.setPos(player.position().add(0, 0, 24));
            aim(player, cow);
            ElectroService.chainLightning(player, 1);
            helper.assertTrue(cow.getHealth() < cow.getMaxHealth(), "Lightning must hit twenty-four blocks away (old range sixteen)");
            cow.setPos(player.position().add(0, 0, 12));
            cow.setDeltaMovement(Vec3.ZERO);
            aim(player, cow);
            TelekinesisService.push(player, 1);
            helper.assertTrue(cow.getDeltaMovement().length() > 0.5, "Telekinetic Push must reach twelve blocks (old range eight)");
            cow.setHealth(cow.getMaxHealth());
            cow.invulnerableTime = 0;
            cow.setPos(player.position().add(0, 0, 40));
            cow.setDeltaMovement(Vec3.ZERO);
            aim(player, cow);
            helper.assertTrue(helper.getLevel().getEntities(player,
                    new net.minecraft.world.phys.AABB(player.position(), cow.position()).inflate(2), Entity::isAlive).contains(cow),
                    "The distant target's chunk must be accessible before testing beam range");
            RadiationService.addMass(player, 20);
            helper.assertTrue(RadiationService.meltdownBeam(player, 1), "Mass must vent into a beam");
            helper.assertTrue(cow.getHealth() < cow.getMaxHealth(), "Radiation beam must hit forty blocks away (old range twenty-four)"
                    + "; alive=" + cow.isAlive() + "; removed=" + cow.isRemoved()
                    + "; loaded=" + helper.getLevel().hasChunkAt(cow.blockPosition())
                    + "; visible=" + player.hasLineOfSight(cow)
                    + "; query=" + helper.getLevel().getEntities(player,
                            new net.minecraft.world.phys.AABB(player.getEyePosition(), player.getEyePosition().add(player.getLookAngle().scale(48))).inflate(1.2), Entity::isAlive).contains(cow));
        } finally {
            remove(player, cow);
            for (var chunk : forced) helper.getLevel().setChunkForced(chunk.x, chunk.z, false);
        } }).thenSucceed();
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 100, batch = "CryoGameTests.distantTelekineticGripSurvivesItsMaintenanceCheck")
    public static void distantTelekineticGripSurvivesItsMaintenanceCheck(GameTestHelper helper) {
        ServerPlayer player = player(helper, 75);
        register(player);
        Cow cow = target(helper, EntityType.COW, player.position().add(0, 0, 28));
        cow.setNoAi(true);
        aim(player, cow);
        helper.assertTrue(TelekinesisService.grip(player, 1), "Grip must acquire a target twenty-eight blocks away");
        helper.runAfterDelay(3, () -> {
            try {
                helper.assertTrue(TelekinesisService.isGripping(player), "Newly reachable targets must survive the former twenty-four-block maintenance cap");
                helper.succeed();
            } finally { remove(player, cow); }
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 120, batch = "CryoGameTests.lightningOrbTravelsFarEnoughToHitDistantTargets")
    public static void lightningOrbTravelsFarEnoughToHitDistantTargets(GameTestHelper helper) {
        ServerPlayer player = player(helper, 85);
        register(player);
        Cow cow = target(helper, EntityType.COW, player.position().add(0, 0, 40));
        cow.setNoAi(true);
        player.setYRot(0);
        player.setXRot(0);
        ElectroService.ballLightning(player, 1);
        helper.runAfterDelay(85, () -> {
            try {
                helper.assertTrue(cow.getHealth() < cow.getMaxHealth(), "Lightning orb must survive its former lifetime and reach a forty-block target");
                helper.succeed();
            } finally { remove(player, cow); }
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 100, batch = "CryoGameTests.blinkExtendsReachWithoutIncreasingCostOrCooldown")
    public static void blinkExtendsReachWithoutIncreasingCostOrCooldown(GameTestHelper helper) {
        ServerPlayer player = player(helper, 95);
        int baseRange = HeroesEvolvedConfig.COMMON.timeBlinkLevelOneDistance.get();
        BlockPos destination = player.blockPosition().offset(0, 1, baseRange + baseRange / 2);
        helper.getLevel().setBlock(destination, Blocks.STONE.defaultBlockState(), 3);
        try {
            player.setYRot(0);
            player.setXRot(0);
            var blink = new TimeManipulationAbility().actions().get(AbilitySlot.PRIMARY);
            helper.assertTrue(blink.cooldownTicks(1) == HeroesEvolvedConfig.COMMON.timeBlinkLevelOneCooldownTicks.get(),
                    "Doubling range must preserve Blink's configured cooldown");
            helper.assertTrue(blink.energyCost(1) == HeroesEvolvedConfig.COMMON.timeBlinkEnergyCost.get(),
                    "Doubling range must preserve Blink's energy cost");
            helper.assertTrue(blink.activate(new AbilityUseContext(player, ModAbilities.TIME_MANIPULATION_ID, 1, false))
                            == AbilityActivationResult.SUCCESS,
                    "Blink must reach a landing block beyond its former configured range");
            helper.succeed();
        } finally {
            helper.getLevel().removeBlock(destination, false);
            remove(player);
        }
    }
    @GameTest(template = "rewind_empty", batch = "cryo.large_dome")
    public static void blizzardImmediatelyFreezesPassiveAndHostileMobsInLargeDome(GameTestHelper helper) {
        ServerPlayer player = player(helper, 95);
        Cow passive = target(helper, EntityType.COW, player.position().add(18, 0, 0));
        Creeper hostile = target(helper, EntityType.CREEPER, player.position().add(0, 0, 18));
        Villager edge = target(helper, EntityType.VILLAGER, player.position().add(20, 0, 0));
        Cow outside = target(helper, EntityType.COW, player.position().add(21, 0, 0));
        Cow above = target(helper, EntityType.COW, player.position().add(0, 21, 0));
        try {
            CryoService.blizzard(player, 1);
            helper.assertTrue(CryoService.isFrozen(passive) && CryoService.isFrozen(hostile) && CryoService.isFrozen(edge),
                    "Activation must freeze passive, hostile and boundary mobs immediately beyond the old radius");
            helper.assertTrue(!CryoService.isFrozen(outside) && !CryoService.isFrozen(above) && !CryoService.isFrozen(player),
                    "The caster and targets beyond the dome must remain unfrozen");
            helper.assertTrue(!passive.isNoAi(), "Freezing cannot persist a NoAI flag");
            helper.succeed();
        } finally { remove(player, passive, hostile, edge, outside, above); }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 70, batch = "cryo.reversible_surface")
    public static void surfaceFrostRestoresWaterAndPropertiesWithoutLosingContainersOrEdits(GameTestHelper helper) {
        ServerPlayer player = player(helper, 105);
        BlockPos base = player.blockPosition().offset(3, -1, 0);
        BlockPos water = base.offset(1, 0, 0), edited = base.offset(2, 0, 0), chest = base.offset(3, 0, 0);
        var original = Blocks.OAK_LOG.defaultBlockState().setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, net.minecraft.core.Direction.Axis.X);
        helper.getLevel().setBlock(base, original, 3);
        helper.getLevel().setBlock(water, Blocks.WATER.defaultBlockState(), 3);
        helper.getLevel().setBlock(edited, Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
        var storage = (net.minecraft.world.level.block.entity.ChestBlockEntity) helper.getLevel().getBlockEntity(chest);
        storage.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 3));
        helper.assertTrue(TemporaryBlocks.frostSurface(helper.getLevel(), base, 10, player)
                && TemporaryBlocks.frostSurface(helper.getLevel(), water, 10, player)
                && TemporaryBlocks.frostSurface(helper.getLevel(), edited, 10, player), "Terrain and exposed water must freeze reversibly");
        helper.assertTrue(!TemporaryBlocks.frostSurface(helper.getLevel(), chest, 10, player), "Containers must keep their block entity and contents");
        helper.getLevel().setBlock(edited, Blocks.GOLD_BLOCK.defaultBlockState(), 3);
        helper.runAfterDelay(30, () -> {
            try {
                helper.assertTrue(helper.getLevel().getBlockState(base).equals(original), "Thaw must restore exact block properties");
                helper.assertTrue(helper.getLevel().getBlockState(water).is(Blocks.WATER), "Temporary ice must restore water");
                helper.assertTrue(helper.getLevel().getBlockState(edited).is(Blocks.GOLD_BLOCK), "Thaw must preserve later player edits");
                helper.assertTrue(storage.getItem(0).getCount() == 3, "Freezing cannot lose container contents");
                helper.succeed();
            } finally { remove(player); }
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 400, batch = "cryo.awakening_dome")
    public static void firstAwakeningCreatesLargeDomeAndEverythingThaws(GameTestHelper helper) {
        ServerPlayer player = player(helper, 115);
        player.setData(ModDataAttachments.PLAYER_ABILITIES.get(), com.jayptucker.heroesevolved.ability.data.PlayerAbilityData.empty()
                .assignDormant(ModAbilities.CRYOKINESIS_ID).unlock(ModAbilities.CRYOKINESIS_ID));
        BlockPos surface = player.blockPosition().offset(22, -1, 0);
        var chunk = new net.minecraft.world.level.ChunkPos(surface);
        boolean forced = !helper.getLevel().getForcedChunks().contains(chunk.toLong());
        if (forced) helper.getLevel().setChunkForced(chunk.x, chunk.z, true);
        helper.getLevel().setBlock(surface, Blocks.STONE.defaultBlockState(), 3);
        Cow passive = target(helper, EntityType.COW, player.position().add(23, 1, 0));
        Cow outside = target(helper, EntityType.COW, player.position().add(25, 1, 0));
        outside.setNoAi(true);
        com.jayptucker.heroesevolved.intro.PowerIntroService.acquired(player, ModAbilities.CRYOKINESIS_ID);
        helper.runAfterDelay(85, () -> helper.assertTrue(CryoService.isFrozen(passive) && !CryoService.isFrozen(outside),
                "First acquisition must freeze passive mobs inside the 24-block dome and respect its edge"));
        helper.runAfterDelay(140, () -> helper.assertTrue(helper.getLevel().getBlockState(surface).is(Blocks.PACKED_ICE),
                "The awakening dome must frost terrain beyond twenty blocks"));
        helper.runAfterDelay(350, () -> {
            try {
                helper.assertTrue(!CryoService.isFrozen(passive) && !passive.isNoAi(), "The awakening freeze must thaw and leave mob AI intact");
                helper.assertTrue(helper.getLevel().getBlockState(surface).is(Blocks.STONE), "Awakening terrain must restore itself");
                helper.succeed();
            } finally {
                com.jayptucker.heroesevolved.intro.PowerIntroService.forget(player);
                remove(player, passive, outside);
                if (forced) helper.getLevel().setChunkForced(chunk.x, chunk.z, false);
            }
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 70, batch = "cryo.thaw_budget")
    public static void largeFrostPatchThawsInBatchesWithoutLeavingIce(GameTestHelper helper) {
        ServerPlayer player = player(helper, 125);
        var positions = new java.util.ArrayList<BlockPos>();
        for (int x = 0; x < 11; x++) for (int z = 0; z < 11; z++) {
            BlockPos pos = player.blockPosition().offset(x, -1, z);
            helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
            helper.assertTrue(TemporaryBlocks.frostSurface(helper.getLevel(), pos, 1, player), "Test surface must frost");
            positions.add(pos);
        }
        helper.runAfterDelay(1, () -> helper.assertTrue(positions.stream().anyMatch(pos -> helper.getLevel().getBlockState(pos).is(Blocks.PACKED_ICE)),
                "A 121-block patch must not thaw all at once under the shared 96-write cap"));
        helper.runAfterDelay(15, () -> {
            try {
                helper.assertTrue(positions.stream().allMatch(pos -> helper.getLevel().getBlockState(pos).is(Blocks.STONE)),
                        "Batched thaw must eventually restore the whole patch");
                helper.succeed();
            } finally { remove(player); }
        });
    }

}

