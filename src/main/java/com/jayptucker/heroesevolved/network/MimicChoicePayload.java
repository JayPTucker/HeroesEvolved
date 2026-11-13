package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.mimicry.MimicryService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** From the memory wheel: channel the set in {@code slot}, or forget it. */
public record MimicChoicePayload(int slot, boolean forget) implements CustomPacketPayload {
    public static final Type<MimicChoicePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "mimic_choice"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MimicChoicePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MimicChoicePayload::slot, ByteBufCodecs.BOOL, MimicChoicePayload::forget, MimicChoicePayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(MimicChoicePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || payload.slot < 0 || payload.slot > 16) return;
            if (payload.forget) MimicryService.forgetSlot(player, payload.slot);
            else MimicryService.choose(player, payload.slot);
        });
    }
}
