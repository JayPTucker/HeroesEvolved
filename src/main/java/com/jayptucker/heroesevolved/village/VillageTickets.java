package com.jayptucker.heroesevolved.village;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps a village loaded and ticking while something is happening there, however far the nearest player is: a village
 * calling for help hundreds of blocks away must still be in trouble when they arrive. A region ticket (not saved with the
 * world) holds the chunks round the bell at entity-ticking level; it comes off when the event ends, and a restart drops it.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class VillageTickets {
    private static final TicketType<ChunkPos> VILLAGE = TicketType.create(HeroesEvolved.MOD_ID + ":village_event", Comparator.comparingLong(ChunkPos::toLong));
    /**
     * The ticket's reach in chunks: everything within {@code DISTANCE - 2} chunks of the bell (5 chunks, 80 blocks: the
     * whole village and its edge) ticks entities.
     */
    static final int DISTANCE = 7;
    /** A spot's reach: the chunk at its middle ticks and the ring round it is loaded (a falling star's landing). */
    public static final int SPOT = 3;

    private record Held(ServerLevel level, ChunkPos at, int distance) {}
    private static final Map<String, Held> HELD = new HashMap<>();

    private VillageTickets() {}

    /** Holds the village round {@code at} loaded under {@code key}; holding the same key again moves it. */
    public static void hold(ServerLevel level, BlockPos at, String key) { hold(level, at, key, DISTANCE); }

    /**
     * Holds the chunks round {@code at} loaded under {@code key}, out to {@code distance} ({@link #DISTANCE} for a whole
     * village, {@link #SPOT} for one place); holding the same key again somewhere else moves it.
     */
    public static void hold(ServerLevel level, BlockPos at, String key, int distance) {
        ChunkPos chunk = new ChunkPos(at);
        Held old = HELD.get(key);
        if (old != null && old.level == level && old.at.equals(chunk) && old.distance == distance) return;
        if (old != null) release(key);
        level.getChunkSource().addRegionTicket(VILLAGE, chunk, distance, chunk);
        HELD.put(key, new Held(level, chunk, distance));
    }

    public static void release(String key) {
        Held held = HELD.remove(key);
        if (held != null && HELD.values().stream().noneMatch(h -> h.level == held.level && h.at.equals(held.at) && h.distance == held.distance))
            held.level.getChunkSource().removeRegionTicket(VILLAGE, held.at, held.distance, held.at);
    }

    /** Releases every key starting with {@code prefix}. */
    public static void releaseAll(String prefix) {
        for (String key : List.copyOf(HELD.keySet())) if (key.startsWith(prefix)) release(key);
    }

    public static boolean held(String key) { return HELD.containsKey(key); }

    /** The chunk a key is holding round, or null (tests). */
    public static ChunkPos at(String key) {
        Held h = HELD.get(key);
        return h == null ? null : h.at;
    }

    @SubscribeEvent
    public static void stop(ServerStoppingEvent event) { HELD.clear(); }
}
