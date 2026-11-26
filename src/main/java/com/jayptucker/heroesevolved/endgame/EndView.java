package com.jayptucker.heroesevolved.endgame;

import net.minecraft.world.phys.Vec3;
import java.util.List;

/** A small read-only snapshot; clients never infer combat geometry from particles or their current aim. */
public record EndView(int stage,int phase,int clock,int stageAge,int boss,int dragon,int altar,int broken,
                      int move,int windup,int grounded,int sampled,float charge,float safeAngle,float floor,
                      Vec3 origin,Vec3 target,Vec3 refuge,Vec3 feed,List<Vec3> anchors,List<Vec3> marks,SealView seal) {
    public static final EndView EMPTY=new EndView(0,1,0,0,-1,-1,-1,0,-1,0,0,-1,0,0,64,
            Vec3.ZERO,Vec3.ZERO,Vec3.ZERO,Vec3.ZERO,List.of(),List.of(),SealView.EMPTY);
    public boolean dragonFight(){return stage==1||stage==2;}
    public boolean inheritance(){return stage>=3&&stage<=5;}
    public boolean active(){return stage>=1&&stage<=5;}
    public Vec3 center(){return EndGeometry.center(floor);}
}
