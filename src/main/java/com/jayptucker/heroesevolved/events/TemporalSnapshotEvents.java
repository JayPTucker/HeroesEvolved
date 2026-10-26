package com.jayptucker.heroesevolved.events;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.data.TemporalErasureSavedData;
import com.jayptucker.heroesevolved.entity.TemporalEchoEntity;
import com.jayptucker.heroesevolved.time.TemporalEchoRecordingService;
import com.jayptucker.heroesevolved.time.TemporalEchoService;
import com.jayptucker.heroesevolved.time.TemporalEntityEchoService;
import com.jayptucker.heroesevolved.time.TemporalSnapshotService;
import com.jayptucker.heroesevolved.time.TemporalChestService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Mirrors authorised player block edits from a snapshot into its present. */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class TemporalSnapshotEvents {
    private TemporalSnapshotEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        TemporalSnapshotService.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TemporalSnapshotService.resumeInterruptedBuild(player);
            com.jayptucker.heroesevolved.time.TemporalParadoxService.login(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TemporalEchoRecordingService.forgetPlayer(player.getUUID());
            com.jayptucker.heroesevolved.time.TemporalParadoxService.logout(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(
            PlayerEvent.PlayerChangedDimensionEvent event
    ) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TemporalSnapshotService.onLeftPast(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        // Dying in the Past respawns the player in the Present, but the
        // visit data is copied on death and used to linger.
        if (event.getEntity() instanceof ServerPlayer player) {
            TemporalSnapshotService.onLeftPast(player);
        }
    }

    /**
     * Right-clicking your own past self is a face-to-face meeting, and a
     * paradox. Past mobs are memories and cannot be used, ridden or traded.
     */
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        Entity target = event.getTarget();
        if (target instanceof TemporalEchoEntity echo
                && !player.isShiftKeyDown()
                && echo.isEchoOf(player)) {
            if (event.getHand() == InteractionHand.MAIN_HAND) {
                TemporalEchoService.encounter(echo, player);
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        if (TemporalEntityEchoService.isPastCopy(target)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && TemporalEntityEchoService.isReplaying(event.getTarget())) {
            TemporalEntityEchoService.disturb(event.getTarget(), player);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (TemporalEntityEchoService.isTracked(event.getEntity())) {
            TemporalEntityEchoService.onPastEntityKilled(
                    event.getEntity(),
                    event.getSource().getEntity() instanceof ServerPlayer killer
                            ? killer
                            : null
            );
        }
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        Entity entity = event.getEntity();
        if (TemporalSnapshotService.isTemporalDimension(level)) {
            // A Past copy saved with its chunk outlived its visit. Echoes are
            // rebuilt fresh on every visit, so drop the stale one.
            if (TemporalEntityEchoService.isPastCopy(entity)
                    && !TemporalEntityEchoService.isTracked(entity)) {
                event.setCanceled(true);
            }
            return;
        }

        // Killed in the Past, so it never survived to the Present.
        if (!(entity instanceof net.minecraft.world.entity.player.Player)
                && TemporalErasureSavedData.get(level.getServer())
                .isErasedAnywhere(entity.getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getContainer() instanceof net.minecraft.world.inventory.ChestMenu menu) {
            TemporalChestService.onOpen(player, menu);
        }
    }

    @SubscribeEvent
    public static void onContainerClose(PlayerContainerEvent.Close event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getContainer() instanceof net.minecraft.world.inventory.ChestMenu menu) {
            TemporalChestService.onClose(player, menu);
        }
    }

    @SubscribeEvent
    public static void onItemPickedUp(ItemEntityPickupEvent.Post event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            int pickedUp = event.getOriginalStack().getCount()
                    - event.getCurrentStack().getCount();
            if (pickedUp <= 0) {
                return;
            }
            com.jayptucker.heroesevolved.time.TemporalParadoxService
                    .onTemporalLootPickedUp(
                            player,
                            event.getOriginalStack().copyWithCount(pickedUp)
                    );
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel snapshotLevel)
                || !TemporalSnapshotService.isTemporalDimension(snapshotLevel)
                || !(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }

        event.setCanceled(true);
        if (!TemporalSnapshotService.isVisitorInsideSnapshot(player,
                event.getPos())) {
            return;
        }

        ServerLevel present = TemporalSnapshotService.getPresentLevel(player);
        if (present == null) {
            return;
        }

        BlockPos presentPos = TemporalSnapshotService.toPresentPosition(
                player, event.getPos());
        if (!com.jayptucker.heroesevolved.effects.WorldMutationPolicy.canEdit(player, present, presentPos)) {
            player.displayClientMessage(Component.literal("That part of the Present is protected."), true);
            return;
        }
        if (snapshotLevel.getBlockEntity(event.getPos())
                instanceof net.minecraft.world.level.block.entity.ChestBlockEntity
                pastChest) {
            TemporalChestService.destroyPastChest(
                    player, pastChest, present, presentPos
            );
            snapshotLevel.setBlock(event.getPos(),
                    net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                    3);
            return;
        }

        BlockState pastState = snapshotLevel.getBlockState(event.getPos());
        BlockState presentState = present.getBlockState(presentPos);
        boolean presentStillMatches = presentState.is(pastState.getBlock());

        // Only chests have their contents copied into the Past. Breaking any
        // other container there would drop an empty Past copy while silently
        // deleting the Present one's items (a shulker box, for example).
        if (presentStillMatches && present.getBlockEntity(presentPos) != null) {
            player.displayClientMessage(Component.literal(
                    "This is anchored too firmly in time to change."
            ), true);
            return;
        }

        Block.dropResources(
                pastState,
                snapshotLevel,
                event.getPos(),
                snapshotLevel.getBlockEntity(event.getPos()),
                player,
                player.getMainHandItem()
        );

        // The Present reflects the changed timeline, but never creates a
        // second item drop that could be collected for duplication. If the
        // Present has since built something else here, that later block is
        // not the one broken in the Past, so leave it alone.
        if (presentStillMatches) {
            present.destroyBlock(presentPos, false, player);
        }
        snapshotLevel.setBlock(event.getPos(),
                net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel snapshotLevel)
                || !TemporalSnapshotService.isTemporalDimension(snapshotLevel)
                || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (!TemporalSnapshotService.isVisitorInsideSnapshot(player,
                event.getPos())) {
            event.setCanceled(true);
            return;
        }

        ServerLevel present = TemporalSnapshotService.getPresentLevel(player);
        BlockPos presentPos = TemporalSnapshotService.toPresentPosition(
                player, event.getPos());
        if (present == null || !present.getBlockState(presentPos).canBeReplaced()) {
            event.setCanceled(true);
            return;
        }

        // Vanilla consumes the item in the snapshot; the matching state is
        // then applied to the present only when its position is replaceable.
        if (!com.jayptucker.heroesevolved.effects.WorldMutationPolicy.place(player, present, presentPos, event.getPlacedBlock()))
            event.setCanceled(true);
    }
}
