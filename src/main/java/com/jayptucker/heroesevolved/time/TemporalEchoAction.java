package com.jayptucker.heroesevolved.time;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/** A block action replayed by an echo using visual-only effects. */
public record TemporalEchoAction(
        int playbackTick,
        BlockPos position,
        ResourceLocation blockId,
        boolean placement
) {
    public static final Codec<TemporalEchoAction> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT.fieldOf("playback_tick")
                            .forGetter(TemporalEchoAction::playbackTick),
                    BlockPos.CODEC.fieldOf("position")
                            .forGetter(TemporalEchoAction::position),
                    ResourceLocation.CODEC.fieldOf("block_id")
                            .forGetter(TemporalEchoAction::blockId),
                    Codec.BOOL.fieldOf("placement")
                            .forGetter(TemporalEchoAction::placement)
            ).apply(instance, TemporalEchoAction::new));
}
