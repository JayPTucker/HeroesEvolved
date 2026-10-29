package com.jayptucker.heroesevolved.client;

import com.jayptucker.heroesevolved.entity.CarryAnchorEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Reads the synchronized carrier -> anchor -> cargo hierarchy on the client. */
public final class CarryVisuals {
    private CarryVisuals() {}

    public static CarryAnchorEntity anchor(Entity carrier) {
        if (carrier == null || carrier.isRemoved()) return null;
        for (Entity passenger : carrier.getPassengers()) {
            if (passenger instanceof CarryAnchorEntity anchor && !anchor.isRemoved()
                    && anchor.getVehicle() == carrier && !anchor.getPassengers().isEmpty()) {
                return anchor;
            }
        }
        return null;
    }

    public static boolean isCarrying(Entity carrier) {
        return anchor(carrier) != null;
    }

    /** Positive is the carrier's right; a centered/behind seat has no outward lean. */
    public static int lateralSide(Entity carrier, CarryAnchorEntity anchor) {
        if (carrier == null || anchor == null) return 0;
        double yaw = Math.toRadians(carrier.getYRot());
        Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        double lateral = anchor.position().subtract(carrier.position()).dot(right);
        return Math.abs(lateral) < .15 ? 0 : lateral > 0 ? 1 : -1;
    }

    public static int supportSide(Entity carrier, CarryAnchorEntity anchor) {
        int side = lateralSide(carrier, anchor);
        if (side != 0) return side;
        return carrier instanceof LivingEntity living && living.getMainArm() == HumanoidArm.LEFT ? -1 : 1;
    }

    public static boolean isLocalFirstPersonCarrier(Entity carrier) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.player != null && minecraft.options != null
                && carrier == minecraft.player && minecraft.getCameraEntity() == carrier
                && minecraft.options.getCameraType().isFirstPerson();
    }

    /** Cargo remains visible in third person and to everybody beside its local carrier. */
    public static boolean hideInFirstPerson(Entity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        if (entity == null || minecraft == null || minecraft.player == null) return false;
        Entity camera = minecraft.getCameraEntity();
        if (!isLocalFirstPersonCarrier(camera) || entity == camera
                || !(entity.getVehicle() instanceof CarryAnchorEntity anchor)) return false;
        return !anchor.isRemoved() && anchor.getVehicle() == camera;
    }
}
