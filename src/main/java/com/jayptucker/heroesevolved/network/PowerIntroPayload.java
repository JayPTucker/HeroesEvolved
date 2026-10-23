package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.gui.TimeIntroCaption;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The existing compact cinematic caption style is shared by all awakenings. */
public record PowerIntroPayload(ResourceLocation power, int phase) implements CustomPacketPayload {
    public static final Type<PowerIntroPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            HeroesEvolved.MOD_ID, "power_intro"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PowerIntroPayload> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> { buf.writeResourceLocation(value.power); buf.writeVarInt(value.phase); },
            buf -> new PowerIntroPayload(buf.readResourceLocation(), buf.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(PowerIntroPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (payload.phase < 0) TimeIntroCaption.clear();
            else TimeIntroCaption.showKey("intro.heroesevolved." + payload.power.getPath()
                    + (payload.phase == 0 ? ".charge" : ".release"));
        });
    }
}
