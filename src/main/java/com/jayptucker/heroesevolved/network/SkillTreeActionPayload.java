package com.jayptucker.heroesevolved.network;
import com.jayptucker.heroesevolved.ability.AbilitySlot;
import com.jayptucker.heroesevolved.skills.SkillTreeService;
import com.jayptucker.heroesevolved.ability.service.PowerFeedback;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
public record SkillTreeActionPayload(Operation operation, String node, AbilitySlot slot) implements CustomPacketPayload {
    public enum Operation { OPEN, BUY, EQUIP }
    public static final Type<SkillTreeActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("heroesevolved", "skill_tree_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SkillTreeActionPayload> STREAM_CODEC = StreamCodec.of(
            (b, v) -> { b.writeEnum(v.operation); b.writeUtf(v.node, 96); b.writeEnum(v.slot); },
            b -> new SkillTreeActionPayload(b.readEnum(Operation.class), b.readUtf(96), b.readEnum(AbilitySlot.class)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(SkillTreeActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            boolean accepted = switch (payload.operation) {
                case OPEN -> true;
                case BUY -> SkillTreeService.purchase(player, payload.node);
                case EQUIP -> SkillTreeService.equip(player, payload.slot, payload.node);
            };
            if (!accepted) PowerFeedback.reject(player, "Cannot change that skill. Check its level, prerequisite and available points.");
            SkillTreeService.sync(player);
        });
    }
}
