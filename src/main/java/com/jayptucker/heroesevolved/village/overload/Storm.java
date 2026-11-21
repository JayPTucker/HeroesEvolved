package com.jayptucker.heroesevolved.village.overload;

import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
import com.jayptucker.heroesevolved.radio.EmergencyBand;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.BossEvent;

/**
 * The weather a gift becomes when it wakes stronger than its villager can hold. One per power that has a storm; the
 * order is sent over the network (append new storms at the end).
 */
public enum Storm {
    WILDFIRE(ModAbilities.PYROKINESIS_ID, EmergencyBand.Trouble.WILDFIRE, "Wildfire", BossEvent.BossBarColor.YELLOW,
            "Fire! One of us has caught alight and it's spreading. Help us!",
            "A villager's gift has woken too strong to hold: a wildfire is spreading out from them. Reach them and calm them down."),
    SUPERCELL(ModAbilities.FLIGHT_ID, EmergencyBand.Trouble.SUPERCELL, "Supercell", BossEvent.BossBarColor.GREEN,
            "Twister! It's got one of us in it and it's coming through the village!",
            "A villager's gift has woken too strong to hold: a tornado has them in its eye. Reach them and calm them down."),
    BLIZZARD(ModAbilities.CRYOKINESIS_ID, EmergencyBand.Trouble.BLIZZARD, "Blizzard", BossEvent.BossBarColor.BLUE,
            "We're snowed in and freezing. It's coming from one of us. Please come.",
            "A villager's gift has woken too strong to hold: a blizzard is pouring out of them. Reach them and calm them down.");

    /** The gift that becomes this storm. */
    public final ResourceLocation power;
    /** Its call's kind on the emergency band (the flare's colour). */
    public final EmergencyBand.Trouble trouble;
    public final String title;
    public final BossEvent.BossBarColor color;
    /** What the village says on the radio, and what everyone near is told when it starts. */
    public final String words, told;

    Storm(ResourceLocation power, EmergencyBand.Trouble trouble, String title, BossEvent.BossBarColor color, String words, String told) {
        this.power = power; this.trouble = trouble; this.title = title; this.color = color; this.words = words; this.told = told;
    }

    private static final Storm[] VALUES = values();

    /** The storm this gift becomes, or null if it has none. */
    public static Storm of(ResourceLocation power) {
        if (power == null) return null;
        for (Storm s : VALUES) if (s.power.equals(power)) return s;
        return null;
    }

    public static Storm byId(int id) { return id >= 0 && id < VALUES.length ? VALUES[id] : null; }

    /** By name ("wildfire", "supercell", "blizzard"; "tornado" too), or null. */
    public static Storm named(String name) {
        if (name == null) return null;
        if (name.equalsIgnoreCase("tornado")) return SUPERCELL;
        for (Storm s : VALUES) if (s.name().equalsIgnoreCase(name)) return s;
        return null;
    }
}
