package com.jayptucker.heroesevolved.entity.client;

import com.jayptucker.heroesevolved.wither.RiftSkull;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** The Wither's burning skull: its own charred skin, eyes and mouth lit, half again the size of a vanilla one. */
public final class RiftSkullRenderer extends EntityRenderer<RiftSkull> {
    private static final ResourceLocation SKIN = ResourceLocation.fromNamespaceAndPath("heroesevolved", "textures/entity/rift_skull.png"),
            GLOW = ResourceLocation.fromNamespaceAndPath("heroesevolved", "textures/entity/rift_skull_glow.png");
    private final ModelPart head;

    public RiftSkullRenderer(EntityRendererProvider.Context context) {
        super(context);
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4, -8, -4, 8, 8, 8), PartPose.ZERO);
        head = LayerDefinition.create(mesh, 64, 32).bakeRoot().getChild("head");
    }
    @Override protected int getBlockLightLevel(RiftSkull entity, BlockPos pos) { return 15; }
    @Override public void render(RiftSkull e, float yaw, float partial, PoseStack poses, MultiBufferSource buffers, int light) {
        poses.pushPose();
        poses.scale(-1.6F, -1.6F, 1.6F);
        poses.translate(0, -.25, 0);
        head.yRot = Mth.rotLerp(partial, e.yRotO, e.getYRot()) * Mth.DEG_TO_RAD;
        head.xRot = Mth.lerp(partial, e.xRotO, e.getXRot()) * Mth.DEG_TO_RAD;
        head.render(poses, buffers.getBuffer(RenderType.entityCutoutNoCull(SKIN)), light, OverlayTexture.NO_OVERLAY);
        head.render(poses, buffers.getBuffer(RenderType.eyes(GLOW)), 15728880, OverlayTexture.NO_OVERLAY, 0xFFB8FFE8);
        poses.popPose();
        super.render(e, yaw, partial, poses, buffers, light);
    }
    @Override public ResourceLocation getTextureLocation(RiftSkull entity) { return SKIN; }
}
