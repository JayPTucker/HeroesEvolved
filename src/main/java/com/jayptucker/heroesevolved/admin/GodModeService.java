package com.jayptucker.heroesevolved.admin;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.network.GodModePayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Operator protection is independent of game mode, teams, and other invulnerability sources. */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class GodModeService {
    private GodModeService() {}

    public static boolean enabled(ServerPlayer player) {
        return player.getData(ModDataAttachments.PLAYER_GOD_MODE);
    }

    public static void set(ServerPlayer player, boolean enabled) {
        player.setData(ModDataAttachments.PLAYER_GOD_MODE, enabled);
        sync(player.getServer(), null);
    }

    private static void sync(MinecraftServer server, ServerPlayer excluded) {
        if (server == null) return;
        var payload = new GodModePayload(server.getPlayerList().getPlayers().stream()
                .filter(p -> p != excluded && enabled(p)).map(ServerPlayer::getUUID).toList());
        for (ServerPlayer viewer : server.getPlayerList().getPlayers())
            if (viewer != excluded) PacketDistributor.sendToPlayer(viewer, payload);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void damage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && enabled(player)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && enabled(player)) {
            event.setCanceled(true);
            player.setHealth(Math.max(1, player.getHealth()));
        }
    }

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player.getServer(), null);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player.getServer(), player);
    }
}
