package com.jayptucker.heroesevolved.energy;

import com.mojang.serialization.Codec;

/**
 * A player's stamina. It is fractional, so held powers can drain a little every tick and recovery can be
 * smooth; {@link #energy()} is the whole-point value most code reads. Saves from before fractional stamina
 * held a whole number, which the double codec reads unchanged.
 */
public record PlayerEnergyData(double stamina) {
    public static final Codec<PlayerEnergyData> CODEC =
        Codec.DOUBLE.xmap(PlayerEnergyData::new, PlayerEnergyData::stamina);

    public PlayerEnergyData {
        if (!(stamina >= 0)) stamina = 0;
    }

    public static PlayerEnergyData initial() {
        return new PlayerEnergyData(100);
    }

    /** Whole points of stamina. */
    public int energy() {
        return (int) Math.floor(stamina + 1e-6);
    }

    public PlayerEnergyData withEnergy(double newEnergy) {
        return new PlayerEnergyData(newEnergy);
    }
}
