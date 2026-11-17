package com.jayptucker.heroesevolved.cryo;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.combat.Takedowns;
import com.jayptucker.heroesevolved.combat.Takedowns.Run;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.pyro.PyroService;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;

/**
 * Cryokinesis's finisher, Last Warmth: an open palm a block from the foe's chest, and its warmth is drawn out as an
 * ember along a thread to your hand while frost creeps over it from the edges in; you close your fist and the ember goes
 * out in a hiss of steam; the foe, white with rime, tips over and breaks into snow. Hot foes (blazes, magma cubes,
 * striders, anything fire-proof, a pyrokinetic) give up a whole flame: twice the stamina back, and a pyrokinetic's fire
 * goes out. It counts as heat theft: stamina back.
 */
public final class LastWarmth {
    public static final int DRAW = 6, CLOSE = 16, LAND = 20, TICKS = 26;
    /** Added to the piece's scale for a hot foe. */
    public static final float HOT = 32;
    public static final int STAMINA = 8;

    private LastWarmth() {}

    static boolean hot(LivingEntity foe) {
        return foe.fireImmune() || foe instanceof ServerPlayer other && PyroService.heat(other) > 0;
    }

    public static final Takedowns.Finisher FINISHER = new Takedowns.Finisher() {
        @Override public ResourceLocation power() { return ModAbilities.CRYOKINESIS_ID; }
        @Override public String id() { return "last_warmth"; }
        @Override public int ticks() { return TICKS; }
        @Override public int landAt() { return LAND; }

        @Override public void start(Run run) {
            ServerPlayer p = run.player;
            boolean hot = hot(run.target);
            run.state = hot;
            Takedowns.show(run.level(), Piece.FINISH_WARMTH, run.anchor, p.position(),
                    Math.max(.6F, run.target.getBbHeight()) + (hot ? HOT : 0), run.target, p.getId());
            ArmPosePayload.show(p, ArmPose.CRYO_PRISON, CLOSE);
            Takedowns.sound(run, run.anchor, SoundEvents.POWDER_SNOW_STEP, 1F, .6F);
        }

        @Override public void tick(Run run) {
            int age = run.age();
            if (age >= DRAW && age < LAND && age % 3 == 0) CryoService.chill(run.target, 40, run.player);
            if (age == DRAW) Takedowns.sound(run, run.anchor, SoundEvents.AMETHYST_BLOCK_RESONATE, .8F, 1.6F);
            if (age == CLOSE) {
                ArmPosePayload.show(run.player, ArmPose.CRYO_SHATTER, 10);
                Takedowns.sound(run, run.player.position(), SoundEvents.FIRE_EXTINGUISH, 1F, 1.2F);
            }
        }

        @Override public void land(Run run) {
            ServerPlayer p = run.player;
            LivingEntity foe = run.target;
            if (foe instanceof ServerPlayer pyro) PyroService.freezeFlames(pyro);
            Takedowns.strike(run, damage -> CryoService.shatterHit(p, foe, damage));
            Takedowns.spill(run, (other, amount) -> {
                CryoService.chill(other, 80, p);
                CryoService.shatterHit(p, other, amount);
            });
            Takedowns.sound(run, run.anchor, SoundEvents.GLASS_BREAK, 1F, .7F);
            Takedowns.sound(run, run.anchor, SoundEvents.POWDER_SNOW_BREAK, 1F, .8F);
        }

        @Override public void reward(Run run) {
            boolean hot = run.state instanceof Boolean b && b;
            PlayerEnergyService.restore(run.player, hot ? STAMINA * 2 : STAMINA);
        }
    };
}
