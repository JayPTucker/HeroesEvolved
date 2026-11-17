package com.jayptucker.heroesevolved.hydro;

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
import net.minecraft.world.phys.Vec3;

/**
 * Hydrokinesis's finisher, Breaker: a wave rises under your feet and lifts you where you stand, curls over with you on
 * its crest, and throws its lip down onto the foe while it carries you a block forward and sets you down; it breaks into
 * foam that runs out across the ground. In water or rain the wave is twice the size and its foam drags other foes along.
 * The wave goes back into you: your reservoir is refilled.
 *
 * <p>You never leave the spot you stood on by more than a block and a half: the wave reaches the foe, you don't. The
 * body glides along {@link #ride} ({@link Takedowns#glide}), and {@code HydroArt}'s {@code FINISH_BREAKER} draws the wave
 * from the same path.</p>
 */
public final class Breaker {
    public static final int CREST = 12, LAND = 20, TICKS = 30;
    /** How high the wave lifts you, and how far forward it sets you down. */
    public static final double LIFT = 2.4, FORWARD = 1.0;
    /** Added to the piece's scale in water or rain. */
    public static final float WET = 32;

    private Breaker() {}

    /** Where the rider is at {@code t} ticks: lifted in place from {@code home}, then carried a block toward the foe and set down. */
    public static Vec3 ride(Vec3 home, Vec3 foe, float t) {
        Vec3 fwd = forward(home, foe);
        Vec3 top = home.add(fwd.scale(.2)).add(0, LIFT, 0);
        Vec3 down = home.add(fwd.scale(FORWARD));
        if (t <= CREST) return home.lerp(top, ease(t / CREST));
        if (t <= LAND) {
            float k = (t - CREST) / (float) (LAND - CREST);
            // Over the top and down: forward early, down late, so it curls rather than slides.
            Vec3 flat = top.lerp(new Vec3(down.x, top.y, down.z), ease(Math.min(1, k * 1.6F)));
            return new Vec3(flat.x, Mth.lerp(k * k, top.y, down.y), flat.z);
        }
        return down;
    }

    /** Level, from you toward the foe. */
    public static Vec3 forward(Vec3 home, Vec3 foe) {
        Vec3 toward = foe.subtract(home).multiply(1, 0, 1);
        return toward.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : toward.normalize();
    }

    private static double ease(float k) { k = Mth.clamp(k, 0, 1); return k * k * (3 - 2 * k); }

    private record Ride(Vec3 home, boolean wet) {}

    public static final Takedowns.Finisher FINISHER = new Takedowns.Finisher() {
        @Override public ResourceLocation power() { return ModAbilities.HYDROKINESIS_ID; }
        @Override public String id() { return "breaker"; }
        @Override public int ticks() { return TICKS; }
        @Override public int landAt() { return LAND; }

        @Override public void start(Run run) {
            ServerPlayer p = run.player;
            boolean wet = p.isInWaterOrRain() || run.target.isInWaterOrRain();
            run.state = new Ride(p.position(), wet);
            Takedowns.show(run.level(), Piece.FINISH_BREAKER, p.position(), run.anchor,
                    Math.max(.6F, run.target.getBbHeight()) + (wet ? WET : 0), null, p.getId());
            ArmPosePayload.show(p, ArmPose.WATER_SURF, LAND + 2);
            StyleMotion.cushion(p, TICKS + 30);
            Takedowns.hover(p, true);
            Takedowns.sound(run, p.position(), SoundEvents.GENERIC_SPLASH, 1F, .8F);
        }

        @Override public void tick(Run run) {
            if (!(run.state instanceof Ride ride)) return;
            ServerPlayer p = run.player;
            int age = run.age();
            if (age < LAND) Takedowns.glide(p, ride(ride.home, run.anchor, age), ride(ride.home, run.anchor, age + 1));
            if (age == LAND) {
                Takedowns.still(p);
                Takedowns.hover(p, false);
            }
            if (age == 6) Takedowns.sound(run, p.position(), SoundEvents.AMBIENT_UNDERWATER_ENTER, 1F, 1.2F);
            if (age == CREST) {
                Takedowns.sound(run, p.position(), SoundEvents.TRIDENT_RIPTIDE_2.value(), 1F, .8F);
                ArmPosePayload.show(p, ArmPose.WATER_HEAVE, LAND - CREST + 4);
            }
            if (age == 14 || age == 18) Takedowns.flinch(run.target);
        }

        @Override public void land(Run run) {
            ServerPlayer p = run.player;
            boolean wet = run.state instanceof Ride r && r.wet;
            Takedowns.strike(run, damage -> HydroService.hit(p, run.target, damage));
            Takedowns.spill(run, (other, amount) -> {
                HydroService.hit(p, other, amount);
                HydroService.soak(other, 120);
                StyleHits.away(other, run.anchor, StyleHits.level(p), wet ? 1.1 : .55, .2);
            });
            Takedowns.sound(run, run.anchor, SoundEvents.GENERIC_SPLASH, 1.4F, .55F);
            Takedowns.sound(run, run.anchor, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1F, .7F);
            CameraShakePayload.sendAround(run.level(), run.anchor, 16, .16F, 6);
        }

        @Override public void reward(Run run) {
            HydroService.fill(run.player, HydroService.capacity(run.player));
        }

        @Override public void end(Run run, boolean broken) { Takedowns.hover(run.player, false); }
    };
}
