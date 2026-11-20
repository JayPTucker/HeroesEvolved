package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.client.effects.TempestCamera;
import com.jayptucker.heroesevolved.network.PlayerPowerSyncService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.story.bunker.CryoChamberService;
import com.jayptucker.heroesevolved.story.prologue.PrologueService;
import com.jayptucker.heroesevolved.story.sylar.CollectionArena;
import com.jayptucker.heroesevolved.story.sylar.LevelFive;
import com.jayptucker.heroesevolved.story.sylar.SylarData;
import com.jayptucker.heroesevolved.story.sylar.SylarStory;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * How you meet Sylar, photographed: the hatch in Gray's old cell, Level 5's corridor, the prisoner behind his glass and
 * what he says, the watch list on the wall, the release lever, the glass falling out when it is pulled, and, his cell
 * open, him standing behind you at dusk. Run with {@code ./gradlew runClient --init-script tools/level5-preview.init.gradle};
 * shots land in {@code build/level5-preview/screenshots/}.
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class LevelFivePreview {
    private static final int PHOTOS = 11;
    private static boolean started;
    private static int ticks, age, photos;
    private static String pending;

    private static boolean on() { return Boolean.getBoolean("heroes.preview.level5"); }

    private static void server(Minecraft mc, Consumer<ServerPlayer> action) {
        var id = mc.player.getUUID();
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            try { action.accept(server.getPlayerList().getPlayer(id)); }
            catch (RuntimeException error) { PreviewQuiet.report("LEVEL5_PREVIEW FAIL step at " + age + ": " + error); error.printStackTrace(); }
        });
    }

    private static void camera(Minecraft mc, Vec3 at, Vec3 look) { mc.execute(() -> TempestCamera.fix(at, look)); }

    private static void free(Minecraft mc, CameraType type) {
        mc.execute(() -> TempestCamera.fix(null, null));
        mc.options.setCameraType(type);
    }

    private static void shot(String name) { pending = "level5-" + name + ".png"; }

    private static void move(ServerPlayer p, ServerLevel level, Vec3 at, float yaw) {
        p.teleportTo(level, at.x, at.y, at.z, yaw, 0);
        p.setDeltaMovement(Vec3.ZERO);
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        if (!on()) return;
        var mc = Minecraft.getInstance();
        ticks++;
        if (!started && ticks > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.options.pauseOnLostFocus = false;
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("Level5-" + System.currentTimeMillis(),
                    new LevelSettings("Level 5 preview", GameType.SURVIVAL, false, Difficulty.NORMAL, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(77L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.getSingleplayerServer() == null || mc.getOverlay() != null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        if (age >= 100) mc.getToasts().clear();
        ++age;
        if (age == 104) { server(mc, PrologueService::skip); return; }
        if (age == 110) {
            server(mc, p -> {
                CryoChamberService.release(p);
                p.setGameMode(GameType.SURVIVAL);
                PlayerAbilityService.grantAbility(p, ModAbilities.QUAKE_ID);
                PlayerProgressionService.setPowerLevel(p, 10);
                PlayerPowerSyncService.sync(p);
                p.removeAllEffects();
                p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 600, 4, false, false));
                p.getServer().overworld().setWeatherParameters(24000, 0, false, false);
                SylarStory.wake(p);
                BlockPos hatch = SylarData.get(p.getServer()).hatch();
                System.out.println("LEVEL5_PREVIEW hatch at " + hatch + " (" + p.serverLevel().getBlockState(hatch).getBlock() + ")");
                // Stand beside the hatch, in Gray's old cell.
                move(p, p.getServer().overworld(), Vec3.atBottomCenterOf(hatch.above()).add(1, 0, -1.5), 0);
            });
            return;
        }
        if (age < 160) return;
        if (age % 20 == 0) com.jayptucker.heroesevolved.network.ClientScreenTintState.clear();
        script(mc, age - 160);
    }

    private static void script(Minecraft mc, int t) {
        switch (t) {
            // ---- The way down ----
            case 0 -> server(mc, p -> {
                p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 60, 0, false, false));
                BlockPos hatch = SylarData.get(p.getServer()).hatch();
                Vec3 h = Vec3.atBottomCenterOf(hatch);
                camera(mc, h.add(1.0, 1.9, -1.5), h.add(0, 1.0, 0.2));
            });
            case 20 -> { mc.options.hideGui = true; shot("01-hatch"); }
            case 30 -> { free(mc, CameraType.FIRST_PERSON); server(mc, p -> { p.removeEffect(MobEffects.NIGHT_VISION); LevelFive.descend(p); }); }
            case 90 -> { mc.options.hideGui = false; shot("02-arrival"); }
            case 100 -> { mc.options.hideGui = true; camera(mc, Vec3.atBottomCenterOf(LevelFive.ORIGIN).add(0, 2.8, -8), LevelFive.glassMiddle()); }
            case 110 -> shot("03-corridor");
            // ---- The prisoner ----
            case 116 -> server(mc, p -> {
                Vec3 g = LevelFive.glassMiddle();
                move(p, (ServerLevel) p.level(), new Vec3(g.x - 0.6, LevelFive.arrival().y, g.z + 2.6), 180);
            });
            case 120 -> camera(mc, LevelFive.glassMiddle().add(-0.6, 0.5, 4.6), LevelFive.cell().add(0, 1.3, 0));
            case 150 -> { mc.options.hideGui = false; shot("04-his-cell"); }
            case 160 -> { mc.options.hideGui = true; camera(mc, LevelFive.glassMiddle().add(0.4, 0.3, 1.4), LevelFive.cell().add(0, 1.6, 0)); }
            case 170 -> shot("05-behind-glass");
            case 180 -> camera(mc, LevelFive.listMiddle().add(2.3, 0.1, 0.2), LevelFive.listMiddle());
            case 190 -> shot("06-watch-list");
            case 410 -> { mc.options.hideGui = false; camera(mc, LevelFive.glassMiddle().add(-0.6, 0.5, 4.6), LevelFive.cell().add(0, 1.3, 0)); }
            case 416 -> shot("07-his-hint");
            case 420 -> { mc.options.hideGui = true; camera(mc, Vec3.atCenterOf(LevelFive.at("lever")).add(-2.2, 0.4, 0.9), Vec3.atCenterOf(LevelFive.at("lever")).add(0, 0.4, 0)); }
            case 430 -> shot("08-release-lever");
            // ---- The lever ----
            case 440 -> {
                camera(mc, LevelFive.glassMiddle().add(1.6, 0.4, 4.2), LevelFive.cell().add(0, 1.2, 0));
                server(mc, SylarStory::release);
            }
            case 452 -> { mc.options.hideGui = false; shot("09-released"); }
            case 520 -> server(mc, p -> CollectionArena.end(p.getServer(), CollectionArena.Outcome.ABANDONED));
            // ---- Out of his cell: behind you ----
            case 570 -> { free(mc, CameraType.FIRST_PERSON); server(mc, LevelFive::ascend); }
            case 580 -> server(mc, p -> {
                ServerLevel world = p.getServer().overworld();
                world.setDayTime(12_600);
                world.setChunkForced(18, 0, true);
                SylarData.get(p.getServer()).lastNight(1_000_000);
                move(p, world, new Vec3(300.5, -60, 0.5), 0);
            });
            case 600 -> server(mc, p -> SylarStory.comeFor(p, 0));
            case 604 -> {
                // Third person, so the player is drawn: they face the camera, and he is over their shoulder.
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                server(mc, p -> {
                    Vec3 at = p.position();
                    camera(mc, at.add(1.7, 1.9, 3.2), at.add(-0.3, 1.3, -1.2));
                });
            }
            case 628 -> { mc.options.hideGui = false; shot("10-behind-you"); }
            case 700 -> free(mc, CameraType.THIRD_PERSON_BACK);
            case 760 -> shot("11-taken");
            case 780 -> server(mc, p -> CollectionArena.end(p.getServer(), CollectionArena.Outcome.ABANDONED));
            case 840 -> {
                System.out.println("LEVEL5_PREVIEW_RESULT " + (photos >= PHOTOS ? "PASS" : "FAIL") + ": " + photos + " photos");
                if (photos < PHOTOS) PreviewQuiet.report("LEVEL5_PREVIEW FAIL only " + photos + " photos");
                PreviewQuiet.quit();
            }
            default -> { }
        }
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post e) {
        if (pending == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(out.getParent());
            image.writeToFile(out);
            photos++;
            System.out.println("LEVEL5_CAPTURE " + pending);
        } catch (Exception ex) { PreviewQuiet.report("LEVEL5_PREVIEW FAIL capture " + pending + ": " + ex); }
        pending = null;
    }
}
