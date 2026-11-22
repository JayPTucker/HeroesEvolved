package com.jayptucker.heroesevolved.trace;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.combat.FightingStyles;
import com.jayptucker.heroesevolved.company.CompanyAttention;
import com.jayptucker.heroesevolved.gifted.Gift;
import com.jayptucker.heroesevolved.radio.BandChatter;
import com.jayptucker.heroesevolved.village.Days;
import com.jayptucker.heroesevolved.village.VillageMemory;
import com.jayptucker.heroesevolved.village.Villages;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Powers leave marks: where someone used a power hard, the place remembers it for a few days (a faint scorch, frost,
 * static, a hum), drawn as motes for players near and fading as it ages. Anyone can tell what a mark is by crouching by
 * it; a telepath, viber, empath or time-bender reads whose it was and when. The Company finds unseen marks near villages
 * ({@link #sweep}), and a hostile gifted mob on the move leaves a trail ({@link #trail}). Saved per level. What the player
 * sees: {@code docs/power-traces.md}.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class Traces extends SavedData {
    static final String NAME = HeroesEvolved.MOD_ID + "_traces";
    /** A move this dear leaves a strong mark, this dear a faint one. */
    public static final int STRONG = 30, FAINT = 15;
    /** How long marks last (calendar ticks): strong, faint, a trail. */
    public static final long STRONG_LIFE = 3 * Days.DAY, FAINT_LIFE = Days.DAY, TRAIL_LIFE = Days.DAY;
    /** Using a power within this of an old mark of yours freshens it; a trail marks every this many blocks. */
    public static final double MERGE = 5, TRAIL_STEP = 6;
    /** Seen from this far; readers from this far; read by crouching within this; noticed by readers within this. */
    public static final double SEEN = 24, READER_SEEN = 48, READ = 3, NOTICE = 16;
    /** The Company finds unseen marks within this of a village: so much attention each, at most so much a day. */
    public static final int VILLAGE = 64;
    public static final float FOUND = 2, FOUND_MOST = 6;
    private static final int MOST = 600, DRAW_EVERY = 20;

    public enum Kind {
        SCORCH("fire", "The ground here is scorched in a ring"), FROST("ice", "There's frost here that shouldn't be"),
        STATIC("lightning", "The air here prickles with static"), DAMP("water", "Everything here is damp, as if after a storm"),
        CRACK("earth", "The ground here is cracked, as if something struck it"), GLOW("radiation", "Something here gives off a faint green light"),
        WIND("wind", "The grass here is flattened in a swirl"), HUM("a power", "The air here hums, very faintly");

        public final String noun, plain;
        final Vector3f colour;
        Kind(String noun, String plain) {
            this.noun = noun;
            this.plain = plain;
            this.colour = switch (noun) {
                case "fire" -> new Vector3f(1F, 0.45F, 0.1F);
                case "ice" -> new Vector3f(0.75F, 0.92F, 1F);
                case "lightning" -> new Vector3f(0.55F, 0.8F, 1F);
                case "water" -> new Vector3f(0.25F, 0.5F, 0.95F);
                case "earth" -> new Vector3f(0.6F, 0.48F, 0.32F);
                case "radiation" -> new Vector3f(0.45F, 1F, 0.35F);
                case "wind" -> new Vector3f(0.92F, 0.94F, 0.9F);
                default -> new Vector3f(0.72F, 0.5F, 1F);
            };
        }

        public static Kind of(ResourceLocation power) {
            if (power == null) return HUM;
            String p = power.getPath();
            if (p.contains("pyro")) return SCORCH;
            if (p.contains("cryo")) return FROST;
            if (p.contains("electro") || p.contains("techno")) return STATIC;
            if (p.contains("hydro")) return DAMP;
            if (p.contains("quake") || p.contains("phasing")) return CRACK;
            if (p.contains("radiation")) return GLOW;
            if (p.contains("flight") || p.contains("speed")) return WIND;
            return HUM;
        }
    }

    /** One mark. */
    public static final class Trace {
        final int id;
        final Kind kind;
        BlockPos at;
        final UUID owner;
        final String name;
        final boolean player;
        float strength;
        long made, life, wear;
        boolean seen, found;
        /** The way a trail went (degrees, compass), or NaN. */
        float heading = Float.NaN;

        Trace(int id, Kind kind, BlockPos at, UUID owner, String name, boolean player) {
            this.id = id; this.kind = kind; this.at = at; this.owner = owner; this.name = name; this.player = player;
        }

        public Kind kind() { return kind; }
        public BlockPos at() { return at; }
        public UUID owner() { return owner; }
        public String name() { return name; }
        public boolean seen() { return seen; }
        public boolean trail() { return !Float.isNaN(heading); }
        /** Calendar ticks since it was made (rain counts double). */
        public long age(long now) { return Math.max(0, now - made) + wear; }
        /** How strong it is now: its first strength, fading to nothing over its life. */
        public float strength(long now) { return Math.max(0, strength * (1 - age(now) / (float) life)); }
    }

    private final List<Trace> traces = new ArrayList<>();
    private int sequence;
    private long sweptDay = -1;
    private static final Set<String> READ_BY = new HashSet<>(), NOTICED_BY = new HashSet<>();
    private static final Map<UUID, BlockPos> LAST_TRAIL = new HashMap<>();

    public static Traces get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(Traces::new, Traces::load), NAME);
    }

    public static List<Trace> all(ServerLevel level) { return List.copyOf(get(level).traces); }

    // ------------------------------------------------------------------
    // Leaving them
    // ------------------------------------------------------------------

    /** {@code p} just used {@code action} for {@code cost} stamina (from {@code AbilityUseService}): a mark, if it was dear. */
    public static Trace left(ServerPlayer p, String action, int cost) {
        if (cost < FAINT || p.isSpectator() || p.isCreative()) return null;
        ResourceLocation power = PlayerAbilityService.getData(p).assignedPower().map(e -> e.getKey()).orElse(null);
        boolean strong = cost >= STRONG;
        boolean seen = CompanyAttention.witnesses(p) > 0;
        Trace t = leave(p.serverLevel(), Kind.of(power), p.blockPosition(), p.getUUID(), p.getName().getString(), true,
                strong ? 1 : 0.5F, strong ? STRONG_LIFE : FAINT_LIFE, seen);
        // Marks near a village that nobody saw get talked about.
        if (strong && !seen && Villages.hasBell(p.serverLevel(), p.blockPosition())) {
            String place = VillageMemory.name(p.serverLevel(), p.blockPosition());
            BandChatter.news(p.serverLevel(), p.blockPosition(), rumour(t.kind) + (place == null ? "" : " out past " + place)
                    + " again. Nobody saw who.", (int) Days.DAY);
        }
        return t;
    }

    /** A mark of {@code kind} at {@code at}: a fresh one, or an old one of theirs nearby made fresh. */
    public static Trace leave(ServerLevel level, Kind kind, BlockPos at, UUID owner, String name, boolean player, float strength,
                              long life, boolean seen) {
        Traces data = get(level);
        long now = Days.clock(level);
        for (Trace t : data.traces)
            if (t.owner.equals(owner) && t.kind == kind && !t.trail() && t.at.distSqr(at) <= MERGE * MERGE) {
                t.strength = Math.max(t.strength(now), strength);
                t.made = now;
                t.wear = 0;
                t.life = Math.max(t.life, life);
                t.seen = seen;
                t.found = false;
                data.setDirty();
                return t;
            }
        Trace t = new Trace(++data.sequence, kind, at.immutable(), owner, name, player);
        t.strength = strength;
        t.made = now;
        t.life = life;
        t.seen = seen;
        data.traces.add(t);
        while (data.traces.size() > MOST) data.traces.removeFirst();
        data.setDirty();
        return t;
    }

    /** From {@code GiftedMob}: a hostile gifted mob on the move marks its way every {@link #TRAIL_STEP} blocks. */
    public static void trail(Mob mob, Gift gift) {
        if (!(mob.level() instanceof ServerLevel level) || !gift.evil()) return;
        BlockPos here = mob.blockPosition(), last = LAST_TRAIL.get(mob.getUUID());
        if (last != null && last.distSqr(here) < TRAIL_STEP * TRAIL_STEP) return;
        LAST_TRAIL.put(mob.getUUID(), here);
        Trace t = leave(level, Kind.of(gift.power()), here, UUID.randomUUID(), gift.name(), false, 0.7F, TRAIL_LIFE, false);
        if (last != null) t.heading = (float) Math.toDegrees(Math.atan2(here.getX() - last.getX(), -(here.getZ() - last.getZ())));
        else t.heading = 0;
        get(level).setDirty();
    }

    // ------------------------------------------------------------------
    // Seeing and reading them
    // ------------------------------------------------------------------

    /** Telepaths, vibers, empaths and time-benders read marks: whose, and when. */
    public static boolean reader(ServerPlayer p) {
        return FightingStyles.hasPower(p, ModAbilities.TELEPATHY_ID) || FightingStyles.hasPower(p, ModAbilities.VIBE_ID)
                || FightingStyles.hasPower(p, ModAbilities.MIMICRY_ID) || FightingStyles.hasPower(p, ModAbilities.TIME_MANIPULATION_ID);
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long tick = server.overworld().getGameTime();
        if (tick % 10 != 0) return;
        for (ServerLevel level : server.getAllLevels()) {
            Traces data = level.getDataStorage().get(new Factory<>(Traces::new, Traces::load), NAME);
            if (data == null || data.traces.isEmpty()) continue;
            long now = Days.clock(level);
            if (tick % 100 == 0) {
                // Fade: gone at the end of their life; rain washes the ones it falls on twice as fast.
                for (Trace t : List.copyOf(data.traces)) {
                    if (level.hasChunkAt(t.at) && level.isRainingAt(t.at.above())) t.wear += 100;
                    if (t.strength(now) <= 0.02F) data.traces.remove(t);
                }
                data.setDirty();
            }
            for (ServerPlayer p : level.players()) {
                if (p.isSpectator()) continue;
                boolean reader = reader(p);
                if (tick % DRAW_EVERY == 0) draw(level, data, p, reader, now);
                read(level, data, p, reader, now);
            }
        }
        if (tick % 200 == 0) sweep(server);
    }

    /** The motes of every mark near {@code p}, for them alone (a reader sees further and more). */
    static void draw(ServerLevel level, Traces data, ServerPlayer p, boolean reader, long now) {
        double reach = reader ? READER_SEEN : SEEN;
        for (Trace t : data.traces) {
            if (t.at.distSqr(p.blockPosition()) > reach * reach || !level.hasChunkAt(t.at)) continue;
            float s = t.strength(now);
            int n = Math.max(1, Math.round(s * (reader ? 6 : 3)));
            Vec3 c = Vec3.atBottomCenterOf(t.at).add(0, 0.3, 0);
            level.sendParticles(p, particle(t.kind), true, c.x, c.y, c.z, n, 0.9, 0.3, 0.9, t.kind == Kind.WIND ? 0.02 : 0.005);
            // And a mote of its colour that lingers, so a mark (or a trail) can be seen from a little way off.
            level.sendParticles(p, new DustParticleOptions(t.kind.colour, 1.1F), true, c.x, c.y, c.z, Math.max(1, n / 2), 0.7, 0.2, 0.7, 0);
        }
    }

    static ParticleOptions particle(Kind kind) {
        return switch (kind) {
            case SCORCH -> ParticleTypes.SMOKE;
            case FROST -> ParticleTypes.SNOWFLAKE;
            case STATIC -> ParticleTypes.ELECTRIC_SPARK;
            case DAMP -> ParticleTypes.FALLING_WATER;
            case CRACK -> ParticleTypes.ASH;
            case GLOW -> new DustParticleOptions(new Vector3f(0.45F, 1F, 0.35F), 0.9F);
            case WIND -> ParticleTypes.POOF;
            case HUM -> new DustParticleOptions(new Vector3f(0.7F, 0.5F, 1F), 0.8F);
        };
    }

    /** Crouching by a mark reads it; a reader passing near a fresh one notices it. */
    static void read(ServerLevel level, Traces data, ServerPlayer p, boolean reader, long now) {
        Trace nearest = null;
        double best = Double.MAX_VALUE;
        // Trace ids are local to each level. A reading power also reveals more than an earlier ordinary reading.
        String viewer = p.getUUID() + ":" + level.dimension().location();
        for (Trace t : data.traces) {
            double d = t.at.distSqr(p.blockPosition());
            // A trail is noticed once for whoever left it, not once a mark.
            if (reader && d <= NOTICE * NOTICE && t.strength(now) >= 0.5F && t.age(now) < 6000 && !t.owner.equals(p.getUUID())
                    && NOTICED_BY.add(viewer + ":" + (t.trail() ? "trail:" + t.name : String.valueOf(t.id))))
                p.sendSystemMessage(Component.literal(t.trail() ? t.name + " passed near here not long ago."
                        : "Someone used " + t.kind.noun + " near here, not long ago.").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
            if (d < best) { best = d; nearest = t; }
        }
        if (nearest == null || !p.isShiftKeyDown() || best > READ * READ) return;
        if (!READ_BY.add(viewer + ":" + nearest.id + ":" + nearest.made + ":" + reader)) return;
        p.sendSystemMessage(Component.literal(describe(nearest, reader, now)).withStyle(reader ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY));
    }

    /** What reading {@code t} tells: what and how old to anyone; a reader, whose and when. */
    public static String describe(Trace t, boolean reader, long now) {
        long age = t.age(now);
        if (!reader) return t.kind.plain + ". " + plainAge(age);
        if (t.trail()) return t.name + " passed this way, heading " + compass(t.heading) + ", " + ago(age) + ".";
        String what = Character.toUpperCase(t.kind.noun.charAt(0)) + t.kind.noun.substring(1);
        return what + " was used here " + ago(age) + "." + (t.player ? " It felt like " + t.name + "." : "");
    }

    static String plainAge(long age) {
        if (age < 1000) return "Fresh.";
        if (age < 6000) return "A few hours old.";
        if (age < 14_000) return "Half a day old, maybe.";
        if (age < 36_000) return "A day old, maybe.";
        return "Days old.";
    }

    static String ago(long age) {
        if (age < 600) return "just now";
        if (age < 2000) return "a little while ago";
        if (age < 24_000) {
            long hours = Math.max(1, Math.round(age / 1000.0));
            return "about " + (hours == 1 ? "an hour" : hours + " hours") + " ago";
        }
        return age < 48_000 ? "about a day ago" : "days ago";
    }

    static String compass(float degrees) {
        String[] points = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};
        return points[(int) Math.floorMod(Math.round(degrees / 45), 8)];
    }

    static String rumour(Kind kind) {
        return switch (kind) {
            case SCORCH -> "Scorch marks";
            case FROST -> "Frost in the middle of the day";
            case STATIC -> "The air crackling";
            case DAMP -> "A field soaked with no rain";
            case CRACK -> "The ground split open";
            case GLOW -> "Something glowing green";
            case WIND -> "The grass flattened in rings";
            case HUM -> "A humming in the air";
        };
    }

    // ------------------------------------------------------------------
    // The Company reads them too
    // ------------------------------------------------------------------

    /**
     * Once a calendar day: every strong mark a player left near a village where nobody saw, from the last day, is found:
     * {@link #FOUND} attention each, up to {@link #FOUND_MOST}.
     */
    static void sweep(MinecraftServer server) {
        ServerLevel level = server.overworld();
        Traces data = level.getDataStorage().get(new Factory<>(Traces::new, Traces::load), NAME);
        if (data == null) return;
        long day = level.getDayTime() / Days.DAY;
        if (day == data.sweptDay) return;
        data.sweptDay = day;
        data.setDirty();
        sweepNow(level);
    }

    /** The sweep now, whatever the day (tests). Returns the attention found in all. */
    public static float sweepNow(ServerLevel level) {
        Traces data = get(level);
        long now = Days.clock(level);
        float total = 0;
        for (ServerPlayer p : level.players()) {
            float found = 0;
            for (Trace t : data.traces) {
                if (found >= FOUND_MOST) break;
                if (!t.player || t.found || t.seen || !t.owner.equals(p.getUUID()) || t.strength <= 0.5F
                        || t.age(now) > Days.DAY || t.strength(now) < 0.5F) continue;
                if (!Villages.hasBell(level, t.at)) continue;
                t.found = true;
                found += FOUND;
            }
            if (found > 0) {
                total += CompanyAttention.raise(p, found, null, "traces found near a village");
                data.setDirty();
            }
        }
        return total;
    }

    /** Whether {@code owner} left a mark at least {@code strength} strong within {@code r} of {@code at}. */
    public static boolean fresh(ServerLevel level, UUID owner, BlockPos at, double r, float strength) {
        long now = Days.clock(level);
        for (Trace t : get(level).traces)
            if (t.owner.equals(owner) && t.at.distSqr(at) <= r * r && t.strength(now) >= strength) return true;
        return false;
    }

    /** Clears every mark within {@code r} of {@code at} (admin, tests). How many. */
    public static int clear(ServerLevel level, BlockPos at, double r) {
        Traces data = get(level);
        int before = data.traces.size();
        data.traces.removeIf(t -> t.at.distSqr(at) <= r * r);
        data.setDirty();
        return before - data.traces.size();
    }

    @SubscribeEvent
    public static void stop(ServerStoppingEvent event) {
        READ_BY.clear();
        NOTICED_BY.clear();
        LAST_TRAIL.clear();
    }

    // ------------------------------------------------------------------
    // Saving
    // ------------------------------------------------------------------

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Trace t : traces) {
            CompoundTag c = new CompoundTag();
            c.putInt("id", t.id);
            c.putString("kind", t.kind.name());
            c.putLong("at", t.at.asLong());
            c.putUUID("owner", t.owner);
            c.putString("name", t.name);
            c.putBoolean("player", t.player);
            c.putFloat("strength", t.strength);
            c.putLong("made", t.made);
            c.putLong("life", t.life);
            c.putLong("wear", t.wear);
            c.putBoolean("seen", t.seen);
            c.putBoolean("found", t.found);
            c.putFloat("heading", t.heading);
            list.add(c);
        }
        tag.put("traces", list);
        tag.putInt("sequence", sequence);
        tag.putLong("swept_day", sweptDay);
        return tag;
    }

    static Traces load(CompoundTag tag, HolderLookup.Provider registries) {
        Traces data = new Traces();
        data.sequence = tag.getInt("sequence");
        data.sweptDay = tag.contains("swept_day") ? tag.getLong("swept_day") : -1;
        for (Tag e : tag.getList("traces", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) e;
            Kind kind;
            try { kind = Kind.valueOf(c.getString("kind")); } catch (IllegalArgumentException x) { continue; }
            Trace t = new Trace(c.getInt("id"), kind, BlockPos.of(c.getLong("at")), c.getUUID("owner"), c.getString("name"), c.getBoolean("player"));
            t.strength = c.getFloat("strength");
            t.made = c.getLong("made");
            t.life = Math.max(1, c.getLong("life"));
            t.wear = c.getLong("wear");
            t.seen = c.getBoolean("seen");
            t.found = c.getBoolean("found");
            t.heading = c.getFloat("heading");
            data.traces.add(t);
        }
        return data;
    }
}
