package com.jayptucker.heroesevolved.entity.client;

import com.jayptucker.heroesevolved.entity.QuakeDebrisEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Renders a small, tumbling copy of the block a Quake power tore loose. */
public final class QuakeDebrisRenderer extends EntityRenderer<QuakeDebrisEntity> {
    private static final float SCALE = 0.55F;

    private final net.minecraft.client.renderer.block.BlockRenderDispatcher
            blockRenderer;

    public QuakeDebrisRenderer(EntityRendererProvider.Context context) {
        super(context);
        blockRenderer = context.getBlockRenderDispatcher();
        shadowRadius = 0.25F;
    }

    @Override
    public void render(
            QuakeDebrisEntity debris,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight
    ) {
        float age = debris.tickCount + partialTick;
        // Each chunk tumbles on its own axis so a burst of debris never
        // spins in lockstep.
        float spinSpeed = 9.0F + Mth.abs(debris.getId() % 7);

        poseStack.pushPose();
        poseStack.translate(0.0D, SCALE / 2.0D, 0.0D);
        poseStack.mulPose(Axis.YP.rotationDegrees(debris.getId() * 37.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(age * spinSpeed));
        poseStack.mulPose(Axis.ZP.rotationDegrees(age * spinSpeed * 0.6F));
        poseStack.scale(SCALE, SCALE, SCALE);
        poseStack.translate(-0.5D, -0.5D, -0.5D);
        blockRenderer.renderSingleBlock(
                debris.getBlockState(),
                poseStack,
                bufferSource,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                ModelData.EMPTY,
                null
        );
        poseStack.popPose();
        super.render(debris, entityYaw, partialTick, poseStack, bufferSource,
                packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(QuakeDebrisEntity debris) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
