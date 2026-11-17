package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * What a player's client needs for finishers: the power whose finisher they have (its path, empty for none), whether
 * finishers are switched on, whether PvP is allowed (so players can glisten), and the finisher's reach. Sent at login
 * and whenever it changes (checked once a second).
 */
public record FinisherStatePayload(String power, boolean enabled, boolean pvp, float reach) implements CustomPacketPayload {
    public static final Type<FinisherStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "finisher_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FinisherStatePayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> { b.writeUtf(p.power, 64); b.writeBoolean(p.enabled); b.writeBoolean(p.pvp); b.writeFloat(p.reach); },
            b -> new FinisherStatePayload(b.readUtf(64), b.readBoolean(), b.readBoolean(), b.readFloat()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(FinisherStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientFinisher.set(payload));
    }
}
