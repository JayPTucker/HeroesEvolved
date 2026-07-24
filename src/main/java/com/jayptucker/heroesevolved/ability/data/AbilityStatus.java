package com.jayptucker.heroesevolved.ability.data;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

public enum AbilityStatus implements StringRepresentable {
    DORMANT("dormant"),
    UNLOCKED("unlocked");

    public static final Codec<AbilityStatus> CODEC =
        StringRepresentable.fromEnum(AbilityStatus::values);

    private final String serializedName;

    AbilityStatus(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
