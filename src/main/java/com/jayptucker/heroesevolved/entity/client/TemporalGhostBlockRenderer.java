package com.jayptucker.heroesevolved.entity.client;

import com.jayptucker.heroesevolved.entity.TemporalGhostBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Renders temporal replay blocks with an intentionally ethereal alpha. */
public final class TemporalGhostBlockRenderer
        extends EntityRenderer<TemporalGhostBlockEntity> {
    private static final int GHOST_ALPHA = 105;

    private final net.minecraft.client.renderer.block.BlockRenderDispatcher
            blockRenderer;

    public TemporalGhostBlockRenderer(EntityRendererProvider.Context context) {
        super(context);
        blockRenderer = context.getBlockRenderDispatcher();
        shadowRadius = 0.0F;
    }

    @Override
    public void render(
            TemporalGhostBlockEntity ghost,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight
    ) {
        if (ghost.getBlockState().isAir()) {
            return;
        }

        VertexConsumer translucentBuffer = new AlphaVertexConsumer(
                bufferSource.getBuffer(RenderType.translucent())
        );
        blockRenderer.renderBatched(
                ghost.getBlockState(),
                BlockPos.containing(ghost.position()),
                ghost.level(),
                poseStack,
                translucentBuffer,
                false,
                RandomSource.create(ghost.getId()),
                ModelData.EMPTY,
                RenderType.translucent()
        );
        super.render(ghost, entityYaw, partialTick, poseStack, bufferSource,
                packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(TemporalGhostBlockEntity ghost) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    /** Applies one alpha value without changing the original block colours. */
    private static final class AlphaVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;

        private AlphaVertexConsumer(VertexConsumer delegate) {
            this.delegate = delegate;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            delegate.setColor(red, green, blue,
                    Math.min(alpha, GHOST_ALPHA));
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(x, y, z);
            return this;
        }
    }
}
