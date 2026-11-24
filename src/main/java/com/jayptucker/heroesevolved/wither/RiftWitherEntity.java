package com.jayptucker.heroesevolved.wither;

import com.jayptucker.heroesevolved.combat.Takedowns;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** The cage is an actor at the summoning site and a three-phase boss inside its breach. */
public final class RiftWitherEntity extends Monster implements Takedowns.PhaseFinishable {
    public static final double BASE_HEALTH=440, PARTY_STEP=.5, PARTY_MAX=3;
    public static final float HIT_CAP=.06F, SECOND_CAP=.04F, BACKFIRE=.1F;
    private static final EntityDataAccessor<Integer> MODE=SynchedEntityData.defineId(RiftWitherEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> AGE=SynchedEntityData.defineId(RiftWitherEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PHASE=SynchedEntityData.defineId(RiftWitherEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ANCHOR=SynchedEntityData.defineId(RiftWitherEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> BURNT=SynchedEntityData.defineId(RiftWitherEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> FINISH=SynchedEntityData.defineId(RiftWitherEntity.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> EXPOSED=SynchedEntityData.defineId(RiftWitherEntity.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> ACTION=SynchedEntityData.defineId(RiftWitherEntity.class,EntityDataSerializers.INT),ACTION_AGE=SynchedEntityData.defineId(RiftWitherEntity.class,EntityDataSerializers.INT),STANCE=SynchedEntityData.defineId(RiftWitherEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> AIM_X=SynchedEntityData.defineId(RiftWitherEntity.class,EntityDataSerializers.FLOAT),AIM_Y=SynchedEntityData.defineId(RiftWitherEntity.class,EntityDataSerializers.FLOAT),AIM_Z=SynchedEntityData.defineId(RiftWitherEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> SIDE_HEAD=SynchedEntityData.defineId(RiftWitherEntity.class,EntityDataSerializers.INT),SIDE_AGE=SynchedEntityData.defineId(RiftWitherEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SIDE_X=SynchedEntityData.defineId(RiftWitherEntity.class,EntityDataSerializers.FLOAT),SIDE_Y=SynchedEntityData.defineId(RiftWitherEntity.class,EntityDataSerializers.FLOAT),SIDE_Z=SynchedEntityData.defineId(RiftWitherEntity.class,EntityDataSerializers.FLOAT);
    private final ServerBossEvent bar=new ServerBossEvent(Component.translatable("entity.heroesevolved.rift_wither"),BossEvent.BossBarColor.PURPLE,BossEvent.BossBarOverlay.NOTCHED_10);
    private final Map<UUID,ServerPlayer> party=new LinkedHashMap<>();
    private final Deque<float[]> recent=new ArrayDeque<>();
    final List<Vec3> marks=new ArrayList<>();
    final List<Vec3> origins=new ArrayList<>();
    final List<RiftSkull> skulls=new ArrayList<>();
    final Deque<WitherGeometry.Move> chain=new ArrayDeque<>();
    final Set<UUID> motionHit=new HashSet<>();
    final List<Mob> minions=new ArrayList<>();
    final Set<UUID> waveHit=new HashSet<>();
    WitherGeometry.Move casting;
    int windup,rest=50,transition,stagger,spent,rotation,waveAge=-1;
    int clock,lastHurt=-1000,lastInterrupt=-1000;
    int recovery,feedTicks,motionTicks,motionStep,lastStance=-1000,attacksLanded;
    int nextHeadShot;Vec3 sideOrigin=Vec3.ZERO,sideTarget=Vec3.ZERO;
    float lockedYaw,effectiveHealth=(float)BASE_HEALTH;
    Vec3 motionFrom=Vec3.ZERO,motionTo=Vec3.ZERO,velocity=Vec3.ZERO;
    WitherGeometry.Move motion;
    private UUID focus;
    boolean bypass,still;
    Vec3 castFrom=Vec3.ZERO,waveCenter=Vec3.ZERO;
    private DamageSource killingBlow;
    private UUID killer;

    public RiftWitherEntity(EntityType<? extends RiftWitherEntity> type,Level level) {
        super(type,level);setPersistenceRequired();setNoGravity(true);xpReward=120;
    }
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,BASE_HEALTH).add(Attributes.MOVEMENT_SPEED,.26)
                .add(Attributes.KNOCKBACK_RESISTANCE,1).add(Attributes.ARMOR,4).add(Attributes.FOLLOW_RANGE,64);
    }
    @Override protected void registerGoals() {}
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);builder.define(MODE,0);builder.define(AGE,0);builder.define(PHASE,1);
        builder.define(ANCHOR,-1);builder.define(BURNT,0);builder.define(FINISH,false);
        builder.define(EXPOSED,false);
        builder.define(ACTION,-1);builder.define(ACTION_AGE,0);builder.define(STANCE,0);builder.define(AIM_X,0F);builder.define(AIM_Y,0F);builder.define(AIM_Z,0F);
        builder.define(SIDE_HEAD,-1);builder.define(SIDE_AGE,100);builder.define(SIDE_X,0F);builder.define(SIDE_Y,0F);builder.define(SIDE_Z,0F);
    }
    public int mode(){return entityData.get(MODE);}
    public int age(){return entityData.get(AGE);}
    public int phase(){return entityData.get(PHASE);}
    public int anchor(){return entityData.get(ANCHOR);}
    public int burnt(){return entityData.get(BURNT);}
    public boolean fighting(){return mode()==2;}
    public boolean dying(){return mode()==3;}
    public UUID killer(){return killer;}
    public WitherGeometry.Move action(){int id=entityData.get(ACTION);return id<0?null:WitherGeometry.Move.values()[id];}
    public int actionAge(){return entityData.get(ACTION_AGE);}
    public int stance(){return entityData.get(STANCE);}
    public boolean exposed(){return entityData.get(EXPOSED);}
    public Vec3 aim(){return new Vec3(entityData.get(AIM_X),entityData.get(AIM_Y),entityData.get(AIM_Z));}
    public int sideHead(){return entityData.get(SIDE_HEAD);}
    public int sideAge(){return entityData.get(SIDE_AGE);}
    public Vec3 sideAim(){return new Vec3(entityData.get(SIDE_X),entityData.get(SIDE_Y),entityData.get(SIDE_Z));}
    public Vec3 core(){return WitherGeometry.core(position());}
    public float modelScale(){return mode()==1?1.5F:WitherGeometry.ARENA_SCALE;}
    public ServerPlayer focus(){var people=fighters();return people.stream().filter(p->p.getUUID().equals(focus)).findFirst().orElse(people.isEmpty()?null:people.getFirst());}
    public int attacksLanded(){return attacksLanded;}
    public void still(boolean value){still=value;}
    public void holdOff(int ticks){rest=Math.max(rest,ticks);}
    public void beginIntro(){entityData.set(MODE,1);entityData.set(AGE,0);}
    public void beginFight(){entityData.set(MODE,2);refreshDimensions();setHealth(getMaxHealth());rest=45;}
    @Override public EntityDimensions getDefaultDimensions(Pose pose){return EntityDimensions.scalable(mode()==1?4.6F:5.6F,mode()==1?5:WitherGeometry.HEIGHT);}
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key){super.onSyncedDataUpdated(key);if(key.equals(MODE))refreshDimensions();}
    public void join(ServerPlayer p) {
        if(party.put(p.getUUID(),p)==null) {
            float fraction=getHealth()/getMaxHealth();
            double maximum=BASE_HEALTH*Math.min(PARTY_MAX,1+PARTY_STEP*(party.size()-1));
            effectiveHealth=(float)maximum;getAttribute(Attributes.MAX_HEALTH).setBaseValue(Math.min(1000,maximum));setHealth(getMaxHealth()*fraction);
        }
        bar.addPlayer(p);bar.setProgress(getHealth()/getMaxHealth());
    }
    public List<ServerPlayer> fighters() {
        return party.values().stream().filter(p->p.isAlive()&&!p.isRemoved()&&p.level()==level()&&!p.isSpectator()&&!WitherArena.sheltered(p)).toList();
    }
    @Override public void tick() {
        super.tick();setDeltaMovement(Vec3.ZERO);fallDistance=0;
        if(!(level() instanceof ServerLevel level))return;
        clock++;
        if(mode()==1) {
            int age=age()+1;entityData.set(AGE,age);
            if(age==1) {
                SetPiecePayload.send(level,Piece.WITHER_FORM,position(),position().add(0,1.8,0),1,this);
                level.playSound(null,blockPosition(),SoundEvents.WITHER_SPAWN,SoundSource.HOSTILE,.8F,.55F);
            }
            if(age==45)CameraShakePayload.sendAround(level,position(),32,.25F,40);
            if(age>=WitherGeometry.INTRO_TICKS)WitherArena.swallowed(this);
            return;
        }
        if(dying()) {
            entityData.set(AGE,age()+1);
            if(age()>=WitherGeometry.DEATH_TICKS) {setHealth(0);die(killingBlow==null?damageSources().generic():killingBlow);}
            return;
        }
        if(!fighting())return;
        if(sideHead()>=0){entityData.set(SIDE_AGE,sideAge()+1);if(sideAge()==WitherGeometry.HEAD_WINDUP)WitherMoves.sideShot(this,sideOrigin,sideTarget);if(sideAge()>30)entityData.set(SIDE_HEAD,-1);}
        entityData.set(EXPOSED,stagger>0||spent>0);
        entityData.set(ACTION_AGE,entityData.get(ACTION_AGE)+1);
        if(casting==null&&motionTicks==0&&recovery==0&&action()!=null&&actionAge()>action().windup+28)entityData.set(ACTION,-1);
        if(clock%10==0)SetPiecePayload.sendSeeded(level,Piece.WITHER_AURA,position(),WitherGeometry.floor(),1,this,0,
                phase()|(burnt()<<2)|((anchor()+1)<<5));
        bar.setProgress(Math.clamp(getHealth()/getMaxHealth(),0,1));
        entityData.set(FINISH,window());
        pollAnchors(level);
        if(feedTicks>0&&--feedTicks==0)entityData.set(ANCHOR,-1);
        WitherMoves.tickWave(this);
        skulls.removeIf(Entity::isRemoved);
        if(fighters().isEmpty()) {
            if(clock-lastHurt>200&&clock%20==0)heal(getMaxHealth()*.05F);
            return;
        }
        if(position().subtract(WitherGeometry.center()).multiply(1,0,1).lengthSqr()>40*40||getY()<WitherGeometry.floor().y-2||getY()>WitherGeometry.floor().y+35){setPos(WitherGeometry.floor().add(0,.3,0));velocity=Vec3.ZERO;cancelChain();}
        if(transition>0){transition--;return;}
        if(stagger>0){stagger--;return;}
        if(spent>0)spent--;
        if(stance()>0&&clock-lastStance>45&&clock%20==0)entityData.set(STANCE,stance()-1);
        if(motionTicks>0){WitherMoves.tickMotion(this);return;}
        if(casting!=null) {
            WitherMoves.windup(this,casting);
            if(--windup<=0) {
                var move=casting;casting=null;WitherMoves.land(this,move);attacksLanded++;
                recovery=move==WitherGeometry.Move.HORIZON?24:move==WitherGeometry.Move.VERDICT?10:8;
                rest=Math.max(rest,move.rest);
                if(chain.isEmpty()){spent=70;rest=Math.max(rest,phase()==1?40:phase()==2?32:26);}
            }
            return;
        }
        if(recovery>0){recovery--;return;}
        if(!still)advance(level);
        if(still||--rest>0)return;
        if(chain.isEmpty()){
            var people=fighters();ServerPlayer foe=people.get(Math.floorMod(rotation,people.size()));focus=foe.getUUID();
            chain.addAll(List.of(WitherGeometry.chain(phase(),rotation++,distanceTo(foe)>17)));
        }
        start(chain.removeFirst());
    }
    private void advance(ServerLevel level){
        ServerPlayer foe=focus();if(foe==null)return;
        Vec3 toward=foe.position().subtract(position());double height=Math.clamp(foe.getY()-1.3,WitherGeometry.floor().y+.3,WitherGeometry.floor().y+30);
        Vec3 side=new Vec3(-toward.z,0,toward.x).normalize().scale((rotation%2==0?1:-1)*2);
        Vec3 desired=new Vec3(foe.getX(),height,foe.getZ()).subtract(toward.multiply(1,0,1).normalize().scale(6.5)).add(side);
        Vec3 delta=desired.subtract(position());double speed=(distanceTo(foe)>18?.64:.34)+phase()*.045;
        speed*=Math.clamp(getAttributeValue(Attributes.MOVEMENT_SPEED)/.26,.2,1.6);
        Vec3 wanted=delta.length()<.7?Vec3.ZERO:delta.normalize().scale(Math.min(speed,delta.length()));velocity=velocity.lerp(wanted,.18);
        Vec3 next=position().add(velocity);if(level.noCollision(this,getBoundingBox().move(velocity)))setPos(next);else velocity=Vec3.ZERO;
        face(foe.position().add(0,1,0));
    }
    private void face(Vec3 at){Vec3 d=at.subtract(position());float yaw=(float)Math.toDegrees(Math.atan2(-d.x,d.z));setYRot(yaw);setYHeadRot(yaw);yBodyRot=yaw;}
    void cancelChain(){
        casting=null;chain.clear();motionTicks=0;motion=null;recovery=0;velocity=Vec3.ZERO;entityData.set(ACTION,-1);entityData.set(SIDE_HEAD,-1);clearHazards();
        SetPiecePayload.send((ServerLevel)level(),Piece.WITHER_CANCEL,position(),position(),1,this);
    }
    private void feed(){
        if(anchor()>=0||burnt()==7)return;ServerPlayer p=focus();if(p==null)return;
        int closest=-1;double distance=Double.MAX_VALUE;for(int i=0;i<3;i++)if((burnt()&(1<<i))==0){double d=p.position().distanceToSqr(Vec3.atCenterOf(WitherGeometry.ANCHORS.get(i)));if(d<distance){distance=d;closest=i;}}
        entityData.set(ANCHOR,closest);feedTicks=WitherGeometry.FEED_TICKS;
    }
    public void start(WitherGeometry.Move move) {
        if(!fighting()||dying())return;
        casting=move;windup=move.windup;marks.clear();origins.clear();motionTicks=0;motion=null;recovery=0;velocity=Vec3.ZERO;
        for(ServerPlayer p:fighters())marks.add(p.position().add(0,.8,0));
        ServerPlayer foe=focus();Vec3 aim=foe==null?position().add(getLookAngle().scale(10)):foe.position().add(0,.8,0);face(aim);lockedYaw=getYRot();
        entityData.set(AIM_X,(float)aim.x);entityData.set(AIM_Y,(float)aim.y);entityData.set(AIM_Z,(float)aim.z);entityData.set(ACTION,move.ordinal());entityData.set(ACTION_AGE,0);
        if(move==WitherGeometry.Move.RAIN||move==WitherGeometry.Move.SLAM||move==WitherGeometry.Move.HORIZON||move==WitherGeometry.Move.RAKE)feed();
        WitherMoves.warn(this,move);
        if(!still&&clock>=nextHeadShot&&(move==WitherGeometry.Move.RAIN||move==WitherGeometry.Move.TETHER||move==WitherGeometry.Move.PULSE)){
            var people=fighters();if(!people.isEmpty()){ServerPlayer other=people.get(Math.floorMod(rotation+1,people.size()));startSideShot(rotation%2+1,other.position().add(0,.8,0));nextHeadShot=clock+(phase()==1?110:phase()==2?95:80);}
        }
    }
    public void startSideShot(int head,Vec3 target){
        if(!fighting()||head<1||head>2)return;sideOrigin=WitherGeometry.mouth(position(),yBodyRot,head,target);sideTarget=target;
        entityData.set(SIDE_HEAD,head);entityData.set(SIDE_AGE,0);entityData.set(SIDE_X,(float)target.x);entityData.set(SIDE_Y,(float)target.y);entityData.set(SIDE_Z,(float)target.z);
        SetPiecePayload.send((ServerLevel)level(),Piece.WITHER_HEAD_WARN,sideOrigin,target,1,this);
    }
    private void pollAnchors(ServerLevel level) {
        for(int i=0;i<3;i++) {
            BlockPos at=WitherGeometry.ANCHORS.get(i);
            if(!level.hasChunkAt(at))continue;
            if((burnt()&(1<<i))!=0)continue;
            if(!level.getBlockState(at).is(Blocks.TINTED_GLASS)) {
                if(anchor()==i)backfire(i,null);
                else level.setBlock(at,Blocks.TINTED_GLASS.defaultBlockState(),3);
            }
        }
    }
    public boolean backfire(int index,ServerPlayer player) {
        if(index!=anchor()||!fighting()||(burnt()&(1<<index))!=0)return false;
        entityData.set(BURNT,burnt()|1<<index);entityData.set(ANCHOR,-1);feedTicks=0;cancelChain();entityData.set(STANCE,0);stagger=60;spent=100;rest=Math.max(rest,70);
        var at=Vec3.atCenterOf(WitherGeometry.ANCHORS.get(index));
        level().setBlock(WitherGeometry.ANCHORS.get(index),Blocks.AIR.defaultBlockState(),3);
        SetPiecePayload.send((ServerLevel)level(),Piece.WITHER_BACKFIRE,at,position().add(0,2,0),1,this);
        SetPiecePayload.send((ServerLevel)level(),Piece.WITHER_CANCEL,position(),position(),1,this);
        bypass=true;
        ServerPlayer credit=player!=null?player:fighters().stream().min(Comparator.comparingDouble(p->p.position().distanceToSqr(at))).orElse(null);
        invulnerableTime=0;
        try {hurt(credit==null?damageSources().magic():damageSources().indirectMagic(this,credit),getMaxHealth()*BACKFIRE);}
        finally {bypass=false;}
        return true;
    }
    private void phaseChanged() {
        int next=getHealth()/getMaxHealth()>2/3F?1:getHealth()/getMaxHealth()>1/3F?2:3;
        if(next<=phase()||dying())return;
        entityData.set(PHASE,next);transition=40;cancelChain();entityData.set(ANCHOR,-1);feedTicks=0;rest=Math.max(rest,30);entityData.set(STANCE,0);
        bar.setColor(next==2?BossEvent.BossBarColor.BLUE:BossEvent.BossBarColor.RED);
        bar.setName(Component.translatable("wither.heroesevolved.phase."+next));
        SetPiecePayload.send((ServerLevel)level(),Piece.WITHER_PHASE,WitherGeometry.floor(),position(),next,this);
        SetPiecePayload.send((ServerLevel)level(),Piece.WITHER_CANCEL,position(),position(),1,this);
        CameraShakePayload.sendAround((ServerLevel)level(),position(),48,.35F,30);
        WitherMoves.minions(this,next);
        WitherArena.phase(phase());
    }
    public void operatorPhase(){if(fighting()&&phase()<3){setHealth(getMaxHealth()*(phase()==1?.65F:.32F));phaseChanged();}}
    @Override public boolean hurt(DamageSource source,float amount) {
        if(level().isClientSide)return false;
        if(source.is(DamageTypeTags.BYPASSES_INVULNERABILITY))return super.hurt(source,amount);
        if(!fighting()||transition>0||!Float.isFinite(amount)||amount<=0)return false;
        if(source.is(DamageTypeTags.IS_FALL)||source.is(DamageTypes.IN_WALL)||source.is(DamageTypes.DROWN)||source.is(DamageTypes.CRAMMING))return false;
        if(source.getEntity()!=null&&WitherArena.sheltered(source.getEntity()))return false;
        boolean swing=source.getEntity() instanceof Player p&&WitherEvents.fullSwing(p);
        boolean heavy=amount>=8||source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.AbstractArrow a&&a.isCritArrow()||swing;
        if(!bypass) {
            amount/=Math.max(1,effectiveHealth/getMaxHealth());
            if(source.getEntity() instanceof Player p)amount*=p.distanceTo(this)<=48?1:Math.clamp((80-p.distanceTo(this))/32,0,1);
            else amount=Math.min(3,amount);
            if(stagger>0)amount*=1.25F;else if(spent>0)amount*=1.5F;
            amount=Math.min(amount,Math.max(10,getMaxHealth()*HIT_CAP));
            while(!recent.isEmpty()&&clock-recent.peekFirst()[0]>=20)recent.removeFirst();
            if(!recent.isEmpty()) {
                float total=0;for(float[] hit:recent)total+=hit[1];
                amount=Math.min(amount,Math.max(0,getMaxHealth()*SECOND_CAP-total));
            }
            if(amount<=0)return false;
        }
        boolean lethal=amount>=getHealth();
        if(lethal)amount=Math.max(0,getHealth()-1);
        boolean landed=amount<=0||super.hurt(source,amount);if(!landed)return false;
        lastHurt=clock;if(!bypass)recent.add(new float[]{clock,amount});
        if(source.getEntity() instanceof Player p)killer=p.getUUID();
        if(heavy&&casting!=null&&casting!=WitherGeometry.Move.HORIZON&&clock-lastInterrupt>=100&&clock-lastStance>=8) {
            lastStance=clock;entityData.set(STANCE,stance()+1);
            if(stance()>=WitherGeometry.STANCE_HITS){lastInterrupt=clock;cancelChain();entityData.set(STANCE,0);stagger=50;spent=80;rest=Math.max(rest,45);}
        }
        if(lethal)beginDeath(source);else phaseChanged();
        return true;
    }
    private void beginDeath(DamageSource source) {
        killingBlow=source;entityData.set(MODE,3);entityData.set(AGE,0);entityData.set(FINISH,false);
        cancelChain();entityData.set(ANCHOR,-1);clearMinions();clearHazards();
        SetPiecePayload.send((ServerLevel)level(),Piece.WITHER_DEATH,position(),position().add(0,2,0),1,this);
        level().playSound(null,blockPosition(),SoundEvents.WITHER_DEATH,SoundSource.HOSTILE,.8F,.7F);
        bar.setName(Component.translatable("wither.heroesevolved.contained"));
    }
    @Override public void die(DamageSource source) {
        boolean reward=fighting()||dying();entityData.set(MODE,4);super.die(source);bar.removeAllPlayers();
        if(reward&&!level().isClientSide)WitherArena.victory(this);
    }
    @Override public void remove(RemovalReason reason) {clearMinions();clearHazards();bar.removeAllPlayers();super.remove(reason);}
    private void clearHazards(){for(var skull:List.copyOf(skulls))skull.discard();skulls.clear();waveAge=-1;}
    private void clearMinions(){for(Mob minion:List.copyOf(minions))minion.discard();minions.clear();}
    private boolean window() {
        if(!fighting()||transition>0)return false;
        float fraction=getHealth()/getMaxHealth();
        return phase()==1?fraction<=2/3F+.06F&&fraction>2/3F:phase()==2?fraction<=1/3F+.06F&&fraction>1/3F:fraction<=.1F;
    }
    @Override public boolean finishWindow(){return entityData.get(FINISH);}
    @Override public void phaseFinished(ServerPlayer p) {
        if(!window())return;
        if(phase()==3){killer=p.getUUID();beginDeath(damageSources().playerAttack(p));return;}
        setHealth(Math.max(1,getMaxHealth()*(phase()==1?2/3F:1/3F)-getMaxHealth()*.06F));
        phaseChanged();stagger=100;
    }
}
