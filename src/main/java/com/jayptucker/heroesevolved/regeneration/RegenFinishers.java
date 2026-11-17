package com.jayptucker.heroesevolved.regeneration;

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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Regeneration's alternate finishers, picked at random beside {@link PaidInFull}; both give its reward (30 Regrowth).
 *
 * <ul>
 *   <li><b>No Pain</b>: you walk into three of the foe's blows, a short step each (a block and a bit in all), each wound
 *   opening and closing as you come on; then one haymaker.</li>
 *   <li><b>Grip</b>: it hits you, you catch its arm with the wound still closing on yours, and you headbutt it.</li>
 * </ul>
 *
 * <p>The blows taken are for show: the foe's arm swings and the wound opens and closes, but you aren't really hurt (a real
 * hurt flashes you red and tilts your camera through the whole move). {@code RegenArt} draws both: bound to you, the foe's entity id in the seed; origin your feet, target the foe's, scale its height.</p>
 */
public final class RegenFinishers {
    /** No Pain: the three blows, the haymaker's wind-up at SWING and the blow at LAND. */
    public static final int[] NO_PAIN_BLOWS = {4, 9, 14};
    public static final int NO_PAIN_SWING = 18, NO_PAIN_LAND = 21, NO_PAIN_TICKS = 32;
    public static final double NO_PAIN_STEP = .4;
    /** Grip: the blow at HIT, the catch at CATCH, the headbutt at BUTT and the blow at LAND. */
    public static final int GRIP_HIT = 3, GRIP_CATCH = 5, GRIP_BUTT = 12, GRIP_LAND = 14, GRIP_TICKS = 28;

    private RegenFinishers() {}

    /** A blow the foe lands on you for the show: its arm swings and you hear it land (the wound is drawn by RegenArt). */
    private static void taken(Run run) {
        ServerPlayer p = run.player;
        run.target.swing(InteractionHand.MAIN_HAND, true);
        Takedowns.sound(run, p.position(), SoundEvents.PLAYER_ATTACK_STRONG, .8F, .8F + p.getRandom().nextFloat() * .2F);
        Takedowns.sound(run, p.position(), SoundEvents.SLIME_SQUISH_SMALL, .6F, 1F);
    }

    private record Walk(Vec3 home, Vec3 fwd, double room) {}

    private static Vec3 walked(Walk w, float age) {
        double along = 0;
        for (int blow : NO_PAIN_BLOWS) along += NO_PAIN_STEP * Math.min(1, Math.max(0, (age - blow) / 3.0));
        return w.home.add(w.fwd.scale(Math.min(along, w.room)));
    }

    public static final Takedowns.Finisher NO_PAIN = new Takedowns.Finisher() {
        @Override public ResourceLocation power() { return ModAbilities.REGENERATION_ID; }
        @Override public String id() { return "no_pain"; }
        @Override public int ticks() { return NO_PAIN_TICKS; }
        @Override public int landAt() { return NO_PAIN_LAND; }

        @Override public void start(Run run) {
            ServerPlayer p = run.player;
            Vec3 toward = run.anchor.subtract(p.position()).multiply(1, 0, 1);
            double gap = Math.sqrt(toward.lengthSqr()) - (run.target.getBbWidth() * .5 + .7);
            Vec3 fwd = toward.lengthSqr() < 1e-4 ? StyleHits.level(p) : toward.normalize();
            run.state = new Walk(p.position(), fwd, Math.max(0, gap));
            Takedowns.show(run.level(), Piece.FINISH_NO_PAIN, p.position(), run.anchor, Math.max(.6F, run.target.getBbHeight()), p, run.target.getId());
            ArmPosePayload.show(p, ArmPose.REGEN_DEFY, NO_PAIN_SWING);
        }

        @Override public void tick(Run run) {
            ServerPlayer p = run.player;
            int age = run.age();
            if (run.state instanceof Walk w && age < NO_PAIN_SWING) Takedowns.glide(p, walked(w, age), walked(w, age + 1));
            if (age == NO_PAIN_SWING) Takedowns.still(p);
            for (int blow : NO_PAIN_BLOWS) if (age == blow) taken(run);
            if (age == NO_PAIN_SWING) {
                ArmPosePayload.show(p, ArmPose.STYLE_HAYMAKER, 10);
                Takedowns.sound(run, p.position(), SoundEvents.HONEY_BLOCK_SLIDE, 1F, .6F);
            }
        }

        @Override public void land(Run run) {
            ServerPlayer p = run.player;
            LivingEntity foe = run.target;
            Takedowns.strike(run, damage -> StyleHits.hurt(p, foe, damage));
            Takedowns.spill(run, (other, amount) -> {
                StyleHits.hurt(p, other, amount);
                StyleHits.away(other, run.anchor, StyleHits.level(p), .7, .25);
            });
            Takedowns.sound(run, run.anchor, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.3F, .55F);
            Takedowns.sound(run, run.anchor, SoundEvents.SLIME_SQUISH, 1F, .6F);
            CameraShakePayload.sendAround(run.level(), run.anchor, 16, .2F, 5);
        }

        @Override public void reward(Run run) { PaidInFull.FINISHER.reward(run); }
    };

    public static final Takedowns.Finisher GRIP = new Takedowns.Finisher() {
        @Override public ResourceLocation power() { return ModAbilities.REGENERATION_ID; }
        @Override public String id() { return "grip"; }
        @Override public int ticks() { return GRIP_TICKS; }
        @Override public int landAt() { return GRIP_LAND; }

        @Override public void start(Run run) {
            ServerPlayer p = run.player;
            Takedowns.show(run.level(), Piece.FINISH_GRIP, p.position(), run.anchor, Math.max(.6F, run.target.getBbHeight()), p, run.target.getId());
            ArmPosePayload.show(p, ArmPose.REGEN_DEFY, GRIP_CATCH);
        }

        @Override public void tick(Run run) {
            ServerPlayer p = run.player;
            int age = run.age();
            if (age == GRIP_HIT) taken(run);
            if (age == GRIP_CATCH) {
                ArmPosePayload.show(p, ArmPose.REGEN_EMBRACE, GRIP_BUTT - GRIP_CATCH);
                Takedowns.flinch(run.target);
                Takedowns.sound(run, run.anchor, SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1F, .7F);
            }
            if (age == GRIP_CATCH + 4) Takedowns.sound(run, p.position(), SoundEvents.HONEY_BLOCK_SLIDE, 1F, .6F);
            if (age == GRIP_BUTT) ArmPosePayload.show(p, ArmPose.STYLE_HEADBUTT, 8);
        }

        @Override public void land(Run run) {
            ServerPlayer p = run.player;
            LivingEntity foe = run.target;
            Takedowns.strike(run, damage -> StyleHits.hurt(p, foe, damage));
            Takedowns.spill(run, (other, amount) -> StyleHits.hurt(p, other, amount));
            Takedowns.sound(run, run.anchor, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, .8F, .7F);
            Takedowns.sound(run, run.anchor, SoundEvents.SLIME_SQUISH, .8F, .7F);
            CameraShakePayload.sendAround(run.level(), run.anchor, 12, .15F, 4);
        }

        @Override public void reward(Run run) { PaidInFull.FINISHER.reward(run); }
    };
}
