package com.jayptucker.heroesevolved.ability.data;

import java.util.Objects;

public record AbilityProgress(
    AbilityStatus status,
    int level,
    int mastery
) {

    public AbilityProgress {
        Objects.requireNonNull(status, "Status cannot be null.");

        if (level < 1) {
            throw new IllegalArgumentException("Ability level must be at least one.");
        }
        if (mastery < 0) {
            throw new IllegalArgumentException("Mastery cannot be negative.");
        }
        if (status == AbilityStatus.DORMANT && mastery != 0) {
            throw new IllegalArgumentException("Dormant abilities cannot have mastery.");
        }
    }

    public static AbilityProgress dormant() {
        return new AbilityProgress(AbilityStatus.DORMANT, 1, 0);
    }

    public boolean isUnlocked() {
        return status == AbilityStatus.UNLOCKED;
    }

    public AbilityProgress unlock() {
        if (isUnlocked()) {
            return this;
        }

        return new AbilityProgress(AbilityStatus.UNLOCKED, level, mastery);
    }

    public AbilityProgress gainMastery(int amount) {
        if (!isUnlocked()) {
            throw new IllegalStateException("Cannot gain mastery for a dormant ability.");
        }

        if (amount <= 0) {
            throw new IllegalArgumentException("Mastery gain must be positive.");
        }

        return new AbilityProgress(status, level, mastery + amount);
    }

    public AbilityProgress withLevel(int newLevel) {
        if (!isUnlocked()) {
            throw new IllegalStateException("Cannot level a dormant ability");
        }

        return new AbilityProgress(status, newLevel, mastery);
    }
}
