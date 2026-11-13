package com.jayptucker.heroesevolved.mixin;

import com.jayptucker.heroesevolved.entity.CarryAnchorEntity;
import com.jayptucker.heroesevolved.entity.CarryGeometry;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A carried player's camera follows the carrier smoothly. Vanilla places it from the player's own position, which is
 * only moved to the seat once a tick from the carrier's position and facing, so every turn and step the carrier made
 * between ticks arrived as a jolt. Here the seat is worked out every frame from the carrier's interpolated position
 * and facing, the same way {@code EntityCarryPoseRenderer} draws the carried body, and the camera is moved by the
 * difference. Only the camera moves.
 */
@Mixin(Camera.class)
public abstract class CarriedCameraMixin {
    @Shadow public abstract Vec3 getPosition();
    @Shadow protected abstract void setPosition(Vec3 pos);

    @Inject(method = "setup", at = @At("TAIL"))
    private void heroesevolved$rideSmoothly(BlockGetter level, Entity entity, boolean detached, boolean thirdPersonReverse,
                                           float partialTick, CallbackInfo ci) {
        if (entity != Minecraft.getInstance().player || !(entity.getVehicle() instanceof CarryAnchorEntity anchor)) return;
        Entity carrier = anchor.getVehicle();
        if (carrier == null || carrier.isRemoved()) return;
        Vec3 carrierAt = new Vec3(Mth.lerp(partialTick, carrier.xOld, carrier.getX()), Mth.lerp(partialTick, carrier.yOld, carrier.getY()),
                Mth.lerp(partialTick, carrier.zOld, carrier.getZ()));
        float yaw = Mth.rotLerp(partialTick, carrier.yRotO, carrier.getYRot());
        Vec3 seat = carrierAt.add(CarryGeometry.rotate(CarryGeometry.seat(anchor.hold(), carrier.getBbWidth(), carrier.getBbHeight(),
                entity.getBbWidth(), entity.getBbHeight(), entity.getEyeHeight(), CarryGeometry.side(carrier)), yaw));
        Vec3 drawn = new Vec3(Mth.lerp(partialTick, entity.xOld, entity.getX()), Mth.lerp(partialTick, entity.yOld, entity.getY()),
                Mth.lerp(partialTick, entity.zOld, entity.getZ()));
        Vec3 shift = seat.subtract(drawn);
        // A seat far from where the player is (a teleport mid-carry, or links still settling) is not ours to fix.
        if (shift.lengthSqr() > 4) return;
        setPosition(getPosition().add(shift));
    }
}
