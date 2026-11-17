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

/**
 * Synced attributes slow players without skipping the ticks that handle their input. A speedster at speed is the
 * exception: slowed time drags at them instead ({@link TimeFlow#drag}), so they stay fast, just not as fast.
 */
public final class PlayerTimeSlow {
    private static final ResourceLocation SLOW = ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "time_slow");

    private PlayerTimeSlow() {}

    public static void update(ServerPlayer player) {
        boolean live = player.isAlive() && !player.isSpectator();
        int interval = live ? Math.max(TimeSlowService.playerSlowInterval(player), AdvancedTechniques.playerSlowInterval(player)) : 1;
        double drag = live ? TimeFlow.drag(player) : 1;
        // Running through slowed time is running through water: the stride keeps its share, and the hands stay quick.
        double move = drag < 1 ? drag : 1D / interval;
        double hands = drag < 1 ? 1 : 1D / interval;
        set(player.getAttribute(Attributes.MOVEMENT_SPEED), move);
        set(player.getAttribute(NeoForgeMod.SWIM_SPEED), move);
        set(player.getAttribute(Attributes.ATTACK_SPEED), hands);
    }

    public static double movementScale(Player player) {
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        var modifier = speed == null ? null : speed.getModifier(SLOW);
        return modifier == null ? 1 : 1 + modifier.amount();
    }

    private static void set(AttributeInstance attribute, double scale) {
        if (attribute == null) return;
        if (scale >= 1) {
            attribute.removeModifier(SLOW);
            return;
        }
        double amount = scale - 1;
        var current = attribute.getModifier(SLOW);
        if (current == null || current.amount() != amount)
            attribute.addOrUpdateTransientModifier(new AttributeModifier(SLOW, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }
}
