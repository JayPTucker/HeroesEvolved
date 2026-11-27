package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.client.effects.SpeedCyclone;
import com.jayptucker.heroesevolved.client.effects.TempestCamera;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.flight.FlightService;
import com.jayptucker.heroesevolved.network.PlayerPowerSyncService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.speed.SpeedKit;
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
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * What a speedster sees while they run a Cyclone: the view glides out from their own eyes, frames the ring as they
 * scroll from the slowest setting to the fastest (a boom ring as they break into a faster pace, a skid as they ease
 * off), glides further out as the tornado rises, and glides home when they step out of it. Then the same for Flight's
 * Tempest: its flyer lapping the eye while the funnel forms (its plan lighting ring by ring), forming, and breaking up
 * against a pillar on a second try. Shot from the player's own camera (no other camera entity), except two side views held with
 * {@link TempestCamera#fix}. Reports FAIL if the view is not pulled back for a ring, or does not come home.
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class CycloneCameraPreview {
    private static boolean started;
    private static int ticks, worldTicks;
    private static String pending;
    private static Vec3 center;
    private static final List<LivingEntity> MOBS = new ArrayList<>();

    private static void onServer(Minecraft mc, Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> action.accept(server.getPlayerList().getPlayer(mc.player.getUUID())));
    }

    private static void place(ServerPlayer p, double x, double z, float yaw) {
        var world = p.getServer().overworld();
        for (int cx = ((int) x - 48) >> 4; cx <= ((int) x + 48) >> 4; cx++)
            for (int cz = ((int) z - 48) >> 4; cz <= ((int) z + 48) >> 4; cz++)
                world.setChunkForced(cx, cz, true);
        p.teleportTo(world, x + 0.5, -60, z + 0.5, yaw, 0);
        PreviewQuiet.report("CYCLONE_CAMERA placed in " + p.level().dimension().location() + " at " + p.blockPosition()
                + " on " + world.getBlockState(p.blockPosition().below()));
    }

    private static void spawn(ServerPlayer p, double x, double z) {
        Mob mob = EntityType.PILLAGER.create(p.serverLevel());
        mob.setPersistenceRequired();
        mob.moveTo(x, -60, z, 180, 0);
        p.serverLevel().addFreshEntity(mob);
        MOBS.add(mob);
    }

    private static void grant(ServerPlayer p, net.minecraft.resources.ResourceLocation power) {
        p.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(power).unlock(power));
        p.setData(ModDataAttachments.POWER_INTRO_HISTORY, p.getData(ModDataAttachments.POWER_INTRO_HISTORY).with(power));
        PlayerProgressionService.setLevel(p, 5);
        PlayerEnergyService.restore(p, 999);
        PlayerPowerSyncService.sync(p);
    }

    /** Scrolls the Cyclone's speed by this many settings, as the wheel does. */
    private static void scroll(Minecraft mc, int by) {
        onServer(mc, p -> { for (int i = 0; i < Math.abs(by); i++) SpeedKit.cycloneSpeed(p, Integer.signum(by)); });
    }

    private static void shot(String name) { pending = "cyclone-" + name + ".png"; }

    /** What the view and the runner are doing, for the log. */
    private static void state(Minecraft mc, String what) {
        var s = TempestCamera.shot(1);
        SpeedCyclone.View mine = SpeedCyclone.views().stream().filter(v -> v.entity == mc.player.getId()).findFirst().orElse(null);
        long now = mc.level.getGameTime();
        PreviewQuiet.report("CYCLONE_CAMERA " + what + " t=" + worldTicks + " watching=" + TempestCamera.watching()
                + " camera=" + (s == null ? "own" : String.format("%.1f %.1f %.1f yaw %.0f pitch %.0f", s.at().x, s.at().y, s.at().z, s.yaw(), s.pitch()))
                + " type=" + mc.options.getCameraType()
                + (mine == null ? "" : String.format(" style=%d omega=%.3f pace=%.2f rose=%s", mine.style, mine.omega(mine.age(now, 0)),
                mine.pace(mine.age(now, 0)), mine.rose())));
    }

    private static void expect(boolean ok, String what) { if (!ok) PreviewQuiet.report("CYCLONE_CAMERA FAIL " + what + " at t=" + worldTicks); }

    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        if (!Boolean.getBoolean("heroes.preview.cycloneCamera")) return;
        var mc = Minecraft.getInstance();
        ticks++;
        if (!started && ticks > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true;
            mc.options.hideGui = true;
            mc.options.pauseOnLostFocus = false;
            var rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            rules.getRule(GameRules.RULE_MOBGRIEFING).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("CycloneCamera-" + System.currentTimeMillis(),
                    new LevelSettings("Cyclone camera preview", GameType.SURVIVAL, false, Difficulty.NORMAL, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(77L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        worldTicks++;
        if (worldTicks % 40 == 0 && worldTicks > 200) onServer(mc, p -> PlayerEnergyService.restore(p, 999));
        switch (worldTicks) {
            // End the Last Day and let the new player out of their pod before anything is staged.
            case 170 -> onServer(mc, com.jayptucker.heroesevolved.story.prologue.PrologueService::skip);
            case 180 -> {
                com.jayptucker.heroesevolved.network.ClientScreenTintState.clear();
                onServer(mc, com.jayptucker.heroesevolved.story.bunker.CryoChamberService::release);
            }
            case 200 -> onServer(mc, p -> {
                p.getServer().overworld().setDayTime(4500);
                grant(p, ModAbilities.SUPERSPEED_ID);
                p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 300, 4, false, false));
                place(p, 300, 0, 0);
            });
            case 212 -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                onServer(mc, p -> {
                    for (double[] at : new double[][] {{303, 2}, {297, 3}, {300, 5}, {304, -2}, {296, -3}}) spawn(p, at[0] + 0.5, at[1] + 0.5);
                });
            }
            // The Cyclone, held, from the slowest setting: the view glides out of first person to frame the ring.
            case 218 -> center = mc.player.position();
            case 220 -> onServer(mc, p -> { SpeedKit.cyclone(p, 5, true); for (int i = 0; i < 6; i++) SpeedKit.cycloneSpeed(p, -1); });
            case 224 -> { shot("01-glide-out"); state(mc, "glide-out"); }
            case 242 -> { shot("02-ring-speed1"); state(mc, "speed-1"); expect(TempestCamera.watching(), "the view is pulled back for a ring with no tornado"); }
            case 246 -> scroll(mc, 2);
            case 262 -> { shot("03-ring-speed3"); state(mc, "speed-3"); }
            case 266 -> scroll(mc, 3);
            case 268 -> { shot("04-surge"); state(mc, "surge"); }
            case 284 -> { shot("05-ring-speed6"); state(mc, "speed-6"); }
            case 287 -> shot("05b-ring-speed6");
            // From the side, low: the fastest laps, the skid of easing off, and the slowest.
            case 290 -> TempestCamera.fix(center.add(0, 2.4, -13.5), center.add(0, 1.0, 0));
            case 292 -> { shot("06-side-speed6"); state(mc, "side-6"); }
            case 294 -> scroll(mc, -5);
            case 297 -> { shot("06b-side-brake"); state(mc, "side-brake"); }
            case 316 -> { shot("07-side-speed1"); state(mc, "side-1"); }
            case 318 -> { TempestCamera.fix(null, null); scroll(mc, 5); }
            case 336 -> { shot("08-ring-speed6-again"); state(mc, "speed-6-again"); }
            // Held long enough, it rises: the view glides further out and up with the funnel.
            case 416 -> { shot("09-tornado-rising"); state(mc, "rising"); }
            case 432 -> { shot("10-tornado-growing"); state(mc, "growing"); }
            case 458 -> { shot("11-tornado"); state(mc, "tornado"); expect(TempestCamera.watching(), "the view is pulled back for the tornado"); }
            // Anchored, let go: the runner steps out and the view comes home to first person.
            case 478 -> onServer(mc, SpeedKit::releaseCyclone);
            case 481 -> { shot("12-coming-home"); state(mc, "coming-home"); }
            case 486 -> { shot("12b-coming-home"); state(mc, "coming-home-2"); }
            case 504 -> {
                shot("13-home"); state(mc, "home");
                expect(!TempestCamera.watching() && TempestCamera.shot(1) == null, "the view has come home");
                expect(mc.options.getCameraType() == CameraType.FIRST_PERSON, "first person is back");
            }
            // Flight's Tempest: the glide out, the flyer's tight laps forming the funnel (its plan lighting from the
            // bottom), the funnel forming and pulling, its finale and home again.
            case 510 -> onServer(mc, p -> {
                MOBS.forEach(LivingEntity::discard); MOBS.clear();
                // The anchored tornado left standing above would share this player's view on every client: end it first.
                com.jayptucker.heroesevolved.speed.SpeedService.forget(p);
                grant(p, ModAbilities.FLIGHT_ID);
                place(p, 300, 60, 0);
                for (double[] at : new double[][] {{303, 72}, {297, 74}, {301, 70}}) spawn(p, at[0] + 0.5, at[1] + 0.5);
            });
            case 520 -> onServer(mc, FlightService::startCyclone);
            case 524 -> { shot("14-tempest-glide-out"); state(mc, "tempest-glide-out"); }
            default -> { }
        }
        tempest(mc);
    }

    private static int lapsAt = -1, formedAt = -1, brokeAt = -1;

    /** The Tempest's shots, timed by what the client sees: laps begun, funnel formed, wind broken up. */
    private static void tempest(Minecraft mc) {
        if (worldTicks < 530) return;
        SpeedCyclone.View mine = SpeedCyclone.views().stream()
                .filter(v -> v.entity == mc.player.getId() && v.style == com.jayptucker.heroesevolved.network.SpeedCyclonePayload.FLIGHT)
                .findFirst().orElse(null);
        long now = mc.level.getGameTime();
        if (brokeAt < 0 && lapsAt < 0 && mine != null && mine.formStart != Long.MIN_VALUE) {
            lapsAt = worldTicks;
            PreviewQuiet.report("CYCLONE_CAMERA tempest laps began t=" + worldTicks);
        }
        if (brokeAt < 0 && formedAt < 0 && mine != null && mine.rose()) {
            formedAt = worldTicks;
            PreviewQuiet.report("CYCLONE_CAMERA tempest formed t=" + worldTicks + " after " + (worldTicks - lapsAt) + " ticks of laps");
            expect(worldTicks - lapsAt >= 50, "the funnel formed only after seconds of laps");
        }
        if (lapsAt >= 0 && formedAt < 0 && brokeAt < 0) {
            int laps = worldTicks - lapsAt;
            if (laps == 6) { shot("15-tempest-laps-begin"); state(mc, "tempest-laps-begin"); expect(TempestCamera.watching(), "the view is pulled back while it forms"); }
            if (laps == 26) { shot("15b-tempest-forming"); state(mc, "tempest-forming"); expect(mine != null && !mine.rose(), "no funnel yet, a second into the laps"); }
            if (laps == 46) { shot("15c-tempest-nearly"); state(mc, "tempest-nearly"); }
        }
        if (formedAt >= 0 && brokeAt < 0) {
            int since = worldTicks - formedAt;
            if (since == 5) { shot("16-tempest-formed"); state(mc, "tempest-formed"); }
            if (since == 30) { shot("16b-tempest-standing"); state(mc, "tempest-standing"); }
            if (since == 60) { shot("16c-tempest"); state(mc, "tempest-3"); }
            if (since == 135) {
                shot("17-tempest-home"); state(mc, "tempest-home");
                expect(!TempestCamera.watching(), "the view has come home after a Tempest");
            }
            // A second Tempest with a pillar standing in the laps: it breaks up before it forms.
            if (since == 140) onServer(mc, p -> {
                MOBS.forEach(LivingEntity::discard); MOBS.clear();
                place(p, 300, 120, 0);
                var world = p.serverLevel();
                var ground = p.blockPosition();
                // The eye will be 6.5 ahead; the laps run 4.8 out from it, 8 up. A short wall across that circle, to the
                // side, reaching from 4 to 7.5 blocks out so the laps meet it while they are still spiralling in.
                for (int y = 0; y < 14; y++) for (int dx = -8; dx <= -4; dx++) for (int dz = 6; dz <= 7; dz++)
                    world.setBlock(ground.offset(dx, y, dz), net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState(), 3);
            });
            if (since == 150) { brokeAt = Integer.MAX_VALUE; onServer(mc, FlightService::startCyclone); }
        }
        if (brokeAt == Integer.MAX_VALUE && worldTicks % 5 == 0) PreviewQuiet.report("CYCLONE_CAMERA pillar run t=" + worldTicks
                + (mine == null ? " no view" : " ended=" + mine.ended() + " rose=" + mine.rose() + " lapping=" + (mine.formStart != Long.MIN_VALUE)
                + String.format(" flyer %.1f %.1f %.1f from %.1f %.1f %.1f", mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                mine.center.x, mine.center.y, mine.center.z)));
        if (brokeAt == Integer.MAX_VALUE && mine != null && mine.ended() && !mine.rose()) {
            brokeAt = worldTicks;
            shot("18-tempest-broke"); state(mc, "tempest-broke");
        }
        if (brokeAt > 0 && brokeAt != Integer.MAX_VALUE) {
            int since = worldTicks - brokeAt;
            if (since == 4) shot("18b-tempest-broke");
            if (since == 40) { shot("19-broke-home"); state(mc, "broke-home"); PreviewQuiet.quit(); }
        }
        if (worldTicks > 1100) { expect(false, "the Tempest shots finished in time (laps " + lapsAt + ", formed " + formedAt + ", broke " + brokeAt + ")"); PreviewQuiet.quit(); }
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post e) {
        if (pending == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(out.getParent());
            image.writeToFile(out);
            System.out.println("CYCLONE_CAMERA_CAPTURE " + pending + " fps=" + mc.getFps());
        } catch (Exception ex) { throw new RuntimeException(ex); }
        pending = null;
    }
}
