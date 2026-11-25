package com.jayptucker.heroesevolved.endgame;

import net.minecraft.world.phys.Vec3;
import java.util.List;

/** Shared by native Dragon control, the Inheritance fight, warning art and tests. */
public final class EndGeometry {
    public static final int MIN_LEVEL=16, ABSORPTION_TICKS=180, DEATH_TICKS=160, RUPTURE_TICK=72, CHECKPOINT=4;
    public static final double RELEASE_WAVE_SPEED=3.8;
    public static double releaseRadius(double age){return Math.max(0,age-RUPTURE_TICK)*RELEASE_WAVE_SPEED;}
    public static final double ARENA_RADIUS=120,FLIGHT_HEIGHT=96,FALLOFF_FULL=96,FALLOFF_NONE=160;
    public static final float DRAGON_HEALTH=900,SYLAR_HEALTH=800,HIT_CAP=.06F,SECOND_CAP=.04F;
    public static final float LANE_RADIUS=2.4F,TETHER_RADIUS=1.4F,IMPACT_RADIUS=5,BOLT_RADIUS=3.2F,
            CHARGE_RADIUS=6,SYLAR_CHARGE_RADIUS=2.1F,NOVA_RADIUS=240,SAFE_HALF_ANGLE=.4F,WAVE_HEIGHT=.8F;
    public static final int GROUND_TICKS=240,STAGGER_TICKS=60,TRANSITION_TICKS=40;
    // The Living Seal's body. It hunts in the air, then comes down to fight on foot; both halves share these numbers
    // with the warnings drawn on the ground, so every fill completes exactly as its hit lands.
    /** Damage the Dragon takes in the air, on the ground, and through its head (the weak point) or the rest of its body. */
    public static final float AIR_DAMAGE=.6F,HEAD_DAMAGE=1.2F,BODY_DAMAGE=.75F,FEED_HEAL=.004F;
    /** How many air moves before it lands, by phase; how long it stays down, by phase; a crash's stun inside GROUND_TICKS. */
    public static final int[] AIR_MOVES={2,3,3},LANDED_TICKS={220,200,180};
    public static final int CRASH_STUN=150,IDLE_TAKEOFF=80,GROUND_REST=18;
    public static final double ORBIT_RADIUS=38,ORBIT_SPEED=.016,ORBIT_LIFT=14,ISLAND_RADIUS=64;
    /** Riftdive: it climbs behind the marked spot, dives through it along the marked strip and pulls out beyond. */
    public static final double DIVE_RUN=34,DIVE_HEIGHT=24,DIVE_EXIT=16,DIVE_STRIP=11;
    public static final float DIVE_RADIUS=4.5F,DIVE_IMPACT=6;
    public static final int DIVE_APPROACH=16,DIVE_PULLOUT=18;
    /** Severing Breath: a low run along the marked strip, leaving breath pools behind it. */
    public static final double RUN_HALF=22,RUN_ALT=8,RUN_LEAD=12;
    public static final float RUN_RADIUS=3,POOL_RADIUS=2.8F;
    public static final int RUN_TICKS=34,RUN_POOLS=9,POOL_TICKS=110,POOL_TICKS_LAST=170;
    /** Descent: it glides over the marked circle and slams down into it. */
    public static final float LAND_RADIUS=7;
    public static final int LAND_DROP=10;
    /** Its ground moves, all measured from the body: the bite's lane ahead, the tail's sector behind, the scorch's cone. */
    public static final double BITE_FROM=3,BITE_REACH=15;
    public static final float BITE_RADIUS=2.6F,TAIL_RADIUS=14,TAIL_HALF=1.22F,SCORCH_RADIUS=16,SCORCH_HALF=.56F,
            ROAR_RADIUS=16,TAKEOFF_RADIUS=9;
    /** Seal Rush: on the ground it charges down a marked lane at someone too far for its jaws, and skids to a stop past them. */
    public static final double RUSH_MIN=12,RUSH_REACH=30,RUSH_OVER=5;
    public static final float RUSH_RADIUS=3.2F;
    public static final int RUSH_TICKS=14;
    /** How long after its windup a move's hit lands; the warning fills over windup plus this. */
    public static int arrival(Move move){return move.windup+(move==Move.RIFT_CHARGE?DIVE_APPROACH:move==Move.DESCENT?LAND_DROP:0);}
    /** The Dragon's forward direction for a yaw, as vanilla flies it. */
    public static Vec3 forward(float yaw){double r=Math.toRadians(yaw);return new Vec3(Math.sin(r),0,-Math.cos(r));}
    public static float yaw(Vec3 direction){return (float)Math.toDegrees(Math.atan2(direction.x,-direction.z));}
    /** Inside a flat sector on the ground: within {@code radius} of {@code center} and {@code half} radians of {@code direction}. */
    public static boolean inSector(Vec3 center,Vec3 direction,double radius,double half,Vec3 point){
        Vec3 d=point.subtract(center).multiply(1,0,1);if(d.lengthSqr()>radius*radius)return false;if(d.lengthSqr()<1)return true;
        Vec3 f=direction.multiply(1,0,1).normalize();return Math.acos(Math.clamp(d.normalize().dot(f),-1,1))<=half;
    }
    /** Horizontal distance from a point to a ground strip's centre line. */
    public static double stripDistance(Vec3 a,Vec3 b,Vec3 p){return segmentDistance(a.multiply(1,0,1),b.multiply(1,0,1),p.multiply(1,0,1));}
    public static final int CHARGE_TICKS=24,ECHO_GAP=10;
    public static final double CHARGE_REACH=72,SAFE_ORBIT_RADIUS=3.5,SAFE_ORBIT_OFFSET=7;
    public static final double WAVE_SPEED=1.35;
    public static final List<Vec3> ALTAR_OFFSETS=List.of(new Vec3(30,0,0),new Vec3(0,0,30),new Vec3(-30,0,0),new Vec3(0,0,-30));
    public static Vec3 center(double floor){return new Vec3(.5,floor,.5);}
    public static double waveHeight(net.minecraft.world.level.Level level,double x,double z,double fallback){
        var at=new net.minecraft.core.BlockPos((int)Math.floor(x),64,(int)Math.floor(z));if(!level.hasChunkAt(at))return fallback;
        int height=level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,at.getX(),at.getZ());
        return height<40||height>100?fallback:height+.1;
    }
    public static double segmentDistance(Vec3 a,Vec3 b,Vec3 p){
        Vec3 d=b.subtract(a);double t=Math.clamp(p.subtract(a).dot(d)/Math.max(1e-8,d.lengthSqr()),0,1);
        return p.distanceTo(a.add(d.scale(t)));
    }
    public static double angleDistance(double a,double b){return Math.abs(Math.atan2(Math.sin(a-b),Math.cos(a-b)));}
    public static boolean safeSector(Vec3 center,Vec3 point,float angle){
        Vec3 delta=point.subtract(center);return angleDistance(Math.atan2(delta.z,delta.x),angle)<SAFE_HALF_ANGLE;
    }
    public enum Move {
        // The Dragon's: BREATH is the Severing Breath run, RIFT_CHARGE the Riftdive, WINGBREAK its grounded wing buffet.
        BREATH(36,30),STARS(32,40),WINGBREAK(24,GROUND_REST),HARPOON(28,36),RIFT_CHARGE(34,24),BLOOM(36,54),UNMAKING(70,40),
        NEEDLE(28,40),STONE(36,50),REVERSAL(30,40),READ(40,45),PATTERN(36,48),WORLDLINE_STEP(36,70),EVENTIDE(40,60),ENDINGS(70,100),
        // Appended so earlier network seeds keep their ids.
        DESCENT(32,14),BITE(14,GROUND_REST),TAIL(22,GROUND_REST),SCORCH(28,GROUND_REST+6),ROAR(30,GROUND_REST+6),TAKEOFF(16,30),RUSH(26,GROUND_REST+8);
        public final int windup,rest;
        Move(int windup,int rest){this.windup=windup;this.rest=rest;}
        public static Move byId(int id){return id<0||id>=values().length?null:values()[id];}
    }
    private EndGeometry(){}
}
