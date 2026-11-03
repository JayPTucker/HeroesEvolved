package com.jayptucker.heroesevolved.client.effects;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import java.util.*;
/** Fine violet thought filaments and broken concentric irises, using the mod's existing glow renderer. */
final class TelepathyEffects {
    static boolean play(ClientLevel level,Kind kind,Vec3 from,Vec3 to,float size,boolean detail){
        if(kind!=Kind.MIND_LINK&&kind!=Kind.MIND_PULSE)return false;
        double phase=level.getGameTime()*.13;
        if(kind==Kind.MIND_LINK){
            Vec3 axis=to.subtract(from).normalize();Vec3 side=axis.cross(new Vec3(0,1,0));if(side.lengthSqr()<.01)side=new Vec3(1,0,0);side=side.normalize();Vec3 up=axis.cross(side).normalize();
            for(int strand=0;strand<(detail?3:1);strand++){
                List<Vec3> points=new ArrayList<>();for(int i=0;i<=24;i++){double t=i/24.0,a=t*12+phase+strand*2.094,r=Math.sin(t*Math.PI)*.11*size;points.add(from.lerp(to,t).add(side.scale(Math.cos(a)*r)).add(up.scale(Math.sin(a)*r)));}
                LightningRenderer.stroke(points,.012f,.006f,strand==0?0xE8D3FF:0xA76BE8,.65f,9,0);
            }
            for(int ring=0;ring<(detail?3:1);ring++){
                List<Vec3> points=new ArrayList<>();for(int i=0;i<=24;i++){double a=i/24.0*Math.PI*1.7+phase*(ring%2==0?1:-1);points.add(to.add(side.scale(Math.cos(a)*(.22+ring*.10))).add(up.scale(Math.sin(a)*(.22+ring*.10))));}
                LightningRenderer.stroke(points,.018f,.006f,0xD4A3FF,.7f,10,ring);
            }
        }else for(int shard=0;shard<(detail?16:8);shard++){
            double a=shard*Math.PI/8;Vec3 direction=new Vec3(Math.cos(a),Math.sin(a*.7)*.7,Math.sin(a));
            LightningRenderer.stroke(List.of(from.add(direction.scale(.15)),from.add(direction.scale(.6*size)),from.add(direction.scale(size))),.025f,.008f,0xD1A6FF,.7f,18,shard%4);
        }
        return true;
    }
}
