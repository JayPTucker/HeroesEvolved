package com.jayptucker.heroesevolved.vibe;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The server tick publishes nearby players' voice paths for Simple Voice Chat's audio thread. */
public final class BreachVoiceRoutes {
    record Link(VibeBreaches.Mouth first, VibeBreaches.Mouth second) {}

    public record Route(UUID listener, Vec3 position, double through, double direct) {
        public boolean audible(double range) { return through < range && direct > range; }
    }

    private static volatile Map<UUID, List<Route>> routes = Map.of();

    private BreachVoiceRoutes() {}

    static void publish(List<Link> open, double range) {
        if (open.isEmpty()) {
            clear();
            return;
        }
        var shortest = new HashMap<UUID, Map<UUID, Route>>();
        for (Link link : open) {
            var first = nearby(link.first(), range);
            var second = nearby(link.second(), range);
            connect(shortest, first, link.first(), second, link.second(), range);
            connect(shortest, second, link.second(), first, link.first(), range);
        }
        var snapshot = new HashMap<UUID, List<Route>>();
        shortest.forEach((speaker, listeners) -> snapshot.put(speaker, List.copyOf(listeners.values())));
        routes = Map.copyOf(snapshot);
    }

    private static List<ServerPlayer> nearby(VibeBreaches.Mouth mouth, double range) {
        return mouth.level().getPlayers(player -> player.isAlive() && !player.isSpectator()
                && player.getEyePosition().distanceToSqr(mouth.center()) < range * range);
    }

    private static void connect(Map<UUID, Map<UUID, Route>> out, List<ServerPlayer> speakers, VibeBreaches.Mouth remote,
                                List<ServerPlayer> listeners, VibeBreaches.Mouth local, double range) {
        for (ServerPlayer speaker : speakers) {
            double remoteDistance = speaker.getEyePosition().distanceTo(remote.center());
            for (ServerPlayer listener : listeners) {
                if (speaker == listener) continue;
                Vec3 ear = listener.getEyePosition();
                Vec3 toMouth = local.center().subtract(ear);
                double localDistance = toMouth.length(), through = localDistance + remoteDistance;
                if (through >= range) continue;
                double direct = local.level() == remote.level() ? speaker.distanceTo(listener) : Double.POSITIVE_INFINITY;
                // Project the speaker's distance behind the nearby mouth so attenuation includes both legs of the path.
                Vec3 direction = localDistance > .001 ? toMouth.scale(1 / localDistance) : local.direction();
                Route route = new Route(listener.getUUID(), ear.add(direction.scale(through)), through, direct);
                out.computeIfAbsent(speaker.getUUID(), id -> new HashMap<>())
                        .merge(listener.getUUID(), route, (a, b) -> a.through() <= b.through() ? a : b);
            }
        }
    }

    public static List<Route> routes(UUID speaker) { return routes.getOrDefault(speaker, List.of()); }

    public static void clear() { routes = Map.of(); }
}
