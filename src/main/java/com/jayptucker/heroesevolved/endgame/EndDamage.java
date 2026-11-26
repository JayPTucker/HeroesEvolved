package com.jayptucker.heroesevolved.endgame;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import java.util.ArrayDeque;

/** Effective health can scale past vanilla's attribute ceiling, without changing any global attribute. */
public final class EndDamage {
    private final ArrayDeque<float[]> recent=new ArrayDeque<>();
    private float effective;
    public void scale(LivingEntity boss,float base,int players){
        effective=base*(float)Math.min(3,1+.5*Math.max(0,players-1));
        float fraction=boss.getHealth()/boss.getMaxHealth();
        boss.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(Math.min(1000,effective));
        boss.setHealth(boss.getMaxHealth()*fraction);
    }
    /** Sets the effective health outright (it may pass vanilla's 1000 attribute ceiling), keeping the share left. */
    public void scaleTo(LivingEntity boss,float value){
        effective=value;float fraction=boss.getHealth()/boss.getMaxHealth();
        boss.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(Math.min(1000,effective));
        boss.setHealth(boss.getMaxHealth()*Math.min(1,fraction));
    }
    public float effective(){return effective;}
    public float units(LivingEntity boss){return effective/Math.max(1,boss.getMaxHealth());}
    public float amount(LivingEntity boss,DamageSource source,float incoming,int clock,float bonus,boolean bypass){
        return amount(boss,source,incoming,clock,bonus,bypass,EndGeometry.HIT_CAP,EndGeometry.SECOND_CAP);
    }
    /** A blow after falloff, its multiplier and the caps: no hit above {@code hitCap} of its health, no second above {@code secondCap}. */
    public float amount(LivingEntity boss,DamageSource source,float incoming,int clock,float bonus,boolean bypass,float hitCap,float secondCap){
        if(!Float.isFinite(incoming)||incoming<=0)return 0;
        float amount=incoming/Math.max(1,units(boss));
        if(!bypass){
            if(source.getEntity() instanceof Player player){
                double distance=player.distanceTo(boss);
                amount*=Math.clamp((EndGeometry.FALLOFF_NONE-distance)/(EndGeometry.FALLOFF_NONE-EndGeometry.FALLOFF_FULL),0,1);
            }else amount=Math.min(3/Math.max(1,units(boss)),amount);
            amount*=bonus;amount=Math.min(amount,boss.getMaxHealth()*hitCap);
            while(!recent.isEmpty()&&clock-recent.peekFirst()[0]>=20)recent.removeFirst();
            if(!recent.isEmpty()){
                float total=0;for(float[] hit:recent)total+=hit[1];
                amount=Math.min(amount,Math.max(0,boss.getMaxHealth()*secondCap-total));
            }
        }
        return amount;
    }
    public void paid(int tick,float amount){if(amount>0)recent.add(new float[]{tick,amount});}
    public void clear(){recent.clear();}
}
