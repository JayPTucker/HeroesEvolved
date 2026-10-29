package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.client.BodyVisuals;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * A regenerating body as everyone around sees it: whether it is down on the ground, and for each limb
 * (right arm, left arm, right leg, left leg) how many ticks until it starts growing back (negative once
 * it has started) and how long it takes. A zero grow time means the limb is whole.
 */
public record BodyStatePayload(int entityId, UUID uuid, boolean downed, int[] growStart, int[] growTicks)
        implements CustomPacketPayload {
    public static final Type<BodyStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "body_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BodyStatePayload> STREAM_CODEC = StreamCodec.of(
            (b, v) -> {
                b.writeVarInt(v.entityId); b.writeUUID(v.uuid); b.writeBoolean(v.downed);
                for (int i = 0; i < 4; i++) { b.writeInt(v.growStart[i]); b.writeVarInt(v.growTicks[i]); }
            },
            b -> {
                int id = b.readVarInt(); UUID uuid = b.readUUID(); boolean downed = b.readBoolean();
                int[] starts = new int[4], grows = new int[4];
                for (int i = 0; i < 4; i++) { starts[i] = b.readInt(); grows[i] = Math.clamp(b.readVarInt(), 0, 20 * 60 * 10); }
                return new BodyStatePayload(id, uuid, downed, starts, grows);
            });

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(BodyStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> BodyVisuals.update(payload));
    }
}
