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
 * An Empathic Mimic's memory, for their memory wheel: the sets they carry (the near one first, then the kept ones),
 * which is channeled, how many can be kept and their Power Level.
 */
public record MimicMemoryPayload(List<Slot> slots, int selected, int capacity, int level) implements CustomPacketPayload {
    /** One set: the power's path, who it came from, what they were, whether it was kept by touch, and their id. */
    public record Slot(String power, String name, String kind, boolean kept, String origin) {}

    public static final Type<MimicMemoryPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "mimic_memory"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MimicMemoryPayload> STREAM_CODEC = StreamCodec.of(
            (b, v) -> {
                b.writeVarInt(v.slots.size());
                for (Slot s : v.slots) {
                    b.writeUtf(s.power, 64); b.writeUtf(s.name, 64); b.writeUtf(s.kind, 16); b.writeBoolean(s.kept); b.writeUtf(s.origin, 40);
                }
                b.writeVarInt(v.selected + 1); b.writeVarInt(v.capacity); b.writeVarInt(v.level);
            },
            b -> {
                int count = Math.min(16, b.readVarInt());
                List<Slot> slots = new ArrayList<>();
                for (int i = 0; i < count; i++) slots.add(new Slot(b.readUtf(64), b.readUtf(64), b.readUtf(16), b.readBoolean(), b.readUtf(40)));
                return new MimicMemoryPayload(List.copyOf(slots), b.readVarInt() - 1, b.readVarInt(), b.readVarInt());
            });

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(MimicMemoryPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientMimicMemory.set(payload));
    }
}
