package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.client.effects.SpeedOverrunClient;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.network.OverrunPayload;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.speed.SpeedKit;
import com.jayptucker.heroesevolved.speed.SpeedOverrun;
import com.jayptucker.heroesevolved.speed.SpeedService;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Cyclone's runner and tornado, the gust of a sudden stop, and outrunning the last minute, in daylight in a
 * flat world. Some shots are taken from a watcher (an invisible armour stand) standing outside the ring.
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class SpeedRunPreview {
    private static boolean started, running;
    private static int ticks, worldTicks, crossedAt = -1, breachAt = -1, wakeShots;
    private static String pending;
    private static volatile int watcher = -1;
    private static Vec3 last;
    private static final List<LivingEntity> MOBS = new ArrayList<>();

    private static void onServer(Minecraft mc, Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> action.accept(server.getPlayerList().getPlayer(mc.player.getUUID())));
    }

    private static void place(ServerPlayer p, double x, double z, float yaw, float pitch) {
        p.teleportTo(p.serverLevel(), x + 0.5, -60, z + 0.5, yaw, pitch);
    }

    private static <T extends Mob> T spawn(ServerPlayer p, EntityType<T> type, double x, double z, boolean ai) {
        T mob = type.create(p.serverLevel());
        mob.setNoAi(!ai); mob.setPersistenceRequired();
        mob.moveTo(x, -60, z, 180, 0);
        p.serverLevel().addFreshEntity(mob);
        MOBS.add(mob);
        return mob;
    }

    private static void clearMobs() { MOBS.forEach(LivingEntity::discard); MOBS.clear(); }

    /** A camera standing at {@code at}, looking along yaw/pitch. */
    private static void stand(ServerPlayer p, Vec3 at, float yaw, float pitch) {
        var level = p.serverLevel();
        if (watcher >= 0 && level.getEntity(watcher) != null) level.getEntity(watcher).discard();
        var stand = new ArmorStand(EntityType.ARMOR_STAND, level);
        stand.setInvisible(true); stand.setNoGravity(true); stand.setInvulnerable(true);
        stand.moveTo(at.x, at.y, at.z, yaw, pitch);
        stand.setYHeadRot(yaw);
        level.addFreshEntity(stand);
        watcher = stand.getId();
    }

    private static void look(Minecraft mc, boolean fromWatcher) {
        var e = fromWatcher && watcher >= 0 ? mc.level.getEntity(watcher) : null;
        mc.setCameraEntity(e != null ? e : mc.player);
    }

    private static void sprint(Minecraft mc, boolean on) {
        mc.options.keyUp.setDown(on);
        mc.options.keySprint.setDown(on);
        if (mc.player != null) mc.player.setSprinting(on);
    }

    private static void shot(String name) { pending = "run-" + name + ".png"; }

    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        if (!Boolean.getBoolean("heroes.preview.speedrun")) return;
        var mc = Minecraft.getInstance();
        ticks++;
        if (!started && ticks > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            // The new world's story captions would cover the effects.
            mc.options.hideGui = true;
            mc.options.pauseOnLostFocus = false;
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            rules.getRule(GameRules.RULE_MOBGRIEFING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("SpeedRun-" + System.currentTimeMillis(),
                    new LevelSettings("Speed run preview", GameType.SURVIVAL, false, Difficulty.NORMAL, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(77L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        worldTicks++;
        if (worldTicks % 80 == 0 && worldTicks > 200) onServer(mc, p -> PlayerEnergyService.restore(p, 999));
        // A circle about 150 blocks across keeps the watcher's chunk loaded through the run.
        if (running) { mc.player.setYRot(mc.player.getYRot() + 2F); overrunShots(mc); }
        // What the server sees while running: Momentum, flat out or not, and Overrun's charge.
        if ((worldTicks >= 484 && worldTicks < 540 || running) && worldTicks % 10 == 0) onServer(mc, p -> System.out.println(
                "SPEEDRUN_STATE t=" + worldTicks + " momentum=" + SpeedService.momentum(p) + " flatOut=" + SpeedService.flatOut(p, SpeedOverrun.TOP_SPEED, SpeedOverrun.TOP_MOMENTUM)
                        + " charge=" + SpeedOverrun.charge(p) + " breach=" + SpeedOverrun.breachOpen(p) + " sprint=" + p.isSprinting()
                        + " food=" + p.getFoodData().getFoodLevel() + "/" + p.getFoodData().getSaturationLevel()));
        switch (worldTicks) {
            case 200 -> onServer(mc, p -> {
                p.serverLevel().setDayTime(4500);
                p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty()
                        .assignDormant(ModAbilities.SUPERSPEED_ID).unlock(ModAbilities.SUPERSPEED_ID));
                p.setData(ModDataAttachments.POWER_INTRO_HISTORY, p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(ModAbilities.SUPERSPEED_ID));
                PlayerProgressionService.setLevel(p, 5);
                p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 300, 4, false, false));
                PlayerEnergyService.restore(p, 999);
                place(p, 300, 0, 0, 4);
                stand(p, new Vec3(300.5, -56, -11.5), 0, 16);
            });
            // Cyclone: the runner on the ring, seen from behind, from outside, and from the eye.
            case 214 -> onServer(mc, p -> {
                for (double[] at : new double[][] {{303, 2}, {297, 3}, {300, 5}, {304, -2}, {296, -3}}) spawn(p, EntityType.PILLAGER, at[0] + 0.5, at[1] + 0.5, true);
            });
            case 220 -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); onServer(mc, p -> SpeedKit.cyclone(p, 5)); }
            case 230 -> shot("1-ring-behind");
            case 233 -> look(mc, true);
            case 236 -> shot("2-ring-outside");
            case 241 -> shot("2b-ring-outside");
            case 246 -> { look(mc, false); mc.options.setCameraType(CameraType.FIRST_PERSON); }
            case 249 -> shot("3-ring-eye");
            case 283 -> { look(mc, true); shot("4-ring-breaks"); }
            case 290 -> { look(mc, false); onServer(mc, p -> clearMobs()); }
            // Tornado: held, it rises; watched from far off, from the eye, from behind; then let go.
            case 300 -> onServer(mc, p -> {
                place(p, 300, 0, 0, 4);
                stand(p, new Vec3(300.5, -52, -38.5), 0, -4);
                for (double[] at : new double[][] {{304, 2}, {296, 4}, {301, 6}, {305, -3}, {295, -4}, {299, -6}}) spawn(p, EntityType.PILLAGER, at[0] + 0.5, at[1] + 0.5, true);
            });
            case 306 -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); onServer(mc, p -> SpeedKit.cyclone(p, 5, true)); }
            case 308 -> look(mc, true);
            case 345 -> shot("5-tornado-rising");
            case 362 -> shot("5b-tornado-growing");
            case 395 -> shot("6-tornado");
            case 420 -> shot("6b-tornado-carrying");
            case 424 -> { look(mc, false); mc.options.setCameraType(CameraType.FIRST_PERSON); onServer(mc, p -> p.setXRot(-48)); }
            case 428 -> shot("7-tornado-eye");
            case 432 -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); onServer(mc, p -> p.setXRot(-12)); }
            case 436 -> shot("8-tornado-behind");
            case 440 -> look(mc, true);
            case 444 -> onServer(mc, SpeedKit::releaseCyclone);
            case 450 -> shot("9-tornado-down");
            case 460 -> shot("9b-tornado-down");
            case 476 -> { look(mc, false); onServer(mc, p -> clearMobs()); }
            // A sudden stop: run, then let go of the keys; pillagers stand where the run ends.
            case 480 -> onServer(mc, p -> { place(p, 300, 60, 0, 4); SpeedService.toggle(p); });
            case 484 -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); sprint(mc, true); }
            case 524 -> onServer(mc, p -> {
                Vec3 ahead = p.position().add(0, 0, 6);
                spawn(p, EntityType.SHEEP, ahead.x + 1.1, ahead.z + 0.5, true);
                spawn(p, EntityType.SHEEP, ahead.x - 1.3, ahead.z + 1.5, true);
                spawn(p, EntityType.SHEEP, ahead.x + 0.2, ahead.z + 2.8, true);
            });
            case 525 -> sprint(mc, false);
            case 529 -> shot("10-stop");
            case 532 -> shot("10b-stop");
            case 537 -> shot("10c-stop");
            case 539 -> { look(mc, false); onServer(mc, p -> { clearMobs(); SpeedService.stop(p); }); }
            // Outrunning the last minute: a minute of history to go back to, then twenty seconds flat out.
            case 541 -> onServer(mc, p -> {
                place(p, 300, 120, 0, 4);
                stand(p, new Vec3(300.5, -57.4, 128.5), 180, 14);
            });
            case 546 -> onServer(mc, p -> {
                long now = p.getServer().overworld().getGameTime();
                long base = now - now % SpeedOverrun.SAMPLE_TICKS - SpeedOverrun.REWIND_TICKS - 100;
                // A fed runner, as from a full bar and a little saturation.
                p.getFoodData().setFoodLevel(20); p.getFoodData().setSaturation(5);
                for (long t = base; t < now; t += SpeedOverrun.SAMPLE_TICKS) SpeedOverrun.recordAt(p, t);
                SpeedService.toggle(p);
            });
            case 552 -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); sprint(mc, true); running = true; last = mc.player.position(); }
            case 1500 -> { sprint(mc, false); running = false; mc.stop(); }
            default -> { }
        }
    }

    /** Shots chosen by what the server says: the wake growing, the breach open and closing in, then arriving. */
    private static void overrunShots(Minecraft mc) {
        Vec3 here = mc.player.position();
        last = here;
        // Crossed: the breach was open and is not any more (the runner never eases off here, so it did not close).
        var mine = SpeedOverrunClient.states().stream().filter(s -> s.entity == mc.player.getId()).findFirst().orElse(null);
        boolean jumped = breachAt >= 0 && (mine == null || mine.stage != OverrunPayload.BREACH);
        if (jumped && crossedAt < 0) {
            crossedAt = worldTicks;
            sprint(mc, false);
            System.out.println("OVERRUN_CROSSED at " + worldTicks);
        }
        if (crossedAt >= 0) {
            mc.player.setYRot(mc.player.getYRot() - 2F);
            int since = worldTicks - crossedAt;
            // First the trip back through the minute (the view is carried back along the path), then arriving.
            switch (since) {
                case 6 -> shot("15-travel");
                case 24 -> shot("15b-travel");
                case 44 -> shot("15c-travel");
                case 66 -> { look(mc, true); System.out.println("TRAVEL_CAMERA " + mc.getCameraEntity()); }
                case 68 -> shot("16-arrive");
                case 80 -> shot("16b-arrive");
                case 96 -> shot("16c-arrive");
                case 104 -> { look(mc, false); mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); }
                case 108 -> shot("17-arrive-behind");
                case 130 -> { running = false; mc.stop(); }
                default -> { }
            }
            return;
        }
        var state = SpeedOverrunClient.states().stream().filter(s -> s.entity == mc.player.getId()).findFirst().orElse(null);
        if (state == null) return;
        if (state.stage == OverrunPayload.WAKE && state.value >= 550 && wakeShots == 0) { wakeShots++; shot("11-wake"); }
        else if (state.stage == OverrunPayload.WAKE && state.value >= 820 && wakeShots == 1) { wakeShots++; mc.options.setCameraType(CameraType.FIRST_PERSON); }
        else if (state.stage == OverrunPayload.WAKE && state.value >= 840 && wakeShots == 2) { wakeShots++; shot("12-wake-ahead"); }
        else if (state.stage == OverrunPayload.WAKE && state.value >= 880 && wakeShots == 3) { wakeShots++; mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); }
        if (state.stage == OverrunPayload.BREACH && breachAt < 0) breachAt = worldTicks;
        if (breachAt >= 0) {
            int open = worldTicks - breachAt;
            if (open == 12) shot("13-breach");
            if (open == 26) shot("13b-breach");
            if (open == 30) mc.options.setCameraType(CameraType.FIRST_PERSON);
            if (open == 36) shot("14-breach-near");
            if (open == 50) shot("14b-breach-nearer");
        }
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post e) {
        if (pending == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(out.getParent()); image.writeToFile(out);
            System.out.println("SPEEDRUN_CAPTURE " + pending + " fps=" + mc.getFps());
        } catch (Exception ex) { throw new RuntimeException(ex); }
        pending = null;
    }
}
