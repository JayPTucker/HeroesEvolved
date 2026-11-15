package com.jayptucker.heroesevolved.phasing;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.combat.ComboStyle;
import com.jayptucker.heroesevolved.combat.FightingStyles.Dodge;
import com.jayptucker.heroesevolved.combat.StyleHits;
import com.jayptucker.heroesevolved.combat.StyleMotion;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Ghost Fist: a fist that goes in intangible and turns solid inside the target. Density's blows ignore armour, and
 * every blow in a combo makes the next denser (heavier) than the last; the finisher is a Stone Hammer, both fists
 * turned to stone and brought down together, pinning the foe to the ground; crouching presses the ground soft round
 * you so foes sink to the shins. A side dodge is a Phase Slip: the body goes intangible for longer than any other
 * style's dodge, and blows and bodies pass straight through. Back (from level 2) sinks into the floor and surfaces a
 * few blocks away. A blow evaded loads Reach Through: the next jab reaches the attacker across nine blocks, through
 * walls.
 */
public final class DensityStyle extends ComboStyle {
    public static final DensityStyle STYLE = new DensityStyle();
    public static final double JAB_REACH = 4, HAMMER_REACH = 4.5, SINK_RADIUS = 3.5, REACH_THROUGH = 9;
    /** How much heavier each blow in a combo is than the last, and how many times it can build. */
    public static final float DENSER = .25F;
    public static final int MAX_STACK = 4;

    private DensityStyle() { super(ModAbilities.PHASING_ID, "density.heroesevolved.too_tired"); }

    private static ServerLevel world(ServerPlayer p) { return p.serverLevel(); }

    /** A blow from inside the body: armour doesn't stop it. */
    private static void inside(ServerPlayer p, LivingEntity target, float damage) {
        StyleHits.hurt(target, p.damageSources().indirectMagic(p, p), damage);
    }

    @Override
    protected void throwMove(ServerPlayer p, Fighter f, Move move, int level, boolean loaded) {
        if (now(p) - f.lastStrike > COMBO_WINDOW) f.stack = 0;
        float weight = 1 + DENSER * f.stack;
        switch (move) {
            case LEAD, REAR -> jab(p, f, level, move == Move.REAR, loaded, weight);
            case FINISHER -> hammer(p, level, weight);
            case LOW -> sink(p, level);
        }
        f.stack = move == Move.FINISHER || move == Move.LOW ? 0 : Math.min(MAX_STACK, f.stack + 1);
    }

    private void jab(ServerPlayer p, Fighter f, int level, boolean rear, boolean loaded, float weight) {
        Vec3 fist = StyleHits.hand(p, 1, rear);
        LivingEntity target = loaded ? f.marked() : null;
        if (target != null && target.distanceTo(p) > REACH_THROUGH) target = null;
        if (target == null) {
            Vec3 eye = p.getEyePosition(), aim = p.getLookAngle().normalize();
            double reach = loaded ? REACH_THROUGH : JAB_REACH;
            target = loaded ? StyleHits.firstInTube(p, eye, aim, reach, .6, true) : StyleHits.aimed(p, reach, .5);
        }
        if (target != null) {
            inside(p, target, (2 + .3F * level) * weight * (loaded ? 1.5F : 1));
            StyleHits.shove(target, p.getLookAngle().normalize().scale(.15 * weight).add(0, .04, 0));
            Vec3 center = target.getBoundingBox().getCenter();
            AbilityEffects.send(world(p), Kind.DENSITY_REACH, fist, center, loaded ? 1.4 : 1);
            AbilityEffects.send(world(p), Kind.DENSITY_RIPPLE, center, fist, .7 + .15 * weight);
        } else {
            AbilityEffects.send(world(p), Kind.DENSITY_RIPPLE, fist, fist.add(p.getLookAngle()), .45);
        }
        pose(p, rear ? ArmPose.STYLE_CROSS : ArmPose.STYLE_JAB, 8);
        StyleHits.sound(p, SoundEvents.STONE_HIT, .6F, 1.4F - .12F * (weight - 1) * 4);
    }

    /** Stone Hammer: both fists turned to stone and brought down together; the foe is pinned to the ground. */
    private void hammer(ServerPlayer p, int level, float weight) {
        LivingEntity target = StyleHits.aimed(p, HAMMER_REACH, .8);
        AbilityEffects.send(world(p), Kind.DENSITY_DENSE, p.position(), p.getEyePosition(), 1);
        if (target != null) {
            inside(p, target, (4 + level) * weight);
            StyleHits.shove(target, new Vec3(0, -.8, 0));
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 3));
            AbilityEffects.send(world(p), Kind.DENSITY_SLAM, target.position(), target.position().add(0, 1, 0), 1.2);
            CameraShakePayload.sendAround(world(p), target.position(), 10, .2F, 5);
        }
        pose(p, ArmPose.STYLE_HAMMER, 12);
        StyleHits.sound(p, SoundEvents.ANVIL_LAND, .5F, .7F);
    }

    /** A palm pressed into the ground: it goes soft round you and foes sink to their shins. */
    private void sink(ServerPlayer p, int level) {
        Vec3 feet = p.position();
        for (LivingEntity target : StyleHits.foes(p, feet, SINK_RADIUS + 1)) {
            if (Math.abs(target.getY() - feet.y) > 1.5
                    || target.position().subtract(feet).horizontalDistance() > SINK_RADIUS + target.getBbWidth() * .5) continue;
            inside(p, target, 1.5F + .4F * level);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 4));
            StyleHits.shove(target, new Vec3(0, -.4, 0));
            AbilityEffects.send(world(p), Kind.DENSITY_EMBED, target.position(), target.position(), .8);
        }
        AbilityEffects.send(world(p), Kind.DENSITY_RIPPLE, feet.add(0, .05, 0), feet.add(0, 1.05, 0), SINK_RADIUS);
        AbilityEffects.send(world(p), Kind.DENSITY_SLAM, feet, feet.add(0, 1, 0), 1);
        pose(p, ArmPose.DENSITY_PRESS, 14);
        StyleHits.sound(p, SoundEvents.MUD_PLACE, 1, .6F);
    }

    /** A Phase Slip stays intangible for longer than any other style's dodge. */
    @Override protected int evadeTicks(boolean special) { return special ? 10 : 14; }

    @Override
    protected void dodgeMove(ServerPlayer p, Fighter f, Dodge dodge, Vec3 direction, boolean special, int level) {
        Vec3 from = p.position();
        if (special) {
            // Down into the floor and up a few blocks away.
            Vec3 to = com.jayptucker.heroesevolved.speed.SpeedService.swept(p, direction.scale(5));
            AbilityEffects.send(world(p), Kind.DENSITY_EMBED, from, from, 1);
            StyleHits.blink(p, to);
            AbilityEffects.send(world(p), Kind.DENSITY_SURFACE, to, to.add(0, 1, 0), 1);
            pose(p, ArmPose.DENSITY_DROP, 10);
        } else {
            StyleMotion.dash(p, direction.scale(1.0), .1, 4, 6, false, false);
            SetPiecePayload.send(world(p), Piece.DENSITY_SLIP, from, from.add(direction), 1, p);
            AbilityEffects.send(world(p), Kind.DENSITY_GHOST, from, from.add(0, 1, 0), 1);
            pose(p, ArmPose.DENSITY_SLIP, 10);
        }
        StyleHits.sound(p, SoundEvents.ILLUSIONER_MIRROR_MOVE, .6F, 1.3F);
    }

    @Override
    protected void onCounter(ServerPlayer p, Fighter f, DamageSource source) {
        AbilityEffects.send(world(p), Kind.DENSITY_GHOST, p.position(), p.getEyePosition(), 1.3);
        StyleHits.sound(p, SoundEvents.ILLUSIONER_PREPARE_MIRROR, .6F, 1.5F);
        say(p, "density.heroesevolved.reach_through");
    }
}
