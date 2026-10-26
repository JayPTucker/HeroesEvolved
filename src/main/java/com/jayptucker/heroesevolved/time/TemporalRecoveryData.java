package com.jayptucker.heroesevolved.time;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.item.ItemStack;
import java.util.List;
import java.util.UUID;

/** Escrow is saved in the same player file as the inventory it changes. */
public record TemporalRecoveryData(UUID owner, List<ItemStack> removed, int remaining, boolean invisible) {
    public static TemporalRecoveryData empty() { return new TemporalRecoveryData(new UUID(0, 0), List.of(), 0, false); }
    public static final Codec<TemporalRecoveryData> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("owner").forGetter(TemporalRecoveryData::owner),
            ItemStack.CODEC.listOf().fieldOf("removed").forGetter(TemporalRecoveryData::removed),
            Codec.INT.fieldOf("remaining").forGetter(TemporalRecoveryData::remaining),
            Codec.BOOL.fieldOf("invisible").forGetter(TemporalRecoveryData::invisible)
    ).apply(i, TemporalRecoveryData::new));
}
