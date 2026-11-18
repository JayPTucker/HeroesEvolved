package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.client.ClientGodMode;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.List;
import java.util.UUID;

/** UUIDs keep the outline valid through respawns, tracking, and dimension changes. */
public record GodModePayload(List<UUID> players) implements CustomPacketPayload {
    public static final Type<GodModePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "god_mode"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GodModePayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list(512)).cast(), GodModePayload::players, GodModePayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(GodModePayload payload, IPayloadContext context) { ClientGodMode.update(payload.players()); }
}
