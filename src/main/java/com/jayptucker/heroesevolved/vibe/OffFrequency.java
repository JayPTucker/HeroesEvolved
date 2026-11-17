package com.jayptucker.heroesevolved.vibe;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.combat.FinisherBeats;
import com.jayptucker.heroesevolved.combat.StyleHits;
import com.jayptucker.heroesevolved.combat.Takedowns;
import com.jayptucker.heroesevolved.combat.Takedowns.Run;
import com.jayptucker.heroesevolved.cooldown.CooldownService;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;

/**
 * Vibe's finisher, Off Frequency: your palms go either side of the foe with wave fronts running between them, it splits
 * into three copies tinted blue, red and green like a bad signal, buzzing, the copies drift apart, and you snap your
 * fingers: they blink out one at a time with a breach's pop, and nothing is left. Foes you have marked (Residual Sense,
 * Return Address) within 12 blocks each take the overkill; otherwise it spills nearby. Breach Guard is ready again.
 *
 * <p>The copies are drawn by {@code BodyCopies} from the {@code FINISH_FREQUENCY} piece bound to the foe.</p>
 */
public final class OffFrequency {
    public static final int SNAP = 18;
    public static final double MARK_RANGE = 12;
    private static final ResourceLocation GUARD = ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "vibe_breach_guard");

    private OffFrequency() {}

    public static final Takedowns.Finisher FINISHER = new Takedowns.Finisher() {
        @Override public ResourceLocation power() { return ModAbilities.VIBE_ID; }
        @Override public String id() { return "off_frequency"; }
        @Override public int ticks() { return FinisherBeats.FREQUENCY_TICKS; }
        @Override public int landAt() { return FinisherBeats.FREQUENCY_LAND; }

        @Override public void start(Run run) {
            ServerPlayer p = run.player;
            Takedowns.show(run.level(), Piece.FINISH_FREQUENCY, run.anchor, p.position(), Math.max(.6F, run.target.getBbHeight()),
                    run.target, p.getId());
            ArmPosePayload.show(p, ArmPose.VIBE_OPEN, SNAP);
            Takedowns.sound(run, run.anchor, SoundEvents.BEACON_AMBIENT, 1F, 1.8F);
        }

        @Override public void tick(Run run) {
            int age = run.age();
            if (age == 4 || age == FinisherBeats.FREQUENCY_SPLIT || age == 12) Takedowns.flinch(run.target);
            if (age == FinisherBeats.FREQUENCY_SPLIT) Takedowns.sound(run, run.anchor, SoundEvents.ENDERMAN_STARE, .5F, 1.8F);
            if (age == SNAP) {
                ArmPosePayload.show(run.player, ArmPose.VIBE_PALM, 8);
                Takedowns.sound(run, run.player.position(), SoundEvents.TRIPWIRE_CLICK_ON, 1F, 1.8F);
            }
            if (age >= FinisherBeats.FREQUENCY_GONE && age < FinisherBeats.FREQUENCY_GONE + 3)
                Takedowns.sound(run, run.anchor, SoundEvents.CHORUS_FRUIT_TELEPORT, .7F, 1.4F + (age - FinisherBeats.FREQUENCY_GONE) * .2F);
        }

        @Override public void land(Run run) {
            ServerPlayer p = run.player;
            Takedowns.strike(run, damage -> StyleHits.hurt(p, run.target, damage));
            int marked = 0;
            for (LivingEntity other : StyleHits.foes(p, run.anchor, MARK_RANGE)) {
                if (other == run.target || !VibeSight.marked(p, other)) continue;
                StyleHits.hurt(p, other, Math.max(2, run.overkill()));
                Takedowns.show(run.level(), Piece.FINISH_FREQUENCY, run.anchor.add(0, 1, 0), other.getBoundingBox().getCenter(), .1F, null, p.getId());
                marked++;
            }
            if (marked == 0) Takedowns.spill(run, (other, amount) -> StyleHits.hurt(p, other, amount));
        }

        @Override public void reward(Run run) { CooldownService.reset(run.player, GUARD); }
    };
}
