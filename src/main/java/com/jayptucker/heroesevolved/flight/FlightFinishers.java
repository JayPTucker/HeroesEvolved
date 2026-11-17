package com.jayptucker.heroesevolved.flight;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.combat.StyleHits;
import com.jayptucker.heroesevolved.combat.Takedowns;
import com.jayptucker.heroesevolved.combat.Takedowns.Run;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.sounds.ModSounds;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;

/**
 * Flight's alternate finishers, picked at random beside {@link Downburst}; both give its reward (Skyburst ready again).
 * Neither moves you: the air does the work.
 *
 * <ul>
 *   <li><b>Vacuum Clap</b>: arms flung wide, then brought together; two pressure fronts close on the foe from either
 *   side and crush it between them, and a ring of dust blows out flat.</li>
 *   <li><b>Updraft</b>: a sweep of the arm, and a column of wind lifts the foe (not you) six blocks up and holds it,
 *   spinning. You cut the wind and it drops.</li>
 * </ul>
 *
 * <p>{@code FlightArt} draws both: origin the foe's feet where it stood, target yours, scale its height; Updraft's piece
 * is bound to the foe, which it lifts.</p>
 */
public final class FlightFinishers {
    public static final int CLAP_WIDE = 2, CLAP_LAND = 12, CLAP_TICKS = 26;
    /** Updraft: up by TOP, held, the wind cut at CUT, down at LAND. */
    public static final int UPDRAFT_TOP = 12, UPDRAFT_CUT = 19, UPDRAFT_LAND = 24, UPDRAFT_TICKS = 36;
    public static final double UPDRAFT_HEIGHT = 6;

    private FlightFinishers() {}

    /** How high Updraft holds the foe at {@code t}. */
    public static double updraft(float t) {
        if (t <= 1) return 0;
        if (t <= UPDRAFT_TOP) { float k = (t - 1) / (UPDRAFT_TOP - 1); return UPDRAFT_HEIGHT * k * k * (3 - 2 * k); }
        if (t <= UPDRAFT_CUT) return UPDRAFT_HEIGHT + .15 * Math.sin((t - UPDRAFT_TOP) * .9);
        if (t <= UPDRAFT_LAND) { float k = (t - UPDRAFT_CUT) / (float) (UPDRAFT_LAND - UPDRAFT_CUT); return UPDRAFT_HEIGHT * (1 - k * k); }
        return 0;
    }

    public static final Takedowns.Finisher VACUUM_CLAP = new Takedowns.Finisher() {
        @Override public ResourceLocation power() { return ModAbilities.FLIGHT_ID; }
        @Override public String id() { return "vacuum_clap"; }
        @Override public int ticks() { return CLAP_TICKS; }
        @Override public int landAt() { return CLAP_LAND; }

        @Override public void start(Run run) {
            ServerPlayer p = run.player;
            Takedowns.show(run.level(), Piece.FINISH_CLAP, run.anchor, p.position(), Math.max(.6F, run.target.getBbHeight()), null, p.getId());
            ArmPosePayload.show(p, ArmPose.ARMS_WIDE, CLAP_LAND - 3);
            Takedowns.sound(run, p.position(), SoundEvents.BREEZE_INHALE, 1F, .9F);
        }

        @Override public void tick(Run run) {
            int age = run.age();
            if (age == CLAP_LAND - 3) ArmPosePayload.show(run.player, ArmPose.FLIGHT_CLAP, 10);
            if (age == 6) Takedowns.flinch(run.target);
        }

        @Override public void land(Run run) {
            ServerPlayer p = run.player;
            LivingEntity foe = run.target;
            Takedowns.strike(run, damage -> StyleHits.hurt(p, foe, damage));
            Takedowns.spill(run, (other, amount) -> {
                StyleHits.hurt(p, other, amount);
                StyleHits.away(other, run.anchor, StyleHits.level(p), .8, .15);
            });
            Takedowns.sound(run, run.anchor, ModSounds.SKYBURST_BOOM.get(), .8F, 1.6F);
            Takedowns.sound(run, run.anchor, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1F, .6F);
            CameraShakePayload.sendAround(run.level(), run.anchor, 20, .24F, 6);
        }

        @Override public void reward(Run run) { Downburst.FINISHER.reward(run); }
    };

    public static final Takedowns.Finisher UPDRAFT = new Takedowns.Finisher() {
        @Override public ResourceLocation power() { return ModAbilities.FLIGHT_ID; }
        @Override public String id() { return "updraft"; }
        @Override public int ticks() { return UPDRAFT_TICKS; }
        @Override public int landAt() { return UPDRAFT_LAND; }
        @Override public boolean holds() { return false; }

        @Override public void start(Run run) {
            ServerPlayer p = run.player;
            Takedowns.show(run.level(), Piece.FINISH_UPDRAFT, run.anchor, p.position(), Math.max(.6F, run.target.getBbHeight()), run.target, p.getId());
            ArmPosePayload.show(p, ArmPose.FLIGHT_CAST, UPDRAFT_TOP);
            Takedowns.sound(run, run.anchor, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 1F, .7F);
        }

        @Override public void tick(Run run) {
            LivingEntity foe = run.target;
            int age = run.age();
            if (age <= UPDRAFT_LAND && foe.isAlive()) {
                Takedowns.lift(run, updraft(age));
                if (age < UPDRAFT_CUT) {
                    // Spun by the wind.
                    float yaw = foe.getYRot() + 28;
                    foe.setYRot(yaw);
                    foe.setYBodyRot(yaw);
                    foe.setYHeadRot(yaw);
                }
            }
            if (age % 4 == 0 && age < UPDRAFT_CUT) Takedowns.sound(run, run.anchor, SoundEvents.BREEZE_IDLE_AIR, .6F, 1.2F);
            if (age == UPDRAFT_CUT) {
                ArmPosePayload.show(run.player, ArmPose.FLIGHT_CLAP, 8);
                Takedowns.sound(run, run.anchor, SoundEvents.BREEZE_DEFLECT, 1F, .6F);
            }
        }

        @Override public void land(Run run) {
            ServerPlayer p = run.player;
            LivingEntity foe = run.target;
            Takedowns.strike(run, damage -> StyleHits.hurt(p, foe, damage));
            Takedowns.spill(run, (other, amount) -> {
                StyleHits.hurt(p, other, amount);
                StyleHits.away(other, run.anchor, StyleHits.level(p), .7, .2);
            });
            Takedowns.sound(run, run.anchor, SoundEvents.GENERIC_BIG_FALL, 1.2F, .8F);
            Takedowns.sound(run, run.anchor, ModSounds.SKYBURST_BOOM.get(), .6F, 1.5F);
            CameraShakePayload.sendAround(run.level(), run.anchor, 20, .2F, 6);
        }

        @Override public void reward(Run run) { Downburst.FINISHER.reward(run); }
    };
}
