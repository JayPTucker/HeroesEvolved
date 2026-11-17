package com.jayptucker.heroesevolved.pyro;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.combat.StyleHits;
import com.jayptucker.heroesevolved.combat.StyleMotion;
import com.jayptucker.heroesevolved.combat.Takedowns;
import com.jayptucker.heroesevolved.combat.Takedowns.Run;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Pyrokinesis's alternate finishers, picked at random beside {@link DragonCoil}. Both burn blue at 70 Heat, like the
 * Dragon Coil, and both give its reward (20 Heat).
 *
 * <ul>
 *   <li><b>Phoenix Kick</b>: a backflip kick throws a vertical crescent of flame that knocks the foe into the air; you
 *   land, hop, and an axe kick's crescent slams it back down, where it bursts into a column of flame with two wings of
 *   fire sweeping out.</li>
 *   <li><b>Hearth</b>: palms either side of the foe's head, and a ball of fire gathers round it and squeezes tighter while
 *   the foe flinches; you clap, and the ball falls in on itself and goes up as a column of flame.</li>
 * </ul>
 *
 * <p>{@code PyroArt} draws both from the same beats ({@link #phoenixLift}).</p>
 */
public final class PyroFinishers {
    /** Phoenix Kick: the flip kick lands at FLIP, the foe hangs highest at APEX, the axe kick lands at AXE and the slam at SLAM. */
    public static final int PHOENIX_FLIP = 4, PHOENIX_APEX = 10, PHOENIX_AXE = 13, PHOENIX_SLAM = 16, PHOENIX_TICKS = 28;
    /** Hearth: the ball gathers until SQUEEZE, the clap at CLAP, the column at LAND. */
    public static final int HEARTH_SQUEEZE = 15, HEARTH_CLAP = 18, HEARTH_LAND = 20, HEARTH_TICKS = 30;

    private PyroFinishers() {}

    /** How high the Phoenix Kick holds the foe at {@code t}: up after the flip kick, down after the axe kick. */
    public static double phoenixLift(float t) {
        if (t <= PHOENIX_FLIP) return 0;
        if (t <= PHOENIX_APEX) { float k = (t - PHOENIX_FLIP) / (PHOENIX_APEX - PHOENIX_FLIP); return 1.7 * (1 - (1 - k) * (1 - k)); }
        if (t <= PHOENIX_AXE) return 1.7 + .08 * Mth.sin((t - PHOENIX_APEX) / 3F * Mth.PI);
        if (t <= PHOENIX_SLAM) { float k = (t - PHOENIX_AXE) / (PHOENIX_SLAM - PHOENIX_AXE); return 1.7 * (1 - k * k); }
        return 0;
    }

    private static boolean blue(ServerPlayer p) { return PyroService.heat(p) >= DragonCoil.BLUE_AT || PyroService.isBlue(p); }

    public static final Takedowns.Finisher PHOENIX_KICK = new Takedowns.Finisher() {
        @Override public ResourceLocation power() { return ModAbilities.PYROKINESIS_ID; }
        @Override public String id() { return "phoenix_kick"; }
        @Override public int ticks() { return PHOENIX_TICKS; }
        @Override public int landAt() { return PHOENIX_SLAM; }
        @Override public boolean holds() { return false; }

        @Override public void start(Run run) {
            ServerPlayer p = run.player;
            boolean blue = blue(p);
            run.state = blue;
            PyroGeometry.show(run.level(), Piece.FINISH_PHOENIX, run.anchor, p.position(), Math.max(.6F, run.target.getBbHeight()),
                    blue, run.target);
            ArmPosePayload.show(p, ArmPose.FIRE_FLIP, 8);
            StyleMotion.push(p, new Vec3(0, .38, 0));
            Takedowns.sound(run, p.position(), SoundEvents.BLAZE_SHOOT, .9F, .8F);
            Takedowns.sound(run, p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, .8F, .7F);
        }

        @Override public void tick(Run run) {
            ServerPlayer p = run.player;
            int age = run.age();
            if (age <= PHOENIX_SLAM) Takedowns.lift(run, phoenixLift(age));
            if (age == PHOENIX_FLIP) {
                Takedowns.flinch(run.target);
                Takedowns.sound(run, run.anchor, SoundEvents.FIRECHARGE_USE, .9F, 1.1F);
            }
            if (age == 9) {
                // A second hop for the axe kick, the heel brought down from overhead.
                ArmPosePayload.show(p, ArmPose.FIRE_KICK, 8);
                StyleMotion.push(p, new Vec3(0, .42, 0));
                Takedowns.face(p, run.target.getBoundingBox().getCenter());
                Takedowns.sound(run, p.position(), SoundEvents.BLAZE_SHOOT, 1F, 1F);
            }
            if (age == PHOENIX_AXE) {
                Takedowns.flinch(run.target);
                Takedowns.sound(run, run.anchor, SoundEvents.PLAYER_ATTACK_SWEEP, 1F, .6F);
            }
        }

        @Override public void land(Run run) {
            ServerPlayer p = run.player;
            LivingEntity foe = run.target;
            Takedowns.strike(run, damage -> PyroService.burn(p, foe, damage, 4));
            Takedowns.spill(run, (other, amount) -> {
                PyroService.burn(p, other, amount, 2);
                StyleHits.away(other, run.anchor, StyleHits.level(p), .5, .35);
            });
            Takedowns.sound(run, run.anchor, SoundEvents.FIRECHARGE_USE, 1.2F, .5F);
            Takedowns.sound(run, run.anchor, SoundEvents.GENERIC_EXPLODE.value(), .5F, 1.4F);
            CameraShakePayload.sendAround(run.level(), run.anchor, 16, .2F, 6);
        }

        @Override public void reward(Run run) { DragonCoil.FINISHER.reward(run); }
    };

    public static final Takedowns.Finisher HEARTH = new Takedowns.Finisher() {
        @Override public ResourceLocation power() { return ModAbilities.PYROKINESIS_ID; }
        @Override public String id() { return "hearth"; }
        @Override public int ticks() { return HEARTH_TICKS; }
        @Override public int landAt() { return HEARTH_LAND; }

        @Override public void start(Run run) {
            ServerPlayer p = run.player;
            boolean blue = blue(p);
            PyroGeometry.show(run.level(), Piece.FINISH_HEARTH, run.anchor, p.position(), Math.max(.6F, run.target.getBbHeight()),
                    blue, null);
            ArmPosePayload.show(p, ArmPose.FIRE_FORGE, HEARTH_CLAP);
            Takedowns.sound(run, run.anchor, SoundEvents.FIRECHARGE_USE, .8F, .7F);
        }

        @Override public void tick(Run run) {
            ServerPlayer p = run.player;
            int age = run.age();
            if (age == 6 || age == 10 || age == 13 || age == HEARTH_SQUEEZE) {
                Takedowns.flinch(run.target);
                Takedowns.sound(run, run.anchor, SoundEvents.BLAZE_SHOOT, .5F + age * .03F, .6F + age * .04F);
            }
            if (age == HEARTH_CLAP) {
                ArmPosePayload.show(p, ArmPose.FLIGHT_CLAP, 10);
                Takedowns.sound(run, p.position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1F, .6F);
            }
        }

        @Override public void land(Run run) {
            ServerPlayer p = run.player;
            LivingEntity foe = run.target;
            Takedowns.strike(run, damage -> PyroService.burn(p, foe, damage, 4));
            Takedowns.spill(run, (other, amount) -> {
                PyroService.burn(p, other, amount, 2);
                StyleHits.away(other, run.anchor, StyleHits.level(p), .4, .3);
            });
            Takedowns.sound(run, run.anchor, SoundEvents.BLAZE_SHOOT, 1.2F, .45F);
            Takedowns.sound(run, run.anchor, SoundEvents.FIRECHARGE_USE, 1.1F, .55F);
            CameraShakePayload.sendAround(run.level(), run.anchor, 16, .16F, 5);
        }

        @Override public void reward(Run run) { DragonCoil.FINISHER.reward(run); }
    };
}
