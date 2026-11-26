package com.jayptucker.heroesevolved.endgame;

import net.minecraft.world.phys.Vec3;
import java.util.List;

/** The End encounter's island and Sylar's Inheritance, shared with their art and tests (the Dragon's numbers: SealGeometry). */
public final class EndGeometry {
    public static final int MIN_LEVEL=16, ABSORPTION_TICKS=180, DEATH_TICKS=160, RUPTURE_TICK=72, CHECKPOINT=4;
    public static final double RELEASE_WAVE_SPEED=3.8;
    public static double releaseRadius(double age){return Math.max(0,age-RUPTURE_TICK)*RELEASE_WAVE_SPEED;}
    public static final double ARENA_RADIUS=120,FLIGHT_HEIGHT=96,FALLOFF_FULL=96,FALLOFF_NONE=160;
    /** Sylar's Inheritance (the Living Seal's own numbers are in SealGeometry). */
    public static final float SYLAR_HEALTH=800,HIT_CAP=.06F,SECOND_CAP=.04F;
    public static final float LANE_RADIUS=2.4F,TETHER_RADIUS=1.4F,IMPACT_RADIUS=5,BOLT_RADIUS=3.2F,
            SYLAR_CHARGE_RADIUS=2.1F,NOVA_RADIUS=240,SAFE_HALF_ANGLE=.4F,WAVE_HEIGHT=.8F;
    public static final int GROUND_TICKS=240,STAGGER_TICKS=60,TRANSITION_TICKS=40;
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
    /** Sylar's moves in his Inheritance (the Dragon's are SealGeometry.Move). */
    public enum Move {
        NEEDLE(28,40),STONE(36,50),REVERSAL(30,40),READ(40,45),PATTERN(36,48),WORLDLINE_STEP(36,70),EVENTIDE(40,60),ENDINGS(70,100);
        public final int windup,rest;
        Move(int windup,int rest){this.windup=windup;this.rest=rest;}
        public static Move byId(int id){return id<0||id>=values().length?null:values()[id];}
    }
    private EndGeometry(){}
}
