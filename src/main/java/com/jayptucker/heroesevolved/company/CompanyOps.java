package com.jayptucker.heroesevolved.company;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.registry.ModEntities;
import com.jayptucker.heroesevolved.village.Shouts;
import com.jayptucker.heroesevolved.village.VillageTickets;
import com.jayptucker.heroesevolved.village.Villages;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * When the Company acts on what it knows ({@link CompanyAttention}), and the operations under way: an
 * {@link Investigation} after a big move is seen in a village (Noticed), a {@link Convoy} that takes a gifted villager
 * (Watched), a {@link Raid} on a player's home at night (Hunted). One at a time for each player, with a rest between.
 *
 * <p>Operations aren't saved. Their agents and prisoners carry {@link #AGENT_TAG} and {@link #PRISONER_TAG}; one that
 * loads again later (a chunk, a restart) and isn't part of a live operation is turned away (an agent) or let go (a
 * prisoner). This class also answers whom an agent on an errand stops ({@link #stops}), the question
 * {@code CompanyAgentEntity.wouldStop} asks.</p>
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class CompanyOps {
    public static final String AGENT_TAG = HeroesEvolved.MOD_ID + "_company_errand";
    public static final String PRISONER_TAG = HeroesEvolved.MOD_ID + "_company_prisoner";
    public enum Kind { NONE, INVESTIGATION, CONVOY, RAID }
    /** An investigator arrives this long after the move that brought him. */
    public static final int INVESTIGATOR_DELAY = 1200;
    /** The rest after an operation before the next one. */
    public static final long AFTER_INVESTIGATION = 12_000, AFTER_OP = 24_000;
    /** A convoy sets out from a village within this of its subject; a raid comes when they are this close to home. */
    public static final int CONVOY_REACH = 160, HOME_REACH = 64;
    /** Homes this close are the same home, for raids. */
    public static final double SHARED_HOME = 96;
    private static final int CONSIDER_EVERY = 200;

    /** One operation: whom it is about, the agents it brought, and how it goes. */
    public abstract static class Op {
        final ServerLevel level;
        final ServerPlayer subject;
        final List<CompanyAgentEntity> agents = new ArrayList<>();
        final long started;
        final int id = ++sequence;
        int age;
        boolean over;

        Op(ServerLevel level, ServerPlayer subject) {
            this.level = level;
            this.subject = subject;
            this.started = level.getGameTime();
        }

        /** Its tickets' keys start with this. */
        String key() { return "company:" + id + ":"; }

        /**
         * Keeps the chunks round {@code at} loaded and ticking for as long as it runs (a village's worth), under
         * {@code part}; keeping the same part somewhere else moves it. Released when it ends.
         */
        void keep(BlockPos at, String part) { VillageTickets.hold(level, at, key() + part); }

        /** Whether one of its people has been unloaded (or left the level) rather than killed: it can't go on. */
        boolean lostTrack() {
            for (CompanyAgentEntity a : agents) if (lost(a)) return true;
            return false;
        }

        public abstract Kind kind();
        public ServerPlayer subject() { return subject; }
        public List<CompanyAgentEntity> agents() { return List.copyOf(agents); }
        public boolean over() { return over; }
        public int age() { return age; }

        abstract void tick();

        /** Whether {@code agent} of this operation stops {@code p} (turns on them). */
        boolean stops(CompanyAgentEntity agent, ServerPlayer p) { return false; }

        /** {@code p} used a power costing {@code cost}: true if this is news an agent of this operation reacts to. */
        boolean powerUsed(ServerPlayer p, int cost) { return false; }

        /** One of its agents died. */
        void down(CompanyAgentEntity agent, DamageSource source) {}

        /** Called off now (admin, a restart, the subject gone): everyone it brought leaves. */
        void callOff() {
            for (CompanyAgentEntity a : agents) leave(a);
            finish(0);
        }

        /** Over, and no new operation for {@code rest} ticks. */
        void finish(long rest) {
            if (over) return;
            over = true;
            VillageTickets.releaseAll(key());
            CompanyAttention.File f = CompanyAttention.file(subject);
            f.nextOp = Math.max(f.nextOp, level.getServer().overworld().getGameTime() + rest);
            CompanyAttention.get(level.getServer()).setDirty();
            CompanyAttention.sync(subject);
        }

        /** An agent for this operation, at {@code at}: tagged, off his leash, and in the world. */
        CompanyAgentEntity spawn(CompanyAgentEntity.Role role, Vec3 at, float yaw) {
            CompanyAgentEntity a = CompanyAgentEntity.create(ModEntities.COMPANY_AGENT.get(), level, role, at, yaw, false);
            if (a == null) return null;
            a.addTag(AGENT_TAG);
            a.clearRestriction();
            // Out in the open he keeps track of whoever he's after further than at a post.
            var follow = a.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);
            if (follow != null) follow.setBaseValue(32);
            LIVE.add(a.getUUID());
            if (!level.addFreshEntity(a)) {
                LIVE.remove(a.getUUID());
                return null;
            }
            agents.add(a);
            return a;
        }

        /** The agents still on their feet. */
        List<CompanyAgentEntity> standing() {
            List<CompanyAgentEntity> out = new ArrayList<>();
            for (CompanyAgentEntity a : agents) if (!downed(a)) out.add(a);
            return out;
        }

        /** Whether the subject is gone (logged out, or a test's player removed). */
        boolean subjectGone() { return subject.isRemoved() && (subject.hasDisconnected() || subject.level() != level || !subject.isAlive()); }

        /** A line in chat to everyone within {@code reach} of {@code at}, and always to the subject. */
        void tell(Vec3 at, double reach, String line) {
            Component c = Component.literal(line).withStyle(ChatFormatting.DARK_AQUA);
            for (ServerPlayer p : level.players())
                if (p != subject && p.distanceToSqr(at) <= reach * reach) p.sendSystemMessage(c);
            subject.sendSystemMessage(c);
        }

        /** An agent's words, over his head and in chat to everyone within {@code reach}. */
        void speak(CompanyAgentEntity agent, String words, double reach) {
            Shouts.talk(level, agent, words, 70);
            Component c = Component.literal("Agent: " + words).withStyle(ChatFormatting.GRAY);
            for (ServerPlayer p : level.players()) if (p.distanceToSqr(agent) <= reach * reach) p.sendSystemMessage(c);
        }
    }

    private record Pending(ServerPlayer subject, ServerLevel level, BlockPos site, BlockPos bell, long due) {}

    private static final List<Op> OPS = new ArrayList<>();
    private static int sequence;
    private static final String PREP = "company:prep:";
    /** Players whose next operation's place is loading, and until when its ticket stays. */
    private static final java.util.Map<UUID, Long> PREPARING = new java.util.HashMap<>();
    private static final List<Pending> PENDING = new ArrayList<>();
    /** Agents and prisoners of live operations. */
    static final Set<UUID> LIVE = new HashSet<>();

    private CompanyOps() {}

    static void add(Op op) {
        OPS.add(op);
        CompanyAttention.sync(op.subject);
        HeroesEvolved.LOGGER.info("Company {} for {}", op.kind(), op.subject.getName().getString());
    }

    public static List<Op> all() { return List.copyOf(OPS); }

    /** {@code p}'s operation under way, or null. */
    public static Op current(ServerPlayer p) {
        for (Op op : OPS) if (!op.over && op.subject.getUUID().equals(p.getUUID())) return op;
        return null;
    }

    public static Kind kind(ServerPlayer p) {
        Op op = current(p);
        return op == null ? Kind.NONE : op.kind();
    }

    /** The operation {@code entity} is an agent or prisoner of, or null. */
    public static Op of(Entity entity) {
        for (Op op : OPS) {
            if (op.over) continue;
            if (entity instanceof CompanyAgentEntity a && op.agents.contains(a)) return op;
            if (op instanceof Convoy c && c.prisoner == entity) return op;
        }
        return null;
    }

    /** Whether an agent on an errand stops {@code p}. An errand left over from a called-off operation stops nobody. */
    public static boolean stops(CompanyAgentEntity agent, ServerPlayer p) {
        Op op = of(agent);
        return op != null && op.stops(agent, p);
    }

    /**
     * Down: dead, discarded, or out cold. An agent whose chunk was unloaded (or who changed level) is not down, only
     * {@linkplain #lost lost}: treating unloading as death once freed a convoy's prisoner, with its rewards, when its
     * escort's chunks unloaded.
     */
    static boolean downed(CompanyAgentEntity a) {
        if (a.isRemoved()) return a.getRemovalReason() != null && a.getRemovalReason().shouldDestroy();
        return !a.isAlive() || a.outCold();
    }

    /** Removed from the level without being destroyed: unloaded with its chunk, or gone to another level. */
    public static boolean lost(Entity e) {
        return e != null && e.isRemoved() && (e.getRemovalReason() == null || !e.getRemovalReason().shouldDestroy());
    }

    /** Whether {@code at} is loaded and ticking entities: somewhere an operation can begin. */
    public static boolean ready(ServerLevel level, BlockPos at) { return level.isPositionEntityTicking(at); }

    /**
     * Whether an operation for {@code p} can begin at {@code at} now. If it isn't loaded and ticking yet, a ticket starts
     * loading it (for a minute) and this says no; asking again shortly says yes.
     */
    static boolean prepare(ServerLevel level, BlockPos at, ServerPlayer p) {
        String key = PREP + p.getUUID();
        if (ready(level, at)) {
            VillageTickets.release(key);
            PREPARING.remove(p.getUUID());
            return true;
        }
        VillageTickets.hold(level, at, key);
        PREPARING.put(p.getUUID(), level.getServer().overworld().getGameTime() + 1200);
        return false;
    }

    /** {@code p} used a power: news to any operation near them. True if an agent saw it (and it counts against them). */
    static boolean powerUsed(ServerPlayer p, int cost) {
        boolean saw = false;
        for (Op op : List.copyOf(OPS)) if (!op.over && op.level == p.level() && op.powerUsed(p, cost)) saw = true;
        return saw;
    }

    // ------------------------------------------------------------------
    // When the Company acts
    // ------------------------------------------------------------------

    /**
     * {@code p} was seen doing something big at {@code site}: if that left them Noticed, and it was in a village, someone
     * comes to ask questions there a minute later (or the next morning).
     */
    static void noticed(ServerPlayer p, BlockPos site) {
        if (CompanyAttention.band(p) != CompanyAttention.Band.NOTICED || current(p) != null) return;
        for (Pending q : PENDING) if (q.subject.getUUID().equals(p.getUUID())) return;
        ServerLevel level = p.serverLevel();
        long now = level.getServer().overworld().getGameTime();
        if (now < CompanyAttention.file(p).nextOp || !Villages.hasBell(level, site)) return;
        BlockPos bell = Villages.bell(level, site);
        // One investigator to a village at a time (two players noticed there don't bring two).
        for (Pending q : PENDING) if (q.bell.equals(bell)) return;
        for (Op op : OPS) if (!op.over && op instanceof Investigation i && i.bell.equals(bell)) return;
        PENDING.add(new Pending(p, level, site.immutable(), bell, now + INVESTIGATOR_DELAY));
    }

    /** An investigation waiting to start for {@code p} (tests). */
    public static boolean pending(ServerPlayer p) {
        for (Pending q : PENDING) if (q.subject.getUUID().equals(p.getUUID())) return true;
        return false;
    }

    /** Daytime: when investigators ask questions and convoys set out. */
    static boolean day(ServerLevel level) { return day(level.getDayTime() % 24_000); }
    static boolean day(long timeOfDay) { return timeOfDay < 11_500; }

    /** Night: when raids come. */
    static boolean night(ServerLevel level) { return night(level.getDayTime() % 24_000); }
    static boolean night(long timeOfDay) { return timeOfDay >= 13_000 && timeOfDay < 22_500; }

    /** Where {@code p} sleeps (their bed or respawn point, in the overworld), or null. */
    public static BlockPos home(ServerPlayer p) {
        return p.getRespawnDimension() == Level.OVERWORLD ? p.getRespawnPosition() : null;
    }

    /**
     * Whether the Company acts on {@code p} now, and if so, does: Watched by day near a village, a convoy; Hunted by night
     * near home, a raid. Returns the operation it started, or null. Run every ten seconds for every player.
     */
    public static Op consider(ServerPlayer p) { return consider(p, p.serverLevel().getDayTime() % 24_000); }

    /** {@link #consider(ServerPlayer)} at {@code timeOfDay} (0 to 23999; tests). */
    public static Op consider(ServerPlayer p, long timeOfDay) {
        if (!CompanyAttention.noticeable(p) || current(p) != null || p.isDeadOrDying()) return null;
        ServerLevel level = p.serverLevel();
        if (level.getServer().overworld().getGameTime() < CompanyAttention.file(p).nextOp) return null;
        switch (CompanyAttention.band(p)) {
            case HUNTED -> {
                if (!night(timeOfDay)) return null;
                BlockPos home = home(p);
                if (home != null && home.distSqr(p.blockPosition()) > (long) HOME_REACH * HOME_REACH) return null;
                BlockPos at = home != null ? home : p.blockPosition();
                // Friends who share a home share its raid: one at a time there, whoever it is for.
                if (Raid.near(level, at, SHARED_HOME) != null) return null;
                return prepare(level, at, p) ? Raid.start(level, p, at, null) : null;
            }
            case WATCHED -> {
                if (!day(timeOfDay)) return null;
                BlockPos bell = convoyVillage(p);
                return bell == null || !prepare(level, bell, p) ? null : Convoy.start(level, p, bell, null);
            }
            default -> { return null; }
        }
    }

    /** The village a convoy sets out from: where {@code p} was last seen, if near enough, or the nearest to them. */
    static BlockPos convoyVillage(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        BlockPos site = CompanyAttention.file(p).site();
        long reach = (long) CONVOY_REACH * CONVOY_REACH;
        if (site != null && site.distSqr(p.blockPosition()) <= reach && Villages.hasBell(level, site)) return Villages.bell(level, site);
        return level.getPoiManager().findClosest(poi -> poi.is(PoiTypes.MEETING), p.blockPosition(), CONVOY_REACH,
                PoiManager.Occupancy.ANY).orElse(null);
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) { tick(event.getServer()); }

    static void tick(MinecraftServer server) {
        for (Op op : List.copyOf(OPS)) {
            if (op.over) { forget(op); continue; }
            if (op.subjectGone()) { op.callOff(); forget(op); continue; }
            if (op.lostTrack()) {
                // Its tickets hold its place loaded, so this is rare: an agent pushed out of it, or a restart's edge.
                HeroesEvolved.LOGGER.info("Company {} for {} lost track of its people; called off", op.kind(), op.subject.getName().getString());
                op.callOff();
                forget(op);
                continue;
            }
            op.age++;
            op.tick();
            if (op.over) forget(op);
        }
        long now = server.overworld().getGameTime();
        PENDING.removeIf(q -> {
            if (q.subject.isRemoved() || now > q.due + 48_000) return true;
            // He waits while the village has trouble of its own.
            if (now < q.due || !day(q.level) || current(q.subject) != null
                    || com.jayptucker.heroesevolved.radio.EmergencyBand.troubled(q.level, q.site)) return false;
            // The village it happened in may be a long way from anyone now: load it first, then send him.
            if (!prepare(q.level, q.site, q.subject)) return false;
            Investigation.start(q.level, q.subject, q.site, q.bell, null);
            return true;
        });
        PREPARING.entrySet().removeIf(e -> {
            if (now < e.getValue()) return false;
            VillageTickets.release(PREP + e.getKey());
            return true;
        });
        if (now % CONSIDER_EVERY == 0) for (ServerPlayer p : server.getPlayerList().getPlayers()) consider(p);
    }

    private static void forget(Op op) {
        OPS.remove(op);
        for (CompanyAgentEntity a : op.agents) LIVE.remove(a.getUUID());
        if (op instanceof Convoy c && c.prisoner != null) LIVE.remove(c.prisoner.getUUID());
        CompanyAttention.sync(op.subject);
    }

    /** Calls every operation off (admin). Returns how many. */
    public static int callOffAll() {
        int n = 0;
        for (Op op : List.copyOf(OPS)) {
            if (op.over) continue;
            op.callOff();
            forget(op);
            n++;
        }
        PENDING.clear();
        VillageTickets.releaseAll(PREP);
        PREPARING.clear();
        return n;
    }

    // ------------------------------------------------------------------
    // Agents and prisoners
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void joined(EntityJoinLevelEvent event) {
        Entity e = event.getEntity();
        if (e.level().isClientSide() || LIVE.contains(e.getUUID())) return;
        if (e instanceof CompanyAgentEntity && e.getTags().contains(AGENT_TAG)) event.setCanceled(true);
        else if (e instanceof Villager && e.getTags().contains(PRISONER_TAG)) e.removeTag(PRISONER_TAG);
    }

    @SubscribeEvent
    public static void died(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof CompanyAgentEntity a) || !a.getTags().contains(AGENT_TAG)) return;
        Op op = of(a);
        if (op != null) op.down(a, event.getSource());
    }

    /**
     * Sleep would carry a Hunted player past the night their raid comes (in singleplayer, every night): so lying down
     * is when it comes. Nobody sleeps while a raid is under way nearby either, as with monsters near a bed.
     */
    @SubscribeEvent
    public static void sleep(net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent event) {
        if (event.getProblem() != null || !(event.getLevel() instanceof ServerLevel level)) return;
        ServerPlayer p = event.getEntity();
        BlockPos bed = event.getPos();
        Raid nearby = Raid.near(level, bed, SHARED_HOME);
        if (nearby != null && nearby.stage() != Raid.Stage.BEATEN) {
            event.setProblem(net.minecraft.world.entity.player.Player.BedSleepingProblem.OTHER_PROBLEM);
            p.displayClientMessage(Component.literal("You can't sleep with men in suits outside.").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!CompanyAttention.noticeable(p) || current(p) != null || CompanyAttention.band(p) != CompanyAttention.Band.HUNTED
                || level.getServer().overworld().getGameTime() < CompanyAttention.file(p).nextOp) return;
        Raid raid = Raid.start(level, p, bed, null);
        if (raid == null) return;
        event.setProblem(net.minecraft.world.entity.player.Player.BedSleepingProblem.OTHER_PROBLEM);
        p.sendSystemMessage(Component.literal("As you lie down, you hear it: someone outside, moving quietly.")
                .withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));
    }

    /** A convoy whose agent is hurt by a player is alarmed, all of it at once. */
    @SubscribeEvent
    public static void hurt(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof CompanyAgentEntity a) || !(event.getSource().getEntity() instanceof ServerPlayer p)) return;
        if (of(a) instanceof Convoy c) c.hurt(p);
    }

    @SubscribeEvent
    public static void stop(ServerStoppingEvent event) { clear(); }

    public static void clear() {
        PREPARING.clear();
        OPS.clear();
        PENDING.clear();
        LIVE.clear();
    }

    /** An agent walks off: gone (he leaves the way he came, out of sight). */
    static void leave(CompanyAgentEntity a) {
        LIVE.remove(a.getUUID());
        if (!a.isRemoved()) a.discard();
    }

    /** Leaves {@code stacks} on the ground at {@code at}. */
    static void drop(ServerLevel level, Vec3 at, List<ItemStack> stacks) {
        for (ItemStack s : stacks) {
            if (s.isEmpty()) continue;
            ItemEntity item = new ItemEntity(level, at.x, at.y + 0.3, at.z, s);
            item.setDefaultPickUpDelay();
            level.addFreshEntity(item);
        }
    }

    /**
     * Somewhere to stand near {@code (x, z)}, close to height {@code nearY}: down from a little above it to the first
     * floor with headroom, or up out of the ground if that is solid. Unloaded ground answers {@code nearY}.
     */
    static Vec3 ground(ServerLevel level, double x, double z, double nearY) {
        BlockPos column = BlockPos.containing(x, nearY, z);
        if (!level.hasChunkAt(column)) return new Vec3(x, nearY, z);
        int y0 = (int) Math.floor(nearY) + 2;
        if (open(level, column.atY(y0)) && open(level, column.atY(y0 + 1))) {
            for (int y = y0; y > y0 - 32; y--) if (stand(level, column.atY(y))) return new Vec3(column.getX() + .5, y, column.getZ() + .5);
        } else {
            for (int y = y0; y < y0 + 32; y++) if (stand(level, column.atY(y))) return new Vec3(column.getX() + .5, y, column.getZ() + .5);
        }
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
        return new Vec3(column.getX() + .5, top, column.getZ() + .5);
    }

    private static boolean open(ServerLevel level, BlockPos p) {
        var s = level.getBlockState(p);
        return s.getCollisionShape(level, p).isEmpty() && s.getFluidState().isEmpty();
    }

    private static boolean stand(ServerLevel level, BlockPos p) {
        return open(level, p) && open(level, p.above()) && !level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty();
    }

    /** A point {@code distance} from {@code from} along {@code yaw} (radians), on the ground there. */
    static Vec3 out(ServerLevel level, Vec3 from, double angle, double distance) {
        return ground(level, from.x + Math.cos(angle) * distance, from.z + Math.sin(angle) * distance, from.y);
    }

    /** Facing (degrees) from {@code a} toward {@code b}. */
    static float yaw(Vec3 a, Vec3 b) { return (float) (Math.toDegrees(Math.atan2(b.z - a.z, b.x - a.x)) - 90); }
}
