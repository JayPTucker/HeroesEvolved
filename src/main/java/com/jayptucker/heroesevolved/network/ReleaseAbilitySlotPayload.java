package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.AbilitySlot;
import com.jayptucker.heroesevolved.ability.service.AbilityUseService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Sent when an ability key is let go, ending held (channelled) actions. */
public record ReleaseAbilitySlotPayload(AbilitySlot slot) implements CustomPacketPayload {
    public static final Type<ReleaseAbilitySlotPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    HeroesEvolved.MOD_ID,
                    "release_ability_slot"
            ));

    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            ReleaseAbilitySlotPayload
    > STREAM_CODEC = ByteBufCodecs.VAR_INT.<RegistryFriendlyByteBuf>cast().map(
            index -> {
                AbilitySlot[] slots = AbilitySlot.values();
                if (index < 0 || index >= slots.length) {
                    throw new IllegalArgumentException("Unknown ability slot: " + index);
                }
                return new ReleaseAbilitySlotPayload(slots[index]);
            },
            payload -> payload.slot().ordinal()
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(
            ReleaseAbilitySlotPayload payload,
            IPayloadContext context
    ) {
        if (context.player() instanceof ServerPlayer player) {
            AbilityUseService.releaseAssignedAction(player, payload.slot());
        }
    }
}
