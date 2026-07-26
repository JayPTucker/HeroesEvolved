package com.jayptucker.heroesevolved.combat;

public record PlayerCombatData(long lastDamageGameTime) {
    public static PlayerCombatData empty() {
        return new PlayerCombatData(-1);
    }

    public boolean wasDamagedWithin(long currentGameTime, long delayTicks) {
        return lastDamageGameTime >= 0
            && currentGameTime - lastDamageGameTime < delayTicks;
    }
}