package com.jayptucker.heroesevolved.events;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.sounds.ModSounds;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class PlayerOnboardingEvents {
    private static final int ONBOARDING_DELAY_TICKS = 20 * 5;

    private static final ResourceLocation NEW_BEGINNING_ADVANCEMENT =
            ResourceLocation.fromNamespaceAndPath(
                    HeroesEvolved.MOD_ID,
                    "new_beginning"
            );

    private static final Map<UUID, Long> PENDING_ONBOARDING = new HashMap<>();

    private PlayerOnboardingEvents() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        long dueGameTime = server.overworld().getGameTime() + ONBOARDING_DELAY_TICKS;
        PENDING_ONBOARDING.putIfAbsent(player.getUUID(), dueGameTime);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PENDING_ONBOARDING.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long gameTime = server.overworld().getGameTime();

        Iterator<Map.Entry<UUID, Long>> iterator =
                PENDING_ONBOARDING.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> pendingEntry = iterator.next();

            if (gameTime < pendingEntry.getValue()) {
                continue;
            }

            iterator.remove();

            ServerPlayer player = server.getPlayerList().getPlayer(pendingEntry.getKey());
            if (player != null) {
                awardNewBeginning(server, player);
            }
        }
    }

    private static void awardNewBeginning(
            MinecraftServer server,
            ServerPlayer player
    ) {
        AdvancementHolder advancement =
                server.getAdvancements().get(NEW_BEGINNING_ADVANCEMENT);

        if (advancement == null) {
            HeroesEvolved.LOGGER.error(
                    "Could not find onboarding advancement {}.",
                    NEW_BEGINNING_ADVANCEMENT
            );
            return;
        }

        if (player.getAdvancements().getOrStartProgress(advancement).isDone()) {
            return;
        }

        if (player.getAdvancements().award(advancement, "granted")) {
            player.playNotifySound(
                    ModSounds.NEW_BEGINNING.get(),
                    SoundSource.MUSIC,
                    0.35F,
                    1.0F
            );
        }
    }
}