package com.jayptucker.heroesevolved.wither;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** The cage, warning shapes and server impacts share one geometry. */
public final class WitherGeometry {
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "wither_cage"));
    public static final BlockPos ORIGIN = new BlockPos(0, 64, 0);
    public static final int INTRO_TICKS = 120, DEATH_TICKS = 60, MINIMUM_LEVEL = 16;
    public static final double RADIUS = 38, FLIGHT_HEIGHT = 46, PULSE_SPEED = .75;
    public static final float ARENA_SCALE=2.8F,CORE_HEIGHT=1.375F*ARENA_SCALE,HEIGHT=3*ARENA_SCALE;
    public static final int LUNGE_TICKS=16,SLAM_TICKS=16,FEED_TICKS=140,STANCE_HITS=3;
    public static final int HEAD_WINDUP=16;
    public static final float WAVE_HEIGHT=.8F,BITE_REACH=8,BITE_HALF_ANGLE=.45F,RAKE_REACH=13,RAKE_HALF_ANGLE=1F;
    public static final float VERDICT_RADIUS=1.35F,TETHER_RADIUS=1.1F,LUNGE_RADIUS=3,LUNGE_REACH=30,SLAM_RADIUS=6,
            SKULL_RADIUS=2.3F,RAIN_HEIGHT=9,RAIN_SPREAD=4;
    public static final List<BlockPos> ANCHORS = List.of(ORIGIN.offset(-24, 6, 8), ORIGIN.offset(24, 6, 8), ORIGIN.offset(0, 6, -27));
    public static Vec3 floor() { return Vec3.atBottomCenterOf(ORIGIN.above(3)); }
    public static Vec3 center() { return floor().add(0, 7, 0); }
    public static Vec3 entrance() { return floor().add(7, 0, -31); }
    public static Vec3 exit() { return floor().add(0, 1.6, -38); }
    public enum Move {
        VERDICT(28,14), RAIN(30,10), PULSE(28,10), TETHER(22,12), LUNGE(26,12), SLAM(32,18), HORIZON(64,22),
        BITE(20,10), RAKE(30,14);
        public final int windup, rest;
        Move(int windup, int rest) { this.windup=windup; this.rest=rest; }
    }
    public static Vec3 core(Vec3 feet){return feet.add(0,CORE_HEIGHT,0);}
    public static Vec3 head(Vec3 feet,float yaw,int index){
        Vec3 side=Vec3.directionFromRotation(0,yaw+90);double x=index==0?0:index==1?-14:14;
        return feet.add(side.scale(x/16*ARENA_SCALE)).add(0,(index==0?43:35)/16D*ARENA_SCALE,0);
    }
    public static Vec3 mouth(Vec3 feet,float yaw,int index,Vec3 aim){Vec3 head=head(feet,yaw,index);return head.add(aim.subtract(head).normalize().scale(.92));}
    public static double angle(Vec3 from,Vec3 to,float yaw){Vec3 d=to.subtract(from);return Math.abs(Math.atan2(Math.sin(Math.atan2(-d.x,d.z)-yaw*Math.PI/180),Math.cos(Math.atan2(-d.x,d.z)-yaw*Math.PI/180)));}
    public static boolean inArc(Vec3 from,Vec3 to,float yaw,double reach,double halfAngle){return to.multiply(1,0,1).distanceToSqr(from.multiply(1,0,1))<=reach*reach&&angle(from,to,yaw)<=halfAngle;}
    public static Move[] chain(int phase,int turn,boolean far){
        if(phase==1)return switch(Math.floorMod(turn,3)){
            case 0->far?new Move[]{Move.LUNGE,Move.VERDICT}:new Move[]{Move.BITE,Move.VERDICT};
            case 1->new Move[]{Move.RAIN,Move.LUNGE};default->new Move[]{Move.TETHER,Move.PULSE};};
        if(phase==2)return switch(Math.floorMod(turn,3)){
            case 0->new Move[]{Move.LUNGE,Move.RAKE,Move.PULSE};case 1->new Move[]{Move.SLAM,Move.VERDICT};default->new Move[]{Move.RAIN,Move.TETHER,Move.BITE};};
        return switch(Math.floorMod(turn,3)){
            case 0->new Move[]{Move.HORIZON,Move.LUNGE};case 1->new Move[]{Move.RAIN,Move.RAKE,Move.VERDICT};default->new Move[]{Move.SLAM,Move.PULSE,Move.BITE};};
    }
    public static double lineDistance(Vec3 a, Vec3 b, Vec3 point) {
        Vec3 d=b.subtract(a);
        double t=Math.clamp(point.subtract(a).dot(d)/Math.max(1e-7,d.lengthSqr()),0,1);
        return point.distanceTo(a.add(d.scale(t)));
    }
    private WitherGeometry() {}
}
