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
    private static void seal(RegistryFriendlyByteBuf b,SealView s){
        for(int value:new int[]{s.action(),s.age(),s.windup(),s.flags(),s.crash(),s.stance(),s.feeding()})b.writeVarInt(value+1);b.writeFloat(s.drag());
        b.writeVarInt(s.knots().size());for(int i=0;i<s.knots().size();i++){b.writeVarInt(s.knots().get(i));b.writeFloat(s.health().get(i));vec(b,s.at().get(i));}
    }
    private static SealView seal(RegistryFriendlyByteBuf b){
        int[] v=new int[7];for(int i=0;i<7;i++)v[i]=b.readVarInt()-1;float drag=b.readFloat();int n=b.readVarInt();if(n<0||n>8)throw new IllegalArgumentException("Invalid seal knot count");
        List<Integer> knots=new ArrayList<>();List<Float> health=new ArrayList<>();List<Vec3> at=new ArrayList<>();
        for(int i=0;i<n;i++){knots.add(b.readVarInt());health.add(b.readFloat());at.add(vec(b));}
        return new SealView(v[0],v[1],v[2],v[3],v[4],v[5],v[6],drag,List.copyOf(knots),List.copyOf(health),List.copyOf(at));
    }
    public static final StreamCodec<RegistryFriendlyByteBuf,EndBattlePayload> STREAM_CODEC=StreamCodec.of((b,p)->{
        var v=p.view;
        for(int value:new int[]{v.stage(),v.phase(),v.clock(),v.stageAge(),v.boss(),v.dragon(),v.altar(),v.broken(),v.move(),v.windup(),v.grounded(),v.sampled()})b.writeInt(value);
        b.writeFloat(v.charge());b.writeFloat(v.safeAngle());b.writeFloat(v.floor());
        vec(b,v.origin());vec(b,v.target());vec(b,v.refuge());vec(b,v.feed());list(b,v.anchors());list(b,v.marks());seal(b,v.seal());
    },b->new EndBattlePayload(new EndView(b.readInt(),b.readInt(),b.readInt(),b.readInt(),b.readInt(),b.readInt(),b.readInt(),b.readInt(),b.readInt(),b.readInt(),b.readInt(),b.readInt(),
            b.readFloat(),b.readFloat(),b.readFloat(),vec(b),vec(b),vec(b),vec(b),list(b),list(b),seal(b))));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static void handle(EndBattlePayload p,IPayloadContext context){context.enqueueWork(()->EndClientState.accept(p.view));}
}
