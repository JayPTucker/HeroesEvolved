package com.jayptucker.heroesevolved.render;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.client.CarryVisuals;
import com.jayptucker.heroesevolved.entity.CarryAnchorEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.HashSet;
import java.util.Set;

/** Keeps cargo upright beside the carrier; humanoids lean gently onto the supporting arm. */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class EntityCarryPoseRenderer {
    private static final Set<Integer> TRANSFORMED_ENTITIES = new HashSet<>();

    private EntityCarryPoseRenderer() {}

    @SubscribeEvent
    public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        if (!(event.getEntity() instanceof Player)) {
            applyCarryPose(event.getEntity(), event.getPoseStack(), event.getPartialTick(),
                    event.getRenderer().getModel() instanceof HumanoidModel<?>);
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
        applyCarryPose(event.getEntity(), event.getPoseStack(), event.getPartialTick(), true);
    }

    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        clearCarryPose(event.getEntity(), event.getPoseStack());
    }

    private static void applyCarryPose(LivingEntity entity, PoseStack poseStack,
                                       float partialTick, boolean humanoid) {
        if (!(entity.getVehicle() instanceof CarryAnchorEntity anchor)) return;
        var carrier = anchor.getVehicle();
        if (carrier == null || carrier.isRemoved()) return;
        float carrierYaw = Mth.rotLerp(partialTick, carrier.yRotO, carrier.getYRot());
        float bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        poseStack.pushPose();
        int side = CarryVisuals.lateralSide(carrier, anchor);
        if (humanoid && side != 0) {
            // Dimension-based pivot keeps short and tall humanoids beside the shoulder.
            double pivot = Math.clamp(entity.getBbHeight() * .5, .1, 2.5);
            poseStack.translate(0, pivot, 0);
            poseStack.mulPose(Axis.YP.rotationDegrees(-carrierYaw));
            poseStack.mulPose(Axis.ZP.rotationDegrees(8 * side));
            poseStack.mulPose(Axis.YP.rotationDegrees(carrierYaw));
            poseStack.translate(0, -pivot, 0);
        }
        // Align the model without changing real entity rotation or the carried player's camera.
        poseStack.mulPose(Axis.YP.rotationDegrees(Mth.wrapDegrees(bodyYaw - carrierYaw)));
        TRANSFORMED_ENTITIES.add(entity.getId());
    }

    private static void clearCarryPose(LivingEntity entity, PoseStack poseStack) {
        if (TRANSFORMED_ENTITIES.remove(entity.getId())) poseStack.popPose();
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) TRANSFORMED_ENTITIES.clear();
    }
}
