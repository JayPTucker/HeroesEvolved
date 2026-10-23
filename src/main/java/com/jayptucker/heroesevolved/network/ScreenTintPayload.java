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

/** Asks one client to tint its screen for a while; purely visual. */
public record ScreenTintPayload(
        int color,
        int durationTicks,
        Style style
) implements CustomPacketPayload {
    public enum Style {
        /** Even wash over the whole screen, fading out. */
        FLASH,
        /** Darkened edges, fading out. */
        VIGNETTE,
        /** Edges that throb steadily for the whole duration. */
        PULSE,
        /** Even wash held steady, fading only at the very end. */
        HOLD,
        /** Darkened edges held steady, fading only at the very end. */
        EDGE
    }

    public static final Type<ScreenTintPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    HeroesEvolved.MOD_ID,
                    "screen_tint"
            ));

    private static final StreamCodec<RegistryFriendlyByteBuf, Style> STYLE_CODEC =
            ByteBufCodecs.VAR_INT.map(
                    index -> Style.values()[Math.floorMod(index, Style.values().length)],
                    Style::ordinal
            ).cast();

    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            ScreenTintPayload
    > STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT,
            ScreenTintPayload::color,
            ByteBufCodecs.VAR_INT,
            ScreenTintPayload::durationTicks,
            STYLE_CODEC,
            ScreenTintPayload::style,
            ScreenTintPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(
            ScreenTintPayload payload,
            IPayloadContext context
    ) {
        ClientScreenTintState.add(payload);
    }

    /** @param argb colour with alpha as the tint's peak strength */
    public static void send(
            ServerPlayer player,
            int argb,
            int durationTicks,
            Style style
    ) {
        PacketDistributor.sendToPlayer(
                player,
                new ScreenTintPayload(argb, durationTicks, style)
        );
    }

    public static void sendAround(
            ServerLevel level,
            Vec3 center,
            double radius,
            int argb,
            int durationTicks,
            Style style
    ) {
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceToSqr(center) <= radius * radius) {
                send(player, argb, durationTicks, style);
            }
        }
    }
}
