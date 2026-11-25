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

/** A real deflectable Wither skull, with a controlled impact instead of a terrain-destroying vanilla explosion. */
public final class RiftSkull extends WitherSkull {
    public RiftSkull(EntityType<? extends RiftSkull> type,Level level){super(type,level);}
    @Override public void tick(){super.tick();if(tickCount>100&&!level().isClientSide)discard();}
    @Override protected void onHit(HitResult hit) {
        if(!(level() instanceof ServerLevel level))return;
        if(!WitherArena.inside(this)){discard();return;}
        var boss=WitherArena.boss();Vec3 at=hit.getLocation();
        if(boss!=null)for(int i=0;i<WitherGeometry.ANCHORS.size();i++)
            if(at.distanceTo(Vec3.atCenterOf(WitherGeometry.ANCHORS.get(i)))<WitherGeometry.SKULL_RADIUS
                    &&boss.backfire(i,getOwner() instanceof ServerPlayer p?p:null)) {discard();return;}
        for(LivingEntity target:level.getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(WitherGeometry.SKULL_RADIUS),e->e.isAlive()&&e!=getOwner()
                &&!(getOwner() instanceof RiftWitherEntity&&e.getPersistentData().getBoolean(WitherMoves.BROOD))
                &&e.getBoundingBox().distanceToSqr(at)<=WitherGeometry.SKULL_RADIUS*WitherGeometry.SKULL_RADIUS)) {
            if(target instanceof ServerPlayer p&&WitherArena.sheltered(p))continue;
            float damage=boss==null?8:WitherGeometry.damage(WitherGeometry.Move.RAIN,boss.phase());
            target.hurt(damageSources().witherSkull(this,getOwner()),target instanceof RiftWitherEntity?12:damage);
        }
        SetPiecePayload.send(level,Piece.WITHER_IMPACT,at,at,2,null);discard();
    }
}
