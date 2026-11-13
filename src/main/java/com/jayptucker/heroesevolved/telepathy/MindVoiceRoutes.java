package com.jayptucker.heroesevolved.telepathy;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who hears whose voice by telepathy. The server thread works the routes out every tick ({@link #compute}, then
 * {@link #publish}); the voice chat thread reads them for every microphone packet ({@link #routes}) in {@link MindVoice},
 * the only class that touches Simple Voice Chat. Nothing here imports the voice chat API, so the rules can be tested
 * without it.
 *
 * <ul>
 *   <li><b>Far Hearing</b>: a voice beyond normal voice range reaches the listener from a point a few blocks away in
 *   the speaker's direction, fainter the farther the speaker is and clear when the listener faces it.</li>
 *   <li><b>Mind Link, Hive Link</b> (and Hive Mind): linked minds hear each other inside their heads, from anywhere.</li>
 *   <li><b>Mind Network</b>: a telepath's voice reaches inside the head of every mind they hold a deep thread to, and
 *   that mind's voice comes back to them.</li>
 * </ul>
 * A route is only made when the two players can't already hear each other (another dimension, or beyond voice range),
 * so nobody hears a voice twice. A telepath speaking with their <b>inner voice</b> is the exception: their voice reaches
 * their linked and threaded minds however close they stand, and nobody else hears it at all ({@link MindVoice} cancels
 * the ordinary broadcast). Every voice heard inside a head is distorted ({@link PsychicVoice}).
 */
public final class MindVoiceRoutes {
    public enum Kind { FAR, LINK, PROJECT }

    /**
     * One way a speaker's voice reaches a listener: inside their head, or from {@code (x, y, z)} with the voice chat's
     * {@code distance}. {@code range} is how far apart the two really are (infinite across dimensions).
     */
    public record Route(UUID listener, Kind kind, boolean inHead, double x, double y, double z, float distance, double range) {}

    /** A Far Hearing listener and how far they hear. */
    public record Listener(ServerPlayer player, double range) {}

    /** The voice chat distance a locational relay attenuates over. */
    public static final float RELAY_DISTANCE = 16;

    private static volatile Map<UUID, List<Route>> routes = Map.of();
    private static volatile Set<UUID> inner = Set.of();
    private static volatile double voiceDistance = 48;
    private static volatile boolean enabled = true, groups;
    /** Speakers each listener has heard by telepathy since the server last looked (written on the voice thread). */
    private static final Map<UUID, Set<UUID>> HEARD = new ConcurrentHashMap<>();
    /** When each player last spoke into their microphone, in {@link System#nanoTime()}. */
    private static final Map<UUID, Long> SPOKE = new ConcurrentHashMap<>();

    private MindVoiceRoutes() {}

    // ------------------------------------------------------------------
    // Server thread
    // ------------------------------------------------------------------

    /**
     * Works out every route: Far Hearing listeners, linked groups (each a list of players) and Mind Network telepaths
     * with the minds they reach. {@code voiceDistance} is how far a voice carries without telepathy.
     */
    public static Map<UUID, List<Route>> compute(Collection<Listener> far, Collection<? extends Collection<ServerPlayer>> links,
                                                 Map<ServerPlayer, ? extends Collection<ServerPlayer>> projections,
                                                 Collection<ServerPlayer> everyone, double voiceDistance) {
        return compute(far, links, projections, everyone, voiceDistance, Set.of());
    }

    /** As above, with the telepaths speaking with their inner voice ({@code inward}). */
    public static Map<UUID, List<Route>> compute(Collection<Listener> far, Collection<? extends Collection<ServerPlayer>> links,
                                                 Map<ServerPlayer, ? extends Collection<ServerPlayer>> projections,
                                                 Collection<ServerPlayer> everyone, double voiceDistance, Set<UUID> inward) {
        Map<UUID, Map<UUID, Route>> out = new HashMap<>();
        for (Listener l : far) {
            ServerPlayer listener = l.player();
            Vec3 eye = listener.getEyePosition(), look = listener.getLookAngle();
            for (ServerPlayer speaker : everyone) {
                // An inner voice isn't spoken aloud: there is nothing to overhear.
                if (speaker == listener || speaker.isSpectator() || speaker.level() != listener.level() || inward.contains(speaker.getUUID())) continue;
                Vec3 voice = speaker.getEyePosition();
                double d = voice.distanceTo(eye);
                if (d <= voiceDistance || d > l.range()) continue;
                Vec3 toward = voice.subtract(eye).normalize();
                double k = facing(look, toward) ? 3 : 6 + 7 * Math.clamp((d - voiceDistance) / Math.max(1, l.range() - voiceDistance), 0, 1);
                Vec3 at = eye.add(toward.scale(k));
                put(out, speaker, new Route(listener.getUUID(), Kind.FAR, false, at.x, at.y, at.z, RELAY_DISTANCE, d));
            }
        }
        for (Collection<ServerPlayer> group : links)
            for (ServerPlayer speaker : group)
                for (ServerPlayer listener : group)
                    if (speaker != listener && (inward.contains(speaker.getUUID()) || apart(speaker, listener, voiceDistance)))
                        put(out, speaker, new Route(listener.getUUID(), Kind.LINK, true, 0, 0, 0, 0, range(speaker, listener)));
        for (var entry : projections.entrySet()) {
            ServerPlayer telepath = entry.getKey();
            for (ServerPlayer mind : entry.getValue()) {
                if (mind == telepath) continue;
                if (inward.contains(telepath.getUUID()) || apart(telepath, mind, voiceDistance))
                    put(out, telepath, new Route(mind.getUUID(), Kind.PROJECT, true, 0, 0, 0, 0, range(telepath, mind)));
                if (apart(telepath, mind, voiceDistance))
                    put(out, mind, new Route(telepath.getUUID(), Kind.PROJECT, true, 0, 0, 0, 0, range(telepath, mind)));
            }
        }
        Map<UUID, List<Route>> result = new HashMap<>();
        out.forEach((speaker, byListener) -> result.put(speaker, List.copyOf(byListener.values())));
        return result;
    }

    /** One route per speaker and listener: a voice inside the head wins over one heard from afar. */
    private static void put(Map<UUID, Map<UUID, Route>> out, ServerPlayer speaker, Route route) {
        Map<UUID, Route> byListener = out.computeIfAbsent(speaker.getUUID(), k -> new LinkedHashMap<>());
        Route old = byListener.get(route.listener());
        if (old == null || route.inHead() && !old.inHead()) byListener.put(route.listener(), route);
    }

    /** Whether two players can't hear each other the ordinary way. */
    static boolean apart(ServerPlayer a, ServerPlayer b, double voiceDistance) {
        return a.level() != b.level() || a.getEyePosition().distanceTo(b.getEyePosition()) > voiceDistance;
    }

    private static double range(ServerPlayer a, ServerPlayer b) {
        return a.level() != b.level() ? Double.POSITIVE_INFINITY : a.getEyePosition().distanceTo(b.getEyePosition());
    }

    /** Within fifteen degrees of the look: a voice you turn toward comes through clear. */
    public static boolean facing(Vec3 look, Vec3 toward) {
        return look.dot(toward) > 0.966;
    }

    public static void publish(Map<UUID, List<Route>> next, boolean voicesOn, boolean hearGroups) {
        publish(next, Set.of(), voicesOn, hearGroups);
    }

    public static void publish(Map<UUID, List<Route>> next, Set<UUID> inward, boolean voicesOn, boolean hearGroups) {
        routes = Map.copyOf(next);
        inner = Set.copyOf(inward);
        enabled = voicesOn;
        groups = hearGroups;
    }

    /** Every speaker heard by {@code listener} since the last call, and forgets them. */
    public static Set<UUID> drain(UUID listener) {
        Set<UUID> heard = HEARD.remove(listener);
        return heard == null ? Set.of() : heard;
    }

    /** Whether {@code speaker} has been speaking within the last {@code millis}. */
    public static boolean speaking(UUID speaker, long millis) {
        Long at = SPOKE.get(speaker);
        return at != null && System.nanoTime() - at < millis * 1_000_000L;
    }

    public static double voiceDistance() { return voiceDistance; }

    public static void clear() {
        routes = Map.of();
        inner = Set.of();
        HEARD.clear();
        SPOKE.clear();
    }

    // ------------------------------------------------------------------
    // Voice chat thread
    // ------------------------------------------------------------------

    static List<Route> routes(UUID speaker) { return enabled ? routes.getOrDefault(speaker, List.of()) : List.of(); }

    static boolean hearsGroups() { return groups; }

    /** Whether a speaker's voice is going only into minds right now (and should not be broadcast). */
    static boolean inner(UUID speaker) { return enabled && inner.contains(speaker); }

    static void voiceDistance(double distance) { if (distance > 0) voiceDistance = distance; }

    static void spoke(UUID speaker) { SPOKE.put(speaker, System.nanoTime()); }

    static void heard(UUID listener, UUID speaker) { HEARD.computeIfAbsent(listener, k -> ConcurrentHashMap.newKeySet()).add(speaker); }

    /** For tests: records a voice as heard, as the voice thread would. */
    public static void testHeard(UUID listener, UUID speaker) { heard(listener, speaker); spoke(speaker); }

    /** The routes the voice thread would use for {@code speaker} right now. */
    public static List<Route> current(UUID speaker) { return routes(speaker); }
}
