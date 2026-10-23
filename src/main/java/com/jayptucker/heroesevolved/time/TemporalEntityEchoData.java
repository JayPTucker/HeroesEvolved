package com.jayptucker.heroesevolved.time;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;

import java.util.Arrays;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * A non-player entity as it existed when a snapshot was made, plus its
 * recorded minute of movement.
 *
 * <p>Snapshots can hold dozens of mobs, so frames are packed into one int
 * array instead of a list of compound tags. Each frame is
 * {@link #FRAME_STRIDE} ints: playback tick, x, y and z in 1/32 block
 * fixed-point, then yaw, pitch and head yaw as raw float bits.</p>
 */
public record TemporalEntityEchoData(
        UUID entityId,
        CompoundTag entity,
        int[] frames
) {
    public static final int FRAME_STRIDE = 7;
    private static final double POSITION_SCALE = 32.0D;

    private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(
            UUID::fromString,
            UUID::toString
    );

    private static final Codec<int[]> INT_ARRAY_CODEC = Codec.INT_STREAM.xmap(
            IntStream::toArray,
            Arrays::stream
    );

    public static final Codec<TemporalEntityEchoData> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    UUID_CODEC.fieldOf("entity_id")
                            .forGetter(TemporalEntityEchoData::entityId),
                    CompoundTag.CODEC.fieldOf("entity")
                            .forGetter(TemporalEntityEchoData::entity),
                    INT_ARRAY_CODEC.optionalFieldOf("frames", new int[0])
                            .forGetter(TemporalEntityEchoData::frames)
            ).apply(instance, TemporalEntityEchoData::new));

    public int frameCount() {
        return frames.length / FRAME_STRIDE;
    }

    public int playbackTick(int frame) {
        return frames[frame * FRAME_STRIDE];
    }

    public double x(int frame) {
        return frames[frame * FRAME_STRIDE + 1] / POSITION_SCALE;
    }

    public double y(int frame) {
        return frames[frame * FRAME_STRIDE + 2] / POSITION_SCALE;
    }

    public double z(int frame) {
        return frames[frame * FRAME_STRIDE + 3] / POSITION_SCALE;
    }

    public float yaw(int frame) {
        return Float.intBitsToFloat(frames[frame * FRAME_STRIDE + 4]);
    }

    public float pitch(int frame) {
        return Float.intBitsToFloat(frames[frame * FRAME_STRIDE + 5]);
    }

    public float headYaw(int frame) {
        return Float.intBitsToFloat(frames[frame * FRAME_STRIDE + 6]);
    }

    /** Appends one frame to a packed frame buffer. */
    public static void packFrame(
            int[] destination,
            int frame,
            int playbackTick,
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            float headYaw
    ) {
        int offset = frame * FRAME_STRIDE;
        destination[offset] = playbackTick;
        destination[offset + 1] = (int) Math.round(x * POSITION_SCALE);
        destination[offset + 2] = (int) Math.round(y * POSITION_SCALE);
        destination[offset + 3] = (int) Math.round(z * POSITION_SCALE);
        destination[offset + 4] = Float.floatToIntBits(yaw);
        destination[offset + 5] = Float.floatToIntBits(pitch);
        destination[offset + 6] = Float.floatToIntBits(headYaw);
    }
}
