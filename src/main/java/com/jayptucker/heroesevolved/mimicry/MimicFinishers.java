package com.jayptucker.heroesevolved.mimicry;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.combat.FinisherBeats;
import com.jayptucker.heroesevolved.combat.StyleHits;
import com.jayptucker.heroesevolved.combat.Takedowns;
import com.jayptucker.heroesevolved.combat.Takedowns.Finisher;
import com.jayptucker.heroesevolved.combat.Takedowns.Run;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;

import java.util.ArrayList;
import java.util.List;

/**
 * Empathic Mimicry's alternate finishers, picked at random beside {@link TakeItIn} (when nothing is channeled; while a
 * power is channeled, its finishers play instead).
 *
 * <ul>
 *   <li><b>Echo</b>: a gold outline of the foe rises in front of it, facing it, and hits it with its own attack. Like
 *   Take It In, a foe with a power is imprinted.</li>
 *   <li><b>Borrowed Strength</b>: one finisher picked at random from the powers you have kept, played as that power's
 *   own, with a gold shimmer over you as it starts. It only comes up once you have kept a power with a finisher; it
 *   gives no power's reward (the stamina back still comes).</li>
 * </ul>
 *
 * <p>Echo's piece is bound to the foe ({@code BodyCopies} draws the outline; {@code MimicArt} the threads); Borrowed
 * Strength's shimmer is bound to you.</p>
 */
public final class MimicFinishers {
    private MimicFinishers() {}

    public static final Takedowns.Finisher ECHO = new Takedowns.Finisher() {
        @Override public ResourceLocation power() { return ModAbilities.MIMICRY_ID; }
        @Override public String id() { return "echo"; }
        @Override public int ticks() { return FinisherBeats.ECHO_TICKS; }
        @Override public int landAt() { return FinisherBeats.ECHO_LAND; }

        @Override public void start(Run run) {
            ServerPlayer p = run.player;
            run.state = MimicryService.imprintOf(p, run.target);
            Takedowns.show(run.level(), Piece.FINISH_ECHO, run.anchor, p.position(), Math.max(.6F, run.target.getBbHeight()), run.target, p.getId());
            ArmPosePayload.show(p, ArmPose.MIMIC_TOUCH, FinisherBeats.ECHO_LAND);
            Takedowns.sound(run, run.anchor, SoundEvents.AMETHYST_BLOCK_RESONATE, 1F, 1.4F);
        }

        @Override public void tick(Run run) {
            int age = run.age();
            if (age == FinisherBeats.ECHO_RISE) Takedowns.sound(run, run.anchor, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1F, 1.1F);
            // Its own swing, which the outline (drawn from its own pose) throws back at it.
            if (age == FinisherBeats.ECHO_LAND - 3) run.target.swing(InteractionHand.MAIN_HAND, true);
            if (age == FinisherBeats.ECHO_LAND - 1) Takedowns.sound(run, run.anchor, SoundEvents.PLAYER_ATTACK_STRONG, 1F, .8F);
        }

        @Override public void land(Run run) {
            ServerPlayer p = run.player;
            Takedowns.strike(run, damage -> StyleHits.hurt(run.target, p.damageSources().indirectMagic(p, p), damage));
            Takedowns.spill(run, (other, amount) -> StyleHits.hurt(other, p.damageSources().indirectMagic(p, p), amount));
            Takedowns.sound(run, run.anchor, SoundEvents.AMETHYST_CLUSTER_BREAK, 1F, 1.3F);
        }

        @Override public void reward(Run run) { TakeItIn.FINISHER.reward(run); }
    };

    /** The kept powers that have a finisher of their own, other than Mimicry. */
    static List<ResourceLocation> keptWithFinishers(ServerPlayer player) {
        List<ResourceLocation> out = new ArrayList<>();
        for (MimicryService.Imprint imprint : MimicryService.memory(player).kept()) {
            ResourceLocation power = imprint.power();
            if (power != null && !power.equals(ModAbilities.MIMICRY_ID) && Takedowns.forPower(power) != null && !out.contains(power))
                out.add(power);
        }
        return out;
    }

    public static final Takedowns.Finisher BORROWED_STRENGTH = new Takedowns.Finisher() {
        @Override public ResourceLocation power() { return ModAbilities.MIMICRY_ID; }
        @Override public String id() { return "borrowed_strength"; }
        @Override public int ticks() { return TakeItIn.TICKS; }
        @Override public int landAt() { return TakeItIn.LAND; }
        @Override public boolean available(ServerPlayer player) { return !keptWithFinishers(player).isEmpty(); }

        @Override public Finisher resolve(ServerPlayer player) {
            List<ResourceLocation> kept = keptWithFinishers(player);
            if (kept.isEmpty()) return TakeItIn.FINISHER;
            List<Finisher> pool = new ArrayList<>();
            for (ResourceLocation power : kept) pool.addAll(Takedowns.variants(power));
            return borrowed(pool.get(player.getRandom().nextInt(pool.size())));
        }

        // Never played itself: resolve() hands over a kept power's finisher.
        @Override public void start(Run run) {}
        @Override public void tick(Run run) {}
        @Override public void land(Run run) {}
    };

    /** A kept power's finisher played by an empath: the same move, a gold shimmer as it starts, and no power's reward. */
    static Finisher borrowed(Finisher inner) {
        return new Finisher() {
            @Override public ResourceLocation power() { return inner.power(); }
            @Override public String id() { return inner.id(); }
            @Override public int ticks() { return inner.ticks(); }
            @Override public int landAt() { return inner.landAt(); }
            @Override public double reach() { return inner.reach(); }
            @Override public boolean holds() { return inner.holds(); }
            @Override public void start(Run run) {
                Takedowns.show(run.level(), Piece.FINISH_BORROW, run.player.position(), run.anchor, 1, run.player, run.player.getId());
                Takedowns.sound(run, run.player.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1F, 1.6F);
                inner.start(run);
            }
            @Override public void tick(Run run) { inner.tick(run); }
            @Override public void land(Run run) { inner.land(run); }
            @Override public void end(Run run, boolean broken) { inner.end(run, broken); }
        };
    }
}
