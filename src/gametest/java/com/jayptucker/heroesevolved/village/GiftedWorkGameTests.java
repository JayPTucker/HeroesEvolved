package com.jayptucker.heroesevolved.village;

import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.company.CompanyAgentEntity;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.gifted.GiftedMobs;
import com.jayptucker.heroesevolved.gifted.Renegades;
import com.jayptucker.heroesevolved.registry.ModEntities;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Gifted villagers at work: each power's job really changes something (a lit forge, wet fields, ice in the barrel, a
 * mended golem, a harvest, a warning shout), the courier's flight and its return from over the horizon, favours, and no
 * work near a Suppressor. Each test stands a bell and a guardian on the arena's floor, in its own batch.
 */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class GiftedWorkGameTests {
    private static BlockPos at(GameTestHelper h, int x, int z) { return h.absolutePos(new BlockPos(x, 2, z)); }

    private static BlockPos bell(GameTestHelper h) {
        BlockPos bell = at(h, 6, 6);
        h.getLevel().setBlockAndUpdate(bell, Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, BellAttachType.FLOOR));
        return bell;
    }

    /** A villager at {@code x, z} whose gift of {@code power} woke good: their village's guardian. */
    private static Villager guardian(GameTestHelper h, int x, int z, ResourceLocation power) {
        Villager v = EntityType.VILLAGER.create(h.getLevel());
        v.moveTo(Vec3.atBottomCenterOf(at(h, x, z)));
        v.getPersistentData().putBoolean(GiftedMobs.ROLLED, true);
        h.getLevel().addFreshEntity(v);
        Renegades.guard(v, power);
        return v;
    }

    private static void cleanup(GameTestHelper h, BlockPos bell, Entity... entities) {
        for (Entity e : entities) {
            if (e instanceof Villager v) GiftedWork.forget(v);
            if (e instanceof ServerPlayer p) p.serverLevel().removePlayerImmediately(p, Entity.RemovalReason.DISCARDED);
            else if (e != null) e.discard();
        }
        if (bell != null) VillageMemory.forget(h.getLevel(), bell);
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "work.jobs")
    public static void everyGiftHasAJob(GameTestHelper h) {
        h.assertTrue(GiftedWork.job(ModAbilities.PYROKINESIS_ID) == GiftedWork.Job.SMITH, "Fire: the smith");
        h.assertTrue(GiftedWork.job(ModAbilities.HYDROKINESIS_ID) == GiftedWork.Job.WATERER, "Water: the waterer");
        h.assertTrue(GiftedWork.job(ModAbilities.CRYOKINESIS_ID) == GiftedWork.Job.ICE_HOUSE, "Ice: the ice house");
        h.assertTrue(GiftedWork.job(ModAbilities.FLIGHT_ID) == GiftedWork.Job.COURIER, "Flight: the courier");
        h.assertTrue(GiftedWork.job(ModAbilities.SUPERSPEED_ID) == GiftedWork.Job.POST, "Speed: the post");
        h.assertTrue(GiftedWork.job(ModAbilities.SONIC_RESONANCE_ID) == GiftedWork.Job.BELL, "Sound: the bell");
        h.assertTrue(GiftedWork.job(ModAbilities.VIBE_ID) == GiftedWork.Job.LOOKOUT, "Anyone else: the lookout");
        for (ResourceLocation power : GiftedMobs.powers()) h.assertTrue(GiftedWork.job(power) != null, "A job for " + power);
        h.succeed();
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 500, batch = "work.smith")
    public static void theSmithLightsTheForge(GameTestHelper h) {
        BlockPos bell = bell(h), forge = at(h, 2, 2);
        h.getLevel().setBlockAndUpdate(forge, Blocks.BLAST_FURNACE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
        Villager v = guardian(h, 9, 9, ModAbilities.PYROKINESIS_ID);
        h.assertTrue(GiftedWork.now(v), "There is a forge to light");
        h.succeedWhen(() -> {
            h.assertTrue(h.getLevel().getBlockState(forge).getValue(BlockStateProperties.LIT), "Not lit yet: " + GiftedWork.of(v).stage());
            cleanup(h, bell, v);
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 500, batch = "work.waterer")
    public static void theWatererSoaksTheFields(GameTestHelper h) {
        BlockPos bell = bell(h), field = h.absolutePos(new BlockPos(3, 1, 3));
        h.getLevel().setBlockAndUpdate(field, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 0));
        h.getLevel().setBlockAndUpdate(field.above(), Blocks.WHEAT.defaultBlockState());
        Villager v = guardian(h, 9, 9, ModAbilities.HYDROKINESIS_ID);
        h.assertTrue(GiftedWork.now(v), "There is dry farmland");
        h.succeedWhen(() -> {
            var s = h.getLevel().getBlockState(field);
            h.assertTrue(s.is(Blocks.FARMLAND) && s.getValue(FarmBlock.MOISTURE) == FarmBlock.MAX_MOISTURE, "Not soaked yet");
            cleanup(h, bell, v);
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 500, batch = "work.ice")
    public static void theIceHouseFillsTheBarrel(GameTestHelper h) {
        BlockPos bell = bell(h), barrel = at(h, 2, 2);
        h.getLevel().setBlockAndUpdate(barrel, Blocks.BARREL.defaultBlockState());
        Villager v = guardian(h, 9, 9, ModAbilities.CRYOKINESIS_ID);
        h.assertTrue(GiftedWork.now(v), "There is an ice house");
        h.succeedWhen(() -> {
            h.assertTrue(GiftedWork.ice(h.getLevel(), barrel) > 0, "No ice yet");
            cleanup(h, bell, v);
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 500, batch = "work.keeper")
    public static void theGolemKeeperChargesTheGolem(GameTestHelper h) {
        BlockPos bell = bell(h);
        IronGolem golem = EntityType.IRON_GOLEM.create(h.getLevel());
        golem.moveTo(Vec3.atBottomCenterOf(at(h, 2, 2)));
        golem.setNoAi(true);
        h.getLevel().addFreshEntity(golem);
        golem.setHealth(40);
        Villager v = guardian(h, 9, 9, ModAbilities.ELECTROKINESIS_ID);
        h.assertTrue(GiftedWork.now(v), "There is a golem");
        h.succeedWhen(() -> {
            h.assertTrue(golem.getHealth() >= 50, "Not charged yet: " + golem.getHealth());
            cleanup(h, bell, v, golem);
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 500, batch = "work.healer")
    public static void theHealerMendsTheHurt(GameTestHelper h) {
        BlockPos bell = bell(h);
        Villager hurt = EntityType.VILLAGER.create(h.getLevel());
        hurt.moveTo(Vec3.atBottomCenterOf(at(h, 2, 2)));
        hurt.getPersistentData().putBoolean(GiftedMobs.ROLLED, true);
        hurt.setNoAi(true);
        h.getLevel().addFreshEntity(hurt);
        hurt.setHealth(6);
        Villager v = guardian(h, 9, 9, ModAbilities.REGENERATION_ID);
        h.assertTrue(GiftedWork.now(v), "Someone is hurt");
        h.succeedWhen(() -> {
            h.assertTrue(hurt.getHealth() >= 12, "Not mended yet: " + hurt.getHealth());
            cleanup(h, bell, v, hurt);
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 500, batch = "work.harvester")
    public static void theHarvesterLiftsTheCropAndReplantsIt(GameTestHelper h) {
        BlockPos bell = bell(h), field = h.absolutePos(new BlockPos(3, 1, 3));
        h.getLevel().setBlockAndUpdate(field, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
        h.getLevel().setBlockAndUpdate(field.above(), ((CropBlock) Blocks.WHEAT).getStateForAge(7));
        Villager v = guardian(h, 9, 9, ModAbilities.TELEKINESIS_ID);
        h.assertTrue(GiftedWork.now(v), "There is a ripe crop");
        h.succeedWhen(() -> {
            var s = h.getLevel().getBlockState(field.above());
            h.assertTrue(s.is(Blocks.WHEAT) && ((CropBlock) Blocks.WHEAT).getAge(s) == 0, "Not harvested yet");
            cleanup(h, bell, v);
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 200, batch = "work.lookout")
    public static void theLookoutShoutsWhenMonstersComeNear(GameTestHelper h) {
        BlockPos bell = bell(h);
        Villager v = guardian(h, 9, 9, ModAbilities.VIBE_ID);
        Zombie zombie = EntityType.ZOMBIE.create(h.getLevel());
        zombie.moveTo(Vec3.atBottomCenterOf(at(h, 1, 1)));
        zombie.setNoAi(true);
        zombie.setInvulnerable(true);
        h.getLevel().addFreshEntity(zombie);
        AtomicBoolean shouted = new AtomicBoolean();
        h.onEachTick(() -> { for (String s : Shouts.shouting(v)) if (s.startsWith("Zombies at the")) shouted.set(true); });
        h.succeedWhen(() -> {
            h.assertTrue(shouted.get(), "No warning yet");
            cleanup(h, bell, v, zombie);
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 500, batch = "work.post")
    public static void thePostRunnerRunsTheRounds(GameTestHelper h) {
        BlockPos bell = bell(h), foot = at(h, 2, 2);
        h.getLevel().setBlockAndUpdate(foot, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.PART, BedPart.FOOT).setValue(BedBlock.FACING, Direction.EAST));
        h.getLevel().setBlockAndUpdate(foot.east(), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.PART, BedPart.HEAD).setValue(BedBlock.FACING, Direction.EAST));
        Villager v = guardian(h, 9, 9, ModAbilities.SUPERSPEED_ID);
        h.assertTrue(GiftedWork.now(v), "There is a house to deliver to");
        AtomicBoolean delivered = new AtomicBoolean();
        h.onEachTick(() -> {
            var w = GiftedWork.of(v);
            if (w != null && w.stage() == GiftedWork.Stage.ACTING && v.position().distanceTo(Vec3.atCenterOf(foot.east())) < 4) delivered.set(true);
        });
        h.succeedWhen(() -> {
            h.assertTrue(delivered.get(), "Not delivered yet");
            cleanup(h, bell, v);
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 600, batch = "work.courier")
    public static void theCourierFliesAndComesBackFromOverTheHorizon(GameTestHelper h) {
        BlockPos bell = bell(h);
        Villager v = guardian(h, 9, 9, ModAbilities.FLIGHT_ID);
        double ground = v.getY();
        h.assertTrue(GiftedWork.now(v), "Off with the post");
        h.assertTrue(v.isNoAi() && v.isNoGravity() && v.getTags().contains(GiftedWork.COURIER_TAG), "Flying: their own AI off, no gravity");
        h.runAfterDelay(20, () -> {
            h.assertTrue(v.getY() > ground + 2, "Up in the air: " + (v.getY() - ground));
            GiftedWork.away(v);
            h.assertTrue(v.isInvisible() && GiftedWork.of(v).leg() == 5, "Away over the horizon: out of the world");
            GiftedWork.hurry(v);
        });
        h.succeedWhen(() -> {
            h.assertTrue(GiftedWork.of(v).stage() == GiftedWork.Stage.RESTING, "Not back yet: leg " + GiftedWork.of(v).leg());
            h.assertTrue(!v.isNoAi() && !v.isNoGravity() && !v.isInvisible() && !v.getTags().contains(GiftedWork.COURIER_TAG),
                    "Back on their feet, themselves again");
            cleanup(h, bell, v);
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "work.courierload")
    public static void aCourierSavedInTheAirLandsWhenLoaded(GameTestHelper h) {
        Villager v = EntityType.VILLAGER.create(h.getLevel());
        v.moveTo(Vec3.atBottomCenterOf(at(h, 5, 5)).add(0, 3, 0));
        v.addTag(GiftedWork.COURIER_TAG);
        v.setNoAi(true);
        v.setNoGravity(true);
        v.setInvisible(true);
        h.getLevel().addFreshEntity(v);
        try {
            h.assertTrue(!v.isNoAi() && !v.isNoGravity() && !v.isInvisible() && !v.getTags().contains(GiftedWork.COURIER_TAG),
                    "Loaded again mid-flight: themselves again");
            h.succeed();
        } finally { v.discard(); }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "work.favour")
    public static void aFavourDoneIsPaidAndRemembered(GameTestHelper h) {
        BlockPos bell = bell(h);
        Villager v = guardian(h, 3, 3, ModAbilities.PYROKINESIS_ID);
        v.setNoAi(true);
        ServerPlayer p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "Wren"));
        p.setPos(Vec3.atBottomCenterOf(at(h, 4, 4)));
        p.setGameMode(GameType.SURVIVAL);
        p.setData(ModDataAttachments.PLAYER_ABILITIES, com.jayptucker.heroesevolved.ability.data.PlayerAbilityData.empty());
        h.getLevel().addNewPlayer(p);
        try {
            Favours.Favour f = Favours.ask(v, GiftedWork.Job.SMITH);
            h.assertTrue(Favours.open(v) != null && (f.item() == Items.COAL || f.item() == Items.IRON_INGOT), "They want coal or iron: " + f.describe());
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT, 64));
            h.assertTrue(!Favours.deliver(p, v), "Not with dirt");
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(f.item(), f.count() + 2));
            int before = VillageMemory.of(h.getLevel(), bell).standing(p.getUUID());
            h.assertTrue(Favours.deliver(p, v), "With what they asked for");
            h.assertTrue(p.getMainHandItem().getCount() == 2, "They take only what they asked for");
            h.assertTrue(p.getInventory().countItem(Items.EMERALD) == f.reward(), "And pay: " + p.getInventory().countItem(Items.EMERALD));
            h.assertTrue(Favours.open(v) == null, "Done");
            h.assertTrue(VillageMemory.of(h.getLevel(), bell).standing(p.getUUID()) == before + 1, "Their village thinks better of them");
            h.succeed();
        } finally { cleanup(h, bell, v, p); }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 300, batch = "work.suppressed")
    public static void noWorkNearASuppressor(GameTestHelper h) {
        BlockPos bell = bell(h), forge = at(h, 2, 2);
        h.getLevel().setBlockAndUpdate(forge, Blocks.FURNACE.defaultBlockState());
        Villager v = guardian(h, 9, 9, ModAbilities.PYROKINESIS_ID);
        CompanyAgentEntity quiet = CompanyAgentEntity.create(ModEntities.COMPANY_AGENT.get(), h.getLevel(), CompanyAgentEntity.Role.SUPPRESSOR,
                Vec3.atBottomCenterOf(at(h, 5, 5)), 0, true);
        quiet.setNoAi(true);   // his field doesn't need his AI; standing still, it covers the forge and the guardian both
        h.getLevel().addFreshEntity(quiet);
        GiftedWork.now(v);
        h.runAfterDelay(240, () -> {
            try {
                h.assertTrue(!h.getLevel().getBlockState(forge).getValue(BlockStateProperties.LIT), "Their gift is quiet: no fire");
                h.succeed();
            } finally { cleanup(h, bell, v, quiet); }
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "work.home")
    public static void aGuardianFarFromHomeGoesBack(GameTestHelper h) {
        BlockPos bell = bell(h);
        Villager v = guardian(h, 9, 9, ModAbilities.PYROKINESIS_ID);
        try {
            GiftedWork.pause(v, 1000);
            // Nobody near, home loaded: simply back.
            v.teleportTo(v.getX() + 100, v.getY(), v.getZ());
            GiftedWork.goHome(h.getLevel(), v, GiftedWork.of(v), bell);
            h.assertTrue(v.position().distanceTo(Vec3.atBottomCenterOf(bell)) < 8, "Home while nobody watched: " + v.position());
            h.succeed();
        } finally { cleanup(h, bell, v); }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "work.homeseen")
    public static void aGuardianSomeoneCanSeeWalksHome(GameTestHelper h) {
        BlockPos bell = bell(h);
        Villager v = guardian(h, 9, 9, ModAbilities.PYROKINESIS_ID);
        ServerPlayer p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "Wren"));
        p.setPos(v.position().add(2, 0, 0));
        h.getLevel().addNewPlayer(p);
        try {
            GiftedWork.pause(v, 1000);
            Vec3 was = v.position();
            GiftedWork.goHome(h.getLevel(), v, GiftedWork.of(v), bell.offset(120, 0, 0));
            h.assertTrue(v.position().equals(was), "Not whisked away in front of someone");
            h.assertTrue(v.getBrain().hasMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET), "They set off walking");
            h.succeed();
        } finally { cleanup(h, bell, v, p); }
    }
}
