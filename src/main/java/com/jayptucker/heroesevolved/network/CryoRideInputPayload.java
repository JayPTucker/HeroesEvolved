package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.cryo.CryoRideService;
import com.jayptucker.heroesevolved.telepathy.PossessionService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Input intent; the server owns ice placement, collision, speed and the ride's height limit. */
public record CryoRideInputPayload(float forward, float strafe, boolean rise, boolean brake, boolean sprint, int scroll)
        implements CustomPacketPayload {
    public static final Type<CryoRideInputPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "cryo_ride_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CryoRideInputPayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> { b.writeFloat(p.forward); b.writeFloat(p.strafe); b.writeBoolean(p.rise); b.writeBoolean(p.brake); b.writeBoolean(p.sprint); b.writeVarInt(p.scroll); },
            b -> new CryoRideInputPayload(b.readFloat(), b.readFloat(), b.readBoolean(), b.readBoolean(), b.readBoolean(), b.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(CryoRideInputPayload p, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sender)) return;
            ServerPlayer actor = PossessionService.actionActor(sender);
            if (actor == null) return;
            CryoRideService.input(actor, p.forward, p.strafe, p.rise, p.brake, p.sprint);
            if (p.scroll != 0) CryoRideService.changeSpeed(actor, Integer.signum(p.scroll));
        });
    }
}
