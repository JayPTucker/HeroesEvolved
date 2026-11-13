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
import com.jayptucker.heroesevolved.radio.EmergencyBand;
import com.jayptucker.heroesevolved.story.elle.CompanyVisitData;
import com.jayptucker.heroesevolved.story.elle.ElleStoryService;
import com.jayptucker.heroesevolved.story.sylar.SylarStoryData;
import com.jayptucker.heroesevolved.story.sylar.SylarStoryService;
import com.jayptucker.heroesevolved.story.sylar.WatchmakerArena;
import com.jayptucker.heroesevolved.village.VillagerMeltdown;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;

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
        return switch (action) {
            case START_ECLIPSE, STOP_ECLIPSE, ECLIPSE_TOTALITY, SYLAR_DISMISS, ELLE_DISMISS, MELTDOWN_CANCEL -> false;
            default -> true;
        };
    }

    /** Raids and meltdowns look for a village this far from the chosen player. */
    private static final int VILLAGE_REACH = 64;
    /** The longest and shortest meltdown the menu starts, in seconds. */
    private static final int MELTDOWN_MIN = 5, MELTDOWN_MAX = 600;

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
            case ECLIPSE_TOTALITY -> EclipseService.beginTotality(admin.getServer())
                    ? "Totality: every power falls silent."
                    : "It is already totality.";
            case SYLAR_STALK -> SylarStoryService.start(target, false)
                    ? "Sylar has found " + name + "'s trail."
                    : sylarRefused(target);
            case SYLAR_HUNT -> SylarStoryService.start(target, true)
                    ? "Sylar is coming for " + name + ". The ambush begins in six seconds."
                    : sylarRefused(target);
            case SYLAR_DISMISS -> {
                boolean was = SylarStoryService.hunter() != null;
                SylarStoryService.dismiss(admin.getServer());
                yield was ? "Sylar has withdrawn." : "Sylar isn't in the world.";
            }
            case ELLE_OMENS -> {
                if (!ElleStoryService.eligible(target)) yield elleRefused(target);
                ElleStoryService.beginOmens(target);
                yield "The day of omens has begun for " + name + ". Elle arrives when it ends.";
            }
            case ELLE_ARRIVE -> ElleStoryService.arrive(target)
                    ? "The lights go out around " + name + ". She's here."
                    : elleRefused(target);
            case ELLE_DISMISS -> {
                boolean was = ElleStoryService.elle() != null;
                ElleStoryService.dismiss(admin.getServer());
                yield was ? "Elle has gone." : "Elle isn't here.";
            }
            case MELTDOWN_START -> {
                int seconds = Math.clamp(request.value(), MELTDOWN_MIN, MELTDOWN_MAX);
                yield VillagerMeltdown.startNear(target.serverLevel(), target.position(), seconds * 20) != null
                        ? "A villager near " + name + " has started to glow: " + seconds + " seconds."
                        : "No grown villager within " + VillagerMeltdown.NEAR + " blocks of " + name + ".";
            }
            case MELTDOWN_CANCEL -> {
                int ended = VillagerMeltdown.cancelAll();
                yield ended == 0 ? "No meltdown is under way." : "Ended " + ended + " meltdown" + (ended == 1 ? "." : "s.");
            }
            case RAID_START -> raid(target);
            case GIVE_RADIO -> {
                var radio = EmergencyBand.radio();
                if (radio.isEmpty()) yield "The radio mod couldn't make a radio.";
                yield target.getInventory().add(radio)
                        ? "Gave " + name + " a walkie-talkie tuned to " + EmergencyBand.FREQUENCY + " FM."
                        : name + "'s inventory is full.";
            }
            case TELEPORT_TO -> {
                if (target == admin) yield "That's you.";
                admin.teleportTo(target.serverLevel(), target.getX(), target.getY(), target.getZ(), target.getYRot(), target.getXRot());
                yield "Teleported to " + name + ".";
            }
            case OPEN, REFRESH -> "";
        };
    }

    private static String sylarRefused(ServerPlayer target) {
        if (SylarStoryService.hunter() != null) return "Sylar is already in the world.";
        if (!SylarStoryService.eligible(target)) return target.getName().getString() + " can't be hunted now (" + why(target) + ").";
        return "Sylar found nowhere safe to watch " + target.getName().getString() + " from. Try somewhere more open.";
    }

    private static String elleRefused(ServerPlayer target) {
        if (ElleStoryService.elle() != null) return "Elle is already here.";
        return target.getName().getString() + " can't be visited now (" + why(target) + ").";
    }

    /** The first reason a boss would turn this player down. */
    private static String why(ServerPlayer target) {
        if (target.isCreative() || target.isSpectator()) return "they're in Creative or Spectator";
        if (target.level().getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) return "the world is on Peaceful";
        if (PlayerAbilityService.getData(target).assignedPower().filter(e -> e.getValue().isUnlocked()).isEmpty()) return "they have no awakened power";
        if (EclipseService.arePowersSuppressed(target)) return "it's totality, and powers are silent";
        if (!target.level().dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) return "they aren't in the Overworld";
        return "they're asleep, or in another story";
    }

    /** A vanilla raid on the village nearest the player, as if they had walked in with Bad Omen. */
    private static String raid(ServerPlayer target) {
        var level = target.serverLevel();
        BlockPos bell = level.getPoiManager().findClosest(poi -> poi.is(PoiTypes.MEETING), target.blockPosition(),
                VILLAGE_REACH, PoiManager.Occupancy.ANY).orElse(null);
        if (bell == null) return "No village within " + VILLAGE_REACH + " blocks of " + target.getName().getString() + ".";
        if (level.getRaidAt(bell) != null) return "That village is already being raided.";
        var raid = level.getRaids().createOrExtendRaid(target, bell);
        return raid != null ? "A raid is coming for the village at " + bell.getX() + ", " + bell.getZ() + "."
                : "The raid couldn't start there (Peaceful, or raids are off).";
    }

    private static String givePower(ServerPlayer target, String powerName) {
        ResourceLocation powerId = ResourceLocation.tryParse(powerName);
        if (powerId == null || !AbilityRegistry.ABILITIES.containsKey(powerId)) {
            return "Unknown power: " + powerName;
        }

        String name = target.getName().getString();
        String holder = PlayerAbilityService.adminFree(target, powerId);
        if (holder != null) return "Only one player can have " + powerId.getPath() + ", and " + holder + " still has it. Remove it from them first.";
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
                players,
                world(server)
        ));
    }

    /** One line on each event, for the events tab. */
    private static AdminStatePayload.World world(MinecraftServer server) {
        int flags = 0;
        if (EclipseService.isActive(server)) flags |= AdminStatePayload.World.ECLIPSE;
        if (EclipseService.isTotality(server)) flags |= AdminStatePayload.World.TOTALITY;

        String sylar;
        if (SylarStoryService.hunter() != null) {
            flags |= AdminStatePayload.World.SYLAR;
            sylar = WatchmakerArena.locked() ? "In the fight, in his shop." : "In the world, hunting.";
        } else sylar = "Not in the world.";
        sylar += " Beaten " + SylarStoryData.get(server).defeats() + " times.";

        String elle;
        long now = server.overworld().getGameTime();
        if (ElleStoryService.elle() != null) {
            flags |= AdminStatePayload.World.ELLE;
            var e = ElleStoryService.elle();
            elle = "On the test floor: " + Math.round(e.getHealth()) + "/" + Math.round(e.getMaxHealth()) + " health.";
        } else {
            elle = "Not here.";
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                var visit = CompanyVisitData.get(server).visit(p.getUUID());
                if (visit.omens(now)) {
                    elle = "Omens around " + p.getName().getString() + "; she arrives in " + Math.max(0, (visit.arriveAt - now) / 20) + "s.";
                    break;
                }
            }
        }

        if (!VillagerMeltdown.all().isEmpty()) flags |= AdminStatePayload.World.MELTDOWN;
        String village = VillagerMeltdown.describe();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            var raid = p.serverLevel().getRaidAt(p.blockPosition());
            if (raid != null && raid.isActive()) {
                flags |= AdminStatePayload.World.RAID;
                village += " Raid on " + p.getName().getString() + "'s village (wave " + raid.getGroupsSpawned() + ").";
                break;
            }
        }

        String radio;
        if (EmergencyBand.onAir()) {
            flags |= AdminStatePayload.World.ON_AIR;
            int calls = EmergencyBand.calls();
            radio = EmergencyBand.FREQUENCY + " FM on air: " + (calls == 0 ? "quiet." : calls + (calls == 1 ? " call." : " calls."));
        } else radio = "Radio mod not answering: calls go out as subtitles only.";

        return new AdminStatePayload.World(EclipseService.describe(server).getString(), sylar, elle, village, radio, flags);
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
                PlayerEnergyService.hasUnlimitedEnergy(player),
                player.level().dimension().location().getPath().replace('_', ' ') + "  "
                        + player.getBlockX() + ", " + player.getBlockY() + ", " + player.getBlockZ(),
                story(player)
        );
    }

    /** How far Sylar and the Company have got with them. */
    private static String story(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return "";
        var trail = SylarStoryData.get(server).trail(player.getUUID());
        var visit = CompanyVisitData.get(server).visit(player.getUUID());
        return "Sylar chapter " + trail.chapter + "/" + SylarStoryData.MAX_CHAPTER + "   Company visits " + visit.visits
                + (visit.beaten > 0 ? " (" + visit.beaten + " won)" : "");
    }
}
