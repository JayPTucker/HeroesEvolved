package com.jayptucker.heroesevolved.trailer;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.cooldown.CooldownService;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.expansion.Signatures;
import com.jayptucker.heroesevolved.network.PlayerPowerSyncService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.pyro.PyroCombat;
import com.jayptucker.heroesevolved.pyro.PyroService;
import com.jayptucker.heroesevolved.skills.SkillTreeData;
import com.jayptucker.heroesevolved.story.bunker.CryoChamberService;
import com.jayptucker.heroesevolved.story.prologue.PrologueService;
import com.jayptucker.heroesevolved.uitest.PreviewQuiet;
import net.minecraft.client.CameraType;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Films the trailer's shots (see {@code docs/trailer/storyboard.md}, the catalog in {@link Shots}) as video frames in
 * {@code build/trailer-preview/film/<shot>/}. Flag {@code heroes.preview.trailer}; {@code tools/trailer-preview.init.gradle},
 * {@code -Pshots=10-ballista,13-epicenter} for some of them (all by default).
 *
 * <p>It makes a normal world (not a flat one: a trailer needs land and sky), scouts a hilltop that falls away to the
 * west ({@link Scout}), waits for every chunk in view to be drawn, then slows the game to {@code heroes.trailer.rate}
 * ticks a second and films each shot in turn on that stage with {@link FilmCamera} and {@link FilmRecorder}: the last
 * shot cleared away and the power changed, the server's clock brought into step with the client's, the scene cast, a
 * roll, the shot's ticks, a cut.</p>
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class TrailerPreview {
    private static final String FLAG = "heroes.preview.trailer";
    /**
     * Ticks a second while filming: the game runs 20 / RATE times slower than real time so every frame is captured. It
     * stays the same from the first shot to the last, slow motion included (slow enough that a 0.3-speed frame still
     * blends several captures): a rate changed mid-shot reaches the client later than the server, and the two drift.
     */
    private static final float RATE = Float.parseFloat(System.getProperty("heroes.trailer.rate", "1.5"));
    private static final long SEED = Long.getLong("heroes.trailer.seed", 20261127L);

    private enum Phase { BOOT, STAGING, LOADING, SETTING, FILMING, DONE }

    private static Phase phase = Phase.BOOT;
    private static int boot, age, phaseAge, t;
    private static volatile boolean staged, settled, failed;
    /** The tick rate last asked of the server while filming. */
    private static float rate = 20;
    /** The server's tick count on each of the last few client ticks, to tell when it keeps steady time. */
    private static final ArrayDeque<Integer> SERVER_TICKS = new ArrayDeque<>();
    private static int castAt = -1;

    /** The shots to film (from {@code heroes.trailer.shots}, or every one), and the one being filmed. */
    private static List<Shot> queue;
    private static int shotIndex;
    private static volatile Shot current;
    /**
     * The shot's moves run on the server's own ticks, counted from the roll (-1 when not filming); the runner brings the
     * server's clock into step with the client's before each roll, so a shot's ticks are the same on both.
     */
    private static volatile int serverShot = -1;
    private static volatile java.util.UUID hero;

    private TrailerPreview() {}

    private static void server(Minecraft mc, Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        var id = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) PreviewQuiet.guard("trailer", () -> action.accept(player));
        });
    }

    private static void next(Phase to) {
        phase = to;
        phaseAge = 0;
        System.out.println("TRAILER_PHASE " + to + " at tick " + age + (current != null && to == Phase.SETTING ? " for " + current.name : ""));
    }

    private static List<Shot> chosen() {
        String wanted = System.getProperty("heroes.trailer.shots", "").trim();
        List<Shot> all = Shots.all();
        if (wanted.isEmpty() || wanted.equals("all")) return all;
        Set<String> names = Set.of(wanted.split(","));
        List<Shot> picked = all.stream().filter(s -> names.contains(s.name) || names.contains(s.name.replaceFirst("^\\d+-", ""))).toList();
        if (picked.size() != names.size())
            PreviewQuiet.report("TRAILER FAIL unknown shot in -Pshots=" + wanted + "; the shots are "
                    + all.stream().map(s -> s.name).toList());
        return picked;
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean(FLAG)) return;
        var mc = Minecraft.getInstance();
        if (phase == Phase.BOOT) {
            if (++boot >= 40 && mc.screen != null && mc.getOverlay() == null) {
                queue = chosen();
                settings(mc);
                createWorld(mc);
                next(Phase.STAGING);
            }
            return;
        }
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null || mc.getOverlay() != null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        mc.getToasts().clear();
        age++;
        phaseAge++;
        switch (phase) {
            case STAGING -> {
                if (phaseAge == 60) server(mc, TrailerPreview::stage);
                if (failed) next(Phase.DONE);
                else if (staged) next(Phase.LOADING);
                else if (phaseAge > 6000) { PreviewQuiet.report("TRAILER FAIL never found a place to film"); next(Phase.DONE); }
            }
            case LOADING -> {
                if (!settled && phaseAge % 20 == 0) server(mc, TrailerPreview::settle);
                // Wait for the land in view to be built and drawn, or the first frames show holes in the world.
                boolean drawn = mc.levelRenderer.hasRenderedAllSections();
                if (failed || queue.isEmpty()) next(Phase.DONE);
                else if (settled && (phaseAge >= 300 && drawn || phaseAge > 2000)) {
                    if (!drawn) System.out.println("TRAILER_NOTE filming before every section was drawn");
                    current = queue.get(shotIndex = 0);
                    next(Phase.SETTING);
                }
            }
            case SETTING -> setting(mc);
            case FILMING -> filming(mc);
            case DONE -> {
                if (phaseAge == 10) PreviewQuiet.quit();
            }
            default -> { }
        }
    }

    /** Before a shot: clear the last one away, change the power, bring the clocks into step, cast the scene, roll. */
    private static void setting(Minecraft mc) {
        Shot shot = current;
        if (phaseAge == 1) {
            mc.options.hideGui = true;
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            pace(mc, RATE);
            castAt = -1;
            SERVER_TICKS.clear();
            server(mc, p -> reset(p, shot));
        }
        // Roll only once the server keeps time with the client: its backlog forgiven (see behind), then a tick of its
        // own for every tick of the client's, and under a second behind.
        if (phaseAge == 4 || phaseAge > 4 && castAt < 0 && phaseAge % 20 == 0) server(mc, TrailerPreview::resync);
        SERVER_TICKS.addLast(mc.getSingleplayerServer().getTickCount());
        if (SERVER_TICKS.size() > 9) SERVER_TICKS.removeFirst();
        double late = behind(mc);
        boolean steady = SERVER_TICKS.size() == 9 && Math.abs(SERVER_TICKS.peekLast() - SERVER_TICKS.peekFirst() - 8) <= 1
                && (Double.isNaN(late) || late < 1000);
        if (castAt < 0 && phaseAge > 8 && (steady || phaseAge > 200)) {
            if (!steady) PreviewQuiet.report("TRAILER FAIL the server never kept steady time (" + late + " ms behind)");
            System.out.printf(Locale.ROOT, "TRAILER_STEADY %s after %d ticks: server ticks %s, %.0f ms behind%n", shot.name, phaseAge, SERVER_TICKS, late);
            server(mc, shot::cast);
            castAt = phaseAge;
        }
        // A few ticks for the cast to reach the client.
        if (castAt > 0 && phaseAge == castAt + 3) {
            FilmCamera.play(shot.camera(), shot.follow(mc), shot.followRate(), shot.near());
            FilmRecorder.roll(shot.name, shot::speed);
            server(mc, p -> { hero = p.getUUID(); serverShot = 0; });
            t = 0;
            next(Phase.FILMING);
        }
    }

    private static void filming(Minecraft mc) {
        Shot shot = current;
        shot.watch(mc, t + 1);
        for (Runnable job; (job = Stage.ON_CLIENT.poll()) != null; ) job.run();
        t++;
        if (t % 20 == 0) System.out.printf(Locale.ROOT, "TRAILER_CLOCK %s client %d server %d film %.2f shot %.2f behind %.0f ms, mspt %.1f%n",
                shot.name, t, serverShot, FilmCamera.now(), FilmRecorder.shotTime(), behind(mc),
                mc.getSingleplayerServer().getAverageTickTimeNanos() / 1e6);
        if (t < shot.length) return;
        serverShot = -1;
        FilmRecorder.cut();
        FilmCamera.stop();
        if (++shotIndex < queue.size()) {
            current = queue.get(shotIndex);
            next(Phase.SETTING);
        } else {
            pace(mc, 20);
            server(mc, Stage::clear);
            next(Phase.DONE);
        }
    }

    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event) {
        Shot shot = current;
        if (serverShot < 0 || hero == null || shot == null) return;
        ServerPlayer p = event.getServer().getPlayerList().getPlayer(hero);
        if (p != null) PreviewQuiet.guard("trailer " + shot.name, () -> shot.act(p, ++serverShot));
    }

    // ------------------------------------------------------------------ Between shots

    /**
     * The fighter back on the stage with nothing left of the last shot: its actors and leftovers gone, every power's
     * running state forgotten, full health and stamina, no cooldowns, the shot's time of day.
     */
    private static void reset(ServerPlayer p, Shot shot) {
        com.jayptucker.heroesevolved.combat.Ultimates.stop(p);
        Stage.clear(p);
        Signatures.forget(p);
        PyroService.forget(p);
        PyroCombat.forget(p);
        CooldownService.clearAll(p);
        PlayerEnergyService.restore(p, 999);
        p.removeAllEffects();
        p.clearFire();
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 600, 4, false, false));
        p.setHealth(p.getMaxHealth());
        p.getFoodData().setFoodLevel(20);
        p.server.overworld().setDayTime(shot.time);
        Stage.place(p, 0, 0);
    }

    /**
     * Gives the fighter {@code power} (with skill-tree {@code node} learned, if any) the way the other previews do, its
     * intro already seen, and drops the last power's drawn state as a real power change does. A Power Level of -1 sets
     * the tier the signature preview uses instead.
     */
    static void grant(ServerPlayer p, ResourceLocation power, String node, int powerLevel) {
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(power).unlock(power));
        p.setData(ModDataAttachments.POWER_INTRO_HISTORY, p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(power));
        com.jayptucker.heroesevolved.hydro.HydroService.powerChanged(p);
        if (powerLevel > 0) PlayerProgressionService.setPowerLevel(p, powerLevel);
        else PlayerProgressionService.setLevel(p, 5);
        p.setData(ModDataAttachments.SKILL_TREES, node == null ? SkillTreeData.empty()
                : SkillTreeData.empty().with(power.toString(), new SkillTreeData.Build(15, Set.of(node), Map.of())));
        PlayerEnergyService.restore(p, 999);
        PlayerPowerSyncService.sync(p);
    }

    // ------------------------------------------------------------------ The server's clock

    /**
     * How far the server is behind its own tick schedule, in milliseconds (NaN if it can't be read). Loading a new place
     * leaves it many seconds behind, and at the slow filming rate it makes that up in a burst of back-to-back ticks
     * (vanilla forgives a backlog only past about 15 seconds, and at most once a minute or so): one take started 28
     * seconds behind and ran 45 server ticks in its first 10 client ones, everything on the server racing.
     */
    private static double behind(Minecraft mc) {
        try {
            return (net.minecraft.Util.getNanos() - SCHEDULE.getLong(mc.getSingleplayerServer())) / 1e6;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return Double.NaN;
        }
    }

    /** Forgives the server's backlog: its next tick is due now, so it keeps time from here instead of racing. */
    private static void resync(ServerPlayer p) {
        try {
            SCHEDULE.setLong(p.server, net.minecraft.Util.getNanos());
        } catch (ReflectiveOperationException | RuntimeException e) {
            PreviewQuiet.report("TRAILER FAIL could not reset the server's tick schedule: " + e);
        }
    }

    /** {@code MinecraftServer.nextTickTimeNanos}, by reflection: this driver only, never shipped. */
    private static final java.lang.reflect.Field SCHEDULE;
    static {
        java.lang.reflect.Field field = null;
        try {
            field = net.minecraft.server.MinecraftServer.class.getDeclaredField("nextTickTimeNanos");
            field.setAccessible(true);
        } catch (ReflectiveOperationException | RuntimeException e) {
            System.out.println("TRAILER_NOTE no access to the server's tick schedule: " + e);
        }
        SCHEDULE = field;
    }

    /** Sets the game's tick rate (the server's, which the client follows) if it has changed. */
    private static void pace(Minecraft mc, float ticksPerSecond) {
        if (Math.abs(ticksPerSecond - rate) < .05f) return;
        rate = ticksPerSecond;
        server(mc, p -> p.server.tickRateManager().setTickRate(ticksPerSecond));
    }

    // ------------------------------------------------------------------ The world

    private static void settings(Minecraft mc) {
        var o = mc.options;
        o.pauseOnLostFocus = false;
        o.renderDistance().set(12);
        o.graphicsMode().set(GraphicsStatus.FANCY);
        o.cloudStatus().set(CloudStatus.FANCY);
        o.ambientOcclusion().set(true);
        o.entityShadows().set(true);
        o.biomeBlendRadius().set(5);
        o.bobView().set(false);
        o.damageTiltStrength().set(0.0);
        o.fovEffectScale().set(0.0);
        o.particles().set(net.minecraft.client.ParticleStatus.ALL);
        o.framerateLimit().set(260);
        o.enableVsync().set(false);
        o.fov().set(70);
        o.chatVisibility().set(ChatVisiblity.HIDDEN);
        mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
    }

    private static void createWorld(Minecraft mc) {
        var rules = new GameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        mc.createWorldOpenFlows().createFreshLevel("Trailer-" + System.currentTimeMillis(),
                new LevelSettings("Trailer", GameType.SURVIVAL, false, Difficulty.EASY, true, rules, WorldDataConfiguration.DEFAULT),
                new WorldOptions(SEED, false, false),
                access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.NORMAL).createWorldDimensions(), mc.screen);
    }

    /** Out of the bunker and onto the best hilltop the scout can find well away from spawn. */
    private static void stage(ServerPlayer p) {
        PrologueService.skip(p);
        CryoChamberService.release(p);
        ServerLevel world = p.server.overworld();
        world.setWeatherParameters(240000, 0, false, false);
        world.setDayTime(Shots.SUNSET);
        // The scout asks the world generator about thousands of places: off the server thread, so the game keeps ticking.
        var server = p.server;
        var id = p.getUUID();
        // The pick is kept per seed, so a retake skips the scout (about 45 seconds).
        var cache = server.getServerDirectory().resolve("scout-" + SEED + ".txt");
        java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try {
                if (java.nio.file.Files.exists(cache)) {
                    String[] xyz = java.nio.file.Files.readString(cache).trim().split(" ");
                    var ground = new BlockPos(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2]));
                    System.out.println("TRAILER_SCOUT kept from " + cache.getFileName() + ": " + ground);
                    return new Scout.Spot(ground, 0, "kept", 0, 0);
                }
            } catch (java.io.IOException | RuntimeException e) {
                System.out.println("TRAILER_NOTE could not read " + cache + ": " + e);
            }
            Scout.Spot found = Scout.find(world, 400, 2400);
            if (found != null) {
                try { java.nio.file.Files.writeString(cache, found.ground().getX() + " " + found.ground().getY() + " " + found.ground().getZ()); }
                catch (java.io.IOException e) { System.out.println("TRAILER_NOTE could not keep the scout's pick: " + e); }
            }
            return found;
        }).whenComplete((spot, error) -> server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (error != null) {
                PreviewQuiet.report("TRAILER FAIL the scout threw " + error);
                failed = true;
            } else if (player != null) PreviewQuiet.guard("trailer", () -> place(player, spot));
        }));
    }

    private static void place(ServerPlayer p, Scout.Spot spot) {
        ServerLevel world = p.server.overworld();
        if (spot == null) {
            PreviewQuiet.report("TRAILER FAIL no dry ground within 2400 blocks of spawn for seed " + SEED + "; try another -Pseed");
            failed = true;
            return;
        }
        BlockPos ground = spot.ground();
        for (int cx = (ground.getX() >> 4) - 3; cx <= (ground.getX() >> 4) + 3; cx++)
            for (int cz = (ground.getZ() >> 4) - 3; cz <= (ground.getZ() >> 4) + 3; cz++) world.setChunkForced(cx, cz, true);
        p.teleportTo(world, ground.getX() + .5, ground.getY() + 1, ground.getZ() + .5, Stage.FORWARD, 4);
        p.setGameMode(GameType.SURVIVAL);
        p.removeAllEffects();
        Stage.ground = ground;
        staged = true;
    }

    /** Once the stage's chunks exist: clears trees off it and stands the fighter on the real ground. */
    private static void settle(ServerPlayer p) {
        ServerLevel world = p.server.overworld();
        BlockPos at = Stage.ground;
        if (!world.hasChunkAt(at) || !world.hasChunkAt(at.offset(-18, 0, -18)) || !world.hasChunkAt(at.offset(18, 0, 18))) return;
        int cleared = Scout.clearTrees(world, at, 17) + Scout.thinGrass(world, at, 7);
        int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
        Stage.ground = new BlockPos(at.getX(), y, at.getZ());
        p.teleportTo(world, at.getX() + .5, y, at.getZ() + .5, Stage.FORWARD, 4);
        p.setDeltaMovement(Vec3.ZERO);
        p.setHealth(p.getMaxHealth());
        p.getFoodData().setFoodLevel(20);
        Stage.lift = Math.max(0, Scout.highest(world, Stage.ground, 6.5) - y);
        var underfoot = world.getBlockState(Stage.ground.below());
        PreviewQuiet.report("TRAILER_STAGE in " + world.dimension().location() + " at " + Stage.ground + " on "
                + underfoot.getBlock() + ", " + cleared + " tree and grass blocks cleared, ground round it up to " + Stage.lift
                + " higher; filming " + queue.stream().map(s -> s.name).toList());
        if (!underfoot.getFluidState().isEmpty() || p.isInWaterOrBubble()) {
            PreviewQuiet.report("TRAILER FAIL the stage is in water");
            failed = true;
        }
        settled = true;
    }
}
