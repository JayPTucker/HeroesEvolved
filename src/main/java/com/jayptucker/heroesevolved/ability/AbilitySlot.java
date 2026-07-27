package com.jayptucker.heroesevolved.ability;

public enum AbilitySlot {
    PRIMARY("key.heroesevolved.ability_1"),
    SECONDARY("key.heroesevolved.ability_2"),
    TERTIARY("key.heroesevolved.ability_3");

    private final String translationKey;

    AbilitySlot(String translationKey) {
        this.translationKey = translationKey;
    }

    public String translationKey() {
        return translationKey;
    }
}