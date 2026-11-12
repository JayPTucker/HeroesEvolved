package com.jayptucker.heroesevolved.client;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.network.SpeedCatchPayload;
import com.jayptucker.heroesevolved.speed.SpeedMovement;
import com.jayptucker.heroesevolved.time.ProjectileCatchGeometry;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A speedster's hands while Accelerate is on: hold attack on an enemy to throw punches as fast as
 * the server allows, and right-click a shot in flight to catch it.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class SpeedHands {
    /** Ticks between automatic punches while attack is held; the server enforces its own limit. */
    private static final int PUNCH_EVERY = 3;
    private static int held;

    private SpeedHands() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null || mc.screen != null || mc.gameMode == null || !mc.options.keyAttack.isDown()
                || player.isUsingItem() || !SpeedMovement.accelerating(player)) { held = 0; return; }
        // The first press is vanilla's own click; repeats start once the button is held.
        if (++held < PUNCH_EVERY || held % PUNCH_EVERY != 0) return;
        if (mc.hitResult instanceof EntityHitResult hit && hit.getEntity().isAlive()) {
            mc.gameMode.attack(player, hit.getEntity());
            player.swing(InteractionHand.MAIN_HAND);
        }
    }

    @SubscribeEvent
    public static void use(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        var player = mc.player;
        if (!event.isUseItem() || event.getHand() != InteractionHand.MAIN_HAND || player == null || mc.level == null) return;
        boolean accelerating = SpeedMovement.accelerating(player), timeCatch = ClientProjectileTime.canCatch();
        if (!accelerating && !timeCatch) return;
        Projectile best = null;
        double bestDistance = Double.MAX_VALUE;
        // Shots are not pickable, so the crosshair never lands on them: trace a generous box instead.
        for (Projectile shot : mc.level.getEntitiesOfClass(Projectile.class, player.getBoundingBox().inflate(ProjectileCatchGeometry.REACH),
                s -> s.isAlive() && (ClientProjectileTime.controls(s) || accelerating && s.getDeltaMovement().lengthSqr() > .0025))) {
            double distance = ProjectileCatchGeometry.hitDistanceSquared(player, shot);
            if (distance < bestDistance) { bestDistance = distance; best = shot; }
        }
        if (best == null) return;
        PacketDistributor.sendToServer(new SpeedCatchPayload(best.getId()));
        event.setSwingHand(true);
        event.setCanceled(true);
    }
}
