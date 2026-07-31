package com.jayptucker.heroesevolved.events;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.time.TemporalSnapshotService;
import com.jayptucker.heroesevolved.time.TemporalChestService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
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
        Block.dropResources(
                pastState,
                snapshotLevel,
                event.getPos(),
                snapshotLevel.getBlockEntity(event.getPos()),
                player,
                player.getMainHandItem()
        );

        // The Present reflects the changed timeline, but never creates a
        // second item drop that could be collected for duplication.
        present.destroyBlock(presentPos, false, player);
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
        present.setBlock(presentPos, event.getPlacedBlock(), 3);
    }
}
