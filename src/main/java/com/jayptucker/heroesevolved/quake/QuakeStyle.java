package com.jayptucker.heroesevolved.quake;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.combat.ComboStyle;
import com.jayptucker.heroesevolved.combat.FightingStyles.Dodge;
import com.jayptucker.heroesevolved.combat.StyleHits;
import com.jayptucker.heroesevolved.combat.StyleMotion;
import com.jayptucker.heroesevolved.cryo.CryoService;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Pressure Palm: Daisy doesn't punch, she pushes the air. Each strike is an open palm that drives a short tube of
 * pressure rings into whoever is in front, harder the more Strain she carries; the finisher is both palms at once, a
 * wide shove that sends a crowd flying; crouching drives a fist into the ground and a little fault cracks open round
 * her, throwing everyone up. She dodges by kicking off the air itself (a ring where she pushed), and backs off by
 * firing a shockwave forward. A blow evaded is sent straight back: Rebound hits the attacker with their own blow,
 * and her next palm is heavier.
 *
 * <p>Every strike adds Strain (so pushing hard in a fight shows on her arms like the rest of her kit), and a palm that
 * lands on someone frozen solid shatters the ice for double.</p>
 */
public final class QuakeStyle extends ComboStyle {
    public static final QuakeStyle STYLE = new QuakeStyle();
    public static final double PALM_REACH = 5, DOUBLE_REACH = 6, DOUBLE_HALF_ANGLE = 38, GROUND_RADIUS = 4;

    private QuakeStyle() { super(ModAbilities.QUAKE_ID, "quake.heroesevolved.too_tired"); }

    private static ServerLevel world(ServerPlayer p) { return p.serverLevel(); }

    /** A pressure blow: frozen targets shatter for double. */
    private static void press(ServerPlayer p, LivingEntity target, float damage) {
        if (CryoService.isFrozen(target)) {
            CryoService.shatterHit(p, target, damage * 2);
            return;
        }
        StyleHits.hurt(p, target, damage);
    }

    @Override
    protected void throwMove(ServerPlayer p, Fighter f, Move move, int level, boolean loaded) {
        switch (move) {
            case LEAD, REAR -> palm(p, level, move == Move.REAR, loaded);
            case FINISHER -> doublePalm(p, level);
            case LOW -> groundPunch(p, level);
        }
    }

    private void palm(ServerPlayer p, int level, boolean rear, boolean loaded) {
        Vec3 aim = p.getLookAngle().normalize(), palm = StyleHits.hand(p, 1, rear);
        LivingEntity target = StyleHits.aimed(p, PALM_REACH, .7);
        Vec3 end = target != null ? target.getBoundingBox().getCenter() : StyleHits.reach(p, p.getEyePosition(), aim, PALM_REACH);
        if (target != null) {
            press(p, target, (.8F + .2F * level) * QuakeKit.power(p) * (loaded ? 1.8F : 1));
            StyleHits.shove(target, aim.scale(loaded ? 1.0 : .55).add(0, .15, 0));
            AbilityEffects.send(world(p), Kind.QUAKE_CONTACT, end.subtract(aim.scale(.3)), end.add(aim), loaded ? 1.2 : .7);
        }
        AbilityEffects.send(world(p), Kind.QUAKE_RINGS, palm, end, loaded ? .9 : .5);
        QuakeKit.strain(p, loaded ? 1 : 2);
        pose(p, rear ? ArmPose.QUAKE_PALM : ArmPose.QUAKE_PALM_OFF, 8);
        StyleHits.sound(p, SoundEvents.WARDEN_ATTACK_IMPACT, .35F, 1.8F);
    }

    /** Both palms at once: a wide shove that sends everyone in front flying. */
    private void doublePalm(ServerPlayer p, int level) {
        Vec3 forward = StyleHits.level(p), chest = StyleHits.chest(p).add(forward.scale(.5));
        int struck = 0;
        for (LivingEntity target : StyleHits.foes(p, chest, DOUBLE_REACH + 1)) {
            Vec3 delta = target.position().subtract(p.position()).multiply(1, 0, 1);
            double distance = delta.length();
            if (distance > DOUBLE_REACH + target.getBbWidth() * .5 || distance > .8
                    && delta.scale(1 / distance).dot(forward) < Math.cos(Math.toRadians(DOUBLE_HALF_ANGLE))
                    || !StyleHits.exposed(p, chest, target.getBoundingBox().getCenter())) continue;
            press(p, target, (1 + .3F * level) * QuakeKit.power(p));
            StyleHits.shove(target, forward.scale(1.35).add(0, .4, 0));
            if (++struck >= 8) break;
        }
        AbilityEffects.send(world(p), Kind.QUAKE_RINGS, chest, chest.add(forward.scale(DOUBLE_REACH)), 1.3);
        AbilityEffects.send(world(p), Kind.QUAKE_CONTACT, chest.add(forward.scale(.4)), chest.add(forward.scale(2)), 1.6);
        QuakeKit.strain(p, 6);
        pose(p, ArmPose.STYLE_DOUBLE_PALM, 12);
        StyleHits.sound(p, SoundEvents.WARDEN_SONIC_BOOM, .45F, 1.6F);
        CameraShakePayload.sendAround(world(p), chest, 12, .2F, 5);
    }

    /** A fist into the ground: a small fault opens round her and throws everyone up. */
    private void groundPunch(ServerPlayer p, int level) {
        Vec3 feet = p.position(), forward = StyleHits.level(p);
        for (LivingEntity target : StyleHits.foes(p, feet, GROUND_RADIUS + 1)) {
            if (Math.abs(target.getY() - feet.y) > 1.5
                    || target.position().subtract(feet).horizontalDistance() > GROUND_RADIUS + target.getBbWidth() * .5) continue;
            press(p, target, (1 + .3F * level) * QuakeKit.power(p));
            StyleHits.shove(target, new Vec3(0, .7, 0).add(target.position().subtract(feet).multiply(1, 0, 1).normalize().scale(.2)));
        }
        for (int i = 0; i < 4; i++) {
            double angle = i * Math.PI / 2 + Math.atan2(forward.z, forward.x) + Math.PI / 4;
            Vec3 tip = feet.add(Math.cos(angle) * GROUND_RADIUS, 0, Math.sin(angle) * GROUND_RADIUS);
            AbilityEffects.send(world(p), Kind.QUAKE_FISSURE, feet.add(forward.scale(.5)), tip, 1);
        }
        AbilityEffects.send(world(p), Kind.QUAKE_IMPACT, feet, feet.add(0, 1, 0), GROUND_RADIUS);
        QuakeKit.strain(p, 5);
        pose(p, ArmPose.QUAKE_PUNCH_DOWN, 14);
        StyleHits.sound(p, SoundEvents.GENERIC_EXPLODE.value(), .45F, 1.5F);
        CameraShakePayload.sendAround(world(p), feet, 10, .22F, 6);
    }

    @Override
    protected void dodgeMove(ServerPlayer p, Fighter f, Dodge dodge, Vec3 direction, boolean special, int level) {
        Vec3 chest = StyleHits.chest(p);
        if (special) {
            // Recoil: a shockwave fired forward throws her back and up, and shoves whoever was in front.
            Vec3 forward = direction.scale(-1);
            StyleMotion.dash(p, direction.scale(.7), .75, 1, 12, true, false);
            for (LivingEntity target : StyleHits.foes(p, chest.add(forward.scale(2)), 2.5))
                StyleHits.shove(target, forward.scale(.8).add(0, .25, 0));
            AbilityEffects.send(world(p), Kind.QUAKE_RINGS, chest, chest.add(forward.scale(4)), .9);
            pose(p, ArmPose.STYLE_DOUBLE_PALM, 10);
        } else {
            // She kicks off the air itself: a ring hangs where she pushed.
            StyleMotion.dash(p, direction.scale(1.15), .14, 4, 6, false, false);
            AbilityEffects.send(world(p), Kind.QUAKE_CONTACT, chest.subtract(direction.scale(.4)), chest.subtract(direction.scale(1.4)), 1.1);
            AbilityEffects.send(world(p), Kind.QUAKE_PULSE, p.position(), p.position(), 1.4);
            pose(p, ArmPose.QUAKE_DASH, 9);
        }
        QuakeKit.strain(p, 2);
        StyleHits.sound(p, SoundEvents.WARDEN_ATTACK_IMPACT, .4F, 1.4F);
    }

    /** Rebound: the evaded blow goes straight back into whoever threw it. */
    @Override
    protected void onCounter(ServerPlayer p, Fighter f, DamageSource source) {
        LivingEntity attacker = f.marked();
        Vec3 chest = StyleHits.chest(p);
        if (attacker != null && attacker.distanceToSqr(p) < 10 * 10) {
            int level = Math.max(1, com.jayptucker.heroesevolved.progression.PlayerProgressionService.getLevel(p));
            press(p, attacker, (.8F + .2F * level) * QuakeKit.power(p));
            StyleHits.away(attacker, p.position(), StyleHits.level(p), .9, .35);
            AbilityEffects.send(world(p), Kind.QUAKE_RINGS, chest, attacker.getBoundingBox().getCenter(), .8);
        }
        AbilityEffects.send(world(p), Kind.QUAKE_PULSE, p.position(), p.position(), 2);
        QuakeKit.strain(p, 4);
        StyleHits.sound(p, SoundEvents.WARDEN_SONIC_CHARGE, .4F, 1.9F);
        say(p, "quake.heroesevolved.rebound");
    }
}
