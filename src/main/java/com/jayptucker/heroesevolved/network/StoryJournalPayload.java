package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.progression.StoryDiscoveries;
import com.jayptucker.heroesevolved.story.AvenueService;
import com.jayptucker.heroesevolved.story.StoryLead;
import com.jayptucker.heroesevolved.story.sylar.SylarStoryData;
import com.jayptucker.heroesevolved.telepathy.FutureVisions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/** One player's story progress, sent when they open the journal. */
public record StoryJournalPayload(
        String lead,
        List<String> records,
        int recordTotal,
        List<String> memories,
        int memoryTotal,
        boolean seenFutureCity,
        int temporalMilestones,
        int sylarChapter,
        int sylarDefeats,
        List<String> sylarLearned,
        String incident,
        boolean avenueOpen,
        String avenue,
        boolean nightOpen,
        String night
) implements CustomPacketPayload {
    public static final Type<StoryJournalPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "story_journal"));

    private static final StreamCodec<RegistryFriendlyByteBuf, List<String>> STRINGS =
            ByteBufCodecs.stringUtf8(512).apply(ByteBufCodecs.list(64)).cast();

    public static final StreamCodec<RegistryFriendlyByteBuf, StoryJournalPayload> STREAM_CODEC =
            StreamCodec.of((buffer, payload) -> {
                buffer.writeUtf(payload.lead, 512);
                STRINGS.encode(buffer, payload.records);
                buffer.writeVarInt(payload.recordTotal);
                STRINGS.encode(buffer, payload.memories);
                buffer.writeVarInt(payload.memoryTotal);
                buffer.writeBoolean(payload.seenFutureCity);
                buffer.writeVarInt(payload.temporalMilestones);
                buffer.writeVarInt(payload.sylarChapter);
                buffer.writeVarInt(payload.sylarDefeats);
                STRINGS.encode(buffer, payload.sylarLearned);
                buffer.writeUtf(payload.incident, 1024);
                buffer.writeBoolean(payload.avenueOpen);
                buffer.writeUtf(payload.avenue, 1024);
                buffer.writeBoolean(payload.nightOpen);
                buffer.writeUtf(payload.night, 1024);
            }, buffer -> new StoryJournalPayload(buffer.readUtf(512), STRINGS.decode(buffer),
                    buffer.readVarInt(), STRINGS.decode(buffer), buffer.readVarInt(),
                    buffer.readBoolean(), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readVarInt(), STRINGS.decode(buffer), buffer.readUtf(1024),
                    buffer.readBoolean(), buffer.readUtf(1024),
                    buffer.readBoolean(), buffer.readUtf(1024)));

    /** Gathers everything the journal shows for this player. */
    public static StoryJournalPayload of(ServerPlayer player) {
        SylarStoryData sylar = SylarStoryData.get(player.getServer());
        return new StoryJournalPayload(
                StoryLead.next(player),
                StoryDiscoveries.recovered(player),
                StoryDiscoveries.ORDER.size(),
                FutureVisions.heard(player),
                FutureVisions.fragmentCount(),
                player.getData(ModDataAttachments.PLAYER_SEEN_FUTURE_CITY.get()),
                player.getData(ModDataAttachments.TEMPORAL_STORY),
                sylar.trail(player.getUUID()).chapter,
                sylar.defeats(),
                sylar.learned().stream().map(ResourceLocation::toString).sorted().toList(),
                com.jayptucker.heroesevolved.expansion.FacilityService.journal(player),
                AvenueService.unlocked(player),
                AvenueService.journal(player),
                com.jayptucker.heroesevolved.story.night.NightThreeService.unlocked(player),
                com.jayptucker.heroesevolved.story.night.NightThreeService.journal(player)
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(StoryJournalPayload payload, IPayloadContext context) {
        ClientJournalState.update(payload);
    }
}
