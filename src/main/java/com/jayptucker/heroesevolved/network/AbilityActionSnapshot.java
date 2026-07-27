package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.ability.AbilitySlot;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.Objects;

public record AbilityActionSnapshot(
        AbilitySlot slot,
        String displayNameKey,
        boolean unlocked,
        long cooldownEndGameTime
) {
    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            AbilityActionSnapshot
    > STREAM_CODEC = StreamCodec.of(
            (buffer, snapshot) -> {
                buffer.writeVarInt(snapshot.slot().ordinal());
                buffer.writeUtf(snapshot.displayNameKey(), 256);
                buffer.writeBoolean(snapshot.unlocked());
                buffer.writeVarLong(snapshot.cooldownEndGameTime());
            },
            buffer -> {
                int slotIndex = buffer.readVarInt();
                AbilitySlot[] slots = AbilitySlot.values();

                if (slotIndex < 0 || slotIndex >= slots.length) {
                    throw new IllegalArgumentException(
                            "Unknown ability slot: " + slotIndex
                    );
                }

                return new AbilityActionSnapshot(
                        slots[slotIndex],
                        buffer.readUtf(256),
                        buffer.readBoolean(),
                        buffer.readVarLong()
                );
            }
    );

    public AbilityActionSnapshot {
        Objects.requireNonNull(slot, "Slot cannot be null.");
        Objects.requireNonNull(
                displayNameKey,
                "Display name key cannot be null."
        );
    }
}