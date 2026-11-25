package com.jayptucker.heroesevolved.endgame;

import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.story.sylar.SylarVoice;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import java.util.*;
import java.util.function.BiConsumer;

/**
 * The Living Seal's body. It hunts in the air (vanilla steering around the party, then exact paths for every committed
 * move), lands to fight on foot, and is knocked out of the sky by the altars. EndBattle owns the fight; this owns how the
 * Dragon moves and what each of its own moves does. Every shape it marks is fixed when the windup starts.
 */
public final class DragonBrain {
    private static final Set<EndGeometry.Move> OWN=EnumSet.of(EndGeometry.Move.RIFT_CHARGE,EndGeometry.Move.BREATH,EndGeometry.Move.WINGBREAK,
            EndGeometry.Move.DESCENT,EndGeometry.Move.BITE,EndGeometry.Move.TAIL,EndGeometry.Move.SCORCH,EndGeometry.Move.ROAR,EndGeometry.Move.TAKEOFF,EndGeometry.Move.RUSH);
    private static final Set<EndGeometry.Move> COMMITTED=EnumSet.of(EndGeometry.Move.DESCENT,EndGeometry.Move.TAKEOFF,EndGeometry.Move.UNMAKING);
    private static final EndGeometry.Move[][] AIR={
            {EndGeometry.Move.RIFT_CHARGE,EndGeometry.Move.STARS,EndGeometry.Move.BREATH},
            {EndGeometry.Move.RIFT_CHARGE,EndGeometry.Move.BREATH,EndGeometry.Move.STARS},
            {EndGeometry.Move.BREATH,EndGeometry.Move.RIFT_CHARGE,EndGeometry.Move.STARS}};

    /** A stretch of exact flight: a cubic curve the body follows tick by tick. */
    static final class Leg {
        final Vec3 a,b,c,d;final int len;final boolean committed,face,accelerate;int age;
        BiConsumer<Vec3,Vec3> step;Runnable end;
        Leg(Vec3 a,Vec3 b,Vec3 c,Vec3 d,int len,boolean committed,boolean face,boolean accelerate){
            this.a=a;this.b=b;this.c=c;this.d=d;this.len=Math.max(1,len);this.committed=committed;this.face=face;this.accelerate=accelerate;
        }
        Vec3 at(double t){double u=1-t;return a.scale(u*u*u).add(b.scale(3*u*u*t)).add(c.scale(3*u*t*t)).add(d.scale(t*t*t));}
    }
    /** A lingering cloud of breath on the ground. */
    public static final class Pool {
        public final Vec3 at;public final int life;int age;
        Pool(Vec3 at,int life){this.at=at;this.life=life;}
    }

    final EndBattle.Encounter e;
    private final ArrayDeque<Leg> legs=new ArrayDeque<>();
    public final List<Pool> pools=new ArrayList<>();
    private final Set<UUID> struck=new HashSet<>();
    private final ArrayDeque<float[]> twists=new ArrayDeque<>();
    public int airMoves,groundMoves,cycle,quiet,picks,chain;
    public EndGeometry.Move last,forced;
    public Vec3 dir=new Vec3(1,0,0),spot=Vec3.ZERO,runA=Vec3.ZERO,runB=Vec3.ZERO;
    private double orbit;private int runPools;private boolean exhausted,toldLanded;
    /** Seal Rush: whom it charges, and where its lane ends (fixed when the windup starts). */
    private ServerPlayer rusher;private Vec3 laneEnd;

    DragonBrain(EndBattle.Encounter e){this.e=e;orbit=e.dragon==null?0:Math.atan2(e.dragon.getZ(),e.dragon.getX());}

    public static boolean owns(EndGeometry.Move move){return OWN.contains(move);}
    public static boolean interruptible(EndGeometry.Move move){return !COMMITTED.contains(move);}
    public boolean busy(){return !legs.isEmpty();}
    public boolean onGround(){return e.grounded>0;}
    private EnderDragon dragon(){return e.dragon;}
    private int phase(){return Math.clamp(e.phase,1,3);}
    private float dmg(float base){return base+phase()-1;}

    // ------------------------------------------------------------------ flight

    /** Where vanilla steering flies the body; null while an exact path or the ground holds it. */
    Vec3 flyTarget(){
        if(e.still||!legs.isEmpty()||e.grounded>0)return null;
        Vec3 center=EndGeometry.center(e.data.floor),focus=center;double top=e.data.floor;var fighters=e.fighters();
        if(!fighters.isEmpty()){
            Vec3 sum=Vec3.ZERO;for(ServerPlayer p:fighters){sum=sum.add(p.position());top=Math.max(top,p.getY());}
            focus=sum.scale(1D/fighters.size());
        }
        focus=island(focus);
        double height=Math.clamp(Math.max(e.data.floor+20,top+EndGeometry.ORBIT_LIFT),e.data.floor+20,e.data.floor+70);
        double lead=orbit+.7;
        return new Vec3(focus.x+Math.cos(lead)*EndGeometry.ORBIT_RADIUS,height,focus.z+Math.sin(lead)*EndGeometry.ORBIT_RADIUS);
    }

    void tick(){
        EnderDragon d=dragon();if(d==null||!d.isAlive())return;
        if(d.getPhaseManager().getCurrentPhase().getPhase()!=EnderDragonPhase.HOVERING)d.getPhaseManager().setPhase(EnderDragonPhase.HOVERING);
        if(!e.still)orbit+=EndGeometry.ORBIT_SPEED;
        Leg leg=legs.peekFirst();
        if(leg!=null){
            leg.age++;double t=Math.min(1,leg.age/(double)leg.len);if(leg.accelerate)t=t*t;
            Vec3 from=d.position(),to=leg.at(t);place(to,leg.face);
            if(leg.step!=null)leg.step.accept(from,to);
            if(leg.age>=leg.len&&legs.peekFirst()==leg){legs.removeFirst();if(leg.end!=null)leg.end.run();}
        }else if(e.grounded>0){
            d.setDeltaMovement(Vec3.ZERO);
            float[] twist=twists.peekFirst();
            if(twist!=null){turn(twist[0],Math.max(1,Math.abs(Mth.wrapDegrees(twist[0]-d.getYRot()))/Math.max(1,twist[1])));if(--twist[1]<=0)twists.removeFirst();}
            else if(e.casting==null&&!e.still&&e.stagger==0){ServerPlayer p=nearest(40);if(p!=null)turn(EndGeometry.yaw(p.position().subtract(d.position())),5);}
        }
        if(e.grounded>0){
            // Its time on the ground pauses while a ground move winds up or plays out, so none outlasts the landing.
            if(e.casting==null&&legs.isEmpty())e.grounded--;
            if(nearest(24)==null)quiet++;else quiet=0;
        }
        if(e.casting==EndGeometry.Move.SCORCH&&e.clock%3==0){Vec3 mouth=d.head.getBoundingBox().getCenter();e.level.sendParticles(ParticleTypes.DRAGON_BREATH,mouth.x,mouth.y,mouth.z,6,.4,.3,.4,.01);}
        tickPools();
        if(e.clock%3==0)aura(d);
        if(e.grounded==0&&e.clock%20==0&&!e.still){var feed=e.feed();if(feed!=null&&!feed.isRemoved()&&d.getHealth()<d.getMaxHealth())d.heal(d.getMaxHealth()*EndGeometry.FEED_HEAL);}
    }

    private void place(Vec3 to,boolean face){
        EnderDragon d=dragon();Vec3 delta=to.subtract(d.position());d.setPos(to);d.setDeltaMovement(delta);
        if(face&&delta.horizontalDistanceSqr()>.0025)turn(EndGeometry.yaw(delta),14);
    }
    private void turn(float yaw,float most){
        EnderDragon d=dragon();float now=d.getYRot(),step=Mth.clamp(Mth.wrapDegrees(yaw-now),-most,most);
        d.setYRot(now+step);d.setYHeadRot(now+step);d.yBodyRot=now+step;
    }
    /** A path from where the body is, leaving along its current heading, that arrives travelling along {@code arrive}. */
    private Leg toward(Vec3 target,Vec3 arrive,int len,boolean committed){
        EnderDragon d=dragon();Vec3 from=d.position(),v=d.getDeltaMovement();
        double reach=Math.min(18,from.distanceTo(target)/3);Vec3 out=v.lengthSqr()<.01?EndGeometry.forward(d.getYRot()):v.normalize();
        return new Leg(from,from.add(out.scale(reach)),target.subtract(arrive.scale(reach)),target,len,committed,true,false);
    }
    private void hold(Vec3 at,int len){legs.add(new Leg(at,at,at,at,len,false,false,false));}
    /** Clear queued flight the move no longer needs; committed paths (dives, drops, crashes, take-offs) always finish. */
    void cancel(){
        Leg leg=legs.peekFirst();if(leg!=null&&leg.committed)return;
        legs.clear();twists.clear();
    }
    void cancelAll(){legs.clear();twists.clear();}

    // ------------------------------------------------------------------ choosing

    /** The next move, or null to wait a moment (it keeps turning toward its target). */
    EndGeometry.Move next(){
        if(forced!=null){var move=forced;forced=null;return move;}
        var fighters=e.fighters();if(fighters.isEmpty())return null;
        if(e.grounded>0){
            if(e.grounded<=EndGeometry.Move.TAKEOFF.windup+4||quiet>=EndGeometry.IDLE_TAKEOFF)return EndGeometry.Move.TAKEOFF;
            EnderDragon d=dragon();Vec3 body=d.position(),f=EndGeometry.forward(d.getYRot());
            boolean behind=fighters.stream().anyMatch(p->near(p,body)&&EndGeometry.inSector(body,f.scale(-1),EndGeometry.TAIL_RADIUS,EndGeometry.TAIL_HALF,p.position()));
            boolean front=fighters.stream().anyMatch(p->near(p,body)&&EndGeometry.inSector(body,f,EndGeometry.BITE_REACH+1,.45,p.position()));
            boolean cone=fighters.stream().anyMatch(p->near(p,body)&&EndGeometry.inSector(body,f,EndGeometry.SCORCH_RADIUS,EndGeometry.SCORCH_HALF,p.position()));
            boolean close=fighters.stream().anyMatch(p->near(p,body)&&p.position().distanceTo(body)<10);
            if(phase()==3&&groundMoves>0&&groundMoves%4==3&&last!=EndGeometry.Move.ROAR)return EndGeometry.Move.ROAR;
            if(behind&&last!=EndGeometry.Move.TAIL)return EndGeometry.Move.TAIL;
            if(front&&last!=EndGeometry.Move.BITE)return EndGeometry.Move.BITE;
            if(cone&&phase()>=2&&last!=EndGeometry.Move.SCORCH)return EndGeometry.Move.SCORCH;
            if(close&&last!=EndGeometry.Move.WINGBREAK)return EndGeometry.Move.WINGBREAK;
            if(cone&&last!=EndGeometry.Move.SCORCH&&last!=EndGeometry.Move.BITE)return front?EndGeometry.Move.BITE:EndGeometry.Move.SCORCH;
            // Nobody in reach of jaws, tail or breath: backing off is not safe, it charges whoever it can reach.
            if(last!=EndGeometry.Move.RUSH){rusher=rushTarget(fighters);if(rusher!=null)return EndGeometry.Move.RUSH;}
            return null;
        }
        if(airMoves>=EndGeometry.AIR_MOVES[phase()-1])return EndGeometry.Move.DESCENT;
        if(phase()==3&&airMoves==0&&cycle%2==1&&last!=EndGeometry.Move.UNMAKING)return EndGeometry.Move.UNMAKING;
        if(phase()>=2&&last!=EndGeometry.Move.HARPOON&&fighters.stream().anyMatch(this::outOfReach))return EndGeometry.Move.HARPOON;
        var list=AIR[phase()-1];var move=list[(cycle+airMoves)%list.length];
        return move==last?list[(cycle+airMoves+1)%list.length]:move;
    }
    private boolean near(ServerPlayer p,Vec3 body){return Math.abs(p.getY()-body.y)<5;}
    private ServerPlayer rushTarget(List<ServerPlayer> fighters){
        ServerPlayer best=null;double most=Double.MAX_VALUE;Vec3 body=dragon().position().multiply(1,0,1);
        for(ServerPlayer p:fighters){double s=p.position().multiply(1,0,1).distanceToSqr(body);if(s<most&&lane(p)!=null){most=s;best=p;}}
        return best;
    }
    /**
     * Where a charge at this fighter would stop: past them along a straight lane that stays on solid island, climbs no
     * cliff and runs into no tower. Null when the fighter is too near or far, up in the air, or the lane can't reach them.
     */
    Vec3 lane(ServerPlayer p){
        EnderDragon d=dragon();Vec3 body=d.position(),flat=p.position().subtract(body).multiply(1,0,1);double far=flat.length();
        if(far<EndGeometry.RUSH_MIN||far>EndGeometry.RUSH_REACH||p.getY()>ground(p.getX(),p.getZ())+3||Math.abs(p.getY()-body.y)>8)return null;
        Vec3 heading=flat.normalize();double want=far+EndGeometry.RUSH_OVER,reached=0,last=body.y;
        for(double step=1;step<=want;step++){
            Vec3 at=body.add(heading.scale(step));if(!solid(at.x,at.z))break;
            double g=ground(at.x,at.z);if(Math.abs(g-last)>2.5||!EndMoves.seen(e.level,body.add(0,2,0),new Vec3(at.x,g+2,at.z)))break;
            last=g;reached=step;
        }
        if(reached<far)return null;Vec3 end=body.add(heading.scale(reached));return new Vec3(end.x,ground(end.x,end.z),end.z);
    }
    /** Real island under x, z: loaded, and neither void nor a tower top. */
    private boolean solid(double x,double z){
        BlockPos at=BlockPos.containing(x,64,z);if(!e.level.hasChunkAt(at))return false;
        int height=e.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,at.getX(),at.getZ());return height>e.level.getMinBuildHeight()&&height<=e.data.floor+12;
    }
    /** Someone standing on a tower or hanging far above the island, where the floor moves can't reach. */
    private boolean outOfReach(ServerPlayer p){return p.getY()>ground(p.getX(),p.getZ())+10||p.getY()>e.data.floor+24;}
    public int rest(EndGeometry.Move move){return (int)Math.round(move.rest*(phase()==1?1:phase()==2?.85:.7));}
    private ServerPlayer pick(){var fighters=e.fighters();return fighters.isEmpty()?null:fighters.get(Math.floorMod(picks++,fighters.size()));}
    private ServerPlayer nearest(double range){
        EnderDragon d=dragon();ServerPlayer best=null;double most=range*range;
        for(ServerPlayer p:e.fighters()){double s=p.distanceToSqr(d);if(s<most){most=s;best=p;}}return best;
    }

    // ------------------------------------------------------------------ geometry

    /** The standing height of the ground at x, z (the island's floor where it isn't loaded). */
    public double ground(double x,double z){return EndGeometry.waveHeight(e.level,x,z,e.data.floor+.1)-.1;}
    private Vec3 groundAt(Vec3 at){return new Vec3(at.x,ground(at.x,at.z),at.z);}
    private Vec3 island(Vec3 at){
        Vec3 center=EndGeometry.center(e.data.floor),flat=new Vec3(at.x-center.x,0,at.z-center.z);
        return flat.length()<=EndGeometry.ISLAND_RADIUS?at:new Vec3(center.x,at.y,center.z).add(flat.normalize().scale(EndGeometry.ISLAND_RADIUS));
    }
    /** The spot a move marks under a fighter: the ground, or the fighter where they fly well above it. */
    private Vec3 under(ServerPlayer p){double g=ground(p.getX(),p.getZ());return p.getY()>g+5?p.position():new Vec3(p.getX(),g,p.getZ());}
    private Vec3 approach(Vec3 at){
        Vec3 d=at.subtract(dragon().position()).multiply(1,0,1);
        return d.lengthSqr()<1?EndGeometry.forward(dragon().getYRot()):d.normalize();
    }
    /** Turn a lane until the whole flight along it is clear of the towers, so the body never passes through stone. */
    private Vec3 clear(Vec3 at,Vec3 heading,double run,double rise,double exit){
        for(double degrees:new double[]{0,35,-35,70,-70,110,-110,150}){
            Vec3 d=heading.yRot((float)Math.toRadians(degrees));
            Vec3 in=at.subtract(d.scale(run)).add(0,rise,0),out=at.add(d.scale(run)).add(0,exit,0),low=at.add(0,1.5,0);
            if(EndMoves.seen(e.level,in,low)&&EndMoves.seen(e.level,low,out))return d;
        }
        return heading;
    }

    // ------------------------------------------------------------------ warnings

    /** Mark the move and line the body up; true when nothing else needs to warn it. */
    boolean warn(EndGeometry.Move move){
        EnderDragon d=dragon();if(d==null)return true;e.marks.clear();struck.clear();
        switch(move){
            case RIFT_CHARGE -> {
                ServerPlayer p=pick();if(p==null)return true;spot=under(p);
                dir=clear(spot,approach(spot),EndGeometry.DIVE_RUN,EndGeometry.DIVE_HEIGHT,EndGeometry.DIVE_EXIT);
                Vec3 start=spot.subtract(dir.scale(EndGeometry.DIVE_RUN)).add(0,EndGeometry.DIVE_HEIGHT,0);
                legs.clear();legs.add(toward(start,dir,move.windup,false));
                e.marks.add(spot.add(0,.9,0));e.target=spot;e.castFrom=start;
                warning(move,spot.subtract(dir.scale(EndGeometry.DIVE_STRIP)),spot.add(dir.scale(EndGeometry.DIVE_STRIP)),1,0);
                sound(d.position(),SoundEvents.ENDER_DRAGON_GROWL,6,.75F);
            }
            case BREATH -> {
                ServerPlayer p=pick();if(p==null)return true;Vec3 mid=groundAt(p.position());
                dir=clear(mid,approach(mid),EndGeometry.RUN_HALF+EndGeometry.RUN_LEAD,EndGeometry.RUN_ALT,EndGeometry.RUN_ALT+4);
                runA=mid.subtract(dir.scale(EndGeometry.RUN_HALF));runB=mid.add(dir.scale(EndGeometry.RUN_HALF));
                for(int i=0;i<EndGeometry.RUN_POOLS;i++)e.marks.add(groundAt(runA.lerp(runB,i/(EndGeometry.RUN_POOLS-1D))).add(0,.9,0));
                Vec3 start=runA.subtract(dir.scale(EndGeometry.RUN_LEAD)).add(0,EndGeometry.RUN_ALT,0);
                legs.clear();legs.add(toward(start,dir,move.windup,false));e.target=mid;e.castFrom=start;
                warning(move,runA,runB,1,0);
                sound(d.position(),SoundEvents.ENDER_DRAGON_GROWL,6,1F);
            }
            case DESCENT -> {
                ServerPlayer p=pick();if(p==null)return true;spot=landing(p);dir=approach(spot);
                Vec3 over=spot.subtract(dir.scale(10)).add(0,14,0);
                legs.clear();legs.add(toward(over,dir,move.windup,true));
                e.marks.add(spot.add(0,.9,0));e.target=spot;e.castFrom=over;
                warning(move,spot,spot,EndGeometry.LAND_RADIUS,0);
                sound(d.position(),SoundEvents.ENDER_DRAGON_GROWL,6,.6F);
            }
            case BITE -> {
                Vec3 body=d.position();dir=EndGeometry.forward(d.getYRot());
                Vec3 a=body.add(dir.scale(EndGeometry.BITE_FROM)),b=body.add(dir.scale(EndGeometry.BITE_REACH));
                e.marks.add(b.add(0,.9,0));e.castFrom=a;e.target=b;spot=body;
                warning(move,a,b,1,0);twist(d.getYRot(),move.windup);
                sound(body,SoundEvents.ENDER_DRAGON_GROWL,3,1.5F);
            }
            case TAIL -> {
                Vec3 body=d.position();dir=EndGeometry.forward(d.getYRot()).scale(-1);spot=body;
                e.marks.add(body.add(dir.scale(EndGeometry.TAIL_RADIUS*.6)).add(0,.9,0));e.castFrom=body;e.target=body.add(dir.scale(EndGeometry.TAIL_RADIUS));
                warning(move,body,e.target,1,0);twist(d.getYRot()-24,move.windup);
                sound(body,SoundEvents.ENDER_DRAGON_FLAP,4,.55F);
            }
            case SCORCH -> {
                Vec3 body=d.position();dir=EndGeometry.forward(d.getYRot());spot=body;
                e.marks.add(body.add(dir.scale(EndGeometry.SCORCH_RADIUS*.6)).add(0,.9,0));e.castFrom=d.head.getBoundingBox().getCenter();e.target=body.add(dir.scale(EndGeometry.SCORCH_RADIUS));
                warning(move,body,e.target,1,0);twist(d.getYRot(),move.windup);
                sound(body,SoundEvents.ENDER_DRAGON_GROWL,5,1.1F);
            }
            case RUSH -> {
                // The lane is fixed now; the body swings round to face it over the windup, then charges.
                Vec3 body=d.position();ServerPlayer target=rusher!=null?rusher:rushTarget(e.fighters());laneEnd=target==null?null:lane(target);rusher=null;if(laneEnd==null)return true;
                dir=laneEnd.subtract(body).multiply(1,0,1).normalize();spot=body;e.castFrom=body.add(dir.scale(2));e.target=laneEnd;e.marks.add(laneEnd.add(0,.9,0));
                warning(move,e.castFrom,laneEnd,1,0);twist(EndGeometry.yaw(dir),Math.max(1,move.windup-8));
                sound(body,SoundEvents.ENDER_DRAGON_GROWL,6,.8F);sound(body,SoundEvents.RAVAGER_ROAR,4,.6F);
            }
            case ROAR,TAKEOFF -> {
                Vec3 body=d.position();spot=body;e.castFrom=body;e.target=body;e.marks.add(body.add(0,.9,0));
                warning(move,body,body,move==EndGeometry.Move.ROAR?EndGeometry.ROAR_RADIUS:EndGeometry.TAKEOFF_RADIUS,0);twist(d.getYRot(),move.windup);
                sound(body,move==EndGeometry.Move.ROAR?SoundEvents.ENDER_DRAGON_AMBIENT:SoundEvents.ENDER_DRAGON_FLAP,5,move==EndGeometry.Move.ROAR?.5F:.8F);
            }
            case WINGBREAK -> {
                Vec3 body=d.position();spot=body;e.castFrom=new Vec3(body.x,e.data.floor+.1,body.z);e.target=e.castFrom;e.marks.add(body.add(0,.9,0));
                warning(move,e.castFrom,e.castFrom,1,0);twist(d.getYRot(),move.windup);
                sound(body,SoundEvents.ENDER_DRAGON_FLAP,5,.65F);
            }
            case STARS -> {
                // From the second phase every fighter gets company: more shards around them, each its own ring.
                int extra=phase()-1;
                for(ServerPlayer p:e.fighters())for(int i=0;i<extra;i++){double angle=e.clock*.37+i*Math.PI;e.marks.add(p.position().add(Math.cos(angle)*5.5,.9,Math.sin(angle)*5.5));}
                sound(d.position(),SoundEvents.ENDER_DRAGON_GROWL,5,1.25F);return false;
            }
            case UNMAKING -> {
                Vec3 high=EndGeometry.center(e.data.floor).add(0,38,0);legs.clear();legs.add(toward(high,approach(high),30,true));hold(high,move.windup);
                e.castFrom=high.add(0,2,0);sound(d.position(),SoundEvents.ENDER_DRAGON_GROWL,8,.45F);return false;
            }
            default -> {return false;}
        }
        return true;
    }
    private void warning(EndGeometry.Move move,Vec3 origin,Vec3 target,float scale,int role){
        SetPiecePayload.sendSeeded(e.level,Piece.END_BATTLE_WARNING,origin,target,scale,dragon(),0,EndMoves.seed(move,-1,role));
    }
    private void twist(float yaw,int ticks){twists.clear();twists.add(new float[]{yaw,ticks});}
    /** It lands on the fighter it picked, pulled back onto the island and onto its surface. */
    private Vec3 landing(ServerPlayer p){Vec3 at=island(p.position());return groundAt(at);}

    // ------------------------------------------------------------------ landing the moves

    /** True when this class did the whole move. */
    boolean land(EndGeometry.Move move){
        EnderDragon d=dragon();if(d==null||!owns(move))return false;
        switch(move){
            case RIFT_CHARGE -> dive();
            case BREATH -> run();
            case DESCENT -> {
                Vec3 from=d.position();Leg drop=new Leg(from,from.add(dir.scale(4)),spot.add(0,6,0),spot,EndGeometry.LAND_DROP,true,false,false);
                drop.end=this::touchdown;legs.clear();legs.add(drop);
            }
            case BITE -> {
                Vec3 body=d.position();Vec3 a=e.castFrom,b=e.target;
                for(ServerPlayer p:e.fighters())if(EndGeometry.stripDistance(a,b,p.position())<EndGeometry.BITE_RADIUS&&p.getY()>body.y-1.5&&p.getY()<body.y+4)
                    if(EndMoves.hit(e,p,dmg(9),-1))push(p,dir.scale(1.1).add(0,.4,0));
                Leg lunge=new Leg(body,body,body.add(dir.scale(3)),body.add(dir.scale(3)),3,true,false,false),back=new Leg(body.add(dir.scale(3)),body.add(dir.scale(3)),body,body,6,true,false,false);
                legs.add(lunge);legs.add(back);flash(move,a,b,1);
                sound(body,SoundEvents.EVOKER_FANGS_ATTACK,3,.5F);sound(body,SoundEvents.ENDER_DRAGON_HURT,3,1.4F);
                CameraShakePayload.sendAround(e.level,body,40,.12F,8);
            }
            case TAIL -> {
                Vec3 body=spot;
                for(ServerPlayer p:e.fighters())if(near(p,body)&&EndGeometry.inSector(body,dir,EndGeometry.TAIL_RADIUS,EndGeometry.TAIL_HALF,p.position()))
                    if(EndMoves.hit(e,p,dmg(8),-1))push(p,p.position().subtract(body).multiply(1,0,1).normalize().scale(1.6).add(0,.5,0));
                // The whip swings past where it faced when the windup began, then settles back there.
                twists.clear();float yaw=EndGeometry.yaw(dir.scale(-1));twists.add(new float[]{yaw+70,4});twists.add(new float[]{yaw,10});
                flash(move,body,e.target,1);sound(body,SoundEvents.PLAYER_ATTACK_SWEEP,4,.45F);sound(body,SoundEvents.ENDER_DRAGON_FLAP,4,1.3F);
                CameraShakePayload.sendAround(e.level,body,48,.18F,10);
            }
            case SCORCH -> {
                Vec3 body=spot,mouth=d.head.getBoundingBox().getCenter();
                for(ServerPlayer p:e.fighters())if(Math.abs(p.getY()-body.y)<6&&EndGeometry.inSector(body,dir,EndGeometry.SCORCH_RADIUS,EndGeometry.SCORCH_HALF,p.position()))
                    EndMoves.hit(e,p,dmg(7),-1,d.damageSources().dragonBreath());
                for(double r:new double[]{6.5,12})for(int s=-1;s<=1;s+=2)pool(groundAt(body.add(dir.yRot((float)(s*EndGeometry.SCORCH_HALF*.5)).scale(r))),false);
                for(int i=0;i<48;i++){Vec3 v=dir.yRot((float)((e.level.random.nextFloat()*2-1)*EndGeometry.SCORCH_HALF)).add(0,-.25-e.level.random.nextFloat()*.2,0).normalize();
                    e.level.sendParticles(ParticleTypes.DRAGON_BREATH,mouth.x,mouth.y,mouth.z,0,v.x,v.y,v.z,.55+e.level.random.nextFloat()*.4);}
                flash(move,body,e.target,1);sound(body,SoundEvents.ENDER_DRAGON_SHOOT,5,.7F);
            }
            case ROAR -> {
                Vec3 body=spot;
                for(ServerPlayer p:e.fighters())if(Math.abs(p.getY()-body.y)<8&&p.position().multiply(1,0,1).distanceTo(body.multiply(1,0,1))<EndGeometry.ROAR_RADIUS)
                    if(EndMoves.hit(e,p,dmg(4),-1)){push(p,away(p,body).scale(1.8).add(0,.55,0));p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,40,1));}
                flash(move,body,body,EndGeometry.ROAR_RADIUS);sound(body,SoundEvents.ENDER_DRAGON_GROWL,8,.5F);
                CameraShakePayload.sendAround(e.level,body,120,.4F,24);
            }
            case TAKEOFF -> {
                Vec3 body=spot;
                for(ServerPlayer p:e.fighters())if(Math.abs(p.getY()-body.y)<5&&p.position().multiply(1,0,1).distanceTo(body.multiply(1,0,1))<EndGeometry.TAKEOFF_RADIUS)
                    if(EndMoves.hit(e,p,dmg(5),-1))push(p,away(p,body).scale(1.4).add(0,.7,0));
                flash(move,body,body,EndGeometry.TAKEOFF_RADIUS);
                e.grounded=0;quiet=0;cycle++;airMoves=0;groundMoves=0;exhausted=false;
                Vec3 f=EndGeometry.forward(d.getYRot());legs.add(new Leg(body,body.add(0,8,0),body.add(f.scale(6)).add(0,18,0),body.add(f.scale(16)).add(0,24,0),24,true,true,false));
                sound(body,SoundEvents.ENDER_DRAGON_FLAP,6,.6F);CameraShakePayload.sendAround(e.level,body,60,.2F,12);
            }
            case RUSH -> {
                if(laneEnd==null)return true;rush(laneEnd);laneEnd=null;
            }
            case WINGBREAK -> {
                e.waves.add(new EndMoves.Wave(e.castFrom,0,dmg(7),-1));
                sound(d.position(),SoundEvents.ENDER_DRAGON_FLAP,6,.5F);CameraShakePayload.sendAround(e.level,d.position(),60,.2F,12);
            }
            default -> {return false;}
        }
        return true;
    }
    /** Bookkeeping after any of the Dragon's moves lands, its own or a shared one. */
    void landed(EndGeometry.Move move){
        last=move;
        if(e.grounded>0&&move!=EndGeometry.Move.TAKEOFF)groundMoves++;
        else if(move!=EndGeometry.Move.DESCENT&&move!=EndGeometry.Move.TAKEOFF)airMoves++;
        if(move==EndGeometry.Move.STARS)sound(dragon().position(),SoundEvents.ENDER_DRAGON_SHOOT,6,.8F);
        if(move==EndGeometry.Move.UNMAKING){exhausted=true;forced=EndGeometry.Move.DESCENT;}
    }

    private void dive(){
        EnderDragon d=dragon();Vec3 from=d.position(),low=spot.add(0,1.5,0),out=spot.add(dir.scale(EndGeometry.DIVE_RUN)).add(0,EndGeometry.DIVE_EXIT,0);
        Vec3 a=spot.subtract(dir.scale(EndGeometry.DIVE_STRIP)),b=spot.add(dir.scale(EndGeometry.DIVE_STRIP));
        Leg in=new Leg(from,from.add(dir.scale(12)).add(0,-3,0),low.subtract(dir.scale(10)).add(0,1,0),low,EndGeometry.DIVE_APPROACH,true,true,false);
        Leg up=new Leg(low,low.add(dir.scale(10)),out.subtract(dir.scale(10)).add(0,-4,0),out,EndGeometry.DIVE_PULLOUT,true,true,false);
        BiConsumer<Vec3,Vec3> sweep=(prev,now)->{
            for(ServerPlayer p:e.fighters()){
                if(struck.contains(p.getUUID()))continue;Vec3 body=p.position().add(0,.9,0);
                if(EndGeometry.segmentDistance(prev.add(0,1.5,0),now.add(0,1.5,0),body)<EndGeometry.DIVE_RADIUS&&EndGeometry.stripDistance(a,b,body)<EndGeometry.DIVE_RADIUS+.5){
                    struck.add(p.getUUID());if(EndMoves.hit(e,p,dmg(10),-1))push(p,dir.scale(1.2).add(0,.6,0));
                }
            }
            SetPiecePayload.send(e.level,Piece.END_BATTLE_DASH,prev.add(0,2,0),now.add(0,2,0),5,d);
        };
        in.step=sweep;up.step=sweep;
        in.end=()->{
            for(ServerPlayer p:e.fighters())if(!struck.contains(p.getUUID())&&p.position().multiply(1,0,1).distanceTo(spot.multiply(1,0,1))<EndGeometry.DIVE_IMPACT&&Math.abs(p.getY()-spot.y)<4){
                struck.add(p.getUUID());if(EndMoves.hit(e,p,dmg(9),-1))push(p,away(p,spot).scale(1.3).add(0,.55,0));
            }
            SetPiecePayload.sendSeeded(e.level,Piece.END_BATTLE_IMPACT,spot.add(0,.2,0),spot,EndGeometry.DIVE_IMPACT,null,0,0);
            flash(EndGeometry.Move.RIFT_CHARGE,a,b,1);
            CameraShakePayload.sendAround(e.level,spot,120,.42F,18);sound(spot,SoundEvents.GENERIC_EXPLODE.value(),4,.6F);
        };
        up.end=()->{if(phase()==3&&chain==0){chain=1;forced=EndGeometry.Move.RIFT_CHARGE;e.holdOff(12);}else chain=0;};
        legs.clear();legs.add(in);legs.add(up);
        sound(from,SoundEvents.ENDER_DRAGON_FLAP,6,1.3F);
    }
    /** Seal Rush: straight down its lane along the ground, trampling whoever is in it aside, and skidding to a stop. */
    private void rush(Vec3 end){
        EnderDragon d=dragon();Vec3 from=d.position(),a=e.castFrom;Vec3 side=new Vec3(-dir.z,0,dir.x);
        Leg charge=new Leg(from,from.lerp(end,1/3D),from.lerp(end,2/3D),end,EndGeometry.RUSH_TICKS,true,false,false);
        ParticleOptions dust=new BlockParticleOption(ParticleTypes.BLOCK,Blocks.END_STONE.defaultBlockState());
        charge.step=(prev,now)->{
            double g=ground(now.x,now.z);d.setPos(now.x,g,now.z);
            for(ServerPlayer p:e.fighters()){
                if(struck.contains(p.getUUID())||Math.abs(p.getY()-g)>3)continue;
                if(EndGeometry.segmentDistance(prev.multiply(1,0,1),now.multiply(1,0,1),p.position().multiply(1,0,1))<EndGeometry.RUSH_RADIUS){
                    struck.add(p.getUUID());double s=Math.signum(p.position().subtract(now).dot(side));
                    if(EndMoves.hit(e,p,dmg(8),-1))push(p,side.scale((s==0?1:s)*1.5).add(dir.scale(.5)).add(0,.6,0));
                }
            }
            burst(dust,now.x,g+.3,now.z,14,1.6,.2,1.6,.15);burst(ParticleTypes.CLOUD,now.x,g+.5,now.z,8,1.5,.25,1.5,.08);
            SetPiecePayload.send(e.level,Piece.END_BATTLE_DASH,prev.add(0,1.2,0),now.add(0,1.2,0),3,d);
        };
        charge.end=()->{
            for(ServerPlayer p:e.fighters())if(!struck.contains(p.getUUID())&&p.position().multiply(1,0,1).distanceTo(end.multiply(1,0,1))<EndGeometry.RUSH_RADIUS+1.5&&Math.abs(p.getY()-end.y)<3)
                if(EndMoves.hit(e,p,dmg(6),-1))push(p,away(p,end).scale(1.2).add(dir.scale(.6)).add(0,.5,0));
            flash(EndGeometry.Move.RUSH,a,end,1);burst(dust,end.x,end.y+.3,end.z,40,2.4,.3,2.4,.25);burst(ParticleTypes.EXPLOSION,end.x,end.y+.8,end.z,3,1.5,.3,1.5,0);
            CameraShakePayload.sendAround(e.level,end,70,.32F,12);sound(end,SoundEvents.GENERIC_EXPLODE.value(),3,.75F);
        };
        legs.clear();legs.add(charge);
        sound(from,SoundEvents.ENDER_DRAGON_GROWL,6,1.15F);CameraShakePayload.sendAround(e.level,from,50,.15F,10);
    }
    private void run(){
        EnderDragon d=dragon();Vec3 from=d.position(),end=runB.add(dir.scale(EndGeometry.RUN_LEAD)).add(0,EndGeometry.RUN_ALT+4,0);
        Leg pass=new Leg(from,from.add(dir.scale(15)),end.subtract(dir.scale(15)).add(0,-4,0),end,EndGeometry.RUN_TICKS,true,true,false);
        runPools=0;Vec3 length=runB.subtract(runA).multiply(1,0,1);double total=length.length();
        pass.step=(prev,now)->{
            Vec3 mouth=d.head.getBoundingBox().getCenter();
            double along=mouth.subtract(runA).multiply(1,0,1).dot(length)/Math.max(1e-6,total*total);
            while(runPools<Math.min(EndGeometry.RUN_POOLS,e.marks.size())&&along>=runPools/(EndGeometry.RUN_POOLS-1D)){pool(e.marks.get(runPools).add(0,-.9,0),true);runPools++;}
            if(along>-.15&&along<1.1)e.level.sendParticles(ParticleTypes.DRAGON_BREATH,mouth.x,mouth.y-1,mouth.z,16,.7,1.4,.7,.02);
        };
        pass.end=()->{while(runPools<Math.min(EndGeometry.RUN_POOLS,e.marks.size())){pool(e.marks.get(runPools).add(0,-.9,0),true);runPools++;}};
        legs.clear();legs.add(pass);sound(from,SoundEvents.ENDER_DRAGON_SHOOT,6,.6F);
    }
    private void touchdown(){
        EnderDragon d=dragon();Vec3 at=d.position();d.setDeltaMovement(Vec3.ZERO);
        for(ServerPlayer p:e.fighters())if(p.position().multiply(1,0,1).distanceTo(at.multiply(1,0,1))<EndGeometry.LAND_RADIUS&&Math.abs(p.getY()-at.y)<4)
            if(EndMoves.hit(e,p,dmg(9),-1))push(p,away(p,at).scale(1.3).add(0,.5,0));
        e.grounded=EndGeometry.LANDED_TICKS[phase()-1]+(exhausted?40:0);airMoves=0;groundMoves=0;quiet=0;last=EndGeometry.Move.DESCENT;
        SetPiecePayload.sendSeeded(e.level,Piece.END_BATTLE_IMPACT,at.add(0,.2,0),at,EndGeometry.LAND_RADIUS,null,0,0);
        flash(EndGeometry.Move.DESCENT,at,at,EndGeometry.LAND_RADIUS);
        CameraShakePayload.sendAround(e.level,at,160,.55F,22);sound(at,SoundEvents.GENERIC_EXPLODE.value(),5,.5F);sound(at,SoundEvents.ENDER_DRAGON_GROWL,8,.7F);
        if(!toldLanded){toldLanded=true;for(ServerPlayer p:e.party.values())SylarVoice.narrate(p,"endgame.heroesevolved.landed");}
    }
    /** Knocked out of the sky: it falls where it is and lies stunned. On the ground already, it simply reels. */
    void crash(){
        EnderDragon d=dragon();if(d==null)return;
        Leg leg=legs.peekFirst();if(e.grounded>0&&(leg==null||!leg.face))return;
        legs.clear();twists.clear();Vec3 from=d.position(),land=groundAt(island(from.add(d.getDeltaMovement().scale(8))));
        Leg fall=new Leg(from,from.add(d.getDeltaMovement().scale(6)),land.add(0,10,0),land,22,true,false,true);
        fall.end=()->{CameraShakePayload.sendAround(e.level,land,160,.5F,20);sound(land,SoundEvents.GENERIC_EXPLODE.value(),5,.45F);
            SetPiecePayload.sendSeeded(e.level,Piece.END_BATTLE_IMPACT,land.add(0,.2,0),land,EndGeometry.LAND_RADIUS,null,0,0);};
        legs.add(fall);airMoves=0;groundMoves=0;quiet=0;
    }
    /** A phase change: everyone near is thrown back while it roars; in the air it hangs there for a moment. */
    void phaseChange(){
        EnderDragon d=dragon();if(d==null)return;Vec3 at=d.position();
        for(ServerPlayer p:e.fighters())if(p.distanceTo(d)<22)push(p,away(p,at).scale(1.5).add(0,.5,0));
        Vec3 floor=groundAt(at);flash(EndGeometry.Move.ROAR,floor,floor,22);
        SetPiecePayload.sendSeeded(e.level,Piece.END_BATTLE_IMPACT,floor.add(0,.2,0),floor,12,null,0,0);
        sound(at,SoundEvents.ENDER_DRAGON_GROWL,10,.55F);
        if(e.grounded>0)e.grounded=Math.max(e.grounded,100);else if(legs.isEmpty())hold(at,EndGeometry.TRANSITION_TICKS);
        airMoves=Math.min(airMoves,1);
    }

    /**
     * What each phase does to its body, seen from anywhere on the island: torn space streams off its wings in the second
     * phase; in the third it is a dying star, shedding embers and red sparks from its wings, spine and jaws.
     */
    private void aura(EnderDragon d){
        int phase=phase();if(phase<2)return;
        for(var part:d.getSubEntities()){
            Vec3 at=part.getBoundingBox().getCenter();boolean wing="wing".equals(part.name);
            if(phase==2){
                if(wing){burst(ParticleTypes.REVERSE_PORTAL,at.x,at.y,at.z,8,1.8,.3,1.8,.05);burst(ParticleTypes.END_ROD,at.x,at.y,at.z,2,1.8,.3,1.8,.02);}
                else if("body".equals(part.name))burst(ParticleTypes.PORTAL,at.x,at.y+1,at.z,4,1.2,.6,1.2,.3);
            }else{
                burst(EMBER,at.x,at.y,at.z,wing?6:3,wing?1.8:.7,.4,wing?1.8:.7,0);
                if(wing||part==d.head)burst(ParticleTypes.FLAME,at.x,at.y-.3,at.z,wing?4:2,wing?1.6:.4,.2,wing?1.6:.4,.02);
                if("body".equals(part.name)&&e.clock%9==0)burst(ParticleTypes.LAVA,at.x,at.y+.8,at.z,1,.8,.3,.8,0);
            }
        }
    }
    /** Particles every fighter sees from anywhere on the island (ordinary ones vanish 32 blocks from the camera). */
    private void burst(ParticleOptions particle,double x,double y,double z,int count,double dx,double dy,double dz,double speed){
        for(ServerPlayer viewer:e.party.values())
            if(viewer.level()==e.level&&viewer.distanceToSqr(x,y,z)<140*140)e.level.sendParticles(viewer,particle,true,x,y,z,count,dx,dy,dz,speed);
    }
    private static final DustParticleOptions EMBER=new DustParticleOptions(new Vector3f(1F,.35F,.43F),3F);

    // ------------------------------------------------------------------ breath pools

    private void pool(Vec3 at,boolean strike){
        int life=phase()==3?EndGeometry.POOL_TICKS_LAST:EndGeometry.POOL_TICKS;pools.add(new Pool(at,life));
        if(strike)for(ServerPlayer p:e.fighters())if(inPool(p,at))EndMoves.hit(e,p,dmg(6),-1,dragon().damageSources().dragonBreath());
        SetPiecePayload.sendSeeded(e.level,Piece.END_BATTLE_POOL,at,at,EndGeometry.POOL_RADIUS,null,0,life);
    }
    private static boolean inPool(ServerPlayer p,Vec3 at){
        return p.position().multiply(1,0,1).distanceTo(at.multiply(1,0,1))<EndGeometry.POOL_RADIUS+.3&&p.getY()>at.y-1&&p.getY()<at.y+2.5;
    }
    private void tickPools(){
        for(var it=pools.iterator();it.hasNext();){
            Pool pool=it.next();if(++pool.age>=pool.life){it.remove();continue;}
            if(pool.age%10==0)for(ServerPlayer p:e.fighters())if(inPool(p,pool.at))EndMoves.hit(e,p,phase()==3?3:2,-1,dragon().damageSources().dragonBreath());
            if(pool.age%4==0)e.level.sendParticles(ParticleTypes.DRAGON_BREATH,pool.at.x,pool.at.y+.25,pool.at.z,4,EndGeometry.POOL_RADIUS*.45,.05,EndGeometry.POOL_RADIUS*.45,.004);
        }
    }
    void clearPools(){pools.clear();}

    // ------------------------------------------------------------------ helpers

    private void flash(EndGeometry.Move move,Vec3 origin,Vec3 target,float scale){warning(move,origin,target,scale,1);}
    private static Vec3 away(ServerPlayer p,Vec3 from){Vec3 d=p.position().subtract(from).multiply(1,0,1);return d.lengthSqr()<.01?new Vec3(1,0,0):d.normalize();}
    private static void push(ServerPlayer p,Vec3 v){p.setDeltaMovement(p.getDeltaMovement().scale(.3).add(v));p.hurtMarked=true;}
    private void sound(Vec3 at,SoundEvent sound,float volume,float pitch){e.level.playSound(null,at.x,at.y,at.z,sound,SoundSource.HOSTILE,volume,pitch);}
}
