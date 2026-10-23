package com.jayptucker.heroesevolved.effects;

import com.jayptucker.heroesevolved.flight.FlightService;
import com.jayptucker.heroesevolved.flight.FlightVisualGeometry;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload;
import com.jayptucker.heroesevolved.network.AbilityEffectPayload.Kind;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-safe visual cues. No damage, sounds, movement, or power state changes. */
public final class AbilityEffects {
    private AbilityEffects() {
    }

    public static void send(ServerLevel level, Kind kind, Vec3 origin, Vec3 target, double scale) {
        AbilityEffectPayload payload = new AbilityEffectPayload(kind, origin, target,
                (float) Math.clamp(scale, 0.1D, 64.0D));
        Vec3 segment = target.subtract(origin);
        double range = Math.min(128.0D, 64.0D + payload.scale());
        for (ServerPlayer viewer : level.players()) {
            // Distance to the whole segment keeps the end of a long bolt visible.
            double along = segment.lengthSqr() < 0.001D ? 0.0D
                    : Math.clamp(viewer.position().subtract(origin).dot(segment)
                            / segment.lengthSqr(), 0.0D, 1.0D);
            if (viewer.position().distanceToSqr(origin.add(segment.scale(along))) <= range * range) {
                PacketDistributor.sendToPlayer(viewer, payload);
            }
        }
    }

    public static void at(ServerPlayer player, Kind kind, double scale) {
        Vec3 origin = kind == Kind.FLIGHT_BURST
                ? FlightVisualGeometry.bodyCenter(player.position()) : player.position();
        Vec3 direction = kind == Kind.FLIGHT_BURST
                ? FlightVisualGeometry.direction(player, FlightService.isCycloneActive(player), 1.0F)
                : player.getLookAngle();
        send(player.serverLevel(), kind, origin, origin.add(direction), scale);
    }

    public static void scan(ServerPlayer player, double radius) {
        // Tremor Sense is private, just like its existing revealed outlines.
        PacketDistributor.sendToPlayer(player, new AbilityEffectPayload(Kind.TREMOR_SCAN,
                player.position(), player.position(), (float) Math.min(64.0D, radius)));
    }

    /** Called only after an action succeeds, including actions with no previous VFX. */
    public static void onAction(ServerPlayer player, ResourceLocation power, String action) {
        switch (power.getPath()) {
            case "time_manipulation" -> {
                if (!action.equals("time_rewind")) {
                    at(player, action.equals("time_blink") ? Kind.TEMPORAL_BURST : Kind.TEMPORAL_FIELD,
                            action.equals("time_stop") ? 7.0D : 3.0D);
                }
            }
            case "flight" -> {
                if (!action.equals("flight_cyclone") && !action.equals("flight_dive_bomb")) {
                    at(player, Kind.FLIGHT_BURST, action.equals("flight_launch") ? 3.0D : 1.6D);
                }
            }
            case "regeneration" -> {
                // Resurrection's key only reports readiness. Its real VFX run on revival.
                if (!action.equals("regeneration_resurrection")
                        && !action.equals("regeneration_healing_aura")) {
                    at(player, Kind.REGENERATION,
                            action.equals("regeneration_indestructible") ? 1.4D : 1.0D);
                }
            }
            case "telekinesis" -> at(player,
                    action.equals("telekinesis_push") ? Kind.PSYCHIC_PUSH : Kind.PSYCHIC_FIELD,
                    action.equals("telekinesis_storm") ? 6.0D : 2.5D);
            case "electrokinesis" -> {
                if (!action.equals("electro_ball_lightning") && !action.equals("electro_thunderstrike")) {
                    at(player, Kind.ELECTRIC_NOVA, action.equals("electro_storm_surge") ? 7.0D : 1.1D);
                }
            }
            // A faint psychic ripple around the telepath; the tether to the
            // target mind is sent by TelepathyService itself.
            case "telepathy" -> at(player, Kind.MIND_PULSE,
                    action.equals("telepathy_nightmare") ? 3.0D : 1.4D);
            default -> { /* Quake's cues are synchronized to its actual wave/impact. */ }
        }
    }
}
