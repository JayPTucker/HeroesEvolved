package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * A viber's frequencies (breach waypoints), sent when the Breach key (or Glimpse) opens the frequency wheel.
 * {@code slots} is how many they can hold at their Power Level, {@code most} the most any viber can, {@code nextLevel}
 * the Power Level that adds the next one (0 at the top), and {@code worlds} whether they can reach other dimensions.
 */
public record FrequencyListPayload(int mode, List<Entry> entries, int slots, int most, int nextLevel, int level,
                                   boolean worlds, int aimRange) implements CustomPacketPayload {
    public static final int BREACH = 0, GLIMPSE = 1;

    /** One saved frequency. {@code distance} is in blocks, or -1 in another dimension. */
    public record Entry(int slot, String name, String biome, String dimension, int x, int y, int z, int distance,
                        boolean reachable) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.of(
                (buf, e) -> {
                    buf.writeVarInt(e.slot);
                    buf.writeUtf(e.name, 64);
                    buf.writeUtf(e.biome, 128);
                    buf.writeUtf(e.dimension, 128);
                    buf.writeVarInt(e.x);
                    buf.writeVarInt(e.y);
                    buf.writeVarInt(e.z);
                    buf.writeVarInt(e.distance);
                    buf.writeBoolean(e.reachable);
                },
                buf -> new Entry(buf.readVarInt(), buf.readUtf(64), buf.readUtf(128), buf.readUtf(128), buf.readVarInt(),
                        buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));
    }

    public static final Type<FrequencyListPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "frequency_list"));
    private static final StreamCodec<RegistryFriendlyByteBuf, List<Entry>> ENTRIES = Entry.STREAM_CODEC.apply(ByteBufCodecs.list(16));
    public static final StreamCodec<RegistryFriendlyByteBuf, FrequencyListPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.mode);
                ENTRIES.encode(buf, p.entries);
                buf.writeVarInt(p.slots);
                buf.writeVarInt(p.most);
                buf.writeVarInt(p.nextLevel);
                buf.writeVarInt(p.level);
                buf.writeBoolean(p.worlds);
                buf.writeVarInt(p.aimRange);
            },
            buf -> new FrequencyListPayload(buf.readVarInt(), ENTRIES.decode(buf), buf.readVarInt(), buf.readVarInt(),
                        buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readVarInt()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(FrequencyListPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.jayptucker.heroesevolved.gui.screen.FrequencyWheelScreen.open(payload));
    }
}
