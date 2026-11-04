package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.data.EclipseTimeline;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Synchronizes the Eclipse timeline and calendar from the server to each client. The client
 * animates the moon from its own synced game clock, so this is only sent on phase changes.
 */
public record EclipseStatePayload(
        long startGameTime,
        int approachTicks,
        int totalityTicks,
        int departureTicks,
        int intervalDays
) implements CustomPacketPayload {
    public static final Type<EclipseStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    HeroesEvolved.MOD_ID,
                    "eclipse_state"
            ));

    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            EclipseStatePayload
    > STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG,
            EclipseStatePayload::startGameTime,
            ByteBufCodecs.VAR_INT,
            EclipseStatePayload::approachTicks,
            ByteBufCodecs.VAR_INT,
            EclipseStatePayload::totalityTicks,
            ByteBufCodecs.VAR_INT,
            EclipseStatePayload::departureTicks,
            ByteBufCodecs.VAR_INT,
            EclipseStatePayload::intervalDays,
            EclipseStatePayload::new
    );

    public static EclipseStatePayload of(EclipseTimeline timeline, int intervalDays) {
        return new EclipseStatePayload(
                timeline.startGameTime(),
                timeline.approachTicks(),
                timeline.totalityTicks(),
                timeline.departureTicks(),
                intervalDays
        );
    }

    public EclipseTimeline timeline() {
        return new EclipseTimeline(startGameTime, approachTicks, totalityTicks, departureTicks);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(
            EclipseStatePayload payload,
            IPayloadContext context
    ) {
        context.enqueueWork(() -> ClientEclipseState.set(payload.timeline(), payload.intervalDays()));
    }
}
