package com.jayptucker.heroesevolved.ability.service;

import com.jayptucker.heroesevolved.ability.AbilityUseResult;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Short, throttled feedback when an ability press does nothing.
 *
 * <p>The activation path records the specific reason ({@link #explain})
 * where the numbers are known: seconds of cooldown left, stamina needed,
 * the unlock level. The input handler then reports it once ({@link #report}).
 * When an action rejects activation itself, it has already told the
 * player why (no target, nothing to vent...), so nothing is added that
 * would overwrite that message.</p>
 */
public final class PowerFeedback {
    private static final int THROTTLE_TICKS = 10;
    private static final WeakHashMap<ServerPlayer, Long> LAST = new WeakHashMap<>();
    private static final Map<UUID, Component> PENDING = new HashMap<>();
    // The last refusal shown to each player; read by the gameplay tests.
    private static final Map<UUID, String> LAST_MESSAGE = new HashMap<>();

    private PowerFeedback() {
    }

    /** Records why the current activation failed, with its details. */
    public static void explain(ServerPlayer player, Component reason) {
        PENDING.put(player.getUUID(), reason);
    }

    /** Reports the outcome of an ability press from the input handler. */
    public static void report(ServerPlayer player, AbilityUseResult result) {
        Component pending = PENDING.remove(player.getUUID());
        if (result == AbilityUseResult.SUCCESS || result == AbilityUseResult.OVEREXERTED_SUCCESS
                // The action explained itself; don't overwrite its message.
                || result == AbilityUseResult.ACTIVATION_REJECTED) {
            return;
        }
        reject(player, pending != null ? pending : Component.literal(generic(result)));
    }

    public static void reject(ServerPlayer player, String reason) {
        reject(player, Component.literal(reason));
    }

    public static void reject(ServerPlayer player, Component reason) {
        long now = player.level().getGameTime();
        if (now - LAST.getOrDefault(player, Long.MIN_VALUE / 2) < THROTTLE_TICKS) {
            return;
        }
        LAST.put(player, now);
        LAST_MESSAGE.put(player.getUUID(), reason.getString());
        player.displayClientMessage(reason.copy().withStyle(ChatFormatting.GRAY), true);
        player.playNotifySound(SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.25F, 1.6F);
    }

    /** The last refusal message shown to a player, or null. */
    public static String lastMessage(ServerPlayer player) {
        return LAST_MESSAGE.get(player.getUUID());
    }

    private static String generic(AbilityUseResult result) {
        return switch (result) {
            case NO_ASSIGNED_POWER -> "You have not discovered a power yet.";
            case NOT_UNLOCKED -> "Your power is dormant. It awakens when your life is in danger.";
            case ACTION_LOCKED -> "Increase your power level to unlock this ability.";
            case ON_COOLDOWN -> "That ability is still recovering.";
            case INSUFFICIENT_ENERGY -> "Not enough stamina. Hold Shift to overexert.";
            case POWER_SUPPRESSED -> "The Eclipse is suppressing your power.";
            case ACTION_NOT_ASSIGNED -> "Nothing is assigned to that key.";
            default -> "You cannot use that ability right now.";
        };
    }
}
