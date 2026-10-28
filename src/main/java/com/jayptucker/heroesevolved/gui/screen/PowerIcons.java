package com.jayptucker.heroesevolved.gui.screen;

import com.jayptucker.heroesevolved.skills.SkillCatalog;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Map;

/** Item icons for powers, their abilities and skill nodes, shared by the Abilities and Skill Tree screens. */
public final class PowerIcons {
    private PowerIcons() {}

    private static final Map<String, Item> EMBLEMS = Map.ofEntries(
            Map.entry("quake", Items.CRACKED_STONE_BRICKS), Map.entry("flight", Items.FEATHER),
            Map.entry("cryokinesis", Items.BLUE_ICE), Map.entry("electrokinesis", Items.LIGHTNING_ROD),
            Map.entry("telekinesis", Items.ENDER_PEARL), Map.entry("telepathy", Items.ENDER_EYE),
            Map.entry("regeneration", Items.GOLDEN_APPLE), Map.entry("time_manipulation", Items.CLOCK),
            Map.entry("radiation", Items.MAGMA_CREAM), Map.entry("sonic_resonance", Items.BELL),
            Map.entry("superspeed", Items.SUGAR), Map.entry("precognition", Items.PAINTING),
            Map.entry("pyrokinesis", Items.BLAZE_POWDER), Map.entry("invisibility", Items.GLASS_PANE),
            Map.entry("phasing", Items.CHORUS_FRUIT), Map.entry("empathic_mimicry", Items.AMETHYST_SHARD));

    /** First keyword found in an ability's id or name picks its picture. Order matters: specific before general. */
    private static final List<Map.Entry<String, Item>> KEYWORDS = List.of(
            Map.entry("blue", Items.SOUL_CAMPFIRE), Map.entry("inferno", Items.FIRE_CHARGE), Map.entry("flare", Items.FIRE_CHARGE),
            Map.entry("fire", Items.BLAZE_POWDER), Map.entry("ember", Items.CAMPFIRE), Map.entry("flame", Items.BLAZE_ROD),
            Map.entry("backdraft", Items.FIREWORK_ROCKET), Map.entry("ring", Items.CAMPFIRE),
            Map.entry("frost", Items.SNOWBALL), Map.entry("freeze", Items.ICE), Map.entry("ice", Items.PACKED_ICE),
            Map.entry("glacial", Items.POINTED_DRIPSTONE), Map.entry("blizzard", Items.POWDER_SNOW_BUCKET), Map.entry("rime", Items.ICE),
            Map.entry("cocoon", Items.BLUE_ICE), Map.entry("stillheart", Items.BLUE_ICE),
            Map.entry("lightning", Items.LIGHTNING_ROD), Map.entry("thunder", Items.TRIDENT), Map.entry("storm", Items.TRIDENT),
            Map.entry("ball", Items.FIRE_CHARGE), Map.entry("arc", Items.CHAIN), Map.entry("circuit", Items.REDSTONE),
            Map.entry("ground", Items.COPPER_INGOT), Map.entry("lattice", Items.TRIPWIRE_HOOK),
            Map.entry("shockwave", Items.STONE), Map.entry("slam", Items.ANVIL), Map.entry("tremor", Items.SCULK_SENSOR),
            Map.entry("earthquake", Items.CRACKED_DEEPSLATE_BRICKS), Map.entry("blast", Items.TNT), Map.entry("fault", Items.CRACKED_STONE_BRICKS),
            Map.entry("counterforce", Items.SHIELD), Map.entry("seamfinder", Items.IRON_PICKAXE),
            Map.entry("launch", Items.FIREWORK_ROCKET), Map.entry("cyclone", Items.WIND_CHARGE), Map.entry("dive", Items.ARROW),
            Map.entry("grab", Items.LEAD), Map.entry("toggle", Items.ELYTRA), Map.entry("tailwind", Items.WIND_CHARGE),
            Map.entry("clap", Items.GOAT_HORN), Map.entry("catch", Items.LEAD),
            Map.entry("push", Items.PISTON), Map.entry("grip", Items.LEAD), Map.entry("bullet", Items.SHIELD),
            Map.entry("disarm", Items.IRON_SWORD), Map.entry("volley", Items.COBBLESTONE), Map.entry("bubble", Items.GLASS),
            Map.entry("remote", Items.LEVER),
            Map.entry("read", Items.BOOK), Map.entry("suggestion", Items.WRITABLE_BOOK), Map.entry("link", Items.ENDER_EYE),
            Map.entry("puppet", Items.LEAD), Map.entry("nightmare", Items.WITHER_ROSE), Map.entry("persuasion", Items.EMERALD),
            Map.entry("memory", Items.BOOK), Map.entry("hive", Items.HONEYCOMB),
            Map.entry("mending", Items.GLISTERING_MELON_SLICE), Map.entry("blood", Items.REDSTONE), Map.entry("indestructible", Items.TOTEM_OF_UNDYING),
            Map.entry("resurrection", Items.TOTEM_OF_UNDYING), Map.entry("aura", Items.GOLDEN_APPLE), Map.entry("wound", Items.RED_DYE),
            Map.entry("adaptation", Items.TURTLE_HELMET), Map.entry("revive", Items.ENCHANTED_GOLDEN_APPLE),
            Map.entry("blink", Items.ENDER_PEARL), Map.entry("slow", Items.COBWEB), Map.entry("stop", Items.CLOCK),
            Map.entry("rewind", Items.RECOVERY_COMPASS), Map.entry("snapshot", Items.FILLED_MAP), Map.entry("seal", Items.CLOCK),
            Map.entry("accelerate", Items.BONE_MEAL), Map.entry("shared_blink", Items.ENDER_EYE),
            Map.entry("radiant", Items.MAGMA_CREAM), Map.entry("pulse", Items.GLOWSTONE_DUST), Map.entry("emp", Items.REDSTONE_TORCH),
            Map.entry("meltdown", Items.MAGMA_BLOCK), Map.entry("exploding", Items.TNT), Map.entry("vent", Items.CAMPFIRE),
            Map.entry("reactor", Items.BEACON), Map.entry("glowing", Items.GLOW_INK_SAC),
            Map.entry("cry", Items.GOAT_HORN), Map.entry("scream", Items.GOAT_HORN), Map.entry("burst", Items.BELL),
            Map.entry("echo", Items.ECHO_SHARD), Map.entry("vault", Items.RABBIT_FOOT), Map.entry("shatter", Items.GLASS),
            Map.entry("quiet", Items.WHITE_WOOL),
            Map.entry("accelerate", Items.SUGAR), Map.entry("quickstep", Items.RABBIT_FOOT), Map.entry("flurry", Items.GOLDEN_SWORD),
            Map.entry("whirlwind", Items.WIND_CHARGE), Map.entry("redline", Items.REDSTONE), Map.entry("mirage", Items.GLASS_PANE),
            Map.entry("perception", Items.SPYGLASS),
            Map.entry("premonition", Items.SPYGLASS), Map.entry("paint", Items.PAINTING), Map.entry("sever", Items.SHEARS),
            Map.entry("fate", Items.STRING), Map.entry("convergence", Items.COMPASS), Map.entry("foresight", Items.ENDER_EYE),
            Map.entry("path", Items.COMPASS), Map.entry("omen", Items.BELL),
            Map.entry("fade", Items.GLASS_PANE), Map.entry("silent", Items.LEATHER_BOOTS), Map.entry("unseen", Items.IRON_SWORD),
            Map.entry("shroud", Items.GRAY_WOOL), Map.entry("ghost", Items.PHANTOM_MEMBRANE), Map.entry("smoke", Items.GUNPOWDER),
            Map.entry("pickpocket", Items.BUNDLE), Map.entry("cloak", Items.GRAY_BANNER),
            Map.entry("phase", Items.CHORUS_FRUIT), Map.entry("intangible", Items.PHANTOM_MEMBRANE), Map.entry("lock", Items.CHAIN),
            Map.entry("empathize", Items.AMETHYST_SHARD), Map.entry("recall", Items.AMETHYST_CLUSTER), Map.entry("echo_", Items.ECHO_SHARD),
            Map.entry("sense", Items.SCULK_SENSOR), Map.entry("overcharge", Items.GLOWSTONE), Map.entry("pain", Items.POISONOUS_POTATO));

    public static ItemStack emblem(String power) { return new ItemStack(EMBLEMS.getOrDefault(power, Items.NETHER_STAR)); }

    /** An ability's icon from its id, falling back to the power's emblem. */
    public static ItemStack action(String power, String actionPath) {
        String id = actionPath.toLowerCase();
        for (var entry : KEYWORDS) if (id.contains(entry.getKey())) return new ItemStack(entry.getValue());
        return emblem(power);
    }

    /**
     * An optional "icon" in skill_trees.json wins. Otherwise: actives by keyword, capstones a nether
     * star, technique upgrades a book, and the stat nodes their branch's theme.
     */
    public static ItemStack node(SkillCatalog.Node node) {
        if (!node.icon().isEmpty()) {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(node.icon()));
            if (item != Items.AIR) return new ItemStack(item);
        }
        if (node.capstone()) return new ItemStack(Items.NETHER_STAR);
        if (node.active()) return action(node.power(), node.id() + "_" + node.name().replace(' ', '_'));
        if (!node.mechanic().isEmpty()) return new ItemStack(Items.ENCHANTED_BOOK);
        return new ItemStack(switch (node.branch()) {
            case 0 -> Items.FEATHER;
            case 1 -> Items.IRON_SWORD;
            default -> Items.CLOCK;
        });
    }
}
