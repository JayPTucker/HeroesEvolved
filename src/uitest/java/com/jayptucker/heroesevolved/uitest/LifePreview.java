package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.client.effects.TempestCamera;
import com.jayptucker.heroesevolved.gifted.Renegades;
import com.jayptucker.heroesevolved.story.bunker.CryoChamberService;
import com.jayptucker.heroesevolved.story.prologue.PrologueService;
import com.jayptucker.heroesevolved.village.Shouts;
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
import net.minecraft.world.level.Level;
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
import net.minecraft.world.phys.AABB;
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

import com.jayptucker.heroesevolved.radio.BandChatter;
import com.jayptucker.heroesevolved.radio.EmergencyBand;
import com.jayptucker.heroesevolved.story.bunker.BunkerData;
import com.jayptucker.heroesevolved.story.bunker.BunkerRadios;
import com.jayptucker.heroesevolved.story.bunker.BunkerService;
import com.jayptucker.heroesevolved.village.Favours;
import com.jayptucker.heroesevolved.village.GiftedWork;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.resources.ResourceLocation;

/**
 * The living radio and gifted villagers at work, photographed: the radios in the bunker's Records chest; a smith
 * lighting the forge, a waterer, the ice house, a golem keeper, a harvester, a lookout's warning, a courier over the
 * roofs and a post runner; a favour asked and done; and the villages' chatter in chat with a radio in hand. Run with
 * {@code ./gradlew runClient --init-script tools/life-preview.init.gradle}; shots land in
 * {@code build/life-preview/screenshots/}.
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class LifePreview {
    private static final int PHOTOS = 13;
    private static boolean started;
    private static int ticks, age, photos;
    private static String pending;
    private static Villager smith, waterer, ice, keeper, harvester, lookout, courier, runner;

    private static boolean on() { return Boolean.getBoolean("heroes.preview.life"); }

    private static void server(Minecraft mc, Consumer<ServerPlayer> action) {
        var id = mc.player.getUUID();
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            try { action.accept(server.getPlayerList().getPlayer(id)); }
            catch (RuntimeException error) { PreviewQuiet.report("LIFE_PREVIEW FAIL step at " + age + ": " + error); error.printStackTrace(); }
        });
    }

    private static void camera(Minecraft mc, Vec3 at, Vec3 look) { mc.execute(() -> TempestCamera.fix(at, look)); }

    private static void shot(String name) { pending = "life-" + name + ".png"; }

    private static void move(ServerPlayer p, Vec3 at, float yaw, float pitch) {
        p.teleportTo(p.getServer().overworld(), at.x, at.y, at.z, yaw, pitch);
        p.setDeltaMovement(Vec3.ZERO);
    }

    private static void set(ServerLevel level, int x, int y, int z, BlockState state) { level.setBlockAndUpdate(new BlockPos(x, y, z), state); }

    private static void hold(ServerLevel level, int x, int z) {
        for (int cx = (x - 64) >> 4; cx <= (x + 64) >> 4; cx++)
            for (int cz = (z - 64) >> 4; cz <= (z + 64) >> 4; cz++) level.setChunkForced(cx, cz, true);
    }

    /** A plank house with a door, glass windows, a slab roof, a bed and a torch, its corner at {@code hx, hz}. */
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
        set(level, hx + 2, -58, hz + (doorSouth ? 5 : -1), Blocks.WALL_TORCH.defaultBlockState()
                .setValue(net.minecraft.world.level.block.WallTorchBlock.FACING, facing));
    }

    /** A little village round {@code x, z}: a square with a bell, four houses, grass and flowers, still villagers. */
    private static BlockPos village(ServerLevel level, int x, int z, int people, double spread) {
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
        for (int i = 0; i < people; i++) {
            double a = i * Math.PI * 2 / people;
            var v = EntityType.VILLAGER.create(level);
            v.moveTo(x + 0.5 + Math.cos(a) * spread, -60, z + 0.5 + Math.sin(a) * spread, (float) Math.toDegrees(a) + 90, 0);
            v.getPersistentData().putBoolean(com.jayptucker.heroesevolved.gifted.GiftedMobs.ROLLED, true);
            v.setNoAi(true);
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
            mc.createWorldOpenFlows().createFreshLevel("Life-" + System.currentTimeMillis(),
                    new LevelSettings("Life preview", GameType.SURVIVAL, false, Difficulty.NORMAL, true, rules, WorldDataConfiguration.DEFAULT),
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
                move(p, new Vec3(700.5, -40, -40.5), 0, 20);
            });
            return;
        }
        if (age < 160) return;
        if (age % 20 == 0) com.jayptucker.heroesevolved.network.ClientScreenTintState.clear();
        script(mc, age - 160);
    }

    /** A guardian of {@code power} standing at {@code x, z}, held from work until their scene. */
    private static Villager guardian(ServerLevel level, double x, double z, float yaw, ResourceLocation power) {
        Villager v = EntityType.VILLAGER.create(level);
        v.moveTo(x, -60, z, yaw, 0);
        v.getPersistentData().putBoolean(com.jayptucker.heroesevolved.gifted.GiftedMobs.ROLLED, true);
        level.addFreshEntity(v);
        Renegades.guard(v, power);
        v.setNoAi(true);
        GiftedWork.pause(v, 20 * 600);
        return v;
    }

    /** Their scene: back to themselves, and to work. */
    private static void work(Villager v) {
        v.setNoAi(false);
        System.out.println("LIFE_PREVIEW " + GiftedWork.job(v) + " works: " + GiftedWork.now(v));
    }

    private static void stations(ServerLevel level) {
        village(level, 700, 0, 4, 8);
        // The smith's forge, the fields, the ice house's barrel, the golem.
        set(level, 694, -60, -4, Blocks.BLAST_FURNACE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, net.minecraft.core.Direction.SOUTH));
        for (int x = 697; x <= 701; x++) for (int z = 16; z <= 19; z++) {
            set(level, x, -61, z, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 0));
            set(level, x, -60, z, ((CropBlock) Blocks.WHEAT).getStateForAge(2));
        }
        for (int x = 690; x <= 694; x++) for (int z = 16; z <= 18; z++) {
            set(level, x, -61, z, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
            set(level, x, -60, z, ((CropBlock) Blocks.CARROTS).getStateForAge(7));
        }
        set(level, 706, -60, 4, Blocks.BARREL.defaultBlockState());
        IronGolem golem = EntityType.IRON_GOLEM.create(level);
        golem.moveTo(686.5, -60, 0.5, 90, 0);
        golem.setNoAi(true);
        level.addFreshEntity(golem);
        golem.setHealth(50);
        smith = guardian(level, 694.5, -1.8, 180, ModAbilities.PYROKINESIS_ID);
        waterer = guardian(level, 699.5, 14.0, 0, ModAbilities.HYDROKINESIS_ID);
        ice = guardian(level, 706.5, 6.3, 180, ModAbilities.CRYOKINESIS_ID);
        keeper = guardian(level, 689.5, 0.5, 90, ModAbilities.ELECTROKINESIS_ID);
        harvester = guardian(level, 692.5, 14.5, 0, ModAbilities.TELEKINESIS_ID);
        lookout = guardian(level, 712.5, 0.5, -90, ModAbilities.VIBE_ID);
        courier = guardian(level, 703.5, -3.5, 0, ModAbilities.FLIGHT_ID);
        runner = guardian(level, 697.5, 4.5, 0, ModAbilities.SUPERSPEED_ID);
    }

    private static void script(Minecraft mc, int t) {
        switch (t) {
            case 0 -> server(mc, p -> {
                mc.options.hideGui = true;
                stations(p.serverLevel());
            });
            // ---- The radios in the bunker ----
            case 10 -> server(mc, p -> {
                var server = p.getServer();
                BunkerData data = BunkerService.ensureBuilt(server);
                BlockPos chest = BunkerRadios.chest(data);
                server.overworld().getChunkAt(chest);
                BunkerRadios.stock(server.overworld(), data);
                System.out.println("LIFE_PREVIEW bunker players " + BunkerRadios.players(data) + " radios " + data.radios + " chest " + chest);
                move(p, Vec3.atBottomCenterOf(chest.above()), 0, 90);
            });
            case 30 -> server(mc, p -> {
                BlockPos chest = BunkerRadios.chest(BunkerData.get(p.getServer()));
                if (p.serverLevel().getBlockEntity(chest) instanceof MenuProvider box) p.openMenu(box);
                mc.options.hideGui = false;
            });
            case 44 -> shot("01-bunker-radios");
            case 48 -> server(mc, p -> {
                p.closeContainer();
                mc.options.hideGui = true;
                move(p, new Vec3(700.5, -40, -40.5), 0, 20);
            });
            // ---- The smith ----
            case 60 -> server(mc, p -> {
                camera(mc, new Vec3(697.5, -58.2, 0.5), new Vec3(694.5, -59.3, -2.6));
                work(smith);
            });
            case 72 -> shot("02-smith");
            case 104 -> shot("03-forge-lit");
            // ---- The waterer ----
            case 110 -> server(mc, p -> {
                camera(mc, new Vec3(704.5, -57.5, 12.5), new Vec3(699.5, -60, 16.5));
                work(waterer);
            });
            case 132 -> shot("04-waterer");
            // ---- The ice house ----
            case 140 -> server(mc, p -> {
                camera(mc, new Vec3(709.5, -58.3, 8.5), new Vec3(706.3, -59.5, 4.8));
                work(ice);
            });
            case 154 -> shot("05-ice-house");
            // ---- The golem keeper ----
            case 190 -> server(mc, p -> {
                System.out.println("LIFE_PREVIEW ice in barrel " + countIce(p.serverLevel()));
                camera(mc, new Vec3(688.0, -58.0, 4.5), new Vec3(687.5, -59.0, 0.5));
                work(keeper);
            });
            case 203 -> shot("06-golem-keeper");
            // ---- The harvester ----
            case 240 -> server(mc, p -> {
                camera(mc, new Vec3(696.5, -57.5, 13.0), new Vec3(692.0, -60, 16.5));
                work(harvester);
            });
            case 263 -> shot("07-harvester");
            // ---- The lookout ----
            case 280 -> server(mc, p -> {
                Zombie z = EntityType.ZOMBIE.create(p.serverLevel());
                z.moveTo(721.5, -60, 1.5, 90, 0);
                z.setNoAi(true);
                z.setInvulnerable(true);
                p.serverLevel().addFreshEntity(z);
                lookout.setNoAi(false);
                camera(mc, new Vec3(710.0, -57.8, 6.0), new Vec3(715.5, -59, 0.5));
            });
            case 330 -> shot("08-lookout");
            // ---- The courier ----
            case 340 -> server(mc, p -> work(courier));
            case 400 -> server(mc, p -> camera(mc, new Vec3(694.5, -58.5, -10.5), courier.position()));
            case 404 -> shot("09-courier");
            // ---- The post runner ----
            case 410 -> server(mc, p -> {
                camera(mc, new Vec3(700.5, -52.0, -14.5), new Vec3(700.5, -60, 2.5));
                work(runner);
            });
            case 418 -> server(mc, p -> {
                Vec3 r = runner.position(), ahead = r.add(runner.getDeltaMovement().multiply(1, 0, 1).normalize().scale(5));
                camera(mc, ahead.add(2.5, 2.2, 2.5), r.add(0, 1.0, 0));
            });
            case 422 -> shot("10-post-runner");
            // ---- A favour ----
            case 440 -> server(mc, p -> {
                p.getAbilities().flying = false; p.onUpdateAbilities();
                move(p, new Vec3(696.5, -60, -0.5), 135, 0);
                Favours.Favour f = Favours.ask(smith, GiftedWork.Job.SMITH);
                System.out.println("LIFE_PREVIEW favour " + f.describe() + " for " + f.reward());
                camera(mc, new Vec3(699.0, -57.6, 2.5), new Vec3(695.3, -58.6, -1.4));
                mc.options.hideGui = false;
            });
            case 540 -> shot("11-favour-asked");
            case 546 -> server(mc, p -> {
                Favours.Favour f = Favours.open(smith);
                if (f != null) {
                    p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(f.item(), f.count()));
                    Favours.deliver(p, smith);
                }
            });
            case 556 -> shot("12-favour-done");
            // ---- The chatter, with a radio in hand ----
            case 562 -> server(mc, p -> {
                p.setItemInHand(InteractionHand.MAIN_HAND, EmergencyBand.radio());
                p.getAbilities().flying = true; p.onUpdateAbilities();
                move(p, new Vec3(700.5, -55, -22.5), 0, 15);
                camera(mc, new Vec3(700.5, -53.4, -22.5), new Vec3(700.5, -58, 0.5));
                for (int i = 0; i < 3; i++) System.out.println("LIFE_PREVIEW chatter " + BandChatter.beat(p.serverLevel(), java.util.List.of(p)));
            });
            case 600 -> shot("13-radio-chatter");
            case 620 -> {
                System.out.println("LIFE_PREVIEW_RESULT " + (photos >= PHOTOS ? "PASS" : "FAIL") + ": " + photos + " photos");
                if (photos < PHOTOS) PreviewQuiet.report("LIFE_PREVIEW FAIL only " + photos + " photos");
                PreviewQuiet.quit();
            }
            default -> { }
        }
    }

    private static int countIce(ServerLevel level) {
        if (!(level.getBlockEntity(new BlockPos(706, -60, 4)) instanceof net.minecraft.world.Container box)) return -1;
        int n = 0;
        for (int i = 0; i < box.getContainerSize(); i++) if (box.getItem(i).is(Items.ICE) || box.getItem(i).is(Items.PACKED_ICE)) n += box.getItem(i).getCount();
        return n;
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post e) {
        if (pending == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve(pending);
            Files.createDirectories(out.getParent());
            image.writeToFile(out);
            photos++;
            System.out.println("LIFE_CAPTURE " + pending);
        } catch (Exception ex) { PreviewQuiet.report("LIFE_PREVIEW FAIL capture " + pending + ": " + ex); }
        pending = null;
    }
}
