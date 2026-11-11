package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.telepathy.PossessionService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The possessor's half-typed chat line. To the server it is what the possessor's chat box holds
 * right now; to the possessed player it is what to show them. Empty clears it.
 */
public record MindTypingPayload(String text) implements CustomPacketPayload {
    /** Vanilla's chat box limit. */
    public static final int MAX_LENGTH = 256;
    public static final Type<MindTypingPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "mind_typing"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MindTypingPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.stringUtf8(MAX_LENGTH), MindTypingPayload::text, MindTypingPayload::new);
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(MindTypingPayload p, IPayloadContext c) {
        c.enqueueWork(() -> {
            if (c.flow().isServerbound()) { if (c.player() instanceof ServerPlayer sp) PossessionService.typed(sp, p.text()); }
            else com.jayptucker.heroesevolved.client.TelepathyClient.typed(p.text());
        });
    }
}
