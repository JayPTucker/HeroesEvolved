package com.jayptucker.heroesevolved.client.effects.setpiece;

import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient.Active;
import com.jayptucker.heroesevolved.endgame.*;
import com.jayptucker.heroesevolved.mimicry.MimicColors;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.phys.Vec3;
import java.util.Set;
import static com.jayptucker.heroesevolved.client.effects.setpiece.Canvas.*;
import static net.minecraft.util.Mth.*;

/** Constellation bones, split membranes and borrowed wings. All attack volumes use the server's geometry. */
final class EndBattleArt {
    private static final Vec3 UP=new Vec3(0,1,0);
    private static final float[] PALE=rgb(0xE8E3FF),VIOLET=rgb(0xB18AFF),CYAN=rgb(0x7BE8F4),GOLD=rgb(0xFFD58A),RED=rgb(0xFF8B9A),DARK=rgb(0x20112F);
    private static final Set<Piece> CANCEL=Set.of(Piece.END_BATTLE_CANCEL,Piece.END_BATTLE_PHASE,Piece.END_BATTLE_RELEASE,Piece.END_BATTLE_ABSORB);
    private static final Set<Piece> FIELD=Set.of(Piece.END_BATTLE_FIELD),CHANNEL=Set.of(Piece.END_BATTLE_CHANNEL);
    static void draw(Canvas c,Active a,float t){
        float alpha=Math.min(smooth(0,2,t),1-smooth(a.piece().ticks-4,a.piece().ticks,t));
        switch(a.piece()){
            case END_BATTLE_FIELD -> {if(!SetPieceClient.superseded(a,FIELD,c.level.getGameTime()))field(c,a,alpha);}
            case END_BATTLE_WARNING -> warning(c,a,t);
            case END_BATTLE_CHANNEL -> {if(!SetPieceClient.superseded(a,CHANNEL,c.level.getGameTime()))channel(c,a,alpha);}
            case END_BATTLE_REFUGE -> {if(!SetPieceClient.superseded(a,Set.of(Piece.END_BATTLE_REFUGE),c.level.getGameTime()))gate(c,a.origin(),alpha,c.time());}
            case END_BATTLE_ABSORB -> absorption(c,a,t);
            case END_BATTLE_RELEASE -> release(c,a,t);
            case END_BATTLE_GROUNDED,END_BATTLE_BACKFIRE -> {
                if(c.glow()){arc(c,a.origin(),a.target(),.13F*alpha,PALE,alpha,c.time());c.ring(a.target(),t*.3F,.07F,CYAN,alpha);star(c,a.target(),(.5F+t*.035F)*alpha,GOLD,alpha,t);}
            }
            case END_BATTLE_PHASE -> {
                if(c.glow()){c.ring(a.origin().add(0,.15,0),t*2.3F,.1F,a.scale()>3?GOLD:VIOLET,alpha*.6F);star(c,a.at(c).add(0,3,0),(1+t*.04F)*alpha,PALE,alpha,t);}
                if(c.soft())for(int i=0;i<12;i++){float ang=i*TWO_PI/12+t*.02F;Vec3 at=a.origin().add(cos(ang)*t*.7,2+t*.2,sin(ang)*t*.7);c.shard(at,at.add(0,4*alpha,0),.24F*alpha,3,ang,VIOLET,alpha*.35F);}
            }
            case END_BATTLE_STRIKE -> strike(c,a,t,alpha);
            case END_BATTLE_IMPACT -> impact(c,a.origin(),a.scale(),rgb(MimicColors.color(a.seed()-1)),t,alpha);
            case END_BATTLE_NOVA -> {if(c.glow()){float r=t*8;for(int i=0;i<3;i++)c.ring(a.origin(),i==0?UP:i==1?new Vec3(1,0,0):new Vec3(0,0,1),r,.18F,0,TWO_PI,PALE,alpha*.55F);c.glow(a.origin(),3,VIOLET,alpha*.5F);}}
            case END_BATTLE_WAVE -> {if(c.glow())wave(c,a.origin(),(float)(t*EndGeometry.WAVE_SPEED),alpha);}
            case END_BATTLE_DASH -> {if(c.glow()){Vec3 delta=a.target().subtract(a.origin());Vec3 side=perpendicular(delta);for(int i=-1;i<=1;i++)c.line(a.origin().add(side.scale(i*a.scale()*.7)),a.target().add(side.scale(i*a.scale()*.7)),.12F*alpha,CYAN,0,alpha*.5F);}}
            case END_BATTLE_POOL -> pool(c,a,t);
            case END_BATTLE_BOLT -> {Vec3 at=a.at(c);float[] tint=rgb(MimicColors.color(a.seed()-1));if(c.solid())c.shard(at.add(0,.5,0),at.add(0,-.5,0),.24F,4,t*.08F,PALE,1);if(c.glow()){star(c,at,.6F,tint,alpha,c.time());c.line(at,at.add(0,2,0),.11F,tint,alpha,0);}}
            default -> {}
        }
    }
    private static void field(Canvas c,Active a,float alpha){
        EndView v=EndClientState.view();if(!v.active()||v.stage()==EndBattle.VICTORY)return;
        float[] tint=v.phase()==3?RED:v.phase()==2?CYAN:VIOLET;
        if(a.bound(c) instanceof EnderDragon dragon){
            Vec3 chest=dragon.getPosition(c.partial).add(0,2.4,0),head=dragon.head.getPosition(c.partial).add(0,1.2,0);
            float yaw=-dragon.getYRot()*DEG_TO_RAD;Vec3 side=new Vec3(cos(yaw),0,sin(yaw));
            Vec3 back=chest.subtract(head).multiply(1,0,1).normalize();
            if(c.solid())for(int i=0;i<5;i++){
                Vec3 base=chest.add(back.scale((i-1)*1.3)).add(0,1,0);
                c.shard(base,base.add(back.scale(.8)).add(0,1.8+v.phase()*.5,0),.6F,3,i*.2F,rgb(0x725A91),1);
            }
            if(c.soft()&&v.phase()>=2)for(int s:new int[]{-1,1}){
                Vec3 root=chest.add(side.scale(s*2)),elbow=chest.add(side.scale(s*7)).add(0,2,0),tip=chest.add(side.scale(s*(13+v.phase()))).add(0,1+sin(c.time()*.12F)*2,0).add(back.scale(3));
                c.tri(root,elbow,tip,tint,alpha*.03F,alpha*.13F,alpha*.1F);c.tri(root,tip,elbow,tint,alpha*.03F,alpha*.1F,alpha*.13F);
            }
            if(c.glow()){
                star(c,chest,.9F+v.phase()*.25F,tint,alpha,c.time()*.7F);arc(c,chest,head,.065F,tint,alpha*.75F,c.time());
                for(int i=0;i<5;i++){Vec3 rib=chest.add(new Vec3(0,0,(i-2)*1.1).yRot(-dragon.getYRot()*DEG_TO_RAD));c.ring(rib,side,2.2F,.018F,0,PI*1.5F,tint,alpha*.42F);}
                for(int s:new int[]{-1,1}){Vec3 root=chest.add(side.scale(s*2)),tip=chest.add(side.scale(s*(12+v.phase()*2))).add(0,2+sin(c.time()*.12F)*2,2);
                    arc(c,root,tip,.045F,tint,alpha*.55F,c.time());star(c,tip,.35F,PALE,alpha,c.time());}
                if(v.grounded()>0)c.ring(chest,UP,3.4F,.06F,0,TWO_PI,GOLD,alpha*.8F);
            }
        }else if(a.bound(c) instanceof LivingEntity boss&&v.inheritance()){
            Vec3 chest=boss.getPosition(c.partial).add(0,1.3,0);wings(c,chest,boss.getYRot(),v.phase(),alpha,c.time());
            if(c.glow()){star(c,chest,.23F+v.phase()*.07F,GOLD,alpha,c.time());c.ring(chest.add(0,1.6,0),new Vec3(0,0,1),.65F,.016F,-PI*.2F,PI*1.2F,PALE,alpha*.55F);}
        }
        if(!c.glow())return;
        for(int i=0;i<v.anchors().size();i++){
            Vec3 focus=v.anchors().get(i);boolean lit=v.inheritance()||i==v.altar();float[] col=lit?GOLD:VIOLET;
            star(c,focus,lit?.65F:.3F,col,alpha*(lit?.75F:.3F),c.time()*.4F);
            c.ring(focus.add(0,-3.3,0),2.1F,.024F,col,alpha*(lit?.6F:.2F));
            if(lit){c.ring(focus,UP,1,.025F,-HALF_PI,-HALF_PI+TWO_PI*clamp(v.charge()/EndTuning.CHANNEL_TICKS,0,1),PALE,alpha);if(v.dragonFight()&&v.feed().lengthSqr()>1)arc(c,focus,v.feed(),.025F,VIOLET,alpha*.3F,c.time());}
            Vec3 lift=focus.add(6,-3.4,0);c.ring(lift,2.8F,.025F,CYAN,alpha*.45F);
            if(!minimal()){Vec3[] helix=new Vec3[33];for(int n=0;n<helix.length;n++){float k=n/32F,ang=k*TWO_PI*3-c.time()*.055F;helix[n]=lift.add(cos(ang)*1.5,k*22,sin(ang)*1.5);}c.ribbon(helix,.04F,CYAN,alpha*.35F,0);}
        }
    }
    private static void wings(Canvas c,Vec3 chest,float yaw,int phase,float alpha,float time){
        float unfold=1;Vec3 side=Vec3.directionFromRotation(0,yaw+90),back=Vec3.directionFromRotation(0,yaw+180);
        for(int s:new int[]{-1,1}){
            Vec3 root=chest.add(side.scale(s*.3)).add(back.scale(.35));Vec3 elbow=root.add(side.scale(s*(3+phase)*unfold)).add(0,3.8+sin(time*.07F)*.7,0).add(back.scale(1));
            Vec3 tip=root.add(side.scale(s*(7+phase*3)*unfold)).add(0,2.7+sin(time*.07F)*1.2,0).add(back.scale(3));
            if(c.glow()){c.line(root,elbow,.045F,PALE,alpha*.8F,alpha*.7F);c.line(elbow,tip,.035F,PALE,alpha*.7F,alpha*.5F);}
            Vec3 last=elbow;
            for(int i=0;i<5;i++){
                float f=i/4F;Vec3 finger=elbow.lerp(tip,f).add(back.scale(1+f*4)).add(0,-2.5-f*3,0);
                if(c.soft()){c.tri(root,last,finger,VIOLET,alpha*.02F,alpha*.09F,alpha*.12F);c.tri(root,finger,last,VIOLET,alpha*.02F,alpha*.12F,alpha*.09F);}
                if(c.glow()){c.line(elbow,finger,.022F,i%2==0?GOLD:CYAN,alpha*.55F,alpha*.2F);c.line(last,finger,.018F,VIOLET,alpha*.3F,alpha*.3F);}
                last=finger;
            }
        }
    }
    private static void warning(Canvas c,Active a,float t){
        if(SetPieceClient.superseded(a,CANCEL,c.level.getGameTime()))return;
        EndGeometry.Move move=EndMoves.move(a.seed());if(move==null)return;
        if(DRAGON.contains(move)){dragonWarning(c,a,t,move);return;}
        int power=EndMoves.power(a.seed());int duration=EndMoves.windup(move,power);if(t>duration||t<0)return;
        float p=clamp(t/duration,0,1),alpha=smooth(0,3,t);float[] tint=power<0?VIOLET:rgb(MimicColors.color(power));
        Vec3 from=a.origin(),to=a.target();var shape=EndMoves.shape(a.seed());
        if(shape==EndMoves.Pattern.NOVA){
            if(EndMoves.role(a.seed())==1){orb(c,to,(float)EndGeometry.SAFE_ORBIT_RADIUS,GOLD,alpha*.7F);if(c.glow())c.ring(to,UP,(float)EndGeometry.SAFE_ORBIT_RADIUS,.075F,-HALF_PI,-HALF_PI+TWO_PI*p,PALE,alpha);return;}
            if(c.glow()){
                star(c,from,.5F+1.5F*p,tint,alpha,c.time());Vec3 center=EndClientState.view().center();double angle=Math.atan2(to.z-center.z,to.x-center.x);
                for(int s:new int[]{-1,1}){Vec3 end=center.add(Math.cos(angle+s*EndGeometry.SAFE_HALF_ANGLE)*120,.08,Math.sin(angle+s*EndGeometry.SAFE_HALF_ANGLE)*120);c.line(center.add(0,.08,0),end,.045F,GOLD,alpha*.6F,alpha*.35F);}
                for(int i=0;i<3;i++)c.ring(from,i==0?UP:i==1?new Vec3(1,0,0):new Vec3(0,0,1),2.8F-2*p,.035F,0,TWO_PI,tint,alpha*.7F);
            }return;
        }
        if(shape==EndMoves.Pattern.RING){if(c.glow()){c.ring(from,4,.025F,PALE,alpha*.6F);c.ring(from,4*(1-p),.07F,tint,alpha);for(int i=0;i<8;i++){double ang=i*Math.PI/4;Vec3 at=from.add(Math.cos(ang)*5,0,Math.sin(ang)*5);c.line(at,at.add(0,1.8,0),.025F,GOLD,alpha*.6F,0);}}return;}
        if(shape==EndMoves.Pattern.CHARGE||shape==EndMoves.Pattern.FAN||shape==EndMoves.Pattern.LATTICE||shape==EndMoves.Pattern.FRAME||shape==EndMoves.Pattern.PRISM||shape==EndMoves.Pattern.FAULT||shape==EndMoves.Pattern.SIPHON){
            float width=shape==EndMoves.Pattern.CHARGE?(move==EndGeometry.Move.RIFT_CHARGE?EndGeometry.CHARGE_RADIUS:EndGeometry.SYLAR_CHARGE_RADIUS):shape==EndMoves.Pattern.SIPHON?EndGeometry.TETHER_RADIUS:EndGeometry.LANE_RADIUS;
            tube(c,from,to,width,tint,alpha,p);
            if(c.glow()){
                if(shape==EndMoves.Pattern.FRAME||shape==EndMoves.Pattern.LATTICE||shape==EndMoves.Pattern.PRISM)for(int i=1;i<=4;i++){Vec3 point=from.lerp(to,i/4D);c.ring(point,to.subtract(from).normalize(),width,.025F,0,shape==EndMoves.Pattern.FRAME?PI*1.5F:TWO_PI,tint,alpha*.45F);}
                if(shape==EndMoves.Pattern.FAULT)for(int i=0;i<8;i++){Vec3 point=from.lerp(to,i/8D);c.shard(point.add(0,-1,0),point.add(0,1+2*p,0),.3F,3,i,tint,alpha*.35F);}
            }return;
        }
        float radius=move==EndGeometry.Move.STARS?EndGeometry.BOLT_RADIUS:EndGeometry.IMPACT_RADIUS;
        orb(c,to,radius,tint,alpha*.45F);
        if(c.glow()){
            c.ring(to,UP,radius,.07F,-HALF_PI,-HALF_PI+TWO_PI*p,PALE,alpha*.85F);star(c,to,.12F+.2F*p,tint,alpha,t);
            if(shape==EndMoves.Pattern.STONE){Vec3 at=to.add(0,5*(1-p),0);c.shard(at.add(-.6,-.4,0),at.add(.5,.6,0),.8F,5,t*.04F,GOLD,alpha*.55F);}
            if(shape==EndMoves.Pattern.MIND){c.ring(to,new Vec3(1,0,0),radius*(1-p*.8F),.04F,0,TWO_PI,tint,alpha*.7F);}
            if(shape==EndMoves.Pattern.SPIRAL){Vec3[] points=new Vec3[25];for(int i=0;i<25;i++){float k=i/24F,ang=k*TWO_PI*2+t*.05F;points[i]=to.add(cos(ang)*radius*(1-k),k*3,sin(ang)*radius*(1-k));}c.ribbon(points,.05F,CYAN,alpha*.7F,0);}
        }
    }
    // ------------------------------------------------------------------ the Dragon's ground warnings
    // Cataclysm-scale decals that hug the terrain: a faint footprint of the whole danger at once, a bright fill that
    // grows to cover it exactly as the hit arrives (EndGeometry.arrival), hot edges, and a white flash when it lands.

    private static final Set<EndGeometry.Move> DRAGON=java.util.EnumSet.of(EndGeometry.Move.RIFT_CHARGE,EndGeometry.Move.BREATH,EndGeometry.Move.WINGBREAK,
            EndGeometry.Move.DESCENT,EndGeometry.Move.BITE,EndGeometry.Move.TAIL,EndGeometry.Move.SCORCH,EndGeometry.Move.ROAR,EndGeometry.Move.TAKEOFF,EndGeometry.Move.RUSH);
    private static final float[] BREATH=rgb(0xD35BFF),EMBER=rgb(0xFF5A6E),WHITE=rgb(0xFFFFFF);

    private static void dragonWarning(Canvas c,Active a,float t,EndGeometry.Move move){
        boolean flash=EndMoves.role(a.seed())==1;int duration=EndGeometry.arrival(move);
        if(t<0||(flash?t>12:t>duration+1))return;
        float p=flash?1:clamp(t/duration,0,1),alpha=flash?1-t/12F:smooth(0,3,t);
        float[] tint=switch(move){case BITE,TAIL,RUSH->EMBER;case BREATH,SCORCH->BREATH;case DESCENT,ROAR->GOLD;default->VIOLET;};
        if(flash)tint=WHITE;
        Vec3 o=a.origin(),to=a.target();
        switch(move){
            case RIFT_CHARGE -> {
                strip(c,o,to,EndGeometry.DIVE_RADIUS,p,tint,alpha,true);
                Vec3 mid=o.lerp(to,.5);sector(c,mid,to.subtract(o),EndGeometry.DIVE_IMPACT,PI,p,flash?WHITE:EMBER,alpha*.9F);
                if(!flash&&c.glow()){Vec3 sky=ground(c,mid).add(0,26,0);c.line(sky,ground(c,mid).add(0,.2,0),.06F+.1F*p,EMBER,alpha*.15F,alpha*.8F);}
            }
            case BREATH -> strip(c,o,to,EndGeometry.RUN_RADIUS,p,tint,alpha,true);
            case BITE -> strip(c,o,to,EndGeometry.BITE_RADIUS,p,tint,alpha,true);
            case RUSH -> strip(c,o,to,EndGeometry.RUSH_RADIUS,p,tint,alpha,true);
            case TAIL -> sector(c,o,to.subtract(o),EndGeometry.TAIL_RADIUS,EndGeometry.TAIL_HALF,p,tint,alpha);
            case SCORCH -> sector(c,o,to.subtract(o),EndGeometry.SCORCH_RADIUS,EndGeometry.SCORCH_HALF,p,tint,alpha);
            case DESCENT -> {
                sector(c,o,new Vec3(1,0,0),EndGeometry.LAND_RADIUS,PI,p,tint,alpha);
                if(!flash&&c.glow()){Vec3 g=ground(c,o);for(int i=0;i<6;i++){float ang=i*TWO_PI/6+c.time()*.01F;c.line(g.add(cos(ang)*EndGeometry.LAND_RADIUS*1.3,.15,sin(ang)*EndGeometry.LAND_RADIUS*1.3),g.add(cos(ang)*EndGeometry.LAND_RADIUS*(1-p*.6),.15,sin(ang)*EndGeometry.LAND_RADIUS*(1-p*.6)),.12F,GOLD,alpha*.2F,alpha*.9F);}}
            }
            case ROAR -> sector(c,o,new Vec3(1,0,0),EndGeometry.ROAR_RADIUS,PI,p,tint,alpha*.8F);
            case TAKEOFF -> sector(c,o,new Vec3(1,0,0),EndGeometry.TAKEOFF_RADIUS,PI,p,tint,alpha*.8F);
            case WINGBREAK -> {
                // The wing buffet's wave rolls outward after this; the warning gathers in toward the body.
                if(c.glow())for(int i=0;i<3;i++){float r=3+(1-((p+i/3F)%1F))*9;ringOnGround(c,o,r,.09F,PALE,alpha*.75F);}
                if(c.glow())ringOnGround(c,o,3,.12F,VIOLET,alpha);
            }
            default -> {}
        }
    }
    private static Vec3 ground(Canvas c,Vec3 at){return new Vec3(at.x,EndGeometry.waveHeight(c.level,at.x,at.z,at.y)+.02,at.z);}
    private static Vec3 ground(Canvas c,double x,double z,double fallback){return new Vec3(x,EndGeometry.waveHeight(c.level,x,z,fallback)+.02,z);}
    /** A lane on the ground from a to b, filling from a toward b. */
    private static void strip(Canvas c,Vec3 a,Vec3 b,float radius,float p,float[] tint,float alpha,boolean chevrons){
        Vec3 d=b.subtract(a).multiply(1,0,1);double len=d.length();if(len<.1)return;Vec3 f=d.scale(1/len),s=new Vec3(-f.z,0,f.x);
        int n=Math.max(2,(int)Math.ceil(len/1.5));Vec3[] left=new Vec3[n+1],right=new Vec3[n+1];
        for(int i=0;i<=n;i++){Vec3 m=a.add(d.scale(i/(double)n));left[i]=ground(c,m.x+s.x*radius,m.z+s.z*radius,a.y);right[i]=ground(c,m.x-s.x*radius,m.z-s.z*radius,a.y);}
        if(c.soft())for(int i=0;i<n;i++){
            boolean lit=(i+1)/(float)n<=p+1e-4F;float fill=lit?.34F:.09F;
            c.tri(left[i],right[i],right[i+1],tint,alpha*fill,alpha*fill,alpha*fill);c.tri(left[i],right[i+1],left[i+1],tint,alpha*fill,alpha*fill,alpha*fill);
        }
        if(!c.glow())return;
        Vec3[] l=new Vec3[n+1],r=new Vec3[n+1];for(int i=0;i<=n;i++){l[i]=left[i].add(0,.03,0);r[i]=right[i].add(0,.03,0);}
        c.ribbon(l,.09F,tint,alpha*.9F,alpha*.9F);c.ribbon(r,.09F,tint,alpha*.9F,alpha*.9F);
        c.line(left[0].add(0,.03,0),right[0].add(0,.03,0),.09F,tint,alpha,alpha);c.line(left[n].add(0,.03,0),right[n].add(0,.03,0),.09F,tint,alpha,alpha);
        int k=Math.min(n,Math.round(p*n));c.line(left[k].add(0,.05,0),right[k].add(0,.05,0),.16F,PALE,alpha,alpha);
        if(chevrons)for(int i=1;i<n;i+=3){
            Vec3 m=ground(c,a.x+d.x*i/n,a.z+d.z*i/n,a.y).add(0,.05,0),tip=m.add(f.scale(1.1)),w1=m.add(s.scale(radius*.45)),w2=m.subtract(s.scale(radius*.45));
            float lit=i/(float)n<=p?1:.35F;c.line(w1,tip,.07F,PALE,alpha*lit*.7F,alpha*lit*.7F);c.line(w2,tip,.07F,PALE,alpha*lit*.7F,alpha*lit*.7F);
        }
    }
    /** A sector of {@code half} radians either side of {@code dir} (a disc when half is PI), filling outward from the centre. */
    private static void sector(Canvas c,Vec3 at,Vec3 dir,float radius,float half,float p,float[] tint,float alpha){
        Vec3 f=dir.multiply(1,0,1);f=f.lengthSqr()<1e-6?new Vec3(1,0,0):f.normalize();
        int m=Math.clamp((int)(half*2*radius/1.6F),8,56),n=Math.clamp((int)(radius/2),3,9);
        Vec3[][] grid=new Vec3[n+1][m+1];
        for(int j=0;j<=n;j++)for(int i=0;i<=m;i++){float ang=-half+2*half*i/m;double r=radius*j/(double)n;Vec3 v=f.yRot(ang).scale(r);grid[j][i]=ground(c,at.x+v.x,at.z+v.z,at.y);}
        if(c.soft())for(int j=0;j<n;j++){
            boolean lit=(j+1)/(float)n<=p+1e-4F;float fill=lit?.32F:.08F;
            for(int i=0;i<m;i++){c.tri(grid[j][i],grid[j+1][i],grid[j+1][i+1],tint,alpha*fill,alpha*fill,alpha*fill);c.tri(grid[j][i],grid[j+1][i+1],grid[j][i+1],tint,alpha*fill,alpha*fill,alpha*fill);}
        }
        if(!c.glow())return;
        Vec3[] rim=new Vec3[m+1],front=new Vec3[m+1];int k=Math.min(n,Math.round(p*n));
        for(int i=0;i<=m;i++){rim[i]=grid[n][i].add(0,.03,0);float ang=-half+2*half*i/m;Vec3 v=f.yRot(ang).scale(radius*p);front[i]=ground(c,at.x+v.x,at.z+v.z,at.y).add(0,.05,0);}
        c.ribbon(rim,.11F,tint,alpha,alpha);if(p>.02F)c.ribbon(front,.15F,PALE,alpha,alpha);
        if(half<PI-.01F){Vec3[] l=new Vec3[n+1],r=new Vec3[n+1];for(int j=0;j<=n;j++){l[j]=grid[j][0].add(0,.03,0);r[j]=grid[j][m].add(0,.03,0);}c.ribbon(l,.09F,tint,alpha*.9F,alpha*.9F);c.ribbon(r,.09F,tint,alpha*.9F,alpha*.9F);}
    }
    private static void ringOnGround(Canvas c,Vec3 at,float radius,float width,float[] tint,float alpha){
        Vec3[] rim=new Vec3[49];for(int i=0;i<rim.length;i++){float ang=i*TWO_PI/48;rim[i]=ground(c,at.x+cos(ang)*radius,at.z+sin(ang)*radius,at.y).add(0,.04,0);}
        c.ribbon(rim,width,tint,alpha,alpha);
    }
    /** A breath pool: a churning violet disc that fades as it runs out. */
    private static void pool(Canvas c,Active a,float t){
        int life=Math.max(1,a.seed());if(t>life)return;float alpha=Math.min(smooth(0,4,t),1-smooth(life-18,life,t)),r=a.scale()*(.7F+.3F*smooth(0,6,t));
        Vec3 at=a.origin();
        if(c.soft()){
            int m=28;Vec3 g=ground(c,at);Vec3[] rim=new Vec3[m+1];
            for(int i=0;i<=m;i++){float ang=i*TWO_PI/m;float wob=1+.08F*sin(ang*3+c.time()*.08F);rim[i]=ground(c,at.x+cos(ang)*r*wob,at.z+sin(ang)*r*wob,at.y).add(0,.02,0);}
            for(int i=0;i<m;i++)c.tri(g.add(0,.03,0),rim[i],rim[i+1],BREATH,alpha*.42F,alpha*.18F,alpha*.18F);
        }
        if(c.glow()){
            ringOnGround(c,at,r,.08F,BREATH,alpha*.85F);ringOnGround(c,at,r*(.4F+.15F*sin(c.time()*.12F)),.05F,PALE,alpha*.35F);
            for(int i=0;i<3;i++){float ang=i*TWO_PI/3+c.time()*.04F;Vec3 p=ground(c,at.x+cos(ang)*r*.5,at.z+sin(ang)*r*.5,at.y);c.line(p,p.add(0,.6+.4*sin(c.time()*.1F+i),0),.07F,BREATH,alpha*.5F,0);}
        }
    }

    private static void tube(Canvas c,Vec3 from,Vec3 to,float radius,float[] tint,float alpha,float progress){
        Vec3 dir=to.subtract(from).normalize(),side=perpendicular(dir),up=side.cross(dir).normalize();if(!c.glow())return;
        for(int i=0;i<4;i++){float angle=i*HALF_PI;Vec3 off=side.scale(cos(angle)*radius).add(up.scale(sin(angle)*radius));c.line(from.add(off),to.add(off),.016F,tint,alpha*.23F,alpha*.6F);}
        c.ring(to,dir,radius,.04F,-HALF_PI,-HALF_PI+TWO_PI*progress,PALE,alpha*.8F);arc(c,from,from.lerp(to,progress),.023F,PALE,alpha*.65F,cTime(c));
    }
    private static void channel(Canvas c,Active a,float alpha){
        if(!(a.bound(c) instanceof LivingEntity player))return;float[] tint=rgb(MimicColors.color(a.seed()&31));float progress=clamp((a.seed()>>>5)/(float)EndTuning.CHANNEL_TICKS,0,1);
        if(c.glow()){for(boolean right:new boolean[]{false,true}){Vec3 hand=PortalArt.hand(c,player,a.target(),right);arc(c,hand,a.target(),.035F+progress*.03F,tint,alpha,c.time());star(c,hand,.08F,tint,alpha,c.time());}c.ring(a.target(),UP,1.1F,.04F,-HALF_PI,-HALF_PI+TWO_PI*progress,GOLD,alpha);}
    }
    private static void strike(Canvas c,Active a,float t,float alpha){
        if(c.glow()){float[] tint=EndMoves.power(a.seed())<0?VIOLET:rgb(MimicColors.color(EndMoves.power(a.seed())));c.line(a.origin(),a.target(),.35F*alpha,tint,alpha*.65F,alpha*.85F);c.line(a.origin(),a.target(),.05F*alpha,PALE,alpha,alpha);}
    }
    private static void impact(Canvas c,Vec3 at,float radius,float[] tint,float t,float alpha){
        float r=radius*smooth(0,8,t);if(c.glow()){for(int i=0;i<3;i++)c.ring(at,i==0?UP:i==1?new Vec3(1,0,0):new Vec3(0,0,1),r,.07F,0,TWO_PI,tint,alpha);c.glow(at,Math.min(radius,1.7F),PALE,alpha*.5F);}
        if(c.soft()&&!minimal())for(int i=0;i<9;i++){float ang=i*TWO_PI/9;Vec3 p=at.add(cos(ang)*r,(hash(i,4)-.3)*r,sin(ang)*r);c.shard(p,p.add(0,.7*alpha,0),.15F*alpha,3,ang,tint,alpha*.5F);}
    }
    private static void wave(Canvas c,Vec3 at,float radius,float alpha){
        Vec3[] rim=new Vec3[97],top=new Vec3[97];for(int i=0;i<rim.length;i++){double ang=i*Math.PI*2/96,x=at.x+Math.cos(ang)*radius,z=at.z+Math.sin(ang)*radius;double y=EndGeometry.waveHeight(c.level,x,z,at.y);rim[i]=new Vec3(x,y,z);top[i]=rim[i].add(0,EndGeometry.WAVE_HEIGHT,0);}
        c.ribbon(rim,1.4F,VIOLET,alpha*.15F,alpha*.15F);c.ribbon(rim,.045F,PALE,alpha,alpha);c.ribbon(top,.018F,GOLD,alpha*.45F,alpha*.45F);
    }
    private static void absorption(Canvas c,Active a,float t){
        Vec3 chest=a.at(c).add(0,1.3,0),source=a.origin();float draw=smooth(10,100,t),finish=1-smooth(160,180,t);
        if(c.glow()){
            for(int i=0;i<7;i++){float ang=i*TWO_PI/7+t*.018F;Vec3 start=source.add(cos(ang)*(12-9*draw),sin(ang*.7F)*5,sin(ang)*(12-9*draw));Vec3[] curve=new Vec3[25];for(int n=0;n<25;n++){float k=n/24F;curve[n]=start.lerp(chest,k).add(sin(k*PI)*cos(ang)*5,sin(k*PI)*8,sin(k*PI)*sin(ang)*5);}c.ribbon(curve,.06F+.1F*draw,i%2==0?GOLD:VIOLET,finish*.7F,finish);}
            star(c,chest,.3F+draw*.7F,GOLD,finish,t*2);c.ring(source,UP,Math.max(.1F,12*(1-draw)),.07F,0,TWO_PI,VIOLET,finish*.5F);
        }
        wings(c,chest,a.bound(c)==null?180:a.bound(c).getYRot(),1,smooth(70,150,t)*finish,t);
        if(c.soft()&&t<120)for(int i=0;i<14;i++){float ang=i*TWO_PI/14;Vec3 p=source.add(cos(ang)*8*(1-draw),4*(1-draw),sin(ang)*8*(1-draw));c.shard(p,p.lerp(chest,.15).add(0,1,0),.35F*(1-draw),3,ang,PALE,.3F*finish);}
    }
    private static void release(Canvas c,Active a,float t){
        if(EndClientState.view().stage()==EndBattle.VICTORY)t=EndClientState.age(c.partial);
        EndRuptureArt.draw(c,a,t);
    }
    private static void gate(Canvas c,Vec3 at,float alpha,float time){
        Vec3[] rim=new Vec3[49];for(int i=0;i<rim.length;i++){float ang=i*TWO_PI/48;rim[i]=at.add(cos(ang)*1.35,sin(ang)*1.8,0);}
        if(c.soft())for(int i=0;i<48;i++)c.tri(at,rim[i],rim[i+1],DARK,alpha*.35F,alpha*.65F,alpha*.65F);
        if(c.glow()){c.ribbon(rim,.08F,CYAN,alpha*.8F,alpha*.8F);c.ribbon(rim,.018F,PALE,alpha,alpha);for(int i=0;i<5;i++){Vec3 p=at.add(cos(time*.03F+i)*.8,sin(time*.03F+i)*1.2,0);c.line(p,p.add(0,.18,0),.018F,GOLD,alpha*.35F,0);}}
    }
    private static void orb(Canvas c,Vec3 at,float radius,float[] tint,float alpha){if(c.glow())for(int i=0;i<3;i++)c.ring(at,i==0?UP:i==1?new Vec3(1,0,0):new Vec3(0,0,1),radius,.024F,0,TWO_PI,tint,alpha);}
    private static void star(Canvas c,Vec3 at,float radius,float[] tint,float alpha,float time){
        if(!c.glow())return;for(int i=0;i<3;i++){Vec3 d=i==0?new Vec3(radius,0,0):i==1?new Vec3(0,radius,0):new Vec3(0,0,radius);c.line(at.subtract(d),at.add(d),.025F,tint,0,alpha);}c.glow(at,radius*.45F,tint,alpha*.3F);
    }
    private static void arc(Canvas c,Vec3 from,Vec3 to,float width,float[] tint,float alpha,float time){
        Vec3 side=perpendicular(to.subtract(from));Vec3[] path=new Vec3[13];for(int i=0;i<13;i++){float k=i/12F;path[i]=from.lerp(to,k).add(side.scale(sin(k*PI)*sin(k*TWO_PI+time*.08F)*.18));}c.ribbon(path,width,tint,alpha*.7F,alpha);
    }
    private static Vec3 perpendicular(Vec3 direction){Vec3 side=direction.cross(UP).normalize();return side.lengthSqr()<.01?new Vec3(1,0,0):side;}
    private static float cTime(Canvas c){return c.time();}
    private static boolean minimal(){return Minecraft.getInstance().options.particles().get()==ParticleStatus.MINIMAL;}
    private EndBattleArt(){}
}
