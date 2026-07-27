package com.jayptucker.heroesevolved.network;

import java.util.List;

public final class ClientPowerState {
    private static volatile PlayerPowerSyncPayload latestSnapshot =
        new PlayerPowerSyncPayload(0, 0, List.of(), List.of());

    private ClientPowerState() {
    }

    public static PlayerPowerSyncPayload getSnapshot() {
        return latestSnapshot;
    }

    public static void update(PlayerPowerSyncPayload snapshot) {
        latestSnapshot = snapshot;
    }
}