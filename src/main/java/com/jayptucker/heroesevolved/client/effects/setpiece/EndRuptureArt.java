package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.client.VisualComfort;
import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient.Active;
import com.jayptucker.heroesevolved.endgame.EndGeometry;
import com.jayptucker.heroesevolved.mimicry.MimicColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.world.phys.Vec3;
import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.*;
import static net.minecraft.util.Mth.*;

/** The prologue blast's volumetric billows become a wave of twenty-one gifts, released from a cracking body. */
final class EndRuptureArt {
    private static final Vec3 UP=new Vec3(0,1,0);
    private static final float[] WHITE=rgb(0xFFFDF5),VIOLET=rgb(0xAA82EE),CYAN=rgb(0x8AE8F5),GOLD=rgb(0xFFD895),COAT=rgb(0x292134);
    static void draw(Canvas c,Active a,float t){
        if(t<0||t>EndGeometry.DEATH_TICKS+15)return;
        Vec3 feet=a.at(c),core=feet.add(0,1.15,0);float yaw=a.bound(c)==null?0:a.bound(c).getYRot();
        float charge=smooth(0,EndGeometry.RUPTURE_TICK,t),burst=t-EndGeometry.RUPTURE_TICK;
        if(burst<0){
            Vec3 forward=Vec3.directionFromRotation(0,yaw),side=Vec3.directionFromRotation(0,yaw+90);
            // The chest separates into irregular, white-edged facets before the body breaks.
            if(t>22)for(int i=0;i<6;i++){
                float ang=i*TWO_PI/6,open=smooth(22,65,t);Vec3 middle=core.add(side.scale(cos(ang)*(.15+.42*open))).add(0,sin(ang)*.35,0).add(forward.scale(.2+.32*open));
                if(c.solid())c.shard(middle,middle.add(side.scale(cos(ang)*.16)).add(0,.24,0),.13F,3,ang,COAT,1);
                if(c.glow()){c.line(middle.add(0,-.22,0),middle.add(0,.23,0),.035F+.02F*charge,WHITE,open,open);}
            }
            if(c.glow()){
                float light=charge*charge,comfort=VisualComfort.reduceFlashing()?.45F:1;
                c.glow(core,.25F+2.3F*light,WHITE,(.12F+.6F*light)*comfort);
                c.line(core.add(0,-.8,0),core.add(0,2,0),.04F+.2F*light,WHITE,light*.4F,light*.75F);
                for(int i=0;i<21;i++){
                    float ang=i*TWO_PI/21,tilt=(hash(i,44)-.5F)*1.4F;Vec3 dir=new Vec3(cos(ang),tilt,sin(ang)).normalize();float reach=(2+charge*charge*22)*( .65F+.35F*hash(i,1));
                    Vec3 start=core.add(dir.scale(.18+.3*charge));float[] color=rgb(MimicColors.color(i));
                    c.line(start,core.add(dir.scale(reach)),.025F+.07F*light,color,light*.9F,0);
                    c.line(start,core.add(dir.scale(reach*.45)),.012F+.025F*light,WHITE,light,0);
                }
                c.ring(core,new Vec3(0,0,1),1.1F-.7F*charge,.025F,0,TWO_PI,GOLD,charge*.6F);
            }return;
        }
        float radius=(float)EndGeometry.releaseRadius(t),fade=1-smooth(30,EndGeometry.DEATH_TICKS-EndGeometry.RUPTURE_TICK,burst);
        boolean minimal=Minecraft.getInstance().options.particles().get()==ParticleStatus.MINIMAL;
        // A few ticks of white light, followed by a readable coloured pressure front. No world explosion or damage.
        if(c.glow()){
            float flash=(1-smooth(1,8,burst))*(VisualComfort.reduceFlashing()?.25F:1);
            c.glow(core,7+burst*6,WHITE,flash*.9F);c.glow(core,2+burst*1.8F,CYAN,(float)Math.exp(-burst/16)*.65F);
            c.ring(core,UP,Math.max(.1F,radius),.32F,0,TWO_PI,WHITE,fade*.85F);
            c.ring(core,UP,Math.max(.1F,radius*.92F),.12F,0,TWO_PI,VIOLET,fade*.65F);
            for(int i=0;i<21;i++){
                float angle=i*TWO_PI/21;float[] color=rgb(MimicColors.color(i));Vec3 direction=new Vec3(cos(angle),0,sin(angle));
                Vec3 front=core.add(direction.scale(radius));c.line(core.add(direction.scale(radius*.65)),front,.1F+fade*.12F,color,0,fade*.7F);
                c.ring(core,UP,Math.max(.1F,radius),.65F,angle,angle+TWO_PI/21*.82F,color,fade*.32F);
                // Each gift writes its own shape into the moving front: crystalline splinters, circuits, rings and threads.
                if(!minimal){
                    Vec3 crown=front.add(0,4+sin(angle*3)*2,0);
                    if(i%4==0)c.shard(front,crown,.3F+fade*.45F,3,angle,color,fade*.7F);
                    else if(i%4==1)c.ring(crown,direction,2+fade*3,.045F,0,TWO_PI,color,fade*.65F);
                    else if(i%4==2)c.bolt(front.add(0,-2,0),crown.add(0,6,0),i,1,.06F,color,fade*.8F);
                    else c.line(front.add(-2,0,0),crown.add(2,0,0),.08F,color,0,fade*.8F);
                }
            }
        }
        if(c.soft()){
            float r=Math.max(.1F,radius);c.shell(core,r,r*.72F,r,PI,WHITE,fade*.055F,.2F,PLAIN,0);
            int count=minimal?7:21;
            for(int i=0;i<count;i++){
                float angle=i*TWO_PI/count,roll=burst*.02F+i;float[] color=mix(rgb(MimicColors.color(i*21/count)),WHITE,.52F);
                Vec3 at=core.add(cos(angle)*radius,2+sin(roll)*3,sin(angle)*radius);float size=2+Math.min(12,burst*.2F);
                c.cloud(at,size*1.5F,size*.6F,size*1.5F,i,roll,fade*.28F,Shades.flat(color));
            }
            // The prologue's rolling cloud crown, now translucent charged light rather than smoke.
            float lift=burst*1.3F,major=Math.min(42,burst*.9F),minor=2+Math.min(9,burst*.15F);
            for(int i=0;i<(minimal?6:12);i++){
                float angle=i*TWO_PI/(minimal?6:12);float[] color=mix(rgb(MimicColors.color(i%21)),WHITE,.7F);
                c.cloud(core.add(cos(angle)*major,lift,sin(angle)*major),minor*1.4F,minor,minor*1.4F,40+i,-burst*.025F,fade*.18F,Shades.flat(color));
            }
        }
        // Six body-sized fragments peel away from the actual pose, whiten and disappear in the first pulse.
        if(burst<20){float gone=1-smooth(6,20,burst);for(int i=0;i<8;i++){
            float ang=i*TWO_PI/8;Vec3 at=core.add(cos(ang)*burst*.22,(hash(i,5)-.25)*burst*.22,sin(ang)*burst*.22);
            if(c.solid())c.box(at,.14F,.22F,.1F,ang+burst*.08F,burst*.06F,ang,COAT,gone);
            if(c.glow())c.line(at.add(0,-.2,0),at.add(0,.2,0),.035F,WHITE,gone,0);
        }}
    }
    private EndRuptureArt(){}
}
