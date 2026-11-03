package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Ice fracture progress follows the actual frozen player for both its owner and observers. */
public record CryoShellStatePayload(int entityId,int cracks,boolean active) implements CustomPacketPayload {
    public static final Type<CryoShellStatePayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID,"cryo_shell_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf,CryoShellStatePayload> STREAM_CODEC=StreamCodec.of(
            (b,p)->{b.writeVarInt(p.entityId);b.writeVarInt(p.cracks);b.writeBoolean(p.active);},
            b->new CryoShellStatePayload(b.readVarInt(),b.readVarInt(),b.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static void send(ServerPlayer player,int cracks,boolean active){
        if(player.connection!=null)PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                new CryoShellStatePayload(player.getId(),Math.clamp(cracks,0,100),active));
    }
    public static void handle(CryoShellStatePayload payload,IPayloadContext context){
        context.enqueueWork(()->com.jayptucker.heroesevolved.client.CryoShellClient.update(payload));
    }
}
