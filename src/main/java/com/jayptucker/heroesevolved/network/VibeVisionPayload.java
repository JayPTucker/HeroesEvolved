package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * A Vibe vision ({@code vibe/VibeVisions}), to the viber alone: it starts (the view flickers for {@code flicker} ticks,
 * then the faint blue vision lasts {@code length}), or it ends early. {@code kind} is the vision's subject (an ally, a
 * village in trouble, a village about to be), which tints it.
 */
public record VibeVisionPayload(boolean start, int flicker, int length, byte kind) implements CustomPacketPayload {
    public static final Type<VibeVisionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "vibe_vision"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VibeVisionPayload> STREAM_CODEC = StreamCodec.of((b, v) -> {
        b.writeBoolean(v.start);
        b.writeVarInt(v.flicker);
        b.writeVarInt(v.length);
        b.writeByte(v.kind);
    }, b -> new VibeVisionPayload(b.readBoolean(), b.readVarInt(), b.readVarInt(), b.readByte()));

    public static VibeVisionPayload start(int flicker, int length, int kind) {
        return new VibeVisionPayload(true, flicker, length, (byte) kind);
    }

    public static VibeVisionPayload end() { return new VibeVisionPayload(false, 0, 0, (byte) 0); }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(VibeVisionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.jayptucker.heroesevolved.client.effects.VibeVisionClient.receive(payload));
    }
}
