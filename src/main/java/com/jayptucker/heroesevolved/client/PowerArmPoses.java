package com.jayptucker.heroesevolved.client;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.entity.CarryAnchorEntity;
import com.jayptucker.heroesevolved.entity.CarryGeometry;
import com.jayptucker.heroesevolved.network.ClientArmPoseState;
import com.jayptucker.heroesevolved.network.ClientFlightVisualState;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Applies power arm poses to the player model. Called from a mixin at the
 * end of {@code PlayerModel#setupAnim}, because vanilla recalculates every
 * limb angle there; poses set any earlier (e.g. in a render event) are lost.
 */
public final class PowerArmPoses {
    private PowerArmPoses() {
    }

    public static void apply(PlayerModel<?> model, LivingEntity entity, float ageInTicks) {
        boolean flying = entity instanceof Player player && !player.onGround()
                && !(player.getVehicle() instanceof CarryAnchorEntity)
                && ClientFlightVisualState.isFlightActive(player.getId());
        boolean flameFlight = entity instanceof Player flamePlayer && !flamePlayer.onGround()
                && ClientFlightVisualState.isPyroFlight(flamePlayer.getId())
                && ClientFlightVisualState.isFlightEnabled(flamePlayer.getId());
        int id = entity.getId();
        boolean hover = entity instanceof Player && !entity.onGround() && !flying && !flameFlight
                && !entity.isPassenger() && ClientFlightVisualState.isFlightEnabled(id);
        if (flying || flameFlight || hover) {
            float flutter = Mth.sin(ageInTicks * .22F) * .025F;
            float spread = ClientFlightVisualState.isDiving(id) ? .045F : .08F;
            float stagger = flameFlight ? .035F : hover ? .14F : .09F;
            if (ClientFlightVisualState.isRolling(id)) stagger = .22F;
            set(model.rightLeg, stagger + flutter, 0, spread);
            set(model.leftLeg, -stagger - flutter, 0, -spread);
            model.rightPants.copyFrom(model.rightLeg);
            model.leftPants.copyFrom(model.leftLeg);
        }
        ArmPose pose = ClientArmPoseState.get(entity.getId());
        if (entity.getVehicle() instanceof CarryAnchorEntity held && held.hold() == CarryGeometry.Hold.ARMS) {
            carried(model, CarryGeometry.side(held.getVehicle()));
            return;
        }
        CarryAnchorEntity carryAnchor = CarryVisuals.anchor(entity);
        if (carryAnchor != null) {
            // Vanilla's empty-hand renderer also calls setupAnim; leave that arm below the view.
            if (CarryVisuals.isLocalFirstPersonCarrier(entity)) return;
            switch (carryAnchor.hold()) {
                case ARMS -> {
                    // One arm under the shoulders, one under the knees, both reaching forward and a little in.
                    set(model.rightArm, -1.05F, -.18F, .06F);
                    set(model.leftArm, -1.05F, .18F, -.06F);
                }
                case CRADLE -> {
                    // Wrapped around something held against the chest.
                    set(model.rightArm, -.95F, -.42F, .05F);
                    set(model.leftArm, -.95F, .42F, -.05F);
                }
                case OVERHEAD -> {
                    set(model.rightArm, -2.95F, .05F, .18F);
                    set(model.leftArm, -2.95F, -.05F, -.18F);
                }
            }
            model.rightSleeve.copyFrom(model.rightArm);
            model.leftSleeve.copyFrom(model.leftArm);
            return;
        }
        boolean iceRide = entity instanceof Player && CryoRideClient.isRiding(entity.getId());
        if (iceRide) {
            float bank = Mth.clamp(Mth.wrapDegrees(entity.getYRot() - entity.yRotO) * .03F, -.25F, .25F);
            set(model.rightLeg, .4F, -.12F, .10F + bank);
            set(model.leftLeg, .55F, .12F, -.10F + bank);
            model.rightPants.copyFrom(model.rightLeg); model.leftPants.copyFrom(model.leftLeg);
        }
        if (pose == null) {
            if (iceRide) {
                set(model.rightArm, -.8F, -.2F, .18F);
                set(model.leftArm, -.35F, .15F, -.65F);
                model.rightSleeve.copyFrom(model.rightArm); model.leftSleeve.copyFrom(model.leftArm);
            } else if (flameFlight) {
                // Both palms push aft beside the hips; the shared Flight body supplies pitch and bank.
                float breath = Mth.sin(ageInTicks * .16F) * .018F;
                set(model.rightArm, .04F + breath, -.025F, .08F);
                set(model.leftArm, .04F - breath, .025F, -.08F);
                model.rightSleeve.copyFrom(model.rightArm);
                model.leftSleeve.copyFrom(model.leftArm);
            } else if (hover) {
                float sway = Mth.sin(ageInTicks * .14F) * .055F;
                set(model.rightArm, -.16F + sway, -.04F, .22F);
                set(model.leftArm, -.16F - sway, .04F, -.22F);
                model.rightSleeve.copyFrom(model.rightArm);
                model.leftSleeve.copyFrom(model.leftArm);
            } else if (flying) {
                // A leading fist and a trailing stabilizer give cruising a readable silhouette.
                float speed = (float) Math.clamp(entity.getDeltaMovement().length() / 2.8, 0, 1);
                float turn = Mth.clamp(Mth.wrapDegrees(entity.getYRot() - entity.yRotO) * .015F, -.25F, .25F);
                float breath = Mth.sin(ageInTicks * .16F) * .018F;
                boolean rightHanded = entity.getMainArm() == HumanoidArm.RIGHT;
                ModelPart leading = rightHanded ? model.rightArm : model.leftArm;
                ModelPart trailing = rightHanded ? model.leftArm : model.rightArm;
                float side = rightHanded ? 1 : -1;
                boolean takeoff = ClientFlightVisualState.isTakingOff(id);
                set(leading, (takeoff ? -2.32F : -2.98F) + breath, side * -.07F, side * (.08F + turn));
                if (ClientFlightVisualState.isPlunging(id))
                    set(trailing, -2.98F - breath, side * .07F, side * (-.08F + turn));
                else
                    set(trailing, .12F - speed * .15F - breath, 0, side * (-.29F + turn));
                model.rightSleeve.copyFrom(model.rightArm);
                model.leftSleeve.copyFrom(model.leftArm);
            }
            return;
        }

        float aimX = -Mth.HALF_PI + model.head.xRot;
        float aimY = model.head.yRot;
        boolean rightHanded = entity.getMainArm() == HumanoidArm.RIGHT;
        ModelPart main = rightHanded ? model.rightArm : model.leftArm;
        // Inward rotation is negative for the right arm and positive for the left.
        float inward = rightHanded ? -1.0F : 1.0F;

        switch (pose) {
            case ELECTRO_DRAW -> {
                float load = smooth(ClientArmPoseState.ageTicks(id) / 5F);
                set(main, aimX + .72F * load, aimY + inward * .30F * load, -.18F * inward);
                set(rightHanded ? model.leftArm : model.rightArm, -.83F, -inward * .32F, .17F * inward);
            }
            case ELECTRO_CAST -> {
                float recover = smooth((ClientArmPoseState.ageTicks(id) - 3F) / 8F);
                set(main, aimX - .14F + .65F * recover, aimY, -.12F * inward);
                set(rightHanded ? model.leftArm : model.rightArm, -.62F, -inward * .27F, .28F * inward);
            }
            case ELECTRO_STREAM -> {
                float twitch = Mth.sin(ageInTicks * 2.4F) * .039F;
                set(main, aimX + twitch, aimY, -.06F * inward);
                set(rightHanded ? model.leftArm : model.rightArm, aimX + .55F - twitch, aimY - inward * .36F, .14F * inward);
            }
            case ELECTRO_CALL -> {
                float lift = smooth(ClientArmPoseState.ageTicks(id) / 9F);
                set(main, aimX - 1.15F * lift, aimY, -.22F * inward);
                set(rightHanded ? model.leftArm : model.rightArm, -.7F - lift, -inward * .21F, .38F * inward);
            }
            case ELECTRO_COIL -> {
                float gather = smooth(ClientArmPoseState.ageTicks(id) / 18F);
                set(model.rightArm, -.95F - .42F * gather, -.68F + .27F * gather, .15F);
                set(model.leftArm, -.95F - .42F * gather, .68F - .27F * gather, -.15F);
            }
            case ELECTRO_RELEASE -> {
                float spread = 1 - smooth((ClientArmPoseState.ageTicks(id) - 3F) / 9F);
                set(model.rightArm, -.97F + .68F * (1 - spread), .39F * spread, .81F * spread);
                set(model.leftArm, -.97F + .68F * (1 - spread), -.39F * spread, -.81F * spread);
            }
            case FLIGHT_COIL -> {
                float gather = smooth(ClientArmPoseState.ageTicks(id) / 17F);
                float reach = -1.17F + .49F * gather;
                set(model.rightArm, reach, -.16F - .39F * gather, .38F - .17F * gather);
                set(model.leftArm, reach, .16F + .39F * gather, -.38F + .17F * gather);
            }
            case FLIGHT_BRACE -> {
                boolean travel = ClientFlightVisualState.isPlunging(id) || ClientFlightVisualState.isBursting(id);
                float pitch = travel ? -2.95F : -1.12F;
                set(model.rightArm, pitch, -.08F, .11F);
                set(model.leftArm, pitch, .08F, -.11F);
            }
            case FLIGHT_ROLL -> {
                float tuck = Mth.sin(ClientFlightVisualState.rollProgress(id) * Mth.PI);
                set(model.rightArm, -.26F - .65F * tuck, -.09F - .35F * tuck, .48F - .34F * tuck);
                set(model.leftArm, -.26F - .65F * tuck, .09F + .35F * tuck, -.48F + .34F * tuck);
            }
            case FLIGHT_CLAP -> {
                float close = smooth(ClientArmPoseState.ageTicks(id) / 8F);
                set(model.rightArm, -.92F - .28F * close, -.52F * close, .95F - .84F * close);
                set(model.leftArm, -.92F - .28F * close, .52F * close, -.95F + .84F * close);
            }
            case FLIGHT_CAST -> {
                float sweep = Mth.sin(Mth.PI * Mth.clamp(ClientArmPoseState.ageTicks(id) / 10F, 0, 1));
                set(main, aimX + .35F - .55F * sweep, aimY, -.20F * inward);
                set(rightHanded ? model.leftArm : model.rightArm, -.52F, -inward * .24F, .34F * inward);
            }
            case CRYO_LANCE -> {
                float age=ClientArmPoseState.ageTicks(entity.getId()),draw=smooth(age/10F),release=smooth((age-11)/4F);
                ModelPart brace=rightHanded?model.leftArm:model.rightArm;
                set(main,-.8F-draw*.25F-release*.6F,aimY+inward*(.7F*draw-.8F*release),-inward*.18F);
                set(brace,aimX+.08F,aimY-inward*.12F,inward*.08F);
            }
            case CRYO_GUARD -> {
                float age=ClientArmPoseState.ageTicks(entity.getId()),fold=smooth(age/8F),open=smooth((age-8)/8F);
                set(model.rightArm,-1.1F,-.12F-.52F*fold+.44F*open,.12F+.35F*open);
                set(model.leftArm,-1.1F,.12F+.52F*fold-.44F*open,-.12F-.35F*open);
            }
            case CRYO_SNAP -> {
                float age=ClientArmPoseState.ageTicks(entity.getId()),pull=smooth(age/4F),snap=smooth((age-4)/4F);
                set(model.rightArm,-1.25F-.25F*pull+1.1F*snap,-.12F,.10F+.32F*snap);
                set(model.leftArm,-1.25F-.25F*pull+1.1F*snap,.12F,-.10F-.32F*snap);
            }
            case CRYO_LOOM -> {
                float weave=Mth.sin(ClientArmPoseState.ageTicks(entity.getId())*.45F)*.18F;
                set(model.rightArm,-1.05F+weave,-.18F+weave*.4F,.18F);
                set(model.leftArm,-1.05F-weave,.18F+weave*.4F,-.18F);
            }
            case CRYO_SHELTER -> {
                float cradle=smooth(ClientArmPoseState.ageTicks(entity.getId())/12F);
                set(model.rightArm,-.7F-cradle*.35F,-.48F+cradle*.13F,.22F);
                set(model.leftArm,-.7F-cradle*.35F,.48F-cradle*.13F,-.22F);
            }
            case CRYO_STARS -> {
                float open=smooth(ClientArmPoseState.ageTicks(entity.getId())/14F);
                set(model.rightArm,aimX+.12F,aimY-.08F-open*.3F,.06F+open*.65F);
                set(model.leftArm,aimX+.12F,aimY+.08F+open*.3F,-.06F-open*.65F);
            }
            case CRYO_WEAVE -> {
                float weave = Mth.sin(ClientArmPoseState.ageTicks(entity.getId()) * .35F) * .07F;
                set(model.rightArm, aimX + .08F + weave, aimY - .12F, .06F);
                set(model.leftArm, aimX + .08F - weave, aimY + .12F, -.06F);
            }
            case CRYO_SCULPT -> {
                float lift = smooth(ClientArmPoseState.ageTicks(entity.getId()) / 18F);
                set(model.rightArm, -.45F - lift * .9F, -.15F - lift * .2F, .12F + lift * .35F);
                set(model.leftArm, -.45F - lift * .9F, .15F + lift * .2F, -.12F - lift * .35F);
            }
            case CRYO_PRISON -> {
                float close = smooth(ClientArmPoseState.ageTicks(entity.getId()) / 14F);
                set(model.rightArm, aimX + .12F, aimY - .42F + close * .3F, .20F - close * .12F);
                set(model.leftArm, aimX + .12F, aimY + .42F - close * .3F, -.20F + close * .12F);
            }
            case CRYO_SHATTER -> {
                float snap = smooth(ClientArmPoseState.ageTicks(entity.getId()) / 4F);
                set(model.rightArm, -1.2F + snap * .5F, -.1F - snap * .25F, .08F + snap * .65F);
                set(model.leftArm, -1.2F + snap * .5F, .1F + snap * .25F, -.08F - snap * .65F);
            }
            case CRYO_WHITEOUT -> {
                float rise = smooth(ClientArmPoseState.ageTicks(entity.getId()) / 35F);
                set(model.rightArm, -.65F - rise * .65F, -.12F, .08F + rise * 1.12F);
                set(model.leftArm, -.65F - rise * .65F, .12F, -.08F - rise * 1.12F);
            }
            case CRYO_SURF -> {
                set(model.rightArm, -.8F, -.2F, .18F);
                set(model.leftArm, -.35F, .15F, -.65F);
            }

            case FORCE_SHIELD -> {
                float age = ClientArmPoseState.ageTicks(entity.getId());
                float open = smooth(age / 4F), sway = Mth.sin(age * .2F) * .03F;
                set(model.rightArm, aimX + .1F - open * .05F + sway, aimY - .12F - open * .16F, .08F + open * .12F);
                set(model.leftArm, aimX + .1F - open * .05F - sway, aimY + .12F + open * .16F, -.08F - open * .12F);
            }
            case FORCE_PUSH -> {
                float age = ClientArmPoseState.ageTicks(entity.getId());
                float draw = smooth(age / 2F) * (1 - smooth((age - 2) / 2F)), recover = smooth((age - 6) / 6F);
                set(model.rightArm, aimX + .55F * draw + .25F * recover, aimY - .1F, .06F + .1F * draw);
                set(model.leftArm, aimX + .55F * draw + .25F * recover, aimY + .1F, -.06F - .1F * draw);
            }
            case FORCE_BUILD -> {
                float sweep = smooth(ClientArmPoseState.ageTicks(entity.getId()) / 10F);
                set(model.rightArm, -.45F - sweep * .55F, -.2F + sweep * .05F, .1F + sweep * .55F);
                set(model.leftArm, -.45F - sweep * .55F, .2F - sweep * .05F, -.1F - sweep * .55F);
            }
            case FORCE_BUBBLE -> {
                float open = smooth(ClientArmPoseState.ageTicks(entity.getId()) / 6F);
                ModelPart off = rightHanded ? model.leftArm : model.rightArm;
                set(main, aimX + .2F * (1 - open), aimY, -inward * (.05F + .1F * open));
                set(off, -.55F - .25F * open, -inward * .25F, inward * .3F);
            }
            case FORCE_CRUSH -> {
                float age = ClientArmPoseState.ageTicks(entity.getId());
                float close = smooth(age / 8F), shake = Mth.sin(age * 2.1F) * .03F * close;
                set(model.rightArm, aimX + .05F + shake, aimY - .35F + close * .3F, .2F - close * .1F);
                set(model.leftArm, aimX + .05F - shake, aimY + .35F - close * .3F, -.2F + close * .1F);
            }
            case FORCE_NOVA -> {
                float age = ClientArmPoseState.ageTicks(entity.getId());
                float gather = smooth(age / 12F), fling = smooth((age - 20) / 3F);
                set(model.rightArm, -1.15F * gather * (1 - fling) - .5F * fling, .5F * gather * (1 - fling), .1F + 1.3F * fling);
                set(model.leftArm, -1.15F * gather * (1 - fling) - .5F * fling, -.5F * gather * (1 - fling), -.1F - 1.3F * fling);
            }
            case OPTICAL_SPLIT, OPTICAL_VEIL, OPTICAL_DISPERSE -> {
                float age = ClientArmPoseState.ageTicks(entity.getId());
                float open = smooth(age / (pose == ArmPose.OPTICAL_DISPERSE ? 9F : 16F));
                float spread = pose == ArmPose.OPTICAL_DISPERSE ? .8F : .45F;
                float height = pose == ArmPose.OPTICAL_VEIL ? -.85F + open * .5F : -1.15F + open * .15F;
                set(model.rightArm, height, -.35F + open * .28F, .06F + open * spread);
                set(model.leftArm, height, .35F - open * .28F, -.06F - open * spread);
            }
            case FIRE_LASH -> {
                float age = ClientArmPoseState.ageTicks(entity.getId());
                float wind = smooth(age / 5F), snap = smooth((age - 5) / 3F), recover = smooth((age - 10) / 6F);
                ModelPart brace = rightHanded ? model.leftArm : model.rightArm;
                set(main, -.45F - wind * .5F - snap * .9F + recover * .5F,
                        aimY + inward * (.7F * wind - .8F * snap + .25F * recover),
                        -inward * (.3F * wind - .22F * snap));
                set(brace, -.55F, -inward * .2F, inward * .2F);
            }
            case FIRE_FORGE -> {
                float age = ClientArmPoseState.ageTicks(entity.getId());
                float gather = smooth(age / 12F), breathe = Mth.sin(age * .3F) * .025F;
                set(model.rightArm, -1.0F - gather * .35F + breathe, -.35F + gather * .12F, .16F);
                set(model.leftArm, -1.0F - gather * .35F - breathe, .35F - gather * .12F, -.16F);
            }
            case FIRE_RELEASE -> {
                float age = ClientArmPoseState.ageTicks(entity.getId());
                float thrust = 1 - smooth((age - 4) / 8F);
                ModelPart brace = rightHanded ? model.leftArm : model.rightArm;
                set(main, aimX + .82F * (1 - thrust), aimY + inward * .08F, -inward * .12F);
                set(brace, -.62F - .25F * thrust, -inward * .31F, inward * .33F);
            }
            case FIRE_BREATH -> {
                float age = ClientArmPoseState.ageTicks(entity.getId());
                float pressure = Mth.sin(age * .60F) * .09F;
                set(model.rightArm, aimX + .48F + pressure, aimY - .31F, .13F);
                set(model.leftArm, aimX + .48F - pressure, aimY + .31F, -.13F);
            }
            case FIRE_SHEPHERD -> {
                float age = ClientArmPoseState.ageTicks(entity.getId());
                float right = .5F + .5F * Mth.sin(age * .3F), left = 1 - right;
                set(model.rightArm, -1.2F + right * .55F, -.45F + right * .3F, .3F);
                set(model.leftArm, -1.2F + left * .55F, .45F - left * .3F, -.3F);
            }
            case FIRE_CAUTERIZE -> {
                float age = ClientArmPoseState.ageTicks(entity.getId());
                float open = smooth((age - 6) / 14F);
                set(model.rightArm, -1.1F + open * .4F, -.65F * (1 - open), .12F + open * 1.0F);
                set(model.leftArm, -1.1F + open * .4F, .65F * (1 - open), -.12F - open * 1.0F);
            }
            case FIRE_SUN -> {
                float age = ClientArmPoseState.ageTicks(entity.getId());
                float lift = smooth(age / 45F), pulse = Mth.sin(age * .24F) * .025F;
                set(model.rightArm, -1.1F - lift * 1.85F + pulse, -.2F * (1 - lift), .18F + lift * .15F);
                set(model.leftArm, -1.1F - lift * 1.85F - pulse, .2F * (1 - lift), -.18F - lift * .15F);
            }
            case FIRE_SWEEP -> {
                float age = ClientArmPoseState.ageTicks(entity.getId());
                float sweep = smooth(age / 12F);
                set(model.rightArm, -.5F - sweep * .7F, -.5F + sweep * .65F, .2F + sweep * .45F);
                set(model.leftArm, -.5F - sweep * .7F, .5F - sweep * .65F, -.2F - sweep * .45F);
            }
            case FIRE_DIVE -> {
                float age = ClientArmPoseState.ageTicks(entity.getId());
                float tuck = smooth(age / 5F), plunge = smooth((age - 6) / 4F);
                set(model.rightArm, -.85F - tuck * .28F + plunge * 1.65F, -.35F + plunge * .26F, .21F);
                set(model.leftArm, -.85F - tuck * .28F + plunge * 1.65F, .35F - plunge * .26F, -.21F);
            }
            case ONE_HAND -> set(main, aimX, aimY + inward * 0.1F, 0.0F);
            case TWO_HANDS, TWO_HANDS_STRAIN -> {
                float tremble = pose == ArmPose.TWO_HANDS_STRAIN
                        ? Mth.sin(ageInTicks * 2.7F) * 0.05F : 0.0F;
                set(model.rightArm, aimX + tremble, aimY - 0.15F, tremble);
                set(model.leftArm, aimX - tremble, aimY + 0.15F, -tremble);
            }
            case WIND_ARMS -> {
                // Both arms straight out along the aim, each hand whipping round a tight circle (the two turning
                // opposite ways), so a funnel of wind spins off each palm.
                float spin = ageInTicks * 1.9F, r = .3F;
                set(model.rightArm, aimX + Mth.sin(spin) * r, aimY - .06F + Mth.cos(spin) * r, 0);
                set(model.leftArm, aimX + Mth.sin(-spin) * r, aimY + .06F + Mth.cos(-spin) * -r, 0);
            }
            case ARMS_RAISED -> {
                set(model.rightArm, -2.9F, 0.0F, 0.25F);
                set(model.leftArm, -2.9F, 0.0F, -0.25F);
            }
            case ARMS_WIDE -> {
                set(model.rightArm, -0.2F, 0.0F, 1.35F);
                set(model.leftArm, -0.2F, 0.0F, -1.35F);
            }
            case TEMPLE -> set(main, -2.35F, inward * 0.1F, inward * 0.55F);
            case GROUND -> {
                set(model.rightArm, -0.55F, -0.15F, 0.1F);
                set(model.leftArm, -0.55F, 0.15F, -0.1F);
            }
            case KINETIC_GATHER -> {
                float pull = .5F + .5F * Mth.sin(ClientArmPoseState.ageTicks(entity.getId()) * .38F);
                set(model.rightArm, -1.32F + pull * .42F, aimY * .35F - .22F, .32F);
                set(model.leftArm, -1.32F + (1 - pull) * .42F, aimY * .35F + .22F, -.32F);
            }
            case KINETIC_HOLD -> {
                float tension = Mth.sin(ageInTicks * 2.3F) * .014F;
                ModelPart brace = rightHanded ? model.leftArm : model.rightArm;
                set(main, aimX + .16F + tension, aimY + inward * .12F, -inward * .12F);
                set(brace, -.92F - tension, aimY * .35F - inward * .25F, inward * .4F);
            }
            case REACH -> {
                // The whole arm driven forward and a little low, the other drawn back for balance.
                ModelPart brace = rightHanded ? model.leftArm : model.rightArm;
                set(main, aimX + .2F, aimY + inward * .05F, 0);
                set(brace, .35F, 0, -inward * .12F);
            }
            case SINK -> {
                set(model.rightArm, -.25F, 0, .55F);
                set(model.leftArm, -.25F, 0, -.55F);
            }
            case KINETIC_RELEASE -> {
                float phase = Mth.clamp(ClientArmPoseState.ageTicks(entity.getId()) / 9F, 0, 1);
                float thrust = Mth.sin(phase * Mth.PI);
                ModelPart brace = rightHanded ? model.leftArm : model.rightArm;
                set(main, aimX + .18F - thrust * .3F, aimY + inward * .06F, -inward * .08F);
                set(brace, aimX + .75F - thrust * .32F, aimY - inward * .3F, inward * .28F);
            }
        }

        // setupAnim copies arms onto sleeves before we run; copy again.
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftSleeve.copyFrom(model.leftArm);
    }

    /**
     * A player carried in someone's arms: the arm on the carrier's side goes up around their neck,
     * the other rests across the body, and the legs fold over the carrier's arm.
     */
    private static void carried(PlayerModel<?> model, int side) {
        ModelPart hug = side > 0 ? model.rightArm : model.leftArm, rest = side > 0 ? model.leftArm : model.rightArm;
        set(hug, -2.1F, side * .25F, side * .9F);
        set(rest, -.55F, side * -.2F, side * .35F);
        set(model.rightLeg, -1.25F, .12F, .04F);
        set(model.leftLeg, -1.1F, -.12F, -.04F);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightPants.copyFrom(model.rightLeg);
        model.leftPants.copyFrom(model.leftLeg);
    }

    private static float smooth(float value) {
        float t = Mth.clamp(value, 0, 1);
        return t * t * (3 - 2 * t);
    }

    private static void set(ModelPart arm, float x, float y, float z) {
        arm.xRot = x;
        arm.yRot = y;
        arm.zRot = z;
    }
}
