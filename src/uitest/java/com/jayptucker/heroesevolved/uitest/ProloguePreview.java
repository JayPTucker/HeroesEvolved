package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.story.prologue.PrologueService;
import com.jayptucker.heroesevolved.story.prologue.PrologueTimeline;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
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
import java.util.function.BiConsumer;

/**
 * Plays the Last Day in a fresh world and photographs it, beat by beat, into build/prologue-preview/screenshots/: the
 * apartment, the evening street, the light and a surge, the agents at Primatech's doors, Noah and Claire, the stun gun,
 * the pod, the frost, the title card and the bunker waking.
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class ProloguePreview {
    private static boolean started, overworld;
    private static int ticks, step, wakeTicks;
    private static String pending, shot;
    private static int shotAt = -1;

    private record Step(String phase, int age, BiConsumer<MinecraftServer, ServerPlayer> action, String shot, CameraType camera) {}

    /** The run's length on the default config. STEPS is built when this class loads, before any config is: never {@code get()} here. */
    private static int run() { return 20 * com.jayptucker.heroesevolved.config.HeroesEvolvedConfig.COMMON.prologueRunSeconds.getDefault(); }

    private static void stand(ServerPlayer p, double x, double y, double z, float yaw, float pitch) {
        p.teleportTo(p.serverLevel(), x, y, z, yaw, pitch);
    }

    /** Relative to the safe house's origin. */
    private static void below(ServerPlayer p, double x, double y, double z, float yaw, float pitch) {
        BlockPos o = PrologueService.safeHouse();
        stand(p, o.getX() + x, o.getY() + y, o.getZ() + z, yaw, pitch);
    }

    /** A still camera for a view the player's own path doesn't give (cleared with nulls); see {@code TempestCamera.fix}. */
    private static void camera(net.minecraft.world.phys.Vec3 at, net.minecraft.world.phys.Vec3 lookAt) {
        Minecraft.getInstance().execute(() -> com.jayptucker.heroesevolved.client.effects.TempestCamera.fix(at, lookAt));
    }

    private static net.minecraft.world.phys.Vec3 v(double x, double y, double z) { return new net.minecraft.world.phys.Vec3(x, y, z); }

    /** Relative to the Odessa Arms' north-west corner, on the first floor (apartment 1A, where a lone player wakes). */
    private static net.minecraft.world.phys.Vec3 arms(double x, double y, double z) {
        return v(com.jayptucker.heroesevolved.time.intro.city.Apartments.X0 + x,
                com.jayptucker.heroesevolved.time.intro.city.Apartments.Y + com.jayptucker.heroesevolved.time.intro.city.Apartments.STOREY + y,
                com.jayptucker.heroesevolved.time.intro.city.Apartments.Z0 + z);
    }

    private static final com.jayptucker.heroesevolved.time.intro.SkyFight.Script FAR = com.jayptucker.heroesevolved.time.intro.SkyFight.LAST_DAY;

    /** A field of view for a shot (the zoomed ones check the far fight; players see it at their own). */
    private static void zoom(int fov) { Minecraft.getInstance().execute(() -> Minecraft.getInstance().options.fov().set(fov)); }

    private static final Step[] STEPS = {
            new Step("apartment", 60, null, "1-apartment", CameraType.FIRST_PERSON),
            new Step("apartment", 70, (s, p) -> p.setYRot(p.getYRot() + 180), null, null),
            new Step("apartment", 80, null, "1b-window", CameraType.FIRST_PERSON),
            // The whole room from its far corner: bed under the window, kitchen, table, radio.
            new Step("apartment", 84, (s, p) -> camera(arms(6.8, 2.4, 4.8), arms(1.5, 1.0, 1.5)), null, null),
            new Step("apartment", 88, null, "1c-apartment-room", null),
            new Step("apartment", 92, (s, p) -> camera(null, null), null, null),
            // Trying on Noah's glasses, seen from the front.
            new Step("apartment", 96, (s, p) -> p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,
                    new net.minecraft.world.item.ItemStack(com.jayptucker.heroesevolved.registry.ModItems.HORN_RIMMED_GLASSES.get())), null, CameraType.THIRD_PERSON_FRONT),
            new Step("apartment", 102, null, "1d-glasses-on-you", CameraType.THIRD_PERSON_FRONT),
            new Step("apartment", 108, (s, p) -> p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.item.ItemStack.EMPTY), null, CameraType.FIRST_PERSON),
            new Step("apartment", 112, (s, p) -> PrologueService.ready(p), null, null),
            new Step("evening", 30, (s, p) -> stand(p, -44.5, 65, 103.5, 90, -4), "2-odessa-arms", CameraType.FIRST_PERSON),
            new Step("evening", 60, (s, p) -> stand(p, -1.5, 65, 79.5, 0, 0), null, null),
            new Step("evening", 140, null, "3-conversation", CameraType.FIRST_PERSON),
            new Step("evening", 150, (s, p) -> PrologueService.skipTo(s, "light", 0), null, null),
            new Step("light", 80, (s, p) -> stand(p, 4.5, 64, 70.5, 180, -8), "4-light", CameraType.FIRST_PERSON),
            // Far up the avenue, Peter and Sylar fighting over the light: as you'd see it, then zoomed in to check it.
            new Step("light", FAR.clashAt(2) - 6, (s, p) -> stand(p, 4.5, 65, 60.5, 180, -14), null, null),
            new Step("light", FAR.clashAt(2), null, "4b-far-fight", CameraType.FIRST_PERSON),
            new Step("light", FAR.clashAt(4), (s, p) -> zoom(30), "4c-far-fight-zoom", CameraType.FIRST_PERSON),
            new Step("light", FAR.impactAt() - 1, null, "4d-far-crash-zoom", CameraType.FIRST_PERSON),
            new Step("light", FAR.impactAt() + 30, (s, p) -> zoom(70), null, null),
            new Step("light", FAR.impactAt() + 34, (s, p) -> PrologueService.skipTo(s, "light", PrologueTimeline.surgeTick(3, run()) - 4), null, null),
            new Step("light", PrologueTimeline.surgeTick(3, run()) - 1, null, "5-surge", CameraType.FIRST_PERSON),
            new Step("light", PrologueTimeline.surgeTick(3, run()) + 20, (s, p) -> stand(p, 4.5, 65, 97.5, 0, 4), "6-primatech-doors", CameraType.FIRST_PERSON),
            // The head of the stair in the lobby, looking down the top flight.
            new Step("light", PrologueTimeline.surgeTick(3, run()) + 40, (s, p) -> stand(p, 6.5, 65, 110.5, -90, 28), "6b-stairwell", CameraType.FIRST_PERSON),
            // The lower flight from its foot, looking up to the turn, and the open vault from the foot of the stair, with the player still upstairs.
            new Step("light", PrologueTimeline.surgeTick(3, run()) + 50, (s, p) -> camera(v(7.3, 49.2, 106.5), v(16.5, 56.5, 106.5)), null, null),
            new Step("light", PrologueTimeline.surgeTick(3, run()) + 54, null, "6c-lower-flight", null),
            new Step("light", PrologueTimeline.surgeTick(3, run()) + 60, (s, p) -> camera(v(6.6, 49.4, 105.4), v(4.5, 48.0, 112.0)), null, null),
            new Step("light", PrologueTimeline.surgeTick(3, run()) + 64, null, "6c-landing", null),
            new Step("light", PrologueTimeline.surgeTick(3, run()) + 70, (s, p) -> camera(null, null), null, null),
            // At the foot of the stair, facing the open vault: being down here ends the light phase at once.
            new Step("light", PrologueTimeline.surgeTick(3, run()) + 80, (s, p) -> below(p, 0.5, 1, -5.5, 0, 6), null, null),
            new Step("safe_house", 3, null, "6d-vault-open", CameraType.FIRST_PERSON),
            // Through it, looking back as it shuts.
            new Step("safe_house", 10, (s, p) -> below(p, 0.5, 1, 3.5, 180, 4), null, null),
            new Step("safe_house", 18, null, "6e-vault-shut", CameraType.FIRST_PERSON),
            new Step("safe_house", 30, (s, p) -> below(p, 0.5, 1, 20.5, 0, 6), "7-stasis-hall", CameraType.FIRST_PERSON),
            new Step("noah", 12, null, "8-claire-pleads", CameraType.FIRST_PERSON),
            // Noah close up, glasses and all, from beside Claire.
            new Step("noah", 26, (s, p) -> { BlockPos o = PrologueService.safeHouse();
                camera(v(o.getX() + 1.9, o.getY() + 2.65, o.getZ() + 29.0), v(o.getX() + 0.5, o.getY() + 2.55, o.getZ() + 28.5)); }, null, null),
            new Step("noah", 32, null, "8b-noah-close", null),
            new Step("noah", 38, (s, p) -> camera(null, null), null, null),
            new Step("noah", 250, null, "9-dad-please", CameraType.FIRST_PERSON),
            new Step("noah", 285, null, "10-sealed", CameraType.FIRST_PERSON),
            new Step("noah", 305, null, "11-hand-on-glass", CameraType.FIRST_PERSON),
            new Step("noah", 410, null, "12-for-your-own-good", CameraType.FIRST_PERSON),
            new Step("noah", 456, null, "13-stun-gun", CameraType.FIRST_PERSON),
            new Step("noah", 473, null, "14-shot", CameraType.FIRST_PERSON),
            new Step("pods", 45, null, "15-pod", CameraType.FIRST_PERSON),
            new Step("pods", 160, null, "16-shaking", CameraType.FIRST_PERSON),
            // Just after the blow, down the aisle: lights stuttering, stone coming down.
            new Step("pods", 182, (s, p) -> { BlockPos o = PrologueService.safeHouse();
                camera(v(o.getX() + 0.5, o.getY() + 5.5, o.getZ() + 19.5), v(o.getX() + 0.5, o.getY() + 1.5, o.getZ() + 34.0)); }, null, null),
            new Step("pods", 186, null, "16b-blast-hall", null),
            new Step("pods", 236, null, "16c-after-blast", null),
            new Step("pods", 240, (s, p) -> camera(null, null), null, null),
            new Step("pods", 300, null, "17-frost", CameraType.FIRST_PERSON),
            new Step("pods", 410, null, "18-years-later", CameraType.FIRST_PERSON),
    };

    /** The day's phases in order; "none" (before and after) sorts first. */
    private static final java.util.List<String> ORDER = java.util.List.of("none", "apartment", "evening", "light", "safe_house", "noah", "pods");

    private static void onServer(Minecraft mc, BiConsumer<MinecraftServer, ServerPlayer> action) {
        MinecraftServer server = mc.getSingleplayerServer();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayer(mc.player.getUUID());
            if (p != null) action.accept(server, p);
        });
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        if (!Boolean.getBoolean("heroes.preview.prologue")) return;
        Minecraft mc = Minecraft.getInstance();
        ticks++;
        if (!started && ticks > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.options.hideGui = false;
            mc.options.pauseOnLostFocus = false;
            mc.options.renderDistance().set(12);
            GameRules rules = new GameRules();
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("Prologue-" + System.currentTimeMillis(),
                    new LevelSettings("Prologue preview", GameType.SURVIVAL, false, Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(5L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        if (overworld) {
            // Handed back: the bunker under spawn, 103 years later.
            if (++wakeTicks == 70) pending = "prologue-19-bunker-wake.png";
            if (wakeTicks == 150) pending = "prologue-20-bunker-awake.png";
            // Pod 14 in the present: open, its glass clawed through, Claire's note on the lectern beside it.
            if (wakeTicks == 155) {
                BlockPos o = com.jayptucker.heroesevolved.story.bunker.BunkerData.get(mc.getSingleplayerServer()).origin;
                camera(v(o.getX() + 2.2, o.getY() + 2.5, o.getZ() + 30.6), v(o.getX() + 5.0, o.getY() + 1.6, o.getZ() + 32.8));
            }
            if (wakeTicks == 165) pending = "prologue-21-pod-14.png";
            if (wakeTicks == 170) camera(null, null);
            if (wakeTicks > 185) mc.stop();
            return;
        }
        String phase = PrologueService.phase();
        int age = PrologueService.age();
        if (shot != null && age >= shotAt) { pending = shot; shot = null; }
        if (step >= STEPS.length) {
            if (phase.equals("none") && mc.level.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
                overworld = true;
                System.out.println("PROLOGUE_HOME");
            }
            return;
        }
        Step s = STEPS[step];
        // The day moved past this step's phase (a shortcut ended it early): skip the step rather than wait forever.
        if (ORDER.indexOf(phase) > ORDER.indexOf(s.phase)) {
            step++;
            System.out.println("PROLOGUE_SKIPPED " + step + " " + s.phase + " (now " + phase + ")");
            return;
        }
        if (!phase.equals(s.phase) || age < s.age) return;
        step++;
        if (s.camera != null) mc.options.setCameraType(s.camera);
        if (s.action != null) onServer(mc, s.action);
        if (s.shot != null) { shotAt = age + 3; shot = "prologue-" + s.shot + ".png"; }
        System.out.println("PROLOGUE_STEP " + step + " " + phase + " at " + age);
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post e) {
        if (pending == null) return;
        Minecraft mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(out.getParent());
            image.writeToFile(out);
            System.out.println("PROLOGUE_CAPTURE " + pending);
        } catch (Exception ex) { throw new RuntimeException(ex); }
        pending = null;
    }
}
