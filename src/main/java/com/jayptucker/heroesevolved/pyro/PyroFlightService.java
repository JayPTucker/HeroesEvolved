package com.jayptucker.heroesevolved.pyro;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.entity.EntityCarryService;
import com.jayptucker.heroesevolved.flight.FlightSpeedControl;
import com.jayptucker.heroesevolved.network.FlightVisualSyncService;
import static com.jayptucker.heroesevolved.config.HeroesEvolvedConfig.COMMON;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.PyroStatePayload;
import com.jayptucker.heroesevolved.network.SkyburstMotionPayload;
import com.jayptucker.heroesevolved.progression.MasteryService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import com.jayptucker.heroesevolved.speed.SpeedService;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server-owned flame propulsion, separate from the Flight power's persistent session. */
public final class PyroFlightService {
    private static final ResourceLocation PERMISSION = ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "pyro_flight");
    public static final int DIVE_WINDUP = 8;
    private static final int AFTERBURNER_SPOOL = 5;
    private static final double MAX_JET_SPEED = 5.8;
    private static final Map<UUID, Jets> ACTIVE = new HashMap<>();
    private PyroFlightService() {}

    private static final class Jets {
        final ServerPlayer player;
        final net.minecraft.server.level.ServerLevel level;
        final boolean priorFlying;
        final int tier;
        float forward, strafe;
        boolean rise, descend, boost;
        long lastInput;
        int age, diveAge, speedStep = 1, boostTicks, boostFuelUntil, takeoffTicks;
        boolean diving, boosted, airborne;
        double momentum, diveMomentum;
        Vec3 lastPosition;
        Jets(ServerPlayer player, int tier) {
            this.player = player; this.level = player.serverLevel(); this.priorFlying = player.getAbilities().flying;
            this.tier = tier; this.lastInput = level.getGameTime();
            this.lastPosition = player.position();
            this.takeoffTicks = player.onGround() ? 8 : 0;
        }
    }

    public static boolean isFlying(ServerPlayer player) { return ACTIVE.containsKey(player.getUUID()); }
    public static int speedStep(ServerPlayer player) {
        Jets jets = ACTIVE.get(player.getUUID());
        return jets == null ? 0 : jets.speedStep;
    }
    public static double momentum(ServerPlayer player) {
        Jets jets = ACTIVE.get(player.getUUID());
        return jets == null ? 0 : jets.momentum;
    }
    public static boolean isBoosting(ServerPlayer player) {
        Jets jets = ACTIVE.get(player.getUUID());
        return jets != null && jets.boosted && jets.boostTicks >= AFTERBURNER_SPOOL && !jets.diving;
    }
    public static boolean isChargingBoost(ServerPlayer player) {
        Jets jets = ACTIVE.get(player.getUUID());
        return jets != null && jets.boosted && jets.boostTicks > 0 && jets.boostTicks < AFTERBURNER_SPOOL && !jets.diving;
    }
    public static boolean visualPoseActive(ServerPlayer player) {
        Jets jets = ACTIVE.get(player.getUUID());
        return jets != null && !player.onGround() && (isPlunging(player) || !jets.diving && jets.takeoffTicks == 0
                && jets.forward > 0 && jets.speedStep > 0 && player.getDeltaMovement().lengthSqr() > .04);
    }
    public static void changeSpeed(ServerPlayer player, int direction) {
        Jets jets = ACTIVE.get(player.getUUID());
        if (jets == null || direction == 0 || !canStart(player) || player.onGround() || jets.diving) return;
        int step = FlightSpeedControl.adjust(jets.speedStep, direction, PlayerProgressionService.getLevel(player));
        if (step == jets.speedStep) return;
        jets.speedStep = step;
        if (step == 0) push(player, player.getDeltaMovement().scale(.25));
        sync(player);
        player.displayClientMessage(Component.translatable("message.heroesevolved.flight_speed", step + 1,
                FlightSpeedControl.maximumStep(PlayerProgressionService.getLevel(player)) + 1), true);
    }
    public static boolean isContainingFire(ServerPlayer player) {
        Jets jets = ACTIVE.get(player.getUUID());
        return player.isShiftKeyDown() || jets != null && jets.descend;
    }
    public static boolean isDiving(ServerPlayer player) {
        Jets jets = ACTIVE.get(player.getUUID());
        return jets != null && jets.diving;
    }
    public static boolean isPlunging(ServerPlayer player) {
        Jets jets = ACTIVE.get(player.getUUID());
        return jets != null && jets.diving && jets.diveAge >= DIVE_WINDUP;
    }
    public static boolean isTakingOff(ServerPlayer player) {
        Jets jets = ACTIVE.get(player.getUUID());
        return jets != null && jets.takeoffTicks > 0;
    }
    public static boolean canStart(ServerPlayer player) {
        return PyroService.isPyrokinetic(player) && PlayerProgressionService.getLevel(player) >= 3
                && PowerRules.canAct(player) && !player.isPassenger() && !player.isInWaterRainOrBubble()
                && !player.isFallFlying();
    }

    public static boolean toggle(ServerPlayer player, int tier) {
        if (isFlying(player)) { stop(player, true); return true; }
        if (!canStart(player)) return false;
        var attribute = player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT);
        if (attribute == null) return false;
        Jets jets = new Jets(player, Math.clamp(PlayerProgressionService.getLevel(player), 1, 5));
        ACTIVE.put(player.getUUID(), jets);
        attribute.addTransientModifier(new AttributeModifier(PERMISSION, 1, AttributeModifier.Operation.ADD_VALUE));
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        player.resetFallDistance();
        push(player, new Vec3(0, .55, 0));
        Vec3 feet = player.position();
        PyroGeometry.show(player.serverLevel(), Piece.PYRO_TAKEOFF, feet, feet, 1, PyroService.isBlue(player), null);
        player.displayClientMessage(Component.translatable("pyro.heroesevolved.flight_started"), true);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE,
                SoundSource.PLAYERS, 1.1F, .7F);
        sync(player);
        return true;
    }

    /** Called before activation costs and cooldowns, so leaving the sky is always possible. */
    public static boolean deactivate(ServerPlayer player) { return deactivate(player, player.isShiftKeyDown()); }
    public static boolean deactivate(ServerPlayer player, boolean modifierHeld) {
        Jets jets = ACTIVE.get(player.getUUID());
        if (jets == null) return false;
        if (modifierHeld && !jets.diving) {
            if (player.onGround() || !canStart(player) || !PyroService.canStartCast(player)
                    || !PlayerEnergyService.tryConsume(player, 16)) return true;
            MasteryService.awardEnergySpent(player, 16);
            jets.diving = true;
            jets.diveAge = 0;
            jets.diveMomentum = jets.momentum;
            jets.momentum = 0;
            jets.boosted = false;
            push(player, Vec3.ZERO);
            ArmPosePayload.show(player, ArmPose.FIRE_DIVE, DIVE_WINDUP + 46);
            player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.BREEZE_CHARGE, SoundSource.PLAYERS, .7F, .62F);
            player.displayClientMessage(Component.translatable("pyro.heroesevolved.meteor"), true);
            sync(player);
        } else stop(player, true);
        return true;
    }

    public static void input(ServerPlayer player, float forward, float strafe, boolean rise, boolean descend, boolean boost) {
        Jets jets = ACTIVE.get(player.getUUID());
        if (jets == null || !Float.isFinite(forward) || !Float.isFinite(strafe)) return;
        jets.forward = Math.clamp(forward, -1, 1);
        jets.strafe = Math.clamp(strafe, -1, 1);
        jets.rise = rise;
        jets.descend = descend;
        jets.boost = boost;
        jets.lastInput = player.serverLevel().getGameTime();
    }

    public static void tick(ServerPlayer player) {
        Jets jets = ACTIVE.get(player.getUUID());
        if (jets == null) {
            if (player.tickCount % 5 == 0 && PyroService.isPyrokinetic(player)) sync(player);
            return;
        }
        if (jets.level != player.serverLevel() || !canStart(player)) { stop(player, true); return; }
        jets.age++;
        player.resetFallDistance();
        if (!player.getAbilities().flying) {
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
        }
        if (player.serverLevel().getGameTime() - jets.lastInput > 10) {
            jets.forward = jets.strafe = 0;
            jets.rise = jets.descend = jets.boost = false;
        }
        if (jets.diving) { tickDive(jets); return; }
        if (!player.onGround()) jets.airborne = true;
        else if (jets.airborne && jets.takeoffTicks == 0) { stop(player, true); return; }
        jets.speedStep = Math.min(jets.speedStep, FlightSpeedControl.maximumStep(PlayerProgressionService.getLevel(player)));
        if (jets.takeoffTicks > 0) {
            jets.takeoffTicks--;
            push(player, new Vec3(0, Math.min(.7, COMMON.flightAscentSpeed.get()), 0));
            jets.lastPosition = player.position();
            if (jets.age % 5 == 0) sync(player);
            return;
        }
        boolean moving = Math.abs(jets.forward) + Math.abs(jets.strafe) > .01 || jets.rise || jets.descend;
        boolean wantsBoost = jets.boost && jets.forward > 0 && jets.speedStep > 0;
        if (wantsBoost && jets.age >= jets.boostFuelUntil && PyroService.spendHeat(player, 8)) {
            jets.boostFuelUntil = jets.age + 20;
        }
        jets.boosted = wantsBoost && jets.age < jets.boostFuelUntil;
        jets.boostTicks = jets.boosted ? Math.min(20, jets.boostTicks + 1) : 0;
        boolean boosted = isBoosting(player);
        if (jets.boostTicks == 1) sync(player);
        if (jets.boostTicks == AFTERBURNER_SPOOL) {
            player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.3F, .68F);
            CameraShakePayload.sendAround(jets.level, player.position(), 12, .22F, 7);
            sync(player);
        }
        boolean pillar = SkillTreeService.has(player, "pyrokinesis_pillar_of_flame");
        if (jets.age % 20 == 0) {
            int drain = moving ? 10 : pillar ? 4 : 6;
            if (!PlayerEnergyService.tryConsume(player, drain)) { stop(player, true); return; }
            MasteryService.awardEnergySpent(player, drain);
            if (!jets.boosted) PyroService.addHeat(player, 2);
        }
        double displacement = player.position().distanceTo(jets.lastPosition);
        boolean gaining = jets.forward > 0 && jets.speedStep > 0 && displacement > .12 && displacement < MAX_JET_SPEED + 1
                && player.getDeltaMovement().lengthSqr() > .12 && !player.horizontalCollision;
        jets.momentum = Math.clamp(jets.momentum + (gaining
                ? (.4 + jets.speedStep * .18) * (boosted ? 1.2 : 1) : -.2), 0, 100);
        if (boosted && gaining) {
            Vec3 at = player.position().add(0, .9, 0);
            PyroGeometry.show(jets.level, Piece.PYRO_WAKE, jets.lastPosition.add(0, .9, 0), at,
                    Math.min(1, displacement / 4), PyroService.isBlue(player), null);
        }
        jets.lastPosition = player.position();
        Vec3 look = player.getLookAngle();
        Vec3 right = Vec3.directionFromRotation(0, player.getYRot() - 90);
        Vec3 direction = look.scale(jets.forward).add(right.scale(jets.strafe));
        if (direction.lengthSqr() > 1) direction = direction.normalize();
        double cruise = jets.speedStep == 0 ? .22 : FlightSpeedControl.speed(jets.speedStep, COMMON.flightCruiseSpeed.get());
        double spool = Math.min(1, jets.boostTicks / (double) AFTERBURNER_SPOOL);
        double speed = cruise + spool * (cruise * .60 + .28 + jets.momentum * .007);
        double vertical = (jets.rise ? 1 : 0) - (jets.descend ? 1 : 0);
        Vec3 desired = direction.scale(speed).add(0, vertical * (pillar ? .65 : .5), 0);
        if (!moving) {
            // Inexperienced jets hunt gently around equilibrium; level five holds steady.
            double instability = Math.clamp(5 - PlayerProgressionService.getLevel(player), 0, 2) / 2.0;
            double phase = jets.age * 0.10 + (player.getUUID().hashCode() & 255) * 0.025;
            desired = desired.add(Math.sin(phase) * 0.075 * instability,
                    Math.sin(phase * 1.4) * 0.035 * instability,
                    Math.cos(phase * 0.85) * 0.075 * instability);
        }
        if (desired.length() > MAX_JET_SPEED) desired = desired.normalize().scale(MAX_JET_SPEED);
        Vec3 velocity = player.getDeltaMovement();
        double alignment = desired.lengthSqr() < .01 || velocity.lengthSqr() < .01 ? 1
                : desired.normalize().dot(velocity.normalize());
        Vec3 next = velocity.lerp(desired, moving ? .22 + spool * .14 + .16 * (1 - Math.max(0, alignment)) : .48);
        push(player, next.lengthSqr() >= 1 ? sweep(player, next) : next);
        if (jets.age % 20 == 0) player.serverLevel().playSound(null, player.blockPosition(),
                SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, boosted ? .65F : .35F, boosted ? .7F : 1.15F);
        if (jets.age % 5 == 0) sync(player);
    }

    private static Vec3 sweep(ServerPlayer player, Vec3 velocity) {
        return SpeedService.swept(player, velocity, point -> EntityCarryService.canTravelTo(player, point), false)
                .subtract(player.position());
    }

    private static void tickDive(Jets jets) {
        ServerPlayer player = jets.player;
        if (++jets.diveAge > 60) { stop(player, true); return; }
        if (jets.diveAge < DIVE_WINDUP) {
            push(player, Vec3.ZERO);
            return;
        }
        if (jets.diveAge == DIVE_WINDUP) {
            sync(player);
            jets.level.playSound(null, player.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.35F, .55F);
        }
        Vec3 look = player.getLookAngle();
        Vec3 direction = new Vec3(look.x, Math.min(-.75, look.y), look.z).normalize();
        Vec3 velocity = direction.scale(Math.min(4.6, 1.2 + (jets.diveAge - DIVE_WINDUP) * .24));
        Vec3 safe = sweep(player, velocity);
        if (player.onGround() || safe.distanceToSqr(velocity) > .001) {
            if (safe.lengthSqr() > .001) player.move(MoverType.SELF, safe);
            player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
            impact(jets);
            return;
        }
        push(player, velocity);
        if (jets.diveAge % 5 == 0) sync(player);
    }

    private static void impact(Jets jets) {
        ServerPlayer player = jets.player;
        Vec3 center = player.position();
        int heat = PyroService.heat(player);
        boolean blue = PyroService.isBlue(player);
        double radius = 4 + jets.tier * .3 + heat * .015 + jets.diveMomentum * .012;
        float damage = (float) (5 + jets.tier + heat * .06 + jets.diveMomentum * .04);
        for (LivingEntity target : jets.level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(radius), e -> PyroService.hostile(player, e)
                        && e.distanceToSqr(player) <= radius * radius && player.hasLineOfSight(e)).stream().limit(32).toList()) {
            PyroService.burn(player, target, damage * (blue ? 1.5F : 1), 5);
            Vec3 away = target.position().subtract(center).multiply(1, 0, 1).normalize();
            target.setDeltaMovement(away.scale(.9).add(0, .45, 0));
            target.hurtMarked = true;
            if (target instanceof ServerPlayer other) other.connection.send(new ClientboundSetEntityMotionPacket(other));
        }
        for (int i = 0; i < 16; i++) {
            double angle = i * Math.PI / 8;
            BlockPos at = BlockPos.containing(center.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius));
            PyroService.ignite(player, jets.level, at, blue);
        }
        PyroGeometry.show(jets.level, Piece.PYRO_BURST, center, center, radius, blue, null);
        CameraShakePayload.sendAround(jets.level, center, radius * 3, .75F, 14);
        jets.level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.5F, .7F);
        PyroService.spendHeat(player, Math.min(30, heat));
        stop(player, true);
        push(player, Vec3.ZERO);
    }

    public static void stop(ServerPlayer player, boolean safeLanding) {
        Jets jets = ACTIVE.remove(player.getUUID());
        if (jets == null) return;
        if (jets.diving) ArmPosePayload.clear(player);
        var attribute = player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT);
        if (attribute != null) attribute.removeModifier(PERMISSION);
        player.getAbilities().flying = jets.priorFlying && player.mayFly();
        player.onUpdateAbilities();
        player.resetFallDistance();
        if (safeLanding && player.isAlive()) {
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 120, 0, false, false));
            push(player, player.getDeltaMovement().multiply(.35, .2, .35));
        }
        sync(player);
    }
    public static void forget(ServerPlayer player) { stop(player, false); }
    public static void clear() { for (Jets jets : List.copyOf(ACTIVE.values())) stop(jets.player, false); }
    public static void sync(ServerPlayer player) {
        PyroStatePayload.send(player, PyroService.isPyrokinetic(player), isFlying(player), isDiving(player),
                PyroService.heat(player), PyroService.isBlue(player));
        FlightVisualSyncService.syncToTrackingPlayers(player);
    }
    private static void push(ServerPlayer player, Vec3 velocity) {
        player.setDeltaMovement(velocity);
        if (velocity.lengthSqr() > 3.8 * 3.8)
            PacketDistributor.sendToPlayer(player, new SkyburstMotionPayload(velocity));
        else player.connection.send(new ClientboundSetEntityMotionPacket(player));
    }
}
