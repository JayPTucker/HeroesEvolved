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
        List<AbilitySnapshot> abilities,
        List<AbilityActionSnapshot> actions
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

                if (size < 0 || size > 1) {
                    throw new IllegalArgumentException(
                            "Invalid assigned-power snapshot count: " + size
                    );
                }

                List<AbilitySnapshot> abilities = new ArrayList<>(size);

                for (int index = 0; index < size; index++) {
                    abilities.add(AbilitySnapshot.STREAM_CODEC.decode(buffer));
                }

                return List.copyOf(abilities);
            }
    );

    private static final StreamCodec<
            RegistryFriendlyByteBuf,
            List<AbilityActionSnapshot>
    > ACTION_LIST_STREAM_CODEC = StreamCodec.of(
            (buffer, actions) -> {
                buffer.writeVarInt(actions.size());

                for (AbilityActionSnapshot action : actions) {
                    AbilityActionSnapshot.STREAM_CODEC.encode(buffer, action);
                }
            },
            buffer -> {
                int size = buffer.readVarInt();

                if (size < 0 || size > 3) {
                    throw new IllegalArgumentException(
                            "Invalid action snapshot count: " + size
                    );
                }

                List<AbilityActionSnapshot> actions = new ArrayList<>(size);

                for (int index = 0; index < size; index++) {
                    actions.add(
                            AbilityActionSnapshot.STREAM_CODEC.decode(buffer)
                    );
                }

                return List.copyOf(actions);
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
            ACTION_LIST_STREAM_CODEC,
            PlayerPowerSyncPayload::actions,
            PlayerPowerSyncPayload::new
    );

    public PlayerPowerSyncPayload {
        if (energy < 0 || maximumEnergy < 0) {
            throw new IllegalArgumentException(
                    "Energy values cannot be negative."
            );
        }

        abilities = List.copyOf(abilities);
        actions = List.copyOf(actions);
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