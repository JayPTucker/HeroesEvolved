package com.jayptucker.heroesevolved.speed;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.combat.ComboStyle;
import com.jayptucker.heroesevolved.combat.FightingStyles.Dodge;
import com.jayptucker.heroesevolved.combat.StyleHits;
import com.jayptucker.heroesevolved.combat.StyleMotion;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Blur Boxing: a speedster's fists. Every jab is three blows too fast to see, landing from slightly different angles
 * with an afterimage at each; the finisher (Blitz) runs straight through the target and stops behind them; crouching
 * slides in under them (Skid Sweep). A side dodge is a Blur Step: the speedster is simply somewhere else, an afterimage
 * left where they stood. Back (from level 2) is a Rewind Step to wherever they were a second ago. A blow evaded loads
 * Too Slow: the next strike is a barrage from afterimages circling the attacker.
 *
 * <p>Drawn with Superspeed's own strokes (gold lanes, afterimages, shock rings), so it reads as the same power.</p>
 */
public final class SpeedStyle extends ComboStyle {
    public static final SpeedStyle STYLE = new SpeedStyle();
    /** A jab's reach and how many blows one click throws; the Blitz's reach; a Blur Step's distance. */
    public static final double JAB_REACH = 4.5, BLITZ_REACH = 9, STEP = 5;
    public static final int JAB_BLOWS = 3, BARRAGE_BLOWS = 6;

    private SpeedStyle() { super(ModAbilities.SUPERSPEED_ID, "speed.heroesevolved.too_tired"); }

    private static ServerLevel world(ServerPlayer p) { return p.serverLevel(); }

    @Override
    protected void throwMove(ServerPlayer p, Fighter f, Move move, int level, boolean loaded) {
        switch (move) {
            case LEAD, REAR -> {
                LivingEntity marked = loaded ? f.marked() : null;
                if (marked != null && marked.distanceToSqr(p) < 16 * 16) barrage(p, marked, level);
                else jab(p, level, move == Move.REAR, loaded);
            }
            case FINISHER -> blitz(p, level);
            case LOW -> sweep(p, level);
        }
    }

    /** Three blows in one click, each from a slightly different angle, an afterimage left at every one. */
    private void jab(ServerPlayer p, int level, boolean rear, boolean loaded) {
        LivingEntity target = StyleHits.aimed(p, JAB_REACH, .55);
        Vec3 fist = StyleHits.hand(p, 1, rear);
        if (target != null) {
            float each = (1 + .3F * level) * (loaded ? 1.6F : 1);
            for (int i = 0; i < JAB_BLOWS; i++) {
                StyleHits.hurt(target, p.damageSources().playerAttack(p), each);
                double angle = (i - 1) * .55;
                Vec3 side = StyleHits.right(StyleHits.level(p)).scale(Math.sin(angle) * .7);
                AbilityEffects.send(world(p), Kind.SPEED_IMPACT, p.getEyePosition().add(side), target.getEyePosition(), .55);
            }
            StyleHits.shove(target, StyleHits.level(p).scale(.25).add(0, .08, 0));
            AbilityEffects.send(world(p), Kind.SPEED_ZIP, p.position(), p.position(), .6);
            StyleHits.sound(p, target.position(), SoundEvents.PLAYER_ATTACK_STRONG, .6F, 1.7F);
        } else {
            AbilityEffects.send(world(p), Kind.SPEED_BURST, fist, fist.add(StyleHits.level(p)), .35);
        }
        pose(p, ArmPose.STYLE_FLURRY, 8);
        StyleHits.sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, .35F, 1.9F);
    }

    /** Blitz: a run straight through the target, ending behind them; the blow lands as they pass. */
    private void blitz(ServerPlayer p, int level) {
        Vec3 forward = StyleHits.level(p);
        LivingEntity target = StyleHits.aimed(p, BLITZ_REACH, .8);
        Vec3 from = p.position();
        Vec3 goal = target != null ? target.position().add(forward.scale(target.getBbWidth() * .5 + 1.3)) : from.add(forward.scale(6));
        Vec3 landed = SpeedService.swept(p, goal.subtract(from));
        if (landed.distanceToSqr(from) > .25) {
            StyleHits.blink(p, landed);
            AbilityEffects.send(world(p), Kind.SPEED_TRAIL, from, landed, 1.4);
            AbilityEffects.send(world(p), Kind.SPEED_ZIP, from, landed, 1.2);
        }
        if (target != null) {
            StyleHits.hurt(p, target, 3 + level);
            StyleHits.shove(target, forward.scale(-.55).add(0, .35, 0));
            AbilityEffects.send(world(p), Kind.SPEED_STRIKE, from, target.getBoundingBox().getCenter(), 1.1);
            CameraShakePayload.sendAround(world(p), target.position(), 10, .14F, 4);
        }
        pose(p, ArmPose.SPEED_LUNGE, 10);
        StyleHits.sound(p, SoundEvents.FIREWORK_ROCKET_BLAST, .7F, 1.6F);
        StyleHits.sound(p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, .8F, 1.2F);
    }

    /** Skid Sweep: a low slide forward that takes everyone's feet out round the speedster. */
    private void sweep(ServerPlayer p, int level) {
        Vec3 forward = StyleHits.level(p), from = p.position();
        StyleMotion.dash(p, forward.scale(1.2), .05, 3, 6, false, false);
        for (LivingEntity target : StyleHits.foes(p, from.add(forward.scale(1.5)), 3.4)) {
            if (Math.abs(target.getY() - from.y) > 1.5) continue;
            StyleHits.hurt(p, target, 2 + .5F * level);
            StyleHits.shove(target, new Vec3(0, .45, 0).add(forward.scale(.2)));
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 2));
        }
        AbilityEffects.send(world(p), Kind.SPEED_TRAIL, from, from.add(forward.scale(3.6)), 1.6);
        AbilityEffects.send(world(p), Kind.SPEED_BOOM, from.add(forward.scale(1.8)), from.add(forward.scale(2.8)), .9);
        pose(p, ArmPose.STYLE_SLIDE, 12);
        StyleHits.sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, .9F, .8F);
    }

    /** Too Slow: a barrage of blows from afterimages circling the one who swung. */
    private void barrage(ServerPlayer p, LivingEntity target, int level) {
        Vec3 center = target.position();
        for (int i = 0; i < BARRAGE_BLOWS; i++) {
            double angle = i * Math.PI * 2 / BARRAGE_BLOWS;
            Vec3 spot = center.add(Math.cos(angle) * 1.6, 0, Math.sin(angle) * 1.6);
            StyleHits.hurt(target, p.damageSources().playerAttack(p), .9F + .3F * level);
            AbilityEffects.send(world(p), Kind.SPEED_IMPACT, spot.add(0, 1.4, 0), target.getEyePosition(), .7);
        }
        AbilityEffects.send(world(p), Kind.SPEED_MIRAGE, center, center, 2);
        StyleHits.shove(target, new Vec3(0, .55, 0));
        pose(p, ArmPose.STYLE_FLURRY, 12);
        StyleHits.sound(p, target.position(), SoundEvents.PLAYER_ATTACK_CRIT, 1, 1.3F);
        CameraShakePayload.sendAround(world(p), center, 10, .18F, 5);
    }

    @Override
    protected void dodgeMove(ServerPlayer p, Fighter f, Dodge dodge, Vec3 direction, boolean special, int level) {
        Vec3 from = p.position();
        Vec3 to;
        if (special) {
            // Rewind Step: back to where they were a second ago (or a long step back if they haven't been anywhere).
            Vec3 then = f.trail.isEmpty() ? null : f.trail.peekFirst();
            to = then != null && then.distanceToSqr(from) > 1 && then.distanceToSqr(from) < 14 * 14
                    ? from.add(SpeedService.swept(p, then.subtract(from)).subtract(from)) : SpeedService.swept(p, direction.scale(STEP + 1));
            AbilityEffects.send(world(p), Kind.SPEED_MIRAGE, from, from, 1);
            pose(p, ArmPose.STYLE_BACKSTEP, 10);
        } else {
            to = SpeedService.swept(p, direction.scale(STEP));
            pose(p, ArmPose.STYLE_SIDESTEP, 10);
        }
        StyleHits.blink(p, to);
        AbilityEffects.send(world(p), Kind.SPEED_DODGE, from, to, 1);
        StyleHits.sound(p, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), .5F, 1.8F);
    }

    @Override
    protected void onCounter(ServerPlayer p, Fighter f, DamageSource source) {
        AbilityEffects.send(world(p), Kind.SPEED_FOCUS, p.position(), p.getEyePosition(), 1);
        StyleHits.sound(p, SoundEvents.EXPERIENCE_ORB_PICKUP, .6F, 1.9F);
        say(p, "speed.heroesevolved.too_slow");
    }
}
