package com.jayptucker.heroesevolved.telepathy;

import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.network.MindStatePayload;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Bounded, expiring mind threads; no arbitrary private player data is recorded. */
public final class TelepathyFocus {
    private static final Map<UUID,Mind> MINDS=new HashMap<>();
    private static final class Thread { final LivingEntity target; int focus; long until; Thread(LivingEntity t){target=t;} }
    private static final class Mind { ServerPlayer owner; final LinkedHashMap<UUID,Thread> threads=new LinkedHashMap<>(); LivingEntity reading; int tier, strain; long since; }
    public static boolean start(ServerPlayer p,int tier) {
        LivingEntity target=TelepathyService.mindInSight(p,24); if(target==null){TelepathyService.readMind(p,tier);return false;} if(PossessionService.occupied(p))return false;
        Mind m=MINDS.computeIfAbsent(p.getUUID(),k->new Mind()); m.owner=p; m.reading=target;m.tier=tier;m.since=p.serverLevel().getGameTime();
        TelepathyService.readMind(p,tier); return true;
    }
    public static boolean reading(ServerPlayer p){Mind m=MINDS.get(p.getUUID());return m!=null&&m.reading!=null;}
    public static void stop(ServerPlayer p){Mind m=MINDS.get(p.getUUID());if(m!=null)m.reading=null;com.jayptucker.heroesevolved.network.ArmPosePayload.clear(p);}
    public static int focus(ServerPlayer p,LivingEntity t){Mind m=MINDS.get(p.getUUID());Thread f=m==null?null:m.threads.get(t.getUUID());return f==null?0:f.focus;}
    public static int strain(ServerPlayer p){Mind m=MINDS.get(p.getUUID());return m==null?0:m.strain;}
    public static List<LivingEntity> targets(ServerPlayer p){Mind m=MINDS.get(p.getUUID());return m==null?List.of():m.threads.values().stream().filter(t->t.focus>=30 && t.target.isAlive() && t.target.level()==p.level()).map(t->t.target).toList();}
    public static void strain(ServerPlayer p,int amount){Mind m=MINDS.computeIfAbsent(p.getUUID(),k->new Mind());m.owner=p;m.strain=Math.clamp(m.strain+amount,0,100);}
    /** Deepens (or starts) a thread to a mind without reading it, up to {@code cap} Focus: listening, persuading, a storm. */
    public static void deepen(ServerPlayer p,LivingEntity t,int amount,int cap){
        if(t==null||t==p||!t.isAlive()||t.level()!=p.level())return;
        Mind m=MINDS.computeIfAbsent(p.getUUID(),k->new Mind());m.owner=p;
        Thread f=m.threads.computeIfAbsent(t.getUUID(),k->new Thread(t));
        if(f.focus<cap)f.focus=Math.min(cap,f.focus+amount);f.until=Math.max(f.until,p.serverLevel().getGameTime()+600);
        while(m.threads.size()>4)m.threads.remove(m.threads.keySet().iterator().next());
    }
    /** Every mind threaded to at least {@code min} Focus, wherever it is in the player's level. */
    public static List<LivingEntity> threads(ServerPlayer p,int min){Mind m=MINDS.get(p.getUUID());return m==null?List.of():m.threads.values().stream().filter(t->t.focus>=min && t.target.isAlive() && t.target.level()==p.level()).map(t->t.target).toList();}
    public static void forget(ServerPlayer p){MINDS.remove(p.getUUID());PacketDistributor.sendToPlayer(p,new MindStatePayload(0,-1,0,0,0,0,0,0));}
    public static void tick(MinecraftServer server){
        for(var e:List.copyOf(MINDS.entrySet())) {
            ServerPlayer p=e.getValue().owner;if(p==null){MINDS.remove(e.getKey());continue;}
            Mind m=e.getValue();long now=p.serverLevel().getGameTime();
            m.threads.values().removeIf(t->!t.target.isAlive() || t.target.level()!=p.level() || now>t.until);
            if(m.reading!=null) {
                LivingEntity t=m.reading;
                if(!PowerRules.canAct(p)||!t.isAlive()||t.level()!=p.level()||p.distanceToSqr(t)>48*48||!p.hasLineOfSight(t)||now-m.since>120) m.reading=null;
                else {
                    Thread f=m.threads.computeIfAbsent(t.getUUID(),k->new Thread(t));f.focus=Math.min(100,f.focus+1+(m.tier>=4?1:0));f.until=now+600;
                    while(m.threads.size()>4)m.threads.remove(m.threads.keySet().iterator().next());
                    if(now%6==0)com.jayptucker.heroesevolved.network.ArmPosePayload.show(p,com.jayptucker.heroesevolved.ability.ArmPose.TELE_READ,8);
                    if(now%6==0)AbilityEffects.send(p.serverLevel(),Kind.MIND_LINK,p.getEyePosition(),t.getEyePosition(),.4+f.focus/100.0);
                }
            }
            if(now%20==0) {
                // Shared Burden: linked friends carry half of it.
                boolean shared=TelepathySkills.sharesBurden(p);
                if(PossessionService.occupied(p))m.strain=Math.min(100,m.strain+(shared?2:4));else m.strain=Math.max(0,m.strain-(shared?6:3));
                if(m.strain>=100){PossessionService.end(p,"Too many voices. Your concentration breaks.");TelepathyService.overload(p);m.strain=60;m.reading=null;}
                if(!PossessionService.occupied(p))PacketDistributor.sendToPlayer(p,new MindStatePayload(0,-1,0,0,0,0,m.reading==null?0:focus(p,m.reading),m.strain));
            }
        }
    }
    private TelepathyFocus(){}
}
