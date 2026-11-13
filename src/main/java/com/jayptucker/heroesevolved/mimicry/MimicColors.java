package com.jayptucker.heroesevolved.mimicry;

import java.util.List;

/**
 * Each power's colour, as an empath feels it: the tint an imprint takes and a switch blooms into. Shared by the
 * server (which sends a power's index in a set piece's scale) and the client (which draws it).
 */
public final class MimicColors {
    private static final List<String> POWERS = List.of("quake", "flight", "electrokinesis", "telekinesis", "telepathy",
            "cryokinesis", "radiation", "sonic_resonance", "superspeed", "regeneration", "pyrokinesis", "invisibility",
            "phasing", "empathic_mimicry", "technopathy", "matter_assembly", "time_manipulation", "vibe");
    private static final int[] COLORS = {0xB1E9FA, 0xF6F4EC, 0x60B5FF, 0xBC86F5, 0xA070FF, 0xBFEFFF, 0xFF781C,
            0xE5CB78, 0xFFBE45, 0xFFD89B, 0xFF8A2A, 0x9CC8FF, 0x9FE3D6, 0xE8C27A, 0x60E0C1, 0xE7B771, 0x9A7CFF, 0x46BDFF};

    private MimicColors() {}

    /** The power's index (sent in a piece's scale), or the empath's own gold when it is unknown. */
    public static int index(String power) {
        int index = POWERS.indexOf(power);
        return index < 0 ? POWERS.indexOf("empathic_mimicry") : index;
    }

    public static int color(int index) { return index >= 0 && index < COLORS.length ? COLORS[index] : 0xE8C27A; }

    public static int color(String power) { return color(index(power)); }
}
