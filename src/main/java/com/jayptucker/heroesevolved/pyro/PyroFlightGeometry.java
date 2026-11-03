package com.jayptucker.heroesevolved.pyro;

import com.jayptucker.heroesevolved.flight.FlightVisualGeometry;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Flame nozzles follow the shared Flight pose, rather than the upright collision hitbox. */
public final class PyroFlightGeometry {
    private PyroFlightGeometry() {}

    public static Vec3 axis(Player player, boolean posed, boolean diving) {
        Vec3 velocity = player.getDeltaMovement();
        if (posed && diving && velocity.lengthSqr() > 0.15) return velocity.normalize();
        return FlightVisualGeometry.direction(player, false, 1);
    }

    public static Vec3 feet(Player player, boolean posed) { return feet(player, posed, false); }
    public static Vec3 feet(Player player, boolean posed, boolean diving) {
        return FlightVisualGeometry.feet(player.position(), axis(player, posed, diving), posed);
    }

    public static Vec3 palm(Player player, boolean posed, int side) { return palm(player, posed, side, false); }
    public static Vec3 palm(Player player, boolean posed, int side, boolean diving) {
        Vec3 direction = axis(player, posed, diving);
        float yaw = posed && diving && direction.horizontalDistanceSqr() > 0.001
                ? (float) Math.toDegrees(Math.atan2(-direction.x, direction.z))
                : FlightVisualGeometry.yaw(player, false, 1);
        double radians = Math.toRadians(yaw);
        Vec3 lateral = new Vec3(Math.cos(radians), 0, Math.sin(radians));
        if (!posed) return player.position().add(lateral.scale(Math.signum(side) * 0.43)).add(0, 0.8, 0);
        float turn = Mth.wrapDegrees(player.getYRot() - player.yRotO);
        double bank = Math.toRadians(Math.clamp(-turn * 2.4, -24, 24)
                * Math.clamp(player.getDeltaMovement().length() / 1.8, 0, 1));
        lateral = lateral.scale(Math.cos(bank)).add(direction.cross(lateral).scale(Math.sin(bank)));
        return FlightVisualGeometry.bodyCenter(player.position()).add(direction.scale(-0.1))
                .add(lateral.scale(Math.signum(side) * 0.43));
    }
}
