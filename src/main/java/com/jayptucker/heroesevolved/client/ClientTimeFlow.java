package com.jayptucker.heroesevolved.client;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.network.TimeFlowPayload;
import com.jayptucker.heroesevolved.time.TimeFlow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This client's picture of {@link TimeFlow}: how fast the world runs at any point (1, a fraction, or 0 when stopped) and
 * who is moving through it faster than it. Particles, rain, lit TNT and every slowed-time effect read it.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class ClientTimeFlow {
    /** A lost refresh must never leave the world slowed for good. */
    private static final int STALE = 60;

    private static List<TimeFlowPayload.Field> fields = List.of();
    private static boolean everywhere;
    private static final Map<Integer, Integer> MOVERS = new HashMap<>();
    private static long received;
    private static ClientLevel world;
    /** The rain's own clock, which runs at the camera's rate, and how far its last tick took it. */
    private static double weather, weatherStep = 1, rainDue;
    private static final Map<Integer, Double> FUSES = new HashMap<>();

    private ClientTimeFlow() {}

    public static void accept(TimeFlowPayload payload) {
        fields = payload.fields();
        everywhere = payload.everywhere();
        MOVERS.clear();
        for (TimeFlowPayload.Mover mover : payload.movers()) MOVERS.put(mover.entity(), mover.style());
        var level = Minecraft.getInstance().level;
        received = level == null ? 0 : level.getGameTime();
    }

    /** Is time bent anywhere this client knows of? */
    public static boolean active() { return !fields.isEmpty(); }

    /** The world's rate at a point: 1 normal, 1/n slowed, 0 stopped. */
    public static float rate(double x, double y, double z) {
        if (fields.isEmpty()) return 1;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return 1;
        float rate = 1;
        for (TimeFlowPayload.Field field : fields) {
            if (!everywhere) {
                if (!field.dimension().equals(level.dimension().location())) continue;
                Vec3 center = center(level, field);
                double dx = x - center.x, dy = y - center.y, dz = z - center.z;
                if (dx * dx + dy * dy + dz * dz > field.radius() * field.radius()) continue;
            }
            rate = Math.min(rate, field.interval() <= 0 ? 0 : 1F / field.interval());
        }
        return rate;
    }

    public static float rate(Vec3 at) { return rate(at.x, at.y, at.z); }

    /** A slowed or stopped field's centre: its owner's, for one that follows them, when this client can see the owner. */
    private static Vec3 center(ClientLevel level, TimeFlowPayload.Field field) {
        if (field.follows() && level.getEntity(field.owner()) instanceof Entity owner) return owner.position();
        return field.center();
    }

    /** How {@code entity} moves through bent time: {@link TimeFlow#NONE}, {@link TimeFlow#SPEED} or {@link TimeFlow#TIME}. */
    public static int style(Entity entity) { return MOVERS.getOrDefault(entity.getId(), TimeFlow.NONE); }

    /** Entity ids of every field's owner (for the dust lifted at the start). */
    public static List<TimeFlowPayload.Field> fields() { return fields; }

    // ------------------------------------------------------------------
    // Rain: its own clock, so drops slow (and hang, when time stops)
    // ------------------------------------------------------------------

    /** The whole ticks of the rain's clock, in place of the level renderer's. */
    public static int weatherTicks() { return (int) Math.floor(weather); }

    /** The rain's partial tick: where its clock is within the current tick. */
    public static float weatherPartial(float partial) {
        return (float) (weather - Math.floor(weather) + partial * weatherStep);
    }

    /** Whether rain should splash (and patter) this tick: as often as the clock runs, never while it is stopped. */
    public static boolean rainTick() {
        if (rainDue < 1) return false;
        rainDue -= 1;
        return true;
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (world != mc.level) {
            world = mc.level;
            fields = List.of(); MOVERS.clear(); FUSES.clear();
        }
        if (world == null || mc.isPaused()) return;
        if (!fields.isEmpty() && Math.abs(world.getGameTime() - received) > STALE) { fields = List.of(); MOVERS.clear(); }
        var camera = mc.gameRenderer.getMainCamera().getPosition();
        weatherStep = rate(camera.x, camera.y, camera.z);
        weather += weatherStep;
        rainDue = Math.min(2, rainDue + weatherStep);
        if (fields.isEmpty() && !FUSES.isEmpty()) FUSES.clear();
    }

    // ------------------------------------------------------------------
    // Lit TNT: the client's fuse burns at the server's slowed rate, so it never flashes ahead and jumps back
    // ------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.HIGH) public static void fuse(EntityTickEvent.Pre event) {
        if (fields.isEmpty() || !(event.getEntity() instanceof PrimedTnt tnt) || tnt.level() != world) return;
        float rate = rate(tnt.getX(), tnt.getY(), tnt.getZ());
        if (rate >= 1) { FUSES.remove(tnt.getId()); return; }
        if (rate > 0) rate = Math.min(rate, 1F / TimeFlow.FUSE_INTERVAL);
        double due = FUSES.getOrDefault(tnt.getId(), 0D) + rate;
        if (due >= 1) FUSES.put(tnt.getId(), due - 1);
        else { FUSES.put(tnt.getId(), due); event.setCanceled(true); }
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        fields = List.of(); MOVERS.clear(); FUSES.clear(); world = null;
    }
}
