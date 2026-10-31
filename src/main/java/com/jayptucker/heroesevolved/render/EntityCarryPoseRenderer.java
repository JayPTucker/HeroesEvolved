package com.jayptucker.heroesevolved.render;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.client.CarryVisuals;
import com.jayptucker.heroesevolved.entity.CarryAnchorEntity;
import com.jayptucker.heroesevolved.entity.CarryGeometry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.HashSet;
import java.util.Set;

/**
 * Draws cargo where the carrier's arms actually are. The model is placed from the carrier's own
 * interpolated position and facing every frame (never from the cargo's lagging one), so it stays
 * locked to the arms while walking and turning:
 * <ul>
 *   <li>A person lies back across both arms, a bridal carry, legs folded over the right arm.</li>
 *   <li>Anything smaller is held against the chest, facing the carrier's left.</li>
 *   <li>A big Skyhook lift is held up over the head.</li>
 * </ul>
 * The carrier's body is turned with their view while carrying, so the arms never point away from
 * what they hold.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class EntityCarryPoseRenderer {
    private static final Set<Integer> TRANSFORMED_ENTITIES = new HashSet<>();

    private EntityCarryPoseRenderer() {}

    @SubscribeEvent
    public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity entity = event.getEntity();
        // A carrier faces what it is holding: its body turns with its head.
        if (CarryVisuals.isCarrying(entity)) {
            entity.yBodyRot = entity.getYRot();
            entity.yBodyRotO = entity.yRotO;
        }
        applyCarryPose(entity, event.getPoseStack(), event.getPartialTick());
    }

    @SubscribeEvent
    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> event) {
        if (TRANSFORMED_ENTITIES.remove(event.getEntity().getId())) event.getPoseStack().popPose();
    }

    private static Vec3 renderPosition(Entity entity, float partialTick) {
        return new Vec3(Mth.lerp(partialTick, entity.xOld, entity.getX()), Mth.lerp(partialTick, entity.yOld, entity.getY()),
                Mth.lerp(partialTick, entity.zOld, entity.getZ()));
    }

    private static void applyCarryPose(LivingEntity entity, PoseStack poseStack, float partialTick) {
        if (!(entity.getVehicle() instanceof CarryAnchorEntity anchor)) return;
        Entity carrier = anchor.getVehicle();
        if (carrier == null || carrier.isRemoved()) return;
        float carrierYaw = Mth.rotLerp(partialTick, carrier.yRotO, carrier.getYRot());
        float bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        CarryGeometry.Hold hold = anchor.hold();
        int side = CarryGeometry.side(carrier);
        Vec3 local = CarryGeometry.pivot(hold, carrier.getBbWidth(), carrier.getBbHeight(), entity.getBbWidth(), entity.getBbHeight(), side);
        Vec3 pivot = renderPosition(carrier, partialTick).add(CarryGeometry.rotate(local, carrierYaw));
        Vec3 offset = pivot.subtract(renderPosition(entity, partialTick));

        poseStack.pushPose();
        poseStack.translate(offset.x, offset.y, offset.z);
        // Into the carrier's frame: x to their left, y up, z forward.
        poseStack.mulPose(Axis.YP.rotationDegrees(-carrierYaw));
        switch (hold) {
            case ARMS -> {
                // Reclined across the arms, head up toward the left shoulder, face turned a little toward the carrier.
                poseStack.mulPose(Axis.ZP.rotationDegrees((float) CarryGeometry.RECLINE_DEGREES * side));
                poseStack.mulPose(Axis.XP.rotationDegrees(-110));
                poseStack.mulPose(Axis.ZP.rotationDegrees(-90 * side));
                poseStack.translate(0, -CarryGeometry.HIP_HEIGHT, 0);
            }
            case CRADLE -> {
                poseStack.mulPose(Axis.YP.rotationDegrees(90 * side));
                poseStack.translate(0, -entity.getBbHeight() * .5, 0);
            }
            case OVERHEAD -> poseStack.mulPose(Axis.YP.rotationDegrees(90 * side));
        }
        // The renderer turns the model by its own body yaw next; cancel that so it faces our frame's +z.
        poseStack.mulPose(Axis.YP.rotationDegrees(bodyYaw));
        TRANSFORMED_ENTITIES.add(entity.getId());
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) TRANSFORMED_ENTITIES.clear();
    }
}
