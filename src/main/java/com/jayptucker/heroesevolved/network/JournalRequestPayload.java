package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The journal asks for the latest story progress when it opens. */
public record JournalRequestPayload() implements CustomPacketPayload {
    public static final Type<JournalRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "journal_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, JournalRequestPayload> STREAM_CODEC =
            StreamCodec.unit(new JournalRequestPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(JournalRequestPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, StoryJournalPayload.of(player));
        }
    }
}
