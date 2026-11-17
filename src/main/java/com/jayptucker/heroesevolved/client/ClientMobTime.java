package com.jayptucker.heroesevolved.client;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.network.MobTimePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Mob;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.Map;
import java.util.HashMap;

/** A continuous local clock per creature; body ticks and bound effects spend the same fraction of a tick. */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class ClientMobTime {
    private static final class Clock {
        double age, previous, fraction;
        int interval = 1;
        Clock(long now) { age = previous = now; }
    }
    private static int actor = -1;
    public static int actor() { return actor; }
    public static void effect(int entity, Runnable action) {
        int old = actor; actor = entity;
        try { action.run(); } finally { actor = old; }
    }
    private static ClientLevel world;
    private static final Map<Integer, Clock> CLOCKS = new HashMap<>();
    public static void accept(MobTimePayload payload) {
        var level = Minecraft.getInstance().level;
        if (world != level) { CLOCKS.clear(); world = level; }
        if (level != null) CLOCKS.computeIfAbsent(payload.entity(), id -> new Clock(level.getGameTime())).interval = Math.max(0, payload.interval());
    }
    public static double clock(int entity, double fallback) {
        Clock c = CLOCKS.get(entity); return c == null ? fallback : c.age;
    }
    public static double start(int entity, long at) {
        var level = Minecraft.getInstance().level;
        return level == null ? at : clock(entity, level.getGameTime()) + at - level.getGameTime();
    }
    public static double elapsed(int entity, double began, float partial) {
        Clock c = CLOCKS.get(entity);
        return c == null ? Minecraft.getInstance().level.getGameTime() + partial - began
                : c.previous + (c.age - c.previous) * partial - began;
    }
    public static float partial(Mob mob, float fallback) {
        Clock c = CLOCKS.get(mob.getId());
        return c == null || c.interval == 1 ? fallback : (float) (c.fraction + (c.interval == 0 ? 0 : fallback / c.interval));
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Pre event) {
        var level = Minecraft.getInstance().level;
        if (world != level) { CLOCKS.clear(); world = level; }
        if (level == null) return;
        for (Clock c : CLOCKS.values()) { c.previous = c.age; c.age += c.interval == 0 ? 0 : 1D / c.interval; }
        if (level.getGameTime() % 40 == 0) CLOCKS.keySet().removeIf(id -> level.getEntity(id) == null);
    }
    @SubscribeEvent public static void before(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level() != world) return;
        Clock c = CLOCKS.get(mob.getId()); if (c == null || c.interval == 1) return;
        c.fraction += c.interval == 0 ? 0 : 1D / c.interval;
        if (c.fraction < 1 - 1e-6) event.setCanceled(true); else c.fraction = Math.max(0, c.fraction - 1);
    }
}
