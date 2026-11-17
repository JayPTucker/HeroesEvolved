package com.jayptucker.heroesevolved.cryo;

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
 * Cryokinesis's alternate finishers, picked at random beside {@link LastWarmth}; both give its reward (stamina, as heat
 * theft).
 *
 * <ul>
 *   <li><b>Coffin</b>: ice grows up over the foe from its feet in tall leaning shards and seals it in; one jab, and the
 *   coffin shatters into shards that skid away across the ground.</li>
 *   <li><b>Icicle Ring</b>: a ring of icicles rises round the foe and leans in until their points meet over it; it
 *   freezes where it stands in a burst of frost, and the icicles crumble.</li>
 * </ul>
 *
 * <p>{@code StyleArt} draws both (with Last Warmth's frost): origin the foe's feet, target yours, scale its height.</p>
 */
public final class CryoFinishers {
    public static final int COFFIN_SEALED = 12, COFFIN_JAB = 15, COFFIN_LAND = 18, COFFIN_TICKS = 30;
    public static final int ICICLES_RISEN = 7, ICICLES_LAND = 16, ICICLES_TICKS = 30;

    private CryoFinishers() {}

    public static final Takedowns.Finisher COFFIN = new Takedowns.Finisher() {
        @Override public ResourceLocation power() { return ModAbilities.CRYOKINESIS_ID; }
        @Override public String id() { return "coffin"; }
        @Override public int ticks() { return COFFIN_TICKS; }
        @Override public int landAt() { return COFFIN_LAND; }

        @Override public void start(Run run) {
            ServerPlayer p = run.player;
            run.state = LastWarmth.hot(run.target);
            Takedowns.show(run.level(), Piece.FINISH_COFFIN, run.anchor, p.position(), Math.max(.6F, run.target.getBbHeight()), null, p.getId());
            ArmPosePayload.show(p, ArmPose.CRYO_SCULPT, COFFIN_JAB - 1);
            Takedowns.sound(run, run.anchor, SoundEvents.POWDER_SNOW_PLACE, 1F, .6F);
        }

        @Override public void tick(Run run) {
            int age = run.age();
            if (age < COFFIN_LAND && age % 3 == 0) {
                CryoService.chill(run.target, 40, run.player);
                Takedowns.sound(run, run.anchor, SoundEvents.GLASS_PLACE, .6F, 1.2F + age * .04F);
            }
            if (age == COFFIN_SEALED) Takedowns.sound(run, run.anchor, SoundEvents.AMETHYST_BLOCK_PLACE, 1F, .7F);
            if (age == COFFIN_JAB) {
                ArmPosePayload.show(run.player, ArmPose.STYLE_JAB, 6);
                Takedowns.sound(run, run.player.position(), SoundEvents.PLAYER_ATTACK_STRONG, 1F, 1.2F);
            }
        }

        @Override public void land(Run run) {
            ServerPlayer p = run.player;
            LivingEntity foe = run.target;
            Takedowns.strike(run, damage -> CryoService.shatterHit(p, foe, damage));
            Takedowns.spill(run, (other, amount) -> {
                CryoService.chill(other, 80, p);
                CryoService.shatterHit(p, other, amount);
                StyleHits.away(other, run.anchor, StyleHits.level(p), .5, .15);
            });
            Takedowns.sound(run, run.anchor, SoundEvents.GLASS_BREAK, 1.2F, .6F);
            Takedowns.sound(run, run.anchor, SoundEvents.AMETHYST_CLUSTER_BREAK, 1F, .7F);
            CameraShakePayload.sendAround(run.level(), run.anchor, 16, .15F, 5);
        }

        @Override public void reward(Run run) { LastWarmth.FINISHER.reward(run); }
    };

    public static final Takedowns.Finisher ICICLE_RING = new Takedowns.Finisher() {
        @Override public ResourceLocation power() { return ModAbilities.CRYOKINESIS_ID; }
        @Override public String id() { return "icicle_ring"; }
        @Override public int ticks() { return ICICLES_TICKS; }
        @Override public int landAt() { return ICICLES_LAND; }

        @Override public void start(Run run) {
            ServerPlayer p = run.player;
            run.state = LastWarmth.hot(run.target);
            Takedowns.show(run.level(), Piece.FINISH_ICICLES, run.anchor, p.position(), Math.max(.6F, run.target.getBbHeight()), null, p.getId());
            ArmPosePayload.show(p, ArmPose.CRYO_LOOM, ICICLES_LAND);
            Takedowns.sound(run, run.anchor, SoundEvents.POINTED_DRIPSTONE_PLACE, 1F, 1.4F);
        }

        @Override public void tick(Run run) {
            int age = run.age();
            if (age < ICICLES_RISEN && age % 2 == 0) Takedowns.sound(run, run.anchor, SoundEvents.GLASS_PLACE, .5F, 1.5F + age * .05F);
            if (age >= ICICLES_RISEN && age < ICICLES_LAND && age % 3 == 0) CryoService.chill(run.target, 40, run.player);
            if (age == ICICLES_LAND - 2) ArmPosePayload.show(run.player, ArmPose.CRYO_SNAP, 8);
        }

        @Override public void land(Run run) {
            ServerPlayer p = run.player;
            LivingEntity foe = run.target;
            Takedowns.strike(run, damage -> CryoService.shatterHit(p, foe, damage));
            Takedowns.spill(run, (other, amount) -> {
                CryoService.chill(other, 80, p);
                CryoService.shatterHit(p, other, amount);
            });
            Takedowns.sound(run, run.anchor, SoundEvents.POWDER_SNOW_BREAK, 1.2F, .6F);
            Takedowns.sound(run, run.anchor, SoundEvents.GLASS_BREAK, .8F, 1.3F);
        }

        @Override public void reward(Run run) { LastWarmth.FINISHER.reward(run); }
    };
}
