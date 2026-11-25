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
import java.nio.file.Files;
import java.util.function.Consumer;

@EventBusSubscriber(modid="heroesevolved",value=Dist.CLIENT)
public final class EndgamePreview {
    private static boolean started;private static int startup,age;private static String capture;
    /** The Dragon's section runs SHIFT ticks; Sylar's beats keep their original numbers after it. */
    private static final int SHIFT=390;
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
    /** Turn the landed Dragon toward the player a little before a ground move, so its body has settled (an instant turn banks the native model). */
    private static void face(){server(p->{var e=EndBattle.current();e.grounded=Math.max(e.grounded,200);e.brain.cancelAll();float yaw=EndGeometry.yaw(p.position().subtract(e.dragon.position()));e.dragon.setYRot(yaw);e.dragon.setYHeadRot(yaw);e.dragon.yBodyRot=yaw;});}
    /** An air move from the Dragon's starting perch over the island. */
    private static void air(EndGeometry.Move move){server(p->{var e=EndBattle.current();e.brain.cancelAll();e.grounded=0;e.dragon.setPos(.5,89,.5);e.dragon.setDeltaMovement(Vec3.ZERO);e.transition=e.stagger=0;e.start(move);});}
    /** A ground move with the landed Dragon turned toward the player. */
    private static void ground(EndGeometry.Move move){server(p->{var e=EndBattle.current();e.grounded=Math.max(e.grounded,200);e.transition=e.stagger=0;e.start(move);});}
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
            p.getAbilities().flying=false;p.onUpdateAbilities();p.teleportTo(l,17,e.brain.ground(17,21),21,0,0);
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
            case 440 -> focus(0,0,26,20);
            case 465 -> capture="04-tethered-sky.png";
            case 490 -> air(EndGeometry.Move.RIFT_CHARGE);
            case 510 -> capture="05-riftdive-marked.png";
            case 530 -> capture="06-riftdive-climb.png";
            case 541 -> capture="07-riftdive-impact.png";
            case 600 -> air(EndGeometry.Move.BREATH);
            case 625 -> capture="08-breath-run-marked.png";
            case 652 -> capture="09-breath-run.png";
            case 680 -> capture="10-breath-pools.png";
            case 720 -> {focus(0,0,22,16);air(EndGeometry.Move.STARS);}
            case 742 -> capture="11-starfall-marked.png";
            case 760 -> capture="12-starfall-shards.png";
            case 800 -> air(EndGeometry.Move.DESCENT);
            case 806 -> server(p->{var e=EndBattle.current();Vec3 at=e.brain.spot.add(10,0,-8);p.teleportTo(p.serverLevel(),at.x,e.brain.ground(at.x,at.z),at.z,0,0);});
            case 812 -> focus(-5,4,26,18);
            case 830 -> capture="13-descent-marked.png";
            case 845 -> capture="14-descent-slam.png";
            case 865 -> {focus(-5,4,22,14);face();}
            case 880 -> ground(EndGeometry.Move.BITE);
            case 890 -> capture="15-bite-lane.png";
            case 905 -> face();
            case 920 -> ground(EndGeometry.Move.TAIL);
            case 938 -> capture="16-tail-sweep-marked.png";
            case 945 -> capture="17-tail-whip.png";
            // Seal Rush: back off out of its reach and it charges down a lane through you.
            case 955 -> server(p->{var e=EndBattle.current();Vec3 d=p.position().subtract(e.dragon.position()).multiply(1,0,1).normalize();Vec3 at=e.dragon.position().add(d.scale(20));p.teleportTo(p.serverLevel(),at.x,e.brain.ground(at.x,at.z),at.z,0,0);});
            case 958 -> between(30,20);
            case 960 -> ground(EndGeometry.Move.RUSH);
            case 980 -> capture="17a-seal-rush-marked.png";
            case 993 -> capture="17b-seal-rush-charge.png";
            case 1010 -> between(22,14);
            case 1060 -> {phase(2);server(p->EndBattle.current().grounded=300);}
            case 1115 -> capture="18-riftstorm-on-foot.png";
            case 1125 -> face();
            case 1140 -> ground(EndGeometry.Move.SCORCH);
            case 1162 -> capture="19-scorch-cone.png";
            case 1172 -> capture="20-scorch-breath.png";
            case 1195 -> face();
            case 1210 -> ground(EndGeometry.Move.WINGBREAK);
            case 1226 -> capture="21-wing-gust-gathers.png";
            case 1245 -> capture="22-wing-wave.png";
            case 1280 -> {phase(3);server(p->EndBattle.current().grounded=300);}
            case 1335 -> capture="23-dying-star.png";
            case 1345 -> {between(28,20);face();}
            case 1360 -> ground(EndGeometry.Move.ROAR);
            case 1382 -> capture="24-roar-marked.png";
            case 1392 -> capture="25-roar.png";
            case 1405 -> face();
            case 1420 -> ground(EndGeometry.Move.TAKEOFF);
            case 1432 -> capture="26-takeoff-marked.png";
            case 1452 -> capture="27-takeoff-climb.png";
            case 1480 -> {camera(40,104,48,9,85,8);air(EndGeometry.Move.UNMAKING);}
            case 1525 -> capture="28-unmaking-safe-orbit.png";
            case 1580 -> {server(p->{var e=EndBattle.current();e.brain.cancelAll();e.grounded=0;e.dragon.setPos(e.brain.spot.add(0,30,0));e.dragon.setDeltaMovement(new Vec3(.6,0,.2));e.ground(p);});}
            case 1582 -> between(30,22);
            case 1588 -> capture="29-crash-falling.png";
            case 1615 -> capture="30-crashed-stunned.png";
            case 1660 -> {if(Boolean.getBoolean("heroes.preview.dragon"))PreviewQuiet.quit();}
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
        // Photograph the server-selected geometry for every one of the twenty-one powers.
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
