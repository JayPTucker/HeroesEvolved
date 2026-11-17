package com.jayptucker.heroesevolved.gifted;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.registry.ModItems;
import com.jayptucker.heroesevolved.sounds.ModSounds;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything a gifted mob can do with its power, as real moves: a windup the player can read (a pose, a telegraph, a
 * sound), then what the power does, in that power's own colours. Two ordinary moves for every power and a signature for
 * Epic and Mythic mobs. Empathic Mimicry has none of its own: a mimic uses the moves of whatever power it copied.
 * <p>
 * Modelled on Sylar's ({@code story/sylar/SylarPowers}), but for any {@link Mob} and either side: an evil gift's foes are
 * players, villagers, golems and pets; a good gift's (a village guardian's) are monsters and evil gifts.
 */
public final class MobMoves {
    /** How the caster holds itself while winding up: an illager raises its arms for it ({@code IllagerCastPoseMixin}). */
    public enum Pose { NONE, POINT, CAST, SLAM }

    public interface Action { void run(GiftedMob g, ServerLevel level, LivingEntity target, float power); }

    /**
     * One move. {@code telegraph} runs when the windup starts and {@code pulse} every four ticks of it; {@code execute}
     * when it ends. Ranges are in blocks from the caster to its target.
     */
    public record Move(String id, ResourceLocation power, int windup, int cooldown, double minRange, double maxRange,
                       boolean needsSight, boolean signature, Pose pose, Action telegraph, Action pulse, Action execute) {}

    private static final Action NOTHING = (g, l, t, p) -> { };

    private static Move m(String id, ResourceLocation power, int windup, int cooldown, double min, double max, boolean sight,
                          Pose pose, Action telegraph, Action pulse, Action execute) {
        return new Move(id, power, windup, cooldown, min, max, sight, false, pose, telegraph, pulse, execute);
    }

    private static Move sig(String id, ResourceLocation power, int windup, int cooldown, double min, double max, boolean sight,
                            Pose pose, Action telegraph, Action pulse, Action execute) {
        return new Move(id, power, windup, cooldown, min, max, sight, true, pose, telegraph, pulse, execute);
    }

    private static final ResourceLocation TK = ModAbilities.TELEKINESIS_ID, ELECTRO = ModAbilities.ELECTROKINESIS_ID,
            CRYO = ModAbilities.CRYOKINESIS_ID, PYRO = ModAbilities.PYROKINESIS_ID, RAD = ModAbilities.RADIATION_ID,
            QUAKE = ModAbilities.QUAKE_ID, SPEED = ModAbilities.SUPERSPEED_ID, TIME = ModAbilities.TIME_MANIPULATION_ID,
            SONIC = ModAbilities.SONIC_RESONANCE_ID, FLIGHT = ModAbilities.FLIGHT_ID, MIND = ModAbilities.TELEPATHY_ID,
            FORCE = ModAbilities.INVISIBILITY_ID, DENSITY = ModAbilities.PHASING_ID, TECH = ModAbilities.TECHNOPATHY_ID,
            MATTER = ModAbilities.MATTER_ASSEMBLY_ID, REGEN = ModAbilities.REGENERATION_ID, HYDRO = ModAbilities.HYDROKINESIS_ID,
            VIBE = ModAbilities.VIBE_ID, SHIFT = ModAbilities.SHAPESHIFTING_ID;

    public static final List<Move> ALL = List.of(
            // ---- Telekinesis: an unseen hand that throws, holds and slams ----
            m("tk_throw", TK, 14, 120, 2, 16, true, Pose.POINT,
                    (g, l, t, p) -> sound(l, g.mob.position(), SoundEvents.ILLUSIONER_PREPARE_MIRROR, .8F, .6F),
                    (g, l, t, p) -> AbilityEffects.send(l, Kind.TK_GRIP, hand(g.mob), chest(t), .6),
                    (g, l, t, p) -> {
                        Vec3 away = flat(t.position().subtract(g.mob.position()));
                        hurt(g, t, 4 * p);
                        push(t, away.scale(1.1).add(0, .75, 0));
                        AbilityEffects.send(l, Kind.TK_LAUNCH, chest(t), chest(t).add(away.scale(4)), 1.2);
                        sound(l, t.position(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), .8F, .7F);
                    }),
            m("tk_pin", TK, 10, 300, 2, 14, true, Pose.CAST,
                    (g, l, t, p) -> sound(l, g.mob.position(), SoundEvents.ILLUSIONER_CAST_SPELL, .8F, .5F),
                    (g, l, t, p) -> AbilityEffects.send(l, Kind.TK_GRIP, hand(g.mob), chest(t), .9),
                    (g, l, t, p) -> { g.pin(t, 50); sound(l, t.position(), SoundEvents.CHAIN_PLACE, 1, .5F); }),
            sig("tk_storm", TK, 30, 420, 0, 10, false, Pose.CAST,
                    (g, l, t, p) -> sound(l, g.mob.position(), SoundEvents.ILLUSIONER_PREPARE_BLINDNESS, 1, .5F),
                    (g, l, t, p) -> AbilityEffects.send(l, Kind.TK_WELL, g.mob.position(), g.mob.position().add(0, 1, 0), 3),
                    (g, l, t, p) -> {
                        // Everything near is lifted, held a breath, and slammed down.
                        List<LivingEntity> lifted = foes(g, g.mob.position(), 9);
                        for (LivingEntity e : lifted) {
                            push(e, new Vec3(0, 1.2, 0));
                            AbilityEffects.send(l, Kind.TK_GATHER, chest(e), chest(e).add(0, 3, 0), 1);
                        }
                        sound(l, g.mob.position(), SoundEvents.BREEZE_JUMP, 1, .5F);
                        g.later(16, () -> {
                            for (LivingEntity e : lifted) if (e.isAlive()) {
                                push(e, new Vec3(0, -2.2, 0));
                                g.later(4, () -> { if (e.isAlive()) { hurt(g, e, 8 * p); AbilityEffects.send(l, Kind.TK_IMPACT, e.position(), e.position(), 1.4); } });
                            }
                            sound(l, g.mob.position(), SoundEvents.ANVIL_LAND, .8F, .6F);
                        });
                    }),

            // ---- Electrokinesis: lightning that seeks bodies, and strikes from a clear sky ----
            m("chain_lightning", ELECTRO, 12, 110, 2, 18, true, Pose.POINT,
                    (g, l, t, p) -> sound(l, g.mob.position(), SoundEvents.BEACON_ACTIVATE, .5F, 2),
                    (g, l, t, p) -> AbilityEffects.send(l, Kind.ELECTRIC_TELEGRAPH, hand(g.mob), hand(g.mob).add(0, 1, 0), 1),
                    (g, l, t, p) -> {
                        AbilityEffects.send(l, Kind.ELECTRIC_ARC, hand(g.mob), chest(t), 1.2);
                        hurt(g, t, 6 * p);
                        t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 2));
                        LivingEntity last = t;
                        int leaps = 0;
                        for (LivingEntity next : foes(g, t.position(), 5)) {
                            if (next == t || ++leaps > 2) continue;
                            AbilityEffects.send(l, Kind.ELECTRIC_ARC, chest(last), chest(next), .9);
                            hurt(g, next, 4 * p);
                            last = next;
                        }
                        sound(l, t.position(), SoundEvents.LIGHTNING_BOLT_IMPACT, .6F, 1.6F);
                    }),
            m("thunderstrike", ELECTRO, 30, 240, 4, 24, true, Pose.CAST,
                    (g, l, t, p) -> { g.mark(t.position()); sound(l, t.position(), SoundEvents.BEACON_POWER_SELECT, .6F, 1.8F); },
                    (g, l, t, p) -> AbilityEffects.send(l, Kind.ELECTRIC_TELEGRAPH, g.mark(), g.mark().add(0, 1, 0), 2.5),
                    // It lands where you stood when the hand went up: step out of the mark.
                    (g, l, t, p) -> strike(g, l, g.mark(), 9 * p)),
            sig("storm_surge", ELECTRO, 26, 380, 3, 24, true, Pose.CAST,
                    (g, l, t, p) -> { g.mark(t.position()); sound(l, t.position(), SoundEvents.BEACON_ACTIVATE, .8F, 1.4F); },
                    (g, l, t, p) -> { for (Vec3 at : surge(g)) AbilityEffects.send(l, Kind.ELECTRIC_TELEGRAPH, at, at.add(0, 1, 0), 2.2); },
                    (g, l, t, p) -> {
                        List<Vec3> points = surge(g);
                        for (int i = 0; i < points.size(); i++) {
                            Vec3 at = points.get(i);
                            g.later(i * 6, () -> strike(g, l, at, 8 * p));
                        }
                    }),

            // ---- Cryokinesis: lances, a cold snap, a whiteout ----
            m("ice_lance", CRYO, 10, 110, 3, 20, true, Pose.POINT,
                    (g, l, t, p) -> sound(l, g.mob.position(), SoundEvents.GLASS_HIT, .8F, .6F),
                    (g, l, t, p) -> AbilityEffects.send(l, Kind.CRYO_CHILL, hand(g.mob), hand(g.mob).add(0, .5, 0), .6),
                    (g, l, t, p) -> {
                        AbilityEffects.send(l, Kind.CRYO_BOLT, hand(g.mob), t.getEyePosition(), 1.2);
                        hurt(g, t, 6 * p);
                        com.jayptucker.heroesevolved.cryo.CryoService.freezeSolid(t, 30, null);
                        sound(l, t.position(), SoundEvents.GLASS_BREAK, .8F, 1.4F);
                    }),
            m("cold_snap", CRYO, 16, 220, 0, 6, false, Pose.SLAM,
                    (g, l, t, p) -> sound(l, g.mob.position(), SoundEvents.POWDER_SNOW_STEP, 1, .5F),
                    (g, l, t, p) -> AbilityEffects.send(l, Kind.CRYO_CHILL, g.mob.position(), g.mob.position().add(0, 1, 0), 2),
                    (g, l, t, p) -> {
                        AbilityEffects.send(l, Kind.CRYO_CHILL, g.mob.position(), g.mob.position().add(0, 1, 0), 6);
                        for (LivingEntity e : foes(g, g.mob.position(), 6)) {
                            hurt(g, e, 4 * p);
                            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
                            e.setTicksFrozen(Math.min(e.getTicksRequiredToFreeze(), e.getTicksFrozen() + 80));
                        }
                    }),
            sig("blizzard", CRYO, 30, 380, 0, 9, false, Pose.CAST,
                    (g, l, t, p) -> sound(l, g.mob.position(), SoundEvents.POWDER_SNOW_STEP, 1.2F, .4F),
                    (g, l, t, p) -> AbilityEffects.send(l, Kind.CRYO_CHILL, g.mob.position(), g.mob.position().add(0, 1, 0), 3),
                    (g, l, t, p) -> {
                        AbilityEffects.send(l, Kind.CRYO_BLIZZARD, g.mob.position(), g.mob.position().add(0, 1, 0), 8);
                        for (LivingEntity e : foes(g, g.mob.position(), 9)) {
                            hurt(g, e, 5 * p);
                            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 2));
                            e.setTicksFrozen(Math.min(e.getTicksRequiredToFreeze() + 40, e.getTicksFrozen() + 160));
                            com.jayptucker.heroesevolved.cryo.CryoService.chill(e, 120);
                        }
                        sound(l, g.mob.position(), SoundEvents.GLASS_BREAK, 1, .5F);
                    }),

            // ---- Pyrokinesis: a stream, a bolt, a marked firestorm ----
            m("fire_stream", PYRO, 10, 140, 1, 10, true, Pose.CAST,
                    (g, l, t, p) -> sound(l, g.mob.position(), SoundEvents.FIRECHARGE_USE, .8F, .6F),
                    (g, l, t, p) -> AbilityEffects.send(l, Kind.FIRE_STREAM, hand(g.mob), hand(g.mob).add(g.mob.getLookAngle()), .5),
                    (g, l, t, p) -> {
                        Vec3 from = hand(g.mob), aim = t.getEyePosition().subtract(from).normalize();
                        AbilityEffects.send(l, Kind.FIRE_STREAM, from, from.add(aim.scale(10)), 1.3);
                        for (LivingEntity e : cone(g, from, aim, 10, .85)) { hurt(g, e, 5 * p); e.igniteForSeconds(4); }
                        sound(l, from, SoundEvents.BLAZE_SHOOT, 1, .8F);
                    }),
            m("fire_bolt", PYRO, 10, 90, 3, 20, true, Pose.POINT,
                    (g, l, t, p) -> sound(l, g.mob.position(), SoundEvents.FIRECHARGE_USE, .7F, 1.2F),
                    NOTHING,
                    (g, l, t, p) -> {
                        AbilityEffects.send(l, Kind.FIRE_BOLT, hand(g.mob), chest(t), 1.2);
                        hurt(g, t, 6 * p);
                        t.igniteForSeconds(5);
                        sound(l, t.position(), SoundEvents.BLAZE_SHOOT, .8F, 1.3F);
                    }),
            sig("firestorm", PYRO, 26, 360, 3, 20, true, Pose.CAST,
                    (g, l, t, p) -> { g.mark(t.position()); sound(l, t.position(), SoundEvents.BLAZE_AMBIENT, 1, .6F); },
                    (g, l, t, p) -> AbilityEffects.send(l, Kind.FIRE_RING, g.mark(), g.mark().add(0, 1, 0), 4),
                    (g, l, t, p) -> {
                        Vec3 at = g.mark();
                        AbilityEffects.send(l, Kind.FIRE_NOVA, at, at.add(0, 1, 0), 4.5);
                        for (LivingEntity e : foes(g, at, 4.5)) { hurt(g, e, 9 * p); e.igniteForSeconds(6); }
                        sound(l, at, SoundEvents.GENERIC_EXPLODE.value(), .8F, 1.2F);
                    }),

            // ---- Induced Radioactivity: heat, a gamma lance, and a meltdown pulse you must interrupt ----
            m("radiant_heat", RAD, 18, 200, 0, 10, true, Pose.CAST,
                    (g, l, t, p) -> sound(l, g.mob.position(), SoundEvents.BEACON_POWER_SELECT, .6F, .5F),
                    (g, l, t, p) -> AbilityEffects.send(l, Kind.RADIATION_PULSE, g.mob.position(), g.mob.getEyePosition(), 1.5),
                    (g, l, t, p) -> {
                        Vec3 from = hand(g.mob), aim = t.getEyePosition().subtract(from).normalize();
                        AbilityEffects.send(l, Kind.RADIATION_CONE, from, from.add(aim.scale(10)), 1.4);
                        for (LivingEntity e : cone(g, from, aim, 10, .75)) {
                            hurt(g, e, 7 * p);
                            e.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 0));
                            e.igniteForSeconds(2);
                        }
                    }),
            m("gamma_lance", RAD, 14, 140, 3, 22, true, Pose.POINT,
                    (g, l, t, p) -> sound(l, g.mob.position(), SoundEvents.BEACON_POWER_SELECT, .6F, 1.4F),
                    (g, l, t, p) -> AbilityEffects.send(l, Kind.RADIATION_PULSE, hand(g.mob), hand(g.mob).add(0, .3, 0), .5),
                    (g, l, t, p) -> {
                        AbilityEffects.send(l, Kind.RADIATION_BEAM, hand(g.mob), t.getEyePosition(), 1.1);
                        hurt(g, t, 7 * p);
                        com.jayptucker.heroesevolved.radiation.RadiationService.irradiate(t, 120);
                    }),
            sig("meltdown_pulse", RAD, 50, 500, 0, 12, false, Pose.CAST,
                    (g, l, t, p) -> {
                        sound(l, g.mob.position(), SoundEvents.BEACON_ACTIVATE, 1, .5F);
                        for (ServerPlayer near : g.near(24))
                            near.displayClientMessage(Component.literal(g.gift.name() + " is going critical! Hit them, or get clear!")
                                    .withStyle(ChatFormatting.GOLD), true);
                    },
                    (g, l, t, p) -> {
                        AbilityEffects.send(l, Kind.NUCLEAR_CHARGE, g.mob.position(), g.mob.getEyePosition(), 1.5);
                        sound(l, g.mob.position(), SoundEvents.BEACON_AMBIENT, 1.2F, .6F + g.castTicks / 50F);
                    },
                    (g, l, t, p) -> {
                        Vec3 at = g.mob.position();
                        AbilityEffects.send(l, Kind.NUCLEAR_SHOCKWAVE, at, at.add(0, 1, 0), 10);
                        for (LivingEntity e : foes(g, at, 10)) {
                            double d = e.position().distanceTo(at);
                            hurt(g, e, (float) (14 * p * (1 - d / 13)));
                            com.jayptucker.heroesevolved.radiation.RadiationService.irradiate(e, 200);
                            push(e, flat(e.position().subtract(at)).scale(1.2).add(0, .4, 0));
                        }
                        CameraShakePayload.sendAround(l, at, 40, 1.2F, 20);
                        sound(l, at, SoundEvents.GENERIC_EXPLODE.value(), 1.4F, .6F);
                        // It burns itself too.
                        g.mob.hurt(g.mob.damageSources().magic(), 4);
                    }),

            // ---- Quake: a palm blast, a ground pound, a fault line ----
            m("ground_pound", QUAKE, 20, 220, 0, 7, false, Pose.SLAM,
                    (g, l, t, p) -> sound(l, g.mob.position(), SoundEvents.WARDEN_DIG, .8F, 1.2F),
                    (g, l, t, p) -> AbilityEffects.send(l, Kind.QUAKE_CHANNEL, g.mob.position(), g.mob.position().add(0, .5, 0), 1),
                    (g, l, t, p) -> {
                        Vec3 at = g.mob.position();
                        AbilityEffects.send(l, Kind.QUAKE_IMPACT, at, at, 7);
                        for (LivingEntity e : foes(g, at, 7)) { hurt(g, e, 7 * p); push(e, flat(e.position().subtract(at)).scale(.6).add(0, .7, 0)); }
                        AbilityEffects.send(l, Kind.QUAKE_ERUPT, at, at.add(0, 1, 0), 2.5);
                        sound(l, at, SoundEvents.GENERIC_EXPLODE.value(), .8F, .6F);
                    }),
            m("shockwave", QUAKE, 10, 140, 3, 14, true, Pose.POINT,
                    (g, l, t, p) -> sound(l, g.mob.position(), SoundEvents.WARDEN_SONIC_CHARGE, .7F, 1.6F),
                    (g, l, t, p) -> AbilityEffects.send(l, Kind.QUAKE_RESONATE, hand(g.mob), hand(g.mob), .4),
                    (g, l, t, p) -> {
                        AbilityEffects.send(l, Kind.QUAKE_RINGS, hand(g.mob), chest(t), 1.4);
                        hurt(g, t, 6 * p);
                        push(t, flat(t.position().subtract(g.mob.position())).scale(1.6).add(0, .45, 0));
                        sound(l, g.mob.position(), SoundEvents.WARDEN_SONIC_BOOM, .6F, 1.6F);
                    }),
            sig("fault_line", QUAKE, 22, 360, 3, 16, true, Pose.SLAM,
                    (g, l, t, p) -> { g.mark(t.position()); sound(l, g.mob.position(), SoundEvents.WARDEN_DIG, 1, .6F); },
                    (g, l, t, p) -> AbilityEffects.send(l, Kind.QUAKE_CHANNEL, g.mob.position(), g.mob.position().add(0, .5, 0), 1.5),
                    (g, l, t, p) -> {
                        Vec3 from = g.mob.position(), end = from.add(flat(g.mark().subtract(from)).scale(16));
                        AbilityEffects.send(l, Kind.QUAKE_FISSURE, from, end, 1.6);
                        for (LivingEntity e : line(g, from, end, 1.8)) { hurt(g, e, 9 * p); push(e, new Vec3(0, 1.1, 0)); }
                        CameraShakePayload.sendAround(l, from, 24, .8F, 12);
                        sound(l, from, SoundEvents.GENERIC_EXPLODE.value(), 1, .5F);
                    }),

            // ---- Superspeed: a blitz of blows, a thrown bolt, a whirlwind ----
            m("blitz", SPEED, 6, 150, 4, 20, true, Pose.NONE,
                    (g, l, t, p) -> sound(l, g.mob.position(), SoundEvents.PLAYER_ATTACK_SWEEP, .8F, 1.6F),
                    NOTHING,
                    (g, l, t, p) -> {
                        Vec3 from = g.mob.position(), delta = t.position().subtract(from);
                        Vec3 end = com.jayptucker.heroesevolved.speed.SpeedService.swept(g.mob, delta.normalize().scale(Math.max(0, delta.length() - 1.5)));
                        if (end.distanceToSqr(t.position()) > 9) return;
                        g.mob.teleportTo(end.x, end.y, end.z);
                        AbilityEffects.send(l, Kind.SPEED_ZIP, from, end, 1.2);
                        g.flurry(t, 3, 2.5F * p);
                    }),
            m("lightning_throw", SPEED, 10, 140, 4, 20, true, Pose.POINT,
                    (g, l, t, p) -> { sound(l, g.mob.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1, 1.8F); AbilityEffects.send(l, Kind.SPEED_AURA, g.mob.position(), g.mob.getEyePosition(), 1); },
                    NOTHING,
                    (g, l, t, p) -> {
                        AbilityEffects.send(l, Kind.SPEED_BOLT, hand(g.mob), chest(t), 1.2);
                        hurt(g, t, 6 * p);
                        t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1));
                        sound(l, t.position(), SoundEvents.LIGHTNING_BOLT_IMPACT, .5F, 2);
                    }),
            sig("whirlwind", SPEED, 20, 380, 0, 10, false, Pose.NONE,
                    (g, l, t, p) -> AbilityEffects.send(l, Kind.SPEED_VORTEX, g.mob.position(), g.mob.position().add(0, 1, 0), 2),
                    (g, l, t, p) -> AbilityEffects.send(l, Kind.SPEED_VORTEX, g.mob.position(), g.mob.position().add(0, 1, 0), 3),
                    (g, l, t, p) -> {
                        Vec3 at = t.position();
                        AbilityEffects.send(l, Kind.SPEED_CYCLONE, at, at.add(0, 1, 0), 5);
                        for (int i = 0; i < 3; i++) g.later(i * 4, () -> {
                            for (LivingEntity e : foes(g, at, 7)) {
                                push(e, at.subtract(e.position()).multiply(1, 0, 1).scale(.25).add(0, .25, 0));
                                hurt(g, e, 3 * p);
                            }
                        });
                        sound(l, at, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 1, .6F);
                    }),

            // ---- Space-Time: a fold behind you, slowed time, a stopped moment ----
            m("time_fold", TIME, 10, 260, 3, 24, true, Pose.CAST,
                    (g, l, t, p) -> sound(l, g.mob.position(), ModSounds.SYLAR_WATCH.get(), 1, 1.4F),
                    (g, l, t, p) -> AbilityEffects.send(l, Kind.TEMPORAL_FIELD, g.mob.position(), g.mob.getEyePosition(), 1.2),
                    (g, l, t, p) -> {
                        Vec3 behind = t.position().subtract(flat(t.getLookAngle()).scale(1.8));
                        if (!free(g.mob, behind)) return;
                        Vec3 from = g.mob.position();
                        g.mob.teleportTo(behind.x, behind.y, behind.z);
                        AbilityEffects.send(l, Kind.TIME_FOLD, from, behind, 1);
                        t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2));
                        hurt(g, t, 5 * p);
                    }),
            m("slow_time", TIME, 12, 280, 2, 16, true, Pose.POINT,
                    (g, l, t, p) -> sound(l, t.position(), ModSounds.SYLAR_WATCH.get(), 1, .6F),
                    NOTHING,
                    (g, l, t, p) -> {
                        AbilityEffects.send(l, Kind.TIME_SLOW_PULSE, t.position(), t.position().add(0, 1, 0), 3);
                        t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 3));
                        t.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 80, 2));
                    }),
            sig("still_moment", TIME, 24, 420, 2, 18, true, Pose.CAST,
                    (g, l, t, p) -> sound(l, t.position(), ModSounds.SYLAR_WATCH.get(), 1.2F, .4F),
                    (g, l, t, p) -> AbilityEffects.send(l, Kind.TEMPORAL_FIELD, t.position(), t.getEyePosition(), 1.4),
                    (g, l, t, p) -> {
                        // Stopped: held still for two and a half seconds while it walks round and strikes twice.
                        AbilityEffects.send(l, Kind.TIME_STOP_PULSE, t.position(), t.position().add(0, 1, 0), 3);
                        g.hold(t, 50);
                        t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 9, false, false, true));
                        g.later(12, () -> {
                            Vec3 behind = t.position().subtract(flat(t.getLookAngle()).scale(1.6));
                            if (free(g.mob, behind)) {
                                AbilityEffects.send(l, Kind.TIME_FOLD, g.mob.position(), behind, 1);
                                g.mob.teleportTo(behind.x, behind.y, behind.z);
                            }
                            if (t.isAlive()) hurt(g, t, 5 * p);
                        });
                        g.later(24, () -> { if (t.isAlive()) hurt(g, t, 5 * p); });
                    }),

            // ---- Sonic Resonance: a scream, a burst, a crescendo ----
            m("scream", SONIC, 10, 170, 2, 14, true, Pose.NONE,
                    (g, l, t, p) -> sound(l, g.mob.position(), SoundEvents.WARDEN_SONIC_CHARGE, .6F, 1.4F),
                    NOTHING,
                    (g, l, t, p) -> {
                        Vec3 from = g.mob.getEyePosition(), aim = t.getEyePosition().subtract(from).normalize();
                        AbilityEffects.send(l, Kind.SONIC_WAVE, from, from.add(aim.scale(14)), 1.2);
                        for (LivingEntity e : cone(g, from, aim, 14, .9)) { hurt(g, e, 6 * p); push(e, aim.add(0, .2, 0)); }
                        sound(l, from, ModSounds.SONIC_CRY.get(), 1, .8F);
                    }),
            m("sonic_burst", SONIC, 12, 160, 0, 6, false, Pose.NONE,
                    (g, l, t, p) -> sound(l, g.mob.position(), SoundEvents.WARDEN_SONIC_CHARGE, .5F, 1.8F),
                    NOTHING,
                    (g, l, t, p) -> {
                        Vec3 at = g.mob.position();
                        AbilityEffects.send(l, Kind.SONIC_BURST, at, at.add(0, 1, 0), 5);
                        for (LivingEntity e : foes(g, at, 5)) {
                            hurt(g, e, 5 * p);
                            push(e, flat(e.position().subtract(at)).scale(1.2).add(0, .3, 0));
                            e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 60, 0));
                        }
                        sound(l, at, ModSounds.SONIC_CRY.get(), 1, 1.2F);
                    }),
            sig("crescendo", SONIC, 36, 400, 2, 18, true, Pose.NONE,
                    (g, l, t, p) -> AbilityEffects.send(l, Kind.SONIC_CRESCENDO_CHARGE, g.mob.position(), g.mob.getEyePosition(), 1),
                    (g, l, t, p) -> AbilityEffects.send(l, Kind.SONIC_CRESCENDO_CHARGE, g.mob.position(), g.mob.getEyePosition(), .6),
                    (g, l, t, p) -> {
                        Vec3 from = g.mob.getEyePosition(), aim = t.getEyePosition().subtract(from).normalize();
                        AbilityEffects.send(l, Kind.SONIC_CRESCENDO, from, from.add(aim.scale(18)), 1.6);
                        for (LivingEntity e : cone(g, from, aim, 18, .82)) {
                            hurt(g, e, 11 * p);
                            push(e, aim.scale(1.8).add(0, .3, 0));
                            e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0));
                        }
                        sound(l, from, ModSounds.SONIC_CRY.get(), 1.5F, .6F);
                    }),

            // ---- Flight: a dive, a gale, a skyburst ----
            m("dive", FLIGHT, 20, 260, 4, 20, true, Pose.CAST,
                    (g, l, t, p) -> {
                        push(g.mob, new Vec3(0, 1.1, 0));
                        AbilityEffects.send(l, Kind.FLIGHT_BURST, g.mob.position(), g.mob.position().add(0, 1, 0), 1.6);
                        sound(l, g.mob.position(), SoundEvents.BREEZE_JUMP, 1, .6F);
                    },
                    (g, l, t, p) -> g.mob.setDeltaMovement(g.mob.getDeltaMovement().multiply(.6, .3, .6)),
                    (g, l, t, p) -> {
                        Vec3 at = free(g.mob, t.position()) ? t.position() : t.position().add(flat(g.mob.position().subtract(t.position())).scale(1.2));
                        Vec3 from = g.mob.position();
                        g.mob.teleportTo(at.x, at.y, at.z);
                        AbilityEffects.send(l, Kind.SPEED_ZIP, from, at, 1.4);
                        AbilityEffects.send(l, Kind.QUAKE_IMPACT, at, at, 3);
                        for (LivingEntity e : foes(g, at, 3)) { hurt(g, e, 7 * p); push(e, flat(e.position().subtract(at)).scale(.7).add(0, .5, 0)); }
                        sound(l, at, SoundEvents.MACE_SMASH_GROUND_HEAVY, 1, .8F);
                    }),
            m("gale", FLIGHT, 10, 150, 0, 10, true, Pose.POINT,
                    (g, l, t, p) -> sound(l, g.mob.position(), SoundEvents.BREEZE_INHALE, 1, .8F),
                    NOTHING,
                    (g, l, t, p) -> {
                        Vec3 from = g.mob.getEyePosition(), aim = t.getEyePosition().subtract(from).normalize();
                        AbilityEffects.send(l, Kind.FLIGHT_PRESSURE_WAVE, from, from.add(aim.scale(10)), 1.2);
                        for (LivingEntity e : cone(g, from, aim, 10, .7)) { push(e, aim.scale(1.8).add(0, .5, 0)); hurt(g, e, 3 * p); }
                        sound(l, from, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 1, .9F);
                    }),
            sig("skyburst", FLIGHT, 22, 380, 4, 20, true, Pose.NONE,
                    (g, l, t, p) -> { AbilityEffects.send(l, Kind.FLIGHT_CHARGE, g.mob.position(), g.mob.position().add(0, 1, 0), 1.4); push(g.mob, new Vec3(0, .6, 0)); },
                    (g, l, t, p) -> { AbilityEffects.send(l, Kind.FLIGHT_CHARGE, g.mob.position(), g.mob.position().add(0, 1, 0), 1); g.mob.setDeltaMovement(g.mob.getDeltaMovement().multiply(.5, .2, .5)); },
                    (g, l, t, p) -> {
                        Vec3 from = g.mob.position(), dir = chest(t).subtract(chest(g.mob)).normalize();
                        double reach = Math.min(g.mob.distanceTo(t) + 4, 22);
                        Vec3 end = com.jayptucker.heroesevolved.speed.SpeedService.swept(g.mob, dir.scale(reach));
                        g.mob.teleportTo(end.x, end.y, end.z);
                        AbilityEffects.send(l, Kind.FLIGHT_SKYBURST, from, end, 1.4);
                        for (LivingEntity e : line(g, from, end, 2)) { hurt(g, e, 9 * p); push(e, dir.scale(1.4).add(0, .4, 0)); }
                        sound(l, end, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 1.4F, .5F);
                    }),

            // ---- Telepathy: a turned crowd, a spike, a crushing mind ----
            m("dominate", MIND, 14, 320, 2, 20, true, Pose.POINT,
                    (g, l, t, p) -> sound(l, t.position(), SoundEvents.WARDEN_NEARBY_CLOSER, .7F, 1.2F),
                    (g, l, t, p) -> AbilityEffects.send(l, Kind.PSYCHIC_TETHER, g.mob.getEyePosition(), t.getEyePosition(), .5),
                    (g, l, t, p) -> {
                        AbilityEffects.send(l, Kind.MIND_PULSE, t.position(), t.getEyePosition(), 1.5);
                        if (g.evil()) {
                            // Every monster near turns on you.
                            t.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0));
                            t.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 60, 0));
                            int turned = 0;
                            for (Mob mob : l.getEntitiesOfClass(Mob.class, t.getBoundingBox().inflate(16), m -> m instanceof Enemy && m != g.mob)) {
                                if (++turned > 8) break;
                                mob.setTarget(t);
                            }
                        } else if (t instanceof Mob monster) {
                            // A guardian turns the monster on its own kind.
                            LivingEntity other = l.getEntitiesOfClass(Mob.class, t.getBoundingBox().inflate(12), m -> m != t && m instanceof Enemy)
                                    .stream().min(java.util.Comparator.comparingDouble(m -> m.distanceToSqr(t))).orElse(null);
                            monster.setTarget(other);
                            if (other instanceof Mob o) o.setTarget(monster);
                        }
                    }),
            m("mind_spike", MIND, 12, 150, 2, 20, true, Pose.POINT,
                    (g, l, t, p) -> sound(l, g.mob.position(), SoundEvents.ILLUSIONER_PREPARE_BLINDNESS, .8F, 1.4F),
                    (g, l, t, p) -> AbilityEffects.send(l, Kind.PSYCHIC_TETHER, g.mob.getEyePosition(), t.getEyePosition(), .4),
                    (g, l, t, p) -> {
                        AbilityEffects.send(l, Kind.MIND_PULSE, t.position(), t.getEyePosition(), 1);
                        magic(g, t, 5 * p);
                        t.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 0));
                        t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
                    }),
            sig("mind_crush", MIND, 30, 400, 2, 20, true, Pose.CAST,
                    (g, l, t, p) -> sound(l, t.position(), SoundEvents.ELDER_GUARDIAN_CURSE, .6F, 1.2F),
                    (g, l, t, p) -> AbilityEffects.send(l, Kind.PSYCHIC_TETHER, g.mob.getEyePosition(), t.getEyePosition(), .7),
                    (g, l, t, p) -> {
                        t.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0));
                        t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
                        for (int i = 0; i < 3; i++) g.later(i * 10, () -> {
                            if (!t.isAlive()) return;
                            AbilityEffects.send(l, Kind.MIND_PULSE, t.position(), t.getEyePosition(), 1.6);
                            magic(g, t, 4 * p);
                        });
                    }),

            // ---- Invisibility (force fields): a vanishing ambush, a push, a bubble ----
            m("vanish", FORCE, 40, 320, 2, 20, false, Pose.NONE,
                    (g, l, t, p) -> {
                        g.mob.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 42, 0, false, false));
                        AbilityEffects.send(l, Kind.OPTIC_FADE, g.mob.position(), g.mob.getEyePosition(), 1.2);
                        sound(l, g.mob.position(), SoundEvents.ILLUSIONER_MIRROR_MOVE, 1, .6F);
                    },
                    NOTHING,
                    (g, l, t, p) -> {
                        Vec3 behind = t.position().subtract(flat(t.getLookAngle()).scale(1.6));
                        if (free(g.mob, behind)) g.mob.teleportTo(behind.x, behind.y, behind.z);
                        g.mob.removeEffect(MobEffects.INVISIBILITY);
                        AbilityEffects.send(l, Kind.OPTIC_REFORM, g.mob.position(), g.mob.getEyePosition(), 1);
                        hurt(g, t, 8 * p);
                        sound(l, t.position(), SoundEvents.PLAYER_ATTACK_CRIT, 1, .7F);
                    }),
            m("force_push", FORCE, 10, 150, 0, 8, true, Pose.POINT,
                    (g, l, t, p) -> sound(l, g.mob.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1, .6F),
                    (g, l, t, p) -> AbilityEffects.send(l, Kind.OPTIC_RIPPLE, hand(g.mob), hand(g.mob).add(0, .2, 0), .6),
                    (g, l, t, p) -> {
                        Vec3 from = hand(g.mob), aim = chest(t).subtract(from).normalize();
                        AbilityEffects.send(l, Kind.PSYCHIC_PUSH, from, from.add(aim.scale(8)), 1.2);
                        for (LivingEntity e : cone(g, from, aim, 8, .65)) { hurt(g, e, 4 * p); push(e, aim.scale(1.7).add(0, .35, 0)); }
                        sound(l, from, SoundEvents.AMETHYST_BLOCK_BREAK, 1, .5F);
                    }),
            sig("force_bubble", FORCE, 20, 400, 2, 14, true, Pose.CAST,
                    (g, l, t, p) -> sound(l, t.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1, .6F),
                    (g, l, t, p) -> AbilityEffects.send(l, Kind.PSYCHIC_BIND, chest(t), chest(t), .8),
                    (g, l, t, p) -> {
                        // Shut in a bubble for three seconds, then crushed.
                        g.hold(t, 60);
                        for (int i = 0; i < 6; i++) g.later(i * 10, () -> { if (t.isAlive()) AbilityEffects.send(l, Kind.PSYCHIC_BIND, chest(t), chest(t), 1.2); });
                        g.later(60, () -> {
                            if (!t.isAlive()) return;
                            AbilityEffects.send(l, Kind.PSYCHIC_IMPACT, chest(t), chest(t).add(0, .5, 0), 1.6);
                            hurt(g, t, 7 * p);
                            sound(l, t.position(), SoundEvents.AMETHYST_CLUSTER_BREAK, 1.2F, .6F);
                        });
                    }),

            // ---- Density: a hand through armour, the floor going soft, an ambush from below ----
            m("reach_through", DENSITY, 8, 150, 0, 3.5, false, Pose.POINT,
                    (g, l, t, p) -> sound(l, g.mob.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, .8F, .5F),
                    (g, l, t, p) -> AbilityEffects.send(l, Kind.DENSITY_GHOST, g.mob.position(), g.mob.position(), .5),
                    (g, l, t, p) -> {
                        if (magic(g, t, 7 * p)) com.jayptucker.heroesevolved.phasing.DensityService.seal(t, 80);
                        AbilityEffects.send(l, Kind.DENSITY_REACH, hand(g.mob), chest(t), 1);
                        sound(l, t.position(), SoundEvents.WARDEN_HEARTBEAT, 1, 1.4F);
                    }),
            m("embed", DENSITY, 16, 320, 0, 8, true, Pose.SLAM,
                    (g, l, t, p) -> sound(l, t.position(), SoundEvents.DEEPSLATE_BREAK, .8F, .5F),
                    (g, l, t, p) -> AbilityEffects.send(l, Kind.DENSITY_RIPPLE, t.position().add(0, .05, 0), t.position().add(0, 1, 0), 1.2),
                    (g, l, t, p) -> com.jayptucker.heroesevolved.phasing.DensityService.embed(t, g.mob, 50, false, .4)),
            sig("phase_ambush", DENSITY, 20, 380, 2, 18, false, Pose.SLAM,
                    (g, l, t, p) -> {
                        g.mob.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 22, 0, false, false));
                        AbilityEffects.send(l, Kind.DENSITY_SHIFT, g.mob.position(), g.mob.position(), 1.2);
                        sound(l, g.mob.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, .8F, .4F);
                    },
                    // The ground ripples under its target: the tell.
                    (g, l, t, p) -> AbilityEffects.send(l, Kind.DENSITY_RIPPLE, t.position().add(0, .05, 0), t.position().add(0, 1, 0), 1.6),
                    (g, l, t, p) -> {
                        g.mob.removeEffect(MobEffects.INVISIBILITY);
                        Vec3 at = free(g.mob, t.position()) ? t.position() : t.position().add(flat(g.mob.position().subtract(t.position())).scale(1.1));
                        g.mob.teleportTo(at.x, at.y, at.z);
                        AbilityEffects.send(l, Kind.DENSITY_SURFACE, at, at.add(0, 1, 0), 1.4);
                        hurt(g, t, 9 * p);
                        push(t, new Vec3(0, 1.2, 0));
                        sound(l, at, SoundEvents.DEEPSLATE_BREAK, 1.2F, .7F);
                    }),

            // ---- Technopathy: stolen arrows, a pulse that jams weapons, an overload cage ----
            m("stolen_trajectory", TECH, 24, 240, 0, 18, true, Pose.CAST,
                    (g, l, t, p) -> sound(l, g.mob.position(), SoundEvents.COMPARATOR_CLICK, 1.2F, .6F),
                    (g, l, t, p) -> AbilityEffects.send(l, Kind.TECH_CAGE, g.mob.position(), chest(g.mob), 2),
                    (g, l, t, p) -> {
                        // The warning gives a bow user time to stop firing into the field.
                        for (var shot : l.getEntitiesOfClass(net.minecraft.world.entity.projectile.AbstractArrow.class, g.mob.getBoundingBox().inflate(7),
                                arrow -> arrow.getOwner() instanceof LivingEntity shooter && foe(g, shooter) && !arrow.onGround()
                                        && arrow.getDeltaMovement().lengthSqr() > .01).stream().limit(6).toList()) {
                            AbilityEffects.send(l, Kind.TECH_LINK, hand(g.mob), shot.position(), 1);
                            shot.setOwner(g.mob);
                            shot.setDeltaMovement(chest(t).subtract(shot.position()).normalize().scale(1.5));
                            shot.hurtMarked = true;
                        }
                        AbilityEffects.send(l, Kind.TECH_LINK, hand(g.mob), chest(t), 1);
                        hurt(g, t, 3 * p);
                        sound(l, g.mob.position(), SoundEvents.IRON_TRAPDOOR_CLOSE, .8F, 1.4F);
                    }),
            m("tech_pulse", TECH, 12, 180, 0, 10, false, Pose.CAST,
                    (g, l, t, p) -> sound(l, g.mob.position(), SoundEvents.COMPARATOR_CLICK, 1, 1.6F),
                    NOTHING,
                    (g, l, t, p) -> {
                        AbilityEffects.send(l, Kind.TECH_PULSE, g.mob.position(), g.mob.position().add(0, 1, 0), 8);
                        for (LivingEntity e : foes(g, g.mob.position(), 8)) {
                            hurt(g, e, 3 * p);
                            e.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 100, 1));
                            // Whatever you are holding jams for three seconds.
                            if (e instanceof Player player && !player.getMainHandItem().isEmpty())
                                player.getCooldowns().addCooldown(player.getMainHandItem().getItem(), 60);
                        }
                        sound(l, g.mob.position(), SoundEvents.IRON_TRAPDOOR_CLOSE, 1, .6F);
                    }),
            sig("overload", TECH, 26, 400, 2, 16, true, Pose.CAST,
                    (g, l, t, p) -> { g.mark(t.position()); sound(l, t.position(), SoundEvents.IRON_TRAPDOOR_CLOSE, 1, .5F); },
                    (g, l, t, p) -> AbilityEffects.send(l, Kind.TECH_CAGE, g.mark(), g.mark().add(0, 1, 0), 2),
                    (g, l, t, p) -> {
                        Vec3 at = g.mark();
                        for (int i = 0; i < 5; i++) g.later(i * 10, () -> {
                            AbilityEffects.send(l, Kind.TECH_CAGE, at, at.add(0, 1, 0), 2.6);
                            for (LivingEntity e : foes(g, at, 3.2)) {
                                AbilityEffects.send(l, Kind.TECH_LINK, at.add(0, 2.5, 0), chest(e), 1);
                                hurt(g, e, 3 * p);
                                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 1));
                            }
                            sound(l, at, SoundEvents.COMPARATOR_CLICK, 1, 2);
                        });
                    }),

            // ---- Matter Assembly: a crossfire, a shield, a storm of shards ----
            m("assembled_crossfire", MATTER, 30, 220, 3, 18, true, Pose.CAST,
                    (g, l, t, p) -> { g.mark(t.position()); sound(l, g.mob.position(), SoundEvents.DEEPSLATE_PLACE, 1, .7F); },
                    (g, l, t, p) -> AbilityEffects.send(l, Kind.MATTER_WEAVE, hand(g.mob), g.mark().add(0, 1, 0), 1.1),
                    (g, l, t, p) -> {
                        // The marked spot is fixed at the start: moving, or cover, beats the fan.
                        Vec3 at = g.mark().add(0, 1, 0);
                        for (int i = -1; i <= 1; i++) {
                            Vec3 end = at.add(flat(at.subtract(g.mob.position())).cross(new Vec3(0, 1, 0)).scale(i * 1.3));
                            AbilityEffects.send(l, Kind.MATTER_SHARD, hand(g.mob), end, 1.4);
                        }
                        for (LivingEntity e : foes(g, g.mark(), 2.7)) if (g.mob.hasLineOfSight(e)) {
                            hurt(g, e, 7 * p);
                            push(e, flat(e.position().subtract(g.mob.position())).scale(.5).add(0, .15, 0));
                        }
                        AbilityEffects.send(l, Kind.MATTER_COLLAPSE, at, at, 2);
                        sound(l, at, SoundEvents.DEEPSLATE_BREAK, 1, .8F);
                    }),
            m("matter_shield", MATTER, 8, 300, 0, 24, false, Pose.CAST,
                    (g, l, t, p) -> sound(l, g.mob.position(), SoundEvents.DEEPSLATE_PLACE, 1, 1.2F),
                    NOTHING,
                    (g, l, t, p) -> {
                        AbilityEffects.send(l, Kind.MATTER_SHIELD, g.mob.position(), chest(g.mob), 1.5);
                        g.mob.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 1));
                        g.mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 0));
                    }),
            sig("shard_storm", MATTER, 26, 380, 3, 20, true, Pose.CAST,
                    (g, l, t, p) -> { g.mark(t.position()); sound(l, t.position(), SoundEvents.DEEPSLATE_PLACE, 1.2F, .5F); },
                    (g, l, t, p) -> AbilityEffects.send(l, Kind.MATTER_WEAVE, g.mark().add(0, 8, 0), g.mark().add(0, .5, 0), 1.4),
                    (g, l, t, p) -> {
                        Vec3 center = g.mark();
                        for (int i = 0; i < 6; i++) {
                            double a = i * 2.4, r = i == 0 ? 0 : 1.6 + (i % 2) * .8;
                            Vec3 at = center.add(Math.cos(a) * r, 0, Math.sin(a) * r);
                            g.later(i * 3, () -> {
                                AbilityEffects.send(l, Kind.MATTER_SHARD, at.add(0, 12, 0), at, 1.6);
                                for (LivingEntity e : foes(g, at, 1.8)) hurt(g, e, 4 * p);
                                sound(l, at, SoundEvents.DEEPSLATE_BREAK, .7F, 1.1F);
                            });
                        }
                        g.later(20, () -> AbilityEffects.send(l, Kind.MATTER_COLLAPSE, center, center, 3));
                    }),

            // ---- Regeneration: a reckless lunge, blood that mends, a severing ----
            m("reckless_lunge", REGEN, 8, 120, 2, 10, true, Pose.NONE,
                    (g, l, t, p) -> sound(l, g.mob.position(), SoundEvents.PLAYER_ATTACK_STRONG, 1, .6F),
                    NOTHING,
                    (g, l, t, p) -> {
                        push(g.mob, flat(t.position().subtract(g.mob.position())).scale(1.3).add(0, .35, 0));
                        g.later(5, () -> {
                            if (!t.isAlive() || g.mob.distanceToSqr(t) > 9) return;
                            AbilityEffects.send(l, Kind.REGEN_RECKLESS, chest(g.mob), chest(t), 1.2);
                            hurt(g, t, 7 * p);
                            // It doesn't care what the blow costs it.
                            g.mob.hurt(g.mob.damageSources().magic(), 2);
                        });
                    }),
            m("blood_mend", REGEN, 16, 260, 0, 24, false, Pose.CAST,
                    (g, l, t, p) -> sound(l, g.mob.position(), SoundEvents.WARDEN_HEARTBEAT, 1, .9F),
                    (g, l, t, p) -> AbilityEffects.send(l, Kind.REGEN_MEND, g.mob.position(), g.mob.getEyePosition(), .6),
                    (g, l, t, p) -> {
                        g.mob.heal(g.mob.getMaxHealth() * .25F);
                        AbilityEffects.send(l, Kind.REGEN_MEND, g.mob.position(), g.mob.getEyePosition(), 1.4);
                        for (LivingEntity e : allies(g, g.mob.position(), 6)) {
                            e.heal(6);
                            AbilityEffects.send(l, Kind.BLOOD_THREAD, chest(g.mob), chest(e), .8);
                        }
                    }),
            sig("sever", REGEN, 18, 360, 2, 14, true, Pose.POINT,
                    (g, l, t, p) -> sound(l, g.mob.position(), SoundEvents.WARDEN_HEARTBEAT, 1.2F, .6F),
                    (g, l, t, p) -> AbilityEffects.send(l, Kind.BLOOD_THREAD, hand(g.mob), chest(t), .6),
                    (g, l, t, p) -> {
                        AbilityEffects.send(l, Kind.REGEN_SEVER, hand(g.mob), chest(t), 1.4);
                        hurt(g, t, 10 * p);
                        t.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1));
                        g.mob.heal(6);
                    }),

            // ---- Hydrokinesis: a whip that pulls, a drenching, a geyser ----
            m("water_whip", HYDRO, 10, 120, 3, 14, true, Pose.POINT,
                    (g, l, t, p) -> sound(l, g.mob.position(), SoundEvents.PLAYER_SPLASH, .6F, 1.4F),
                    NOTHING,
                    (g, l, t, p) -> {
                        SetPiecePayload.send(l, Piece.HYDRO_WHIP, hand(g.mob), chest(t), 1, null);
                        hurt(g, t, 5 * p);
                        com.jayptucker.heroesevolved.hydro.HydroService.soak(t, 200);
                        push(t, flat(g.mob.position().subtract(t.position())).scale(.9).add(0, .3, 0));
                        sound(l, t.position(), SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1, 1);
                    }),
            m("drench", HYDRO, 14, 160, 2, 16, true, Pose.CAST,
                    (g, l, t, p) -> sound(l, g.mob.position(), SoundEvents.GENERIC_SPLASH, 1, .7F),
                    NOTHING,
                    (g, l, t, p) -> {
                        SetPiecePayload.send(l, Piece.HYDRO_SPLASH, t.position(), t.position().add(0, 1, 0), 2.2F, null);
                        for (LivingEntity e : foes(g, t.position(), 3)) {
                            hurt(g, e, 4 * p);
                            com.jayptucker.heroesevolved.hydro.HydroService.soak(e, 300);
                            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
                        }
                        sound(l, t.position(), SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1.2F, .8F);
                    }),
            sig("geyser", HYDRO, 24, 360, 3, 20, true, Pose.SLAM,
                    (g, l, t, p) -> {
                        g.mark(t.position());
                        SetPiecePayload.send(l, Piece.HYDRO_GEYSER_MARK, g.mark(), g.mark(), 2.5F, null);
                        sound(l, t.position(), SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, 1.2F, .7F);
                    },
                    (g, l, t, p) -> { if (g.castTicks % 12 == 0) SetPiecePayload.send(l, Piece.HYDRO_GEYSER_MARK, g.mark(), g.mark(), 2.5F, null); },
                    (g, l, t, p) -> {
                        Vec3 at = g.mark();
                        SetPiecePayload.send(l, Piece.HYDRO_GEYSER, at, at, 2.5F, null);
                        for (LivingEntity e : foes(g, at, 2.5)) {
                            push(e, new Vec3(0, 1.5, 0));
                            hurt(g, e, 8 * p);
                            com.jayptucker.heroesevolved.hydro.HydroService.soak(e, 300);
                        }
                        sound(l, at, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1.5F, .6F);
                    }),

            // ---- Vibe: a blast, a step through a breach, a frequency lock that switches powers off ----
            m("vibe_blast", VIBE, 10, 120, 2, 18, true, Pose.POINT,
                    (g, l, t, p) -> sound(l, g.mob.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1, 1.6F),
                    NOTHING,
                    (g, l, t, p) -> {
                        SetPiecePayload.send(l, Piece.VIBE_WAVE, hand(g.mob), chest(t), 1, null);
                        hurt(g, t, 6 * p);
                        push(t, flat(t.position().subtract(g.mob.position())).scale(1.1).add(0, .3, 0));
                        sound(l, t.position(), SoundEvents.WARDEN_SONIC_BOOM, .5F, 1.8F);
                    }),
            m("breach_step", VIBE, 12, 220, 4, 20, true, Pose.NONE,
                    (g, l, t, p) -> SetPiecePayload.send(l, Piece.VIBE_HIT, chest(g.mob), chest(g.mob).add(0, 1, 0), .9F, null),
                    NOTHING,
                    (g, l, t, p) -> {
                        Vec3 behind = t.position().subtract(flat(t.getLookAngle()).scale(1.6));
                        if (!free(g.mob, behind)) return;
                        SetPiecePayload.send(l, Piece.VIBE_HIT, chest(g.mob), chest(g.mob).add(flat(behind.subtract(g.mob.position()))), .9F, null);
                        g.mob.teleportTo(behind.x, behind.y, behind.z);
                        SetPiecePayload.send(l, Piece.VIBE_HIT, behind.add(0, 1, 0), chest(t), .9F, null);
                        hurt(g, t, 5 * p);
                        sound(l, behind, SoundEvents.ENDERMAN_TELEPORT, .8F, 1.4F);
                    }),
            sig("frequency_lock", VIBE, 30, 420, 2, 18, true, Pose.CAST,
                    (g, l, t, p) -> { g.mark(t.position()); SetPiecePayload.send(l, Piece.VIBE_LOCK_WARN, g.mark(), g.mark(), 2.5F, null); },
                    (g, l, t, p) -> { if (g.castTicks % 12 == 0) SetPiecePayload.send(l, Piece.VIBE_LOCK_WARN, g.mark(), g.mark(), 2.5F, null); },
                    (g, l, t, p) -> {
                        Vec3 at = g.mark();
                        SetPiecePayload.send(l, Piece.VIBE_LOCK, at, at, 2.5F, null);
                        // Whoever is still in the ring is out of tune: their power goes quiet for five seconds.
                        for (LivingEntity e : foes(g, at, 2.5)) {
                            e.addEffect(new MobEffectInstance(ModItems.DAMPENED, 100, 0));
                            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1));
                            hurt(g, e, 4 * p);
                            if (e instanceof ServerPlayer player)
                                player.displayClientMessage(Component.literal("Your power goes quiet: you're out of tune.").withStyle(ChatFormatting.AQUA), true);
                        }
                        sound(l, at, SoundEvents.BEACON_DEACTIVATE, 1, 1.4F);
                    }),

            // ---- Shapeshifting: a beast's lunge, a terrifying face, Apex Form ----
            m("beast_lunge", SHIFT, 8, 110, 2, 10, true, Pose.NONE,
                    (g, l, t, p) -> sound(l, g.mob.position(), SoundEvents.POLAR_BEAR_WARNING, 1, 1),
                    NOTHING,
                    (g, l, t, p) -> {
                        SetPiecePayload.send(l, Piece.SHIFT_LUNGE, hand(g.mob), chest(t), 1, null);
                        push(g.mob, flat(t.position().subtract(g.mob.position())).scale(1).add(0, .3, 0));
                        hurt(g, t, 6 * p);
                        push(t, flat(t.position().subtract(g.mob.position())).scale(.6).add(0, .25, 0));
                    }),
            m("terrify", SHIFT, 14, 220, 2, 16, true, Pose.NONE,
                    (g, l, t, p) -> sound(l, g.mob.position(), SoundEvents.WARDEN_ROAR, .5F, 1.4F),
                    NOTHING,
                    (g, l, t, p) -> {
                        AbilityEffects.send(l, Kind.MIND_PULSE, t.position(), t.getEyePosition(), 1.3);
                        t.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0));
                        t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
                        t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0));
                    }),
            sig("apex_form", SHIFT, 20, 600, 0, 24, false, Pose.CAST,
                    (g, l, t, p) -> sound(l, g.mob.position(), SoundEvents.EVOKER_PREPARE_SUMMON, 1, .6F),
                    (g, l, t, p) -> AbilityEffects.send(l, Kind.DENSITY_SHIFT, g.mob.position(), g.mob.position(), 1),
                    (g, l, t, p) -> {
                        // It grows half again its size, and hits like it.
                        SetPiecePayload.send(l, Piece.SHIFT_DECOY_BURST, g.mob.position(), g.mob.position().add(0, 1, 0), 1.5F, null);
                        grow(g.mob);
                        g.grownUntil = g.now() + 300;
                        g.mob.heal(g.mob.getMaxHealth() * .3F);
                        sound(l, g.mob.position(), SoundEvents.RAVAGER_ROAR, 1.2F, .8F);
                    })
    );

    private MobMoves() {}

    /** Every move of {@code power} (none for Empathic Mimicry, which borrows). */
    public static List<Move> of(ResourceLocation power) {
        return ALL.stream().filter(m -> m.power().equals(power)).toList();
    }

    public static Move byId(String id) {
        for (Move m : ALL) if (m.id().equals(id)) return m;
        return null;
    }

    /**
     * The moves a mob of {@code rarity} knows: the first of its power's ordinary moves (an Uncommon mob) or both, and its
     * signature for a Mythic mob always and an Epic one once unbound.
     */
    public static List<Move> known(ResourceLocation power, Rarity rarity, boolean unbound) {
        List<Move> out = new ArrayList<>();
        int ordinary = 0;
        for (Move m : of(power)) {
            if (m.signature()) { if (rarity.signature && (rarity == Rarity.MYTHIC || unbound)) out.add(m); }
            else if (ordinary++ < rarity.moves) out.add(m);
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------

    /** Where a cast leaves the body: in front of the chest, a little below the eyes. */
    static Vec3 hand(Entity e) { return e.getEyePosition().add(flat(e.getLookAngle()).scale(.5)).subtract(0, .45, 0); }

    static Vec3 chest(Entity e) { return e.getBoundingBox().getCenter(); }

    static Vec3 flat(Vec3 v) {
        Vec3 f = new Vec3(v.x, 0, v.z);
        return f.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : f.normalize();
    }

    static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.HOSTILE, volume, pitch);
    }

    static boolean hurt(GiftedMob g, LivingEntity target, float amount) {
        target.invulnerableTime = 0;
        return target.hurt(g.mob.damageSources().mobAttack(g.mob), amount);
    }

    /** Damage that passes armour (a mind, a hand inside you). */
    static boolean magic(GiftedMob g, LivingEntity target, float amount) {
        target.invulnerableTime = 0;
        return target.hurt(g.mob.damageSources().indirectMagic(g.mob, g.mob), amount);
    }

    public static void push(Entity target, Vec3 velocity) {
        target.setDeltaMovement(velocity);
        target.hurtMarked = true;
        if (target instanceof ServerPlayer player) player.connection.send(new ClientboundSetEntityMotionPacket(player));
    }

    /** Room for the mob's body at {@code at}. */
    static boolean free(Mob mob, Vec3 at) {
        return mob.level().noCollision(mob, mob.getBoundingBox().move(at.subtract(mob.position())));
    }

    /**
     * Whom a gifted mob's power hurts. An evil gift: players (not in creative), villagers (a renegade only when its
     * village's turn comes, see {@link Renegades#mayHurt}), golems, tamed pets and good gifts, never another monster.
     * A good gift (a guardian): monsters and evil gifts, never people, villagers, golems or animals.
     */
    public static boolean foe(GiftedMob g, LivingEntity t) {
        if (t == g.mob || !t.isAlive() || t.isSpectator() || t.isInvulnerable() || t instanceof ArmorStand) return false;
        if (t instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
        GiftedMob other = GiftedMobs.of(t);
        if (g.evil()) {
            if (other != null) return !other.evil();
            if (t instanceof Enemy) return false;
            if (t instanceof AbstractVillager) return Renegades.mayHurt(g.mob, t);
            return t instanceof Player || t instanceof AbstractGolem || t instanceof TamableAnimal pet && pet.isTame();
        }
        if (other != null) return other.evil();
        return t instanceof Enemy;
    }

    /** Those on the mob's own side near {@code center} (Blood Mend's reach). */
    static List<LivingEntity> allies(GiftedMob g, Vec3 center, double radius) {
        return g.mob.level().getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius), t -> t != g.mob && t.isAlive()
                && !foe(g, t) && t.distanceToSqr(center) <= radius * radius
                && (g.evil() ? t instanceof Enemy : t instanceof AbstractVillager || t instanceof Player || t instanceof AbstractGolem));
    }

    static List<LivingEntity> foes(GiftedMob g, Vec3 center, double radius) {
        return g.mob.level().getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius),
                t -> foe(g, t) && t.distanceToSqr(center) <= radius * radius).stream().limit(16).toList();
    }

    static List<LivingEntity> cone(GiftedMob g, Vec3 from, Vec3 aim, double reach, double dot) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity t : foes(g, from, reach)) {
            Vec3 to = chest(t).subtract(from);
            if (to.normalize().dot(aim) >= dot && g.mob.hasLineOfSight(t)) out.add(t);
        }
        return out;
    }

    /** Foes within {@code width} of the segment from {@code a} to {@code b}. */
    static List<LivingEntity> line(GiftedMob g, Vec3 a, Vec3 b, double width) {
        Vec3 ab = b.subtract(a);
        double length = ab.length();
        if (length < 1.0E-4) return foes(g, a, width);
        Vec3 dir = ab.scale(1 / length);
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity t : g.mob.level().getEntitiesOfClass(LivingEntity.class, new AABB(a, b).inflate(width + 1), t -> foe(g, t))) {
            Vec3 to = t.position().subtract(a);
            double along = Math.clamp(to.dot(dir), 0, length);
            if (to.subtract(dir.scale(along)).horizontalDistance() <= width && Math.abs(to.y - dir.y * along) < 3) out.add(t);
        }
        return out;
    }

    /** Thunderstrike's bolt, from a clear sky onto a marked spot. */
    private static void strike(GiftedMob g, ServerLevel l, Vec3 at, float damage) {
        AbilityEffects.send(l, Kind.ELECTRIC_SKY_BOLT, at, at.add(0, 28, 0), 1);
        for (LivingEntity e : foes(g, at, 2.5)) hurt(g, e, damage);
        sound(l, at, SoundEvents.LIGHTNING_BOLT_THUNDER, 1, 1.2F);
    }

    /** Storm Surge's three marks: the target's spot and one each side, across the line from the caster. */
    private static List<Vec3> surge(GiftedMob g) {
        Vec3 at = g.mark(), across = flat(at.subtract(g.mob.position())).cross(new Vec3(0, 1, 0)).scale(3);
        return List.of(at, at.add(across), at.subtract(across));
    }

    private static final ResourceLocation APEX = ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "gifted_apex");

    /** Apex Form: half again as big, and hits as hard. */
    static void grow(Mob mob) {
        AttributeInstance scale = mob.getAttribute(Attributes.SCALE);
        if (scale != null) scale.addOrUpdateTransientModifier(new AttributeModifier(APEX, .5, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        AttributeInstance attack = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null) attack.addOrUpdateTransientModifier(new AttributeModifier(APEX, .5, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        mob.refreshDimensions();
    }

    static void shrink(Mob mob) {
        AttributeInstance scale = mob.getAttribute(Attributes.SCALE);
        if (scale != null) scale.removeModifier(APEX);
        AttributeInstance attack = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null) attack.removeModifier(APEX);
        mob.refreshDimensions();
    }
}
