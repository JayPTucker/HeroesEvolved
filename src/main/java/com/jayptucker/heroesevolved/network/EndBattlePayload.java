package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.endgame.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.*;

public record EndBattlePayload(EndView view) implements CustomPacketPayload {
    public static final Type<EndBattlePayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("heroesevolved","end_battle"));
    private static void vec(RegistryFriendlyByteBuf b,Vec3 v){b.writeDouble(v.x);b.writeDouble(v.y);b.writeDouble(v.z);}
    private static Vec3 vec(RegistryFriendlyByteBuf b){return new Vec3(b.readDouble(),b.readDouble(),b.readDouble());}
    private static void list(RegistryFriendlyByteBuf b,List<Vec3> values){b.writeVarInt(values.size());values.forEach(v->vec(b,v));}
    private static List<Vec3> list(RegistryFriendlyByteBuf b){int size=b.readVarInt();if(size<0||size>256)throw new IllegalArgumentException("Invalid End geometry count");List<Vec3> out=new ArrayList<>(size);for(int i=0;i<size;i++)out.add(vec(b));return List.copyOf(out);}
    public static final StreamCodec<RegistryFriendlyByteBuf,EndBattlePayload> STREAM_CODEC=StreamCodec.of((b,p)->{
        var v=p.view;
        for(int value:new int[]{v.stage(),v.phase(),v.clock(),v.stageAge(),v.boss(),v.dragon(),v.altar(),v.broken(),v.move(),v.windup(),v.grounded(),v.sampled()})b.writeInt(value);
        b.writeFloat(v.charge());b.writeFloat(v.safeAngle());b.writeFloat(v.floor());
        vec(b,v.origin());vec(b,v.target());vec(b,v.refuge());vec(b,v.feed());list(b,v.anchors());list(b,v.marks());
    },b->new EndBattlePayload(new EndView(b.readInt(),b.readInt(),b.readInt(),b.readInt(),b.readInt(),b.readInt(),b.readInt(),b.readInt(),b.readInt(),b.readInt(),b.readInt(),b.readInt(),
            b.readFloat(),b.readFloat(),b.readFloat(),vec(b),vec(b),vec(b),vec(b),list(b),list(b))));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static void handle(EndBattlePayload p,IPayloadContext context){context.enqueueWork(()->EndClientState.accept(p.view));}
}
