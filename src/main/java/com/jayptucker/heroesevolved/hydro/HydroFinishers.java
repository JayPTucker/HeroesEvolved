package com.jayptucker.heroesevolved.hydro;

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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Hydrokinesis's alternate finishers, picked at random beside {@link Breaker}; both refill your reservoir, as it does.
 * Neither moves you.
 *
 * <ul>
 *   <li><b>Drowning Bubble</b>: a ball of water closes over the foe's head and bubbles stream up through it while it
 *   struggles; it drops, and the water splashes away.</li>
 *   <li><b>Riptide</b>: a water whip wraps its waist, swings it once round you through the air, and slams it into the
 *   ground where it stood.</li>
 * </ul>
 *
 * <p>{@code HydroArt} draws both. Drowning Bubble: origin the foe's feet, target yours, scale its height. Riptide: bound
 * to the foe (it moves), origin where it stood, target your feet, the bender's entity id in the seed.</p>
 */
public final class HydroFinishers {
    public static final int BUBBLE_CLOSE = 6, BUBBLE_LAND = 22, BUBBLE_TICKS = 32;
    /** Riptide: the whip wraps by WRAP, the swing runs SWING to SLAM, and the blow lands at LAND. */
    public static final int RIPTIDE_WRAP = 5, RIPTIDE_SWING = 6, RIPTIDE_SLAM = 17, RIPTIDE_LAND = 19, RIPTIDE_TICKS = 30;

    private HydroFinishers() {}

    /**
     * Where Riptide's foe is at {@code t}: lifted and swung once round {@code home} (your feet) at its own distance, then
     * brought down onto the spot it started from.
     */
    public static Vec3 riptide(Vec3 home, Vec3 start, float t) {
        Vec3 from = start.subtract(home);
        double radius = Math.max(1.6, Math.sqrt(from.x * from.x + from.z * from.z));
        double angle0 = Math.atan2(from.z, from.x);
        if (t <= RIPTIDE_SWING) return start.add(0, .6 * Mth.clamp(t / RIPTIDE_SWING, 0, 1), 0);
        if (t >= RIPTIDE_LAND) return start;
        float k = Mth.clamp((t - RIPTIDE_SWING) / (float) (RIPTIDE_SLAM - RIPTIDE_SWING), 0, 1);
        double eased = k * k * (3 - 2 * k);
        double angle = angle0 + eased * Math.PI * 2;
        double lift = t <= RIPTIDE_SLAM ? .6 + 1.4 * Math.sin(Math.PI * k) : .6 * (1 - (t - RIPTIDE_SLAM) / (float) (RIPTIDE_LAND - RIPTIDE_SLAM));
        return new Vec3(home.x + Math.cos(angle) * radius, start.y + Math.max(0, lift), home.z + Math.sin(angle) * radius);
    }

    public static final Takedowns.Finisher DROWNING_BUBBLE = new Takedowns.Finisher() {
        @Override public ResourceLocation power() { return ModAbilities.HYDROKINESIS_ID; }
        @Override public String id() { return "drowning_bubble"; }
        @Override public int ticks() { return BUBBLE_TICKS; }
        @Override public int landAt() { return BUBBLE_LAND; }

        @Override public void start(Run run) {
            ServerPlayer p = run.player;
            Takedowns.show(run.level(), Piece.FINISH_BUBBLE, run.anchor, p.position(), Math.max(.6F, run.target.getBbHeight()), null, p.getId());
            ArmPosePayload.show(p, ArmPose.WATER_CALL, BUBBLE_LAND - 2);
            Takedowns.sound(run, run.anchor, SoundEvents.BUCKET_FILL, 1F, .8F);
        }

        @Override public void tick(Run run) {
            int age = run.age();
            if (age == BUBBLE_CLOSE) Takedowns.sound(run, run.anchor, SoundEvents.PLAYER_SPLASH, .8F, 1.2F);
            if (age > BUBBLE_CLOSE && age < BUBBLE_LAND && age % 4 == 0) {
                Takedowns.flinch(run.target);
                Takedowns.sound(run, run.anchor, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, 1F, .8F + age * .02F);
            }
            if (age == BUBBLE_LAND - 2) ArmPosePayload.show(run.player, ArmPose.WATER_BURST, 8);
        }

        @Override public void land(Run run) {
            ServerPlayer p = run.player;
            LivingEntity foe = run.target;
            Takedowns.strike(run, damage -> HydroService.hit(p, foe, damage));
            Takedowns.spill(run, (other, amount) -> {
                HydroService.hit(p, other, amount);
                HydroService.soak(other, 120);
            });
            Takedowns.sound(run, run.anchor, SoundEvents.GENERIC_SPLASH, 1.2F, .9F);
        }

        @Override public void reward(Run run) { Breaker.FINISHER.reward(run); }
    };

    private record Swing(Vec3 home) {}

    public static final Takedowns.Finisher RIPTIDE = new Takedowns.Finisher() {
        @Override public ResourceLocation power() { return ModAbilities.HYDROKINESIS_ID; }
        @Override public String id() { return "riptide"; }
        @Override public int ticks() { return RIPTIDE_TICKS; }
        @Override public int landAt() { return RIPTIDE_LAND; }
        @Override public boolean holds() { return false; }

        @Override public void start(Run run) {
            ServerPlayer p = run.player;
            run.state = new Swing(p.position());
            Takedowns.show(run.level(), Piece.FINISH_RIPTIDE, run.anchor, p.position(), Math.max(.6F, run.target.getBbHeight()), run.target, p.getId());
            ArmPosePayload.show(p, ArmPose.WATER_WHIP, RIPTIDE_SWING + 2);
            Takedowns.sound(run, run.anchor, SoundEvents.TRIDENT_RIPTIDE_1.value(), 1F, 1.1F);
        }

        @Override public void tick(Run run) {
            if (!(run.state instanceof Swing swing)) return;
            ServerPlayer p = run.player;
            LivingEntity foe = run.target;
            int age = run.age();
            if (age <= RIPTIDE_LAND && foe.isAlive()) {
                Vec3 want = riptide(swing.home, run.anchor, age);
                // Only through open air: a wall in the way leaves it where it was this tick.
                if (run.level().noCollision(foe, foe.getDimensions(foe.getPose()).makeBoundingBox(want))) Takedowns.move(foe, want);
                foe.resetFallDistance();
                Takedowns.face(p, foe.getBoundingBox().getCenter());
            }
            if (age == RIPTIDE_WRAP) Takedowns.flinch(foe);
            if (age == RIPTIDE_SWING) {
                ArmPosePayload.show(p, ArmPose.WATER_WHEEL, RIPTIDE_SLAM - RIPTIDE_SWING);
                Takedowns.sound(run, p.position(), SoundEvents.TRIDENT_RIPTIDE_2.value(), 1F, .8F);
            }
            if (age == RIPTIDE_SLAM) ArmPosePayload.show(p, ArmPose.WATER_LASH, 10);
        }

        @Override public void land(Run run) {
            ServerPlayer p = run.player;
            LivingEntity foe = run.target;
            Takedowns.strike(run, damage -> HydroService.hit(p, foe, damage));
            Takedowns.spill(run, (other, amount) -> {
                HydroService.hit(p, other, amount);
                StyleHits.away(other, run.anchor, StyleHits.level(p), .6, .3);
            });
            Takedowns.sound(run, run.anchor, SoundEvents.GENERIC_SPLASH, 1.4F, .6F);
            Takedowns.sound(run, run.anchor, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1F, .7F);
            CameraShakePayload.sendAround(run.level(), run.anchor, 16, .18F, 6);
        }

        @Override public void reward(Run run) { Breaker.FINISHER.reward(run); }
    };
}
