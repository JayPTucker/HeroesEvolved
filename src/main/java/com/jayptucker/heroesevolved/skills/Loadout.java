package com.jayptucker.heroesevolved.skills;

import com.jayptucker.heroesevolved.ability.Ability;
import com.jayptucker.heroesevolved.ability.AbilityAction;
import com.jayptucker.heroesevolved.ability.AbilitySlot;
import com.jayptucker.heroesevolved.ability.registry.AbilityRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Every ability a player can cast, and which of their eight ability keys each one is on.
 *
 * <p>An ability is named by an <b>action key</b>: {@code base:<SLOT>} for one of the power's five
 * built-in abilities, or a skill node's id for a learned active skill. Any action can go on any key,
 * or on none (everything is always on the ability wheel). Keys 1 to 5 default to the five built-in
 * abilities in order; keys 6 to 8 start empty. Bindings are saved in the skill build under "PRIMARY"
 * to "QUINARY" (the names older saves already use) and "KEY6" to "KEY8"; an empty value is a key
 * deliberately left empty.</p>
 */
public final class Loadout {
    public static final int KEYS = 8;
    public static final String BASE = "base:";

    private Loadout() {}

    /** One castable ability: its action key, the action, and the built-in slot it belongs to (base actions only). */
    public record Entry(String key, AbilityAction action, AbilitySlot slot, SkillCatalog.Node node) {}

    public static String saveName(int key) {
        return key <= 5 ? AbilitySlot.values()[key - 1].name() : "KEY" + key;
    }

    public static String defaultFor(int key) {
        return key <= 5 ? BASE + AbilitySlot.values()[key - 1].name() : "";
    }

    public static String base(AbilitySlot slot) { return BASE + slot.name(); }

    /** The action key on {@code key} (1 to 8), or "" when the key is empty. */
    public static String binding(SkillTreeData.Build build, int key) {
        return binding(build.slots(), key);
    }

    /** The key (1 to 8) an action is on, or 0 if it is only on the wheel. */
    public static int keyOf(SkillTreeData.Build build, String actionKey) {
        for (int key = 1; key <= KEYS; key++) if (binding(build, key).equals(actionKey)) return key;
        return 0;
    }

    /** Everything the player's power can cast right now (unlocked or not): the base kit, then learned active skills. */
    public static List<Entry> available(ServerPlayer player, ResourceLocation power) {
        Ability ability = AbilityRegistry.ABILITIES.get(power);
        if (ability == null) return List.of();
        List<Entry> entries = new ArrayList<>();
        for (AbilitySlot slot : AbilitySlot.values()) {
            AbilityAction action = ability.actions().get(slot);
            if (action != null) entries.add(new Entry(base(slot), action, slot, null));
        }
        var build = SkillTreeService.build(player, power);
        for (SkillCatalog.Node node : SkillCatalog.nodes(power.getPath()))
            if (node.active() && build.learned().contains(node.id())) entries.add(new Entry(node.id(), node.action(), null, node));
        return entries;
    }

    /** The action behind an action key, if the player's power has it (and has learned it, for a skill). */
    public static AbilityAction resolve(ServerPlayer player, ResourceLocation power, String actionKey) {
        if (actionKey == null || actionKey.isEmpty()) return null;
        if (actionKey.startsWith(BASE)) {
            Ability ability = AbilityRegistry.ABILITIES.get(power);
            if (ability == null) return null;
            try {
                return ability.actions().get(AbilitySlot.valueOf(actionKey.substring(BASE.length())));
            } catch (IllegalArgumentException unknown) {
                return null;
            }
        }
        var build = SkillTreeService.build(player, power);
        return SkillCatalog.find(power.getPath(), actionKey)
                .filter(node -> node.active() && build.learned().contains(node.id()))
                .map(SkillCatalog.Node::action).orElse(null);
    }

    /** The action on {@code key}, or null if the key is empty (or holds something no longer learned). */
    public static AbilityAction onKey(ServerPlayer player, ResourceLocation power, int key) {
        var build = SkillTreeService.build(player, power);
        AbilityAction action = resolve(player, power, binding(build, key));
        // A skill that was unlearned leaves its key holding the default again.
        if (action == null && !binding(build, key).isEmpty()) action = resolve(player, power, defaultFor(key));
        return action;
    }

    /**
     * The new key-to-action map after putting {@code actionKey} on {@code key} ("" empties the key, and
     * null restores its default). An action is on one key at most: the key it leaves goes back to its
     * default, or empty if that default is already on another key.
     */
    public static Map<String, String> rebind(SkillTreeData.Build build, int key, String actionKey) {
        var slots = new java.util.HashMap<>(build.slots());
        String placed = actionKey == null ? defaultFor(key) : actionKey;
        if (placed.equals(defaultFor(key))) slots.remove(saveName(key));
        else slots.put(saveName(key), placed);
        if (placed.isEmpty()) return slots;
        for (int other = 1; other <= KEYS; other++) {
            if (other == key || !binding(slots, other).equals(placed)) continue;
            slots.remove(saveName(other));
            String fallback = defaultFor(other);
            if (fallback.equals(placed) || !fallback.isEmpty() && boundElsewhere(slots, other, fallback)) slots.put(saveName(other), "");
        }
        return slots;
    }

    private static String binding(Map<String, String> slots, int key) {
        String saved = slots.get(saveName(key));
        return saved == null ? defaultFor(key) : saved;
    }

    private static boolean boundElsewhere(Map<String, String> slots, int key, String actionKey) {
        for (int other = 1; other <= KEYS; other++) if (other != key && binding(slots, other).equals(actionKey)) return true;
        return false;
    }
}
