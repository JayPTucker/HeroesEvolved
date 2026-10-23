package com.jayptucker.heroesevolved.electro;

import com.jayptucker.heroesevolved.ability.AbilityRanges;

import com.jayptucker.heroesevolved.ability.Targeting;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.events.EclipseService;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.ScreenTintPayload;
import com.jayptucker.heroesevolved.progression.MasteryService;
import com.jayptucker.heroesevolved.time.TemporalEntityEchoService;
import net.minecraft.ChatFormatting;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Electrokinesis, after Elle Bishop: chain lightning, an electrified
 * touch, called-down strikes, riding the lightning, and a storm nova.
 * Electricity and water do not mix, for her foes or for herself.
 */
public final class ElectroService {
    private static final int ELECTRIC_TINT = 0x5555AAFF;

    private static boolean dealingPowerDamage;

    private ElectroService() {
    }

    public static boolean isDealingPowerDamage() {
        return dealingPowerDamage;
    }

    // ------------------------------------------------------------------
    // Chain Lightning
    // ------------------------------------------------------------------

    /** A bolt from the fingertips that leaps between nearby foes. */
    public static void chainLightning(ServerPlayer player, int powerLevel) {
        ServerLevel level = player.serverLevel();
        Vec3 hand = handPosition(player);
        LivingEntity target = Targeting.livingEntityInSight(player, 16.0D,
                entity -> true);

        if (target == null) {
            BlockHitResult hit = level.clip(new ClipContext(
                    player.getEyePosition(),
                    player.getEyePosition().add(player.getLookAngle().scale(AbilityRanges.ranged(16.0D))),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            drawArc(level, hand, hit.getLocation(), level.getRandom());
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, hit.getLocation().x,
                    hit.getLocation().y, hit.getLocation().z, 12, 0.2D, 0.2D, 0.2D, 0.3D);
        } else {
            float damage = 4.0F + powerLevel * 1.5F;
            int jumps = powerLevel - 1;
            Set<UUID> struck = new HashSet<>();
            Vec3 from = hand;
            LivingEntity current = target;

            for (int hop = 0; current != null && hop <= jumps; hop++) {
                Vec3 to = current.getBoundingBox().getCenter();
                drawArc(level, from, to, level.getRandom());
                shock(player, current, damage);
                struck.add(current.getUUID());

                from = to;
                damage *= 0.75F;
                current = nearestUnstruck(level, current, player, struck, AbilityRanges.ranged(5.0D));
            }
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.6F, 1.7F);
        backfireIfWet(player, powerLevel);
        MasteryService.markPowerUse(player, ModAbilities.ELECTROKINESIS_ID);
    }

    // ------------------------------------------------------------------
    // Static Touch (Level 2 passive)
    // ------------------------------------------------------------------

    public static final int STATIC_TOUCH_UNLOCK_LEVEL = 2;
    public static final int STATIC_TOUCH_ENERGY_COST = 4;

    /**
     * Elle's touch carries a charge. Each melee hit that she can pay for
     * shocks and briefly stuns its target.
     *
     * @return the extra damage to add, or 0 if there was no energy for it
     */
    public static float staticTouch(
            ServerPlayer player,
            LivingEntity target,
            int powerLevel
    ) {
        if (!PlayerEnergyService.tryConsume(player, STATIC_TOUCH_ENERGY_COST)) {
            return 0.0F;
        }
        MasteryService.awardEnergySpent(player, STATIC_TOUCH_ENERGY_COST);

        ServerLevel level = player.serverLevel();
        drawArc(level, handPosition(player), target.getBoundingBox().getCenter(),
                level.getRandom());
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(),
                target.getY() + target.getBbHeight() / 2, target.getZ(),
                16, 0.3D, 0.4D, 0.3D, 0.4D);
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.4F, 2.0F);
        // A jolt locks the muscles for a moment.
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 3));
        float bonus = 3.0F + powerLevel;
        return target.isInWaterRainOrBubble() ? bonus * 1.5F : bonus;
    }

    // ------------------------------------------------------------------
    // Thunderstrike
    // ------------------------------------------------------------------

    /**
     * Calls lightning onto the spot Elle points at. It behaves like real
     * lightning for whatever it hits (creepers charge, pigs turn), but
     * never sets the terrain alight.
     */
    public static boolean thunderstrike(ServerPlayer player, int powerLevel) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        BlockHitResult hit = level.clip(new ClipContext(eye,
                eye.add(player.getLookAngle().scale(AbilityRanges.ranged(48.0D))),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            player.displayClientMessage(Component.literal(
                    "There is nothing there to strike."), true);
            return false;
        }

        Vec3 impact = hit.getLocation();
        LightningBolt bolt = visualBolt(level, impact, player);
        AbilityEffects.send(level, Kind.ELECTRIC_IMPACT,
                impact.add(0.0D, 0.1D, 0.0D), impact, 3.5D);
        float damage = 4.0F + powerLevel * 2.0F;
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(impact, impact).inflate(3.5D),
                entity -> entity != player && entity.isAlive())) {
            if (TemporalEntityEchoService.isReplaying(entity)) {
                TemporalEntityEchoService.disturb(entity, player);
            }
            // Vanilla's lightning reactions: charged creepers, witches,
            // zombified piglins and the rest.
            entity.thunderHit(level, bolt);
            shock(player, entity, damage);
        }

        CameraShakePayload.sendAround(level, impact, 24.0D, 1.4F, 10);
        ScreenTintPayload.sendAround(level, impact, 32.0D, 0x60FFFFFF, 6,
                ScreenTintPayload.Style.FLASH);
        MasteryService.markPowerUse(player, ModAbilities.ELECTROKINESIS_ID);
        return true;
    }

    // ------------------------------------------------------------------
    // Ride the Lightning
    // ------------------------------------------------------------------

    /** Elle becomes a streak of current and reappears down the line. */
    public static void rideTheLightning(ServerPlayer player, int powerLevel) {
        ServerLevel level = player.serverLevel();
        Vec3 direction = player.getLookAngle().multiply(1.0D, 0.35D, 1.0D)
                .normalize();
        double maximumDistance = AbilityRanges.ranged(10.0D + powerLevel);
        Vec3 start = player.position();
        Vec3 end = start;

        // Walk forward until the body would collide with something.
        for (double distance = 0.5D; distance <= maximumDistance; distance += 0.5D) {
            Vec3 candidate = start.add(direction.scale(distance));
            AABB box = player.getBoundingBox().move(candidate.subtract(start));
            if (!level.noCollision(player, box)) {
                break;
            }
            end = candidate;
        }
        if (end.distanceToSqr(start) < 1.0D) {
            return;
        }

        float damage = 4.0F + powerLevel;
        AABB path = new AABB(start, end).inflate(1.2D);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, path,
                entity -> entity != player && entity.isAlive())) {
            shock(player, entity, damage);
        }

        drawArc(level, start.add(0.0D, 1.0D, 0.0D), end.add(0.0D, 1.0D, 0.0D),
                level.getRandom());
        drawArc(level, start.add(0.0D, 0.4D, 0.0D), end.add(0.0D, 1.5D, 0.0D),
                level.getRandom());
        level.sendParticles(ParticleTypes.FLASH, start.x, start.y + 1.0D, start.z,
                1, 0.0D, 0.0D, 0.0D, 0.0D);

        player.teleportTo(end.x, end.y, end.z);
        player.resetFallDistance();
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, end.x, end.y + 1.0D,
                end.z, 30, 0.3D, 0.6D, 0.3D, 0.5D);
        level.playSound(null, end.x, end.y, end.z,
                SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.8F, 1.4F);
        ScreenTintPayload.send(player, 0x40AACCFF, 6, ScreenTintPayload.Style.FLASH);
        MasteryService.markPowerUse(player, ModAbilities.ELECTROKINESIS_ID);
    }

    // ------------------------------------------------------------------
    // Storm Surge
    // ------------------------------------------------------------------

    /**
     * Everything Elle has, released at once. A storm overhead feeds it,
     * nearly doubling its reach. Creepers caught in it become charged.
     */
    public static void stormSurge(ServerPlayer player, int powerLevel) {
        ServerLevel level = player.serverLevel();
        boolean storm = level.isRainingAt(player.blockPosition());
        double radius = storm ? 16.0D : 10.0D;
        float damage = 8.0F + powerLevel;
        Vec3 center = player.position().add(0.0D, 1.0D, 0.0D);
        RandomSource random = level.getRandom();

        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class,
                        player.getBoundingBox().inflate(radius),
                        entity -> entity != player && entity.isAlive()
                                && entity.distanceToSqr(player) <= radius * radius)
                .stream()
                .sorted(Comparator.comparingDouble(player::distanceToSqr))
                .limit(24)
                .toList();

        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        for (LivingEntity target : targets) {
            drawArc(level, center, target.getBoundingBox().getCenter(), random);
            if (target instanceof Creeper creeper && bolt != null) {
                creeper.thunderHit(level, bolt);
            }
            shock(player, target, damage);
            Vec3 away = target.position().subtract(player.position())
                    .multiply(1.0D, 0.0D, 1.0D);
            if (away.lengthSqr() > 0.01D) {
                setVelocity(target, away.normalize().scale(1.2D).add(0.0D, 0.45D, 0.0D));
            }
        }

        // A crown of visual strikes around Elle.
        for (int strike = 0; strike < (storm ? 6 : 3); strike++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double distance = 3.0D + random.nextDouble() * (radius - 3.0D);
            Vec3 at = player.position().add(Math.cos(angle) * distance, 0.0D,
                    Math.sin(angle) * distance);
            visualBolt(level, at, player);
        }

        level.sendParticles(ParticleTypes.FLASH, center.x, center.y, center.z,
                1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.x, center.y,
                center.z, 120, radius / 3, 1.0D, radius / 3, 0.6D);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 2.0F, 1.2F);
        CameraShakePayload.sendAround(level, center, radius * 2.0D, 2.0F, 16);
        ScreenTintPayload.sendAround(level, center, radius * 1.5D, 0x80FFFFFF, 10,
                ScreenTintPayload.Style.FLASH);

        if (player.isInWaterOrBubble()) {
            // Discharging this much power in water is nearly suicidal.
            player.hurt(player.damageSources().lightningBolt(), 8.0F);
            player.displayClientMessage(Component.literal(
                    "The water drives the surge straight back into you!")
                    .withStyle(ChatFormatting.RED), true);
        }
        MasteryService.markPowerUse(player, ModAbilities.ELECTROKINESIS_ID);
    }

    // ------------------------------------------------------------------
    // Passives and overexertion
    // ------------------------------------------------------------------

    /**
     * Lightning cannot hurt Elle; it recharges her instead.
     *
     * @return true when the lightning damage should be cancelled
     */
    public static boolean absorbLightning(ServerPlayer player) {
        PlayerEnergyService.restore(player, 30);
        player.serverLevel().sendParticles(ParticleTypes.ELECTRIC_SPARK,
                player.getX(), player.getY() + 1.0D, player.getZ(),
                40, 0.3D, 0.8D, 0.3D, 0.5D);
        player.displayClientMessage(Component.literal(
                "You drink in the lightning.").withStyle(ChatFormatting.AQUA), true);
        ScreenTintPayload.send(player, ELECTRIC_TINT, 30,
                ScreenTintPayload.Style.VIGNETTE);
        return true;
    }

    /** Thunderstorms charge Elle faster: bonus energy per second. */
    public static int stormEnergyBonus(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        return level.isThundering() && level.isRainingAt(player.blockPosition())
                ? 2 : 0;
    }

    /** Overexertion: Elle loses control and the current lashes out at everyone. */
    public static void uncontrolledDischarge(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 center = player.position().add(0.0D, 1.0D, 0.0D);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(5.0D),
                entity -> entity != player && entity.isAlive())) {
            drawArc(level, center, entity.getBoundingBox().getCenter(),
                    level.getRandom());
            entity.hurt(player.damageSources().lightningBolt(), 3.0F);
        }
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 1));
        player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 200, 1));
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0F, 0.6F);
        player.sendSystemMessage(Component.literal(
                "You lose control of the current!").withStyle(ChatFormatting.RED));
    }

    // ------------------------------------------------------------------
    // Ball Lightning
    // ------------------------------------------------------------------

    private static final List<Orb> ORBS = new ArrayList<>();
    private static final int ORB_LIFETIME_TICKS = AbilityRanges.ranged(50);

    /**
     * A crackling sphere of current that drifts forward, lashing out at
     * anything near it, and bursts when it touches something solid.
     */
    public static void ballLightning(ServerPlayer player, int powerLevel) {
        Vec3 start = handPosition(player);
        ORBS.add(new Orb(player.getUUID(), player.serverLevel(), start,
                player.getLookAngle().normalize().scale(0.55D), powerLevel));
        player.serverLevel().playSound(null, player.getX(), player.getY(),
                player.getZ(), SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS,
                0.8F, 1.8F);
        backfireIfWet(player, powerLevel);
        MasteryService.markPowerUse(player, ModAbilities.ELECTROKINESIS_ID);
    }

    public static void tick(MinecraftServer server) {
        java.util.Iterator<Orb> iterator = ORBS.iterator();
        while (iterator.hasNext()) {
            Orb orb = iterator.next();
            ServerPlayer owner = server.getPlayerList().getPlayer(orb.ownerId);
            if (owner == null || owner.level() != orb.level) {
                iterator.remove();
                continue;
            }
            if (tickOrb(owner, orb)) {
                iterator.remove();
            }
        }
    }

    /** @return true once the orb has burst */
    private static boolean tickOrb(ServerPlayer owner, Orb orb) {
        ServerLevel level = orb.level;
        orb.ageTicks++;
        Vec3 next = orb.position.add(orb.velocity);

        BlockHitResult blockHit = level.clip(new ClipContext(orb.position, next,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        LivingEntity touched = level.getEntitiesOfClass(LivingEntity.class,
                        new AABB(next, next).inflate(0.6D),
                        entity -> entity != owner && entity.isAlive())
                .stream().findFirst().orElse(null);

        if (blockHit.getType() != HitResult.Type.MISS || touched != null
                || orb.ageTicks >= ORB_LIFETIME_TICKS) {
            burst(owner, orb, blockHit.getType() != HitResult.Type.MISS
                    ? blockHit.getLocation() : next);
            return true;
        }

        orb.position = next;
        // Animate a moving shell between updates rather than a static spark cloud.
        if (orb.ageTicks % 2 == 0) {
            AbilityEffects.send(level, Kind.ELECTRIC_ORB,
                    next, next.add(orb.velocity), 0.42D);
        }

        // Every few ticks it lashes the nearest foe within reach.
        if (orb.ageTicks % 4 == 0) {
            LivingEntity nearest = level.getEntitiesOfClass(LivingEntity.class,
                            new AABB(next, next).inflate(4.0D),
                            entity -> entity != owner && entity.isAlive())
                    .stream()
                    .min(Comparator.comparingDouble(entity -> entity.distanceToSqr(next)))
                    .orElse(null);
            if (nearest != null) {
                drawArc(level, next, nearest.getBoundingBox().getCenter(),
                        level.getRandom());
                shock(owner, nearest, 3.0F + orb.powerLevel);
            }
        }
        if (orb.ageTicks % 10 == 0) {
            level.playSound(null, next.x, next.y, next.z,
                    SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.3F, 2.0F);
        }
        return false;
    }

    private static void burst(ServerPlayer owner, Orb orb, Vec3 at) {
        ServerLevel level = orb.level;
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(at, at).inflate(3.0D),
                entity -> entity != owner && entity.isAlive())) {
            drawArc(level, at, entity.getBoundingBox().getCenter(), level.getRandom());
            shock(owner, entity, 5.0F + orb.powerLevel);
        }
        level.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z,
                40, 0.5D, 0.5D, 0.5D, 0.6D);
        AbilityEffects.send(level, Kind.ELECTRIC_IMPACT, at, at.add(0.0D, 1.0D, 0.0D), 3.0D);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_IMPACT,
                SoundSource.PLAYERS, 0.9F, 1.3F);
    }

    private static final class Orb {
        private final UUID ownerId;
        private final ServerLevel level;
        private final Vec3 velocity;
        private final int powerLevel;
        private Vec3 position;
        private int ageTicks;

        private Orb(UUID ownerId, ServerLevel level, Vec3 position,
                    Vec3 velocity, int powerLevel) {
            this.ownerId = ownerId;
            this.level = level;
            this.position = position;
            this.velocity = velocity;
            this.powerLevel = powerLevel;
        }
    }

    /** Faint static around Elle while a storm is feeding her. */
    public static void ambientCrackle(ServerPlayer player, long gameTime) {
        if (gameTime % 10 == 0 && stormEnergyBonus(player) > 0) {
            player.serverLevel().sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    player.getX(), player.getY() + 1.0D, player.getZ(),
                    3, 0.35D, 0.6D, 0.35D, 0.15D);
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static void shock(ServerPlayer player, LivingEntity target, float damage) {
        // Water conducts: wet targets take half again as much.
        float amount = target.isInWaterRainOrBubble() ? damage * 1.5F : damage;
        // Power damage uses the player-attack type for aggro and kill credit;
        // the flag stops Static Touch from treating it as a punch.
        dealingPowerDamage = true;
        try {
            target.hurt(player.damageSources().playerAttack(player), amount);
        } finally {
            dealingPowerDamage = false;
        }
        if (target.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(),
                    target.getY() + target.getBbHeight() / 2, target.getZ(),
                    10, 0.25D, 0.35D, 0.25D, 0.3D);
        }
    }

    private static void backfireIfWet(ServerPlayer player, int powerLevel) {
        if (player.isInWaterOrBubble()) {
            player.hurt(player.damageSources().lightningBolt(), 1.0F + powerLevel);
            player.displayClientMessage(Component.literal(
                    "The water carries the current back into you!")
                    .withStyle(ChatFormatting.RED), true);
        }
    }

    private static LivingEntity nearestUnstruck(
            ServerLevel level,
            LivingEntity from,
            ServerPlayer caster,
            Set<UUID> struck,
            double range
    ) {
        return level.getEntitiesOfClass(LivingEntity.class,
                        from.getBoundingBox().inflate(range),
                        entity -> entity != caster && entity.isAlive()
                                && !struck.contains(entity.getUUID()))
                .stream()
                .min(Comparator.comparingDouble(from::distanceToSqr))
                .orElse(null);
    }

    private static LightningBolt visualBolt(
            ServerLevel level,
            Vec3 position,
            ServerPlayer cause
    ) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(position.x, position.y, position.z);
            // Visual only: our own code applies the damage, and visual bolts
            // never start fires in the terrain.
            bolt.setVisualOnly(true);
            bolt.setCause(cause);
            level.addFreshEntity(bolt);
        }
        return bolt;
    }

    /** A jagged arc of current between two points. */
    public static void drawArc(
            ServerLevel level,
            Vec3 from,
            Vec3 to,
            RandomSource random
    ) {
        if (from.distanceToSqr(to) < 0.0001D) {
            return;
        }
        AbilityEffects.send(level, Kind.ELECTRIC_ARC, from, to, 1.0D);
    }

    private static Vec3 handPosition(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 right = new Vec3(-look.z, 0.0D, look.x).normalize();
        return player.getEyePosition().add(look.scale(0.5D))
                .add(right.scale(0.3D)).add(0.0D, -0.35D, 0.0D);
    }

    private static void setVelocity(Entity entity, Vec3 velocity) {
        entity.setDeltaMovement(velocity);
        if (entity instanceof ServerPlayer player) {
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
        } else {
            entity.hurtMarked = true;
        }
    }
}
