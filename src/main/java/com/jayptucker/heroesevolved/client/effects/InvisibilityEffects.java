package com.jayptucker.heroesevolved.client.effects;

import com.jayptucker.heroesevolved.client.OpticalClient;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import static com.jayptucker.heroesevolved.client.effects.AbilityEffectRenderer.*;

/** Sparse silver/cyan/violet refraction contours. Optical powers bend light rather than emit smoke. */
final class InvisibilityEffects {
    private static final Vec3 UP = new Vec3(0,1,0);
    private static final int SILVER=0xE5F8FF,CYAN=0x8ACEEA,VIOLET=0xC3A9F3;
    private InvisibilityEffects() {}
    static boolean play(ClientLevel level, Kind kind, Vec3 from, Vec3 to, float size, boolean detail) {
        switch(kind) {
            case OPTIC_FADE -> dissolve(level,from,false,detail);
            case OPTIC_REFORM -> dissolve(level,from,true,detail);
            case OPTIC_RIPPLE -> {
                OpticalClient.ripple(from);
                for(int i=0;i<(detail?3:1);i++) {
                    double y=.4+i*.55;
                    brokenLoop(from.add(0,y,0),UP,.35F+i*.08F,level.getGameTime()*.2+i,detail?14:8,CYAN,.33F,8,i);
                }
            }
            case OPTIC_STEP -> step(level,from,to,detail);
            case OPTIC_VEIL -> veil(level,from,size,detail);
            case OPTIC_BURST, OPTIC_DECOY_SHATTER -> shatter(level,from,size,detail);
            case OPTIC_THREAD -> thread(level,from,to,detail);
            case OPTIC_FOOTSTEP -> {
                ring(level,from.add(0,.025,0),UP,.08F,.65F,14,0,CYAN,.28F);
                ring(level,from.add(0,.03,0),UP,.04F,.35F,10,2,SILVER,.3F);
                for(int side=-1;side<=1;side+=2)
                    streak(level,from.add(side*.12,.04,-.11),from.add(side*.12,.04,.12),.025F,10,VIOLET);
            }
            default -> { return false; }
        }
        return true;
    }
    private static void dissolve(ClientLevel level,Vec3 feet,boolean appearing,boolean detail) {
        int strips=detail?12:6;
        for(int i=0;i<strips;i++) {
            double angle=i*Math.PI*2/strips, radius=.32+(i%3)*.08;
            Vec3 base=feet.add(Math.cos(angle)*radius,.15+(i%4)*.35,Math.sin(angle)*radius);
            Vec3 end=base.add(Math.cos(angle)*(appearing?-.18:.4),.35,Math.sin(angle)*(appearing?-.18:.4));
            LightningRenderer.stroke(List.of(base,end.lerp(base,.3).add(0,.08,0),end),.023F,.007F,
                    i%3==0?VIOLET:i%3==1?CYAN:SILVER,.55F,12,i%4);
        }
        brokenLoop(feet.add(0,1.0,0),UP,appearing?.75F:.25F,level.getGameTime()*.1,detail?18:10,SILVER,.4F,14,0);
    }
    private static void step(ClientLevel level,Vec3 from,Vec3 to,boolean detail) {
        Vec3 axis=to.subtract(from).normalize(),side=axis.cross(UP).normalize();
        if(side.lengthSqr()<.01)side=new Vec3(1,0,0);
        for(int strand=0;strand<(detail?5:2);strand++) {
            List<Vec3> points=new ArrayList<>();
            for(int i=0;i<=18;i++) {
                double t=i/18.0,offset=(strand-2)*.13;
                points.add(from.lerp(to,t).add(side.scale(offset+Math.sin(t*Math.PI*2+strand)*.1)).add(0,.35+strand*.24,0));
            }
            LightningRenderer.stroke(points,.025F,.006F,strand%2==0?CYAN:VIOLET,.5F,13,strand);
        }
        for(int i=1;i<=(detail?4:2);i++) {
            Vec3 center=from.lerp(to,i/(detail?5.0:3.0)).add(0,.9,0);
            brokenLoop(center,axis,.6F,level.getGameTime()*.1+i,detail?18:10,SILVER,.35F,10,i);
        }
    }
    private static void veil(ClientLevel level,Vec3 center,float radius,boolean detail) {
        double phase=level.getGameTime()*.04;
        // Open, separated meridians keep the world readable rather than putting an opaque bubble over it.
        int arcs=detail?6:3;
        for(int arc=0;arc<arcs;arc++) {
            double yaw=phase+arc*Math.PI*2/arcs;
            List<Vec3> points=new ArrayList<>();
            for(int i=0;i<=16;i++) {
                double t=i/16.0*Math.PI*.63;
                points.add(center.add(Math.cos(yaw)*Math.cos(t)*radius,
                        .08+Math.sin(t)*Math.min(2.7,radius*.7),Math.sin(yaw)*Math.cos(t)*radius));
            }
            LightningRenderer.stroke(points,.014F,.004F,arc%2==0?CYAN:VIOLET,.22F,13,arc%2);
        }
        brokenLoop(center.add(0,.035,0),UP,radius,phase,detail?32:18,CYAN,.25F,13,0);
    }
    private static void thread(ClientLevel level,Vec3 from,Vec3 to,boolean detail) {
        Vec3 axis=to.subtract(from).normalize(),side=axis.cross(UP).normalize();
        if(side.lengthSqr()<.01)side=new Vec3(1,0,0);
        List<Vec3> points=new ArrayList<>();
        for(int i=0;i<=18;i++) {
            double t=i/18.0;
            points.add(from.lerp(to,t).add(side.scale(Math.sin(t*Math.PI*4+level.getGameTime()*.1)*.055)));
        }
        LightningRenderer.stroke(points,.015F,.004F,CYAN,.42F,12,0);
        if(detail)brokenLoop(to,axis,.35F,level.getGameTime()*.1,16,SILVER,.35F,10,0);
    }
    private static void shatter(ClientLevel level,Vec3 feet,float size,boolean detail) {
        Vec3 center=feet.add(0,1,0);
        int shards=detail?18:8;
        for(int i=0;i<shards;i++) {
            double a=i*Math.PI*2/shards;
            Vec3 direction=new Vec3(Math.cos(a),Math.sin(a*2.3)*.65,Math.sin(a));
            Vec3 middle=center.add(direction.scale(.35)),tip=center.add(direction.scale(Math.min(2,size)*.75));
            LightningRenderer.stroke(List.of(middle,tip,tip.add(0,.12,0)),.032F,.005F,
                    i%2==0?SILVER:VIOLET,.65F,15,i%3);
            if(detail)mote(level,middle,direction.scale(.04),.045F,14,CYAN);
        }
        ring(level,feet.add(0,.05,0),UP,.15F,Math.min(4,size),16,0,CYAN,.25F);
    }
    private static void brokenLoop(Vec3 center,Vec3 normal,float radius,double phase,int count,int color,float alpha,int ticks,int delay) {
        Vec3 side=normal.cross(Math.abs(normal.y)>.9?new Vec3(1,0,0):UP).normalize();
        Vec3 up=normal.cross(side).normalize();
        for(int part=0;part<3;part++) {
            List<Vec3> points=new ArrayList<>();
            for(int i=0;i<=count/3;i++) {
                double angle=phase+part*Math.PI*2/3+i/(double)(count/3)*Math.PI*.46;
                points.add(center.add(side.scale(Math.cos(angle)*radius)).add(up.scale(Math.sin(angle)*radius)));
            }
            LightningRenderer.stroke(points,.018F,.005F,color,alpha,ticks,delay);
        }
    }
}
