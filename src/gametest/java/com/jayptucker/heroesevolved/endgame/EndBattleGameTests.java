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

/** The actual native End, the Living Seal on the native multipart Dragon (its locks, moves and windows), Sylar's stage, and progression. */
@GameTestHolder("heroesevolved") @PrefixGameTestTemplate(false)
public final class EndBattleGameTests {
    private static final List<ServerPlayer> LIVE=new ArrayList<>();
    private static ServerLevel end(GameTestHelper h){return h.getLevel().getServer().getLevel(Level.END);}
    private static void clean(GameTestHelper h){
        EndBattle.reset(h.getLevel().getServer(),false);EndChannel.clear();
        for(ServerPlayer p:LIVE){p.discard();if(p.connection.getConnection().channel()!=null)p.connection.getConnection().channel().close();}LIVE.clear();
        ServerLevel level=end(h);if(level!=null){
            for(Entity e:java.util.stream.StreamSupport.stream(level.getAllEntities().spliterator(),false).toList())
                if(e instanceof EnderDragon||e instanceof EndCrystal||e instanceof EndBolt||e instanceof SealKnot||e instanceof ExperienceOrb)e.discard();
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
        var e=EndBattle.begin(dragon,List.of(p));for(EndCrystal c:added)if(!e.crystals.contains(c))e.crystals.add(c);e.seal.assignFeeders();
        e.still(true);dragon.aiStep();e.holdOff(10000);return e;
    }
    private static void done(GameTestHelper h){clean(h);h.succeed();}
    /** Stand a player on the island's real surface. */
    static void stand(EndBattle.Encounter e,ServerPlayer p,double x,double z){p.setPos(x,e.seal.ground(x,z),z);p.setDeltaMovement(Vec3.ZERO);}
    /** Put the Dragon down on the surface facing yaw, as a landing leaves it. */
    static Vec3 landed(EndBattle.Encounter e,double x,double z,float yaw){
        Vec3 at=new Vec3(x,e.seal.ground(x,z),z);e.seal.landAt(at,yaw);return at;
    }
    @GameTest(template="rewind_empty",batch="endgame_01",timeoutTicks=400)
    public static void nativeDragonAcceptsSoloAndPreservesMultipartIdentity(GameTestHelper h){clean(h);try{
        ServerPlayer p=player(h,15);h.assertTrue(!EndBattle.eligible(p),"Level 15 refused");PlayerProgressionService.setPowerLevel(p,16);var e=fight(h,p);
        h.assertTrue(EndBattle.nativeDragon(e.dragon)&&EndBattle.managed(e.dragon)&&e.dragon.getSubEntities().length==8,"Original multipart dragon owns encounter");
        h.assertTrue(Math.abs(e.damage.effective()-SealGeometry.BASE_HEALTH)<.01&&e.fighters().size()==1,"Solo fight scaled to one awakened player at the baseline level");
        h.assertTrue(e.seal.warding()==4&&e.seal.warded(),"It arrives warded by four knots");
        e.grounded=200;p.setPos(e.dragon.position().add(0,0,10));float hp=e.dragon.getHealth();e.dragon.hurt(e.dragon.damageSources().playerAttack(p),20);
        h.assertTrue(e.dragon.getHealth()<hp,"Parent-body ability damage reaches native head path");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="endgame_02",timeoutTicks=400)
    public static void lateJoinPreservesHealthFractionAboveVanillaAttributeCeiling(GameTestHelper h){clean(h);try{
        var e=fight(h,player(h,16));e.seal.shattered=true;e.dragon.setHealth(450);ServerPlayer q=player(h,17);e.join(q);
        float weight=1+com.jayptucker.heroesevolved.combat.BossScaling.strength(true,17,16),wanted=SealGeometry.BASE_HEALTH*com.jayptucker.heroesevolved.combat.BossScaling.healthScale(weight);
        h.assertTrue(e.dragon.getMaxHealth()==1000&&Math.abs(e.dragon.getHealth()/e.dragon.getMaxHealth()-450F/800)<.001,"Party health past the attribute ceiling keeps its fraction");
        h.assertTrue(Math.abs(e.damage.effective()-wanted)<.01,"Virtual health follows the party's strength ("+e.damage.effective()+" vs "+wanted+")");
        e.grounded=200;q.setPos(e.dragon.position().add(0,0,10));float hp=e.dragon.getHealth();
        e.dragon.hurt(e.dragon.damageSources().playerAttack(q),10000);float first=hp-e.dragon.getHealth();h.assertTrue(first<=e.dragon.getMaxHealth()*SealGeometry.HIT_CAP+.01F&&first>0,"One enormous hit capped to five percent");
        hp=e.dragon.getHealth();for(int i=0;i<30;i++){e.dragon.invulnerableTime=0;e.dragon.hurt(e.dragon.damageSources().playerAttack(q),10000);}h.assertTrue(e.dragon.getHealth()==hp,"Same-tick burst shares the per-second budget");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="endgame_03",timeoutTicks=400)
    public static void towerCrystalBreaksQuietlyAndCutsItsKnotsFeed(GameTestHelper h){clean(h);try{
        var p=player(h,16);var e=fight(h,p);h.assertTrue(e.crystals.size()>=4,"Native tower crystals discovered");
        int altar=-1;for(int i=0;i<4;i++)if(e.seal.feeders[i]!=null){altar=i;break;}h.assertTrue(altar>=0,"Each altar's knot is fed by a tower crystal");
        var crystal=e.seal.feeders[altar];BlockPos marker=crystal.blockPosition().below();e.level.setBlock(marker,Blocks.DIAMOND_BLOCK.defaultBlockState(),3);
        h.assertTrue(crystal.hurt(crystal.damageSources().playerAttack(p),1)&&crystal.isRemoved(),"A fighter breaks a tower crystal");
        h.assertTrue(e.seal.feeders[altar]==null&&e.level.getBlockState(marker).is(Blocks.DIAMOND_BLOCK),"It goes dark without a native explosion and stops feeding its knot");
        var knot=e.seal.knots[altar];while(!knot.isRemoved())knot.hurt(knot.damageSources().playerAttack(p),1000);
        h.assertTrue(e.seal.mend[altar]==0,"A knot with no crystal won't mend");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="endgame_04",timeoutTicks=400)
    public static void breathRunIsFixedDodgeableAndLeavesBurningPools(GameTestHelper h){clean(h);try{
        var p=player(h,16);var e=fight(h,p);stand(e,p,16.5,15.5);h.assertTrue(e.seal.start(SealGeometry.Move.BREATH),"It can breathe from the air");var fixed=List.copyOf(e.marks);
        h.assertTrue(fixed.size()==SealGeometry.RUN_POOLS&&EndGeometry.stripDistance(e.seal.runA,e.seal.runB,p.position())<.6,"The run's strip is laid through the fighter");
        Vec3 side=new Vec3(-e.seal.dir.z,0,e.seal.dir.x);Vec3 away=p.position().add(side.scale(12));stand(e,p,away.x,away.z);float hp=p.getHealth();
        for(int i=0;i<e.seal.castTotal+SealGeometry.RUN_TICKS+2;i++)e.tick();
        h.assertTrue(p.getHealth()==hp&&e.marks.equals(fixed),"The strip stays where it was marked and stepping off it is safe");
        h.assertTrue(e.seal.pools.size()==SealGeometry.RUN_POOLS&&!e.seal.busy(),"The Dragon really flew the strip and left every pool");
        Vec3 pool=fixed.get(SealGeometry.RUN_POOLS/2).add(0,-.9,0);stand(e,p,pool.x,pool.z);for(int i=0;i<21;i++)e.tick();h.assertTrue(p.getHealth()<hp,"A pool keeps burning anyone who stands in it");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="endgame_05",timeoutTicks=400)
    public static void novaOffersReachableAirborneSafeOrbit(GameTestHelper h){clean(h);try{
        var p=player(h,16);var e=fight(h,p);p.setPos(25,110,20);e.seal.start(SealGeometry.Move.UNMAKING);Vec3 safe=e.marks.get(0);h.assertTrue(safe.distanceTo(p.position().add(0,.9,0))<8,"Safe orbit within ordinary movement reach");
        p.setPos(safe.add(0,-.9,0));float hp=p.getHealth();for(int i=0;i<72;i++)e.tick();h.assertTrue(p.getHealth()==hp,"Safe orbit protects at flight height");
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
    /** The altars are Sylar's counter-resonators: move a fresh fight to his stage. */
    private static EndBattle.Encounter sylarStage(GameTestHelper h,ServerPlayer p){var e=fight(h,p);e.startAbsorption(new Vec3(0,90,0));e.beginSylar();e.transition=0;return e;}
    @GameTest(template="rewind_empty",batch="endgame_11",timeoutTicks=400)
    public static void soloChannelPaysFractionalStaminaAndSwallowsKeyUp(GameTestHelper h){clean(h);try{
        var p=player(h,16);var e=sylarStage(h,p);face(p,e.data.anchors.get(0));fill(p);
        h.assertTrue(EndChannel.press(p,"slot:0")==com.jayptucker.heroesevolved.ability.AbilityUseResult.SUCCESS&&EndChannel.active(p),"Ordinary ability input begins a solo counter-resonance");
        for(int i=0;i<EndTuning.CHANNEL_TICKS;i++)EndChannel.tick();
        h.assertTrue(e.stagger==60&&!EndChannel.active(p),"One player completes the overload and Sylar reels");h.assertTrue(Math.abs(com.jayptucker.heroesevolved.energy.PlayerEnergyService.getStamina(p)-76)<.001,"Exact 24 fractional stamina paid");
        h.assertTrue(EndChannel.release(p,"slot:0")&&!EndChannel.release(p,"slot:0"),"Completion owns precisely one release receipt");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="endgame_12",timeoutTicks=400)
    public static void cooperatingPlayersShareChargeAndDamageCancelsChannel(GameTestHelper h){clean(h);try{
        var p=player(h,16);var e=sylarStage(h,p);var q=player(h,16);e.join(q);Vec3 focus=e.data.anchors.get(0);face(p,focus);face(q,focus);
        fill(p);fill(q);
        EndChannel.press(p,"key:primary");EndChannel.press(q,"key:primary");for(int i=0;i<30;i++)EndChannel.tick();
        h.assertTrue(e.stagger==60&&!EndChannel.active(p)&&!EndChannel.active(q),"Partners finish in half the time");
        h.assertTrue(Math.abs(com.jayptucker.heroesevolved.energy.PlayerEnergyService.getStamina(p)+com.jayptucker.heroesevolved.energy.PlayerEnergyService.getStamina(q)-176)<.001,"Cooperation shares total cost");
        face(p,e.data.anchors.get(1));EndChannel.press(p,"slot:1");h.assertTrue(EndChannel.active(p),"Another focus can be held");p.hurt(p.damageSources().mobAttack(e.sylar),1);h.assertTrue(!EndChannel.active(p),"Real incoming damage releases held channel");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="endgame_13",timeoutTicks=400)
    public static void movementAndSuppressionCancelWithoutSpendingWholeCost(GameTestHelper h){clean(h);try{
        var p=player(h,16);var e=sylarStage(h,p);face(p,e.data.anchors.get(0));fill(p);EndChannel.press(p,"slot:0");
        for(int i=0;i<10;i++)EndChannel.tick();p.setPos(p.position().add(1,0,0));EndChannel.tick();h.assertTrue(!EndChannel.active(p)&&Math.abs(com.jayptucker.heroesevolved.energy.PlayerEnergyService.getStamina(p)-96)<.001,"Moving interrupts and only paid ticks are charged");
        face(p,e.data.anchors.get(0));EndChannel.press(p,"slot:1");e.suppressed.put(p.getUUID(),e.clock+40);EndChannel.tick();h.assertTrue(!EndChannel.active(p),"Canonical PowerRules suppression stops channel");
        var d=fight(h,p);face(p,d.data.anchors.get(0));h.assertTrue(EndChannel.press(p,"slot:0")!=com.jayptucker.heroesevolved.ability.AbilityUseResult.SUCCESS,"While the Dragon lives the altars hold its knots, not a channel");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="endgame_14",timeoutTicks=400)
    public static void threeChargedHitsBreakItsStanceButNotTheUnmaking(GameTestHelper h){clean(h);try{
        var p=player(h,16);var e=fight(h,p);Vec3 body=landed(e,10.5,.5,0);stand(e,p,body.x,body.z+8);e.seal.start(SealGeometry.Move.SCORCH);
        for(int i=0;i<3;i++){e.clock+=25;e.dragon.invulnerableTime=0;e.dragon.hurt(e.dragon.damageSources().playerAttack(p),9);}
        h.assertTrue(e.seal.casting==null&&e.seal.stagger>0,"Three charged hits in its windups break its stance");
        e.clock+=200;e.seal.stagger=0;e.seal.start(SealGeometry.Move.UNMAKING);
        for(int i=0;i<3;i++){e.clock+=25;e.dragon.invulnerableTime=0;e.dragon.hurt(e.dragon.damageSources().playerAttack(p),9);}
        h.assertTrue(e.seal.casting==SealGeometry.Move.UNMAKING,"The Unmaking keeps its whole warning");
        e.startAbsorption(new Vec3(0,90,0));e.beginSylar();e.transition=e.stagger=0;e.clock+=120;e.sampled=6;e.start(EndGeometry.Move.PATTERN);e.sylar.invulnerableTime=0;e.sylar.hurt(e.sylar.damageSources().playerAttack(p),8);h.assertTrue(e.casting==EndGeometry.Move.PATTERN,"Sampled radiation has the same ultimate rule");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="endgame_15",timeoutTicks=400)
    public static void refugeCannotDamageBossAndDistantDamageFallsOff(GameTestHelper h){clean(h);try{
        var p=player(h,16);var e=fight(h,p);p.setPos(e.data.refuge);float hp=e.dragon.getHealth();e.dragon.hurt(e.dragon.damageSources().playerAttack(p),100);h.assertTrue(e.dragon.getHealth()==hp,"Refuge is no firing platform");
        e.grounded=200;p.setPos(e.dragon.position().add(200,0,0));e.dragon.hurt(e.dragon.damageSources().playerAttack(p),100);h.assertTrue(e.dragon.getHealth()==hp,"Outside arena cannot snipe");
        e.dragon.setPos(-90,80,0);p.setPos(90,80,0);e.dragon.hurt(e.dragon.damageSources().playerAttack(p),100);h.assertTrue(e.dragon.getHealth()==hp,"Distant hits inside opposing arena edges fall to zero");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="endgame_16",timeoutTicks=400)
    public static void nativeLethalHitEntersDyingPhaseAndDefersExperience(GameTestHelper h){clean(h);try{
        var p=player(h,16);var e=fight(h,p);e.seal.shattered=true;e.grounded=200;e.dragon.setHealth(2);p.setPos(e.dragon.position().add(0,0,10));e.dragon.hurt(e.dragon.damageSources().playerAttack(p),10);
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
        var p=player(h,16);var e=fight(h,p);stand(e,p,30.5,.5);e.seal.start(SealGeometry.Move.RIFTDIVE);Vec3 spot=e.seal.spot;
        h.assertTrue(spot.multiply(1,0,1).distanceTo(p.position().multiply(1,0,1))<.6,"The dive marks the ground under its target");
        int whole=e.seal.castTotal+SealGeometry.DIVE_APPROACH+SealGeometry.DIVE_PULLOUT+2;
        for(int i=0;i<whole;i++)e.tick();
        h.assertTrue(p.getHealth()<20&&p.getHealth()>0,"The body and the slam hit the marked fighter exactly once ("+p.getHealth()+" left)");
        h.assertTrue(!e.seal.busy()&&e.dragon.position().multiply(1,0,1).distanceTo(spot.multiply(1,0,1))>20,"It flew through the mark and pulled out beyond it");
        p.setHealth(20);stand(e,p,30.5,.5);e.dragon.setPos(.5,90,.5);e.seal.start(SealGeometry.Move.RIFTDIVE);Vec3 side=new Vec3(-e.seal.dir.z,0,e.seal.dir.x);
        Vec3 away=p.position().add(side.scale(SealGeometry.DIVE_RADIUS+SealGeometry.DIVE_IMPACT));stand(e,p,away.x,away.z);
        whole=e.seal.castTotal+SealGeometry.DIVE_APPROACH+SealGeometry.DIVE_PULLOUT+2;
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
        var p=player(h,16);var e=fight(h,p);landed(e,.5,.5,0);double ground=EndGeometry.waveHeight(e.level,12,.5,64);p.setPos(12,ground+1.15,.5);e.seal.start(SealGeometry.Move.BUFFET);for(int i=0;i<e.seal.castTotal+14;i++)e.tick();
        h.assertTrue(p.getHealth()==20,"Normal jump apex clears wave on actual End terrain");e.seal.rings.clear();landed(e,.5,.5,0);e.clock+=40;p.setPos(12,ground,.5);e.seal.start(SealGeometry.Move.BUFFET);for(int i=0;i<e.seal.castTotal+14;i++)e.tick();h.assertTrue(p.getHealth()<20,"Standing in the same spot is hit");
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
        var p=player(h,16);var e=fight(h,p);p.setPos(25,110,20);e.seal.start(SealGeometry.Move.UNMAKING);for(int i=0;i<72;i++)e.tick();
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
    public static void returnedShardSparesAlliesAndHitsTheDragonPastTheSeal(GameTestHelper h){clean(h);try{
        var p=player(h,16);var e=fight(h,p);var q=player(h,16);e.join(q);q.setPos(14,90,10);
        var reflected=com.jayptucker.heroesevolved.registry.ModEntities.END_BOLT.get().create(e.level);reflected.setOwner(p);reflected.setPos(q.position());reflected.onHit(new EntityHitResult(q));
        h.assertTrue(q.getHealth()==20,"A shard sent back never hurts a fighter");
        var hostile=com.jayptucker.heroesevolved.registry.ModEntities.END_BOLT.get().create(e.level);hostile.setOwner(e.dragon);hostile.setPos(q.position());hostile.onHit(new EntityHitResult(q));
        h.assertTrue(q.getHealth()<20,"The Dragon's own shard still hits fighters");
        float hp=e.dragon.getHealth();var back=com.jayptucker.heroesevolved.registry.ModEntities.END_BOLT.get().create(e.level);back.setOwner(p);back.setPos(SealGeometry.chest(e.dragon));back.onHit(new EntityHitResult(e.dragon));
        h.assertTrue(hp-e.dragon.getHealth()>=e.dragon.getMaxHealth()*SealGeometry.RETURNED_SHARE*.95F,"A returned shard takes its share past the Seal ("+(hp-e.dragon.getHealth())+")");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="endgame_28",timeoutTicks=400)
    public static void descentSlamsOnlyTheMarkedCircleThenFightsOnFoot(GameTestHelper h){clean(h);try{
        var p=player(h,16);var e=fight(h,p);stand(e,p,20.5,.5);e.seal.start(SealGeometry.Move.DESCENT);Vec3 spot=e.seal.spot;
        stand(e,p,spot.x+SealGeometry.LAND_RADIUS+2,spot.z);for(int i=0;i<e.seal.castTotal+SealGeometry.LAND_DROP+2;i++)e.tick();
        h.assertTrue(p.getHealth()==20,"Outside the gold circle is safe");
        h.assertTrue(e.grounded>0&&e.dragon.position().distanceTo(spot)<1&&!e.seal.busy(),"It lands exactly on the mark and stays down to fight");
        h.assertTrue(e.view().grounded()>0&&!e.view().seal().has(SealView.AIRBORNE),"Clients are told it is down, so it sits instead of flapping");
        e.seal.liftTo(new Vec3(.5,90,.5));stand(e,p,20.5,.5);e.seal.start(SealGeometry.Move.DESCENT);for(int i=0;i<e.seal.castTotal+SealGeometry.LAND_DROP+2;i++)e.tick();
        h.assertTrue(p.getHealth()<20,"Staying in the circle is hit by the landing");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="endgame_29",timeoutTicks=400)
    public static void tailSweepsBehindAndBiteReachesAhead(GameTestHelper h){clean(h);try{
        var p=player(h,16);var e=fight(h,p);Vec3 body=landed(e,10.5,.5,0);Vec3 ahead=SealGeometry.forward(0);
        stand(e,p,body.x-ahead.x*8,body.z-ahead.z*8);e.seal.start(SealGeometry.Move.TAIL);for(int i=0;i<=e.seal.castTotal;i++)e.tick();
        h.assertTrue(p.getHealth()<20,"The tail sweeps the arc behind it");
        p.setHealth(20);body=landed(e,10.5,.5,0);e.clock+=200;stand(e,p,body.x+ahead.x*8,body.z+ahead.z*8);e.seal.start(SealGeometry.Move.TAIL);for(int i=0;i<=e.seal.castTotal;i++)e.tick();
        h.assertTrue(p.getHealth()==20,"Standing in front of it is safe from the tail");
        body=landed(e,10.5,.5,0);stand(e,p,body.x+ahead.x*9,body.z+ahead.z*9);e.seal.start(SealGeometry.Move.BITE);for(int i=0;i<=e.seal.castTotal;i++)e.tick();
        h.assertTrue(p.getHealth()<20,"The bite reaches along the lane ahead");
        for(int i=0;i<12;i++)e.tick();h.assertTrue(e.dragon.position().distanceTo(body)<.05,"The lunge returns the body to where it stood");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="endgame_30",timeoutTicks=400)
    public static void powersReachItInTheAirBladesDoLittleAndItsHeadIsTheWeakPoint(GameTestHelper h){clean(h);try{
        var p=player(h,16);var e=fight(h,p);e.seal.shattered=true;p.setPos(e.dragon.position().add(0,0,10));
        float hp=e.dragon.getHealth();e.dragon.hurt(e.dragon.damageSources().playerAttack(p),10);float air=hp-e.dragon.getHealth();
        e.clock+=40;e.dragon.invulnerableTime=0;p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_SWORD));hp=e.dragon.getHealth();e.dragon.hurt(e.dragon.damageSources().playerAttack(p),10);float blade=hp-e.dragon.getHealth();
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        e.clock+=40;e.grounded=200;e.dragon.invulnerableTime=0;hp=e.dragon.getHealth();e.dragon.hurt(e.dragon.damageSources().playerAttack(p),10);float ground=hp-e.dragon.getHealth();
        e.clock+=40;e.dragon.invulnerableTime=0;hp=e.dragon.getHealth();e.dragon.head.hurt(e.dragon.damageSources().playerAttack(p),10);float head=hp-e.dragon.getHealth();
        e.clock+=40;e.dragon.invulnerableTime=0;hp=e.dragon.getHealth();e.dragon.getSubEntities()[6].hurt(e.dragon.damageSources().playerAttack(p),10);float wing=hp-e.dragon.getHealth();
        float reach=com.jayptucker.heroesevolved.combat.BossLocks.STRONG_HITS*SealGeometry.AIR_TAKES;
        h.assertTrue(Math.abs(air-10*reach)<.01&&Math.abs(ground-10)<.01,"A fire jet's blows reach it in the air ("+air+"), and a power does full damage on the ground ("+ground+")");
        h.assertTrue(Math.abs(blade-10*com.jayptucker.heroesevolved.combat.BossLocks.PLAIN*SealGeometry.AIR_TAKES)<.01,"A blade does a quarter to it in the air ("+blade+")");
        h.assertTrue(Math.abs(head-10*SealGeometry.HEAD_TAKES)<.01&&Math.abs(wing-10*SealGeometry.BODY_TAKES)<.01,"Head takes "+head+", a wing "+wing);
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="endgame_31",timeoutTicks=400)
    public static void itHuntsInChainsLandsTakesOffAndStaysDownInTheLastPhase(GameTestHelper h){clean(h);try{
        var p=player(h,16);var e=fight(h,p);stand(e,p,20.5,.5);
        e.seal.choose();h.assertTrue(e.seal.chain.stream().allMatch(m->m.air)&&e.seal.chain.size()>=2,"It opens in the air with a chain of air moves");
        e.seal.choose();e.seal.choose();h.assertTrue(e.seal.chain.peekFirst()==SealGeometry.Move.DESCENT,"After its air chains it comes down");
        Vec3 body=landed(e,10.5,.5,0);Vec3 ahead=SealGeometry.forward(0);
        stand(e,p,body.x-ahead.x*6,body.z-ahead.z*6);e.seal.action=null;e.seal.choose();h.assertTrue(e.seal.chain.peekFirst()==SealGeometry.Move.TAIL,"Someone hugging its back meets the tail first");
        stand(e,p,body.x+ahead.x*6,body.z+ahead.z*6);e.seal.choose();h.assertTrue(e.seal.chain.contains(SealGeometry.Move.BITE),"Someone close in front gets the bite");
        e.grounded=SealGeometry.Move.TAKEOFF.windup;e.seal.choose();h.assertTrue(e.seal.chain.peekFirst()==SealGeometry.Move.TAKEOFF,"Its time on the ground ends with a take-off");
        e.seal.chain.clear();e.seal.start(SealGeometry.Move.TAKEOFF);for(int i=0;i<=e.seal.castTotal+26;i++)e.tick();
        h.assertTrue(e.grounded==0&&e.dragon.getY()>body.y+15,"It climbs away and starts a new hunt");
        landed(e,10.5,.5,0);e.phase=3;e.grounded=1;boolean unmaking=false,takeoff=false;
        for(int i=0;i<8;i++){e.seal.choose();unmaking|=e.seal.chain.contains(SealGeometry.Move.UNMAKING);takeoff|=e.seal.chain.contains(SealGeometry.Move.TAKEOFF);}
        h.assertTrue(unmaking&&!takeoff,"In the last phase its wings are torn: it stays down, and The Unmaking comes every fourth chain");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="endgame_32",timeoutTicks=400)
    public static void sealRushChargesWhoeverStandsOutOfReach(GameTestHelper h){clean(h);
        // The charge only runs over loaded island, so keep the fight's ground loaded.
        var level=end(h);for(int x=0;x<=3;x++)for(int z=-1;z<=0;z++)level.setChunkForced(x,z,true);try{
        var p=player(h,16);var e=fight(h,p);
        // A flat stretch of island for the lane (other tests leave altars and markers standing here).
        int top=(int)Math.floor(e.data.floor)-1;
        for(int x=4;x<=44;x++)for(int z=-6;z<=6;z++){level.setBlock(new BlockPos(x,top,z),Blocks.END_STONE.defaultBlockState(),2);for(int y=top+1;y<=top+12;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);}
        Vec3 body=landed(e,10.5,.5,0);stand(e,p,body.x+20,body.z);e.seal.choose();
        h.assertTrue(e.seal.chain.peekFirst()==SealGeometry.Move.RUSH,"Someone far off at its side gets charged (lane "+e.seal.lane(p)+")");
        e.seal.chain.clear();h.assertTrue(e.seal.start(SealGeometry.Move.RUSH),"The lane reaches them");Vec3 end=e.target;
        h.assertTrue(end!=null&&end.x>body.x+20&&Math.abs(end.z-body.z)<.5,"The lane runs through them and on past");
        int run=e.seal.castTotal+SealGeometry.RUSH_TICKS+2,ground=e.grounded;for(int i=0;i<run;i++)e.tick();
        h.assertTrue(p.getHealth()<20,"Standing in the marked lane is a hit");
        h.assertTrue(e.dragon.position().multiply(1,0,1).distanceTo(end.multiply(1,0,1))<.5,"It skids to a stop at the lane's end");
        h.assertTrue(ground-e.grounded<=3,"Its time on the ground waits while a ground move plays out ("+(ground-e.grounded)+" of "+run+" ticks spent)");
        p.setHealth(20);body=landed(e,10.5,.5,0);e.clock+=200;stand(e,p,body.x+20,body.z);e.seal.start(SealGeometry.Move.RUSH);run=e.seal.castTotal+SealGeometry.RUSH_TICKS+2;
        stand(e,p,body.x+20,body.z+7);for(int i=0;i<run;i++)e.tick();
        h.assertTrue(p.getHealth()==20,"Stepping out of the lane once it is marked beats the charge");
    }finally{for(int x=0;x<=3;x++)for(int z=-1;z<=0;z++)level.setChunkForced(x,z,false);clean(h);}h.succeed();}
    private static ServerPlayer gifted(GameTestHelper h,net.minecraft.resources.ResourceLocation power,int level){
        var p=player(h,level);p.setData(ModDataAttachments.PLAYER_ABILITIES,PlayerAbilityData.empty().replaceWithUnlockedPower(power));return p;
    }
    private static void toPhase(EndBattle.Encounter e,int phase){e.dragon.setHealth(e.dragon.getMaxHealth()*(phase==2?.6F:.3F));e.phaseChange();e.transition=0;}
    private static void breakKnot(SealKnot knot,ServerPlayer p){for(int i=0;i<20&&!knot.isRemoved();i++)knot.hurt(knot.damageSources().playerAttack(p),1000);}
    @GameTest(template="rewind_empty",batch="endgame_33",timeoutTicks=100)
    public static void everyPowerHasAStrongAnswerInEveryPhase(GameTestHelper h){
        var all=List.of(ModAbilities.PYROKINESIS_ID,ModAbilities.CRYOKINESIS_ID,ModAbilities.ELECTROKINESIS_ID,ModAbilities.HYDROKINESIS_ID,ModAbilities.SUPERSPEED_ID,
                ModAbilities.FLIGHT_ID,ModAbilities.QUAKE_ID,ModAbilities.SONIC_RESONANCE_ID,ModAbilities.TELEPATHY_ID,ModAbilities.TELEKINESIS_ID,ModAbilities.TIME_MANIPULATION_ID,
                ModAbilities.RADIATION_ID,ModAbilities.REGENERATION_ID,ModAbilities.PHASING_ID,ModAbilities.INVISIBILITY_ID,ModAbilities.MIMICRY_ID,ModAbilities.SHAPESHIFTING_ID,
                ModAbilities.MATTER_ASSEMBLY_ID,ModAbilities.VIBE_ID,ModAbilities.AMPLIFICATION_ID);
        for(var power:all)for(int phase=1;phase<=3;phase++){
            boolean strong=false;for(var lock:SealGeometry.locks(phase)){var key=com.jayptucker.heroesevolved.combat.BossLocks.key(power,lock);strong|=key!=null&&key.strong();}
            h.assertTrue(strong,power+" has a strong answer to the Living Seal in phase "+phase);
        }
        for(int phase=1;phase<=3;phase++)for(var lock:SealGeometry.locks(phase)){
            Set<com.jayptucker.heroesevolved.combat.BossLocks.Role> roles=new HashSet<>();int keys=0;
            for(var power:all){var key=com.jayptucker.heroesevolved.combat.BossLocks.key(power,lock);if(key!=null){keys++;roles.add(key.role());}}
            h.assertTrue(keys>=3&&roles.size()>=2,lock+" has at least three power answers in more than one role");
        }
        h.assertTrue(com.jayptucker.heroesevolved.combat.BossLocks.curses().contains(ModItems.RIFTBRAND),"Riftbrand is a boss curse every cleansing power and milk lift");
        h.succeed();
    }
    @GameTest(template="rewind_empty",batch="endgame_34",timeoutTicks=400)
    public static void sealQuartersBlowsAndHoldsTheGateUntilEveryKnotBreaks(GameTestHelper h){clean(h);try{
        var p=player(h,16);var e=fight(h,p);Vec3 body=landed(e,10.5,.5,0);stand(e,p,body.x,body.z+10);
        float hp=e.dragon.getHealth();e.dragon.hurt(e.dragon.damageSources().playerAttack(p),20);
        h.assertTrue(Math.abs(hp-e.dragon.getHealth()-20*SealGeometry.SEAL_TAKES[4])<.01,"With four knots standing it takes a quarter");
        e.dragon.setHealth(e.dragon.getMaxHealth()*(SealGeometry.SEAL_GATE+.005F));
        for(int i=0;i<20;i++){e.clock+=21;e.dragon.invulnerableTime=0;e.dragon.hurt(e.dragon.damageSources().playerAttack(p),40);}
        h.assertTrue(e.dragon.getHealth()>=e.dragon.getMaxHealth()*SealGeometry.SEAL_GATE-.01F&&e.phase==1,"The Seal holds the line: it can't be brought below seven tenths");
        for(int i=0;i<3;i++)breakKnot(e.seal.knots[i],p);
        h.assertTrue(e.seal.warding()==1&&!e.seal.shattered,"Three broken knots leave one warding");
        breakKnot(e.seal.knots[3],p);
        h.assertTrue(e.seal.shattered&&e.seal.crash>0&&!e.seal.warded(),"The fourth shatters the Seal and throws it down stunned");
        e.clock+=21;e.dragon.invulnerableTime=0;hp=e.dragon.getHealth();e.dragon.hurt(e.dragon.damageSources().playerAttack(p),10);
        h.assertTrue(e.dragon.getHealth()<hp,"With the Seal shattered the gate is gone");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="endgame_35",timeoutTicks=900)
    public static void aKnotMendsOnlyWhileItsTowerCrystalStillFeedsIt(GameTestHelper h){clean(h);try{
        var p=player(h,16);var e=fight(h,p);int fed=-1;for(int i=0;i<4;i++)if(e.seal.feeders[i]!=null){fed=i;break;}h.assertTrue(fed>=0,"A crystal feeds a knot");
        breakKnot(e.seal.knots[fed],p);h.assertTrue(e.seal.knots[fed]==null&&e.seal.mend[fed]==SealGeometry.KNOT_MEND,"A broken knot starts to mend");
        for(int i=0;i<SealGeometry.KNOT_MEND+1;i++)e.tick();
        h.assertTrue(e.seal.knots[fed]!=null&&e.seal.knots[fed].state()==SealView.WARDING,"It re-forms while its crystal lives");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="endgame_36",timeoutTicks=400)
    public static void bladesDoHalfToAKnotPowersDoAllAndKeysMore(GameTestHelper h){clean(h);try{
        var pyro=player(h,16);var e=fight(h,pyro);var mind=gifted(h,ModAbilities.TELEPATHY_ID,16);e.join(mind);
        var knot=e.seal.knots[0];float max=knot.getMaxHealth();
        knot.hurt(knot.damageSources().playerAttack(mind),4);float power=max-knot.getHealth();knot.setHealth(max);
        mind.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_SWORD));knot.hurt(knot.damageSources().playerAttack(mind),4);float blade=max-knot.getHealth();knot.setHealth(max);
        knot.hurt(knot.damageSources().playerAttack(pyro),4);float key=max-knot.getHealth();
        h.assertTrue(Math.abs(power-4)<.01&&Math.abs(blade-2)<.01&&Math.abs(key-10)<.01,"A power's blow does it all ("+power+"), a blade half ("+blade+"), a breaking key two and a half times ("+key+")");
        knot.setHealth(max);var aimed=com.jayptucker.heroesevolved.combat.BossLocks.key(mind,com.jayptucker.heroesevolved.combat.BossLocks.Lock.SHIELD);knot.answered(mind,aimed);
        h.assertTrue(Math.abs(max-knot.getHealth()-max*com.jayptucker.heroesevolved.combat.BossLocks.STRONG_AIMED)<.01,"A telepath severs two fifths of a knot with one aimed use");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="endgame_37",timeoutTicks=300)
    public static void aimedBringDownsDragItOutOfTheSky(GameTestHelper h){clean(h);
        // An aimed use finds its target through the level's entity lists, which stay empty in a freshly loaded chunk
        // until it is entity-ticking: keep the island's middle loaded and aim at tick 100 (as the Wither's lock tests do).
        var level=end(h);for(int x=-2;x<=1;x++)for(int z=-2;z<=1;z++)level.setChunkForced(x,z,true);
        ServerPlayer p;EndBattle.Encounter e;
        try{p=gifted(h,ModAbilities.TELEKINESIS_ID,16);e=fight(h,p);e.dragon.setPos(.5,85,.5);e.dragon.aiStep();}
        catch(RuntimeException|Error failure){for(int x=-2;x<=1;x++)for(int z=-2;z<=1;z++)level.setChunkForced(x,z,false);clean(h);throw failure;}
        h.runAtTickTime(100,()->{try{
            h.assertTrue(((com.jayptucker.heroesevolved.combat.BossLocks.Lockable)e.dragon).lock()==com.jayptucker.heroesevolved.combat.BossLocks.Lock.OUT_OF_REACH,"In the air the Dragon is out of reach");
            Vec3 chest=SealGeometry.chest(e.dragon);p.setPos(chest.add(0,-4,14));Vec3 d=chest.subtract(p.getEyePosition());
            p.setYRot((float)Math.toDegrees(Math.atan2(-d.x,d.z)));p.setXRot((float)-Math.toDegrees(Math.atan2(d.y,Math.sqrt(d.x*d.x+d.z*d.z))));
            com.jayptucker.heroesevolved.combat.BossLocks.used(p,ModAbilities.TELEKINESIS_ID,"test",20);
            h.assertTrue(Math.abs(e.seal.drag-SealGeometry.DRAG_STRONG)<.02,"An aimed telekinetic finds it in the air and takes hold ("+e.seal.drag+")");
            e.seal.answered(p,com.jayptucker.heroesevolved.combat.BossLocks.key(p,com.jayptucker.heroesevolved.combat.BossLocks.Lock.OUT_OF_REACH));
            h.assertTrue(e.seal.busy()&&e.seal.view().has(SealView.FALLING),"Two strong pulls drag it out of the sky");
            for(int i=0;i<20;i++)e.tick();
            h.assertTrue(e.grounded>0&&!e.seal.airborne()&&((com.jayptucker.heroesevolved.combat.BossLocks.Lockable)e.dragon).lock()==null,"It hits the island and fights on foot");
        }finally{for(int x=-2;x<=1;x++)for(int z=-2;z<=1;z++)level.setChunkForced(x,z,false);clean(h);}h.succeed();});
    }
    @GameTest(template="rewind_empty",batch="endgame_38",timeoutTicks=400)
    public static void breakingTheKnotItDrinksFromBackfires(GameTestHelper h){clean(h);try{
        var p=player(h,16);var e=fight(h,p);toPhase(e,2);
        h.assertTrue(e.phase==2&&e.seal.warding()==0&&Arrays.stream(e.seal.knots).allMatch(k->k!=null&&k.state()==SealView.DORMANT),"From the second phase the knots sleep");
        var sleeping=e.seal.knots[0];float max=sleeping.getHealth();sleeping.hurt(sleeping.damageSources().playerAttack(p),10);h.assertTrue(sleeping.getHealth()==max,"A sleeping knot rings off blows");
        stand(e,p,16.5,15.5);h.assertTrue(e.seal.start(SealGeometry.Move.BREATH)&&e.seal.feeding>=0,"Severing Breath drinks from a knot");
        var feeding=e.seal.knots[e.seal.feeding];h.assertTrue(feeding.lock()==com.jayptucker.heroesevolved.combat.BossLocks.Lock.ROOM_WEAPON,"The feeding knot is the room's weapon");
        float hp=e.dragon.getHealth();breakKnot(feeding,p);
        h.assertTrue(e.seal.casting==null&&e.seal.crash>0&&e.seal.feeding<0,"Breaking it mid-drink cancels the move and crashes it");
        h.assertTrue(hp-e.dragon.getHealth()>=e.dragon.getMaxHealth()*SealGeometry.BACKFIRE_SHARE*.95F,"The backfire takes a tenth of its health past the caps");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="endgame_39",timeoutTicks=400)
    public static void riftbrandDrinksWhileSeenAndBreaksWhenUnseen(GameTestHelper h){clean(h);try{
        var p=player(h,16);var e=fight(h,p);toPhase(e,2);landed(e,.5,.5,0);stand(e,p,10.5,.5);e.dragon.setHealth(e.dragon.getMaxHealth()*.5F);
        e.seal.brand(p);h.assertTrue(p.hasEffect(ModItems.RIFTBRAND),"Its breath brands");
        float hp=e.dragon.getHealth();for(int i=0;i<21;i++)e.tick();
        h.assertTrue(p.getHealth()<20&&e.dragon.getHealth()>hp,"While it sees you the Seal drinks from you and mends");
        h.assertTrue(com.jayptucker.heroesevolved.combat.BossLocks.lift(p)&&!p.hasEffect(ModItems.RIFTBRAND),"Milk and cleansing powers lift it");
        e.seal.brand(p);BlockPos at=BlockPos.containing(p.position());
        for(int x=-1;x<=1;x++)for(int y=-1;y<=2;y++)for(int z=-1;z<=1;z++)if(x!=0||z!=0||y<0||y>1)e.level.setBlock(at.offset(x,y,z),Blocks.OBSIDIAN.defaultBlockState(),3);
        for(int i=0;i<SealGeometry.BRAND_UNSEEN+25;i++)e.tick();
        h.assertTrue(!p.hasEffect(ModItems.RIFTBRAND),"Out of its sight for three seconds the brand breaks");
        for(int x=-1;x<=1;x++)for(int y=-1;y<=2;y++)for(int z=-1;z<=1;z++)e.level.setBlock(at.offset(x,y,z),y<0?Blocks.END_STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),3);
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="endgame_40",timeoutTicks=400)
    public static void theOpenStarTakesMoreAndCracksAfterThree(GameTestHelper h){clean(h);try{
        var p=player(h,16);var e=fight(h,p);toPhase(e,3);Vec3 body=landed(e,10.5,.5,0);stand(e,p,body.x,body.z+8);
        var chest=SealGeometry.body(e.dragon);float hp=e.dragon.getHealth();chest.hurt(e.dragon.damageSources().playerAttack(p),10);float star=hp-e.dragon.getHealth();
        e.clock+=25;e.dragon.invulnerableTime=0;hp=e.dragon.getHealth();e.dragon.getSubEntities()[6].hurt(e.dragon.damageSources().playerAttack(p),10);float wing=hp-e.dragon.getHealth();
        h.assertTrue(star>wing*2,"A fire-wielder's blow on the open star does far more ("+star+") than on a wing ("+wing+")");
        e.clock+=25;e.dragon.invulnerableTime=0;chest.hurt(e.dragon.damageSources().playerAttack(p),10);
        e.clock+=25;e.dragon.invulnerableTime=0;chest.hurt(e.dragon.damageSources().playerAttack(p),10);
        h.assertTrue(e.seal.stagger>=SealGeometry.STAR_REEL,"Three on the star in quick succession crack it and it reels");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="endgame_41",timeoutTicks=400)
    public static void aPowerUsedAsTheUnmakingLandsCarriesItsUserOut(GameTestHelper h){clean(h);try{
        var pyro=player(h,16);var e=fight(h,pyro);toPhase(e,3);landed(e,.5,.5,0);var slow=gifted(h,ModAbilities.MIMICRY_ID,16);e.join(slow);
        pyro.setPos(25,110,20);slow.setPos(-25,110,-20);e.seal.start(SealGeometry.Move.UNMAKING);int windup=e.seal.castTotal;
        for(int i=0;i<windup;i++){if(i==windup-5)com.jayptucker.heroesevolved.combat.BossLocks.markUsed(pyro);e.tick();}
        e.tick();
        h.assertTrue(pyro.getHealth()==20,"A jet step as the last ring closes slips The Unmaking");
        h.assertTrue(slow.getHealth()<20,"Without an answer in the moment, it lands");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="endgame_42",timeoutTicks=100)
    public static void feintsHoldLongerAndNoWindupFallsUnderItsFloor(GameTestHelper h){
        for(var move:SealGeometry.Move.values())for(int phase=1;phase<=3;phase++)for(int cast=0;cast<6;cast++)for(float avg:new float[]{.5F,1,1.3F}){
            int w=SealGeometry.castWindup(move,phase,cast,avg);
            h.assertTrue(w>=SealGeometry.floor(move),move+" in phase "+phase+" never warns under its floor ("+w+")");
        }
        h.assertTrue(SealGeometry.castWindup(SealGeometry.Move.BITE,2,2,1)>SealGeometry.castWindup(SealGeometry.Move.BITE,2,1,1),"Every third bite from the second phase is a feint that holds longer");
        h.assertTrue(SealGeometry.castWindup(SealGeometry.Move.SCORCH,3,0,1)<SealGeometry.castWindup(SealGeometry.Move.SCORCH,1,0,1),"Moves come faster each phase");
        h.assertTrue(SealGeometry.castWindup(SealGeometry.Move.SCORCH,1,0,.6F)>SealGeometry.castWindup(SealGeometry.Move.SCORCH,1,0,1),"Weaker parties get longer warnings");
        h.succeed();
    }
    @GameTest(template="rewind_empty",batch="endgame_43",timeoutTicks=400)
    public static void itScalesToThePartysStrengthAndNeverWeakens(GameTestHelper h){clean(h);try{
        var p=player(h,20);var e=fight(h,p);float strong=e.damage.effective();
        h.assertTrue(Math.abs(strong-SealGeometry.BASE_HEALTH*com.jayptucker.heroesevolved.combat.BossScaling.healthScale(com.jayptucker.heroesevolved.combat.BossScaling.strength(true,20,16)))<.01,"An Evolved fighter faces a tougher Seal");
        var q=player(h,16);e.join(q);h.assertTrue(e.damage.effective()>strong,"A second fighter raises its health");float two=e.damage.effective();
        q.discard();e.join(p);h.assertTrue(e.damage.effective()==two,"Leaving never weakens it");
        h.assertTrue(e.seal.fracture().is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR),"The Seal's fracture goes through armour");
        stand(e,p,10.5,.5);landed(e,.5,.5,0);float before=p.getHealth();e.seal.hit(p,SealGeometry.Move.TAIL,4);
        h.assertTrue(before-p.getHealth()>=4+p.getMaxHealth()*SealGeometry.HEAVY_SHARE-.01F,"A heavy blow also takes a share of the target's own health");
    }finally{clean(h);}h.succeed();}
    @GameTest(template="rewind_empty",batch="endgame_44",timeoutTicks=400)
    public static void adminConsoleStartsControlsAndCancelsTheEndFight(GameTestHelper h){clean(h);try{
        var p=player(h,16);var entry=com.jayptucker.heroesevolved.admin.AdminEvents.describe(p.getServer(),p).stream().filter(x->x.id().equals("dragon")).findFirst().orElseThrow();
        h.assertTrue(entry.starts().size()==5&&!entry.active(),"The End fight is listed with its five controls");
        String started=com.jayptucker.heroesevolved.admin.AdminEvents.start(p.getServer(),p,"dragon",0);
        h.assertTrue(EndBattle.active()&&EndBattle.current().stage==EndBattle.DRAGON,"Start wakes the Living Seal ("+started+")");
        h.assertTrue(EndBattle.status(p.getServer()).contains("Living Seal"),"Its status reads live");
        com.jayptucker.heroesevolved.admin.AdminEvents.start(p.getServer(),p,"dragon",2);h.assertTrue(EndBattle.current().seal.shattered,"Break its locks shatters the Seal");
        com.jayptucker.heroesevolved.admin.AdminEvents.start(p.getServer(),p,"dragon",1);h.assertTrue(EndBattle.current().phase==2,"Advance phase moves it on");
        com.jayptucker.heroesevolved.admin.AdminEvents.cancel(p.getServer(),p,"dragon");h.assertTrue(!EndBattle.active(),"Cancel ends the attempt");
        h.assertTrue(com.jayptucker.heroesevolved.admin.AdminEvents.start(p.getServer(),p,"dragon",999).equals("Unknown event or start option."),"Unknown options are refused");
    }finally{clean(h);}h.succeed();}
}
