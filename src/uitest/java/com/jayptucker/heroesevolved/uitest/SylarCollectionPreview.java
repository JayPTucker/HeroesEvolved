package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.client.effects.TempestCamera;
import com.jayptucker.heroesevolved.network.PlayerPowerSyncService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.registry.ModItems;
import com.jayptucker.heroesevolved.story.bunker.CryoChamberService;
import com.jayptucker.heroesevolved.story.prologue.PrologueService;
import com.jayptucker.heroesevolved.story.sylar.CollectionArena;
import com.jayptucker.heroesevolved.story.sylar.SylarEntity;
import com.jayptucker.heroesevolved.story.sylar.SylarEvents;
import com.jayptucker.heroesevolved.story.sylar.SylarGeometry;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
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
 * Sylar's Collection, move by move: a real Repair Ticket opens the hall 300 blocks from spawn, and every telegraph is
 * photographed while its schematic assembles and again as it lands, then each phase change, the backfire, Appraisal, the
 * pillars and the great movement, Every Hour, the light, Claire's case, his death and the drop. Run with
 * {@code ./gradlew runClient --init-script tools/sylar-preview.init.gradle}; shots land in
 * {@code build/sylar-preview/screenshots/}.
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class SylarCollectionPreview {
    private static boolean started;
    private static int ticks, age, photos;
    private static String pending;

    private static boolean on() { return Boolean.getBoolean("heroes.preview.sylar"); }

    private static void server(Minecraft mc, Consumer<ServerPlayer> action) {
        var id = mc.player.getUUID();
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            try { action.accept(server.getPlayerList().getPlayer(id)); }
            catch (RuntimeException error) { PreviewQuiet.report("SYLAR_PREVIEW FAIL step at " + age + ": " + error); error.printStackTrace(); }
        });
    }

    private static void sylar(Minecraft mc, Consumer<SylarEntity> action) {
        server(mc, p -> {
            SylarEntity s = CollectionArena.sylar();
            if (s == null) { PreviewQuiet.report("SYLAR_PREVIEW FAIL no Sylar at " + age); return; }
            action.accept(s);
        });
    }

    /** Stands the player on the dial plate at clock angle {@code hours} and radius {@code r}, facing the dais. */
    private static void stand(ServerPlayer p, double hours, double r) {
        double g = hours * Math.PI / 6;
        Vec3 at = SylarGeometry.center().add(SylarGeometry.dx(g, r), 0, SylarGeometry.dz(g, r));
        Vec3 c = SylarGeometry.center();
        float yaw = (float) Math.toDegrees(Math.atan2(-(c.x - at.x), c.z - at.z));
        p.teleportTo((ServerLevel) p.level(), at.x, at.y, at.z, yaw, 8);
        p.setDeltaMovement(Vec3.ZERO);
    }

    /** A still camera at an offset from the hall's middle, looking at another. */
    private static void camera(Minecraft mc, double x, double y, double z, double lx, double ly, double lz) {
        mc.getSingleplayerServer().execute(() -> {
            Vec3 c = SylarGeometry.center();
            Vec3 at = c.add(x, y, z), look = c.add(lx, ly, lz);
            mc.execute(() -> TempestCamera.fix(at, look));
        });
    }

    private static void free(Minecraft mc, CameraType type) {
        mc.execute(() -> TempestCamera.fix(null, null));
        mc.options.setCameraType(type);
    }

    private static void shot(String name) { pending = "sylar-" + name + ".png"; }

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
            mc.createWorldOpenFlows().createFreshLevel("Sylar-" + System.currentTimeMillis(),
                    new LevelSettings("Sylar preview", GameType.SURVIVAL, false, Difficulty.NORMAL, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(77L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.getSingleplayerServer() == null || mc.getOverlay() != null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        if (age >= 100) mc.getToasts().clear();
        ++age;
        if (age == 104) { server(mc, PrologueService::skip); return; }
        if (age == 110) {
            mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
            com.jayptucker.heroesevolved.network.ClientScreenTintState.clear();
            server(mc, p -> {
                CryoChamberService.release(p);
                p.setGameMode(GameType.SURVIVAL);
                PlayerAbilityService.grantAbility(p, ModAbilities.QUAKE_ID);
                PlayerProgressionService.setPowerLevel(p, 10);
                PlayerPowerSyncService.sync(p);
                p.removeAllEffects();
                p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 600, 4, false, false));
                p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 600, 0, false, false));
                ServerLevel world = p.getServer().overworld();
                world.setChunkForced(18, 0, true);
                p.teleportTo(world, 300.5, -60, 0.5, 0, 0);
                com.jayptucker.heroesevolved.story.sylar.RepairTicketItem.give(p);
                System.out.println("SYLAR_PREVIEW staged in " + p.level().dimension().location() + " on "
                        + world.getBlockState(p.blockPosition().below()).getBlock());
            });
            return;
        }
        if (age < 160) return;
        if (age % 20 == 0) com.jayptucker.heroesevolved.network.ClientScreenTintState.clear();
        script(mc, age - 160);
    }

    private static void script(Minecraft mc, int t) {
        switch (t) {
            // ---- The ticket ----
            case 0 -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                server(mc, p -> { p.getInventory().selected = 0; p.getInventory().setItem(0, new ItemStack(ModItems.REPAIR_TICKET.get())); });
            }
            case 10 -> shot("01-ticket");
            case 14 -> server(mc, p -> {
                SylarEntity s = CollectionArena.open(p);
                if (s == null) { PreviewQuiet.report("SYLAR_PREVIEW FAIL the Collection didn't open"); return; }
                s.still(true); s.holdOff(100_000);
                System.out.println("SYLAR_PREVIEW in the Collection=" + CollectionArena.inCollection(p) + " cases=" + Integer.toBinaryString(s.cases()));
            });
            // ---- The hall: from high over the south walk, and at eye height as the party arrives ----
            case 80 -> { mc.options.hideGui = true; camera(mc, 0, 13, 20, 0, 0, -2); }
            case 90 -> shot("02-hall");
            case 96 -> { free(mc, CameraType.FIRST_PERSON); server(mc, p -> stand(p, 6, 11)); }
            case 104 -> shot("03-arrival");
            case 108 -> camera(mc, 12.5, 2.4, -3.5, 14, 1.4, -8);
            case 116 -> shot("04-case-frost");
            // ---- Viewing Hours ----
            case 120 -> { free(mc, CameraType.THIRD_PERSON_BACK); server(mc, p -> stand(p, 6, 10)); }
            case 126 -> sylar(mc, s -> s.castNow("hurl"));
            case 138 -> shot("05-hurl-windup");
            case 151 -> shot("06-hurl-lands");
            case 160 -> { server(mc, p -> stand(p, 4, 9)); camera(mc, -6, 14, 14, 2, 0, 0); }
            case 166 -> sylar(mc, s -> s.castNow("lattice"));
            case 182 -> shot("07-lattice-windup");
            case 191 -> shot("08-lattice");
            case 200 -> { free(mc, CameraType.THIRD_PERSON_BACK); server(mc, p -> stand(p, 3, 9)); }
            case 206 -> sylar(mc, s -> s.castNow("fault"));
            case 220 -> shot("09-fault-windup");
            case 228 -> shot("10-fault");
            case 240 -> camera(mc, 0, 12, 16, 0, 0, 0);
            case 244 -> sylar(mc, s -> s.castNow("resonance"));
            case 258 -> shot("11-resonance-windup");
            case 276 -> shot("12-resonance-rolls");
            // The case he draws on is outlined in gilt, its thread running to his hand; then it is broken.
            case 300 -> { server(mc, p -> stand(p, 2, 10)); camera(mc, 6, 4, -2, 13, 2, -8); }
            case 306 -> sylar(mc, s -> s.castNow("icefall"));
            case 322 -> shot("13-drawing-frost-case");
            case 326 -> sylar(mc, s -> s.breakCase(2));
            case 330 -> shot("14-backfire");
            case 344 -> shot("15-staggered");
            // ---- Unlocked: the lights go out, the hour marks glow red ----
            // Just above two thirds he glistens: a finisher now would force the next phase.
            case 356 -> {
                free(mc, CameraType.THIRD_PERSON_BACK);
                server(mc, p -> stand(p, 6, 5));
                sylar(mc, s -> s.setHealth(s.getMaxHealth() * 0.7F));
                camera(mc, 3.5, 2.2, 3.5, 0, 1.2, 0);
            }
            case 366 -> shot("16a-finish-glisten");
            case 380 -> { camera(mc, 0, 13, 20, 0, 0, -2); sylar(mc, s -> s.setHealth(s.getMaxHealth() * 0.6F)); }
            case 386 -> shot("16-unlocked-shove");
            case 420 -> shot("17-unlocked-dark");
            // An echo has stepped out of a case; its case stands open, edged in gilt.
            case 424 -> sylar(mc, s -> {
                if (s.echoes().isEmpty()) { PreviewQuiet.report("SYLAR_PREVIEW FAIL no echo in phase two"); return; }
                var echo = s.echoes().getFirst();
                echo.setNoAi(true);
                Vec3 c = SylarGeometry.center(), e = echo.position();
                Vec3 at = c.add(e.subtract(c).scale(0.55)).add(0, 3.5, 0), look = e.add(0, 1.2, 0);
                mc.execute(() -> TempestCamera.fix(at, look));
            });
            case 432 -> shot("17b-echo");
            case 434 -> sylar(mc, s -> { if (!s.echoes().isEmpty()) s.breakCase(s.echoCase(s.echoes().getFirst())); });
            case 438 -> shot("17c-echo-fades");
            case 440 -> { camera(mc, 0, 18, 6, 0, 0, -1); server(mc, p -> stand(p, 1, 12)); }
            case 446 -> sylar(mc, s -> s.castNow("second_hand"));
            case 466 -> shot("18-second-hand-windup");
            case 490 -> shot("19-second-hand-sweeps");
            case 520 -> { camera(mc, 0, 14, 16, 0, 0, 0); server(mc, p -> stand(p, 8, 9)); }
            case 526 -> sylar(mc, s -> s.castNow("blur"));
            case 542 -> shot("20-blur-windup");
            case 570 -> { free(mc, CameraType.THIRD_PERSON_BACK); server(mc, p -> stand(p, 6, 8)); }
            case 576 -> sylar(mc, s -> s.castNow("appraise"));
            case 586 -> shot("21-appraise-windup");
            case 594 -> camera(mc, 5, 4, 5, 0, 3.5, 8);
            case 610 -> shot("22-appraise-held");
            case 640 -> shot("23-appraise-nearly");
            case 660 -> camera(mc, 9, 3.5, 4, 16, 2, 0);
            case 668 -> shot("24-power-in-a-case");
            case 680 -> { free(mc, CameraType.THIRD_PERSON_BACK); server(mc, p -> stand(p, 6, 9)); }
            case 684 -> sylar(mc, s -> s.castNow("yours"));
            case 694 -> shot("25-your-own-windup");
            case 700 -> shot("26-your-own");
            // ---- The Masterwork: pillars rise and the great movement turns overhead ----
            case 740 -> { camera(mc, 0, 13, 20, 0, 3, -2); sylar(mc, s -> s.setHealth(s.getMaxHealth() * 0.3F)); }
            case 760 -> shot("27-masterwork-pillars-rising");
            case 820 -> shot("28-masterwork");
            case 830 -> camera(mc, 0, 3, 14, 0, 14, -2);
            case 836 -> shot("29-great-movement");
            case 850 -> camera(mc, 0, 14, 9, 0, 0, -3);
            case 856 -> sylar(mc, s -> s.castNow("every_hour"));
            case 880 -> shot("30-every-hour-windup");
            case 914 -> shot("31-every-hour-gold");
            case 919 -> shot("32-every-hour-lands");
            case 1000 -> { camera(mc, 15, 6, 15, 0, 4, 0); server(mc, p -> stand(p, 1.5, 13)); }
            case 1006 -> sylar(mc, s -> s.castNow("light"));
            case 1040 -> shot("33-light-windup");
            case 1062 -> shot("34-light-rays-and-shadows");
            case 1068 -> shot("35-light");
            // ---- The end: Claire's case brings him back unless it breaks ----
            case 1180 -> { free(mc, CameraType.THIRD_PERSON_BACK); server(mc, p -> stand(p, 6, 6)); }
            case 1186 -> server(mc, p -> {
                SylarEntity s = CollectionArena.sylar();
                if (s == null) return;
                s.setHealth(2); s.invulnerableTime = 0;
                s.hurt(p.damageSources().playerAttack(p), 10);
            });
            case 1196 -> camera(mc, 0, 6, 9, 0, 1, -12);
            case 1204 -> shot("36-claires-case");
            case 1210 -> sylar(mc, s -> s.breakCase(SylarGeometry.REGENERATION_CASE));
            case 1222 -> { camera(mc, 4, 3, 5, 0, 1.2, 0); }
            case 1230 -> shot("37-death-scene");
            case 1250 -> shot("38-death-scene-late");
            case 1290 -> shot("39-what-he-leaves");
            case 1440 -> { free(mc, CameraType.THIRD_PERSON_BACK); }
            case 1450 -> shot("40-home");
            case 1460 -> {
                System.out.println("SYLAR_PREVIEW_RESULT " + (photos >= 43 ? "PASS" : "FAIL") + ": " + photos + " photos");
                if (photos < 43) PreviewQuiet.report("SYLAR_PREVIEW FAIL only " + photos + " photos");
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
            System.out.println("SYLAR_CAPTURE " + pending);
        } catch (Exception ex) { PreviewQuiet.report("SYLAR_PREVIEW FAIL capture " + pending + ": " + ex); }
        pending = null;
    }
}
