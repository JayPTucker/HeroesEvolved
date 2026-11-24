package com.jayptucker.heroesevolved.entity.client;

import com.jayptucker.heroesevolved.wither.RiftWitherEntity;
import com.jayptucker.heroesevolved.wither.WitherGeometry;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Charred bone over a hungry star. The eyes and the star burn in the phase's colour (mint, then violet, then ember); the
 * fractures through every bone are faint at first, bright once the vessel breaks, and white-hot at critical mass.
 */
public final class RiftWitherRenderer extends MobRenderer<RiftWitherEntity, RiftWitherModel> {
    private static final ResourceLocation SKIN = texture("rift_wither"), EYES = texture("rift_wither_glow"), CRACKS = texture("rift_wither_cracks");

    public RiftWitherRenderer(EntityRendererProvider.Context context) {
        super(context, new RiftWitherModel(), 3.2F);
        addLayer(new RenderLayer<>(this) {
            @Override public void render(PoseStack poses, MultiBufferSource buffers, int light, RiftWitherEntity e, float walk, float amount, float partial, float age, float yaw, float pitch) {
                if (e.deathTime > 0) return;
                float flare = e.dying() ? 1 : e.exposed() ? .75F + .25F * Mth.sin(age * .5F) : 1;
                getParentModel().renderToBuffer(poses, buffers.getBuffer(RenderType.eyes(EYES)), 15728880, OverlayTexture.NO_OVERLAY, tint(phaseColour(e), flare));
                float cracks = e.dying() ? 1 : e.phase() == 1 ? .18F : e.phase() == 2 ? .65F : 1;
                if (e.exposed()) cracks = Math.min(1, cracks + .35F + .15F * Mth.sin(age * .7F));
                getParentModel().renderToBuffer(poses, buffers.getBuffer(RenderType.eyes(CRACKS)), 15728880, OverlayTexture.NO_OVERLAY, tint(phaseColour(e), cracks));
            }
        });
    }
    private static ResourceLocation texture(String name) { return ResourceLocation.fromNamespaceAndPath("heroesevolved", "textures/entity/" + name + ".png"); }
    static int phaseColour(RiftWitherEntity e) { return e.phase() == 3 || e.dying() ? 0xFF9E80 : e.phase() == 2 ? 0xC2AEFF : 0x9EFFE1; }
    private static int tint(int rgb, float k) {
        int r = (int) (((rgb >> 16) & 255) * k), g = (int) (((rgb >> 8) & 255) * k), b = (int) ((rgb & 255) * k);
        return 0xFF000000 | Mth.clamp(r, 0, 255) << 16 | Mth.clamp(g, 0, 255) << 8 | Mth.clamp(b, 0, 255);
    }
    @Override public ResourceLocation getTextureLocation(RiftWitherEntity entity) { return SKIN; }
    @Override public void render(RiftWitherEntity e, float yaw, float partial, PoseStack poses, MultiBufferSource buffers, int light) {
        if (e.deathTime > 0) return;
        super.render(e, yaw, partial, poses, buffers, light);
    }
    @Override protected float getFlipDegrees(RiftWitherEntity entity) { return 0; }
    @Override protected boolean shouldShowName(RiftWitherEntity entity) { return false; }
    @Override protected void scale(RiftWitherEntity e, PoseStack poses, float partial) {
        float size;
        if (e.mode() == 1) {
            float t = e.age() + partial;
            size = WitherGeometry.INTRO_SCALE * (.25F + .75F * ease(0, 28, t)) * (1 - .97F * ease(104, 120, t));
        } else {
            size = e.modelScale();
            if (e.fighting() && e.tickCount < 30) size *= .15F + .85F * ease(0, 30, e.tickCount + partial);
        }
        poses.scale(size, size, size);
    }
    private static float ease(float a, float b, float x) { float t = Mth.clamp((x - a) / (b - a), 0, 1); return t * t * (3 - 2 * t); }
}
