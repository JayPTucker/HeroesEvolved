package com.jayptucker.heroesevolved.telekinesis;

import com.jayptucker.heroesevolved.ability.AbilityRanges;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.Targeting;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.cooldown.CooldownService;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.entity.EntityCarryService;
import com.jayptucker.heroesevolved.entity.TemporalEchoEntity;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.ScreenTintPayload;
import com.jayptucker.heroesevolved.progression.MasteryService;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import com.jayptucker.heroesevolved.speed.SpeedService;
import com.jayptucker.heroesevolved.time.TemporalEntityEchoService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
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
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static com.jayptucker.heroesevolved.config.HeroesEvolvedConfig.COMMON;

/** A physical force kit: acquire, hold beside you, redirect, and make the impact count. */
public final class TelekinesisService {
    public static final int STORM_GATHER_TICKS = 20;
    public static final int STORM_LIFT_TICKS = 45;
    public static final int MAX_CAUGHT_PROJECTILES = 12;
    private static final int MAX_TARGETS = 64;
    private static final Map<UUID, Grip> GRIPS = new HashMap<>();
    private static final Map<UUID, BulletStop> BULLET_STOPS = new HashMap<>();
    private static final Map<UUID, Lift> LIFTS = new HashMap<>();
    private static final Map<UUID, Impact> IMPACTS = new HashMap<>();
    private static final ResourceLocation BULLET_ID = id("telekinesis_bullet_stop");
    private static final ResourceLocation STORM_ID = id("telekinesis_storm");

    private TelekinesisService() {}

    /** A visible cone of force. Walls stop it; one orbiting object follows an ordinary palm strike. */
    public static void push(ServerPlayer player, int powerLevel) {
        if (!PowerRules.canAct(player)) return;
        Vec3 eye = player.getEyePosition(), direction = player.getLookAngle().normalize();
        double range = AbilityRanges.ranged(8);
        Vec3 end = clippedEnd(player, eye, direction, range);
        double reach = end.distanceTo(eye);
        int affected = 0;
        for (Entity entity : player.serverLevel().getEntities(player, player.getBoundingBox().inflate(range), Entity::isAlive)
                .stream().limit(MAX_TARGETS).toList()) {
            Vec3 delta = entity.getBoundingBox().getCenter().subtract(eye);
            if (delta.lengthSqr() < .01 || delta.length() > reach + .5
                    || delta.normalize().dot(direction) < Math.cos(Math.toRadians(35))
                    || !clearLine(player.serverLevel(), player, eye, entity.getBoundingBox().getCenter())) continue;
            if (entity instanceof LivingEntity living) {
                if (!hostile(player, living) || isHeld(living)) continue;
                float damage = 2 + Math.clamp(powerLevel, 1, 5) * .25F;
                living.hurt(player.damageSources().playerAttack(player), damage);
                registerImpact(player, living, 3 + powerLevel, false, 24);
                if (TemporalEntityEchoService.isReplaying(living)) TemporalEntityEchoService.disturb(living, player);
            } else if (entity instanceof Projectile shot) {
                if (!hostileProjectile(player, shot)) continue;
                shot.setOwner(player);
            } else if (!(entity instanceof ItemEntity) && !(entity instanceof ExperienceOrb)) continue;
            double falloff = 1 - .45 * Math.min(1, delta.length() / range);
            setVelocity(entity, direction.scale((1.5 + powerLevel * .17) * falloff).add(0, .22, 0));
            affected++;
        }
        TelekineticArsenalService.launchOne(player);
        if (affected > 0) TelekineticArsenalService.salvage(player);
        AbilityEffects.send(player.serverLevel(), Kind.TK_WAVE, hand(player), end, 2.2);
        ArmPosePayload.show(player, ArmPose.KINETIC_RELEASE, 10);
        sound(player, SoundEvents.WIND_CHARGE_BURST.value(), .8F, .85F);
        MasteryService.markPowerUse(player, ModAbilities.TELEKINESIS_ID);
    }

    /** Both ordinary grips and an active storm reserve their targets against competing controllers. */
    public static boolean isHeld(Entity entity) {
        return GRIPS.values().stream().anyMatch(grip -> grip.target == entity)
                || LIFTS.values().stream().anyMatch(lift -> lift.targets.stream().anyMatch(t -> t.entity == entity));
    }

    public static boolean isGripping(ServerPlayer player) { return GRIPS.containsKey(player.getUUID()); }

    /** A launched slam owns its landing damage until its single pending collision is resolved. */
    public static boolean isSlammed(Entity entity) {
        Impact impact = IMPACTS.get(entity.getUUID());
        return impact != null && impact.target == entity && impact.slam && impact.owner.valid()
                && entity.level() == impact.owner.level && now(impact.owner.player) <= impact.until;
    }

    public static LivingEntity findGripTarget(ServerPlayer player) {
        double baseRange = 16 + (TelekinesisSkillService.has(player, "long_reach") ? 4 : 0);
        return Targeting.livingEntityInSight(player, baseRange, target -> eligibleTarget(player, target)
                && !isHeld(target) && (friendly(player, target) || hostile(player, target)));
    }

    /** The held body stays beside the crosshair, and never follows camera pitch through the floor. */
    public static boolean grip(ServerPlayer player, int powerLevel) {
        if (!PowerRules.canAct(player) || isGripping(player) || hasActiveLift(player)) return false;
        LivingEntity target = findGripTarget(player);
        if (target == null) return false;
        GRIPS.put(player.getUUID(), new Grip(new Owner(player), target, powerLevel,
                now(player) + 20L * (6 + Math.clamp(powerLevel, 1, 5))));
        AbilityEffects.send(player.serverLevel(), Kind.TK_GRIP, hand(player), target.getBoundingBox().getCenter(),
                Math.max(.6, target.getBbWidth()));
        ArmPosePayload.show(player, ArmPose.KINETIC_HOLD, 20);
        sound(player, SoundEvents.BREEZE_CHARGE, .7F, .75F);
        if (target instanceof ServerPlayer held)
            held.displayClientMessage(Component.literal("An invisible force takes hold of you.")
                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        MasteryService.markPowerUse(player, ModAbilities.TELEKINESIS_ID);
        return true;
    }

    public static boolean throwGripped(ServerPlayer player) {
        Grip grip = GRIPS.get(player.getUUID());
        if (grip == null) return false;
        if (!grip.owner.valid() || !eligibleTarget(player, grip.target)) return cancelGrip(player);
        GRIPS.remove(player.getUUID());
        Vec3 aim = clippedEnd(player, player.getEyePosition(), player.getLookAngle(), AbilityRanges.ranged(20));
        Vec3 direction = aim.subtract(grip.target.getBoundingBox().getCenter()).normalize();
        if (direction.lengthSqr() < .01) direction = player.getLookAngle();
        if (friendly(player, grip.target)) {
            gentleRelease(player, grip.target);
            setVelocity(grip.target, direction.scale(1.25).add(0, .15, 0));
        } else {
            setVelocity(grip.target, direction.scale(2.7).add(0, .12, 0));
            registerImpact(player, grip.target, 4 + grip.powerLevel, false, 55);
        }
        AbilityEffects.send(player.serverLevel(), Kind.TK_LAUNCH, grip.target.getBoundingBox().getCenter(),
                grip.target.getBoundingBox().getCenter().add(direction.scale(6)), 2);
        ArmPosePayload.show(player, ArmPose.KINETIC_RELEASE, 10);
        sound(player, SoundEvents.WIND_CHARGE_BURST.value(), .95F, .65F);
        return true;
    }

    /** Shift + palm converts a held hostile into a downward strike. Friendly grips are released gently. */
    public static boolean slamGripped(ServerPlayer player) {
        Grip grip = GRIPS.get(player.getUUID());
        if (grip == null) return false;
        if (!grip.owner.valid() || !hostile(player, grip.target)) return placeGripped(player);
        GRIPS.remove(player.getUUID());
        registerImpact(player, grip.target, 5 + grip.powerLevel, true, 45);
        setVelocity(grip.target, new Vec3(0, -2.5, 0));
        AbilityEffects.send(player.serverLevel(), Kind.TK_LAUNCH, grip.target.getBoundingBox().getCenter(),
                grip.target.position().add(0, -3, 0), 2.4);
        ArmPosePayload.show(player, ArmPose.KINETIC_RELEASE, 12);
        sound(player, SoundEvents.BREEZE_SHOOT, .8F, .6F);
        return true;
    }

    /** A free precision release. Only an unobstructed, body-safe surface can reposition the target. */
    public static boolean placeGripped(ServerPlayer player) {
        Grip grip = GRIPS.remove(player.getUUID());
        if (grip == null) return false;
        LivingEntity target = grip.target;
        if (eligibleTarget(player, target)) {
            Vec3 eye = player.getEyePosition();
            BlockHitResult hit = player.serverLevel().clip(new ClipContext(eye,
                    eye.add(player.getLookAngle().scale(16)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() == HitResult.Type.BLOCK && hit.getDirection() == Direction.UP) {
                Vec3 feet = hit.getLocation().add(0, .02, 0);
                Vec3 reached = SpeedService.swept(target, feet.subtract(target.position()));
                if (reached.distanceToSqr(feet) < .04 && SpeedService.safe(target, feet)) teleport(target, feet);
            }
            gentleRelease(player, target);
            AbilityEffects.send(player.serverLevel(), Kind.TK_GRIP, hand(player), target.getBoundingBox().getCenter(), .6);
        }
        ArmPosePayload.clear(player);
        return true;
    }

    /** Lifecycle cancellation never aims, launches, or teleports an already released target. */
    public static boolean cancelGrip(ServerPlayer player) {
        Grip grip = GRIPS.remove(player.getUUID());
        if (grip == null) return false;
        if (grip.target.isAlive() && grip.target.level() == grip.owner.level && !grip.target.isPassenger())
            gentleRelease(player, grip.target);
        ArmPosePayload.clear(player);
        return true;
    }

    public static void disarm(ServerPlayer player, int powerLevel) {
        if (!PowerRules.canAct(player)) return;
        Grip grip = GRIPS.get(player.getUUID());
        LivingEntity target = grip == null ? findGripTarget(player) : grip.target;
        boolean affected = false;
        if (target != null && hostile(player, target) && player.hasLineOfSight(target)) {
            if (target instanceof Mob mob && !mob.getMainHandItem().isEmpty()) {
                ItemStack weapon = mob.getMainHandItem().copy();
                mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                ItemEntity item = new ItemEntity(player.serverLevel(), mob.getX(), mob.getEyeY(), mob.getZ(), weapon);
                item.setDeltaMovement(towards(item.position(), player, .16));
                item.setPickUpDelay(10);
                player.serverLevel().addFreshEntity(item);
                sound(player, SoundEvents.ITEM_BREAK, .6F, 1.4F);
            } else if (grip == null) {
                setVelocity(target, safeVelocity(target, towards(target.position(), player, .12).add(0, .18, 0)));
            }
            AbilityEffects.send(player.serverLevel(), Kind.TK_GATHER, target.getEyePosition(), hand(player), .8);
            affected = true;
        }
        double radius = 8 + Math.clamp(powerLevel, 1, 5) * 2;
        for (Entity loose : player.serverLevel().getEntities(player, player.getBoundingBox().inflate(radius),
                entity -> entity instanceof ItemEntity || entity instanceof ExperienceOrb).stream().limit(32).toList()) {
            if (loose.distanceToSqr(player) > radius * radius
                    || !clearLine(player.serverLevel(), player, player.getEyePosition(), loose.position().add(0, .1, 0))) continue;
            setVelocity(loose, safeVelocity(loose, towards(loose.position(), player, .1).add(0, .18, 0)));
            AbilityEffects.send(player.serverLevel(), Kind.TK_GATHER, loose.position(), hand(player), .5);
            affected = true;
        }
        if (affected) TelekineticArsenalService.salvage(player);
        ArmPosePayload.show(player, ArmPose.KINETIC_GATHER, 14);
        MasteryService.markPowerUse(player, ModAbilities.TELEKINESIS_ID);
    }

    public static boolean hasBulletStop(ServerPlayer player) { return BULLET_STOPS.containsKey(player.getUUID()); }
    public static int caughtCount(ServerPlayer player) {
        BulletStop stop = BULLET_STOPS.get(player.getUUID());
        return stop == null ? 0 : stop.frozen.size();
    }

    public static void bulletStop(ServerPlayer player, int powerLevel) {
        if (!PowerRules.canAct(player) || hasBulletStop(player)) return;
        BULLET_STOPS.put(player.getUUID(), new BulletStop(new Owner(player), now(player) + 20L * (5 + Math.clamp(powerLevel, 1, 5))));
        ArmPosePayload.show(player, ArmPose.KINETIC_HOLD, 20);
        sound(player, SoundEvents.AMETHYST_BLOCK_RESONATE, .8F, .7F);
        AbilityEffects.send(player.serverLevel(), Kind.TK_BARRIER, player.position().add(0, 1, 0),
                player.getEyePosition().add(player.getLookAngle()), 3);
        MasteryService.markPowerUse(player, ModAbilities.TELEKINESIS_ID);
    }

    /** Press Z again to converge the captured projectiles on the aimed point. */
    public static void releaseBulletStop(ServerPlayer player) { finishBulletStop(player, true); }
    public static boolean cancelBulletStop(ServerPlayer player) { return finishBulletStop(player, false); }

    private static boolean finishBulletStop(ServerPlayer player, boolean launch) {
        BulletStop stop = BULLET_STOPS.remove(player.getUUID());
        if (stop == null) return false;
        launch &= stop.owner.valid();
        Vec3 aim = clippedEnd(player, player.getEyePosition(), player.getLookAngle(), AbilityRanges.ranged(20));
        for (FrozenProjectile frozen : stop.frozen) {
            Projectile projectile = frozen.projectile;
            if (projectile.isRemoved()) continue;
            projectile.setNoGravity(frozen.hadNoGravity);
            if (launch && projectile.level() == player.level()) {
                Vec3 direction = aim.subtract(projectile.position()).normalize();
                if (direction.lengthSqr() < .01) direction = player.getLookAngle();
                projectile.setOwner(player);
                setVelocity(projectile, direction.scale(2.5));
                AbilityEffects.send(player.serverLevel(), Kind.TK_LAUNCH,
                        projectile.position(), projectile.position().add(direction.scale(5)), 1.5);
            } else setVelocity(projectile, Vec3.ZERO);
        }
        startRecovery(player, BULLET_ID, COMMON.telekinesisBulletStopCooldownTicks.get());
        ArmPosePayload.show(player, launch ? ArmPose.KINETIC_RELEASE : ArmPose.KINETIC_GATHER, 10);
        if (launch && !stop.frozen.isEmpty()) sound(player, SoundEvents.WIND_CHARGE_BURST.value(), 1, 1.3F);
        return true;
    }

    public static boolean hasActiveLift(ServerPlayer player) { return LIFTS.containsKey(player.getUUID()); }

    /** A readable gathering phase precedes the lift. Press again to slam early; Shift cancels safely. */
    public static void telekineticStorm(ServerPlayer player, int powerLevel) {
        if (!PowerRules.canAct(player) || hasActiveLift(player)) return;
        cancelGrip(player);
        List<Lifted> targets = player.serverLevel().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(12), target -> hostile(player, target)
                        && !isHeld(target) && eligibleTarget(player, target) && target.distanceToSqr(player) <= 144
                        && player.hasLineOfSight(target)).stream()
                .sorted(Comparator.comparingDouble(target -> target.distanceToSqr(player))).limit(16)
                .map(target -> new Lifted(target, target.getY() + 3.5)).toList();
        LIFTS.put(player.getUUID(), new Lift(new Owner(player), targets, Math.clamp(powerLevel, 1, 5)));
        AbilityEffects.send(player.serverLevel(), Kind.TK_WELL, player.position(), player.position().add(0, 1, 0), 12);
        ArmPosePayload.show(player, ArmPose.KINETIC_GATHER, STORM_GATHER_TICKS);
        sound(player, SoundEvents.WARDEN_SONIC_CHARGE, 1.1F, .7F);
        MasteryService.markPowerUse(player, ModAbilities.TELEKINESIS_ID);
    }

    public static boolean slamStorm(ServerPlayer player) {
        Lift lift = LIFTS.get(player.getUUID());
        if (lift == null) return false;
        if (!lift.owner.valid()) return cancelStorm(player);
        if (lift.age < STORM_GATHER_TICKS) {
            player.displayClientMessage(Component.literal("The storm is still gathering.").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            return false;
        }
        LIFTS.remove(player.getUUID());
        for (Lifted lifted : lift.targets) {
            LivingEntity target = lifted.entity;
            if (!target.isAlive() || target.level() != player.level() || !hostile(player, target)
                    || !player.hasLineOfSight(target)) continue;
            registerImpact(player, target, 6 + lift.powerLevel, true, 50);
            setVelocity(target, new Vec3(0, -2.8, 0));
            AbilityEffects.send(player.serverLevel(), Kind.TK_LAUNCH, target.getBoundingBox().getCenter(),
                    target.position().add(0, -3, 0), 2.2);
        }
        startRecovery(player, STORM_ID, COMMON.telekinesisStormCooldownTicks.get());
        ArmPosePayload.show(player, ArmPose.KINETIC_RELEASE, 15);
        sound(player, SoundEvents.WIND_CHARGE_BURST.value(), 1.2F, .55F);
        CameraShakePayload.sendAround(player.serverLevel(), player.position(), 24, .8F, 10);
        return true;
    }

    public static boolean cancelStorm(ServerPlayer player) {
        Lift lift = LIFTS.remove(player.getUUID());
        if (lift == null) return false;
        for (Lifted lifted : lift.targets)
            if (lifted.entity.isAlive() && lifted.entity.level() == lift.owner.level) gentleRelease(player, lifted.entity);
        startRecovery(player, STORM_ID, COMMON.telekinesisStormCooldownTicks.get());
        ArmPosePayload.clear(player);
        return true;
    }

    /** Loose objects approach visibly, with fresh drops and solid cover respected. */
    public static void drawNearbyItems(ServerPlayer player) {
        if (!PowerRules.canAct(player)) return;
        for (ItemEntity item : player.serverLevel().getEntitiesOfClass(ItemEntity.class,
                player.getBoundingBox().inflate(6), item -> item.getAge() > 40
                        && item.distanceToSqr(player) <= 36).stream().limit(24).toList()) {
            if (clearLine(player.serverLevel(), player, player.getEyePosition(), item.position().add(0, .1, 0)))
                item.setDeltaMovement(item.getDeltaMovement().add(towards(item.position(), player, .02)));
        }
    }

    public static void awaken(ServerPlayer player) {
        AbilityEffects.send(player.serverLevel(), Kind.TK_WAVE, player.position().add(0, .8, 0),
                player.getEyePosition().add(player.getLookAngle()), 6);
        sound(player, SoundEvents.WIND_CHARGE_BURST.value(), 1.1F, .55F);
    }

    public static void migraine(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100));
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100));
        ScreenTintPayload.send(player, 0x503C294C, 40, ScreenTintPayload.Style.EDGE);
        player.displayClientMessage(Component.literal("Your concentration fractures.").withStyle(ChatFormatting.DARK_RED), true);
    }

    public static void stopAll(ServerPlayer player) {
        cancelGrip(player);
        cancelStorm(player);
        cancelBulletStop(player);
        IMPACTS.values().removeIf(impact -> impact.owner.player == player);
        TelekineticArsenalService.forget(player);
        TelekinesisSkillService.forget(player);
        ArmPosePayload.clear(player);
    }

    public static void clear() {
        Set<ServerPlayer> owners = new HashSet<>();
        GRIPS.values().forEach(grip -> owners.add(grip.owner.player));
        LIFTS.values().forEach(lift -> owners.add(lift.owner.player));
        BULLET_STOPS.values().forEach(stop -> owners.add(stop.owner.player));
        owners.forEach(TelekinesisService::stopAll);
        GRIPS.clear(); LIFTS.clear(); BULLET_STOPS.clear(); IMPACTS.clear();
        TelekineticArsenalService.clear();
        TelekinesisSkillService.clear();
    }

    public static void tick(MinecraftServer server) {
        long time = server.overworld().getGameTime();
        tickGrips(time);
        tickBulletStops(time);
        tickLifts();
        tickImpacts(time);
    }

    private static void tickGrips(long time) {
        for (Grip grip : List.copyOf(GRIPS.values())) {
            ServerPlayer player = grip.owner.player;
            LivingEntity target = grip.target;
            if (!grip.owner.valid() || !eligibleTarget(player, target) || time >= grip.until
                    || target.distanceToSqr(player) > Math.pow(AbilityRanges.ranged(24), 2)
                    || !player.hasLineOfSight(target)) {
                cancelGrip(player);
                continue;
            }
            Vec3 hold = gripPosition(player, target);
            Vec3 displacement = hold.subtract(target.position());
            Vec3 velocity = displacement.scale(.28);
            if (velocity.lengthSqr() > 1.25 * 1.25) velocity = velocity.normalize().scale(1.25);
            // Gravity is compensated in velocity, never by persisting noGravity/noAI flags.
            setVelocity(target, safeVelocity(target, velocity.add(0, .08, 0)));
            target.resetFallDistance();
            if (time % 10 == 0) {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 15, 4, false, false));
                ArmPosePayload.show(player, ArmPose.KINETIC_HOLD, 15);
            }
            if (time % 5 == 0) AbilityEffects.send(player.serverLevel(), Kind.TK_GRIP,
                    hand(player), target.getBoundingBox().getCenter(), Math.max(.6, target.getBbWidth()));
        }
    }

    private static void tickBulletStops(long time) {
        for (BulletStop stop : List.copyOf(BULLET_STOPS.values())) {
            ServerPlayer player = stop.owner.player;
            if (!stop.owner.valid() || time >= stop.until) {
                cancelBulletStop(player);
                continue;
            }
            stop.frozen.removeIf(frozen -> frozen.projectile.isRemoved());
            for (Projectile shot : player.serverLevel().getEntitiesOfClass(Projectile.class,
                    player.getBoundingBox().inflate(6), shot -> !shot.isRemoved()
                            && shot.getDeltaMovement().lengthSqr() > .01).stream().limit(MAX_TARGETS).toList()) {
                if (stop.frozen.size() >= MAX_CAUGHT_PROJECTILES) break;
                if (!hostileProjectile(player, shot) || stop.frozen.stream().anyMatch(frozen -> frozen.projectile == shot)
                        || shot.distanceToSqr(player) > 36
                        || shot.getDeltaMovement().dot(shot.position().subtract(player.getEyePosition())) >= 0
                        || !clearLine(player.serverLevel(), player, player.getEyePosition(), shot.position())) continue;
                stop.frozen.add(new FrozenProjectile(shot, shot.isNoGravity()));
                AbilityEffects.send(player.serverLevel(), Kind.TK_GATHER, shot.position(), hand(player), .5);
                sound(player, SoundEvents.AMETHYST_BLOCK_HIT, .35F, 1.7F);
            }
            for (int i = 0; i < stop.frozen.size(); i++) {
                Projectile shot = stop.frozen.get(i).projectile;
                if (shot.level() != stop.owner.level) continue;
                Vec3 destination = projectilePosition(player, i);
                Vec3 safe = SpeedService.swept(shot, destination.subtract(shot.position()));
                shot.setNoGravity(true);
                shot.setPos(safe);
                setVelocity(shot, Vec3.ZERO);
                if (time % 5 == 0) AbilityEffects.send(player.serverLevel(), Kind.TK_ORBIT,
                        player.position().add(0, 1, 0), shot.position(), .55);
            }
            if (time % 10 == 0) {
                ArmPosePayload.show(player, ArmPose.KINETIC_HOLD, 15);
                AbilityEffects.send(player.serverLevel(), Kind.TK_BARRIER, player.position().add(0, .8, 0),
                        player.getEyePosition().add(player.getLookAngle()), 3);
            }
        }
    }

    private static void tickLifts() {
        for (Lift lift : List.copyOf(LIFTS.values())) {
            ServerPlayer player = lift.owner.player;
            if (!lift.owner.valid() || player.position().distanceToSqr(lift.center) > 24 * 24) {
                cancelStorm(player);
                continue;
            }
            lift.age++;
            if (lift.age < STORM_GATHER_TICKS) {
                if (lift.age % 5 == 0) {
                    AbilityEffects.send(player.serverLevel(), Kind.TK_WELL, lift.center,
                            lift.center.add(0, 1, 0), 12);
                    for (Lifted target : lift.targets) if (target.entity.isAlive())
                        AbilityEffects.send(player.serverLevel(), Kind.TK_GATHER,
                                target.entity.getBoundingBox().getCenter(), hand(player), .7);
                }
                continue;
            }
            for (Lifted lifted : lift.targets) {
                LivingEntity target = lifted.entity;
                if (!eligibleTarget(player, target) || !hostile(player, target) || !player.hasLineOfSight(target)) continue;
                Vec3 offset = target.position().subtract(lift.center).multiply(1, 0, 1);
                Vec3 swirl = offset.lengthSqr() < .01 ? Vec3.ZERO : new Vec3(-offset.z, 0, offset.x).normalize().scale(.08);
                Vec3 velocity = swirl.add(0, Math.clamp((lifted.hoverY - target.getY()) * .23, -.3, .65) + .08, 0);
                setVelocity(target, safeVelocity(target, velocity));
                target.resetFallDistance();
                if (lift.age % 5 == 0) AbilityEffects.send(player.serverLevel(), Kind.TK_GRIP,
                        hand(player), target.getBoundingBox().getCenter(), Math.max(.6, target.getBbWidth()));
            }
            if (lift.age % 10 == 0) ArmPosePayload.show(player, ArmPose.KINETIC_GATHER, 15);
            if (lift.age >= STORM_GATHER_TICKS + STORM_LIFT_TICKS) slamStorm(player);
        }
    }

    private static void registerImpact(ServerPlayer player, LivingEntity target, float damage, boolean slam, int ticks) {
        IMPACTS.put(target.getUUID(), new Impact(new Owner(player), target, damage, slam, now(player) + ticks));
    }

    private static void tickImpacts(long time) {
        for (Impact impact : List.copyOf(IMPACTS.values())) {
            LivingEntity target = impact.target;
            if (!impact.owner.valid() || !target.isAlive() || target.level() != impact.owner.level
                    || time > impact.until || !hostile(impact.owner.player, target)) {
                IMPACTS.remove(target.getUUID(), impact);
                continue;
            }
            if (!(impact.slam ? target.onGround() || target.verticalCollision || target.isInWater()
                    : target.horizontalCollision)) continue;
            IMPACTS.remove(target.getUUID(), impact);
            ServerPlayer player = impact.owner.player;
            if (!player.hasLineOfSight(target)) continue;
            boolean hurt = target.hurt(player.damageSources().playerAttack(player), impact.damage);
            if (impact.slam) {
                target.resetFallDistance();
                // A slam drives the body into the ground: it doesn't skid away on the hit's knockback.
                setVelocity(target, new Vec3(0, Math.min(0, target.getDeltaMovement().y), 0));
            }
            Vec3 at = impact.slam ? target.position().add(0, .1, 0) : target.getBoundingBox().getCenter();
            Vec3 normal = impact.slam ? new Vec3(0, 1, 0) : target.getDeltaMovement().reverse().normalize();
            if (normal.lengthSqr() < .01) normal = player.getLookAngle().reverse();
            AbilityEffects.send(player.serverLevel(), Kind.TK_IMPACT, at, at.add(normal), impact.slam ? 3 : 1.5);
            soundAt(player.serverLevel(), at, SoundEvents.ANVIL_LAND, .5F, .8F);
            if (hurt) TelekinesisSkillService.onImpact(player, target, at, impact.damage, impact.slam);
        }
    }

    private static Vec3 gripPosition(ServerPlayer player, LivingEntity target) {
        Vec3 forward = Vec3.directionFromRotation(0, player.getYRot());
        Vec3 side = new Vec3(-forward.z, 0, forward.x).scale(player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1);
        double width = Math.clamp(target.getBbWidth(), .3, 4);
        return player.position().add(forward.scale(2.8)).add(side.scale(1.5 + width * .5))
                .add(0, Math.max(.7, 1.6 - target.getBbHeight() * .35), 0);
    }

    private static Vec3 projectilePosition(ServerPlayer player, int index) {
        Vec3 forward = Vec3.directionFromRotation(0, player.getYRot());
        Vec3 side = new Vec3(-forward.z, 0, forward.x);
        double bank = index % 2 == 0 ? 1 : -1;
        int row = index / 2;
        return player.position().add(side.scale(bank * (1.65 + row % 2 * .35)))
                .add(forward.scale(.5 + row / 2 * .35)).add(0, .75 + row % 3 * .35, 0);
    }

    private static void gentleRelease(ServerPlayer player, LivingEntity target) {
        target.resetFallDistance();
        setVelocity(target, Vec3.ZERO);
        if (friendly(player, target) && TelekinesisSkillService.has(player, "0_0"))
            target.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0));
    }

    private static boolean eligibleTarget(ServerPlayer player, LivingEntity target) {
        return target != player && target.isAlive() && !target.isRemoved() && !target.isSpectator()
                && target.level() == player.level() && !target.noPhysics
                && !target.isPassenger() && target.getPassengers().isEmpty()
                && !(target instanceof TemporalEchoEntity) && !TemporalEntityEchoService.isPastCopy(target)
                && !EntityCarryService.isBeingCarried(target);
    }

    private static boolean friendly(ServerPlayer player, LivingEntity target) {
        return target == player || player.isAlliedTo(target) || target.isAlliedTo(player)
                || target instanceof TamableAnimal pet && player.getUUID().equals(pet.getOwnerUUID());
    }

    private static boolean hostile(ServerPlayer player, LivingEntity target) {
        return eligibleTarget(player, target) && !friendly(player, target)
                && (!(target instanceof ServerPlayer other) || player.getServer().isPvpAllowed() && player.canHarmPlayer(other));
    }

    private static boolean hostileProjectile(ServerPlayer player, Projectile projectile) {
        Entity owner = projectile.getOwner();
        return owner != player && !(owner instanceof LivingEntity living && friendly(player, living));
    }

    private static Vec3 hand(ServerPlayer player) { return player.getEyePosition().add(player.getLookAngle().scale(.35)).add(0, -.25, 0); }
    private static Vec3 towards(Vec3 from, ServerPlayer player, double strength) { return player.position().add(0, .8, 0).subtract(from).scale(strength); }
    private static Vec3 safeVelocity(Entity entity, Vec3 desired) { return SpeedService.swept(entity, desired).subtract(entity.position()); }
    private static boolean clearLine(ServerLevel level, Entity context, Vec3 from, Vec3 to) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, context))
                .getType() == HitResult.Type.MISS;
    }
    private static Vec3 clippedEnd(ServerPlayer player, Vec3 origin, Vec3 direction, double range) {
        BlockHitResult hit = player.serverLevel().clip(new ClipContext(origin, origin.add(direction.scale(range)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : origin.add(direction.scale(range));
    }
    private static long now(ServerPlayer player) { return player.getServer().overworld().getGameTime(); }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("heroesevolved", path); }
    private static void sound(ServerPlayer player, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        soundAt(player.serverLevel(), player.position(), sound, volume, pitch);
    }
    private static void soundAt(ServerLevel level, Vec3 at, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }
    private static void teleport(Entity entity, Vec3 at) {
        if (entity instanceof ServerPlayer player) player.teleportTo(player.serverLevel(), at.x, at.y, at.z, player.getYRot(), player.getXRot());
        else entity.teleportTo(at.x, at.y, at.z);
    }
    private static void setVelocity(Entity entity, Vec3 velocity) {
        boolean changed = entity.getDeltaMovement().distanceToSqr(velocity) > .0001;
        entity.setDeltaMovement(velocity);
        entity.hurtMarked = true;
        if (entity instanceof ServerPlayer player && (changed || velocity.lengthSqr() > .0001 || player.tickCount % 5 == 0))
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
        else if (entity instanceof Projectile && entity.level() instanceof ServerLevel level && changed)
            level.getChunkSource().broadcastAndSend(entity, new ClientboundSetEntityMotionPacket(entity));
        if (entity instanceof Mob) entity.setOnGround(false);
    }
    private static void startRecovery(ServerPlayer player, ResourceLocation id, int configured) {
        int ticks = SkillTreeService.cooldown(player, ModAbilities.TELEKINESIS_ID,
                com.jayptucker.heroesevolved.ability.PowerBalance.cooldown(id, configured));
        if (ticks > 0) CooldownService.startCooldown(player, id, ticks);
    }
    private static ResourceLocation assigned(ServerPlayer player) {
        return PlayerAbilityService.getData(player).assignedPower().filter(entry -> entry.getValue().isUnlocked())
                .map(Map.Entry::getKey).orElse(null);
    }

    private static final class Owner {
        final ServerPlayer player;
        final ServerLevel level;
        final ResourceLocation power;
        Owner(ServerPlayer player) { this.player = player; this.level = player.serverLevel(); this.power = assigned(player); }
        boolean valid() { return !player.isRemoved() && player.level() == level && PowerRules.canAct(player)
                && Objects.equals(power, assigned(player)); }
    }
    private record Grip(Owner owner, LivingEntity target, int powerLevel, long until) {}
    private record FrozenProjectile(Projectile projectile, boolean hadNoGravity) {}
    private static final class BulletStop {
        final Owner owner; final long until; final List<FrozenProjectile> frozen = new ArrayList<>();
        BulletStop(Owner owner, long until) { this.owner = owner; this.until = until; }
    }
    private record Lifted(LivingEntity entity, double hoverY) {}
    private static final class Lift {
        final Owner owner; final List<Lifted> targets; final int powerLevel; final Vec3 center; int age;
        Lift(Owner owner, List<Lifted> targets, int powerLevel) {
            this.owner = owner; this.targets = targets; this.powerLevel = powerLevel; this.center = owner.player.position();
        }
    }
    private record Impact(Owner owner, LivingEntity target, float damage, boolean slam, long until) {}
}
