package com.jayptucker.heroesevolved.company;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.config.HeroesEvolvedConfig;
import com.jayptucker.heroesevolved.invisibility.InvisibilityService;
import com.jayptucker.heroesevolved.network.CompanyAttentionPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * How closely the Company watches each player: an attention from 0 to 100, saved with the overworld. Big moves that
 * villagers see raise it ({@link #seen}, from every successful activation), and so does ending a village's trouble in
 * front of everyone ({@link #deed}, from {@code VillageMemory.record}); quiet days lower it. {@link CompanyOps} reads
 * it to decide when the Company acts. What the player sees is in {@code docs/company-notices.md}.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class CompanyAttention extends SavedData {
    static final String NAME = "heroesevolved_company_attention";
    public static final float MAX = 100;
    /** Where each band starts. */
    public static final int NOTICED = 25, WATCHED = 50, HUNTED = 80;
    /** A move this dear (stamina, as charged) is a big one, the kind people talk about. */
    public static final int LOUD = 30;
    /** A villager sees you from this far, with a clear line. */
    public static final double SEEN_REACH = 32;
    /** Gains. */
    public static final float HERO = 12, HIT_HERO = 6, PRESENT = 5, AGENT_SAW = 10, FILED = 10, KILLED = 20, FREED = 10;
    /** Losses. */
    public static final float COVERED = 8, WIPED = 10, GOT_AWAY = 15;
    /** What a raid leaves it at. */
    public static final float RAID_BEATEN = 35, RAID_WON = 50, RAID_DAWN = 60;
    /** After a day with nothing seen, it falls this much a day. */
    public static final long QUIET = 24_000;
    public static final float QUIET_FALL = 10;
    private static final int TICK_EVERY = 100;

    public enum Band {
        QUIET("COMPANY"), NOTICED("NOTICED"), WATCHED("WATCHED"), HUNTED("HUNTED");
        public final String label;
        Band(String label) { this.label = label; }
    }

    /** One player's file. */
    public static final class File {
        float attention;
        long lastSeen = -100_000;
        /** Where they were last seen doing something big, and when. */
        BlockPos site;
        long siteAt = -100_000;
        /** No new operation before this game time. */
        long nextOp = -1000;
        /** The band they were last told about. */
        int told;
        /** What was last sent to their HUD. */
        int sentAttention = -1, sentOp = -1;

        public float attention() { return attention; }
        public BlockPos site() { return site; }
        public long siteAt() { return siteAt; }
        public long nextOp() { return nextOp; }
    }

    private final Map<UUID, File> files = new HashMap<>();

    public static CompanyAttention get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(CompanyAttention::new, CompanyAttention::load), NAME);
    }

    public static File file(ServerPlayer p) {
        CompanyAttention data = get(p.server);
        return data.files.computeIfAbsent(p.getUUID(), k -> new File());
    }

    public static boolean enabled() { return HeroesEvolvedConfig.COMMON.companyNotices.get(); }

    public static float of(ServerPlayer p) { return file(p).attention; }

    public static Band band(float attention) {
        return attention >= HUNTED ? Band.HUNTED : attention >= WATCHED ? Band.WATCHED : attention >= NOTICED ? Band.NOTICED : Band.QUIET;
    }

    public static Band band(ServerPlayer p) { return band(of(p)); }

    /** Creative and spectating players, and anyone outside the overworld, are never noticed. */
    static boolean noticeable(ServerPlayer p) {
        return enabled() && !p.isCreative() && !p.isSpectator() && p.level().dimension() == Level.OVERWORLD;
    }

    /** Sets it outright (admin, a raid's end). */
    public static void set(ServerPlayer p, float value) {
        File f = file(p);
        f.attention = Math.clamp(value, 0, MAX);
        // Set outright (an admin, a raid's end): the file is current, so quiet days count from now.
        f.lastSeen = p.server.overworld().getDayTime();
        get(p.server).setDirty();
        changed(p, f);
    }

    /**
     * Raises it by {@code by} (scaled by the config) for something seen at {@code site} (or null). Returns the gain.
     * Raising it to Noticed with a site in a village sends someone to ask questions there ({@link CompanyOps#noticed}).
     */
    public static float raise(ServerPlayer p, float by, BlockPos site, String why) {
        if (by <= 0 || !noticeable(p)) return 0;
        File f = file(p);
        float gain = (float) (by * HeroesEvolvedConfig.COMMON.companyAttentionScale.get());
        f.attention = Math.min(MAX, f.attention + gain);
        long now = p.server.overworld().getGameTime();
        // When they were last seen is on the calendar (village/Days): quiet days are days, slept nights included.
        f.lastSeen = p.server.overworld().getDayTime();
        if (site != null) {
            f.site = site.immutable();
            f.siteAt = now;
        }
        get(p.server).setDirty();
        HeroesEvolved.LOGGER.debug("Company attention on {} +{} ({}): {}", p.getName().getString(), gain, why, f.attention);
        changed(p, f);
        if (site != null) CompanyOps.noticed(p, site);
        return gain;
    }

    /** Lowers it by {@code by} (not scaled: covering for someone is worth the same everywhere). */
    public static void lower(ServerPlayer p, float by) {
        if (by <= 0) return;
        File f = file(p);
        f.attention = Math.max(0, f.attention - by);
        get(p.server).setDirty();
        changed(p, f);
    }

    // ------------------------------------------------------------------
    // What raises it
    // ------------------------------------------------------------------

    /**
     * {@code p} just used {@code action}, costing {@code cost} stamina (from {@code AbilityUseService}, after a successful
     * activation). Every use is news to an operation near them (an agent who sees it, a convoy it alarms); a big one
     * that villagers see raises attention by an eighth of its cost, more for each further witness, up to twice that.
     */
    public static void seen(ServerPlayer p, String action, int cost) {
        if (!noticeable(p)) return;
        boolean agentSaw = CompanyOps.powerUsed(p, cost);
        if (agentSaw) raise(p, AGENT_SAW, null, "an agent saw " + action);
        if (cost < LOUD) return;
        int witnesses = witnesses(p);
        if (witnesses == 0 && !agentSaw) return;
        float gain = cost / 8F * Math.min(2F, 1 + 0.15F * Math.max(0, witnesses - 1));
        raise(p, gain, p.blockPosition(), action + " seen by " + witnesses);
    }

    /** How many villagers see {@code p} right now: awake, within {@link #SEEN_REACH}, with a clear line. */
    public static int witnesses(ServerPlayer p) {
        if (hidden(p)) return 0;
        int n = 0;
        for (Villager v : p.serverLevel().getEntitiesOfClass(Villager.class, p.getBoundingBox().inflate(SEEN_REACH),
                v -> v.isAlive() && !v.isSleeping() && !com.jayptucker.heroesevolved.village.Villagers.inStasis(v)
                        && v.distanceToSqr(p) <= SEEN_REACH * SEEN_REACH))
            if (v.hasLineOfSight(p)) n++;
        return n;
    }

    /** Nobody sees a cloaked or invisible player do anything. */
    public static boolean hidden(ServerPlayer p) { return p.isInvisible() || InvisibilityService.isHidden(p); }

    /**
     * A village's trouble ended ({@code VillageMemory.record}): whoever ended it is talked about, and so is everyone
     * else who was there. Only ends the village saw (saved, or hit and survived) count.
     */
    public static void deed(ServerLevel level, BlockPos bell, ServerPlayer hero, Collection<ServerPlayer> present, boolean saved) {
        if (level.dimension() != Level.OVERWORLD) return;
        if (hero != null) raise(hero, saved ? HERO : HIT_HERO, bell, "ended a village's trouble");
        if (saved) for (ServerPlayer p : present) if (p != hero) raise(p, PRESENT, null, "was there");
    }

    // ------------------------------------------------------------------
    // Telling them
    // ------------------------------------------------------------------

    /** Their band changed or their number did: chat for a new band, and the HUD. */
    static void changed(ServerPlayer p, File f) {
        int band = band(f.attention).ordinal();
        if (band != f.told) {
            String line = band > f.told ? switch (Band.values()[band]) {
                case NOTICED -> "Somewhere, someone has opened a file with your name on it.";
                case WATCHED -> "The same man in a dark suit has been at the edge of town three days running. You're being watched.";
                case HUNTED -> "The Company has stopped watching you. It's coming for you.";
                case QUIET -> null;
            } : band == Band.QUIET.ordinal() ? "The feeling of being watched has faded." : null;
            if (line != null && f.told >= 0) p.sendSystemMessage(Component.literal(line).withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));
            f.told = band;
        }
        sync(p);
    }

    /** Sends {@code p}'s attention, band and operation to their HUD, if any of it changed. */
    public static void sync(ServerPlayer p) {
        File f = file(p);
        int shown = Math.round(f.attention), op = CompanyOps.kind(p).ordinal();
        if (shown == f.sentAttention && op == f.sentOp) return;
        f.sentAttention = shown;
        f.sentOp = op;
        if (p.connection != null) PacketDistributor.sendToPlayer(p, new CompanyAttentionPayload(f.attention, op));
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        File f = file(p);
        f.sentAttention = -1;
        f.told = band(f.attention).ordinal();
        sync(p);
    }

    // ------------------------------------------------------------------
    // What lowers it
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();
        if (now % TICK_EVERY != 0) return;
        CompanyAttention data = server.overworld().getDataStorage().get(new Factory<>(CompanyAttention::new, CompanyAttention::load), NAME);
        if (data == null) return;
        long clock = server.overworld().getDayTime();
        long step = lastClock < 0 ? 0 : clock - lastClock;
        lastClock = clock;
        boolean dirty = false;
        for (File f : data.files.values()) if (decay(f, clock, step)) dirty = true;
        if (dirty) data.setDirty();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            File f = data.files.get(p.getUUID());
            if (f != null) changed(p, f);
        }
    }

    /** The calendar time the last decay pass ran at (-1: none yet this run). */
    private static long lastClock = -1;

    /**
     * Brings {@code f} down for the calendar time {@code step} that has passed up to {@code clock}, counting only what came
     * after its first quiet day. Time set back (an admin) moves "last seen" back with it. True if it changed.
     */
    static boolean decay(File f, long clock, long step) {
        if (step < 0) {
            if (f.lastSeen > clock) { f.lastSeen = clock; return true; }
            return false;
        }
        long quietFrom = f.lastSeen + QUIET;
        if (f.attention <= 0 || clock <= quietFrom || step == 0) return false;
        long counted = Math.min(step, clock - quietFrom);
        f.attention = Math.max(0, f.attention - QUIET_FALL * counted / QUIET);
        return true;
    }

    @SubscribeEvent
    public static void stopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) { lastClock = -1; }

    /** Ages {@code p}'s file by {@code ticks} of quiet (tests). */
    public static void quiet(ServerPlayer p, long ticks) {
        File f = file(p);
        long now = p.server.overworld().getDayTime();
        long quietFor = Math.max(0, ticks - Math.max(0, QUIET - (now - f.lastSeen)));
        f.lastSeen = Math.min(f.lastSeen, now - QUIET);
        f.attention = Math.max(0, f.attention - QUIET_FALL * quietFor / QUIET);
        get(p.server).setDirty();
        changed(p, f);
    }

    /** Forgets {@code p} entirely (admin, tests). */
    public static void forget(ServerPlayer p) {
        get(p.server).files.remove(p.getUUID());
        get(p.server).setDirty();
        sync(p);
    }

    // ------------------------------------------------------------------
    // Saving
    // ------------------------------------------------------------------

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        files.forEach((id, f) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", id);
            t.putFloat("attention", f.attention);
            t.putLong("last_seen", f.lastSeen);
            if (f.site != null) t.putLong("site", f.site.asLong());
            t.putLong("site_at", f.siteAt);
            t.putLong("next_op", f.nextOp);
            list.add(t);
        });
        tag.put("files", list);
        return tag;
    }

    static CompanyAttention load(CompoundTag tag, HolderLookup.Provider registries) {
        CompanyAttention data = new CompanyAttention();
        for (Tag e : tag.getList("files", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) e;
            File f = new File();
            f.attention = t.getFloat("attention");
            f.lastSeen = t.getLong("last_seen");
            if (t.contains("site")) f.site = BlockPos.of(t.getLong("site"));
            f.siteAt = t.getLong("site_at");
            f.nextOp = t.getLong("next_op");
            f.told = band(f.attention).ordinal();
            data.files.put(t.getUUID("id"), f);
        }
        return data;
    }
}
