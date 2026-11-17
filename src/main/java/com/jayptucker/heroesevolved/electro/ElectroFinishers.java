package com.jayptucker.heroesevolved.electro;

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
 * Electrokinesis's alternate finishers, picked at random beside {@link ShortCircuit}; both give its reward (25 Charge).
 *
 * <ul>
 *   <li><b>Lightning Rod</b>: one hand up to the sky, the other toward the foe. Static climbs it while a dark knot of
 *   charge gathers overhead and a warning ring closes at its feet; a bolt comes down through it and on into your open
 *   palm, and it earths there.</li>
 *   <li><b>Overload</b>: you grab it and pump current in. A corona swells round it, brighter and brighter, forks
 *   crawling from it into the ground, its flinches coming faster, until it pops in a white flash and a ground pulse.</li>
 * </ul>
 *
 * <p>{@code ElectroArt} draws both; each piece is bound to the foe, origin its feet, target yours, the electrokinetic's
 * entity id in the seed, scale its height.</p>
 */
public final class ElectroFinishers {
    public static final int ROD_CALL = 2, ROD_LAND = 18, ROD_TICKS = 30;
    public static final int OVERLOAD_GRAB = 3, OVERLOAD_LAND = 20, OVERLOAD_TICKS = 30;
    /** Overload's flinches, closer and closer together. */
    public static final int[] OVERLOAD_JOLTS = {6, 10, 13, 15, 17, 18, 19};

    private ElectroFinishers() {}

    public static final Takedowns.Finisher LIGHTNING_ROD = new Takedowns.Finisher() {
        @Override public ResourceLocation power() { return ModAbilities.ELECTROKINESIS_ID; }
        @Override public String id() { return "lightning_rod"; }
        @Override public int ticks() { return ROD_TICKS; }
        @Override public int landAt() { return ROD_LAND; }

        @Override public void start(Run run) {
            ServerPlayer p = run.player;
            Takedowns.show(run.level(), Piece.FINISH_ROD, run.anchor, p.position(), Math.max(.6F, run.target.getBbHeight()), run.target, p.getId());
            ArmPosePayload.show(p, ArmPose.ELECTRO_CALL, ROD_LAND - 2);
            Takedowns.sound(run, run.anchor, SoundEvents.BEACON_ACTIVATE, .6F, 1.6F);
        }

        @Override public void tick(Run run) {
            int age = run.age();
            if (age == 6 || age == 11) Takedowns.flinch(run.target);
            if (age == 10) Takedowns.sound(run, run.anchor.add(0, 8, 0), SoundEvents.LIGHTNING_BOLT_THUNDER, .5F, 1.6F);
            if (age == ROD_LAND - 2) ArmPosePayload.show(run.player, ArmPose.ELECTRO_GROUND, 10);
        }

        @Override public void land(Run run) {
            ServerPlayer p = run.player;
            LivingEntity foe = run.target;
            Takedowns.strike(run, damage -> ElectroService.shock(p, foe, damage));
            Takedowns.spill(run, (other, amount) -> ElectroService.shock(p, other, amount));
            Takedowns.sound(run, run.anchor, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.1F, 1F);
            Takedowns.sound(run, run.anchor, SoundEvents.LIGHTNING_BOLT_THUNDER, .6F, 1.3F);
            CameraShakePayload.sendAround(run.level(), run.anchor, 24, .25F, 6);
        }

        @Override public void reward(Run run) { ShortCircuit.FINISHER.reward(run); }
    };

    public static final Takedowns.Finisher OVERLOAD = new Takedowns.Finisher() {
        @Override public ResourceLocation power() { return ModAbilities.ELECTROKINESIS_ID; }
        @Override public String id() { return "overload"; }
        @Override public int ticks() { return OVERLOAD_TICKS; }
        @Override public int landAt() { return OVERLOAD_LAND; }

        @Override public void start(Run run) {
            ServerPlayer p = run.player;
            Takedowns.show(run.level(), Piece.FINISH_OVERLOAD, run.anchor, p.position(), Math.max(.6F, run.target.getBbHeight()), run.target, p.getId());
            ArmPosePayload.show(p, ArmPose.ELECTRO_TOUCH, OVERLOAD_LAND - 2);
            Takedowns.sound(run, run.anchor, SoundEvents.BEACON_POWER_SELECT, .7F, 1.8F);
        }

        @Override public void tick(Run run) {
            int age = run.age();
            for (int i = 0; i < OVERLOAD_JOLTS.length; i++) if (age == OVERLOAD_JOLTS[i]) {
                Takedowns.flinch(run.target);
                Takedowns.sound(run, run.anchor, SoundEvents.FIRECHARGE_USE, .3F + i * .06F, 1.4F + i * .08F);
            }
            if (age == OVERLOAD_LAND - 2) ArmPosePayload.show(run.player, ArmPose.ELECTRO_OVERCHARGE, 10);
        }

        @Override public void land(Run run) {
            ServerPlayer p = run.player;
            LivingEntity foe = run.target;
            Takedowns.strike(run, damage -> ElectroService.shock(p, foe, damage));
            Takedowns.spill(run, (other, amount) -> {
                ElectroService.shock(p, other, amount);
                StyleHits.away(other, run.anchor, StyleHits.level(p), .5, .25);
            });
            Takedowns.sound(run, run.anchor, SoundEvents.LIGHTNING_BOLT_IMPACT, .9F, 1.6F);
            Takedowns.sound(run, run.anchor, SoundEvents.GENERIC_EXPLODE.value(), .4F, 1.8F);
            CameraShakePayload.sendAround(run.level(), run.anchor, 16, .18F, 5);
        }

        @Override public void reward(Run run) { ShortCircuit.FINISHER.reward(run); }
    };
}
