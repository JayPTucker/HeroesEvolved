package com.jayptucker.heroesevolved.client.sound;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.endgame.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Six original, bar-aligned 100 BPM arrangements. Music slider, pause, retreat and sound reload all work. */
@EventBusSubscriber(modid=HeroesEvolved.MOD_ID,value=Dist.CLIENT)
public final class EndBattleMusic {
    private static final String[] NAMES={"tethered","riftstorm","dying_star","inheritance","collector","one_more_future"};
    private static Stem current;private static int phase,requested,barClock,lastStage;
    private static SoundEvent sound(String name){return SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("heroesevolved","end_"+name));}
    @SubscribeEvent public static void select(SelectMusicEvent event){if(here()&&EndClientState.view().active())event.setMusic(null);}
    private static boolean here(){var mc=Minecraft.getInstance();return mc.level!=null&&mc.player!=null&&mc.level.dimension().equals(Level.END)&&mc.player.position().multiply(1,0,1).lengthSqr()<180*180;}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();if(mc.isPaused())return;EndClientState.tick();
        EndView v=EndClientState.view();
        if(!here()||!v.active()){fade();phase=requested=barClock=lastStage=0;if(!here())EndClientState.clear();return;}
        if(v.stage()!=lastStage){
            if(v.stage()==EndBattle.ABSORPTION){fade();phase=requested=0;mc.getMusicManager().stopPlaying();mc.getSoundManager().play(SimpleSoundInstance.forMusic(sound("false_victory")));}
            if(v.stage()==EndBattle.VICTORY){fade();mc.getSoundManager().play(SimpleSoundInstance.forMusic(sound("last_light")));}
            lastStage=v.stage();
        }
        if(v.stage()==EndBattle.ABSORPTION||v.stage()==EndBattle.VICTORY)return;
        int next=(v.stage()<=EndBattle.DRAGON_DEATH?0:3)+Math.clamp(v.phase(),1,3);
        if(phase==0){mc.getMusicManager().stopPlaying();switchTo(next);}
        else if(current!=null&&!mc.getSoundManager().isActive(current)&&mc.options.getSoundSourceOptionInstance(SoundSource.MUSIC).get()>0&&mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).get()>0)switchTo(phase);
        if(next!=phase&&requested!=next){requested=next;mc.getSoundManager().play(SimpleSoundInstance.forMusic(sound("phase_break")));}
        // 4 beats * .6 seconds * 20 server ticks = 48 ticks per bar.
        if(++barClock%48==0&&requested!=phase)switchTo(requested);
    }
    private static void fade(){if(current!=null){current.fade=true;current=null;}}
    private static void switchTo(int next){fade();phase=requested=next;barClock=0;current=new Stem(sound(NAMES[next-1]));Minecraft.getInstance().getSoundManager().play(current);}
    private static final class Stem extends AbstractTickableSoundInstance {
        boolean fade;
        Stem(SoundEvent event){super(event,SoundSource.MUSIC,RandomSource.create());looping=true;delay=0;relative=true;attenuation=Attenuation.NONE;volume=.001F;}
        @Override public boolean canStartSilent(){return true;}
        @Override public void tick(){volume=Math.clamp(volume+(fade?-.04F:.04F),0,.8F);if(fade&&volume<=0)stop();}
    }
    private EndBattleMusic(){}
}
