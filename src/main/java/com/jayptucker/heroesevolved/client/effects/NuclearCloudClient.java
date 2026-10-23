package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.network.NuclearCloudPayload;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.ParticleStatus;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import java.util.Comparator;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class NuclearCloudClient {
    public static final int LIFETIME = 600;
    private record Cloud(Vec3 center, float scale, int radius, long began) {}
    private static final Map<UUID, Cloud> CLOUDS = new LinkedHashMap<>();
    private static ClientLevel world;
    private static final ResourceLocation SMOKE = ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID,
            "textures/effects/nuclear_smoke.png");
    private NuclearCloudClient() {}
    public static void update(NuclearCloudPayload packet) {
        ClientLevel level = Minecraft.getInstance().level;
        if (world != level) { CLOUDS.clear(); world = level; }
        if (level == null || !level.dimension().location().equals(packet.dimension())
                || !Double.isFinite(packet.center().lengthSqr()) || !Float.isFinite(packet.scale())
                || packet.age() < 0 || packet.age() >= LIFETIME) return;
        if (!CLOUDS.containsKey(packet.id()) && CLOUDS.size() >= 4) CLOUDS.remove(CLOUDS.keySet().iterator().next());
        Cloud old = CLOUDS.get(packet.id());
        CLOUDS.put(packet.id(), new Cloud(old == null ? packet.center() : old.center, Math.clamp(packet.scale(), .2F, 2),
                Math.clamp(packet.radius(), 1, 64), level.getGameTime() - packet.age()));
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { CLOUDS.clear(); world = null; }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (world != Minecraft.getInstance().level) { CLOUDS.clear(); world = Minecraft.getInstance().level; }
        if (world != null) CLOUDS.values().removeIf(cloud -> world.getGameTime() - cloud.began >= LIFETIME);
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || world == null
                || world != Minecraft.getInstance().level || CLOUDS.isEmpty()) return;
        Vec3 camera = event.getCamera().getPosition();
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, SMOKE);
        try {
            for (Cloud cloud : CLOUDS.values().stream().sorted(Comparator.comparingDouble(
                    (Cloud c) -> camera.distanceToSqr(c.center)).reversed()).toList()) {
                float age = world.getGameTime() - cloud.began + event.getPartialTick().getGameTimeDeltaPartialTick(false);
                if (age < 0 || age >= LIFETIME || camera.distanceToSqr(cloud.center) > 512 * 512) continue;
                if (!event.getFrustum().isVisible(new AABB(cloud.center.add(-64 * cloud.scale, -32 * cloud.scale, -64 * cloud.scale),
                        cloud.center.add(64 * cloud.scale, 110 * cloud.scale, 64 * cloud.scale)))) continue;
                // AFTER_PARTICLES already has the camera view in RenderSystem's shader matrix.
                // Vertices need only world-minus-camera translation, not a second view rotation.
                var transform = DetonationCloudGeometry.worldTransform(cloud.center.add(0, 1.5, 0), camera, cloud.scale);
                float opacity = Mth.clamp((LIFETIME - age) / 100, 0, 1);
                RenderSystem.depthMask(false);
                double distance = camera.distanceTo(cloud.center);
                int detail = distance < 96 ? 2 : distance < 220 ? 1 : 0;
                if (Minecraft.getInstance().options.particles().get() == ParticleStatus.MINIMAL) detail = Math.max(0, detail - 1);
                BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                DetonationCloudGeometry.draw(buffer, transform, event.getCamera().rotation(),
                        DetonationCloudGeometry.puffs(age / 20, opacity, detail), camera.subtract(cloud.center.add(0, 1.5, 0)).scale(1 / cloud.scale));
                BufferUploader.drawWithShader(buffer.buildOrThrow());
            }
        } finally { RenderSystem.depthMask(true); RenderSystem.enableCull(); RenderSystem.disableBlend(); }
    }
}
