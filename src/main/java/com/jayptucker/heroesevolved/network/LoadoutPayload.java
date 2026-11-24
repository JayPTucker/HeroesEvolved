package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.skills.Loadout;
import com.jayptucker.heroesevolved.skills.WheelLoadout;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * The player's own power at a glance: Power Level and its progress, the Evolved trial, story points,
 * every ability they can cast, the direct key bindings and the saved wheel layout. {@code innate} holds skills every
 * holder has with no key or wheel position (Flight's Barrel Roll), so the HUD can still show their cooldown.
 */
public record LoadoutPayload(int level, long masteryInto, long masteryNeeded, boolean evolved, String trial, int trialProgress,
                             int trialGoal, int storyPoints, List<Entry> entries, List<String> keys,
                             String power, List<String> wheel, String quickCast, List<Entry> innate) implements CustomPacketPayload {
    public static final LoadoutPayload EMPTY = new LoadoutPayload(1, 0, 1, false, "", 0, 1, 0,
            List.of(), List.of(), "", List.of(), "", List.of());
    /**
     * One castable ability. {@code actionKey} is how it is cast and bound; {@code path} its action id;
     * {@code unlockLevel} the Power Level it unlocks at; {@code cooldownEnd} in game time.
     */
    public record Entry(String actionKey, String path, String nameKey, boolean unlocked, int unlockLevel, int energy,
                        int cooldownTicks, long cooldownEnd, int rank, int uses,
                        int alternateCooldownTicks, long alternateCooldownEnd) {
        public Entry(String actionKey, String path, String nameKey, boolean unlocked, int unlockLevel, int energy,
                     int cooldownTicks, long cooldownEnd, int rank, int uses) {
            this(actionKey, path, nameKey, unlocked, unlockLevel, energy, cooldownTicks, cooldownEnd, rank, uses, 0, 0);
        }
    }

    public static final Type<LoadoutPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "loadout"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LoadoutPayload> STREAM_CODEC = StreamCodec.of(
            (b, v) -> {
                b.writeVarInt(v.level); b.writeVarLong(v.masteryInto); b.writeVarLong(v.masteryNeeded); b.writeBoolean(v.evolved);
                b.writeUtf(v.trial, 200); b.writeVarInt(v.trialProgress); b.writeVarInt(v.trialGoal); b.writeVarInt(v.storyPoints);
                writeEntries(b, v.entries);
                b.writeVarInt(v.keys.size());
                for (String key : v.keys) b.writeUtf(key, 96);
                b.writeUtf(v.power, 96);
                b.writeVarInt(v.wheel.size());
                for (String slot : v.wheel) b.writeUtf(slot, 96);
                b.writeUtf(v.quickCast, 96);
                writeEntries(b, v.innate);
            },
            b -> {
                int level = b.readVarInt(); long into = b.readVarLong(), needed = b.readVarLong(); boolean evolved = b.readBoolean();
                String trial = b.readUtf(200); int progress = b.readVarInt(), goal = b.readVarInt(), story = b.readVarInt();
                List<Entry> entries = readEntries(b);
                int keys = Math.min(Loadout.KEYS, b.readVarInt());
                List<String> bound = new ArrayList<>();
                for (int i = 0; i < keys; i++) bound.add(b.readUtf(96));
                String power = b.readUtf(96);
                int slots = Math.min(WheelLoadout.SLOTS, b.readVarInt());
                List<String> wheel = new ArrayList<>();
                for (int i = 0; i < slots; i++) wheel.add(b.readUtf(96));
                return new LoadoutPayload(level, into, needed, evolved, trial, progress, goal, story,
                        entries, List.copyOf(bound), power, List.copyOf(wheel), b.readUtf(96), readEntries(b));
            });

    private static void writeEntries(RegistryFriendlyByteBuf b, List<Entry> entries) {
        b.writeVarInt(entries.size());
        for (Entry e : entries) {
            b.writeUtf(e.actionKey, 96); b.writeUtf(e.path, 96); b.writeUtf(e.nameKey, 160); b.writeBoolean(e.unlocked);
            b.writeVarInt(e.unlockLevel); b.writeVarInt(e.energy); b.writeVarInt(e.cooldownTicks); b.writeVarLong(e.cooldownEnd);
            b.writeVarInt(e.rank); b.writeVarInt(e.uses);
            b.writeVarInt(e.alternateCooldownTicks); b.writeVarLong(e.alternateCooldownEnd);
        }
    }

    private static List<Entry> readEntries(RegistryFriendlyByteBuf b) {
        int count = Math.min(64, b.readVarInt());
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < count; i++) entries.add(new Entry(b.readUtf(96), b.readUtf(96), b.readUtf(160), b.readBoolean(),
                b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarLong(), b.readVarInt(), b.readVarInt(),
                b.readVarInt(), b.readVarLong()));
        return List.copyOf(entries);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(LoadoutPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientLoadout.update(payload));
    }
}
