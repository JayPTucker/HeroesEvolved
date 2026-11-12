package com.jayptucker.heroesevolved.client;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.network.TemporalProjectilePayload;
import com.jayptucker.heroesevolved.time.ProjectileTimeService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Mirrors server-owned motion, including TACZ rounds that do not track velocity by default. */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class ClientProjectileTime {
    private record State(TemporalProjectilePayload payload, long received, Projectile entity) {}
    private static final Map<Integer, State> SHOTS = new HashMap<>();
    private static ClientLevel level;
    private static boolean catchEnabled;

    private ClientProjectileTime() {}

    public static void setCatchEnabled(boolean enabled) { catchEnabled = enabled; }
    public static boolean canCatch() { return catchEnabled; }
    public static boolean controls(Projectile shot) { return shot.level() == level && SHOTS.containsKey(shot.getId()); }

    public static void apply(TemporalProjectilePayload payload) {
        var world = Minecraft.getInstance().level;
        if (world == null) return;
        if (world != level) { SHOTS.clear(); level = world; }
        Projectile shot = world.getEntity(payload.entityId()) instanceof Projectile projectile ? projectile : null;
        if (shot != null) applyMotion(shot, payload);
        if (payload.interval() == ProjectileTimeService.NORMAL) SHOTS.remove(payload.entityId());
        else SHOTS.put(payload.entityId(), new State(payload, world.getGameTime(), shot));
    }

    private static void applyMotion(Projectile shot, TemporalProjectilePayload payload) {
        shot.setPos(payload.position());
        shot.setDeltaMovement(payload.motion());
    }

    public static boolean hasControlledShots() { return !SHOTS.isEmpty(); }

    public static void forEachShot(Consumer<Projectile> action) {
        for (State state : SHOTS.values())
            if (state.entity != null && !state.entity.isRemoved()) action.accept(state.entity);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void beforeTick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof Projectile shot) || shot.level() != level) return;
        State state = SHOTS.get(shot.getId());
        if (state == null) return;
        if (state.entity == null) {
            applyMotion(shot, state.payload);
            SHOTS.put(shot.getId(), new State(state.payload, state.received, shot));
        }
        shot.setDeltaMovement(state.payload.motion());
        if (ProjectileTimeService.isTaczBullet(shot)) shot.tickCount = Math.max(0, shot.tickCount - 1);
        if (state.payload.interval() == ProjectileTimeService.STOPPED) event.setCanceled(true);
    }

    @SubscribeEvent public static void afterTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Projectile shot) || shot.level() != level) return;
        State state = SHOTS.get(shot.getId());
        if (state != null && ProjectileTimeService.isTaczBullet(shot)) shot.setDeltaMovement(state.payload.motion());
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var world = Minecraft.getInstance().level;
        if (world != level) { SHOTS.clear(); level = world; }
        if (world != null) SHOTS.values().removeIf(state -> state.entity != null ? state.entity.isRemoved()
                : world.getGameTime() - state.received > 20);
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        SHOTS.clear();
        level = null;
        catchEnabled = false;
    }
}
