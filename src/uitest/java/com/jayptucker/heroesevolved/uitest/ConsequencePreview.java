package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.client.effects.TempestCamera;
import com.jayptucker.heroesevolved.meteor.Meteor;
import com.jayptucker.heroesevolved.meteor.Meteors;
import com.jayptucker.heroesevolved.story.bunker.CryoChamberService;
import com.jayptucker.heroesevolved.story.prologue.PrologueService;
import com.jayptucker.heroesevolved.village.Rebuild;
import com.jayptucker.heroesevolved.village.Scars;
import com.jayptucker.heroesevolved.village.VillageMemory;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
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
import java.util.List;
import java.util.function.Consumer;

/**
 * Lasting consequences, photographed: a saved village's memorials and its hero's bust, a villager greeting them by name,
 * a fallen village's ruin and its stone for the lost, a meteor crater on the day it fell and healing over five days, a
 * wildfire's burn blooming, and a village rebuilding what a great meteor broke (broken, half way, done). Run with
 * {@code ./gradlew runClient --init-script tools/consequence-preview.init.gradle}; shots land in
 * {@code build/consequence-preview/screenshots/}.
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class ConsequencePreview {
    private static final int PHOTOS = 15;
    private static boolean started;
    private static int ticks, age, photos;
    private static String pending;
    private static Scars.Scar scar;
    private static Rebuild.Site site;
    private static Meteor meteor;

    private static boolean on() { return Boolean.getBoolean("heroes.preview.consequence"); }

    private static void server(Minecraft mc, Consumer<ServerPlayer> action) {
        var id = mc.player.getUUID();
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            try { action.accept(server.getPlayerList().getPlayer(id)); }
            catch (RuntimeException error) { PreviewQuiet.report("CONSEQUENCE_PREVIEW FAIL step at " + age + ": " + error); error.printStackTrace(); }
        });
    }

    private static void camera(Minecraft mc, Vec3 at, Vec3 look) { mc.execute(() -> TempestCamera.fix(at, look)); }

    private static void shot(String name) { pending = "consequence-" + name + ".png"; }

    private static void move(ServerPlayer p, Vec3 at, float yaw, float pitch) {
        p.teleportTo(p.getServer().overworld(), at.x, at.y, at.z, yaw, pitch);
        p.setDeltaMovement(Vec3.ZERO);
    }

    private static void set(ServerLevel level, int x, int y, int z, BlockState state) { level.setBlockAndUpdate(new BlockPos(x, y, z), state); }

    private static void hold(ServerLevel level, int x, int z) {
        for (int cx = (x - 48) >> 4; cx <= (x + 48) >> 4; cx++)
            for (int cz = (z - 48) >> 4; cz <= (z + 48) >> 4; cz++) level.setChunkForced(cx, cz, true);
    }

    /** A plank house with a door, glass windows and a slab roof, its corner at {@code hx, hz}, its door on the side facing {@code doorZ}. */
    private static void house(ServerLevel level, int hx, int hz, boolean doorSouth) {
        for (int dx = 0; dx < 5; dx++) for (int dz = 0; dz < 5; dz++) for (int y = -60; y <= -58; y++) {
            boolean wall = dx == 0 || dx == 4 || dz == 0 || dz == 4, corner = (dx == 0 || dx == 4) && (dz == 0 || dz == 4);
            BlockState s = !wall ? Blocks.AIR.defaultBlockState() : corner ? Blocks.OAK_LOG.defaultBlockState()
                    : y == -59 && (dx == 2 || dz == 2) ? Blocks.GLASS_PANE.defaultBlockState() : Blocks.OAK_PLANKS.defaultBlockState();
            set(level, hx + dx, y, hz + dz, s);
        }
        int dz = doorSouth ? 4 : 0;
        Direction facing = doorSouth ? Direction.SOUTH : Direction.NORTH;
        set(level, hx + 2, -60, hz + dz, Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, facing).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        set(level, hx + 2, -59, hz + dz, Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, facing).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        for (int x = -1; x < 6; x++) for (int z = -1; z < 6; z++)
            set(level, hx + x, -57, hz + z, Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        set(level, hx + 1, -60, hz + 1, Blocks.RED_BED.defaultBlockState()
                .setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT)
                .setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.EAST));
        set(level, hx + 2, -60, hz + 1, Blocks.RED_BED.defaultBlockState()
                .setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD)
                .setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.EAST));
        set(level, hx + 3, -60, hz + 3, Blocks.TORCH.defaultBlockState());
    }

    /** A little village round {@code x, z}: a square with a bell, four houses, grass and flowers, villagers at {@code spread}. */
    private static BlockPos village(ServerLevel level, int x, int z, double spread) {
        hold(level, x, z);
        RandomSource r = RandomSource.create(x * 31L + z);
        for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++)
            set(level, x + dx, -61, z + dz, ((Math.abs(dx) + Math.abs(dz)) % 3 == 0 ? Blocks.MOSSY_COBBLESTONE : Blocks.COBBLESTONE).defaultBlockState());
        set(level, x, -60, z, Blocks.COBBLESTONE_WALL.defaultBlockState());
        set(level, x, -59, z, Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, BellAttachType.FLOOR));
        int[][] houses = {{-13, -11}, {9, -11}, {-13, 9}, {9, 9}};
        for (int[] h : houses) house(level, x + h[0], z + h[1], h[1] < 0);
        for (int i = 0; i < 160; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = 7 + r.nextDouble() * 30;
            int px = x + (int) Math.round(Math.cos(a) * d), pz = z + (int) Math.round(Math.sin(a) * d);
            if (!level.getBlockState(new BlockPos(px, -61, pz)).is(Blocks.GRASS_BLOCK) || !level.getBlockState(new BlockPos(px, -60, pz)).isAir()) continue;
            set(level, px, -60, pz, (r.nextInt(4) == 0 ? Blocks.POPPY : Blocks.SHORT_GRASS).defaultBlockState());
        }
        for (int i = 0; i < 5; i++) {
            double a = i * Math.PI * 2 / 5;
            var v = EntityType.VILLAGER.create(level);
            v.moveTo(x + 0.5 + Math.cos(a) * spread, -60, z + 0.5 + Math.sin(a) * spread, r.nextFloat() * 360, 0);
            level.addFreshEntity(v);
        }
        return new BlockPos(x, -59, z);
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
            mc.createWorldOpenFlows().createFreshLevel("Consequence-" + System.currentTimeMillis(),
                    new LevelSettings("Consequence preview", GameType.SURVIVAL, false, Difficulty.NORMAL, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(91L, false, false),
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
                p.getAbilities().invulnerable = true; p.getAbilities().mayfly = true; p.getAbilities().flying = true; p.onUpdateAbilities();
                ServerLevel level = p.getServer().overworld();
                level.setDayTime(6_000);
                level.setWeatherParameters(24000, 0, false, false);
                move(p, new Vec3(700.5, -50, -20.5), 0, 20);
            });
            return;
        }
        if (age < 160) return;
        if (age % 20 == 0) com.jayptucker.heroesevolved.network.ClientScreenTintState.clear();
        script(mc, age - 160);
    }

    private static void script(Minecraft mc, int t) {
        switch (t) {
            // ---- A saved village remembers ----
            case 0 -> server(mc, p -> {
                mc.options.hideGui = true;
                BlockPos bell = village(p.serverLevel(), 700, 0, 4);
                VillageMemory.record(p.serverLevel(), bell, "the wildfire", VillageMemory.Outcome.SAVED, p, List.of(p), 6, 6);
                VillageMemory.record(p.serverLevel(), bell, "a great meteor", VillageMemory.Outcome.SAVED, p, List.of(p), 6, 6);
                camera(mc, new Vec3(700.5, -55.5, -9.5), new Vec3(700.5, -59, 0.5));
                System.out.println("CONSEQUENCE_PREVIEW saved " + VillageMemory.describe(p.serverLevel(), bell));
            });
            case 40 -> shot("01-memorials");
            case 42 -> server(mc, p -> {
                var v = VillageMemory.near(p.serverLevel(), new BlockPos(700, -59, 0));
                BlockPos m = v.memorials().getLast();
                camera(mc, Vec3.atCenterOf(m).add(-2.2, 1.2, -2.2), Vec3.atCenterOf(m).add(0, 0.8, 0));
            });
            case 60 -> shot("02-bust");
            case 62 -> server(mc, p -> {
                var v = VillageMemory.near(p.serverLevel(), new BlockPos(700, -59, 0));
                BlockPos m = v.memorials().getFirst();
                camera(mc, Vec3.atCenterOf(m).add(Vec3.atCenterOf(v.bell()).subtract(Vec3.atCenterOf(m)).normalize().scale(2.4)).add(0, 0.9, 0),
                        Vec3.atCenterOf(m).add(0, 1.1, 0));
            });
            case 80 -> shot("03-memorial-sign");
            // A villager greets them by name.
            case 82 -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                server(mc, p -> {
                    p.getAbilities().flying = false; p.onUpdateAbilities();
                    Villager near = p.serverLevel().getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(new BlockPos(700, -60, 0)).inflate(12)).getFirst();
                    near.setNoAi(true);
                    Vec3 at = near.position();
                    move(p, at.add(2.5, 0, 0), 90, 0);
                    VillageMemory.greetSoon(p);
                    camera(mc, at.add(-1.5, 2.8, -7.5), at.add(1.2, 2.0, 0));
                });
            }
            case 126 -> mc.options.hideGui = false;
            case 128 -> {
                shot("04-greeting");
                server(mc, p -> {
                    for (Villager v : p.serverLevel().getEntitiesOfClass(Villager.class, p.getBoundingBox().inflate(8)))
                        System.out.println("CONSEQUENCE_PREVIEW greeting " + VillageMemory.greeting(v));
                });
            }
            case 131 -> mc.options.hideGui = true;
            // ---- A fallen village ----
            case 132 -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                server(mc, p -> {
                    p.getAbilities().flying = true; p.onUpdateAbilities();
                    BlockPos bell = village(p.serverLevel(), 850, 0, 4);
                    for (Villager v : p.serverLevel().getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(bell).inflate(20))) v.discard();
                    move(p, new Vec3(850.5, -45, -40.5), 0, 20);
                    camera(mc, new Vec3(830.5, -52, -22.5), new Vec3(850.5, -59, 2.5));
                });
            }
            case 150 -> shot("05-village-before");
            case 152 -> server(mc, p -> VillageMemory.record(p.serverLevel(), new BlockPos(850, -59, 0), "Hollis the Ashen", VillageMemory.Outcome.FALLEN, null, List.of(), 0, 5));
            case 170 -> shot("06-fallen");
            case 172 -> camera(mc, new Vec3(856.5, -57.5, 13.5), new Vec3(861.5, -59.5, 10.5));
            case 185 -> shot("07-fallen-house");
            case 187 -> server(mc, p -> {
                var v = VillageMemory.near(p.serverLevel(), new BlockPos(850, -59, 0));
                BlockPos m = v.memorials().getFirst();
                camera(mc, Vec3.atCenterOf(m).add(Vec3.atCenterOf(v.bell()).subtract(Vec3.atCenterOf(m)).normalize().scale(2.4)).add(0, 0.9, 0),
                        Vec3.atCenterOf(m).add(0, 1.1, 0));
            });
            case 200 -> shot("08-stone-for-the-lost");
            // ---- A crater heals ----
            case 205 -> server(mc, p -> {
                hold(p.serverLevel(), 1000, 0);
                move(p, new Vec3(1000.5, -45, -30.5), 0, 20);
                meteor = Meteors.drop(p.serverLevel(), new Vec3(1000.5, -60, 0.5), false, 600, 0.6, null);
                Meteors.hurry(meteor, 2);
                camera(mc, new Vec3(993.5, -54, -7.5), new Vec3(1000.5, -61, 0.5));
            });
            case 260 -> {
                server(mc, p -> {
                    List<Scars.Scar> all = Scars.all(p.serverLevel());
                    scar = all.isEmpty() ? null : all.getLast();
                    System.out.println("CONSEQUENCE_PREVIEW crater scar " + (scar == null ? null : scar.center()));
                });
                shot("09-crater-day0");
            }
            case 262 -> server(mc, p -> { if (scar != null) { Scars.age(p.serverLevel(), scar, 2.5F); Scars.heal(p.serverLevel(), scar, 300); } });
            case 272 -> shot("10-crater-day3");
            case 274 -> server(mc, p -> { if (scar != null) { Scars.age(p.serverLevel(), scar, 2.5F); Scars.heal(p.serverLevel(), scar, 600); } });
            case 284 -> {
                shot("11-crater-day5");
                server(mc, p -> System.out.println("CONSEQUENCE_PREVIEW meteorite still there: "
                        + p.serverLevel().getBlockState(new BlockPos(1000, -63, 0)).getBlock()));
            }
            // ---- A burn blooms ----
            case 288 -> server(mc, p -> {
                ServerLevel level = p.serverLevel();
                hold(level, 1000, 60);
                // Scorched ground (dirt, plants gone), with grass already creeping back as it does on its own.
                RandomSource r = RandomSource.create(5);
                for (int dx = -12; dx <= 12; dx++) for (int dz = -12; dz <= 12; dz++) {
                    if (dx * dx + dz * dz > 144) continue;
                    set(level, 1000 + dx, -60, 60 + dz, Blocks.AIR.defaultBlockState());
                    if (r.nextFloat() < 0.3F) set(level, 1000 + dx, -61, 60 + dz, Blocks.DIRT.defaultBlockState());
                }
                scar = Scars.burn(level, new BlockPos(1000, -60, 60), 12);
                Scars.age(level, scar, 3.5F);
                Scars.heal(level, scar, 200);
                camera(mc, new Vec3(1000.5, -52, 40.5), new Vec3(1000.5, -60, 60.5));
            });
            case 305 -> shot("12-burn-blooms");
            // ---- A village rebuilds ----
            case 310 -> server(mc, p -> {
                ServerLevel level = p.serverLevel();
                BlockPos bell = village(level, 1150, 0, 24);
                house(level, 1150 + 3, 0 - 7, true);
                move(p, new Vec3(1150.5, -40, -60.5), 0, 20);
                meteor = Meteors.great(level, new BlockPos(1150, -60, 0), 2400);
                Meteors.hurry(meteor, 3);
                camera(mc, new Vec3(1136.5, -50, -20.5), new Vec3(1152.5, -60, -2.5));
            });
            case 360 -> {
                server(mc, p -> {
                    List<Rebuild.Site> all = Rebuild.all(p.serverLevel());
                    site = all.isEmpty() ? null : all.getLast();
                    System.out.println("CONSEQUENCE_PREVIEW rebuild site " + (site == null ? "NONE" : site.size() + " blocks"));
                });
                shot("13-broken");
            }
            case 362 -> server(mc, p -> { if (site != null) Rebuild.hurryTo(p.serverLevel(), site, 0.86F); });
            case 372 -> shot("14-rebuilding");
            case 374 -> server(mc, p -> { if (site != null) Rebuild.hurry(p.serverLevel(), site, 5000); });
            case 384 -> shot("15-rebuilt");
            case 400 -> {
                System.out.println("CONSEQUENCE_PREVIEW_RESULT " + (photos >= PHOTOS ? "PASS" : "FAIL") + ": " + photos + " photos");
                if (photos < PHOTOS) PreviewQuiet.report("CONSEQUENCE_PREVIEW FAIL only " + photos + " photos");
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
            System.out.println("CONSEQUENCE_CAPTURE " + pending);
        } catch (Exception ex) { PreviewQuiet.report("CONSEQUENCE_PREVIEW FAIL capture " + pending + ": " + ex); }
        pending = null;
    }
}
