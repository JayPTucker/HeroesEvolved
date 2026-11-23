package com.jayptucker.heroesevolved.endgame;

import com.jayptucker.heroesevolved.ability.*;
import com.jayptucker.heroesevolved.ability.service.*;
import com.jayptucker.heroesevolved.energy.*;
import com.jayptucker.heroesevolved.mimicry.MimicColors;
import com.jayptucker.heroesevolved.network.*;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Contextual held input, shared charge and fractional stamina. Receipts last until key-up. */
public final class EndChannel {
    public static final String DRAIN="end_grounding";
    private record Held(ServerPlayer player,ResourceLocation power,int altar,String input,Vec3 at){}
    private static final Map<UUID,Held> HELD=new HashMap<>();
    private static final Map<UUID,Set<String>> INPUTS=new HashMap<>();
    private static final Map<UUID,Long> HINTS=new HashMap<>();
    private EndChannel(){}
    public static boolean active(ServerPlayer p){return HELD.containsKey(p.getUUID());}
    public static AbilityUseResult press(ServerPlayer p,String input){
        if(active(p)){INPUTS.computeIfAbsent(p.getUUID(),x->new HashSet<>()).add(input);return AbilityUseResult.SUCCESS;}
        var battle=EndBattle.current();if(battle==null||p.level()!=battle.level)return null;
        int index=-1;for(int i=0;i<battle.data.anchors.size();i++)if(near(p,battle.data.anchors.get(i))){index=i;break;}
        if(index<0)return null;
        INPUTS.computeIfAbsent(p.getUUID(),x->new HashSet<>()).add(input);
        var power=PlayerAbilityService.getData(p).assignedPower();
        if(!EndBattle.eligible(p)||power.isEmpty()||!PowerRules.canAct(p)||!battle.canChannel(index))return refused(p,"endgame.heroesevolved.channel.unavailable");
        if(PlayerEnergyService.getStamina(p)+1e-6<EndTuning.CHANNEL_DRAIN*EndTuning.CHANNEL_TICKS/20){PlayerEnergyService.refused(p);return refused(p,"endgame.heroesevolved.channel.stamina");}
        HELD.put(p.getUUID(),new Held(p,power.get().getKey(),index,input,p.position()));
        StaminaDrain.start(p,DRAIN,0,true,()->cancel(p));pulse(p,index,power.get().getKey(),battle);return AbilityUseResult.SUCCESS;
    }
    private static boolean near(ServerPlayer p,Vec3 focus){
        Vec3 d=focus.subtract(p.getEyePosition());return d.lengthSqr()<36&&Math.abs(p.getY()-(focus.y-3.5))<2.5&&p.getLookAngle().dot(d.normalize())>.75;
    }
    private static AbilityUseResult refused(ServerPlayer p,String key){
        long now=p.level().getGameTime();if(now-HINTS.getOrDefault(p.getUUID(),-1000L)>60){HINTS.put(p.getUUID(),now);p.sendSystemMessage(Component.translatable(key));}return AbilityUseResult.CANNOT_USE;
    }
    public static boolean release(ServerPlayer p,String input){
        var keys=INPUTS.get(p.getUUID());boolean owned=keys!=null&&keys.remove(input);if(keys!=null&&keys.isEmpty())INPUTS.remove(p.getUUID());
        Held held=HELD.get(p.getUUID());if(held!=null&&held.input.equals(input))cancel(p);return owned;
    }
    public static void cancel(ServerPlayer p){
        Held held=HELD.remove(p.getUUID());if(held==null)return;StaminaDrain.stop(p,DRAIN);ArmPosePayload.clearIf(p,ArmPose.PORTAL_CHANNEL);PlayerEnergyService.sendNow(p);
    }
    public static void cancelAll(){for(Held held:List.copyOf(HELD.values()))cancel(held.player);}
    public static void clear(){cancelAll();INPUTS.clear();HINTS.clear();}
    public static void tick(){
        var battle=EndBattle.current();if(battle==null){cancelAll();return;}boolean[] paid=new boolean[4];
        for(Held held:List.copyOf(HELD.values())){
            if(HELD.get(held.player.getUUID())!=held)continue;
            ServerPlayer p=held.player;
            if(p.level()!=battle.level||!EndBattle.eligible(p)||!PowerRules.canAct(p)||!battle.canChannel(held.altar)
                    ||p.position().distanceToSqr(held.at)>.49||!near(p,battle.data.anchors.get(held.altar))
                    ||!PlayerAbilityService.getData(p).assignedPower().map(e->e.getKey().equals(held.power)).orElse(false)){cancel(p);continue;}
            if(!PlayerEnergyService.drain(p,EndTuning.CHANNEL_DRAIN/20)){PlayerEnergyService.refused(p);cancel(p);continue;}
            battle.charge[held.altar]++;paid[held.altar]=true;if(battle.clock%5==0)pulse(p,held.altar,held.power,battle);
            if(battle.charge[held.altar]>=EndTuning.CHANNEL_TICKS){for(Held other:List.copyOf(HELD.values()))if(other.altar==held.altar)cancel(other.player);battle.overloaded(held.altar,p);}
        }
        for(int i=0;i<4;i++)if(!paid[i])battle.charge[i]=Math.max(0,battle.charge[i]-1);
    }
    private static void pulse(ServerPlayer p,int index,ResourceLocation power,EndBattle.Encounter battle){
        ArmPosePayload.show(p,ArmPose.PORTAL_CHANNEL,16);int seed=MimicColors.index(power.getPath())|((int)battle.charge[index]<<5);
        SetPiecePayload.sendSeeded(battle.level,Piece.END_BATTLE_CHANNEL,p.position(),battle.data.anchors.get(index),1,p,0,seed);
    }
}
