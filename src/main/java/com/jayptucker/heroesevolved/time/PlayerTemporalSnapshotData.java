package com.jayptucker.heroesevolved.time;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Persistent ownership and coordinate mapping for one player's saved past. */
public record PlayerTemporalSnapshotData(
        boolean exists,
        boolean ready,
        ResourceLocation sourceDimension,
        int sourceMinX,
        int sourceMinZ,
        int snapshotMinX,
        int snapshotMinZ,
        List<TemporalEchoData> echoes
) {
    public static final Codec<PlayerTemporalSnapshotData> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.BOOL.fieldOf("exists")
                            .forGetter(PlayerTemporalSnapshotData::exists),
                    Codec.BOOL.fieldOf("ready")
                            .forGetter(PlayerTemporalSnapshotData::ready),
                    ResourceLocation.CODEC.fieldOf("source_dimension")
                            .forGetter(
                                    PlayerTemporalSnapshotData::sourceDimension
                            ),
                    Codec.INT.fieldOf("source_min_x")
                            .forGetter(PlayerTemporalSnapshotData::sourceMinX),
                    Codec.INT.fieldOf("source_min_z")
                            .forGetter(PlayerTemporalSnapshotData::sourceMinZ),
                    Codec.INT.fieldOf("snapshot_min_x")
                            .forGetter(
                                    PlayerTemporalSnapshotData::snapshotMinX
                            ),
                    Codec.INT.fieldOf("snapshot_min_z")
                            .forGetter(
                                    PlayerTemporalSnapshotData::snapshotMinZ
                            ),
                    // Existing worlds created before echoes have no "echoes"
                    // field, so default it to an empty list when loading them.
                    TemporalEchoData.CODEC.listOf()
                            .optionalFieldOf("echoes", List.of())
                            .forGetter(PlayerTemporalSnapshotData::echoes)
            ).apply(instance, PlayerTemporalSnapshotData::new));

    public static PlayerTemporalSnapshotData empty() {
        return new PlayerTemporalSnapshotData(
                false,
                false,
                ResourceLocation.withDefaultNamespace("overworld"),
                0,
                0,
                0,
                0,
                List.of()
        );
    }

    public PlayerTemporalSnapshotData withReady() {
        return new PlayerTemporalSnapshotData(
                exists,
                true,
                sourceDimension,
                sourceMinX,
                sourceMinZ,
                snapshotMinX,
                snapshotMinZ,
                echoes
        );
    }
}
