package com.jayptucker.heroesevolved.admin;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.Ability;
import com.jayptucker.heroesevolved.ability.AbilityUseContext;
import com.jayptucker.heroesevolved.ability.data.AbilityProgress;
import com.jayptucker.heroesevolved.ability.registry.AbilityRegistry;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.cooldown.CooldownService;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.events.EclipseService;
import com.jayptucker.heroesevolved.network.AdminActionPayload;
import com.jayptucker.heroesevolved.network.AdminStatePayload;
import com.jayptucker.heroesevolved.network.PlayerPowerSyncService;
import com.jayptucker.heroesevolved.progression.MasteryService;
import com.jayptucker.heroesevolved.progression.PlayerProgressionData;
import com.jayptucker.heroesevolved.progression.PlayerProgressionService;
import com.jayptucker.heroesevolved.progression.PowerLevels;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Server side of the operator admin menu. Every request is permission
 * checked here, logged to the server console, and answered with fresh
 * state so the menu always shows what really happened.
 */
public final class AdminService {
    /** Vanilla's game-master level: the same requirement as /heroesevolved. */
    public static final int REQUIRED_PERMISSION_LEVEL = 2;
    private static final int MAXIMUM_MASTERY_GRANT = 1_000_000;

    private AdminService() {
    }

    public static boolean isAdmin(ServerPlayer player) {
        return player.hasPermissions(REQUIRED_PERMISSION_LEVEL);
    }

    public static void open(ServerPlayer admin) {
        sendState(admin, true);
    }

    public static void handle(ServerPlayer admin, AdminActionPayload request) {
        if (!isAdmin(admin)) {
            HeroesEvolved.LOGGER.warn(
                    "{} sent an admin menu request without permission.",
                    admin.getName().getString()
            );
            return;
        }

        MinecraftServer server = admin.getServer();
        if (server == null) {
            return;
        }

        AdminActionPayload.Action action = request.action();
        if (action == AdminActionPayload.Action.OPEN
                || action == AdminActionPayload.Action.REFRESH) {
            sendState(admin, action == AdminActionPayload.Action.OPEN);
            return;
        }

        ServerPlayer target = server.getPlayerList().getPlayer(request.target());
        String result = target == null && needsTarget(action)
                ? "That player is no longer online."
                : perform(admin, target, request);

        HeroesEvolved.LOGGER.info("[Admin] {} -> {} on {}: {}",
                admin.getName().getString(), action,
                target == null ? "-" : target.getName().getString(), result);
        admin.displayClientMessage(Component.literal(result)
                .withStyle(ChatFormatting.GOLD), true);
        sendState(admin, false);
    }

    private static boolean needsTarget(AdminActionPayload.Action action) {
        return action != AdminActionPayload.Action.START_ECLIPSE
                && action != AdminActionPayload.Action.STOP_ECLIPSE;
    }

    private static String perform(
            ServerPlayer admin,
            ServerPlayer target,
            AdminActionPayload request
    ) {
        String name = target == null ? "" : target.getName().getString();
        return switch (request.action()) {
            case GIVE_POWER -> givePower(target, request.argument());
            case REMOVE_POWER -> PlayerAbilityService.removeAssignedAbility(target)
                    ? "Removed " + name + "'s power."
                    : name + " has no power to remove.";
            case AWAKEN -> awaken(target);
            case SET_LEVEL -> setLevel(target, request.value());
            case ADD_MASTERY -> {
                int amount = Math.clamp(request.value(), 1, MAXIMUM_MASTERY_GRANT);
                MasteryService.grantMastery(target, amount);
                yield "Gave " + name + " " + amount + " Mastery.";
            }
            case RESET_PROGRESS -> {
                target.setData(
                        com.jayptucker.heroesevolved.data.ModDataAttachments
                                .PLAYER_PROGRESSION.get(),
                        PlayerProgressionData.empty()
                );
                refill(target);
                yield "Reset " + name + " to Level 1.";
            }
            case REFILL_ENERGY -> {
                refill(target);
                yield "Refilled " + name + "'s energy.";
            }
            case TOGGLE_UNLIMITED_ENERGY -> {
                boolean enabled = !PlayerEnergyService.hasUnlimitedEnergy(target);
                PlayerEnergyService.setUnlimitedEnergy(target, enabled);
                yield "Unlimited energy " + (enabled ? "enabled" : "disabled")
                        + " for " + name + ".";
            }
            case RESET_COOLDOWNS -> {
                CooldownService.clearAll(target);
                PlayerPowerSyncService.sync(target);
                yield "Reset all of " + name + "'s cooldowns.";
            }
            case FULL_HEAL -> {
                target.setHealth(target.getMaxHealth());
                target.getFoodData().setFoodLevel(20);
                target.getFoodData().setSaturation(20.0F);
                target.removeAllEffects();
                target.clearFire();
                yield "Fully healed " + name + ".";
            }
            case START_ECLIPSE -> EclipseService.start(admin.getServer())
                    ? "Started an Eclipse."
                    : "An Eclipse is already active.";
            case STOP_ECLIPSE -> EclipseService.end(admin.getServer())
                    ? "Stopped the Eclipse."
                    : "No Eclipse is active.";
            case OPEN, REFRESH -> "";
        };
    }

    private static String givePower(ServerPlayer target, String powerName) {
        ResourceLocation powerId = ResourceLocation.tryParse(powerName);
        if (powerId == null || !AbilityRegistry.ABILITIES.containsKey(powerId)) {
            return "Unknown power: " + powerName;
        }

        String name = target.getName().getString();
        if (!PlayerAbilityService.availableTo(target, powerId)) return "That unique power already belongs to another player.";
        return PlayerAbilityService.replaceWithAbility(target, powerId)
                ? "Gave " + name + " " + powerId.getPath() + " (awakened)."
                : name + " already has that power awakened.";
    }

    private static String awaken(ServerPlayer target) {
        Optional<Map.Entry<ResourceLocation, AbilityProgress>> assignment =
                PlayerAbilityService.getData(target).assignedPower();
        String name = target.getName().getString();
        if (assignment.isEmpty()) {
            return name + " has no power to awaken.";
        }
        if (!PlayerAbilityService.unlockAbility(target, assignment.get().getKey())) {
            return name + "'s power is already awake.";
        }

        // Play the power's dramatic first-awakening reaction too.
        Ability ability = AbilityRegistry.ABILITIES.get(assignment.get().getKey());
        if (ability != null) {
            ability.onAwaken(new AbilityUseContext(
                    target,
                    assignment.get().getKey(),
                    PlayerProgressionService.getLevel(target),
                    false
            ));
        }
        return "Awakened " + name + "'s power.";
    }

    private static String setLevel(ServerPlayer target, int requestedLevel) {
        int level = Math.clamp(requestedLevel, 1, PowerLevels.maxLevel());
        int before = PlayerProgressionService.getPowerLevel(target);
        PlayerProgressionService.setPowerLevel(target, level);
        if (level > before) {
            MasteryService.celebrateLevelUp(target, before, level);
        } else {
            refill(target);
        }
        com.jayptucker.heroesevolved.skills.SkillTreeService.sync(target);
        PlayerPowerSyncService.sync(target);
        return "Set " + target.getName().getString() + " to Level " + level + ".";
    }

    private static void refill(ServerPlayer target) {
        PlayerEnergyService.restore(
                target, PlayerEnergyService.getMaximumEnergy(target)
        );
        PlayerPowerSyncService.sync(target);
    }

    private static void sendState(ServerPlayer admin, boolean openScreen) {
        MinecraftServer server = admin.getServer();
        if (server == null) {
            return;
        }

        int maximumLevel = PowerLevels.maxLevel();
        List<String> powers = AbilityRegistry.ABILITIES.keySet().stream()
                .map(ResourceLocation::toString)
                .sorted()
                .toList();
        List<AdminStatePayload.PlayerEntry> players = server.getPlayerList()
                .getPlayers().stream()
                // The admin is listed first: managing yourself is most common.
                .sorted(Comparator.comparing((ServerPlayer player) ->
                                !player.getUUID().equals(admin.getUUID()))
                        .thenComparing(player -> player.getName().getString()))
                .map(player -> describe(player, maximumLevel))
                .toList();

        PacketDistributor.sendToPlayer(admin, new AdminStatePayload(
                openScreen,
                EclipseService.isActive(server),
                maximumLevel,
                powers,
                players
        ));
    }

    private static AdminStatePayload.PlayerEntry describe(
            ServerPlayer player,
            int maximumLevel
    ) {
        Optional<Map.Entry<ResourceLocation, AbilityProgress>> assignment =
                PlayerAbilityService.getData(player).assignedPower();
        int level = PlayerProgressionService.getPowerLevel(player);
        long mastery = PlayerProgressionService.getData(player).mastery();
        long next = level >= maximumLevel
                ? mastery
                : PowerLevels.masteryFor(level + 1);

        return new AdminStatePayload.PlayerEntry(
                player.getUUID(),
                player.getName().getString(),
                assignment.map(entry -> entry.getKey().toString()).orElse(""),
                assignment.map(entry -> entry.getValue().isUnlocked()).orElse(false),
                level,
                mastery,
                next,
                PlayerEnergyService.getEnergy(player),
                PlayerEnergyService.getMaximumEnergy(player),
                PlayerEnergyService.hasUnlimitedEnergy(player)
        );
    }
}
