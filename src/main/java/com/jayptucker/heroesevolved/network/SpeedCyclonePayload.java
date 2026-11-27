package com.jayptucker.heroesevolved.network;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.speed.CycloneGeometry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * A tornado on the ground, for everyone near enough to see it: a speedster's Cyclone, or Flight's Tempest. Whose
 * it is, round where (a Tempest's moves as it is steered), its ring's radius, how long it has run, how long ago
 * its tornado began to rise (-1 if it has not), whether it has just ended, whether it turns without a runner on
 * its ring (an anchored Cyclone after its runner left, or a Tempest, whose flyer is seen as they are), and whose
 * colours it wears ({@link #SPEED} gold lightning, {@link #FLIGHT} sky-blue). A speedster's Cyclone also carries its
 * {@code laps}: the schedule the server carries the runner round the ring on, so every client draws them where they
 * are, as fast as they are running (null for every other style). A Tempest also carries {@code forming}: how many ticks
 * its flyer has lapped the eye to form the funnel (-1 before they start; its {@code tornadoAge} stays -1 until it has
 * formed).
 */
public record SpeedCyclonePayload(int entity, Vec3 center, float radius, int age, int tornadoAge, boolean ended, boolean free, int style,
                                  CycloneGeometry.Laps laps, int forming)
        implements CustomPacketPayload {
    /** A speedster's Cyclone, Flight's Tempest, and a speedster running the heat out of a radioactive player. */
    public static final int SPEED = 0, FLIGHT = 1, CONTAIN = 2;
    /** A containment run the heat has beaten: the runner slows to a stop on the ring and stands there. */
    public static final int CONTAIN_FAIL = 3;
    /** Hydrokinesis's Maelstrom: a waterspout, its funnel drawn as spray and green-blue water over a whirlpool. */
    public static final int HYDRO = 4;
    /**
     * An overload's supercell ({@code village/overload/Supercell}): nobody's, a storm's dark funnel. Its radius is the
     * ring times the storm's scale, so the client draws the shared shape {@code radius / OverloadGeometry.RING} times
     * its size.
     */
    public static final int STORM = 5;
    public static final Type<SpeedCyclonePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HeroesEvolved.MOD_ID, "speed_cyclone"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SpeedCyclonePayload> STREAM_CODEC = StreamCodec.of((buf, p) -> {
        buf.writeVarInt(p.entity);
        buf.writeDouble(p.center.x); buf.writeDouble(p.center.y); buf.writeDouble(p.center.z);
        buf.writeFloat(p.radius);
        buf.writeVarInt(p.age);
        buf.writeVarInt(p.tornadoAge + 1);
        buf.writeBoolean(p.ended);
        buf.writeBoolean(p.free);
        buf.writeVarInt(p.style);
        buf.writeBoolean(p.laps != null);
        if (p.laps != null) {
            buf.writeFloat(p.laps.from()); buf.writeFloat(p.laps.angle()); buf.writeFloat(p.laps.omega()); buf.writeFloat(p.laps.target());
        }
        buf.writeVarInt(p.forming + 1);
    }, buf -> new SpeedCyclonePayload(buf.readVarInt(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
            buf.readFloat(), buf.readVarInt(), buf.readVarInt() - 1, buf.readBoolean(), buf.readBoolean(), buf.readVarInt(),
            buf.readBoolean() ? new CycloneGeometry.Laps(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat()) : null,
            buf.readVarInt() - 1));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SpeedCyclonePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.jayptucker.heroesevolved.client.effects.SpeedCyclone.update(payload));
    }

    /** Tells everyone within sight of the ring (and the runner) how it stands. */
    public static void send(ServerLevel level, ServerPlayer runner, Vec3 center, float radius, int age, int tornadoAge, boolean ended,
                            boolean free, int style) {
        send(level, (net.minecraft.world.entity.Entity) runner, center, radius, age, tornadoAge, ended, free, style, null, -1);
    }

    /** A speedster's Cyclone, with the laps its runner is carried round on. */
    public static void send(ServerLevel level, ServerPlayer runner, Vec3 center, float radius, int age, int tornadoAge, boolean ended,
                            boolean free, int style, CycloneGeometry.Laps laps) {
        send(level, (net.minecraft.world.entity.Entity) runner, center, radius, age, tornadoAge, ended, free, style, laps, -1);
    }

    /** A Flight Tempest: how long its flyer has lapped the eye to form it ({@code forming}, -1 before they start). */
    public static void sendTempest(ServerLevel level, ServerPlayer flyer, Vec3 center, float radius, int age, int tornadoAge, boolean ended,
                                   int forming) {
        send(level, flyer, center, radius, age, tornadoAge, ended, true, FLIGHT, null, forming);
    }

    /** As above, for a tornado whose owner is not a player (an overload's, owned by the villager at its eye). */
    public static void send(ServerLevel level, net.minecraft.world.entity.Entity runner, Vec3 center, float radius, int age, int tornadoAge,
                            boolean ended, boolean free, int style) {
        send(level, runner, center, radius, age, tornadoAge, ended, free, style, null, -1);
    }

    private static void send(ServerLevel level, net.minecraft.world.entity.Entity runner, Vec3 center, float radius, int age, int tornadoAge,
                             boolean ended, boolean free, int style, CycloneGeometry.Laps laps, int forming) {
        SpeedCyclonePayload payload = new SpeedCyclonePayload(runner.getId(), center, radius, age, tornadoAge, ended, free, style, laps, forming);
        for (ServerPlayer viewer : level.players())
            if (viewer == runner || viewer.position().distanceToSqr(center) <= 160 * 160)
                PacketDistributor.sendToPlayer(viewer, payload);
    }
}
