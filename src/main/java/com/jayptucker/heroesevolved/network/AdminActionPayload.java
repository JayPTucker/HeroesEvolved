package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.admin.AdminService;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * An operator's request from the admin menu. The server re-checks
 * permission for every request; the client is never trusted.
 */
public record AdminActionPayload(
        Action action,
        UUID target,
        String argument,
        int value
) implements CustomPacketPayload {
    public enum Action {
        OPEN,
        REFRESH,
        GIVE_POWER,
        REMOVE_POWER,
        AWAKEN,
        SET_LEVEL,
        ADD_MASTERY,
        RESET_PROGRESS,
        REFILL_ENERGY,
        TOGGLE_UNLIMITED_ENERGY,
        RESET_COOLDOWNS,
        FULL_HEAL,
        START_ECLIPSE,
        STOP_ECLIPSE
    }

    public static final Type<AdminActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    HeroesEvolved.MOD_ID,
                    "admin_action"
            ));

    private static final StreamCodec<RegistryFriendlyByteBuf, Action>
            ACTION_CODEC = ByteBufCodecs.VAR_INT.map(
                    index -> {
                        Action[] actions = Action.values();
                        if (index < 0 || index >= actions.length) {
                            throw new IllegalArgumentException(
                                    "Unknown admin action: " + index
                            );
                        }
                        return actions[index];
                    },
                    Action::ordinal
            ).cast();

    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            AdminActionPayload
    > STREAM_CODEC = StreamCodec.composite(
            ACTION_CODEC,
            AdminActionPayload::action,
            UUIDUtil.STREAM_CODEC.cast(),
            AdminActionPayload::target,
            ByteBufCodecs.stringUtf8(256).cast(),
            AdminActionPayload::argument,
            ByteBufCodecs.VAR_INT.cast(),
            AdminActionPayload::value,
            AdminActionPayload::new
    );

    public static AdminActionPayload of(Action action, UUID target) {
        return new AdminActionPayload(action, target, "", 0);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(
            AdminActionPayload payload,
            IPayloadContext context
    ) {
        if (context.player() instanceof ServerPlayer player) {
            AdminService.handle(player, payload);
        }
    }
}
