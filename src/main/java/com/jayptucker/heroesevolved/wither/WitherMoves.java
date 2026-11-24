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
        b.castFrom=b.core();
        if(move==WitherGeometry.Move.VERDICT) {
            var aimed=List.copyOf(b.marks);b.marks.clear();
            for(Vec3 mark:aimed) {
                Vec3 side=mark.subtract(b.castFrom).normalize().cross(new Vec3(0,1,0));
                for(int i=-1;i<=1;i++) {
                    if(i!=0&&(b.burnt()&(1<<(i<0?0:1)))!=0)continue;
                    Vec3 end=mark.add(side.scale(i*3.5));b.marks.add(end);b.origins.add(WitherGeometry.mouth(b.position(),b.lockedYaw,i+1==3?0:i+1,end));
                }
            }
        } else if(move==WitherGeometry.Move.RAIN){
            var aimed=List.copyOf(b.marks);b.marks.clear();for(Vec3 mark:aimed)for(int i=0;i<3;i++){
                double angle=i*Math.PI*2/3,radius=i==0?0:WitherGeometry.RAIN_SPREAD;b.marks.add(mark.add(Math.cos(angle)*radius,0,Math.sin(angle)*radius));
            }
        } else if(move==WitherGeometry.Move.LUNGE||move==WitherGeometry.Move.SLAM||move==WitherGeometry.Move.TETHER) {
            if(b.marks.size()>1)b.marks.subList(1,b.marks.size()).clear();
        }
        if(move==WitherGeometry.Move.SLAM){b.marks.replaceAll(p->new Vec3(p.x,WitherGeometry.floor().y,p.z));b.motionFrom=b.position();}
        if(move==WitherGeometry.Move.TETHER&&!b.marks.isEmpty())b.castFrom=WitherGeometry.mouth(b.position(),b.lockedYaw,0,b.marks.getFirst());
        if(move==WitherGeometry.Move.LUNGE&&!b.marks.isEmpty()) {
            // Commit to the same three-dimensional lane that is drawn, including an airborne target's height.
            b.castFrom=b.position().add(0,3,0);
            Vec3 delta=b.marks.getFirst().add(0,.2,0).subtract(b.castFrom);
            if(delta.length()>WitherGeometry.LUNGE_REACH)delta=delta.normalize().scale(WitherGeometry.LUNGE_REACH);
            Vec3 end=b.castFrom.add(delta);b.marks.set(0,new Vec3(end.x,Math.max(WitherGeometry.floor().y+3.3,end.y),end.z));
        }
        if(move==WitherGeometry.Move.HORIZON||move==WitherGeometry.Move.PULSE||move==WitherGeometry.Move.BITE||move==WitherGeometry.Move.RAKE)
            SetPiecePayload.sendSeeded(level,Piece.WITHER_WARNING,b.castFrom,WitherGeometry.floor(),1,b,0,move.ordinal());
        else for(int i=0;i<b.marks.size();i++)SetPiecePayload.sendSeeded(level,Piece.WITHER_WARNING,move==WitherGeometry.Move.VERDICT?b.origins.get(i):b.castFrom,b.marks.get(i),1,b,0,move.ordinal());
        var cue=switch(move){case BITE,RAKE->SoundEvents.SKELETON_STEP;case VERDICT,RAIN->SoundEvents.AMETHYST_BLOCK_RESONATE;case HORIZON,PULSE->SoundEvents.BEACON_POWER_SELECT;default->SoundEvents.WITHER_AMBIENT;};
        level.playSound(null,b.blockPosition(),cue,SoundSource.HOSTILE,.8F,.55F+b.phase()*.07F);
    }
    public static void windup(RiftWitherEntity b,WitherGeometry.Move move){
        if(move!=WitherGeometry.Move.SLAM||b.marks.isEmpty())return;double progress=1-b.windup/(double)move.windup;
        Vec3 wanted=b.motionFrom.lerp(b.marks.getFirst().add(0,12,0),Math.min(1,progress*1.25));Vec3 delta=wanted.subtract(b.position());
        if(delta.length()>1.2)delta=delta.normalize().scale(1.2);
        if(b.level().noCollision(b,b.getBoundingBox().move(delta)))b.setPos(b.position().add(delta));
    }
    public static void sideShot(RiftWitherEntity b,Vec3 from,Vec3 to){
        ServerLevel level=(ServerLevel)b.level();var skull=ModEntities.RIFT_SKULL.get().create(level);if(skull==null)return;
        skull.setOwner(b);skull.setPos(from);skull.setDeltaMovement(to.subtract(from).normalize().scale(.72));skull.accelerationPower=.01;level.addFreshEntity(skull);b.skulls.add(skull);
        SetPiecePayload.send(level,Piece.WITHER_MUZZLE,from,to,1,b);level.playSound(null,from.x,from.y,from.z,SoundEvents.WITHER_SHOOT,SoundSource.HOSTILE,.75F,.9F);
    }
    public static boolean seen(ServerLevel level,Vec3 from,Vec3 to) {
        return level.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,net.minecraft.world.phys.shapes.CollisionContext.empty())).getType()==HitResult.Type.MISS;
    }
    public static boolean hit(RiftWitherEntity b,ServerPlayer p,float amount) {
        if(!b.fighting()||WitherArena.sheltered(p)||!p.isAlive())return false;
        p.invulnerableTime=0;
        boolean landed=p.hurt(b.damageSources().mobAttack(b),amount);if(landed)p.addEffect(new MobEffectInstance(MobEffects.WITHER,60));return landed;
    }
    public static void land(RiftWitherEntity b,WitherGeometry.Move move) {
        ServerLevel level=(ServerLevel)b.level();float damage=6+b.phase();
        switch(move) {
            case VERDICT -> {
                for(ServerPlayer p:b.fighters())for(int i=0;i<b.marks.size();i++)
                    if(WitherGeometry.lineDistance(b.origins.get(i),b.marks.get(i),p.position().add(0,.8,0))<WitherGeometry.VERDICT_RADIUS
                            &&seen(level,b.origins.get(i),p.getEyePosition())) {hit(b,p,damage);break;}
                for(int i=0;i<b.marks.size();i++)SetPiecePayload.sendSeeded(level,Piece.WITHER_FIRE,b.origins.get(i),b.marks.get(i),WitherGeometry.VERDICT_RADIUS,b,0,move.ordinal());
            }
            case RAIN -> {
                int index=0;for(Vec3 mark:b.marks) {
                    var skull=ModEntities.RIFT_SKULL.get().create(level);if(skull==null)continue;
                    skull.setOwner(b);skull.setPos(mark.add(0,WitherGeometry.RAIN_HEIGHT+(index++%3)*1.2,0));skull.setDeltaMovement(0,-.7,0);skull.accelerationPower=.03;
                    level.addFreshEntity(skull);b.skulls.add(skull);
                }
            }
            case PULSE -> {
                b.waveAge=0;b.waveHit.clear();b.waveCenter=new Vec3(b.castFrom.x,WitherGeometry.floor().y+.05,b.castFrom.z);
                SetPiecePayload.send(level,Piece.WITHER_PULSE,b.waveCenter,b.waveCenter,1,b);
            }
            case TETHER -> {
                if(b.marks.isEmpty())break;
                for(ServerPlayer p:b.fighters())if(WitherGeometry.lineDistance(b.castFrom,b.marks.getFirst(),p.position().add(0,.8,0))<WitherGeometry.TETHER_RADIUS
                        &&seen(level,b.castFrom,p.getEyePosition())) {
                    hit(b,p,5);p.setDeltaMovement(b.castFrom.subtract(p.position()).normalize().scale(1.1).add(0,.2,0));p.hurtMarked=true;
                }
                SetPiecePayload.sendSeeded(level,Piece.WITHER_FIRE,b.castFrom,b.marks.getFirst(),WitherGeometry.TETHER_RADIUS,b,0,move.ordinal());
            }
            case LUNGE -> {
                if(b.marks.isEmpty())break;
                b.motion=move;b.motionTicks=WitherGeometry.LUNGE_TICKS;b.motionStep=0;b.motionFrom=b.position();b.motionTo=b.marks.getFirst().add(0,-3,0);b.motionHit.clear();
            }
            case SLAM -> {
                if(b.marks.isEmpty())break;b.motion=move;b.motionTicks=WitherGeometry.SLAM_TICKS;b.motionStep=0;b.motionFrom=b.position();b.motionTo=b.marks.getFirst().add(0,.3,0);b.motionHit.clear();
            }
            case BITE,RAKE -> {
                float reach=move==WitherGeometry.Move.BITE?WitherGeometry.BITE_REACH:WitherGeometry.RAKE_REACH;
                float angle=move==WitherGeometry.Move.BITE?WitherGeometry.BITE_HALF_ANGLE:WitherGeometry.RAKE_HALF_ANGLE;
                for(ServerPlayer p:b.fighters())if(WitherGeometry.inArc(b.castFrom,p.position(),b.lockedYaw,reach,angle)&&Math.abs(p.getY()+.8-b.castFrom.y)<4.2&&seen(level,b.castFrom,p.getEyePosition()))hit(b,p,move==WitherGeometry.Move.BITE?7:9);
                Vec3 direction=Vec3.directionFromRotation(0,b.lockedYaw);SetPiecePayload.sendSeeded(level,Piece.WITHER_ARC,b.castFrom,b.castFrom.add(direction),reach,b,0,move.ordinal());
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
            if(Math.abs(d-radius)<1.1&&Math.abs(p.getY()-b.waveCenter.y)<WitherGeometry.WAVE_HEIGHT&&b.waveHit.add(p.getUUID()))hit(b,p,6+b.phase());
        }
        if(b.waveAge>=110)b.waveAge=-1;
    }
    public static void tickMotion(RiftWitherEntity b){
        ServerLevel level=(ServerLevel)b.level();Vec3 before=b.position();double p=++b.motionStep/(double)(b.motion==WitherGeometry.Move.LUNGE?WitherGeometry.LUNGE_TICKS:WitherGeometry.SLAM_TICKS);
        double ease=b.motion==WitherGeometry.Move.SLAM?p*p:p*p*(3-2*p);Vec3 desired=b.motionFrom.lerp(b.motionTo,ease),delta=desired.subtract(before),end=before;
        if(delta.length()>3.6)delta=delta.normalize().scale(3.6);
        for(int i=1;i<=12;i++){Vec3 next=before.add(delta.scale(i/12D));if(!level.noCollision(b,b.getBoundingBox().move(next.subtract(before))))break;end=next;}
        b.setPos(end);
        if(b.motion==WitherGeometry.Move.LUNGE){
            for(ServerPlayer player:b.fighters())if(!b.motionHit.contains(player.getUUID())&&WitherGeometry.lineDistance(before.add(0,3,0),end.add(0,3,0),player.position().add(0,.8,0))<WitherGeometry.LUNGE_RADIUS){b.motionHit.add(player.getUUID());if(hit(b,player,8+b.phase())){player.setDeltaMovement(b.motionTo.subtract(b.motionFrom).normalize().scale(.9).add(0,.25,0));player.hurtMarked=true;}}
            SetPiecePayload.send(level,Piece.WITHER_WAKE,before.add(0,3,0),end.add(0,3,0),3,b);
        }
        if(--b.motionTicks<=0||end.distanceToSqr(before.add(delta))>.05){
            if(b.motion==WitherGeometry.Move.SLAM&&end.distanceTo(b.motionTo)<1){Vec3 at=b.motionTo;for(ServerPlayer player:b.fighters())if(player.position().distanceTo(at)<WitherGeometry.SLAM_RADIUS)hit(b,player,8+b.phase());
                SetPiecePayload.send(level,Piece.WITHER_IMPACT,at,at,WitherGeometry.SLAM_RADIUS,null);CameraShakePayload.sendAround(level,at,80,.45F,22);level.playSound(null,at.x,at.y,at.z,SoundEvents.GENERIC_EXPLODE.value(),SoundSource.HOSTILE,1,.65F);}
            b.motionTicks=0;b.motion=null;b.spent=80;b.recovery=12;
        }
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
