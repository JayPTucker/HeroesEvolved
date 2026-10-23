package com.jayptucker.heroesevolved.radiation;

import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.Targeting;
import com.jayptucker.heroesevolved.ability.AbilityRanges;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.entity.TemporalEchoEntity;
import com.jayptucker.heroesevolved.events.EclipseService;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.ScreenTintPayload;
import com.jayptucker.heroesevolved.network.RadiationStatePayload;
import com.jayptucker.heroesevolved.intro.PowerIntroService;
import net.neoforged.neoforge.network.PacketDistributor;
import com.jayptucker.heroesevolved.progression.MasteryService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ted Sprague: induced radioactivity, heat and electromagnetic pulses.
 * Injury and emissions raise stress. Vent the heat, or lose control.
 */
public final class RadiationService {
    public static final int MAXIMUM_MASS = 100;
    private static final int GLOW_THRESHOLD = 60;
    private static final int BREACH_COUNTDOWN_TICKS = 60;
    private static final int DETONATION_CHARGE_TICKS = 60;
    private static final DustParticleOptions RADIANCE =
            new DustParticleOptions(new Vector3f(0.75F, 1.0F, 0.45F), 1.1F);
    private static final DustParticleOptions WHITE_HOT =
            new DustParticleOptions(new Vector3f(1.0F, 1.0F, 0.9F), 1.4F);

    private static final Map<UUID, Long> BREACHES = new HashMap<>();
    private static final Map<UUID, Detonation> DETONATIONS = new HashMap<>();
    private static final Map<UUID, Long> LAST_HURT = new HashMap<>();

    private RadiationService() {
    }

    // ------------------------------------------------------------------
    // Critical Mass
    // ------------------------------------------------------------------

    public static int mass(ServerPlayer player) {
        return player.getData(ModDataAttachments.PLAYER_RADIATION_STRESS);
    }

    /** Raises Critical Mass. At 100% containment begins to fail. */
    public static void addMass(ServerPlayer player, int amount) {
        int mass = Math.min(MAXIMUM_MASS, mass(player) + amount);
        player.setData(ModDataAttachments.PLAYER_RADIATION_STRESS, mass);
        syncVisuals(player);
        if (mass >= MAXIMUM_MASS && !BREACHES.containsKey(player.getUUID())
                && !DETONATIONS.containsKey(player.getUUID())) {
            BREACHES.put(player.getUUID(),
                    player.serverLevel().getGameTime() + BREACH_COUNTDOWN_TICKS);
            player.sendSystemMessage(Component.literal(
                    "You're losing control! Vent it NOW, or it all comes out at once.")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            ScreenTintPayload.send(player, 0x80FFFF60, BREACH_COUNTDOWN_TICKS,
                    ScreenTintPayload.Style.PULSE);
            player.playNotifySound(SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.5F, 1.4F);
        }
    }

    private static void setMass(ServerPlayer player, int mass) {
        player.setData(ModDataAttachments.PLAYER_RADIATION_STRESS, Math.clamp(mass, 0, MAXIMUM_MASS));
        if (mass < MAXIMUM_MASS) {
            BREACHES.remove(player.getUUID());
        }
    }

    /** Actual health damage raises stress; blocked or canceled damage contributes nothing. */
    public static void hurtStress(ServerPlayer player, float damage) {
        if (damage <= 0 || !Float.isFinite(damage) || PowerIntroService.isActive(player)) return;
        LAST_HURT.put(player.getUUID(), player.serverLevel().getGameTime());
        addMass(player, Math.clamp((int) Math.ceil(damage * 3), 1, MAXIMUM_MASS));
    }

    public static RadiationStatePayload visualState(ServerPlayer player) {
        if (EclipseService.arePowersSuppressed(player))
            return new RadiationStatePayload(player.getId(), player.getUUID(), 0, 0, 1);
        int remaining = PowerIntroService.radiationChargeRemaining(player), duration = 600;
        Detonation detonation = DETONATIONS.get(player.getUUID());
        if (remaining <= 0 && detonation != null) {
            remaining = (int) Math.max(0, detonation.at - player.serverLevel().getGameTime());
            duration = DETONATION_CHARGE_TICKS;
        }
        return new RadiationStatePayload(player.getId(), player.getUUID(), mass(player), remaining, duration);
    }
    public static void syncVisuals(ServerPlayer player) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, visualState(player));
    }
    public static void refreshGlow(ServerPlayer player) { updateGlow(player); syncVisuals(player); }

    /** The first uncontrolled burn affects every nearby living body, including poison-immune undead. */
    public static void awakeningAura(ServerPlayer player) {
        for (LivingEntity target : player.serverLevel().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(8), entity -> entity != player && entity.isAlive() && !entity.isSpectator()
                        && entity.distanceToSqr(player) <= 64
                        && (!(entity instanceof ServerPlayer other) || !other.isCreative()))) {
            irradiate(target, 80);
            target.hurt(player.damageSources().indirectMagic(player, player), target.distanceToSqr(player) <= 9 ? 4 : 2);
        }
    }

    public static void awakeningRelease(ServerPlayer player) {
        nuclearBlast(player, true);
        setMass(player, 40);
        refreshGlow(player);
    }

    // ------------------------------------------------------------------
    // Radiant Touch
    // ------------------------------------------------------------------

    /** Hands that burn white-hot: a scorching close-range blast. */
    public static boolean radiantTouch(ServerPlayer player, int powerLevel) {
        LivingEntity target = Targeting.livingEntityInSight(player, 5.0D,
                entity -> !(entity instanceof TemporalEchoEntity));
        if (target == null && heatBlock(player)) {
            addMass(player, 8);
            MasteryService.markPowerUse(player, ModAbilities.RADIATION_ID);
            return true;
        }
        if (target == null) {
            player.displayClientMessage(Component.literal(
                    "Your hands glow, but there's nothing close enough to touch."), true);
            return false;
        }

        target.hurt(player.damageSources().playerAttack(player), 4.0F + powerLevel);
        target.igniteForSeconds(3.0F);
        irradiate(target, 100 + powerLevel * 20);
        ServerLevel level = player.serverLevel();
        level.sendParticles(WHITE_HOT, target.getX(), target.getY() + target.getBbHeight() / 2,
                target.getZ(), 20, 0.3D, 0.4D, 0.3D, 0.0D);
        level.sendParticles(ParticleTypes.FLAME, target.getX(), target.getY() + target.getBbHeight() / 2,
                target.getZ(), 10, 0.3D, 0.4D, 0.3D, 0.02D);
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.9F, 0.7F);
        addMass(player, 8);
        MasteryService.markPowerUse(player, ModAbilities.RADIATION_ID);
        return true;
    }

    /** Ted's close-range heat boils water, thaws ice and melts metal locks. */
    private static boolean heatBlock(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(AbilityRanges.ranged(5.0))),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.SOURCE_ONLY, player));
        if (hit.getType() != HitResult.Type.BLOCK) return false;
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        boolean ice = state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE) || state.is(Blocks.FROSTED_ICE);
        boolean snow = state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW);
        boolean metal = state.is(Blocks.IRON_DOOR) || state.is(Blocks.IRON_TRAPDOOR) || state.is(Blocks.IRON_BARS);
        boolean water = state.is(Blocks.WATER) && state.getFluidState().is(Fluids.WATER);
        if (!(ice || snow || metal || water) || !player.mayBuild() || !level.mayInteract(player, pos)
                || !level.getWorldBorder().isWithinBounds(pos)
                || NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, state, player)).isCanceled()) return false;
        if (water) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        else if (ice) level.setBlock(pos, level.dimensionType().ultraWarm() ? Blocks.AIR.defaultBlockState() : Blocks.WATER.defaultBlockState(), 3);
        else if (!level.destroyBlock(pos, metal, player)) return false;
        level.sendParticles(ParticleTypes.CLOUD, pos.getX() + .5, pos.getY() + .7, pos.getZ() + .5, 20, .3, .4, .3, .04);
        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1, 0.8F);
        AbilityEffects.send(level, Kind.NUCLEAR_CHARGE, Vec3.atBottomCenterOf(pos), Vec3.atCenterOf(pos), .5);
        return true;
    }

    /** Radiation sickness: poison, hunger and weakness. */
    public static void irradiate(LivingEntity target, int durationTicks) {
        target.addEffect(new MobEffectInstance(MobEffects.POISON, durationTicks, 0));
        target.addEffect(new MobEffectInstance(MobEffects.HUNGER, durationTicks, 1));
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, durationTicks, 0));
    }

    // ------------------------------------------------------------------
    // Radiation Pulse
    // ------------------------------------------------------------------

    /** An invisible wave of radiation sickens everything around you. */
    public static void radiationPulse(ServerPlayer player, int powerLevel) {
        radiationPulse(player, powerLevel, false);
    }

    public static void radiationPulse(ServerPlayer player, int powerLevel, boolean bright) {
        ServerLevel level = player.serverLevel();
        double radius = 6.0D + powerLevel;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(radius),
                entity -> entity != player && entity.isAlive()
                        && entity.distanceToSqr(player) <= radius * radius)) {
            if (bright) target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60));
            else {
                target.hurt(player.damageSources().indirectMagic(player, player), 2.0F);
                irradiate(target, 120 + powerLevel * 20);
            }
        }

        for (int ring = 1; ring <= 3; ring++) {
            double r = radius * ring / 3.0D;
            for (int point = 0; point < 24; point++) {
                double angle = Math.PI * 2.0D * point / 24;
                level.sendParticles(RADIANCE, player.getX() + Math.cos(angle) * r,
                        player.getY() + 0.3D, player.getZ() + Math.sin(angle) * r,
                        1, 0.0D, 0.1D, 0.0D, 0.0D);
            }
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 0.5F);
        AbilityEffects.at(player, bright ? Kind.NUCLEAR_EMP : Kind.NUCLEAR_CHARGE, bright ? radius : 1);
        ScreenTintPayload.sendAround(level, player.position(), radius, bright ? 0x90FFF1C1 : 0x30FF961C, 10,
                ScreenTintPayload.Style.FLASH);
        addMass(player, bright ? 8 : 15);
        MasteryService.markPowerUse(player, ModAbilities.RADIATION_ID);
    }

    // ------------------------------------------------------------------
    // Stress venting
    // ------------------------------------------------------------------

    public static boolean canVent(ServerPlayer player) {
        return PowerIntroService.radiationChargeRemaining(player) == 0 && mass(player) >= 20;
    }

    public static void emp(ServerPlayer player) {
        RadiationEmp.pulse(player);
        addMass(player, 12);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.4F, 0.6F);
        MasteryService.markPowerUse(player, ModAbilities.RADIATION_ID);
    }

    /** Vent Critical Mass through your hands as a piercing beam. */
    public static boolean meltdownBeam(ServerPlayer player, int powerLevel) {
        if (PowerIntroService.radiationChargeRemaining(player) > 0) return false;
        int mass = mass(player);
        if (mass < 20) {
            player.displayClientMessage(Component.literal(
                    "Not enough built up to vent (" + mass + "%)."), true);
            return false;
        }

        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(AbilityRanges.ranged(24.0D))),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 end = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation()
                : eye.add(look.scale(AbilityRanges.ranged(24.0D)));
        float damage = 4.0F + mass / 7.0F + powerLevel;

        // It pierces: everything along the line is struck.
        for (Entity entity : level.getEntities(player, new AABB(eye, end).inflate(1.2D),
                Entity::isAlive)) {
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            Vec3 toEntity = living.getBoundingBox().getCenter().subtract(eye);
            double along = toEntity.dot(look);
            if (along < 0.0D || toEntity.subtract(look.scale(along)).length()
                    > 1.0D + living.getBbWidth() / 2) {
                continue;
            }
            living.hurt(player.damageSources().indirectMagic(player, player), damage);
            living.igniteForSeconds(4.0F);
            irradiate(living, 100);
        }

        AbilityEffects.send(level, Kind.NUCLEAR_RAY, eye, end, 1.0D);
        level.sendParticles(ParticleTypes.FLASH, end.x, end.y, end.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.LAVA, end.x, end.y, end.z, 8, 0.2D, 0.2D, 0.2D, 0.0D);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.2F, 1.6F);
        level.playSound(null, end.x, end.y, end.z,
                SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.6F, 1.4F);
        CameraShakePayload.sendAround(level, player.position(), 8.0D, 0.8F, 8);

        setMass(player, 0);
        player.displayClientMessage(Component.literal(
                "Vented " + mass + "% Stress.").withStyle(ChatFormatting.GREEN), true);
        MasteryService.markPowerUse(player, ModAbilities.RADIATION_ID);
        return true;
    }

    // ------------------------------------------------------------------
    // Exploding Man
    // ------------------------------------------------------------------

    public static boolean isDetonating(ServerPlayer player) {
        return DETONATIONS.containsKey(player.getUUID());
    }

    /** Let it all out. Three seconds to change your mind (Shift). */
    public static void beginDetonation(ServerPlayer player, int powerLevel, boolean voluntary) {
        if (PowerIntroService.radiationChargeRemaining(player) > 0 || isDetonating(player)) return;
        DETONATIONS.put(player.getUUID(), new Detonation(
                player.serverLevel().getGameTime() + DETONATION_CHARGE_TICKS,
                voluntary));
        BREACHES.remove(player.getUUID());
        player.setGlowingTag(true);
        ArmPosePayload.show(player, ArmPose.ARMS_WIDE, DETONATION_CHARGE_TICKS + 10);
        ScreenTintPayload.send(player, 0xA0FFFFE0, DETONATION_CHARGE_TICKS,
                ScreenTintPayload.Style.HOLD);
        player.sendSystemMessage(Component.literal(voluntary
                ? "You stop holding it in. (Shift + key to stop it.)"
                : "Containment fails!").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        if (voluntary) {
            MasteryService.markPowerUse(player, ModAbilities.RADIATION_ID);
        }
        syncVisuals(player);
    }

    /** "You have to hold it in." Aborts a voluntary detonation. */
    public static boolean holdItIn(ServerPlayer player) {
        Detonation detonation = DETONATIONS.get(player.getUUID());
        if (PowerIntroService.radiationChargeRemaining(player) > 0 || detonation == null || !detonation.voluntary) {
            return false;
        }
        DETONATIONS.remove(player.getUUID());
        updateGlow(player);
        RadiationDecay.clear(player);
        ScreenTintPayload.send(player, 0xA0FFFFE0, 0, ScreenTintPayload.Style.HOLD);
        ArmPosePayload.clear(player);
        syncVisuals(player);
        player.sendSystemMessage(Component.literal(
                "You hold it in. The light sinks back beneath your skin.")
                .withStyle(ChatFormatting.GREEN));
        return true;
    }

    private static void nuclearBlast(ServerPlayer player, boolean awakening) {
        ServerLevel level = player.serverLevel();
        Vec3 center = player.position();
        RadiationDecay.clear(player);
        NuclearFallout.begin(player, awakening);

        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y + 1.0D,
                center.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.FLASH, center.x, center.y + 1.0D, center.z,
                1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(WHITE_HOT, center.x, center.y + 1.0D, center.z,
                32, 2, 2, 2, 0.0D);
        level.playSound(null, center.x, center.y, center.z,
                com.jayptucker.heroesevolved.sounds.ModSounds.NUCLEAR_BLAST.get(), SoundSource.PLAYERS, 4.0F, 1.0F);
        level.playSound(null, center.x, center.y, center.z,
                SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 4.0F, 0.4F);
        level.playSound(null, center.x, center.y, center.z,
                SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 4.0F, 0.5F);
        CameraShakePayload.sendAround(level, center, 300.0D, 3.5F, awakening ? 120 : 80);
        ScreenTintPayload.sendAround(level, center, 128.0D, 0xD0FFFFFF, 12,
                ScreenTintPayload.Style.FLASH);
        warnDistantWitnesses(player, center);
    }

    private static void detonate(ServerPlayer player) {
        nuclearBlast(player, false);
        // Ted is immune to his own emissions; ordinary attacks remain dangerous after the intro.
        setMass(player, 0);
        refreshGlow(player);
        ArmPosePayload.clear(player);
    }

    /** Far-off players see the sky flash; those who've seen the future recognise it. */
    private static void warnDistantWitnesses(ServerPlayer source, Vec3 center) {
        for (ServerPlayer witness : source.serverLevel().players()) {
            double distance = witness.position().distanceTo(center);
            if (witness == source || distance < 128.0D || distance > 1024.0D) {
                continue;
            }
            witness.displayClientMessage(Component.literal(
                    "The sky flashes white in the distance.")
                    .withStyle(ChatFormatting.YELLOW), true);
            ScreenTintPayload.send(witness, 0x50FFFFFF, 12, ScreenTintPayload.Style.FLASH);
            if (witness.getData(ModDataAttachments.PLAYER_SEEN_FUTURE_CITY.get())) {
                witness.sendSystemMessage(Component.literal(
                        "You've seen that light before. On an avenue that hasn't been built yet.")
                        .withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));
            }
        }
    }

    // ------------------------------------------------------------------
    // Passive, intro hooks, overexertion
    // ------------------------------------------------------------------

    /** Overexertion: the radiation burns from the inside. */
    public static void radiationBurn(ServerPlayer player) {
        addMass(player, 25);
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0));
        player.sendSystemMessage(Component.literal(
                "Something inside you flares hotter. Your stress surges.")
                .withStyle(ChatFormatting.YELLOW));
    }

    /** Above 60% you visibly glow, and radiation leaks onto nearby creatures. */
    private static void updateGlow(ServerPlayer player) {
        boolean glowing = !EclipseService.arePowersSuppressed(player) && (mass(player) >= GLOW_THRESHOLD || isDetonating(player)
                || PowerIntroService.radiationChargeRemaining(player) > 0);
        if (player.hasGlowingTag() != glowing) {
            player.setGlowingTag(glowing);
        }
    }

    public static void tickPlayer(ServerPlayer player, long gameTime) {
        if (PowerIntroService.radiationChargeRemaining(player) > 0) {
            player.setGlowingTag(true);
            if (gameTime % 20 == 0) syncVisuals(player);
            return;
        }
        int mass = mass(player);
        if (mass >= MAXIMUM_MASS && !BREACHES.containsKey(player.getUUID()) && !isDetonating(player)) addMass(player, 0);

        // Mass bleeds away slowly while you rest.
        if (gameTime % 20 == 0 && mass > 0 && !BREACHES.containsKey(player.getUUID()) && !isDetonating(player)
                && gameTime - LAST_HURT.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2) > 100) {
            setMass(player, mass - 1);
        }

        if (gameTime % 10 == 0) {
            updateGlow(player);
            if (mass > 0) {
                ChatFormatting colour = mass >= 80 ? ChatFormatting.RED
                        : mass >= GLOW_THRESHOLD ? ChatFormatting.GOLD : ChatFormatting.GREEN;
                int bars = mass / 10;
                player.displayClientMessage(Component.translatable("message.heroesevolved.radiation_stress")
                        .append(Component.literal("|".repeat(bars) + ".".repeat(10 - bars))
                                .withStyle(colour))
                        .append(Component.literal(" " + mass + "%")), true);
            }
        }
        if (gameTime % 20 == 0) syncVisuals(player);

        if (mass >= GLOW_THRESHOLD && gameTime % 4 == 0) {
            player.serverLevel().sendParticles(RADIANCE, player.getX(), player.getY() + 1.0D,
                    player.getZ(), 2, 0.35D, 0.6D, 0.35D, 0.0D);
        }
        if (mass >= GLOW_THRESHOLD && gameTime % 40 == 0) {
            for (LivingEntity nearby : player.serverLevel().getEntitiesOfClass(LivingEntity.class,
                    player.getBoundingBox().inflate(3.0D),
                    entity -> entity != player && entity.isAlive())) {
                irradiate(nearby, 60);
            }
        }
    }

    public static void tick(MinecraftServer server) {
        long gameTime = server.overworld().getGameTime();
        NuclearFallout.tick(server);
        RadiationEmp.tick(server);

        for (Map.Entry<UUID, Long> entry : List.copyOf(BREACHES.entrySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                BREACHES.remove(entry.getKey());
            } else if (EclipseService.arePowersSuppressed(player)) {
                BREACHES.remove(entry.getKey());
                refreshGlow(player);
            } else if (gameTime >= entry.getValue()) {
                // Too late to vent: it all comes out.
                beginDetonation(player, PlayerProgressionService.getLevel(player), false);
            } else if (gameTime % 10 == 0) {
                player.playNotifySound(SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS,
                        1.5F, 1.6F);
            }
        }

        for (Map.Entry<UUID, Detonation> entry : List.copyOf(DETONATIONS.entrySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Detonation detonation = entry.getValue();
            if (player == null || !player.isAlive()) {
                DETONATIONS.remove(entry.getKey());
                if (player != null) RadiationDecay.clear(player);
                continue;
            }
            long remaining = detonation.at - gameTime;
            if (remaining <= 0 || EclipseService.arePowersSuppressed(player)) {
                DETONATIONS.remove(entry.getKey());
                if (!EclipseService.arePowersSuppressed(player) && remaining <= 0) {
                    detonate(player);
                } else {
                    refreshGlow(player);
                    RadiationDecay.clear(player);
                    ArmPosePayload.clear(player);
                }
                continue;
            }

            // Orange heat intensifies as containment fails.
            player.setDeltaMovement(player.getDeltaMovement().multiply(0.2D, 0.0D, 0.2D)
                    .add(0.0D, 0.02D, 0.0D));
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
            player.resetFallDistance();
            ServerLevel level = player.serverLevel();
            double intensity = 1.0D - (double) remaining / DETONATION_CHARGE_TICKS;
            if (gameTime % 4 == 0) RadiationDecay.tick(player, (float) intensity, false);
            level.sendParticles(WHITE_HOT, player.getX(), player.getY() + 1.0D, player.getZ(),
                    (int) (6 + intensity * 30), 0.5D + intensity, 0.8D + intensity,
                    0.5D + intensity, 0.0D);
            if (gameTime % 8 == 0) {
                level.playSound(null, player.blockPosition(), SoundEvents.BEACON_AMBIENT,
                        SoundSource.PLAYERS, 2.0F, 0.6F + (float) intensity);
                CameraShakePayload.sendAround(level, player.position(), 32.0D,
                        (float) (0.4D + intensity * 1.6D), 10);
            }
            if (gameTime % 20 == 0) syncVisuals(player);
        }
    }

    public static void stopAll(ServerPlayer player) {
        RadiationDecay.clear(player);
        player.setData(ModDataAttachments.PLAYER_RADIATION_STRESS, 0);
        BREACHES.remove(player.getUUID());
        DETONATIONS.remove(player.getUUID());
        LAST_HURT.remove(player.getUUID());
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                new RadiationStatePayload(player.getId(), player.getUUID(), 0, 0, 1));
        if (player.hasGlowingTag()) {
            player.setGlowingTag(false);
        }
    }

    public static void clear() {
        RadiationDecay.clearAll();
        BREACHES.clear(); DETONATIONS.clear(); LAST_HURT.clear();
        NuclearFallout.clear();
        RadiationEmp.clear();
    }

    public static void disconnect(ServerPlayer player) {
        int stress = mass(player);
        stopAll(player);
        player.setData(ModDataAttachments.PLAYER_RADIATION_STRESS, stress);
    }

    // ------------------------------------------------------------------

    private record Detonation(long at, boolean voluntary) {
    }
}
