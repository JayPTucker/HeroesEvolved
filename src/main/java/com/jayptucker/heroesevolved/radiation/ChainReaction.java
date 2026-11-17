package com.jayptucker.heroesevolved.radiation;

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

/**
 * Induced Radioactivity's finisher, Chain Reaction: you flick a bead of white light into the foe's chest, it splits
 * inside them (two, four, eight, sixteen motes) while the body glows from within, a contained flash holds the outline in
 * light, and the spare motes fly to the nearest foes and split again on them. It vents 25 Critical Mass; during a
 * meltdown warning it vents 40 and stands the warning down, so a low foe is an emergency exit.
 */
public final class ChainReaction {
    public static final int FLICK = 3, SPLIT = 4, FLASH = 14, LAND = 16, TICKS = 26;
    /** How far the spare motes fly. */
    public static final double REACH = 6;
    public static final int VENT = 25, EMERGENCY = 40;

    private ChainReaction() {}

    public static final Takedowns.Finisher FINISHER = new Takedowns.Finisher() {
        @Override public ResourceLocation power() { return ModAbilities.RADIATION_ID; }
        @Override public String id() { return "chain_reaction"; }
        @Override public int ticks() { return TICKS; }
        @Override public int landAt() { return LAND; }

        @Override public void start(Run run) {
            ServerPlayer p = run.player;
            Takedowns.show(run.level(), Piece.FINISH_CHAIN, p.getEyePosition().add(0, -.4, 0), run.anchor,
                    Math.max(.6F, run.target.getBbHeight()), run.target, p.getId());
            ArmPosePayload.show(p, ArmPose.ELECTRO_POINT, 8);
            Takedowns.sound(run, p.position(), SoundEvents.BEACON_POWER_SELECT, .8F, 1.8F);
        }

        @Override public void tick(Run run) {
            int age = run.age();
            if (age == FLICK) Takedowns.flinch(run.target);
            if (age >= SPLIT && age < FLASH && (age - SPLIT) % 3 == 0)
                Takedowns.sound(run, run.anchor, SoundEvents.BEACON_AMBIENT, 1F, 1.2F + (age - SPLIT) * .08F);
        }

        @Override public void land(Run run) {
            ServerPlayer p = run.player;
            LivingEntity foe = run.target;
            Takedowns.strike(run, damage -> {
                RadiationSickness.dose(foe, p, 2, 200);
                StyleHits.hurt(p, foe, damage);
            });
            // The spare motes fly to the nearest foes, where they split again.
            for (LivingEntity other : Takedowns.others(run, REACH, 3)) {
                RadiationSickness.dose(other, p, 1, 160);
                StyleHits.hurt(p, other, Math.max(2, run.overkill()));
                Takedowns.show(run.level(), Piece.FINISH_CHAIN, run.anchor.add(0, foe.getBbHeight() * .6, 0), other.position(),
                        .1F, other, p.getId());
            }
            Takedowns.sound(run, run.anchor, SoundEvents.GENERIC_EXPLODE.value(), .6F, 1.6F);
            Takedowns.sound(run, run.anchor, SoundEvents.BEACON_DEACTIVATE, 1F, 1.5F);
            CameraShakePayload.sendAround(run.level(), run.anchor, 20, .2F, 6);
        }

        @Override public void reward(Run run) {
            ServerPlayer p = run.player;
            if (RadiationService.critical(p)) RadiationService.defuse(p, Math.max(0, RadiationService.mass(p) - EMERGENCY));
            else RadiationService.vent(p, VENT);
        }
    };
}
