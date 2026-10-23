package com.jayptucker.heroesevolved.events;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.network.ClientArmPoseState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;

/**
 * In first person, empty hands are raised and thrust forward while a power
 * pose is active. Two-handed poses also show the off arm, which vanilla
 * never draws when it is empty.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class FirstPersonArmEvents {
    private FirstPersonArmEvents() {
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        AbstractClientPlayer player = minecraft.player;
        if (player == null || player.isInvisible()
                || !event.getItemStack().isEmpty()) {
            return;
        }

        ArmPose pose = ClientArmPoseState.get(player.getId());
        if (pose == null) {
            return;
        }

        boolean mainHand = event.getHand() == InteractionHand.MAIN_HAND;
        boolean oneHanded = pose == ArmPose.ONE_HAND || pose == ArmPose.TEMPLE;
        if (oneHanded && !mainHand) {
            return;
        }

        HumanoidArm side = mainHand ? player.getMainArm() : player.getMainArm().getOpposite();
        event.setCanceled(true);
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
        float f = side == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        float time = player.tickCount + event.getPartialTick();
        float tremble = pose == ArmPose.TWO_HANDS_STRAIN
                ? Mth.sin(time * 2.7F) * 0.012F : 0.0F;

        poseStack.pushPose();
        // Vanilla rests an empty arm at (0.64, -0.6, -0.72), turned 45 degrees.
        // Powers pull it inward, raise it and push it out in front.
        switch (pose) {
            case ARMS_RAISED -> poseStack.translate(f * 0.45F, -0.05F, -0.7F);
            case ARMS_WIDE -> poseStack.translate(f * 0.75F, -0.45F, -0.6F);
            case GROUND -> poseStack.translate(f * 0.38F, -0.62F, -0.82F);
            case TEMPLE -> poseStack.translate(f * 0.32F, -0.12F, -0.5F);
            default -> poseStack.translate(f * 0.36F + tremble, -0.36F + tremble, -0.86F);
        }
        poseStack.mulPose(Axis.YP.rotationDegrees(f * (pose == ArmPose.ARMS_WIDE ? 55.0F : 18.0F)));
        if (pose == ArmPose.ARMS_RAISED) {
            poseStack.mulPose(Axis.XP.rotationDegrees(-35.0F));
        }

        // Vanilla's mapping from this hand space onto the arm model.
        poseStack.translate(f * -1.0F, 3.6F, 3.5F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(f * 120.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(200.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(f * -135.0F));
        poseStack.translate(f * 5.6F, 0.0F, 0.0F);

        PlayerRenderer renderer = (PlayerRenderer) Minecraft.getInstance()
                .getEntityRenderDispatcher().getRenderer(player);
        if (side == HumanoidArm.RIGHT) {
            renderer.renderRightHand(poseStack, event.getMultiBufferSource(),
                    event.getPackedLight(), player);
        } else {
            renderer.renderLeftHand(poseStack, event.getMultiBufferSource(),
                    event.getPackedLight(), player);
        }
        poseStack.popPose();
    }
}
