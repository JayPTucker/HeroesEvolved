package com.jayptucker.heroesevolved.render;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.network.ClientFlightVisualState;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class FlightPoseRenderer {
    private static final float HEAD_LOOK_UP_DEGREES = -40.0F;
    private static final Set<Integer> POSED_ENTITIES = new HashSet<>();
    private static final Map<Integer, Float> WALK_SPEEDS = new HashMap<>();
    private static final Map<Integer, BodyYaw> BODY_YAWS = new HashMap<>();
    private static final Map<Integer, HeadOrientation> HEAD_ORIENTATIONS =
            new HashMap<>();

    private FlightPoseRenderer() {
    }

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();

        if (!shouldUseFlightPose(player)) {
            return;
        }

        float viewYaw = Mth.rotLerp(
                event.getPartialTick(),
                player.yRotO,
                player.getYRot()
        );
        float bodyYaw = viewYaw + 180.0F;
        float viewPitch = player.getXRot();

        // Minecraft's upright player model faces the opposite direction once
        // it is pitched horizontal, so Flight uses the inverted yaw to keep
        // the head leading and the feet trailing behind.
        BODY_YAWS.put(
                player.getId(),
                new BodyYaw(player.yBodyRot, player.yBodyRotO)
        );
        player.yBodyRot = bodyYaw;
        player.yBodyRotO = bodyYaw;

        // Turn the model face-down into its Flight pose, then let its pitch
        // follow the player's view for climbing and diving.
        float flightPitch = 90.0F + viewPitch;

        // Keep the head aligned with the Flight body. Without this, vanilla
        // applies the look pitch a second time and the head bends unnaturally
        // when the player dives or climbs.
        HEAD_ORIENTATIONS.put(
                player.getId(),
                new HeadOrientation(
                        player.yHeadRot,
                        player.yHeadRotO,
                        viewPitch,
                        player.xRotO
                )
        );
        // Keep the head centered with the body, then lift it slightly so the
        // player looks ahead rather than directly along the body's flat axis.
        float headYaw = bodyYaw + 0.0F;
        player.setYHeadRot(headYaw);
        player.yHeadRotO = headYaw;
        player.setXRot(HEAD_LOOK_UP_DEGREES);
        player.xRotO = HEAD_LOOK_UP_DEGREES;

        // PlayerRenderer applies body yaw after this event. The two yaw
        // rotations make the pitch relative to the direction the player faces,
        // rather than relative to the world's north/south axis.
        event.getPoseStack().pushPose();
        event.getPoseStack().translate(0.0D, 0.9D, 0.0D);
        // Rotate the completed horizontal pose toward the player's travel
        // direction. Keeping this separate prevents it from flipping torso
        // orientation back toward the sky.
        event.getPoseStack().mulPose(Axis.YP.rotationDegrees(180.0F));
        event.getPoseStack().mulPose(Axis.YP.rotationDegrees(-bodyYaw));
        event.getPoseStack().mulPose(
                Axis.XP.rotationDegrees(flightPitch)
        );
        event.getPoseStack().mulPose(Axis.YP.rotationDegrees(bodyYaw));
        event.getPoseStack().translate(0.0D, -0.9D, 0.0D);

        // Stop vanilla's walk cycle for this render pass so the legs remain
        // straight instead of appearing to run while the player is flying.
        WALK_SPEEDS.put(player.getId(), player.walkAnimation.speed());
        player.walkAnimation.setSpeed(0.0F);
        // speed(partialTick) interpolates from the previous speed. Updating
        // after setting zero clears that previous value too.
        player.walkAnimation.update(0.0F, 1.0F);
        POSED_ENTITIES.add(player.getId());
    }

    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        if (POSED_ENTITIES.remove(event.getEntity().getId())) {
            Float originalWalkSpeed = WALK_SPEEDS.remove(
                    event.getEntity().getId()
            );

            if (originalWalkSpeed != null) {
                event.getEntity().walkAnimation.setSpeed(originalWalkSpeed);
            }

            BodyYaw originalBodyYaw = BODY_YAWS.remove(
                    event.getEntity().getId()
            );

            if (originalBodyYaw != null) {
                event.getEntity().yBodyRot = originalBodyYaw.current();
                event.getEntity().yBodyRotO = originalBodyYaw.previous();
            }

            HeadOrientation originalHead = HEAD_ORIENTATIONS.remove(
                    event.getEntity().getId()
            );

            if (originalHead != null) {
                event.getEntity().setYHeadRot(originalHead.currentYaw());
                event.getEntity().yHeadRotO = originalHead.previousYaw();
                event.getEntity().setXRot(originalHead.currentPitch());
                event.getEntity().xRotO = originalHead.previousPitch();
            }

            event.getPoseStack().popPose();
        }
    }

    private static boolean shouldUseFlightPose(Player player) {
        return ClientFlightVisualState.isFlightActive(player.getId())
                && !player.onGround();
    }

    private record BodyYaw(float current, float previous) {
    }

    private record HeadOrientation(
            float currentYaw,
            float previousYaw,
            float currentPitch,
            float previousPitch
    ) {
    }
}
