package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record PlayerPowerSyncPayload(
        int energy,
        int maximumEnergy,
        List<AbilitySnapshot> abilities
) implements CustomPacketPayload {
    public static final Type<PlayerPowerSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    HeroesEvolved.MOD_ID,
                    "player_power_sync"
            ));

    private static final StreamCodec<
            RegistryFriendlyByteBuf,
            List<AbilitySnapshot>
    > ABILITY_LIST_STREAM_CODEC = StreamCodec.of(
            (buffer, abilities) -> {
                buffer.writeVarInt(abilities.size());

                for (AbilitySnapshot ability : abilities) {
                    AbilitySnapshot.STREAM_CODEC.encode(buffer, ability);
                }
            },
            buffer -> {
                int size = buffer.readVarInt();

                if (size < 0 || size > 256) {
                    throw new IllegalArgumentException(
                            "Invalid ability snapshot count: " + size
                    );
                }

                List<AbilitySnapshot> abilities = new ArrayList<>(size);

                for (int index = 0; index < size; index++) {
                    abilities.add(AbilitySnapshot.STREAM_CODEC.decode(buffer));
                }

                return List.copyOf(abilities);
            }
    );

    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            PlayerPowerSyncPayload
    > STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            PlayerPowerSyncPayload::energy,
            ByteBufCodecs.VAR_INT,
            PlayerPowerSyncPayload::maximumEnergy,
            ABILITY_LIST_STREAM_CODEC,
            PlayerPowerSyncPayload::abilities,
            PlayerPowerSyncPayload::new
    );

    public PlayerPowerSyncPayload {
        if (energy < 0) {
            throw new IllegalArgumentException("Energy cannot be negative.");
        }

        if (maximumEnergy < 0) {
            throw new IllegalArgumentException("Maximum energy cannot be negative.");
        }

        abilities = List.copyOf(abilities);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(
            PlayerPowerSyncPayload payload,
            IPayloadContext context
    ) {
        ClientPowerState.update(payload);
    }
}