package com.jayptucker.heroesevolved.mimicry;

import com.jayptucker.heroesevolved.ability.*;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.AbilityRegistry;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.cryo.CryoService;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.data.PowerOwnershipSavedData;
import com.jayptucker.heroesevolved.electro.ElectroService;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.expansion.ExpansionSkills;
import com.jayptucker.heroesevolved.flight.DiveBombService;
import com.jayptucker.heroesevolved.flight.FlightDynamicsService;
import com.jayptucker.heroesevolved.flight.FlightService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.skills.SkillTreeData;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Compatibility is tested through real borrowed actions and catalog IDs, never by assigning a second power. */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class PowerCompatibilityGameTests {
    private static ServerPlayer player(GameTestHelper h, ResourceLocation power) {
        var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "compatibility"));
        try {
            Connection connection = p.connection.getConnection();
            Field channel = Connection.class.getDeclaredField("channel"); channel.setAccessible(true);
            if (connection.channel() == null) channel.set(connection, new EmbeddedChannel());
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 40, 3))));
        p.setYRot(0); p.setXRot(0); p.setOnGround(false);
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(power).unlock(power));
        PlayerProgressionService.setLevel(p, 5); PlayerEnergyService.restore(p, 999);
        return p;
    }
    private static void learn(ServerPlayer p, ResourceLocation power, String... ids) {
        p.setData(ModDataAttachments.SKILL_TREES, SkillTreeData.empty().with(power.toString(),
                new SkillTreeData.Build(5, Set.of(ids), Map.of(), 6)));
    }
    private static AbilityUseContext context(ServerPlayer p) { return new AbilityUseContext(p, ModAbilities.MIMICRY_ID, 5, false); }
    private static AbilityAction echo(AbilitySlot slot) { return AbilityRegistry.ABILITIES.get(ModAbilities.MIMICRY_ID).actions().get(slot); }
    private static void cast(GameTestHelper h, ServerPlayer p, AbilityAction action) {
        h.assertTrue(action.canUse(context(p)) && action.activate(context(p)) == AbilityActivationResult.SUCCESS, "A remembered Echo must actually activate");
    }
    private static LivingEntity target(GameTestHelper h, Vec3 at, boolean friend) {
        var t = (friend ? EntityType.VILLAGER : EntityType.PILLAGER).create(h.getLevel());
        t.setNoAi(true); t.setNoGravity(true); t.setPos(at);
        t.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100); t.setHealth(100);
        h.getLevel().addFreshEntity(t); return t;
    }
    private static void cleanup(ServerPlayer p, Entity... targets) {
        PowerRules.rewinding(p, false); MimicryService.forget(p); ExpansionSkills.forget(p);
        CryoService.releaseEntity(p); FlightService.stopForPowerRemoval(p); DiveBombService.forget(p);
        for (Entity t : targets) { CryoService.releaseEntity(t); t.discard(); }
    }
    @SuppressWarnings("unchecked") private static <T> T field(Object object, Class<?> type, String name) {
        try { Field f = type.getDeclaredField(name); f.setAccessible(true); return (T) f.get(object); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void register(ServerPlayer p) {
        PlayerList list = p.getServer().getPlayerList();
        PowerCompatibilityGameTests.<List<ServerPlayer>>field(list, PlayerList.class, "players").add(p);
        PowerCompatibilityGameTests.<Map<UUID, ServerPlayer>>field(list, PlayerList.class, "playersByUUID").put(p.getUUID(), p);
    }
    private static void unregister(ServerPlayer p) {
        PlayerList list = p.getServer().getPlayerList();
        PowerCompatibilityGameTests.<List<ServerPlayer>>field(list, PlayerList.class, "players").remove(p);
        PowerCompatibilityGameTests.<Map<UUID, ServerPlayer>>field(list, PlayerList.class, "playersByUUID").remove(p.getUUID());
    }

    @GameTest(template = "rewind_empty", batch = "compat.mimic_ids")
    public static void learnedMimicryCapacityCostAndCooldownUpgradesUseTheirCatalogIds(GameTestHelper h) {
        ServerPlayer p = player(h, ModAbilities.MIMICRY_ID);
        LivingEntity enemy = target(h, p.position().add(0, 0, 6), false);
        try {
            MimicryService.remember(p, ModAbilities.PYROKINESIS_ID, 5);
            AbilityAction action = echo(AbilitySlot.TERTIARY);
            int baselineCost = action.energyCost(context(p)), baselineCooldown = action.cooldownTicks(context(p));
            learn(p, ModAbilities.MIMICRY_ID, "empathic_mimicry_sylars_mirror", "empathic_mimicry_peters_burden", "empathic_mimicry_twin_echo");
            h.assertTrue(MimicryService.slots(p, 5) == 5, "The learned Sylar's Mirror grants its fifth real memory slot");
            h.assertTrue(action.energyCost(context(p)) == (int) Math.ceil(baselineCost * .75), "Peter's Burden discounts the actual borrowed move");
            MimicryService.technique(context(p), "overcharge"); cast(h, p, action);
            h.assertTrue(action.cooldownTicks(context(p)) == baselineCooldown / 2, "Twin Echo halves the empowered cast's recovery");
            action.release(context(p));
            h.assertTrue(action.cooldownTicks(context(p)) == baselineCooldown, "Empowerment is spent once rather than persisting into later Echoes");
            h.succeed();
        } finally { cleanup(p, enemy); }
    }

    @GameTest(template = "rewind_empty", batch = "compat.quick_study")
    public static void quickStudyReachesAPoweredPersonBehindCover(GameTestHelper h) {
        ServerPlayer p = player(h, ModAbilities.MIMICRY_ID), donor = player(h, ModAbilities.PYROKINESIS_ID);
        donor.setPos(p.position().add(0, 0, 12)); h.getLevel().addNewPlayer(donor);
        BlockPos wall = p.blockPosition().offset(0, 0, 6);
        try {
            for (int y = 0; y < 3; y++) h.getLevel().setBlock(wall.above(y), Blocks.STONE.defaultBlockState(), 3);
            h.assertTrue(!MimicryService.empathize(p, 5), "The ordinary eight-block sight-based absorb cannot reach this person");
            learn(p, ModAbilities.MIMICRY_ID, "empathic_mimicry_quick_study");
            h.assertTrue(MimicryService.empathize(p, 5) && ModAbilities.PYROKINESIS_ID.equals(MimicryService.memory(p).current()),
                    "The learned Quick Study node expands reach and senses through cover");
            h.succeed();
        } finally {
            for (int y = 0; y < 3; y++) h.getLevel().setBlock(wall.above(y), Blocks.AIR.defaultBlockState(), 3);
            h.getLevel().removePlayerImmediately(donor, Entity.RemovalReason.DISCARDED); cleanup(p);
        }
    }

    @GameTest(template = "rewind_empty", batch = "compat.empathy_healing", timeoutTicks = 50)
    public static void sharedHealingAndEmpathsCalmApplyTheLearnedUpgrades(GameTestHelper h) {
        ServerPlayer p = player(h, ModAbilities.MIMICRY_ID);
        LivingEntity friend = target(h, p.position().add(0, 0, 4), true); friend.setHealth(10);
        friend.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 0));
        learn(p, ModAbilities.MIMICRY_ID, "empathic_mimicry_shared_healing", "empathic_mimicry_empaths_calm");
        register(p);
        try {
            h.assertTrue(MimicryService.technique(context(p), "absorb_pain") && friend.getHealth() == 18,
                    "The learned Shared Healing node makes Absorb Pain heal eight health");
            h.assertTrue(p.hasEffect(MobEffects.POISON) && !friend.hasEffect(MobEffects.POISON), "Pain is actually transferred to the empath");
        } catch (RuntimeException | Error failure) { unregister(p); cleanup(p, friend); throw failure; }
        h.runAfterDelay(21, () -> {
            try {
                h.assertTrue(!p.hasEffect(MobEffects.POISON), "The learned Empath's Calm clears borrowed poison on its real passive tick");
                h.succeed();
            } finally { unregister(p); cleanup(p, friend); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "compat.borrowed_flight")
    public static void flightEchoesHaveTemporaryAuthorizationWithoutClaimingThePower(GameTestHelper h) {
        ServerPlayer p = player(h, ModAbilities.MIMICRY_ID);
        var owner = PowerOwnershipSavedData.get(p.getServer()).owner(ModAbilities.FLIGHT_ID);
        try {
            MimicryService.remember(p, ModAbilities.PYROKINESIS_ID, 5); MimicryService.remember(p, ModAbilities.FLIGHT_ID, 5);
            FlightDynamicsService.pressSkyburst(p);
            h.assertTrue(!FlightDynamicsService.isCharging(p), "Merely remembering Flight must not grant arbitrary native Flight access");
            AbilityAction toggle = echo(AbilitySlot.TERTIARY); cast(h, p, toggle); toggle.release(context(p));
            FlightService.tick(p);
            h.assertTrue(FlightService.hasActiveSession(p) && MimicryService.isBorrowingFlight(p), "A real Flight Echo survives native server ownership checks");
            FlightService.setForwardInput(p, true); FlightService.changeSpeed(p, 1); FlightService.tick(p);
            h.assertTrue(p.getData(ModDataAttachments.PLAYER_FLIGHT).speedStep() == 1, "An authorized borrowed session accepts real flight speed controls");
            h.assertTrue(p.getData(ModDataAttachments.PLAYER_ABILITIES).assignedPower().orElseThrow().getKey().equals(ModAbilities.MIMICRY_ID)
                            && PowerOwnershipSavedData.get(p.getServer()).owner(ModAbilities.FLIGHT_ID).equals(owner),
                    "Borrowing never assigns, claims or transfers the unique Flight role");
            MimicryService.recall(p, 1);
            h.assertTrue(!FlightService.hasActiveSession(p) && !MimicryService.isBorrowingFlight(p), "Changing memories retires borrowed Flight immediately");
            h.succeed();
        } finally { cleanup(p); }
    }

    @GameTest(template = "rewind_empty", batch = "compat.flight_lifecycle")
    public static void borrowedSkyburstAndMeteorCleanUpOnSuppressionAndLogout(GameTestHelper h) {
        ServerPlayer p = player(h, ModAbilities.MIMICRY_ID);
        try {
            MimicryService.remember(p, ModAbilities.FLIGHT_ID, 5);
            AbilityAction burst = echo(AbilitySlot.QUATERNARY); cast(h, p, burst);
            for (int i = 0; i < 10; i++) { FlightService.tick(p); FlightDynamicsService.tick(p); }
            h.assertTrue(FlightDynamicsService.isCharging(p), "The borrowed held Skyburst remains a real charge");
            burst.release(context(p));
            h.assertTrue(!FlightDynamicsService.isCharging(p) && p.getDeltaMovement().length() > 1, "Releasing the Echo fires its borrowed slingshot");
            AbilityAction dive = echo(AbilitySlot.QUINARY); cast(h, p, dive); dive.release(context(p));
            DiveBombService.tick(p);
            h.assertTrue(DiveBombService.isDiving(p), "Echo III can perform Meteor Drop under temporary borrowed authorization");
            PowerRules.rewinding(p, true); MimicryService.tickBorrowed(p);
            h.assertTrue(!FlightService.hasActiveSession(p) && !DiveBombService.isDiving(p) && !MimicryService.isBorrowingFlight(p),
                    "Losing control cancels both borrowed flight and its dive runtime");
            PowerRules.rewinding(p, false); cast(h, p, burst);
            MimicryService.logout(new PlayerEvent.PlayerLoggedOutEvent(p));
            h.assertTrue(!FlightDynamicsService.isCharging(p) && !FlightService.hasActiveSession(p), "Logout cleans up a pending borrowed maneuver too");
            h.succeed();
        } finally { cleanup(p); }
    }

    @GameTest(template = "rewind_empty", batch = "compat.echo_release", timeoutTicks = 50)
    public static void releaseAfterRecallStillEndsTheOriginalLightningChannel(GameTestHelper h) {
        ServerPlayer p = player(h, ModAbilities.MIMICRY_ID);
        LivingEntity enemy = target(h, p.position().add(0, 0, 5), false);
        MimicryService.remember(p, ModAbilities.ELECTROKINESIS_ID, 5);
        AbilityAction action = echo(AbilitySlot.TERTIARY); cast(h, p, action);
        h.runAfterDelay(12, () -> {
            try {
                h.assertTrue(ElectroService.isHolding(p), "The borrowed lightning key is an actual held channel");
                MimicryService.remember(p, ModAbilities.PYROKINESIS_ID, 5);
                action.release(context(p));
                h.assertTrue(!ElectroService.isHolding(p), "The release must target the original lightning action instead of the newly selected Flare");
                h.succeed();
            } finally { cleanup(p, enemy); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "compat.cryo_loom")
    public static void doubleWeaveAndSuspensionStitchChangeTheActualIceBridge(GameTestHelper h) {
        ServerPlayer p = player(h, ModAbilities.CRYOKINESIS_ID);
        learn(p, ModAbilities.CRYOKINESIS_ID, "cryokinesis_double_weave", "cryokinesis_suspension_stitch");
        p.setXRot(-30);
        for (int i = 1; i <= 24; i++) for (int side = -1; side <= 1; side++)
            h.getLevel().getChunkAt(BlockPos.containing(p.position().add(side, -1 + i / 4, i)));
        try {
            h.assertTrue(ExpansionSkills.activate(new AbilityUseContext(p, ModAbilities.CRYOKINESIS_ID, 5, false), "rime_loom"), "The learned loom creates a bridge");
            BlockPos farRaised = BlockPos.containing(p.position().add(0, 5, 24));
            h.assertTrue(h.getLevel().getBlockState(farRaised).is(Blocks.PACKED_ICE), "Suspension Stitch reaches twenty-four blocks and rises with upward aim");
            h.assertTrue(h.getLevel().getBlockState(farRaised.east()).is(Blocks.PACKED_ICE)
                            && h.getLevel().getBlockState(farRaised.west()).is(Blocks.PACKED_ICE),
                    "Double Weave creates a real three-block-wide bridge");
            h.succeed();
        } finally {
            for (int i = 1; i <= 24; i++) for (int side = -1; side <= 1; side++)
                h.getLevel().setBlock(BlockPos.containing(p.position().add(side, -1 + i / 4, i)), Blocks.AIR.defaultBlockState(), 3);
            cleanup(p);
        }
    }

    @GameTest(template = "rewind_empty", batch = "compat.cryo_healing", timeoutTicks = 90)
    public static void sterileIceAndSecondSpringCleanseAndHealWhenTheCocoonOpens(GameTestHelper h) {
        ServerPlayer p = player(h, ModAbilities.CRYOKINESIS_ID); p.setShiftKeyDown(true); p.setHealth(10);
        LivingEntity friend = target(h, p.position().add(2, 0, 0), true); friend.setHealth(10);
        learn(p, ModAbilities.CRYOKINESIS_ID, "cryokinesis_sterile_ice", "cryokinesis_second_spring");
        p.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
        h.assertTrue(ExpansionSkills.activate(new AbilityUseContext(p, ModAbilities.CRYOKINESIS_ID, 5, true), "stillheart"), "The cocoon can shelter its owner");
        h.assertTrue(!p.hasEffect(MobEffects.POISON), "Sterile Ice removes harmful effects as the cocoon forms");
        h.runAfterDelay(65, () -> {
            try {
                h.assertTrue(p.getHealth() == 16 && friend.getHealth() == 12, "Second Spring heals its recipient six health and nearby friends two when the shell opens");
                h.succeed();
            } finally { cleanup(p, friend); }
        });
    }

    @GameTest(template = "rewind_empty", batch = "compat.cryo_constellation", timeoutTicks = 160)
    public static void longWinterAndShatteredSkyKeepAndStrengthenTheFrozenLink(GameTestHelper h) {
        ServerPlayer p = player(h, ModAbilities.CRYOKINESIS_ID);
        LivingEntity first = target(h, p.position().add(0, 0, 6), false), second = target(h, p.position().add(2, 0, 7), false);
        learn(p, ModAbilities.CRYOKINESIS_ID, "cryokinesis_long_winter", "cryokinesis_shattered_sky");
        h.assertTrue(ExpansionSkills.activate(new AbilityUseContext(p, ModAbilities.CRYOKINESIS_ID, 5, false), "brittle_constellation"), "The constellation records real nearby foes");
        h.runAfterDelay(130, () -> {
            try {
                CryoService.freezeSolid(first, 40, p); second.invulnerableTime = 0;
                float before = second.getHealth(); first.hurt(p.damageSources().playerAttack(p), 1);
                h.assertTrue(before - second.getHealth() >= 7 && CryoService.isFrozen(second),
                        "After the ordinary link would expire, Long Winter still propagates Shattered Sky's stronger hit and refreeze");
                h.succeed();
            } finally { cleanup(p, first, second); }
        });
    }
}
