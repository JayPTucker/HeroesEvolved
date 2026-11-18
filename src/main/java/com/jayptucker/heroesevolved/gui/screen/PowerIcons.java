package com.jayptucker.heroesevolved.gui.screen;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Map;

/** Item emblems for whole powers, shared by the hero menu and memory wheel. */
public final class PowerIcons {
    private PowerIcons() {}

    private static final Map<String, Item> EMBLEMS = Map.ofEntries(
            Map.entry("quake", Items.CRACKED_STONE_BRICKS), Map.entry("flight", Items.FEATHER),
            Map.entry("cryokinesis", Items.BLUE_ICE), Map.entry("electrokinesis", Items.LIGHTNING_ROD),
            Map.entry("telekinesis", Items.ENDER_PEARL), Map.entry("telepathy", Items.ENDER_EYE),
            Map.entry("regeneration", Items.GOLDEN_APPLE), Map.entry("time_manipulation", Items.CLOCK),
            Map.entry("radiation", Items.MAGMA_CREAM), Map.entry("sonic_resonance", Items.BELL),
            Map.entry("superspeed", Items.SUGAR),
            Map.entry("pyrokinesis", Items.BLAZE_POWDER), Map.entry("invisibility", Items.GLASS_PANE),
            Map.entry("phasing", Items.CHORUS_FRUIT), Map.entry("empathic_mimicry", Items.AMETHYST_SHARD),
            Map.entry("technopathy", Items.COMPARATOR), Map.entry("matter_assembly", Items.STONECUTTER),
            Map.entry("vibe", Items.ECHO_SHARD), Map.entry("shapeshifting", Items.ARMADILLO_SCUTE),
            Map.entry("hydrokinesis", Items.HEART_OF_THE_SEA),
            Map.entry("power_amplification", Items.REDSTONE));

    public static ItemStack emblem(String power) { return new ItemStack(EMBLEMS.getOrDefault(power, Items.NETHER_STAR)); }
}
