package com.jayptucker.heroesevolved.client;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.speed.SpeedMovement;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * A speedster running off a ledge of a block or two goes straight down onto the lower ground instead of hanging in
 * the air. It is done here, on the player's own client, because the client owns its movement: the run keeps every
 * bit of its speed, and the server just sees the next step land lower (a server teleport would stop the run dead).
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class SpeedLedge {
    private static boolean grounded;

    private SpeedLedge() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var player = Minecraft.getInstance().player;
        if (player == null) { grounded = false; return; }
        boolean now = player.onGround();
        // Walked off (not jumped: a jump leaves the ground going up) while running under Accelerate.
        if (!now && grounded && player.getDeltaMovement().y <= 0 && player.isSprinting() && !player.isShiftKeyDown()
                && !player.isInWater() && !player.getAbilities().flying && SpeedMovement.accelerating(player)) {
            double drop = SpeedMovement.ledge(player);
            if (drop > 0) {
                player.setPos(player.getX(), player.getY() - drop, player.getZ());
                player.setDeltaMovement(player.getDeltaMovement().multiply(1, 0, 1));
                player.resetFallDistance();
                now = true;
            }
        }
        grounded = now;
    }
}
