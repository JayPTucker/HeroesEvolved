package com.jayptucker.heroesevolved.intro;

import com.jayptucker.heroesevolved.ability.AbilitySlot;
import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.AbilityUseResult;
import com.jayptucker.heroesevolved.ability.registry.AbilityRegistry;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.AbilityUseService;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.flight.FlightService;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;
import java.util.function.Consumer;

@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class PowerIntroGameTests {
    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "power-intro"));
        player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(6, 2, 6))));
        helper.getLevel().addNewPlayer(player);
        return player;
    }
    private static void remove(ServerPlayer player) {
        com.jayptucker.heroesevolved.PowerOwnershipTestSupport.release(player);
        PowerIntroService.forget(player);
        FlightService.stopForPowerRemoval(player);
        player.serverLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 180, batch = "PowerIntroGameTests.quakeChargesThenFracturesTerrain")
    public static void quakeChargesThenFracturesTerrain(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        BlockPos center = player.blockPosition(), near = center.offset(6, 0, 0), outside = center.offset(13, 0, 0);
        BlockPos bedrock = center.offset(4, 0, 0), protectedBlock = center.offset(5, 1, 0), footing = center.below();
        for (BlockPos pos : new BlockPos[] {near, outside, protectedBlock, footing})
            helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(bedrock, Blocks.BEDROCK.defaultBlockState(), 3);
        Consumer<BlockEvent.BreakEvent> guard = event -> {
            if (event.getPos().equals(protectedBlock)) event.setCanceled(true);
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, BlockEvent.BreakEvent.class, guard);
        PlayerAbilityService.grantAbility(player, ModAbilities.QUAKE_ID);
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(helper.getLevel().getBlockState(near).is(Blocks.STONE), "Charge must finish before terrain breaks");
            helper.assertTrue(PowerIntroService.isActive(player), "First grant must start intro");
            helper.assertTrue(AbilityUseService.activateAssignedAction(player, AbilitySlot.PRIMARY, false)
                    == AbilityUseResult.CANNOT_USE, "Intro must not spend normal ability energy");
            float health = player.getHealth();
            player.hurt(player.damageSources().generic(), 15);
            helper.assertTrue(player.getHealth() == health, "Awakening owner must be protected");
        });
        helper.runAfterDelay(120, () -> {
            try {
                helper.assertTrue(helper.getLevel().getBlockState(near).isAir(), "Release must actually destroy stone within radius");
                helper.assertTrue(helper.getLevel().getBlockState(outside).is(Blocks.STONE), "Terrain beyond twelve blocks must remain");
                helper.assertTrue(helper.getLevel().getBlockState(bedrock).is(Blocks.BEDROCK), "Unbreakable blocks must remain");
                helper.assertTrue(helper.getLevel().getBlockState(protectedBlock).is(Blocks.STONE), "Protection events must be respected");
                helper.assertTrue(helper.getLevel().getBlockState(footing).is(Blocks.STONE), "Owner must retain footing");
                helper.assertTrue(!PowerIntroService.isActive(player), "Intro must end after release");
                PowerIntroHistory history = player.getData(ModDataAttachments.POWER_INTRO_HISTORY);
                var encoded = PowerIntroHistory.CODEC.encodeStart(JsonOps.INSTANCE, history).getOrThrow();
                helper.assertTrue(PowerIntroHistory.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow().equals(history),
                        "Seen powers must survive serialization");
                PlayerAbilityService.removeAssignedAbility(player);
                PlayerAbilityService.grantAbility(player, ModAbilities.QUAKE_ID);
                helper.assertTrue(!PowerIntroService.isActive(player), "Removing/regranting must not replay a terrain blast");
                helper.succeed();
            } finally { NeoForge.EVENT_BUS.unregister(guard); remove(player); }
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 160, batch = "PowerIntroGameTests.regenerationRepairsOwnerAndNearbyLife")
    public static void regenerationRepairsOwnerAndNearbyLife(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        player.setHealth(2);
        player.addEffect(new MobEffectInstance(MobEffects.POISON, 300));
        Cow cow = EntityType.COW.create(helper.getLevel());
        cow.setNoAi(true);
        cow.setPos(player.position().add(3, 0, 0));
        cow.setHealth(2);
        helper.getLevel().addFreshEntity(cow);
        PlayerAbilityService.assignDormantAbility(player, ModAbilities.REGENERATION_ID);
        helper.assertTrue(!PowerIntroService.isActive(player), "Dormant assignment must not play an intro");
        PlayerAbilityService.unlockAbility(player, ModAbilities.REGENERATION_ID);
        AbilityRegistry.ABILITIES.get(ModAbilities.REGENERATION_ID).onAwaken(
                new AbilityUseContext(player, ModAbilities.REGENERATION_ID, 1, false));
        helper.runAfterDelay(105, () -> {
            try {
                helper.assertTrue(player.getHealth() == player.getMaxHealth(), "Regeneration release must restore full health");
                helper.assertTrue(!player.hasEffect(MobEffects.POISON), "Regeneration release must purge poison");
                helper.assertTrue(cow.getHealth() >= 8, "Healing surge must reach nearby life");
                helper.assertTrue(!PowerIntroService.isActive(player), "Duplicate awakening callback must not restart sequence");
                helper.succeed();
            } finally { cow.discard(); remove(player); }
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 150, batch = "PowerIntroGameTests.flightReleasesIntoActualFlight")
    public static void flightReleasesIntoActualFlight(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        PlayerAbilityService.grantAbility(player, ModAbilities.FLIGHT_ID);
        helper.runAfterDelay(15, () -> helper.assertTrue(!FlightService.hasActiveSession(player), "Wind must charge before launch"));
        helper.runAfterDelay(40, () -> helper.assertTrue(FlightService.hasActiveSession(player), "Flight intro must trigger real launch"));
        helper.runAfterDelay(105, () -> {
            try {
                helper.assertTrue(!PowerIntroService.isActive(player), "Flight intro must release normal controls");
                helper.assertTrue(player.getData(ModDataAttachments.POWER_INTRO_HISTORY).seen().contains(ModAbilities.FLIGHT_ID),
                        "Flight must be remembered independently");
                helper.succeed();
            } finally { remove(player); }
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 150, batch = "PowerIntroGameTests.electricalIntroDoesNotBurnTerrain")
    public static void electricalIntroDoesNotBurnTerrain(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        BlockPos wood = player.blockPosition().offset(3, 0, 0);
        helper.getLevel().setBlock(wood, Blocks.OAK_PLANKS.defaultBlockState(), 3);
        PlayerAbilityService.grantAbility(player, ModAbilities.ELECTROKINESIS_ID);
        helper.runAfterDelay(105, () -> {
            try {
                helper.assertTrue(helper.getLevel().getBlockState(wood).is(Blocks.OAK_PLANKS), "Cinematic lightning must not ignite wood");
                helper.assertTrue(!PowerIntroService.isActive(player), "Lightning awakening must finish");
                helper.assertTrue(player.getData(ModDataAttachments.POWER_INTRO_HISTORY).seen().contains(ModAbilities.ELECTROKINESIS_ID),
                        "Electrical intro must be remembered");
                helper.succeed();
            } finally { remove(player); }
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 150, batch = "PowerIntroGameTests.telekinesisLiftsAndReleasesLooseObjects")
    public static void telekinesisLiftsAndReleasesLooseObjects(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        ItemEntity item = new ItemEntity(helper.getLevel(), player.getX() + 3, player.getY(), player.getZ(), new ItemStack(Items.IRON_INGOT));
        item.setPickUpDelay(200);
        helper.getLevel().addFreshEntity(item);
        PlayerAbilityService.grantAbility(player, ModAbilities.TELEKINESIS_ID);
        helper.runAfterDelay(52, () -> helper.assertTrue(item.getY() > player.getY() + 0.3,
                "Telekinetic intro must lift real loose items"));
        helper.runAfterDelay(105, () -> {
            try {
                helper.assertTrue(!PowerIntroService.isActive(player), "Psychic intro must finish");
                helper.assertTrue(!item.isNoGravity(), "Intro must not leave objects with changed gravity flags");
                helper.assertTrue(item.getItem().getCount() == 1, "Intro must not duplicate or consume items");
                helper.succeed();
            } finally { item.discard(); remove(player); }
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 90, batch = "PowerIntroGameTests.removalInterruptsChargeWithoutDamagingTerrain")
    public static void removalInterruptsChargeWithoutDamagingTerrain(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        BlockPos stone = player.blockPosition().offset(5, 0, 0);
        helper.getLevel().setBlock(stone, Blocks.STONE.defaultBlockState(), 3);
        PlayerAbilityService.grantAbility(player, ModAbilities.QUAKE_ID);
        helper.runAfterDelay(20, () -> {
            PlayerAbilityService.removeAssignedAbility(player);
            helper.assertTrue(!PowerIntroService.isActive(player), "Power removal must cancel charging immediately");
        });
        helper.runAfterDelay(75, () -> {
            try {
                helper.assertTrue(helper.getLevel().getBlockState(stone).is(Blocks.STONE), "Cancelled charge must not leave a delayed blast");
                helper.succeed();
            } finally { remove(player); }
        });
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 150, batch = "PowerIntroGameTests.nonBuildingQuakeStillFinishes")
    public static void nonBuildingQuakeStillFinishes(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        player.getAbilities().mayBuild = false;
        BlockPos stone = player.blockPosition().offset(5, 0, 0);
        helper.getLevel().setBlock(stone, Blocks.STONE.defaultBlockState(), 3);
        PlayerAbilityService.grantAbility(player, ModAbilities.QUAKE_ID);
        helper.runAfterDelay(120, () -> {
            try {
                helper.assertTrue(helper.getLevel().getBlockState(stone).is(Blocks.STONE), "Intro must respect inability to build");
                helper.assertTrue(!PowerIntroService.isActive(player), "Suppressed terrain damage must not leave intro stuck active");
                helper.succeed();
            } finally { player.getAbilities().mayBuild = true; remove(player); }
        });
    }
}
