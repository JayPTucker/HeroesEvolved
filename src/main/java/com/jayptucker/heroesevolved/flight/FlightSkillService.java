package com.jayptucker.heroesevolved.flight;

import com.jayptucker.heroesevolved.ability.AbilityAction;
import com.jayptucker.heroesevolved.ability.ArmPose;
import com.jayptucker.heroesevolved.ability.SimpleAction;
import com.jayptucker.heroesevolved.ability.Targeting;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import com.jayptucker.heroesevolved.network.ArmPosePayload;
import com.jayptucker.heroesevolved.network.FlightVisualSyncService;
import com.jayptucker.heroesevolved.skills.SkillCatalog;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import com.jayptucker.heroesevolved.speed.SpeedService;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Six distinct purchasable Flight techniques and their bounded, transient fields. */
public final class FlightSkillService {
    private static final int TARGET_LIMIT = 64;
    private static final int ROLL_TICKS = 12;
    private static final int VACUUM_TICKS = 8;
    private static final double SANCTUARY_RADIUS = 5;
    private static final double THERMAL_RADIUS = 3.5;
    private static final Map<UUID, State> STATES = new HashMap<>();

    private FlightSkillService() {}

    public static AbilityAction action(SkillCatalog.Node node) {
        return SimpleAction.builder(node.id(), node.level()).cost(node::energy, node::cooldown)
                .canUse(c -> canUse(c.player(), node.id()))
                .activate(c -> activate(c.player(), node.id()))
                // Shift on an initial Barrel Roll chooses left; pressing it again can abort the roll.
                .deactivate(c -> cancel(c.player(), node.id())).build();
    }

    public static boolean canUse(ServerPlayer player, String id) {
        if (!eligible(player) || !SkillTreeService.has(player, id) || !freeToManeuver(player)) return false;
        return switch (id) {
            case "flight_0_1" -> rescueTarget(player) != null;
            case "flight_sky_catch", "flight_2_1" -> groundAim(player, 20) != null;
            case "flight_1_1" -> !player.onGround() && !isLaunching(player);
            case "flight_tailwind" -> laneEnd(player, body(player), horizontal(player),
                    SkillTreeService.has(player, "flight_jetstream") ? 20 : 12).distanceToSqr(body(player)) >= 1;
            case "flight_thunderclap" -> !isLaunching(player);
            default -> false;
        };
    }

    public static boolean activate(ServerPlayer player, String id) {
        if (!canUse(player, id)) return false;
        return switch (id) {
            case "flight_0_1" -> rescue(player);
            case "flight_sky_catch" -> sanctuary(player);
            case "flight_1_1" -> roll(player);
            case "flight_tailwind" -> jetstream(player);
            case "flight_2_1" -> thermal(player);
            case "flight_thunderclap" -> vacuum(player);
            default -> false;
        };
    }

    public static boolean cancel(ServerPlayer player, String id) {
        State state = STATES.get(player.getUUID());
        if (state == null) return false;
        return switch (id) {
            case "flight_1_1" -> {
                boolean active = state.roll != null;
                if (active) {
                    push(player, Vec3.ZERO);
                    finishRoll(player, state);
                }
                yield active;
            }
            case "flight_sky_catch" -> {
                boolean active = state.sanctuary != null;
                state.sanctuary = null;
                yield active;
            }
            case "flight_tailwind" -> {
                boolean active = state.lane != null;
                state.lane = null;
                yield active;
            }
            case "flight_2_1" -> {
                boolean active = state.thermal != null;
                state.thermal = null;
                yield active;
            }
            case "flight_thunderclap" -> {
                boolean active = state.vacuum != null;
                state.vacuum = null;
                if (active) {
                    push(player, Vec3.ZERO);
                    player.resetFallDistance();
                    ArmPosePayload.clear(player);
                }
                yield active;
            }
            default -> false;
        };
    }

    /** G may abort movement without deleting fields that were already placed. */
    public static void cancelBlocking(ServerPlayer player) {
        State state = STATES.get(player.getUUID());
        if (state == null) return;
        boolean active = state.roll != null || state.vacuum != null;
        state.roll = null;
        state.vacuum = null;
        if (active) {
            push(player, Vec3.ZERO);
            player.resetFallDistance();
            ArmPosePayload.clear(player);
            FlightVisualSyncService.syncToTrackingPlayers(player);
        }
    }

    public static boolean isRolling(ServerPlayer player) {
        State state = STATES.get(player.getUUID());
        return state != null && state.roll != null;
    }

    public static boolean isChanneling(ServerPlayer player) {
        State state = STATES.get(player.getUUID());
        return state != null && state.vacuum != null;
    }

    public static float rollProgress(ServerPlayer player) {
        State state = STATES.get(player.getUUID());
        return state == null || state.roll == null ? 0 : Math.clamp(state.roll.age / (float) ROLL_TICKS, 0, 1);
    }

    public static int rollDirection(ServerPlayer player) {
        State state = STATES.get(player.getUUID());
        return state == null || state.roll == null ? 1 : state.roll.side;
    }

    /** A friend is the caster, a real teammate, or a pet owned by the caster. */
    public static boolean isFriend(ServerPlayer player, LivingEntity target) {
        return target == player || player.isAlliedTo(target) || target.isAlliedTo(player)
                || target instanceof TamableAnimal pet && player.getUUID().equals(pet.getOwnerUUID());
    }

    public static void onSkyburst(ServerPlayer player, Vec3 start, Vec3 direction, double spent) {
        if (!eligible(player) || spent < 50 || !SkillTreeService.has(player, "flight_pressure_front")) return;
        Vec3 axis = direction.normalize();
        if (axis.lengthSqr() < 0.01) return;
        State state = state(player);
        Vec3 from = start.add(0, .6, 0);
        Vec3 to = laneEnd(player, from, axis, 8);
        if (to.distanceToSqr(from) >= 1) state.echo = new Lane(from, to, state.age + 80, false);
    }

    public static void onMeteorImpact(ServerPlayer player, Vec3 center, double radius, float damage) {
        if (!eligible(player) || !SkillTreeService.has(player, "flight_sound_barrier")) return;
        scheduleBloom(state(player), center, radius, damage);
    }

    public static void tick(ServerPlayer player) {
        State state = STATES.get(player.getUUID());
        if (state == null) return;
        if (!eligible(player) || state.level != player.serverLevel()) {
            forget(player);
            return;
        }
        // Respec normally forgets everything at once; also defend direct build changes here.
        if (state.sanctuary != null && !SkillTreeService.has(player, "flight_sky_catch")) state.sanctuary = null;
        if (state.lane != null && !SkillTreeService.has(player, "flight_tailwind")) state.lane = null;
        if (state.echo != null && !SkillTreeService.has(player, "flight_pressure_front")) state.echo = null;
        if (state.thermal != null && !SkillTreeService.has(player, "flight_2_1")) state.thermal = null;
        if (!state.blooms.isEmpty() && !SkillTreeService.has(player, "flight_sound_barrier")) state.blooms.clear();
        if (state.roll != null && !SkillTreeService.has(player, "flight_1_1")) finishRoll(player, state);
        if (state.vacuum != null && !SkillTreeService.has(player, "flight_thunderclap")) cancel(player, "flight_thunderclap");
        state.age++;
        tickRoll(player, state);
        tickVacuum(player, state);
        tickThermal(player, state);
        if (state.sanctuary != null) {
            if (state.age >= state.sanctuary.until) state.sanctuary = null;
            else if (state.age % 5 == 0) tickSanctuary(player, state.sanctuary);
        }
        if (state.lane != null) {
            if (state.age >= state.lane.until) state.lane = null;
            else if (state.age % 5 == 0) tickLane(player, state.lane);
        }
        if (state.echo != null) {
            if (state.age >= state.echo.until) state.echo = null;
            else if (state.age % 5 == 0) tickLane(player, state.echo);
        }
        Iterator<Bloom> blooms = state.blooms.iterator();
        while (blooms.hasNext()) {
            Bloom bloom = blooms.next();
            if (state.age >= bloom.due) {
                blooms.remove();
                pressureWave(player, bloom.center, bloom.radius, bloom.damage, .7);
            }
        }
        if (state.roll == null && state.vacuum == null && state.thermal == null
                && state.sanctuary == null && state.lane == null && state.echo == null && state.blooms.isEmpty())
            STATES.remove(player.getUUID());
    }

    public static void forget(ServerPlayer player) {
        State state = STATES.remove(player.getUUID());
        if (state != null && (state.roll != null || state.vacuum != null)) {
            ArmPosePayload.clear(player);
            FlightVisualSyncService.syncToTrackingPlayers(player);
        }
    }

    public static void clear() { STATES.clear(); }

    private static boolean eligible(ServerPlayer player) {
        return PowerRules.canAct(player) && !player.isRemoved() && !player.isPassenger()
                && !player.isInWaterOrBubble() && !player.isFallFlying()
                && PlayerAbilityService.getAbility(player, ModAbilities.FLIGHT_ID)
                        .map(progress -> progress.isUnlocked()).orElse(false);
    }

    private static boolean isLaunching(ServerPlayer player) {
        return player.getData(ModDataAttachments.PLAYER_FLIGHT.get()).launchTicksRemaining() > 0;
    }

    private static boolean freeToManeuver(ServerPlayer player) {
        return !FlightDynamicsService.isCharging(player) && !DiveBombService.isDiving(player)
                && !FlightService.isCycloneActive(player) && !isRolling(player) && !isChanneling(player);
    }

    private static State state(ServerPlayer player) {
        State existing = STATES.get(player.getUUID());
        if (existing != null && existing.level == player.serverLevel()) return existing;
        State created = new State(player.serverLevel());
        STATES.put(player.getUUID(), created);
        return created;
    }

    private static LivingEntity rescueTarget(ServerPlayer player) {
        LivingEntity aimed = Targeting.livingEntityInSight(player, 18,
                entity -> entity != player && isFriend(player, entity));
        if (aimed != null && reachableRescue(player, aimed)) return aimed;
        return living(player, player.position(), 18).stream()
                .filter(entity -> entity != player && isFriend(player, entity) && visible(player, entity)
                        && reachableRescue(player, entity))
                .sorted(Comparator.<LivingEntity>comparingInt(entity -> falling(entity) ? 0 : 1)
                        .thenComparingDouble(entity -> entity.distanceToSqr(player)))
                .findFirst().orElse(null);
    }

    private static boolean falling(LivingEntity entity) {
        return !entity.onGround() && (entity.fallDistance > 2 || entity.getDeltaMovement().y < -.3);
    }

    private static Vec3 rescueDestination(ServerPlayer player, LivingEntity friend) {
        Vec3 delta = friend.position().subtract(player.position());
        return SpeedService.swept(player, delta.normalize().scale(Math.max(0, delta.length() - 1.5)));
    }

    private static boolean reachableRescue(ServerPlayer player, LivingEntity friend) {
        return friend.isAlive() && !friend.isSpectator() && !friend.isPassenger()
                && visible(player, friend) && rescueDestination(player, friend).distanceToSqr(friend.position()) <= 6.25;
    }

    private static boolean rescue(ServerPlayer player) {
        LivingEntity friend = rescueTarget(player);
        if (friend == null) return false;
        Vec3 start = player.position();
        Vec3 destination = rescueDestination(player, friend);
        if (!FlightService.hasActiveSession(player)) FlightService.toggleFlight(player);
        player.teleportTo(player.serverLevel(), destination.x, destination.y, destination.z,
                player.getYRot(), player.getXRot());
        push(player, Vec3.ZERO);
        player.resetFallDistance();
        friend.resetFallDistance();
        friend.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 160, 0));
        friend.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 160, 0));
        if (SkillTreeService.has(player, "flight_0_2")) {
            push(player, new Vec3(0, .55, 0));
            push(friend, friend.getDeltaMovement().multiply(.2, 0, .2).add(0, .55, 0));
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 160, 0));
            FlightDynamicsService.addMomentum(player, 15);
        }
        AbilityEffects.send(player.serverLevel(), Kind.FLIGHT_RESCUE, start, friend.position(), 1);
        ArmPosePayload.show(player, ArmPose.TWO_HANDS, 8);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.BREEZE_JUMP,
                SoundSource.PLAYERS, .8F, 1.4F);
        return true;
    }

    private static boolean sanctuary(ServerPlayer player) {
        Vec3 center = groundAim(player, 20);
        if (center == null) return false;
        State state = state(player);
        state.sanctuary = new Sanctuary(center, state.age + 120);
        tickSanctuary(player, state.sanctuary);
        ArmPosePayload.show(player, ArmPose.ARMS_WIDE, 12);
        return true;
    }

    private static void tickSanctuary(ServerPlayer player, Sanctuary sanctuary) {
        for (LivingEntity friend : living(player, sanctuary.center, SANCTUARY_RADIUS)) {
            if (!isFriend(player, friend) || !visibleFrom(player, sanctuary.center.add(0, 1, 0), friend)) continue;
            friend.resetFallDistance();
            friend.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 25, 0));
            if (SkillTreeService.has(player, "flight_safe_harbor")) {
                if (sanctuary.healed.size() < TARGET_LIMIT && sanctuary.healed.add(friend.getUUID())) friend.heal(2);
                friend.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 25, 0));
            }
        }
        for (Projectile shot : projectiles(player, sanctuary.center, SANCTUARY_RADIUS)) {
            if (!hostileProjectile(player, shot) || sanctuary.bent.contains(shot.getUUID())
                    || sanctuary.bent.size() >= TARGET_LIMIT) continue;
            Vec3 outward = away(shot.position(), sanctuary.center, player.getLookAngle());
            Vec3 velocity = shot.getDeltaMovement();
            if (velocity.dot(outward) >= 0) continue;
            sanctuary.bent.add(shot.getUUID());
            push(shot, outward.scale(Math.max(.08, velocity.length() * .35)));
        }
        AbilityEffects.send(player.serverLevel(), Kind.FLIGHT_SANCTUARY,
                sanctuary.center, sanctuary.center, SANCTUARY_RADIUS);
    }

    private static boolean roll(ServerPlayer player) {
        if (!FlightService.hasActiveSession(player)) FlightService.toggleFlight(player);
        State state = state(player);
        state.roll = new Roll(horizontal(player), player.isShiftKeyDown() ? -1 : 1);
        ArmPosePayload.show(player, ArmPose.TWO_HANDS, ROLL_TICKS);
        FlightVisualSyncService.syncToTrackingPlayers(player);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.BREEZE_JUMP,
                SoundSource.PLAYERS, .8F, 1.7F);
        return true;
    }

    private static void tickRoll(ServerPlayer player, State state) {
        Roll roll = state.roll;
        if (roll == null) return;
        if (player.onGround() || isLaunching(player) || FlightDynamicsService.isCharging(player)
                || DiveBombService.isDiving(player) || FlightService.isCycloneActive(player)) {
            finishRoll(player, state);
            return;
        }
        double progress = roll.age / (double) ROLL_TICKS;
        Vec3 side = new Vec3(-roll.forward.z, 0, roll.forward.x).scale(roll.side);
        Vec3 direction = roll.forward.scale(.8).add(side.scale(.85 * Math.sin(Math.PI * progress)))
                .add(0, .1 * Math.sin(progress * Math.PI * 2), 0).normalize();
        Vec3 velocity = direction.scale(1.8);
        Vec3 safe = SpeedService.swept(player, velocity);
        Vec3 actual = safe.subtract(player.position());
        if (actual.lengthSqr() < .04) {
            push(player, Vec3.ZERO);
            finishRoll(player, state);
            return;
        }
        push(player, actual);
        player.resetFallDistance();
        for (Projectile shot : projectiles(player, body(player), 2)) {
            if (!hostileProjectile(player, shot) || roll.deflected.contains(shot.getUUID())
                    || roll.deflected.size() >= TARGET_LIMIT) continue;
            Vec3 outward = away(shot.position(), body(player), side);
            if (shot.getDeltaMovement().dot(outward) >= 0) continue;
            roll.deflected.add(shot.getUUID());
            push(shot, outward.add(side.scale(.5)).normalize()
                    .scale(Math.max(.8, shot.getDeltaMovement().length())));
        }
        if (roll.age % 3 == 0) {
            Vec3 center = body(player);
            AbilityEffects.send(player.serverLevel(), Kind.FLIGHT_ROLL, center,
                    center.add(direction), Math.max(.1, progress));
            FlightVisualSyncService.syncToTrackingPlayers(player);
        }
        if (++roll.age >= ROLL_TICKS) finishRoll(player, state);
    }

    private static void finishRoll(ServerPlayer player, State state) {
        state.roll = null;
        ArmPosePayload.clear(player);
        FlightVisualSyncService.syncToTrackingPlayers(player);
    }

    private static boolean jetstream(ServerPlayer player) {
        State state = state(player);
        Vec3 from = body(player);
        Vec3 to = laneEnd(player, from, horizontal(player),
                SkillTreeService.has(player, "flight_jetstream") ? 20 : 12);
        if (to.distanceToSqr(from) < 1) return false;
        state.lane = new Lane(from, to, state.age + 160, true);
        tickLane(player, state.lane);
        ArmPosePayload.show(player, ArmPose.TWO_HANDS, 10);
        return true;
    }

    private static void tickLane(ServerPlayer player, Lane lane) {
        Vec3 axis = lane.end.subtract(lane.start).normalize();
        AABB bounds = new AABB(lane.start, lane.end).inflate(2);
        List<LivingEntity> targets = player.serverLevel().getEntitiesOfClass(LivingEntity.class, bounds,
                entity -> entity.isAlive() && !entity.isSpectator()).stream().limit(TARGET_LIMIT).toList();
        for (LivingEntity target : targets) {
            Vec3 center = target.getBoundingBox().getCenter();
            Vec3 nearest = closestPoint(lane.start, lane.end, center);
            if (nearest.distanceToSqr(center) > 4 || !visibleFrom(player, nearest, target)) continue;
            if (isFriend(player, target)) {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 30, 1));
                target.addEffect(new MobEffectInstance(MobEffects.JUMP, 30, 0));
                target.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 30, 0));
                target.resetFallDistance();
                if (lane.bonusEligible && !lane.friendBonus && target != player
                        && FlightService.hasActiveSession(player)
                        && SkillTreeService.has(player, "flight_jetstream")) {
                    lane.friendBonus = true;
                    FlightDynamicsService.addMomentum(player, 20);
                }
            } else if (hostile(player, target)) {
                push(target, target.getDeltaMovement().scale(.5).add(axis.scale(.35)).add(0, .06, 0));
            }
        }
        AbilityEffects.send(player.serverLevel(), Kind.FLIGHT_JETSTREAM, lane.start, lane.end, 2);
    }

    private static boolean thermal(ServerPlayer player) {
        Vec3 center = groundAim(player, 20);
        if (center == null) return false;
        State state = state(player);
        state.thermal = new Thermal(center, state.age + 8,
                SkillTreeService.has(player, "flight_2_2") ? 2 : 1);
        AbilityEffects.send(player.serverLevel(), Kind.FLIGHT_THERMAL_MARK, center, center, THERMAL_RADIUS);
        ArmPosePayload.show(player, ArmPose.ARMS_RAISED, 8);
        return true;
    }

    private static void tickThermal(ServerPlayer player, State state) {
        Thermal thermal = state.thermal;
        if (thermal == null) return;
        if (state.age < thermal.due) {
            if (state.age % 4 == 0) AbilityEffects.send(player.serverLevel(), Kind.FLIGHT_THERMAL_MARK,
                    thermal.center, thermal.center, THERMAL_RADIUS);
            return;
        }
        // The updraft is a column: the second pulse must still reach foes lifted by the first.
        AABB column = new AABB(thermal.center.add(-THERMAL_RADIUS, -.5, -THERMAL_RADIUS),
                thermal.center.add(THERMAL_RADIUS, 9, THERMAL_RADIUS));
        List<LivingEntity> targets = player.serverLevel().getEntitiesOfClass(LivingEntity.class, column, entity -> {
            Vec3 delta = entity.getBoundingBox().getCenter().subtract(thermal.center);
            return entity.isAlive() && !entity.isSpectator() && delta.y >= -.5 && delta.y <= 9
                    && delta.x * delta.x + delta.z * delta.z <= THERMAL_RADIUS * THERMAL_RADIUS;
        }).stream().limit(TARGET_LIMIT).toList();
        for (LivingEntity target : targets) {
            if (!visibleFrom(player, thermal.center.add(0, .3, 0), target)) continue;
            if (isFriend(player, target)) {
                target.resetFallDistance();
                target.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 80, 0));
            } else if (hostile(player, target)) {
                target.hurt(player.damageSources().playerAttack(player), 2);
                push(target, target.getDeltaMovement().multiply(.2, 0, .2).add(0, 1.05, 0));
            }
        }
        AbilityEffects.send(player.serverLevel(), Kind.FLIGHT_THERMAL,
                thermal.center, thermal.center, THERMAL_RADIUS);
        player.serverLevel().playSound(null, thermal.center.x, thermal.center.y, thermal.center.z,
                SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, .9F, .8F);
        if (--thermal.pulses <= 0) state.thermal = null;
        else thermal.due = state.age + 8;
    }

    private static boolean vacuum(ServerPlayer player) {
        State state = state(player);
        state.vacuum = new Vacuum(state.age + VACUUM_TICKS,
                SkillTreeService.has(player, "flight_wider_clap") ? 9 : 6,
                FlightDynamicsService.consumeMomentum(player));
        push(player, Vec3.ZERO);
        ArmPosePayload.show(player, ArmPose.ARMS_WIDE, VACUUM_TICKS);
        AbilityEffects.send(player.serverLevel(), Kind.FLIGHT_VACUUM, body(player),
                body(player).add(player.getLookAngle()), state.vacuum.radius);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.BREEZE_CHARGE,
                SoundSource.PLAYERS, 1, .7F);
        return true;
    }

    private static void tickVacuum(ServerPlayer player, State state) {
        Vacuum vacuum = state.vacuum;
        if (vacuum == null) return;
        push(player, Vec3.ZERO);
        player.resetFallDistance();
        Vec3 center = body(player);
        if (state.age < vacuum.releaseAt) {
            for (LivingEntity target : living(player, center, vacuum.radius)) {
                if (!hostile(player, target) || !visible(player, target)) continue;
                Vec3 inward = center.subtract(target.getBoundingBox().getCenter()).normalize().scale(.2);
                push(target, target.getDeltaMovement().scale(.6).add(inward));
            }
            if (SkillTreeService.has(player, "flight_wider_clap")) {
                for (Projectile shot : projectiles(player, center, vacuum.radius)) {
                    if (!hostileProjectile(player, shot) || vacuum.braked.contains(shot.getUUID())
                            || vacuum.braked.size() >= TARGET_LIMIT) continue;
                    Vec3 outward = away(shot.position(), center, player.getLookAngle());
                    if (shot.getDeltaMovement().dot(outward) >= 0) continue;
                    vacuum.braked.add(shot.getUUID());
                    push(shot, shot.getDeltaMovement().scale(.15));
                }
            }
            if (state.age % 2 == 0) AbilityEffects.send(player.serverLevel(), Kind.FLIGHT_VACUUM,
                    center, center.add(player.getLookAngle()), vacuum.radius);
            return;
        }
        state.vacuum = null;
        float damage = (float) (4 + vacuum.momentum * .05);
        pressureWave(player, center, vacuum.radius, damage, 1 + vacuum.momentum * .005);
        if (SkillTreeService.has(player, "flight_sound_barrier"))
            scheduleBloom(state, center, vacuum.radius, damage);
        ArmPosePayload.show(player, ArmPose.TWO_HANDS, 6);
    }

    private static void scheduleBloom(State state, Vec3 center, double radius, float damage) {
        if (state.blooms.size() >= 4) state.blooms.removeFirst();
        state.blooms.add(new Bloom(center, Math.clamp(radius * .9, .5, 32), damage * .5F, state.age + 12));
    }

    private static void pressureWave(ServerPlayer player, Vec3 center, double radius, float damage, double strength) {
        for (LivingEntity target : living(player, center, radius)) {
            if (!hostile(player, target) || !visibleFrom(player, center, target)) continue;
            target.hurt(player.damageSources().playerAttack(player), damage);
            push(target, away(target.position(), center, player.getLookAngle()).multiply(1, 0, 1)
                    .normalize().scale(strength).add(0, .35, 0));
        }
        AbilityEffects.send(player.serverLevel(), Kind.FLIGHT_PRESSURE_WAVE, center, center.add(0, 1, 0), radius);
        player.serverLevel().playSound(null, center.x, center.y, center.z,
                SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1, .8F);
    }

    private static Vec3 groundAim(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        BlockHitResult hit = player.serverLevel().clip(new ClipContext(eye,
                eye.add(player.getLookAngle().scale(range)), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK || hit.getLocation().distanceToSqr(eye) > range * range) return null;
        // A floor or ledge supports the field; walls and undersides are not ground.
        if (hit.getDirection() != net.minecraft.core.Direction.UP) return null;
        return hit.getLocation().add(0, .04, 0);
    }

    private static Vec3 laneEnd(ServerPlayer player, Vec3 start, Vec3 direction, double length) {
        Vec3 end = start.add(direction.scale(length));
        BlockHitResult hit = player.serverLevel().clip(new ClipContext(start, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK
                ? hit.getLocation().subtract(direction.scale(.15)) : end;
    }

    private static Vec3 horizontal(ServerPlayer player) {
        Vec3 direction = player.getLookAngle().multiply(1, 0, 1);
        return direction.lengthSqr() < .001
                ? Vec3.directionFromRotation(0, player.getYRot()) : direction.normalize();
    }

    private static Vec3 body(ServerPlayer player) { return FlightVisualGeometry.bodyCenter(player.position()); }

    private static Vec3 closestPoint(Vec3 start, Vec3 end, Vec3 point) {
        Vec3 axis = end.subtract(start);
        double along = axis.lengthSqr() < .001 ? 0
                : Math.clamp(point.subtract(start).dot(axis) / axis.lengthSqr(), 0, 1);
        return start.add(axis.scale(along));
    }

    private static Vec3 away(Vec3 point, Vec3 center, Vec3 fallback) {
        Vec3 delta = point.subtract(center);
        return delta.lengthSqr() < .001 ? fallback.normalize() : delta.normalize();
    }

    private static List<LivingEntity> living(ServerPlayer player, Vec3 center, double radius) {
        return player.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius),
                entity -> entity.isAlive() && !entity.isSpectator()
                        && entity.getBoundingBox().getCenter().distanceToSqr(center) <= radius * radius)
                .stream().limit(TARGET_LIMIT).toList();
    }

    private static List<Projectile> projectiles(ServerPlayer player, Vec3 center, double radius) {
        return player.serverLevel().getEntitiesOfClass(Projectile.class, new AABB(center, center).inflate(radius),
                shot -> shot.isAlive() && shot.position().distanceToSqr(center) <= radius * radius
                        && clearLine(player, center, shot.position()))
                .stream().limit(TARGET_LIMIT).toList();
    }

    private static boolean hostile(ServerPlayer player, LivingEntity target) {
        return !isFriend(player, target) && target.isAlive() && !target.isSpectator()
                && !com.jayptucker.heroesevolved.entity.EntityCarryService.isBeingCarried(target)
                && (!(target instanceof ServerPlayer other)
                    || player.getServer().isPvpAllowed() && player.canHarmPlayer(other));
    }

    private static boolean hostileProjectile(ServerPlayer player, Projectile shot) {
        Entity shooter = shot.getOwner();
        return shooter != player && !(shooter instanceof LivingEntity living && isFriend(player, living));
    }

    private static boolean visible(ServerPlayer player, LivingEntity target) {
        return player.hasLineOfSight(target);
    }

    private static boolean visibleFrom(ServerPlayer player, Vec3 from, LivingEntity target) {
        return clearLine(player, from, target.getBoundingBox().getCenter());
    }

    private static boolean clearLine(ServerPlayer player, Vec3 from, Vec3 to) {
        return player.serverLevel().clip(new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS;
    }

    private static void push(Entity entity, Vec3 velocity) {
        boolean changed = entity.getDeltaMovement().distanceToSqr(velocity) > .0001;
        entity.setDeltaMovement(velocity);
        entity.hurtMarked = true;
        if (entity instanceof ServerPlayer player && (changed || velocity.lengthSqr() > .0001 || player.tickCount % 4 == 0))
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
    }

    private static final class State {
        final ServerLevel level;
        int age;
        Sanctuary sanctuary;
        Lane lane;
        Lane echo;
        Roll roll;
        Thermal thermal;
        Vacuum vacuum;
        final List<Bloom> blooms = new ArrayList<>();
        State(ServerLevel level) { this.level = level; }
    }
    private static final class Sanctuary {
        final Vec3 center;
        final int until;
        final Set<UUID> healed = new HashSet<>(), bent = new HashSet<>();
        Sanctuary(Vec3 center, int until) { this.center = center; this.until = until; }
    }
    private static final class Lane {
        final Vec3 start, end;
        final int until;
        final boolean bonusEligible;
        boolean friendBonus;
        Lane(Vec3 start, Vec3 end, int until, boolean bonusEligible) {
            this.start = start; this.end = end; this.until = until; this.bonusEligible = bonusEligible;
        }
    }
    private static final class Roll {
        final Vec3 forward;
        final int side;
        int age;
        final Set<UUID> deflected = new HashSet<>();
        Roll(Vec3 forward, int side) { this.forward = forward; this.side = side; }
    }
    private static final class Thermal {
        final Vec3 center;
        int due, pulses;
        Thermal(Vec3 center, int due, int pulses) { this.center = center; this.due = due; this.pulses = pulses; }
    }
    private static final class Vacuum {
        final int releaseAt;
        final double radius, momentum;
        final Set<UUID> braked = new HashSet<>();
        Vacuum(int releaseAt, double radius, double momentum) {
            this.releaseAt = releaseAt; this.radius = radius; this.momentum = momentum;
        }
    }
    private record Bloom(Vec3 center, double radius, float damage, int due) {}
}
