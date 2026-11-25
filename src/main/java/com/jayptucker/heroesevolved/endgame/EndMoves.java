package com.jayptucker.heroesevolved.endgame;

import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.hydro.HydroService;
import com.jayptucker.heroesevolved.mimicry.MimicColors;
import com.jayptucker.heroesevolved.network.*;
import com.jayptucker.heroesevolved.registry.ModEntities;
import com.jayptucker.heroesevolved.story.sylar.SylarEntity;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.CollisionContext;
import java.util.*;

/** One fixed geometric language for all six phases. Sampled gifts have distinct shapes and effects, not just tints. */
public final class EndMoves {
    public enum Pattern { FAN,IMPACT,LATTICE,SPIRAL,NOVA,FAULT,RING,CHARGE,STONE,MIND,EMP,FRAME,SIPHON,PRISM,SEQUENCE }
    public static String powerPath(int index){String path=MimicColors.power(index);return path==null?"telekinesis":path;}
    public static Component powerName(int index){return Component.translatable("ability.heroesevolved."+powerPath(index));}
    public static Pattern pattern(int index){return switch(powerPath(index)){
        case "pyrokinesis"->Pattern.FAN;case "cryokinesis"->Pattern.IMPACT;case "electrokinesis","invisibility"->Pattern.LATTICE;
        case "hydrokinesis","flight"->Pattern.SPIRAL;case "radiation","power_amplification"->Pattern.NOVA;
        case "quake","phasing"->Pattern.FAULT;case "sonic_resonance"->Pattern.RING;case "superspeed","shapeshifting"->Pattern.CHARGE;
        case "telepathy"->Pattern.MIND;case "technopathy"->Pattern.EMP;case "matter_assembly"->Pattern.FRAME;
        case "regeneration"->Pattern.SIPHON;case "vibe"->Pattern.PRISM;case "time_manipulation","empathic_mimicry"->Pattern.SEQUENCE;
        default->Pattern.STONE;
    };}
    public static int windup(EndGeometry.Move move,int sampled){return move==EndGeometry.Move.PATTERN&&pattern(sampled)==Pattern.NOVA?EndGeometry.Move.ENDINGS.windup:move.windup;}
    /** Sylar's rotation. The Dragon chooses by where it is and where you stand ({@link DragonBrain#next}). */
    public static EndGeometry.Move[] rotation(int stage,int phase,boolean sampled){
        return phase==1?new EndGeometry.Move[]{EndGeometry.Move.NEEDLE,EndGeometry.Move.STONE,EndGeometry.Move.READ,EndGeometry.Move.PATTERN,EndGeometry.Move.REVERSAL}:
                phase==2?new EndGeometry.Move[]{EndGeometry.Move.WORLDLINE_STEP,EndGeometry.Move.READ,EndGeometry.Move.PATTERN,EndGeometry.Move.EVENTIDE,EndGeometry.Move.STONE,EndGeometry.Move.NEEDLE}:
                new EndGeometry.Move[]{EndGeometry.Move.ENDINGS,EndGeometry.Move.READ,EndGeometry.Move.PATTERN,EndGeometry.Move.WORLDLINE_STEP,EndGeometry.Move.EVENTIDE,EndGeometry.Move.NEEDLE};
    }
    public static boolean seen(ServerLevel level,Vec3 from,Vec3 to){return level.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,CollisionContext.empty())).getType()==HitResult.Type.MISS;}
    public static boolean hit(EndBattle.Encounter e,ServerPlayer p,float damage,int power){
        return hit(e,p,damage,power,e.boss().damageSources().mobAttack(e.boss()));
    }
    public static boolean hit(EndBattle.Encounter e,ServerPlayer p,float damage,int power,net.minecraft.world.damagesource.DamageSource source){
        if(e.stage!=EndBattle.DRAGON&&e.stage!=EndBattle.SYLAR||!e.fighters().contains(p)||EndBattle.sheltered(p))return false;
        if(source.getEntity() instanceof ServerPlayer owner&&!com.jayptucker.heroesevolved.flight.FlightSkillService.hostile(owner,p))return false;
        p.invulnerableTime=0;boolean paid=p.hurt(source,damage);
        if(!paid)return false;EndChannel.cancel(p);
        switch(powerPath(power)){
            case "pyrokinesis"->p.igniteForTicks(80);
            case "cryokinesis"->p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,60,1));
            case "electrokinesis"->{p.addEffect(new MobEffectInstance(MobEffects.GLOWING,60));p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,25));}
            case "hydrokinesis"->{HydroService.soak(p,160);p.clearFire();}
            case "telepathy"->p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,60));
            case "technopathy"->{e.suppressed.put(p.getUUID(),e.clock+40);p.sendSystemMessage(Component.translatable("endgame.heroesevolved.interference"));}
            case "phasing","quake"->{p.setDeltaMovement(p.getDeltaMovement().add(0,.55,0));p.hurtMarked=true;}
            default->{}
        }
        return true;
    }
    public static int seed(EndGeometry.Move move,int power,int role){return move.ordinal()|((power+1)&31)<<5|role<<10;}
    public static int power(int seed){return ((seed>>>5)&31)-1;}
    public static int role(int seed){return seed>>>10;}
    public static EndGeometry.Move move(int seed){return EndGeometry.Move.byId(seed&31);}
    private static Pattern shape(EndGeometry.Move move,int sampled){return switch(move){
        case BREATH->Pattern.FAN;case NEEDLE->Pattern.LATTICE;case STARS,STONE,BLOOM->Pattern.IMPACT;
        case WINGBREAK,EVENTIDE->Pattern.RING;case HARPOON,REVERSAL->Pattern.SIPHON;case RIFT_CHARGE,WORLDLINE_STEP->Pattern.CHARGE;
        case UNMAKING,ENDINGS->Pattern.NOVA;case READ->Pattern.MIND;case PATTERN->pattern(sampled);
        case DESCENT,BITE,TAIL,SCORCH,ROAR,TAKEOFF,RUSH->Pattern.IMPACT;
    };}
    public static Pattern shape(int seed){return shape(move(seed),power(seed));}
    public static void warn(EndBattle.Encounter e,EndGeometry.Move move){
        List<ServerPlayer> fighters=e.fighters();if(fighters.isEmpty())return;
        if(e.stage==EndBattle.DRAGON&&e.brain!=null&&e.brain.warn(move))return;
        if(move==EndGeometry.Move.READ){e.sampled=PlayerAbilityService.getData(fighters.get(e.rotation%fighters.size())).assignedPower().map(p->MimicColors.index(p.getKey().getPath())).orElse(3);}
        Pattern shape=shape(move,e.sampled);int power=move==EndGeometry.Move.PATTERN||move==EndGeometry.Move.READ?e.sampled:-1;
        for(ServerPlayer p:fighters)e.marks.add(p.position().add(0,.9,0));
        if(shape==Pattern.FAN||shape==Pattern.LATTICE||shape==Pattern.FRAME||shape==Pattern.PRISM||shape==Pattern.FAULT){
            var aimed=List.copyOf(e.marks);e.marks.clear();
            for(Vec3 mark:aimed){Vec3 side=mark.subtract(e.castFrom).normalize().cross(new Vec3(0,1,0));
                for(int i=-1;i<=1;i++)e.marks.add(mark.add(side.scale(i*(shape==Pattern.FAN?5:3.5))));}
        }
        if(shape==Pattern.SEQUENCE){var aimed=List.copyOf(e.marks);e.marks.clear();for(Vec3 mark:aimed)for(int i=0;i<3;i++)e.marks.add(mark.add((i-1)*5,0,0));}
        if(shape==Pattern.SPIRAL){var aimed=List.copyOf(e.marks);e.marks.clear();for(Vec3 mark:aimed)for(int i=0;i<3;i++){double angle=i*Math.PI*2/3;e.marks.add(mark.add(Math.cos(angle)*3,i*1.5,Math.sin(angle)*3));}}
        if(shape==Pattern.CHARGE){
            e.chargeFrom=e.boss().position();Vec3 delta=e.marks.getFirst().add(0,-.9,0).subtract(e.chargeFrom);
            if(delta.length()>EndGeometry.CHARGE_REACH)delta=delta.normalize().scale(EndGeometry.CHARGE_REACH);
            e.chargeTo=e.chargeFrom.add(delta);e.castFrom=e.chargeFrom.add(0,e.stage==EndBattle.DRAGON?3:1,0);e.marks.clear();e.marks.add(e.chargeTo.add(0,e.stage==EndBattle.DRAGON?3:1,0));
            float yaw=(float)Math.toDegrees(e.stage==EndBattle.DRAGON?Math.atan2(delta.x,-delta.z):Math.atan2(-delta.x,delta.z));
            e.boss().setYRot(yaw);e.boss().setYHeadRot(yaw);e.boss().yBodyRot=yaw;
        }
        if(shape==Pattern.NOVA){
            e.safeAngle=(float)(Math.atan2(fighters.getFirst().getZ(),fighters.getFirst().getX())+Math.PI/2);
            e.target=EndGeometry.center(e.data.floor).add(Math.cos(e.safeAngle)*110,0,Math.sin(e.safeAngle)*110);e.marks.clear();
            for(ServerPlayer p:fighters){Vec3 side=p.getLookAngle().multiply(1,0,1).normalize().cross(new Vec3(0,1,0));if(side.lengthSqr()<.01)side=new Vec3(1,0,0);
                e.marks.add(p.position().add(0,.9,0).add(side.scale(EndGeometry.SAFE_ORBIT_OFFSET)));}
            SetPiecePayload.sendSeeded(e.level,Piece.END_BATTLE_WARNING,e.castFrom,e.target,1,e.boss(),0,seed(move,power,0));
            for(Vec3 safe:e.marks)SetPiecePayload.sendSeeded(e.level,Piece.END_BATTLE_WARNING,e.castFrom,safe,1,e.boss(),0,seed(move,power,1));
        }else if(shape==Pattern.RING){e.castFrom=new Vec3(e.boss().getX(),e.data.floor+.1,e.boss().getZ());SetPiecePayload.sendSeeded(e.level,Piece.END_BATTLE_WARNING,e.castFrom,e.castFrom,1,e.boss(),0,seed(move,power,0));}
        else {int index=0;for(Vec3 mark:e.marks)SetPiecePayload.sendSeeded(e.level,Piece.END_BATTLE_WARNING,e.castFrom,mark,1,e.boss(),shape==Pattern.SEQUENCE?index++%3*EndGeometry.ECHO_GAP:0,seed(move,power,0));}
        if(e.sylar!=null)e.sylar.inheritancePose(e.phase,shape==Pattern.CHARGE?SylarEntity.Pose.CROUCH:shape==Pattern.NOVA?SylarEntity.Pose.RAISED:shape==Pattern.SIPHON||shape==Pattern.MIND?SylarEntity.Pose.ONE_HAND:SylarEntity.Pose.TWO_HANDS,true,false,e.finishWindow());
        sound(e,e.castFrom,move==EndGeometry.Move.UNMAKING||move==EndGeometry.Move.ENDINGS?SoundEvents.BEACON_POWER_SELECT:SoundEvents.AMETHYST_BLOCK_RESONATE,.8F,.6F+e.phase*.1F);
    }
    public static void land(EndBattle.Encounter e,EndGeometry.Move move){
        if(e.stage==EndBattle.DRAGON&&e.brain!=null){if(!e.brain.land(move))landShape(e,move);e.brain.landed(move);return;}
        landShape(e,move);
    }
    private static void landShape(EndBattle.Encounter e,EndGeometry.Move move){
        Pattern shape=shape(move,e.sampled);int power=move==EndGeometry.Move.PATTERN?e.sampled:-1;float damage=e.stage==EndBattle.DRAGON?7+e.phase:6+e.phase;
        if(move==EndGeometry.Move.READ){e.spawnPrism();return;}
        switch(shape){
            case FAN,LATTICE,FRAME,PRISM,FAULT -> {
                for(ServerPlayer p:e.fighters())for(Vec3 mark:e.marks)if(EndGeometry.segmentDistance(e.castFrom,mark,p.position().add(0,.9,0))<EndGeometry.LANE_RADIUS&&seen(e.level,e.castFrom,p.getEyePosition())){hit(e,p,damage,power);break;}
                for(Vec3 mark:e.marks)SetPiecePayload.sendSeeded(e.level,Piece.END_BATTLE_STRIKE,e.castFrom,mark,1,e.boss(),0,seed(move,power,0));
            }
            case IMPACT,STONE,MIND,EMP,SPIRAL -> {
                if(move==EndGeometry.Move.STARS){for(Vec3 mark:e.marks)bolt(e,mark.add(0,12,0),new Vec3(0,-.8,0),power);}
                else for(Vec3 mark:e.marks)impact(e,mark,damage,power,EndGeometry.IMPACT_RADIUS);
            }
            case RING -> {int n=move==EndGeometry.Move.EVENTIDE?3:1;for(int i=0;i<n;i++)e.waves.add(new Wave(e.castFrom,-i*12,damage,power));}
            case SIPHON -> {
                for(ServerPlayer p:e.fighters())for(Vec3 mark:e.marks)if(EndGeometry.segmentDistance(e.castFrom,mark,p.position().add(0,.9,0))<EndGeometry.TETHER_RADIUS&&seen(e.level,e.castFrom,p.getEyePosition())){
                    if(hit(e,p,6,power)){p.setDeltaMovement(e.castFrom.subtract(p.position()).normalize().scale(1.3).add(0,.25,0));p.hurtMarked=true;if("regeneration".equals(powerPath(power)))e.boss().heal(e.boss().getMaxHealth()*.03F);}break;
                }
                for(Vec3 mark:e.marks)SetPiecePayload.sendSeeded(e.level,Piece.END_BATTLE_STRIKE,e.castFrom,mark,1,e.boss(),0,seed(move,power,0));
            }
            case CHARGE -> {e.chargeTicks=EndGeometry.CHARGE_TICKS;e.chargeStep=0;e.chargeHit.clear();}
            case NOVA -> {
                for(ServerPlayer p:e.fighters()){
                    boolean safe=EndGeometry.safeSector(EndGeometry.center(e.data.floor),p.position(),e.safeAngle)||e.marks.stream().anyMatch(mark->mark.distanceToSqr(p.position().add(0,.9,0))<=EndGeometry.SAFE_ORBIT_RADIUS*EndGeometry.SAFE_ORBIT_RADIUS);
                    if(!safe&&seen(e.level,e.castFrom,p.getEyePosition()))hit(e,p,14,power);
                }
                SetPiecePayload.sendSeeded(e.level,Piece.END_BATTLE_NOVA,e.castFrom,e.target,1,e.boss(),0,seed(move,power,0));
            }
            case SEQUENCE -> {int i=0;for(Vec3 mark:e.marks)e.waves.add(new Wave(mark,-(i++%3)*EndGeometry.ECHO_GAP,damage,power,true));}
        }
        CameraShakePayload.sendAround(e.level,e.boss().position(),180,shape==Pattern.NOVA?.4F:.13F,14);
        sound(e,e.boss().position(),shape==Pattern.NOVA?SoundEvents.ENDER_DRAGON_GROWL:SoundEvents.BREEZE_WIND_CHARGE_BURST.value(),.8F,.7F);
    }
    private static void bolt(EndBattle.Encounter e,Vec3 at,Vec3 velocity,int power){
        EndBolt bolt=ModEntities.END_BOLT.get().create(e.level);if(bolt==null)return;bolt.setOwner(e.boss());bolt.setPos(at);bolt.setDeltaMovement(velocity);bolt.accelerationPower=.01;bolt.power(power);e.level.addFreshEntity(bolt);e.bolts.add(bolt);
    }
    static void impact(EndBattle.Encounter e,Vec3 at,float damage,int power,float radius){
        for(ServerPlayer p:e.fighters())if(p.getBoundingBox().distanceToSqr(at)<radius*radius)hit(e,p,damage,power);
        SetPiecePayload.sendSeeded(e.level,Piece.END_BATTLE_IMPACT,at,at,radius,null,0,power+1);
    }
    public static final class Wave {
        final Vec3 at;int age;final float damage;final int power;final boolean bloom;final Set<UUID> hit=new HashSet<>();
        Wave(Vec3 at,int age,float damage,int power){this(at,age,damage,power,false);}
        Wave(Vec3 at,int age,float damage,int power,boolean bloom){this.at=at;this.age=age;this.damage=damage;this.power=power;this.bloom=bloom;}
    }
    public static void tick(EndBattle.Encounter e){
        for(var it=e.waves.iterator();it.hasNext();){var wave=it.next();if(++wave.age<0)continue;
            if(wave.bloom){impact(e,wave.at,wave.damage,wave.power,EndGeometry.IMPACT_RADIUS);it.remove();continue;}
            double radius=wave.age*EndGeometry.WAVE_SPEED;
            if(wave.age==1)SetPiecePayload.sendSeeded(e.level,Piece.END_BATTLE_WAVE,wave.at,wave.at,1,null,0,wave.power+1);
            for(ServerPlayer p:e.fighters())if(!wave.hit.contains(p.getUUID())&&Math.abs(p.position().multiply(1,0,1).distanceTo(wave.at.multiply(1,0,1))-radius)<1.4&&Math.abs(p.getY()-EndGeometry.waveHeight(e.level,p.getX(),p.getZ(),wave.at.y))<EndGeometry.WAVE_HEIGHT){wave.hit.add(p.getUUID());hit(e,p,wave.damage,wave.power);}
            if(radius>EndGeometry.ARENA_RADIUS*2)it.remove();
        }
        if(e.chargeTicks>0){
            LivingEntity boss=e.boss();Vec3 from=boss.position(),wanted=e.chargeFrom.lerp(e.chargeTo,++e.chargeStep/(double)EndGeometry.CHARGE_TICKS);Vec3 delta=wanted.subtract(from);Vec3 end=from;
            for(double f=.1;f<=1.0001;f+=.1){Vec3 at=from.add(delta.scale(f));if(!e.level.noCollision(boss,boss.getBoundingBox().move(at.subtract(from))))break;end=at;}
            boss.setPos(end);boss.setDeltaMovement(Vec3.ZERO);float radius=e.stage==EndBattle.DRAGON?EndGeometry.CHARGE_RADIUS:EndGeometry.SYLAR_CHARGE_RADIUS;
            for(ServerPlayer p:e.fighters())if(!e.chargeHit.contains(p.getUUID())&&EndGeometry.segmentDistance(from.add(0,e.stage==EndBattle.DRAGON?3:1,0),end.add(0,e.stage==EndBattle.DRAGON?3:1,0),p.position().add(0,.9,0))<radius){e.chargeHit.add(p.getUUID());hit(e,p,9+e.phase,e.sampled);}
            SetPiecePayload.send(e.level,Piece.END_BATTLE_DASH,from,end,e.stage==EndBattle.DRAGON?6:1,boss);
            if(--e.chargeTicks==0||end.distanceToSqr(wanted)>.3){e.chargeTicks=0;e.spent=100;e.rest=Math.max(e.rest,90);}
        }
        for(int i=0;i<e.data.anchors.size();i++){
            Vec3 at=e.data.anchors.get(i).add(6,-2,0);
            for(ServerPlayer p:e.fighters())if(p.isShiftKeyDown()&&p.position().multiply(1,0,1).distanceToSqr(at.multiply(1,0,1))<9&&p.getY()<e.data.floor+60){Vec3 velocity=p.getDeltaMovement().multiply(.8,1,.8);p.setDeltaMovement(velocity.x,Math.min(1.2,velocity.y+.18),velocity.z);p.fallDistance=0;p.hurtMarked=true;}
        }
        if(e.prism!=null){if(e.prism.isRemoved())e.breakPrism(null);else if(e.sylar!=null&&e.clock%10==0)e.prism.setBeamTarget(e.sylar.blockPosition().above());}
        EndCrystalFeed.update(e);
    }
    private static final class EndCrystalFeed {
        static void update(EndBattle.Encounter e){
            if(e.stage!=EndBattle.DRAGON||e.clock%10!=0)return;var feeding=e.feed();
            for(var crystal:e.crystals)if(!crystal.isRemoved())crystal.setBeamTarget(crystal==feeding?e.dragon.blockPosition().above(2):null);
        }
    }
    private static void sound(EndBattle.Encounter e,Vec3 at,SoundEvent event,float volume,float pitch){
        for(ServerPlayer p:e.party.values())if(p.level()==e.level)p.playNotifySound(event,SoundSource.HOSTILE,volume,pitch);
    }
    private EndMoves(){}
}
