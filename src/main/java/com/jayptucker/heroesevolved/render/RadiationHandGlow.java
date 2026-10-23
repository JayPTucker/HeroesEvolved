package com.jayptucker.heroesevolved.render;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.client.effects.RadiationVisuals;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderArmEvent;

/** Apply the same skin-aligned emission to the player's own first-person hands. */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class RadiationHandGlow {
    private static boolean rendering;
    private RadiationHandGlow() {}
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void arm(RenderArmEvent event) {
        float heat = RadiationVisuals.heat(event.getPlayer());
        if (rendering || heat <= 0 || event.getPlayer().isInvisible()) return;
        var renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(event.getPlayer());
        if (!(renderer instanceof PlayerRenderer playerRenderer)) return;
        event.setCanceled(true);
        rendering = true;
        try {
            // Let vanilla set the exact arm pose and draw the skin before its emissive overlay.
            boolean right = event.getArm() == HumanoidArm.RIGHT;
            if (right) playerRenderer.renderRightHand(event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight(), event.getPlayer());
            else playerRenderer.renderLeftHand(event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight(), event.getPlayer());
            var model = playerRenderer.getModel();
            var buffer = event.getMultiBufferSource().getBuffer(RenderType.eyes(RadiationGlowLayer.TEXTURE));
            int color = RadiationGlowLayer.color(heat, event.getPlayer().tickCount);
            (right ? model.rightArm : model.leftArm).render(event.getPoseStack(), buffer, 0xF000F0, OverlayTexture.NO_OVERLAY, color);
            (right ? model.rightSleeve : model.leftSleeve).render(event.getPoseStack(), buffer, 0xF000F0, OverlayTexture.NO_OVERLAY, color);
        } finally { rendering = false; }
    }
}
