package com.jayptucker.heroesevolved.mixin;

import com.jayptucker.heroesevolved.client.effects.TempestCamera;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While you are watching your own tornado, the view is moved to a still spot well back from it. Only the camera
 * moves: your player stays the camera's entity, so it keeps moving, reporting its position, and being drawn.
 */
@Mixin(Camera.class)
public abstract class TornadoCameraMixin {
    @Shadow protected abstract void setPosition(Vec3 pos);
    @Shadow protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "setup", at = @At("TAIL"))
    private void heroesevolved$watchTheTornado(BlockGetter level, Entity entity, boolean detached, boolean thirdPersonReverse,
                                               float partialTick, CallbackInfo ci) {
        TempestCamera.Shot shot = TempestCamera.shot(partialTick);
        if (shot == null) return;
        setPosition(shot.at());
        setRotation(shot.yaw(), shot.pitch());
    }
}
