package com.jayptucker.heroesevolved.cryo;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Saved atomically in the same chunk as the temporary block, including on unexpected shutdown. */
public record FrostRecovery(BlockPos pos, BlockState original, BlockState placed, long expires) {
    public static final Codec<FrostRecovery> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.fieldOf("pos").forGetter(FrostRecovery::pos),
            BlockState.CODEC.fieldOf("original").forGetter(FrostRecovery::original),
            BlockState.CODEC.fieldOf("placed").forGetter(FrostRecovery::placed),
            Codec.LONG.fieldOf("expires").forGetter(FrostRecovery::expires)
    ).apply(i, FrostRecovery::new));
}
