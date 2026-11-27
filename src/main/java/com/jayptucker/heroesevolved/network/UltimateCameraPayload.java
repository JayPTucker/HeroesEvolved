package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Your Ultimate's camera path began ({@code ticks} long), or ended ({@code ticks} 0). Each key is a tick, an eye and a
 * point to look at; a key with a {@code follow} body looks at that body, and with {@code eyeFollows} keeps its eye at
 * that offset from it. Sent to the caster only; drawn by {@code client/UltimateCamera}.
 */
public record UltimateCameraPayload(int player, int ticks, List<Key> keys) implements CustomPacketPayload {
    public record Key(int tick, Vec3 eye, Vec3 look, int follow, boolean eyeFollows) {}

    public static final Type<UltimateCameraPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "ultimate_camera"));
    public static final StreamCodec<RegistryFriendlyByteBuf, UltimateCameraPayload> STREAM_CODEC = StreamCodec.of((b, p) -> {
        b.writeVarInt(p.player);
        b.writeVarInt(p.ticks);
        b.writeVarInt(Math.min(32, p.keys.size()));
        for (int i = 0; i < Math.min(32, p.keys.size()); i++) {
            Key k = p.keys.get(i);
            b.writeVarInt(k.tick);
            b.writeDouble(k.eye.x); b.writeDouble(k.eye.y); b.writeDouble(k.eye.z);
            b.writeDouble(k.look.x); b.writeDouble(k.look.y); b.writeDouble(k.look.z);
            b.writeVarInt(k.follow + 1);
            b.writeBoolean(k.eyeFollows);
        }
    }, b -> {
        int player = b.readVarInt(), ticks = b.readVarInt(), n = Math.min(32, b.readVarInt());
        List<Key> keys = new ArrayList<>(n);
        for (int i = 0; i < n; i++)
            keys.add(new Key(b.readVarInt(), new Vec3(b.readDouble(), b.readDouble(), b.readDouble()),
                    new Vec3(b.readDouble(), b.readDouble(), b.readDouble()), b.readVarInt() - 1, b.readBoolean()));
        return new UltimateCameraPayload(player, ticks, keys);
    });

    /** The path is over: the view comes back. */
    public static UltimateCameraPayload end(int player) { return new UltimateCameraPayload(player, 0, List.of()); }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(UltimateCameraPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.jayptucker.heroesevolved.client.UltimateCamera.play(payload));
    }
}
