package com.jayptucker.heroesevolved.entity;

import com.jayptucker.heroesevolved.registry.ModEntities;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Maintains invisible carry anchors for server-synchronized held entities. */
public final class EntityCarryService {
    private static final double HOLD_DISTANCE = 0.75D;
    private static final double HOLD_HEIGHT = 0.85D;
    private static final Map<UUID, CarryAnchorEntity> CARRY_ANCHORS =
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
        positionAnchor(anchor, player);
        player.serverLevel().addFreshEntity(anchor);

        if (!target.startRiding(anchor, true)) {
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

        Entity carried = anchor.getPassengers().getFirst();
        AABB destinationBox = carried.getBoundingBox().move(
                destination.subtract(carried.position())
        );
        if (!player.serverLevel().noCollision(carried, destinationBox)) {
            return false;
        }

        carried.stopRiding();
        carried.teleportTo(destination.x, destination.y, destination.z);
        anchor.discard();
        CARRY_ANCHORS.remove(player.getUUID());
        return true;
    }

    public static boolean isCarrying(ServerPlayer player) {
        CarryAnchorEntity anchor = CARRY_ANCHORS.get(player.getUUID());
        return anchor != null && !anchor.isRemoved()
                && !anchor.getPassengers().isEmpty();
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

        positionAnchor(anchor, player);
        anchor.positionRider(anchor.getPassengers().getFirst());
    }

    public static void release(ServerPlayer player) {
        CarryAnchorEntity anchor = CARRY_ANCHORS.remove(player.getUUID());
        if (anchor == null) {
            return;
        }

        for (Entity passenger : anchor.getPassengers()) {
            passenger.stopRiding();
            passenger.teleportTo(player.getX(), player.getY(), player.getZ());
        }
        anchor.discard();
    }

    private static void positionAnchor(
            CarryAnchorEntity anchor,
            ServerPlayer carrier
    ) {
        Vec3 look = carrier.getLookAngle();
        Vec3 horizontalLook = new Vec3(look.x, 0.0D, look.z);
        if (horizontalLook.lengthSqr() < 0.0001D) {
            horizontalLook = new Vec3(0.0D, 0.0D, 1.0D);
        } else {
            horizontalLook = horizontalLook.normalize();
        }

        Vec3 position = carrier.position()
                .add(horizontalLook.scale(HOLD_DISTANCE))
                .add(0.0D, HOLD_HEIGHT, 0.0D);
        anchor.setPos(position.x, position.y, position.z);
        anchor.setYRot(carrier.getYRot());
        anchor.setYHeadRot(carrier.getYRot());
    }

    private static void removeAnchor(UUID playerId, CarryAnchorEntity anchor) {
        CARRY_ANCHORS.remove(playerId);
        if (!anchor.isRemoved()) {
            anchor.discard();
        }
    }
}
