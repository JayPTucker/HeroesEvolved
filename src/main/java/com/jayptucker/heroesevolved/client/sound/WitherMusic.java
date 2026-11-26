package com.jayptucker.heroesevolved.client.sound;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.wither.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.SelectMusicEvent;

/**
 * "The Hungry Cage": three original 75 bpm loops on one chart (tools/generate-wither-music.py). Phase changes play a
 * stinger and cross to the next loop on the next 64-tick bar; retreat fades the score. Its death silences the fight; the
 * last chord rises out of the ringing once the star detonates, and as it dies away the aftermath fades in and loops for
 * as long as you stay in the Ossuary.
 */
@EventBusSubscriber(modid=HeroesEvolved.MOD_ID,value=Dist.CLIENT)
public final class WitherMusic {
    private static Stem current,after;
    private static int phase,requested,beat,afterIn=-1;
    private static boolean ended;
    /** One bar at 75 bpm, in ticks. */
    private static final int BAR=64;
    /** From the detonation to the aftermath: the last chord's length, less its tail. */
    private static final int AFTER_DELAY=130;
    private WitherMusic(){}
    private static SoundEvent sound(String name){return SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("heroesevolved","wither_"+name));}
    @SubscribeEvent public static void select(SelectMusicEvent event){
        var mc=Minecraft.getInstance();if(mc.level!=null&&mc.level.dimension().equals(WitherGeometry.DIMENSION))event.setMusic(null);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();
        boolean inside=mc.level!=null&&mc.player!=null&&mc.level.dimension().equals(WitherGeometry.DIMENSION);
        aftermath(mc,inside);
        RiftWitherEntity boss=inside?mc.level.getEntitiesOfClass(RiftWitherEntity.class,new AABB(mc.player.blockPosition()).inflate(130),e->e.fighting()||e.dying()).stream().findFirst().orElse(null):null;
        if(boss==null){if(current!=null){current.fade=true;current=null;}phase=requested=beat=0;if(!inside)ended=false;return;}
        if(boss.dying()) {
            if(current!=null){current.fade=true;current=null;}
            if(!ended&&boss.deathAge(0)>=WitherGeometry.DETONATE){ended=true;afterIn=AFTER_DELAY;mc.getSoundManager().play(SimpleSoundInstance.forMusic(sound("last_chord")));}
            return;
        }
        // A new fight: whatever was left of the last one's aftermath gives way.
        ended=false;afterIn=-1;if(after!=null){after.fade=true;after=null;}
        if(phase==0){mc.getMusicManager().stopPlaying();switchTo(mc,boss.phase());}
        else if(current!=null&&!mc.getSoundManager().isActive(current)
                &&mc.options.getSoundSourceOptionInstance(SoundSource.MUSIC).get()>0)switchTo(mc,phase);
        if(boss.phase()!=phase&&requested!=boss.phase()){
            requested=boss.phase();mc.getSoundManager().play(SimpleSoundInstance.forMusic(sound("stinger")));
        }
        if(++beat%BAR==0&&requested>phase)switchTo(mc,requested);
    }
    /** After it dies: the aftermath fades in as the last chord dies away, and fades out when you leave. */
    private static void aftermath(Minecraft mc,boolean inside){
        if(!inside){afterIn=-1;if(after!=null){after.fade=true;after=null;}return;}
        if(afterIn>0&&--afterIn==0){after=new Stem(sound("aftermath"),.008F);mc.getSoundManager().play(after);}
        else if(after!=null&&!mc.getSoundManager().isActive(after)&&mc.options.getSoundSourceOptionInstance(SoundSource.MUSIC).get()>0){
            after=new Stem(sound("aftermath"),.008F);mc.getSoundManager().play(after);
        }
    }
    private static void switchTo(Minecraft mc,int next){
        if(current!=null)current.fade=true;
        phase=next;requested=next;
        beat=0;
        current=new Stem(sound(next==1?"hunger":next==2?"vessel":"critical"),.04F);mc.getSoundManager().play(current);
    }
    private static final class Stem extends AbstractTickableSoundInstance {
        boolean fade;
        private final float rise;
        Stem(SoundEvent event,float rise){super(event,SoundSource.MUSIC,RandomSource.create());this.rise=rise;looping=true;delay=0;relative=true;attenuation=Attenuation.NONE;volume=.001F;}
        @Override public void tick(){volume=Math.clamp(volume+(fade?-.04F:rise),0,.8F);if(fade&&volume<=0)stop();}
    }
}
