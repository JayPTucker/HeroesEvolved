package com.jayptucker.heroesevolved.wither;

import com.jayptucker.heroesevolved.gifted.GiftedMobs;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.registry.ModEntities;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** Fixed warning positions, committed attacks and actual cover. No terrain explosions outside the cage. */
public final class WitherMoves {
    private WitherMoves() {}
    public static void warn(RiftWitherEntity b,WitherGeometry.Move move) {
        ServerLevel level=(ServerLevel)b.level();
        b.castFrom=b.position().add(0,2.1,0);
        if(move==WitherGeometry.Move.VERDICT) {
            var aimed=List.copyOf(b.marks);b.marks.clear();
            for(Vec3 mark:aimed) {
                Vec3 side=mark.subtract(b.castFrom).normalize().cross(new Vec3(0,1,0));
                for(int i=-1;i<=1;i++) {
                    if(i!=0&&(b.burnt()&(1<<(i<0?0:1)))!=0)continue;
                    b.marks.add(mark.add(side.scale(i*2.8)));
                }
            }
        } else if(move==WitherGeometry.Move.LUNGE||move==WitherGeometry.Move.SLAM||move==WitherGeometry.Move.TETHER) {
            if(b.marks.size()>1)b.marks.subList(1,b.marks.size()).clear();
        }
        if(move==WitherGeometry.Move.SLAM)b.marks.replaceAll(p->new Vec3(p.x,WitherGeometry.floor().y,p.z));
        if(move==WitherGeometry.Move.LUNGE&&!b.marks.isEmpty()) {
            // Commit to the same three-dimensional lane that is drawn, including an airborne target's height.
            b.castFrom=b.position().add(0,1,0);
            Vec3 delta=b.marks.getFirst().add(0,.2,0).subtract(b.castFrom);
            if(delta.length()>WitherGeometry.LUNGE_REACH)delta=delta.normalize().scale(WitherGeometry.LUNGE_REACH);
            b.marks.set(0,b.castFrom.add(delta));
        }
        if(move==WitherGeometry.Move.HORIZON||move==WitherGeometry.Move.PULSE)
            SetPiecePayload.sendSeeded(level,Piece.WITHER_WARNING,b.castFrom,WitherGeometry.floor(),1,b,0,move.ordinal());
        else for(Vec3 mark:b.marks)SetPiecePayload.sendSeeded(level,Piece.WITHER_WARNING,b.castFrom,mark,1,b,0,move.ordinal());
        level.playSound(null,b.blockPosition(),SoundEvents.WITHER_AMBIENT,SoundSource.HOSTILE,.8F,.65F+b.phase()*.07F);
    }
    public static boolean seen(ServerLevel level,Vec3 from,Vec3 to) {
        return level.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,net.minecraft.world.phys.shapes.CollisionContext.empty())).getType()==HitResult.Type.MISS;
    }
    public static void hit(RiftWitherEntity b,ServerPlayer p,float amount) {
        if(WitherArena.sheltered(p)||!p.isAlive())return;
        p.invulnerableTime=0;
        if(p.hurt(b.damageSources().mobAttack(b),amount))p.addEffect(new MobEffectInstance(MobEffects.WITHER,60));
    }
    public static void land(RiftWitherEntity b,WitherGeometry.Move move) {
        ServerLevel level=(ServerLevel)b.level();float damage=6+b.phase();
        switch(move) {
            case VERDICT -> {
                for(ServerPlayer p:b.fighters())for(Vec3 mark:b.marks)
                    if(WitherGeometry.lineDistance(b.castFrom,mark,p.position().add(0,.8,0))<WitherGeometry.VERDICT_RADIUS
                            &&seen(level,b.castFrom,p.getEyePosition())) {hit(b,p,damage);break;}
            }
            case RAIN -> {
                for(Vec3 mark:b.marks) {
                    var skull=ModEntities.RIFT_SKULL.get().create(level);if(skull==null)continue;
                    skull.setOwner(b);skull.setPos(mark.add(0,WitherGeometry.RAIN_HEIGHT,0));skull.setDeltaMovement(0,-.7,0);skull.accelerationPower=.03;
                    level.addFreshEntity(skull);
                }
            }
            case PULSE -> {
                b.waveAge=0;b.waveHit.clear();b.waveCenter=new Vec3(b.castFrom.x,WitherGeometry.floor().y+.05,b.castFrom.z);
                SetPiecePayload.send(level,Piece.WITHER_PULSE,b.waveCenter,b.waveCenter,1,null);
            }
            case TETHER -> {
                if(b.marks.isEmpty())break;
                for(ServerPlayer p:b.fighters())if(WitherGeometry.lineDistance(b.castFrom,b.marks.getFirst(),p.position().add(0,.8,0))<WitherGeometry.TETHER_RADIUS
                        &&seen(level,b.castFrom,p.getEyePosition())) {
                    hit(b,p,5);p.setDeltaMovement(b.castFrom.subtract(p.position()).normalize().scale(1.1).add(0,.2,0));p.hurtMarked=true;
                }
            }
            case LUNGE -> {
                if(b.marks.isEmpty())break;
                Vec3 from=b.castFrom.add(0,-1,0),target=b.marks.getFirst().add(0,-1,0);
                Vec3 delta=target.subtract(from);
                Vec3 end=from;
                for(double t=.03;t<=1;t+=.03) {
                    Vec3 at=from.add(delta.scale(t));
                    if(!level.noCollision(b,b.getBoundingBox().move(at.subtract(b.position()))))break;
                    end=at;
                }
                b.setPos(end);
                for(ServerPlayer p:b.fighters())if(WitherGeometry.lineDistance(from.add(0,1,0),end.add(0,1,0),p.position().add(0,.8,0))<WitherGeometry.LUNGE_RADIUS)hit(b,p,damage+2);
                SetPiecePayload.send(level,Piece.WITHER_IMPACT,end,end,3,b);
            }
            case SLAM -> {
                if(b.marks.isEmpty())break;Vec3 at=b.marks.getFirst();
                for(ServerPlayer p:b.fighters())if(p.position().distanceTo(at)<WitherGeometry.SLAM_RADIUS)hit(b,p,damage+2);
                SetPiecePayload.send(level,Piece.WITHER_IMPACT,at,at,4,null);
            }
            case HORIZON -> {
                for(ServerPlayer p:b.fighters())if(seen(level,b.castFrom,p.getEyePosition()))hit(b,p,14);
                SetPiecePayload.send(level,Piece.WITHER_NOVA,b.castFrom,b.castFrom,22,b);
            }
        }
        CameraShakePayload.sendAround(level,b.position(),48,move==WitherGeometry.Move.HORIZON?.5F:.18F,12);
        level.playSound(null,b.blockPosition(),SoundEvents.WITHER_SHOOT,SoundSource.HOSTILE,1,.6F);
    }
    public static void tickWave(RiftWitherEntity b) {
        if(b.waveAge<0)return;
        double radius=++b.waveAge*WitherGeometry.PULSE_SPEED;
        for(ServerPlayer p:b.fighters()) {
            double d=p.position().multiply(1,0,1).distanceTo(b.waveCenter.multiply(1,0,1));
            if(Math.abs(d-radius)<1.1&&p.getY()<b.waveCenter.y+1.4&&b.waveHit.add(p.getUUID()))hit(b,p,6+b.phase());
        }
        if(b.waveAge>=110)b.waveAge=-1;
    }
    public static void minions(RiftWitherEntity b,int phase) {
        ServerLevel level=(ServerLevel)b.level();int count=phase==2?Math.min(3,1+b.fighters().size()/2):b.fighters().size()>2?1:0;
        for(int i=0;i<count;i++) {
            var skeleton=EntityType.WITHER_SKELETON.create(level);if(skeleton==null)continue;
            skeleton.getPersistentData().putBoolean(GiftedMobs.FOLLOWER,true);
            skeleton.getPersistentData().putBoolean(GiftedMobs.ROLLED,true);
            skeleton.moveTo(WitherGeometry.floor().add(i%2==0?-15:15,0,9-i*3));skeleton.setPersistenceRequired();
            skeleton.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE_SWORD));
            skeleton.setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND,0);
            if(!b.fighters().isEmpty())skeleton.setTarget(b.fighters().getFirst());
            level.addFreshEntity(skeleton);b.minions.add(skeleton);
        }
    }
}
