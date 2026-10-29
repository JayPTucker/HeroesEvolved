package com.jayptucker.heroesevolved.entity;

import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.registry.ModEntities;
import com.jayptucker.heroesevolved.time.TemporalEntityEchoService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Carrier -> anchor -> cargo is one vanilla riding graph, predicted identically on both clients. */
public final class EntityCarryService {
    private static final String RECOVERY_TAG = "heroesevolved_carry_original";
    private static final double MAX_REACH = 7;
    private static final Map<UUID, Carry> CARRIES = new HashMap<>();
    private static final Map<UUID, UUID> CARRIERS_BY_CARGO = new HashMap<>();

    private EntityCarryService() {}

    public static boolean canPickUp(ServerPlayer player, Entity target, double reach) {
        if (target == null || target == player || !Double.isFinite(reach) || reach <= 0
                || !player.isAlive() || player.isRemoved() || player.isSpectator() || player.isSleeping()
                || PowerRules.immobilized(player) || player.isPassenger() || !player.getPassengers().isEmpty()
                || target.level() != player.level() || !target.isAlive() || target.isRemoved() || target.isSpectator()
                || target instanceof CarryAnchorEntity || target instanceof TemporalEchoEntity
                || TemporalEntityEchoService.isPastCopy(target) || target.isPassenger() || !target.getPassengers().isEmpty()
                || CARRIES.containsKey(target.getUUID()) || CARRIERS_BY_CARGO.containsKey(player.getUUID())
                || isCarrying(player) || CARRIERS_BY_CARGO.containsKey(target.getUUID())) return false;
        Vec3 center = target.getBoundingBox().getCenter();
        double range = Math.min(reach, MAX_REACH);
        if (player.getEyePosition().distanceToSqr(center) > range * range) return false;
        return player.serverLevel().clip(new ClipContext(player.getEyePosition(), center,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS;
    }

    /** Skyhook uses the full seven-block reach; ordinary interaction supplies its shorter reach. */
    public static boolean pickUp(ServerPlayer player, Entity target) { return pickUp(player, target, MAX_REACH); }

    public static boolean pickUp(ServerPlayer player, Entity target, double reach) {
        if (target == null) return false;
        recover(target);
        if (!canPickUp(player, target, reach)) return false;
        Vec3 seat = CarryGeometry.position(player, target);
        if (!CarryGeometry.clear(player, target.getBbWidth(), target.getBbHeight(), seat)) return false;
        CarryAnchorEntity anchor = new CarryAnchorEntity(ModEntities.CARRY_ANCHOR.get(), player.serverLevel());
        anchor.setCargo(target);
        anchor.setPos(seat);
        anchor.setYRot(player.getYRot());
        if (!player.serverLevel().addFreshEntity(anchor)) return false;
        if (!anchor.startRiding(player, true)) {
            anchor.discard();
            return false;
        }
        Original original = Original.capture(target);
        original.store(target);
        hold(target);
        if (!target.startRiding(anchor, true)) {
            original.restore(target);
            target.getPersistentData().remove(RECOVERY_TAG);
            anchor.stopRiding();
            anchor.discard();
            return false;
        }
        Carry carry = new Carry(player, target, anchor, original, player.serverLevel(), seat);
        CARRIES.put(player.getUUID(), carry);
        CARRIERS_BY_CARGO.put(target.getUUID(), player.getUUID());
        position(carry);
        return true;
    }

    public static boolean isCarrying(ServerPlayer player) {
        Carry carry = active(player.getUUID());
        return carry != null;
    }

    public static boolean isBeingCarried(Entity entity) {
        UUID carrier = CARRIERS_BY_CARGO.get(entity.getUUID());
        Carry carry = carrier == null ? null : active(carrier);
        return carry != null && carry.cargo == entity;
    }

    public static void tick(ServerPlayer player) {
        Carry carry = active(player.getUUID());
        if (carry == null) return;
        if (PowerRules.immobilized(player)) {
            releaseCarry(carry, carry.lastSafe, Vec3.ZERO, true);
            return;
        }
        Vec3 seat = CarryGeometry.position(player, carry.cargo);
        if (!CarryGeometry.clear(player, carry.cargo.getBbWidth(), carry.cargo.getBbHeight(), seat)) {
            // An oversized passenger must not be dragged into a solid ceiling or narrow tunnel.
            releaseCarry(carry, carry.lastSafe, Vec3.ZERO, true);
            return;
        }
        hold(carry.cargo);
        position(carry);
        carry.lastSafe = carry.cargo.position();
    }

    /** Invalid placement does not change either mount link, protection or ownership. */
    public static boolean setDown(ServerPlayer player, Vec3 destination) {
        Carry carry = active(player.getUUID());
        if (carry == null || !finite(destination) || player.position().distanceToSqr(destination) > 8 * 8
                || !safe(carry.cargo, destination)
                || !carry.level.noCollision(carry.cargo, CarryGeometry.box(carry.cargo, destination))) return false;
        Vec3 from = carry.cargo.getBoundingBox().getCenter();
        Vec3 to = destination.add(0, carry.cargo.getBbHeight() * .5, 0);
        if (carry.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, carry.cargo)).getType() != HitResult.Type.MISS) return false;
        releaseCarry(carry, destination, Vec3.ZERO, false);
        return true;
    }

    /** Ends both roles, so disconnecting or changing dimension while held cannot strand a partner. */
    public static void release(ServerPlayer player) { releaseEntity(player); }

    public static void releaseEntity(Entity entity) {
        Carry own = CARRIES.get(entity.getUUID());
        if (own != null) releaseCarry(own, currentSeat(own), Vec3.ZERO, true);
        UUID owner = CARRIERS_BY_CARGO.get(entity.getUUID());
        Carry held = owner == null ? null : CARRIES.get(owner);
        if (held != null) releaseCarry(held, currentSeat(held), Vec3.ZERO, true);
        recover(entity);
    }

    public static Entity hurl(ServerPlayer player, Vec3 velocity) {
        Carry carry = active(player.getUUID());
        if (carry == null || !finite(velocity)) return null;
        Entity cargo = carry.cargo;
        Vec3 bounded = velocity.lengthSqr() > 3.8 * 3.8 ? velocity.normalize().scale(3.8) : velocity;
        releaseCarry(carry, currentSeat(carry), bounded, false);
        return cargo;
    }

    /** Release before world/player saving: temporary anchors intentionally have no disk representation. */
    public static void releaseAll() {
        for (Carry carry : List.copyOf(CARRIES.values()))
            releaseCarry(carry, currentSeat(carry), Vec3.ZERO, true);
        CARRIES.clear();
        CARRIERS_BY_CARGO.clear();
    }

    /** Restores originals from a saved mid-carry entity after restart or an interrupted session. */
    public static void recover(Entity entity) {
        if (entity.level().isClientSide || !entity.getPersistentData().contains(RECOVERY_TAG)) return;
        UUID owner = CARRIERS_BY_CARGO.get(entity.getUUID());
        Carry carry = owner == null ? null : CARRIES.get(owner);
        if (carry != null && carry.cargo == entity && valid(carry)) return;
        if (carry != null) releaseCarry(carry, entity.position(), Vec3.ZERO, false);
        if (!entity.getPersistentData().contains(RECOVERY_TAG)) return;
        Original.read(entity.getPersistentData().getCompound(RECOVERY_TAG)).restore(entity);
        entity.getPersistentData().remove(RECOVERY_TAG);
        if (entity.getVehicle() instanceof CarryAnchorEntity anchor) {
            entity.stopRiding();
            if (anchor.getPassengers().isEmpty()) anchor.discard();
        }
        entity.resetFallDistance();
    }

    private static Carry active(UUID owner) {
        Carry carry = CARRIES.get(owner);
        if (carry != null && !valid(carry)) {
            releaseCarry(carry, currentSeat(carry), Vec3.ZERO, true);
            return null;
        }
        return carry;
    }

    private static boolean valid(Carry carry) {
        return carry.carrier.isAlive() && !carry.carrier.isRemoved() && !carry.carrier.isSpectator()
                && !carry.carrier.isPassenger() && carry.carrier.level() == carry.level
                && carry.cargo.isAlive() && !carry.cargo.isRemoved() && carry.cargo.level() == carry.level
                && !carry.anchor.isRemoved() && carry.anchor.level() == carry.level
                && carry.anchor.getVehicle() == carry.carrier && carry.cargo.getVehicle() == carry.anchor
                && carry.anchor.getPassengers().size() == 1
                && carry.cargo.getPassengers().isEmpty();
    }

    private static void position(Carry carry) {
        // Vanilla also invokes these links while ticking passengers on both sides.
        // There are no independent anchor teleports or per-tick player correction packets.
        carry.carrier.positionRider(carry.anchor);
        carry.anchor.positionRider(carry.cargo);
        carry.anchor.setYRot(carry.carrier.getYRot());
    }

    private static Vec3 currentSeat(Carry carry) {
        if (carry.cargo.getVehicle() != carry.anchor || carry.anchor.getVehicle() != carry.carrier)
            return carry.cargo.position();
        if (carry.carrier.level() == carry.level && carry.cargo.level() == carry.level
                && !carry.carrier.isRemoved() && carry.carrier.isAlive()) {
            Vec3 seat = CarryGeometry.position(carry.carrier, carry.cargo);
            if (safe(carry.cargo, seat)) return seat;
        }
        return carry.cargo.level() == carry.level ? carry.lastSafe : carry.cargo.position();
    }

    private static void releaseCarry(Carry carry, Vec3 preferred, Vec3 velocity, boolean cushion) {
        if (!CARRIES.remove(carry.carrier.getUUID(), carry)) return;
        CARRIERS_BY_CARGO.remove(carry.cargo.getUUID(), carry.carrier.getUUID());
        Entity cargo = carry.cargo;
        // A dismount/teleport/other mount belongs to that system. Only retire our old carry state.
        boolean movedElsewhere = cargo.getVehicle() != carry.anchor;
        Vec3 fallback = cargo.level() == carry.level ? carry.lastSafe : cargo.position();
        Vec3 destination = movedElsewhere ? cargo.position() : releasePosition(cargo, preferred, fallback);
        if (cargo.getVehicle() == carry.anchor) cargo.stopRiding();
        carry.original.restore(cargo);
        cargo.getPersistentData().remove(RECOVERY_TAG);
        carry.anchor.ejectPassengers();
        carry.anchor.stopRiding();
        carry.anchor.discard();
        if (!cargo.isRemoved() && !movedElsewhere) {
            teleport(cargo, destination);
            cargo.resetFallDistance();
            cargo.setDeltaMovement(velocity);
            cargo.hurtMarked = true;
            if (cargo instanceof ServerPlayer player) player.connection.send(new ClientboundSetEntityMotionPacket(player));
            if (cushion && cargo instanceof LivingEntity living)
                living.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0));
        }
    }

    private static Vec3 releasePosition(Entity cargo, Vec3 preferred, Vec3 lastSafe) {
        if (safe(cargo, preferred)) return preferred;
        if (safe(cargo, lastSafe)) return lastSafe;
        Vec3 origin = cargo.position();
        for (double height : new double[]{0, 1, 2, 3}) {
            for (double distance : new double[]{0, 1, 2, 3}) {
                for (int side = 0; side < (distance == 0 ? 1 : 8); side++) {
                    double angle = side * Math.PI / 4;
                    Vec3 candidate = origin.add(Math.cos(angle) * distance, height, Math.sin(angle) * distance);
                    if (safe(cargo, candidate)) return candidate;
                }
            }
        }
        // Never send non-finite coordinates or pull a dimension traveller back to the former world.
        return origin;
    }

    private static boolean safe(Entity cargo, Vec3 feet) {
        return finite(feet) && CarryGeometry.clear(cargo, cargo.getBbWidth(), cargo.getBbHeight(), feet);
    }

    private static boolean finite(Vec3 vector) {
        return vector != null && Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }

    private static void teleport(Entity entity, Vec3 position) {
        if (entity instanceof ServerPlayer player)
            player.teleportTo(player.serverLevel(), position.x, position.y, position.z, player.getYRot(), player.getXRot());
        else entity.teleportTo(position.x, position.y, position.z);
    }

    private static void hold(Entity entity) {
        entity.setDeltaMovement(Vec3.ZERO);
        entity.resetFallDistance();
        entity.setNoGravity(true);
        entity.noPhysics = true;
        entity.setInvulnerable(true);
        if (entity instanceof Mob mob) {
            mob.getNavigation().stop();
            mob.setNoAi(true);
        }
    }

    private record Original(boolean noGravity, boolean noPhysics, boolean invulnerable, boolean noAi) {
        static Original capture(Entity entity) {
            return new Original(entity.isNoGravity(), entity.noPhysics, entity.isInvulnerable(), entity instanceof Mob mob && mob.isNoAi());
        }
        void store(Entity entity) {
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("no_gravity", noGravity);
            tag.putBoolean("no_physics", noPhysics);
            tag.putBoolean("invulnerable", invulnerable);
            tag.putBoolean("no_ai", noAi);
            entity.getPersistentData().put(RECOVERY_TAG, tag);
        }
        static Original read(CompoundTag tag) {
            return new Original(tag.getBoolean("no_gravity"), tag.getBoolean("no_physics"), tag.getBoolean("invulnerable"), tag.getBoolean("no_ai"));
        }
        void restore(Entity entity) {
            entity.setNoGravity(noGravity);
            entity.noPhysics = noPhysics;
            entity.setInvulnerable(invulnerable);
            if (entity instanceof Mob mob) mob.setNoAi(noAi);
        }
    }

    private static final class Carry {
        final ServerPlayer carrier;
        final Entity cargo;
        final CarryAnchorEntity anchor;
        final Original original;
        final ServerLevel level;
        Vec3 lastSafe;
        Carry(ServerPlayer carrier, Entity cargo, CarryAnchorEntity anchor, Original original, ServerLevel level, Vec3 lastSafe) {
            this.carrier = carrier;
            this.cargo = cargo;
            this.anchor = anchor;
            this.original = original;
            this.level = level;
            this.lastSafe = lastSafe;
        }
    }
}
