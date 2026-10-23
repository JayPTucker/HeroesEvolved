package com.jayptucker.heroesevolved.entity;

import com.jayptucker.heroesevolved.registry.ModEntities;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Maintains invisible carry anchors for server-synchronized held entities. */
public final class EntityCarryService {
    private static final double HOLD_DISTANCE = 1.45D;
    private static final double HOLD_HEIGHT = 0.85D;
    private static final Map<UUID, CarryAnchorEntity> CARRY_ANCHORS =
            new HashMap<>();
    private static final Map<UUID, CarriedEntityState> CARRIED_ENTITIES =
            new HashMap<>();

    private EntityCarryService() {
    }

    public static boolean pickUp(ServerPlayer player, Entity target) {
        if (isCarrying(player) || target.isPassenger()) {
            return false;
        }

        CarryAnchorEntity anchor = new CarryAnchorEntity(
                ModEntities.CARRY_ANCHOR.get(), player.serverLevel()
        );
        applyCarriedState(player, target);
        positionAnchor(anchor, player, target);
        player.serverLevel().addFreshEntity(anchor);

        if (!target.startRiding(anchor, true)) {
            restoreCarriedState(player.getUUID());
            anchor.discard();
            return false;
        }

        CARRY_ANCHORS.put(player.getUUID(), anchor);
        return true;
    }

    public static boolean setDown(ServerPlayer player, Vec3 destination) {
        CarryAnchorEntity anchor = CARRY_ANCHORS.get(player.getUUID());
        if (anchor == null || anchor.isRemoved()
                || anchor.getPassengers().isEmpty()) {
            CARRY_ANCHORS.remove(player.getUUID());
            return false;
        }

        Entity carried = CARRIED_ENTITIES.get(player.getUUID()).entity();
        AABB destinationBox = carried.getBoundingBox().move(
                destination.subtract(carried.position())
        );
        if (!player.serverLevel().noCollision(carried, destinationBox)) {
            return false;
        }

        carried.stopRiding();
        carried.teleportTo(destination.x, destination.y, destination.z);
        restoreCarriedState(player.getUUID());
        anchor.discard();
        CARRY_ANCHORS.remove(player.getUUID());
        return true;
    }

    public static boolean isCarrying(ServerPlayer player) {
        CarryAnchorEntity anchor = CARRY_ANCHORS.get(player.getUUID());
        return anchor != null && !anchor.isRemoved()
                && CARRIED_ENTITIES.containsKey(player.getUUID())
                && !anchor.getPassengers().isEmpty();
    }

    /** Carried entities are protected from every damage source until set down. */
    public static boolean isBeingCarried(Entity entity) {
        return CARRIED_ENTITIES.values().stream()
                .anyMatch(state -> state.entity() == entity);
    }

    public static void tick(ServerPlayer player) {
        CarryAnchorEntity anchor = CARRY_ANCHORS.get(player.getUUID());
        if (anchor == null) {
            return;
        }

        if (anchor.isRemoved() || anchor.getPassengers().isEmpty()) {
            removeAnchor(player.getUUID(), anchor);
            return;
        }

        Entity carried = CARRIED_ENTITIES.get(player.getUUID()).entity();
        keepCarriedState(carried);
        positionAnchor(anchor, player, carried);
        anchor.positionRider(carried);
    }

    public static void release(ServerPlayer player) {
        CarryAnchorEntity anchor = CARRY_ANCHORS.remove(player.getUUID());
        if (anchor == null) {
            return;
        }

        // The anchor always lives in the carried entity's level. After a
        // dimension change the player's coordinates belong to a different
        // world, so set the entity down where it was being held instead.
        boolean sameLevel = anchor.level() == player.level();
        double x = sameLevel ? player.getX() : anchor.getX();
        double y = sameLevel ? player.getY() : anchor.getY() - HOLD_HEIGHT;
        double z = sameLevel ? player.getZ() : anchor.getZ();
        for (Entity passenger : anchor.getPassengers()) {
            passenger.stopRiding();
            passenger.teleportTo(x, y, z);
        }
        restoreCarriedState(player.getUUID());
        anchor.discard();
    }

    /**
     * Lets go of the carried entity from where it is being held and flings
     * it. Returns the thrown entity, or null if nothing was carried.
     */
    public static Entity hurl(ServerPlayer player, Vec3 velocity) {
        CarryAnchorEntity anchor = CARRY_ANCHORS.remove(player.getUUID());
        CarriedEntityState state = CARRIED_ENTITIES.get(player.getUUID());
        if (anchor == null || state == null) {
            if (anchor != null) {
                anchor.discard();
            }
            return null;
        }

        Entity carried = state.entity();
        carried.stopRiding();
        carried.teleportTo(anchor.getX(), anchor.getY(), anchor.getZ());
        restoreCarriedState(player.getUUID());
        anchor.discard();

        carried.setDeltaMovement(velocity);
        carried.hurtMarked = true;
        if (carried instanceof ServerPlayer thrownPlayer) {
            thrownPlayer.connection.send(
                    new net.minecraft.network.protocol.game
                            .ClientboundSetEntityMotionPacket(thrownPlayer)
            );
        }
        return carried;
    }

    private static void positionAnchor(
            CarryAnchorEntity anchor,
            ServerPlayer carrier,
            Entity carried
    ) {
        Vec3 look = carrier.getLookAngle();
        Vec3 horizontalLook = new Vec3(look.x, 0.0D, look.z);
        if (horizontalLook.lengthSqr() < 0.0001D) {
            horizontalLook = new Vec3(0.0D, 0.0D, 1.0D);
        } else {
            horizontalLook = horizontalLook.normalize();
        }

        // Keep the held hitbox outside the carrier's own hitbox. This avoids
        // physical collision pushing while still placing it at arm's length.
        double clearance = carrier.getBbWidth() / 2.0D
                + carried.getBbWidth() / 2.0D + 0.35D;
        Vec3 position = carrier.position()
                .add(horizontalLook.scale(Math.max(HOLD_DISTANCE, clearance)))
                .add(0.0D, HOLD_HEIGHT, 0.0D);
        anchor.setPos(position.x, position.y, position.z);
        anchor.setYRot(carrier.getYRot());
        anchor.setYHeadRot(carrier.getYRot());
    }

    private static void removeAnchor(UUID playerId, CarryAnchorEntity anchor) {
        CARRY_ANCHORS.remove(playerId);
        restoreCarriedState(playerId);
        if (!anchor.isRemoved()) {
            anchor.discard();
        }
    }

    private static void applyCarriedState(ServerPlayer carrier, Entity entity) {
        CARRIED_ENTITIES.put(carrier.getUUID(), new CarriedEntityState(
                entity,
                entity.isNoGravity(),
                entity.noPhysics,
                entity.isInvulnerable(),
                entity instanceof Mob mob && mob.isNoAi()
        ));
        keepCarriedState(entity);
    }

    private static void keepCarriedState(Entity entity) {
        entity.setDeltaMovement(Vec3.ZERO);
        entity.resetFallDistance();
        entity.setNoGravity(true);
        entity.noPhysics = true;
        entity.setInvulnerable(true);
        if (entity instanceof Mob mob) {
            mob.setNoAi(true);
        }
    }

    private static void restoreCarriedState(UUID carrierId) {
        CarriedEntityState state = CARRIED_ENTITIES.remove(carrierId);
        if (state == null || state.entity().isRemoved()) {
            return;
        }

        state.entity().setNoGravity(state.hadNoGravity());
        state.entity().noPhysics = state.hadNoPhysics();
        state.entity().setInvulnerable(state.hadInvulnerable());
        if (state.entity() instanceof Mob mob) {
            mob.setNoAi(state.mobHadNoAi());
        }
    }

    private record CarriedEntityState(
            Entity entity,
            boolean hadNoGravity,
            boolean hadNoPhysics,
            boolean hadInvulnerable,
            boolean mobHadNoAi
    ) {
    }
}
