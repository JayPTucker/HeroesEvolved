package com.jayptucker.heroesevolved.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

public record AbilitySnapshot(
    ResourceLocation abilityId,
    boolean unlocked,
    int level,
    int mastery
) {
    public static final StreamCodec<RegistryFriendlyByteBuf, AbilitySnapshot> STREAM_CODEC =
        StreamCodec.composite(
            ResourceLocation.STREAM_CODEC,
            AbilitySnapshot::abilityId,
            ByteBufCodecs.BOOL,
            AbilitySnapshot::unlocked,
            ByteBufCodecs.VAR_INT,
            AbilitySnapshot::level,
            ByteBufCodecs.VAR_INT,
            AbilitySnapshot::mastery,
            AbilitySnapshot::new
        );
}