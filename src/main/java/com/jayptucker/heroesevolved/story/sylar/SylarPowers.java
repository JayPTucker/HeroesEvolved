package com.jayptucker.heroesevolved.story.sylar;

import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Everything Sylar can do with what he has taken, as real moves: each has a windup the player can
 * read (a pose, a telegraph, a sound), then does what the power does, in its own colours. Telekinesis
 * is his own gift and always available. Passive gifts (Precognition's sidestep, Regeneration's second
 * life, Mimicry's borrowing) live on {@link SylarEntity}.
 */
public final class SylarPowers {
    /** How he holds himself while winding a move up: drawn by the renderer. */
    public enum Pose { NONE, ONE_HAND, TWO_HANDS, RAISED, CROUCH }

    interface Action { void run(SylarEntity sylar, ServerLevel level, LivingEntity target, float power); }

    /**
     * One move. {@code telegraph} runs when the windup starts (and its {@code pulse} every few ticks of
     * it); {@code execute} when it ends. Ranges are in blocks from Sylar to the target.
     */
    public record Move(String id, ResourceLocation power, int windup, int cooldown, double minRange, double maxRange,
                       boolean needsSight, Pose pose, Action telegraph, Action pulse, Action execute) {}

    private static final ResourceLocation TELEKINESIS = ModAbilities.TELEKINESIS_ID;

    public static final List<Move> MOVES = List.of(
            // ---- His own gift ----
            new Move("tk_throw", TELEKINESIS, 14, 120, 2, 16, true, Pose.ONE_HAND,
                    (s, l, t, p) -> sound(l, s.position(), SoundEvents.ILLUSIONER_PREPARE_MIRROR, 0.8F, 0.6F),
                    (s, l, t, p) -> AbilityEffects.send(l, Kind.TK_GRIP, hand(s), t.getBoundingBox().getCenter(), 0.6),
                    (s, l, t, p) -> {
                        Vec3 away = flat(t.position().subtract(s.position()));
                        hurt(s, t, 4 * p);
                        push(t, away.scale(1.1).add(0, 0.75, 0));
                        AbilityEffects.send(l, Kind.TK_LAUNCH, t.getBoundingBox().getCenter(), t.getBoundingBox().getCenter().add(away.scale(4)), 1.2);
                        sound(l, t.position(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 0.8F, 0.7F);
                    }),
            new Move("tk_pin", TELEKINESIS, 10, 360, 2, 14, true, Pose.TWO_HANDS,
                    (s, l, t, p) -> sound(l, s.position(), SoundEvents.ILLUSIONER_CAST_SPELL, 0.8F, 0.5F),
                    (s, l, t, p) -> AbilityEffects.send(l, Kind.TK_GRIP, hand(s), t.getBoundingBox().getCenter(), 0.9),
                    (s, l, t, p) -> {
                        s.pin(t, 50);
                        if (t instanceof ServerPlayer player) SylarVoice.warn(player, "story.heroesevolved.sylar.warn.pin");
                        sound(l, t.position(), SoundEvents.CHAIN_PLACE, 1.0F, 0.5F);
                    }),
            // ---- Electrokinesis ----
            new Move("chain_lightning", ModAbilities.ELECTROKINESIS_ID, 12, 110, 2, 18, true, Pose.ONE_HAND,
                    (s, l, t, p) -> sound(l, s.position(), SoundEvents.BEACON_ACTIVATE, 0.5F, 2.0F),
                    (s, l, t, p) -> AbilityEffects.send(l, Kind.ELECTRIC_TELEGRAPH, hand(s), hand(s).add(0, 1, 0), 1.0),
                    (s, l, t, p) -> {
                        Vec3 from = hand(s);
                        LivingEntity last = t;
                        AbilityEffects.send(l, Kind.ELECTRIC_ARC, from, t.getBoundingBox().getCenter(), 1.2);
                        hurt(s, t, 6 * p);
                        t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 2));
                        int leaps = 0;
                        for (LivingEntity next : foes(s, t.position(), 5)) {
                            if (next == t || ++leaps > 2) continue;
                            AbilityEffects.send(l, Kind.ELECTRIC_ARC, last.getBoundingBox().getCenter(), next.getBoundingBox().getCenter(), 0.9);
                            hurt(s, next, 4 * p);
                            last = next;
                        }
                        sound(l, t.position(), SoundEvents.LIGHTNING_BOLT_IMPACT, 0.6F, 1.6F);
                    }),
            new Move("thunderstrike", ModAbilities.ELECTROKINESIS_ID, 30, 240, 4, 24, true, Pose.RAISED,
                    (s, l, t, p) -> { s.mark(t.position()); sound(l, t.position(), SoundEvents.TRIDENT_THUNDER.value(), 0.4F, 1.8F); },
                    (s, l, t, p) -> AbilityEffects.send(l, Kind.ELECTRIC_TELEGRAPH, s.mark(), s.mark().add(0, 1, 0), 2.5),
                    (s, l, t, p) -> {
                        // It lands where you were standing when he raised his hand: step out of the mark.
                        Vec3 at = s.mark();
                        AbilityEffects.send(l, Kind.ELECTRIC_SKY_BOLT, at, at.add(0, 28, 0), 1.0);
                        for (LivingEntity hit : foes(s, at, 2.5)) hurt(s, hit, 9 * p);
                        sound(l, at, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.0F, 1.2F);
                    }),
            // ---- Cryokinesis ----
            new Move("ice_lance", ModAbilities.CRYOKINESIS_ID, 10, 110, 3, 20, true, Pose.ONE_HAND,
                    (s, l, t, p) -> sound(l, s.position(), SoundEvents.GLASS_HIT, 0.8F, 0.6F),
                    (s, l, t, p) -> AbilityEffects.send(l, Kind.CRYO_CHILL, hand(s), hand(s).add(0, 0.5, 0), 0.6),
                    (s, l, t, p) -> {
                        AbilityEffects.send(l, Kind.CRYO_BOLT, hand(s), t.getEyePosition(), 1.2);
                        hurt(s, t, 6 * p);
                        com.jayptucker.heroesevolved.cryo.CryoService.freezeSolid(t, 30, null);
                        sound(l, t.position(), SoundEvents.GLASS_BREAK, 0.8F, 1.4F);
                    }),
            new Move("cold_snap", ModAbilities.CRYOKINESIS_ID, 16, 220, 0, 6, false, Pose.CROUCH,
                    (s, l, t, p) -> sound(l, s.position(), SoundEvents.POWDER_SNOW_STEP, 1.0F, 0.5F),
                    (s, l, t, p) -> AbilityEffects.send(l, Kind.CRYO_CHILL, s.position(), s.position().add(0, 1, 0), 2.0),
                    (s, l, t, p) -> {
                        AbilityEffects.send(l, Kind.CRYO_CHILL, s.position(), s.position().add(0, 1, 0), 6.0);
                        for (LivingEntity hit : foes(s, s.position(), 6)) {
                            hurt(s, hit, 4 * p);
                            hit.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
                            hit.setTicksFrozen(Math.min(hit.getTicksRequiredToFreeze(), hit.getTicksFrozen() + 80));
                        }
                    }),
            // ---- Pyrokinesis ----
            new Move("fire_stream", ModAbilities.PYROKINESIS_ID, 10, 140, 1, 10, true, Pose.TWO_HANDS,
                    (s, l, t, p) -> sound(l, s.position(), SoundEvents.FIRECHARGE_USE, 0.8F, 0.6F),
                    (s, l, t, p) -> AbilityEffects.send(l, Kind.FIRE_STREAM, hand(s), hand(s).add(s.getLookAngle()), 0.5),
                    (s, l, t, p) -> {
                        Vec3 from = hand(s), aim = t.getEyePosition().subtract(from).normalize();
                        AbilityEffects.send(l, Kind.FIRE_STREAM, from, from.add(aim.scale(10)), 1.3);
                        for (LivingEntity hit : cone(s, from, aim, 10, 0.85)) { hurt(s, hit, 5 * p); hit.igniteForSeconds(4); }
                        sound(l, from, SoundEvents.BLAZE_SHOOT, 1.0F, 0.8F);
                    }),
            // ---- Radiation ----
            new Move("radiant_heat", ModAbilities.RADIATION_ID, 18, 200, 0, 10, true, Pose.TWO_HANDS,
                    (s, l, t, p) -> sound(l, s.position(), SoundEvents.BEACON_POWER_SELECT, 0.6F, 0.5F),
                    (s, l, t, p) -> AbilityEffects.send(l, Kind.RADIATION_PULSE, s.position(), s.getEyePosition(), 1.5),
                    (s, l, t, p) -> {
                        Vec3 from = hand(s), aim = t.getEyePosition().subtract(from).normalize();
                        AbilityEffects.send(l, Kind.RADIATION_CONE, from, from.add(aim.scale(10)), 1.4);
                        for (LivingEntity hit : cone(s, from, aim, 10, 0.75)) {
                            hurt(s, hit, 7 * p);
                            hit.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 0));
                            hit.igniteForSeconds(2);
                        }
                    }),
            // ---- Quake ----
            new Move("ground_pound", ModAbilities.QUAKE_ID, 20, 220, 0, 7, false, Pose.CROUCH,
                    (s, l, t, p) -> sound(l, s.position(), SoundEvents.WARDEN_DIG, 0.8F, 1.2F),
                    (s, l, t, p) -> AbilityEffects.send(l, Kind.QUAKE_CHANNEL, s.position(), s.position().add(0, 0.5, 0), 1.0),
                    (s, l, t, p) -> {
                        AbilityEffects.send(l, Kind.QUAKE_IMPACT, s.position(), s.position(), 7);
                        for (LivingEntity hit : foes(s, s.position(), 7)) {
                            hurt(s, hit, 7 * p);
                            push(hit, flat(hit.position().subtract(s.position())).scale(0.6).add(0, 0.7, 0));
                        }
                        sound(l, s.position(), SoundEvents.GENERIC_EXPLODE.value(), 0.8F, 0.6F);
                    }),
            // ---- Superspeed ----
            new Move("blitz", ModAbilities.SUPERSPEED_ID, 6, 150, 4, 20, true, Pose.NONE,
                    (s, l, t, p) -> sound(l, s.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.8F, 1.6F),
                    (s, l, t, p) -> { },
                    (s, l, t, p) -> {
                        Vec3 from = s.position(), delta = t.position().subtract(from);
                        Vec3 end = com.jayptucker.heroesevolved.speed.SpeedService.swept(s, delta.normalize().scale(Math.max(0, delta.length() - 1.5)));
                        if (end.distanceToSqr(t.position()) > 9) return;
                        s.teleportTo(end.x, end.y, end.z);
                        AbilityEffects.send(l, Kind.SPEED_ZIP, from, end, 1.2);
                        s.flurry(t, 3, 2.5F * p);
                    }),
            // ---- Time ----
            new Move("time_fold", ModAbilities.TIME_MANIPULATION_ID, 10, 260, 3, 24, true, Pose.RAISED,
                    (s, l, t, p) -> sound(l, s.position(), com.jayptucker.heroesevolved.sounds.ModSounds.SYLAR_WATCH.get(), 1.0F, 1.4F),
                    (s, l, t, p) -> AbilityEffects.send(l, Kind.TEMPORAL_FIELD, s.position(), s.getEyePosition(), 1.2),
                    (s, l, t, p) -> {
                        // Behind you, between one second and the next.
                        Vec3 behind = t.position().subtract(flat(t.getLookAngle()).scale(1.8));
                        if (!s.level().noCollision(s, s.getBoundingBox().move(behind.subtract(s.position())))) return;
                        Vec3 from = s.position();
                        s.teleportTo(behind.x, behind.y, behind.z);
                        AbilityEffects.send(l, Kind.TIME_FOLD, from, behind, 1.0);
                        t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2));
                        hurt(s, t, 5 * p);
                    }),
            new Move("slow_time", ModAbilities.TIME_MANIPULATION_ID, 12, 280, 2, 16, true, Pose.ONE_HAND,
                    (s, l, t, p) -> sound(l, t.position(), com.jayptucker.heroesevolved.sounds.ModSounds.SYLAR_WATCH.get(), 1.0F, 0.6F),
                    (s, l, t, p) -> { },
                    (s, l, t, p) -> {
                        AbilityEffects.send(l, Kind.TIME_SLOW_PULSE, t.position(), t.position().add(0, 1, 0), 3);
                        t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 3));
                        t.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 80, 2));
                    }),
            // ---- Sonic Resonance ----
            new Move("scream", ModAbilities.SONIC_RESONANCE_ID, 10, 170, 2, 14, true, Pose.NONE,
                    (s, l, t, p) -> sound(l, s.position(), SoundEvents.WARDEN_SONIC_CHARGE, 0.6F, 1.4F),
                    (s, l, t, p) -> { },
                    (s, l, t, p) -> {
                        Vec3 from = s.getEyePosition(), aim = t.getEyePosition().subtract(from).normalize();
                        AbilityEffects.send(l, Kind.SONIC_WAVE, from, from.add(aim.scale(14)), 1.2);
                        for (LivingEntity hit : cone(s, from, aim, 14, 0.9)) { hurt(s, hit, 6 * p); push(hit, aim.scale(1.0).add(0, 0.2, 0)); }
                        sound(l, from, com.jayptucker.heroesevolved.sounds.ModSounds.SONIC_CRY.get(), 1.0F, 0.8F);
                    }),
            // ---- Flight ----
            new Move("dive", ModAbilities.FLIGHT_ID, 20, 260, 4, 20, true, Pose.RAISED,
                    (s, l, t, p) -> {
                        s.setDeltaMovement(0, 1.1, 0); s.hurtMarked = true;
                        AbilityEffects.send(l, Kind.FLIGHT_BURST, s.position(), s.position().add(0, 1, 0), 1.6);
                        sound(l, s.position(), SoundEvents.BREEZE_JUMP, 1.0F, 0.6F);
                    },
                    (s, l, t, p) -> s.setDeltaMovement(s.getDeltaMovement().multiply(0.6, 0.3, 0.6)),
                    (s, l, t, p) -> {
                        Vec3 at = t.position();
                        if (!s.level().noCollision(s, s.getBoundingBox().move(at.subtract(s.position())))) at = t.position().add(flat(s.position().subtract(t.position())).scale(1.2));
                        Vec3 from = s.position();
                        s.teleportTo(at.x, at.y, at.z);
                        AbilityEffects.send(l, Kind.SPEED_ZIP, from, at, 1.4);
                        AbilityEffects.send(l, Kind.QUAKE_IMPACT, at, at, 3);
                        for (LivingEntity hit : foes(s, at, 3)) { hurt(s, hit, 7 * p); push(hit, flat(hit.position().subtract(at)).scale(0.7).add(0, 0.5, 0)); }
                        sound(l, at, SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.0F, 0.8F);
                    }),
            // ---- Telepathy ----
            new Move("dominate", ModAbilities.TELEPATHY_ID, 14, 320, 2, 20, true, Pose.ONE_HAND,
                    (s, l, t, p) -> sound(l, t.position(), SoundEvents.WARDEN_NEARBY_CLOSER, 0.7F, 1.2F),
                    (s, l, t, p) -> AbilityEffects.send(l, Kind.PSYCHIC_TETHER, s.getEyePosition(), t.getEyePosition(), 0.5),
                    (s, l, t, p) -> {
                        t.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0));
                        t.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 60, 0));
                        AbilityEffects.send(l, Kind.MIND_PULSE, t.position(), t.getEyePosition(), 1.5);
                        // Every monster nearby turns on you.
                        int turned = 0;
                        for (Mob mob : l.getEntitiesOfClass(Mob.class, t.getBoundingBox().inflate(16), m -> m instanceof Enemy && !(m instanceof SylarEntity))) {
                            if (++turned > 8) break;
                            mob.setTarget(t);
                        }
                    }),
            // ---- Invisibility ----
            new Move("vanish", ModAbilities.INVISIBILITY_ID, 40, 320, 2, 20, false, Pose.NONE,
                    (s, l, t, p) -> {
                        s.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 42, 0, false, false));
                        AbilityEffects.send(l, Kind.MIND_PULSE, s.position(), s.getEyePosition(), 1.2);
                        sound(l, s.position(), SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.0F, 0.6F);
                    },
                    (s, l, t, p) -> { },
                    (s, l, t, p) -> {
                        Vec3 behind = t.position().subtract(flat(t.getLookAngle()).scale(1.6));
                        if (s.level().noCollision(s, s.getBoundingBox().move(behind.subtract(s.position())))) s.teleportTo(behind.x, behind.y, behind.z);
                        s.removeEffect(MobEffects.INVISIBILITY);
                        hurt(s, t, 8 * p);
                        sound(l, t.position(), SoundEvents.PLAYER_ATTACK_CRIT, 1.0F, 0.7F);
                    }),
            // ---- Phasing ----
            new Move("phase_strike", ModAbilities.PHASING_ID, 8, 150, 0, 3.5, false, Pose.ONE_HAND,
                    (s, l, t, p) -> sound(l, s.position(), SoundEvents.ENDERMAN_TELEPORT, 0.6F, 1.6F),
                    (s, l, t, p) -> AbilityEffects.send(l, Kind.MIND_PULSE, hand(s), hand(s), 0.5),
                    (s, l, t, p) -> {
                        // A hand that passes through armour.
                        t.invulnerableTime = 0;
                        if (t.hurt(s.damageSources().indirectMagic(s, s), 7 * p)) sound(l, t.position(), SoundEvents.WARDEN_HEARTBEAT, 1.0F, 1.4F);
                    })
    );

    private SylarPowers() {}

    // ------------------------------------------------------------------

    static Vec3 hand(Entity s) { return s.getEyePosition().add(flat(s.getLookAngle()).scale(0.6)).subtract(0, 0.4, 0); }

    static Vec3 flat(Vec3 v) {
        Vec3 f = new Vec3(v.x, 0, v.z);
        return f.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : f.normalize();
    }

    static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.HOSTILE, volume, pitch);
    }

    static boolean hurt(SylarEntity s, LivingEntity target, float amount) {
        target.invulnerableTime = 0;
        return target.hurt(s.damageSources().mobAttack(s), amount);
    }

    static void push(Entity target, Vec3 velocity) {
        target.setDeltaMovement(velocity);
        target.hurtMarked = true;
        if (target instanceof ServerPlayer player) player.connection.send(new ClientboundSetEntityMotionPacket(player));
    }

    /** Who his powers hurt: players he can harm and anything that is not a monster. */
    static boolean foe(SylarEntity s, LivingEntity t) {
        return t != s && t.isAlive() && !t.isSpectator() && !(t instanceof Enemy)
                && !(t instanceof ServerPlayer p && (p.isCreative() || p.isSpectator()));
    }

    static List<LivingEntity> foes(SylarEntity s, Vec3 center, double radius) {
        return s.level().getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius),
                t -> foe(s, t) && t.distanceToSqr(center) <= radius * radius).stream().limit(16).toList();
    }

    static List<LivingEntity> cone(SylarEntity s, Vec3 from, Vec3 aim, double reach, double dot) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity t : foes(s, from, reach)) {
            Vec3 to = t.getBoundingBox().getCenter().subtract(from);
            if (to.normalize().dot(aim) >= dot && s.hasLineOfSight(t)) out.add(t);
        }
        return out;
    }

    /** The moves open to him: telekinesis always, plus every power he knows (and one he is borrowing). */
    static List<Move> available(Set<ResourceLocation> known) {
        return MOVES.stream().filter(m -> m.power().equals(TELEKINESIS) || known.contains(m.power())).toList();
    }
}
