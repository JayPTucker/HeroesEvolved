package com.jayptucker.heroesevolved.invisibility;

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
import net.minecraft.world.phys.Vec3;

/**
 * Invisibility's alternate finishers, picked at random beside {@link HallOfMirrors}; both give its reward (three seconds
 * of free Fade). Neither moves you.
 *
 * <ul>
 *   <li><b>Bubble Crush</b>: a force bubble closes round the foe, its facets crack dark under the strain, and it squeezes
 *   shut and bursts into tumbling shards.</li>
 *   <li><b>Lens Strike</b>: you raise a Lens Shield and shove it; the lens flies at the foe and flattens it in a ring of
 *   shards.</li>
 * </ul>
 *
 * <p>{@code ForceArt} draws both: origin the foe's feet, target yours, scale its height.</p>
 */
public final class ForceFinishers {
    public static final int BUBBLE_SHUT = 4, BUBBLE_LAND = 18, BUBBLE_TICKS = 30;
    public static final int LENS_RAISE = 3, LENS_SHOVE = 8, LENS_LAND = 11, LENS_TICKS = 26;

    private ForceFinishers() {}

    public static final Takedowns.Finisher BUBBLE_CRUSH = new Takedowns.Finisher() {
        @Override public ResourceLocation power() { return ModAbilities.INVISIBILITY_ID; }
        @Override public String id() { return "bubble_crush"; }
        @Override public int ticks() { return BUBBLE_TICKS; }
        @Override public int landAt() { return BUBBLE_LAND; }

        @Override public void start(Run run) {
            ServerPlayer p = run.player;
            Takedowns.show(run.level(), Piece.FINISH_BUBBLE_CRUSH, run.anchor, p.position(), Math.max(.6F, run.target.getBbHeight()), null, p.getId());
            ArmPosePayload.show(p, ArmPose.FORCE_BUBBLE, BUBBLE_LAND - 4);
            Takedowns.sound(run, run.anchor, SoundEvents.ILLUSIONER_PREPARE_MIRROR, 1F, 1.2F);
        }

        @Override public void tick(Run run) {
            int age = run.age();
            if (age == BUBBLE_SHUT) Takedowns.sound(run, run.anchor, SoundEvents.AMETHYST_BLOCK_PLACE, 1F, 1.4F);
            if (age == 9 || age == 13 || age == 16) {
                Takedowns.flinch(run.target);
                Takedowns.sound(run, run.anchor, SoundEvents.GLASS_HIT, 1F, .7F + age * .03F);
            }
            if (age == BUBBLE_LAND - 4) ArmPosePayload.show(run.player, ArmPose.FORCE_CRUSH, 10);
        }

        @Override public void land(Run run) {
            ServerPlayer p = run.player;
            Takedowns.strike(run, damage -> StyleHits.hurt(p, run.target, damage));
            Takedowns.spill(run, (other, amount) -> {
                StyleHits.hurt(p, other, amount);
                StyleHits.away(other, run.anchor, StyleHits.level(p), .6, .2);
            });
            Takedowns.sound(run, run.anchor, SoundEvents.GLASS_BREAK, 1.3F, .8F);
            Takedowns.sound(run, run.anchor, SoundEvents.PLAYER_ATTACK_CRIT, 1F, .6F);
        }

        @Override public void reward(Run run) { HallOfMirrors.FINISHER.reward(run); }
    };

    public static final Takedowns.Finisher LENS_STRIKE = new Takedowns.Finisher() {
        @Override public ResourceLocation power() { return ModAbilities.INVISIBILITY_ID; }
        @Override public String id() { return "lens_strike"; }
        @Override public int ticks() { return LENS_TICKS; }
        @Override public int landAt() { return LENS_LAND; }

        @Override public void start(Run run) {
            ServerPlayer p = run.player;
            Takedowns.show(run.level(), Piece.FINISH_LENS, run.anchor, p.position(), Math.max(.6F, run.target.getBbHeight()), null, p.getId());
            ArmPosePayload.show(p, ArmPose.FORCE_SHIELD, LENS_SHOVE);
            Takedowns.sound(run, p.position(), SoundEvents.ILLUSIONER_CAST_SPELL, .8F, 1.4F);
        }

        @Override public void tick(Run run) {
            if (run.age() == LENS_SHOVE) {
                ArmPosePayload.show(run.player, ArmPose.FORCE_PUSH, 10);
                Takedowns.sound(run, run.player.position(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), .8F, 1.5F);
            }
        }

        @Override public void land(Run run) {
            ServerPlayer p = run.player;
            Vec3 fwd = run.anchor.subtract(p.position()).multiply(1, 0, 1);
            fwd = fwd.lengthSqr() < 1e-4 ? StyleHits.level(p) : fwd.normalize();
            Takedowns.strike(run, damage -> StyleHits.hurt(p, run.target, damage));
            StyleHits.shove(run.target, fwd.scale(1).add(0, .2, 0));
            Vec3 push = fwd;
            Takedowns.spill(run, (other, amount) -> {
                StyleHits.hurt(p, other, amount);
                StyleHits.away(other, run.anchor, push, .8, .2);
            });
            Takedowns.sound(run, run.anchor, SoundEvents.GLASS_BREAK, 1.2F, 1.1F);
            Takedowns.sound(run, run.anchor, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1F, .8F);
            CameraShakePayload.sendAround(run.level(), run.anchor, 16, .18F, 5);
        }

        @Override public void reward(Run run) { HallOfMirrors.FINISHER.reward(run); }
    };
}
