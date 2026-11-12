package com.jayptucker.heroesevolved.pyro;

import com.jayptucker.heroesevolved.flight.FlightVisualGeometry;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Flame nozzles follow the shared Flight pose, rather than the upright collision hitbox. */
public final class PyroFlightGeometry {
    private PyroFlightGeometry() {}

    public static Vec3 axis(Player player, boolean posed, boolean diving) {
        return axis(player, posed, diving, 1);
    }

    public static Vec3 axis(Player player, boolean posed, boolean diving, float partial) {
        Vec3 velocity = FlightVisualGeometry.travel(player);
        if (posed && velocity.lengthSqr() > .15) return velocity.normalize();
        return FlightVisualGeometry.direction(player, false, partial);
    }

    public static Vec3 feet(Player player, boolean posed) { return feet(player, posed, false); }
    public static Vec3 feet(Player player, boolean posed, boolean diving) {
        return feet(player, posed, diving, 1);
    }

    public static Vec3 feet(Player player, boolean posed, boolean diving, float partial) {
        return FlightVisualGeometry.feet(player.getPosition(partial), axis(player, posed, diving, partial), posed);
    }

    public static Vec3 palm(Player player, boolean posed, int side) { return palm(player, posed, side, false); }
    public static Vec3 palm(Player player, boolean posed, int side, boolean diving) {
        return palm(player, posed, side, diving, 1);
    }

    public static Vec3 palm(Player player, boolean posed, int side, boolean diving, float partial) {
        Vec3 direction = axis(player, posed, diving, partial);
        float yaw = posed && direction.horizontalDistanceSqr() > 0.001
                ? (float) Math.toDegrees(Math.atan2(-direction.x, direction.z))
                : FlightVisualGeometry.yaw(player, false, partial);
        double radians = Math.toRadians(yaw);
        Vec3 lateral = new Vec3(Math.cos(radians), 0, Math.sin(radians));
        Vec3 feet = player.getPosition(partial);
        if (!posed) return feet.add(lateral.scale(Math.signum(side) * 0.43)).add(0, 0.8, 0);
        float turn = Mth.wrapDegrees(player.getYRot() - player.yRotO);
        double bank = Math.toRadians(Math.clamp(-turn * 2.4, -24, 24)
                * Math.clamp(player.getDeltaMovement().length() / 1.8, 0, 1));
        lateral = lateral.scale(Math.cos(bank)).add(direction.cross(lateral).scale(Math.sin(bank)));
        return FlightVisualGeometry.bodyCenter(feet).add(direction.scale(-0.1))
                .add(lateral.scale(Math.signum(side) * 0.43));
    }
}
