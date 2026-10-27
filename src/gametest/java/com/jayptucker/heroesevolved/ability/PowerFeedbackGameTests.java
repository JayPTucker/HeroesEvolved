package com.jayptucker.heroesevolved.ability;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.AbilityUseService;
import com.jayptucker.heroesevolved.ability.service.PowerFeedback;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Failed ability presses must explain themselves, with the real numbers. */
@GameTestHolder("heroesevolved")
@PrefixGameTestTemplate(false)
public final class PowerFeedbackGameTests {
    @GameTest(template = "rewind_empty", batch = "feedback.locked")
    public static void lockedActionNamesItsUnlockLevel(GameTestHelper helper) {
        ServerPlayer player = player(helper, ModAbilities.CRYOKINESIS_ID, 1);
        AbilityUseResult result = press(player, AbilitySlot.QUATERNARY, false);
        String message = PowerFeedback.lastMessage(player);
        helper.assertTrue(result == AbilityUseResult.ACTION_LOCKED, "Glacial Spikes is a Level 4 skill");
        helper.assertTrue(message != null && message.contains("unlocks at Level 4")
                && message.contains("Level 1"), "Locked feedback must name both levels: " + message);
        helper.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "feedback.cooldown")
    public static void cooldownFeedbackShowsSecondsLeft(GameTestHelper helper) {
        ServerPlayer player = player(helper, ModAbilities.QUAKE_ID, 5);
        helper.assertTrue(press(player, AbilitySlot.PRIMARY, false) == AbilityUseResult.SUCCESS,
                "Shockwave must fire the first time");
        AbilityUseResult again = press(player, AbilitySlot.PRIMARY, false);
        String message = PowerFeedback.lastMessage(player);
        helper.assertTrue(again == AbilityUseResult.ON_COOLDOWN, "A second Shockwave must wait");
        helper.assertTrue(message != null && message.contains("ready in") && message.endsWith("s."),
                "Cooldown feedback must show the time left: " + message);
        helper.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "feedback.energy")
    public static void staminaFeedbackShowsCostAndCurrentStamina(GameTestHelper helper) {
        ServerPlayer player = player(helper, ModAbilities.ELECTROKINESIS_ID, 1);
        PlayerEnergyService.consumeUpTo(player, 10_000);
        AbilityUseResult result = press(player, AbilitySlot.PRIMARY, false);
        String message = PowerFeedback.lastMessage(player);
        helper.assertTrue(result == AbilityUseResult.INSUFFICIENT_ENERGY, "No stamina, no Chain Lightning");
        helper.assertTrue(message != null && message.contains("needs") && message.contains("you have 0")
                && message.contains("Shift"), "Stamina feedback must give numbers and the hint: " + message);
        helper.succeed();
    }

    @GameTest(template = "rewind_empty", batch = "feedback.rejected")
    public static void actionsThatExplainThemselvesAreNotOverwritten(GameTestHelper helper) {
        ServerPlayer player = player(helper, ModAbilities.TELEPATHY_ID, 1);
        // Read Mind with nobody in sight explains itself ("no mind there").
        AbilityUseResult result = press(player, AbilitySlot.PRIMARY, false);
        helper.assertTrue(result == AbilityUseResult.ACTIVATION_REJECTED, "There is no mind to read");
        helper.assertTrue(PowerFeedback.lastMessage(player) == null,
                "The generic message must not overwrite the action's own explanation");
        helper.succeed();
    }

    /** Presses a key the way the network handler does, feedback included. */
    private static AbilityUseResult press(ServerPlayer player, AbilitySlot slot, boolean shift) {
        AbilityUseResult result = AbilityUseService.activateAssignedAction(player, slot, shift);
        PowerFeedback.report(player, result);
        return result;
    }

    private static ServerPlayer player(GameTestHelper helper, ResourceLocation power, int level) {
        ServerPlayer player = new FakePlayer(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "feedback-test"));
        player.setData(ModDataAttachments.PLAYER_ABILITIES,
                PlayerAbilityData.empty().assignDormant(power).unlock(power));
        PlayerProgressionService.setLevel(player, level);
        PlayerEnergyService.restore(player, PlayerEnergyService.getMaximumEnergy(player));
        player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, 2, 3))));
        return player;
    }
}
