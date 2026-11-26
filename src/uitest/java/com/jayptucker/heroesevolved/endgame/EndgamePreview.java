package com.jayptucker.heroesevolved.endgame;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.client.effects.TempestCamera;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.mixin.EndFightAccess;
import com.jayptucker.heroesevolved.network.*;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.story.bunker.CryoChamberService;
import com.jayptucker.heroesevolved.story.prologue.PrologueService;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.time.intro.city.CityAudit;
import com.jayptucker.heroesevolved.uitest.PreviewQuiet;
import net.minecraft.client.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.boss.enderdragon.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import com.jayptucker.heroesevolved.combat.BossLocks;
import java.nio.file.Files;
import java.util.function.Consumer;

@EventBusSubscriber(modid="heroesevolved",value=Dist.CLIENT)
public final class EndgamePreview {
    private static boolean started;private static int startup,age;private static String capture;
    /** The Dragon's section runs SHIFT ticks; Sylar's beats keep their original numbers after it. */
    private static final int SHIFT=310;
    private static void server(Consumer<ServerPlayer> task){var mc=Minecraft.getInstance();var id=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{try{task.accept(mc.getSingleplayerServer().getPlayerList().getPlayer(id));}catch(Throwable e){e.printStackTrace();PreviewQuiet.report("ENDGAME_PREVIEW FAIL "+e);}});}
    private static void camera(double x,double y,double z,double tx,double ty,double tz){Minecraft.getInstance().options.setCameraType(CameraType.THIRD_PERSON_BACK);TempestCamera.fix(new Vec3(x,y,z),new Vec3(tx,ty,tz));}
    private static void mark(EndGeometry.Move move){server(p->{var e=EndBattle.current();e.transition=e.stagger=0;e.start(move);});}
    /** Look at a point offset from the player from the island's middle side, so the obsidian pillars ringing the island stay behind the shot. */
    private static void focus(double dx,double dz,double back,double up){
        Vec3 at=Minecraft.getInstance().player.position().add(dx,0,dz),in=new Vec3(-at.x,0,-at.z);in=in.lengthSqr()<1?new Vec3(-1,0,0):in.normalize();
        camera(at.x+in.x*back,at.y+up,at.z+in.z*back,at.x,at.y+2,at.z);
    }
    /** Frame the player and the Dragon together, from the island's middle side. */
    private static void between(double back,double up){
        var mc=Minecraft.getInstance();var dragons=mc.level.getEntitiesOfClass(EnderDragon.class,mc.player.getBoundingBox().inflate(90));
        if(dragons.isEmpty()){focus(0,0,back,up);return;}Vec3 mid=mc.player.position().lerp(dragons.getFirst().position(),.5);
        focus(mid.x-mc.player.getX(),mid.z-mc.player.getZ(),back,up);
    }
    private static EnderDragon dragon(){var mc=Minecraft.getInstance();var all=mc.level.getEntitiesOfClass(EnderDragon.class,mc.player.getBoundingBox().inflate(160));return all.isEmpty()?null:all.getFirst();}
    /** Low behind the player, looking up past them at the Dragon in the sky, so the marks and the body share the frame. */
    private static void sky(double back,double up){
        var mc=Minecraft.getInstance();var d=dragon();if(d==null){focus(0,0,back,up);return;}
        Vec3 p=mc.player.position(),away=p.subtract(d.position()).multiply(1,0,1);away=away.lengthSqr()<1?new Vec3(1,0,0):away.normalize();
        Vec3 cam=p.add(away.scale(back)).add(0,up,0),look=p.lerp(SealGeometry.chest(d),.42);camera(cam.x,cam.y,cam.z,look.x,look.y,look.z);
    }
    /** Side-on to the Dragon's chest, a little ahead of it, so its pose reads in profile. */
    private static void profile(double dist,double up){
        var d=dragon();if(d==null)return;Vec3 f=SealGeometry.forward(d.getYRot()),side=new Vec3(-f.z,0,f.x),chest=SealGeometry.chest(d);
        // Whichever side has a clear view of the chest (an altar's pillars stand close to where it lands).
        Vec3 cam=null;
        for(double k:new double[]{1,-1,.6,-.6}){Vec3 at=chest.add(side.scale(dist*k)).add(f.scale(dist*.45)).add(0,up,0);
            var hit=Minecraft.getInstance().level.clip(new net.minecraft.world.level.ClipContext(at,chest,net.minecraft.world.level.ClipContext.Block.VISUAL,net.minecraft.world.level.ClipContext.Fluid.NONE,net.minecraft.world.phys.shapes.CollisionContext.empty()));
            if(hit.getType()==net.minecraft.world.phys.HitResult.Type.MISS){cam=at;break;}}
        if(cam==null)cam=chest.add(0,dist,0).add(f.scale(dist*.5));
        camera(cam.x,cam.y,cam.z,chest.x,chest.y-1,chest.z);
    }
    /** Put the Dragon down where it is, turned toward the player a little before a ground move, so its body has settled (an instant turn banks the native model). */
    private static void face(){server(p->{var e=EndBattle.current();Vec3 at=e.dragon.position();float yaw=SealGeometry.yaw(p.position().subtract(at));e.seal.landAt(new Vec3(at.x,e.seal.ground(at.x,at.z),at.z),yaw);e.transition=0;});}
    /** An air move from the Dragon's perch over the island. */
    private static void air(SealGeometry.Move move){server(p->{var e=EndBattle.current();e.seal.liftTo(new Vec3(.5,89,.5));e.transition=0;e.seal.start(move);});}
    /** A ground move with the landed Dragon. */
    private static void ground(SealGeometry.Move move){server(p->{var e=EndBattle.current();e.transition=0;e.seal.crash=0;e.seal.stagger=0;e.seal.slump=0;e.seal.chain.clear();e.seal.start(move);});}
    private static void phase(int phase){server(p->{var e=EndBattle.current();e.boss().setHealth(e.boss().getMaxHealth()*(phase==2?.6F:.25F));e.phaseChange();});}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!Boolean.getBoolean("heroes.preview.endgame"))return;var mc=Minecraft.getInstance();
        if(!started&&++startup>=35&&mc.screen!=null&&mc.getOverlay()==null){
            started=true;mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(10);mc.options.guiScale().set(2);mc.options.fov().set(70);mc.resizeDisplay();mc.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(0D);
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);var rules=new GameRules();rules.getRule(GameRules.RULE_DAYLIGHT).set(false,null);rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
            mc.createWorldOpenFlows().createFreshLevel("Inheritance-"+System.currentTimeMillis(),new LevelSettings("Endgame Preview",GameType.CREATIVE,false,Difficulty.NORMAL,true,rules,WorldDataConfiguration.DEFAULT),new WorldOptions(73,false,false),a->a.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(),mc.screen);
        }
        if(mc.player==null||mc.getSingleplayerServer()==null||mc.getOverlay()!=null)return;
        if(mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen)mc.setScreen(null);age++;
        if(age%20==0){ClientScreenTintState.clear();mc.getToasts().clear();}
        if(age==104)server(PrologueService::skip);
        if(age==110){mc.options.hideGui=true;mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);server(p->{
            CryoChamberService.release(p);EndBattle.reset(p.getServer(),false);p.removeAllEffects();
            p.setData(ModDataAttachments.POWER_INTRO_HISTORY,p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(ModAbilities.FLIGHT_ID));p.setData(ModDataAttachments.PLAYER_ABILITIES,PlayerAbilityData.empty().replaceWithUnlockedPower(ModAbilities.FLIGHT_ID));PlayerProgressionService.setPowerLevel(p,20);PlayerPowerSyncService.sync(p);
            var l=p.getServer().getLevel(Level.END);EndScenery.hold(l);p.teleportTo(l,17,72,21,0,0);p.getAbilities().flying=true;p.onUpdateAbilities();
            var f=l.getDragonFight();var a=(EndFightAccess)f;a.heroes$needsScan(false);a.heroes$dragonKilled(false);
            for(Entity entity:java.util.stream.StreamSupport.stream(l.getAllEntities().spliterator(),false).toList())if(entity instanceof EnderDragon)entity.discard();
            EnderDragon dragon=EntityType.ENDER_DRAGON.create(l);dragon.setPos(.5,89,.5);dragon.setDragonFight(f);a.heroes$dragonUUID(dragon.getUUID());l.addFreshEntity(dragon);
            var e=EndBattle.begin(dragon,java.util.List.of(p));e.still(true);e.holdOff(10000);
            // Stand on the island, so the Dragon's ground moves mark the surface under the player.
            p.getAbilities().flying=false;p.onUpdateAbilities();p.teleportTo(l,17,e.seal.ground(17,21),21,0,0);
            PreviewQuiet.report("ENDGAME_STAGE "+p.level().dimension()+" "+p.position()+" floor="+l.getBlockState(new BlockPos(0,63,0))+" altar="+e.data.anchors);
        });}
        switch(age){
            case 260 -> camera(115,131,119,0,83,0);
            case 300 -> capture="01-native-island-arena.png";
            case 330 -> server(p->{var e=EndBattle.current();Vec3 a=e.data.anchors.getFirst();PreviewQuiet.report("ENDGAME_ALTAR_AUDIT "+CityAudit.run(p.serverLevel(),(int)a.x-6,(int)a.y-7,(int)a.z-6,(int)a.x+6,(int)a.y+10,(int)a.z+6,true));PreviewQuiet.report("ENDGAME_REFUGE_AUDIT "+CityAudit.run(p.serverLevel(),63,55,-11,89,85,11,true));});
            case 340 -> camera(42,74,15,30,67,0);
            case 365 -> capture="02-grounding-altar.png";
            case 390 -> camera(90,75,18,76,66,0);
            case 415 -> capture="03-refuge-and-return.png";
            // The Living Seal: every air move, the landing, every ground move and each phase, around a player standing on the island.
            // The Living Seal: every move's opening pose and strike, its locks and each phase, round a player on the island.
            case 440 -> sky(18,9);
            case 465 -> capture="04-the-living-seal.png";
            case 466 -> {var d=dragon();if(d!=null){Vec3 c=SealGeometry.chest(d);camera(c.x+9,c.y+3,c.z+9,c.x,c.y,c.z);}}
            case 469 -> capture="04b-the-seals-rings.png";
            case 470 -> server(p->{var e=EndBattle.current();Vec3 k=e.seal.knotAt(0);p.teleportTo(p.serverLevel(),k.x-9,e.seal.ground(k.x-9,k.z+3),k.z+3,0,0);});
            case 478 -> {var k=EndClientState.view().seal().at();if(!k.isEmpty()){Vec3 at=k.get(0).add(0,.6,0);camera(at.x-4.5,at.y+1.5,at.z+3.5,at.x,at.y,at.z);}}
            case 490 -> capture="05-seal-knot.png";
            case 495 -> server(p->p.teleportTo(p.serverLevel(),17,EndBattle.current().seal.ground(17,21),21,0,0));
            case 500 -> air(SealGeometry.Move.STARFALL);
            case 503 -> sky(18,9);
            case 512 -> capture="06-starfall-rears.png";
            case 531 -> capture="07-starfall-shards.png";
            case 560 -> air(SealGeometry.Move.RIFTDIVE);
            case 575 -> capture="08-riftdive-climbs.png";
            case 594 -> capture="09-riftdive-dive.png";
            case 603 -> capture="10-riftdive-slam.png";
            case 650 -> air(SealGeometry.Move.BREATH);
            case 654 -> sky(18,9);
            case 668 -> capture="11-breath-draws-back.png";
            case 690 -> capture="12-breath-run.png";
            case 730 -> {focus(0,0,26,20);air(SealGeometry.Move.DESCENT);}
            case 745 -> capture="13-descent-glides-in.png";
            case 772 -> capture="14-descent-lands.png";
            case 790 -> face();
            case 796 -> profile(15,2);
            case 800 -> ground(SealGeometry.Move.BITE);
            case 806 -> capture="15-bite-coils.png";
            case 811 -> capture="16-bite-strikes.png";
            case 830 -> {server(p->{var e=EndBattle.current();Vec3 back=SealGeometry.forward(e.dragon.getYRot()).scale(-7);Vec3 at=e.dragon.position().add(back);p.teleportTo(p.serverLevel(),at.x,e.seal.ground(at.x,at.z),at.z,0,0);});}
            case 834 -> {profile(16,4);ground(SealGeometry.Move.TAIL);}
            case 845 -> capture="17-tail-coils.png";
            case 851 -> capture="18-tail-whips.png";
            case 870 -> face();
            case 876 -> profile(17,3);
            case 880 -> ground(SealGeometry.Move.BUFFET);
            case 892 -> capture="19-buffet-wings-high.png";
            case 899 -> capture="20-buffet-slams.png";
            case 920 -> server(p->{var e=EndBattle.current();Vec3 d=p.position().subtract(e.dragon.position()).multiply(1,0,1).normalize();Vec3 at=e.dragon.position().add(d.scale(20));p.teleportTo(p.serverLevel(),at.x,e.seal.ground(at.x,at.z),at.z,0,0);});
            case 924 -> {between(30,20);face();}
            case 932 -> ground(SealGeometry.Move.RUSH);
            case 944 -> capture="21-rush-digs-in.png";
            case 958 -> capture="22-rush-charges.png";
            case 980 -> profile(16,4);
            case 984 -> server(p->EndBattle.current().seal.operatorBreak());
            case 987 -> capture="23-the-seal-shatters.png";
            case 1004 -> capture="24-crashed.png";
            case 1030 -> {phase(2);server(p->EndBattle.current().grounded=400);}
            case 1056 -> profile(18,4);
            case 1060 -> capture="25-riftstorm.png";
            case 1062 -> mc.options.hideGui=false;
            case 1066 -> capture="26-boss-bar.png";
            case 1068 -> mc.options.hideGui=true;
            case 1075 -> air(SealGeometry.Move.BREATH);
            case 1080 -> {var v=EndClientState.view().seal();int f=v.feeding();if(f>=0){Vec3 at=v.at().get(f);camera(at.x-10,at.y+6,at.z+10,at.x*.5,at.y+8,at.z*.5);}}
            case 1092 -> capture="27-knot-feeding.png";
            case 1110 -> server(p->EndBattle.current().seal.interrupt());
            case 1114 -> server(p->{var e=EndBattle.current();e.seal.liftTo(new Vec3(.5,89,.5));e.seal.answered(p,new BossLocks.Key(BossLocks.Lock.OUT_OF_REACH,BossLocks.Role.BRING_DOWN,BossLocks.Strength.STRONG,BossLocks.Channel.AIMED));});
            case 1116 -> sky(18,8);
            case 1118 -> capture="28-dragging-it-down.png";
            case 1124 -> server(p->EndBattle.current().seal.answered(p,new BossLocks.Key(BossLocks.Lock.OUT_OF_REACH,BossLocks.Role.BRING_DOWN,BossLocks.Strength.STRONG,BossLocks.Channel.AIMED)));
            case 1130 -> capture="29-dragged-falling.png";
            case 1150 -> capture="30-dragged-down.png";
            case 1180 -> face();
            case 1186 -> profile(16,3);
            case 1190 -> ground(SealGeometry.Move.SCORCH);
            case 1204 -> capture="31-scorch-rears.png";
            case 1214 -> capture="32-scorch-breathes.png";
            case 1230 -> server(p->EndBattle.current().seal.brand(p));
            case 1239 -> capture="33-riftbrand.png";
            case 1260 -> {phase(3);server(p->{p.removeEffect(com.jayptucker.heroesevolved.registry.ModItems.RIFTBRAND);EndBattle.current().grounded=400;});}
            case 1296 -> profile(11,2);
            case 1300 -> capture="34-dying-star.png";
            case 1310 -> face();
            case 1316 -> profile(18,3);
            case 1320 -> ground(SealGeometry.Move.ROAR);
            case 1333 -> capture="35-roar-rears.png";
            case 1342 -> capture="36-roar.png";
            case 1360 -> server(p->{var e=EndBattle.current();Vec3 d=p.position().subtract(e.dragon.position()).multiply(1,0,1).normalize();Vec3 at=e.dragon.position().add(d.scale(24));p.teleportTo(p.serverLevel(),at.x,e.seal.ground(at.x,at.z),at.z,0,0);});
            case 1364 -> {between(32,22);face();}
            case 1372 -> ground(SealGeometry.Move.LEAP);
            case 1381 -> capture="37-leap-crouches.png";
            case 1391 -> capture="38-leap-arcs.png";
            case 1398 -> capture="39-leap-lands.png";
            case 1420 -> {camera(40,104,48,9,85,8);ground(SealGeometry.Move.UNMAKING);}
            case 1465 -> capture="40-the-unmaking.png";
            case 1455 -> {var c=EndClientState.view().center();camera(c.x+13,c.y+30,c.z+13,c.x,c.y+38,c.z);}
            case 1460 -> capture="40b-unmaking-close.png";
            case 1461 -> camera(40,104,48,9,85,8);
            case 1491 -> capture="41-unmaking-blast.png";
            case 1505 -> between(30,22);
            case 1530 -> capture="42-exhausted.png";
            case 1580 -> {if(Boolean.getBoolean("heroes.preview.dragon"))PreviewQuiet.quit();}
            default -> {}
        }
        // Sylar: Inheritance, after the Dragon.
        switch(age-SHIFT){
            case 1290 -> {camera(21,86,27,0,80,8);server(p->{var e=EndBattle.current();e.startAbsorption(e.dragon.position());e.dragon.discard();});}
            case 1320 -> capture="17-false-victory.png";
            case 1390 -> capture="18-dragon-inheritance.png";
            case 1455 -> capture="19-wing-unfurling.png";
            case 1510 -> {camera(17,78,28,0,73,8);server(p->{var e=EndBattle.current();e.sylar.setPos(.5,72,8);p.teleportTo(p.serverLevel(),12,72,22,0,0);e.still(true);e.holdOff(10000);});}
            case 1545 -> capture="20-sylar-inheritance.png";
            case 1570 -> mark(EndGeometry.Move.NEEDLE);
            case 1588 -> capture="21-white-needle.png";
            case 1630 -> mark(EndGeometry.Move.STONE);
            case 1652 -> capture="22-borrowed-stone.png";
            case 1710 -> mark(EndGeometry.Move.READ);
            case 1765 -> capture="23-gift-echo-prism.png";
            case 1810 -> phase(2);
            case 1870 -> capture="24-collector-of-worlds.png";
            case 1900 -> mark(EndGeometry.Move.WORLDLINE_STEP);
            case 1922 -> capture="25-worldline-step-warning.png";
            case 1942 -> capture="26-worldline-body-echoes.png";
            case 1990 -> {server(p->{var e=EndBattle.current();e.sylar.setPos(.5,72,8);});mark(EndGeometry.Move.EVENTIDE);}
            case 2060 -> capture="27-three-eventide-rings.png";
            case 2120 -> phase(3);
            case 2180 -> capture="28-one-more-future.png";
            case 2210 -> mark(EndGeometry.Move.ENDINGS);
            case 2258 -> capture="29-all-the-endings.png";
            default -> {}
        }
        // Photograph the server-selected geometry for every one of the twenty powers.
        int late=age-SHIFT;
        if(late>=2330&&late<2330+21*85){int i=(late-2330)/85,at=(late-2330)%85;
            if(at==0){camera(22,81,32,7,74,15);final int index=i;server(p->{var e=EndBattle.current();e.sylar.setPos(.5,72,8);e.sampled=index;e.transition=e.stagger=0;e.start(EndGeometry.Move.PATTERN);});}
            if(at==25)capture=String.format("%02d-pattern-%s.png",30+i,EndMoves.powerPath(i));
        }
        if(late==4140){camera(3.8,74.4,13,.5,73.1,8);server(p->EndBattle.current().startVictory());}
        if(late==4164)capture="51-body-fractures.png";
        if(late==4195)capture="52-the-light-breaks.png";
        if(late==4211)camera(37,95,44,0,77,8);
        if(late==4215)capture="53-ability-detonation.png";
        if(late==4232)capture="54-world-of-power.png";
        if(late==4255){camera(105,117,112,0,78,0);capture="55-island-wide-energy-wave.png";}
        if(late==4282)capture="56-borrowed-light-disperses.png";
        if(late==4330){camera(30,86,36,0,67,0);capture="57-native-exit-released.png";}
        if(late==4430)PreviewQuiet.quit();
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event){if(capture==null)return;var mc=Minecraft.getInstance();try(var shot=Screenshot.takeScreenshot(mc.getMainRenderTarget())){var f=mc.gameDirectory.toPath().resolve("screenshots").resolve(capture);Files.createDirectories(f.getParent());shot.writeToFile(f);System.out.println("ENDGAME_CAPTURE "+capture+" camera="+mc.gameRenderer.getMainCamera().getPosition()+" player="+mc.player.position());}catch(Exception e){throw new RuntimeException(e);}capture=null;}
}
