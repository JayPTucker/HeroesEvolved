package com.jayptucker.heroesevolved.render;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.entity.CarryAnchorEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

import java.util.HashSet;
import java.util.Set;

/** Renders anchor passengers horizontally across their carrier's arms. */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class EntityCarryPoseRenderer {
    private static final Set<Integer> TRANSFORMED_ENTITIES = new HashSet<>();

    private EntityCarryPoseRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        if (!(event.getEntity() instanceof Player)) {
            applyCarryPose(event.getEntity(), event.getPoseStack());
        }
    }

    @SubscribeEvent
    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> event) {
        if (!(event.getEntity() instanceof Player)) {
            clearCarryPose(event.getEntity(), event.getPoseStack());
        }
    }

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        applyCarryPose(event.getEntity(), event.getPoseStack());
    }

    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        clearCarryPose(event.getEntity(), event.getPoseStack());
    }

    private static void applyCarryPose(Entity entity, PoseStack poseStack) {
        if (!(entity.getVehicle() instanceof CarryAnchorEntity anchor)) {
            return;
        }

        // Rotate the normal upright model into a horizontal, arms-held pose.
        // The anchor itself is synchronized in front of the carrier's chest.
        float carrierYaw = anchor.getYRot();
        poseStack.pushPose();
        poseStack.translate(0.0D, 0.9D, 0.0D);
        poseStack.mulPose(Axis.YP.rotationDegrees(-carrierYaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(carrierYaw));
        poseStack.translate(0.0D, -0.9D, 0.0D);
        TRANSFORMED_ENTITIES.add(entity.getId());
    }

    private static void clearCarryPose(Entity entity, PoseStack poseStack) {
        if (TRANSFORMED_ENTITIES.remove(entity.getId())) {
            poseStack.popPose();
        }
    }
}
