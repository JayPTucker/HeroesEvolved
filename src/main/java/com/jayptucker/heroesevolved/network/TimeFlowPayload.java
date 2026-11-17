package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.time.TimeFlow;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Where time is slowed or stopped and who is moving through it faster than the world ({@link TimeFlow}). Sent to every
 * player when it changes and once a second while any field lasts; an empty one clears it.
 */
public record TimeFlowPayload(boolean everywhere, List<Field> fields, List<Mover> movers) implements CustomPacketPayload {
    /** One field. A following field's centre is its owner's (the client reads it from the owner when it can). */
    public record Field(int owner, ResourceLocation dimension, Vec3 center, float radius, int interval, boolean follows) {}

    /** A player moving faster than the world: {@link TimeFlow#SPEED} or {@link TimeFlow#TIME}. */
    public record Mover(int entity, int style) {}

    public static final Type<TimeFlowPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "time_flow"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TimeFlowPayload> STREAM_CODEC = StreamCodec.of((buf, p) -> {
        buf.writeBoolean(p.everywhere);
        buf.writeVarInt(p.fields.size());
        for (Field f : p.fields) {
            buf.writeVarInt(f.owner);
            buf.writeResourceLocation(f.dimension);
            buf.writeDouble(f.center.x); buf.writeDouble(f.center.y); buf.writeDouble(f.center.z);
            buf.writeFloat(f.radius);
            buf.writeVarInt(f.interval);
            buf.writeBoolean(f.follows);
        }
        buf.writeVarInt(p.movers.size());
        for (Mover m : p.movers) { buf.writeVarInt(m.entity); buf.writeByte(m.style); }
    }, buf -> {
        boolean everywhere = buf.readBoolean();
        int count = Math.min(buf.readVarInt(), 256);
        List<Field> fields = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            fields.add(new Field(buf.readVarInt(), buf.readResourceLocation(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                    buf.readFloat(), buf.readVarInt(), buf.readBoolean()));
        int moving = Math.min(buf.readVarInt(), 1024);
        List<Mover> movers = new ArrayList<>(moving);
        for (int i = 0; i < moving; i++) movers.add(new Mover(buf.readVarInt(), buf.readByte()));
        return new TimeFlowPayload(everywhere, List.copyOf(fields), List.copyOf(movers));
    });

    public static TimeFlowPayload of(boolean everywhere, List<TimeFlow.Field> fields, List<Mover> movers) {
        List<Field> sent = new ArrayList<>(fields.size());
        for (TimeFlow.Field f : fields)
            sent.add(new Field(f.ownerEntity(), f.dimension().location(), f.center(), (float) f.radius(), f.interval(), f.follows()));
        return new TimeFlowPayload(everywhere, List.copyOf(sent), List.copyOf(movers));
    }

    /**
     * The same picture, give or take where a following field's owner has walked to (the client follows them itself), so
     * a field that moves with its owner isn't re-sent every tick.
     */
    public boolean sameAs(TimeFlowPayload other) {
        if (other == null || everywhere != other.everywhere || fields.size() != other.fields.size() || !movers.equals(other.movers)) return false;
        for (int i = 0; i < fields.size(); i++) {
            Field a = fields.get(i), b = other.fields.get(i);
            if (a.owner != b.owner || !a.dimension.equals(b.dimension) || a.radius != b.radius || a.interval != b.interval
                    || a.follows != b.follows || !a.follows && !Objects.equals(a.center, b.center)) return false;
        }
        return true;
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(TimeFlowPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.jayptucker.heroesevolved.client.ClientTimeFlow.accept(payload));
    }
}
