package com.jayptucker.heroesevolved.quake;

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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Quake's alternate finishers, picked at random beside {@link Counterbeat}; both vent Strain as it does.
 *
 * <ul>
 *   <li><b>Fault Line</b>: you stomp, a crack runs from your foot to the foe, the ground heaves it up on a ring of
 *   tilting slabs, and the slabs close over it as it drops.</li>
 *   <li><b>Shatter Point</b>: your palm on it, and it shakes, harder and harder; cracks of light spread over its body,
 *   and it bursts into a ring of dust.</li>
 * </ul>
 *
 * <p>{@code QuakeArt} draws both: origin the foe's feet, target yours, scale its height; Fault Line's piece is bound to
 * the foe, which it lifts.</p>
 */
public final class QuakeFinishers {
    /** Fault Line: the stomp at STOMP, the crack reaches the foe at REACH, it heaves to HEAVE, and drops at LAND. */
    public static final int FAULT_STOMP = 3, FAULT_REACH = 9, FAULT_HEAVE = 13, FAULT_LAND = 16, FAULT_TICKS = 30;
    public static final int SHATTER_TOUCH = 2, SHATTER_LAND = 18, SHATTER_TICKS = 28;

    private QuakeFinishers() {}

    /** How high Fault Line's ground heaves the foe at {@code t}. */
    public static double heave(float t) {
        if (t <= FAULT_REACH) return 0;
        if (t <= FAULT_HEAVE) { float k = (t - FAULT_REACH) / (float) (FAULT_HEAVE - FAULT_REACH); return .9 * (1 - (1 - k) * (1 - k)); }
        if (t <= FAULT_LAND) { float k = (t - FAULT_HEAVE) / (float) (FAULT_LAND - FAULT_HEAVE); return .9 * (1 - k * k); }
        return 0;
    }

    public static final Takedowns.Finisher FAULT_LINE = new Takedowns.Finisher() {
        @Override public ResourceLocation power() { return ModAbilities.QUAKE_ID; }
        @Override public String id() { return "fault_line"; }
        @Override public int ticks() { return FAULT_TICKS; }
        @Override public int landAt() { return FAULT_LAND; }
        @Override public boolean holds() { return false; }

        @Override public void start(Run run) {
            ServerPlayer p = run.player;
            Takedowns.show(run.level(), Piece.FINISH_FAULT, run.anchor, p.position(), Math.max(.6F, run.target.getBbHeight()), run.target, p.getId());
            ArmPosePayload.show(p, ArmPose.QUAKE_ROOT, FAULT_STOMP + 4);
        }

        @Override public void tick(Run run) {
            int age = run.age();
            if (age <= FAULT_LAND && run.target.isAlive()) Takedowns.lift(run, heave(age));
            if (age == FAULT_STOMP) {
                Takedowns.sound(run, run.player.position(), SoundEvents.GENERIC_BIG_FALL, 1.2F, .6F);
                CameraShakePayload.sendAround(run.level(), run.player.position(), 12, .14F, 4);
            }
            if (age > FAULT_STOMP && age < FAULT_REACH && age % 2 == 0)
                Takedowns.sound(run, run.player.position().lerp(run.anchor, (age - FAULT_STOMP) / (float) (FAULT_REACH - FAULT_STOMP)),
                        SoundEvents.STONE_BREAK, .8F, .7F);
            if (age == FAULT_REACH) {
                Takedowns.flinch(run.target);
                ArmPosePayload.show(run.player, ArmPose.QUAKE_UPHEAVAL, 8);
                Takedowns.sound(run, run.anchor, SoundEvents.DEEPSLATE_BREAK, 1.2F, .6F);
            }
        }

        @Override public void land(Run run) {
            ServerPlayer p = run.player;
            float power = QuakeKit.power(p);
            Takedowns.strike(run, damage -> StyleHits.hurt(p, run.target, damage * power));
            Takedowns.spill(run, (other, amount) -> {
                StyleHits.hurt(p, other, amount);
                StyleHits.away(other, run.anchor, StyleHits.level(p), .5, .35);
            });
            Takedowns.sound(run, run.anchor, SoundEvents.GENERIC_EXPLODE.value(), .5F, .6F);
            Takedowns.sound(run, run.anchor, SoundEvents.DEEPSLATE_PLACE, 1.2F, .5F);
            CameraShakePayload.sendAround(run.level(), run.anchor, 20, .26F, 7);
        }

        @Override public void reward(Run run) { Counterbeat.FINISHER.reward(run); }
    };

    public static final Takedowns.Finisher SHATTER_POINT = new Takedowns.Finisher() {
        @Override public ResourceLocation power() { return ModAbilities.QUAKE_ID; }
        @Override public String id() { return "shatter_point"; }
        @Override public int ticks() { return SHATTER_TICKS; }
        @Override public int landAt() { return SHATTER_LAND; }
        // It shakes the foe itself, round the spot it stood on (pinning it too would teleport it twice a tick).
        @Override public boolean holds() { return false; }

        @Override public void start(Run run) {
            ServerPlayer p = run.player;
            Takedowns.show(run.level(), Piece.FINISH_SHATTER, run.anchor, p.position(), Math.max(.6F, run.target.getBbHeight()), run.target, p.getId());
            ArmPosePayload.show(p, ArmPose.QUAKE_TOUCH, SHATTER_LAND - 2);
            Takedowns.sound(run, run.anchor, SoundEvents.AMETHYST_BLOCK_RESONATE, 1F, .5F);
        }

        @Override public void tick(Run run) {
            LivingEntity foe = run.target;
            int age = run.age();
            // It shakes, harder as the cracks spread: a nudge off the spot it is pinned to, put back each tick.
            if (age < SHATTER_LAND && foe.isAlive()) {
                double shake = age <= SHATTER_TOUCH ? 0 : .03 + .07 * (age - SHATTER_TOUCH) / (float) (SHATTER_LAND - SHATTER_TOUCH);
                Vec3 want = run.anchor.add((foe.getRandom().nextFloat() - .5) * shake * 2, 0, (foe.getRandom().nextFloat() - .5) * shake * 2);
                Takedowns.move(foe, run.level().noCollision(foe, foe.getDimensions(foe.getPose()).makeBoundingBox(want)) ? want : run.anchor);
                if (shake > 0 && age % 3 == 0) Takedowns.sound(run, run.anchor, SoundEvents.STONE_HIT, .7F, .6F + age * .04F);
            }
            if (age == 8 || age == 13) Takedowns.flinch(foe);
            if (age == SHATTER_LAND - 2) ArmPosePayload.show(run.player, ArmPose.QUAKE_PALM, 10);
        }

        @Override public void land(Run run) {
            ServerPlayer p = run.player;
            float power = QuakeKit.power(p);
            Takedowns.move(run.target, run.anchor);
            Takedowns.strike(run, damage -> StyleHits.hurt(p, run.target, damage * power));
            Takedowns.spill(run, (other, amount) -> {
                StyleHits.hurt(p, other, amount);
                StyleHits.away(other, run.anchor, StyleHits.level(p), .6, .25);
            });
            Takedowns.sound(run, run.anchor, SoundEvents.DEEPSLATE_BREAK, 1.3F, .5F);
            Takedowns.sound(run, run.anchor, SoundEvents.AMETHYST_CLUSTER_BREAK, 1F, .6F);
            CameraShakePayload.sendAround(run.level(), run.anchor, 16, .2F, 5);
        }

        @Override public void reward(Run run) { Counterbeat.FINISHER.reward(run); }
    };
}
