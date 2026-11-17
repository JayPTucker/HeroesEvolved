package com.jayptucker.heroesevolved.pyro;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.combat.StyleHits;
import com.jayptucker.heroesevolved.combat.StyleMotion;
import com.jayptucker.heroesevolved.combat.Takedowns;
import com.jayptucker.heroesevolved.combat.Takedowns.Run;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Pyrokinesis's finisher, Dragon Coil: three kicks in a row (a low spinning hook, a turning back kick, a jumping
 * roundhouse), each laying a crescent of fire round the foe at a new height, and then the three crescents join into
 * one coiled dragon whose head rears over the foe and bites down. At 70 Heat or more the dragon is blue and its tail
 * lashes the nearest other foe (the overkill). Gives 20 Heat.
 *
 * <p>The beats are timed here and drawn from the same ticks by {@code PyroArt}'s {@code FINISH_DRAGON}.</p>
 */
public final class DragonCoil {
    /** The kicks land on these ticks; the coil forms from KNOT and bites at {@link #BITE}. */
    public static final int HOOK = 3, BACK = 9, ROUNDHOUSE = 15, KNOT = 18, BITE = 22, TICKS = 30;
    /** At this much Heat the dragon burns blue and its tail takes the overkill. */
    public static final int BLUE_AT = 70;
    public static final int HEAT_REWARD = 20;

    private DragonCoil() {}

    public static final Takedowns.Finisher FINISHER = new Takedowns.Finisher() {
        @Override public ResourceLocation power() { return ModAbilities.PYROKINESIS_ID; }
        @Override public String id() { return "dragon_coil"; }
        @Override public int ticks() { return TICKS; }
        @Override public int landAt() { return BITE; }

        @Override public void start(Run run) {
            ServerPlayer p = run.player;
            boolean blue = PyroService.heat(p) >= BLUE_AT || PyroService.isBlue(p);
            run.state = blue;
            PyroGeometry.show(run.level(), Piece.FINISH_DRAGON, run.anchor, p.position(), run.target.getBbHeight(), blue, null);
            ArmPosePayload.show(p, ArmPose.FIRE_SWEEP_KICK, 8);
            sound(p, SoundEvents.BLAZE_SHOOT, .9F, .7F);
            sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, .8F, .6F);
        }

        @Override public void tick(Run run) {
            ServerPlayer p = run.player;
            int age = run.age();
            if (age == HOOK || age == BACK || age == ROUNDHOUSE) Takedowns.flinch(run.target);
            if (age == 6) {
                ArmPosePayload.show(p, ArmPose.FIRE_KICK, 8);
                sound(p, SoundEvents.BLAZE_SHOOT, .9F, .85F);
            } else if (age == 12) {
                // The roundhouse leaves the ground: a small hop, even though the move holds you in place.
                ArmPosePayload.show(p, ArmPose.FIRE_KICK, 8);
                StyleMotion.push(p, new Vec3(0, .42, 0));
                sound(p, SoundEvents.BLAZE_SHOOT, 1F, 1F);
                sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, .9F, .8F);
            } else if (age == KNOT) {
                ArmPosePayload.show(p, ArmPose.FINISH_DRAGON, 14);
                sound(p, SoundEvents.FIRECHARGE_USE, 1F, .6F);
            } else if (age == 20) {
                // The dragon rears: a roar made of fire, pitched down.
                sound(p, SoundEvents.BLAZE_AMBIENT, 1.2F, .5F);
                sound(p, SoundEvents.BLAZE_SHOOT, 1.1F, .45F);
            }
        }

        @Override public void land(Run run) {
            ServerPlayer p = run.player;
            LivingEntity foe = run.target;
            boolean blue = run.state instanceof Boolean b && b;
            Takedowns.strike(run, damage -> PyroService.burn(p, foe, damage, 4));
            if (blue) {
                // The tail lashes the nearest other foe with everything left over.
                for (LivingEntity other : Takedowns.others(run, 5, 1)) {
                    PyroService.burn(p, other, Math.max(2, run.overkill()), 4);
                    StyleHits.away(other, run.anchor, StyleHits.level(p), .7, .3);
                }
            } else {
                Takedowns.spill(run, (other, amount) -> {
                    PyroService.burn(p, other, amount, 2);
                    StyleHits.away(other, run.anchor, StyleHits.level(p), .45, .2);
                });
            }
            sound(p, SoundEvents.FIRECHARGE_USE, 1.2F, .5F);
            sound(p, SoundEvents.GENERIC_EXPLODE.value(), .55F, 1.5F);
            CameraShakePayload.sendAround(run.level(), run.anchor, 16, .18F, 6);
        }

        @Override public void reward(Run run) {
            PyroService.addHeat(run.player, HEAT_REWARD);
        }
    };

    private static void sound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }
}
