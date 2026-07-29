package com.jayptucker.heroesevolved.render;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.network.ClientEclipseState;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** Renders a dark square directly over the vanilla Overworld sun. */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class EclipseSkyRenderer {

        private static final float ECLIPSE_HALF_SIZE = 7.0F;

    private EclipseSkyRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();

        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY
                || !ClientEclipseState.isEclipseActive()
                || minecraft.level == null
                || !minecraft.level.dimension().equals(Level.OVERWORLD)) {
            return;
        }

        // Match the rotation Minecraft uses for its sun, then draw a slightly
        // smaller opaque square over it. This leaves the moon and stars alone.
        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(event.getModelViewMatrix());
        poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(
                minecraft.level.getTimeOfDay(
                        event.getPartialTick().getGameTimeDeltaPartialTick(false)
                ) * 360.0F
        ));

        Matrix4f matrix = poseStack.last().pose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        BufferBuilder bufferBuilder = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_COLOR
        );
        bufferBuilder.addVertex(
                        matrix,
                        -ECLIPSE_HALF_SIZE,
                        100.0F,
                        -ECLIPSE_HALF_SIZE
                )
                .setColor(0.0F, 0.0F, 0.0F, 0.90F);
        bufferBuilder.addVertex(
                        matrix,
                        ECLIPSE_HALF_SIZE,
                        100.0F,
                        -ECLIPSE_HALF_SIZE
                )
                .setColor(0.0F, 0.0F, 0.0F, 0.90F);
        bufferBuilder.addVertex(
                        matrix,
                        ECLIPSE_HALF_SIZE,
                        100.0F,
                        ECLIPSE_HALF_SIZE
                )
                .setColor(0.0F, 0.0F, 0.0F, 0.90F);
        bufferBuilder.addVertex(
                        matrix,
                        -ECLIPSE_HALF_SIZE,
                        100.0F,
                        ECLIPSE_HALF_SIZE
                )
                .setColor(0.0F, 0.0F, 0.0F, 0.90F);

        BufferUploader.drawWithShader(bufferBuilder.buildOrThrow());
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }
}
