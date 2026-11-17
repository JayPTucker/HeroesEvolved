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

/** Assigns one of eight radial positions, or pins the wheel action used by a tap. */
public record BindWheelPayload(String power, int slot, String actionKey) implements CustomPacketPayload {
    public static final Type<BindWheelPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "bind_wheel"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BindWheelPayload> STREAM_CODEC = StreamCodec.of(
            (b, v) -> { b.writeUtf(v.power, 96); b.writeVarInt(v.slot); b.writeUtf(v.actionKey, 96); },
            b -> new BindWheelPayload(b.readUtf(96), b.readVarInt(), b.readUtf(96)));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(BindWheelPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (SkillTreeService.bindWheel(player, payload.power, payload.slot, payload.actionKey)) SkillTreeService.sync(player);
            else PowerFeedback.reject(player, "That ability can't go there on the wheel.");
        });
    }
}
