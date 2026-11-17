package com.jayptucker.heroesevolved.client.sound;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.Musics;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.SelectMusicEvent;

@EventBusSubscriber(modid = HeroesEvolved.MOD_ID, value = Dist.CLIENT)
public final class TitleMusic {
    private static final long INTRO_CUE_MS = 12_050;
    private static final long THEME_LEAD_MS = 3_000;
    private static final ResourceLocation TRACK = ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "main_menu");
    private static final Music THEME = new Music(Holder.direct(SoundEvent.createVariableRangeEvent(TRACK)), 0, 0, true);
    private static final SoundEvent INTRO = SoundEvent.createVariableRangeEvent(
            ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "main_menu_intro"));
    private static boolean menuSession;
    private static SoundInstance intro;
    private static long introStarted;

    private TitleMusic() {}

    /** Start the cue with the reveal, then fade the theme in under its tail. */
    public static void beginIntro(Minecraft minecraft) {
        resumeMenu();
        skipIntro(minecraft);
        minecraft.getMusicManager().stopPlaying(THEME);
        if (audible(minecraft)) {
            intro = SimpleSoundInstance.forMusic(INTRO);
            introStarted = Util.getMillis();
            minecraft.getSoundManager().play(intro);
        }
    }

    public static void resumeMenu() {
        menuSession = true;
    }

    public static void skipIntro(Minecraft minecraft) {
        if (intro != null) {
            minecraft.getSoundManager().stop(intro);
            intro = null;
        }
    }

    @SubscribeEvent
    public static void select(SelectMusicEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null || minecraft.screen instanceof ConnectScreen || minecraft.screen instanceof LevelLoadingScreen) {
            menuSession = false;
        }

        boolean audible = audible(minecraft);
        if (!audible || !menuSession || event.getOriginalMusic() != Musics.MENU) skipIntro(minecraft);
        if (intro != null && !minecraft.getSoundManager().isActive(intro)) intro = null;

        if (menuSession && event.getOriginalMusic() == Musics.MENU) {
            // Both tracks carry through Options and world selection without restarting.
            boolean introSolo = intro != null && Util.getMillis() - introStarted < INTRO_CUE_MS - THEME_LEAD_MS;
            event.setMusic(introSolo || !audible || minecraft.getOverlay() != null ? null : THEME);
        } else if (minecraft.screen instanceof TitleScreen) {
            event.setMusic(null);
        } else {
            // Most biomes let existing music finish, so stop the menu track explicitly when leaving the front end.
            minecraft.getMusicManager().stopPlaying(THEME);
        }
    }

    private static boolean audible(Minecraft minecraft) {
        return minecraft.options.getSoundSourceVolume(SoundSource.MUSIC) > 0
                && minecraft.options.getSoundSourceVolume(SoundSource.MASTER) > 0;
    }
}
