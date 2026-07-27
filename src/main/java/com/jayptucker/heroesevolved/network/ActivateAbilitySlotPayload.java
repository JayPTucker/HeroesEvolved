package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.AbilitySlot;
import com.jayptucker.heroesevolved.ability.service.AbilityUseService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ActivateAbilitySlotPayload(
        AbilitySlot slot,
        boolean allowOverexertion
) implements CustomPacketPayload {
    public static final Type<ActivateAbilitySlotPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    HeroesEvolved.MOD_ID,
                    "activate_ability_slot"
            ));

    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            ActivateAbilitySlotPayload
    > STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.slot().ordinal());
                buffer.writeBoolean(payload.allowOverexertion());
            },
            buffer -> {
                int slotIndex = buffer.readVarInt();
                AbilitySlot[] slots = AbilitySlot.values();

                if (slotIndex < 0 || slotIndex >= slots.length) {
                    throw new IllegalArgumentException(
                            "Unknown ability slot: " + slotIndex
                    );
                }

                return new ActivateAbilitySlotPayload(
                        slots[slotIndex],
                        buffer.readBoolean()
                );
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(
            ActivateAbilitySlotPayload payload,
            IPayloadContext context
    ) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }

        // The server determines the player's assigned power and action.
        // The client is never allowed to provide a power ID directly.
        AbilityUseService.activateAssignedAction(
                player,
                payload.slot(),
                payload.allowOverexertion()
        );
    }
}