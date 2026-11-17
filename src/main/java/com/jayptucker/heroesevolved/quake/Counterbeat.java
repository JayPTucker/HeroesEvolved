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

/**
 * Quake's finisher, Counterbeat: a palm flat on the foe's chest, and she feels its heartbeat, a double pulse coming
 * faster and faster while her forearm shakes in time; then one counter-pulse from her palm, the rings closing inward,
 * and silence: a flat ring over the ground and no more beats. It spends Strain instead of adding it: it always vents
 * 50, a way out of the red zone that also ends a fight.
 */
public final class Counterbeat {
    /** The heartbeats the foe's body shows (flinches), coming closer together, then the counter-pulse and the blow. */
    public static final int[] BEATS = {4, 9, 13, 16, 18, 19};
    public static final int COUNTER = 19, LAND = 22, TICKS = 28, RELIEF = 50;

    private Counterbeat() {}

    public static final Takedowns.Finisher FINISHER = new Takedowns.Finisher() {
        @Override public ResourceLocation power() { return ModAbilities.QUAKE_ID; }
        @Override public String id() { return "counterbeat"; }
        @Override public int ticks() { return TICKS; }
        @Override public int landAt() { return LAND; }

        @Override public void start(Run run) {
            ServerPlayer p = run.player;
            Takedowns.show(run.level(), Piece.FINISH_BEAT, run.anchor, p.position(), Math.max(.6F, run.target.getBbHeight()),
                    run.target, p.getId());
            ArmPosePayload.show(p, ArmPose.QUAKE_FEEL, COUNTER);
        }

        @Override public void tick(Run run) {
            int age = run.age();
            for (int i = 0; i < BEATS.length; i++) if (age == BEATS[i]) {
                Takedowns.flinch(run.target);
                Takedowns.sound(run, run.anchor, SoundEvents.WARDEN_HEARTBEAT, .6F + i * .12F, .8F + i * .12F);
            }
            if (age == COUNTER) {
                ArmPosePayload.show(run.player, ArmPose.QUAKE_PALM, 10);
                Takedowns.sound(run, run.anchor, SoundEvents.WARDEN_SONIC_BOOM, .35F, 1.8F);
            }
        }

        @Override public void land(Run run) {
            ServerPlayer p = run.player;
            float power = QuakeKit.power(p);
            Takedowns.strike(run, damage -> StyleHits.hurt(p, run.target, damage * power));
            Takedowns.spill(run, (other, amount) -> {
                StyleHits.hurt(p, other, amount);
                StyleHits.away(other, run.anchor, StyleHits.level(p), .6, .3);
            });
            Takedowns.sound(run, run.anchor, SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F, .5F);
            CameraShakePayload.sendAround(run.level(), run.anchor, 16, .2F, 6);
        }

        @Override public void reward(Run run) { QuakeKit.relieve(run.player, RELIEF); }
    };
}
