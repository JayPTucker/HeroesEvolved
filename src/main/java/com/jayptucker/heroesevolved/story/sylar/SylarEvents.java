package com.jayptucker.heroesevolved.story.sylar;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sylar's hooks into the game: full-strength swings (which break his concentration), the story's clock (the prisoner and
 * the list, {@link SylarStory}), and keeping the Collection safe to leave (going home after a restart or a logout,
 * getting back what you dropped in his hall when you respawn).
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class SylarEvents {
    private static final Map<UUID, Long> SWINGS = new HashMap<>();

    private SylarEvents() {}

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

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        CollectionArena.tick(server);
        SylarVoice.tick(server);
        SylarsWatchItem.tick(server);
        SylarStory.tick(server);
    }

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        // A restart or a logout mid-fight: nobody stays in his hall without a fight to be in.
        if (CollectionArena.inCollection(player)) {
            SylarEntity boss = CollectionArena.sylar();
            if (boss == null || !boss.party().contains(player)) CollectionArena.sendHome(player);
        }
        if (player.isAlive()) unstash(player);
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        SWINGS.remove(event.getEntity().getUUID());
        SylarVoice.forget(event.getEntity().getUUID());
        SylarsWatchItem.forget(event.getEntity().getUUID());
        if (event.getEntity() instanceof ServerPlayer player) SylarStory.left(player);
        if (event.getEntity() instanceof ServerPlayer player && CollectionArena.inCollection(player)) CollectionArena.unsteal(player.getUUID());
    }

    /** Leaving the hall any way but the fight's own: they are no longer owed a way home. */
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !CollectionArena.moving(player)
                && event.getFrom().equals(SylarGeometry.DIMENSION)) CollectionArena.forget(player);
    }

    @SubscribeEvent public static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && CollectionArena.inCollection(player)) CollectionArena.forget(player);
    }

    /** He keeps nothing of yours: what you drop dying in his hall is handed back when you respawn. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void drops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !CollectionArena.inCollection(player) || event.isCanceled()) return;
        SylarData data = SylarData.get(player.getServer());
        for (ItemEntity drop : event.getDrops()) data.stash(player.getUUID(), drop.getItem(), player.registryAccess());
        event.getDrops().clear();
        event.setCanceled(true);
    }

    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) unstash(player);
    }

    private static void unstash(ServerPlayer player) {
        SylarData data = SylarData.get(player.getServer());
        if (!data.hasStash(player.getUUID())) return;
        for (var stack : data.unstash(player.getUUID(), player.registryAccess()))
            if (!player.getInventory().add(stack)) player.drop(stack, false);
        SylarVoice.narrate(player, "story.heroesevolved.sylar.narrate.returned_items");
    }

    @SubscribeEvent public static void stop(ServerStoppingEvent event) {
        CollectionArena.reset(event.getServer());
        SylarStory.reset();
        SylarVoice.clear();
        SWINGS.clear();
    }
}
