package com.jayptucker.heroesevolved.energy;

import com.jayptucker.heroesevolved.config.HeroesEvolvedConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import com.jayptucker.heroesevolved.network.OverexertionEffectPayload;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Objects;

public final class OverexertionService {
    private OverexertionService() {
    }

    public static void apply(

        ServerPlayer player,
        int energyCost
    ) {
        int nauseaDurationTicks = HeroesEvolvedConfig.COMMON
        .overexertionNauseaDurationSeconds
        .get() * 20;

        Objects.requireNonNull(player, "Player cannot be null.");

        if (energyCost < 0) {
            throw new IllegalArgumentException(
                "Energy cost cannot be negative."
            );
        }

        int availableEnergy = PlayerEnergyService.getEnergy(player);

        // This method should only run if the player lacks enough energy.
        // Calling it with sufficient energy would be a programming mistake.
        if (availableEnergy >= energyCost) {
            throw new IllegalStateException(
                "Overexertion requires insufficient energy."
            );
        }

        int missingEnergy = energyCost - availableEnergy;

        // The ability consumes every bit of remaining energy.
        PlayerEnergyService.consumeUpTo(player, energyCost);

        // Larger energy shortages cause greater physical strain.
        float damage = (float) Math.min(
            20.0D,
            HeroesEvolvedConfig.COMMON.overexertionBaseDamage.get()
                + (
                missingEnergy
                    * HeroesEvolvedConfig.COMMON
                    .overexertionDamagePerMissingEnergy
                    .get()
            )
        );

        player.sendSystemMessage(
            Component.translatable(
                "message.heroesevolved.overexertion"
            )
            .withStyle(ChatFormatting.DARK_RED)
        );

        // Amplifier 0 means Weakness I.
        player.addEffect(new MobEffectInstance(
            MobEffects.WEAKNESS,
            HeroesEvolvedConfig.COMMON
                .overexertionWeaknessDurationSeconds
                .get() * 20,
            0
        ));

        // CONFUSION is Minecraft's internal name for Nausea.
        // Amplifier 0 means Nausea I.
        player.addEffect(new MobEffectInstance(
            MobEffects.CONFUSION,
            nauseaDurationTicks,
            0
        ));

        // Only the affected player receives this packet.
        PacketDistributor.sendToPlayer(
            player,
            new OverexertionEffectPayload(nauseaDurationTicks)
        );

        // Generic damage has no attacker, because the player caused it to themselves.
        player.hurt(player.damageSources().generic(), damage);
    }
}