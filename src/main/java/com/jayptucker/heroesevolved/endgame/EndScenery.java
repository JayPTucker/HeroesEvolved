package com.jayptucker.heroesevolved.endgame;

import com.jayptucker.heroesevolved.structure.Blueprint;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Small rooted ruins over the native island, never an island replacement. Every placed cell has a durable receipt. */
public final class EndScenery {
    private static final Set<Long> HELD=new HashSet<>();
    private EndScenery(){}
    public static void hold(ServerLevel level){
        for(int x=-8;x<=7;x++)for(int z=-8;z<=7;z++){
            long key=ChunkPos.asLong(x,z);if(!level.getForcedChunks().contains(key)){level.setChunkForced(x,z,true);HELD.add(key);}level.getChunk(x,z);
        }
    }
    public static void release(ServerLevel level){for(long key:HELD){ChunkPos at=new ChunkPos(key);level.setChunkForced(at.x,at.z,false);}HELD.clear();}
    public static int surface(ServerLevel level,int x,int z){
        if(!level.hasChunkAt(new BlockPos(x,64,z)))return 64;
        int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
        return y<48||y>100?64:y;
    }
    public static void build(ServerLevel level,EndData data){
        restore(level,data);data.floor=64;data.anchors.clear();
        for(Vec3 offset:EndGeometry.ALTAR_OFFSETS){
            int x=(int)offset.x,z=(int)offset.z,y=surface(level,x,z);
            BlockPos at=new BlockPos(x,y,z);place(level,data,"end_grounding_altar",at);
            data.anchors.add(Vec3.atCenterOf(at.above(3)));
        }
        Vec3 refuge=new Vec3(76.5,surface(level,76,0),.5);
        for(Vec3 offset:List.of(new Vec3(76,0,0),new Vec3(0,0,76),new Vec3(-76,0,0),new Vec3(0,0,-76))){
            BlockPos at=new BlockPos((int)offset.x,surface(level,(int)offset.x,(int)offset.z),(int)offset.z);
            if(place(level,data,"end_refuge",at)){refuge=Vec3.atBottomCenterOf(at);break;}
        }
        data.refuge=refuge;data.changed();
    }
    public static boolean place(ServerLevel level,EndData data,String name,BlockPos at){
        Blueprint bp=Blueprint.load(name);var states=bp.statesMatching(s->true);
        for(BlockPos offset:states.keySet()){
            BlockPos pos=at.offset(offset);if(!level.hasChunkAt(pos)||level.isOutsideBuildHeight(pos))return false;
            var old=level.getBlockState(pos);
            if(level.getBlockEntity(pos)!=null||!old.isAir()&&!old.is(Blocks.END_STONE))return false;
        }
        Map<BlockPos,net.minecraft.world.level.block.state.BlockState> before=new LinkedHashMap<>();
        for(BlockPos offset:states.keySet()){BlockPos pos=at.offset(offset);before.put(pos,level.getBlockState(pos));}
        bp.place(level,at,Rotation.NONE);
        before.forEach((pos,old)->{var after=level.getBlockState(pos);if(!after.equals(old))data.scenery.put(pos,new EndData.Change(old,after));});
        data.changed();return true;
    }
    public static void restore(ServerLevel level,EndData data){
        for(var entry:List.copyOf(data.scenery.entrySet())){
            BlockPos at=entry.getKey();if(!level.hasChunkAt(at))continue;
            if(level.getBlockState(at).equals(entry.getValue().after()))level.setBlock(at,entry.getValue().before(),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
            data.scenery.remove(at);
        }
        data.changed();
    }
    public static void phase(ServerLevel level,EndData data,int phase){
        for(var entry:List.copyOf(data.scenery.entrySet()))if(entry.getValue().after().is(Blocks.SEA_LANTERN)&&phase==3){
            var after=Blocks.SHROOMLIGHT.defaultBlockState();BlockPos at=entry.getKey();
            if(level.hasChunkAt(at)&&level.getBlockState(at).equals(entry.getValue().after())){
                level.setBlock(at,after,3);data.scenery.put(at,new EndData.Change(entry.getValue().before(),after));
            }
        }
        data.changed();
    }
}
