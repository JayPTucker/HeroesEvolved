package com.jayptucker.heroesevolved.mixin;

import com.jayptucker.heroesevolved.ability.Airborne;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The floating-too-long kick ("Flying is not enabled on this server") counts only floating the mod didn't cause
 * ({@link Airborne}). A false answer takes vanilla's own branch for a grounded player, which resets the count.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerFloatingMixin {
    @Shadow
    public ServerPlayer player;

    @ModifyExpressionValue(method = "tick", at = @At(value = "FIELD",
            target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;clientIsFloating:Z", opcode = Opcodes.GETFIELD))
    private boolean heroes$poweredFloat(boolean floating) {
        return floating && !Airborne.excused(player);
    }

    @ModifyExpressionValue(method = "tick", at = @At(value = "FIELD",
            target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;clientVehicleIsFloating:Z", opcode = Opcodes.GETFIELD))
    private boolean heroes$poweredVehicleFloat(boolean floating) {
        return floating && !Airborne.excused(player);
    }
}
