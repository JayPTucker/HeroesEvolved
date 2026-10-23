package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.cryo.ColdDomeShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.Comparator;

@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class ColdDomeClient {
    private ColdDomeClient() {}
    @SubscribeEvent public static void snow(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused() || mc.level.getGameTime() % 2 != 0) return;
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        var storms = BlizzardFog.storms();
        if (storms.stream().noneMatch(storm -> ColdDomeShape.contains(storm.center(), camera, storm.radius()))) return;
        int count = switch (mc.options.particles().get()) { case ALL -> 16; case DECREASED -> 8; case MINIMAL -> 4; };
        for (int i = 0; i < count; i++) {
            Vec3 point = camera.add((mc.level.random.nextDouble() - .5) * 14,
                    (mc.level.random.nextDouble() - .35) * 9, (mc.level.random.nextDouble() - .5) * 14);
            if (storms.stream().noneMatch(storm -> ColdDomeShape.contains(storm.center(), point, storm.radius()))) continue;
            AbilityEffectRenderer.mote(mc.level, point, new Vec3(.07, -.065, .025), .035F + mc.level.random.nextFloat() * .035F, 24, 0xEFFAFF);
        }
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || mc.level == null) return;
        Vec3 camera = event.getCamera().getPosition();
        var storms = BlizzardFog.storms().stream()
                .filter(storm -> camera.distanceToSqr(storm.center()) <= 160 * 160
                        && event.getFrustum().isVisible(ColdDomeShape.bounds(storm.center(), storm.radius())))
                .sorted(Comparator.comparingDouble(storm -> camera.distanceToSqr(storm.center())))
                .limit(4).sorted(Comparator.comparingDouble((BlizzardFog.Storm storm) -> camera.distanceToSqr(storm.center())).reversed()).toList();
        if (storms.isEmpty()) return;
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.disableCull(); RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        try {
            BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (var storm : storms) {
                float remaining = storm.expires() - mc.level.getGameTime();
                float fade = Math.clamp(remaining / 6, 0, 1);
                ColdDomeGeometry.draw(buffer, DetonationCloudGeometry.worldTransform(storm.center(), camera, 1),
                        storm.radius(), mc.level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false), fade,
                        mc.options.particles().get() == ParticleStatus.ALL && camera.distanceToSqr(storm.center()) < 64 * 64);
            }
            BufferUploader.drawWithShader(buffer.buildOrThrow());
        } finally { RenderSystem.depthMask(true); RenderSystem.enableCull(); RenderSystem.disableBlend(); }
    }
}
