package com.jayptucker.heroesevolved.render;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.client.effects.RadiationVisuals;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Emissive orange heat follows the animated skin model, for both wide and slim arms. */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class RadiationGlowLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID,
            "textures/entity/radiation_heat.png");
    public RadiationGlowLayer(PlayerRenderer renderer) { super(renderer); }
    @SubscribeEvent public static void layers(EntityRenderersEvent.AddLayers event) {
        for (var skin : event.getSkins()) {
            PlayerRenderer renderer = event.getSkin(skin);
            if (renderer != null) renderer.addLayer(new RadiationGlowLayer(renderer));
        }
    }
    public static int color(float heat, float age) {
        float pulse = 0.92F + 0.08F * (float) Math.sin(age * 0.24F);
        float intensity = Math.clamp(heat * pulse, 0, 1);
        // Eyes use additive blending: scale RGB as well, rather than relying on alpha.
        return 0xFF000000 | (int) (255 * intensity) << 16 | (int) (120 * intensity) << 8 | (int) (28 * intensity);
    }
    @Override public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                                 float swing, float amount, float partial, float age, float yaw, float pitch) {
        float heat = RadiationVisuals.heat(player);
        if (heat <= 0 || player.isInvisible()) return;
        getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(TEXTURE)), 0xF000F0,
                OverlayTexture.NO_OVERLAY, color(heat, age));
    }
}
