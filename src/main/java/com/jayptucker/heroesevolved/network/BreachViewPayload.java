package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.vibe.BreachSnapshot;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

/**
 * What a viewer sees through one breach mouth: where the mouth stands here ({@code center}, turned to {@code yaw},
 * {@code normal} the way through it), where its other mouth stands ({@code farCenter}, {@code farYaw}, {@code farNormal})
 * and the terrain round that far mouth. A payload with no
 * snapshot ends the view (the breach shut).
 */
public record BreachViewPayload(int id, Vec3 center, float yaw, Vec3 normal, Vec3 farCenter, float farYaw, Vec3 farNormal,
                                int ticks, @Nullable BreachSnapshot snapshot) implements CustomPacketPayload {
    public static final Type<BreachViewPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "breach_view"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BreachViewPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.id);
                buf.writeBoolean(payload.snapshot != null);
                if (payload.snapshot == null) return;
                buf.writeVec3(payload.center);
                buf.writeFloat(payload.yaw);
                buf.writeVec3(payload.normal);
                buf.writeVec3(payload.farCenter);
                buf.writeFloat(payload.farYaw);
                buf.writeVec3(payload.farNormal);
                buf.writeVarInt(payload.ticks);
                payload.snapshot.write(buf);
            },
            buf -> {
                int id = buf.readVarInt();
                if (!buf.readBoolean()) return ended(id);
                return new BreachViewPayload(id, buf.readVec3(), buf.readFloat(), buf.readVec3(), buf.readVec3(), buf.readFloat(),
                        buf.readVec3(), buf.readVarInt(), BreachSnapshot.read(buf));
            });

    public static BreachViewPayload ended(int id) { return new BreachViewPayload(id, Vec3.ZERO, 0, Vec3.ZERO, Vec3.ZERO, 0, Vec3.ZERO, 0, null); }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(BreachViewPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.jayptucker.heroesevolved.client.effects.BreachViewClient.accept(payload));
    }
}
