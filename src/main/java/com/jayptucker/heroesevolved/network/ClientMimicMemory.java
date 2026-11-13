package com.jayptucker.heroesevolved.network;

import java.util.List;

/** The local player's mimic memory, as last sent by the server. */
public final class ClientMimicMemory {
    private static MimicMemoryPayload memory = new MimicMemoryPayload(List.of(), -1, 0, 1);

    private ClientMimicMemory() {}

    public static MimicMemoryPayload get() { return memory; }

    static void set(MimicMemoryPayload payload) { memory = payload; }

    public static void clear() { memory = new MimicMemoryPayload(List.of(), -1, 0, 1); }
}
