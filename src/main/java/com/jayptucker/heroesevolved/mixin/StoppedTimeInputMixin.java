package com.jayptucker.heroesevolved.mixin;

import com.jayptucker.heroesevolved.time.TimeStopService;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Entity tick cancellation alone does not stop player movement or item-use packets. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class StoppedTimeInputMixin {
    @Shadow public ServerPlayer player;
    private boolean heroes$stopped() {
        // Vanilla reschedules network-thread calls. Only consult temporal state on the server thread.
        return player.getServer() != null && player.getServer().isSameThread() && TimeStopService.isStopped(player);
    }
    @Inject(method = "handleMovePlayer", at = @At("HEAD"), cancellable = true)
    private void heroes$holdPosition(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        if (!heroes$stopped()) return;
        if (packet.hasPosition() && player.position().distanceToSqr(packet.getX(player.getX()), packet.getY(player.getY()), packet.getZ(player.getZ())) > .0001)
            player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        ci.cancel();
    }
    @Inject(method = {"handleUseItem", "handleUseItemOn", "handleInteract", "handlePlayerAction", "handleContainerClick"}, at = @At("HEAD"), cancellable = true)
    private void heroes$holdActions(CallbackInfo ci) {
        if (heroes$stopped()) { player.containerMenu.sendAllDataToRemote(); ci.cancel(); }
    }
}
