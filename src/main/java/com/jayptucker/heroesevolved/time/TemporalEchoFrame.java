package com.jayptucker.heroesevolved.time;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** One sampled moment in a player's one-minute temporal recording. */
public record TemporalEchoFrame(
        int playbackTick,
        double x,
        double y,
        double z,
        float yaw,
        float pitch
) {
    public static final Codec<TemporalEchoFrame> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT.fieldOf("playback_tick")
                            .forGetter(TemporalEchoFrame::playbackTick),
                    Codec.DOUBLE.fieldOf("x").forGetter(TemporalEchoFrame::x),
                    Codec.DOUBLE.fieldOf("y").forGetter(TemporalEchoFrame::y),
                    Codec.DOUBLE.fieldOf("z").forGetter(TemporalEchoFrame::z),
                    Codec.FLOAT.fieldOf("yaw").forGetter(TemporalEchoFrame::yaw),
                    Codec.FLOAT.fieldOf("pitch")
                            .forGetter(TemporalEchoFrame::pitch)
            ).apply(instance, TemporalEchoFrame::new));
}
