package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.network.BlizzardStatePayload;
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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Dimension-local whiteout. Graphics preferences and other dimensions are never changed. */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class BlizzardFog {
    public record Storm(Vec3 center, float radius, long expires) {}
    static java.util.List<Storm> storms() { return java.util.List.copyOf(STORMS.values()); }
    private static final Map<UUID, Storm> STORMS = new HashMap<>();
    private static ClientLevel world;
    private static float strength;
    private BlizzardFog() {}
    public static void update(BlizzardStatePayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != world) { clear(); world = level; }
        if (level == null) return;
        if (payload.radius() <= 0 || payload.ticks() <= 0) { STORMS.remove(payload.owner()); return; }
        if (!Float.isFinite(payload.radius()) || !Double.isFinite(payload.center().lengthSqr())) return;
        if (STORMS.size() >= 64 && !STORMS.containsKey(payload.owner())) return;
        STORMS.put(payload.owner(), new Storm(payload.center(), Math.clamp(payload.radius(), 1, 32),
                level.getGameTime() + Math.clamp(payload.ticks(), 1, 40)));
    }
    private static void clear() { STORMS.clear(); world = null; strength = 0; }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != world || mc.player == null) { clear(); world = mc.level; return; }
        STORMS.values().removeIf(storm -> mc.level.getGameTime() >= storm.expires);
        float desired = 0;
        for (Storm storm : STORMS.values()) {
            Vec3 delta = mc.player.position().subtract(storm.center);
            double above = Math.max(0, delta.y);
            float inside = delta.y < -6 ? 0 : (float) ((storm.radius
                    - Math.sqrt(delta.x * delta.x + delta.z * delta.z + above * above)) / 1.5);
            desired = Math.max(desired, Mth.clamp(inside, 0, 1));
        }
        strength = Mth.lerp(0.35F, strength, desired * com.jayptucker.heroesevolved.config.ClientVisualSettings.FOG.get().floatValue());
        if (strength < 0.005F) strength = 0;
    }
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void fog(ViewportEvent.RenderFog event) {
        if (strength <= 0 || event.getType() != FogType.NONE) return;
        float far = Math.min(event.getFarPlaneDistance(), Mth.lerp(strength, event.getFarPlaneDistance(), 8.0F));
        event.setFarPlaneDistance(far);
        event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), Mth.lerp(strength, event.getNearPlaneDistance(), 0.6F)));
        event.setCanceled(true);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void color(ViewportEvent.ComputeFogColor event) {
        if (strength <= 0 || event.getCamera().getFluidInCamera() != FogType.NONE) return;
        float blend = strength * 0.92F;
        event.setRed(Mth.lerp(blend, event.getRed(), 0.80F));
        event.setGreen(Mth.lerp(blend, event.getGreen(), 0.91F));
        event.setBlue(Mth.lerp(blend, event.getBlue(), 0.97F));
    }
}
