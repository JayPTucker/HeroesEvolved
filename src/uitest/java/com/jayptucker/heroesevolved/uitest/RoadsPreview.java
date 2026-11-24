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

import com.jayptucker.heroesevolved.company.Pursuit;
import com.jayptucker.heroesevolved.gifted.Gift;
import com.jayptucker.heroesevolved.gifted.GiftedMob;
import com.jayptucker.heroesevolved.gifted.GiftedMobs;
import com.jayptucker.heroesevolved.gifted.Rarity;
import com.jayptucker.heroesevolved.trace.Traces;
import com.jayptucker.heroesevolved.travel.Caravan;
import com.jayptucker.heroesevolved.travel.Party;
import com.jayptucker.heroesevolved.travel.Refugees;
import com.jayptucker.heroesevolved.travel.Travellers;
import com.jayptucker.heroesevolved.village.Days;
import com.jayptucker.heroesevolved.village.Growth;
import com.jayptucker.heroesevolved.village.Rebuild;
import com.jayptucker.heroesevolved.village.VillageMemory;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.monster.Pillager;
import java.util.UUID;

/**
 * Power traces, travellers on the road and villages that grow, photographed: marks of fire, frost and static and a
 * reader's reading, a renegade's trail; a pedlar, a travelling healer, a caravan and its ambush, refugees with the Company
 * behind them and settling; a house going up and finished; a struggling village. Run with
 * {@code ./gradlew runClient --init-script tools/roads-preview.init.gradle}; shots land in
 * {@code build/roads-preview/screenshots/}.
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class RoadsPreview {
    private static final int PHOTOS = 31;
    private static boolean started;
    private static int ticks, age, photos;
    private static String pending;
    private static Caravan caravan;
    private static Refugees refugees;
    private static Rebuild.Site site;
    private static Villager sheltering;
    private static BlockPos shelterBed;

    private static boolean on() { return Boolean.getBoolean("heroes.preview.roads"); }

    private static void server(Minecraft mc, Consumer<ServerPlayer> action) {
        var id = mc.player.getUUID();
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            try { action.accept(server.getPlayerList().getPlayer(id)); }
            catch (RuntimeException error) { PreviewQuiet.report("ROADS_PREVIEW FAIL step at " + age + ": " + error); error.printStackTrace(); }
        });
    }

    private static void camera(Minecraft mc, Vec3 at, Vec3 look) { mc.execute(() -> TempestCamera.fix(at, look)); }

    private static void shot(String name) { pending = "roads-" + name + ".png"; }

    private static void move(ServerPlayer p, Vec3 at, float yaw, float pitch) {
        p.teleportTo(p.getServer().overworld(), at.x, at.y, at.z, yaw, pitch);
        p.setDeltaMovement(Vec3.ZERO);
    }

    private static void set(ServerLevel level, int x, int y, int z, BlockState state) {
        BlockPos p = new BlockPos(x, y, z);
        level.setBlockAndUpdate(p, state);
        // A pane or stair joins what is already beside it (what comes later joins it in turn).
        if (com.jayptucker.heroesevolved.village.Rebuild.settles(state)) level.setBlockAndUpdate(p, net.minecraft.world.level.block.Block.updateFromNeighbourShapes(state, level, p));
    }

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

    /** Rebuilds the grown village's newest home bigger ({@code town}: as a town house), halfway done, and frames it. */
    private static void upgrade(Minecraft mc, ServerPlayer p, int built, boolean town) {
        ServerLevel level = p.serverLevel();
        var v = VillageMemory.of(level, new BlockPos(1100, -59, 0));
        v.setBuiltForPreview(built);
        for (var h : v.homesForPreview()) System.out.println("ROADS_PREVIEW home " + h + " standing "
                + com.jayptucker.heroesevolved.village.Projects.standing(level, v, h) + " listed " + com.jayptucker.heroesevolved.village.Projects.homes(level, v).contains(h));
        com.jayptucker.heroesevolved.village.Projects.rebuild(level, v, town ? com.jayptucker.heroesevolved.village.Projects.Tier.TOWN
                : com.jayptucker.heroesevolved.village.Projects.Tier.VILLAGE);
        com.jayptucker.heroesevolved.village.Projects.fill(level, v);
        site = Rebuild.all(level).stream().filter(s -> s.growth() && s.bell().equals(v.bell())).findFirst().orElse(null);
        System.out.println("ROADS_PREVIEW rebuild " + (town ? "town " : "") + (site == null ? "NONE" : site.center() + " clearing " + site.clearing() + " of " + site.size()));
        if (site != null) Rebuild.hurryTo(level, site, (site.clearing() + (site.size() - site.clearing()) * 0.45F) / site.size());
        houseCamera(mc, site, 12, 3);
    }

    /** In front of a building site's house, {@code back} blocks out at {@code up} above its floor, looking at it. */
    private static void houseCamera(Minecraft mc, Rebuild.Site s, double back, double up) {
        if (s == null || s.home() == null) return;
        var template = mc.getSingleplayerServer().overworld().getStructureManager().get(s.home().template()).orElse(null);
        net.minecraft.core.Direction front = template == null ? net.minecraft.core.Direction.NORTH
                : s.home().rotation().rotate(Growth.front(template, s.home().template()));
        Vec3 c = Vec3.atBottomCenterOf(s.center()).add(0, 1, 0);
        clearView(mc, c.add(0, up * 0.5 + 1.5, 0), Math.atan2(front.getStepZ(), front.getStepX()), back, up - up * 0.5 - 1.5, 6);
    }

    /** A camera {@code back} out from {@code look} at {@code base} radians, {@code up} above it, turned until its view is clear. */
    private static void clearView(Minecraft mc, Vec3 look, double base, double back, double up, double near) {
        Vec3 c = look;
        var level = mc.getSingleplayerServer().overworld();
        // From the front, turning a little each way until nothing (another house) stands between the camera and it.
        for (double turn : new double[]{0.35, -0.35, 0.8, -0.8, 1.3, -1.3, 0, Math.PI}) {
            double a = base + turn;
            Vec3 at = c.add(Math.cos(a) * back, up, Math.sin(a) * back);
            var hit = level.clip(new net.minecraft.world.level.ClipContext(at, look, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
            if (hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS || hit.getLocation().distanceTo(look) < near) {
                camera(mc, at, look);
                return;
            }
        }
        camera(mc, c.add(Math.cos(base) * back, up, Math.sin(base) * back), look);
    }

    /** In front of a village's notice board at eye height, close enough to read the signs. */
    private static void boardCamera(Minecraft mc, ServerPlayer p, com.jayptucker.heroesevolved.village.Projects.Board b) {
        Vec3 front = Vec3.atBottomCenterOf(b.at(0, 0, 4)).add(0, 1.62, 0);
        Vec3 face = Vec3.atCenterOf(b.at(0, 1, 1)).add(0, 0.5, 0);
        move(p, Vec3.atBottomCenterOf(b.at(0, 0, 5)), b.facing().getOpposite().toYRot(), 10);
        camera(mc, front, face);
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
            mc.createWorldOpenFlows().createFreshLevel("Roads-" + System.currentTimeMillis(),
                    new LevelSettings("Roads preview", GameType.SURVIVAL, false, Difficulty.NORMAL, true, rules, WorldDataConfiguration.DEFAULT),
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

    private static void script(Minecraft mc, int t) {
        switch (t) {
            // ---- Power traces ----
            case 0 -> server(mc, p -> {
                mc.options.hideGui = true;
                ServerLevel level = p.serverLevel();
                hold(level, 700, 0);
                UUID wren = UUID.randomUUID();
                long day = Days.DAY;
                Traces.leave(level, Traces.Kind.SCORCH, new BlockPos(700, -60, 0), wren, "Wren", true, 1, 3 * day, false);
                Traces.leave(level, Traces.Kind.FROST, new BlockPos(708, -60, 3), UUID.randomUUID(), "Ash", true, 1, 3 * day, false);
                Traces.leave(level, Traces.Kind.STATIC, new BlockPos(693, -60, 5), UUID.randomUUID(), "Rue", true, 0.6F, 3 * day, false);
                Traces.leave(level, Traces.Kind.HUM, new BlockPos(702, -60, 9), UUID.randomUUID(), "Juno", true, 1, 3 * day, false);
                p.setData(com.jayptucker.heroesevolved.data.ModDataAttachments.PLAYER_ABILITIES,
                        com.jayptucker.heroesevolved.ability.data.PlayerAbilityData.empty().assignDormant(ModAbilities.TELEPATHY_ID).unlock(ModAbilities.TELEPATHY_ID));
                com.jayptucker.heroesevolved.network.PlayerPowerSyncService.sync(p);
                move(p, new Vec3(700.5, -56, -6.5), 0, 30);
                camera(mc, new Vec3(700.5, -57.0, -7.5), new Vec3(701.5, -60, 3.5));
            });
            case 50 -> shot("01-traces");
            case 52 -> server(mc, p -> {
                mc.options.hideGui = false;
                long now = Days.clock(p.serverLevel());
                for (var tr : Traces.all(p.serverLevel()))
                    if (tr.name().equals("Wren")) p.sendSystemMessage(Component.literal(Traces.describe(tr, true, now)).withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
                for (var tr : Traces.all(p.serverLevel()))
                    if (tr.name().equals("Ash")) p.sendSystemMessage(Component.literal(Traces.describe(tr, false, now)).withStyle(net.minecraft.ChatFormatting.GRAY));
                camera(mc, new Vec3(699.5, -56.5, -4.5), new Vec3(700.5, -60, 0.5));
            });
            case 90 -> shot("02-reading");
            case 92 -> server(mc, p -> {
                mc.options.hideGui = true;
                ServerLevel level = p.serverLevel();
                Pillager mob = EntityType.PILLAGER.create(level);
                mob.moveTo(720.5, -60, -20.5, 0, 0);
                mob.getPersistentData().putBoolean(GiftedMobs.ROLLED, true);
                mob.setNoAi(true);
                level.addFreshEntity(mob);
                GiftedMob g = GiftedMobs.bestow(mob, ModAbilities.ELECTROKINESIS_ID, Rarity.RARE, true, Gift.Origin.WILD, null, "Vesna Stormcaller");
                for (int i = 0; i < 7; i++) {
                    mob.teleportTo(720.5 + i * 7, -60, -20.5 + i * 3);
                    Traces.trail(mob, g.gift());
                }
                move(p, new Vec3(735.5, -55, -28.5), 0, 30);
                camera(mc, new Vec3(728.5, -55.0, -32.5), new Vec3(745.5, -60, -12.5));
            });
            case 140 -> shot("03-trail");
            // ---- Travellers ----
            case 145 -> server(mc, p -> {
                ServerLevel level = p.serverLevel();
                BlockPos bell = village(level, 900, 0, 4, 8);
                move(p, new Vec3(900.5, -55, -40.5), 0, 20);
                Travellers.start(level, Party.Kind.PEDLAR, new Vec3(900.5, -60, -50.5), bell);
                Villager hurt = level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(bell).inflate(12)).getFirst();
                hurt.setHealth(6);
            });
            case 200 -> server(mc, p -> {
                mc.options.hideGui = false;
                var trader = Travellers.all().getFirst().members().getFirst();
                camera(mc, trader.position().add(3.5, 1.9, 2.5), trader.position().add(0, 1.3, 0));
            });
            case 208 -> shot("04-pedlar");
            case 210 -> server(mc, p -> {
                mc.options.hideGui = true;
                ServerLevel level = p.serverLevel();
                BlockPos bell = new BlockPos(900, -59, 0);
                Villager hurt = level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(bell).inflate(12)).getFirst();
                Travellers.start(level, Party.Kind.HEALER, hurt.position().add(2, 0, 1), bell);
                camera(mc, hurt.position().add(-3.0, 2.0, 3.5), hurt.position().add(1, 1.0, 0.5));
            });
            case 272 -> shot("05-healer");
            case 274 -> server(mc, p -> {
                ServerLevel level = p.serverLevel();
                caravan = (Caravan) Travellers.start(level, Party.Kind.CARAVAN, new Vec3(940.5, -60, -30.5), new BlockPos(900, -59, 0));
                move(p, new Vec3(935.5, -55, -40.5), 0, 20);
            });
            case 340 -> server(mc, p -> {
                var lead = caravan.drovers().getFirst();
                camera(mc, lead.position().add(5.5, 2.4, -4.5), lead.position().add(-1, 0.8, 1));
            });
            case 346 -> shot("06-caravan");
            case 348 -> server(mc, p -> caravan.ambush());
            case 380 -> server(mc, p -> {
                var lead = caravan.drovers().getFirst();
                Vec3 foe = caravan.ambushers().getFirst().position();
                Vec3 back = lead.position().subtract(foe).multiply(1, 0, 1).normalize();
                Vec3 side = new Vec3(-back.z, 0, back.x);
                camera(mc, lead.position().add(back.scale(5)).add(side.scale(3)).add(0, 3, 0), lead.position().add(foe).scale(0.5).add(0, 0.8, 0));
            });
            case 384 -> shot("07-ambush");
            case 386 -> server(mc, p -> {
                for (var m : caravan.ambushers()) m.discard();
                ServerLevel level = p.serverLevel();
                refugees = (Refugees) Travellers.start(level, Party.Kind.REFUGEES, new Vec3(860.5, -60, -36.5), new BlockPos(900, -59, 0));
                move(p, new Vec3(868.5, -58, -26.5), 0, 20);
                Pursuit.start(level, p, refugees.family(), new Vec3(852.5, -60, -44.5));
            });
            case 440 -> server(mc, p -> {
                var kid = refugees.family().getFirst();
                var hunt = com.jayptucker.heroesevolved.company.CompanyOps.all().stream().filter(o -> o instanceof Pursuit).findFirst().orElse(null);
                Vec3 agent = hunt == null || hunt.agents().isEmpty() ? kid.position().add(-8, 0, -8) : hunt.agents().getFirst().position();
                Vec3 mid = kid.position().add(agent).scale(0.5);
                Vec3 along = kid.position().subtract(agent).multiply(1, 0, 1).normalize();
                camera(mc, mid.add(-along.z * 9, 3.5, along.x * 9), mid.add(0, 0.9, 0));
            });
            case 446 -> shot("08-refugees");
            case 448 -> server(mc, p -> {
                com.jayptucker.heroesevolved.company.CompanyOps.callOffAll();
                refugees.hurry(1000);
                mc.options.hideGui = false;
                camera(mc, new Vec3(900.5, -54, -18.5), new Vec3(900.5, -59, 0.5));
                move(p, new Vec3(900.5, -58, -14.5), 0, 20);
            });
            case 470 -> shot("09-settled");
            // ---- A village that grows: its notice board, a delivery, the building going up ----
            case 475 -> server(mc, p -> {
                mc.options.hideGui = true;
                ServerLevel level = p.serverLevel();
                BlockPos bell = village(level, 1100, 0, 4, 8);
                var v = VillageMemory.of(level, bell);
                com.jayptucker.heroesevolved.village.Projects.consider(level, v);
                System.out.println("ROADS_PREVIEW board " + (v.board() == null ? "NONE" : v.board().origin() + " facing " + v.board().facing())
                        + " " + com.jayptucker.heroesevolved.village.Projects.describe(level, v));
                if (v.board() != null) boardCamera(mc, p, v.board());
            });
            case 500 -> shot("10-board");
            case 504 -> server(mc, p -> {
                ServerLevel level = p.serverLevel();
                var v = VillageMemory.of(level, new BlockPos(1100, -59, 0));
                if (v.board() == null) return;
                if (level.getBlockEntity(v.board().chest()) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest) {
                    chest.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_LOG, 16));
                    chest.setItem(1, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE, 20));
                    chest.setItem(2, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GLASS_PANE, 3));
                    chest.setItem(3, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WHITE_BED, 1));
                }
                com.jayptucker.heroesevolved.village.Projects.collect(level, v, p);
                mc.options.hideGui = false;
            });
            case 524 -> shot("11-board-filling");
            case 528 -> server(mc, p -> {
                mc.options.hideGui = true;
                ServerLevel level = p.serverLevel();
                var v = VillageMemory.of(level, new BlockPos(1100, -59, 0));
                com.jayptucker.heroesevolved.village.Projects.fill(level, v);
                site = Rebuild.all(level).stream().filter(s -> s.growth() && s.bell().equals(v.bell())).findFirst().orElse(null);
                System.out.println("ROADS_PREVIEW house site " + (site == null ? "NONE" : site.center() + " " + site.size()));
                if (site != null) Rebuild.hurryTo(level, site, 0.45F);
                move(p, new Vec3(1100.5, -50, -50.5), 0, 20);
            });
            case 548 -> server(mc, p -> {
                if (site == null) return;
                Vec3 c = Vec3.atCenterOf(site.center());
                Vec3 away = c.subtract(Vec3.atCenterOf(new BlockPos(1100, -59, 0))).multiply(1, 0, 1).normalize();
                camera(mc, c.add(away.scale(11)).add(away.z * 6, 2.5, -away.x * 6), c.add(0, 2, 0));
            });
            case 554 -> shot("12-house-going-up");
            case 556 -> server(mc, p -> { if (site != null) Rebuild.hurry(p.serverLevel(), site, 100_000); });
            case 576 -> mc.options.hideGui = false;
            case 582 -> shot("13-new-house");
            case 586 -> server(mc, p -> {
                mc.options.hideGui = true;
                var v = VillageMemory.of(p.serverLevel(), new BlockPos(1100, -59, 0));
                if (v.board() != null) boardCamera(mc, p, v.board());
            });
            case 606 -> shot("14-board-finished");
            // ---- Homes rebuilt bigger: the village's small house becomes a bigger one, then a two-storey town house ----
            case 610 -> server(mc, p -> upgrade(mc, p, 4, false));
            case 630 -> shot("15-rebuilding");
            case 632 -> server(mc, p -> { if (site != null) Rebuild.hurry(p.serverLevel(), site, 100_000); houseCamera(mc, site, 12, 3); });
            case 652 -> shot("16-bigger-home");
            case 656 -> server(mc, p -> upgrade(mc, p, 10, true));
            case 676 -> shot("17-second-storey-going-up");
            case 678 -> server(mc, p -> { if (site != null) Rebuild.hurry(p.serverLevel(), site, 100_000); houseCamera(mc, site, 10, 1.6); });
            case 698 -> shot("18-town-house");
            case 700 -> server(mc, p -> {
                ServerLevel level = p.serverLevel();
                var v = VillageMemory.of(level, new BlockPos(1100, -59, 0));
                site = Growth.grow(level, v, com.jayptucker.heroesevolved.village.Projects.TOWN_HOUSES.get(0));
                if (site != null) Rebuild.hurry(level, site, 100_000);
                houseCamera(mc, site, 12, 2.5);
            });
            case 720 -> shot("19-town-house-2");
            case 722 -> server(mc, p -> {
                // From above and back: the village as it has grown.
                camera(mc, new Vec3(1100.5 - 30, -38, -30.5), new Vec3(1100.5, -58, 6.5));
            });
            case 742 -> shot("20-grown-village");
            // ---- Market day, and a home of your own ----
            case 744 -> server(mc, p -> {
                ServerLevel level = p.serverLevel();
                var v = VillageMemory.of(level, new BlockPos(1100, -59, 0));
                v.setBuiltForPreview(10);
                boolean opened = com.jayptucker.heroesevolved.village.Markets.open(level, v, com.jayptucker.heroesevolved.village.Projects.Tier.TOWN);
                System.out.println("ROADS_PREVIEW market " + opened + " " + (v.market() == null ? "" : v.market().kinds() + " at " + v.market().stalls()));
                move(p, new Vec3(1100.5, -60, -16.5), 0, 0);
                camera(mc, new Vec3(1100.5 + 16, -48, -16.5), new Vec3(1100.5, -59, 0.5));
            });
            case 764 -> shot("21-market-day");
            case 766 -> server(mc, p -> {
                var v = VillageMemory.of(p.serverLevel(), new BlockPos(1100, -59, 0));
                if (v.market() == null || v.market().stalls().isEmpty()) return;
                BlockPos stall = v.market().stalls().getFirst();
                var trader = com.jayptucker.heroesevolved.village.Markets.traders(p.serverLevel(), v).getFirst();
                Vec3 front = Vec3.directionFromRotation(0, trader.getYRot());
                clearView(mc, Vec3.atBottomCenterOf(stall).add(0, 1.2, 0), Math.atan2(front.z, front.x), 4.0, 0.5, 1.2);
            });
            case 786 -> shot("22-market-stall");
            case 788 -> server(mc, p -> {
                ServerLevel level = p.serverLevel();
                var v = VillageMemory.of(level, new BlockPos(1100, -59, 0));
                com.jayptucker.heroesevolved.village.Markets.close(level, v, false);
                com.jayptucker.heroesevolved.village.VillageMemory.thanked(level, v.bell(), p, 3);
                if (v.board() != null) boardCamera(mc, p, v.board());
                com.jayptucker.heroesevolved.village.Homes.ask(level, v, p);
                com.jayptucker.heroesevolved.village.Homes.ask(level, v, p);
                site = Rebuild.all(level).stream().filter(s -> s.growth() && s.bell().equals(v.bell())).findFirst().orElse(null);
                System.out.println("ROADS_PREVIEW home site " + (site == null ? "NONE" : site.center() + " " + site.home()));
                mc.options.hideGui = false;
            });
            case 808 -> shot("23-asking-for-a-home");
            case 810 -> server(mc, p -> {
                mc.options.hideGui = false;
                if (site == null) return;
                Rebuild.hurry(p.serverLevel(), site, 100_000);
                houseCamera(mc, site, 13, 7);
            });
            case 830 -> shot("24-your-home");
            // ---- A village that struggles ----
            case 834 -> server(mc, p -> {
                mc.options.hideGui = true;
                ServerLevel level = p.serverLevel();
                BlockPos bell = village(level, 1300, 0, 4, 8);
                VillageMemory.record(level, bell, "a renegade", VillageMemory.Outcome.HIT, null, java.util.List.of(), 3, 5);
                VillageMemory.record(level, bell, "a meltdown", VillageMemory.Outcome.HIT, null, java.util.List.of(), 2, 4);
                int worn = 0;
                for (int i = 0; i < 12; i++) worn += Growth.wear(level, bell);
                System.out.println("ROADS_PREVIEW worn " + worn + " mood " + Growth.mood(level, VillageMemory.of(level, bell)));
                move(p, new Vec3(1300.5, -50, -40.5), 0, 20);
                BlockPos web = null;
                for (BlockPos q : BlockPos.betweenClosed(bell.offset(-32, -4, -32), bell.offset(32, 6, 32)))
                    if (level.getBlockState(q).is(Blocks.COBWEB)) { web = q.immutable(); break; }
                System.out.println("ROADS_PREVIEW cobweb at " + web);
                if (web != null) {
                    Vec3 w = Vec3.atCenterOf(web);
                    Vec3 out = w.subtract(Vec3.atCenterOf(bell)).multiply(1, 0, 1).normalize();
                    camera(mc, w.add(out.scale(-3.5)).add(out.z * 2, 1.0, -out.x * 2), w);
                } else camera(mc, new Vec3(1316.5, -57.5, 4.5), new Vec3(1311.5, -59, 10.5));
            });
            case 864 -> shot("25-struggling");
            case 884 -> server(mc, p -> {
                mc.options.hideGui = true;
                var v = VillageMemory.of(p.serverLevel(), new BlockPos(1100, -59, 0));
                com.jayptucker.heroesevolved.village.Homes.order(p.serverLevel(), v, UUID.randomUUID(), "Alex");
                com.jayptucker.heroesevolved.village.Homes.order(p.serverLevel(), v, UUID.randomUUID(), "Robin");
                com.jayptucker.heroesevolved.village.Projects.consider(p.serverLevel(), v);
                var b = v.board();
                move(p, Vec3.atBottomCenterOf(b.at(0, 0, -5)), b.facing().toYRot(), 10);
                camera(mc, Vec3.atBottomCenterOf(b.at(0, 0, -4)).add(0, 1.62, 0), Vec3.atCenterOf(b.at(0, 1, -1)).add(0, .5, 0));
            });
            case 908 -> shot("26-build-schedule");
            case 912 -> server(mc, p -> {
                var level = p.serverLevel();
                var bell = new BlockPos(1100, -59, 0);
                var paths = Rebuild.all(level).stream().filter(s -> s.paving() && s.bell().equals(bell)).toList();
                int paved = 0;
                for (var path : paths) { paved += path.size(); Rebuild.hurry(level, path, 100_000); }
                System.out.println("ROADS_PATHS " + (paved > 0 ? "PASS" : "FAIL") + ": " + paths.size() + " connections, " + paved + " work blocks");
                if (paved == 0) PreviewQuiet.report("ROADS_PATHS FAIL no connections planned");
                var v = VillageMemory.of(level, bell);
                var home = v.homesForPreview().getLast();
                var c = Vec3.atCenterOf(home.origin());
                var middle = c.add(Vec3.atCenterOf(bell)).scale(.5);
                move(p, middle.add(0, 20, -28), 0, 35);
                camera(mc, middle.add(5, 24, -28), middle.add(0, -1, 0));
                site = paths.isEmpty() ? null : paths.getLast();
            });
            case 936 -> shot("27-paved-connections");
            case 940 -> server(mc, p -> {
                if (site == null) return;
                var level = p.serverLevel();
                var home = site.home();
                var template = level.getStructureManager().get(home.template()).orElseThrow();
                var box = template.getBoundingBox(new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings()
                        .setRotation(home.rotation()), home.origin());
                BlockPos door = null;
                for (BlockPos at : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()))
                    if (level.getBlockState(at).getBlock() instanceof DoorBlock && level.getBlockState(at).getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER)
                        door = at.immutable();
                if (door == null) { PreviewQuiet.report("ROADS_PATHS FAIL no front door"); return; }
                Direction front = home.rotation().rotate(Growth.front(template, home.template()));
                move(p, Vec3.atCenterOf(door).add(front.getStepX() * 18, 8, front.getStepZ() * 18), front.getOpposite().toYRot(), 10);
                clearView(mc, Vec3.atBottomCenterOf(door).add(0, 1.2, 0), Math.atan2(front.getStepZ(), front.getStepX()), 9, .42, .5);
            });
            case 964 -> shot("28-path-at-home");
            case 968 -> server(mc, p -> {
                var level = p.serverLevel();
                BlockPos bell = village(level, 1500, 0, 4, 8);
                var v = VillageMemory.of(level, bell);
                site = Growth.grow(level, v, net.minecraft.resources.ResourceLocation.withDefaultNamespace("village/plains/houses/plains_small_house_1"));
                if (site == null) { PreviewQuiet.report("ROADS_ENTRANCE FAIL no house"); return; }
                Rebuild.hurry(level, site, 100_000);
                for (var path : Rebuild.all(level)) if (path.paving() && path.bell().equals(bell)) Rebuild.hurry(level, path, 100_000);
                houseCamera(mc, site, 9, 1.62);
                move(p, Vec3.atCenterOf(site.center()).add(18, 6, 18), 0, 10);
            });
            case 1000 -> shot("29-visible-entrance-steps");
            case 1004 -> server(mc, p -> {
                var level = p.serverLevel();
                var bell = new BlockPos(1500, -59, 0);
                // A home away from the uncontrolled power, with an ordinary villager allowed to seek it naturally.
                house(level, 1532, -5, true);
                shelterBed = new BlockPos(1534, -60, -4);
                var people = com.jayptucker.heroesevolved.village.Villages.members(level, bell);
                sheltering = people.getFirst();
                sheltering.setPos(Vec3.atBottomCenterOf(bell.offset(4, -1, 0)));
                sheltering.setNoAi(false);
                sheltering.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.HOME,
                        net.minecraft.core.GlobalPos.of(level.dimension(), shelterBed));
                for (var v : people) if (v != sheltering) {
                    v.setNoAi(false);
                    v.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.HOME);
                }
                Villager source = EntityType.VILLAGER.create(level);
                source.moveTo(Vec3.atBottomCenterOf(bell.offset(0, -1, 2)));
                source.setNoAi(true);
                source.getPersistentData().putBoolean(GiftedMobs.ROLLED, true);
                level.addFreshEntity(source);
                com.jayptucker.heroesevolved.mimicry.GiftedVillagers.give(source, ModAbilities.PYROKINESIS_ID);
                Renegades.awaken(source, true);
                move(p, Vec3.atCenterOf(bell).add(14, 7, -24), 0, 10);
                camera(mc, Vec3.atCenterOf(bell).add(3, 2.5, -12), Vec3.atCenterOf(bell).add(3, 1, 1));
            });
            case 1012 -> shot("30-uncontrolled-gift-panic");
            case 1380 -> server(mc, p -> {
                boolean home = sheltering != null && sheltering.distanceToSqr(Vec3.atCenterOf(shelterBed)) <= 9;
                System.out.println("ROADS_SHELTER " + (home ? "PASS" : "FAIL") + ": villager at " + (sheltering == null ? "none" : sheltering.position()) + ", home " + shelterBed);
                if (!home) PreviewQuiet.report("ROADS_SHELTER FAIL villager did not reach home");
                move(p, Vec3.atCenterOf(shelterBed).add(12, 6, 12), 0, 10);
                camera(mc, new Vec3(1535.3, -58.5, -3.5), sheltering.position().add(0, 1, 0));
            });
            case 1406 -> shot("31-sheltered-at-home");
            case 1430 -> {
                System.out.println("ROADS_PREVIEW_RESULT " + (photos >= PHOTOS ? "PASS" : "FAIL") + ": " + photos + " photos");
                if (photos < PHOTOS) PreviewQuiet.report("ROADS_PREVIEW FAIL only " + photos + " photos");
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
            System.out.println("ROADS_CAPTURE " + pending);
        } catch (Exception ex) { PreviewQuiet.report("ROADS_PREVIEW FAIL capture " + pending + ": " + ex); }
        pending = null;
    }
}
