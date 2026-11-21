package com.jayptucker.heroesevolved.client;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.gui.HudGauges;
import com.jayptucker.heroesevolved.network.OverloadPayload;
import com.jayptucker.heroesevolved.village.overload.OverloadGeometry;
import com.jayptucker.heroesevolved.village.overload.Storm;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Overloads' storms as this client knows them ({@link OverloadPayload}, every five ticks), drawn by {@code OverloadArt}.
 * A blizzard's whiteout is fed to {@code BlizzardFog}; a wildfire's smoke hazes the air here; a blizzard shows the COLD
 * ring on the HUD while you are out in it.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class ClientOverloads {
    /** A storm not heard of for this long is over; one that ended fades over this long. */
    static final int STALE = 40;
    public static final int FADE = 30;

    /** One storm on this client's clock. */
    public static final class View {
        public final int entity;
        public Storm storm;
        /** Where the eye is drawn (eased toward where the server last put it); where the drawing aims besides. */
        public Vec3 eye = Vec3.ZERO, previous = Vec3.ZERO, target = Vec3.ZERO;
        Vec3 goal = Vec3.ZERO;
        public float progress, power, calm;
        public int stage;
        /** When its stage began, when it began, when it was last heard of, when it ended (or MAX). */
        public long stageStart, start, heard, end = Long.MAX_VALUE;
        public boolean sees;
        public int[] helpers = new int[0];
        View(int entity) { this.entity = entity; }

        public Vec3 at(float partial) { return previous.lerp(eye, partial); }
        public boolean ended() { return end != Long.MAX_VALUE; }
        /** 1 while it runs, falling to 0 over {@link #FADE} ticks once it has ended. */
        public float fade(long now, float partial) { return ended() ? Mth.clamp(1 - (now - end + partial) / FADE, 0, 1) : 1; }
        public float stageAge(long now, float partial) { return now - stageStart + partial; }
        public float age(long now, float partial) { return now - start + partial; }
        public boolean growing() { return stage == 0; }
        public boolean breaking() { return stage == 1; }
        public boolean burning() { return stage == 2; }
        public boolean dying() { return stage == 3; }

        /** A supercell's size against the shared tornado, as the server works it out ({@code Supercell.tick}). */
        public float scale(long now, float partial) {
            float k = Mth.clamp(stageAge(now, partial) / OverloadGeometry.BREAK_WARNING, 0, 1);
            if (breaking()) return OverloadGeometry.scale(1) + (OverloadGeometry.TOUCHDOWN_SCALE - OverloadGeometry.scale(1)) * k;
            if (burning()) return OverloadGeometry.TOUCHDOWN_SCALE;
            return OverloadGeometry.scale(progress);
        }

        /** How far it reaches now, as the server works it out. */
        public float reach() {
            return switch (storm) {
                case WILDFIRE -> burning() ? OverloadGeometry.FIRESTORM : OverloadGeometry.front(progress);
                case SUPERCELL -> OverloadGeometry.pull(burning() ? 1 : progress);
                case BLIZZARD -> burning() ? OverloadGeometry.reach(1) + 8 : OverloadGeometry.reach(progress);
            };
        }
    }

    private static final Map<Integer, View> VIEWS = new HashMap<>();
    private static ClientLevel world;
    private static float haze;

    private ClientOverloads() {}

    public static Collection<View> views() { return VIEWS.values(); }

    public static boolean any() { return !VIEWS.isEmpty(); }

    public static void accept(OverloadPayload p) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !Double.isFinite(p.eye().lengthSqr()) || !Double.isFinite(p.target().lengthSqr())) return;
        if (world != level) { VIEWS.clear(); world = level; }
        long now = level.getGameTime();
        View v = VIEWS.get(p.entity());
        if (p.ended()) { if (v != null && !v.ended()) v.end = now; return; }
        Storm storm = Storm.byId(p.storm());
        if (storm == null) return;
        if (v == null || v.ended()) {
            if (VIEWS.size() >= 16) return;
            v = new View(p.entity());
            v.eye = v.previous = p.eye();
            VIEWS.put(p.entity(), v);
        }
        if (v.goal == Vec3.ZERO || v.goal.distanceToSqr(p.eye()) > 64) { v.eye = p.eye(); v.previous = p.eye(); }
        v.goal = p.eye();
        v.target = p.target();
        v.storm = storm;
        v.progress = Mth.clamp(p.progress(), 0, 1);
        v.power = Mth.clamp(p.power(), 0, 1);
        v.calm = Mth.clamp(p.calm(), 0, 1);
        if (v.stage != p.stage() || Math.abs(now - p.stageAge() - v.stageStart) > 3) v.stageStart = now - p.stageAge();
        v.stage = p.stage();
        if (Math.abs(now - p.age() - v.start) > 3) v.start = now - p.age();
        v.sees = p.sees();
        v.helpers = p.helpers();
        v.heard = now;
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { VIEWS.clear(); world = null; haze = 0; }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (world != mc.level) { VIEWS.clear(); world = mc.level; }
        if (world == null || mc.player == null) { haze = 0; return; }
        long now = world.getGameTime();
        for (View v : VIEWS.values()) if (!v.ended() && now - v.heard > STALE) v.end = now;
        VIEWS.values().removeIf(v -> v.ended() && now - v.end > FADE + 2);
        float smoke = 0;
        Vec3 me = mc.player.position();
        for (View v : VIEWS.values()) {
            v.previous = v.eye;
            v.eye = v.eye.lerp(v.goal, 0.35);
            float fade = v.fade(now, 0);
            double d = Math.sqrt(me.subtract(v.eye).horizontalDistanceSqr());
            if (v.storm == Storm.BLIZZARD) {
                // The whiteout: thickest at the eye, thinning to nothing at its edge.
                float reach = v.reach(), thick = v.power * fade;
                com.jayptucker.heroesevolved.client.effects.BlizzardFog.whiteout(v.entity, v.eye, reach, thick, now + 10);
            } else if (v.storm == Storm.WILDFIRE) {
                float reach = v.reach() + 14;
                smoke = Math.max(smoke, v.power * fade * (float) (1 - Mth.clamp((d - reach * 0.4) / (reach * 0.6), 0, 1)));
            }
        }
        haze = Mth.lerp(0.2F, haze, smoke * com.jayptucker.heroesevolved.config.ClientVisualSettings.FOG.get().floatValue());
        if (haze < 0.005F) haze = 0;
    }

    /** A wildfire's smoke closes the air in, warm and grey. */
    @SubscribeEvent(priority = EventPriority.LOW, receiveCanceled = true)
    public static void fog(ViewportEvent.RenderFog event) {
        if (haze <= 0 || event.getType() != FogType.NONE) return;
        event.setFarPlaneDistance(Math.min(event.getFarPlaneDistance(), Mth.lerp(haze, event.getFarPlaneDistance(), 36.0F)));
        event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), Mth.lerp(haze, event.getNearPlaneDistance(), 2.0F)));
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void color(ViewportEvent.ComputeFogColor event) {
        if (haze <= 0 || event.getCamera().getFluidInCamera() != FogType.NONE) return;
        float blend = haze * 0.8F;
        event.setRed(Mth.lerp(blend, event.getRed(), 0.62F));
        event.setGreen(Mth.lerp(blend, event.getGreen(), 0.45F));
        event.setBlue(Mth.lerp(blend, event.getBlue(), 0.34F));
    }

    /** Whether a supercell's deck hangs over the camera, so the sky's own clouds are hidden ({@code StormCloudsMixin}). */
    public static boolean hidesClouds() {
        var mc = Minecraft.getInstance();
        if (VIEWS.isEmpty() || mc.gameRenderer == null) return false;
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        double reach = OverloadGeometry.DECK_RADIUS + 96;
        for (View v : VIEWS.values())
            if (v.storm == Storm.SUPERCELL && camera.subtract(v.eye).horizontalDistanceSqr() <= reach * reach) return true;
        return false;
    }

    /** The blizzard this player is out in, or null. */
    public static View blizzardHere() {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        for (View v : VIEWS.values()) {
            if (v.storm != Storm.BLIZZARD || v.ended() || v.dying()) continue;
            float reach = v.reach();
            if (mc.player.position().subtract(v.eye).horizontalDistanceSqr() <= reach * reach) return v;
        }
        return null;
    }

    /** The COLD ring, while a blizzard has hold of you: how frozen you are (vanilla's own frozen ticks). */
    public static void gauges(List<HudGauges.Gauge> gauges) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || blizzardHere() == null) return;
        float cold = Mth.clamp(mc.player.getTicksFrozen() / (float) mc.player.getTicksRequiredToFreeze(), 0, 1);
        boolean frozen = mc.player.isFullyFrozen(), flash = HudGauges.flash();
        int color = frozen ? (flash ? 0xFFF4FAFC : 0xFF7FC8FF) : cold > 0.6F ? 0xFF9FDCFF : 0xFFCFEAF5;
        gauges.add(HudGauges.Gauge.ring(cold, color).middle(Math.round(cold * 100) + "%").label(frozen ? "GET WARM!" : "COLD"));
    }
}
