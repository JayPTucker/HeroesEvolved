package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.speed.SpeedService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** A speedster right-clicked a shot in flight; the server checks reach and aim before catching it. */
public record SpeedCatchPayload(int entityId) implements CustomPacketPayload {
    public static final Type<SpeedCatchPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "speed_catch"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SpeedCatchPayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(SpeedCatchPayload::new, SpeedCatchPayload::entityId).cast();

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SpeedCatchPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            SpeedService.catchShot(player, player.serverLevel().getEntity(payload.entityId()));
        }
    }
}
