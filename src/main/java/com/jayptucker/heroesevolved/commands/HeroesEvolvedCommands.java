package com.jayptucker.heroesevolved.commands;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.ability.registry.AbilityRegistry;
import com.jayptucker.heroesevolved.ability.service.PlayerAbilityService;
import com.jayptucker.heroesevolved.energy.PlayerEnergyService;
import com.jayptucker.heroesevolved.events.EclipseService;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class HeroesEvolvedCommands {
    private HeroesEvolvedCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal(HeroesEvolved.MOD_ID)
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("power")
                                .then(Commands.literal("give")
                                        .then(Commands.argument(
                                                        "targets",
                                                        EntityArgument.players()
                                                )
                                                .then(Commands.argument(
                                                                "ability",
                                                                StringArgumentType.word()
                                                        )
                                                        .suggests((context, builder) ->
                                                                SharedSuggestionProvider.suggest(
                                                                        AbilityRegistry.ABILITIES
                                                                                .keySet()
                                                                                .stream()
                                                                                .map(ResourceLocation::toString),
                                                                        builder
                                                                )
                                                        )
                                                        .executes(context -> giveAbility(
                                                                context.getSource(),
                                                                EntityArgument.getPlayers(
                                                                        context,
                                                                        "targets"
                                                                ),
                                                                StringArgumentType.getString(
                                                                        context,
                                                                        "ability"
                                                                )
                                                        ))
                                                )
                                        )
                                )
                                .then(Commands.literal("replace")
                                        .then(Commands.argument(
                                                        "targets",
                                                        EntityArgument.players()
                                                )
                                                .then(Commands.argument(
                                                                "ability",
                                                                StringArgumentType.word()
                                                        )
                                                        .suggests((context, builder) ->
                                                                SharedSuggestionProvider.suggest(
                                                                        AbilityRegistry.ABILITIES
                                                                                .keySet()
                                                                                .stream()
                                                                                .map(ResourceLocation::toString),
                                                                        builder
                                                                )
                                                        )
                                                        .executes(context -> replaceAbility(
                                                                context.getSource(),
                                                                EntityArgument.getPlayers(
                                                                        context,
                                                                        "targets"
                                                                ),
                                                                StringArgumentType.getString(
                                                                        context,
                                                                        "ability"
                                                                )
                                                        ))
                                                )
                                        )
                                )
                                .then(Commands.literal("remove")
                                        .then(Commands.argument(
                                                        "targets",
                                                        EntityArgument.players()
                                                )
                                                .executes(context -> removeAbility(
                                                        context.getSource(),
                                                        EntityArgument.getPlayers(
                                                                context,
                                                                "targets"
                                                        )
                                                ))
                                        )
                                )
                        )
                        .then(Commands.literal("stamina")
                                .then(Commands.literal("unlimited")
                                        .then(Commands.argument(
                                                        "targets",
                                                        EntityArgument.players()
                                                )
                                                .executes(context -> setUnlimitedStamina(
                                                        context.getSource(),
                                                        EntityArgument.getPlayers(
                                                                context,
                                                                "targets"
                                                        ),
                                                        true
                                                ))
                                                .then(Commands.argument(
                                                                "enabled",
                                                                BoolArgumentType.bool()
                                                        )
                                                        .executes(context -> setUnlimitedStamina(
                                                                context.getSource(),
                                                                EntityArgument.getPlayers(
                                                                        context,
                                                                        "targets"
                                                                ),
                                                                BoolArgumentType.getBool(
                                                                        context,
                                                                        "enabled"
                                                                )
                                                        ))
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("eclipse")
                                .then(Commands.literal("start")
                                        .executes(context -> startEclipse(
                                                context.getSource()
                                        ))
                                )
                                .then(Commands.literal("stop")
                                        .executes(context -> stopEclipse(
                                                context.getSource()
                                        ))
                                )
                                .then(Commands.literal("status")
                                        .executes(context -> eclipseStatus(
                                                context.getSource()
                                        ))
                                )
                        )
        );
    }

    private static int startEclipse(CommandSourceStack source) {
        if (!EclipseService.start(source.getServer())) {
            source.sendFailure(Component.literal("An Eclipse is already active."));
            return 0;
        }

        source.sendSuccess(
                () -> Component.literal("Started the Eclipse."),
                true
        );
        return 1;
    }

    private static int stopEclipse(CommandSourceStack source) {
        if (!EclipseService.end(source.getServer())) {
            source.sendFailure(Component.literal("No Eclipse is currently active."));
            return 0;
        }

        source.sendSuccess(
                () -> Component.literal("Stopped the Eclipse."),
                true
        );
        return 1;
    }

    private static int eclipseStatus(CommandSourceStack source) {
        boolean active = EclipseService.isActive(source.getServer());
        source.sendSuccess(
                () -> Component.literal(
                        active ? "An Eclipse is currently active."
                                : "No Eclipse is currently active."
                ),
                false
        );
        return 1;
    }

    private static int setUnlimitedStamina(
            CommandSourceStack source,
            java.util.Collection<ServerPlayer> players,
            boolean enabled
    ) {
        for (ServerPlayer player : players) {
            PlayerEnergyService.setUnlimitedEnergy(player, enabled);
        }

        source.sendSuccess(
                () -> Component.literal(
                        "Unlimited stamina " + (enabled ? "enabled" : "disabled")
                                + " for " + players.size() + " player(s)."
                ),
                true
        );

        return players.size();
    }

    private static int giveAbility(
            CommandSourceStack source,
            java.util.Collection<ServerPlayer> players,
            String abilityName
    ) {
        ResourceLocation abilityId = toAbilityId(abilityName);

        if (!AbilityRegistry.ABILITIES.containsKey(abilityId)) {
            source.sendFailure(Component.literal(
                    "Unknown Heroes Evolved ability: " + abilityName
            ));
            return 0;
        }

        int grantedCount = 0;

        for (ServerPlayer player : players) {
            if (PlayerAbilityService.grantAbility(player, abilityId)) {
                grantedCount++;
            }
        }

        if (grantedCount == 0) {
            source.sendFailure(Component.literal(
                    "Every selected player already has an assigned power."
            ));
            return 0;
        }

        int finalGrantedCount = grantedCount;

        source.sendSuccess(
                () -> Component.literal(
                        "Granted " + abilityId.getPath()
                                + " to " + finalGrantedCount + " player(s)."
                ),
                true
        );

        return grantedCount;
    }

    private static int replaceAbility(
            CommandSourceStack source,
            java.util.Collection<ServerPlayer> players,
            String abilityName
    ) {
        ResourceLocation abilityId = toAbilityId(abilityName);

        if (!AbilityRegistry.ABILITIES.containsKey(abilityId)) {
            source.sendFailure(Component.literal(
                    "Unknown Heroes Evolved power: " + abilityName
            ));
            return 0;
        }

        int replacedCount = 0;

        for (ServerPlayer player : players) {
            if (PlayerAbilityService.replaceWithAbility(player, abilityId)) {
                replacedCount++;
            }
        }

        if (replacedCount == 0) {
            source.sendFailure(Component.literal(
                    "Every selected player already has this power unlocked."
            ));
            return 0;
        }

        int finalReplacedCount = replacedCount;

        source.sendSuccess(
                () -> Component.literal(
                        "Replaced the power of " + finalReplacedCount
                                + " player(s) with " + abilityId.getPath() + "."
                ),
                true
        );

        return replacedCount;
    }

    private static int removeAbility(
            CommandSourceStack source,
            java.util.Collection<ServerPlayer> players
    ) {
        int removedCount = 0;

        for (ServerPlayer player : players) {
            if (PlayerAbilityService.removeAssignedAbility(player)) {
                removedCount++;
            }
        }

        if (removedCount == 0) {
            source.sendFailure(Component.literal(
                    "None of the selected players have a power to remove."
            ));
            return 0;
        }

        int finalRemovedCount = removedCount;

        source.sendSuccess(
                () -> Component.literal(
                        "Removed the power of "
                                + finalRemovedCount
                                + " player(s)."
                ),
                true
        );

        return removedCount;
    }

    private static ResourceLocation toAbilityId(String abilityName) {
        if (abilityName.contains(":")) {
            return ResourceLocation.parse(abilityName);
        }

        return ResourceLocation.fromNamespaceAndPath(
                HeroesEvolved.MOD_ID,
                abilityName
        );
    }
}
