package com.jayptucker.heroesevolved.intro;

import com.jayptucker.heroesevolved.cryo.CryoService;
import com.jayptucker.heroesevolved.cryo.TemporaryBlocks;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.effects.PrivateGlow;
import com.jayptucker.heroesevolved.entity.QuakeDebrisEntity;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.radiation.RadiationService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * What every power's awakening does for an ally it woke to save (Superspeed and Flight carry them off
 * instead; see {@link SpeedRescue} and {@link FlightRescue}). The owner's own release still plays where
 * they stand; this reaches across to the ward and ends <em>their</em> fight in the power's own way.
 */
final class WardRelease {
    private WardRelease() {}

    /** While the power charges, a thread of it runs to the ward, so everyone can see who it is for. */
    static void charge(String power, ServerPlayer owner, LivingEntity ward, int age, Kind kind) {
        ServerLevel level = owner.serverLevel();
        if (age % 8 == 0) {
            AbilityEffects.send(level, Kind.PROPHECY_THREAD, owner.getEyePosition(), ward.getEyePosition(), 1);
            AbilityEffects.send(level, kind, ward.position(), ward.getEyePosition(), 0.5);
        }
        // The uncontrolled heat finds the ward's attackers too.
        if (power.equals("radiation") && age % 10 == 0) {
            for (LivingEntity threat : Danger.threats(owner, ward, 8)) {
                RadiationService.irradiate(threat, 80);
                threat.hurt(owner.damageSources().indirectMagic(owner, owner), 3);
                AbilityEffects.send(level, Kind.NUCLEAR_CHARGE, threat.position(), threat.getEyePosition(), 0.4);
            }
        }
    }

    static void apply(String power, ServerPlayer owner, LivingEntity ward) {
        ServerLevel level = owner.serverLevel();
        List<LivingEntity> threats = Danger.threats(owner, ward, 12);
        AbilityEffects.send(level, Kind.PROPHECY_THREAD, owner.getEyePosition(), ward.getEyePosition(), 1.5);
        switch (power) {
            case "invisibility" -> {
                // The ward fades with them; whatever was fighting them has nothing to aim at.
                if (ward instanceof ServerPlayer friend) {
                    com.jayptucker.heroesevolved.invisibility.InvisibilityService.fade(friend, 100, false);
                } else {
                    com.jayptucker.heroesevolved.invisibility.InvisibilityService.hide(ward, 100);
                }
                for (LivingEntity threat : threats) lose(threat);
                ward.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 1));
                AbilityEffects.send(level, Kind.OPTIC_BURST, ward.position(), ward.getEyePosition(), 6);
            }
            case "phasing" -> {
                // They go thin for five seconds: blows pass straight through, and the ripple shoves.
                PowerIntroService.shieldWard(ward, 100);
                for (LivingEntity threat : threats) shove(threat, ward.position(), 1.1, 0.4);
                AbilityEffects.send(level, Kind.DENSITY_RIPPLE, ward.position(), ward.getEyePosition(), 6);
                AbilityEffects.send(level, Kind.DENSITY_GHOST, ward.position(), ward.getEyePosition(), 1);
                sound(level, ward, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), 1.2F, 1.4F);
            }
            case "empathic_mimicry" -> {
                // Their attackers' strength drains through the owner into the ward.
                for (LivingEntity threat : Danger.threats(owner, ward, 8)) {
                    threat.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 1));
                    AbilityEffects.send(level, Kind.PROPHECY_THREAD, threat.getEyePosition(), ward.getEyePosition(), 1);
                    shove(threat, ward.position(), 0.8, 0.3);
                }
                ward.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
                ward.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200, 0));
            }
            case "telepathy" -> {
                // A psychic shout into every mind attacking them.
                for (LivingEntity threat : threats) {
                    lose(threat);
                    threat.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1));
                    threat.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
                    threat.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0));
                    PrivateGlow.glow(owner, threat, 120);
                }
                AbilityEffects.send(level, Kind.PSYCHIC_PUSH, ward.position(), ward.position().add(0, 1, 0), 10);
            }
            case "cryokinesis" -> {
                // Everything on them freezes solid where it stands.
                for (LivingEntity threat : Danger.threats(owner, ward, 9)) {
                    if (!CryoService.freezeSolid(threat, 120, owner)) CryoService.chill(threat, 120, owner);
                }
                AbilityEffects.send(level, Kind.CRYO_AWAKEN_RELEASE, ward.position(), ward.getEyePosition(), 7);
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.SNOWFLAKE,
                        ward.getX(), ward.getY() + 1, ward.getZ(), 120, 4, 1.5, 4, 0.1);
                sound(level, ward, SoundEvents.GLASS_BREAK, 1.4F, 0.5F);
            }
            case "sonic_resonance" -> {
                // The scream is aimed: a wall of sound lands on their attackers and hurls them off.
                for (LivingEntity threat : Danger.threats(owner, ward, 10)) shove(threat, ward.position(), 1.3, 0.35);
                for (Projectile shot : level.getEntitiesOfClass(Projectile.class, ward.getBoundingBox().inflate(8),
                        shot -> shot.getDeltaMovement().dot(ward.position().subtract(shot.position())) > 0)) {
                    shot.setDeltaMovement(shot.getDeltaMovement().scale(-0.6));
                    shot.hurtMarked = true;
                }
                AbilityEffects.send(level, Kind.SONIC_BURST, ward.position(), ward.getEyePosition(), 10);
                sound(level, ward, SoundEvents.WARDEN_SONIC_BOOM, 1.2F, 1.3F);
            }
            case "pyrokinesis" -> com.jayptucker.heroesevolved.pyro.PyroService.awaken(owner, ward.position());
            case "quake" -> {
                // A fault line runs from the owner to the ward and erupts under whatever is on them.
                fissure(owner, ward.position());
                for (LivingEntity threat : Danger.threats(owner, ward, 8)) {
                    threat.hurt(owner.damageSources().playerAttack(owner), 9);
                    shove(threat, ward.position(), 1.2, 0.9);
                }
                AbilityEffects.send(level, Kind.QUAKE_AWAKEN_RELEASE, ward.position(), ward.getEyePosition(), 6);
                CameraShakePayload.sendAround(level, ward.position(), 32, 0.9F, 30);
                sound(level, ward, SoundEvents.GENERIC_EXPLODE.value(), 2, 0.5F);
            }
            case "electrokinesis" -> {
                // Lightning picks out every one of their attackers.
                int struck = 0;
                for (LivingEntity threat : Danger.threats(owner, ward, 10)) {
                    if (struck++ >= 8) break;
                    lightning(level, threat.position());
                    threat.hurt(owner.damageSources().playerAttack(owner), 8);
                    AbilityEffects.send(level, Kind.ELECTRIC_ARC, owner.getEyePosition(), threat.getEyePosition(), 1.5);
                    shove(threat, ward.position(), 0.9, 0.3);
                }
                AbilityEffects.send(level, Kind.ELECTRIC_AWAKEN_RELEASE, ward.position(), ward.getEyePosition(), 6);
            }
            case "telekinesis" -> {
                // Their attackers are flung away; the ward is lifted out and carried to the owner (aftermath).
                for (LivingEntity threat : threats) {
                    shove(threat, ward.position(), 1.2, 0.5);
                    threat.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 80));
                }
                AbilityEffects.send(level, Kind.PSYCHIC_PUSH, ward.position(), ward.position().add(0, 1, 0), 9);
            }
            case "regeneration" -> {
                ward.setHealth(ward.getMaxHealth());
                ward.clearFire();
                for (MobEffectInstance effect : List.copyOf(ward.getActiveEffects())) {
                    if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) ward.removeEffect(effect.getEffect());
                }
                AbilityEffects.send(level, Kind.BLOOD_THREAD, owner.getEyePosition(), ward.getEyePosition(), 1.5);
                AbilityEffects.send(level, Kind.REGEN_AWAKEN_RELEASE, ward.position(), ward.getEyePosition(), 4);
            }
            case "technopathy" -> {
                // A diagnostic lock on their attackers, and every arrow in flight at them turned aside.
                for (LivingEntity threat : threats) {
                    threat.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 2));
                    PrivateGlow.glow(owner, threat, 160);
                    AbilityEffects.send(level, Kind.TECH_LINK, owner.getEyePosition(), threat.getEyePosition(), .8);
                }
                for (Projectile shot : level.getEntitiesOfClass(Projectile.class, ward.getBoundingBox().inflate(8),
                        shot -> shot.getDeltaMovement().dot(ward.position().subtract(shot.position())) > 0)) {
                    shot.setDeltaMovement(new Vec3(0, -0.1, 0));
                    shot.hurtMarked = true;
                }
                AbilityEffects.send(level, Kind.TECH_PULSE, ward.position(), ward.getEyePosition(), 6);
            }
            case "matter_assembly" -> {
                // Shove them back; a lattice rises round the ward once the space is clear (aftermath).
                for (LivingEntity threat : Danger.threats(owner, ward, 7)) shove(threat, ward.position(), 1.4, 0.3);
                AbilityEffects.send(level, Kind.MATTER_SHIELD, ward.position().add(0, 1, 0), ward.getEyePosition(), 4);
            }
            default -> { }
        }
    }

    static void aftermath(String power, ServerPlayer owner, LivingEntity ward, int tick) {
        if (!ward.isAlive() || ward.level() != owner.level()) return;
        ServerLevel level = owner.serverLevel();
        switch (power) {
            case "telekinesis" -> {
                if (tick >= 30) return;
                Vec3 hold = owner.position().add(owner.getLookAngle().multiply(1, 0, 1).normalize().scale(2)).add(0, 0.6, 0);
                Vec3 offset = hold.subtract(ward.position());
                if (offset.lengthSqr() < 2.25) {
                    push(ward, Vec3.ZERO);
                    return;
                }
                push(ward, offset.normalize().scale(Math.min(1.4, offset.length() * 0.35)).add(0, tick < 6 ? 0.25 : 0.04, 0));
                ward.resetFallDistance();
                if (tick % 6 == 0) AbilityEffects.send(level, Kind.PSYCHIC_BIND, ward.position(), ward.position().add(0, 1, 0),
                        Math.max(0.6, ward.getBbWidth()));
            }
            case "matter_assembly" -> {
                // Walls rise after the shove, so nothing is caught inside them.
                if (tick != 3) return;
                BlockPos center = ward.blockPosition();
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != 2) continue;
                        for (int dy = 0; dy <= 2; dy++) {
                            TemporaryBlocks.place(level, center.offset(dx, dy, dz),
                                    Blocks.WAXED_COPPER_GRATE.defaultBlockState(), 20 * 8, owner);
                        }
                    }
                }
                sound(level, ward, SoundEvents.COPPER_PLACE, 1.4F, 0.7F);
            }
            default -> { }
        }
    }

    private static void fissure(ServerPlayer owner, Vec3 to) {
        Vec3 from = owner.position();
        Vec3 line = to.subtract(from).multiply(1, 0, 1);
        double length = line.length();
        if (length < 1) return;
        Vec3 step = line.normalize();
        for (double along = 2; along < length; along += 2.5) {
            Vec3 at = from.add(step.scale(along));
            BlockPos ground = BlockPos.containing(at).below();
            var state = owner.serverLevel().getBlockState(ground);
            QuakeDebrisEntity.launch(owner.serverLevel(), state.isAir() ? Blocks.STONE.defaultBlockState() : state,
                    Vec3.atCenterOf(ground).add(0, 0.8, 0), new Vec3(0, 0.45, 0));
        }
    }

    private static void lightning(ServerLevel level, Vec3 at) {
        var bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) return;
        bolt.moveTo(at);
        bolt.setVisualOnly(true);
        level.addFreshEntity(bolt);
    }

    private static void lose(LivingEntity threat) {
        if (threat instanceof Mob mob) mob.setTarget(null);
    }

    private static void shove(Entity entity, Vec3 from, double force, double lift) {
        Vec3 away = entity.position().subtract(from).multiply(1, 0, 1);
        away = away.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : away.normalize();
        push(entity, away.scale(force).add(0, lift, 0));
    }

    private static void push(Entity entity, Vec3 velocity) {
        entity.setDeltaMovement(velocity);
        entity.hurtMarked = true;
        if (entity instanceof ServerPlayer player) player.connection.send(new ClientboundSetEntityMotionPacket(player));
    }

    private static void sound(ServerLevel level, Entity at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.blockPosition(), sound, SoundSource.PLAYERS, volume, pitch);
    }
}
