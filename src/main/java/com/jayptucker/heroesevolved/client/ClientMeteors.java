package com.jayptucker.heroesevolved.client;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.meteor.MeteorGeometry;
import com.jayptucker.heroesevolved.network.MeteorPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Meteors as this client knows them ({@link MeteorPayload}, every five ticks), run forward between packets on the
 * shared path ({@link MeteorGeometry#position}), and whether stars are falling tonight. Drawn by {@code MeteorArt}.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class ClientMeteors {
    static final int STALE = 40;

    /** One meteor on this client's clock. */
    public static final class View {
        public final int id;
        public boolean great, held;
        public Vec3 start = Vec3.ZERO, land = Vec3.ZERO;
        Vec3 goal = Vec3.ZERO;
        public float elapsed, cracked, braced;
        public int fall = 1;
        public int[] helpers = new int[0], kinds = new int[0];
        long heard;
        View(int id) { this.id = id; }

        /** How far down it is between ticks. */
        public float fallen(float partial) { return Mth.clamp((elapsed + (held ? 0 : partial)) / fall, 0, 1); }
        /** Where it is in the sky between ticks. */
        public Vec3 sky(float partial) { return MeteorGeometry.position(start, land, fallen(partial)); }
        /** Where it was {@code back} of its fall ago (for its trail). */
        public Vec3 behind(float partial, float back) { return MeteorGeometry.position(start, land, Math.max(0, fallen(partial) - back)); }
        public float rock() { return MeteorGeometry.rock(great); }
        public float blast() { return MeteorGeometry.blast(great); }
    }

    private static final Map<Integer, View> VIEWS = new HashMap<>();
    private static ClientLevel world;
    private static boolean starfall;
    private static long starfallSince;

    private ClientMeteors() {}

    public static Collection<View> views() { return VIEWS.values(); }

    public static boolean any() { return !VIEWS.isEmpty() || starfall; }

    /** Whether stars are falling tonight here, and since when. */
    public static boolean starfall() { return starfall; }
    public static long starfallSince() { return starfallSince; }

    public static void starfall(boolean on) {
        ClientLevel level = Minecraft.getInstance().level;
        if (on && !starfall) starfallSince = level == null ? 0 : level.getGameTime();
        starfall = on;
    }

    public static void accept(MeteorPayload p) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !Double.isFinite(p.start().lengthSqr()) || !Double.isFinite(p.land().lengthSqr())) return;
        if (world != level) { VIEWS.clear(); world = level; }
        if (p.ended()) { VIEWS.remove(p.id()); return; }
        View v = VIEWS.get(p.id());
        if (v == null) {
            if (VIEWS.size() >= 64) return;
            v = new View(p.id());
            v.land = p.land();
            VIEWS.put(p.id(), v);
        }
        // Pushed aside, it slides; a jump (a fresh view) snaps.
        if (v.goal == Vec3.ZERO || v.goal.distanceToSqr(p.land()) > 64) v.land = p.land();
        v.goal = p.land();
        v.great = p.great();
        v.start = p.start();
        v.fall = Math.max(1, p.fall());
        if (Math.abs(v.elapsed - p.elapsed()) > 3) v.elapsed = p.elapsed();
        v.held = p.held();
        v.cracked = Mth.clamp(p.cracked(), 0, 1);
        v.braced = Mth.clamp(p.braced(), 0, 1);
        v.helpers = p.helpers();
        v.kinds = p.kinds();
        v.heard = level.getGameTime();
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { VIEWS.clear(); world = null; starfall = false; }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (world != mc.level) { VIEWS.clear(); world = mc.level; }
        if (world == null || mc.isPaused()) return;
        long now = world.getGameTime();
        VIEWS.values().removeIf(v -> now - v.heard > STALE || v.elapsed > v.fall + 20);
        for (View v : VIEWS.values()) {
            if (!v.held) v.elapsed++;
            v.land = v.land.lerp(v.goal, 0.3);
        }
    }
}
