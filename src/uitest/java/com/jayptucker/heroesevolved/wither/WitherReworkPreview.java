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
    private static void mark(WitherGeometry.Move move){server(p->{var b=WitherArena.boss();b.cancelChain();b.setPos(WitherGeometry.floor().add(0,.35,0));b.stagger=b.transition=0;queuedBoss=b;queuedMove=move;queuedAt=b.level().getGameTime()+3;});}
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
            case 330 -> click("Enter fight");
            case 365 -> {mc.setScreen(null);mc.options.hideGui=true;camera(10,6,16);server(p->{var b=WitherArena.boss();if(b==null)throw new IllegalStateException("Admin entry did not start Wither");b.cancelChain();b.still(true);b.holdOff(10000);p.teleportTo(p.serverLevel(),.5,WitherGeometry.floor().y,14.5,180,0);});}
            case 395 -> capture="02-new-creature.png";
            case 400 -> server(p->PreviewQuiet.report("WITHER_AUDIT "+CityAudit.run(p.serverLevel(),-42,64,-43,42,118,43,true)));
            case 410 -> camera(13,7,23);
            case 420 -> mark(WitherGeometry.Move.BITE);
            case 435 -> capture="03-maw-snap.png";
            case 444 -> capture="04-maw-release.png";
            case 480 -> mark(WitherGeometry.Move.VERDICT);
            case 500 -> capture="05-mouth-beam-windup.png";
            case 512 -> capture="06-three-mouth-beams-fire.png";
            case 550 -> mark(WitherGeometry.Move.LUNGE);
            case 570 -> capture="07-charge-brace.png";
            case 584 -> capture="08-body-in-motion.png";
            case 610 -> mark(WitherGeometry.Move.SLAM);
            case 615 -> camera(25,22,32);
            case 639 -> capture="09-lifted-slam.png";
            case 657 -> capture="10-actual-landing.png";
            case 690 -> {camera(18,8,25);mark(WitherGeometry.Move.PULSE);}
            case 733 -> capture="11-jumpable-pressure.png";
            case 765 -> mark(WitherGeometry.Move.RAIN);
            case 786 -> capture="12-constellation-warning.png";
            case 805 -> capture="13-skull-constellation.png";
            case 812 -> server(p->WitherArena.boss().startSideShot(2,p.position().add(0,.8,0)));
            case 823 -> capture="13b-side-head-aims.png";
            case 831 -> capture="13c-side-head-fires.png";
            case 840 -> mark(WitherGeometry.Move.TETHER);
            case 866 -> capture="14-visible-hook.png";
            case 900 -> {mc.options.hideGui=false;AdminScreen.showStory();server(AdminService::open);}
            case 920 -> click("Wither: Containment Failure");
            case 930 -> click("Advance phase");
            case 947 -> capture="15-admin-live-status.png";
            case 960 -> {mc.setScreen(null);mc.options.hideGui=true;camera(15,8,24);}
            case 985 -> capture="16-broken-vessel.png";
            case 1010 -> mark(WitherGeometry.Move.RAKE);
            case 1032 -> capture="17-rib-reaping-brace.png";
            case 1045 -> capture="18-ribs-sweep.png";
            case 1090 -> server(p->WitherArena.boss().operatorPhase());
            case 1150 -> capture="19-critical-mass.png";
            case 1175 -> mark(WitherGeometry.Move.HORIZON);
            case 1228 -> capture="20-hungry-star-charge.png";
            case 1243 -> capture="21-hungry-star-discharge.png";
            case 1280 -> {camera(22,15,27);server(p->{var b=WitherArena.boss();b.cancelChain();b.still(false);b.rest=0;b.setPos(WitherGeometry.floor().add(0,.35,0));liveStart=b.attacksLanded();p.teleportTo(p.serverLevel(),.5,WitherGeometry.floor().y,22.5,180,0);});}
            case 2020 -> {keys(false);server(p->{var b=WitherArena.boss();int actions=b.attacksLanded()-liveStart;PreviewQuiet.report("WITHER_ACTIVE_AI "+(actions>=8?"PASS":"FAIL")+" "+actions+" attacks in 37 seconds; final="+b.position());b.cancelChain();b.still(true);});}
            case 2050 -> {mc.options.hideGui=false;TempestCamera.fix(null,null);mc.setScreen(new GuideScreen(null).topic("wither"));}
            case 2070 -> capture="29-withers-guide.png";
            case 2100 -> {mc.setScreen(null);AdminScreen.showStory();server(AdminService::open);}
            case 2120 -> click("Wither: Containment Failure");
            case 2140 -> click("Cancel event");
            case 2170 -> {server(p->PreviewQuiet.report("WITHER_ADMIN_CANCEL "+(!WitherArena.busy(p.getServer())&&!WitherArena.inside(p)?"PASS":"FAIL")));capture="30-admin-cancelled.png";}
            case 2210 -> PreviewQuiet.quit();
            default -> {}
        }
        if(age>=1285&&age<2020){
            var list=mc.level.getEntitiesOfClass(RiftWitherEntity.class,new AABB(mc.player.blockPosition()).inflate(90));
            if(!list.isEmpty()){
                var b=list.getFirst();Vec3 delta=b.position().subtract(mc.player.position());mc.player.setYRot((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)));mc.player.setXRot(0);keys(true);mc.options.keyUp.setDown(delta.length()>13);mc.options.keyJump.setDown(b.action()==WitherGeometry.Move.PULSE&&b.actionAge()>WitherGeometry.Move.PULSE.windup+3&&b.actionAge()<WitherGeometry.Move.PULSE.windup+17);
                if(age%80==0){TempestCamera.fix(b.position().add(19,10,24),b.core());capture=String.format("%02d-active-chain-%d.png",22+(age-1280)/100,age);}
            }
        }
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event){
        boolean clip=age>=470&&age<665&&lastClipAge!=age;if(capture==null&&!clip)return;var mc=Minecraft.getInstance();
        try(var shot=Screenshot.takeScreenshot(mc.getMainRenderTarget())){
            var folder=mc.gameDirectory.toPath().resolve("screenshots");
            if(capture!=null){var path=folder.resolve(capture);Files.createDirectories(path.getParent());shot.writeToFile(path);System.out.println("WITHER_REWORK_CAPTURE "+capture);}
            if(clip){var path=folder.resolve("frames").resolve(String.format("%04d.png",age-470));Files.createDirectories(path.getParent());shot.writeToFile(path);lastClipAge=age;}
        }catch(Exception e){throw new RuntimeException(e);}capture=null;
    }
}
