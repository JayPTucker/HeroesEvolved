package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * The player's own power at a glance: Power Level and its progress, the Evolved trial, story points,
 * every ability they can cast (for the wheel and the Abilities page) and what is on each of the eight keys.
 */
public record LoadoutPayload(int level, long masteryInto, long masteryNeeded, boolean evolved, String trial, int trialProgress,
                             int trialGoal, int storyPoints, List<Entry> entries, List<String> keys) implements CustomPacketPayload {
    /**
     * One castable ability. {@code actionKey} is how it is cast and bound; {@code path} its action id;
     * {@code unlockLevel} the Power Level it unlocks at; {@code cooldownEnd} in game time.
     */
    public record Entry(String actionKey, String path, String nameKey, boolean unlocked, int unlockLevel, int energy,
                        int cooldownTicks, long cooldownEnd, int rank, int uses) {}

    public static final Type<LoadoutPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "loadout"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LoadoutPayload> STREAM_CODEC = StreamCodec.of(
            (b, v) -> {
                b.writeVarInt(v.level); b.writeVarLong(v.masteryInto); b.writeVarLong(v.masteryNeeded); b.writeBoolean(v.evolved);
                b.writeUtf(v.trial, 200); b.writeVarInt(v.trialProgress); b.writeVarInt(v.trialGoal); b.writeVarInt(v.storyPoints);
                b.writeVarInt(v.entries.size());
                for (Entry e : v.entries) {
                    b.writeUtf(e.actionKey, 96); b.writeUtf(e.path, 96); b.writeUtf(e.nameKey, 160); b.writeBoolean(e.unlocked);
                    b.writeVarInt(e.unlockLevel); b.writeVarInt(e.energy); b.writeVarInt(e.cooldownTicks); b.writeVarLong(e.cooldownEnd);
                    b.writeVarInt(e.rank); b.writeVarInt(e.uses);
                }
                b.writeVarInt(v.keys.size());
                for (String key : v.keys) b.writeUtf(key, 96);
            },
            b -> {
                int level = b.readVarInt(); long into = b.readVarLong(), needed = b.readVarLong(); boolean evolved = b.readBoolean();
                String trial = b.readUtf(200); int progress = b.readVarInt(), goal = b.readVarInt(), story = b.readVarInt();
                int count = Math.min(64, b.readVarInt());
                List<Entry> entries = new ArrayList<>();
                for (int i = 0; i < count; i++) entries.add(new Entry(b.readUtf(96), b.readUtf(96), b.readUtf(160), b.readBoolean(),
                        b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarLong(), b.readVarInt(), b.readVarInt()));
                int keys = Math.min(8, b.readVarInt());
                List<String> bound = new ArrayList<>();
                for (int i = 0; i < keys; i++) bound.add(b.readUtf(96));
                return new LoadoutPayload(level, into, needed, evolved, trial, progress, goal, story, List.copyOf(entries), List.copyOf(bound));
            });

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(LoadoutPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientLoadout.update(payload));
    }
}
