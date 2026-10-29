package com.jayptucker.heroesevolved.ability.service;

import com.jayptucker.heroesevolved.ability.AbilityAction;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Abilities rank up by being used: Rank I to start, Rank II after 25 uses (10% shorter cooldown) and
 * Rank III after 75 (20% shorter cooldown and 10% less stamina). Uses are counted per power, so a power
 * you get back keeps what you trained, and they survive death.
 */
public final class AbilityRanks {
    public static final int RANK_TWO = 25, RANK_THREE = 75;

    private AbilityRanks() {}

    public static int rank(int uses) { return uses >= RANK_THREE ? 3 : uses >= RANK_TWO ? 2 : 1; }

    public static int uses(ServerPlayer player, ResourceLocation power, AbilityAction action) {
        return player.getData(ModDataAttachments.POWER_JOURNEY).uses(power.getPath(), action.definition().id().getPath());
    }

    public static int rank(ServerPlayer player, ResourceLocation power, AbilityAction action) {
        return rank(uses(player, power, action));
    }

    public static int energy(ServerPlayer player, ResourceLocation power, AbilityAction action, int cost) {
        return rank(player, power, action) >= 3 && cost > 0 ? Math.max(1, (int) Math.ceil(cost * 0.9)) : cost;
    }

    public static int cooldown(ServerPlayer player, ResourceLocation power, AbilityAction action, int ticks) {
        int rank = rank(player, power, action);
        if (ticks <= 0 || rank == 1) return ticks;
        return Math.max(1, (int) Math.ceil(ticks * (rank == 3 ? 0.8 : 0.9)));
    }

    /** Counts one successful use, and announces a new rank. */
    public static void used(ServerPlayer player, ResourceLocation power, AbilityAction action) {
        int before = uses(player, power, action);
        player.setData(ModDataAttachments.POWER_JOURNEY,
                player.getData(ModDataAttachments.POWER_JOURNEY).used(power.getPath(), action.definition().id().getPath()));
        int now = rank(before + 1);
        if (now > rank(before)) {
            player.displayClientMessage(Component.translatable(action.definition().displayNameKey())
                    .append(Component.literal(now == 3 ? " reached Rank III: 20% faster recovery, 10% less stamina."
                            : " reached Rank II: 10% faster recovery."))
                    .withStyle(ChatFormatting.GOLD), false);
            player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.6F);
        }
    }
}
