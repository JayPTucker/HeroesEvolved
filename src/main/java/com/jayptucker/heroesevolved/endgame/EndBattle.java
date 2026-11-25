package com.jayptucker.heroesevolved.endgame;

import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.mimicry.MimicColors;
import com.jayptucker.heroesevolved.mixin.EndDragonAccess;
import com.jayptucker.heroesevolved.mixin.EndFightAccess;
import com.jayptucker.heroesevolved.network.*;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.registry.*;
import com.jayptucker.heroesevolved.story.sylar.*;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.tags.*;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.*;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** One native End fight and its worldline continuation. Native completion is deferred, never emulated with an egg. */
public final class EndBattle {
    public static final int DRAGON=1,DRAGON_DEATH=2,ABSORPTION=3,SYLAR=4,VICTORY=5,COMPLETE=6;
    private static Encounter current;
    private static boolean rawDragon,releasing;
    private static final Set<UUID> MOVING=new HashSet<>();
    private static final Map<UUID,Long> SWINGS=new HashMap<>();
    private EndBattle(){}
    public static Encounter current(){return current;}
    public static boolean active(){return current!=null&&current.stage<=VICTORY;}
    public static boolean moving(ServerPlayer p){return MOVING.contains(p.getUUID());}
    public static boolean eligible(ServerPlayer p){return p.isAlive()&&!p.isSpectator()&&PlayerProgressionService.getPowerLevel(p)>=EndGeometry.MIN_LEVEL
            &&PlayerAbilityService.getData(p).assignedPower().filter(e->e.getValue().isUnlocked()).isPresent();}
    public static boolean nativeDragon(EnderDragon dragon){
        if(!(dragon.level() instanceof ServerLevel level)||!level.dimension().equals(Level.END)||level.getDragonFight()==null)return false;
        return dragon.getUUID().equals(level.getDragonFight().getDragonUUID());
    }
    public static boolean managed(EnderDragon dragon){
        if(dragon.level().isClientSide){var v=EndClientState.view();return dragon.level().dimension().equals(Level.END)&&v.active()&&v.dragon()==dragon.getId();}
        return current!=null&&current.dragon==dragon&&current.stage<=DRAGON_DEATH;
    }
    public static boolean rawDragon(){return rawDragon;}
    public static Vec3 flyTarget(EnderDragon dragon){return current!=null&&current.dragon==dragon?current.flyTarget():dragon.position();}
    public static boolean grounded(EnderDragon dragon){return dragon.level().isClientSide?EndClientState.view().grounded()>0:current!=null&&current.dragon==dragon&&current.grounded>0;}
    public static boolean holdNativeFight(ServerLevel level){return !releasing&&level.dimension().equals(Level.END)&&EndData.get(level.getServer()).checkpoint==SYLAR&&!EndData.get(level.getServer()).completed;}
    public static boolean hideNativeBar(ServerLevel level){return level.dimension().equals(Level.END)&&(active()||holdNativeFight(level));}
    public static boolean sheltered(Entity e){return current!=null&&e.level()==current.level&&e.position().distanceToSqr(current.data.refuge)<8*8;}
    public static boolean inArena(Entity e){return e.level().dimension().equals(Level.END)&&e.position().multiply(1,0,1).distanceToSqr(new Vec3(.5,0,.5))<=EndGeometry.ARENA_RADIUS*EndGeometry.ARENA_RADIUS;}
    public static void swing(ServerPlayer p){SWINGS.put(p.getUUID(),p.level().getGameTime());}
    public static void unchargedSwing(ServerPlayer p){SWINGS.remove(p.getUUID());}
    private static boolean charged(DamageSource source,float amount){
        boolean full=false;if(source.getEntity() instanceof Player p){Long at=SWINGS.remove(p.getUUID());full=at!=null&&p.level().getGameTime()-at<=1;}
        return full||amount>=8||source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.AbstractArrow arrow&&arrow.isCritArrow();
    }
    /** Explicit entry for preview/test drivers; the gameplay path discovers the native UUID through EndDragonFight. */
    public static Encounter begin(EnderDragon dragon,List<ServerPlayer> players){
        if(current!=null)reset(dragon.level().getServer(),true);
        var level=(ServerLevel)dragon.level();var data=EndData.get(level.getServer());
        if(data.dragon==null||!data.dragon.equals(dragon.getUUID())){
            data.dragon=dragon.getUUID();data.checkpoint=0;data.stage=DRAGON;data.completed=false;data.paid=false;data.pendingXP=0;data.deathTick=0;data.xpTicks.clear();data.participants.clear();data.crystals.clear();
            data.expectedXP=level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOMOBLOOT)?level.getDragonFight()!=null&&!level.getDragonFight().hasPreviouslyKilledDragon()?12000:500:0;
        }
        current=new Encounter(level,data,dragon);for(ServerPlayer p:players)current.join(p);return current;
    }
    private static boolean ensure(EnderDragon dragon,DamageSource source){
        if(managed(dragon))return true;if(!nativeDragon(dragon))return false;
        if(current!=null&&current.dragon==dragon||EndData.get(dragon.getServer()).checkpoint==SYLAR)return false;
        if(source.getEntity() instanceof ServerPlayer p&&eligible(p)){begin(dragon,List.of(p));return true;}
        return false;
    }
    public static boolean dragonHit(EnderDragon dragon,DamageSource source,float incoming){
        if(!ensure(dragon,source))return false;
        return current.hit(dragon,source,incoming,true);
    }
    /** A blow on one of the body's parts: the head is the weak point, the rest takes less (vanilla's quarter is replaced). */
    public static boolean dragonPartHit(EnderDragon dragon,net.minecraft.world.entity.boss.EnderDragonPart part,DamageSource source,float incoming){
        return dragonHit(dragon,source,incoming*(part==dragon.head?EndGeometry.HEAD_DAMAGE:EndGeometry.BODY_DAMAGE));
    }
    public static boolean sylarHit(SylarEntity sylar,DamageSource source,float incoming){return current!=null&&current.sylar==sylar&&current.hit(sylar,source,incoming,false);}
    public static boolean interference(ServerPlayer p){return current!=null&&current.level==p.level()&&current.suppressed.getOrDefault(p.getUUID(),-1000)>current.clock;}
    public static void finishSylar(SylarEntity sylar,ServerPlayer p){
        if(current==null||current.sylar!=sylar||!current.finishWindow())return;
        if(current.phase==3){current.killingBlow=sylar.damageSources().playerAttack(p);current.killer=p.getUUID();current.startVictory();}
        else{sylar.setHealth(sylar.getMaxHealth()*(current.phase==1?.60F:.27F));current.phaseChange();current.stagger=100;}
    }
    public static String mindLine(){return current==null?"An immense, borrowed silence.":current.prism!=null?"The pattern is held outside him, in the echo prism. Break it while he reaches for it.":"He is watching how you move. His next pattern is fixed before it lands.";}
    public static boolean deferDragonCompletion(EnderDragon dragon){
        if(releasing||!nativeDragon(dragon))return false;
        var level=(ServerLevel)dragon.level();var data=EndData.get(level.getServer());
        if(data.completed)return false;
        if(current==null)current=new Encounter(level,data,dragon);
        data.dragon=dragon.getUUID();data.checkpoint=SYLAR;data.stage=ABSORPTION;data.pendingXP=Math.max(data.pendingXP,data.expectedXP);data.changed();
        current.startAbsorption(dragon.position());return true;
    }
    public static void dragonDeathTick(EnderDragon dragon){
        if(!managed(dragon))return;var data=current.data;
        current.stage=DRAGON_DEATH;data.stage=DRAGON_DEATH;data.checkpoint=SYLAR;
        if(dragon.dragonDeathTime<data.deathTick)dragon.dragonDeathTime=data.deathTick;
        data.deathTick=dragon.dragonDeathTime;data.changed();
    }
    public static boolean captureXP(EnderDragon dragon,int amount){
        if(current==null||current.dragon!=dragon)return false;
        if(current.data.xpTicks.add(dragon.dragonDeathTime)){current.data.pendingXP+=amount;current.data.changed();}return true;
    }
    /** Null leaves an ordinary vanilla crystal alone. */
    public static Boolean crystalHit(EndCrystal crystal,DamageSource source,float amount){
        if(current==null||crystal.level()!=current.level)return null;
        if(crystal==current.prism){
            if(source.getEntity() instanceof ServerPlayer p&&(!eligible(p)||sheltered(p)||!inArena(p)))return false;
            if(!Float.isFinite(amount)||amount<=0)return false;
            current.prismHealth-=Math.min(12,amount);if(current.prismHealth<=0)current.breakPrism(source.getEntity() instanceof ServerPlayer p?p:null);return true;
        }
        if(!current.crystals.contains(crystal))return null;
        if(source.is(DamageTypeTags.BYPASSES_INVULNERABILITY))return null;
        if(current.stage!=DRAGON||crystal!=current.feed()||!(source.getEntity() instanceof ServerPlayer p)||!eligible(p)||sheltered(p))return false;
        current.ground(p);return true;
    }
    public static boolean prism(Entity e){return current!=null&&current.prism==e||e.getPersistentData().getBoolean("heroesevolved_end_prism");}
    public static void tick(MinecraftServer server){
        ServerLevel level=server.getLevel(Level.END);if(level==null)return;
        if(current==null){
            EndData data=EndData.get(server);
            List<ServerPlayer> people=level.players().stream().filter(p->eligible(p)&&inArena(p)).toList();
            if(people.isEmpty())return;
            if(data.checkpoint==SYLAR&&!data.completed){current=new Encounter(level,data,null);current.startAbsorption(EndGeometry.center(data.floor).add(0,25,0));for(ServerPlayer p:people)current.join(p);}
            else if(level.getGameTime()%10==0&&level.getDragonFight()!=null){
                Entity e=level.getDragonFight().getDragonUUID()==null?null:level.getEntity(level.getDragonFight().getDragonUUID());
                if(e instanceof EnderDragon dragon&&dragon.isAlive())begin(dragon,people);
            }
        }
        if(current!=null){for(ServerPlayer p:level.players())if(eligible(p)&&inArena(p))current.join(p);current.tick();}
    }
    public static void returnHome(ServerPlayer p){
        var data=EndData.get(p.getServer());var home=data.home(p.getUUID());ServerLevel level=home==null?null:p.getServer().getLevel(home.dimension());
        if(level==null||level.dimension().equals(Level.END))level=p.getServer().overworld();
        Vec3 at=home==null||home.dimension().equals(Level.END)?Vec3.atBottomCenterOf(level.getSharedSpawnPos()):home.at();
        level.getChunk(BlockPos.containing(at));if(!level.noCollision(p,p.getBoundingBox().move(at.subtract(p.position()))))at=Vec3.atBottomCenterOf(level.getSharedSpawnPos());
        if(onEndPortal(level,at)){
            BlockPos origin=BlockPos.containing(at);
            escape:for(int radius=1;radius<=5;radius++)for(int dy=0;dy<=2;dy++)for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
                if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;BlockPos cell=origin.offset(dx,dy,dz);if(!level.hasChunkAt(cell))continue;
                Vec3 candidate=Vec3.atBottomCenterOf(cell).add(0,.02,0);
                if(!onEndPortal(level,candidate)&&!level.getBlockState(cell.below()).getCollisionShape(level,cell.below()).isEmpty()
                        &&level.noCollision(p,p.getBoundingBox().move(candidate.subtract(p.position())))){at=candidate;break escape;}
            }
        }
        place(p,level,at,home==null?p.getYRot():home.yaw(),home==null?0:home.pitch());p.setPortalCooldown();data.forgetHome(p.getUUID());deliver(p);
    }
    private static boolean onEndPortal(ServerLevel level,Vec3 at){BlockPos cell=BlockPos.containing(at);return level.getBlockState(cell).is(net.minecraft.world.level.block.Blocks.END_PORTAL)||level.getBlockState(cell.below()).is(net.minecraft.world.level.block.Blocks.END_PORTAL);}
    public static void place(ServerPlayer p,ServerLevel level,Vec3 at,float yaw,float pitch){
        MOVING.add(p.getUUID());try{p.teleportTo(level,at.x,at.y,at.z,yaw,pitch);p.absMoveTo(at.x,at.y,at.z,yaw,pitch);}finally{MOVING.remove(p.getUUID());}
        p.setDeltaMovement(Vec3.ZERO);p.fallDistance=0;
    }
    public static void rescue(ServerPlayer p){
        ServerLevel level=p.getServer().getLevel(Level.END);if(level==null)return;var data=EndData.get(p.getServer());Vec3 at=data.refuge.add(0,1,4);
        // The temporary refuge may have been restored since victory, or built over during later exploration.
        search:for(int radius=0;radius<=8;radius++)for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
            if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;BlockPos column=BlockPos.containing(at.add(dx,0,dz));level.getChunk(column);
            int y=level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,column.getX(),column.getZ());
            if(y<40||y>level.getMaxBuildHeight()-3)continue;Vec3 candidate=new Vec3(column.getX()+.5,y+.05,column.getZ()+.5);
            if(level.noCollision(p,p.getBoundingBox().move(candidate.subtract(p.position())))){at=candidate;break search;}
        }
        place(p,level,at,p.getYRot(),0);p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOW_FALLING,100));
    }
    public static void deliver(ServerPlayer p){for(ItemStack stack:EndData.get(p.getServer()).take(p.getUUID(),p.registryAccess()))if(!p.getInventory().add(stack))p.drop(stack,false);}
    public static void reset(MinecraftServer server,boolean preserve){
        EndChannel.cancelAll();if(current!=null){current.cleanup();current=null;}
        ServerLevel level=server.getLevel(Level.END);EndData data=EndData.get(server);
        if(level!=null){if(!data.scenery.isEmpty()){EndScenery.hold(level);EndScenery.restore(level,data);}EndScenery.release(level);}
        if(!preserve){data.stage=0;data.checkpoint=0;data.completed=false;data.dragon=null;data.pendingXP=0;data.xpTicks.clear();data.participants.clear();}
        data.changed();SWINGS.clear();MOVING.clear();
    }
    public static final class Encounter {
        public final ServerLevel level;public final EndData data;
        public final EnderDragon dragon;public SylarEntity sylar;public final DragonBrain brain;
        public final Map<UUID,ServerPlayer> party=new LinkedHashMap<>();
        final EndDamage damage=new EndDamage();final ServerBossEvent bar=new ServerBossEvent(Component.literal("The Living Seal"),BossEvent.BossBarColor.PURPLE,BossEvent.BossBarOverlay.NOTCHED_10);
        final List<EndCrystal> crystals=new ArrayList<>();EndCrystal prism;float prismHealth;
        final List<Vec3> marks=new ArrayList<>();final List<EndMoves.Wave> waves=new ArrayList<>();final List<EndBolt> bolts=new ArrayList<>();
        final Map<UUID,Integer> suppressed=new HashMap<>();final Map<ItemEntity,UUID> prizes=new LinkedHashMap<>();
        public int stage=DRAGON,phase=1,clock,stageAge,grounded,stagger,spent,transition,rest=140,active,rotation,windup,sampled=-1,empty,lastHurt=-1000,lastInterrupt=-1000;
        int crystalCursor,chargeTicks,chargeStep,closeIn=-1;boolean bypass,still;
        public final float[] charge=new float[4];final int[] altarCooldown=new int[4];
        public EndGeometry.Move casting;public Vec3 castFrom=Vec3.ZERO,target=Vec3.ZERO,deathAt=Vec3.ZERO;float safeAngle;
        final Set<UUID> chargeHit=new HashSet<>();Vec3 chargeFrom=Vec3.ZERO,chargeTo=Vec3.ZERO;
        DamageSource killingBlow;UUID killer;
        Encounter(ServerLevel level,EndData data,EnderDragon dragon){
            this.level=level;this.data=data;this.dragon=dragon;brain=dragon==null?null:new DragonBrain(this);EndScenery.hold(level);EndScenery.build(level,data);
            if(dragon!=null){
                stage=DRAGON;data.stage=DRAGON;dragon.setNoAi(false);dragon.setSilent(false);dragon.getPhaseManager().setPhase(EnderDragonPhase.HOVERING);
                damage.scale(dragon,EndGeometry.DRAGON_HEALTH,1);dragon.setHealth(dragon.getMaxHealth());dragon.setCustomName(Component.translatable("endgame.heroesevolved.dragon.1"));
                discoverCrystals();
            }else{stage=ABSORPTION;data.stage=ABSORPTION;}
            updateBar();data.changed();
        }
        public LivingEntity boss(){return stage<=DRAGON_DEATH?dragon:sylar;}
        public void still(boolean value){still=value;if(boss()!=null)boss().setDeltaMovement(Vec3.ZERO);}
        public void holdOff(int ticks){rest=Math.max(rest,ticks);}
        public List<ServerPlayer> fighters(){return party.values().stream().filter(p->eligible(p)&&!p.isRemoved()&&p.level()==level&&inArena(p)&&!sheltered(p)).toList();}
        public void join(ServerPlayer p){
            if(!eligible(p)||p.level()!=level)return;
            boolean added=party.put(p.getUUID(),p)==null;data.participants.add(p.getUUID());bar.addPlayer(p);
            if(added){if(boss()!=null&&stage!=ABSORPTION&&stage!=VICTORY)damage.scale(boss(),stage<=DRAGON_DEATH?EndGeometry.DRAGON_HEALTH:EndGeometry.SYLAR_HEALTH,party.size());
                SylarVoice.narrate(p,"endgame.heroesevolved.arrive");data.changed();}
        }
        public EndCrystal feed(){
            if(crystals.isEmpty())return null;
            for(int i=0;i<crystals.size();i++){EndCrystal c=crystals.get((crystalCursor+i)%crystals.size());if(!c.isRemoved())return c;}return null;
        }
        void discoverCrystals(){
            // A player can arrive before the pillar chunks become entity-ticking. Discover their crystals as they wake.
            for(var spike:net.minecraft.world.level.levelgen.feature.SpikeFeature.getSpikesForLevel(level))
                for(EndCrystal crystal:level.getEntitiesOfClass(EndCrystal.class,spike.getTopBoundingBox(),c->!prism(c)))
                    if(!crystals.contains(crystal)){crystals.add(crystal);if(data.crystals.stream().noneMatch(at->at.distanceToSqr(crystal.position())<1))data.crystals.add(crystal.position());}
            if(crystalCursor==0)crystals.sort(Comparator.comparingDouble(c->Math.atan2(c.getZ(),c.getX())));data.changed();
        }
        public boolean canChannel(int index){return (stage==DRAGON||stage==SYLAR)&&index>=0&&index<data.anchors.size()&&clock>=altarCooldown[index]&&(stage==SYLAR||index==active)&&transition==0;}
        public void overloaded(int index,ServerPlayer p){
            if(!canChannel(index))return;altarCooldown[index]=clock+200;charge[index]=0;
            if(stage==DRAGON)ground(p);else{cancelCast();stagger=60;spent=100;roomDamage(.06F,p);active=(index+1)%4;
                SetPiecePayload.send(level,Piece.END_BATTLE_BACKFIRE,data.anchors.get(index),sylar.getEyePosition(),1,sylar);}
        }
        void ground(ServerPlayer p){
            if(stage!=DRAGON)return;EndCrystal old=feed();if(old!=null)old.discard();crystalCursor++;active=(active+1)%4;
            cancelCast();brain.crash();grounded=EndGeometry.GROUND_TICKS;stagger=EndGeometry.CRASH_STUN;spent=100;dragon.nearestCrystal=null;
            roomDamage(.05F,p);SetPiecePayload.send(level,Piece.END_BATTLE_GROUNDED,dragon.position(),EndGeometry.center(data.floor),1,dragon);
            ServerPlayer heard=p!=null?p:party.values().stream().findFirst().orElse(null);
            if(heard!=null)SylarVoice.narrate(heard,"endgame.heroesevolved.grounded");
        }
        void roomDamage(float fraction,ServerPlayer p){
            LivingEntity boss=boss();if(boss==null||!boss.isAlive())return;
            ServerPlayer credit=p!=null?p:fighters().stream().findFirst().orElse(null);
            bypass=true;boss.invulnerableTime=0;try{hit(boss,credit==null?boss.damageSources().magic():boss.damageSources().indirectMagic(boss,credit),damage.effective()*fraction,stage==DRAGON);}finally{bypass=false;}
        }
        boolean hit(LivingEntity boss,DamageSource source,float incoming,boolean isDragon){
            if(stage!=(isDragon?DRAGON:SYLAR)||transition>0||boss==null||!Float.isFinite(incoming)||incoming<=0)return false;
            if(source.getEntity() instanceof ServerPlayer p){if(!eligible(p)||!inArena(p)||sheltered(p)||p.level()!=level)return false;join(p);}
            if(source.is(DamageTypeTags.IS_FALL)||source.is(DamageTypes.IN_WALL)||source.is(DamageTypes.DROWN)||source.is(DamageTypes.CRAMMING))return false;
            boolean heavy=charged(source,incoming);
            float bonus=bypass?1:stagger>0?1.35F:spent>0?1.5F:1;
            if(isDragon&&grounded==0&&!bypass)bonus*=EndGeometry.AIR_DAMAGE;
            float amount=damage.amount(boss,source,incoming,clock,bonus,bypass);
            if(source.is(DamageTypeTags.BYPASSES_INVULNERABILITY))amount=incoming;
            if(amount<=0)return false;
            boolean lethal=amount>=boss.getHealth();float actual=Math.min(amount,Math.max(0,boss.getHealth()-1));
            boolean landed;
            if(isDragon){rawDragon=true;try{landed=actual<=0||((EndDragonAccess)boss).heroes$reallyHurt(source,lethal?amount:actual);}finally{rawDragon=false;}}
            else landed=actual<=0||((SylarEntity)boss).inheritanceDamage(source,actual);
            if(!landed)return false;
            if(!bypass)damage.paid(clock,amount);lastHurt=clock;
            if(source.getEntity() instanceof Player p)killer=p.getUUID();
            if(heavy&&casting!=null&&EndMoves.shape(EndMoves.seed(casting,sampled,0))!=EndMoves.Pattern.NOVA&&(!isDragon||DragonBrain.interruptible(casting))&&clock-lastInterrupt>=100){
                lastInterrupt=clock;cancelCast();stagger=50;rest=Math.max(rest,60);
                if(isDragon)level.playSound(null,boss.getX(),boss.getY(),boss.getZ(),SoundEvents.ENDER_DRAGON_HURT,SoundSource.HOSTILE,5,.6F);
            }
            if(lethal){killingBlow=source;if(isDragon){stage=DRAGON_DEATH;data.stage=DRAGON_DEATH;data.checkpoint=SYLAR;data.changed();cancelCast();clearHazards();dragon.setHealth(1);dragon.getPhaseManager().setPhase(EnderDragonPhase.DYING);}else startVictory();}
            else phaseChange();return true;
        }
        void phaseChange(){
            if(boss()==null)return;float fraction=boss().getHealth()/boss().getMaxHealth();int next=fraction>2/3F?1:fraction>1/3F?2:3;
            if(next<=phase)return;phase=next;transition=EndGeometry.TRANSITION_TICKS;cancelCast();rest=Math.max(rest,80);EndScenery.phase(level,data,phase);
            SetPiecePayload.send(level,Piece.END_BATTLE_PHASE,EndGeometry.center(data.floor),boss().position(),stage<=2?phase:phase+3,boss());
            CameraShakePayload.sendAround(level,boss().position(),180,.32F,28);updateBar();if(stage==DRAGON&&brain!=null)brain.phaseChange();
            for(ServerPlayer p:party.values())SylarVoice.narrate(p,"endgame.heroesevolved.phase."+(stage<=2?"dragon.":"sylar.")+phase);
        }
        public boolean finishWindow(){
            if(stage!=SYLAR||transition>0||sylar==null)return false;float fraction=sylar.getHealth()/sylar.getMaxHealth();
            return phase==1?fraction>2/3F&&fraction<=2/3F+.06F:phase==2?fraction>1/3F&&fraction<=1/3F+.06F:fraction<=.1F;
        }
        public void start(EndGeometry.Move move){
            if(boss()==null||stage!=DRAGON&&stage!=SYLAR)return;cancelCast();casting=move;windup=EndMoves.windup(move,sampled);marks.clear();
            castFrom=stage==DRAGON?dragon.head.getBoundingBox().getCenter():sylar.getEyePosition().add(0,-.15,0);boss().setDeltaMovement(Vec3.ZERO);
            EndMoves.warn(this,move);
        }
        void cancelCast(){
            if(casting!=null&&boss()!=null)SetPiecePayload.send(level,Piece.END_BATTLE_CANCEL,boss().position(),boss().position(),1,boss());
            casting=null;windup=0;chargeTicks=0;if(brain!=null&&stage==DRAGON)brain.cancel();if(sylar!=null)sylar.inheritancePose(phase,SylarEntity.Pose.NONE,false,false,finishWindow());
        }
        Vec3 flyTarget(){return brain==null?null:brain.flyTarget();}
        void spawnPrism(){
            if(prism!=null)prism.discard();prism=EntityType.END_CRYSTAL.create(level);if(prism==null)return;
            Vec3 at=data.anchors.get(Math.floorMod(rotation,data.anchors.size())).add(0,.4,0);prism.setPos(at);prism.setShowBottom(false);
            prism.getPersistentData().putBoolean("heroesevolved_end_prism",true);prism.setCustomName(Component.translatable("endgame.heroesevolved.prism",EndMoves.powerName(sampled)));prism.setCustomNameVisible(true);
            prismHealth=12;level.addFreshEntity(prism);
            for(ServerPlayer p:party.values())SylarVoice.say(p,"endgame.heroesevolved.read."+EndMoves.powerPath(sampled));
        }
        public void breakPrism(ServerPlayer p){
            if(prism==null)return;Vec3 at=prism.position();prism.discard();prism=null;cancelCast();stagger=80;spent=100;roomDamage(.08F,p);
            SetPiecePayload.send(level,Piece.END_BATTLE_BACKFIRE,at,sylar.getEyePosition(),1,sylar);sampled=-1;rest=Math.max(rest,70);
        }
        void startAbsorption(Vec3 at){
            if(stage==ABSORPTION&&sylar!=null||stage==SYLAR||stage==VICTORY)return;
            cancelCast();clearHazards();stage=ABSORPTION;stageAge=0;deathAt=at;data.stage=ABSORPTION;data.checkpoint=SYLAR;data.pendingXP=Math.max(data.pendingXP,data.expectedXP);data.changed();
            sylar=ModEntities.SYLAR.get().create(level);if(sylar!=null){sylar.moveTo(EndGeometry.center(data.floor).add(0,8,8));sylar.beginInheritance();level.addFreshEntity(sylar);}
            SetPiecePayload.send(level,Piece.END_BATTLE_ABSORB,at,sylar==null?EndGeometry.center(data.floor).add(0,10,8):sylar.position(),1,sylar);
            for(ServerPlayer p:party.values()){award(p,"false_victory");SylarVoice.narrate(p,"endgame.heroesevolved.false_victory");}
            updateBar();
        }
        void beginSylar(){
            stage=SYLAR;stageAge=0;phase=1;data.stage=SYLAR;data.checkpoint=SYLAR;data.changed();rest=100;transition=30;stagger=0;
            if(sylar==null||sylar.isRemoved()){sylar=ModEntities.SYLAR.get().create(level);sylar.moveTo(EndGeometry.center(data.floor).add(0,2,0));sylar.beginInheritance();level.addFreshEntity(sylar);}
            damage.clear();damage.scale(sylar,EndGeometry.SYLAR_HEALTH,Math.max(1,party.size()));sylar.setHealth(sylar.getMaxHealth());
            sylar.inheritancePose(phase,SylarEntity.Pose.ONE_HAND,false,false,false);updateBar();
            for(ServerPlayer p:party.values())SylarVoice.say(p,"endgame.heroesevolved.inheritance");
        }
        void startVictory(){
            if(stage==VICTORY||stage==COMPLETE)return;cancelCast();clearHazards();stage=VICTORY;stageAge=0;data.stage=VICTORY;data.changed();if(prism!=null){prism.discard();prism=null;}
            sylar.inheritancePose(phase,SylarEntity.Pose.RUPTURE,false,false,false);sylar.setDeltaMovement(Vec3.ZERO);
            for(ServerPlayer p:party.values())SylarVoice.sayNow(p,"endgame.heroesevolved.rupture");
            SetPiecePayload.send(level,Piece.END_BATTLE_RELEASE,sylar.position(),EndGeometry.center(data.floor),1,sylar);updateBar();broadcast();
        }
        void tick(){
            clock++;stageAge++;if(stage==COMPLETE){if(closeIn>0&&--closeIn==0){cleanup();current=null;}return;}
            for(ServerPlayer p:party.values())if(p.isAlive()&&p.level()==level){
                if(p.getY()<data.floor-24)rescue(p);
                if(sheltered(p)&&p.isShiftKeyDown()&&p.position().distanceToSqr(data.refuge.add(0,0,-3))<9)returnHome(p);
                if(p.getY()>data.floor+EndGeometry.FLIGHT_HEIGHT+4&&inArena(p)){place(p,level,new Vec3(p.getX(),data.floor+EndGeometry.FLIGHT_HEIGHT-2,p.getZ()),p.getYRot(),p.getXRot());}
            }
            if(clock%5==0){broadcast();for(ServerPlayer p:party.values())if(p.level()==level)bar.setProgress(boss()==null?0:Math.clamp(boss().getHealth()/boss().getMaxHealth(),0,1));}
            if(clock%10==0){
                if(boss()!=null)SetPiecePayload.send(level,Piece.END_BATTLE_FIELD,EndGeometry.center(data.floor),boss().position(),phase,boss());
                SetPiecePayload.send(level,Piece.END_BATTLE_REFUGE,data.refuge.add(0,1.6,-3),data.refuge.add(0,1.6,-2),1,null);
            }
            if(fighters().isEmpty())empty++;else empty=0;
            if(empty>=400){abandon();return;}
            if(stage==ABSORPTION){
                if(sylar==null) {stage=DRAGON_DEATH;startAbsorption(deathAt);}
                if(stageAge==100)for(ServerPlayer p:party.values())SylarVoice.say(p,"endgame.heroesevolved.arrival."+(SylarData.get(level.getServer()).wins(p.getUUID())>0?"remembered":"new"));
                if(stageAge>=EndGeometry.ABSORPTION_TICKS)beginSylar();return;
            }
            if(stage==VICTORY){
                if(stageAge==EndGeometry.RUPTURE_TICK){
                    CameraShakePayload.sendAround(level,sylar.position(),220,.6F,40);
                    for(ServerPlayer p:party.values())if(p.level()==level)p.playNotifySound(SoundEvents.GENERIC_EXPLODE.value(),SoundSource.HOSTILE,1.2F,.55F);
                }
                if(stageAge>=EndGeometry.DEATH_TICKS)complete();return;
            }
            if(stage==DRAGON_DEATH)return;
            LivingEntity boss=boss();
            if(boss!=null&&boss.isRemoved()&&boss.getRemovalReason()!=null&&!boss.getRemovalReason().shouldDestroy()){abandon();return;}
            if(boss==null||!boss.isAlive()){if(stage==SYLAR)startVictory();return;}
            if(!inArena(boss)||boss.getY()<data.floor-8||boss.getY()>data.floor+EndGeometry.FLIGHT_HEIGHT){
                boss.setPos(EndGeometry.center(data.floor).add(0,stage==DRAGON?24:4,0));boss.setDeltaMovement(Vec3.ZERO);cancelCast();rest=Math.max(rest,60);
                if(stage==DRAGON){brain.cancelAll();grounded=0;}
            }
            phaseChange();if(stage==DRAGON&&clock<=100&&clock%10==0)discoverCrystals();EndMoves.tick(this);
            if(stage==DRAGON)brain.tick();
            if(clock%20==0&&clock-lastHurt>=200&&fighters().isEmpty())boss.heal(boss.getMaxHealth()*.05F);
            if(transition>0){transition--;return;}if(stagger>0){stagger--;return;}if(spent>0)spent--;
            if(casting!=null){if(--windup<=0){var move=casting;casting=null;EndMoves.land(this,move);rest=Math.max(rest,stage==DRAGON?brain.rest(move):move.rest);if(move==EndGeometry.Move.UNMAKING||move==EndGeometry.Move.ENDINGS||move==EndGeometry.Move.STONE)spent=100;}return;}
            if(chargeTicks>0)return;
            if(stage==DRAGON&&brain.busy())return;
            if(stage==SYLAR){
                sylar.inheritancePose(phase,SylarEntity.Pose.NONE,false,false,finishWindow());
                if(!still&&!fighters().isEmpty()){
                    ServerPlayer p=fighters().getFirst();Vec3 desired=p.position().add(p.position().subtract(sylar.position()).normalize().scale(-9));desired=new Vec3(desired.x,Math.clamp(p.getY()+1,data.floor+1,data.floor+60),desired.z);
                    Vec3 step=desired.subtract(sylar.position());if(step.length()>1)step=step.normalize().scale(.26+phase*.07);
                    if(level.noCollision(sylar,sylar.getBoundingBox().move(step)))sylar.setPos(sylar.position().add(step));sylar.getLookControl().setLookAt(p,30,30);
                }
            }
            if(still||fighters().isEmpty()||--rest>0)return;
            if(stage==DRAGON){var next=brain.next();if(next==null)rest=6;else start(next);return;}
            var moves=EndMoves.rotation(stage,phase,sampled>=0);var next=moves[rotation++%moves.length];
            if(next==EndGeometry.Move.PATTERN&&(sampled<0||prism==null))next=EndGeometry.Move.NEEDLE;
            start(next);
        }
        public void broadcast(){var packet=new EndBattlePayload(view());for(ServerPlayer p:level.players())if(p.position().lengthSqr()<400*400)PacketDistributor.sendToPlayer(p,packet);for(ServerPlayer p:party.values())if(!level.players().contains(p)&&p.level()==level)PacketDistributor.sendToPlayer(p,packet);}
        public EndView view(){EndCrystal feed=feed();return new EndView(stage,phase,clock,stageAge,boss()==null?-1:boss().getId(),dragon==null?-1:dragon.getId(),active,crystals.size()-(int)crystals.stream().filter(c->!c.isRemoved()).count(),casting==null?-1:casting.ordinal(),windup,grounded,sampled,charge[Math.clamp(active,0,3)],safeAngle,data.floor,castFrom,target,data.refuge,feed==null?Vec3.ZERO:feed.position(),List.copyOf(data.anchors),List.copyOf(marks));}
        void updateBar(){bar.setName(Component.translatable("endgame.heroesevolved."+(stage<=2?"dragon.":stage==3?"absorption":stage==5?"release":stage==6?"complete":"sylar.")+(stage==3||stage>=5?"":phase)));bar.setColor(stage==VICTORY?BossEvent.BossBarColor.WHITE:phase==1?BossEvent.BossBarColor.PURPLE:phase==2?BossEvent.BossBarColor.BLUE:BossEvent.BossBarColor.RED);}
        void abandon(){
            boolean checkpoint=data.checkpoint==SYLAR;
            cleanup();data.stage=checkpoint?SYLAR:0;data.changed();current=null;
        }
        void prize(ItemStack stack,UUID id){var item=new ItemEntity(level,sylar.getX(),sylar.getY()+1,sylar.getZ(),stack);item.setTarget(id);item.setPickUpDelay(10);level.addFreshEntity(item);prizes.put(item,id);}
        void complete(){
            if(data.completed)return;stage=COMPLETE;data.stage=COMPLETE;data.completed=true;data.checkpoint=0;data.changed();
            if(sylar!=null){sylar.setHealth(0);sylar.inheritanceDeath(killingBlow==null?sylar.damageSources().generic():killingBlow);}
            var fight=level.getDragonFight();if(fight!=null&&data.dragon!=null){EnderDragon nativeBody=dragon;if(nativeBody==null){nativeBody=EntityType.ENDER_DRAGON.create(level);nativeBody.setUUID(data.dragon);}releasing=true;try{fight.setDragonKilled(nativeBody);}finally{releasing=false;}}
            if(!data.paid){data.paid=true;ExperienceOrb.award(level,EndGeometry.center(data.floor).add(0,2,0),data.pendingXP);data.pendingXP=0;data.changed();}
            for(UUID id:data.participants){prize(new ItemStack(ModItems.INHERITANCE_CORE.get()),id);if(data.win(id))prize(new ItemStack(ModItems.ANCHOR_OF_RETURN.get()),id);else if(level.random.nextFloat()<.1F)prize(new ItemStack(ModItems.MUSIC_DISC_INHERITANCE.get()),id);ServerPlayer p=party.get(id);if(p!=null){award(p,"inheritance");SylarVoice.narrate(p,"endgame.heroesevolved.won");}}
            closeIn=120;bar.setProgress(0);updateBar();broadcast();
        }
        void cleanup(){
            EndChannel.cancelAll();cancelCast();clearHazards();
            if(prism!=null)prism.discard();if(sylar!=null)sylar.discard();
            for(var entry:prizes.entrySet())if(!entry.getKey().isRemoved()&&!entry.getKey().getItem().isEmpty()){data.stash(entry.getValue(),entry.getKey().getItem(),level.registryAccess());entry.getKey().discard();}prizes.clear();
            for(ServerPlayer p:party.values())if(p.isAlive())deliver(p);
            if(dragon!=null&&dragon.isAlive()&&stage==DRAGON){
                dragon.setHealth(dragon.getMaxHealth());dragon.getPhaseManager().setPhase(EnderDragonPhase.HOLDING_PATTERN);
                for(Vec3 pos:data.crystals)if(crystals.stream().noneMatch(c->!c.isRemoved()&&c.position().distanceToSqr(pos)<4)){var c=EntityType.END_CRYSTAL.create(level);c.setPos(pos);level.addFreshEntity(c);}
            }
            bar.removeAllPlayers();EndScenery.restore(level,data);EndScenery.release(level);data.changed();
            for(ServerPlayer p:level.players())PacketDistributor.sendToPlayer(p,new EndBattlePayload(EndView.EMPTY));
        }
        void clearHazards(){for(EndBolt bolt:List.copyOf(bolts))bolt.discard();bolts.clear();waves.clear();suppressed.clear();if(brain!=null){brain.clearPools();brain.cancelAll();}}
    }
    private static void award(ServerPlayer p,String id){var a=p.getServer().getAdvancements().get(ResourceLocation.fromNamespaceAndPath("heroesevolved",id));if(a!=null)p.getAdvancements().award(a,"observed");}
}
