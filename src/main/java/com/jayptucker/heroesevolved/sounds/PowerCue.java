package com.jayptucker.heroesevolved.sounds;

import java.util.List;
import java.util.Set;

/**
 * The kinds of signature sound each power has, built by {@code tools/generate-power-audio.py} into
 * {@code sounds/power/<power>/} and registered as {@code heroesevolved:power.<power>.<cue>}. Plain data, so that
 * {@link ModSounds} can register them without loading anything else.
 */
public enum PowerCue {
    /** A light move: most presses and skill-tree actives. */
    CAST(0.8F),
    /** An ultimate, a class ability or a signature move. */
    HEAVY(1.5F),
    /** A wind-up that a key release finishes. */
    CHARGE(0.9F),
    /** A power's blow landing, and most free follow-ups. */
    IMPACT(0.8F),
    /** A held effect let go or dismissed. */
    END(0.6F),
    /** A fighting-style blow. */
    STRIKE(0.5F),
    /** A fighting-style dodge. */
    DODGE(0.55F);

    /** How loud this cue plays; above 1 it also carries further (16 blocks per unit of volume). */
    public final float volume;

    PowerCue(float volume) {
        this.volume = volume;
    }

    public String id() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    /** Every power with a palette (the paths of their ability ids). */
    public static final List<String> POWERS = List.of(
            "cryokinesis", "electrokinesis", "flight", "hydrokinesis", "invisibility", "matter_assembly",
            "empathic_mimicry", "phasing", "pyrokinesis", "quake", "radiation", "regeneration", "shapeshifting",
            "sonic_resonance", "superspeed", "telekinesis", "telepathy", "time_manipulation", "vibe");

    /** Powers with a fighting style, which alone have STRIKE and DODGE cues. */
    public static final Set<String> STYLED = Set.of(
            "cryokinesis", "electrokinesis", "hydrokinesis", "invisibility", "phasing", "pyrokinesis", "quake",
            "regeneration", "superspeed", "telekinesis");

    public static boolean has(String power, PowerCue cue) {
        if (!POWERS.contains(power)) return false;
        return (cue != STRIKE && cue != DODGE) || STYLED.contains(power);
    }

    /** The sound event's path, as sounds.json names it. */
    public static String event(String power, PowerCue cue) {
        return "power." + power + "." + cue.id();
    }
}
