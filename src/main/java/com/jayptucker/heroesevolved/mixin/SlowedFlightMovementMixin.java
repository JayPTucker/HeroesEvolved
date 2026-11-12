package com.jayptucker.heroesevolved.mixin;

import com.jayptucker.heroesevolved.time.PlayerTimeSlow;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Flight bypasses the walking-speed attribute. Scale the player's own flight step once. */
@Mixin(Entity.class)
public abstract class SlowedFlightMovementMixin {
    @ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true)
    private Vec3 heroesevolved$slowFlightStep(Vec3 movement, MoverType type) {
        if (type != MoverType.SELF || !((Object) this instanceof Player player)
                || !(player.getAbilities().flying || player.isFallFlying())) return movement;
        double scale = PlayerTimeSlow.movementScale(player);
        return scale == 1 ? movement : movement.scale(scale);
    }
}
