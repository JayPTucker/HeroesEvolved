package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.client.effects.TimeIntroClient;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Reliable scene cues, independent of particle settings or distant entity tracking. */
public record TimeIntroPayload(int stage) implements CustomPacketPayload {
    public static final int ARRIVAL = 0, DETONATION = 1, RETURN = 2, BLAST_SOUND = 3, CANCEL = 4;
    public static final Type<TimeIntroPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            HeroesEvolved.MOD_ID, "time_intro"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TimeIntroPayload> STREAM_CODEC =
            StreamCodec.of((buf, value) -> buf.writeVarInt(value.stage),
                    buf -> new TimeIntroPayload(buf.readVarInt()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(TimeIntroPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> TimeIntroClient.cue(payload.stage));
    }
}
