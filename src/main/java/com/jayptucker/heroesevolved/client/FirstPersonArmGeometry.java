package com.jayptucker.heroesevolved.client;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

/** Camera-space shoulder anchors. Each arm remains on its own side of the crosshair. */
public final class FirstPersonArmGeometry {
    private FirstPersonArmGeometry() {}

    public static void pose(PoseStack stack, HumanoidArm arm, ArmPose pose, float time) {
        pose(stack, arm, pose, time, time);
    }

    public static void pose(PoseStack stack, HumanoidArm arm, ArmPose pose, float time, float poseAge) {
        float side = arm == HumanoidArm.RIGHT ? 1 : -1;
        if (switch (pose) {
            case ELECTRO_DRAW, ELECTRO_CAST, ELECTRO_STREAM, ELECTRO_CALL, ELECTRO_COIL, ELECTRO_RELEASE -> true;
            default -> false;
        }) {
            electro(stack, side, pose, poseAge);
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
                float weave = (float)Math.sin(age * .35 + side * 1.5);
                spread += weave * .025F; depth -= .08F + weave * .025F;
                pitch -= weave * 3; roll += side * 6;
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
