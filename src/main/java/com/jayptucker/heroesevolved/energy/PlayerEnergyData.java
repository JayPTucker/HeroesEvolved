package com.jayptucker.heroesevolved.energy;

import com.mojang.serialization.Codec;

public record PlayerEnergyData(int energy) {
    public static final Codec<PlayerEnergyData> CODEC =
        Codec.INT.xmap(PlayerEnergyData::new, PlayerEnergyData::energy);

    public PlayerEnergyData {
        if (energy < 0) {
            throw new IllegalArgumentException("Energy cannot be negative.");
        }
    }

    public static PlayerEnergyData initial() {
        return new PlayerEnergyData(100);
    }

    public PlayerEnergyData withEnergy (int newEnergy) {
        return new PlayerEnergyData(newEnergy);
    }
}
