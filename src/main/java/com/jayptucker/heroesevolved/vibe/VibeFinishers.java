package com.jayptucker.heroesevolved.vibe;

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
import net.minecraft.world.phys.Vec3;

/**
 * Vibe's alternate finishers, picked at random beside {@link OffFrequency}; both give its reward (Breach Guard ready
 * again).
 *
 * <ul>
 *   <li><b>Breach Drop</b>: a breach opens flat under the foe; it falls through, and the breach snaps shut over it.</li>
 *   <li><b>Point Blank</b>: both palms on the foe, wave fronts gathering in them, and a Vibe Blast goes straight through
 *   it and out the far side.</li>
 * </ul>
 *
 * <p>{@code VibeArt} draws both. Breach Drop: bound to the foe ({@code BodyCopies} drops it through), origin its feet,
 * target yours. Point Blank: bound to you, origin your feet, target the foe's chest. Scale the foe's height.</p>
 */
public final class VibeFinishers {
    public static final int BLANK_GATHER = 2, BLANK_LAND = 12, BLANK_TICKS = 26;

    private VibeFinishers() {}

    public static final Takedowns.Finisher BREACH_DROP = new Takedowns.Finisher() {
        @Override public ResourceLocation power() { return ModAbilities.VIBE_ID; }
        @Override public String id() { return "breach_drop"; }
        @Override public int ticks() { return FinisherBeats.BREACH_TICKS; }
        @Override public int landAt() { return FinisherBeats.BREACH_LAND; }

        @Override public void start(Run run) {
            ServerPlayer p = run.player;
            Takedowns.show(run.level(), Piece.FINISH_BREACH, run.anchor, p.position(), Math.max(.6F, run.target.getBbHeight()), run.target, p.getId());
            ArmPosePayload.show(p, ArmPose.VIBE_OPEN, FinisherBeats.BREACH_FALL);
            Takedowns.sound(run, run.anchor, SoundEvents.BEACON_AMBIENT, 1F, 1.6F);
        }

        @Override public void tick(Run run) {
            int age = run.age();
            if (age == FinisherBeats.BREACH_OPEN) Takedowns.sound(run, run.anchor, SoundEvents.ENDERMAN_TELEPORT, .8F, .7F);
            if (age == FinisherBeats.BREACH_FALL) {
                Takedowns.flinch(run.target);
                ArmPosePayload.show(run.player, ArmPose.VIBE_LOCK, 10);
            }
        }

        @Override public void land(Run run) {
            ServerPlayer p = run.player;
            Takedowns.strike(run, damage -> StyleHits.hurt(p, run.target, damage));
            Takedowns.spill(run, (other, amount) -> StyleHits.hurt(p, other, amount));
            Takedowns.sound(run, run.anchor, SoundEvents.CHORUS_FRUIT_TELEPORT, 1F, .6F);
            Takedowns.sound(run, run.anchor, SoundEvents.IRON_TRAPDOOR_CLOSE, 1F, .6F);
        }

        @Override public void reward(Run run) { OffFrequency.FINISHER.reward(run); }
    };

    public static final Takedowns.Finisher POINT_BLANK = new Takedowns.Finisher() {
        @Override public ResourceLocation power() { return ModAbilities.VIBE_ID; }
        @Override public String id() { return "point_blank"; }
        @Override public int ticks() { return BLANK_TICKS; }
        @Override public int landAt() { return BLANK_LAND; }

        @Override public void start(Run run) {
            ServerPlayer p = run.player;
            Takedowns.show(run.level(), Piece.FINISH_POINT_BLANK, p.position(), run.target.getBoundingBox().getCenter(),
                    Math.max(.6F, run.target.getBbHeight()), p, run.target.getId());
            ArmPosePayload.show(p, ArmPose.VIBE_CROSS, BLANK_LAND - 2);
            Takedowns.sound(run, p.position(), SoundEvents.BEACON_POWER_SELECT, .8F, 1.6F);
        }

        @Override public void tick(Run run) {
            int age = run.age();
            if (age == BLANK_LAND - 2) {
                ArmPosePayload.show(run.player, ArmPose.VIBE_PALM, 10);
                Takedowns.sound(run, run.player.position(), SoundEvents.WARDEN_SONIC_CHARGE, .4F, 2F);
            }
        }

        @Override public void land(Run run) {
            ServerPlayer p = run.player;
            Vec3 fwd = run.anchor.subtract(p.position()).multiply(1, 0, 1);
            fwd = fwd.lengthSqr() < 1e-4 ? StyleHits.level(p) : fwd.normalize();
            Takedowns.strike(run, damage -> StyleHits.hurt(p, run.target, damage));
            StyleHits.shove(run.target, fwd.scale(1.1).add(0, .25, 0));
            Vec3 push = fwd;
            Takedowns.spill(run, (other, amount) -> {
                StyleHits.hurt(p, other, amount);
                StyleHits.away(other, run.anchor, push, .8, .2);
            });
            Takedowns.sound(run, run.anchor, SoundEvents.WARDEN_SONIC_BOOM, .5F, 1.6F);
            CameraShakePayload.sendAround(run.level(), run.anchor, 16, .2F, 5);
        }

        @Override public void reward(Run run) { OffFrequency.FINISHER.reward(run); }
    };
}
