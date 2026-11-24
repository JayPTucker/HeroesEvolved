package com.jayptucker.heroesevolved.wither;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.registry.ModEntities;
import com.jayptucker.heroesevolved.registry.ModItems;
import com.jayptucker.heroesevolved.structure.Blueprint;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.Connection;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;
import java.lang.reflect.Field;
import java.util.*;

/** One cage per world: each integration test has its own batch and cleans up before and after using it. */
@GameTestHolder("heroesevolved") @PrefixGameTestTemplate(false)
public final class WitherGameTests {
    private static final List<ServerPlayer> LIVE=new ArrayList<>();
    private static void clean(GameTestHelper h){
        WitherArena.reset(h.getLevel().getServer(),false);
        for(ServerPlayer p:LIVE){p.discard();if(p.connection.getConnection().channel()!=null)p.connection.getConnection().channel().close();}
        LIVE.clear();h.getLevel().getServer().overworld().getDataStorage().set("heroesevolved_wither",new WitherData());
    }
    private static ServerPlayer player(GameTestHelper h,int level){
        return player(h,level,false);
    }
    private static ServerPlayer player(GameTestHelper h,int level,boolean real){
        GameProfile profile=new GameProfile(UUID.randomUUID(),"CageTester");
        // FakePlayer.die deliberately does nothing: death/drop integration needs an actual ServerPlayer.
        ServerPlayer p=real?new ServerPlayer(h.getLevel().getServer(),h.getLevel(),profile,ClientInformation.createDefault()):new FakePlayer(h.getLevel(),profile){
            @Override public boolean isInvulnerableTo(DamageSource s){return false;}
            @Override public void teleportTo(ServerLevel l,double x,double y,double z,float yaw,float pitch){super.teleportTo(l,x,y,z,yaw,pitch);absMoveTo(x,y,z,yaw,pitch);}
        };
        try{
            Field grace=ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");grace.setAccessible(true);grace.setInt(p,0);
            Connection connection=p.connection==null?new Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND):p.connection.getConnection();
            Field channel=Connection.class.getDeclaredField("channel");channel.setAccessible(true);if(connection.channel()==null)channel.set(connection,new EmbeddedChannel());
            if(p.connection==null)p.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(p.getServer(),connection,p,
                    net.minecraft.server.network.CommonListenerCookie.createInitial(profile,false)){
                @Override public void send(net.minecraft.network.protocol.Packet<?> packet){}
            };
        }catch(ReflectiveOperationException error){throw new AssertionError(error);}
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(4,2,4))));p.setGameMode(GameType.SURVIVAL);
        p.setData(ModDataAttachments.PLAYER_ABILITIES,PlayerAbilityData.empty().replaceWithUnlockedPower(ModAbilities.PYROKINESIS_ID));
        PlayerProgressionService.setPowerLevel(p,level);LIVE.add(p);return p;
    }
    private static void ready(GameTestHelper h){
        WitherData.get(h.getLevel().getServer()).source(new WitherData.Home(h.getLevel().dimension(),Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(5,2,5))),0,0));
        WitherArena.tick(h.getLevel().getServer());
    }
    private static RiftWitherEntity fight(GameTestHelper h,ServerPlayer p){ready(h);h.assertTrue(WitherArena.enter(p),"Eligible solo fighter enters");RiftWitherEntity b=WitherArena.boss();b.still(true);b.holdOff(10000);return b;}
    private static void done(GameTestHelper h){clean(h);h.succeed();}
    @GameTest(template="rewind_empty",batch="wither_01",timeoutTicks=200)
    public static void nativeFormationBecomesBreachWithoutExplosion(GameTestHelper h){
        clean(h);ServerLevel level=h.getLevel();BlockPos base=h.absolutePos(new BlockPos(5,2,5));
        level.setBlock(base.below(),Blocks.DIAMOND_BLOCK.defaultBlockState(),3);
        level.setBlock(base,Blocks.SOUL_SAND.defaultBlockState(),3);
        for(int x=-1;x<=1;x++){
            level.setBlock(base.offset(x,1,0),Blocks.SOUL_SAND.defaultBlockState(),3);
            level.setBlock(base.offset(x,2,0),Blocks.WITHER_SKELETON_SKULL.defaultBlockState(),3);
        }
        BlockPos last=base.offset(1,2,0);
        WitherSkullBlock.checkSpawn(level,last,(net.minecraft.world.level.block.entity.SkullBlockEntity)level.getBlockEntity(last));
        h.assertTrue(level.getBlockState(base).isAir()&&level.getBlockState(last).isAir(),"Real T-shaped summoning consumed its blocks");
        h.assertTrue(level.getEntitiesOfClass(WitherBoss.class,new AABB(base).inflate(8)).isEmpty(),"Native forming Wither intercepted before explosion");
        h.runAtTickTime(140,()->{try{h.assertTrue(WitherArena.ready(),"Six-second folding sequence leaves usable breach");h.assertTrue(level.getBlockState(base.below()).is(Blocks.DIAMOND_BLOCK),"Summoning terrain survives");h.assertTrue(WitherData.get(level.getServer()).source!=null,"Entrance persisted");}finally{clean(h);}h.succeed();});
    }
    @GameTest(template="rewind_empty",batch="wither_02")
    public static void levelGateSoloAndExactReturn(GameTestHelper h){clean(h);try{
        ServerPlayer p=player(h,15);Vec3 home=p.position();ready(h);h.assertTrue(!WitherArena.enter(p),"Level 15 refused");PlayerProgressionService.setPowerLevel(p,16);
        h.assertTrue(WitherArena.enter(p)&&WitherArena.inside(p),"Level 16 solo entry");WitherArena.sendHome(p);h.assertTrue(p.level()==h.getLevel()&&p.position().distanceTo(home)<.01,"Exact dimensional return");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="wither_03")
    public static void arenaMarkersAndFlightVolume(GameTestHelper h){clean(h);try{
        var bp=Blueprint.load("wither_cage");ServerPlayer p=player(h,16);fight(h,p);
        for(int i=0;i<3;i++)h.assertTrue(bp.markerOffsets("anchor:"+i).contains(WitherGeometry.ANCHORS.get(i).subtract(WitherGeometry.ORIGIN)),"Anchor marker matches shared geometry");
        ServerLevel cage=p.serverLevel();
        for(int y=6;y<=44;y+=2)for(int x=-10;x<=10;x+=2)for(int z=-10;z<=10;z+=2)
            h.assertTrue(cage.getBlockState(WitherGeometry.ORIGIN.offset(x,y,z)).isAir(),"Central aerial dogfight volume stays clear");
        p.setPos(WitherGeometry.floor().add(20,35,0));WitherArena.tick(p.getServer());h.assertTrue(p.getY()>WitherGeometry.floor().y+34,"Flight at 35 blocks is not leashed to floor");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="wither_04")
    public static void lateJoinScalesWithoutHealingAndCapsBurst(GameTestHelper h){clean(h);try{
        ServerPlayer p=player(h,16);var b=fight(h,p);b.setHealth(220);ServerPlayer q=player(h,18);h.assertTrue(WitherArena.enter(q),"Late join allowed");
        h.assertTrue(b.getMaxHealth()==660&&Math.abs(b.getHealth()/b.getMaxHealth()-.5)<.001,"Party scaling preserves fraction");
        p.setPos(b.position().add(0,0,6));float health=b.getHealth();b.hurt(b.damageSources().playerAttack(p),1000);h.assertTrue(health-b.getHealth()<=b.getMaxHealth()*.061,"One huge hit cannot skip boss");
        float after=b.getHealth();for(int i=0;i<20;i++){b.invulnerableTime=0;b.hurt(b.damageSources().playerAttack(p),1000);}h.assertTrue(after-b.getHealth()<1,"Burst budget is shared across hits");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="wither_05")
    public static void markedLaneCanBeDodgedAndReachesFlight(GameTestHelper h){clean(h);try{
        ServerPlayer p=player(h,16);var b=fight(h,p);b.setPos(WitherGeometry.floor().add(0,24,0));p.setPos(b.position().add(0,0,14));
        b.start(WitherGeometry.Move.VERDICT);var fixed=List.copyOf(b.marks);float health=p.getHealth();p.setPos(p.position().add(8,0,0));
        for(int i=0;i<28;i++)b.tick();h.assertTrue(p.getHealth()==health&&b.marks.equals(fixed),"Moving out of fixed air lanes beats attack");
        p.setPos(fixed.get(1).add(0,-.8,0));b.start(WitherGeometry.Move.VERDICT);for(int i=0;i<28;i++)b.tick();h.assertTrue(p.getHealth()<health,"Lane hits airborne target on warning");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="wither_06")
    public static void anchorsBackfireOnceAndForcePhase(GameTestHelper h){clean(h);try{
        ServerPlayer p=player(h,16);var b=fight(h,p);ServerLevel l=(ServerLevel)b.level();
        l.setBlock(WitherGeometry.ANCHORS.get(0),Blocks.AIR.defaultBlockState(),3);b.tick();h.assertTrue(l.getBlockState(WitherGeometry.ANCHORS.get(0)).is(Blocks.TINTED_GLASS),"Inactive anchor repairs");
        b.start(WitherGeometry.Move.RAIN);int index=b.anchor();float hp=b.getHealth();l.setBlock(WitherGeometry.ANCHORS.get(index),Blocks.AIR.defaultBlockState(),3);b.tick();
        h.assertTrue(b.getHealth()<hp-b.getMaxHealth()*.085&&b.stagger>0,"Lit anchor backfires and exposes core");h.assertTrue(!b.backfire(index,p),"Spent anchor cannot be farmed");
        b.setHealth(b.getMaxHealth()*.65F);b.invulnerableTime=0;b.hurt(b.damageSources().playerAttack(p),1);h.assertTrue(b.phase()==2&&b.transition>0,"Threshold changes phase");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="wither_07")
    public static void solidCoverStopsNovaAndDoorwayCannotSnipe(GameTestHelper h){clean(h);try{
        ServerPlayer p=player(h,16);var b=fight(h,p);ServerLevel l=(ServerLevel)b.level();b.setPos(WitherGeometry.floor().add(0,1,0));p.setPos(WitherGeometry.floor().add(0,0,9));
        for(int x=-3;x<=3;x++)for(int y=0;y<8;y++)l.setBlock(WitherGeometry.ORIGIN.offset(x,y+3,5),Blocks.BLACKSTONE.defaultBlockState(),3);
        float health=p.getHealth();b.start(WitherGeometry.Move.HORIZON);for(int i=0;i<WitherGeometry.Move.HORIZON.windup;i++)b.tick();h.assertTrue(p.getHealth()==health,"Real cover blocks the released star discharge");
        p.setPos(WitherGeometry.exit());float hp=b.getHealth();h.assertTrue(!b.hurt(b.damageSources().playerAttack(p),20)&&b.getHealth()==hp,"Return alcove cannot be used to snipe");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="wither_08")
    public static void heavyInterruptAndPhaseFinishers(GameTestHelper h){clean(h);try{
        ServerPlayer p=player(h,16);var b=fight(h,p);p.setPos(b.position().add(0,0,6));b.start(WitherGeometry.Move.TETHER);
        for(int i=0;i<3;i++){b.clock+=8;WitherEvents.swing(p);b.invulnerableTime=0;b.hurt(b.damageSources().playerAttack(p),4);}
        h.assertTrue(b.casting==null&&b.stagger>0,"Three charged hits crack the stance and interrupt the chain");
        b.setHealth(b.getMaxHealth()*.7F);b.phaseFinished(p);h.assertTrue(b.phase()==2&&b.stagger==100,"Phase finisher earns long stagger");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="wither_09")
    public static void retreatResetsAbandonedAttempt(GameTestHelper h){clean(h);try{
        ServerPlayer p=player(h,16);var b=fight(h,p);WitherArena.sendHome(p);for(int i=0;i<401;i++)WitherArena.tick(p.getServer());
        h.assertTrue(WitherArena.boss()==null&&WitherArena.ready(),"Abandoned attempt resets but entrance remains");h.assertTrue(WitherArena.enter(p)&&WitherArena.boss()!=b,"A fresh solo attempt can follow retreat");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="wither_10")
    public static void deathSceneRewardsOnceAndReturns(GameTestHelper h){clean(h);try{
        ServerPlayer p=player(h,16);var b=fight(h,p);b.setHealth(1);p.setPos(b.position().add(0,0,5));b.hurt(b.damageSources().playerAttack(p),3);
        h.assertTrue(b.dying(),"Lethal blow starts death scene");for(int i=0;i<59;i++)b.tick();h.assertTrue(b.dying(),"Reward waits for final collapse");b.tick();
        h.assertTrue(!b.dying(),"Death scene ends once");for(int i=0;i<101;i++)WitherArena.tick(p.getServer());
        h.assertTrue(!WitherArena.inside(p)&&!WitherArena.busy(p.getServer()),"Victory returns fighter and closes breach");
        h.assertTrue(p.getInventory().countItem(Items.NETHER_STAR)==1&&p.getInventory().countItem(ModItems.FRACTURED_STAR.get())==1,"One Nether Star and participant fragment survive reset");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="wither_11")
    public static void savedReturnsAndDropsSurviveReload(GameTestHelper h){
        var data=new WitherData();UUID id=UUID.randomUUID();var home=new WitherData.Home(h.getLevel().dimension(),new Vec3(100,72,-20),35,-10);data.source(home);data.home(id,home);data.stash(id,new ItemStack(Items.DIAMOND,4),h.getLevel().registryAccess());data.win(id);
        var loaded=WitherData.load(data.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());
        h.assertTrue(loaded.source.equals(home)&&loaded.home(id).equals(home),"Entrance and exact home survive NBT reload");h.assertTrue(loaded.take(id,h.getLevel().registryAccess()).getFirst().getCount()==4&&!loaded.win(id),"Items and first-victory record survive restart");h.assertTrue(loaded.take(id,h.getLevel().registryAccess()).isEmpty(),"Stash pays only once");h.succeed();
    }
    @GameTest(template="rewind_empty",batch="wither_12")
    public static void secondSummoningRefundsInsteadOfEscaping(GameTestHelper h){
        clean(h);ready(h);Vec3 at=Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(5,2,5)));
        WitherBoss duplicate=EntityType.WITHER.create(h.getLevel());duplicate.moveTo(at);duplicate.makeInvulnerable();
        h.assertTrue(!h.getLevel().addFreshEntity(duplicate),"Second summoning cannot create unsafe vanilla boss");
        h.runAtTickTime(3,()->{try{
            var drops=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new AABB(BlockPos.containing(at)).inflate(8));
            h.assertTrue(drops.stream().filter(e->e.getItem().is(Items.SOUL_SAND)).mapToInt(e->e.getItem().getCount()).sum()==4,"Soul body refunded");
            h.assertTrue(drops.stream().filter(e->e.getItem().is(Items.WITHER_SKELETON_SKULL)).mapToInt(e->e.getItem().getCount()).sum()==3,"Three skulls refunded");
        }finally{clean(h);}h.succeed();});
    }
    @GameTest(template="rewind_empty",batch="wither_13")
    public static void powerAnswersAndOwnedChunkTickets(GameTestHelper h){clean(h);
        ServerLevel cage=h.getLevel().getServer().getLevel(WitherGeometry.DIMENSION);cage.setChunkForced(-3,-3,true);
        ServerPlayer p=null;
        try{
            p=player(h,16);var b=fight(h,p);
            h.assertTrue(!com.jayptucker.heroesevolved.cryo.CryoService.freezeSolid(b,120,p)&&!com.jayptucker.heroesevolved.cryo.CryoService.isFrozen(b),"Boss chills instead of being frozen solid");
            h.assertTrue(com.jayptucker.heroesevolved.phasing.DensityService.immovable(b),"Boss is not embedded or lifted");
            p.setPos(WitherGeometry.floor().add(0,2,12));p.setYRot(0);p.setXRot(0);
            com.jayptucker.heroesevolved.energy.PlayerEnergyService.restore(p,999);
            h.assertTrue(com.jayptucker.heroesevolved.speed.SpeedWindArms.start(p),"Wind Arms can answer skull rain");
            var skull=ModEntities.RIFT_SKULL.get().create(cage);skull.setOwner(b);skull.setPos(p.getEyePosition().add(0,0,5));skull.setDeltaMovement(0,0,-1);
            h.assertTrue(com.jayptucker.heroesevolved.speed.SpeedWindArms.catchShot(cage,skull,skull.position(),skull.position().add(0,0,-1))&&skull.getOwner()==p,"Real boss skull can be redirected by Wind Arms");skull.discard();
            WitherArena.reset(p.getServer(),false);h.assertTrue(cage.getForcedChunks().contains(net.minecraft.world.level.ChunkPos.asLong(-3,-3)),"Operator-owned ticket survives encounter cleanup");
        }finally{if(p!=null)com.jayptucker.heroesevolved.speed.SpeedWindArms.stop(p);clean(h);cage.setChunkForced(-3,-3,false);}h.succeed();
    }
    @GameTest(template="rewind_empty",batch="wither_14")
    public static void actualDeathDropsAreReturnedOnce(GameTestHelper h){clean(h);
        boolean keep=h.getLevel().getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_KEEPINVENTORY);
        try{
            ServerPlayer p=player(h,16,true);fight(h,p);p.serverLevel().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_KEEPINVENTORY).set(false,p.getServer());
            p.hasChangedDimension(); // Stand in for the client's completed dimension-transfer acknowledgement.
            p.getInventory().add(new ItemStack(Items.DIAMOND,5));p.hurt(p.damageSources().genericKill(),1000);
            h.assertTrue(!p.isAlive(),"Fighter actually died");
            var returned=WitherData.get(p.getServer()).take(p.getUUID(),p.registryAccess());
            h.assertTrue(returned.stream().filter(s->s.is(Items.DIAMOND)).mapToInt(ItemStack::getCount).sum()==5,"Real death event saved belongings before arena reset");
            h.assertTrue(WitherData.get(p.getServer()).take(p.getUUID(),p.registryAccess()).isEmpty(),"Death stash cannot pay twice");
        }finally{h.getLevel().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_KEEPINVENTORY).set(keep,h.getLevel().getServer());clean(h);}h.succeed();
    }
    @GameTest(template="rewind_empty",batch="wither_15",timeoutTicks=180)
    public static void skullImpactHonorsCircularWarning(GameTestHelper h){clean(h);
        ServerPlayer p=player(h,16);var boss=fight(h,p);Vec3 impact=WitherGeometry.floor().add(0,.8,20);
        p.setPos(impact.add(2.1,-.8,2.1));
        h.runAtTickTime(100,()->{try{
            float before=p.getHealth();RiftSkull first=ModEntities.RIFT_SKULL.get().create(p.level());first.setOwner(boss);first.setPos(impact);
            first.onHit(new BlockHitResult(impact,net.minecraft.core.Direction.UP,BlockPos.containing(impact),false));
            h.assertTrue(p.getHealth()==before,"Corner of broad-phase box is outside circular rain warning");
            p.setPos(impact.add(0,-.8,0));p.invulnerableTime=0;
            RiftSkull second=ModEntities.RIFT_SKULL.get().create(p.level());second.setOwner(boss);second.setPos(impact);
            second.onHit(new BlockHitResult(impact,net.minecraft.core.Direction.UP,BlockPos.containing(impact),false));
            h.assertTrue(p.getHealth()<before,"Standing inside impact circle is hit");
        }finally{clean(h);}h.succeed();});
    }
    @GameTest(template="rewind_empty",batch="wither_16")
    public static void lungeFollowsItsMarkedAirLane(GameTestHelper h){clean(h);try{
        ServerPlayer p=player(h,16);var b=fight(h,p);b.setPos(WitherGeometry.floor().add(0,25,0));p.setPos(b.position().add(0,0,14));
        float health=p.getHealth();b.start(WitherGeometry.Move.LUNGE);Vec3 mark=b.marks.getFirst(),from=b.castFrom;
        for(int i=0;i<WitherGeometry.Move.LUNGE.windup+WitherGeometry.LUNGE_TICKS;i++)b.tick();
        h.assertTrue(b.position().add(0,3,0).distanceTo(mark)<.5,"Actual swept lunge follows fixed warning endpoint");
        h.assertTrue(b.getY()>WitherGeometry.floor().y+20&&p.getHealth()<health,"Lunge engages a flying opponent without dropping to ground");
        h.assertTrue(Math.abs(from.y-mark.y)<3,"Warning commits to the opponent's aerial body height");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="wither_17")
    public static void activeBossClosesDistanceAndChainsAttacks(GameTestHelper h){clean(h);try{
        var p=player(h,16);var b=fight(h,p);b.still(false);b.rest=0;p.setPos(WitherGeometry.floor().add(0,0,27));double initial=b.distanceTo(p),closest=initial;
        for(int i=0;i<600;i++){p.setHealth(20);b.tick();closest=Math.min(closest,b.distanceTo(p));}
        h.assertTrue(closest<initial-8,"Active pursuit closes a meaningful part of the arena");h.assertTrue(b.attacksLanded()>=8,"Thirty seconds contains multiple complete attack chains");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="wither_18")
    public static void phaseChainsChangeTheirJobsAndBodyHasBossScale(GameTestHelper h){clean(h);try{
        var b=fight(h,player(h,16));h.assertTrue(b.getBbHeight()>8&&b.getBbWidth()>5,"Arena creature is visibly and physically imposing");
        var close=List.of(WitherGeometry.chain(1,0,false));var far=List.of(WitherGeometry.chain(1,0,true));
        h.assertTrue(close.getFirst()==WitherGeometry.Move.BITE&&far.getFirst()==WitherGeometry.Move.LUNGE,"Close and distant fighters get different openings");
        h.assertTrue(List.of(WitherGeometry.chain(2,0,false)).contains(WitherGeometry.Move.RAKE)&&List.of(WitherGeometry.chain(3,0,false)).contains(WitherGeometry.Move.HORIZON),"Later phases add distinct linked threats");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="wither_19")
    public static void chargeTravelsOverTicksAndCannotHitOnePlayerRepeatedly(GameTestHelper h){clean(h);try{
        var p=player(h,16);var b=fight(h,p);p.setPos(WitherGeometry.floor().add(0,0,22));b.start(WitherGeometry.Move.LUNGE);Vec3 initial=b.position();
        for(int i=0;i<WitherGeometry.Move.LUNGE.windup;i++)b.tick();h.assertTrue(b.position().distanceTo(initial)<.01&&b.motionTicks>0,"Windup starts a committed motion rather than teleporting");
        for(int i=0;i<WitherGeometry.LUNGE_TICKS;i++){Vec3 before=b.position();b.tick();h.assertTrue(b.position().distanceTo(before)<=3.61,"Every charge step fits native motion bounds");}
        h.assertTrue(b.motionTicks==0&&b.spent>0&&b.motionHit.size()<=1,"Charge brakes into recovery and hits a fighter at most once");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="wither_20")
    public static void slamReallyLiftsAndLandsAtItsFixedMark(GameTestHelper h){clean(h);try{
        var p=player(h,16);var b=fight(h,p);p.setPos(WitherGeometry.floor().add(0,0,12));double start=b.getY();b.start(WitherGeometry.Move.SLAM);Vec3 fixed=b.marks.getFirst();
        for(int i=0;i<WitherGeometry.Move.SLAM.windup;i++)b.tick();h.assertTrue(b.getY()>start+8&&p.getHealth()==20,"Body lifts before descent and cannot hit early");
        p.setPos(p.position().add(12,0,0));for(int i=0;i<WitherGeometry.SLAM_TICKS;i++)b.tick();h.assertTrue(b.position().distanceTo(fixed.add(0,.3,0))<.5&&p.getHealth()==20,"Slam lands on the original warning and a dodge works");
        b.setPos(WitherGeometry.floor().add(0,.35,0));p.setPos(WitherGeometry.floor().add(0,0,12));b.start(WitherGeometry.Move.SLAM);
        for(int i=0;i<WitherGeometry.Move.SLAM.windup;i++)b.tick();p.serverLevel().setBlock(p.blockPosition().above(4),Blocks.BLACKSTONE.defaultBlockState(),3);
        for(int i=0;i<WitherGeometry.SLAM_TICKS;i++)b.tick();h.assertTrue(b.getY()>p.getY()+3&&p.getHealth()==20,"A blocked descent cannot deliver a phantom impact through cover");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="wither_21")
    public static void ordinaryJumpClearsPulse(GameTestHelper h){clean(h);try{
        var p=player(h,16);var b=fight(h,p);p.setPos(WitherGeometry.floor().add(0,1.15,12));b.start(WitherGeometry.Move.PULSE);for(int i=0;i<50;i++)b.tick();h.assertTrue(p.getHealth()==20,"Normal jump height clears the pressure front");
        p.setPos(WitherGeometry.floor().add(0,0,12));b.start(WitherGeometry.Move.PULSE);for(int i=0;i<50;i++)b.tick();h.assertTrue(p.getHealth()<20,"Standing in the same lane is struck");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="wither_22")
    public static void anchorRemainsCounterableAfterItsInitialAttack(GameTestHelper h){clean(h);try{
        var p=player(h,16);var b=fight(h,p);b.start(WitherGeometry.Move.RAIN);int anchor=b.anchor();for(int i=0;i<80;i++)b.tick();
        h.assertTrue(b.casting==null&&b.anchor()==anchor,"Feed remains visible beyond the short rain windup");h.assertTrue(b.backfire(anchor,p)&&b.stagger>0,"A solo fighter can reach the focus and still backfire it");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="wither_23")
    public static void oneHeavyHitCannotKeepBossPermanentlyInterrupted(GameTestHelper h){clean(h);try{
        var p=player(h,16);var b=fight(h,p);p.setPos(b.position().add(0,0,6));b.start(WitherGeometry.Move.VERDICT);WitherEvents.swing(p);b.hurt(b.damageSources().playerAttack(p),4);
        h.assertTrue(b.casting==WitherGeometry.Move.VERDICT&&b.stance()==1,"Single routine charged hit cracks stance without deleting the attack");
        b.clock+=8;b.invulnerableTime=0;WitherEvents.swing(p);b.hurt(b.damageSources().playerAttack(p),4);b.clock+=8;b.invulnerableTime=0;WitherEvents.swing(p);b.hurt(b.damageSources().playerAttack(p),4);
        h.assertTrue(b.casting==null&&b.stagger>0&&b.stance()==0,"Sustained coordinated pressure earns a true chain break");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="wither_24")
    public static void exposedRainWarnsAllSkullLandingPositions(GameTestHelper h){clean(h);try{
        var p=player(h,16);var b=fight(h,p);b.start(WitherGeometry.Move.RAIN);h.assertTrue(b.marks.size()==3,"Solo rain has a readable small constellation");var fixed=List.copyOf(b.marks);
        h.assertTrue(fixed.stream().anyMatch(mark->mark.distanceTo(p.position().add(0,.8,0))<.01),"Rain threatens the original position, so standing still is not a free safe spot");
        for(int i=0;i<WitherGeometry.Move.RAIN.windup;i++)b.tick();h.assertTrue(b.skulls.size()==fixed.size(),"Each released skull had its own warning");
        for(int i=0;i<fixed.size();i++)h.assertTrue(b.skulls.get(i).position().multiply(1,0,1).distanceTo(fixed.get(i).multiply(1,0,1))<.01,"Skull falls on the committed horizontal mark");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="wither_25")
    public static void largePartyPreservesEffectiveHealthAboveAttributeCeiling(GameTestHelper h){clean(h);try{
        var p=player(h,16);var b=fight(h,p);b.setHealth(220);for(int i=0;i<4;i++)WitherArena.enter(player(h,16));
        h.assertTrue(b.getMaxHealth()==1000&&Math.abs(b.getHealth()/b.getMaxHealth()-.5)<.001&&b.effectiveHealth==1320,"Five fighters keep the full effective pool and existing health fraction");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="wither_26")
    public static void anchorGlassHasRealReachableOpenFaces(GameTestHelper h){clean(h);try{
        var p=player(h,16);var b=fight(h,p);for(BlockPos anchor:WitherGeometry.ANCHORS){
            Vec3 eye=Vec3.atCenterOf(anchor).add(0,-.88,2.4);var hit=p.serverLevel().clip(new net.minecraft.world.level.ClipContext(eye,Vec3.atCenterOf(anchor),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,net.minecraft.world.phys.shapes.CollisionContext.empty()));
            h.assertTrue(hit.getBlockPos().equals(anchor),"A fighter on the approach plate can actually target the glass, not its housing");
        }
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="wither_27")
    public static void sideHeadShotWarnsThenUsesItsCommittedTarget(GameTestHelper h){clean(h);try{
        var p=player(h,16);var b=fight(h,p);p.setPos(WitherGeometry.floor().add(10,0,12));Vec3 target=p.position().add(0,.8,0);b.startSideShot(1,target);Vec3 mouth=b.sideOrigin;
        for(int i=0;i<WitherGeometry.HEAD_WINDUP-1;i++)b.tick();h.assertTrue(b.skulls.isEmpty(),"Independent side head does not fire before its cue finishes");p.setPos(p.position().add(6,0,0));b.tick();
        h.assertTrue(b.skulls.size()==1&&b.skulls.getFirst().position().distanceTo(mouth)<.01,"Skull comes from the actual prepared mouth");h.assertTrue(b.skulls.getFirst().getDeltaMovement().normalize().dot(target.subtract(mouth).normalize())>.999,"Projectile uses the fixed warning, not a late retarget");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="wither_28",timeoutTicks=180)
    public static void adminCatalogueAndPermissionChecksCoverWither(GameTestHelper h){clean(h);try{
        var p=player(h,16);var entries=com.jayptucker.heroesevolved.admin.AdminEvents.describe(p.getServer(),p);var entry=entries.stream().filter(e->e.id().equals("wither")).findFirst().orElseThrow();
        h.assertTrue(entry.starts().size()==3&&entry.scope().contains("all fighters"),"Console exposes scoped start, entry and phase controls");
        com.jayptucker.heroesevolved.admin.AdminService.handle(p,new com.jayptucker.heroesevolved.network.AdminActionPayload(com.jayptucker.heroesevolved.network.AdminActionPayload.Action.EVENT_START,p.getUUID(),"wither",0));
        h.assertTrue(!WitherArena.busy(p.getServer()),"An untrusted event payload cannot create a breach");ready(h);
        com.jayptucker.heroesevolved.admin.AdminService.handle(p,new com.jayptucker.heroesevolved.network.AdminActionPayload(com.jayptucker.heroesevolved.network.AdminActionPayload.Action.EVENT_CANCEL,p.getUUID(),"wither",0));
        h.assertTrue(WitherArena.busy(p.getServer()),"An untrusted event payload cannot cancel another party's encounter");
        h.assertTrue(com.jayptucker.heroesevolved.admin.AdminEvents.start(p.getServer(),p,"wither",999).equals("Unknown event or start option."),"Unknown variants are rejected");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="wither_29",timeoutTicks=180)
    public static void cancellationAlsoStopsQueuedFormation(GameTestHelper h){clean(h);var p=player(h,16);
        h.assertTrue(WitherArena.start(p.serverLevel(),p.position().add(7,0,0),0),"Formation queued");WitherArena.reset(p.getServer(),false);
        h.runAtTickTime(145,()->{try{h.assertTrue(!WitherArena.busy(p.getServer())&&!WitherArena.ready(),"Cancelled queued formation cannot reopen a breach");}finally{clean(h);}h.succeed();});
    }
}
