package com.jayptucker.heroesevolved.story.elle;

import com.jayptucker.heroesevolved.HeroesEvolved;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** The Company's visits hook into the game here: the tick, players leaving, the server stopping, and the operator commands. */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class ElleEvents {
    private ElleEvents() {}

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) { ElleStoryService.tick(event.getServer()); }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) ElleStoryService.forget(player);
    }

    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) PrimatechArena.forget(player);
    }

    @SubscribeEvent public static void stopping(ServerStoppingEvent event) { ElleStoryService.clear(event.getServer()); }

    /** {@code /heroesevolved elle omens|arrive|dismiss|status <player>}: operator previews without waiting days. */
    public static LiteralArgumentBuilder<CommandSourceStack> branch() {
        return Commands.literal("elle").requires(source -> source.hasPermission(2))
                .then(Commands.literal("omens").then(Commands.argument("player", EntityArgument.player()).executes(context -> {
                    ElleStoryService.beginOmens(EntityArgument.getPlayer(context, "player"));
                    context.getSource().sendSuccess(() -> Component.literal("The day of omens has begun. Elle arrives when it ends."), false);
                    return 1;
                })))
                .then(Commands.literal("arrive").then(Commands.argument("player", EntityArgument.player()).executes(context -> {
                    boolean started = ElleStoryService.arrive(EntityArgument.getPlayer(context, "player"));
                    context.getSource().sendSuccess(() -> Component.literal(started ? "The lights go out. She's here."
                            : "No arrival: a fight is already on, or the test floor's dimension is missing."), false);
                    return started ? 1 : 0;
                })))
                .then(Commands.literal("dismiss").executes(context -> {
                    ElleStoryService.dismiss(context.getSource().getServer());
                    context.getSource().sendSuccess(() -> Component.literal("Elle has gone."), false);
                    return 1;
                }))
                .then(Commands.literal("status").then(Commands.argument("player", EntityArgument.player()).executes(context -> {
                    var server = context.getSource().getServer();
                    var visit = CompanyVisitData.get(server).visit(EntityArgument.getPlayer(context, "player").getUUID());
                    long now = server.overworld().getGameTime();
                    context.getSource().sendSuccess(() -> Component.literal("Company: " + (visit.omens(now)
                            ? "omens running, arrives in " + (visit.arriveAt - now) / 20 + "s"
                            : visit.next == 0 ? "not scheduled" : "next omens in " + Math.max(0, visit.next - now) / 20 + "s")
                            + "; visits " + visit.visits + ", beaten " + visit.beaten + "; fight " + (ElleStoryService.elle() == null ? "none" : "on")), false);
                    return 1;
                })));
    }
}
