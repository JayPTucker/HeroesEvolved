package com.jayptucker.heroesevolved.sonic;

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
 * Sonic Resonance's alternate finishers, picked at random beside {@link Encore}.
 *
 * <ul>
 *   <li><b>Shatter Note</b>: one held high note. A waveform runs from your mouth to the foe, its body ripples in time
 *   with rings that close in, tighter and tighter, until it goes still and the note breaks like glass.</li>
 *   <li><b>Silence</b>: slate rings close in on the foe from all round and every sound stops. It crumples without a
 *   sound, and then comes one loud crack.</li>
 * </ul>
 *
 * <p>{@code SonicArt} draws both: origin your mouth, target the foe's chest, bound to the foe, scale its height.</p>
 */
public final class SonicFinishers {
    public static final int NOTE_SING = 2, NOTE_LAND = 20, NOTE_TICKS = 30;
    /** Silence: the hush closes from HUSH to SHUT, it crumples silently at CRUMPLE, and the crack comes at LAND. */
    public static final int SILENCE_HUSH = 2, SILENCE_SHUT = 14, SILENCE_CRUMPLE = 17, SILENCE_LAND = 22, SILENCE_TICKS = 32;

    private SonicFinishers() {}

    public static final Takedowns.Finisher SHATTER_NOTE = new Takedowns.Finisher() {
        @Override public ResourceLocation power() { return ModAbilities.SONIC_RESONANCE_ID; }
        @Override public String id() { return "shatter_note"; }
        @Override public int ticks() { return NOTE_TICKS; }
        @Override public int landAt() { return NOTE_LAND; }

        @Override public void start(Run run) {
            ServerPlayer p = run.player;
            Takedowns.show(run.level(), Piece.FINISH_NOTE, p.getEyePosition().add(0, -.3, 0), run.target.getBoundingBox().getCenter(),
                    Math.max(.6F, run.target.getBbHeight()), run.target, p.getId());
            ArmPosePayload.show(p, ArmPose.SONIC_SING, NOTE_LAND - 1);
        }

        @Override public void tick(Run run) {
            int age = run.age();
            if (age == NOTE_SING) Takedowns.sound(run, run.player.position(), SoundEvents.NOTE_BLOCK_FLUTE.value(), 1F, 2F);
            if (age > NOTE_SING && age < NOTE_LAND && age % 3 == 0) {
                Takedowns.sound(run, run.player.position(), SoundEvents.NOTE_BLOCK_FLUTE.value(), .8F, 2F);
                Takedowns.sound(run, run.anchor, SoundEvents.AMETHYST_BLOCK_RESONATE, .5F, 1.2F + age * .03F);
            }
            if (age == 9 || age == 15) Takedowns.flinch(run.target);
        }

        @Override public void land(Run run) {
            ServerPlayer p = run.player;
            Takedowns.strike(run, damage -> SonicService.hit(p, run.target, damage));
            Takedowns.spill(run, (other, amount) -> SonicService.hit(p, other, amount));
            Takedowns.sound(run, run.anchor, SoundEvents.GLASS_BREAK, 1.3F, 1.8F);
            Takedowns.sound(run, run.anchor, SoundEvents.AMETHYST_CLUSTER_BREAK, 1.2F, 1.6F);
        }

        @Override public void reward(Run run) { Encore.FINISHER.reward(run); }
    };

    public static final Takedowns.Finisher SILENCE = new Takedowns.Finisher() {
        @Override public ResourceLocation power() { return ModAbilities.SONIC_RESONANCE_ID; }
        @Override public String id() { return "silence"; }
        @Override public int ticks() { return SILENCE_TICKS; }
        @Override public int landAt() { return SILENCE_LAND; }

        @Override public void start(Run run) {
            ServerPlayer p = run.player;
            Takedowns.show(run.level(), Piece.FINISH_SILENCE, p.getEyePosition().add(0, -.3, 0), run.target.getBoundingBox().getCenter(),
                    Math.max(.6F, run.target.getBbHeight()), run.target, p.getId());
            ArmPosePayload.show(p, ArmPose.SONIC_HUSH, SILENCE_LAND - 2);
            Takedowns.sound(run, run.anchor, SoundEvents.SCULK_SHRIEKER_SHRIEK, .3F, 1.6F);
        }

        @Override public void tick(Run run) {
            int age = run.age();
            // Nothing makes a sound once the hush shuts: the foe crumples in silence.
            if (age == SILENCE_CRUMPLE) Takedowns.shudder(run.target);
            if (age == SILENCE_LAND - 2) ArmPosePayload.show(run.player, ArmPose.SONIC_SNAP, 8);
        }

        @Override public void land(Run run) {
            ServerPlayer p = run.player;
            LivingEntity foe = run.target;
            Takedowns.strike(run, damage -> SonicService.hit(p, foe, damage));
            Takedowns.spill(run, (other, amount) -> {
                SonicService.hit(p, other, amount);
                StyleHits.away(other, run.anchor, StyleHits.level(p), .7, .25);
            });
            Takedowns.sound(run, run.anchor, SoundEvents.LIGHTNING_BOLT_THUNDER, .5F, 2F);
            Takedowns.sound(run, run.anchor, SoundEvents.GENERIC_EXPLODE.value(), .7F, 1.6F);
            CameraShakePayload.sendAround(run.level(), run.anchor, 20, .26F, 5);
        }

        @Override public void reward(Run run) { Encore.FINISHER.reward(run); }
    };
}
