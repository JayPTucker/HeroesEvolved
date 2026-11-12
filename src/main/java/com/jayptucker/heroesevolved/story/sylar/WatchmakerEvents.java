package com.jayptucker.heroesevolved.story.sylar;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** The fight's hooks into the game: full-strength swings (which crack his watch), the watch's memory, going home. */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class WatchmakerEvents {
    private static final Map<UUID, Long> SWINGS = new HashMap<>();

    private WatchmakerEvents() {}

    /** Fires before the swing resets the attack meter, so this is where its charge can be read. */
    @SubscribeEvent public static void attack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getTarget() instanceof SylarEntity)) return;
        if (player.getAttackStrengthScale(0.5F) >= 0.9F) swing(player);
        else SWINGS.remove(player.getUUID());
    }

    /** A fully charged swing is landing this tick (tests call this to stand in for one). */
    public static void swing(Player player) { SWINGS.put(player.getUUID(), player.level().getGameTime()); }

    /** Whether the hit landing now is a fully charged swing (each swing counts once). */
    static boolean fullSwing(Player player) {
        Long at = SWINGS.remove(player.getUUID());
        return at != null && player.level().getGameTime() - at <= 1;
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) { SylarsWatchItem.tick(event.getServer()); }

    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) WatchmakerArena.forget(player);
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        SWINGS.remove(event.getEntity().getUUID());
        SylarsWatchItem.forget(event.getEntity().getUUID());
    }
}
