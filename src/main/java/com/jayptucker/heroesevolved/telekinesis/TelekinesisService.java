package com.jayptucker.heroesevolved.telekinesis;

import com.jayptucker.heroesevolved.ability.AbilityRanges;

import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;

import com.jayptucker.heroesevolved.ability.Targeting;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.entity.TemporalEchoEntity;
import com.jayptucker.heroesevolved.events.EclipseService;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Telekinesis, after Sylar: shove, seize, disarm, stop bullets in the air,
 * and hold a whole crowd aloft before dashing them to the ground.
 */
public final class TelekinesisService {
    private static final double CONE_COSINE = Math.cos(Math.toRadians(35.0D));
    private static final double GRIP_DISTANCE = 4.0D;
    private static final int LIFT_DURATION_TICKS = 60;

    private static final Map<UUID, Impact> IMPACTS = new HashMap<>();
    private static final Map<UUID, Grip> GRIPS = new HashMap<>();
    private static final Map<UUID, BulletStop> BULLET_STOPS = new HashMap<>();
    private static final Map<UUID, Lift> LIFTS = new HashMap<>();

    private TelekinesisService() {
    }

    // ------------------------------------------------------------------
    // Telekinetic Push
    // ------------------------------------------------------------------

    /** A cone of force. Whatever it throws into a wall takes extra damage. */
    public static void push(ServerPlayer player, int powerLevel) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double strength = 1.4D + powerLevel * 0.2D;
        double range = AbilityRanges.ranged(8.0D);

        for (Entity entity : level.getEntities(player,
                player.getBoundingBox().inflate(range), Entity::isAlive)) {
            if (entity instanceof TemporalEchoEntity) {
                continue;
            }
            Vec3 toEntity = entity.getBoundingBox().getCenter().subtract(eye);
            double distance = toEntity.length();
            if (distance > range || distance < 0.01D
                    || toEntity.normalize().dot(look) < CONE_COSINE) {
                continue;
            }

            double falloff = 1.0D - 0.5D * distance / range;
            setVelocity(entity, look.scale(strength * falloff).add(0.0D, 0.3D, 0.0D));
            if (entity instanceof LivingEntity living) {
                if (TemporalEntityEchoService.isReplaying(living)) {
                    TemporalEntityEchoService.disturb(living, player);
                }
                IMPACTS.put(living.getUUID(), new Impact(living, player.getUUID(),
                        level.getGameTime() + 15, 3.0F + powerLevel, false));
            }
            level.sendParticles(ParticleTypes.SMALL_GUST, entity.getX(),
                    entity.getY() + entity.getBbHeight() / 2, entity.getZ(),
                    3, 0.2D, 0.2D, 0.2D, 0.0D);
        }

        for (int index = 1; index <= 4; index++) {
            Vec3 point = eye.add(look.scale(index * 1.8D));
            level.sendParticles(ParticleTypes.GUST, point.x, point.y, point.z,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS,
                0.8F, 0.7F);
        MasteryService.markPowerUse(player, ModAbilities.TELEKINESIS_ID);
    }

    // ------------------------------------------------------------------
    // Telekinetic Grip
    // ------------------------------------------------------------------

    /** True while any telekinetic holds this creature in the air. */
    public static boolean isHeld(net.minecraft.world.entity.Entity entity) {
        return GRIPS.values().stream().anyMatch(grip -> grip.target == entity);
    }

    public static boolean isGripping(ServerPlayer player) {
        return GRIPS.containsKey(player.getUUID());
    }

    public static LivingEntity findGripTarget(ServerPlayer player) {
        return Targeting.livingEntityInSight(player, 16.0D,
                target -> !(target instanceof TemporalEchoEntity)
                        && !TemporalEntityEchoService.isPastCopy(target));
    }

    /** Seize a creature and hold it in the air before you, choking. */
    public static boolean grip(ServerPlayer player, int powerLevel) {
        LivingEntity target = findGripTarget(player);
        if (target == null) {
            return false;
        }

        GRIPS.put(player.getUUID(), new Grip(target,
                player.serverLevel().getGameTime() + 20L * (6 + powerLevel),
                powerLevel));
        AbilityEffects.send(player.serverLevel(),
                Kind.PSYCHIC_BIND,
                target.position(), target.position(), Math.max(0.5D, target.getBbWidth()));
        player.serverLevel().playSound(null, target.getX(), target.getY(),
                target.getZ(), SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS,
                0.8F, 0.6F);
        if (target instanceof ServerPlayer gripped) {
            gripped.displayClientMessage(Component.literal(
                    "An invisible force lifts you off your feet!")
                    .withStyle(ChatFormatting.DARK_PURPLE), true);
            ScreenTintPayload.send(gripped, 0x40301040, 40,
                    ScreenTintPayload.Style.VIGNETTE);
        }
        MasteryService.markPowerUse(player, ModAbilities.TELEKINESIS_ID);
        return true;
    }

    /** Let go with a flick: the held creature flies where you look. */
    public static boolean throwGripped(ServerPlayer player) {
        Grip grip = GRIPS.remove(player.getUUID());
        if (grip == null || !grip.target.isAlive()) {
            return false;
        }

        setVelocity(grip.target, player.getLookAngle().scale(2.6D)
                .add(0.0D, 0.2D, 0.0D));
        IMPACTS.put(grip.target.getUUID(), new Impact(grip.target,
                player.getUUID(), player.serverLevel().getGameTime() + AbilityRanges.ranged(30),
                4.0F + grip.powerLevel, false));
        player.serverLevel().playSound(null, player.getX(), player.getY(),
                player.getZ(), SoundEvents.WIND_CHARGE_BURST.value(),
                SoundSource.PLAYERS, 1.0F, 0.5F);
        return true;
    }

    // ------------------------------------------------------------------
    // Disarm
    // ------------------------------------------------------------------

    /**
     * Rip the weapon from a creature's hands and draw every loose item and
     * experience orb nearby to you.
     */
    public static void disarm(ServerPlayer player, int powerLevel) {
        ServerLevel level = player.serverLevel();
        LivingEntity target = findGripTarget(player);
        Vec3 hand = player.getEyePosition().add(player.getLookAngle().scale(0.5D))
                .add(0.0D, -0.3D, 0.0D);
        if (target != null) {
            AbilityEffects.send(level, Kind.PSYCHIC_TETHER, hand,
                    target.getEyePosition(), 0.45D);
        }

        if (target instanceof Mob mob && !mob.getMainHandItem().isEmpty()) {
            ItemStack weapon = mob.getMainHandItem().copy();
            // Taking it from the hand also removes its chance to drop later.
            mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            ItemEntity flying = new ItemEntity(level, mob.getX(), mob.getEyeY(),
                    mob.getZ(), weapon);
            flying.setDeltaMovement(towards(flying.position(), player, 0.12D));
            flying.setPickUpDelay(10);
            level.addFreshEntity(flying);
            level.playSound(null, mob.getX(), mob.getY(), mob.getZ(),
                    SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.6F, 1.6F);
            player.displayClientMessage(Component.literal(
                    "You tear the " + weapon.getHoverName().getString()
                            + " from its grip.")
                    .withStyle(ChatFormatting.DARK_PURPLE), true);
        } else if (target != null) {
            // Players keep their items; they are yanked toward you instead.
            setVelocity(target, towards(target.position(), player, 0.15D)
                    .add(0.0D, 0.3D, 0.0D));
        }

        double radius = 8.0D + powerLevel * 2.0D;
        int visibleTethers = 0;
        for (Entity loose : level.getEntities(player,
                player.getBoundingBox().inflate(radius),
                entity -> entity instanceof ItemEntity
                        || entity instanceof ExperienceOrb)) {
            setVelocity(loose, towards(loose.position(), player, 0.1D)
                    .add(0.0D, 0.25D, 0.0D));
            if (visibleTethers++ < 8) {
                AbilityEffects.send(level, Kind.PSYCHIC_TETHER,
                        hand, loose.position().add(0, 0.2D, 0), 0.2D);
            }
        }
        MasteryService.markPowerUse(player, ModAbilities.TELEKINESIS_ID);
    }

    // ------------------------------------------------------------------
    // Bullet Stop
    // ------------------------------------------------------------------

    public static boolean hasBulletStop(ServerPlayer player) {
        return BULLET_STOPS.containsKey(player.getUUID());
    }

    /**
     * Incoming projectiles freeze in the air around you. When it ends
     * they all fly where you are looking.
     */
    public static void bulletStop(ServerPlayer player, int powerLevel) {
        int durationTicks = 20 * (6 + powerLevel);
        BULLET_STOPS.put(player.getUUID(), new BulletStop(
                player.serverLevel().getGameTime() + durationTicks));
        ScreenTintPayload.send(player, 0x30C8B8FF, durationTicks,
                ScreenTintPayload.Style.PULSE);
        player.serverLevel().playSound(null, player.getX(), player.getY(),
                player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS,
                0.8F, 0.6F);
        MasteryService.markPowerUse(player, ModAbilities.TELEKINESIS_ID);
    }

    public static void releaseBulletStop(ServerPlayer player) {
        BulletStop stop = BULLET_STOPS.remove(player.getUUID());
        if (stop == null) {
            return;
        }

        Vec3 look = player.getLookAngle();
        for (FrozenProjectile frozen : stop.frozen) {
            Projectile projectile = frozen.projectile;
            if (projectile.isRemoved()) {
                continue;
            }
            projectile.setNoGravity(frozen.hadNoGravity);
            projectile.setOwner(player);
            projectile.setDeltaMovement(look.scale(2.4D));
            if (projectile.level() instanceof ServerLevel level) {
                AbilityEffects.send(level, Kind.PSYCHIC_IMPACT, projectile.position(),
                        projectile.position().add(look), 0.45D);
                level.getChunkSource().broadcastAndSend(projectile,
                        new ClientboundSetEntityMotionPacket(projectile));
            }
        }
        if (!stop.frozen.isEmpty()) {
            player.serverLevel().playSound(null, player.getX(), player.getY(),
                    player.getZ(), SoundEvents.WIND_CHARGE_BURST.value(),
                    SoundSource.PLAYERS, 1.0F, 1.2F);
        }
    }

    // ------------------------------------------------------------------
    // Telekinetic Storm
    // ------------------------------------------------------------------

    public static boolean hasActiveLift(ServerPlayer player) {
        return LIFTS.containsKey(player.getUUID());
    }

    /** Everyone nearby rises helplessly into the air, then is dashed down. */
    public static void telekineticStorm(ServerPlayer player, int powerLevel) {
        ServerLevel level = player.serverLevel();
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class,
                        player.getBoundingBox().inflate(12.0D),
                        entity -> entity != player && entity.isAlive()
                                && !(entity instanceof TemporalEchoEntity)
                                && entity.distanceToSqr(player) <= 144.0D)
                .stream().limit(20).toList();

        List<Lifted> lifted = new ArrayList<>();
        for (LivingEntity target : targets) {
            if (TemporalEntityEchoService.isReplaying(target)) {
                TemporalEntityEchoService.disturb(target, player);
            }
            lifted.add(new Lifted(target, target.getY() + 4.0D
                    + level.getRandom().nextDouble() * 2.0D));
        }
        LIFTS.put(player.getUUID(), new Lift(lifted,
                level.getGameTime() + LIFT_DURATION_TICKS, powerLevel));

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 1.2F, 0.6F);
        ScreenTintPayload.send(player, 0x30200830, LIFT_DURATION_TICKS,
                ScreenTintPayload.Style.VIGNETTE);
        MasteryService.markPowerUse(player, ModAbilities.TELEKINESIS_ID);
    }

    // ------------------------------------------------------------------
    // Passives, awakening and overexertion
    // ------------------------------------------------------------------

    /** Telekinetic reach: loose items drift toward you on their own. */
    public static void drawNearbyItems(ServerPlayer player) {
        for (ItemEntity item : player.serverLevel().getEntitiesOfClass(
                ItemEntity.class, player.getBoundingBox().inflate(6.0D),
                // Fresh drops are left alone, or you could never drop items.
                item -> item.getAge() > 40)) {
            item.setDeltaMovement(item.getDeltaMovement()
                    .add(towards(item.position(), player, 0.02D)));
        }
    }

    /** Awakening: everything loose around you is flung away at once. */
    public static void awaken(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        for (Entity entity : level.getEntities(player,
                player.getBoundingBox().inflate(6.0D), Entity::isAlive)) {
            Vec3 away = entity.position().subtract(player.position());
            if (away.lengthSqr() < 0.01D) {
                continue;
            }
            setVelocity(entity, away.normalize().scale(1.1D).add(0.0D, 0.5D, 0.0D));
        }
        level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, player.getX(),
                player.getY() + 1.0D, player.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS,
                1.2F, 0.5F);
        player.sendSystemMessage(Component.literal(
                "Everything around you flies away from you... did you do that?")
                .withStyle(ChatFormatting.DARK_PURPLE));
    }

    /** Overexertion: a splitting migraine. */
    public static void migraine(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 160, 0));
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0));
        player.sendSystemMessage(Component.literal(
                "A splitting migraine blinds you.").withStyle(ChatFormatting.DARK_RED));
    }

    public static void stopAll(ServerPlayer player) {
        GRIPS.remove(player.getUUID());
        LIFTS.remove(player.getUUID());
        releaseBulletStop(player);
    }

    // ------------------------------------------------------------------
    // Ticking
    // ------------------------------------------------------------------

    public static void tick(MinecraftServer server) {
        long gameTime = server.overworld().getGameTime();
        tickGrips(server, gameTime);
        tickBulletStops(server, gameTime);
        tickLifts(server, gameTime);
        tickImpacts(server, gameTime);
    }

    private static void tickGrips(MinecraftServer server, long gameTime) {
        Iterator<Map.Entry<UUID, Grip>> iterator = GRIPS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Grip> entry = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Grip grip = entry.getValue();
            LivingEntity target = grip.target;
            if (player == null || !target.isAlive() || target.isRemoved()
                    || target.level() != player.level()
                    || target.distanceToSqr(player) > Math.pow(AbilityRanges.ranged(24.0D), 2)
                    || gameTime >= grip.until
                    || EclipseService.arePowersSuppressed(player)) {
                iterator.remove();
                continue;
            }

            Vec3 hold = player.getEyePosition().add(player.getLookAngle()
                    .scale(GRIP_DISTANCE + target.getBbWidth()))
                    .subtract(0.0D, target.getBbHeight() / 2.0D, 0.0D);
            Vec3 pull = hold.subtract(target.position()).scale(0.35D);
            if (pull.length() > 1.2D) {
                pull = pull.normalize().scale(1.2D);
            }
            setVelocity(target, pull);
            target.resetFallDistance();

            if (gameTime % 20 == 0) {
                // Sylar's grip crushes.
                target.hurt(player.damageSources().playerAttack(player), 1.0F);
            }
            if (gameTime % 10 == 0) {
                // The hand stays outstretched toward whatever it holds.
                com.jayptucker.heroesevolved.network.ArmPosePayload.show(player,
                        com.jayptucker.heroesevolved.ability.ArmPose.ONE_HAND, 15);
            }
            if (gameTime % 4 == 0 && target.level() instanceof ServerLevel level) {
                AbilityEffects.send(level,
                        Kind.PSYCHIC_BIND,
                        target.position(), target.position(), Math.max(0.5D, target.getBbWidth()));
            }
        }
    }

    private static void tickBulletStops(MinecraftServer server, long gameTime) {
        for (Map.Entry<UUID, BulletStop> entry : List.copyOf(BULLET_STOPS.entrySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            BulletStop stop = entry.getValue();
            if (player == null) {
                BULLET_STOPS.remove(entry.getKey());
                continue;
            }
            if (gameTime >= stop.until || EclipseService.arePowersSuppressed(player)) {
                releaseBulletStop(player);
                continue;
            }

            ServerLevel level = player.serverLevel();
            if (gameTime % 12 == 0) {
                AbilityEffects.at(player,
                        Kind.PSYCHIC_FIELD, 3.0D);
            }
            for (Projectile projectile : level.getEntitiesOfClass(Projectile.class,
                    player.getBoundingBox().inflate(6.0D),
                    projectile -> projectile.getOwner() != player
                            && projectile.getDeltaMovement().lengthSqr() > 0.01D)) {
                if (stop.frozen.stream().noneMatch(f -> f.projectile == projectile)) {
                    stop.frozen.add(new FrozenProjectile(projectile,
                            projectile.isNoGravity()));
                    AbilityEffects.send(level, Kind.PSYCHIC_IMPACT,
                            projectile.position(), projectile.position().add(0, 1, 0), 0.3D);
                    level.playSound(null, projectile.getX(), projectile.getY(),
                            projectile.getZ(), SoundEvents.AMETHYST_BLOCK_HIT,
                            SoundSource.PLAYERS, 0.6F, 1.8F);
                }
            }

            for (FrozenProjectile frozen : stop.frozen) {
                Projectile projectile = frozen.projectile;
                if (projectile.isRemoved()) {
                    continue;
                }
                boolean wasMoving = projectile.getDeltaMovement().lengthSqr() > 0.0001D;
                projectile.setNoGravity(true);
                projectile.setDeltaMovement(Vec3.ZERO);
                if (wasMoving) {
                    level.getChunkSource().broadcastAndSend(projectile,
                            new ClientboundSetEntityMotionPacket(projectile));
                }
            }
        }
    }

    private static void tickLifts(MinecraftServer server, long gameTime) {
        Iterator<Map.Entry<UUID, Lift>> iterator = LIFTS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Lift> entry = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Lift lift = entry.getValue();
            if (player == null || EclipseService.arePowersSuppressed(player)) {
                iterator.remove();
                continue;
            }

            if (gameTime >= lift.releaseAt) {
                iterator.remove();
                slamDown(player, lift, gameTime);
                continue;
            }

            for (Lifted lifted : lift.targets) {
                LivingEntity target = lifted.entity;
                if (!target.isAlive() || target.level() != player.level()) {
                    continue;
                }
                // Hover with a slow, helpless spin around the caster.
                Vec3 offset = target.position().subtract(player.position());
                Vec3 swirl = new Vec3(-offset.z, 0.0D, offset.x).normalize().scale(0.06D);
                double rise = (lifted.hoverY - target.getY()) * 0.25D;
                setVelocity(target, new Vec3(swirl.x, Math.clamp(rise, -0.3D, 0.6D),
                        swirl.z));
                target.resetFallDistance();
                if (gameTime % 5 == 0 && target.level() instanceof ServerLevel level) {
                    AbilityEffects.send(level,
                            Kind.PSYCHIC_BIND,
                            target.position(), target.position(), Math.max(0.5D, target.getBbWidth()));
                }
            }
        }
    }

    private static void slamDown(ServerPlayer player, Lift lift, long gameTime) {
        for (Lifted lifted : lift.targets) {
            LivingEntity target = lifted.entity;
            if (!target.isAlive() || target.level() != player.level()) {
                continue;
            }
            setVelocity(target, new Vec3(0.0D, -2.4D, 0.0D));
            IMPACTS.put(target.getUUID(), new Impact(target, player.getUUID(),
                    gameTime + 40, 6.0F + lift.powerLevel, true));
        }
        player.serverLevel().playSound(null, player.getX(), player.getY(),
                player.getZ(), SoundEvents.WIND_CHARGE_BURST.value(),
                SoundSource.PLAYERS, 1.5F, 0.4F);
        CameraShakePayload.sendAround(player.serverLevel(), player.position(),
                24.0D, 1.2F, 10);
    }

    /** Damage for things thrown into walls, or slammed into the ground. */
    private static void tickImpacts(MinecraftServer server, long gameTime) {
        Iterator<Impact> iterator = IMPACTS.values().iterator();
        while (iterator.hasNext()) {
            Impact impact = iterator.next();
            LivingEntity entity = impact.entity;
            if (!entity.isAlive() || gameTime > impact.expires) {
                iterator.remove();
                continue;
            }

            boolean hit = impact.groundSlam
                    ? entity.onGround() || entity.isInWater()
                    : entity.horizontalCollision;
            if (!hit) {
                continue;
            }

            iterator.remove();
            ServerPlayer owner = server.getPlayerList().getPlayer(impact.ownerId);
            entity.hurt(owner == null
                    ? entity.damageSources().flyIntoWall()
                    : owner.damageSources().playerAttack(owner), impact.damage);
            if (impact.groundSlam) {
                entity.resetFallDistance();
            }
            if (entity.level() instanceof ServerLevel level) {
                Vec3 contact = entity.position().add(0, impact.groundSlam ? 0.1D : 0.7D, 0);
                Vec3 normal = impact.groundSlam ? new Vec3(0, 1, 0)
                        : entity.getDeltaMovement().reverse().normalize();
                AbilityEffects.send(level, Kind.PSYCHIC_IMPACT, contact,
                        contact.add(normal), impact.groundSlam ? 2.5D : 1.4D);
                level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                        SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.5F, 0.6F);
            }
        }
    }

    // ------------------------------------------------------------------

    private static Vec3 towards(Vec3 from, ServerPlayer player, double scale) {
        Vec3 delta = player.position().add(0.0D, 1.0D, 0.0D).subtract(from);
        return delta.scale(scale);
    }

    private static void setVelocity(Entity entity, Vec3 velocity) {
        entity.setDeltaMovement(velocity);
        if (entity instanceof ServerPlayer player) {
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
        } else {
            entity.hurtMarked = true;
            if (entity instanceof Mob mob) {
                mob.setOnGround(false);
            }
        }
    }

    private record Impact(
            LivingEntity entity,
            UUID ownerId,
            long expires,
            float damage,
            boolean groundSlam
    ) {
    }

    private record Grip(LivingEntity target, long until, int powerLevel) {
    }

    private static final class BulletStop {
        private final long until;
        private final List<FrozenProjectile> frozen = new ArrayList<>();

        private BulletStop(long until) {
            this.until = until;
        }
    }

    private record FrozenProjectile(Projectile projectile, boolean hadNoGravity) {
    }

    private record Lift(List<Lifted> targets, long releaseAt, int powerLevel) {
    }

    private record Lifted(LivingEntity entity, double hoverY) {
    }
}
