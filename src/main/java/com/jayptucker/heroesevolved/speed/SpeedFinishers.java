package com.jayptucker.heroesevolved.speed;

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
import net.minecraft.world.phys.Vec3;

/**
 * Superspeed's alternate finishers, picked at random beside {@link LateThunder}; both give its reward (Momentum to at
 * least 60). Neither moves you from where you stand.
 *
 * <ul>
 *   <li><b>Hundred Fists</b>: your arms blur into a fan of gold afterimage fists, landing all over the foe faster than it
 *   can flinch, and the last one throws it.</li>
 *   <li><b>Flick</b>: the air round you goes still, dust hanging where it was; you draw one hand back and flick it,
 *   leaving gold trails hanging off the wrist; the foe is thrown, and the boom arrives late.</li>
 * </ul>
 *
 * <p>{@code SpeedArt} draws both, bound to you; origin the foe's feet, target yours, scale its height.</p>
 */
public final class SpeedFinishers {
    public static final int FISTS_START = 3, FISTS_LAND = 18, FISTS_TICKS = 28;
    public static final int FLICK_STILL = 0, FLICK_DRAW = 6, FLICK_SNAP = 11, FLICK_LAND = 12, FLICK_BOOM = 17, FLICK_TICKS = 28;

    private SpeedFinishers() {}

    private static Vec3 toward(Run run) {
        Vec3 d = run.anchor.subtract(run.player.position()).multiply(1, 0, 1);
        return d.lengthSqr() < 1e-4 ? StyleHits.level(run.player) : d.normalize();
    }

    public static final Takedowns.Finisher HUNDRED_FISTS = new Takedowns.Finisher() {
        @Override public ResourceLocation power() { return ModAbilities.SUPERSPEED_ID; }
        @Override public String id() { return "hundred_fists"; }
        @Override public int ticks() { return FISTS_TICKS; }
        @Override public int landAt() { return FISTS_LAND; }

        @Override public void start(Run run) {
            ServerPlayer p = run.player;
            Takedowns.show(run.level(), Piece.FINISH_FISTS, run.anchor, p.position(), Math.max(.6F, run.target.getBbHeight()), p, p.getId());
            ArmPosePayload.show(p, ArmPose.SPEED_GUARD, FISTS_START);
            Takedowns.sound(run, p.position(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), .5F, 1.8F);
        }

        @Override public void tick(Run run) {
            ServerPlayer p = run.player;
            int age = run.age();
            if (age == FISTS_START) ArmPosePayload.show(p, ArmPose.STYLE_FLURRY, FISTS_LAND - FISTS_START);
            if (age > FISTS_START && age < FISTS_LAND) {
                if (age % 2 == 0) Takedowns.flinch(run.target);
                Takedowns.sound(run, run.anchor, SoundEvents.PLAYER_ATTACK_WEAK, .5F, 1.3F + run.target.getRandom().nextFloat() * .5F);
            }
            if (age == FISTS_LAND - 1) ArmPosePayload.show(p, ArmPose.STYLE_CROSS, 10);
        }

        @Override public void land(Run run) {
            ServerPlayer p = run.player;
            LivingEntity foe = run.target;
            Vec3 fwd = toward(run);
            Takedowns.strike(run, damage -> SpeedService.hit(p, foe, damage));
            StyleHits.shove(foe, fwd.scale(1.2).add(0, .35, 0));
            Takedowns.spill(run, (other, amount) -> {
                SpeedService.hit(p, other, amount);
                StyleHits.away(other, run.anchor, fwd, .6, .25);
            });
            Takedowns.sound(run, run.anchor, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.2F, .8F);
            CameraShakePayload.sendAround(run.level(), run.anchor, 16, .18F, 5);
        }

        @Override public void reward(Run run) { LateThunder.FINISHER.reward(run); }
    };

    public static final Takedowns.Finisher FLICK = new Takedowns.Finisher() {
        @Override public ResourceLocation power() { return ModAbilities.SUPERSPEED_ID; }
        @Override public String id() { return "flick"; }
        @Override public int ticks() { return FLICK_TICKS; }
        @Override public int landAt() { return FLICK_LAND; }

        @Override public void start(Run run) {
            ServerPlayer p = run.player;
            Takedowns.show(run.level(), Piece.FINISH_FLICK, run.anchor, p.position(), Math.max(.6F, run.target.getBbHeight()), p, p.getId());
            ArmPosePayload.show(p, ArmPose.SPEED_STILL, FLICK_DRAW);
            Takedowns.sound(run, p.position(), SoundEvents.BEACON_DEACTIVATE, .6F, 1.8F);
        }

        @Override public void tick(Run run) {
            ServerPlayer p = run.player;
            int age = run.age();
            if (age == FLICK_DRAW) ArmPosePayload.show(p, ArmPose.SPEED_THROW, FLICK_SNAP - FLICK_DRAW + 6);
            if (age == FLICK_SNAP) Takedowns.sound(run, p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, .8F, 2F);
            if (age == FLICK_BOOM) {
                Takedowns.sound(run, run.anchor, ModSounds.SKYBURST_BOOM.get(), 1.1F, 1.2F);
                CameraShakePayload.sendAround(run.level(), run.anchor, 32, .26F, 7);
            }
        }

        @Override public void land(Run run) {
            ServerPlayer p = run.player;
            LivingEntity foe = run.target;
            Vec3 fwd = toward(run);
            Takedowns.strike(run, damage -> SpeedService.hit(p, foe, damage));
            StyleHits.shove(foe, fwd.scale(1.6).add(0, .4, 0));
            Takedowns.spill(run, (other, amount) -> {
                SpeedService.hit(p, other, amount);
                StyleHits.away(other, run.anchor, fwd, .8, .3);
            });
            Takedowns.sound(run, run.anchor, SoundEvents.PLAYER_ATTACK_CRIT, 1F, .6F);
        }

        @Override public void reward(Run run) { LateThunder.FINISHER.reward(run); }
    };
}
