package com.jayptucker.heroesevolved.amplify;

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
import net.minecraft.world.phys.Vec3;

/**
 * Power Amplification's finishers. None needs an ally: every one is red lightning poured into the foe.
 *
 * <ul>
 *   <li><b>Sidekick Juice</b> (signature): a palm flat on the foe's chest; red light pours in until it shines through the skin,
 *   and the foe bursts in a flash. Reward: 40 Charge.</li>
 *   <li><b>Red Wave</b>: the ball between cupped hands, both hands driven forward, and a beam that drives the foe back
 *   across the ground and drops it.</li>
 *   <li><b>Shock Therapy, Reversed</b>: two quick jolts at the temples; the foe stiffens, sparks run over it, and it
 *   falls.</li>
 * </ul>
 */
public final class AmpFinishers {
    public static final int JUICE_TICKS = 30, JUICE_LAND = 20, WAVE_TICKS = 28, WAVE_LAND = 16, WAVE_FIRE = 9,
            REVERSE_TICKS = 26, REVERSE_LAND = 18;
    public static final int REWARD = 40;

    private AmpFinishers() {}

    private static ResourceLocation power() { return ModAbilities.AMPLIFICATION_ID; }

    private static void blow(Run run, float damage) {
        ServerPlayer p = run.player;
        Takedowns.strike(run, d -> StyleHits.hurt(run.target, p.damageSources().indirectMagic(p, p), d));
        Takedowns.spill(run, (other, amount) -> {
            AmpService.zap(p, other, 1);
            StyleHits.hurt(other, p.damageSources().indirectMagic(p, p), amount);
            AmpService.stun(other, 30);
        });
    }

    public static final Takedowns.Finisher SIDEKICK_JUICE = new Takedowns.Finisher() {
        @Override public ResourceLocation power() { return AmpFinishers.power(); }
        @Override public String id() { return "sidekick_juice"; }
        @Override public int ticks() { return JUICE_TICKS; }
        @Override public int landAt() { return JUICE_LAND; }

        @Override public void start(Run run) {
            ServerPlayer p = run.player;
            Takedowns.show(run.level(), Piece.FINISH_JUICE, run.anchor, p.position(), Math.max(.6F, run.target.getBbHeight()), run.target, p.getId());
            ArmPosePayload.show(p, ArmPose.AMP_PALM, JUICE_LAND + 2);
            Takedowns.sound(run, run.anchor, SoundEvents.BEACON_ACTIVATE, 1, 1.6F);
        }

        @Override public void tick(Run run) {
            int age = run.age();
            if (age == 6 || age == 11 || age == 15) {
                Takedowns.flinch(run.target);
                Takedowns.sound(run, run.anchor, SoundEvents.BEACON_POWER_SELECT, .9F, 1.4F + age * .03F);
            }
            if (age == JUICE_LAND - 2) Takedowns.sound(run, run.anchor, SoundEvents.BEACON_AMBIENT, 1.2F, 2F);
        }

        @Override public void land(Run run) {
            blow(run, run.damage());
            Takedowns.sound(run, run.anchor, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.2F, 1.4F);
            Takedowns.sound(run, run.anchor, SoundEvents.GENERIC_EXPLODE.value(), .6F, 1.7F);
            CameraShakePayload.sendAround(run.level(), run.anchor, 16, .2F, 6);
        }

        @Override public void reward(Run run) { AmpService.addCharge(run.player, REWARD); }
    };

    public static final Takedowns.Finisher RED_WAVE = new Takedowns.Finisher() {
        @Override public ResourceLocation power() { return AmpFinishers.power(); }
        @Override public String id() { return "red_wave"; }
        @Override public int ticks() { return WAVE_TICKS; }
        @Override public int landAt() { return WAVE_LAND; }
        @Override public boolean holds() { return false; }

        @Override public void start(Run run) {
            ServerPlayer p = run.player;
            run.state = p.position();
            Takedowns.show(run.level(), Piece.FINISH_RED_WAVE, run.anchor, p.position(), Math.max(.6F, run.target.getBbHeight()), p, run.target.getId());
            ArmPosePayload.show(p, ArmPose.AMP_CUP, WAVE_FIRE);
            Takedowns.sound(run, p.position(), SoundEvents.BEACON_AMBIENT, 1, 1.8F);
        }

        @Override public void tick(Run run) {
            ServerPlayer p = run.player;
            LivingEntity foe = run.target;
            int age = run.age();
            if (age == WAVE_FIRE) {
                ArmPosePayload.show(p, ArmPose.AMP_BEAM, WAVE_LAND - WAVE_FIRE + 6);
                Takedowns.sound(run, p.position(), SoundEvents.TRIDENT_THUNDER.value(), 1, 1.4F);
            }
            // The beam drives it back across the ground until it drops.
            if (age > WAVE_FIRE && age <= WAVE_LAND && foe.isAlive() && run.state instanceof Vec3 home) {
                Vec3 back = run.anchor.subtract(home).multiply(1, 0, 1);
                back = back.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : back.normalize();
                Vec3 want = foe.position().add(back.scale(.32));
                if (run.level().noCollision(foe, foe.getDimensions(foe.getPose()).makeBoundingBox(want))) Takedowns.move(foe, want);
                if (age % 2 == 0) Takedowns.shudder(foe);
            }
        }

        @Override public void land(Run run) {
            blow(run, run.damage());
            Takedowns.sound(run, run.target.position(), SoundEvents.LIGHTNING_BOLT_IMPACT, 1, 1.3F);
            CameraShakePayload.sendAround(run.level(), run.anchor, 16, .18F, 5);
        }

        @Override public void reward(Run run) { SIDEKICK_JUICE.reward(run); }
    };

    public static final Takedowns.Finisher REVERSE = new Takedowns.Finisher() {
        @Override public ResourceLocation power() { return AmpFinishers.power(); }
        @Override public String id() { return "shock_reversed"; }
        @Override public int ticks() { return REVERSE_TICKS; }
        @Override public int landAt() { return REVERSE_LAND; }

        @Override public void start(Run run) {
            ServerPlayer p = run.player;
            Takedowns.show(run.level(), Piece.FINISH_REVERSE, run.anchor, p.position(), Math.max(.6F, run.target.getBbHeight()), run.target, p.getId());
            ArmPosePayload.show(p, ArmPose.AMP_ZAP, REVERSE_LAND);
        }

        @Override public void tick(Run run) {
            int age = run.age();
            if (age == 6 || age == 12) {
                Takedowns.shudder(run.target);
                Takedowns.sound(run, run.target.getEyePosition(), SoundEvents.BEACON_DEACTIVATE, 1, 1.9F);
            }
        }

        @Override public void land(Run run) {
            blow(run, run.damage());
            Takedowns.sound(run, run.anchor, SoundEvents.BEACON_DEACTIVATE, 1.2F, .6F);
        }

        @Override public void reward(Run run) { SIDEKICK_JUICE.reward(run); }
    };
}
