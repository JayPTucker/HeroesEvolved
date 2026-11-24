package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient.Active;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.wither.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;
import java.util.Set;
import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.*;
import static net.minecraft.util.Mth.*;

/** Bone white edges around mint light, violet refraction and a dark center. Warning dimensions match WitherMoves. */
final class WitherArt {
    private static final Vec3 UP=new Vec3(0,1,0);
    private static final float[] MINT=rgb(0x6EE9CF),WHITE=rgb(0xE8FFF4),VIOLET=rgb(0xA18AE4),DARK=rgb(0x06070E),RED=rgb(0xFF947F);
    private static final Set<Piece> CANCEL=Set.of(Piece.WITHER_CANCEL,Piece.WITHER_BACKFIRE,Piece.WITHER_PHASE,Piece.WITHER_DEATH);
    private WitherArt(){}
    static void draw(Canvas c,Active a,float t) {
        float alpha=Math.min(smooth(0,2,t),1-smooth(a.piece().ticks-4,a.piece().ticks,t));
        Vec3 core=a.origin().add(0,2.1,0);
        if(a.piece()!=Piece.WITHER_FORM&&a.bound(c) instanceof RiftWitherEntity body)core=WitherGeometry.core(body.getPosition(c.partial));
        switch(a.piece()) {
            case WITHER_BREACH -> gate(c,a.origin(),a.target().subtract(a.origin()).normalize(),1,alpha,c.time());
            case WITHER_FORM -> {
                if(t>30) {
                    Vec3 normal=Vec3.directionFromRotation(0,a.bound(c)==null?0:a.bound(c).getYRot());
                    gate(c,core,normal,smooth(30,66,t)*(1-.45F*smooth(102,120,t)),alpha,c.time());
                    if(c.glow())for(int i=0;i<3;i++) {
                        float pull=smooth(66+i*12,88+i*12,t);
                        Vec3 head=core.add((i-1)*1.5,2.2-Math.abs(i-1)*.7,0);
                        thread(c,head.lerp(core,pull),core,.08F,WHITE,alpha*(1-pull));
                    }
                }
            }
            case WITHER_AURA -> {
                if(a.bound(c) instanceof RiftWitherEntity b)core=WitherGeometry.core(b.getPosition(c.partial));
                int phase=a.seed()&3;float[] tint=phase==3?RED:MINT;
                if(c.glow()) {
                    star(c,core,.62F+phase*.16F,tint,alpha,c.time());
                    if(a.bound(c) instanceof RiftWitherEntity b){
                        for(int i=0;i<3;i++)c.ring(core,new Vec3(0,0,1),.9F+i*.12F,.025F,-HALF_PI+i*.22F,HALF_PI+i*.22F,i<b.stance()?WHITE:tint,alpha*(i<b.stance()?.9F:.2F));
                        for(int i=0;i<3;i++){Vec3 head=WitherGeometry.head(b.getPosition(c.partial),b.yBodyRot,i);star(c,head.add(Vec3.directionFromRotation(0,b.yBodyRot).scale(.85)),.12F,tint,alpha,c.time());}
                    }
                    int index=((a.seed()>>>5)&3)-1;
                    if(index>=0) {
                        Vec3 at=Vec3.atCenterOf(WitherGeometry.ANCHORS.get(index));
                        thread(c,at,core,.055F,tint,alpha*.7F);
                        c.ring(at,UP,1.7F,.035F,0,TWO_PI,tint,alpha*.7F);c.glow(at,1.4F,tint,alpha*.3F);
                    }
                }
            }
            case WITHER_WARNING -> warning(c,a,t);
            case WITHER_HEAD_WARN -> {
                if(SetPieceClient.superseded(a,CANCEL,c.level.getGameTime())||t>WitherGeometry.HEAD_WINDUP)return;
                if(c.glow()){float p=clamp(t/WitherGeometry.HEAD_WINDUP,0,1);star(c,a.origin(),.15F+.35F*p,MINT,alpha,t);thread(c,a.origin(),a.target(),.015F,MINT,alpha*.4F);c.ring(a.target(),UP,WitherGeometry.SKULL_RADIUS,.03F,-HALF_PI,-HALF_PI+TWO_PI*p,WHITE,alpha);}
            }
            case WITHER_MUZZLE -> {if(c.glow()){float fade=1-smooth(0,12,t);star(c,a.origin(),(.25F+t*.06F)*fade,WHITE,fade,t);c.ring(a.origin(),a.target().subtract(a.origin()).normalize(),.3F+t*.07F,.04F,0,TWO_PI,MINT,fade);}}
            case WITHER_FIRE -> {
                if(c.glow()){
                    float envelope=(1-smooth(4,16,t));float[] tint=a.bound(c) instanceof RiftWitherEntity b&&b.phase()==3?RED:MINT;
                    thread(c,a.origin(),a.target(),a.scale()*.5F*envelope,tint,alpha);c.line(a.origin(),a.target(),.09F*envelope,WHITE,alpha,alpha);
                    for(int i=0;i<4;i++)c.ring(a.origin().lerp(a.target(),i/4D),a.target().subtract(a.origin()).normalize(),a.scale()*.9F,.04F,0,TWO_PI,tint,alpha*envelope);
                }
            }
            case WITHER_WAKE -> {
                if(c.glow()){Vec3 side=a.target().subtract(a.origin()).normalize().cross(UP);for(int i:new int[]{-1,1})c.line(a.origin().add(side.scale(i*2)),a.target().add(side.scale(i*2)),.15F,MINT,0,alpha*.7F);}
            }
            case WITHER_ARC -> {
                if(c.glow()){float half=a.seed()==WitherGeometry.Move.BITE.ordinal()?WitherGeometry.BITE_HALF_ANGLE:WitherGeometry.RAKE_HALF_ANGLE;
                    float angle=(float)Math.atan2(a.target().z-a.origin().z,a.target().x-a.origin().x),sweep=smooth(0,10,t);
                    c.ring(a.origin(),UP,a.scale(),.45F,angle-half,angle-half+2*half*sweep,MINT,alpha*.45F);c.ring(a.origin(),UP,a.scale(),.065F,angle-half,angle-half+2*half*sweep,WHITE,alpha);
                }
            }
            case WITHER_PULSE -> {if(c.glow()&&!SetPieceClient.superseded(a,CANCEL,c.level.getGameTime())) {float radius=(float)(t*WitherGeometry.PULSE_SPEED);c.ring(a.origin(),radius,.15F,MINT,alpha*.5F);c.ring(a.origin().add(0,.08,0),radius,.025F,WHITE,alpha);}}
            case WITHER_IMPACT,WITHER_NOVA -> {
                if(c.glow()){float radius=(a.piece()==Piece.WITHER_NOVA?42:a.scale())*smooth(0,16,t);c.ring(a.origin(),radius,.12F,MINT,alpha);c.glow(a.origin(),Math.min(4,radius),WHITE,alpha*.3F);}
                if(c.soft()&&a.piece()==Piece.WITHER_IMPACT)for(int i=0;i<10;i++){float angle=i*TWO_PI/10;Vec3 at=a.origin().add(cos(angle)*t*.3,.15,sin(angle)*t*.3);c.cloud(at,.5F+t*.05F,.25F+t*.02F,.5F+t*.05F,i,t*.04F,alpha*.3F,Shades.smoke(c,rgb(0x777872),0));}
            }
            case WITHER_BACKFIRE -> {if(c.glow()){thread(c,a.origin(),a.target(),.3F*alpha,WHITE,alpha);star(c,a.target(),1.3F*alpha,MINT,alpha,t);}}
            case WITHER_PHASE -> {if(c.glow()){c.ring(a.origin().add(0,.05,0),t*.8F,.12F,a.scale()>2?RED:MINT,alpha*.6F);}}
            case WITHER_DEATH -> {
                float collapse=1-smooth(30,60,t);
                if(c.glow()) {
                    star(c,core,(.5F+smooth(0,25,t))*collapse,WHITE,alpha,t*2);
                    for(int i=0;i<3;i++){double angle=i*TWO_PI/3+t*.045;Vec3 end=core.add(Math.cos(angle)*9*collapse,3*collapse,Math.sin(angle)*9*collapse);thread(c,end,core,.13F*collapse,MINT,alpha);}
                    if(t>55)c.ring(core,UP,(t-55)*.8F,.12F,0,TWO_PI,WHITE,alpha);
                }
            }
            default -> {}
        }
    }
    private static void gate(Canvas c,Vec3 at,Vec3 normal,float size,float alpha,float time) {
        if(size<.01)return;
        Vec3 side=normal.cross(UP).normalize();
        if(c.soft()) {
            // Fill precisely the same ellipse as the rim; no opaque circular plate outside the opening.
            for(int i=0;i<64;i++) {
                double u=i*Math.PI/32,v=(i+1)*Math.PI/32;
                Vec3 b=at.add(side.scale(Math.cos(u)*1.1*size)).add(0,Math.sin(u)*2*size,0),d=at.add(side.scale(Math.cos(v)*1.1*size)).add(0,Math.sin(v)*2*size,0);
                c.v(at.x,at.y,at.z,DARK,alpha*.97F);c.v(b.x,b.y,b.z,DARK,alpha*.88F);c.v(d.x,d.y,d.z,DARK,alpha*.88F);
            }
            c.disc(at.add(normal.scale(.015)),normal,.8F*size,VIOLET,alpha*.15F,0);
        }
        if(!c.glow())return;
        Vec3[] rim=new Vec3[65];
        for(int i=0;i<rim.length;i++) {
            float angle=i*TWO_PI/64;
            double jag=1+.055*Math.sin(i*2.7+time*.11)+.035*Math.cos(i*4.1-time*.08);
            rim[i]=at.add(side.scale(Math.cos(angle)*1.15*size*jag)).add(0,Math.sin(angle)*2.05*size*jag,0);
        }
        c.ribbon(rim,.14F*size,VIOLET,alpha*.35F,alpha*.35F);
        c.ribbon(rim,.045F*size,MINT,alpha*.8F,alpha*.8F);
        c.ribbon(rim,.008F*size,WHITE,alpha*.9F,alpha*.9F);
        for(int i=0;i<6;i++) {
            float angle=i*TWO_PI/6+time*.015F;
            Vec3 from=at.add(side.scale(cos(angle)*1.5*size)).add(0,sin(angle)*2.5*size,0);
            Vec3 to=at.add(side.scale(cos(angle+.7F)*.45*size)).add(0,sin(angle+.7F)*.8*size,0);
            thread(c,from,to,.016F,MINT,alpha*.35F);
        }
    }
    private static void warning(Canvas c,Active a,float t) {
        if(SetPieceClient.superseded(a,CANCEL,c.level.getGameTime()))return;
        WitherGeometry.Move move=WitherGeometry.Move.values()[Math.floorMod(a.seed(),WitherGeometry.Move.values().length)];
        if(t>move.windup)return;
        float p=clamp(t/move.windup,0,1),alpha=smooth(0,3,t);Vec3 from=a.origin(),to=a.target();
        if(!c.glow())return;
        switch(move) {
            case BITE,RAKE -> {
                if(!(a.bound(c) instanceof RiftWitherEntity b))return;
                float reach=move==WitherGeometry.Move.BITE?WitherGeometry.BITE_REACH:WitherGeometry.RAKE_REACH,half=move==WitherGeometry.Move.BITE?WitherGeometry.BITE_HALF_ANGLE:WitherGeometry.RAKE_HALF_ANGLE;
                float angle=(float)Math.atan2(b.aim().z-from.z,b.aim().x-from.x);
                c.ring(from,UP,reach,.025F,angle-half,angle+half,MINT,alpha*.65F);
                c.ring(from,UP,reach*(1-p*.35F),.04F,angle-half,angle+half,WHITE,alpha*.8F);
                for(int side:new int[]{-1,1})c.line(from,from.add(cos(angle+side*half)*reach,0,sin(angle+side*half)*reach),.02F,MINT,0,alpha*.65F);
            }
            case VERDICT,TETHER,LUNGE -> {
                float width=move==WitherGeometry.Move.LUNGE?WitherGeometry.LUNGE_RADIUS:move==WitherGeometry.Move.TETHER?WitherGeometry.TETHER_RADIUS:WitherGeometry.VERDICT_RADIUS;
                Vec3 side=to.subtract(from).normalize().cross(UP).scale(width);
                c.line(from.add(side),to.add(side),.025F,MINT,alpha*.35F,alpha*.7F);
                c.line(from.subtract(side),to.subtract(side),.025F,MINT,alpha*.35F,alpha*.7F);
                thread(c,from,from.lerp(to,p),.03F,WHITE,alpha*.8F);
                c.ring(to,UP,.5F,.03F,0,TWO_PI*p,MINT,alpha);
            }
            case RAIN,SLAM -> {
                Vec3 at=move==WitherGeometry.Move.RAIN?to.add(0,-.75,0):to.add(0,.05,0);
                float radius=move==WitherGeometry.Move.SLAM?WitherGeometry.SLAM_RADIUS:WitherGeometry.SKULL_RADIUS;
                c.ring(at,radius,.025F,MINT,alpha*.6F);c.ring(at,UP,radius,.09F,-HALF_PI,-HALF_PI+TWO_PI*p,WHITE,alpha*.85F);
                c.ring(at,radius*(1-p),.025F,VIOLET,alpha*.6F);
                if(move==WitherGeometry.Move.RAIN)c.line(to.add(0,WitherGeometry.RAIN_HEIGHT,0),to,.025F,MINT,0,alpha*.6F);
            }
            case PULSE -> {Vec3 at=new Vec3(from.x,WitherGeometry.floor().y+.05,from.z);c.ring(at,3,.025F,MINT,alpha);c.ring(at,UP,3,.14F,0,TWO_PI*p,WHITE,alpha);}
            case HORIZON -> {
                star(c,from,.4F+p*.8F,WHITE,alpha,t);
                for(int i=0;i<36;i++) {
                    double angle=i*Math.PI/18;Vec3 end=from.add(Math.cos(angle)*70,-from.y+WitherGeometry.floor().y+.1,Math.sin(angle)*70);
                    Vec3 stop=c.level.clip(new ClipContext(from,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,net.minecraft.world.phys.shapes.CollisionContext.empty())).getLocation();
                    c.line(from,stop,.02F,MINT,0,alpha*p*.45F);
                    c.glow(stop,.13F,MINT,alpha*p*.5F);
                }
            }
        }
    }
    private static void star(Canvas c,Vec3 at,float radius,float[] tint,float alpha,float time) {
        c.glow(at,radius*2,tint,alpha*.25F);c.glow(at,radius*.65F,WHITE,alpha*.65F);
        for(int i=0;i<3;i++) {
            Vec3 axis=new Vec3(cos(time*.035F+i*2),sin(time*.025F+i),sin(time*.035F+i*2)).normalize();
            c.ring(at,axis,radius,.018F,0,TWO_PI,tint,alpha*.8F);
            c.line(at.subtract(axis.scale(radius*1.5)),at.add(axis.scale(radius*1.5)),.015F,WHITE,0,alpha*.8F);
        }
    }
    private static void thread(Canvas c,Vec3 from,Vec3 to,float width,float[] tint,float alpha) {
        Vec3[] points=new Vec3[13];Vec3 side=to.subtract(from).normalize().cross(UP);
        for(int i=0;i<13;i++){float p=i/12F;points[i]=from.lerp(to,p).add(side.scale(sin(p*PI)*sin(p*PI*3+c.time()*.08F)*.12));}
        c.ribbon(points,width*3,tint,alpha*.13F,alpha*.13F);c.ribbon(points,width,tint,alpha*.7F,alpha*.7F);c.ribbon(points,width*.17F,WHITE,alpha*.8F,alpha*.8F);
    }
}
