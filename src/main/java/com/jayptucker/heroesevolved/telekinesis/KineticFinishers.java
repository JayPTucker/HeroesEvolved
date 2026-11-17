package com.jayptucker.heroesevolved.telekinesis;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.combat.StyleHits;
import com.jayptucker.heroesevolved.combat.Takedowns;
import com.jayptucker.heroesevolved.combat.Takedowns.Run;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;

/**
 * Telekinesis's alternate finishers, picked at random beside {@link CutStrings}; both give its reward (Grip ready
 * again). Both use the Puppeteer's visible hand of bent air.
 *
 * <ul>
 *   <li><b>Crush</b>: you close your fist slowly and the fingers of air close over the foe, squeezing tighter until it
 *   buckles.</li>
 *   <li><b>Toss</b>: the hand lifts the foe high, holds it a beat, trembling, and you pull your arm down hard: it is
 *   slammed into the ground.</li>
 * </ul>
 *
 * <p>{@code StyleArt} draws both: bound to the foe, origin its feet where it stood, target yours, scale its height.</p>
 */
public final class KineticFinishers {
    public static final int CRUSH_CLOSE = 4, CRUSH_LAND = 16, CRUSH_TICKS = 28;
    /** Toss: up by HIGH, held to PULL, slammed down by LAND. */
    public static final int TOSS_HIGH = 10, TOSS_PULL = 16, TOSS_LAND = 20, TOSS_TICKS = 32;
    public static final double TOSS_HEIGHT = 3.5;

    private KineticFinishers() {}

    /** How high Toss holds the foe at {@code t}. */
    public static double toss(float t) {
        if (t <= 1) return 0;
        if (t <= TOSS_HIGH) { float k = (t - 1) / (TOSS_HIGH - 1); return TOSS_HEIGHT * k * k * (3 - 2 * k); }
        if (t <= TOSS_PULL) return TOSS_HEIGHT + .1 * Math.sin((t - TOSS_HIGH) * 1.7);
        if (t <= TOSS_LAND) { float k = (t - TOSS_PULL) / (float) (TOSS_LAND - TOSS_PULL); return TOSS_HEIGHT * (1 - k * k); }
        return 0;
    }

    public static final Takedowns.Finisher CRUSH = new Takedowns.Finisher() {
        @Override public ResourceLocation power() { return ModAbilities.TELEKINESIS_ID; }
        @Override public String id() { return "crush"; }
        @Override public int ticks() { return CRUSH_TICKS; }
        @Override public int landAt() { return CRUSH_LAND; }

        @Override public void start(Run run) {
            ServerPlayer p = run.player;
            Takedowns.show(run.level(), Piece.FINISH_CRUSH, run.anchor, p.position(), Math.max(.6F, run.target.getBbHeight()), run.target, p.getId());
            ArmPosePayload.show(p, ArmPose.KINETIC_HOLD, CRUSH_LAND - 3);
            Takedowns.sound(run, run.anchor, SoundEvents.BEACON_POWER_SELECT, .6F, .6F);
        }

        @Override public void tick(Run run) {
            int age = run.age();
            if (age == 7 || age == 11 || age == 14) {
                Takedowns.flinch(run.target);
                Takedowns.sound(run, run.anchor, SoundEvents.ARMOR_EQUIP_IRON.value(), .8F, .5F + age * .03F);
            }
            if (age == CRUSH_LAND - 3) ArmPosePayload.show(run.player, ArmPose.QUAKE_CLENCH, 10);
        }

        @Override public void land(Run run) {
            ServerPlayer p = run.player;
            Takedowns.strike(run, damage -> StyleHits.hurt(p, run.target, damage));
            Takedowns.spill(run, (other, amount) -> {
                StyleHits.hurt(p, other, amount);
                StyleHits.away(other, run.anchor, StyleHits.level(p), .4, .2);
            });
            Takedowns.sound(run, run.anchor, SoundEvents.ANVIL_LAND, .6F, .7F);
            Takedowns.sound(run, run.anchor, SoundEvents.PLAYER_ATTACK_CRIT, 1F, .6F);
        }

        @Override public void reward(Run run) { CutStrings.FINISHER.reward(run); }
    };

    public static final Takedowns.Finisher TOSS = new Takedowns.Finisher() {
        @Override public ResourceLocation power() { return ModAbilities.TELEKINESIS_ID; }
        @Override public String id() { return "toss"; }
        @Override public int ticks() { return TOSS_TICKS; }
        @Override public int landAt() { return TOSS_LAND; }
        @Override public boolean holds() { return false; }

        @Override public void start(Run run) {
            ServerPlayer p = run.player;
            Takedowns.show(run.level(), Piece.FINISH_TOSS, run.anchor, p.position(), Math.max(.6F, run.target.getBbHeight()), run.target, p.getId());
            ArmPosePayload.show(p, ArmPose.STYLE_LIFT, TOSS_PULL);
            Takedowns.sound(run, run.anchor, SoundEvents.BEACON_ACTIVATE, .5F, 1.4F);
        }

        @Override public void tick(Run run) {
            LivingEntity foe = run.target;
            int age = run.age();
            if (age <= TOSS_LAND && foe.isAlive()) {
                Takedowns.lift(run, toss(age));
                Takedowns.face(run.player, foe.getBoundingBox().getCenter());
            }
            if (age == TOSS_HIGH) Takedowns.flinch(foe);
            if (age == TOSS_PULL) {
                ArmPosePayload.show(run.player, ArmPose.STYLE_HAMMER, 10);
                Takedowns.sound(run, run.player.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1F, .6F);
            }
        }

        @Override public void land(Run run) {
            ServerPlayer p = run.player;
            Takedowns.strike(run, damage -> StyleHits.hurt(p, run.target, damage));
            Takedowns.spill(run, (other, amount) -> {
                StyleHits.hurt(p, other, amount);
                StyleHits.away(other, run.anchor, StyleHits.level(p), .6, .35);
            });
            Takedowns.sound(run, run.anchor, SoundEvents.GENERIC_BIG_FALL, 1.3F, .7F);
            Takedowns.sound(run, run.anchor, SoundEvents.ANVIL_LAND, .5F, .5F);
            CameraShakePayload.sendAround(run.level(), run.anchor, 20, .24F, 6);
        }

        @Override public void reward(Run run) { CutStrings.FINISHER.reward(run); }
    };
}
