package com.jayptucker.heroesevolved.radiation;

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
import net.minecraft.world.entity.LivingEntity;

/**
 * Induced Radioactivity's alternate finishers, picked at random beside {@link ChainReaction}; both vent Critical Mass as
 * it does (and stand a meltdown warning down).
 *
 * <ul>
 *   <li><b>Half-Life</b>: one touch, and the foe glows green from within, rings of decay pulsing slower and slower over
 *   it while the Geiger ticks; it greys to ash and crumbles.</li>
 *   <li><b>Pocket Sun</b>: a white bead of heat grows between your cupped hands; you press it into the foe and it goes up
 *   in a contained burst, a fist-sized mushroom cloud that hurts nothing else.</li>
 * </ul>
 *
 * <p>{@code RadiationArt} draws both: origin the foe's feet, target yours, scale its height; Half-Life's piece is bound to
 * the foe ({@code BodyCopies} greens it and greys it to ash).</p>
 */
public final class RadiationFinishers {
    public static final int HALF_TOUCH = 2;
    public static final int SUN_FORM = 3, SUN_PRESS = 13, SUN_LAND = 17, SUN_TICKS = 34;

    private RadiationFinishers() {}

    public static final Takedowns.Finisher HALF_LIFE = new Takedowns.Finisher() {
        @Override public ResourceLocation power() { return ModAbilities.RADIATION_ID; }
        @Override public String id() { return "half_life"; }
        @Override public int ticks() { return FinisherBeats.HALF_LIFE_TICKS; }
        @Override public int landAt() { return FinisherBeats.HALF_LIFE_LAND; }

        @Override public void start(Run run) {
            ServerPlayer p = run.player;
            Takedowns.show(run.level(), Piece.FINISH_HALF_LIFE, run.anchor, p.position(), Math.max(.6F, run.target.getBbHeight()), run.target, p.getId());
            ArmPosePayload.show(p, ArmPose.RAD_GRIP, 8);
            Takedowns.sound(run, run.anchor, SoundEvents.BEACON_POWER_SELECT, .7F, 1.4F);
        }

        @Override public void tick(Run run) {
            int age = run.age();
            // The Geiger: clicks that come slower as it decays.
            if (age > HALF_TOUCH && age < FinisherBeats.HALF_LIFE_LAND && (age < 8 || age % (1 + age / 6) == 0))
                Takedowns.sound(run, run.anchor, SoundEvents.UI_BUTTON_CLICK.value(), .3F, 1.9F + run.target.getRandom().nextFloat() * .2F);
            if (age == HALF_TOUCH) Takedowns.flinch(run.target);
            if (age == FinisherBeats.HALF_LIFE_ASH) Takedowns.sound(run, run.anchor, SoundEvents.SAND_BREAK, 1F, .6F);
        }

        @Override public void land(Run run) {
            ServerPlayer p = run.player;
            LivingEntity foe = run.target;
            Takedowns.strike(run, damage -> {
                RadiationSickness.dose(foe, p, 2, 200);
                StyleHits.hurt(p, foe, damage);
            });
            Takedowns.spill(run, (other, amount) -> {
                RadiationSickness.dose(other, p, 1, 160);
                StyleHits.hurt(p, other, amount);
            });
            Takedowns.sound(run, run.anchor, SoundEvents.SAND_BREAK, 1.3F, .5F);
            Takedowns.sound(run, run.anchor, SoundEvents.BEACON_DEACTIVATE, .8F, 1.3F);
        }

        @Override public void reward(Run run) { ChainReaction.FINISHER.reward(run); }
    };

    public static final Takedowns.Finisher POCKET_SUN = new Takedowns.Finisher() {
        @Override public ResourceLocation power() { return ModAbilities.RADIATION_ID; }
        @Override public String id() { return "pocket_sun"; }
        @Override public int ticks() { return SUN_TICKS; }
        @Override public int landAt() { return SUN_LAND; }

        @Override public void start(Run run) {
            ServerPlayer p = run.player;
            Takedowns.show(run.level(), Piece.FINISH_POCKET_SUN, run.anchor, p.position(), Math.max(.6F, run.target.getBbHeight()), null, p.getId());
            ArmPosePayload.show(p, ArmPose.RAD_CUP, SUN_PRESS);
            Takedowns.sound(run, p.position(), SoundEvents.BEACON_ACTIVATE, .7F, 1.8F);
        }

        @Override public void tick(Run run) {
            int age = run.age();
            if (age > SUN_FORM && age < SUN_PRESS && age % 3 == 0)
                Takedowns.sound(run, run.player.position(), SoundEvents.BEACON_AMBIENT, .8F, 1F + age * .06F);
            if (age == SUN_PRESS) {
                ArmPosePayload.show(run.player, ArmPose.RAD_PALMS, 8);
                Takedowns.flinch(run.target);
            }
        }

        @Override public void land(Run run) {
            ServerPlayer p = run.player;
            LivingEntity foe = run.target;
            // Contained: it hurts nothing but the foe, so there is no spill.
            Takedowns.strike(run, damage -> {
                RadiationSickness.dose(foe, p, 2, 200);
                StyleHits.hurt(p, foe, damage);
            });
            Takedowns.sound(run, run.anchor, SoundEvents.GENERIC_EXPLODE.value(), .9F, 1.5F);
            Takedowns.sound(run, run.anchor, SoundEvents.FIRECHARGE_USE, 1F, .5F);
            CameraShakePayload.sendAround(run.level(), run.anchor, 20, .24F, 6);
        }

        @Override public void reward(Run run) { ChainReaction.FINISHER.reward(run); }
    };
}
