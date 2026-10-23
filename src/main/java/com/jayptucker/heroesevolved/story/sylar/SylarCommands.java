package com.jayptucker.heroesevolved.story.sylar;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;

/** Operator previews avoid waiting through the natural story's grace periods. */
public final class SylarCommands {
    private SylarCommands() {}
    public static LiteralArgumentBuilder<CommandSourceStack> branch() {
        return Commands.literal("sylar").requires(source -> source.hasPermission(2))
                .then(Commands.literal("stalk").then(Commands.argument("player", EntityArgument.player()).executes(context -> {
                    boolean started = SylarStoryService.start(EntityArgument.getPlayer(context, "player"), false);
                    context.getSource().sendSuccess(() -> Component.literal(started ? "A distant watcher has found their trail."
                            : "No encounter started: Sylar is active, the player is ineligible, or no safe loaded sightline was found."), false);
                    return started ? 1 : 0;
                })))
                .then(Commands.literal("hunt").then(Commands.argument("player", EntityArgument.player()).executes(context -> {
                    boolean started = SylarStoryService.start(EntityArgument.getPlayer(context, "player"), true);
                    context.getSource().sendSuccess(() -> Component.literal(started ? "Sylar is watching. The ambush begins after six seconds."
                            : "No encounter started: Sylar is active, the player is ineligible, or no safe loaded sightline was found."), false);
                    return started ? 1 : 0;
                })))
                .then(Commands.literal("dismiss").executes(context -> {
                    SylarStoryService.dismiss(context.getSource().getServer());
                    context.getSource().sendSuccess(() -> Component.literal("The hunter has withdrawn."), false); return 1;
                }))
                .then(Commands.literal("status").then(Commands.argument("player", EntityArgument.player()).executes(context -> {
                    var server = context.getSource().getServer(); var data = SylarStoryData.get(server);
                    var trail = data.trail(EntityArgument.getPlayer(context, "player").getUUID());
                    context.getSource().sendSuccess(() -> Component.literal("Sylar: " + (SylarStoryService.hunter() == null ? "absent" : "active")
                            + "; chapter " + trail.chapter + "/3; attention " + trail.attention + "/12; next encounter in "
                            + Math.max(0, (trail.nextEncounter - server.overworld().getGameTime()) / 20) + "s; defeats "
                            + data.defeats() + "; learned " + data.learned()), false); return 1;
                })));
    }
}
