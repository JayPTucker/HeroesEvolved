package com.jayptucker.heroesevolved.telekinesis;

import com.jayptucker.heroesevolved.HeroesEvolved;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Collection;
import java.util.List;

/**
 * Telekinesis's admin controls (the Commands tab finds them under {@code /heroesevolved}): {@code telekinesis status
 * [targets]} reports what each telekinetic's far hand is doing (a grip, caught shots, a storm, pins, juggles, a Skyfall,
 * a hover); {@code telekinesis release [targets]} lets everything go gently and lands a levitating caster.
 */
@EventBusSubscriber(modid = HeroesEvolved.MOD_ID)
public final class KineticCommands {
    private KineticCommands() {}

    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("heroesevolved").requires(source -> source.hasPermission(2))
                .then(Commands.literal("telekinesis")
                        .then(Commands.literal("status")
                                .executes(c -> status(c.getSource(), List.of(c.getSource().getPlayerOrException())))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(c -> status(c.getSource(), EntityArgument.getPlayers(c, "targets")))))
                        .then(Commands.literal("release")
                                .executes(c -> release(c.getSource(), List.of(c.getSource().getPlayerOrException())))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(c -> release(c.getSource(), EntityArgument.getPlayers(c, "targets")))))));
    }

    /** One line about a telekinetic, for the console and the admin screen. */
    public static String describe(ServerPlayer p) {
        Entity held = TelekinesisService.gripped(p);
        StringBuilder out = new StringBuilder(p.getGameProfile().getName()).append(": ");
        out.append(held == null ? "holding nothing" : "gripping " + held.getName().getString());
        if (TelekinesisService.hasBulletStop(p)) out.append(", ").append(TelekinesisService.caughtCount(p)).append(" shots caught");
        if (TelekinesisService.hasActiveLift(p)) out.append(", storm running");
        if (TelekinesisService.hasPlucked(p)) out.append(", a plucked weapon ready to fling");
        out.append("; techniques: ").append(TelekinesisSkillService.status(p));
        return out.toString();
    }

    private static int status(CommandSourceStack source, Collection<ServerPlayer> targets) {
        int shown = 0;
        for (ServerPlayer p : targets) {
            if (!TelekinesisSkillService.eligible(p)) {
                source.sendFailure(Component.literal(p.getGameProfile().getName() + " has no awakened Telekinesis."));
                continue;
            }
            String line = describe(p);
            source.sendSuccess(() -> Component.literal(line), false);
            shown++;
        }
        return shown;
    }

    private static int release(CommandSourceStack source, Collection<ServerPlayer> targets) {
        int released = 0;
        for (ServerPlayer p : targets) {
            TelekinesisService.stopAll(p);
            released++;
        }
        int done = released;
        source.sendSuccess(() -> Component.literal("Released everything held by " + done + " telekinetic" + (done == 1 ? "." : "s.")), true);
        return released;
    }
}
