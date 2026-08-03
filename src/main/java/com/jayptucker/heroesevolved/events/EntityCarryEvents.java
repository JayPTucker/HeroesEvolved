package com.jayptucker.heroesevolved.events;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.entity.EntityCarryService;
import com.jayptucker.heroesevolved.entity.TemporalEchoEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Lets any player carry one entity at a time. Riding is used instead of
 * client-only movement so the carried entity stays synchronized in multiplayer.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class EntityCarryEvents {
    private EntityCarryEvents() {
    }

    @SubscribeEvent
    public static void onEntityInteract(
            PlayerInteractEvent.EntityInteract event
    ) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || event.getHand() != InteractionHand.MAIN_HAND
                || !player.isShiftKeyDown()) {
            return;
        }

        Entity target = event.getTarget();
        if (target instanceof TemporalEchoEntity echo
                && com.jayptucker.heroesevolved.time.TemporalParadoxService
                .restoreByEcho(player, echo.getProfileId())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        if (!canPickUp(player, target)) {
            return;
        }

        if (EntityCarryService.pickUp(player, target)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(
            PlayerInteractEvent.RightClickBlock event
    ) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || event.getHand() != InteractionHand.MAIN_HAND
                || !player.isShiftKeyDown()
                || !EntityCarryService.isCarrying(player)) {
            return;
        }

        BlockPos placementBlock = event.getPos().relative(event.getFace());
        Vec3 destination = Vec3.atBottomCenterOf(placementBlock);
        if (EntityCarryService.setDown(player, destination)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    private static boolean canPickUp(ServerPlayer player, Entity target) {
        return target != player
                && !(target instanceof TemporalEchoEntity)
                && !target.isPassenger()
                && target.getPassengers().isEmpty()
                && !EntityCarryService.isCarrying(player);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            EntityCarryService.tick(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            EntityCarryService.release(player);
        }
    }

    @SubscribeEvent
    public static void onCarriedEntityAttacked(LivingIncomingDamageEvent event) {
        // Invulnerability is the primary safeguard; cancelling this event as
        // well prevents attack animations and edge-case mod damage while an
        // entity is visibly being carried.
        if (EntityCarryService.isBeingCarried(event.getEntity())) {
            event.setCanceled(true);
        }
    }
}
