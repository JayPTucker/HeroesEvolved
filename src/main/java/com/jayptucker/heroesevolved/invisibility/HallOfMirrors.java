package com.jayptucker.heroesevolved.invisibility;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.combat.FinisherBeats;
import com.jayptucker.heroesevolved.combat.StyleHits;
import com.jayptucker.heroesevolved.combat.Takedowns;
import com.jayptucker.heroesevolved.combat.Takedowns.Run;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

/**
 * Invisibility's finisher, Hall of Mirrors: you fade, and five panes of bent light rise round the foe, each holding a
 * reflection of you turned toward it; the foe can't tell which is real. You step out of the pane behind it with a Force
 * Palm, and every pane shatters inward into tumbling facets. Three seconds of Fade afterwards, free, to get away.
 *
 * <p>Your reflections and your hiding are drawn by {@code BodyCopies} from the {@code FINISH_MIRRORS} piece bound to you;
 * the panes by {@code ForceArt}. Both read {@link FinisherBeats}.</p>
 */
public final class HallOfMirrors {
    public static final int FADE_TICKS = 60;

    private HallOfMirrors() {}

    public static final Takedowns.Finisher FINISHER = new Takedowns.Finisher() {
        @Override public ResourceLocation power() { return ModAbilities.INVISIBILITY_ID; }
        @Override public String id() { return "hall_of_mirrors"; }
        @Override public int ticks() { return FinisherBeats.MIRRORS_TICKS; }
        @Override public int landAt() { return FinisherBeats.MIRRORS_LAND; }

        @Override public void start(Run run) {
            ServerPlayer p = run.player;
            run.state = p.position();
            Takedowns.show(run.level(), Piece.FINISH_MIRRORS, run.anchor, p.position(), Math.max(.6F, run.target.getBbHeight()), p,
                    run.target.getId());
            ArmPosePayload.show(p, ArmPose.FORCE_BUILD, FinisherBeats.MIRRORS_STEP);
            Takedowns.sound(run, p.position(), SoundEvents.ILLUSIONER_MIRROR_MOVE, 1F, 1.2F);
        }

        @Override public void tick(Run run) {
            if (!(run.state instanceof Vec3 home)) return;
            ServerPlayer p = run.player;
            int age = run.age();
            if (age >= FinisherBeats.MIRRORS_RISE && age < FinisherBeats.MIRRORS_RISE + FinisherBeats.MIRRORS_PANES)
                Takedowns.sound(run, FinisherBeats.pane(run.anchor, home, age - FinisherBeats.MIRRORS_RISE, FinisherBeats.MIRRORS_PANES),
                        SoundEvents.AMETHYST_BLOCK_PLACE, .8F, 1.5F);
            if (age == 11) Takedowns.flinch(run.target);
            if (age == FinisherBeats.MIRRORS_STEP) {
                Vec3 behind = FinisherBeats.pane(run.anchor, home, FinisherBeats.behind(FinisherBeats.MIRRORS_PANES), FinisherBeats.MIRRORS_PANES);
                behind = new Vec3(behind.x, p.getY(), behind.z);
                if (p.serverLevel().noCollision(p, p.getDimensions(p.getPose()).makeBoundingBox(behind))) Takedowns.move(p, behind);
                Takedowns.face(p, run.target.getBoundingBox().getCenter());
                ArmPosePayload.show(p, ArmPose.FORCE_PUSH, 10);
                Takedowns.sound(run, p.position(), SoundEvents.ILLUSIONER_CAST_SPELL, 1F, 1.3F);
            }
        }

        @Override public void land(Run run) {
            ServerPlayer p = run.player;
            Takedowns.strike(run, damage -> StyleHits.hurt(p, run.target, damage));
            Takedowns.spill(run, (other, amount) -> {
                StyleHits.hurt(p, other, amount);
                StyleHits.away(other, run.anchor, StyleHits.level(p), .7, .2);
            });
            Takedowns.sound(run, run.anchor, SoundEvents.GLASS_BREAK, 1.3F, .9F);
            Takedowns.sound(run, run.anchor, SoundEvents.AMETHYST_CLUSTER_BREAK, 1F, 1.2F);
        }

        @Override public void reward(Run run) { InvisibilityService.fade(run.player, FADE_TICKS, false); }
    };
}
