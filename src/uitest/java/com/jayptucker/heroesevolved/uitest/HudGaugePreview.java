package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.gui.HudGauges;
import com.jayptucker.heroesevolved.hazard.BodyBurn;
import com.jayptucker.heroesevolved.network.ClientMomentum;
import com.jayptucker.heroesevolved.network.ClientOverrun;
import com.jayptucker.heroesevolved.network.MomentumPayload;
import com.jayptucker.heroesevolved.network.OverrunStatusPayload;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.story.bunker.CryoChamberService;
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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Photographs the HUD's ring gauges (gui/HudGauges) in each state, with the readings set directly on the client
 * (flag {@code heroes.preview.gauges}). Shots go to {@code build/gauge-preview/screenshots/}.
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class HudGaugePreview {
    private static boolean started;
    private static int startup, age;
    private static String capture;

    private static boolean on() { return Boolean.getBoolean("heroes.preview.gauges"); }

    private static void server(Minecraft mc, Consumer<ServerPlayer> action) {
        var id = mc.player.getUUID();
        var server = mc.getSingleplayerServer();
        server.execute(() -> action.accept(server.getPlayerList().getPlayer(id)));
    }

    private static void momentum(int momentum, int step, int max) {
        try {
            var set = ClientMomentum.class.getDeclaredMethod("set", MomentumPayload.class);
            set.setAccessible(true);
            set.invoke(null, new MomentumPayload(momentum, step, max));
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private static void overrun(int state, int value) {
        try {
            var set = ClientOverrun.class.getDeclaredMethod("set", int.class, int.class);
            set.setAccessible(true);
            set.invoke(null, state, value);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    /** Calls a package-private client-state setter. */
    private static void call(Class<?> owner, String name, Class<?>[] types, Object... args) {
        try {
            var m = owner.getDeclaredMethod(name, types);
            m.setAccessible(true);
            m.invoke(null, args);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    /** Sets a private static field on a client class (its state payload, its last-seen time). */
    private static void field(Class<?> owner, String name, Object value) {
        try {
            var f = owner.getDeclaredField(name);
            f.setAccessible(true);
            f.set(null, value);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    /** Makes you a tier-5 holder of {@code power}, with the speedster's readings cleared. */
    private static void power(Minecraft mc, net.minecraft.resources.ResourceLocation power) {
        ClientMomentum.clear();
        ClientOverrun.clear();
        HudGauges.friction(0);
        server(mc, p -> {
            p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(power).unlock(power));
            p.setData(ModDataAttachments.POWER_INTRO_HISTORY, p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(power));
            PlayerProgressionService.setLevel(p, 5);
            com.jayptucker.heroesevolved.network.PlayerPowerSyncService.sync(p);
        });
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!on()) return;
        var mc = Minecraft.getInstance();
        if (!started && ++startup >= 35 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.options.pauseOnLostFocus = false;
            mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("Gauges-" + System.currentTimeMillis(),
                    new LevelSettings("Gauge Preview", GameType.CREATIVE, false, Difficulty.EASY, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(61L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.getSingleplayerServer() == null || mc.getOverlay() != null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        if (age >= 100) mc.getToasts().clear();
        ++age;
        if (age == 104) { server(mc, com.jayptucker.heroesevolved.story.prologue.PrologueService::skip); return; }
        if (age == 110) {
            server(mc, p -> {
                CryoChamberService.release(p);
                p.setGameMode(GameType.SURVIVAL);
                p.getAbilities().invulnerable = true;
                p.onUpdateAbilities();
                p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(ModAbilities.SUPERSPEED_ID).unlock(ModAbilities.SUPERSPEED_ID));
                p.setData(ModDataAttachments.POWER_INTRO_HISTORY, p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(ModAbilities.SUPERSPEED_ID));
                PlayerProgressionService.setLevel(p, 5);
                com.jayptucker.heroesevolved.network.PlayerPowerSyncService.sync(p);
                var world = p.getServer().overworld();
                world.setChunkForced(9, 9, true);
                p.teleportTo(world, 150.5, -60, 150.5, 0, 0);
                world.setDayTime(6000);
            });
            return;
        }
        if (age < 170) return;
        if (age % 20 == 0) com.jayptucker.heroesevolved.network.ClientScreenTintState.clear();
        int t = age - 170;
        switch (t) {
            case 5 -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            // Running: Momentum and the run speed only.
            case 20 -> { momentum(42, 3, 6); overrun(OverrunStatusPayload.HIDDEN, 0); HudGauges.friction(0); capture = "1-running.png"; }
            // Flat out: Momentum full, Overrun charging, friction past scorch, skin burning.
            case 30 -> server(mc, p -> BodyBurn.atLeast(p, 0.45F));
            case 45 -> { momentum(100, 6, 6); overrun(OverrunStatusPayload.READY, 640); HudGauges.friction(62); capture = "2-flat-out.png"; }
            // Burning up, the breach open.
            case 55 -> { momentum(100, 6, 6); overrun(OverrunStatusPayload.BREACH, 1000); HudGauges.friction(84); capture = "3-burning-a.png"; }
            case 57 -> capture = "3-burning-b.png";
            // Eased off: friction gone, Overrun cooling down, skin growing back.
            case 60 -> { momentum(18, 2, 6); HudGauges.friction(0); overrun(OverrunStatusPayload.COOLDOWN, 412); }
            case 72 -> { overrun(OverrunStatusPayload.COOLDOWN, 412); capture = "4-regrowing.png"; }
            // Below Power Level 15, flat out: Overrun shows it's locked.
            case 140 -> { momentum(96, 4, 4); overrun(OverrunStatusPayload.LOCKED, 0); capture = "5-locked.png"; }
            // Every other power's rings.
            case 170 -> power(mc, ModAbilities.RADIATION_ID);
            case 185 -> {
                com.jayptucker.heroesevolved.client.effects.RadiationVisuals.update(
                        new com.jayptucker.heroesevolved.network.RadiationStatePayload(mc.player.getId(), mc.player.getUUID(), 72, 0, 1));
                capture = "06-radiation.png";
            }
            case 195 -> power(mc, ModAbilities.SONIC_RESONANCE_ID);
            case 210 -> { call(com.jayptucker.heroesevolved.network.ClientResonance.class, "set", new Class<?>[] {int.class}, 65); capture = "07-sonic.png"; }
            case 220 -> power(mc, ModAbilities.QUAKE_ID);
            case 235 -> { call(com.jayptucker.heroesevolved.network.ClientStrain.class, "set", new Class<?>[] {int.class, boolean.class}, 58, false); capture = "08-quake.png"; }
            case 238 -> { call(com.jayptucker.heroesevolved.network.ClientStrain.class, "set", new Class<?>[] {int.class, boolean.class}, 30, true); capture = "08-quake-fractured.png"; }
            case 250 -> power(mc, ModAbilities.PHASING_ID);
            case 265 -> { call(com.jayptucker.heroesevolved.network.ClientDensity.class, "set", new Class<?>[] {int.class, int.class}, -50, 40); capture = "09-density-ghost.png"; }
            case 268 -> { call(com.jayptucker.heroesevolved.network.ClientDensity.class, "set", new Class<?>[] {int.class, int.class}, 100, -1); capture = "09-density-dense.png"; }
            case 280 -> power(mc, ModAbilities.REGENERATION_ID);
            case 295 -> {
                call(com.jayptucker.heroesevolved.network.ClientRegenerationState.class, "update",
                        new Class<?>[] {com.jayptucker.heroesevolved.network.RegenerationStatePayload.class},
                        new com.jayptucker.heroesevolved.network.RegenerationStatePayload(64, 0, 600, -1));
                capture = "10-regeneration.png";
            }
            case 298 -> {
                call(com.jayptucker.heroesevolved.network.ClientRegenerationState.class, "update",
                        new Class<?>[] {com.jayptucker.heroesevolved.network.RegenerationStatePayload.class},
                        new com.jayptucker.heroesevolved.network.RegenerationStatePayload(64, 380, 600, 25));
                capture = "10-regeneration-down.png";
            }
            case 310 -> power(mc, ModAbilities.FLIGHT_ID);
            case 325 -> capture = "11-flight.png";
            case 340 -> power(mc, ModAbilities.PYROKINESIS_ID);
            case 355 -> {
                field(com.jayptucker.heroesevolved.client.PyroFlightClient.class, "state",
                        new com.jayptucker.heroesevolved.network.PyroStatePayload(true, false, false, 24, false, 0, 0, false));
                field(com.jayptucker.heroesevolved.client.PyroFlightClient.class, "seen", mc.level.getGameTime());
                capture = "12-pyro.png";
            }
            case 358 -> {
                field(com.jayptucker.heroesevolved.client.PyroFlightClient.class, "state",
                        new com.jayptucker.heroesevolved.network.PyroStatePayload(true, true, false, 71, true, 3, 64, false));
                field(com.jayptucker.heroesevolved.client.PyroFlightClient.class, "seen", mc.level.getGameTime());
                capture = "12-pyro-flying-blue.png";
            }
            case 370 -> power(mc, ModAbilities.INVISIBILITY_ID);
            case 385 -> {
                field(com.jayptucker.heroesevolved.client.OpticalClient.class, "state",
                        new com.jayptucker.heroesevolved.network.OpticalStatePayload(true, true, 42, false, false));
                field(com.jayptucker.heroesevolved.client.OpticalClient.class, "seen", mc.level.getGameTime());
                capture = "13-invisibility.png";
            }
            case 395 -> power(mc, ModAbilities.TELEPATHY_ID);
            case 410 -> {
                field(com.jayptucker.heroesevolved.client.TelepathyClient.class, "state",
                        new com.jayptucker.heroesevolved.network.MindStatePayload(0, -1, 0, 0, 0, 0, 60, 85));
                capture = "14-telepathy.png";
            }
            case 413 -> {
                field(com.jayptucker.heroesevolved.client.TelepathyClient.class, "state",
                        new com.jayptucker.heroesevolved.network.MindStatePayload(3, -1, 90, 38, 1, 0, 0, 0));
                capture = "14-telepathy-possessed.png";
            }
            case 416 -> field(com.jayptucker.heroesevolved.client.TelepathyClient.class, "state",
                    new com.jayptucker.heroesevolved.network.MindStatePayload(0, -1, 0, 0, 0, 0, 0, 0));
            case 420 -> server(mc, p -> com.jayptucker.heroesevolved.cryo.CryoService.freezeSolid(p, 200, null));
            case 432 -> {
                try {
                    var cracks = com.jayptucker.heroesevolved.client.CryoShellClient.class.getDeclaredField("CRACKS");
                    cracks.setAccessible(true);
                    @SuppressWarnings("unchecked") var map = (java.util.Map<Integer, Integer>) cracks.get(null);
                    map.put(mc.player.getId(), 45);
                } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
                capture = "15-cryo-shell.png";
            }
            case 450 -> PreviewQuiet.quit();
            default -> { }
        }
    }

    @SubscribeEvent public static void frame(RenderFrameEvent.Post event) {
        if (capture == null || !on()) return;
        Minecraft mc = Minecraft.getInstance();
        Path path = mc.gameDirectory.toPath().resolve("screenshots").resolve(capture);
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Files.createDirectories(path.getParent());
            image.writeToFile(path);
            System.out.println("GAUGE_PREVIEW CAPTURE " + capture);
        } catch (Exception e) {
            System.out.println("GAUGE_PREVIEW CAPTURE FAILED " + capture + " " + e);
        }
        capture = null;
    }
}
