package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.flight.FlightSkillService;
import com.jayptucker.heroesevolved.telepathy.PossessionService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** A double-tapped strafe key while flying, intent only: {@code side} -1 is left, 1 is right. The server decides. */
public record FlightRollPayload(int side) implements CustomPacketPayload {
    public static final Type<FlightRollPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "flight_roll"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FlightRollPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, FlightRollPayload::side, FlightRollPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(FlightRollPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sender)) return;
            ServerPlayer actor = PossessionService.actionActor(sender);
            if (actor != null) FlightSkillService.requestRoll(actor, payload.side);
        });
    }
}
