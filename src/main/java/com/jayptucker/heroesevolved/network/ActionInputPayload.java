package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.service.AbilityUseService;
import com.jayptucker.heroesevolved.ability.service.PowerFeedback;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * An ability key (1 to 8) or the ability wheel pressed or let go. A key sends its index; the wheel
 * sends key 0 and the action key it chose. The server decides what that key or action actually is.
 */
public record ActionInputPayload(int key, String actionKey, boolean down, boolean overexert) implements CustomPacketPayload {
    public static final Type<ActionInputPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "action_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ActionInputPayload> STREAM_CODEC = StreamCodec.of(
            (b, v) -> { b.writeVarInt(v.key); b.writeUtf(v.actionKey, 96); b.writeBoolean(v.down); b.writeBoolean(v.overexert); },
            b -> new ActionInputPayload(b.readVarInt(), b.readUtf(96), b.readBoolean(), b.readBoolean()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ActionInputPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || payload.key < 0 || payload.key > 8) return;
            if (payload.down) {
                var result = payload.key == 0
                        ? AbilityUseService.activateAction(player, payload.actionKey, payload.overexert)
                        : AbilityUseService.activateKey(player, payload.key, payload.overexert);
                PowerFeedback.report(player, result);
            } else if (payload.key == 0) AbilityUseService.releaseAction(player, payload.actionKey);
            else AbilityUseService.releaseKey(player, payload.key);
        });
    }
}
