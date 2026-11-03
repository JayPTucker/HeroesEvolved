package com.jayptucker.heroesevolved.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.PlayerModelPart;

/** Skin-only optical projections: no world entity, equipment inventory, nametag or shadow. */
public final class OpticalSkinRenderer {
    private static PlayerModel<AbstractClientPlayer> wide, slim;
    private OpticalSkinRenderer() {}

    public static PlayerModel<AbstractClientPlayer> model(AbstractClientPlayer actor) {
        Minecraft mc = Minecraft.getInstance();
        boolean narrow = actor.getSkin().model() == PlayerSkin.Model.SLIM;
        if (wide == null) wide = new PlayerModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
        if (slim == null) slim = new PlayerModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER_SLIM), true);
        return narrow ? slim : wide;
    }

    /** Caller translates to the projection's feet. Rotation and vanilla model scale are applied here. */
    public static void draw(AbstractClientPlayer actor, PoseStack pose, MultiBufferSource source,
                            float partial, float alpha, int rgb) {
        PlayerModel<AbstractClientPlayer> m = model(actor);
        m.setAllVisible(true);
        m.young = false; m.riding = actor.isPassenger(); m.crouching = actor.isCrouching();
        m.attackTime = actor.getAttackAnim(partial); m.swimAmount = actor.getSwimAmount(partial);
        m.leftArmPose = HumanoidModel.ArmPose.EMPTY; m.rightArmPose = HumanoidModel.ArmPose.EMPTY;
        m.setupAnim(actor, actor.walkAnimation.position(partial), actor.walkAnimation.speed(partial),
                actor.tickCount + partial, Mth.wrapDegrees(actor.yHeadRot - actor.yBodyRot), actor.getXRot());
        m.hat.visible = actor.isModelPartShown(PlayerModelPart.HAT);
        m.jacket.visible = actor.isModelPartShown(PlayerModelPart.JACKET);
        m.leftSleeve.visible = actor.isModelPartShown(PlayerModelPart.LEFT_SLEEVE);
        m.rightSleeve.visible = actor.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE);
        m.leftPants.visible = actor.isModelPartShown(PlayerModelPart.LEFT_PANTS_LEG);
        m.rightPants.visible = actor.isModelPartShown(PlayerModelPart.RIGHT_PANTS_LEG);
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180 - Mth.rotLerp(partial, actor.yBodyRotO, actor.yBodyRot)));
        pose.scale(-.9375F, -.9375F, .9375F);
        pose.translate(0, -1.501, 0);
        m.renderToBuffer(pose, source.getBuffer(RenderType.entityTranslucent(actor.getSkin().texture())),
                0xF000F0, OverlayTexture.NO_OVERLAY,
                ((int) (Math.clamp(alpha, 0, 1) * 255) << 24) | (rgb & 0xFFFFFF));
        pose.popPose();
    }

    public static void clear() { wide = slim = null; }
}
