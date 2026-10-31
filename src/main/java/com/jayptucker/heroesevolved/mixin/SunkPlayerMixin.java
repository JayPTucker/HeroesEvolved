package com.jayptucker.heroesevolved.mixin;

import com.jayptucker.heroesevolved.phasing.DensityService;
import net.minecraft.world.entity.player.Player;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A phaser travelling under the ground moves through it the way a spectator does, on both sides,
 * so the client neither pushes them out of the rock nor gets rubber-banded by the server.
 */
@Mixin(Player.class)
public abstract class SunkPlayerMixin {
    @Inject(method = "tick", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/player/Player;noPhysics:Z",
            opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER))
    private void heroesevolved$phaseThroughGround(CallbackInfo callback) {
        Player self = (Player) (Object) this;
        if (DensityService.sunk(self)) self.noPhysics = true;
    }
}
