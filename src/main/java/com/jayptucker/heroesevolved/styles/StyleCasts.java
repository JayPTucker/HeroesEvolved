package com.jayptucker.heroesevolved.styles;

import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.Targeting;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.ScreenTintPayload;
import com.jayptucker.heroesevolved.radiation.RadiationService;
import com.jayptucker.heroesevolved.sounds.ModSounds;
import com.jayptucker.heroesevolved.styles.StyleRuntime.Ward;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.jayptucker.heroesevolved.styles.StyleOps.*;

/**
 * The class abilities themselves: for every power an Offense, a Defense and a Support ability (see
 * {@code skill_trees.json}, "styles"). Each one starts its set piece (so everyone nearby sees it) and does
 * what it does over the same ticks the piece is drawn, so the light and the effect agree.
 */
public final class StyleCasts {
    /** Ticks Starfall's star takes to fall from the sky; it lands (and burns) at the end of them. */
    public static final int STARFALL_FALL = 30;
    private StyleCasts() {}

    private static final ResourceLocation STEADY = ResourceLocation.fromNamespaceAndPath("heroesevolved", "class_steady");

    public static boolean canUse(AbilityUseContext c, String mechanic) {
        ServerPlayer p = c.player();
        return switch (mechanic) {
            case "vital_burst" -> p.getHealth() > 6;
            case "lightning_flurry", "shadow_strike" -> !foes(p, p.position(), mechanic.equals("shadow_strike") ? 10 : 12).isEmpty();
            default -> true;
        };
    }

    public static boolean cast(AbilityUseContext c, String mechanic) {
        ServerPlayer p = c.player();
        ServerLevel level = p.serverLevel();
        int lvl = Math.max(1, c.abilityLevel());
        Vec3 at = p.position(), f = facing(p);
        switch (mechanic) {
            // ---------------------------------------------------------- Radiation
            case "ground_zero" -> {
                Vec3 point = aim(p, 32);
                float radius = 9;
                piece(p, Piece.GROUND_ZERO, at, point, radius, null);
                sound(level, point, ModSounds.NUCLEAR_CHARGE.get(), 0.7F, 1.3F);
                StyleRuntime.run(p, 30, age -> {
                    if (age != 30) return;
                    for (LivingEntity t : foes(p, point, radius)) {
                        double d = t.position().distanceTo(point);
                        hurt(p, t, (float) (10 + 2 * lvl + 12 * (1 - d / radius)));
                        RadiationService.irradiate(t, p, 1, 160);
                        shove(t, point, 1.3, 0.5);
                    }
                    sound(level, point, ModSounds.NUCLEAR_BLAST.get(), 0.9F, 1.5F);
                    sound(level, point, SoundEvents.GENERIC_EXPLODE.value(), 1.4F, 0.6F);
                    ScreenTintPayload.sendAround(level, point, 40, 0xC8FFF6E6, 10, ScreenTintPayload.Style.FLASH);
                    CameraShakePayload.sendAround(level, point, 40, 0.3F, 30);
                });
            }
            case "containment_dome" -> {
                float radius = 5;
                piece(p, Piece.CONTAINMENT_DOME, at, at, radius, null);
                StyleRuntime.zone(p, at, radius, 160, 0.65F);
                sound(level, at, SoundEvents.BEACON_ACTIVATE, 1, 1.3F);
                StyleRuntime.run(p, 160, age -> {
                    if (destroy(p, at.add(0, 1, 0), radius + 0.5) > 0) sound(level, at, SoundEvents.SHIELD_BLOCK, 0.6F, 1.6F);
                    if (age % 20 == 10) for (LivingEntity t : foes(p, at, radius)) { magic(p, t, 1); RadiationService.irradiate(t, p, 1, 60); }
                }, () -> sound(level, at, SoundEvents.BEACON_DEACTIVATE, 1, 1.3F));
            }
            case "reactor_hearth" -> {
                float radius = 7;
                piece(p, Piece.REACTOR_HEARTH, at, at, radius, null);
                sound(level, at, SoundEvents.CONDUIT_ACTIVATE, 1, 0.8F);
                for (LivingEntity friend : friends(p, at, radius)) effect(friend, MobEffects.ABSORPTION, 200, 0);
                StyleRuntime.run(p, 200, age -> {
                    if (age % 20 != 0) return;
                    for (LivingEntity friend : friends(p, at, radius)) {
                        effect(friend, MobEffects.REGENERATION, 50, 1);
                        effect(friend, MobEffects.DIG_SPEED, 50, 0);
                        if (friend instanceof ServerPlayer fp) com.jayptucker.heroesevolved.energy.PlayerEnergyService.restore(fp, 2);
                    }
                });
            }
            // ---------------------------------------------------------- Space-Time
            case "hour_hand" -> {
                float radius = 7;
                piece(p, Piece.HOUR_HAND, at, at.add(f), radius, null);
                sound(level, at, ModSounds.TIME_BLINK.get(), 1, 0.7F);
                Set<LivingEntity> struck = new HashSet<>();
                Vec3 right = new Vec3(-f.z, 0, f.x);
                StyleRuntime.run(p, 22, age -> {
                    float sweep = Mth.TWO_PI * smooth(4, 20, age);
                    if (age == 6) sound(level, at, SoundEvents.PLAYER_ATTACK_SWEEP, 1.2F, 0.6F);
                    for (LivingEntity t : foes(p, at, radius + 0.5)) {
                        if (struck.contains(t)) continue;
                        Vec3 d = t.position().subtract(at);
                        float clock = (float) Math.atan2(d.dot(right), d.dot(f));
                        if (clock < 0) clock += Mth.TWO_PI;
                        if (clock > sweep) continue;
                        struck.add(t);
                        hurt(p, t, 9 + 2 * lvl);
                        effect(t, MobEffects.MOVEMENT_SLOWDOWN, 60, 1);
                        Vec3 tangent = new Vec3(-d.z, 0, d.x).normalize();
                        push(t, tangent.scale(-0.8).add(0, 0.25, 0));
                    }
                });
            }
            case "time_loop" -> {
                piece(p, Piece.TIME_LOOP, at, at, 1, p);
                sound(level, at, ModSounds.TIME_SLOW.get(), 1, 1.2F);
                var loop = StyleRuntime.ward(p, Ward.TIME_LOOP, 120, p);
                loop.value = p.getHealth();
                StyleRuntime.run(p, 120, age -> { }, () -> StyleRuntime.warded(p, Ward.TIME_LOOP).ifPresent(state -> {
                    StyleRuntime.unward(p, Ward.TIME_LOOP);
                    closeLoop(p, state.value);
                }));
            }
            case "borrowed_time" -> {
                sound(level, at, ModSounds.TIME_UNSLOW.get(), 1, 1.3F);
                for (LivingEntity friend : friends(p, at, 10)) {
                    effect(friend, MobEffects.MOVEMENT_SPEED, 160, 1);
                    effect(friend, MobEffects.DIG_SPEED, 160, 1);
                    piece(p, Piece.BORROWED_TIME, friend.position(), friend.position(), 1, friend);
                    if (friend instanceof ServerPlayer fp) com.jayptucker.heroesevolved.energy.PlayerEnergyService.restore(fp, 15);
                }
            }
            // ---------------------------------------------------------- Pyrokinesis
            case "sunfall" -> {
                // Starfall: a star dropped from high in the sky, falling for a second and a half onto where you aim.
                Vec3 point = aim(p, 48);
                float radius = 5;
                piece(p, Piece.SUNFALL, at, point, radius, null);
                sound(level, point.add(0, 24, 0), SoundEvents.FIRECHARGE_USE, 2.0F, 0.4F);
                sound(level, point.add(0, 16, 0), SoundEvents.BLAZE_SHOOT, 1.5F, 0.4F);
                StyleRuntime.run(p, STARFALL_FALL, age -> {
                    if (age == STARFALL_FALL / 2) sound(level, point.add(0, 8, 0), SoundEvents.FIREWORK_ROCKET_LAUNCH, 2.0F, 0.5F);
                    if (age != STARFALL_FALL) return;
                    for (LivingEntity t : foes(p, point, radius)) {
                        hurt(p, t, 12 + 2 * lvl);
                        t.igniteForSeconds(5);
                        shove(t, point, 1.0, 0.6);
                    }
                    sound(level, point, SoundEvents.GENERIC_EXPLODE.value(), 1.6F, 0.8F);
                    sound(level, point, SoundEvents.BLAZE_SHOOT, 1.5F, 0.6F);
                    CameraShakePayload.sendAround(level, point, 24, 0.25F, 20);
                });
            }
            case "ember_shroud" -> {
                piece(p, Piece.EMBER_SHROUD, at, at, 1, p);
                StyleRuntime.ward(p, Ward.EMBER, 160, p);
                effect(p, MobEffects.FIRE_RESISTANCE, 160, 0);
                sound(level, at, SoundEvents.BLAZE_AMBIENT, 1, 0.8F);
                StyleRuntime.run(p, 160, age -> {
                    if (destroy(p, p.position().add(0, 1, 0), 3.5) > 0) sound(level, p.position(), SoundEvents.LAVA_POP, 1, 1);
                });
            }
            case "hearthfire" -> {
                float radius = 6;
                piece(p, Piece.HEARTHFIRE, at, at, radius, null);
                sound(level, at, SoundEvents.CAMPFIRE_CRACKLE, 2, 0.8F);
                StyleRuntime.run(p, 240, age -> {
                    if (age % 20 != 0) return;
                    for (LivingEntity friend : friends(p, at, radius)) {
                        heal(friend, 1.5F);
                        effect(friend, MobEffects.FIRE_RESISTANCE, 50, 0);
                        friend.setTicksFrozen(0);
                    }
                    if (age % 60 == 0) sound(level, at, SoundEvents.CAMPFIRE_CRACKLE, 1.5F, 0.9F);
                });
            }
            // ---------------------------------------------------------- Cryokinesis
            case "hailstorm" -> {
                Vec3 point = aim(p, 30);
                float radius = 5;
                piece(p, Piece.HAILSTORM, at, point, radius, null);
                sound(level, point.add(0, 10, 0), SoundEvents.LIGHTNING_BOLT_THUNDER, 0.4F, 1.6F);
                StyleRuntime.run(p, 84, age -> {
                    if (age < 16 || age % 8 != 0) return;
                    for (LivingEntity t : foes(p, point, radius)) {
                        hurt(p, t, 3 + lvl * 0.5F);
                        effect(t, MobEffects.MOVEMENT_SLOWDOWN, 40, 1);
                        t.setTicksFrozen(Math.min(t.getTicksRequiredToFreeze() + 40, t.getTicksFrozen() + 30));
                    }
                    sound(level, point, SoundEvents.GLASS_BREAK, 0.7F, 1.6F);
                });
            }
            case "crystal_bastion" -> {
                float radius = 3;
                piece(p, Piece.CRYSTAL_BASTION, at, at, radius, null);
                effect(p, MobEffects.DAMAGE_RESISTANCE, 160, 0);
                sound(level, at, SoundEvents.AMETHYST_BLOCK_PLACE, 1.5F, 0.6F);
                StyleRuntime.run(p, 160, age -> {
                    if (destroy(p, at.add(0, 1, 0), radius + 1.5) > 0) sound(level, at, SoundEvents.AMETHYST_BLOCK_HIT, 1, 1.3F);
                    if (age % 5 == 0) for (LivingEntity t : foes(p, at, radius - 0.2)) shove(t, at, 0.7, 0.2);
                }, () -> sound(level, at, SoundEvents.GLASS_BREAK, 1.5F, 0.7F));
            }
            case "aurora_veil" -> {
                float radius = 8;
                piece(p, Piece.AURORA_VEIL, at, at, radius, null);
                sound(level, at, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.5F, 0.6F);
                StyleRuntime.run(p, 200, age -> {
                    if (age % 20 != 0) return;
                    for (LivingEntity friend : friends(p, at, radius)) {
                        effect(friend, MobEffects.REGENERATION, 50, 0);
                        effect(friend, MobEffects.FIRE_RESISTANCE, 50, 0);
                    }
                    for (LivingEntity t : foes(p, at, radius)) effect(t, MobEffects.MOVEMENT_SLOWDOWN, 50, 0);
                });
            }
            // ---------------------------------------------------------- Electrokinesis
            case "tesla_spire" -> {
                Vec3 point = aim(p, 20);
                float reach = 9;
                piece(p, Piece.TESLA_SPIRE, at, point, reach, null);
                sound(level, point, SoundEvents.ANVIL_PLACE, 0.6F, 1.6F);
                Vec3 crown = point.add(0, 3.2, 0);
                StyleRuntime.run(p, 120, age -> {
                    if (age < 10 || age % 10 != 0) return;
                    int n = 0;
                    for (LivingEntity t : foes(p, point, reach)) {
                        if (n++ >= 3) break;
                        hurt(p, t, 4 + lvl * 0.5F);
                        effect(t, MobEffects.MOVEMENT_SLOWDOWN, 20, 2);
                        piece(p, Piece.TESLA_ZAP, crown, t.getEyePosition().subtract(0, 0.4, 0), 1, null);
                    }
                    if (n > 0) sound(level, point, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.35F, 1.8F);
                });
            }
            case "faraday_cage" -> {
                piece(p, Piece.FARADAY_CAGE, at, at, 2.2F, p);
                StyleRuntime.ward(p, Ward.FARADAY, 120, p);
                sound(level, at, SoundEvents.BEACON_POWER_SELECT, 1, 1.8F);
                StyleRuntime.run(p, 120, age -> {
                    if (destroy(p, p.position().add(0, 1, 0), 3) > 0) sound(level, p.position(), SoundEvents.LIGHTNING_BOLT_IMPACT, 0.3F, 2);
                });
            }
            case "jumpstart" -> {
                List<LivingEntity> chain = new ArrayList<>();
                LivingEntity from = p;
                List<LivingEntity> left = new ArrayList<>(friends(p, at, 12));
                left.remove(p);
                for (int hop = 0; hop < 5 && !left.isEmpty(); hop++) {
                    LivingEntity here = from;
                    LivingEntity next = left.stream().min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(here))).get();
                    if (next.distanceToSqr(here) > 10 * 10) break;
                    piece(p, Piece.JUMPSTART, here.position(), next.position(), 1, null, hop * 3);
                    chain.add(next);
                    left.remove(next);
                    from = next;
                }
                chain.add(p);
                if (chain.size() == 1) piece(p, Piece.JUMPSTART, at.add(0, 2, 0), at, 1, null);
                for (LivingEntity friend : chain) {
                    heal(friend, friend == p ? 4 : 6);
                    effect(friend, MobEffects.MOVEMENT_SPEED, 120, 0);
                    friend.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                    friend.removeEffect(MobEffects.WEAKNESS);
                    friend.removeEffect(MobEffects.DIG_SLOWDOWN);
                }
                sound(level, at, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.5F, 1.9F);
            }
            // ---------------------------------------------------------- Quake
            case "fissure_maw" -> {
                float length = 14;
                piece(p, Piece.FISSURE_MAW, at, at.add(f.scale(length)), length, null);
                sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 1, 0.5F);
                Set<LivingEntity> thrown = new HashSet<>();
                Vec3 side = new Vec3(-f.z, 0, f.x);
                StyleRuntime.run(p, 16, age -> {
                    double reach = Math.min(length, age / 0.9);
                    for (LivingEntity t : foes(p, at.add(f.scale(reach / 2)), reach / 2 + 2.5)) {
                        Vec3 d = t.position().subtract(at);
                        double along = d.dot(f), off = d.dot(side);
                        if (thrown.contains(t) || along < 0 || along > reach || Math.abs(off) > 2) continue;
                        thrown.add(t);
                        hurt(p, t, 10 + 2 * lvl);
                        push(t, side.scale(Math.signum(off == 0 ? 1 : off) * 0.5).add(0, 1.0, 0));
                    }
                    if (age % 4 == 0) sound(level, at.add(f.scale(reach)), SoundEvents.STONE_BREAK, 1.2F, 0.6F);
                });
            }
            case "stone_rampart" -> {
                float radius = 3.5F;
                piece(p, Piece.STONE_RAMPART, at, at.add(f), radius, null);
                sound(level, at, SoundEvents.STONE_PLACE, 2, 0.5F);
                StyleRuntime.run(p, 200, age -> {
                    for (Projectile shot : incoming(p, at.add(0, 1, 0), radius + 1.2)) {
                        Vec3 d = shot.position().subtract(at).multiply(1, 0, 1);
                        if (d.length() > radius - 0.7 && d.normalize().dot(f) > Math.cos(1.35)) { shot.discard(); sound(level, shot.position(), SoundEvents.STONE_HIT, 1, 1); }
                    }
                    if (age % 5 == 0) for (LivingEntity t : foes(p, at, radius + 0.8)) {
                        Vec3 d = t.position().subtract(at).multiply(1, 0, 1);
                        if (d.lengthSqr() > 1e-3 && d.normalize().dot(f) > Math.cos(1.35)) shove(t, at, 0.6, 0.15);
                    }
                }, () -> sound(level, at, SoundEvents.STONE_BREAK, 2, 0.5F));
            }
            case "bedrock_stance" -> {
                float radius = 10;
                piece(p, Piece.BEDROCK_STANCE, at, at, radius, null);
                sound(level, at, SoundEvents.ANVIL_LAND, 0.8F, 0.5F);
                List<LivingEntity> held = friends(p, at, radius);
                for (LivingEntity friend : held) {
                    effect(friend, MobEffects.DAMAGE_RESISTANCE, 160, 0);
                    var knock = friend.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
                    if (knock != null) knock.addOrUpdateTransientModifier(new AttributeModifier(STEADY, 1, AttributeModifier.Operation.ADD_VALUE));
                }
                StyleRuntime.run(p, 160, age -> { }, () -> held.forEach(friend -> {
                    var knock = friend.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
                    if (knock != null) knock.removeModifier(STEADY);
                }));
            }
            // ---------------------------------------------------------- Flight
            case "tempest_drill" -> {
                Vec3 dir = p.getLookAngle();
                dir = new Vec3(dir.x, Mth.clamp(dir.y, -0.4, 0.4), dir.z).normalize();
                float length = 18;
                piece(p, Piece.TEMPEST_DRILL, at, at.add(dir.scale(length)), 1, null);
                sound(level, at, ModSounds.FLIGHT_LAUNCH.get(), 1, 1.2F);
                Set<LivingEntity> hit = new HashSet<>();
                Vec3 velocity = dir.scale(length / 10);
                StyleRuntime.run(p, 10, age -> {
                    push(p, velocity);
                    p.fallDistance = 0;
                    for (LivingEntity t : foes(p, p.position(), 2.5)) {
                        if (!hit.add(t)) continue;
                        hurt(p, t, 9 + 2 * lvl);
                        Vec3 side = t.position().subtract(p.position()).multiply(1, 0, 1);
                        push(t, (side.lengthSqr() < 1e-3 ? new Vec3(0, 0, 1) : side.normalize()).scale(1.2).add(0, 0.5, 0));
                    }
                }, () -> { push(p, velocity.scale(0.15)); p.fallDistance = 0; });
            }
            case "gale_wall" -> {
                float radius = 3;
                piece(p, Piece.GALE_WALL, at, at, radius, p);
                sound(level, at, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 1, 0.7F);
                StyleRuntime.run(p, 160, age -> {
                    reflect(p, p.position().add(0, 1, 0), radius + 0.5);
                    if (age % 5 == 0) for (LivingEntity t : foes(p, p.position(), radius)) shove(t, p.position(), 0.8, 0.2);
                });
            }
            case "formation_flight" -> {
                sound(level, at, ModSounds.FLIGHT_LAUNCH.get(), 0.8F, 1.4F);
                for (LivingEntity friend : friends(p, at, 12)) {
                    effect(friend, MobEffects.SLOW_FALLING, 200, 0);
                    effect(friend, MobEffects.MOVEMENT_SPEED, 200, 1);
                    effect(friend, MobEffects.JUMP, 200, 0);
                    push(friend, friend.getDeltaMovement().add(0, 0.55, 0));
                    piece(p, Piece.FORMATION_FLIGHT, friend.position(), friend.position(), 1, friend);
                }
            }
            // ---------------------------------------------------------- Telekinesis
            case "implosion" -> {
                Vec3 point = aim(p, 24);
                float radius = 6;
                piece(p, Piece.IMPLOSION, at, point, radius, null);
                sound(level, point, SoundEvents.BEACON_DEACTIVATE, 1.5F, 0.5F);
                Vec3 core = point.add(0, 1.2, 0);
                StyleRuntime.run(p, 22, age -> {
                    if (age < 22) {
                        for (LivingEntity t : foes(p, core, radius)) push(t, core.subtract(t.position()).scale(0.14).add(0, 0.04, 0));
                        return;
                    }
                    for (LivingEntity t : foes(p, core, 3.5)) { hurt(p, t, 12 + 2 * lvl); push(t, new Vec3(0, -1.2, 0)); }
                    sound(level, point, SoundEvents.GENERIC_EXPLODE.value(), 1.2F, 0.6F);
                    CameraShakePayload.sendAround(level, point, 20, 0.2F, 15);
                });
            }
            case "debris_halo" -> {
                piece(p, Piece.DEBRIS_HALO, at, at, 2.6F, p);
                sound(level, at, SoundEvents.GRAVEL_BREAK, 1.5F, 0.6F);
                StyleRuntime.run(p, 160, age -> {
                    destroy(p, p.position().add(0, 1.2, 0), 3.2);
                    if (age % 10 == 0) for (LivingEntity t : foes(p, p.position(), 2.8)) { hurt(p, t, 3); shove(t, p.position(), 0.8, 0.3); }
                });
            }
            case "gathering_hand" -> {
                sound(level, at, SoundEvents.ENDER_EYE_DEATH, 1, 0.8F);
                List<LivingEntity> gathered = friends(p, at, 16);
                gathered.remove(p);
                int i = 0;
                for (LivingEntity friend : gathered) {
                    double ang = i++ * Math.PI * 2 / Math.max(1, gathered.size());
                    Vec3 spot = at.add(Math.cos(ang) * 1.6, 0, Math.sin(ang) * 1.6);
                    piece(p, Piece.GATHERING_HAND, friend.position(), at, 1, friend);
                    StyleRuntime.run(p, 10, age -> { if (age == 10) friend.teleportTo(spot.x, spot.y, spot.z); });
                    effect(friend, MobEffects.DAMAGE_RESISTANCE, 110, 1);
                }
                effect(p, MobEffects.DAMAGE_RESISTANCE, 110, 1);
                if (gathered.isEmpty()) piece(p, Piece.GATHERING_HAND, at, at, 1, p);
            }
            // ---------------------------------------------------------- Telepathy
            case "psychic_storm" -> {
                Vec3 point = aim(p, 24);
                float radius = 6;
                piece(p, Piece.PSYCHIC_STORM, at, point, radius, null);
                sound(level, point, SoundEvents.ELDER_GUARDIAN_CURSE, 0.6F, 1.4F);
                StyleRuntime.run(p, 120, age -> {
                    if (age % 20 != 0) return;
                    List<LivingEntity> caught = foes(p, point, radius);
                    for (LivingEntity t : caught) {
                        magic(p, t, 2 + lvl * 0.5F);
                        effect(t, MobEffects.CONFUSION, 80, 0);
                        effect(t, MobEffects.WEAKNESS, 60, 0);
                        if (t instanceof Mob mob && caught.size() > 1) {
                            LivingEntity other = caught.get((caught.indexOf(t) + 1) % caught.size());
                            if (other != t) mob.setTarget(other);
                        }
                    }
                });
            }
            case "blind_spot" -> {
                piece(p, Piece.BLIND_SPOT, at, at, 1, p);
                StyleRuntime.ward(p, Ward.BLIND, 100, p);
                for (Mob mob : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(16), m -> m.getTarget() == p)) mob.setTarget(null);
                sound(level, at, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1, 1);
            }
            case "hive_mind" -> {
                List<LivingEntity> group = friends(p, at, 14).stream().limit(6).toList();
                for (LivingEntity friend : group) {
                    StyleRuntime.ward(friend, Ward.HIVE, 200, p).group = group;
                    piece(p, Piece.HIVE_MIND, at, at, 1, friend);
                }
                sound(level, at, SoundEvents.BEACON_POWER_SELECT, 1, 0.6F);
            }
            // ---------------------------------------------------------- Regeneration
            case "vital_burst" -> {
                float radius = 6;
                p.setHealth(Math.max(1, p.getHealth() - 4));
                piece(p, Piece.VITAL_BURST, at, at, radius, null);
                sound(level, at, SoundEvents.WARDEN_HEARTBEAT, 2, 1);
                StyleRuntime.run(p, 6, age -> { if (age == 6) sound(level, at, SoundEvents.WARDEN_HEARTBEAT, 2, 1.1F); });
                int hits = 0;
                for (LivingEntity t : foes(p, at, radius)) { hurt(p, t, 10 + 2 * lvl); hits++; }
                heal(p, Math.min(10, hits * 2));
            }
            case "undying" -> {
                piece(p, Piece.UNDYING, at, at, 1, p);
                StyleRuntime.ward(p, Ward.UNDYING, 120, p);
                sound(level, at, SoundEvents.BEACON_POWER_SELECT, 1, 1.5F);
                StyleRuntime.run(p, 120, age -> { }, () -> { heal(p, p.getMaxHealth() * 0.3F); sound(level, p.position(), SoundEvents.TOTEM_USE, 0.4F, 1.6F); });
            }
            case "lifebloom" -> {
                float radius = 7;
                piece(p, Piece.LIFEBLOOM, at, at, radius, null);
                sound(level, at, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.5F, 1.4F);
                StyleRuntime.run(p, 240, age -> {
                    if (age % 20 != 0) return;
                    for (LivingEntity friend : friends(p, at, radius)) {
                        heal(friend, 2);
                        friend.removeEffect(MobEffects.POISON);
                        friend.removeEffect(MobEffects.WITHER);
                    }
                });
            }
            // ---------------------------------------------------------- Sonic Resonance
            case "resonance_cascade" -> {
                Vec3 point = aim(p, 24);
                float radius = 4.5F;
                piece(p, Piece.RESONANCE_CASCADE, at, point, radius, null);
                StyleRuntime.run(p, 16, age -> {
                    if (age % 4 == 0 && age < 16) sound(level, point, SoundEvents.NOTE_BLOCK_CHIME.value(), 1.2F, 0.6F + age * 0.06F);
                    if (age != 16) return;
                    for (LivingEntity t : foes(p, point, radius)) {
                        hurt(p, t, 12 + 2 * lvl);
                        effect(t, MobEffects.MOVEMENT_SLOWDOWN, 40, 3);
                        effect(t, MobEffects.WEAKNESS, 60, 0);
                    }
                    sound(level, point, ModSounds.SONIC_BURST.get(), 1.5F, 1.2F);
                });
            }
            case "silence_dome" -> {
                float radius = 5;
                piece(p, Piece.SILENCE_DOME, at, at, radius, null);
                sound(level, at, SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.4F, 1.8F);
                StyleRuntime.run(p, 160, age -> {
                    for (Projectile shot : incoming(p, at.add(0, 1, 0), radius)) push(shot, shot.getDeltaMovement().scale(0.2));
                    if (age % 20 == 0) for (LivingEntity t : foes(p, at, radius)) effect(t, MobEffects.WEAKNESS, 30, 0);
                });
            }
            case "battle_hymn" -> {
                piece(p, Piece.BATTLE_HYMN, at, at, 1, p);
                for (LivingEntity friend : friends(p, at, 12)) {
                    effect(friend, MobEffects.DAMAGE_BOOST, 200, 0);
                    effect(friend, MobEffects.MOVEMENT_SPEED, 200, 0);
                }
                for (float pitch : new float[] {0.8F, 1.0F, 1.2F}) sound(level, at, SoundEvents.NOTE_BLOCK_BELL.value(), 1, pitch);
            }
            // ---------------------------------------------------------- Superspeed
            case "lightning_flurry" -> {
                List<LivingEntity> marks = foes(p, at, 12).stream().limit(6).toList();
                Vec3[] last = {at};
                StyleRuntime.run(p, marks.size() * 2 + 2, age -> {
                    int i = age / 2;
                    if (age % 2 != 0) return;
                    if (i >= marks.size()) {
                        piece(p, Piece.LIGHTNING_FLURRY, last[0], at, 1, null);
                        p.teleportTo(at.x, at.y, at.z);
                        return;
                    }
                    LivingEntity t = marks.get(i);
                    if (!t.isAlive()) return;
                    Vec3 behind = t.position().subtract(facingOf(t).scale(1.2));
                    piece(p, Piece.LIGHTNING_FLURRY, last[0], behind, 1, null);
                    p.teleportTo(behind.x, behind.y, behind.z);
                    last[0] = behind;
                    hurt(p, t, 5 + lvl);
                    sound(level, behind, SoundEvents.PLAYER_ATTACK_CRIT, 1, 1.4F);
                });
            }
            case "phase_blur" -> {
                piece(p, Piece.PHASE_BLUR, at, at, 1, p);
                StyleRuntime.ward(p, Ward.BLUR, 120, p);
                sound(level, at, SoundEvents.BEE_LOOP_AGGRESSIVE, 1, 2);
            }
            case "relay_run" -> {
                List<LivingEntity> runners = friends(p, at, 24);
                runners.remove(p);
                int i = 0;
                for (LivingEntity friend : runners) {
                    double ang = i++ * Math.PI * 2 / Math.max(1, runners.size());
                    Vec3 spot = at.add(Math.cos(ang) * 1.5, 0, Math.sin(ang) * 1.5);
                    piece(p, Piece.RELAY_RUN, friend.position(), spot, 1, null);
                    friend.teleportTo(spot.x, spot.y, spot.z);
                    effect(friend, MobEffects.MOVEMENT_SPEED, 120, 1);
                }
                effect(p, MobEffects.MOVEMENT_SPEED, 120, 1);
                if (runners.isEmpty()) piece(p, Piece.RELAY_RUN, at.add(f.scale(-4)), at, 1, null);
                sound(level, at, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.4F, 2);
            }
            // ---------------------------------------------------------- Precognition
            case "foretold_strike" -> {
                Vec3 point = aim(p, 24);
                float radius = 3;
                piece(p, Piece.FORETOLD_STRIKE, at, point, radius, null);
                sound(level, point, SoundEvents.BOOK_PAGE_TURN, 1.5F, 0.7F);
                StyleRuntime.run(p, 40, age -> {
                    if (age != 40) return;
                    for (LivingEntity t : foes(p, point, radius)) hurt(p, t, 16 + 3 * lvl);
                    sound(level, point, SoundEvents.GENERIC_EXPLODE.value(), 0.8F, 1.6F);
                    sound(level, point, SoundEvents.SLIME_SQUISH, 1.5F, 0.6F);
                });
            }
            case "branching_paths" -> {
                piece(p, Piece.BRANCHING_PATHS, at, at, 1, p);
                StyleRuntime.ward(p, Ward.BRANCH, 120, p);
                sound(level, at, SoundEvents.AMETHYST_BLOCK_CHIME, 1.5F, 0.8F);
            }
            case "prophecy_canvas" -> {
                piece(p, Piece.PROPHECY_CANVAS, at, at, 12, null);
                for (LivingEntity friend : friends(p, at, 12)) {
                    StyleRuntime.ward(friend, Ward.PROPHECY, 300, p);
                    effect(friend, MobEffects.LUCK, 300, 0);
                }
                sound(level, at, SoundEvents.VILLAGER_WORK_CARTOGRAPHER, 1.5F, 0.8F);
            }
            // ---------------------------------------------------------- Invisibility
            case "shadow_strike" -> {
                List<LivingEntity> marks = foes(p, at, 10).stream().limit(3).toList();
                StyleRuntime.run(p, marks.size() * 4, age -> {
                    if (age % 4 != 0 || age / 4 >= marks.size()) return;
                    LivingEntity t = marks.get(age / 4);
                    if (!t.isAlive()) return;
                    Vec3 behind = t.position().subtract(facingOf(t).scale(1.1));
                    piece(p, Piece.SHADOW_STRIKE, p.position(), behind, 1, null);
                    p.teleportTo(behind.x, behind.y, behind.z);
                    hurt(p, t, (8 + lvl) * 1.5F);
                    sound(level, behind, SoundEvents.PLAYER_ATTACK_SWEEP, 1, 1.6F);
                }, () -> effect(p, MobEffects.INVISIBILITY, 60, 0));
            }
            case "refraction_cloak" -> {
                piece(p, Piece.REFRACTION_CLOAK, at, at, 1, p);
                effect(p, MobEffects.INVISIBILITY, 120, 0);
                sound(level, at, SoundEvents.ILLUSIONER_PREPARE_MIRROR, 1, 1.2F);
                StyleRuntime.run(p, 120, age -> {
                    for (Projectile shot : incoming(p, p.position().add(0, 1, 0), 4)) {
                        Vec3 v = shot.getDeltaMovement();
                        if (v.dot(p.position().add(0, 1, 0).subtract(shot.position())) <= 0) continue;
                        push(shot, new Vec3(v.x * 0.5 - v.z * 0.87, v.y, v.x * 0.87 + v.z * 0.5));
                    }
                });
            }
            case "prism_haze" -> {
                float radius = 6;
                piece(p, Piece.PRISM_HAZE, at, at, radius, null);
                sound(level, at, SoundEvents.ILLUSIONER_CAST_SPELL, 1, 1.3F);
                StyleRuntime.run(p, 200, age -> {
                    if (age % 10 != 0) return;
                    for (LivingEntity friend : friends(p, at, radius)) {
                        effect(friend, MobEffects.INVISIBILITY, 30, 0);
                        effect(friend, MobEffects.REGENERATION, 30, 0);
                    }
                    for (LivingEntity t : foes(p, at, radius)) effect(t, MobEffects.BLINDNESS, 30, 0);
                });
            }
            // ---------------------------------------------------------- Density
            case "phase_eruption" -> {
                Vec3 point = aim(p, 20);
                float radius = 3.5F;
                piece(p, Piece.PHASE_ERUPTION, at, point, radius, null);
                sound(level, at, SoundEvents.ENDERMAN_TELEPORT, 1, 0.6F);
                StyleRuntime.run(p, 8, age -> {
                    if (age != 8) return;
                    p.teleportTo(point.x, point.y, point.z);
                    p.fallDistance = 0;
                    for (LivingEntity t : foes(p, point, radius)) { hurt(p, t, 10 + 2 * lvl); push(t, new Vec3(0, 1.1, 0)); }
                    sound(level, point, SoundEvents.GENERIC_EXPLODE.value(), 1, 1.2F);
                });
            }
            case "dense_bulwark" -> {
                piece(p, Piece.DENSE_BULWARK, at, at, 1, p);
                effect(p, MobEffects.DAMAGE_RESISTANCE, 160, 2);
                effect(p, MobEffects.MOVEMENT_SLOWDOWN, 160, 1);
                var knock = p.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
                if (knock != null) knock.addOrUpdateTransientModifier(new AttributeModifier(STEADY, 1, AttributeModifier.Operation.ADD_VALUE));
                sound(level, at, SoundEvents.ANVIL_LAND, 1, 0.4F);
                StyleRuntime.run(p, 160, age -> { }, () -> { var k = p.getAttribute(Attributes.KNOCKBACK_RESISTANCE); if (k != null) k.removeModifier(STEADY); });
            }
            case "phase_field" -> {
                for (LivingEntity friend : friends(p, at, 8)) {
                    StyleRuntime.ward(friend, Ward.PHASE_FIELD, 160, p);
                    piece(p, Piece.PHASE_FIELD, friend.position(), friend.position(), 1, friend);
                }
                sound(level, at, SoundEvents.BEACON_AMBIENT, 1.5F, 1.6F);
            }
            // ---------------------------------------------------------- Empathic Mimicry
            case "prismatic_overflow" -> {
                float radius = 7;
                int remembered = Math.max(1, com.jayptucker.heroesevolved.mimicry.MimicryService.memory(p).powers().size());
                piece(p, Piece.PRISMATIC_OVERFLOW, at, at, radius, null);
                for (LivingEntity t : foes(p, at, radius)) {
                    hurt(p, t, 8 + 2 * remembered + lvl);
                    t.igniteForSeconds(3);
                    effect(t, MobEffects.MOVEMENT_SLOWDOWN, 60, 0);
                    effect(t, MobEffects.GLOWING, 100, 0);
                }
                sound(level, at, SoundEvents.ILLUSIONER_CAST_SPELL, 1.5F, 0.8F);
                sound(level, at, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.5F, 0.5F);
            }
            case "empathic_ward" -> {
                piece(p, Piece.EMPATHIC_WARD, at, at, 1, p);
                StyleRuntime.ward(p, Ward.EMPATHY, 160, p).charges = 3;
                sound(level, at, SoundEvents.AMETHYST_BLOCK_RESONATE, 1, 1.2F);
            }
            case "kindred_light" -> {
                int remembered = Math.max(1, com.jayptucker.heroesevolved.mimicry.MimicryService.memory(p).powers().size());
                for (LivingEntity friend : friends(p, at, 12)) {
                    heal(friend, 4 + remembered);
                    cleanse(friend);
                    piece(p, Piece.KINDRED_LIGHT, friend.position(), friend.position(), 1, friend);
                }
                sound(level, at, SoundEvents.PLAYER_LEVELUP, 0.6F, 1.6F);
            }
            // ---------------------------------------------------------- Technopathy
            case "drone_swarm" -> {
                piece(p, Piece.DRONE_SWARM, at, at, 1, p);
                sound(level, at, SoundEvents.BEACON_ACTIVATE, 0.8F, 2);
                StyleRuntime.run(p, 160, age -> {
                    if (age < 10 || age % 15 != 0) return;
                    int n = 0;
                    for (LivingEntity t : foes(p, p.position(), 12)) {
                        if (n >= 3 || !p.hasLineOfSight(t)) continue;
                        double ang = (age / 15.0 + n) * 2.1;
                        Vec3 from = p.position().add(Math.cos(ang) * 1.8, 2, Math.sin(ang) * 1.8);
                        piece(p, Piece.DRONE_SHOT, from, t.getEyePosition().subtract(0, 0.3, 0), 1, null);
                        hurt(p, t, 2.5F + lvl * 0.5F);
                        n++;
                    }
                    if (n > 0) sound(level, p.position(), SoundEvents.DISPENSER_LAUNCH, 0.5F, 2);
                });
            }
            case "firewall" -> {
                Vec3 centre = at.add(f.scale(2.5)), side = new Vec3(-f.z, 0, f.x);
                piece(p, Piece.FIREWALL, at, at.add(f), 1, null);
                sound(level, centre, SoundEvents.BEACON_ACTIVATE, 1, 1.8F);
                java.util.Map<LivingEntity, Integer> stung = new java.util.HashMap<>();
                StyleRuntime.run(p, 200, age -> {
                    for (Projectile shot : incoming(p, centre.add(0, 1.5, 0), 4)) {
                        Vec3 d = shot.position().subtract(centre);
                        if (Math.abs(d.dot(f)) < 1.2 && Math.abs(d.dot(side)) < 2.6 && d.y > -0.5 && d.y < 3.5) shot.discard();
                    }
                    for (LivingEntity t : foes(p, centre, 3.5)) {
                        Vec3 d = t.position().subtract(centre);
                        if (Math.abs(d.dot(f)) > 1 || Math.abs(d.dot(side)) > 2.6) continue;
                        if (age - stung.getOrDefault(t, -40) >= 20) { hurt(p, t, 3); stung.put(t, age); }
                        push(t, f.scale(0.7).add(0, 0.15, 0));
                    }
                });
            }
            case "overclock" -> {
                for (LivingEntity friend : friends(p, at, 12)) {
                    effect(friend, MobEffects.DIG_SPEED, 200, 1);
                    effect(friend, MobEffects.MOVEMENT_SPEED, 200, 0);
                    if (friend instanceof ServerPlayer fp) com.jayptucker.heroesevolved.energy.PlayerEnergyService.restore(fp, 10);
                    piece(p, Piece.OVERCLOCK, friend.position(), friend.position(), 1, friend);
                }
                sound(level, at, SoundEvents.BEACON_POWER_SELECT, 1, 2);
            }
            // ---------------------------------------------------------- Matter Assembly
            case "forge_cannon" -> {
                LivingEntity mark = Targeting.livingEntityInSight(p, 32, t -> isFoe(p, t));
                Vec3 point = mark != null ? mark.getEyePosition() : aim(p, 32).add(0, 0.5, 0);
                piece(p, Piece.FORGE_CANNON, at, point, 1, p);
                sound(level, at, SoundEvents.AMETHYST_CLUSTER_PLACE, 1.5F, 0.8F);
                StyleRuntime.run(p, 28, age -> {
                    if (age < 8 || (age - 8) % 5 != 0) return;
                    Vec3 target = mark != null && mark.isAlive() ? mark.getEyePosition().subtract(0, 0.3, 0) : point;
                    Vec3 muzzle = p.position().add(0, 1.9, 0).add(new Vec3(-facing(p).z, 0, facing(p).x).scale(0.7))
                            .add(target.subtract(p.getEyePosition()).normalize().scale(1.3));
                    piece(p, Piece.FORGE_SLUG, muzzle, target, 1, null);
                    for (LivingEntity t : foes(p, target, 1.6)) hurt(p, t, 5 + lvl);
                    sound(level, muzzle, SoundEvents.AMETHYST_BLOCK_BREAK, 1, 1.4F);
                });
            }
            case "bulwark_lattice" -> {
                piece(p, Piece.BULWARK_LATTICE, at, at, 1, p);
                effect(p, MobEffects.ABSORPTION, 160, 1);
                sound(level, at, SoundEvents.AMETHYST_BLOCK_PLACE, 1.5F, 1);
                StyleRuntime.run(p, 160, age -> {
                    if (destroy(p, p.position().add(0, 1, 0), 2.6) > 0) sound(level, p.position(), SoundEvents.AMETHYST_BLOCK_HIT, 1, 1.2F);
                });
            }
            case "mending_lattice" -> {
                for (LivingEntity friend : friends(p, at, 10)) {
                    effect(friend, MobEffects.ABSORPTION, 160, 0);
                    effect(friend, MobEffects.REGENERATION, 160, 0);
                    piece(p, Piece.MENDING_LATTICE, friend.position(), friend.position(), 1, friend);
                }
                sound(level, at, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.5F, 1.2F);
            }
            default -> { return false; }
        }
        return true;
    }

    /** The loop closes: back to the health they had when it opened, harmful effects undone. */
    static void closeLoop(ServerPlayer p, float health) {
        if (p.getHealth() < health) p.setHealth(health);
        cleanse(p);
        sound(p.serverLevel(), p.position(), ModSounds.TIME_UNSLOW.get(), 1, 1.4F);
    }

    private static Vec3 facingOf(LivingEntity e) {
        Vec3 look = e.getLookAngle().multiply(1, 0, 1);
        return look.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : look.normalize();
    }

    private static float smooth(float a, float b, float x) {
        float k = Mth.clamp((x - a) / (b - a), 0, 1);
        return k * k * (3 - 2 * k);
    }
}
