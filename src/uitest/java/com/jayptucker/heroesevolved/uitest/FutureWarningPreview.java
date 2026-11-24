package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.admin.AdminService;
import com.jayptucker.heroesevolved.client.effects.TempestCamera;
import com.jayptucker.heroesevolved.client.effects.intro.FutureLook;
import com.jayptucker.heroesevolved.client.effects.setpiece.SetPieceClient;
import com.jayptucker.heroesevolved.gui.screen.AdminScreen;
import com.jayptucker.heroesevolved.gui.screen.GuideScreen;
import com.jayptucker.heroesevolved.network.AdminActionPayload;
import com.jayptucker.heroesevolved.story.bunker.CryoChamberService;
import com.jayptucker.heroesevolved.story.future.FutureWarning;
import com.jayptucker.heroesevolved.story.prologue.PrologueService;
import com.jayptucker.heroesevolved.styles.Piece;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
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
import java.util.function.Consumer;

@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class FutureWarningPreview {
    private static boolean started, omenShot;
    private static int ticks, age, photos, scene;
    private static String pending;
    private static final Vec3 PLAYER = new Vec3(1500.5, -60, .5), CENTER = PLAYER.add(0, 1.2, -4);
    private static final boolean[] SHOTS = new boolean[6];
    private static void server(Minecraft mc, Consumer<ServerPlayer> work) {
        var id = mc.player.getUUID(); var server = mc.getSingleplayerServer();
        server.execute(() -> {
            try { work.accept(server.getPlayerList().getPlayer(id)); }
            catch (RuntimeException | AssertionError error) { PreviewQuiet.report("FUTURE_PREVIEW FAIL: " + error); }
        });
    }
    private static void shot(String name) { pending = "future-" + name + ".png"; }
    private static void report(boolean ok, String message) { PreviewQuiet.report("FUTURE_PREVIEW " + (ok ? "PASS: " : "FAIL: ") + message); }
    private static void click(Minecraft mc, String label) {
        if (!(mc.screen instanceof AdminScreen s)) { report(false, "Admin closed: " + label); return; }
        for (int i = 0; i < 45; i++) {
            for (var child : s.children()) if (child instanceof net.minecraft.client.gui.components.Button b
                    && b.getMessage().getString().equals(label) && b.active) {
                s.mouseClicked(b.getX() + b.getWidth() / 2.0, b.getY() + 9, 0); report(true, "Clicked " + label); return;
            }
            s.mouseScrolled(s.width - 25, s.height / 2.0, 0, -1);
        }
        report(false, "Missing button: " + label);
    }
    private static void refresh(Minecraft mc) {
        server(mc, p -> AdminService.handle(p, new AdminActionPayload(AdminActionPayload.Action.REFRESH, p.getUUID(), "", 0)));
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("heroes.preview.futurewarning")) return;
        var mc = Minecraft.getInstance(); ticks++;
        if (!started && ticks > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true; mc.options.pauseOnLostFocus = false; mc.options.guiScale().set(2); mc.resizeDisplay();
            mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
            var rules = new GameRules(); rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("Future-" + System.currentTimeMillis(),
                    new LevelSettings("Future warning preview", GameType.SURVIVAL, false, Difficulty.NORMAL, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(91, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null || mc.getOverlay() != null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        mc.getToasts().clear(); ++age;
        if (age == 130) server(mc, PrologueService::skip);
        if (age == 155) server(mc, p -> {
            CryoChamberService.release(p); var level = p.getServer().overworld();
            for (int x = 92; x <= 95; x++) for (int z = -2; z <= 1; z++) level.setChunkForced(x, z, true);
            p.teleportTo(level, PLAYER.x, PLAYER.y, PLAYER.z, 0, 0); p.setGameMode(GameType.SURVIVAL);
            p.removeAllEffects(); level.setDayTime(6000);
            System.out.println("FUTURE_STAGE " + p.level().dimension().location() + " on " + level.getBlockState(p.blockPosition().below()));
        });
        if (age == 210) server(mc, AdminService::open);
        if (age == 225) { AdminScreen.showStory(); refresh(mc); }
        if (age == 240) click(mc, "Future-self warning");
        if (age == 250) shot("01-admin-controls");
        if (age == 260) click(mc, "Omen, then one day");
        if (age == 268) {
            server(mc, p -> report(FutureWarning.active(p) && FutureWarning.pending(p), "Omen button starts a saved next-day encounter"));
            mc.setScreen(null); mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            TempestCamera.fix(PLAYER.add(5, 2.5, 1), CENTER);
        }
        if (age > 270 && age < 345 && !omenShot && !SetPieceClient.where(Piece.FUTURE_OMEN).isEmpty()) {
            omenShot = true; shot("02-omen");
        }
        if (age == 370) { TempestCamera.fix(null, null); server(mc, AdminService::open); }
        if (age == 380) click(mc, "Cancel event");
        if (age == 390) {
            server(mc, p -> report(!FutureWarning.active(p) && !FutureWarning.pending(p), "Cancel removes the saved warning"));
            mc.options.guiScale().set(3); mc.resizeDisplay();
        }
        if (age == 398) shot("03-admin-compact");
        if (age == 405) { mc.options.guiScale().set(2); mc.resizeDisplay(); }
        if (age == 415) click(mc, "Reset encounter");
        if (age == 425) {
            mc.setScreen(null); mc.options.setCameraType(CameraType.FIRST_PERSON);
            server(mc, p -> { p.teleportTo(p.serverLevel(), PLAYER.x, PLAYER.y, PLAYER.z, 0, 0); AdminService.open(p); });
        }
        if (age == 440) click(mc, "Warning now");
        if (age == 443) mc.setScreen(null);
        if (age >= 440 && age < 780) watch(mc);
        if (age == 790) { TempestCamera.fix(null, null); mc.setScreen(new GuideScreen(null).topic("world")); }
        if (age == 800) shot("10-guide");
        if (age == 820) {
            report(omenShot && scene >= 6 && photos == 10, "Ten photographs, the actual turn, reaching, pullback and closure");
            PreviewQuiet.quit();
        }
    }
    private static void watch(Minecraft mc) {
        var actor = java.util.stream.StreamSupport.stream(mc.level.entitiesForRendering().spliterator(), false)
                .filter(e -> e instanceof com.jayptucker.heroesevolved.story.actor.StoryActorEntity a && a.doubleOf().filter(mc.player.getUUID()::equals).isPresent())
                .findFirst().orElse(null);
        int phase = age - 440;
        if (phase >= 24 && !SHOTS[0]) {
            SHOTS[0] = true; scene++;
            Vec3 d = CENTER.subtract(mc.player.getEyePosition()).normalize();
            report(FutureLook.active() && mc.player.getLookAngle().dot(d) > .95, "Player's own head smoothly turns to the breach");
            shot("04-first-person-turn");
        }
        if (phase >= 75 && !SHOTS[1] && actor != null) {
            SHOTS[1] = true; scene++; mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            TempestCamera.fix(PLAYER.add(4, 2.2, -1), CENTER); shot("05-reaching-side");
        }
        if (phase >= 115 && !SHOTS[2] && actor != null) {
            SHOTS[2] = true; scene++; TempestCamera.fix(PLAYER.add(0, 1.6, -1), CENTER); shot("06-reaching-front");
        }
        if (phase >= 193 && !SHOTS[3] && actor != null) {
            SHOTS[3] = true; scene++; TempestCamera.fix(PLAYER.add(4, 2.1, -2), CENTER); shot("07-pulled-back");
        }
        if (phase >= 244 && !SHOTS[4] && !SetPieceClient.where(Piece.FUTURE_SHUT).isEmpty()) {
            SHOTS[4] = true; scene++; shot("08-snap-shut");
        }
        if (phase >= 290 && !SHOTS[5]) {
            SHOTS[5] = true; scene++; TempestCamera.fix(null, null); mc.options.setCameraType(CameraType.FIRST_PERSON);
            server(mc, p -> report(!FutureWarning.active(p) && !FutureWarning.pending(p), "Encounter completes and cleans up"));
            shot("09-aftermath");
        }
    }
    @SubscribeEvent public static void render(RenderFrameEvent.Post event) {
        if (pending == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            var out = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(out.getParent()); image.writeToFile(out); photos++;
            System.out.println("FUTURE_CAPTURE " + pending);
        } catch (Exception error) { report(false, "Capture " + pending + ": " + error); }
        pending = null;
    }
}
