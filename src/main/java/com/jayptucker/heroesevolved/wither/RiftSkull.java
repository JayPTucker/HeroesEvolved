package com.jayptucker.heroesevolved.wither;

import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A real deflectable Wither skull, with a controlled impact instead of a terrain-destroying vanilla explosion. Sent back
 * by a fighter (Wind Arms, Bullet Catch, a force field, a quake's push, a punch: anything that makes it theirs) it is a
 * counter: it shatters a Hunger Heart outright and hits the Wither past its ward ({@link WitherGeometry#RETURNED_SHARE}).
 */
public final class RiftSkull extends WitherSkull {
    public RiftSkull(EntityType<? extends RiftSkull> type,Level level){super(type,level);}
    /** A fighter has sent it back. */
    public boolean returned(){return getOwner() instanceof ServerPlayer;}
    @Override public void tick(){super.tick();if(tickCount>100&&!level().isClientSide)discard();}
    /** Its own skulls pass through its hearts; only a returned one breaks them. */
    @Override protected boolean canHitEntity(net.minecraft.world.entity.Entity target){
        if(target instanceof HungerHeart&&!returned())return false;
        return super.canHitEntity(target);
    }
    @Override protected void onHit(HitResult hit) {
        if(!(level() instanceof ServerLevel level))return;
        if(!WitherArena.inside(this)){discard();return;}
        var boss=WitherArena.boss();Vec3 at=hit.getLocation();
        ServerPlayer sender=getOwner() instanceof ServerPlayer p?p:null;
        if(boss!=null&&sender!=null)for(HungerHeart heart:boss.hearts)
            if(heart!=null&&!heart.isRemoved()&&heart.lock()!=null&&at.distanceTo(heart.aimPoint())<WitherGeometry.SKULL_RADIUS){
                heart.hurt(damageSources().witherSkull(this,sender),1);
                SetPiecePayload.send(level,Piece.WITHER_IMPACT,at,at,2,null);discard();return;
            }
        for(LivingEntity target:level.getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(WitherGeometry.SKULL_RADIUS),e->e.isAlive()&&e!=getOwner()
                &&!(e instanceof HungerHeart)
                &&!(getOwner() instanceof RiftWitherEntity&&e.getPersistentData().getBoolean(WitherMoves.BROOD))
                &&e.getBoundingBox().distanceToSqr(at)<=WitherGeometry.SKULL_RADIUS*WitherGeometry.SKULL_RADIUS)) {
            if(target instanceof ServerPlayer p&&WitherArena.sheltered(p))continue;
            float damage=boss==null?8:WitherGeometry.damage(WitherGeometry.Move.RAIN,boss.phase());
            target.invulnerableTime=0;
            target.hurt(damageSources().witherSkull(this,getOwner()),target instanceof RiftWitherEntity w?w.getMaxHealth()*WitherGeometry.RETURNED_SHARE:damage);
        }
        SetPiecePayload.send(level,Piece.WITHER_IMPACT,at,at,2,null);discard();
    }
}
