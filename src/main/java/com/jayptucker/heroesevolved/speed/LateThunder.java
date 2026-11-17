package com.jayptucker.heroesevolved.speed;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.combat.StyleHits;
import com.jayptucker.heroesevolved.combat.StyleMotion;
import com.jayptucker.heroesevolved.combat.Takedowns;
import com.jayptucker.heroesevolved.combat.Takedowns.Run;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.sounds.ModSounds;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Superspeed's finisher, Late Thunder: you coil where you stand while a gold lane burns in behind you, lightning crawls
 * over you and white rings pile up at your shoulders; then one short dash (two blocks at most) and the punch lands. A
 * cone of white rings bursts out behind the foe, it is thrown in a straight line, and the sound barrier's boom arrives a
 * beat after the hit. At 100 Momentum the cone carries the overkill down the whole line behind the foe. Momentum goes to
 * at least 60.
 *
 * <p>The lane is only drawn: the body glides ({@link Takedowns#glide}) the short way in and no further.</p>
 */
public final class LateThunder {
    public static final int COIL = 8, LAND = 12, BOOM = 18, TICKS = 24;
    /** How long the drawn lane behind you is, how far the dash may carry you, and the full-Momentum line. */
    public static final double LANE = 5, DASH = 2, LINE = 8;
    public static final int MOMENTUM = 60;
    /** Added to the piece's scale at full Momentum. */
    public static final float FULL = 32;

    private LateThunder() {}

    private record Lane(Vec3 home, Vec3 front, Vec3 fwd, boolean full) {}

    private static Vec3 at(Lane lane, float age) {
        if (age <= COIL) return lane.home;
        float k = Mth.clamp((age - COIL) / (float) (LAND - COIL), 0, 1);
        return lane.home.lerp(lane.front, k * k * (3 - 2 * k));
    }

    public static final Takedowns.Finisher FINISHER = new Takedowns.Finisher() {
        @Override public ResourceLocation power() { return ModAbilities.SUPERSPEED_ID; }
        @Override public String id() { return "late_thunder"; }
        @Override public int ticks() { return TICKS; }
        @Override public int landAt() { return LAND; }

        @Override public void start(Run run) {
            ServerPlayer p = run.player;
            Vec3 toward = run.anchor.subtract(p.position()).multiply(1, 0, 1);
            Vec3 fwd = toward.lengthSqr() < 1e-4 ? StyleHits.level(p) : toward.normalize();
            // Close only what a punch can't reach, and never more than two blocks.
            double gap = Math.sqrt(toward.lengthSqr()) - (run.target.getBbWidth() * .5 + .9);
            Vec3 front = p.position().add(fwd.scale(Mth.clamp(gap, 0, DASH)));
            boolean full = SpeedService.momentum(p) >= 100;
            run.state = new Lane(p.position(), front, fwd, full);
            Takedowns.show(run.level(), Piece.FINISH_THUNDER, run.anchor, p.position().subtract(fwd.scale(LANE)),
                    Math.max(.6F, run.target.getBbHeight()) + (full ? FULL : 0), p, p.getId());
            StyleMotion.cushion(p, TICKS + 20);
            ArmPosePayload.show(p, ArmPose.SPEED_GUARD, COIL);
            Takedowns.sound(run, p.position(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), .7F, 1.6F);
        }

        @Override public void tick(Run run) {
            if (!(run.state instanceof Lane lane)) return;
            ServerPlayer p = run.player;
            int age = run.age();
            if (age < LAND) Takedowns.glide(p, at(lane, age), at(lane, age + 1));
            if (age == LAND) Takedowns.still(p);
            if (age == COIL) {
                ArmPosePayload.show(p, ArmPose.SPEED_LUNGE, LAND - COIL);
                Takedowns.sound(run, p.position(), SoundEvents.TRIDENT_RIPTIDE_3.value(), .8F, 1.4F);
            }
            if (age == LAND - 1) {
                Takedowns.face(p, run.target.getBoundingBox().getCenter());
                ArmPosePayload.show(p, ArmPose.STYLE_CROSS, 10);
            }
            if (age == BOOM) {
                Takedowns.sound(run, run.anchor, ModSounds.SKYBURST_BOOM.get(), 1.2F, 1F);
                CameraShakePayload.sendAround(run.level(), run.anchor, 32, .3F, 8);
            }
        }

        @Override public void land(Run run) {
            ServerPlayer p = run.player;
            LivingEntity foe = run.target;
            Lane lane = run.state instanceof Lane l ? l : null;
            Vec3 fwd = lane == null ? StyleHits.level(p) : lane.fwd;
            Takedowns.strike(run, damage -> SpeedService.hit(p, foe, damage));
            StyleHits.shove(foe, fwd.scale(1.4).add(0, .3, 0));
            if (lane != null && lane.full) {
                // At full Momentum the cone carries everything left down the line behind the foe.
                for (LivingEntity other : StyleHits.foes(p, run.anchor.add(fwd.scale(LINE / 2)), LINE / 2 + 1)) {
                    Vec3 d = other.position().subtract(run.anchor);
                    double along = d.dot(fwd);
                    if (other == foe || along < 0 || along > LINE || d.subtract(fwd.scale(along)).horizontalDistance() > 1.6) continue;
                    SpeedService.hit(p, other, Math.max(2, run.overkill()));
                    StyleHits.shove(other, fwd.scale(1.1).add(0, .25, 0));
                }
            } else {
                Takedowns.spill(run, (other, amount) -> {
                    SpeedService.hit(p, other, amount);
                    StyleHits.away(other, run.anchor, fwd, .7, .25);
                });
            }
            Takedowns.sound(run, run.anchor, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.2F, .7F);
        }

        @Override public void reward(Run run) { SpeedService.atLeastMomentum(run.player, MOMENTUM); }
    };
}
