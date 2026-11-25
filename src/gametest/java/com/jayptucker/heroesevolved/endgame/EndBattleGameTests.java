package com.jayptucker.heroesevolved.endgame;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.mixin.EndFightAccess;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.registry.ModItems;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.Connection;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.boss.enderdragon.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;
import java.lang.reflect.Field;
import java.util.*;

/** The actual native End, native multipart dragon, native fight and ordinary power damage paths. */
@GameTestHolder("heroesevolved") @PrefixGameTestTemplate(false)
public final class EndBattleGameTests {
    private static final List<ServerPlayer> LIVE=new ArrayList<>();
    private static ServerLevel end(GameTestHelper h){return h.getLevel().getServer().getLevel(Level.END);}
    private static void clean(GameTestHelper h){
        EndBattle.reset(h.getLevel().getServer(),false);EndChannel.clear();
        for(ServerPlayer p:LIVE){p.discard();if(p.connection.getConnection().channel()!=null)p.connection.getConnection().channel().close();}LIVE.clear();
        ServerLevel level=end(h);if(level!=null){
            for(Entity e:java.util.stream.StreamSupport.stream(level.getAllEntities().spliterator(),false).toList())
                if(e instanceof EnderDragon||e instanceof EndCrystal||e instanceof EndBolt||e instanceof ExperienceOrb)e.discard();
        }
        h.getLevel().getServer().overworld().getDataStorage().set(EndData.NAME,new EndData());
    }
    static ServerPlayer player(GameTestHelper h,int powerLevel){
        return player(h,powerLevel,false);
    }
    static ServerPlayer player(GameTestHelper h,int powerLevel,boolean real){
        ServerLevel level=end(h);GameProfile profile=new GameProfile(UUID.randomUUID(),"SealTester");
        ServerPlayer p=real?new ServerPlayer(level.getServer(),level,profile,ClientInformation.createDefault()):new FakePlayer(level,profile){@Override public boolean isInvulnerableTo(DamageSource s){return false;}};
        try{
            Field grace=ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");grace.setAccessible(true);grace.setInt(p,0);
            Connection connection=p.connection==null?new Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND):p.connection.getConnection();Field channel=Connection.class.getDeclaredField("channel");channel.setAccessible(true);channel.set(connection,new EmbeddedChannel());
            if(p.connection==null)p.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(p.getServer(),connection,p,net.minecraft.server.network.CommonListenerCookie.createInitial(profile,false)){@Override public void send(net.minecraft.network.protocol.Packet<?> packet){}};
        }catch(ReflectiveOperationException error){throw new AssertionError(error);}
        p.setGameMode(GameType.SURVIVAL);p.setPos(12.5,66,.5);p.setHealth(20);
        p.setData(ModDataAttachments.PLAYER_ABILITIES,PlayerAbilityData.empty().replaceWithUnlockedPower(ModAbilities.PYROKINESIS_ID));
        PlayerProgressionService.setPowerLevel(p,powerLevel);p.hasChangedDimension();LIVE.add(p);return p;
    }
    static EndBattle.Encounter fight(GameTestHelper h,ServerPlayer p){
        ServerLevel level=end(h);var fight=level.getDragonFight();var access=(EndFightAccess)fight;
        access.heroes$needsScan(false);access.heroes$dragonKilled(false);
        EnderDragon dragon=EntityType.ENDER_DRAGON.create(level);dragon.setPos(.5,90,.5);dragon.setDragonFight(fight);access.heroes$dragonUUID(dragon.getUUID());level.addFreshEntity(dragon);
        List<EndCrystal> added=new ArrayList<>();for(int i=0;i<4;i++){EndCrystal c=EntityType.END_CRYSTAL.create(level);c.setPos(Math.cos(i*Math.PI/2)*42,86,Math.sin(i*Math.PI/2)*42);level.getChunk(c.blockPosition());level.addFreshEntity(c);added.add(c);}
        var e=EndBattle.begin(dragon,List.of(p));for(EndCrystal c:added)if(!e.crystals.contains(c))e.crystals.add(c);
        e.still(true);dragon.aiStep();e.holdOff(10000);return e;
    }
    private static void done(GameTestHelper h){clean(h);h.succeed();}
    /** Stand a player on the island's real surface. */
    static void stand(EndBattle.Encounter e,ServerPlayer p,double x,double z){p.setPos(x,e.brain.ground(x,z),z);p.setDeltaMovement(Vec3.ZERO);}
    /** Put the Dragon down on the surface facing yaw, as a landing leaves it. */
    static Vec3 landed(EndBattle.Encounter e,double x,double z,float yaw){
        Vec3 at=new Vec3(x,e.brain.ground(x,z),z);e.brain.cancelAll();e.dragon.setPos(at);e.dragon.setYRot(yaw);e.grounded=200;return at;
    }
    @GameTest(template="rewind_empty",batch="endgame_01",timeoutTicks=400)
    public static void nativeDragonAcceptsSoloAndPreservesMultipartIdentity(GameTestHelper h){clean(h);try{
        ServerPlayer p=player(h,15);h.assertTrue(!EndBattle.eligible(p),"Level 15 refused");PlayerProgressionService.setPowerLevel(p,16);var e=fight(h,p);
        h.assertTrue(EndBattle.nativeDragon(e.dragon)&&EndBattle.managed(e.dragon)&&e.dragon.getSubEntities().length==8,"Original multipart dragon owns encounter");
        h.assertTrue(e.dragon.getMaxHealth()==900&&e.fighters().size()==1,"Solo fight scaled to one awakened player");
        e.grounded=200;p.setPos(e.dragon.position().add(0,0,10));float hp=e.dragon.getHealth();e.dragon.hurt(e.dragon.damageSources().playerAttack(p),20);
        h.assertTrue(e.dragon.getHealth()<hp,"Parent-body ability damage reaches native head path");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="endgame_02",timeoutTicks=400)
    public static void lateJoinPreservesHealthFractionAboveVanillaAttributeCeiling(GameTestHelper h){clean(h);try{
        var e=fight(h,player(h,16));e.dragon.setHealth(450);ServerPlayer q=player(h,17);e.join(q);
        h.assertTrue(e.dragon.getMaxHealth()==1000&&Math.abs(e.dragon.getHealth()/e.dragon.getMaxHealth()-.5)<.001,"1350 effective health respects attribute ceiling and keeps fraction");
        h.assertTrue(e.damage.effective()==1350,"Virtual health retains party difficulty");e.grounded=200;q.setPos(e.dragon.position().add(0,0,10));float hp=e.dragon.getHealth();
        e.dragon.hurt(e.dragon.damageSources().playerAttack(q),10000);float first=hp-e.dragon.getHealth();h.assertTrue(first<=60.01F&&first>0,"One enormous hit capped to six percent");
        hp=e.dragon.getHealth();for(int i=0;i<30;i++){e.dragon.invulnerableTime=0;e.dragon.hurt(e.dragon.damageSources().playerAttack(q),10000);}h.assertTrue(e.dragon.getHealth()==hp,"Same-tick burst shares budget");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="endgame_03",timeoutTicks=400)
    public static void onlyFeedingCrystalBreaksAndGroundsWithoutExplosion(GameTestHelper h){clean(h);try{
        var p=player(h,16);var e=fight(h,p);h.assertTrue(e.crystals.size()>=4,"Native feed crystals discovered");var active=e.feed();var other=e.crystals.stream().filter(c->c!=active).findFirst().orElseThrow();
        h.assertTrue(!other.hurt(other.damageSources().playerAttack(p),40)&&!other.isRemoved(),"Inactive crystal shielded");
        BlockPos marker=active.blockPosition().below();e.level.setBlock(marker,Blocks.DIAMOND_BLOCK.defaultBlockState(),3);float health=e.dragon.getHealth();
        h.assertTrue(active.hurt(active.damageSources().playerAttack(p),1)&&active.isRemoved(),"Active crystal safely overloaded");
        h.assertTrue(e.grounded==240&&e.dragon.getHealth()<health&&e.level.getBlockState(marker).is(Blocks.DIAMOND_BLOCK),"Grounding hurts boss without native explosion");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="endgame_04",timeoutTicks=400)
    public static void breathRunIsFixedDodgeableAndLeavesBurningPools(GameTestHelper h){clean(h);try{
        var p=player(h,16);var e=fight(h,p);stand(e,p,16.5,15.5);e.start(EndGeometry.Move.BREATH);var fixed=List.copyOf(e.marks);
        h.assertTrue(fixed.size()==EndGeometry.RUN_POOLS&&EndGeometry.stripDistance(e.brain.runA,e.brain.runB,p.position())<.6,"The run's strip is laid through the fighter");
        Vec3 side=new Vec3(-e.brain.dir.z,0,e.brain.dir.x);Vec3 away=p.position().add(side.scale(12));stand(e,p,away.x,away.z);float hp=p.getHealth();
        for(int i=0;i<EndGeometry.Move.BREATH.windup+EndGeometry.RUN_TICKS+2;i++)e.tick();
        h.assertTrue(p.getHealth()==hp&&e.marks.equals(fixed),"The strip stays where it was marked and stepping off it is safe");
        h.assertTrue(e.brain.pools.size()==EndGeometry.RUN_POOLS&&!e.brain.busy(),"The Dragon really flew the strip and left every pool");
        Vec3 pool=fixed.get(EndGeometry.RUN_POOLS/2).add(0,-.9,0);stand(e,p,pool.x,pool.z);for(int i=0;i<21;i++)e.tick();h.assertTrue(p.getHealth()<hp,"A pool keeps burning anyone who stands in it");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="endgame_05",timeoutTicks=400)
    public static void novaOffersReachableAirborneSafeOrbit(GameTestHelper h){clean(h);try{
        var p=player(h,16);var e=fight(h,p);p.setPos(25,110,20);e.start(EndGeometry.Move.UNMAKING);Vec3 safe=e.marks.get(0);h.assertTrue(safe.distanceTo(p.position().add(0,.9,0))<8,"Safe orbit within ordinary movement reach");
        p.setPos(safe.add(0,-.9,0));float hp=p.getHealth();for(int i=0;i<70;i++)e.tick();h.assertTrue(p.getHealth()==hp,"Safe orbit protects at flight height");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="endgame_06",timeoutTicks=400)
    public static void falseVictoryDefersNativeExitAndSurvivesRuntimeRestart(GameTestHelper h){clean(h);try{
        var p=player(h,16);var e=fight(h,p);var dragon=e.dragon;
        e.level.getDragonFight().setDragonKilled(dragon);h.assertTrue(e.stage==EndBattle.ABSORPTION&&e.sylar!=null,"Native completion intercepted and Sylar arrives");
        h.assertTrue(!e.data.completed&&EndBattle.holdNativeFight(e.level),"Exit progression held while inheritance remains");
        CompoundTag saved=e.data.save(new CompoundTag(),e.level.registryAccess());var loaded=EndData.load(saved,e.level.registryAccess());h.assertTrue(loaded.checkpoint==EndBattle.SYLAR&&loaded.dragon.equals(dragon.getUUID()),"Checkpoint survives NBT");
        EndBattle.reset(p.getServer(),true);h.assertTrue(EndData.get(p.getServer()).checkpoint==EndBattle.SYLAR,"Runtime reset preserves defeated Dragon checkpoint");
        var resumed=new EndBattle.Encounter(end(h),EndData.get(p.getServer()),null);resumed.startAbsorption(new Vec3(0,90,0));h.assertTrue(resumed.sylar!=null,"Reload can spawn inheritance actor without original Dragon");resumed.cleanup();
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="endgame_07",timeoutTicks=400)
    public static void echoPrismBackfiresWithoutTakingPlayerPower(GameTestHelper h){clean(h);try{
        var p=player(h,16);var e=fight(h,p);e.startAbsorption(new Vec3(0,90,0));e.beginSylar();e.transition=0;e.sampled=10;e.spawnPrism();float hp=e.sylar.getHealth();
        h.assertTrue(e.prism.hurt(e.prism.damageSources().playerAttack(p),12),"Echo prism accepts power strike");
        h.assertTrue(e.prism==null&&e.stagger>0&&e.sylar.getHealth()<hp,"Broken prism backfires and exposes Sylar");
        h.assertTrue(p.getData(ModDataAttachments.PLAYER_ABILITIES).assignedPower().isPresent(),"Player retains real gift");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="endgame_08",timeoutTicks=400)
    public static void everyGiftHasPatternAndStableNetworkSeed(GameTestHelper h){
        Set<EndMoves.Pattern> shapes=EnumSet.noneOf(EndMoves.Pattern.class);for(int i=0;i<21;i++){int seed=EndMoves.seed(EndGeometry.Move.PATTERN,i,1);shapes.add(EndMoves.pattern(i));h.assertTrue(EndMoves.power(seed)==i&&EndMoves.role(seed)==1&&EndMoves.move(seed)==EndGeometry.Move.PATTERN,"Pattern seed round trips");}
        h.assertTrue(shapes.size()>=13,"Gifts use distinct geometry families");h.assertTrue(EndMoves.windup(EndGeometry.Move.PATTERN,6)==70,"Radiation receives full nova warning");h.succeed();
    }
    @GameTest(template="rewind_empty",batch="endgame_09",timeoutTicks=400)
    public static void savedSceneryRestoresOnlyOwnedBlocks(GameTestHelper h){clean(h);try{
        var e=fight(h,player(h,16));BlockPos a=new BlockPos(110,67,0),b=a.east();e.level.setBlock(a,Blocks.PURPUR_BLOCK.defaultBlockState(),3);e.level.setBlock(b,Blocks.DIAMOND_BLOCK.defaultBlockState(),3);
        e.data.scenery.put(a,new EndData.Change(Blocks.END_STONE.defaultBlockState(),Blocks.PURPUR_BLOCK.defaultBlockState()));e.data.scenery.put(b,new EndData.Change(Blocks.END_STONE.defaultBlockState(),Blocks.PURPUR_BLOCK.defaultBlockState()));
        EndScenery.restore(e.level,e.data);h.assertTrue(e.level.getBlockState(a).is(Blocks.END_STONE)&&e.level.getBlockState(b).is(Blocks.DIAMOND_BLOCK),"Expected state restored, player replacement preserved");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="endgame_10",timeoutTicks=400)
    public static void deathXpCapturedOncePerNativeAwardTick(GameTestHelper h){clean(h);try{
        var e=fight(h,player(h,16));e.dragon.dragonDeathTime=160;EndBattle.captureXP(e.dragon,960);EndBattle.captureXP(e.dragon,960);e.dragon.dragonDeathTime=165;EndBattle.captureXP(e.dragon,960);
        h.assertTrue(e.data.pendingXP==1920,"Reload or repeated callbacks cannot duplicate Dragon XP");
    }finally{clean(h);}h.succeed();}
    private static void face(ServerPlayer p,Vec3 focus){p.setPos(focus.add(0,-3.5,2.4));Vec3 d=focus.subtract(p.getEyePosition());p.setYRot((float)Math.toDegrees(Math.atan2(-d.x,d.z)));p.setXRot((float)-Math.toDegrees(Math.atan2(d.y,Math.sqrt(d.x*d.x+d.z*d.z))));}
    private static void fill(ServerPlayer p){com.jayptucker.heroesevolved.energy.PlayerEnergyService.consumeUpTo(p,100000);com.jayptucker.heroesevolved.energy.PlayerEnergyService.restore(p,100);}
    @GameTest(template="rewind_empty",batch="endgame_11",timeoutTicks=400)
    public static void soloChannelPaysFractionalStaminaAndSwallowsKeyUp(GameTestHelper h){clean(h);try{
        var p=player(h,16);var e=fight(h,p);face(p,e.data.anchors.get(e.active));fill(p);
        h.assertTrue(EndChannel.press(p,"slot:0")==com.jayptucker.heroesevolved.ability.AbilityUseResult.SUCCESS&&EndChannel.active(p),"Ordinary ability input begins solo grounding");
        for(int i=0;i<EndTuning.CHANNEL_TICKS;i++)EndChannel.tick();
        h.assertTrue(e.grounded==240&&!EndChannel.active(p),"One player completes grounding");h.assertTrue(Math.abs(com.jayptucker.heroesevolved.energy.PlayerEnergyService.getStamina(p)-76)<.001,"Exact 24 fractional stamina paid");
        h.assertTrue(EndChannel.release(p,"slot:0")&&!EndChannel.release(p,"slot:0"),"Completion owns precisely one release receipt");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="endgame_12",timeoutTicks=400)
    public static void cooperatingPlayersShareChargeAndDamageCancelsChannel(GameTestHelper h){clean(h);try{
        var p=player(h,16);var e=fight(h,p);var q=player(h,16);e.join(q);Vec3 focus=e.data.anchors.get(e.active);face(p,focus);face(q,focus);
        fill(p);fill(q);
        EndChannel.press(p,"key:primary");EndChannel.press(q,"key:primary");for(int i=0;i<30;i++)EndChannel.tick();
        h.assertTrue(e.grounded==240&&!EndChannel.active(p)&&!EndChannel.active(q),"Partners finish in half the time");
        h.assertTrue(Math.abs(com.jayptucker.heroesevolved.energy.PlayerEnergyService.getStamina(p)+com.jayptucker.heroesevolved.energy.PlayerEnergyService.getStamina(q)-176)<.001,"Cooperation shares total cost");
        face(p,e.data.anchors.get(e.active));EndChannel.press(p,"slot:1");h.assertTrue(EndChannel.active(p),"Next focus can be held");p.hurt(p.damageSources().mobAttack(e.dragon),1);h.assertTrue(!EndChannel.active(p),"Real incoming damage releases held channel");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="endgame_13",timeoutTicks=400)
    public static void movementAndSuppressionCancelWithoutSpendingWholeCost(GameTestHelper h){clean(h);try{
        var p=player(h,16);var e=fight(h,p);face(p,e.data.anchors.get(e.active));fill(p);EndChannel.press(p,"slot:0");
        for(int i=0;i<10;i++)EndChannel.tick();p.setPos(p.position().add(1,0,0));EndChannel.tick();h.assertTrue(!EndChannel.active(p)&&Math.abs(com.jayptucker.heroesevolved.energy.PlayerEnergyService.getStamina(p)-96)<.001,"Moving interrupts and only paid ticks are charged");
        face(p,e.data.anchors.get(e.active));EndChannel.press(p,"slot:1");e.suppressed.put(p.getUUID(),e.clock+40);EndChannel.tick();h.assertTrue(!EndChannel.active(p),"Canonical PowerRules suppression stops channel");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="endgame_14",timeoutTicks=400)
    public static void ordinaryWindupsInterruptButNovaKeepsFullWarning(GameTestHelper h){clean(h);try{
        var p=player(h,16);var e=fight(h,p);p.setPos(e.dragon.position().add(0,0,-8));e.grounded=200;e.start(EndGeometry.Move.BITE);e.dragon.hurt(e.dragon.damageSources().playerAttack(p),8);
        h.assertTrue(e.casting==null&&e.stagger>0,"Heavy strike interrupts ordinary windup");e.clock+=120;e.stagger=0;e.dragon.invulnerableTime=0;e.start(EndGeometry.Move.UNMAKING);e.dragon.hurt(e.dragon.damageSources().playerAttack(p),8);h.assertTrue(e.casting==EndGeometry.Move.UNMAKING,"Ultimate keeps committed warning");
        e.startAbsorption(new Vec3(0,90,0));e.beginSylar();e.transition=e.stagger=0;e.clock+=120;e.sampled=6;e.start(EndGeometry.Move.PATTERN);e.sylar.invulnerableTime=0;e.sylar.hurt(e.sylar.damageSources().playerAttack(p),8);h.assertTrue(e.casting==EndGeometry.Move.PATTERN,"Sampled radiation has same ultimate rule");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="endgame_15",timeoutTicks=400)
    public static void refugeCannotDamageBossAndDistantDamageFallsOff(GameTestHelper h){clean(h);try{
        var p=player(h,16);var e=fight(h,p);p.setPos(e.data.refuge);float hp=e.dragon.getHealth();e.dragon.hurt(e.dragon.damageSources().playerAttack(p),100);h.assertTrue(e.dragon.getHealth()==hp,"Refuge is no firing platform");
        e.grounded=200;p.setPos(e.dragon.position().add(200,0,0));e.dragon.hurt(e.dragon.damageSources().playerAttack(p),100);h.assertTrue(e.dragon.getHealth()==hp,"Outside arena cannot snipe");
        e.dragon.setPos(-90,80,0);p.setPos(90,80,0);e.dragon.hurt(e.dragon.damageSources().playerAttack(p),100);h.assertTrue(e.dragon.getHealth()==hp,"Distant hits inside opposing arena edges fall to zero");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="endgame_16",timeoutTicks=400)
    public static void nativeLethalHitEntersDyingPhaseAndDefersExperience(GameTestHelper h){clean(h);try{
        var p=player(h,16);var e=fight(h,p);e.grounded=200;e.dragon.setHealth(2);p.setPos(e.dragon.position().add(0,0,10));e.dragon.hurt(e.dragon.damageSources().playerAttack(p),10);
        h.assertTrue(e.stage==EndBattle.DRAGON_DEATH&&e.data.checkpoint==EndBattle.SYLAR&&e.dragon.getHealth()==1,"Lethal native hit records checkpoint and preserves death flight");
        h.assertTrue(e.dragon.getPhaseManager().getCurrentPhase().getPhase()==net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase.DYING,"Native dying phase retained");
        e.dragon.setHealth(0);for(int i=0;i<202&&!e.dragon.isRemoved();i++)e.dragon.tick();
        h.assertTrue(e.stage==EndBattle.ABSORPTION&&e.data.pendingXP==e.data.expectedXP,"Native 200 tick death creates inheritance and captures native XP");
        h.assertTrue(e.level.getEntitiesOfClass(ExperienceOrb.class,new AABB(-20,40,-20,20,120,20)).isEmpty(),"No early XP escape");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="endgame_17",timeoutTicks=400)
    public static void realDeathDropsAreSavedAndDeliveredOnce(GameTestHelper h){clean(h);try{
        var p=player(h,16,true);var e=fight(h,p);p.setPos(15,72,15);p.getInventory().add(new ItemStack(Items.DIAMOND,7));p.hurt(p.damageSources().genericKill(),10000);
        var stash=e.data.take(p.getUUID(),p.registryAccess());h.assertTrue(stash.stream().filter(s->s.is(Items.DIAMOND)).mapToInt(ItemStack::getCount).sum()==7,"Real death stashes inventory");h.assertTrue(e.data.take(p.getUUID(),p.registryAccess()).isEmpty(),"Stash delivered once");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="endgame_18",timeoutTicks=400)
    public static void anchorConsumesOnlyOnFatalEndVoidFall(GameTestHelper h){clean(h);try{
        var p=player(h,16,true);fight(h,p);p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,new ItemStack(ModItems.ANCHOR_OF_RETURN.get()));p.setHealth(1);p.hurt(p.damageSources().fellOutOfWorld(),100);
        h.assertTrue(p.isAlive()&&p.getOffhandItem().isEmpty()&&p.getY()>50,"Fatal void fall consumes anchor and returns player alive");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="endgame_19",timeoutTicks=400)
    public static void riftdiveFliesThroughItsMarkAndHitsOnce(GameTestHelper h){clean(h);try{
        var p=player(h,16);var e=fight(h,p);stand(e,p,30.5,.5);e.start(EndGeometry.Move.RIFT_CHARGE);Vec3 spot=e.brain.spot;
        h.assertTrue(spot.multiply(1,0,1).distanceTo(p.position().multiply(1,0,1))<.6,"The dive marks the ground under its target");
        int whole=EndGeometry.arrival(EndGeometry.Move.RIFT_CHARGE)+EndGeometry.DIVE_PULLOUT+2;
        for(int i=0;i<whole;i++)e.tick();
        h.assertTrue(p.getHealth()<20&&p.getHealth()>=20-11,"The body and the slam hit the marked fighter exactly once");
        h.assertTrue(!e.brain.busy()&&e.dragon.position().multiply(1,0,1).distanceTo(spot.multiply(1,0,1))>20,"It flew through the mark and pulled out beyond it");
        p.setHealth(20);stand(e,p,30.5,.5);e.dragon.setPos(.5,90,.5);e.start(EndGeometry.Move.RIFT_CHARGE);Vec3 side=new Vec3(-e.brain.dir.z,0,e.brain.dir.x);
        Vec3 away=p.position().add(side.scale(EndGeometry.DIVE_RADIUS+EndGeometry.DIVE_IMPACT));stand(e,p,away.x,away.z);
        for(int i=0;i<whole;i++)e.tick();h.assertTrue(p.getHealth()==20,"Stepping sideways off the marked lane beats the dive");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="endgame_20",timeoutTicks=400)
    public static void finishingVictoryReleasesNativeExitAndPaysOnce(GameTestHelper h){clean(h);try{
        var p=player(h,16);var e=fight(h,p);e.startAbsorption(new Vec3(0,90,0));e.beginSylar();e.transition=0;e.phase=3;e.sylar.setHealth(e.sylar.getMaxHealth()*.08F);EndBattle.finishSylar(e.sylar,p);
        h.assertTrue(e.stage==EndBattle.VICTORY&&!e.data.completed,"Finisher starts a death scene before reward");for(int i=0;i<EndGeometry.DEATH_TICKS;i++)e.tick();
        h.assertTrue(e.data.completed&&e.data.paid&&e.data.checkpoint==0,"Final win clears checkpoint and pays captured XP");h.assertTrue(e.level.getDragonFight().hasPreviouslyKilledDragon(),"Native egg/gateway/exit progression completed");
        int prizes=e.prizes.size();e.complete();h.assertTrue(prizes==2&&e.prizes.size()==prizes,"One core and first anchor, with no duplicate completion");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="endgame_21",timeoutTicks=400)
    public static void ruptureWaveIsADeathSceneAndLeavesPlayersAndTerrainIntact(GameTestHelper h){clean(h);try{
        var p=player(h,16);var e=fight(h,p);e.startAbsorption(new Vec3(0,90,0));e.beginSylar();e.sylar.setPos(12,70,0);p.setPos(14,70,0);BlockPos marker=new BlockPos(12,69,0);e.level.setBlock(marker,Blocks.DIAMOND_BLOCK.defaultBlockState(),3);
        var lingering=com.jayptucker.heroesevolved.registry.ModEntities.END_BOLT.get().create(e.level);e.bolts.add(lingering);e.startVictory();h.assertTrue(lingering.isRemoved()&&!EndMoves.hit(e,p,8,-1),"Victory clears lingering boss hazards and rejects cinematic damage");
        for(int i=0;i<EndGeometry.RUPTURE_TICK+35;i++)e.tick();
        h.assertTrue(!e.data.completed&&e.sylar.isAlive()&&e.sylar.castPose()==com.jayptucker.heroesevolved.story.sylar.SylarEntity.Pose.RUPTURE,"Cracking body and blast finish before rewards");
        h.assertTrue(p.getHealth()==20&&e.level.getBlockState(marker).is(Blocks.DIAMOND_BLOCK),"Victory wave does not damage players or their builds");h.assertTrue(EndGeometry.releaseRadius(e.stageAge)>EndGeometry.ARENA_RADIUS,"Power front crosses the whole island");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="endgame_22",timeoutTicks=400)
    public static void ordinaryJumpClearsTerrainFollowingPressureWave(GameTestHelper h){clean(h);try{
        var p=player(h,16);var e=fight(h,p);double ground=EndGeometry.waveHeight(e.level,12,.5,64);p.setPos(12,ground+1.15,.5);e.start(EndGeometry.Move.WINGBREAK);for(int i=0;i<43;i++)e.tick();
        h.assertTrue(p.getHealth()==20,"Normal jump apex clears wave on actual End terrain");e.waves.clear();p.setPos(12,ground,.5);e.start(EndGeometry.Move.WINGBREAK);for(int i=0;i<43;i++)e.tick();h.assertTrue(p.getHealth()<20,"Standing in the same spot is hit");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="endgame_23",timeoutTicks=400)
    public static void unloadedInheritanceActorIsNotCountedAsDefeated(GameTestHelper h){clean(h);try{
        var p=player(h,16);var e=fight(h,p);e.startAbsorption(new Vec3(0,90,0));e.beginSylar();e.sylar.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);e.tick();
        h.assertTrue(EndBattle.current()==null&&!e.data.completed&&e.data.checkpoint==EndBattle.SYLAR&&!e.data.paid,"Chunk unload preserves checkpoint without awarding a victory");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="endgame_24",timeoutTicks=220)
    public static void craftedSigilStartsActualNativeResurrection(GameTestHelper h){clean(h);
        ServerPlayer p=player(h,16);try{var e=fight(h,p);e.startAbsorption(new Vec3(0,90,0));e.dragon.discard();e.beginSylar();e.complete();}
        catch(RuntimeException|Error failure){clean(h);throw failure;}
        h.runAtTickTime(100,()->{try{
            var level=end(h);var access=(EndFightAccess)level.getDragonFight();BlockPos fountain=access.heroes$portal();h.assertTrue(fountain!=null,"Native final victory created its fountain");
            p.setPos(Vec3.atBottomCenterOf(fountain).add(4,2,0));p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(ModItems.WORLDLINE_SIGIL.get()));
            var result=ModItems.WORLDLINE_SIGIL.get().use(level,p,net.minecraft.world.InteractionHand.MAIN_HAND);
            h.assertTrue(result.getResult()==net.minecraft.world.InteractionResult.CONSUME&&p.getMainHandItem().isEmpty(),"One crafted sigil is consumed on success");
            h.assertTrue(access.heroes$respawn()!=null,"The actual native four-crystal resurrection has started");
        }finally{clean(h);}h.succeed();});
    }
    @GameTest(template="rewind_empty",batch="endgame_25",timeoutTicks=400)
    public static void standingStillDoesNotEvadeUltimate(GameTestHelper h){clean(h);try{
        var p=player(h,16);var e=fight(h,p);p.setPos(25,110,20);e.start(EndGeometry.Move.UNMAKING);for(int i=0;i<70;i++)e.tick();
        h.assertTrue(p.getHealth()<20,"Original position lies outside the fixed sector and nearby shelter, so the player must answer the warning");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="endgame_26",timeoutTicks=400)
    public static void retreatReturnsBesideSourcePortalInsteadOfReenteringIt(GameTestHelper h){clean(h);
        var source=h.getLevel();BlockPos base=h.absolutePos(new BlockPos(5,2,5));Map<BlockPos,net.minecraft.world.level.block.state.BlockState> before=new HashMap<>();
        try{
            for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)for(int y=-1;y<=2;y++){BlockPos at=base.offset(x,y,z);before.put(at,source.getBlockState(at));source.setBlock(at,y==-1?Blocks.STONE.defaultBlockState():y==0&&Math.abs(x)<=1&&Math.abs(z)<=1?Blocks.END_PORTAL.defaultBlockState():Blocks.AIR.defaultBlockState(),3);}
            var p=player(h,16);var e=fight(h,p);e.data.home(p.getUUID(),new EndData.Home(source.dimension(),Vec3.atBottomCenterOf(base),0,0));EndBattle.returnHome(p);
            h.assertTrue(p.level()==source&&!source.getBlockState(p.blockPosition()).is(Blocks.END_PORTAL)&&!source.getBlockState(p.blockPosition().below()).is(Blocks.END_PORTAL),"Retreat finds walkable ground beside the saved source portal");
            h.assertTrue(p.isOnPortalCooldown()&&source.noCollision(p,p.getBoundingBox()),"Return is unobstructed and has portal cooldown");
        }finally{before.forEach((at,state)->source.setBlock(at,state,3));clean(h);}h.succeed();
    }
    @GameTest(template="rewind_empty",batch="endgame_27",timeoutTicks=400)
    public static void reflectedBoltRespectsPvpWhileBossBoltRemainsDangerous(GameTestHelper h){clean(h);boolean pvp=h.getLevel().getServer().isPvpAllowed();try{
        var p=player(h,16);var e=fight(h,p);var q=player(h,16);e.join(q);q.setPos(14,90,10);p.getServer().setPvpAllowed(false);
        var reflected=com.jayptucker.heroesevolved.registry.ModEntities.END_BOLT.get().create(e.level);reflected.setOwner(p);reflected.setPos(q.position());reflected.onHit(new EntityHitResult(q));
        h.assertTrue(q.getHealth()==20,"A player's redirected bolt respects disabled PvP");
        var hostile=com.jayptucker.heroesevolved.registry.ModEntities.END_BOLT.get().create(e.level);hostile.setOwner(e.dragon);hostile.setPos(q.position());hostile.onHit(new EntityHitResult(q));
        h.assertTrue(q.getHealth()<20,"The boss's original bolt still hits fighters");
    }finally{h.getLevel().getServer().setPvpAllowed(pvp);clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="endgame_28",timeoutTicks=400)
    public static void descentSlamsOnlyTheMarkedCircleThenFightsOnFoot(GameTestHelper h){clean(h);try{
        var p=player(h,16);var e=fight(h,p);stand(e,p,20.5,.5);e.start(EndGeometry.Move.DESCENT);Vec3 spot=e.brain.spot;
        stand(e,p,spot.x+EndGeometry.LAND_RADIUS+2,spot.z);for(int i=0;i<EndGeometry.arrival(EndGeometry.Move.DESCENT)+2;i++)e.tick();
        h.assertTrue(p.getHealth()==20,"Outside the gold circle is safe");
        h.assertTrue(e.grounded>0&&e.dragon.position().distanceTo(spot)<1&&!e.brain.busy(),"It lands exactly on the mark and stays down to fight");
        h.assertTrue(e.view().grounded()>0,"Clients are told it is down, so it sits instead of flapping");
        e.grounded=0;e.dragon.setPos(.5,90,.5);stand(e,p,20.5,.5);e.start(EndGeometry.Move.DESCENT);for(int i=0;i<EndGeometry.arrival(EndGeometry.Move.DESCENT)+2;i++)e.tick();
        h.assertTrue(p.getHealth()<20,"Staying in the circle is hit by the landing");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="endgame_29",timeoutTicks=400)
    public static void tailSweepsBehindAndBiteReachesAhead(GameTestHelper h){clean(h);try{
        var p=player(h,16);var e=fight(h,p);Vec3 body=landed(e,10.5,.5,0);Vec3 ahead=EndGeometry.forward(0);
        stand(e,p,body.x-ahead.x*8,body.z-ahead.z*8);e.start(EndGeometry.Move.TAIL);for(int i=0;i<=EndGeometry.Move.TAIL.windup;i++)e.tick();
        h.assertTrue(p.getHealth()<20,"The tail sweeps the arc behind it");
        p.setHealth(20);body=landed(e,10.5,.5,0);e.clock+=200;stand(e,p,body.x+ahead.x*8,body.z+ahead.z*8);e.start(EndGeometry.Move.TAIL);for(int i=0;i<=EndGeometry.Move.TAIL.windup;i++)e.tick();
        h.assertTrue(p.getHealth()==20,"Standing in front of it is safe from the tail");
        body=landed(e,10.5,.5,0);stand(e,p,body.x+ahead.x*9,body.z+ahead.z*9);e.start(EndGeometry.Move.BITE);for(int i=0;i<=EndGeometry.Move.BITE.windup;i++)e.tick();
        h.assertTrue(p.getHealth()<20,"The bite reaches along the lane ahead");
        for(int i=0;i<12;i++)e.tick();h.assertTrue(e.dragon.position().distanceTo(body)<.05,"The lunge returns the body to where it stood");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="endgame_30",timeoutTicks=400)
    public static void groundedDragonTakesFullDamageAndItsHeadIsTheWeakPoint(GameTestHelper h){clean(h);try{
        var p=player(h,16);var e=fight(h,p);p.setPos(e.dragon.position().add(0,0,10));
        float hp=e.dragon.getHealth();e.dragon.hurt(e.dragon.damageSources().playerAttack(p),10);float air=hp-e.dragon.getHealth();
        e.clock+=40;e.grounded=200;e.dragon.invulnerableTime=0;hp=e.dragon.getHealth();e.dragon.hurt(e.dragon.damageSources().playerAttack(p),10);float ground=hp-e.dragon.getHealth();
        e.clock+=40;e.dragon.invulnerableTime=0;hp=e.dragon.getHealth();e.dragon.head.hurt(e.dragon.damageSources().playerAttack(p),10);float head=hp-e.dragon.getHealth();
        e.clock+=40;e.dragon.invulnerableTime=0;hp=e.dragon.getHealth();e.dragon.getSubEntities()[6].hurt(e.dragon.damageSources().playerAttack(p),10);float wing=hp-e.dragon.getHealth();
        h.assertTrue(Math.abs(air-10*EndGeometry.AIR_DAMAGE)<.01&&Math.abs(ground-10)<.01,"In the air it takes "+air+", on the ground "+ground);
        h.assertTrue(Math.abs(head-10*EndGeometry.HEAD_DAMAGE)<.01&&Math.abs(wing-10*EndGeometry.BODY_DAMAGE)<.01,"Head takes "+head+", a wing "+wing);
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="endgame_31",timeoutTicks=400)
    public static void dragonHuntsThenLandsThenTakesOff(GameTestHelper h){clean(h);try{
        var p=player(h,16);var e=fight(h,p);stand(e,p,20.5,.5);
        h.assertTrue(e.brain.next()==EndGeometry.Move.RIFT_CHARGE,"It opens in the air with a dive");
        e.brain.airMoves=EndGeometry.AIR_MOVES[0];h.assertTrue(e.brain.next()==EndGeometry.Move.DESCENT,"After its air moves it comes down");
        Vec3 body=landed(e,10.5,.5,0);Vec3 ahead=EndGeometry.forward(0);e.brain.last=null;
        stand(e,p,body.x-ahead.x*8,body.z-ahead.z*8);h.assertTrue(e.brain.next()==EndGeometry.Move.TAIL,"Someone behind it gets the tail");
        stand(e,p,body.x+ahead.x*8,body.z+ahead.z*8);h.assertTrue(e.brain.next()==EndGeometry.Move.BITE,"Someone in front gets the bite");
        e.grounded=EndGeometry.Move.TAKEOFF.windup;h.assertTrue(e.brain.next()==EndGeometry.Move.TAKEOFF,"Its time on the ground ends with a take-off");
        e.start(EndGeometry.Move.TAKEOFF);for(int i=0;i<=EndGeometry.Move.TAKEOFF.windup+26;i++)e.tick();
        h.assertTrue(e.grounded==0&&e.dragon.getY()>body.y+15&&e.brain.cycle==1,"It climbs away and starts a new hunt");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="endgame_32",timeoutTicks=400)
    public static void sealRushChargesWhoeverStandsOutOfReach(GameTestHelper h){clean(h);
        // The charge only runs over loaded island, so keep the fight's ground loaded.
        var level=end(h);for(int x=0;x<=3;x++)for(int z=-1;z<=0;z++)level.setChunkForced(x,z,true);try{
        var p=player(h,16);var e=fight(h,p);Vec3 body=landed(e,10.5,.5,0);e.brain.last=null;int run=EndGeometry.Move.RUSH.windup+EndGeometry.RUSH_TICKS+2;
        // A flat stretch of island for the lane (other tests leave altars and markers standing here).
        int top=(int)Math.floor(e.data.floor)-1;
        for(int x=4;x<=44;x++)for(int z=-6;z<=6;z++){level.setBlock(new BlockPos(x,top,z),Blocks.END_STONE.defaultBlockState(),2);for(int y=top+1;y<=top+12;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);}
        body=landed(e,10.5,.5,0);stand(e,p,body.x+20,body.z);var chosen=e.brain.next();
        h.assertTrue(chosen==EndGeometry.Move.RUSH,"Someone out of reach at its side gets charged (chose "+chosen+", lane "+e.brain.lane(p)+")");
        e.start(EndGeometry.Move.RUSH);Vec3 end=e.target;
        h.assertTrue(end!=null&&end.x>body.x+20&&Math.abs(end.z-body.z)<.5,"The lane runs through them and on past");
        int ground=e.grounded;for(int i=0;i<run;i++)e.tick();
        h.assertTrue(p.getHealth()<20,"Standing in the marked lane is a hit");
        h.assertTrue(e.dragon.position().multiply(1,0,1).distanceTo(end.multiply(1,0,1))<.5,"It skids to a stop at the lane's end");
        h.assertTrue(ground-e.grounded<=3,"Its time on the ground waits while a ground move plays out ("+(ground-e.grounded)+" of "+run+" ticks spent)");
        p.setHealth(20);body=landed(e,10.5,.5,0);e.brain.last=null;e.clock+=200;stand(e,p,body.x+20,body.z);e.start(EndGeometry.Move.RUSH);
        stand(e,p,body.x+20,body.z+7);for(int i=0;i<run;i++)e.tick();
        h.assertTrue(p.getHealth()==20,"Stepping out of the lane once it is marked beats the charge");
        body=landed(e,10.5,.5,0);e.brain.last=null;stand(e,p,body.x+8,body.z);
        h.assertTrue(e.brain.next()!=EndGeometry.Move.RUSH,"Close enough to turn and bite, it does not charge");
    }finally{for(int x=0;x<=3;x++)for(int z=-1;z<=0;z++)level.setChunkForced(x,z,false);clean(h);}h.succeed();}
}
