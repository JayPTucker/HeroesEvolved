package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.combat.FightingStyles;
import com.jayptucker.heroesevolved.telepathy.PossessionService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Fighting-style input (firebending, lightning, waterbending), intent only. {@code STRIKE} is an empty-handed left
 * click ({@code value} is the entity the crosshair was on, or -1, so an exhausted fighter still lands a plain punch);
 * {@code DODGE} is a double-tapped direction ({@code value}: 0 left, 1 right, 2 back). The server picks the style from
 * the player's power, picks the move, pays for it and times it.
 */
public record FightingStylePayload(int kind, int value) implements CustomPacketPayload {
    public static final int STRIKE = 0, DODGE = 1;
    public static final Type<FightingStylePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "fighting_style"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FightingStylePayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> { b.writeVarInt(p.kind); b.writeVarInt(p.value); },
            b -> new FightingStylePayload(b.readVarInt(), b.readVarInt()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(FightingStylePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sender)) return;
            ServerPlayer actor = PossessionService.actionActor(sender);
            if (actor == null) return;
            if (payload.kind == STRIKE) FightingStyles.strikeInput(actor, payload.value);
            else if (payload.kind == DODGE && payload.value >= 0 && payload.value <= 2)
                FightingStyles.dodge(actor, FightingStyles.Dodge.values()[payload.value]);
        });
    }
}
