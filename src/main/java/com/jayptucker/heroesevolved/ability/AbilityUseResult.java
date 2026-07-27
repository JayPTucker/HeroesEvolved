package com.jayptucker.heroesevolved.ability;

public enum AbilityUseResult {
    SUCCESS,
    OVEREXERTED_SUCCESS,

    NO_ASSIGNED_POWER,
    ACTION_NOT_ASSIGNED,
    ACTION_LOCKED,

    UNKNOWN_ABILITY,
    NOT_UNLOCKED,
    ON_COOLDOWN,
    INSUFFICIENT_ENERGY,
    CANNOT_USE,
    ACTIVATION_REJECTED
}