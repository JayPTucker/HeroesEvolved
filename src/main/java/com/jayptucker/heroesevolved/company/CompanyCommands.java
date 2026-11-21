package com.jayptucker.heroesevolved.company;

import com.jayptucker.heroesevolved.village.Villages;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /heroesevolved company attention [player] [value]}, {@code company investigate|convoy|raid [player]},
 * {@code company calm}.
 */
public final class CompanyCommands {
    private CompanyCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> company() {
        return Commands.literal("company")
                .then(Commands.literal("attention")
                        .executes(c -> describe(c, c.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> describe(c, EntityArgument.getPlayer(c, "player")))
                                .then(Commands.argument("value", FloatArgumentType.floatArg(0, CompanyAttention.MAX))
                                        .executes(c -> {
                                            ServerPlayer p = EntityArgument.getPlayer(c, "player");
                                            CompanyAttention.set(p, FloatArgumentType.getFloat(c, "value"));
                                            return describe(c, p);
                                        }))))
                .then(operation("investigate"))
                .then(operation("convoy"))
                .then(operation("raid"))
                .then(Commands.literal("calm").executes(c -> {
                    int n = CompanyOps.callOffAll();
                    c.getSource().sendSuccess(() -> Component.literal(n + " Company operation(s) called off."), true);
                    return n;
                }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> operation(String name) {
        return Commands.literal(name)
                .executes(c -> start(c, name, c.getSource().getPlayerOrException()))
                .then(Commands.argument("player", EntityArgument.player()).executes(c -> start(c, name, EntityArgument.getPlayer(c, "player"))));
    }

    private static int describe(CommandContext<CommandSourceStack> c, ServerPlayer p) {
        float a = CompanyAttention.of(p);
        CompanyOps.Op op = CompanyOps.current(p);
        String doing = op == null ? "nothing under way" : op.kind().name().toLowerCase() + " under way";
        c.getSource().sendSuccess(() -> Component.literal(p.getName().getString() + ": Company attention " + Math.round(a) + " ("
                + CompanyAttention.band(a).name().toLowerCase() + "), " + doing + "."), false);
        return Math.round(a);
    }

    private static int start(CommandContext<CommandSourceStack> c, String name, ServerPlayer p) throws CommandSyntaxException {
        var level = p.serverLevel();
        BlockPos at = p.blockPosition();
        CompanyOps.Op op = switch (name) {
            case "investigate" -> Investigation.start(level, p, at, Villages.bell(level, at), null);
            case "convoy" -> {
                BlockPos bell = CompanyOps.convoyVillage(p);
                yield bell == null ? null : Convoy.start(level, p, bell, null);
            }
            default -> {
                BlockPos home = CompanyOps.home(p);
                Raid r = Raid.start(level, p, home != null && home.distSqr(at) <= 64L * 64 ? home : at, null);
                if (r != null) r.now();
                yield r;
            }
        };
        if (op == null) {
            c.getSource().sendFailure(Component.literal("The Company can't do that now (already busy with " + p.getName().getString()
                    + ", or no village near enough with someone to take)."));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.literal("Company " + name + " started for " + p.getName().getString() + "."), true);
        return 1;
    }
}
