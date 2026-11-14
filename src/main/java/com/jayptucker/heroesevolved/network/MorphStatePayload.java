package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.shapeshift.ShapeshiftForm;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Optional;

/**
 * Tells clients which form a shapeshifter wears (with a person's profile for a person's form); empty means their own
 * body. Sent once per change, with {@code animate} set so the plates ripple; a viewer who only now comes into range
 * gets it without.
 */
public record MorphStatePayload(int entity, Optional<ShapeshiftForm> form, boolean animate, int flags) implements CustomPacketPayload {
    /** The wearer has Perfect Copy: in a person's shape their own gear isn't drawn. */
    public static final int PERFECT_COPY = 1;
    public static final Type<MorphStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("heroesevolved", "morph_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MorphStatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MorphStatePayload::entity,
            ByteBufCodecs.optional(ShapeshiftForm.STREAM_CODEC), MorphStatePayload::form,
            ByteBufCodecs.BOOL, MorphStatePayload::animate,
            ByteBufCodecs.VAR_INT, MorphStatePayload::flags,
            MorphStatePayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(MorphStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.jayptucker.heroesevolved.client.MorphClient.update(payload.entity, payload.form.orElse(null), payload.animate, payload.flags));
    }
}
