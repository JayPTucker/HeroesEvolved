package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.vibe.VibeFrequencies;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * From the frequency wheel: open a breach to a slot (-1 for where you look), glimpse it, mark where you stand into it,
 * forget it or rename it.
 */
public record FrequencyChoicePayload(int op, int slot, String name) implements CustomPacketPayload {
    public static final int BREACH = 0, GLIMPSE = 1, MARK = 2, FORGET = 3, RENAME = 4;

    public static final Type<FrequencyChoicePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "frequency_choice"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FrequencyChoicePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, FrequencyChoicePayload::op,
            ByteBufCodecs.VAR_INT, FrequencyChoicePayload::slot,
            ByteBufCodecs.stringUtf8(64), FrequencyChoicePayload::name,
            FrequencyChoicePayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(FrequencyChoicePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) VibeFrequencies.choose(player, payload.op, payload.slot, payload.name);
        });
    }
}
