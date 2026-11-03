package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.pyro.PyroFlightService;
import com.jayptucker.heroesevolved.telepathy.PossessionService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Input intent only: the server chooses speed, fuel consumption and the controlled body. */
public record PyroFlightInputPayload(float forward, float strafe, boolean rise, boolean descend, boolean boost)
        implements CustomPacketPayload {
    public static final Type<PyroFlightInputPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "pyro_flight_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PyroFlightInputPayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> { b.writeFloat(p.forward); b.writeFloat(p.strafe); b.writeBoolean(p.rise); b.writeBoolean(p.descend); b.writeBoolean(p.boost); },
            b -> new PyroFlightInputPayload(b.readFloat(), b.readFloat(), b.readBoolean(), b.readBoolean(), b.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(PyroFlightInputPayload p, IPayloadContext c) {
        c.enqueueWork(() -> {
            if (c.player() instanceof ServerPlayer sender) {
                ServerPlayer actor = PossessionService.actionActor(sender);
                if (actor != null) PyroFlightService.input(actor, p.forward, p.strafe, p.rise, p.descend, p.boost);
            }
        });
    }
}
