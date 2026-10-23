package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.gui.screen.AdminScreen;
import net.minecraft.client.Minecraft;

import java.util.List;

/** Latest admin-menu data on the client. */
public final class ClientAdminState {
    private static AdminStatePayload latest =
            new AdminStatePayload(false, false, 5, List.of(), List.of());

    private ClientAdminState() {
    }

    public static AdminStatePayload get() {
        return latest;
    }

    public static void update(AdminStatePayload payload) {
        latest = payload;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof AdminScreen screen) {
            screen.onStateUpdated();
        } else if (payload.openScreen()) {
            minecraft.setScreen(new AdminScreen());
        }
    }
}
