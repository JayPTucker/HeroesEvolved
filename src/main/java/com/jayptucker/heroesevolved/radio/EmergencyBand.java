package com.jayptucker.heroesevolved.radio;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.config.HeroesEvolvedConfig;
import com.jayptucker.heroesevolved.network.DistressPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * The emergency band: villages in trouble call for help on {@link #FREQUENCY} FM. A call goes out from a point (a
 * village's bell) as radio static with an SOS tone in it, through Simple Radio to every radio tuned to the band, and its
 * words come up as a subtitle for whoever is listening, with which way and how far the call is.
 * <p>
 * Calls carry a long way: everyone within {@link #range()} blocks (768 by default) hears that a village is calling when
 * it starts, with a line in chat and a far-off bell, is reminded every thirty seconds which way it is, and sees a flare
 * burning over it from far off ({@link DistressPayload}, drawn by the client's {@code ClientDistress}). A radio tuned to
 * the band gets the words every three seconds, anywhere in the same world. Village events keep their village loaded
 * while they last ({@code village/VillageTickets}), so a call from hundreds of blocks away is still in trouble when help
 * arrives.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class EmergencyBand {
    /** The emergency band, in Simple Radio's format (three whole places, two decimal). Tune a radio to 101.10 FM. */
    public static final String FREQUENCY = "101.10";
    /** Subtitles reach radio listeners this often, and everyone else in range a reminder this often. */
    static final int SUBTITLE_EVERY = 60, REMINDER_EVERY = 600, FLARE_EVERY = 100;
    /** Closer than this, a player can see what is happening: no reminders. */
    static final int NEAR = 96;
    static final int SAMPLE_RATE = 48000, SAMPLES_PER_TICK = SAMPLE_RATE / 20;

    /** What kind of trouble: the flare's colour on the client (by ordinal; append new kinds at the end). */
    public enum Trouble { OTHER, MELTDOWN, RENEGADE, RAID, WILDFIRE, SUPERCELL, BLIZZARD, METEOR, COMPANY }

    /** One call going out. */
    public static final class Call {
        final UUID id;
        final ServerLevel level;
        final BlockPos at;
        final Trouble trouble;
        Component words;
        final long until;
        final Object transmitter;
        long sample;
        final Random noise;
        float low;
        final Set<UUID> told = new HashSet<>();
        /** The village calling, by name (village/VillageMemory), or null where there is no village. */
        final String place;
        Call(UUID id, ServerLevel level, BlockPos at, Trouble trouble, Component words, long until, Object transmitter) {
            this.id = id; this.level = level; this.at = at; this.trouble = trouble; this.words = words; this.until = until;
            this.transmitter = transmitter;
            this.noise = new Random(id.getLeastSignificantBits());
            this.place = com.jayptucker.heroesevolved.village.VillageMemory.name(level, at);
        }
        public String place() { return place; }
        public UUID id() { return id; }
        public BlockPos at() { return at; }
        public Trouble trouble() { return trouble; }
        /** Changes what the caller is saying (the countdown, say). */
        public void say(Component words) { this.words = words; }
    }

    private static final List<Call> CALLS = new ArrayList<>();

    private EmergencyBand() {}

    /** How far a call carries without a radio. */
    public static int range() { return HeroesEvolvedConfig.COMMON.distressRange.get(); }

    /** Starts a call from {@code at} saying {@code words}, for {@code ticks} ticks. */
    public static Call call(ServerLevel level, BlockPos at, Component words, int ticks) {
        return call(level, at, Trouble.OTHER, words, ticks);
    }

    public static Call call(ServerLevel level, BlockPos at, Trouble trouble, Component words, int ticks) {
        UUID id = UUID.randomUUID();
        Call call = new Call(id, level, at, trouble, words, level.getGameTime() + ticks, RadioLink.open(level, at, id, FREQUENCY));
        CALLS.add(call);
        subtitles(call);
        announce(call);
        flare(call, true);
        return call;
    }

    /** Ends a call now. */
    public static void end(Call call) {
        if (!CALLS.remove(call)) return;
        RadioLink.close(FREQUENCY, call.id);
        flare(call, false);
    }

    public static boolean live(Call call) { return CALLS.contains(call); }

    /** A walkie-talkie tuned to the emergency band, or an empty stack if the radio mod can't make one. */
    public static net.minecraft.world.item.ItemStack radio() { return RadioLink.tuned(FREQUENCY); }

    /** Calls on the air now. */
    public static int calls() { return CALLS.size(); }

    public static List<Call> all() { return List.copyOf(CALLS); }

    /** Is the radio network reachable (so calls are heard as sound on tuned radios, not only as subtitles)? */
    public static boolean onAir() { return RadioLink.available(); }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) { tick(event.getServer()); }

    static void tick(MinecraftServer server) {
        for (Call c : List.copyOf(CALLS)) {
            long now = c.level.getGameTime();
            if (now >= c.until || c.level.getServer() != server) { end(c); continue; }
            if (c.transmitter != null) RadioLink.push(c.transmitter, audio(c, SAMPLES_PER_TICK));
            if (now % SUBTITLE_EVERY == 0) subtitles(c);
            if (now % FLARE_EVERY == 0) { flare(c, true); announce(c); }
            if (now % REMINDER_EVERY == 0) remind(c);
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

    /** Everyone listening on a radio hears the words, with which way and how far the call is. */
    private static void subtitles(Call c) {
        Set<UUID> told = new HashSet<>();
        for (RadioLink.Listening l : RadioLink.listeners(c.level, FREQUENCY)) {
            if (l.holder() instanceof ServerPlayer p) tell(p, c, told);
            else for (Player p : c.level.players())
                if (p instanceof ServerPlayer sp && sp.position().distanceToSqr(l.at()) <= 8 * 8) tell(sp, c, told);
        }
        if (!RadioLink.available())
            for (ServerPlayer sp : c.level.players())
                if (sp.blockPosition().distSqr(c.at) <= (long) NEAR * NEAR) tell(sp, c, told);
    }

    private static void tell(ServerPlayer p, Call c, Set<UUID> told) {
        if (!told.add(p.getUUID())) return;
        p.displayClientMessage(Component.literal("📻 " + FREQUENCY + " FM  " + (c.place == null ? "" : c.place + ": ")).withStyle(ChatFormatting.DARK_AQUA)
                .append(c.words.copy().withStyle(ChatFormatting.WHITE))
                .append(Component.literal("  (" + where(p, c) + ")").withStyle(ChatFormatting.GRAY)), true);
    }

    /** "north-east, 212 blocks" from {@code p} to the call. */
    static String where(ServerPlayer p, Call c) {
        Vec3 to = Vec3.atCenterOf(c.at).subtract(p.position());
        return bearing(to) + ", " + Math.round(to.horizontalDistance()) + " blocks";
    }

    /** Whether someone at {@code at} is within this call's reach without a radio. */
    public static boolean reaches(Call c, Vec3 at) {
        double range = range(), dx = at.x - (c.at.getX() + .5), dz = at.z - (c.at.getZ() + .5);
        return dx * dx + dz * dz <= range * range;
    }

    /** Players in this call's reach (radio or not). */
    static List<ServerPlayer> inRange(Call c) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : c.level.players())
            if (!p.isSpectator() && reaches(c, p.position())) out.add(p);
        return out;
    }

    /**
     * Everyone in reach who hasn't heard this call yet hears it start: a line in chat with which way and how far, and a
     * bell tolling faintly from that direction. Players who ride into reach later hear it then.
     */
    private static void announce(Call c) {
        for (ServerPlayer p : inRange(c)) {
            if (!c.told.add(p.getUUID())) continue;
            p.sendSystemMessage(Component.literal("📻 Distress call" + (c.place == null ? "" : " from " + c.place) + ": ").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                    .append(c.words.copy().withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("  (" + where(p, c) + "). Follow the flare, or tune a radio to " + FREQUENCY + " FM.")
                            .withStyle(ChatFormatting.GRAY)));
            Vec3 toward = Vec3.atCenterOf(c.at).subtract(p.position());
            Vec3 at = toward.lengthSqr() > 1 ? p.position().add(toward.normalize().scale(12)) : p.position();
            p.connection.send(new ClientboundSoundPacket(net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.BELL_BLOCK), SoundSource.AMBIENT, at.x, at.y, at.z, .7F, .8F, p.getRandom().nextLong()));
        }
    }

    /** Every thirty seconds, those in reach without a radio are reminded which way it is. */
    private static void remind(Call c) {
        Set<UUID> radios = new HashSet<>();
        for (RadioLink.Listening l : RadioLink.listeners(c.level, FREQUENCY)) if (l.holder() != null) radios.add(l.holder().getUUID());
        for (ServerPlayer p : inRange(c))
            if (!radios.contains(p.getUUID()) && p.blockPosition().distSqr(c.at) > (long) NEAR * NEAR)
                p.displayClientMessage(Component.literal("Distress call: ").withStyle(ChatFormatting.RED)
                        .append(Component.literal(where(p, c)).withStyle(ChatFormatting.WHITE)), true);
    }

    /** The flare over the call, for everyone in reach (and its end for everyone in the world). */
    private static void flare(Call c, boolean live) {
        DistressPayload payload = new DistressPayload(c.id, Vec3.atBottomCenterOf(c.at), c.trouble.ordinal(), live);
        for (ServerPlayer p : live ? inRange(c) : c.level.players()) PacketDistributor.sendToPlayer(p, payload);
    }

    /** A compass point for a direction on the ground. */
    static String bearing(Vec3 to) {
        double angle = Math.toDegrees(Math.atan2(to.x, -to.z));
        String[] points = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};
        return points[(int) Math.floorMod(Math.round(angle / 45), 8)];
    }

    /** Players who would hear this call on a radio right now (for tests and the HUD). */
    public static List<ServerPlayer> hearing(Call c) {
        Set<UUID> told = new HashSet<>();
        List<ServerPlayer> out = new ArrayList<>();
        for (RadioLink.Listening l : RadioLink.listeners(c.level, FREQUENCY)) {
            if (l.holder() instanceof ServerPlayer p && told.add(p.getUUID())) out.add(p);
        }
        return out;
    }

    /** Whether {@code p} has been told this call started (tests). */
    public static boolean told(Call c, ServerPlayer p) { return c.told.contains(p.getUUID()); }

    @SubscribeEvent public static void stop(ServerStoppingEvent event) {
        for (Call c : List.copyOf(CALLS)) end(c);
    }
}
