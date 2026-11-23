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
    private static void arena(double x,double y,double z){camera(WitherGeometry.floor().add(x,y,z),WitherGeometry.center().add(0,5,0));}
    private static void mark(WitherGeometry.Move move){server(p->{var b=WitherArena.boss();b.start(move);});}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!Boolean.getBoolean("heroes.preview.wither"))return;
        var mc=Minecraft.getInstance();
        if(!started&&++startup>=35&&mc.screen!=null&&mc.getOverlay()==null){
            started=true;mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(7);mc.options.guiScale().set(2);mc.options.fov().set(70);mc.resizeDisplay();
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            var rules=new GameRules();rules.getRule(GameRules.RULE_DAYLIGHT).set(false,null);rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
            mc.createWorldOpenFlows().createFreshLevel("Wither-"+System.currentTimeMillis(),new LevelSettings("Wither Preview",GameType.CREATIVE,false,Difficulty.NORMAL,true,rules,WorldDataConfiguration.DEFAULT),new WorldOptions(73,false,false),a->a.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(),mc.screen);
        }
        if(mc.player==null||mc.getSingleplayerServer()==null||mc.getOverlay()!=null)return;
        if(mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen)mc.setScreen(null);
        age++;
        if(age%20==0){ClientScreenTintState.clear();mc.getToasts().clear();}
        if(age==104)server(PrologueService::skip);
        if(age==110){mc.options.hideGui=true;mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);server(p->{
            CryoChamberService.release(p);WitherArena.reset(p.getServer(),false);var l=p.getServer().overworld();l.setDayTime(18000);
            for(int x=8;x<=10;x++)for(int z=8;z<=10;z++)l.setChunkForced(x,z,true);
            for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++){l.setBlock(BlockPos.containing(SOURCE).offset(x,-1,z),Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(),3);for(int y=0;y<9;y++)l.setBlock(BlockPos.containing(SOURCE).offset(x,y,z),Blocks.AIR.defaultBlockState(),3);}
            for(int x:new int[]{-5,5})for(int z:new int[]{-5,5})l.setBlock(BlockPos.containing(SOURCE).offset(x,-1,z),Blocks.SEA_LANTERN.defaultBlockState(),3);
            p.teleportTo(l,SOURCE.x+5,SOURCE.y,SOURCE.z-6,0,0);p.setData(ModDataAttachments.POWER_INTRO_HISTORY,p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(ModAbilities.FLIGHT_ID));
            p.setData(ModDataAttachments.PLAYER_ABILITIES,PlayerAbilityData.empty().replaceWithUnlockedPower(ModAbilities.FLIGHT_ID));PlayerProgressionService.setPowerLevel(p,20);PlayerPowerSyncService.sync(p);
        });camera(SOURCE.add(7,4,-9),SOURCE.add(0,2,0));}
        switch(age){
            case 150 -> {camera(SOURCE.add(5,3,-6),SOURCE.add(0,2,0));server(p->{var world=p.getServer().overworld();p.teleportTo(world,SOURCE.x+4,SOURCE.y,SOURCE.z-4,0,0);p.absMoveTo(SOURCE.x+4,SOURCE.y,SOURCE.z-4,0,0);var w=EntityType.WITHER.create(world);w.moveTo(SOURCE);w.setYRot(180);w.makeInvulnerable();world.addFreshEntity(w);PreviewQuiet.report("WITHER_STAGE "+p.level().dimension()+" "+p.position()+" floor="+world.getBlockState(BlockPos.containing(SOURCE).below()));});}
            case 181 -> capture="01-the-cage-forms.png";
            case 205 -> capture="02-chest-opens.png";
            case 230 -> capture="03-heads-swallowed.png";
            case 250 -> capture="04-body-folds.png";
            case 290 -> capture="05-unstable-breach.png";
            case 310 -> server(p->{if(!WitherArena.enter(p))throw new IllegalStateException("Entry refused");WitherArena.boss().still(true);WitherArena.boss().holdOff(10000);p.teleportTo(p.serverLevel(),.5,WitherGeometry.floor().y,14.5,0,0);});
            case 390 -> arena(49,39,-52);
            case 420 -> capture="06-flight-sized-arena.png";
            case 440 -> {camera(WitherGeometry.floor().add(7,1.7,-30),WitherGeometry.center().add(0,2,0));server(p->PreviewQuiet.report("WITHER_AUDIT "+CityAudit.run(p.serverLevel(),-42,64,-43,42,118,43,true)));}
            case 460 -> capture="07-player-entrance.png";
            case 480 -> {arena(12,9,23);mark(WitherGeometry.Move.VERDICT);}
            case 497 -> capture="08-threefold-verdict.png";
            case 540 -> mark(WitherGeometry.Move.RAIN);
            case 560 -> capture="09-hollow-rain.png";
            case 610 -> mark(WitherGeometry.Move.PULSE);
            case 658 -> capture="10-expanding-pulse.png";
            case 750 -> {mark(WitherGeometry.Move.TETHER);}
            case 765 -> capture="11-grave-tether.png";
            case 800 -> server(p->{var b=WitherArena.boss();b.setHealth(b.getMaxHealth()*.7F);b.phaseFinished(p);});
            case 875 -> {arena(14,9,21);capture="12-broken-vessel.png";}
            case 960 -> mark(WitherGeometry.Move.LUNGE);
            case 978 -> capture="13-ribbreaker.png";
            case 1000 -> mark(WitherGeometry.Move.SLAM);
            case 1022 -> capture="14-ossuary-slam.png";
            case 1080 -> server(p->{var b=WitherArena.boss();b.start(WitherGeometry.Move.RAIN);b.backfire(b.anchor(),p);});
            case 1090 -> capture="15-anchor-backfire.png";
            case 1160 -> server(p->{var b=WitherArena.boss();b.setHealth(b.getMaxHealth()*.36F);b.phaseFinished(p);});
            case 1230 -> {arena(18,10,26);capture="16-critical-mass.png";}
            case 1320 -> mark(WitherGeometry.Move.HORIZON);
            case 1366 -> capture="17-event-horizon-cover.png";
            case 1390 -> server(p->{var b=WitherArena.boss();b.setPos(WitherGeometry.floor().add(0,25,0));p.getAbilities().flying=true;p.onUpdateAbilities();p.teleportTo(p.serverLevel(),.5,WitherGeometry.floor().y+25,16.5,0,0);});
            case 1420 -> {camera(WitherGeometry.floor().add(14,30,25),WitherGeometry.floor().add(0,27,0));mark(WitherGeometry.Move.VERDICT);}
            case 1438 -> capture="18-aerial-fight.png";
            case 1490 -> server(p->{var b=WitherArena.boss();b.setHealth(b.getMaxHealth()*.08F);b.phaseFinished(p);});
            case 1510 -> capture="19-star-collapse.png";
            case 1630 -> PreviewQuiet.quit();
            default -> {}
        }
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event){
        if(capture==null)return;var mc=Minecraft.getInstance();
        try(var shot=Screenshot.takeScreenshot(mc.getMainRenderTarget())){var file=mc.gameDirectory.toPath().resolve("screenshots").resolve(capture);Files.createDirectories(file.getParent());shot.writeToFile(file);System.out.println("WITHER_CAPTURE "+capture+" camera="+mc.gameRenderer.getMainCamera().getPosition()+" player="+mc.player.position()+" sourceFloor="+mc.level.getBlockState(BlockPos.containing(SOURCE).below())+" bodies="+mc.level.getEntitiesOfClass(RiftWitherEntity.class,new net.minecraft.world.phys.AABB(BlockPos.containing(SOURCE)).inflate(8)).size());}catch(Exception e){throw new RuntimeException(e);}capture=null;
    }
}
