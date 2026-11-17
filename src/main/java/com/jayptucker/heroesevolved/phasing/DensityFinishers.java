package com.jayptucker.heroesevolved.phasing;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.combat.FinisherBeats;
import com.jayptucker.heroesevolved.combat.StyleHits;
import com.jayptucker.heroesevolved.combat.Takedowns;
import com.jayptucker.heroesevolved.combat.Takedowns.Run;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

/**
 * Density's alternate finishers, picked at random beside {@link SixFeet}; both prime your Ambush hand, as it does.
 * Neither moves you.
 *
 * <ul>
 *   <li><b>Phase Punch</b>: your fist goes ghostly and sinks into the foe's chest through rippling ovals, then turns
 *   solid inside it: a burst of stone ripples out of its back.</li>
 *   <li><b>Heavy Hand</b>: you go Dense, stone gathering on your forearm, and one overhead blow drives the foe down into
 *   a crater that seals over it.</li>
 * </ul>
 *
 * <p>{@code DensityArt} draws both: origin the foe's feet, target yours, scale its height; Heavy Hand's piece is bound to
 * the foe ({@code BodyCopies} sinks it into the crater).</p>
 */
public final class DensityFinishers {
    public static final int PHASE_ENTER = 4, PHASE_SOLID = 11, PHASE_LAND = 13, PHASE_TICKS = 28;
    public static final int HEAVY_BRACE = 2, HEAVY_SWING = 12;

    private DensityFinishers() {}

    public static final Takedowns.Finisher PHASE_PUNCH = new Takedowns.Finisher() {
        @Override public ResourceLocation power() { return ModAbilities.PHASING_ID; }
        @Override public String id() { return "phase_punch"; }
        @Override public int ticks() { return PHASE_TICKS; }
        @Override public int landAt() { return PHASE_LAND; }

        @Override public void start(Run run) {
            ServerPlayer p = run.player;
            Takedowns.show(run.level(), Piece.FINISH_PHASE, run.anchor, p.position(), Math.max(.6F, run.target.getBbHeight()), null, p.getId());
            ArmPosePayload.show(p, ArmPose.DENSITY_FADE, PHASE_ENTER);
            Takedowns.sound(run, p.position(), SoundEvents.SOUL_ESCAPE.value(), .8F, 1.3F);
        }

        @Override public void tick(Run run) {
            int age = run.age();
            if (age == PHASE_ENTER) {
                ArmPosePayload.show(run.player, ArmPose.DENSITY_LUNGE, PHASE_SOLID - PHASE_ENTER + 6);
                Takedowns.sound(run, run.anchor, SoundEvents.AMETHYST_BLOCK_RESONATE, .9F, .7F);
            }
            if (age == PHASE_SOLID) {
                Takedowns.flinch(run.target);
                Takedowns.sound(run, run.anchor, SoundEvents.STONE_PLACE, 1.2F, .5F);
            }
        }

        @Override public void land(Run run) {
            ServerPlayer p = run.player;
            Takedowns.strike(run, damage -> StyleHits.hurt(p, run.target, damage));
            Takedowns.spill(run, (other, amount) -> StyleHits.hurt(p, other, amount));
            Takedowns.sound(run, run.anchor, SoundEvents.STONE_BREAK, 1.2F, .6F);
            Takedowns.sound(run, run.anchor, SoundEvents.PLAYER_ATTACK_CRIT, 1F, .5F);
        }

        @Override public void reward(Run run) { SixFeet.FINISHER.reward(run); }
    };

    public static final Takedowns.Finisher HEAVY_HAND = new Takedowns.Finisher() {
        @Override public ResourceLocation power() { return ModAbilities.PHASING_ID; }
        @Override public String id() { return "heavy_hand"; }
        @Override public int ticks() { return FinisherBeats.HEAVY_TICKS; }
        @Override public int landAt() { return FinisherBeats.HEAVY_LAND; }

        @Override public void start(Run run) {
            ServerPlayer p = run.player;
            // Bound to the foe: BodyCopies sinks it into the crater after the blow.
            Takedowns.show(run.level(), Piece.FINISH_HEAVY, run.anchor, p.position(), Math.max(.6F, run.target.getBbHeight()), run.target, p.getId());
            ArmPosePayload.show(p, ArmPose.DENSITY_BRACE, HEAVY_SWING - 1);
            Takedowns.sound(run, p.position(), SoundEvents.STONE_PLACE, 1F, .5F);
        }

        @Override public void tick(Run run) {
            int age = run.age();
            if (age == 6) Takedowns.sound(run, run.player.position(), SoundEvents.DEEPSLATE_PLACE, 1F, .5F);
            if (age == HEAVY_SWING) {
                ArmPosePayload.show(run.player, ArmPose.STYLE_HAMMER, 10);
                Takedowns.sound(run, run.player.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1F, .5F);
            }
        }

        @Override public void land(Run run) {
            ServerPlayer p = run.player;
            Takedowns.strike(run, damage -> StyleHits.hurt(p, run.target, damage));
            Takedowns.spill(run, (other, amount) -> {
                StyleHits.hurt(p, other, amount);
                StyleHits.away(other, run.anchor, StyleHits.level(p), .5, .3);
            });
            Takedowns.sound(run, run.anchor, SoundEvents.ANVIL_LAND, .9F, .5F);
            Takedowns.sound(run, run.anchor, SoundEvents.DEEPSLATE_BREAK, 1.3F, .5F);
            CameraShakePayload.sendAround(run.level(), run.anchor, 24, .3F, 7);
        }

        @Override public void reward(Run run) { SixFeet.FINISHER.reward(run); }
    };
}
