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
 * The Last Day's thread of light for one player: the way from where they stand to where they should go next (the lobby,
 * the market, someone trapped, the stair, the hall), as the corners of a walkable route. Empty clears it.
 */
public record LastDayGuidePayload(List<Vec3> route) implements CustomPacketPayload {
    public static final Type<LastDayGuidePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "last_day_guide"));
    /** More corners than any route through Odessa needs. */
    public static final int MAX = 256;

    public static final StreamCodec<RegistryFriendlyByteBuf, LastDayGuidePayload> STREAM_CODEC = StreamCodec.of((buf, value) -> {
        int n = Math.min(MAX, value.route.size());
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            Vec3 v = value.route.get(i);
            buf.writeFloat((float) v.x); buf.writeFloat((float) v.y); buf.writeFloat((float) v.z);
        }
    }, buf -> {
        int n = Math.min(MAX, buf.readVarInt());
        List<Vec3> route = new ArrayList<>(n);
        for (int i = 0; i < n; i++) route.add(new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat()));
        return new LastDayGuidePayload(List.copyOf(route));
    });

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(LastDayGuidePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.jayptucker.heroesevolved.client.effects.intro.GuideArt.route(payload.route));
    }
}
