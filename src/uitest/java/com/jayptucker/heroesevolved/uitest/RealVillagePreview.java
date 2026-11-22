package com.jayptucker.heroesevolved.uitest;

import com.jayptucker.heroesevolved.client.effects.TempestCamera;
import com.jayptucker.heroesevolved.story.bunker.CryoChamberService;
import com.jayptucker.heroesevolved.story.prologue.PrologueService;
import com.jayptucker.heroesevolved.village.Growth;
import com.jayptucker.heroesevolved.village.Markets;
import com.jayptucker.heroesevolved.village.Projects;
import com.jayptucker.heroesevolved.village.Rebuild;
import com.jayptucker.heroesevolved.village.VillageMemory;
import com.jayptucker.heroesevolved.village.Villages;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The village systems on real generated villages, one of each kind (plains, desert, savanna, snowy, taiga) in a normal
 * world: the notice board, the tier, the village's own houses read from its structure, one rebuilt bigger (before and
 * after), a market, and a town house in the kind's colours, with every pane, stair and door we put down checked. Run with
 * {@code ./gradlew runClient --init-script tools/real-village-preview.init.gradle}; shots land in
 * {@code build/real-village-preview/screenshots/} and each village prints a {@code REAL_VILLAGE} line.
 */
@EventBusSubscriber(modid = "heroesevolved", value = Dist.CLIENT)
public final class RealVillagePreview {
    private static final long SEED = 20261121L;
    private static final int RETRIES = 90;

    private interface Step { boolean run(Minecraft mc, ServerPlayer p); }

    private record Shot(String name) {}

    private static final List<Object> SCRIPT = new ArrayList<>();
    private static final AtomicInteger STATE = new AtomicInteger();   // 0 idle, 1 running, 2 done, 3 not yet
    private static boolean started, built;
    private static int ticks, age, index, waitUntil, retries, photos, expected, failures;
    private static String pending;

    // The village being looked at now.
    private static String kind;
    private static BlockPos found, bell;
    private static VillageMemory.Village village;
    private static Projects.Home old;
    private static Rebuild.Site site;
    private static final List<String> notes = new ArrayList<>();

    private static boolean on() { return Boolean.getBoolean("heroes.preview.realvillages"); }

    private static void camera(Minecraft mc, Vec3 at, Vec3 look) { mc.execute(() -> TempestCamera.fix(at, look)); }

    private static void fail(String why) {
        failures++;
        notes.add("FAIL " + why);
        System.out.println("REAL_VILLAGE FAIL " + kind + ": " + why);
    }

    // ------------------------------------------------------------------
    // The script
    // ------------------------------------------------------------------

    private static void build() {
        List<ResourceKey<Structure>> kinds = List.of(BuiltinStructures.VILLAGE_PLAINS, BuiltinStructures.VILLAGE_DESERT,
                BuiltinStructures.VILLAGE_SAVANNA, BuiltinStructures.VILLAGE_SNOWY, BuiltinStructures.VILLAGE_TAIGA);
        for (ResourceKey<Structure> key : kinds) {
            String name = key.location().getPath().replace("village_", "");
            SCRIPT.add((Step) (mc, p) -> find(p, key, name));
            SCRIPT.add(20);
            SCRIPT.add((Step) (mc, p) -> arrived(p));
            SCRIPT.add(40);
            SCRIPT.add((Step) (mc, p) -> board(mc, p));
            SCRIPT.add(30);
            SCRIPT.add(new Shot(name + "-1-board"));
            SCRIPT.add((Step) (mc, p) -> chooseHome(mc, p));
            SCRIPT.add(30);
            SCRIPT.add(new Shot(name + "-2-old-home"));
            SCRIPT.add((Step) (mc, p) -> rebuild(mc, p));
            SCRIPT.add(30);
            SCRIPT.add(new Shot(name + "-3-rebuilt-home"));
            SCRIPT.add((Step) (mc, p) -> market(mc, p));
            SCRIPT.add(30);
            SCRIPT.add(new Shot(name + "-4-market"));
            SCRIPT.add((Step) (mc, p) -> townHouse(mc, p));
            SCRIPT.add(30);
            SCRIPT.add(new Shot(name + "-5-town-house"));
            SCRIPT.add((Step) (mc, p) -> carry(mc, p));
            SCRIPT.add(80);
            SCRIPT.add(new Shot(name + "-6-carrying"));
            SCRIPT.add((Step) (mc, p) -> blowUp(mc, p));
            SCRIPT.add(30);
            SCRIPT.add(new Shot(name + "-7-blown-up"));
            SCRIPT.add((Step) (mc, p) -> mend(mc, p));
            SCRIPT.add(300);
            SCRIPT.add(new Shot(name + "-8-mending"));
            SCRIPT.add((Step) (mc, p) -> mended(mc, p));
            SCRIPT.add(30);
            SCRIPT.add(new Shot(name + "-9-mended"));
            SCRIPT.add((Step) (mc, p) -> summary(p));
            expected += 9;
        }
    }

    /** Finds the nearest village of {@code key} and flies there. */
    private static boolean find(ServerPlayer p, ResourceKey<Structure> key, String name) {
        kind = name;
        notes.clear();
        found = bell = null;
        village = null;
        old = null;
        site = null;
        ServerLevel level = p.serverLevel();
        var holder = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolderOrThrow(key);
        var at = level.getChunkSource().getGenerator().findNearestMapStructure(level, HolderSet.direct(holder), BlockPos.ZERO, 300, false);
        if (at == null) { fail("no " + name + " village within reach of this seed"); return true; }
        found = at.getFirst();
        System.out.println("REAL_VILLAGE " + name + " found at " + found.toShortString());
        p.teleportTo(level, found.getX() + .5, 200, found.getZ() + .5, 0, 60);
        return true;
    }

    /** Waits for the village's ground to generate and its bell to be found. */
    private static boolean arrived(ServerPlayer p) {
        if (found == null) return true;
        ServerLevel level = p.serverLevel();
        if (!level.isPositionEntityTicking(found.atY(64))) return false;
        if (bell == null) {
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, found.getX(), found.getZ());
            p.teleportTo(level, found.getX() + .5, top + 25, found.getZ() + .5, 0, 60);
            if (!Villages.hasBell(level, found.atY(top))) return false;
            bell = Villages.bell(level, found.atY(top));
        }
        village = VillageMemory.of(level, bell);
        return true;
    }

    private static boolean board(Minecraft mc, ServerPlayer p) {
        if (village == null) return true;
        ServerLevel level = p.serverLevel();
        Projects.consider(level, village);
        if (village.board() == null) { fail("no board went up"); camera(mc, Vec3.atCenterOf(bell).add(8, 6, 8), Vec3.atCenterOf(bell)); return true; }
        if (!Projects.whole(level, village)) fail("the board isn't whole");
        notes.add("tier " + Projects.tier(level, village) + ", growth kind " + kindOf(level) + ", project " + (village.project() == null ? "none" : village.project().title()));
        var b = village.board();
        p.teleportTo(level, b.at(0, 0, 6).getX() + .5, b.origin().getY(), b.at(0, 0, 6).getZ() + .5, b.facing().getOpposite().toYRot(), 0);
        camera(mc, Vec3.atBottomCenterOf(b.at(0, 0, 4)).add(0, 1.62, 0), Vec3.atCenterOf(b.at(0, 1, 1)).add(0, 0.5, 0));
        return true;
    }

    private static String kindOf(ServerLevel level) {
        // Growth.kind is package-private; the board's wood says the same thing.
        var b = village.board();
        return b == null ? "?" : level.getBlockState(b.at(-2, 0, 0)).getBlock().getName().getString();
    }

    /** Reads the village's own homes and picks the one a village tier would rebuild. */
    private static boolean chooseHome(Minecraft mc, ServerPlayer p) {
        if (village == null) return true;
        ServerLevel level = p.serverLevel();
        List<Projects.Home> homes = Projects.homes(level, village);
        long small = homes.stream().filter(Projects.Home::small).count(), medium = homes.stream().filter(Projects.Home::medium).count();
        notes.add("homes read from its pieces: " + homes.size() + " (" + small + " small, " + medium + " medium)");
        if (homes.isEmpty()) { fail("no homes read from the village's structure"); return true; }
        for (Projects.Home h : homes)
            System.out.println("REAL_VILLAGE " + kind + " home " + h.template().getPath() + " at " + h.origin().toShortString() + " " + h.rotation()
                    + " standing " + String.format("%.2f", Projects.standing(level, village, h)));
        village.setBuiltForPreview(4);   // at least a village, whatever its beds
        if (!Projects.rebuild(level, village, Projects.Tier.VILLAGE)) { notes.add("no small home with room to rebuild bigger"); return true; }
        old = village.project().upgrade();
        notes.add("rebuilding " + old.template().getPath() + " as " + village.project().template().getPath());
        var t = level.getStructureManager().get(old.template()).orElseThrow();
        var box = t.getBoundingBox(new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings().setRotation(old.rotation()), old.origin());
        Direction front = old.rotation().rotate(Growth.front(t, old.template()));
        clearView(mc, level, Vec3.atCenterOf(box.getCenter()).add(0, 1, 0), front, 12, 4, 6);
        return true;
    }

    private static boolean rebuild(Minecraft mc, ServerPlayer p) {
        if (village == null || old == null) return true;
        ServerLevel level = p.serverLevel();
        Projects.fill(level, village);
        site = Rebuild.all(level).stream().filter(s -> s.growth() && s.bell().equals(bell)).findFirst().orElse(null);
        if (site == null || site.replaces() == null) { fail("the rebuild found no room when it was time to build (" + village.project().stage() + ")"); return true; }
        Rebuild.hurry(level, site, 100_000);
        check(level, site, "rebuilt home");
        if (Projects.standing(level, village, site.home()) < 0.9F) fail("the rebuilt home isn't all there: " + Projects.standing(level, village, site.home()));
        houseCamera(mc, level, site, 12, 4);
        return true;
    }

    private static boolean market(Minecraft mc, ServerPlayer p) {
        if (village == null) return true;
        ServerLevel level = p.serverLevel();
        // It may be this village's market day already: then it's the village's own market we look at.
        if (village.market() == null) Markets.open(level, village, Projects.Tier.TOWN);
        int stalls = village.market() == null ? 0 : village.market().stalls().size();
        notes.add("market stalls: " + stalls);
        if (stalls == 0) { fail("no room for a single stall"); return true; }
        for (BlockPos s : village.market().stalls())
            if (!level.getBlockState(s.below()).isFaceSturdy(level, s.below(), Direction.UP) && !level.getBlockState(s.below()).is(net.minecraft.world.level.block.Blocks.DIRT_PATH))
                fail("a stall not on the ground at " + s.toShortString());
        camera(mc, Vec3.atCenterOf(bell).add(14, 12, 14), Vec3.atCenterOf(bell));
        return true;
    }

    private static boolean townHouse(Minecraft mc, ServerPlayer p) {
        if (village == null) return true;
        ServerLevel level = p.serverLevel();
        Markets.close(level, village, false);
        var id = Projects.TOWN_HOUSES.get(kind.hashCode() % 2 == 0 ? 0 : 1);
        site = Growth.grow(level, village, id);
        if (site == null) { notes.add("no open ground for a town house"); return true; }
        Rebuild.hurry(level, site, 100_000);
        check(level, site, "town house");
        houseCamera(mc, level, site, 11, 3);
        return true;
    }

    /** Someone gathers a share for the board's project and carries it there. */
    private static boolean carry(Minecraft mc, ServerPlayer p) {
        if (village == null || village.board() == null) return true;
        ServerLevel level = p.serverLevel();
        if (village.project() == null || village.project().stage() != Projects.Stage.GATHERING) Projects.post(level, village);
        com.jayptucker.heroesevolved.village.Workers.gatherNowForPreview(level, village);
        notes.add("carrying a share: " + com.jayptucker.heroesevolved.village.Workers.carrying(village));
        var b = village.board();
        camera(mc, Vec3.atBottomCenterOf(b.at(0, 0, 9)).add(0, 4, 0), Vec3.atCenterOf(b.at(0, 1, 1)));
        return true;
    }

    private static Projects.Home target;

    /** An explosion in one of the village's houses. */
    private static boolean blowUp(Minecraft mc, ServerPlayer p) {
        if (village == null) return true;
        ServerLevel level = p.serverLevel();
        com.jayptucker.heroesevolved.village.Repairs.check(level, village);   // it knows how it was first
        target = Projects.homes(level, village).stream().filter(h -> h.small() || h.medium()).findFirst().orElse(null);
        if (target == null) { notes.add("no house to blow up"); return true; }
        var t = level.getStructureManager().get(target.template()).orElseThrow();
        var box = t.getBoundingBox(new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings().setRotation(target.rotation()), target.origin());
        Vec3 c = Vec3.atCenterOf(box.getCenter());
        level.explode(null, c.x, box.minY() + 3, c.z, 3.5F, net.minecraft.world.level.Level.ExplosionInteraction.BLOCK);
        Direction front = target.rotation().rotate(Growth.front(t, target.template()));
        clearView(mc, level, c, front, 13, 6, 7);
        return true;
    }

    /** The village notices and its people go to work on it. */
    private static boolean mend(Minecraft mc, ServerPlayer p) {
        if (village == null || target == null) return true;
        ServerLevel level = p.serverLevel();
        var missing = com.jayptucker.heroesevolved.village.Repairs.missing(level, village, target);
        site = com.jayptucker.heroesevolved.village.Repairs.check(level, village);
        // In a real world the village may have seen the blast and planned the mending itself already.
        if (site == null) site = Rebuild.all(level).stream().filter(s -> s.mending() && target.equals(s.home())).findFirst().orElse(null);
        notes.add("blown up: " + missing.size() + " blocks missing; mending " + (site == null ? "NOT PLANNED" : site.size() + " blocks"));
        if (site == null && missing.size() >= 3) fail("nothing planned to mend " + missing.size() + " missing blocks");
        return true;
    }

    private static boolean mended(Minecraft mc, ServerPlayer p) {
        if (village == null || target == null || site == null) return true;
        ServerLevel level = p.serverLevel();
        notes.add("crew at work: " + com.jayptucker.heroesevolved.village.Workers.crew(level, site).size() + ", " + site.done() + " of " + site.size() + " back by the photo");
        Rebuild.hurry(level, site, 100_000);
        var left = com.jayptucker.heroesevolved.village.Repairs.missing(level, village, target);
        notes.add("still missing after mending: " + left.size());
        if (left.size() > 2) fail("mending left " + left.size() + " blocks missing");
        return true;
    }

    private static boolean summary(ServerPlayer p) {
        System.out.println("REAL_VILLAGE " + kind + (bell == null ? "" : " bell " + bell.toShortString()) + ": " + String.join("; ", notes));
        return true;
    }

    // ------------------------------------------------------------------
    // Checks and cameras
    // ------------------------------------------------------------------

    /** Every pane joined to something, every stair, pane, fence and wall settled, every door whole, round {@code s}. */
    private static void check(ServerLevel level, Rebuild.Site s, String what) {
        int panes = 0, unsettled = 0, doors = 0;
        BlockPos c = s.center();
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-10, -1, -10), c.offset(10, 16, 10))) {
            if (com.jayptucker.heroesevolved.village.PlayerBuilt.is(level, p)) continue;
            BlockState st = level.getBlockState(p);
            if (st.getBlock() instanceof IronBarsBlock && !st.getValue(CrossCollisionBlock.NORTH) && !st.getValue(CrossCollisionBlock.SOUTH)
                    && !st.getValue(CrossCollisionBlock.EAST) && !st.getValue(CrossCollisionBlock.WEST)) panes++;
            if (Rebuild.settles(st) && Block.updateFromNeighbourShapes(st, level, p) != st) unsettled++;
            if (st.getBlock() instanceof DoorBlock) {
                BlockState other = level.getBlockState(st.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? p.above() : p.below());
                if (!(other.getBlock() instanceof DoorBlock)) doors++;
            }
        }
        notes.add(what + " checked: " + panes + " loose panes, " + unsettled + " unsettled, " + doors + " half doors");
        if (panes + unsettled + doors > 0) fail(what + " has " + panes + " loose panes, " + unsettled + " unsettled, " + doors + " half doors");
    }

    private static void houseCamera(Minecraft mc, ServerLevel level, Rebuild.Site s, double back, double up) {
        var t = level.getStructureManager().get(s.home().template()).orElse(null);
        Direction front = t == null ? Direction.NORTH : s.home().rotation().rotate(Growth.front(t, s.home().template()));
        clearView(mc, level, Vec3.atBottomCenterOf(s.center()).add(0, 2.5, 0), front, back, up, 6);
    }

    private static void clearView(Minecraft mc, ServerLevel level, Vec3 look, Direction front, double back, double up, double near) {
        double base = Math.atan2(front.getStepZ(), front.getStepX());
        for (double turn : new double[]{0.35, -0.35, 0.8, -0.8, 1.3, -1.3, 0, Math.PI}) {
            double a = base + turn;
            Vec3 at = look.add(Math.cos(a) * back, up, Math.sin(a) * back);
            var hit = level.clip(new ClipContext(at, look, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
            if (hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceTo(look) < near) { camera(mc, at, look); return; }
        }
        camera(mc, look.add(Math.cos(base) * back, up + 6, Math.sin(base) * back), look);
    }

    // ------------------------------------------------------------------
    // Running it
    // ------------------------------------------------------------------

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
            mc.createWorldOpenFlows().createFreshLevel("RealVillages-" + System.currentTimeMillis(),
                    new LevelSettings("Real villages preview", GameType.SURVIVAL, false, Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(SEED, true, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.NORMAL).createWorldDimensions(), mc.screen);
        }
        if (mc.player == null || mc.getSingleplayerServer() == null || mc.getOverlay() != null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
        if (age >= 100) mc.getToasts().clear();
        ++age;
        var server = mc.getSingleplayerServer();
        var id = mc.player.getUUID();
        if (age == 104) { server.execute(() -> PrologueService.skip(server.getPlayerList().getPlayer(id))); return; }
        if (age == 110) {
            server.execute(() -> {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                CryoChamberService.release(p);
                p.setGameMode(GameType.SPECTATOR);
                p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 20 * 3000, 0, false, false));
                server.overworld().setDayTime(6_000);
                server.overworld().setWeatherParameters(24000, 0, false, false);
            });
            mc.options.hideGui = true;
            return;
        }
        if (age < 160) return;
        if (!built) { build(); built = true; }
        if (age < waitUntil || pending != null) return;
        if (index >= SCRIPT.size()) {
            System.out.println("REAL_VILLAGES_RESULT " + (failures == 0 && photos >= expected ? "PASS" : "FAIL") + ": " + photos + " photos, " + failures + " failures");
            if (failures > 0 || photos < expected) PreviewQuiet.report("REAL_VILLAGES FAIL " + failures + " failures, " + photos + " photos");
            PreviewQuiet.quit();
            index = Integer.MAX_VALUE;
            return;
        }
        if (index == Integer.MAX_VALUE) return;
        Object item = SCRIPT.get(index);
        if (item instanceof Integer n) { waitUntil = age + n; index++; return; }
        if (item instanceof Shot s) { pending = "real-" + s.name() + ".png"; index++; return; }
        Step step = (Step) item;
        switch (STATE.get()) {
            case 0 -> {
                STATE.set(1);
                server.execute(() -> {
                    try { STATE.set(step.run(mc, server.getPlayerList().getPlayer(id)) ? 2 : 3); }
                    catch (RuntimeException error) { error.printStackTrace(); fail("step " + index + " threw " + error); STATE.set(2); }
                });
            }
            case 2 -> { STATE.set(0); retries = 0; index++; }
            case 3 -> {
                STATE.set(0);
                waitUntil = age + 10;
                if (++retries > RETRIES) { fail("timed out at step " + index); retries = 0; index++; }
            }
            default -> { }
        }
    }

    @SubscribeEvent public static void render(RenderFrameEvent.Post e) {
        if (!on() || pending == null) return;
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Path dir = mc.gameDirectory.toPath().resolve("screenshots");
            Files.createDirectories(dir);
            image.writeToFile(dir.resolve(pending));
            System.out.println("REAL_VILLAGE_CAPTURE " + pending);
            photos++;
        } catch (Exception error) {
            System.out.println("REAL_VILLAGE capture failed: " + error);
        }
        pending = null;
        waitUntil = age + 4;
    }
}
