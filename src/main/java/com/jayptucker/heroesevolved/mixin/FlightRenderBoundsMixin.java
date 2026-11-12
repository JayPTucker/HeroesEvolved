package com.jayptucker.heroesevolved.mixin;

import com.jayptucker.heroesevolved.entity.CarryAnchorEntity;
import com.jayptucker.heroesevolved.network.ClientFlightVisualState;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Skyburst can move the carrier and cargo past the camera before their rendered poses catch up. */
@Mixin(EntityRenderDispatcher.class)
abstract class FlightRenderBoundsMixin {
    @Inject(method = "shouldRender", at = @At("RETURN"), cancellable = true)
    private void heroesevolved$includeInterpolatedFlightBounds(Entity entity, Frustum frustum,
            double cameraX, double cameraY, double cameraZ, CallbackInfoReturnable<Boolean> result) {
        if (result.getReturnValue()) return;
        Entity mover = entity.getVehicle() instanceof CarryAnchorEntity anchor ? anchor.carrier() : entity;
        if (!(mover instanceof Player player) || !entity.shouldRender(cameraX, cameraY, cameraZ)) return;
        int id = player.getId();
        if (!ClientFlightVisualState.isBursting(id) && !ClientFlightVisualState.isPyroBoosting(id)
                && !(ClientFlightVisualState.isPyroFlight(id) && ClientFlightVisualState.isPlunging(id))) return;
        double dx = mover.xOld - mover.getX();
        double dy = mover.yOld - mover.getY();
        double dz = mover.zOld - mover.getZ();
        double distanceSquared = dx * dx + dy * dy + dz * dz;
        if (distanceSquared <= 1 || distanceSquared > 16 * 16) return;
        result.setReturnValue(frustum.isVisible(entity.getBoundingBoxForCulling().inflate(.7).expandTowards(dx, dy, dz)));
    }
}
