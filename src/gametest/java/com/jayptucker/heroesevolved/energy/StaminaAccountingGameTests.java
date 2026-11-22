package com.jayptucker.heroesevolved.energy;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.amplify.AmpService;
import com.jayptucker.heroesevolved.amplify.Amplification;
import com.jayptucker.heroesevolved.config.HeroesEvolvedConfig;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.network.StaminaPayload;
import com.jayptucker.heroesevolved.progression.MasteryService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Stamina packets and Mastery must describe the amount actually paid, including Surge and unlimited energy. */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class StaminaAccountingGameTests {
    private record Actor(ServerPlayer player, List<StaminaPayload> packets) {
        StaminaPayload last() { return packets.getLast(); }
    }

    private static Actor player(GameTestHelper h, ResourceLocation power) {
        ServerPlayer p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "stamina-account"));
        List<StaminaPayload> packets = new ArrayList<>();
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        p.connection = new ServerGamePacketListenerImpl(p.getServer(), connection, p,
                CommonListenerCookie.createInitial(p.getGameProfile(), false)) {
            @Override public boolean hasChannel(ResourceLocation id) { return true; }
            @Override public void send(Packet<?> packet) {
                if (packet instanceof ClientboundCustomPayloadPacket custom && custom.payload() instanceof StaminaPayload stamina)
                    packets.add(stamina);
            }
        };
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 2, 3))));
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(power).unlock(power));
        p.setData(ModDataAttachments.PLAYER_UNLIMITED_ENERGY, false);
        PlayerProgressionService.setPowerLevel(p, 1);
        return new Actor(p, packets);
    }

    private static void cleanup(Actor... actors) {
        for (Actor actor : actors) {
            ServerPlayer p = actor.player();
            StaminaDrain.forget(p);
            Amplification.logout(new PlayerEvent.PlayerLoggedOutEvent(p));
            MasteryService.forgetPlayer(p.getUUID());
            PlayerEnergyService.forget(p);
            p.discard();
            p.connection.getConnection().channel().close();
        }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "stamina.initialsync")
    public static void aFreshStaminaSyncHasNoRecoveryCountdown(GameTestHelper h) {
        Actor a = player(h, ModAbilities.TIME_MANIPULATION_ID);
        try {
            a.player().setData(ModDataAttachments.PLAYER_ENERGY, new PlayerEnergyData(50));
            PlayerEnergyService.sendNow(a.player());
            h.assertTrue(!a.packets().isEmpty(), "The stamina packet reached the connection");
            h.assertTrue(a.last().pauseTicks() == 0, "A player who never spent stamina has no pause: " + a.last().pauseTicks());
            h.assertTrue(a.last().regen() > 0, "Recovery is active");
            PlayerEnergyService.tryConsume(a.player(), 5);
            PlayerEnergyService.sendNow(a.player());
            h.assertTrue(a.last().pauseTicks() == HeroesEvolvedConfig.COMMON.staminaRecoveryDelayTicks.get(), "A real spend still sends its real pause");
            h.succeed();
        } finally { cleanup(a); }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "stamina.initialrefusal")
    public static void refusingAMoveDoesNotInventARecoveryCountdown(GameTestHelper h) {
        Actor a = player(h, ModAbilities.TIME_MANIPULATION_ID);
        try {
            PlayerEnergyService.refused(a.player());
            h.assertTrue(!a.packets().isEmpty(), "The refusal packet reached the connection");
            h.assertTrue((a.last().flags() & StaminaPayload.REFUSED) != 0, "The refusal still flashes the HUD");
            h.assertTrue(a.last().pauseTicks() == 0, "A refusal without a spend has no recovery pause: " + a.last().pauseTicks());
            h.succeed();
        } finally { cleanup(a); }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "stamina.surgerate")
    public static void aSurgedHeldPowersReportedDrainMatchesWhatItPays(GameTestHelper h) {
        Actor amp = player(h, ModAbilities.AMPLIFICATION_ID), ally = player(h, ModAbilities.TIME_MANIPULATION_ID);
        try {
            h.assertTrue(AmpService.surge(amp.player(), ally.player()), "The ally is surged");
            double rate = 20 * HeroesEvolvedConfig.COMMON.staminaCostScale.get();
            StaminaDrain.start(ally.player(), "accounting", 20, false, () -> {});
            double before = PlayerEnergyService.getStamina(ally.player());
            StaminaDrain.tick(h.getLevel().getServer());
            double paidPerSecond = (before - PlayerEnergyService.getStamina(ally.player())) * 20;
            h.assertTrue(Math.abs(paidPerSecond - rate * Amplification.SURGE_DRAIN) < 1e-6, "The Surge halves the real payment");
            h.assertTrue(Math.abs(StaminaDrain.rate(ally.player()) - paidPerSecond) < 1e-6, "The reported drain matches the payment");
            PlayerEnergyService.tick(ally.player());
            h.assertTrue(Math.abs(ally.last().drain() - paidPerSecond) < 1e-5, "The HUD receives that same drain rate");
            h.succeed();
        } finally { cleanup(ally, amp); }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "stamina.surgemastery")
    public static void aSurgedHeldPowerEarnsMasteryOnlyForStaminaActuallySpent(GameTestHelper h) {
        Actor amp = player(h, ModAbilities.AMPLIFICATION_ID), ally = player(h, ModAbilities.TIME_MANIPULATION_ID);
        try {
            h.assertTrue(AmpService.surge(amp.player(), ally.player()), "The ally is surged");
            StaminaDrain.start(ally.player(), "accounting", 20, false, () -> {});
            double before = PlayerEnergyService.getStamina(ally.player());
            long masteryBefore = PlayerProgressionService.getData(ally.player()).mastery();
            for (int i = 0; i < 20; i++) StaminaDrain.tick(h.getLevel().getServer());
            double paid = before - PlayerEnergyService.getStamina(ally.player());
            long expected = (long) Math.floor(Math.floor(paid + 1e-6) * HeroesEvolvedConfig.COMMON.masteryPerEnergySpent.get());
            long earned = PlayerProgressionService.getData(ally.player()).mastery() - masteryBefore;
            h.assertTrue(earned == expected, "Mastery follows the " + paid + " stamina paid: earned " + earned + ", expected " + expected);
            h.succeed();
        } finally { cleanup(ally, amp); }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "stamina.unlimitedmastery")
    public static void unlimitedEnergyDoesNotBankFreeFractionalMastery(GameTestHelper h) {
        Actor a = player(h, ModAbilities.TIME_MANIPULATION_ID);
        try {
            PlayerEnergyService.setUnlimitedEnergy(a.player(), true);
            StaminaDrain.start(a.player(), "accounting", 10, false, () -> {});
            for (int i = 0; i < 3; i++) StaminaDrain.tick(h.getLevel().getServer());
            h.assertTrue(PlayerProgressionService.getData(a.player()).mastery() == 0, "Unlimited energy earns no Mastery");
            PlayerEnergyService.setUnlimitedEnergy(a.player(), false);
            double before = PlayerEnergyService.getStamina(a.player());
            StaminaDrain.tick(h.getLevel().getServer());
            PlayerEnergyService.tryConsume(a.player(), 1);
            MasteryService.awardEnergySpent(a.player(), 1);
            double paid = before - PlayerEnergyService.getStamina(a.player());
            long expected = (long) Math.floor(paid * HeroesEvolvedConfig.COMMON.masteryPerEnergySpent.get());
            long earned = PlayerProgressionService.getData(a.player()).mastery();
            h.assertTrue(earned == expected, "Leaving unlimited mode cannot cash in a free fraction: " + earned + " from " + paid + " stamina");
            h.succeed();
        } finally { cleanup(a); }
    }

    @GameTest(template = "rewind_empty", timeoutTicks = 40, batch = "stamina.fractionaloverexertion")
    public static void overexertionSpendsTheLastFractionAndPausesRecovery(GameTestHelper h) {
        Actor a = player(h, ModAbilities.TIME_MANIPULATION_ID);
        try {
            a.player().setData(ModDataAttachments.PLAYER_ENERGY, new PlayerEnergyData(0.75));
            OverexertionService.apply(a.player(), 5);
            h.assertTrue(PlayerEnergyService.getStamina(a.player()) == 0, "Overexertion spends the final fraction too: " + PlayerEnergyService.getStamina(a.player()));
            h.assertTrue(PlayerEnergyService.paused(a.player(), a.player().level().getGameTime()), "Spending a fraction still pauses recovery");
            h.succeed();
        } finally { cleanup(a); }
    }
}
