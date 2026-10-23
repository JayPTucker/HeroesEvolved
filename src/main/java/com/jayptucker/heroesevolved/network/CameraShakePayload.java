package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Asks one client to shake its camera; purely visual. */
public record CameraShakePayload(
        float intensity,
        int durationTicks
) implements CustomPacketPayload {
    public static final Type<CameraShakePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    HeroesEvolved.MOD_ID,
                    "camera_shake"
            ));

    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            CameraShakePayload
    > STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT,
            CameraShakePayload::intensity,
            ByteBufCodecs.VAR_INT,
            CameraShakePayload::durationTicks,
            CameraShakePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(
            CameraShakePayload payload,
            IPayloadContext context
    ) {
        ClientCameraShakeState.start(
                payload.intensity(),
                payload.durationTicks()
        );
    }

    /**
     * Shakes every player near a tremor. Intensity falls off linearly with
     * distance, so bystanders feel a quake without being blinded by it.
     */
    public static void sendAround(
            ServerLevel level,
            Vec3 center,
            double radius,
            float intensity,
            int durationTicks
    ) {
        double radiusSquared = radius * radius;
        for (ServerPlayer player : level.players()) {
            double distanceSquared = player.position().distanceToSqr(center);
            if (distanceSquared > radiusSquared) {
                continue;
            }

            float falloff = (float) (1.0D - Math.sqrt(distanceSquared) / radius);
            float scaled = intensity * Math.max(0.15F, falloff);
            PacketDistributor.sendToPlayer(
                    player,
                    new CameraShakePayload(scaled, durationTicks)
            );
        }
    }
}
