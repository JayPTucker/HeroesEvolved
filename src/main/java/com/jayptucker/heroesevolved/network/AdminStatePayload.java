package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.UUID;

/** Everything the admin menu displays, sent only to operators. */
public record AdminStatePayload(
        boolean openScreen,
        boolean eclipseActive,
        int maximumLevel,
        List<String> powers,
        List<PlayerEntry> players
) implements CustomPacketPayload {
    public record PlayerEntry(
            UUID id,
            String name,
            String powerId,
            boolean awakened,
            int level,
            long mastery,
            long masteryForNextLevel,
            int energy,
            int maximumEnergy,
            boolean unlimitedEnergy
    ) {
        public static final StreamCodec<RegistryFriendlyByteBuf, PlayerEntry>
                STREAM_CODEC = StreamCodec.of(
                        (buffer, entry) -> {
                            UUIDUtil.STREAM_CODEC.encode(buffer, entry.id());
                            buffer.writeUtf(entry.name(), 64);
                            buffer.writeUtf(entry.powerId(), 256);
                            buffer.writeBoolean(entry.awakened());
                            buffer.writeVarInt(entry.level());
                            buffer.writeVarLong(entry.mastery());
                            buffer.writeVarLong(entry.masteryForNextLevel());
                            buffer.writeVarInt(entry.energy());
                            buffer.writeVarInt(entry.maximumEnergy());
                            buffer.writeBoolean(entry.unlimitedEnergy());
                        },
                        buffer -> new PlayerEntry(
                                UUIDUtil.STREAM_CODEC.decode(buffer),
                                buffer.readUtf(64),
                                buffer.readUtf(256),
                                buffer.readBoolean(),
                                buffer.readVarInt(),
                                buffer.readVarLong(),
                                buffer.readVarLong(),
                                buffer.readVarInt(),
                                buffer.readVarInt(),
                                buffer.readBoolean()
                        )
                );
    }

    public static final Type<AdminStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    HeroesEvolved.MOD_ID,
                    "admin_state"
            ));

    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            AdminStatePayload
    > STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            AdminStatePayload::openScreen,
            ByteBufCodecs.BOOL,
            AdminStatePayload::eclipseActive,
            ByteBufCodecs.VAR_INT,
            AdminStatePayload::maximumLevel,
            ByteBufCodecs.stringUtf8(256).apply(ByteBufCodecs.list(64)),
            AdminStatePayload::powers,
            PlayerEntry.STREAM_CODEC.apply(ByteBufCodecs.list(512)),
            AdminStatePayload::players,
            AdminStatePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(
            AdminStatePayload payload,
            IPayloadContext context
    ) {
        ClientAdminState.update(payload);
    }
}
