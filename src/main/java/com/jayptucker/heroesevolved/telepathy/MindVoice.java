package com.jayptucker.heroesevolved.telepathy;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.vibe.BreachVoiceRoutes;
import com.jayptucker.heroesevolved.time.TemporalVoice;
import com.jayptucker.heroesevolved.time.TemporalVoiceState;
import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent;
import de.maxhenkel.voicechat.api.events.LocationalSoundPacketEvent;
import de.maxhenkel.voicechat.api.events.StaticSoundPacketEvent;
import de.maxhenkel.voicechat.api.events.SoundPacketEvent;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.PlayerDisconnectedEvent;
import de.maxhenkel.voicechat.api.events.VoiceDistanceEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStoppedEvent;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoderMode;
import de.maxhenkel.voicechat.api.packets.MicrophonePacket;
import de.maxhenkel.voicechat.api.packets.SoundPacket;
import de.maxhenkel.voicechat.api.packets.EntitySoundPacket;
import de.maxhenkel.voicechat.api.packets.LocationalSoundPacket;
import de.maxhenkel.voicechat.api.packets.StaticSoundPacket;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * The one place that speaks to Simple Voice Chat (henkelmax), found by its {@code @ForgeVoicechatPlugin} scan. For every
 * microphone packet it looks up the telepathic routes the server worked out ({@link MindVoiceRoutes}) and sends the voice
 * on inside the listener's head or from a point in the speaker's direction. Public proximity speech can also pass
 * through open Vibe breaches. Each kind of relay uses its own channel and volume category.
 *
 * <p>A voice heard inside the head is decoded, run through {@link PsychicVoice} and encoded again (one codec pair per
 * speaker, so the stream stays continuous), so it sounds like a thought rather than a voice in the room. A telepath
 * speaking with their inner voice has their ordinary broadcast cancelled: only the minds they reach hear them.
 *
 * <p>This runs on the voice chat's thread and reads only the routes' snapshot. Every call is guarded: if the voice chat
 * changes and something here breaks, it logs once and telepathic voices stop, and nothing else does.
 */
@ForgeVoicechatPlugin
public final class MindVoice implements VoicechatPlugin {
    static final String CATEGORY = "telepathy";
    private static final String BREACH_CATEGORY = "breach";
    private static volatile boolean broken;
    private static volatile boolean breachBroken;
    private static final Map<UUID, UUID> FAR_CHANNELS = new ConcurrentHashMap<>(), HEAD_CHANNELS = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> BREACH_CHANNELS = new ConcurrentHashMap<>();
    /** Each speaker's codecs and distortion, kept while they speak into minds. */
    private static final Map<UUID, Head> HEADS = new ConcurrentHashMap<>();
    private static final TemporalAudio TEMPORAL = new TemporalAudio();

    private record Head(OpusDecoder decoder, OpusEncoder encoder, PsychicVoice voice) {
        void close() {
            if (!decoder.isClosed()) decoder.close();
            if (!encoder.isClosed()) encoder.close();
        }
    }

    public MindVoice() {}

    @Override public String getPluginId() { return HeroesEvolved.MOD_ID; }

    @Override public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, this::started);
        registration.registerEvent(VoicechatServerStoppedEvent.class, event -> {
            TEMPORAL.stop();
            TemporalVoiceState.clear();
            MindVoiceRoutes.clear();
            BreachVoiceRoutes.clear();
            BREACH_CHANNELS.clear();
            HEADS.values().forEach(Head::close);
            HEADS.clear();
        });
        registration.registerEvent(PlayerDisconnectedEvent.class, event -> {
            TEMPORAL.forget(event.getPlayerUuid());
            Head head = HEADS.remove(event.getPlayerUuid());
            if (head != null) synchronized (head) { head.close(); }
            BREACH_CHANNELS.remove(event.getPlayerUuid());
        });
        // Ahead of Simple Radio (priority 0): a frozen mouth must never enter a transmitter's buffer.
        registration.registerEvent(MicrophonePacketEvent.class, event -> {
            var sender = event.getSenderConnection();
            if (sender != null && sender.getPlayer() != null && TemporalVoiceState.frozen(sender.getPlayer().getUuid())) event.cancel();
        }, 1000);
        registration.registerEvent(MicrophonePacketEvent.class, this::microphone);
        // These are separate concrete events in SVC, not a polymorphic SoundPacketEvent subscription.
        registration.registerEvent(EntitySoundPacketEvent.class, TEMPORAL::outgoing, -1000);
        registration.registerEvent(LocationalSoundPacketEvent.class, TEMPORAL::outgoing, -1000);
        registration.registerEvent(StaticSoundPacketEvent.class, TEMPORAL::outgoing, -1000);
        registration.registerEvent(VoiceDistanceEvent.class, this::breach, -100);
    }

    private static void fail(String what, Throwable t) {
        if (!broken) HeroesEvolved.LOGGER.warn("Simple Voice Chat link failed ({}); telepaths will no longer hear voices", what, t);
        broken = true;
    }

    private void started(VoicechatServerStartedEvent event) {
        broken = breachBroken = false;
        TEMPORAL.start(event.getVoicechat());
        try {
            VoicechatServerApi api = event.getVoicechat();
            MindVoiceRoutes.voiceDistance(api.getVoiceChatDistance());
            api.registerVolumeCategory(api.volumeCategoryBuilder().setId(CATEGORY).setName("Telepathy")
                    .setDescription("Voices you hear in your head: Far Hearing, Hive Link and Mind Network")
                    .setIcon(icon()).build());
        } catch (Throwable t) {
            fail("start", t);
        }
        try {
            VoicechatServerApi api = event.getVoicechat();
            api.registerVolumeCategory(api.volumeCategoryBuilder().setId(BREACH_CATEGORY).setName("Vibe Breach")
                    .setDescription("Proximity voices through an open breach").build());
        } catch (Throwable t) {
            failBreach(t);
        }
    }

    private void breach(VoiceDistanceEvent event) {
        if (breachBroken || event.isCancelled()) return;
        try {
            VoicechatConnection sender = event.getSenderConnection();
            if (sender == null || sender.getPlayer() == null || sender.isDisabled()) return;
            Group group = sender.getGroup();
            if (group != null && group.getType() != Group.Type.OPEN) return;
            UUID speaker = sender.getPlayer().getUuid();
            if (MindVoiceRoutes.inner(speaker)) return;
            List<BreachVoiceRoutes.Route> routes = BreachVoiceRoutes.routes(speaker);
            if (routes.isEmpty()) return;
            var packet = event.getPacket();
            var voice = event.getVoicechat();
            float range = event.getDistance();
            for (BreachVoiceRoutes.Route route : routes) {
                if (TemporalVoiceState.blocks(speaker, route.listener())) continue;
                if (!route.audible(range)) continue;
                VoicechatConnection to = voice.getConnectionOf(route.listener());
                if (to == null || !to.isConnected() || to.isDisabled()) continue;
                Group theirs = to.getGroup();
                if (theirs != null && (theirs.getType() == Group.Type.ISOLATED
                        || group != null && theirs.getId().equals(group.getId()))) continue;
                if (!broken && MindVoiceRoutes.routes(speaker).stream().anyMatch(mind -> mind.listener().equals(route.listener())
                        && (!packet.isWhispering() || mind.kind() != MindVoiceRoutes.Kind.FAR
                        || mind.range() <= TelepathyGeometry.WHISPER_RANGE))) continue;
                var at = route.position();
                voice.sendLocationalSoundPacketTo(to, packet.locationalSoundPacketBuilder()
                        .channelId(channel(BREACH_CHANNELS, speaker, BREACH_CATEGORY))
                        .position(voice.createPosition(at.x, at.y, at.z)).distance(range).category(BREACH_CATEGORY).build());
            }
        } catch (Throwable t) {
            failBreach(t);
        }
    }

    private static void failBreach(Throwable failure) {
        if (!breachBroken) HeroesEvolved.LOGGER.warn("Simple Voice Chat is unable to relay voices through breaches", failure);
        breachBroken = true;
    }

    private void microphone(MicrophonePacketEvent event) {
        if (broken) return;
        try {
            VoicechatConnection sender = event.getSenderConnection();
            if (sender == null || sender.getPlayer() == null) return;
            UUID speaker = sender.getPlayer().getUuid();
            MindVoiceRoutes.spoke(speaker);
            // The inner voice: nobody hears it the ordinary way, in the room or in a group.
            boolean inner = MindVoiceRoutes.inner(speaker);
            if (inner) event.cancel();
            List<MindVoiceRoutes.Route> routes = MindVoiceRoutes.routes(speaker);
            if (routes.isEmpty()) return;
            VoicechatServerApi voice = event.getVoicechat();
            MicrophonePacket packet = event.getPacket();
            Group group = sender.getGroup();
            // A voice in a closed group is private: telepathy doesn't carry it unless the server says so. The inner voice
            // is the speaker's own choice to send, so it isn't held back.
            boolean closed = !inner && group != null && group.getType() != Group.Type.OPEN && !MindVoiceRoutes.hearsGroups();
            byte[] thought = null;
            for (MindVoiceRoutes.Route route : routes) {
                if (TemporalVoiceState.blocks(speaker, route.listener())) continue;
                VoicechatConnection to = voice.getConnectionOf(route.listener());
                if (to == null || !to.isConnected() || to.isDisabled()) continue;
                Group theirs = to.getGroup();
                if (!inner && group != null && theirs != null && theirs.getId().equals(group.getId())) continue;
                if (closed) continue;
                if (packet.isWhispering() && route.kind() == MindVoiceRoutes.Kind.FAR && route.range() > TelepathyGeometry.WHISPER_RANGE) continue;
                if (route.inHead()) {
                    if (thought == null) thought = distort(voice, speaker, packet.getOpusEncodedData());
                    voice.sendStaticSoundPacketTo(to, packet.staticSoundPacketBuilder().opusEncodedData(thought)
                            .channelId(channel(HEAD_CHANNELS, speaker, "head")).category(CATEGORY).build());
                } else {
                    voice.sendLocationalSoundPacketTo(to, packet.locationalSoundPacketBuilder()
                            .channelId(channel(FAR_CHANNELS, speaker, "far"))
                            .position(voice.createPosition(route.x(), route.y(), route.z()))
                            .distance(route.distance()).category(CATEGORY).build());
                }
                MindVoiceRoutes.heard(route.listener(), speaker);
            }
        } catch (Throwable t) {
            fail("relay", t);
        }
    }

    /**
     * The speaker's frame as a voice inside your head: decoded, distorted and encoded again with the speaker's own codecs.
     * If the codecs can't be made, the voice goes through undistorted rather than not at all.
     */
    private static byte[] distort(VoicechatServerApi voice, UUID speaker, byte[] opus) {
        if (opus == null || opus.length == 0) return opus;
        Head head = HEADS.computeIfAbsent(speaker, s -> new Head(voice.createDecoder(), voice.createEncoder(OpusEncoderMode.VOIP), new PsychicVoice()));
        synchronized (head) {
            if (head.decoder.isClosed() || head.encoder.isClosed()) return opus;
            short[] pcm = head.decoder.decode(opus);
            if (pcm == null || pcm.length == 0) return opus;
            head.voice.process(pcm, System.nanoTime());
            byte[] out = head.encoder.encode(pcm);
            return out == null || out.length == 0 ? opus : out;
        }
    }

    /** A channel of its own for each speaker and way of hearing, so a relayed voice never shares a stream. */
    private static UUID channel(Map<UUID, UUID> channels, UUID speaker, String kind) {
        return channels.computeIfAbsent(speaker, s -> UUID.nameUUIDFromBytes(
                ("heroesevolved:telepathy:" + kind + ":" + s).getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * The final delivery gate covers proximity, groups, telepathy, breaches and Simple Radio's output. A short rolling
     * buffer holds only audio that was actually sent; a single voice worker plays its dying tail even if PTT is released
     * at the instant time changes. No entity or mutable gameplay state is read here.
     */
    private static final class TemporalAudio {
        private static final String CATEGORY = "temporal_echo";
        private record StreamKey(UUID speaker, UUID channel) {}
        private record DeliveryKey(StreamKey stream, UUID listener) {}
        private final Map<StreamKey, HeardStream> streams = new java.util.HashMap<>();
        private final Map<DeliveryKey, Delivery> deliveries = new java.util.HashMap<>();
        private final List<DyingVoice> tails = new ArrayList<>();
        private VoicechatServerApi api;
        private ScheduledExecutorService worker;
        private boolean failed;

        synchronized void start(VoicechatServerApi voice) {
            stop();
            api = voice;
            failed = false;
            try {
                voice.registerVolumeCategory(voice.volumeCategoryBuilder().setId(CATEGORY).setName("Time fading voices")
                        .setDescription("Speech slowing into reverb when time changes").build());
                worker = Executors.newSingleThreadScheduledExecutor(task -> {
                    Thread thread = new Thread(task, "Heroes temporal voice"); thread.setDaemon(true); return thread;
                });
                worker.scheduleAtFixedRate(this::tick, 20, 20, TimeUnit.MILLISECONDS);
            } catch (Throwable error) { fail(error); }
        }

        private void fail(Throwable error) {
            if (!failed) HeroesEvolved.LOGGER.warn("Temporal voice tails unavailable; time still silences speech", error);
            failed = true;
            streams.values().forEach(HeardStream::close); streams.clear();
            tails.forEach(DyingVoice::close); tails.clear(); deliveries.clear();
        }

        synchronized void outgoing(SoundPacketEvent<? extends SoundPacket> event) {
            SoundPacket packet = event.getPacket();
            if (CATEGORY.equals(packet.getCategory()) || event.isCancelled()) return;
            var receiver = event.getReceiverConnection();
            if (receiver == null || receiver.getPlayer() == null) return;
            UUID listener = receiver.getPlayer().getUuid(), speaker = packet.getSender();
            if (TemporalVoiceState.blocks(speaker, listener)) { event.cancel(); return; }
            if (failed || packet.getOpusEncodedData().length == 0 || api == null) return;
            try {
                long now = System.nanoTime();
                StreamKey key = new StreamKey(speaker, packet.getChannelId());
                HeardStream stream = streams.get(key);
                if (stream == null) {
                    if (streams.size() >= 256) return;
                    stream = new HeardStream(api.createDecoder()); streams.put(key, stream);
                }
                stream.hear(packet, now);
                DeliveryKey delivery = new DeliveryKey(key, listener);
                if (deliveries.size() < 2048 || deliveries.containsKey(delivery))
                    deliveries.put(delivery, new Delivery(packet, now));
            } catch (Throwable error) { fail(error); }
        }

        private synchronized void tick() {
            if (api == null || failed) return;
            try {
                long now = System.nanoTime();
                var heard = deliveries.entrySet().iterator();
                while (heard.hasNext()) {
                    var entry = heard.next(); var key = entry.getKey(); var delivery = entry.getValue();
                    boolean blocked = TemporalVoiceState.blocks(key.stream.speaker, key.listener);
                    if (blocked && now - delivery.at < 300_000_000L) {
                        HeardStream stream = streams.get(key.stream);
                        if (stream != null) tails.add(new DyingVoice(key, delivery.packet, TemporalVoice.tail(stream.recent()),
                                api.createEncoder(OpusEncoderMode.AUDIO)));
                    }
                    if (blocked || now - delivery.at > 500_000_000L) heard.remove();
                }
                var playing = tails.iterator();
                while (playing.hasNext()) {
                    DyingVoice tail = playing.next();
                    var to = api.getConnectionOf(tail.key.listener);
                    if (!TemporalVoiceState.blocks(tail.key.stream.speaker, tail.key.listener) || to == null
                            || !to.isConnected() || to.isDisabled() || !tail.send(api, to)) {
                        tail.close(); playing.remove();
                    }
                }
                var idle = streams.values().iterator();
                while (idle.hasNext()) {
                    HeardStream stream = idle.next();
                    if (now - stream.at > 2_000_000_000L) { stream.close(); idle.remove(); }
                }
            } catch (Throwable error) { fail(error); }
        }

        synchronized void forget(UUID player) {
            deliveries.keySet().removeIf(key -> key.listener.equals(player) || key.stream.speaker.equals(player));
            streams.entrySet().removeIf(entry -> {
                if (!entry.getKey().speaker.equals(player)) return false;
                entry.getValue().close(); return true;
            });
            tails.removeIf(tail -> {
                if (!tail.key.listener.equals(player) && !tail.key.stream.speaker.equals(player)) return false;
                tail.close(); return true;
            });
        }

        synchronized void stop() {
            if (worker != null) worker.shutdownNow(); worker = null;
            streams.values().forEach(HeardStream::close); streams.clear();
            tails.forEach(DyingVoice::close); tails.clear(); deliveries.clear(); api = null;
        }

        private record Delivery(SoundPacket packet, long at) {}

        private static final class HeardStream {
            final OpusDecoder decoder;
            final short[] history = new short[TemporalVoice.HISTORY];
            long sequence = Long.MIN_VALUE, at;
            int size;
            HeardStream(OpusDecoder decoder) { this.decoder = decoder; }
            void hear(SoundPacket packet, long now) {
                if (packet.getSequenceNumber() == sequence) return; // One decode per stream frame, however many listeners.
                if (now - at > 300_000_000L) { size = 0; decoder.resetState(); }
                sequence = packet.getSequenceNumber(); at = now;
                short[] pcm = decoder.decode(packet.getOpusEncodedData());
                if (pcm == null || pcm.length == 0) return;
                int length = Math.min(pcm.length, history.length), keep = Math.min(size, history.length - length);
                System.arraycopy(history, size - keep, history, 0, keep);
                System.arraycopy(pcm, pcm.length - length, history, keep, length); size = keep + length;
            }
            short[] recent() { return Arrays.copyOf(history, size); }
            void close() { if (!decoder.isClosed()) decoder.close(); }
        }

        private static final class DyingVoice {
            final DeliveryKey key;
            final SoundPacket original;
            final short[] samples;
            final OpusEncoder encoder;
            final UUID channel = UUID.randomUUID();
            int cursor;
            DyingVoice(DeliveryKey key, SoundPacket original, short[] samples, OpusEncoder encoder) {
                this.key = key; this.original = original; this.samples = samples; this.encoder = encoder;
            }
            boolean send(VoicechatServerApi api, VoicechatConnection to) {
                if (cursor >= samples.length) return false;
                byte[] opus = encoder.encode(Arrays.copyOfRange(samples, cursor, cursor + TemporalVoice.FRAME));
                long sequence = cursor / TemporalVoice.FRAME; cursor += TemporalVoice.FRAME;
                if (original instanceof StaticSoundPacket)
                    api.sendStaticSoundPacketTo(to, original.staticSoundPacketBuilder().channelId(channel)
                            .opusEncodedData(opus).sequenceNumber(sequence).category(CATEGORY).build());
                else if (original instanceof LocationalSoundPacket)
                    api.sendLocationalSoundPacketTo(to, original.locationalSoundPacketBuilder().channelId(channel)
                            .opusEncodedData(opus).sequenceNumber(sequence).category(CATEGORY).build());
                else if (original instanceof EntitySoundPacket)
                    api.sendEntitySoundPacketTo(to, original.entitySoundPacketBuilder().channelId(channel)
                            .opusEncodedData(opus).sequenceNumber(sequence).category(CATEGORY).build());
                return true;
            }
            void close() { if (!encoder.isClosed()) encoder.close(); }
        }
    }

    /** A lilac eye for the volume slider: 16 by 16, ARGB. */
    private static int[][] icon() {
        int[][] pixels = new int[16][16];
        for (int y = 0; y < 16; y++)
            for (int x = 0; x < 16; x++) {
                double dx = (x - 7.5) / 7.2, dy = (y - 7.5) / 4.2;
                double lid = dx * dx + dy * dy, iris = Math.hypot(x - 7.5, y - 7.5);
                int colour = 0;
                if (lid <= 1) colour = lid > 0.7 ? 0xFF5A2AA8 : 0xFFEADCFF;
                if (iris <= 3.1) colour = 0xFFB794FF;
                if (iris <= 1.4) colour = 0xFF241638;
                pixels[x][y] = colour;
            }
        return pixels;
    }
}
