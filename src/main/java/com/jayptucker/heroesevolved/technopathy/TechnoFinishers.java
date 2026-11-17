package com.jayptucker.heroesevolved.technopathy;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.combat.StyleHits;
import com.jayptucker.heroesevolved.combat.Takedowns;
import com.jayptucker.heroesevolved.combat.Takedowns.Run;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

/**
 * Technopathy's alternate finishers, picked at random beside {@link HardReset}; both renew your circuit's enrolment, as
 * it does. Neither needs a circuit: the foe is the circuit.
 *
 * <ul>
 *   <li><b>Kill Switch</b>: a copper circuit traces itself over the foe's body, node by node; you flick a finger and it
 *   goes dark node by node until the foe powers down.</li>
 *   <li><b>Overclock</b>: current races round the foe's body, faster and faster, spitting sparks, until it shorts out
 *   in a burst of them.</li>
 * </ul>
 *
 * <p>{@code CircuitArt} draws both: bound to the foe, origin its feet, target yours, scale its height.</p>
 */
public final class TechnoFinishers {
    /** Kill Switch: traced by TRACED, the flick at FLICK, dark node by node until LAND. */
    public static final int SWITCH_TRACED = 11, SWITCH_FLICK = 13, SWITCH_LAND = 20, SWITCH_TICKS = 30;
    public static final int CLOCK_LAND = 16, CLOCK_TICKS = 28;

    private TechnoFinishers() {}

    public static final Takedowns.Finisher KILL_SWITCH = new Takedowns.Finisher() {
        @Override public ResourceLocation power() { return ModAbilities.TECHNOPATHY_ID; }
        @Override public String id() { return "kill_switch"; }
        @Override public int ticks() { return SWITCH_TICKS; }
        @Override public int landAt() { return SWITCH_LAND; }

        @Override public void start(Run run) {
            ServerPlayer p = run.player;
            Takedowns.show(run.level(), Piece.FINISH_KILL_SWITCH, run.anchor, p.position(), Math.max(.6F, run.target.getBbHeight()), run.target, p.getId());
            ArmPosePayload.show(p, ArmPose.ELECTRO_POINT, SWITCH_FLICK);
            Takedowns.sound(run, run.anchor, SoundEvents.COPPER_BULB_TURN_ON, 1F, 1.2F);
        }

        @Override public void tick(Run run) {
            int age = run.age();
            if (age < SWITCH_TRACED && age % 2 == 0) Takedowns.sound(run, run.anchor, SoundEvents.UI_BUTTON_CLICK.value(), .3F, 1.6F + age * .03F);
            if (age == SWITCH_FLICK) {
                ArmPosePayload.show(run.player, ArmPose.SONIC_SNAP, 8);
                Takedowns.sound(run, run.player.position(), SoundEvents.LEVER_CLICK, 1F, 1.3F);
            }
            if (age > SWITCH_FLICK && age < SWITCH_LAND && age % 2 == 0)
                Takedowns.sound(run, run.anchor, SoundEvents.COPPER_BULB_TURN_OFF, .7F, 1.4F - (age - SWITCH_FLICK) * .08F);
        }

        @Override public void land(Run run) {
            ServerPlayer p = run.player;
            Takedowns.strike(run, damage -> StyleHits.hurt(p, run.target, damage));
            Takedowns.spill(run, (other, amount) -> StyleHits.hurt(p, other, amount));
            Takedowns.sound(run, run.anchor, SoundEvents.BEACON_DEACTIVATE, 1F, .7F);
        }

        @Override public void reward(Run run) { HardReset.FINISHER.reward(run); }
    };

    public static final Takedowns.Finisher OVERCLOCK = new Takedowns.Finisher() {
        @Override public ResourceLocation power() { return ModAbilities.TECHNOPATHY_ID; }
        @Override public String id() { return "overclock"; }
        @Override public int ticks() { return CLOCK_TICKS; }
        @Override public int landAt() { return CLOCK_LAND; }

        @Override public void start(Run run) {
            ServerPlayer p = run.player;
            Takedowns.show(run.level(), Piece.FINISH_OVERCLOCK, run.anchor, p.position(), Math.max(.6F, run.target.getBbHeight()), run.target, p.getId());
            ArmPosePayload.show(p, ArmPose.ELECTRO_TOUCH, CLOCK_LAND - 2);
            Takedowns.sound(run, run.anchor, SoundEvents.BEACON_POWER_SELECT, .7F, 1.5F);
        }

        @Override public void tick(Run run) {
            int age = run.age();
            if (age % 2 == 0 && age < CLOCK_LAND) Takedowns.sound(run, run.anchor, SoundEvents.COPPER_BULB_TURN_ON, .4F, 1F + age * .06F);
            if (age == 8 || age == 12 || age == 14) Takedowns.flinch(run.target);
            if (age == CLOCK_LAND - 2) ArmPosePayload.show(run.player, ArmPose.ELECTRO_OVERCHARGE, 8);
        }

        @Override public void land(Run run) {
            ServerPlayer p = run.player;
            Takedowns.strike(run, damage -> StyleHits.hurt(p, run.target, damage));
            Takedowns.spill(run, (other, amount) -> StyleHits.hurt(p, other, amount));
            Takedowns.sound(run, run.anchor, SoundEvents.FIRECHARGE_USE, 1F, 1.6F);
            Takedowns.sound(run, run.anchor, SoundEvents.FIRE_EXTINGUISH, .8F, 1.2F);
        }

        @Override public void reward(Run run) { HardReset.FINISHER.reward(run); }
    };
}
