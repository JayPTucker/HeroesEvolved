package com.jayptucker.heroesevolved.render;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.flight.FlightVisualGeometry;
import com.jayptucker.heroesevolved.network.ClientArmPoseState;
import com.jayptucker.heroesevolved.pyro.PyroGeometry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.HashSet;
import java.util.Set;

/**
 * The fighting styles turn the whole body, not just the limbs. Firebending: the roundhouse twists the hips round with
 * the kick, the sweep kick spins once on the planted foot, a jet step leans away from its jet, and the flip kick turns a
 * full backflip. Lightning: the shoulders snap into each spark, the clap leans in, the slam drops to one knee. Water: the
 * whip winds away and cracks through, the wheel spins, the vault flips, and a surfer stands sideways on the wave. A
 * speedster's skid drops onto a knee side-on, or leans low over a braking hand. Only the model turns; the camera never
 * does.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class FightingStylePoseRenderer {
    private static final Set<Integer> POSED = new HashSet<>();

    private FightingStylePoseRenderer() {}

    @SubscribeEvent
    public static void pre(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        ArmPose pose = ClientArmPoseState.get(player.getId());
        if (pose == null || !player.isAlive()) return;
        float age = ClientArmPoseState.ageTicks(player.getId());
        float body = Mth.rotLerp(event.getPartialTick(), player.yBodyRotO, player.yBodyRot);
        float handed = player.getMainArm() == HumanoidArm.LEFT ? -1 : 1;
        float twist = 0, pitch = 0, roll = 0, drop = 0;
        switch (pose) {
            case FIRE_JAB, FIRE_CROSS -> {
                // The shoulders turn into the punch.
                float punch = smooth(age / 1.5F) * (1 - smooth((age - 4) / 4F));
                twist = (pose == ArmPose.FIRE_CROSS ? 14 : -10) * handed * punch;
            }
            case FIRE_KICK -> {
                float swing = Mth.sin(Mth.PI * Mth.clamp(age / 10F, 0, 1));
                twist = 48 * handed * swing;
                pitch = -10 * swing;
            }
            case FIRE_SWEEP_KICK -> {
                float spin = Mth.clamp(age / PyroGeometry.SWEEP_SPIN, 0, 1);
                twist = 360 * handed * (1 - (1 - spin) * (1 - spin));
                drop = .18F * smooth(age / 2F) * (1 - smooth((age - 10) / 4F));
            }
            case FIRE_STEP -> {
                Vec3 travel = FlightVisualGeometry.travel(player);
                double yaw = Math.toRadians(body);
                double sideways = travel.x * -Math.cos(yaw) + travel.z * -Math.sin(yaw);
                float lean = 1 - smooth((age - 3) / 6F);
                roll = (float) Math.clamp(sideways * 18, -16, 16) * lean;
            }
            case FIRE_FLIP -> pitch = -360 * smooth(age / PyroGeometry.FLIP_SPIN);
            // Dragon Coil's landing: dropped into a low stance, leaning a little into the thrust palm.
            case FINISH_DRAGON -> {
                float settle = smooth(age / 2F) * (1 - smooth((age - 11) / 4F));
                drop = .32F * settle;
                pitch = 7 * settle;
                twist = 14 * handed * settle;
            }
            // Lightning: a quicker, smaller turn into each spark, a lean into the clap, a drop to one knee for the slam,
            // a lean along a flash sideways and a lean back into a Flashback.
            case ELECTRO_JAB, ELECTRO_CROSS -> {
                float punch = smooth(age / 1.2F) * (1 - smooth((age - 3.5F) / 4F));
                twist = (pose == ArmPose.ELECTRO_CROSS ? 12 : -8) * handed * punch;
            }
            case ELECTRO_CLAP -> {
                float lean = smooth((age - 1) / 1.5F) * (1 - smooth((age - 7) / 5F));
                pitch = 9 * lean;
            }
            case ELECTRO_SLAM -> {
                float down = smooth(age / 2F) * (1 - smooth((age - 10) / 4F));
                drop = .34F * down;
                pitch = 24 * down;
            }
            case ELECTRO_STEP -> {
                Vec3 travel = FlightVisualGeometry.travel(player);
                double yaw = Math.toRadians(body);
                double sideways = travel.x * -Math.cos(yaw) + travel.z * -Math.sin(yaw);
                roll = (float) Math.clamp(sideways * 14, -20, 20) * (1 - smooth((age - 2) / 5F));
            }
            case ELECTRO_FLASH -> pitch = -17 * smooth(age / 1.2F) * (1 - smooth((age - 4) / 5F));
            // Water: the body flows with the arms. The whip winds away and cracks through, the heave leans back then
            // drives forward, the wheel spins once, the vault flips over backward, the surfer stands sideways on the
            // wave and leans into each turn.
            case WATER_WHIP -> {
                float wind = smooth(age / 2F), crack = smooth((age - 2) / 1.6F), recover = smooth((age - 7) / 3F);
                twist = handed * (-12 * wind * (1 - crack) + 18 * crack * (1 - recover));
                pitch = 9 * crack * (1 - recover);
            }
            case WATER_SNARE -> {
                float haul = smooth((age - 4) / 4F) * (1 - smooth((age - 10) / 3F));
                twist = -12 * handed * haul;
                pitch = -9 * haul;
            }
            case WATER_HEAVE -> {
                float scoop = smooth(age / 2.5F), heave = smooth((age - 2.5F) / 3F), recover = smooth((age - 9) / 5F);
                pitch = (-8 * scoop * (1 - heave) + 12 * heave) * (1 - recover);
            }
            case WATER_LASH, WATER_BACKHAND -> {
                float flick = smooth(age / 1.3F) * (1 - smooth((age - 4) / 4F));
                twist = (pose == ArmPose.WATER_BACKHAND ? 15 : -11) * handed * flick;
            }
            case WATER_DAGGERS -> pitch = 8 * smooth((age - 2) / 1.5F) * (1 - smooth((age - 7) / 5F));
            case WATER_WHEEL -> {
                float spin = Mth.clamp(age / com.jayptucker.heroesevolved.hydro.HydroGeometry.WHEEL_SPIN, 0, 1);
                twist = 360 * handed * (1 - (1 - spin) * (1 - spin));
                drop = .14F * smooth(age / 2F) * (1 - smooth((age - 10) / 4F));
            }
            case WATER_FLOW -> {
                Vec3 travel = FlightVisualGeometry.travel(player);
                double yaw = Math.toRadians(body);
                double sideways = travel.x * -Math.cos(yaw) + travel.z * -Math.sin(yaw);
                roll = (float) Math.clamp(sideways * 16, -14, 14) * (1 - smooth((age - 3) / 6F));
            }
            case WATER_VAULT -> pitch = -360 * smooth(age / com.jayptucker.heroesevolved.hydro.HydroGeometry.VAULT_FLIP);
            case WATER_SURF -> {
                float settle = smooth(age / 4F);
                float turn = Mth.clamp(Mth.wrapDegrees(player.getYRot() - player.yRotO) * .9F, -18, 18);
                twist = 58 * handed * settle;
                roll = -turn * settle;
                drop = (.12F + .04F * Mth.sin(age * .21F)) * settle;
            }
            case WATER_LEAP -> pitch = 16 * Mth.sin(Mth.PI * Mth.clamp(age / 14F, 0, 1));
            case WATER_CALL -> pitch = -5 * smooth(age / 5F);
            // The shared styles: the shoulders turn into every blow, the haymaker and hook twist the hips, the hammer and
            // headbutt pitch forward into the target, the slide drops onto a hip leaning back, the spin turns once, the
            // tackle drives the shoulder down, and a side step leans along its line.
            case STYLE_JAB, STYLE_CROSS -> {
                float punch = smooth(age / 1.4F) * (1 - smooth((age - 4) / 4F));
                twist = (pose == ArmPose.STYLE_CROSS ? 12 : -9) * handed * punch;
            }
            case STYLE_FLURRY -> twist = 7 * handed * Mth.sin(age * 3.1F) * (1 - smooth((age - 7) / 3F));
            case STYLE_HOOK -> twist = -22 * handed * smooth(age / 1.8F) * (1 - smooth((age - 6) / 4F));
            case STYLE_HAYMAKER -> {
                float wind = smooth(age / 2F), swing = smooth((age - 2) / 2F), recover = smooth((age - 7) / 4F);
                twist = handed * (-18 * wind * (1 - swing) + 32 * swing * (1 - recover));
            }
            case STYLE_UPPERCUT -> {
                float rise = smooth((age - 1.5F) / 2F) * (1 - smooth((age - 7) / 4F));
                pitch = -10 * rise;
                drop = .1F * smooth(age / 1.5F) * (1 - smooth((age - 1.5F) / 1.5F));
            }
            case STYLE_HAMMER -> pitch = 20 * smooth((age - 3) / 2F) * (1 - smooth((age - 8) / 4F)) - 6 * smooth(age / 2.5F) * (1 - smooth((age - 3) / 2F));
            case STYLE_DOUBLE_PALM -> pitch = 9 * smooth((age - 1.5F) / 1.5F) * (1 - smooth((age - 7) / 4F));
            case STYLE_GROUND -> {
                float down = smooth(age / 2F) * (1 - smooth((age - 10) / 4F));
                drop = .34F * down;
                pitch = 24 * down;
            }
            case STYLE_SLIDE -> {
                float on = smooth(age / 1.5F) * (1 - smooth((age - 8) / 4F));
                drop = .5F * on;
                pitch = -22 * on;
            }
            case STYLE_SIDESTEP -> {
                Vec3 travel = FlightVisualGeometry.travel(player);
                double yaw = Math.toRadians(body);
                double sideways = travel.x * -Math.cos(yaw) + travel.z * -Math.sin(yaw);
                roll = (float) Math.clamp(sideways * 14, -16, 16) * (1 - smooth((age - 3) / 5F));
            }
            case STYLE_BACKSTEP -> pitch = -12 * smooth(age / 1.2F) * (1 - smooth((age - 5) / 5F));
            case STYLE_SPIN -> {
                float spin = Mth.clamp(age / 9F, 0, 1);
                twist = 360 * handed * (1 - (1 - spin) * (1 - spin));
            }
            case STYLE_HEADBUTT -> {
                float back = smooth(age / 1.5F) * (1 - smooth((age - 1.5F) / 1F)), drive = smooth((age - 2) / 1.2F) * (1 - smooth((age - 6) / 5F));
                pitch = -10 * back + 30 * drive;
            }
            case STYLE_TACKLE -> {
                float on = smooth(age / 1.5F) * (1 - smooth((age - 8) / 4F));
                pitch = 26 * on;
                twist = -25 * handed * on;
                drop = .1F * on;
            }
            case STYLE_BRACE -> drop = .1F * smooth(age / 1.5F);
            case STYLE_SWAT, STYLE_SWAT_OFF -> twist = (pose == ArmPose.STYLE_SWAT ? 10 : -10) * handed
                    * smooth(age / 1.2F) * (1 - smooth((age - 5) / 3F));
            case STYLE_LIFT -> pitch = -7 * smooth(age / 3F) * (1 - smooth((age - 9) / 2F));
            // Blur Boxing's skids. Back: thrown back, turning side-on and dropping onto one knee, leaning forward over
            // the hand that drags in front, then rising. Sideways: crouched low and leaning away from the slide, over
            // the trailing hand on the ground.
            case SPEED_SKID_BACK -> {
                float thrown = smooth(age) * (1 - smooth((age - 1.5F) / 2F));
                float kneel = smooth((age - 1) / 2.5F) * (1 - smooth((age - 12) / 5F));
                drop = .45F * kneel;
                pitch = 22 * kneel - 6 * thrown;
                twist = -42 * handed * smooth(age / 4F) * (1 - smooth((age - 12) / 5F));
            }
            case SPEED_SKID_LEFT, SPEED_SKID_RIGHT -> {
                float on = smooth(age / 1.5F) * (1 - smooth((age - 9) / 5F));
                // A positive roll tips the head to the body's right; lean away from the way the skid goes.
                roll = (pose == ArmPose.SPEED_SKID_RIGHT ? -26 : 26) * on;
                drop = .4F * on;
                pitch = 8 * on;
            }
            // A runner's hockey stop: turned side-on to the run (the rear shoulder back), leaning away from it.
            case SPEED_SKID_STOP -> {
                float on = smooth(age / 1.5F) * (1 - smooth((age - 9) / 5F));
                twist = -68 * handed * on;
                roll = 24 * handed * on;
                drop = .38F * on;
                pitch = 4 * on;
            }
            // Density's earthbending (Hung Gar): the body sits low in Horse Stance, turns behind its punches, leans over a
            // shove or a slam, and rides an Earth Surf side-on.
            // Held level from one move to the next (a ramp from standing bobbed the body at every change).
            case EARTH_STANCE, EARTH_LISTEN, EARTH_LIFT, EARTH_RAISE, EARTH_WALL, EARTH_HEAVE, EARTH_TENT, EARTH_CRUSH -> drop = .1F;
            case EARTH_STRIKE, EARTH_GAUNTLET -> {
                float punch = smooth(age / 1.5F) * (1 - smooth((age - 5) / 5F));
                twist = -18 * handed * punch;
                drop = .08F;
            }
            case EARTH_CLAW -> {
                twist = 14 * handed * smooth(age / 1.5F) * (1 - smooth((age - 5) / 4F));
                drop = .08F;
            }
            case EARTH_SHOVE -> {
                pitch = 9 * smooth((age - 1.5F) / 1.5F) * (1 - smooth((age - 7) / 4F));
                drop = .1F;
            }
            case EARTH_STOMP -> {
                float lift = smooth(age / 2.5F) * (1 - smooth((age - 2.5F) / 1.5F));
                drop = .12F * smooth((age - 3) / 1.5F) - .06F * lift;
                pitch = 6 * smooth((age - 3) / 1.5F) * (1 - smooth((age - 8) / 4F));
            }
            case EARTH_SLAM -> {
                pitch = 15 * smooth((age - 1) / 2F) * (1 - smooth((age - 7) / 5F)) - 5 * smooth(age / 1.5F) * (1 - smooth((age - 1) / 2F));
                drop = .14F * smooth((age - 1) / 2F);
            }
            case EARTH_SURF -> {
                float settle = smooth(age / 4F);
                float turn = Mth.clamp(Mth.wrapDegrees(player.getYRot() - player.yRotO) * .9F, -18, 18);
                twist = 50 * handed * settle;
                roll = -turn * settle;
                drop = (.14F + .03F * Mth.sin(age * .25F)) * settle;
            }
            case EARTH_SLIDE_LEFT, EARTH_SLIDE_RIGHT -> {
                float on = smooth(age / 1.5F) * (1 - smooth((age - 8) / 5F));
                roll = (pose == ArmPose.EARTH_SLIDE_RIGHT ? -9 : 9) * on;
                drop = .16F * on;
            }
            default -> { return; }
        }
        if (twist == 0 && pitch == 0 && roll == 0 && drop == 0) return;
        PoseStack stack = event.getPoseStack();
        stack.pushPose();
        stack.translate(0, .9 - drop, 0);
        stack.mulPose(Axis.YP.rotationDegrees(-body));
        stack.mulPose(Axis.YP.rotationDegrees(twist));
        stack.mulPose(Axis.ZP.rotationDegrees(roll));
        stack.mulPose(Axis.XP.rotationDegrees(pitch));
        stack.mulPose(Axis.YP.rotationDegrees(body));
        stack.translate(0, -.9, 0);
        POSED.add(player.getId());
    }

    @SubscribeEvent
    public static void post(RenderPlayerEvent.Post event) {
        if (POSED.remove(event.getEntity().getId())) event.getPoseStack().popPose();
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) POSED.clear();
    }

    private static float smooth(float value) {
        float t = Mth.clamp(value, 0, 1);
        return t * t * (3 - 2 * t);
    }
}
