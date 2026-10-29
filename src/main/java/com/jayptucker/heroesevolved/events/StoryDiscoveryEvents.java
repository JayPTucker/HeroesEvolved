package com.jayptucker.heroesevolved.events;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.progression.StoryDiscoveries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class StoryDiscoveryEvents {
    private StoryDiscoveryEvents() {}

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) StoryDiscoveries.tick(player);
    }

    /** Reading a note on a lectern counts too. */
    @SubscribeEvent
    public static void lectern(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player
                && player.level().getBlockEntity(event.getPos()) instanceof LecternBlockEntity lectern) {
            StoryDiscoveries.found(player, lectern.getBook());
        }
    }
}
