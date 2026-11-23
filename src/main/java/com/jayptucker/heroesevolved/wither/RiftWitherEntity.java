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
    private final ServerBossEvent bar=new ServerBossEvent(Component.translatable("entity.heroesevolved.rift_wither"),BossEvent.BossBarColor.PURPLE,BossEvent.BossBarOverlay.NOTCHED_10);
    private final Map<UUID,ServerPlayer> party=new LinkedHashMap<>();
    private final Deque<float[]> recent=new ArrayDeque<>();
    final List<Vec3> marks=new ArrayList<>();
    final List<Mob> minions=new ArrayList<>();
    final Set<UUID> waveHit=new HashSet<>();
    WitherGeometry.Move casting;
    int windup,rest=50,transition,stagger,spent,rotation,waveAge=-1;
    int clock,lastHurt=-1000,lastInterrupt=-1000;
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
    }
    public int mode(){return entityData.get(MODE);}
    public int age(){return entityData.get(AGE);}
    public int phase(){return entityData.get(PHASE);}
    public int anchor(){return entityData.get(ANCHOR);}
    public int burnt(){return entityData.get(BURNT);}
    public boolean fighting(){return mode()==2;}
    public boolean dying(){return mode()==3;}
    public UUID killer(){return killer;}
    public void still(boolean value){still=value;}
    public void holdOff(int ticks){rest=Math.max(rest,ticks);}
    public void beginIntro(){entityData.set(MODE,1);entityData.set(AGE,0);}
    public void beginFight(){entityData.set(MODE,2);setHealth(getMaxHealth());}
    public void join(ServerPlayer p) {
        if(party.put(p.getUUID(),p)==null) {
            float fraction=getHealth()/getMaxHealth();
            double maximum=BASE_HEALTH*Math.min(PARTY_MAX,1+PARTY_STEP*(party.size()-1));
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(maximum);setHealth((float)maximum*fraction);
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
        if(clock%10==0)SetPiecePayload.sendSeeded(level,Piece.WITHER_AURA,position(),WitherGeometry.floor(),1,this,0,
                phase()|(burnt()<<2)|((anchor()+1)<<5));
        bar.setProgress(Math.clamp(getHealth()/getMaxHealth(),0,1));
        entityData.set(FINISH,window());
        pollAnchors(level);
        WitherMoves.tickWave(this);
        if(fighters().isEmpty()) {
            if(clock-lastHurt>200&&clock%20==0)heal(getMaxHealth()*.05F);
            return;
        }
        if(position().subtract(WitherGeometry.center()).multiply(1,0,1).lengthSqr()>40*40)setPos(WitherGeometry.center());
        if(transition>0){transition--;return;}
        if(stagger>0){stagger--;return;}
        if(spent>0)spent--;
        if(casting!=null) {
            if(--windup<=0) {
                var move=casting;casting=null;entityData.set(ANCHOR,-1);WitherMoves.land(this,move);
                rest=Math.max(rest,move.rest);if(move==WitherGeometry.Move.SLAM||move==WitherGeometry.Move.LUNGE||move==WitherGeometry.Move.HORIZON)spent=60;
            }
            return;
        }
        if(!still) {
            ServerPlayer foe=fighters().getFirst();
            double height=Math.clamp(foe.getY()+2,WitherGeometry.floor().y+3,WitherGeometry.floor().y+32);
            Vec3 delta=new Vec3(foe.getX(),height,foe.getZ()).subtract(position());
            if(delta.length()>9) {
                Vec3 step=delta.normalize().scale(.12+phase()*.035);
                if(level.noCollision(this,getBoundingBox().move(step)))setPos(position().add(step));
            }
            getLookControl().setLookAt(foe,30,30);
        }
        if(still||--rest>0)return;
        var moves=phase()==1?new WitherGeometry.Move[]{WitherGeometry.Move.VERDICT,WitherGeometry.Move.RAIN,WitherGeometry.Move.PULSE,WitherGeometry.Move.TETHER}
                :phase()==2?new WitherGeometry.Move[]{WitherGeometry.Move.LUNGE,WitherGeometry.Move.RAIN,WitherGeometry.Move.SLAM,WitherGeometry.Move.VERDICT,WitherGeometry.Move.PULSE,WitherGeometry.Move.TETHER}
                :new WitherGeometry.Move[]{WitherGeometry.Move.HORIZON,WitherGeometry.Move.LUNGE,WitherGeometry.Move.RAIN,WitherGeometry.Move.PULSE,WitherGeometry.Move.SLAM,WitherGeometry.Move.VERDICT};
        start(moves[rotation++%moves.length]);
    }
    public void start(WitherGeometry.Move move) {
        if(!fighting()||dying())return;
        casting=move;windup=move.windup;marks.clear();
        for(ServerPlayer p:fighters())marks.add(p.position().add(0,.8,0));
        if(move==WitherGeometry.Move.RAIN||move==WitherGeometry.Move.SLAM||move==WitherGeometry.Move.HORIZON) {
            for(int i=0;i<3;i++)if((burnt()&(1<<((rotation+i)%3)))==0) {entityData.set(ANCHOR,(rotation+i)%3);break;}
        }
        WitherMoves.warn(this,move);
    }
    private void pollAnchors(ServerLevel level) {
        for(int i=0;i<3;i++) {
            BlockPos at=WitherGeometry.ANCHORS.get(i);
            if(!level.hasChunkAt(at))continue;
            if((burnt()&(1<<i))!=0)continue;
            if(!level.getBlockState(at).is(Blocks.TINTED_GLASS)) {
                if(anchor()==i&&casting!=null)backfire(i,null);
                else level.setBlock(at,Blocks.TINTED_GLASS.defaultBlockState(),3);
            }
        }
    }
    public boolean backfire(int index,ServerPlayer player) {
        if(index!=anchor()||casting==null||!fighting()||(burnt()&(1<<index))!=0)return false;
        entityData.set(BURNT,burnt()|1<<index);entityData.set(ANCHOR,-1);casting=null;stagger=60;rest=Math.max(rest,70);
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
        entityData.set(PHASE,next);transition=40;casting=null;entityData.set(ANCHOR,-1);rest=Math.max(rest,60);
        bar.setColor(next==2?BossEvent.BossBarColor.BLUE:BossEvent.BossBarColor.RED);
        bar.setName(Component.translatable("wither.heroesevolved.phase."+next));
        SetPiecePayload.send((ServerLevel)level(),Piece.WITHER_PHASE,WitherGeometry.floor(),position(),next,this);
        SetPiecePayload.send((ServerLevel)level(),Piece.WITHER_CANCEL,position(),position(),1,this);
        CameraShakePayload.sendAround((ServerLevel)level(),position(),48,.35F,30);
        WitherMoves.minions(this,next);
        WitherArena.phase(phase());
    }
    @Override public boolean hurt(DamageSource source,float amount) {
        if(level().isClientSide)return false;
        if(source.is(DamageTypeTags.BYPASSES_INVULNERABILITY))return super.hurt(source,amount);
        if(!fighting()||transition>0||!Float.isFinite(amount)||amount<=0)return false;
        if(source.is(DamageTypeTags.IS_FALL)||source.is(DamageTypes.IN_WALL)||source.is(DamageTypes.DROWN)||source.is(DamageTypes.CRAMMING))return false;
        if(source.getEntity()!=null&&WitherArena.sheltered(source.getEntity()))return false;
        boolean swing=source.getEntity() instanceof Player p&&WitherEvents.fullSwing(p);
        boolean heavy=amount>=8||source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.AbstractArrow a&&a.isCritArrow()||swing;
        if(!bypass) {
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
        if(heavy&&casting!=null&&casting!=WitherGeometry.Move.HORIZON&&clock-lastInterrupt>=100) {
            lastInterrupt=clock;casting=null;entityData.set(ANCHOR,-1);stagger=50;rest=Math.max(rest,60);
            SetPiecePayload.send((ServerLevel)level(),Piece.WITHER_CANCEL,position(),position(),1,this);
        }
        if(lethal)beginDeath(source);else phaseChanged();
        return true;
    }
    private void beginDeath(DamageSource source) {
        killingBlow=source;entityData.set(MODE,3);entityData.set(AGE,0);entityData.set(FINISH,false);
        casting=null;entityData.set(ANCHOR,-1);clearMinions();
        SetPiecePayload.send((ServerLevel)level(),Piece.WITHER_DEATH,position(),position().add(0,2,0),1,this);
        level().playSound(null,blockPosition(),SoundEvents.WITHER_DEATH,SoundSource.HOSTILE,.8F,.7F);
        bar.setName(Component.translatable("wither.heroesevolved.contained"));
    }
    @Override public void die(DamageSource source) {
        boolean reward=fighting()||dying();entityData.set(MODE,4);super.die(source);bar.removeAllPlayers();
        if(reward&&!level().isClientSide)WitherArena.victory(this);
    }
    @Override public void remove(RemovalReason reason) {clearMinions();bar.removeAllPlayers();super.remove(reason);}
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
