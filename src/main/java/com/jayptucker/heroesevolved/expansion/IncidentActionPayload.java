package com.jayptucker.heroesevolved.expansion;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Requests carry no coordinates, rewards, target players or world edits. */
public record IncidentActionPayload(Operation operation) implements CustomPacketPayload {
    /** ENTER walks into the Annex; AVENUE into Odessa at 6:10; LEAVE returns from either. */
    public enum Operation { READ, ENTER, LEAVE, AVENUE }
    public static final Type<IncidentActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("heroesevolved", "incident_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, IncidentActionPayload> STREAM_CODEC = StreamCodec.of((b, p) -> b.writeEnum(p.operation), b -> new IncidentActionPayload(b.readEnum(Operation.class)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(IncidentActionPayload message, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer p)) return;
            if (message.operation == Operation.LEAVE) { if (!com.jayptucker.heroesevolved.story.AvenueService.leave(p)) FacilityService.leave(p); }
            else if (!com.jayptucker.heroesevolved.ability.service.PowerRules.canAct(p)) return;
            else if (message.operation == Operation.ENTER) FacilityService.enter(p);
            else if (message.operation == Operation.AVENUE) com.jayptucker.heroesevolved.story.AvenueService.enter(p);
            else {
                var d = FacilityData.get(p.getServer());
                boolean clue = d.discovered(p.getUUID(), "unfinished_hour") || p.getData(com.jayptucker.heroesevolved.data.ModDataAttachments.PLAYER_STORY_PROGRESS) >= 3
                        || p.getData(com.jayptucker.heroesevolved.data.ModDataAttachments.PLAYER_SEEN_FUTURE_CITY)
                        || p.getInventory().items.stream().anyMatch(PropheticPainting::isCanvas);
                if (!clue) { p.sendSystemMessage(net.minecraft.network.chat.Component.literal("A cartographer has been painting a place they cannot remember. Find one, or ask a precognitive to share their canvas.")); return; }
                if (FacilityService.plan(p) && FacilityData.get(p.getServer()).discover(p.getUUID(), "canvas_received")) PropheticPainting.give(p);
                p.sendSystemMessage(net.minecraft.network.chat.Component.literal(FacilityService.journal(p)));
            }
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, com.jayptucker.heroesevolved.network.StoryJournalPayload.of(p));
        });
    }
}
