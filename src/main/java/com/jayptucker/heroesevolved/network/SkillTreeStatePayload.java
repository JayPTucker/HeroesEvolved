package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.skills.*;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.*;

public record SkillTreeStatePayload(String power, boolean awakened, int level, SkillTreeData.Build build) implements CustomPacketPayload {
    public static final Type<SkillTreeStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("heroesevolved", "skill_tree_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SkillTreeStatePayload> STREAM_CODEC = StreamCodec.of(
            (b, v) -> {
                b.writeUtf(v.power, 96); b.writeBoolean(v.awakened); b.writeVarInt(v.level); b.writeVarInt(v.build.highestLevel()); b.writeVarInt(v.build.training());
                b.writeVarInt(v.build.learned().size()); v.build.learned().stream().sorted().forEach(id -> b.writeUtf(id, 96));
                b.writeVarInt(v.build.slots().size()); v.build.slots().forEach((slot, id) -> { b.writeUtf(slot, 24); b.writeUtf(id, 96); });
            }, b -> {
                String power = b.readUtf(96); boolean awakened = b.readBoolean(); int level = b.readVarInt(), highest = b.readVarInt(); int training = b.readVarInt();
                if (training < 0 || training > 6) throw new IllegalArgumentException("Invalid training");
                if (level < 1 || level > 100 || highest < 1 || highest > 100) throw new IllegalArgumentException("Invalid skill level");
                int count = b.readVarInt(); if (count < 0 || count > 90) throw new IllegalArgumentException("Invalid skill count");
                Set<String> learned = new HashSet<>(); for (int i = 0; i < count; i++) learned.add(b.readUtf(96));
                count = b.readVarInt(); if (count < 0 || count > 5) throw new IllegalArgumentException("Invalid loadout");
                Map<String, String> slots = new HashMap<>(); for (int i = 0; i < count; i++) slots.put(b.readUtf(24), b.readUtf(96));
                return new SkillTreeStatePayload(power, awakened, level, new SkillTreeData.Build(highest, learned, slots, training));
            });
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(SkillTreeStatePayload state, IPayloadContext context) { context.enqueueWork(() -> ClientSkillTreeState.update(state)); }
    public static void send(ServerPlayer player) {
        var assigned = PlayerAbilityService.getData(player).assignedPower();
        PacketDistributor.sendToPlayer(player, assigned.map(e -> new SkillTreeStatePayload(e.getKey().getPath(),
                e.getValue().isUnlocked(), PlayerProgressionService.getLevel(player), SkillTreeService.build(player, e.getKey())))
                .orElseGet(() -> new SkillTreeStatePayload("", false, 1, SkillTreeData.Build.empty())));
    }
}
