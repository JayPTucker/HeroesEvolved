package com.jayptucker.heroesevolved.village;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.gifted.GiftedMobs;
import com.jayptucker.heroesevolved.radio.BandChatter;
import com.jayptucker.heroesevolved.radio.EmergencyBand;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Villages that grow and decline: each keeps a score from what it remembers and what it has now ({@link #score}). It grows
 * by the projects on its notice board ({@link Projects}): once players have brought what a building needs, it puts it up
 * over two days of daylight ({@link Rebuild}), and a newcomer arrives for each bed. Its mood decides how much it gathers
 * for itself; a thriving one with a guardian is a haven refugees make for. A struggling one wears (broken panes, cobwebs,
 * moss) and loses people. Only villages that are loaded and ticking change. What the player sees:
 * {@code docs/village-growth.md}.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class Growth {
    public enum Mood { THRIVING, STEADY, STRUGGLING }
    public static final int THRIVING = 8, RECENT_DAYS = 10, WEAR_EVERY = 2, LEAVE_EVERY = 3, FEWEST = 3;
    /** A house goes this far from the bell, on ground at most {@link #SLOPE} blocks out of level. */
    public static final int NEAREST = 20, FURTHEST = 48, SLOPE = 3;
    private static final String[] FAMILIES = {"Halloran", "Pike", "Ashby", "Corrigan", "Mott", "Fenwick", "Larue", "Dunmore", "Quill", "Brand"};
    private static boolean templatesBroken;

    private Growth() {}

    // ------------------------------------------------------------------
    // How a village is doing
    // ------------------------------------------------------------------

    /** Its score: saved, guarded, helped and peaceful count for it; hit and robbed by the Company, against it. */
    public static int score(ServerLevel level, VillageMemory.Village v) {
        long day = level.getDayTime() / Days.DAY + 1;
        int score = 0;
        long lastTrouble = v.firstDay < 0 ? day : v.firstDay;
        for (VillageMemory.Entry e : v.history) {
            lastTrouble = Math.max(lastTrouble, e.day());
            if (day - e.day() > RECENT_DAYS) continue;
            boolean company = e.what().equals("the Company");
            switch (e.outcome()) {
                case SAVED -> score += 3;
                case HIT -> score -= company ? 3 : 4;
                case FALLEN -> { }
            }
        }
        score += 2 * guardians(level, v.bell);
        int kind = 0;
        for (long d : v.kindness) if (day - d <= RECENT_DAYS) kind++;
        score += Math.min(5, kind);
        score += Math.min(6, 2 * v.sheltered);
        score += (int) Math.min(5, Math.max(0, (day - lastTrouble) / 2));
        return score;
    }

    public static Mood mood(ServerLevel level, VillageMemory.Village v) {
        int s = score(level, v);
        return s >= THRIVING ? Mood.THRIVING : s < 0 ? Mood.STRUGGLING : Mood.STEADY;
    }

    /** Guardians living there (loaded ones). */
    static int guardians(ServerLevel level, BlockPos bell) {
        return level.getEntitiesOfClass(Villager.class, new AABB(bell).inflate(Villages.RADIUS), GiftedMobs::isGood).size();
    }

    /** A thriving village with a guardian: a haven. */
    public static boolean haven(ServerLevel level, VillageMemory.Village v) {
        return !v.fallen && mood(level, v) == Mood.THRIVING && guardians(level, v.bell) > 0;
    }

    /** The nearest haven within {@code r} of {@code near}, or null. */
    public static BlockPos haven(ServerLevel level, BlockPos near, int r) {
        BlockPos best = null;
        for (VillageMemory.Village v : VillageMemory.all(level))
            if (v.bell.distSqr(near) <= (long) r * r && haven(level, v) && (best == null || v.bell.distSqr(near) < best.distSqr(near))) best = v.bell;
        return best;
    }

    /** It took in a family of refugees. */
    public static void sheltered(ServerLevel level, BlockPos bell) {
        VillageMemory.Village v = VillageMemory.of(level, Villages.bell(level, bell));
        v.sheltered++;
        VillageMemory.get(level).setDirty();
    }

    /** Someone did one of its people a kindness (a favour, a caravan delivered). */
    public static void kindness(ServerLevel level, BlockPos at) {
        if (!Villages.hasBell(level, at)) return;
        VillageMemory.Village v = VillageMemory.of(level, Villages.bell(level, at));
        v.kindness.add(level.getDayTime() / Days.DAY + 1);
        while (v.kindness.size() > 20) v.kindness.removeFirst();
        VillageMemory.get(level).setDirty();
    }

    // ------------------------------------------------------------------
    // What that does to it
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.overworld().getGameTime() % 200 != 0) return;
        ServerLevel level = server.overworld();
        // Boards go up by themselves only in real worlds: on the game-test server every test's bell would get one on its
        // floor. Tests call Projects.consider themselves.
        boolean projects = !(server instanceof net.minecraft.gametest.framework.GameTestServer);
        if (projects) discover(level);
        for (VillageMemory.Village v : VillageMemory.all(level)) {
            if (v.fallen || !level.isPositionEntityTicking(v.bell)) continue;
            if (EmergencyBand.troubled(level, v.bell)) {
                if (v.market != null) Markets.close(level, v, true);   // the traders don't stay for trouble
                continue;
            }
            consider(level, v, projects);
        }
    }

    /** A village players come near is remembered (named), so it has a board and a mood even if nothing has happened there. */
    static void discover(ServerLevel level) {
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || !Villages.hasBell(level, p.blockPosition())) continue;
            BlockPos bell = Villages.bell(level, p.blockPosition());
            if (VillageMemory.remembers(level, bell) || !level.isPositionEntityTicking(bell) || Villages.members(level, bell).isEmpty()) continue;
            VillageMemory.of(level, bell);
        }
    }

    /** What happens to {@code v} today, if anything (once a tick-pass; tests call it directly). */
    public static void consider(ServerLevel level, VillageMemory.Village v) { consider(level, v, true); }

    static void consider(ServerLevel level, VillageMemory.Village v, boolean projects) {
        long day = level.getDayTime() / Days.DAY + 1;
        if (projects) {
            Projects.consider(level, v);
            Markets.consider(level, v);
            Repairs.consider(level, v);
        }
        switch (mood(level, v)) {
            case STRUGGLING -> {
                if (day - v.lastWear >= WEAR_EVERY) {
                    v.lastWear = day;
                    wear(level, v.bell);
                    BandChatter.news(level, v.bell, "Times are hard in " + v.name + ".", (int) Days.DAY);
                }
                if (day - v.lastLeave >= LEAVE_EVERY && leave(level, v.bell)) v.lastLeave = day;
                VillageMemory.get(level).setDirty();
            }
            case THRIVING, STEADY -> { }
        }
    }

    static boolean building(ServerLevel level, VillageMemory.Village v) {
        for (Rebuild.Site s : Rebuild.all(level)) if (s.growth() && s.bell().equals(v.bell)) return true;
        return false;
    }

    // ------------------------------------------------------------------
    // A new house
    // ------------------------------------------------------------------

    /** The village's kind, for its houses: plains, desert, savanna, snowy or taiga. */
    static String kind(ServerLevel level, BlockPos bell) {
        var biome = level.getBiome(bell);
        if (biome.is(BiomeTags.HAS_VILLAGE_DESERT)) return "desert";
        if (biome.is(BiomeTags.HAS_VILLAGE_SAVANNA)) return "savanna";
        if (biome.is(BiomeTags.HAS_VILLAGE_SNOWY)) return "snowy";
        if (biome.is(BiomeTags.HAS_VILLAGE_TAIGA)) return "taiga";
        return "plains";
    }

    /** Plans one of {@code v}'s kind's small houses now (tests, previews; villages build what their board asks for). */
    public static Rebuild.Site grow(ServerLevel level, VillageMemory.Village v) {
        String kind = kind(level, v.bell);
        int count = kind.equals("taiga") ? 5 : 8;
        int first = level.random.nextInt(count);
        for (int k = 0; k < count; k++) {
            Rebuild.Site site = grow(level, v, ResourceLocation.withDefaultNamespace("village/" + kind + "/houses/" + kind + "_small_house_" + ((first + k) % count + 1)));
            if (site != null) return site;
        }
        return null;
    }

    /**
     * Plans the building in template {@code id} for {@code v}, on open, level, natural ground {@link #NEAREST} to
     * {@link #FURTHEST} blocks from its bell, turned whichever way fits, to go up from the next morning. Null if there is
     * nowhere to put it (or {@code mobGriefing} is off).
     */
    public static Rebuild.Site grow(ServerLevel level, VillageMemory.Village v, ResourceLocation id) {
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING) || templatesBroken) return null;
        StructureTemplate template = level.getStructureManager().get(id).orElse(null);
        if (template == null) return null;
        int turn = level.random.nextInt(4);
        for (int k = 0; k < 4; k++) {
            Rotation rotation = Rotation.values()[(turn + k) % 4];
            BlockPos corner = spot(level, v.bell, template, rotation);
            if (corner == null) continue;
            // spot() checked the ground from this corner; a turned template is built out from its origin the other way.
            BlockPos origin = originFor(template, rotation, corner);
            List<StructureTemplate.StructureBlockInfo> blocks = blocks(level, template, origin, rotation);
            if (blocks == null) return null;
            restyle(level, v, id, blocks);
            List<BlockPos> positions = new ArrayList<>();
            List<BlockState> states = new ArrayList<>();
            blocks.sort(Comparator.<StructureTemplate.StructureBlockInfo>comparingInt(b -> b.pos().getY())
                    .thenComparingDouble(b -> b.pos().distSqr(origin)));
            for (StructureTemplate.StructureBlockInfo b : blocks) {
                if (b.state().is(Blocks.STRUCTURE_VOID) || b.state().is(Blocks.JIGSAW)) continue;
                // The house's empty rooms are work only where they clear grass or flowers; over air there's nothing to do,
                // and counting it would finish the house well inside its two days.
                if (b.state().isAir() && level.getBlockState(b.pos()).isAir()) continue;
                positions.add(b.pos().immutable());
                states.add(b.state());
            }
            BlockPos center = corner.offset(rotated(template, rotation).getX() / 2, 0, rotated(template, rotation).getZ() / 2);
            // On a slope: dig first, then the foundation, then the house.
            List<BlockPos> dig = new ArrayList<>(), fill = new ArrayList<>();
            List<BlockState> fillStates = new ArrayList<>();
            grade(level, v, corner, rotated(template, rotation), dig, fill, fillStates);
            Rebuild.Site site;
            if (dig.isEmpty() && fill.isEmpty()) site = Rebuild.plan(level, v.bell, center, origin.getY(), positions, states);
            else {
                fill.addAll(positions);
                fillStates.addAll(states);
                List<BlockState> air = new ArrayList<>(java.util.Collections.nCopies(dig.size(), Blocks.AIR.defaultBlockState()));
                site = Rebuild.replan(level, v.bell, center, origin.getY(), dig, air, fill, fillStates);
            }
            site.home = new Projects.Home(id, origin, rotation);
            Rebuild.begin(level, site);
            HeroesEvolved.LOGGER.info("Village {} will build {} at {}", v.name, id, origin);
            return site;
        }
        return null;
    }

    private static net.minecraft.core.Vec3i rotated(StructureTemplate t, Rotation r) { return t.getSize(r); }

    /** The origin to place {@code template} turned by {@code rotation} from, so its footprint starts at {@code corner}. */
    static BlockPos originFor(StructureTemplate template, Rotation rotation, BlockPos corner) {
        var box = template.getBoundingBox(new StructurePlaceSettings().setRotation(rotation), BlockPos.ZERO);
        return corner.offset(-box.minX(), 0, -box.minZ());
    }

    /** Our own buildings in the colours of {@code v}'s kind ({@link Styles}); vanilla's are left as they are. */
    static void restyle(ServerLevel level, VillageMemory.Village v, ResourceLocation id, List<StructureTemplate.StructureBlockInfo> blocks) {
        if (!Styles.ours(id)) return;
        String kind = kind(level, v.bell);
        blocks.replaceAll(b -> new StructureTemplate.StructureBlockInfo(b.pos(), Styles.apply(b.state(), kind), b.nbt()));
    }

    /** Which way a building's front (the side its door and street are on) faces, before it is turned. */
    @SuppressWarnings("unchecked")
    public static net.minecraft.core.Direction front(StructureTemplate template, ResourceLocation id) {
        if (Styles.ours(id)) return net.minecraft.core.Direction.NORTH;
        try {
            var field = StructureTemplate.class.getDeclaredField("palettes");
            field.setAccessible(true);
            var palettes = (List<StructureTemplate.Palette>) field.get(template);
            // A village house has one jigsaw, where it joins the street.
            for (var b : palettes.getFirst().blocks())
                if (b.state().is(Blocks.JIGSAW)) {
                    var facing = b.state().getValue(net.minecraft.world.level.block.JigsawBlock.ORIENTATION).front();
                    if (facing.getAxis().isHorizontal()) return facing;
                }
        } catch (ReflectiveOperationException | RuntimeException e) {
            HeroesEvolved.LOGGER.debug("No front found for {}", id, e);
        }
        return net.minecraft.core.Direction.NORTH;
    }

    /**
     * Plans rebuilding {@code old} as {@code target}: the old house comes down (top first; its floor goes back to ground),
     * and the new one goes up facing the street the same way, its front on the old front line, centred on the old house
     * (or shifted a block or two either way if that's where it fits). Outside the old footprint the new one needs open,
     * level, natural ground, or, where only its upper floor or roof reaches, clear air. Null if it doesn't fit (or
     * {@code mobGriefing} is off).
     */
    public static Rebuild.Site upgrade(ServerLevel level, VillageMemory.Village v, Projects.Home old, ResourceLocation target) {
        return upgrade(level, v, old, target, true);
    }

    /** Whether {@code old} could be rebuilt as {@code target} where it stands, now (nothing is planned). */
    public static boolean upgradeFits(ServerLevel level, VillageMemory.Village v, Projects.Home old, ResourceLocation target) {
        return upgrade(level, v, old, target, false) != null;
    }

    /** The answer to a dry run that found room. */
    private static final Rebuild.Site DRY_RUN = new Rebuild.Site(BlockPos.ZERO, BlockPos.ZERO);

    /** {@link #upgrade}, or only the search for room when {@code plan} is false (a non-null, unplanned answer). */
    private static Rebuild.Site upgrade(ServerLevel level, VillageMemory.Village v, Projects.Home old, ResourceLocation target, boolean plan) {
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING) || templatesBroken) return null;
        StructureTemplate was = level.getStructureManager().get(old.template()).orElse(null);
        StructureTemplate next = level.getStructureManager().get(target).orElse(null);
        if (was == null || next == null) return null;
        List<StructureTemplate.StructureBlockInfo> oldBlocks = blocks(level, was, old.origin(), old.rotation());
        if (oldBlocks == null) return null;
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        java.util.Set<BlockPos> oldCells = new java.util.HashSet<>();
        for (var b : oldBlocks) {
            if (b.state().isAir() || b.state().is(Blocks.STRUCTURE_VOID) || b.state().is(Blocks.JIGSAW)) continue;
            if (!level.hasChunkAt(b.pos())) return null;
            oldCells.add(b.pos());
            minX = Math.min(minX, b.pos().getX()); maxX = Math.max(maxX, b.pos().getX());
            minZ = Math.min(minZ, b.pos().getZ()); maxZ = Math.max(maxZ, b.pos().getZ());
        }
        if (oldCells.isEmpty()) return null;
        net.minecraft.core.Direction facing = old.rotation().rotate(front(was, old.template()));
        Rotation turn = Rotation.NONE;
        for (Rotation r : Rotation.values()) if (r.rotate(front(next, target)) == facing) turn = r;
        net.minecraft.core.Vec3i size = next.getSize(turn);
        int base = old.origin().getY(), sx = size.getX(), sz = size.getZ();
        int midX = (minX + maxX) / 2, midZ = (minZ + maxZ) / 2;
        for (int back : new int[]{0, 1, 2})
            for (int side : new int[]{0, 1, -1, 2, -2, 3, -3}) {
                int x0, z0;
                switch (facing) {
                    case NORTH -> { z0 = minZ + back; x0 = midX - (sx - 1) / 2 + side; }
                    case SOUTH -> { z0 = maxZ - (sz - 1) - back; x0 = midX - (sx - 1) / 2 + side; }
                    case WEST -> { x0 = minX + back; z0 = midZ - (sz - 1) / 2 + side; }
                    default -> { x0 = maxX - (sx - 1) - back; z0 = midZ - (sz - 1) / 2 + side; }
                }
                BlockPos corner = new BlockPos(x0, base, z0);
                BlockPos origin = originFor(next, turn, corner);
                List<StructureTemplate.StructureBlockInfo> newBlocks = blocks(level, next, origin, turn);
                if (newBlocks == null) return null;
                restyle(level, v, target, newBlocks);
                if (!room(level, newBlocks, minX, maxX, minZ, maxZ, base)) continue;
                if (!plan) return DRY_RUN;
                // Down first, top first; the old floor goes back to ground for the new one to stand on.
                BlockState ground = kind(level, v.bell).equals("desert") ? Blocks.SAND.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState();
                List<BlockPos> clear = new ArrayList<>(oldCells);
                clear.removeIf(p -> level.getBlockState(p).isAir() || p.getY() < base);
                clear.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY).reversed());
                List<BlockState> cleared = new ArrayList<>();
                for (BlockPos p : clear) cleared.add(p.getY() == base ? ground : Blocks.AIR.defaultBlockState());
                List<BlockPos> positions = new ArrayList<>();
                List<BlockState> states = new ArrayList<>();
                newBlocks.sort(Comparator.<StructureTemplate.StructureBlockInfo>comparingInt(b -> b.pos().getY())
                        .thenComparingDouble(b -> b.pos().distSqr(corner)));
                for (var b : newBlocks) {
                    if (b.state().is(Blocks.STRUCTURE_VOID) || b.state().is(Blocks.JIGSAW)) continue;
                    if (b.state().isAir() && level.getBlockState(b.pos()).isAir() && !oldCells.contains(b.pos())) continue;
                    if (b.state().isAir() && oldCells.contains(b.pos())) continue;   // cleared already
                    positions.add(b.pos().immutable());
                    states.add(b.state());
                }
                BlockPos center = corner.offset(sx / 2, 0, sz / 2);
                Rebuild.Site site = Rebuild.replan(level, v.bell, center, base, clear, cleared, positions, states);
                site.home = new Projects.Home(target, origin, turn);
                site.replaces = old;
                Rebuild.begin(level, site);
                HeroesEvolved.LOGGER.info("Village {} will rebuild {} at {} as {}", v.name, old.template(), old.origin(), target);
                return site;
            }
        return null;
    }

    /** Whether a building's {@code blocks} have room where the old house (its footprint given) doesn't already stand. */
    static boolean room(ServerLevel level, List<StructureTemplate.StructureBlockInfo> blocks, int minX, int maxX, int minZ, int maxZ, int base) {
        java.util.Map<Long, int[]> columns = new java.util.HashMap<>();   // lowest and highest block in each column
        for (var b : blocks) {
            if (b.state().isAir() || b.state().is(Blocks.STRUCTURE_VOID) || b.state().is(Blocks.JIGSAW)) continue;
            long key = BlockPos.asLong(b.pos().getX(), 0, b.pos().getZ());
            int[] c = columns.computeIfAbsent(key, k -> new int[]{Integer.MAX_VALUE, Integer.MIN_VALUE});
            c[0] = Math.min(c[0], b.pos().getY());
            c[1] = Math.max(c[1], b.pos().getY());
        }
        for (var e : columns.entrySet()) {
            BlockPos column = BlockPos.of(e.getKey());
            int x = column.getX(), z = column.getZ(), low = e.getValue()[0], high = e.getValue()[1];
            if (x >= minX && x <= maxX && z >= minZ && z <= maxZ) continue;
            if (!level.hasChunkAt(column)) return false;
            int from = low;
            if (low - base < 2) {
                // It stands here: natural ground at its floor (or a block below), and nothing in the way.
                BlockPos g = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column).below();
                if (g.getY() < base - 1 || g.getY() > base || !natural(level.getBlockState(g)) || !level.getFluidState(g.above()).isEmpty()
                        || PlayerBuilt.is(level, g)) return false;
                from = base + 1;
            }
            for (int y = from; y <= high; y++) {
                BlockPos at = new BlockPos(x, y, z);
                BlockState s = level.getBlockState(at);
                if (!s.isAir() && !(s.canBeReplaced() && s.getFluidState().isEmpty()) || PlayerBuilt.is(level, at)) return false;
            }
        }
        return true;
    }

    /** Where a house of {@code template}'s footprint fits: open, level ground, a block clear all round. Its corner, or null. */
    static BlockPos spot(ServerLevel level, BlockPos bell, StructureTemplate template, Rotation rotation) {
        net.minecraft.core.Vec3i size = template.getSize(rotation);
        // Level ground first; then gentle slopes, which the builders dig into and lay a foundation on.
        for (int slope : new int[]{1, SLOPE})
            for (int ring = NEAREST; ring <= FURTHEST; ring += 3)
                for (int i = 0; i < 20; i++) {
                    double a = (i + level.random.nextDouble() * 0.5) * Math.PI * 2 / 20;
                    int x0 = bell.getX() + (int) Math.round(Math.cos(a) * ring), z0 = bell.getZ() + (int) Math.round(Math.sin(a) * ring);
                    BlockPos corner = fits(level, x0, z0, size, slope);
                    if (corner != null) return corner;
                }
        return null;
    }

    /** The corner to build a footprint of {@code size} from at {@code x0, z0}, if the ground there is level enough. */
    static BlockPos fits(ServerLevel level, int x0, int z0, net.minecraft.core.Vec3i size) { return fits(level, x0, z0, size, 1); }

    /**
     * The corner (at the floor's height) to build a footprint of {@code size} from at {@code x0, z0}, if the ground there is
     * natural and at most {@code slope} out of level: the floor goes at its middle height, the ground above it is dug away
     * and the ground below gets a foundation ({@link #grade}). Nothing but air or plants may stand above the ground.
     */
    static BlockPos fits(ServerLevel level, int x0, int z0, net.minecraft.core.Vec3i size, int slope) {
        int low = Integer.MAX_VALUE, high = Integer.MIN_VALUE;
        List<Integer> heights = new ArrayList<>();
        for (int dx = -1; dx <= size.getX(); dx++)
            for (int dz = -1; dz <= size.getZ(); dz++) {
                BlockPos column = new BlockPos(x0 + dx, 0, z0 + dz);
                if (!level.hasChunkAt(column)) return null;
                BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
                BlockPos ground = top.below();
                BlockState g = level.getBlockState(ground);
                if (!natural(g) || !level.getFluidState(ground).isEmpty() || !level.getFluidState(top).isEmpty() || PlayerBuilt.is(level, ground)) return null;
                low = Math.min(low, ground.getY());
                high = Math.max(high, ground.getY());
                heights.add(ground.getY());
                if (high - low > slope) return null;
            }
        java.util.Collections.sort(heights);
        int base = slope <= 1 ? low : heights.get(heights.size() / 2);
        // Clear all the way up: nothing but air, grass, flowers or snow above the ground, and only natural ground (never
        // anything a player put down) between it and the floor where the builders dig.
        for (int dx = -1; dx <= size.getX(); dx++)
            for (int dz = -1; dz <= size.getZ(); dz++) {
                int ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x0 + dx, 0, z0 + dz)).getY() - 1;
                for (int y = base + 1; y <= base + size.getY(); y++) {
                    BlockPos p = new BlockPos(x0 + dx, y, z0 + dz);
                    BlockState s = level.getBlockState(p);
                    if (PlayerBuilt.is(level, p)) return null;
                    if (y <= ground ? !natural(s) : !s.isAir() && !(s.canBeReplaced() && s.getFluidState().isEmpty())) return null;
                }
            }
        return new BlockPos(x0, base, z0);
    }

    /**
     * Readies sloping ground for a building of {@code size} at {@code corner} (its floor's height): the ground standing above
     * the floor is dug away (into {@code dig}), and under the footprint the ground below it is built up with a foundation
     * of the village's stone (into {@code fill}, lowest first). Nothing to do on level ground.
     */
    static void grade(ServerLevel level, VillageMemory.Village v, BlockPos corner, net.minecraft.core.Vec3i size,
                      List<BlockPos> dig, List<BlockPos> fill, List<BlockState> fillStates) {
        int base = corner.getY();
        BlockState foundation = (kind(level, v.bell).equals("desert") ? Blocks.SANDSTONE : Blocks.COBBLESTONE).defaultBlockState();
        for (int dx = -1; dx <= size.getX(); dx++)
            for (int dz = -1; dz <= size.getZ(); dz++) {
                int x = corner.getX() + dx, z = corner.getZ() + dz;
                int ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z)).getY() - 1;
                if (ground > base && !level.getBlockState(new BlockPos(x, ground + 1, z)).isAir()) dig.add(new BlockPos(x, ground + 1, z));
                for (int y = ground; y > base; y--) dig.add(new BlockPos(x, y, z));
                // Dug down: the ground's own top (grass, snow, sand) goes back on, not bare earth.
                BlockState top = level.getBlockState(new BlockPos(x, ground, z));
                if (ground > base && natural(top)) { fill.add(new BlockPos(x, base, z)); fillStates.add(top); }
                boolean inside = dx >= 0 && dz >= 0 && dx < size.getX() && dz < size.getZ();
                if (inside) for (int y = ground + 1; y < base; y++) { fill.add(new BlockPos(x, y, z)); fillStates.add(foundation); }
            }
        dig.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY).reversed());
    }

    /** Ground a house may stand on (and replace its top with): soil, sand, snow or stone, not a path or anything built. */
    public static boolean natural(BlockState s) {
        return (s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.GRAVEL)
                || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(Blocks.SNOW) || s.is(Blocks.POWDER_SNOW))
                && !s.is(Blocks.DIRT_PATH) && !s.is(Blocks.FARMLAND);
    }

    /** {@code template}'s blocks as they would be placed at {@code origin}, turned by {@code rotation} (jigsaws replaced). */
    @SuppressWarnings("unchecked")
    static List<StructureTemplate.StructureBlockInfo> blocks(ServerLevel level, StructureTemplate template, BlockPos origin, Rotation rotation) {
        try {
            var field = StructureTemplate.class.getDeclaredField("palettes");
            field.setAccessible(true);
            var palettes = (List<StructureTemplate.Palette>) field.get(template);
            if (palettes.isEmpty()) return null;
            StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation).setIgnoreEntities(true)
                    .addProcessor(JigsawReplacementProcessor.INSTANCE);
            List<StructureTemplate.StructureBlockInfo> raw = palettes.get(level.random.nextInt(palettes.size())).blocks();
            // processBlockInfos only moves the positions; the blocks themselves must be turned too, as vanilla's own
            // placeInWorld does, or a turned house gets stairs facing the wrong way and panes joined to nothing.
            List<StructureTemplate.StructureBlockInfo> out = new ArrayList<>();
            for (StructureTemplate.StructureBlockInfo b : StructureTemplate.processBlockInfos(level, origin, origin, settings, raw, template))
                out.add(new StructureTemplate.StructureBlockInfo(b.pos(), b.state().mirror(settings.getMirror()).rotate(rotation), b.nbt()));
            return out;
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (!templatesBroken) HeroesEvolved.LOGGER.warn("Village growth can't read house templates; villages won't build", e);
            templatesBroken = true;
            return null;
        }
    }

    /** The building is up ({@link Rebuild}): its project is done, a newcomer walks in for each bed, and word gets round. */
    static void built(ServerLevel level, Rebuild.Site s) {
        VillageMemory.Village v = VillageMemory.of(level, s.bell());
        v.houses++;
        if (s.replaces() != null) {
            v.replaced.add(s.replaces().origin().asLong());
            v.homes.removeIf(h -> h.origin().equals(s.replaces().origin()));
        }
        if (s.home() != null) v.homes.add(s.home());
        VillageMemory.get(level).setDirty();
        Projects.Project project = v.project() != null && v.project().stage() == Projects.Stage.BUILDING ? v.project() : null;
        boolean rebuilt = s.replaces() != null;
        String what = project == null ? "a new house" : Projects.lower(project.title());
        int beds = project == null ? 1 : project.owner() != null ? 0 : Math.min(2, project.beds());
        boolean home = project != null && project.owner() != null;
        Projects.built(level, v, s);
        if (home) return;   // Homes.built has told the owner and the radio
        for (int i = 0; i < beds; i++) {
            Villager newcomer = EntityType.VILLAGER.create(level);
            if (newcomer == null) break;
            BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, s.center().offset(i * 2, 0, -4));
            newcomer.moveTo(at.getX() + .5, at.getY(), at.getZ() + .5, 0, 0);
            level.addFreshEntity(newcomer);
        }
        String family = FAMILIES[level.random.nextInt(FAMILIES.length)];
        String where = compass(s.bell(), s.center());
        BandChatter.news(level, s.bell(), (rebuilt ? v.name + " has rebuilt a home by the " + where + " road, bigger than it was."
                : v.name + "'s got " + what + " up by the " + where + " road.") + (beds > 0 ? " Welcome the " + family + "s." : ""), (int) Days.DAY);
        String done = rebuilt ? v.name + " has rebuilt one of its homes: " + what + " now" : v.name + " has built " + what;
        for (ServerPlayer p : level.players())
            if (p.blockPosition().distSqr(s.center()) <= 96 * 96)
                p.sendSystemMessage(Component.literal(done + (beds > 0 ? ", and the " + family + "s have moved in." : "."))
                        .withStyle(ChatFormatting.GREEN));
    }

    static String compass(BlockPos from, BlockPos to) {
        double angle = Math.toDegrees(Math.atan2(to.getX() - from.getX(), -(to.getZ() - from.getZ())));
        String[] points = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};
        return points[(int) Math.floorMod(Math.round(angle / 45), 8)];
    }

    // ------------------------------------------------------------------
    // Struggling
    // ------------------------------------------------------------------

    /** The village looks more worn: a few panes gone, cobwebs in corners, moss on stone. How many blocks changed. */
    public static int wear(ServerLevel level, BlockPos bell) {
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) return 0;
        int panes = 0, webs = 0, moss = 0;
        var r = level.random;
        for (int i = 0; i < 2000 && (panes < 3 || webs < 3 || moss < 6); i++) {
            BlockPos p = bell.offset(r.nextInt(65) - 32, r.nextInt(13) - 4, r.nextInt(65) - 32);
            if (!level.hasChunkAt(p) || PlayerBuilt.is(level, p) || PlayerBuilt.is(level, p.above())) continue;
            BlockState s = level.getBlockState(p);
            if (panes < 3 && s.is(Blocks.GLASS_PANE)) { level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL); panes++; }
            else if (moss < 6 && s.is(Blocks.COBBLESTONE)) { level.setBlock(p, Blocks.MOSSY_COBBLESTONE.defaultBlockState(), Block.UPDATE_ALL); moss++; }
            else if (moss < 6 && s.is(Blocks.STONE_BRICKS)) { level.setBlock(p, Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), Block.UPDATE_ALL); moss++; }
            else if (webs < 3 && s.isAir() && level.getBlockState(p.above()).isSolid() && solidSides(level, p) >= 2) {
                level.setBlock(p, Blocks.COBWEB.defaultBlockState(), Block.UPDATE_ALL);
                webs++;
            }
        }
        return panes + webs + moss;
    }

    private static int solidSides(ServerLevel level, BlockPos p) {
        int n = 0;
        for (var d : net.minecraft.core.Direction.Plane.HORIZONTAL) if (level.getBlockState(p.relative(d)).isSolid()) n++;
        return n;
    }

    /** Someone leaves (out of sight, never someone with a gift, never below {@link #FEWEST}). True if someone did. */
    public static boolean leave(ServerLevel level, BlockPos bell) {
        List<Villager> people = Villages.members(level, bell);
        if (people.size() <= FEWEST) return false;
        for (Villager v : people) {
            if (v.isBaby() || GiftedMobs.isGifted(v) || com.jayptucker.heroesevolved.mimicry.GiftedVillagers.gift(v) != null
                    || v.getTags().contains(com.jayptucker.heroesevolved.company.CompanyOps.PRISONER_TAG)) continue;
            boolean seen = false;
            for (ServerPlayer p : level.players()) if (p.distanceToSqr(v) <= 32 * 32) seen = true;
            if (seen) continue;
            v.discard();
            return true;
        }
        return false;
    }
}
