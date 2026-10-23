package com.jayptucker.heroesevolved.quake;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.config.HeroesEvolvedConfig;
import com.jayptucker.heroesevolved.cooldown.CooldownService;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.PlayerPowerSyncService;
import com.jayptucker.heroesevolved.entity.QuakeDebrisEntity;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.events.EclipseService;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.progression.MasteryService;
import com.jayptucker.heroesevolved.time.TemporalEntityEchoService;
import com.jayptucker.heroesevolved.time.TemporalSnapshotService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Vibration manipulation, after Daisy "Quake" Johnson. Every power here is
 * server-authoritative; clients only receive particles, sounds, debris and
 * camera shake.
 */
public final class QuakeService {
    private static final double WAVE_SPEED_BLOCKS_PER_TICK = 0.8D;
    private static final int TREMOR_REFRESH_TICKS = 5;
    private static final int PLUNGE_TIMEOUT_TICKS = 100;

    private static final List<SeismicWave> WAVES = new ArrayList<>();
    private static final Map<UUID, Earthquake> EARTHQUAKES = new HashMap<>();
    private static final Map<UUID, TremorSense> TREMOR_SENSES = new HashMap<>();
    private static final Map<UUID, Plunge> PLUNGES = new HashMap<>();
    private static final Map<UUID, Blast> BLASTS = new HashMap<>();
    private static final int BLAST_MAXIMUM_TICKS = 20 * 15;
    public static final net.minecraft.resources.ResourceLocation QUAKE_BLAST_ID =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                    com.jayptucker.heroesevolved.HeroesEvolved.MOD_ID, "quake_blast");
    // Players who propelled themselves with vibrations; their next landing
    // is fully cushioned.
    private static final Set<UUID> CUSHIONED_LANDINGS = new HashSet<>();

    private QuakeService() {
    }

    // ------------------------------------------------------------------
    // Shockwave
    // ------------------------------------------------------------------

    /**
     * A focused vibration blast from the palms. It hurls whatever it hits,
     * turns arrows back on their archer, and shatters glass. Aimed at the
     * ground close by, the recoil launches Quake into the air instead.
     */
    public static void shockwave(ServerPlayer player, int powerLevel) {
        ServerLevel level = player.serverLevel();
        Vec3 origin = player.getEyePosition();
        Vec3 direction = player.getLookAngle().normalize();
        double range = 6.0D + powerLevel * 2.0D;
        float damage = 3.0F + powerLevel * 1.5F;
        double knockback = 1.0D + powerLevel * 0.3D;

        BlockHitResult hit = level.clip(new ClipContext(
                origin, origin.add(direction.scale(range)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player
        ));
        double length = hit.getType() == HitResult.Type.BLOCK
                ? hit.getLocation().distanceTo(origin)
                : range;
        Vec3 end = origin.add(direction.scale(length));

        spawnBeamRipples(level, origin, direction, length);
        hitEntitiesAlongBeam(player, origin, direction, length, damage, knockback);
        shatterAlongBeam(player, origin, direction, length);

        if (hit.getType() == HitResult.Type.BLOCK) {
            burstDebris(level, hit.getBlockPos(), direction.reverse(),
                    3 + powerLevel, 0.45D);
        }

        // Daisy flies by firing vibrations into the ground beneath her.
        boolean propulsion = hit.getType() == HitResult.Type.BLOCK
                && player.getXRot() > 50.0F
                && length < 4.5D;
        Vec3 recoil = direction.reverse().scale(
                propulsion ? 1.3D + powerLevel * 0.12D : 0.12D
        );
        if (propulsion) {
            recoil = recoil.add(0.0D, 0.35D, 0.0D);
            CUSHIONED_LANDINGS.add(player.getUUID());
        }
        setVelocity(player, player.getDeltaMovement().add(recoil));

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS,
                0.7F, 1.55F + level.getRandom().nextFloat() * 0.15F);
        CameraShakePayload.sendAround(level, end, 10.0D, 0.9F, 8);
        // A single Shockwave is thrown from one palm.
        ArmPosePayload.show(player, ArmPose.ONE_HAND, 8);
        MasteryService.markPowerUse(player, ModAbilities.QUAKE_ID);
    }

    private static void spawnBeamRipples(
            ServerLevel level,
            Vec3 origin,
            Vec3 direction,
            double length
    ) {
        AbilityEffects.send(level, Kind.QUAKE_BEAM, origin,
                origin.add(direction.scale(length)), 1.0D);
    }

    private static void hitEntitiesAlongBeam(
            ServerPlayer player,
            Vec3 origin,
            Vec3 direction,
            double length,
            float damage,
            double knockback
    ) {
        ServerLevel level = player.serverLevel();
        Vec3 end = origin.add(direction.scale(length));
        AABB area = new AABB(origin, end).inflate(1.5D);

        for (Entity target : level.getEntities(player, area, Entity::isAlive)) {
            Vec3 toTarget = target.getBoundingBox().getCenter().subtract(origin);
            double along = toTarget.dot(direction);
            if (along < 0.0D || along > length + 0.5D) {
                continue;
            }
            double offBeam = toTarget.subtract(direction.scale(along)).length();
            if (offBeam > 1.2D + target.getBbWidth() / 2.0D) {
                continue;
            }

            // Incoming projectiles are caught by the wave and flung back.
            if (target instanceof Projectile projectile) {
                double speed = Math.max(1.6D, projectile.getDeltaMovement().length());
                projectile.setOwner(player);
                projectile.setDeltaMovement(direction.scale(speed));
                level.getChunkSource().broadcastAndSend(projectile,
                        new ClientboundSetEntityMotionPacket(projectile));
                continue;
            }

            if (!(target instanceof LivingEntity living) || target.isSpectator()) {
                continue;
            }

            if (TemporalEntityEchoService.isReplaying(living)) {
                TemporalEntityEchoService.disturb(living, player);
            }
            // The blast loses some force over distance.
            double falloff = 1.0D - 0.4D * (along / (length + 0.5D));
            living.hurt(player.damageSources().playerAttack(player),
                    (float) (damage * falloff));
            setVelocity(living, direction.scale(knockback * falloff)
                    .add(0.0D, 0.35D, 0.0D));
        }
    }

    private static void shatterAlongBeam(
            ServerPlayer player,
            Vec3 origin,
            Vec3 direction,
            double length
    ) {
        Set<BlockPos> checked = new HashSet<>();
        for (double distance = 0.5D; distance <= length + 1.0D; distance += 0.5D) {
            BlockPos center = BlockPos.containing(origin.add(direction.scale(distance)));
            for (BlockPos position : BlockPos.betweenClosed(
                    center.offset(-1, -1, -1), center.offset(1, 1, 1))) {
                if (checked.add(position.immutable())) {
                    tryShatter(player.serverLevel(), position, player);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Quake Blast (held)
    // ------------------------------------------------------------------

    public static boolean isBlasting(ServerPlayer player) {
        return BLASTS.containsKey(player.getUUID());
    }

    /**
     * Both palms out, pouring vibration into a continuous beam for as long
     * as the key is held and energy lasts.
     */
    public static void startQuakeBlast(ServerPlayer player, int powerLevel) {
        BLASTS.put(player.getUUID(), new Blast(powerLevel));
        Vec3 palms = player.getEyePosition().add(player.getLookAngle().scale(0.6D))
                .add(0.0D, -0.25D, 0.0D);
        AbilityEffects.send(player.serverLevel(), Kind.QUAKE_CONTACT,
                palms, palms.add(player.getLookAngle()), 0.6D);
        ArmPosePayload.show(player, ArmPose.TWO_HANDS_STRAIN, 15);
        player.serverLevel().playSound(null, player.getX(), player.getY(),
                player.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS,
                0.9F, 1.5F);
        MasteryService.markPowerUse(player, ModAbilities.QUAKE_ID);
    }

    /** Ends the beam: on release, exhaustion, or its time limit. */
    public static void stopQuakeBlast(ServerPlayer player, boolean exhausted) {
        if (BLASTS.remove(player.getUUID()) == null) {
            return;
        }

        ArmPosePayload.clear(player);
        Vec3 palms = player.getEyePosition().add(player.getLookAngle().scale(0.6D))
                .add(0.0D, -0.25D, 0.0D);
        AbilityEffects.send(player.serverLevel(), Kind.QUAKE_CONTACT,
                palms, palms.add(player.getLookAngle()), 0.4D);
        // The cooldown starts when the beam ends, not when it begins.
        CooldownService.startCooldown(player, QUAKE_BLAST_ID,
                HeroesEvolvedConfig.COMMON.quakeBlastCooldownTicks.get());
        PlayerPowerSyncService.sync(player);
        if (exhausted) {
            player.displayClientMessage(Component.literal(
                    "Your arms shake and the blast gutters out.")
                    .withStyle(ChatFormatting.RED), true);
        }
    }

    private static void tickQuakeBlast(
            ServerPlayer player,
            Blast blast,
            long gameTime
    ) {
        ServerLevel level = player.serverLevel();
        blast.ageTicks++;

        // Stamina drains in half-second steps while the beam is held.
        if (blast.ageTicks % 10 == 0) {
            int drain = Math.max(1,
                    HeroesEvolvedConfig.COMMON.quakeBlastDrainPerSecond.get() / 2);
            if (!PlayerEnergyService.tryConsume(player, drain)) {
                stopQuakeBlast(player, true);
                return;
            }
            MasteryService.awardEnergySpent(player, drain);
            ArmPosePayload.show(player, ArmPose.TWO_HANDS_STRAIN, 15);
            // Daisy plants her feet to hold a sustained blast.
            player.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN, 15, 1, false, false));
        }

        Vec3 origin = player.getEyePosition();
        Vec3 direction = player.getLookAngle().normalize();
        double range = 10.0D + blast.powerLevel;
        BlockHitResult hit = level.clip(new ClipContext(
                origin, origin.add(direction.scale(range)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player
        ));
        boolean hitBlock = hit.getType() == HitResult.Type.BLOCK;
        double length = hitBlock ? hit.getLocation().distanceTo(origin) : range;

        if (blast.ageTicks % 3 == 0) {
            Vec3 palms = origin.add(direction.scale(Math.min(0.6D, length * 0.5D)))
                    .add(0.0D, -0.25D, 0.0D);
            AbilityEffects.send(level, Kind.QUAKE_CHANNEL, palms,
                    origin.add(direction.scale(length)), 0.8D);
            if (hitBlock && blast.ageTicks % 6 == 0) {
                Vec3 normal = Vec3.atLowerCornerOf(hit.getDirection().getNormal());
                AbilityEffects.send(level, Kind.QUAKE_CONTACT,
                        hit.getLocation(), hit.getLocation().add(normal), 0.9D);
            }
        }
        if (blast.ageTicks % 5 == 0) {
            hitEntitiesAlongBeam(player, origin, direction, length,
                    1.5F + blast.powerLevel * 0.5F, 0.7D);
        }
        if (blast.ageTicks % 10 == 0) {
            shatterAlongBeam(player, origin, direction, length);
            if (hitBlock) {
                burstDebris(level, hit.getBlockPos(), direction.reverse(), 2, 0.4D);
            }
        }
        if (blast.ageTicks % 8 == 0) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS,
                    0.35F, 1.8F + level.getRandom().nextFloat() * 0.2F);
            CameraShakePayload.sendAround(level, player.position(), 6.0D, 0.6F, 10);
        }

        // Pointed at the ground below, the beam holds Quake aloft.
        if (hitBlock && player.getXRot() > 50.0F && length < 5.5D) {
            Vec3 velocity = player.getDeltaMovement();
            setVelocity(player, new Vec3(velocity.x * 0.9D,
                    Math.max(velocity.y, 0.28D + blast.powerLevel * 0.02D),
                    velocity.z * 0.9D));
            player.resetFallDistance();
            CUSHIONED_LANDINGS.add(player.getUUID());
        } else if (!player.onGround() && blast.ageTicks % 2 == 0) {
            // In the air, the recoil pushes Quake gently backward.
            setVelocity(player, player.getDeltaMovement()
                    .add(direction.reverse().scale(0.04D)));
        }

        if (blast.ageTicks >= BLAST_MAXIMUM_TICKS) {
            stopQuakeBlast(player, true);
        }
    }

    // ------------------------------------------------------------------
    // Seismic Slam
    // ------------------------------------------------------------------

    /**
     * A punch into the ground that sends a ring of force rolling outward.
     * Used in midair it becomes a plunge: Quake drives herself down and the
     * landing hits much harder.
     */
    public static void slam(ServerPlayer player, int powerLevel) {
        ArmPosePayload.show(player, ArmPose.GROUND, player.onGround() ? 12 : 40);
        boolean airborne = !player.onGround()
                && findSurface(player.serverLevel(), player.getBlockX(),
                player.getBlockZ(), player.getBlockY(), 2) == null;

        if (airborne) {
            PLUNGES.put(player.getUUID(), new Plunge(powerLevel,
                    player.serverLevel().getGameTime()));
            CUSHIONED_LANDINGS.add(player.getUUID());
            setVelocity(player, new Vec3(0.0D, -2.8D, 0.0D));
            player.serverLevel().playSound(null, player.getX(), player.getY(),
                    player.getZ(), SoundEvents.WARDEN_SONIC_CHARGE,
                    SoundSource.PLAYERS, 0.9F, 1.4F);
        } else {
            impact(player, powerLevel, 1.0D, true);
        }
        MasteryService.markPowerUse(player, ModAbilities.QUAKE_ID);
    }

    private static void impact(
            ServerPlayer player,
            int powerLevel,
            double power,
            boolean shatter
    ) {
        ServerLevel level = player.serverLevel();
        Vec3 center = player.position();
        double radius = (5.0D + powerLevel) * power;
        float damage = (float) ((4.0D + powerLevel) * power);
        double launch = (0.6D + powerLevel * 0.08D) * Math.min(power, 1.4D);

        WAVES.add(new SeismicWave(player.getUUID(), level, center, radius,
                damage, launch, shatter));

        BlockPos ground = BlockPos.containing(center).below();
        burstDebris(level, ground, new Vec3(0.0D, 1.0D, 0.0D),
                (int) (6 * power), 0.55D);
        AbilityEffects.send(level, Kind.QUAKE_IMPACT, center, center, radius);
        level.playSound(null, center.x, center.y, center.z,
                SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS,
                (float) (0.9D * power), 0.55F);
        level.playSound(null, center.x, center.y, center.z,
                SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.5F, 0.5F);
        CameraShakePayload.sendAround(level, center, radius * 2.5D,
                (float) (1.6D * power), 14);
    }

    // ------------------------------------------------------------------
    // Tremor Sense
    // ------------------------------------------------------------------

    /**
     * Quake feels the vibrations of every heartbeat around her. Living
     * creatures glow through walls, but only on her own screen.
     */
    public static void startTremorSense(ServerPlayer player, int powerLevel) {
        ArmPosePayload.show(player, ArmPose.TEMPLE, 30);
        int durationTicks = 20 * (8 + 4 * Math.max(0, powerLevel - 3));
        double radius = 24.0D + 8.0D * Math.max(0, powerLevel - 3);

        stopTremorSense(player);
        TREMOR_SENSES.put(player.getUUID(), new TremorSense(
                player.serverLevel().getGameTime() + durationTicks, radius
        ));

        ServerLevel level = player.serverLevel();
        com.jayptucker.heroesevolved.network.ScreenTintPayload.send(player,
                0x3040C0FF, durationTicks,
                com.jayptucker.heroesevolved.network.ScreenTintPayload.Style.EDGE);
        AbilityEffects.scan(player, radius);
        player.playNotifySound(SoundEvents.WARDEN_HEARTBEAT,
                SoundSource.PLAYERS, 1.0F, 1.0F);
        player.playNotifySound(SoundEvents.SCULK_CLICKING,
                SoundSource.PLAYERS, 0.8F, 0.7F);
        MasteryService.markPowerUse(player, ModAbilities.QUAKE_ID);
    }

    public static boolean hasTremorSense(ServerPlayer player) {
        return TREMOR_SENSES.containsKey(player.getUUID());
    }

    public static void stopTremorSense(ServerPlayer player) {
        TremorSense sense = TREMOR_SENSES.remove(player.getUUID());
        if (sense != null) {
            clearRevealed(player, sense);
            com.jayptucker.heroesevolved.network.ScreenTintPayload.send(player,
                    0x3040C0FF, 0,
                    com.jayptucker.heroesevolved.network.ScreenTintPayload.Style.EDGE);
        }
    }

    private static void tickTremorSense(
            ServerPlayer player,
            TremorSense sense,
            long gameTime
    ) {
        ServerLevel level = player.serverLevel();
        if (gameTime % TREMOR_REFRESH_TICKS == 0) {
            List<LivingEntity> sensed = level.getEntitiesOfClass(
                    LivingEntity.class,
                    player.getBoundingBox().inflate(sense.radius),
                    entity -> entity != player && entity.isAlive()
                            && !entity.isSpectator()
                            && entity.distanceToSqr(player)
                            <= sense.radius * sense.radius
            );

            Set<Integer> stillSensed = new HashSet<>();
            for (LivingEntity entity : sensed) {
                stillSensed.add(entity.getId());
                sendSharedFlags(player, entity, true);
            }
            for (Integer lostId : sense.revealedIds) {
                if (!stillSensed.contains(lostId)) {
                    Entity lost = level.getEntity(lostId);
                    if (lost != null) {
                        sendSharedFlags(player, lost, false);
                    }
                }
            }
            sense.revealedIds = stillSensed;

            if (gameTime % 20 == 0) {
                long hostile = sensed.stream()
                        .filter(entity -> entity instanceof Enemy).count();
                player.displayClientMessage(Component.literal(
                        "You feel " + sensed.size() + " heartbeat"
                                + (sensed.size() == 1 ? "" : "s")
                                + (hostile > 0 ? " (" + hostile + " hostile)" : "")
                ).withStyle(hostile > 0
                        ? ChatFormatting.RED : ChatFormatting.AQUA), true);
                // Heartbeats quicken when danger is close.
                player.playNotifySound(SoundEvents.WARDEN_HEARTBEAT,
                        SoundSource.PLAYERS, 0.55F,
                        hostile > 0 ? 1.35F : 1.0F);
            }
        }

        if (gameTime % 40 == 0) {
            AbilityEffects.scan(player, sense.radius);
        }
    }

    /**
     * Sends one player a private copy of an entity's shared flags with the
     * glowing bit changed. Vanilla renders the outline through walls, and no
     * other player sees it. The server re-sends real flags whenever they
     * change, so revealed entities are refreshed every few ticks.
     */
    private static void sendSharedFlags(
            ServerPlayer viewer,
            Entity entity,
            boolean reveal
    ) {
        byte flags = 0;
        if (entity.isOnFire()) {
            flags |= 1;
        }
        if (entity.isShiftKeyDown()) {
            flags |= 1 << 1;
        }
        if (entity.isSprinting()) {
            flags |= 1 << 3;
        }
        if (entity.isSwimming()) {
            flags |= 1 << 4;
        }
        if (entity.isInvisible()) {
            flags |= 1 << 5;
        }
        if (reveal || entity.isCurrentlyGlowing()) {
            flags |= 1 << 6;
        }
        if (entity instanceof LivingEntity living && living.isFallFlying()) {
            flags |= (byte) (1 << 7);
        }

        // Data id 0 is Entity's shared-flags byte.
        viewer.connection.send(new ClientboundSetEntityDataPacket(
                entity.getId(),
                List.of(new SynchedEntityData.DataValue<>(
                        0, EntityDataSerializers.BYTE, flags
                ))
        ));
    }

    private static void clearRevealed(ServerPlayer player, TremorSense sense) {
        for (Integer id : sense.revealedIds) {
            Entity entity = player.serverLevel().getEntity(id);
            if (entity != null) {
                sendSharedFlags(player, entity, false);
            }
        }
        sense.revealedIds = Set.of();
    }

    // ------------------------------------------------------------------
    // Earthquake
    // ------------------------------------------------------------------

    public static void startEarthquake(ServerPlayer player, int powerLevel) {
        startEarthquake(
                player,
                HeroesEvolvedConfig.COMMON.quakeEarthquakeRadius.get(),
                HeroesEvolvedConfig.COMMON.quakeEarthquakeDurationSeconds.get() * 20,
                2.0F + powerLevel * 0.5F,
                HeroesEvolvedConfig.COMMON.quakeShattersFragileBlocks.get()
        );
        ArmPosePayload.show(player, ArmPose.ARMS_WIDE,
                HeroesEvolvedConfig.COMMON.quakeEarthquakeDurationSeconds.get() * 20);
        player.sendSystemMessage(Component.literal(
                "The ground answers you."
        ).withStyle(ChatFormatting.DARK_AQUA));
        MasteryService.markPowerUse(player, ModAbilities.QUAKE_ID);
    }

    /**
     * Terrigenesis-style awakening: the new power escapes as a short,
     * involuntary tremor that cannot break anything.
     */
    public static void awaken(ServerPlayer player) {
        startEarthquake(player, 8.0D, 20 * 3, 1.0F, false);
        impact(player, 1, 0.8D, false);
        player.sendSystemMessage(Component.literal(
                "Everything around you is shaking... or is it you?"
        ).withStyle(ChatFormatting.DARK_AQUA));
    }

    public static boolean hasActiveEarthquake(ServerPlayer player) {
        return EARTHQUAKES.containsKey(player.getUUID());
    }

    /** Ends an Earthquake early, still releasing its aftershock. */
    public static void stopEarthquake(ServerPlayer player) {
        Earthquake quake = EARTHQUAKES.remove(player.getUUID());
        if (quake != null) {
            aftershock(player, quake);
        }
    }

    private static void startEarthquake(
            ServerPlayer player,
            double radius,
            int durationTicks,
            float damage,
            boolean shatter
    ) {
        ServerLevel level = player.serverLevel();
        Earthquake quake = new Earthquake(level, player.position(), radius,
                durationTicks, damage, shatter);
        if (shatter && !TemporalSnapshotService.isTemporalDimension(level)) {
            quake.fragileBlocks.addAll(findFragileBlocks(level,
                    BlockPos.containing(player.position()), (int) radius));
        }
        EARTHQUAKES.put(player.getUUID(), quake);
        AbilityEffects.send(level, Kind.QUAKE_PULSE, quake.center, quake.center, radius);

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WARDEN_EMERGE, SoundSource.PLAYERS, 1.5F, 0.6F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS,
                1.2F, 0.4F);
    }

    private static void tickEarthquake(
            ServerPlayer owner,
            Earthquake quake,
            long gameTime
    ) {
        ServerLevel level = quake.level;
        RandomSource random = level.getRandom();
        quake.ageTicks++;

        // The quake builds for a second before reaching full strength.
        double strength = Math.min(1.0D, quake.ageTicks / 20.0D);
        AABB area = new AABB(quake.center, quake.center)
                .inflate(quake.radius, 6.0D, quake.radius);

        for (LivingEntity entity : level.getEntitiesOfClass(
                LivingEntity.class, area,
                entity -> entity != owner && entity.isAlive()
                        && !entity.isSpectator()
                        && entity.position().distanceToSqr(quake.center)
                        <= quake.radius * quake.radius)) {
            if (!entity.onGround()) {
                continue;
            }

            // Ground-bound creatures are jostled off their feet.
            Vec3 jolt = new Vec3(
                    (random.nextDouble() - 0.5D) * 0.45D * strength,
                    random.nextFloat() < 0.15F ? 0.25D * strength : 0.0D,
                    (random.nextDouble() - 0.5D) * 0.45D * strength
            );
            setVelocity(entity, entity.getDeltaMovement().add(jolt));

            if (quake.ageTicks % 20 == 0) {
                entity.hurt(owner.damageSources().playerAttack(owner),
                        quake.damage);
                entity.addEffect(new MobEffectInstance(
                        MobEffects.MOVEMENT_SLOWDOWN, 30, 1));
                if (entity instanceof ServerPlayer) {
                    entity.addEffect(new MobEffectInstance(
                            MobEffects.CONFUSION, 80, 0));
                }
            }
        }

        // Ground erupts at random points across the quake.
        for (int burst = 0; burst < 4; burst++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double distance = Math.sqrt(random.nextDouble()) * quake.radius;
            int x = (int) Math.floor(quake.center.x + Math.cos(angle) * distance);
            int z = (int) Math.floor(quake.center.z + Math.sin(angle) * distance);
            BlockPos surface = findSurface(level, x, z,
                    (int) Math.floor(quake.center.y), 6);
            if (surface == null) {
                continue;
            }

            BlockState state = level.getBlockState(surface);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                    x + 0.5D, surface.getY() + 1.05D, z + 0.5D,
                    6, 0.35D, 0.05D, 0.35D, 0.1D);
            if (random.nextFloat() < 0.3F * strength) {
                QuakeDebrisEntity.launch(level, state,
                        Vec3.atBottomCenterOf(surface.above()),
                        new Vec3((random.nextDouble() - 0.5D) * 0.2D,
                                0.35D + random.nextDouble() * 0.45D,
                                (random.nextDouble() - 0.5D) * 0.2D));
            }
        }

        if (quake.ageTicks % 10 == 0) {
            drawFaultLine(level, quake, random);
            if (quake.ageTicks % 20 == 0) {
                AbilityEffects.send(level, Kind.QUAKE_PULSE,
                        quake.center, quake.center, quake.radius * strength);
            }
            level.playSound(null, quake.center.x, quake.center.y, quake.center.z,
                    SoundEvents.WARDEN_DIG, SoundSource.PLAYERS, 2.0F,
                    0.45F + random.nextFloat() * 0.1F);
            CameraShakePayload.sendAround(level, quake.center,
                    quake.radius * 2.0D, (float) (2.4D * strength), 16);
        }

        shatterSomeFragileBlocks(owner, quake, random);

        if (quake.ageTicks >= quake.durationTicks) {
            EARTHQUAKES.remove(owner.getUUID());
            aftershock(owner, quake);
        }
    }

    /** A crack of churned earth racing outward from the epicenter. */
    private static void drawFaultLine(
            ServerLevel level,
            Earthquake quake,
            RandomSource random
    ) {
        double angle = random.nextDouble() * Math.PI * 2.0D;
        for (double distance = 1.0D; distance <= quake.radius; distance += 0.7D) {
            double wobble = Math.sin(distance * 0.9D) * 0.35D;
            int x = (int) Math.floor(quake.center.x
                    + Math.cos(angle + wobble * 0.2D) * distance);
            int z = (int) Math.floor(quake.center.z
                    + Math.sin(angle + wobble * 0.2D) * distance);
            BlockPos surface = findSurface(level, x, z,
                    (int) Math.floor(quake.center.y), 6);
            if (surface != null) {
                level.sendParticles(
                        new BlockParticleOption(ParticleTypes.BLOCK,
                                level.getBlockState(surface)),
                        x + 0.5D, surface.getY() + 1.0D, z + 0.5D,
                        3, 0.15D, 0.02D, 0.15D, 0.15D);
                level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                        x + 0.5D, surface.getY() + 1.0D, z + 0.5D,
                        0, 0.0D, 0.03D, 0.0D, 1.0D);
            }
        }
    }

    /** Windows give way one after another as the shaking continues. */
    private static void shatterSomeFragileBlocks(
            ServerPlayer owner,
            Earthquake quake,
            RandomSource random
    ) {
        if (quake.fragileBlocks.isEmpty()) {
            return;
        }

        int remainingTicks = Math.max(1, quake.durationTicks - quake.ageTicks);
        // Spread breakage over the quake so about four in five panes go.
        double chance = 0.8D / remainingTicks * 2.0D;
        Iterator<BlockPos> iterator = quake.fragileBlocks.iterator();
        while (iterator.hasNext()) {
            BlockPos position = iterator.next();
            if (random.nextDouble() < chance) {
                tryShatter(quake.level, position, owner);
                iterator.remove();
            }
        }
    }

    private static void aftershock(ServerPlayer owner, Earthquake quake) {
        if (owner.level() != quake.level) {
            return;
        }

        WAVES.add(new SeismicWave(owner.getUUID(), quake.level, quake.center,
                quake.radius, quake.damage * 2.0F, 1.0D, quake.shatter));
        quake.level.playSound(null, quake.center.x, quake.center.y,
                quake.center.z, SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.PLAYERS, 2.0F, 0.35F);
        AbilityEffects.send(quake.level, Kind.QUAKE_IMPACT,
                quake.center, quake.center, quake.radius);
        CameraShakePayload.sendAround(quake.level, quake.center,
                quake.radius * 2.5D, 3.5F, 24);
    }

    // ------------------------------------------------------------------
    // Passives
    // ------------------------------------------------------------------

    /**
     * Quake softens every landing with a burst of vibration. Levels 1-4
     * reduce fall damage by 20% each; Level 5 negates it.
     */
    public static float fallDamageMultiplier(ServerPlayer player, int powerLevel) {
        if (CUSHIONED_LANDINGS.remove(player.getUUID())) {
            return 0.0F;
        }
        return Math.max(0.0F, 1.0F - 0.2F * powerLevel);
    }

    /** A long fall ends in a small shock ring that shoves nearby creatures. */
    public static void onCushionedLanding(
            ServerPlayer player,
            float fallDistance,
            int powerLevel
    ) {
        if (fallDistance < 5.0F) {
            return;
        }

        ServerLevel level = player.serverLevel();
        AbilityEffects.at(player, Kind.QUAKE_PULSE, 2.8D);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.4F, 1.9F);

        if (powerLevel < 3) {
            return;
        }
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(3.0D),
                entity -> entity != player && entity.isAlive())) {
            Vec3 away = entity.position().subtract(player.position())
                    .multiply(1.0D, 0.0D, 1.0D);
            if (away.lengthSqr() < 0.01D) {
                away = new Vec3(1.0D, 0.0D, 0.0D);
            }
            setVelocity(entity, away.normalize().scale(0.6D)
                    .add(0.0D, 0.3D, 0.0D));
        }
    }

    /**
     * Daisy's bones fracture when she pushes her power past its limit;
     * overexertion leaves Quake's arms too damaged to work properly.
     */
    public static void fractureArms(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(
                MobEffects.DIG_SLOWDOWN, 20 * 30, 1));
        player.addEffect(new MobEffectInstance(
                MobEffects.WEAKNESS, 20 * 30, 1));
        player.serverLevel().playSound(null, player.getX(), player.getY(),
                player.getZ(), SoundEvents.SKELETON_HURT, SoundSource.PLAYERS,
                0.8F, 0.6F);
        player.sendSystemMessage(Component.literal(
                "The bones in your arms crack under the strain."
        ).withStyle(ChatFormatting.DARK_RED));
    }

    public static void stopAll(ServerPlayer player) {
        stopQuakeBlast(player, false);
        EARTHQUAKES.remove(player.getUUID());
        PLUNGES.remove(player.getUUID());
        CUSHIONED_LANDINGS.remove(player.getUUID());
        stopTremorSense(player);
    }

    // ------------------------------------------------------------------
    // Ticking
    // ------------------------------------------------------------------

    public static void tick(MinecraftServer server) {
        long gameTime = server.overworld().getGameTime();

        var earthquakes = EARTHQUAKES.entrySet().iterator();
        while (earthquakes.hasNext()) {
            Map.Entry<UUID, Earthquake> entry = earthquakes.next();
            ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
            if (owner == null || !owner.isAlive()
                    || owner.level() != entry.getValue().level
                    || EclipseService.arePowersSuppressed(owner)) {
                earthquakes.remove();
            }
        }
        for (Map.Entry<UUID, Earthquake> entry : List.copyOf(EARTHQUAKES.entrySet())) {
            ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
            if (owner != null) {
                tickEarthquake(owner, entry.getValue(), gameTime);
            }
        }

        var senses = TREMOR_SENSES.entrySet().iterator();
        while (senses.hasNext()) {
            Map.Entry<UUID, TremorSense> entry = senses.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                senses.remove();
                continue;
            }
            if (gameTime >= entry.getValue().endGameTime
                    || EclipseService.arePowersSuppressed(player)) {
                clearRevealed(player, entry.getValue());
                senses.remove();
                continue;
            }
            tickTremorSense(player, entry.getValue(), gameTime);
        }

        var plunges = PLUNGES.entrySet().iterator();
        while (plunges.hasNext()) {
            Map.Entry<UUID, Plunge> entry = plunges.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null
                    || gameTime - entry.getValue().startedAt > PLUNGE_TIMEOUT_TICKS) {
                plunges.remove();
                continue;
            }
            if (player.onGround() || player.isInWater()) {
                plunges.remove();
                impact(player, entry.getValue().powerLevel, 1.5D, true);
            } else if (gameTime % 4 == 0) {
                AbilityEffects.at(player, Kind.QUAKE_PLUNGE, 1.0D);
            }
        }

        for (Map.Entry<UUID, Blast> entry : List.copyOf(BLASTS.entrySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                BLASTS.remove(entry.getKey());
            } else if (!player.isAlive()
                    || EclipseService.arePowersSuppressed(player)) {
                stopQuakeBlast(player, false);
            } else {
                tickQuakeBlast(player, entry.getValue(), gameTime);
            }
        }

        tickWaves(server);
    }

    private static void tickWaves(MinecraftServer server) {
        Iterator<SeismicWave> iterator = WAVES.iterator();
        while (iterator.hasNext()) {
            SeismicWave wave = iterator.next();
            double inner = wave.radius;
            wave.radius += WAVE_SPEED_BLOCKS_PER_TICK;
            ServerPlayer owner = server.getPlayerList().getPlayer(wave.ownerId);

            advanceWave(wave, owner, inner, Math.min(wave.radius, wave.maxRadius));
            if (wave.radius >= wave.maxRadius) {
                iterator.remove();
            }
        }
    }

    private static void advanceWave(
            SeismicWave wave,
            ServerPlayer owner,
            double inner,
            double outer
    ) {
        ServerLevel level = wave.level;
        RandomSource random = level.getRandom();
        int points = Math.max(8, (int) (Math.PI * 2.0D * outer));

        for (int point = 0; point < points; point++) {
            double angle = Math.PI * 2.0D * point / points;
            int x = (int) Math.floor(wave.center.x + Math.cos(angle) * outer);
            int z = (int) Math.floor(wave.center.z + Math.sin(angle) * outer);
            BlockPos surface = findSurface(level, x, z,
                    (int) Math.floor(wave.center.y), 4);
            if (surface == null) {
                continue;
            }

            BlockState state = level.getBlockState(surface);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                    x + 0.5D, surface.getY() + 1.05D, z + 0.5D,
                    1, 0.25D, 0.05D, 0.25D, 0.15D);
            if (random.nextFloat() < 0.12F) {
                // The ring visibly heaves the ground as it passes.
                QuakeDebrisEntity.launch(level, state,
                        Vec3.atBottomCenterOf(surface.above()),
                        new Vec3(Math.cos(angle) * 0.12D,
                                0.3D + random.nextDouble() * 0.3D,
                                Math.sin(angle) * 0.12D));
            }
            if (owner != null && wave.shatter) {
                tryShatter(level, surface.above(), owner);
                tryShatter(level, surface.above(2), owner);
            }
        }

        AABB area = new AABB(wave.center, wave.center)
                .inflate(outer + 1.0D, 3.0D, outer + 1.0D);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class,
                area, Entity::isAlive)) {
            if (entity.getUUID().equals(wave.ownerId) || entity.isSpectator()
                    || wave.hit.contains(entity.getUUID())) {
                continue;
            }

            Vec3 offset = entity.position().subtract(wave.center);
            double horizontal = Math.sqrt(offset.x * offset.x + offset.z * offset.z);
            if (horizontal < inner - 1.0D || horizontal > outer + 0.5D) {
                continue;
            }

            wave.hit.add(entity.getUUID());
            if (TemporalEntityEchoService.isReplaying(entity) && owner != null) {
                TemporalEntityEchoService.disturb(entity, owner);
            }
            Vec3 outward = horizontal < 0.01D
                    ? new Vec3(1.0D, 0.0D, 0.0D)
                    : new Vec3(offset.x / horizontal, 0.0D, offset.z / horizontal);
            entity.hurt(owner == null
                    ? level.damageSources().magic()
                    : owner.damageSources().playerAttack(owner), wave.damage);
            setVelocity(entity, outward.scale(0.55D).add(0.0D, wave.launch, 0.0D));
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static void burstDebris(
            ServerLevel level,
            BlockPos source,
            Vec3 bias,
            int count,
            double speed
    ) {
        BlockState state = level.getBlockState(source);
        if (state.isAir()) {
            return;
        }

        RandomSource random = level.getRandom();
        Vec3 origin = Vec3.atCenterOf(source).add(bias.scale(0.7D));
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                origin.x, origin.y, origin.z, 16, 0.3D, 0.3D, 0.3D, 0.2D);
        for (int index = 0; index < count; index++) {
            Vec3 velocity = bias.scale(speed).add(
                    (random.nextDouble() - 0.5D) * 0.5D,
                    0.25D + random.nextDouble() * 0.35D,
                    (random.nextDouble() - 0.5D) * 0.5D
            );
            QuakeDebrisEntity.launch(level, state, origin, velocity);
        }
    }

    /**
     * Breaks one fragile block on Quake's behalf. The break event is posted
     * so claim and protection mods can veto it, and nothing is touched in a
     * Temporal Snapshot, where block changes mirror into the Present.
     */
    private static boolean tryShatter(
            ServerLevel level,
            BlockPos position,
            ServerPlayer player
    ) {
        if (!HeroesEvolvedConfig.COMMON.quakeShattersFragileBlocks.get()
                || TemporalSnapshotService.isTemporalDimension(level)) {
            return false;
        }

        BlockState state = level.getBlockState(position);
        if (!isFragile(state) || !player.mayBuild()
                || !level.mayInteract(player, position)) {
            return false;
        }

        if (NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(
                level, position, state, player)).isCanceled()) {
            return false;
        }
        return level.destroyBlock(position, false, player);
    }

    private static boolean isFragile(BlockState state) {
        return state.is(Tags.Blocks.GLASS_BLOCKS)
                || state.is(Tags.Blocks.GLASS_PANES)
                || state.is(BlockTags.ICE);
    }

    private static List<BlockPos> findFragileBlocks(
            ServerLevel level,
            BlockPos center,
            int radius
    ) {
        List<BlockPos> found = new ArrayList<>();
        int radiusSquared = radius * radius;
        for (BlockPos position : BlockPos.betweenClosed(
                center.offset(-radius, -6, -radius),
                center.offset(radius, 10, radius))) {
            int dx = position.getX() - center.getX();
            int dz = position.getZ() - center.getZ();
            if (dx * dx + dz * dz <= radiusSquared
                    && isFragile(level.getBlockState(position))) {
                found.add(position.immutable());
            }
        }
        return found;
    }

    /** The topmost solid block near a given height, or null over a void. */
    private static BlockPos findSurface(
            ServerLevel level,
            int x,
            int z,
            int aroundY,
            int searchDepth
    ) {
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        for (int y = aroundY + 2; y >= aroundY - searchDepth; y--) {
            position.set(x, y, z);
            BlockState state = level.getBlockState(position);
            if (state.isFaceSturdy(level, position, Direction.UP)
                    && !level.getBlockState(position.above()).isSolid()) {
                return position.immutable();
            }
        }
        return null;
    }

    private static void setVelocity(Entity entity, Vec3 velocity) {
        entity.setDeltaMovement(velocity);
        if (entity instanceof ServerPlayer player) {
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
        } else {
            entity.hurtMarked = true;
            if (entity instanceof Mob mob) {
                // Grounded mobs otherwise cancel the launch on their next step.
                mob.setOnGround(false);
            }
        }
    }

    private static final class SeismicWave {
        private final UUID ownerId;
        private final ServerLevel level;
        private final Vec3 center;
        private final double maxRadius;
        private final float damage;
        private final double launch;
        private final boolean shatter;
        private final Set<UUID> hit = new HashSet<>();
        private double radius;

        private SeismicWave(
                UUID ownerId,
                ServerLevel level,
                Vec3 center,
                double maxRadius,
                float damage,
                double launch,
                boolean shatter
        ) {
            this.ownerId = ownerId;
            this.level = level;
            this.center = center;
            this.maxRadius = maxRadius;
            this.damage = damage;
            this.launch = launch;
            this.shatter = shatter;
            this.radius = 0.5D;
        }
    }

    private static final class Earthquake {
        private final ServerLevel level;
        private final Vec3 center;
        private final double radius;
        private final int durationTicks;
        private final float damage;
        private final boolean shatter;
        private final List<BlockPos> fragileBlocks = new ArrayList<>();
        private int ageTicks;

        private Earthquake(
                ServerLevel level,
                Vec3 center,
                double radius,
                int durationTicks,
                float damage,
                boolean shatter
        ) {
            this.level = level;
            this.center = center;
            this.radius = radius;
            this.durationTicks = durationTicks;
            this.damage = damage;
            this.shatter = shatter;
        }
    }

    private static final class TremorSense {
        private final long endGameTime;
        private final double radius;
        private Set<Integer> revealedIds = Set.of();

        private TremorSense(long endGameTime, double radius) {
            this.endGameTime = endGameTime;
            this.radius = radius;
        }
    }

    private record Plunge(int powerLevel, long startedAt) {
    }

    private static final class Blast {
        private final int powerLevel;
        private int ageTicks;

        private Blast(int powerLevel) {
            this.powerLevel = powerLevel;
        }
    }
}
