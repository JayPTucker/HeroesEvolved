package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The stamina bar alone, sent often and small (a full power sync carries the whole loadout). The client
 * runs the bar forward between packets from the rates, so it moves smoothly at 20 ticks a second.
 *
 * @param stamina   current stamina, fractional
 * @param maximum   the bar's size
 * @param regen     stamina recovered per second right now (0 while paused)
 * @param drain     stamina held powers are taking per second right now
 * @param flags     {@link #PAUSED}, {@link #CALM}, {@link #HELD}, {@link #UNLIMITED}, and {@link #REFUSED} once when a move
 *                  was refused for lack of stamina
 * @param pauseTicks ticks left before recovery starts again
 */
public record StaminaPayload(float stamina, int maximum, float regen, float drain, int flags, int pauseTicks)
        implements CustomPacketPayload {
    public static final int PAUSED = 1, CALM = 2, HELD = 4, UNLIMITED = 8, REFUSED = 16;
    public static final Type<StaminaPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "stamina"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StaminaPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, StaminaPayload::stamina,
            ByteBufCodecs.VAR_INT, StaminaPayload::maximum,
            ByteBufCodecs.FLOAT, StaminaPayload::regen,
            ByteBufCodecs.FLOAT, StaminaPayload::drain,
            ByteBufCodecs.VAR_INT, StaminaPayload::flags,
            ByteBufCodecs.VAR_INT, StaminaPayload::pauseTicks,
            StaminaPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(StaminaPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientStamina.receive(payload));
    }
}
