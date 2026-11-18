package com.jayptucker.heroesevolved.client;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class ClientGodMode {
    private static Set<UUID> players = Set.of();
    private ClientGodMode() {}
    public static void update(List<UUID> ids) { players = Set.copyOf(ids); }
    public static boolean enabled(UUID id) { return players.contains(id); }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { players = Set.of(); }
}
