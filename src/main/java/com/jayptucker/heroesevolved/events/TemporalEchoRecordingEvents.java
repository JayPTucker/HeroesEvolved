package com.jayptucker.heroesevolved.events;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.time.TemporalEchoRecordingService;
import com.jayptucker.heroesevolved.time.TemporalSnapshotService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Captures real Present-world block actions for later echo playback. */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class TemporalEchoRecordingEvents {
    private TemporalEchoRecordingEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled()
                || !(event.getLevel() instanceof ServerLevel level)
                || TemporalSnapshotService.isTemporalDimension(level)
                || !(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }

        TemporalEchoRecordingService.recordBlockAction(
                player,
                event.getPos(),
                event.getState(),
                false
        );
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled()
                || !(event.getLevel() instanceof ServerLevel level)
                || TemporalSnapshotService.isTemporalDimension(level)
                || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        TemporalEchoRecordingService.recordBlockAction(
                player,
                event.getPos(),
                event.getPlacedBlock(),
                true
        );
    }
}
