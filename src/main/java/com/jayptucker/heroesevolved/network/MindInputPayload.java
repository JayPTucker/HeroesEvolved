package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.telepathy.PossessionService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Never supplies an actor or target: the server resolves the current session. */
public record MindInputPayload(int operation, int cue, int nonce, float forward, float strafe, float yaw, float pitch, boolean jump, boolean sneak) implements CustomPacketPayload {
    public static final Type<MindInputPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "mind_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MindInputPayload> STREAM_CODEC = StreamCodec.of(
        (b,p) -> { b.writeVarInt(p.operation); b.writeVarInt(p.cue); b.writeVarInt(p.nonce); b.writeFloat(p.forward); b.writeFloat(p.strafe); b.writeFloat(p.yaw); b.writeFloat(p.pitch); b.writeBoolean(p.jump); b.writeBoolean(p.sneak); },
        b -> new MindInputPayload(b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readFloat(),b.readFloat(),b.readFloat(),b.readFloat(),b.readBoolean(),b.readBoolean()));
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(MindInputPayload p, IPayloadContext c) { c.enqueueWork(() -> { if(c.player() instanceof ServerPlayer sp) PossessionService.input(sp,p); }); }
}
