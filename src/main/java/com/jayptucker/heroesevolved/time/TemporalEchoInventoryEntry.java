package com.jayptucker.heroesevolved.time;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;

/** A non-empty inventory slot captured with a Past echo. */
public record TemporalEchoInventoryEntry(int slot, ItemStack stack) {
    public static final Codec<TemporalEchoInventoryEntry> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT.fieldOf("slot")
                            .forGetter(TemporalEchoInventoryEntry::slot),
                    ItemStack.CODEC.fieldOf("stack")
                            .forGetter(TemporalEchoInventoryEntry::stack)
            ).apply(instance, TemporalEchoInventoryEntry::new));
}
