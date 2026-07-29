package com.jayptucker.heroesevolved.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

public record AbilitySnapshot(
    ResourceLocation abilityId,
    boolean unlocked,
    int level,
    long mastery,
    long masteryRequiredForCurrentLevel,
    long masteryRequiredForNextLevel,
    int maximumLevel
) {
    public static final StreamCodec<RegistryFriendlyByteBuf, AbilitySnapshot> STREAM_CODEC =
        StreamCodec.of(
            (buffer, snapshot) -> {
                ResourceLocation.STREAM_CODEC.encode(buffer, snapshot.abilityId());
                ByteBufCodecs.BOOL.encode(buffer, snapshot.unlocked());
                ByteBufCodecs.VAR_INT.encode(buffer, snapshot.level());
                ByteBufCodecs.VAR_LONG.encode(buffer, snapshot.mastery());
                ByteBufCodecs.VAR_LONG.encode(
                        buffer,
                        snapshot.masteryRequiredForCurrentLevel()
                );
                ByteBufCodecs.VAR_LONG.encode(
                        buffer,
                        snapshot.masteryRequiredForNextLevel()
                );
                ByteBufCodecs.VAR_INT.encode(buffer, snapshot.maximumLevel());
            },
            buffer -> new AbilitySnapshot(
                    ResourceLocation.STREAM_CODEC.decode(buffer),
                    ByteBufCodecs.BOOL.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.VAR_LONG.decode(buffer),
                    ByteBufCodecs.VAR_LONG.decode(buffer),
                    ByteBufCodecs.VAR_LONG.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer)
            )
        );
}
