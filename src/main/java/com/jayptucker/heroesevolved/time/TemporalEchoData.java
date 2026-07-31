package com.jayptucker.heroesevolved.time;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.UUID;

/** Immutable record of a player as they appeared when a snapshot was made. */
public record TemporalEchoData(
        UUID playerId,
        String playerName,
        double x,
        double y,
        double z,
        float yaw,
        float pitch
) {
    private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(
            UUID::fromString,
            UUID::toString
    );

    public static final Codec<TemporalEchoData> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    UUID_CODEC.fieldOf("player_id")
                            .forGetter(TemporalEchoData::playerId),
                    Codec.STRING.fieldOf("player_name")
                            .forGetter(TemporalEchoData::playerName),
                    Codec.DOUBLE.fieldOf("x").forGetter(TemporalEchoData::x),
                    Codec.DOUBLE.fieldOf("y").forGetter(TemporalEchoData::y),
                    Codec.DOUBLE.fieldOf("z").forGetter(TemporalEchoData::z),
                    Codec.FLOAT.fieldOf("yaw").forGetter(TemporalEchoData::yaw),
                    Codec.FLOAT.fieldOf("pitch")
                            .forGetter(TemporalEchoData::pitch)
            ).apply(instance, TemporalEchoData::new));
}
