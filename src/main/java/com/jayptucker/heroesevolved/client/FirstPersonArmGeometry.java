package com.jayptucker.heroesevolved.client;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;

/** Camera-space shoulder anchors. Each arm remains on its own side of the crosshair. */
public final class FirstPersonArmGeometry {
    private FirstPersonArmGeometry() {}

    public static void pose(PoseStack stack, HumanoidArm arm, ArmPose pose, float time) {
        pose(stack, arm, pose, time, time);
    }

    public static void pose(PoseStack stack, HumanoidArm arm, ArmPose pose, float time, float poseAge) {
        float side = arm == HumanoidArm.RIGHT ? 1 : -1;
        if (switch (pose) {
            case VIBE_PALM, VIBE_OPEN, VIBE_READ, VIBE_GUARD, VIBE_LOCK, VIBE_LANCE, VIBE_CROSS, VIBE_STEP -> true;
            default -> false;
        }) {
            vibe(stack, arm, side, pose, poseAge);
            return;
        }
        if (switch (pose) {
            case TIME_HALT, TIME_SNAP, TIME_PLANT, TIME_RECALL, TIME_WIND, TIME_FRAME, TIME_THROW, TIME_DRAW, TIME_TURN -> true;
            default -> false;
        }) {
            time(stack, arm, side, pose, poseAge, time);
            return;
        }
        if (switch (pose) {
            case DENSITY_LUNGE, DENSITY_USHER, DENSITY_BRACE, DENSITY_BURST, DENSITY_PRESS, DENSITY_CLOSE, DENSITY_FADE,
                    DENSITY_DROP, DENSITY_GRASP, DENSITY_SLIP -> true;
            default -> false;
        }) {
            density(stack, arm, side, pose, poseAge, time);
            return;
        }
        if (switch (pose) {
            case ELECTRO_DRAW, ELECTRO_CAST, ELECTRO_STREAM, ELECTRO_CALL, ELECTRO_COIL, ELECTRO_RELEASE -> true;
            default -> false;
        }) {
            electro(stack, side, pose, poseAge);
            return;
        }
        if (switch (pose) {
            case ELECTRO_RIDE, ELECTRO_CUP, ELECTRO_LANCE, ELECTRO_TOUCH, ELECTRO_OVERCHARGE, ELECTRO_GROUND, ELECTRO_POINT,
                    SPEED_GUARD, SPEED_SPLIT, SPEED_LUNGE, SPEED_THROW, SPEED_ESCORT, SPEED_STILL,
                    MIMIC_TOUCH, MIMIC_SHIFT,
                    SONIC_CRY, SONIC_SCREAM, SONIC_PLUCK, SONIC_CLENCH, SONIC_WALL, SONIC_SHOVE, SONIC_VAULT, SONIC_DIVE,
                    SONIC_GATHER, SONIC_RELEASE, SONIC_CUP, SONIC_SNAP, SONIC_HOWL, SONIC_LISTEN, SONIC_HUM, SONIC_HUSH,
                    SONIC_CONDUCT, SONIC_DOME, SONIC_SING,
                    QUAKE_PALM, QUAKE_PALM_OFF, QUAKE_TUNE, QUAKE_CLENCH, QUAKE_PUNCH_DOWN, QUAKE_PLUNGE, QUAKE_UPHEAVAL, QUAKE_BEAM,
                    QUAKE_FLY, QUAKE_RUMBLE, QUAKE_AFTERSHOCK, QUAKE_FEEL, QUAKE_FRACTURED, QUAKE_UPPERCUT, QUAKE_TRACE, QUAKE_SHELL,
                    QUAKE_BRACE, QUAKE_RELEASE, QUAKE_DASH, QUAKE_TOUCH, QUAKE_SPLIT, QUAKE_RAMPART, QUAKE_ROOT,
                    REGEN_MEND, REGEN_OFFER, REGEN_STREAM, REGEN_BRACE, REGEN_LEAP, REGEN_LAND, REGEN_EMBRACE, REGEN_PAYBACK,
                    REGEN_INJECT, REGEN_FLING, REGEN_DRAW, REGEN_PUMP, REGEN_HARDEN, REGEN_RISE, REGEN_COMPRESS, REGEN_HEARTBEAT,
                    REGEN_DEFY, REGEN_BLOOM,
                    TELE_READ, TELE_SUGGEST, TELE_LINK, TELE_PUPPET, TELE_NIGHTMARE, TELE_COMMAND, TELE_LISTEN, TELE_WEAVE,
                    TELE_PERSUADE, TELE_MEMORY, TELE_HIVE, TELE_STORM, TELE_VEIL, TELE_GATHER,
                    SHIFT_STUDY, SHIFT_SWEEP, SHIFT_TOUCH, SHIFT_LUNGE,
                    SHIFT_SHED, SHIFT_REFLECT, SHIFT_NIGHTMARE, SHIFT_GUARD, SHIFT_GIFT, SHIFT_SLAM -> true;
            default -> false;
        }) {
            skills(stack, arm, side, pose, time, poseAge);
            return;
        }
        if (switch (pose) {
            case FLIGHT_COIL, FLIGHT_BRACE, FLIGHT_ROLL, FLIGHT_CLAP, FLIGHT_CAST -> true;
            default -> false;
        }) {
            flight(stack, side, pose, poseAge);
            return;
        }
        if (switch (pose) {
            case CRYO_WEAVE, CRYO_SCULPT, CRYO_PRISON, CRYO_SHATTER, CRYO_WHITEOUT, CRYO_SURF,
                    CRYO_LANCE, CRYO_GUARD, CRYO_SNAP, CRYO_LOOM, CRYO_SHELTER, CRYO_STARS -> true;
            default -> false;
        }) {
            cryo(stack, side, pose, poseAge);
            return;
        }
        if (switch (pose) {
            case FORCE_SHIELD, FORCE_PUSH, FORCE_BUILD, FORCE_BUBBLE, FORCE_CRUSH, FORCE_NOVA -> true;
            default -> false;
        }) {
            force(stack, side, pose, poseAge);
            return;
        }
        if (pose == ArmPose.OPTICAL_SPLIT || pose == ArmPose.OPTICAL_VEIL || pose == ArmPose.OPTICAL_DISPERSE) {
            optical(stack, side, pose, poseAge);
            return;
        }
        if (switch (pose) {
            case FIRE_LASH, FIRE_FORGE, FIRE_RELEASE, FIRE_BREATH, FIRE_SHEPHERD,
                    FIRE_CAUTERIZE, FIRE_SUN, FIRE_SWEEP, FIRE_DIVE -> true;
            default -> false;
        }) {
            fire(stack, side, pose, poseAge);
            return;
        }
        if (switch (pose) {
            case FIRE_JAB, FIRE_CROSS, FIRE_KICK, FIRE_SWEEP_KICK, FIRE_STEP, FIRE_FLIP -> true;
            default -> false;
        }) {
            var player = net.minecraft.client.Minecraft.getInstance().player;
            firebend(stack, side, player == null || arm == player.getMainArm(), pose, poseAge);
            return;
        }
        if (pose == ArmPose.KINETIC_GATHER || pose == ArmPose.KINETIC_HOLD || pose == ArmPose.KINETIC_RELEASE) {
            kinetic(stack, side, pose, time, poseAge);
            return;
        }
        if (pose == ArmPose.WIND_ARMS) {
            // Out in front, each hand circling fast and tight, the two turning opposite ways.
            float spin = time * 1.9F * side;
            stack.translate(side * .36F + Mth.cos(spin) * .07F, -.36F + Mth.sin(spin) * .07F, -.3F);
            stack.mulPose(Axis.YP.rotationDegrees(side * 4));
            stack.mulPose(Axis.XP.rotationDegrees(-90 + Mth.sin(spin) * 9));
            return;
        }
        float shake = pose == ArmPose.TWO_HANDS_STRAIN ? (float) Math.sin(time * 2.7) * .008F : 0;
        float spread = pose == ArmPose.ARMS_WIDE ? .65F : pose == ArmPose.SINK ? .6F : pose == ArmPose.REACH ? .3F : .42F;
        float height = switch (pose) {
            case ARMS_RAISED, TEMPLE -> -.1F;
            case GROUND, SINK -> -.55F;
            case REACH -> -.42F;
            default -> -.38F;
        };
        // Reach pushes the hand further out, as if it were already passing into the surface.
        stack.translate(side * spread + shake, height + shake, pose == ArmPose.REACH ? -.42F : -.22F);
        stack.mulPose(Axis.YP.rotationDegrees(side * (pose == ArmPose.ARMS_WIDE ? -30 : 4)));
        // Model arms extend along +Y; -90 degrees points them along camera -Z.
        // Smaller angles raise the hands, larger negative angles point downward.
        float pitch = switch (pose) {
            case GROUND -> -130;
            case SINK -> -150;
            case REACH -> -84;
            case ARMS_RAISED -> -35;
            case TEMPLE -> -20;
            default -> -90;
        };
        stack.mulPose(Axis.XP.rotationDegrees(pitch));
    }

    /** The centre of the palm under the same transform as RenderHandEvent. */
    public static Vec3 vibePalm(HumanoidArm arm, ArmPose pose, float age) {
        float side = arm == HumanoidArm.RIGHT ? 1 : -1;
        PoseStack stack = new PoseStack();
        vibe(stack, arm, side, pose, age);
        var palm = stack.last().pose().transformPosition(-side / 16F, .55F, 0, new org.joml.Vector3f());
        return new Vec3(palm.x, palm.y, palm.z);
    }

    private static void vibe(PoseStack stack, HumanoidArm arm, float side, ArmPose pose, float age) {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        boolean main = player == null ? arm == HumanoidArm.RIGHT : arm == player.getMainArm();
        float push = smooth(age / (pose == ArmPose.VIBE_PALM ? 3F : 6F));
        float settle = smooth((age - 10) / 7F);
        float x = .35F, y = -.39F, z = -.41F, pitch = -86, yaw = 0, roll = 0;
        switch (pose) {
            case VIBE_PALM -> {
                z += main ? .14F * (1 - push) - .09F * push + .08F * settle : .14F;
                y -= main ? .02F : .16F;
                pitch += main ? 32 * (1 - push) + 16 * settle : 40;
            }
            case VIBE_LANCE -> {
                x -= main ? 0 : .12F;
                y -= main ? .01F : .13F;
                z += main ? -.13F * push : .06F;
                yaw = main ? 0 : -24;
                pitch += main ? 14 * settle : 21;
            }
            case VIBE_CROSS -> {
                x += .13F * push - .10F;
                z -= .11F * push;
                yaw = -22 + 35 * push;
                roll = 18 * (1 - push);
            }
            case VIBE_OPEN -> {
                float open = smooth(age / 9F);
                x = .22F + .34F * open;
                y += .10F;
                yaw = -24 + 48 * open;
                roll = 16;
            }
            case VIBE_READ -> {
                float sight = smooth((age - 7) / 8F);
                y += main ? .41F * sight : -.15F;
                z += main ? .17F * sight : .14F;
                pitch -= main ? 34 * sight : -32;
                roll = main ? -22 * sight : 0;
            }
            case VIBE_GUARD -> {
                x += .08F * push;
                y += .12F * push;
                yaw = 20;
                roll = 19;
            }
            case VIBE_LOCK -> {
                float fall = smooth((age - 12) / 6F);
                y += .29F * push - .30F * fall;
                pitch += -26 * push + 43 * fall;
                x += .18F * push;
                yaw = 18;
            }
            case VIBE_STEP -> {
                x += main ? 0 : .14F;
                y += main ? .06F : -.20F;
                pitch += main ? 15 : 61;
                roll = main ? -12 : 19;
            }
            default -> { }
        }
        stack.translate(side * x, y, z);
        stack.mulPose(Axis.YP.rotationDegrees(side * yaw));
        stack.mulPose(Axis.ZP.rotationDegrees(side * roll));
        stack.mulPose(Axis.XP.rotationDegrees(pitch));
    }

    /**
     * Space-Time's skill gestures in first person. The casting hand stays at the edge of the view; the other drops away
     * unless both hands work together (the seal's frame, winding a crown).
     */
    private static void time(PoseStack stack, HumanoidArm arm, float side, ArmPose pose, float age, float clock) {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        boolean main = player == null ? arm == HumanoidArm.RIGHT : arm == player.getMainArm();
        float x = .35F, y = -.39F, z = -.41F, pitch = -86, yaw = 0, roll = 0;
        boolean both = pose == ArmPose.TIME_FRAME || pose == ArmPose.TIME_WIND;
        if (!main && !both) {
            // The idle hand drops out of view.
            y -= .5F; pitch += 55;
        } else switch (pose) {
            case TIME_HALT -> {
                float push = smooth(age / 3F), settle = smooth((age - 8) / 5F);
                z -= .14F * push - .05F * settle;
                x -= .06F;
                yaw = -8;
                pitch -= 6 * push;
            }
            case TIME_SNAP -> {
                float rise = smooth(age / 3F), flick = Mth.sin(Mth.clamp((age - 3) / 3F, 0, 1) * Mth.PI);
                y += .2F * rise;
                pitch -= 28 * rise - 10 * flick;
                roll = -20 * rise;
            }
            case TIME_PLANT -> {
                float press = smooth((age - 1) / 5F);
                y += .12F - .3F * press;
                pitch += -30 + 70 * press;
            }
            case TIME_RECALL -> {
                float open = smooth((age - 7) / 4F);
                x = Mth.lerp(open, .14F, .46F);
                y += .04F;
                roll = Mth.lerp(open, 35, -20);
            }
            case TIME_WIND -> {
                float lift = smooth(age / 3F);
                x = main ? .16F : .22F;
                y = main ? -.3F : -.44F;
                pitch += main ? 0 : 22;
                roll = main ? Mth.sin(clock * 1.4F) * 18 * lift : -10;
            }
            case TIME_FRAME -> {
                float lift = smooth(age / 4F);
                x = Mth.lerp(lift, .35F, .22F);
                y += .1F * lift;
                z -= .08F * lift;
                roll = (main ? -14 : 14) * lift;
            }
            case TIME_THROW -> {
                float whip = smooth((age - 1) / 3F);
                y += .26F * (1 - whip);
                pitch += -38 * (1 - whip);
                z -= .1F * whip;
            }
            case TIME_DRAW -> {
                float draw = smooth(age / 3F);
                x = Mth.lerp(draw, -.12F, .5F);
                y = Mth.lerp(draw, -.46F, -.32F);
                yaw = Mth.lerp(draw, 32, -26);
                roll = Mth.lerp(draw, 40, -30);
            }
            case TIME_TURN -> {
                float flick = smooth((age - 6) / 3F);
                x = Mth.lerp(flick, .26F, .34F);
                y = Mth.lerp(flick, -.3F, -.37F);
                z -= .14F * flick;
                roll = Mth.sin(clock * 1.4F) * 18 * (1 - flick);
            }
            default -> { }
        }
        stack.translate(side * x, y, z);
        stack.mulPose(Axis.YP.rotationDegrees(side * yaw));
        stack.mulPose(Axis.ZP.rotationDegrees(side * roll));
        stack.mulPose(Axis.XP.rotationDegrees(pitch));
    }

    /**
     * Density's skill gestures in first person. Hands stay at the edges of the view; the idle hand drops away unless both
     * work together (the brace, the burst, closing the ground, the fade, the drop).
     */
    private static void density(PoseStack stack, HumanoidArm arm, float side, ArmPose pose, float age, float clock) {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        boolean main = player == null ? arm == HumanoidArm.RIGHT : arm == player.getMainArm();
        float x = .35F, y = -.39F, z = -.41F, pitch = -86, yaw = 0, roll = 0;
        boolean both = pose == ArmPose.DENSITY_BRACE || pose == ArmPose.DENSITY_BURST || pose == ArmPose.DENSITY_CLOSE
                || pose == ArmPose.DENSITY_FADE || pose == ArmPose.DENSITY_DROP;
        if (!main && !both) {
            y -= .5F; pitch += 55;
        } else switch (pose) {
            case DENSITY_LUNGE -> {
                float turn = smooth((age - 5) / 3F);
                y -= .32F * (1 - turn);
                pitch += 45 * (1 - turn);
                z -= .08F * turn;
            }
            case DENSITY_USHER -> {
                x = .3F;
                z -= .1F;
                pitch -= 4;
            }
            case DENSITY_BRACE -> {
                float set = smooth(age / 3F), shake = Mth.sin(clock * 2.6F) * .006F;
                x = .44F + shake;
                y = Mth.lerp(set, -.45F, -.58F);
                pitch = -62;
                roll = -12 * set;
            }
            case DENSITY_BURST -> {
                float k = smooth(age / 2F);
                x = Mth.lerp(k, .44F, .62F);
                y = Mth.lerp(k, -.58F, -.72F);
                pitch = -62 + 20 * k;
                roll = -40 * k;
            }
            case DENSITY_PRESS -> {
                float press = smooth((age - 1) / 4F);
                y = Mth.lerp(press, -.22F, -.42F);
                z -= .12F * press;
                pitch = Mth.lerp(press, -112, -58);
            }
            case DENSITY_CLOSE -> {
                float rise = smooth(age / 3F), press = smooth((age - 3) / 4F);
                x = Mth.lerp(press, .35F, .16F);
                y = Mth.lerp(press, -.39F + .2F * rise, -.46F);
                pitch = Mth.lerp(press, -86 - 25 * rise, -52);
                roll = 18 * press;
            }
            case DENSITY_FADE -> {
                float open = smooth(age / 4F);
                x = Mth.lerp(open, .35F, .52F);
                y = -.5F;
                pitch = -72;
                roll = (Mth.sin(clock * .25F) * 8 - 14) * open;
            }
            case DENSITY_DROP -> {
                float up = smooth(age / 2F);
                x = .46F;
                y = Mth.lerp(up, -.39F, -.12F);
                pitch = Mth.lerp(up, -86, -128);
            }
            case DENSITY_GRASP -> {
                float thrust = smooth(age / 2F);
                x = .28F;
                y = Mth.lerp(thrust, -.39F, -.1F);
                z -= .12F * thrust;
                pitch = Mth.lerp(thrust, -86, -140);
            }
            case DENSITY_SLIP -> {
                float k = smooth(age / 2F), settle = smooth((age - 7) / 5F);
                x = Mth.lerp(k * (1 - settle), .35F, .06F);
                yaw = 22 * k * (1 - settle);
                roll = 30 * k * (1 - settle);
            }
            default -> { }
        }
        stack.translate(side * x, y, z);
        stack.mulPose(Axis.YP.rotationDegrees(side * yaw));
        stack.mulPose(Axis.ZP.rotationDegrees(side * roll));
        stack.mulPose(Axis.XP.rotationDegrees(pitch));
    }

    private static void electro(PoseStack stack, float side, ArmPose pose, float age) {
        float spread = .38F, height = -.43F, depth = -.43F, pitch = -90, yaw = 0;
        switch (pose) {
            case ELECTRO_DRAW -> {
                float draw = smooth(age / 5);
                spread -= .13F * draw;
                depth += .13F * draw;
                pitch += 27 * draw;
                yaw = -17 * draw;
            }
            case ELECTRO_CAST -> {
                float recover = smooth((age - 3) / 8);
                depth -= .13F * (1 - recover);
                pitch += 21 * recover;
            }
            case ELECTRO_STREAM -> {
                height += .016F * Mth.sin(age * 2.4F);
                yaw = -9;
            }
            case ELECTRO_CALL -> {
                float lift = smooth(age / 9);
                height += .22F * lift;
                pitch += 41 * lift;
            }
            case ELECTRO_COIL -> {
                float gather = smooth(age / 18);
                spread -= .16F * gather;
                height += .10F * gather;
                yaw = -30;
                pitch += 28 * gather;
            }
            case ELECTRO_RELEASE -> {
                float spreadOut = 1 - smooth((age - 3) / 9);
                spread += .29F * spreadOut;
                yaw = 37 * spreadOut;
            }
            default -> { }
        }
        stack.translate(side * spread, height, depth);
        stack.mulPose(Axis.YP.rotationDegrees(side * yaw));
        stack.mulPose(Axis.XP.rotationDegrees(pitch));
    }

    /**
     * Electrokinesis's and Superspeed's skill gestures. One-armed gestures keep the off hand low at the edge of
     * the view, so the casting hand reads clearly against the crosshair.
     */
    private static void skills(PoseStack stack, HumanoidArm arm, float side, ArmPose pose, float time, float age) {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        boolean main = player == null || arm == player.getMainArm();
        float spread = .4F, height = -.42F, depth = -.4F, pitch = -90, yaw = 0, roll = 0;
        switch (pose) {
            case ELECTRO_RIDE -> {
                // Both hands whipped down and back past the edges of the view.
                float sweep = smooth(age / 1.5F);
                spread += .14F * sweep; height -= .32F * sweep; depth += .1F * sweep;
                pitch = -90 - 70 * sweep; yaw = -20 * sweep;
            }
            case ELECTRO_CUP -> {
                // Cupped together just below the crosshair, then pushed out.
                float push = smooth((age - 3) / 5), settle = smooth((age - 9) / 6);
                spread = .26F + .05F * push + .12F * settle; height = -.38F - .06F * settle; depth = -.38F - .14F * push;
                yaw = 12 - 8 * push; roll = side * (1 - push) * 8;
            }
            case ELECTRO_LANCE -> {
                float kick = 1 - smooth(age / 6);
                if (main) { spread = .3F; height = -.34F + .04F * kick; depth = -.5F + .06F * kick; pitch = -92 + 12 * kick; yaw = 7; }
                else { spread = .12F; height = -.5F; depth = -.42F; pitch = -70; yaw = 36; }
            }
            case ELECTRO_TOUCH -> {
                float reach = smooth(age / 4);
                if (main) { spread = .34F; height = -.42F - .14F * reach; depth = -.38F - .14F * reach;
                    pitch = -90 - 30 * reach + Mth.sin(time * 3.1F) * 1.5F; }
                else { spread = .55F; height = -.62F; pitch = -55; }
            }
            case ELECTRO_OVERCHARGE -> {
                // Fists low at the edges of the view, shaking, then flexed outward.
                float flood = smooth(age / 10), flex = smooth((age - 10) / 5);
                float shake = Mth.sin(time * 3.7F) * (.006F + .014F * flood) * (1 - flex);
                spread = .46F + .1F * flex + shake; height = -.56F + .04F * flex + shake; depth = -.32F;
                pitch = -62 - 14 * flex; yaw = -14 * flex;
            }
            case ELECTRO_GROUND -> {
                float drive = smooth((age - 2) / 4);
                if (main) { spread = .3F; height = -.16F - .5F * drive; depth = -.42F; pitch = -50 - 95 * drive; }
                else { spread = .6F; height = -.5F; pitch = -70; yaw = -25; }
            }
            case ELECTRO_POINT -> {
                float flick = (float) Math.sin(Math.min(1, age / 4) * Math.PI);
                if (main) { spread = .28F; height = -.36F; depth = -.5F - .05F * flick; pitch = -92 + 8 * flick; yaw = 6; }
                else { spread = .56F; height = -.62F; pitch = -50; }
            }
            case MIMIC_TOUCH -> {
                // The palm pressed forward, flat, into the person in front of you.
                float reach = smooth(age / 3);
                if (main) { spread = .26F; height = -.38F; depth = -.3F - .2F * reach; pitch = -95; yaw = 8; roll = -side * 70; }
                else { spread = .22F; height = -.58F; depth = -.28F; pitch = -55; yaw = 40; }
            }
            case SHIFT_STUDY -> {
                // The hand raised to the temple, at the top edge of the view; the other low at the side.
                float raise = smooth(age / 4), tap = Mth.sin(age * .9F) * .01F * raise;
                if (main) { spread = .44F; height = Mth.lerp(raise, -.42F, -.08F) + tap; depth = -.3F; pitch = Mth.lerp(raise, -90, -18); yaw = -12 * raise; }
                else { spread = .5F; height = -.66F; pitch = -50; yaw = 20; }
            }
            case SHIFT_SWEEP -> {
                // The palm raised in front of the eyes and drawn down across the view.
                float raise = smooth(age / 3), sweep = smooth((age - 3) / 8);
                if (main) { spread = Mth.lerp(raise, .4F, .16F); height = Mth.lerp(sweep, Mth.lerp(raise, -.42F, .02F), -.78F); depth = -.34F;
                    pitch = Mth.lerp(sweep, Mth.lerp(raise, -90, -15), -125); roll = -side * 25 * raise * (1 - sweep); }
                else { spread = .6F; height = -.7F; pitch = -40; yaw = 15; }
            }
            case SHIFT_LUNGE -> {
                // Thrown straight out toward the crosshair as it lengthens, then drawn back.
                float out = smooth(age / 2) * (1 - smooth((age - 6) / 3));
                if (main) { spread = Mth.lerp(out, .42F, .22F); height = Mth.lerp(out, -.5F, -.34F); depth = -.3F - .3F * out;
                    pitch = Mth.lerp(out, -60, -90); yaw = 4; roll = -side * 20 * out; }
                else { spread = .62F; height = -.76F; pitch = -40; yaw = 20; }
            }
            case SHIFT_TOUCH -> {
                // The palm pressed forward, flat, against the creature.
                float reach = smooth(age / 3);
                if (main) { spread = Mth.lerp(reach, .42F, .26F); height = Mth.lerp(reach, -.52F, -.4F); depth = -.32F - .2F * reach;
                    pitch = Mth.lerp(reach, -70, -86); yaw = 6; roll = -side * 80 * reach; }
                else { spread = .62F; height = -.76F; pitch = -40; yaw = 20; }
            }
            case SHIFT_SHED -> {
                // Both hands drop away to the lower corners as the shoulders roll back, then return.
                float back = smooth(age / 3) * (1 - smooth((age - 7) / 6));
                spread = .5F + .2F * back; height = -.6F - .25F * back; depth = -.28F + .1F * back;
                pitch = -50 + 30 * back; yaw = 20 * back;
            }
            case SHIFT_REFLECT -> {
                // The palm raised flat at the edge of the view, fingers up, like a pane held toward the attacker.
                float raise = smooth(age / 3) * (1 - smooth((age - 12) / 4));
                if (main) { spread = Mth.lerp(raise, .44F, .3F); height = Mth.lerp(raise, -.56F, -.3F); depth = -.4F;
                    pitch = Mth.lerp(raise, -60, -12); yaw = -8 * raise; roll = side * 6 * raise; }
                else { spread = .6F; height = -.7F; pitch = -40; yaw = 20; }
            }
            case SHIFT_NIGHTMARE -> {
                // Both arms flung out to the sides of the view and up.
                float fling = smooth(age / 4) * (1 - smooth((age - 16) / 6));
                spread = Mth.lerp(fling, .48F, .78F); height = Mth.lerp(fling, -.56F, -.18F); depth = -.34F;
                pitch = Mth.lerp(fling, -55, -20); roll = -side * 40 * fling;
            }
            case SHIFT_GUARD -> {
                // Forearms crossed low in front of the view as the plates lock on.
                float cross = smooth(age / 3) * (1 - smooth((age - 12) / 4));
                spread = Mth.lerp(cross, .5F, .12F); height = Mth.lerp(cross, -.6F, -.42F); depth = -.34F;
                pitch = Mth.lerp(cross, -50, -80); yaw = 35 * cross; roll = -side * 30 * cross;
            }
            case SHIFT_GIFT -> {
                // Both palms turned up and out at the lower edges of the view.
                float open = smooth(age / 4) * (1 - smooth((age - 12) / 4));
                spread = Mth.lerp(open, .5F, .62F); height = Mth.lerp(open, -.62F, -.5F); depth = -.36F;
                pitch = Mth.lerp(open, -50, -75); roll = side * 70 * open;
            }
            case SHIFT_SLAM -> {
                // Both fists raised to the top edge, then driven down out of the view.
                float raise = smooth(age / 3), drive = smooth((age - 3) / 3);
                spread = .32F; height = Mth.lerp(drive, Mth.lerp(raise, -.6F, -.1F), -.9F); depth = -.34F;
                pitch = Mth.lerp(drive, Mth.lerp(raise, -50, -10), -120);
            }
            case MIMIC_SHIFT -> {
                // Drawn in to the chest, then opened out to the side.
                float draw = smooth(age / 5), open = smooth((age - 5) / 5);
                if (main) { spread = .38F - .2F * draw + .28F * open; height = -.52F + .06F * draw; depth = -.3F;
                    pitch = -60 - 15 * draw + 10 * open; yaw = 30 * draw - 45 * open; }
                else { spread = .55F; height = -.66F; pitch = -50; }
            }
            case SPEED_GUARD -> {
                // Fists up either side of the crosshair.
                float bounce = Mth.sin(time * .9F) * .012F;
                spread = .26F; height = -.22F + bounce; depth = -.36F; pitch = -38; yaw = 28; roll = side * 8;
            }
            case SPEED_SPLIT -> {
                float flicker = age < 12 ? Mth.sin(time * 6.5F) * .05F * (1 - age / 12) : 0;
                spread = .66F + flicker; height = -.56F; depth = -.3F; pitch = -60; yaw = -34;
            }
            case SPEED_LUNGE -> {
                float drive = smooth(age / 2);
                if (main) { spread = .24F; height = -.34F; depth = -.3F - .26F * drive; pitch = -92; yaw = 10; }
                else { spread = .56F; height = -.6F; depth = -.2F; pitch = -55; }
            }
            case SPEED_THROW -> {
                // Cocked out at the top corner of the view, then whipped down through the centre.
                float whip = smooth((age - 1) / 2.5F), follow = smooth((age - 4) / 6);
                if (main) { spread = .62F - .28F * whip; height = -.1F - .34F * whip - .1F * follow; depth = -.22F - .28F * whip;
                    pitch = -30 - 65 * whip + 22 * follow; yaw = -32 + 38 * whip; }
                else { spread = .3F; height = -.38F - .2F * whip; depth = -.46F; pitch = -90 + 35 * whip; }
            }
            case SPEED_ESCORT -> {
                if (main) { spread = .62F; height = -.34F; depth = -.36F; pitch = -82; yaw = -40; }
                else { spread = .5F; height = -.62F + Mth.sin(time * 1.2F) * .02F; pitch = -50; }
            }
            case SPEED_STILL -> {
                float settle = smooth(age / 6);
                spread = .5F + .05F * settle; height = -.58F; depth = -.34F; pitch = -60 - 10 * settle; yaw = -10 * settle;
            }
            // Sonic Resonance. A cry is a scream, so its arms leave the centre of the view to the voice.
            case SONIC_CRY -> {
                float snap = smooth(age / 2), settle = smooth((age - 6) / 3);
                float k = snap * (1 - .4F * settle);
                spread = .4F + .22F * k; height = -.42F - .18F * k; depth = -.4F + .08F * k; pitch = -90 + 30 * k; yaw = -25 * k;
            }
            case SONIC_SCREAM -> {
                float shake = Mth.sin(time * 4.1F) * .012F, open = smooth(age / 3);
                spread = .4F + .26F * open + shake; height = -.42F - .2F * open + shake; pitch = -90 + 35 * open; yaw = -30 * open;
            }
            case SONIC_PLUCK -> {
                float flick = smooth(age / 2), follow = smooth((age - 3) / 5);
                if (main) { spread = .3F + .25F * flick; height = -.36F; depth = -.5F; pitch = -92 + 10 * follow; yaw = 6 - 20 * flick; }
                else { spread = .56F; height = -.62F; pitch = -50; }
            }
            case SONIC_CLENCH -> {
                float pull = smooth((age - 2) / 4);
                if (main) { spread = .3F; height = -.36F; depth = -.52F + .1F * pull; pitch = -92 + 12 * pull; yaw = 6; roll = -side * 20 * pull; }
                else { spread = .56F; height = -.62F; pitch = -50; }
            }
            case SONIC_WALL -> {
                float raise = smooth(age / 3);
                spread = .32F; height = -.42F + .12F * raise; depth = -.42F - .08F * raise; pitch = -92; yaw = 8; roll = -side * 60 * raise;
            }
            case SONIC_SHOVE -> {
                float drive = smooth((age - 1) / 2.5F);
                spread = .32F - .08F * drive; height = -.42F + .1F * drive; depth = -.3F - .24F * drive; pitch = -90; yaw = 8; roll = -side * 60;
            }
            case SONIC_VAULT -> {
                float fling = smooth(age / 2), settle = smooth((age - 8) / 4);
                float k = fling * (1 - .5F * settle);
                spread = .4F + .25F * k; height = -.42F + .3F * k; depth = -.4F + .1F * k; pitch = -90 + 55 * k; yaw = -30 * k;
            }
            case SONIC_DIVE -> {
                spread = .62F; height = -.7F + Mth.sin(time * 2.3F) * .01F; depth = -.3F; pitch = -40; yaw = -20;
            }
            case SONIC_GATHER -> {
                float built = smooth(age / 60), tremble = Mth.sin(time * 3.3F) * .008F * built;
                spread = Mth.lerp(built, .66F, .2F) + tremble; height = Mth.lerp(built, -.4F, -.5F) + tremble;
                depth = Mth.lerp(built, -.4F, -.34F); pitch = Mth.lerp(built, -70, -80); yaw = Mth.lerp(built, -30, 30);
            }
            case SONIC_RELEASE -> {
                float fling = smooth(age / 2), settle = smooth((age - 9) / 6);
                float k = fling * (1 - .5F * settle);
                spread = .2F + .5F * k; height = -.5F - .1F * k; depth = -.34F; pitch = -80 + 25 * k; yaw = 30 - 70 * k;
            }
            case SONIC_CUP -> {
                float lift = smooth(age / 3);
                // Raised toward the mouth but kept low at the edge, so the needle leaves the crosshair clear.
                if (main) { spread = .34F; height = -.42F + .02F * lift; depth = -.42F - .04F * lift; pitch = -88; yaw = 8 * lift; roll = side * 12 * lift; }
                else { spread = .56F; height = -.64F; pitch = -50; }
            }
            case SONIC_SNAP -> {
                float raise = smooth(age / 2.5F), snap = smooth((age - 3) / 2);
                if (main) { spread = .4F + .15F * raise - .25F * snap; height = -.42F + .3F * raise - .24F * snap;
                    depth = -.3F - .2F * snap; pitch = -90 + 50 * raise - 52 * snap; yaw = -10 * raise + 16 * snap; }
                else { spread = .56F; height = -.62F; pitch = -50; }
            }
            case SONIC_HOWL -> {
                float spreadOut = smooth(age / 3), shake = Mth.sin(time * 3.7F) * .01F;
                spread = .4F + .24F * spreadOut + shake; height = -.42F - .16F * spreadOut; pitch = -90 + 32 * spreadOut; yaw = -30 * spreadOut;
            }
            case SONIC_LISTEN -> {
                float lift = smooth(age / 4);
                if (main) { spread = .4F + .3F * lift; height = -.42F + .34F * lift; depth = -.4F + .2F * lift; pitch = -90 + 70 * lift; yaw = -60 * lift; }
                else { spread = .55F; height = -.5F; depth = -.45F; pitch = -88; roll = -side * 70 * lift; }
            }
            case SONIC_HUM -> {
                float cross = smooth(age / 4), breath = Mth.sin(time * .35F) * .01F;
                spread = .4F - .3F * cross; height = -.42F - .14F * cross + breath; depth = -.34F; pitch = -90 + 20 * cross; yaw = 45 * cross;
                if (!main) height -= .05F;
            }
            case SONIC_HUSH -> {
                float lift = smooth(age / 4);
                if (main) { spread = .4F - .32F * lift; height = -.42F + .12F * lift; depth = -.4F + .18F * lift; pitch = -90 + 70 * lift; yaw = 40 * lift; }
                else { spread = .5F; height = -.62F; pitch = -60; roll = -side * 60 * lift; }
            }
            case SONIC_CONDUCT -> {
                float raise = smooth(age / 4);
                float beat = age < 4 || age > 20 ? 0 : Mth.sin(((age - 4) % 4) / 4 * Mth.PI);
                spread = .5F; height = -.42F + .32F * raise - .22F * beat; depth = -.4F; pitch = -90 + 50 * raise - 35 * beat; yaw = -12;
            }
            case SONIC_DOME -> {
                float press = smooth(age / 12);
                spread = .38F; height = -.3F - .25F * press; depth = -.42F; pitch = -92; roll = -side * 80;
            }
            // Quake. The beam and the palms stay just off the crosshair; everything else keeps to the edges of the view.
            case QUAKE_PALM, QUAKE_PALM_OFF -> {
                boolean thrusting = main == (pose == ArmPose.QUAKE_PALM);
                float drive = smooth(age / 1.5F), recoil = smooth((age - 2) / 3);
                if (thrusting) { spread = .32F; height = -.36F + .04F * recoil; depth = -.4F - .18F * drive + .06F * recoil;
                    pitch = -92 + 10 * recoil; yaw = 8; roll = -side * 70 * drive; }
                else { spread = .58F; height = -.62F; pitch = -50; }
            }
            case QUAKE_TUNE, QUAKE_CLENCH -> {
                float pull = pose == ArmPose.QUAKE_CLENCH ? smooth((age - 1) / 3) : 0;
                float tremble = pose == ArmPose.QUAKE_TUNE ? Mth.sin(time * 4.3F) * .008F : 0;
                if (main) { spread = .3F + tremble; height = -.36F + tremble; depth = -.52F + .12F * pull; pitch = -92 + 10 * pull;
                    yaw = 6; roll = -side * 25 * pull; }
                else { spread = .2F; height = -.5F; depth = -.42F; pitch = -80; yaw = 30; }
            }
            case QUAKE_PUNCH_DOWN -> {
                float drive = smooth((age - 1) / 2);
                if (main) { spread = .3F; height = -.16F - .5F * drive; depth = -.42F; pitch = -50 - 95 * drive; }
                else { spread = .6F; height = -.6F; pitch = -60; yaw = -20; }
            }
            case QUAKE_PLUNGE -> { spread = .5F; height = -.05F; depth = -.4F; pitch = -20; yaw = -20; }
            case QUAKE_UPHEAVAL -> {
                float lift = smooth(age / 3);
                spread = .5F; height = -.62F + .5F * lift; pitch = -60 + 40 * lift; yaw = -15; roll = side * 50 * lift;
            }
            case QUAKE_BEAM -> {
                float shake = Mth.sin(time * 4.3F) * .008F;
                spread = .26F + shake; height = -.38F + shake; depth = -.5F; pitch = -92; yaw = 10; roll = -side * 60;
            }
            case QUAKE_FLY -> { spread = .6F; height = -.75F; depth = -.25F; pitch = -30; yaw = -25; }
            case QUAKE_RUMBLE -> {
                float shake = Mth.sin(time * 4.3F) * .01F;
                spread = .62F + shake; height = -.62F + shake; pitch = -58; yaw = -30; roll = -side * 60;
            }
            case QUAKE_AFTERSHOCK -> {
                float slam = smooth((age - 1) / 2);
                spread = .45F; height = Mth.lerp(slam, -.05F, -.7F); pitch = Mth.lerp(slam, -30, -140); yaw = -10;
            }
            case QUAKE_FEEL -> {
                float reach = smooth(age / 4);
                if (main) { spread = .3F; height = -.42F - .2F * reach; depth = -.45F; pitch = -90 - 50 * reach; roll = -side * 60 * reach; }
                else { spread = .6F; height = -.66F; pitch = -50; }
            }
            case QUAKE_FRACTURED -> {
                float hurt = Mth.sin(time * 6.1F) * .015F;
                spread = .2F + hurt; height = -.65F + hurt; depth = -.3F; pitch = -55; yaw = 40;
            }
            case QUAKE_UPPERCUT -> {
                float rise = smooth((age - 1) / 2);
                if (main) { spread = .25F; height = Mth.lerp(rise, -.75F, -.22F); depth = -.42F; pitch = Mth.lerp(rise, -50, -100); yaw = 8; }
                else { spread = .3F; height = -.42F; pitch = -70; yaw = 30; }
            }
            case QUAKE_TRACE -> {
                float sweep = smooth(age / 12);
                if (main) { spread = Mth.lerp(sweep, .55F, .1F); height = -.62F; depth = -.45F; pitch = -125; roll = -side * 70; }
                else { spread = .6F; height = -.66F; pitch = -50; }
            }
            case QUAKE_SHELL -> {
                float fling = smooth((age - 3) / 2);
                spread = Mth.lerp(fling, .1F, .7F); height = -.42F - .03F * fling; pitch = -90 + 10 * fling; yaw = Mth.lerp(fling, 45, -45);
                roll = -side * 80 * fling;
            }
            case QUAKE_BRACE -> {
                spread = .12F; height = main ? -.36F : -.4F; depth = -.38F; pitch = -75; yaw = 50; roll = side * 10;
            }
            case QUAKE_RELEASE -> {
                float drive = smooth(age / 2);
                spread = .12F + .2F * drive; height = -.38F; depth = -.3F - .25F * drive; pitch = -92; yaw = 50 - 40 * drive; roll = -side * 60 * drive;
            }
            case QUAKE_DASH -> {
                float fling = smooth(age / 2);
                spread = .4F + .2F * fling; height = -.42F - .38F * fling; pitch = -90 + 80 * fling; yaw = -30 * fling;
            }
            case QUAKE_TOUCH -> {
                float reach = smooth(age / 3);
                if (main) { spread = .3F; height = -.4F; depth = -.4F - .15F * reach; pitch = -95; roll = -side * 70; }
                else { spread = .58F; height = -.62F; pitch = -50; }
            }
            case QUAKE_SPLIT -> {
                float apart = smooth((age - 2) / 6);
                spread = Mth.lerp(apart, .25F, .7F); height = -.68F; pitch = -130; roll = -side * 40;
            }
            case QUAKE_RAMPART -> {
                float lift = smooth(age / 4);
                spread = .45F; height = Mth.lerp(lift, -.7F, -.25F); pitch = Mth.lerp(lift, -130, -70); roll = side * 50;
            }
            case QUAKE_ROOT -> { spread = .58F; height = -.66F; pitch = -55; yaw = -15; }
            // Regeneration: the giving hand stays below the crosshair, everything else at the edges.
            case REGEN_MEND -> {
                float flex = smooth((age - 6) / 4);
                spread = Mth.lerp(flex, .15F, .6F); height = Mth.lerp(flex, -.55F, -.6F); pitch = -60 + 10 * flex; yaw = Mth.lerp(flex, 40, -25);
            }
            case REGEN_OFFER, REGEN_STREAM -> {
                float tremble = pose == ArmPose.REGEN_STREAM ? Mth.sin(time * 4.1F) * .006F : 0;
                if (main) { spread = .32F + tremble; height = -.42F + tremble; depth = -.48F; pitch = -88; roll = side * 70; }
                else { spread = .2F; height = -.5F; depth = -.42F; pitch = -80; yaw = 30; }
            }
            case REGEN_BRACE -> { spread = .62F; height = -.66F; pitch = -50; yaw = -25; }
            case REGEN_LEAP -> { spread = .55F; height = -.05F; pitch = -20; yaw = -20; }
            case REGEN_LAND -> {
                float slam = smooth(age / 2);
                spread = .45F; height = Mth.lerp(slam, -.1F, -.75F); pitch = Mth.lerp(slam, -30, -140);
            }
            case REGEN_EMBRACE -> { spread = .66F; height = -.42F; depth = -.42F; pitch = -88; yaw = -35; }
            case REGEN_PAYBACK -> {
                float fling = smooth((age - 3) / 2);
                spread = Mth.lerp(fling, .12F, .7F); height = -.42F; pitch = -88; yaw = Mth.lerp(fling, 45, -45); roll = -side * 70 * fling;
            }
            case REGEN_INJECT -> {
                float drive = smooth(age / 3);
                if (main) { spread = .28F; height = -.42F; depth = -.35F - .2F * drive; pitch = -92; roll = -side * 30; }
                else { spread = .58F; height = -.62F; pitch = -50; }
            }
            case REGEN_FLING -> {
                float sweep = smooth(age / 3);
                if (main) { spread = Mth.lerp(sweep, .05F, .6F); height = -.36F; pitch = -90; yaw = Mth.lerp(sweep, 40, -30); roll = -side * 40; }
                else { spread = .58F; height = -.62F; pitch = -50; }
            }
            case REGEN_DRAW -> {
                float pull = smooth((age - 2) / 8);
                if (main) { spread = Mth.lerp(pull, .3F, .15F); height = -.42F; depth = Mth.lerp(pull, -.55F, -.3F); pitch = -90; roll = -side * 70; }
                else { spread = .58F; height = -.62F; pitch = -50; }
            }
            case REGEN_PUMP -> {
                float shake = Mth.sin(time * 4.1F) * .008F;
                spread = .6F + shake; height = -.2F + shake; pitch = -40; yaw = -30;
            }
            case REGEN_HARDEN -> {
                float shake = Mth.sin(time * 4.1F) * .008F;
                spread = .6F + shake; height = -.66F; pitch = -50;
            }
            case REGEN_RISE -> {
                if (main) { spread = .35F; height = -.7F; pitch = -140; }
                else { spread = .6F; height = -.2F; pitch = -40; yaw = -20; }
            }
            case REGEN_COMPRESS -> {
                float pump = Mth.abs(Mth.sin(time * .9F)) * .05F;
                spread = .14F; height = -.55F + pump; depth = -.42F; pitch = -120; yaw = 30;
            }
            case REGEN_HEARTBEAT -> {
                float beat = Mth.abs(Mth.sin(age * .6F)) * .04F;
                if (main) { spread = .12F; height = -.55F + beat; pitch = -60; yaw = 50; }
                else { spread = .58F; height = -.62F; pitch = -50; }
            }
            case REGEN_DEFY -> { spread = .65F; height = -.5F; pitch = -80; yaw = -40; roll = -side * 70; }
            case REGEN_BLOOM -> {
                float lift = smooth(age / 6);
                spread = .4F; height = Mth.lerp(lift, -.6F, -.2F); pitch = Mth.lerp(lift, -90, -40); roll = side * 60;
            }
            // Telepathy: hands go to the temples at the top corners and gestures stay low or at the edges, so the
            // crosshair (and the mind being read) is never covered.
            case TELE_READ -> {
                float lift = smooth(age / 4), tremble = Mth.sin(time * 2.1F) * .004F;
                if (main) { spread = .5F; height = Mth.lerp(lift, -.42F, -.13F) + tremble; depth = -.32F; pitch = Mth.lerp(lift, -90, -28); yaw = -22 * lift; }
                else { spread = .34F; height = -.46F; depth = -.4F - .1F * lift; pitch = -88; yaw = 6; }
            }
            case TELE_SUGGEST -> {
                float press = smooth((age - 3) / 8);
                if (main) { spread = .36F; height = -.4F - .09F * press; depth = -.46F; pitch = -96 + 20 * press; yaw = 6; roll = -side * 12; }
                else { spread = .56F; height = -.64F; pitch = -50; }
            }
            case TELE_LINK -> {
                float open = smooth((age - 8) / 6);
                spread = .5F + .14F * open; height = Mth.lerp(open, -.13F, -.45F); depth = -.32F;
                pitch = Mth.lerp(open, -28, -72); yaw = Mth.lerp(open, -22, -42);
            }
            case TELE_PUPPET -> {
                float raise = smooth(age / 4), twitch = Mth.sin(time * 1.7F + (main ? 0 : 1.5F)) * .012F;
                spread = main ? .4F : .5F; height = -.42F + .14F * raise + twitch; depth = -.46F;
                pitch = -90 - 14 * raise + twitch * 300; yaw = main ? 4 : -6;
            }
            case TELE_NIGHTMARE -> {
                float draw = smooth(age / 6), fling = smooth((age - 6) / 3);
                if (main) { spread = .3F + .12F * fling; height = -.2F - .28F * draw + .12F * fling; depth = -.3F - .25F * fling;
                    pitch = Mth.lerp(fling, -40 - 40 * draw, -95); yaw = 14 * (1 - fling); }
                else { spread = .56F; height = -.64F; pitch = -50; }
            }
            case TELE_COMMAND -> {
                float raise = smooth(age / 1.5F), snap = smooth((age - 2) / 1.5F);
                if (main) { spread = .38F + .12F * raise - .16F * snap; height = -.42F + .26F * raise - .2F * snap;
                    depth = -.32F - .22F * snap; pitch = -90 + 48 * raise - 50 * snap; yaw = 12 * snap; }
                else { spread = .56F; height = -.62F; pitch = -50; }
            }
            case TELE_LISTEN -> {
                float lift = smooth(age / 4), breath = Mth.sin(time * .3F) * .005F;
                spread = .52F; height = Mth.lerp(lift, -.42F, -.11F) + breath; depth = -.3F; pitch = Mth.lerp(lift, -90, -24); yaw = -26 * lift;
            }
            case TELE_WEAVE -> {
                float turn = age * .45F + (main ? 0 : Mth.PI);
                spread = .3F + Mth.cos(turn) * .05F; height = -.5F + Mth.sin(turn) * .05F; depth = -.4F; pitch = -80 + Mth.sin(turn) * 8; yaw = 15;
            }
            case TELE_PERSUADE -> {
                float reach = smooth(age / 5), sway = Mth.sin(time * .2F) * .006F;
                if (main) { spread = .32F; height = -.4F + sway; depth = -.4F - .1F * reach; pitch = -92; roll = -side * 22 * reach; }
                else { spread = .2F; height = -.62F; depth = -.3F; pitch = -40; yaw = 40; }
            }
            case TELE_MEMORY -> {
                float sweep = smooth(age / 18);
                if (main) { spread = Mth.lerp(sweep, .5F, .08F); height = -.36F; depth = -.42F; pitch = -88; yaw = Mth.lerp(sweep, -10, 30); roll = -side * 30; }
                else { spread = .56F; height = -.64F; pitch = -50; }
            }
            case TELE_HIVE -> {
                float raise = smooth(age / 5), open = smooth((age - 8) / 6);
                spread = .4F + .25F * open; height = -.42F + .12F * raise - .08F * open; depth = -.38F; pitch = -80 - 10 * raise; yaw = -38 * open; roll = side * 50 * raise;
            }
            case TELE_STORM -> {
                float lift = smooth(age / 4), thrust = smooth((age - 6) / 3);
                spread = .52F - .14F * thrust; height = Mth.lerp(lift, -.42F, -.12F) - .16F * thrust; depth = -.3F - .24F * thrust;
                pitch = Mth.lerp(thrust, Mth.lerp(lift, -90, -26), -108); yaw = Mth.lerp(thrust, -24 * lift, 6);
            }
            case TELE_VEIL -> {
                float sweep = smooth(age / 8);
                if (main) { spread = Mth.lerp(sweep, .52F, .1F); height = -.3F; depth = -.36F; pitch = -70; yaw = Mth.lerp(sweep, -20, 35); }
                else { spread = .58F; height = -.62F; pitch = -55; }
            }
            case TELE_GATHER -> {
                float clasp = smooth(age / 5), open = smooth((age - 8) / 6);
                spread = Mth.lerp(open, .4F - .2F * clasp, .62F); height = -.48F + .06F * open; depth = -.36F; pitch = -80; yaw = Mth.lerp(open, 30 * clasp, -40);
            }
            case SONIC_SING -> {
                float open = smooth(age / 5), sway = Mth.sin(time * .25F) * .01F;
                spread = .4F + .2F * open + sway; height = -.42F - .08F * open; depth = -.36F; pitch = -90 + 15 * open; yaw = -30 * open; roll = side * 60 * open;
            }
            default -> { }
        }
        stack.translate(side * spread, height, depth);
        stack.mulPose(Axis.YP.rotationDegrees(side * yaw));
        stack.mulPose(Axis.XP.rotationDegrees(pitch));
        if (roll != 0) stack.mulPose(Axis.ZP.rotationDegrees(roll));
    }

    private static void flight(PoseStack stack, float side, ArmPose pose, float age) {
        float spread = .42F, height = -.42F, depth = -.42F, pitch = -91, yaw = 5, roll = 6;
        switch (pose) {
            case FLIGHT_COIL -> {
                float draw = smooth(age / 17);
                spread = .46F - draw * .17F;
                depth += .14F * draw;
                pitch += 28 * draw;
                yaw -= 26 * draw;
                roll += 18 * draw;
            }
            case FLIGHT_BRACE -> {
                float thrust = smooth(age / 3);
                depth -= .21F * thrust;
                height += .07F * thrust;
            }
            case FLIGHT_ROLL -> {
                float tuck = Mth.sin(Mth.PI * Mth.clamp(age / 12, 0, 1));
                spread -= .12F * tuck;
                depth += .12F * tuck;
                yaw -= 32 * tuck;
                roll += 22 * tuck;
            }
            case FLIGHT_CLAP -> {
                float close = smooth(age / 8);
                spread = .63F - .34F * close;
                depth -= .13F * close;
                yaw = 25 - 48 * close;
            }
            case FLIGHT_CAST -> {
                float sweep = Mth.sin(Mth.PI * Mth.clamp(age / 10, 0, 1));
                depth -= .19F * sweep;
                yaw = 24 - 31 * sweep;
            }
            default -> { }
        }
        stack.translate(side * spread, height, depth);
        stack.mulPose(Axis.YP.rotationDegrees(side * yaw));
        stack.mulPose(Axis.XP.rotationDegrees(pitch));
        stack.mulPose(Axis.ZP.rotationDegrees(side * roll));
    }

    private static void cryo(PoseStack stack, float side, ArmPose pose, float age) {
        float spread = .46F, height = -.48F, depth = -.42F, pitch = -85, yaw = side * 9, roll = side * 5;
        switch (pose) {
            case CRYO_LANCE -> {
                float draw=smooth(age/10F),release=smooth((age-11)/4F);
                spread=side>0?.43F+.1F*draw:.29F;
                height=side>0?-.36F: -.48F;
                depth=side>0?-.32F+.18F*draw-.37F*release:-.58F;
                pitch=side>0?-65-20*release:-92;
                yaw=side*(side>0?20*draw-28*release:-10);roll=side>0?13-19*release:-8;
            }
            case CRYO_GUARD -> {
                float fold=smooth(age/8F),open=smooth((age-8)/8F);
                spread=.48F-.22F*fold+.14F*open;depth=-.34F-.12F*open;
                height=-.43F;pitch=-70-13*open;yaw=side*(-28*fold+38*open);roll=side*(16-8*open);
            }
            case CRYO_SNAP -> {
                float pull=smooth(age/4F),snap=smooth((age-4)/4F);
                spread=.4F+.15F*snap;height=-.32F-.3F*snap;
                depth=-.32F+.06F*pull-.22F*snap;pitch=-64-66*snap;roll=side*(8+19*snap);
            }
            case CRYO_LOOM -> {
                float weave=(float)Math.sin(age*.45+side*Math.PI/2);
                spread=.41F+weave*.07F;height=-.48F+weave*.04F;depth=-.46F-weave*.09F;
                pitch=-111+weave*12;yaw=side*(16+weave*9);roll=side*(10+weave*9);
            }
            case CRYO_SHELTER -> {
                float cradle=smooth(age/12F);spread=.54F-cradle*.19F;height=-.53F+cradle*.1F;
                depth=-.45F;pitch=-113+cradle*24;yaw=side*(24-cradle*18);roll=side*(18-cradle*8);
            }
            case CRYO_STARS -> {
                float spreadStars=smooth(age/14F);float turn=(float)Math.sin(age*.2)*3;
                spread=.29F+.31F*spreadStars;height=-.4F+.1F*spreadStars;
                depth=-.49F;pitch=-80+turn;yaw=side*(-15+45*spreadStars);roll=side*(7+24*spreadStars);
            }
            case CRYO_WEAVE -> {
                float brace = smooth(age / 3F);
                float tremor = (float) Math.sin(age * .72 + side * 1.5);
                spread = .43F - brace * .03F;
                height = -.49F + brace * .10F + tremor * .01F;
                depth = -.39F - brace * .15F;
                pitch = -101 + brace * 11 + tremor * 1.5F;
                yaw = side * 5;
                roll = side * 8;
            }
            case CRYO_SCULPT -> {
                float lift = smooth(age / 18F);
                height = -.58F + lift * .13F; spread = .35F + lift * .25F;
                pitch = -120 + lift * 40; yaw = side * (-8 + lift * 30);
            }
            case CRYO_PRISON -> {
                float close = smooth(age / 14F);
                spread = .58F - close * .18F; depth = -.49F;
                pitch = -92 + close * 10; yaw = side * (20 - close * 12);
            }
            case CRYO_SHATTER -> {
                float snap = smooth(age / 4F), recover = smooth((age - 5) / 7F);
                spread = .36F + snap * .28F - recover * .12F;
                depth -= snap * .12F - recover * .1F; roll = side * (5 + snap * 22 - recover * 16);
            }
            case CRYO_WHITEOUT -> {
                float rise = smooth(age / 35F);
                spread = .44F + rise * .2F; height += rise * .10F;
                pitch = -88 + rise * 20; yaw = side * (8 + rise * 15);
            }
            case CRYO_SURF -> {
                height = side > 0 ? -.52F : -.42F; spread = side > 0 ? .4F : .62F;
                depth = side > 0 ? -.55F : -.35F; pitch = side > 0 ? -112 : -80;
                roll = side * 20;
            }
            default -> { }
        }
        stack.translate(side * spread, height, depth);
        stack.mulPose(Axis.YP.rotationDegrees(yaw));
        stack.mulPose(Axis.XP.rotationDegrees(pitch));
        stack.mulPose(Axis.ZP.rotationDegrees(roll));
    }

    /** Invisibility's force fields: the hands hold up a lens, thrust it, sweep out a span, seal and crush a bubble. */
    private static void force(PoseStack stack, float side, ArmPose pose, float age) {
        float spread = .36F, height = -.42F, depth = -.4F, yaw = side * -4, pitch = -88, roll = side * 12;
        switch (pose) {
            case FORCE_SHIELD -> {
                float open = smooth(age / 4F);
                spread = .3F + .06F * open; depth = -.38F - .08F * open; yaw = side * (-2 - 6 * open); roll = side * (10 + 18 * open);
                height += Mth.sin(age * .2F) * .01F;
            }
            case FORCE_PUSH -> {
                float draw = smooth(age / 2F) * (1 - smooth((age - 2) / 2F)), recover = smooth((age - 6) / 6F);
                spread = .32F; depth = -.5F + .22F * draw + .1F * recover; pitch = -92 + 14 * draw + 8 * recover; roll = side * 20;
            }
            case FORCE_BUILD -> {
                float sweep = smooth(age / 10F);
                spread = .5F - .12F * sweep; height = -.62F + .2F * sweep; pitch = -58 - 30 * sweep; yaw = side * (10 - 14 * sweep);
            }
            case FORCE_BUBBLE -> {
                float open = smooth(age / 6F);
                if (side > 0) { spread = .3F; height = -.38F; depth = -.46F - .06F * open; pitch = -90; roll = side * (8 + 20 * open); }
                else { spread = .52F; height = -.6F; depth = -.3F; pitch = -66; yaw = side * 6; }
            }
            case FORCE_CRUSH -> {
                float close = smooth(age / 8F), shake = Mth.sin(age * 2.1F) * .006F * close;
                spread = .38F - .12F * close + shake; yaw = side * (-6 - 14 * close); roll = side * (12 + 30 * close); depth = -.44F;
            }
            case FORCE_NOVA -> {
                float gather = smooth(age / 12F), fling = smooth((age - 20) / 3F);
                spread = Mth.lerp(fling, .36F - .14F * gather, .64F);
                yaw = side * Mth.lerp(fling, -4 - 22 * gather, 34);
                pitch = Mth.lerp(fling, -88 + 8 * gather, -66);
                height = Mth.lerp(fling, -.42F - .04F * gather, -.34F);
            }
            default -> { }
        }
        stack.translate(side * spread, height, depth);
        stack.mulPose(Axis.YP.rotationDegrees(yaw));
        stack.mulPose(Axis.XP.rotationDegrees(pitch));
        stack.mulPose(Axis.ZP.rotationDegrees(roll));
    }

    private static void optical(PoseStack stack, float side, ArmPose pose, float age) {
        float open = smooth(age / (pose == ArmPose.OPTICAL_DISPERSE ? 9F : 16F));
        float spread = .36F + open * .26F;
        float height = -.48F + (float) Math.sin(open * Math.PI) * .035F;
        float yaw = side * (8 + open * 17);
        if (pose == ArmPose.OPTICAL_VEIL) {
            spread = .40F + open * .22F;
            height -= open * .06F;
        }
        if (pose == ArmPose.OPTICAL_DISPERSE) {
            spread = .28F + open * .42F;
            yaw = side * (-12 + open * 40);
        }
        stack.translate(side * spread, height, -.38F);
        stack.mulPose(Axis.YP.rotationDegrees(yaw));
        stack.mulPose(Axis.XP.rotationDegrees(-87 + open * 8));
        stack.mulPose(Axis.ZP.rotationDegrees(side * (4 + open * 12)));
    }

    private static void fire(PoseStack stack, float side, ArmPose pose, float age) {
        float spread = .43F, height = -.4F, depth = -.25F, pitch = -82, yaw = side * 5, roll = side * 4;
        switch (pose) {
            case FIRE_LASH -> {
                float wind = smooth(age / 5F), snap = smooth((age - 5) / 3F), recover = smooth((age - 10) / 6F);
                spread += .16F * wind - .1F * snap;
                depth += .12F * wind - .35F * snap + .16F * recover;
                pitch += 24 * wind - 38 * snap + 20 * recover;
                yaw += side * (26 * wind - 34 * snap + 10 * recover);
                roll += side * 12 * wind;
            }
            case FIRE_FORGE -> {
                float gather = smooth(age / 12F), pulse = (float) Math.sin(age * .3) * .012F;
                spread = .4F - gather * .035F;
                height = -.42F + gather * .04F + pulse;
                pitch = -65 - gather * 15;
                yaw = side * 18;
                roll = side * 12;
            }
            case FIRE_RELEASE -> {
                float thrust = 1 - smooth((age - 4) / 8F);
                depth -= thrust * .31F;
                pitch = -58 - thrust * 36;
                spread -= thrust * .055F;
            }
            case FIRE_BREATH -> {
                float pressure = (float) Math.sin(age * .85 + side) * .012F;
                depth -= .03F + pressure;
                height -= .04F;
                pitch = -65 + pressure * 160;
                yaw = side * 13;
            }
            case FIRE_SHEPHERD -> {
                float pull = .5F + .5F * (float) Math.sin(age * .3 + (side > 0 ? 0 : Math.PI));
                spread += .12F * (1 - pull);
                depth -= .18F * (1 - pull);
                pitch = -65 - 25 * (1 - pull);
                yaw = side * (8 + 20 * pull);
                roll = side * 12;
            }
            case FIRE_CAUTERIZE -> {
                float open = smooth((age - 6) / 14F);
                spread = .44F + open * .23F;
                depth = -.4F - open * .03F;
                height = -.5F + open * .03F;
                pitch = -88 + open * 8;
                yaw = side * (6 + open * 12);
                roll = side * (12 + open * 15);
            }
            case FIRE_SUN -> {
                float lift = smooth(age / 45F);
                height = -.45F + lift * .35F;
                spread += lift * .06F;
                pitch = -75 + lift * 45;
                yaw = side * 12;
                roll = side * (10 + lift * 8);
            }
            case FIRE_SWEEP -> {
                float sweep = smooth(age / 12F);
                spread = .35F + sweep * .25F;
                height = -.55F + sweep * .16F;
                depth = -.18F - sweep * .17F;
                pitch = -125 + sweep * 40;
                yaw = side * (-15 + sweep * 38);
            }
            case FIRE_DIVE -> {
                float tuck = smooth(age / 5F), plunge = smooth((age - 6) / 4F);
                spread = .44F - tuck * .15F + plunge * .22F;
                height = -.42F + tuck * .08F - plunge * .12F;
                depth = -.27F - tuck * .08F + plunge * .19F;
                pitch = -74 - tuck * 15 - plunge * 27;
                yaw = side * (9 + tuck * 18 - plunge * 22);
            }
            default -> { }
        }
        stack.translate(side * spread, height, depth);
        stack.mulPose(Axis.YP.rotationDegrees(yaw));
        stack.mulPose(Axis.XP.rotationDegrees(pitch));
        stack.mulPose(Axis.ZP.rotationDegrees(roll));
    }

    /**
     * Firebending in first person. A punching fist drives in toward the crosshair and back while the other stays
     * raised at the edge of the view; kicks swing the arms for balance; a sweep plants both hands low; a dodge throws
     * the arms wide; a backflip flings them up out of sight.
     */
    private static void firebend(PoseStack stack, float side, boolean main, ArmPose pose, float age) {
        float spread = .43F, height = -.4F, depth = -.25F, pitch = -82, yaw = side * 5, roll = side * 4;
        switch (pose) {
            case FIRE_JAB, FIRE_CROSS -> {
                boolean striking = main == (pose == ArmPose.FIRE_CROSS);
                float punch = smooth(age / 1.5F) * (1 - smooth((age - 4) / 4F));
                if (striking) {
                    spread = .4F - .2F * punch;
                    height = -.42F + .08F * punch;
                    depth = -.22F - .38F * punch;
                    pitch = -78 - 10 * punch;
                    yaw = side * (6 + 12 * punch);
                } else {
                    // The guard stays low at the edge of the view, out of the line of fire.
                    spread = .47F;
                    height = -.5F;
                    depth = -.27F;
                    pitch = -76;
                    yaw = side * 10;
                    roll = side * 10;
                }
            }
            case FIRE_KICK -> {
                float swing = (float) Math.sin(Math.PI * Math.clamp(age / 10F, 0, 1));
                if (main) {
                    spread = .5F + .18F * swing;
                    height = -.45F - .2F * swing;
                    pitch = -82 + 38 * swing;
                    yaw = side * (5 - 18 * swing);
                } else {
                    spread = .4F - .12F * swing;
                    height = -.38F + .04F * swing;
                    depth = -.3F;
                    yaw = side * (5 + 24 * swing);
                    roll = side * 10;
                }
            }
            case FIRE_SWEEP_KICK -> {
                float plant = smooth(age / 2F) * (1 - smooth((age - 10) / 4F));
                spread = .42F + .06F * plant;
                height = -.4F - .24F * plant;
                depth = -.25F - .08F * plant;
                pitch = -82 - 45 * plant;
                yaw = side * (5 + 6 * plant);
            }
            case FIRE_STEP -> {
                float wide = smooth(age / 1.5F) * (1 - smooth((age - 5) / 5F));
                spread = .43F + .25F * wide;
                height = -.4F - .04F * wide;
                pitch = -82 + 22 * wide;
                yaw = side * (5 - 30 * wide);
                roll = side * (4 + 20 * wide);
            }
            case FIRE_FLIP -> {
                float up = smooth(age / 2.5F) * (1 - smooth((age - 9) / 4F));
                height = -.4F + .55F * up;
                spread = .43F + .1F * up;
                pitch = -82 + 55 * up;
                roll = side * (4 + 10 * up);
            }
            default -> { }
        }
        stack.translate(side * spread, height, depth);
        stack.mulPose(Axis.YP.rotationDegrees(yaw));
        stack.mulPose(Axis.XP.rotationDegrees(pitch));
        stack.mulPose(Axis.ZP.rotationDegrees(roll));
    }

    private static float smooth(float value) {
        float t = Math.clamp(value, 0, 1);
        return t * t * (3 - 2 * t);
    }

    private static void kinetic(PoseStack stack, float side, ArmPose pose, float time, float age) {
        float pull = .5F + .5F * (float) Math.sin(age * .38F + (side > 0 ? 0 : Math.PI));
        float thrust = (float) Math.sin(Math.clamp(age / 9F, 0, 1) * Math.PI);
        float tension = (float) Math.sin(time * 2.3F) * .004F;
        float spread = pose == ArmPose.KINETIC_GATHER ? .48F + pull * .035F : .48F;
        float depth = switch (pose) {
            case KINETIC_GATHER -> -.25F - pull * .08F;
            case KINETIC_RELEASE -> -.24F - thrust * .18F;
            default -> -.25F;
        };
        float pitch = switch (pose) {
            case KINETIC_GATHER -> -70 - pull * 18;
            case KINETIC_RELEASE -> -72 - thrust * 18;
            default -> -82;
        };
        stack.translate(side * spread, -.42F + tension, depth);
        stack.mulPose(Axis.YP.rotationDegrees(side * (pose == ArmPose.KINETIC_GATHER ? 6 + pull * 8 : 8)));
        stack.mulPose(Axis.XP.rotationDegrees(pitch));
        stack.mulPose(Axis.ZP.rotationDegrees(side * (pose == ArmPose.KINETIC_GATHER ? 8 : 4)));
    }
}
