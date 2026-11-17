package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.combat.Takedowns;
import com.jayptucker.heroesevolved.telepathy.PossessionService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The Finisher key, intent only: {@code FINISH} the foe with this entity id, or {@code TOGGLE} finishers on or off
 * (Shift + the key). The server checks the foe, the reach, the cost and the cooldown again.
 */
public record FinisherPayload(int kind, int target) implements CustomPacketPayload {
    public static final int FINISH = 0, TOGGLE = 1;
    public static final Type<FinisherPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "finisher"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FinisherPayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> { b.writeVarInt(p.kind); b.writeVarInt(p.target + 1); },
            b -> new FinisherPayload(b.readVarInt(), b.readVarInt() - 1));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(FinisherPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sender)) return;
            ServerPlayer actor = PossessionService.actionActor(sender);
            if (actor == null) return;
            if (payload.kind == TOGGLE) Takedowns.toggle(actor);
            else if (payload.kind == FINISH && payload.target >= 0) Takedowns.request(actor, payload.target);
        });
    }
}
