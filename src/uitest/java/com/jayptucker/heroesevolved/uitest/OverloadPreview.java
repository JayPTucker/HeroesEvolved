package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.client.effects.TempestCamera;
import com.jayptucker.heroesevolved.network.PlayerPowerSyncService;
import com.jayptucker.heroesevolved.story.bunker.CryoChamberService;
import com.jayptucker.heroesevolved.story.prologue.PrologueService;
import com.jayptucker.heroesevolved.village.overload.Overload;
import com.jayptucker.heroesevolved.village.overload.Overloads;
import com.jayptucker.heroesevolved.village.overload.Storm;
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
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
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
 * Overloads, photographed: a wildfire growing out of a village (early, wide, the column of fire round them, someone
 * calming them, the break's warning, the Firestorm), a supercell (its funnel, from the ground, its deck, Touchdown's
 * warning and run), and a blizzard (from outside, inside with the COLD ring, seen through the snow by a telepath, and the
 * Deep Freeze). Each storm has its own little village 200 blocks apart, well away from spawn. Run with
 * {@code ./gradlew runClient --init-script tools/overload-preview.init.gradle}; shots land in
 * {@code build/overload-preview/screenshots/}.
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class OverloadPreview {
    private static final int PHOTOS = 16;
    private static boolean started;
    private static int ticks, age, photos;
    private static String pending;
    private static Villager eye;
    private static Overload storm;

    private static boolean on() { return Boolean.getBoolean("heroes.preview.overload"); }

    private static void server(Minecraft mc, Consumer<ServerPlayer> action) {
        var id = mc.player.getUUID();
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            try { action.accept(server.getPlayerList().getPlayer(id)); }
            catch (RuntimeException error) { PreviewQuiet.report("OVERLOAD_PREVIEW FAIL step at " + age + ": " + error); error.printStackTrace(); }
        });
    }

    private static void camera(Minecraft mc, Vec3 at, Vec3 look) { mc.execute(() -> TempestCamera.fix(at, look)); }

    private static void shot(String name) { pending = "overload-" + name + ".png"; }

    private static void move(ServerPlayer p, Vec3 at, float yaw, float pitch) {
        p.teleportTo(p.getServer().overworld(), at.x, at.y, at.z, yaw, pitch);
        p.setDeltaMovement(Vec3.ZERO);
    }

    private static void set(ServerLevel level, int x, int y, int z, BlockState state) { level.setBlockAndUpdate(new BlockPos(x, y, z), state); }

    /**
     * A little village round {@code x, z}: a cobbled square with a bell, four plank houses with slab roofs and open doors,
     * grass and flowers round about, and six villagers. Its chunks are held loaded.
     */
    private static void village(ServerLevel level, int x, int z) {
        for (int cx = (x - 64) >> 4; cx <= (x + 64) >> 4; cx++)
            for (int cz = (z - 64) >> 4; cz <= (z + 64) >> 4; cz++) level.setChunkForced(cx, cz, true);
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
        }
        for (int i = 0; i < 260; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = 7 + r.nextDouble() * 34;
            int px = x + (int) Math.round(Math.cos(a) * d), pz = z + (int) Math.round(Math.sin(a) * d);
            if (!level.getBlockState(new BlockPos(px, -61, pz)).is(Blocks.GRASS_BLOCK) || !level.getBlockState(new BlockPos(px, -60, pz)).isAir()) continue;
            BlockState plant = switch (r.nextInt(5)) {
                case 0 -> Blocks.POPPY.defaultBlockState();
                case 1 -> Blocks.DANDELION.defaultBlockState();
                default -> Blocks.SHORT_GRASS.defaultBlockState();
            };
            set(level, px, -60, pz, plant);
        }
        double[][] spots = {{-3, 2}, {3, 3}, {-2, -4}, {4, -2}, {0, 5}, {-5, -1}};
        for (double[] s : spots) {
            var v = EntityType.VILLAGER.create(level);
            v.moveTo(x + 0.5 + s[0], -60, z + 0.5 + s[1], r.nextFloat() * 360, 0);
            level.addFreshEntity(v);
        }
    }

    /** The villager whose gift overloads, just south of the bell, and their storm. */
    private static void overload(ServerLevel level, int x, int z, Storm kind, float progress) {
        if (eye != null) { Overloads.cancel(eye); eye.discard(); }
        eye = EntityType.VILLAGER.create(level);
        eye.setVillagerData(eye.getVillagerData().setProfession(VillagerProfession.FARMER));
        eye.moveTo(x + 0.5, -60, z + 3.5, 180, 0);
        level.addFreshEntity(eye);
        storm = Overloads.start(eye, kind, 6000);
        if (storm != null) Overloads.progress(storm, progress);
        System.out.println("OVERLOAD_PREVIEW " + kind + " started=" + (storm != null) + " village=" + (storm == null ? 0 : storm.villageSize()));
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
            mc.createWorldOpenFlows().createFreshLevel("Overload-" + System.currentTimeMillis(),
                    new LevelSettings("Overload preview", GameType.SURVIVAL, false, Difficulty.NORMAL, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(83L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.getSingleplayerServer() == null || mc.getOverlay() != null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        if (age >= 100) mc.getToasts().clear();
        ++age;
        if (age == 104) { server(mc, PrologueService::skip); return; }
        if (age == 110) {
            server(mc, p -> {
                CryoChamberService.release(p);
                p.setGameMode(GameType.SURVIVAL);
                p.removeAllEffects();
                p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 900, 4, false, false));
                p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 900, 0, false, false));
                p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 900, 2, false, false));
                p.getAbilities().invulnerable = true; p.getAbilities().mayfly = true; p.getAbilities().flying = true; p.onUpdateAbilities();
                ServerLevel level = p.getServer().overworld();
                level.setDayTime(6000);
                level.setWeatherParameters(24000, 0, false, false);
                move(p, new Vec3(600.5, -45, -40.5), 0, 20);
            });
            return;
        }
        if (age < 160) return;
        if (age % 20 == 0) com.jayptucker.heroesevolved.network.ClientScreenTintState.clear();
        script(mc, age - 160);
    }

    private static void script(Minecraft mc, int t) {
        switch (t) {
            // ---- The wildfire ----
            case 0 -> server(mc, p -> {
                village(p.serverLevel(), 600, 0);
                overload(p.serverLevel(), 600, 0, Storm.WILDFIRE, 0.25F);
                mc.options.hideGui = true;
                camera(mc, new Vec3(600.5, -49, -26), new Vec3(600.5, -59, 3));
            });
            case 50 -> shot("01-wildfire-early");
            case 52 -> server(mc, p -> {
                Overloads.progress(storm, 0.75F);
                camera(mc, new Vec3(600.5, -36, -44), new Vec3(600.5, -58, 4));
            });
            case 100 -> shot("02-wildfire-wide");
            case 102 -> camera(mc, new Vec3(605, -57.4, -2.5), new Vec3(600.5, -58.6, 3.5));
            case 118 -> shot("03-wildfire-eye");
            case 120 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                server(mc, p -> {
                    p.getAbilities().flying = false; p.onUpdateAbilities();
                    move(p, new Vec3(602.5, -60, 1.5), 135, 10);
                    Overloads.calm(storm, 0.3F);
                    camera(mc, new Vec3(607.5, -57.5, -4), new Vec3(601.5, -59, 2.5));
                });
            }
            case 150 -> shot("04-wildfire-calm");
            case 152 -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                server(mc, p -> {
                    p.getAbilities().flying = true; p.onUpdateAbilities();
                    move(p, new Vec3(600.5, -40, -60.5), 0, 20);
                    Overloads.calm(storm, 0);
                    Overloads.hurry(storm, 1);
                    camera(mc, new Vec3(600.5, -30, -52), new Vec3(600.5, -58, 3));
                });
            }
            case 185 -> shot("05-wildfire-breaking");
            case 218 -> shot("06-firestorm");
            case 236 -> server(mc, p -> Overloads.cancel(eye));

            // ---- The supercell ----
            case 240 -> server(mc, p -> {
                move(p, new Vec3(800.5, -40, -45.5), 0, 20);
                village(p.serverLevel(), 800, 0);
                overload(p.serverLevel(), 800, 0, Storm.SUPERCELL, 0.3F);
                camera(mc, new Vec3(800.5, -46, -40), new Vec3(800.5, -46, 4));
            });
            case 320 -> shot("07-supercell");
            case 322 -> server(mc, p -> {
                Overloads.progress(storm, 0.85F);
                Vec3 at = eye.position();
                camera(mc, at.add(-14, 2.2, -22), at.add(0, 6, 0));
            });
            case 380 -> shot("08-supercell-ground");
            case 382 -> server(mc, p -> {
                Vec3 at = eye.position();
                camera(mc, at.add(0, 34, -70), at.add(0, 18, 0));
            });
            case 410 -> shot("09-supercell-deck");
            case 412 -> server(mc, p -> {
                Overloads.hurry(storm, 1);
                Vec3 at = eye.position();
                camera(mc, at.add(-14, 14, -52), at.add(0, 6, 6));
            });
            case 450 -> {
                for (var v : com.jayptucker.heroesevolved.client.ClientOverloads.views())
                    System.out.println("OVERLOAD_PREVIEW client view " + v.storm + " stage " + v.stage + " eye " + v.eye
                            + " target " + v.target + " camera " + mc.gameRenderer.getMainCamera().getPosition());
                shot("10-touchdown-warning");
            }
            case 482 -> shot("11-touchdown");
            case 496 -> server(mc, p -> Overloads.cancel(eye));

            // ---- The blizzard ----
            case 500 -> server(mc, p -> {
                // Out in it, and not unhurtable, so the cold takes hold and the COLD ring shows.
                p.getAbilities().invulnerable = false; p.onUpdateAbilities();
                move(p, new Vec3(1000.5, -40, -60.5), 0, 20);
                village(p.serverLevel(), 1000, 0);
                overload(p.serverLevel(), 1000, 0, Storm.BLIZZARD, 0.5F);
                camera(mc, new Vec3(1000.5, -48, -52), new Vec3(1000.5, -58, 3));
            });
            case 570 -> shot("12-blizzard-outside");
            case 572 -> {
                mc.execute(() -> TempestCamera.fix(null, null));
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                server(mc, p -> {
                    p.getAbilities().flying = false; p.onUpdateAbilities();
                    move(p, new Vec3(1000.5, -60, -5.5), 0, 6);
                });
            }
            case 574 -> { mc.player.setYRot(0); mc.player.setXRot(6); }
            case 640 -> { mc.options.hideGui = false; shot("13-blizzard-inside"); }
            case 642 -> {
                mc.options.hideGui = true;
                server(mc, p -> {
                    PlayerAbilityService.grantAbility(p, ModAbilities.TELEPATHY_ID);
                    PlayerPowerSyncService.sync(p);
                    move(p, new Vec3(1000.5, -60, -22.5), 0, 4);
                });
            }
            case 646 -> { mc.player.setYRot(0); mc.player.setXRot(4); }
            case 680 -> shot("14-blizzard-seen");
            case 682 -> server(mc, p -> {
                p.getAbilities().invulnerable = true; p.getAbilities().flying = true; p.onUpdateAbilities();
                move(p, new Vec3(1000.5, -40, -70.5), 0, 20);
                Overloads.hurry(storm, 1);
                camera(mc, new Vec3(1000.5, -34, -62), new Vec3(1000.5, -58, 3));
            });
            case 715 -> shot("15-deep-freeze-warning");
            case 748 -> shot("16-deep-freeze");
            case 770 -> server(mc, p -> {
                System.out.println("OVERLOAD_PREVIEW blizzard stage " + (storm == null ? null : storm.stage()));
                Overloads.cancel(eye);
            });
            case 800 -> {
                System.out.println("OVERLOAD_PREVIEW_RESULT " + (photos >= PHOTOS ? "PASS" : "FAIL") + ": " + photos + " photos");
                if (photos < PHOTOS) PreviewQuiet.report("OVERLOAD_PREVIEW FAIL only " + photos + " photos");
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
            System.out.println("OVERLOAD_CAPTURE " + pending);
        } catch (Exception ex) { PreviewQuiet.report("OVERLOAD_PREVIEW FAIL capture " + pending + ": " + ex); }
        pending = null;
    }
}
