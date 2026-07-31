package com.jayptucker.heroesevolved.time;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Stores which snapshot a visitor is currently allowed to travel back from. */
public record PlayerTemporalVisitData(
        boolean visiting,
        UUID ownerId,
        ResourceLocation sourceDimension,
        int sourceMinX,
        int sourceMinZ,
        int snapshotMinX,
        int snapshotMinZ
) {
    private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(
            UUID::fromString,
            UUID::toString
    );

    public static final Codec<PlayerTemporalVisitData> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.BOOL.fieldOf("visiting")
                            .forGetter(PlayerTemporalVisitData::visiting),
                    UUID_CODEC.fieldOf("owner_id")
                            .forGetter(PlayerTemporalVisitData::ownerId),
                    ResourceLocation.CODEC.fieldOf("source_dimension")
                            .forGetter(PlayerTemporalVisitData::sourceDimension),
                    Codec.INT.fieldOf("source_min_x")
                            .forGetter(PlayerTemporalVisitData::sourceMinX),
                    Codec.INT.fieldOf("source_min_z")
                            .forGetter(PlayerTemporalVisitData::sourceMinZ),
                    Codec.INT.fieldOf("snapshot_min_x")
                            .forGetter(PlayerTemporalVisitData::snapshotMinX),
                    Codec.INT.fieldOf("snapshot_min_z")
                            .forGetter(PlayerTemporalVisitData::snapshotMinZ)
            ).apply(instance, PlayerTemporalVisitData::new));

    public static PlayerTemporalVisitData empty() {
        return new PlayerTemporalVisitData(
                false,
                new UUID(0L, 0L),
                ResourceLocation.withDefaultNamespace("overworld"),
                0,
                0,
                0,
                0
        );
    }
}
