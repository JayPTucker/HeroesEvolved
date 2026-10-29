package com.jayptucker.heroesevolved.render;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.flight.FlightVisualGeometry;
import com.jayptucker.heroesevolved.network.ClientFlightVisualState;
import com.mojang.math.Axis;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

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
    private static final Map<Integer, FlightAnimation> ANIMATIONS = new HashMap<>();

    private FlightPoseRenderer() {
    }

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        boolean active = shouldUseFlightPose(player);
        FlightAnimation animation = ANIMATIONS.get(player.getId());
        if (!active && animation == null) {
            return;
        }

        double time = player.level().getGameTime() + event.getPartialTick();
        if (animation == null) {
            animation = new FlightAnimation(time - 1, player.getXRot());
            ANIMATIONS.put(player.getId(), animation);
        }
        float elapsed = (float) Math.clamp(time - animation.time, 0, 2);
        animation.time = time;
        float blend = 1 - (float) Math.exp(-elapsed * .48F);
        animation.amount = Mth.lerp(blend, animation.amount, active ? 1 : 0);
        if (player.onGround() || !player.isAlive()) animation.amount = 0;
        if (animation.amount < .01F) {
            if (!active) ANIMATIONS.remove(player.getId());
            return;
        }

        float viewYaw = FlightVisualGeometry.yaw(player,
                ClientFlightVisualState.isCycloneActive(player.getId()), event.getPartialTick());
        float viewPitch = player.getXRot();
        float renderPitch = FlightVisualGeometry.pitch(player, event.getPartialTick());
        Vec3 velocity = player.getDeltaMovement();
        boolean rolling = ClientFlightVisualState.isRolling(player.getId());
        if ((ClientFlightVisualState.isDiving(player.getId()) || rolling) && velocity.lengthSqr() > .15) {
            // Maneuvers follow their real travel; a dive can descend while the pilot looks level.
            viewYaw = (float) Math.toDegrees(Math.atan2(-velocity.x, velocity.z));
            renderPitch = (float) Math.toDegrees(Math.atan2(-velocity.y, velocity.horizontalDistance()));
        }
        animation.pitch = Mth.lerp(blend, animation.pitch, renderPitch);
        float turn = Mth.wrapDegrees(player.getYRot() - player.yRotO);
        float speed = (float) Math.clamp(velocity.length() / 1.8, 0, 1);
        float bank = Mth.clamp(-turn * 2.4F, -24, 24) * speed;
        animation.bank = Mth.lerp(blend, animation.bank, bank);
        float bodyYaw = viewYaw + 180.0F * animation.amount;

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
        float flightPitch = (90.0F + animation.pitch) * animation.amount;

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
        float headPitch = Mth.lerp(animation.amount, viewPitch, HEAD_LOOK_UP_DEGREES);
        player.setXRot(headPitch);
        player.xRotO = headPitch;

        // PlayerRenderer applies body yaw after this event. The two yaw
        // rotations make the pitch relative to the direction the player faces,
        // rather than relative to the world's north/south axis.
        event.getPoseStack().pushPose();
        event.getPoseStack().translate(0.0D, FlightVisualGeometry.PIVOT_HEIGHT, 0.0D);
        // Rotate the completed horizontal pose toward the player's travel
        // direction. Keeping this separate prevents it from flipping torso
        // orientation back toward the sky.
        event.getPoseStack().mulPose(Axis.YP.rotationDegrees(180.0F * animation.amount));
        event.getPoseStack().mulPose(Axis.YP.rotationDegrees(-bodyYaw));
        event.getPoseStack().mulPose(
                Axis.XP.rotationDegrees(flightPitch)
        );
        // Bank the body about its own long axis; the camera remains level.
        float roll = 0;
        if (rolling) {
            float progress = Mth.clamp(ClientFlightVisualState.rollProgress(player.getId())
                    + event.getPartialTick() / 12F, 0, 1);
            // Ease in and out of one complete turn. Clearing the flag returns from 360 to 0.
            roll = progress * progress * (3 - 2 * progress) * 360
                    * ClientFlightVisualState.rollDirection(player.getId());
        }
        event.getPoseStack().mulPose(Axis.YP.rotationDegrees(animation.bank * animation.amount + roll));
        event.getPoseStack().mulPose(Axis.YP.rotationDegrees(bodyYaw));
        event.getPoseStack().translate(0.0D, -FlightVisualGeometry.PIVOT_HEIGHT, 0.0D);

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
                && !(player.getVehicle() instanceof com.jayptucker.heroesevolved.entity.CarryAnchorEntity)
                && !player.onGround();
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (!event.getLevel().isClientSide()) return;
        ANIMATIONS.clear();
        POSED_ENTITIES.clear();
        WALK_SPEEDS.clear();
        BODY_YAWS.clear();
        HEAD_ORIENTATIONS.clear();
    }

    private static final class FlightAnimation {
        private double time;
        private float amount;
        private float pitch;
        private float bank;

        private FlightAnimation(double time, float pitch) {
            this.time = time;
            this.pitch = pitch;
        }
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
