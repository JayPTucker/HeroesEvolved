package com.jayptucker.heroesevolved.client;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.network.DistressPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Distress flares burning over villages in trouble ({@link DistressPayload}), drawn every frame by
 * {@code VillageArt.flares} however far off they are. The server re-sends each every five seconds; one not heard of
 * for fifteen has gone.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class ClientDistress {
    /** One flare: where, which kind of trouble (its colour), when it was first and last heard of. */
    public record Flare(UUID id, Vec3 at, int kind, long since, long heard) {}

    private static final Map<UUID, Flare> FLARES = new HashMap<>();
    private static ClientLevel world;
    static final int STALE = 300;

    private ClientDistress() {}

    public static void accept(DistressPayload p) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        if (!p.live()) { FLARES.remove(p.id()); return; }
        long now = level.getGameTime();
        Flare old = FLARES.get(p.id());
        FLARES.put(p.id(), new Flare(p.id(), p.at(), p.kind(), old != null ? old.since() : now, now));
    }

    public static Iterable<Flare> all() { return FLARES.values(); }

    public static boolean any() { return !FLARES.isEmpty(); }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != world) { FLARES.clear(); world = level; }
        if (level != null) FLARES.values().removeIf(f -> level.getGameTime() - f.heard() > STALE);
    }
}
