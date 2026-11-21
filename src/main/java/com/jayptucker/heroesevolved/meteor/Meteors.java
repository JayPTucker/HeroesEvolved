package com.jayptucker.heroesevolved.meteor;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.combat.FightingStyles;
import com.jayptucker.heroesevolved.entity.EntityCarryService;
import com.jayptucker.heroesevolved.hazard.HazardDamage;
import com.jayptucker.heroesevolved.network.CameraShakePayload;
import com.jayptucker.heroesevolved.network.MeteorPayload;
import com.jayptucker.heroesevolved.network.SetPiecePayload;
import com.jayptucker.heroesevolved.radio.EmergencyBand;
import com.jayptucker.heroesevolved.radiation.NuclearFallout;
import com.jayptucker.heroesevolved.registry.ModBlocks;
import com.jayptucker.heroesevolved.registry.ModItems;
import com.jayptucker.heroesevolved.styles.Piece;
import com.jayptucker.heroesevolved.village.Shouts;
import com.jayptucker.heroesevolved.village.VillageTickets;
import com.jayptucker.heroesevolved.village.Villages;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Every meteor in the sky: a falling star ({@link MeteorGeometry#FALL} ticks from a point far up to where it lands, its
 * blast ringed on the ground the whole way) or a great meteor coming down on a village ({@link MeteorGeometry#GREAT_FALL},
 * with a distress call). Players answer it by crouching while they look at it, within {@link MeteorGeometry#REACH}: a
 * telekinetic or a flyer pushes it aside, a time-stopper holds it in the sky, a sonic cracks it apart, and a quaker
 * crouching under it braces the ground. Where it lands it leaves a crater and Helixite meteorite.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class Meteors {
    /** The clients hear of each meteor this often, and from this far off (they are seen across the sky). */
    static final int SYNC_EVERY = 5, SEEN = 512;
    /** A great meteor's bar reaches this far; its village is gathered again this often. */
    static final int WATCH = 200, GATHER_EVERY = 40;
    static final int SHOUT_EVERY = 30, SHOUT_TICKS = 50;
    static final String[] SHOUTS = {"Look at the sky!", "It's coming down on us!", "Run!", "Get out of the ring!",
            "It's getting bigger!", "Leave everything, go!", "The sky's on fire!", "Take the children!"};

    private static final Map<Integer, Meteor> ACTIVE = new HashMap<>();
    private static int sequence;

    private Meteors() {}

    public static List<Meteor> all() { return List.copyOf(ACTIVE.values()); }

    public static Meteor get(int id) { return ACTIVE.get(id); }

    // ------------------------------------------------------------------
    // Starting one
    // ------------------------------------------------------------------

    /** A falling star coming down at {@code land} (the ground there), from a random side of the sky. */
    public static Meteor drop(ServerLevel level, Vec3 land) {
        return drop(level, land, false, MeteorGeometry.FALL, level.random.nextDouble() * Math.PI * 2, null);
    }

    /** A great meteor coming down on the village round {@code near} (its bell, if it has one). */
    public static Meteor great(ServerLevel level, BlockPos near, int fall) {
        BlockPos bell = Villages.bell(level, near);
        Vec3 land = ground(level, Vec3.atBottomCenterOf(bell));
        return drop(level, land, true, fall, level.random.nextDouble() * Math.PI * 2, bell);
    }

    /**
     * A meteor falling for {@code fall} ticks onto {@code land}, from the side of the sky at {@code azimuth}. A great one
     * calls for help, holds its village loaded and shows its bar.
     */
    public static Meteor drop(ServerLevel level, Vec3 land, boolean great, int fall, double azimuth, BlockPos bell) {
        return launch(level, MeteorGeometry.start(land, azimuth, great), land, great, fall, bell);
    }

    private static Meteor launch(ServerLevel level, Vec3 start, Vec3 land, boolean great, int fall, BlockPos bell) {
        Meteor m = new Meteor(++sequence, level, great, start, land, fall, bell);
        ACTIVE.put(m.id, m);
        // Where it lands stays loaded until it has (a player who walks off doesn't leave it falling into nothing, and its
        // crater is never carved by loading chunks in a hurry): a great one holds its whole village, a falling star its spot.
        if (!great) VillageTickets.hold(level, BlockPos.containing(land), key(m), VillageTickets.SPOT);
        if (great) {
            gather(m);
            VillageTickets.hold(level, bell, key(m));
            m.call = EmergencyBand.call(level, bell, EmergencyBand.Trouble.METEOR, words(m), fall + 200);
            for (ServerPlayer p : watchers(m))
                p.sendSystemMessage(Component.literal("A great meteor is coming down on a village. Push it aside, hold it back, break it up, or get everyone out.")
                        .withStyle(ChatFormatting.AQUA));
            level.playSound(null, land.x, land.y + 40, land.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 6.0F, 0.4F);
        }
        sync(m, false);
        return m;
    }

    /** The ground under {@code at}: the top of the highest solid column there. */
    static Vec3 ground(ServerLevel level, Vec3 at) {
        BlockPos column = BlockPos.containing(at);
        if (!level.hasChunkAt(column)) return at;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
        return new Vec3(at.x, y, at.z);
    }

    // ------------------------------------------------------------------
    // Running them
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        for (Meteor m : List.copyOf(ACTIVE.values())) if (m.level.getServer() == server) tick(m);
    }

    static void tick(Meteor m) {
        long now = m.level.getGameTime();
        answers(m);
        if (!ACTIVE.containsKey(m.id)) return;
        if (!m.held) m.elapsed++;
        if (m.elapsed >= m.fall) { impact(m); return; }
        // Pushed aside, it lands somewhere else: its spot's ticket goes with it.
        if (!m.great && now % 20 == 0) VillageTickets.hold(m.level, BlockPos.containing(m.land), key(m), VillageTickets.SPOT);
        int left = m.left();
        if (m.great) {
            if (now % GATHER_EVERY == 0) gather(m);
            if (now % 5 == 0) village(m, now);
            if (now % 20 == 0) {
                m.call.say(words(m));
                bar(m);
            }
            if (left < 400 && now % 20 == 0)
                CameraShakePayload.sendAround(m.level, m.land, MeteorGeometry.GREAT_BLAST * 3, 0.15F + 0.5F * (1 - left / 400F), 20);
        }
        // It roars as it comes: a rising whistle near the end.
        int roar = m.great ? 300 : 80;
        if (left < roar && now % (m.great ? 10 : 8) == 0) {
            Vec3 sky = m.sky();
            float k = 1 - left / (float) roar;
            m.level.playSound(null, sky.x, sky.y, sky.z, SoundEvents.PHANTOM_SWOOP, SoundSource.WEATHER, (m.great ? 6 : 3) * (0.4F + k), 0.5F + 0.4F * k);
            m.level.playSound(null, m.land.x, m.land.y, m.land.z, SoundEvents.ELYTRA_FLYING, SoundSource.WEATHER, (m.great ? 3 : 1.5F) * k, 0.4F + 0.6F * k);
        }
        if (now % SYNC_EVERY == 0) sync(m, false);
    }

    /**
     * Who is working on it this tick: crouching while looking at it within {@link MeteorGeometry#REACH} of its ring
     * (a quaker crouching under it instead). Pushes move where it lands, holds stop its fall, cracks and braces add up.
     */
    private static void answers(Meteor m) {
        m.helpers.clear();
        m.held = false;
        Vec3 sky = m.sky();
        double reach = MeteorGeometry.REACH;
        double cos = Math.cos(Math.toRadians(MeteorGeometry.LOOK_DEGREES));
        double push = 0;
        Vec3 pushFrom = null;
        long now = m.level.getGameTime();
        for (ServerPlayer p : m.level.players()) {
            if (!p.isAlive() || p.isSpectator() || !p.isShiftKeyDown()) continue;
            if (p.position().subtract(m.land).horizontalDistanceSqr() > reach * reach) continue;
            Meteor.Help help = null;
            if (FightingStyles.hasPower(p, ModAbilities.QUAKE_ID)) {
                if (p.position().subtract(m.land).horizontalDistanceSqr() <= MeteorGeometry.BRACE_REACH * MeteorGeometry.BRACE_REACH) {
                    help = Meteor.Help.BRACE;
                    m.brace++;
                }
            } else if (looking(p, sky, cos)) {
                if (FightingStyles.hasPower(p, ModAbilities.TELEKINESIS_ID)) { help = Meteor.Help.PUSH; push += MeteorGeometry.PUSH; }
                else if (FightingStyles.hasPower(p, ModAbilities.FLIGHT_ID)) { help = Meteor.Help.WIND; push += MeteorGeometry.WIND_PUSH; }
                else if (FightingStyles.hasPower(p, ModAbilities.TIME_MANIPULATION_ID)) { help = Meteor.Help.HOLD; m.held = true; }
                else if (FightingStyles.hasPower(p, ModAbilities.SONIC_RESONANCE_ID)) { help = Meteor.Help.CRACK; m.crack++; }
            }
            if (help == null) continue;
            m.helpers.add(new Meteor.Helper(p, help));
            if (help != Meteor.Help.BRACE) m.lastHelper = p;
            if ((help == Meteor.Help.PUSH || help == Meteor.Help.WIND) && pushFrom == null) pushFrom = p.position();
            if (now % 10 == 0) p.displayClientMessage(progress(m, help), true);
        }
        if (push > 0) shove(m, push, pushFrom);
        if (m.cracked() >= 1) shatter(m);
    }

    /** Whether {@code p} is looking at {@code at}, within the angle whose cosine is {@code cos}. */
    static boolean looking(ServerPlayer p, Vec3 at, double cos) {
        Vec3 to = at.subtract(p.getEyePosition());
        double d = to.length();
        return d > 1.0E-3 && p.getLookAngle().dot(to.scale(1 / d)) >= cos;
    }

    private static Component progress(Meteor m, Meteor.Help help) {
        String words = switch (help) {
            case PUSH, WIND -> "Pushing it aside  " + Math.round(m.pushed) + " blocks";
            case HOLD -> "Holding it in the sky";
            case CRACK -> "Cracking it  " + Math.round(m.cracked() * 100) + "%";
            case BRACE -> "Bracing the ground  " + Math.round(m.braced() * 100) + "%";
        };
        return Component.literal(words).withStyle(ChatFormatting.AQUA);
    }

    /** Pushed aside: away from its village's bell (or from whoever pushes), along the ground. */
    private static void shove(Meteor m, double by, Vec3 pusher) {
        Vec3 from = m.bell != null ? Vec3.atBottomCenterOf(m.bell) : pusher;
        Vec3 away = m.land.subtract(from).multiply(1, 0, 1);
        if (away.lengthSqr() < 0.25) away = pusher == null ? new Vec3(1, 0, 0) : m.land.subtract(pusher).multiply(1, 0, 1);
        if (away.lengthSqr() < 0.25) away = new Vec3(1, 0, 0);
        Vec3 next = m.land.add(away.normalize().scale(by));
        m.land = ground(m.level, next);
        m.pushed += by;
    }

    /**
     * Cracked through: a great meteor breaks into {@link MeteorGeometry#SHARDS} falling stars, each coming down from where
     * it broke onto ground round its ring; a falling star bursts in the sky and rains its shards down.
     */
    static void shatter(Meteor m) {
        Vec3 sky = m.sky();
        finish(m);
        // Sent by where it would have landed: set pieces reach players near their origin or target, and it broke far up.
        SetPiecePayload.send(m.level, Piece.METEOR_SHATTER, sky, m.land, MeteorGeometry.rock(m.great), null);
        m.level.playSound(null, sky.x, sky.y, sky.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.WEATHER, m.great ? 8 : 4, 0.5F);
        m.level.playSound(null, m.land.x, m.land.y, m.land.z, SoundEvents.GLASS_BREAK, SoundSource.WEATHER, 3, 0.4F);
        if (!m.great) {
            for (int i = 0; i < 2; i++) drop(m.level, m.land, new ItemStack(ModItems.HELIXITE_SHARD.get()));
            return;
        }
        int fall = Math.max(100, m.left());
        for (int i = 0; i < MeteorGeometry.SHARDS; i++) {
            double a = i * Math.PI * 2 / MeteorGeometry.SHARDS + m.level.random.nextDouble() * 0.6;
            double d = 6 + m.level.random.nextDouble() * (MeteorGeometry.SHARD_SPREAD - 6);
            Vec3 land = ground(m.level, m.land.add(Math.cos(a) * d, 0, Math.sin(a) * d));
            launch(m.level, sky, land, false, fall, null);
        }
        List<ServerPlayer> watching = watchers(m);
        for (ServerPlayer p : watching)
            p.sendSystemMessage(Component.literal("It cracked apart! " + MeteorGeometry.SHARDS + " pieces are coming down instead.")
                    .withStyle(ChatFormatting.AQUA));
        if (m.bell != null)
            com.jayptucker.heroesevolved.village.VillageMemory.record(m.level, m.bell, "a great meteor",
                    com.jayptucker.heroesevolved.village.VillageMemory.Outcome.SAVED, m.lastHelper, watching, m.alive(), m.village.size());
    }

    // ------------------------------------------------------------------
    // Landing
    // ------------------------------------------------------------------

    /**
     * It lands: everything in its blast is hurt (worst at the middle), thrown and set alight; a crater is torn out and its
     * meteorite left at the bottom, where {@code mobGriefing} allows (otherwise its shards are dropped). Braced ground holds:
     * no crater, and half the blast.
     */
    static void impact(Meteor m) {
        List<ServerPlayer> watching = m.great ? watchers(m) : List.of();
        finish(m);
        ServerLevel level = m.level;
        Vec3 at = m.land;
        boolean braced = m.braced() >= 1;
        float blast = MeteorGeometry.blast(m.great);
        float most = (m.great ? 40 : 12) * (braced ? 0.5F : 1), least = (m.great ? 4 : 2) * (braced ? 0.5F : 1);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(blast),
                e -> e.isAlive() && !e.isSpectator() && e.distanceToSqr(at) <= blast * blast)) {
            double d = e.position().distanceTo(at);
            float k = (float) (1 - d / blast);
            e.hurt(HazardDamage.source(level, HazardDamage.METEOR), least + (most - least) * k);
            Vec3 out = e.position().subtract(at);
            out = out.lengthSqr() < 1.0E-4 ? new Vec3(0, 1, 0) : out.normalize();
            e.push(out.x * (0.6 + 1.4 * k), 0.4 + 0.6 * k, out.z * (0.6 + 1.4 * k));
            e.hurtMarked = true;
            e.igniteForSeconds(m.great ? 6 : 3);
        }
        boolean griefing = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        if (griefing && !braced) {
            // A great one landing in its village: note what stood there first, to rebuild if the village lives.
            int r = Math.round(MeteorGeometry.crater(m.great));
            if (m.great && m.bell != null && at.distanceTo(Vec3.atBottomCenterOf(m.bell)) <= Villages.RADIUS)
                m.site = com.jayptucker.heroesevolved.village.Rebuild.note(level, m.bell, BlockPos.containing(at).above(Math.round(r * 0.45F)), r + 1);
            crater(level, BlockPos.containing(at), MeteorGeometry.crater(m.great), m.great);
        }
        else for (int i = 0; i < (m.great ? 14 : 3); i++) drop(level, at, new ItemStack(ModItems.HELIXITE_SHARD.get()));
        SetPiecePayload.send(level, Piece.METEOR_IMPACT, at, at.add(0, 1, 0), blast, null);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.WEATHER, m.great ? 8 : 3, m.great ? 0.45F : 0.8F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.WEATHER, m.great ? 6 : 2, 0.6F);
        CameraShakePayload.sendAround(level, at, blast * 5, m.great ? 1.0F : 0.5F, m.great ? 30 : 12);
        if (m.great) level.getServer().tell(new net.minecraft.server.TickTask(level.getServer().getTickCount() + 10, () -> tally(m, watching, braced)));
    }

    /**
     * The crater: a bowl {@code r} across (a sphere carved from a little above the ground), its floor scorched to
     * blackstone and magma, its meteorite at the bottom (a heap of seven for a great one), and the ground round its rim
     * burnt.
     */
    static void crater(ServerLevel level, BlockPos at, float radius, boolean great) {
        int r = Math.round(radius);
        NuclearFallout.carve(level, at.above(Math.round(r * 0.45F)), r, null);
        // It heals over the next days into a landmark (village/Scars).
        com.jayptucker.heroesevolved.village.Scars.crater(level, at, r + (great ? 5 : 3));
        // The floor is found by looking down from where it struck, never from the heightmap: under a tree or a roof the
        // heightmap's top is the overhang, not the bottom of the bowl.
        BlockPos floor = floorUnder(level, at, r + 4);
        if (floor == null) return;
        var random = level.random;
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > r + 1.5) continue;
                BlockPos ground = floorUnder(level, at.offset(dx, d <= r * 0.75 ? 0 : 1, dz), r + 4);
                if (ground == null) continue;
                BlockState was = level.getBlockState(ground);
                if (was.isAir() || !was.getFluidState().isEmpty() || was.getDestroySpeed(level, ground) < 0 || level.getBlockEntity(ground) != null) continue;
                if (d <= r * 0.75) {
                    float roll = random.nextFloat();
                    if (roll < 0.12F) level.setBlockAndUpdate(ground, Blocks.MAGMA_BLOCK.defaultBlockState());
                    else if (roll < 0.55F) level.setBlockAndUpdate(ground, (random.nextBoolean() ? Blocks.BLACKSTONE : Blocks.BASALT).defaultBlockState());
                } else com.jayptucker.heroesevolved.hazard.Scorch.column(level, ground);
            }
        // A lip of thrown-out rubble round the rim, on the ground there, so it reads as an impact and not a dug pit.
        int lip = great ? 90 : 14;
        for (int i = 0; i < lip; i++) {
            double a = random.nextDouble() * Math.PI * 2, d = r + 0.5 + random.nextDouble() * (great ? 4 : 2);
            BlockPos column = at.offset((int) Math.round(Math.cos(a) * d), 2, (int) Math.round(Math.sin(a) * d));
            BlockPos under = floorUnder(level, column, 6);
            if (under == null || !level.getBlockState(under).isFaceSturdy(level, under, net.minecraft.core.Direction.UP)) continue;
            BlockPos top = under.above();
            if (!level.getBlockState(top).canBeReplaced() || !level.getBlockState(top).getFluidState().isEmpty()) continue;
            float roll = random.nextFloat();
            level.setBlockAndUpdate(top, (roll < 0.45F ? Blocks.COARSE_DIRT : roll < 0.75F ? Blocks.COBBLED_DEEPSLATE : Blocks.BLACKSTONE).defaultBlockState());
        }
        // The meteorite sits in the floor, or on it where the floor can't be broken (bedrock).
        BlockPos seat = level.getBlockState(floor).getDestroySpeed(level, floor) < 0 ? floor.above() : floor;
        level.setBlockAndUpdate(seat, ModBlocks.HELIXITE_METEORITE.get().defaultBlockState());
        if (great) {
            for (int[] o : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}}) {
                BlockPos p = seat.offset(o[0], 0, o[1]);
                if (level.getBlockState(p).getDestroySpeed(level, p) >= 0) level.setBlockAndUpdate(p, ModBlocks.HELIXITE_METEORITE.get().defaultBlockState());
            }
            level.setBlockAndUpdate(seat.above(), ModBlocks.HELIXITE_METEORITE.get().defaultBlockState());
        }
    }

    /** The first solid, dry block at or under {@code from}, looking at most {@code depth} down; null if there is none. */
    static BlockPos floorUnder(ServerLevel level, BlockPos from, int depth) {
        BlockPos.MutableBlockPos p = from.mutable();
        for (int i = 0; i <= depth && p.getY() >= level.getMinBuildHeight(); i++, p.move(net.minecraft.core.Direction.DOWN)) {
            if (!level.hasChunkAt(p)) return null;
            BlockState s = level.getBlockState(p);
            if (!s.isAir() && s.getFluidState().isEmpty() && !s.getCollisionShape(level, p).isEmpty()) return p.immutable();
        }
        return null;
    }

    private static void drop(ServerLevel level, Vec3 at, ItemStack stack) {
        ItemEntity item = new ItemEntity(level, at.x, at.y + 0.5, at.z, stack);
        item.setDeltaMovement((level.random.nextDouble() - 0.5) * 0.3, 0.3, (level.random.nextDouble() - 0.5) * 0.3);
        level.addFreshEntity(item);
    }

    private static void tally(Meteor m, List<ServerPlayer> watching, boolean braced) {
        int alive = m.alive(), all = m.village.size();
        boolean everyone = alive == all;
        boolean clear = m.bell != null && m.land.subtract(Vec3.atBottomCenterOf(m.bell)).horizontalDistance()
                > MeteorGeometry.GREAT_BLAST + Villages.RADIUS * 0.5;
        if (m.bell != null) {
            var outcome = all > 0 && alive == 0 ? com.jayptucker.heroesevolved.village.VillageMemory.Outcome.FALLEN
                    : everyone || clear ? com.jayptucker.heroesevolved.village.VillageMemory.Outcome.SAVED
                    : com.jayptucker.heroesevolved.village.VillageMemory.Outcome.HIT;
            com.jayptucker.heroesevolved.village.VillageMemory.record(m.level, m.bell, "a great meteor", outcome, m.lastHelper, watching, alive, all);
            if (m.site != null) {
                if (outcome == com.jayptucker.heroesevolved.village.VillageMemory.Outcome.FALLEN) com.jayptucker.heroesevolved.village.Rebuild.drop(m.level, m.site);
                else com.jayptucker.heroesevolved.village.Rebuild.begin(m.level, m.site);
            }
        }
        String head = clear ? "It came down clear of the village. " : braced ? "The ground held. " : "It came down on the village. ";
        for (ServerPlayer p : watching) {
            if (p.isRemoved()) continue;
            if (everyone && p.isAlive()) p.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 20 * 60 * 20, 0, false, false, true));
            p.sendSystemMessage(Component.literal(head + (all == 0 ? "" : everyone ? "All " + all + " of the village lived." : alive + " of " + all + " of the village lived."))
                    .withStyle(everyone ? ChatFormatting.GREEN : ChatFormatting.GOLD));
        }
    }

    // ------------------------------------------------------------------
    // A great meteor's village
    // ------------------------------------------------------------------

    static void gather(Meteor m) {
        if (m.bell != null) for (Villager v : Villages.members(m.level, m.bell)) m.village.add(v.getUUID());
    }

    /** Its village inside the ring glows and runs from it, and someone near a player shouts. */
    private static void village(Meteor m, long now) {
        double reach = MeteorGeometry.GREAT_BLAST + 4;
        List<Villager> danger = new ArrayList<>();
        for (UUID id : m.village) {
            if (!(m.level.getEntity(id) instanceof Villager v) || !v.isAlive() || EntityCarryService.isBeingCarried(v)) continue;
            if (v.position().subtract(m.land).horizontalDistanceSqr() > reach * reach) continue;
            danger.add(v);
            v.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20, 0, false, false, false));
            Shouts.runFrom(v, m.land, reach + 10);
        }
        if (now >= m.nextShout && !danger.isEmpty()) {
            List<Villager> heard = danger.stream().filter(v -> m.level.players().stream()
                    .anyMatch(p -> !p.isSpectator() && p.distanceToSqr(v) <= 32 * 32)).toList();
            if (!heard.isEmpty())
                Shouts.say(m.level, heard.get(m.level.random.nextInt(heard.size())),
                        SHOUTS[m.shouts++ == 0 ? 0 : m.level.random.nextInt(SHOUTS.length)], SHOUT_TICKS);
            m.nextShout = now + SHOUT_EVERY;
        }
    }

    private static void bar(Meteor m) {
        int seconds = (m.left() + 19) / 20;
        String state = m.held ? "   Held" : m.crack > 0 ? "   Cracked " + Math.round(m.cracked() * 100) + "%"
                : m.pushed >= 1 ? "   Pushed " + Math.round(m.pushed) + " blocks" : "";
        m.bar.setName(Component.literal("Great meteor  " + seconds / 60 + ":" + String.format("%02d", seconds % 60) + state
                + (m.brace > 0 ? "   Braced " + Math.round(m.braced() * 100) + "%" : "") + "     Village " + m.alive() + "/" + m.village.size()));
        m.bar.setProgress(Mth.clamp(1 - m.fallen(), 0, 1));
        m.bar.setColor(m.left() < 400 ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.BLUE);
        Set<ServerPlayer> near = new HashSet<>(watchers(m));
        for (ServerPlayer p : List.copyOf(m.bar.getPlayers())) if (!near.contains(p)) m.bar.removePlayer(p);
        for (ServerPlayer p : near) m.bar.addPlayer(p);
    }

    private static Component words(Meteor m) {
        int seconds = (m.left() + 19) / 20;
        return Component.literal("Something's falling on us from the sky! Help! " + seconds / 60 + ":" + String.format("%02d", seconds % 60));
    }

    static List<ServerPlayer> watchers(Meteor m) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : m.level.players())
            if (!p.isSpectator() && p.position().subtract(m.land).horizontalDistanceSqr() <= (double) WATCH * WATCH) out.add(p);
        return out;
    }

    // ------------------------------------------------------------------
    // Ending
    // ------------------------------------------------------------------

    /** It is gone from the sky: stop everything it set going. */
    private static void finish(Meteor m) {
        ACTIVE.remove(m.id);
        if (m.call != null) EmergencyBand.end(m.call);
        VillageTickets.release(key(m));
        m.bar.removeAllPlayers();
        for (UUID id : m.village) if (m.level.getEntity(id) instanceof Villager v) v.removeEffect(MobEffects.GLOWING);
        sync(m, true);
    }

    /** Burns it up harmlessly (admins, tests). */
    public static void cancel(Meteor m) { if (ACTIVE.containsKey(m.id)) finish(m); }

    public static int cancelAll() {
        List<Meteor> all = all();
        for (Meteor m : all) finish(m);
        return all.size();
    }

    /** Brings its landing forward: {@code ticks} left (tests, previews). */
    public static void hurry(Meteor m, int ticks) { m.elapsed = Math.max(m.elapsed, m.fall - Math.max(1, ticks)); }

    /** Sets how far it has fallen (previews). */
    public static void fallen(Meteor m, float fallen) { m.elapsed = Math.round(m.fall * Mth.clamp(fallen, 0, 0.999F)); }

    private static String key(Meteor m) { return "meteor:" + m.id; }

    /** Tells everyone who can see it where it is. */
    static void sync(Meteor m, boolean ended) {
        int[] helpers = new int[m.helpers.size()], kinds = new int[m.helpers.size()];
        for (int i = 0; i < helpers.length; i++) { helpers[i] = m.helpers.get(i).player().getId(); kinds[i] = m.helpers.get(i).help().ordinal(); }
        MeteorPayload payload = new MeteorPayload(m.id, m.great, m.start, m.land, m.elapsed, m.fall, m.held, m.cracked(), m.braced(),
                helpers, kinds, ended);
        for (ServerPlayer p : m.level.players())
            if (ended || p.position().distanceToSqr(m.land) <= (double) SEEN * SEEN) PacketDistributor.sendToPlayer(p, payload);
    }

    @SubscribeEvent
    public static void died(LivingDeathEvent event) {
        UUID id = event.getEntity().getUUID();
        for (Meteor m : ACTIVE.values()) if (m.village.contains(id)) m.dead.add(id);
    }

    @SubscribeEvent
    public static void stop(ServerStoppingEvent event) { clear(); }

    public static void clear() {
        for (Meteor m : List.copyOf(ACTIVE.values())) finish(m);
        ACTIVE.clear();
    }

    /** One line for admins. */
    public static String describe() {
        List<Meteor> all = all();
        long great = all.stream().filter(Meteor::great).count();
        return all.isEmpty() ? "No meteors in the sky." : all.size() + " meteor(s) in the sky" + (great > 0 ? ", " + great + " great" : "") + ".";
    }

    static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, path); }
}
