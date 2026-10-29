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
 * One attached riding graph, with cleanup for both the carrier and the passenger.
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

        if (EntityCarryService.pickUp(player, target, 4.0D)) {
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
        return EntityCarryService.canPickUp(player, target, 4.0D);
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
    public static void onPlayerChangedDimension(
            PlayerEvent.PlayerChangedDimensionEvent event
    ) {
        // Without this the anchor stayed behind in the old dimension and
        // dragged the carried entity to the player's new coordinates there.
        if (event.getEntity() instanceof ServerPlayer player) {
            EntityCarryService.release(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerDeath(
            net.neoforged.neoforge.event.entity.living.LivingDeathEvent event
    ) {
        EntityCarryService.releaseEntity(event.getEntity());
    }

    @SubscribeEvent
    public static void onEntityJoined(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide()) EntityCarryService.recover(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) EntityCarryService.recover(player);
    }

    @SubscribeEvent
    public static void onServerStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        EntityCarryService.releaseAll();
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
