package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.time.intro.FractureCity;
import com.jayptucker.heroesevolved.time.intro.IntroBlast;
import com.jayptucker.heroesevolved.time.intro.TimeIntroService;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

import java.nio.file.*;
import java.util.function.Consumer;

/** Plays the whole Time Manipulation awakening and photographs it, beat by beat. */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class TimeIntroPreview {
    private static boolean started, granted, home;
    private static int ticks, worldTicks, step, homeTicks;
    private static String pending, shot;
    private static int shotAt = -1;

    /** When time stops for someone standing in the street where the wave shots are taken. */
    private static final int FROZEN = TimeIntroService.DETONATION_TICK + IntroBlast.arrival(IntroBlast.distance(4.5, 72.5));

    /** Scene tick, then what to do: a position to stand (x, z, yaw, pitch) and/or a photograph. */
    private static final Object[][] STEPS = {
            {40, 4.5, 72.5, 180f, -6f, "1-arrival"},
            // The group just behind the arrival point is talking by now.
            {100, 4.5, 70.5, -15f, -4f, "1b-conversation"},
            {120, -18.5, 70.5, 200f, -4f, "2-side-street"},
            {300, 4.5, 72.5, 180f, -8f, "3-glow-early"},
            {520, 4.5, 72.5, 180f, -9f, "4-glow-bright"},
            // Up on a roof for the light and the cloud ...
            {560, 20.5, 84.5, 174f, -6f, null},
            {620, null, null, null, -6f, "5-glow-roof"},
            {TimeIntroService.DETONATION_TICK + 2, null, null, null, null, "6-flash"},
            {TimeIntroService.DETONATION_TICK + 14, null, null, null, -8f, "7-fireball"},
            {TimeIntroService.DETONATION_TICK + 34, null, null, null, -14f, "8-rising"},
            {TimeIntroService.DETONATION_TICK + 44, 4.5, 96.5, 180f, -2f, null},
            {TimeIntroService.DETONATION_TICK + 50, null, null, null, -2f, "9-cap-aerial"},
            // ... then down in the street for the wave.
            {TimeIntroService.DETONATION_TICK + 56, 4.5, 72.5, 180f, -10f, null},
            {TimeIntroService.DETONATION_TICK + 66, null, null, null, -10f, "10-wave"},
            {TimeIntroService.DETONATION_TICK + 80, null, null, null, -12f, "11-wave-close"},
            // Time stops: the clock of the power opens under them, holds, then runs back. Optional 7th: the camera.
            {FROZEN + 1, null, null, null, 55f, "12-frozen-dial"},
            {FROZEN + 16, null, null, null, 32f, "12b-frozen-behind", CameraType.THIRD_PERSON_BACK},
            {FROZEN + 28, null, null, null, 6f, "12c-frozen-ahead", CameraType.FIRST_PERSON},
            {FROZEN + 40, null, null, null, 18f, "12d-frozen-wave", CameraType.THIRD_PERSON_BACK},
            // The crowd, down the street behind them: held mid-run, running back, and home before the sirens.
            {FROZEN + 46, null, null, null, 4f, "12e-crowd-frozen", CameraType.THIRD_PERSON_FRONT},
            {FROZEN + IntroBlast.FREEZE_TICKS + 9, null, null, null, 30f, "13-rewinding", CameraType.THIRD_PERSON_BACK},
            {FROZEN + IntroBlast.FREEZE_TICKS + 21, null, null, null, 62f, "13b-rewinding-dial", CameraType.FIRST_PERSON},
            {FROZEN + IntroBlast.FREEZE_TICKS + 28, null, null, null, 22f, "13c-rewinding-front", CameraType.THIRD_PERSON_FRONT},
            {FROZEN + IntroBlast.FREEZE_TICKS + 33, null, null, null, 4f, "13e-crowd-running-back", CameraType.THIRD_PERSON_FRONT},
            {FROZEN + IntroBlast.FREEZE_TICKS + 38, null, null, null, 26f, "13d-closing", CameraType.THIRD_PERSON_BACK},
            {FROZEN + IntroBlast.FREEZE_TICKS + 41, null, null, null, 4f, "13f-crowd-home", CameraType.THIRD_PERSON_FRONT},
            // Time holds a few seconds more after the rewind, everyone back where they stood.
            {FROZEN + IntroBlast.FREEZE_TICKS + IntroBlast.REWIND_TICKS + 30, null, null, null, 4f, "13g-held", CameraType.THIRD_PERSON_FRONT},
    };

    private static void onServer(Minecraft mc, Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayer(mc.player.getUUID());
            if (p != null) action.accept(p);
        });
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        if (!Boolean.getBoolean("heroes.preview.timeintro")) return;
        var mc = Minecraft.getInstance();
        ticks++;
        if (!started && ticks > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.options.hideGui = false; // captions and the instruction line are part of what is checked
            mc.options.pauseOnLostFocus = false;
            mc.options.renderDistance().set(12);
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("Intro-" + System.currentTimeMillis(),
                    new LevelSettings("Intro preview", GameType.SURVIVAL, false, Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(5L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        var server = mc.getSingleplayerServer();
        worldTicks++;
        var city = server.getLevel(TimeIntroService.DIMENSION);
        // After the stasis awakening has let the player go and the city stands, the power wakes.
        if (!granted && worldTicks > 240 && city != null && FractureCity.get(city).ready()) {
            granted = true;
            System.out.println("INTRO_GRANT at " + worldTicks);
            onServer(mc, p -> PlayerAbilityService.grantAbility(p, ModAbilities.TIME_MANIPULATION_ID));
        }
        if (!granted) return;
        int scene = TimeIntroService.sceneTicks();
        if (shot != null && scene >= shotAt) { pending = shot; shot = null; }
        if (scene < 0 && step > 0) {
            if (!home) { home = true; homeTicks = 0; mc.options.setCameraType(CameraType.FIRST_PERSON); System.out.println("INTRO_HOME"); }
            if (++homeTicks == 30) pending = "intro-14-home.png";
            if (homeTicks > 50) mc.stop();
            return;
        }
        if (step >= STEPS.length || scene < 0) return;
        Object[] s = STEPS[step];
        if (scene < (int) s[0]) return;
        step++;
        if (s[1] != null) {
            double x = (double) s[1], z = (double) s[2];
            float yaw = (float) s[3], pitch = (float) s[4];
            onServer(mc, p -> {
                boolean air = z == 96.5;
                p.getAbilities().mayfly = air; p.getAbilities().flying = air; p.onUpdateAbilities();
                p.teleportTo(p.serverLevel(), x, air ? 175 : x == 20.5 ? 130 : 64, z, yaw, pitch);
            });
        } else if (s[4] != null) {
            float pitch = (float) s[4];
            onServer(mc, p -> p.teleportTo(p.serverLevel(), p.getX(), p.getY(), p.getZ(), p.getYRot(), pitch));
        }
        if (s.length > 6) mc.options.setCameraType((CameraType) s[6]);
        if (s[5] != null) { shotAt = scene + 3; shot = "intro-" + s[5] + ".png"; }
        System.out.println("INTRO_STEP " + step + " at scene " + scene);
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post e) {
        if (pending == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(out.getParent()); image.writeToFile(out);
            System.out.println("INTRO_CAPTURE " + pending + " fps=" + mc.getFps() + " " + mc.getSoundManager().getDebugString());
        } catch (Exception ex) { throw new RuntimeException(ex); }
        pending = null;
    }
}
