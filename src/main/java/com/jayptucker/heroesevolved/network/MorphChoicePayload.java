package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.shapeshift.ShapeshiftService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** From the form wheel: wear the remembered form with this key, or "" to go back to your own body. */
public record MorphChoicePayload(String form) implements CustomPacketPayload {
    public static final Type<MorphChoicePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "morph_choice"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MorphChoicePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(256), MorphChoicePayload::form, MorphChoicePayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(MorphChoicePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) ShapeshiftService.choose(player, payload.form);
        });
    }
}
