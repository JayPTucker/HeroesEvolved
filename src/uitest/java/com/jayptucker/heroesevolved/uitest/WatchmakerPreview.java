package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.story.sylar.SylarEntity;
import com.jayptucker.heroesevolved.story.sylar.SylarStoryData;
import com.jayptucker.heroesevolved.story.sylar.SylarStoryService;
import com.jayptucker.heroesevolved.story.sylar.WatchmakerArena;
import com.jayptucker.heroesevolved.story.sylar.WatchmakerEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

import java.nio.file.*;
import java.util.function.Consumer;

/**
 * The Watchmaker, phase by phase: a real hunt starts 300 blocks from spawn, he takes the player into his clock shop, and
 * each move is photographed at its telegraph and its landing. Run with
 * {@code ./gradlew runClient --init-script tools/watchmaker-preview.init.gradle}; shots land in
 * {@code build/watchmaker-preview/screenshots/}.
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class WatchmakerPreview {
    private static boolean started;
    private static int ticks, worldTicks;
    private static String pending;

    private static void onServer(Minecraft mc, Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> action.accept(server.getPlayerList().getPlayer(mc.player.getUUID())));
    }

    private static void sylar(Minecraft mc, Consumer<SylarEntity> action) {
        onServer(mc, p -> { SylarEntity s = SylarStoryService.hunter(); if (s != null) action.accept(s); });
    }

    /** Puts the player somewhere in the shop (offset from the clock face's middle), flying when off the floor. */
    private static void stand(ServerPlayer p, double dx, double dy, double dz, float yaw, float pitch) {
        Vec3 at = WatchmakerArena.center().add(dx, dy, dz);
        p.getAbilities().mayfly = dy > 0.5; p.getAbilities().flying = dy > 0.5; p.onUpdateAbilities();
        p.teleportTo((ServerLevel) p.level(), at.x, at.y, at.z, yaw, pitch);
        p.setDeltaMovement(Vec3.ZERO);
    }

    /** Puts him on his mark (offset from the middle), facing the player, and keeps him from choosing moves of his own. */
    private static void mark(SylarEntity s, double dx, double dz) {
        Vec3 at = WatchmakerArena.center().add(dx, 0, dz);
        s.teleportTo(at.x, at.y, at.z);
        s.holdOff(200);
    }

    private static void shot(String name) { pending = "watchmaker-" + name + ".png"; }

    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        if (!Boolean.getBoolean("heroes.preview.watchmaker")) return;
        var mc = Minecraft.getInstance();
        ticks++;
        if (!started && ticks > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.options.pauseOnLostFocus = false;
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("Watchmaker-" + System.currentTimeMillis(),
                    new LevelSettings("Watchmaker preview", GameType.SURVIVAL, false, Difficulty.NORMAL, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(77L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        switch (++worldTicks) {
            case 200 -> {
                mc.options.hideGui = true;
                onServer(mc, p -> {
                    p.serverLevel().setDayTime(13000);
                    p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty()
                            .assignDormant(ModAbilities.ELECTROKINESIS_ID).unlock(ModAbilities.ELECTROKINESIS_ID));
                    p.setData(ModDataAttachments.POWER_INTRO_HISTORY, p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(ModAbilities.ELECTROKINESIS_ID));
                    PlayerProgressionService.setLevel(p, 5);
                    p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 300, 4, false, false));
                    p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 300, 3, false, false));
                    var story = SylarStoryData.get(p.getServer());
                    for (var id : java.util.List.of(ModAbilities.CRYOKINESIS_ID, ModAbilities.TIME_MANIPULATION_ID)) story.learn(id);
                    // 300 blocks from spawn, well clear of the bunker.
                    p.teleportTo(p.serverLevel(), 300.5, -60, 0.5, 0, 2);
                });
            }
            case 215 -> onServer(mc, p -> {
                if (!SylarStoryService.start(p, true)) { System.out.println("WATCHMAKER_PREVIEW no sightline"); return; }
                SylarStoryService.huntNow(p);
                System.out.println("WATCHMAKER_PREVIEW in shop=" + WatchmakerArena.inShop(p));
            });
            // The shop, from up by the ceiling: the clock face in the floor, the gear rails, the great clock.
            case 235 -> { sylar(mc, s -> mark(s, 0, -7)); onServer(mc, p -> stand(p, 0, 6.5, 12.5, 180, 32)); }
            case 250 -> shot("1-shop");
            // ---- Taking Inventory ----
            case 260 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                onServer(mc, p -> stand(p, -3, 0, -4, 200, 12));
            }
            case 266 -> sylar(mc, s -> { mark(s, 3, -6); s.castNow("rail_arc"); });
            case 280 -> shot("2-inventory-rail-windup");
            case 291 -> shot("2-inventory-rail-arc");
            case 305 -> onServer(mc, p -> stand(p, 5, 0, 4, 180, 10));
            case 310 -> sylar(mc, s -> { mark(s, 5, -5); s.castNow("floor_slam"); });
            case 326 -> shot("2-inventory-slam-windup");
            case 342 -> shot("2-inventory-slam");
            case 360 -> onServer(mc, p -> stand(p, -4, 0, 2, 160, 10));
            case 364 -> sylar(mc, s -> { mark(s, -4, -6); s.castNow("ice_wall"); });
            case 382 -> shot("2-inventory-ice-windup");
            case 400 -> shot("2-inventory-ice-wall");
            // ---- The Hunger (once the ice has melted) ----
            case 570 -> {
                onServer(mc, p -> stand(p, 0, 0, 7, 0, 10));
                sylar(mc, s -> { mark(s, 0, -2); s.setHealth(s.getMaxHealth() * 0.55F); });
            }
            case 578 -> shot("3-hunger-doors-lock");
            case 592 -> onServer(mc, p -> stand(p, -4, 0, -2, 180, 8));
            case 596 -> sylar(mc, s -> { mark(s, -4, -7); s.castNow("grab"); });
            case 604 -> shot("3-hunger-grab-windup");
            case 618 -> shot("3-hunger-held");
            case 626 -> sylar(mc, s -> s.struggle(6));
            case 630 -> shot("3-hunger-torn-free");
            // ---- The counterplay: crack his watch while it shows ----
            case 660 -> onServer(mc, p -> stand(p, 1.2, 0, -1.2, 150, 18));
            case 664 -> sylar(mc, s -> { mark(s, 0, -3.5); s.castNow("floor_slam"); });
            case 672 -> shot("4-watch-exposed");
            case 674 -> onServer(mc, p -> {
                SylarEntity s = SylarStoryService.hunter();
                if (s == null) return;
                WatchmakerEvents.swing(p);
                s.invulnerableTime = 0;
                s.hurt(p.damageSources().playerAttack(p), 3);
            });
            case 677 -> shot("4-watch-cracks");
            case 690 -> shot("4-staggered");
            // ---- Clockwork: from up under the ceiling, the whole clock face ----
            case 720 -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                onServer(mc, p -> stand(p, 0, 6.3, 12.5, 180, 38));
                sylar(mc, s -> { mark(s, 0, -2); s.setHealth(s.getMaxHealth() * 0.25F); });
            }
            case 730 -> sylar(mc, s -> { mark(s, 0, -1); s.castNow("time_rings"); });
            case 756 -> shot("5-clockwork-gap");
            case 774 -> shot("5-clockwork-rings");
            case 784 -> shot("5-clockwork-rings-close");
            // ---- The chase ----
            case 800 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                onServer(mc, p -> stand(p, -4, 0, -4, 135, 6));
                sylar(mc, s -> { mark(s, -5, -8); s.setHealth(s.getMaxHealth() * 0.08F); });
            }
            case 812 -> shot("6-chase-back-door");
            case 824 -> onServer(mc, p -> stand(p, -10, 0, -12, 180, 4));
            case 836 -> shot("6-chase-alley");
            // ---- What he leaves ----
            case 844 -> onServer(mc, p -> {
                SylarEntity s = SylarStoryService.hunter();
                if (s != null) s.hurt(p.damageSources().playerAttack(p), 1000);
            });
            case 854 -> shot("7-watch-drop");
            case 872 -> shot("7-watch-caught");
            case 980 -> shot("8-home");
            case 990 -> mc.stop();
            default -> { }
        }
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post e) {
        if (pending == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(out.getParent()); image.writeToFile(out);
            System.out.println("WATCHMAKER_CAPTURE " + pending);
        } catch (Exception ex) { throw new RuntimeException(ex); }
        pending = null;
    }
}
