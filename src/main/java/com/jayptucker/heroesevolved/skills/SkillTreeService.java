package com.jayptucker.heroesevolved.skills;

import com.jayptucker.heroesevolved.ability.*;
import com.jayptucker.heroesevolved.ability.registry.AbilityRegistry;
import com.jayptucker.heroesevolved.ability.registry.ModAbilities;
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
        int highest = Math.max(build.highestLevel(), PlayerProgressionService.getPowerLevel(player));
        if (highest != build.highestLevel()) {
            build = new SkillTreeData.Build(highest, build.learned(), build.slots(), build.training());
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
                || SkillCatalog.available(power.getPath(), build, storyPoints(player)) < node.cost()) return false;
        if (node.capstone() && SkillCatalog.nodes(power.getPath()).stream().anyMatch(n -> n.capstone() && build.learned().contains(n.id()))) return false;
        Set<String> learned = new HashSet<>(build.learned()); learned.add(node.id());
        save(player, power, new SkillTreeData.Build(build.highestLevel(), learned, build.slots(), build.training()));
        player.sendSystemMessage(Component.literal("Learned " + node.name() + (node.active() ? ". It's on your ability wheel; put it on a key from the Abilities page if you like." : ".")));
        return true;
    }
    /** Keys 1 to 5 by their slot: a skill id puts that skill on the key, "" restores the key's own ability. */
    public static boolean equip(ServerPlayer player, AbilitySlot slot, String actionId) {
        if (!actionId.isEmpty() && actionId.startsWith(Loadout.BASE)) return false;
        return bind(player, slot.ordinal() + 1, actionId.isEmpty() ? null : actionId);
    }

    /**
     * Puts an action on ability key {@code key} (1 to 8): a base action ({@code base:SLOT}) or a learned
     * active skill's id. "" leaves the key empty; null restores its default.
     */
    public static boolean bind(ServerPlayer player, int key, String actionKey) {
        var assignment = PlayerAbilityService.getData(player).assignedPower();
        if (key < 1 || key > Loadout.KEYS || assignment.isEmpty() || !assignment.get().getValue().isUnlocked() || !PowerRules.canAct(player)) return false;
        var power = assignment.get().getKey();
        if (AbilityRegistry.ABILITIES.get(power) == null) return false;
        var build = build(player, power);
        if (actionKey != null && !actionKey.isEmpty()) {
            if (Loadout.resolve(player, power, actionKey) == null) return false;
            var node = SkillCatalog.find(power.getPath(), actionKey).orElse(null);
            if (node != null && PlayerProgressionService.getLevel(player) < node.level()) return false;
        }
        // Release whatever is held on the key being changed and the action being moved, so a beam can't keep running.
        var context = new AbilityUseContext(player, power, PlayerProgressionService.getLevel(player), true);
        for (int other = 1; other <= Loadout.KEYS; other++) {
            String bound = Loadout.binding(build, other);
            if (other != key && (actionKey == null || !bound.equals(actionKey))) continue;
            var action = Loadout.resolve(player, power, bound);
            if (action != null) { action.release(context); action.deactivate(context); }
        }
        save(player, power, new SkillTreeData.Build(build.highestLevel(), build.learned(), Loadout.rebind(build, key, actionKey), build.training()));
        return true;
    }

    private static void save(ServerPlayer player, ResourceLocation power, SkillTreeData.Build build) {
        player.setData(ModDataAttachments.SKILL_TREES, player.getData(ModDataAttachments.SKILL_TREES).with(power.toString(), build));
    }
    /** What ability keys 1 to 5 hold, by their slot. Keys 6 to 8 and the wheel go through {@link Loadout}. */
    public static Map<AbilitySlot, AbilityAction> actions(ServerPlayer player, ResourceLocation power) {
        if (AbilityRegistry.ABILITIES.get(power) == null) return Map.of();
        var result = new EnumMap<AbilitySlot, AbilityAction>(AbilitySlot.class);
        for (var slot : AbilitySlot.values()) {
            AbilityAction action = Loadout.onKey(player, power, slot.ordinal() + 1);
            if (action != null) result.put(slot, action);
        }
        return result;
    }

    /** Everything a player's actions could be holding: what's on all eight keys, for releases and cleanup. */
    public static java.util.List<AbilityAction> allActions(ServerPlayer player, ResourceLocation power) {
        return Loadout.available(player, power).stream().map(Loadout.Entry::action).toList();
    }
    public static int storyPoints(ServerPlayer player) {
        return player.getData(ModDataAttachments.POWER_JOURNEY).storyPoints();
    }
    public static int activationCost(ServerPlayer player, ResourceLocation power, int cost) {
        return discounted(cost, bonus(player, power, 0));
    }
    public static int cooldown(ServerPlayer player, ResourceLocation power, int ticks) {
        return discounted(ticks, bonus(player, power, 2));
    }
    private static int discounted(int value, double bonus) { return value <= 0 ? 0 : Math.max(1, (int) Math.ceil(value * (1 - bonus))); }
    public static double bonus(ServerPlayer player, ResourceLocation power, int branch) {
        // These trees unlock specific maneuvers and interactions, rather than blanket stat bonuses.
        if (power.equals(ModAbilities.FLIGHT_ID) || power.equals(ModAbilities.TELEKINESIS_ID)) return 0;
        var build = build(player, power); String prefix = power.getPath() + "_" + branch + "_";
        return (build.learned().contains(prefix + "0") ? .10 : 0) + (build.learned().contains(prefix + "2") ? .15 : 0);
    }
    public static boolean has(ServerPlayer player, String id) {
        return PlayerAbilityService.getData(player).assignedPower().filter(e -> e.getValue().isUnlocked())
                .map(e -> build(player, e.getKey()).learned().contains(id)).orElse(false);
    }
    public static void awardTraining(ServerPlayer player) {
        PlayerAbilityService.getData(player).assignedPower().ifPresent(e -> {
            var b = build(player, e.getKey());
            save(player, e.getKey(), new SkillTreeData.Build(b.highestLevel(), b.learned(), b.slots(), Math.min(6, b.training() + 2)));
            sync(player);
        });
    }
    public static boolean respecialize(ServerPlayer player) {
        var assignment = PlayerAbilityService.getData(player).assignedPower();
        if (assignment.isEmpty() || !assignment.get().getValue().isUnlocked() || !PowerRules.canAct(player)
                || player.getData(ModDataAttachments.PLAYER_COMBAT).wasDamagedWithin(player.level().getGameTime(), 200)) return false;
        var power = assignment.get().getKey(); var b = build(player, power);
        if (power.equals(ModAbilities.FLIGHT_ID)) {
            com.jayptucker.heroesevolved.flight.FlightDynamicsService.cancelSkyburst(player);
            com.jayptucker.heroesevolved.flight.FlightSkillService.forget(player);
        }
        if (power.equals(ModAbilities.TELEKINESIS_ID)) com.jayptucker.heroesevolved.telekinesis.TelekinesisService.stopAll(player);
        for (var action : allActions(player, power)) {
            var context = new AbilityUseContext(player, power, PlayerProgressionService.getLevel(player), true);
            action.release(context); action.deactivate(context);
        }
        com.jayptucker.heroesevolved.expansion.ExpansionSkills.forget(player);
        if (power.equals(ModAbilities.FLIGHT_ID)) {
            com.jayptucker.heroesevolved.flight.FlightSkillService.forget(player);
            com.jayptucker.heroesevolved.flight.FlightSkillPassives.forget(player);
        }
        save(player, power, new SkillTreeData.Build(b.highestLevel(), Set.of(), Map.of(), b.training()));
        return true;
    }
    public static void sync(ServerPlayer player) { SkillTreeStatePayload.send(player); PlayerPowerSyncService.sync(player); }
}
