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

/** Three original 96 BPM loops. Phase changes crossfade at the next 50-tick bar; retreat fades the score. */
@EventBusSubscriber(modid=HeroesEvolved.MOD_ID,value=Dist.CLIENT)
public final class WitherMusic {
    private static Stem current;
    private static int phase,requested,beat;
    private static boolean ended;
    private WitherMusic(){}
    private static SoundEvent sound(String name){return SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("heroesevolved","wither_"+name));}
    @SubscribeEvent public static void select(SelectMusicEvent event){
        var mc=Minecraft.getInstance();if(mc.level!=null&&mc.level.dimension().equals(WitherGeometry.DIMENSION))event.setMusic(null);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();
        boolean inside=mc.level!=null&&mc.player!=null&&mc.level.dimension().equals(WitherGeometry.DIMENSION);
        RiftWitherEntity boss=inside?mc.level.getEntitiesOfClass(RiftWitherEntity.class,new AABB(mc.player.blockPosition()).inflate(130),e->e.fighting()||e.dying()).stream().findFirst().orElse(null):null;
        if(boss==null){if(current!=null){current.fade=true;current=null;}phase=requested=beat=0;if(!inside)ended=false;return;}
        if(boss.dying()) {
            if(!ended){ended=true;if(current!=null){current.fade=true;current=null;}mc.getSoundManager().play(SimpleSoundInstance.forMusic(sound("last_chord")));}
            return;
        }
        ended=false;
        if(phase==0){mc.getMusicManager().stopPlaying();switchTo(mc,boss.phase());}
        else if(current!=null&&!mc.getSoundManager().isActive(current)
                &&mc.options.getSoundSourceOptionInstance(SoundSource.MUSIC).get()>0)switchTo(mc,phase);
        if(boss.phase()!=phase&&requested!=boss.phase()){
            requested=boss.phase();mc.getSoundManager().play(SimpleSoundInstance.forMusic(sound("stinger")));
        }
        if(++beat%50==0&&requested>phase)switchTo(mc,requested);
    }
    private static void switchTo(Minecraft mc,int next){
        if(current!=null)current.fade=true;
        phase=next;requested=next;
        beat=0;
        current=new Stem(sound(next==1?"hunger":next==2?"vessel":"critical"));mc.getSoundManager().play(current);
    }
    private static final class Stem extends AbstractTickableSoundInstance {
        boolean fade;
        Stem(SoundEvent event){super(event,SoundSource.MUSIC,RandomSource.create());looping=true;delay=0;relative=true;attenuation=Attenuation.NONE;volume=.001F;}
        @Override public void tick(){volume=Math.clamp(volume+(fade?-.04F:.04F),0,.8F);if(fade&&volume<=0)stop();}
    }
}
