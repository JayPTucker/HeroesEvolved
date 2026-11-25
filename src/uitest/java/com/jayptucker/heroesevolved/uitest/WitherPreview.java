package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.client.effects.TempestCamera;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.network.*;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.story.bunker.CryoChamberService;
import com.jayptucker.heroesevolved.story.prologue.PrologueService;
import com.jayptucker.heroesevolved.time.intro.city.CityAudit;
import com.jayptucker.heroesevolved.wither.*;
import net.minecraft.client.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import java.nio.file.Files;
import java.util.function.Consumer;

/**
 * The Wither's story, photographed: the construction dragged backwards through the breach it tears open, the standing
 * breach, the arrival out of a tear over the Ossuary's dais, the Ossuary itself, and the death scene: the star's swell,
 * the detonation, being thrown flat and blinking awake, the exit torn open where it died, and the breach at home closing.
 */
@EventBusSubscriber(modid="heroesevolved",value=Dist.CLIENT)
public final class WitherPreview {
    private static final Vec3 SOURCE=new Vec3(150.5,70,150.5);
    private static boolean started;
    private static int startup,age;
    private static String capture;
    private static void server(Consumer<ServerPlayer> task){
        var mc=Minecraft.getInstance();var id=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{try{task.accept(mc.getSingleplayerServer().getPlayerList().getPlayer(id));}catch(Throwable e){e.printStackTrace();PreviewQuiet.report("WITHER_PREVIEW FAIL "+e);}});
    }
    private static void camera(Vec3 eye,Vec3 look){Minecraft.getInstance().options.setCameraType(CameraType.THIRD_PERSON_BACK);TempestCamera.fix(eye,look);}
    private static void arena(double x,double y,double z,Vec3 look){camera(WitherGeometry.floor().add(x,y,z),look);}
    private static Vec3 core(){var mc=Minecraft.getInstance();var list=mc.level.getEntitiesOfClass(RiftWitherEntity.class,mc.player.getBoundingBox().inflate(80));return list.isEmpty()?WitherGeometry.center():list.getFirst().core();}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!Boolean.getBoolean("heroes.preview.wither"))return;
        var mc=Minecraft.getInstance();
        if(!started&&++startup>=35&&mc.screen!=null&&mc.getOverlay()==null){
            started=true;mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(8);mc.options.guiScale().set(2);mc.options.fov().set(70);mc.resizeDisplay();
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            var rules=new GameRules();rules.getRule(GameRules.RULE_DAYLIGHT).set(false,null);rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
            mc.createWorldOpenFlows().createFreshLevel("Wither-"+System.currentTimeMillis(),new LevelSettings("Wither Preview",GameType.CREATIVE,false,Difficulty.NORMAL,true,rules,WorldDataConfiguration.DEFAULT),new WorldOptions(73,false,false),a->a.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(),mc.screen);
        }
        if(mc.player==null||mc.getSingleplayerServer()==null||mc.getOverlay()!=null)return;
        if(mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen)mc.setScreen(null);
        age++;
        if(age%20==0){mc.getToasts().clear();}
        if(age==104)server(PrologueService::skip);
        if(age==110){mc.options.hideGui=true;mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);server(p->{
            CryoChamberService.release(p);WitherArena.reset(p.getServer(),false);var l=p.getServer().overworld();l.setDayTime(18000);
            for(int x=8;x<=10;x++)for(int z=8;z<=10;z++)l.setChunkForced(x,z,true);
            for(int x=-10;x<=10;x++)for(int z=-10;z<=12;z++){l.setBlock(BlockPos.containing(SOURCE).offset(x,-1,z),(x+z)%7==0?Blocks.COARSE_DIRT.defaultBlockState():Blocks.GRASS_BLOCK.defaultBlockState(),3);for(int y=0;y<9;y++)l.setBlock(BlockPos.containing(SOURCE).offset(x,y,z),Blocks.AIR.defaultBlockState(),3);}
            p.teleportTo(l,SOURCE.x,SOURCE.y,SOURCE.z-9,0,0);p.setData(ModDataAttachments.POWER_INTRO_HISTORY,p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(ModAbilities.FLIGHT_ID));
            p.setData(ModDataAttachments.PLAYER_ABILITIES,PlayerAbilityData.empty().replaceWithUnlockedPower(ModAbilities.FLIGHT_ID));PlayerProgressionService.setPowerLevel(p,20);PlayerPowerSyncService.sync(p);
        });camera(SOURCE.add(7,3,-8),SOURCE.add(0,2.5,2));}
        Vec3 gate=SOURCE.add(0,WitherGeometry.GATE_LIFT,4.5);
        switch(age){
            // The summoning site: the native construction is intercepted; the Wither forms facing the builder.
            case 150 -> {camera(SOURCE.add(8,3.5,-7),SOURCE.add(0,2.6,2));server(p->{var world=p.getServer().overworld();p.teleportTo(world,SOURCE.x,SOURCE.y,SOURCE.z-9,0,0);var w=EntityType.WITHER.create(world);w.moveTo(SOURCE);w.setYRot(180);w.makeInvulnerable();world.addFreshEntity(w);});}
            case 165 -> capture="01-the-wither-knits-together.png";
            case 187 -> capture="02-it-roars-at-its-builder.png";
            case 200 -> capture="03-a-seam-splits-behind-it.png";
            case 214 -> capture="04-the-breach-tears-open.png";
            case 232 -> {camera(SOURCE.add(9,3,-3),SOURCE.add(0,2.6,2.5));capture="05-dragged-claws-dug-in.png";}
            case 256 -> capture="06-yanked-through.png";
            case 268 -> capture="07-swallowed.png";
            case 286 -> capture="08-the-breach-convulses-shut.png";
            case 320 -> {camera(SOURCE.add(2.5,2.6,-1.5),gate);capture="09-the-breach-it-left.png";}
            case 330 -> server(p->PreviewQuiet.report("WITHER_SOURCE "+WitherArena.ready()+" "+WitherData.get(p.getServer()).source));
            // The Ossuary: in through the breach, and the Wither falls out of a tear over the dais.
            case 336 -> {TempestCamera.fix(null,null);mc.options.setCameraType(CameraType.FIRST_PERSON);}
            case 340 -> server(p->{if(!WitherArena.enter(p))throw new IllegalStateException("Entry refused");p.teleportTo(p.serverLevel(),.5,WitherGeometry.floor().y,-24.5,0,-17);WitherArena.boss().still(true);WitherArena.boss().holdOff(10000);});
            case 364 -> server(p->p.teleportTo(p.serverLevel(),.5,WitherGeometry.floor().y,-24.5,0,-6));
            case 352 -> capture="10-a-tear-over-the-dais.png";
            case 361 -> capture="11-it-falls-out.png";
            case 371 -> capture="12-it-lands.png";
            case 384 -> capture="13-and-roars.png";
            case 405 -> server(p->p.teleportTo(p.serverLevel(),.5,WitherGeometry.floor().y,-31.5,0,0));
            case 410 -> {arena(-21,27,-21,WitherGeometry.floor().add(0,2,4));capture="14-the-ossuary.png";}
            case 425 -> {arena(0,1.7,-27.5,WitherGeometry.floor().add(0,5,0));capture="15-from-the-entrance.png";}
            case 440 -> {arena(24,6,8,WitherGeometry.floor().add(-10,8,-4));capture="16-wall-and-vault.png";server(p->PreviewQuiet.report("WITHER_AUDIT "+CityAudit.run(p.serverLevel(),-42,64,-43,42,121,46,true)));}
            // Its death: the star swells, drinks the room in and goes off; everyone is thrown flat.
            case 460 -> {server(p->{p.teleportTo(p.serverLevel(),.5,WitherGeometry.floor().y,-13.5,0,-8);WitherArena.boss().operatorDefeat();
                // The camera's player holds a shield power through the detonation so the blast itself can be photographed;
                // the knock-down is applied to them just after.
                p.setData(ModDataAttachments.PLAYER_ABILITIES,PlayerAbilityData.empty().replaceWithUnlockedPower(ModAbilities.INVISIBILITY_ID));PlayerPowerSyncService.sync(p);});}
            case 462 -> arena(13,5,-15,WitherGeometry.center());
            case 476 -> capture="17-death-throes.png";
            case 495 -> capture="18-its-ribs-burst.png";
            case 525 -> {arena(15,8,-17,WitherGeometry.center().add(0,2,0));capture="19-the-star-swells.png";}
            case 550 -> capture="20-it-drinks-everything-in.png";
            case 556 -> arena(-16,10,-20,WitherGeometry.center());
            case 558 -> capture="21-detonation.png";
            case 563 -> capture="21b-shock-dome.png";
            case 572 -> capture="21c-smoke-and-scorch.png";
            case 574 -> {TempestCamera.fix(null,null);mc.options.setCameraType(CameraType.FIRST_PERSON);server(p->{
                p.setData(ModDataAttachments.PLAYER_ABILITIES,PlayerAbilityData.empty().replaceWithUnlockedPower(ModAbilities.FLIGHT_ID));PlayerPowerSyncService.sync(p);
                WitherArena.blast(p,WitherGeometry.floor().add(0,5,0));});}
            case 590 -> capture="22-thrown-flat-out-cold.png";
            case 640 -> capture="23-eyes-blinking-open.png";
            case 708 -> capture="24-coming-to-the-way-out.png";
            case 728 -> {server(p->PreviewQuiet.report("WITHER_EXIT "+WitherArena.exitPortal()+" down="+WitherArena.down(p)));Vec3 exit=WitherArena.exitPortal();if(exit!=null)camera(exit.add(5,1,-7),exit);}
            case 740 -> capture="25-the-exit-where-it-died.png";
            case 752 -> server(p->{Vec3 exit=WitherArena.exitPortal();if(exit!=null)p.teleportTo(p.serverLevel(),exit.x,WitherGeometry.floor().y,exit.z,180,0);});
            case 760 -> camera(SOURCE.add(5,2.5,-6),gate);
            case 777 -> capture="26-home-the-breach-slams-shut.png";
            case 784 -> capture="27-and-is-gone.png";
            case 800 -> server(p->PreviewQuiet.report("WITHER_HOME "+(p.level()==p.getServer().overworld()&&!WitherArena.busy(p.getServer())?"PASS":"FAIL")+" stars="+p.getInventory().countItem(net.minecraft.world.item.Items.NETHER_STAR)));
            case 840 -> PreviewQuiet.quit();
            default -> {}
        }
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event){
        if(capture==null)return;var mc=Minecraft.getInstance();
        try(var shot=Screenshot.takeScreenshot(mc.getMainRenderTarget())){var file=mc.gameDirectory.toPath().resolve("screenshots").resolve(capture);Files.createDirectories(file.getParent());shot.writeToFile(file);System.out.println("WITHER_CAPTURE "+capture+" camera="+mc.gameRenderer.getMainCamera().getPosition()+" player="+mc.player.position());}catch(Exception e){throw new RuntimeException(e);}capture=null;
    }
}
