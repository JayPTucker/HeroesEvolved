package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.service.PowerFeedback;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Puts an action on ability key 1 to 8. An empty action key leaves the key empty. */
public record BindKeyPayload(int key, String actionKey) implements CustomPacketPayload {
    public static final Type<BindKeyPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "bind_key"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BindKeyPayload> STREAM_CODEC = StreamCodec.of(
            (b, v) -> { b.writeVarInt(v.key); b.writeUtf(v.actionKey, 96); },
            b -> new BindKeyPayload(b.readVarInt(), b.readUtf(96)));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(BindKeyPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (SkillTreeService.bind(player, payload.key, payload.actionKey)) SkillTreeService.sync(player);
            else PowerFeedback.reject(player, "That ability can't go on that key yet.");
        });
    }
}
