package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.client.FlightRollClient;
import com.jayptucker.heroesevolved.cooldown.CooldownService;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.flight.FlightService;
import com.jayptucker.heroesevolved.flight.FlightSkillService;
import com.jayptucker.heroesevolved.gui.screen.PowersScreen;
import com.jayptucker.heroesevolved.network.ClientFlightVisualState;
import com.jayptucker.heroesevolved.network.ClientScreenTintState;
import com.jayptucker.heroesevolved.network.PlayerPowerSyncService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.skills.SkillTreeData;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import com.jayptucker.heroesevolved.story.bunker.CryoChamberService;
import com.jayptucker.heroesevolved.story.prologue.PrologueService;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
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
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * Presses the real strafe keys (flag {@code heroes.preview.flightroll}) through the game's own key handling, because game
 * tests never touch input. A level-one flyer with no skills runs each case below and the server reports whether a roll
 * started and which way, then the HUD's ROLL ring is read and photographed ready, recharging and low. Results print as {@code ROLL_INPUT ... PASS/FAIL}; shots go to
 * {@code build/flight-roll-preview/screenshots/}.
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class FlightRollPreview {
    private static final int STAGE = 150, HEIGHT = -40, CASE_TICKS = 44, FIRST_CASE = 240;
    private static final InputConstants.Key A = InputConstants.getKey("key.keyboard.a"),
            D = InputConstants.getKey("key.keyboard.d"), J = InputConstants.getKey("key.keyboard.j");

    /** {@code taps}: the keys, in order. {@code gap}: ticks from one press to the next. {@code expected}: -1, 1 or 0 for no roll. */
    private record Case(String name, List<InputConstants.Key> taps, int gap, int expected, Setup setup) {}
    private enum Setup { FLYING, CHAT, LANDED, REBOUND }

    private static final List<Case> CASES = List.of(
            new Case("double-tap A rolls left", List.of(A, A), 3, -1, Setup.FLYING),
            new Case("double-tap D rolls right", List.of(D, D), 3, 1, Setup.FLYING),
            new Case("a single tap does nothing", List.of(D), 3, 0, Setup.FLYING),
            new Case("A then D does nothing", List.of(A, D), 3, 0, Setup.FLYING),
            new Case("two slow taps do nothing", List.of(A, A), 10, 0, Setup.FLYING),
            new Case("typing in chat does nothing", List.of(A, A), 3, 0, Setup.CHAT),
            new Case("on the ground does nothing", List.of(D, D), 3, 0, Setup.LANDED),
            new Case("rebound Strafe Left to J rolls left", List.of(J, J), 3, -1, Setup.REBOUND),
            new Case("after rebinding, A does nothing", List.of(A, A), 3, 0, Setup.REBOUND));

    private static boolean started;
    private static int startup, age, failures;
    private static volatile int seen;
    private static String capture;

    private FlightRollPreview() {}

    private static boolean on() { return Boolean.getBoolean("heroes.preview.flightroll"); }

    private static void server(Minecraft mc, Consumer<ServerPlayer> action) {
        var id = mc.player.getUUID();
        var server = mc.getSingleplayerServer();
        server.execute(() -> action.accept(server.getPlayerList().getPlayer(id)));
    }

    private static void stage(ServerPlayer p) {
        ServerLevel world = p.getServer().overworld();
        for (int cx = (STAGE - 32) >> 4; cx <= (STAGE + 32) >> 4; cx++)
            for (int cz = (STAGE - 32) >> 4; cz <= (STAGE + 32) >> 4; cz++) world.setChunkForced(cx, cz, true);
        p.teleportTo(world, STAGE + .5, HEIGHT, STAGE + .5, 0, 0);
        System.out.println("ROLL_INPUT staged in " + p.level().dimension().location() + " at " + p.blockPosition());
    }

    /** Back to the same spot with a ready roll, flying or standing as the case needs. */
    private static void reset(ServerPlayer p, boolean flying) {
        FlightSkillService.forget(p);
        CooldownService.clearAll(p);
        PlayerEnergyService.restore(p, 999);
        ServerLevel world = p.getServer().overworld();
        world.setDayTime(6000);
        p.removeAllEffects();
        p.setHealth(p.getMaxHealth());
        if (!flying && FlightService.hasActiveSession(p)) FlightService.toggleFlight(p);
        p.teleportTo(world, STAGE + .5, flying ? HEIGHT : -60, STAGE + .5, 0, 0);
        p.setDeltaMovement(Vec3.ZERO);
        if (flying && !FlightService.hasActiveSession(p)) FlightService.toggleFlight(p);
    }

    private static void rebind(Minecraft mc, InputConstants.Key key) {
        mc.options.keyLeft.setKey(key);
        KeyMapping.resetMapping();
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!on()) return;
        var mc = Minecraft.getInstance();
        if (!started && ++startup >= 35 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.options.pauseOnLostFocus = false;
            mc.options.renderDistance().set(6);
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("Roll-" + System.currentTimeMillis(),
                    new LevelSettings("Roll Preview", GameType.CREATIVE, false, Difficulty.EASY, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(73L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.getSingleplayerServer() == null || mc.getOverlay() != null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        if (age >= 100) mc.getToasts().clear();
        ++age;
        if (age == 104) { server(mc, PrologueService::skip); return; }
        if (age == 110) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            ClientScreenTintState.clear();
            var power = ModAbilities.FLIGHT_ID;
            server(mc, p -> {
                CryoChamberService.release(p);
                p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(power).unlock(power));
                p.setData(ModDataAttachments.POWER_INTRO_HISTORY, p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(power));
                PlayerProgressionService.setLevel(p, 1);
                p.setData(ModDataAttachments.SKILL_TREES, SkillTreeData.empty());
                p.setGameMode(GameType.SURVIVAL);
                stage(p);
                PlayerPowerSyncService.sync(p);
                SkillTreeService.sync(p);
            });
            return;
        }
        if (age < FIRST_CASE) return;
        if (age % 20 == 0) ClientScreenTintState.clear();
        int index = (age - FIRST_CASE) / CASE_TICKS, t = (age - FIRST_CASE) % CASE_TICKS;
        if (index < CASES.size()) runCase(mc, index, CASES.get(index), t);
        else finish(mc, age - FIRST_CASE - CASES.size() * CASE_TICKS);
    }

    private static void runCase(Minecraft mc, int index, Case c, int t) {
        if (t == 0) {
            seen = 0;
            if (c.setup == Setup.REBOUND) rebind(mc, J);
            server(mc, p -> reset(p, c.setup != Setup.LANDED));
        }
        if (t == 8 && c.setup == Setup.CHAT) mc.setScreen(new ChatScreen(""));
        if (t >= 14 && t < 36) server(mc, p -> {
            if (FlightSkillService.isRolling(p)) seen = FlightSkillService.rollDirection(p);
        });
        for (int i = 0; i < c.taps.size(); i++) {
            int press = 14 + i * c.gap;
            if (t == press) KeyMapping.set(c.taps.get(i), true);
            if (t == press + 1) KeyMapping.set(c.taps.get(i), false);
        }
        if (index == 1 && t == 14 + c.gap + 5) capture = "roll-right.png";
        if (t == 38 && c.setup == Setup.CHAT) mc.setScreen(null);
        if (t == 40) {
            boolean ok = seen == c.expected;
            if (!ok) failures++;
            PreviewQuiet.report("ROLL_INPUT " + c.name + " " + (ok ? "PASS" : "FAIL") + " (expected " + c.expected + ", saw " + seen
                    + ", flight on client " + ClientFlightVisualState.isFlightEnabled(mc.player.getId()) + ")");
        }
    }

    private static void finish(Minecraft mc, int t) {
        if (t == 0) {
            rebind(mc, A);
            server(mc, p -> reset(p, true));
        }
        if (t == 12) {
            mc.setScreen(new PowersScreen());
            if (mc.screen instanceof PowersScreen screen) screen.select(FlightSkillService.ROLL);
        }
        if (t == 30) capture = "tree-barrel-roll.png";
        if (t == 40) mc.setScreen(null);
        if (t == 50) hud("ready", FlightRollClient.RollStatus.READY, "hud-ready.png");
        if (t == 54 || t == 57) KeyMapping.set(D, true);
        if (t == 55 || t == 58) KeyMapping.set(D, false);
        if (t == 70) hud("recharging", FlightRollClient.RollStatus.COOLING, "hud-recharging.png");
        if (t == 74) server(mc, p -> {
            CooldownService.clearAll(p);
            PlayerPowerSyncService.sync(p);
        });
        if (t >= 76 && t <= 84) server(mc, p -> PlayerEnergyService.drain(p, Math.max(0, PlayerEnergyService.getStamina(p) - 2)));
        if (t == 86) hud("low on stamina", FlightRollClient.RollStatus.LOW, "hud-low.png");
        if (t == 100) {
            int total = CASES.size() + 3;
            PreviewQuiet.report("ROLL_INPUT_RESULT " + (failures == 0 ? "PASS" : "FAIL") + " (" + failures + " of " + total + " failed)");
            PreviewQuiet.quit();
        }
    }

    private static void hud(String name, FlightRollClient.RollStatus expected, String shot) {
        var saw = FlightRollClient.status();
        boolean ok = saw == expected;
        if (!ok) failures++;
        PreviewQuiet.report("ROLL_INPUT HUD ring " + name + " " + (ok ? "PASS" : "FAIL") + " (expected " + expected + ", saw " + saw + ")");
        capture = shot;
    }

    @SubscribeEvent public static void frame(RenderFrameEvent.Post event) {
        if (capture == null) return;
        Minecraft mc = Minecraft.getInstance();
        Path path = mc.gameDirectory.toPath().resolve("screenshots").resolve(capture);
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Files.createDirectories(path.getParent());
            image.writeToFile(path);
            System.out.println("ROLL_INPUT captured " + capture);
        } catch (Exception e) {
            PreviewQuiet.report("ROLL_INPUT FAIL: could not capture " + capture + ": " + e);
        }
        capture = null;
    }
}
