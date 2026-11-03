package com.jayptucker.heroesevolved.mixin;
import net.minecraft.server.network.*;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.PacketSendListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class PossessionSyncMixin {
    @Inject(method="send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V",at=@At("HEAD"),cancellable=true)
    private void heroes$mirror(Packet<?> packet,PacketSendListener listener,CallbackInfo ci) {
        if((Object)this instanceof ServerGamePacketListenerImpl game && com.jayptucker.heroesevolved.telepathy.PossessionService.mirror(game.player,packet))ci.cancel();
    }
}
