package com.jayptucker.heroesevolved.uitest;
import com.jayptucker.heroesevolved.data.EclipseCalendar;
import com.jayptucker.heroesevolved.data.EclipseTimeline;
import com.jayptucker.heroesevolved.network.ClientEclipseState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import java.nio.file.*;
/** The square moon crossing the sun, photographed at each stage of an Eclipse, 200 blocks from spawn. */
@EventBusSubscriber(modid="heroesevolved",value=Dist.CLIENT)
public final class EclipsePreview {
    static final int APPROACH=3600,TOTALITY=4200,DEPARTURE=3600;
    /** Ticks after first contact, field of view, and file name for each photograph. */
    record Shot(int sinceFirstContact,int fov,String name){}
    static final Shot[] SHOTS={
        new Shot(APPROACH/2,70,"eclipse-partial-wide.png"),
        new Shot(APPROACH/2,30,"eclipse-partial.png"),
        new Shot(APPROACH*93/100,30,"eclipse-crescent.png"),
        new Shot(APPROACH+8,30,"eclipse-diamond-ring.png"),
        new Shot(APPROACH+TOTALITY/2,30,"eclipse-totality.png"),
        new Shot(APPROACH+TOTALITY/2,70,"eclipse-totality-wide.png"),
        new Shot(APPROACH+TOTALITY+DEPARTURE/3,30,"eclipse-departure.png")};
    static int ticks,world;static boolean started;static String pending;
    @SubscribeEvent public static void tick(ClientTickEvent.Post e){
        if(!Boolean.getBoolean("heroes.preview.eclipse"))return;
        Minecraft mc=Minecraft.getInstance();
        if(!started&&++ticks>40&&mc.screen!=null&&mc.getOverlay()==null){
            started=true;mc.options.pauseOnLostFocus=false;mc.options.guiScale().set(3);mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            var rules=new GameRules();rules.getRule(GameRules.RULE_DAYLIGHT).set(false,null);rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false,null);
            mc.createWorldOpenFlows().createFreshLevel("Eclipse-"+System.currentTimeMillis(),new LevelSettings("Eclipse preview",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,rules,WorldDataConfiguration.DEFAULT),new WorldOptions(77L,false,false),access->access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(),mc.screen);
        }
        if(mc.player==null||mc.level==null)return;
        if(mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen)mc.setScreen(null);
        world++;
        if(world==180)mc.getSingleplayerServer().execute(()->{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            com.jayptucker.heroesevolved.story.bunker.CryoChamberService.release(p);
            p.getAbilities().flying=true;p.onUpdateAbilities();
            p.connection.teleport(200.5,-58,200.5,-90,-42);
            // Mid-morning: the sun stands about 42 degrees up in the east.
            p.serverLevel().setDayTime(2000);p.serverLevel().setWeatherParameters(12000,0,false,false);
        });
        if(world==200){mc.setScreen(null);mc.options.hideGui=true;mc.gui.getChat().clearMessages(true);mc.getToasts().clear();}
        if(world>=200){mc.player.setYRot(-90);mc.player.setXRot(-42);mc.player.yRotO=-90;mc.player.xRotO=-42;}
        int shot=(world-220)/40,step=(world-220)%40;
        if(world>=220&&shot<SHOTS.length){
            Shot s=SHOTS[shot];
            if(step==0){
                mc.options.fov().set(s.fov());
                ClientEclipseState.set(new EclipseTimeline(mc.level.getGameTime()-s.sinceFirstContact(),APPROACH,TOTALITY,DEPARTURE),EclipseCalendar.intervalDays());
            }
            if(step==30)pending=s.name();
        }
        if(world==220+40*SHOTS.length){ClientEclipseState.clear();mc.stop();}
    }
    @SubscribeEvent public static void render(RenderFrameEvent.Post e){if(pending==null)return;var mc=Minecraft.getInstance();try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){Path out=mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);Files.createDirectories(out.getParent());image.writeToFile(out);}catch(Exception ex){throw new RuntimeException(ex);}pending=null;}
}
