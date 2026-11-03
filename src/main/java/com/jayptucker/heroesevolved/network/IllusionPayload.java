package com.jayptucker.heroesevolved.network;
import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
public record IllusionPayload(int id,int kind,int source,Vec3 origin,Vec3 end,int ticks) implements CustomPacketPayload {
    public static final Type<IllusionPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID,"illusion"));
    public static final StreamCodec<RegistryFriendlyByteBuf,IllusionPayload> STREAM_CODEC=StreamCodec.of(
        (b,p)->{b.writeInt(p.id);b.writeVarInt(p.kind);b.writeInt(p.source);b.writeDouble(p.origin.x);b.writeDouble(p.origin.y);b.writeDouble(p.origin.z);b.writeDouble(p.end.x);b.writeDouble(p.end.y);b.writeDouble(p.end.z);b.writeVarInt(p.ticks);},
        b->new IllusionPayload(b.readInt(),b.readVarInt(),b.readInt(),new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),b.readVarInt()));
    public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static void handle(IllusionPayload p,IPayloadContext c){c.enqueueWork(()->com.jayptucker.heroesevolved.client.IllusionClient.accept(p));}
}
