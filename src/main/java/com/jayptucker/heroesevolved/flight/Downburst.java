package com.jayptucker.heroesevolved.flight;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.combat.StyleHits;
import com.jayptucker.heroesevolved.combat.StyleMotion;
import com.jayptucker.heroesevolved.combat.Takedowns;
import com.jayptucker.heroesevolved.combat.Takedowns.Run;
import com.jayptucker.heroesevolved.cooldown.CooldownService;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.sounds.ModSounds;
import com.jayptucker.heroesevolved.speed.SpeedService;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Flight's finisher, Downburst: you shoot straight up from where you stand with a ring of vapour at your feet, hang and
 * flip head-down, and dive back onto your own spot. The air you drove down bursts out of the ground and rolls along it
 * to the foe as a front of rings, flattens it and blows a ring of dust flat outward; everything in that ring takes the
 * overkill. You never touch it, and you land where you took off. Under a low ceiling the rise is shorter. Skyburst is
 * ready again.
 *
 * <p>The body glides ({@link Takedowns#glide}), so the rise and the dive are drawn every frame, not once a tick.</p>
 */
public final class Downburst {
    public static final int RISE = 8, FLIP = 11, DIVE = 15, LAND = 19, TICKS = 28;
    public static final double HEIGHT = 4.5, RING = 3.5;
    private static final ResourceLocation SKYBURST = ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "flight_launch");

    private Downburst() {}

    private record Flight(Vec3 home, Vec3 apex) {}

    /** Where the flyer is at {@code age}: up, hanging, and back down onto the spot they left. */
    private static Vec3 path(Flight f, float age) {
        if (age <= RISE) return f.home.lerp(f.apex, ease(age / RISE));
        if (age <= FLIP) return f.apex.add(0, .15 * Mth.sin((age - RISE) / (FLIP - RISE) * Mth.PI), 0);
        if (age <= DIVE) {
            float k = (age - FLIP) / (float) (DIVE - FLIP);
            return f.apex.lerp(f.home, k * k);
        }
        return f.home;
    }

    public static final Takedowns.Finisher FINISHER = new Takedowns.Finisher() {
        @Override public ResourceLocation power() { return ModAbilities.FLIGHT_ID; }
        @Override public String id() { return "downburst"; }
        @Override public int ticks() { return TICKS; }
        @Override public int landAt() { return LAND; }

        @Override public void start(Run run) {
            ServerPlayer p = run.player;
            Vec3 apex = SpeedService.swept(p, new Vec3(0, HEIGHT, 0));
            run.state = new Flight(p.position(), apex);
            Takedowns.hover(p, true);
            StyleMotion.cushion(p, TICKS + 40);
            Takedowns.show(run.level(), Piece.FINISH_DOWNBURST, run.anchor, p.position(), Math.max(.6F, run.target.getBbHeight()),
                    p, p.getId());
            ArmPosePayload.show(p, ArmPose.FLIGHT_BRACE, RISE);
            Takedowns.sound(run, p.position(), ModSounds.FLIGHT_LAUNCH.get(), 1F, 1.1F);
        }

        @Override public void tick(Run run) {
            if (!(run.state instanceof Flight f)) return;
            ServerPlayer p = run.player;
            int age = run.age();
            if (age < DIVE) Takedowns.glide(p, path(f, age), path(f, age + 1));
            if (age == DIVE) {
                Takedowns.still(p);
                Takedowns.hover(p, false);
                ArmPosePayload.show(p, ArmPose.FLIGHT_CLAP, 10);
                Takedowns.sound(run, f.home, ModSounds.SKYBURST_BOOM.get(), .7F, 1.6F);
                CameraShakePayload.sendAround(run.level(), f.home, 16, .14F, 4);
            }
            if (age == RISE) {
                ArmPosePayload.show(p, ArmPose.FLIGHT_ROLL, FLIP - RISE + 2);
                Takedowns.face(p, run.target.getBoundingBox().getCenter());
            }
            if (age == FLIP + 1) {
                ArmPosePayload.show(p, ArmPose.FLIGHT_BRACE, DIVE - FLIP);
                Takedowns.sound(run, p.position(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 1F, .7F);
            }
        }

        @Override public void land(Run run) {
            ServerPlayer p = run.player;
            LivingEntity foe = run.target;
            Takedowns.strike(run, damage -> StyleHits.hurt(p, foe, damage));
            for (LivingEntity other : Takedowns.others(run, RING, 6)) {
                StyleHits.hurt(p, other, Math.max(2, run.overkill()));
                StyleHits.away(other, run.anchor, StyleHits.level(p), .8, .15);
            }
            Takedowns.sound(run, run.anchor, ModSounds.SKYBURST_BOOM.get(), 1F, 1.3F);
            CameraShakePayload.sendAround(run.level(), run.anchor, 24, .28F, 7);
        }

        @Override public void reward(Run run) { CooldownService.reset(run.player, SKYBURST); }

        @Override public void end(Run run, boolean broken) { Takedowns.hover(run.player, false); }
    };

    private static double ease(float k) { k = Mth.clamp(k, 0, 1); return 1 - (1 - k) * (1 - k); }
}
