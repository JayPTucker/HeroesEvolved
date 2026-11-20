package com.jayptucker.heroesevolved.story.sylar;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;

/**
 * Operator commands: move the prisoner's story on a step, give a ticket, open the Collection for someone now, end a
 * fight, start the story over, and see where it stands. {@code /heroesevolved sylar next|ticket|fight <player>},
 * {@code /heroesevolved sylar end|reset|status}.
 */
public final class SylarCommands {
    private SylarCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> branch() {
        return Commands.literal("sylar").requires(source -> source.hasPermission(2))
                .then(Commands.literal("ticket").then(Commands.argument("player", EntityArgument.player()).executes(context -> {
                    var player = EntityArgument.getPlayer(context, "player");
                    RepairTicketItem.give(player);
                    context.getSource().sendSuccess(() -> Component.literal("A Repair Ticket is in " + player.getName().getString() + "'s pocket."), true);
                    return 1;
                })))
                .then(Commands.literal("next").then(Commands.argument("player", EntityArgument.player()).executes(context -> {
                    String said = SylarStory.next(EntityArgument.getPlayer(context, "player"));
                    context.getSource().sendSuccess(() -> Component.literal(said), true);
                    return 1;
                })))
                .then(Commands.literal("reset").executes(context -> {
                    var server = context.getSource().getServer();
                    SylarStory.reset();
                    SylarData.get(server).resetStory();
                    LevelFive.refresh(server);
                    context.getSource().sendSuccess(() -> Component.literal("The prisoner is asleep again, and his list is gone. (A hatch already placed stays.)"), true);
                    return 1;
                }))
                .then(Commands.literal("fight").then(Commands.argument("player", EntityArgument.player()).executes(context -> {
                    var player = EntityArgument.getPlayer(context, "player");
                    Component refused = CollectionArena.refusal(player);
                    if (refused != null) { context.getSource().sendFailure(refused); return 0; }
                    boolean started = CollectionArena.open(player) != null;
                    context.getSource().sendSuccess(() -> Component.literal(started ? "Gray & Sons is open." : "The Collection couldn't be opened."), true);
                    return started ? 1 : 0;
                })))
                .then(Commands.literal("end").executes(context -> {
                    boolean was = CollectionArena.open();
                    CollectionArena.end(context.getSource().getServer(), CollectionArena.Outcome.ABANDONED);
                    context.getSource().sendSuccess(() -> Component.literal(was ? "Gray & Sons is closed." : "No fight is on."), true);
                    return was ? 1 : 0;
                }))
                .then(Commands.literal("status").executes(context -> {
                    context.getSource().sendSuccess(() -> Component.literal(status(context.getSource().getServer())), false);
                    return 1;
                }));
    }

    /** One line on the fight and the story's state. */
    public static String status(net.minecraft.server.MinecraftServer server) {
        SylarEntity boss = CollectionArena.sylar();
        SylarData data = SylarData.get(server);
        String fight = boss == null ? "No fight is on." : "Fighting in the Collection: phase " + boss.phase() + ", "
                + Math.round(boss.getHealth()) + "/" + Math.round(boss.getMaxHealth()) + " health, " + Integer.bitCount(boss.cases()) + " cases standing.";
        return SylarStory.status(server) + " " + fight + " Beaten " + data.defeats() + " times. Claire's case "
                + (SylarData.holds(server, com.jayptucker.heroesevolved.ability.registry.ModAbilities.REGENERATION_ID) ? "full" : "dark")
                + ", Sprague's case " + (SylarData.holds(server, com.jayptucker.heroesevolved.ability.registry.ModAbilities.RADIATION_ID) ? "full." : "dark.");
    }
}
