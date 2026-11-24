package com.jayptucker.heroesevolved.endportal;

import com.jayptucker.heroesevolved.ability.AbilityUseResult;
import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.AbilityRegistry;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.AbilityUseService;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.energy.StaminaDrain;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.registry.ModItems;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Real item insertion and ability dispatch, server-ticked FakePlayers, exact stamina, and persistent vanilla portals. */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class PortalRitualGameTests {
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h;
        final BlockPos center;
        final List<ServerPlayer> players = new ArrayList<>();
        Fixture(GameTestHelper h) {
            this.h = h; center = h.absolutePos(new BlockPos(5, 2, 5));
            for (BlockPos at : PortalGeometry.frames(center)) h.getLevel().setBlock(at,
                    Blocks.END_PORTAL_FRAME.defaultBlockState().setValue(EndPortalFrameBlock.HAS_EYE, true)
                            .setValue(EndPortalFrameBlock.FACING, PortalGeometry.facing(at.subtract(center))), 3);
        }
        ServerPlayer player(int x, int z, ResourceLocation power, int level) {
            var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "portal-test"));
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            new EmbeddedChannel(connection);
            p.connection = new ServerGamePacketListenerImpl(p.getServer(), connection, p,
                    CommonListenerCookie.createInitial(p.getGameProfile(), false)) {
                @Override public boolean hasChannel(ResourceLocation id) { return true; }
                @Override public void send(Packet<?> packet) {}
            };
            players.add(p);
            power(p, power, level);
            stand(p, x, z);
            return p;
        }
        void stand(ServerPlayer p, int x, int z) {
            p.setPos(Vec3.atBottomCenterOf(center.offset(x, 1, z)));
            p.lookAt(EntityAnchorArgument.Anchor.EYES, PortalGeometry.surface(center));
            p.yBodyRot = p.getYRot(); p.yBodyRotO = p.yBodyRot;
            p.setOnGround(true);
        }
        void power(ServerPlayer p, ResourceLocation power, int level) {
            p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().replaceWithUnlockedPower(power));
            p.setData(ModDataAttachments.PLAYER_UNLIMITED_ENERGY, false);
            PlayerProgressionService.setPowerLevel(p, level);
            PlayerEnergyService.restore(p, 999);
        }
        void opened() {
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
                h.assertTrue(h.getLevel().getBlockState(center.offset(x, 0, z)).is(Blocks.END_PORTAL), "All nine portal cells opened");
        }
        @Override public void close() {
            for (ServerPlayer p : players) {
                EndPortalRitual.logout(new PlayerEvent.PlayerLoggedOutEvent(p));
                StaminaDrain.forget(p); PlayerEnergyService.forget(p);
                p.discard(); p.connection.getConnection().channel().close();
            }
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++)
                h.getLevel().setBlock(center.offset(x, 0, z), Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private static void later(Fixture f, int ticks, Runnable check) {
        f.h.runAfterDelay(ticks, () -> {
            try { check.run(); }
            catch (RuntimeException | AssertionError failure) { f.close(); throw failure; }
        });
    }
    private static void start(Fixture f, ServerPlayer p) {
        f.h.assertTrue(AbilityUseService.activateKey(p, 1, false) == AbilityUseResult.SUCCESS, "The frame accepted the held ability key");
        f.h.assertTrue(EndPortalRitual.channeling(p), "A contextual ritual, not the regular power, started");
    }
    private static void done(Fixture f) { f.close(); f.h.succeed(); }

    @GameTest(template = "rewind_empty", batch = "portal.item", timeoutTicks = 60)
    public static void theLastEyeIsConsumedButDoesNotOpenThePortal(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            var p = f.player(0, -2, ModAbilities.PYROKINESIS_ID, 16);
            BlockPos last = f.center.offset(1, 0, -2);
            h.getLevel().setBlock(last, h.getLevel().getBlockState(last).setValue(EndPortalFrameBlock.HAS_EYE, false), 3);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.ENDER_EYE, 2));
            Items.ENDER_EYE.useOn(new UseOnContext(p, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(last), Direction.UP, last, false)));
            h.assertTrue(p.getMainHandItem().getCount() == 1, "Vanilla consumed exactly one eye");
            h.assertTrue(PortalGeometry.complete(h.getLevel(), f.center), "The real vanilla frame is filled and faces inward");
            h.assertTrue(!PortalGeometry.open(h.getLevel(), f.center), "The Mixin deferred the real vanilla opening");
            h.succeed();
        }
    }

    @GameTest(template = "rewind_empty", batch = "portal.eligibility", timeoutTicks = 60)
    public static void levelFifteenIsTooEarlyAndEveryAwakenedPowerWorksAtSixteen(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            var p = f.player(0, -2, ModAbilities.REGENERATION_ID, 15);
            double before = PlayerEnergyService.getStamina(p);
            h.assertTrue(AbilityUseService.activateKey(p, 1, true) == AbilityUseResult.CANNOT_USE, "Overexertion cannot bypass the level gate");
            h.assertTrue(PlayerEnergyService.getStamina(p) == before && !EndPortalRitual.channeling(p), "Refusal is free");
            for (ResourceLocation power : AbilityRegistry.ABILITIES.keySet()) {
                f.power(p, power, 16);
                start(f, p);
                AbilityUseService.releaseKey(p, 1);
                h.assertTrue(!EndPortalRitual.channeling(p) && !StaminaDrain.active(p, EndPortalRitual.DRAIN), "Every gift releases cleanly: " + power);
            }
            p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(ModAbilities.REGENERATION_ID));
            h.assertTrue(AbilityUseService.activateKey(p, 1, false) == AbilityUseResult.CANNOT_USE, "A dormant power cannot make an offering");
            h.succeed();
        }
    }

    @GameTest(template = "rewind_empty", batch = "portal.pair", timeoutTicks = 260)
    public static void twoPeopleWithTheSameGiftEachPayTheirOwnWholeBar(GameTestHelper h) {
        var f = new Fixture(h);
        try {
            var a = f.player(0, -2, ModAbilities.REGENERATION_ID, 16);
            var b = f.player(1, -2, ModAbilities.REGENERATION_ID, 20);
            start(f, a); start(f, b);
            later(f, 45, () -> {
                h.assertTrue(PlayerEnergyService.getStamina(a) < PlayerEnergyService.getMaximumEnergy(a), "The first bar is draining");
                h.assertTrue(PlayerEnergyService.currentRegeneration(a, h.getLevel().getGameTime()) == 0, "Recovery stays held during the offering");
                h.assertTrue(!PortalGeometry.open(h.getLevel(), f.center), "Paid windup has not opened early");
            });
            later(f, PortalGeometry.CHANNEL_TICKS + 3, () -> {
                h.assertTrue(PlayerEnergyService.getStamina(a) < 1e-5 && PlayerEnergyService.getStamina(b) < 1e-5,
                        "Different maximum stamina pools both ended at zero");
                h.assertTrue(!EndPortalRitual.channeling(a) && !EndPortalRitual.channeling(b), "Both channels released at completion");
                h.assertTrue(EndPortalRitual.release(a, "key:1") && EndPortalRitual.release(b, "key:1"),
                        "Completed offerings still consume their key-up instead of releasing a normal charged action");
            });
            later(f, PortalGeometry.CHANNEL_TICKS + PortalGeometry.OPEN_TICKS + 5, () -> { f.opened(); done(f); });
        } catch (RuntimeException | AssertionError failure) { f.close(); throw failure; }
    }

    @GameTest(template = "rewind_empty", batch = "portal.solo", timeoutTicks = 440)
    public static void aLoneTravelerCanRecoverAndChargeTheNeighbor(GameTestHelper h) {
        var f = new Fixture(h);
        try {
            var p = f.player(0, -2, ModAbilities.TELEPATHY_ID, 16);
            start(f, p);
            later(f, PortalGeometry.CHANNEL_TICKS + 3, () -> {
                h.assertTrue(!PortalGeometry.open(h.getLevel(), f.center), "One offering is remembered, not mistaken for a pair");
                h.assertTrue(PlayerEnergyService.currentRegeneration(p, h.getLevel().getGameTime()) > 0, "A solo player can recover normally");
                for (int tick = 0; tick < 500; tick++) PlayerEnergyService.tick(p);
                h.assertTrue(PlayerEnergyService.getStamina(p) >= PlayerEnergyService.getMaximumEnergy(p) * .95, "The shared stamina recovery replenished the bar");
                f.stand(p, 1, -2);
                start(f, p);
                later(f, PortalGeometry.CHANNEL_TICKS + PortalGeometry.OPEN_TICKS + 5, () -> {
                    f.opened();
                    h.assertTrue(PlayerEnergyService.getStamina(p) < 1e-5, "The second offering also emptied the lone player's bar");
                    h.assertTrue(AbilityUseService.activateKey(p, 1, false) != AbilityUseResult.SUCCESS || !EndPortalRitual.channeling(p), "An open portal never charges again");
                    done(f);
                });
            });
        } catch (RuntimeException | AssertionError failure) { f.close(); throw failure; }
    }

    @GameTest(template = "rewind_empty", batch = "portal.spacing", timeoutTicks = 260)
    public static void oppositeFramesAndTwoPeopleOnOneEyeCannotOpenIt(GameTestHelper h) {
        var f = new Fixture(h);
        try {
            var a = f.player(0, -2, ModAbilities.PYROKINESIS_ID, 16);
            var b = f.player(0, 2, ModAbilities.ELECTROKINESIS_ID, 16);
            var c = f.player(0, -2, ModAbilities.TELEPATHY_ID, 16);
            start(f, a); start(f, b);
            h.assertTrue(EndPortalRitual.press(c, "key:1") == AbilityUseResult.CANNOT_USE, "One eye cannot accept a second caster at the same time");
            later(f, PortalGeometry.CHANNEL_TICKS + PortalGeometry.OPEN_TICKS + 5, () -> {
                h.assertTrue(!PortalGeometry.open(h.getLevel(), f.center), "Opposite offerings do not meet");
                PlayerEnergyService.restore(a, 999);
                h.assertTrue(EndPortalRitual.press(a, "key:1") == AbilityUseResult.CANNOT_USE, "A remembered eye cannot be counted twice");
                h.assertTrue(!PortalGeometry.adjacent(f.center.offset(-1, 0, -2), f.center.offset(-2, 0, -1)), "Missing corners do not count as touching frames");
                done(f);
            });
        } catch (RuntimeException | AssertionError failure) { f.close(); throw failure; }
    }

    @GameTest(template = "rewind_empty", batch = "portal.release", timeoutTicks = 240)
    public static void onlyTheHeldKeyReleaseCancelsAndStopsPayment(GameTestHelper h) {
        var f = new Fixture(h);
        try {
            var p = f.player(0, -2, ModAbilities.PYROKINESIS_ID, 16);
            start(f, p);
            later(f, 30, () -> {
                AbilityUseService.releaseKey(p, 2);
                h.assertTrue(EndPortalRitual.channeling(p), "An unrelated release did not end the offering");
                AbilityUseService.releaseKey(p, 1);
                h.assertTrue(!EndPortalRitual.channeling(p) && !StaminaDrain.active(p, EndPortalRitual.DRAIN), "The actual release cleared the channel and recovery hold");
                double remaining = PlayerEnergyService.getStamina(p);
                later(f, PortalGeometry.CHANNEL_TICKS + 3, () -> {
                    h.assertTrue(PlayerEnergyService.getStamina(p) == remaining, "The server stopped taking installments immediately");
                    h.assertTrue(!PortalGeometry.open(h.getLevel(), f.center), "A canceled offering cannot finish later");
                    done(f);
                });
            });
        } catch (RuntimeException | AssertionError failure) { f.close(); throw failure; }
    }

    @GameTest(template = "rewind_empty", batch = "portal.interrupt", timeoutTicks = 100)
    public static void steppingAwaySuppressionPowerLossAndLogoutReleaseTheHold(GameTestHelper h) {
        var f = new Fixture(h);
        try {
            var move = f.player(-1, -2, ModAbilities.PYROKINESIS_ID, 16);
            var suppress = f.player(0, -2, ModAbilities.TELEPATHY_ID, 16);
            var lose = f.player(1, -2, ModAbilities.REGENERATION_ID, 16);
            start(f, move); start(f, suppress); start(f, lose);
            later(f, 12, () -> {
                f.stand(move, 0, 0);
                suppress.addEffect(new MobEffectInstance(ModItems.DAMPENED, 100));
                lose.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty());
            });
            later(f, 16, () -> {
                for (var p : f.players) h.assertTrue(!EndPortalRitual.channeling(p) && !StaminaDrain.active(p, EndPortalRitual.DRAIN), "Every interrupted caster released recovery");
                f.stand(move, -1, -2); PlayerEnergyService.restore(move, 999); start(f, move);
                EndPortalRitual.logout(new PlayerEvent.PlayerLoggedOutEvent(move));
                h.assertTrue(!EndPortalRitual.channeling(move), "Logout canceled immediately");
                done(f);
            });
        } catch (RuntimeException | AssertionError failure) { f.close(); throw failure; }
    }

    @GameTest(template = "rewind_empty", batch = "portal.payment", timeoutTicks = 240)
    public static void externallyEmptyingTheBarDoesNotCompleteAnOffering(GameTestHelper h) {
        var f = new Fixture(h);
        try {
            var a = f.player(0, -2, ModAbilities.REGENERATION_ID, 16);
            var b = f.player(1, -2, ModAbilities.TELEPATHY_ID, 16);
            start(f, a); start(f, b);
            later(f, 30, () -> PlayerEnergyService.drain(a, PlayerEnergyService.getStamina(a)));
            later(f, PortalGeometry.CHANNEL_TICKS + PortalGeometry.OPEN_TICKS + 5, () -> {
                h.assertTrue(!PortalGeometry.open(h.getLevel(), f.center), "Only one completed payment exists; an empty bar was not treated as success");
                h.assertTrue(!StaminaDrain.active(a, EndPortalRitual.DRAIN), "The failed payment released recovery");
                done(f);
            });
        } catch (RuntimeException | AssertionError failure) { f.close(); throw failure; }
    }

    @GameTest(template = "rewind_empty", batch = "portal.blocked", timeoutTicks = 60)
    public static void anIncompleteOrObstructedPortalCannotTakeAnOffering(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            var p = f.player(0, -2, ModAbilities.REGENERATION_ID, 16);
            h.getLevel().setBlock(f.center, Blocks.CHEST.defaultBlockState(), 3);
            h.assertTrue(EndPortalRitual.press(p, "key:1") == AbilityUseResult.CANNOT_USE, "An offering cannot erase the player's container");
            h.assertTrue(!EndPortalRitual.channeling(p), "An obstructed opening takes no stamina");
            h.getLevel().setBlock(f.center, Blocks.AIR.defaultBlockState(), 3);
            BlockPos other = f.center.offset(1, 0, -2);
            h.getLevel().setBlock(other, h.getLevel().getBlockState(other).setValue(EndPortalFrameBlock.HAS_EYE, false), 3);
            h.assertTrue(EndPortalRitual.press(p, "key:1") == null, "All twelve eyes are required");
            h.succeed();
        }
    }

    @GameTest(template = "rewind_empty", batch = "portal.exhaustion", timeoutTicks = 70)
    public static void deathAndInsufficientStartingStaminaNeverProduceAnOffering(GameTestHelper h) {
        var f = new Fixture(h);
        try {
            var p = f.player(0, -2, ModAbilities.REGENERATION_ID, 16);
            PlayerEnergyService.drain(p, PlayerEnergyService.getStamina(p) * .1);
            double before = PlayerEnergyService.getStamina(p);
            h.assertTrue(EndPortalRitual.press(p, "key:1") == AbilityUseResult.CANNOT_USE, "A ninety-percent bar cannot bypass the starting threshold");
            h.assertTrue(PlayerEnergyService.getStamina(p) == before, "Refusing a low starting bar is free");
            PlayerEnergyService.restore(p, 999); start(f, p);
            later(f, 12, () -> p.setHealth(0));
            later(f, 16, () -> {
                h.assertTrue(!EndPortalRitual.channeling(p) && !StaminaDrain.active(p, EndPortalRitual.DRAIN), "Death's drain callback canceled instead of completing");
                h.assertTrue(!PortalGeometry.open(h.getLevel(), f.center), "Death did not open the portal");
                done(f);
            });
        } catch (RuntimeException | AssertionError failure) { f.close(); throw failure; }
    }

    @GameTest(template = "rewind_empty", batch = "portal.restart", timeoutTicks = 260)
    public static void anOpenedPortalSurvivesClearingAllSessionState(GameTestHelper h) {
        var f = new Fixture(h);
        try {
            var a = f.player(0, -2, ModAbilities.PYROKINESIS_ID, 16);
            var b = f.player(1, -2, ModAbilities.ELECTROKINESIS_ID, 16);
            start(f, a); start(f, b);
            later(f, PortalGeometry.CHANNEL_TICKS + PortalGeometry.OPEN_TICKS + 5, () -> {
                f.opened(); EndPortalRitual.clear(); f.opened();
                h.assertTrue(EndPortalRitual.press(a, "key:1") == null, "Existing portal blocks are usable without repeating the ritual");
                done(f);
            });
        } catch (RuntimeException | AssertionError failure) { f.close(); throw failure; }
    }
}
