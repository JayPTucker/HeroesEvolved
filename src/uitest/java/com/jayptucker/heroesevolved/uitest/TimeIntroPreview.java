package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.time.intro.FractureCity;
import com.jayptucker.heroesevolved.time.intro.IntroBlast;
import com.jayptucker.heroesevolved.time.intro.TimeIntroService;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.phys.Vec3;
import com.jayptucker.heroesevolved.time.intro.SkyFight;
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

    /** When time stops for someone standing at the vantage, where the wave shots are taken. */
    private static final int FROZEN = TimeIntroService.DETONATION_TICK + IntroBlast.arrival(IntroBlast.distance(4.5, -14.5));

    private static final SkyFight.Script FIGHT = SkyFight.VISION;
    private static final int CLASH_1 = FIGHT.clashAt(1), CLASH_2 = FIGHT.clashAt(2), IMPACT = FIGHT.impactAt(), LANDED = FIGHT.landAt();

    /** Scene tick, then what to do: a position to stand (x, z, yaw, pitch) and/or a photograph. */
    private static final Object[][] STEPS = {
            {40, 4.5, -14.5, 180f, -32f, "1-arrival-sky-fight"},
            // Peter and Sylar over the plaza: a clash Sylar takes (his grip), one Peter takes (lightning), the throw.
            {CLASH_1, null, null, null, -32f, "1b-sylar-grips-peter"},
            {CLASH_2, null, null, null, -32f, "1c-peter-strikes"},
            {IMPACT - 2, 4.5, -14.5, 166f, -38f, "2-sylar-into-the-tower"},
            {IMPACT + 18, null, null, null, -38f, "2b-crater"},
            {LANDED - 2, 4.5, -20.5, 180f, -4f, "2c-peter-lands-by-ted"},
            {300, 4.5, -20.5, 180f, -4f, "3-glow-early"},
            {440, 4.5, -20.5, 180f, -4f, "3b-peter-holds-ted", CameraType.FIRST_PERSON},
            {520, 4.5, -14.5, 180f, -6f, "4-glow-bright"},
            {560, null, null, null, -4f, "5-sirens-crowd", CameraType.THIRD_PERSON_FRONT},
            // Close in on ground zero (a preview camera): the traveler's double folds in beside Peter, and the two are gone.
            {584, null, null, null, -4f, null, CameraType.FIRST_PERSON, new Vec3[] {new Vec3(9.5, 67.2, -69.5), new Vec3(4.8, 65.4, -76.5)}},
            {590, null, null, null, null, "5b-traveler-arrives"},
            {607, null, null, null, null, "5b2-who-are-you"},
            {614, null, null, null, null, "5c-traveler-takes-hold"},
            {619, null, null, null, null, "5d-gone"},
            {626, null, null, null, null, null, null, "clear"},
            {TimeIntroService.DETONATION_TICK - 2, null, null, null, -4f, null, CameraType.FIRST_PERSON},
            {TimeIntroService.DETONATION_TICK + 2, null, null, null, null, "6-flash"},
            {TimeIntroService.DETONATION_TICK + 8, null, null, null, -8f, "7-fireball"},
            // Time stops: the clock of the power opens under them, holds, then runs back.
            {FROZEN + 1, null, null, null, 55f, "12-frozen-dial"},
            {FROZEN + 16, null, null, null, 20f, "12b-frozen-behind", CameraType.THIRD_PERSON_BACK},
            {FROZEN + 28, null, null, null, -4f, "12c-frozen-ahead", CameraType.FIRST_PERSON},
            {FROZEN + 46, null, null, null, 4f, "12e-crowd-frozen", CameraType.THIRD_PERSON_FRONT},
            {FROZEN + IntroBlast.FREEZE_TICKS + 9, null, null, null, 20f, "13-rewinding", CameraType.THIRD_PERSON_BACK},
            {FROZEN + IntroBlast.FREEZE_TICKS + 21, null, null, null, 62f, "13b-rewinding-dial", CameraType.FIRST_PERSON},
            {FROZEN + IntroBlast.FREEZE_TICKS + 33, null, null, null, 4f, "13e-crowd-running-back", CameraType.THIRD_PERSON_FRONT},
            // Time holds a few seconds more after the rewind: everyone back where they stood, Peter and Ted again.
            {FROZEN + IntroBlast.FREEZE_TICKS + IntroBlast.REWIND_TICKS + 30, null, null, null, -4f, "13g-held", CameraType.FIRST_PERSON},
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
        // A new world starts players on the Last Day; this preview is about the vision, so wake straight in the bunker.
        if (worldTicks == 5) onServer(mc, p -> com.jayptucker.heroesevolved.story.prologue.PrologueService.skip(p));
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
                p.teleportTo(p.serverLevel(), x, 64, z, yaw, pitch);
            });
        } else if (s[4] != null) {
            float pitch = (float) s[4];
            onServer(mc, p -> p.teleportTo(p.serverLevel(), p.getX(), p.getY(), p.getZ(), p.getYRot(), pitch));
        }
        if (s.length > 6 && s[6] != null) mc.options.setCameraType((CameraType) s[6]);
        if (s.length > 7) {
            if (s[7] instanceof Vec3[] v) com.jayptucker.heroesevolved.client.effects.TempestCamera.fix(v[0], v[1]);
            else com.jayptucker.heroesevolved.client.effects.TempestCamera.fix(null, null);
        }
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
