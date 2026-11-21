package com.jayptucker.heroesevolved.village.overload;

import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.config.HeroesEvolvedConfig;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.gifted.GiftedMobs;
import com.jayptucker.heroesevolved.gifted.Renegades;
import com.jayptucker.heroesevolved.mimicry.GiftedVillagers;
import com.jayptucker.heroesevolved.radio.EmergencyBand;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Overloads: a villager at the centre of their own storm. Most are staged high in the air over the test's own chunk, so a
 * storm can't reach the test structures below; the supercell stands on the test's floor, since it follows the ground.
 * Each test has its own batch: a storm reaches further than a test's plot.
 */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class OverloadGameTests {
    /** High over the test plots, but under the test world's build limit (blocks are placed up here). */
    private static final int SKY = 300;

    private static Vec3 sky(GameTestHelper helper, double dx, double dz) {
        return Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(5, 0, 5))).add(dx, 0, dz).with(net.minecraft.core.Direction.Axis.Y, SKY);
    }

    private static Villager villager(GameTestHelper helper, Vec3 at) {
        Villager v = EntityType.VILLAGER.create(helper.getLevel());
        if (v == null) throw new AssertionError("No villager");
        v.setPos(at);
        v.setNoGravity(true);
        v.getPersistentData().putBoolean(GiftedMobs.ROLLED, true);
        helper.getLevel().addFreshEntity(v);
        GiftedVillagers.give(v, null);
        return v;
    }

    /** A pig: floating ones have no AI and stay put; one on the ground keeps its AI, or nothing could move it. */
    private static Pig pig(GameTestHelper helper, Vec3 at, boolean floating) {
        Pig p = EntityType.PIG.create(helper.getLevel());
        if (p == null) throw new AssertionError("No pig");
        p.setPos(at);
        p.setNoGravity(floating);
        p.setNoAi(floating);
        helper.getLevel().addFreshEntity(p);
        return p;
    }

    private static ServerPlayer player(GameTestHelper helper, Vec3 at, ResourceLocation power) {
        ServerPlayer p = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "overload-helper")) {
            @Override public boolean isInvulnerableTo(DamageSource damage) { return false; }
        };
        try {
            Field grace = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            grace.setAccessible(true); grace.setInt(p, 0);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        p.setPos(at);
        p.setNoGravity(true);
        p.setGameMode(GameType.SURVIVAL);
        var data = com.jayptucker.heroesevolved.ability.data.PlayerAbilityData.empty();
        if (power != null) data = data.assignDormant(power).unlock(power);
        p.setData(ModDataAttachments.PLAYER_ABILITIES, data);
        helper.getLevel().addNewPlayer(p);
        return p;
    }

    /** Ends what a test started, whatever happened. */
    private static void cleanup(GameTestHelper helper, List<BlockPos> blocks, Entity... entities) {
        for (Entity e : entities) {
            if (e == null) continue;
            Overloads.cancel(e);
            if (e instanceof ServerPlayer p) p.serverLevel().removePlayerImmediately(p, Entity.RemovalReason.DISCARDED);
            else e.discard();
        }
        for (BlockPos b : blocks) helper.getLevel().setBlockAndUpdate(b, Blocks.AIR.defaultBlockState());
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 60, batch = "overload.start")
    public static void aGiftOverloadsIntoItsStorm(GameTestHelper helper) {
        Villager eye = villager(helper, sky(helper, 0, 0)), neighbour = villager(helper, sky(helper, 3, 0));
        try {
            Overload o = Overloads.start(eye, Storm.WILDFIRE, 400);
            helper.assertTrue(o != null && Overloads.critical(eye), "A grown villager can overload");
            helper.assertTrue(ModAbilities.PYROKINESIS_ID.equals(GiftedVillagers.gift(eye)), "Their gift becomes the storm's power");
            helper.assertTrue(o.inVillage(neighbour) && !o.inVillage(eye), "Their village is everyone round them but them");
            helper.assertTrue(EmergencyBand.live(o.call()) && o.call().trouble() == EmergencyBand.Trouble.WILDFIRE,
                    "Their village calls for help, with the wildfire's flare");
            helper.assertTrue(eye.getAttribute(Attributes.MOVEMENT_SPEED).getValue() <= 1.0E-6, "They can't walk out of it");
            helper.assertTrue(Overloads.start(neighbour, Storm.BLIZZARD, 400) != null && Overloads.all().size() >= 2,
                    "Two can overload at once");
            Overloads.cancel(neighbour);
            helper.assertTrue(Storm.of(ModAbilities.FLIGHT_ID) == Storm.SUPERCELL && Storm.of(ModAbilities.QUAKE_ID) == null,
                    "Each storm belongs to one power; others have none");
        } catch (RuntimeException e) { cleanup(helper, List.of(), eye, neighbour); throw e; }
        helper.runAfterDelay(12, () -> {
            try {
                var tag = eye.getPersistentData().getCompound(Overloads.TAG);
                helper.assertTrue(tag.getInt("storm") == Storm.WILDFIRE.ordinal() && tag.getInt("fuse") < 400 && tag.getInt("fuse") > 380,
                        "Its countdown is kept in their own data: " + tag);
                helper.succeed();
            } finally { cleanup(helper, List.of(), eye, neighbour); }
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 260, batch = "overload.calm")
    public static void stayingWithThemCalmsThemAndTheyKeepTheGift(GameTestHelper helper) {
        Villager eye = villager(helper, sky(helper, 0, 0));
        // A pyrokinetic: the fire can't hurt them, and they calm a wildfire twice as fast.
        ServerPlayer friend = player(helper, sky(helper, 1.5, 0), ModAbilities.PYROKINESIS_ID);
        Overload o;
        try {
            o = Overloads.start(eye, Storm.WILDFIRE, 4000);
            helper.assertTrue(o != null, "It starts");
        } catch (RuntimeException e) { cleanup(helper, List.of(), eye, friend); throw e; }
        helper.runAfterDelay(60, () -> {
            try {
                helper.assertTrue(o.calm() > 0.45F && o.calm() < 0.8F, "Someone with the same power calms them twice as fast: " + o.calm());
                helper.assertTrue(!friend.isOnFire() && friend.getHealth() >= friend.getMaxHealth() - 0.01F, "Their own power's fire doesn't touch them");
            } catch (RuntimeException e) { cleanup(helper, List.of(), eye, friend); throw e; }
        });
        helper.runAfterDelay(110, () -> {
            try {
                helper.assertTrue(o.stage() == Overload.Stage.DYING, "Calm, the storm dies away: " + o.stage() + " " + o.calm());
            } catch (RuntimeException e) { cleanup(helper, List.of(), eye, friend); throw e; }
        });
        helper.runAfterDelay(110 + OverloadGeometry.DIE_AWAY + 10, () -> {
            try {
                helper.assertTrue(!Overloads.critical(eye), "Then it is over");
                helper.assertTrue(GiftedMobs.isGood(eye) && ModAbilities.PYROKINESIS_ID.equals(GiftedMobs.powerOf(eye)),
                        "They keep the gift, under control, and guard their village with it");
                helper.assertTrue(friend.hasEffect(MobEffects.HERO_OF_THE_VILLAGE), "Whoever was there is a hero of the village");
                helper.succeed();
            } finally { GiftedMobs.cleanse(eye); cleanup(helper, List.of(), eye, friend); }
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 80, batch = "overload.slip")
    public static void aloneTheCalmSlipsBackAndATelepathReachesFromFarOff(GameTestHelper helper) {
        Villager eye = villager(helper, sky(helper, 0, 0));
        ServerPlayer stranger = player(helper, sky(helper, 9, 0), null);
        Overload o;
        try {
            o = Overloads.start(eye, Storm.BLIZZARD, 4000);
            Overloads.calm(o, 0.5F);
        } catch (RuntimeException e) { cleanup(helper, List.of(), eye, stranger); throw e; }
        helper.runAfterDelay(20, () -> {
            try {
                helper.assertTrue(o.calm() < 0.5F && o.calm() > 0.45F, "With nobody beside them the calm slips back, slowly: " + o.calm());
                helper.assertTrue(o.helpers().isEmpty(), "Nine blocks off, someone without a telepath's reach doesn't count");
                stranger.setData(ModDataAttachments.PLAYER_ABILITIES, com.jayptucker.heroesevolved.ability.data.PlayerAbilityData.empty()
                        .assignDormant(ModAbilities.TELEPATHY_ID).unlock(ModAbilities.TELEPATHY_ID));
            } catch (RuntimeException e) { cleanup(helper, List.of(), eye, stranger); throw e; }
        });
        helper.runAfterDelay(40, () -> {
            try {
                helper.assertTrue(o.helpers().contains(stranger) && o.calm() > 0.6F,
                        "A telepath calms them from nine blocks off, twice as fast: " + o.calm());
                helper.succeed();
            } finally { cleanup(helper, List.of(), eye, stranger); }
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 140, batch = "overload.dampener")
    public static void aDampenerEndsItAndTheGift(GameTestHelper helper) {
        Villager eye = villager(helper, sky(helper, 0, 0));
        try {
            Overloads.start(eye, Storm.BLIZZARD, 4000);
            eye.addEffect(new MobEffectInstance(com.jayptucker.heroesevolved.registry.ModItems.DAMPENED, 200, 0));
        } catch (RuntimeException e) { cleanup(helper, List.of(), eye); throw e; }
        helper.runAfterDelay(OverloadGeometry.DIE_AWAY + 15, () -> {
            try {
                helper.assertTrue(!Overloads.critical(eye), "Switched off, the storm dies away");
                helper.assertTrue(GiftedVillagers.gift(eye) == null && !GiftedMobs.isGifted(eye), "And the gift with it, for good");
                helper.succeed();
            } finally { cleanup(helper, List.of(), eye); }
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 80, batch = "overload.hold")
    public static void coldHoldsAWildfireAndBlowsMakeItWorse(GameTestHelper helper) {
        Villager fire = villager(helper, sky(helper, 0, 0)), snow = villager(helper, sky(helper, 0, 30));
        ServerPlayer brute = player(helper, sky(helper, 1.5, 0), ModAbilities.PYROKINESIS_ID);
        Overload burning, freezing;
        try {
            burning = Overloads.start(fire, Storm.WILDFIRE, 2000);
            freezing = Overloads.start(snow, Storm.BLIZZARD, 2000);
        } catch (RuntimeException e) { cleanup(helper, List.of(), fire, snow, brute); throw e; }
        // Ice Blast on each, every tick for twenty ticks (the brute standing by calms the fire meanwhile, which doesn't
        // touch the countdown).
        for (int t = 1; t <= 20; t++)
            helper.runAfterDelay(t, () -> { Overloads.cooled(fire); Overloads.cooled(snow); });
        helper.runAfterDelay(22, () -> {
            try {
                helper.assertTrue(burning.fuse() >= 1998, "Cold holds a wildfire's countdown: " + burning.fuse());
                helper.assertTrue(freezing.fuse() <= 1980, "But not a blizzard's: " + freezing.fuse());
                float health = fire.getHealth();
                int before = burning.fuse();
                fire.hurt(brute.damageSources().playerAttack(brute), 6);
                helper.assertTrue(fire.getHealth() == health, "They can't be hurt");
                helper.assertTrue(burning.fuse() <= before - OverloadGeometry.HIT_PENALTY + 1, "A blow takes ten seconds off: " + burning.fuse());
                helper.succeed();
            } finally { cleanup(helper, List.of(), fire, snow, brute); }
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 640, batch = "overload.break")
    public static void leftAloneItBreaksBurnsOutAndTakesTheGift(GameTestHelper helper) {
        Villager eye = villager(helper, sky(helper, 0, 0));
        // Past where the front reaches (28) but inside the Firestorm (36), in a chunk held loaded for the test.
        BlockPos far = BlockPos.containing(sky(helper, 32, 0));
        boolean forced = helper.getLevel().setChunkForced(far.getX() >> 4, far.getZ() >> 4, true);
        Pig near = pig(helper, sky(helper, 32, 0), true);
        Overload o;
        try {
            o = Overloads.start(eye, Storm.WILDFIRE, 20);
        } catch (RuntimeException e) { cleanup(helper, List.of(), eye, near); throw e; }
        Runnable unforce = () -> { if (forced) helper.getLevel().setChunkForced(far.getX() >> 4, far.getZ() >> 4, false); };
        helper.runAfterDelay(25, () -> {
            try {
                helper.assertTrue(o.stage() == Overload.Stage.BREAKING, "When the time runs out it starts to break: " + o.stage());
                helper.assertTrue(!near.isOnFire(), "Outside the front, nothing burns yet");
            } catch (RuntimeException e) { cleanup(helper, List.of(), eye, near); throw e; }
        });
        helper.runAfterDelay(25 + OverloadGeometry.BREAK_WARNING + 2, () -> {
            try {
                helper.assertTrue(o.stage() == Overload.Stage.BURNING, "Then it breaks: " + o.stage());
                helper.assertTrue(near.isOnFire(), "The Firestorm sets everything in its reach alight");
            } catch (RuntimeException e) { cleanup(helper, List.of(), eye, near); throw e; }
        });
        helper.runAfterDelay(25 + OverloadGeometry.BREAK_WARNING + OverloadGeometry.BURNOUT + 10, () -> {
            try {
                helper.assertTrue(!Overloads.critical(eye), "And burns itself out");
                helper.assertTrue(eye.isAlive() && eye.hasEffect(MobEffects.WEAKNESS), "They live through it, weak");
                helper.assertTrue(GiftedVillagers.gift(eye) == null && !GiftedMobs.isGifted(eye), "But the gift has burnt out");
                helper.succeed();
            } finally { cleanup(helper, List.of(), eye, near); unforce.run(); }
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 60, batch = "overload.wildfire")
    public static void theFrontSetsThingsAlightAndScorchesOnlyPlants(GameTestHelper helper) {
        Villager eye = villager(helper, sky(helper, 0, 0));
        Pig inside = pig(helper, sky(helper, 2, 0), true), outside = pig(helper, sky(helper, 0, 12), true);
        // A patch of grass with a plant on it, high up, for the scorching.
        BlockPos ground = BlockPos.containing(sky(helper, -3, -3)).below(4), plant = ground.above(), crop = ground.east().above();
        List<BlockPos> blocks = new ArrayList<>(List.of(ground, plant, ground.east(), crop));
        helper.getLevel().setBlockAndUpdate(ground, Blocks.GRASS_BLOCK.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(plant, Blocks.SHORT_GRASS.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(ground.east(), Blocks.FARMLAND.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(crop, Blocks.WHEAT.defaultBlockState());
        try {
            Overloads.start(eye, Storm.WILDFIRE, 4000);
            Wildfire.scorchColumn(helper.getLevel(), ground);
            Wildfire.scorchColumn(helper.getLevel(), ground.east());
            helper.assertTrue(helper.getLevel().getBlockState(ground).is(Blocks.DIRT), "Grass burns to dirt: "
                    + helper.getLevel().getBlockState(ground) + " under " + helper.getLevel().getBlockState(plant) + ", top "
                    + helper.getLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ground.getX(), ground.getZ())
                    + " at " + ground.getY());
            helper.assertTrue(helper.getLevel().getBlockState(plant).isAir(), "Plants burn away");
            helper.assertTrue(helper.getLevel().getBlockState(crop).is(Blocks.WHEAT), "Crops don't");
        } catch (RuntimeException e) { cleanup(helper, blocks, eye, inside, outside); throw e; }
        helper.runAfterDelay(25, () -> {
            try {
                helper.assertTrue(inside.isOnFire(), "Inside the front, anything catches fire");
                helper.assertTrue(!outside.isOnFire(), "Outside it, nothing does");
                helper.succeed();
            } finally { cleanup(helper, blocks, eye, inside, outside); }
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 120, batch = "overload.supercell")
    public static void theFunnelLiftsThemTakesItsWallAndSparesItsEye(GameTestHelper helper) {
        Vec3 floor = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(5, 2, 5)));
        Villager eye = villager(helper, floor);
        eye.setNoGravity(false);
        Pig wall = pig(helper, floor.add(5.5, 0, 0), false);
        ServerPlayer flyer = player(helper, floor.add(0, 0, 9), ModAbilities.FLIGHT_ID);
        Overload o;
        try {
            o = Overloads.start(eye, Storm.SUPERCELL, 4000);
        } catch (RuntimeException e) { cleanup(helper, List.of(), eye, wall, flyer); throw e; }
        boolean[] lifted = new boolean[1], caught = new boolean[1];
        double startY = wall.getY();
        for (int t = 2; t <= 60; t++)
            helper.runAfterDelay(t, () -> {
                if (wall.getY() > startY + 1.0) lifted[0] = true;
                if (Supercell.state(o).caught.contains(wall.getUUID())) caught[0] = true;
            });
        helper.runAfterDelay(62, () -> {
            try {
                helper.assertTrue(eye.isNoGravity() && eye.getY() > floor.y + 0.5, "They are lifted into its eye: " + (eye.getY() - floor.y));
                helper.assertTrue(caught[0] && lifted[0], "What reaches its wall is taken up it: caught " + caught[0] + ", lifted " + lifted[0]
                        + ", " + wall.position().subtract(eye.position()) + " from the eye");
                helper.assertTrue(!Supercell.state(o).caught.contains(flyer.getUUID()) && flyer.position().distanceTo(floor.add(0, 0, 9)) < 1.5,
                        "A flyer, of the same power, isn't touched");
                helper.assertTrue(o.storm() == Storm.SUPERCELL && Supercell.state(o).waypoint != null, "It is wandering somewhere");
            } catch (RuntimeException e) { cleanup(helper, List.of(), eye, wall, flyer); throw e; }
            Overloads.cancel(eye);
            helper.runAfterDelay(2, () -> {
                try {
                    helper.assertTrue(!eye.isNoGravity(), "Once it is over they come down");
                    helper.succeed();
                } finally { cleanup(helper, List.of(), eye, wall, flyer); }
            });
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 100, batch = "overload.blizzard")
    public static void theColdTakesThoseInTheOpenButNotTheSheltered(GameTestHelper helper) {
        Villager eye = villager(helper, sky(helper, 0, 0));
        // Far enough apart that the campfire warms only the one beside it.
        Pig open = pig(helper, sky(helper, 8, 0), true), roofed = pig(helper, sky(helper, -4, 0), true),
                warm = pig(helper, sky(helper, 0, 4), true);
        BlockPos roof = BlockPos.containing(sky(helper, -4, 0)).above(3), fire = BlockPos.containing(sky(helper, 0, 4)).east(2);
        List<BlockPos> blocks = List.of(roof, fire);
        helper.getLevel().setBlockAndUpdate(roof, Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(fire, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
        ServerPlayer cryo = player(helper, sky(helper, 0, -4), ModAbilities.CRYOKINESIS_ID);
        // No snow on the other tests' plots below.
        var griefing = helper.getLevel().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING);
        boolean wasGriefing = griefing.get();
        griefing.set(false, helper.getLevel().getServer());
        Runnable restore = () -> griefing.set(wasGriefing, helper.getLevel().getServer());
        try {
            Overloads.start(eye, Storm.BLIZZARD, 4000);
        } catch (RuntimeException e) { restore.run(); cleanup(helper, blocks, eye, open, roofed, warm, cryo); throw e; }
        helper.runAfterDelay(60, () -> {
            try {
                helper.assertTrue(open.getTicksFrozen() > 15, "Out in the open, the cold takes hold: " + open.getTicksFrozen());
                helper.assertTrue(roofed.getTicksFrozen() == 0, "Under a roof it can't: " + roofed.getTicksFrozen());
                helper.assertTrue(warm.getTicksFrozen() == 0, "Nor beside a fire: " + warm.getTicksFrozen());
                helper.assertTrue(cryo.getTicksFrozen() == 0, "A cryokinetic doesn't feel it");
                var o = Overloads.of(eye);
                helper.assertTrue(o != null && Overloads.weather(Storm.BLIZZARD).sees(o, cryo), "And sees them through the snow");
                helper.succeed();
            } finally { restore.run(); cleanup(helper, blocks, eye, open, roofed, warm, cryo); }
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 60, batch = "overload.wake")
    public static void aStormsGiftWakingGoodMayOverload(GameTestHelper helper) {
        Villager v = villager(helper, sky(helper, 0, 0)), quake = villager(helper, sky(helper, 6, 0));
        double chance = HeroesEvolvedConfig.COMMON.overloadChance.get();
        HeroesEvolvedConfig.COMMON.overloadChance.set(1.0D);
        try {
            GiftedVillagers.give(v, ModAbilities.FLIGHT_ID);
            GiftedVillagers.give(quake, ModAbilities.QUAKE_ID);
            helper.assertTrue(Renegades.awaken(v, false) == v, "The gift wakes");
            var o = Overloads.of(v);
            helper.assertTrue(o != null && o.storm() == Storm.SUPERCELL && !GiftedMobs.isGifted(v), "A flyer's overloads into a supercell");
            Renegades.awaken(quake, false);
            helper.assertTrue(!Overloads.critical(quake) && GiftedMobs.isGood(quake), "A power with no storm guards its village as before");
            helper.succeed();
        } finally {
            HeroesEvolvedConfig.COMMON.overloadChance.set(chance);
            GiftedMobs.cleanse(quake);
            cleanup(helper, List.of(), v, quake);
        }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 60, batch = "overload.resume")
    public static void itCarriesOnThroughAnUnloadedChunk(GameTestHelper helper) {
        Villager eye = villager(helper, sky(helper, 0, 0));
        try {
            Overload o = Overloads.start(eye, Storm.BLIZZARD, 1000);
            Overloads.calm(o, 0.4F);
        } catch (RuntimeException e) { cleanup(helper, List.of(), eye); throw e; }
        helper.runAfterDelay(12, () -> {
            try {
                // As if the chunk unloaded and loaded again: forgotten, then found in their data when they join.
                Overloads.clear();
                helper.assertTrue(!Overloads.critical(eye) && eye.getPersistentData().contains(Overloads.TAG), "Their data keeps it");
                Overloads.joined(new EntityJoinLevelEvent(eye, helper.getLevel()));
            } catch (RuntimeException e) { cleanup(helper, List.of(), eye); throw e; }
        });
        helper.runAfterDelay(15, () -> {
            try {
                var o = Overloads.of(eye);
                helper.assertTrue(o != null && o.storm() == Storm.BLIZZARD, "It picks up where it was");
                helper.assertTrue(o.fuse() < 1000 && o.fuse() > 970 && o.calm() > 0.3F, "Countdown and calm and all: " + o.fuse() + " " + o.calm());
                helper.succeed();
            } finally { cleanup(helper, List.of(), eye); }
        });
    }
}
