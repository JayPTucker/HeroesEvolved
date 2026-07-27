package com.jayptucker.heroesevolved.ability;

public enum AbilityUseResult {
    SUCCESS,
    OVEREXERTED_SUCCESS,

    // Failure reasons allow a future HUD or chat message to explain why use failed.
    UNKNOWN_ABILITY,
    NOT_UNLOCKED,
    ON_COOLDOWN,
    INSUFFICIENT_ENERGY,
    CANNOT_USE,
    ACTIVATION_REJECTED
}