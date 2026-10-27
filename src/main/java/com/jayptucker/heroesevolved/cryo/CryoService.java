package com.jayptucker.heroesevolved.cryo;

import com.jayptucker.heroesevolved.ability.Targeting;
import com.jayptucker.heroesevolved.ability.AbilityRanges;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.entity.TemporalEchoEntity;
import com.jayptucker.heroesevolved.events.EclipseService;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.network.FrozenEntityPayload;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.network.PacketDistributor;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.ScreenTintPayload;
import com.jayptucker.heroesevolved.progression.MasteryService;
import com.jayptucker.heroesevolved.time.TemporalEntityEchoService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cryokinesis, after Tracy Strauss: chilling bolts, a freezing touch,
 * walls and spikes of ice, and a blizzard of her own. Chill uses
 * Minecraft's own powder-snow freezing, so frozen players see the frost
 * overlay and frozen mobs shiver. Anything frozen solid shatters when hit.
 */
public final class CryoService {
    public static final int FROST_TINT = 0x40C8F0FF;
    private static final DustParticleOptions FROST =
            new DustParticleOptions(new Vector3f(0.75F, 0.92F, 1.0F), 1.0F);
    private static final int MAXIMUM_CHILL_TICKS = 280;

    private static final Map<UUID, Long> THAW_GRACE = new HashMap<>();
    private static final Map<UUID, Frozen> FROZEN = new HashMap<>();
    private static final Map<UUID, Chilled> CHILLED = new HashMap<>();
    private static final ResourceLocation CHILL_SLOW = ResourceLocation.fromNamespaceAndPath("heroesevolved", "cryo_chill");
    private static int coldDamageDepth;
    private static final Map<UUID, Blizzard> BLIZZARDS = new HashMap<>();
    private static final List<Delayed> DELAYED = new ArrayList<>();

    private CryoService() {
    }

    // ------------------------------------------------------------------
    // Chill, freeze and shatter
    // ------------------------------------------------------------------

    /** Adds cold using vanilla freezing (frost overlay, shivering, slowness). */
    public static void chill(LivingEntity target, int ticks) {
        chill(target, ticks, null);
    }

    public static void chill(LivingEntity target, int ticks, ServerPlayer caster) {
        if (!(target.level() instanceof ServerLevel level) || !target.isAlive() || ticks <= 0 || immune(target)) {
            return;
        }
        target.clearFire();
        if (isFrozen(target)) return;
        Chilled previous = CHILLED.get(target.getUUID());
        int amount = Math.min(MAXIMUM_CHILL_TICKS, (previous == null ? target.getTicksFrozen() : previous.amount) + ticks);
        CHILLED.put(target.getUUID(), new Chilled(target, amount, level.getGameTime(),
                caster == null ? null : caster.getUUID()));
        target.setTicksFrozen(amount);
        slow(target, amount);
        if (amount >= target.getTicksRequiredToFreeze() && !target.getType().is(Tags.EntityTypes.BOSSES))
            freezeSolid(target, target instanceof ServerPlayer ? 45 : 80, caster);
    }

    private static boolean immune(LivingEntity target) {
        return !target.canFreeze() || target instanceof ServerPlayer player
                && !EclipseService.arePowersSuppressed(player)
                && PlayerAbilityService.getData(player).ability(ModAbilities.CRYOKINESIS_ID)
                .filter(progress -> progress.isUnlocked()).isPresent();
    }

    private static void slow(LivingEntity target, int amount) {
        var movement = target.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movement == null) return;
        movement.removeModifier(CHILL_SLOW);
        if (amount > 0) movement.addTransientModifier(new AttributeModifier(CHILL_SLOW,
                -Math.min(0.7, 0.15 + 0.5 * amount / target.getTicksRequiredToFreeze()),
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    public static boolean isApplyingColdDamage() { return coldDamageDepth > 0; }
    private static void coldHit(ServerPlayer caster, LivingEntity target, float damage) {
        coldDamageDepth++;
        try { target.hurt(caster.damageSources().playerAttack(caster), damage); }
        finally { coldDamageDepth--; }
    }

    public static boolean isFrozen(Entity entity) {
        Frozen frozen = FROZEN.get(entity.getUUID());
        return frozen != null && frozen.target == entity && entity.isAlive()
                && entity.level().dimension().equals(frozen.dimension)
                && entity.level().getGameTime() < frozen.until;
    }

    /** Pausing the full mob tick also stops attacks, brains, creeper fuses and spell windups. */
    public static boolean shouldPauseMob(Entity entity) { return entity instanceof Mob && isFrozen(entity); }
    public static void releaseEntity(Entity entity) {
        Frozen frozen = FROZEN.remove(entity.getUUID());
        if (frozen != null) thaw(frozen, false);
        Chilled chilled = CHILLED.remove(entity.getUUID());
        if (chilled != null) { slow(chilled.target, 0); chilled.target.setTicksFrozen(0); }
    }

    public static void syncTo(ServerPlayer viewer, Entity target) {
        Frozen frozen = FROZEN.get(target.getUUID());
        if (frozen != null && isFrozen(target)) PacketDistributor.sendToPlayer(viewer, payload(frozen));
    }

    private static FrozenEntityPayload payload(Frozen frozen) {
        return new FrozenEntityPayload(frozen.target.getId(), frozen.target.getUUID(),
                (int) Math.max(0, frozen.until - frozen.target.level().getGameTime()));
    }

    /**
     * Locks a creature inside a shell of ice. Bosses only chill: nothing
     * holds the Wither or the Ender Dragon for long.
     */
    public static boolean freezeSolid(LivingEntity target, int durationTicks, ServerPlayer caster) {
        if (!(target.level() instanceof ServerLevel level) || !target.isAlive() || durationTicks <= 0 || immune(target)) return false;
        if (target.getType().is(Tags.EntityTypes.BOSSES) || !target.isAlive()) {
            chill(target, 100, caster);
            return false;
        }

        // The ice is enforced every tick (position, target, pathing) rather
        // than by switching off the mob's AI, which would be saved with the
        // mob if its chunk unloaded mid-freeze.
        if (caster != null) com.jayptucker.heroesevolved.expansion.FacilityService.technique(caster, target.blockPosition(), "cold");
        Frozen old = isFrozen(target) ? FROZEN.get(target.getUUID()) : null;
        if (target instanceof ServerPlayer && old == null
                && THAW_GRACE.getOrDefault(target.getUUID(), 0L) > level.getServer().overworld().getGameTime()) return false;
        if (target instanceof ServerPlayer) durationTicks = Math.min(60, durationTicks);
        Frozen state = new Frozen(target, old == null ? target.position() : old.lockedAt,
                old != null && target instanceof ServerPlayer ? old.until
                        : Math.max(old == null ? 0 : old.until, level.getGameTime() + Math.clamp(durationTicks, 1, 600)),
                caster == null ? null : caster.getUUID(), level.dimension());
        FROZEN.put(target.getUUID(), state);
        if (old != null) return true;
        CHILLED.remove(target.getUUID());
        slow(target, 0);
        if (target instanceof Mob mob) {
            mob.setTarget(null);
            mob.getNavigation().stop();
        }
        target.clearFire();
        target.setTicksFrozen(Math.max(160, target.getTicksRequiredToFreeze()));
        target.invulnerableTime = 0;
        target.setDeltaMovement(Vec3.ZERO);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(target, payload(state));
        AbilityEffects.send(level, Kind.CRYO_FREEZE, target.position(),
                target.position().add(0, target.getBbHeight(), 0), target.getBbWidth());
        if (old != null) return true;
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,
                        Blocks.PACKED_ICE.defaultBlockState()),
                target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(),
                30, target.getBbWidth() / 2, target.getBbHeight() / 2,
                target.getBbWidth() / 2, 0.05D);
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 1.0F, 0.6F);
        if (target instanceof ServerPlayer frozenPlayer) {
            ScreenTintPayload.send(frozenPlayer, 0x70B8E8FF, durationTicks,
                    ScreenTintPayload.Style.EDGE);
            frozenPlayer.displayClientMessage(Component.literal(
                    "You are frozen solid!").withStyle(ChatFormatting.AQUA), true);
        }
        return true;
    }

    /**
     * A blow against a frozen creature shatters its ice for extra damage.
     *
     * @return the damage to deal instead of {@code amount}, or {@code amount}
     *         unchanged if the target was not frozen
     */
    public static float onFrozenHit(LivingEntity target, float amount, boolean freezingDamage) {
        Frozen frozen = FROZEN.get(target.getUUID());
        if (frozen == null || !isFrozen(target) || freezingDamage || amount <= 0) {
            return amount;
        }

        thaw(frozen, true);
        FROZEN.remove(target.getUUID());
        return target instanceof ServerPlayer ? amount + Math.min(6, amount * .25F + 2) : amount * 1.5F + 4.0F;
    }

    private static void thaw(Frozen frozen, boolean shatter) {
        LivingEntity target = frozen.target;
        target.setTicksFrozen(0);
        slow(target, 0);
        CHILLED.remove(target.getUUID());
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        if (target instanceof ServerPlayer) THAW_GRACE.put(target.getUUID(), level.getServer().overworld().getGameTime() + 40);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(target,
                new FrozenEntityPayload(target.getId(), target.getUUID(), 0));
        AbilityEffects.send(level, shatter ? Kind.CRYO_SHATTER : Kind.CRYO_CHILL,
                target.position(), target.position().add(0, target.getBbHeight(), 0), target.getBbWidth());
        if (shatter) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,
                            Blocks.BLUE_ICE.defaultBlockState()),
                    target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(),
                    50, target.getBbWidth() / 2, target.getBbHeight() / 2,
                    target.getBbWidth() / 2, 0.25D);
            level.sendParticles(ParticleTypes.SNOWFLAKE, target.getX(),
                    target.getY() + target.getBbHeight() / 2, target.getZ(),
                    25, 0.4D, 0.5D, 0.4D, 0.15D);
            level.playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.2F, 0.7F);
        } else {
            level.sendParticles(ParticleTypes.DRIPPING_WATER, target.getX(),
                    target.getY() + target.getBbHeight(), target.getZ(),
                    8, 0.3D, 0.2D, 0.3D, 0.0D);
        }
    }

    // ------------------------------------------------------------------
    // Frost Bolt
    // ------------------------------------------------------------------

    /** A lance of cold. It chills, and freezes the water it skims. */
    public static void frostBolt(ServerPlayer player, int powerLevel) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double range = 20.0D;
        LivingEntity target = Targeting.livingEntityInSight(player, range,
                entity -> !(entity instanceof TemporalEchoEntity));

        BlockHitResult blockHit = level.clip(new ClipContext(eye, eye.add(look.scale(AbilityRanges.ranged(range))),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
        Vec3 end = target != null ? target.getBoundingBox().getCenter()
                : blockHit.getLocation();

        AbilityEffects.send(level, Kind.CRYO_BOLT, eye.add(0, -0.15, 0), end, 1);

        if (target != null) {
            if (TemporalEntityEchoService.isReplaying(target)) {
                TemporalEntityEchoService.disturb(target, player);
            }
            coldHit(player, target, 3.0F + powerLevel);
            chill(target, 80 + powerLevel * 15, player);
            level.sendParticles(ParticleTypes.ITEM_SNOWBALL, end.x, end.y, end.z,
                    12, 0.2D, 0.3D, 0.2D, 0.1D);
        } else if (blockHit.getType() == HitResult.Type.BLOCK) {
            // Skimming water leaves a small raft of ice behind.
            BlockPos hit = blockHit.getBlockPos();
            for (BlockPos pos : BlockPos.betweenClosed(hit.offset(-1, 0, -1), hit.offset(1, 0, 1))) {
                TemporaryBlocks.freezeWater(level, pos.immutable());
            }
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.POWDER_SNOW_BREAK, SoundSource.PLAYERS, 1.0F, 1.6F);
        MasteryService.markPowerUse(player, ModAbilities.CRYOKINESIS_ID);
    }

    // ------------------------------------------------------------------
    // Flash Freeze
    // ------------------------------------------------------------------

    /** Tracy's touch: whatever she reaches freezes solid where it stands. */
    public static boolean flashFreeze(ServerPlayer player, int powerLevel) {
        LivingEntity target = Targeting.livingEntityInSight(player, 6.0D,
                entity -> !(entity instanceof TemporalEchoEntity));
        if (target == null) {
            player.displayClientMessage(Component.literal(
                    "You need to be close enough to touch them."), true);
            return false;
        }

        if (TemporalEntityEchoService.isReplaying(target)) {
            TemporalEntityEchoService.disturb(target, player);
        }
        // Players are held for less time, to keep fights fair.
        int duration = target instanceof ServerPlayer
                ? 40 + powerLevel * 6
                : 60 + powerLevel * 10;
        // Apply the contact hit before installing the shell. It must not shatter its own freeze.
        coldHit(player, target, 2.0F);
        freezeSolid(target, duration, player);
        MasteryService.markPowerUse(player, ModAbilities.CRYOKINESIS_ID);
        return true;
    }

    // ------------------------------------------------------------------
    // Ice Wall
    // ------------------------------------------------------------------

    /** A wall of packed ice rises row by row in front of you. */
    public static boolean iceWall(ServerPlayer player, int powerLevel) {
        ServerLevel level = player.serverLevel();
        Vec3 flat = player.getLookAngle().multiply(1.0D, 0.0D, 1.0D);
        if (flat.lengthSqr() < 0.01D) {
            flat = Vec3.directionFromRotation(0.0F, player.getYRot());
        }
        flat = flat.normalize();
        Vec3 right = new Vec3(-flat.z, 0.0D, flat.x);
        Vec3 base = player.position().add(flat.scale(3.0D));
        int duration = 20 * (12 + 2 * powerLevel);
        int halfWidth = powerLevel >= 5 ? 3 : 2;

        int placed = 0;
        long now = level.getGameTime();
        for (int row = 0; row < 3; row++) {
            int height = row;
            for (int column = -halfWidth; column <= halfWidth; column++) {
                BlockPos pos = BlockPos.containing(base.add(right.scale(column)))
                        .above(height);
                placed++;
                // Rows rise two ticks apart, so the wall grows from the ground.
                DELAYED.add(new Delayed(player, level, now + height * 2L, () -> {
                    if (TemporaryBlocks.place(level, pos, Blocks.PACKED_ICE.defaultBlockState(),
                            duration, player)) {
                        AbilityEffects.send(level, Kind.CRYO_ICE_GROWTH, Vec3.atBottomCenterOf(pos),
                                Vec3.atBottomCenterOf(pos.above()), 0.8);
                        level.sendParticles(ParticleTypes.SNOWFLAKE, pos.getX() + 0.5D,
                                pos.getY() + 0.5D, pos.getZ() + 0.5D, 4, 0.3D, 0.3D, 0.3D, 0.02D);
                    }
                }));
            }
        }
        if (placed == 0) {
            return false;
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GLASS_PLACE, SoundSource.PLAYERS, 1.2F, 0.6F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.4F);
        MasteryService.markPowerUse(player, ModAbilities.CRYOKINESIS_ID);
        return true;
    }

    // ------------------------------------------------------------------
    // Glacial Spikes
    // ------------------------------------------------------------------

    /** A line of ice spikes bursts from the ground toward where you look. */
    public static void glacialSpikes(ServerPlayer player, int powerLevel) {
        ServerLevel level = player.serverLevel();
        Vec3 flat = player.getLookAngle().multiply(1.0D, 0.0D, 1.0D);
        if (flat.lengthSqr() < 0.01D) {
            flat = Vec3.directionFromRotation(0.0F, player.getYRot());
        }
        flat = flat.normalize();
        int length = AbilityRanges.ranged(12 + powerLevel);
        long now = level.getGameTime();
        float damage = 5.0F + powerLevel;

        for (int step = 2; step <= length; step++) {
            Vec3 point = player.position().add(flat.scale(step));
            int height = 1 + (step % 3 == 0 ? 1 : 0);
            DELAYED.add(new Delayed(player, level, now + step, () ->
                    eruptSpike(player, point, height, damage)));
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.POWDER_SNOW_BREAK, SoundSource.PLAYERS, 1.2F, 0.5F);
        MasteryService.markPowerUse(player, ModAbilities.CRYOKINESIS_ID);
    }

    private static void eruptSpike(ServerPlayer player, Vec3 point, int height, float damage) {
        if (!player.isAlive()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos ground = surfaceNear(level, BlockPos.containing(point), 3);
        if (ground == null) {
            return;
        }

        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(ground.above()).inflate(0.8D, 1.0D, 0.8D),
                entity -> entity != player && entity.isAlive())) {
            coldHit(player, entity, damage);
            chill(entity, 80, player);
            if (!isFrozen(entity)) push(entity, new Vec3(0.0D, 0.9D, 0.0D));
        }

        for (int y = 1; y <= height; y++) {
            BlockState state = y == height
                    ? Blocks.BLUE_ICE.defaultBlockState()
                    : Blocks.PACKED_ICE.defaultBlockState();
            TemporaryBlocks.place(level, ground.above(y), state, 30 + y * 4, player);
        }
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,
                        Blocks.PACKED_ICE.defaultBlockState()),
                ground.getX() + 0.5D, ground.getY() + 1.2D, ground.getZ() + 0.5D,
                12, 0.3D, 0.4D, 0.3D, 0.15D);
        AbilityEffects.send(level, Kind.CRYO_ICE_GROWTH, Vec3.atBottomCenterOf(ground.above()),
                Vec3.atBottomCenterOf(ground.above(height + 1)), 0.9);
        level.sendParticles(ParticleTypes.SNOWFLAKE, ground.getX() + 0.5D,
                ground.getY() + 1.5D, ground.getZ() + 0.5D, 6, 0.3D, 0.5D, 0.3D, 0.05D);
        level.playSound(null, ground.above(), SoundEvents.GLASS_PLACE, SoundSource.PLAYERS,
                0.7F, 0.8F + level.getRandom().nextFloat() * 0.5F);
        CameraShakePayload.sendAround(level, Vec3.atCenterOf(ground), 8.0D, 0.5F, 5);
    }

    // ------------------------------------------------------------------
    // Blizzard
    // ------------------------------------------------------------------

    public static boolean hasBlizzard(ServerPlayer player) {
        return BLIZZARDS.containsKey(player.getUUID());
    }

    /**
     * A storm that follows Tracy: snow piles up, water freezes, everyone
     * else in it chills toward frozen. When it ends, anything frozen
     * through is frozen solid.
     */
    public static void blizzard(ServerPlayer player, int powerLevel) {
        int duration = 20 * (10 + powerLevel);
        BLIZZARDS.put(player.getUUID(), new Blizzard(
                player.serverLevel().getGameTime() + duration, powerLevel));
        ColdDomeService.start(player, (int) BLIZZARD_RADIUS, duration, true);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 0.5F, 1.6F);
        player.displayClientMessage(Component.literal(
                "The temperature plummets.").withStyle(ChatFormatting.AQUA), true);
        MasteryService.markPowerUse(player, ModAbilities.CRYOKINESIS_ID);
    }

    public static void endBlizzard(ServerPlayer player) {
        Blizzard blizzard = BLIZZARDS.remove(player.getUUID());
        if (blizzard == null) {
            return;
        }
        ColdDomeService.stop(player);
        // Deep Freeze: anything frozen through by the storm locks solid.
        for (LivingEntity entity : player.serverLevel().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(BLIZZARD_RADIUS),
                entity -> entity != player && entity.isAlive() && entity.isFullyFrozen()
                        && ColdDomeShape.contains(player.position(), entity.position(), BLIZZARD_RADIUS))) {
            freezeSolid(entity, 60, player);
        }
    }

    private static final double BLIZZARD_RADIUS = ColdDomeService.BLIZZARD_RADIUS;

    private static void tickBlizzard(ServerPlayer player, Blizzard blizzard, long gameTime) {
        ServerLevel level = player.serverLevel();
        RandomSource random = level.getRandom();
        Vec3 center = player.position();
        if (gameTime % 8 == 0) {
            AbilityEffects.send(level, Kind.CRYO_BLIZZARD, center, center.add(player.getDeltaMovement()), BLIZZARD_RADIUS);
        }

        if (gameTime % 10 == 0) {
            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class,
                    player.getBoundingBox().inflate(BLIZZARD_RADIUS),
                    entity -> entity != player && entity.isAlive()
                            && ColdDomeShape.contains(center, entity.position(), BLIZZARD_RADIUS))) {
                chill(entity, 45 + blizzard.powerLevel * 5, player);
                if (gameTime % 20 == 0) {
                    coldHit(player, entity, 1.0F);
                }
                if (entity instanceof ServerPlayer caught) {
                    ScreenTintPayload.send(caught, 0x60E8F6FF, 14, ScreenTintPayload.Style.HOLD);
                }
            }
            ScreenTintPayload.send(player, 0x28E8F6FF, 14, ScreenTintPayload.Style.EDGE);
            level.playSound(null, center.x + random.nextGaussian() * 5, center.y,
                    center.z + random.nextGaussian() * 5, SoundEvents.POWDER_SNOW_STEP,
                    SoundSource.WEATHER, 1.0F, 0.5F);
        }

        // ColdDomeService spreads and restores exposed terrain under a shared tick budget.
    }

    // ------------------------------------------------------------------
    // Passives, intro hooks, overexertion
    // ------------------------------------------------------------------

    /** Rime Walk: still water freezes under Tracy's feet as she walks. */
    public static void rimeWalk(ServerPlayer player) {
        if (player.isShiftKeyDown() || player.isSpectator()) {
            return;
        }
        BlockPos feet = player.blockPosition().below();
        for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-2, 0, -2), feet.offset(2, 0, 2))) {
            if (pos.distSqr(feet) <= 5) {
                TemporaryBlocks.freezeWater(player.serverLevel(), pos.immutable());
            }
        }
    }

    /** Overexertion: the cold turns inward. */
    public static void hypothermia(ServerPlayer player) {
        player.setTicksFrozen(MAXIMUM_CHILL_TICKS);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 2));
        player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 200, 1));
        ScreenTintPayload.send(player, 0x80B8E8FF, 100, ScreenTintPayload.Style.PULSE);
        player.sendSystemMessage(Component.literal(
                "Your own cold turns inward. You can't stop shivering.")
                .withStyle(ChatFormatting.DARK_AQUA));
    }

    public static void stopAll(ServerPlayer player) {
        ColdDomeService.stop(player);
        DELAYED.removeIf(task -> task.owner == player);
        BLIZZARDS.remove(player.getUUID());
        FROZEN.values().removeIf(frozen -> {
            if (frozen.target == player || player.getUUID().equals(frozen.casterId)) {
                thaw(frozen, false);
                return true;
            }
            return false;
        });
        CHILLED.values().removeIf(chilled -> {
            if (chilled.target == player || player.getUUID().equals(chilled.casterId)) {
                slow(chilled.target, 0);
                chilled.target.setTicksFrozen(0);
                return true;
            }
            return false;
        });
    }

    /** The topmost sturdy block within a few blocks of a height. */
    public static BlockPos surfaceNear(ServerLevel level, BlockPos around, int depth) {
        for (int dy = depth; dy >= -depth; dy--) {
            BlockPos pos = around.above(dy);
            BlockState state = level.getBlockState(pos);
            BlockState above = level.getBlockState(pos.above());
            boolean groundOrWater = state.isFaceSturdy(level, pos, Direction.UP)
                    || state.is(Blocks.WATER);
            if (groundOrWater && (above.isAir() || above.canBeReplaced())
                    && !above.is(Blocks.WATER)) {
                return pos;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Ticking
    // ------------------------------------------------------------------

    public static void tick(MinecraftServer server) {
        THAW_GRACE.values().removeIf(until -> until <= server.overworld().getGameTime());
        ColdDomeService.tick(server);
        long gameTime = server.overworld().getGameTime();

        Iterator<Delayed> delayed = DELAYED.iterator();
        List<Runnable> due = new ArrayList<>();
        while (delayed.hasNext()) {
            Delayed task = delayed.next();
            if (!task.owner.isAlive() || task.owner.isRemoved() || task.owner.level() != task.level
                    || EclipseService.arePowersSuppressed(task.owner)) {
                delayed.remove();
            } else if (task.level.getGameTime() >= task.at) {
                due.add(task.action);
                delayed.remove();
            }
        }
        due.forEach(Runnable::run);

        Iterator<Chilled> chilled = CHILLED.values().iterator();
        while (chilled.hasNext()) {
            Chilled entry = chilled.next();
            LivingEntity target = entry.target;
            if (!target.isAlive() || target.isRemoved() || immune(target)
                    || !target.level().dimension().equals(entry.dimension)) {
                slow(target, 0);
                target.setTicksFrozen(0);
                chilled.remove();
                continue;
            }
            long now = target.level().getGameTime();
            if (target.isOnFire()) entry.amount = 0;
            else if (now - entry.lastApplied > 40) entry.amount = Math.max(0, entry.amount - 3);
            target.setTicksFrozen(entry.amount);
            if (entry.amount == 0) {
                slow(target, 0);
                chilled.remove();
            } else if (now % 10 == 0) {
                slow(target, entry.amount);
                AbilityEffects.send((ServerLevel) target.level(), Kind.CRYO_CHILL, target.position(),
                        target.position().add(0, target.getBbHeight(), 0), target.getBbWidth());
            }
        }

        Iterator<Frozen> frozen = FROZEN.values().iterator();
        while (frozen.hasNext()) {
            Frozen entry = frozen.next();
            LivingEntity target = entry.target;
            long now = target.level().getGameTime();
            if (!target.isAlive() || target.isRemoved() || now >= entry.until || target.isOnFire() || immune(target)
                    || !target.level().dimension().equals(entry.dimension)) {
                thaw(entry, false);
                frozen.remove();
                continue;
            }
            // Held in place inside the ice, shivering under the frost.
            target.setTicksFrozen(Math.max(target.getTicksFrozen(), 160));
            target.setDeltaMovement(Vec3.ZERO);
            target.fallDistance = 0;
            if (target instanceof Mob mob) {
                mob.setTarget(null);
                mob.getNavigation().stop();
            }
            if (target instanceof ServerPlayer player) {
                if (player.position().distanceToSqr(entry.lockedAt) > 0.04D) {
                    player.teleportTo(entry.lockedAt.x, entry.lockedAt.y, entry.lockedAt.z);
                }
            } else if (target.position().distanceToSqr(entry.lockedAt) > 0.04D) {
                target.teleportTo(entry.lockedAt.x, entry.lockedAt.y, entry.lockedAt.z);
            }
            if (now % 20 == 0) PacketDistributor.sendToPlayersTrackingEntityAndSelf(target, payload(entry));
            if (now % 6 == 0 && target.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.SNOWFLAKE, target.getX(),
                        target.getY() + target.getBbHeight() / 2, target.getZ(),
                        2, target.getBbWidth() / 2, target.getBbHeight() / 2,
                        target.getBbWidth() / 2, 0.0D);
            }
        }

        for (Map.Entry<UUID, Blizzard> entry : List.copyOf(BLIZZARDS.entrySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                BLIZZARDS.remove(entry.getKey());
            } else if (gameTime >= entry.getValue().until
                    || EclipseService.arePowersSuppressed(player)) {
                endBlizzard(player);
            } else {
                tickBlizzard(player, entry.getValue(), gameTime);
            }
        }
    }

    public static void clear() {
        ColdDomeService.clear();
        List.copyOf(FROZEN.values()).forEach(entry -> thaw(entry, false));
        FROZEN.clear();
        for (Chilled entry : CHILLED.values()) {
            slow(entry.target, 0);
            entry.target.setTicksFrozen(0);
        }
        CHILLED.clear();
        BLIZZARDS.clear();
        DELAYED.clear();
        coldDamageDepth = 0;
        THAW_GRACE.clear();
    }

    private static void push(Entity entity, Vec3 velocity) {
        entity.setDeltaMovement(velocity);
        if (entity instanceof ServerPlayer player) {
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
        } else {
            entity.hurtMarked = true;
        }
    }

    private record Frozen(
            LivingEntity target,
            Vec3 lockedAt,
            long until,
            UUID casterId,
            ResourceKey<Level> dimension
    ) {
    }

    private static final class Chilled {
        final LivingEntity target;
        int amount;
        final long lastApplied;
        final UUID casterId;
        final ResourceKey<Level> dimension;
        Chilled(LivingEntity target, int amount, long lastApplied, UUID casterId) {
            this.target = target; this.amount = amount; this.lastApplied = lastApplied;
            this.casterId = casterId; this.dimension = target.level().dimension();
        }
    }

    private record Blizzard(long until, int powerLevel) {
    }

    private record Delayed(ServerPlayer owner, ServerLevel level, long at, Runnable action) {
    }
}
