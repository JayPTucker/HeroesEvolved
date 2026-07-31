package com.jayptucker.heroesevolved.entity.client;

import com.jayptucker.heroesevolved.entity.CarryAnchorEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;

/** The carrier anchor exists for positioning only and must never be visible. */
public final class CarryAnchorRenderer extends EntityRenderer<CarryAnchorEntity> {
    public CarryAnchorRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(
            CarryAnchorEntity anchor,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight
    ) {
        // Intentionally invisible.
    }

    @Override
    public ResourceLocation getTextureLocation(CarryAnchorEntity anchor) {
        return MissingTextureAtlasSprite.getLocation();
    }
}
