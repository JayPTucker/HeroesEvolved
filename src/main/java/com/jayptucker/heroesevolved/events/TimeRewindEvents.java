package com.jayptucker.heroesevolved.events;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.time.TimeRewindService;
import com.jayptucker.heroesevolved.time.TimeRewindBlocks;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.EventPriority;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Keeping the timeline: births, drops, deaths and their spoils are recorded for rewinds; a timeline carries
 * across a world change (a rewind takes you back across it) but not into a new life once its death can no
 * longer be rewound; and anyone travelling back through their past cannot be hurt, and lands before they leave.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class TimeRewindEvents {
    @SubscribeEvent public static void entityBorn(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        // Something a rewind un-made while its chunk was unloaded is not allowed back when the chunk loads.
        if (com.jayptucker.heroesevolved.time.TimeRewindEntities.unmade(event.getEntity())) {
            event.setCanceled(true);
            return;
        }
        com.jayptucker.heroesevolved.time.TimeRewindEntities.born(event.getEntity(), event.loadedFromDisk());
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player)
            com.jayptucker.heroesevolved.time.PendingPlayerRewinds.get(player.getServer()).login(player);
    }
    private TimeRewindEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockDrops(BlockDropsEvent event) {
        TimeRewindBlocks.recordDrops(event.getLevel(), event.getPos(), event.getDrops());
    }

    @SubscribeEvent
    public static void onItemSpawn(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof ItemEntity item) {
            if (TimeRewindBlocks.isRestoring()) {
                event.setCanceled(true);
            } else {
                TimeRewindBlocks.recordContainerDrop(level, item);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // A trip back through the past lands before its traveller leaves, so they are saved as they were.
            com.jayptucker.heroesevolved.time.RewindTravel.finish(player.getServer(), player.getUUID());
            com.jayptucker.heroesevolved.time.SpaceTimeService.forget(player);
            com.jayptucker.heroesevolved.time.TimeStopService.stop(player);
            com.jayptucker.heroesevolved.time.TimeSlowService.stop(player);
            TimeRewindService.forget(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            com.jayptucker.heroesevolved.time.SpaceTimeService.forget(player);
            com.jayptucker.heroesevolved.time.TimeStopService.stop(player);
            com.jayptucker.heroesevolved.time.TimeSlowService.stop(player);
            // A death from the last fifteen seconds can still be rewound, so its history is kept.
            TimeRewindService.respawned(player);
        }
    }

    /** Last in line, so only a death that really happens is offered a rewind. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TimeRewindService.died(player);
            com.jayptucker.heroesevolved.time.TimeRewindPlayers.died(player, player.getServer().overworld().getGameTime());
        }
    }

    /** What a dying player drops, so undoing the death takes it back. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDrops(net.neoforged.neoforge.event.entity.living.LivingDropsEvent event) {
        if (event.getEntity() instanceof ServerPlayer player)
            com.jayptucker.heroesevolved.time.TimeRewindPlayers.dropped(player, event.getDrops());
    }

    /** Nothing reaches someone travelling back through their past. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onHurt(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        if (com.jayptucker.heroesevolved.time.RewindTravel.travelling(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onServerStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        com.jayptucker.heroesevolved.time.RewindTravel.finishAll(event.getServer());
    }

    @SubscribeEvent
    public static void onDimensionChanged(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            com.jayptucker.heroesevolved.time.SpaceTimeService.forget(player);
            com.jayptucker.heroesevolved.time.TimeStopService.stop(player);
            com.jayptucker.heroesevolved.time.TimeSlowService.stop(player);
            // The timeline carries across: a rewind takes you back into the world you were in then.
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        com.jayptucker.heroesevolved.time.SpaceTimeService.clear();
        com.jayptucker.heroesevolved.time.TimeStopService.clear();
        com.jayptucker.heroesevolved.time.TimeSlowService.clear();
        TimeRewindService.clear();
    }
}
