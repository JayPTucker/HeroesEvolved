package com.jayptucker.heroesevolved.telepathy;
import com.jayptucker.heroesevolved.network.*;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Private hallucinations. No entities, inventories or terrain are created on the server. */
public final class IllusionService {
    private record Pending(ServerPlayer victim,long until){}
    private record Vision(int id,ServerPlayer caster,ServerPlayer viewer,int kind,Vec3 from,Vec3 to,long start,int ticks) {
        Vec3 position(long now){return kind<3?from.lerp(to,Math.min(1,(now-start)/(double)ticks)):from;}
        AABB bounds(long now){Vec3 p=position(now);return kind==4?new AABB(p.x-1.5,p.y,p.z-1.5,p.x+1.5,p.y+3,p.z+1.5):new AABB(p.x-.45,p.y,p.z-.45,p.x+.45,p.y+(kind<3?1.9:1),p.z+.45);}
    }
    private static final Map<UUID,Pending> PENDING=new HashMap<>();
    private static final Map<Integer,Vision> VISIONS=new HashMap<>();
    private static int next=1;
    public static boolean open(ServerPlayer p){
        LivingEntity t=TelepathyService.mindInSight(p,24);
        if(!(t instanceof ServerPlayer v)||!eligible(p,v)) {p.displayClientMessage(Component.literal("Choose a player's mind to weave a private illusion."),true);return false;}
        PENDING.put(p.getUUID(),new Pending(v,p.serverLevel().getGameTime()+200));
        PacketDistributor.sendToPlayer(p,new MindStatePayload(6,v.getId(),0,0,0,0,0,0));return true;
    }
    private static boolean eligible(ServerPlayer p,ServerPlayer v){return p!=v && v.isAlive() && !v.isCreative() && !v.isSpectator() && p.server.isPvpAllowed() && p.canHarmPlayer(v) && p.level()==v.level() && p.distanceToSqr(v)<48*48 && PowerRules.canAct(p);}
    public static void choose(ServerPlayer p,int kind){
        Pending pending=PENDING.remove(p.getUUID());if(pending==null||pending.until<p.serverLevel().getGameTime()||kind<0||kind>4||!eligible(p,pending.victim))return;
        ServerPlayer v=pending.victim;
        Vec3 ahead=v.getLookAngle().multiply(1,0,1).normalize();
        Vec3 start=v.position().add(ahead.scale(7));
        // Ground placement is bounded and must be visible to the affected mind.
        var hit=v.serverLevel().clip(new net.minecraft.world.level.ClipContext(start.add(0,4,0),start.add(0,-6,0),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,v));
        if(hit.getType()!=HitResult.Type.MISS)start=new Vec3(start.x,hit.getLocation().y,start.z);
        if(create(p,v,kind,start,v.position(),160))TelepathySkills.wove(p,v);
    }
    public static boolean create(ServerPlayer p,ServerPlayer v,int kind,Vec3 from,Vec3 to,int ticks){
        if(!eligible(p,v)||kind<0||kind>4)return false;
        for(Vision old:List.copyOf(VISIONS.values()))if(old.viewer==v)remove(old);
        Vision vision=new Vision(next++,p,v,kind,from,to,p.serverLevel().getGameTime(),Math.clamp(ticks,20,240));
        VISIONS.put(vision.id,vision);
        PacketDistributor.sendToPlayer(v,new IllusionPayload(vision.id,kind,p.getId(),from,to,vision.ticks));
        TelepathyService.whisper(v,.75f);TelepathyFocus.strain(p,8);return true;
    }
    public static void dissolve(ServerPlayer viewer,int id){
        Vision v=VISIONS.get(id);if(v==null||v.viewer!=viewer)return;
        AABB bounds=v.bounds(viewer.serverLevel().getGameTime()).inflate(.35);
        if(bounds.contains(viewer.getEyePosition()) || bounds.clip(viewer.getEyePosition(),viewer.getEyePosition().add(viewer.getLookAngle().scale(6))).isPresent()){TelepathySkills.fright(v.caster,viewer,v.position(viewer.serverLevel().getGameTime()));remove(v);}
    }
    private static void remove(Vision v){VISIONS.remove(v.id);PacketDistributor.sendToPlayer(v.viewer,new IllusionPayload(v.id,-1,0,v.position(v.viewer.serverLevel().getGameTime()),Vec3.ZERO,0));}
    public static void tick(MinecraftServer server){
        long now=server.overworld().getGameTime();PENDING.entrySet().removeIf(e->e.getValue().until<now);
        for(Vision v:List.copyOf(VISIONS.values())){
            boolean touched=v.bounds(now).intersects(v.viewer.getBoundingBox());
            if(now-v.start>=v.ticks||!eligible(v.caster,v.viewer)||v.viewer.hasDisconnected()||v.caster.hasDisconnected()||touched){
                // Waking Fright: walking into the illusion frightens the mind that made it real.
                if(touched&&!v.viewer.hasDisconnected())TelepathySkills.fright(v.caster,v.viewer,v.position(now));
                remove(v);
            }
        }
    }
    public static void forget(ServerPlayer p){PENDING.remove(p.getUUID());for(Vision v:List.copyOf(VISIONS.values()))if(v.caster==p||v.viewer==p)remove(v);}
    public static void clear(){for(Vision v:List.copyOf(VISIONS.values()))remove(v);PENDING.clear();}
    private IllusionService(){}
}
