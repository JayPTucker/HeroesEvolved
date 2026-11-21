package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.client.ClientMeteors;
import com.jayptucker.heroesevolved.client.effects.TempestCamera;
import com.jayptucker.heroesevolved.meteor.Meteor;
import com.jayptucker.heroesevolved.meteor.Meteors;
import com.jayptucker.heroesevolved.meteor.Starfall;
import com.jayptucker.heroesevolved.network.PlayerPowerSyncService;
import com.jayptucker.heroesevolved.story.bunker.CryoChamberService;
import com.jayptucker.heroesevolved.story.prologue.PrologueService;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.SlabType;
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
 * Starfall, photographed: the night's streaks, a falling star coming down onto its ring (half way, nearly down), its
 * landing and its crater with the meteorite glowing, the ring by day, then a great meteor on a little village (early,
 * late, a telekinetic pushing it aside, its landing and its crater) and a falling star cracked apart by a sonic. Run with
 * {@code ./gradlew runClient --init-script tools/meteor-preview.init.gradle}; shots land in
 * {@code build/meteor-preview/screenshots/}.
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class MeteorPreview {
    private static final int PHOTOS = 17;
    private static boolean started, pushing, cracking;
    private static int ticks, age, photos;
    private static String pending;
    private static Meteor meteor;

    private static boolean on() { return Boolean.getBoolean("heroes.preview.meteor"); }

    private static void server(Minecraft mc, Consumer<ServerPlayer> action) {
        var id = mc.player.getUUID();
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            try { action.accept(server.getPlayerList().getPlayer(id)); }
            catch (RuntimeException error) { PreviewQuiet.report("METEOR_PREVIEW FAIL step at " + age + ": " + error); error.printStackTrace(); }
        });
    }

    private static void camera(Minecraft mc, Vec3 at, Vec3 look) { mc.execute(() -> TempestCamera.fix(at, look)); }

    private static void shot(String name) { pending = "meteor-" + name + ".png"; }

    private static void move(ServerPlayer p, Vec3 at, float yaw, float pitch) {
        p.teleportTo(p.getServer().overworld(), at.x, at.y, at.z, yaw, pitch);
        p.setDeltaMovement(Vec3.ZERO);
    }

    private static void set(ServerLevel level, int x, int y, int z, BlockState state) { level.setBlockAndUpdate(new BlockPos(x, y, z), state); }

    private static void hold(ServerLevel level, int x, int z) {
        for (int cx = (x - 48) >> 4; cx <= (x + 48) >> 4; cx++)
            for (int cz = (z - 48) >> 4; cz <= (z + 48) >> 4; cz++) level.setChunkForced(cx, cz, true);
    }

    /** A little village round {@code x, z}: a cobbled square with a bell, four plank houses, grass and flowers, villagers. */
    private static void village(ServerLevel level, int x, int z) {
        hold(level, x, z);
        RandomSource r = RandomSource.create(x * 31L + z);
        for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++)
            set(level, x + dx, -61, z + dz, ((Math.abs(dx) + Math.abs(dz)) % 3 == 0 ? Blocks.MOSSY_COBBLESTONE : Blocks.COBBLESTONE).defaultBlockState());
        set(level, x, -60, z, Blocks.COBBLESTONE_WALL.defaultBlockState());
        set(level, x, -59, z, Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, BellAttachType.FLOOR));
        int[][] houses = {{-13, -11}, {9, -11}, {-13, 9}, {9, 9}};
        for (int[] h : houses) {
            int hx = x + h[0], hz = z + h[1];
            for (int dx = 0; dx < 5; dx++) for (int dz = 0; dz < 5; dz++) for (int y = -60; y <= -58; y++) {
                boolean wall = dx == 0 || dx == 4 || dz == 0 || dz == 4, door = dz == (h[1] < 0 ? 4 : 0) && dx == 2 && y < -58;
                set(level, hx + dx, y, hz + dz, wall && !door ? (dx == 0 || dx == 4) && (dz == 0 || dz == 4)
                        ? Blocks.OAK_LOG.defaultBlockState() : Blocks.OAK_PLANKS.defaultBlockState() : Blocks.AIR.defaultBlockState());
            }
            for (int dx = -1; dx < 6; dx++) for (int dz = -1; dz < 6; dz++)
                set(level, hx + dx, -57, hz + dz, Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
            set(level, hx + 2, -58, hz + 2, Blocks.LANTERN.defaultBlockState());
        }
        for (int i = 0; i < 200; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = 7 + r.nextDouble() * 30;
            int px = x + (int) Math.round(Math.cos(a) * d), pz = z + (int) Math.round(Math.sin(a) * d);
            if (!level.getBlockState(new BlockPos(px, -61, pz)).is(Blocks.GRASS_BLOCK) || !level.getBlockState(new BlockPos(px, -60, pz)).isAir()) continue;
            set(level, px, -60, pz, (r.nextInt(4) == 0 ? Blocks.POPPY : Blocks.SHORT_GRASS).defaultBlockState());
        }
        double[][] spots = {{-3, 2}, {3, 3}, {-2, -4}, {4, -2}, {0, 5}, {-5, -1}};
        for (double[] s : spots) {
            var v = EntityType.VILLAGER.create(level);
            v.moveTo(x + 0.5 + s[0], -60, z + 0.5 + s[1], r.nextFloat() * 360, 0);
            level.addFreshEntity(v);
        }
    }

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
            rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
            mc.createWorldOpenFlows().createFreshLevel("Meteor-" + System.currentTimeMillis(),
                    new LevelSettings("Meteor preview", GameType.SURVIVAL, false, Difficulty.NORMAL, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(87L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.getSingleplayerServer() == null || mc.getOverlay() != null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        if (age >= 100) mc.getToasts().clear();
        ++age;
        if (pushing) aim(mc);
        if (age == 104) { server(mc, PrologueService::skip); return; }
        if (age == 110) {
            server(mc, p -> {
                CryoChamberService.release(p);
                p.setGameMode(GameType.SURVIVAL);
                p.removeAllEffects();
                p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 900, 4, false, false));
                p.getAbilities().invulnerable = true; p.getAbilities().mayfly = true; p.getAbilities().flying = true; p.onUpdateAbilities();
                ServerLevel level = p.getServer().overworld();
                level.setDayTime(18_000);
                level.setWeatherParameters(24000, 0, false, false);
                hold(level, 700, 0);
                move(p, new Vec3(700.5, -55, -20.5), 0, -30);
            });
            return;
        }
        if (age < 160) return;
        if (age % 20 == 0) com.jayptucker.heroesevolved.network.ClientScreenTintState.clear();
        script(mc, age - 160);
    }

    /** The player looks at the great meteor (so pushing it counts). */
    private static void aim(Minecraft mc) {
        for (ClientMeteors.View v : ClientMeteors.views()) {
            if (!v.great) continue;
            Vec3 to = v.sky(0).subtract(mc.player.getEyePosition());
            float yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z)), pitch = (float) -Math.toDegrees(Math.atan2(to.y, to.horizontalDistance()));
            mc.player.setYRot(yaw); mc.player.setXRot(pitch);
            mc.player.yRotO = yaw; mc.player.xRotO = pitch;
        }
    }

    private static void script(Minecraft mc, int t) {
        switch (t) {
            // ---- The night sky ----
            case 0 -> server(mc, p -> {
                Starfall.force(p.getServer(), true);
                mc.options.hideGui = true;
                camera(mc, new Vec3(700.5, -58, -20.5), new Vec3(700.5, -20, 40));
            });
            case 30 -> shot("01-starfall-sky");
            case 44 -> shot("02-starfall-sky-b");
            // ---- A falling star ----
            case 50 -> server(mc, p -> {
                meteor = Meteors.drop(p.serverLevel(), new Vec3(700.5, -60, 12.5), false, 600, Math.PI * 0.35, null);
                Meteors.fallen(meteor, 0.5F);
                camera(mc, new Vec3(694.5, -56, -14.5), new Vec3(704, -40, 20));
            });
            case 70 -> shot("03-falling");
            case 72 -> server(mc, p -> Meteors.fallen(meteor, 0.93F));
            case 80 -> shot("04-almost-down");
            case 82 -> server(mc, p -> Meteors.hurry(meteor, 2));
            case 86 -> shot("05-impact-a");
            case 92 -> shot("05-impact-b");
            case 130 -> {
                camera(mc, new Vec3(704.5, -56.5, 7.5), new Vec3(700.5, -61, 12.5));
                server(mc, p -> System.out.println("METEOR_PREVIEW small crater meteorite " + meteorite(p.serverLevel(), new Vec3(700.5, -60, 12.5))));
            }
            case 140 -> shot("06-crater");
            // ---- The ring by day ----
            case 145 -> server(mc, p -> {
                p.serverLevel().setDayTime(6_000);
                meteor = Meteors.drop(p.serverLevel(), new Vec3(712.5, -60, 18.5), false, 600, 0.4, null);
                Meteors.fallen(meteor, 0.6F);
                camera(mc, new Vec3(712.5, -52, 2.5), new Vec3(712.5, -60, 18.5));
            });
            case 170 -> shot("07-day-ring");
            case 172 -> server(mc, p -> Meteors.cancelAll());

            // ---- A great meteor on a village ----
            case 180 -> server(mc, p -> {
                p.serverLevel().setDayTime(14_500);
                village(p.serverLevel(), 900, 0);
                move(p, new Vec3(900.5, -40, -60.5), 0, 20);
                meteor = Meteors.great(p.serverLevel(), new BlockPos(900, -60, 0), 2400);
                Meteors.fallen(meteor, 0.3F);
                // From the far side of the village, looking toward where it comes from.
                Vec3 land = meteor.land(), from = from(meteor), side = new Vec3(-from.z, 0, from.x);
                // Off to one side, so the village's flare doesn't stand in front of it.
                camera(mc, land.subtract(from.scale(40)).add(side.scale(26)).add(0, 7, 0), land.add(meteor.sky().subtract(land).normalize().scale(60)));
            });
            case 240 -> shot("08-great-early");
            case 242 -> server(mc, p -> {
                Meteors.fallen(meteor, 0.8F);
                Vec3 land = meteor.land(), from = from(meteor);
                camera(mc, land.subtract(from.scale(34)).add(0, 4, 0), land.add(meteor.sky().subtract(land).normalize().scale(45)));
            });
            case 290 -> { mc.options.hideGui = false; shot("09-great-late"); }
            // A telekinetic pushing it aside: crouching, looking at it.
            case 292 -> {
                mc.options.hideGui = true;
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                server(mc, p -> {
                    PlayerAbilityService.grantAbility(p, ModAbilities.TELEKINESIS_ID);
                    PlayerPowerSyncService.sync(p);
                    p.getAbilities().flying = false; p.onUpdateAbilities();
                    // On the near side of the village, the meteor ahead and up; the camera behind and beside them.
                    Vec3 land = meteor.land(), from = from(meteor);
                    Vec3 stand = land.subtract(from.scale(14));
                    move(p, new Vec3(stand.x, -60, stand.z), 0, -30);
                    Vec3 side = new Vec3(-from.z, 0, from.x);
                    camera(mc, stand.subtract(from.scale(7)).add(side.scale(4)).add(0, 2.5, 0),
                            stand.add(meteor.sky().subtract(stand).normalize().scale(30)));
                });
            }
            case 296 -> { pushing = true; mc.options.keyShift.setDown(true); }
            case 340 -> shot("10-pushing");
            case 342 -> server(mc, p -> System.out.println("METEOR_PREVIEW pushed " + meteor.pushed() + " blocks"));
            case 344 -> {
                pushing = false;
                mc.options.keyShift.setDown(false);
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                server(mc, p -> {
                    p.getAbilities().flying = true; p.onUpdateAbilities();
                    move(p, new Vec3(900.5, -30, -80.5), 0, 20);
                    Meteors.hurry(meteor, 3);
                    Vec3 at = meteor.land();
                    camera(mc, at.add(-14, 16, -44), at.add(0, 2, 0));
                });
            }
            case 349 -> shot("11-great-impact-a");
            case 358 -> shot("11-great-impact-b");
            case 380 -> server(mc, p -> System.out.println("METEOR_PREVIEW great crater meteorite " + meteorite(p.serverLevel(), meteor.land())));
            case 400 -> server(mc, p -> {
                Vec3 at = meteor.land();
                camera(mc, at.add(-12, 10, -12), at.add(0, -3, 0));
            });
            case 410 -> shot("12-great-crater");
            // ---- A falling star cracked apart ----
            case 420 -> {
                server(mc, p -> {
                    PlayerAbilityService.replaceWithAbility(p, ModAbilities.SONIC_RESONANCE_ID);
                    PlayerPowerSyncService.sync(p);
                    p.getAbilities().flying = false; p.onUpdateAbilities();
                    move(p, new Vec3(940.5, -60, 0.5), 0, -40);
                    hold(p.serverLevel(), 950, 20);
                    meteor = Meteors.drop(p.serverLevel(), new Vec3(948.5, -60, 22.5), false, 600, 1.0, null);
                    Meteors.fallen(meteor, 0.55F);
                });
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            }
            case 424 -> server(mc, p -> {
                Vec3 sky = meteor.sky();
                camera(mc, new Vec3(934.5, -57, -8.5), sky.add(0, -6, 0));
            });
            case 428 -> { pushing = false; cracking = true; mc.options.keyShift.setDown(true); }
            default -> { }
        }
        // The sonic looks at the falling star while they crack it (its view isn't great, so aim here).
        if (t > 428 && t < 540) {
            for (ClientMeteors.View v : ClientMeteors.views()) {
                Vec3 to = v.sky(0).subtract(mc.player.getEyePosition());
                float yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z)), pitch = (float) -Math.toDegrees(Math.atan2(to.y, to.horizontalDistance()));
                mc.player.setYRot(yaw); mc.player.setXRot(pitch);
                // Follow it down, so the burst is in frame when it breaks.
                TempestCamera.fix(new Vec3(934.5, -57, -8.5), mc.player.getEyePosition().lerp(v.sky(0), 0.75));
            }
            if (t == 480) shot("13-cracking");
            // The moment it breaks, while the burst is still bright.
            if (cracking && t > 440 && ClientMeteors.views().isEmpty()) { cracking = false; shot("14-cracked"); }
        }
        switch (t) {
            case 540 -> { if (ClientMeteors.views().isEmpty()) System.out.println("METEOR_PREVIEW cracked apart"); }
            case 548 -> { mc.options.keyShift.setDown(false); mc.options.setCameraType(CameraType.FIRST_PERSON); }
            case 560 -> server(mc, p -> {
                p.serverLevel().setDayTime(18_000);
                camera(mc, new Vec3(940.5, -59, -10.5), new Vec3(940.5, -20, 40));
            });
            case 580 -> shot("15-starfall-sky-c");
            case 600 -> server(mc, p -> { Starfall.force(p.getServer(), false); Meteors.cancelAll(); });
            case 620 -> {
                System.out.println("METEOR_PREVIEW_RESULT " + (photos >= PHOTOS ? "PASS" : "FAIL") + ": " + photos + " photos");
                if (photos < PHOTOS) PreviewQuiet.report("METEOR_PREVIEW FAIL only " + photos + " photos");
                PreviewQuiet.quit();
            }
            default -> { }
        }
    }

    /** Which way along the ground the meteor comes from, from where it lands. */
    private static Vec3 from(Meteor m) { return m.start().subtract(m.land()).multiply(1, 0, 1).normalize(); }

    /** Where the meteorite blocks are round {@code at} (the log says whether the crater kept them). */
    private static String meteorite(ServerLevel level, Vec3 at) {
        StringBuilder out = new StringBuilder();
        for (BlockPos q : BlockPos.betweenClosed(BlockPos.containing(at).offset(-14, -12, -14), BlockPos.containing(at).offset(14, 6, 14)))
            if (level.getBlockState(q).is(com.jayptucker.heroesevolved.registry.ModBlocks.HELIXITE_METEORITE.get())) out.append(q.toShortString()).append("; ");
        return out.length() == 0 ? "NONE" : out.toString();
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post e) {
        if (pending == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(out.getParent());
            image.writeToFile(out);
            photos++;
            System.out.println("METEOR_CAPTURE " + pending);
        } catch (Exception ex) { PreviewQuiet.report("METEOR_PREVIEW FAIL capture " + pending + ": " + ex); }
        pending = null;
    }
}
