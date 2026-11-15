package com.jayptucker.heroesevolved.cryo;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.combat.ComboStyle;
import com.jayptucker.heroesevolved.combat.FightingStyles.Dodge;
import com.jayptucker.heroesevolved.combat.StyleHits;
import com.jayptucker.heroesevolved.combat.StyleMotion;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.hydro.HydroService;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Ice Skater: a cryokinetic who fights on a skin of their own ice. Jabs are ice-cased fists that chill (a Soaked foe
 * freezes solid at once); the finisher is an uppercut that drives a spike of ice up under the foe in front and throws
 * them; crouching presses a ring of frost into the ground that slows everyone round. Every dodge is a skate: a strip
 * of glittering ice is laid along it and lingers, chilling any foe who stands on it, and back (from level 2) is a
 * spinning backward glide that throws frost off as it turns. A blow evaded is a Cold Snap: the attacker freezes solid,
 * and the next jab shatters them for double.
 */
public final class CryoStyle extends ComboStyle {
    public static final CryoStyle STYLE = new CryoStyle();
    public static final double JAB_REACH = 4.5, SPIKE_REACH = 6, FROST_RADIUS = 4;
    /** How long a skate's ice strip lingers. */
    public static final int STRIP_TICKS = 60;

    private record Strip(ServerPlayer owner, Vec3 from, Vec3 to, long until) {}
    private final List<Strip> strips = new ArrayList<>();

    private CryoStyle() { super(ModAbilities.CRYOKINESIS_ID, "cryo.heroesevolved.too_tired"); }

    private static ServerLevel world(ServerPlayer p) { return p.serverLevel(); }

    public int strips(ServerPlayer p) { return (int) strips.stream().filter(s -> s.owner == p).count(); }

    /** A cold blow: Soaked foes freeze solid, frozen ones shatter. */
    private static void cold(ServerPlayer p, LivingEntity target, float damage, int freezeTicks) {
        if (CryoService.isFrozen(target)) {
            CryoService.shatterHit(p, target, damage * 2);
            return;
        }
        boolean soaked = HydroService.soaked(target);
        CryoService.coldHit(p, target, damage);
        if (soaked) CryoService.freezeSolid(target, Math.max(freezeTicks, 30), p);
        else CryoService.chill(target, 40, p);
    }

    @Override
    protected void throwMove(ServerPlayer p, Fighter f, Move move, int level, boolean loaded) {
        switch (move) {
            case LEAD, REAR -> jab(p, level, move == Move.REAR, loaded);
            case FINISHER -> uppercut(p, level);
            case LOW -> frostRing(p, level);
        }
    }

    private void jab(ServerPlayer p, int level, boolean rear, boolean loaded) {
        Vec3 fist = StyleHits.hand(p, 1, rear), aim = p.getLookAngle().normalize();
        LivingEntity target = StyleHits.aimed(p, JAB_REACH, .5);
        Vec3 end = target != null ? target.getBoundingBox().getCenter() : StyleHits.reach(p, p.getEyePosition(), aim, 2.5);
        if (target != null) {
            float damage = (2 + .4F * level) * (loaded ? 1.6F : 1);
            if (loaded) CryoService.shatterHit(p, target, damage * 2);
            else cold(p, target, damage, 0);
            StyleHits.shove(target, aim.scale(.22).add(0, .05, 0));
            AbilityEffects.send(world(p), Kind.CRYO_CHILL, target.position(), target.getEyePosition(), loaded ? 1.6 : 1);
            if (loaded) AbilityEffects.send(world(p), Kind.CRYO_SHATTER, target.position(), target.getEyePosition(), 1.2);
        }
        AbilityEffects.send(world(p), Kind.CRYO_BOLT, fist, end, .5);
        pose(p, rear ? ArmPose.STYLE_CROSS : ArmPose.STYLE_JAB, 8);
        StyleHits.sound(p, SoundEvents.GLASS_HIT, .6F, 1.6F);
        StyleHits.sound(p, SoundEvents.PLAYER_HURT_FREEZE, .3F, 1.8F);
    }

    /** A spike of ice driven up under the foe in front: it throws them high. */
    private void uppercut(ServerPlayer p, int level) {
        LivingEntity target = StyleHits.aimed(p, SPIKE_REACH, .9);
        Vec3 forward = StyleHits.level(p);
        Vec3 base = target != null ? target.position() : p.position().add(forward.scale(2.5));
        if (target != null) {
            cold(p, target, 4 + level, 25);
            StyleHits.shove(target, new Vec3(0, 1.0, 0).add(forward.scale(.25)));
        }
        AbilityEffects.send(world(p), Kind.CRYO_ICE_GROWTH, base, base.add(0, 2.6, 0), 1.6);
        AbilityEffects.send(world(p), Kind.CRYO_SHATTER, base.add(0, 1, 0), base.add(0, 2.4, 0), .9);
        pose(p, ArmPose.STYLE_UPPERCUT, 12);
        StyleHits.sound(p, base, SoundEvents.AMETHYST_BLOCK_BREAK, 1, .7F);
        StyleHits.sound(p, base, SoundEvents.GLASS_BREAK, .6F, 1.2F);
    }

    /** A palm pressed to the ground: a ring of frost spreads, slowing everyone it reaches. */
    private void frostRing(ServerPlayer p, int level) {
        Vec3 feet = p.position();
        for (LivingEntity target : StyleHits.foes(p, feet, FROST_RADIUS + 1)) {
            if (Math.abs(target.getY() - feet.y) > 1.5
                    || target.position().subtract(feet).horizontalDistance() > FROST_RADIUS + target.getBbWidth() * .5) continue;
            cold(p, target, 2 + .4F * level, 30);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
            StyleHits.away(target, feet, StyleHits.level(p), .35, .15);
        }
        AbilityEffects.send(world(p), Kind.CRYO_CHILL, feet, feet.add(0, 1.2, 0), FROST_RADIUS * 2);
        AbilityEffects.send(world(p), Kind.CRYO_FREEZE, feet, feet.add(0, .8, 0), FROST_RADIUS);
        pose(p, ArmPose.STYLE_GROUND, 14);
        StyleHits.sound(p, SoundEvents.POWDER_SNOW_PLACE, 1, .6F);
        StyleHits.sound(p, SoundEvents.PLAYER_HURT_FREEZE, .5F, .8F);
    }

    @Override
    protected void dodgeMove(ServerPlayer p, Fighter f, Dodge dodge, Vec3 direction, boolean special, int level) {
        Vec3 from = p.position();
        double length = special ? 5 : 6;
        if (special) {
            // A spinning backward glide that throws frost off as it turns.
            StyleMotion.dash(p, direction.scale(1.0), .18, 5, 8, false, false);
            for (LivingEntity target : StyleHits.foes(p, from, 2.8)) CryoService.chill(target, 50, p);
            AbilityEffects.send(world(p), Kind.CRYO_CHILL, from, from.add(0, 1, 0), 5);
            pose(p, ArmPose.STYLE_SPIN, 12);
        } else {
            StyleMotion.dash(p, direction.scale(1.2), .04, 5, 7, false, false);
            pose(p, ArmPose.CRYO_SURF, 10);
        }
        Vec3 to = from.add(direction.scale(length));
        strips.add(new Strip(p, from, to, now(p) + STRIP_TICKS));
        SetPiecePayload.send(world(p), Piece.STYLE_ICE_STRIP, from, to, 1, null);
        AbilityEffects.send(world(p), Kind.CRYO_RIDE, from, to, 1);
        StyleHits.sound(p, SoundEvents.GLASS_STEP, .8F, 1.5F);
        StyleHits.sound(p, SoundEvents.POWDER_SNOW_STEP, .8F, 1.3F);
    }

    /** Cold Snap: the one who swung freezes solid. */
    @Override
    protected void onCounter(ServerPlayer p, Fighter f, DamageSource source) {
        LivingEntity attacker = f.marked();
        if (attacker != null && attacker.distanceToSqr(p) < 10 * 10) {
            CryoService.freezeSolid(attacker, 40, p);
            AbilityEffects.send(world(p), Kind.CRYO_FREEZE, attacker.position(), attacker.getEyePosition(), 1.4);
        }
        AbilityEffects.send(world(p), Kind.CRYO_CHILL, p.position(), p.getEyePosition(), 2);
        StyleHits.sound(p, SoundEvents.GLASS_BREAK, .7F, 1.8F);
        say(p, "cryo.heroesevolved.cold_snap");
    }

    /** Foes standing on a skate's strip are chilled while it lasts. */
    @Override
    public void tick(net.minecraft.server.MinecraftServer server) {
        super.tick(server);
        long now = server.overworld().getGameTime();
        strips.removeIf(s -> now > s.until || !s.owner.isAlive());
        if (now % 10 != 0) return;
        for (Strip s : strips) {
            Vec3 ab = s.to.subtract(s.from);
            for (LivingEntity target : StyleHits.foes(s.owner, s.from.lerp(s.to, .5), ab.length() * .5 + 1.5)) {
                double t = Math.clamp(target.position().subtract(s.from).dot(ab) / Math.max(1e-4, ab.lengthSqr()), 0, 1);
                if (target.position().distanceTo(s.from.add(ab.scale(t))) > 1.1 || Math.abs(target.getY() - s.from.y) > 1.2) continue;
                CryoService.chill(target, 30, s.owner);
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 3));
            }
        }
    }

    @Override
    public void forget(ServerPlayer player) {
        super.forget(player);
        strips.removeIf(s -> s.owner == player);
    }

    @Override public void clear() { super.clear(); strips.clear(); }
}
