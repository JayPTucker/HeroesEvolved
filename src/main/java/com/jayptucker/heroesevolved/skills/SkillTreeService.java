package com.jayptucker.heroesevolved.skills;

import com.jayptucker.heroesevolved.ability.*;
import com.jayptucker.heroesevolved.ability.registry.AbilityRegistry;
import com.jayptucker.heroesevolved.ability.service.*;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.jayptucker.heroesevolved.network.*;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

public final class SkillTreeService {
    private SkillTreeService() {}
    public static SkillTreeData.Build build(ServerPlayer player, ResourceLocation power) {
        SkillTreeData data = player.getData(ModDataAttachments.SKILL_TREES);
        var build = data.build(power.toString());
        int highest = Math.max(build.highestLevel(), PlayerProgressionService.getLevel(player));
        if (highest != build.highestLevel()) {
            build = new SkillTreeData.Build(highest, build.learned(), build.slots());
            player.setData(ModDataAttachments.SKILL_TREES, data.with(power.toString(), build));
        }
        return build;
    }
    public static boolean purchase(ServerPlayer player, String nodeId) {
        var assignment = PlayerAbilityService.getData(player).assignedPower();
        if (assignment.isEmpty() || !assignment.get().getValue().isUnlocked() || !PowerRules.canAct(player)) return false;
        ResourceLocation power = assignment.get().getKey();
        var node = SkillCatalog.find(power.getPath(), nodeId).orElse(null);
        if (node == null) return false;
        var build = build(player, power);
        if (build.learned().contains(node.id()) || PlayerProgressionService.getLevel(player) < node.level()
                || !node.prerequisite().isEmpty() && !build.learned().contains(node.prerequisite())
                || SkillCatalog.available(power.getPath(), build) < node.cost()) return false;
        Set<String> learned = new HashSet<>(build.learned()); learned.add(node.id());
        save(player, power, new SkillTreeData.Build(build.highestLevel(), learned, build.slots()));
        player.sendSystemMessage(Component.literal("Learned " + node.name() + (node.active() ? ". Choose a key in your skill tree to equip it." : ".")));
        return true;
    }
    public static boolean equip(ServerPlayer player, AbilitySlot slot, String actionId) {
        var assignment = PlayerAbilityService.getData(player).assignedPower();
        if (assignment.isEmpty() || !assignment.get().getValue().isUnlocked() || !PowerRules.canAct(player)) return false;
        var power = assignment.get().getKey(); var ability = AbilityRegistry.ABILITIES.get(power);
        if (ability == null) return false;
        var build = build(player, power);
        if (!actionId.isEmpty()) {
            var node = SkillCatalog.find(power.getPath(), actionId).orElse(null);
            if (node == null || !node.active() || !build.learned().contains(node.id())
                    || PlayerProgressionService.getLevel(player) < node.level()) return false;
        }
        // Release a held action before replacing it, so changing loadout cannot leave a beam running.
        var previous = actions(player, power);
        for (var entry : previous.entrySet()) {
            if (entry.getKey() != slot && !entry.getValue().definition().id().getPath().equals(actionId)) continue;
            var context = new AbilityUseContext(player, power, PlayerProgressionService.getLevel(player), true);
            entry.getValue().release(context); entry.getValue().deactivate(context);
        }
        var slots = new HashMap<>(build.slots());
        if (actionId.isEmpty()) slots.remove(slot.name());
        else {
            slots.values().removeIf(actionId::equals); // A sub-ability occupies one key only.
            slots.put(slot.name(), actionId);
        }
        save(player, power, new SkillTreeData.Build(build.highestLevel(), build.learned(), slots));
        return true;
    }
    private static void save(ServerPlayer player, ResourceLocation power, SkillTreeData.Build build) {
        player.setData(ModDataAttachments.SKILL_TREES, player.getData(ModDataAttachments.SKILL_TREES).with(power.toString(), build));
    }
    public static Map<AbilitySlot, AbilityAction> actions(ServerPlayer player, ResourceLocation power) {
        Ability ability = AbilityRegistry.ABILITIES.get(power);
        if (ability == null) return Map.of();
        var result = new EnumMap<AbilitySlot, AbilityAction>(AbilitySlot.class); result.putAll(ability.actions());
        var build = build(player, power);
        for (var slot : AbilitySlot.values()) {
            String id = build.slots().get(slot.name());
            if (id == null) continue;
            SkillCatalog.find(power.getPath(), id).filter(n -> n.active() && build.learned().contains(n.id()))
                    .ifPresent(n -> result.put(slot, n.action()));
        }
        return result;
    }
    public static int activationCost(ServerPlayer player, ResourceLocation power, int cost) {
        return discounted(cost, bonus(player, power, 0));
    }
    public static int cooldown(ServerPlayer player, ResourceLocation power, int ticks) {
        return discounted(ticks, bonus(player, power, 2));
    }
    private static int discounted(int value, double bonus) { return value <= 0 ? 0 : Math.max(1, (int) Math.ceil(value * (1 - bonus))); }
    public static double bonus(ServerPlayer player, ResourceLocation power, int branch) {
        var build = build(player, power); String prefix = power.getPath() + "_" + branch + "_";
        return (build.learned().contains(prefix + "0") ? .10 : 0) + (build.learned().contains(prefix + "2") ? .15 : 0);
    }
    public static void sync(ServerPlayer player) { SkillTreeStatePayload.send(player); PlayerPowerSyncService.sync(player); }
}
