package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.invisibility.OpticalDecoyService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Tests a nearby optical silhouette; the server checks the real line of sight and reach. */
public record DecoyInteractPayload(int id) implements CustomPacketPayload {
    public static final Type<DecoyInteractPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "decoy_interact"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DecoyInteractPayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.<RegistryFriendlyByteBuf>cast().map(DecoyInteractPayload::new, DecoyInteractPayload::id);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(DecoyInteractPayload packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sender) {
                ServerPlayer actor = com.jayptucker.heroesevolved.telepathy.PossessionService.actionActor(sender);
                if (actor != null) OpticalDecoyService.dissolve(actor, packet.id);
            }
        });
    }
}
