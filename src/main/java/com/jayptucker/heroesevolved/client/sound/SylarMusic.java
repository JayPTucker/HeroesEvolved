package com.jayptucker.heroesevolved.client.sound;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.story.sylar.SylarEntity;
import com.jayptucker.heroesevolved.story.sylar.SylarGeometry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.SelectMusicEvent;

/**
 * Sylar's theme, as the game's own music (it follows the music slider and replaces the vanilla track): while a fighter is
 * in the Collection, the loop for his phase plays (Viewing Hours, Unlocked, the Masterwork; tools/generate-sylar-music.py),
 * a stinger marks each phase change, and when he dies the music ends on his last chord and then silence. Only those in
 * the hall hear it.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class SylarMusic {
    private static final Music[] LOOPS = {loop("sylar_viewing"), loop("sylar_unlocked"), loop("sylar_masterwork")};
    private static final SoundEvent STINGER = event("sylar_stinger"), LAST_CHORD = event("sylar_last_chord");
    private static int lastPhase;
    private static boolean ended;

    private SylarMusic() {}

    private static SoundEvent event(String name) {
        return SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, name));
    }

    private static Music loop(String name) { return new Music(Holder.direct(event(name)), 0, 0, true); }

    /** The fighting Sylar in the local player's hall, if they are in it. */
    private static SylarEntity boss(Minecraft mc) {
        if (mc.level == null || mc.player == null || !mc.level.dimension().equals(SylarGeometry.DIMENSION)) return null;
        return mc.level.getEntitiesOfClass(SylarEntity.class, new AABB(mc.player.blockPosition()).inflate(64), SylarEntity::fighting)
                .stream().findFirst().orElse(null);
    }

    @SubscribeEvent public static void select(SelectMusicEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !mc.level.dimension().equals(SylarGeometry.DIMENSION)) return;
        SylarEntity sylar = boss(mc);
        // In his hall the only music is his: silence before he wakes and after his last chord.
        event.setMusic(sylar == null || ended || sylar.is(SylarEntity.DYING) ? null : LOOPS[Math.clamp(sylar.phase(), 1, 3) - 1]);
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        SylarEntity sylar = boss(mc);
        if (sylar == null) {
            if (mc.level == null || !mc.level.dimension().equals(SylarGeometry.DIMENSION)) { lastPhase = 0; ended = false; }
            return;
        }
        int phase = sylar.phase();
        if (lastPhase != 0 && phase > lastPhase) mc.getSoundManager().play(SimpleSoundInstance.forMusic(STINGER));
        lastPhase = phase;
        if (sylar.is(SylarEntity.DYING) && !ended) {
            ended = true;
            mc.getMusicManager().stopPlaying();
            mc.getSoundManager().play(SimpleSoundInstance.forMusic(LAST_CHORD));
        }
    }
}
