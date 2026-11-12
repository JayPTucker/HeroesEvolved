package com.jayptucker.heroesevolved.radio;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * The emergency band: villages in trouble call for help on {@link #FREQUENCY} FM. A call goes out from a point (a
 * village's bell) as radio static with an SOS tone in it, through Simple Radio to every radio tuned to the band and in
 * range, and its words come up as a subtitle for whoever is listening, with which way and how far the call is.
 * If the radio network can't be reached, players within {@link #FALLBACK_RANGE} blocks get the subtitle alone.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class EmergencyBand {
    /** The emergency band, in Simple Radio's format (three whole places, two decimal). Tune a radio to 101.10 FM. */
    public static final String FREQUENCY = "101.10";
    /** Subtitles reach listeners this often, and players this close when there is no radio network. */
    static final int SUBTITLE_EVERY = 60, FALLBACK_RANGE = 96;
    static final int SAMPLE_RATE = 48000, SAMPLES_PER_TICK = SAMPLE_RATE / 20;

    /** One call going out. */
    public static final class Call {
        final UUID id;
        final ServerLevel level;
        final BlockPos at;
        Component words;
        final long until;
        final Object transmitter;
        long sample;
        final Random noise;
        float low;
        Call(UUID id, ServerLevel level, BlockPos at, Component words, long until, Object transmitter) {
            this.id = id; this.level = level; this.at = at; this.words = words; this.until = until; this.transmitter = transmitter;
            this.noise = new Random(id.getLeastSignificantBits());
        }
        public UUID id() { return id; }
        public BlockPos at() { return at; }
        /** Changes what the caller is saying (the countdown, say). */
        public void say(Component words) { this.words = words; }
    }

    private static final List<Call> CALLS = new ArrayList<>();

    private EmergencyBand() {}

    /** Starts a call from {@code at} saying {@code words}, for {@code ticks} ticks. */
    public static Call call(ServerLevel level, BlockPos at, Component words, int ticks) {
        UUID id = UUID.randomUUID();
        Call call = new Call(id, level, at, words, level.getGameTime() + ticks, RadioLink.open(level, at, id, FREQUENCY));
        CALLS.add(call);
        subtitles(call);
        return call;
    }

    /** Ends a call now. */
    public static void end(Call call) {
        if (CALLS.remove(call)) RadioLink.close(FREQUENCY, call.id);
    }

    public static boolean live(Call call) { return CALLS.contains(call); }

    /** A walkie-talkie tuned to the emergency band, or an empty stack if the radio mod can't make one. */
    public static net.minecraft.world.item.ItemStack radio() { return RadioLink.tuned(FREQUENCY); }

    /** Calls on the air now. */
    public static int calls() { return CALLS.size(); }

    /** Is the radio network reachable (so calls are heard as sound on tuned radios, not only as subtitles)? */
    public static boolean onAir() { return RadioLink.available(); }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) { tick(event.getServer()); }

    static void tick(MinecraftServer server) {
        for (Call c : List.copyOf(CALLS)) {
            long now = c.level.getGameTime();
            if (now >= c.until || c.level.getServer() != server) { end(c); continue; }
            if (c.transmitter != null) RadioLink.push(c.transmitter, audio(c, SAMPLES_PER_TICK));
            if (now % SUBTITLE_EVERY == 0) subtitles(c);
        }
    }

    /**
     * The sound of a call: radio static (noise, softened), a slow wash in and out, and an SOS tone at 820 Hz keyed in it
     * (three short, three long, three short, then a pause).
     */
    static short[] audio(Call c, int count) {
        short[] out = new short[count];
        for (int i = 0; i < count; i++, c.sample++) {
            double t = c.sample / (double) SAMPLE_RATE;
            // Static: white noise through a gentle low-pass, swelling and fading as if the signal drifts.
            float white = (float) (c.noise.nextDouble() * 2 - 1);
            c.low += (white - c.low) * 0.35F;
            double drift = 0.55 + 0.45 * Math.sin(t * 0.7) * Math.sin(t * 1.9 + 1);
            double sample = c.low * 0.22 * drift;
            // SOS: a dot is 0.12 s; dashes are three dots; gaps of a dot between, three between letters, seven after.
            if (keyed(t)) sample += Math.sin(t * Math.PI * 2 * 820) * 0.28;
            out[i] = (short) Mth.clamp((int) (sample * 32767), Short.MIN_VALUE, Short.MAX_VALUE);
        }
        return out;
    }

    private static final double DOT = 0.12;
    /** The SOS pattern in dots: on/off runs. */
    private static final int[] SOS = {1, 1, 1, 1, 1, 3, 3, 1, 3, 1, 3, 3, 1, 1, 1, 1, 1, 7};

    static boolean keyed(double t) {
        int total = 0;
        for (int d : SOS) total += d;
        double unit = (t / DOT) % total;
        boolean on = true;
        for (int d : SOS) {
            if (unit < d) return on;
            unit -= d;
            on = !on;
        }
        return false;
    }

    /** Everyone listening hears the words, with which way and how far the call is. */
    private static void subtitles(Call c) {
        Set<UUID> told = new HashSet<>();
        for (RadioLink.Listening l : RadioLink.listeners(c.level, FREQUENCY)) {
            if (l.holder() instanceof ServerPlayer p) tell(p, c, told);
            else for (Player p : c.level.players())
                if (p instanceof ServerPlayer sp && sp.position().distanceToSqr(l.at()) <= 8 * 8) tell(sp, c, told);
        }
        if (!RadioLink.available())
            for (Player p : c.level.players())
                if (p instanceof ServerPlayer sp && sp.blockPosition().distSqr(c.at) <= (long) FALLBACK_RANGE * FALLBACK_RANGE) tell(sp, c, told);
    }

    private static void tell(ServerPlayer p, Call c, Set<UUID> told) {
        if (!told.add(p.getUUID())) return;
        Vec3 to = Vec3.atCenterOf(c.at).subtract(p.position());
        int distance = (int) Math.round(to.horizontalDistance());
        p.displayClientMessage(Component.literal("📻 " + FREQUENCY + " FM  ").withStyle(ChatFormatting.DARK_AQUA)
                .append(c.words.copy().withStyle(ChatFormatting.WHITE))
                .append(Component.literal("  (" + bearing(to) + ", " + distance + " blocks)").withStyle(ChatFormatting.GRAY)), true);
    }

    /** A compass point for a direction on the ground. */
    static String bearing(Vec3 to) {
        double angle = Math.toDegrees(Math.atan2(to.x, -to.z));
        String[] points = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};
        return points[(int) Math.floorMod(Math.round(angle / 45), 8)];
    }

    /** Players who would hear this call right now (for tests and the HUD). */
    public static List<ServerPlayer> hearing(Call c) {
        Set<UUID> told = new HashSet<>();
        List<ServerPlayer> out = new ArrayList<>();
        for (RadioLink.Listening l : RadioLink.listeners(c.level, FREQUENCY)) {
            if (l.holder() instanceof ServerPlayer p && told.add(p.getUUID())) out.add(p);
        }
        return out;
    }

    @SubscribeEvent public static void stop(ServerStoppingEvent event) {
        for (Call c : List.copyOf(CALLS)) end(c);
    }
}
