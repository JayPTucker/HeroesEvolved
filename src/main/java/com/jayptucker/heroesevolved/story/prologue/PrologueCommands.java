package com.jayptucker.heroesevolved.story.prologue;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.jayptucker.heroesevolved.data.ModDataAttachments;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Collection;

/**
 * {@code /lastday ready}: anyone in the Last Day's apartments says they are ready to go out (the chat button runs it).
 * {@code /heroesevolved prologue replay|skip <players>} and {@code /heroesevolved prologue jump <phase> [ticks]}: for
 * admins and previews.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class PrologueCommands {
    private PrologueCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("lastday")
                .then(Commands.literal("ready").executes(context -> {
                    PrologueService.ready(context.getSource().getPlayerOrException());
                    return 1;
                })));
        event.getDispatcher().register(Commands.literal("heroesevolved")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("prologue")
                        .then(Commands.literal("replay").then(Commands.argument("targets", EntityArgument.players())
                                .executes(context -> replay(context.getSource(), EntityArgument.getPlayers(context, "targets")))))
                        .then(Commands.literal("skip").then(Commands.argument("targets", EntityArgument.players())
                                .executes(context -> skip(context.getSource(), EntityArgument.getPlayers(context, "targets")))))
                        .then(Commands.literal("jump").then(Commands.argument("phase", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        new String[] {"evening", "light", "safe", "noah", "pods"}, builder))
                                .executes(context -> jump(context.getSource(), StringArgumentType.getString(context, "phase"), 0))
                                .then(Commands.argument("ticks", IntegerArgumentType.integer(0))
                                        .executes(context -> jump(context.getSource(), StringArgumentType.getString(context, "phase"),
                                                IntegerArgumentType.getInteger(context, "ticks"))))))));
    }

    /** Lives the Last Day again from the apartment, then wakes in the bunker as a new player would. */
    private static int replay(CommandSourceStack source, Collection<ServerPlayer> players) {
        int done = 0;
        for (ServerPlayer player : players) {
            if (PrologueService.isMember(player) || PrologueService.isWaiting(player)) continue;
            player.getPersistentData().remove(PrologueService.SLEPT);
            player.setData(ModDataAttachments.BUNKER_AWOKEN, false);
            PrologueService.join(player);
            done++;
        }
        int count = done;
        source.sendSuccess(() -> Component.literal("Replaying the Last Day for " + count + " player(s)."), true);
        return done;
    }

    private static int skip(CommandSourceStack source, Collection<ServerPlayer> players) {
        int done = 0;
        for (ServerPlayer player : players) {
            if (!PrologueService.isMember(player) && !PrologueService.isWaiting(player)) continue;
            PrologueService.skip(player);
            done++;
        }
        int count = done;
        source.sendSuccess(() -> Component.literal("Woke " + count + " player(s) in the bunker."), true);
        return done;
    }

    private static int jump(CommandSourceStack source, String phase, int ticks) {
        if (!PrologueService.skipTo(source.getServer(), phase, ticks)) {
            source.sendFailure(Component.literal("No Last Day is playing, or no such phase."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("The Last Day: " + PrologueService.phase()), false);
        return 1;
    }
}
