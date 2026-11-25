package com.jayptucker.heroesevolved.wither;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.admin.AdminService;
import com.jayptucker.heroesevolved.client.effects.TempestCamera;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.gui.screen.*;
import com.jayptucker.heroesevolved.network.*;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.story.bunker.CryoChamberService;
import com.jayptucker.heroesevolved.story.prologue.PrologueService;
import com.jayptucker.heroesevolved.time.intro.city.CityAudit;
import com.jayptucker.heroesevolved.uitest.PreviewQuiet;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.nio.file.Files;
import java.util.function.Consumer;

/** Live player input and AI are observed separately from the staged pose photographs. */
@EventBusSubscriber(modid="heroesevolved",value=Dist.CLIENT)
public final class WitherReworkPreview {
    private static boolean started;private static int startup,age,lastClipAge=-1;private static String capture;private static int liveStart;
    private static RiftWitherEntity queuedBoss;private static WitherGeometry.Move queuedMove;private static long queuedAt;
    private static void server(Consumer<ServerPlayer> task){var mc=Minecraft.getInstance();var id=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{try{task.accept(mc.getSingleplayerServer().getPlayerList().getPlayer(id));}catch(Throwable e){e.printStackTrace();PreviewQuiet.report("WITHER_REWORK FAIL "+e);}});}
    private static void camera(double x,double y,double z){Minecraft.getInstance().options.setCameraType(CameraType.THIRD_PERSON_BACK);TempestCamera.fix(WitherGeometry.floor().add(x,y,z),WitherGeometry.floor().add(0,4,3));}
    private static void mark(WitherGeometry.Move move){server(p->{var b=WitherArena.boss();b.cancelChain();b.setPos(WitherGeometry.floor().add(0,.35,0));b.stagger=b.transition=b.crash=0;queuedBoss=b;queuedMove=move;queuedAt=b.level().getGameTime()+3;});}
    private static void stage(Consumer<ServerPlayer> setup,WitherGeometry.Move move){server(p->{var b=WitherArena.boss();b.cancelChain();b.stagger=b.transition=b.crash=0;setup.accept(p);queuedBoss=b;queuedMove=move;queuedAt=b.level().getGameTime()+3;});}
    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event){
        if(!Boolean.getBoolean("heroes.preview.witherRework")||queuedBoss==null||queuedBoss.level().getGameTime()<queuedAt)return;
        if(queuedBoss==WitherArena.boss()&&!queuedBoss.isRemoved())queuedBoss.start(queuedMove);queuedBoss=null;
    }
    private static void click(String text){var mc=Minecraft.getInstance();if(mc.screen instanceof AdminScreen screen){for(var child:screen.children())if(child instanceof Button button&&button.getMessage().getString().contains(text)&&button.active){screen.mouseClicked(button.getX()+button.getWidth()/2D,button.getY()+button.getHeight()/2D,0);PreviewQuiet.report("WITHER_ADMIN_CLICK PASS "+text);return;}}PreviewQuiet.report("WITHER_ADMIN_CLICK FAIL "+text);}
    private static void keys(boolean active){var mc=Minecraft.getInstance();mc.options.keyUp.setDown(false);mc.options.keyRight.setDown(active);mc.options.keyJump.setDown(false);mc.options.keySprint.setDown(false);}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!Boolean.getBoolean("heroes.preview.witherRework"))return;var mc=Minecraft.getInstance();
        if(!started&&++startup>35&&mc.screen!=null&&mc.getOverlay()==null){
            started=true;mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(7);mc.options.guiScale().set(2);mc.resizeDisplay();mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            var rules=new GameRules();rules.getRule(GameRules.RULE_DAYLIGHT).set(false,null);rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
            mc.createWorldOpenFlows().createFreshLevel("Wither-Rework-"+System.currentTimeMillis(),new LevelSettings("Wither Rework",GameType.CREATIVE,false,Difficulty.NORMAL,true,rules,WorldDataConfiguration.DEFAULT),new WorldOptions(73,false,false),a->a.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(),mc.screen);
        }
        if(mc.player==null||mc.getSingleplayerServer()==null||mc.getOverlay()!=null)return;if(mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen)mc.setScreen(null);age++;
        mc.getToasts().clear();ClientScreenTintState.clear();
        if(age==104)server(PrologueService::skip);
        if(age==115)server(p->{CryoChamberService.release(p);p.removeAllEffects();WitherArena.reset(p.getServer(),false);p.teleportTo(p.getServer().overworld(),150.5,-60,150.5,0,0);
            p.setData(ModDataAttachments.POWER_INTRO_HISTORY,p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(ModAbilities.FLIGHT_ID));p.setData(ModDataAttachments.PLAYER_ABILITIES,PlayerAbilityData.empty().replaceWithUnlockedPower(ModAbilities.FLIGHT_ID));PlayerProgressionService.setPowerLevel(p,20);PlayerPowerSyncService.sync(p);});
        switch(age){
            case 140 -> {mc.options.hideGui=false;AdminScreen.showStory();server(AdminService::open);}
            case 160 -> click("Wither: Containment Failure");
            case 180 -> capture="01-admin-wither-controls.png";
            case 190 -> click("Open breach");
            case 360 -> click("Enter fight");
            case 400 -> {mc.setScreen(null);mc.options.hideGui=true;camera(10,6,16);server(p->{var b=WitherArena.boss();if(b==null)throw new IllegalStateException("Admin entry did not start Wither");b.skipArrival();b.cancelChain();b.still(true);b.holdOff(10000);p.teleportTo(p.serverLevel(),.5,WitherGeometry.floor().y,14.5,180,0);});}
            case 425 -> capture="02-new-creature.png";
            case 430 -> {camera(-9,4,11);}
            case 440 -> capture="02b-creature-three-quarter.png";
            case 444 -> {mc.options.hideGui=false;capture="02c-boss-bar-phase-one.png";}
            case 448 -> {mc.options.hideGui=true;camera(5,7,24);}
            case 455 -> mark(WitherGeometry.Move.BITE);
            case 468 -> capture="03-maw-snap-warning.png";
            case 474 -> capture="04-maw-snap.png";
            case 505 -> mark(WitherGeometry.Move.VERDICT);
            case 525 -> capture="05-verdict-windup.png";
            case 535 -> capture="06-three-mouth-beams-fire.png";
            case 570 -> stage(p->{var b=WitherArena.boss();int[] pillar=WitherGeometry.PILLARS[0];Vec3 face=WitherGeometry.floor().add(pillar[0]+.5,0,pillar[1]+.5-pillar[2]-1.5);b.setPos(face.add(0,.35,-13));p.teleportTo(p.serverLevel(),face.x,face.y,face.z,180,0);},WitherGeometry.Move.LUNGE);
            case 572 -> TempestCamera.fix(WitherGeometry.floor().add(-4,7,-17),WitherGeometry.floor().add(-14,4,1));
            case 590 -> capture="07-charge-lane-ends-in-stone.png";
            case 604 -> capture="08-charge-in-motion.png";
            case 614 -> capture="09-crashed-into-the-pillar.png";
            case 680 -> {camera(16,12,17);mark(WitherGeometry.Move.SLAM);}
            case 705 -> capture="10-lifted-slam.png";
            case 712 -> capture="11-slam-lands.png";
            case 722 -> capture="12-floor-thrown-up.png";
            case 760 -> {camera(-5,8,22);mark(WitherGeometry.Move.PULSE);}
            case 776 -> capture="13-pulse-draws-in.png";
            case 795 -> capture="14-rolling-floor.png";
            case 830 -> mark(WitherGeometry.Move.RAIN);
            case 846 -> capture="15-rain-warnings.png";
            case 862 -> capture="16-skulls-fall.png";
            case 868 -> server(p->WitherArena.boss().startSideShot(2,p.position().add(0,.8,0)));
            case 879 -> capture="17-side-head-aims.png";
            case 886 -> capture="18-side-head-fires.png";
            case 905 -> mark(WitherGeometry.Move.TETHER);
            case 921 -> capture="19-tether-hook-warning.png";
            case 927 -> capture="20-tether-snaps-out.png";
            case 960 -> {mc.options.hideGui=false;AdminScreen.showStory();server(AdminService::open);}
            case 980 -> click("Wither: Containment Failure");
            case 990 -> click("Advance phase");
            case 1005 -> capture="21-admin-live-status.png";
            case 1010 -> {mc.setScreen(null);mc.options.hideGui=true;camera(-6,9,20);}
            case 1022 -> capture="22-broken-vessel-roar.png";
            case 1045 -> capture="23-plates-shed.png";
            case 1070 -> mark(WitherGeometry.Move.RAKE);
            case 1084 -> capture="24-rake-warning.png";
            case 1091 -> capture="25-rake-sweep.png";
            case 1130 -> {camera(-6,10,18);mark(WitherGeometry.Move.SPIRES);}
            case 1146 -> capture="26-spire-crack.png";
            case 1158 -> capture="27-bone-spires.png";
            case 1166 -> capture="27b-bone-spires-run.png";
            case 1168 -> {camera(-7,9,21);mark(WitherGeometry.Move.BROOD);}
            case 1190 -> capture="27c-brood-retch-warning.png";
            case 1202 -> capture="27d-brood-spewed.png";
            case 1216 -> capture="27e-brood-lands-and-fights.png";
            case 1222 -> server(p->WitherArena.boss().operatorPhase());
            case 1240 -> {camera(-6,9,20);capture="28-critical-mass.png";}
            case 1250 -> {camera(4,6,23);mark(WitherGeometry.Move.HORIZON);}
            case 1300 -> capture="29-hungry-star-charge.png";
            case 1318 -> capture="30-hungry-star-discharge.png";
            default -> {}
        }
        if(age==1343)mc.options.hideGui=false;
        if(age==1342){var cam=WitherGeometry.floor().add(0,28,-21);TempestCamera.fix(cam,WitherGeometry.floor().add(0,2,6));server(p->{var b=WitherArena.boss();b.cancelChain();b.still(false);b.rest=0;b.setPos(WitherGeometry.floor().add(0,.35,0));liveStart=b.attacksLanded();p.teleportTo(p.serverLevel(),.5,WitherGeometry.floor().y,22.5,180,0);});}
        if(age==2082){keys(false);server(p->{var b=WitherArena.boss();int actions=b.attacksLanded()-liveStart;PreviewQuiet.report("WITHER_ACTIVE_AI "+(actions>=8?"PASS":"FAIL")+" "+actions+" attacks in 37 seconds; final="+b.position());b.cancelChain();b.still(true);});}
        if(age==2110){mc.options.hideGui=false;TempestCamera.fix(null,null);mc.setScreen(new GuideScreen(null).topic("wither"));}
        if(age==2130)capture="40-withers-guide.png";
        if(age==2160){mc.setScreen(null);AdminScreen.showStory();server(AdminService::open);}
        if(age==2180)click("Wither: Containment Failure");
        if(age==2200)click("Cancel event");
        if(age==2230){server(p->PreviewQuiet.report("WITHER_ADMIN_CANCEL "+(!WitherArena.busy(p.getServer())&&!WitherArena.inside(p)?"PASS":"FAIL")));capture="41-admin-cancelled.png";}
        if(age==2270)PreviewQuiet.quit();
        if(age>=1345&&age<2080){
            var list=mc.level.getEntitiesOfClass(RiftWitherEntity.class,new AABB(mc.player.blockPosition()).inflate(90));
            if(!list.isEmpty()){
                var b=list.getFirst();Vec3 delta=b.position().subtract(mc.player.position());mc.player.setYRot((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)));mc.player.setXRot(0);keys(true);mc.options.keyUp.setDown(delta.length()>13);mc.options.keyJump.setDown(b.action()==WitherGeometry.Move.PULSE&&b.actionAge()>WitherGeometry.Move.PULSE.windup+3&&b.actionAge()<WitherGeometry.Move.PULSE.windup+17);
                if(age%80==0){TempestCamera.fix(WitherGeometry.floor().add(0,26,-21),b.core());capture=String.format("%02d-active-chain-%d.png",31+(age-1345)/80,age);}
            }
        }
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event){
        boolean clip=age>=570&&age<730&&lastClipAge!=age;if(capture==null&&!clip)return;var mc=Minecraft.getInstance();
        try(var shot=Screenshot.takeScreenshot(mc.getMainRenderTarget())){
            var folder=mc.gameDirectory.toPath().resolve("screenshots");
            if(capture!=null){var path=folder.resolve(capture);Files.createDirectories(path.getParent());shot.writeToFile(path);System.out.println("WITHER_REWORK_CAPTURE "+capture);}
            if(clip){var path=folder.resolve("frames").resolve(String.format("%04d.png",age-570));Files.createDirectories(path.getParent());shot.writeToFile(path);lastClipAge=age;}
        }catch(Exception e){throw new RuntimeException(e);}capture=null;
    }
}
