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
    private static void server(Consumer<ServerPlayer> task){var mc=Minecraft.getInstance();var id=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{try{task.accept(mc.getSingleplayerServer().getPlayerList().getPlayer(id));}catch(Throwable e){e.printStackTrace();PreviewQuiet.report("ENDGAME_PREVIEW FAIL "+e);}});}
    private static void camera(double x,double y,double z,double tx,double ty,double tz){Minecraft.getInstance().options.setCameraType(CameraType.THIRD_PERSON_BACK);TempestCamera.fix(new Vec3(x,y,z),new Vec3(tx,ty,tz));}
    private static void mark(EndGeometry.Move move){server(p->{var e=EndBattle.current();e.transition=e.stagger=0;e.start(move);});}
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
            case 440 -> camera(20,98,27,0,91,0);
            case 465 -> capture="04-tethered-sky.png";
            case 490 -> mark(EndGeometry.Move.BREATH);
            case 513 -> capture="05-severing-breath-air-lanes.png";
            case 555 -> mark(EndGeometry.Move.STARS);
            case 576 -> capture="06-starfall-warning.png";
            case 595 -> capture="07-falling-constellations.png";
            case 630 -> mark(EndGeometry.Move.WINGBREAK);
            case 684 -> {camera(32,80,42,0,67,0);capture="08-wingbreak-wave.png";}
            case 730 -> {camera(22,98,28,0,91,0);mark(EndGeometry.Move.HARPOON);}
            case 748 -> capture="09-sky-harpoon.png";
            case 800 -> phase(2);
            case 856 -> capture="10-riftstorm.png";
            case 890 -> mark(EndGeometry.Move.RIFT_CHARGE);
            case 914 -> capture="11-committed-charge.png";
            case 925 -> capture="12-dragon-charge.png";
            case 970 -> {server(p->{var e=EndBattle.current();e.dragon.setPos(.5,89,.5);});mark(EndGeometry.Move.BLOOM);}
            case 992 -> capture="13-fracture-bloom.png";
            case 1040 -> phase(3);
            case 1095 -> capture="14-dying-star.png";
            case 1130 -> mark(EndGeometry.Move.UNMAKING);
            case 1175 -> {camera(40,104,48,9,85,8);capture="15-unmaking-safe-orbit.png";}
            case 1220 -> server(p->{var e=EndBattle.current();e.ground(p);});
            case 1230 -> capture="16-grounding-backfire.png";
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
        if(age>=2330&&age<2330+21*85){int i=(age-2330)/85,at=(age-2330)%85;
            if(at==0){camera(22,81,32,7,74,15);final int index=i;server(p->{var e=EndBattle.current();e.sylar.setPos(.5,72,8);e.sampled=index;e.transition=e.stagger=0;e.start(EndGeometry.Move.PATTERN);});}
            if(at==25)capture=String.format("%02d-pattern-%s.png",30+i,EndMoves.powerPath(i));
        }
        if(age==4140){camera(3.8,74.4,13,.5,73.1,8);server(p->EndBattle.current().startVictory());}
        if(age==4164)capture="51-body-fractures.png";
        if(age==4195)capture="52-the-light-breaks.png";
        if(age==4211)camera(37,95,44,0,77,8);
        if(age==4215)capture="53-ability-detonation.png";
        if(age==4232)capture="54-world-of-power.png";
        if(age==4255){camera(105,117,112,0,78,0);capture="55-island-wide-energy-wave.png";}
        if(age==4282)capture="56-borrowed-light-disperses.png";
        if(age==4330){camera(30,86,36,0,67,0);capture="57-native-exit-released.png";}
        if(age==4430)PreviewQuiet.quit();
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event){if(capture==null)return;var mc=Minecraft.getInstance();try(var shot=Screenshot.takeScreenshot(mc.getMainRenderTarget())){var f=mc.gameDirectory.toPath().resolve("screenshots").resolve(capture);Files.createDirectories(f.getParent());shot.writeToFile(f);System.out.println("ENDGAME_CAPTURE "+capture+" camera="+mc.gameRenderer.getMainCamera().getPosition()+" player="+mc.player.position());}catch(Exception e){throw new RuntimeException(e);}capture=null;}
}
