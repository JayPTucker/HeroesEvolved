package com.jayptucker.heroesevolved.network;
import com.jayptucker.heroesevolved.client.sound.SonicVoiceSound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.UUID;
public record SonicVoicePayload(int entity, UUID uuid, boolean active) implements CustomPacketPayload {
    public static final Type<SonicVoicePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("heroesevolved", "sonic_voice"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SonicVoicePayload> STREAM_CODEC = StreamCodec.of(
            (b, v) -> { b.writeVarInt(v.entity); b.writeUUID(v.uuid); b.writeBoolean(v.active); },
            b -> new SonicVoicePayload(b.readVarInt(), b.readUUID(), b.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(SonicVoicePayload payload, IPayloadContext context) { context.enqueueWork(() -> SonicVoiceSound.update(payload)); }
}
