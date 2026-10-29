package com.jayptucker.heroesevolved.events;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.client.FirstPersonArmGeometry;
import com.jayptucker.heroesevolved.client.CarryVisuals;
import com.jayptucker.heroesevolved.client.effects.RadiationVisuals;
import com.jayptucker.heroesevolved.network.ClientArmPoseState;
import com.jayptucker.heroesevolved.render.RadiationGlowLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;

/**
 * Draws power poses directly in camera space, without vanilla's idle-hand
 * rotations or the third-person arm pivot. Two-handed poses draw both arms
 * during the main-hand pass, including when vanilla skips an empty offhand.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class FirstPersonArmEvents {
    private FirstPersonArmEvents() {
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        AbstractClientPlayer player = minecraft.player;
        if (player == null || player.isInvisible()) {
            return;
        }

        ArmPose pose = ClientArmPoseState.get(player.getId());
        if (pose == null || (pose == ArmPose.TWO_HANDS && CarryVisuals.isCarrying(player))) {
            // Carrying leaves the normal low hands/items available; cargo is beside the shoulder.
            return;
        }

        boolean mainHand = event.getHand() == InteractionHand.MAIN_HAND;
        boolean oneHanded = pose == ArmPose.ONE_HAND || pose == ArmPose.TEMPLE;
        if (oneHanded && !mainHand) {
            return;
        }
        if (oneHanded && !event.getItemStack().isEmpty()) return;

        HumanoidArm side = mainHand ? player.getMainArm() : player.getMainArm().getOpposite();
        event.setCanceled(true);
        if (!oneHanded) {
            // Render both hands exactly once, even while an item is held. Vanilla's
            // per-hand item transforms otherwise rotate the arms across each other.
            if (!mainHand) return;
            renderArm(event.getPoseStack(), event, player, HumanoidArm.RIGHT, pose);
            renderArm(event.getPoseStack(), event, player, HumanoidArm.LEFT, pose);
            return;
        }
        renderArm(event.getPoseStack(), event, player, side, pose);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientArmPoseState.clear();
    }

    private static void renderArm(
            PoseStack poseStack,
            RenderHandEvent event,
            AbstractClientPlayer player,
            HumanoidArm side,
            ArmPose pose
    ) {
        PlayerRenderer renderer = (PlayerRenderer) Minecraft.getInstance()
                .getEntityRenderDispatcher().getRenderer(player);
        var arm = side == HumanoidArm.RIGHT ? renderer.getModel().rightArm : renderer.getModel().leftArm;
        var sleeve = side == HumanoidArm.RIGHT ? renderer.getModel().rightSleeve : renderer.getModel().leftSleeve;
        var oldArm = arm.storePose();
        var oldSleeve = sleeve.storePose();
        boolean showSleeve = player.isModelPartShown(side == HumanoidArm.RIGHT
                ? PlayerModelPart.RIGHT_SLEEVE : PlayerModelPart.LEFT_SLEEVE);
        poseStack.pushPose();
        try {
            FirstPersonArmGeometry.pose(poseStack, side, pose, player.tickCount + event.getPartialTick(),
                    ClientArmPoseState.ageTicks(player.getId()) + event.getPartialTick());
            arm.setPos(0, 0, 0);
            arm.setRotation(0, 0, 0);
            sleeve.copyFrom(arm);
            var buffer = event.getMultiBufferSource().getBuffer(RenderType.entityTranslucent(player.getSkin().texture()));
            arm.render(poseStack, buffer, event.getPackedLight(), OverlayTexture.NO_OVERLAY);
            if (showSleeve) sleeve.render(poseStack, buffer, event.getPackedLight(), OverlayTexture.NO_OVERLAY);
            float heat = RadiationVisuals.heat(player);
            if (heat > 0) {
                var glow = event.getMultiBufferSource().getBuffer(RenderType.eyes(RadiationGlowLayer.TEXTURE));
                int color = RadiationGlowLayer.color(heat, player.tickCount + event.getPartialTick());
                arm.render(poseStack, glow, 0xF000F0, OverlayTexture.NO_OVERLAY, color);
                if (showSleeve) sleeve.render(poseStack, glow, 0xF000F0, OverlayTexture.NO_OVERLAY, color);
            }
        } finally {
            arm.loadPose(oldArm);
            sleeve.loadPose(oldSleeve);
            poseStack.popPose();
        }
    }
}
