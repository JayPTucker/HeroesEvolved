package com.jayptucker.heroesevolved.client;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * The Regrowth every regenerator in sight carries (from {@code RegrowthPayload}), eased a few points a tick so the
 * veins brighten as it builds and fade as it is spent, never jumping.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class ClientRegrowth {
    private static final Map<Integer, Float> TARGET = new HashMap<>(), SHOWN = new HashMap<>(), PREVIOUS = new HashMap<>();
    private static ClientLevel world;

    private ClientRegrowth() {}

    public static void set(int entity, int regrowth) {
        TARGET.put(entity, (float) Mth.clamp(regrowth, 0, 100));
        SHOWN.putIfAbsent(entity, 0F);
    }

    /** The Regrowth drawn on this entity, 0 to 1, between ticks. */
    public static float shown(int entity, float partial) {
        Float now = SHOWN.get(entity);
        if (now == null) return 0;
        return Mth.lerp(partial, PREVIOUS.getOrDefault(entity, now), now) / 100F;
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != world) { TARGET.clear(); SHOWN.clear(); PREVIOUS.clear(); world = level; }
        if (level == null || SHOWN.isEmpty()) return;
        for (var entry : SHOWN.entrySet()) {
            float target = TARGET.getOrDefault(entry.getKey(), 0F), now = entry.getValue();
            PREVIOUS.put(entry.getKey(), now);
            // Builds a couple of points a tick; a spend drains faster, so Rapid Mending visibly empties the veins.
            entry.setValue(now < target ? Math.min(target, now + 2) : Math.max(target, now - 5));
        }
        SHOWN.entrySet().removeIf(e -> e.getValue() <= 0 && TARGET.getOrDefault(e.getKey(), 0F) <= 0 && level.getEntity(e.getKey()) == null);
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { TARGET.clear(); SHOWN.clear(); PREVIOUS.clear(); }
}
