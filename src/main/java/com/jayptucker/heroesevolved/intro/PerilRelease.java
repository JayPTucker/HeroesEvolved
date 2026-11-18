package com.jayptucker.heroesevolved.intro;

import com.jayptucker.heroesevolved.cryo.CryoService;
import com.jayptucker.heroesevolved.cryo.TemporaryBlocks;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.effects.WorldMutationPolicy;
import com.jayptucker.heroesevolved.intro.Danger.Peril;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The part of a danger awakening that answers what was actually about to kill someone, after the power's own
 * release: the creeper about to go off, the ground coming up too fast, the lava, the water. Fights are answered
 * by the releases themselves ({@link PowerIntroService}, {@link WardRelease}); Space-Time answers everything by
 * stopping time ({@link TimeAwakening}). Each power only gets here for a danger it answers ({@link Danger#answers}).
 */
final class PerilRelease {
    /** How much lava one awakening can turn to stone. */
    private static final int QUENCH_LIMIT = 160;

    private PerilRelease() {}

    /** {@code carried} is true when the release is a rescue run or airlift that takes them out of it anyway. */
    static void apply(String power, ServerPlayer owner, LivingEntity saved, Peril peril, boolean carried) {
        switch (peril) {
            case BLAST -> blast(power, owner, saved, carried);
            case FALL -> fall(power, owner, saved, carried);
            case FIRE -> fire(power, owner, saved);
            case DROWNING -> drowning(power, saved);
            default -> { }
        }
    }

    // ------------------------------------------------------------------
    // The blast
    // ------------------------------------------------------------------

    private static void blast(String power, ServerPlayer owner, LivingEntity saved, boolean carried) {
        ServerLevel level = owner.serverLevel();
        // A runner or a flyer is already taking them out of it; let it go off behind them.
        if (carried) return;
        List<Entity> explosives = new ArrayList<>();
        explosives.addAll(level.getEntitiesOfClass(Creeper.class, saved.getBoundingBox().inflate(10)));
        explosives.addAll(level.getEntitiesOfClass(PrimedTnt.class, saved.getBoundingBox().inflate(10)));
        switch (power) {
            case "telekinesis" -> {
                // Picked up and thrown at the sky, where it can go off without anyone under it.
                for (Entity explosive : explosives) {
                    Vec3 away = away(explosive, saved);
                    if (explosive instanceof PrimedTnt tnt) tnt.setFuse(Math.max(tnt.getFuse(), 25));
                    if (explosive instanceof Creeper creeper) creeper.setSwellDir(-1);
                    Danger.push(explosive, away.scale(0.7).add(0, 1.8, 0));
                    AbilityEffects.send(level, Kind.PSYCHIC_BIND, explosive.position(), explosive.position().add(0, 1, 0), 1);
                }
            }
            case "cryokinesis" -> {
                // The fuse freezes out: creepers locked in ice with their fuse back at the start, TNT snuffed.
                for (Entity explosive : explosives) {
                    if (explosive instanceof Creeper creeper) {
                        Danger.rewind(creeper);
                        if (!CryoService.freezeSolid(creeper, 160, owner)) CryoService.chill(creeper, 160, owner);
                    } else if (explosive instanceof PrimedTnt tnt) {
                        level.sendParticles(ParticleTypes.SNOWFLAKE, tnt.getX(), tnt.getY() + 0.5, tnt.getZ(), 30, 0.4, 0.4, 0.4, 0.05);
                        tnt.discard();
                    }
                }
                sound(level, saved, SoundEvents.GLASS_BREAK, 1.2F, 0.6F);
            }
            case "sonic_resonance" -> {
                // Screamed away, too far to reach anyone when it goes.
                for (Entity explosive : explosives) Danger.push(explosive, away(explosive, saved).scale(2.2).add(0, 0.6, 0));
            }
            case "phasing" -> {
                // A shell of rock slams up round them and takes the blast.
                com.jayptucker.heroesevolved.phasing.EarthGeometry.show(level, com.jayptucker.heroesevolved.styles.Piece.EARTH_TENT,
                        saved.position(), saved.position(), 1.6F, null, null);
            }
            default -> {
                Danger.disarm(saved, 8);
                if (saved != owner) Danger.disarm(owner, 8);
            }
        }
    }

    // ------------------------------------------------------------------
    // The fall
    // ------------------------------------------------------------------

    private static void fall(String power, ServerPlayer owner, LivingEntity saved, boolean carried) {
        if (carried) return;
        ServerLevel level = owner.serverLevel();
        saved.resetFallDistance();
        BlockPos landing = landing(saved);
        switch (power) {
            case "telekinesis" -> {
                // Held in mid-air, then let down gently.
                Danger.push(saved, Vec3.ZERO);
                saved.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 160));
                AbilityEffects.send(level, Kind.PSYCHIC_BIND, saved.position(), saved.position().add(0, 1, 0),
                        Math.max(0.6, saved.getBbWidth()));
            }
            case "cryokinesis" -> {
                // A drift of powder snow where they were going to land.
                if (landing != null) carpet(level, landing, Blocks.POWDER_SNOW.defaultBlockState(), 20 * 20, owner);
                saved.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 30));
                if (landing != null) AbilityEffects.send(level, Kind.CRYO_AWAKEN_RELEASE, Vec3.atBottomCenterOf(landing),
                        Vec3.atBottomCenterOf(landing).add(0, 1, 0), 3);
            }
            case "hydrokinesis" -> {
                // A pool rises to catch them.
                if (landing != null) carpet(level, landing, Blocks.WATER.defaultBlockState(), 20 * 10, owner);
                saved.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 30));
            }
            case "matter_assembly" -> {
                // A platform assembles under their feet.
                BlockPos under = saved.blockPosition().below();
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                    TemporaryBlocks.place(level, under.offset(dx, 0, dz), Blocks.WAXED_COPPER_GRATE.defaultBlockState(), 20 * 12, owner);
                Danger.push(saved, Vec3.ZERO);
                AbilityEffects.send(level, Kind.MATTER_WEAVE, Vec3.atCenterOf(under), saved.position(), 2);
                sound(level, saved, SoundEvents.COPPER_PLACE, 1.4F, 0.8F);
            }
            default -> saved.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 40));
        }
    }

    // ------------------------------------------------------------------
    // Fire and lava
    // ------------------------------------------------------------------

    private static void fire(String power, ServerPlayer owner, LivingEntity saved) {
        ServerLevel level = owner.serverLevel();
        saved.clearFire();
        switch (power) {
            case "cryokinesis" -> {
                // The lava round them sets hard in an instant; the flames go out.
                quench(owner, saved, 4);
                level.sendParticles(ParticleTypes.SNOWFLAKE, saved.getX(), saved.getY() + 1, saved.getZ(), 80, 3, 1, 3, 0.05);
                sound(level, saved, SoundEvents.LAVA_EXTINGUISH, 1.6F, 0.7F);
            }
            case "hydrokinesis" -> {
                // Water from nowhere: the lava steams and turns to stone.
                quench(owner, saved, 4);
                level.sendParticles(ParticleTypes.SPLASH, saved.getX(), saved.getY() + 1, saved.getZ(), 120, 3, 1, 3, 0.2);
                level.sendParticles(ParticleTypes.CLOUD, saved.getX(), saved.getY() + 1, saved.getZ(), 40, 3, 1, 3, 0.05);
                sound(level, saved, SoundEvents.LAVA_EXTINGUISH, 1.6F, 0.5F);
            }
            case "pyrokinesis" -> {
                // The fire was never going to burn them. It bends away, and the lava lets them go.
                saved.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 60));
                if (saved.isInLava()) Danger.push(saved, new Vec3(0, 0.9, 0));
                level.sendParticles(ParticleTypes.FLAME, saved.getX(), saved.getY() + 1, saved.getZ(), 60, 1.2, 1, 1.2, 0.08);
            }
            case "regeneration" -> {
                saved.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 10));
                if (saved.isInLava()) Danger.push(saved, new Vec3(0, 0.8, 0));
            }
            default -> saved.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 100));
        }
    }

    /** Lava within {@code radius} turns to stone (obsidian where it pooled), fire goes out, and nobody is left inside. */
    private static void quench(ServerPlayer owner, LivingEntity saved, int radius) {
        ServerLevel level = owner.serverLevel();
        BlockPos center = saved.blockPosition();
        int changed = 0;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
            if (changed >= QUENCH_LIMIT) break;
            if (pos.distSqr(center) > radius * radius || !level.hasChunkAt(pos)) continue;
            BlockState state = level.getBlockState(pos);
            BlockState into = state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)
                    ? (state.getFluidState().isSource() ? Blocks.OBSIDIAN : Blocks.COBBLESTONE).defaultBlockState()
                    : state.is(BlockTags.FIRE) ? Blocks.AIR.defaultBlockState() : null;
            if (into != null && WorldMutationPolicy.place(owner, level, pos.immutable(), into)) changed++;
        }
        // Stand them on top of the new stone.
        for (int lift = 0; lift < 4 && !level.noCollision(saved); lift++) {
            Vec3 up = saved.position().add(0, 1, 0);
            if (saved instanceof ServerPlayer player) player.teleportTo(level, up.x, up.y, up.z, player.getYRot(), player.getXRot());
            saved.setPos(up);
        }
        Danger.push(saved, Vec3.ZERO);
    }

    // ------------------------------------------------------------------
    // Drowning
    // ------------------------------------------------------------------

    private static void drowning(String power, LivingEntity saved) {
        saved.setAirSupply(saved.getMaxAirSupply());
        if (power.equals("hydrokinesis")) {
            // The water stops fighting them. It carries them up instead.
            saved.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 20 * 60));
            saved.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 20 * 30));
            Danger.push(saved, new Vec3(0, 1.0, 0));
            if (saved.level() instanceof ServerLevel level)
                level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, saved.getX(), saved.getY(), saved.getZ(), 80, 0.5, 1.5, 0.5, 0.2);
        } else {
            saved.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 20 * 10));
        }
    }

    // ------------------------------------------------------------------

    /** The open cell just above the first solid ground under them, or null if there is none close. */
    private static BlockPos landing(LivingEntity saved) {
        BlockPos.MutableBlockPos cursor = saved.blockPosition().mutable();
        for (int step = 0; step < 48; step++, cursor.move(0, -1, 0)) {
            if (!saved.level().hasChunkAt(cursor) || saved.level().isOutsideBuildHeight(cursor)) return null;
            if (!saved.level().getBlockState(cursor).getCollisionShape(saved.level(), cursor).isEmpty()) return cursor.above().immutable();
        }
        return null;
    }

    /** A 3×3 of temporary blocks on the ground at {@code at}. */
    private static void carpet(ServerLevel level, BlockPos at, BlockState state, int ticks, ServerPlayer owner) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
            TemporaryBlocks.place(level, at.offset(dx, 0, dz), state, ticks, owner);
    }

    private static Vec3 away(Entity entity, LivingEntity from) {
        Vec3 away = entity.position().subtract(from.position()).multiply(1, 0, 1);
        return away.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : away.normalize();
    }

    private static void sound(ServerLevel level, Entity at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.blockPosition(), sound, SoundSource.PLAYERS, volume, pitch);
    }
}
