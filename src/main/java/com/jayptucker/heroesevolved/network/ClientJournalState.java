package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.gui.screen.JournalScreen;
import net.minecraft.client.Minecraft;

/** The latest journal contents received from the server. */
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
}
