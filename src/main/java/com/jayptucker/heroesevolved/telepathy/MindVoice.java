package com.jayptucker.heroesevolved.telepathy;

import com.jayptucker.heroesevolved.HeroesEvolved;
import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.PlayerDisconnectedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStoppedEvent;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoderMode;
import de.maxhenkel.voicechat.api.packets.MicrophonePacket;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The one place that speaks to Simple Voice Chat (henkelmax), found by its {@code @ForgeVoicechatPlugin} scan. For every
 * microphone packet it looks up the telepathic routes the server worked out ({@link MindVoiceRoutes}) and sends the voice
 * on: inside the listener's head (a static packet) or from a point near them in the speaker's direction (a locational
 * one). Relayed voices use their own channel per speaker, so they never mix with the speaker's ordinary voice, and their
 * own volume category, "Telepathy", which every player can turn down in the voice chat settings.
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
    private static volatile boolean broken;
    private static final Map<UUID, UUID> FAR_CHANNELS = new ConcurrentHashMap<>(), HEAD_CHANNELS = new ConcurrentHashMap<>();
    /** Each speaker's codecs and distortion, kept while they speak into minds. */
    private static final Map<UUID, Head> HEADS = new ConcurrentHashMap<>();

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
            MindVoiceRoutes.clear();
            HEADS.values().forEach(Head::close);
            HEADS.clear();
        });
        registration.registerEvent(PlayerDisconnectedEvent.class, event -> {
            Head head = HEADS.remove(event.getPlayerUuid());
            if (head != null) synchronized (head) { head.close(); }
        });
        registration.registerEvent(MicrophonePacketEvent.class, this::microphone);
    }

    private static void fail(String what, Throwable t) {
        if (!broken) HeroesEvolved.LOGGER.warn("Simple Voice Chat link failed ({}); telepaths will no longer hear voices", what, t);
        broken = true;
    }

    private void started(VoicechatServerStartedEvent event) {
        try {
            VoicechatServerApi api = event.getVoicechat();
            MindVoiceRoutes.voiceDistance(api.getVoiceChatDistance());
            api.registerVolumeCategory(api.volumeCategoryBuilder().setId(CATEGORY).setName("Telepathy")
                    .setDescription("Voices you hear in your head: Far Hearing, Hive Link and Mind Network")
                    .setIcon(icon()).build());
        } catch (Throwable t) {
            fail("start", t);
        }
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
