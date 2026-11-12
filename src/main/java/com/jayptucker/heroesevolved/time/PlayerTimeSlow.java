package com.jayptucker.heroesevolved.time;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.expansion.AdvancedTechniques;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForgeMod;

/** Synced attributes slow players without skipping the ticks that handle their input. */
public final class PlayerTimeSlow {
    private static final ResourceLocation SLOW = ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "time_slow");

    private PlayerTimeSlow() {}

    public static void update(ServerPlayer player) {
        int interval = player.isAlive() && !player.isSpectator()
                ? Math.max(TimeSlowService.playerSlowInterval(player), AdvancedTechniques.playerSlowInterval(player)) : 1;
        set(player.getAttribute(Attributes.MOVEMENT_SPEED), interval);
        set(player.getAttribute(NeoForgeMod.SWIM_SPEED), interval);
        set(player.getAttribute(Attributes.ATTACK_SPEED), interval);
    }

    public static double movementScale(Player player) {
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        var modifier = speed == null ? null : speed.getModifier(SLOW);
        return modifier == null ? 1 : 1 + modifier.amount();
    }

    private static void set(AttributeInstance attribute, int interval) {
        if (attribute == null) return;
        if (interval == 1) {
            attribute.removeModifier(SLOW);
            return;
        }
        double amount = 1D / interval - 1;
        var current = attribute.getModifier(SLOW);
        if (current == null || current.amount() != amount)
            attribute.addOrUpdateTransientModifier(new AttributeModifier(SLOW, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }
}
