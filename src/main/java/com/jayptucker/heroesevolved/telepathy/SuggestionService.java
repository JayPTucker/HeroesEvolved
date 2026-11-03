package com.jayptucker.heroesevolved.telepathy;
import com.jayptucker.heroesevolved.network.*;
import com.jayptucker.heroesevolved.ability.service.PowerRules;
import com.jayptucker.heroesevolved.effects.AbilityEffects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
public final class SuggestionService {
    private record Pending(Mob mob,long until){}
    private record Order(ServerPlayer owner,Mob mob,int command,Vec3 point,long until){}
    private static final Map<UUID,Pending> PENDING=new HashMap<>();
    private static final Map<UUID,Order> ORDERS=new HashMap<>();
    public static boolean open(ServerPlayer p){
        LivingEntity t=TelepathyService.mindInSight(p,24);if(!(t instanceof Mob m))return false;
        PENDING.put(p.getUUID(),new Pending(m,p.serverLevel().getGameTime()+200));
        PacketDistributor.sendToPlayer(p,new MindStatePayload(5,m.getId(),0,0,0,0,0,0));return true;
    }
    public static void choose(ServerPlayer p,int command){
        Pending pending=PENDING.remove(p.getUUID());long now=p.serverLevel().getGameTime();
        if(pending==null||pending.until<now||command<0||command>4||!PowerRules.canAct(p))return;
        Mob m=pending.mob;if(!m.isAlive()||m.level()!=p.level()||m.distanceToSqr(p)>48*48||!p.hasLineOfSight(m))return;
        if(command==4&&TelepathyFocus.focus(p,m)<60){p.displayClientMessage(Component.literal("Defend Me needs a deeper mind thread: 60 Focus."),true);return;}
        Vec3 point=command==0?p.pick(20,0,false).getLocation():command==2?m.position().add(m.position().subtract(p.position()).normalize().scale(12)):p.position();
        ORDERS.put(m.getUUID(),new Order(p,m,command,point,now+200));m.setTarget(null);m.getNavigation().stop();TelepathyFocus.strain(p,5);
    }
    // Clearing a target must remain possible: cancelling a null target preserves the old attacker.
    public static boolean forbidden(Mob m,LivingEntity target){if(target==null)return false;Order o=ORDERS.get(m.getUUID());return o!=null&&(o.command!=4||target==o.owner||target instanceof net.minecraft.world.entity.player.Player);}
    public static void tick(MinecraftServer server){
        long now=server.overworld().getGameTime();PENDING.entrySet().removeIf(e->e.getValue().until<now);
        for(Order o:List.copyOf(ORDERS.values())){
            if(!o.mob.isAlive()||!o.owner.isAlive()||o.owner.hasDisconnected()||o.mob.level()!=o.owner.level()||now>=o.until||!PowerRules.canAct(o.owner)){ORDERS.remove(o.mob.getUUID());o.mob.getNavigation().stop();continue;}
            if(now%5!=0)continue;
            if(o.command==4){LivingEntity threat=o.mob.level().getEntitiesOfClass(Mob.class,o.owner.getBoundingBox().inflate(16),m->m!=o.mob&&m.getTarget()==o.owner).stream().findFirst().orElse(null);o.mob.setTarget(threat);}
            else {o.mob.setTarget(null);if(o.command!=3){Vec3 pt=o.command==1?o.owner.position():o.point;o.mob.getNavigation().moveTo(pt.x,pt.y,pt.z,1.1);o.mob.getLookControl().setLookAt(pt.x,pt.y+1,pt.z);}}
            if(now%10==0)AbilityEffects.send(o.owner.serverLevel(),AbilityEffectPayload.Kind.MIND_LINK,o.mob.getEyePosition(),o.command==1?o.owner.getEyePosition():o.point.add(0,1,0),.5);
        }
    }
    public static void forget(ServerPlayer p){PENDING.remove(p.getUUID());ORDERS.values().removeIf(o->{if(o.owner==p){o.mob.getNavigation().stop();return true;}return false;});}
    private SuggestionService(){}
}
