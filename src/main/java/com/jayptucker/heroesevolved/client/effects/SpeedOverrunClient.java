package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.network.OverrunPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * What this client knows of speedsters close to outrunning the last minute: how strong their wake is or how
 * long their breach has been open, and the path they have just run (the wake streams along it). Drawn by
 * {@code SpeedArt}.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class SpeedOverrunClient {
    /** Ticks of path kept for the wake to stream along. */
    public static final int PATH = 24;

    public static final class State {
        public final int entity;
        public int stage, value;
        /** When the last word arrived; how strong the wake looks now (eased toward the charge); when the breach opened. */
        public long heard, opened = Long.MIN_VALUE;
        public float shown, shownOld;
        public final Deque<Vec3> path = new ArrayDeque<>();
        State(int entity) { this.entity = entity; }

        /** Wake strength, 0 to 1, between ticks. */
        public float wake(float partial) { return shownOld + (shown - shownOld) * partial; }
        /** Ticks the breach has been open, between ticks, or -1. */
        public float breach(long now, float partial) { return stage == OverrunPayload.BREACH ? now - opened + partial : -1; }
    }

    private static final Map<Integer, State> STATES = new HashMap<>();
    private static ClientLevel world;

    private SpeedOverrunClient() {}

    public static Collection<State> states() { return STATES.values(); }

    public static void update(OverrunPayload p) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        if (world != level) { STATES.clear(); world = level; }
        long now = level.getGameTime();
        State s = STATES.computeIfAbsent(p.entity(), State::new);
        if (p.stage() == OverrunPayload.BREACH && s.stage != OverrunPayload.BREACH) s.opened = now - p.value();
        s.stage = p.stage();
        s.value = p.value();
        s.heard = now;
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { STATES.clear(); world = null; }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (world != mc.level) { STATES.clear(); world = mc.level; }
        if (world == null || mc.isPaused()) return;
        long now = world.getGameTime();
        for (State s : STATES.values()) {
            // A breach not heard of for three seconds has closed.
            if (s.stage != OverrunPayload.NONE && now - s.heard > 60) s.stage = OverrunPayload.NONE;
            float goal = s.stage == OverrunPayload.BREACH ? 1 : s.stage == OverrunPayload.WAKE ? s.value / 1000F : 0;
            s.shownOld = s.shown;
            s.shown += (goal - s.shown) * 0.12F;
            Entity e = world.getEntity(s.entity);
            if (e == null || s.stage == OverrunPayload.NONE && s.shown < 0.02F) { s.path.clear(); continue; }
            if (!s.path.isEmpty() && s.path.getLast().distanceToSqr(e.position()) > 25) s.path.clear();
            s.path.addLast(e.position());
            while (s.path.size() > PATH) s.path.removeFirst();
        }
        STATES.values().removeIf(s -> s.stage == OverrunPayload.NONE && s.shown < 0.02F);
    }
}
