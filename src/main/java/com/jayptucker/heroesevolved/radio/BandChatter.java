package com.jayptucker.heroesevolved.radio;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.company.CompanyOps;
import com.jayptucker.heroesevolved.company.Convoy;
import com.jayptucker.heroesevolved.company.Investigation;
import com.jayptucker.heroesevolved.company.Raid;
import com.jayptucker.heroesevolved.gifted.GiftedMobs;
import com.jayptucker.heroesevolved.gifted.Renegades;
import com.jayptucker.heroesevolved.village.Favours;
import com.jayptucker.heroesevolved.village.GiftedWork;
import com.jayptucker.heroesevolved.village.VillageMemory;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * The villages talking on the emergency band between calls for help: every minute or two, a village near someone with
 * a radio says something (its news, its neighbours', the weather, small talk) as a line in chat and a murmur of a voice
 * on the radio. Other systems post what happened with {@link #news}, so the band knows; this class picks what is worth
 * saying, newest first. What the player sees: {@code docs/living-radio.md}.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class BandChatter {
    /** Ticks between one beat of chatter and the next, at least and at most. */
    public static final int BEAT_MIN = 1200, BEAT_MAX = 2400;
    /** A village knows what happened within this of its bell; a listener hears villages within the band's range. */
    public static final int KNOWS = 512;
    /** A village whose news is this many days old has stopped talking about it. */
    public static final int FRESH_DAYS = 3;
    private static final int RECENT = 16;

    /** Something that happened, for the villages to talk about until {@code until}. */
    public record News(ServerLevel level, BlockPos at, String line, long until) {}

    /** A line going out on the band, and who says it. */
    public record Said(BlockPos bell, String place, String line) {}

    private static final List<News> NEWS = new ArrayList<>();
    private static final Deque<String> RECENTLY = new ArrayDeque<>();
    private static final List<Voice> VOICES = new ArrayList<>();
    private static long nextBeat = -1;

    private BandChatter() {}

    /** Posts {@code line} as news from {@code at}, talked about for {@code ticks}. */
    public static void news(ServerLevel level, BlockPos at, String line, int ticks) {
        if (level.dimension() != Level.OVERWORLD) return;
        NEWS.removeIf(n -> n.line.equals(line));
        NEWS.add(new News(level, at.immutable(), line, level.getGameTime() + ticks));
        while (NEWS.size() > 64) NEWS.removeFirst();
    }

    public static List<News> news() { return List.copyOf(NEWS); }

    // ------------------------------------------------------------------
    // The beat
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) { tick(event.getServer()); }

    static void tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        long now = level.getGameTime();
        for (Voice v : List.copyOf(VOICES)) if (!v.tick()) VOICES.remove(v);
        if (now % 20 != 0) return;
        NEWS.removeIf(n -> now > n.until);
        if (nextBeat < 0) nextBeat = now + BEAT_MIN;
        if (now < nextBeat) return;
        nextBeat = now + BEAT_MIN + level.random.nextInt(BEAT_MAX - BEAT_MIN + 1);
        beat(level, listeners(level));
    }

    /** Everyone hearing the band now: a radio in hand (its holder), or within 8 blocks of a radio block. */
    public static List<ServerPlayer> listeners(ServerLevel level) {
        Set<UUID> seen = new HashSet<>();
        List<ServerPlayer> out = new ArrayList<>();
        for (RadioLink.Listening l : RadioLink.listeners(level, EmergencyBand.FREQUENCY)) {
            if (l.holder() instanceof ServerPlayer p) { if (seen.add(p.getUUID())) out.add(p); }
            else for (Player p : level.players())
                if (p instanceof ServerPlayer sp && sp.position().distanceToSqr(l.at()) <= 8 * 8 && seen.add(sp.getUUID())) out.add(sp);
        }
        return out;
    }

    /**
     * One beat: each listener not yet served hears a village near them say something, and so does every other listener
     * in that village's reach. Returns what was said.
     */
    public static List<Said> beat(ServerLevel level, List<ServerPlayer> listeners) {
        List<Said> out = new ArrayList<>();
        Set<UUID> served = new HashSet<>();
        for (ServerPlayer p : listeners) {
            if (served.contains(p.getUUID()) || p.level() != level || p.isSpectator()) continue;
            Said said = pick(level, p);
            if (said == null) continue;
            out.add(said);
            remember(said.line);
            Component c = Component.literal("📻 " + EmergencyBand.FREQUENCY + " FM  " + said.place + ": ").withStyle(ChatFormatting.DARK_AQUA)
                    .append(Component.literal(said.line).withStyle(ChatFormatting.WHITE));
            for (ServerPlayer q : listeners)
                if (!served.contains(q.getUUID()) && q.level() == level && hears(q, said.bell)) {
                    served.add(q.getUUID());
                    q.sendSystemMessage(c);
                }
            speak(level, said);
        }
        return out;
    }

    private static boolean hears(ServerPlayer p, BlockPos bell) {
        double r = EmergencyBand.range();
        return p.position().distanceToSqr(Vec3.atCenterOf(bell)) <= r * r;
    }

    private static void remember(String line) {
        RECENTLY.addLast(line);
        while (RECENTLY.size() > RECENT) RECENTLY.removeFirst();
    }

    // ------------------------------------------------------------------
    // What gets said
    // ------------------------------------------------------------------

    record Option(BlockPos bell, String place, String line, int weight) {}

    /** Villages within reach of {@code p}: every remembered one, and the nearest bell to them. */
    static List<BlockPos> villages(ServerLevel level, ServerPlayer p) {
        List<BlockPos> out = new ArrayList<>();
        double r = EmergencyBand.range();
        for (VillageMemory.Village v : VillageMemory.all(level))
            if (p.position().distanceToSqr(Vec3.atCenterOf(v.bell())) <= r * r) out.add(v.bell());
        level.getPoiManager().findClosest(poi -> poi.is(PoiTypes.MEETING), p.blockPosition(), 192, PoiManager.Occupancy.ANY)
                .ifPresent(bell -> { if (!out.contains(bell)) out.add(bell); });
        return out;
    }

    /** What the band says to {@code p} now (and who says it), or null if no village is in reach. */
    public static Said pick(ServerLevel level, ServerPlayer p) {
        List<BlockPos> bells = villages(level, p);
        if (bells.isEmpty()) return null;
        List<Option> options = options(level, bells);
        int total = 0;
        for (Option o : options) total += o.weight;
        if (total <= 0) return null;
        int roll = level.random.nextInt(total);
        for (Option o : options) {
            roll -= o.weight;
            if (roll < 0) return new Said(o.bell, o.place, o.line);
        }
        return null;
    }

    /** Everything the villages round {@code bells} might say now, weighted (news heaviest), minus what was just said. */
    static List<Option> options(ServerLevel level, List<BlockPos> bells) {
        List<Option> all = new ArrayList<>();
        RandomSource r = level.random;
        long day = level.getDayTime() / 24_000 + 1;
        List<VillageMemory.Village> standing = new ArrayList<>(), fallen = new ArrayList<>();
        for (BlockPos b : bells) {
            VillageMemory.Village v = VillageMemory.of(level, b);
            (v.fallen() ? fallen : standing).add(v);
        }
        if (standing.isEmpty()) return all;
        // A live distress call: everyone else talks about it.
        for (EmergencyBand.Call call : EmergencyBand.all()) {
            for (VillageMemory.Village v : standing) {
                if (call.place() != null && call.place().equals(v.name())) continue;
                if (v.bell().distSqr(call.at()) > (long) KNOWS * KNOWS) continue;
                String caller = call.place() == null ? "them" : call.place();
                add(all, v, pick(r, "Anyone near " + caller + "? They need help over there!",
                        "We can see " + caller + "'s flare from here. Somebody go!"), 8);
            }
        }
        // Posted news: said by the village nearest where it happened.
        for (News n : NEWS) {
            VillageMemory.Village speaker = nearest(standing, n.at);
            if (speaker != null && speaker.bell().distSqr(n.at) <= (long) KNOWS * KNOWS) add(all, speaker, n.line, 6);
        }
        for (VillageMemory.Village v : standing) {
            // Their own history, while it's fresh; and their neighbours' heroes.
            for (VillageMemory.Entry e : v.history()) {
                if (day - e.day() > FRESH_DAYS) continue;
                add(all, v, memory(r, v, e), 4);
                VillageMemory.Village other = other(standing, v);
                if (other != null && e.hero() != null && e.outcome() == VillageMemory.Outcome.SAVED)
                    add(all, other, "Heard " + e.hero() + " saved " + v.name() + " from " + e.what() + ".", 2);
            }
            // Gifted neighbours at work, and the favours they want.
            for (Villager g : level.getEntitiesOfClass(Villager.class, new AABB(v.bell()).inflate(64), GiftedMobs::isGood)) {
                GiftedWork.Job job = GiftedWork.job(g);
                if (job == null) continue;
                String name = g.hasCustomName() ? g.getCustomName().getString() : v.name() + "'s " + job.title;
                add(all, v, job.boast.replace("{name}", name), 1);
                Favours.Favour f = Favours.open(g);
                if (f != null) add(all, v, name + " at " + v.name() + " is asking for " + f.describe() + ". Pays in emeralds.", 2);
            }
            // Renegades on the loose near them.
            for (Renegades.Terror t : Renegades.all()) {
                var who = t.renegade();
                if (who == null || who.level() != level || who.blockPosition().distSqr(v.bell()) > 256L * 256) continue;
                add(all, v, "Someone saw " + who.getName().getString() + " " + compass(v.bell(), who.blockPosition()) + " of "
                        + v.name() + ". Keep away.", 3);
            }
            // The weather and the sky.
            if (level.isThundering()) add(all, v, "Storm overhead. Stay off the hills and out from under the trees.", 2);
            else if (level.isRaining()) add(all, v, pick(r, "Rain's set in for the day. Good for the fields, bad for the washing.",
                    "Wet again. The ducks are happy, anyway."), 1);
            if (com.jayptucker.heroesevolved.meteor.Starfall.active(level.getServer()))
                add(all, v, pick(r, "Stars falling over the hills tonight! Stay under a roof.",
                        "Did anyone see that one come down? Lit up the whole sky."), 3);
            if (com.jayptucker.heroesevolved.events.EclipseService.isActive(level.getServer()))
                add(all, v, "The sun's going dark. Keep your gifted close.", 3);
            long t = level.getDayTime() % 24_000;
            if (t >= 12_500 && t < 23_000) add(all, v, pick(r, "Doors locked, everyone. Things walk at night.",
                    "Lanterns lit. Nobody go out past the fields till morning."), 1);
            // Small talk.
            add(all, v, SMALL_TALK[r.nextInt(SMALL_TALK.length)], 1);
        }
        // The Company, while it is about.
        for (CompanyOps.Op op : CompanyOps.all()) {
            if (op.over()) continue;
            switch (op) {
                case Investigation i -> {
                    VillageMemory.Village v = nearest(standing, i.site());
                    if (v != null) add(all, v, "A man in a suit is asking questions here. Polite. Too polite.", 5);
                }
                case Convoy c -> {
                    VillageMemory.Village v = other(standing, VillageMemory.of(level, c.bell()));
                    if (v != null) add(all, v, "Men in suits walking someone down the road out of " + VillageMemory.of(level, c.bell()).name()
                            + ". Anyone near?", 5);
                }
                case Raid raid -> {
                    VillageMemory.Village v = nearest(standing, raid.home());
                    if (v != null && raid.stage() != Raid.Stage.WARNING)
                        add(all, v, "Lights and gunfire out at " + raid.subject().getName().getString() + "'s place. Stay indoors.", 5);
                }
                default -> { }
            }
        }
        // Villages that fell: someone still calls for them.
        for (VillageMemory.Village f : fallen) {
            VillageMemory.Village v = other(standing, f);
            VillageMemory.Entry last = f.history().isEmpty() ? null : f.history().getLast();
            if (v != null) add(all, v, "Still nothing from " + f.name() + (last == null ? "." : ". Not since " + last.what() + "."), 1);
        }
        all.removeIf(o -> RECENTLY.contains(o.line));
        return all;
    }

    private static void add(List<Option> all, VillageMemory.Village v, String line, int weight) {
        if (line != null) all.add(new Option(v.bell(), v.name(), line, weight));
    }

    /** What a village says about something that happened to it. */
    static String memory(RandomSource r, VillageMemory.Village v, VillageMemory.Entry e) {
        boolean company = e.what().equals("the Company");
        return switch (e.outcome()) {
            case SAVED -> company ? (e.hero() != null ? e.hero() + " got our neighbour back from the Company. We owe them."
                    : "Our neighbour's home from the Company. Don't ask how.")
                    : e.hero() != null ? pick(r, v.name() + " here. Still cleaning up after " + e.what() + ". Thanks again, " + e.hero() + ".",
                    "We'd have lost everything to " + e.what() + " if " + e.hero() + " hadn't come.")
                    : v.name() + " came through " + e.what() + ". All of us.";
            case HIT -> company ? "They took one of ours. The Company. Nobody's saying where."
                    : v.name() + " is still counting what " + e.what() + " cost us. " + e.alive() + " of " + e.total() + " of us made it.";
            case FALLEN -> null;
        };
    }

    private static VillageMemory.Village nearest(List<VillageMemory.Village> villages, BlockPos at) {
        VillageMemory.Village best = null;
        for (VillageMemory.Village v : villages) if (best == null || v.bell().distSqr(at) < best.bell().distSqr(at)) best = v;
        return best;
    }

    /** Another standing village (not {@code not}), the nearest to it. */
    private static VillageMemory.Village other(List<VillageMemory.Village> villages, VillageMemory.Village not) {
        VillageMemory.Village best = null;
        for (VillageMemory.Village v : villages)
            if (!v.bell().equals(not.bell()) && (best == null || v.bell().distSqr(not.bell()) < best.bell().distSqr(not.bell()))) best = v;
        return best;
    }

    private static String pick(RandomSource r, String... lines) { return lines[r.nextInt(lines.length)]; }

    static String compass(BlockPos from, BlockPos to) {
        double angle = Math.toDegrees(Math.atan2(to.getX() - from.getX(), -(to.getZ() - from.getZ())));
        String[] points = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};
        return points[(int) Math.floorMod(Math.round(angle / 45), 8)];
    }

    private static final String[] SMALL_TALK = {
            "Wheat's cheap this week, if anyone's buying.",
            "Lost a cow. Brown, answers to nothing. If you see her, she's ours.",
            "Somebody left the gate open again. The sheep are in the beans.",
            "The well's running sweet again after the rain.",
            "Anyone got a spare bucket? Ours has a hole in it.",
            "Harvest's in. Best in years.",
            "The baker burned the bread again. We're eating it anyway.",
            "Quiet day here. Just how we like it.",
            "There's a wandering trader camped by the road. His prices are a robbery.",
            "Kids found a cave past the hill. Nobody go in it.",
            "Saw a fox take a chicken this morning. Bold as anything.",
            "Roof's patched. Took three of us and most of the morning.",
            "Testing, testing. Is this thing on?",
            "Whoever borrowed the cart, we'd like it back.",
            "Bees are swarming by the orchard. Mind yourselves.",
            "Iron golem's been standing in the same spot all day. Thinking, maybe.",
    };

    // ------------------------------------------------------------------
    // How it sounds
    // ------------------------------------------------------------------

    /** Opens a transmitter at the speaker's bell for as long as the line takes, with a voice on it. */
    static void speak(ServerLevel level, Said said) {
        UUID id = UUID.randomUUID();
        Object transmitter = RadioLink.open(level, said.bell, id, EmergencyBand.FREQUENCY);
        if (transmitter == null) return;
        int ticks = Mth.clamp(said.line.length() * 11 / 10, 30, 90);
        VOICES.add(new Voice(id, transmitter, ticks, new Random(id.getMostSignificantBits())));
    }

    /**
     * A distant voice on a radio: a buzz at a speaking pitch through two vowel resonances, broken into syllables and
     * words, band-limited like a telephone, over soft static, with a squelch click at each end. No words: a murmur.
     */
    static final class Voice {
        private static final int RATE = EmergencyBand.SAMPLE_RATE, PER_TICK = EmergencyBand.SAMPLES_PER_TICK;
        private static final double[][] VOWELS = {{730, 1090}, {530, 1840}, {270, 2290}, {570, 840}, {440, 1020}, {300, 870}, {660, 1720}};
        final UUID id;
        final Object transmitter;
        final int samples;
        final Random noise;
        int at;
        double phase, pitch, syllableEnd, f1, f2, envelope, low, high;
        boolean voiced;
        final double[] r1 = new double[2], r2 = new double[2];

        Voice(UUID id, Object transmitter, int ticks, Random noise) {
            this.id = id; this.transmitter = transmitter; this.samples = ticks * PER_TICK; this.noise = noise;
            this.pitch = 105 + noise.nextDouble() * 60;
        }

        /** Sends the next tick of sound; false when done (and closed). */
        boolean tick() {
            if (at >= samples) {
                RadioLink.close(EmergencyBand.FREQUENCY, id);
                return false;
            }
            RadioLink.push(transmitter, next(PER_TICK));
            return true;
        }

        short[] next(int count) {
            short[] out = new short[count];
            for (int i = 0; i < count; i++, at++) {
                double t = at / (double) RATE, end = samples / (double) RATE;
                double s;
                if (t < 0.06 || t > end - 0.06) s = (noise.nextDouble() * 2 - 1) * 0.35;   // squelch
                else {
                    if (t >= syllableEnd) syllable(t, end);
                    double target = voiced ? 1 : 0;
                    envelope += (target - envelope) * 0.004;
                    double source;
                    if (voiced) {
                        double f0 = pitch * (1 + 0.06 * Math.sin(t * 3.1) - 0.08 * (t / end));
                        phase += f0 / RATE;
                        phase -= Math.floor(phase);
                        source = phase * 2 - 1;   // a buzz, as the throat makes
                    } else source = (noise.nextDouble() * 2 - 1) * 0.4;
                    s = (resonate(source, f1, 90, r1) * 0.6 + resonate(source, f2, 120, r2) * 0.4) * envelope * 0.5;
                    s += (noise.nextDouble() * 2 - 1) * 0.04;   // static under it
                }
                // A telephone band: 300 Hz to 3 kHz.
                low += (s - low) * 0.32;
                high += (low - high) * 0.04;
                double band = low - high;
                // Softly limited, so a loud syllable rounds off instead of clipping.
                out[i] = (short) Math.round(Math.tanh(band * 1.2) * 14000);
            }
            return out;
        }

        private void syllable(double t, double end) {
            boolean gap = noise.nextDouble() < 0.18;
            voiced = !gap;
            double[] v = VOWELS[noise.nextInt(VOWELS.length)];
            f1 = v[0] * (0.92 + noise.nextDouble() * 0.16);
            f2 = v[1] * (0.92 + noise.nextDouble() * 0.16);
            syllableEnd = Math.min(end - 0.06, t + (gap ? 0.08 + noise.nextDouble() * 0.08 : 0.09 + noise.nextDouble() * 0.12));
        }

        private static double resonate(double x, double f, double bandwidth, double[] y) {
            double r = Math.exp(-Math.PI * bandwidth / RATE);
            double b1 = 2 * r * Math.cos(2 * Math.PI * f / RATE), b2 = -r * r;
            double out = (1 - r) * x + b1 * y[0] + b2 * y[1];
            y[1] = y[0];
            y[0] = out;
            return out;
        }
    }

    /** The next beat comes in {@code ticks} (tests, previews). */
    public static void soon(MinecraftServer server, int ticks) { nextBeat = server.overworld().getGameTime() + ticks; }

    @SubscribeEvent
    public static void stop(ServerStoppingEvent event) {
        for (Voice v : VOICES) RadioLink.close(EmergencyBand.FREQUENCY, v.id);
        VOICES.clear();
        NEWS.clear();
        RECENTLY.clear();
        nextBeat = -1;
    }
}
