package com.jayptucker.heroesevolved.assembly;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Collection;

/**
 * Matter Assembly's admin controls (the Commands tab finds them under {@code /heroesevolved}):
 * {@code matter status [targets]} reports each assembler's Matter, panels, pads and standing cells;
 * {@code matter fill [targets]} fills their Matter; {@code matter set <amount> [targets]} sets it;
 * {@code matter clear [targets]} takes every construct apart (its Matter comes home).
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class AssemblyCommands {
    private AssemblyCommands() {}

    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("heroesevolved").requires(source -> source.hasPermission(2))
                .then(Commands.literal("matter")
                        .then(Commands.literal("status")
                                .executes(c -> status(c.getSource(), java.util.List.of(c.getSource().getPlayerOrException())))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(c -> status(c.getSource(), EntityArgument.getPlayers(c, "targets")))))
                        .then(Commands.literal("fill")
                                .executes(c -> set(c.getSource(), java.util.List.of(c.getSource().getPlayerOrException()), -1))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(c -> set(c.getSource(), EntityArgument.getPlayers(c, "targets"), -1))))
                        .then(Commands.literal("set").then(Commands.argument("amount", IntegerArgumentType.integer(0, Matter.TIER_FIVE_CAPACITY))
                                .executes(c -> set(c.getSource(), java.util.List.of(c.getSource().getPlayerOrException()), IntegerArgumentType.getInteger(c, "amount")))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(c -> set(c.getSource(), EntityArgument.getPlayers(c, "targets"), IntegerArgumentType.getInteger(c, "amount"))))))
                        .then(Commands.literal("clear")
                                .executes(c -> clear(c.getSource(), java.util.List.of(c.getSource().getPlayerOrException())))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(c -> clear(c.getSource(), EntityArgument.getPlayers(c, "targets")))))));
    }

    private static int status(CommandSourceStack source, Collection<ServerPlayer> targets) {
        int shown = 0;
        for (ServerPlayer p : targets) {
            if (!MatterAssemblyService.authorized(p)) {
                source.sendFailure(Component.literal(p.getGameProfile().getName() + " does not assemble matter."));
                continue;
            }
            source.sendSuccess(() -> Component.literal(p.getGameProfile().getName() + ": " + Matter.of(p) + "/" + Matter.capacity(p)
                    + " Matter, " + MatterAssemblyService.panels(p) + " panels, " + MatterAssemblyService.pads(p) + " pads, "
                    + MatterAssemblyService.cells(p).size() + " standing cells."), false);
            shown++;
        }
        return shown;
    }

    private static int set(CommandSourceStack source, Collection<ServerPlayer> targets, int amount) {
        int changed = 0;
        for (ServerPlayer p : targets) {
            if (!MatterAssemblyService.authorized(p)) {
                source.sendFailure(Component.literal(p.getGameProfile().getName() + " does not assemble matter."));
                continue;
            }
            Matter.fill(p);
            if (amount >= 0) { while (Matter.of(p) > amount) Matter.take(p); Matter.sync(p, true); }
            changed++;
        }
        int done = changed;
        source.sendSuccess(() -> Component.literal("Matter set for " + done + " assembler" + (done == 1 ? "." : "s.")), true);
        return changed;
    }

    private static int clear(CommandSourceStack source, Collection<ServerPlayer> targets) {
        int cleared = 0;
        for (ServerPlayer p : targets) if (MatterAssemblyService.dismantle(p)) cleared++;
        int done = cleared;
        source.sendSuccess(() -> Component.literal("Took apart the constructs of " + done + " assembler" + (done == 1 ? "." : "s.")), true);
        return cleared;
    }
}
