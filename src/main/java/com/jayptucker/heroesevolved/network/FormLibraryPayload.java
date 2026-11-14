package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.shapeshift.ShapeshiftForm;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * A shapeshifter's remembered forms (oldest first), the key of the one they wear ("" for their own body), how many they
 * can hold and their Power Level. Sent when Shift + Morph opens the form wheel.
 */
public record FormLibraryPayload(List<ShapeshiftForm> forms, String current, int capacity, int level) implements CustomPacketPayload {
    public static final Type<FormLibraryPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "form_library"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FormLibraryPayload> STREAM_CODEC = StreamCodec.composite(
            ShapeshiftForm.STREAM_CODEC.apply(ByteBufCodecs.list(32)), FormLibraryPayload::forms,
            ByteBufCodecs.STRING_UTF8, FormLibraryPayload::current,
            ByteBufCodecs.VAR_INT, FormLibraryPayload::capacity,
            ByteBufCodecs.VAR_INT, FormLibraryPayload::level,
            FormLibraryPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(FormLibraryPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.jayptucker.heroesevolved.gui.screen.FormWheelScreen.open(payload));
    }
}
