package com.jayptucker.heroesevolved.sonic;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.combat.StyleHits;
import com.jayptucker.heroesevolved.combat.Takedowns;
import com.jayptucker.heroesevolved.combat.Takedowns.Run;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Sonic Resonance's finisher, Encore: you flick a Note into the air behind the foe and scream point-blank; the wave goes
 * through the foe, the Note throws it back to your raised palm, you throw it back again, each pass faster and in the next
 * colour (rose, amber, mint, azure, violet), until the Note breaks harmonic and the foe comes apart with it. Two passes
 * always; each 20 Resonance spent adds one, up to five, and at five the last pass Rings every foe within 6 blocks. Half
 * the Resonance spent comes back.
 */
public final class Encore {
    public static final int NOTE = 2, SCREAM = 5, FIRST = 6, LAST = 24, LAND = 26, TICKS = 32;
    public static final double RING_RANGE = 6;

    private Encore() {}

    /** The tick pass {@code i} of {@code passes} arrives at the foe (shared with the drawing: each faster than the last). */
    public static float passAt(int i, int passes) {
        float k = (i + 1) / (float) passes;
        return FIRST + (LAST - FIRST) * (float) Math.sqrt(k);
    }

    private record Song(int passes, int spent) {}

    public static final Takedowns.Finisher FINISHER = new Takedowns.Finisher() {
        @Override public ResourceLocation power() { return ModAbilities.SONIC_RESONANCE_ID; }
        @Override public String id() { return "encore"; }
        @Override public int ticks() { return TICKS; }
        @Override public int landAt() { return LAND; }

        @Override public void start(Run run) {
            ServerPlayer p = run.player;
            int extra = Math.min(3, SonicService.resonance(p) / 20), spent = extra * 20;
            if (spent > 0) SonicService.addResonance(p, -spent);
            Song song = new Song(2 + extra, spent);
            run.state = song;
            Vec3 chest = run.target.getBoundingBox().getCenter();
            Takedowns.show(run.level(), Piece.FINISH_ENCORE, p.getEyePosition().add(0, -.3, 0), chest, song.passes, run.target, p.getId());
            ArmPosePayload.show(p, ArmPose.SONIC_PLUCK, SCREAM);
            Takedowns.sound(run, chest, SoundEvents.NOTE_BLOCK_CHIME.value(), 1F, 1.2F);
        }

        @Override public void tick(Run run) {
            if (!(run.state instanceof Song song)) return;
            int age = run.age();
            if (age == SCREAM) {
                ArmPosePayload.show(run.player, ArmPose.SONIC_SCREAM, 4);
                Takedowns.sound(run, run.player.position(), SoundEvents.WARDEN_SONIC_CHARGE, .5F, 1.8F);
            }
            for (int i = 0; i < song.passes; i++) if (age == Math.round(passAt(i, song.passes))) {
                Takedowns.flinch(run.target);
                if (i % 2 == 1) ArmPosePayload.show(run.player, ArmPose.SONIC_SHOVE, 4);
                Takedowns.sound(run, run.anchor, SoundEvents.NOTE_BLOCK_BELL.value(), 1F, .7F + i * .25F);
            }
        }

        @Override public void land(Run run) {
            ServerPlayer p = run.player;
            boolean crescendo = run.state instanceof Song song && song.passes >= 5;
            Takedowns.strike(run, damage -> SonicService.hit(p, run.target, damage));
            Takedowns.spill(run, (other, amount) -> SonicService.hit(p, other, amount));
            if (crescendo) for (LivingEntity other : StyleHits.foes(p, run.anchor, RING_RANGE))
                if (other != run.target) SonicService.ring(p, other);
            Takedowns.sound(run, run.anchor, SoundEvents.AMETHYST_CLUSTER_BREAK, 1.3F, 1.4F);
            Takedowns.sound(run, run.anchor, SoundEvents.GLASS_BREAK, 1F, 1.6F);
        }

        @Override public void reward(Run run) {
            if (run.state instanceof Song song && song.spent > 0) SonicService.addResonance(run.player, song.spent / 2);
        }
    };
}
