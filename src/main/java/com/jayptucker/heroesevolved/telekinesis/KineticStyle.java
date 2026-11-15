package com.jayptucker.heroesevolved.telekinesis;

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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Puppeteer: a telekinetic who never lays a hand on anyone. Each click is a swat: a flick of the hand and the foe in
 * sight (out to twelve blocks) is batted sideways by an unseen hand, left then right, and anyone they are batted into
 * is hurt too; the finisher lifts the foe into the air and slams them down a moment later; crouching repulses everyone
 * round. Dodges are the telekinetic shoving themselves (side) or floating up and back (from level 2). A blow evaded is
 * answered with a Grip: the attacker hangs helpless in the air for a moment, and the next swat throws them twice as hard.
 */
public final class KineticStyle extends ComboStyle {
    public static final KineticStyle STYLE = new KineticStyle();
    public static final double SWAT_REACH = 12, REPULSE_RADIUS = 4.5;
    /** Ticks a lifted foe hangs before the slam, and how long a Grip holds. */
    public static final int LIFT_HANG = 9, GRIP_TICKS = 30, THROWN_TICKS = 10;

    /** A body thrown by a swat: whoever it is batted into is hurt as well, once. */
    private record Thrown(ServerPlayer owner, LivingEntity body, long until, float damage, Set<UUID> struck) {}
    private record Lift(ServerPlayer owner, LivingEntity body, long slamAt, float damage) {}
    private record Grip(ServerPlayer owner, LivingEntity body, long until, Vec3 at) {}
    private final List<Thrown> thrown = new ArrayList<>();
    private final List<Lift> lifts = new ArrayList<>();
    private final List<Grip> grips = new ArrayList<>();

    private KineticStyle() { super(ModAbilities.TELEKINESIS_ID, "telekinesis.heroesevolved.too_tired"); }

    private static ServerLevel world(ServerPlayer p) { return p.serverLevel(); }

    public boolean gripping(LivingEntity body) { return grips.stream().anyMatch(g -> g.body == body); }

    public int lifting() { return lifts.size(); }

    @Override
    protected void throwMove(ServerPlayer p, Fighter f, Move move, int level, boolean loaded) {
        switch (move) {
            case LEAD, REAR -> swat(p, f, level, move == Move.REAR, loaded);
            case FINISHER -> lift(p, level);
            case LOW -> repulse(p, level);
        }
    }

    /** A flick of the hand: the foe in sight is batted sideways by an unseen hand. */
    private void swat(ServerPlayer p, Fighter f, int level, boolean rear, boolean loaded) {
        LivingEntity target = loaded ? f.marked() : null;
        if (target == null) target = StyleHits.aimed(p, SWAT_REACH, .9);
        Vec3 forward = StyleHits.level(p), side = StyleHits.right(forward).scale(rear ? 1 : -1);
        if (target != null) {
            float damage = (2 + .3F * level) * (loaded ? 2 : 1);
            StyleHits.hurt(p, target, damage);
            Vec3 throwing = side.scale(loaded ? 1.6 : .9).add(forward.scale(loaded ? .6 : .15)).add(0, .3, 0);
            // A swat lets go of a gripped body: the throw is the release.
            final LivingEntity hit = target;
            grips.removeIf(g -> g.body == hit);
            StyleHits.shove(target, throwing);
            thrown.add(new Thrown(p, target, now(p) + THROWN_TICKS, damage, new HashSet<>()));
            Vec3 center = target.getBoundingBox().getCenter();
            AbilityEffects.send(world(p), Kind.TK_IMPACT, center.subtract(side.scale(.6)), center.add(side), loaded ? 1.6 : .9);
            SetPiecePayload.send(world(p), Piece.STYLE_TK_PALM, center, center.add(throwing.normalize()), loaded ? 1.6F : 1, null);
            StyleHits.sound(p, center, SoundEvents.PLAYER_ATTACK_KNOCKBACK, .8F, 1.3F);
        }
        pose(p, rear ? ArmPose.STYLE_SWAT : ArmPose.STYLE_SWAT_OFF, 8);
        StyleHits.sound(p, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), .3F, 1.9F);
    }

    /** The foe is lifted into the air, held, and slammed down. */
    private void lift(ServerPlayer p, int level) {
        LivingEntity target = StyleHits.aimed(p, SWAT_REACH, .9);
        if (target != null) {
            // The grip crushes as it lifts; the slam does the rest.
            StyleHits.hurt(p, target, 1.5F + .2F * level);
            StyleHits.shove(target, new Vec3(0, .85, 0));
            lifts.add(new Lift(p, target, now(p) + LIFT_HANG, 4 + level));
            AbilityEffects.send(world(p), Kind.TK_GRIP, target.position(), target.getEyePosition(), 1.2);
            SetPiecePayload.send(world(p), Piece.STYLE_TK_GRASP, target.position(), target.position(), LIFT_HANG, target);
            AbilityEffects.send(world(p), Kind.TK_LAUNCH, target.position(), target.position().add(0, 1, 0), 1);
        }
        pose(p, ArmPose.STYLE_LIFT, LIFT_HANG + 6);
        StyleHits.sound(p, SoundEvents.ILLUSIONER_CAST_SPELL, .6F, 1.4F);
    }

    /** Everyone round is shoved away at once. */
    private void repulse(ServerPlayer p, int level) {
        Vec3 feet = p.position();
        for (LivingEntity target : StyleHits.foes(p, feet.add(0, 1, 0), REPULSE_RADIUS + 1)) {
            StyleHits.hurt(p, target, 1.5F + .4F * level);
            StyleHits.away(target, feet, StyleHits.level(p), 1.0, .4);
        }
        AbilityEffects.send(world(p), Kind.TK_WAVE, feet.add(0, .6, 0), feet.add(0, 1.6, 0), REPULSE_RADIUS);
        pose(p, ArmPose.KINETIC_RELEASE, 12);
        StyleHits.sound(p, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), .9F, .7F);
    }

    @Override
    protected void dodgeMove(ServerPlayer p, Fighter f, Dodge dodge, Vec3 direction, boolean special, int level) {
        Vec3 from = p.position();
        if (special) {
            // Float up and back on the mind alone, falling slow.
            StyleMotion.dash(p, direction.scale(.6), .62, 1, 12, true, false);
            p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 30, 0, false, false, true));
            AbilityEffects.send(world(p), Kind.TK_LAUNCH, from, from.add(0, 1, 0), 1.2);
            pose(p, ArmPose.STYLE_BACKSTEP, 12);
        } else {
            // The telekinetic shoves themself.
            StyleMotion.dash(p, direction.scale(1.15), .12, 4, 6, false, false);
            AbilityEffects.send(world(p), Kind.TK_STEP, from.add(0, .2, 0), from.add(direction), 1.2);
            pose(p, ArmPose.STYLE_SIDESTEP, 10);
        }
        StyleHits.sound(p, SoundEvents.BREEZE_JUMP, .7F, 1.3F);
    }

    /** Grip: the attacker hangs helpless in the air for a moment. */
    @Override
    protected void onCounter(ServerPlayer p, Fighter f, DamageSource source) {
        LivingEntity attacker = f.marked();
        if (attacker != null && attacker.distanceToSqr(p) < 14 * 14) {
            grips.add(new Grip(p, attacker, now(p) + GRIP_TICKS, attacker.position().add(0, 1.2, 0)));
            AbilityEffects.send(world(p), Kind.TK_GRIP, attacker.position(), attacker.getEyePosition(), 1.4);
            SetPiecePayload.send(world(p), Piece.STYLE_TK_GRASP, attacker.position(), attacker.position(), GRIP_TICKS, attacker);
        }
        StyleHits.sound(p, SoundEvents.ILLUSIONER_PREPARE_BLINDNESS, .6F, 1.6F);
        say(p, "telekinesis.heroesevolved.grip");
    }

    @Override
    public void tick(MinecraftServer server) {
        super.tick(server);
        long now = server.overworld().getGameTime();
        // Bodies batted into others hurt them too.
        thrown.removeIf(t -> now > t.until || !t.body.isAlive());
        for (Thrown t : thrown)
            for (LivingEntity other : t.body.level().getEntitiesOfClass(LivingEntity.class, t.body.getBoundingBox().inflate(.6),
                    e -> e != t.body && e != t.owner && StyleHits.hostile(t.owner, e))) {
                if (!t.struck.add(other.getUUID())) continue;
                StyleHits.hurt(t.owner, other, t.damage * .8F);
                StyleHits.hurt(t.owner, t.body, 1.5F);
                StyleHits.shove(other, t.body.getDeltaMovement().scale(.7).add(0, .25, 0));
                AbilityEffects.send((ServerLevel) other.level(), Kind.TK_IMPACT, other.getBoundingBox().getCenter(),
                        other.getBoundingBox().getCenter().add(t.body.getDeltaMovement()), 1.2);
                StyleHits.sound(t.owner, other.position(), SoundEvents.PLAYER_ATTACK_CRIT, .9F, .8F);
            }
        // Lifted foes are slammed down.
        for (Lift l : List.copyOf(lifts)) {
            if (!l.body.isAlive()) { lifts.remove(l); continue; }
            if (now < l.slamAt) { l.body.fallDistance = 0; continue; }
            lifts.remove(l);
            StyleMotion.push(l.body, new Vec3(0, -1.7, 0));
            StyleHits.hurt(l.owner, l.body, l.damage);
            Vec3 ground = l.body.position();
            AbilityEffects.send((ServerLevel) l.body.level(), Kind.TK_IMPACT, ground, ground.add(0, -1, 0), 1.6);
            CameraShakePayload.sendAround((ServerLevel) l.body.level(), ground, 10, .2F, 5);
            StyleHits.sound(l.owner, ground, SoundEvents.ANVIL_LAND, .5F, .9F);
        }
        // Gripped attackers hang where they were caught.
        grips.removeIf(g -> now > g.until || !g.body.isAlive());
        for (Grip g : grips) {
            StyleMotion.push(g.body, g.at.subtract(g.body.position()).scale(.3));
            g.body.fallDistance = 0;
        }
    }

    @Override
    public void forget(ServerPlayer player) {
        super.forget(player);
        thrown.removeIf(t -> t.owner == player);
        lifts.removeIf(l -> l.owner == player);
        grips.removeIf(g -> g.owner == player);
    }

    @Override public void clear() { super.clear(); thrown.clear(); lifts.clear(); grips.clear(); }
}
