package com.jayptucker.heroesevolved.village;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.company.CompanyOps;
import com.jayptucker.heroesevolved.gifted.GiftedMobs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Villagers seen at work. They gather for their village's project themselves, a share each day, and one of them carries
 * it to the notice board's chest ({@link #gather}); and every building site has a crew of up to {@link #CREW} of the
 * village's people who walk to the block going down next, hold it, and set it with a puff of dust ({@link #direct},
 * {@link #placed}). A site with nobody left in its village to build it waits. What the player sees:
 * {@code docs/village-growth.md}.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class Workers {
    /** Builders on one site. */
    public static final int CREW = 3;
    /** How much of what a project still needs a village gathers in a day, by how it is doing, with five or more people. */
    static final float THRIVING_RATE = 0.2F, STEADY_RATE = 0.12F, STRUGGLING_RATE = 0.05F;
    /** A carrier gives up walking after this long and the share counts anyway (ticks). */
    static final int CARRY = 600;
    private static final String[] SHOUTS = {"Hand me those planks!", "Mind your head!", "Steady... steady.", "Nearly there!",
            "Who moved my hammer?", "That's level. Near enough.", "Another one up!", "More nails!"};

    private Workers() {}

    // ------------------------------------------------------------------
    // Gathering
    // ------------------------------------------------------------------

    /** A share on its way to the board: who carries it, what it is, since when. */
    record Carry(UUID villager, BlockPos bell, EnumMap<Projects.Material, Float> share, long since) {}

    private static final Map<Long, Carry> CARRYING = new HashMap<>();

    /** How much of what a project still needs {@code v} gathers itself in a day. */
    public static float rate(ServerLevel level, VillageMemory.Village v, int people) {
        float base = switch (Growth.mood(level, v)) {
            case THRIVING -> THRIVING_RATE;
            case STEADY -> STEADY_RATE;
            case STRUGGLING -> STRUGGLING_RATE;
        };
        return base * Math.min(1F, people / 5F);
    }

    /**
     * Once a calendar day, in daylight: {@code v} gathers its share of what its project still needs (a day's worth for each
     * day missed while nobody was about, up to three), and one of its people carries it to the board.
     */
    public static void gather(ServerLevel level, VillageMemory.Village v) {
        Projects.Project p = v.project;
        if (p == null || p.stage != Projects.Stage.GATHERING || p.owner != null) return;
        long day = Days.today(level), time = level.getDayTime() % 24_000;
        if (p.gathered < 0) { p.gathered = day; VillageMemory.get(level).setDirty(); return; }
        if (day <= p.gathered || time < 2_000 || time > 10_000 || CARRYING.containsKey(v.bell.asLong())) return;
        List<Villager> people = carriers(level, v);
        if (people.isEmpty()) return;
        float rate = rate(level, v, people.size()) * Math.min(3, day - p.gathered);
        EnumMap<Projects.Material, Float> share = new EnumMap<>(Projects.Material.class);
        for (var e : p.need.entrySet()) {
            float left = p.left(e.getKey());
            if (left > 1e-3F) share.put(e.getKey(), Math.min(left, Math.max(1, e.getValue() * rate)));
        }
        p.gathered = day;
        VillageMemory.get(level).setDirty();
        if (share.isEmpty()) return;
        Villager carrier = people.get(level.random.nextInt(people.size()));
        if (v.board == null) { give(level, v, share); return; }
        Projects.Material most = share.keySet().stream().max(java.util.Comparator.comparing(share::get)).orElseThrow();
        carrier.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(item(most)));
        carrier.setDropChance(EquipmentSlot.MAINHAND, 0);
        walk(carrier, v.board.chest().relative(v.board.facing()), 0.6F);
        CARRYING.put(v.bell.asLong(), new Carry(carrier.getUUID(), v.bell, share, level.getGameTime()));
    }

    /** Villagers of {@code v} free to fetch and carry: grown, awake, not trading, not a guardian or a traveller. */
    static List<Villager> carriers(ServerLevel level, VillageMemory.Village v) {
        List<Villager> out = new ArrayList<>();
        for (Villager x : Villages.members(level, v.bell))
            if (!x.isBaby() && !x.isSleeping() && !x.isTrading() && !GiftedMobs.isGifted(x)
                    && !x.getTags().contains(com.jayptucker.heroesevolved.travel.Travellers.TAG)
                    && !x.getTags().contains(CompanyOps.PRISONER_TAG)) out.add(x);
        return out;
    }

    /** What a villager carries for a material. */
    static Item item(Projects.Material m) {
        return switch (m) {
            case PLANKS -> Items.OAK_LOG;
            case STONE -> Items.COBBLESTONE;
            case SANDSTONE -> Items.SANDSTONE;
            case TERRACOTTA -> Items.TERRACOTTA;
            case GLASS -> Items.GLASS_PANE;
            case WOOL -> Items.WHITE_WOOL;
            case BEDS -> Items.WHITE_BED;
            case LIGHT -> Items.TORCH;
            case IRON -> Items.IRON_INGOT;
            case HAY -> Items.HAY_BLOCK;
        };
    }

    /** The share goes in: the counts rise, the signs say so, and if that was all it needed, the building starts. */
    static void give(ServerLevel level, VillageMemory.Village v, EnumMap<Projects.Material, Float> share) {
        Projects.Project p = v.project;
        if (p == null || p.stage != Projects.Stage.GATHERING) return;
        share.forEach((m, n) -> p.have.merge(m, Math.min(n, p.left(m)), Float::sum));
        VillageMemory.get(level).setDirty();
        if (v.board != null) {
            BlockPos at = v.board.chest();
            level.playSound(null, at, SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.8F, 1.1F);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, at.getX() + .5, at.getY() + 1.1, at.getZ() + .5, 6, .4, .3, .4, 0);
        }
        if (p.complete()) Projects.ready(level, v);
        Projects.signs(level, v);
    }

    // ------------------------------------------------------------------
    // Building
    // ------------------------------------------------------------------

    /** A site's crew, picked again now and then. */
    private static final class Crew {
        final List<UUID> builders = new ArrayList<>();
        long picked = -1000, shouted = -1000;
    }

    private static final Map<Rebuild.Site, Crew> CREWS = new WeakHashMap<>();

    /**
     * Each working pass on a site: its crew walks to the block going down next, looks at it and holds it. False if the
     * village has nobody left to build (the site waits).
     */
    static boolean direct(ServerLevel level, Rebuild.Site s) {
        Crew crew = CREWS.computeIfAbsent(s, k -> new Crew());
        long now = level.getGameTime();
        if (now - crew.picked >= 100 || crew.builders.isEmpty()) {
            crew.picked = now;
            release(level, crew);
            List<Villager> people = new ArrayList<>(Villages.members(level, s.bell));
            people.removeIf(x -> x.isBaby() || x.isSleeping() || x.isTrading() || x.getTags().contains(CompanyOps.PRISONER_TAG));
            people.sort(java.util.Comparator.comparingDouble(x -> x.distanceToSqr(s.center.getX(), s.center.getY(), s.center.getZ())));
            for (int i = 0; i < Math.min(CREW, people.size()); i++) crew.builders.add(people.get(i).getUUID());
        }
        if (crew.builders.isEmpty()) return false;
        if (s.next >= s.positions.size()) return true;
        BlockPos next = s.positions.get(s.next);
        BlockState state = s.states.get(s.next);
        int i = 0;
        for (UUID id : crew.builders) {
            if (!(level.getEntity(id) instanceof Villager v) || !v.isAlive()) continue;
            double a = i++ * Math.PI * 2 / CREW + (now / 200) % 6;
            BlockPos column = next.offset((int) Math.round(Math.cos(a) * 2.5), 0, (int) Math.round(Math.sin(a) * 2.5));
            BlockPos stand = level.hasChunkAt(column) ? level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column) : next.above();
            walk(v, stand, 0.55F);
            v.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(next));
            Item hold = s.paving || state.isAir() ? Items.IRON_SHOVEL : state.getBlock().asItem();
            if (hold == Items.AIR) hold = Items.IRON_AXE;
            if (v.distanceToSqr(next.getX() + .5, next.getY(), next.getZ() + .5) <= 36) {
                v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(hold));
                v.setDropChance(EquipmentSlot.MAINHAND, 0);
            }
        }
        return i > 0;
    }

    /** A block went down (or came away) on {@code s}: dust and its sound where a builder can be seen, and now and then a shout. */
    static void placed(ServerLevel level, Rebuild.Site s, BlockPos p, BlockState state, BlockState was) {
        Crew crew = CREWS.get(s);
        if (crew == null || crew.builders.isEmpty()) return;
        BlockState shown = state.isAir() ? was : state;
        if (!shown.isAir()) level.levelEvent(2001, p, Block.getId(shown));
        long now = level.getGameTime();
        if (now - crew.shouted > 400 && level.random.nextInt(4) == 0
                && level.getEntity(crew.builders.get(level.random.nextInt(crew.builders.size()))) instanceof Villager v && v.isAlive()) {
            crew.shouted = now;
            Shouts.talk(level, v, SHOUTS[level.random.nextInt(SHOUTS.length)], 50);
        }
    }

    /** The site is done (or the day is): the crew puts its tools and blocks down. */
    static void done(ServerLevel level, Rebuild.Site s) {
        Crew crew = CREWS.remove(s);
        if (crew != null) release(level, crew);
    }

    private static void release(ServerLevel level, Crew crew) {
        for (UUID id : crew.builders)
            if (level.getEntity(id) instanceof Villager v) v.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        crew.builders.clear();
    }

    private static void walk(Villager v, BlockPos to, float speed) {
        v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(to, speed, 1));
    }

    // ------------------------------------------------------------------
    // Each second: carriers arriving, crews off for the night
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        ServerLevel level = event.getServer().overworld();
        long now = level.getGameTime();
        if (now % 20 != 0) return;
        for (Carry c : List.copyOf(CARRYING.values())) {
            VillageMemory.Village v = VillageMemory.remembers(level, c.bell()) ? VillageMemory.of(level, c.bell()) : null;
            if (v == null) { CARRYING.remove(c.bell().asLong()); continue; }
            if (!level.isPositionEntityTicking(c.bell())) continue;
            Villager carrier = level.getEntity(c.villager()) instanceof Villager x && x.isAlive() ? x : null;
            boolean there = carrier != null && v.board != null && carrier.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(v.board.chest())) <= 9;
            if (there || carrier == null || now - c.since() > CARRY) {
                CARRYING.remove(c.bell().asLong());
                if (carrier != null) carrier.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                give(level, v, c.share());
            } else if (now % 100 == 0 && v.board != null) walk(carrier, v.board.chest().relative(v.board.facing()), 0.6F);
        }
        long time = level.getDayTime() % 24_000;
        if (time > Rebuild.WORK_TO && !CREWS.isEmpty())
            for (Rebuild.Site s : List.copyOf(CREWS.keySet())) done(level, s);
    }

    @SubscribeEvent
    public static void stop(ServerStoppingEvent event) {
        CARRYING.clear();
        CREWS.clear();
    }

    /** Gathers today's share now, whatever the day says (previews). */
    public static void gatherNowForPreview(ServerLevel level, VillageMemory.Village v) {
        if (v.project != null) v.project.gathered = Days.today(level) - 1;
        gather(level, v);
    }

        /** Whether a share for {@code v} is on its way to the board (tests). */
    public static boolean carrying(VillageMemory.Village v) { return CARRYING.containsKey(v.bell.asLong()); }

    /** The builders on {@code s} now (tests, previews). */
    public static List<Villager> crew(ServerLevel level, Rebuild.Site s) {
        List<Villager> out = new ArrayList<>();
        Crew crew = CREWS.get(s);
        if (crew != null) for (UUID id : crew.builders) if (level.getEntity(id) instanceof Villager v) out.add(v);
        return out;
    }
}
