package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.data.PlayerAbilityData;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.client.ClientTimeFlow;
import com.jayptucker.heroesevolved.client.effects.TempestCamera;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.expansion.AdvancedTechniques;
import com.jayptucker.heroesevolved.gifted.GiftedMobs;
import com.jayptucker.heroesevolved.network.PlayerPowerSyncService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.speed.SpeedService;
import com.jayptucker.heroesevolved.story.bunker.CryoChamberService;
import com.jayptucker.heroesevolved.story.prologue.PrologueService;
import com.jayptucker.heroesevolved.time.PlayerTimeSlow;
import com.jayptucker.heroesevolved.time.TimeSlowService;
import com.jayptucker.heroesevolved.time.TimeStopService;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
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

/**
 * Slowed and stopped time (flag {@code heroes.preview.slowtime}): dust and rain in Between Seconds, a time traveller's
 * blur, a lit creeper and its slow explosion, the rings shots leave in the air, Still World, time resuming, and a
 * speedster running through their own Perception. Shots go to {@code build/slowtime-preview/screenshots/}.
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class SlowTimePreview {
    private static final double Z = 150.5, FLOOR = -60;
    private static boolean started;
    private static int startup, age, exploded = -1;
    private static String capture;
    private static Creeper creeper;
    private static boolean tracking;

    private static void server(Consumer<ServerPlayer> action) {
        var mc = Minecraft.getInstance(); var server = mc.getSingleplayerServer(); var id = mc.player.getUUID();
        server.execute(() -> action.accept(server.getPlayerList().getPlayer(id)));
    }

    private static void camera(double x, double y, double z, double lx, double ly, double lz) {
        Minecraft.getInstance().execute(() -> TempestCamera.fix(new Vec3(x, y, z), new Vec3(lx, ly, lz)));
    }

    private static void power(ServerPlayer player, ResourceLocation power) {
        player.setData(ModDataAttachments.PLAYER_ABILITIES, PlayerAbilityData.empty().assignDormant(power).unlock(power));
        PlayerProgressionService.setLevel(player, 5);
        PlayerPowerSyncService.sync(player);
    }

    private static void stage(ServerPlayer player) {
        PrologueService.skip(player); CryoChamberService.release(player);
        ServerLevel world = player.getServer().overworld();
        for (int x = 7; x <= 11; x++) for (int z = 8; z <= 10; z++) world.setChunkForced(x, z, true);
        // A sand runway across the scene, so what the feet kick up reads against the grass.
        for (int x = 120; x <= 190; x++) for (int dz = -1; dz <= 1; dz++)
            world.setBlockAndUpdate(new BlockPos(x, (int) FLOOR - 1, (int) Math.floor(Z) + dz), Blocks.SAND.defaultBlockState());
        player.teleportTo(world, 150.5, FLOOR, Z, 0, 10);
        power(player, ModAbilities.TIME_MANIPULATION_ID);
        player.getAbilities().invulnerable = true; player.onUpdateAbilities();
        PlayerEnergyService.setUnlimitedEnergy(player, true);
        world.setDayTime(6000);
        world.setWeatherParameters(0, 12000, true, false);
        System.out.println("SLOWTIME_PREVIEW staged " + player.level().dimension().location() + " at " + player.blockPosition()
                + " on " + world.getBlockState(player.blockPosition().below()));
    }

    private static void creeper(ServerPlayer player) {
        ServerLevel world = player.serverLevel();
        creeper = EntityType.CREEPER.create(world);
        creeper.getPersistentData().putBoolean(GiftedMobs.ROLLED, true);
        creeper.setNoAi(true);
        creeper.setPersistenceRequired();
        creeper.moveTo(153.5, FLOOR, 156.5, 160, 0);
        world.addFreshEntity(creeper);
        creeper.ignite();
    }

    private static void shots(ServerPlayer player, double x0) {
        ServerLevel world = player.serverLevel();
        for (int i = 0; i < 3; i++) {
            Arrow arrow = new Arrow(world, x0 - i * 1.4, FLOOR + 1.3 + i * 0.45, Z + 2.6 + i * 0.35, new ItemStack(Items.ARROW), null);
            arrow.shoot(1, 0.02, 0, 3, 0);
            world.addFreshEntity(arrow);
            Snowball ball = new Snowball(world, x0 - 0.7 - i * 1.6, FLOOR + 1.0 + i * 0.5, Z + 3.4 + i * 0.3);
            ball.shoot(1, 0.03, 0, 2.5F, 0);
            world.addFreshEntity(ball);
        }
    }

    private static void press(boolean down) {
        var options = Minecraft.getInstance().options;
        options.keyUp.setDown(down);
        options.keySprint.setDown(down);
    }

    private static void log(String what) {
        var mc = Minecraft.getInstance();
        var p = mc.player;
        System.out.printf("SLOWTIME_PREVIEW %s age=%d pos=%s rate=%.3f scale=%.3f style=%d%n", what, age, p.position(),
                ClientTimeFlow.rate(p.position()), PlayerTimeSlow.movementScale(p), ClientTimeFlow.style(p));
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("heroes.preview.slowtime")) return;
        var mc = Minecraft.getInstance();
        if (!started && ++startup > 40 && mc.screen != null && mc.getOverlay() == null) {
            started = true; mc.options.pauseOnLostFocus = false;
            var rules = new GameRules(); rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("SlowTime-" + System.currentTimeMillis(),
                    new LevelSettings("Slow time", GameType.SURVIVAL, false, Difficulty.EASY, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(91L, false, false), access -> access.registryOrThrow(Registries.WORLD_PRESET)
                            .getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.level == null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        age++;
        if (tracking) {
            // A side camera that runs alongside the speedster.
            Vec3 at = mc.player.position();
            TempestCamera.fix(new Vec3(at.x + 1.5, FLOOR + 1.6, Z - 6.2), new Vec3(at.x + 0.8, FLOOR + 0.8, Z));
        }
        // The creeper's blast in slowed time, photographed as it happens and as it hangs.
        if (creeper != null && exploded < 0 && age > 300 && mc.level.getEntity(creeper.getId()) == null) {
            exploded = age;
            log("creeper exploded");
        }
        if (exploded > 0 && age == exploded + 4) capture = "06-slow-explosion.png";
        if (exploded > 0 && age == exploded + 24) capture = "07-slow-explosion-later.png";
        if (age % 20 == 0 && age > 150) log("tick");
        switch (age) {
            case 160 -> server(SlowTimePreview::stage);
            case 200 -> {
                mc.options.hideGui = true;
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                server(p -> { TimeSlowService.hold(p, 1); creeper(p); });
                camera(150.5, FLOOR + 1.9, Z - 7.5, 150.5, FLOOR + 0.6, Z + 2);
            }
            case 228 -> capture = "01-slowed-dust-rain.png";
            case 232 -> server(p -> p.connection.teleport(141.5, FLOOR, Z, -90, 0));
            case 236 -> press(true);
            case 258 -> capture = "02-traveller-blur.png";
            case 270 -> press(false);
            case 276 -> {
                server(p -> shots(p, 141));
                camera(149, FLOOR + 1.8, Z - 4, 149, FLOOR + 1.6, Z + 3);
            }
            case 306 -> capture = "03-shot-wakes.png";
            case 310 -> camera(143.2, FLOOR + 2.0, Z + 1.2, 148.5, FLOOR + 1.6, Z + 3.2);
            case 316 -> capture = "04-shot-wake-close.png";
            case 322 -> camera(149.8, FLOOR + 1.5, Z + 1.8, 153.5, FLOOR + 0.9, Z + 6);
            case 340 -> capture = "05-creeper-fuse.png";
            case 470 -> server(p -> shots(p, 143));
            case 486 -> {
                server(p -> TimeStopService.start(p, 5));
                camera(149, FLOOR + 1.8, Z - 4, 149, FLOOR + 1.6, Z + 3);
            }
            case 500 -> capture = "08-stopped-rings.png";
            case 504 -> camera(150.5, FLOOR + 1.9, Z - 7.5, 150.5, FLOOR + 0.6, Z + 2);
            case 512 -> capture = "09-stopped-rain-dust.png";
            case 516 -> server(p -> { TimeStopService.stop(p); TimeSlowService.stop(p); });
            case 530 -> capture = "10-resumed.png";
            case 540 -> server(p -> {
                p.serverLevel().setWeatherParameters(12000, 0, false, false);
                power(p, ModAbilities.SUPERSPEED_ID);
                p.connection.teleport(124.5, FLOOR, Z, -90, 0);
                SpeedService.toggle(p);
            });
            case 552 -> { server(AdvancedTechniques::startPerception); tracking = true; }
            case 556 -> press(true);
            case 576 -> capture = "11-speedster-wading.png";
            case 584 -> capture = "12-speedster-wading-later.png";
            case 588 -> tracking = false;
            case 589 -> {
                Vec3 at = mc.player.position();
                TempestCamera.fix(new Vec3(at.x - 4.5, FLOOR + 2.3, Z - 3.2), at.add(1.5, 0.7, 0));
            }
            case 592 -> capture = "13-speedster-behind.png";
            case 596 -> {
                Vec3 at = mc.player.position();
                TempestCamera.fix(new Vec3(at.x + 7, FLOOR + 1.4, Z + 1.6), at.add(0, 0.8, 0));
            }
            case 600 -> capture = "14-speedster-front.png";
            case 606 -> { press(false); server(AdvancedTechniques::endPerception); }
            case 620 -> PreviewQuiet.quit();
            default -> { }
        }
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post event) {
        if (capture == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            var out = mc.gameDirectory.toPath().resolve("screenshots").resolve(capture);
            Files.createDirectories(out.getParent()); image.writeToFile(out);
            System.out.println("SLOWTIME_CAPTURE " + capture);
        } catch (Exception error) { throw new RuntimeException(error); }
        capture = null;
    }
}
