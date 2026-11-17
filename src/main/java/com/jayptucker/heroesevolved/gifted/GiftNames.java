package com.jayptucker.heroesevolved.gifted;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

import java.util.Map;

/**
 * Names for gifted mobs, the way Apotheosis names its bosses: a first name and an epithet that says what the power is
 * ("Hollis the Ashen", "Vesna Stormcaller"). The server writes the name once and saves it with the gift.
 */
public final class GiftNames {
    private static final String[] FIRST = {"Aldric", "Brannoc", "Cedrin", "Dorrel", "Edda", "Fenwick", "Garron", "Hollis",
            "Isolde", "Jorund", "Kestrel", "Lio", "Marrow", "Nell", "Osric", "Perrin", "Quill", "Rook", "Sable", "Tamsin",
            "Ulric", "Vesna", "Wick", "Yara", "Zev", "Bryn", "Corvin", "Della", "Esk", "Gideon", "Hesper", "Ivo", "Juno",
            "Lark", "Mabry", "Nyx", "Orla", "Pike", "Rhea", "Silas", "Thorne", "Vale", "Wren", "Agna", "Bastian", "Cress"};

    /** Each power's display name (the client's lang file has the same) and three epithets. */
    private static final Map<String, String> NAMES = Map.ofEntries(
            Map.entry("regeneration", "Regeneration"), Map.entry("flight", "Flight"),
            Map.entry("time_manipulation", "Space-Time Manipulation"), Map.entry("quake", "Quake"),
            Map.entry("electrokinesis", "Electrokinesis"), Map.entry("telekinesis", "Telekinesis"),
            Map.entry("radiation", "Induced Radioactivity"), Map.entry("cryokinesis", "Cryokinesis"),
            Map.entry("telepathy", "Telepathy"), Map.entry("sonic_resonance", "Sonic Resonance"),
            Map.entry("superspeed", "Superspeed"), Map.entry("pyrokinesis", "Pyrokinesis"),
            Map.entry("invisibility", "Invisibility"), Map.entry("phasing", "Density"),
            Map.entry("empathic_mimicry", "Empathic Mimicry"), Map.entry("technopathy", "Technopathy"),
            Map.entry("matter_assembly", "Matter Assembly"), Map.entry("vibe", "Vibe"),
            Map.entry("shapeshifting", "Shapeshifting"), Map.entry("hydrokinesis", "Hydrokinesis"));

    private static final Map<String, String[]> EPITHETS = Map.ofEntries(
            Map.entry("regeneration", new String[]{"the Unkillable", "Bloodmend", "the Undying"}),
            Map.entry("flight", new String[]{"Skystrider", "the Gale", "Windborne"}),
            Map.entry("time_manipulation", new String[]{"the Hourless", "Clockbreaker", "the Late"}),
            Map.entry("quake", new String[]{"the Faultmaker", "Groundbreaker", "the Tremor"}),
            Map.entry("electrokinesis", new String[]{"Stormcaller", "the Arc", "Thunderhand"}),
            Map.entry("telekinesis", new String[]{"the Unseen Hand", "Mindhand", "the Lifter"}),
            Map.entry("radiation", new String[]{"the Glowing", "Half-Life", "the Reactor"}),
            Map.entry("cryokinesis", new String[]{"the Rimeborn", "Frostgrip", "the Pale"}),
            Map.entry("telepathy", new String[]{"the Whisperer", "Mindreaver", "the Listener"}),
            Map.entry("sonic_resonance", new String[]{"the Howler", "Shrieker", "the Resonant"}),
            Map.entry("superspeed", new String[]{"the Blur", "Quickstep", "the Streak"}),
            Map.entry("pyrokinesis", new String[]{"the Ashen", "Emberhand", "the Kindler"}),
            Map.entry("invisibility", new String[]{"the Unseen", "Glasswalker", "the Veil"}),
            Map.entry("phasing", new String[]{"the Ghost", "Wallwalker", "the Dense"}),
            Map.entry("empathic_mimicry", new String[]{"the Mirror", "Echo", "the Borrower"}),
            Map.entry("technopathy", new String[]{"the Tinker", "Wirewhisper", "the Machinist"}),
            Map.entry("matter_assembly", new String[]{"the Shaper", "Shardsmith", "the Maker"}),
            Map.entry("vibe", new String[]{"the Breacher", "Riftseer", "the Tuned"}),
            Map.entry("shapeshifting", new String[]{"the Many-Faced", "Skinchanger", "the Masked"}),
            Map.entry("hydrokinesis", new String[]{"the Tidecaller", "Wavebreaker", "the Drowned"}));

    private GiftNames() {}

    /** A new name for a mob with {@code power}. */
    public static String roll(ResourceLocation power, RandomSource random) {
        String first = FIRST[random.nextInt(FIRST.length)];
        String[] epithets = EPITHETS.getOrDefault(power.getPath(), new String[]{"the Gifted"});
        String epithet = epithets[random.nextInt(epithets.length)];
        return first + " " + epithet;
    }

    public static String powerName(ResourceLocation power) {
        return NAMES.getOrDefault(power.getPath(), power.getPath());
    }
}
