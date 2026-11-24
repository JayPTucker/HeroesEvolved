package com.jayptucker.heroesevolved.endgame;

import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import java.util.*;

/** A deflectable, tangible crystal shard; no terrain explosion and no stray fire. */
public final class EndBolt extends LargeFireball implements net.minecraft.world.entity.projectile.ItemSupplier {
    private int power=-1;
    public EndBolt(EntityType<? extends EndBolt> type,Level level){super(type,level);}
    public void power(int value){power=value;}
    @Override public ItemStack getItem(){return new ItemStack(Items.AMETHYST_SHARD);}
    @Override protected boolean shouldBurn(){return false;}
    @Override public void tick(){super.tick();if(!level().isClientSide){if(tickCount>180)discard();else if(tickCount%5==0)SetPiecePayload.sendSeeded((ServerLevel)level(),Piece.END_BATTLE_BOLT,position(),position().subtract(getDeltaMovement().scale(2)),1,this,0,power+1);}}
    public void disrupt(ServerPlayer p){setOwner(p);impact(position(),null);}
    @Override protected void onHit(HitResult hit){if(!level().isClientSide)impact(hit.getLocation(),hit instanceof EntityHitResult entityHit?entityHit.getEntity():null);}
    private void impact(Vec3 at,Entity direct){
        if(!(level() instanceof ServerLevel level))return;var battle=EndBattle.current();if(battle==null||battle.level!=level){discard();return;}
        Set<LivingEntity> targets=new HashSet<>(level.getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(EndGeometry.BOLT_RADIUS),e->e.getBoundingBox().distanceToSqr(at)<=EndGeometry.BOLT_RADIUS*EndGeometry.BOLT_RADIUS));
        if(direct instanceof EnderDragonPart part)targets.add(part.parentMob);
        if(direct instanceof LivingEntity living)targets.add(living);
        for(LivingEntity target:targets){
            if(target==getOwner())continue;
            if(target instanceof ServerPlayer p){
                if(getOwner() instanceof ServerPlayer owner)EndMoves.hit(battle,p,8,power,damageSources().fireball(this,owner));
                else EndMoves.hit(battle,p,8,power);
            }
            else if(target==battle.boss()&&getOwner() instanceof ServerPlayer p)target.hurt(damageSources().fireball(this,p),14);
        }
        if(direct instanceof net.minecraft.world.entity.boss.enderdragon.EndCrystal crystal&&getOwner() instanceof ServerPlayer p)EndBattle.crystalHit(crystal,damageSources().playerAttack(p),12);
        SetPiecePayload.sendSeeded(level,Piece.END_BATTLE_IMPACT,at,at,EndGeometry.BOLT_RADIUS,null,0,power+1);discard();
    }
}
