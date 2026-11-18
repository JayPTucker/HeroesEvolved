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
 * Density's alternate finishers, picked at random beside {@link SixFeet}; both fill your Root, as it does. Neither moves
 * you.
 *
 * <ul>
 *   <li><b>Rock Clap</b>: both palms rise and two slabs of ground stand up on either side of the foe; the palms clap
 *   together and so do the slabs, and break apart.</li>
 *   <li><b>Heavy Hand</b>: you drop into stance, stone gathering on your forearm, and one overhead blow drives the foe
 *   down into a crater that seals over it.</li>
 * </ul>
 *
 * <p>{@code DensityArt} draws both: origin the foe's feet, target yours, scale its height; Rock Clap's seed is the ground's
 * block (its slabs are made of it); Heavy Hand's piece is bound to the foe ({@code BodyCopies} sinks it into the
 * crater).</p>
 */
public final class DensityFinishers {
    /** Rock Clap: the slabs stand up by {@code CLAP_RISE}, and meet at {@code CLAP_LAND}. */
    public static final int CLAP_RISE = 8, CLAP_LAND = 13, CLAP_TICKS = 28;
    public static final int HEAVY_BRACE = 2, HEAVY_SWING = 12;

    private DensityFinishers() {}

    public static final Takedowns.Finisher ROCK_CLAP = new Takedowns.Finisher() {
        @Override public ResourceLocation power() { return ModAbilities.PHASING_ID; }
        @Override public String id() { return "rock_clap"; }
        @Override public int ticks() { return CLAP_TICKS; }
        @Override public int landAt() { return CLAP_LAND; }

        @Override public void start(Run run) {
            ServerPlayer p = run.player;
            var ground = DensityService.footing(p);
            Takedowns.show(run.level(), Piece.FINISH_PHASE, run.anchor, p.position(), Math.max(.6F, run.target.getBbHeight()), null,
                    net.minecraft.world.level.block.Block.getId(ground == null ? net.minecraft.world.level.block.Blocks.STONE.defaultBlockState() : ground));
            ArmPosePayload.show(p, ArmPose.EARTH_WALL, CLAP_LAND);
            Takedowns.sound(run, run.anchor, SoundEvents.ROOTED_DIRT_BREAK, 1.2F, .5F);
            Takedowns.sound(run, run.anchor, SoundEvents.DEEPSLATE_PLACE, 1F, .5F);
        }

        @Override public void tick(Run run) {
            int age = run.age();
            if (age == CLAP_RISE) Takedowns.sound(run, run.anchor, SoundEvents.GRAVEL_STEP, 1F, .5F);
            if (age == CLAP_LAND - 2) ArmPosePayload.show(run.player, ArmPose.EARTH_CRUSH, 10);
        }

        @Override public void land(Run run) {
            ServerPlayer p = run.player;
            Takedowns.strike(run, damage -> StyleHits.hurt(p, run.target, damage));
            Takedowns.spill(run, (other, amount) -> StyleHits.hurt(p, other, amount));
            Takedowns.sound(run, run.anchor, SoundEvents.DEEPSLATE_BRICKS_BREAK, 1.3F, .5F);
            Takedowns.sound(run, run.anchor, SoundEvents.ANVIL_LAND, .7F, .6F);
            CameraShakePayload.sendAround(run.level(), run.anchor, 20, .3F, 6);
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
            ArmPosePayload.show(p, ArmPose.EARTH_STANCE, HEAVY_SWING - 1);
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
