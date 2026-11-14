package com.jayptucker.heroesevolved.client;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient;
import com.jayptucker.heroesevolved.cryo.CryoBlastGeometry;
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
import net.minecraft.world.phys.Vec3;

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
        if (entity instanceof Player player) {
            Vec3 frostTarget = SetPieceClient.cryoCastTarget(id);
            if (frostTarget != null) {
                float partial = Mth.clamp(ageInTicks - player.tickCount, 0, 1);
                frostArm(model.rightArm, player, partial, frostTarget, 1);
                frostArm(model.leftArm, player, partial, frostTarget, -1);
                model.rightSleeve.copyFrom(model.rightArm);
                model.leftSleeve.copyFrom(model.leftArm);
                return;
            }
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
            case ELECTRO_RIDE -> {
                // The arms snap from the forward lean straight back past the hips: the body is the bolt.
                float sweep = smooth(ClientArmPoseState.ageTicks(id) / 1.5F);
                float x = -1.0F + 2.35F * sweep + Mth.sin(ageInTicks * 2.9F) * .03F;
                set(model.rightArm, x, 0, .3F + .15F * sweep);
                set(model.leftArm, x, 0, -.3F - .15F * sweep);
            }
            case ELECTRO_CUP -> {
                // Palms cupped together in front of the chest round the forming orb, then pushed out along the aim.
                float age = ClientArmPoseState.ageTicks(id);
                float push = smooth((age - 3F) / 5F), settle = smooth((age - 9F) / 6F);
                float x = Mth.lerp(push, -1.18F, aimX + .05F) + .5F * settle;
                float cup = .52F - .26F * push;
                set(model.rightArm, x, aimY - cup, .04F);
                set(model.leftArm, x, aimY + cup, -.04F);
            }
            case ELECTRO_LANCE -> {
                // The casting arm locked straight along the aim, kicked up by the recoil; the other hand braces it.
                float kick = 1 - smooth(ClientArmPoseState.ageTicks(id) / 6F);
                set(main, aimX - .22F * kick, aimY, -.05F * inward);
                set(rightHanded ? model.leftArm : model.rightArm, aimX + .32F - .1F * kick,
                        aimY - inward * .62F, -.1F * inward);
            }
            case ELECTRO_TOUCH -> {
                // A flat palm laid on the conductor, trembling as the current runs into the metal; the other arm held off.
                float reach = smooth(ClientArmPoseState.ageTicks(id) / 4F);
                float tremor = Mth.sin(ageInTicks * 3.1F) * .025F;
                set(main, Mth.lerp(reach, -.4F, aimX + .25F) + tremor, aimY + inward * .08F, -.1F * inward);
                set(rightHanded ? model.leftArm : model.rightArm, .32F, 0, -.42F * inward);
            }
            case ELECTRO_OVERCHARGE -> {
                // Fists clenched at the sides, arms locked and shaking harder as the current floods in, then flexed out.
                float age = ClientArmPoseState.ageTicks(id);
                float flood = smooth(age / 10F), flex = smooth((age - 10F) / 5F);
                float shake = Mth.sin(ageInTicks * 3.7F) * (.02F + .05F * flood) * (1 - flex);
                set(model.rightArm, -.18F - .35F * flex + shake, 0, .22F + .32F * flex - shake);
                set(model.leftArm, -.18F - .35F * flex - shake, 0, -.22F - .32F * flex + shake);
            }
            case ELECTRO_GROUND -> {
                // Raised overhead, then driven palm-first down at the ground; the other arm thrown out for balance.
                float drive = smooth((ClientArmPoseState.ageTicks(id) - 2F) / 4F);
                set(main, Mth.lerp(drive, -2.85F, -.38F), 0, -.08F * inward);
                set(rightHanded ? model.leftArm : model.rightArm, -.25F - .2F * drive, 0, -.75F * inward);
            }
            case ELECTRO_POINT -> {
                // Two fingers flicked at the surface; the other hand on the hip.
                float flick = Mth.sin(Math.min(1, ClientArmPoseState.ageTicks(id) / 4F) * Mth.PI) * .18F;
                set(main, aimX - flick, aimY, -.05F * inward);
                set(rightHanded ? model.leftArm : model.rightArm, .18F, -inward * .45F, -.55F * inward);
            }
            case MIMIC_TOUCH -> {
                // An open palm laid flat on the person in front of you; the other hand held at the heart.
                float reach = smooth(ClientArmPoseState.ageTicks(id) / 3F);
                set(main, Mth.lerp(reach, -.6F, aimX + .12F), aimY, -.05F * inward);
                set(rightHanded ? model.leftArm : model.rightArm, -1.05F, -inward * .62F, .05F * inward);
            }
            case MIMIC_SHIFT -> {
                // A hand drawn to the heart as the old power goes, then opened outward as the new one answers.
                float age = ClientArmPoseState.ageTicks(id);
                float draw = smooth(age / 5F), open = smooth((age - 5F) / 5F);
                set(main, Mth.lerp(open, -1.15F * draw, -.75F), Mth.lerp(open, inward * .75F * draw, -inward * .2F),
                        Mth.lerp(open, .05F, -.65F * inward));
                set(rightHanded ? model.leftArm : model.rightArm, -.15F, 0, .12F * inward);
            }
            case SPEED_GUARD -> {
                // A boxer's guard, both forearms up at the face, bouncing light on the toes.
                float bounce = Mth.sin(ageInTicks * .9F) * .05F;
                set(model.rightArm, -1.9F + bounce, -.58F, .1F);
                set(model.leftArm, -1.9F - bounce, .58F, -.1F);
            }
            case SPEED_SPLIT -> {
                // Flung low and wide, flickering between places faster than the eye: the mirage tears loose.
                float age = ClientArmPoseState.ageTicks(id);
                float flicker = age < 12 ? Mth.sin(ageInTicks * 6.5F) * .14F * (1 - age / 12F) : 0;
                set(model.rightArm, -.22F + flicker, 0, 1.0F - flicker);
                set(model.leftArm, -.22F - flicker, 0, -1.0F + flicker);
            }
            case SPEED_LUNGE -> {
                // The fist driven straight through along the aim; the other arm thrown back.
                float drive = smooth(ClientArmPoseState.ageTicks(id) / 2F);
                set(main, Mth.lerp(drive, aimX + 1.2F, aimX - .06F), aimY, -.04F * inward);
                set(rightHanded ? model.leftArm : model.rightArm, .85F * drive, 0, -.2F * inward);
            }
            case SPEED_THROW -> {
                // Cocked back over the shoulder, then whipped through like a javelin; the other hand sights the target.
                float age = ClientArmPoseState.ageTicks(id);
                float whip = smooth((age - 1F) / 2.5F), follow = smooth((age - 4F) / 6F);
                set(main, Mth.lerp(whip, -2.75F, aimX + .35F) + .5F * follow, aimY - inward * .25F * (1 - whip),
                        -.25F * inward * (1 - whip));
                set(rightHanded ? model.leftArm : model.rightArm, Mth.lerp(whip, aimX, .4F), aimY, .1F * inward);
            }
            case SPEED_ESCORT -> {
                // One arm reaching out and round the shoulders beside you; the other pumping back.
                float pump = Mth.sin(ageInTicks * 1.2F) * .25F;
                set(main, -1.25F, inward * .2F, -.62F * inward);
                set(rightHanded ? model.leftArm : model.rightArm, .7F + pump, 0, .08F * inward);
            }
            case SPEED_STILL -> {
                // Open hands held a little out from the sides, perfectly still while the world crawls.
                float settle = smooth(ClientArmPoseState.ageTicks(id) / 6F);
                set(model.rightArm, -.32F * settle, .12F * settle, .42F * settle);
                set(model.leftArm, -.32F * settle, -.12F * settle, -.42F * settle);
            }
            case SONIC_CRY, SONIC_SCREAM, SONIC_PLUCK, SONIC_CLENCH, SONIC_WALL, SONIC_SHOVE, SONIC_VAULT, SONIC_DIVE,
                    SONIC_GATHER, SONIC_RELEASE, SONIC_CUP, SONIC_SNAP, SONIC_HOWL, SONIC_LISTEN, SONIC_HUM, SONIC_HUSH,
                    SONIC_CONDUCT, SONIC_DOME, SONIC_SING ->
                    sonic(model, pose, main, rightHanded ? model.leftArm : model.rightArm, inward, aimX, aimY,
                            ClientArmPoseState.ageTicks(id), ageInTicks);
            case QUAKE_PALM, QUAKE_PALM_OFF, QUAKE_TUNE, QUAKE_CLENCH, QUAKE_PUNCH_DOWN, QUAKE_PLUNGE, QUAKE_UPHEAVAL, QUAKE_BEAM,
                    QUAKE_FLY, QUAKE_RUMBLE, QUAKE_AFTERSHOCK, QUAKE_FEEL, QUAKE_FRACTURED, QUAKE_UPPERCUT, QUAKE_TRACE, QUAKE_SHELL,
                    QUAKE_BRACE, QUAKE_RELEASE, QUAKE_DASH, QUAKE_TOUCH, QUAKE_SPLIT, QUAKE_RAMPART, QUAKE_ROOT ->
                    quake(model, pose, main, rightHanded ? model.leftArm : model.rightArm, inward, aimX, aimY,
                            ClientArmPoseState.ageTicks(id), ageInTicks, rightHanded);
            case VIBE_PALM, VIBE_OPEN, VIBE_READ, VIBE_GUARD, VIBE_LOCK, VIBE_LANCE, VIBE_CROSS, VIBE_STEP ->
                    vibe(pose, main, rightHanded ? model.leftArm : model.rightArm, inward, aimX, aimY, ClientArmPoseState.ageTicks(id));
            case TIME_HALT, TIME_SNAP, TIME_PLANT, TIME_RECALL, TIME_WIND, TIME_FRAME, TIME_THROW, TIME_DRAW, TIME_TURN ->
                    time(pose, main, rightHanded ? model.leftArm : model.rightArm, inward, aimX, aimY,
                            ClientArmPoseState.ageTicks(id), ageInTicks);
            case DENSITY_LUNGE, DENSITY_USHER, DENSITY_BRACE, DENSITY_BURST, DENSITY_PRESS, DENSITY_CLOSE, DENSITY_FADE,
                    DENSITY_DROP, DENSITY_GRASP, DENSITY_SLIP ->
                    density(pose, main, rightHanded ? model.leftArm : model.rightArm, inward, aimX, aimY,
                            ClientArmPoseState.ageTicks(id), ageInTicks);
            case REGEN_MEND, REGEN_OFFER, REGEN_STREAM, REGEN_BRACE, REGEN_LEAP, REGEN_LAND, REGEN_EMBRACE, REGEN_PAYBACK,
                    REGEN_INJECT, REGEN_FLING, REGEN_DRAW, REGEN_PUMP, REGEN_HARDEN, REGEN_RISE, REGEN_COMPRESS, REGEN_HEARTBEAT,
                    REGEN_DEFY, REGEN_BLOOM ->
                    regen(pose, main, rightHanded ? model.leftArm : model.rightArm, inward, aimX, aimY,
                            ClientArmPoseState.ageTicks(id), ageInTicks);
            case TELE_READ, TELE_SUGGEST, TELE_LINK, TELE_PUPPET, TELE_NIGHTMARE, TELE_COMMAND, TELE_LISTEN, TELE_WEAVE,
                    TELE_PERSUADE, TELE_MEMORY, TELE_HIVE, TELE_STORM, TELE_VEIL, TELE_GATHER ->
                    telepathy(pose, main, rightHanded ? model.leftArm : model.rightArm, inward, aimX, aimY,
                            ClientArmPoseState.ageTicks(id), ageInTicks);
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
                float age = ClientArmPoseState.ageTicks(entity.getId());
                float brace = smooth(age / 3F), tremor = Mth.sin(age * .72F) * .025F;
                set(model.rightArm, aimX + (1 - brace) * .42F + tremor, aimY + .06F, .04F);
                set(model.leftArm, aimX + (1 - brace) * .42F - tremor, aimY - .06F, -.04F);
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
            case FIRE_JAB, FIRE_CROSS, FIRE_KICK, FIRE_SWEEP_KICK, FIRE_STEP, FIRE_FLIP ->
                    firebend(model, entity, pose, main, rightHanded ? model.leftArm : model.rightArm,
                            rightHanded ? model.rightLeg : model.leftLeg, rightHanded ? model.leftLeg : model.rightLeg,
                            inward, aimX, aimY, ClientArmPoseState.ageTicks(id));
            case SHIFT_STUDY -> {
                // Two fingers at the temple, tapping as the shape is learned; the other arm folded under the elbow.
                float age = ClientArmPoseState.ageTicks(id);
                float raise = smooth(age / 4F), tap = Mth.sin(age * .9F) * .04F * raise;
                set(main, -2.45F * raise + tap, inward * .18F * raise, -inward * .62F * raise);
                set(rightHanded ? model.leftArm : model.rightArm, -1.0F * raise, -inward * .78F * raise, inward * .05F);
            }
            case SHIFT_SWEEP -> {
                // A palm raised before the face and drawn down over it, the plates following the hand.
                float age = ClientArmPoseState.ageTicks(id);
                float raise = smooth(age / 3F), sweep = smooth((age - 3F) / 8F);
                set(main, -2.6F * raise + 2.05F * sweep, inward * .5F * raise * (1 - .4F * sweep), inward * .22F * raise);
                set(rightHanded ? model.leftArm : model.rightArm, -.2F * raise, 0, -inward * .28F * raise);
            }
            case SHIFT_LUNGE -> {
                // The arm thrown straight along the aim as it lengthens; the other drawn back for balance.
                float age = ClientArmPoseState.ageTicks(id);
                float throwOut = smooth(age / 2F) * (1 - smooth((age - 6F) / 3F));
                set(main, Mth.lerp(throwOut, -.4F, aimX), aimY * throwOut, -.04F * inward);
                set(rightHanded ? model.leftArm : model.rightArm, .45F * throwOut, 0, -inward * .2F * throwOut);
            }
            case SHIFT_TOUCH -> {
                // An open palm pressed to the creature; the other hand loose at the side.
                float reach = smooth(ClientArmPoseState.ageTicks(id) / 3F);
                set(main, Mth.lerp(reach, -.6F, aimX + .1F), aimY, -.05F * inward);
                set(rightHanded ? model.leftArm : model.rightArm, -.25F, 0, -inward * .15F);
            }
            case SHIFT_SHED -> {
                // Shoulders rolled back as you step out of a shell: both arms swung back and out, then loose again.
                float age = ClientArmPoseState.ageTicks(id);
                float roll = smooth(age / 3F) * (1 - smooth((age - 7F) / 6F));
                set(model.rightArm, .85F * roll, 0, .35F * roll);
                set(model.leftArm, .85F * roll, 0, -.35F * roll);
            }
            case SHIFT_REFLECT -> {
                // A flat palm raised between you and your attacker, like a mirror held up; the other arm drawn in.
                float age = ClientArmPoseState.ageTicks(id);
                float raise = smooth(age / 3F) * (1 - smooth((age - 12F) / 4F));
                set(main, Mth.lerp(raise, -.3F, aimX - .15F), aimY * raise, inward * .1F * raise);
                set(rightHanded ? model.leftArm : model.rightArm, -.7F * raise, -inward * .5F * raise, 0);
            }
            case SHIFT_GUARD -> {
                // Forearms crossed over the chest as the plates lock on, held, then let go.
                float age = ClientArmPoseState.ageTicks(id);
                float cross = smooth(age / 3F) * (1 - smooth((age - 12F) / 4F));
                set(model.rightArm, -1.35F * cross, -.75F * cross, 0);
                set(model.leftArm, -1.35F * cross, .75F * cross, 0);
            }
            case SHIFT_GIFT -> {
                // Both palms turned out and low, giving a shape away to the friends around.
                float age = ClientArmPoseState.ageTicks(id);
                float open = smooth(age / 4F) * (1 - smooth((age - 12F) / 4F));
                set(model.rightArm, -.55F * open, -.35F * open, .55F * open);
                set(model.leftArm, -.55F * open, .35F * open, -.55F * open);
            }
            case SHIFT_SLAM -> {
                // Both fists raised high and driven down to the ground.
                float age = ClientArmPoseState.ageTicks(id);
                float raise = smooth(age / 3F), drive = smooth((age - 3F) / 3F);
                float x = Mth.lerp(drive, -2.9F * raise, -.3F);
                set(model.rightArm, x, -.2F, .15F);
                set(model.leftArm, x, .2F, -.15F);
            }
            case SHIFT_NIGHTMARE -> {
                // Arms flung wide and up as the nightmare rears, held, then dropped.
                float age = ClientArmPoseState.ageTicks(id);
                float fling = smooth(age / 4F) * (1 - smooth((age - 16F) / 6F)), shake = Mth.sin(age * 1.9F) * .04F * fling;
                set(model.rightArm, -.6F * fling + shake, 0, 1.75F * fling);
                set(model.leftArm, -.6F * fling - shake, 0, -1.75F * fling);
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

    /**
     * Sonic Resonance's gestures. {@code main} is the casting arm and {@code off} the other; an arm swings outward with
     * {@code z = -inward * k} (main) or {@code z = inward * k} (off), and turns in with {@code y = inward * k} (main) or
     * {@code y = -inward * k} (off).
     */
    private static void sonic(PlayerModel<?> model, ArmPose pose, ModelPart main, ModelPart off, float inward,
                              float aimX, float aimY, float age, float ageInTicks) {
        switch (pose) {
            case SONIC_CRY -> {
                // The voice goes out and the arms are thrown back by it, fists low and wide.
                float snap = smooth(age / 2F), settle = smooth((age - 6F) / 3F);
                float back = 1.0F * snap * (1 - .45F * settle), out = .58F * snap;
                set(main, back, 0, -inward * out);
                set(off, back, 0, inward * out);
            }
            case SONIC_SCREAM -> {
                // Braced further back and wider, shaking while the scream lasts.
                float shake = Mth.sin(ageInTicks * 4.1F) * .05F, open = smooth(age / 3F);
                set(main, (.8F + shake) * open, 0, -inward * (.85F - shake) * open);
                set(off, (.8F - shake) * open, 0, inward * (.85F + shake) * open);
            }
            case SONIC_PLUCK -> {
                // A hand flicks out past the aim as if plucking a string; the other hangs loose.
                float flick = smooth(age / 2F), follow = smooth((age - 3F) / 5F);
                set(main, Mth.lerp(flick, -1.0F, aimX + .1F) + .25F * follow, aimY + Mth.lerp(flick, inward * .5F, -inward * .35F), -inward * .1F);
                set(off, -.15F, 0, inward * .1F);
            }
            case SONIC_CLENCH -> {
                // The arm out at the Note, the fist closing and drawing back as it breaks.
                float pull = smooth((age - 2F) / 4F);
                set(main, aimX + .4F * pull, aimY + inward * .1F * pull, 0);
                set(off, -.25F, 0, inward * .15F);
            }
            case SONIC_WALL -> {
                // Both palms raised flat to the front, a little apart: holding the wall up.
                float raise = smooth(age / 3F);
                set(main, Mth.lerp(raise, 0, aimX - .05F), aimY - inward * .26F * raise, 0);
                set(off, Mth.lerp(raise, 0, aimX - .05F), aimY + inward * .26F * raise, 0);
            }
            case SONIC_SHOVE -> {
                // Drawn in to the chest, then a two-handed shove straight along the aim.
                float drive = smooth((age - 1F) / 2.5F);
                set(main, Mth.lerp(drive, -1.0F, aimX - .1F), aimY + Mth.lerp(drive, inward * .6F, -inward * .12F), 0);
                set(off, Mth.lerp(drive, -1.0F, aimX - .1F), aimY + Mth.lerp(drive, -inward * .6F, inward * .12F), 0);
            }
            case SONIC_VAULT -> {
                // Flung up and out as the cry at the ground throws the body away.
                float fling = smooth(age / 2F), settle = smooth((age - 8F) / 4F);
                float up = -2.5F * fling * (1 - .3F * settle), out = .45F * fling;
                set(main, up, 0, -inward * out);
                set(off, up, 0, inward * out);
            }
            case SONIC_DIVE -> {
                // Pinned back along the body, streamlined for the plunge.
                float flutter = Mth.sin(ageInTicks * 2.3F) * .03F;
                set(main, .45F + flutter, 0, -inward * .08F);
                set(off, .45F - flutter, 0, inward * .08F);
            }
            case SONIC_GATHER -> {
                // Raised wide, then drawn in to the chest as the Crescendo builds, trembling more as it fills.
                float built = smooth(age / 60F), tremble = Mth.sin(ageInTicks * 3.3F) * .035F * built;
                float x = Mth.lerp(built, -1.75F, -1.25F) + tremble, out = Mth.lerp(built, 1.0F, 0), in = .7F * built;
                set(main, x, inward * in, -inward * out);
                set(off, x - tremble * 2, -inward * in, inward * out);
            }
            case SONIC_RELEASE -> {
                // Flung wide and back from the chest, the body open as everything goes out.
                float fling = smooth(age / 2F), settle = smooth((age - 9F) / 6F);
                float x = Mth.lerp(fling, -1.25F, .4F) * (1 - .6F * settle), out = 1.3F * fling * (1 - .5F * settle);
                set(main, x, 0, -inward * out);
                set(off, x, 0, inward * out);
            }
            case SONIC_CUP -> {
                // A hand cupped at the mouth to put the whole voice through one point.
                float lift = smooth(age / 3F);
                set(main, -2.05F * lift, inward * .55F * lift, 0);
                set(off, .2F, 0, inward * .12F);
            }
            case SONIC_SNAP -> {
                // A hand raised beside the head, then a snap forward at the point.
                float raise = smooth(age / 2.5F), snap = smooth((age - 3F) / 2F);
                set(main, Mth.lerp(snap, -2.7F * raise, aimX), Mth.lerp(snap, -inward * .15F, aimY), 0);
                set(off, -.2F, 0, inward * .12F);
            }
            case SONIC_HOWL -> {
                // Arms flung low and wide, hands open, shaking with the howl.
                float spread = smooth(age / 3F), shake = Mth.sin(ageInTicks * 3.7F) * .04F;
                set(main, -.35F * spread + shake, 0, -inward * (1.0F * spread - shake));
                set(off, -.35F * spread - shake, 0, inward * (1.0F * spread + shake));
            }
            case SONIC_LISTEN -> {
                // A hand to the ear; the other held out flat, feeling for the echo.
                float lift = smooth(age / 4F);
                set(main, -2.6F * lift, 0, inward * .3F * lift);
                set(off, -1.2F * lift, 0, inward * .7F * lift);
            }
            case SONIC_HUM -> {
                // Arms crossed over the chest, rising and falling with a low hum.
                float breath = Mth.sin(ageInTicks * .35F) * .04F, cross = smooth(age / 4F);
                set(main, (-1.05F + breath) * cross, inward * .85F * cross, 0);
                set(off, (-1.25F - breath) * cross, -inward * .85F * cross, 0);
            }
            case SONIC_HUSH -> {
                // A finger to the lips; the other hand held low, palm down, quieting the air.
                float lift = smooth(age / 4F);
                set(main, -2.0F * lift, inward * .5F * lift, 0);
                set(off, -.55F * lift, 0, inward * .45F * lift);
            }
            case SONIC_CONDUCT -> {
                // A conductor's raised hands, then four downbeats as the waves stack, held up for the burst.
                float raise = smooth(age / 4F);
                float beat = age < 4 || age > 20 ? 0 : Mth.sin(((age - 4F) % 4F) / 4F * Mth.PI);
                float x = -2.55F * raise + 1.3F * beat;
                set(main, x, 0, -inward * .3F);
                set(off, x + .1F, 0, inward * .3F);
            }
            case SONIC_DOME -> {
                // Palms pressed slowly down, flattening the air into a dome.
                float press = smooth(age / 12F);
                float x = Mth.lerp(press, -1.7F, -.75F);
                set(main, x, 0, -inward * .4F);
                set(off, x, 0, inward * .4F);
            }
            case SONIC_SING -> {
                // Arms opened in song, palms up, swaying with the hymn.
                float open = smooth(age / 5F), sway = Mth.sin(ageInTicks * .25F) * .06F;
                set(main, -1.1F * open + sway, -inward * .2F * open, -inward * .8F * open);
                set(off, -1.1F * open - sway, inward * .2F * open, inward * .8F * open);
            }
            default -> { }
        }
    }

    /**
     * Quake's gestures, with the same conventions as {@link #sonic}: {@code main} casts, {@code off} is the other arm; an
     * arm swings outward with {@code z = -inward * k} (main) or {@code z = inward * k} (off).
     */
    private static void quake(PlayerModel<?> model, ArmPose pose, ModelPart main, ModelPart off, float inward,
                              float aimX, float aimY, float age, float ageInTicks, boolean rightHanded) {
        float shake = Mth.sin(ageInTicks * 4.3F) * .035F;
        switch (pose) {
            case QUAKE_PALM, QUAKE_PALM_OFF -> {
                // One palm driven out along the aim; the other drawn back at the hip.
                boolean second = pose == ArmPose.QUAKE_PALM_OFF;
                ModelPart thrust = second ? off : main, back = second ? main : off;
                float sign = second ? -1 : 1;
                float drive = smooth(age / 1.5F), recoil = smooth((age - 2F) / 3F) * .2F;
                set(thrust, Mth.lerp(drive, -1.0F, aimX) - recoil, aimY + sign * inward * .05F, 0);
                set(back, .35F, 0, sign * inward * .12F);
            }
            case QUAKE_TUNE, QUAKE_CLENCH -> {
                // A hand out at the target, fingers spread and trembling; the other grips its wrist. Shatter pulls it closed.
                float pull = pose == ArmPose.QUAKE_CLENCH ? smooth((age - 1F) / 3F) : 0;
                float tremble = pose == ArmPose.QUAKE_TUNE ? shake : 0;
                set(main, aimX + tremble + .5F * pull, aimY, 0);
                set(off, aimX + .3F + .4F * pull, aimY + inward * .45F, 0);
            }
            case QUAKE_PUNCH_DOWN -> {
                // Raised, then a fist driven down into the ground ahead.
                float drive = smooth((age - 1F) / 2F);
                set(main, Mth.lerp(drive, -2.7F, -.45F), 0, -inward * .08F);
                set(off, .4F, 0, inward * .2F);
            }
            case QUAKE_PLUNGE -> {
                // Both fists locked together overhead, ready to hammer the landing.
                set(main, -3.0F + shake, inward * .12F, 0);
                set(off, -3.0F - shake, -inward * .12F, 0);
            }
            case QUAKE_UPHEAVAL -> {
                // Both palms swept up from the ground to overhead, lifting the whole line.
                float lift = smooth(age / 3F);
                set(main, Mth.lerp(lift, -.3F, -2.4F), 0, -inward * .35F);
                set(off, Mth.lerp(lift, -.3F, -2.4F), 0, inward * .35F);
            }
            case QUAKE_BEAM -> {
                // Both palms pressed out along the aim, close together, shaking with the beam.
                set(main, aimX + shake, aimY + inward * .12F, 0);
                set(off, aimX - shake, aimY - inward * .12F, 0);
            }
            case QUAKE_FLY -> {
                // Thrust down and back, palms to the ground: the beam carries her.
                set(main, .55F + shake, 0, -inward * .25F);
                set(off, .55F - shake, 0, inward * .25F);
            }
            case QUAKE_RUMBLE -> {
                // Low and wide, palms down, the whole body trembling with the ground.
                set(main, -.45F + shake, 0, -inward * (.7F + shake));
                set(off, -.45F - shake, 0, inward * (.7F - shake));
            }
            case QUAKE_AFTERSHOCK -> {
                // Both fists brought down from overhead together.
                float slam = smooth((age - 1F) / 2F);
                set(main, Mth.lerp(slam, -2.9F, -.4F), 0, -inward * .2F);
                set(off, Mth.lerp(slam, -2.9F, -.4F), 0, inward * .2F);
            }
            case QUAKE_FEEL -> {
                // A palm laid on the ground ahead, the other hand resting on the knee.
                float reach = smooth(age / 4F);
                set(main, -.75F * reach, 0, 0);
                set(off, -.25F * reach, 0, inward * .12F);
            }
            case QUAKE_FRACTURED -> {
                // Both arms clutched in to the chest, shaking.
                float hurt = Mth.sin(ageInTicks * 6.1F) * .06F;
                set(main, -.85F + hurt, inward * .9F, 0);
                set(off, -1.0F - hurt, -inward * .9F, 0);
            }
            case QUAKE_UPPERCUT -> {
                // A fist from the hip straight up through the target; the other arm guards the face.
                float rise = smooth((age - 1F) / 2F);
                set(main, Mth.lerp(rise, .35F, -2.25F), aimY, 0);
                set(off, -1.2F, -inward * .6F, 0);
            }
            case QUAKE_TRACE -> {
                // A hand trailed low across the ground, writing the fault.
                float sweep = smooth(age / 12F);
                set(main, -.55F, Mth.lerp(sweep, -inward * .5F, inward * .6F), 0);
                set(off, .3F, 0, inward * .15F);
            }
            case QUAKE_SHELL -> {
                // Crossed at the chest, then flung wide, palms out, holding the air apart.
                float fling = smooth((age - 3F) / 2F);
                set(main, Mth.lerp(fling, -1.2F, -1.4F) + shake * fling, Mth.lerp(fling, inward * .9F, 0), -inward * 1.1F * fling);
                set(off, Mth.lerp(fling, -1.25F, -1.4F) - shake * fling, Mth.lerp(fling, -inward * .9F, 0), inward * 1.1F * fling);
            }
            case QUAKE_BRACE -> {
                // Forearms crossed up in front of the face, taking the hits.
                set(main, -1.7F + shake * .5F, inward * .85F, 0);
                set(off, -1.55F - shake * .5F, -inward * .85F, 0);
            }
            case QUAKE_RELEASE -> {
                // Out of the brace, a double-palm push.
                float drive = smooth(age / 2F);
                set(main, Mth.lerp(drive, -1.6F, aimX - .1F), aimY + Mth.lerp(drive, inward * .8F, -inward * .3F), 0);
                set(off, Mth.lerp(drive, -1.6F, aimX - .1F), aimY + Mth.lerp(drive, -inward * .8F, inward * .3F), 0);
            }
            case QUAKE_DASH -> {
                // Palms flung back at the air behind her.
                float fling = smooth(age / 2F);
                set(main, .95F * fling, 0, -inward * .3F * fling);
                set(off, .95F * fling, 0, inward * .3F * fling);
            }
            case QUAKE_TOUCH -> {
                // A palm laid flat on the wall, held still; the other arm loose.
                float reach = smooth(age / 3F);
                set(main, Mth.lerp(reach, -.6F, aimX + .15F), aimY - inward * .15F, 0);
                set(off, -.2F, 0, inward * .1F);
            }
            case QUAKE_SPLIT -> {
                // Both fists driven down, then pulled apart, tearing the ground open between them.
                float apart = smooth((age - 2F) / 6F);
                set(main, -.5F, 0, -inward * Mth.lerp(apart, .1F, .9F));
                set(off, -.5F, 0, inward * Mth.lerp(apart, .1F, .9F));
            }
            case QUAKE_RAMPART -> {
                // Both palms swept up in front, raising the slabs.
                float lift = smooth(age / 4F);
                set(main, Mth.lerp(lift, -.4F, -1.65F), -inward * .35F, 0);
                set(off, Mth.lerp(lift, -.4F, -1.65F), inward * .35F, 0);
            }
            case QUAKE_ROOT -> {
                // A wide, rooted stance: feet apart, fists low and out.
                float settle = smooth(age / 5F);
                set(main, -.2F, 0, -inward * .45F * settle);
                set(off, -.2F, 0, inward * .45F * settle);
                set(rightHanded ? model.rightLeg : model.leftLeg, 0, 0, -inward * .22F * settle);
                set(rightHanded ? model.leftLeg : model.rightLeg, 0, 0, inward * .22F * settle);
            }
            default -> { }
        }
    }

    /** Regeneration's gestures, with the same conventions as {@link #sonic}. */
    private static void regen(ArmPose pose, ModelPart main, ModelPart off, float inward, float aimX, float aimY,
                              float age, float ageInTicks) {
        float shake = Mth.sin(ageInTicks * 4.1F) * .03F;
        switch (pose) {
            case REGEN_MEND -> {
                // Both hands pressed to the wound, then flexed out as it closes.
                float flex = smooth((age - 6F) / 4F);
                set(main, Mth.lerp(flex, -1.0F, -.3F), inward * .8F * (1 - flex), -inward * .5F * flex);
                set(off, Mth.lerp(flex, -1.1F, -.3F), -inward * .8F * (1 - flex), inward * .5F * flex);
            }
            case REGEN_OFFER, REGEN_STREAM -> {
                // The cut palm held out to them; the other hand grips the forearm (and keeps it there while the blood flows).
                float reach = smooth(age / 3F), tremble = pose == ArmPose.REGEN_STREAM ? shake : 0;
                set(main, Mth.lerp(reach, -.5F, aimX + .2F) + tremble, aimY, 0);
                set(off, Mth.lerp(reach, -.5F, aimX + .45F), aimY - inward * .45F, 0);
            }
            case REGEN_BRACE -> {
                // Chest out, arms spread low and back: daring it.
                float open = smooth(age / 4F);
                set(main, .3F * open, 0, -inward * .45F * open);
                set(off, .3F * open, 0, inward * .45F * open);
            }
            case REGEN_LEAP -> {
                // Arms flung up as she throws herself off.
                set(main, -2.9F + shake, 0, -inward * .35F);
                set(off, -2.9F - shake, 0, inward * .35F);
            }
            case REGEN_LAND -> {
                // Fists driven down beside her as she lands.
                float slam = smooth(age / 2F);
                set(main, Mth.lerp(slam, -2.5F, -.3F), 0, -inward * .25F);
                set(off, Mth.lerp(slam, -2.5F, -.3F), 0, inward * .25F);
            }
            case REGEN_EMBRACE -> {
                // Arms opened wide in front, drawing her friends in.
                float open = smooth(age / 5F);
                set(main, -1.3F * open, -inward * .6F * open, 0);
                set(off, -1.3F * open, inward * .6F * open, 0);
            }
            case REGEN_PAYBACK -> {
                // Hands pulled to the chest, then thrown open wide.
                float fling = smooth((age - 3F) / 2F);
                set(main, Mth.lerp(fling, -1.1F, -1.4F), Mth.lerp(fling, inward * .85F, 0), -inward * 1.2F * fling);
                set(off, Mth.lerp(fling, -1.1F, -1.4F), Mth.lerp(fling, -inward * .85F, 0), inward * 1.2F * fling);
            }
            case REGEN_INJECT -> {
                // A hand driven into the friend; the other holds her own elbow where the blood was drawn.
                float drive = smooth(age / 3F);
                set(main, Mth.lerp(drive, -.8F, aimX + .3F), aimY, 0);
                set(off, -.6F, -inward * .9F, 0);
            }
            case REGEN_FLING -> {
                // The cut palm swept across from the far side, flinging the blood out.
                float sweep = smooth(age / 3F);
                set(main, -1.4F, Mth.lerp(sweep, inward * .9F, -inward * .8F), 0);
                set(off, .2F, 0, inward * .15F);
            }
            case REGEN_DRAW -> {
                // A palm on their wound, drawing it in to her own chest.
                float pull = smooth((age - 2F) / 8F);
                set(main, Mth.lerp(pull, aimX, -1.2F), Mth.lerp(pull, aimY, inward * .6F), 0);
                set(off, -.2F, 0, inward * .12F);
            }
            case REGEN_PUMP -> {
                // Both fists up at the shoulders, flexing: the adrenaline hits.
                float lift = smooth(age / 3F);
                set(main, -.3F + shake, 0, -inward * 1.45F * lift);
                set(off, -.3F - shake, 0, inward * 1.45F * lift);
            }
            case REGEN_HARDEN -> {
                // Fists tensed at her sides, trembling as the body learns.
                set(main, .1F + shake, 0, -inward * .25F);
                set(off, .1F - shake, 0, inward * .25F);
            }
            case REGEN_RISE -> {
                // One hand pushing off the ground, the other swung up for balance.
                float push = smooth(age / 4F);
                set(main, -.35F, 0, -inward * .1F);
                set(off, Mth.lerp(push, -.4F, -1.6F), 0, inward * .4F);
            }
            case REGEN_COMPRESS -> {
                // Both hands locked together on their chest, pumping.
                float pump = Mth.abs(Mth.sin(ageInTicks * .9F)) * .25F;
                set(main, -.9F + pump, inward * .35F, 0);
                set(off, -.9F + pump, -inward * .35F, 0);
            }
            case REGEN_HEARTBEAT -> {
                // A fist beaten against the chest, twice.
                float beat = Mth.abs(Mth.sin(age * .6F)) * .2F;
                set(main, -1.2F + beat, inward * .9F, 0);
                set(off, -.2F, 0, inward * .3F);
            }
            case REGEN_DEFY -> {
                // Arms out to the sides, palms open: come and try.
                float open = smooth(age / 4F);
                set(main, -.6F * open, 0, -inward * .9F * open);
                set(off, -.6F * open, 0, inward * .9F * open);
            }
            case REGEN_BLOOM -> {
                // Palms raised up in front as the tree of light grows.
                float lift = smooth(age / 6F);
                set(main, Mth.lerp(lift, -.6F, -2.2F), -inward * .25F, -inward * .15F);
                set(off, Mth.lerp(lift, -.6F, -2.2F), inward * .25F, inward * .15F);
            }
            default -> { }
        }
    }

    private static void vibe(ArmPose pose, ModelPart main, ModelPart off, float inward, float aimX, float aimY, float age) {
        float push = smooth(age / (pose == ArmPose.VIBE_PALM ? 3F : 6F));
        float settle = smooth((age - 10) / 7F);
        switch (pose) {
            case VIBE_PALM -> {
                set(main, aimX + .61F * (1 - push) + .42F * settle, aimY - inward * .09F, inward * (.16F - .10F * push));
                set(off, -.43F + .26F * push, -.14F * inward, -.27F * inward);
            }
            case VIBE_LANCE -> {
                set(main, aimX + .38F * (1 - push) + .32F * settle, aimY, inward * .06F);
                set(off, -.94F - .32F * push, -.30F * inward, .18F * inward);
            }
            case VIBE_CROSS -> {
                set(main, aimX + .42F * (1 - push), aimY + inward * (.36F - .57F * push), inward * .23F);
                set(off, aimX + .48F * (1 - push), aimY - inward * (.36F - .57F * push), -inward * .23F);
            }
            case VIBE_OPEN -> {
                float open = smooth(age / 9F);
                set(main, -1.52F + .22F * settle, inward * (.26F - .79F * open), inward * (.08F + .31F * open));
                set(off, -1.52F + .22F * settle, -inward * (.26F - .79F * open), -inward * (.08F + .31F * open));
            }
            case VIBE_READ -> {
                float sight = smooth((age - 7) / 8F);
                set(main, aimX - .76F * sight, aimY + inward * (.08F + .50F * sight), inward * .13F);
                set(off, -.27F, -.11F * inward, -.22F * inward);
            }
            case VIBE_GUARD -> {
                set(main, -1.33F - .31F * push, -.31F * inward, .35F * inward);
                set(off, -1.21F - .28F * push, .31F * inward, -.37F * inward);
            }
            case VIBE_LOCK -> {
                float fall = smooth((age - 12) / 6F);
                set(main, -1.26F - .86F * push + 1.45F * fall, -.43F * inward, .52F * inward);
                set(off, -1.26F - .86F * push + 1.45F * fall, .43F * inward, -.52F * inward);
            }
            case VIBE_STEP -> {
                set(main, -1.38F + .36F * settle, inward * .18F, .13F * inward);
                set(off, .47F - .29F * settle, 0, -.29F * inward);
            }
            default -> { }
        }
    }

    /**
     * Space-Time's skill gestures, with the same conventions as {@link #telepathy}: {@code main} casts, {@code off} is
     * the other arm; {@code y = inward * k} turns the main arm across the body ({@code -inward} for the other arm).
     */
    private static void time(ArmPose pose, ModelPart main, ModelPart off, float inward, float aimX, float aimY,
                             float age, float ageInTicks) {
        switch (pose) {
            case TIME_HALT -> {
                // A palm thrust out, fingers spread, held as the clock locks, then drawn back a touch into a fist.
                float push = smooth(age / 3F), settle = smooth((age - 8F) / 5F);
                set(main, aimX + .5F * (1 - push) + .15F * settle, aimY, -.05F * inward);
                set(off, .2F, 0, inward * .15F);
            }
            case TIME_SNAP -> {
                // A hand raised beside the head snaps its fingers: a small flick at the third tick.
                float rise = smooth(age / 3F), flick = Mth.sin(Mth.clamp((age - 3F) / 3F, 0, 1) * Mth.PI) * .25F;
                set(main, -2.05F * rise + flick, -inward * .2F, -inward * .3F);
                set(off, .1F, 0, inward * .1F);
            }
            case TIME_PLANT -> {
                // Raised, then pressed palm-first down at the ground ahead; the other arm out for balance.
                float press = smooth((age - 1F) / 5F);
                set(main, Mth.lerp(press, -2.2F, -.55F), inward * .1F, -.05F * inward);
                set(off, -.3F, 0, inward * .6F);
            }
            case TIME_RECALL -> {
                // A hand drawn to the chest as if for a pocket watch, then flicked open out to the side.
                float open = smooth((age - 7F) / 4F);
                set(main, Mth.lerp(open, -1.1F, -1.4F), Mth.lerp(open, inward * .65F, -inward * .2F),
                        Mth.lerp(open, inward * .1F, -inward * .35F));
                set(off, -.2F, 0, inward * .1F);
            }
            case TIME_WIND -> {
                // A hand at the chest turning a crown backward, the other cupped beneath it.
                float lift = smooth(age / 3F), turn = Mth.sin(ageInTicks * 1.4F) * .12F * lift;
                set(main, -1.2F * lift, inward * .55F * lift, turn);
                set(off, -.95F * lift, -inward * .45F * lift, 0);
            }
            case TIME_FRAME -> {
                // Both hands raised before the face, framing a point ahead like a photograph.
                float lift = smooth(age / 4F);
                set(main, Mth.lerp(lift, -.3F, aimX + .1F), aimY - inward * .18F * lift, -inward * .1F * lift);
                set(off, Mth.lerp(lift, -.3F, aimX + .1F), aimY + inward * .18F * lift, inward * .1F * lift);
            }
            case TIME_THROW -> {
                // Raised back, then swept forward along the aim; the other arm swings back.
                float whip = smooth((age - 1F) / 3F);
                set(main, Mth.lerp(whip, -2.6F, aimX + .2F), aimY, -inward * .2F * (1 - whip));
                set(off, Mth.lerp(whip, aimX, .3F), 0, inward * .1F);
            }
            case TIME_DRAW -> {
                // A blade drawn from the opposite hip and slashed out across the body.
                float draw = smooth(age / 3F);
                set(main, Mth.lerp(draw, -.4F, aimX + .1F), Mth.lerp(draw, inward * .9F, -inward * .7F),
                        Mth.lerp(draw, inward * .3F, -inward * .2F));
                set(off, .2F, 0, inward * .2F);
            }
            case TIME_TURN -> {
                // A crown twisted forward at the chest, then flicked out at the target.
                float flick = smooth((age - 6F) / 3F), turn = -Mth.sin(ageInTicks * 1.4F) * .12F * (1 - flick);
                set(main, Mth.lerp(flick, -1.1F, aimX), Mth.lerp(flick, inward * .5F, aimY), turn - .05F * inward * flick);
                set(off, -.25F, 0, inward * .12F);
            }
            default -> { }
        }
    }

    /**
     * Density's skill gestures, with the same conventions as {@link #time}: {@code main} casts, {@code off} is the other
     * arm; {@code z = -inward * k} swings the main arm outward ({@code inward * k} for the other).
     */
    private static void density(ArmPose pose, ModelPart main, ModelPart off, float inward, float aimX, float aimY,
                                float age, float ageInTicks) {
        switch (pose) {
            case DENSITY_LUNGE -> {
                // Shoulder first through the body, both arms trailing; then the main arm comes round as you turn to face it.
                float lunge = smooth(age / 2F), turn = smooth((age - 5F) / 3F);
                set(main, Mth.lerp(turn, .65F * lunge, aimX + .3F), inward * .1F, -inward * .3F * (1 - turn));
                set(off, .65F * lunge * (1 - turn * .6F), -inward * .1F, inward * .3F);
            }
            case DENSITY_USHER -> {
                // One arm leads into the wall; the other waves friends on behind you.
                float wave = Mth.sin(ageInTicks * .9F) * .25F * (1 - smooth((age - 8F) / 4F));
                set(main, aimX + .1F, aimY, -inward * .05F);
                set(off, .45F + wave, -inward * .2F, inward * .65F);
            }
            case DENSITY_BRACE -> {
                // Fists clenched low at the sides, elbows out, a tremor of held weight.
                float set = smooth(age / 3F), shake = Mth.sin(ageInTicks * 2.6F) * .015F;
                set(main, -.3F * set + shake, inward * .15F * set, -inward * .32F * set);
                set(off, -.3F * set - shake, -inward * .15F * set, inward * .32F * set);
            }
            case DENSITY_BURST -> {
                // The weight let go: both arms flung out and down from the brace.
                float k = smooth(age / 2F);
                set(main, Mth.lerp(k, -.35F, .15F), 0, -inward * Mth.lerp(k, .3F, 1.15F));
                set(off, Mth.lerp(k, -.35F, .15F), 0, inward * Mth.lerp(k, .3F, 1.15F));
            }
            case DENSITY_PRESS -> {
                // Raised, then a palm pressed down at the far point; the other arm out for balance.
                float press = smooth((age - 1F) / 4F);
                set(main, Mth.lerp(press, -2.1F, aimX + .25F), aimY, -inward * .05F);
                set(off, -.3F, 0, inward * .55F);
            }
            case DENSITY_CLOSE -> {
                // Both hands raised together, then pressed down and closed, as if shutting a lid.
                float rise = smooth(age / 3F), press = smooth((age - 3F) / 4F);
                float x = Mth.lerp(press, -2F * rise, -.75F);
                set(main, x, inward * Mth.lerp(press, .25F, .55F), inward * .1F);
                set(off, x, -inward * Mth.lerp(press, .25F, .55F), -inward * .1F);
            }
            case DENSITY_FADE -> {
                // Arms loose and open, drifting as if underwater.
                float open = smooth(age / 4F), drift = Mth.sin(ageInTicks * .25F) * .1F;
                set(main, -.35F * open + drift, 0, -inward * (.7F + drift) * open);
                set(off, -.35F * open - drift, 0, inward * (.7F - drift) * open);
            }
            case DENSITY_DROP -> {
                // Arms thrown up overhead as the floor gives way.
                float up = smooth(age / 2F);
                set(main, -2.8F * up, 0, -inward * .3F * up);
                set(off, -2.8F * up, 0, inward * .3F * up);
            }
            case DENSITY_GRASP -> {
                // A hand thrust straight up through the rock, clutching; the other braced down.
                float thrust = smooth(age / 2F), clutch = Mth.sin(Mth.clamp((age - 2F) / 4F, 0, 1) * Mth.PI) * .15F;
                set(main, -3F * thrust + clutch, 0, -inward * .1F);
                set(off, .2F, 0, inward * .25F);
            }
            case DENSITY_SLIP -> {
                // A twist away from the blow: one arm across the body, the other flung back.
                float k = smooth(age / 2F), settle = smooth((age - 7F) / 5F);
                set(main, -1.15F * k * (1 - settle), inward * .9F * k * (1 - settle), inward * .2F * k);
                set(off, .6F * k * (1 - settle), 0, inward * .45F * k);
            }
            default -> { }
        }
    }

    /**
     * Telepathy's gestures, with the same conventions as {@link #sonic}: {@code main} casts, {@code off} is the other arm;
     * an arm swings outward with {@code z = -inward * k} (main) or {@code z = inward * k} (off). The temple is
     * {@code (-2.35, inward * .1, inward * .55)}: the arm raised and turned in until two fingers touch beside the eye.
     */
    private static void telepathy(ArmPose pose, ModelPart main, ModelPart off, float inward, float aimX, float aimY,
                                  float age, float ageInTicks) {
        switch (pose) {
            case TELE_READ -> {
                // Two fingers at the temple, trembling a little; the other hand reaching toward the mind.
                float lift = smooth(age / 4F), tremble = Mth.sin(ageInTicks * 2.1F) * .02F;
                set(main, -2.35F * lift + tremble, inward * .1F * lift, inward * .55F * lift);
                set(off, Mth.lerp(lift, -.2F, aimX + .35F), aimY * .6F, inward * .08F);
            }
            case TELE_SUGGEST -> {
                // A palm out at the mind, then lowered slowly: easy now.
                float press = smooth((age - 3F) / 8F);
                set(main, Mth.lerp(press, aimX, -.85F), aimY + inward * .1F, -inward * .1F);
                set(off, -.15F, 0, inward * .1F);
            }
            case TELE_LINK -> {
                // Both hands to the temples, then opened outward to every mind around.
                float lift = smooth(age / 4F), open = smooth((age - 8F) / 6F);
                float x = Mth.lerp(open, -2.35F * lift, -1.3F), y = .1F * lift * (1 - open), z = Mth.lerp(open, .55F * lift, -1.0F);
                set(main, x, inward * y, inward * z);
                set(off, x, -inward * y, -inward * z);
            }
            case TELE_PUPPET -> {
                // Both hands raised before the face, fingers hooked down as if on strings, twitching the body along.
                float raise = smooth(age / 4F);
                float a = Mth.sin(ageInTicks * 1.7F) * .05F, b = Mth.sin(ageInTicks * 1.7F + 1.5F) * .05F;
                set(main, Mth.lerp(raise, 0, aimX - .45F) + a, aimY + inward * .15F, 0);
                set(off, Mth.lerp(raise, 0, aimX - .2F) + b, aimY - inward * .3F, inward * .1F);
            }
            case TELE_NIGHTMARE -> {
                // A hand drawn down over the face, then flung out at the mind; the other held back, low and wide.
                float draw = smooth(age / 6F), fling = smooth((age - 6F) / 3F);
                set(main, Mth.lerp(fling, -2.6F + .7F * draw, aimX - .1F), Mth.lerp(fling, inward * .6F, aimY), 0);
                set(off, .3F * fling, 0, inward * .35F);
            }
            case TELE_COMMAND -> {
                // Two fingers raised beside the head, then a sharp point at the mind.
                float raise = smooth(age / 1.5F), snap = smooth((age - 2F) / 1.5F);
                set(main, Mth.lerp(snap, -2.6F * raise, aimX - .05F), Mth.lerp(snap, inward * .2F, aimY), 0);
                set(off, -.9F, -inward * .5F, 0);
            }
            case TELE_LISTEN -> {
                // Fingers at both temples, still, listening.
                float lift = smooth(age / 4F), breath = Mth.sin(ageInTicks * .3F) * .02F;
                set(main, -2.45F * lift + breath, inward * .15F * lift, inward * .6F * lift);
                set(off, -2.45F * lift - breath, -inward * .15F * lift, -inward * .6F * lift);
            }
            case TELE_WEAVE -> {
                // Hands weaving opposite circles before the chest, spinning the dream.
                float turn = age * .45F;
                set(main, -1.25F + Mth.sin(turn) * .18F, inward * (.35F + Mth.cos(turn) * .18F), 0);
                set(off, -1.25F + Mth.sin(turn + Mth.PI) * .18F, -inward * (.35F + Mth.cos(turn + Mth.PI) * .18F), 0);
            }
            case TELE_PERSUADE -> {
                // An open palm held out to the mind, swaying gently; the other hand on the heart.
                float reach = smooth(age / 5F), sway = Mth.sin(ageInTicks * .2F) * .04F;
                set(main, Mth.lerp(reach, -.3F, aimX + .1F) + sway, aimY + inward * .05F, -inward * .05F);
                set(off, -1.0F * reach, -inward * .8F * reach, 0);
            }
            case TELE_MEMORY -> {
                // A palm passed slowly before the eyes, wiping back through time.
                float lift = smooth(age / 4F), sweep = smooth(age / 18F);
                set(main, -1.9F * lift, Mth.lerp(sweep, inward * .6F, -inward * .4F), 0);
                set(off, -.2F, 0, inward * .12F);
            }
            case TELE_HIVE -> {
                // Palms raised, then pressed outward to the friends being linked.
                float raise = smooth(age / 5F), open = smooth((age - 8F) / 6F);
                float x = Mth.lerp(open, -2.0F * raise, -1.4F), out = Mth.lerp(open, .5F * raise, 1.1F);
                set(main, x, 0, -inward * out);
                set(off, x, 0, inward * out);
            }
            case TELE_STORM -> {
                // Hands to the temples, then thrown up at the point where the storm forms.
                float lift = smooth(age / 4F), thrust = smooth((age - 6F) / 3F);
                float x = Mth.lerp(thrust, -2.35F * lift, aimX - .5F), z = Mth.lerp(thrust, .55F * lift, -.15F);
                set(main, x, aimY * thrust, inward * z);
                set(off, x, aimY * thrust, -inward * z);
            }
            case TELE_VEIL -> {
                // A hand swept across the face like drawing a veil; the other low and wide.
                float sweep = smooth(age / 8F);
                set(main, -2.0F, Mth.lerp(sweep, -inward * .4F, inward * .7F), 0);
                set(off, -.3F, 0, inward * .5F);
            }
            case TELE_GATHER -> {
                // Hands clasped at the chest, then spread to share the hive.
                float clasp = smooth(age / 5F), open = smooth((age - 8F) / 6F);
                float x = Mth.lerp(open, -1.1F * clasp, -1.0F), in = .7F * clasp * (1 - open), out = 1.2F * open;
                set(main, x, inward * in, -inward * out);
                set(off, x, -inward * in, inward * out);
            }
            default -> { }
        }
    }

    /**
     * Firebending, with the conventions of {@link #telepathy}: {@code main} is the rear (cross) arm and {@code off} the
     * lead (jab) arm; {@code rear} and {@code lead} are the legs on those sides. {@code y = inward * k} turns the main
     * arm across the body, and {@code z = -inward * k} lifts it outward (the other side takes the opposite signs).
     * The whole body's turn (the roundhouse's twist, the sweep's spin, the backflip) is {@code PyroCombatPoseRenderer}.
     */
    private static void firebend(PlayerModel<?> model, LivingEntity entity, ArmPose pose, ModelPart main, ModelPart off,
                                 ModelPart rear, ModelPart lead, float inward, float aimX, float aimY, float age) {
        switch (pose) {
            case FIRE_JAB, FIRE_CROSS -> {
                // A fist snapped out along the aim and drawn back to guard; the other fist stays up at the chin.
                float punch = smooth(age / 1.5F) * (1 - smooth((age - 4) / 4F));
                boolean cross = pose == ArmPose.FIRE_CROSS;
                ModelPart striking = cross ? main : off, guard = cross ? off : main;
                float side = cross ? inward : -inward;
                set(striking, Mth.lerp(punch, -1.3F, aimX + .04F), Mth.lerp(punch, side * .5F, aimY + side * .1F),
                        Mth.lerp(punch, -side * .1F, 0));
                set(guard, -1.4F, -side * .55F, side * .12F);
                // A fighting stance: lead foot forward, rear foot back, the rear one driving the cross.
                set(lead, -.28F - (cross ? 0 : .08F * punch), 0, inward * .05F);
                set(rear, .32F + (cross ? .12F * punch : 0), 0, -inward * .05F);
            }
            case FIRE_KICK -> {
                // The rear leg chambers, swings round at hip height from its own side across, and comes back down.
                float chamber = smooth(age / 2.5F), swing = smooth((age - 1.5F) / 4F), recover = smooth((age - 7) / 5F);
                float lift = chamber * (1 - recover);
                set(rear, -1.45F * lift, Mth.lerp(swing, -inward * .95F, inward * .55F) * lift, -inward * .32F * lift);
                set(lead, .12F * lift, 0, inward * .08F * lift);
                // Arms flung out for balance: the rear arm swings back as the leg goes forward.
                set(main, .55F * swing * (1 - recover), inward * .2F, -inward * (.35F + .5F * lift));
                set(off, -1.1F * (1 - recover) - .2F, -inward * .45F * lift, inward * (.25F + .6F * lift));
            }
            case FIRE_SWEEP_KICK -> {
                // Dropped low on the lead leg; the rear leg swept straight out at the ankle; both hands to the ground.
                float settle = smooth(age / 2F) * (1 - smooth((age - 10) / 4F));
                set(rear, -.12F * settle, -inward * .35F * settle, -inward * 1.3F * settle);
                set(lead, -.95F * settle, inward * .15F * settle, inward * .1F * settle);
                set(main, -.6F * settle, 0, -inward * .3F * settle);
                set(off, -.6F * settle, 0, inward * .3F * settle);
            }
            case FIRE_STEP -> {
                // The palm on the far side shoves out against the jet's direction; the near arm guards; feet tucked.
                Vec3 travel = entity instanceof Player player
                        ? com.jayptucker.heroesevolved.flight.FlightVisualGeometry.travel(player) : entity.getDeltaMovement();
                float yaw = entity.yBodyRot * Mth.DEG_TO_RAD;
                double sideways = travel.x * -Math.cos(yaw) + travel.z * -Math.sin(yaw);
                double ahead = travel.x * -Math.sin(yaw) + travel.z * Math.cos(yaw);
                float settle = 1 - smooth((age - 5) / 5F);
                if (ahead < -Math.abs(sideways)) {
                    // Straight back: both palms thrust forward, the jets pushing from the hands.
                    set(main, -1.45F * settle, inward * .2F, -inward * .1F);
                    set(off, -1.45F * settle, -inward * .2F, inward * .1F);
                } else {
                    // Moving to the player's right means the left palm pushes out to the left, and the other way.
                    boolean towardRight = sideways > 0;
                    ModelPart push = towardRight ? model.leftArm : model.rightArm;
                    ModelPart near = towardRight ? model.rightArm : model.leftArm;
                    float out = towardRight ? -1 : 1;
                    set(push, -.15F * settle, 0, out * 1.45F * settle);
                    set(near, -1.25F * settle, out * .45F * settle, out * .1F);
                }
                set(model.rightLeg, -.35F * settle, 0, .12F * settle);
                set(model.leftLeg, -.25F * settle, 0, -.12F * settle);
            }
            case FIRE_FLIP -> {
                // Arms thrown up and back over the head; the knees drawn in through the turn, then straight to land.
                float throwUp = smooth(age / 2.5F) * (1 - smooth((age - 9) / 4F));
                float tuck = Mth.sin(Mth.PI * Mth.clamp(age / 11F, 0, 1));
                set(main, -2.85F * throwUp, 0, -inward * .35F * throwUp);
                set(off, -2.85F * throwUp, 0, inward * .35F * throwUp);
                set(rear, -1.35F * tuck, 0, -inward * .1F);
                set(lead, -1.5F * tuck, 0, inward * .1F);
            }
            default -> { }
        }
        model.rightPants.copyFrom(model.rightLeg);
        model.leftPants.copyFrom(model.leftLeg);
    }

    private static float smooth(float value) {
        float t = Mth.clamp(value, 0, 1);
        return t * t * (3 - 2 * t);
    }

    private static void frostArm(ModelPart arm, Player player, float partial, Vec3 target, int side) {
        Vec3 aim = target.subtract(CryoBlastGeometry.shoulder(player, partial, side));
        float pitch = (float) -Math.atan2(aim.y, aim.horizontalDistance());
        float yaw = (float) Math.atan2(-aim.x, aim.z);
        float body = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
        set(arm, -Mth.HALF_PI + pitch, yaw - body, 0);
    }

    private static void set(ModelPart arm, float x, float y, float z) {
        arm.xRot = x;
        arm.yRot = y;
        arm.zRot = z;
    }
}
