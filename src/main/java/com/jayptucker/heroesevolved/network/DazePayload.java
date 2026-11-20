package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Knocked flat by a blast (the Last Day's wave): this player's view lies on their back, their eyes are shut and blink
 * open for {@code ticks}, and with {@code ringing} their ears ring and the world goes muffled ({@code client/DazeView}).
 * For the first {@code blackout} ticks they are out cold, the screaming around them slowed and smeared. The server holds
 * their body down for the same time.
 */
public record DazePayload(int ticks, boolean ringing, int blackout) implements CustomPacketPayload {
    public static final Type<DazePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "daze"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DazePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DazePayload::ticks, ByteBufCodecs.BOOL, DazePayload::ringing, ByteBufCodecs.VAR_INT,
            DazePayload::blackout, DazePayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(DazePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.jayptucker.heroesevolved.client.DazeView.begin(payload.ticks, 0, payload.ringing, payload.blackout));
    }
}
