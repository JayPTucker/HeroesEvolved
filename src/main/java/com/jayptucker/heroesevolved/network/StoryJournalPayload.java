package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
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
        List<String> memories,
        int memoryTotal,
        boolean seenFutureCity,
        int temporalMilestones,
        int sylarChapter,
        int sylarDefeats,
        List<String> sylarLearned,
        String incident
) implements CustomPacketPayload {
    public static final Type<StoryJournalPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "story_journal"));

    private static final StreamCodec<RegistryFriendlyByteBuf, List<String>> STRINGS =
            ByteBufCodecs.stringUtf8(512).apply(ByteBufCodecs.list(64)).cast();

    public static final StreamCodec<RegistryFriendlyByteBuf, StoryJournalPayload> STREAM_CODEC =
            StreamCodec.of((buffer, payload) -> {
                STRINGS.encode(buffer, payload.memories);
                buffer.writeVarInt(payload.memoryTotal);
                buffer.writeBoolean(payload.seenFutureCity);
                buffer.writeVarInt(payload.temporalMilestones);
                buffer.writeVarInt(payload.sylarChapter);
                buffer.writeVarInt(payload.sylarDefeats);
                STRINGS.encode(buffer, payload.sylarLearned);
                buffer.writeUtf(payload.incident, 1024);
            }, buffer -> new StoryJournalPayload(STRINGS.decode(buffer), buffer.readVarInt(),
                    buffer.readBoolean(), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readVarInt(), STRINGS.decode(buffer), buffer.readUtf(1024)));

    /** Gathers everything the journal shows for this player. */
    public static StoryJournalPayload of(ServerPlayer player) {
        SylarStoryData sylar = SylarStoryData.get(player.getServer());
        return new StoryJournalPayload(
                FutureVisions.heard(player),
                FutureVisions.fragmentCount(),
                player.getData(ModDataAttachments.PLAYER_SEEN_FUTURE_CITY.get()),
                player.getData(ModDataAttachments.TEMPORAL_STORY),
                sylar.trail(player.getUUID()).chapter,
                sylar.defeats(),
                sylar.learned().stream().map(ResourceLocation::toString).sorted().toList(),
                com.jayptucker.heroesevolved.expansion.FacilityService.journal(player)
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
