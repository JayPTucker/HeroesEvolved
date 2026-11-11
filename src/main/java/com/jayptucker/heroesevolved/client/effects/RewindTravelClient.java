package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.network.RewindTravelPayload;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Trips back through the past as this client sees them. Your own trip moves your view: a camera glides back
 * along the path you took, tick by tick and smooth between ticks, while your controls do nothing and your body
 * (which the server is carrying along the same path) is not drawn. Everyone's trip leaves the rewind's violet
 * trail ({@code RewindArt}).
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class RewindTravelClient {
    /** One stretch of a trip, in one world: stop i is where they are i ticks after it arrived. */
    public static final class Trip {
        public final int entity;
        public final double[] stops;
        public final long received;
        final ClientLevel level;
        Trip(int entity, double[] stops, long received, ClientLevel level) {
            this.entity = entity; this.stops = stops; this.received = received; this.level = level;
        }
        public int count() { return stops.length / 5; }
        /** How far along this stretch it is, in stops (fractional between ticks). */
        public float index(long now, float partial) { return Mth.clamp(now - received + partial, 0, count() - 1); }
        public Vec3 stop(int i) { i = Mth.clamp(i, 0, count() - 1); return new Vec3(stops[i * 5], stops[i * 5 + 1], stops[i * 5 + 2]); }
        public Vec3 at(float index) {
            int i = (int) Math.floor(index);
            return stop(i).lerp(stop(i + 1), index - i);
        }
        float yaw(int i) { return (float) stops[Mth.clamp(i, 0, count() - 1) * 5 + 3]; }
        float pitch(int i) { return (float) stops[Mth.clamp(i, 0, count() - 1) * 5 + 4]; }
        boolean over(long now) { return now - received > count() + 4; }
        /** Is it showing in the world this client is in now? */
        public boolean here() { return level == Minecraft.getInstance().level; }
    }

    private static final Map<Integer, Trip> TRIPS = new HashMap<>();
    /**
     * Client ticks since start: trips run on this, not on a world's clock, because crossing into another world
     * makes a new one that reads 0 until the server sends the time.
     */
    private static long clock;
    private static ArmorStand eye;
    private static CameraType before;

    private RewindTravelClient() {}

    public static Collection<Trip> trips() { return TRIPS.values(); }

    public static long clock() { return clock; }

    public static void receive(RewindTravelPayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || payload.stops().length < 10) return;
        for (double d : payload.stops()) if (!Double.isFinite(d)) return;
        TRIPS.put(payload.entity(), new Trip(payload.entity(), payload.stops(), clock, level));
    }

    /** Is this client's own player being carried back right now? */
    public static boolean travelling() {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return false;
        Trip trip = TRIPS.get(mc.player.getId());
        return trip != null && trip.here() && !trip.over(clock);
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        TRIPS.clear();
        release(Minecraft.getInstance());
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        clock++;
        if (mc.level == null) return;
        long now = clock;
        TRIPS.values().removeIf(trip -> trip.over(now) || trip.level != mc.level && now - trip.received > 100);
        if (!travelling()) { release(mc); return; }
        Trip trip = TRIPS.get(mc.player.getId());
        if (eye == null || eye.level() != mc.level) {
            eye = new ArmorStand(EntityType.ARMOR_STAND, mc.level);
            eye.setInvisible(true);
            eye.setNoGravity(true);
        }
        int i = (int) Math.min(now - trip.received, trip.count() - 1);
        Vec3 was = trip.stop(i - 1), is = trip.stop(i);
        if (i == 0) was = is;
        eye.xo = eye.xOld = was.x; eye.yo = eye.yOld = was.y; eye.zo = eye.zOld = was.z;
        eye.setPos(is.x, is.y, is.z);
        eye.yRotO = trip.yaw(i - 1); eye.setYRot(trip.yaw(i));
        eye.xRotO = trip.pitch(i - 1); eye.setXRot(trip.pitch(i));
        if (mc.getCameraEntity() != eye) {
            if (before == null) before = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.setCameraEntity(eye);
        }
    }

    private static void release(Minecraft mc) {
        if (eye != null && mc.getCameraEntity() == eye && mc.player != null) mc.setCameraEntity(mc.player);
        if (before != null) { mc.options.setCameraType(before); before = null; }
        eye = null;
    }

    /** Your body, which the server is carrying, is not drawn over your own view of the trip. */
    @SubscribeEvent public static void hide(RenderPlayerEvent.Pre event) {
        var mc = Minecraft.getInstance();
        if (event.getEntity() == mc.player && travelling()) event.setCanceled(true);
    }

    @SubscribeEvent public static void hand(RenderHandEvent event) {
        if (travelling()) event.setCanceled(true);
    }

    /** Your controls do nothing while you are carried back. */
    @SubscribeEvent public static void still(MovementInputUpdateEvent event) {
        if (!travelling()) return;
        var input = event.getInput();
        input.leftImpulse = 0; input.forwardImpulse = 0;
        input.up = false; input.down = false; input.left = false; input.right = false;
        input.jumping = false; input.shiftKeyDown = false;
    }
}
