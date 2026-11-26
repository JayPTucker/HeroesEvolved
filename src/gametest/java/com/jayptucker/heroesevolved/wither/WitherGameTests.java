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
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
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
    private static ServerPlayer player(GameTestHelper h,int level,net.minecraft.resources.ResourceLocation power){
        ServerPlayer p=player(h,level,false);p.setData(ModDataAttachments.PLAYER_ABILITIES,PlayerAbilityData.empty().replaceWithUnlockedPower(power));return p;
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
    /** The fight without its ward, for tests of everything else. */
    private static RiftWitherEntity fight(GameTestHelper h,ServerPlayer p){RiftWitherEntity b=warded(h,p);b.unward();return b;}
    /** The fight as it begins: warded, its three hearts in their pylons. */
    private static RiftWitherEntity warded(GameTestHelper h,ServerPlayer p){ready(h);h.assertTrue(WitherArena.enter(p),"Eligible solo fighter enters");RiftWitherEntity b=WitherArena.boss();b.skipArrival();b.still(true);b.holdOff(10000);b.tick();return b;}
    private static void look(ServerPlayer p,Vec3 at){p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,at);}
    private static void done(GameTestHelper h){clean(h);h.succeed();}
    @GameTest(template="rewind_empty",batch="wither_01",timeoutTicks=240)
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
        Vec3 gate=WitherData.get(level.getServer()).source.at();
        h.assertTrue(gate.distanceTo(Vec3.atBottomCenterOf(base))>2&&gate.distanceTo(Vec3.atBottomCenterOf(base))<5.5,"The breach opens a few blocks behind where it formed");
        h.runAtTickTime(175,()->{try{h.assertTrue(WitherArena.ready(),"Seven-and-a-half-second dragging sequence leaves a usable breach");h.assertTrue(level.getBlockState(base.below()).is(Blocks.DIAMOND_BLOCK),"Summoning terrain survives");h.assertTrue(WitherData.get(level.getServer()).source!=null,"Entrance persisted");}finally{clean(h);}h.succeed();});
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
            h.assertTrue(cage.getBlockState(WitherGeometry.ORIGIN.offset(x,y,z)).getCollisionShape(cage,WitherGeometry.ORIGIN.offset(x,y,z)).isEmpty(),"Central aerial dogfight volume stays clear");
        for(int[] pillar:WitherGeometry.PILLARS)h.assertTrue(!cage.getBlockState(WitherGeometry.ORIGIN.offset(pillar[0],4,pillar[1])).isAir(),"Every pillar the geometry lists stands as cover");
        h.assertTrue(!cage.getBlockState(BlockPos.containing(WitherGeometry.floor().add(30.5,4,0))).isAir()&&!cage.getBlockState(BlockPos.containing(WitherGeometry.floor().add(0,4,30.5))).isAir(),"The charnel wall encloses the floor");
        p.setPos(WitherGeometry.floor().add(20,35,0));WitherArena.tick(p.getServer());h.assertTrue(p.getY()>WitherGeometry.floor().y+34,"Flight at 35 blocks is not leashed to floor");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="wither_04")
    public static void lateJoinScalesWithoutHealingAndCapsBurst(GameTestHelper h){clean(h);try{
        ServerPlayer p=player(h,16);var b=fight(h,p);b.setHealth((float)(RiftWitherEntity.BASE_HEALTH/2));ServerPlayer q=player(h,18);h.assertTrue(WitherArena.enter(q),"Late join allowed");
        h.assertTrue(b.getMaxHealth()==(float)Math.min(1000,RiftWitherEntity.BASE_HEALTH*1.5)&&Math.abs(b.getHealth()/b.getMaxHealth()-.5)<.001,"Party scaling preserves fraction");
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
        ServerPlayer p=player(h,16);var b=fight(h,p);
        b.start(WitherGeometry.Move.RAIN);h.assertTrue(b.anchor()<0,"In the first phase its hearts are its ward, not its fuel");b.cancelChain();
        b.setHealth(b.getMaxHealth()*.65F);b.invulnerableTime=0;b.hurt(b.damageSources().playerAttack(p),1);h.assertTrue(b.phase()==2&&b.transition>0,"Threshold changes phase");
        b.transition=0;b.tick();
        for(int i=0;i<3;i++)h.assertTrue(b.hearts[i]!=null&&b.hearts[i].state()==HungerHeart.DORMANT&&b.hearts[i].lock()==null,"The broken vessel's hearts re-form asleep");
        float sleeping=b.hearts[0].getHealth();b.hearts[0].hurt(b.damageSources().playerAttack(p),20);h.assertTrue(b.hearts[0].getHealth()==sleeping,"A sleeping heart cannot be broken");
        p.setPos(WitherGeometry.heart(1).add(0,-3,3));b.start(WitherGeometry.Move.RAIN);int index=b.anchor();
        h.assertTrue(index==1&&b.hearts[index].state()==HungerHeart.FEEDING,"A heavy move lights the heart nearest its focus");
        float hp=b.getHealth();var lit=b.hearts[index];for(int i=0;i<6&&!lit.isRemoved();i++)lit.hurt(b.damageSources().playerAttack(p),20);
        h.assertTrue(lit.isRemoved()&&b.getHealth()<hp-b.getMaxHealth()*.07&&b.stagger>0,"A broken feeding heart backfires and exposes the core");
        h.assertTrue(!b.backfire(index,p)&&b.hearts[index]==null,"A spent heart cannot be farmed this phase");
        b.setHealth(b.getMaxHealth()*.32F);b.invulnerableTime=0;b.hurt(b.damageSources().playerAttack(p),1);b.transition=0;b.tick();
        h.assertTrue(b.phase()==3&&b.burnt()==0&&b.hearts[index]!=null,"Each later phase re-forms every heart");
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
        b.setHealth(b.getMaxHealth()*.7F);b.phaseFinished(p);h.assertTrue(b.phase()==2&&b.stagger==70,"Phase finisher earns long stagger");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="wither_09")
    public static void retreatResetsAbandonedAttempt(GameTestHelper h){clean(h);try{
        ServerPlayer p=player(h,16);var b=fight(h,p);WitherArena.sendHome(p);for(int i=0;i<401;i++)WitherArena.tick(p.getServer());
        h.assertTrue(WitherArena.boss()==null&&WitherArena.ready(),"Abandoned attempt resets but entrance remains");h.assertTrue(WitherArena.enter(p)&&WitherArena.boss()!=b,"A fresh solo attempt can follow retreat");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="wither_10")
    public static void deathSceneDetonatesThenExitOpensWhereItDied(GameTestHelper h){clean(h);try{
        ServerPlayer p=player(h,16);var b=fight(h,p);b.setPos(WitherGeometry.floor().add(6,.35,4));b.setHealth(1);p.setPos(b.position().add(0,0,6));b.hurt(b.damageSources().playerAttack(p),3);
        h.assertTrue(b.dying(),"Lethal blow starts death scene");
        for(int i=0;i<WitherGeometry.DETONATE;i++)b.tick();
        h.assertTrue(b.dying()&&WitherArena.down(p)&&p.getForcedPose()==Pose.SLEEPING,"The star's detonation knocks the fighter flat before the reward");
        for(int i=WitherGeometry.DETONATE;i<WitherGeometry.DEATH_TICKS-1;i++)b.tick();h.assertTrue(b.dying(),"Reward waits for the end of the scene");b.tick();
        h.assertTrue(!b.dying(),"Death scene ends once");
        for(int i=0;i<WitherGeometry.EXIT_OPENS+WitherGeometry.EXIT_OPEN_TICKS;i++)WitherArena.tick(p.getServer());
        Vec3 exit=WitherArena.exitPortal();
        h.assertTrue(exit!=null&&exit.multiply(1,0,1).distanceTo(new Vec3(6.5,0,4.5))<1.5,"The exit tears open where it died");
        h.assertTrue(WitherArena.inside(p)&&WitherArena.down(p),"Nobody is thrown out while still on the ground");
        for(int i=0;i<WitherGeometry.DAZE;i++)WitherArena.tick(p.getServer());
        h.assertTrue(!WitherArena.down(p)&&p.getForcedPose()!=Pose.SLEEPING,"The fighter comes to");
        p.setPos(new Vec3(exit.x,WitherGeometry.floor().y,exit.z));WitherArena.tick(p.getServer());
        h.assertTrue(!WitherArena.inside(p),"Walking into the exit goes home");
        for(int i=0;i<30;i++)WitherArena.tick(p.getServer());
        h.assertTrue(!WitherArena.busy(p.getServer()),"The Ossuary and its breach close once everyone has left");
        h.assertTrue(p.getInventory().countItem(Items.NETHER_STAR)==1&&p.getInventory().countItem(ModItems.FRACTURED_STAR.get())==1,"One Nether Star and participant fragment go home with the fighter");
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
        h.assertTrue(Math.abs(from.y-mark.y)<4,"Warning commits to the opponent's aerial body height");
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
        var close=List.of(WitherGeometry.chain(1,0,5,false));var far=List.of(WitherGeometry.chain(1,0,25,false));var high=List.of(WitherGeometry.chain(1,0,12,true));
        h.assertTrue(close.getFirst()==WitherGeometry.Move.BITE&&far.getFirst()==WitherGeometry.Move.RAIN&&high.getFirst()==WitherGeometry.Move.TETHER,"Close, distant and airborne fighters get different openings");
        h.assertTrue(List.of(WitherGeometry.chain(2,0,5,false)).contains(WitherGeometry.Move.SLAM)&&List.of(WitherGeometry.chain(2,0,12,false)).contains(WitherGeometry.Move.SPIRES)
                &&List.of(WitherGeometry.chain(3,2,5,false)).contains(WitherGeometry.Move.HORIZON),"Later phases add distinct linked threats");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="wither_19")
    public static void chargeTravelsOverTicksAndCannotHitOnePlayerRepeatedly(GameTestHelper h){clean(h);try{
        var p=player(h,16);var b=fight(h,p);p.setPos(WitherGeometry.floor().add(0,0,22));b.start(WitherGeometry.Move.LUNGE);Vec3 initial=b.position();
        for(int i=0;i<WitherGeometry.Move.LUNGE.windup;i++)b.tick();h.assertTrue(b.position().distanceTo(initial)<.01&&b.motionTicks>0,"Windup starts a committed motion rather than teleporting");
        for(int i=0;i<WitherGeometry.LUNGE_TICKS;i++){Vec3 before=b.position();b.tick();h.assertTrue(b.position().distanceTo(before)<=3.61,"Every charge step fits native motion bounds");}
        h.assertTrue(b.motionTicks==0&&(b.spent>0||b.crash>0)&&b.motionHit.size()<=1,"Charge brakes (or crashes) into recovery and hits a fighter at most once");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="wither_20")
    public static void slamReallyLiftsAndLandsAtItsFixedMark(GameTestHelper h){clean(h);try{
        var p=player(h,16);var b=fight(h,p);p.setPos(WitherGeometry.floor().add(0,0,12));double start=b.getY();b.start(WitherGeometry.Move.SLAM);Vec3 fixed=b.marks.getFirst();
        for(int i=0;i<WitherGeometry.Move.SLAM.windup;i++)b.tick();h.assertTrue(b.getY()>start+8&&p.getHealth()==20,"Body lifts before descent and cannot hit early");
        p.setPos(p.position().add(12,0,0));for(int i=0;i<WitherGeometry.SLAM_TICKS;i++)b.tick();h.assertTrue(b.position().distanceTo(fixed.add(0,.3,0))<.5&&p.getHealth()==20,"Slam lands on the original warning and a dodge works");
        b.cancelChain();b.recovery=0;b.setPos(WitherGeometry.floor().add(0,.35,0));p.setPos(WitherGeometry.floor().add(0,0,12));b.start(WitherGeometry.Move.SLAM);
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
        var p=player(h,16);var b=fight(h,p);b.operatorPhase();b.transition=0;b.tick();b.start(WitherGeometry.Move.RAIN);int anchor=b.anchor();for(int i=0;i<80;i++)b.tick();
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
        var p=player(h,16);var b=fight(h,p);b.setHealth((float)(RiftWitherEntity.BASE_HEALTH/2));for(int i=0;i<4;i++)WitherArena.enter(player(h,16));
        h.assertTrue(b.getMaxHealth()==1000&&Math.abs(b.getHealth()/b.getMaxHealth()-.5)<.001&&b.effectiveHealth==(float)(RiftWitherEntity.BASE_HEALTH*3),"Five fighters keep the full effective pool and existing health fraction");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="wither_26")
    public static void anchorGlassHasRealReachableOpenFaces(GameTestHelper h){clean(h);try{
        var p=player(h,16);var b=warded(h,p);for(int i=0;i<3;i++){
            h.assertTrue(b.hearts[i]!=null&&b.hearts[i].state()==HungerHeart.WARDING&&b.hearts[i].position().distanceTo(WitherGeometry.heart(i))<.01,"Each pylon holds a warding heart in its cradle");
            for(Vec3 side:List.of(new Vec3(0,-.88,2.4),new Vec3(2.4,-.88,0),new Vec3(0,-.88,-2.4),new Vec3(-2.4,-.88,0))){
                Vec3 eye=WitherGeometry.heartCenter(i).add(side);
                var hit=p.serverLevel().clip(new net.minecraft.world.level.ClipContext(eye,WitherGeometry.heartCenter(i),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,net.minecraft.world.phys.shapes.CollisionContext.empty()));
                h.assertTrue(hit.getType()==net.minecraft.world.phys.HitResult.Type.MISS,"The cradle is open on every side: nothing between a fighter on the plate and the heart");
            }
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
        h.assertTrue(entry.starts().size()==5&&entry.scope().contains("all fighters"),"Console exposes scoped start, entry, phase, finish and ward controls");
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
    @GameTest(template="rewind_empty",batch="wither_30")
    public static void arrivalFallsOutOfItsTearAndCannotBeHurtUntilItLands(GameTestHelper h){clean(h);try{
        ServerPlayer p=player(h,16);ready(h);h.assertTrue(WitherArena.enter(p),"Entry");var b=WitherArena.boss();b.still(true);b.holdOff(10000);
        h.assertTrue(b.arriving()&&b.getY()>WitherGeometry.floor().y+5&&b.gate()!=null,"It begins up in the tear over the dais");
        float hp=b.getHealth();h.assertTrue(!b.hurt(b.damageSources().playerAttack(p),5)&&b.getHealth()==hp,"Invulnerable while it arrives");
        for(int i=0;i<WitherGeometry.ARRIVAL_LAND+1;i++)b.tick();
        h.assertTrue(Math.abs(b.getY()-WitherGeometry.floor().y-.3)<.2,"It lands on the dais");
        for(int i=WitherGeometry.ARRIVAL_LAND+1;i<WitherGeometry.ARRIVAL_TICKS+1;i++)b.tick();
        h.assertTrue(!b.arriving()&&b.gate()==null,"The arrival ends and its tear shuts");
        b.invulnerableTime=0;h.assertTrue(b.hurt(b.damageSources().playerAttack(p),5),"Then it can be fought");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="wither_31")
    public static void raisedShieldStandsUpToTheDetonation(GameTestHelper h){clean(h);try{
        ServerPlayer p=player(h,16),q=player(h,16);var b=fight(h,p);WitherArena.enter(q);
        p.setPos(b.position().add(0,0,8));q.setPos(b.position().add(0,0,-8));
        q.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.SHIELD));
        q.setYRot(0);q.setYHeadRot(0);q.setXRot(0);q.startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);
        try{Field held=LivingEntity.class.getDeclaredField("useItemRemaining");held.setAccessible(true);held.setInt(q,q.getUseItem().getUseDuration(q)-10);}catch(ReflectiveOperationException e){throw new AssertionError(e);}
        h.assertTrue(q.isBlocking(),"The second fighter has a shield raised toward the star");
        b.operatorDefeat();for(int i=0;i<=WitherGeometry.DETONATE;i++)b.tick();
        h.assertTrue(WitherArena.down(p)&&p.getForcedPose()==Pose.SLEEPING,"Without a shield the blast knocks a fighter flat");
        h.assertTrue(!WitherArena.down(q)&&q.getForcedPose()!=Pose.SLEEPING,"A raised shield facing the blast keeps its holder standing");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="wither_32")
    public static void chargeBaitedIntoAPillarCrashesAndStuns(GameTestHelper h){clean(h);try{
        ServerPlayer p=player(h,16);var b=fight(h,p);int[] pillar=WitherGeometry.PILLARS[0];
        Vec3 face=WitherGeometry.floor().add(pillar[0]+.5,0,pillar[1]+.5-pillar[2]-1.5);
        b.setPos(face.add(0,.35,-13));p.setPos(face);b.start(WitherGeometry.Move.LUNGE);
        h.assertTrue(b.lungeCrash,"The warning already knows the lane ends in stone");
        for(int i=0;i<WitherGeometry.Move.LUNGE.windup+WitherGeometry.LUNGE_TICKS;i++)b.tick();
        h.assertTrue(b.crash>0&&b.exposed(),"It crashes into the pillar and is stunned with its chest open");
        float hp=b.getHealth();b.invulnerableTime=0;b.hurt(b.damageSources().playerAttack(p),4);
        h.assertTrue(hp-b.getHealth()>4.0F,"A crashed Wither takes more (4 through its armour alone would be under 3.5)");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="wither_33")
    public static void phaseRoarThrowsFightersBackAndLightsTheChannels(GameTestHelper h){clean(h);try{
        ServerPlayer p=player(h,16);var b=fight(h,p);p.setPos(b.position().add(0,0,5));
        b.setHealth(b.getMaxHealth()*.65F);b.invulnerableTime=0;b.hurt(b.damageSources().playerAttack(p),1);
        h.assertTrue(b.phase()==2&&b.transition>0&&p.getDeltaMovement().horizontalDistance()>.5,"The roar throws a close fighter back");
        h.assertTrue(b.minions.size()>=1,"Skeletal flankers answer the broken vessel");
        var channel=Blueprint.load("wither_cage").markerOffsets("wither:channel").getFirst();
        h.assertTrue(p.serverLevel().getBlockState(WitherGeometry.ORIGIN.offset(channel)).is(Blocks.CRYING_OBSIDIAN),"The floor's channels light in the second phase");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="wither_34")
    public static void broodIsSpewedOntoItsMarksFightsAndLeavesNothing(GameTestHelper h){clean(h);try{
        ServerPlayer p=player(h,16);var b=fight(h,p);p.setPos(WitherGeometry.floor().add(0,0,12));
        h.assertTrue(List.of(WitherGeometry.chain(1,0,25,false)).contains(WitherGeometry.Move.BROOD),"It spews its brood at distant fighters");
        b.start(WitherGeometry.Move.BROOD);var marks=List.copyOf(b.marks);
        h.assertTrue(marks.size()==WitherGeometry.broodCount(1,1),"Phase one spews two for a solo fighter");
        h.assertTrue(marks.stream().allMatch(m->m.distanceTo(p.position())<5&&m.distanceTo(p.position())>1.5),"Every mark is beside the fighter, not on them");
        for(int i=0;i<WitherGeometry.Move.BROOD.windup;i++)b.tick();
        h.assertTrue(b.spews.size()==marks.size()&&b.minions.size()==marks.size(),"They are thrown out of its mouth");
        h.assertTrue(b.spews.getFirst().mob().getY()>WitherGeometry.floor().y+4,"In flight they leave from up at its mouth");
        for(int i=0;i<WitherGeometry.BROOD_FLIGHT;i++)b.tick();
        h.assertTrue(b.spews.isEmpty(),"Every one has landed");
        for(int i=0;i<marks.size();i++){var m=b.minions.get(i);
            h.assertTrue(m.position().distanceTo(marks.get(i))<.6&&!m.isNoAi()&&!m.isInvulnerable()&&m.getPersistentData().getBoolean(WitherMoves.BROOD),"Each lands on its own mark and then fights");}
        h.assertTrue(!b.broodReady(),"A cooldown follows a brood");
        var first=b.minions.getFirst();Vec3 at=first.position();first.hurt(first.damageSources().playerAttack(p),1000);
        h.assertTrue(p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new AABB(BlockPos.containing(at)).inflate(3)).isEmpty(),"The brood drops nothing to farm");
        var second=b.minions.get(1);second.tickCount=WitherGeometry.BROOD_LIFE+1;b.tick();
        h.assertTrue(second.isRemoved(),"It crumbles when its time is up");
        b.clock+=100000;
        for(int i=0;i<WitherGeometry.broodCap(1);i++){var extra=EntityType.WITHER_SKELETON.create(p.serverLevel());extra.moveTo(WitherGeometry.floor().add(i,0,-6));p.serverLevel().addFreshEntity(extra);b.minions.add(extra);}
        h.assertTrue(!b.broodReady(),"Never more than the cap on the floor at once");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="wither_35")
    public static void laterPhasesAreFasterAndFeintsHoldLonger(GameTestHelper h){
        var bite=WitherGeometry.Move.BITE;
        int one=WitherGeometry.castWindup(bite,1,1,false),two=WitherGeometry.castWindup(bite,2,1,false),three=WitherGeometry.castWindup(bite,3,1,false);
        h.assertTrue(one==bite.windup&&two<one&&three<two,"Each phase winds up faster");
        h.assertTrue(WitherGeometry.castWindup(bite,2,3,false)>two&&WitherGeometry.castWindup(bite,1,3,false)==one,"From the second phase every third bite is a held feint");
        h.assertTrue(WitherGeometry.castWindup(WitherGeometry.Move.VERDICT,2,3,false)==WitherGeometry.castWindup(WitherGeometry.Move.VERDICT,2,1,false),"Beams never feint");
        h.assertTrue(WitherGeometry.castWindup(WitherGeometry.Move.VERDICT,3,1,true)<WitherGeometry.castWindup(WitherGeometry.Move.VERDICT,3,1,false),"Starving is faster still");
        h.assertTrue(WitherGeometry.castWindup(bite,3,1,true)>=WitherGeometry.floor(bite),"...but never under a move's reading time");
        h.assertTrue(WitherGeometry.damage(WitherGeometry.Move.SLAM,3)>WitherGeometry.damage(WitherGeometry.Move.SLAM,1)&&WitherGeometry.damage(WitherGeometry.Move.HORIZON,1)>=20,"Its hits grow with the phases, and its discharge is near-lethal");
        h.succeed();
    }
    @GameTest(template="rewind_empty",batch="wither_36")
    public static void itsBiteReallyHurtsAndWithers(GameTestHelper h){clean(h);try{
        ServerPlayer p=player(h,16);var b=fight(h,p);b.setYRot(0);p.setPos(b.position().add(0,0,6));float before=p.getHealth();
        b.start(WitherGeometry.Move.BITE);for(int i=0;i<WitherGeometry.Move.BITE.windup;i++)b.tick();
        h.assertTrue(before-p.getHealth()>=5.5F&&p.hasEffect(net.minecraft.world.effect.MobEffects.WITHER),"A bite takes a big bite out of a fighter and withers them");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="wither_38")
    public static void everyPowerHasAStrongAnswerInEveryPhase(GameTestHelper h){
        var all=List.of(ModAbilities.PYROKINESIS_ID,ModAbilities.CRYOKINESIS_ID,ModAbilities.ELECTROKINESIS_ID,ModAbilities.HYDROKINESIS_ID,ModAbilities.SUPERSPEED_ID,
                ModAbilities.FLIGHT_ID,ModAbilities.QUAKE_ID,ModAbilities.SONIC_RESONANCE_ID,ModAbilities.TELEPATHY_ID,ModAbilities.TELEKINESIS_ID,ModAbilities.TIME_MANIPULATION_ID,
                ModAbilities.RADIATION_ID,ModAbilities.REGENERATION_ID,ModAbilities.PHASING_ID,ModAbilities.INVISIBILITY_ID,ModAbilities.MIMICRY_ID,ModAbilities.SHAPESHIFTING_ID,
                ModAbilities.TECHNOPATHY_ID,ModAbilities.MATTER_ASSEMBLY_ID,ModAbilities.VIBE_ID,ModAbilities.AMPLIFICATION_ID);
        h.assertTrue(com.jayptucker.heroesevolved.combat.BossLocks.powers().containsAll(all)&&all.size()==21,"The lock table knows all twenty-one powers");
        for(var power:all)for(int phase=1;phase<=3;phase++){
            boolean strong=false;
            for(var lock:WitherGeometry.locks(phase)){var key=com.jayptucker.heroesevolved.combat.BossLocks.key(power,lock);strong|=key!=null&&key.strong();}
            h.assertTrue(strong,power+" has a strong answer in phase "+phase);
        }
        for(int phase=1;phase<=3;phase++)for(var lock:WitherGeometry.locks(phase)){
            Set<com.jayptucker.heroesevolved.combat.BossLocks.Role> roles=new HashSet<>();int keys=0;
            for(var power:all){var key=com.jayptucker.heroesevolved.combat.BossLocks.key(power,lock);if(key!=null){keys++;roles.add(key.role());}}
            h.assertTrue(keys>=3&&roles.size()>=2,lock+" has at least three power answers in more than one role");
        }
        h.succeed();
    }
    @GameTest(template="rewind_empty",batch="wither_39")
    public static void wardQuartersBlowsAndHoldsTheGate(GameTestHelper h){clean(h);try{
        ServerPlayer p=player(h,16);var b=warded(h,p);p.setPos(b.position().add(0,0,6));
        h.assertTrue(b.ward&&b.wardHearts()==3&&b.warded(),"It arrives warded by three hearts");
        float hp=b.getHealth();b.invulnerableTime=0;b.hurt(b.damageSources().playerAttack(p),20);float warded=hp-b.getHealth();
        b.setHealth(b.getMaxHealth()*.705F);
        for(int i=0;i<20;i++){b.clock+=20;b.invulnerableTime=0;b.hurt(b.damageSources().playerAttack(p),40);}
        h.assertTrue(b.getHealth()>=b.getMaxHealth()*WitherGeometry.WARD_GATE-.01F&&b.phase()==1,"The ward holds the line: it cannot be brought below seven tenths");
        b.setHealth(b.getMaxHealth());b.clock+=40;b.unward();hp=b.getHealth();b.invulnerableTime=0;b.hurt(b.damageSources().playerAttack(p),20);float open=hp-b.getHealth();
        h.assertTrue(warded>0&&warded<open*.3F,"While three hearts feed it, the ward takes three quarters of every blow ("+warded+" vs "+open+")");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="wither_40")
    public static void heartsCountPowersOverPlainWeapons(GameTestHelper h){clean(h);try{
        ServerPlayer pyro=player(h,16),mind=player(h,16,ModAbilities.TELEPATHY_ID);var b=warded(h,pyro);WitherArena.enter(mind);
        var heart=b.hearts[0];float max=heart.getMaxHealth();
        heart.hurt(b.damageSources().playerAttack(pyro),4);h.assertTrue(Math.abs(max-heart.getHealth()-4*com.jayptucker.heroesevolved.combat.BossLocks.STRONG_HITS)<.01,"A pyrokinetic's blows break a heart two and a half times over");
        float before=heart.getHealth();heart.hurt(b.damageSources().playerAttack(mind),4);h.assertTrue(Math.abs(before-heart.getHealth()-4)<.01,"Any power's blow counts in full");
        pyro.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_SWORD));before=heart.getHealth();heart.hurt(b.damageSources().playerAttack(pyro),4);
        h.assertTrue(Math.abs(before-heart.getHealth()-2)<.01,"A blade does half: plain weapons are the slow answer");
        float boss=b.getHealth();b.invulnerableTime=0;b.hurt(b.damageSources().playerAttack(pyro),10);float blade=boss-b.getHealth();
        pyro.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,ItemStack.EMPTY);b.clock+=40;boss=b.getHealth();b.invulnerableTime=0;b.hurt(b.damageSources().playerAttack(pyro),10);
        h.assertTrue(blade<(boss-b.getHealth())*.7F,"Its body takes less from a blade than from a power");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="wither_41",timeoutTicks=200)
    public static void aimedPowersAnswerAHeart(GameTestHelper h){clean(h);
        ServerPlayer tech=player(h,16,ModAbilities.TECHNOPATHY_ID);var b=warded(h,tech);var heart=b.hearts[2];
        h.runAtTickTime(100,()->{try{
        tech.setPos(WitherGeometry.heart(2).add(0,-2.5,-7));look(tech,heart.aimPoint());float max=heart.getMaxHealth();
        com.jayptucker.heroesevolved.combat.BossLocks.used(tech,ModAbilities.TECHNOPATHY_ID,"test",20);
        h.assertTrue(Math.abs(max*(1-com.jayptucker.heroesevolved.combat.BossLocks.STRONG_AIMED)-heart.getHealth())<.01,"A technopath overrides a heart it looks at: two fifths at once ("+heart.getHealth()+" of "+max+", lock "+heart.lock()+", key "+com.jayptucker.heroesevolved.combat.BossLocks.key(tech,com.jayptucker.heroesevolved.combat.BossLocks.Lock.SHIELD)+", level "+(tech.level()==heart.level())+", eye "+tech.getEyePosition()+" look "+tech.getLookAngle()+" heart "+heart.aimPoint()+")");
        float after=heart.getHealth();com.jayptucker.heroesevolved.combat.BossLocks.used(tech,ModAbilities.TECHNOPATHY_ID,"test",20);
        h.assertTrue(heart.getHealth()==after,"The same heart can't be answered again at once");
        tech.setPos(WitherGeometry.heart(1).add(0,-2.5,7));look(tech,b.hearts[1].aimPoint());com.jayptucker.heroesevolved.combat.BossLocks.used(tech,ModAbilities.TECHNOPATHY_ID,"test",2);
        h.assertTrue(b.hearts[1].getHealth()==b.hearts[1].getMaxHealth(),"A trifling use (under eight stamina) is not an answer");
        look(tech,tech.position().add(0,0,-10));com.jayptucker.heroesevolved.combat.BossLocks.used(tech,ModAbilities.TECHNOPATHY_ID,"test",20);
        h.assertTrue(b.hearts[1].getHealth()==b.hearts[1].getMaxHealth(),"It must be aimed at the heart");
        ServerPlayer pyro=player(h,16);WitherArena.enter(pyro);pyro.setPos(tech.position());look(pyro,b.hearts[1].aimPoint());
        com.jayptucker.heroesevolved.combat.BossLocks.used(pyro,ModAbilities.PYROKINESIS_ID,"test",20);
        h.assertTrue(b.hearts[1].getHealth()==b.hearts[1].getMaxHealth(),"A power whose key is its blows has nothing to aim");
    
        }finally{clean(h);}h.succeed();});
    }
    @GameTest(template="rewind_empty",batch="wither_42")
    public static void breakingAllHeartsShattersTheWardAndAHeartReforms(GameTestHelper h){clean(h);try{
        ServerPlayer p=player(h,16);var b=warded(h,p);
        var first=b.hearts[0];first.take(first.getMaxHealth()+1,p);
        h.assertTrue(b.hearts[0]==null&&b.mendAt[0]>b.clock&&b.ward&&b.wardHearts()==2,"One broken heart thins the ward and starts to re-form");
        b.clock=b.mendAt[0];b.tick();h.assertTrue(b.hearts[0]!=null&&b.hearts[0].state()==HungerHeart.WARDING,"Left alone, a broken heart re-forms while the ward stands");
        b.setPos(WitherGeometry.floor().add(0,9,0));double high=b.getY();
        for(int i=0;i<3;i++)b.hearts[i].take(1000,p);
        h.assertTrue(!b.ward&&b.crash>0&&b.exposed()&&!b.warded(),"Break all three and the ward shatters, throwing it down");
        for(int i=0;i<12;i++)b.tick();
        h.assertTrue(b.getY()<high-5&&!b.warded(),"It falls to the floor");
        for(int i=0;i<3;i++)h.assertTrue(b.hearts[i]==null&&b.mendAt[i]<0,"With the ward gone, the cradles stay dark for the rest of the phase");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="wither_43",timeoutTicks=200)
    public static void returnedSkullShattersAHeartAndHitsPastTheWard(GameTestHelper h){clean(h);
        ServerPlayer p=player(h,16);var b=warded(h,p);ServerLevel cage=p.serverLevel();
        h.runAtTickTime(100,()->{try{
        var mine=ModEntities.RIFT_SKULL.get().create(cage);mine.setOwner(b);Vec3 at=WitherGeometry.heartCenter(1);mine.setPos(at);
        mine.onHit(new BlockHitResult(at,net.minecraft.core.Direction.UP,BlockPos.containing(at),false));
        h.assertTrue(b.hearts[1]!=null&&b.hearts[1].getHealth()==b.hearts[1].getMaxHealth(),"Its own skulls never break its hearts");
        var back=ModEntities.RIFT_SKULL.get().create(cage);back.setOwner(p);back.setPos(at);
        back.onHit(new BlockHitResult(at,net.minecraft.core.Direction.UP,BlockPos.containing(at),false));
        h.assertTrue(b.hearts[1]==null&&b.wardHearts()==2,"A skull sent back shatters a heart outright");
        float hp=b.getHealth();var into=ModEntities.RIFT_SKULL.get().create(cage);into.setOwner(p);Vec3 core=b.core();into.setPos(core);
        into.onHit(new BlockHitResult(core,net.minecraft.core.Direction.UP,BlockPos.containing(core),false));
        h.assertTrue(hp-b.getHealth()>=b.getMaxHealth()*WitherGeometry.RETURNED_SHARE*.9F,"A skull sent into it hits for a twenty-fifth of its health, past the ward ("+(hp-b.getHealth())+" of "+b.getMaxHealth()+")");
    
        }finally{clean(h);}h.succeed();});
    }
    @GameTest(template="rewind_empty",batch="wither_44")
    public static void broodCarriesBoneBackToMendItInTheSecondPhase(GameTestHelper h){clean(h);try{
        ServerPlayer p=player(h,16);var b=fight(h,p);ServerLevel cage=p.serverLevel();
        java.util.function.Supplier<net.minecraft.world.entity.monster.WitherSkeleton> brood=()->{var s=EntityType.WITHER_SKELETON.create(cage);WitherMoves.brood(s);
            s.moveTo(b.position().add(2.5,0,0));cage.addFreshEntity(s);b.minions.add(s);s.tickCount=WitherGeometry.BROOD_FLIGHT+WitherGeometry.BROOD_RETURN+1;return s;};
        var early=brood.get();b.setHealth(b.getMaxHealth()*.8F);float hp=b.getHealth();b.tick();
        h.assertTrue(!early.isRemoved()&&b.getHealth()==hp,"In the first phase its brood only fights");
        early.discard();b.minions.clear();b.operatorPhase();b.transition=0;b.setHealth(b.getMaxHealth()*.5F);
        var carrier=brood.get();hp=b.getHealth();b.tick();
        h.assertTrue(carrier.isRemoved()&&!b.minions.contains(carrier)&&Math.abs(b.getHealth()-hp-b.getMaxHealth()*WitherGeometry.MEND_SHARE)<.05,"A skeleton that reaches it climbs in and mends it");
        b.setHealth(b.getMaxHealth()*2/3F-1);brood.get();b.tick();
        h.assertTrue(b.getHealth()<=b.getMaxHealth()*2/3F+.01,"Mending never lifts it back into a beaten phase");
        var young=EntityType.WITHER_SKELETON.create(cage);WitherMoves.brood(young);young.moveTo(b.position().add(2.5,0,0));cage.addFreshEntity(young);b.minions.add(young);young.tickCount=10;b.tick();
        h.assertTrue(!young.isRemoved(),"A fresh skeleton fights before it carries bone back");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="wither_45",timeoutTicks=200)
    public static void hungerMarkDrainsWhileSeenAndSnapsOutOfSight(GameTestHelper h){clean(h);
        ServerPlayer p=player(h,16);var b=fight(h,p);b.operatorPhase();b.transition=0;b.setYRot(0);p.setPos(b.position().add(0,0,6));
        h.runAtTickTime(100,()->{try{
        b.cancelChain();b.casts=1;b.start(WitherGeometry.Move.BITE);for(int i=0;i<b.actionWindup();i++)b.tick();
        h.assertTrue(p.hasEffect(com.jayptucker.heroesevolved.registry.ModItems.HUNGERING),"From the second phase its bite leaves the Hunger Mark");
        p.setHealth(20);b.setHealth(b.getMaxHealth()*.5F);float hp=b.getHealth();p.invulnerableTime=0;
        for(int i=0;i<41;i++)b.tick();
        h.assertTrue(p.getHealth()<20&&b.getHealth()>hp,"While it can see you, its star drinks from you and heals it");
        ServerLevel l=p.serverLevel();for(int x=-3;x<=3;x++)for(int y=0;y<6;y++)l.setBlock(BlockPos.containing(p.position().add(x,y,-1.5)),Blocks.BLACKSTONE.defaultBlockState(),3);
        for(int i=0;i<WitherGeometry.MARK_UNSEEN+1;i++)b.tick();
        h.assertTrue(!p.hasEffect(com.jayptucker.heroesevolved.registry.ModItems.HUNGERING),"Out of its sight long enough, the thread snaps");
        ServerPlayer healer=player(h,16,ModAbilities.REGENERATION_ID);WitherArena.enter(healer);
        healer.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.jayptucker.heroesevolved.registry.ModItems.HUNGERING,200));
        com.jayptucker.heroesevolved.combat.BossLocks.used(healer,ModAbilities.REGENERATION_ID,"test",20);
        h.assertTrue(!healer.hasEffect(com.jayptucker.heroesevolved.registry.ModItems.HUNGERING),"A cleansing power lifts its own mark");
        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.jayptucker.heroesevolved.registry.ModItems.HUNGERING,200));
        healer.setPos(p.position().add(0,0,4));look(healer,p.getBoundingBox().getCenter());
        com.jayptucker.heroesevolved.combat.BossLocks.used(healer,ModAbilities.REGENERATION_ID,"test",20);
        h.assertTrue(!p.hasEffect(com.jayptucker.heroesevolved.registry.ModItems.HUNGERING),"...or a marked friend's, by looking at them");
    
        }finally{clean(h);}h.succeed();});
    }
    @GameTest(template="rewind_empty",batch="wither_46")
    public static void eventHorizonIsAnsweredByPower(GameTestHelper h){clean(h);try{
        ServerPlayer pyro=player(h,16),ice=player(h,16,ModAbilities.CRYOKINESIS_ID),speed=player(h,16,ModAbilities.SUPERSPEED_ID),regen=player(h,16,ModAbilities.REGENERATION_ID);
        var b=fight(h,pyro);for(var q:List.of(ice,speed,regen))WitherArena.enter(q);b.operatorPhase();b.transition=0;b.operatorPhase();b.transition=0;b.unward();
        int n=0;for(var q:List.of(pyro,ice,speed,regen)){q.setPos(WitherGeometry.floor().add(-9+6*n++,0,12));q.setHealth(20);}regen.setHealth(10);
        b.start(WitherGeometry.Move.HORIZON);int windup=b.actionWindup();
        for(int i=0;i<windup;i++){if(i==windup-5)com.jayptucker.heroesevolved.combat.BossLocks.markUsed(speed);b.tick();}
        h.assertTrue(ice.getHealth()==20,"A shield power holds the discharge");
        h.assertTrue(speed.getHealth()==20,"A speedster who uses their power as it lands slips out of it");
        h.assertTrue(regen.getHealth()>=WitherGeometry.OUTLAST_LEFT-.5F&&regen.isAlive(),"A regenerator outlasts it");
        h.assertTrue(pyro.getHealth()<10,"A pyrokinetic who did not move in time takes it in full");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="wither_47")
    public static void openStarTakesMoreAndThreeBlowsCrackIt(GameTestHelper h){clean(h);try{
        ServerPlayer p=player(h,16);var b=fight(h,p);b.operatorPhase();b.transition=0;b.operatorPhase();b.transition=0;b.unward();p.setPos(b.position().add(0,0,14));look(p,p.position().add(10,0,0));
        ServerLevel l=p.serverLevel();
        var wide=EntityType.ARROW.create(l);wide.setPos(b.position().add(0,1,2));float hp=b.getHealth();b.invulnerableTime=0;b.hurt(b.damageSources().arrow(wide,p),6);float body=hp-b.getHealth();
        b.clock+=40;var onStar=EntityType.ARROW.create(l);onStar.setPos(b.core());hp=b.getHealth();b.invulnerableTime=0;b.hurt(b.damageSources().arrow(onStar,p),6);float star=hp-b.getHealth();
        h.assertTrue(star>body*1.5F,"A shot on its open star does more ("+star+" vs "+body+")");
        b.starHits.clear();b.cancelChain();b.start(WitherGeometry.Move.VERDICT);
        for(int i=0;i<3;i++){b.clock+=10;b.invulnerableTime=0;b.hurt(b.damageSources().arrow(onStar,p),6);}
        h.assertTrue(b.stagger>0&&b.casting==null,"Three blows on the star in quick succession crack it and it reels");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="wither_48")
    public static void wardShowsInStatusAndBreaksFromTheConsole(GameTestHelper h){clean(h);try{
        ServerPlayer p=player(h,16);var b=warded(h,p);
        h.assertTrue(WitherArena.status(p.getServer()).contains("Star Ward up: 3 / 3"),"Live status names the ward and its hearts");
        h.assertTrue(WitherArena.operatorStart(p,4).contains("shatters")&&!b.ward&&b.crash>0,"The console can break its ward");
        h.assertTrue(WitherArena.operatorStart(p,4).contains("No live Wither"),"...once");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="wither_49")
    public static void breachStepComesOutPastItsFocusAndShakesTheRing(GameTestHelper h){clean(h);try{
        ServerPlayer p=player(h,16);var b=fight(h,p);p.setPos(WitherGeometry.floor().add(0,0,-12));
        b.start(WitherGeometry.Move.STEP);Vec3 mark=b.marks.getFirst();
        h.assertTrue(mark.z<p.getZ()-3.5&&Math.abs(mark.x-p.getX())<.5,"It marks a spot past its focus, on the far side from it ("+mark+")");
        ServerPlayer q=player(h,16);WitherArena.enter(q);q.setPos(mark.add(.6,0,.4));
        for(int i=0;i<b.actionWindup();i++)b.tick();
        h.assertTrue(b.gate()!=null&&b.gateDepth(1)>1,"It is behind the far tear's plane, coming through it");
        for(int i=0;i<WitherGeometry.STEP_TICKS;i++)b.tick();
        h.assertTrue(b.position().distanceTo(mark.add(0,.3,0))<.2&&b.gate()==null,"It comes out on its mark, and the tear shuts behind it");
        h.assertTrue(q.getHealth()<20&&p.getHealth()==20,"The arrival shakes whoever stands on the ring, not its focus outside it");
        h.assertTrue(Math.abs(Mth.wrapDegrees(b.getYRot()-b.yawTo(p.position())))<30,"It comes out facing the one it stepped behind");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="wither_50")
    public static void tailReapSweepsItsBackAndAJumpClearsIt(GameTestHelper h){clean(h);try{
        ServerPlayer p=player(h,16),q=player(h,16);var b=fight(h,p);WitherArena.enter(q);b.setYRot(0);b.yBodyRot=0;
        p.setPos(b.position().add(0,-.35,-5));q.setPos(b.position().add(5,.8,0));
        h.assertTrue(b.atItsBack(),"A fighter hugging its back is noticed, so its next chain opens with the tail");
        b.start(WitherGeometry.Move.TAIL);float start=b.getYRot();
        for(int i=0;i<b.actionWindup()+WitherGeometry.TAIL_TICKS;i++)b.tick();
        h.assertTrue(p.getHealth()<20,"The tail sweeps a full circle and catches the one at its back");
        h.assertTrue(q.getHealth()==20,"A jump clears it");
        h.assertTrue(b.motion==null,"It spins once and stops");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="wither_51")
    public static void boneCageHoldsUntilAPowerBreaksIt(GameTestHelper h){clean(h);try{
        ServerPlayer p=player(h,16);var b=fight(h,p);b.operatorPhase();b.transition=0;p.setPos(WitherGeometry.floor().add(0,0,10));
        b.cancelChain();b.start(WitherGeometry.Move.CAGE);
        h.assertTrue(b.marks.getFirst().distanceTo(new Vec3(p.getX(),WitherGeometry.floor().y,p.getZ()))<.01,"Its ribs are thrown round where the fighter stood when it began");
        for(int i=0;i<b.actionWindup();i++)b.tick();
        h.assertTrue(b.cage!=null&&b.cage.held.containsKey(p.getUUID())&&p.getHealth()<20,"Standing inside when it closes, they are caged");
        h.assertTrue(b.chain.peekFirst()==WitherGeometry.Move.VERDICT,"Its three mouths come next for the caged");
        Vec3 at=p.position();p.setPos(at.add(1.5,0,0));b.tick();
        h.assertTrue(p.position().distanceTo(at)<.35,"The cage holds them where they stood");
        com.jayptucker.heroesevolved.combat.BossLocks.markUsed(p);b.tick();
        h.assertTrue(b.cage==null,"A power used breaks the bars at once");
        b.cancelChain();b.start(WitherGeometry.Move.CAGE);float health=p.getHealth();
        p.setPos(p.position().add(8,0,0));for(int i=0;i<b.actionWindup();i++)b.tick();
        h.assertTrue(b.cage!=null&&b.cage.held.isEmpty()&&p.getHealth()==health,"Stepping out of the ring before it closes beats it");
        for(int i=0;i<14;i++)b.tick();h.assertTrue(b.cage==null,"An empty cage sinks soon");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="wither_52")
    public static void starvingWellDriftsPullsAndCollapses(GameTestHelper h){clean(h);try{
        ServerPlayer p=player(h,16);var b=fight(h,p);b.operatorPhase();b.transition=0;b.operatorPhase();b.transition=0;b.unward();
        p.setPos(WitherGeometry.floor().add(0,0,20));b.cancelChain();b.start(WitherGeometry.Move.WELL);
        for(int i=0;i<b.actionWindup();i++)b.tick();
        h.assertTrue(b.wells.size()==1,"It spits a starving star");
        var w=b.wells.getFirst();Vec3 target=p.position();double before=w.at.distanceTo(target);
        for(int i=0;i<20;i++)b.tick();
        h.assertTrue(w.at.distanceTo(target)<before-2,"It drifts after the fighter farthest from the Wither");
        p.setDeltaMovement(Vec3.ZERO);w.at=new Vec3(p.getX()+4,WitherGeometry.floor().y+WitherGeometry.WELL_HEIGHT,p.getZ());b.tick();
        h.assertTrue(p.getDeltaMovement().x>0,"It pulls whoever is near toward it");
        ServerPlayer q=player(h,16);WitherArena.enter(q);q.setPos(p.position().add(-10,0,0));
        w.age=WitherGeometry.WELL_LIFE-1;w.at=new Vec3(p.getX(),WitherGeometry.floor().y+WitherGeometry.WELL_HEIGHT,p.getZ());p.setHealth(20);b.tick();
        h.assertTrue(b.wells.isEmpty()&&p.getHealth()<20&&q.getHealth()==20,"It collapses on whoever it caught, and only them");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="wither_53")
    public static void gearFromOtherModsCannotTrivialiseIt(GameTestHelper h){clean(h);try{
        ServerPlayer p=player(h,16);var b=fight(h,p);
        for(var slot:new EquipmentSlot[]{EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET})
            p.setItemSlot(slot,new ItemStack(switch(slot){case HEAD->Items.NETHERITE_HELMET;case CHEST->Items.NETHERITE_CHESTPLATE;case LEGS->Items.NETHERITE_LEGGINGS;default->Items.NETHERITE_BOOTS;}));
        float before=p.getHealth();WitherMoves.hit(b,p,10,WitherGeometry.Move.BITE);
        h.assertTrue(before-p.getHealth()>=10*WitherGeometry.PIERCE-.01F,"Its star's hunger goes through the best armour ("+(before-p.getHealth())+")");
        ServerPlayer big=player(h,16);WitherArena.enter(big);big.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100);big.setHealth(100);
        WitherMoves.hit(b,big,16,WitherGeometry.Move.SLAM);
        h.assertTrue(100-big.getHealth()>=16+100*WitherGeometry.HEAVY_SHARE-.5F,"A heavy blow takes a share of a big health pool too");
        float hp=b.getHealth();b.invulnerableTime=0;b.hurt(b.damageSources().playerAttack(p),100000);
        h.assertTrue(hp-b.getHealth()<=b.getMaxHealth()*RiftWitherEntity.HIT_CAP+.01F,"No weapon takes more than a twentieth of it in one blow");
        float after=b.getHealth();for(int i=0;i<30;i++){b.invulnerableTime=0;b.hurt(b.damageSources().playerAttack(p),100000);}
        h.assertTrue(after-b.getHealth()<.01F,"And nothing more in that second, however many blows");
        h.assertTrue(1/RiftWitherEntity.SECOND_CAP>=40,"A flawless fight is at least forty seconds of damage");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="wither_54")
    public static void fasterPaceNeverCutsAWarningUnderItsFloor(GameTestHelper h){
        for(var move:WitherGeometry.Move.values())for(int phase=1;phase<=3;phase++)for(int cast=0;cast<3;cast++)for(boolean starving:new boolean[]{false,true})
            h.assertTrue(WitherGeometry.castWindup(move,phase,cast,starving)>=WitherGeometry.floor(move),move+" keeps its reading time in phase "+phase);
        for(int phase=1;phase<=3;phase++)for(int turn=0;turn<6;turn++)for(double d:new double[]{4,12,25}){
            var chain=WitherGeometry.chain(phase,turn,d,false);
            if(chain[0]!=WitherGeometry.Move.HORIZON)h.assertTrue(chain.length>=3,"Every chain but the discharge is three moves or more");
        }
        h.assertTrue(WitherGeometry.restScale(1)<=.6F&&WitherGeometry.headShotGap(1)<=60&&WitherGeometry.Move.STEP.rest==0,"It rests less, its side heads fire more often, and a Breach Step strikes on");
        h.assertTrue(List.of(WitherGeometry.chain(1,1,25,false)).contains(WitherGeometry.Move.STEP)&&List.of(WitherGeometry.chain(2,1,5,false)).contains(WitherGeometry.Move.CAGE)
                &&List.of(WitherGeometry.chain(3,1,12,false)).contains(WitherGeometry.Move.WELL)&&List.of(WitherGeometry.chain(1,0,5,false)).contains(WitherGeometry.Move.TAIL),"Its new ways to fight are in its chains");
        h.succeed();
    }
    @GameTest(template="rewind_empty",batch="wither_37")
    public static void tetherIntoBiteAndDoubleCharge(GameTestHelper h){clean(h);try{
        ServerPlayer p=player(h,16);var b=fight(h,p);b.operatorPhase();b.transition=0;p.setPos(b.position().add(0,0,12));
        b.chain.clear();b.start(WitherGeometry.Move.TETHER);for(int i=0;i<b.actionWindup();i++)b.tick();
        h.assertTrue(b.chain.peekFirst()==WitherGeometry.Move.BITE&&b.spent==0,"From the second phase a hauled-in fighter is bitten next, at once");
        b.cancelChain();b.operatorPhase();b.transition=0;b.setPos(WitherGeometry.floor().add(0,.35,-4));p.setPos(WitherGeometry.floor().add(0,0,-12));p.setHealth(20);p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE,400,4));
        b.start(WitherGeometry.Move.LUNGE);h.assertTrue(!b.lungeCrash,"An open lane");
        for(int i=0;i<b.actionWindup()+WitherGeometry.LUNGE_TICKS;i++)b.tick();
        h.assertTrue(b.chain.peekFirst()==WitherGeometry.Move.LUNGE&&b.crash==0,"In Critical Mass a charge that misses turns and comes again");
    }finally{clean(h);}h.succeed();}
}
