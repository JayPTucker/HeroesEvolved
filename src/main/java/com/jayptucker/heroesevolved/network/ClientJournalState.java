package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.gui.screen.JournalScreen;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** The latest journal contents received from the server. */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class ClientJournalState {
    private static StoryJournalPayload latest;

    private ClientJournalState() {
    }

    public static StoryJournalPayload get() {
        return latest;
    }

    public static void update(StoryJournalPayload payload) {
        latest = payload;
        if (Minecraft.getInstance().screen instanceof JournalScreen screen) {
            screen.onContentsUpdated();
        }
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        latest = null;
    }
}
