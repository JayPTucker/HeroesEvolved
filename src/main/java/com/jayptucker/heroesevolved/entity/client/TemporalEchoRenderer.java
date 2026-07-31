package com.jayptucker.heroesevolved.entity.client;

import com.jayptucker.heroesevolved.entity.TemporalEchoEntity;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Renders an echo with Minecraft's standard player model and captured skin. */
public final class TemporalEchoRenderer
        extends HumanoidMobRenderer<TemporalEchoEntity, PlayerModel<TemporalEchoEntity>> {
    public TemporalEchoRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(TemporalEchoEntity echo) {
        GameProfile profile = new GameProfile(
                echo.getProfileId(),
                echo.getProfileName()
        );
        return Minecraft.getInstance().getSkinManager()
                .getInsecureSkin(profile)
                .texture();
    }
}
