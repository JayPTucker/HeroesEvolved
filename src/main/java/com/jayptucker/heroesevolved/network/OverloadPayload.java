package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * An overload's storm, for one player near enough to see it ({@code village/overload/Overloads}): whose it is (the
 * villager at the centre), which storm, where the eye is, where the drawing aims besides (the funnel's next stop, or
 * Touchdown's far end), how much of the countdown has run, how hard it blows, how calm they are, its stage and how long
 * it has been in it, how long it has run, whether this player sees the eye through it, who is calming them, and whether
 * it has ended. Sent every five ticks; drawn by {@code OverloadArt} from {@code ClientOverloads}.
 */
public record OverloadPayload(int entity, int storm, Vec3 eye, Vec3 target, float progress, float power, float calm,
                              int stage, int stageAge, int age, boolean sees, int[] helpers, boolean ended)
        implements CustomPacketPayload {
    public static final Type<OverloadPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "overload"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OverloadPayload> STREAM_CODEC = StreamCodec.of((buf, p) -> {
        buf.writeVarInt(p.entity);
        buf.writeVarInt(p.storm);
        buf.writeDouble(p.eye.x); buf.writeDouble(p.eye.y); buf.writeDouble(p.eye.z);
        buf.writeDouble(p.target.x); buf.writeDouble(p.target.y); buf.writeDouble(p.target.z);
        buf.writeFloat(p.progress);
        buf.writeFloat(p.power);
        buf.writeFloat(p.calm);
        buf.writeVarInt(p.stage);
        buf.writeVarInt(p.stageAge);
        buf.writeVarInt(p.age);
        buf.writeBoolean(p.sees);
        buf.writeVarInt(Math.min(p.helpers.length, 8));
        for (int i = 0; i < Math.min(p.helpers.length, 8); i++) buf.writeVarInt(p.helpers[i]);
        buf.writeBoolean(p.ended);
    }, buf -> {
        int entity = buf.readVarInt(), storm = buf.readVarInt();
        Vec3 eye = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        Vec3 target = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        float progress = buf.readFloat(), power = buf.readFloat(), calm = buf.readFloat();
        int stage = buf.readVarInt(), stageAge = buf.readVarInt(), age = buf.readVarInt();
        boolean sees = buf.readBoolean();
        int[] helpers = new int[Math.min(buf.readVarInt(), 8)];
        for (int i = 0; i < helpers.length; i++) helpers[i] = buf.readVarInt();
        return new OverloadPayload(entity, storm, eye, target, progress, power, calm, stage, stageAge, age, sees, helpers, buf.readBoolean());
    });

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(OverloadPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.jayptucker.heroesevolved.client.ClientOverloads.accept(payload));
    }
}
