package com.jayptucker.heroesevolved.time;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.combat.FinisherBeats;
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
 * Space-Time Manipulation's alternate finishers, picked at random beside {@link ClosedLoop}; both give its reward (Fold
 * Space back).
 *
 * <ul>
 *   <li><b>Stopped Clock</b>: a violet clock face stands across the foe and its hands stop at ten past ten. You cut it six
 *   times where you stand; each cut hangs frozen in the air and cracks the face. Time starts again and every cut lands
 *   at once.</li>
 *   <li><b>Unwound</b>: a clock across the foe runs backward, faster and faster, and its last moments step back out of
 *   it as violet afterimages that are drawn in again; then it folds into the clock's centre and is gone.</li>
 * </ul>
 */
public final class TimeFinishers {
    /** Stopped Clock: the six cuts, time starting again at RESUME, every cut landing at LAND. */
    public static final int[] STOPPED_CUTS = {5, 7, 9, 11, 13, 15};
    public static final int STOPPED_RESUME = 20, STOPPED_LAND = 22, STOPPED_TICKS = 32;

    private TimeFinishers() {}

    public static final Takedowns.Finisher STOPPED_CLOCK = new Takedowns.Finisher() {
        @Override public ResourceLocation power() { return ModAbilities.TIME_MANIPULATION_ID; }
        @Override public String id() { return "stopped_clock"; }
        @Override public int ticks() { return STOPPED_TICKS; }
        @Override public int landAt() { return STOPPED_LAND; }

        @Override public void start(Run run) {
            ServerPlayer p = run.player;
            TimeGeometry.show(run.level(), Piece.FINISH_STOPPED, run.anchor, p.position(), Math.max(.6F, run.target.getBbHeight()), null);
            ArmPosePayload.show(p, ArmPose.TIME_HALT, 5);
            Takedowns.sound(run, run.anchor, SoundEvents.BELL_RESONATE, .8F, 1.4F);
            Takedowns.sound(run, run.anchor, SoundEvents.AMETHYST_BLOCK_RESONATE, 1F, .7F);
        }

        @Override public void tick(Run run) {
            ServerPlayer p = run.player;
            int age = run.age();
            for (int i = 0; i < STOPPED_CUTS.length; i++) if (age == STOPPED_CUTS[i] - 1) {
                // Six cuts with nothing answering them: no flinch, no sound from the foe, only the blade.
                ArmPosePayload.show(p, i % 2 == 0 ? ArmPose.STYLE_CROSS : ArmPose.STYLE_HOOK, 4);
                Takedowns.sound(run, run.anchor, SoundEvents.PLAYER_ATTACK_SWEEP, .6F, 1.5F + i * .05F);
            }
            if (age == STOPPED_RESUME) {
                ArmPosePayload.show(p, ArmPose.TIME_SNAP, 10);
                Takedowns.sound(run, run.anchor, ModSounds.TIME_BLINK.get(), .9F, .6F);
            }
        }

        @Override public void land(Run run) {
            ServerPlayer p = run.player;
            LivingEntity foe = run.target;
            Takedowns.strike(run, damage -> StyleHits.hurt(foe, p.damageSources().playerAttack(p), damage));
            Takedowns.spill(run, (other, amount) -> StyleHits.hurt(other, p.damageSources().playerAttack(p), amount));
            for (int i = 0; i < 3; i++) Takedowns.sound(run, run.anchor, SoundEvents.PLAYER_ATTACK_CRIT, 1F, .8F + i * .25F);
            Takedowns.sound(run, run.anchor, SoundEvents.AMETHYST_CLUSTER_BREAK, 1.2F, .8F);
            CameraShakePayload.sendAround(run.level(), run.anchor, 16, .2F, 6);
        }

        @Override public void reward(Run run) { ClosedLoop.FINISHER.reward(run); }
    };

    public static final Takedowns.Finisher UNWOUND = new Takedowns.Finisher() {
        @Override public ResourceLocation power() { return ModAbilities.TIME_MANIPULATION_ID; }
        @Override public String id() { return "unwound"; }
        @Override public int ticks() { return FinisherBeats.UNWOUND_TICKS; }
        @Override public int landAt() { return FinisherBeats.UNWOUND_LAND; }

        @Override public void start(Run run) {
            ServerPlayer p = run.player;
            // Bound to the foe: BodyCopies draws its afterimages and hides it once it has folded away.
            TimeGeometry.show(run.level(), Piece.FINISH_UNWOUND, run.anchor, p.position(), Math.max(.6F, run.target.getBbHeight()),
                    run.target);
            ArmPosePayload.show(p, ArmPose.TIME_WIND, FinisherBeats.UNWOUND_GONE);
            Takedowns.sound(run, run.anchor, SoundEvents.BELL_RESONATE, .7F, .6F);
        }

        @Override public void tick(Run run) {
            int age = run.age();
            if (age % 3 == 0 && age < FinisherBeats.UNWOUND_GONE)
                Takedowns.sound(run, run.anchor, SoundEvents.UI_BUTTON_CLICK.value(), .35F, 2F - age * .05F);
            if (age == FinisherBeats.UNWOUND_GONE - 4) Takedowns.sound(run, run.anchor, ModSounds.TIME_BLINK.get(), .8F, .5F);
            if (age == FinisherBeats.UNWOUND_GONE) ArmPosePayload.show(run.player, ArmPose.TIME_SNAP, 10);
        }

        @Override public void land(Run run) {
            ServerPlayer p = run.player;
            LivingEntity foe = run.target;
            Takedowns.strike(run, damage -> StyleHits.hurt(foe, p.damageSources().indirectMagic(p, p), damage));
            Takedowns.spill(run, (other, amount) -> StyleHits.hurt(other, p.damageSources().indirectMagic(p, p), amount));
            Takedowns.sound(run, run.anchor, SoundEvents.AMETHYST_CLUSTER_BREAK, 1F, 1.4F);
            CameraShakePayload.sendAround(run.level(), run.anchor, 16, .14F, 5);
        }

        @Override public void reward(Run run) { ClosedLoop.FINISHER.reward(run); }
    };
}
