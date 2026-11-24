package com.jayptucker.heroesevolved.guidance;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Personal hint preferences and receipts, saved with the player and copied on death. */
public record GuidanceData(boolean enabled, long seen) {
    public static final Codec<GuidanceData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(GuidanceData::enabled),
            Codec.LONG.optionalFieldOf("seen", 0L).forGetter(GuidanceData::seen)
    ).apply(i, GuidanceData::new));

    // These numbers are save data: append new hints without changing existing IDs.
    public enum Hint {
        WELCOME(0, "start"), AWAKENED(1, "controls"), STAMINA(2, "growth"),
        SKILLS(3, "growth"), STYLE(4, "combat"), PORTAL(5, "portal"),
        DISTRESS(6, "world"), EVOLVE(7, "growth");

        public final int id;
        public final String topic;
        Hint(int id, String topic) { this.id = id; this.topic = topic; }
        public String key() { return "guide.heroesevolved.hint." + name().toLowerCase(java.util.Locale.ROOT); }
        public static Hint byId(int id) {
            for (Hint hint : values()) if (hint.id == id) return hint;
            return null;
        }
    }

    public static GuidanceData empty() { return new GuidanceData(true, 0); }
    /** Observations use existing client state; no second set of gameplay eligibility rules. */
    public record Context(boolean portal, boolean awake, boolean needsRest, boolean distress,
                          boolean skills, boolean styled, boolean evolve) {}

    public Hint next(Context c) {
        if (!enabled) return null;
        if (c.portal && !hasSeen(Hint.PORTAL)) return Hint.PORTAL;
        if (c.awake && c.needsRest && !hasSeen(Hint.STAMINA)) return Hint.STAMINA;
        if (c.distress && !hasSeen(Hint.DISTRESS)) return Hint.DISTRESS;
        if (!hasSeen(Hint.WELCOME)) return Hint.WELCOME;
        if (!c.awake) return null;
        if (!hasSeen(Hint.AWAKENED)) return Hint.AWAKENED;
        if (c.skills && !hasSeen(Hint.SKILLS)) return Hint.SKILLS;
        if (c.styled && !hasSeen(Hint.STYLE)) return Hint.STYLE;
        if (c.evolve && !hasSeen(Hint.EVOLVE)) return Hint.EVOLVE;
        return null;
    }

    public boolean hasSeen(Hint hint) { return (seen & (1L << hint.id)) != 0; }
    public GuidanceData mark(Hint hint) { return new GuidanceData(enabled, seen | (1L << hint.id)); }
    public GuidanceData enabled(boolean value) { return new GuidanceData(value, seen); }
}
