package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.cryo.CryoService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** A press edge, never an amount of damage: the server meters all attempts to crack a prison. */
public record CryoShellInputPayload(boolean press) implements CustomPacketPayload {
    public static final Type<CryoShellInputPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID,"cryo_shell_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf,CryoShellInputPayload> STREAM_CODEC=StreamCodec.of(
            (b,p)->b.writeBoolean(p.press),b->new CryoShellInputPayload(b.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static void handle(CryoShellInputPayload payload,IPayloadContext context){
        context.enqueueWork(()->{if(payload.press&&context.player() instanceof ServerPlayer player)CryoService.struggle(player);});
    }
}
