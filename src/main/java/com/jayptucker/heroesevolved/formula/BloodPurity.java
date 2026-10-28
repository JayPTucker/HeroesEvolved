package com.jayptucker.heroesevolved.formula;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Where a Formula's catalyst came from. It travels from the blood sample, through the
 * Centrifuge and Synthesis Station, to the finished vial, and sets the injection's odds.
 *
 * @param donated true when the Regeneration owner consented
 * @param donorLevel the donor's power level when the blood was drawn
 * @param donor the donor's name, for the tooltip
 */
public record BloodPurity(boolean donated, int donorLevel, String donor) {
    public static final Codec<BloodPurity> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.fieldOf("donated").forGetter(BloodPurity::donated),
            Codec.INT.fieldOf("donor_level").forGetter(BloodPurity::donorLevel),
            Codec.STRING.fieldOf("donor").forGetter(BloodPurity::donor)
    ).apply(instance, BloodPurity::new));

    public static final StreamCodec<ByteBuf, BloodPurity> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, BloodPurity::donated,
            ByteBufCodecs.VAR_INT, BloodPurity::donorLevel,
            ByteBufCodecs.STRING_UTF8, BloodPurity::donor,
            BloodPurity::new);

    public Component describe() {
        return Component.translatable(donated ? "formula.heroesevolved.purity.donated" : "formula.heroesevolved.purity.forced",
                donor, donorLevel).withStyle(donated ? ChatFormatting.AQUA : ChatFormatting.DARK_RED);
    }
}
