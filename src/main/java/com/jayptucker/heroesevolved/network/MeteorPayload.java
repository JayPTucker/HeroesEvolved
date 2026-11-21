package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * A meteor in the sky ({@code meteor/Meteors}), for everyone who can see it: whether it is a great one, where it started
 * and where it will land (which moves as it is pushed), how far into its fall it is and how long the fall is, whether it
 * is held this moment, how far it is cracked and the ground under it braced, who is working on it and how (by
 * {@code Meteor.Help} ordinal), and whether it has gone. Sent every five ticks; drawn by {@code MeteorArt} from
 * {@code ClientMeteors}, which runs it forward between packets.
 */
public record MeteorPayload(int id, boolean great, Vec3 start, Vec3 land, int elapsed, int fall, boolean held, float cracked,
                            float braced, int[] helpers, int[] kinds, boolean ended) implements CustomPacketPayload {
    public static final Type<MeteorPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "meteor"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MeteorPayload> STREAM_CODEC = StreamCodec.of((buf, p) -> {
        buf.writeVarInt(p.id);
        buf.writeBoolean(p.great);
        buf.writeDouble(p.start.x); buf.writeDouble(p.start.y); buf.writeDouble(p.start.z);
        buf.writeDouble(p.land.x); buf.writeDouble(p.land.y); buf.writeDouble(p.land.z);
        buf.writeVarInt(p.elapsed);
        buf.writeVarInt(p.fall);
        buf.writeBoolean(p.held);
        buf.writeFloat(p.cracked);
        buf.writeFloat(p.braced);
        int n = Math.min(Math.min(p.helpers.length, p.kinds.length), 8);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) { buf.writeVarInt(p.helpers[i]); buf.writeVarInt(p.kinds[i]); }
        buf.writeBoolean(p.ended);
    }, buf -> {
        int id = buf.readVarInt();
        boolean great = buf.readBoolean();
        Vec3 start = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        Vec3 land = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        int elapsed = buf.readVarInt(), fall = buf.readVarInt();
        boolean held = buf.readBoolean();
        float cracked = buf.readFloat(), braced = buf.readFloat();
        int n = Math.min(buf.readVarInt(), 8);
        int[] helpers = new int[n], kinds = new int[n];
        for (int i = 0; i < n; i++) { helpers[i] = buf.readVarInt(); kinds[i] = buf.readVarInt(); }
        return new MeteorPayload(id, great, start, land, elapsed, fall, held, cracked, braced, helpers, kinds, buf.readBoolean());
    });

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(MeteorPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.jayptucker.heroesevolved.client.ClientMeteors.accept(payload));
    }
}
